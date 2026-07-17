package nl.jolanrensen.kodex.utils

import java.io.ObjectOutputStream
import java.io.OutputStream
import java.io.Serializable

/**
 * Serializable stand-in for [IntRange], which does not itself implement [Serializable].
 *
 * On the write side, instances of [IntRange] anywhere in the object graph are swapped for
 * [SerializableIntRange] via [IntRangeReplacingObjectOutputStream.replaceObject].
 * On the read side, [readResolve] converts them back to [IntRange] automatically, so no
 * custom `ObjectInputStream` is needed.
 */
class SerializableIntRange(val first: Int, val last: Int) : Serializable {
    fun readResolve(): Any = first..last

    companion object {
        const val serialVersionUID: Long = 1L
    }
}

/**
 * Object output stream that transparently replaces any [IntRange] with a [SerializableIntRange]
 * so that graphs containing [IntRange] can be serialized.
 */
internal class IntRangeReplacingObjectOutputStream(out: OutputStream) : ObjectOutputStream(out) {
    init {
        enableReplaceObject(true)
    }

    override fun replaceObject(obj: Any?): Any? =
        when (obj) {
            is IntRange -> SerializableIntRange(obj.first, obj.last)
            else -> obj
        }
}
