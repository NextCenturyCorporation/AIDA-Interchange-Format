package com.ncc.aif;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import com.ncc.aif.AIFUtils.*;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.apache.jena.vocabulary.XSD;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import static com.ncc.aif.AIFUtils.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

@TestInstance(Lifecycle.PER_CLASS)
public class ExamplesAndValidationTest {
    // Set this flag to true if attempting to get examples
    private static final boolean FORCE_DUMP = false;

    private static final String LDC_NS = "https://tac.nist.gov/tracks/SM-KBP/2018/LdcAnnotations#";
    private static final String NAMESPACE = "https://tac.nist.gov/tracks/SM-KBP/2018/ontologies/SeedlingOntology#";
    private static final CharSource SEEDLING_ONTOLOGY = Resources.asCharSource(
            Resources.getResource("com/ncc/aif/ontologies/SeedlingOntology"),
            StandardCharsets.UTF_8
    );

    @BeforeAll
    static void declutterLogging() {
        // prevent too much logging from obscuring the Turtle examples which will be printed
        ((Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
    }

    private static final ValidateAIF seedlingValidator =
            ValidateAIF.createForDomainOntologySource(SEEDLING_ONTOLOGY);

    private static final ValidateAIF nistSeedlingValidator =
            ValidateAIF.create(ImmutableSet.of(SEEDLING_ONTOLOGY), true);

    private int assertionCount;
    private int entityCount;
    private int clusterCount;

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
        private final String bukKBEntityUri = getUri("E0084");
        private final String mh17AttackDocumentEventUri = getUri("V779961.00012");
        private final String mh17DocumentEntityUri = getUri("E779961.00032");

        @Test
        void createSeedlingEntityOfTypePersonWithAllJustificationTypesAndConfidence() {
            final Model model = createModel();

            // every AIF needs an object for the system responsible for creating it
            final Resource system = makeSystemWithURI(model, getTestSystemUri());

            // it doesn't matter what URI we give entities, events, etc. so long as they are
            // unique
            final Resource putinMentionResource = makeEntity(model, putinDocumentEntityUri, system);

            // in order to allow uncertainty about the type of an entity, we don't mark an
            // entity's type directly on the entity, but rather make a separate assertion for it
            // its URI doesn't matter either
            final Resource typeAssertion = markType(model, getAssertionUri(), putinMentionResource,
                    SeedlingOntology.Person, system, 1.0);

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

            assertAndDump(model, "create a seedling entity of type person with textual " +
                    "justification and confidence", seedlingValidator, true);
        }

        @Test
        void createSeedlingEntityWithUncertaintyAboutItsType() {
            final Model model = createModel();

            // every AIF needs an object for the system responsible for creating it
            final Resource system = makeSystemWithURI(model, getTestSystemUri());

            final Resource entity = makeEntity(model, putinDocumentEntityUri, system);
            final Resource entityIsAPerson = markType(model, getAssertionUri(), entity, SeedlingOntology.Person,
                    system, 0.5);
            final Resource entityIsAPoliticalEntity = markType(model, getAssertionUri(), entity,
                    SeedlingOntology.GeopoliticalEntity, system, 0.2);

            markTextJustification(model, ImmutableSet.of(entity, entityIsAPerson),
                    "HC000T6IV", 1029, 1033, system, 0.973);

            markTextJustification(model, ImmutableSet.of(entity, entityIsAPoliticalEntity),
                    "NYT_ENG_201181231", 343, 367, system, 0.3);

            markAsMutuallyExclusive(model, ImmutableMap.of(ImmutableSet.of(entityIsAPerson), 0.5,
                    ImmutableSet.of(entityIsAPoliticalEntity), 0.2), system, null);

            assertAndDump(model, "create a seedling entity with uncertainty about its type", seedlingValidator, true);
        }

        @Test
        void createARelationBetweenTwoSeedlingEntitiesWhereThereIsUncertaintyAboutIdentityOfOneArgument() {
            final Model model = createModel();

            // every AIF needs an object for the system responsible for creating it
            final Resource system = makeSystemWithURI(model, getTestSystemUri());

            // we want to represent a "city_of_birth" relation for a person, but we aren't sure whether
            // they were born in Louisville or Cambridge
            final Resource personEntity = makeEntity(model, putinDocumentEntityUri, system);
            markType(model, getAssertionUri(), personEntity, SeedlingOntology.Person, system, 1.0);

            // create entities for the two locations
            final Resource russiaDocumentEntity = makeEntity(model, russiaDocumentEntityUri, system);
            markType(model, getAssertionUri(), russiaDocumentEntity, SeedlingOntology.GeopoliticalEntity, system, 1.0);

            final Resource ukraineDocumentEntity = makeEntity(model, russiaDocumentEntityUri, system);
            markType(model, getAssertionUri(), ukraineDocumentEntity, SeedlingOntology.GeopoliticalEntity, system, 1.0);

            // create an entity for the uncertain place of birth
            final Resource uncertainPlaceOfReidenceEntity = makeEntity(model, getEntityUri(), system);
            markType(model, getAssertionUri(), uncertainPlaceOfReidenceEntity, SeedlingOntology.GeopoliticalEntity, system, 1d);

            // whatever this place turns out to refer to, we're sure it's where they live
            makeRelationInEventForm(model, putinResidesDocumentRelationUri,
                    SeedlingOntology.Physical_Resident,
                    SeedlingOntology.Physical_Resident_Resident, personEntity,
                    SeedlingOntology.Physical_Resident_Place, uncertainPlaceOfReidenceEntity,
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

            assertAndDump(model, "create a relation between two seedling entities where there "
                    + "is uncertainty about identity of one argument", seedlingValidator, true);
        }

        @Test
        void createSeedlingEvent() {

            final Model model = createModel();

            // every AIF needs an object for the system responsible for creating it
            final Resource system = makeSystemWithURI(model, getTestSystemUri());

            // we make a resource for the event itself
            // mark the event as a Personnel.Elect event; type is encoded separately so we can express
            // uncertainty about type
            final Resource event = makeEvent(model, putinElectedDocumentEventUri, system);
            markType(model, getAssertionUri(), event, SeedlingOntology.Personnel_Elect, system, 1.0);

            // create the two entities involved in the event
            final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
            markType(model, getAssertionUri(), putin, SeedlingOntology.Person, system, 1.0);

            final Resource russia = makeEntity(model, russiaDocumentEntityUri, system);
            markType(model, getAssertionUri(), russia, SeedlingOntology.GeopoliticalEntity, system, 1.0);

            // link those entities to the event
            markAsArgument(model, event,
                    SeedlingOntology.Personnel_Elect_Elect,
                    putin, system, 0.785);
            markAsArgument(model, event,
                    SeedlingOntology.Personnel_Elect_Place,
                    russia, system, 0.589);

            assertAndDump(model, "create a seedling event", seedlingValidator, true);
        }

        /**
         * Same as createSeedlingEvent above, except with event argument URI's
         */
        @Test
        void createSeedlingEventWithEventArgumentURI() {

            final Model model = createModel();

            // every AIF needs an object for the system responsible for creating it
            final Resource system = makeSystemWithURI(model, getTestSystemUri());

            // we make a resource for the event itself
            // mark the event as a Personnel.Elect event; type is encoded separately so we can express
            // uncertainty about type
            final Resource event = makeEvent(model, putinElectedDocumentEventUri, system);
            markType(model, getAssertionUri(), event, SeedlingOntology.Personnel_Elect, system, 1.0);

            // create the two entities involved in the event
            final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
            markType(model, getAssertionUri(), putin, SeedlingOntology.Person, system, 1.0);

            final Resource russia = makeEntity(model, russiaDocumentEntityUri, system);
            markType(model, getAssertionUri(), russia, SeedlingOntology.GeopoliticalEntity, system, 1.0);

            // link those entities to the event
            markAsArgument(model, event, SeedlingOntology.Personnel_Elect_Elect,
                    putin, system, 0.785, getUri("eventArgument-1"));
            markAsArgument(model, event, SeedlingOntology.Personnel_Elect_Place,
                    russia, system, 0.589, getUri("eventArgument-2"));

            assertAndDump(model, "create a seedling event with event assertion URI", seedlingValidator, true);
        }

        @Test
        void useSubgraphConfidencesToShowMutuallyExclusiveLinkedSeedlingEventArgumentOptions() {
            // we want to say that either Ukraine or Russia attacked MH17, but we aren't sure which
            final Model model = createModel();

            // every AIF needs an object for the system responsible for creating it
            final Resource system = makeSystemWithURI(model, getTestSystemUri());

            // we make a resource for the event itself
            final Resource event = makeEvent(model, mh17AttackDocumentEventUri, system);

            // mark the event as a Personnel.Elect event; type is encoded separately so we can express
            // uncertainty about type
            // NOTE: mapper keys use '.' separator but produce correct seedling output
            markType(model, getAssertionUri(), event, SeedlingOntology.Conflict_Attack,
                    system, 1.0);

            // create the entities involved in the event
            final Resource ukraine = makeEntity(model, ukraineDocumentEntityUri, system);
            markType(model, getAssertionUri(), ukraine, SeedlingOntology.GeopoliticalEntity, system, 1.0);

            final Resource russia = makeEntity(model, russiaDocumentEntityUri, system);
            markType(model, getAssertionUri(), russia, SeedlingOntology.GeopoliticalEntity, system, 1.0);

            final Resource mh17 = makeEntity(model, mh17DocumentEntityUri, system);
            markType(model, getAssertionUri(), mh17, SeedlingOntology.Vehicle, system, 1.0);

            // we link all possible argument fillers to the event
            final ImmutableSet<Resource> ukraineAttackedMH17 = ImmutableSet.of(
                    markAsArgument(model, event,
                            SeedlingOntology.Conflict_Attack_Attacker, ukraine, system, null),
                    markAsArgument(model, event,
                            SeedlingOntology.Conflict_Attack_Target, mh17, system, null));

            final ImmutableSet<Resource> russiaAttackedMH17 = ImmutableSet.of(
                    markAsArgument(model, event,
                            SeedlingOntology.Conflict_Attack_Attacker, russia, system, null),
                    markAsArgument(model, event,
                            SeedlingOntology.Conflict_Attack_Target, mh17, system, null));

            // then we mark these as mutually exclusive
            // we also mark confidence 0.2 that neither of these are true
            markAsMutuallyExclusive(model, ImmutableMap.of(ukraineAttackedMH17, 0.6,
                    russiaAttackedMH17, 0.2), system, 0.2);

            assertAndDump(model, "seedling sub-graph confidences", seedlingValidator, true);
        }

        @Test
        void twoSeedlingHypotheses() {

            final Model model = createModel();

            // every AIF needs an object for the system responsible for creating it
            final Resource system = makeSystemWithURI(model, getTestSystemUri());

            // we want to represent that we know, regardless of hypothesis, that there is a BUK missile launcher,
            // a plane MH17, two countries (Russia and Ukraine), and the BUK missile launcher was used to attack MH17
            final Resource buk = makeEntity(model, bukDocumentEntityUri, system);
            markType(model, getAssertionUri(), buk, SeedlingOntology.Weapon, system, 1.0);

            final Resource mh17 = makeEntity(model, mh17DocumentEntityUri, system);
            markType(model, getAssertionUri(), mh17, SeedlingOntology.Vehicle, system, 1.0);

            final Resource russia = makeEntity(model, russiaDocumentEntityUri, system);
            markType(model, getAssertionUri(), russia, SeedlingOntology.GeopoliticalEntity, system, 1.0);

            final Resource ukraine = makeEntity(model, ukraineDocumentEntityUri, system);
            markType(model, getAssertionUri(), ukraine, SeedlingOntology.GeopoliticalEntity, system, 1.0);

            final Resource attackOnMH17 = makeEvent(model, mh17AttackDocumentEventUri, system);
            markType(model, getAssertionUri(), attackOnMH17, SeedlingOntology.Conflict_Attack,
                    system, 1.0);
            markAsArgument(model, attackOnMH17, SeedlingOntology.Conflict_Attack_Target,
                    mh17, system, null);
            markAsArgument(model, attackOnMH17, SeedlingOntology.Conflict_Attack_Instrument,
                    buk, system, null);

            final Resource isAttacker = SeedlingOntology.Conflict_Attack_Attacker;

            // under the background hypothesis that the BUK is Russian, we believe Russia attacked MH17
            final Resource bukIsRussian = makeRelationInEventForm(model, russiaOwnsBukDocumentRelationUri,
                    SeedlingOntology.GeneralAffiliation_APORA,
                    SeedlingOntology.GeneralAffiliation_APORA_Affiliate, buk,
                    SeedlingOntology.GeneralAffiliation_APORA_Affiliation, russia,
                    getAssertionUri(), system, 1.0);

            final Resource bukIsRussianHypothesis = makeHypothesis(model, getUri("hypothesis-1"),
                    ImmutableSet.of(bukIsRussian), system);
            final Resource russiaShotMH17 = markAsArgument(model, attackOnMH17, isAttacker, russia, system, 1.0);
            markDependsOnHypothesis(russiaShotMH17, bukIsRussianHypothesis);
            markConfidence(model, bukIsRussianHypothesis, 0.75, system);

            // under the background hypothesis that BUK is Ukrainian, we believe Ukraine attacked MH17
            final Resource bukIsUkrainian = makeRelationInEventForm(model, ukraineOwnsBukDocumentRelationUri,
                    SeedlingOntology.GeneralAffiliation_APORA,
                    SeedlingOntology.GeneralAffiliation_APORA_Affiliate, buk,
                    SeedlingOntology.GeneralAffiliation_APORA_Affiliation, ukraine,
                    getAssertionUri(), system, 1.0);

            final Resource bukIsUkranianHypothesis = makeHypothesis(model, getUri("hypothesis-2"),
                    ImmutableSet.of(bukIsUkrainian), 0.25, system);
            final Resource ukraineShotMH17 = markAsArgument(model, attackOnMH17, isAttacker, russia, system, 1.0);
            markDependsOnHypothesis(ukraineShotMH17, bukIsUkranianHypothesis);

            assertAndDump(model, "two seedling hypotheses", seedlingValidator, true);
        }

        // Create simple hypothesis that the BUK weapon system was owned by Russia
        @Test
        void simpleHypothesisWithCluster() {

            final Model model = createModel();

            // every AIF needs an object for the system responsible for creating it
            final Resource system = makeSystemWithURI(model, getTestSystemUri());

            // buk document entity
            final Resource buk = makeEntity(model, bukDocumentEntityUri, system);
            final Resource bukIsWeapon = markType(model, getAssertionUri(), buk, SeedlingOntology.Weapon,
                    system, 1.0);

            // buk cross-document entity
            final Resource bukKBEntity = makeEntity(model, bukKBEntityUri, system);
            final Resource bukKBIsWeapon = markType(model, getAssertionUri(), bukKBEntity, SeedlingOntology.Weapon,
                    system, 1.0);

            // russia document entity
            final Resource russia = makeEntity(model, russiaDocumentEntityUri, system);
            final Resource russiaIsGPE = markType(model, getAssertionUri(), russia, SeedlingOntology.GeopoliticalEntity,
                    system, 1.0);

            // cluster buk
            final Resource bukCluster = makeClusterWithPrototype(model, getClusterUri(), bukKBEntity, system);
            final Resource bukIsClustered = markAsPossibleClusterMember(model, buk, bukCluster, .9, system);

            // Russia owns buk relation
            final Resource bukIsRussian = makeRelation(model, russiaOwnsBukDocumentRelationUri, system);
            markType(model, getAssertionUri(), bukIsRussian, SeedlingOntology.GeneralAffiliation_APORA,
                    system, 1.0);
            final Resource bukArgument = markAsArgument(model, bukIsRussian,
                    SeedlingOntology.GeneralAffiliation_APORA_Affiliate, buk, system, 1.0);
            final Resource russiaArgument = markAsArgument(model, bukIsRussian,
                    SeedlingOntology.GeneralAffiliation_APORA_Affiliation, russia, system, 1.0);

            // Russia owns buk hypothesis
            final Resource bukIsRussianHypothesis = makeHypothesis(model, getUri("hypothesis-1"),
                    ImmutableSet.of(
                            buk, bukIsWeapon, bukIsClustered,
                            russia, russiaIsGPE,
                            bukIsRussian, bukArgument, russiaArgument
                    ), system);

            assertAndDump(model, "simple hypothesis with cluster", seedlingValidator, true);
        }

        // Create simple hypothesis with an importance value where the BUK weapon system was owned by Russia
        @Test
        void simpleHypothesisWithImportanceWithCluster() {

            final Model model = createModel();

            // every AIF needs an object for the system responsible for creating it
            final Resource system = makeSystemWithURI(model, getTestSystemUri());

            // buk document entity
            final Resource buk = makeEntity(model, bukDocumentEntityUri, system);
            final Resource bukIsWeapon = markType(model, getAssertionUri(), buk, SeedlingOntology.Weapon,
                    system, 1.0);

            // buk cross-document entity
            final Resource bukKBEntity = makeEntity(model, bukKBEntityUri, system);
            final Resource bukKBIsWeapon = markType(model, getAssertionUri(), bukKBEntity, SeedlingOntology.Weapon,
                    system, 1.0);

            // russia document entity
            final Resource russia = makeEntity(model, russiaDocumentEntityUri, system);
            final Resource russiaIsGPE = markType(model, getAssertionUri(), russia, SeedlingOntology.GeopoliticalEntity,
                    system, 1.0);

            // cluster buk
            final Resource bukCluster = makeClusterWithPrototype(model, getClusterUri(), bukKBEntity, system);
            final Resource bukIsClustered = markAsPossibleClusterMember(model, buk, bukCluster, .9, system);
            // add importance of 90
            markImportance(bukCluster, 90);

            // Russia owns buk relation
            final Resource bukIsRussian = makeRelation(model, russiaOwnsBukDocumentRelationUri, system);
            markType(model, getAssertionUri(), bukIsRussian, SeedlingOntology.GeneralAffiliation_APORA,
                    system, 1.0);
            final Resource bukArgument = markAsArgument(model, bukIsRussian,
                    SeedlingOntology.GeneralAffiliation_APORA_Affiliate, buk, system, 1.0);
            final Resource russiaArgument = markAsArgument(model, bukIsRussian,
                    SeedlingOntology.GeneralAffiliation_APORA_Affiliation, russia, system, 1.0);
            // add importance to the statements
            markImportance(bukArgument, 100);
            markImportance(russiaArgument, 125);

            // Russia owns buk hypothesis
            final Resource bukIsRussianHypothesis = makeHypothesis(model, getUri("hypothesis-1"),
                    ImmutableSet.of(
                            buk, bukIsWeapon, bukIsClustered,
                            russia, russiaIsGPE,
                            bukIsRussian, bukArgument, russiaArgument
                    ), system);

            markImportance(bukIsRussianHypothesis, 102); //add importance of 102

            assertAndDump(model, "simple hypothesis with importance with cluster",
                    seedlingValidator, true);
        }

        @Test
        void createSeedlingEntityOfTypePersonWithImageJustificationAndVector() {
            final Model model = createModel();

            // every AIF needs an object for the system responsible for creating it
            final Resource system = makeSystemWithURI(model, getTestSystemUri());

            // it doesn't matter what URI we give entities, events, etc. so long as they are unique
            final Resource putin = makeEntity(model, putinDocumentEntityUri, system);

            // in order to allow uncertainty about the type of an entity, we don't mark an
            // entity's type directly on the entity, but rather make a separate assertion for it
            // its URI doesn't matter either
            final Resource typeAssertion = markType(model, getAssertionUri(), putin, SeedlingOntology.Person,
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
            try {
                markPrivateData(model, putin, getUri("testSystem-personVector"),
                        Arrays.asList(2.0, 7.5, 0.2, 8.1), system);
            } catch (JsonProcessingException jpe) {
                System.err.println("Unable to convert vector data to String " + jpe.getMessage());
                jpe.printStackTrace();
            }

            assertAndDump(model, "create a seedling entity of type person with image " +
                    "justification and vector", seedlingValidator, true);
        }

        @Test
        void createSeedlingEntityWithAlternateNames() {
            final Model model = createModel();

            final Resource system = makeSystemWithURI(model, getTestSystemUri());

            // assign alternate names to the putin entity
            final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
            markType(model, getAssertionUri(), putin, SeedlingOntology.Person, system, 1.0);
            markName(putin, "Путина");
            markName(putin, "Владимира Путина");


            final Resource results = makeEntity(model, getUri("E966733.00068"), system);
            markType(model, getAssertionUri(), results, SeedlingOntology.Results, system, 1.0);
            markTextValue(results, "проти 10,19%");

            final Resource value = makeEntity(model, getUri("E831667.00871"), system);
            markType(model, getAssertionUri(), value, SeedlingOntology.NumericalValue, system, 1.0);
            markNumericValueAsDouble(value, 16.0);
            markNumericValueAsLong(value, (long) 16);
            markNumericValueAsString(value, "на висоті менше 16 кілометрів");
            markNumericValueAsString(value, "at a height less than 16 kilometers");

            assertAndDump(model, "create a seedling entity of type person with names", seedlingValidator, true);
        }

        @Test
        void createCompoundJustification() {
            final Model model = createModel();

            // every AIF needs an object for the system responsible for creating it
            final Resource system = makeSystemWithURI(model, getTestSystemUri());

            // we make a resource for the event itself
            // mark the event as a Personnel.Elect event; type is encoded separately so we can express
            // uncertainty about type
            final Resource event = makeEvent(model, putinElectedDocumentEventUri, system);
            final Resource eventTypeAssertion = markType(model, getAssertionUri(), event,
                    SeedlingOntology.Personnel_Elect, system, 1.0);

            // create the two entities involved in the event
            final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
            final Resource personTypeAssertion = markType(model, getAssertionUri(), putin,
                    SeedlingOntology.Person, system, 1.0);

            final Resource russia = makeEntity(model, russiaDocumentEntityUri, system);
            final Resource gpeTypeAssertion = markType(model, getAssertionUri(), russia,
                    SeedlingOntology.GeopoliticalEntity, system, 1.0);

            // link those entities to the event
            final Resource electeeArgument = markAsArgument(model, event,
                    SeedlingOntology.Personnel_Elect_Elect,
                    putin, system, 0.785, getAssertionUri());
            final Resource placeArgument = markAsArgument(model, event,
                    SeedlingOntology.Personnel_Elect_Place,
                    russia, system, 0.589, getAssertionUri());


            // the justification provides the evidence for our claim about the entity's type
            // we attach this justification to both the type assertion and the entity object
            // itself, since it provides evidence both for the entity's existence and its type.
            // in TA1 -> TA2 communications, we attach confidences at the level of justifications
            final Resource textJustification = makeTextJustification(model, "NYT_ENG_20181231",
                    42, 143, system, 0.973);

            markJustification(personTypeAssertion, textJustification);
            markJustification(putin, textJustification);
            addSourceDocumentToJustification(textJustification, "NYT_PARENT_ENG_20181231_03");

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

            // combine all justifications into single justifiedBy triple with new confidence
            markCompoundJustification(model, ImmutableSet.of(electeeArgument),
                    ImmutableSet.of(textJustification, imageJustification, keyFrameVideoJustification,
                            shotVideoJustification, audioJustification), system, 0.321);

            markCompoundJustification(model, ImmutableSet.of(placeArgument), ImmutableSet.of(textJustification, imageJustification),
                    system, 0.543);

            assertAndDump(model, "create a compound justification", seedlingValidator, true);
        }

        @Test
        void createHierarchicalCluster() {
            // we want to say that the cluster of Trump entities might be the same as the cluster of the president entities
            final Model model = createModel();

            // every AIF needs an object for the system responsible for creating it
            final Resource system = makeSystemWithURI(model, getTestSystemUri());

            // create president entities
            final Resource presidentUSA = makeEntity(model, getEntityUri(), system);
            markType(model, getAssertionUri(), presidentUSA, SeedlingOntology.GeopoliticalEntity, system, 1.0);
            markName(presidentUSA, "the president");

            // clustered entities don't require types
            final Resource newPresident = makeEntity(model, getEntityUri(), system);
            markName(newPresident, "the newly-inaugurated president");

            final Resource president45 = makeEntity(model, getEntityUri(), system);
            markType(model, getAssertionUri(), president45, SeedlingOntology.GeopoliticalEntity, system, 1.0);
            markName(president45, "the 45th president");

            // cluster president entities
            final Resource presidentCluster = makeClusterWithPrototype(model, getClusterUri(), presidentUSA, system);
            markAsPossibleClusterMember(model, newPresident, presidentCluster, .8, system);
            markAsPossibleClusterMember(model, president45, presidentCluster, .7, system);

            // create Trump entities
            final Resource donaldTrump = makeEntity(model, getEntityUri(), system);
            markType(model, getAssertionUri(), donaldTrump, SeedlingOntology.Person, system, .4);
            markName(donaldTrump, "Donald Trump");

            final Resource trump = makeEntity(model, getEntityUri(), system);
            markType(model, getAssertionUri(), trump, SeedlingOntology.Person, system, .5);
            markName(trump, "Trump");

            // cluster trump entities
            final Resource trumpCluster = makeClusterWithPrototype(model, getClusterUri(), donaldTrump, system);
            markAsPossibleClusterMember(model, trump, trumpCluster, .9, system);

            // mark president cluster as being part of trump cluster
            markAsPossibleClusterMember(model, presidentCluster, trumpCluster, .6, system);

            assertAndDump(model, "seedling hierarchical cluster", seedlingValidator, true);
        }

        /**
         * Simplest possible cluster example.  Two entities might be the same thing.
         */
        @Test
        void createASimpleCluster() {
            final Model model = createModel();

            // every AIF needs an object for the system responsible for creating it
            final Resource system = makeSystemWithURI(model, getTestSystemUri());

            // Two people, probably the same person
            final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
            markType(model, getAssertionUri(), putin, SeedlingOntology.Person, system, 1.0);
            markName(putin, "Путин");

            final Resource vladimirPutin = makeEntity(model, getUri("E780885.00311"), system);
            markType(model, getAssertionUri(), vladimirPutin, SeedlingOntology.Person, system, 1.0);
            markName(vladimirPutin, "Vladimir Putin");

            // create a cluster with prototype
            final Resource putinCluster = makeClusterWithPrototype(model, getClusterUri(), putin, system);

            // person 1 is definitely in the cluster, person 2 is probably in the cluster
            markAsPossibleClusterMember(model, putin, putinCluster, 1d, system);
            markAsPossibleClusterMember(model, vladimirPutin, putinCluster, 0.71, system);

            assertAndDump(model, "create a simple cluster", seedlingValidator, true);
        }

        /**
         * Simplest possible cluster example, plus justification
         */
        @Test
        void createASimpleClusterWithJustification() {
            final Model model = createModel();

            // every AIF needs an object for the system responsible for creating it
            final Resource system = makeSystemWithURI(model, getTestSystemUri());

            // Two people, probably the same person
            final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
            markType(model, getAssertionUri(), putin, SeedlingOntology.Person, system, 1.0);
            markName(putin, "Путин");

            final Resource vladimirPutin = makeEntity(model, getUri("E780885.00311"), system);
            markType(model, getAssertionUri(), vladimirPutin, SeedlingOntology.Person, system, 1.0);
            markName(vladimirPutin, "Vladimir Putin");

            // create a cluster with prototype
            final Resource putinCluster = makeClusterWithPrototype(model, getClusterUri(), putin, system);

            // person 1 is definitely in the cluster, person 2 is probably in the cluster
            markAsPossibleClusterMember(model, putin, putinCluster, 1d, system);
            final Resource vladMightBePutin = markAsPossibleClusterMember(model, vladimirPutin, putinCluster,
                    0.71, system);

            markTextJustification(model, vladMightBePutin, "NYT_ENG_20181231", 42,
                    143, system, 0.973);

            assertAndDump(model, "create a simple cluster with justification", seedlingValidator, true);
        }

        /**
         * Simplest possible cluster example, plus handle
         */
        @Test
        void createASimpleClusterWithHandle() {
            final Model model = createModel();

            // every AIF needs an object for the system responsible for creating it
            final Resource system = makeSystemWithURI(model, getTestSystemUri());

            // Two people, probably the same person
            final String vladName = "Vladimir Putin";
            final Resource vladimirPutin = makeEntity(model, getUri("E780885.00311"), system);
            markType(model, getAssertionUri(), vladimirPutin, SeedlingOntology.Person, system, 1.0);
            markName(vladimirPutin, vladName);

            final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
            markType(model, getAssertionUri(), putin, SeedlingOntology.Person, system, 1.0);
            markName(putin, "Путин");

            // create a cluster with prototype
            final Resource putinCluster = makeClusterWithPrototype(model, getClusterUri(), vladimirPutin, vladName, system);

            // person 1 is definitely in the cluster, person 2 is probably in the cluster
            markAsPossibleClusterMember(model, putin, putinCluster, 0.71, system);

            assertAndDump(model, "create a simple cluster with handle", seedlingValidator, true);
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
            final Model model = createModel();

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
                markType(model, getAssertionUri(), person, SeedlingOntology.Person, system, 1.0);
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
                    SeedlingOntology.PersonalSocial_Business,
                    SeedlingOntology.PersonalSocial_Business_Person,
                    uncertainPresidentObamaDoc2,
                    SeedlingOntology.PersonalSocial_Business_Person,
                    uncertainSecretaryClintonDoc2, getAssertionUri(), system, 0.75);
            // mark justification "President Obama worked with Secretary Clinton"
            markTextJustification(model, relation, "doc2", 0, 10, system,
                    0.75);

            assertAndDump(model, "create a relation where both endpoints are ambiguous (NIST way)",
                    seedlingValidator, true);
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
            final Model model = createModel();

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
                markType(model, getAssertionUri(), person, SeedlingOntology.Person, system, 1.0);
            }

            // in general AIF you only need to use clusters if you need to show coreference uncertainty (which in this
            // case we do for all entities)
            final Resource michelleObamaCluster = makeClusterWithPrototype(model, getClusterUri(), michelleObama, system);
            final Resource barackObamaCluster = makeClusterWithPrototype(model, getClusterUri(), barackObama, system);
            final Resource billClintonCluster = makeClusterWithPrototype(model, getClusterUri(), billClinton, system);
            final Resource hillaryClintonCluster = makeClusterWithPrototype(model, getClusterUri(), hillaryClinton, system);

            // mark coref uncertainty for "President Obama" and "Secretary Clinton"
            final Resource presidentObamaIsMichelle = markAsPossibleClusterMember(model,
                    presidentObama, michelleObamaCluster, 0.5, system);
            final Resource presidentObamaIsBarack = markAsPossibleClusterMember(model,
                    presidentObama, barackObamaCluster, 0.5, system);
            markEdgesAsMutuallyExclusive(model, ImmutableMap.of(presidentObamaIsMichelle, 0.5,
                    presidentObamaIsBarack, 0.5), system, null);

            final Resource secretaryClintonIsBill = markAsPossibleClusterMember(model,
                    secretaryClinton, billClintonCluster, 0.5, system);
            final Resource secretaryClintonIsHillary = markAsPossibleClusterMember(model,
                    secretaryClinton, hillaryClintonCluster, 0.5, system);
            markEdgesAsMutuallyExclusive(model, ImmutableMap.of(secretaryClintonIsBill, 0.5,
                    secretaryClintonIsHillary, 0.5), system, null);

            // relation that President Obama (of uncertain reference) worked with Secretary Clinton (of uncertain reference)
            // is asserted in document 2
            final Resource relation = makeRelationInEventForm(model, getUri("relation-1"),
                    SeedlingOntology.PersonalSocial_Business,
                    SeedlingOntology.PersonalSocial_Business_Person,
                    presidentObama,
                    SeedlingOntology.PersonalSocial_Business_Person,
                    secretaryClinton, getAssertionUri(), system, 0.75);

            // mark justification "President Obama worked with Secretary Clinton"
            markTextJustification(model, relation, "doc2", 0, 10, system,
                    0.75);

            assertAndDump(model, "create a relation where both endpoints are ambiguous (unrestricted way)",
                    seedlingValidator, true);
        }

        @Test
        void createEntityWithDiskBaseModelAndWriteOut() {
            final Model model = createDiskBasedModel();

            // every AIF needs an object for the system responsible for creating it
            final Resource system = makeSystemWithURI(model, getTestSystemUri());

            final Resource entity = makeEntity(model, putinDocumentEntityUri, system);
            final Resource entityIsAPerson = markType(model, getAssertionUri(), entity, SeedlingOntology.Person,
                    system, 0.5);
//        final Resource entityIsAPoliticalEntity = markType(model, getAssertionUri(), entity,
//                SeedlingOntology.GeopoliticalEntity, system, 0.2);

            markTextJustification(model, ImmutableSet.of(entityIsAPerson),
                    "HC000T6IV", 1029, 1033, system, 0.973);

            Path filename = writeModelToDisk(model);

            final Model model2 = readModelFromDisk(filename);
            Resource rtest = model2.getResource(putinDocumentEntityUri);
            assertNotNull(rtest, "Entity does not exist");
        }

    }


    /**
     * Don't do what these do!
     * <p>
     * These should fail to validate.
     */
    @Nested
    class InvalidExamples {
        @Test
        void entityMissingType() {
            // having multiple type assertions in case of uncertainty is ok, but there must always
            // be at least one type assertion
            final Model model = createModel();

            final Resource system = AIFUtils.makeSystemWithURI(model,
                    "http://www.test.edu/testSystem");

            AIFUtils.makeEntity(model, "http://www.test.edu/entities/1",
                    system);
            assertAndDump(model, "Invalid: entity with missing type", seedlingValidator, false);
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
            assertAndDump(model, "Invalid: event missing type", seedlingValidator, false);
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
            assertAndDump(model, "Invalid: non-type used as type", seedlingValidator, false);
        }

        @Test
        void relationOfUnknownType() {
            final Model model = createModel();

            final Resource system = AIFUtils.makeSystemWithURI(model, "http://www.test.edu/testSystem");

            final Resource personEntity = makeEntity(model, getEntityUri(), system);
            markType(model, getAssertionUri(), personEntity, SeedlingOntology.Person, system, 1.0);

            final Resource louisvilleEntity = makeEntity(model, getEntityUri(), system);
            markType(model, getAssertionUri(), louisvilleEntity, SeedlingOntology.GeopoliticalEntity, system, 1.0);

            makeRelationInEventForm(model, "http://www.test.edu/relations/1",
                    model.createResource(NAMESPACE + "unknown_type"),
                    SeedlingOntology.Physical_Resident_Resident, personEntity,
                    SeedlingOntology.Physical_Resident_Place, louisvilleEntity,
                    getAssertionUri(), system, 1.0);

            assertAndDump(model, "Invalid: relation of unknown type", seedlingValidator, false);
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
            markType(model, getAssertionUri(), entity, SeedlingOntology.Person, system, 1d);

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

            assertAndDump(model, "Invalid: justification missing confidence", seedlingValidator, false);
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
            assertAndDump(model, "Invalid: missing rdf type", seedlingValidator, false);
        }
    }

    /**
     * Set of tests to show that NIST restrictions pass and fail appropriately
     */
    @Nested
    class NISTExamples {
        Model model;
        Resource system;
        Resource entity;
        Resource relation;
        Resource event;
        Resource entityCluster;
        Resource relationCluster;
        Resource eventCluster;

        void addType(Resource resource, Resource type) {
            markType(model, getAssertionUri(), resource, type, system, 1d);
        }

        @BeforeEach
        void setup() {
            model = createModel();
            system = AIFUtils.makeSystemWithURI(model, getTestSystemUri());
            entity = AIFUtils.makeEntity(model, getEntityUri(), system);
            addType(entity, SeedlingOntology.Person);
            event = AIFUtils.makeEvent(model, getUri("event1"), system);
            addType(event, SeedlingOntology.Conflict_Attack);
            relation = makeRelation(model, getUri("relation1"), system);
            addType(relation, SeedlingOntology.GeneralAffiliation_APORA);
            entityCluster = makeClusterWithPrototype(model, getClusterUri(), entity, system);
            eventCluster = makeClusterWithPrototype(model, getClusterUri(), event, system);
            relationCluster = makeClusterWithPrototype(model, getClusterUri(), relation, system);
        }

        // Each edge justification must be represented uniformly in AIF by
        // aida:CompoundJustification, even if only one span is provided
        // Edges are assumed to be relation and event arguments
        @Nested
        class EdgeJustificationCompound {
            @Test
            void invalid() {
                // test relation
                final Resource relation = makeRelation(model, getUri("relationX"), system);
                addType(relation, SeedlingOntology.GeneralAffiliation_APORA);
                makeClusterWithPrototype(model, getClusterUri(), relation, system);
                final Resource relationEdge = markAsArgument(model, relation,
                        SeedlingOntology.GeneralAffiliation_APORA_Affiliate, entity, system, 1d, getAssertionUri());
                final Resource justification = markTextJustification(model, relationEdge,
                        "source1", 0, 4, system, 1d);

                // test event
                final Resource eventEdge = markAsArgument(model, event, SeedlingOntology.Conflict_Attack_Target,
                        entity, system, 1.0, getAssertionUri());
                markJustification(eventEdge, justification);
                assertAndDump(model, "NIST.invalid: edge justification is compound", nistSeedlingValidator,
                        false);
            }

            @Test
            void valid() {
                // test relation
                final Resource relation = makeRelation(model, getUri("relationX"), system);
                addType(relation, SeedlingOntology.GeneralAffiliation_APORA);
                makeClusterWithPrototype(model, getClusterUri(), relation, system);
                final Resource relationEdge = markAsArgument(model, relation,
                        SeedlingOntology.GeneralAffiliation_APORA_Affiliate, entity, system, 1d);
                final Resource justification1 = makeTextJustification(model, "source1", 0, 4, system, 1d);
                final Resource compound = markCompoundJustification(model,
                        ImmutableSet.of(relationEdge),
                        ImmutableSet.of(justification1),
                        system,
                        1d);

                // test event
                final Resource eventEdge = markAsArgument(model, event, SeedlingOntology.Conflict_Attack_Target, entity, system, 1.0);
                markJustification(eventEdge, compound);

                assertAndDump(model, "NIST.valid: edge justification is compound", nistSeedlingValidator,
                        true);
            }
        }

        // Each edge justification is limited to either one or two spans.
        @Nested
        class EdgeJustificationLimit {
            @Test
            void invalid() {
                // test relation
                final Resource relation = makeRelation(model, getUri("relationX"), system);
                addType(relation, SeedlingOntology.GeneralAffiliation_APORA);
                makeClusterWithPrototype(model, getClusterUri(), relation, system);
                final Resource relationEdge = markAsArgument(model, relation,
                        SeedlingOntology.GeneralAffiliation_APORA_Affiliate, entity, system, 1d, getAssertionUri());
                final Resource justification1 = makeTextJustification(model, "source1", 0, 4, system, 1d);
                final Resource justification2 = makeTextJustification(model, "source1", 10, 14, system, 1d);
                final Resource justification3 = makeTextJustification(model, "source1", 20, 24, system, 1d);
                final Resource compound = markCompoundJustification(model,
                        ImmutableSet.of(relationEdge),
                        ImmutableSet.of(justification1, justification2, justification3),
                        system,
                        1d);
                final Resource emptyCompound = markCompoundJustification(model,
                        ImmutableSet.of(relationEdge),
                        ImmutableSet.of(),
                        system,
                        1d);

                // test event
                final Resource eventEdge = markAsArgument(model, event, SeedlingOntology.Conflict_Attack_Target, entity,
                        system, 1.0, getAssertionUri());
                markJustification(eventEdge, compound);
                markJustification(eventEdge, emptyCompound);

                assertAndDump(model, "NIST.invalid: edge justification contains at most two mentions",
                        nistSeedlingValidator, false);
            }

            @Test
            void valid() {
                // test relation
                final Resource relation = makeRelation(model, getUri("relationX"), system);
                addType(relation, SeedlingOntology.GeneralAffiliation_APORA);
                makeClusterWithPrototype(model, getClusterUri(), relation, system);
                final Resource relationEdge = markAsArgument(model, relation,
                        SeedlingOntology.GeneralAffiliation_APORA_Affiliate, entity, system, 1d);
                final Resource justification1 = makeTextJustification(model, "source1", 0, 4, system, 1d);
                final Resource justification2 = makeTextJustification(model, "source1", 10, 14, system, 1d);
                final Resource compound = markCompoundJustification(model,
                        ImmutableSet.of(relationEdge),
                        ImmutableSet.of(justification1, justification2),
                        system,
                        1d);

                // test event
                final Resource eventEdge = markAsArgument(model, event, SeedlingOntology.Conflict_Attack_Target, entity, system, 1.0);
                markJustification(eventEdge, compound);

                assertAndDump(model, "NIST.valid: edge justification contains at most two mentions",
                        nistSeedlingValidator, true);
            }

            @Test
            void validOneSpan() {
                // test relation
                final Resource relation = makeRelation(model, getUri("relationX"), system);
                addType(relation, SeedlingOntology.GeneralAffiliation_APORA);
                makeClusterWithPrototype(model, getClusterUri(), relation, system);
                final Resource relationEdge = markAsArgument(model, relation,
                        SeedlingOntology.GeneralAffiliation_APORA_Affiliate, entity, system, 1d);
                final Resource justification1 = makeTextJustification(model, "source1", 0, 4, system, 1d);
                final Resource compound = markCompoundJustification(model,
                        ImmutableSet.of(relationEdge),
                        ImmutableSet.of(justification1),
                        system,
                        1d);

                // test event
                final Resource eventEdge = markAsArgument(model, event, SeedlingOntology.Conflict_Attack_Target, entity, system, 1.0);
                markJustification(eventEdge, compound);

                assertAndDump(model, "NIST.valid: edge justification contains at most two mentions",
                        nistSeedlingValidator, true);
            }
        }

        // Video must use aida:KeyFrameVideoJustification. Remove ShotVideoJustification
        @Nested
        class PreventShotVideo {
            @Test
            void invalid() {
                markShotVideoJustification(model, entity, "source1", "shotId", system, 1d);
                assertAndDump(model, "NIST.invalid: No shot video", nistSeedlingValidator, false);
            }

            @Test
            void valid() {
                markKeyFrameVideoJustification(model, entity, "source1", "keyframe",
                        new BoundingBox(new Point(0, 0), new Point(100, 100)), system, 1d);
                assertAndDump(model, "NIST.valid: No shot video", nistSeedlingValidator, true);
            }
        }

        // Members of clusters are entity objects, relation objects, and event objects (not clusters)
        @Nested
        class FlatClusters {
            @Test
            void invalid() {
                markAsPossibleClusterMember(model, eventCluster, entityCluster, .5, system);
                assertAndDump(model, "NIST.invalid: Flat clusters", nistSeedlingValidator, false);
            }

            @Test
            void valid() {
                final Resource newEntity = makeEntity(model, getEntityUri(), system);
                addType(newEntity, SeedlingOntology.Person);
                markAsPossibleClusterMember(model, newEntity, entityCluster, .75, system);
                assertAndDump(model, "NIST.valid: Flat clusters", nistSeedlingValidator, true);
            }
        }

        // Entity, Relation, and Event object is required to be part of at least one cluster.
        // This is true even if there is nothing else in the cluster
        @Nested
        class EverythingClustered {
            @Test
            void invalid() {
                // Test entity, relation, and event. Correct other than being clustered
                addType(makeEntity(model, getEntityUri(), system), SeedlingOntology.Weapon);
                addType(makeRelation(model, getUri("relationX"), system),
                        SeedlingOntology.GeneralAffiliation_APORA);
                addType(makeEvent(model, getUri("eventX"), system),
                        SeedlingOntology.Life_BeBorn);
                assertAndDump(model, "NIST.invalid: Everything has cluster", nistSeedlingValidator, false);
            }

            @Test
            void valid() {
                final Resource newEntity = makeEntity(model, getEntityUri(), system);
                addType(newEntity, SeedlingOntology.Weapon);
                makeClusterWithPrototype(model, getClusterUri(), newEntity, system);

                final Resource relation = makeRelation(model, getUri("relationX"), system);
                addType(relation, SeedlingOntology.GeneralAffiliation_APORA);
                makeClusterWithPrototype(model, getClusterUri(), relation, system);

                final Resource newEvent = makeEvent(model, getUri("eventX"), system);
                addType(newEvent, SeedlingOntology.Life_BeBorn);
                makeClusterWithPrototype(model, getClusterUri(), newEvent, system);

                assertAndDump(model, "NIST.valid: Everything has cluster", nistSeedlingValidator, true);
            }
        }

        // Each confidence value must be between 0 and 1
        @Nested
        class ConfidenceValueRange {
            @Test
            void invalid() {
                final Resource newEntity = makeEntity(model, getEntityUri(), system);
                addType(newEntity, SeedlingOntology.Person);
                markAsPossibleClusterMember(model, newEntity, entityCluster, 1.2, system);
                assertAndDump(model, "NIST.invalid: confidence must be between 0 and 1", nistSeedlingValidator, false);
            }
            @Test
            void valid() {
                final Resource newEntity = makeEntity(model, getEntityUri(), system);
                addType(newEntity, SeedlingOntology.Person);
                markAsPossibleClusterMember(model, newEntity, entityCluster, .7, system);
                assertAndDump(model, "NIST.valid: confidence must be between 0 and 1", nistSeedlingValidator, true);
            }
        }

        // CompoundJustification must be used only for justifications of argument assertions,
        // and not for justifications for entities, events, or relation KEs
        @Nested
        class RestrictCompoundJustification {
            @Test
            void invalid() {

                //test entity
                final Resource newEntity = makeEntity(model, getEntityUri(), system);
                addType(newEntity, SeedlingOntology.GeneralAffiliation_APORA);
                final Resource justification1 = makeTextJustification(model, "source1", 0, 4, system, 1d);
                final Resource compound1 = markCompoundJustification(model,
                        ImmutableSet.of(newEntity),
                        ImmutableSet.of(justification1),
                        system,
                        1d);

                markJustification(newEntity, compound1);

                //test relation
                final Resource newRelation = makeRelation(model, getUri("relationX"), system);
                addType(newRelation, SeedlingOntology.GeneralAffiliation_APORA);
                final Resource justification2 = makeTextJustification(model, "source1", 0, 4, system, 1d);
                final Resource compound2 = markCompoundJustification(model,
                        ImmutableSet.of(newRelation),
                        ImmutableSet.of(justification2),
                        system,
                        1d);

                markJustification(newRelation, compound2);

                //test event
                final Resource newEvent = makeEvent(model, getUri("eventX"), system);
                addType(newEvent, SeedlingOntology.Life_BeBorn);
                final Resource justification3 = makeTextJustification(model, "source1", 0, 4, system, 1d);
                final Resource compound3 = markCompoundJustification(model,
                        ImmutableSet.of(newEvent),
                        ImmutableSet.of(justification3),
                        system,
                        1d);

                markJustification(newEvent, compound3);

                assertAndDump(model, "NIST.invalid: CompoundJustification must be used only for " +
                                "justifications of argument assertions",
                        nistSeedlingValidator, false);

            }
            @Test
            void valid() {

                // test relation argument
                final Resource relation = makeRelation(model, getUri("relationX"), system);
                addType(relation, SeedlingOntology.GeneralAffiliation_APORA);
                makeClusterWithPrototype(model, getClusterUri(), relation, system);
                final Resource relationEdge = markAsArgument(model, relation,
                        SeedlingOntology.GeneralAffiliation_APORA_Affiliate, entity, system, 1d);
                final Resource justification1 = makeTextJustification(model, "source1", 0, 4, system, 1d);
                final Resource compound = markCompoundJustification(model,
                        ImmutableSet.of(relationEdge),
                        ImmutableSet.of(justification1),
                        system,
                        1d);

                markJustification(relationEdge, compound);

                // test event argument
                final Resource eventEdge = markAsArgument(model, event, SeedlingOntology.Conflict_Attack_Target, entity, system, 1.0);
                final Resource justification2 = makeTextJustification(model, "source1", 0, 4, system, 1d);
                final Resource compound2 = markCompoundJustification(model,
                        ImmutableSet.of(eventEdge),
                        ImmutableSet.of(justification2),
                        system,
                        1d);

                markJustification(eventEdge, compound);

                assertAndDump(model, "NIST.valid: CompoundJustification must be used only for " +
                                "justifications of argument assertions",
                        nistSeedlingValidator, true);

        // Entity, Relation, and Event clusters must have IRI
        @Nested
        class ClusterHasIRI {
            @Test
            void invalid() {
                // Test entity, relation, and event. Correct other than being clustered
                makeClusterWithPrototype(model, null, entity, system);
                makeClusterWithPrototype(model, null, relation, system);
                makeClusterWithPrototype(model, null, event, system);

                assertAndDump(model, "NIST.invalid: Cluster has IRI", nistSeedlingValidator, false);
            }

            @Test
            void valid() {
                assertAndDump(model, "NIST.valid: Cluster has IRI", nistSeedlingValidator, true);
            }
        }
    }

    // we dump the test name and the model in Turtle format so that whenever the user
    // runs the tests, they will also get the examples7
    private void assertAndDump(Model model, String testName, ValidateAIF validator, boolean expected) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PrintStream oldErr = System.err;
        System.setErr(new PrintStream(baos));
        boolean valid = validator.validateKB(model);
        System.setErr(oldErr);

        // print model if result unexpected or if forcing (for examples)
        if (valid != expected || (FORCE_DUMP && expected)) {
            System.out.println("\n\n" + testName + "\n\nAIF Model:");
            RDFDataMgr.write(System.out, model, RDFFormat.TURTLE_PRETTY);
        }

        // fail if result is unexpected
        if (valid != expected) {
            // only print output if there is any
            if (!valid) {
                System.out.println("\nFailure:");
                System.out.println(baos);
            }
            fail("Validation was expected to " + (expected ? "pass" : "fail") + " but did not");
        }
    }

    private Path writeModelToDisk(Model model) {

        Path outputPath = null;
        try {
            outputPath = Files.createTempFile("testoutput", ".ttl");
            System.out.println("Writing final model to " + outputPath);
            RDFDataMgr.write(Files.newOutputStream(outputPath), model, RDFFormat.TURTLE_PRETTY);
        } catch (Exception e) {
            System.err.println("Unable to write to tempfile " + e.getMessage());
            e.printStackTrace();
        }
        return outputPath;
    }


    private Model readModelFromDisk(Path filename) {
        try {
            Model model = createDiskBasedModel();
            RDFDataMgr.read(model, Files.newInputStream(filename), Lang.TURTLE);
            return model;
        } catch (Exception e) {
            System.err.println("Unable to write to tempfile " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private Model createModel() {
        final Model model = ModelFactory.createDefaultModel();
        return addNamespacesToModel(model);
    }

    private Model createDiskBasedModel() {
        try {
            final Path outputPath = Files.createTempDirectory("diskbased-model-");
            System.out.println("Creating disk based model at " + outputPath.toString());
            final Dataset dataset = TDBFactory.createDataset(outputPath.toString());
            final Model model = dataset.getDefaultModel();
            return addNamespacesToModel(model);
        } catch (Exception e) {
            System.err.println("Unable to create temp directory: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private Model addNamespacesToModel(Model model) {
        // adding namespace prefixes makes the Turtle output more readable
        model.setNsPrefix("rdf", RDF.uri);
        model.setNsPrefix("xsd", XSD.getURI());
        model.setNsPrefix("aida", AidaAnnotationOntology.NAMESPACE);
        model.setNsPrefix("ldcOnt", NAMESPACE);
        model.setNsPrefix("ldc", LDC_NS);
        model.setNsPrefix("skos", SKOS.uri);
        return model;
    }
}
