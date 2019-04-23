import json
import uuid
from abc import ABCMeta, abstractmethod

from rdflib import URIRef, RDF, Graph, BNode, Literal, XSD

from aida_interchange.aida_rdf_ontologies import AIDA_ANNOTATION
from rdflib.plugins.sparql import prepareQuery

"""
A convenient interface for creating simple AIF graphs.

More complicated graphs will require direct manipulation of the RDF
"""

def make_graph():
    """
    Creates the underlying RDF model

    :returns: The RDF model
    :rtype: rdflib.graph.Graph
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
    to [mark_system].

    :param rdflib.graph.Graph graph: The underlying RDF model
    :param str system_uri: A string URI representation of the system
    :returns: The created system resource
    :rtype: rdflib.term.URIRef
        
    """
    system = URIRef(system_uri)
    graph.add((system, RDF.type, AIDA_ANNOTATION.System))
    return system


def mark_system(g, to_mark_on, system):
    """
    Mark a resource as coming from the specified [system]

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.BNode to_mark_on: The resource to mark as coming from the specified system
    :param rdflib.term.URIRef system: The system with which to mark the specified resource
    """
    g.add((to_mark_on, AIDA_ANNOTATION.system, system))


def mark_name(g, entity, name):
    """
    Mark [entity] as having the specified [name].

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef entity: The resource to mark on
    :param str name: The string name with which to mark the specified resource
    """
    g.add((entity, AIDA_ANNOTATION.hasName,
           Literal(name, datatype=XSD.string)))


def mark_text_value(g, entity, text_value):
    """
    Mark [entity] as having the specified [text_value].

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef entity: The resource to mark as having the specified text value
    :param str text_value: The string text value with which to mark the specified resource
    """
    g.add((entity, AIDA_ANNOTATION.textValue,
           Literal(text_value, datatype=XSD.string)))

def mark_numeric_value_as_string(g, entity, numeric_value):
    """
    Mark [entity] as having the specified [numeric_value] as string.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef entity: The resource to mark as having the specified numeric value
    :param str numeric_value: A string representation of a numeric value with which to 
         mark the specified resource
    """
    g.add((entity, AIDA_ANNOTATION.numericValue,
           Literal(numeric_value, datatype=XSD.string)))


def mark_numeric_value_as_double(g, entity, numeric_value):
    """
    Mark [entity] as having the specified [numeric_value] as double floating point.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef entity: The Resource to mark as having the specified numeric value
    :param str numeric_value: A double representation of a numeric value with which to mark the 
        specified resource
    """
    g.add((entity, AIDA_ANNOTATION.numericValue,
           Literal(numeric_value, datatype=XSD.double)))


def mark_numeric_value_as_long(g, entity, numeric_value):
    """
    Mark [entity] as having the specified [numeric_value] as long integer.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef entity: The resource to mark as having the specified numeric value
    :param str numeric_value: A long representation of a numeric value with which to mark the 
        specified resource
    """
    g.add((entity, AIDA_ANNOTATION.numericValue,
           Literal(numeric_value, datatype=XSD.long)))


def make_entity(g, entity_uri, system):
    """
    Create an entity.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef entity_uri: A unique string representing the uri. This can be any unique string.
    :param rdflib.term.URIRef system: The system object for the system which created this entity.
    :returns: The created entity resource
    :rtype: rdflib.term.URIRef
    """
    return _make_aif_resource(g, entity_uri, AIDA_ANNOTATION.Entity, system)


def mark_type(g, type_assertion_uri, entity_or_event,
              _type, system, confidence):
    """
    Mark an entity, event, or relation as having a specified type.
    
    This is marked with a separate assertion so that uncertainty about type can be expressed.
    In such a case, bundle together the type assertion resources returned by this method with
    [mark_as_mutually_exclusive].

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef type_assertion_uri: The string URI of a type assertion 
        resource with which to mark the entity or event
    :param rdflib.term.URIRef entity_or_event: The entity, event, or relation to mark 
        as having the specified type
    :param rdflib.term.URIRef _type: The type of the entity, event, or relation being asserted
    :param rdflib.term.URIRef system: The system object for the system which created this entity
    :param float confidence: If non-null, the confidence with which to mark the specified type
    :returns The created type assertion resource
    :rtype: rdflib.term.URIRef 
    """
    type_assertion = _make_aif_resource(g, type_assertion_uri, RDF.Statement, system)
    g.add((type_assertion, RDF.subject, entity_or_event))
    g.add((type_assertion, RDF.predicate, RDF.type))
    g.add((type_assertion, RDF['object'], _type))
    mark_confidence(g, type_assertion, confidence, system)
    return type_assertion


def mark_justification(g, things_to_justify, justification):
    """
    Mark something as being justified by a particular justification.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param list things_to_justify: The resource to be marked by the specified justification
    :param rdflib.term.BNode justification: The justification to be marked onto the 
        specified resource
    """
    if isinstance(things_to_justify, URIRef):
        things_to_justify = [things_to_justify]

    for thing in things_to_justify:
        g.add((thing, AIDA_ANNOTATION.justifiedBy, justification))


def make_text_justification(g, doc_id, start_offset, end_offset_inclusive,
                            system, confidence, uri_ref=None):
    """
    Create a justification from a particular snippet of text.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param str doc_id: A string containing the document element (child) ID of the source 
        of the justification
    :param int start_offset: An integer offset within the document for the start of 
        the justification
    :param int end_offset_inclusive: An integer offset within the document for the end of 
        the justification
    :param rdflib.term.URIRef system: The system object for the system which made this 
        justification
    :param float confidence: The confidence with which to mark the justification
    :param str uri_ref: A string URI representation of the justification (Default is None)
    """
    if start_offset > end_offset_inclusive:
        raise RuntimeError('start_offset cannot be larger than end_offset_inclusive')
    if start_offset < 0:
        raise RuntimeError('start_offset must be a non-negative number')
    justification = _make_aif_justification(
        g, doc_id, AIDA_ANNOTATION.TextJustification, system, confidence,
        uri_ref)
    g.add((justification, AIDA_ANNOTATION.startOffset,
           Literal(start_offset, datatype=XSD.int)))
    g.add((justification, AIDA_ANNOTATION.endOffsetInclusive,
           Literal(end_offset_inclusive, datatype=XSD.int)))
    return justification


def mark_text_justification(g, things_to_justify, doc_id, start_offset,
                            end_offset_inclusive, system, confidence,
                            uri_ref=None):
    """
    Mark multiple things as being justified by a particular snippet of text.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param list things_to_justify: A list of resources to be marked by the specified text 
        document
    :param str doc_id: A string containing the document element (child) ID of the source of 
        the justification
    :param int start_offset: An integer offset within the document for the start of 
        the justification
    :param int end_offset_inclusive: An integer offset within the document for the end of 
        the justification
    :param rdflib.term.URIRef system: The system object for the system which made this 
        justification
    :param float confidence: The confidence with which to mark the justification
    :param str uri_ref: A string URI representation of the justification (Default is None)
    """
    justification = make_text_justification(
        g, doc_id, start_offset, end_offset_inclusive, system, confidence,
        uri_ref)
    mark_justification(g, things_to_justify, justification)

    return justification


def mark_confidence(g, to_mark_on, confidence, system):
    """
    Mark a confidence value on a resource.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param to_mark_on: The resource to mark with the specified confidence
    :param float confidence: The confidence with which to mark the resource
    :param rdflib.term.URIRef system: The system object for the system which marked this 
        confidence
    """
    confidence_blank_node = _make_aif_resource(g, None, AIDA_ANNOTATION.Confidence, system)
    g.add((confidence_blank_node, AIDA_ANNOTATION.confidenceValue,
           Literal(confidence, datatype=XSD.double)))
    g.add((to_mark_on, AIDA_ANNOTATION.confidence, confidence_blank_node))


def make_relation(g, relation_uri, system):
    """
    Create a relation.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param str relation_uri: A unique string URI for the relation
    :param rdflib.term.URIRef system: The system object for the system which created the 
        specified relation
    :returns: The relaton object
    :rtype: rdflib.term.URIRef  
    """
    return _make_aif_resource(g, relation_uri, AIDA_ANNOTATION.Relation, system)


def make_relation_in_event_form(g, relation_uri, relation_type, subject_role, subject_resource, object_role,
                                object_resource, type_assertion_uri, system, confidence):
    """
    Make a relation of type [relation_type] between [subject_resource] and [object_resource] 
    in a form similar to that of an event: subjects and objects are explicitly linked to 
    relation via [subject_role] and [object_role], respectively.

    If [confidence] is non-null the relation is marked with the given [confidence]

    :param rdflib.graph.Graph g: The underlying RDF model
    :param str relation_uri: A unique string URI for the specified relation
    :param rdflib.term.URIRef relation_type: The type of relation to make
    :param rdflib.term.URIRef subject_role: The role to link the specified subject to 
        the specified relation
    :param rdflib.term.URIRef subject_resource: The subject to which to link the specified 
        relation via the specified role
    :param rdflib.term.URIRef object_role: The role to link the specified object to 
        the specified relation
    :param rdflib.term.URIRef object_resource: The object to which to link the specified 
        relation via the specified role
    :param str type_assertion_uri: The string URI of a type assertion resource with which 
        to mark the relation
    :param rdflib.term.URIRef system: The system object for the system which created 
        the specified relation
    :param float confidence: If non-null, the confidence with which to mark the specified 
        relation
    :returns: The created relation resource
    :rtype: rdflib.term.URIRef
    """
    relation = make_relation(g, relation_uri, system)
    mark_type(g, type_assertion_uri, relation, relation_type, system, confidence)
    mark_as_argument(g, relation, subject_role, subject_resource, system, confidence)
    mark_as_argument(g, relation, object_role, object_resource, system, confidence)
    return relation


def mark_as_argument(g, event_or_relation, argument_type, argument_filler, system, confidence, uri=None):
    """
    Mark an entity as filling an argument role for an event or relation. The argument assertion
    will be a blank node.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef event_or_relation: The event or relation for which to mark 
        the specified argument role
    :param rdflib.term.URIRef argument_type: The type (predicate) of the argument
    :param rdflib.term.URIRef argument_filler: The filler (object) of the argument
    :param rdflib.term.URIRef system: The system object for the system which created this 
        argument
    :param float confidence: If non-null, the confidence with which to mark the specified 
        argument
    :param str uri: A unique string URI for the argument (Default is None)
    :returns: The created event or relation argument assertion
    :rtype: rdflib.term.BNode

    """
    arg_assertion = _make_aif_resource(g, uri, RDF.Statement, system)
    g.add((arg_assertion, RDF.subject, event_or_relation))
    g.add((arg_assertion, RDF.predicate, argument_type))
    g.add((arg_assertion, RDF['object'], argument_filler))
    if confidence is not None:
        mark_confidence(g, arg_assertion, confidence, system)
    return arg_assertion


def make_event(g, event_uri, system):
    """
    Create an event

    :param rdflib.graph.Graph g: The underlying RDF model
    :param str event_uri: A unique string URI for the event
    :param rdflib.term.URIRef system: The system object for the system which created 
        this event
    :returns: The created event resource
    :rtype: rdflib.term.URIRef
    """
    return _make_aif_resource(g, event_uri, AIDA_ANNOTATION.Event, system)


def mark_boundingbox(g, to_mark_on, boundingbox):
    """
    Mark the specified resource with the specified bounding box.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.BNode to_mark_on: The resource to mark with the specified bounding
        box
    :param rdflib.term.URIRef system: The system object for the system which marked this
         bounding box
    """
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

    g.add((to_mark_on, AIDA_ANNOTATION.boundingBox, bounding_box_resource))

    return bounding_box_resource


def make_image_justification(g, doc_id, boundingbox, system, confidence,
                             uri_ref=None):
    """
    Marks a justification for something appearing in an image.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param str doc_id: A string containing the document element (child) ID of 
        the source of the justification
    :param aida_interchange.Bounding_Box.Bounding_Box boundingbox: A rectangular box 
        within the image that bounds the justification
    :param rdflib.term.URIRef system: The system object for the system which made 
        this justification
    :param float confidence: The confidence with which to mark the justification
    :param str uri_ref: A string URI representation of the image justification 
        (Default is None)
    :returns: The created image justification resource
    :rtype: rdflib.term.BNode
    """
    justification = _make_aif_justification(
        g, doc_id, AIDA_ANNOTATION.ImageJustification, system, confidence,
        uri_ref)
    mark_boundingbox(g, justification, boundingbox)
    return justification


def mark_image_justification(g, things_to_justify, doc_id, boundingbox, system,
                             confidence, uri_ref=None):
    """
    Mark multiple things as being justified by a particular image.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param list things_to_justify: A collection of resources to be marked by the 
        specified image document
    :param str doc_id: A string containing the document element (child) ID of 
        the source of the justification
    :param aida_interchange.Bounding_Box.Bounding_Box boundingbox: A rectangular box 
        within the image that bounds the justification
    :param rdflib.term.URIRef system: The system object for the system which marked
        this justification
    :param float confidence: The confidence with which to mark the justification
    :param str uri_ref: A string URI representation of the image justification 
        (Default is None)
    :returns: The created image justification resource
    :rtype: rdflib.term.BNode
    """
    justification = make_image_justification(g, doc_id, boundingbox, system,
                                             confidence, uri_ref)
    mark_justification(g, things_to_justify, justification)

    return justification


def make_audio_justification(g, doc_id, start_timestamp, end_timestamp, system,
                             confidence, uri_ref=None):
    """
    Make an audio justification.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param str doc_id: A string containing the document element (child) ID of 
        the source of the justification
    :param float start_timestamp: A timestamp within the audio document where the 
        justification starts
    :param float end_timestamp: A timestamp within the audio document where the 
        justification ends
    :param rdflib.term.URIRef system: The system object for the system which made this 
        justification
    :param float confidence: The confidence with which to mark the justification
    :param str uri_ref: (Default is None)
    :returns: The created audio justification resource
    :rtype: rdflib.term.BNode
    """
    if start_timestamp > end_timestamp:
        raise RuntimeError("start_timestamp cannot be larger than end_timestamp")
    justification = _make_aif_justification(
        g, doc_id, AIDA_ANNOTATION.AudioJustification, system, confidence,
        uri_ref)
    g.add((justification, AIDA_ANNOTATION.startTimestamp,
           Literal(start_timestamp, datatype=XSD.double)))
    g.add((justification, AIDA_ANNOTATION.endTimestamp,
           Literal(end_timestamp, datatype=XSD.double)))

    return justification


def mark_audio_justification(g, things_to_justify, doc_id, start_timestamp,
                             end_timestamp, system, confidence, uri_ref=None):
    """
    Mark multiple things as being justified by appearing in an audio document.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param list things_to_justify: A collection of resources to be marked by the 
        specified audio document
    :param str doc_id: A string containing the document element (child) ID of 
        the source of the justification
    :param float start_timestamp: A timestamp within the audio document where the 
        justification starts
    :param float end_timestamp: A timestamp within the audio document where the 
        justification ends
    :param rdflib.term.URIRef system: The system object for the system which marked
        this justification
    :param float confidence: The confidence with which to mark the justification
    :param str uri_ref: A string URI representation of the audio justification 
        (Default is None)
    :returns: The created audio justification resource
    :rtype: rdflib.term.BNode
    """
    justification = make_audio_justification(
        g, doc_id, start_timestamp, end_timestamp, system, confidence, uri_ref)
    mark_justification(g, things_to_justify, justification)

    return justification


def make_keyframe_video_justification(g, doc_id, key_frame, boundingbox, system, confidence, uri_ref=None):
    """
    Create a justification from something appearing in a key frame of a video.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param str doc_id: A string containing the document element (child) ID of 
        the source of the justification
    :param str key_frame: The string Id of the key frame of the specified video document
    :param aida_interchange.Bounding_Box.Bounding_Box boundingbox: A rectangular box within 
        the key frame that bounds the justification
    :param rdflib.term.URIRef system: The system object for the system which marked
        this justification
    :param float confidence: The confidence with which to mark the justification
    :param str uri_ref: A string URI representation of the video justification (Default is None)
    :returns: The created video justification resource
    :rtype: rdflib.term.BNode
    """
    print("keyframe", type(key_frame))
    print("boundingbox", type(boundingbox))
    justification = _make_aif_justification(
        g, doc_id, AIDA_ANNOTATION.KeyFrameVideoJustification, system,
        confidence, uri_ref)
    g.add((justification, AIDA_ANNOTATION.keyFrame,
           Literal(key_frame, datatype=XSD.string)))
    mark_boundingbox(g, justification, boundingbox)

    return justification


def mark_keyframe_video_justification(g, things_to_justify, doc_id, key_frame, boundingbox, system, confidence, uri_ref=None):
    """
    Mark multiple things as being justified by appearing in a key frame of a video.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param list things_to_justify: A collection of resources to be marked by the specified 
        video document
    :param str doc_id: A string containing the document element (child) ID of 
        the source of the justification
    :param str key_frame: The string Id of the key frame of the specified video document
    :param aida_interchange.Bounding_Box.Bounding_Box boundingbox: A rectangular box within 
        the key frame that bounds the justification
    :param rdflib.term.URIRef system: The system object for the system which marked
        this justification
    :param float confidence: The confidence with which to mark the justification
    :param str uri_ref: A string URI representation of the video justification (Default is None)
    :returns: The created video justification resource
    :rtype: rdflib.term.BNode
    """
    justification = make_keyframe_video_justification(g, doc_id, key_frame, boundingbox, system, confidence, uri_ref)
    mark_justification(g, things_to_justify, justification)

    return justification


def make_shot_video_justification(g, doc_id, shot_id, system, confidence,
                                  uri_ref=None):
    """
    Create a justification from something appearing in a video but not in a key frame.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param str doc_id: A string containing the document element (child) ID of the 
        source of the justification
    :param rdflib.term.URIRef system: TThe system object for the system which made 
        this justification
    :param float confidence: The confidence with which to mark the justification
    :returns: The created video justification resource
    :rtype: rdflib.term.BNode
    """
    justification = _make_aif_justification(
        g, doc_id, AIDA_ANNOTATION.ShotVideoJustification, system, confidence,
        uri_ref)
    g.add((justification, AIDA_ANNOTATION.shot,
           Literal(shot_id, datatype=XSD.string)))

    return justification


def mark_shot_video_justification(g, things_to_justify, doc_id, shot_id, system,
                                  confidence, uri_ref=None):
    """
    Mark multiple things as being justified by appearing in a video but not in a key frame

    :param rdflib.graph.Graph g: The underlying RDF model
    :param list things_to_justify: A collection of resources to be marked by the specified video document
    :param str doc_id: A string containing the document element (child) ID of the 
        source of the justification
    :param rdflib.term.URIRef system: TThe system object for the system which made 
        this justification
    :param float confidence: The confidence with which to mark the justification
    :returns: The created video justification resource
    :rtype: rdflib.term.BNode
    :return: The justification resource
    """
    justification = make_shot_video_justification(g, doc_id, shot_id, system,
                                                  confidence, uri_ref)
    mark_justification(g, things_to_justify, justification)

    return justification


def mark_compound_justification(g, things_to_justify, justifications, system, confidence):
    """
    Combine justifications into single justifiedBy triple with new confidence.

    :param g: The underlying RDF model for the operation
    :param things_to_justify: A list of resources to be marked by the specified justifications
    :param justifications: A list of resources that justify the resources to be marked
    :param system: The system object for the system which made these justifications
    :param confidence: The confidence with which to mark each justification

    :return: The created compound justification resource
    """
    compound_justification = _make_aif_resource(g, None, AIDA_ANNOTATION.CompoundJustification, system)
    mark_confidence(g, compound_justification, confidence, system)
    for justification in justifications:
        g.add((compound_justification, AIDA_ANNOTATION.containedJustification, justification))
    mark_justification(g, things_to_justify, compound_justification)
    return compound_justification

def add_source_document_to_justification(g, justification, source_document) :
    """
    Add a sourceDocument to a pre-existing justification

    :param g: The underlying RDF model for the operation
    :param justification: A pre-existing justification resource
    :param source_document: A string containing the source document (parent) ID

    :return: The modified justification
    """
    g.add((justification, AIDA_ANNOTATION.sourceDocument,
            Literal(source_document, datatype=XSD.string)))
    return justification


def make_cluster_with_prototype(g, cluster_uri, prototype, system, handle=None):
    """
    Create a "same-as" cluster.

    A same-as cluster is used to represent multiple entities which might be the same, but we
    aren't sure. (If we were sure, they would just be a single node).

    Every cluster requires a [prototype] - an entity or event that we are *certain* is in the
    cluster.

    :return: The cluster created
    """
    cluster = _make_aif_resource(g, cluster_uri, AIDA_ANNOTATION.SameAsCluster, system)
    g.add((cluster, AIDA_ANNOTATION.prototype, prototype))
    if handle is not None:
        g.add((cluster, AIDA_ANNOTATION.handle, Literal(handle, datatype=XSD.string)))
    return cluster

def mark_as_possible_cluster_member(g, possible_cluster_member, cluster, confidence, system):
    """
    Mark an entity or event as a possible member of a cluster.

    :return: The cluster membership assertion
    """
    cluster_member_assertion = _make_aif_resource(g, None, AIDA_ANNOTATION.ClusterMembership, system)
    g.add((cluster_member_assertion, AIDA_ANNOTATION.cluster, cluster))
    g.add((cluster_member_assertion, AIDA_ANNOTATION.clusterMember, possible_cluster_member))
    mark_confidence(g, cluster_member_assertion, confidence, system)
    return cluster_member_assertion


def make_hypothesis(g, hypothesis_uri, hypothesis_content, system):
    """
    Create a hypothesis

    You can then indicate that some other object depends on this hypothesis using mark_depends_on_hypothesis

    :return: The hypothesis resource.
    """
    if not hypothesis_content:
        raise RuntimeError("hypothesis_content cannot be empty")

    hypothesis = _make_aif_resource(g, hypothesis_uri, AIDA_ANNOTATION.Hypothesis, system)

    subgraph = BNode()
    g.add((subgraph, RDF.type, AIDA_ANNOTATION.Subgraph))

    for content in hypothesis_content:
        g.add((subgraph, AIDA_ANNOTATION.subgraphContains, content))

    g.add((hypothesis, AIDA_ANNOTATION.hypothesisContent, subgraph))
    return hypothesis


def mark_importance(g, resource, importance):
    """
    Mark resource as having an importance value
    """
    g.add((resource, AIDA_ANNOTATION.importance, Literal(importance, datatype=XSD.double)))


def mark_informative_justification(g, resource, informative_justification):
    """
    Mark resource as having an informativeJustification value

    :param g: The underlying RDF model for the operation
    :param resource: the resource to mark with the specified imporatance
    :param informative_justification: the justification which will be considered informative
    """
    g.add((resource, AIDA_ANNOTATION.informativeJustification , informative_justification))


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

    mutual_exclusion_assertion = _make_aif_resource(g, None, AIDA_ANNOTATION.MutualExclusion, system)

    for (edges_for_alternative, confidence) in alternatives.items():
        alternative = BNode()
        g.add((alternative, RDF.type, AIDA_ANNOTATION.MutualExclusionAlternative))

        alternative_graph = BNode()
        g.add((alternative_graph, RDF.type, AIDA_ANNOTATION.Subgraph))
        for alt in edges_for_alternative:
            g.add((alternative_graph, AIDA_ANNOTATION.subgraphContains, alt))

        g.add((alternative, AIDA_ANNOTATION.alternativeGraph, alternative_graph))
        mark_confidence(g, alternative, confidence, system)

        g.add((mutual_exclusion_assertion, AIDA_ANNOTATION.alternative, alternative))

    if none_of_the_above_prob is not None:
        g.add((mutual_exclusion_assertion, AIDA_ANNOTATION.noneOfTheAbove,
               Literal(none_of_the_above_prob, datatype=XSD.double)))

    return mutual_exclusion_assertion


def mark_private_data(g, resource, json_content, system):
    private_data = _make_aif_resource(g, None, AIDA_ANNOTATION.PrivateData, system)
    g.add((private_data, AIDA_ANNOTATION.jsonContent,
           Literal(json_content, datatype=XSD.string)))

    g.add((resource, AIDA_ANNOTATION.privateData, private_data))

    return private_data

def mark_private_data_with_vector(g, resource, system, vector):
    """

    :param g:
    :param resource:
    :param system: The system object
    :param vector: vector data and vector type in dictionary
    :return:
    """
    if vector is None:
        raise RuntimeError("vector cannot be null")

    vector = json.dumps(vector)
    private_data = mark_private_data(g, resource, str(vector), system)

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


def _make_aif_resource(g, uri, class_type, system):
    if uri is None:
        resource = BNode()
    else:
        resource = URIRef(uri)
    g.add((resource, RDF.type, class_type))
    mark_system(g, resource, system)
    return resource


def _make_aif_justification(g, doc_id, class_type, system, confidence,
                            uri_ref=None):
    justification = _make_aif_resource(g, uri_ref, class_type, system)
    g.add((justification, AIDA_ANNOTATION.source,
           Literal(doc_id, datatype=XSD.string)))
    mark_confidence(g, justification, confidence, system)
    return justification

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
