package nl.jolanrensen.kodex.utils

import org.jgrapht.graph.DefaultEdge
import org.jgrapht.graph.SimpleDirectedGraph
import org.jgrapht.graph.builder.GraphBuilder
import kotlin.experimental.ExperimentalTypeInference

@Suppress("UNCHECKED_CAST")
fun <T : Any> emptySimpleDirectedGraph(): SimpleDirectedGraph<T, Edge<T>> =
    SimpleDirectedGraph<T, Edge<T>>(Edge::class.java as Class<out Edge<T>>)

@OptIn(ExperimentalTypeInference::class)
fun <T : Any> buildSimpleDirectedGraph(
    @BuilderInference block: GraphBuilder<T, Edge<T>, out SimpleDirectedGraph<T, Edge<T>>>.() -> Unit,
): SimpleDirectedGraph<T, Edge<T>> =
    SimpleDirectedGraph.createBuilder<T, Edge<T>>(Edge::class.java as Class<out Edge<T>>)
        .apply(block)
        .build()

data class Edge<T : Any>(val from: T, val to: T) : DefaultEdge() {
    override fun getSource(): Any = from

    override fun getTarget(): Any = to
}
