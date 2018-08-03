import unittest
import sys
sys.path.append('../')
from io import BytesIO
from rdflib import URIRef
from aida_interchange.Bounding_Box import Bounding_Box
from aida_interchange.aida_rdf_ontologies import SEEDLING_EVENT_TYPES_NIST, RELATION_TYPES_NIST
from aida_interchange import aifutils


# Running these tests will output the examples to the console
class Examples(unittest.TestCase):

    def test_create_an_entity_with_all_justification_types_and_confidence(self):
        g = aifutils.make_graph()
        g.bind('ldcOnt', SEEDLING_EVENT_TYPES_NIST.uri)

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # it doesn't matter what URI we give entities, events, etc. so long as they are
        # unique
        entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)

        # in order to allow uncertainty about the type of an entity, we don't mark an
        # entity's type directly on the entity, but rather make a separate assertion for it
        # its URI doesn't matter either
        type_assertion = aifutils.mark_type(g, "http://www.test.org/assertions/1", entity,
                                            SEEDLING_EVENT_TYPES_NIST.Person, system, 1.0)

        # the justification provides the evidence for our claim about the entity's type
        # we attach this justification to both the type assertion and the entity object
        # itself, since it provides evidence both for the entity's existence and its type.
        # in TA1 -> TA2 communications, we attach confidences at the level of justifications
        aifutils.mark_text_justification(g, [entity, type_assertion], "NYT_ENG_20181231", 42, 143, system, 0.973)

        # let's suppose we also have evidence from an image
        bb1 = Bounding_Box((123, 45), (167, 98))
        aifutils.mark_image_justification(g, [entity, type_assertion], "NYT_ENG_20181231_03",
                                          bb1, system, 0.123)

        # and also a video where the entity appears in a keyframe
        bb2 = Bounding_Box((123, 45), (167, 98))
        aifutils.mark_keyframe_video_justification(g, [entity, type_assertion], "NYT_ENG_20181231_03", "keyframe ID",
                                                   bb2, system, 0.234)

        # and also a video where the entity does not appear in a keyframe
        aifutils.mark_shot_video_justification(g, [entity, type_assertion], "SOME_VIDEO", "some shot ID", system, 0.487)

        # and even audio!
        aifutils.mark_audio_justification(g, [entity, type_assertion], "NYT_ENG_201181231", 4.566, 9.876, system, 0.789)

        # also we can link this entity to something in an external KB
        aifutils.link_to_external_kb(g, entity, "freebase.FOO", system, .398)

        # let's mark our entity with some arbitrary system-private data. You can attach such data
        # to nearly anything
        aifutils.mark_private_data(g, entity, "{ 'hello' : 'world' }", system)

        self.dump_graph(g, "Example of entity with all justifications")


    def test_create_an_event(self):
        g = aifutils.make_graph()
        g.bind('ldcOnt', SEEDLING_EVENT_TYPES_NIST.uri)

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # we make a resource for the event itself
        event = aifutils.make_event(g, "http://www.test.edu/events/1", system)

        # mark the event as a Personnel.Elect event; type is encoded separately so we can express
        # uncertainty about type
        aifutils.mark_type(g, "http://www.test.edu/assertions/5", event,
                           SEEDLING_EVENT_TYPES_NIST['Personnel.Elect'], system, 1.0)

        # create the two entities involved in the event
        electee = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/6", electee, SEEDLING_EVENT_TYPES_NIST.Person, system, 1.0)

        election_country = aifutils.make_entity(g, "http://www.test.edu/entities/2", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/7", election_country,
                           SEEDLING_EVENT_TYPES_NIST.GeopoliticalEntity, system, 1.0)

        # link those entities to the event
        arg = URIRef(SEEDLING_EVENT_TYPES_NIST.Person)
        aifutils.mark_as_event_argument(g, event, arg, electee, system, 0.785)
        arg2 = URIRef(SEEDLING_EVENT_TYPES_NIST.uri + "Place")
        aifutils.mark_as_event_argument(g, event, arg2, election_country, system, 0.589)

        self.dump_graph(g, "Example of creating an event")


    def test_create_an_entity_with_uncertainty_about_its_type(self):
        g = aifutils.make_graph()
        g.bind('ldcOnt', SEEDLING_EVENT_TYPES_NIST.uri)

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        entity_is_a_person = aifutils.mark_type(g, "http://www.test.org/assertions/1", entity,
                                                SEEDLING_EVENT_TYPES_NIST.Person, system, 0.5)
        entity_is_an_organization = aifutils.mark_type(g, "http://www.test.org/assertions/2", entity,
                                                       SEEDLING_EVENT_TYPES_NIST.Organization, system, 0.2)

        aifutils.mark_text_justification(g, [entity, entity_is_a_person], "NYT_ENG_201181231", 42, 143, system, 0.6)

        aifutils.mark_text_justification(g, [entity, entity_is_an_organization],
                                         "NYT_ENG_201181231", 343, 367, system, 0.3)

        aifutils.mark_as_mutually_exclusive(g, [([entity_is_a_person], 0.5),
                                                ([entity_is_an_organization], 0.2)], system, None)

        self.dump_graph(g, "Example of entity with uncertainty about type")


    def test_create_a_relation_between_two_entities_where_there_is_uncertainty_about_identity_of_one_argument(self):
        g = aifutils.make_graph()
        g.bind('ldcOnt', SEEDLING_EVENT_TYPES_NIST.uri)

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # we want to represent a "city_of_birth" relation for a person, but we aren't sure whether
        # they were born in Louisville or Cambridge
        person_entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/1", person_entity, SEEDLING_EVENT_TYPES_NIST.Person, system, 1.0)

        # create entities for the two locations
        louisville_entity = aifutils.make_entity(g, "http://test.edu/entities/2", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/2", louisville_entity,
                           SEEDLING_EVENT_TYPES_NIST.GeopoliticalEntity, system, 1.0)
        cambridge_entity = aifutils.make_entity(g, "http://test.edu/entities/3", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/3", cambridge_entity,
                           SEEDLING_EVENT_TYPES_NIST.GeopoliticalEntity, system, 1.0)

        # create an entity for the uncertain place of birth
        uncertain_place_of_birth_entity = aifutils.make_entity(g, "http://www.test.edu/entities/4", system)

        # whatever this place turns out to refer to, we're sure it's where they live
        aifutils.make_relation_in_event_form(g, "http://test.edu/relations/1",
                                             RELATION_TYPES_NIST['Physical.Resident'],
                                             RELATION_TYPES_NIST['Physical.Resident'] + '_Resident', person_entity,
                                             RELATION_TYPES_NIST['Physical.Resident'] + '_Place', uncertain_place_of_birth_entity,
                                             "http://www.test.edu/assertions/4", system, 1.0)
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
        g.bind('ldcOnt', SEEDLING_EVENT_TYPES_NIST.uri)

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # we want to represent that we know, regardless of hypothesis, that there is a person named Bob,
        # two companies (Google and Amazon), and two places (Seattle and California)
        bob = aifutils.make_entity(g, "http://www.test.edu/entites/Bob", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/1", bob, SEEDLING_EVENT_TYPES_NIST.Person, system, 1.0)
        google = aifutils.make_entity(g, "http://www.test.edu/entities/Google", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/2", google, SEEDLING_EVENT_TYPES_NIST.Organization, system, 1.0)
        amazon = aifutils.make_entity(g, "http://www.test.edu/entities/Amazon", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/3", amazon, SEEDLING_EVENT_TYPES_NIST.Organization, system, 1.0)
        seattle = aifutils.make_entity(g, "http://www.test.edu/entities/Seattle", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/4", seattle, SEEDLING_EVENT_TYPES_NIST.GeopoliticalEntity, system, 1.0)
        california = aifutils.make_entity(g, "http://www.test.edu/entities/California", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/5", california, SEEDLING_EVENT_TYPES_NIST.GeopoliticalEntity, system, 1.0)

        city_relation_subject = RELATION_TYPES_NIST['Physical.Resident'] + '_Resident'
        city_relation_object = RELATION_TYPES_NIST['Physical.Resident'] + '_Place'
        employee_relation_subject = RELATION_TYPES_NIST['OrganizationAffiliation.EmploymentMembership'] + '_Employee'
        employee_relation_object = RELATION_TYPES_NIST['OrganizationAffiliation.EmploymentMembership'] + '_Organization'


        # under the background hypothesis that Bob lives in Seattle, we believe he works for Amazon
        bob_lives_in_seattle = aifutils.make_relation_in_event_form(g, "http://www.test.edu/relations/1",
                                                                    RELATION_TYPES_NIST['Physical.Resident'],
                                                                    city_relation_subject,
                                                                    bob,
                                                                    city_relation_object,
                                                                    seattle,
                                                                    "http://www/test.org/assertions/6", system, 1.0)
        bob_lives_in_seattle_hypothesis = aifutils.make_hypothesis(g, "http://www.test.edu/hypotheses/1",
                                                                   [bob_lives_in_seattle], system)

        bob_works_for_amazon = aifutils.make_relation_in_event_form(g, "http://www.test.edu/relations/2",
                                                                    RELATION_TYPES_NIST['OrganizationAffiliation.EmploymentMembership'],
                                                                    employee_relation_subject,
                                                                    bob,
                                                                    employee_relation_object,
                                                                    amazon,
                                                                    "http://www/test.org/assertions/7", system, 1.0)
        aifutils.mark_depends_on_hypothesis(g, bob_works_for_amazon, bob_lives_in_seattle_hypothesis)

        # under the background hypothesis that Bob lives in California, we believe he works for Google
        bob_lives_in_california = aifutils.make_relation_in_event_form(g, "http://www.test.edu/relations/3",
                                                                       RELATION_TYPES_NIST['Physical.Resident'],
                                                                       city_relation_subject,
                                                                       bob,
                                                                       city_relation_object,
                                                                       california,
                                                                       "http://www/test.org/assertions/8", system, 1.0)
        bob_lives_in_california_hypothesis = aifutils.make_hypothesis(g, "http://www.test.edu/hypotheses/2",
                                                                      [bob_lives_in_california], system)

        bob_works_for_google = aifutils.make_relation_in_event_form(g, "http://www.test.edu/relations/4",
                                                                    RELATION_TYPES_NIST['OrganizationAffiliation.EmploymentMembership'],
                                                                    employee_relation_subject,
                                                                    bob,
                                                                    employee_relation_object,
                                                                    google,
                                                                    "http://www/test.org/assertions/9", system, 1.0)
        aifutils.mark_depends_on_hypothesis(g, bob_works_for_google, bob_lives_in_california_hypothesis)

        self.dump_graph(g, "Example of two hypotheses")


    def test_use_subgraph_confidences_to_show_mutually_exclusive_linked_event_argument_options(self):
        g = aifutils.make_graph()
        g.bind('ldcOnt', SEEDLING_EVENT_TYPES_NIST.uri)

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # we make a resource for the event itself
        event = aifutils.make_event(g, "http://www.test.edu/events/1", system)

        # mark the event as a Personnel.Elect event; type is encoded separately so we can express uncertainty about type
        aifutils.mark_type(g, "http://www.test.edu/assertions/5", event, SEEDLING_EVENT_TYPES_NIST['Conflict.Attack'], system, 1.0)

        # create the two entities involved in the event
        bob = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/6", bob, SEEDLING_EVENT_TYPES_NIST.Person, system, 1.0)

        fred = aifutils.make_entity(g, "http://www.test.edu/entities/2", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/7", fred, SEEDLING_EVENT_TYPES_NIST.Person, system, 1.0)

        # we link all possible argument fillers to the event
        bob_hit_fred_assertions = [aifutils.mark_as_event_argument(g, event, URIRef(SEEDLING_EVENT_TYPES_NIST['Conflict.Attack'] + "_Attacker"), bob, system, None),
                                   aifutils.mark_as_event_argument(g, event, URIRef(SEEDLING_EVENT_TYPES_NIST['Conflict.Attack'] + "_Target"), fred, system, None)]

        fred_hit_bob_assertions = [aifutils.mark_as_event_argument(g, event, URIRef(SEEDLING_EVENT_TYPES_NIST['Conflict.Attack'] + "_Attacker"), fred, system, None),
                                   aifutils.mark_as_event_argument(g, event, URIRef(SEEDLING_EVENT_TYPES_NIST['Conflict.Attack'] + "_Target"), bob, system, None)]

        # then we mark these as mutually exclusive
        # we also mark confidence 0.2 that neither of these are true
        aifutils.mark_as_mutually_exclusive(g, [(bob_hit_fred_assertions, 0.6), (fred_hit_bob_assertions, 0.2)], system, 0.2)

        self.dump_graph(g, "Example of subgraph confidences to show mutually exclusive linked event argument options")


    def test_create_seedling_event(self):
        g = aifutils.make_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSytem")

        # we make a resource for the event itself
        event = aifutils.make_entity(g, "http://www.test.edu/events/1", system)

        aifutils.mark_type(g, "http://www.test.edu/assertions/5", event,
                           URIRef("http://darpa.mil/ontologies/SeedlingOntology/BUSINESS_DECLARE-BANKRUPTCY"), system, 1.0)

        # create the two entities involved in the event
        electee = aifutils.make_entity(g, "http://www.test.edu/entities/1", system,)
        aifutils.mark_type(g, "http://www.test.edu/assertions/6", electee,
                           URIRef("http://darpa.mil/ontologies/SeedlingOntology/Organization"), system, 1.0)

        election_country = aifutils.make_entity(g, "http://www.test.edu/entities/2", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/7", election_country,
                           URIRef("http://darpa.mil/ontologies/SeedlingOntology/GeopoliticalEntity"), system, 1.0)

        time = aifutils.make_entity(g, "http://test.edu/entities/3", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/8", time,
                           URIRef("http://darpa.mil/ontologies/SeedlingOntology/Time"), system, 1.0)

        # link those entities to the event
        aifutils.mark_as_event_argument(g, event, URIRef("http://darpa.mil/ontologies/SeedlingOntology/BUSINESS_DECLARE-BANKRUPTCY_arg1"), electee, system, 0.785)
        aifutils.mark_as_event_argument(g, event, URIRef("http://darpa.mil/ontologies/SeedlingOntology/BUSINESS_DECLARE-BANKRUPTCY_arg2"), election_country, system, 0.589)
        aifutils.mark_as_event_argument(g, event, URIRef("http://darpa.mil/ontologies/SeedlingOntology/BUSINESS_DECLARE-BANKRUPTCY_arg3"), time, system, 0.589)

    def test_create_an_entity_with_image_justification_and_vector(self):
        g = aifutils.make_graph()
        g.bind('ldcOnt', SEEDLING_EVENT_TYPES_NIST.uri)

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # it doesn't matter what URI we give entities, events, etc. so long as they are
        # unique
        entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)

        # in order to allow uncertainty about the type of an entity, we don't mark an
        # entity's type directly on the entity, but rather make a separate assertion for it
        # its URI doesn't matter either
        type_assertion = aifutils.mark_type(g, "http://www.test.org/assertions/1", entity,
                                            SEEDLING_EVENT_TYPES_NIST.Person, system, 1.0)

        # the justification provides the evidence for our claim about the entity's type
        # we attach this justification to both the type assertion and the entity object
        # itself, since it provides evidence both for the entity's existence and its type.
        # in TA1 -> TA2 communications, we attach confidences at the level of justifications
        # let's suppose we also have evidence from an image
        bb1 = Bounding_Box((123, 45), (167, 98))
        aifutils.mark_image_justification(g, [entity, type_assertion], "NYT_ENG_20181231_03",
                                          bb1, system, 0.123)

        # also we can link this entity to something in an external KB
        aifutils.link_to_external_kb(g, entity, "freebase.FOO", system, .398)

        vec = {"vector_type": "http://www.test.edu/systemX/personVector", "vector_data": [2.0, 7.5, 0.2, 8.1]}
        # let's mark our entity with some arbitrary system-private data. You can attach such data
        # to nearly anything
        aifutils.mark_private_data_with_vector(g, entity, system, vec)

        self.dump_graph(g, "Example of entity with image justification and vector")

    def test_make_entity(self):
        g = aifutils.make_graph()
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/system")
        entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        type_assertion = aifutils.mark_type(g, "http://www.test.edu/assertions/1", entity,
                                            SEEDLING_EVENT_TYPES_NIST.Person, system, 1.0)

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