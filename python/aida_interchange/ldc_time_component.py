from enum import Enum

from rdflib import XSD, Literal, URIRef

from aida_interchange import aifutils
from aida_interchange.rdf_ontologies import interchange_ontology


class LDCTimeType(Enum):
    """
    Enumeration of time types
    """
    ON = 1
    BEFORE = 2
    AFTER = 3
    UNKNOWN = 4

class LDCTimeComponent:
    """
    This class encapsulates the LDC representation of time
    """

    def __init__(self, time_type, year, month, day):
        self.time_type = time_type
        self.year = year
        self.month = month
        self.day = day


    def add_literal(self, g, time_component, time_property, value, literal_type):
        """
        Creates and adds a Literal to the model if a value exists

        :param LDCTimeComponent self: contains the time component information when LDCTimeComponent is instantiated
        :param rdflib.graph.Graph g: The underlying RDF model for the operation
        :param rdflib.term.BNode time_component: The resource created to contain time information
        :param time_property: AIDA_ANNOTATION.year, AIDA_ANNOTATION.month, or AIDA_ANNOTATION.day
        :param str value: the string value of month, day, or year
        :param literal_type: the datatype that corresponds to the given value
        """
        if value is not None:
            temp_literal = Literal(value)
            temp_literal._datatype = URIRef(literal_type)
            g.add((time_component, time_property, temp_literal))


    def make_aif_time_component(self, g):
        """
        Creates a time component containing a Year, Month, and Day as well as a Type to clarify relative time
        
        :param LDCTimeComponent self: contains the time component information when LDCTimeComponent is instantiated
        :param rdflib.graph.Graph g: The underlying RDF model for the operation
        :returns: time_component node
        """
        time_component = aifutils._make_aif_resource(g, None, interchange_ontology.LDCTimeComponent, None)
        g.add((time_component, interchange_ontology.timeType, Literal(self.time_type.name, datatype=XSD.string)))
        self.add_literal(g, time_component, interchange_ontology.year, self.year, XSD.gYear)
        self.add_literal(g, time_component, interchange_ontology.month, self.month, XSD.gMonth)
        self.add_literal(g, time_component, interchange_ontology.day, self.day, XSD.gDay)
        return time_component
