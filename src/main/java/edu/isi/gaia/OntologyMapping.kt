package edu.isi.gaia

import org.apache.jena.rdf.model.Resource

/**
 * Specifies how ColdStart type strings should be mapped to RDF IRIs.
 */
interface OntologyMapping {
    fun prefixes(): Map<Resource, String>
    fun entityType(ontology_type: String): Resource?
    fun relationType(relationName: String): Resource?
    fun eventType(eventName: String): Resource?
    fun eventArgumentType(argName: String): Resource?
    fun relationArgumentType(relationArgumentName: String): Resource?

    fun typeAllowedToHaveAName(type: Resource): Boolean
    fun typeAllowedToHaveTextValue(type: Resource): Boolean
    fun typeAllowedToHaveNumericValue(type: Resource): Boolean
}
