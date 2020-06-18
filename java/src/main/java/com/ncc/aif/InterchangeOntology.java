package com.ncc.aif;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

// WARNING. This is a Generated File.  Please do not edit.
// This class contains variables generated from ontologies using the OntologyGeneration class
// Please refer to the README at src/main/java/com/ncc/aif/ont2javagen for more information
// Last Generated On: 06/18/2020 12:22:34
public final class InterchangeOntology {
    public static final String NAMESPACE = "https://tac.nist.gov/tracks/SM-KBP/2019/ontologies/InterchangeOntology#";
    // Classes
    public static final Resource AudioJustification = ResourceFactory.createResource(NAMESPACE + "AudioJustification");
    public static final Resource BoundingBox = ResourceFactory.createResource(NAMESPACE + "BoundingBox");
    public static final Resource ClusterMembership = ResourceFactory.createResource(NAMESPACE + "ClusterMembership");
    public static final Resource CompoundJustification = ResourceFactory.createResource(NAMESPACE + "CompoundJustification");
    public static final Resource Confidence = ResourceFactory.createResource(NAMESPACE + "Confidence");
    public static final Resource Entity = ResourceFactory.createResource(NAMESPACE + "Entity");
    public static final Resource Event = ResourceFactory.createResource(NAMESPACE + "Event");
    public static final Resource Hypothesis = ResourceFactory.createResource(NAMESPACE + "Hypothesis");
    public static final Resource ImageJustification = ResourceFactory.createResource(NAMESPACE + "ImageJustification");
    public static final Resource Justification = ResourceFactory.createResource(NAMESPACE + "Justification");
    public static final Resource JustificationTypes = ResourceFactory.createResource(NAMESPACE + "JustificationTypes");
    public static final Resource KeyFrameVideoJustification = ResourceFactory.createResource(NAMESPACE + "KeyFrameVideoJustification");
    public static final Resource LDCTime = ResourceFactory.createResource(NAMESPACE + "LDCTime");
    public static final Resource LDCTimeComponent = ResourceFactory.createResource(NAMESPACE + "LDCTimeComponent");
    public static final Resource LinkAssertion = ResourceFactory.createResource(NAMESPACE + "LinkAssertion");
    public static final Resource MutualExclusion = ResourceFactory.createResource(NAMESPACE + "MutualExclusion");
    public static final Resource MutualExclusionAlternative = ResourceFactory.createResource(NAMESPACE + "MutualExclusionAlternative");
    public static final Resource PrivateData = ResourceFactory.createResource(NAMESPACE + "PrivateData");
    public static final Resource Relation = ResourceFactory.createResource(NAMESPACE + "Relation");
    public static final Resource SameAsCluster = ResourceFactory.createResource(NAMESPACE + "SameAsCluster");
    public static final Resource ShotVideoJustification = ResourceFactory.createResource(NAMESPACE + "ShotVideoJustification");
    public static final Resource Subgraph = ResourceFactory.createResource(NAMESPACE + "Subgraph");
    public static final Resource System = ResourceFactory.createResource(NAMESPACE + "System");
    public static final Resource TextJustification = ResourceFactory.createResource(NAMESPACE + "TextJustification");
    public static final Resource VideoJustification = ResourceFactory.createResource(NAMESPACE + "VideoJustification");
    public static final Resource VideoJustificationChannel = ResourceFactory.createResource(NAMESPACE + "VideoJustificationChannel");

    // Individuals
    public static final Resource VideoJustificationChannelBoth = ResourceFactory.createResource(NAMESPACE + "VideoJustificationChannelBoth");
    public static final Resource VideoJustificationChannelPicture = ResourceFactory.createResource(NAMESPACE + "VideoJustificationChannelPicture");
    public static final Resource VideoJustificationChannelSound = ResourceFactory.createResource(NAMESPACE + "VideoJustificationChannelSound");

    // Properties
    public static final Property alternative = ResourceFactory.createProperty(NAMESPACE + "alternative");
    public static final Property alternativeGraph = ResourceFactory.createProperty(NAMESPACE + "alternativeGraph");
    public static final Property boundingBox = ResourceFactory.createProperty(NAMESPACE + "boundingBox");
    public static final Property boundingBoxLowerRightX = ResourceFactory.createProperty(NAMESPACE + "boundingBoxLowerRightX");
    public static final Property boundingBoxLowerRightY = ResourceFactory.createProperty(NAMESPACE + "boundingBoxLowerRightY");
    public static final Property boundingBoxUpperLeftX = ResourceFactory.createProperty(NAMESPACE + "boundingBoxUpperLeftX");
    public static final Property boundingBoxUpperLeftY = ResourceFactory.createProperty(NAMESPACE + "boundingBoxUpperLeftY");
    public static final Property channel = ResourceFactory.createProperty(NAMESPACE + "channel");
    public static final Property cluster = ResourceFactory.createProperty(NAMESPACE + "cluster");
    public static final Property clusterMember = ResourceFactory.createProperty(NAMESPACE + "clusterMember");
    public static final Property confidence = ResourceFactory.createProperty(NAMESPACE + "confidence");
    public static final Property confidenceValue = ResourceFactory.createProperty(NAMESPACE + "confidenceValue");
    public static final Property containedJustification = ResourceFactory.createProperty(NAMESPACE + "containedJustification");
    public static final Property day = ResourceFactory.createProperty(NAMESPACE + "day");
    public static final Property dependsOnHypothesis = ResourceFactory.createProperty(NAMESPACE + "dependsOnHypothesis");
    public static final Property end = ResourceFactory.createProperty(NAMESPACE + "end");
    public static final Property endOffsetInclusive = ResourceFactory.createProperty(NAMESPACE + "endOffsetInclusive");
    public static final Property endTimestamp = ResourceFactory.createProperty(NAMESPACE + "endTimestamp");
    public static final Property handle = ResourceFactory.createProperty(NAMESPACE + "handle");
    public static final Property hasName = ResourceFactory.createProperty(NAMESPACE + "hasName");
    public static final Property hypothesisContent = ResourceFactory.createProperty(NAMESPACE + "hypothesisContent");
    public static final Property importance = ResourceFactory.createProperty(NAMESPACE + "importance");
    public static final Property informativeJustification = ResourceFactory.createProperty(NAMESPACE + "informativeJustification");
    public static final Property jsonContent = ResourceFactory.createProperty(NAMESPACE + "jsonContent");
    public static final Property justifiedBy = ResourceFactory.createProperty(NAMESPACE + "justifiedBy");
    public static final Property keyFrame = ResourceFactory.createProperty(NAMESPACE + "keyFrame");
    public static final Property ldcTime = ResourceFactory.createProperty(NAMESPACE + "ldcTime");
    public static final Property link = ResourceFactory.createProperty(NAMESPACE + "link");
    public static final Property linkTarget = ResourceFactory.createProperty(NAMESPACE + "linkTarget");
    public static final Property month = ResourceFactory.createProperty(NAMESPACE + "month");
    public static final Property noneOfTheAbove = ResourceFactory.createProperty(NAMESPACE + "noneOfTheAbove");
    public static final Property numericValue = ResourceFactory.createProperty(NAMESPACE + "numericValue");
    public static final Property privateData = ResourceFactory.createProperty(NAMESPACE + "privateData");
    public static final Property prototype = ResourceFactory.createProperty(NAMESPACE + "prototype");
    public static final Property shot = ResourceFactory.createProperty(NAMESPACE + "shot");
    public static final Property source = ResourceFactory.createProperty(NAMESPACE + "source");
    public static final Property sourceDocument = ResourceFactory.createProperty(NAMESPACE + "sourceDocument");
    public static final Property start = ResourceFactory.createProperty(NAMESPACE + "start");
    public static final Property startOffset = ResourceFactory.createProperty(NAMESPACE + "startOffset");
    public static final Property startTimestamp = ResourceFactory.createProperty(NAMESPACE + "startTimestamp");
    public static final Property subgraphContains = ResourceFactory.createProperty(NAMESPACE + "subgraphContains");
    public static final Property system = ResourceFactory.createProperty(NAMESPACE + "system");
    public static final Property textValue = ResourceFactory.createProperty(NAMESPACE + "textValue");
    public static final Property timeType = ResourceFactory.createProperty(NAMESPACE + "timeType");
    public static final Property year = ResourceFactory.createProperty(NAMESPACE + "year");
}
