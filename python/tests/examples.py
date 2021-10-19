# Remove the below comment once we update to python3
# -*- coding: utf-8 -*-
import os
import sys
import unittest
from io import BytesIO

from rdflib import Graph, URIRef, RDF

from aida_interchange import aifutils
from aida_interchange.bounding_box import Bounding_Box
from aida_interchange.ldc_time_component import LDCTimeComponent, LDCTimeType
from aida_interchange.rdf_ontologies import ldc_ontology, interchange_ontology

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../')))

def get_initialized_graph():
    graph = aifutils.make_graph()
    graph.bind('ldcOnt', ldc_ontology.NAMESPACE)
    return graph

# Running these tests will output the examples to the console
class Examples(unittest.TestCase):
    test_dir_path = "./output"

    def new_file(self, g, test_name):
        if self.test_dir_path:
            f = open(self.test_dir_path + "/" + test_name, "wb+")
            f.write(g.serialize(format='turtle'))
            f.close()

    def test_create_an_entity_with_all_justification_types_and_confidence(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # it doesn't matter what URI we give entities, events, etc. so long as they are
        # unique
        entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)

        # in order to allow uncertainty about the type of an entity, we don't mark an
        # entity's type directly on the entity, but rather make a separate assertion for it
        # its URI doesn't matter either
        type_assertion = aifutils.mark_type(g, "http://www.test.org/assertions/1", entity,
                                            ldc_ontology.PER, system, 1.0)

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
        self.new_file(g, "test_create_an_entity_with_all_justification_types_and_confidence.ttl")

        self.dump_graph(g, "Example of entity with all justifications")


    def test_create_an_event(self):
        g = get_initialized_graph()

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
        aifutils.mark_as_argument(g, event, ldc_ontology.Personnel_Elect_Candidate, electee, system, 0.785)
        aifutils.mark_as_argument(g, event, ldc_ontology.Personnel_Elect_Place, election_country, system, 0.589)
        self.new_file(g, "test_create_an_event.ttl")
        self.dump_graph(g, "Example of creating an event")


    def test_create_an_entity_with_uncertainty_about_its_type(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        entity_is_a_person = aifutils.mark_type(g, "http://www.test.org/assertions/1", entity,
                                                ldc_ontology.PER, system, 0.5)
        entity_is_an_organization = aifutils.mark_type(g, "http://www.test.org/assertions/2", entity,
                                                       ldc_ontology.ORG, system, 0.2)

        aifutils.mark_text_justification(g, [entity, entity_is_a_person], "NYT_ENG_201181231", 42, 143, system, 0.6)

        aifutils.mark_text_justification(g, [entity, entity_is_an_organization],
                                         "NYT_ENG_201181231", 343, 367, system, 0.3)

        aifutils.mark_as_mutually_exclusive(g, { tuple([entity_is_a_person]):0.5, tuple([entity_is_an_organization]):0.2}, system, None)
        self.new_file(g, "test_create_an_entity_with_uncertainty_about_its_type.ttl")
        self.dump_graph(g, "Example of entity with uncertainty about type")


    def test_create_a_relation_between_two_entities_where_there_is_uncertainty_about_identity_of_one_argument(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # we want to represent a "city_of_birth" relation for a person, but we aren't sure whether
        # they were born in Louisville or Cambridge
        person_entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/1", person_entity, ldc_ontology.PER, system, 1.0)

        # create entities for the two locations
        louisville_entity = aifutils.make_entity(g, "http://test.edu/entities/2", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/2", louisville_entity,
                           ldc_ontology.GPE, system, 1.0)
        cambridge_entity = aifutils.make_entity(g, "http://test.edu/entities/3", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/3", cambridge_entity,
                           ldc_ontology.GPE, system, 1.0)

        # create an entity for the uncertain place of birth
        uncertain_place_of_birth_entity = aifutils.make_entity(g, "http://www.test.edu/entities/4", system)

        # whatever this place turns out to refer to, we're sure it's where they live
        aifutils.make_relation_in_event_form(g, "http://test.edu/relations/1",
                                             ldc_ontology.Physical_Resident_Resident,
                                             ldc_ontology.Physical_Resident_Resident_Resident, person_entity,
                                             ldc_ontology.Physical_Resident_Resident_Place, uncertain_place_of_birth_entity,
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
        aifutils.mark_as_mutually_exclusive(g, { tuple([place_of_birth_in_cambridge_cluster]):0.4,
                                                 tuple([place_of_birth_in_louisville_cluster]):0.6}, system, None)
        self.new_file(g, "test_create_a_relation_between_two_entities_where_there_is_uncertainty_about_identity_of_one_argument.ttl")
        self.dump_graph(g, "Relation between two entities with uncertainty about id of one")


    def _add_buk_hypothesis(self, g: Graph, buk: URIRef, russia: URIRef, system: URIRef) -> URIRef:
        buk_is_russian = aifutils.make_relation(g, "http://www.test.org/relations/buk_is_russian", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/buk_russia_relation_type", buk_is_russian, ldc_ontology.GeneralAffiliation_ArtifactPoliticalOrganizationReligiousAffiliation_OwnershipPossession, system, 1.0)
        aifutils.mark_as_argument(g, buk_is_russian, ldc_ontology.GeneralAffiliation_ArtifactPoliticalOrganizationReligiousAffiliation_OwnershipPossession_Artifact, buk, system, 1.0, "http://www.test.edu/arguments/affiliate_buk")
        aifutils.mark_as_argument(g, buk_is_russian, ldc_ontology.GeneralAffiliation_ArtifactPoliticalOrganizationReligiousAffiliation_OwnershipPossession_Owner, russia, system, 1.0, "http://www.test.edu/arguments/affiliation_russia")
        return aifutils.make_hypothesis(g, "http://www.test.edu/hypotheses/buk_is_russian", [buk_is_russian], system)

    def test_event_argument_based_on_preexisting_hypothesis(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # there is a BUK missile launcher
        buk = aifutils.make_entity(g, "http://www.test.edu/entites/Buk", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/buk_type", buk, ldc_ontology.WEA, system, 1.0)
        # there is a country (Russia)
        russia = aifutils.make_entity(g, "http://www.test.edu/entities/Russia", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/russia_type", russia, ldc_ontology.GPE, system, 1.0)

        # add buk_is_russian hypothesis to another model to simulate pre-existence
        hypo_g = aifutils.make_graph()
        hypo_g += g
        buk_is_russian_hypothesis = self._add_buk_hypothesis(hypo_g, buk, russia, system)

        # there is a plane (MH17)
        mh17 = aifutils.make_entity(g, "http://www.test.edu/entites/MH-17", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/mh17_type", mh17, ldc_ontology.VEH, system, 1.0)
        # the BUK missile launcher was used to attack MH17
        attack_on_mh17 = aifutils.make_event(g, "http://www.test.edu/events/AttackOnMH-17", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/attck_on_mh17_type", attack_on_mh17, ldc_ontology.Conflict_Attack, system, 1.0)
        aifutils.mark_as_argument(g, attack_on_mh17, ldc_ontology.Conflict_Attack_Target, mh17, system, 1.0, "http://www.test.org/arguments/mh_17_is_the_target")
        aifutils.mark_as_argument(g, attack_on_mh17, ldc_ontology.Conflict_Attack_Instrument, buk, system, 1.0, "http://www.test.org/arguments/buk_is_the_instrument")

        # Mark the attacker argument as dependent on the hypothesis that bob lives in california
        russia_shot_mh17 = aifutils.mark_as_argument(g, attack_on_mh17, ldc_ontology.Conflict_Attack_Attacker, russia, system, 1.0, "http://www.test.org/arguments/russia_is_the_attacker")
        aifutils.mark_depends_on_hypothesis(g, russia_shot_mh17, buk_is_russian_hypothesis)

        # print hypothesis
        self.new_file(hypo_g, "test_event_argument_based_on_preexisting_hypothesis__hypothesis.ttl")
        self.dump_graph(hypo_g, "Example of event argument based on pre-existing hypothesis (hypothesis)")

        # print normal graph
        self.dump_graph(g, "Example of event argument based on pre-existing hypothesis")

        # when validating, need both graphs
        g += hypo_g
        self.new_file(g, "test_event_argument_based_on_preexisting_hypothesis.ttl")

    def _add_bob_hypothesis(self, g: Graph, bob: URIRef, system: URIRef) -> URIRef:
        california = aifutils.make_entity(g, "http://www.test.edu/entites/California", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/California_type", california, ldc_ontology.GPE, system, 1.0)


        bob_lives_in_california = aifutils.make_relation(g, "http://www.test.org/relations/Bob_lives_in_California", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/Bob_California_relation_type", bob_lives_in_california, ldc_ontology.Physical_Resident_Resident, system, 1.0)
        aifutils.mark_as_argument(g, bob_lives_in_california, ldc_ontology.Physical_Resident_Resident_Resident, bob, system, 1.0, "http://www.test.edu/arguments/resident_bob")
        aifutils.mark_as_argument(g, bob_lives_in_california, ldc_ontology.Physical_Resident_Resident_Place, california, system, 1.0, "http://www.test.edu/arguments/place_california")
        return aifutils.make_hypothesis(g, "http://www.test.edu/hypotheses/Bob_lives_in_California", [bob_lives_in_california], system)

    def test_relation_based_on_preexisting_hypothesis(self):
        g = get_initialized_graph()

        # Every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # There is a person, Bob
        bob = aifutils.make_entity(g, "http://www.test.edu/entites/Bob", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/Bob_type", bob, ldc_ontology.PER, system, 1.0)

        # add bob_lives_in_california hypothesis to another model to simulate pre-existence
        hypo_g = aifutils.make_graph()
        hypo_g += g
        bob_lives_in_california = self._add_bob_hypothesis(hypo_g, bob, system)

        # There is a GPE, California
        google = aifutils.make_entity(g, "http://www.test.edu/entities/Google", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/google_type", google, ldc_ontology.ORG, system, 1.0)

        # Create a relation stating tha bob works for google
        bob_works_for_google = aifutils.make_relation(g, "http://www.test.edu/relations/bob_works_for_google", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/relation_type", bob_works_for_google, ldc_ontology.OrganizationAffiliation_EmploymentMembership, system, 1.0)

        # Add bob and google as arguments to the relation
        aifutils.mark_as_argument(g, bob_works_for_google,
            ldc_ontology.OrganizationAffiliation_EmploymentMembership_EmployeeMember, bob, system, 1.0,
            "http://www.test.org/arguments/bob_is_the_employee")
        aifutils.mark_as_argument(g, bob_works_for_google,
            ldc_ontology.OrganizationAffiliation_EmploymentMembership_PlaceOfEmploymentMembership, google, system, 1.0,
            "http://www.test.org/arguments/google_is_the_organization")

        # Mark the relation as dependent on the hypothesis that bob lives in california
        aifutils.mark_depends_on_hypothesis(g, bob_works_for_google, bob_lives_in_california)

        # print hypothesis graph
        self.new_file(hypo_g, "test_relation_based_on_preexisting_hypothesis__hypothesis.ttl")
        self.dump_graph(hypo_g, "Example of relation based on pre-existing hypothesis (hypothesis)")

        # print normal graph
        self.dump_graph(g, "Example of relation based on pre-existing hypothesis")

        # when validating, need both graphs
        g += hypo_g
        self.new_file(g, "test_relation_based_on_preexisting_hypothesis.ttl")

    def test_two_hypotheses(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # we want to represent that we know, regardless of hypothesis, that there is a person named Bob,
        # two companies (Google and Amazon), and two places (Seattle and California)
        bob = aifutils.make_entity(g, "http://www.test.edu/entites/Bob", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/1", bob, ldc_ontology.PER, system, 1.0)
        google = aifutils.make_entity(g, "http://www.test.edu/entities/Google", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/2", google, ldc_ontology.ORG, system, 1.0)
        amazon = aifutils.make_entity(g, "http://www.test.edu/entities/Amazon", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/3", amazon, ldc_ontology.ORG, system, 1.0)
        seattle = aifutils.make_entity(g, "http://www.test.edu/entities/Seattle", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/4", seattle, ldc_ontology.GPE, system, 1.0)
        california = aifutils.make_entity(g, "http://www.test.edu/entities/California", system)
        aifutils.mark_type(g, "http://www.test.org/assertions/5", california, ldc_ontology.GPE, system, 1.0)

        city_relation_subject = ldc_ontology.Physical_Resident_Resident_Resident
        city_relation_object = ldc_ontology.Physical_Resident_Resident_Place
        employee_relation_subject = ldc_ontology.OrganizationAffiliation_EmploymentMembership_EmployeeMember
        employee_relation_object = ldc_ontology.OrganizationAffiliation_EmploymentMembership_PlaceOfEmploymentMembership

        # under the background hypothesis that Bob lives in Seattle, we believe he works for Amazon
        bob_lives_in_seattle = aifutils.make_relation_in_event_form(g, "http://www.test.edu/relations/1",
                                                                    ldc_ontology.Physical_Resident_Resident,
                                                                    city_relation_subject,
                                                                    bob,
                                                                    city_relation_object,
                                                                    seattle,
                                                                    "http://www/test.org/assertions/6", system, 1.0)
        bob_lives_in_seattle_hypothesis = aifutils.make_hypothesis(g, "http://www.test.edu/hypotheses/1",
                                                                   [bob_lives_in_seattle], system)

        bob_works_for_amazon = aifutils.make_relation_in_event_form(g, "http://www.test.edu/relations/2",
                                                                    ldc_ontology.OrganizationAffiliation_EmploymentMembership,
                                                                    employee_relation_subject,
                                                                    bob,
                                                                    employee_relation_object,
                                                                    amazon,
                                                                    "http://www/test.org/assertions/7", system, 1.0)
        aifutils.mark_depends_on_hypothesis(g, bob_works_for_amazon, bob_lives_in_seattle_hypothesis)

        # under the background hypothesis that Bob lives in California, we believe he works for Google
        bob_lives_in_california = aifutils.make_relation_in_event_form(g, "http://www.test.edu/relations/3",
                                                                       ldc_ontology.Physical_Resident_Resident,
                                                                       city_relation_subject,
                                                                       bob,
                                                                       city_relation_object,
                                                                       california,
                                                                       "http://www/test.org/assertions/8", system, 1.0)
        bob_lives_in_california_hypothesis = aifutils.make_hypothesis(g, "http://www.test.edu/hypotheses/2",
                                                                      [bob_lives_in_california], system)

        bob_works_for_google = aifutils.make_relation_in_event_form(g, "http://www.test.edu/relations/4",
                                                                    ldc_ontology.OrganizationAffiliation_EmploymentMembership,
                                                                    employee_relation_subject,
                                                                    bob,
                                                                    employee_relation_object,
                                                                    google,
                                                                    "http://www/test.org/assertions/9", system, 1.0)
        aifutils.mark_depends_on_hypothesis(g, bob_works_for_google, bob_lives_in_california_hypothesis)
        self.new_file(g, "test_two_hypotheses.ttl")
        self.dump_graph(g, "Example of two hypotheses")


    def test_use_subgraph_confidences_to_show_mutually_exclusive_linked_event_argument_options(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # we make a resource for the event itself
        event = aifutils.make_event(g, "http://www.test.edu/events/1", system)

        # mark the event as a Personnel.Elect event; type is encoded separately so we can express uncertainty about type
        aifutils.mark_type(g, "http://www.test.edu/assertions/5", event, ldc_ontology.Conflict_Attack, system, 1.0)

        # create the two entities involved in the event
        bob = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/6", bob, ldc_ontology.PER, system, 1.0)

        fred = aifutils.make_entity(g, "http://www.test.edu/entities/2", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/7", fred, ldc_ontology.PER, system, 1.0)

        # we link all possible argument fillers to the event
        bob_hit_fred_assertions = [aifutils.mark_as_argument(g, event, URIRef(ldc_ontology.Conflict_Attack_Attacker), bob, system, None),
                                   aifutils.mark_as_argument(g, event, URIRef(ldc_ontology.Conflict_Attack_Target), fred, system, None)]

        fred_hit_bob_assertions = [aifutils.mark_as_argument(g, event, URIRef(ldc_ontology.Conflict_Attack_Attacker), fred, system, None),
                                   aifutils.mark_as_argument(g, event, URIRef(ldc_ontology.Conflict_Attack_Target), bob, system, None)]

        # then we mark these as mutually exclusive
        # we also mark confidence 0.2 that neither of these are true
        aifutils.mark_as_mutually_exclusive(g, { tuple(bob_hit_fred_assertions):0.6, tuple(fred_hit_bob_assertions):0.2}, system, 0.2)
        self.new_file(g, "test_use_subgraph_confidences_to_show_mutually_exclusive_linked_event_argument_options.ttl")
        self.dump_graph(g, "Example of subgraph confidences to show mutually exclusive linked event argument options")


    def test_create_seedling_event(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # we make a resource for the event itself
        event = aifutils.make_event(g, "http://www.test.edu/events/1", system)

        # mark the event as a Personnel.Elect event; type is encoded separately so we can express
        # uncertainty about type
        aifutils.mark_type(g, "http://www.test.edu/assertions/5", event, ldc_ontology.Personnel_Elect,
                           system, 1.0)

        # create the two entities involved in the event
        electee = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/7", electee, ldc_ontology.PER, system, 1.0)

        election_country = aifutils.make_entity(g, "http://www.test.edu/entities/2", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/8", election_country,
                           ldc_ontology.GPE, system, 1.0)

        # link those entities to the event
        aifutils.mark_as_argument(g, event, ldc_ontology.Personnel_Elect_Candidate, electee, system,
                                  .785)
        aifutils.mark_as_argument(g, event, ldc_ontology.Personnel_Elect_Place, election_country, system,
                                  .589)
        self.new_file(g, "test_create_seedling_event.ttl")
        self.dump_graph(g, "Example of seedling event")


    def test_create_seedling_event_with_event_argument_uri(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # we make a resource for the event itself
        event = aifutils.make_event(g, "http://www.test.edu/events/1", system)

        # mark the event as a Personnel.Elect event; type is encoded separately so we can express
        # uncertainty about type
        event_type_string = "Personnel.Elect"
        aifutils.mark_type(g, "http://www.test.edu/assertions/5", event, ldc_ontology.Personnel_Elect,
                           system, 1.0)

        # create the two entities involved in the event
        electee = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/7", electee, ldc_ontology.PER, system, 1.0)

        election_country = aifutils.make_entity(g, "http://www.test.edu/entities/2", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/8", election_country,
                           ldc_ontology.GPE, system, 1.0)

        # link those entities to the event
        aifutils.mark_as_argument(g, event, ldc_ontology.Personnel_Elect_Candidate, electee, system,
                                  .785, "http://www.test.edu/eventArgument/1")
        aifutils.mark_as_argument(g, event, ldc_ontology.Personnel_Elect_Place, election_country, system,
                                  .589, "http://www.test.edu/eventArgument/2")
        self.new_file(g, "test_create_seedling_event_with_event_argument_uri.ttl")
        self.dump_graph(g, "Example of seedling event with event assertion URI")


    def test_create_an_entity_with_image_justification_and_vector(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # it doesn't matter what URI we give entities, events, etc. so long as they are
        # unique
        entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)

        # in order to allow uncertainty about the type of an entity, we don't mark an
        # entity's type directly on the entity, but rather make a separate assertion for it
        # its URI doesn't matter either
        type_assertion = aifutils.mark_type(g, "http://www.test.org/assertions/1", entity,
                                            ldc_ontology.PER, system, 1.0)

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
        self.new_file(g, "test_create_an_entity_with_image_justification_and_vector.ttl")
        self.dump_graph(g, "Example of entity with image justification and vector")

    def test_make_entity(self):
        g = get_initialized_graph()

        system = aifutils.make_system_with_uri(g, "http://www.test.edu/system")
        entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        type_assertion = aifutils.mark_type(g, "http://www.test.edu/assertions/1", entity,
                                            ldc_ontology.PER, system, 1.0)

        aifutils.mark_text_justification(g, [entity, type_assertion], "NYT_ENG_20181231",
                                         42, 143, system, 0.973)

        self.new_file(g, "test_make_an_entity.ttl")
        self.dump_graph(g, "Example of creating an entity")
        self.assertEqual([type_assertion], aifutils.get_type_assertions(g, entity))

    def test_create_seedling_entity_with_alternate_names(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)

        # in order to allow uncertainty about the type of an entity, we don't mark an entity's type directly on the
        # entity, but rather make a separate assertion for it.
        type_assertion = aifutils.mark_type(g, "http://www.test.org/assertions/1", entity, ldc_ontology.PER,
                                            system, 1.0)

        # This is just a test to make sure that validation works for the different
        # mark types.  Rare that you would have all three with a single entity.
        aifutils.mark_name(g, entity, "Name One")
        aifutils.mark_name(g, entity, "N. One")
        aifutils.mark_name(g, entity, "N-Money")

        entity = aifutils.make_entity(g, "http://www.test.edu/entities/2", system)

        # in order to allow uncertainty about the type of an entity, we don't mark an entity's type directly on the
        # entity, but rather make a separate assertion for it.
        type_assertion = aifutils.mark_type(g, "http://www.test.org/assertions/2", entity, ldc_ontology.MON,
                                            system, 1.0)

        # Ensure MON can have text value
        aifutils.mark_text_value(g, entity, "$4")

        # Below are disabled because LDCOntology doesn't have any CanHaveNumericValue classes
        # aifutils.mark_numeric_value_as_double(g, entity, 100)
        # aifutils.mark_numeric_value_as_long(g, entity, 100)
        # aifutils.mark_numeric_value_as_string(g, entity, "100")

        self.new_file(g, "test_create_a_seedling_entity_with_alternate_names.ttl")
        self.dump_graph(g, "Example of seedling entity with alternate names")

    def test_create_compound_justification(self):
        g = get_initialized_graph()

        system = aifutils.make_system_with_uri(g, "http://www.test.edu/system")

        event = aifutils.make_event(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#V779961.00010", system)
        event_type_assertion = aifutils.mark_type(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#assertion-1", event, ldc_ontology.Personnel_Elect, system, 1.0)

        # create the two entities involved in the event
        putin = aifutils.make_entity(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#E781167.00398", system)
        person_type_assertion = aifutils.mark_type(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#assertion-2", putin, ldc_ontology.PER, system, 1.0)

        russia = aifutils.make_entity(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#E779954.00004", system)
        gpe_type_assertion = aifutils.mark_type(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#assertion-3", russia, ldc_ontology.GPE, system, 1.0)

        # link those entities to the event
        electee_argument = aifutils.mark_as_argument(g, event, ldc_ontology.Personnel_Elect_Candidate, putin, system, 0.785, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#assertion-4")
        place_argument = aifutils.mark_as_argument(g, event, ldc_ontology.Personnel_Elect_Place, russia, system, 0.589, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#assertion-5")


        # the justification provides the evidence for our claim about the entity's type
        # we attach this justification to both the type assertion and the entity object itself, since it provides
        # evidence both for the entity's existence and its type.
        # in TA1 -> TA2 communications, we attach confidences at the level of justifications
        text_justification = aifutils.make_text_justification(g, "NYT_ENG_20181231",
                                                              42, 143, system, 0.973)
        aifutils.mark_justification(g, person_type_assertion, text_justification)
        aifutils.mark_justification(g, putin, text_justification)
        aifutils.add_source_document_to_justification(g, text_justification, "NYT_PARENT_ENG_20181231_03")

        bb1 = Bounding_Box((123, 45), (167, 98))
        # let's suppose we also have evidence from an image
        image_justification = aifutils.make_image_justification(g, "NYT_ENG_20181231_03",
                                                                bb1, system, 0.123)
        bb2 = Bounding_Box((234, 56), (345, 101))
        # and also a video where the entity appears in a keyframe
        keyframe_video_justification = aifutils.make_keyframe_video_justification(g, "NYT_ENG_20181231_03", "keyframe ID",
                                                                                  bb2, system, .0234)
        #and also a video where the entity does not appear in a keyframe
        shot_video_justification = aifutils.make_shot_video_justification(g, "SOME_VIDEO", "some shot ID", system, 0.487)
        # and even audio!
        audio_justification = aifutils.make_audio_justification(g, "NYT_ENG_201181231", 4.566, 9.876, system, 0.789)
        # and time-bound video
        video_justification = aifutils.make_video_justification(g, "OTHER_VIDEO", 1.0, 1.5,
            interchange_ontology.VideoJustificationChannelBoth, system, 0.93)

        # combine all justifications into single justifiedBy triple with new confidence
        aifutils.mark_compound_justification(g, [electee_argument],
                                             [text_justification, image_justification, keyframe_video_justification,
                                              shot_video_justification, audio_justification, video_justification],
                                             system, .321)

        aifutils.mark_compound_justification(g, [place_argument], [text_justification, image_justification], system, 0.543)

        self.new_file(g, "test_create_compound_justification.ttl")
        self.dump_graph(g, "Example of compound justification")


    def test_create_hierarchical_cluster(self):
        # we want to say that the cluster of Trump entities might be the same as the cluster of the president entities
        g = get_initialized_graph()

        #every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, 'http://www.test.edu/testSystem')

        # create president entities
        president_usa = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/1", president_usa, ldc_ontology.GPE,
                           system, 1.0)
        aifutils.mark_name(g, president_usa, "the president")

        new_president = aifutils.make_entity(g, "http://www.test.edu/entities/2", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/2", president_usa, ldc_ontology.GPE,
                           system, 1.0)
        aifutils.mark_name(g, president_usa, "the newly-inaugurated president")

        president_45 = aifutils.make_entity(g, "http://www.test.edu/entities/3", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/3", president_usa, ldc_ontology.GPE,
                           system, 1.0)
        aifutils.mark_name(g, president_usa, "the 45th president")

        # cluster president entities
        president_cluster = aifutils.make_cluster_with_prototype(g, "http://www.test.edu/clusters/president",
                                                                 president_usa, system)

        aifutils.mark_as_possible_cluster_member(g, president_usa, president_cluster, 1, system)
        aifutils.mark_as_possible_cluster_member(g, new_president, president_cluster, .9, system)
        aifutils.mark_as_possible_cluster_member(g, president_45, president_cluster, .9, system)

        # create Trump entities
        donald_trump = aifutils.make_entity(g, "http://www.test.edu/entities/4", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/4", donald_trump, ldc_ontology.PER, system, 1.0)
        aifutils.mark_name(g, president_usa, "Donald Trump")

        trump = aifutils.make_entity(g, "http://www.test.edu/entities/5", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/5", president_usa, ldc_ontology.PER, system, 1.0)
        aifutils.mark_name(g, president_usa, "Trump")

        # cluster trump entities
        trump_cluster = aifutils.make_cluster_with_prototype(g, "http://www.test.edu/clusters/trump", donald_trump, system)
        aifutils.mark_as_possible_cluster_member(g, donald_trump, trump_cluster, 1, system)
        aifutils.mark_as_possible_cluster_member(g, trump, trump_cluster, .9, system)

        aifutils.mark_as_possible_cluster_member(g, president_cluster, trump_cluster, .6, system)

        self.new_file(g, "test_create_hierarchical_cluster.ttl")
        self.dump_graph(g, "Seedling hierarchical cluster")

    def test_simple_hypothesis_with_cluster(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, 'http://www.test.edu/testSystem')
        # buk document entity
        buk = aifutils.make_entity(g, "E779954.00005", system)
        buk_is_weapon = aifutils.mark_type(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#assertion-1", buk, ldc_ontology.WEA, system, 1.0)

        # buk cross-document-entity
        buk_kb_entity = aifutils.make_entity(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#E0084", system)
        buk_kb_is_weapon = aifutils.mark_type(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#assertion-2", buk_kb_entity, ldc_ontology.WEA, system, 1.0)

        # russia document entity
        russia = aifutils.make_entity(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#E779954.00004", system)
        russia_is_gpe = aifutils.mark_type(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#assertion-3", russia, ldc_ontology.GPE, system, 1.0)

        # cluster buk
        buk_cluster = aifutils.make_cluster_with_prototype(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#cluster-1", buk_kb_entity, system)
        buk_is_clustered = aifutils.mark_as_possible_cluster_member(g, buk, buk_cluster, .9, system)

        # Russia owns buk relation
        buk_is_russian = aifutils.make_relation(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#R779959.00004", system)
        aifutils.mark_type(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#assertion-4",
            buk_is_russian, ldc_ontology.GeneralAffiliation_ArtifactPoliticalOrganizationReligiousAffiliation_OwnershipPossession, system, 1.0)
        buk_argument = aifutils.mark_as_argument(g, buk_is_russian,
            ldc_ontology.GeneralAffiliation_ArtifactPoliticalOrganizationReligiousAffiliation_OwnershipPossession_Artifact, buk, system, 1.0)
        russia_argument = aifutils.mark_as_argument(g, buk_is_russian,
            ldc_ontology.GeneralAffiliation_ArtifactPoliticalOrganizationReligiousAffiliation_OwnershipPossession_Owner, russia, system, 1.0)

        # Russia owns buk hypothesis
        buk_is_russian_hypothesis = aifutils.make_hypothesis(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#hypothesis-1",
                                                            [buk, buk_is_weapon, buk_is_clustered, buk_is_russian, buk_argument, russia_argument], system)

        self.new_file(g, "test_simple_hypothesis_with_cluster.ttl")
        self.dump_graph(g, "Simple hypothesis with cluster")

    def test_simple_hypothesis_with_importance_with_cluster(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, 'http://www.test.edu/testSystem')
        # buk document entity
        buk = aifutils.make_entity(g, "E779954.00005", system)
        buk_is_weapon = aifutils.mark_type(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#assertion-1", buk, ldc_ontology.WEA, system, 1.0)

        # buk cross-document-entity
        buk_kb_entity = aifutils.make_entity(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#E0084", system)
        buk_kb_is_weapon = aifutils.mark_type(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#assertion-2", buk_kb_entity, ldc_ontology.WEA, system, 1.0)

        # russia document entity
        russia = aifutils.make_entity(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#E779954.00004", system)
        russia_is_gpe = aifutils.mark_type(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#assertion-3", russia, ldc_ontology.GPE, system, 1.0)

        # cluster buk
        buk_cluster = aifutils.make_cluster_with_prototype(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#cluster-1", buk_kb_entity, system)
        buk_is_clustered = aifutils.mark_as_possible_cluster_member(g, buk, buk_cluster, .9, system)
        # add importance to the cluster - test negative importance
        aifutils.mark_importance(g, buk_kb_entity, -70.234)

        # Russia owns buk relation
        buk_is_russian = aifutils.make_relation(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#R779959.00004", system)
        aifutils.mark_type(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#assertion-4", buk_is_russian, ldc_ontology.GeneralAffiliation_ArtifactPoliticalOrganizationReligiousAffiliation_OwnershipPossession, system, 1.0)
        buk_argument = aifutils.mark_as_argument(g, buk_is_russian, ldc_ontology.GeneralAffiliation_ArtifactPoliticalOrganizationReligiousAffiliation_OwnershipPossession_Artifact, buk, system, 1.0)
        russia_argument = aifutils.mark_as_argument(g, buk_is_russian, ldc_ontology.GeneralAffiliation_ArtifactPoliticalOrganizationReligiousAffiliation_OwnershipPossession_Owner, russia, system, 1.0)
        # add importance to the statements
        aifutils.mark_importance(g, buk_argument, 100.0)
        # add large importance
        aifutils.mark_importance(g, russia_argument, 9.999999e6)

        # Russia owns buk hypothesis
        buk_is_russian_hypothesis = aifutils.make_hypothesis(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#hypothesis-1",
                                                            [buk, buk_is_weapon, buk_is_clustered, buk_is_russian, buk_argument, russia_argument], system)
        # test highest possible importance value
        aifutils.mark_importance(g, buk_is_russian_hypothesis, sys.float_info.max)

        self.new_file(g, "test_simple_hypothesis_with_importance_cluster.ttl")
        self.dump_graph(g, "Simple hypothesis with importance with cluster")

    def test_create_a_simple_cluster_with_handle(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, 'http://www.test.edu/testSystem')

        # Two people, probably the same person
        vladimir_putin = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/1", vladimir_putin, ldc_ontology.PER,
                           system, 1.0)
        aifutils.mark_name(g, vladimir_putin, "Vladimir Putin")

        putin = aifutils.make_entity(g, "http://www.test.edu/entities/2", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/2", putin, ldc_ontology.PER,
                           system, 1.0)

        aifutils.mark_name(g, putin, "Путин")

        # create a cluster with prototype
        putin_cluster = aifutils.make_cluster_with_prototype(g, "http://www.test.edu/clusters/1", vladimir_putin, system, "Vladimir Putin")

        # person 1 is definitely in the cluster, person 2 is probably in the cluster
        aifutils.mark_as_possible_cluster_member(g, putin, putin_cluster, 0.71, system)

        self.new_file(g, "test_create_a_simple_cluster_with_handle.ttl")
        self.dump_graph(g, "create a simple cluster with handle")

    def test_create_an_entity_with_information_justification(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, 'http://www.test.edu/testSystem')

        # Two people, probably the same person
        vladimir_putin = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        aifutils.mark_name(g, vladimir_putin, "Vladimir Putin")

        type_assertion = aifutils.mark_type(g, "http://www.test.org/assertions/1", vladimir_putin,
            ldc_ontology.PER, system, 1.0)

        text_justification_1 = aifutils.mark_text_justification(g, [vladimir_putin, type_assertion], "HC00002Z0", 0, 10, system, 1.0)
        aifutils.mark_informative_justification(g, vladimir_putin, text_justification_1)

        putin = aifutils.make_entity(g, "http://www.test.edu/entities/2", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/2", putin, ldc_ontology.PER,
                           system, 1.0)

        aifutils.mark_name(g, putin, "Путин")

        # create a cluster with prototype
        putin_cluster = aifutils.make_cluster_with_prototype(g, "http://www.test.edu/clusters/1", vladimir_putin, system, "Vladimir Putin")
        text_justification_2 = aifutils.mark_text_justification(g, [putin, type_assertion], "HC00002Z0", 0, 10, system, 1.0)
        aifutils.mark_informative_justification(g, putin_cluster, text_justification_2)

        # person 1 is definitely in the cluster, person 2 is probably in the cluster
        aifutils.mark_as_possible_cluster_member(g, putin, putin_cluster, 0.71, system)

        self.new_file(g, "test_create_an_entity_and_cluster_with_informative_mention.ttl")
        self.dump_graph(g, "create an entity and cluster with informative mention")


    def test_create_a_cluster_with_link_and_confidence(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        putin = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/1", putin, ldc_ontology.PER,
                system, 1.0)
        aifutils.mark_name(g, putin, "Путин")

        vladimir_putin = aifutils.make_entity(g, "http://www.test.edu/entities/2", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/2", vladimir_putin, ldc_ontology.PER,
                system, 1.0)
        aifutils.mark_name(g, vladimir_putin, "Vladimir Putin")

        # create a cluster with prototype
        putin_cluster = aifutils.make_cluster_with_prototype(g, "http://www.test.edu/clusters/1", vladimir_putin, system, "Vladimir Putin")

        # person 1 is definitely in the cluster, person 2 is probably in the cluster
        aifutils.mark_as_possible_cluster_member(g, putin, putin_cluster, 1.0, system)
        aifutils.mark_as_possible_cluster_member(g, vladimir_putin, putin_cluster, 0.71, system)

        # also we can link this entity to something in an external KB
        aifutils.link_to_external_kb(g, putin_cluster, "freebase.FOO", system, .398)

        self.new_file(g, "test_create_a_cluster_with_link_and_confidence.ttl")
        self.dump_graph(g, "create a cluster with link and confidence")

    def test_create_an_event_with_ldc_time(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # create a start position event with unknown start and end time
        event_start_position = aifutils.make_event(g, "http://www.test.edu/event/1", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/1", event_start_position, ldc_ontology.Personnel_StartPosition, system, 1.0)
        unknown = LDCTimeComponent(LDCTimeType.UNKNOWN, None, None, None)
        endBefore = LDCTimeComponent(LDCTimeType.BEFORE, "2016", None, None)
        aifutils.mark_ldc_time(g, event_start_position, unknown, endBefore, system)

        # create an attack event with an unknown start date, but definite end date
        event_attack_unknown = aifutils.make_event(g, "http://www.test.edu/event/2", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/2", event_attack_unknown, ldc_ontology.Conflict_Attack, system, 1.0)
        start = LDCTimeComponent(LDCTimeType.AFTER, "2014", "--02", None)
        end = LDCTimeComponent(LDCTimeType.ON, "2014", "--02", "---21")
        aifutils.mark_ldc_time(g, event_attack_unknown, start, end, system)

        self.new_file(g, "test_create_an_event_with_ldc_time.ttl")
        self.dump_graph(g, "create an event with LDCTime")

    def test_create_an_event_with_ldc_time_range(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # create an attack event with an unknown start date range, but definite end date range
        event_attack_unknown = aifutils.make_event(g, "http://www.test.edu/event/2", system)
        aifutils.mark_type(g, "http://www.test.edu/assertions/2", event_attack_unknown, ldc_ontology.Conflict_Attack, system, 1.0)
        startE = LDCTimeComponent(LDCTimeType.AFTER, "2014", "--02", None)
        startL = LDCTimeComponent(LDCTimeType.BEFORE, "2014", "--03", None)
        endE = LDCTimeComponent(LDCTimeType.AFTER, "2015", "--02", "---21")
        endL = LDCTimeComponent(LDCTimeType.BEFORE, "2015", "--02", "---26")
        aifutils.mark_ldc_time_range(g, event_attack_unknown, startE, startL, endE, endL, system)

        self.new_file(g, "test_create_an_event_with_ldc_time_range.ttl")
        self.dump_graph(g, "create an event with LDCTime range")


    def test_create_an_event_add_attribute(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # we make a resource for the event itself
        event = aifutils.make_event(g, "http://www.test.edu/events/1", system)

        aifutils.mark_attribute(g, event, interchange_ontology.Negated)
        aifutils.mark_attribute(g, event, interchange_ontology.Hedged)
        aifutils.mark_attribute(g, event, interchange_ontology.Irrealis)
        aifutils.mark_attribute(g, event, interchange_ontology.Generic)


        self.new_file(g, "test_create_an_event_add_Attribute.ttl")
        self.dump_graph(g, "Example of creating an event and adding valid semantic attributes")


    def test_create_a_relation_add_attribute(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # we make a resource for the event itself
        relation = aifutils.make_relation(g, "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#R779959.00004", system)

        aifutils.mark_attribute(g, relation, interchange_ontology.Negated)
        aifutils.mark_attribute(g, relation, interchange_ontology.Hedged)
        aifutils.mark_attribute(g, relation, interchange_ontology.Irrealis)
        aifutils.mark_attribute(g, relation, interchange_ontology.Generic)


        self.new_file(g, "test_create_a_relation_add_attribute.ttl")
        self.dump_graph(g, "Example of creating a relation and adding valid semantic attributes")

    def test_create_an_event_argument_add_attribute(self):
        g = get_initialized_graph()

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

        aifutils.mark_attribute(g, argument1, interchange_ontology.Negated)
        aifutils.mark_attribute(g, argument1, interchange_ontology.Hedged)

        aifutils.mark_attribute(g, argument2, interchange_ontology.Negated)
        aifutils.mark_attribute(g, argument2, interchange_ontology.Hedged)


        self.new_file(g, "test_create_a_relation_add_attribute.ttl")
        self.dump_graph(g, "Example of creating an event argument and addeding valid semantic attributes")

    def test_create_an_entity_with_add_attribute(self):
        g = get_initialized_graph()

        # every AIF needs an object for the system responsible for creating it
        system = aifutils.make_system_with_uri(g, "http://www.test.edu/testSystem")

        # it doesn't matter what URI we give entities, events, etc. so long as they are
        # unique
        entity = aifutils.make_entity(g, "http://www.test.edu/entities/1", system)


        aifutils.mark_attribute(g, entity, interchange_ontology.Generic)

        self.new_file(g, "test_create_an_entity_with_add_attribute.ttl")
        self.dump_graph(g, "Example of creating an entity and adding valid semantic attributes")

    def dump_graph(self, g, description):
        print("\n\n======================================\n"
              "{!s}\n"
              "======================================\n\n".format(description))
        serialization = BytesIO()
        # need .buffer because serialize will write bytes, not str
        g.serialize(destination=serialization, format='turtle')
        print(serialization.getvalue().decode('utf-8'))

if __name__ == '__main__':
    # get directory path
    Examples.test_dir_path = os.environ.get("DIR_PATH", None)
    if Examples.test_dir_path is not None:
        if not os.path.exists(Examples.test_dir_path):
            Examples.test_dir_path = None
            print("Test output directory does not exist. Example turtle files will not be saved")
    else:
        print("Test output directory was not provided. Example turtle files will not be saved")

    unittest.main()
