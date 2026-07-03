package nl.jolanrensen.kodex.documentableWrapper

/**
 * Unsafe and simple mirror of
 * https://github.com/JetBrains/kotlin/blob/master/compiler/frontend.common/src/org/jetbrains/kotlin/resolve/ImportPath.kt
 *
 * @property [fqName] The fully qualified name of the import without `.*`.
 * @property [isAllUnder] Whether the import is a wildcard *-import.
 * @property [alias] The alias of the import if it has any.
 */
data class SimpleImportPath(val fqName: String, val isAllUnder: Boolean, val alias: String? = null) {

    val pathStr: String
        get() = fqName + if (isAllUnder) ".*" else ""

    override fun toString(): String = pathStr + if (alias != null) " as $alias" else ""

    fun hasAlias(): Boolean = alias != null

    val importedName: String?
        get() {
            if (!isAllUnder) {
                return alias ?: fqName.takeLastWhile { it != '.' }
            }

            return null
        }
}
