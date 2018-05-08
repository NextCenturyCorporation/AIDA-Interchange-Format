package edu.isi.gaia;

import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

@TestInstance(Lifecycle.PER_CLASS)
public class ExamplesAndValidationTest {

  @BeforeAll
  static void declutterLogging() {
    // prevent too much logging from obscuring the Turtle examples which will be printed
    ((Logger)org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
  }

  private final ValidateAIF validator = new ValidateAIF();

  @Test
  void createAnEntityOfTypePersonWithTextualJustificationAndConfidence() {
    final Model model = createModel();

    // every AIF needs an object for the system responsible for creating it
    final Resource system = AIFUtils.makeSystemWithURI(model,
        "http://www.test.edu/testSystem");

    // it doesn't matter what URI we give entities, events, etc. so long as they are
    // unique
    final Resource entity = AIFUtils.makeEntity(model, "http://www.test.edu/entities/1",
        system);

    // in order to allow uncertainty about the type of an entity, we don't mark an
    // entity's type directly on the entity, but rather make a separate assertion for it
    // its URI doesn't matter either
    final Resource typeAssertion = AIFUtils.markType(model, "http://www.test.org/assertions/1",
        entity, AidaDomainOntology.PERSON, system);

    // the justification provides the evidence for our claim about the entity's type
    // we attach this justification to both the type assertion and the entity object
    // itself, since it provides evidence both for the entity's existence and its type.
    final Resource justification = AIFUtils.markTextJustification(model,
        ImmutableSet.of(entity, typeAssertion), "NYT_ENG_201181231",
        42, 14, system);

    // in TA1 -> TA2 communications, we attach confidences at the level of justifications
    AIFUtils.markConfidence(model, justification, 0.973, system);

    dumpAndAssertValid(model, "create an entity of type person with textual " +
        "justification and confidence");
  }

  @Test
  void createAnEntityWithUncertaintyAboutItsType() {
    final Model model = createModel();

    // every AIF needs an object for the system responsible for creating it
    final Resource system = AIFUtils.makeSystemWithURI(model, "http://www.test.edu/testSystem");

    final Resource entity = AIFUtils.makeEntity(model, "http://www.test.edu/entities/1", system);
    final Resource entityIsAPerson = AIFUtils.markType(model, "http://www.test.org/assertions/1",
        entity, AidaDomainOntology.PERSON, system);
    final Resource entityIsAnOrganization = AIFUtils.markType(model, "http://www.test.org/assertions/2",
        entity, AidaDomainOntology.ORGANIZATION, system);

    final Resource justificationIsAPerson = AIFUtils.markTextJustification(model,
        ImmutableSet.of(entity, entityIsAPerson), "NYT_ENG_201181231", 42, 14, system);
    AIFUtils.markConfidence(model, justificationIsAPerson, 0.6, system);

    final Resource justificationIsAnOrg = AIFUtils.markTextJustification(model,
        ImmutableSet.of(entity, entityIsAnOrganization),
        "NYT_ENG_201181231", 343, 367, system);
    AIFUtils.markConfidence(model, justificationIsAnOrg, 0.3, system);

    AIFUtils.markAsMutuallyExclusive(model, ImmutableMap.of(ImmutableSet.of(entityIsAPerson), 0.5,
        ImmutableSet.of(entityIsAnOrganization), 0.2), system, null);

    dumpAndAssertValid(model, "create an entity with uncertainty about its type");
  }

  @Test
  void createARelationBetweenTwoEntitiesWhereThereIsUncertaintyAboutIdentityOfOneArgument() {

  }

  @Test
  void createAnEvent() {

  }

  @Test
  void labelSentimentRegardingAnEntity() {

  }

  @Test
  void useSubgraphConfidencesToShowMutuallyExclusiveLinkedEventArgumentOptions() {

  }

  // we dump the test name and the model in Turtle format so that whenever the user
  // runs the tests, they will also get the examples
  private void dumpAndAssertValid(Model model, String testName) {
    System.out.println("\n\n" + testName + "\n\n");
    RDFDataMgr.write(System.out, model, RDFFormat.TURTLE_PRETTY);
    assertTrue(validator.validateKB(model));
  }

  private Model createModel() {
    final Model model = ModelFactory.createDefaultModel();
    // adding namespace prefixes makes the Turtle output more readable
    model.setNsPrefix("rdf", RDF.uri);
    model.setNsPrefix("xsd", XSD.getURI());
    model.setNsPrefix("aida", AidaAnnotationOntology.NAMESPACE);
    model.setNsPrefix("aidaProgramOntology", AidaDomainOntology.NAMESPACE);
    model.setNsPrefix("skos", SKOS.uri);
    return model;
  }
}
