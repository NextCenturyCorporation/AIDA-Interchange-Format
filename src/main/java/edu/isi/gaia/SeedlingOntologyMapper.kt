package edu.isi.gaia

import org.apache.commons.csv.CSVFormat
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.vocabulary.OWL
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS
import java.io.File
import java.util.*

class PassThroughOntologyMapper(private val model: Model,
                                override val NAMESPACE: String,
                                private val relationsToArguments: Map<Resource, Pair<Resource, Resource>>) : OntologyMapping {
    companion object {
        fun fromFile(ontologyFile: File, relationArgFile: File): PassThroughOntologyMapper {
            val model = ModelFactory.createDefaultModel()
            model.read(ontologyFile.absolutePath, "TURTLE")
            val ontologyObject = model.subjectsWithProperty(RDF.type, OWL.Ontology).first()

            val relationsToArgumentAssertion = relationArgFile.bufferedReader().use {
                val records = CSVFormat.EXCEL.withHeader("Relation Name", "Arg1", "Arg2")
                        .parse(it)
                records.asSequence().map {
                    ResourceFactory.createResource(it.get("Relation Name")!!) to
                            Pair(ResourceFactory.createResource(it.get("Arg1")!!),
                                    ResourceFactory.createResource(it.get("Arg2")!!))
                }.toMap()
            }

            return PassThroughOntologyMapper(model, ontologyObject.uri, relationsToArgumentAssertion)
        }
    }

    override fun entityShortNames() = setOf<String>()

    override fun entityType(ontology_type: String) = resourceInOntology(ontology_type)

    override fun relationType(relationName: String) = resourceInOntology(relationName)

    override fun eventType(eventName: String) = resourceInOntology(eventName)

    override fun eventArgumentType(argName: String) = resourceInOntology(argName)

    override fun typeAllowedToHaveAName(type: Resource) =
            model.contains(type, RDFS.subClassOf, AidaDomainOntologiesCommon.CanHaveName)


    override fun typeAllowedToHaveTextValue(type: Resource) =
            model.contains(type, RDFS.subClassOf, AidaDomainOntologiesCommon.CanHaveTextValue)

    override fun typeAllowedToHaveNumericValue(type: Resource) =
            model.contains(type, RDFS.subClassOf, AidaDomainOntologiesCommon.CanHaveNumericValue)

    private fun resourceInOntology(uri: String): Resource? {
        val ret = ResourceFactory.createResource(uri)!!
        return if (model.containsResource(ret)) {
            ret
        } else {
            null
        }
    }

    override fun relationArgumentTypes(relation: Resource): Pair<Resource, Resource> {
        return relationsToArguments[relation]
                ?: throw RuntimeException("Arguments not known for relation ${relation.uri}")
    }
}

