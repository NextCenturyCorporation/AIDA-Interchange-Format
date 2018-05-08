package edu.isi.gaia

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Resource
import org.apache.jena.vocabulary.RDF

/**
 * A convenient interface for creating simple AIF graphs.
 *
 * More complicated graphs will require direct manipulation of the RDF.
 *
 * @author Ryan Gabbard (USC ISI)
 */
object AIFUtils {
    /**
     * Create a resource representing the system which produced some data.
     *
     * Such a resource should be attached to all entities, events, event arguments, relations,
     * sentiment assertions, confidences, justifications, etc. produced by a system. You should
     * only create the system resource once; reuse the returned objects for all calls
     * to [markSystem].
     *
     * @return The created system resource.
     */
    @JvmStatic
    fun makeSystemWithURI(model: Model, systemURI: String): Resource {
        val system = model.createResource(systemURI)!!
        system.addProperty(RDF.type, AidaAnnotationOntology.SYSTEM_CLASS)
        return system
    }

    /**
     * Mark a resource as coming from the specified [system].
     */
    @JvmStatic
    fun markSystem(toMarkOn: Resource, system: Resource) {
        toMarkOn.addProperty(AidaAnnotationOntology.SYSTEM_PROPERTY, system)
    }


    /**
     * Create an entity.
     *
     * @param [entityUri] can be any unique string.
     * @param [system] The system object for the system which created this entity.
     */
    @JvmStatic
    fun makeEntity(model: Model, entityUri: String, system: Resource): Resource {
        val entity = model.createResource(entityUri)!!
        entity.addProperty(RDF.type, AidaAnnotationOntology.ENTITY_CLASS)
        entity.addProperty(AidaAnnotationOntology.SYSTEM_PROPERTY, system)
        return entity
    }

    /**
     * Mark an entity or event as having a specified type.
     *
     * This is marked with a separate assertion so that uncertainty about type can be expressed.
     * In such a case, bundle together the type assertion resources returned by this method with
     * [markAsMutuallyExclusive].
     *
     * @param [type] The type of the entity or event being asserted
     * @param [system] The system object for the system which created this entity.
     */
    @JvmStatic
    fun markType(model: Model, typeAssertionUri: String, entityOrEvent: Resource,
                 type: Resource, system: Resource): Resource {
        val typeAssertion = model.createResource(typeAssertionUri)!!
        typeAssertion.addProperty(RDF.type, RDF.Statement)
        typeAssertion.addProperty(RDF.subject, entityOrEvent)
        typeAssertion.addProperty(RDF.predicate, RDF.type)
        typeAssertion.addProperty(RDF.`object`, type)
        typeAssertion.addProperty(AidaAnnotationOntology.SYSTEM_PROPERTY, system)
        return typeAssertion
    }

    /**
     * Mark something as being justified by a particular snippet of text.
     *
     * @return The text justification resource created.
     */
    @JvmStatic
    fun markTextJustification(model: Model, toMarkOn: Resource, docId: String,
                              startOffset: Int, endOffsetInclusive: Int,
                              system: Resource): Resource {
        return markTextJustification(model, setOf(toMarkOn), docId, startOffset,
                endOffsetInclusive, system)
    }

    /**
     * Mark multiple things as being justified by a particular snippet of text.
     *
     * @return The text justification resource created.
     */
    @JvmStatic
    fun markTextJustification(model: Model, toMarkOn: Collection<Resource>, docId: String,
                              startOffset: Int, endOffsetInclusive: Int,
                              system: Resource): Resource {
        // the justification provides the evidence for our claim about the entity's type
        val justification = model.createResource()
        // there can also be video justifications, audio justifications, etc.
        justification.addProperty(RDF.type, AidaAnnotationOntology.TEXT_JUSTIFICATION_CLASS)
        // the document ID for the justifying source document
        justification.addProperty(AidaAnnotationOntology.SOURCE, model.createTypedLiteral(docId))
        justification.addProperty(AidaAnnotationOntology.START_OFFSET,
                model.createTypedLiteral(startOffset))
        justification.addProperty(AidaAnnotationOntology.END_OFFSET_INCLUSIVE,
                model.createTypedLiteral(endOffsetInclusive))
        justification.addProperty(AidaAnnotationOntology.SYSTEM_PROPERTY, system)

        toMarkOn.forEach({ it.addProperty(AidaAnnotationOntology.JUSTIFIED_BY, justification) })
        return justification
    }

    /**
     * Mark a confidence value on a resource.
     */
    @JvmStatic
    fun markConfidence(model: Model, toMarkOn: Resource, confidence: Double, system: Resource) {
        val confidenceBlankNode = model.createResource()
        confidenceBlankNode.addProperty(RDF.type, AidaAnnotationOntology.CONFIDENCE_CLASS)
        confidenceBlankNode.addProperty(AidaAnnotationOntology.CONFIDENCE_VALUE,
                model.createTypedLiteral(confidence))
        confidenceBlankNode.addProperty(AidaAnnotationOntology.SYSTEM_PROPERTY, system)
        toMarkOn.addProperty(AidaAnnotationOntology.CONFIDENCE, confidenceBlankNode)
    }

    /**
     * Mark the given resources as mutually exclusive.
     *
     * @param [alternatives] is a map from the collection of edges which form a sub-graph for
     * an alternative to the confidence associated with an alternative.
     *
     * @param [noneOfTheAboveProb] - if non-null, the given confidence will be applied to the
     * "none of the above" option.
     *
     * @return The mutual exclusion assertion.
     */
    @JvmStatic
    fun markAsMutuallyExclusive(model: Model, alternatives: Map<out Collection<Resource>, Double>,
                                system: Resource, noneOfTheAboveProb: Double? = null): Resource {
        require(alternatives.size >= 2) {
            "Must have at least two mutually exclusive things when " +
                    "making a mutual exclusion constraint, but got $alternatives"
        }

        val mutualExclusionAssertion = model.createResource()!!
        mutualExclusionAssertion.addProperty(RDF.type, AidaAnnotationOntology.MUTUAL_EXCLUSION_CLASS)
        markSystem(mutualExclusionAssertion, system)

        alternatives.forEach {
            val alternative = model.createResource()
            alternative.addProperty(RDF.type,
                    AidaAnnotationOntology.MUTUAL_EXCLUSION_ALTERNATIVE_CLASS)


            val alternativeGraph = model.createResource()
            alternativeGraph.addProperty(RDF.type, AidaAnnotationOntology.SUBGRAPH_CLASS)
            for (edge in it.key) {
                alternativeGraph.addProperty(AidaAnnotationOntology.GRAPH_CONTAINS, edge)
            }

            alternative.addProperty(AidaAnnotationOntology.ALTERNATIVE_GRAPH_PROPERTY,
                    alternativeGraph)

            markConfidence(model, alternative, it.value, system)

            mutualExclusionAssertion.addProperty(AidaAnnotationOntology.ALTERNATIVE_PROPERTY,
                    alternative)
        }

        if (noneOfTheAboveProb != null) {
            mutualExclusionAssertion.addProperty(AidaAnnotationOntology.NONE_OF_THE_ABOVE_PROPERTY,
                    model.createTypedLiteral(noneOfTheAboveProb))
        }

        return mutualExclusionAssertion
    }

    /**
     * Create a "same-as" cluster.
     *
     * A same-as cluster is used to represent multiple entities which might be the same, but we
     * aren't sure. (If we were sure, they would just be a single node).
     *
     * Every cluster requires a [prototype] - an entity or event that we are *certain* is in the
     * cluster.
     *
     * @return The cluster created
     */
    @JvmStatic
    fun makeClusterWithPrototype(model: Model, clusterUri: String, prototype: Resource,
                                 system: Resource): Resource {
        val cluster = model.createResource(clusterUri)!!
        cluster.addProperty(RDF.type, AidaAnnotationOntology.SAME_AS_CLUSTER_CLASS)
        cluster.addProperty(AidaAnnotationOntology.PROTOTYPE, prototype)
        markSystem(cluster, system)
        return cluster
    }

    /**
     * Mark an entity or event as a possible member of a cluster.
     *
     * @return The cluster membership assertion
     */
    @JvmStatic
    fun markAsPossibleClusterMember(model: Model, possibleClusterMember: Resource,
                                    cluster: Resource, confidence: Double,
                                    system: Resource): Resource {
        val clusterMemberAssertion = model.createResource()
        clusterMemberAssertion.addProperty(RDF.type, AidaAnnotationOntology.CLUSTER_MEMBERSHIP_CLASS)
        clusterMemberAssertion.addProperty(AidaAnnotationOntology.CLUSTER_PROPERTY, cluster)
        clusterMemberAssertion.addProperty(AidaAnnotationOntology.CLUSTER_MEMBER, possibleClusterMember)
        markConfidence(model, clusterMemberAssertion, confidence = confidence, system = system)
        markSystem(clusterMemberAssertion, system)
        return clusterMemberAssertion
    }
}