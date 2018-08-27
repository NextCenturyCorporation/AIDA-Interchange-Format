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
    fun eventType(eventName: String): Resource?
    fun eventArgumentType(argName: String): Resource?
    /**
     * Given a relation, get its two argument types in the same order as in LDC annotation.
     */
    fun relationArgumentTypes(relation: Resource): Pair<Resource, Resource>

    /**
     * Is an object of this type allowed to have a name property?
     */
    fun typeAllowedToHaveAName(type: Resource): Boolean

    fun typeAllowedToHaveTextValue(type: Resource): Boolean
    fun typeAllowedToHaveNumericValue(type: Resource): Boolean
}
