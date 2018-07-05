package edu.isi.gaia

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
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
import java.io.IOException

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

            val inputTabularFile = Files.asCharSource(
                    params.getExistingFile("input_tabular_file"), Charsets.UTF_8)
            val systemIri = (inputTabularFile.readFirstLine()?.trim()
                    ?: throw IOException("Input file appears to be empty"))
            if (systemIri.endsWith('/')) {
                throw RuntimeException("System IRI may not end with / but got $systemIri")
            }

            log.logger.info { "Read header line as system URI $systemIri" }

            val converter = createForSystemIri(systemIri)

            // drop(1) to skip system IRI header
            val imageMentionsByDocId = inputTabularFile.readLines().drop(1)
                    .map { parseTabularLineToMention(it) }
                    .associate { it.docId to it }

            val outputFileList = mutableListOf<File>()
            val outputFileMap = ImmutableMap.builder<Symbol, File>()

            for ((docId, imageMentionsForDoc) in imageMentionsByDocId) {
                val outputFile = outputDir.resolve("$docId.ttl").toFile()
                outputFileList.add(outputFile)
                outputFileMap.put(Symbol.from(docId), outputFile)

                val docModel = ModelFactory.createDefaultModel()
                AIFUtils.addStandardNamespaces(docModel)
                val system = AIFUtils.makeSystemWithURI(docModel, systemIri)
                converter.convertImageMentionToRdf(docModel, imageMentionsForDoc, system)
                Files.asCharSink(outputFile, Charsets.UTF_8)
                        .openBufferedStream()
                        .use {
                            // deprecation is OK because Guava guarantees the writer handles the
                            // charset properly
                            @Suppress("DEPRECATION")
                            RDFDataMgr.write(it, docModel, RDFFormat.TURTLE_PRETTY)
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

        fun createForSystemIri(systemIri: String): ImagesToAIF {
            return ImagesToAIF(UuidIriGenerator("$systemIri/entities"),
                    UuidIriGenerator("$systemIri/assertions"))
        }

        private const val DOC_ID = 0
        private const val OBJECT_TYPE = 1
        private const val ONTOLOGY_TYPE = 2
        private const val UPPER_LEFT_X = 3
        private const val UPPER_LEFT_Y = 4
        private const val LOWER_RIGHT_X = 5
        private const val LOWER_RIGHT_Y = 6
        private const val CONFIDENCE = 7
        private const val NUM_FIELDS = CONFIDENCE + 1

        private fun parseTabularLineToMention(line: String): ImageMention {
            val parts = line.trim().split('\t')
            if (parts.size != NUM_FIELDS) {
                throw RuntimeException("Cannot parse line $line: expected $NUM_FIELDS but " +
                        "got ${parts.size}")
            }
            val boundingBox = AIFUtils.BoundingBox(
                    AIFUtils.Point(parts[UPPER_LEFT_X].toInt(), parts[UPPER_LEFT_Y].toInt()),
                    AIFUtils.Point(parts[LOWER_RIGHT_X].toInt(), parts[LOWER_RIGHT_Y].toInt()))
            return ImageMention(docId = parts[DOC_ID],
                    objectType = ObjectType.valueOf(parts[OBJECT_TYPE]),
                    ontologyType = parts[ONTOLOGY_TYPE], boundingBox = boundingBox,
                    confidence = parts[CONFIDENCE].toDouble())
        }
    }

    private enum class ObjectType { ENTITY, EVENT }

    private data class ImageMention(val docId: String, val objectType: ObjectType,
                                    val ontologyType: String, val boundingBox: AIFUtils.BoundingBox,
                                    val confidence: Double)


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
    }
}
