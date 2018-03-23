package edu.isi.gaia

import com.google.common.collect.ImmutableMultiset
import mu.KLogging
import org.apache.jena.rdf.model.*
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
import org.apache.jena.tdb.TDBFactory
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.SKOS
import org.apache.jena.vocabulary.XSD
import org.slf4j.LoggerFactory
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/*
Converts ColdStart++ knowledge bases to the GAIA interchange format.

See main method for a description of the parameters expected.
 */

// these could be changed to darpa.mil if the interchange format is adopted program-wide
// TODO: temporarily extend these to include all ColdStart entity types - #2
// TODO: long-term, we need to make the program ontology configurable

object AidaSyntaxOntology {
    val _namespace: String = "http://www.isi.edu/aida/interchangeOntology#"

    // properties
    val SYSTEM = ResourceFactory.createProperty(_namespace + "system")!!
    val CONFIDENCE = ResourceFactory.createProperty(_namespace + "confidence")!!
    val CONFIDENCE_VALUE = ResourceFactory.createProperty(_namespace + "confidenceValue")!!
    val JUSTIFIED_BY = ResourceFactory.createProperty(_namespace + "justifiedBy")!!
    val SOURCE = ResourceFactory.createProperty(_namespace + "source")!!
    val START_OFFSET = ResourceFactory.createProperty(_namespace + "startOffset")!!
    val END_OFFSET_INCLUSIVE = ResourceFactory.createProperty(_namespace
            + "endOffsetInclusive")!!
    val LINK = ResourceFactory.createProperty(_namespace + "link")!!
    val LINK_TARGET = ResourceFactory.createProperty(_namespace + "linkTarget")!!
    val REALIS = ResourceFactory.createProperty(_namespace + "realis")
    val REALIS_VALUE = ResourceFactory.createProperty(_namespace + "realisValue")

    // classes
    val TEXT_PROVENANCE = ResourceFactory.createProperty(_namespace + "TextProvenance")!!
    val LINK_ASSERTION = ResourceFactory.createProperty(_namespace + "LinkAssertion")!!
}

private class Memoize<Arg, Result>(val f: (Arg) -> Result) : (Arg) -> Result {
    private val cache = mutableMapOf<Arg, Result>()
    override fun invoke(arg: Arg) = cache.getOrPut(arg, {f(arg)})
}

object AidaProgramOntology {
    val _namespace: String = "http://www.isi.edu/aida/programOntology#"
    val PERSON = ResourceFactory.createResource(_namespace + "Person")!!
    val ORGANIZATION = ResourceFactory.createResource(_namespace + "Organization")!!
    val LOCATION = ResourceFactory.createResource(_namespace + "Location")!!
    val GPE = ResourceFactory.createResource(_namespace + "GeopoliticalEntity")!!
    val FACILITY = ResourceFactory.createResource(_namespace + "Facility")!!
    val STRING = ResourceFactory.createResource(_namespace + "String")

    val EVENT_AND_RELATION_TYPES = listOf("CONFLICT.ATTACK", "CONFLICT.DEMONSTRATE",
            "CONTACT.BROADCAST", "CONTACT.CONTACT", "CONTACT.CORRESPONDENCE", "CONTACT.MEET",
            "JUSTICE.ARREST-JAIL",
            "LIFE.DIE", "LIFE.INJURE", "MANUFACTURE.ARTIFACT", "MOVEMENT.TRANSPORT-ARTIFACT",
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
            "cause_of_death", "website", "title", "religion", "charges"
            )
            .map { it to ResourceFactory.createResource(_namespace + it.toLowerCase()) }
            .toMap()

    // realis types
    val ACTUAL = ResourceFactory.createResource(_namespace + "Actual")!!
    val GENERIC = ResourceFactory.createResource(_namespace + "Generic")!!
    val OTHER = ResourceFactory.createResource(_namespace + "Other")!!

    val likes = ResourceFactory.createResource(_namespace + "likes")!!
    val dislikes = ResourceFactory.createResource(_namespace + "dislikes")!!

    val ontologizeEventType: (String) -> Resource = Memoize( {eventType: String ->
        ResourceFactory.createResource(_namespace + eventType)})
}



interface NodeGenerator {
    /*
    A strategy for generating RDF graph nodes
     */
    fun nextNode(model:Model): Resource
}

class BlankNodeGenerator : NodeGenerator {
    /*
    A node generation strategy which always returns blank nodes.

    This is useful for testing because we don't need to coordinate entity, event, etc.
    URIs in order to test isomorphism between graphs.  At runtime, it avoids
    generating URIs for nodes which only need to be referred to once as part of a
    large structure (e.g. confidences)
    */
    override fun nextNode(model: Model): Resource {
        return model.createResource()
    }
}

class UUIDNodeGenerator(val baseURI: String) : NodeGenerator {
    /**
     *     A node generation strategy which uses UUIDs appended to a base URI.
      */

    init {
        require(baseURI.isNotEmpty()){ "Base URI cannot be empty"}
        require(!baseURI.endsWith("/")){ "Base URI cannot end in /"}
    }

    override fun nextNode(model: Model): Resource {
        return model.createResource(baseURI + '/' + UUID.randomUUID().toString())
    }
}

class ColdStart2GaiaConverter(val entityNodeGenerator: NodeGenerator = BlankNodeGenerator(),
                              val eventNodeGenerator: NodeGenerator = BlankNodeGenerator(),
                              val assertionNodeGenerator: NodeGenerator = BlankNodeGenerator(),
                              val stringNodeGenerator: NodeGenerator = BlankNodeGenerator()) {
    companion object: KLogging()

    /**
     * Concert a ColdStart KB to an RDFLib graph in the proposed AIDA interchange format.
     */

    fun coldstartToGaia(system_uri: String, cs_kb: ColdStartKB, destinationModel: Model) {
        return Conversion(system_uri, destinationModel).convert(cs_kb)
    }

    private inner class Conversion(val system_uri: String, val model: Model) {
        // stores a mapping of ColdStart objects to their URIs in the interchange format
        val object_to_uri = mutableMapOf<Any, Resource>()

        // this is the URI for the generating system
        val system_node = model.getResource(system_uri)

        // mark a triple as having been generated by this system
        fun associate_with_system(identifier: Resource) {
            identifier.addProperty(AidaSyntaxOntology.SYSTEM, system_node)
        }

        fun markSingleAssertionConfidence(reifiedAssertion: Resource, confidence: Double) {
            //  mark an assertion with confidence from this system
            val confidenceBlankNode = model.createResource()
            reifiedAssertion.addProperty(AidaSyntaxOntology.CONFIDENCE, confidenceBlankNode)
            confidenceBlankNode.addProperty(AidaSyntaxOntology.CONFIDENCE_VALUE,
                    model.createTypedLiteral(confidence))
            associate_with_system(confidenceBlankNode)
        }

        // converts a ColdStart object to an RDF identifier (node in the RDF graph)
        // if this ColdStart node has been previously converted, we return the same RDF identifier
        fun toResource(node: Node): Resource {
            if (!object_to_uri.containsKey(node)) {
                val rdfNode = when (node) {
                    is EntityNode -> entityNodeGenerator.nextNode(model)
                    is EventNode -> eventNodeGenerator.nextNode(model)
                    is StringNode -> stringNodeGenerator.nextNode(model)
                    else -> throw RuntimeException("Cannot make a URI for " + node.toString())
                }
                object_to_uri.put(node, rdfNode)
            }
            return object_to_uri.getValue(node)
        }

        // converts a ColdStart ontology type to a corresponding RDF identifier
        // TODO: This is temporarily hardcoded but will eventually need to be configurable
        // @xujun: you will need to extend this hardcoding
        fun toOntologyType(ontology_type: String): Resource {
            // can't go in the when statement because it has an arbitrary boolean condition
            if (':' in ontology_type) {
                return AidaProgramOntology.ontologizeEventType(ontology_type)
            }

            return when (ontology_type) {
                "PER" -> AidaProgramOntology.PERSON
                "ORG" -> AidaProgramOntology.ORGANIZATION
                "LOC" -> AidaProgramOntology.LOCATION
                "FAC" -> AidaProgramOntology.FACILITY
                "GPE" -> AidaProgramOntology.GPE
                "STRING", "String" -> AidaProgramOntology.STRING
                in AidaProgramOntology.EVENT_AND_RELATION_TYPES.keys ->
                    AidaProgramOntology.EVENT_AND_RELATION_TYPES.getValue(ontology_type)
                else -> throw RuntimeException("Unknown ontology type $ontology_type")
            }
        }

        // below are the functions for translating each individual type of ColdStart assertion
        // into the appropriate RDF structures
        // each will return a boolean specifying whether or not the conversion was successful

        // translate ColdStart type assertions
        fun translateType(cs_assertion: TypeAssertion, confidence: Double?): Boolean {
            val rdfAssertion = assertionNodeGenerator.nextNode(model)
            val entity = toResource(cs_assertion.subject)
            val ontology_type = toOntologyType(cs_assertion.type)
            rdfAssertion.addProperty(RDF.type, RDF.Statement)
            rdfAssertion.addProperty(RDF.subject, entity)
            rdfAssertion.addProperty(RDF.predicate, RDF.type)
            rdfAssertion.addProperty(RDF.`object`, ontology_type)
            markWithConfidenceAndSystem(rdfAssertion, confidence)
            return true
        }

        fun toRealisType(realis: Realis) = when (realis) {
            Realis.actual -> AidaProgramOntology.ACTUAL
            Realis.generic -> AidaProgramOntology.GENERIC
            Realis.other -> AidaProgramOntology.OTHER
        }

        fun markWithConfidenceAndSystem(assertion: Resource, confidence: Double?) {
            if (confidence != null) {
                markSingleAssertionConfidence(assertion, confidence)
            }
            associate_with_system(assertion)
        }

        // translate ColdStart entity mentions
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
                entityResource.addProperty(AidaSyntaxOntology.REALIS, realisNode)
                realisNode.addProperty(AidaSyntaxOntology.REALIS_VALUE,
                        toRealisType(cs_assertion.realis))
                associate_with_system(realisNode)
            }

            registerJustifications(entityResource, cs_assertion.justifications,
                    cs_assertion.string, confidence)

            return true
        }

        fun registerJustifications(resource: Resource,
                                   provenance: Provenance, string: String?=null,
                                   confidence: Double?=null) {
            for (justification in provenance.predicate_justifications) {
                val justification_node = model.createResource()
                if (confidence != null) {
                    markSingleAssertionConfidence(justification_node, confidence)
                }
                associate_with_system(justification_node)
                justification_node.addProperty(RDF.type, AidaSyntaxOntology.TEXT_PROVENANCE)
                justification_node.addProperty(AidaSyntaxOntology.SOURCE,
                        model.createTypedLiteral(provenance.docID))
                justification_node.addProperty(AidaSyntaxOntology.START_OFFSET,
                        model.createTypedLiteral(justification.start))
                justification_node.addProperty(AidaSyntaxOntology.END_OFFSET_INCLUSIVE,
                        model.createTypedLiteral(justification.end_inclusive))
                resource.addProperty(AidaSyntaxOntology.JUSTIFIED_BY, justification_node)

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
            entityResource.addProperty(AidaSyntaxOntology.LINK, linkAssertion)
            // how do we want to handle links to external KBs ? currently we just store
            // them as strings
            linkAssertion.addProperty(RDF.type, AidaSyntaxOntology.LINK_ASSERTION)
            linkAssertion.addProperty(AidaSyntaxOntology.LINK_TARGET,
                    model.createTypedLiteral(cs_assertion.global_id))
            markWithConfidenceAndSystem(linkAssertion, confidence)

            return true
        }

        fun translateRelation(csAssertion: RelationAssertion, confidence: Double?) : Boolean {
            val subjectResouce = toResource(csAssertion.subject)
            val objectResource = toResource(csAssertion.obj)
            val relationAssertion = assertionNodeGenerator.nextNode(model)
            relationAssertion.addProperty(RDF.type, toOntologyType(csAssertion.relationType))
            relationAssertion.addProperty(RDF.subject, subjectResouce)
            relationAssertion.addProperty(RDF.`object`, objectResource)
            markWithConfidenceAndSystem(relationAssertion, confidence)
            registerJustifications(relationAssertion, csAssertion.justifications)
            return true
        }

        fun translateSentiment(csAssertion: SentimentAssertion, confidence: Double?) : Boolean {
            fun toSentimentType(sentiment: String) = when (sentiment) {
                "likes" -> AidaProgramOntology.likes
                "dislikes" -> AidaProgramOntology.dislikes
                else -> throw RuntimeException("Unknown sentiment $sentiment")
            }

            val subjectResouce = toResource(csAssertion.subject)
            val objectResource = toResource(csAssertion.obj)
            val sentimentAssertion = assertionNodeGenerator.nextNode(model)
            sentimentAssertion.addProperty(RDF.type, toSentimentType(csAssertion.sentiment))
            sentimentAssertion.addProperty(RDF.subject, subjectResouce)
            sentimentAssertion.addProperty(RDF.`object`, objectResource)
            markWithConfidenceAndSystem(sentimentAssertion, confidence)
            registerJustifications(sentimentAssertion, csAssertion.justifications)
            return true
        }

        fun translateEventArgument(csAssertion: EventArgumentAssertion, confidence: Double?)
                : Boolean {
            val subjectResource = toResource(csAssertion.subject)
            val objectResource = toResource(csAssertion.argument)
            val eventArgumentAssertion = assertionNodeGenerator.nextNode(model)
            eventArgumentAssertion.addProperty(RDF.type, toOntologyType(csAssertion.argument_role))
            eventArgumentAssertion.addProperty(RDF.subject, subjectResource)
            eventArgumentAssertion.addProperty(RDF.`object`, objectResource)
            markWithConfidenceAndSystem(eventArgumentAssertion, confidence)
            registerJustifications(eventArgumentAssertion, csAssertion.justifications)
            return true
        }

        fun convert(csKB: ColdStartKB) {
            val untranslatableAssertionsB = ImmutableMultiset.builder<Class<Assertion>>()

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
                    is TypeAssertion -> translateType(assertion, confidence)
                    is MentionAssertion -> translateMention(assertion, confidence,
                            objectToCanonicalMentons)
                    is LinkAssertion -> translateLink(assertion, confidence)
                    is SentimentAssertion -> translateSentiment(assertion, confidence)
                    is RelationAssertion -> translateRelation(assertion, confidence)
                    is EventArgumentAssertion -> translateEventArgument(assertion, confidence)
                    else -> false
                }

                if (!translated) {
                    untranslatableAssertionsB.add(assertion.javaClass)
                }

                if (assertionNum > 0 && assertionNum % progressInterval == 0) {
                    logger.info { "Processed $assertionNum / $numAssertions assertions" }
                }
            }

            val untranslatableAssertions = untranslatableAssertionsB.build()

            if (!untranslatableAssertions.isEmpty()) {
                logger.warn("The following ColdStart assertions could not be translated: "
                        + untranslatableAssertions.toString())
            }

            model.setNsPrefix("rdf", RDF.uri)
            model.setNsPrefix("xsd", XSD.getURI())
            model.setNsPrefix("aida", AidaSyntaxOntology._namespace)
            model.setNsPrefix("aidaProgramOntology", AidaProgramOntology._namespace)
            model.setNsPrefix("skos", SKOS.uri)
        }
    }
}


fun main(args: Array<String>) {
    val inputKBFile = Paths.get(args[0])
    val outputRDFPath = Paths.get(args[1])
    val mode = args[2]
    val baseUri = "http://www.isi.edu"

    // we can run in two modes
    // in one mode, we output one big RDF file for the whole KB. If we do that, we need to
    // use an uglier output format (Blocked Turtle) or serializing the output takes forever
    // (see  https://jena.apache.org/documentation/io/rdf-output.html )
    // if working file by file, we write a separate RDF file for each source document in the CS KB
    // and can afford to use pretty-printed Turtle on the output
    val outputFormat: RDFFormat
    val shatterByDocument: Boolean

    when (mode) {
        "full" -> {
            outputFormat = RDFFormat.TURTLE_BLOCKS
            shatterByDocument = false
        }
        "shatter" -> {
            outputFormat = RDFFormat.TURTLE_PRETTY
            shatterByDocument = true
        }
        else -> throw RuntimeException("Invalid mode $mode")
    }

    val logger = LoggerFactory.getLogger("main")

    val coldstartKB = ColdStartKBLoader(shatterByDocument = shatterByDocument).load(inputKBFile)
    val converter = ColdStart2GaiaConverter(
            entityNodeGenerator = UUIDNodeGenerator(baseUri + "/entities"),
            eventNodeGenerator = UUIDNodeGenerator(baseUri + "/events"),
            assertionNodeGenerator = UUIDNodeGenerator(baseUri + "/assertions"))

    // conversion logic shared between the two modes
    fun convertKB(kb: ColdStartKB, model: Model, outPath: Path) {
        converter.coldstartToGaia("http://www.rpi.edu/coldstart", kb, model)
        outPath.toFile().bufferedWriter(UTF_8).use {
            // deprecation is OK because Guava guarantees the writer handles the charset properly
            @Suppress("DEPRECATION")
            RDFDataMgr.write(it, model, outputFormat)
        }
    }


    when (mode) {
        "full" -> {
            val tempDir = createTempDir()
            try {
                logger.info("Using temporary directory $tempDir")
                val dataset = TDBFactory.createDataset(tempDir.absolutePath)
                val model = dataset.defaultModel
                convertKB(coldstartKB, model, outputRDFPath)
            } finally {
                tempDir.deleteRecursively()
            }
        }
        "shatter" -> {
            outputRDFPath.toFile().mkdirs()
            var docsProcessed = 0
            val kbsByDocument = coldstartKB.shatterByDocument()
            for ((docId, perDocKB) in kbsByDocument) {
                docsProcessed += 1
                convertKB(perDocKB, ModelFactory.createDefaultModel(),
                        outputRDFPath.resolve("$docId.turtle"))
                if (docsProcessed % 1000 == 0) {
                    logger.info("Translated $docsProcessed / ${kbsByDocument.size}")
                }
            }
        }
        else -> throw RuntimeException("Can't happen")
    }
}

/**


def main(params: Parameters) -> None:
"""
    A single YAML parameter file is expected as input.
    """
logging_utils.configure_logging_from(params)
# Coldstart KB is assumed to be gzip compressed
coldstart_kb_file = params.existing_file('input_coldstart_gz_file')
output_interchange_file = params.creatable_file('output_interchange_file')
# the URI to be used to identify the system which generated this ColdStart KB
system_uri = params.string('system_uri')
converter = ColdStartToGaiaConverter.from_parameters(params)

_log.info("Loading Coldstart KB from {!s}".format(coldstart_kb_file))
coldstart_kb = ColdStartKBLoader().load(
CharSource.from_gzipped_file(coldstart_kb_file, 'utf-8'))
_log.info("Converting ColdStart KB to RDF graph")
converted_graph = converter.convert_coldstart_to_gaia(system_uri, coldstart_kb)
_log.info("Serializing RDF graph in Turtle format to {!s}".format(output_interchange_file))
with open(output_interchange_file, 'wb') as out:
converted_graph.serialize(destination=out, format='turtle')


if __name__ == '__main__':
if len(sys.argv) == 2:
main(YAMLParametersLoader().load(Path(sys.argv[1])))
**/