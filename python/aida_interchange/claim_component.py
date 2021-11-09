from enum import Enum

from rdflib import XSD, Literal, URIRef

from aida_interchange import aifutils
from aida_interchange.rdf_ontologies import interchange_ontology


class ClaimComponent:
    """
    This class encapsulates the LDC representation of time
    """

    def __init__(self, uri,  name, identity, type, provenance=None, ke=None):
        self.uri = uri
        self.name = name
        self.identity  = identity
        self.type  = type
        self.provenance  = provenance

        #OPTIONAL
        self.componentKE  = ke


    def add_literal(self, g, claim_component, property, value, literal_type):
        """
        Creates and adds a Literal to the model if a value exists

        :param ClaimComponent self
        :param rdflib.graph.Graph g: The underlying RDF model for the operation
        :param rdflib.term.BNode claim_component: The resource created to contain claim component information
        :param time_property: AIDA_ANNOTATION.year, AIDA_ANNOTATION.month, or AIDA_ANNOTATION.day
        :param str value: the string value
        :param literal_type: the datatype that corresponds to the given value
        """
        if value is not None:
            claim_literal = Literal(value)
            claim_literal._datatype = URIRef(literal_type)
            g.add((claim_component, property,claim_literal))


    def make_aif_claim_component(self, g, claim_component):
        """
        Creates a claim component
        
        :param ClaimComponent self
        :param rdflib.graph.Graph g: The underlying RDF model for the operation
        :returns: time_component node
        """
        
        self.add_literal(g, claim_component, interchange_ontology.componentName, self.name, XSD.string)
        
        self.add_literal(g, claim_component, interchange_ontology.componentIdentity, self.identity, XSD.string)
        
        self.add_literal(g, claim_component, interchange_ontology.componentType, self.type, XSD.string)
        
        if (self.provenance != None):
            self.add_literal(g, claim_component, interchange_ontology.componentProvenance, self.provenance, XSD.string)

        if (self.componentKE != None):
            g.add((claim_component, interchange_ontology.componentKE, self.componentKE))  
            # self.add_literal(g, claim_component, interchange_ontology.componentKE, self.ke, XSD.string)

        return claim_component
