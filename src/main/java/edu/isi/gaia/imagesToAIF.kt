package edu.isi.gaia

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.fasterxml.jackson.databind.ObjectMapper
import com.google.common.collect.ImmutableMap
import com.google.common.io.Files
import edu.isi.nlp.files.FileUtils
import edu.isi.nlp.parameters.Parameters
import edu.isi.nlp.symbols.Symbol
import mu.KLogging
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Resource
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
import java.io.File
import kotlin.coroutines.experimental.buildSequence

/**
 * Convert simple entity & event mentions from images to AIF format.
 *
 * This tool can convert simple entity and event mentions from images to the AIF format.
 * You provide it with a tab-separated file describing the entity/event mentions found in images
 * and it will produce AIF in Turtle format, one file per document ID seen in the input.
 *
 * The input file should have the IRI to be assigned for this system configuration as the first
 * line.  This can be an arbitrary string as long as it does not end in `/`.
 *
 * Each following line should describe one image entity or event mention using the following
 * tab-separated fields:
 *
 *   * document ID
 *   * either ENTITY or EVENT
 *   * the ontology type for the entity or event. This should be a full IRI!
 *   * the x coordinate of the upper-left corner of the bounding box
 *   * the y coordinate of the upper-left corner of the bounding box
 *   * the x coordinate of the lower-right corner of the bounding box
 *   * the y coordinate of the lower-right corner of the bounding box
 *   * the confidence of the mention (in `(0, 1.0]`).
 *
 *  No field may contain a tab.
 *
 *  Entities and events can optionally have associated dense vectors.  To attach such a dense vector, follow the line
 *  for the entity or event with a line with the following tab-separated fields:
 *
 *  VECTOR
 *  vector type IRI
 *  vector components as floats
 *
 * For example,
 *
 * VECTOR www.usc.edu/vectors/personVectors 0.754 0.3984 1.344
 *
 * You may follow an entity or event line with multiple vectors lines to attach multiple vectors to the object.
 * Vector support is provisional pending finalization of how vectors will be stored in AIF ( issue #21 ).
 * All lines related to the same document must be contiguous.
 *
 *   This script will cannot encode any information about coreference relations between mentions
 *   or other more complex forms of uncertainty.
 *
 *  The input to the script is a single parameter file with keys and values separated by `:`. The
 *  required parameters are:
 *
 *  * `input_tabular_file:` path of input file described above
 *  * `output_directory:` directory AIF Turtle files will be written, one file per doc mentioned in
 *         the input
 *
 *  ImagesToAIF will write two additional files to the output directory to make downstream
 *  processing easier: `images_to_aif.list.txt`, listing the Turtle files generated, and
 *  `images.aif.map.txt`, providing a tab-separated mapping from document IDs to Turtle files.
 */
class ImagesToAIF(private val entityUriGenerator: IriGenerator,
                  private val typeAssertionUriGenerator: IriGenerator) {
    companion object {
        private val log = KLogging()

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.size != 1) {
                print("""usage: imageToAIF param_file\n
                    |    input_tabular_file: tab-separated file of image entity and event mentions
                    |    output_directory: directory to output AIF, one file per input doc ID
                    |    See class comment on edu.isi.gaia.ImagesToAIF for details.
                """.trimMargin())
            }

            // prevent too much logging from confusing people
            (org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).level = Level.INFO

            run(Parameters.loadSerifStyle(File(args[0])))
        }

        @JvmStatic
        fun run(params: Parameters) {
            val outputDir = params.getCreatableDirectory("output_directory").toPath()

            val inputTabularFile = params.getExistingFile("input_tabular_file")
            val systemIri = inputTabularFile.useLines { it.first() }.trim()
            if (systemIri.endsWith('/')) {
                throw RuntimeException("System IRI may not end with / but got $systemIri")
            }

            log.logger.info { "Read header line as system URI $systemIri" }


            val converter = createForSystemIri(systemIri)

            val outputFileList = mutableListOf<File>()
            val outputFileMap = ImmutableMap.builder<Symbol, File>()

            // we compute this as a lazy sequence because the input file with vectors on an eval corpus could
            // be quite large
            inputTabularFile.useLines {
                val imageMentionsByDocument = ImageMention.parseToDocumentMentions(it)


                for (imageMentionsForDoc in imageMentionsByDocument) {
                    // sanity check - this should be guaranteed by our parsing code
                    require(imageMentionsForDoc.isNotEmpty())
                    val docId = imageMentionsForDoc[0].docId
                    val outputFile = outputDir.resolve("$docId.ttl").toFile()
                    outputFileList.add(outputFile)
                    outputFileMap.put(Symbol.from(docId), outputFile)

                    val docModel = ModelFactory.createDefaultModel()
                    AIFUtils.addStandardNamespaces(docModel)

                    val system = AIFUtils.makeSystemWithURI(docModel, systemIri)

                    for (imageMention in imageMentionsForDoc) {
                        converter.convertImageMentionToRdf(docModel, imageMention, system)
                    }
                    Files.asCharSink(outputFile, Charsets.UTF_8)
                            .openBufferedStream()
                            .use {
                                // deprecation is OK because Guava guarantees the writer handles the
                                // charset properly
                                @Suppress("DEPRECATION")
                                RDFDataMgr.write(it, docModel, RDFFormat.TURTLE_PRETTY)
                            }
                }
            }

            val listFile = outputDir.resolve("images_to_aif.list.txt").toFile()
            FileUtils.writeFileList(outputFileList,
                    Files.asCharSink(listFile, com.google.common.base.Charsets.UTF_8))
            val mapFile = outputDir.resolve("images_to_aif.map.txt").toFile()
            FileUtils.writeSymbolToFileMap(outputFileMap.build(),
                    Files.asCharSink(mapFile, com.google.common.base.Charsets.UTF_8))
            log.logger.info("Wrote list and map of AIF files to $listFile " +
                    "and $mapFile respectively")
        }

        private fun createForSystemIri(systemIri: String): ImagesToAIF {
            return ImagesToAIF(UuidIriGenerator("$systemIri/entities"),
                    UuidIriGenerator("$systemIri/assertions"))
        }

        private data class ImageMention(val docId: String, val objectType: ObjectType,
                                        val ontologyType: String, val boundingBox: AIFUtils.BoundingBox,
                                        val confidence: Double, val vectors: List<ImageVector>) {
            companion object {
                private const val DOC_ID = 0
                private const val OBJECT_TYPE = 1
                private const val ONTOLOGY_TYPE = 2
                private const val UPPER_LEFT_X = 3
                private const val UPPER_LEFT_Y = 4
                private const val LOWER_RIGHT_X = 5
                private const val LOWER_RIGHT_Y = 6
                private const val CONFIDENCE = 7
                private const val NUM_FIELDS = CONFIDENCE + 1

                private val OBJECT_TYPE_NAMES = ObjectType.values().map { it.name }.toSet()

                fun splitOnTabs(line: String) = line.split("\t")
                fun isEntityOrEventLine(lineParts: List<String>) = lineParts[OBJECT_TYPE] in OBJECT_TYPE_NAMES

                class EnsureAllMentionsForSameDocAreContiguous : (List<ImageMention>) -> List<ImageMention> {
                    val docIdsSeen = mutableSetOf<String>()
                    override fun invoke(x: List<ImageMention>): List<ImageMention> {
                        require(x.isNotEmpty())
                        val docId = x[0].docId
                        if (docId in docIdsSeen) {
                            throw RuntimeException("Lines for document $docId are discontinuous")
                        }
                        docIdsSeen.add(docId)
                        return x
                    }
                }

                fun parseToDocumentMentions(lines: Sequence<String>): Sequence<List<ImageMention>> = lines.asSequence()
                        // drop(1) to skip system IRI header
                        .drop(1)
                        .map(::splitOnTabs)
                        // group entity or event lines with any vector lines which follow them
                        .split(::isEntityOrEventLine, keepBoundary = true)
                        // translate each group to an image mention with its associated vectors
                        .map(::parseLinesToMention)
                        // group all mentions from each document together for output
                        .chunkedBy(ImageMention::docId)
                        // this last step enforces the named constraint but doesn't change the stream elements
                        .map(EnsureAllMentionsForSameDocAreContiguous())


                /**
                 * Parse a group of lines to an image mention, where the first line describes the image mention itself
                 * (in the format given in the main class header) and the following lines describe attacked vectors.
                 */
                fun parseLinesToMention(linePartses: List<List<String>>): ImageMention {
                    require(linePartses.isNotEmpty()) { "Cannot parse empty set of lines to an image mention!" }

                    // the first line in any block defines the image mention itself
                    val objectLineParts = linePartses[0]

                    if (objectLineParts.size != NUM_FIELDS) {
                        throw RuntimeException("Cannot parse object line fields $objectLineParts: " +
                                "expected $NUM_FIELDS but got ${objectLineParts.size}")
                    }
                    val boundingBox = AIFUtils.BoundingBox(
                            AIFUtils.Point(objectLineParts[UPPER_LEFT_X].toInt(), objectLineParts[UPPER_LEFT_Y].toInt()),
                            AIFUtils.Point(objectLineParts[LOWER_RIGHT_X].toInt(), objectLineParts[LOWER_RIGHT_Y].toInt()))


                    return ImageMention(docId = objectLineParts[DOC_ID],
                            objectType = ObjectType.valueOf(objectLineParts[OBJECT_TYPE]),
                            ontologyType = objectLineParts[ONTOLOGY_TYPE], boundingBox = boundingBox,
                            confidence = objectLineParts[CONFIDENCE].toDouble(),
                            vectors = linePartses.drop(1).map(ImageVector.Companion::parseFromLine).toList())
                }
            }

            internal data class ImageVector(val type: String, val data: List<Double>) {
                companion object {
                    private const val VECTOR_FLAG = 0
                    private const val VECTOR_TYPE = 1

                    /**
                     * Parses a vector from a line of the format
                     *
                     * VECTOR vector_type 0.436 1.345 -445.0 ...
                     */
                    internal fun parseFromLine(vectorLineParts: List<String>): ImageVector {
                        // all vector lines must start with "VECTOR"
                        require(vectorLineParts[VECTOR_FLAG] == "VECTOR") {
                            "Vector line fields " +
                                    "do not start with VECTOR: $vectorLineParts"
                        }
                        return ImageVector(type = vectorLineParts[VECTOR_TYPE],
                                // skip first two fields for VECTOR flag and vector type
                                data = vectorLineParts.drop(2).map { it.toDouble() })
                    }
                }
            }

        }
    }

    private enum class ObjectType { ENTITY, EVENT }

    private val mapper = ObjectMapper()

    private fun markVectorInPrivateData(model: Model, entityOrEvent: Resource, vector: ImageMention.ImageVector,
                                        system: Resource) {
        val jsonMap = mapOf<Any, Any>("vector_type" to vector.type, "vector_data" to vector.data)
        AIFUtils.markPrivateData(model, entityOrEvent, mapper.writeValueAsString(jsonMap), system)
    }

    private fun convertImageMentionToRdf(model: Model, imageMention: ImageMention,
                                         system: Resource) {
        val entityOrEvent = when (imageMention.objectType) {
            ObjectType.ENTITY -> AIFUtils.makeEntity(model, entityUriGenerator.nextIri(), system)
            ObjectType.EVENT -> AIFUtils.makeEvent(model, entityUriGenerator.nextIri(), system)
        }

        val typeAssertion = AIFUtils.markType(model, typeAssertionUriGenerator.nextIri(),
                entityOrEvent, model.getResource(imageMention.ontologyType), system,
                imageMention.confidence)
        AIFUtils.markImageJustification(model, setOf(entityOrEvent, typeAssertion), imageMention.docId,
                imageMention.boundingBox, system, imageMention.confidence)
        for (vector in imageMention.vectors) {
            markVectorInPrivateData(model, entityOrEvent, vector, system)
        }
    }
}

/**
 * Splits a sequence into sub-lists, where boundaries are determined by the elements matching the given predicate.
 *
 * The elements triggering a boundary are included in the resulting sub-lists only if [keepBoundary] is true. If they
 * are included, they go with the "following" list, not the "preceding" one.  Empty sub-list are returned only if
 * [omitEmpty] is false.
 *
 */
fun <T> Sequence<T>.split(predicate: (T) -> Boolean, keepBoundary: Boolean = false,
                          omitEmpty: Boolean = true): Sequence<List<T>> {
    val itemSequence = this
    return buildSequence {
        var accumulator = mutableListOf<T>()

        for (item in itemSequence) {
            if (predicate(item)) {
                if (accumulator.isNotEmpty() || !omitEmpty) {
                    yield(accumulator)
                }

                // make a new list since the user could store a reference to the old one
                accumulator = mutableListOf()


                if (keepBoundary) {
                    accumulator.add(item)
                }
            } else {
                accumulator.add(item)
            }
        }

        // get the items between the last boundary and the end
        if (accumulator.isNotEmpty()) {
            yield(accumulator)
        }
    }
}

/**
 * Splits this sequence into a sequence of lists, where each list contains maximal runs of contiguous elements of
 * the original sequence with the same key value according to [keyFunction].
 *
 * If you call [flatten] on the result of
 * this, it is guaranteed to yield the same elements in the same order as the original sequence.
 *
 * Note that if there are discontinuous sub-sequences of items with the same key value, they will end up in different
 * lists.  If you wish these to be grouped together, consider [groupBy]].
 *
 * This is operation is intermediate and stateless.
 */
fun <T, K> Sequence<T>.chunkedBy(keyFunction: (T) -> K): Sequence<List<T>> {
    val itemSequence = this
    return buildSequence {
        var accumulator = mutableListOf<T>()
        var lastKey: K? = null

        for (item in itemSequence) {
            val itemKey = keyFunction(item)
            if (itemKey != lastKey) {
                if (lastKey != null) {
                    yield(accumulator)
                    // don't reuse the same list because the consumer may be storing a reference to it
                    accumulator = mutableListOf()
                }
            }
            accumulator.add(item)
            lastKey = itemKey
        }

        // the last chunk will never encounter a differing key, so we need to handle it specially
        if (accumulator.isNotEmpty()) {
            yield(accumulator)
        }
    }
}
