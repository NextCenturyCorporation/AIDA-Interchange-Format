package edu.isi.gaia

import com.google.common.base.Charsets
import com.google.common.collect.ImmutableList
import com.google.common.collect.Iterators
import com.google.common.io.Files
import com.google.common.io.Resources
import edu.isi.nlp.parameters.Parameters
import mu.KLogging
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.sparql.function.library.leviathan.log
import org.apache.jena.util.FileUtils
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

        for (fileToValidate in filesToValidate) {
            log.logger.info { "Validating $fileToValidate" }
            val dataToBeValidated = Files.asCharSource(fileToValidate, Charsets.UTF_8)
                    .openBufferedStream().use { loadModel(it) }
            validateKB(shapeModel, dataToBeValidated)
        }

        // TODO: return non-zero exit code on failure
    }

    private fun validateKB(shapeModel: Model, dataToBeValidated: Model) {
        // do SHACL validation
        val report = ValidationUtil.validateModel(dataToBeValidated, shapeModel, true)
        report.model.write(System.out, FileUtils.langTurtle)

    }
}