package nl.jolanrensen.example

/*
 * Examples from https://github.com/JetBrains/kotlin/blob/master/libraries/stdlib/common/src/kotlin/MathH.kt
 */

/**
 * Gradle plugin end results are stored at `/build/kodex`
 *
 * In most cases we can even use the Gradle plugin to generate a jar and sources-jar for us.
 */
interface MathH4 {

    /**
     * Computes the {@get OP} of the angle [x] given in radians.
     *
     *  Special cases:
     *   - `{@get FUN}(NaN|+Inf|-Inf)` is `NaN`
     *
     * @param x the angle in radians
     * @return the {@get OP} of the angle [x]
     */
    @ExcludeFromSources
    private interface CosSinTanDocs

    /** @include [CosSinTanDocs] {@set OP cosine} {@set FUN cos} */
    fun cos(x: Double): Double

    /** @include [cos] */
    fun cos(x: Float): Double

    /** @include [cos] */
    fun cos(x: Int): Double

    /** @include [CosSinTanDocs] {@set OP sine} {@set FUN sin} */
    fun sin(x: Double): Double

    /** @include [sin] */
    fun sin(x: Float): Double

    /** @include [sin] */
    fun sin(x: Int): Double

    /** @include [CosSinTanDocs] {@set OP tangent} {@set FUN tan} */
    fun tan(x: Double): Double

    /** @include [tan] */
    fun tan(x: Float): Double

    /** @include [tan] */
    fun tan(x: Int): Double
}
