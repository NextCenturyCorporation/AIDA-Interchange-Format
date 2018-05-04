package edu.isi.gaia

import com.google.common.base.Charsets
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableMultiset
import com.google.common.io.Files
import edu.isi.nlp.files.FileUtils
import edu.isi.nlp.parameters.Parameters.loadSerifStyle
import edu.isi.nlp.symbols.Symbol
import mu.KLogging
import org.apache.jena.rdf.model.*
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
import org.apache.jena.tdb.TDBFactory
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.SKOS
import org.apache.jena.vocabulary.XSD
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.util.*

/*
Converts ColdStart++ knowledge bases to the AIDA Interchange Format (AIF).

See main method for a description of the parameters expected.
 */

/**
 * The non-domain-specific portion of the AIDA ontology.
 */
object AidaAnnotationOntology {
    // URI would change from isi.edu to something else if adopted program-wide
    val _namespace: String = "http://www.isi.edu/aida/interchangeOntology#"

    // properties
    val SYSTEM_PROPERTY = ResourceFactory.createProperty(_namespace + "system")!!
    val CONFIDENCE = ResourceFactory.createProperty(_namespace + "confidence")!!
    val CONFIDENCE_VALUE = ResourceFactory.createProperty(_namespace + "confidenceValue")!!
    val JUSTIFIED_BY = ResourceFactory.createProperty(_namespace + "justifiedBy")!!
    val SOURCE = ResourceFactory.createProperty(_namespace + "source")!!
    val START_OFFSET = ResourceFactory.createProperty(_namespace + "startOffset")!!
    val END_OFFSET_INCLUSIVE = ResourceFactory.createProperty(_namespace
            + "endOffsetInclusive")!!
    val LINK = ResourceFactory.createProperty(_namespace + "link")!!
    val LINK_TARGET = ResourceFactory.createProperty(_namespace + "linkTarget")!!
    val REALIS = ResourceFactory.createProperty(_namespace + "realis")!!
    val REALIS_VALUE = ResourceFactory.createProperty(_namespace + "realisValue")!!
    val PROTOTYPE = ResourceFactory.createProperty(_namespace + "prototype")!!
    val CLUSTER_PROPERTY = ResourceFactory.createProperty(_namespace + "cluster")!!
    val CLUSTER_MEMBER = ResourceFactory.createProperty(_namespace + "clusterMember")!!

    // classes
    val SYSTEM = ResourceFactory.createResource(_namespace + "System")
    val ENTITY = ResourceFactory.createResource(_namespace + "Entity")!!
    val RELATION = ResourceFactory.createResource(_namespace + "Relation")!!
    val EVENT = ResourceFactory.createResource(_namespace + "Event")!!
    val CONFIDENCE_CLASS = ResourceFactory.createResource(_namespace + "Confidence")!!
    val TEXT_PROVENANCE = ResourceFactory.createResource(_namespace + "TextProvenance")!!
    val LINK_ASSERTION = ResourceFactory.createResource(_namespace + "LinkAssertion")!!
    val KNOWLEDGE_GRAPH = ResourceFactory.createResource(_namespace + "KnowledgeGraph")!!
    val SAME_AS_CLUSTER = ResourceFactory.createResource(_namespace + "SameAsCluster")!!
    val CLUSTER_MEMBERSHIP = ResourceFactory.createResource(_namespace + "ClusterMembership")!!
}

// used in AidaDomainOntology
private class Memoize<in Arg, out Result>(val f: (Arg) -> Result) : (Arg) -> Result {
    private val cache = mutableMapOf<Arg, Result>()
    override fun invoke(arg: Arg) = cache.getOrPut(arg, { f(arg) })
}

/**
 * The domain ontology.
 *
 * For the moment, this is hard-coded to match ColdStart.
 */
object AidaDomainOntology {
    val _namespace: String = "http://www.isi.edu/aida/programOntology#"
    val PERSON = ResourceFactory.createResource(_namespace + "Person")!!
    val ORGANIZATION = ResourceFactory.createResource(_namespace + "Organization")!!
    val LOCATION = ResourceFactory.createResource(_namespace + "Location")!!
    val GPE = ResourceFactory.createResource(_namespace + "GeopoliticalEntity")!!
    val FACILITY = ResourceFactory.createResource(_namespace + "Facility")!!
    val STRING = ResourceFactory.createResource(_namespace + "String")!!

    val ENTITY_TYPES = setOf(PERSON, ORGANIZATION, LOCATION, GPE, FACILITY)

    val EVENT_AND_RELATION_TYPES = listOf("CONFLICT.ATTACK", "CONFLICT.DEMONSTRATE",
            "CONTACT.BROADCAST", "CONTACT.CONTACT", "CONTACT.CORRESPONDENCE", "CONTACT.MEET",
            "JUSTICE.ARREST-JAIL",
            "LIFE.DIE", "LIFE.INJURE", "MANUFACTURE.ARTIFACT",
            "MOVEMENT.TRANSPORT-ARTIFACT",
            "MOVEMENT.TRANSPORT-PERSON", "PERSONNEL.ELECT", "PERSONNEL.END-POSITION",
            "PERSONNEL.START-POSITION", "TRANSACTION.TRANSACTION", "TRANSACTION.TRANSFER-MONEY",
            "TRANSACTION.TRANSFER-OWNERSHIP", "children", "parents", "other_family", "other_family",
            "parents", "children", "siblings", "siblings", "spouse", "spouse",
            "employee_or_member_of", "employees_or_members", "schools_attended", "students",
            "city_of_birth", "births_in_city", "stateorprovince_of_birth",
            "births_in_stateorprovince", "country_of_birth", "births_in_country",
            "cities_of_residence", "residents_of_city",
            "statesorprovinces_of_residence", "residents_of_stateorprovince",
            "countries_of_residence", "residents_of_country",
            "city_of_death", "deaths_in_city",
            "stateorprovince_of_death", "deaths_in_stateorprovince",
            "country_of_death", "deaths_in_country",
            "shareholders", "holds_shares_in",
            "founded_by", "organizations_founded",
            "top_members_employees", "top_member_employee_of",
            "member_of", "members",
            "members", "member_of",
            "parents", "subsidiaries",
            "subsidiaries", "parents",
            "city_of_headquarters", "headquarters_in_city",
            "stateorprovince_of_headquarters", "headquarters_in_stateorprovince",
            "country_of_headquarters", "headquarters_in_country",
            "alternate_names", "alternate_names", "date_of_birth",
            "political_religious_affiliation", "age", "number_of_employees_members",
            "origin", "date_founded", "date_of_death", "date_dissolved",
            "cause_of_death", "website", "title", "religion", "charges",
            // needed to read RPI ColdStart output
            "CONTACT.PHONEWRITE", "BUSINESS.DECLAREBANKRUPTCY", "BUSINESS.ENDORG",
            "BUSINESS.MERGEORG", "BUSINESS.STARTORG", "MOVEMENT.TRANSPORT", "JUSTICE.ACQUIT",
            "JUSTICE.APPEAL", "JUSTICE.CHARGEINDICT", "JUSTICE.CONVICT", "JUSTICE.EXECUTE",
            "JUSTICE.EXTRADITE", "JUSTICE.FINE", "JUSTICE.RELEASEPAROLE", "JUSTICE.SENTENCE",
            "JUSTICE.SUE", "JUSTICE.TRIALHEARING", "LIFE.BEBORN", "LIFE.MARRY", "LIFE.DIVORCE",
            "PERSONNEL.NOMINATE")
            .map { it to ResourceFactory.createResource(_namespace + it.toLowerCase()) }
            .toMap()

    // realis types
    val ACTUAL = ResourceFactory.createResource(_namespace + "Actual")!!
    val GENERIC = ResourceFactory.createResource(_namespace + "Generic")!!
    val OTHER = ResourceFactory.createResource(_namespace + "Other")!!

    // sentiment types
    val likes = ResourceFactory.createResource(_namespace + "likes")!!
    val dislikes = ResourceFactory.createResource(_namespace + "dislikes")!!

    val ontologizeEventType: (String) -> Resource = Memoize({ eventType: String ->
        ResourceFactory.createResource(_namespace + eventType)
    })
}


/**
A strategy for generating RDF graph nodes
 */
interface NodeGenerator {
    fun nextNode(model: Model): Resource
}

/**
A node generation strategy which always returns blank nodes.

This is useful for testing because we don't need to coordinate entity, event, etc.
URIs in order to test isomorphism between graphs.  At runtime, it avoids
generating URIs for nodes which only need to be referred to once as part of a
large structure (e.g. confidences)
 */
class BlankNodeGenerator : NodeGenerator {
    override fun nextNode(model: Model): Resource {
        return model.createResource()
    }
}

/**
 *     A node generation strategy which uses UUIDs appended to a base URI.
 */
class UUIDNodeGenerator(val baseURI: String) : NodeGenerator {
    init {
        require(baseURI.isNotEmpty()) { "Base URI cannot be empty" }
        require(!baseURI.endsWith("/")) { "Base URI cannot end in /" }
    }

    override fun nextNode(model: Model): Resource {
        return model.createResource(baseURI + '/' + UUID.randomUUID().toString())
    }
}

/**
 * Allows logging of the types of all ColdStart assertions which could not be translated
 */
class UntranslatableColdstartAssertionTypeLogger {
    private val untranslatableAssertionTypesB = ImmutableMultiset.builder<Class<Assertion>>()

    fun observe(assertion: Assertion) {
        untranslatableAssertionTypesB.add(assertion.javaClass)
    }

    fun logUntranslatableTypesMessage() : String {
        val untranslatable = untranslatableAssertionTypesB.build()
        return "The following ColdStart assertions could not be translated: $untranslatable"
    }
}

/**
 * Can convert a ColdStart++ KB to the AIDA Interchange Format (AIF).
 */
class ColdStart2AidaInterchangeConverter(
        val entityNodeGenerator: NodeGenerator = BlankNodeGenerator(),
        val eventNodeGenerator: NodeGenerator = BlankNodeGenerator(),
        val assertionNodeGenerator: NodeGenerator = BlankNodeGenerator(),
        val stringNodeGenerator: NodeGenerator = BlankNodeGenerator(),
        val clusterNodeGenerator: NodeGenerator = BlankNodeGenerator(),
        val useClustersForCoref: Boolean = false,
        val restrictConfidencesToJustifications: Boolean = false) {
    companion object : KLogging()

    /**
     * Concert a ColdStart KB to an RDFLib graph in the proposed AIDA interchange format.
     *
     * {@code system_uri) is the URI to mark as generating all content produced from this ColdStart
     * KB.
     * {@code cs_kb} is the ColdStart KB to translate.
     * {@code destinationModel} is the JENA {@link Model} which will be populated with the AIF
     * translation of the ColdStart KB contents.
     * The optional parameter {@code untranslatableAssertionListener} allows the user to take
     * action on assertions the converter doesn't know how to deal with.
     */
    fun coldstartToAidaInterchange(
            system_uri: String, cs_kb: ColdStartKB, destinationModel: Model,
            untranslatableAssertionListener: (Assertion) -> Unit = {} ) {
        return Conversion(system_uri, destinationModel, untranslatableAssertionListener).convert(cs_kb)
    }

    /**
     * Bundles together all the state for a conversion process.
     *
     * We use an inner class for this so that the converter object itself stays thread-safe.
     */
    private inner class Conversion(system_uri: String, val model: Model,
                                   val untranslatableAssertionListener: (Assertion) -> Unit) {
        // stores a mapping of ColdStart objects to their URIs in the interchange format
        val object_to_uri = mutableMapOf<Any, Resource>()

        // this is the URI for the generating system
        val system_node = model.getResource(system_uri)!!

        init {
            // mark the system as a system
            system_node.addProperty(RDF.type, AidaAnnotationOntology.SYSTEM)
        }

        // mark a triple as having been generated by this system
        fun associate_with_system(identifier: Resource) {
            identifier.addProperty(AidaAnnotationOntology.SYSTEM_PROPERTY, system_node)
        }

        // the confidence for a KB-level assertion (event, relation, etc.) will only be marked
        // if restrictConfidencesToJustification is false
        fun markKBAssertionWithConfidenceAndSystem(assertion: Resource, confidence: Double?) {
            markWithConfidenceAndSystemCommon(assertion,
                    if (restrictConfidencesToJustifications) null else confidence)
        }


        private fun markWithConfidenceAndSystemCommon(assertion: Resource, confidence: Double?) {
            if (confidence != null) {
                //  mark an assertion with confidence from this system
                val confidenceBlankNode = model.createResource()
                confidenceBlankNode.addProperty(RDF.type, AidaAnnotationOntology.CONFIDENCE_CLASS)
                assertion.addProperty(AidaAnnotationOntology.CONFIDENCE, confidenceBlankNode)
                confidenceBlankNode.addProperty(AidaAnnotationOntology.CONFIDENCE_VALUE,
                        model.createTypedLiteral(confidence))
                associate_with_system(confidenceBlankNode)
            }
            associate_with_system(assertion)
        }

        // converts a ColdStart object to an RDF identifier (node in the RDF graph)
        // if this ColdStart node has been previously converted, we return the same RDF identifier
        fun toResource(node: Node): Resource {
            if (!object_to_uri.containsKey(node)) {
                val (type, rdfNode) = when (node) {
                    is EntityNode -> Pair(AidaAnnotationOntology.ENTITY,
                            entityNodeGenerator.nextNode(model))
                    is EventNode -> Pair(AidaAnnotationOntology.EVENT,
                            eventNodeGenerator.nextNode(model))
                    is StringNode -> Pair(null, stringNodeGenerator.nextNode(model))
                    else -> throw RuntimeException("Cannot make a URI for " + node.toString())
                }
                if (type != null) {
                    rdfNode.addProperty(RDF.type, type)
                }

                object_to_uri.put(node, rdfNode)
            }
            return object_to_uri.getValue(node)
        }

        // converts a ColdStart ontology type to a corresponding RDF identifier
        fun toOntologyType(ontology_type: String): Resource {
            // can't go in the when statement because it has an arbitrary boolean condition
            // this handles ColdStart event arguments
            if (':' in ontology_type) {
                return AidaDomainOntology.ontologizeEventType(ontology_type)
            }

            return when (ontology_type) {
                "PER" -> AidaDomainOntology.PERSON
                "ORG" -> AidaDomainOntology.ORGANIZATION
                "LOC" -> AidaDomainOntology.LOCATION
                "FAC" -> AidaDomainOntology.FACILITY
                "GPE" -> AidaDomainOntology.GPE
                "STRING", "String" -> AidaDomainOntology.STRING
                in AidaDomainOntology.EVENT_AND_RELATION_TYPES.keys ->
                    AidaDomainOntology.EVENT_AND_RELATION_TYPES.getValue(ontology_type)
                else -> throw RuntimeException("Unknown ontology type $ontology_type")
            }
        }

        fun toRealisType(realis: Realis) = when (realis) {
            Realis.actual -> AidaDomainOntology.ACTUAL
            Realis.generic -> AidaDomainOntology.GENERIC
            Realis.other -> AidaDomainOntology.OTHER
        }

        // below are the functions for translating each individual type of ColdStart assertion
        // into the appropriate RDF structures
        // each will return a boolean specifying whether or not the conversion was successful

        fun translateTypeAssertion(cs_assertion: TypeAssertion, confidence: Double?): Boolean {
            val rdfAssertion = assertionNodeGenerator.nextNode(model)
            val entity = toResource(cs_assertion.subject)
            val ontology_type = toOntologyType(cs_assertion.type)
            rdfAssertion.addProperty(RDF.type, RDF.Statement)
            rdfAssertion.addProperty(RDF.subject, entity)
            rdfAssertion.addProperty(RDF.predicate, RDF.type)
            rdfAssertion.addProperty(RDF.`object`, ontology_type)
            markKBAssertionWithConfidenceAndSystem(rdfAssertion, confidence)

            // when requested to use clusters, we need to generate a cluster for each entity
            // and event the first time it is mentioned
            // we trigger this on the type assertion because such an assertion should occur
            // exactly once on each coreffable object
            val isCoreffableObject = cs_assertion.subject is EntityNode
                    || cs_assertion.subject is EventNode
            if (useClustersForCoref && isCoreffableObject) {
                makeCluster(entity)
            }
            return true
        }

        /**
         * this is used only when useClusterForCoref is true. See documentation of that parameter
         * for details.
         */
        fun makeCluster(entityOrEvent: Resource) {
            val clusterNode: Resource = clusterNodeGenerator.nextNode(model)
            clusterNode.addProperty(RDF.type, AidaAnnotationOntology.SAME_AS_CLUSTER)
            clusterNode.addProperty(AidaAnnotationOntology.PROTOTYPE, entityOrEvent)
            associate_with_system(clusterNode)
            val clusterLinkAssertion = assertionNodeGenerator.nextNode(model)
            clusterLinkAssertion.addProperty(RDF.type,
                    AidaAnnotationOntology.CLUSTER_MEMBERSHIP)
            clusterLinkAssertion.addProperty(AidaAnnotationOntology.CLUSTER_PROPERTY, clusterNode)
            clusterLinkAssertion.addProperty(AidaAnnotationOntology.CLUSTER_MEMBER, entityOrEvent)
            markWithConfidenceAndSystemCommon(clusterLinkAssertion, 1.0)
        }

        fun translateMention(cs_assertion: MentionAssertion, confidence: Double?,
                             objectToCanonicalMentions: Map<Node, Provenance>): Boolean {
            val entityResource = toResource(cs_assertion.subject)
            // if this is a canonical mention, then we need to make a skos:preferredLabel triple
            if (cs_assertion.mention_type == CANONICAL_MENTION) {
                // TODO: because skos:preferredLabel isn't reified we can't attach info
                // on the generating system
                entityResource.addProperty(SKOS.prefLabel,
                        model.createTypedLiteral(cs_assertion.string))
            } else if (cs_assertion.justifications
                    == objectToCanonicalMentions.getValue(cs_assertion.subject)) {
                // this mention assertion just duplicates a canonical mention assertion
                // so we won't add a duplicate RDF structure for it
                return false
            }
            associate_with_system(entityResource)

            if (cs_assertion is EventMentionAssertion) {
                val realisNode = model.createResource()
                entityResource.addProperty(AidaAnnotationOntology.REALIS, realisNode)
                realisNode.addProperty(AidaAnnotationOntology.REALIS_VALUE,
                        toRealisType(cs_assertion.realis))
                associate_with_system(realisNode)
            }

            registerJustifications(entityResource, cs_assertion.justifications,
                    cs_assertion.string, confidence)

            return true
        }

        fun registerJustifications(resource: Resource,
                                   provenance: Provenance, string: String? = null,
                                   confidence: Double? = null) {
            for ((start, end_inclusive) in provenance.predicate_justifications) {
                val justification_node = model.createResource()
                if (confidence != null) {
                    markWithConfidenceAndSystemCommon(justification_node, confidence)
                }
                associate_with_system(justification_node)
                justification_node.addProperty(RDF.type, AidaAnnotationOntology.TEXT_PROVENANCE)
                justification_node.addProperty(AidaAnnotationOntology.SOURCE,
                        model.createTypedLiteral(provenance.docID))
                justification_node.addProperty(AidaAnnotationOntology.START_OFFSET,
                        model.createTypedLiteral(start))
                justification_node.addProperty(AidaAnnotationOntology.END_OFFSET_INCLUSIVE,
                        model.createTypedLiteral(end_inclusive))
                resource.addProperty(AidaAnnotationOntology.JUSTIFIED_BY, justification_node)

                if (string != null) {
                    justification_node.addProperty(SKOS.prefLabel,
                            model.createTypedLiteral(string))
                }
            }
        }

        // translate ColdStart link assertions
        fun translateLink(cs_assertion: LinkAssertion, confidence: Double?): Boolean {
            val entityResource = toResource(cs_assertion.subject)
            val linkAssertion = model.createResource()
            entityResource.addProperty(AidaAnnotationOntology.LINK, linkAssertion)
            // TODO: how do we want to handle links to external KBs? currently we just store
            // them as strings
            linkAssertion.addProperty(RDF.type, AidaAnnotationOntology.LINK_ASSERTION)
            linkAssertion.addProperty(AidaAnnotationOntology.LINK_TARGET,
                    model.createTypedLiteral(cs_assertion.global_id))
            markKBAssertionWithConfidenceAndSystem(linkAssertion, confidence)

            return true
        }

        fun translateRelation(csAssertion: RelationAssertion, confidence: Double?): Boolean {
            val subjectResouce = toResource(csAssertion.subject)
            val objectResource = toResource(csAssertion.obj)
            val relationAssertion = assertionNodeGenerator.nextNode(model)
            relationAssertion.addProperty(RDF.type, RDF.Statement)
            relationAssertion.addProperty(RDF.subject, subjectResouce)
            relationAssertion.addProperty(RDF.predicate, toOntologyType(csAssertion.relationType))
            relationAssertion.addProperty(RDF.`object`, objectResource)
            markKBAssertionWithConfidenceAndSystem(relationAssertion, confidence)
            registerJustifications(relationAssertion, csAssertion.justifications)
            return true
        }

        fun translateSentiment(csAssertion: SentimentAssertion, confidence: Double?): Boolean {
            fun toSentimentType(sentiment: String) = when (sentiment) {
                "likes" -> AidaDomainOntology.likes
                "dislikes" -> AidaDomainOntology.dislikes
                else -> throw RuntimeException("Unknown sentiment $sentiment")
            }

            val subjectResouce = toResource(csAssertion.subject)
            val objectResource = toResource(csAssertion.obj)
            val sentimentAssertion = assertionNodeGenerator.nextNode(model)
            sentimentAssertion.addProperty(RDF.type, RDF.Statement)
            sentimentAssertion.addProperty(RDF.subject, subjectResouce)
            sentimentAssertion.addProperty(RDF.predicate, toSentimentType(csAssertion.sentiment))
            sentimentAssertion.addProperty(RDF.`object`, objectResource)
            markKBAssertionWithConfidenceAndSystem(sentimentAssertion, confidence)
            registerJustifications(sentimentAssertion, csAssertion.justifications)
            return true
        }

        fun translateEventArgument(csAssertion: EventArgumentAssertion, confidence: Double?)
                : Boolean {
            val subjectResource = toResource(csAssertion.subject)
            val objectResource = toResource(csAssertion.argument)
            val eventArgumentAssertion = assertionNodeGenerator.nextNode(model)
            // TODO: type should get its own statement here
            eventArgumentAssertion.addProperty(RDF.type, RDF.Statement)
            eventArgumentAssertion.addProperty(RDF.subject, subjectResource)
            eventArgumentAssertion.addProperty(RDF.predicate, toOntologyType(csAssertion.argument_role))
            eventArgumentAssertion.addProperty(RDF.`object`, objectResource)
            markKBAssertionWithConfidenceAndSystem(eventArgumentAssertion, confidence)
            registerJustifications(eventArgumentAssertion, csAssertion.justifications)
            return true
        }

        fun convert(csKB: ColdStartKB) {
            val progressInterval = 100000
            val numAssertions = csKB.allAssertions.size
            // when an item has a canonical mention assertion in the ColdStart database, it will
            // also have a corresponding regular mention assertion.  We don't want duplicate
            // RDF structures of these in our output, though, so we track what the canonical
            // mention is for each object so we can block translation of the duplicate mention
            // assertion in `translateMention`
            val objectToCanonicalMentons = csKB.allAssertions.filterIsInstance<MentionAssertion>()
                    .filter { it.mention_type == CANONICAL_MENTION }
                    .map { it.subject to it.justifications }.toMap()

            for ((assertionNum, assertion) in csKB.allAssertions.withIndex()) {
                // note not all ColdStart assertions have confidences
                val confidence = csKB.assertionsToConfidence[assertion]
                val translated = when (assertion) {
                    is TypeAssertion -> translateTypeAssertion(assertion, confidence)
                    is MentionAssertion -> translateMention(assertion, confidence,
                            objectToCanonicalMentons)
                    is LinkAssertion -> translateLink(assertion, confidence)
                    is SentimentAssertion -> translateSentiment(assertion, confidence)
                    is RelationAssertion -> translateRelation(assertion, confidence)
                    is EventArgumentAssertion -> translateEventArgument(assertion, confidence)
                    else -> false
                }

                if (!translated) {
                    untranslatableAssertionListener(assertion)
                }

                if (assertionNum > 0 && assertionNum % progressInterval == 0) {
                    logger.info { "Processed $assertionNum / $numAssertions assertions" }
                }
            }

            model.setNsPrefix("rdf", RDF.uri)
            model.setNsPrefix("xsd", XSD.getURI())
            model.setNsPrefix("aida", AidaAnnotationOntology._namespace)
            model.setNsPrefix("aidaProgramOntology", AidaDomainOntology._namespace)
            model.setNsPrefix("skos", SKOS.uri)
        }
    }
}

/**
 * The modes the converter can be run in.
 */
enum class Mode {
    /**
     * Translates the entire ColdStart KB into one big AIF RDF file.
     */
    FULL,
    /**
     * Translates the content related to each document in the ColdStart KB into its own
     * separate AIF RDF file.
     */
    SHATTER
}

fun main(args: Array<String>) {
    val params = loadSerifStyle(File(args[0]))
    val inputKBFile = params.getExistingFile("inputKBFile").toPath()
    val baseUri = params.getString("baseURI")
    val systemUri = params.getString("systemURI")

    // we can run in two modes
    // in one mode, we output one big RDF file for the whole KB. If we do that, we need to
    // use an uglier output format (Blocked Turtle) or serializing the output takes forever
    // (see  https://jena.apache.org/documentation/io/rdf-output.html )
    // if working file by file, we write a separate RDF file for each source document in the CS KB
    // and can afford to use pretty-printed Turtle on the output
    // the mode difference also affects whether we expect the output path to be a file or a
    // directory
    val outputPath: Path
    val outputFormat: RDFFormat

    // the ColdStart format already includes cross-document coref information. If this is true,
    // we throw this information away during conversion

    val breakCrossDocCoref: Boolean
    // should confidences be attach directly to the entity or assertion they pertain to or to
    // the justifications thereof? When working at the single-document level (TA1 -> TA2), it should
    // be the latter; in TA2 and TA3, the former.
    val restrictConfidencesToJustifications: Boolean

    val mode = params.getEnum("mode", Mode::class.java)!!
    when(mode) {
        Mode.FULL -> {
            outputPath = params.getCreatableFile("outputAIFFile").toPath()
            outputFormat = RDFFormat.TURTLE_BLOCKS
            breakCrossDocCoref = params.getOptionalBoolean("breakCrossDocCoref").or(false)
            restrictConfidencesToJustifications =
                    params.getOptionalBoolean("restrictConfidencesToJustifications").or(false)
        }
        Mode.SHATTER -> {
            outputPath = params.getCreatableDirectory("outputAIFDirectory").toPath()
            outputFormat = RDFFormat.TURTLE_PRETTY
            breakCrossDocCoref = true
            restrictConfidencesToJustifications = true
        }
    }

    // In AIDA, there can be uncertainty about coreference, so the AIDA interchange format provides
    // a means of representing coreference uncertainty.  In ColdStart, however, coref
    // decisions were always "hard". We provide the user with the option of whether to encode these
    // coref decisions in the way they would be encoded in AIDA if there were any uncertainty so
    // that users can test these data structures
    val useClustersForCoref = params.getOptionalBoolean("useClustersForCoref").or(false)

    val logger = LoggerFactory.getLogger("main")

    // we need to let the ColdStart KB loader itself know we are shattering by document so it
    // knows to eliminate the cross-document coreference links which have already been added by
    // the ColdStart system
    val coldstartKB = ColdStartKBLoader(breakCrossDocCoref = breakCrossDocCoref).load(inputKBFile)
    val converter = ColdStart2AidaInterchangeConverter(
            entityNodeGenerator = UUIDNodeGenerator(baseUri + "/entities"),
            eventNodeGenerator = UUIDNodeGenerator(baseUri + "/events"),
            assertionNodeGenerator = UUIDNodeGenerator(baseUri + "/assertions"),
            clusterNodeGenerator = UUIDNodeGenerator(baseUri + "/clusters"),
            useClustersForCoref = useClustersForCoref,
            restrictConfidencesToJustifications = restrictConfidencesToJustifications)

    // this will track which assertions could not be converted. This is useful for debugging.
    // we pull this out into its own object instead of doing it inside the conversion method
    // so that it will aggregate results across doc-level KB conversions when running in shatter
    // mode
    val untranslatableAssertionListener = UntranslatableColdstartAssertionTypeLogger()

    // conversion logic shared between the two modes
    fun convertKB(kb: ColdStartKB, model: Model, outPath: Path) {
        converter.coldstartToAidaInterchange(systemUri, kb, model,
                untranslatableAssertionListener = untranslatableAssertionListener::observe)
        outPath.toFile().bufferedWriter(UTF_8).use {
            // deprecation is OK because Guava guarantees the writer handles the charset properly
            @Suppress("DEPRECATION")
            RDFDataMgr.write(it, model, outputFormat)
        }
    }


    when (mode) {
        // converting entire ColdStart KB at once
        Mode.FULL -> {
            // we use a temporary directory to back a triple store in case there is too much
            // to fit in memory
            val tempDir = createTempDir()
            try {
                logger.info("Using temporary directory $tempDir")
                val dataset = TDBFactory.createDataset(tempDir.absolutePath)
                val model = dataset.defaultModel
                convertKB(coldstartKB, model, outputPath)
            } finally {
                tempDir.deleteRecursively()
            }
        }
        // converting document-by-document
        Mode.SHATTER -> {
            outputPath.toFile().mkdirs()
            // for the convenience of programs processing the output, we provide
            // a list of all doc-level turtle files generated and a file mapping from doc IDs
            // to these files
            val outputFileList = mutableListOf<File>()
            val outputFileMap = ImmutableMap.builder<Symbol, File>()

            var docsProcessed = 0
            val kbsByDocument = coldstartKB.shatterByDocument()
            for ((docId, perDocKB) in kbsByDocument) {
                docsProcessed += 1
                val outputFile = outputPath.resolve("$docId.turtle")
                convertKB(perDocKB, ModelFactory.createDefaultModel(),
                        outputFile)

                outputFileList.add(outputFile.toFile())
                outputFileMap.put(Symbol.from(docId), outputFile.toFile())

                if (docsProcessed % 1000 == 0) {
                    logger.info("Translated $docsProcessed / ${kbsByDocument.size}")
                }
            }


            val listFile = outputPath.resolve("translated_files.list").toFile()
            FileUtils.writeFileList(outputFileList,
                    Files.asCharSink(listFile, Charsets.UTF_8))
            val mapFile = outputPath.resolve("translated_files.map").toFile()
            FileUtils.writeSymbolToFileMap(outputFileMap.build(),
                    Files.asCharSink(mapFile, Charsets.UTF_8))
            ColdStart2AidaInterchangeConverter.logger.info(
                    "Wrote list and map of translated files to $listFile and $mapFile " +
                            "respectively")
        }
    }

    ColdStart2AidaInterchangeConverter.logger.info(
            untranslatableAssertionListener.logUntranslatableTypesMessage())
}
