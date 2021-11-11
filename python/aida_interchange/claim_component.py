from enum import Enum

from rdflib import XSD, Literal, URIRef

from aida_interchange import aifutils
from aida_interchange.rdf_ontologies import interchange_ontology


class ClaimComponent:
    """
    This class encapsulates the LDC representation of time
    """

    def __init__(self):
        self._name          = None
        self._identity      = None
        self._type          = set(())
        self._provenance    = None
        self._ke            = None

    @property
    def name(self):
        return self._name
    @name.setter
    def setName(self, x):
        self._name = x
        
    @property
    def identity(self):
        return self._identity
    @identity.setter
    def setIdentity(self, x):
        self._identity = x

    @property
    def type(self):
        return self._type
    @type.setter
    def addType(self, x):
        self._type.add(x)

    @property
    def provenance(self):
        return self._provenance
    @provenance.setter
    def setProvenance(self, x):
        self._provenance = x

    @property
    def ke(self):
        return self._ke
    @ke.setter
    def setKe(self, x):
        self._ke = x


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
        :param claim_component: The parent RDF model for the operation
        :returns: node
        """
        
        self.add_literal(g, claim_component, interchange_ontology.componentName, self.name, XSD.string)
        
        self.add_literal(g, claim_component, interchange_ontology.componentIdentity, self.identity, XSD.string)

        for item in self.type:
            self.add_literal(g, claim_component, interchange_ontology.componentType, item, XSD.string)
        
        if (self.provenance != None):
            self.add_literal(g, claim_component, interchange_ontology.componentProvenance, self.provenance, XSD.string)

        if (self.ke != None):
            g.add((claim_component, interchange_ontology.componentKE, self.ke))  

        return claim_component
