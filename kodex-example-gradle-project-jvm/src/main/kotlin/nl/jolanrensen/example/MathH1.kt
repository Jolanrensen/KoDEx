package nl.jolanrensen.example

interface MathH1 {

    /**
     * Computes the cosine of the anglle [x] given in degrees.
     *
     *  Special cases:
     *   - `cos(NaN|+Inf|-Inf)` is `NaN`
     *
     * @param x the angle in degrees
     * @return the cosine of the angle [x]
     */
    fun cos(x: Double): Double

    /**
     * Computes ...
     */
    fun cos(x: Float): Double

    fun cos(x: Int): Double

    fun sin(x: Double): Double

    fun sin(x: Float): Double

    fun sin(x: Int): Double

    fun tan(x: Double): Double

    fun tan(x: Float): Double

    fun tan(x: Int): Double
}
