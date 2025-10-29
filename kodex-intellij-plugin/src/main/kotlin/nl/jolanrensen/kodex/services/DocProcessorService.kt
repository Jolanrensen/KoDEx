@file:OptIn(ExperimentalContracts::class)

package nl.jolanrensen.kodex.services

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.LogLevel
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementFactory
import com.intellij.psi.PsiPolyVariantReference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.jolanrensen.kodex.createFromIntellijOrNull
import nl.jolanrensen.kodex.defaultProcessors.IncludeDocProcessor
import nl.jolanrensen.kodex.docContent.DocContent
import nl.jolanrensen.kodex.docContent.asDocContent
import nl.jolanrensen.kodex.docContent.toDocText
import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.documentableWrapper.ProgrammingLanguage
import nl.jolanrensen.kodex.exceptions.TagDocProcessorFailedException
import nl.jolanrensen.kodex.getLoadedProcessors
import nl.jolanrensen.kodex.getOrigin
import nl.jolanrensen.kodex.kodexRenderingIsEnabled
import nl.jolanrensen.kodex.query.DocumentablesByPath
import nl.jolanrensen.kodex.query.DocumentablesByPathWithCache
import nl.jolanrensen.kodex.utils.copiedWithFile
import nl.jolanrensen.kodex.utils.docComment
import nl.jolanrensen.kodex.utils.programmingLanguage
import org.jetbrains.kotlin.idea.base.psi.copied
import org.jetbrains.kotlin.idea.kdoc.KDocElementFactory
import org.jetbrains.kotlin.kdoc.psi.impl.KDocName
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtElement
import java.util.concurrent.CancellationException
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.InvocationKind
import kotlin.contracts.contract
import kotlin.reflect.KType
import kotlin.reflect.typeOf

@Service(Service.Level.PROJECT)
class DocProcessorService(private val project: Project) {

    /**
     * See [DocProcessorService]
     */
    private val logger = logger<DocProcessorService>()

    companion object {
        fun getInstance(project: Project): DocProcessorService = project.service()
    }

    // TODO make configurable
    val processLimit: Int = 10_000

    /**
     * Determines whether the DocProcessor is enabled or disabled.
     */
    val isEnabled get() = kodexRenderingIsEnabled

    fun PsiElement.allChildren(): List<PsiElement> = children.toList() + children.flatMap { it.allChildren() }

    fun PsiElement.allChildrenOfType(kType: KType): List<PsiElement> =
        children.filter { it::class == kType.classifier } +
            children.flatMap { it.allChildrenOfType(kType) }

    /**
     * Resolves a KDoc link from a context by copying the context, adding
     * a new KDoc with just the link there, then resolve it, finally restoring the original.
     */
    private fun resolveKDocLink(link: String, context: PsiElement): List<PsiElement> {
        // Create a copy of the element, so we can modify it
        val psiElement = try {
            context.copiedWithFile()
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        val originalComment = psiElement.docComment

        try {
            val newComment = KDocElementFactory(project)
                .createKDocFromText("/**[$link]*/")

            val newCommentInContext =
                if (psiElement.docComment != null) {
                    psiElement.docComment!!.replace(newComment)
                } else {
                    psiElement.addBefore(newComment, psiElement.firstChild)
                }

            // KDocLink is `[X.Y]`, KDocNames are X, X.Y, for some reason
            return newCommentInContext
                .allChildrenOfType(typeOf<KDocName>())
                .maxBy { it.text.length }
                .let {
                    when (val ref = it.reference) {
                        is PsiPolyVariantReference -> ref.multiResolve(false).mapNotNull { it?.element }
                        else -> listOfNotNull(ref?.resolve())
                    }
                }
        } catch (e: Exception) {
            return emptyList()
        } finally {
            // restore the original docComment state so the text range is still correct
            if (originalComment == null) {
                psiElement.docComment!!.delete()
            } else {
                psiElement.docComment!!.replace(originalComment.copied())
            }
        }
    }

    /**
     * Helper function that queries the project for reference links and returns them as a list of DocumentableWrappers.
     */
    private fun query(context: PsiElement, link: String): List<DocumentableWrapper>? {
        logger.debug { "querying intellij for: $link, from ${(context.navigationElement as? KtElement)?.name}" }

        val kaSymbols = when (val navElement = context.navigationElement) {
            is KtElement -> {
                resolveKDocLink(
                    link = link,
                    context = navElement,
                )
            }

            else -> error("Java not supported yet.")
        }

        val targets = kaSymbols.map {
            when (it) {
                is KtDeclaration, is PsiDocCommentOwner ->
                    DocumentableWrapper.createFromIntellijOrNull(it, useK2 = true)

                else -> null
            }
        }

        return when {
            // No declarations found in entire project, so null
            targets.isEmpty() -> null

            // All documentables are null, but still declarations found, so empty list
            targets.all { it == null } -> emptyList()

            else -> targets.filterNotNull()
        }
    }

    /**
     * Returns a copy of the element with the doc comment modified. If the doc comment is empty, it will be deleted.
     * If it didn't exist before, it will be created anew. Return `null` means it could not be modified and the original
     * rendering method should be used.
     */
    suspend fun getModifiedElement(unmodifiedElement: PsiElement): PsiElement? {
        // Create a copy of the element, so we can modify it
        val psiElement = try {
            unmodifiedElement.copiedWithFile()
        } catch (e: Exception) {
            null
        } ?: return null

        // must have the ability to own a docComment
        try {
            psiElement.docComment
        } catch (e: IllegalStateException) {
            return null
        }

        val newDocContent = getProcessedDocContent(psiElement) ?: return null

        // If the new doc is empty, delete the comment
        if (newDocContent.value.isEmpty()) {
            psiElement.docComment?.delete()
            return psiElement
        }

        // If the new doc is not empty, generate a new doc element
        val newComment = try {
            when (unmodifiedElement.programmingLanguage) {
                ProgrammingLanguage.KOTLIN ->
                    KDocElementFactory(project)
                        .createKDocFromText(newDocContent.toDocText().value)

                // TODO can crash here?

                ProgrammingLanguage.JAVA ->
                    PsiElementFactory.getInstance(project)
                        .createDocCommentFromText(newDocContent.toDocText().value)
            }
        } catch (_: Exception) {
            return null
        }

        // Replace the old doc element with the new one if it exists, otherwise add a new one
        if (psiElement.docComment != null) {
            psiElement.docComment?.replace(newComment)
        } else {
            psiElement.addBefore(newComment, psiElement.firstChild)
        }

        return psiElement
    }

    /**
     * Thread/coroutine-safe wrapper around [DocumentablesByPathWithCache] so it
     * is modified in a thread-safe way.
     */
    private inner class CacheHolder {
        private val mutex = Mutex()

        private val documentableCache = DocumentablesByPathWithCache(
            processLimit = processLimit,
            loadedProcessors = getLoadedProcessors(),
            logDebug = { logger.debug(null, it) },
            queryNew = { context, link ->
                query(context.getOrigin(), link)
            },
        )

        suspend inline fun <T> withLock(block: suspend (DocumentablesByPathWithCache) -> T): T {
            contract {
                callsInPlace(block, InvocationKind.EXACTLY_ONCE)
            }
            return mutex.withLock {
                block(documentableCache)
            }
        }
    }

    private val documentableCacheHolder = CacheHolder()

    fun getDocumentableWrapperOrNull(psiElement: PsiElement): DocumentableWrapper? {
        val documentableWrapper = DocumentableWrapper.createFromIntellijOrNull(psiElement, useK2 = true)
        if (documentableWrapper == null) {
            thisLogger().warn("Could not create DocumentableWrapper from element: $psiElement")
        }
        return documentableWrapper
    }

    /**
     * Returns a processed version of the DocumentableWrapper, or `null` if it could not be processed.
     * ([DocumentableWrapper.docContent] contains the modified doc content).
     */
    suspend fun getProcessedDocumentableWrapperOrNull(documentableWrapper: DocumentableWrapper): DocumentableWrapper? =
        documentableCacheHolder.withLock { documentableCache ->
            val needsRebuild = documentableCache.updatePreProcessing(documentableWrapper)

            logger.debug { "\n\n" }

            if (!needsRebuild) {
                logger.debug {
                    "loading fully cached ${
                        documentableWrapper.fullyQualifiedPath
                    }/${documentableWrapper.fullyQualifiedExtensionPath}"
                }

                val docContentFromCache = documentableCache.getDocContentResult(documentableWrapper.identifier)

                // should never be null, but just in case
                if (docContentFromCache != null) {
                    return documentableWrapper.copy(
                        docContent = docContentFromCache,
                        isModified = true,
                    )
                }
            }
            logger.debug {
                "preprocessing ${
                    documentableWrapper.fullyQualifiedPath
                }/${documentableWrapper.fullyQualifiedExtensionPath}"
            }

            // Process the DocumentablesByPath
            val results = processDocumentablesByPath(documentableCache)

            // Retrieve the original DocumentableWrapper from the results
            val doc = results[documentableWrapper.identifier] ?: return null

            documentableCache.updatePostProcessing()

            return doc
        }

    private suspend fun getProcessedDocContent(psiElement: PsiElement): DocContent? {
        return try {
            // Create a DocumentableWrapper from the element
            val documentableWrapper = getDocumentableWrapperOrNull(psiElement)
                ?: return null

            // get the processed version of the DocumentableWrapper
            val processed = getProcessedDocumentableWrapperOrNull(documentableWrapper)
                ?: return null

            processed.docContent
        } catch (_: ProcessCanceledException) {
            return null
        } catch (_: CancellationException) {
            return null
        } catch (e: TagDocProcessorFailedException) {
//            println(e.message)
//            println(e.cause)
            // e.printStackTrace()
            // render fancy :)
            e.renderDoc()
        } catch (e: Throwable) {
//            println(e.message)
//            println(e.cause)
            // e.printStackTrace()

            // instead of throwing the exception, render it inside the kdoc
            """
            |```
            |$e
            |
            |${e.stackTrace.joinToString("\n")}
            |```
            """.trimMargin().asDocContent()
        }
    }

    private suspend fun processDocumentablesByPath(
        sourceDocsByPath: DocumentablesByPathWithCache,
    ): DocumentablesByPath {
        // Find all processors
        val processors = getLoadedProcessors().toMutableList()

        // for cache collecting after include doc processor
        processors.add(
            processors.indexOfFirst { it is IncludeDocProcessor } + 1,
            PostIncludeDocProcessorCacheCollector(sourceDocsByPath),
        )

        // Run all processors
        val modifiedDocumentables = processors.fold(sourceDocsByPath as DocumentablesByPath) { acc, processor ->
            processor.processSafely(processLimit = processLimit, documentablesByPath = acc)
        }

        return modifiedDocumentables
    }

    init {
        thisLogger().setLevel(LogLevel.INFO) // TEMP
    }
}
