package nl.jolanrensen.example

interface MathH3 {

    /**
     * Computes the {@get OP} of the angle [x] given in degrees. {@comment Oh no! I mean radians!}
     *
     *  Special cases:
     *   - `{@get FUN}(NaN|+Inf|-Inf)` is `NaN`
     *
     * @param x the angle in degrees
     * @return the {@get OP} of the angle [x]
     */
    private interface CosSinTanDocs

    /**
     * @include [CosSinTanDocs]
     * @set OP cosine
     * @set FUN cos
     */
    fun cos(x: Double): Double

    /** @include [cos] */
    fun cos(x: Float): Double

    /** @include [cos] */
    fun cos(x: Int): Double

    /**
     * @include [CosSinTanDocs]
     * @set OP sine
     * @set FUN sin
     */
    fun sin(x: Double): Double

    /** @include [sin] */
    fun sin(x: Float): Double

    /** @include [sin] */
    fun sin(x: Int): Double

    /**
     * @include [CosSinTanDocs]
     * @set OP tangent
     * @set FUN tan
     */
    fun tan(x: Double): Double

    /** @include [tan] */
    fun tan(x: Float): Double

    /** @include [tan] */
    fun tan(x: Int): Double
}
