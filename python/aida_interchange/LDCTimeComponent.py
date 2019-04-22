from enum import Enum
from rdflib import Literal, XSD
from aida_interchange.aida_rdf_ontologies import AIDA_ANNOTATION
from aida_interchange import aifutils

class LDCTimeType(Enum):
    ON = 1
    BEFORE = 2
    AFTER = 3
    UNKONWN = 4

class LDCTimeComponent:
    """
    This class encapsulates the LDC representation of time
    """

    def __init__(self, time_type, year, month, day):
        self.time_type = time_type
        self.year = year
        self.month = month
        self.day = day


    def make_aif_time_component(g):
        time_component = aifutils._make_aif_resource(g, None, AIDA_ANNOTATION.LDCTimeComponent, None)
        g.add(time_component, AIDA_ANNOTATION.ldcTimeType, time_type.__str__())
        addInteger(g, time_component, AIDA_ANNOTATION.ldcTimeYear, year, datatype=XSD.gYear)
        addInteger(g, time_component, AIDA_ANNOTATION.ldcTimeMonth, month, datatype=XSD.gMonth)
        addInteger(g, time_component, AIDA_ANNOTATION.ldcTimeDay, day, datatype=XSD.gDay)
        return time_component


    def addInteger(g, time_component, time_property, value, literal_type):
        if value is not None:
            g.add(time_component, time_property, Literal(value, literal_type))