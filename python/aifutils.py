import uuid
from abc import abstractmethod, ABCMeta

from rdflib import URIRef, RDF, Graph, BNode, Literal, XSD
from aida_interchange.aida_rdf_ontologies import AIDA_ANNOTATION

class IriGenerator(metaclass=ABCMeta):
    """
    A strategy for generating a sequence of IRIs.
    """
    @abstractmethod
    def next_iri(self):
        """
        Get the next IRI in this sequence.
        """


class UuidIriGenerator(IriGenerator):
    """
    Creates a sequences of IRIs using UUIDs
    """
    def __init__(self, base_iri: str):
        if not base_iri:
            raise RuntimeError("Base IRI may not be empty")
        if base_iri.endswith('/'):
            raise RuntimeError(f"Base IRI may not end with / but got {base_iri}")
        self.base_iri = base_iri

    def next_iri(self):
        return self.base_iri + '/' + str(uuid.uuid4())

def make_graph():
    g = Graph()
    g.bind('aida', AIDA_ANNOTATION.uri)
    return g


def make_system_with_uri(graph, system_uri):
    system = URIRef(system_uri)
    graph.add((system, RDF.type, AIDA_ANNOTATION.system))
    return system


def mark_system(g, to_mark_on, system):
    g.add((to_mark_on, AIDA_ANNOTATION.system, system))


def make_entity(graph, entity_uri, system):
    entity = URIRef(entity_uri)
    graph.add((entity, RDF.type, AIDA_ANNOTATION.Entity))
    mark_system(graph, entity, system)
    return entity


def mark_type(g, type_assertion_uri, entity_or_event,
              type, system, confidence):
    type_assertion = URIRef(type_assertion_uri)
    g.add((type_assertion, RDF.type, RDF.Statement))
    g.add((type_assertion, RDF.subject, entity_or_event))
    g.add((type_assertion, RDF.predicate, RDF.type))
    g.add((type_assertion, RDF['object'], type))
    mark_system(g, type_assertion, system)
    mark_confidence(g, type_assertion, confidence, system)
    return type_assertion


def mark_text_justification(g, things_to_justify, doc_id, start_offset,
                            end_offset_inclusive, system, confidence):
    justification = BNode()
    g.add((justification, RDF.type, AIDA_ANNOTATION.TextJustification))
    g.add((justification, AIDA_ANNOTATION.source,
           Literal(doc_id, datatype=XSD.string)))
    g.add((justification, AIDA_ANNOTATION.startOffset,
           Literal(start_offset, datatype=XSD.integer)))
    g.add((justification, AIDA_ANNOTATION.endOffsetInclusive,
           Literal(end_offset_inclusive, datatype=XSD.integer)))
    mark_system(g, justification, system)
    mark_confidence(g, justification, confidence, system)

    for things_to_justify in things_to_justify:
        g.add((things_to_justify, AIDA_ANNOTATION.justifiedBy, justification))
    return justification


def mark_confidence(g, to_mark_on, confidence, system):
    confidence_blank_node = BNode()
    g.add((confidence_blank_node, RDF.type, AIDA_ANNOTATION.Confidence))
    g.add((confidence_blank_node, AIDA_ANNOTATION.confidenceValue,
           Literal(confidence, datatype=XSD.double)))
    mark_system(g, confidence_blank_node, system)
    g.add((to_mark_on, AIDA_ANNOTATION.confidence, confidence_blank_node))


def make_relation(g, relation_uri, first_arg, relation_type,
                  second_arg, system, confidence):
    relation = URIRef(relation_uri)
    g.add((relation, RDF.type, RDF.Statement))
    g.add((relation, RDF.subject, first_arg))
    g.add((relation, RDF.predicate, relation_type))
    g.add((relation, RDF['object'], second_arg))
    mark_system(g, relation, system)
    mark_confidence(g, relation, confidence, system)
    return relation


def mark_as_event_argument(g, event, argument_type, argument_filler, system, confidence):
    arg_assertion = BNode()
    g.add((arg_assertion, RDF.type, RDF.Statement))
    g.add((arg_assertion, RDF.subject, event))
    g.add((arg_assertion, RDF.predicate, argument_type))
    g.add((arg_assertion, RDF['object'], argument_filler))
    mark_system(g, arg_assertion, system)
    mark_confidence(g, arg_assertion, confidence, system)
    return arg_assertion


def make_event(g, event_uri, system):
    event = URIRef(event_uri)
    g.add((event, RDF.type, AIDA_ANNOTATION.Event))
    g.add((event, AIDA_ANNOTATION.System, system))
    return event


def mark_image_justification(g, things_to_justify, doc_id, boundingbox, system, confidence):
    justification = BNode()
    g.add((justification, RDF.type, AIDA_ANNOTATION.ImageJustification))
    g.add((justification, AIDA_ANNOTATION.source,
           Literal(doc_id, datatype=XSD.string)))

    bounding_box_resource = BNode()
    g.add((bounding_box_resource, RDF.type, AIDA_ANNOTATION.BoundingBox))
    g.add((bounding_box_resource, AIDA_ANNOTATION.boundingBoxUpperLeftX,
           Literal(boundingbox.upper_left[0], datatype=XSD.integer)))
    g.add((bounding_box_resource, AIDA_ANNOTATION.boundingBoxUpperLeftY,
           Literal(boundingbox.upper_left[1], datatype=XSD.integer)))
    g.add((bounding_box_resource, AIDA_ANNOTATION.boundingBoxLowerRightX,
           Literal(boundingbox.lower_right[0], datatype=XSD.integer)))
    g.add((bounding_box_resource, AIDA_ANNOTATION.boundingBoxLowerRightY,
           Literal(boundingbox.lower_right[1], datatype=XSD.integer)))

    g.add((justification, AIDA_ANNOTATION.boundingBox, bounding_box_resource))
    mark_system(g, justification, system)
    mark_confidence(g, justification, confidence, system)

    for things in things_to_justify:
        g.add((things, AIDA_ANNOTATION.justifiedBy, justification))

    return justification


def mark_audio_justification(g, things_to_justify, doc_id, start_timestamp, end_timestamp, system, confidence):
    #TODO throw exception if start_timestamp > end_timestamp
    justification = BNode()
    g.add((justification, RDF.type, AIDA_ANNOTATION.AudioJustification))
    g.add((justification, AIDA_ANNOTATION.source,
           Literal(doc_id, datatype=XSD.string)))
    g.add((justification, AIDA_ANNOTATION.StartTimestamp,
           Literal(start_timestamp, datatype=XSD.integer)))
    g.add((justification, AIDA_ANNOTATION.EndTimestamp,
           Literal(end_timestamp, datatype=XSD.integer)))
    mark_system(g, justification, system)
    mark_confidence(g, justification, confidence, system)

    for things_to_justify in things_to_justify:
        g.add((things_to_justify, AIDA_ANNOTATION.justifiedBy, justification))
    return justification


def mark_keyframe_video_justification(g, things_to_justify, doc_id, key_frame, boundingbox, system, confidence):
    justification = BNode()
    g.add((justification, RDF.type, AIDA_ANNOTATION.KeyFrameVideoJustification))
    g.add((justification, AIDA_ANNOTATION.source,
           Literal(doc_id, datatype=XSD.string)))
    g.add((justification, AIDA_ANNOTATION.keyFrame,
           Literal(key_frame, datatype=XSD.string)))

    bounding_box_resource = BNode()
    g.add((bounding_box_resource, RDF.type, AIDA_ANNOTATION.BoundingBox))
    g.add((bounding_box_resource, AIDA_ANNOTATION.boundingBoxUpperLeftX,
           Literal(boundingbox.upper_left[0], datatype=XSD.integer)))
    g.add((bounding_box_resource, AIDA_ANNOTATION.boundingBoxUpperLeftY,
           Literal(boundingbox.upper_left[1], datatype=XSD.integer)))
    g.add((bounding_box_resource, AIDA_ANNOTATION.boundingBoxLowerRightX,
           Literal(boundingbox.lower_right[0], datatype=XSD.integer)))
    g.add((bounding_box_resource, AIDA_ANNOTATION.boundingBoxLowerRightY,
           Literal(boundingbox.lower_right[1], datatype=XSD.integer)))
    mark_system(g, justification, system)
    mark_confidence(g, justification, confidence, system)

    for things in things_to_justify:
        g.add((things, AIDA_ANNOTATION.justifiedBy, justification))

    return justification


def mark_shot_video_justification(g, things_to_justify, doc_id, shot_id, system, confidence):
    justification = BNode()
    g.add((justification, RDF.type, AIDA_ANNOTATION.ShotVideoJustification))
    g.add((justification, AIDA_ANNOTATION.source,
           Literal(doc_id, datatype=XSD.string)))
    g.add((justification, AIDA_ANNOTATION.shot,
           Literal(shot_id, datatype=XSD.string)))
    mark_system(g, justification, system)
    mark_confidence(g, justification, confidence, system)

    for things_to_justify in things_to_justify:
        g.add((things_to_justify, AIDA_ANNOTATION.justifiedBy, justification))
    return justification


def make_cluster_with_prototype(g, cluster_uri, prototype, system):
    cluster = URIRef(cluster_uri)
    g.add((cluster, RDF.type, AIDA_ANNOTATION.SameAsCluster))
    g.add((cluster, AIDA_ANNOTATION.prototype, prototype))
    mark_system(g, cluster, system)
    return cluster


def mark_as_possible_cluster_member(g, possible_cluster_member, cluster, confidence, system):
    cluster_member_assertion = BNode()
    g.add((cluster_member_assertion, RDF.type, AIDA_ANNOTATION.ClusterMembership))
    g.add((cluster_member_assertion, AIDA_ANNOTATION.cluster, cluster))
    g.add((cluster_member_assertion, AIDA_ANNOTATION.clusterMember, possible_cluster_member))
    mark_confidence(g, cluster_member_assertion, confidence, system)
    mark_system(g, cluster_member_assertion, system)
    return cluster_member_assertion


def make_hypothesis(g, hypothesis_uri, hypothesis_content, system):
    #TODO throw exception if hypothesis_content is empty
    hypothesis = URIRef(hypothesis_uri)
    g.add((hypothesis, RDF.type, AIDA_ANNOTATION.Hypothesis))
    mark_system(g, hypothesis, system)

    subgraph = BNode()
    g.add((subgraph, RDF.type, AIDA_ANNOTATION.Subgraph))

    for content in hypothesis_content:
        g.add((subgraph, AIDA_ANNOTATION.graphContains, content))

    g.add((hypothesis, AIDA_ANNOTATION.hypothesisContent, subgraph))
    return hypothesis


def mark_depends_on_hypothesis(g, depender, hypothesis):
    g.add((depender, AIDA_ANNOTATION.dependsOnHypothesis, hypothesis))


def mark_as_mutually_exclusive(g, alternatives, system, none_of_the_above_prob):
    #TODO throw exception when alternatives has less than 2 mutually exclusive things
    mutual_exclusion_assertion = BNode()
    g.add((mutual_exclusion_assertion, RDF.type, AIDA_ANNOTATION.MutualExclusion))
    mark_system(g, mutual_exclusion_assertion, system)

    for alts in alternatives:
        alternative = BNode()
        g.add((alternative, RDF.type, AIDA_ANNOTATION.MutualExclusionAlternative))

        alternative_graph = BNode()
        g.add((alternative_graph, RDF.type, AIDA_ANNOTATION.Subgraph))
        print alts[0]
        for alt in alts[0]:
            g.add((alternative_graph, AIDA_ANNOTATION.graphContains, alt))

        g.add((alternative, AIDA_ANNOTATION.alternateGraph, alternative_graph))
        mark_confidence(g, alternative, alts[1], system)

        g.add((mutual_exclusion_assertion, AIDA_ANNOTATION.alternative, alternative))

    if none_of_the_above_prob is not None:
        g.add((mutual_exclusion_assertion, AIDA_ANNOTATION.noneOfTheAbove))

    return mutual_exclusion_assertion


def mark_private_data(g, resource, json_content, system):
    private_data = BNode()
    g.add((private_data, RDF.type, AIDA_ANNOTATION.PrivateData))
    g.add((private_data, AIDA_ANNOTATION.jsonContent,
           Literal(json_content, datatype=XSD.string)))
    mark_system(g, private_data, system)

    g.add((resource, AIDA_ANNOTATION.privateData, private_data))

    return private_data


def link_to_external_kb(g, to_link, external_kb_id, system, confidence):
    link_assertion = BNode()
    g.add((to_link, AIDA_ANNOTATION.link, link_assertion))
    g.add((link_assertion, RDF.type, AIDA_ANNOTATION.LinkAssertion))
    g.add((link_assertion, AIDA_ANNOTATION.linkTarget,
           Literal(external_kb_id, datatype=XSD.string)))
    mark_system(g, link_assertion, system)
    mark_confidence(g, link_assertion, confidence, system)
    return link_assertion

