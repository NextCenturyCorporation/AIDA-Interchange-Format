package com.ncc.aif;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.topbraid.shacl.vocabulary.SH;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Comparator;

import static com.ncc.aif.AIFUtils.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@TestInstance(Lifecycle.PER_CLASS)
public class ExamplesAndValidationTest {
    // Modify these flags to control how tests output their models/reports and if so, how they output them
    // When DUMP_ALWAYS is false, the model is only dumped when the result is unexpected (and if invalid, the report is also dumped)
    // When DUMP_ALWAYS is true, the model is always dumped, and the report is always dumped if invalid
    private static final boolean DUMP_ALWAYS = false;
    // When DUMP_TO_FILE is false, if a model or report is dumped, it goes to stdout
    // WHen DUMP_TO_FILE is true, if a model or report is dumped, it goes to a file in target/test-dump-output
    private static final boolean DUMP_TO_FILE = false;

    private static final String NIST_ROOT = "https://tac.nist.gov/tracks/SM-KBP/";
    private static final String LDC_NS = NIST_ROOT + "2019/LdcAnnotations#";
    private static final String ONTOLOGY_NS = NIST_ROOT + "2018/ontologies/SeedlingOntology#";
    private static final String DISKBASED_MODEL_PATH = System.getProperty("java.io.tmpdir") + "/diskbased-models/tests";
    private static final CharSource SEEDLING_ONTOLOGY = Resources.asCharSource(
            Resources.getResource("com/ncc/aif/ontologies/SeedlingOntology"),
            StandardCharsets.UTF_8);
    private static TestUtils utils;

    @BeforeAll
    static void initTest() {
        // prevent too much logging from obscuring the Turtle examples which will be printed
        ((Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        try {
            if (Files.exists(Paths.get(DISKBASED_MODEL_PATH))) { // Delete the directory if it exists
                Files.walk(Paths.get(DISKBASED_MODEL_PATH)).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        } catch (IOException ioe) {
            System.err.println("Unable to delete disk-based model directory: " + ioe.getMessage());
        }
        utils = new TestUtils(LDC_NS, ValidateAIF.createForDomainOntologySource(SEEDLING_ONTOLOGY), DUMP_ALWAYS, DUMP_TO_FILE);
    }

    private Model model;
    private Resource system;
    private int diskModelCount = 0;

    @BeforeEach
    void setup() {
        model = utils.startNewTest();
        addNamespacesToModel(model);
        system = utils.getSystem();
    }

    @Nested
    class ValidExamples {
        private final String putinDocumentEntityUri = utils.getUri("E781167.00398");
        private final String putinResidesDocumentRelationUri = utils.getUri("R779959.00000");
        private final String putinElectedDocumentEventUri = utils.getUri("V779961.00010");
        private final String russiaDocumentEntityUri = utils.getUri("E779954.00004");
        private final String russiaOwnsBukDocumentRelationUri = utils.getUri("R779959.00004");
        private final String ukraineDocumentEntityUri = utils.getUri("E779959.00021");
        private final String ukraineOwnsBukDocumentRelationUri = utils.getUri("R779959.00002");
        private final String bukDocumentEntityUri = utils.getUri("E779954.00005");
        private final String bukKBEntityUri = utils.getUri("E0084");
        private final String mh17AttackDocumentEventUri = utils.getUri("V779961.00012");
        private final String mh17DocumentEntityUri = utils.getUri("E779961.00032");

        @Test
        void createSeedlingEntityOfTypePersonWithAllJustificationTypesAndConfidence() {
            // it doesn't matter what URI we give entities, events, etc. so long as they are
            // unique
            final Resource putinMentionResource = makeEntity(model, putinDocumentEntityUri, system);

            // in order to allow uncertainty about the type of an entity, we don't mark an
            // entity's type directly on the entity, but rather make a separate assertion for it
            // its URI doesn't matter either
            final Resource typeAssertion = markType(model, utils.getAssertionUri(), putinMentionResource,
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

            utils.testValid("create a seedling entity of type person with textual justification and confidence");
        }

        @Test
        void createSeedlingEntityWithUncertaintyAboutItsType() {
            final Resource entity = makeEntity(model, putinDocumentEntityUri, system);
            final Resource entityIsAPerson = markType(model, utils.getAssertionUri(), entity, SeedlingOntology.Person,
                    system, 0.5);
            final Resource entityIsAPoliticalEntity = markType(model, utils.getAssertionUri(), entity,
                    SeedlingOntology.GeopoliticalEntity, system, 0.2);

            markTextJustification(model, ImmutableSet.of(entity, entityIsAPerson),
                    "HC000T6IV", 1029, 1033, system, 0.973);

            markTextJustification(model, ImmutableSet.of(entity, entityIsAPoliticalEntity),
                    "NYT_ENG_201181231", 343, 367, system, 0.3);

            markAsMutuallyExclusive(model, ImmutableMap.of(ImmutableSet.of(entityIsAPerson), 0.5,
                    ImmutableSet.of(entityIsAPoliticalEntity), 0.2), system, null);

            utils.testValid("create a seedling entity with uncertainty about its type");
        }

        @Test
        void createARelationBetweenTwoSeedlingEntitiesWhereThereIsUncertaintyAboutIdentityOfOneArgument() {
            // we want to represent a "physical_resident" relation for a person, but we aren't sure whether
            // they reside in Russia or Ukraine
            final Resource personEntity = makeEntity(model, putinDocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), personEntity, SeedlingOntology.Person, system, 1.0);

            // create entities for the two locations
            final Resource russiaDocumentEntity = makeEntity(model, russiaDocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), russiaDocumentEntity, SeedlingOntology.GeopoliticalEntity, system, 1.0);

            final Resource ukraineDocumentEntity = makeEntity(model, russiaDocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), ukraineDocumentEntity, SeedlingOntology.GeopoliticalEntity, system, 1.0);

            // create an entity for the uncertain place of residence
            final Resource uncertainPlaceOfResidenceEntity = makeEntity(model, utils.getEntityUri(), system);
            markType(model, utils.getAssertionUri(), uncertainPlaceOfResidenceEntity, SeedlingOntology.GeopoliticalEntity, system, 1d);

            // whatever this place turns out to refer to, we're sure it's where they live
            final Resource relation = makeRelation(model, putinResidesDocumentRelationUri, system);
            markType(model, utils.getAssertionUri(), relation, SeedlingOntology.Physical_Resident, system, 1.0);
            markAsArgument(model, relation, SeedlingOntology.Physical_Resident_Resident, personEntity, system, 1.0);
            markAsArgument(model, relation, SeedlingOntology.Physical_Resident_Place, uncertainPlaceOfResidenceEntity, system, 1.0);

            // we use clusters to represent uncertainty about identity
            // we make two clusters, one for Russia and one for Ukraine
            final Resource russiaCluster = makeClusterWithPrototype(model, utils.getClusterUri(), russiaDocumentEntity, system);
            final Resource ukraineCluster = makeClusterWithPrototype(model, utils.getClusterUri(), ukraineDocumentEntity, system);

            // the uncertain place of residence is either Russia or Ukraine
            final Resource placeOfResidenceInRussiaCluster = markAsPossibleClusterMember(model,
                    uncertainPlaceOfResidenceEntity, russiaCluster, 0.4, system);
            final Resource placeOfResidenceInUkraineCluster = markAsPossibleClusterMember(model,
                    uncertainPlaceOfResidenceEntity, ukraineCluster, 0.6, system);

            // but not both
            markAsMutuallyExclusive(model, ImmutableMap.of(
                    ImmutableSet.of(placeOfResidenceInUkraineCluster), 0.4,
                    ImmutableSet.of(placeOfResidenceInRussiaCluster), 0.6),
                    system, null);

            utils.testValid("create a relation between two seedling entities where there "
                    + "is uncertainty about identity of one argument");
        }

        @Test
        void createSeedlingEvent() {
            // we make a resource for the event itself
            // mark the event as a Personnel.Elect event; type is encoded separately so we can express
            // uncertainty about type
            final Resource event = makeEvent(model, putinElectedDocumentEventUri, system);
            markType(model, utils.getAssertionUri(), event, SeedlingOntology.Personnel_Elect, system, 1.0);

            // create the two entities involved in the event
            final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), putin, SeedlingOntology.Person, system, 1.0);

            final Resource russia = makeEntity(model, russiaDocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), russia, SeedlingOntology.GeopoliticalEntity, system, 1.0);

            // link those entities to the event
            markAsArgument(model, event,
                    SeedlingOntology.Personnel_Elect_Elect,
                    putin, system, 0.785);
            markAsArgument(model, event,
                    SeedlingOntology.Personnel_Elect_Place,
                    russia, system, 0.589);

            utils.testValid("create a seedling event");
        }

        /**
         * Same as createSeedlingEvent above, except with event argument URI's
         */
        @Test
        void createSeedlingEventWithEventArgumentURI() {
            // we make a resource for the event itself
            // mark the event as a Personnel.Elect event; type is encoded separately so we can express
            // uncertainty about type
            final Resource event = makeEvent(model, putinElectedDocumentEventUri, system);
            markType(model, utils.getAssertionUri(), event, SeedlingOntology.Personnel_Elect, system, 1.0);

            // create the two entities involved in the event
            final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), putin, SeedlingOntology.Person, system, 1.0);

            final Resource russia = makeEntity(model, russiaDocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), russia, SeedlingOntology.GeopoliticalEntity, system, 1.0);

            // link those entities to the event
            markAsArgument(model, event, SeedlingOntology.Personnel_Elect_Elect,
                    putin, system, 0.785, utils.getUri("eventArgument-1"));
            markAsArgument(model, event, SeedlingOntology.Personnel_Elect_Place,
                    russia, system, 0.589, utils.getUri("eventArgument-2"));

            utils.testValid("create a seedling event with event assertion URI");
        }

        @Test
        void useSubgraphConfidencesToShowMutuallyExclusiveLinkedSeedlingEventArgumentOptions() {
            // we want to say that either Ukraine or Russia attacked MH17, but we aren't sure which

            // we make a resource for the event itself
            final Resource event = makeEvent(model, mh17AttackDocumentEventUri, system);

            // mark the event as a Personnel.Elect event; type is encoded separately so we can express
            // uncertainty about type
            // NOTE: mapper keys use '.' separator but produce correct seedling output
            markType(model, utils.getAssertionUri(), event, SeedlingOntology.Conflict_Attack,
                    system, 1.0);

            // create the entities involved in the event
            final Resource ukraine = makeEntity(model, ukraineDocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), ukraine, SeedlingOntology.GeopoliticalEntity, system, 1.0);

            final Resource russia = makeEntity(model, russiaDocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), russia, SeedlingOntology.GeopoliticalEntity, system, 1.0);

            final Resource mh17 = makeEntity(model, mh17DocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), mh17, SeedlingOntology.Vehicle, system, 1.0);

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

            utils.testValid("seedling sub-graph confidences");
        }

        @Test
        void twoSeedlingHypotheses() {
            // we want to represent that we know, regardless of hypothesis, that there is a BUK missile launcher,
            // a plane MH17, two countries (Russia and Ukraine), and the BUK missile launcher was used to attack MH17
            final Resource buk = makeEntity(model, bukDocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), buk, SeedlingOntology.Weapon, system, 1.0);

            final Resource mh17 = makeEntity(model, mh17DocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), mh17, SeedlingOntology.Vehicle, system, 1.0);

            final Resource russia = makeEntity(model, russiaDocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), russia, SeedlingOntology.GeopoliticalEntity, system, 1.0);

            final Resource ukraine = makeEntity(model, ukraineDocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), ukraine, SeedlingOntology.GeopoliticalEntity, system, 1.0);

            final Resource attackOnMH17 = makeEvent(model, mh17AttackDocumentEventUri, system);
            markType(model, utils.getAssertionUri(), attackOnMH17, SeedlingOntology.Conflict_Attack,
                    system, 1.0);
            markAsArgument(model, attackOnMH17, SeedlingOntology.Conflict_Attack_Target,
                    mh17, system, null);
            markAsArgument(model, attackOnMH17, SeedlingOntology.Conflict_Attack_Instrument,
                    buk, system, null);

            final Resource isAttacker = SeedlingOntology.Conflict_Attack_Attacker;

            // under the background hypothesis that the BUK is Russian, we believe Russia attacked MH17
            final Resource bukIsRussian = makeRelation(model, russiaOwnsBukDocumentRelationUri, system);
            markType(model, utils.getAssertionUri(), bukIsRussian, SeedlingOntology.GeneralAffiliation_APORA, system, 1.0);
            markAsArgument(model, bukIsRussian, SeedlingOntology.GeneralAffiliation_APORA_Affiliate, buk, system, 1.0);
            markAsArgument(model, bukIsRussian, SeedlingOntology.GeneralAffiliation_APORA_Affiliation, russia, system, 1.0);

            final Resource bukIsRussianHypothesis = makeHypothesis(model, utils.getHypothesisUri(),
                    ImmutableSet.of(bukIsRussian), system);
            final Resource russiaShotMH17 = markAsArgument(model, attackOnMH17, isAttacker, russia, system, 1.0);
            markDependsOnHypothesis(russiaShotMH17, bukIsRussianHypothesis);
            markConfidence(model, bukIsRussianHypothesis, 0.75, system);

            // under the background hypothesis that BUK is Ukrainian, we believe Ukraine attacked MH17
            final Resource bukIsUkrainian = makeRelation(model, ukraineOwnsBukDocumentRelationUri, system);
            markType(model, utils.getAssertionUri(), bukIsUkrainian, SeedlingOntology.GeneralAffiliation_APORA, system, 1.0);
            markAsArgument(model, bukIsUkrainian, SeedlingOntology.GeneralAffiliation_APORA_Affiliate, buk, system, 1.0);
            markAsArgument(model, bukIsUkrainian, SeedlingOntology.GeneralAffiliation_APORA_Affiliation, ukraine, system, 1.0);

            final Resource bukIsUkranianHypothesis = makeHypothesis(model, utils.getHypothesisUri(),
                    ImmutableSet.of(bukIsUkrainian), 0.25, system);
            final Resource ukraineShotMH17 = markAsArgument(model, attackOnMH17, isAttacker, russia, system, 1.0);
            markDependsOnHypothesis(ukraineShotMH17, bukIsUkranianHypothesis);

            utils.testValid("two seedling hypotheses");
        }

        // Create simple hypothesis that the BUK weapon system was owned by Russia
        @Test
        void simpleHypothesisWithCluster() {
            // buk document entity
            final Resource buk = makeEntity(model, bukDocumentEntityUri, system);
            final Resource bukIsWeapon = markType(model, utils.getAssertionUri(), buk, SeedlingOntology.Weapon,
                    system, 1.0);

            // buk cross-document entity
            final Resource bukKBEntity = makeEntity(model, bukKBEntityUri, system);
            final Resource bukKBIsWeapon = markType(model, utils.getAssertionUri(), bukKBEntity, SeedlingOntology.Weapon,
                    system, 1.0);

            // russia document entity
            final Resource russia = makeEntity(model, russiaDocumentEntityUri, system);
            final Resource russiaIsGPE = markType(model, utils.getAssertionUri(), russia, SeedlingOntology.GeopoliticalEntity,
                    system, 1.0);

            // cluster buk
            final Resource bukCluster = makeClusterWithPrototype(model, utils.getClusterUri(), bukKBEntity, system);
            final Resource bukIsClustered = markAsPossibleClusterMember(model, buk, bukCluster, .9, system);

            // Russia owns buk relation
            final Resource bukIsRussian = makeRelation(model, russiaOwnsBukDocumentRelationUri, system);
            markType(model, utils.getAssertionUri(), bukIsRussian, SeedlingOntology.GeneralAffiliation_APORA,
                    system, 1.0);
            final Resource bukArgument = markAsArgument(model, bukIsRussian,
                    SeedlingOntology.GeneralAffiliation_APORA_Affiliate, buk, system, 1.0);
            final Resource russiaArgument = markAsArgument(model, bukIsRussian,
                    SeedlingOntology.GeneralAffiliation_APORA_Affiliation, russia, system, 1.0);

            // Russia owns buk hypothesis
            final Resource bukIsRussianHypothesis = makeHypothesis(model, utils.getHypothesisUri(),
                    ImmutableSet.of(
                            buk, bukIsWeapon, bukIsClustered,
                            russia, russiaIsGPE,
                            bukIsRussian, bukArgument, russiaArgument
                    ), system);

            utils.testValid("simple hypothesis with cluster");
        }

        // Create simple hypothesis with an importance value where the BUK weapon system was owned by Russia
        @Test
        void simpleHypothesisWithImportanceWithCluster() {
            // buk document entity
            final Resource buk = makeEntity(model, bukDocumentEntityUri, system);
            final Resource bukIsWeapon = markType(model, utils.getAssertionUri(), buk, SeedlingOntology.Weapon,
                    system, 1.0);

            // buk cross-document entity
            final Resource bukKBEntity = makeEntity(model, bukKBEntityUri, system);
            final Resource bukKBIsWeapon = markType(model, utils.getAssertionUri(), bukKBEntity, SeedlingOntology.Weapon,
                    system, 1.0);

            // russia document entity
            final Resource russia = makeEntity(model, russiaDocumentEntityUri, system);
            final Resource russiaIsGPE = markType(model, utils.getAssertionUri(), russia, SeedlingOntology.GeopoliticalEntity,
                    system, 1.0);

            // cluster buk
            final Resource bukCluster = makeClusterWithPrototype(model, utils.getClusterUri(), bukKBEntity, system);
            final Resource bukIsClustered = markAsPossibleClusterMember(model, buk, bukCluster, .9, system);

            // add importance to the cluster - test negative importance
            markImportance(bukCluster, -70.234);

            // Russia owns buk relation
            final Resource bukIsRussian = makeRelation(model, russiaOwnsBukDocumentRelationUri, system);
            markType(model, utils.getAssertionUri(), bukIsRussian, SeedlingOntology.GeneralAffiliation_APORA,
                    system, 1.0);
            final Resource bukArgument = markAsArgument(model, bukIsRussian,
                    SeedlingOntology.GeneralAffiliation_APORA_Affiliate, buk, system, 1.0);
            final Resource russiaArgument = markAsArgument(model, bukIsRussian,
                    SeedlingOntology.GeneralAffiliation_APORA_Affiliation, russia, system, 1.0);

            // add importance to the statements
            markImportance(bukArgument, 100.0);

            // add large importance
            markImportance(russiaArgument, 9.999999e6);

            // Russia owns buk hypothesis
            final Resource bukIsRussianHypothesis = makeHypothesis(model, utils.getHypothesisUri(),
                    ImmutableSet.of(
                            buk, bukIsWeapon, bukIsClustered,
                            russia, russiaIsGPE,
                            bukIsRussian, bukArgument, russiaArgument
                    ), system);

            // test highest possible importance value
            markImportance(bukIsRussianHypothesis, Double.MAX_VALUE);

            utils.testValid("simple hypothesis with importance with cluster");
        }

        @Test
        void createSeedlingEntityOfTypePersonWithImageJustificationAndVector() {
            // it doesn't matter what URI we give entities, events, etc. so long as they are unique
            final Resource putin = makeEntity(model, putinDocumentEntityUri, system);

            // in order to allow uncertainty about the type of an entity, we don't mark an
            // entity's type directly on the entity, but rather make a separate assertion for it
            // its URI doesn't matter either
            final Resource typeAssertion = markType(model, utils.getAssertionUri(), putin, SeedlingOntology.Person,
                    system, 1.0);

            // the justification provides the evidence for our claim about the entity's type
            // we attach this justification to both the type assertion and the entity object
            // itself, since it provides evidence both for the entity's existence and its type.
            // in TA1 -> TA2 communications, we attach confidences at the level of justifications

            // let's suppose we have evidence from an image
            markImageJustification(model, ImmutableSet.of(putin, typeAssertion),
                    "NYT_ENG_20181231_03",
                    new BoundingBox(new Point(123, 45), new Point(167, 98)),
                    system, 0.123);

            // let's mark our entity with some arbitrary system-private data. You can attach such data
            // to nearly anything
            try {
                markPrivateData(model, putin, utils.getUri("testSystem-personVector"),
                        Arrays.asList(2.0, 7.5, 0.2, 8.1), system);
            } catch (JsonProcessingException jpe) {
                System.err.println("Unable to convert vector data to String " + jpe.getMessage());
                jpe.printStackTrace();
            }

            utils.testValid("create a seedling entity of type person with image " +
                    "justification and vector");
        }

        @Test
        void createSeedlingEntityWithAlternateNames() {
            // assign alternate names to the putin entity
            final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), putin, SeedlingOntology.Person, system, 1.0);
            markName(putin, "Путина");
            markName(putin, "Владимира Путина");


            final Resource results = makeEntity(model, utils.getUri("E966733.00068"), system);
            markType(model, utils.getAssertionUri(), results, SeedlingOntology.Results, system, 1.0);
            markTextValue(results, "проти 10,19%");

            final Resource value = makeEntity(model, utils.getUri("E831667.00871"), system);
            markType(model, utils.getAssertionUri(), value, SeedlingOntology.NumericalValue, system, 1.0);
            markNumericValueAsDouble(value, 16.0);
            markNumericValueAsLong(value, (long) 16);
            markNumericValueAsString(value, "на висоті менше 16 кілометрів");
            markNumericValueAsString(value, "at a height less than 16 kilometers");

            utils.testValid("create a seedling entity of type person with names");
        }

        @Test
        void createCompoundJustification() {
            // we make a resource for the event itself
            // mark the event as a Personnel.Elect event; type is encoded separately so we can express
            // uncertainty about type
            final Resource event = makeEvent(model, putinElectedDocumentEventUri, system);
            final Resource eventTypeAssertion = markType(model, utils.getAssertionUri(), event,
                    SeedlingOntology.Personnel_Elect, system, 1.0);

            // create the two entities involved in the event
            final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
            final Resource personTypeAssertion = markType(model, utils.getAssertionUri(), putin,
                    SeedlingOntology.Person, system, 1.0);

            final Resource russia = makeEntity(model, russiaDocumentEntityUri, system);
            final Resource gpeTypeAssertion = markType(model, utils.getAssertionUri(), russia,
                    SeedlingOntology.GeopoliticalEntity, system, 1.0);

            // link those entities to the event
            final Resource electeeArgument = markAsArgument(model, event,
                    SeedlingOntology.Personnel_Elect_Elect,
                    putin, system, 0.785, utils.getAssertionUri());
            final Resource placeArgument = markAsArgument(model, event,
                    SeedlingOntology.Personnel_Elect_Place,
                    russia, system, 0.589, utils.getAssertionUri());


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

            utils.testValid("create a compound justification");
        }

        @Test
        void createCompoundJustificationWithSingleJustification() {

            final Resource event = makeEvent(model, putinElectedDocumentEventUri, system);
            markType(model, utils.getAssertionUri(), event,
                    SeedlingOntology.Personnel_Elect, system, 1.0);

            final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), putin,
                    SeedlingOntology.Person, system, 1.0);

            // link those entities to the event
            final Resource electeeArgument = markAsArgument(model, event,
                    SeedlingOntology.Personnel_Elect_Elect,
                    putin, system, 0.785, utils.getAssertionUri());

            final Resource textJustification = makeTextJustification(model, "NYT_ENG_20181231",
                    42, 143, system, 0.973);

            markJustification(putin, textJustification);
            addSourceDocumentToJustification(textJustification, "NYT_PARENT_ENG_20181231_03");

            // combine single justification into single justifiedBy triple with new confidence
            markCompoundJustification(model, ImmutableSet.of(electeeArgument),
                    ImmutableSet.of(textJustification), system, 0.321);

            utils.testValid("create a compound justification with single justification");
        }

        @Test
        void createHierarchicalCluster() {
            // we want to say that the cluster of Trump entities might be the same as the cluster of the president entities
            // create president entities
            final Resource presidentUSA = makeEntity(model, utils.getEntityUri(), system);
            markType(model, utils.getAssertionUri(), presidentUSA, SeedlingOntology.GeopoliticalEntity, system, 1.0);
            markName(presidentUSA, "the president");

            // clustered entities don't require types
            final Resource newPresident = makeEntity(model, utils.getEntityUri(), system);
            markName(newPresident, "the newly-inaugurated president");

            final Resource president45 = makeEntity(model, utils.getEntityUri(), system);
            markType(model, utils.getAssertionUri(), president45, SeedlingOntology.GeopoliticalEntity, system, 1.0);
            markName(president45, "the 45th president");

            // cluster president entities
            final Resource presidentCluster = makeClusterWithPrototype(model, utils.getClusterUri(), presidentUSA, system);
            markAsPossibleClusterMember(model, newPresident, presidentCluster, .8, system);
            markAsPossibleClusterMember(model, president45, presidentCluster, .7, system);

            // create Trump entities
            final Resource donaldTrump = makeEntity(model, utils.getEntityUri(), system);
            markType(model, utils.getAssertionUri(), donaldTrump, SeedlingOntology.Person, system, .4);
            markName(donaldTrump, "Donald Trump");

            final Resource trump = makeEntity(model, utils.getEntityUri(), system);
            markType(model, utils.getAssertionUri(), trump, SeedlingOntology.Person, system, .5);
            markName(trump, "Trump");

            // cluster trump entities
            final Resource trumpCluster = makeClusterWithPrototype(model, utils.getClusterUri(), donaldTrump, system);
            markAsPossibleClusterMember(model, trump, trumpCluster, .9, system);

            // mark president cluster as being part of trump cluster
            markAsPossibleClusterMember(model, presidentCluster, trumpCluster, .6, system);

            utils.testValid("seedling hierarchical cluster");
        }

        /**
         * Simplest possible cluster example.  Two entities might be the same thing.
         */
        @Test
        void createASimpleCluster() {
            // Two people, probably the same person
            final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), putin, SeedlingOntology.Person, system, 1.0);
            markName(putin, "Путин");

            final Resource vladimirPutin = makeEntity(model, utils.getUri("E780885.00311"), system);
            markType(model, utils.getAssertionUri(), vladimirPutin, SeedlingOntology.Person, system, 1.0);
            markName(vladimirPutin, "Vladimir Putin");

            // create a cluster with prototype
            final Resource putinCluster = makeClusterWithPrototype(model, utils.getClusterUri(), putin, system);

            // person 1 is definitely in the cluster, person 2 is probably in the cluster
            markAsPossibleClusterMember(model, putin, putinCluster, 1d, system);
            markAsPossibleClusterMember(model, vladimirPutin, putinCluster, 0.71, system);

            utils.testValid("create a simple cluster");
        }

        /**
         * Simplest possible cluster example, plus justification
         */
        @Test
        void createASimpleClusterWithJustification() {
            // Two people, probably the same person
            final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), putin, SeedlingOntology.Person, system, 1.0);
            markName(putin, "Путин");

            final Resource vladimirPutin = makeEntity(model, utils.getUri("E780885.00311"), system);
            markType(model, utils.getAssertionUri(), vladimirPutin, SeedlingOntology.Person, system, 1.0);
            markName(vladimirPutin, "Vladimir Putin");

            // create a cluster with prototype
            final Resource putinCluster = makeClusterWithPrototype(model, utils.getClusterUri(), putin, system);

            // person 1 is definitely in the cluster, person 2 is probably in the cluster
            markAsPossibleClusterMember(model, putin, putinCluster, 1d, system);
            final Resource vladMightBePutin = markAsPossibleClusterMember(model, vladimirPutin, putinCluster,
                    0.71, system);

            markTextJustification(model, vladMightBePutin, "NYT_ENG_20181231", 42,
                    143, system, 0.973);

            utils.testValid("create a simple cluster with justification");
        }

        /**
         * Simplest possible cluster example, plus handle
         */
        @Test
        void createASimpleClusterWithHandle() {
            // Two people, probably the same person
            final String vladName = "Vladimir Putin";
            final Resource vladimirPutin = makeEntity(model, utils.getUri("E780885.00311"), system);
            markType(model, utils.getAssertionUri(), vladimirPutin, SeedlingOntology.Person, system, 1.0);
            markName(vladimirPutin, vladName);

            final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), putin, SeedlingOntology.Person, system, 1.0);
            markName(putin, "Путин");

            // create a cluster with prototype
            final Resource putinCluster = makeClusterWithPrototype(model, utils.getClusterUri(), vladimirPutin, vladName, system);

            // person 1 is definitely in the cluster, person 2 is probably in the cluster
            markAsPossibleClusterMember(model, putin, putinCluster, 0.71, system);

            utils.testValid("create a simple cluster with handle");
        }

        @Test
        void createEntityAndClusterWithInformativeJustification() {
            // Two people, probably the same person
            final String vladName = "Vladimir Putin";
            final Resource vladimirPutin = makeEntity(model, utils.getUri("E780885.00311"), system);
            markName(vladimirPutin, vladName);

            final Resource typeAssertion = markType(model, utils.getAssertionUri(), vladimirPutin, SeedlingOntology.Person, system, 1.0);
            final Resource justification = markTextJustification(model, typeAssertion, "HC00002Z0", 0, 10, system, 1d);
            markInformativeJustification(vladimirPutin, justification);

            final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), putin, SeedlingOntology.Person, system, 1.0);
            markName(putin, "Путин");

            // create a cluster with prototype
            final Resource putinCluster = makeClusterWithPrototype(model, utils.getClusterUri(), vladimirPutin, vladName, system);
            markInformativeJustification(putinCluster, justification);

            // person 1 is definitely in the cluster, person 2 is probably in the cluster
            markAsPossibleClusterMember(model, putin, putinCluster, 0.71, system);

            utils.testValid("create an entity and cluster with informative mention");
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

            final Resource michelleObamaDoc1 = makeEntity(model, utils.getUri("entity-michelleObamaDoc1"), system);
            markJustification(michelleObamaDoc1, michelleObamaMention);
            markJustification(michelleObamaDoc1, firstLadyMention);

            final Resource barackObamaDoc1 = makeEntity(model, utils.getUri("entity-barackObamaDoc1"), system);
            markJustification(barackObamaDoc1, barackObamaDoc1Mention);

            // the uncertain "President Obama" gets its own entity, since we aren't sure which of the other two it
            // is identical to
            final Resource uncertainPresidentObamaDoc1 = makeEntity(model,
                    utils.getUri("entity-uncertainPresidentObamaDoc1"), system);
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


            final Resource barackObamaDoc2 = makeEntity(model, utils.getUri("entity-barackObamaDoc2"), system);
            markJustification(barackObamaDoc2, barackObamaDoc2Mention);

            final Resource uncertainPresidentObamaDoc2 = makeEntity(model,
                    utils.getUri("entity-uncertainPresidentObamaDoc2"), system);
            markJustification(uncertainPresidentObamaDoc2, presidentObamaDoc2Mention1);
            markJustification(uncertainPresidentObamaDoc2, presidentObamaDoc2Mention2);

            final Resource uncertainSecretaryClintonDoc2 = makeEntity(model,
                    utils.getUri("entity-uncertainSecretaryClintonDoc2"), system);
            markJustification(uncertainSecretaryClintonDoc2, secretaryClintonDoc2Mention);


            // document 3 text:  [Bill Clinton] is married to Hilary Clinton.  [Secretary Clinton] doesn't like hamburgers.
            final Resource billClintonMention = makeTextJustification(model, "doc3", 0,
                    1, system, 1.0);
            final Resource hillaryClintonMention = makeTextJustification(model, "doc3", 0,
                    1, system, 1.0);
            final Resource uncertainSecretaryClintonDoc3Mention = makeTextJustification(model, "doc3", 0,
                    1, system, 1.0);

            final Resource billClintonDoc3 = makeEntity(model, utils.getUri("entity-billClintonDoc3"), system);
            markJustification(billClintonDoc3, billClintonMention);

            final Resource hillaryClintonDoc3 = makeEntity(model, utils.getUri("entity-hillaryClintonDoc3"), system);
            markJustification(hillaryClintonDoc3, hillaryClintonMention);

            final Resource uncertainSecretaryClintonDoc3 = makeEntity(model,
                    utils.getUri("entity-uncertainSecretaryClintonDoc3"), system);
            markJustification(uncertainSecretaryClintonDoc3, uncertainSecretaryClintonDoc3Mention);

            // mark that all these entities are people
            for (Resource person : ImmutableList.of(michelleObamaDoc1, barackObamaDoc1, uncertainPresidentObamaDoc1,
                    barackObamaDoc2, uncertainPresidentObamaDoc2, uncertainSecretaryClintonDoc2,
                    billClintonDoc3, hillaryClintonDoc3, uncertainSecretaryClintonDoc3)) {
                markType(model, utils.getAssertionUri(), person, SeedlingOntology.Person, system, 1.0);
            }

            // in NAIF, all cross-document linking is done via clusters and every entity must belong to some cluster
            final Resource michelleObamaCluster = makeClusterWithPrototype(model, utils.getClusterUri(), michelleObamaDoc1, system);
            final Resource barackObamaCluster = makeClusterWithPrototype(model, utils.getClusterUri(), barackObamaDoc1, system);
            final Resource billClintonCluster = makeClusterWithPrototype(model, utils.getClusterUri(), billClintonDoc3, system);
            final Resource hillaryClintonCluster = makeClusterWithPrototype(model,
                    utils.getClusterUri(), hillaryClintonDoc3, system);

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
            final Resource relation = makeRelation(model, utils.getRelationUri(), system);
            markType(model, utils.getAssertionUri(), relation, SeedlingOntology.PersonalSocial_Business, system, 0.75);
            markAsArgument(model, relation, SeedlingOntology.PersonalSocial_Business_Person, uncertainPresidentObamaDoc2, system, 0.75);
            markAsArgument(model, relation, SeedlingOntology.PersonalSocial_Business_Person, uncertainSecretaryClintonDoc2, system, 0.75);

            // mark justification "President Obama worked with Secretary Clinton"
            markTextJustification(model, relation, "doc2", 0, 10, system,
                    0.75);

            utils.testValid("create a relation where both endpoints are ambiguous (NIST way)");
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

            final Resource michelleObama = makeEntity(model, utils.getUri("entity-michelleObama"), system);
            markJustification(michelleObama, michelleObamaMention);
            markJustification(michelleObama, firstLadyMention);

            final Resource barackObama = makeEntity(model, utils.getUri("entity-barackObama"), system);
            markJustification(barackObama, barackObamaDoc1Mention);
            markJustification(barackObama, barackObamaDoc2Mention);

            final Resource billClinton = makeEntity(model, utils.getUri("entity-billClinton"), system);
            markJustification(billClinton, billClintonMention);

            final Resource hillaryClinton = makeEntity(model, utils.getUri("entity-hillaryClinton"), system);
            markJustification(hillaryClinton, hillaryClintonMention);

            // the uncertain "President Obama" gets its own entity, since we aren't sure which other entity it is
            // identical to. Here we are assuming all the "President Obamas" in our little mini-corpus are the same person.
            final Resource presidentObama = makeEntity(model, utils.getUri("entity-presidentObama"), system);
            markJustification(presidentObama, presidentObamaDoc1Mention);
            markJustification(presidentObama, presidentObamaDoc2Mention2);
            markJustification(presidentObama, presidentObamaDoc2Mention1);

            // same for "Secretary Clinton"
            final Resource secretaryClinton = makeEntity(model, utils.getUri("entity-secretaryClinton"), system);
            markJustification(secretaryClinton, secretaryClintonDoc2Mention);
            markJustification(secretaryClinton, uncertainSecretaryClintonDoc3Mention);

            // mark that all these entities are people
            for (Resource person : ImmutableList.of(michelleObama, barackObama, presidentObama,
                    secretaryClinton, billClinton, hillaryClinton)) {
                markType(model, utils.getAssertionUri(), person, SeedlingOntology.Person, system, 1.0);
            }

            // in general AIF you only need to use clusters if you need to show coreference uncertainty (which in this
            // case we do for all entities)
            final Resource michelleObamaCluster = makeClusterWithPrototype(model, utils.getClusterUri(), michelleObama, system);
            final Resource barackObamaCluster = makeClusterWithPrototype(model, utils.getClusterUri(), barackObama, system);
            final Resource billClintonCluster = makeClusterWithPrototype(model, utils.getClusterUri(), billClinton, system);
            final Resource hillaryClintonCluster = makeClusterWithPrototype(model, utils.getClusterUri(), hillaryClinton, system);

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
            final Resource relation = makeRelation(model, utils.getRelationUri(), system);
            markType(model, utils.getAssertionUri(), relation, SeedlingOntology.PersonalSocial_Business, system, 0.75);
            markAsArgument(model, relation, SeedlingOntology.PersonalSocial_Business_Person, presidentObama, system, 0.75);
            markAsArgument(model, relation, SeedlingOntology.PersonalSocial_Business_Person, secretaryClinton, system, 0.75);

            // mark justification "President Obama worked with Secretary Clinton"
            markTextJustification(model, relation, "doc2", 0, 10, system,
                    0.75);

            utils.testValid("create a relation where both endpoints are ambiguous (unrestricted way)");
        }

        @Test
        void createEntityWithDiskBaseModelAndWriteOut() {
            final ImmutablePair<Model, Dataset> pair = createDiskBasedModel();
            assertNotNull(pair, "Disk-based model does not exist");
            final Model model = pair.getLeft();

            // every AIF needs an object for the system responsible for creating it
            final Resource system = makeSystemWithURI(model, utils.getTestSystemUri());

            final Resource entity = makeEntity(model, putinDocumentEntityUri, system);
            final Resource entityIsAPerson = markType(model, utils.getAssertionUri(), entity, SeedlingOntology.Person,
                    system, 0.5);
//        final Resource entityIsAPoliticalEntity = markType(model, utils.getAssertionUri(), entity,
//                SeedlingOntology.GeopoliticalEntity, system, 0.2);

            markTextJustification(model, ImmutableSet.of(entityIsAPerson),
                    "HC000T6IV", 1029, 1033, system, 0.973);

            Path filename = writeModelToDisk(model);
            model.close();
            pair.getRight().close();

            final ImmutablePair<Model, Dataset> pair2 = readModelFromDisk(filename);
            assertNotNull(pair2, "Disk-based model does not exist");
            final Model model2 = pair2.getLeft();
            Resource rtest = model2.getResource(putinDocumentEntityUri);
            model2.close();
            pair2.getRight().close();
            assertNotNull(rtest, "Entity does not exist");
        }

        /**
         * A cluster should be able to contain a link to one or more external KBs
         */
        @Test
        void createClusterWithLinkAndConfidence() {

            // Two people, probably the same person
            final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
            markType(model, utils.getAssertionUri(), putin, SeedlingOntology.Person, system, 1.0);
            markName(putin, "Путин");

            final Resource vladimirPutin = makeEntity(model, utils.getUri("E780885.00311"), system);
            markType(model, utils.getAssertionUri(), vladimirPutin, SeedlingOntology.Person, system, 1.0);
            markName(vladimirPutin, "Vladimir Putin");

            // create a cluster with prototype
            final Resource putinCluster = makeClusterWithPrototype(model, utils.getClusterUri(), putin, "handle", system);

            // person 1 is definitely in the cluster, person 2 is probably in the cluster
            markAsPossibleClusterMember(model, putin, putinCluster, 1d, system);
            markAsPossibleClusterMember(model, vladimirPutin, putinCluster, 0.71, system);

            // Link a cluster to a KB
            linkToExternalKB(model, putinCluster, "freebase:FOO", system, .398);

            utils.testValid("Create a cluster with link and confidence.");
        }

        @Test
        void createEventWithLDCTime() {
            // Create a start position event with unknown start and end time
            final Resource eventStartPosition = utils.makeValidAIFEvent(SeedlingOntology.Personnel_StartPosition);
            LDCTimeComponent unknown = new LDCTimeComponent(LDCTimeComponent.LDCTimeType.UNKNOWN, null, null, null);
            LDCTimeComponent endBefore = new LDCTimeComponent(LDCTimeComponent.LDCTimeType.BEFORE, "2016", null, null);
            markLDCTime(model, eventStartPosition, unknown, endBefore, system);
            Resource time = markLDCTime(model, eventStartPosition, unknown, endBefore, system);
            markJustification(time, utils.makeValidJustification());

            // Create an attack event with an unknown start date, but definite end date
            final Resource eventAttackUnknown = utils.makeValidAIFEvent(SeedlingOntology.Conflict_Attack);
            LDCTimeComponent start = new LDCTimeComponent(LDCTimeComponent.LDCTimeType.AFTER, "2014", "--02", null);
            LDCTimeComponent end = new LDCTimeComponent(LDCTimeComponent.LDCTimeType.ON, "2014", "--02", "---21");
            markLDCTime(model, eventAttackUnknown, start, end, system);
            time = markLDCTime(model, eventAttackUnknown, start, end, system);
            markJustification(time, utils.makeValidJustification());

            utils.testValid("create an event with LDCTime");
        }

        /**
         * Create justifications and cluster memberships with and without optional URIs.
         * Without a URI, justifications and cluster memberships will be blank nodes.
         */
        @Nested
        class testOptionalURIs {
            private int uriCount;
            private double confidence;
            private BoundingBox boundingBox;
            private Resource person1;
            private Resource person2;
            private ImmutableSet<Resource> personCollection;
            private ImmutableSet<Resource> gpeCollection;

            @BeforeEach
            void setup() {
                uriCount = 0;
                confidence = 1.0;
                boundingBox = new BoundingBox(new Point(123, 45), new Point(167, 98));

                person1 = utils.makeValidAIFEntity(SeedlingOntology.Person);
                person2 = utils.makeValidAIFEntity(SeedlingOntology.Person);
                personCollection = ImmutableSet.of(person1, person2);

                gpeCollection = ImmutableSet.of(utils.makeValidAIFEntity(SeedlingOntology.GeopoliticalEntity),
                        utils.makeValidAIFEntity(SeedlingOntology.GeopoliticalEntity));
            }

            /**
             * Create text justifications with and without optional URIs.  Without a URI, a blank node is created.
             */
            @Test
            void textJustification() {
                final int startOffset = 2;
                final int endOffsetInclusive = 4;

                makeTextJustification(model, utils.getDocumentName(), startOffset, endOffsetInclusive, system, confidence);
                makeTextJustification(model, utils.getDocumentName(), startOffset*2, endOffsetInclusive*2, system, confidence, utils.getUri("custom-uri-" + ++uriCount));
                markTextJustification(model, person1, utils.getDocumentName(), startOffset*3, endOffsetInclusive*3, system, confidence);
                markTextJustification(model, person2, utils.getDocumentName(), startOffset*4, endOffsetInclusive*4, system, confidence, utils.getUri("custom-uri-" + ++uriCount));
                markTextJustification(model, personCollection, utils.getDocumentName(), startOffset*5, endOffsetInclusive*5, system, confidence);
                markTextJustification(model, gpeCollection, utils.getDocumentName(), startOffset*6, endOffsetInclusive*6, system, confidence, utils.getUri("custom-uri-" + ++uriCount));

                utils.testValid("textJustification with and without optional URI argument");
            }

            /**
             * Create image justifications with and without optional URIs.  Without a URI, a blank node is created.
             */
            @Test
            void imageJustification() {
                makeImageJustification(model, utils.getDocumentName(), boundingBox, system, confidence);
                makeImageJustification(model, utils.getDocumentName(), boundingBox, system, confidence, utils.getUri("custom-uri-" + ++uriCount));
                markImageJustification(model, person1, utils.getDocumentName(), boundingBox, system, confidence);
                markImageJustification(model, person2, utils.getDocumentName(), boundingBox, system, confidence, utils.getUri("custom-uri-" + ++uriCount));
                markImageJustification(model, personCollection, utils.getDocumentName(), boundingBox, system, confidence);
                markImageJustification(model, gpeCollection, utils.getDocumentName(), boundingBox, system, confidence, utils.getUri("custom-uri-" + ++uriCount));

                utils.testValid("imageJustification with and without optional URI argument");
            }

            /**
             * Create keyFrame justifications with and without optional URIs.  Without a URI, a blank node is created.
             */
            @Test
            void keyFrameJustification() {
                final String keyFrame = "Keyframe ID#";

                makeKeyFrameVideoJustification(model, utils.getDocumentName(), keyFrame+1, boundingBox, system, confidence);
                makeKeyFrameVideoJustification(model, utils.getDocumentName(), keyFrame+2, boundingBox, system, confidence, utils.getUri("custom-uri-" + ++uriCount));
                markKeyFrameVideoJustification(model, person1, utils.getDocumentName(), keyFrame+3, boundingBox, system, confidence);
                markKeyFrameVideoJustification(model, person2, utils.getDocumentName(), keyFrame+4, boundingBox, system, confidence, utils.getUri("custom-uri-" + ++uriCount));
                markKeyFrameVideoJustification(model, personCollection, utils.getDocumentName(), keyFrame+5, boundingBox, system, confidence);
                markKeyFrameVideoJustification(model, gpeCollection, utils.getDocumentName(), keyFrame+6, boundingBox, system, confidence, utils.getUri("custom-uri-" + ++uriCount));

                utils.testValid("keyFrameJustification with and without optional URI argument");
            }

            /**
             * Create shot justifications with and without optional URIs.  Without a URI, a blank node is created.
             */
            @Test
            void shotJustification() {
                final String shotId = "Shot ID#";

                makeShotVideoJustification(model, utils.getDocumentName(), shotId+1, system, confidence);
                makeShotVideoJustification(model, utils.getDocumentName(), shotId+2, system, confidence, utils.getUri("custom-uri-" + ++uriCount));
                markShotVideoJustification(model, person1, utils.getDocumentName(), shotId+3, system, confidence);
                markShotVideoJustification(model, person2, utils.getDocumentName(), shotId+4, system, confidence, utils.getUri("custom-uri-" + ++uriCount));
                markShotVideoJustification(model, personCollection, utils.getDocumentName(), shotId+5, system, confidence);
                markShotVideoJustification(model, gpeCollection, utils.getDocumentName(), shotId+6, system, confidence, utils.getUri("custom-uri-" + ++uriCount));

                utils.testValid("shotJustification with and without optional URI argument");
            }

            /**
             * Create audio justifications with and without optional URIs.  Without a URI, a blank node is created.
             */
            @Test
            void audioJustification() {
                final Double startTimestamp = 5.0;
                final Double endTimestamp = 10.0;

                makeAudioJustification(model, utils.getDocumentName(), startTimestamp, endTimestamp, system, confidence);
                makeAudioJustification(model, utils.getDocumentName(), startTimestamp*1.1, endTimestamp*1.1, system, confidence, utils.getUri("custom-uri-" + ++uriCount));
                markAudioJustification(model, person1, utils.getDocumentName(), startTimestamp*1.2, endTimestamp*1.2, system, confidence);
                markAudioJustification(model, person2, utils.getDocumentName(), startTimestamp*1.3, endTimestamp*1.3, system, confidence, utils.getUri("custom-uri-" + ++uriCount));
                markAudioJustification(model, personCollection, utils.getDocumentName(), startTimestamp*1.4, endTimestamp*1.4, system, confidence);
                markAudioJustification(model, gpeCollection, utils.getDocumentName(), startTimestamp*1.5, endTimestamp*1.5, system, confidence, utils.getUri("custom-uri-" + ++uriCount));

                utils.testValid("audioJustification with and without optional URI argument");
            }

            /**
             * Create cluster memberships with and without optional URIs.  Without a URI, a blank node is created.
             */
            @Test
            void possibleClusterMember() {
                // Two people, probably the same person
                final Resource putin = makeEntity(model, putinDocumentEntityUri, system);
                markType(model, utils.getAssertionUri(), putin, SeedlingOntology.Person, system, 1.0);
                markName(putin, "Путин");

                final Resource vladimirPutin = makeEntity(model, utils.getUri("E780885.00311"), system);
                markType(model, utils.getAssertionUri(), vladimirPutin, SeedlingOntology.Person, system, 1.0);
                markName(vladimirPutin, "Vladimir Putin");

                // create a cluster with prototype
                final Resource putinCluster = makeClusterWithPrototype(model, utils.getClusterUri(), putin, system);

                // person 1 is definitely in the cluster, person 2 is probably in the cluster
                markAsPossibleClusterMember(model, putin, putinCluster, 1.0, system);
                markAsPossibleClusterMember(model, vladimirPutin, putinCluster, 0.71, system, utils.getUri("clusterMembershipURI"));

                utils.testValid("possibleClusterMember with and without optional URI argument");
            }
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
            makeEntity(model, utils.getEntityUri(), system);
            utils.expect(ShaclShapes.EntityShape, SH.SPARQLConstraintComponent, null);
            utils.testInvalid("Invalid: entity with missing type");
        }

        @Test
        void eventMissingType() {
            // having multiple type assertions in case of uncertainty is ok, but there must always
            // be at least one type assertion
            makeEvent(model, utils.getEventUri(), system);
            utils.expect(ShaclShapes.EventRelationShape, SH.SPARQLConstraintComponent, null);
            utils.testInvalid("Invalid: event missing type");
        }

        @Test
        void nonTypeUsedAsType() {
            // use a blank node as the bogus entity type
            utils.makeValidAIFEntity(model.createResource());
            utils.expect(ShaclShapes.EntitySubclass, SH.HasValueConstraintComponent, null);
            utils.expect(ShaclShapes.EntitySubclass, SH.MinCountConstraintComponent, null);
            utils.testInvalid("Invalid: non-type used as type");
        }

        @Test
        void relationOfUnknownType() {
            final Resource personEntity = utils.makeValidAIFEntity(SeedlingOntology.Person);
            final Resource louisvilleEntity = utils.makeValidAIFEntity(SeedlingOntology.GeopoliticalEntity);
            final Resource relation = utils.makeValidAIFRelation(model.createResource(ONTOLOGY_NS + "unknown_type"));
            markAsArgument(model, relation, SeedlingOntology.Physical_Resident_Resident, personEntity, system, 1.0);
            markAsArgument(model, relation, SeedlingOntology.Physical_Resident_Place, louisvilleEntity, system, 1.0);

            utils.expect(ShaclShapes.RelationSubclass, SH.HasValueConstraintComponent, null);
            utils.expect(ShaclShapes.RelationSubclass, SH.MinCountConstraintComponent, null);
            utils.testInvalid("Invalid: relation of unknown type");
        }

        @Test
        void justificationMissingConfidence() {
            final Resource entity = utils.makeValidAIFEntity(SeedlingOntology.Person);

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

            utils.expect(ShaclShapes.RequiredConfidencePropertyShape, SH.MinCountConstraintComponent, null);
            utils.testInvalid("Invalid: justification missing confidence");
        }

        // this validation constraint is not working yet
        @Disabled("Missing RDF type constraint not implemented")
        @Test
        void missingRdfTypeOnNamedNode() {
            // below we copy the code from AIFUtils.makeEntity but forget to mark it as an entity
            final Resource entity = model.createResource("http://www.test.edu/entity/1");
            entity.addProperty(AidaAnnotationOntology.SYSTEM_PROPERTY, system);
            utils.testInvalid("Invalid: missing rdf type");
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

    private ImmutablePair<Model, Dataset> readModelFromDisk(Path filename) {
        try {
            ImmutablePair<Model, Dataset> pair = createDiskBasedModel();
            RDFDataMgr.read(pair.getLeft(), Files.newInputStream(filename), Lang.TURTLE);
            return pair;
        } catch (Exception e) {
            System.err.println("Unable to write to tempfile " + e.getMessage());
            e.printStackTrace();
        }
        return null;
    }

    private ImmutablePair<Model, Dataset> createDiskBasedModel() {
        try {
            Path dataModelDir = Paths.get(DISKBASED_MODEL_PATH, "testmodel" + ++diskModelCount);
            System.out.println("Creating disk based model at " + dataModelDir.toString());
            Files.createDirectories(dataModelDir);
            final Dataset dataset = TDBFactory.createDataset(dataModelDir.toString());
            final Model model = dataset.getDefaultModel();
            return new ImmutablePair<>(addNamespacesToModel(model), dataset);
        } catch (Exception e) {
            System.err.println("Unable to create temp directory: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private Model addNamespacesToModel(Model model) {
        // adding namespace prefixes makes the Turtle output more readable
        model.setNsPrefix("ldcOnt", ONTOLOGY_NS);
        model.setNsPrefix("ldc", LDC_NS);
        return model;
    }
}
