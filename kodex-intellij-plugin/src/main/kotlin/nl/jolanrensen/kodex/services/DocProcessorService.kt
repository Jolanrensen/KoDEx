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
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiPolyVariantReference
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import nl.jolanrensen.kodex.ProcessorLoader
import nl.jolanrensen.kodex.createFromIntellijOrNull
import nl.jolanrensen.kodex.defaultProcessors.IncludeDocProcessor
import nl.jolanrensen.kodex.docContent.DocContent
import nl.jolanrensen.kodex.docContent.asDocContent
import nl.jolanrensen.kodex.docContent.toDocText
import nl.jolanrensen.kodex.documentableWrapper.DocumentableWrapper
import nl.jolanrensen.kodex.documentableWrapper.ProgrammingLanguage
import nl.jolanrensen.kodex.exceptions.TagDocProcessorFailedException
import nl.jolanrensen.kodex.getOrigin
import nl.jolanrensen.kodex.gradle.KodexFileContext
import nl.jolanrensen.kodex.gradle.KodexGradleConnectionService
import nl.jolanrensen.kodex.gradle.KodexSourceSetConfiguration
import nl.jolanrensen.kodex.kodexRenderingIsEnabled
import nl.jolanrensen.kodex.processor.DocProcessor
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
import java.io.File
import java.util.concurrent.CancellationException
import java.util.concurrent.ConcurrentHashMap
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

    /** Used in [KodexFileContext.Standalone] mode, connected files use [KodexSourceSetConfiguration.processLimit]. */
    val processLimit: Int = 10_000

    /**
     * Determines whether the DocProcessor is enabled or disabled.
     */
    val isEnabled get() = kodexRenderingIsEnabled

    private val gradleConnection get() = KodexGradleConnectionService.getInstance(project)

    /**
     * Creates the processors for a [KodexFileContext]: the built-in defaults when standalone,
     * or the processors (and KoDEx plugins) configured in the Gradle build when connected.
     */
    private val processorLoader = ProcessorLoader(DocProcessorService::class.java.classLoader)

    /**
     * How KoDEx treats [file]: standalone, or connected to one of the `preprocess` configurations
     * of the Gradle build.
     */
    fun contextFor(file: PsiFile?): KodexFileContext = gradleConnection.contextFor(file)

    fun contextFor(element: PsiElement): KodexFileContext = gradleConnection.contextFor(element)

    private fun contextFor(file: File): KodexFileContext = gradleConnection.contextFor(file)

    /**
     * Fresh processor instances for the given [context].
     * Fresh, because a [DocProcessor] instance can only run once.
     */
    fun processorsFor(context: KodexFileContext): List<DocProcessor> = processorLoader.load(context)

    /**
     * Processor instances for the given [context] that are only used for their stateless information
     * ([DocProcessor.completionInfos], [DocProcessor.getHighlightsFor]), so they can be reused.
     */
    fun statelessProcessorsFor(context: KodexFileContext): List<DocProcessor> =
        statelessProcessors.getOrPut(context.cacheKey) { processorsFor(context) }

    fun statelessProcessorsFor(file: PsiFile?): List<DocProcessor> = statelessProcessorsFor(contextFor(file))

    private val statelessProcessors = ConcurrentHashMap<Any, List<DocProcessor>>()

    /** Processors with the same key behave identically, so caches and stateless instances can be shared. */
    private val KodexFileContext.cacheKey: Any
        get() = when (this) {
            is KodexFileContext.Standalone -> KodexFileContext.Standalone
            is KodexFileContext.Connected -> configuration.processorsKey
        }

    private val KodexFileContext.processLimit: Int
        get() = when (this) {
            is KodexFileContext.Standalone -> this@DocProcessorService.processLimit
            is KodexFileContext.Connected -> configuration.processLimit
        }

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
     *
     * When [fileContext] is [KodexFileContext.Connected], only declarations living in the source roots the Gradle
     * task would analyze are returned (the preprocessed source set and its `contextualSourceSets`), mirroring
     * the result of the Gradle build. Declarations outside of these are treated as non-existent (`null`).
     */
    private fun query(context: PsiElement, link: String, fileContext: KodexFileContext): List<DocumentableWrapper>? {
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

        val targets = kaSymbols
            .filter { fileContext.isVisible(it) }
            .map {
                when (it) {
                    is KtDeclaration, is PsiDocCommentOwner ->
                        createFromIntellijOrNull(it)

                    else -> null
                }
            }

        return when {
            // No declarations found in entire project (or in the visible source sets), so null
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
     * Whether [element] can be seen from a file with this context. Everything is visible when standalone.
     * Elements outside the local file system (like compiled library classes) are never visible when connected,
     * as Gradle KoDEx only analyzes sources.
     */
    private fun KodexFileContext.isVisible(element: PsiElement): Boolean {
        if (this !is KodexFileContext.Connected) return true
        val virtualFile = element.containingFile?.originalFile?.virtualFile ?: return false
        if (!virtualFile.isInLocalFileSystem) return false
        return isVisible(File(virtualFile.path))
    }

    /**
     * Thread/coroutine-safe wrapper around [DocumentablesByPathWithCache] so it
     * is modified in a thread-safe way.
     *
     * There is one per set of processors (see [cacheKey]), since the cached results depend on which processors
     * ran with which arguments.
     */
    private inner class CacheHolder(private val context: KodexFileContext) {
        private val mutex = Mutex()

        val processLimit: Int = context.processLimit

        /** Fresh processors for the [context] of this cache. */
        fun processors(): List<DocProcessor> = processorsFor(context)

        private val documentableCache = DocumentablesByPathWithCache(
            processLimit = processLimit,
            loadedProcessors = statelessProcessorsFor(context),
            logDebug = { logger.debug(null, it) },
            queryNew = { context, link ->
                // the visibility depends on the file the query comes from, not on the cache
                query(context.getOrigin(), link, contextFor(context.file))
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

    private val cacheHolders = ConcurrentHashMap<Any, CacheHolder>()

    private fun cacheHolderFor(context: KodexFileContext): CacheHolder =
        cacheHolders.getOrPut(context.cacheKey) { CacheHolder(context) }

    fun getDocumentableWrapperOrNull(psiElement: PsiElement): DocumentableWrapper? {
        val documentableWrapper = createFromIntellijOrNull(psiElement)
        if (documentableWrapper == null) {
            thisLogger().warn("Could not create DocumentableWrapper from element: $psiElement")
        }
        return documentableWrapper
    }

    /**
     * Returns a processed version of the DocumentableWrapper, or `null` if it could not be processed.
     * ([DocumentableWrapper.docContent] contains the modified doc content).
     */
    suspend fun getProcessedDocumentableWrapperOrNull(documentableWrapper: DocumentableWrapper): DocumentableWrapper? {
        val cacheHolder = cacheHolderFor(contextFor(documentableWrapper.file))
        return cacheHolder.withLock { documentableCache ->
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
                        tags = emptySet(),
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
            val results = processDocumentablesByPath(documentableCache, cacheHolder)

            // Retrieve the original DocumentableWrapper from the results
            val doc = results[documentableWrapper.identifier] ?: return null

            documentableCache.updatePostProcessing()

            return doc
        }
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
        } catch (e: ProcessCanceledException) {
           throw e
        } catch (e: CancellationException) {
            throw e
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
        cacheHolder: CacheHolder,
    ): DocumentablesByPath {
        // Find all processors (fresh instances) for the configuration of this cache
        val processors = cacheHolder.processors().toMutableList()

        // for cache collecting after include doc processor (or at the start if there is none)
        processors.add(
            processors.indexOfFirst { it is IncludeDocProcessor } + 1,
            PostIncludeDocProcessorCacheCollector(sourceDocsByPath),
        )

        // Run all processors
        val processLimit = cacheHolder.processLimit
        val modifiedDocumentables = processors.fold(sourceDocsByPath as DocumentablesByPath) { acc, processor ->
            processor.processSafely(processLimit = processLimit, documentablesByPath = acc)
        }

        return modifiedDocumentables
    }

    init {
        thisLogger().setLevel(LogLevel.INFO) // TEMP

        // caches depend on the Gradle configuration, so start over when it changes (after a sync)
        project.messageBus.connect().subscribe(
            KodexGradleConnectionService.TOPIC,
            KodexGradleConnectionService.KodexGradleConnectionListener {
                statelessProcessors.clear()
                processorLoader.reset()
                cacheHolders.clear()
            },
        )
    }
}
