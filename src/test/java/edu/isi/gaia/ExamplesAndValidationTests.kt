package edu.isi.gaia

import edu.isi.gaia.AIFUtils.makeEntity
import edu.isi.gaia.AIFUtils.markConfidence
import edu.isi.gaia.AIFUtils.markTextJustification
import edu.isi.gaia.AIFUtils.markType
import org.apache.jena.rdf.model.ModelFactory
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
            val system = AIFUtils.makeSystemWithURI(model, "http://www.test.edu/testSystem")

            // it doesn't matter what URI we give entities, events, etc. so long as they are
            // unique
            val entity = makeEntity(model, "http://www.test.edu/entities/1", system)

            // in order to allow uncertainty about the type of an entity, we don't mark an
            // entity's type directly on the entity, but rather make a separate assertion for it
            // its URI doesn't matter either
            val typeAssertion = markType(model, "http://www.test.org/assertions/1",
                    entity, AidaDomainOntology.PERSON, system)

            // the justification provides the evidence for our claim about the entity's type
            // we attach this justification to both the type assertion and the entity object
            // itself, since it provides evidence both for the entity's existence and its type.
            val justification = markTextJustification(model, setOf(entity, typeAssertion),
                    "NYT_ENG_201181231", 42, 14, system)

            // in TA1 -> TA2 communications, we attach confidences at the level of justifications
            markConfidence(model, justification, 0.973, system)

            assertTrue { validator.validateKB(model) }
        }
    }

    @Nested
    internal inner class ValidationFailures
}
