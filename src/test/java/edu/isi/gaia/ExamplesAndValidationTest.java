package edu.isi.gaia;

import static edu.isi.gaia.AIFUtils.makeHypothesis;
import static edu.isi.gaia.AIFUtils.makeRelation;
import static edu.isi.gaia.AIFUtils.markAsMutuallyExclusive;
import static edu.isi.gaia.AIFUtils.markAsPossibleClusterMember;
import static edu.isi.gaia.AIFUtils.markDependsOnHypothesis;
import static edu.isi.gaia.AIFUtils.markType;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import edu.isi.gaia.AIFUtils.BoundingBox;
import edu.isi.gaia.AIFUtils.Point;
import kotlin.text.Charsets;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
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

  private final ValidateAIF validator = ValidateAIF.createForDomainOntologySource(
      Resources.asCharSource(Resources.getResource("edu/isi/gaia/coldstart-ontology.ttl"),
          Charsets.UTF_8));

  @Nested
  class ValidExamples {

    @Test
    void createAnEntityOfTypePersonWithAllJustificationTypesAndConfidence() {
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
          entity, ColdStartOntology.PERSON, system, 1.0);

      // the justification provides the evidence for our claim about the entity's type
      // we attach this justification to both the type assertion and the entity object
      // itself, since it provides evidence both for the entity's existence and its type.
      // in TA1 -> TA2 communications, we attach confidences at the level of justifications
      AIFUtils.markTextJustification(model, ImmutableSet.of(entity, typeAssertion),
          "NYT_ENG_20181231", 42, 143, system, 0.973);

      // let's suppose we also have evidence from an image
      AIFUtils.markImageJustification(model, ImmutableSet.of(entity, typeAssertion),
          "NYT_ENG_20181231_03",
          new BoundingBox(new Point(123, 45), new Point(167, 98)),
          system, 0.123);

      // and also a video where the entity appears in a keyframe
      AIFUtils.markKeyFrameVideoJustification(model, ImmutableSet.of(entity, typeAssertion),
          "NYT_ENG_20181231_03", "keyframe ID",
          new BoundingBox(new Point(234, 56), new Point(345, 101)),
          system, 0.234);

      // and also a video where the entity does not appear in a keyframe
      AIFUtils.markShotVideoJustification(model, ImmutableSet.of(entity, typeAssertion),
          "SOME_VIDEO", "some shot ID", system, 0.487);

      // and even audio!
      AIFUtils.markAudioJustification(model, ImmutableSet.of(entity, typeAssertion),
          "NYT_ENG_201181231", 4.566, 9.876, system, 0.789);

      // also we can link this entity to something in an external KB
      AIFUtils.linkToExternalKB(model, entity, "freebase:FOO", system, .398);

      // let's mark our entity with some arbitrary system-private data. You can attach such data
      // to nearly anything
      AIFUtils.markPrivateData(model, entity, "{ 'hello' : 'world' }", system);

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
          entity, ColdStartOntology.PERSON, system, 0.5);
      final Resource entityIsAnOrganization = AIFUtils
          .markType(model, "http://www.test.org/assertions/2",
              entity, ColdStartOntology.ORGANIZATION, system, 0.2);

      AIFUtils.markTextJustification(model, ImmutableSet.of(entity, entityIsAPerson),
          "NYT_ENG_201181231",
          42, 143, system, 0.6);

      AIFUtils.markTextJustification(model, ImmutableSet.of(entity, entityIsAnOrganization),
          "NYT_ENG_201181231", 343, 367, system, 0.3);

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
          personEntity, ColdStartOntology.PERSON, system, 1.0);

      // create entities for the two locations
      final Resource louisvilleEntity = AIFUtils
          .makeEntity(model, "http://www.test.edu/entities/2", system);
      AIFUtils.markType(model, "http://www.test.org/assertions/2",
          louisvilleEntity, ColdStartOntology.GPE, system, 1.0);
      final Resource cambridgeEntity = AIFUtils
          .makeEntity(model, "http://www.test.edu/entities/3", system);
      AIFUtils.markType(model, "http://www.test.org/assertions/3",
          cambridgeEntity, ColdStartOntology.GPE, system, 1.0);

      // create an entity for the uncertain place of birth
      final Resource uncertainPlaceOfBirthEntity = AIFUtils
          .makeEntity(model, "http://www.test.edu/entities/4", system);

      // whatever this place turns out to refer to, we're sure it's where they live
      makeRelation(model, "http://www.test.edu/relations/1", personEntity,
          ColdStartOntology.relationTypeJava("cities_of_residence"),
          uncertainPlaceOfBirthEntity, system, 1.0);

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

      dumpAndAssertValid(model, "create a relation between two entities where there"
          + "is uncertainty about identity of one argument");

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
          ColdStartOntology.eventTypeJava("PERSONNEL.ELECT"), system, 1.0);

      // create the two entities involved in the event
      final Resource electee = AIFUtils.makeEntity(model, "http://www.test.edu/entities/1",
          system);
      AIFUtils.markType(model, "http://www.test.edu/assertions/6", electee,
          ColdStartOntology.PERSON, system, 1.0);

      final Resource electionCountry = AIFUtils.makeEntity(model,
          "http://www.test.edu/entities/2", system);
      AIFUtils.markType(model, "http://www.test.edu/assertions/7", electionCountry,
          ColdStartOntology.GPE, system, 1.0);

      // link those entities to the event
      AIFUtils.markAsEventArgument(model, event, ColdStartOntology.eventArgumentTypeJava("Person"),
          electee, system, 0.785);
      AIFUtils.markAsEventArgument(model, event, ColdStartOntology.eventArgumentTypeJava("Place"),
          electionCountry, system, 0.589);

      dumpAndAssertValid(model, "create an event");
    }

    @Test
    void createSeedlingEvent() {
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
              model.createResource("http://darpa.mil/ontologies/SeedlingOntology/BUSINESS_DECLARE-BANKRUPTCY"),
              system, 1.0);

      // create the two entities involved in the event
      final Resource electee = AIFUtils.makeEntity(model, "http://www.test.edu/entities/1",
              system);
      AIFUtils.markType(model, "http://www.test.edu/assertions/6", electee,
              model.createResource("http://darpa.mil/ontologies/SeedlingOntology/Organization"),
              system, 1.0);

      final Resource electionCountry = AIFUtils.makeEntity(model,
              "http://www.test.edu/entities/2", system);
      AIFUtils.markType(model, "http://www.test.edu/assertions/7", electionCountry,
              model.createResource("http://darpa.mil/ontologies/SeedlingOntology/GeopoliticalEntity"),
              system, 1.0);

      final Resource time = AIFUtils.makeEntity(model,
              "http://www.test.edu/entities/3", system);
      AIFUtils.markType(model, "http://www.test.edu/assertions/8", time,
              model.createResource("http://darpa.mil/ontologies/SeedlingOntology/Time"),
              system, 1.0);

      // link those entities to the event
      AIFUtils.markAsEventArgument(model, event, model.createResource("http://darpa.mil/ontologies/SeedlingOntology/BUSINESS_DECLARE-BANKRUPTCY_arg1"),
              electee, system, 0.785);
      AIFUtils.markAsEventArgument(model, event, model.createResource("http://darpa.mil/ontologies/SeedlingOntology/BUSINESS_DECLARE-BANKRUPTCY_arg2"),
              electionCountry, system, 0.589);
      AIFUtils.markAsEventArgument(model, event, model.createResource("http://darpa.mil/ontologies/SeedlingOntology/BUSINESS_DECLARE-BANKRUPTCY_arg3"),
              time, system, 0.589);

      RDFDataMgr.write(System.out, model, RDFFormat.TURTLE_PRETTY);
      assertTrue(ValidateAIF.createForDomainOntologySource(
              Resources.asCharSource(Resources.getResource("edu/isi/gaia/seedling-ontology.ttl"),
                      Charsets.UTF_8)).validateKB(model));
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
          ColdStartOntology.eventTypeJava("CONFLICT.ATTACK"), system, 1.0);

      // create the two entities involved in the event
      final Resource bob = AIFUtils.makeEntity(model, "http://www.test.edu/entities/1",
          system);
      AIFUtils.markType(model, "http://www.test.edu/assertions/6", bob,
          ColdStartOntology.PERSON, system, 1.0);

      final Resource fred = AIFUtils.makeEntity(model,
          "http://www.test.edu/entities/2", system);
      AIFUtils.markType(model, "http://www.test.edu/assertions/7", fred,
          ColdStartOntology.PERSON, system, 1.0);

      // we link all possible argument fillers to the event
      final ImmutableSet<Resource> bobHitFredAssertions = ImmutableSet.of(
          AIFUtils.markAsEventArgument(model, event,
              ColdStartOntology.eventArgumentTypeJava("Attacker"), bob, system, null),
          AIFUtils.markAsEventArgument(model, event,
              ColdStartOntology.eventArgumentTypeJava("Target"), fred, system, null));

      final ImmutableSet<Resource> fredHitBobAssertions = ImmutableSet.of(
          AIFUtils.markAsEventArgument(model, event,
              ColdStartOntology.eventArgumentTypeJava("Attacker"), fred, system, null),
          AIFUtils.markAsEventArgument(model, event,
              ColdStartOntology.eventArgumentTypeJava("Target"), bob, system, null));

      // then we mark these as mutually exclusive
      // we also mark confidence 0.2 that neither of these are true
      markAsMutuallyExclusive(model, ImmutableMap.of(bobHitFredAssertions, 0.6,
          fredHitBobAssertions, 0.2), system, 0.2);

      dumpAndAssertValid(model, "sub-graph confidences");
    }

    @Test
    void twoHypotheses() {
      final Model model = createModel();

      // every AIF needs an object for the system responsible for creating it
      final Resource system = AIFUtils.makeSystemWithURI(model,
          "http://www.test.edu/testSystem");

      // we want to represent that we know, regardless of hypothesis, that there is a person
      // named Bob, two companies (Google and Amazon), and two places (Seattle and California).
      final Resource bob = AIFUtils.makeEntity(model, "http://www.test.edu/entities/Bob",
          system);
      AIFUtils.markType(model, "http://www.test.org/assertions/1",
          bob, ColdStartOntology.PERSON, system, 1.0);
      final Resource google = AIFUtils.makeEntity(model, "http://www.test.edu/entities/Google",
          system);
      AIFUtils.markType(model, "http://www.test.org/assertions/2",
          google, ColdStartOntology.ORGANIZATION, system, 1.0);
      final Resource amazon = AIFUtils.makeEntity(model, "http://www.test.edu/entities/Amazon",
          system);
      AIFUtils.markType(model, "http://www.test.org/assertions/3",
          amazon, ColdStartOntology.ORGANIZATION, system, 1.0);
      final Resource seattle = AIFUtils.makeEntity(model, "http://www.test.edu/entities/Seattle",
          system);
      AIFUtils.markType(model, "http://www.test.org/assertions/4",
          seattle, ColdStartOntology.GPE, system, 1.0);
      final Resource california = AIFUtils
          .makeEntity(model, "http://www.test.edu/entities/California",
              system);
      AIFUtils.markType(model, "http://www.test.org/assertions/5",
          california, ColdStartOntology.GPE, system, 1.0);

      // under the background hypothesis that Bob lives in Seattle, we believe he works for Amazon
      final Resource bobLivesInSeattle = makeRelation(model, "http://www.test.edu/relations/1",
          bob, ColdStartOntology.relationTypeJava("cities_of_residence"),
          seattle, system, 1.0);
      final Resource bobLivesInSeattleHypothesis = makeHypothesis(model,
          "http://www.test.edu/hypotheses/1", ImmutableSet.of(bobLivesInSeattle),
          system);
      final Resource bobWorksForAmazon = makeRelation(model, "http://www.test.edu/relations/2",
          bob, ColdStartOntology.relationTypeJava("employee_or_member_of"),
          amazon, system, 1.0);
      markDependsOnHypothesis(bobWorksForAmazon, bobLivesInSeattleHypothesis);

      // under the background hypothesis that Bob lives in California, we believe he works for Google
      final Resource bobLivesInCalifornia = makeRelation(model, "http://www.test.edu/relations/3",
          bob, ColdStartOntology.relationTypeJava("cities_of_residence"),
          california, system, 1.0);
      final Resource bobLivesInCaliforniaHypothesis = makeHypothesis(model,
          "http://www.test.edu/hypotheses/2", ImmutableSet.of(bobLivesInCalifornia),
          system);
      final Resource bobWorksForGoogle = makeRelation(model, "http://www.test.edu/relations/4",
          bob, ColdStartOntology.relationTypeJava("employee_or_member_of"),
          google, system, 1.0);
      markDependsOnHypothesis(bobWorksForGoogle, bobLivesInCaliforniaHypothesis);

      dumpAndAssertValid(model, "two hypotheses");
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
          entity, ColdStartOntology.PERSON, system, 100.0);

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

      AIFUtils.makeEvent(model, "http://www.test.edu/events/1",
          system);
      assertFalse(validator.validateKB(model));
    }

    @Test
    void nonTypeUsedAsType() {
      final Model model = createModel();

      final Resource system = AIFUtils.makeSystemWithURI(model,
          "http://www.test.edu/testSystem");

      final Resource entity = AIFUtils.makeEntity(model, "http://www.test.edu/entities/1",
          system);
      markType(model, "http://www.test.edu/typeAssertion/1", entity,
          // use a blank node as the bogus entity type
          model.createResource(), system, 1.0);
      assertFalse(validator.validateKB(model));
    }

    @Test
    void justificationMissingConfidence() {
      // having multiple type assertions in case of uncertainty is ok, but there must always
      // be at least one type assertion
      final Model model = createModel();

      final Resource system = AIFUtils.makeSystemWithURI(model,
          "http://www.test.edu/testSystem");

      final Resource entity = AIFUtils.makeEntity(model, "http://www.test.edu/events/1",
          system);

      // below is just the content of AIFUtils.markTextJustification, except without the required
      // confidence

      final Resource justification = model.createResource();
      justification.addProperty(RDF.type, AidaAnnotationOntology.TEXT_JUSTIFICATION_CLASS);
      // the document ID for the justifying source document
      justification.addProperty(AidaAnnotationOntology.SOURCE, model.createTypedLiteral("FOO"));
      justification.addProperty(AidaAnnotationOntology.START_OFFSET,
          model.createTypedLiteral(14));
      justification.addProperty(AidaAnnotationOntology.END_OFFSET_INCLUSIVE,
          model.createTypedLiteral(56));
      justification.addProperty(AidaAnnotationOntology.SYSTEM_PROPERTY, system);
      entity.addProperty(AidaAnnotationOntology.JUSTIFIED_BY, justification);

      assertFalse(validator.validateKB(model));
    }

    // this validation constraint is not working yet
    @Disabled
    @Test
    void missingRdfTypeOnNamedNode() {
      final Model model = createModel();

      final Resource system = AIFUtils.makeSystemWithURI(model,
          "http://www.test.edu/testSystem");

      // below we copy the code from AIFUtils.makeEntity but forget to mark it as an entity
      final Resource entity = model.createResource("http://www.test.edu/entity/1");
      entity.addProperty(AidaAnnotationOntology.SYSTEM_PROPERTY, system);
      RDFDataMgr.write(System.out, model, RDFFormat.TURTLE_PRETTY);
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
    model.setNsPrefix("coldstart", ColdStartOntology.NAMESPACE);
    model.setNsPrefix("skos", SKOS.uri);
    return model;
  }
}
