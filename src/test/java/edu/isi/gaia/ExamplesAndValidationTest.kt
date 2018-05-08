package edu.isi.gaia

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import edu.isi.gaia.AIFUtils.makeEntity
import edu.isi.gaia.AIFUtils.markAsMutuallyExclusive
import edu.isi.gaia.AIFUtils.markConfidence
import edu.isi.gaia.AIFUtils.markTextJustification
import edu.isi.gaia.AIFUtils.markType
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
import org.apache.jena.vocabulary.RDF
import org.apache.jena.vocabulary.SKOS
import org.apache.jena.vocabulary.XSD
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import kotlin.test.assertTrue

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal class ExamplesAndValidationTest {
    init {
        // prevent too much logging from obscuring the Turtle examples which will be printed
        (org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME) as Logger).level = Level.INFO
    }

    private val validator = ValidateAIF()

    // we dump the test name and the model in Turtle format so that whenever the user
    // runs the tests, they will also get the examples
    private fun dumpAndAssertValid(model: Model, testName: String) {
        System.out.println(testName)
        RDFDataMgr.write(System.out, model, RDFFormat.TURTLE_PRETTY)
        assertTrue { validator.validateKB(model) }
    }

    private fun createModel(): Model {
        val model = ModelFactory.createDefaultModel()
        model.setNsPrefix("rdf", RDF.uri)
        model.setNsPrefix("xsd", XSD.getURI())
        model.setNsPrefix("aida", AidaAnnotationOntology._namespace)
        model.setNsPrefix("aidaProgramOntology", AidaDomainOntology._namespace)
        model.setNsPrefix("skos", SKOS.uri)
        return model
    }

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

            dumpAndAssertValid(model, "create an entity of type person with textual " +
                    "justification and confidence")
        }

        @Test
        fun `create an entity with uncertainty about its type`() {
            val model = createModel()

            // every AIF needs an object for the system responsible for creating it
            val system = AIFUtils.makeSystemWithURI(model, "http://www.test.edu/testSystem")

            val entity = makeEntity(model, "http://www.test.edu/entities/1", system)
            val entityIsAPerson = markType(model, "http://www.test.org/assertions/1",
                    entity, AidaDomainOntology.PERSON, system)
            val entityIsAnOrganization = markType(model, "http://www.test.org/assertions/2",
                    entity, AidaDomainOntology.ORGANIZATION, system)

            val justificationIsAPerson = markTextJustification(model, setOf(entity, entityIsAPerson),
                    "NYT_ENG_201181231", 42, 14, system)
            markConfidence(model, justificationIsAPerson, 0.6, system)

            val justificationIsAnOrg = markTextJustification(model,
                    setOf(entity, entityIsAnOrganization),
                    "NYT_ENG_201181231", 343, 367, system)
            markConfidence(model, justificationIsAnOrg, 0.3, system)

            markAsMutuallyExclusive(model, mapOf(setOf(entityIsAPerson) to 0.5,
                    setOf(entityIsAnOrganization) to 0.2), system)

            dumpAndAssertValid(model, "create an entity with uncertainty about its type")
        }

        @Test
        fun `create a relation between two entities where there is uncertainty about identity of one argument`() {

        }

        @Test
        fun `create an event`() {

        }

        @Test
        fun `label sentiment regarding an entity`() {

        }

        @Test
        fun `use sub-graph confidences to show mutually exclusive linked event argument options`() {

        }
    }

    @Nested
    internal inner class ValidationFailures {

    }
}
