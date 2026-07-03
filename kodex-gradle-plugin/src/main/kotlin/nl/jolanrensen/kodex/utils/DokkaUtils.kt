@file:OptIn(InternalDokkaApi::class)

package nl.jolanrensen.kodex.utils

/*
 * Many of the contents of this file are copied from Dokka's
 * [DocComment][org.jetbrains.dokka.base.translators.psi.parsers.DocComment]
 * since many of these methods were internal or private.
 * However, there's no other way to access JavaDoc than with [findClosestDocComment].
 * See https://github.com/Kotlin/dokka/blob/master/plugins/base/src/main/kotlin/translators/psi/parsers/PsiCommentsUtils.kt
 */

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiDocCommentOwner
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaCodeReferenceElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.PsiMember
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.PsiPackage
import com.intellij.psi.PsiQualifiedNamedElement
import com.intellij.psi.impl.source.tree.JavaDocElementType
import com.intellij.psi.javadoc.PsiDocComment
import com.intellij.psi.javadoc.PsiDocTag
import com.intellij.psi.util.PsiTreeUtil
import nl.jolanrensen.kodex.docContent.DocContent
import nl.jolanrensen.kodex.docContent.asDocTextOrNull
import nl.jolanrensen.kodex.docContent.getDocContent
import nl.jolanrensen.kodex.documentableWrapper.AnnotationWrapper
import nl.jolanrensen.kodex.documentableWrapper.ProgrammingLanguage
import nl.jolanrensen.kodex.documentableWrapper.SimpleImportPath
import org.jetbrains.dokka.InternalDokkaApi
import org.jetbrains.dokka.analysis.java.DescriptionJavadocTag
import org.jetbrains.dokka.analysis.java.ExceptionJavadocTag
import org.jetbrains.dokka.analysis.java.JavadocTag
import org.jetbrains.dokka.analysis.java.ParamJavadocTag
import org.jetbrains.dokka.analysis.java.ThrowingExceptionJavadocTag
import org.jetbrains.dokka.analysis.java.ThrowsJavadocTag
import org.jetbrains.dokka.analysis.java.doccomment.DocCommentCreator
import org.jetbrains.dokka.analysis.java.doccomment.DocumentationContent
import org.jetbrains.dokka.analysis.java.util.PsiDocumentableSource
import org.jetbrains.dokka.analysis.java.util.from
import org.jetbrains.dokka.links.DRI
import org.jetbrains.dokka.links.JavaClassReference
import org.jetbrains.dokka.links.Nullable
import org.jetbrains.dokka.links.TypeConstructor
import org.jetbrains.dokka.links.TypeReference
import org.jetbrains.dokka.model.AnnotationParameterValue
import org.jetbrains.dokka.model.AnnotationValue
import org.jetbrains.dokka.model.ArrayValue
import org.jetbrains.dokka.model.BooleanValue
import org.jetbrains.dokka.model.Callable
import org.jetbrains.dokka.model.ClassValue
import org.jetbrains.dokka.model.DClasslike
import org.jetbrains.dokka.model.DParameter
import org.jetbrains.dokka.model.DTypeAlias
import org.jetbrains.dokka.model.Documentable
import org.jetbrains.dokka.model.DocumentableSource
import org.jetbrains.dokka.model.DoubleValue
import org.jetbrains.dokka.model.EnumValue
import org.jetbrains.dokka.model.FloatValue
import org.jetbrains.dokka.model.IntValue
import org.jetbrains.dokka.model.LiteralValue
import org.jetbrains.dokka.model.LongValue
import org.jetbrains.dokka.model.NullValue
import org.jetbrains.dokka.model.StringValue
import org.jetbrains.dokka.utilities.DokkaLogger
import org.jetbrains.kotlin.kdoc.parser.KDocKnownTag
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.kdoc.psi.impl.KDocSection
import org.jetbrains.kotlin.kdoc.psi.impl.KDocTag
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtConstructor
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtDeclarationWithBody
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtParameter
import org.jetbrains.kotlin.psi.KtPrimaryConstructor
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtTypeParameter
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildrenOfType
import org.jetbrains.kotlin.psi.psiUtil.isPropertyParameter
import org.jetbrains.kotlin.renderer.render
import org.jetbrains.kotlin.resolve.ImportPath
import org.jetbrains.kotlin.util.capitalizeDecapitalize.toLowerCaseAsciiOnly
import org.jetbrains.kotlin.utils.addToStdlib.UnsafeCastFunction
import org.jetbrains.kotlin.utils.addToStdlib.firstIsInstanceOrNull
import org.jetbrains.kotlin.utils.addToStdlib.safeAs
import org.jetbrains.dokka.analysis.java.doccomment.DocComment as DokkaDocComment

/**
 * Is [linkable element](https://kotlinlang.org/docs/kotlin-doc.html#links-to-elements)
 *
 * @receiver [Documentable]
 * @return true if receiver is linkable element
 */
fun Documentable.isLinkableElement(): Boolean =
    this is DClasslike ||
        this is Callable ||
        this is DParameter ||
        this is DTypeAlias // TODO: issue #12: will not be included in DocumentableWithSources since it has no source

/**
 * Get Kdoc content. Note! This doesn't include the @ Kdoc tags.
 *
 * @receiver [DokkaDocComment]
 * @return
 */
val DokkaDocComment.documentString: DocContent?
    get() = when (this) {
        is JavaDocComment -> comment.text
        is KotlinDocComment -> comment.text
        else -> null
    }?.asDocTextOrNull()
        ?.getDocContent()

/**
 * Get text range of Kdoc/JavaDoc comment from /** to */
 *
 * @receiver [DokkaDocComment]
 * @return [TextRange]
 */
val DokkaDocComment.textRange: TextRange
    get() = when (this) {
        is JavaDocComment -> comment.textRange
        is KotlinDocComment -> comment.parent.textRange
        else -> error("")
    }

/**
 * Gets the [PsiNamedElement] from the [DocumentableSource] if it can find it.
 */
val DocumentableSource.psi: PsiElement?
    get() = when (this::class.qualifiedName) {
        PsiDocumentableSource::class.qualifiedName -> {
            (this as PsiDocumentableSource).psi
        }

        "org.jetbrains.dokka.analysis.kotlin.symbols.services.KtPsiDocumentableSource" -> {
            Class.forName("org.jetbrains.dokka.analysis.kotlin.symbols.services.KtPsiDocumentableSource")
                .getMethod("getPsi")
                .invoke(this) as PsiElement?
        }

        else -> {
            null
        }
    }

/**
 * Get the text range in the file for this [DocumentableSource].
 */
val DocumentableSource.textRange: TextRange?
    get() = psi?.textRange

internal class JavaDocComment(val comment: PsiDocComment) : DokkaDocComment {
    override fun hasTag(tag: JavadocTag): Boolean =
        when (tag) {
            is ThrowingExceptionJavadocTag -> hasTag(tag)
            else -> comment.hasTag(tag)
        }

    private fun hasTag(tag: ThrowingExceptionJavadocTag): Boolean =
        comment.hasTag(tag) &&
            comment.resolveTag(tag).firstIsInstanceOrNull<PsiDocTag>()
                ?.resolveToElement()
                ?.getKotlinFqName() == tag.exceptionQualifiedName

    override fun resolveTag(tag: JavadocTag): List<DocumentationContent> =
        when (tag) {
            is ParamJavadocTag -> resolveParamTag(tag)
            is ThrowingExceptionJavadocTag -> resolveThrowingTag(tag)
            else -> comment.resolveTag(tag).map { PsiDocumentationContent(it, tag) }
        }

    private fun resolveParamTag(tag: ParamJavadocTag): List<DocumentationContent> {
        val resolvedParamElements = comment.resolveTag(tag)
            .filterIsInstance<PsiDocTag>()
            .map { it.contentElementsWithSiblingIfNeeded() }
            .firstOrNull { it.firstOrNull()?.text == tag.paramName }.orEmpty()

        return resolvedParamElements
            .withoutReferenceLink()
            .map { PsiDocumentationContent(it, tag) }
    }

    private fun resolveThrowingTag(tag: ThrowingExceptionJavadocTag): List<DocumentationContent> {
        val resolvedElements = comment.resolveTag(tag)
            .flatMap {
                when (it) {
                    is PsiDocTag -> it.contentElementsWithSiblingIfNeeded()
                    else -> listOf(it)
                }
            }

        return resolvedElements
            .withoutReferenceLink()
            .map { PsiDocumentationContent(it, tag) }
    }

    private fun PsiDocComment.resolveTag(tag: JavadocTag): List<PsiElement> =
        when (tag) {
            DescriptionJavadocTag -> this.descriptionElements.toList()
            else -> this.findTagsByName(tag.name).toList()
        }

    private fun List<PsiElement>.withoutReferenceLink(): List<PsiElement> = drop(1)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as JavaDocComment

        if (comment != other.comment) return false

        return true
    }

    override fun hashCode(): Int = comment.hashCode()
}

class KotlinDocComment(val comment: KDocTag, val resolveDocContext: ResolveDocContext) : DokkaDocComment {

    private val tagsWithContent: List<KDocTag> = comment.children.mapNotNull { (it as? KDocTag) }

    override fun hasTag(tag: JavadocTag): Boolean =
        when (tag) {
            is DescriptionJavadocTag -> comment.getContent().isNotEmpty()
            is ThrowingExceptionJavadocTag -> tagsWithContent.any { it.hasException(tag) }
            else -> tagsWithContent.any { it.text.startsWith("@${tag.name}") }
        }

    private fun KDocTag.hasException(tag: ThrowingExceptionJavadocTag) =
        text.startsWith("@${tag.name}") && getSubjectName() == tag.exceptionQualifiedName

    override fun resolveTag(tag: JavadocTag): List<DocumentationContent> =
        when (tag) {
            is DescriptionJavadocTag -> {
                listOf(DescriptorDocumentationContent(resolveDocContext, comment, tag))
            }

            is ParamJavadocTag -> {
                val resolvedContent = resolveGeneric(tag)
                listOf(resolvedContent[tag.paramIndex])
            }

            is ThrowsJavadocTag -> {
                resolveThrowingException(tag)
            }

            is ExceptionJavadocTag -> {
                resolveThrowingException(tag)
            }

            else -> {
                resolveGeneric(tag)
            }
        }

    private fun resolveThrowingException(tag: ThrowingExceptionJavadocTag): List<DescriptorDocumentationContent> {
        val exceptionName = tag.exceptionQualifiedName ?: return resolveGeneric(tag)

        return comment.children
            .filterIsInstance<KDocTag>()
            .filter { it.name == tag.name && it.getSubjectName() == exceptionName }
            .map { DescriptorDocumentationContent(resolveDocContext, it, tag) }
    }

    private fun resolveGeneric(tag: JavadocTag): List<DescriptorDocumentationContent> =
        comment.children.mapNotNull { element ->
            if (element is KDocTag && element.name == tag.name) {
                DescriptorDocumentationContent(resolveDocContext, element, tag)
            } else {
                null
            }
        }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KotlinDocComment

        if (comment != other.comment) return false
        // if (resolveDocContext.name != other.resolveDocContext.name) return false
        if (tagsWithContent != other.tagsWithContent) return false

        return true
    }

    override fun hashCode(): Int {
        var result = comment.hashCode()
        // result = 31 * result + resolveDocContext.name.hashCode()
        result = 31 * result + tagsWithContent.hashCode()
        return result
    }
}

interface DocumentationContent {
    val tag: JavadocTag
}

data class PsiDocumentationContent(val psiElement: PsiElement, override val tag: JavadocTag) : DocumentationContent {

    override fun resolveSiblings(): List<DocumentationContent> =
        if (psiElement is PsiDocTag) {
            psiElement.contentElementsWithSiblingIfNeeded()
                .map { content -> PsiDocumentationContent(content, tag) }
        } else {
            listOf(this)
        }
}

fun PsiDocTag.contentElementsWithSiblingIfNeeded(): List<PsiElement> =
    if (dataElements.isNotEmpty()) {
        listOfNotNull(
            dataElements[0],
            dataElements[0].nextSibling?.takeIf { it.text != dataElements.drop(1).firstOrNull()?.text },
            *dataElements.drop(1).toTypedArray(),
        )
    } else {
        emptyList()
    }

class ResolveDocContext(val ktElement: KtElement)

data class DescriptorDocumentationContent(
    val resolveDocContext: ResolveDocContext,
    val element: KDocTag,
    override val tag: JavadocTag,
) : DocumentationContent {
    override fun resolveSiblings(): List<DocumentationContent> = listOf(this)
}

internal fun PsiDocComment.hasTag(tag: JavadocTag): Boolean =
    when (tag) {
        DescriptionJavadocTag -> descriptionElements.isNotEmpty()
        else -> findTagByName(tag.name) != null
    }

fun PsiDocComment.tagsByName(tag: JavadocTag): List<PsiElement> =
    when (tag) {
        DescriptionJavadocTag -> descriptionElements.toList()
        else -> findTagsByName(tag.toString()).toList()
    }

internal fun PsiElement.getKotlinFqName(): String? = this.kotlinFqNameProp

// // from import org.jetbrains.kotlin.idea.base.psi.kotlinFqName
internal val PsiElement.kotlinFqNameProp: String?
    get() = when (val element = this) {
        is PsiPackage -> element.qualifiedName

        is PsiClass -> element.qualifiedName

        is PsiMember -> element.name?.let { name ->
            val prefix = element.containingClass?.qualifiedName
            if (prefix != null) "$prefix.$name" else name
        }

        //        is KtNamedDeclaration -> element.fqName TODO [beresnev] decide what to do with it
        is PsiQualifiedNamedElement -> element.qualifiedName

        else -> null
    }

fun PsiClass.implementsInterface(fqName: FqName): Boolean =
    allInterfaces().any { it.getKotlinFqName() == fqName.toString() }

fun PsiClass.allInterfaces(): Sequence<PsiClass> =
    sequence {
        this.yieldAll(interfaces.toList())
        interfaces.forEach { yieldAll(it.allInterfaces()) }
    }

/**
 * Workaround for failing [PsiMethod.findSuperMethods].
 * This might be resolved once ultra light classes are enabled for dokka
 * See [KT-39518](https://youtrack.jetbrains.com/issue/KT-39518)
 */
@OptIn(UnsafeCastFunction::class)
fun PsiMethod.findSuperMethodsOrEmptyArray(logger: DokkaLogger): Array<PsiMethod> {
    return try {
        /*
        We are not even attempting to call "findSuperMethods" on all methods called "getGetter" or "getSetter"
        on any object implementing "kotlin.reflect.KProperty", since we know that those methods will fail
        (KT-39518). Just catching the exception is not good enough, since "findSuperMethods" will
        print the whole exception to stderr internally and then spoil the console.
         */
        val kPropertyFqName = FqName("kotlin.reflect.KProperty")
        if (
            this.parent?.safeAs<PsiClass>()?.implementsInterface(kPropertyFqName) == true &&
            (this.name == "getSetter" || this.name == "getGetter")
        ) {
            logger.warn("Skipped lookup of super methods for ${getKotlinFqName()} (KT-39518)")
            return emptyArray()
        }
        findSuperMethods()
    } catch (_: Throwable) {
        logger.warn("Failed to lookup of super methods for ${getKotlinFqName()} (KT-39518)")
        emptyArray()
    }
}

internal data class KDocContent(val contentTag: KDocTag, val sections: List<KDocSection>)

internal fun KtElement.findKDoc(): KDocContent? = this.lookupOwnedKDoc()
// We're only interested in directly owned KDocs, actually written next to the documentable
// ?: this.lookupKDocInContainer()

/**
 * Looks for sections that have a deeply nested [tag],
 * as opposed to [KDoc.findSectionByTag], which only looks among the top level
 */
private fun KDoc.findSectionsContainingTag(tag: KDocKnownTag): List<KDocSection> =
    getChildrenOfType<KDocSection>()
        .filter { it.findTagByName(tag.name.toLowerCaseAsciiOnly()) != null }

private fun KtElement.lookupOwnedKDoc(): KDocContent? {
    // KDoc for primary constructor is located inside of its class KDoc
    val psiDeclaration = when (this) {
        is KtPrimaryConstructor -> getContainingClassOrObject()
        else -> this
    }

    if (psiDeclaration is KtDeclaration) {
        val kdoc = psiDeclaration.docComment
        if (kdoc != null) {
            if (this is KtConstructor<*>) {
                // ConstructorDescriptor resolves to the same JetDeclaration
                val constructorSection = kdoc.findSectionByTag(KDocKnownTag.CONSTRUCTOR)
                if (constructorSection != null) {
                    // if annotated with @constructor tag and the caret is on constructor definition,
                    // then show @constructor description as the main content, and additional sections
                    // that contain @param tags (if any), as the most relatable ones
                    // practical example: val foo = Fo<caret>o("argument") -- show @constructor and @param content
                    val paramSections = kdoc.findSectionsContainingTag(KDocKnownTag.PARAM)
                    return KDocContent(constructorSection, paramSections)
                }
            }
            return KDocContent(kdoc.getDefaultSection(), kdoc.getAllSections())
        }
    }

    return null
}

/**
 * Can find KDoc for properties, functions, classes, and type parameters written
 * in the class's KDoc, like `@property s: String`.
 */
private fun KtElement.lookupKDocInContainer(): KDocContent? {
    val subjectName = name
    val containingDeclaration =
        PsiTreeUtil.findFirstParent(this, true) {
            it is KtDeclarationWithBody &&
                it !is KtPrimaryConstructor ||
                it is KtClassOrObject
        }

    val containerKDoc = containingDeclaration?.getChildOfType<KDoc>()
    if (containerKDoc == null || subjectName == null) return null
    val propertySection = containerKDoc.findSectionByTag(KDocKnownTag.PROPERTY, subjectName)
    val paramTag =
        containerKDoc.findDescendantOfType<KDocTag> {
            it.knownTag == KDocKnownTag.PARAM &&
                it.getSubjectName() == subjectName
        }

    val primaryContent = when {
        // class Foo(val <caret>s: String)
        this is KtParameter && this.isPropertyParameter() -> propertySection ?: paramTag

        // fun some(<caret>f: String) || class Some<<caret>T: Base> || Foo(<caret>s = "argument")
        this is KtParameter || this is KtTypeParameter -> paramTag

        // if this property is declared separately (outside primary constructor), but it's for some reason
        // annotated as @property in class's description, instead of having its own KDoc
        this is KtProperty && containingDeclaration is KtClassOrObject -> propertySection

        else -> null
    }
    return primaryContent?.let {
        // makes little sense to include any other sections, since we found
        // documentation for a very specific element, like a property/param
        KDocContent(it, sections = emptyList())
    }
}

object DescriptorKotlinDocCommentCreator : DocCommentCreator {
    override fun create(element: PsiNamedElement): DokkaDocComment? {
        val ktElement = element.navigationElement as? KtElement ?: return null
        val kdoc = ktElement.findKDoc() ?: return null

        return KotlinDocComment(kdoc.contentTag, ResolveDocContext(ktElement))
    }
}

object JavaDocCommentCreator : DocCommentCreator {
    override fun create(element: PsiNamedElement): DokkaDocComment? {
        val psiDocComment = (element as? PsiDocCommentOwner)?.docComment ?: return null
        return JavaDocComment(psiDocComment)
    }
}

fun findClosestDocComment(element: PsiNamedElement?, logger: DokkaLogger): DokkaDocComment? {
    if (element == null) return null
    JavaDocCommentCreator.create(element)?.run { return this }
    DescriptorKotlinDocCommentCreator.create(element)?.run { return this }

    if (element is PsiMethod) {
        val superMethods = element.findSuperMethodsOrEmptyArray(logger)
        if (superMethods.isEmpty()) return null

        if (superMethods.size == 1) {
            return findClosestDocComment(superMethods.single(), logger)
        }

        val superMethodDocumentation = superMethods.map { method -> findClosestDocComment(method, logger) }.distinct()
        if (superMethodDocumentation.size == 1) {
            return superMethodDocumentation.single()
        }

        logger.debug(
            "Conflicting documentation for ${DRI.from(element)}" +
                "${superMethods.map { DRI.from(it) }}",
        )

        // Prioritize super class over interface
        val indexOfSuperClass = superMethods.indexOfFirst { method ->
            val parent = method.parent
            if (parent is PsiClass) {
                !parent.isInterface
            } else {
                false
            }
        }

        return if (indexOfSuperClass >= 0) {
            superMethodDocumentation[indexOfSuperClass]
        } else {
            superMethodDocumentation.first()
        }
    }
    return element.children.firstIsInstanceOrNull<PsiDocComment>()?.let { JavaDocComment(it) }
}

fun PsiDocTag.resolveToElement(): PsiElement? =
    dataElements
        .firstOrNull()
        ?.firstChild
        ?.referenceElementOrSelf()
        ?.resolveToGetDri()

fun PsiElement.referenceElementOrSelf(): PsiElement? =
    if (node.elementType == JavaDocElementType.DOC_REFERENCE_HOLDER) {
        PsiTreeUtil.findChildOfType(this, PsiJavaCodeReferenceElement::class.java)
    } else {
        this
    }

fun PsiElement.resolveToGetDri(): PsiElement? = reference?.resolve()

/**
 * Gets the fully qualified path of a linkable target.
 * If it's an extension function/property, the receiver is ignored.
 */
val DRI.fullyQualifiedPath: String
    get() = listOf(
        packageName?.split('.').orEmpty(),
        classNames?.split('.').orEmpty(),
        listOfNotNull(callable?.name),
    ).flatten().joinToString(".")

/**
 * Gets the fully qualified path of a linkable target that is an extension
 * function/property from the perspective of the receiver. So
 * `com.something.Receiver.extension`
 */
val DRI.fullyQualifiedExtensionPath: String?
    get() = callable?.receiver?.let { receiver ->
        listOf(
            receiver.path.split('.'),
            listOf(callable!!.name),
        ).flatten().joinToString(".")
    }

val DRI.paths: List<String>
    get() = listOfNotNull(fullyQualifiedPath, fullyQualifiedExtensionPath)

val TypeReference.path: String
    get() = when (this) {
        is TypeConstructor -> fullyQualifiedName
        is JavaClassReference -> name
        is Nullable -> wrapped.path
        else -> toString()
    }

val ImportPath.hasStar: Boolean
    get() = isAllUnder

val DocumentableSource.programmingLanguage: ProgrammingLanguage
    get() = when (this::class.qualifiedName) {
        PsiDocumentableSource::class.qualifiedName -> ProgrammingLanguage.JAVA
        "org.jetbrains.dokka.analysis.kotlin.symbols.services.KtPsiDocumentableSource" -> ProgrammingLanguage.KOTLIN
        else -> error("Unknown source type: ${this::class.simpleName}")
    }

/**
 * Collects the imports from this [DocumentableSource] as [ImportPath]s, irrespective of the programming language.
 */
fun DocumentableSource.getImports(): List<ImportPath> =
    buildList {
        when (programmingLanguage) {
            ProgrammingLanguage.JAVA -> {
                val psiFile = psi?.containingFile as? PsiJavaFile

                val implicitImports = psiFile?.implicitlyImportedPackages?.toList().orEmpty()
                val writtenImports = psiFile
                    ?.importList
                    ?.allImportStatements
                    ?.toList()
                    .orEmpty()

                for (import in implicitImports) {
                    this += ImportPath(
                        fqName = FqName(import),
                        isAllUnder = true,
                    )
                }

                for (import in writtenImports) {
                    val qualifiedName = import.importReference?.qualifiedName ?: continue
                    this += ImportPath(
                        fqName = FqName(qualifiedName),
                        isAllUnder = import.isOnDemand,
                    )
                }
            }

            ProgrammingLanguage.KOTLIN -> {
                val writtenImports = psi
                    ?.containingFile
                    .let { it as? KtFile }
                    ?.importDirectives
                    ?.mapNotNull { it.importPath }
                    ?: emptyList()

                this += writtenImports

                val implicitImports = listOf(
                    "kotlin",
                    "kotlin.annotation",
                    "kotlin.collections",
                    "kotlin.comparisons",
                    "kotlin.io",
                    "kotlin.ranges",
                    "kotlin.sequences",
                    "kotlin.text",
                    "kotlin.math",
                )

                for (import in implicitImports) {
                    this += ImportPath(
                        fqName = FqName(import),
                        isAllUnder = true,
                    )
                }
            }
        }
    }

fun ImportPath.toSimpleImportPath(): SimpleImportPath =
    SimpleImportPath(
        fqName = fqName.toUnsafe().render(),
        isAllUnder = isAllUnder,
        alias = alias?.asString(),
    )

fun AnnotationParameterValue.getValue(): Any? =
    when (this) {
        is StringValue -> value

        is BooleanValue -> value

        is NullValue -> null

        is DoubleValue -> value

        is FloatValue -> value

        is LongValue -> value

        is IntValue -> value

        is LiteralValue -> text()

        is ClassValue -> classDRI.fullyQualifiedPath

        is EnumValue -> enumDri.fullyQualifiedPath

        is ArrayValue -> value.map { it.getValue() }

        is AnnotationValue -> AnnotationWrapper(
            fullyQualifiedPath = annotation.dri.fullyQualifiedPath,
            arguments = annotation.params.entries.map { (name, paramValue) ->
                name to paramValue.getValue()
            },
        )
    }
