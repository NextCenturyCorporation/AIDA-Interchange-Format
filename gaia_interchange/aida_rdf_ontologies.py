from rdflib import URIRef
from rdflib.namespace import ClosedNamespace

# these could be changed to darpa.mil if the interchange format is adopted program-wide
# TODO: temporarily extend these to include all ColdStart entity types - #2
# TODO: long-term, we need to make the program ontology configurable
AIDA_PROGRAM_ONTOLOGY = ClosedNamespace(
    uri=URIRef("http://www.isi.edu/aida/programOntology#"),
    terms=["Person", "Organization", "Location", "Facility", "Geopolitical"])
AIDA = ClosedNamespace(
    uri=URIRef("http://www.isi.edu/aida/interchangeOntology#"),
    terms=["system", "confidence", "confidenceValue", "justifiedBy",
           "source", "startOffset", "endOffsetInclusive", "link", "linkTarget",
           # classes
           "TextProvenance", "LinkAssertion", "RelationAssertion"])
