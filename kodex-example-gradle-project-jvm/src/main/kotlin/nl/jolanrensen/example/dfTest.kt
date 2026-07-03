package nl.jolanrensen.example

import org.jetbrains.kotlinx.dataframe.api.dataFrameOf

fun main() {
    val df = dataFrameOf("A", "B")(1, 2)
    println(df)
}
