package edu.isi.gaia

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.google.common.base.Charsets
import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterators
import com.google.common.io.Files
import com.google.common.io.Resources
import edu.isi.nlp.parameters.Parameters
import mu.KLogging
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Property
import org.apache.jena.sparql.function.library.leviathan.root
import org.apache.jena.util.FileUtils
import org.slf4j.LoggerFactory
import org.topbraid.shacl.validation.ValidationUtil
import java.io.File
import java.io.Reader



fun loadModel(reader: Reader) : Model {
    val ret = ModelFactory.createOntologyModel()

    ret.read(reader, "urn:x-base", FileUtils.langTurtle)
    return ret
}

val LACKS_TYPES_QUERY = """
    PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
    SELECT *
    WHERE {
        OPTIONAL {?node rdf:type ?type}
        FILTER(!bound(?type))
    }
    """

object ValidateAIF {

    val log = KLogging()

    @JvmStatic
    fun main(args: Array<String>) {
        if (args.size != 1) {
            print("Usage: validateGraph paramFile")
            System.exit(1)
        }

        // prevent too much logging from obscuring the actual problems
        (org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).level = Level.INFO

        val params = Parameters.loadSerifStyle(File(args[0]))

        // this is an RDF model which uses SHACL to encode constraints on the AIF
        val shapeModel = Resources.asCharSource(
                Resources.getResource("edu/isi/gaia/aida_ontology.shacl"), Charsets.UTF_8)
                .openBufferedStream().use {
            loadModel(it)
        }

        val fileList = params.getOptionalExistingFile("kbsToValidate")
        val filesToValidate = if (fileList.isPresent) {
            edu.isi.nlp.files.FileUtils.loadFileList(fileList.get())
        } else {
            ImmutableList.of(params.getExistingFile("kbToValidate"))
        }

        var allValid = true
        for (fileToValidate in filesToValidate) {
            log.logger.info { "Validating $fileToValidate" }
            val dataToBeValidated = Files.asCharSource(fileToValidate, Charsets.UTF_8)
                    .openBufferedStream().use { loadModel(it) }
            allValid = validateKB(shapeModel, dataToBeValidated) || allValid
        }

        if (!allValid) {
            // failure code if anything fails to validate
            System.exit(1)
        }
    }

    /**
     * Returns whether or not the KB is valid
     */
    private fun validateKB(shapeModel: Model, dataToBeValidated: Model) : Boolean {
        var valid = true
        // we short-circuit because earlier validation failures may make later
        // validation attempts misleading nonsense
        valid = valid && validateAgainstShacl(dataToBeValidated, shapeModel)
        return valid
    }

    /**
     * Validates against the SHACL file to ensure that resources have the required properties
     * (and in some cases, only the required properties) of the proper types.  Returns true if
     * validation passes.
     */
    private fun validateAgainstShacl(dataToBeValidated: Model, shapeModel: Model) : Boolean {
        // do SHACL validation
        val report = ValidationUtil.validateModel(dataToBeValidated, shapeModel, true)
        val valid = report.getRequiredProperty(
                shapeModel.createProperty("http://www.w3.org/ns/shacl#conforms")).boolean
        if (!valid) {
            report.model.write(System.out, FileUtils.langTurtle)
        }
        return valid
    }
}