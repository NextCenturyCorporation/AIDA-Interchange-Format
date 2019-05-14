package com.ncc.aif;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

/**
 * The non-domain-specific portion of the AIDA ontology.
 */
public final class AidaAnnotationOntology {

    public static final String NAMESPACE = "https://tac.nist.gov/tracks/SM-KBP/2019/ontologies/InterchangeOntology#";

    // properties
    public static final Property SYSTEM_PROPERTY = ResourceFactory.createProperty(NAMESPACE + "system");
    public static final Property NAME_PROPERTY = ResourceFactory.createProperty(NAMESPACE + "hasName");
    public static final Property TEXT_VALUE_PROPERTY = ResourceFactory.createProperty(NAMESPACE + "textValue");
    public static final Property NUMERIC_VALUE_PROPERTY = ResourceFactory.createProperty(NAMESPACE + "numericValue");
    public static final Property IMPORTANCE_PROPERTY = ResourceFactory.createProperty(NAMESPACE + "importance");
    public static final Property CONFIDENCE = ResourceFactory.createProperty(NAMESPACE + "confidence");
    public static final Property CONFIDENCE_VALUE = ResourceFactory.createProperty(NAMESPACE + "confidenceValue");
    public static final Property JUSTIFIED_BY = ResourceFactory.createProperty(NAMESPACE + "justifiedBy");
    public static final Property CONTAINED_JUSTIFICATION =
            ResourceFactory.createProperty(NAMESPACE + "containedJustification");
    public static final Property SOURCE = ResourceFactory.createProperty(NAMESPACE + "source");
    public static final Property SOURCE_DOCUMENT = ResourceFactory.createProperty(NAMESPACE + "sourceDocument");
    public static final Property START_OFFSET = ResourceFactory.createProperty(NAMESPACE + "startOffset");
    public static final Property END_OFFSET_INCLUSIVE = ResourceFactory.createProperty(NAMESPACE
            + "endOffsetInclusive");
    public static final Property START_TIMESTAMP = ResourceFactory.createProperty(NAMESPACE + "startTimestamp");
    public static final Property END_TIMESTAMP = ResourceFactory.createProperty(NAMESPACE
            + "endTimestamp");
    public static final Property BOUNDING_BOX_PROPERTY = ResourceFactory.createProperty(NAMESPACE + "boundingBox");
    public static final Property BOUNDING_BOX_UPPER_LEFT_X = ResourceFactory.createProperty(
            NAMESPACE + "boundingBoxUpperLeftX");
    public static final Property BOUNDING_BOX_UPPER_LEFT_Y = ResourceFactory.createProperty(
            NAMESPACE + "boundingBoxUpperLeftY");
    public static final Property BOUNDING_BOX_LOWER_RIGHT_X = ResourceFactory.createProperty(
            NAMESPACE + "boundingBoxLowerRightX");
    public static final Property BOUNDING_BOX_LOWER_RIGHT_Y = ResourceFactory.createProperty(
            NAMESPACE + "boundingBoxLowerRightY");
    public static final Property KEY_FRAME = ResourceFactory.createProperty(NAMESPACE + "keyFrame");
    public static final Property SHOT = ResourceFactory.createProperty(NAMESPACE + "shot");
    public static final Property LINK = ResourceFactory.createProperty(NAMESPACE + "link");
    public static final Property LINK_TARGET = ResourceFactory.createProperty(NAMESPACE + "linkTarget");
    public static final Property PROTOTYPE = ResourceFactory.createProperty(NAMESPACE + "prototype");
    public static final Property HANDLE = ResourceFactory.createProperty(NAMESPACE + "handle");
    public static final Property CLUSTER_PROPERTY = ResourceFactory.createProperty(NAMESPACE + "cluster");
    public static final Property CLUSTER_MEMBER = ResourceFactory.createProperty(NAMESPACE + "clusterMember");
    public static final Property GRAPH_CONTAINS = ResourceFactory.createProperty(NAMESPACE + "subgraphContains");
    public static final Property ALTERNATIVE_PROPERTY = ResourceFactory.createProperty(NAMESPACE + "alternative");
    public static final Property ALTERNATIVE_GRAPH_PROPERTY = ResourceFactory.createProperty(NAMESPACE
            + "alternativeGraph");
    public static final Property NONE_OF_THE_ABOVE_PROPERTY =
            ResourceFactory.createProperty(NAMESPACE + "noneOfTheAbove");
    public static final Property DEPENDS_ON_HYPOTHESIS =
            ResourceFactory.createProperty(NAMESPACE + "dependsOnHypothesis");
    public static final Property HYPOTHESIS_CONTENT_PROPERTY =
            ResourceFactory.createProperty(NAMESPACE + "hypothesisContent");
    public static final Property PRIVATE_DATA_PROPERTY =
            ResourceFactory.createProperty(NAMESPACE + "privateData");
    public static final Property JSON_CONTENT_PROPERTY =
            ResourceFactory.createProperty(NAMESPACE + "jsonContent");
    public static final Property INFORMATIVE_JUSTIFICATION =
            ResourceFactory.createProperty(NAMESPACE + "informativeJustification");
    public static final Property LDC_TIME_PROPERTY =
            ResourceFactory.createProperty(NAMESPACE + "ldcTime");
    public static final Property LDC_TIME_START =
            ResourceFactory.createProperty(NAMESPACE + "start");
    public static final Property LDC_TIME_END =
            ResourceFactory.createProperty(NAMESPACE + "end");
    public static final Property LDC_TIME_TYPE =
            ResourceFactory.createProperty(NAMESPACE + "timeType");
    public static final Property LDC_TIME_YEAR =
            ResourceFactory.createProperty(NAMESPACE + "year");
    public static final Property LDC_TIME_MONTH =
            ResourceFactory.createProperty(NAMESPACE + "month");
    public static final Property LDC_TIME_DAY =
            ResourceFactory.createProperty(NAMESPACE + "day");

    // classes
    public static final Resource SUBGRAPH_CLASS = ResourceFactory.createResource(NAMESPACE + "Subgraph");
    public static final Resource SYSTEM_CLASS = ResourceFactory.createResource(NAMESPACE + "System");
    public static final Resource ENTITY_CLASS = ResourceFactory.createResource(NAMESPACE + "Entity");
    public static final Resource EVENT_CLASS = ResourceFactory.createResource(NAMESPACE + "Event");
    public static final Resource RELATION_CLASS = ResourceFactory.createResource(NAMESPACE + "Relation");
    public static final Resource CONFIDENCE_CLASS = ResourceFactory.createResource(NAMESPACE + "Confidence");
    public static final Resource TEXT_JUSTIFICATION_CLASS =
            ResourceFactory.createResource(NAMESPACE + "TextJustification");
    public static final Resource BOUNDING_BOX_CLASS =
            ResourceFactory.createResource(NAMESPACE + "BoundingBox");
    public static final Resource IMAGE_JUSTIFICATION_CLASS =
            ResourceFactory.createResource(NAMESPACE + "ImageJustification");
    public static final Resource AUDIO_JUSTIFICATION_CLASS =
            ResourceFactory.createResource(NAMESPACE + "AudioJustification");
    public static final Resource KEYFRAME_VIDEO_JUSTIFICATION_CLASS =
            ResourceFactory.createResource(NAMESPACE + "KeyFrameVideoJustification");
    public static final Resource SHOT_VIDEO_JUSTIFICATION_CLASS =
            ResourceFactory.createResource(NAMESPACE + "ShotVideoJustification");
    public static final Resource COMPOUND_JUSTIFICATION_CLASS =
            ResourceFactory.createResource(NAMESPACE + "CompoundJustification");
    public static final Resource LINK_ASSERTION_CLASS = ResourceFactory.createResource(NAMESPACE + "LinkAssertion");
    public static final Resource SAME_AS_CLUSTER_CLASS = ResourceFactory.createResource(NAMESPACE + "SameAsCluster");
    public static final Resource CLUSTER_MEMBERSHIP_CLASS =
            ResourceFactory.createResource(NAMESPACE + "ClusterMembership");
    public static final Resource MUTUAL_EXCLUSION_CLASS =
            ResourceFactory.createResource(NAMESPACE + "MutualExclusion");
    public static final Resource MUTUAL_EXCLUSION_ALTERNATIVE_CLASS =
            ResourceFactory.createResource(NAMESPACE + "MutualExclusionAlternative");
    public static final Resource HYPOTHESIS_CLASS =
            ResourceFactory.createResource(NAMESPACE + "Hypothesis");
    public static final Resource PRIVATE_DATA_CLASS =
            ResourceFactory.createResource(NAMESPACE + "PrivateData");
    public static final Resource LDC_TIME_CLASS =
            ResourceFactory.createProperty(NAMESPACE + "LDCTime");
    public static final Resource LDC_TIME_COMPONENT =
            ResourceFactory.createProperty(NAMESPACE + "LDCTimeComponent");
}
