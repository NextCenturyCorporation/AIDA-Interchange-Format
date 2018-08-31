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
    private static final String LDC_NS = "https://tac.nist.gov/tracks/SM-KBP/2018/ontologies/LdcAnnotations#";

  @BeforeAll
  static void declutterLogging() {
    // prevent too much logging from obscuring the Turtle examples which will be printed
    ((Logger)org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
  }

  private final ValidateAIF validatorForColdStart = ValidateAIF.createForDomainOntologySource(
          Resources.asCharSource(Resources.getResource("edu/isi/gaia/ColdStartOntology"), Charsets.UTF_8));

  private final ValidateAIF seedlingValidator = ValidateAIF.createForDomainOntologySource(
          Resources.asCharSource(Resources.getResource("edu/isi/gaia/SeedlingOntology"), Charsets.UTF_8));

    private int assertionCount = 1;
    private int entityCount = 1;
    private int clusterCount = 1;

    private static String getUri(String localName) {
        return LDC_NS + localName;
    }

    private String getAssertionUri() {
        return getUri("assertion-" + assertionCount++);
    }

    private String getEntityUri() {
        return getUri("entity-" + entityCount++);
    }

    private String getClusterUri() {
        return getUri("cluster-" + clusterCount++);
    }

    private String getTestSystemUri() {
        return getUri("testSystem");
    }

    @BeforeEach
    void setUp() {
        assertionCount = 1;
        entityCount = 1;
        clusterCount = 1;
    }

@Nested
class ValidExamples {
    private final String putinDocumentEntityUri = getUri("E781167.00398");
    private final String putinResidesDocumentRelationUri = getUri("R779959.00000");
    private final String putinElectedDocumentEventUri = getUri("V779961.00010");
    private final String russiaDocumentEntityUri = getUri("E779954.00004");
    private final String russiaOwnsBukDocumentRelationUri = getUri("R779959.00004");
    private final String ukraineDocumentEntityUri = getUri("E779959.00021");
    private final String ukraineOwnsBukDocumentRelationUri = getUri("R779959.00002");
    private final String bukDocumentEntityUri = getUri("E779954.00005");
    private final String mh17AttackDocumentEventUri = getUri("V779961.00012");
    private final String mh17DocumentEntityUri = getUri("E779961.00032");

    @Test
    void createSeedlingEntityOfTypePersonWithAllJustificationTypesAndConfidence() {
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = makeSystemWithURI(model, getTestSystemUri());

        // it doesn't matter what URI we give entities, events, etc. so long as they are
        // unique
        final Resource putinMentionResource = makeEntity(model, putinDocumentEntityUri, system);

        // in order to allow uncertainty about the type of an entity, we don't mark an
        // entity's type directly on the entity, but rather make a separate assertion for it
        // its URI doesn't matter either
        final Resource typeAssertion = markType(model, getAssertionUri(), putinMentionResource,
                SeedlingOntologyMapper.PERSON, system, 1.0);

        // the justification provides the evidence for our claim about the entity's type
        // we attach this justification to both the type assertion and the entity object
        // itself, since it provides evidence both for the entity's existence and its type.
        // in TA1 -> TA2 communications, we attach confidences at the level of justifications
        markTextJustification(model, ImmutableSet.of(putinMentionResource, typeAssertion),
                "HC000T6IV", 1029, 1033, system, 0.973);

        // let's suppose we also have evidence from an image
        markImageJustification(model, ImmutableSet.of(putinMentionResource, typeAssertion),
                "NYT_ENG_20181231_03",
                new BoundingBox(new Point(123, 45), new Point(167, 98)),
                system, 0.123);

        // and also a video where the entity appears in a keyframe
        markKeyFrameVideoJustification(model, ImmutableSet.of(putinMentionResource, typeAssertion),
                "NYT_ENG_20181231_03", "keyframe ID",
                new BoundingBox(new Point(234, 56), new Point(345, 101)),
                system, 0.234);

        // and also a video where the entity does not appear in a keyframe
        markShotVideoJustification(model, ImmutableSet.of(putinMentionResource, typeAssertion),
                "SOME_VIDEO", "some shot ID", system, 0.487);

        // and even audio!
        markAudioJustification(model, ImmutableSet.of(putinMentionResource, typeAssertion),
                "NYT_ENG_201181231", 4.566, 9.876, system, 0.789);

        // also we can link this entity to something in an external KB
        linkToExternalKB(model, putinMentionResource, "freebase:FOO", system, .398);

        // let's mark our entity with some arbitrary system-private data. You can attach such data
        // to nearly anything
        markPrivateData(model, putinMentionResource, "{ 'privateKey' : 'privateValue' }", system);

        dumpAndAssertValid(model, "create a seedling entity of type person with textual " +
                "justification and confidence", true);
    }

    @Test
    void createSeedlingEntityWithUncertaintyAboutItsType() {
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = makeSystemWithURI(model, getTestSystemUri());

        final Resource entity = makeEntity(model, putinDocumentEntityUri, system);
        final Resource entityIsAPerson = markType(model, getAssertionUri(), entity, SeedlingOntologyMapper.PERSON,
                system, 0.5);
        final Resource entityIsAPoliticalEntity = markType(model, getAssertionUri(), entity,
                SeedlingOntologyMapper.GPE, system, 0.2);

        markTextJustification(model, ImmutableSet.of(entity, entityIsAPerson),
                "HC000T6IV", 1029, 1033, system, 0.973);

        markTextJustification(model, ImmutableSet.of(entity, entityIsAPoliticalEntity),
                "NYT_ENG_201181231", 343, 367, system, 0.3);

        markAsMutuallyExclusive(model, ImmutableMap.of(ImmutableSet.of(entityIsAPerson), 0.5,
                ImmutableSet.of(entityIsAPoliticalEntity), 0.2), system, null);

        dumpAndAssertValid(model, "create a seedling entity with uncertainty about its type", true);
    }

    @Test
    void createARelationBetweenTwoSeedlingEntitiesWhereThereIsUncertaintyAboutIdentityOfOneArgument() {
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = makeSystemWithURI(model, getTestSystemUri());

        final SeedlingOntologyMapper ontologyMapping = new SeedlingOntologyMapper();

        // we want to represent a "city_of_birth" relation for a person, but we aren't sure whether
        // they were born in Louisville or Cambridge
        final Resource personEntity = makeEntity(model, putinDocumentEntityUri, system);
        markType(model, getAssertionUri(), personEntity, SeedlingOntologyMapper.PERSON, system, 1.0);

        // create entities for the two locations
        final Resource russiaDocumentEntity = makeEntity(model, russiaDocumentEntityUri, system);
        markType(model, getAssertionUri(), russiaDocumentEntity, SeedlingOntologyMapper.GPE, system, 1.0);

        final Resource ukraineDocumentEntity = makeEntity(model, russiaDocumentEntityUri, system);
        markType(model, getAssertionUri(), ukraineDocumentEntity, SeedlingOntologyMapper.GPE, system, 1.0);

        // create an entity for the uncertain place of birth
        final Resource uncertainPlaceOfReidenceEntity = makeEntity(model, getEntityUri(), system);

        // whatever this place turns out to refer to, we're sure it's where they live
        String relation = "Physical.Resident";
        makeRelationInEventForm(model, putinResidesDocumentRelationUri,
                ontologyMapping.relationType(relation),
                ontologyMapping.eventArgumentTypeNotLowercase(relation + "_Resident"), personEntity,
                ontologyMapping.eventArgumentTypeNotLowercase(relation + "_Place"), uncertainPlaceOfReidenceEntity,
                getAssertionUri(), system, 1.0);

        // we use clusters to represent uncertainty about identity
        // we make two clusters, one for Russia and one for Ukraine
        final Resource russiaCluster = makeClusterWithPrototype(model, getClusterUri(), russiaDocumentEntity, system);
        final Resource ukraineCluster = makeClusterWithPrototype(model, getClusterUri(), ukraineDocumentEntity, system);

        // the uncertain place of birth is either Louisville or Cambridge
        final Resource placeOfResidenceInRussiaCluster = markAsPossibleClusterMember(model,
                uncertainPlaceOfReidenceEntity, russiaCluster, 0.4, system);
        final Resource placeOfResidenceInUkraineCluster = markAsPossibleClusterMember(model,
                uncertainPlaceOfReidenceEntity, ukraineCluster, 0.6, system);

        // but not both
        markAsMutuallyExclusive(model, ImmutableMap.of(
                ImmutableSet.of(placeOfResidenceInUkraineCluster), 0.4,
                ImmutableSet.of(placeOfResidenceInRussiaCluster), 0.6),
                system, null);

        dumpAndAssertValid(model, "create a relation between two seedling entities where there"
                + "is uncertainty about identity of one argument", true);
    }

    @Test
    void createSeedlingEvent() {
        final SeedlingOntologyMapper ontologyMapping = new SeedlingOntologyMapper();

        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = makeSystemWithURI(model, getTestSystemUri());

        // we make a resource for the event itself
        // mark the event as a Personnel.Elect event; type is encoded separately so we can express
        // uncertainty about type
        String eventTypeString = "Personnel.Elect";
        final Resource event = makeEvent(model, putinElectedDocumentEventUri, system);
        markType(model, getAssertionUri(), event, ontologyMapping.eventType(eventTypeString), system, 1.0);

        // create the two entities involved in the event
        final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
        markType(model, getAssertionUri(), putin, SeedlingOntologyMapper.PERSON, system, 1.0);

        final Resource russia = makeEntity(model, russiaDocumentEntityUri, system);
        markType(model, getAssertionUri(), russia, SeedlingOntologyMapper.GPE, system, 1.0);

        // link those entities to the event
        markAsArgument(model, event,
                ontologyMapping.eventArgumentTypeNotLowercase(eventTypeString + "_Elect"),
                putin, system, 0.785);
        markAsArgument(model, event,
                ontologyMapping.eventArgumentTypeNotLowercase(eventTypeString + "_Place"),
                russia, system, 0.589);

        dumpAndAssertValid(model, "create a seedling event", true);
    }

    /**
     * Same as createSeedlingEvent above, except with event argument URI's
     */
    @Test
    void createSeedlingEventWithEventArgumentURI() {
        final SeedlingOntologyMapper ontologyMapping = new SeedlingOntologyMapper();

        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = makeSystemWithURI(model, getTestSystemUri());

        // we make a resource for the event itself
        // mark the event as a Personnel.Elect event; type is encoded separately so we can express
        // uncertainty about type
        String eventTypeString = "Personnel.Elect";
        final Resource event = makeEvent(model, putinElectedDocumentEventUri, system);
        markType(model, getAssertionUri(), event, ontologyMapping.eventType(eventTypeString),
                system, 1.0);

        // create the two entities involved in the event
        final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
        markType(model, getAssertionUri(), putin, SeedlingOntologyMapper.PERSON, system, 1.0);

        final Resource russia = makeEntity(model, russiaDocumentEntityUri, system);
        markType(model, getAssertionUri(), russia, SeedlingOntologyMapper.GPE, system, 1.0);

        // link those entities to the event
        markAsArgument(model, event,
                ontologyMapping.eventArgumentTypeNotLowercase(eventTypeString + "_Elect"),
                putin, system, 0.785, getUri("eventArgument-1"));
        markAsArgument(model, event,
                ontologyMapping.eventArgumentTypeNotLowercase(eventTypeString + "_Place"),
                russia, system, 0.589, getUri("eventArgument-2"));

        dumpAndAssertValid(model, "create a seedling event with event assertion URI", true);
    }

    @Test
    void useSubgraphConfidencesToShowMutuallyExclusiveLinkedSeedlingEventArgumentOptions() {
        // we want to say that either Ukraine or Russia attacked MH17, but we aren't sure which
        final SeedlingOntologyMapper ontologyMapping = new SeedlingOntologyMapper();
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = makeSystemWithURI(model, getTestSystemUri());

        // we make a resource for the event itself
        String eventTypeString = "Conflict.Attack";
        final Resource event = makeEvent(model, mh17AttackDocumentEventUri, system);

        // mark the event as a Personnel.Elect event; type is encoded separately so we can express
        // uncertainty about type
        // NOTE: mapper keys use '.' separator but produce correct seedling output
        markType(model, getAssertionUri(), event, ontologyMapping.eventType(eventTypeString),
                system, 1.0);

        // create the entities involved in the event
        final Resource ukraine = makeEntity(model, ukraineDocumentEntityUri, system);
        markType(model, getAssertionUri(), ukraine, SeedlingOntologyMapper.GPE, system, 1.0);

        final Resource russia = makeEntity(model, russiaDocumentEntityUri, system);
        markType(model, getAssertionUri(), russia, SeedlingOntologyMapper.GPE, system, 1.0);

        final Resource mh17 = makeEntity(model, mh17DocumentEntityUri, system);
        markType(model, getAssertionUri(), mh17, SeedlingOntologyMapper.VEHICLE, system, 1.0);

        String attackerString = eventTypeString + "_Attacker";
        String targetString = eventTypeString + "_Target";

        // we link all possible argument fillers to the event
        final ImmutableSet<Resource> ukraineAttackedMH17 = ImmutableSet.of(
                markAsArgument(model, event,
                        ontologyMapping.eventArgumentTypeNotLowercase(attackerString), ukraine, system, null),
                markAsArgument(model, event,
                        ontologyMapping.eventArgumentTypeNotLowercase(targetString), mh17, system, null));

        final ImmutableSet<Resource> russiaAttackedMH17 = ImmutableSet.of(
                markAsArgument(model, event,
                        ontologyMapping.eventArgumentTypeNotLowercase(attackerString), russia, system, null),
                markAsArgument(model, event,
                        ontologyMapping.eventArgumentTypeNotLowercase(targetString), mh17, system, null));

        // then we mark these as mutually exclusive
        // we also mark confidence 0.2 that neither of these are true
        markAsMutuallyExclusive(model, ImmutableMap.of(ukraineAttackedMH17, 0.6,
                russiaAttackedMH17, 0.2), system, 0.2);

        dumpAndAssertValid(model, "seedling sub-graph confidences", true);
    }

    @Test
    void twoSeedlingHypotheses() {
        final SeedlingOntologyMapper ontologyMapping = new SeedlingOntologyMapper();

        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = makeSystemWithURI(model, getTestSystemUri());

        // we want to represent that we know, regardless of hypothesis, that there is a BUK missile launcher,
        // a plane MH17, two countries (Russia and Ukraine), and the BUK missile launcher was used to attack MH17
        final Resource buk = makeEntity(model, bukDocumentEntityUri, system);
        markType(model, getAssertionUri(), buk, SeedlingOntologyMapper.WEAPON, system, 1.0);

        final Resource mh17 = makeEntity(model, mh17DocumentEntityUri, system);
        markType(model, getAssertionUri(), buk, SeedlingOntologyMapper.VEHICLE, system, 1.0);

        final Resource russia = makeEntity(model, russiaDocumentEntityUri, system);
        markType(model, getAssertionUri(), russia, SeedlingOntologyMapper.GPE, system, 1.0);

        final Resource ukraine = makeEntity(model, ukraineDocumentEntityUri, system);
        markType(model, getAssertionUri(), ukraine, SeedlingOntologyMapper.GPE, system, 1.0);

        String eventTypeString = "Conflict.Attack";
        String targetString = eventTypeString + "_Target";
        String instrumentString = eventTypeString + "_Instrument";
        final Resource attackOnMH17 = makeEvent(model, mh17AttackDocumentEventUri, system);
        markType(model, getAssertionUri(), attackOnMH17, ontologyMapping.eventType(eventTypeString),
                system, 1.0);
        markAsArgument(model, attackOnMH17, ontologyMapping.eventArgumentTypeNotLowercase(targetString),
                mh17, system, null);
        markAsArgument(model, attackOnMH17, ontologyMapping.eventArgumentTypeNotLowercase(instrumentString),
                buk, system, null);

        final Resource isAttacker = ontologyMapping.eventArgumentType(eventTypeString + "_Attacker");
        String affiliationRelationString = "GeneralAffiliation.APORA";
        String affiliationRelationSubject = affiliationRelationString + "_Affiliate";
        String affiliationRelationObject = affiliationRelationString + "_Affiliation";

        // under the background hypothesis that the BUK is Russian, we believe Russia attacked MH17
        final Resource bukIsRussian = makeRelationInEventForm(model, russiaOwnsBukDocumentRelationUri,
                ontologyMapping.relationType(affiliationRelationString),
                ontologyMapping.eventArgumentTypeNotLowercase(affiliationRelationSubject), buk,
                ontologyMapping.eventArgumentTypeNotLowercase(affiliationRelationObject), russia,
                getAssertionUri(), system, 1.0);

        final Resource bukIsRussianHypothesis = makeHypothesis(model, getUri("hypothesis-1"),
                ImmutableSet.of(bukIsRussian), system);
        final Resource russiaShotMH17 = markAsArgument(model, attackOnMH17, isAttacker, russia, system, 1.0);
        markDependsOnHypothesis(russiaShotMH17, bukIsRussianHypothesis);

        // under the background hypothesis that BUK is Ukrainian, we believe Ukraine attacked MH17
        final Resource bukIsUkrainian = makeRelationInEventForm(model, ukraineOwnsBukDocumentRelationUri,
                ontologyMapping.relationType(affiliationRelationString),
                ontologyMapping.eventArgumentTypeNotLowercase(affiliationRelationSubject), buk,
                ontologyMapping.eventArgumentTypeNotLowercase(affiliationRelationObject), ukraine,
                getAssertionUri(), system, 1.0);

        final Resource bukIsUkranianHypothesis = makeHypothesis(model, getUri("hypothesis-2"),
                ImmutableSet.of(bukIsUkrainian), system);
        final Resource ukraineShotMH17 = markAsArgument(model, attackOnMH17, isAttacker, russia, system, 1.0);
        markDependsOnHypothesis(ukraineShotMH17, bukIsUkranianHypothesis);

        dumpAndAssertValid(model, "two seedling hypotheses", true);
    }

    @Test
    void createSeedlingEntityOfTypePersonWithImageJustificationAndVector() {
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = makeSystemWithURI(model, getTestSystemUri());

        // it doesn't matter what URI we give entities, events, etc. so long as they are unique
        final Resource putin = makeEntity(model, putinDocumentEntityUri, system);

        // in order to allow uncertainty about the type of an entity, we don't mark an
        // entity's type directly on the entity, but rather make a separate assertion for it
        // its URI doesn't matter either
        final Resource typeAssertion = markType(model, getAssertionUri(), putin, SeedlingOntologyMapper.PERSON,
                system, 1.0);

        // the justification provides the evidence for our claim about the entity's type
        // we attach this justification to both the type assertion and the entity object
        // itself, since it provides evidence both for the entity's existence and its type.
        // in TA1 -> TA2 communications, we attach confidences at the level of justifications

        // let's suppose we have evidence from an image
        AIFUtils.markImageJustification(model, ImmutableSet.of(putin, typeAssertion),
                "NYT_ENG_20181231_03",
                new BoundingBox(new Point(123, 45), new Point(167, 98)),
                system, 0.123);

        // let's mark our entity with some arbitrary system-private data. You can attach such data
        // to nearly anything
        markPrivateData(model, putin, getUri("testSystem-personVector"),
                Arrays.asList(2.0, 7.5, 0.2, 8.1), system);

        dumpAndAssertValid(model, "create a seedling entity of type person with image " +
                "justification and vector", true);
    }

    @Test
    void createSeedlingEntityWithAlternateNames() {
        final Model model = createModel(true);

        final Resource system = makeSystemWithURI(model, getTestSystemUri());

        // assign alternate names to the putin entity
        final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
        markType(model, getAssertionUri(), putin, SeedlingOntologyMapper.PERSON, system, 1.0);
        markName(putin, "Путина");
        markName(putin, "Владимира Путина");


        final Resource results = makeEntity(model, getUri("E966733.00068"), system);
        markType(model, getAssertionUri(), results, SeedlingOntologyMapper.PERSON, system, 1.0);
        markTextValue(results, "проти 10,19%");

        final Resource value = makeEntity(model, getUri("E831667.00871"), system);
        markType(model, getAssertionUri(), value, SeedlingOntologyMapper.NUMERICAL_VALUE, system, 1.0);
        markNumericValueAsDouble(value, 16.0);
        markNumericValueAsLong(value, 16);
        markNumericValueAsString(value, "на висоті менше 16 кілометрів");
        markNumericValueAsString(value, "at a height less than 16 kilometers");

        dumpAndAssertValid(model, "create a seedling entity of type person with names", true);
    }

    @Test
    void createCompoundJustification() {
        final Model model = createModel(true);

        final Resource system = makeSystemWithURI(model, getTestSystemUri());

        final Resource entity = makeEntity(model, putinDocumentEntityUri, system);

        // in order to allow uncertainty about the type of an entity, we don't mark an
        // entity's type directly on the entity, but rather make a separate assertion for it
        // its URI doesn't matter either
        final Resource typeAssertion = markType(model, getAssertionUri(),
                entity, SeedlingOntologyMapper.PERSON, system, 1.0);

        // the justification provides the evidence for our claim about the entity's type
        // we attach this justification to both the type assertion and the entity object
        // itself, since it provides evidence both for the entity's existence and its type.
        // in TA1 -> TA2 communications, we attach confidences at the level of justifications
        final Resource textJustification = makeTextJustification(model, "NYT_ENG_20181231",
                42, 143, system, 0.973);

        // let's suppose we also have evidence from an image
        final Resource imageJustification = makeImageJustification(model, "NYT_ENG_20181231_03",
                new BoundingBox(new Point(123, 45), new Point(167, 98)), system, 0.123);

        // and also a video where the entity appears in a keyframe
        final Resource keyFrameVideoJustification = makeKeyFrameVideoJustification(model,
                "NYT_ENG_20181231_03", "keyframe ID",
                new BoundingBox(new Point(234, 56), new Point(345, 101)), system, 0.234);

        // and also a video where the entity does not appear in a keyframe
        final Resource shotVideoJustification = makeShotVideoJustification(model, "SOME_VIDEO",
                "some shot ID", system, 0.487);

        // and even audio!
        final Resource audioJustification = makeAudioJustification(model, "NYT_ENG_201181231",
                4.566, 9.876, system, 0.789);

        // combine all jutifications into single justifiedBy triple with new confidence
        markCompoundJustification(model, ImmutableSet.of(entity),
                ImmutableSet.of(textJustification, imageJustification, keyFrameVideoJustification,
                        shotVideoJustification, audioJustification), system, 0.321);

        dumpAndAssertValid(model, "create a compound justification", true);
    }

    @Test
    void createHierarchicalCluster() {
        // we want to say that the cluster of Trump entities might be the same as the cluster of the president entities
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = makeSystemWithURI(model, getTestSystemUri());

        // create president entities
        final Resource presidentUSA = makeEntity(model, getEntityUri(), system);
        markType(model, getAssertionUri(), presidentUSA, SeedlingOntologyMapper.GPE, system, 1.0);
        markName(presidentUSA, "the president");

        final Resource newPresident = makeEntity(model, getEntityUri(), system);
        markType(model, getAssertionUri(), presidentUSA, SeedlingOntologyMapper.GPE, system, 1.0);
        markName(presidentUSA, "the newly-inaugurated president");

        final Resource president45 = makeEntity(model, getEntityUri(), system);
        markType(model, getAssertionUri(), presidentUSA, SeedlingOntologyMapper.GPE, system, 1.0);
        markName(presidentUSA, "the 45th president");

        // cluster president entities
        final Resource presidentCluster = makeClusterWithPrototype(model, getClusterUri(), presidentUSA, system);
        // TODO: verify. Seems redundant
        markAsPossibleClusterMember(model, presidentUSA, presidentCluster, 1, system);
        markAsPossibleClusterMember(model, newPresident, presidentCluster, .9, system);
        markAsPossibleClusterMember(model, president45, presidentCluster, .9, system);

        // create Trump entities
        final Resource donaldTrump = makeEntity(model, getEntityUri(), system);
        markType(model, getAssertionUri(), presidentUSA, SeedlingOntologyMapper.PERSON, system, 1.0);
        markName(presidentUSA, "Donald Trump");

        final Resource trump = makeEntity(model, getEntityUri(), system);
        markType(model, getAssertionUri(), presidentUSA, SeedlingOntologyMapper.PERSON, system, 1.0);
        markName(presidentUSA, "Trump");

        // cluster trump entities
        final Resource trumpCluster = makeClusterWithPrototype(model, getClusterUri(), donaldTrump, system);
        // TODO: verify. Seems redundant
        markAsPossibleClusterMember(model, donaldTrump, trumpCluster, 1, system);
        markAsPossibleClusterMember(model, trump, trumpCluster, .9, system);

        // mark president cluster as being part of trump cluster
        markAsPossibleClusterMember(model, presidentCluster, trumpCluster, .6, system);

        dumpAndAssertValid(model, "seedling hierarchical cluster", true);
    }

    /**
     * Simplest possible cluster example.  Two entities might be the same thing.
     */
    @Test
    void createASimpleCluster() {
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = makeSystemWithURI(model, getTestSystemUri());

        final SeedlingOntologyMapper ontologyMapping = new SeedlingOntologyMapper();

        // Two people, probably the same person
        final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
        markType(model, getAssertionUri(), putin, SeedlingOntologyMapper.PERSON, system, 1.0);
        markName(putin, "Путин");

        final Resource vladimirPutin = makeEntity(model, getUri("E780885.00311"), system);
        markType(model, getAssertionUri(), vladimirPutin, SeedlingOntologyMapper.PERSON, system, 1.0);
        markName(vladimirPutin, "Vladimir Putin");

        // create a cluster with prototype
        final Resource putinCluster = makeClusterWithPrototype(model, getClusterUri(), putin, system);

        // person 1 is definitely in the cluster, person 2 is probably in the cluster
        markAsPossibleClusterMember(model, putin, putinCluster, 1, system);
        markAsPossibleClusterMember(model, vladimirPutin, putinCluster, 0.71, system);

        dumpAndAssertValid(model, "create a simple cluster", true);
    }

    /**
     * Simplest possible cluster example, plus justification
     */
    @Test
    void createASimpleClusterWithJustification() {
        final Model model = createModel(true);

        // every AIF needs an object for the system responsible for creating it
        final Resource system = makeSystemWithURI(model, getTestSystemUri());

        final SeedlingOntologyMapper ontologyMapping = new SeedlingOntologyMapper();

        // Two people, probably the same person
        final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
        markType(model, getAssertionUri(), putin, SeedlingOntologyMapper.PERSON, system, 1.0);
        markName(putin, "Путин");

        final Resource vladimirPutin = makeEntity(model, getUri("E780885.00311"), system);
        markType(model, getAssertionUri(), vladimirPutin, SeedlingOntologyMapper.PERSON, system, 1.0);
        markName(vladimirPutin, "Vladimir Putin");

        // create a cluster with prototype
        final Resource putinCluster = makeClusterWithPrototype(model, getClusterUri(), putin, system);

        // person 1 is definitely in the cluster, person 2 is probably in the cluster
        markAsPossibleClusterMember(model, putin, putinCluster, 1, system);
        final Resource vladMightBePutin = markAsPossibleClusterMember(model, vladimirPutin, putinCluster,
                0.71, system);

        markTextJustification(model, vladMightBePutin, "NYT_ENG_20181231", 42,
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
        final Resource system = makeSystemWithURI(model, getTestSystemUri());

        // let's imagine we have three documents.  We will make TextJustification objects for some entity mentions in
        // them which we will use later (with bogus offsets, because I don't want to bother to count them out).
        // Since NAIF requires entities to be restricted to justifications from a single document, we go ahead and
        // create our entities now, too.

        // In all the below, we are going to imagine the system is unsure whether "President Obama" is "Barack
        // Obama" or "Michelle Obama" and whether "Secretary Clinton" is "Hillary Clinton" or "Bill Clinton"

        // document 1: [Michelle Obama] was first Lady (married to [Barack Obama]).  [President Obama] was
        // a senator from Chicago.
        final Resource michelleObamaMention = makeTextJustification(model, "doc1", 0,
                1, system, 1.0);
        final Resource firstLadyMention = makeTextJustification(model, "doc1", 0,
                1, system, 1.0);
        final Resource barackObamaDoc1Mention = makeTextJustification(model, "doc1", 0,
                1, system, 1.0);
        final Resource presidentObamaDoc1Mention = makeTextJustification(model, "doc1", 0,
                1, system, 1.0);

        final Resource michelleObamaDoc1 = makeEntity(model, getUri("entity-michelleObamaDoc1"), system);
        markJustification(michelleObamaDoc1, michelleObamaMention);
        markJustification(michelleObamaDoc1, firstLadyMention);

        final Resource barackObamaDoc1 = makeEntity(model, getUri("entity-barackObamaDoc1"), system);
        markJustification(barackObamaDoc1, barackObamaDoc1Mention);

        // the uncertain "President Obama" gets its own entity, since we aren't sure which of the other two it
        // is identical to
        final Resource uncertainPresidentObamaDoc1 = makeEntity(model,
                getUri("entity-uncertainPresidentObamaDoc1"), system);
        markJustification(uncertainPresidentObamaDoc1, presidentObamaDoc1Mention);

        // document 2 text: "[Barack Obama] was the 44th president of the United States. [President Obama] was elected
        // in 2008.  [President Obama] worked with [Secretary Clinton].
        final Resource barackObamaDoc2Mention = makeTextJustification(model, "doc2", 0,
                1, system, 1.0);
        final Resource presidentObamaDoc2Mention1 = makeTextJustification(model, "doc2", 0,
                1, system, 1.0);
        final Resource presidentObamaDoc2Mention2 = makeTextJustification(model, "doc2", 0,
                1, system, 1.0);
        final Resource secretaryClintonDoc2Mention = makeTextJustification(model, "doc2", 0,
                1, system, 1.0);


        final Resource barackObamaDoc2 = makeEntity(model, getUri("entity-barackObamaDoc2"), system);
        markJustification(barackObamaDoc2, barackObamaDoc2Mention);

        final Resource uncertainPresidentObamaDoc2 = makeEntity(model,
                getUri("entity-uncertainPresidentObamaDoc2"), system);
        markJustification(uncertainPresidentObamaDoc2, presidentObamaDoc2Mention1);
        markJustification(uncertainPresidentObamaDoc2, presidentObamaDoc2Mention2);

        final Resource uncertainSecretaryClintonDoc2 = makeEntity(model,
                getUri("entity-uncertainSecretaryClintonDoc2"), system);
        markJustification(uncertainSecretaryClintonDoc2, secretaryClintonDoc2Mention);


        // document 3 text:  [Bill Clinton] is married to Hilary Clinton.  [Secretary Clinton] doesn't like hamburgers.
        final Resource billClintonMention = makeTextJustification(model, "doc3", 0,
                1, system, 1.0);
        final Resource hillaryClintonMention = makeTextJustification(model, "doc3", 0,
                1, system, 1.0);
        final Resource uncertainSecretaryClintonDoc3Mention = makeTextJustification(model, "doc3", 0,
                1, system, 1.0);

        final Resource billClintonDoc3 = makeEntity(model, getUri("entity-billClintonDoc3"), system);
        markJustification(billClintonDoc3, billClintonMention);

        final Resource hillaryClintonDoc3 = makeEntity(model, getUri("entity-hillaryClintonDoc3"), system);
        markJustification(hillaryClintonDoc3, hillaryClintonMention);

        final Resource uncertainSecretaryClintonDoc3 = makeEntity(model,
                getUri("entity-uncertainSecretaryClintonDoc3"), system);
        markJustification(uncertainSecretaryClintonDoc3, uncertainSecretaryClintonDoc3Mention);

        // mark that all these entities are people
        for (Resource person : ImmutableList.of(michelleObamaDoc1, barackObamaDoc1, uncertainPresidentObamaDoc1,
                barackObamaDoc2, uncertainPresidentObamaDoc2, uncertainSecretaryClintonDoc2,
                billClintonDoc3, hillaryClintonDoc3, uncertainSecretaryClintonDoc3)) {
            markType(model, getAssertionUri(), person, SeedlingOntologyMapper.PERSON, system, 1.0);
        }

        // in NAIF, all cross-document linking is done via clusters and every entity must belong to some cluster
        final Resource michelleObamaCluster = makeClusterWithPrototype(model, getClusterUri(), michelleObamaDoc1, system);
        final Resource barackObamaCluster = makeClusterWithPrototype(model, getClusterUri(), barackObamaDoc1, system);
        final Resource billClintonCluster = makeClusterWithPrototype(model, getClusterUri(), billClintonDoc3, system);
        final Resource hillaryClintonCluster = makeClusterWithPrototype(model,
                getClusterUri(), hillaryClintonDoc3, system);

        // There are also some entities whose reference to is ambiguous. They belong to multiple clusters.
        final Resource presidentObamaDoc1IsMichelle = markAsPossibleClusterMember(model,
                uncertainPresidentObamaDoc1, michelleObamaCluster, 0.5, system);
        final Resource presidentObamaDoc1IsBarack = markAsPossibleClusterMember(model,
                uncertainPresidentObamaDoc1, barackObamaCluster, 0.5, system);
        markEdgesAsMutuallyExclusive(model, ImmutableMap.of(presidentObamaDoc1IsMichelle, 0.5,
                presidentObamaDoc1IsBarack, 0.5), system, null);

        final Resource presidentObamaDoc2IsMichelle = markAsPossibleClusterMember(model,
                uncertainPresidentObamaDoc2, michelleObamaCluster, 0.5, system);
        final Resource presidentObamaDoc2IsBarack = markAsPossibleClusterMember(model,
                uncertainPresidentObamaDoc2, barackObamaCluster, 0.5, system);
        markEdgesAsMutuallyExclusive(model, ImmutableMap.of(presidentObamaDoc2IsMichelle, 0.5,
                presidentObamaDoc2IsBarack, 0.5), system, null);

        final Resource secretaryClintonDoc2IsBill = markAsPossibleClusterMember(model,
                uncertainSecretaryClintonDoc2, billClintonCluster, 0.5, system);
        final Resource secretaryClintonDoc2IsHillary = markAsPossibleClusterMember(model,
                uncertainSecretaryClintonDoc2, hillaryClintonCluster, 0.5, system);
        markEdgesAsMutuallyExclusive(model, ImmutableMap.of(secretaryClintonDoc2IsBill, 0.5,
                secretaryClintonDoc2IsHillary, 0.5), system, null);

        final Resource secretaryClintonDoc3IsBill = markAsPossibleClusterMember(model,
                uncertainSecretaryClintonDoc3, billClintonCluster, 0.5, system);
        final Resource secretaryClintonDoc3IsHillary = markAsPossibleClusterMember(model,
                uncertainSecretaryClintonDoc3, hillaryClintonCluster, 0.5, system);
        markEdgesAsMutuallyExclusive(model, ImmutableMap.of(secretaryClintonDoc3IsBill, 0.5,
                secretaryClintonDoc3IsHillary, 0.5), system, null);

        // relation that President Obama (of uncertain reference) worked with Secretary Clinton (of uncertain reference)
        // is asserted in document 2
        final Resource relation = makeRelationInEventForm(model, getUri("relation-1"),
                ResourceFactory.createResource(SeedlingOntologyMapper.NAMESPACE_STATIC + "PersonalSocial.Business"),
                ResourceFactory.createResource(SeedlingOntologyMapper.NAMESPACE_STATIC + "PersonalSocial.Business_Person"),
                uncertainPresidentObamaDoc2,
                ResourceFactory.createResource(SeedlingOntologyMapper.NAMESPACE_STATIC + "PersonalSocial.Business_Person"),
                uncertainSecretaryClintonDoc2, getAssertionUri(), system, 0.75);
        // mark justification "President Obama worked with Secretary Clinton"
        markTextJustification(model, relation, "doc2", 0, 10, system,
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
        final Resource system = makeSystemWithURI(model, getTestSystemUri());

        // let's imagine we have three documents.  We will make TextJustification objects for some entity mentions in
        // them which we will use later (with bogus offsets, because I don't want to bother to count them out).

        // In all the below, we are going to imagine the system is unsure whether "President Obama" is "Barack
        // Obama" or "Michelle Obama" and whether "Secretary Clinton" is "Hillary Clinton" or "Bill Clinton"

        // document 1: [Michelle Obama] was first Lady (married to [Barack Obama]).  [President Obama] was
        // a senator from Chicago.
        final Resource michelleObamaMention = makeTextJustification(model, "doc1", 0,
                1, system, 1.0);
        final Resource firstLadyMention = makeTextJustification(model, "doc1", 0,
                1, system, 1.0);
        final Resource barackObamaDoc1Mention = makeTextJustification(model, "doc1", 0,
                1, system, 1.0);
        final Resource presidentObamaDoc1Mention = makeTextJustification(model, "doc1", 0,
                1, system, 1.0);

        // DIFFERENCE: notice we don't create a separate set of entities for each document. Instead we will create them
        // below at the corpus level

        // document 2 text: "[Barack Obama] was the 44th president of the United States. [President Obama] was elected
        // in 2008.  [President Obama] worked with [Secretary Clinton].
        final Resource barackObamaDoc2Mention = makeTextJustification(model, "doc2", 0,
                1, system, 1.0);
        final Resource presidentObamaDoc2Mention1 = makeTextJustification(model, "doc2", 0,
                1, system, 1.0);
        final Resource presidentObamaDoc2Mention2 = makeTextJustification(model, "doc2", 0,
                1, system, 1.0);
        final Resource secretaryClintonDoc2Mention = makeTextJustification(model, "doc2", 0,
                1, system, 1.0);

        // document 3 text:  [Bill Clinton] is married to Hilary Clinton.  [Secretary Clinton] doesn't like hamburgers.
        final Resource billClintonMention = makeTextJustification(model, "doc3", 0,
                1, system, 1.0);
        final Resource hillaryClintonMention = makeTextJustification(model, "doc3", 0,
                1, system, 1.0);
        final Resource uncertainSecretaryClintonDoc3Mention = makeTextJustification(model, "doc3", 0,
                1, system, 1.0);

        // DIFFERENCE: here we make our corpus-level entities

        final Resource michelleObama = makeEntity(model, getUri("entity-michelleObama"), system);
        markJustification(michelleObama, michelleObamaMention);
        markJustification(michelleObama, firstLadyMention);

        final Resource barackObama = makeEntity(model, getUri("entity-barackObama"), system);
        markJustification(barackObama, barackObamaDoc1Mention);
        markJustification(barackObama, barackObamaDoc2Mention);

        final Resource billClinton = makeEntity(model, getUri("entity-billClinton"), system);
        markJustification(billClinton, billClintonMention);

        final Resource hillaryClinton = makeEntity(model, getUri("entity-hillaryClinton"), system);
        markJustification(hillaryClinton, hillaryClintonMention);

        // the uncertain "President Obama" gets its own entity, since we aren't sure which other entity it is
        // identical to. Here we are assuming all the "President Obamas" in our little mini-corpus are the same person.
        final Resource presidentObama = makeEntity(model, getUri("entity-presidentObama"), system);
        markJustification(presidentObama, presidentObamaDoc1Mention);
        markJustification(presidentObama, presidentObamaDoc2Mention2);
        markJustification(presidentObama, presidentObamaDoc2Mention1);

        // same for "Secretary Clinton"
        final Resource secretaryClinton = makeEntity(model, getUri("entity-secretaryClinton"), system);
        markJustification(secretaryClinton, secretaryClintonDoc2Mention);
        markJustification(secretaryClinton, uncertainSecretaryClintonDoc3Mention);

        // mark that all these entities are people
        for (Resource person : ImmutableList.of(michelleObama, barackObama, presidentObama,
                secretaryClinton, billClinton, hillaryClinton)) {
            markType(model, getAssertionUri(), person, SeedlingOntologyMapper.PERSON, system, 1.0);
        }

        // in general AIF you only need to use clusters if you need to show coreference uncertainty (which in this
        // case we do for all entities)
        final Resource michelleObamaCluster = makeClusterWithPrototype(model, getClusterUri(), michelleObama, system);
        final Resource barackObamaCluster = makeClusterWithPrototype(model, getClusterUri(), barackObama, system);
        final Resource billClintonCluster = makeClusterWithPrototype(model, getClusterUri(), billClinton, system);
        final Resource hillaryClintonCluster = makeClusterWithPrototype(model, getClusterUri(), hillaryClinton, system);

        // mark coref uncertainty for "President Obama" and "Secretary Clinon"
        final Resource presidentObamaIsMichelle = markAsPossibleClusterMember(model,
                presidentObama, michelleObamaCluster, 0.5, system);
        final Resource presidentObamaIsBarack = markAsPossibleClusterMember(model,
                presidentObama, barackObamaCluster, 0.5, system);
        markEdgesAsMutuallyExclusive(model, ImmutableMap.of(presidentObamaIsMichelle, 0.5,
                presidentObamaIsBarack, 0.5), system, null);

        final Resource secretaryClintonIsBill = markAsPossibleClusterMember(model,
                secretaryClinton, billClintonCluster, 0.5, system);
        final Resource secretaryClintoIsHillary = markAsPossibleClusterMember(model,
                secretaryClinton, hillaryClintonCluster, 0.5, system);
        markEdgesAsMutuallyExclusive(model, ImmutableMap.of(secretaryClintonIsBill, 0.5,
                secretaryClintoIsHillary, 0.5), system, null);

        // relation that President Obama (of uncertain reference) worked with Secretary Clinton (of uncertain reference)
        // is asserted in document 2
        final Resource relation = makeRelationInEventForm(model, getUri("relation-1"),
                ResourceFactory.createResource(SeedlingOntologyMapper.NAMESPACE_STATIC + "PersonalSocial.Business"),
                ResourceFactory.createResource(SeedlingOntologyMapper.NAMESPACE_STATIC + "PersonalSocial.Business_Person"),
                presidentObama,
                ResourceFactory.createResource(SeedlingOntologyMapper.NAMESPACE_STATIC + "PersonalSocial.Business_Person"),
                secretaryClinton, getAssertionUri(), system, 0.75);

        // mark justification "President Obama worked with Secretary Clinton"
        markTextJustification(model, relation, "doc2", 0, 10, system,
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
