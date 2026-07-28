package nl.jolanrensen.kodex.documentableWrapper

import java.io.Serializable

data class AnnotationWrapper(val fullyQualifiedPath: String, val arguments: List<Pair<String?, Any?>>) : Serializable {
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
