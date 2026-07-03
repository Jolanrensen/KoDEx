package nl.jolanrensen.kodex.documentableWrapper

data class AnnotationWrapper(val fullyQualifiedPath: String, val arguments: List<Pair<String?, Any?>>) {
    override fun toString(): String =
        if (arguments.isEmpty()) {
            "@$fullyQualifiedPath"
        } else {
            "@$fullyQualifiedPath(${
                arguments.joinToString(", ") { (key, value) ->
                    if (key == null) {
                        value.toString()
                    } else {
                        "$key = $value"
                    }
                }
            })"
        }

    val simpleName: String
        get() = fullyQualifiedPath.substringAfterLast(".")
}
