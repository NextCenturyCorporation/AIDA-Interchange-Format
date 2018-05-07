package edu.isi.gaia

import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.vocabulary.RDF
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ExamplesAndValidationTests {
    private val validator = ValidateAIF()

    private fun createModel() = ModelFactory.createDefaultModel()

    @Nested
    internal inner class TA1Examples {
        @Test
        fun `create an entity of type person with textual justification and confidence`() {
            val model = createModel()

            // every AIF needs an object for the system responsible for creating it
            val system = model.createResource("http://www.test.edu/testSystem")
            system.addProperty(RDF.type, AidaAnnotationOntology.SYSTEM_CLASS)

            // it doesn't matter what URI we give entities, events, etc. so long as they are
            // unique
            val entity = model.createResource("http://www.test.org/entities/1")
            entity.addProperty(RDF.type, AidaAnnotationOntology.ENTITY_CLASS)

            // in order to allow uncertainty about the type of an entity, we don't mark an
            // entity's type directly on the entity, but rather make a separate assertion for it
            // its URI doesn't matter either
            val typeAssertion = model.createResource("http://www.test.org/assertions/1")
            typeAssertion.addProperty(RDF.type, RDF.Statement)
            typeAssertion.addProperty(RDF.subject, entity)
            typeAssertion.addProperty(RDF.predicate, RDF.type)
            typeAssertion.addProperty(RDF.`object`, AidaDomainOntology.PERSON)

            // the justification provides the evidence for our claim about the entity's type
            val justification = model.createResource()
            // there can also be video justifications, audio justifications, etc.
            justification.addProperty(RDF.type, AidaAnnotationOntology.TEXT_JUSTIFICATION_CLASS)
            // the document ID for the justifying source document
            justification.addProperty(AidaAnnotationOntology.SOURCE,
                    model.createTypedLiteral("NYT_ENG_201181231"))
            justification.addProperty(AidaAnnotationOntology.START_OFFSET,
                    model.createTypedLiteral(14))
            justification.addProperty(AidaAnnotationOntology.END_OFFSET_INCLUSIVE,
                    model.createTypedLiteral(42))
            // we attach this justification to both the type assertion and the entity object
            // itself, since it provides evidence both for the entity's existence and its type.
            entity.addProperty(AidaAnnotationOntology.JUSTIFIED_BY, justification)
            typeAssertion.addProperty(AidaAnnotationOntology.JUSTIFIED_BY, justification)

            // in TA1 -> TA2 communications, we attach confidences at the level of justifications
            // TODO: should we enforce confidences from the same system are the same?
            val confidenceBlankNode = model.createResource()
            confidenceBlankNode.addProperty(RDF.type, AidaAnnotationOntology.CONFIDENCE_CLASS)
            confidenceBlankNode.addProperty(AidaAnnotationOntology.CONFIDENCE_VALUE,
                    model.createTypedLiteral(0.974))
            justification.addProperty(AidaAnnotationOntology.CONFIDENCE, confidenceBlankNode)

            assertTrue { validator.validateKB(model) }
        }
    }

    @Nested
    internal inner class ValidationFailures
}
