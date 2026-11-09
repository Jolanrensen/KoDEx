package nl.jolanrensen.kodex.utils

/**
 * Shorthand for `.let { if (condition) block(it) else it }`
 */
inline fun <T : R, R> T.letIf(condition: Boolean, block: (T) -> R): R = if (condition) this.let(block) else this
