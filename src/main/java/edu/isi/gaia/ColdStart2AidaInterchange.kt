package edu.isi.gaia

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.google.common.base.Charsets
import com.google.common.collect.*
import com.google.common.io.Files
import com.ncc.aif.AIFUtils
import com.ncc.aif.AIFUtils.markSystem
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
        val relationIriGenerator: IriGenerator = UuidIriGenerator(),
        val stringIriGenerator: IriGenerator = UuidIriGenerator(),
        val clusterIriGenerator: IriGenerator = UuidIriGenerator(),
        val useClustersForCoref: Boolean = false,
        val restrictConfidencesToJustifications: Boolean = false,
        val defaultMentionConfidence: Double? = null,
        val ontologyMapping: OntologyMapping,
        // this is not allowed in the real eval and should be done only for debugging
        val includePrefLabelsOnJustifications: Boolean = false) {
    companion object : KLogging()

    init {
        if (includePrefLabelsOnJustifications) {
            logger.warn { "Including prefLabels. This should be done for debugging only and" +
                    " should be turned off for the evaluation" }
        }
    }

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
     * The type of some object together with the AIF assertion assigning that type.
     *
     * NIST requires justifications on all type assertions. This a convenience class to help us track these
     * type assertions for objects so that we can attach justifications to them in [Conversion.registerJustifications].
     */
    private data class TypeAndTypeAssertion(val type: Resource, val typeAssertion: Resource)

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
                    is RelationNode -> AIFUtils.makeRelation(model,
                            relationIriGenerator.nextIri(), systemNode)
                    is StringNode -> model.createResource(stringIriGenerator.nextIri())
                }

                objectToUri.put(node, rdfNode)
            }
            return objectToUri.getValue(node)
        }

        // below are the functions for translating each individual type of ColdStart assertion
        // into the appropriate RDF structures
        // each will return a boolean specifying whether or not the conversion was successful
        // except translateTypeAssertion, which returns the type asserted

        fun translateTypeAssertion(cs_assertion: TypeAssertion, confidence: Double?): TypeAndTypeAssertion? {
            val ontologyType = when (cs_assertion.subject) {
                is EntityNode -> ontologyMapping.entityType(cs_assertion.type)
                is EventNode -> ontologyMapping.eventType(cs_assertion.type)
                is RelationNode -> ontologyMapping.relationType(cs_assertion.type)
                is StringNode -> ontologyMapping.entityType(cs_assertion.type)
            }

            if (ontologyType != null) {
                val entityEventOrRelation = toResource(cs_assertion.subject)

                val typeAssertion = AIFUtils.markType(model, assertionIriGenerator.nextIri(), entityEventOrRelation,
                        ontologyType, systemNode, confidence)

                // when requested to use clusters, we need to generate a cluster for each entity
                // and event the first time it is mentioned
                // we trigger this on the type assertion because such an assertion should occur
                // exactly once on each coreffable object
                val isCoreffableObject = cs_assertion.subject is EntityNode
                        || cs_assertion.subject is EventNode
                if (useClustersForCoref && isCoreffableObject) {
                    AIFUtils.makeClusterWithPrototype(model, clusterIriGenerator.nextIri(),
                            entityEventOrRelation, systemNode)
                }

                return TypeAndTypeAssertion(type=ontologyType, typeAssertion = typeAssertion)
            } else {
                return null
            }
        }

        fun translateMention(
                cs_assertion: MentionAssertion, confidence: Double,
                objectToCanonicalMentions: Map<Node, Provenance>,
                objectToType: Map<Node, TypeAndTypeAssertion>,
                /**
                 * Accumulates all names associated with entities of a type capable of bearing a
                 * `hasName` property. At the end of translation this will be used to add the
                 * `hasName` properties. We accumualte this way instead of adding the properties
                 * immediately to avoid duplication.
                 */
                nameableEntitiesToNames: ImmutableSetMultimap.Builder<Resource, String>,
                provenanceToMentionType: ImmutableSetMultimap<Provenance, MentionType>): Boolean {
            val entityResource = toResource(cs_assertion.subject)
            // if this is a canonical mention, then we need to make a skos:preferredLabel triple
            val (entityType, typeAssertion) = (objectToType[cs_assertion.subject]
                    ?: throw RuntimeException("Entity $entityResource lacks a type"))

            if (cs_assertion.mentionType == CANONICAL_MENTION) {
                when {
                    // we deliberately don't translate for things which can have names because
                    // the canonical string is not necessarily a name (e.g. if a name for the entity
                    // is never mentioned in a document. Instead, we gather the names from
                    // ColdStart "mention" mentions (which are in fact name mentions) below
                    ontologyMapping.typeAllowedToHaveTextValue(entityType) -> AidaAnnotationOntology.TEXT_VALUE_PROPERTY
                    ontologyMapping.typeAllowedToHaveNumericValue(entityType) -> AidaAnnotationOntology.NUMERIC_VALUE_PROPERTY
                    else -> null
                }?.let { property -> entityResource.addProperty(property,
                        model.createTypedLiteral(cs_assertion.string)) }
            } else {
                if (cs_assertion.mentionType == NAME_MENTION
                        && ontologyMapping.typeAllowedToHaveAName(entityType)) {
                    // record name in order to create hasName property later
                    nameableEntitiesToNames.put(entityResource, cs_assertion.string)
                }

                if (cs_assertion.justifications
                        == objectToCanonicalMentions.getValue(cs_assertion.subject)) {
                    // this mention assertion just duplicates a canonical mention assertion
                    // so we won't add a duplicate RDF structure for it
                    // TODO: this will block the justification type being marked for such
                    // justifications.  On the other hand, this is probably ok, because knowing it
                    // is the canonical mention is more informative. Issue #46
                    // we return true here even though the statement wasn't translated because it is just a duplicate
                    // of an already translated assertion, so we don't need to warn about it
                    return true
                }
            }
            AIFUtils.markSystem(entityResource, systemNode)

            // it is possible that we have seen the same span of text assigned multiple
            // mention types (in particular, for all canonical mentions, there will be another
            // ColdStart assertion with a different mention type). What we want to record is
            // the single "best" mention type, where names are best and pronouns are worst.
            val justificationType = provenanceToMentionType[cs_assertion.justifications]
                    .asSequence()
                    .maxWith(Ordering.explicit(listOf(CANONICAL_MENTION, PRONOMINAL_MENTION,
                            NOMINAL_MENTION, NORMALIZED_MENTION, NAME_MENTION)))!!

            if (justificationType == CANONICAL_MENTION) {
                logger.warn {
                    "Got a canonical_mention as only mention type for $cs_assertion, " +
                            "but there should always be another CS assertion with a more concrete" +
                            "mention type"
                }
            }

            registerJustifications(entityResource, cs_assertion.justifications,
                    typeAssertion, cs_assertion.string, confidence,
                    justificationType = justificationType.name)

            return true
        }

        fun registerJustifications(resource: Resource,
                                   provenance: Provenance,
                                   /**
                                    * AIF Assertion assigning type to [resource]
                                    */
                                   typeAssertion: Resource? = null,
                                   string: String? = null,
                                   confidence: Double, justificationType: String? = null) {
            for ((start, end_inclusive) in provenance.predicate_justifications) {
                val justification = AIFUtils.markTextJustification(model, resource, provenance.docID,
                        start, end_inclusive, systemNode, confidence)
                // NIST wants justifications on type assertions
                if (typeAssertion != null) {
                    AIFUtils.markJustification(ImmutableList.of(typeAssertion), justification)
                }

                if (includePrefLabelsOnJustifications && string != null) {
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

        fun translateEventArgument(csAssertion: EventArgumentAssertion, confidence: Double)
                : Boolean {
            val event = toResource(csAssertion.subject)
            val filler = toResource(csAssertion.argument)

            val argTypeIri = ontologyMapping.eventArgumentType(csAssertion.argument_role)
            return if (argTypeIri != null) {
                val argAssertion = AIFUtils.markAsArgument(model, event,
                        argTypeIri, filler, systemNode, confidence)

                registerJustifications(argAssertion, csAssertion.justifications, null,
                        confidence = confidence)
                true
            } else {
                errorLogger.observeOutOfDomainType(csAssertion.argument_role)
                false
            }
        }

        fun translateRelationArgument(csAssertion: RelationArgumentAssertion, confidence: Double)
                : Boolean {
            val relation = toResource(csAssertion.subject)
            val filler = toResource(csAssertion.argument)

            val argTypeIri = ontologyMapping.relationArgumentType(csAssertion.argument_role)
            return if (argTypeIri != null) {
                val argAssertion = AIFUtils.markAsArgument(model, relation,
                        argTypeIri, filler, systemNode, confidence)

                registerJustifications(argAssertion, csAssertion.justifications, null,
                        confidence = confidence)
                true
            } else {
                errorLogger.observeOutOfDomainType(csAssertion.argument_role)
                false
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
            val provenanceToMentionType = csKB.allAssertions
                    .asSequence()
                    .filterIsInstance<MentionAssertion>()
                    .map { it.justifications to it.mentionType }
                    .toImmutableSetMultimap()


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
            val objectToType = mutableMapOf<Node, TypeAndTypeAssertion>()

            translateAssertions(typeAssertions, "type",
                    untranslatedFunction = {
                        errorLogger.observeOutOfDomainType((it as TypeAssertion).type)
                    })
            { assertion: Assertion, confidence: Double? ->
                if (assertion is TypeAssertion) {
                    val typeAndAssertion = translateTypeAssertion(assertion, confidence)
                    if (typeAndAssertion != null) {
                        objectToType[assertion.subject] = typeAndAssertion
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

            // we use an ImmutableSetMultimap here for deterministic iteration ordering
            val nameableEntitiesToNames =
                    ImmutableSetMultimap.builder<Resource, String>()
            translateAssertions(assertionsInvolvingTypedObjects, "non-type",
                    errorLogger::observeUntranslatableForOtherReason) { assertion: Assertion, confidence: Double? ->
                when (assertion) {
                    is TypeAssertion -> throw RuntimeException("Can't happen")
                    is MentionAssertion -> translateMention(assertion, confidence!!,
                            objectToCanonicalMentions, objectToType, nameableEntitiesToNames,
                            provenanceToMentionType)
                    is LinkAssertion -> translateLink(assertion, confidence!!)
                    is EventArgumentAssertion -> translateEventArgument(assertion, confidence!!)
                    is RelationArgumentAssertion -> translateRelationArgument(assertion, confidence!!)
                    else -> false
                }
            }

            // add hasName properties where applicable. We accumulate them all and do this at the
            // end to avoid duplicates
            nameableEntitiesToNames.build().asMap().forEach { entity, names ->
                names.forEach { name ->
                    entity.addProperty(AidaAnnotationOntology.NAME_PROPERTY,
                            model.createTypedLiteral(name))
                }
            }

            // make Turtle output prettier by setting some namespace prefixes
            AIFUtils.addStandardNamespaces(model)

            ontologyMapping.prefixes().forEach { ontologyIri, prefix ->
                model.setNsPrefix(prefix, ontologyIri.uri)
            }
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

    val ontologyMapping = M18OntologyMapping.fromOntologyFiles(
            entityOntologyFile = params.getExistingFile("entityOntology"),
            eventOntologyFile = params.getExistingFile("eventOntology"),
            relationOntologyFile = params.getExistingFile("relationOntology"))

    // this will track which assertions could not be converted. This is useful for debugging.
    // we pull this out into its own object instead of doing it inside the conversion method
    // so that it will aggregate results across doc-level KB conversions when running in shatter
    // mode
    val errorLogger = DefaultErrorLogger(
            allowedOutOfDomainTypes = params.getOptionalExistingFile("out_of_domain_type_whitelist").orNull()
                    ?.readLines()?.toSet() ?: setOf())

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
            breakCrossDocCoref = breakCrossDocCoref).load(inputKBFile)

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

    ColdStart2AidaInterchangeConverter.logger.info { "Writing output to $outputPath" }
    ColdStart2AidaInterchangeConverter.logger.info(errorLogger.errorsMessage())
    if (!errorLogger.runWasSuccess()) {
        ColdStart2AidaInterchangeConverter.logger.error { "Run failed with errors" }
        System.exit(1)
    }
}

fun convertColdStartAsSingleKB(converter: ColdStart2AidaInterchangeConverter,
                               systemUri: String,
                               coldstartLoadingResult: ColdStartKBLoader.LoadingResult,
                               outputPath: Path,
                               errorLogger: ErrorLogger) {
    val coldstartKB = coldstartLoadingResult.kb
    ISIAIFUtils.workWithBigModel {
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
        converter: ColdStart2AidaInterchangeConverter, systemUri: String,
        coldstartLoadingResult: ColdStartKBLoader.LoadingResult,
        outputPath: Path, errorLogger: ErrorLogger) {

    outputPath.toFile().mkdirs()
    // for the convenience of programs processing the output, we provide
    // a list of all doc-level turtle files generated and a file mapping from doc IDs
    // to these files
    val outputFileList = mutableListOf<File>()
    val outputFileMap = ImmutableMap.builder<Symbol, File>()

    var docsProcessed = 0
    val kbsByDocument = coldstartLoadingResult.shatterByDocument()
    for ((docId, perDocKB) in kbsByDocument) {
        docsProcessed += 1
        val outputFile = outputPath.resolve("$docId.ttl")
        val model = ModelFactory.createDefaultModel()
        converter.coldstartToAidaInterchange(systemUri, perDocKB.kb, model, errorLogger = errorLogger)
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
            relationIriGenerator = UuidIriGenerator("$baseUri/relations"),
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
                    "defaultMentionConfidence").orNull(),
            includePrefLabelsOnJustifications = params.getOptionalBoolean("includeDebugPrefLabels").orNull() ?: false
    )
}

interface ErrorLogger {
    fun observeUntranslatableBecauseNodeOutsideOntology(assertion: Assertion)
    fun observeUntranslatableForOtherReason(assertion: Assertion)
    fun observeOutOfDomainType(type: String)
    fun errorsMessage(): String
    fun runWasSuccess(): Boolean
}

/**
 * Allows logging of the types of all ColdStart assertions which could not be translated
 */
class DefaultErrorLogger(
        // if we encounter an out-of-domain type we usually crash, but user can explicitly
        // white-list certain types
        private val allowedOutOfDomainTypes: Set<String> = setOf()) : ErrorLogger {
    // we use ImmutableMultiSet.builders to maintain determinism
    private val untranslatableAssertionTypesB = ImmutableMultiset.builder<Class<Assertion>>()
    private val untranslatableObjectTypesB = ImmutableMultiset.builder<String>()
    private val hasOutOfDomainNode = ImmutableMultiset.builder<Class<Assertion>>()
    private val outOfDomainTypes = ImmutableMultiset.builder<String>()

    override fun observeUntranslatableForOtherReason(assertion: Assertion) {
        untranslatableAssertionTypesB.add(assertion.javaClass)
    }

    override fun observeUntranslatableBecauseNodeOutsideOntology(assertion: Assertion) {
        hasOutOfDomainNode.add(assertion.javaClass)
    }

    override fun observeOutOfDomainType(type: String) {
        if (!allowedOutOfDomainTypes.contains(type)) {
            outOfDomainTypes.add(type)
        }
    }

    override fun errorsMessage(): String {
        return "The following types were not found in the target ontology: ${outOfDomainTypes.build()}\n" +
                "The following assertions were omitted because they involved out-of-ontology nodes:" +
                " ${hasOutOfDomainNode.build()}\n" +
                "The following ColdStart assertions could not be translated: ${untranslatableAssertionTypesB.build()}\n" +
                "The following object types could not be translated: ${untranslatableObjectTypesB.build()}\n"
    }

    override fun runWasSuccess(): Boolean {
        if (outOfDomainTypes.build().isNotEmpty()) {
            ColdStart2AidaInterchangeConverter.logger.error { "Treating out-of-domain types as fatal errors" }
        }

        return outOfDomainTypes.build().isEmpty()
    }
}

fun <K, V> Sequence<Pair<K, V>>.toImmutableSetMultimap(): ImmutableSetMultimap<K, V> {
    return ImmutableSetMultimap.copyOf(this.map { java.util.AbstractMap.SimpleEntry(it.first, it.second) }.asIterable())
}
