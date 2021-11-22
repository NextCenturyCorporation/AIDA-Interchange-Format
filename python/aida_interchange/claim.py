from enum import Enum

from rdflib import XSD, Literal, URIRef

from aida_interchange import aifutils
from aida_interchange.rdf_ontologies import interchange_ontology
from aida_interchange.claim_component import ClaimComponent


class Claim:
    """
    This class encapsulates the AIDA representation of Claim
    """

    def __init__(self):
        self._claimId = None
        self._sourceDocument = None
        self._topic = None
        self._subtopic = None
        self._claimTemplate = None
        self._xVariable = set(())
        self._naturalLanguageDescription = None
        self._claimSemantics = set(())
        self._claimer = None
        self._claimLocation = None
        self._claimMedium = None
        self._associatedKE = set(())
        self._queryId = None
        self._importance = None
        self._claimerAffiliation = set(())
        self._identicalClaims = set(())
        self._relatedClaims = set(())
        self._supportingClaims = set(())
        self._refutingClaims = set(())
        self._epistemic = None
        self._sentiment = None
        self._claimDateTime = None
        
    @property
    def queryId(self):
        return self._queryId
    @queryId.setter
    def queryId(self, x):
        self._queryId = x

    @property
    def claimId(self):
        return self._claimId
    @claimId.setter
    def claimId(self, x):
        self._claimId = x
    
    @property
    def topic(self):
        return self._topic
    @topic.setter
    def topic(self, x):
        self._topic = x

    @property
    def subtopic(self):
        return self._subtopic
    @subtopic.setter
    def subtopic(self, x):
        self._subtopic = x

    @property
    def claimTemplate(self):
        return self._claimTemplate
    @claimTemplate.setter
    def claimTemplate(self, x):
        self._claimTemplate = x

    @property
    def xVariable(self):
        return self._xVariable
    @xVariable.setter
    def addXVariable(self, x):
        self._xVariable.add(x)

    @property
    def naturalLanguageDescription(self):
        return self._naturalLanguageDescription
    @naturalLanguageDescription.setter
    def naturalLanguageDescription(self, x):
        self._naturalLanguageDescription = x

    @property
    def sourceDocument(self):
        return self._sourceDocument
    @sourceDocument.setter
    def sourceDocument(self, x):
        self._sourceDocument = x

    @property
    def claimer(self):
        return self._claimer
    @claimer.setter
    def claimer(self, x):
        self._claimer = x

    @property
    def claimLocation(self):
        return self._claimLocation
    @claimLocation.setter
    def claimLocation(self, x):
        self._claimLocation = x

    @property
    def claimMedium(self):
        return self._claimMedium
    @claimMedium.setter
    def claimMedium(self, x):
        self._claimMedium = x

    @property
    def claimSemantics(self):
        return self._claimSemantics
    @claimSemantics.setter
    def addClaimSemantics(self, x):
        self._claimSemantics.add(x)

    @property
    def associatedKE(self):
        return self._associatedKE
    @associatedKE.setter
    def addAssociatedKE(self, x):
        self._associatedKE.add(x)

    @property
    def claimDateTime(self):
        return self._claimDateTime
    @claimDateTime.setter
    def claimDateTime(self, x):
        self._claimDateTime = x

    @property
    def claimerAffiliation(self):
        return self._claimerAffiliation
    @claimerAffiliation.setter
    def addClaimerAffiliation(self, x):
        self._claimerAffiliation.add(x)

    @property
    def epistemic(self):
        return self._epistemic
    @epistemic.setter
    def epistemic(self, x):
        self._epistemic = x

    @property
    def importance(self):
        return self._importance
    @importance.setter
    def importance(self, x):
        self._importance = x

    @property
    def sentiment(self):
        return self._sentiment
    @sentiment.setter
    def sentiment(self, x):
        self._sentiment = x

    @property
    def identicalClaims(self):
        return self._identicalClaims
    @identicalClaims.setter
    def addIdenticalClaims(self, x):
        self._identicalClaims.add(x)

    @property
    def supportingClaims(self):
        return self._supportingClaims
    @supportingClaims.setter
    def addSupportingClaims(self, x):
        self._supportingClaims.add(x)

    @property
    def relatedClaims(self):
        return self._relatedClaims
    @relatedClaims.setter
    def addRelatedClaims(self, x):
        self._relatedClaims.add(x)

    @property
    def refutingClaims(self):
        return self._refutingClaims
    @refutingClaims.setter
    def addRefutingClaims(self, x):
        self._refutingClaims.add(x)

    @property
    def epistemic(self):
        return self._epistemic
    @epistemic.setter
    def epistemic(self, x):
        self._epistemic = x

    @property
    def sentiment(self):
        return self._sentiment
    @sentiment.setter
    def sentiment(self, x):
        self._sentiment = x
        
    @property
    def claimDateTime(self):
        return self._claimDateTime
    @claimDateTime.setter
    def claimDateTime(self, x):
        self._claimDateTime = x

    def add_literal(self, g, claim, property, value, literal_type):
        """
        Creates and adds a Literal to the model if a value exists

        :param ClaimComponent self
        :param rdflib.graph.Graph g: The underlying RDF model for the operation
        :param claim: The resource created to contain claim information
        :param property: interchange_ontology property
        :param value: the value
        :param literal_type: the datatype that corresponds to the given value
        """
        if value is not None:
            claim_literal = Literal(value)
            claim_literal._datatype = URIRef(literal_type)
            g.add((claim, property, claim_literal))

    def make_aif_claim(self, g, claim, system):
        #MINIMAL
        self.add_literal(g, claim, interchange_ontology.sourceDocument, self.sourceDocument, XSD.string)
        self.add_literal(g, claim, interchange_ontology.topic, self.topic, XSD.string)
        self.add_literal(g, claim, interchange_ontology.subtopic, self.subtopic, XSD.string)
        self.add_literal(g, claim, interchange_ontology.claimTemplate, self.claimTemplate, XSD.string)
        self.add_literal(g, claim, interchange_ontology.naturalLanguageDescription, self.naturalLanguageDescription, XSD.string)

        #Can be more than 1            
        for item in self.xVariable:
            # xVariable_claimcomponent = aifutils.make_claim_component(g, item.uri, item, system)
            # g.add((claim, interchange_ontology.xVariable, xVariable_claimcomponent))  
            g.add((claim, interchange_ontology.xVariable, item))
            
        # claimer_claimcomponent = aifutils.make_claim_component(g, self.claimer.uri, self.claimer, system)
        # g.add((claim, interchange_ontology.claimer, claimer_claimcomponent))  

        g.add((claim, interchange_ontology.claimer, self.claimer))  


        #Can be more than 1            
        for item in self.associatedKE:
            g.add((claim, interchange_ontology.associatedKEs, item))  

        #Can be more than 1            
        for item in self.claimSemantics:
            g.add((claim, interchange_ontology.claimSemantics, item))  

        #OPTIONALS?
        if (self.queryId != None):
            self.add_literal(g, claim, interchange_ontology.queryId, self.queryId, XSD.string)
        if (self.claimId != None):
            self.add_literal(g, claim, interchange_ontology.claimId, self.claimId, XSD.string)
        if (self.importance != None):
            self.add_literal(g, claim, interchange_ontology.importance, self.importance, XSD.double)
        if (self.claimLocation != None):
            g.add((claim, interchange_ontology.claimLocation, self.claimLocation))  
        if (self.claimMedium != None):
            g.add((claim, interchange_ontology.claimMedium, self.claimMedium))  

        #Can be more than 1            
        if (self.claimerAffiliation != None):
            for item in self.claimerAffiliation:
                g.add((claim, interchange_ontology.claimerAffiliation, item))               
        if (self.identicalClaims != None):
            for item in self.identicalClaims:
                self.add_literal(g, claim, interchange_ontology.identicalClaims, item, XSD.string)
        if (self.relatedClaims != None):
            for item in self.relatedClaims:
                self.add_literal(g, claim, interchange_ontology.relatedClaims, item, XSD.string)
        if (self.supportingClaims != None):
            for item in self.supportingClaims:
                self.add_literal(g, claim, interchange_ontology.supportingClaims, item, XSD.string)
        if (self.refutingClaims != None):
            for item in self.refutingClaims:
                self.add_literal(g, claim, interchange_ontology.refutingClaims, item, XSD.string)
        if (self.epistemic != None):
            g.add((claim, interchange_ontology.epistemic, self.epistemic))
        if (self.sentiment != None):
            g.add((claim, interchange_ontology.sentiment, self.sentiment))
        if (self.claimDateTime != None):
            g.add((claim, interchange_ontology.claimDateTime, self.claimDateTime))

        return claim
