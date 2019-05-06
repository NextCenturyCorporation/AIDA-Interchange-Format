package edu.isi.gaia

import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableSetMultimap
import mu.KLogging
import java.io.IOException
import java.nio.file.Path
import java.nio.file.Paths


sealed class Node

class EventNode : Node()

class RelationNode : Node()

class EntityNode : Node()

class StringNode : Node()

data class Span (val start: Int, val end_inclusive: Int) {
    init {
        require(start >= 0) { "Start must be non-negative but got $start" }
        require(start <= end_inclusive) { "Start must less than or equal to end but" +
                " got $start > $end_inclusive" }
    }
}

data class Provenance(val docID: String, val predicate_justifications: Set<Span>,
                      val filler_string: Span? = null, val base_filler: Span? = null,
                      val additional_justifications: Set<Span> = setOf())


interface Assertion {
    val subject: Node
    fun nodes() : Set<Node>
}

data class TypeAssertion(override val subject: Node, val type: String) : Assertion {
    init {
        require(type.isNotEmpty()) {"Type cannot be empty" }
    }

    override fun nodes() = setOf(subject)
}

data class LinkAssertion(override val subject: Node, val global_id: String) : Assertion {
    init {
        require(global_id.isNotEmpty()) {"Global ID can't be empty"}
    }

    override fun nodes() = setOf(subject)
}

// these are currently lowercased, but we enforce it in case someone changes it in the future
// we always compare against lowercase in order to be case-insensitive
val MENTION_TYPES = setOf("mention", "pronominal_mention", "nominal_mention",
        "canonical_mention", "normalized_mention").map { it.toLowerCase() }.toSet()


data class MentionType(val name: String) {
    init {
        require(MENTION_TYPES.contains(name)) { "Expected known mention type but got $name"}
    }
}

val CANONICAL_MENTION = MentionType("canonical_mention")
val PRONOMINAL_MENTION = MentionType("pronominal_mention")
val NOMINAL_MENTION = MentionType("nominal_mention")
val NORMALIZED_MENTION = MentionType("normalized_mention")
val NAME_MENTION = MentionType("mention")

enum class Realis {
    actual, generic, other
}

interface JustifiedAssertion : Assertion {
    val justifications: Provenance
}

interface MentionAssertion : JustifiedAssertion {
    val mentionType: MentionType
    val string: String
}

data class EntityMentionAssertion(override val subject: EntityNode, override val mentionType: MentionType,
                                  override val string: String, override val justifications: Provenance)
    : MentionAssertion {
    override fun nodes() = setOf(subject)
}

data class StringMentionAssertion(override val subject: StringNode,
                                  override val mentionType: MentionType,
                                  override val string: String, override val justifications: Provenance)
    : MentionAssertion {
    override fun nodes() = setOf(subject)
}

data class EventMentionAssertion(override val subject: EventNode, override val mentionType: MentionType,
                                 val realis: Realis, override val string: String,
                                 override val justifications: Provenance) : MentionAssertion {
    override fun nodes() = setOf(subject)
}

data class RelationMentionAssertion(override val subject: RelationNode, override val mentionType: MentionType,
                                    override val string: String,
                                    override val justifications: Provenance) : MentionAssertion {
    override fun nodes() = setOf(subject)
}

data class RelationAssertion(override val subject: Node, val relationType: String,
                        val obj: Node, val justifications: Provenance)
    : Assertion {
    override fun nodes() = setOf(subject, obj)
}

data class SentimentAssertion(override val subject: Node, val sentiment: String,
                             val obj: Node, val justifications: Provenance)
    : Assertion {
    init {
        require(sentiment.isNotEmpty()) { "Empty sentiment not allowed" }
    }
    override fun nodes() = setOf(subject, obj)
}

data class EventArgumentAssertion(override val subject: Node, val argument_role: String,
                                  val realis: Realis?, val argument: Node,
                                  val justifications: Provenance) : Assertion {
    init {
        require(argument_role.isNotEmpty()) { "Empty argument role not allowed" }
    }
    override fun nodes() = setOf(subject, argument)
}

data class RelationArgumentAssertion(override val subject: Node, val argument_role: String,
                                     val argument: Node,
                                     val justifications: Provenance) : Assertion {
    init {
        require(argument_role.isNotEmpty()) { "Empty argument role not allowed" }
    }

    override fun nodes() = setOf(subject, argument)
}

data class ColdStartKB(val assertionsToConfidence: Map<Assertion, Double>,
                  val assertionsWithoutConfidences: Set<Assertion>) {
    val allAssertions: Set<Assertion> by lazy {
        return@lazy assertionsToConfidence.keys.union(assertionsWithoutConfidences)
    }
}


typealias MaybeScoredAssertion = Pair<Assertion, Double?>

class ColdStartKBLoader(
        val breakCrossDocCoref: Boolean = false,
        val requireLinkConfidences: Boolean = true) {
    /**
     * Loads a TAC KBP 2017 ColdStart++ knowledge-base into a [ColdStartKB]
     *
     * If [breakCrossDocCoref] is `true` (default: `false`), this will break all cross-document
     * coreference links by appending document IDs to entity/event IDs.  This is useful when
     * using this KB as input to cross-document linking experiments.
     */
    companion object: KLogging()

    data class LoadingResult(val kb: ColdStartKB,
            // we return this to enable printing more user-friendly error messages
                             val nodeToInputIds: Map<Node, String>) {
        /**
         * Get this ColdStartKB split into one mini-KB per document.
         *
         * @return A map from a document ID to a mini-ColdStartKB containing only items
         * with justifications in that document.
         */
        fun shatterByDocument(): Map<String, LoadingResult> {
            val nodesToDoc = mutableMapOf<Node, String>()

            val fillerNodes = nodeToInputIds.filter { it.value.contains("Filler") }
                    .map { it.key }
                    .toSet()

            // a node is in a document if it has any justification in that document
            for (justifiedAssertion in kb.allAssertions.filterIsInstance<JustifiedAssertion>()) {
                val docID = justifiedAssertion.justifications.docID
                for (node in justifiedAssertion.nodes()) {
                    nodesToDoc[node] = docID
                    if (fillerNodes.contains(node)) {
                        System.out.println("${nodeToInputIds[node]} <-- $node")
                    }
                }
            }

            // group assertions by what document they come from
            // we can determine this by examining what nodes are involved.
            val docIDToAssertionB = ImmutableSetMultimap.builder<String, Assertion>()
            for (assertion in kb.allAssertions) {
                assertion.nodes().forEach {
                    if (!nodesToDoc.containsKey(it)) {
                        System.out.println("failed on $it")
                    }
                }
                val docIDsForAssertion = assertion.nodes()
                        .map {
                            nodesToDoc[it]
                                    ?: throw RuntimeException("Cannot map ${nodeToInputIds[it]} to a document ID; " +
                                            "it must be lacking a mention.")
                        }.toSet()
                check(docIDsForAssertion.size == 1)
                docIDToAssertionB.put(docIDsForAssertion.first(), assertion)
            }

            // add an extension function to Map to make a copy keeping only the specified keys
            fun <K, V> Map<K, V>.copyRestrictingKeys(keysToKeep: Iterable<K>): Map<K, V> {
                return keysToKeep.filter { it in this }.map { it to getValue(it) }.toMap()
            }

            return ImmutableMap.copyOf(docIDToAssertionB.build().asMap().mapValues { (_, assertions) ->
                LoadingResult(kb = ColdStartKB(kb.assertionsToConfidence.copyRestrictingKeys(assertions),
                        // the order here is important - `assertions` is much smaller
                        assertions.intersect(kb.assertionsWithoutConfidences)),
                        nodeToInputIds = nodeToInputIds)
            })
        }
    }

    fun load(source: Path): LoadingResult {
        return Loading().load(source)
    }

    private inner class Loading {
        val _SBJ_NODE_ID = 0
        val _ASSERTION_TYPE = 1
        val _TYPE_STRING = 2
        val _LINK_STRING = 2
        val _OBJ_STRING = 2
        val _OBJ_NODE_ID = 2
        val _JST_STRING = 3
        val _CONF_FLOAT = 4
        private val LINK_CONFIDENCE_FIELD = 3

        val _JUSTIFICATION_PAT = Regex("""^(.+):(\d+)-(\d+)$""")
        val _SPAN_PAT = Regex("""(\d+)-(\d+)""")
        /*val _ENTITY_TYPES: String = (ontologyMapping.entityShortNames().map { it.toLowerCase() } +
                ontologyMapping.entityShortNames().map { it.toUpperCase() })
                .toList().joinToString(separator="|")*/
        val _ASSERTION_PAT = Regex("""^(?:(?:$.*):)?(.+?)\.?(other|generic|actual)?$""")

        val idToNode: MutableMap<String, Node> = HashMap()
        // we track this mapping so we can give more informative error messages
        val nodeToId: MutableMap<Node, String> = HashMap()
        // if `breakCrossDocCoref` is false, this will match `idToNode` exactly
        // otherwise, it will account for each original ColdStart ID now corresponding to many
        // per-document entity nodes
        val rawCSIdToNodesB = ImmutableSetMultimap.builder<String, Node>()!!
        // this won't be available until the second pass of document processing (see `load`)
        var rawCSIdToNodes: ImmutableSetMultimap<String, Node>? = null

        fun load(source: Path): ColdStartKBLoader.LoadingResult {
            val assertionToConfidence = mutableListOf<Pair<Assertion, Double>>()
            val assertionsWithoutConfidence = mutableListOf<Assertion>()

            val progress_interval = 100000

            // we need to process the file twice (see below), so we factor out common parsing logic
            fun parseColdStartFile(msg: String, lineProcessor: (List<String>) -> Collection<MaybeScoredAssertion>) {
                var line_num = 0
                source.toFile().forEachLine(charset = Charsets.UTF_8) {
                    line_num += 1
                    val line = it.trim()
                    // skip first line which contains a KB label
                    if (line_num > 1 && line.isNotEmpty()) {
                        // skip blank lines
                        try {
                            val fields = it.split('\t')
                            for ((assertion, confidence) in lineProcessor(fields)) {
                                if (confidence != null) {
                                    assertionToConfidence.add(assertion to confidence)
                                } else {
                                    assertionsWithoutConfidence.add(assertion)
                                }
                            }
                        } catch (e: Exception) {
                            throw IOException("Failure when parsing line $line_num of $source:\n$line",
                                    e)
                        }
                    }

                    if (line_num % progress_interval == 0) {
                        logger.info { "During pass $msg, processed $line_num lines" }
                    }
                }
            }

            // we need to parse the file twice to support breakCrossDocCoref
            // the reason is that `type` and `link` ColdStart entries don't have any document ID
            // locally available, so when `breakCrossDocCoref` is enabled and we are breaking up
            // entities, etc., we don't know what to apply the types and link assertions to.
            // So on the first pass we determine our set of entity, event, etc. nodes...
            parseColdStartFile("mentions", {
                val assertionType = extractAssertionType(it)
                return@parseColdStartFile when (assertionType.toLowerCase()) {
                    "type" -> listOf()
                    "link" -> listOf()
                    in MENTION_TYPES -> parseMention(it)
                    else -> parsePredicate(it)
                }
            })

            rawCSIdToNodes = rawCSIdToNodesB.build()

            System.out.println(assertionToConfidence.filter { it.first is RelationMentionAssertion }.size)
            System.out.println(assertionsWithoutConfidence.filter { it is RelationMentionAssertion }.size)

            // and on the second pass we associated types with them. This is trivial if
            // `breakCrossDocCoref` is not on, but if it is, for each entity, etc. appearing in the
            // ColdStart KB, we need to add a copy of the type and link assertion for each
            // entity (etc.) we shattered it into.
            parseColdStartFile("types/links") {
                val assertionType = extractAssertionType(it)
                return@parseColdStartFile when (assertionType.toLowerCase()) {
                    "type" -> parseTypeAssertion(it)
                    "link" -> parseLinkAssertion(it)
                    else -> listOf()
                }
            }

            return ColdStartKBLoader.LoadingResult(
                    kb = ColdStartKB(assertionToConfidence.toMap(),
                            assertionsWithoutConfidence.toSet()),
                    nodeToInputIds = nodeToId)
        }

        private fun extractAssertionType(fields: List<String>) : String {
            val assertionTypeField = fields[_ASSERTION_TYPE]
            val match = _ASSERTION_PAT.matchEntire(assertionTypeField)
            return match?.groups?.get(1)?.value
                    ?: throw RuntimeException("Unknown assertion type $assertionTypeField")
        }


        private fun parseTypeAssertion(fields: List<String>): Collection<MaybeScoredAssertion> {
            // we allow two possible values here because RPI's ColdStart output sometimes has
            // a 1.0 confidence here and some times omits it
            require((fields.size == (_TYPE_STRING + 1))
                    or (fields.size == (_TYPE_STRING + 2)))
            { "Wrong number of fields in type assertion" }

            val rawCSSubjectID = fields[_SBJ_NODE_ID]
            checkNotNull(rawCSIdToNodes)
            val subjectNodes = rawCSIdToNodes!!.get(rawCSSubjectID)
            check(subjectNodes.isNotEmpty())

            // NOTE: This is a hack to support performers who can't yet type fillers.
            val isFiller: Boolean = rawCSSubjectID.startsWith(":Filler")
            val trueType: String = if (isFiller) "STRING" else fields[_TYPE_STRING]

            return subjectNodes.map { MaybeScoredAssertion(TypeAssertion(it, trueType), null) }
        }

        private fun parseLinkAssertion(fields: List<String>): Collection<MaybeScoredAssertion> {
            require(fields.size -1 in _LINK_STRING..LINK_CONFIDENCE_FIELD)
            { "Wrong number of fields in link assertion" }

            val rawCSSubjectID = fields[_SBJ_NODE_ID]
            checkNotNull(rawCSIdToNodes)
            val subjectNodes = rawCSIdToNodes!!.get(rawCSSubjectID)
            check(subjectNodes.isNotEmpty())

            val confidence = if (LINK_CONFIDENCE_FIELD < fields.size) {
                fields[LINK_CONFIDENCE_FIELD].toDouble()
            } else {
                if (requireLinkConfidences) {
                    throw RuntimeException("Link assertion is missing required link confidence.")
                } else {
                    null
                }
            }

            return subjectNodes.map { MaybeScoredAssertion(LinkAssertion(it, fields[_LINK_STRING]),
                    confidence) }
        }

        private fun parseMention(fields: List<String>): Collection<MaybeScoredAssertion>  {
            require(fields.size in 4..5) { "Unknown assertion format" }

            val match = _ASSERTION_PAT.matchEntire(fields[_ASSERTION_TYPE])
            val (mention_type, realis) = match?.destructured ?:
                    throw RuntimeException("Unknown mention type")

            // strip surrounding "s if present
            val mention_string = fields[_OBJ_STRING].trim('"')

            val provenance = toJustificationSpan(fields[_JST_STRING])

            val subjectNode = toNode(fields[_SBJ_NODE_ID], provenance.docID)

            val confidence = if (_CONF_FLOAT < fields.size) fields[_CONF_FLOAT].toDouble() else null

            return listOf(when (subjectNode) {
                is EventNode -> {
                    if (realis.isEmpty()) throw IOException("Invalid empty realis on event argument")
                    MaybeScoredAssertion(
                            EventMentionAssertion(subjectNode,
                                    MentionType(mention_type),
                                    Realis.valueOf(realis),
                                    mention_string,
                                    provenance),
                            confidence)
                }
                is RelationNode -> {
                    MaybeScoredAssertion(
                            RelationMentionAssertion(subjectNode,
                                    MentionType(mention_type),
                                    mention_string,
                                    provenance),
                            confidence)
                }
                is EntityNode -> MaybeScoredAssertion(
                        EntityMentionAssertion(subjectNode,
                                MentionType(mention_type),
                                mention_string,
                                provenance),
                        confidence)
                is StringNode -> MaybeScoredAssertion(
                        StringMentionAssertion(subjectNode,
                                MentionType(mention_type),
                                mention_string,
                                provenance),
                        confidence)
                else -> throw IOException("Unknown node type " + fields[_SBJ_NODE_ID])
            })
        }

        val SENTIMENT_RELATIONS = setOf("LIKES", "DISLIKES")
        val INVERSE_SENTIMENT_RELATIONS = setOf("is_disliked_by", "is_liked_by")

        private fun parsePredicate(fields: List<String>): Collection<MaybeScoredAssertion> {
            require(fields.size == 5) {
                "Wrong number of fields in predicate " +
                        "declaration"
            }
            val match = _ASSERTION_PAT.matchEntire(fields[_ASSERTION_TYPE])
            val (relation_type, realis) = match?.destructured
                    ?: throw RuntimeException("Unknown assertion type " + fields[_ASSERTION_TYPE])

            if (relation_type in INVERSE_SENTIMENT_RELATIONS) {
                // both relations and their inverses are present; we only want to process one
                // direction
                return listOf()
            }

            val provenance = toJustificationSpan(fields[_JST_STRING])
            val subjectNode = toNode(fields[_SBJ_NODE_ID], provenance.docID)
            val objectNode = toNode(fields[_OBJ_NODE_ID], provenance.docID)

            if (objectNode is EventNode) {
                // event arguments are encoded with predicates running in both directions,
                // but we only keep one of the,
                return listOf()
            }

            return listOf(when {
                relation_type in SENTIMENT_RELATIONS -> MaybeScoredAssertion(SentimentAssertion(
                        subjectNode,
                        relation_type,
                        objectNode,
                        provenance),
                        fields[_CONF_FLOAT].toDouble())

                subjectNode is EventNode -> {
                    MaybeScoredAssertion(EventArgumentAssertion(
                            subjectNode,
                            relation_type,
                            if (realis.isEmpty()) null else Realis.valueOf(realis),
                            objectNode,
                            provenance),
                            fields[_CONF_FLOAT].toDouble())
                }

                subjectNode is RelationNode -> {
                    MaybeScoredAssertion(RelationArgumentAssertion(
                            subjectNode,
                            relation_type,
                            objectNode,
                            provenance),
                            fields[_CONF_FLOAT].toDouble())
                }

                else -> throw java.lang.RuntimeException("Cannot parse assertion")
            })
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
                        return Provenance(docID =pred_doc_id,
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
                    return Provenance(docID =pred_doc_id,
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
                        return Provenance(docID = pred_doc_id,
                                predicate_justifications = pred_span.toSet(),
                                base_filler = base_span,
                                additional_justifications = addi_span.toSet(),
                                filler_string = fill_span)
                    }
                }
                else -> throw RuntimeException("Invalid justification span $provenanceString")
            }
        }

        private fun toNode(nodeName: String, documentID: String) : Node {
            // as you see each reference to an entity or event with an unknown ID, create a node
            // of the appropriate type and remember it for when we see this ID in the future

            val trueNodeName = if (breakCrossDocCoref) "$nodeName-$documentID" else nodeName

            val known = idToNode[trueNodeName]
            if (known != null) {
                return known
            }

            val created = when {
                trueNodeName.startsWith(":Entity") -> EntityNode()
                trueNodeName.startsWith(":Filler") -> EntityNode()
                trueNodeName.startsWith(":Event") -> EventNode()
                trueNodeName.startsWith(":Relation") -> RelationNode()
                trueNodeName.startsWith(":String") -> StringNode()
                else -> throw IOException("Unknown node type for node name $nodeName")
            }

            idToNode.put(trueNodeName, created)
            // we track what IDs in the CS input file go with each node so we can print
            // informative error messages
            nodeToId.put(created, trueNodeName)
            // if we are shattering by document, we need to keep track of all nodes originating
            // from the same original ColdStart ID (without the doc ID prefix) so we can
            // copy type and linking assertions to each of them
            rawCSIdToNodesB.put(nodeName, created)
            return created
        }
    }
}

fun main(args: Array<String>) {
    ColdStartKBLoader().load(Paths.get(args[0]))
}

