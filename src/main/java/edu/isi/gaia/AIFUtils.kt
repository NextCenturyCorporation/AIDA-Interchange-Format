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
     * Makes a relation of type [relationType] between [firstArg] and [secondArg].
     *
     * If [confidence] is non-null the relation is marked with the given [confidence]
     *
     * @return The relaton object
     */
    @JvmStatic
    fun makeRelation(model: Model, relationUri: String, firstArg: Resource, relationType: Resource,
                     secondArg: Resource, system: Resource, confidence: Double?): Resource {
        val relation = model.createResource(relationUri)
        markSystem(relation, system)
        relation.addProperty(RDF.type, RDF.Statement)
        relation.addProperty(RDF.subject, firstArg)
        relation.addProperty(RDF.predicate, relationType)
        relation.addProperty(RDF.`object`, secondArg)

        if (confidence != null) {
            markConfidence(model, relation, confidence, system)
        }

        return relation
    }

    /**
     * Create an event
     *
     * @param [eventUri] can be any unique string.
     * @param [system] The system object for the system which created this event.
     */
    @JvmStatic
    fun makeEvent(model: Model, eventUri: String, system: Resource): Resource {
        val event = model.createResource(eventUri)!!
        event.addProperty(RDF.type, AidaAnnotationOntology.EVENT_CLASS)
        event.addProperty(AidaAnnotationOntology.SYSTEM_PROPERTY, system)
        return event
    }

    /**
     * Marks an entity as filling an argument role for an event.
     *
     * @return The created event argument assertion
     */
    @JvmStatic
    fun markAsEventArgument(model: Model, event: Resource, argumentType: Resource,
                            argumentFiller: Resource, system: Resource,
                            confidence: Double?): Resource {
        val argAssertion = model.createResource()!!
        argAssertion.addProperty(RDF.type, RDF.Statement)
        argAssertion.addProperty(RDF.subject, event)
        argAssertion.addProperty(RDF.predicate, argumentType)
        argAssertion.addProperty(RDF.`object`, argumentFiller)
        markSystem(argAssertion, system)
        if (confidence != null) {
            markConfidence(model, argAssertion, confidence = confidence, system = system)
        }
        return argAssertion
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
                 type: Resource, system: Resource, confidence: Double?): Resource {
        val typeAssertion = model.createResource(typeAssertionUri)!!
        typeAssertion.addProperty(RDF.type, RDF.Statement)
        typeAssertion.addProperty(RDF.subject, entityOrEvent)
        typeAssertion.addProperty(RDF.predicate, RDF.type)
        typeAssertion.addProperty(RDF.`object`, type)
        typeAssertion.addProperty(AidaAnnotationOntology.SYSTEM_PROPERTY, system)
        if (confidence != null) {
            markConfidence(model, typeAssertion, confidence, system)
        }
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
                              system: Resource, confidence: Double): Resource {
        return markTextJustification(model, setOf(toMarkOn), docId, startOffset,
                endOffsetInclusive, system, confidence)
    }

    /**
     * Mark multiple things as being justified by a particular snippet of text.
     *
     * @return The text justification resource created.
     */
    @JvmStatic
    fun markTextJustification(model: Model, toMarkOn: Set<Resource>, docId: String,
                              startOffset: Int, endOffsetInclusive: Int,
                              system: Resource, confidence: Double): Resource {
        require(endOffsetInclusive >= startOffset, {
            "End offset $endOffsetInclusive " +
                    "precedes start offset $startOffset"
        })
        val justification = model.createResource()
        justification.addProperty(RDF.type, AidaAnnotationOntology.TEXT_JUSTIFICATION_CLASS)
        // the document ID for the justifying source document
        justification.addProperty(AidaAnnotationOntology.SOURCE, model.createTypedLiteral(docId))
        justification.addProperty(AidaAnnotationOntology.START_OFFSET,
                model.createTypedLiteral(startOffset))
        justification.addProperty(AidaAnnotationOntology.END_OFFSET_INCLUSIVE,
                model.createTypedLiteral(endOffsetInclusive))
        justification.addProperty(AidaAnnotationOntology.SYSTEM_PROPERTY, system)
        markConfidence(model, justification, confidence, system)

        toMarkOn.forEach({ it.addProperty(AidaAnnotationOntology.JUSTIFIED_BY, justification) })
        return justification
    }

    data class Point(val x: Int, val y: Int) {
        init {
            require(x >= 0, {
                "Aida image/video coordinates must be non-negative " +
                        "but got $x"
            })
            require(y >= 0, {
                "Aida image/video coordinates must be non-negative " +
                        "but got $y"
            })
        }
    }

    data class BoundingBox(val upperLeft: Point, val lowerRight: Point) {
        init {
            require(upperLeft.x <= lowerRight.x && upperLeft.y <= lowerRight.y,
                    {
                        "Upper left of bounding box $upperLeft not above " +
                                "and to the left of lower right $lowerRight"
                    })
        }
    }

    @JvmStatic
    fun markImageJustification(model: Model, toMarkOn: Resource, docId: String,
                               boundingBox: BoundingBox, system: Resource, confidence: Double)
            : Resource {
        return markImageJustification(model, setOf(toMarkOn), docId, boundingBox,
                system, confidence)
    }

    @JvmStatic
    fun markImageJustification(model: Model, toMarkOn: Collection<Resource>, docId: String,
                               boundingBox: BoundingBox, system: Resource, confidence: Double)
            : Resource {
        val justification = model.createResource()
        justification.addProperty(RDF.type, AidaAnnotationOntology.IMAGE_JUSTIFICATION_CLASS)
        // the document ID for the justifying source document
        justification.addProperty(AidaAnnotationOntology.SOURCE, model.createTypedLiteral(docId))

        val boundingBoxResource = model.createResource()
        boundingBoxResource.addProperty(RDF.type, AidaAnnotationOntology.BOUNDING_BOX_CLASS)
        boundingBoxResource.addProperty(AidaAnnotationOntology.BOUNDING_BOX_UPPER_LEFT_X,
                model.createTypedLiteral(boundingBox.upperLeft.x))
        boundingBoxResource.addProperty(AidaAnnotationOntology.BOUNDING_BOX_UPPER_LEFT_Y,
                model.createTypedLiteral(boundingBox.upperLeft.y))
        boundingBoxResource.addProperty(AidaAnnotationOntology.BOUNDING_BOX_LOWER_RIGHT_X,
                model.createTypedLiteral(boundingBox.lowerRight.x))
        boundingBoxResource.addProperty(AidaAnnotationOntology.BOUNDING_BOX_LOWER_RIGHT_Y,
                model.createTypedLiteral(boundingBox.lowerRight.y))

        justification.addProperty(AidaAnnotationOntology.BOUNDING_BOX_PROPERTY, boundingBoxResource)
        markSystem(justification, system)
        markConfidence(model, justification, confidence, system)

        toMarkOn.forEach({ it.addProperty(AidaAnnotationOntology.JUSTIFIED_BY, justification) })
        return justification
    }

    @JvmStatic
    fun markVideoJustification(model: Model, toMarkOn: Resource, docId: String,
                               keyFrame: String,
                               boundingBox: BoundingBox, system: Resource, confidence: Double)
            : Resource {
        return markVideoJustification(model, setOf(toMarkOn), docId, keyFrame, boundingBox,
                system, confidence)
    }

    @JvmStatic
    fun markVideoJustification(model: Model, toMarkOn: Collection<Resource>, docId: String,
                               keyFrame: String,
                               boundingBox: BoundingBox, system: Resource, confidence: Double)
            : Resource {
        val justification = model.createResource()
        justification.addProperty(RDF.type, AidaAnnotationOntology.VIDEO_JUSTIFICATION_CLASS)
        // the document ID for the justifying source document
        justification.addProperty(AidaAnnotationOntology.SOURCE, model.createTypedLiteral(docId))
        justification.addProperty(AidaAnnotationOntology.KEY_FRAME,
                model.createTypedLiteral(keyFrame))

        val boundingBoxResource = model.createResource()
        boundingBoxResource.addProperty(RDF.type, AidaAnnotationOntology.BOUNDING_BOX_CLASS)
        boundingBoxResource.addProperty(AidaAnnotationOntology.BOUNDING_BOX_UPPER_LEFT_X,
                model.createTypedLiteral(boundingBox.upperLeft.x))
        boundingBoxResource.addProperty(AidaAnnotationOntology.BOUNDING_BOX_UPPER_LEFT_Y,
                model.createTypedLiteral(boundingBox.upperLeft.y))
        boundingBoxResource.addProperty(AidaAnnotationOntology.BOUNDING_BOX_LOWER_RIGHT_X,
                model.createTypedLiteral(boundingBox.lowerRight.x))
        boundingBoxResource.addProperty(AidaAnnotationOntology.BOUNDING_BOX_LOWER_RIGHT_Y,
                model.createTypedLiteral(boundingBox.lowerRight.y))

        justification.addProperty(AidaAnnotationOntology.BOUNDING_BOX_PROPERTY, boundingBoxResource)
        markSystem(justification, system)
        markConfidence(model, justification, confidence, system)

        toMarkOn.forEach({ it.addProperty(AidaAnnotationOntology.JUSTIFIED_BY, justification) })
        return justification
    }

    @JvmStatic
    fun markAudioJustification(model: Model, toMarkOn: Resource, docId: String,
                               startTimestamp: Double, endTimestamp: Double,
                               system: Resource, confidence: Double): Resource {
        return markAudioJustification(model, setOf(toMarkOn), docId, startTimestamp,
                endTimestamp, system, confidence)
    }

    @JvmStatic
    fun markAudioJustification(model: Model, toMarkOn: Set<Resource>, docId: String,
                               startTimestamp: Double, endTimestamp: Double,
                               system: Resource, confidence: Double): Resource {
        require(endTimestamp > startTimestamp, {
            "End timestamp $endTimestamp does not " +
                    "follow start timestamp $startTimestamp"
        })
        val justification = model.createResource()
        justification.addProperty(RDF.type, AidaAnnotationOntology.AUDIO_JUSTIFICATION_CLASS)
        // the document ID for the justifying source document
        justification.addProperty(AidaAnnotationOntology.SOURCE, model.createTypedLiteral(docId))
        justification.addProperty(AidaAnnotationOntology.START_TIMESTAMP,
                model.createTypedLiteral(startTimestamp))
        justification.addProperty(AidaAnnotationOntology.END_TIMESTAMP,
                model.createTypedLiteral(endTimestamp))
        justification.addProperty(AidaAnnotationOntology.SYSTEM_PROPERTY, system)
        markConfidence(model, justification, confidence, system)

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

    /**
     * Create a hypothesis
     *
     * You can then indicate that some other object depends on this hypothesis using
     * [markDependsOnHypothesis].
     *
     * @return The hypothesis resource.
     */
    @JvmStatic
    fun makeHypothesis(model: Model, hypothesisURI: String, hypothesisContent: Set<Resource>,
                       system: Resource): Resource {
        require(!hypothesisContent.isEmpty()) { "A hypothesis must have content" }
        val hypothesis = model.createResource(hypothesisURI)!!
        hypothesis.addProperty(RDF.type, AidaAnnotationOntology.HYPOTHESIS_CLASS)
        markSystem(hypothesis, system)

        val subgraph = model.createResource()
        subgraph.addProperty(RDF.type, AidaAnnotationOntology.SUBGRAPH_CLASS)
        hypothesisContent.forEach { subgraph.addProperty(AidaAnnotationOntology.GRAPH_CONTAINS, it) }
        hypothesis.addProperty(AidaAnnotationOntology.HYPOTHESIS_CONTENT_PROPERTY, subgraph)

        return hypothesis
    }

    @JvmStatic
    fun markDependsOnHypothesis(depender: Resource, hypothesis: Resource) {
        depender.addProperty(AidaAnnotationOntology.DEPENDS_ON_HYPOTHESIS, hypothesis)
    }

    @JvmStatic
    fun markPrivateData(model: Model, resource: Resource, jsonContent: String, system: Resource):
            Resource {
        val privateData = model.createResource()
        privateData.addProperty(RDF.type, AidaAnnotationOntology.PRIVATE_DATA_CLASS)
        privateData.addProperty(AidaAnnotationOntology.JSON_CONTENT_PROPERTY,
                model.createTypedLiteral(jsonContent))
        markSystem(privateData, system)

        resource.addProperty(AidaAnnotationOntology.PRIVATE_DATA_PROPERTY, privateData)

        return privateData
    }

    @JvmStatic
    fun linkToExternalKB(model: Model, toLink: Resource, externalKbId: String, system: Resource,
                         confidence: Double?): Resource {
        val linkAssertion = model.createResource()!!
        toLink.addProperty(AidaAnnotationOntology.LINK, linkAssertion)
        linkAssertion.addProperty(RDF.type, AidaAnnotationOntology.LINK_ASSERTION_CLASS)
        linkAssertion.addProperty(AidaAnnotationOntology.LINK_TARGET,
                model.createTypedLiteral(externalKbId))
        markSystem(linkAssertion, system)
        if (confidence != null) {
            markConfidence(model, linkAssertion, confidence, system)
        }
        return linkAssertion
    }
}