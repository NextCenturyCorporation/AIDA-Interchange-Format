package edu.isi.gaia

import com.fasterxml.jackson.databind.ObjectMapper
import edu.isi.gaia.AIFUtils.SparqlQueries.TYPE_QUERY
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.apache.jena.query.QuerySolutionMap
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Resource
import org.apache.jena.tdb.TDBFactory
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.SKOS
import org.apache.jena.vocabulary.XSD
import org.slf4j.LoggerFactory
import java.util.*

/**
 * A convenient interface for creating simple AIF graphs.
 *
 * More complicated graphs will require direct manipulation of the RDF.
 *
 * @author Ryan Gabbard (USC ISI)
 */
object AIFUtils {
    /**
     * Adds common non-ontology-specific namespaces to make AIF files more readable
     */
    @JvmStatic
    fun addStandardNamespaces(model: Model) {
        model.setNsPrefix("rdf", RDF.uri)
        model.setNsPrefix("xsd", XSD.getURI())
        model.setNsPrefix("aida", AidaAnnotationOntology.NAMESPACE)
        model.setNsPrefix("skos", SKOS.uri)
    }

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
     * Mark [entity] as having the specified [name]
     */
    @JvmStatic
    fun markName(entity: Resource, name: String) {
        entity.addLiteral(AidaAnnotationOntology.NAME_PROPERTY, name)
    }

    /**
     * Mark [entity] as having the specified [textValue]
     */
    @JvmStatic
    fun markTextValue(entity: Resource, textValue: String) {
        entity.addLiteral(AidaAnnotationOntology.TEXT_VALUE_PROPERTY, textValue)
    }

    /**
     * Mark [entity] as having the specified [numericValue] as string
     */
    @JvmStatic
    fun markNumericValueAsString(entity: Resource, numericValue: String) {
        entity.addLiteral(AidaAnnotationOntology.NUMERIC_VALUE_PROPERTY, numericValue)
    }

    /**
     * Mark [entity] as having the specified [numericValue] as double floating point
     */
    @JvmStatic
    fun markNumericValueAsDouble(entity: Resource, numericValue: Number) {
        entity.addLiteral(AidaAnnotationOntology.NUMERIC_VALUE_PROPERTY, numericValue.toDouble())
    }

    /**
     * Mark [entity] as having the specified [numericValue] as long integer
     */
    @JvmStatic
    fun markNumericValueAsLong(entity: Resource, numericValue: Number) {
        entity.addLiteral(AidaAnnotationOntology.NUMERIC_VALUE_PROPERTY, numericValue.toLong())
    }

    /**
     * Create an entity.
     *
     * @param [entityUri] can be any unique string.
     * @param [system] The system object for the system which created this entity.
     */
    @JvmStatic
    fun makeEntity(model: Model, entityUri: String, system: Resource): Resource {
        return makeAIFResource(model, entityUri, AidaAnnotationOntology.ENTITY_CLASS, system)
    }

    /**
     * Create a relation
     *
     * @param [relationUri] can be any unique string.
     * @param [system] The system object for the system which created this event.
     */
    @JvmStatic
    fun makeRelation(model: Model, relationUri: String, system: Resource): Resource {
        return makeAIFResource(model, relationUri, AidaAnnotationOntology.RELATION_CLASS, system)
    }

    /**
     * Makes a relation of type [relationType] between [subjectResource] and [objectResource] in a form
     * similar to that of an event: subjects and objects are explicitly linked to relation via [subjectRole]
     * and [objectRole], respectively.
     *
     * If [confidence] is non-null the relation is marked with the given [confidence]
     *
     * @return The relaton object
     */
    @JvmStatic
    fun makeRelationInEventForm(model: Model, relationUri: String, relationType: Resource, subjectRole: Resource,
                                subjectResource: Resource, objectRole: Resource, objectResource: Resource,
                                typeAssertionUri: String, system: Resource, confidence: Double?): Resource {
        val relation = AIFUtils.makeRelation(model, relationUri, system)
        AIFUtils.markType(model, typeAssertionUri, relation, relationType, system, confidence)
        AIFUtils.markAsArgument(model, relation, subjectRole, subjectResource, system, confidence)
        AIFUtils.markAsArgument(model, relation, objectRole, objectResource, system, confidence)
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
        return makeAIFResource(model, eventUri, AidaAnnotationOntology.EVENT_CLASS, system)
    }

    /**
     * Marks an entity as filling an argument role for an event or relation.
     *
     * @return The created event or relation argument assertion
     */
    @JvmStatic
    fun markAsArgument(model: Model, eventOrRelation: Resource, argumentType: Resource,
                       argumentFiller: Resource, system: Resource,
                       confidence: Double?): Resource {

        return markAsArgument(model, eventOrRelation, argumentType, argumentFiller, system, confidence, null)
    }

    /**
     * Marks an entity as filling an argument role for an event or relation.
     *
     * @return The created event or relation argument assertion with uri
     */
    @JvmStatic
    fun markAsArgument(model: Model, eventOrRelation: Resource, argumentType: Resource,
                       argumentFiller: Resource, system: Resource,
                       confidence: Double?, uri: String?): Resource {

        val argAssertion = makeAIFResource(model, uri, RDF.Statement, system)

        argAssertion.addProperty(RDF.subject, eventOrRelation)
        argAssertion.addProperty(RDF.predicate, argumentType)
        argAssertion.addProperty(RDF.`object`, argumentFiller)
        if (confidence != null) {
            markConfidence(model, argAssertion, confidence, system)
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
     * @param [type] The type of the entity, event, or relation being asserted
     * @param [system] The system object for the system which created this entity.
     */
    @JvmStatic
    fun markType(model: Model, typeAssertionUri: String, entityOrEventOrRelation: Resource,
                 type: Resource, system: Resource, confidence: Double?): Resource {
        val typeAssertion = model.createResource(typeAssertionUri)!!
        typeAssertion.addProperty(RDF.type, RDF.Statement)
        typeAssertion.addProperty(RDF.subject, entityOrEventOrRelation)
        typeAssertion.addProperty(RDF.predicate, RDF.type)
        typeAssertion.addProperty(RDF.`object`, type)
        typeAssertion.addProperty(AidaAnnotationOntology.SYSTEM_PROPERTY, system)
        if (confidence != null) {
            markConfidence(model, typeAssertion, confidence, system)
        }
        return typeAssertion
    }

    private fun makeAIFJustification(model: Model, docId: String, classType: Resource,
                                     system: Resource, confidence: Double): Resource {
        val justification = makeAIFResource(model, null, classType, system)
        justification.addProperty(AidaAnnotationOntology.SOURCE, model.createTypedLiteral(docId))
        markConfidence(model, justification, confidence, system)
        return justification
    }

    /**
     * Mark multiple things as being justified by a particular justification
     */
    @JvmStatic
    fun markJustification(toMarkOn: Collection<Resource>, justification: Resource) {
        toMarkOn.forEach { it.addProperty(AidaAnnotationOntology.JUSTIFIED_BY, justification) }
    }

    /**
     * Create justification from a particular snippet of text.
     *
     * @return The text justification resource created.
     */
    @JvmStatic
    fun makeTextJustification(model: Model, docId: String, startOffset: Int, endOffsetInclusive: Int,
                              mentionType: Resource?, system: Resource, confidence: Double): Resource {
        require(endOffsetInclusive >= startOffset) {
            "End offset $endOffsetInclusive precedes start offset $startOffset"
        }
        require(startOffset >= 0) { "Start offset must be non-negative but got $startOffset" }
        val justification = makeAIFJustification(model, docId, AidaAnnotationOntology.TEXT_JUSTIFICATION_CLASS,
                system, confidence)
        // the document ID for the justifying source document
        justification.addProperty(AidaAnnotationOntology.START_OFFSET,
                model.createTypedLiteral(startOffset))
        justification.addProperty(AidaAnnotationOntology.END_OFFSET_INCLUSIVE,
                model.createTypedLiteral(endOffsetInclusive))
        if (mentionType != null) {
            require(mentionType in AidaAnnotationOntology.MENTION_TYPES) {
                "Mention type must be in ${AidaAnnotationOntology.MENTION_TYPES} but got $mentionType"
            }
            justification.addProperty(AidaAnnotationOntology.MENTION_TYPE, mentionType)
        }

        return justification
    }

    /**
     * Create justification from a particular snippet of text.
     *
     * This is a backwards-compatible version for when the mention type is not provided.
     */
    @JvmStatic
    fun makeTextJustification(model: Model, docId: String, startOffset: Int, endOffsetInclusive: Int,
                              system: Resource, confidence: Double): Resource {
        return makeTextJustification(model, docId, startOffset, endOffsetInclusive, null,
                system, confidence)
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
        return markTextJustification(model, setOf(toMarkOn), docId, startOffset, endOffsetInclusive, system, confidence)
    }

    /**
     * Mark multiple things as being justified by a particular snippet of text.
     *
     * @return The text justification resource created.
     */
    @JvmStatic
    fun markTextJustification(model: Model, toMarkOn: Collection<Resource>, docId: String,
                              startOffset: Int, endOffsetInclusive: Int,
                              system: Resource, confidence: Double): Resource {
        val justification = makeTextJustification(model, docId, startOffset, endOffsetInclusive, system, confidence)
        markJustification(toMarkOn, justification)
        return justification
    }

    data class Point(val x: Int, val y: Int) {
        init {
            require(x >= 0) { "Aida image/video coordinates must be non-negative but got $x" }
            require(y >= 0) { "Aida image/video coordinates must be non-negative but got $y" }
        }
    }

    data class BoundingBox(val upperLeft: Point, val lowerRight: Point) {
        init {
            require(upperLeft.x <= lowerRight.x && upperLeft.y <= lowerRight.y) {
                "Upper left of bounding box $upperLeft not above and to the left of lower right $lowerRight"
            }
        }
    }

    private fun markBoundingBox(model: Model, toMarkOn: Resource, boundingBox: BoundingBox): Resource {

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

        toMarkOn.addProperty(AidaAnnotationOntology.BOUNDING_BOX_PROPERTY, boundingBoxResource)

        return boundingBoxResource
    }

    @JvmStatic
    fun makeImageJustification(model: Model, docId: String, boundingBox: BoundingBox, system: Resource,
                               confidence: Double): Resource {
        val justification = makeAIFJustification(model, docId, AidaAnnotationOntology.IMAGE_JUSTIFICATION_CLASS,
                system, confidence)
        markBoundingBox(model, justification, boundingBox)
        return justification
    }

    @JvmStatic
    fun markImageJustification(model: Model, toMarkOn: Resource, docId: String,
                               boundingBox: BoundingBox, system: Resource, confidence: Double): Resource {
        return markImageJustification(model, setOf(toMarkOn), docId, boundingBox, system, confidence)
    }

    @JvmStatic
    fun markImageJustification(model: Model, toMarkOn: Collection<Resource>, docId: String,
                               boundingBox: BoundingBox, system: Resource, confidence: Double)
            : Resource {
        val justification = makeImageJustification(model, docId, boundingBox, system, confidence)
        markJustification(toMarkOn, justification)
        return justification
    }

    /**
     * Create a justification from something appearing in a key frame of a video.
     */
    @JvmStatic
    fun makeKeyFrameVideoJustification(model: Model, docId: String, keyFrame: String, boundingBox: BoundingBox,
                                       system: Resource, confidence: Double): Resource {
        val justification = makeAIFJustification(model, docId, AidaAnnotationOntology.KEYFRAME_VIDEO_JUSTIFICATION_CLASS,
                system, confidence)
        justification.addProperty(AidaAnnotationOntology.KEY_FRAME, model.createTypedLiteral(keyFrame))
        markBoundingBox(model, justification, boundingBox)
        return justification
    }

    /**
     * Marks a justification for something appearing in a key frame of a video.
     */
    @JvmStatic
    fun markKeyFrameVideoJustification(model: Model, toMarkOn: Resource, docId: String, keyFrame: String,
                                       boundingBox: BoundingBox, system: Resource, confidence: Double): Resource {
        return markKeyFrameVideoJustification(model, setOf(toMarkOn), docId, keyFrame, boundingBox, system, confidence)
    }

    /**
     * Marks a justification for something appearing in a key frame of a video.
     */
    @JvmStatic
    fun markKeyFrameVideoJustification(model: Model, toMarkOn: Collection<Resource>, docId: String, keyFrame: String,
                                       boundingBox: BoundingBox, system: Resource, confidence: Double): Resource {
        val justification = makeKeyFrameVideoJustification(model, docId, keyFrame, boundingBox, system, confidence)
        markJustification(toMarkOn, justification)
        return justification
    }

    /**
     * Create a justification from something appearing in a video but not in a key frame.
     */
    @JvmStatic
    fun makeShotVideoJustification(model: Model, docId: String, shotId: String, system: Resource,
                                   confidence: Double): Resource {
        val justification = makeAIFJustification(model, docId, AidaAnnotationOntology.SHOT_VIDEO_JUSTIFICATION_CLASS,
                system, confidence)
        justification.addProperty(AidaAnnotationOntology.SHOT, model.createTypedLiteral(shotId))
        return justification
    }

    /**
     * Marks a justification for something appearing in a video but not in a key frame.
     */
    @JvmStatic
    fun markShotVideoJustification(model: Model, toMarkOn: Resource, docId: String, shotId: String,
                                   system: Resource, confidence: Double): Resource {
        return markShotVideoJustification(model, setOf(toMarkOn), docId, shotId, system, confidence)
    }

    /**
     * Marks a justification for something appearing in a video but not in a key frame.
     */
    @JvmStatic
    fun markShotVideoJustification(model: Model, toMarkOn: Collection<Resource>, docId: String, shotId: String,
                                   system: Resource, confidence: Double): Resource {
        val justification = makeShotVideoJustification(model, docId, shotId, system, confidence)
        markJustification(toMarkOn, justification)
        return justification
    }

    @JvmStatic
    fun makeAudioJustification(model: Model, docId: String, startTimestamp: Double, endTimestamp: Double,
                               system: Resource, confidence: Double): Resource {
        require(endTimestamp > startTimestamp) {
            "End timestamp $endTimestamp does not follow start timestamp $startTimestamp"
        }
        val justification = makeAIFJustification(model, docId, AidaAnnotationOntology.AUDIO_JUSTIFICATION_CLASS,
                system, confidence)

        justification.addProperty(AidaAnnotationOntology.START_TIMESTAMP,
                model.createTypedLiteral(startTimestamp))
        justification.addProperty(AidaAnnotationOntology.END_TIMESTAMP,
                model.createTypedLiteral(endTimestamp))

        return justification
    }

    @JvmStatic
    fun markAudioJustification(model: Model, toMarkOn: Resource, docId: String,
                               startTimestamp: Double, endTimestamp: Double,
                               system: Resource, confidence: Double): Resource {
        return markAudioJustification(model, setOf(toMarkOn), docId, startTimestamp, endTimestamp, system, confidence)
    }

    @JvmStatic
    fun markAudioJustification(model: Model, toMarkOn: Collection<Resource>, docId: String,
                               startTimestamp: Double, endTimestamp: Double,
                               system: Resource, confidence: Double): Resource {
        val justification = makeAudioJustification(model, docId, startTimestamp, endTimestamp, system, confidence)
        markJustification(toMarkOn, justification)
        return justification
    }

    @JvmStatic
    fun markCompoundJustification(model: Model, toMarkOn: Collection<Resource>, justifications: Collection<Resource>,
                                  system: Resource, confidence: Double): Resource {
        val compoundJustification = makeAIFResource(model, null,
                AidaAnnotationOntology.COMPOUND_JUSTIFICATION_CLASS, system)
        markConfidence(model, compoundJustification, confidence, system)
        justifications.forEach { compoundJustification.addProperty(AidaAnnotationOntology.CONTAINED_JUSTIFICATION, it) }
        markJustification(toMarkOn, compoundJustification)
        return compoundJustification
    }

    /**
     * Mark a confidence value on a resource.
     */
    @JvmStatic
    fun markConfidence(model: Model, toMarkOn: Resource, confidence: Double, system: Resource) {
        val confidenceBlankNode = model.createResource()
        confidenceBlankNode.addProperty(RDF.type, AidaAnnotationOntology.CONFIDENCE_CLASS)
        confidenceBlankNode.addProperty(AidaAnnotationOntology.CONFIDENCE_VALUE, model.createTypedLiteral(confidence))
        markSystem(confidenceBlankNode, system)
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

        val mutualExclusionAssertion = makeAIFResource(model, null, AidaAnnotationOntology.MUTUAL_EXCLUSION_CLASS, system)

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
        val cluster = makeAIFResource(model, clusterUri, AidaAnnotationOntology.SAME_AS_CLUSTER_CLASS, system)
        cluster.addProperty(AidaAnnotationOntology.PROTOTYPE, prototype)
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
        val clusterMemberAssertion = makeAIFResource(model, null,
                AidaAnnotationOntology.CLUSTER_MEMBERSHIP_CLASS, system);
        clusterMemberAssertion.addProperty(AidaAnnotationOntology.CLUSTER_PROPERTY, cluster)
        clusterMemberAssertion.addProperty(AidaAnnotationOntology.CLUSTER_MEMBER, possibleClusterMember)
        markConfidence(model, clusterMemberAssertion, confidence, system)
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
        val hypothesis = makeAIFResource(model, hypothesisURI, AidaAnnotationOntology.HYPOTHESIS_CLASS, system)
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
        val privateData = makeAIFResource(model, null, AidaAnnotationOntology.PRIVATE_DATA_CLASS, system)
        privateData.addProperty(AidaAnnotationOntology.JSON_CONTENT_PROPERTY, model.createTypedLiteral(jsonContent))

        resource.addProperty(AidaAnnotationOntology.PRIVATE_DATA_PROPERTY, privateData)

        return privateData
    }

    /**
     * Private data should not contain document-level content features. Allowable private data include:
     *
     * fringe type(s) for the KE
     * a vectorized representation of the KE, which cannot grow as the number of mentions/justifications for the KE increases, and from which a raw document (or significant portions thereof) cannot be recoverable.
     * the number of documents that justify the KE
     * time stamps of justification documents
     * fringe type(s) for each image or shot, to describe features that are not represented explicitly in the seedling ontology. For example: Physical.LocatedNear.Inside(Arg1_Type=Person.Soldier, Arg2_Type=Facility.Hospital)
     *
     * The KE is not allowed to contain any strings from document text except for the strings in the HasName, NumericValue, and TextValue properties
     */
    @JvmStatic
    fun markPrivateData(model: Model, resource: Resource, vectorType: String, vectorData: List<Double>, system: Resource):
            Resource {
        val mapper = ObjectMapper()
        val jsonMap = mapOf<Any, Any>("vector_type" to vectorType, "vector_data" to vectorData)
        return AIFUtils.markPrivateData(model, resource, mapper.writeValueAsString(jsonMap), system)
    }

    @JvmStatic
    fun linkToExternalKB(model: Model, toLink: Resource, externalKbId: String, system: Resource,
                         confidence: Double?): Resource {
        val linkAssertion = makeAIFResource(model, null, AidaAnnotationOntology.LINK_ASSERTION_CLASS, system)
        toLink.addProperty(AidaAnnotationOntology.LINK, linkAssertion)
        linkAssertion.addProperty(AidaAnnotationOntology.LINK_TARGET, model.createTypedLiteral(externalKbId))
        if (confidence != null) {
            markConfidence(model, linkAssertion, confidence, system)
        }
        return linkAssertion
    }

    /**
     * Run a task on a model when the model might grown too big to fit into memory.
     *
     * This hides the setup and cleanup boilerplate for using a Jena TDB model backed by
     * a temporary directory.
     */
    @JvmStatic
    fun workWithBigModel(workFunction: (Model) -> Unit) {
        val tempDir = createTempDir()
        try {
            LoggerFactory.getLogger("main").info("Using temporary directory $tempDir for " +
                    "triple store")
            val dataset = TDBFactory.createDataset(tempDir.absolutePath)
            val model = dataset.defaultModel
            workFunction(model)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private object SparqlQueries {
        val TYPE_QUERY = QueryFactory.create("""
            PREFIX rdf: <${RDF.uri}>

            SELECT ?typeAssertion WHERE {
            ?typeAssertion a rdf:Statement .
            ?typeAssertion rdf:predicate rdf:type .
            ?typeAssertion rdf:subject ?typedObject .
        }
        """)!!
    }

    @JvmStatic
    fun getTypeAssertions(model: Model, typedObject: Resource): Set<Resource> {
        val boundVariables = QuerySolutionMap()
        boundVariables.add("typedObject", typedObject)
        val queryExecution = QueryExecutionFactory.create(TYPE_QUERY, model, boundVariables)
        val results = queryExecution.execSelect()
        return results.asSequence().map { it.get("typeAssertion").asResource() }.toSet()
    }

    @JvmStatic
    fun getConfidenceAssertions(model: Model, confidencedObject: Resource): Set<Resource> {
        return model.objectsWithProperty(confidencedObject, AidaAnnotationOntology.CONFIDENCE)
                .map { it.asResource() }.toSet()
    }

    private fun makeAIFResource(model: Model, uri: String?, classType: Resource, system: Resource): Resource {
        val resource = if (uri == null) model.createResource()!! else model.createResource(uri)!!
        resource.addProperty(RDF.type, classType)
        markSystem(resource, system)
        return resource
    }
}

/**
A strategy for generating RDF graph nodes
 */
interface IriGenerator {
    fun nextIri(): String
}

/**
 *     A node generation strategy which uses UUIDs appended to a base URI.
 */
class UuidIriGenerator(private val baseUri: String = "dummmy") : IriGenerator {
    init {
        require(baseUri.isNotEmpty()) { "Base URI cannot be empty" }
        require(!baseUri.endsWith("/")) { "Base URI cannot end in /" }
    }

    override fun nextIri(): String {
        return baseUri + '/' + UUID.randomUUID().toString()
    }
}
