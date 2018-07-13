package edu.isi.gaia

import org.apache.jena.rdf.model.Resource

/**
 * Memoizing class, used for implementing ontologies.
 */
class OntoMemoize<in Arg, out Result>(val f: (Arg) -> Result) : (Arg) -> Result {
    private val cache = mutableMapOf<Arg, Result>()
    override fun invoke(arg: Arg) = cache.getOrPut(arg) { f(arg) }
}

/**
 * A domain ontology.
 */
interface OntologyMapping {
    val NAMESPACE: String
    fun entityShortNames(): Set<String>
    fun entityType(ontology_type: String): Resource?

    fun relationType(relationName: String): Resource?
    fun knownRelationTypes(): Set<String>
    fun eventType(eventName: String): Resource?
    fun knownEventTypes(): Set<String>
    fun eventArgumentType(argName: String): Resource?
}
