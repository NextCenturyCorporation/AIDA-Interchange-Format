package com.ncc.aif;

import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
// WARNING. This is a Generated File. Please do not edit.
// This class contains variables generated from ontologies using the OntologyGeneration class
// Please refer to the README at java/src/main/java/com/ncc/aif/ont2javagen for more information
// Last generated on: 10/05/2021 17:54:29
public final class InterchangeOntology {
    public static final String NAMESPACE = "https://raw.githubusercontent.com/NextCenturyCorporation/AIDA-Interchange-Format/master/java/src/main/resources/com/ncc/aif/ontologies/InterchangeOntology#";
    // Classes
    public static final Resource Attribute = ResourceFactory.createResource(NAMESPACE + "Attribute");
    public static final Resource AudioJustification = ResourceFactory.createResource(NAMESPACE + "AudioJustification");
    public static final Resource BoundingBox = ResourceFactory.createResource(NAMESPACE + "BoundingBox");
    public static final Resource Claim = ResourceFactory.createResource(NAMESPACE + "Claim");
    public static final Resource ClaimComponent = ResourceFactory.createResource(NAMESPACE + "ClaimComponent");
    public static final Resource ClusterMembership = ResourceFactory.createResource(NAMESPACE + "ClusterMembership");
    public static final Resource CompoundJustification = ResourceFactory.createResource(NAMESPACE + "CompoundJustification");
    public static final Resource Confidence = ResourceFactory.createResource(NAMESPACE + "Confidence");
    public static final Resource Entity = ResourceFactory.createResource(NAMESPACE + "Entity");
    public static final Resource EpistemicStatus = ResourceFactory.createResource(NAMESPACE + "EpistemicStatus");
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
    public static final Resource Sentiment = ResourceFactory.createResource(NAMESPACE + "Sentiment");
    public static final Resource ShotVideoJustification = ResourceFactory.createResource(NAMESPACE + "ShotVideoJustification");
    public static final Resource Subgraph = ResourceFactory.createResource(NAMESPACE + "Subgraph");
    public static final Resource System = ResourceFactory.createResource(NAMESPACE + "System");
    public static final Resource TextJustification = ResourceFactory.createResource(NAMESPACE + "TextJustification");
    public static final Resource VideoJustification = ResourceFactory.createResource(NAMESPACE + "VideoJustification");
    public static final Resource VideoJustificationChannel = ResourceFactory.createResource(NAMESPACE + "VideoJustificationChannel");

    // Individuals
    public static final Resource EpistemicFalseCertain = ResourceFactory.createResource(NAMESPACE + "EpistemicFalseCertain");
    public static final Resource EpistemicFalseUncertain = ResourceFactory.createResource(NAMESPACE + "EpistemicFalseUncertain");
    public static final Resource EpistemicTrueCertain = ResourceFactory.createResource(NAMESPACE + "EpistemicTrueCertain");
    public static final Resource EpistemicTrueUncertain = ResourceFactory.createResource(NAMESPACE + "EpistemicTrueUncertain");
    public static final Resource EpistemicUnknown = ResourceFactory.createResource(NAMESPACE + "EpistemicUnknown");
    public static final Resource Generic = ResourceFactory.createResource(NAMESPACE + "Generic");
    public static final Resource Hedged = ResourceFactory.createResource(NAMESPACE + "Hedged");
    public static final Resource Irrealis = ResourceFactory.createResource(NAMESPACE + "Irrealis");
    public static final Resource Negated = ResourceFactory.createResource(NAMESPACE + "Negated");
    public static final Resource SentimentMixed = ResourceFactory.createResource(NAMESPACE + "SentimentMixed");
    public static final Resource SentimentNegative = ResourceFactory.createResource(NAMESPACE + "SentimentNegative");
    public static final Resource SentimentNeutralUnknown = ResourceFactory.createResource(NAMESPACE + "SentimentNeutralUnknown");
    public static final Resource SentimentPositive = ResourceFactory.createResource(NAMESPACE + "SentimentPositive");
    public static final Resource VideoJustificationChannelBoth = ResourceFactory.createResource(NAMESPACE + "VideoJustificationChannelBoth");
    public static final Resource VideoJustificationChannelPicture = ResourceFactory.createResource(NAMESPACE + "VideoJustificationChannelPicture");
    public static final Resource VideoJustificationChannelSound = ResourceFactory.createResource(NAMESPACE + "VideoJustificationChannelSound");

    // Properties
    public static final Property alternative = ResourceFactory.createProperty(NAMESPACE + "alternative");
    public static final Property alternativeGraph = ResourceFactory.createProperty(NAMESPACE + "alternativeGraph");
    public static final Property associatedKEs = ResourceFactory.createProperty(NAMESPACE + "associatedKEs");
    public static final Property attribute = ResourceFactory.createProperty(NAMESPACE + "attribute");
    public static final Property boundingBox = ResourceFactory.createProperty(NAMESPACE + "boundingBox");
    public static final Property boundingBoxLowerRightX = ResourceFactory.createProperty(NAMESPACE + "boundingBoxLowerRightX");
    public static final Property boundingBoxLowerRightY = ResourceFactory.createProperty(NAMESPACE + "boundingBoxLowerRightY");
    public static final Property boundingBoxUpperLeftX = ResourceFactory.createProperty(NAMESPACE + "boundingBoxUpperLeftX");
    public static final Property boundingBoxUpperLeftY = ResourceFactory.createProperty(NAMESPACE + "boundingBoxUpperLeftY");
    public static final Property channel = ResourceFactory.createProperty(NAMESPACE + "channel");
    public static final Property claimDateTime = ResourceFactory.createProperty(NAMESPACE + "claimDateTime");
    public static final Property claimId = ResourceFactory.createProperty(NAMESPACE + "claimId");
    public static final Property claimLocation = ResourceFactory.createProperty(NAMESPACE + "claimLocation");
    public static final Property claimSemantics = ResourceFactory.createProperty(NAMESPACE + "claimSemantics");
    public static final Property claimTemplate = ResourceFactory.createProperty(NAMESPACE + "claimTemplate");
    public static final Property claimer = ResourceFactory.createProperty(NAMESPACE + "claimer");
    public static final Property claimerAffiliation = ResourceFactory.createProperty(NAMESPACE + "claimerAffiliation");
    public static final Property cluster = ResourceFactory.createProperty(NAMESPACE + "cluster");
    public static final Property clusterMember = ResourceFactory.createProperty(NAMESPACE + "clusterMember");
    public static final Property componentIdentity = ResourceFactory.createProperty(NAMESPACE + "componentIdentity");
    public static final Property componentName = ResourceFactory.createProperty(NAMESPACE + "componentName");
    public static final Property componentProvenance = ResourceFactory.createProperty(NAMESPACE + "componentProvenance");
    public static final Property componentType = ResourceFactory.createProperty(NAMESPACE + "componentType");
    public static final Property confidence = ResourceFactory.createProperty(NAMESPACE + "confidence");
    public static final Property confidenceValue = ResourceFactory.createProperty(NAMESPACE + "confidenceValue");
    public static final Property containedJustification = ResourceFactory.createProperty(NAMESPACE + "containedJustification");
    public static final Property day = ResourceFactory.createProperty(NAMESPACE + "day");
    public static final Property dependsOnHypothesis = ResourceFactory.createProperty(NAMESPACE + "dependsOnHypothesis");
    public static final Property end = ResourceFactory.createProperty(NAMESPACE + "end");
    public static final Property endOffsetInclusive = ResourceFactory.createProperty(NAMESPACE + "endOffsetInclusive");
    public static final Property endTimestamp = ResourceFactory.createProperty(NAMESPACE + "endTimestamp");
    public static final Property epistemic = ResourceFactory.createProperty(NAMESPACE + "epistemic");
    public static final Property handle = ResourceFactory.createProperty(NAMESPACE + "handle");
    public static final Property hasName = ResourceFactory.createProperty(NAMESPACE + "hasName");
    public static final Property hypothesisContent = ResourceFactory.createProperty(NAMESPACE + "hypothesisContent");
    public static final Property identicalClaims = ResourceFactory.createProperty(NAMESPACE + "identicalClaims");
    public static final Property importance = ResourceFactory.createProperty(NAMESPACE + "importance");
    public static final Property informativeJustification = ResourceFactory.createProperty(NAMESPACE + "informativeJustification");
    public static final Property jsonContent = ResourceFactory.createProperty(NAMESPACE + "jsonContent");
    public static final Property justifiedBy = ResourceFactory.createProperty(NAMESPACE + "justifiedBy");
    public static final Property keyFrame = ResourceFactory.createProperty(NAMESPACE + "keyFrame");
    public static final Property ldcTime = ResourceFactory.createProperty(NAMESPACE + "ldcTime");
    public static final Property link = ResourceFactory.createProperty(NAMESPACE + "link");
    public static final Property linkTarget = ResourceFactory.createProperty(NAMESPACE + "linkTarget");
    public static final Property month = ResourceFactory.createProperty(NAMESPACE + "month");
    public static final Property naturalLanguageDescription = ResourceFactory.createProperty(NAMESPACE + "naturalLanguageDescription");
    public static final Property noneOfTheAbove = ResourceFactory.createProperty(NAMESPACE + "noneOfTheAbove");
    public static final Property numericValue = ResourceFactory.createProperty(NAMESPACE + "numericValue");
    public static final Property privateData = ResourceFactory.createProperty(NAMESPACE + "privateData");
    public static final Property prototype = ResourceFactory.createProperty(NAMESPACE + "prototype");
    public static final Property queryId = ResourceFactory.createProperty(NAMESPACE + "queryId");
    public static final Property refutingClaims = ResourceFactory.createProperty(NAMESPACE + "refutingClaims");
    public static final Property relatedClaims = ResourceFactory.createProperty(NAMESPACE + "relatedClaims");
    public static final Property sentiment = ResourceFactory.createProperty(NAMESPACE + "sentiment");
    public static final Property shot = ResourceFactory.createProperty(NAMESPACE + "shot");
    public static final Property source = ResourceFactory.createProperty(NAMESPACE + "source");
    public static final Property sourceDocument = ResourceFactory.createProperty(NAMESPACE + "sourceDocument");
    public static final Property start = ResourceFactory.createProperty(NAMESPACE + "start");
    public static final Property startOffset = ResourceFactory.createProperty(NAMESPACE + "startOffset");
    public static final Property startTimestamp = ResourceFactory.createProperty(NAMESPACE + "startTimestamp");
    public static final Property subgraphContains = ResourceFactory.createProperty(NAMESPACE + "subgraphContains");
    public static final Property subtopic = ResourceFactory.createProperty(NAMESPACE + "subtopic");
    public static final Property supportingClaims = ResourceFactory.createProperty(NAMESPACE + "supportingClaims");
    public static final Property system = ResourceFactory.createProperty(NAMESPACE + "system");
    public static final Property textValue = ResourceFactory.createProperty(NAMESPACE + "textValue");
    public static final Property timeType = ResourceFactory.createProperty(NAMESPACE + "timeType");
    public static final Property topic = ResourceFactory.createProperty(NAMESPACE + "topic");
    public static final Property xvariable = ResourceFactory.createProperty(NAMESPACE + "xvariable");
    public static final Property year = ResourceFactory.createProperty(NAMESPACE + "year");
}
