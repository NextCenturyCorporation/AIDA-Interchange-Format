import unittest
import sys
sys.path.append('../')
from rdflib import URIRef, Literal, XSD
from aida_interchange.aida_rdf_ontologies import AIDA_PROGRAM_ONTOLOGY, AIDA_ANNOTATION
import aifutils

class InvalidExamples(unittest.TestCase):

    def test_confidence_outside_of_zero_one(self):
        g = aifutils.make_graph()
        system = aifutils.make_system_with_URI(g, "http://test.edu/testSystem")

        entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)

        aifutils.mark_type(g, "http://www.test.org/assertions/1",
                           #illegal confidence value - not in [0.0, 1.0]
                           entity, AIDA_PROGRAM_ONTOLOGY.Person, system, 100.0)


    def test_entity_missing_type(self):
        # having mulitple type assertions in case of uncertainty is ok, but there must alsways be at
        # least one type assertion
        g = aifutils.make_graph()
        system = aifutils.make_system_with_URI(g, "http://www.test.edu/testSytem")

        aifutils.make_entity(g, "http://www.test.edu/entities/1", system)


    def test_event_missing_type(self):
        # having mulitple type assertions in case of uncertainty is ok, but there must alsways be at
        # least one type assertion
        g = aifutils.make_graph()
        system = aifutils.make_system_with_URI(g, "http://www.test.edu/testSytem")

        aifutils.make_event(g, "http://www.test.edu/events/1", system)


    def test_non_type_used_as_type(self):
        g = aifutils.make_graph()
        system = aifutils.make_system_with_URI(g, "http://www.test.edu/testSystem")

        entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)

        aifutils.mark_type(g, "http://www.test.edu/typeAssertion/1", entity,
                           # use a blank node as teh bogus entity type
                           BNode(), system, 1.0)


    def test_justification_missing_confidence(self):
        g = aifutils.make_graph()
        system = aifutils.make_system_with_URI(g, "http://test.edu/testSystem")

        entity = aifutils.make_entity(g, "http://www.test.edu/events/1", system)

        # create justification without the required confidence
        justification = BNode()
        g.add((justification, RDF.type, AIDA_ANNOTATION.TextJustification))
        g.add((justification, AIDA_ANNOTATION.source, Literal("FOO", datatype=XSD.string)))
        g.add((justification, AIDA_ANNOTATION.startOffset, Literal(14, datatype=XSD.integer)))
        g.add((justification, AIDA_ANNOTATION.endOffset, Literal(56, datatype=XSD.integer)))
        g.add((justification, AIDA_ANNOTATION.system, system))
        g.add((entity, AIDA_ANNOTATION.justifiedBy, justification))
