package edu.isi.gaia

import org.apache.jena.rdf.model.ResourceFactory

/**
 * The non-domain-specific portion of the AIDA ontology.
 */
object AidaAnnotationOntology {
    // URI would change from isi.edu to something else if adopted program-wide
    @JvmField
    val NAMESPACE: String = "http://www.isi.edu/aida/interchangeOntology#"

    // properties
    @JvmField
    val SYSTEM_PROPERTY = ResourceFactory.createProperty(NAMESPACE + "system")!!
    @JvmField
    val CONFIDENCE = ResourceFactory.createProperty(NAMESPACE + "confidence")!!
    @JvmField
    val CONFIDENCE_VALUE = ResourceFactory.createProperty(NAMESPACE + "confidenceValue")!!
    @JvmField
    val JUSTIFIED_BY = ResourceFactory.createProperty(NAMESPACE + "justifiedBy")!!
    @JvmField
    val SOURCE = ResourceFactory.createProperty(NAMESPACE + "source")!!
    @JvmField
    val START_OFFSET = ResourceFactory.createProperty(NAMESPACE + "startOffset")!!
    @JvmField
    val END_OFFSET_INCLUSIVE = ResourceFactory.createProperty(NAMESPACE
            + "endOffsetInclusive")!!
    @JvmField
    val START_TIMESTAMP = ResourceFactory.createProperty(NAMESPACE + "startTimestamp")!!
    @JvmField
    val END_TIMESTAMP = ResourceFactory.createProperty(NAMESPACE
            + "endTimestamp")!!
    @JvmField
    val BOUNDING_BOX_PROPERTY = ResourceFactory.createProperty(NAMESPACE + "boundingBox")!!
    @JvmField
    val BOUNDING_BOX_UPPER_LEFT_X = ResourceFactory.createProperty(
            NAMESPACE + "boundingBoxUpperLeftX")!!
    @JvmField
    val BOUNDING_BOX_UPPER_LEFT_Y = ResourceFactory.createProperty(
            NAMESPACE + "boundingBoxUpperLeftY")!!
    @JvmField
    val BOUNDING_BOX_LOWER_RIGHT_X = ResourceFactory.createProperty(
            NAMESPACE + "boundingBoxLowerRightX")!!
    @JvmField
    val BOUNDING_BOX_LOWER_RIGHT_Y = ResourceFactory.createProperty(
            NAMESPACE + "boundingBoxLowerRightY")!!
    @JvmField
    val KEY_FRAME = ResourceFactory.createProperty(NAMESPACE + "keyFrame")!!
    @JvmField
    val LINK = ResourceFactory.createProperty(NAMESPACE + "link")!!
    @JvmField
    val LINK_TARGET = ResourceFactory.createProperty(NAMESPACE + "linkTarget")!!
    // realis is currently disabled because it probably won't be used in AIDA
    //val REALIS = ResourceFactory.createProperty(NAMESPACE + "realis")!!
    //val REALIS_VALUE = ResourceFactory.createProperty(NAMESPACE + "realisValue")!!
    @JvmField
    val PROTOTYPE = ResourceFactory.createProperty(NAMESPACE + "prototype")!!
    @JvmField
    val CLUSTER_PROPERTY = ResourceFactory.createProperty(NAMESPACE + "cluster")!!
    @JvmField
    val CLUSTER_MEMBER = ResourceFactory.createProperty(NAMESPACE + "clusterMember")!!
    @JvmField
    val GRAPH_CONTAINS = ResourceFactory.createProperty(NAMESPACE + "subgraphContains")!!
    @JvmField
    val ALTERNATIVE_PROPERTY = ResourceFactory.createProperty(NAMESPACE + "alternative")!!
    @JvmField
    val ALTERNATIVE_GRAPH_PROPERTY = ResourceFactory.createProperty(NAMESPACE
            + "alternativeGraph")!!
    @JvmField
    val NONE_OF_THE_ABOVE_PROPERTY =
            ResourceFactory.createProperty(NAMESPACE + "noneOfTheAbove")!!
    @JvmField
    val DEPENDS_ON_HYPOTHESIS =
            ResourceFactory.createProperty(NAMESPACE + "dependsOnHypothesis")!!
    @JvmField
    val HYPOTHESIS_CONTENT_PROPERTY =
            ResourceFactory.createProperty(NAMESPACE + "hypothesisContent")!!
    @JvmField
    val PRIVATE_DATA_PROPERTY =
            ResourceFactory.createProperty(NAMESPACE + "privateData")!!
    @JvmField
    val JSON_CONTENT_PROPERTY =
            ResourceFactory.createProperty(NAMESPACE + "jsonContent")!!

    // classes
    @JvmField
    val SUBGRAPH_CLASS = ResourceFactory.createResource(NAMESPACE + "Subgraph")!!
    @JvmField
    val SYSTEM_CLASS = ResourceFactory.createResource(NAMESPACE + "System")!!
    @JvmField
    val ENTITY_CLASS = ResourceFactory.createResource(NAMESPACE + "Entity")!!
    @JvmField
    val EVENT_CLASS = ResourceFactory.createResource(NAMESPACE + "Event")!!
    @JvmField
    val CONFIDENCE_CLASS = ResourceFactory.createResource(NAMESPACE + "Confidence")!!
    @JvmField
    val TEXT_JUSTIFICATION_CLASS =
            ResourceFactory.createResource(NAMESPACE + "TextJustification")!!
    @JvmField
    val BOUNDING_BOX_CLASS =
            ResourceFactory.createResource(NAMESPACE + "BoundingBox")!!
    @JvmField
    val IMAGE_JUSTIFICATION_CLASS =
            ResourceFactory.createResource(NAMESPACE + "ImageJustification")!!
    @JvmField
    val AUDIO_JUSTIFICATION_CLASS =
            ResourceFactory.createResource(NAMESPACE + "AudioJustification")!!
    @JvmField
    val VIDEO_JUSTIFICATION_CLASS =
            ResourceFactory.createResource(NAMESPACE + "VideoJustification")!!
    @JvmField
    val LINK_ASSERTION_CLASS = ResourceFactory.createResource(NAMESPACE + "LinkAssertion")!!
    @JvmField
    val SAME_AS_CLUSTER_CLASS = ResourceFactory.createResource(NAMESPACE + "SameAsCluster")!!
    @JvmField
    val CLUSTER_MEMBERSHIP_CLASS =
            ResourceFactory.createResource(NAMESPACE + "ClusterMembership")!!
    @JvmField
    val MUTUAL_EXCLUSION_CLASS =
            ResourceFactory.createResource(NAMESPACE + "MutualExclusion")!!
    @JvmField
    val MUTUAL_EXCLUSION_ALTERNATIVE_CLASS =
            ResourceFactory.createResource(NAMESPACE + "MutualExclusionAlternative")!!
    @JvmField
    val HYPOTHESIS_CLASS =
            ResourceFactory.createResource(NAMESPACE + "Hypothesis")!!
    @JvmField
    val PRIVATE_DATA_CLASS =
            ResourceFactory.createResource(NAMESPACE + "PrivateData")!!
}