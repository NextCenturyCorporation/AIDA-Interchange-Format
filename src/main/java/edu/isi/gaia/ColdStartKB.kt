package edu.isi.gaia

import mu.KLogging
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths


interface Node

class EventNode : Node

class EntityNode : Node

class StringNode : Node

data class Span (val start: Int, val end_inclusive: Int) {
    init {
        require(start >= 0) { "Start must be non-negative but got $start" }
        require(start <= end_inclusive) { "Start must less than or equal to end but" +
                " got $start > $end_inclusive" }
    }
}

data class Provenance(val doc_id: String, val predicate_justifications: Set<Span>,
                 val filler_string: Span? = null, val base_filler: Span? = null,
                 val additional_justifications: Set<Span> = setOf())


interface Assertion {
    val subject: Node
}

data class TypeAssertion(override val subject: Node, val type: String) : Assertion {
    init {
        require(type.isNotEmpty()) {"Type cannot be empty" }
    }
}

data class LinkAssertion(override val subject: Node, val global_id: String) : Assertion {
    init {
        require(global_id.isNotEmpty()) {"Global ID can't be empty"}
    }
}

val MENTION_TYPES = setOf("mention", "pronominal_mention", "nominal_mention",
        "canonical_mention", "normalized_mention")


data class MentionType(val name: String) {
    init {
        require(MENTION_TYPES.contains(name)) { "Expected known mention type but got $name"}
    }
}

val CANONICAL_MENTION = MentionType("canonical_mention")

enum class Realis {
    actual, generic, other
}

interface MentionAssertion : Assertion {
    val mention_type: MentionType
    val string: String
    val justifications: Provenance
}

data class EntityMentionAssertion(override val subject: EntityNode, override val mention_type: MentionType,
                             override val string : String, override val justifications: Provenance)
    : MentionAssertion

data class StringMentionAssertion(override val subject: StringNode,
                                  override val mention_type: MentionType,
                             override val string : String, override val justifications: Provenance)
    : MentionAssertion

data class EventMentionAssertion(override val subject: EventNode, override val mention_type: MentionType,
                            val realis: Realis, override val string : String,
                            override val justifications: Provenance) : MentionAssertion

data class RelationAssertion(override val subject: Node, val relationType: String,
                        val obj: Node, val justifications: Provenance)
    : Assertion

data class EventArgumentAssertion(override val subject: Node, val argument_role: String,
                             val realis: Realis, val argument: Node,
                             val justifications: Provenance) : Assertion {
    init {
        require(argument_role.isNotEmpty()) { "Empty argument role not allowed" }
    }
}

data class ColdStartKB(val assertionsToConfidence: Map<Assertion, Double>,
                  val assertionsWithoutConfidences: Set<Assertion>) {
    val allAssertions: Set<Assertion> by lazy {
        return@lazy assertionsToConfidence.keys.union(assertionsWithoutConfidences)
    }
}

class ColdStartKBLoader {
    companion object: KLogging()

    fun load(source: Path) : ColdStartKB {
        return Loading().load(source)
    }

    private class Loading {
        val _SBJ_NODE_ID = 0
        val _ASSERTION_TYPE = 1
        val _TYPE_STRING = 2
        val _LINK_STRING = 2
        val _OBJ_STRING = 2
        val _OBJ_NODE_ID = 2
        val _JST_STRING = 3
        val _CONF_FLOAT = 4

        val _JUSTIFICATION_PAT = Regex("""^(.+):(\d+)-(\d+)$""")
        val _SPAN_PAT = Regex("""(\d+)-(\d+)""")
        val _ASSERTION_PAT = Regex("""^(.+?)\.?(other|generic|actual)?$""")

        val idToNode: MutableMap<String, Node> = HashMap()

        fun load(source: Path): ColdStartKB {
            val assertionToConfidence = mutableListOf<Pair<Assertion, Double>>()
            val assertionsWithoutConfidence = mutableListOf<Assertion>()

            val progress_interval = 100000

            var line_num = 0
            source.toFile().forEachLine(charset = Charsets.UTF_8) {
                line_num += 1
                val line = it.trim()
                // skip first line which contains a KB label
                if (line_num > 1 && line.isNotEmpty()) {
                    // skip blank lines
                    try {
                        val (assertion, confidence) = parseLine(line) ?: return@forEachLine
                        if (confidence != null) {
                            assertionToConfidence.add(assertion to confidence)
                        } else {
                            assertionsWithoutConfidence.add(assertion)
                        }
                    } catch (e: Exception) {
                        throw IOException("Failure when parsing line $line_num of $source:\n$line",
                                e)
                    }
                }

                if (line_num % progress_interval == 0) {
                    logger.info { "Processed $line_num lines" }
                }
            }
            return ColdStartKB(assertionToConfidence.toMap(),
                    assertionsWithoutConfidence.toSet())
        }

        private fun parseLine(line: String): Pair<Assertion, Double?>? {
            val fields = line.split('\t')
            val assertionTypeField = fields[_ASSERTION_TYPE]
            val match = _ASSERTION_PAT.matchEntire(assertionTypeField)
            val assertionType = match?.groups?.get(1)?.value ?:
                    throw RuntimeException("Unknown assertion type $assertionTypeField")

            return when (assertionType) {
                "type" -> parseTypeAssertion(fields)
                "link" -> parseLinkAssertion(fields)
                else -> if (MENTION_TYPES.contains(assertionType)) {
                    parseMention(fields)
                } else {
                    parsePredicate(fields)
                }
            }
        }

        private fun parseTypeAssertion(fields: List<String>): Pair<Assertion, Double?> {
            // we allow two possible values here because RPI's ColdStart output sometimes has
            // a 1.0 confidence here and some times omits it
            require((fields.size == (_TYPE_STRING + 1))
                    or (fields.size == (_TYPE_STRING + 2)))
            { "Wrong number of fields in type assertion" }

            return Pair(TypeAssertion(toNode(fields[_SBJ_NODE_ID]),
                    fields[_TYPE_STRING]), null)

        }

        private fun parseLinkAssertion(fields: List<String>): Pair<Assertion, Double?> {
            require(fields.size == _LINK_STRING + 1)
            { "Wrong number of fields in link assertion" }

            return Pair(LinkAssertion(toNode(fields[_SBJ_NODE_ID]),
                    fields[_LINK_STRING]), 1.0)

        }

        private fun parseMention(fields: List<String>): Pair<Assertion, Double?>?  {
            require(fields.size in 4..5) { "Unknown assertion format" }

            val match = _ASSERTION_PAT.matchEntire(fields[_ASSERTION_TYPE])
            val (mention_type, realis) = match?.destructured ?:
                    throw RuntimeException("Unknown mention type")

            // strip surrounding "s if present
            val mention_string = fields[_OBJ_STRING].trim('"')

            val subjectNode = toNode(fields[_SBJ_NODE_ID])

            val confidence = if (_CONF_FLOAT < fields.size) fields[_CONF_FLOAT].toDouble() else null

            if (subjectNode is EventNode) {
                if (realis.isEmpty()) throw IOException("Invalid empty realis on event argument")
                return Pair(
                        EventMentionAssertion(subjectNode,
                                MentionType(mention_type),
                                Realis.valueOf(realis),
                                mention_string,
                                toJustificationSpan(fields[_JST_STRING])),
                        confidence)
            } else if (subjectNode is EntityNode) {
                return Pair(
                        EntityMentionAssertion(subjectNode,
                                MentionType(mention_type),
                                mention_string,
                                toJustificationSpan(fields[_JST_STRING])),
                        confidence)
            } else if (subjectNode is StringNode) {
                return Pair(
                        StringMentionAssertion(subjectNode,
                                MentionType(mention_type),
                                mention_string,
                                toJustificationSpan(fields[_JST_STRING])),
                        confidence)
            } else {
                throw IOException("Unknown node type " + fields[_SBJ_NODE_ID])
            }
        }

        private fun parsePredicate(fields: List<String>): Pair<Assertion, Double?>? {
            require(fields.size == 5) {
                "Wrong number of fields in predicate " +
                        "declaration"
            }
            val match = _ASSERTION_PAT.matchEntire(fields[_ASSERTION_TYPE])
            val (relation_type, realis) = match?.destructured
                    ?: throw RuntimeException("Unknown assertion type " + fields[_ASSERTION_TYPE])

            val subjectNode = toNode(fields[_SBJ_NODE_ID])
            val objectNode = toNode(fields[_OBJ_NODE_ID])

            if (objectNode is EventNode) {
                // event arguments are encoded with predicates running in both directions,
                // but we only keep one of the,
                return null
            }

            if (subjectNode is EventNode) {
                if (realis.isEmpty()) throw IOException("Invalid empty realis on event argument")
                return Pair(EventArgumentAssertion(
                        subjectNode,
                        relation_type,
                        Realis.valueOf(realis),
                        objectNode,
                        toJustificationSpan(fields[_JST_STRING])),
                        fields[_CONF_FLOAT].toDouble())
            } else {
                return Pair(
                        RelationAssertion(
                                subjectNode,
                                relation_type,
                                objectNode,
                                toJustificationSpan(fields[_JST_STRING])),
                        fields[_CONF_FLOAT].toDouble())
            }
        }

        private fun toJustificationSpan(provenanceString: String) : Provenance {
            fun parseSpanSet(justification: String) : Pair<String, List<Span>> {
                val fields = justification.split(':')
                val doc_id = fields[0]

                return Pair(doc_id,
                        fields.subList(1, fields.size).map
                        {
                            val match = _SPAN_PAT.matchEntire(it)
                            val (start, end_inclusive) = match?.destructured
                                    ?: throw RuntimeException("Invalid justification " +
                                    "span $justification")
                            Span(start.toInt(), end_inclusive.toInt())
                        })
            }

            fun parseSpan(justification: String) : Pair<String, Span> {
                val match = _JUSTIFICATION_PAT.matchEntire(justification)
                val (docId, start, end_inclusive) = match?.destructured ?:
                    throw RuntimeException("Invalid justification span $justification")
                return Pair(docId, Span(start.toInt(), end_inclusive.toInt()))
            }

            val justifications = provenanceString.split(';')

            when(justifications.size) {
                1 -> { // PREDICATE_JUSTIFICATION, may have 1-3 spans
                    val (docID, predSpan) = parseSpanSet(justifications[0])
                    return Provenance(docID, predSpan.toSet())
                }
                2 -> { //  FILLER_STRING;PREDICATE_JUSTIFICATION
                    val (fill_doc_id, fill_span) = parseSpan(justifications[0])
                    val (pred_doc_id, pred_span) = parseSpan(justifications[1])

                    if (fill_doc_id != pred_doc_id) {
                        throw IOException("Doc IDs must be the same for all spans:" +
                                " $provenanceString")
                    } else {
                        return Provenance(doc_id=pred_doc_id,
                                predicate_justifications=setOf(pred_span),
                                filler_string=fill_span)
                    }
                }
                3 -> {
                    // PREDICATE_JUSTIFICATION;BASE_FILLER;ADDITIONAL_JUSTIFICATION
                    val (pred_doc_id, pred_span) = parseSpanSet(justifications[0])
                    val (base_doc_id, base_span) = parseSpan(justifications[1])
                    val (addi_doc_id, addi_span) = parseSpanSet(justifications[2])

                    if (base_doc_id != pred_doc_id ||
                            (addi_doc_id != pred_doc_id && addi_doc_id != "NIL")) {
                        throw RuntimeException ("Doc IDs must be the same for all spans: " +
                                provenanceString)
                    }
                    return Provenance(doc_id=pred_doc_id,
                            predicate_justifications= pred_span.toSet(),
                            base_filler=base_span,
                            additional_justifications=addi_span.toSet())
                }
                4 -> {
                    // FILLER_STRING;PREDICATE_JUSTIFICATION;BASE_FILLER;ADDITIONAL_JUSTIFICATION
                    val (fill_doc_id, fill_span) = parseSpan(justifications[0])
                    val (pred_doc_id, pred_span) = parseSpanSet(justifications[1])
                    val (base_doc_id, base_span) = parseSpan(justifications[2])
                    val (addi_doc_id, addi_span) = parseSpanSet(justifications[3])

                    if (fill_doc_id != pred_doc_id ||fill_doc_id != base_doc_id ||
                            (fill_doc_id != addi_doc_id && addi_doc_id != "NIL")) {
                        throw IOException("Doc IDs must be the same for all spans: "
                                + provenanceString)
                    } else {
                        return Provenance(doc_id = pred_doc_id,
                                predicate_justifications = pred_span.toSet(),
                                base_filler = base_span,
                                additional_justifications = addi_span.toSet(),
                                filler_string = fill_span)
                    }
                }
                else -> throw RuntimeException("Invalid justification span $provenanceString")
            }
        }

        private fun toNode(nodeName: String) : Node {
            // as you see each reference to an entity or event with an unknown ID, create a node
            // of the appropriate type and remember it for when we see this ID in the future

            val known = idToNode[nodeName]
            if (known != null) {
                return known
            }

            val created = when {
                nodeName.startsWith(":Entity") -> EntityNode()
                nodeName.startsWith(":Event") -> EventNode()
                nodeName.startsWith(":String") -> StringNode()
                else -> throw IOException("Unknown node type for node name $nodeName")
            }

            idToNode.put(nodeName, created)
            return created
        }
    }
}

fun main(args: Array<String>) {
    ColdStartKBLoader().load(Paths.get(args[0]))
}

