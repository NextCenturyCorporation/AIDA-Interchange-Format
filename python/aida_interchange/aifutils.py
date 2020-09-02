import json

from rdflib import RDF, XSD, BNode, Graph, Literal, URIRef
from rdflib.plugins.sparql import prepareQuery

from aida_interchange.rdf_ontologies import interchange_ontology


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
    g.bind('aida', interchange_ontology.NAMESPACE)
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
    graph.add((system, RDF.type, interchange_ontology.System))
    return system


def mark_system(g, to_mark_on, system):
    """
    Mark a resource as coming from the specified [system]

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.BNode to_mark_on: The resource to mark as coming from the specified system
    :param rdflib.term.URIRef system: The system with which to mark the specified resource
    """
    g.add((to_mark_on, interchange_ontology.system, system))


def mark_name(g, entity, name):
    """
    Mark [entity] as having the specified [name].

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef entity: The resource to mark on
    :param str name: The string name with which to mark the specified resource
    """
    g.add((entity, interchange_ontology.hasName,
           Literal(name, datatype=XSD.string)))


def mark_text_value(g, entity, text_value):
    """
    Mark [entity] as having the specified [text_value].

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef entity: The resource to mark as having the specified text value
    :param str text_value: The string text value with which to mark the specified resource
    """
    g.add((entity, interchange_ontology.textValue,
           Literal(text_value, datatype=XSD.string)))


def mark_numeric_value_as_string(g, entity, numeric_value):
    """
    Mark [entity] as having the specified [numeric_value] as string.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef entity: The resource to mark as having the specified numeric value
    :param str numeric_value: A string representation of a numeric value with which to
         mark the specified resource
    """
    g.add((entity, interchange_ontology.numericValue,
           Literal(numeric_value, datatype=XSD.string)))


def mark_numeric_value_as_double(g, entity, numeric_value):
    """
    Mark [entity] as having the specified [numeric_value] as double floating point.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef entity: The Resource to mark as having the specified numeric value
    :param str numeric_value: A double representation of a numeric value with which to mark the
        specified resource
    """
    g.add((entity, interchange_ontology.numericValue,
           Literal(numeric_value, datatype=XSD.double)))


def mark_numeric_value_as_long(g, entity, numeric_value):
    """
    Mark [entity] as having the specified [numeric_value] as long integer.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef entity: The resource to mark as having the specified numeric value
    :param str numeric_value: A long representation of a numeric value with which to mark the
        specified resource
    """
    g.add((entity, interchange_ontology.numericValue,
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
    return _make_aif_resource(g, entity_uri, interchange_ontology.Entity, system)


def mark_type(g, type_assertion_uri, entity_or_event, _type, system, confidence):
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
    :param float confidence: If not None, the confidence with which to mark the specified type
    :returns: The created type assertion resource
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
        g.add((thing, interchange_ontology.justifiedBy, justification))


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
        g, doc_id, interchange_ontology.TextJustification, system, confidence,
        uri_ref)
    g.add((justification, interchange_ontology.startOffset,
           Literal(start_offset, datatype=XSD.int)))
    g.add((justification, interchange_ontology.endOffsetInclusive,
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
    confidence_blank_node = _make_aif_resource(g, None, interchange_ontology.Confidence, system)
    g.add((confidence_blank_node, interchange_ontology.confidenceValue,
           Literal(confidence, datatype=XSD.double)))
    g.add((to_mark_on, interchange_ontology.confidence, confidence_blank_node))


def make_relation(g, relation_uri, system):
    """
    Create a relation.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param str relation_uri: A unique string URI for the relation
    :param rdflib.term.URIRef system: The system object for the system which created the
        specified relation
    :returns: The relation object
    :rtype: rdflib.term.URIRef
    """
    return _make_aif_resource(g, relation_uri, interchange_ontology.Relation, system)


def make_relation_in_event_form(g, relation_uri, relation_type, subject_role, subject_resource, object_role,
                                object_resource, type_assertion_uri, system, confidence):
    """
    Make a relation of type [relation_type] between [subject_resource] and [object_resource]
    in a form similar to that of an event: subjects and objects are explicitly linked to
    relation via [subject_role] and [object_role], respectively.

    If [confidence] is not None the relation is marked with the given [confidence]

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
    :param float confidence: If not None, the confidence with which to mark the specified
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
    :param float confidence: If not None, the confidence with which to mark the specified
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
    return _make_aif_resource(g, event_uri, interchange_ontology.Event, system)


def mark_boundingbox(g, to_mark_on, boundingbox):
    """
    Mark the specified resource with the specified bounding box.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.BNode to_mark_on: The resource to mark with the specified bounding
        box
    :param Bounding_Box boundingbox: A rectangular box
        within the image that bounds the justification
    """
    bounding_box_resource = BNode()
    g.add((bounding_box_resource, RDF.type, interchange_ontology.BoundingBox))
    g.add((bounding_box_resource, interchange_ontology.boundingBoxUpperLeftX,
           Literal(boundingbox.upper_left[0], datatype=XSD.int)))
    g.add((bounding_box_resource, interchange_ontology.boundingBoxUpperLeftY,
           Literal(boundingbox.upper_left[1], datatype=XSD.int)))
    g.add((bounding_box_resource, interchange_ontology.boundingBoxLowerRightX,
           Literal(boundingbox.lower_right[0], datatype=XSD.int)))
    g.add((bounding_box_resource, interchange_ontology.boundingBoxLowerRightY,
           Literal(boundingbox.lower_right[1], datatype=XSD.int)))

    g.add((to_mark_on, interchange_ontology.boundingBox, bounding_box_resource))

    return bounding_box_resource


def make_image_justification(g, doc_id, boundingbox, system, confidence,
                             uri_ref=None):
    """
    Marks a justification for something appearing in an image.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param str doc_id: A string containing the document element (child) ID of
        the source of the justification
    :param Bounding_Box boundingbox: A rectangular box
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
        g, doc_id, interchange_ontology.ImageJustification, system, confidence,
        uri_ref)
    mark_boundingbox(g, justification, boundingbox)
    return justification


def mark_image_justification(g, things_to_justify, doc_id, boundingbox, system,
                             confidence, uri_ref=None):
    """
    Mark multiple things as being justified by a particular image.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param list things_to_justify: A list of resources to be marked by the
        specified image document
    :param str doc_id: A string containing the document element (child) ID of
        the source of the justification
    :param Bounding_Box boundingbox: A rectangular box
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
        g, doc_id, interchange_ontology.AudioJustification, system, confidence,
        uri_ref)
    g.add((justification, interchange_ontology.startTimestamp,
           Literal(start_timestamp, datatype=XSD.double)))
    g.add((justification, interchange_ontology.endTimestamp,
           Literal(end_timestamp, datatype=XSD.double)))

    return justification


def mark_audio_justification(g, things_to_justify, doc_id, start_timestamp,
                             end_timestamp, system, confidence, uri_ref=None):
    """
    Mark multiple things as being justified by appearing in an audio document.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param list things_to_justify: A list of resources to be marked by the
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


def make_video_justification(g, doc_id, start_timestamp, end_timestamp, channel,
                             system, confidence, uri_ref=None):
    """
    Make a video justification.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param str doc_id: A string containing the document element (child) ID of
        the source of the justification
    :param float start_timestamp: A timestamp within the video document where the
        justification starts
    :param float end_timestamp: A timestamp within the video document where the
        justification ends
    :param rdflib.term.URIRef channel: The channel of the video that the mention
        appears in. See: InterchangeOntology.VideoJustificationChannel
    :param rdflib.term.URIRef system: The system object for the system which made this
        justification
    :param float confidence: The confidence with which to mark the justification
    :param str uri_ref: (Default is None)
    :returns: The created video justification resource
    :rtype: rdflib.term.BNode
    """
    if start_timestamp > end_timestamp:
        raise RuntimeError("start_timestamp cannot be larger than end_timestamp")
    justification = _make_aif_justification(
        g, doc_id, interchange_ontology.VideoJustification, system, confidence,
        uri_ref)
    g.add((justification, interchange_ontology.startTimestamp,
           Literal(start_timestamp, datatype=XSD.double)))
    g.add((justification, interchange_ontology.endTimestamp,
           Literal(end_timestamp, datatype=XSD.double)))
    g.add((justification, interchange_ontology.channel, channel))

    return justification

def make_keyframe_video_justification(g, doc_id, key_frame, boundingbox, system, confidence, uri_ref=None):
    """
    Create a justification from something appearing in a key frame of a video.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param str doc_id: A string containing the document element (child) ID of
        the source of the justification
    :param str key_frame: The string Id of the key frame of the specified video document
    :param Bounding_Box boundingbox: A rectangular box within
        the key frame that bounds the justification
    :param rdflib.term.URIRef system: The system object for the system which marked
        this justification
    :param float confidence: The confidence with which to mark the justification
    :param str uri_ref: A string URI representation of the video justification (Default is None)
    :returns: The created video justification resource
    :rtype: rdflib.term.BNode
    """
    justification = _make_aif_justification(
        g, doc_id, interchange_ontology.KeyFrameVideoJustification, system,
        confidence, uri_ref)
    g.add((justification, interchange_ontology.keyFrame,
           Literal(key_frame, datatype=XSD.string)))
    mark_boundingbox(g, justification, boundingbox)

    return justification


def mark_keyframe_video_justification(g, things_to_justify, doc_id, key_frame, boundingbox, system, confidence, uri_ref=None):
    """
    Mark multiple things as being justified by appearing in a key frame of a video.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param list things_to_justify: A list of resources to be marked by the specified
        video document
    :param str doc_id: A string containing the document element (child) ID of
        the source of the justification
    :param str key_frame: The string Id of the key frame of the specified video document
    :param Bounding_Box boundingbox: A rectangular box within
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
    :param str shot_id: The string Id of the shot of the specified video document
    :param rdflib.term.URIRef system: The system object for the system which made
        this justification
    :param float confidence: The confidence with which to mark the justification
    :param str uri_ref: A string URI representation of the video justification (Default is None)
    :returns: The created video justification resource
    :rtype: rdflib.term.BNode
    """
    justification = _make_aif_justification(
        g, doc_id, interchange_ontology.ShotVideoJustification, system, confidence,
        uri_ref)
    g.add((justification, interchange_ontology.shot,
           Literal(shot_id, datatype=XSD.string)))

    return justification


def mark_shot_video_justification(g, things_to_justify, doc_id, shot_id, system,
                                  confidence, uri_ref=None):
    """
    Mark multiple things as being justified by appearing in a video but not in a key frame

    :param rdflib.graph.Graph g: The underlying RDF model
    :param list things_to_justify: A list of resources to be marked by the specified
        video document
    :param str shot_id: The string Id of the shot of the specified video document
    :param str doc_id: A string containing the document element (child) ID of the
        source of the justification
    :param rdflib.term.URIRef system: The system object for the system which made
        this justification
    :param float confidence: The confidence with which to mark the justification
    :param str uri_ref: A string URI representation of the video justification (Default is None)
    :returns: The created video justification resource
    :rtype: rdflib.term.BNode
    """
    justification = make_shot_video_justification(g, doc_id, shot_id, system,
                                                  confidence, uri_ref)
    mark_justification(g, things_to_justify, justification)

    return justification


def mark_compound_justification(g, things_to_justify, justifications, system, confidence):
    """
    Combine justifications into single justifiedBy triple with new confidence.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param list things_to_justify: A list of resources to be marked by the specified justifications
    :param list justifications: A list of resources that justify the resources to be marked
    :param rdflib.term.URIRef system: The system object for the system which made these justifications
    :param float confidence: The confidence with which to mark each justification
    :returns: The created compound justification resource
    :rtype: rdflib.term.BNode
    """
    compound_justification = _make_aif_resource(g, None, interchange_ontology.CompoundJustification, system)
    mark_confidence(g, compound_justification, confidence, system)
    for justification in justifications:
        g.add((compound_justification, interchange_ontology.containedJustification, justification))
    mark_justification(g, things_to_justify, compound_justification)
    return compound_justification


def add_source_document_to_justification(g, justification, source_document):
    """
    Add a sourceDocument to a pre-existing justification

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.BNode justification: A pre-existing justification resource
    :param str source_document: A string containing the source document (parent) ID
    :returns: The modified justification
    :rtype: rdflib.term.BNode
    """
    g.add((justification, interchange_ontology.sourceDocument,
            Literal(source_document, datatype=XSD.string)))
    return justification

def mark_handle(g, to_mark, handle) -> URIRef:
    """
    Add a handle to an existing resource

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef to_mark: Reference to mark with a handle
    :param str handle: A string containing the handle
    :returns: The marked reference
    :rtype: rdflib.term.URIRef
    """
    if handle is not None:
        g.add((to_mark, interchange_ontology.handle, Literal(handle, datatype=XSD.string)))
    return to_mark

def make_cluster_with_prototype(g, cluster_uri, prototype, system, handle=None):
    """
    Create a "same-as" cluster.

    A same-as cluster is used to represent multiple entities which might be the same, but we
    aren't sure. (If we were sure, they would just be a single node).

    Every cluster requires a [prototype] - an entity or event that we are *certain* is in the
    cluster.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param str cluster_uri: A unique String URI for the cluster
    :param rdflib.term.URIRef prototype: an entity, event, or relation that we are certain is in the cluster
    :param system: The system object for the system which created the specified cluster
    :param str handle: A string describing the cluster (Default is None)
    :returns: The cluster created
    :rtype: rdflib.term.URIRef
    """
    cluster = _make_aif_resource(g, cluster_uri, interchange_ontology.SameAsCluster, system)
    g.add((cluster, interchange_ontology.prototype, prototype))
    return mark_handle(g, cluster, handle)


def mark_as_possible_cluster_member(g, possible_cluster_member, cluster, confidence, system, uri_ref=None):
    """
    Mark an entity or event as a possible member of a cluster.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef possible_cluster_member: The entity or event to mark as a possible
        member of the specified cluster
    :param rdflib.term.URIRef cluster: The cluster to associate with the possible cluster member
    :param float confidence: The confidence with which to mark the cluster membership
    :param rdflib.term.URIRef system: The system object for the system which marked the specified cluster
    :param str uri_ref: A string URI representation of the cluster member (Default is None)
    :returns: The cluster membership assertion
    :rtype: rdflib.term.BNode
    """
    cluster_member_assertion = _make_aif_resource(g, uri_ref, interchange_ontology.ClusterMembership, system)
    g.add((cluster_member_assertion, interchange_ontology.cluster, cluster))
    g.add((cluster_member_assertion, interchange_ontology.clusterMember, possible_cluster_member))
    mark_confidence(g, cluster_member_assertion, confidence, system)
    return cluster_member_assertion


def make_hypothesis(g, hypothesis_uri, hypothesis_content, system):
    """
    Create a hypothesis

    You can then indicate that some other object depends on this hypothesis using mark_depends_on_hypothesis

    :param rdflib.graph.Graph g: The underlying RDF model
    :param str hypothesis_uri: A unique String URI for the hypothesis
    :param list hypothesis_content: A list of entities, relations, and arguments that contribute
        to the hypothesis
    :param rdflib.term.URIRef system: The system object for the system which made the hypothesis
    :return: The hypothesis resource
    :rtype: rdflib.term.URIRef
    """
    if not hypothesis_content:
        raise RuntimeError("hypothesis_content cannot be empty")

    hypothesis = _make_aif_resource(g, hypothesis_uri, interchange_ontology.Hypothesis, system)

    subgraph = BNode()
    g.add((subgraph, RDF.type, interchange_ontology.Subgraph))

    for content in hypothesis_content:
        g.add((subgraph, interchange_ontology.subgraphContains, content))

    g.add((hypothesis, interchange_ontology.hypothesisContent, subgraph))
    return hypothesis


def mark_importance(g, resource, importance):
    """
    Mark [resource] as having the specified [importance] value.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef resource: The resource to mark with the specified importance
    :param float importance: The importance value with which to mark the specified Resource
    """
    g.add((resource, interchange_ontology.importance, Literal(importance, datatype=XSD.double)))


def mark_informative_justification(g, resource, informative_justification):
    """
    Mark resource as having an informativeJustification value

    :param rdflib.graph.Graph g: The underlying RDF model
    :param resource: the resource to mark with the specified importance
    :param informative_justification: the justification which will be considered informative
    """
    g.add((resource, interchange_ontology.informativeJustification, informative_justification))


def mark_depends_on_hypothesis(g, depender, hypothesis):
    """
    Mark an argument as depending on a hypothesis.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef depender: the argument that depends on the specified hypothesis
    :param rdflib.term.URIRef hypothesis: The hypothesis upon which to depend
    """
    g.add((depender, interchange_ontology.dependsOnHypothesis, hypothesis))


def mark_as_mutually_exclusive(g, alternatives, system, none_of_the_above_prob):
    """
    Mark the given resources as mutually exclusive.

    This is a special case of [mark_as_mutually_exclusive] where the alternatives are
    each single edges, so we simply wrap each edge in a collection and pass to
    mark_as_mutually_exclusive.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param dict alternatives: a dictionary of edges which form a sub-graph for
        an alternative to the confidence associated with an alternative.
    :param rdflib.term.URIRef system: The system object for the system which contains the
        mutual exclusion
    :param float none_of_the_above_prob: if not None, the given confidence will be applied for
        the "none of the above" option.
    :returns: The created mutual exclusion assertion resource
    :rtype: rdflib.term.BNode
    """
    if len(alternatives) < 2:
        raise RuntimeError("alternatives cannot have less than 2 mutually exclusive things")

    mutual_exclusion_assertion = _make_aif_resource(g, None, interchange_ontology.MutualExclusion, system)

    for (edges_for_alternative, confidence) in alternatives.items():
        alternative = BNode()
        g.add((alternative, RDF.type, interchange_ontology.MutualExclusionAlternative))

        alternative_graph = BNode()
        g.add((alternative_graph, RDF.type, interchange_ontology.Subgraph))
        for alt in edges_for_alternative:
            g.add((alternative_graph, interchange_ontology.subgraphContains, alt))

        g.add((alternative, interchange_ontology.alternativeGraph, alternative_graph))
        mark_confidence(g, alternative, confidence, system)

        g.add((mutual_exclusion_assertion, interchange_ontology.alternative, alternative))

    if none_of_the_above_prob is not None:
        g.add((mutual_exclusion_assertion, interchange_ontology.noneOfTheAbove,
               Literal(none_of_the_above_prob, datatype=XSD.double)))

    return mutual_exclusion_assertion


def mark_private_data(g, resource, json_content, system):
    """
    Mark data as private from JSON data. Private data should not contain document-level content features.
    Allowable private data include:

    - fringe type(s) for the KE
    - a vectorized representation of the KE, which cannot grow as the number of mentions/justifications for the KE
      increases, and from which a raw document (or significant portions thereof) cannot be recoverable
    - the number of documents that justify the KE
    - time stamps of justification documents
    - fringe type(s) for each image or shot, to describe features that are not represented explicitly in the
      ontology.  For example: Physical.LocatedNear.Inside(Arg1_Type=Person.Soldier, Arg2_Type=Facility.Hospital)

    The KE is not allowed to contain any strings from document text except for the strings in the HasName,
    NumericValue, and TextValue properties

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef resource: The entity with which to associate private data
    :param str json_content: Valid JSON content (in key/value pairs) that represents the private data
    :param rdflib.term.URIRef system: The system object for the system which marks the private data
    :returns: The created private data resource
    :rtype: rdflib.term.BNode
    """
    private_data = _make_aif_resource(g, None, interchange_ontology.PrivateData, system)
    g.add((private_data, interchange_ontology.jsonContent,
           Literal(json_content, datatype=XSD.string)))

    g.add((resource, interchange_ontology.privateData, private_data))

    return private_data


def mark_private_data_with_vector(g, resource, system, vector):
    """
    Mark data as private from vector data. Private data should not contain document-level content features.
    Allowable private data include:

    - fringe type(s) for the KE
    - a vectorized representation of the KE, which cannot grow as the number of mentions/justifications for the KE
      increases, and from which a raw document (or significant portions thereof) cannot be recoverable
    - the number of documents that justify the KE
    - time stamps of justification documents
    - fringe type(s) for each image or shot, to describe features that are not represented explicitly in the
      ontology.  For example: Physical.LocatedNear.Inside(Arg1_Type=Person.Soldier, Arg2_Type=Facility.Hospital)

    The KE is not allowed to contain any strings from document text except for the strings in the HasName,
    NumericValue, and TextValue properties

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef resource: The entity with which to associate private data
    :param str vector: A string of numeric data that represents the private data
    :param rdflib.term.URIRef system: The system object for the system which marks the private data
    :returns: The created private data resource
    :rtype: rdflib.term.BNode
    :raises RuntimeError: if vector is None
    """
    if vector is None:
        raise RuntimeError("vector cannot be null")

    vector = json.dumps(vector)
    private_data = mark_private_data(g, resource, str(vector), system)
    return private_data


def link_to_external_kb(g, to_link, external_kb_id, system, confidence,
                        uri_ref=None):
    """
    Link an entity to something in an external KB.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef to_link: The entity to which to link
    :param str external_kb_id: A unique String URI of the external KB
    :param rdflib.term.URIRef system: The system object for the system which make the link
    :param float confidence: If not None, the confidence with which to mark the linkage
    :param str uri_ref: A string URI representation of the link (Default is None)
    :returns: The created link assertion resource
    :rtype: rdflib.term.BNode
    """
    if uri_ref is None:
        link_assertion = BNode()
    else:
        link_assertion = URIRef(uri_ref)
    g.add((to_link, interchange_ontology.link, link_assertion))
    g.add((link_assertion, RDF.type, interchange_ontology.LinkAssertion))
    g.add((link_assertion, interchange_ontology.linkTarget,
           Literal(external_kb_id, datatype=XSD.string)))
    mark_system(g, link_assertion, system)
    mark_confidence(g, link_assertion, confidence, system)
    return link_assertion


def _make_aif_resource(g, uri, class_type, system):
    """
    Helper function to create an event, relation, justification, etc. in the system.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param str uri: The string URI of the resource
    :param rdflib.term.URIRef class_type: The class type of the resource
    :param rdflib.term.URIRef system: The system object for the system which marks the resource
    :returns: The created AIF resource
    :rtype: rdflib.term.BNode
    """
    if uri is None:
        resource = BNode()
    else:
        resource = URIRef(uri)
    g.add((resource, RDF.type, class_type))
    if system is not None:
        mark_system(g, resource, system)
    return resource


def _make_aif_justification(g, doc_id, class_type, system, confidence,
                            uri_ref=None):
    """
    Helper function to create a justification (text, image, audio, etc.) in the system.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param str doc_id: A string containing the document element (child) ID of the source
        of the justification
    :param rdflib.term.URIRef class_type: The class type of the resource
    :param rdflib.term.URIRef system: The system object for the system which marks the
        justification
    :param float confidence: If not None, the confidence with which to mark the linkage
    :param str uri_ref: A string URI representation of the justification (Default is None)
    :returns: The created justification
    :rtype: rdflib.term.BNode
    """
    justification = _make_aif_resource(g, uri_ref, class_type, system)
    g.add((justification, interchange_ontology.source,
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
    Retrieve all type assertions from an entity.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef typed_object: The entity from which to retrieve
        type assertions
    :returns: A list of type assertions for the specified entity
    :rtype: list
    """
    query_result = g.query(_TYPE_QUERY, initBindings={'typedObject': typed_object})
    return [x for (x,) in query_result]


def get_confidences(g, confidenced_object):
    """
    Retrieve all confidence assertions from an entity.

    This does not get confidences attached to sub-graphs containing the object.

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef confidenced_object: The entity from which to retrieve
        confidence assertions
    :returns: A list of confidence assertions describing this object.
    :rtype: list
    """
    return list(g.objects(confidenced_object, interchange_ontology.confidence))


def mark_ldc_time(g, to_mark, start, end, system):
    """
    Add LDC start and end time representation to an Event or Relation

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef to_mark: The Event or Relation to add the LDC time data to
    :param LDCTimeComponent start: containing the start time information
    :param LDCTimeComponent end: containing the end time information
    :param rdflib.term.URIRef  system: The system object for the system which marks the time
    :returns: The LDCTimeComponent resource
    :rtype: rdflib.term.BNode
    """
    ldc_time = _make_aif_resource(g, None, interchange_ontology.LDCTime, system)
    if start:
        g.add((ldc_time, interchange_ontology.start, start.make_aif_time_component(g)))
    if end:
        g.add((ldc_time, interchange_ontology.end, end.make_aif_time_component(g)))

    g.add((to_mark, interchange_ontology.ldcTime, ldc_time))

    return ldc_time

def mark_ldc_time_range(g, to_mark, startEarliest, startLatest, endEarliest, endLatest, system):
    """
    Add LDC start and end time representation to an Event or Relation

    :param rdflib.graph.Graph g: The underlying RDF model
    :param rdflib.term.URIRef to_mark: The Event or Relation to add the LDC time data to
    :param LDCTimeComponent startEarliest: containing the earliest start time information
    :param LDCTimeComponent startLatest: containing the latest start time information
    :param LDCTimeComponent endEarliest: containing the earliest end time information
    :param LDCTimeComponent endLatest: containing the latest end time information
    :param rdflib.term.URIRef  system: The system object for the system which marks the time
    :returns: The LDCTimeComponent resource
    :rtype: rdflib.term.BNode
    """
    ldc_time = _make_aif_resource(g, None, interchange_ontology.LDCTime, system)
    if startEarliest:
        g.add((ldc_time, interchange_ontology.start, startEarliest.make_aif_time_component(g)))
    if startLatest:
        g.add((ldc_time, interchange_ontology.start, startLatest.make_aif_time_component(g)))
    if endEarliest:
        g.add((ldc_time, interchange_ontology.end, endEarliest.make_aif_time_component(g)))
    if endLatest:
        g.add((ldc_time, interchange_ontology.end, endLatest.make_aif_time_component(g)))

    g.add((to_mark, interchange_ontology.ldcTime, ldc_time))

    return ldc_time
