package nl.jolanrensen.example

interface MathH2 {

    /**
     * Computes the cosine of the angle [x] given in degrees.
     *
     *  Special cases:
     *   - `cos(NaN|+Inf|-Inf)` is `NaN`
     *
     * @param x the angle in degrees
     * @return the cosine of the angle [x]
     */
    fun cos(x: Double): Double

    /** @include [cos] */
    fun cos(x: Float): Double

    /** @include [cos] */
    fun cos(x: Int): Double

    /**
     * Computes the sine of the angle [x] given in degrees.
     *
     *  Special cases:
     *   - `sin(NaN|+Inf|-Inf)` is `NaN`
     *
     * @param x the angle in degrees
     * @return the sine of the angle [x]
     */
    fun sin(x: Double): Double

    /** @include [sin] */
    fun sin(x: Float): Double

    /** @include [sin] */
    fun sin(x: Int): Double

    /**
     * Computes the tangent of the angle [x] given in degrees.
     *
     *  Special cases:
     *   - `tan(NaN|+Inf|-Inf)` is `NaN`
     *
     * @param x the angle in degrees
     * @return the tangent of the angle [x]
     */
    fun tan(x: Double): Double

    /** @include [tan] */
    fun tan(x: Float): Double

    /** @include [tan] */
    fun tan(x: Int): Double
}
