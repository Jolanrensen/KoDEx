package nl.jolanrensen.example

/**
 * Our data looks something like this:
 *
 * ```json
 * {@includeFile (../../../../resources/test.json)}
 * ```
 */
interface IncludeFileDemo

/**
 * Hello {@get name World}, today is {@get day unknown day}!
 *
 * @set day Friday
 */
interface GetSetDemo1

/**
 * Hello $name=World, today is ${day=unknown day}!
 *
 * @set day Friday
 */
interface GetSetDemo2

/**
 * Fibonacci in Kotlin:
 * @sample [fibonacciUsingIteration]
 */
interface SampleDemo

fun fibonacciUsingIteration(num: Int): Int {
    // SampleStart
    var a = 0
    var b = 1
    var tmp: Int
    for (i in 2..num) {
        tmp = a + b
        a = b
        b = tmp
    }
    return b
    // SampleEnd
}

/**
 * That will be \$5.00
 *
 * To filter a list in Kotlin, you can call $example.
 *
 * {@set example `list.filter { it > 1 \}`}
 */
interface EscapeCharDemo
