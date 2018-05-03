package edu.isi.gaia

import com.google.common.base.Charsets
import com.google.common.collect.Iterators
import com.google.common.io.Files
import com.google.common.io.Resources
import org.apache.commons.lang3.CharSet
import org.apache.jena.enhanced.BuiltinPersonalities.model
import org.apache.jena.query.QueryExecutionFactory
import org.apache.jena.query.QueryFactory
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import org.apache.jena.sparql.vocabulary.EARL.failed
import org.apache.jena.sparql.vocabulary.VocabTestQuery.query
import org.apache.jena.util.FileUtils
import org.apache.jena.vocabulary.RDF
import org.topbraid.shacl.validation.ValidationUtil
import org.topbraid.shacl.vocabulary.SH.nodes
import org.topbraid.shacl.vocabulary.SH.not
import java.io.File
import java.io.InputStream
import java.io.Reader
import java.nio.file.Paths
import kotlin.coroutines.experimental.EmptyCoroutineContext.plus

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

fun requireSingleContentGraph(model: Model) : Resource {
    val numContentGraphs = Iterators.size(
            model.listSubjectsWithProperty(RDF.type, AidaAnnotationOntology.KNOWLEDGE_GRAPH))
    if (numContentGraphs == 0) {
        print("Input lacks resource with type aida:KnowledgeGraph")
        System.exit(1)
    } else if (numContentGraphs > 1) {
        print("Input has multiple aida:KnowledgeGraphs")
        System.exit(1)
    }
    val rootGraph = Iterators.getOnlyElement(
            model.listSubjectsWithProperty(RDF.type, AidaAnnotationOntology.KNOWLEDGE_GRAPH))

    if (!rootGraph.isURIResource) {
        print("Root graph must have a URI")
        System.exit(1)
    }

    return rootGraph
}

val CONNECTED_COMPONENTS_QUERY = """
prefix dummy: <urn:dummy:>

construct { ?s ?p ?o }
where {<%s> (dummy:|!dummy:)* ?s . ?s ?p ?o .}
"""

fun requireConnectedToContentGraph(model: Model, rootGraphURI: String) {
    val query = CONNECTED_COMPONENTS_QUERY.format(rootGraphURI)
    val nodes = mutableSetOf<RDFNode>()
    QueryExecutionFactory.create(QueryFactory.create(query), model).use {
        for (statement in it.execConstruct().listStatements()) {
            nodes.add(statement.subject)
            nodes.add(statement.`object`)
        }
    }
    var foundDisconnectedSubject = false
    for (subject in model.listSubjects()) {
        if (!nodes.contains(subject)) {
            val name = subject.uri ?: "blank node"
            // TODO: hack
            if (!name.startsWith("http://www.w3.org")) {
                print("Subject $name is not connected to the root graph $rootGraphURI\n")
                foundDisconnectedSubject = true
            }
        }
    }
    if (foundDisconnectedSubject) {
        System.exit(1)
    }
}

fun main(args: Array<String>) {
    if (args.size != 1) {
        print("Usage: validateGraph graph-rdf-file")
        System.exit(1)
    }
    val shapeModel = Resources.asCharSource(
            Resources.getResource("edu/isi/gaia/aida_ontology.shacl"), Charsets.UTF_8)
            .openBufferedStream().use {
        loadModel(it)
    }

    val dataModel = Files.asCharSource(File(args[0]), Charsets.UTF_8).openBufferedStream().use {
        loadModel(it)
    }

    // do SHACL validation
    val report = ValidationUtil.validateModel(shapeModel, dataModel, true)
    report.model.write(System.out, FileUtils.langTurtle)

    val rootGraph = requireSingleContentGraph(dataModel)
    requireConnectedToContentGraph(dataModel, rootGraph.uri)

}
