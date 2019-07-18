from enum import Enum
from rdflib import Graph, Literal, XSD, URIRef
from aida_rdf_ontologies import AIDA_ANNOTATION
import aifutils

class LDCTimeType(Enum):
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
        creates and adds a Literal to the model if a value exists

        param self: contains the time component information when LDCTimeComponent is istantiated
        param g: The underlying RDF model for the operation
        param time_component: The resource created to contain time information
        param value: the string value of month, day, or year
        param literal_type: the datatype that corrisponds to the given value
        """
        if value is not None:
            temp_literal = Literal(value)
            temp_literal._datatype = URIRef(literal_type)
            g.add((time_component, time_property, temp_literal))


    def make_aif_time_component(self, g):
        """
        creates a time component containing a Year, Month, and Day as well as a Type to clarify relative time
        
        param self: contains the time component information when LDCTimeComponent is istantiated
        param g: The underlying RDF model for the operation
        return: time_component node
        """
        time_component = aifutils._make_aif_resource(g, None, AIDA_ANNOTATION.LDCTimeComponent, None)
        g.add((time_component, AIDA_ANNOTATION.timeType, Literal(self.time_type.name, datatype=XSD.string)))
        self.add_literal(g, time_component, AIDA_ANNOTATION.year, self.year, XSD.gYear)
        self.add_literal(g, time_component, AIDA_ANNOTATION.month, self.month, XSD.gMonth)
        self.add_literal(g, time_component, AIDA_ANNOTATION.day, self.day, XSD.gDay)
        return time_component
        