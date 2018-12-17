package com.ncc.gaia;

import org.apache.jena.rdf.model.Resource;

import java.util.Set;

/**
 * A domain ontology.
 */
interface OntologyMapping {
    String NAMESPACE;

    Set<String> entityShortNames();
    Resource entityType(String ontology_type);

    Resource relationType(String relationName);
    Resource eventType(String eventName);
    Resource eventArgumentType(String argName);
    /**
     * Given a relation, get its two argument types in the same order as in LDC annotation.
     */
    ResourcePair relationArgumentTypes(Resource relation);

    /**
     * Is an object of this type allowed to have a name property?
     */
    boolean typeAllowedToHaveAName(Resource type);
    boolean typeAllowedToHaveTextValue(Resource type);
    boolean typeAllowedToHaveNumericValue(Resource type);
}