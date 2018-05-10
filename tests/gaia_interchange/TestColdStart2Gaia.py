from unittest import TestCase

from flexnlp.utils.io_utils import CharSource
from flexnlp_sandbox.formats.tac.coldstart import ColdStartKBLoader
from rdflib import Graph, RDF, Literal, URIRef
from rdflib.namespace import SKOS

from gaia_interchange.aida_rdf_ontologies import AIDA_PROGRAM_ONTOLOGY, \
  AIDA_ANNOTATION
from gaia_interchange.coldstart2gaia import ColdStartToGaiaConverter, \
  BlankNodeGenerator


class TestColdstartToGaia(TestCase):
    _CS_FOR_ENTITY_TEST = (":Entity_EDL_ENG_0000001\ttype\tPER\n:Entity_EDL_ENG_0000001"
                           "\tcanonical_mention\t\"Hollande Heads\"\tVOA_EN_NW_2015.11.26.3074625:"
                           "0-13\t0.875\n:Entity_EDL_ENG_0000001\tmention\t\"Hollande Heads\"\t"
                           "VOA_EN_NW_2015.11.26.3074625:14-26\t0.123")

    _CONVERTER = ColdStartToGaiaConverter()

    def test_entity_translation(self) -> None:
        cs_kb = ColdStartKBLoader().load(
            CharSource.from_string(TestColdstartToGaia._CS_FOR_ENTITY_TEST))
        system_node = URIRef("http://www.isi.edu/conversionTest")
        converted_rdf = TestColdstartToGaia._CONVERTER.convert_coldstart_to_gaia(
            str(system_node), cs_kb)

        # build the correct answer graph
        ref_graph = Graph()
        node_gen = BlankNodeGenerator()
        entity_node = node_gen.next_node()

        type_assertion = node_gen.next_node()
        ref_graph.add((type_assertion, RDF.subject, entity_node))
        ref_graph.add((type_assertion, RDF.predicate, RDF.type))
        ref_graph.add((type_assertion, RDF.object, AIDA_PROGRAM_ONTOLOGY.Person))
        ref_graph.add((type_assertion, AIDA_ANNOTATION.system, system_node))

        # canonical string
        ref_graph.add((entity_node, SKOS.prefLabel, Literal("Hollande Heads")))

        # note what system the entity itself came from
        ref_graph.add((entity_node, AIDA_ANNOTATION.system, system_node))

        # add justifications
        first_justification = node_gen.next_node()
        ref_graph.add(
            (entity_node, AIDA_ANNOTATION.justifiedBy, first_justification))
        ref_graph.add(
            (first_justification, RDF.type, AIDA_ANNOTATION.TextProvenance))
        ref_graph.add((first_justification, AIDA_ANNOTATION.source,
                       Literal("VOA_EN_NW_2015.11.26.3074625")))
        ref_graph.add(
            (first_justification, AIDA_ANNOTATION.startOffset, Literal(0)))
        # TODO check inclusive/exclusive
        ref_graph.add((first_justification, AIDA_ANNOTATION.endOffsetInclusive,
                       Literal(13)))
        ref_graph.add(
            (first_justification, AIDA_ANNOTATION.system, system_node))

        first_confidence_node = node_gen.next_node()
        ref_graph.add((first_confidence_node, AIDA_ANNOTATION.confidenceValue,
                       Literal(0.875)))
        ref_graph.add(
            (first_confidence_node, AIDA_ANNOTATION.system, system_node))
        ref_graph.add((first_justification, AIDA_ANNOTATION.confidence,
                       first_confidence_node))

        second_justification = node_gen.next_node()
        ref_graph.add(
            (entity_node, AIDA_ANNOTATION.justifiedBy, second_justification))
        ref_graph.add(
            (second_justification, RDF.type, AIDA_ANNOTATION.TextProvenance))
        ref_graph.add((second_justification, AIDA_ANNOTATION.source,
                       Literal("VOA_EN_NW_2015.11.26.3074625")))
        ref_graph.add(
            (second_justification, AIDA_ANNOTATION.startOffset, Literal(14)))
        # TODO check inclusive/exclusive
        ref_graph.add((second_justification, AIDA_ANNOTATION.endOffsetInclusive,
                       Literal(26)))
        ref_graph.add(
            (second_justification, AIDA_ANNOTATION.system, system_node))

        second_confidence_node = node_gen.next_node()
        ref_graph.add((second_confidence_node, AIDA_ANNOTATION.confidenceValue,
                       Literal(0.123)))
        ref_graph.add(
            (second_confidence_node, AIDA_ANNOTATION.system, system_node))
        ref_graph.add((second_justification, AIDA_ANNOTATION.confidence,
                       second_confidence_node))

        ref_graph.namespace_manager.bind('aida', AIDA_ANNOTATION)
        ref_graph.namespace_manager.bind('aidaProgramOntology', AIDA_PROGRAM_ONTOLOGY)
        ref_graph.namespace_manager.bind('skos', SKOS)

        print("Ref")
        print(ref_graph.serialize(format='turtle').decode('utf-8'))
        print(converted_rdf.serialize(format='turtle').decode('utf-8'))

        self.assertTrue(ref_graph.isomorphic(converted_rdf))

# TODO: parse plain mention assertions
