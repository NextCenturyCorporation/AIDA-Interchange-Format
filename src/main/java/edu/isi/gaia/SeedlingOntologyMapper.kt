package edu.isi.gaia

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.ResourceFactory
import org.apache.jena.vocabulary.OWL
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.RDFS
import java.io.File

class M18OntologyMapping(private val entityModel: Model,
                         private val eventModel: Model,
                         private val relationModel: Model,
                         private val prefixes: Map<Resource, String>) : OntologyMapping {
    companion object {
        fun fromOntologyFiles(entityOntologyFile: File, eventOntologyFile: File,
                     relationOntologyFile: File): M18OntologyMapping {
            val ontologyNameToModel = mapOf("entityOnt" to entityOntologyFile,
                    "eventOnt" to eventOntologyFile,
                    "relationOnt" to relationOntologyFile)
                    .mapValues {
                        val model = ModelFactory.createDefaultModel()
                        model.read(it.value.absolutePath, "TURTLE")
                        model
                    }

            val prefixToIri = ontologyNameToModel.mapValues {
                it.value.subjectsWithProperty(RDF.type, OWL.Ontology).first()
            }.entries.associate { (k, v) -> v to k }

            return M18OntologyMapping(ontologyNameToModel.getValue("entityOny"),
                    ontologyNameToModel.getValue("eventOnt"),
                    ontologyNameToModel.getValue("relationOnt"),
                    prefixToIri)
        }
    }

    override fun prefixes() = prefixes

    override fun entityType(ontology_type: String) = resourceInOntology(entityModel, ontology_type)

    override fun relationType(relationName: String) = resourceInOntology(relationModel, relationName)

    override fun eventType(eventName: String) = resourceInOntology(eventModel, eventName)

    override fun eventArgumentType(argName: String) = resourceInOntology(eventModel, argName)

    override fun relationArgumentType(relationArgumentName: String) = resourceInOntology(
            relationModel, relationArgumentName)

    override fun typeAllowedToHaveAName(type: Resource) =
            entityModel.contains(type, RDFS.subClassOf, AidaDomainOntologiesCommon.CanHaveName)


    override fun typeAllowedToHaveTextValue(type: Resource) =
            entityModel.contains(type, RDFS.subClassOf, AidaDomainOntologiesCommon.CanHaveTextValue)

    override fun typeAllowedToHaveNumericValue(type: Resource) =
            entityModel.contains(type, RDFS.subClassOf, AidaDomainOntologiesCommon.CanHaveNumericValue)

    private fun resourceInOntology(model: Model, uri: String): Resource? {
        val ret = ResourceFactory.createResource(uri)!!
        return if (model.containsResource(ret)) {
            ret
        } else {
            null
        }
    }
}

