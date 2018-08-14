package edu.isi.gaia

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.google.common.base.Charsets
import com.google.common.collect.ImmutableMap
import com.google.common.collect.ImmutableMultiset
import com.google.common.io.Files
import edu.isi.gaia.AIFUtils.markSystem
import edu.isi.nlp.files.FileUtils
import edu.isi.nlp.parameters.Parameters
import edu.isi.nlp.parameters.Parameters.loadSerifStyle
import edu.isi.nlp.symbols.Symbol
import mu.KLogging
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.SKOS
import org.slf4j.LoggerFactory
import java.io.File
import java.nio.charset.StandardCharsets.UTF_8
import java.nio.file.Path

/*
Converts ColdStart++ knowledge bases to the AIDA Interchange Format (AIF).

See main method for a description of the parameters expected.
 */


/**
 * Can convert a ColdStart++ KB to the AIDA Interchange Format (AIF).
 */
class ColdStart2AidaInterchangeConverter(
        val entityIriGenerator: IriGenerator = UuidIriGenerator(),
        val eventIriGenerator: IriGenerator = UuidIriGenerator(),
        val assertionIriGenerator: IriGenerator = UuidIriGenerator(),
        val stringIriGenerator: IriGenerator = UuidIriGenerator(),
        val clusterIriGenerator: IriGenerator = UuidIriGenerator(),
        val useClustersForCoref: Boolean = false,
        val restrictConfidencesToJustifications: Boolean = false,
        val defaultMentionConfidence: Double? = null,
        val ontologyMapping: OntologyMapping = ColdStartOntologyMapper()) {
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
            errorLogger: ErrorLogger) {
        return Conversion(system_uri, destinationModel, errorLogger).convert(cs_kb)
    }

    /**
     * Bundles together all the state for a conversion process.
     *
     * We use an inner class for this so that the converter object itself stays thread-safe.
     */
    private inner class Conversion(systemUri: String, val model: Model,
                                   val errorLogger: ErrorLogger) {
        // stores a mapping of ColdStart objects to their URIs in the interchange format
        val objectToUri = mutableMapOf<Any, Resource>()

        // this is the URI for the generating system
        val systemNode = AIFUtils.makeSystemWithURI(model, systemUri)

        // converts a ColdStart object to an RDF identifier (node in the RDF graph)
        // if this ColdStart node has been previously converted, we return the same RDF identifier
        fun toResource(node: Node): Resource {
            if (!objectToUri.containsKey(node)) {
                val rdfNode = when (node) {
                    is EntityNode -> AIFUtils.makeEntity(model,
                            entityIriGenerator.nextIri(), systemNode)
                    is EventNode -> AIFUtils.makeEvent(model,
                            eventIriGenerator.nextIri(), systemNode)
                    is StringNode -> model.createResource(stringIriGenerator.nextIri())
                }

                objectToUri.put(node, rdfNode)
            }
            return objectToUri.getValue(node)
        }

        // converts a ColdStart ontology type to a corresponding RDF identifier
        fun toRealisType(realis: Realis) = when (realis) {
            Realis.actual -> ColdStartOntologyMapper.ACTUAL
            Realis.generic -> ColdStartOntologyMapper.GENERIC
            Realis.other -> ColdStartOntologyMapper.OTHER
        }

        // below are the functions for translating each individual type of ColdStart assertion
        // into the appropriate RDF structures
        // each will return a boolean specifying whether or not the conversion was successful
        // except translateTypeAssertion, which returns the type asserted

        fun translateTypeAssertion(cs_assertion: TypeAssertion, confidence: Double?): Resource? {
            val ontologyType = when (cs_assertion.subject) {
                is EntityNode -> ontologyMapping.entityType(cs_assertion.type)
                is EventNode -> ontologyMapping.eventType(cs_assertion.type)
                is StringNode -> ontologyMapping.entityType(cs_assertion.type)
            }

            if (ontologyType != null) {
                val entityOrEvent = toResource(cs_assertion.subject)

                AIFUtils.markType(model, assertionIriGenerator.nextIri(), entityOrEvent,
                        ontologyType, systemNode, confidence)

                // when requested to use clusters, we need to generate a cluster for each entity
                // and event the first time it is mentioned
                // we trigger this on the type assertion because such an assertion should occur
                // exactly once on each coreffable object
                val isCoreffableObject = cs_assertion.subject is EntityNode
                        || cs_assertion.subject is EventNode
                if (useClustersForCoref && isCoreffableObject) {
                    AIFUtils.makeClusterWithPrototype(model, clusterIriGenerator.nextIri(),
                            entityOrEvent, systemNode)
                }

                return ontologyType
            } else {
                return null
            }
        }

        fun translateMention(cs_assertion: MentionAssertion, confidence: Double,
                             objectToCanonicalMentions: Map<Node, Provenance>,
                             objectToType: Map<Node, Resource>): Boolean {
            val entityResource = toResource(cs_assertion.subject)
            // if this is a canonical mention, then we need to make a skos:preferredLabel triple
            if (cs_assertion.mentionType == CANONICAL_MENTION) {
                val entityType = (objectToType[cs_assertion.subject]
                        ?: throw RuntimeException("Entity $entityResource lacks a type"))
                when {
                    ontologyMapping.typeAllowedToHaveAName(entityType) -> AidaAnnotationOntology.NAME_PROPERTY
                    ontologyMapping.typeAllowedToHaveTextValue(entityType) -> AidaAnnotationOntology.TEXT_VALUE_PROPERTY
                    ontologyMapping.typeAllowedToHaveNumericValue(entityType) -> AidaAnnotationOntology.NUMERIC_VALUE_PROPERTY
                    else -> null
                }?.let { property -> entityResource.addProperty(property, model.createTypedLiteral(cs_assertion.string)) }
            } else if (cs_assertion.justifications
                    == objectToCanonicalMentions.getValue(cs_assertion.subject)) {
                // this mention assertion just duplicates a canonical mention assertion
                // so we won't add a duplicate RDF structure for it
                // TODO: this will block the justification type being marked for such
                // justifications.  On the other hand, this is probably ok, because knowing it
                // is the canonical mention is more informative. Issue #46
                return false
            }
            AIFUtils.markSystem(entityResource, systemNode)

            // for the moment, we don't do anything with realis because it is unclear how it
            // fits into AIDA
//            if (cs_assertion is EventMentionAssertion) {
//                val realisNode = model.createResource()
//                entityResource.addProperty(AidaAnnotationOntology.REALIS, realisNode)
//                realisNode.addProperty(AidaAnnotationOntology.REALIS_VALUE,
//                        toRealisType(cs_assertion.realis))
//                associate_with_system(realisNode)
//            }

            registerJustifications(entityResource, cs_assertion.justifications,
                    cs_assertion.string, confidence,
                    justificationType = cs_assertion.mentionType.name)

            return true
        }

        fun registerJustifications(resource: Resource,
                                   provenance: Provenance, string: String? = null,
                                   confidence: Double, justificationType: String? = null) {
            for ((start, end_inclusive) in provenance.predicate_justifications) {
                val justification = AIFUtils.markTextJustification(model, resource, provenance.docID,
                        start, end_inclusive, systemNode, confidence)

                if (string != null) {
                    justification.addProperty(SKOS.prefLabel,
                            model.createTypedLiteral(string))
                }

                // we record whether a justification is nominal, pronominal, etc.
                // as system-private data
                if (justificationType != null) {
                    val privateData = model.createResource()
                    privateData.addProperty(RDF.type, AidaAnnotationOntology.PRIVATE_DATA_CLASS)
                    justification.addProperty(AidaAnnotationOntology.PRIVATE_DATA_PROPERTY,
                            privateData)
                    markSystem(privateData, systemNode)
                    privateData.addProperty(AidaAnnotationOntology.JSON_CONTENT_PROPERTY,
                            "{ \"justificationType\" : \"$justificationType\"}")
                }
            }
        }

        fun translateLink(cs_assertion: LinkAssertion, confidence: Double): Boolean {
            AIFUtils.linkToExternalKB(model, toResource(cs_assertion.subject),
                    cs_assertion.global_id, systemNode, confidence)
            return true
        }

        fun translateRelation(csAssertion: RelationAssertion, confidence: Double): Boolean {
            val relationTypeIri = ontologyMapping.relationType(csAssertion.relationType)
            if (relationTypeIri != null) {
                val subjectRole = ontologyMapping.eventArgumentType(csAssertion.relationType + "_subject")
                        ?: RDF.subject
                val objectRole = ontologyMapping.eventArgumentType(csAssertion.relationType + "_object") ?: RDF.subject
                AIFUtils.makeRelationInEventForm(model, assertionIriGenerator.nextIri(), relationTypeIri,
                        subjectRole, toResource(csAssertion.subject), objectRole, toResource(csAssertion.obj),
                        assertionIriGenerator.nextIri(), systemNode, confidence)
                return true
            } else {
                return false
            }
        }

        fun translateSentiment(csAssertion: SentimentAssertion, confidence: Double): Boolean {


            fun toSentimentType(sentiment: String) = when (sentiment.toLowerCase()) {
                "likes" -> ColdStartOntologyMapper.LIKES
                "dislikes" -> ColdStartOntologyMapper.DISLIKES
                else -> throw RuntimeException("Unknown sentiment $sentiment")
            }

            val sentimentHolder = toResource(csAssertion.subject)
            val thingSentimentIsAbout = toResource(csAssertion.obj)

            val relation = AIFUtils.makeRelation(model, assertionIriGenerator.nextIri(), systemNode)
            AIFUtils.markType(model, assertionIriGenerator.nextIri(), relation, toSentimentType(csAssertion.sentiment),
                    systemNode, confidence)

            val subjectRole = ontologyMapping.eventArgumentType(csAssertion.sentiment + "_holder") ?: RDF.subject
            val subjectArg = AIFUtils.markAsArgument(model, relation, subjectRole, sentimentHolder, systemNode, confidence)
            registerJustifications(subjectArg, csAssertion.justifications, null,
                    confidence)

            val objectRole = ontologyMapping.eventArgumentType(csAssertion.sentiment + "_isAbout") ?: RDF.subject
            val objectArg = AIFUtils.markAsArgument(model, relation, objectRole, thingSentimentIsAbout, systemNode, confidence)
            registerJustifications(objectArg, csAssertion.justifications, null,
                    confidence)
            return true
        }

        fun translateEventArgument(csAssertion: EventArgumentAssertion, confidence: Double)
                : Boolean {
            val event = toResource(csAssertion.subject)
            val filler = toResource(csAssertion.argument)

            val argTypeIri = ontologyMapping.eventArgumentType(csAssertion.argument_role)
            if (argTypeIri != null) {
                val argAssertion = AIFUtils.markAsArgument(model, event,
                        argTypeIri, filler, systemNode, confidence)

                registerJustifications(argAssertion, csAssertion.justifications, null, confidence)
                return true
            } else {
                return false
            }
        }

        fun convert(csKB: ColdStartKB) {
            val progressInterval = 100000
            val numAssertions = csKB.allAssertions.size
            // when an item has a canonical mention assertion in the ColdStart database, it will
            // also have a corresponding regular mention assertion.  We don't want duplicate
            // RDF structures of these in our output, though, so we track what the canonical
            // mention is for each object so we can block translation of the duplicate mention
            // assertion in `translateMention`
            val objectToCanonicalMentions = csKB.allAssertions.filterIsInstance<MentionAssertion>()
                    .filter { it.mentionType == CANONICAL_MENTION }
                    .map { it.subject to it.justifications }.toMap()


            // factors out common behavior from two passes we will make over the KB below
            fun translateAssertions(assertions: Collection<Assertion>, assertionGroupName: String,
                                    untranslatedFunction: (Assertion) -> Unit,
                                    translatorFunction: (Assertion, Double?) -> Boolean) {
                for ((assertionNum, assertion) in assertions.withIndex()) {
                    // note not all ColdStart assertions have confidences
                    val confidenceFromColdStart = csKB.assertionsToConfidence[assertion]
                    val confidence = if (confidenceFromColdStart != null) {
                        confidenceFromColdStart
                    } else if (assertion is MentionAssertion && defaultMentionConfidence != null) {
                        defaultMentionConfidence
                    } else if (assertion is TypeAssertion) {
                        null // type assertions are permitted to lack confidence
                    } else {
                        throw RuntimeException("Assertion $assertion lacks confidence")
                    }

                    val translated = translatorFunction(assertion, confidence)

                    if (!translated) {
                        untranslatedFunction(assertion)
                    }

                    if (assertionNum > 0 && assertionNum % progressInterval == 0) {
                        logger.info { "Processed $assertionNum / $numAssertions $assertionGroupName assertions" }
                    }
                }
            }

            // we process type assertions first because we want to omit all other things which reference
            // objects with types outside the target domain ontology
            val (typeAssertions, otherAssertions) = csKB.allAssertions.partition { it is TypeAssertion }
            val objectToType = mutableMapOf<Node, Resource>()

            translateAssertions(typeAssertions, "type",
                    untranslatedFunction = { errorLogger.observeOutOfDomainType((it as TypeAssertion).type) })
            { assertion: Assertion, confidence: Double? ->
                if (assertion is TypeAssertion) {
                    val type = translateTypeAssertion(assertion, confidence)
                    if (type != null) {
                        objectToType[assertion.subject] = type
                        true
                    } else {
                        false
                    }
                } else {
                    throw RuntimeException("Can't happen")
                }
            }

            val (assertionsInvolvingTypedObjects, assertionsInvolvingUntypedObjects) =
                    otherAssertions.partition { it.nodes().all { it in objectToType } }

            assertionsInvolvingUntypedObjects.forEach { errorLogger.observeUntranslatableBecauseNodeOutsideOntology(it) }

            translateAssertions(assertionsInvolvingTypedObjects, "non-type",
                    errorLogger::observeUntranslatableForOtherReason) { assertion: Assertion, confidence: Double? ->
                when (assertion) {
                    is TypeAssertion -> throw RuntimeException("Can't happen")
                    is MentionAssertion -> translateMention(assertion, confidence!!,
                            objectToCanonicalMentions, objectToType)
                    is LinkAssertion -> translateLink(assertion, confidence!!)
                    is SentimentAssertion -> translateSentiment(assertion, confidence!!)
                    is RelationAssertion -> translateRelation(assertion, confidence!!)
                    is EventArgumentAssertion -> translateEventArgument(assertion, confidence!!)
                    else -> false
                }
            }

            AIFUtils.addStandardNamespaces(model)
            model.setNsPrefix("domainOntology", ontologyMapping.NAMESPACE)
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
    val systemUri = params.getString("systemURI")

    // we can run in two modes
    // in one mode, we output one big RDF file for the whole KB. If we do that, we need to
    // use an uglier output format (Blocked Turtle) or serializing the output takes forever
    // (see  https://jena.apache.org/documentation/io/rdf-output.html )
    // if working file by file, we write a separate RDF file for each source document in the CS KB
    // and can afford to use pretty-printed Turtle on the output
    // the mode difference also affects whether we expect the output path to be a file or a
    // directory
    val mode = params.getEnum("mode", Mode::class.java)!!

    val outputPath: Path

    // don't log too much Jena-internal stuff
    (org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).level = Level.INFO

    val ontologyMappings: Map<String, OntologyMapping> = listOf(
            "coldstart" to ColdStartOntologyMapper(),
            "seedling" to SeedlingOntology(),
            "rpi_seedling" to RPISeedlingOntologyMapper()
    ).toMap()

    val ontologyName: String = params.getOptionalString("ontology").or("coldstart")
    val ontologyMapping = ontologyMappings[ontologyName] ?: ColdStartOntologyMapper()

    // this will track which assertions could not be converted. This is useful for debugging.
    // we pull this out into its own object instead of doing it inside the conversion method
    // so that it will aggregate results across doc-level KB conversions when running in shatter
    // mode
    val errorLogger = DefaultErrorLogger()

    // the ColdStart format already includes cross-document coref information. If this is true,
    // we throw this information away during conversion
    val breakCrossDocCoref = when(mode) {
        Mode.FULL -> params.getOptionalBoolean("breakCrossDocCoref").or(false)
        Mode.SHATTER -> true
    }

    val coldstartKB = ColdStartKBLoader(
            // we need to let the ColdStart KB loader itself know we are shattering by document so it
            // knows to eliminate the cross-document coreference links which have already been added by
            // the ColdStart system
            breakCrossDocCoref = breakCrossDocCoref,
            ontologyMapping = ontologyMapping).load(inputKBFile)

    when(mode) {
        // converting entire ColdStart KB at once
        Mode.FULL -> {
            val converter = configureConverterFromParams(params,
                    // should confidences be attach directly to the entity or assertion they pertain
                    // to or to the justifications thereof? When working at the single-document
                    // level (TA1 -> TA2), it should be the latter; in TA2 and TA3, the former.
                    restrictConfidencesToJustifications=params.getOptionalBoolean(
                    "restrictConfidencesToJustifications").or(false),
                    ontologyMapping = ontologyMapping)
            outputPath = params.getCreatableFile("outputAIFFile").toPath()
            convertColdStartAsSingleKB(converter, systemUri, coldstartKB, outputPath,
                    errorLogger = errorLogger)
        }
        // converting document-by-document
        Mode.SHATTER -> {
            val converter = configureConverterFromParams(params,
                    restrictConfidencesToJustifications=true,
                    ontologyMapping = ontologyMapping)
            outputPath = params.getCreatableDirectory("outputAIFDirectory").toPath()
            convertColdStartShatteringByDocument(converter, systemUri, coldstartKB, outputPath,
                    errorLogger = errorLogger)
        }
    }

    ColdStart2AidaInterchangeConverter.logger.info(errorLogger.errorsMessage())
}

fun convertColdStartAsSingleKB(converter: ColdStart2AidaInterchangeConverter,
                               systemUri: String,
                               coldstartKB: ColdStartKB, outputPath: Path,
                               errorLogger: ErrorLogger) {
    AIFUtils.workWithBigModel {
        val model = it
        converter.coldstartToAidaInterchange(systemUri, coldstartKB, model, errorLogger = errorLogger)
        outputPath.toFile().bufferedWriter(UTF_8).use {
            // deprecation is OK because Guava guarantees the writer handles the charset properly
            @Suppress("DEPRECATION")
            RDFDataMgr.write(it, model, RDFFormat.NTRIPLES)
        }
    }
}

fun convertColdStartShatteringByDocument(
        converter: ColdStart2AidaInterchangeConverter, systemUri: String, coldstartKB: ColdStartKB,
        outputPath: Path, errorLogger: ErrorLogger) {
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
        val model = ModelFactory.createDefaultModel()
        converter.coldstartToAidaInterchange(systemUri, perDocKB, model, errorLogger = errorLogger)
        outputFile.toFile().bufferedWriter(UTF_8).use {
            // deprecation is OK because Guava guarantees the writer handles the charset properly
            @Suppress("DEPRECATION")
            RDFDataMgr.write(it, model, RDFFormat.TURTLE_PRETTY)
        }

        outputFileList.add(outputFile.toFile())
        outputFileMap.put(Symbol.from(docId), outputFile.toFile())

        if (docsProcessed % 1000 == 0) {
            LoggerFactory.getLogger("main").info(
                    "Translated $docsProcessed / ${kbsByDocument.size}")
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



private fun configureConverterFromParams(
        params: Parameters,
        restrictConfidencesToJustifications: Boolean,
        ontologyMapping: OntologyMapping): ColdStart2AidaInterchangeConverter {
    val baseUri =  params.getString("baseURI")!!


    return ColdStart2AidaInterchangeConverter(
            entityIriGenerator = UuidIriGenerator("$baseUri/entities"),
            eventIriGenerator = UuidIriGenerator("$baseUri/events"),
            assertionIriGenerator = UuidIriGenerator("$baseUri/assertions"),
            stringIriGenerator = UuidIriGenerator("$baseUri/strings"),
            clusterIriGenerator = UuidIriGenerator("$baseUri/clusters"),
            ontologyMapping = ontologyMapping,
            // In AIDA, there can be uncertainty about coreference, so the AIDA interchange format
            // provides a means of representing coreference uncertainty.  In ColdStart, however,
            // coref decisions were always "hard". We provide the user with the option of whether
            // to encode these coref decisions in the way they would be encoded in AIDA if there
            // were any uncertainty so that users can test these data structures
            useClustersForCoref = params.getOptionalBoolean("useClustersForCoref")
                    .or(false),
            restrictConfidencesToJustifications = restrictConfidencesToJustifications,
            // support TA1s which erroneously leave confidences off some of their mentions
            defaultMentionConfidence = params.getOptionalPositiveDouble(
                    "defaultMentionConfidence").orNull())
}

interface ErrorLogger {
    fun observeUntranslatableBecauseNodeOutsideOntology(assertion: Assertion)
    fun observeUntranslatableForOtherReason(assertion: Assertion)
    fun observeOutOfDomainType(type: String)
    fun errorsMessage(): String
}

/**
 * Allows logging of the types of all ColdStart assertions which could not be translated
 */
class DefaultErrorLogger : ErrorLogger {
    // we use ImmutableMultiSet.builders to maintain determinism
    private val untranslatableAssertionTypesB = ImmutableMultiset.builder<Class<Assertion>>()
    private val untranslatableObjectTypesB = ImmutableMultiset.builder<String>()
    private val hasOutOfDomainNode = ImmutableMultiset.builder<Class<Assertion>>()
    private val outOfDomainTypes = ImmutableMultiset.builder<String>()

    override fun observeUntranslatableForOtherReason(assertion: Assertion) {
        untranslatableAssertionTypesB.add(assertion.javaClass)
        if (assertion is TypeAssertion) {
            untranslatableObjectTypesB.add(assertion.type)
        }
    }

    override fun observeUntranslatableBecauseNodeOutsideOntology(assertion: Assertion) {
        hasOutOfDomainNode.add(assertion.javaClass)
    }

    override fun observeOutOfDomainType(type: String) {
        outOfDomainTypes.add(type)
    }

    override fun errorsMessage(): String {
        return "The following types were not found in the target ontology: ${outOfDomainTypes.build()}\n" +
                "The following assertions were omitted because they involved out-of-ontology nodes:" +
                " ${hasOutOfDomainNode.build()}\n" +
                "The following ColdStart assertions could not be translated for other " +
                "reasons: ${untranslatableAssertionTypesB.build()}\n" +
                "The following object types could not be translated for other reasons:" +
                " ${untranslatableObjectTypesB.build()}\n"
    }
}

