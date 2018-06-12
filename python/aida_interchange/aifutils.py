import uuid
from abc import ABCMeta, abstractmethod

from rdflib import URIRef, RDF, Graph, BNode, Literal, XSD

from aida_rdf_ontologies import AIDA_ANNOTATION
from rdflib.plugins.sparql import prepareQuery

"""
A convenient interface for creating simple AIF graphs.

More complicated graphs will require direct manipulation of the RDF
"""

def make_graph():
    """
    Creates an RDF triple store

    :return: The created graph
    """
    g = Graph()
    g.bind('aida', AIDA_ANNOTATION.uri)
    return g


def make_system_with_uri(graph, system_uri):
    """
    Create a resource representing the system which produced some data.

    Such a resource should be attached to all entities, events, event arguments, relations,
    sentiment assertions, confidences, justifications, etc. produced by a system. You should
    only create the system resource once; reuse the returned objects for all calls
    to [markSystem].

    :return: The created system resource.
    """
    system = URIRef(system_uri)
    graph.add((system, RDF.type, AIDA_ANNOTATION.System))
    return system


def mark_system(g, to_mark_on, system):
    """
    Mark a resource as coming from the specified [system]
    """
    g.add((to_mark_on, AIDA_ANNOTATION.system, system))


def make_entity(graph, entity_uri, system):
    """
    Create an entity.

    :param entity_uri: can be any unique string.
    :param system: The system object for the system which created this entity.
    :return: The created entity resource
    """
    entity = URIRef(entity_uri)
    graph.add((entity, RDF.type, AIDA_ANNOTATION.Entity))
    mark_system(graph, entity, system)
    return entity


def mark_type(g, type_assertion_uri, entity_or_event,
              _type, system, confidence):
    """
    Mark an entity or event as having a specified type.

    :return: The assertion resource
    """
    type_assertion = URIRef(type_assertion_uri)
    g.add((type_assertion, RDF.type, RDF.Statement))
    g.add((type_assertion, RDF.subject, entity_or_event))
    g.add((type_assertion, RDF.predicate, RDF.type))
    g.add((type_assertion, RDF['object'], _type))
    mark_system(g, type_assertion, system)
    mark_confidence(g, type_assertion, confidence, system)
    return type_assertion


def mark_text_justification(g, things_to_justify, doc_id, start_offset,
                            end_offset_inclusive, system, confidence):
    """
    Mark multiple things as being justified by a particular snippet of text.

    :return: The text justification resource created.
    """
    if isinstance(things_to_justify, URIRef):
        things_to_justify = [things_to_justify]

    justification = BNode()
    g.add((justification, RDF.type, AIDA_ANNOTATION.TextJustification))
    g.add((justification, AIDA_ANNOTATION.source,
           Literal(doc_id, datatype=XSD.string)))
    g.add((justification, AIDA_ANNOTATION.startOffset,
           Literal(start_offset, datatype=XSD.int)))
    g.add((justification, AIDA_ANNOTATION.endOffsetInclusive,
           Literal(end_offset_inclusive, datatype=XSD.int)))
    mark_system(g, justification, system)
    mark_confidence(g, justification, confidence, system)

    for things_to_justify in things_to_justify:
        g.add((things_to_justify, AIDA_ANNOTATION.justifiedBy, justification))
    return justification


def mark_confidence(g, to_mark_on, confidence, system):
    """
    Mark a confidence value on a resource.

    """
    confidence_blank_node = BNode()
    g.add((confidence_blank_node, RDF.type, AIDA_ANNOTATION.Confidence))
    g.add((confidence_blank_node, AIDA_ANNOTATION.confidenceValue,
           Literal(confidence, datatype=XSD.double)))
    mark_system(g, confidence_blank_node, system)
    g.add((to_mark_on, AIDA_ANNOTATION.confidence, confidence_blank_node))


def make_relation(g, relation_uri, first_arg, relation_type,
                  second_arg, system, confidence):
    """
    Makes a relation of type [relationType] between [firstArg] and [secondArg].

    If [confidence] is non-null the relation is marked with the given [confidence]

    :return: The relaton object
    """
    relation = URIRef(relation_uri)
    g.add((relation, RDF.type, RDF.Statement))
    g.add((relation, RDF.subject, first_arg))
    g.add((relation, RDF.predicate, relation_type))
    g.add((relation, RDF['object'], second_arg))
    mark_system(g, relation, system)
    mark_confidence(g, relation, confidence, system)
    return relation


def mark_as_event_argument(g, event, argument_type, argument_filler, system, confidence):
    """
    Marks an entity as filling an argument role for an event.

    :return: The created event argument assertion
    """
    arg_assertion = BNode()
    g.add((arg_assertion, RDF.type, RDF.Statement))
    g.add((arg_assertion, RDF.subject, event))
    g.add((arg_assertion, RDF.predicate, argument_type))
    g.add((arg_assertion, RDF['object'], argument_filler))
    mark_system(g, arg_assertion, system)
    if confidence is not None:
        mark_confidence(g, arg_assertion, confidence, system)
    return arg_assertion


def make_event(g, event_uri, system):
    """
    Create an event\

    :param event_uri: can be any unique string.
    :param system: The system object for the system which created this event.

    :return: The event resource
    """
    event = URIRef(event_uri)
    g.add((event, RDF.type, AIDA_ANNOTATION.Event))
    g.add((event, AIDA_ANNOTATION.system, system))
    return event


def mark_image_justification(g, things_to_justify, doc_id, boundingbox, system, confidence):
    """
    Marks a justification for something appearing in an image

    :return: The created image justification resource
    """
    if isinstance(things_to_justify, URIRef):
        things_to_justify = [things_to_justify]

    justification = BNode()
    g.add((justification, RDF.type, AIDA_ANNOTATION.ImageJustification))
    g.add((justification, AIDA_ANNOTATION.source,
           Literal(doc_id, datatype=XSD.string)))

    bounding_box_resource = BNode()
    g.add((bounding_box_resource, RDF.type, AIDA_ANNOTATION.BoundingBox))
    g.add((bounding_box_resource, AIDA_ANNOTATION.boundingBoxUpperLeftX,
           Literal(boundingbox.upper_left[0], datatype=XSD.int)))
    g.add((bounding_box_resource, AIDA_ANNOTATION.boundingBoxUpperLeftY,
           Literal(boundingbox.upper_left[1], datatype=XSD.int)))
    g.add((bounding_box_resource, AIDA_ANNOTATION.boundingBoxLowerRightX,
           Literal(boundingbox.lower_right[0], datatype=XSD.int)))
    g.add((bounding_box_resource, AIDA_ANNOTATION.boundingBoxLowerRightY,
           Literal(boundingbox.lower_right[1], datatype=XSD.int)))

    g.add((justification, AIDA_ANNOTATION.boundingBox, bounding_box_resource))
    mark_system(g, justification, system)
    mark_confidence(g, justification, confidence, system)

    for things in things_to_justify:
        g.add((things, AIDA_ANNOTATION.justifiedBy, justification))

    return justification


def mark_audio_justification(g, things_to_justify, doc_id, start_timestamp, end_timestamp, system, confidence):
    """
    Marks a justification for something referenced in audio

    :return: The created audio justification resource
    """
    if start_timestamp > end_timestamp:
        raise RuntimeError("start_timestamp cannot be larger than end_timestamp")

    if isinstance(things_to_justify, URIRef):
        things_to_justify = [things_to_justify]

    justification = BNode()
    g.add((justification, RDF.type, AIDA_ANNOTATION.AudioJustification))
    g.add((justification, AIDA_ANNOTATION.source,
           Literal(doc_id, datatype=XSD.string)))
    g.add((justification, AIDA_ANNOTATION.startTimestamp,
           Literal(start_timestamp, datatype=XSD.double)))
    g.add((justification, AIDA_ANNOTATION.endTimestamp,
           Literal(end_timestamp, datatype=XSD.double)))
    mark_system(g, justification, system)
    mark_confidence(g, justification, confidence, system)

    for things_to_justify in things_to_justify:
        g.add((things_to_justify, AIDA_ANNOTATION.justifiedBy, justification))
    return justification


def mark_keyframe_video_justification(g, things_to_justify, doc_id, key_frame, boundingbox, system, confidence):
    """
    Marks a justification for something appearing in a key frame of a video.

    :return: The justification resource
    """
    if isinstance(things_to_justify, URIRef):
        things_to_justify = [things_to_justify]

    justification = BNode()
    g.add((justification, RDF.type, AIDA_ANNOTATION.KeyFrameVideoJustification))
    g.add((justification, AIDA_ANNOTATION.source,
           Literal(doc_id, datatype=XSD.string)))
    g.add((justification, AIDA_ANNOTATION.keyFrame,
           Literal(key_frame, datatype=XSD.string)))

    bounding_box_resource = BNode()
    g.add((bounding_box_resource, RDF.type, AIDA_ANNOTATION.BoundingBox))
    g.add((bounding_box_resource, AIDA_ANNOTATION.boundingBoxUpperLeftX,
           Literal(boundingbox.upper_left[0], datatype=XSD.int)))
    g.add((bounding_box_resource, AIDA_ANNOTATION.boundingBoxUpperLeftY,
           Literal(boundingbox.upper_left[1], datatype=XSD.int)))
    g.add((bounding_box_resource, AIDA_ANNOTATION.boundingBoxLowerRightX,
           Literal(boundingbox.lower_right[0], datatype=XSD.int)))
    g.add((bounding_box_resource, AIDA_ANNOTATION.boundingBoxLowerRightY,
           Literal(boundingbox.lower_right[1], datatype=XSD.int)))
    g.add((justification, AIDA_ANNOTATION.boundingBox, bounding_box_resource))
    mark_system(g, justification, system)
    mark_confidence(g, justification, confidence, system)

    for things in things_to_justify:
        g.add((things, AIDA_ANNOTATION.justifiedBy, justification))

    return justification


def mark_shot_video_justification(g, things_to_justify, doc_id, shot_id, system, confidence):
    """
    Marks a justification for something appearing in a video but not in a key frame.

    :return: The justification resource
    """
    if isinstance(things_to_justify, URIRef):
        things_to_justify = [things_to_justify]

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
    """
    Create a "same-as" cluster.

    A same-as cluster is used to represent multiple entities which might be the same, but we
    aren't sure. (If we were sure, they would just be a single node).

    Every cluster requires a [prototype] - an entity or event that we are *certain* is in the
    cluster.

    :return: The cluster created
    """
    cluster = URIRef(cluster_uri)
    g.add((cluster, RDF.type, AIDA_ANNOTATION.SameAsCluster))
    g.add((cluster, AIDA_ANNOTATION.prototype, prototype))
    mark_system(g, cluster, system)
    return cluster


def mark_as_possible_cluster_member(g, possible_cluster_member, cluster, confidence, system):
    """
    Mark an entity or event as a possible member of a cluster.

    :return: The cluster membership assertion
    """
    cluster_member_assertion = BNode()
    g.add((cluster_member_assertion, RDF.type, AIDA_ANNOTATION.ClusterMembership))
    g.add((cluster_member_assertion, AIDA_ANNOTATION.cluster, cluster))
    g.add((cluster_member_assertion, AIDA_ANNOTATION.clusterMember, possible_cluster_member))
    mark_confidence(g, cluster_member_assertion, confidence, system)
    mark_system(g, cluster_member_assertion, system)
    return cluster_member_assertion


def make_hypothesis(g, hypothesis_uri, hypothesis_content, system):
    """
    Create a hypothesis

    You can then indicate that some other object depends on this hypothesis using mark_depends_on_hypothesis

    :return: The hypothesis resource.
    """
    if not hypothesis_content:
        raise RuntimeError("hypothesis_content cannot be empty")

    hypothesis = URIRef(hypothesis_uri)
    g.add((hypothesis, RDF.type, AIDA_ANNOTATION.Hypothesis))
    mark_system(g, hypothesis, system)

    subgraph = BNode()
    g.add((subgraph, RDF.type, AIDA_ANNOTATION.Subgraph))

    for content in hypothesis_content:
        g.add((subgraph, AIDA_ANNOTATION.subgraphContains, content))

    g.add((hypothesis, AIDA_ANNOTATION.hypothesisContent, subgraph))
    return hypothesis


def mark_depends_on_hypothesis(g, depender, hypothesis):
    g.add((depender, AIDA_ANNOTATION.dependsOnHypothesis, hypothesis))


def mark_as_mutually_exclusive(g, alternatives, system, none_of_the_above_prob):
    """
    Mark the given resources as mutually exclusive.

    :param alternatives: a map from the collection of edges which form a sub-graph for
    an alternative to the confidence associated with an alternative.
    :param system: The system object for the system which contains the mutual exclusion
    :param none_of_the_above_prob: if not None, the given confidence will be applied for
    the "none of the above" option.
    :return: The mutual exclusion assertion.
    """
    if len(alternatives) < 2:
        raise RuntimeError("alternatives cannot have less than 2 mutually exclusive things")

    mutual_exclusion_assertion = BNode()
    g.add((mutual_exclusion_assertion, RDF.type, AIDA_ANNOTATION.MutualExclusion))
    mark_system(g, mutual_exclusion_assertion, system)

    for alts in alternatives:
        alternative = BNode()
        g.add((alternative, RDF.type, AIDA_ANNOTATION.MutualExclusionAlternative))

        alternative_graph = BNode()
        g.add((alternative_graph, RDF.type, AIDA_ANNOTATION.Subgraph))
        for alt in alts[0]:
            g.add((alternative_graph, AIDA_ANNOTATION.subgraphContains, alt))

        g.add((alternative, AIDA_ANNOTATION.alternativeGraph, alternative_graph))
        mark_confidence(g, alternative, alts[1], system)

        g.add((mutual_exclusion_assertion, AIDA_ANNOTATION.alternative, alternative))

    if none_of_the_above_prob is not None:
        g.add((mutual_exclusion_assertion, AIDA_ANNOTATION.noneOfTheAbove,
               Literal(none_of_the_above_prob, datatype=XSD.double)))

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


_TYPE_QUERY = prepareQuery("""SELECT ?typeAssertion WHERE {
  ?typeAssertion a rdf:Statement .
  ?typeAssertion rdf:predicate rdf:type .
  ?typeAssertion rdf:subject ?typedObject .
  }
  """)


def get_type_assertions(g, typed_object):
    """
    Get all types associated with an AIF object.

    :return: A list of type assertions describing this object.
    """
    query_result = g.query(_TYPE_QUERY, initBindings={'typedObject': typed_object})
    return [x for (x,) in query_result]


def get_confidences(g, confidenced_object):
    """
    Get all confidence structures associated with an AIF object.

    This does not get confidences attached to sub-graphs containing the object.

    :return: A list of confidence assertions describing this object.
  """
    return list(g.objects(confidenced_object, AIDA_ANNOTATION.confidence))
