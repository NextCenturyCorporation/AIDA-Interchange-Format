package edu.isi.gaia;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import edu.isi.gaia.AIFUtils.*;
import kotlin.text.Charsets;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.util.Arrays;

import static edu.isi.gaia.AIFUtils.*;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(Lifecycle.PER_CLASS)
public class ExamplesAndValidationTest {
    private static final String NS = "http://www.test.org/";

  @BeforeAll
  static void declutterLogging() {
    // prevent too much logging from obscuring the Turtle examples which will be printed
    ((Logger)org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
  }

  private final ValidateAIF validatorForColdStart = ValidateAIF.createForDomainOntologySource(
      Resources.asCharSource(Resources.getResource("edu/isi/gaia/ColdStartOntology"),
          Charsets.UTF_8));

  private final ValidateAIF seedlingValidator = ValidateAIF.createForDomainOntologySource(
      Resources.asCharSource(Resources.getResource("edu/isi/gaia/SeedlingOntology"),
          Charsets.UTF_8));

    private int assertionCount = 1;
    private int entityCount = 1;

    private String getUri(String localName) {
        return NS + localName;
    }

    private String getAssertionUri() {
        return getUri("assertions/" + assertionCount++);
    }

    private String getEntityUri() {
        return getUri("entities/" + entityCount++);
    }

    @BeforeEach
    void setUp() {
        assertionCount = 1;
        entityCount = 1;
    }

@Nested
class ValidExamples {

    @Test
    void createSeedlingEntityOfTypePersonWithAllJustificationTypesAndConfidence() {
        final Model model = createModel(true);

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
                entity, SeedlingOntologyMapper.PERSON, system, 1.0);

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

        dumpAndAssertValid(model, "create a seedling entity of type person with textual " +
                "justification and confidence", true);
    }

    @Test
    void createSeedlingEntityWithUncertaintyAboutItsType() {
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = AIFUtils.makeSystemWithURI(model, "http://www.test.edu/testSystem");

        final Resource entity = AIFUtils.makeEntity(model, "http://www.test.edu/entities/1", system);
        final Resource entityIsAPerson = AIFUtils.markType(model, "http://www.test.org/assertions/1",
                entity, SeedlingOntologyMapper.PERSON, system, 0.5);
        final Resource entityIsAnOrganization = AIFUtils
                .markType(model, "http://www.test.org/assertions/2",
                        entity, SeedlingOntologyMapper.ORGANIZATION, system, 0.2);

        AIFUtils.markTextJustification(model, ImmutableSet.of(entity, entityIsAPerson),
                "NYT_ENG_201181231",
                42, 143, system, 0.6);

        AIFUtils.markTextJustification(model, ImmutableSet.of(entity, entityIsAnOrganization),
                "NYT_ENG_201181231", 343, 367, system, 0.3);

        AIFUtils.markAsMutuallyExclusive(model, ImmutableMap.of(ImmutableSet.of(entityIsAPerson), 0.5,
                ImmutableSet.of(entityIsAnOrganization), 0.2), system, null);

        dumpAndAssertValid(model, "create a seedling entity with uncertainty about its type", true);
    }

    @Test
    void createARelationBetweenTwoSeedlingEntitiesWhereThereIsUncertaintyAboutIdentityOfOneArgument() {
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = AIFUtils.makeSystemWithURI(model, "http://www.test.edu/testSystem");

        final SeedlingOntologyMapper ontologyMapping = new SeedlingOntologyMapper();

        // we want to represent a "city_of_birth" relation for a person, but we aren't sure whether
        // they were born in Louisville or Cambridge
        final Resource personEntity = AIFUtils
                .makeEntity(model, "http://www.test.edu/entities/1", system);
        AIFUtils.markType(model, getAssertionUri(),
                personEntity, SeedlingOntologyMapper.PERSON, system, 1.0);

        // create entities for the two locations
        final Resource louisvilleEntity = AIFUtils
                .makeEntity(model, "http://www.test.edu/entities/2", system);
        AIFUtils.markType(model, getAssertionUri(),
                louisvilleEntity, SeedlingOntologyMapper.GPE, system, 1.0);
        final Resource cambridgeEntity = AIFUtils
                .makeEntity(model, "http://www.test.edu/entities/3", system);
        AIFUtils.markType(model, getAssertionUri(),
                cambridgeEntity, SeedlingOntologyMapper.GPE, system, 1.0);

        // create an entity for the uncertain place of birth
        final Resource uncertainPlaceOfBirthEntity = AIFUtils
                .makeEntity(model, "http://www.test.edu/entities/4", system);

        // whatever this place turns out to refer to, we're sure it's where they live
        String relation = "Physical.Resident";
        makeRelationInEventForm(model, "http://www.test.edu/relations/1",
                ontologyMapping.relationType(relation),
                ontologyMapping.eventArgumentTypeNotLowercase(relation + "_Resident"), personEntity,
                ontologyMapping.eventArgumentTypeNotLowercase(relation + "_Place"), uncertainPlaceOfBirthEntity,
                getAssertionUri(), system, 1.0);

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

        dumpAndAssertValid(model, "create a relation between two seedling entities where there"
                + "is uncertainty about identity of one argument", true);

    }

    @Test
    void createSeedlingEvent() {
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = AIFUtils.makeSystemWithURI(model,
                "http://www.test.edu/testSystem");

        // we make a resource for the event itself
        final Resource event = AIFUtils.makeEvent(model,
                "http://www.test.edu/events/1", system);

        final SeedlingOntologyMapper ontologyMapping = new SeedlingOntologyMapper();

        String eventTypeString = "Personnel.Elect";

        // mark the event as a Personnel.Elect event; type is encoded separately so we can express
        // uncertainty about type
        // NOTE: mapper keys use '.' separator but produce correct seedling output
        AIFUtils.markType(model, "http://www.test.edu/assertions/5", event,
                ontologyMapping.eventType(eventTypeString), system, 1.0);

        // create the two entities involved in the event
        final Resource electee = AIFUtils.makeEntity(model, "http://www.test.edu/entities/1",
                system);
        AIFUtils.markType(model, "http://www.test.edu/assertions/6", electee,
                SeedlingOntologyMapper.PERSON, system, 1.0);

        final Resource electionCountry = AIFUtils.makeEntity(model,
                "http://www.test.edu/entities/2", system);
        AIFUtils.markType(model, "http://www.test.edu/assertions/7", electionCountry,
                SeedlingOntologyMapper.GPE, system, 1.0);

        // link those entities to the event
        AIFUtils.markAsArgument(model, event,
                ontologyMapping.eventArgumentTypeNotLowercase(eventTypeString + "_Elect"),
                electee, system, 0.785);
        AIFUtils.markAsArgument(model, event,
                ontologyMapping.eventArgumentTypeNotLowercase(eventTypeString + "_Place"),
                electionCountry, system, 0.589);

        dumpAndAssertValid(model, "create a seedling event", true);
    }

    /**
     * Same as createSeedlingEvent above, except with event argument URI's
     */
    @Test
    void createSeedlingEventWithEventArgumentURI() {
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = AIFUtils.makeSystemWithURI(model,
                "http://www.test.edu/testSystem");

        // we make a resource for the event itself
        final Resource event = AIFUtils.makeEvent(model,
                "http://www.test.edu/events/1", system);

        final SeedlingOntologyMapper ontologyMapping = new SeedlingOntologyMapper();

        String eventTypeString = "Personnel.Elect";

        // mark the event as a Personnel.Elect event; type is encoded separately so we can express
        // uncertainty about type
        // NOTE: mapper keys use '.' separator but produce correct seedling output
        AIFUtils.markType(model, "http://www.test.edu/assertions/5", event,
                ontologyMapping.eventType(eventTypeString), system, 1.0);

        // create the two entities involved in the event
        final Resource electee = AIFUtils.makeEntity(model, "http://www.test.edu/entities/1",
                system);
        AIFUtils.markType(model, "http://www.test.edu/assertions/6", electee,
                SeedlingOntologyMapper.PERSON, system, 1.0);

        final Resource electionCountry = AIFUtils.makeEntity(model,
                "http://www.test.edu/entities/2", system);
        AIFUtils.markType(model, "http://www.test.edu/assertions/7", electionCountry,
                SeedlingOntologyMapper.GPE, system, 1.0);

        // link those entities to the event
        AIFUtils.markAsArgument(model, event,
                ontologyMapping.eventArgumentTypeNotLowercase(eventTypeString + "_Elect"),
                electee, system, 0.785, "http://www.test.edu/eventArgument/1");
        AIFUtils.markAsArgument(model, event,
                ontologyMapping.eventArgumentTypeNotLowercase(eventTypeString + "_Place"),
                electionCountry, system, 0.589, "http://www.test.edu/eventArgument/2");

        dumpAndAssertValid(model, "create a seedling event with event assertion URI", true);
    }

    @Test
    void useSubgraphConfidencesToShowMutuallyExclusiveLinkedSeedlingEventArgumentOptions() {
        // we want to say that either Fred hit Bob or Bob hit Fred, but we aren't sure which
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = AIFUtils.makeSystemWithURI(model,
                "http://www.test.edu/testSystem");

        // we make a resource for the event itself
        final Resource event = AIFUtils.makeEvent(model,
                "http://www.test.edu/events/1", system);

        final SeedlingOntologyMapper ontologyMapping = new SeedlingOntologyMapper();

        String eventTypeString = "Conflict.Attack";

        // mark the event as a Personnel.Elect event; type is encoded separately so we can express
        // uncertainty about type
        // NOTE: mapper keys use '.' separator but produce correct seedling output
        AIFUtils.markType(model, "http://www.test.edu/assertions/5", event,
                ontologyMapping.eventType(eventTypeString), system, 1.0);

        // create the two entities involved in the event
        final Resource bob = AIFUtils.makeEntity(model, "http://www.test.edu/entities/1",
                system);
        AIFUtils.markType(model, "http://www.test.edu/assertions/6", bob,
                SeedlingOntologyMapper.PERSON, system, 1.0);

        final Resource fred = AIFUtils.makeEntity(model,
                "http://www.test.edu/entities/2", system);
        AIFUtils.markType(model, "http://www.test.edu/assertions/7", fred,
                SeedlingOntologyMapper.PERSON, system, 1.0);

        String attackerString = eventTypeString + "_Attacker";
        String targetString = eventTypeString + "_Target";

        // we link all possible argument fillers to the event
        final ImmutableSet<Resource> bobHitFredAssertions = ImmutableSet.of(
                AIFUtils.markAsArgument(model, event,
                        ontologyMapping.eventArgumentTypeNotLowercase(attackerString), bob, system, null),
                AIFUtils.markAsArgument(model, event,
                        ontologyMapping.eventArgumentTypeNotLowercase(targetString), fred, system, null));

        final ImmutableSet<Resource> fredHitBobAssertions = ImmutableSet.of(
                AIFUtils.markAsArgument(model, event,
                        ontologyMapping.eventArgumentTypeNotLowercase(attackerString), fred, system, null),
                AIFUtils.markAsArgument(model, event,
                        ontologyMapping.eventArgumentTypeNotLowercase(targetString), bob, system, null));

        // then we mark these as mutually exclusive
        // we also mark confidence 0.2 that neither of these are true
        markAsMutuallyExclusive(model, ImmutableMap.of(bobHitFredAssertions, 0.6,
                fredHitBobAssertions, 0.2), system, 0.2);

        dumpAndAssertValid(model, "seedling sub-graph confidences", true);
    }

    @Test
    void twoSeedlingHypotheses() {
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = AIFUtils.makeSystemWithURI(model,
                "http://www.test.edu/testSystem");

        final SeedlingOntologyMapper ontologyMapping = new SeedlingOntologyMapper();

        // we want to represent that we know, regardless of hypothesis, that there is a person
        // named Bob, two companies (Google and Amazon), and two places (Seattle and California).
        final Resource bob = AIFUtils.makeEntity(model, "http://www.test.edu/entities/Bob",
                system);
        AIFUtils.markType(model, getAssertionUri(),
                bob, SeedlingOntologyMapper.PERSON, system, 1.0);
        final Resource google = AIFUtils.makeEntity(model, "http://www.test.edu/entities/Google",
                system);
        AIFUtils.markType(model, getAssertionUri(),
                google, SeedlingOntologyMapper.ORGANIZATION, system, 1.0);
        final Resource amazon = AIFUtils.makeEntity(model, "http://www.test.edu/entities/Amazon",
                system);
        AIFUtils.markType(model, getAssertionUri(),
                amazon, SeedlingOntologyMapper.ORGANIZATION, system, 1.0);
        final Resource seattle = AIFUtils.makeEntity(model, "http://www.test.edu/entities/Seattle",
                system);
        AIFUtils.markType(model, getAssertionUri(),
                seattle, SeedlingOntologyMapper.GPE, system, 1.0);
        final Resource california = AIFUtils
                .makeEntity(model, "http://www.test.edu/entities/California",
                        system);
        AIFUtils.markType(model, getAssertionUri(),
                california, SeedlingOntologyMapper.GPE, system, 1.0);

        // under the background hypothesis that Bob lives in Seattle, we believe he works for Amazon
        String cityRelation = "Physical.Resident";
        String cityRelationSubject = cityRelation + "_Resident";
        String cityRelationObject = cityRelation + "_Place";
        final Resource bobLivesInSeattle = makeRelationInEventForm(model, "http://www.test.edu/relations/1",
                ontologyMapping.relationType(cityRelation),
                ontologyMapping.eventArgumentTypeNotLowercase(cityRelationSubject), bob,
                ontologyMapping.eventArgumentTypeNotLowercase(cityRelationObject), seattle,
                getAssertionUri(), system, 1.0);
        final Resource bobLivesInSeattleHypothesis = makeHypothesis(model,
                "http://www.test.edu/hypotheses/1", ImmutableSet.of(bobLivesInSeattle),
                system);

        String employeeRelation = "OrganizationAffiliation.EmploymentMembership";
        String employeeRelationSubject = employeeRelation + "_Employee";
        String employeeRelationOjbect = employeeRelation + "_Organization";
        final Resource bobWorksForAmazon = makeRelationInEventForm(model, "http://www.test.edu/relations/2",
                ontologyMapping.relationType(employeeRelation),
                ontologyMapping.eventArgumentTypeNotLowercase(employeeRelationSubject), bob,
                ontologyMapping.eventArgumentTypeNotLowercase(employeeRelationOjbect), amazon,
                getAssertionUri(), system, 1.0);
        markDependsOnHypothesis(bobWorksForAmazon, bobLivesInSeattleHypothesis);

        // under the background hypothesis that Bob lives in California, we believe he works for Google
        final Resource bobLivesInCalifornia = makeRelationInEventForm(model, "http://www.test.edu/relations/3",
                ontologyMapping.relationType(cityRelation),
                ontologyMapping.eventArgumentTypeNotLowercase(cityRelationSubject), bob,
                ontologyMapping.eventArgumentTypeNotLowercase(cityRelationObject), california,
                getAssertionUri(), system, 1.0);
        final Resource bobLivesInCaliforniaHypothesis = makeHypothesis(model,
                "http://www.test.edu/hypotheses/2", ImmutableSet.of(bobLivesInCalifornia),
                system);
        final Resource bobWorksForGoogle = makeRelationInEventForm(model, "http://www.test.edu/relations/4",
                ontologyMapping.relationType(employeeRelation),
                ontologyMapping.eventArgumentTypeNotLowercase(employeeRelationSubject), bob,
                ontologyMapping.eventArgumentTypeNotLowercase(employeeRelationOjbect), google,
                getAssertionUri(), system, 1.0);
        markDependsOnHypothesis(bobWorksForGoogle, bobLivesInCaliforniaHypothesis);

        dumpAndAssertValid(model, "two seedling hypotheses", true);
    }


    @Test
    void createSeedlingEntityOfTypePersonWithImageJustificationAndVector() {
        final Model model = createModel(true);

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
                entity, SeedlingOntologyMapper.PERSON, system, 1.0);

        // the justification provides the evidence for our claim about the entity's type
        // we attach this justification to both the type assertion and the entity object
        // itself, since it provides evidence both for the entity's existence and its type.
        // in TA1 -> TA2 communications, we attach confidences at the level of justifications

        // let's suppose we have evidence from an image
        AIFUtils.markImageJustification(model, ImmutableSet.of(entity, typeAssertion),
                "NYT_ENG_20181231_03",
                new BoundingBox(new Point(123, 45), new Point(167, 98)),
                system, 0.123);

        // let's mark our entity with some arbitrary system-private data. You can attach such data
        // to nearly anything
        AIFUtils.markPrivateData(model, entity, "http://www.test.edu/systemX/personVector", Arrays.asList(2.0, 7.5, 0.2, 8.1), system);

        dumpAndAssertValid(model, "create a seedling entity of type person with image " +
                "justification and vector", true);
    }


    @Test
    void createSeedlingEntityWithAlternateNames() {
        final Model model = createModel(true);

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
                entity, SeedlingOntologyMapper.PERSON, system, 1.0);

        // This is just a test to make sure that validation works for the different
        // mark types.  Rare that you would have all three with a single entity.
        AIFUtils.markName(entity, "Name One");
        AIFUtils.markName(entity, "N. One");
        AIFUtils.markName(entity, "N-Money");

        AIFUtils.markTextValue(entity, "TextValue");

        AIFUtils.markNumericValueAsDouble(entity, 100);
        AIFUtils.markNumericValueAsLong(entity, 100);
        AIFUtils.markNumericValueAsString(entity, "100");

        dumpAndAssertValid(model, "create a seedling entity of type person with names", true);
    }

    @Test
    void createCompoundJustification() {
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = AIFUtils.makeSystemWithURI(model, "http://www.test.edu/testSystem");

        // it doesn't matter what URI we give entities, events, etc. so long as they are
        // unique
        final Resource entity = AIFUtils.makeEntity(model, "http://www.test.edu/entities/1", system);

        // in order to allow uncertainty about the type of an entity, we don't mark an
        // entity's type directly on the entity, but rather make a separate assertion for it
        // its URI doesn't matter either
        final Resource typeAssertion = AIFUtils.markType(model, "http://www.test.org/assertions/1",
                entity, SeedlingOntologyMapper.PERSON, system, 1.0);

        // the justification provides the evidence for our claim about the entity's type
        // we attach this justification to both the type assertion and the entity object
        // itself, since it provides evidence both for the entity's existence and its type.
        // in TA1 -> TA2 communications, we attach confidences at the level of justifications
        final Resource textJustification = AIFUtils.makeTextJustification(model, "NYT_ENG_20181231",
                42, 143, system, 0.973);

        // let's suppose we also have evidence from an image
        final Resource imageJustification = AIFUtils.makeImageJustification(model, "NYT_ENG_20181231_03",
                new BoundingBox(new Point(123, 45), new Point(167, 98)), system, 0.123);

        // and also a video where the entity appears in a keyframe
        final Resource keyFrameVideoJustification = AIFUtils.makeKeyFrameVideoJustification(model,
                "NYT_ENG_20181231_03", "keyframe ID",
                new BoundingBox(new Point(234, 56), new Point(345, 101)), system, 0.234);

        // and also a video where the entity does not appear in a keyframe
        final Resource shotVideoJustification = AIFUtils.makeShotVideoJustification(model, "SOME_VIDEO",
                "some shot ID", system, 0.487);

        // and even audio!
        final Resource audioJustification = AIFUtils.makeAudioJustification(model, "NYT_ENG_201181231",
                4.566, 9.876, system, 0.789);

        // combine all jutifications into single justifiedBy triple with new confidence
        AIFUtils.markCompoundJustification(model, ImmutableSet.of(entity),
                ImmutableSet.of(textJustification, imageJustification, keyFrameVideoJustification,
                        shotVideoJustification, audioJustification), system, 0.321);

        dumpAndAssertValid(model, "create a compound justification", true);
    }

    @Test
    void createHierarchicalCluster() {
        // we want to say that the cluster of Trump entities might be the same as the cluster of the president entities
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = AIFUtils.makeSystemWithURI(model, "http://www.test.edu/testSystem");

        // create president entities
        final Resource presidentUSA = AIFUtils.makeEntity(model, getEntityUri(), system);
        AIFUtils.markType(model, getAssertionUri(), presidentUSA, SeedlingOntologyMapper.GPE, system, 1.0);
        AIFUtils.markName(presidentUSA, "the president");

        final Resource newPresident = AIFUtils.makeEntity(model, getEntityUri(), system);
        AIFUtils.markType(model, getAssertionUri(), presidentUSA, SeedlingOntologyMapper.GPE, system, 1.0);
        AIFUtils.markName(presidentUSA, "the newly-inaugurated president");

        final Resource president45 = AIFUtils.makeEntity(model, getEntityUri(), system);
        AIFUtils.markType(model, getAssertionUri(), presidentUSA, SeedlingOntologyMapper.GPE, system, 1.0);
        AIFUtils.markName(presidentUSA, "the 45th president");

        // cluster president entities
        final Resource presidentCluster = AIFUtils.makeClusterWithPrototype(model,
                "http://www.test.edu/clusters/president", presidentUSA, system);
        // TODO: verify. Seems redundant
        AIFUtils.markAsPossibleClusterMember(model, presidentUSA, presidentCluster, 1, system);
        AIFUtils.markAsPossibleClusterMember(model, newPresident, presidentCluster, .9, system);
        AIFUtils.markAsPossibleClusterMember(model, president45, presidentCluster, .9, system);

        // create Trump entities
        final Resource donaldTrump = AIFUtils.makeEntity(model, getEntityUri(), system);
        AIFUtils.markType(model, getAssertionUri(), presidentUSA, SeedlingOntologyMapper.PERSON, system, 1.0);
        AIFUtils.markName(presidentUSA, "Donald Trump");

        final Resource trump = AIFUtils.makeEntity(model, getEntityUri(), system);
        AIFUtils.markType(model, getAssertionUri(), presidentUSA, SeedlingOntologyMapper.PERSON, system, 1.0);
        AIFUtils.markName(presidentUSA, "Trump");

        // cluster trump entities
        final Resource trumpCluster = AIFUtils.makeClusterWithPrototype(model,
                "http://www.test.edu/clusters/trump", donaldTrump, system);
        // TODO: verify. Seems redundant
        AIFUtils.markAsPossibleClusterMember(model, donaldTrump, trumpCluster, 1, system);
        AIFUtils.markAsPossibleClusterMember(model, trump, trumpCluster, .9, system);

        // mark president cluster as being part of trump cluster
        AIFUtils.markAsPossibleClusterMember(model, presidentCluster, trumpCluster, .6, system);

        dumpAndAssertValid(model, "seedling hierarchical cluster", true);
    }

    /**
     * Simplest possible cluster example.  Two entities might be the same thing.
     */
    @Test
    void createASimpleCluster() {
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = AIFUtils.makeSystemWithURI(model, "http://www.test.edu/testSystem");

        final SeedlingOntologyMapper ontologyMapping = new SeedlingOntologyMapper();

        // Two people, probably the same person
        final Resource personEntity1 = AIFUtils.makeEntity(model, "http://www.test.edu/entities/1", system);
        AIFUtils.markType(model, getAssertionUri(), personEntity1, SeedlingOntologyMapper.PERSON, system, 1.0);
        AIFUtils.markName(personEntity1, "Robert");

        final Resource personEntity2 = AIFUtils.makeEntity(model, "http://www.test.edu/entities/2", system);
        AIFUtils.markType(model, getAssertionUri(), personEntity2, SeedlingOntologyMapper.PERSON, system, 1.0);
        AIFUtils.markName(personEntity2, "Bobby");

        // create a cluster with prototype
        final Resource bobCluster = AIFUtils.makeClusterWithPrototype(model, "http://www.test.edu/clusters/bob", personEntity1, system);

        // person 1 is definitely in the cluster, person 2 is probably in the cluster
        AIFUtils.markAsPossibleClusterMember(model, personEntity1, bobCluster, 1, system);
        AIFUtils.markAsPossibleClusterMember(model, personEntity2, bobCluster, 0.71, system);

        dumpAndAssertValid(model, "create a simple cluster", true);
    }

    /**
     * Simplest possible cluster example, plus justification
     */
    @Test
    void createASimpleClusterWithJustification() {
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = AIFUtils.makeSystemWithURI(model, "http://www.test.edu/testSystem");

        final SeedlingOntologyMapper ontologyMapping = new SeedlingOntologyMapper();

        // Two people, probably the same person
        final Resource personEntity1 = AIFUtils.makeEntity(model, "http://www.test.edu/entities/1", system);
        AIFUtils.markType(model, getAssertionUri(), personEntity1, SeedlingOntologyMapper.PERSON, system, 1.0);
        AIFUtils.markName(personEntity1, "Robert");

        final Resource personEntity2 = AIFUtils.makeEntity(model, "http://www.test.edu/entities/2", system);
        AIFUtils.markType(model, getAssertionUri(), personEntity2, SeedlingOntologyMapper.PERSON, system, 1.0);
        AIFUtils.markName(personEntity2, "Bobby");

        // create a cluster with prototype
        final Resource bobCluster = AIFUtils.makeClusterWithPrototype(model, "http://www.test.edu/clusters/bob", personEntity1, system);

        // person 1 is definitely in the cluster, person 2 is probably in the cluster
        AIFUtils.markAsPossibleClusterMember(model, personEntity1, bobCluster, 1, system);
        final Resource bobbyMightBeRobert = AIFUtils.markAsPossibleClusterMember(model, personEntity2, bobCluster, 0.71, system);

        AIFUtils.markTextJustification(model, bobbyMightBeRobert, "NYT_ENG_20181231", 42,
                143, system, 0.973);

        dumpAndAssertValid(model, "create a simple cluster", true);
    }

    /**
     * Shows how to create a relation with uncertain endpoints using the version of coreference expected for
     * output NIST will execute SPARQL queries on.
     * <p>
     * In NIST-AIF (NAIF), all entities are restricted to justifications from a single document. All cross-document
     * coreference is indicated via cluster membership. Also, each entity is required to be part of at least
     * one cluster.
     */
    @Test
    void relationWhereBothEndpointsAreAmbiguousNISTRestrictedVersion() {
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = AIFUtils.makeSystemWithURI(model, "http://www.test.edu/testSystem");

        // let's imagine we have three documents.  We will make TextJustification objects for some entity mentions in
        // them which we will use later (with bogus offsets, because I don't want to bother to count them out).
        // Since NAIF requires entities to be restricted to justifications from a single document, we go ahead and
        // create our entities now, too.

        // In all the below, we are going to imagine the system is unsure whether "President Obama" is "Barack
        // Obama" or "Michelle Obama" and whether "Secretary Clinton" is "Hillary Clinton" or "Bill Clinton"

        // document 1: [Michelle Obama] was first Lady (married to [Barack Obama]).  [President Obama] was
        // a senator from Chicago.
        final Resource michelleObamaMention = AIFUtils.makeTextJustification(model, "doc1", 0,
                1, system, 1.0);
        final Resource firstLadyMention = AIFUtils.makeTextJustification(model, "doc1", 0,
                1, system, 1.0);
        final Resource barackObamaDoc1Mention = AIFUtils.makeTextJustification(model, "doc1", 0,
                1, system, 1.0);
        final Resource presidentObamaDoc1Mention = AIFUtils.makeTextJustification(model, "doc1", 0,
                1, system, 1.0);

        final Resource michelleObamaDoc1 = AIFUtils.makeEntity(model,
                "http://www.test.edu/entities/michelleObamaDoc1", system);
        markJustification(michelleObamaDoc1, michelleObamaMention);
        markJustification(michelleObamaDoc1, firstLadyMention);

        final Resource barackObamaDoc1 = AIFUtils.makeEntity(model,
                "http://www.test.edu/entities/barackObamaDoc1", system);
        markJustification(barackObamaDoc1, barackObamaDoc1Mention);

        // the uncertain "President Obama" gets its own entity, since we aren't sure which of the other two it
        // is identical to
        final Resource uncertainPresidentObamaDoc1 = AIFUtils.makeEntity(model,
                "http://www.test.edu/entities/uncertainPresidentObamaDoc1", system);
        markJustification(uncertainPresidentObamaDoc1, presidentObamaDoc1Mention);

        // document 2 text: "[Barack Obama] was the 44th president of the United States. [President Obama] was elected
        // in 2008.  [President Obama] worked with [Secretary Clinton].
        final Resource barackObamaDoc2Mention = AIFUtils.makeTextJustification(model, "doc2", 0,
                1, system, 1.0);
        final Resource presidentObamaDoc2Mention1 = AIFUtils.makeTextJustification(model, "doc2", 0,
                1, system, 1.0);
        final Resource presidentObamaDoc2Mention2 = AIFUtils.makeTextJustification(model, "doc2", 0,
                1, system, 1.0);
        final Resource secretaryClintonDoc2Mention = AIFUtils.makeTextJustification(model, "doc2", 0,
                1, system, 1.0);


        final Resource barackObamaDoc2 = AIFUtils.makeEntity(model,
                "http://www.test.edu/entities/barackObamaDoc2", system);
        markJustification(barackObamaDoc2, barackObamaDoc2Mention);

        final Resource uncertainPresidentObamaDoc2 = AIFUtils.makeEntity(model,
                "http://www.test.edu/entities/uncertainPresidentObamaDoc2", system);
        markJustification(uncertainPresidentObamaDoc2, presidentObamaDoc2Mention1);
        markJustification(uncertainPresidentObamaDoc2, presidentObamaDoc2Mention2);

        final Resource uncertainSecretaryClintonDoc2 = AIFUtils.makeEntity(model,
                "http://www.test.edu/entities/uncertainSecretaryClintonDoc2", system);
        markJustification(uncertainSecretaryClintonDoc2, secretaryClintonDoc2Mention);


        // document 3 text:  [Bill Clinton] is married to Hilary Clinton.  [Secretary Clinton] doesn't like hamburgers.
        final Resource billClintonMention = AIFUtils.makeTextJustification(model, "doc3", 0,
                1, system, 1.0);
        final Resource hillaryClintonMention = AIFUtils.makeTextJustification(model, "doc3", 0,
                1, system, 1.0);
        final Resource uncertainSecretaryClintonDoc3Mention = AIFUtils.makeTextJustification(model, "doc3", 0,
                1, system, 1.0);

        final Resource billClintonDoc3 = AIFUtils.makeEntity(model,
                "http://www.test.edu/entities/billClintonDoc3", system);
        markJustification(billClintonDoc3, billClintonMention);

        final Resource hillaryClintonDoc3 = AIFUtils.makeEntity(model,
                "http://www.test.edu/entities/hillaryClintonDoc3", system);
        markJustification(hillaryClintonDoc3, hillaryClintonMention);

        final Resource uncertainSecretaryClintonDoc3 = AIFUtils.makeEntity(model,
                "http://www.test.edu/entities/uncertainSecretaryClintonDoc3", system);
        markJustification(uncertainSecretaryClintonDoc3, uncertainSecretaryClintonDoc3Mention);

        // mark that all these entities are people
        int typeAssertionIndex = 0;
        for (Resource person : ImmutableList.of(michelleObamaDoc1, barackObamaDoc1, uncertainPresidentObamaDoc1,
                barackObamaDoc2, uncertainPresidentObamaDoc2, uncertainSecretaryClintonDoc2,
                billClintonDoc3, hillaryClintonDoc3, uncertainSecretaryClintonDoc3)) {
            markType(model, "http://www.test.edu/type_assertions/" + typeAssertionIndex,
                    person, SeedlingOntologyMapper.PERSON, system, 1.0);
        }

        // in NAIF, all cross-document linking is done via clusters and every entity must belong to some cluster
        final Resource michelleObamaCluster = AIFUtils.makeClusterWithPrototype(model,
                "http://www.test.edu/cluster/1", michelleObamaDoc1, system);
        final Resource barackObamaCluster = AIFUtils.makeClusterWithPrototype(model,
                "http://www.test.edu/cluster/2", barackObamaDoc1, system);
        final Resource billClintonCluster = AIFUtils.makeClusterWithPrototype(model,
                "http://www.test.edu/cluster/3", billClintonDoc3, system);
        final Resource hillaryClintonCluster = AIFUtils.makeClusterWithPrototype(model,
                "http://www.test.edu/cluster/4", hillaryClintonDoc3, system);

        // There are also some entities whose reference to is ambiguous. They belong to multiple clusters.
        final Resource presidentObamaDoc1IsMichelle = AIFUtils.markAsPossibleClusterMember(model,
                uncertainPresidentObamaDoc1, michelleObamaCluster, 0.5, system);
        final Resource presidentObamaDoc1IsBarack = AIFUtils.markAsPossibleClusterMember(model,
                uncertainPresidentObamaDoc1, barackObamaCluster, 0.5, system);
        AIFUtils.markEdgesAsMutuallyExclusive(model, ImmutableMap.of(presidentObamaDoc1IsMichelle, 0.5,
                presidentObamaDoc1IsBarack, 0.5), system, null);

        final Resource presidentObamaDoc2IsMichelle = AIFUtils.markAsPossibleClusterMember(model,
                uncertainPresidentObamaDoc2, michelleObamaCluster, 0.5, system);
        final Resource presidentObamaDoc2IsBarack = AIFUtils.markAsPossibleClusterMember(model,
                uncertainPresidentObamaDoc2, barackObamaCluster, 0.5, system);
        AIFUtils.markEdgesAsMutuallyExclusive(model, ImmutableMap.of(presidentObamaDoc2IsMichelle, 0.5,
                presidentObamaDoc2IsBarack, 0.5), system, null);

        final Resource secretaryClintonDoc2IsBill = AIFUtils.markAsPossibleClusterMember(model,
                uncertainSecretaryClintonDoc2, billClintonCluster, 0.5, system);
        final Resource secretaryClintonDoc2IsHillary = AIFUtils.markAsPossibleClusterMember(model,
                uncertainSecretaryClintonDoc2, hillaryClintonCluster, 0.5, system);
        AIFUtils.markEdgesAsMutuallyExclusive(model, ImmutableMap.of(secretaryClintonDoc2IsBill, 0.5,
                secretaryClintonDoc2IsHillary, 0.5), system, null);

        final Resource secretaryClintonDoc3IsBill = AIFUtils.markAsPossibleClusterMember(model,
                uncertainSecretaryClintonDoc3, billClintonCluster, 0.5, system);
        final Resource secretaryClintonDoc3IsHillary = AIFUtils.markAsPossibleClusterMember(model,
                uncertainSecretaryClintonDoc3, hillaryClintonCluster, 0.5, system);
        AIFUtils.markEdgesAsMutuallyExclusive(model, ImmutableMap.of(secretaryClintonDoc3IsBill, 0.5,
                secretaryClintonDoc3IsHillary, 0.5), system, null);

        // relation that President Obama (of uncertain reference) worked with Secretary Clinton (of uncertain reference)
        // is asserted in document 2
        final Resource relation = AIFUtils.makeRelationInEventForm(model, "http://www.test.edu/relation/1",
                ResourceFactory.createResource(SeedlingOntologyMapper.NAMESPACE_STATIC + "PersonalSocial.Business"),
                ResourceFactory.createResource(SeedlingOntologyMapper.NAMESPACE_STATIC + "PersonalSocial.Business_Person"),
                uncertainPresidentObamaDoc2,
                ResourceFactory.createResource(SeedlingOntologyMapper.NAMESPACE_STATIC + "PersonalSocial.Business_Person"),
                uncertainSecretaryClintonDoc2,
                "http://www.test.edu/relationType/1",
                system,
                0.75);
        // mark justification "President Obama worked with Secretary Clinton"
        AIFUtils.markTextJustification(model, relation, "doc2", 0, 10, system,
                0.75);

        dumpAndAssertValid(model, "create a relation where both endpoints are ambiguous (NIST way)",
                true);

    }

    /**
     * Another way to represent cross-document coref + relations. This way allows entities to have justifications from
     * multiple documents and only uses clusters when needed to represent coreference uncertainty. This way is not
     * allowable in output intended for NIST to run SPARQL queries over.
     * <p>
     * For reference, search for "DIFFERENCE" to find places where this diverges from the NAIF version.
     */
    @Test
    void relationWhereBothEndpointsAreAmbiguousCrossDocEntitiesVersion() {
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = AIFUtils.makeSystemWithURI(model, "http://www.test.edu/testSystem");

        // let's imagine we have three documents.  We will make TextJustification objects for some entity mentions in
        // them which we will use later (with bogus offsets, because I don't want to bother to count them out).

        // In all the below, we are going to imagine the system is unsure whether "President Obama" is "Barack
        // Obama" or "Michelle Obama" and whether "Secretary Clinton" is "Hillary Clinton" or "Bill Clinton"

        // document 1: [Michelle Obama] was first Lady (married to [Barack Obama]).  [President Obama] was
        // a senator from Chicago.
        final Resource michelleObamaMention = AIFUtils.makeTextJustification(model, "doc1", 0,
                1, system, 1.0);
        final Resource firstLadyMention = AIFUtils.makeTextJustification(model, "doc1", 0,
                1, system, 1.0);
        final Resource barackObamaDoc1Mention = AIFUtils.makeTextJustification(model, "doc1", 0,
                1, system, 1.0);
        final Resource presidentObamaDoc1Mention = AIFUtils.makeTextJustification(model, "doc1", 0,
                1, system, 1.0);

        // DIFFERENCE: notice we don't create a separate set of entities for each document. Instead we will create them
        // below at the corpus level

        // document 2 text: "[Barack Obama] was the 44th president of the United States. [President Obama] was elected
        // in 2008.  [President Obama] worked with [Secretary Clinton].
        final Resource barackObamaDoc2Mention = AIFUtils.makeTextJustification(model, "doc2", 0,
                1, system, 1.0);
        final Resource presidentObamaDoc2Mention1 = AIFUtils.makeTextJustification(model, "doc2", 0,
                1, system, 1.0);
        final Resource presidentObamaDoc2Mention2 = AIFUtils.makeTextJustification(model, "doc2", 0,
                1, system, 1.0);
        final Resource secretaryClintonDoc2Mention = AIFUtils.makeTextJustification(model, "doc2", 0,
                1, system, 1.0);

        // document 3 text:  [Bill Clinton] is married to Hilary Clinton.  [Secretary Clinton] doesn't like hamburgers.
        final Resource billClintonMention = AIFUtils.makeTextJustification(model, "doc3", 0,
                1, system, 1.0);
        final Resource hillaryClintonMention = AIFUtils.makeTextJustification(model, "doc3", 0,
                1, system, 1.0);
        final Resource uncertainSecretaryClintonDoc3Mention = AIFUtils.makeTextJustification(model, "doc3", 0,
                1, system, 1.0);

        // DIFFERENCE: here we make our corpus-level entities

        final Resource michelleObama = AIFUtils.makeEntity(model,
                "http://www.test.edu/entities/michelleObama", system);
        markJustification(michelleObama, michelleObamaMention);
        markJustification(michelleObama, firstLadyMention);

        final Resource barackObama = AIFUtils.makeEntity(model,
                "http://www.test.edu/entities/barackObama", system);
        markJustification(barackObama, barackObamaDoc1Mention);
        markJustification(barackObama, barackObamaDoc2Mention);

        final Resource billClinton = AIFUtils.makeEntity(model,
                "http://www.test.edu/entities/billClinton", system);
        markJustification(billClinton, billClintonMention);

        final Resource hillaryClinton = AIFUtils.makeEntity(model,
                "http://www.test.edu/entities/hillaryClinton", system);
        markJustification(hillaryClinton, hillaryClintonMention);

        // the uncertain "President Obama" gets its own entity, since we aren't sure which other entity it is
        // identical to. Here we are assuming all the "President Obamas" in our little mini-corpus are the same person.
        final Resource presidentObama = AIFUtils.makeEntity(model,
                "http://www.test.edu/entities/presidentObama", system);
        markJustification(presidentObama, presidentObamaDoc1Mention);
        markJustification(presidentObama, presidentObamaDoc2Mention2);
        markJustification(presidentObama, presidentObamaDoc2Mention1);

        // same for "Secretary Clinton"
        final Resource secretaryClinton = AIFUtils.makeEntity(model,
                "http://www.test.edu/entities/secretaryClinton", system);
        markJustification(secretaryClinton, secretaryClintonDoc2Mention);
        markJustification(secretaryClinton, uncertainSecretaryClintonDoc3Mention);

        // mark that all these entities are people
        int typeAssertionIndex = 0;
        for (Resource person : ImmutableList.of(michelleObama, barackObama, presidentObama,
                secretaryClinton, billClinton, hillaryClinton)) {
            markType(model, "http://www.test.edu/type_assertions/" + typeAssertionIndex,
                    person, SeedlingOntologyMapper.PERSON, system, 1.0);
        }

        // in general AIF you only need to use clusters if you need to show coreference uncertainty (which in this
        // case we do for all entities)
        final Resource michelleObamaCluster = AIFUtils.makeClusterWithPrototype(model,
                "http://www.test.edu/cluster/1", michelleObama, system);
        final Resource barackObamaCluster = AIFUtils.makeClusterWithPrototype(model,
                "http://www.test.edu/cluster/2", barackObama, system);
        final Resource billClintonCluster = AIFUtils.makeClusterWithPrototype(model,
                "http://www.test.edu/cluster/3", billClinton, system);
        final Resource hillaryClintonCluster = AIFUtils.makeClusterWithPrototype(model,
                "http://www.test.edu/cluster/4", hillaryClinton, system);

        // mark coref uncertainty for "President Obama" and "Secretary Clinon"
        final Resource presidentObamaIsMichelle = AIFUtils.markAsPossibleClusterMember(model,
                presidentObama, michelleObamaCluster, 0.5, system);
        final Resource presidentObamaIsBarack = AIFUtils.markAsPossibleClusterMember(model,
                presidentObama, barackObamaCluster, 0.5, system);
        AIFUtils.markEdgesAsMutuallyExclusive(model, ImmutableMap.of(presidentObamaIsMichelle, 0.5,
                presidentObamaIsBarack, 0.5), system, null);

        final Resource secretaryClintonIsBill = AIFUtils.markAsPossibleClusterMember(model,
                secretaryClinton, billClintonCluster, 0.5, system);
        final Resource secretaryClintoIsHillary = AIFUtils.markAsPossibleClusterMember(model,
                secretaryClinton, hillaryClintonCluster, 0.5, system);
        AIFUtils.markEdgesAsMutuallyExclusive(model, ImmutableMap.of(secretaryClintonIsBill, 0.5,
                secretaryClintoIsHillary, 0.5), system, null);

        // relation that President Obama (of uncertain reference) worked with Secretary Clinton (of uncertain reference)
        // is asserted in document 2
        final Resource relation = AIFUtils.makeRelationInEventForm(model, "http://www.test.edu/relation/1",
                ResourceFactory.createResource(SeedlingOntologyMapper.NAMESPACE_STATIC + "PersonalSocial.Business"),
                ResourceFactory.createResource(SeedlingOntologyMapper.NAMESPACE_STATIC + "PersonalSocial.Business_Person"),
                presidentObama,
                ResourceFactory.createResource(SeedlingOntologyMapper.NAMESPACE_STATIC + "PersonalSocial.Business_Person"),
                secretaryClinton,
                "http://www.test.edu/relationType/1",
                system,
                0.75);
        // mark justification "President Obama worked with Secretary Clinton"
        AIFUtils.markTextJustification(model, relation, "doc2", 0, 10, system,
                0.75);

        dumpAndAssertValid(model, "create a relation where both endpoints are ambiguous (unrestricted way)",
                true);
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
      final Model model = createModel(false);

      final Resource system = AIFUtils.makeSystemWithURI(model,
          "http://www.test.edu/testSystem");

      final Resource entity = AIFUtils.makeEntity(model, "http://www.test.edu/entities/1",
          system);

      AIFUtils.markType(model, "http://www.test.org/assertions/1",
          // illegal confidence value - not in [0.0, 1.0]
          entity, ColdStartOntologyMapper.PERSON, system, 100.0);

      assertFalse(validatorForColdStart.validateKB(model));
    }

    @Test
    void entityMissingType() {
      // having multiple type assertions in case of uncertainty is ok, but there must always
      // be at least one type assertion
      final Model model = createModel(false);

      final Resource system = AIFUtils.makeSystemWithURI(model,
          "http://www.test.edu/testSystem");

      AIFUtils.makeEntity(model, "http://www.test.edu/entities/1",
          system);
      assertFalse(validatorForColdStart.validateKB(model));
    }

    @Test
    void eventMissingType() {
      // having multiple type assertions in case of uncertainty is ok, but there must always
      // be at least one type assertion
      final Model model = createModel(false);

      final Resource system = AIFUtils.makeSystemWithURI(model,
          "http://www.test.edu/testSystem");

      AIFUtils.makeEvent(model, "http://www.test.edu/events/1",
          system);
      assertFalse(validatorForColdStart.validateKB(model));
    }

    @Test
    void nonTypeUsedAsType() {
      final Model model = createModel(false);

      final Resource system = AIFUtils.makeSystemWithURI(model,
          "http://www.test.edu/testSystem");

      final Resource entity = AIFUtils.makeEntity(model, "http://www.test.edu/entities/1",
          system);
      markType(model, "http://www.test.edu/typeAssertion/1", entity,
          // use a blank node as the bogus entity type
          model.createResource(), system, 1.0);
      assertFalse(validatorForColdStart.validateKB(model));
    }

    @Test
    void relationOfUnknownType() {
      final Model model = createModel(true);

      final Resource system = AIFUtils.makeSystemWithURI(model, "http://www.test.edu/testSystem");
      final ColdStartOntologyMapper ontologyMapping = new ColdStartOntologyMapper();

      final Resource personEntity = AIFUtils
              .makeEntity(model, "http://www.test.edu/entities/1", system);
      AIFUtils.markType(model, "http://www.test.org/assertions/1",
              personEntity, SeedlingOntologyMapper.PERSON, system, 1.0);
      final Resource louisvilleEntity = AIFUtils
              .makeEntity(model, "http://www.test.edu/entities/2", system);
      AIFUtils.markType(model, "http://www.test.org/assertions/1",
              louisvilleEntity, SeedlingOntologyMapper.GPE, system, 1.0);

        String relation = SeedlingOntologyMapper.NAMESPACE_STATIC + "unknown_type";
        makeRelationInEventForm(model, "http://www.test.edu/relations/1", model.createResource(relation),
                ontologyMapping.eventArgumentType(relation + "_person"), personEntity,
                ontologyMapping.eventArgumentType(relation + "_person"), louisvilleEntity,
                getAssertionUri(), system, 1.0);

      assertFalse(seedlingValidator.validateKB(model));
    }

    @Test
    void justificationMissingConfidence() {
      // having multiple type assertions in case of uncertainty is ok, but there must always
      // be at least one type assertion
      final Model model = createModel(false);

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

      assertFalse(validatorForColdStart.validateKB(model));
    }

    // this validation constraint is not working yet
    @Disabled
    @Test
    void missingRdfTypeOnNamedNode() {
      final Model model = createModel(false);

      final Resource system = AIFUtils.makeSystemWithURI(model,
          "http://www.test.edu/testSystem");

      // below we copy the code from AIFUtils.makeEntity but forget to mark it as an entity
      final Resource entity = model.createResource("http://www.test.edu/entity/1");
      entity.addProperty(AidaAnnotationOntology.SYSTEM_PROPERTY, system);
      RDFDataMgr.write(System.out, model, RDFFormat.TURTLE_PRETTY);
      assertFalse(validatorForColdStart.validateKB(model));
    }
  }


  // we dump the test name and the model in Turtle format so that whenever the user
  // runs the tests, they will also get the examples
  private void dumpAndAssertValid(Model model, String testName, boolean seedling) {
    System.out.println("\n\n" + testName + "\n\n");
    RDFDataMgr.write(System.out, model, RDFFormat.TURTLE_PRETTY);
    if (seedling) {
        assertTrue(seedlingValidator.validateKB(model));
    } else {
        assertTrue(validatorForColdStart.validateKB(model));
    }
  }

  private Model createModel(boolean seedling) {
    final Model model = ModelFactory.createDefaultModel();
    // adding namespace prefixes makes the Turtle output more readable
    model.setNsPrefix("rdf", RDF.uri);
    model.setNsPrefix("xsd", XSD.getURI());
    model.setNsPrefix("aida", AidaAnnotationOntology.NAMESPACE);
    if (seedling) {
        model.setNsPrefix("ldcOnt", SeedlingOntologyMapper.NAMESPACE_STATIC);
    } else {
        model.setNsPrefix("coldstart", ColdStartOntologyMapper.NAMESPACE_STATIC);
    }
    model.setNsPrefix("skos", SKOS.uri);
    return model;
  }
}
