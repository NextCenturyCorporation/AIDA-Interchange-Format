import sys
import os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../')))
import unittest

from io import BytesIO
from rdflib import URIRef, Literal, XSD, BNode, RDF
from aida_interchange import aifutils
from aida_interchange.aida_rdf_ontologies import AIDA_PROGRAM_ONTOLOGY, AIDA_ANNOTATION


class InvalidExamples(unittest.TestCase):

    def test_confidence_outside_of_zero_one(self):
        g = aifutils.make_graph()
        system = aifutils.make_system_with_uri(g, "http://test.edu/testSystem")

        entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)

        aifutils.mark_type(g, "http://www.test.org/assertions/1",
                           #illegal confidence value - not in [0.0, 1.0]
                           entity, AIDA_PROGRAM_ONTOLOGY.Person, system, 100.0)

        self.dump_graph(g, "Invalid: Confidence outside of zero to one")


    def test_entity_missing_type(self):
        # having mulitple type assertions in case of uncertainty is ok, but there must always be at
        # least one type assertion
        g = aifutils.make_graph()
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSytem")

        aifutils.make_entity(g, "http://www.test.edu/entities/1", system)

        self.dump_graph(g, "Invalid: Entity missing type")



    def test_event_missing_type(self):
        # having mulitple type assertions in case of uncertainty is ok, but there must always be at
        # least one type assertion
        g = aifutils.make_graph()
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSytem")

        aifutils.make_event(g, "http://www.test.edu/events/1", system)

        self.dump_graph(g, "Invalid: Event missing type")


    def test_non_type_used_as_type(self):
        g = aifutils.make_graph()
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)

        aifutils.mark_type(g, "http://www.test.edu/typeAssertion/1", entity,
                           # use a blank node as teh bogus entity type
                           BNode(), system, 1.0)

        self.dump_graph(g, "Invalid: Non type used as type")


    def test_justification_missing_confidence(self):
        g = aifutils.make_graph()
        system = aifutils.make_system_with_uri(g, "http://test.edu/testSystem")

        entity = aifutils.make_entity(g, "http://www.test.edu/events/1", system)

        # create justification without the required confidence
        justification = BNode()
        g.add((justification, RDF.type, AIDA_ANNOTATION.TextJustification))
        g.add((justification, AIDA_ANNOTATION.source, Literal("FOO", datatype=XSD.string)))
        g.add((justification, AIDA_ANNOTATION.startOffset, Literal(14, datatype=XSD.integer)))
        g.add((justification, AIDA_ANNOTATION.endOffsetInclusive, Literal(56, datatype=XSD.integer)))
        g.add((justification, AIDA_ANNOTATION.system, system))
        g.add((entity, AIDA_ANNOTATION.justifiedBy, justification))

        self.dump_graph(g, "Invalid: Justification missing confidence")


    def dump_graph(self, g, description):
        print("\n\n======================================\n"
              "{!s}\n"
              "======================================\n\n".format(description))
        serialization = BytesIO()
        # need .buffer because serialize will write bytes, not str
        g.serialize(destination=serialization, format='turtle')
        print(serialization.getvalue().decode('utf-8'))


if __name__ == '__main__':
    unittest.main()