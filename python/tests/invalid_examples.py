import sys
import os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../')))
import unittest

from io import BytesIO
from rdflib import URIRef, Literal, XSD, BNode, RDF
from aida_interchange import aifutils
# from aida_interchange.aida_rdf_ontologies import AIDA_PROGRAM_ONTOLOGY, AIDA_ANNOTATION
from aida_interchange.rdf_ontologies import ldc_ontology, interchange_ontology

class InvalidExamples(unittest.TestCase):
    test_dir_path = "./output"

    def new_file(self, g, test_name):
        if self.test_dir_path:
            # f = open(self.test_dir_path + "/" + test_name, "wb+")
            # f.write(g.serialize(format='turtle'))
            # f.close()
            g.serialize(destination=self.test_dir_path + "/" + test_name, format='turtle')

    def test_confidence_outside_of_zero_one(self):
        g = aifutils.make_graph()
        system = aifutils.make_system_with_uri(g, "http://test.edu/testSystem")
        entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        # Older Ontology?
        # aifutils.mark_type(g, "http://www.test.org/assertions/1",
        #                    #illegal confidence value - not in [0.0, 1.0]
        #                    entity, AIDA_PROGRAM_ONTOLOGY.Person, system, 100.0)

        aifutils.mark_type(g, "http://www.test.org/assertions/1",
                           #illegal confidence value - not in [0.0, 1.0]
                           entity, ldc_ontology.PER, system, 100.0)




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


    # def test_justification_missing_confidence(self):
    #     g = aifutils.make_graph()
    #     system = aifutils.make_system_with_uri(g, "http://test.edu/testSystem")

    #     entity = aifutils.make_entity(g, "http://www.test.edu/events/1", system)

    #     # create justification without the required confidence
    #     justification = BNode()
    #     g.add((justification, RDF.type, AIDA_ANNOTATION.TextJustification))
    #     g.add((justification, AIDA_ANNOTATION.source, Literal("FOO", datatype=XSD.string)))
    #     g.add((justification, AIDA_ANNOTATION.startOffset, Literal(14, datatype=XSD.integer)))
    #     g.add((justification, AIDA_ANNOTATION.endOffsetInclusive, Literal(56, datatype=XSD.integer)))
    #     g.add((justification, AIDA_ANNOTATION.system, system))
    #     g.add((entity, AIDA_ANNOTATION.justifiedBy, justification))

    #     self.dump_graph(g, "Invalid: Justification missing confidence")

    def test_create_an_event_add_invalid_attribute(self):
        #g = get_initialized_graph()
        g = aifutils.make_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # we make a resource for the event itself
        event = aifutils.make_event(g, "http://www.test.edu/events/1", system)

        aifutils.mark_attribute(g, event, interchange_ontology.VideoJustificationChannelPicture)


        self.new_file(g, "test_create_an_event_add_invalid_attribute.ttl")
        self.dump_graph(g, "Invalid: Semantic Attribute for Event, must be aida:Negated, aida:Hedged, aida:Irrealis, aida:Generic")


    def test_create_a_relation_add_invalid_attribute(self):
        #g = get_initialized_graph()
        g = aifutils.make_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # we make a resource for the event itself
        relation = aifutils.make_relation(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#R779959.00004", system)

        aifutils.mark_attribute(g, relation, interchange_ontology.VideoJustificationChannelPicture)

        self.new_file(g, "test_create_a_relation_add_invalid_attribute.ttl")
        self.dump_graph(g, "Invalid: Semantic Attribute for Relation, must be aida:Negated, aida:Hedged, aida:Irrealis, aida:Generic")

    def test_create_an_event_argument_add_invalid_attribute(self):
        #g = get_initialized_graph()
        g = aifutils.make_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # we make a resource for the event itself
        event = aifutils.make_event(g, "http://www.test.edu/events/1", system)

        # mark the event as a Personnel.Elect event; type is encoded separately so we can express
        # uncertainty about type
        aifutils.mark_type(g, "http://www.test.edu/assertions/5", event,
                           ldc_ontology.Personnel_Elect, system, 1.0)

        # create the two entities involved in the event
        electee = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/6", electee, ldc_ontology.PER, system, 1.0)

        election_country = aifutils.make_entity(g, "http://www.test.edu/entities/2", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/7", election_country,
                           ldc_ontology.GPE, system, 1.0)

        # link those entities to the event
        argument1 = aifutils.mark_as_argument(g, event, ldc_ontology.Personnel_Elect_Candidate, electee, system, 0.785)
        argument2 = aifutils.mark_as_argument(g, event, ldc_ontology.Personnel_Elect_Place, election_country, system, 0.589)

        aifutils.mark_attribute(g, argument1, interchange_ontology.Irrealis)
        aifutils.mark_attribute(g, argument1, interchange_ontology.Generic)

        aifutils.mark_attribute(g, argument2, interchange_ontology.VideoJustificationChannelPicture)
        aifutils.mark_attribute(g, argument2, interchange_ontology.VideoJustificationChannelSound)

        self.new_file(g, "test_create_an_event_argument_add_invalid_attribute.ttl")
        self.dump_graph(g, "Invalid: Semantic Attribute for Event Argument, must be aida:Negated, aida:Hedged")

    def test_create_an_entity_with_add_invalid_attribute(self):
        #g = get_initialized_graph()
        g = aifutils.make_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # it doesn't matter what URI we give entities, events, etc. so long as they are
        # unique
        entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)

        aifutils.mark_attribute(g, entity, interchange_ontology.Irrealis)
        aifutils.mark_attribute(g, entity, interchange_ontology.Negated)
        aifutils.mark_attribute(g, entity, interchange_ontology.Hedged)
        aifutils.mark_attribute(g, entity, interchange_ontology.VideoJustificationChannelPicture)

        self.new_file(g, "test_create_an_entity_with_add_invalid_attribute.ttl")
        self.dump_graph(g, "Invalid: Semantic Attribute for Entity can only be must be aida:Generic")

    def test_create_a_relation_argument_add_attribute(self):
        #g = get_initialized_graph()
        g = aifutils.make_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        bob = aifutils.make_entity(g, "http://www.test.edu/entites/person/Bob", system)
        maryland = aifutils.make_entity(g, "http://www.test.edu/entites/place/Maryland", system)

        aifutils.mark_type(g, "http://www.test.edu/assertions/bobIsAPerson", bob, ldc_ontology.PER, system, 1.0)
        aifutils.mark_type(g, "http://www.test.edu/assertions/marylandIsALocation", maryland, ldc_ontology.LOC_Position_Region, system, 1.0)

        # we make a resource for the event itself
        relationBobLiveInMD = aifutils.make_relation(g, "http://www.test.edu/relationss/bobLivesInMaryland", system)

        argument1 = aifutils.mark_as_argument(g, relationBobLiveInMD, ldc_ontology.Physical_Resident_Resident, bob, system, 1)

        aifutils.mark_attribute(g, argument1, interchange_ontology.Generic)

        self.new_file(g, "test_create_a_relation_argument_add_attribute.ttl")
        self.dump_graph(g, "Invalid: Relation Argument cannot have aida:Attribute")

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