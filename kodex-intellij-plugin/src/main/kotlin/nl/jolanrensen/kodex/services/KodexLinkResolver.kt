package nl.jolanrensen.kodex.services

import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiJavaFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.GlobalSearchScopesCore
import com.intellij.psi.util.parentsOfType
import nl.jolanrensen.kodex.gradle.KodexFileContext
import org.jetbrains.kotlin.idea.stubindex.KotlinFullClassNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelFunctionFqnNameIndex
import org.jetbrains.kotlin.idea.stubindex.KotlinTopLevelPropertyFqnNameIndex
import org.jetbrains.kotlin.psi.KtClassOrObject
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.psi.KtNamedDeclaration
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import java.io.File

/**
 * Resolves KDoc-style links (like `[PivotSamples.withOnPivot_properties]`) by their (fully) qualified name
 * through the IDE's stub indices instead of through the Kotlin resolver.
 *
 * The Kotlin resolver only sees what the module of the context can see: `main` cannot reference declarations
 * of `test`. The Gradle KoDEx task does not have that restriction: it analyzes the preprocessed source set
 * and its `contextualSourceSets()` as one big [nl.jolanrensen.kodex.query.DocumentablesByPath], so `@sample`s in
 * `test` can be referenced from `main`. This resolver mirrors that by looking up the link in [scope]:
 * all visible source roots of a [KodexFileContext.Connected] file (its own and the contextual ones), or the whole
 * project when [KodexFileContext.Standalone].
 *
 * It's used as a fallback when the Kotlin resolver cannot find (visible) targets for a link,
 * see `DocProcessorService.query`.
 */
internal class KodexLinkResolver(private val project: Project) {

    /** The [GlobalSearchScope] in which links of files with the given [context] may be resolved. */
    fun scopeFor(context: KodexFileContext): GlobalSearchScope =
        when (context) {
            is KodexFileContext.Standalone -> GlobalSearchScope.projectScope(project)

            is KodexFileContext.Connected -> {
                val roots = context.visibleSourceRoots.mapNotNull { it.toVirtualFile() }
                if (roots.isEmpty()) {
                    GlobalSearchScope.EMPTY_SCOPE
                } else {
                    GlobalSearchScopesCore.directoriesScope(project, true, *roots.toTypedArray())
                }
            }
        }

    /**
     * Resolves [link] as written in a KDoc of [context] to the declarations it can refer to in [scope]:
     * the link itself as fully qualified name, the link relative to the package or the enclosing classes of
     * [context], or relative to the imports of its file.
     */
    fun resolve(link: String, context: PsiElement, scope: GlobalSearchScope): List<PsiElement> {
        if (scope == GlobalSearchScope.EMPTY_SCOPE) return emptyList()

        return try {
            candidateQualifiedNames(link, context)
                .asSequence()
                .map { resolveQualifiedName(it, scope) }
                .firstOrNull { it.isNotEmpty() }
                .orEmpty()
        } catch (_: IndexNotReadyException) {
            // indexing is in progress, the doc will be re-rendered later anyway
            emptyList()
        }
    }

    /**
     * All qualified names [link] could point to from [context], most specific first:
     * relative to the enclosing classes, the package, the imports, and finally the link itself.
     */
    private fun candidateQualifiedNames(link: String, context: PsiElement): List<String> {
        val link = link.replace("`", "").trim()
        if (link.isEmpty()) return emptyList()

        val file = context.containingFile
        val packageName = when (file) {
            is KtFile -> file.packageFqName.asString()
            is PsiJavaFile -> file.packageName
            else -> ""
        }

        return buildList {
            // enclosing classes of the context, innermost first: `Outer.Inner.link`, `Outer.link`
            context.parentsOfType<KtClassOrObject>(withSelf = true)
                .mapNotNull { it.fqName?.asString() }
                .forEach { add("$it.$link") }
            context.parentsOfType<PsiClass>(withSelf = true)
                .mapNotNull { it.qualifiedName }
                .forEach { add("$it.$link") }

            if (packageName.isNotEmpty()) add("$packageName.$link")

            addAll(qualifiedNamesUsingImports(link, file))

            add(link)
        }.distinct()
    }

    private fun qualifiedNamesUsingImports(link: String, file: PsiElement?): List<String> =
        buildList {
            when (file) {
                is KtFile -> for (import in file.importDirectives) {
                    val qualifiedName = import.importedFqName?.asString() ?: continue
                    if (import.isAllUnder) {
                        add("$qualifiedName.$link")
                    } else {
                        val importedName = import.aliasName ?: import.importedName?.asString() ?: continue
                        if (link == importedName) {
                            add(qualifiedName)
                        } else if (link.startsWith("$importedName.")) {
                            add(qualifiedName + link.removePrefix(importedName))
                        }
                    }
                }

                is PsiJavaFile -> for (import in file.importList?.importStatements.orEmpty()) {
                    val qualifiedName = import.qualifiedName ?: continue
                    if (import.isOnDemand) {
                        add("$qualifiedName.$link")
                    } else {
                        val importedName = qualifiedName.substringAfterLast('.')
                        if (link == importedName) {
                            add(qualifiedName)
                        } else if (link.startsWith("$importedName.")) {
                            add(qualifiedName + link.removePrefix(importedName))
                        }
                    }
                }

                else -> {}
            }
        }

    /**
     * Finds all declarations with the fully qualified name [fqName] in [scope]:
     * Kotlin classes/objects, top-level functions/properties, members (also of nested classes), and Java classes
     * with their members.
     */
    private fun resolveQualifiedName(fqName: String, scope: GlobalSearchScope): List<PsiElement> {
        val results = mutableListOf<PsiElement>()

        results += KotlinFullClassNameIndex.Helper[fqName, project, scope]
        results += KotlinTopLevelFunctionFqnNameIndex.Helper[fqName, project, scope]
        results += KotlinTopLevelPropertyFqnNameIndex.Helper[fqName, project, scope]
        results += JavaPsiFacade.getInstance(project).findClasses(fqName, scope)

        // members: `a.b.Class.member` or `a.b.Class.Nested.member`, so try all prefixes as class name
        val segments = fqName.split('.')
        for (split in segments.size - 1 downTo 1) {
            val classFqName = segments.subList(0, split).joinToString(".")
            val memberPath = segments.subList(split, segments.size)

            val ktClasses = KotlinFullClassNameIndex.Helper[classFqName, project, scope]
            if (ktClasses.isNotEmpty()) {
                results += ktClasses.flatMap { it.resolveMembers(memberPath) }
            }

            val javaClasses = JavaPsiFacade.getInstance(project).findClasses(classFqName, scope)
            if (javaClasses.isNotEmpty()) {
                results += javaClasses.flatMap { it.resolveMembers(memberPath) }
            }

            if (results.isNotEmpty()) break
        }

        return results.distinct()
    }

    /** Follows [memberPath] (like `["Nested", "function"]`) down from this class, also through companion objects. */
    private fun KtClassOrObject.resolveMembers(memberPath: List<String>): List<KtDeclaration> {
        var current: List<KtDeclaration> = listOf(this)
        for (name in memberPath) {
            current = current
                .filterIsInstance<KtClassOrObject>()
                .flatMap { it.memberDeclarations() }
                .filter { it is KtNamedDeclaration && it.name == name }
            if (current.isEmpty()) break
        }
        return current
    }

    private fun KtClassOrObject.memberDeclarations(): List<KtDeclaration> =
        declarations +
            declarations
                .filterIsInstance<KtObjectDeclaration>()
                .filter { it.isCompanion() }
                .flatMap { it.declarations } +
            primaryConstructorParameters.filter { it.hasValOrVar() }

    private fun PsiClass.resolveMembers(memberPath: List<String>): List<PsiElement> {
        var current: List<PsiElement> = listOf(this)
        for (name in memberPath) {
            current = current
                .filterIsInstance<PsiClass>()
                .flatMap { psiClass ->
                    psiClass.findInnerClassByName(name, false)?.let { listOf(it) }.orEmpty() +
                        psiClass.findMethodsByName(name, false).toList() +
                        listOfNotNull(psiClass.findFieldByName(name, false))
                }
            if (current.isEmpty()) break
        }
        return current
    }

    private fun String.toVirtualFile(): VirtualFile? = LocalFileSystem.getInstance().findFileByIoFile(File(this))
}
