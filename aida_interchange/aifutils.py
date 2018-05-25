from rdflib import URIRef, RDF, Graph, BNode, Literal, XSD

from aida_interchange.aida_rdf_ontologies import AIDA_ANNOTATION
from rdflib.plugins.sparql import prepareQuery


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
