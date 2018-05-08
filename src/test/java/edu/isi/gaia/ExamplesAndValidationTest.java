package edu.isi.gaia;

import static edu.isi.gaia.AIFUtils.markAsMutuallyExclusive;
import static edu.isi.gaia.AIFUtils.markAsPossibleClusterMember;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.junit.jupiter.api.Nested;
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

  @Nested
  class ValidExamples {

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
          entity, AidaDomainOntology.PERSON, system, 1.0);

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
          entity, AidaDomainOntology.PERSON, system, 0.5);
      final Resource entityIsAnOrganization = AIFUtils
          .markType(model, "http://www.test.org/assertions/2",
              entity, AidaDomainOntology.ORGANIZATION, system, 0.2);

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
      final Model model = createModel();

      // every AIF needs an object for the system responsible for creating it
      final Resource system = AIFUtils.makeSystemWithURI(model, "http://www.test.edu/testSystem");

      // we want to represent a "city_of_birth" relation for a person, but we aren't sure whether
      // they were born in Louisville or Cambridge
      final Resource personEntity = AIFUtils
          .makeEntity(model, "http://www.test.edu/entities/1", system);
      AIFUtils.markType(model, "http://www.test.org/assertions/1",
          personEntity, AidaDomainOntology.PERSON, system, 1.0);

      // create entities for the two locations
      final Resource louisvilleEntity = AIFUtils
          .makeEntity(model, "http://www.test.edu/entities/2", system);
      AIFUtils.markType(model, "http://www.test.org/assertions/2",
          louisvilleEntity, AidaDomainOntology.GPE, system, 1.0);
      final Resource cambridgeEntity = AIFUtils
          .makeEntity(model, "http://www.test.edu/entities/3", system);
      AIFUtils.markType(model, "http://www.test.org/assertions/3",
          cambridgeEntity, AidaDomainOntology.GPE, system, 1.0);

      // create an entity for the uncertain place of birth
      final Resource uncertainPlaceOfBirthEntity = AIFUtils
          .makeEntity(model, "http://www.test.edu/entities/4", system);

      // we use clusters to represent uncertainty about identity
      // we make two clusters, one for Louisville and one for Cambridge
      final Resource louisvilleCluster = AIFUtils.makeClusterWithPrototype(model,
          "http://www.test.edu/clusters/1", louisvilleEntity, system);
      final Resource cambridgeCluster = AIFUtils.makeClusterWithPrototype(model,
          "http://www.test.edu/clusters/2", cambridgeEntity, system);

      // the uncertain place of birth is either Louisville or Cambridge
      final Resource placeOfBirthInLouisvilleCluster = markAsPossibleClusterMember(model,
          uncertainPlaceOfBirthEntity, louisvilleCluster, 0.4, system);
      final Resource placeOfBirthInCambridgeCluster = markAsPossibleClusterMember(model,
          uncertainPlaceOfBirthEntity, cambridgeCluster, 0.6, system);
      // but not both
      AIFUtils.markAsMutuallyExclusive(model,
          ImmutableMap.of(ImmutableSet.of(placeOfBirthInCambridgeCluster), 0.4,
              ImmutableSet.of(placeOfBirthInLouisvilleCluster), 0.6),
          system, null);

    }

    @Test
    void createAnEvent() {
      final Model model = createModel();

      // every AIF needs an object for the system responsible for creating it
      final Resource system = AIFUtils.makeSystemWithURI(model,
          "http://www.test.edu/testSystem");

      // we make a resource for the event itself
      final Resource event = AIFUtils.makeEvent(model,
          "http://www.test.edu/events/1", system);

      // mark the event as a Personnel.Elect event; type is encoded separately so we can express
      // uncertainty about type
      AIFUtils.markType(model, "http://www.test.edu/assertions/5", event,
          AidaDomainOntology.eventType("PERSONNEL.ELECT"), system, 1.0);

      // create the two entities involved in the event
      final Resource electee = AIFUtils.makeEntity(model, "http://www.test.edu/entities/1",
          system);
      AIFUtils.markType(model, "http://www.test.edu/assertions/6", electee,
          AidaDomainOntology.PERSON, system, 1.0);

      final Resource electionCountry = AIFUtils.makeEntity(model,
          "http://www.test.edu/entities/2", system);
      AIFUtils.markType(model, "http://www.test.edu/assertions/7", electionCountry,
          AidaDomainOntology.GPE, system, 1.0);

      // link those entities to the event
      AIFUtils.markAsEventArgument(model, event, AidaDomainOntology.eventArgumentType("Person"),
          electee, system, 0.785);
      AIFUtils.markAsEventArgument(model, event, AidaDomainOntology.eventArgumentType("Place"),
          electionCountry, system, 0.589);
    }

    @Test
    void labelSentimentRegardingAnEntity() {
      // TODO
    }

    @Test
    void useSubgraphConfidencesToShowMutuallyExclusiveLinkedEventArgumentOptions() {
      // we want to say that either Fred hit Bob or Bob hit Fred, but we aren't sure which
      final Model model = createModel();

      // every AIF needs an object for the system responsible for creating it
      final Resource system = AIFUtils.makeSystemWithURI(model,
          "http://www.test.edu/testSystem");

      // we make a resource for the event itself
      final Resource event = AIFUtils.makeEvent(model,
          "http://www.test.edu/events/1", system);

      // mark the event as a Personnel.Elect event; type is encoded separately so we can express
      // uncertainty about type
      AIFUtils.markType(model, "http://www.test.edu/assertions/5", event,
          AidaDomainOntology.eventType("CONFLICT.ATTACK"), system, 1.0);

      // create the two entities involved in the event
      final Resource bob = AIFUtils.makeEntity(model, "http://www.test.edu/entities/1",
          system);
      AIFUtils.markType(model, "http://www.test.edu/assertions/6", bob,
          AidaDomainOntology.PERSON, system, 1.0);

      final Resource fred = AIFUtils.makeEntity(model,
          "http://www.test.edu/entities/2", system);
      AIFUtils.markType(model, "http://www.test.edu/assertions/7", fred,
          AidaDomainOntology.PERSON, system, 1.0);

      // we link all possible argument fillers to the event
      final ImmutableSet<Resource> bobHitFredAssertions = ImmutableSet.of(
          AIFUtils.markAsEventArgument(model, event,
              AidaDomainOntology.eventArgumentType("Attacker"), bob, system, null),
          AIFUtils.markAsEventArgument(model, event,
              AidaDomainOntology.eventArgumentType("Target"), fred, system, null));

      final ImmutableSet<Resource> fredHitBobAssertions = ImmutableSet.of(
          AIFUtils.markAsEventArgument(model, event,
              AidaDomainOntology.eventArgumentType("Attacker"), fred, system, null),
          AIFUtils.markAsEventArgument(model, event,
              AidaDomainOntology.eventArgumentType("Target"), bob, system, null));

      // then we mark these as mutually exclusive
      // we also mark confidence 0.2 that neither of these are true
      markAsMutuallyExclusive(model, ImmutableMap.of(bobHitFredAssertions, 0.6,
          fredHitBobAssertions, 0.2), system, 0.2);
    }
  }

  /**
   * Don't do what these do!
   *
   * These should fail to validate.
   */
  @Nested
  class InvalidExamples {

    @Test
    void confidenceOutsideOfZeroOne() {
      final Model model = createModel();

      final Resource system = AIFUtils.makeSystemWithURI(model,
          "http://www.test.edu/testSystem");

      final Resource entity = AIFUtils.makeEntity(model, "http://www.test.edu/entities/1",
          system);

      AIFUtils.markType(model, "http://www.test.org/assertions/1",
          // illegal confidence value - not in [0.0, 1.0]
          entity, AidaDomainOntology.PERSON, system, 100.0);

      assertFalse(validator.validateKB(model));
    }

    @Test
    void entityMissingType() {
      // having multiple type assertions in case of uncertainty is ok, but there must always
      // be at least one type assertion
      final Model model = createModel();

      final Resource system = AIFUtils.makeSystemWithURI(model,
          "http://www.test.edu/testSystem");

      AIFUtils.makeEntity(model, "http://www.test.edu/entities/1",
          system);
      assertFalse(validator.validateKB(model));
    }

    @Test
    void eventMissingType() {
      // having multiple type assertions in case of uncertainty is ok, but there must always
      // be at least one type assertion
      final Model model = createModel();

      final Resource system = AIFUtils.makeSystemWithURI(model,
          "http://www.test.edu/testSystem");

      AIFUtils.makeEntity(model, "http://www.test.edu/events/1",
          system);
      assertFalse(validator.validateKB(model));
    }
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
