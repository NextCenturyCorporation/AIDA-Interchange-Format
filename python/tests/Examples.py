import unittest
import sys
sys.path.append('../')
from io import BytesIO
from rdflib import URIRef
from Bounding_Box import Bounding_Box
from aida_interchange.aida_rdf_ontologies import AIDA_PROGRAM_ONTOLOGY
import aifutils


# Running these tests will output the examples to the console
class Examples(unittest.TestCase):

    def test_create_an_entity_with_all_justification_types_and_confidence(self):
        g = aifutils.make_graph()
        g.bind('coldstart', AIDA_PROGRAM_ONTOLOGY.uri)

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)

        type_assertion = aifutils.mark_type(g, "http://www.test.org/assertions/1", entity, AIDA_PROGRAM_ONTOLOGY.Person, system, 1.0)

        aifutils.mark_text_justification(g, [entity, type_assertion], "NYT_ENG_20181231", 42, 143, system, 0.973)

        bb1 = Bounding_Box((123, 45), (167, 98))
        aifutils.mark_image_justification(g, [entity, type_assertion], "NYT_ENG_20181231_03",
                                          bb1, system, 0.123)

        bb2 = Bounding_Box((123, 45), (167, 98))
        aifutils.mark_keyframe_video_justification(g, [entity, type_assertion], "NYT_ENG_20181231_03", "keyframe ID",
                                                   bb2, system, 0.234)

        aifutils.mark_shot_video_justification(g, [entity, type_assertion], "SOME_VIDEO", "some shot ID", system, 0.487)

        aifutils.mark_audio_justification(g, [entity, type_assertion], "NYT_ENG_201181231", 4.566, 9.876, system, 0.789)

        aifutils.link_to_external_kb(g, entity, "freebase.FOO", system, .398)

        aifutils.mark_private_data(g, entity, "{ 'hello' : 'world' }", system)

        self.dump_graph(g, "Example of entity with all justifications")


    def test_create_an_event(self):
        g = aifutils.make_graph()
        g.bind('coldstart', AIDA_PROGRAM_ONTOLOGY.uri)

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        event = aifutils.make_event(g, "http://www.test.edu/events/1", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/5", event, AIDA_PROGRAM_ONTOLOGY['personnel.elect'], system, 1.0)
        electee = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/6", electee, AIDA_PROGRAM_ONTOLOGY.Person, system, 1.0)
        election_country = aifutils.make_entity(g, "http://www.test.edu/entities/2", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/7", election_country, AIDA_PROGRAM_ONTOLOGY.GeopoliticalEntity, system, 1.0)
        arg = URIRef(AIDA_PROGRAM_ONTOLOGY['personnel.elect'] + "_person")
        aifutils.mark_as_event_argument(g, event, arg, electee, system, 0.785)
        arg2 = URIRef(AIDA_PROGRAM_ONTOLOGY['personnel.elect'] + "_place")
        aifutils.mark_as_event_argument(g, event, arg2, election_country, system, 0.589)
        self.dump_graph(g, "Example of creating an event")


    def test_create_an_entity_with_uncertainty_about_its_type(self):
        g = aifutils.make_graph()
        g.bind('coldstart', AIDA_PROGRAM_ONTOLOGY.uri)

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        entity_is_a_person = aifutils.mark_type(g, "http://www.test.org/assertions/1", entity, AIDA_PROGRAM_ONTOLOGY.Person, system, 0.5)
        entity_is_an_organization = aifutils.mark_type(g, "http://www.test.org/assertions/2", entity, AIDA_PROGRAM_ONTOLOGY.Organization, system, 0.2)

        aifutils.mark_text_justification(g, [entity, entity_is_a_person], "NYT_ENG_201181231", 42, 143, system, 0.6)

        aifutils.mark_text_justification(g, [entity, entity_is_an_organization], "NYT_ENG_201181231", 343, 367, system, 0.3)

        aifutils.mark_as_mutually_exclusive(g, [([entity_is_a_person], 0.5), ([entity_is_an_organization], 0.2)], system, None)

        self.dump_graph(g, "Example of entity with uncertainty about type")


    def test_create_a_relation_between_two_entities_where_there_is_uncertainty_about_identity_of_one_argument(self):
        g = aifutils.make_graph()
        g.bind('coldstart', AIDA_PROGRAM_ONTOLOGY.uri)

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # we want to represent a "city_of_birth" relation for a person, but we aren't sure whether
        # they were born in Louisville or Cambridge
        person_entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/1", person_entity, AIDA_PROGRAM_ONTOLOGY.Person, system, 1.0)

        # create entities for the two locations
        louisville_entity = aifutils.make_entity(g, "http://test.edu/entities/2", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/2", louisville_entity,
                           AIDA_PROGRAM_ONTOLOGY.GeopoliticalEntity, system, 1.0)
        cambridge_entity = aifutils.make_entity(g, "http://test.edu/entities/3", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/3", cambridge_entity,
                           AIDA_PROGRAM_ONTOLOGY.GeopoliticalEntity, system, 1.0)

        # create an entity for the uncertain place of birth
        uncertain_place_of_birth_entity = aifutils.make_entity(g, "http://www.test.edu/entities/4", system)

        # whatever this place turns out to refer to, we're sure it's where they live
        aifutils.make_relation(g, "http://www.test.edu/relations/1", person_entity,
                               AIDA_PROGRAM_ONTOLOGY.cities_of_residence, uncertain_place_of_birth_entity, system, 1.0)

        # we use clusters to represent uncertainty about identity
        # we make two clusters, one for Louisville and one for Cambridge
        louisville_cluster = aifutils.make_cluster_with_prototype(g, "http://www.test.edu/clusters/1",
                                                                  louisville_entity, system)
        cambridge_cluster = aifutils.make_cluster_with_prototype(g, "http://www.test.edu/clusters/2",
                                                                 cambridge_entity, system)

        # the uncertain place of birth is either Louisville or Cambridge
        place_of_birth_in_louisville_cluster = aifutils.mark_as_possible_cluster_member(g, uncertain_place_of_birth_entity,
                                                                                        louisville_cluster, 0.4, system)
        place_of_birth_in_cambridge_cluster = aifutils.mark_as_possible_cluster_member(g, uncertain_place_of_birth_entity,
                                                                                       cambridge_cluster, 0.6, system)

        # but not both
        aifutils.mark_as_mutually_exclusive(g, [([place_of_birth_in_cambridge_cluster], 0.4),
                                                ([place_of_birth_in_louisville_cluster], 0.6)], system, None)

        self.dump_graph(g, "Relation between two entities with uncertainty about id of one")


    def test_two_hypotheses(self):
        g = aifutils.make_graph()
        g.bind('coldstart', AIDA_PROGRAM_ONTOLOGY.uri)

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # we want to represent that we know, regardless of hypothesis, that there is a person named Bob,
        # two companies (Google and Amazon), and two places (Seattle and California)
        bob = aifutils.make_entity(g, "http://www.test.edu/entites/Bob", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/1", bob, AIDA_PROGRAM_ONTOLOGY.Person, system, 1.0)
        google = aifutils.make_entity(g, "http://www.test.edu/entities/Google", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/2", google, AIDA_PROGRAM_ONTOLOGY.Organization, system, 1.0)
        amazon = aifutils.make_entity(g, "http://www.test.edu/entities/Amazon", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/3", amazon, AIDA_PROGRAM_ONTOLOGY.Organization, system, 1.0)
        seattle = aifutils.make_entity(g, "http://www.test.edu/entities/Seattle", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/4", seattle, AIDA_PROGRAM_ONTOLOGY.GeopoliticalEntity, system, 1.0)
        california = aifutils.make_entity(g, "http://www.test.edu/entities/California", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/5", california, AIDA_PROGRAM_ONTOLOGY.GeopoliticalEntity, system, 1.0)

        # under the background hypothesis that Bob lives in Seattle, we believe he works for Amazon
        bob_lives_in_seattle = aifutils.make_relation(g, "http://www.test.edu/relations/1", bob,
                                                      AIDA_PROGRAM_ONTOLOGY.cities_of_residence, seattle, system, 1.0)
        bob_lives_in_seattle_hypothesis = aifutils.make_hypothesis(g, "http://www.test.edu/hypotheses/1",
                                                                   [bob_lives_in_seattle], system)
        bob_works_for_amazon = aifutils.make_relation(g, "http://www.test.edu/relations/2", bob,
                                                      AIDA_PROGRAM_ONTOLOGY.employee_or_member_of, amazon, system, 1.0)
        aifutils.mark_depends_on_hypothesis(g, bob_works_for_amazon, bob_lives_in_seattle_hypothesis)

        # under the background hypothesis that Bob lives in California, we believe he works for Google
        bob_lives_in_california = aifutils.make_relation(g, "http://www.test.edu/relations/3", bob,
                                                         AIDA_PROGRAM_ONTOLOGY.cities_of_residence, california, system, 1.0)
        bob_lives_in_california_hypothesis = aifutils.make_hypothesis(g, "http://www.test.edu/hypotheses/2",
                                                                      [bob_lives_in_california], system)
        bob_works_for_google = aifutils.make_relation(g, "http://www.test.edu/relations/4",
                                                      bob, AIDA_PROGRAM_ONTOLOGY.employee_or_member_of, google, system, 1.0)
        aifutils.mark_depends_on_hypothesis(g, bob_works_for_google, bob_lives_in_california_hypothesis)

        self.dump_graph(g, "Example of two hypotheses")


    def test_make_entity(self):
        g = aifutils.make_graph()
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/system")
        entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        type_assertion = aifutils.mark_type(g, "http://www.test.edu/assertions/1", entity,
                                            AIDA_PROGRAM_ONTOLOGY.Person, system, 1.0)

        aifutils.mark_text_justification(g, [entity, type_assertion], "NYT_ENG_20181231",
                                         42, 143, system, 0.973)

        self.dump_graph(g, "Example of creating an entity")
        self.assertEqual([type_assertion], aifutils.get_type_assertions(g, entity))

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