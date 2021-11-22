package com.ncc.aif;

import java.util.HashSet;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

/**
 * Represent TA3 Claim (https://nextcentury.atlassian.net/wiki/spaces/AIDAC/pages/2565406734/Claims+in+AIF)
 */
public class Claim {
    private String sourceDocument;
    private String claimId;
    private String queryId;
    private Double importance;
    private String topic;
    private String subtopic;
    private String claimTemplate;
    private Set<Resource> xVariables;
    private String naturalLanguageDescription;
    private Set<Resource> claimSemantics = new HashSet<>();
    private Resource claimer;
    private Set<Resource> claimerAffiliation = new HashSet<>();
    private Resource epistemic = InterchangeOntology.EpistemicUnknown;
    private Resource sentiment = InterchangeOntology.SentimentNeutralUnknown;
    private Resource claimDateTime;
    private Resource claimLocation;
    private Resource claimMedium;
    private Set<Resource> associatedKEs = new HashSet<>();
    private Set<String> identicalClaims = new HashSet<>();
    private Set<String> relatedClaims = new HashSet<>();
    private Set<String> supportingClaims = new HashSet<>();
    private Set<String> refutingClaims = new HashSet<>();
    private Resource resource;

    public Claim setSourceDocument(String sourceDocument) {
        this.sourceDocument = sourceDocument;
        return this;
    }

    public Claim setClaimId(String claimId) {
        this.claimId = claimId;
        return this;
    }

    public Claim setQueryId(String queryId) {
        this.queryId = queryId;
        return this;
    }

    public Claim setImportance(double importance) {
        this.importance = importance;
        return this;
    }

    public Claim setTopic(String topic) {
        this.topic = topic;
        return this;
    }

    public Claim setSubtopic(String subtopic) {
        this.subtopic = subtopic;
        return this;
    }

    public Claim setClaimTemplate(String claimTemplate) {
        this.claimTemplate = claimTemplate;
        return this;
    }

    public Claim setXVariable(Set<Resource> xVariables) {
        this.xVariables = xVariables;
        return this;
    }

    public Claim setNaturalLanguageDescription(String naturalLanguageDescription) {
        this.naturalLanguageDescription = naturalLanguageDescription;
        return this;
    }

    public Claim setClaimSemantics(Set<Resource> claimSemantics) {
        this.claimSemantics = claimSemantics;
        return this;
    }

    public Claim setClaimer(Resource claimer) {
        this.claimer = claimer;
        return this;
    }

    public Claim setClaimerAffiliation(Set<Resource> claimerAffiliation) {
        this.claimerAffiliation = claimerAffiliation;
        return this;
    }

    public Claim setEpistemic(Resource epistemic) {
        this.epistemic = epistemic;
        return this;
    }

    public Claim setSentiment(Resource sentiment) {
        this.sentiment = sentiment;
        return this;
    }

    public Claim setClaimDateTime(Resource claimDateTime) {
        this.claimDateTime = claimDateTime;
        return this;
    }

    public Claim setClaimLocation(Resource claimLocation) {
        this.claimLocation = claimLocation;
        return this;
    }

    public Claim setClaimMedium(Resource claimMedium) {
        this.claimMedium = claimMedium;
        return this;
    }    
    public Claim setAssociatedKEs(Set<Resource> associatedKEs) {
        this.associatedKEs = associatedKEs;
        return this;
    }

    public Claim setIdenticalClaims(Set<String> identicalClaims) {
        this.identicalClaims = identicalClaims;
        return this;
    }

    public Claim setRelatedClaims(Set<String> relatedClaims) {
        this.relatedClaims = relatedClaims;
        return this;
    }

    public Claim setSupportingClaims(Set<String> supportingClaims) {
        this.supportingClaims = supportingClaims;
        return this;
    }

    public Claim setRefutingClaims(Set<String> refutingClaims) {
        this.refutingClaims = refutingClaims;
        return this;
    }

    public Claim addClaimSementics(Resource ke) {
        if (claimSemantics == null) {
            claimSemantics = new HashSet<>();
        }
        claimSemantics.add(ke);
        return this;
    }

    public Claim addClaimerAfilliation(Resource component) {
        if (claimerAffiliation == null) {
            claimerAffiliation = new HashSet<>();
        }
        claimerAffiliation.add(component);
        return this;
    }

    public Claim addAssociatedKE(Resource ke) {
        if (associatedKEs == null) {
            associatedKEs = new HashSet<>();
        }
        associatedKEs.add(ke);
        return this;
    }

    public Claim addIdenticalClaim(String claim) {
        if (identicalClaims == null) {
            identicalClaims = new HashSet<>();
        }
        identicalClaims.add(claim);
        return this;
    }

    public Claim addRelatedClaim(String claim) {
        if (relatedClaims == null) {
            relatedClaims = new HashSet<>();
        }
        relatedClaims.add(claim);
        return this;
    }

    public Claim addSupportingClaim(String claim) {
        if (supportingClaims == null) {
            supportingClaims = new HashSet<>();
        }
        supportingClaims.add(claim);
        return this;
    }

    public Claim addRefutingClaim(String claim) {
        if (refutingClaims == null) {
            refutingClaims = new HashSet<>();
        }
        refutingClaims.add(claim);
        return this;
    }

    public Claim addXVariable(Resource xVariable) {
        if (xVariables == null) {
            xVariables = new HashSet<>();
        }
        xVariables.add(xVariable);
        return this;
    }

    public String getSourceDocument() {
        return this.sourceDocument;
    }

    public String getClaimId() {
        return this.claimId;
    }

    public String getQueryId() {
        return this.queryId;
    }

    public double getImportance() {
        return this.importance;
    }

    public String getTopic() {
        return this.topic;
    }

    public String getSubtopic() {
        return this.subtopic;
    }

    public String getClaimTemplate() {
        return this.claimTemplate;
    }

    public Set<Resource> getXVariables() {
        return this.xVariables;
    }

    public String getNaturalLanguageDescription() {
        return this.naturalLanguageDescription;
    }

    public Set<Resource> getClaimSemantics() {
        return this.claimSemantics;
    }

    public Resource getClaimer() {
        return this.claimer;
    }

    public Set<Resource> getClaimerAffiliation() {
        return this.claimerAffiliation;
    }

    public Resource getEpistemic() {
        return this.epistemic;
    }

    public Resource getSentiment() {
        return this.sentiment;
    }

    public Resource getClaimDateTime() {
        return this.claimDateTime;
    }

    public Resource getClaimLocation() {
        return this.claimLocation;
    }

    public Resource getClaimMedium() {
        return this.claimMedium;
    }    
    public Set<Resource> getAssociatedKEs() {
        return this.associatedKEs;
    }

    public Set<String> getIdenticalClaims() {
        return this.identicalClaims;
    }

    public Set<String> getRelatedClaims() {
        return this.relatedClaims;
    }

    public Set<String> getSupportingClaims() {
        return this.supportingClaims;
    }

    public Set<String> getRefutingClaims() {
        return this.refutingClaims;
    }

    public Resource getResource() {
        return this.resource;
    }

    public Resource addToModel(Model model, String uri, Resource system) {
        // required properties
        resource = AIFUtils.makeAIFResource(model, uri, InterchangeOntology.Claim, system)
            .addProperty(InterchangeOntology.sourceDocument, sourceDocument)
            .addProperty(InterchangeOntology.topic, topic)
            .addProperty(InterchangeOntology.subtopic, subtopic)
            .addProperty(InterchangeOntology.claimTemplate, claimTemplate)
            .addProperty(InterchangeOntology.naturalLanguageDescription, naturalLanguageDescription)
            .addProperty(InterchangeOntology.claimer, claimer)
            .addProperty(InterchangeOntology.epistemic, epistemic)
            .addProperty(InterchangeOntology.sentiment, sentiment);

        if (importance != null) {
            AIFUtils.markImportance(resource, importance);
        }

        // optional properties
        AIFUtils.addOptionalProperty(resource, InterchangeOntology.claimId, claimId);
        AIFUtils.addOptionalProperty(resource, InterchangeOntology.queryId, queryId);
        AIFUtils.addOptionalProperty(resource, InterchangeOntology.claimDateTime, claimDateTime);
        AIFUtils.addOptionalProperty(resource, InterchangeOntology.claimLocation, claimLocation);
        AIFUtils.addOptionalProperty(resource, InterchangeOntology.claimMedium, claimMedium);

        // required collections
        AIFUtils.addProperties(resource, InterchangeOntology.xVariable, xVariables);
        AIFUtils.addProperties(resource, InterchangeOntology.claimSemantics, claimSemantics);
        AIFUtils.addProperties(resource, InterchangeOntology.associatedKEs, associatedKEs);

        // optional collections
        AIFUtils.addProperties(resource, InterchangeOntology.claimerAffiliation, claimerAffiliation);
        AIFUtils.addProperties(resource, InterchangeOntology.identicalClaims, identicalClaims);
        AIFUtils.addProperties(resource, InterchangeOntology.relatedClaims, relatedClaims);
        AIFUtils.addProperties(resource, InterchangeOntology.supportingClaims, supportingClaims);
        AIFUtils.addProperties(resource, InterchangeOntology.refutingClaims, refutingClaims);

        return resource;
    }
}
