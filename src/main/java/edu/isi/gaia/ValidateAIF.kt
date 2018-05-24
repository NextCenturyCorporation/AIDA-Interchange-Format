package edu.isi.gaia

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import com.google.common.base.Charsets
import com.google.common.collect.ImmutableList
import com.google.common.io.CharSource
import com.google.common.io.Files
import com.google.common.io.Resources
import edu.isi.nlp.parameters.Parameters
import mu.KLogging
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.shared.JenaException
import org.apache.jena.util.FileUtils
import org.apache.jena.vocabulary.RDF
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

class ValidateAIF(private val domainModel: Model) {
    private val shaclModel = Resources.asCharSource(
            Resources.getResource("edu/isi/gaia/aida_ontology.shacl"), Charsets.UTF_8)
            .openBufferedStream().use {
                loadModel(it)
            }

    companion object {
        private val log = KLogging()

        // data will always be interpreted in the context of these two ontology files
        private val PRELOAD_ONTOLOGIES = listOf("edu/isi/gaia/interchange-ontology.ttl",
                "edu/isi/gaia/aida-domain-common.ttl")
                .map { Resources.getResource(it) }
                .map { Resources.asCharSource(it, Charsets.UTF_8) }

        @JvmStatic
        fun createForDomainOntologySource(domainOntologySource: CharSource): ValidateAIF {
            val ret = ModelFactory.createOntologyModel()

            // ensure what file name an RDF syntax error occurs in is printed, which
            // doesn't happen by default
            fun loadOntologyWithFriendlyError(ontologySource: CharSource) = try {
                ontologySource.openBufferedStream().use {
                    ret.read(it, "urn:x-base", FileUtils.langTurtle)
                }
            } catch (jenaException: JenaException) {
                throw RuntimeException("While parsing domain ontology $ontologySource:",
                        jenaException)
            }

            // load all the hard-coded ontologies and also the user-specified domain ontology
            PRELOAD_ONTOLOGIES
                    .union(listOf(domainOntologySource))
                    .forEach({ loadOntologyWithFriendlyError(it) })

            return ValidateAIF(ret)
        }

        @JvmStatic
        fun main(args: Array<String>) {
            if (args.size != 1) {
                print("Usage: validateAIF paramFile\n\tSee repo README for details.")
                System.exit(1)
            }

            // prevent too much logging from obscuring the actual problems
            (org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).level = Level.INFO

            val params = Parameters.loadSerifStyle(File(args[0]))
            val domainOntologyFile = params.getExistingFile("domainOntology")

            // this is an RDF model which uses SHACL to encode constraints on the AIF
            val validator = ValidateAIF.createForDomainOntologySource(
                    Files.asCharSource(domainOntologyFile, Charsets.UTF_8))

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
                allValid = validator.validateKB(dataToBeValidated) || allValid
            }

            if (!allValid) {
                // failure code if anything fails to validate
                System.exit(1)
            }
        }

    }

    /**
     * Returns whether or not the KB is valid
     */
    fun validateKB(dataToBeValidated: Model): Boolean {
        // we unify the given KB with the background and domain KBs before validation
        // this is required so that constraints like "the object of a type must be an
        // entity type" will know what types are in fact entity types
        val unionModel = ModelFactory.createUnion(domainModel, dataToBeValidated)

        // we short-circuit because earlier validation failures may make later
        // validation attempts misleading nonsense
        return /*ensureEveryNamedNodeHasARdfType(dataToBeValidated)
                &&*/ validateAgainstShacl(unionModel)
                && ensureConfidencesInZeroOne(unionModel)
                && ensureEveryEntityAndEventHasAType(unionModel)
    }

    private val ENSURE_EVERY_NAMED_NODE_HAS_A_TYPE_SPARQL_QUERY = """
        PREFIX rdf: <${RDF.uri}>
        PREFIX aida: <${AidaAnnotationOntology.NAMESPACE}>


        SELECT ?namedNode
        WHERE {
           ?namedNode ?foo ?bar ;
           FILTER (isIRI(?namedNode)  ) .
           MINUS { ?nameNode rdf:type ?anything }
        }
        """

    /**
     * Ensure that every named node has an RDF type specified.
     *
     * Note that "type" here is RDF type, not domain ontology type.
     *
     * The motivation here is to keep users from e.g. making an entity, forgetting to mark it
     * as an entity, and being confused when it appears downstream that they aren't producing
     * entities.
     */
    private fun ensureEveryNamedNodeHasARdfType(dataToBeValidated: Model): Boolean {
        // TODO: this is not working yet - I need to fiddle with the SPARQL query
        val query = QueryFactory.create(ENSURE_EVERY_NAMED_NODE_HAS_A_TYPE_SPARQL_QUERY)
        val queryExecution = QueryExecutionFactory.create(query, dataToBeValidated)
        val results = queryExecution.execSelect()

        var valid = true
        while (results.hasNext()) {
            val match = results.nextSolution()
            val typelessNamedNode = match.getResource("namedNode")

            System.err.println("Node $typelessNamedNode lacks an rdf:type property")
            valid = false
        }
        return valid
    }

    /**
     * Validates against the SHACL file to ensure that resources have the required properties
     * (and in some cases, only the required properties) of the proper types.  Returns true if
     * validation passes.
     */
    private fun validateAgainstShacl(dataToBeValidated: Model): Boolean {
        // do SHACL validation
        val report = ValidationUtil.validateModel(dataToBeValidated, shaclModel, true)
        val valid = report.getRequiredProperty(
                shaclModel.createProperty("http://www.w3.org/ns/shacl#conforms")).boolean
        if (!valid) {
            report.model.write(System.err, FileUtils.langTurtle)
        }
        return valid
    }

    private fun ensureConfidencesInZeroOne(dataToBeValidated: Model): Boolean {
        val badVals = mutableSetOf<Double>()
        dataToBeValidated.listObjectsOfProperty(AidaAnnotationOntology.CONFIDENCE_VALUE).forEach {
            // we can assume all objects of confidenceValue are double-valued literals
            // or else we would have failed SHACL validation
            val floatVal = it.asLiteral().double
            if (floatVal < 0 || floatVal > 1.0) {
                badVals.add(floatVal)
            }
        }
        if (!badVals.isEmpty()) {
            // TODO: provide more context for this error
            System.err.println("The following confidence values outside the range [0, 1.0] were " +
                    "found: $badVals")
        }
        return badVals.isEmpty()
    }

    // used by ensureEveryEntityAndEventHasAType below
    private val ENSURE_TYPE_SPARQL_QUERY = """
        PREFIX rdf: <${RDF.uri}>
        PREFIX aida: <${AidaAnnotationOntology.NAMESPACE}>

        SELECT ?entityOrEvent
        WHERE {
           {?entityOrEvent a aida:Entity} UNION  {?entityOrEvent a aida:Event}
           FILTER NOT EXISTS {
           ?typeAssertion a rdf:Statement .
           ?typeAssertion rdf:predicate rdf:type .
           ?typeAssertion rdf:subject ?entityOrEvent .
            }
        }
        """

    private fun ensureEveryEntityAndEventHasAType(dataToBeValidated: Model): Boolean {
        // it is okay if there are multiple type assertions (in case of uncertainty)
        // but there has to be at least one
        // TODO: we would like to make sure if there are multiple, then they must be in some sort
        // of mutual exclusion relationship. This may be complicated and slow, however, so we
        // don't do it yet
        val query = QueryFactory.create(ENSURE_TYPE_SPARQL_QUERY)
        val queryExecution = QueryExecutionFactory.create(query, dataToBeValidated)
        val results = queryExecution.execSelect()

        var valid = true
        while (results.hasNext()) {
            val match = results.nextSolution()
            val typelessEntityOrEvent = match.getResource("entityOrEvent")

            // an entity is permitted to lack a type if it is a non-prototype member of a cluster
            // this could be the case when the entity arises from coreference resolution where
            // the referents are different types
            val isNonPrototypeMemberOfCluster = dataToBeValidated.subjectsWithProperty(
                    AidaAnnotationOntology.CLUSTER_MEMBER, typelessEntityOrEvent).any()
            if (!isNonPrototypeMemberOfCluster) {
                System.err.println("Entity or event ${typelessEntityOrEvent.uri} has no type " +
                        "assertion")
                valid = false
            }
        }
        return valid
    }

}