package com.ncc.aif;

import static com.ncc.aif.AIFUtils.addSourceDocumentToJustification;
import static com.ncc.aif.AIFUtils.makeClusterWithPrototype;
import static com.ncc.aif.AIFUtils.makeEntity;
import static com.ncc.aif.AIFUtils.makeEvent;
import static com.ncc.aif.AIFUtils.makeRelation;
import static com.ncc.aif.AIFUtils.makeSystemWithURI;
import static com.ncc.aif.AIFUtils.makeTextJustification;
import static com.ncc.aif.AIFUtils.markAsArgument;
import static com.ncc.aif.AIFUtils.markAsPossibleClusterMember;
import static com.ncc.aif.AIFUtils.markAttribute;
import static com.ncc.aif.AIFUtils.markCompoundJustification;
import static com.ncc.aif.AIFUtils.markConfidence;
import static com.ncc.aif.AIFUtils.markEdgesAsMutuallyExclusive;
import static com.ncc.aif.AIFUtils.markHandle;
import static com.ncc.aif.AIFUtils.markInformativeJustification;
import static com.ncc.aif.AIFUtils.markJustification;
import static com.ncc.aif.AIFUtils.markKeyFrameVideoJustification;
import static com.ncc.aif.AIFUtils.markLDCTime;
import static com.ncc.aif.AIFUtils.markLDCTimeRange;
import static com.ncc.aif.AIFUtils.markName;
import static com.ncc.aif.AIFUtils.markNumericValueAsString;
import static com.ncc.aif.AIFUtils.markShotVideoJustification;
import static com.ncc.aif.AIFUtils.markSystem;
import static com.ncc.aif.AIFUtils.markTextJustification;
import static com.ncc.aif.AIFUtils.markTextValue;
import static com.ncc.aif.AIFUtils.markType;

import java.util.Collections;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.ncc.aif.AIFUtils.BoundingBox;
import com.ncc.aif.AIFUtils.LDCTimeComponent;
import com.ncc.aif.AIFUtils.Point;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;
import org.topbraid.shacl.vocabulary.SH;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

@TestInstance(Lifecycle.PER_CLASS)
public class NistTA3ExamplesAndValidationTest {
        // Modify these flags to control how tests output their models/reports and if
        // so, how they output them
        // When DUMP_ALWAYS is false, the model is only dumped when the result is
        // unexpected (and if invalid, the report is also dumped)
        // When DUMP_ALWAYS is true, the model is always dumped, and the report is
        // always dumped if invalid
        private static final boolean DUMP_ALWAYS = true;
        // When DUMP_TO_FILE is false, if a model or report is dumped, it goes to stdout
        // WHen DUMP_TO_FILE is true, if a model or report is dumped, it goes to a file
        // in target/test-dump-output
        private static final boolean DUMP_TO_FILE = true;

        private static final String NIST_ROOT = "https://raw.githubusercontent.com/NextCenturyCorporation/AIDA-Interchange-Format/master/java/src/main/resources/com/ncc/aif/";
        private static final String LDC_NS = NIST_ROOT + "LdcAnnotations#";
        // private static final String ONTOLOGY_NS = NIST_ROOT +
        // "ontologies/LDCOntology#";
        private static NistTestUtils utils;

        @BeforeAll
        static void initTest() {
                // prevent too much logging from obscuring the Turtle examples which will be
                // printed
                ((Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
                // Set<String> ont = Set.of("com/ncc/aif/ontologies/LDCOntologyM36");
                // utils = new NistTestUtils(LDC_NS, ValidateAIF.create(ont,
                // ValidateAIF.Restriction.NIST_TA3), DUMP_ALWAYS,
                // DUMP_TO_FILE);

                utils = new NistTestUtils(LDC_NS,
                                ValidateAIF.createForDWD(ValidateAIF.Restriction.NIST_TA3), DUMP_ALWAYS,
                                DUMP_TO_FILE);
        }

        private Model model;
        private Resource system;

        @BeforeEach
        void setup() {
                model = utils.startNewTest();
                addNamespacesToModel(model);
                system = utils.getSystem();
        }

        /**
         * Set of tests to show that NIST restrictions pass and fail appropriately
         */
        @Nested
        class NISTTA3Examples {

                @Nested
                class ClaimsTests {
                        Resource validProtoType;
                        Resource validSameAsCluster;

                        /**
                         * Create ClaimComponent objects
                         */
                        @Nested
                        class ClaimTest {
                                String someOtherClaimFrame1;
                                String someOtherClaimFrame2;
                                String someOtherClaimFrame3;
                                Resource validXComponent;
                                Resource validComponentKE;
                                Resource validClaimerComponent;
                                Resource validClaimerComponent2;
                                Resource validClaimerComponent3;
                                Resource validClaimerComponent4;
                                Resource validClaimLocationComponent;
                                Resource validProtoType1;
                                Resource validProtoType2;
                                Resource validProtoType3;
                                Resource validSameAsCluster1;
                                Resource validSameAsCluster2;
                                Resource validSameAsCluster3;
                                Claim validClaim;

                                String claimComponentTestURI = "https://www.wikidata.org/wiki/Q8440";
                                ClaimComponent validComponentTest = new ClaimComponent()
                                                .setName("Hugo Chávez")
                                                .setIdentity("Q8440")
                                                .addType("Q82955"); // Politician

                                @BeforeEach
                                void setup() {

                                        ImmutablePair<Resource, Resource> aPair = utils.makeValidNistEntity(
                                                LDCOntology.PER);

                                        markHandle(aPair.getKey(), "SomeBasicCluster1");

                                        Resource entityCluster1 = aPair.getValue();
                                        Resource entityCluster2 = aPair.getValue();
                                        Resource entityCluster3 = aPair.getValue();

                                        ////////////////////////////////////////////////////////////////////////////
                                        String Entity1Uri = utils.getEntityUri();
                                        String Event1Uri = utils.getEventUri();

                                        final Resource validProtoTypeEvent1 = makeEvent(model, Event1Uri, system);
                                        markType(model, utils.getAssertionUri(), validProtoTypeEvent1, SeedlingOntology.Personnel_Elect, system, 1.0);
                
                                        final Resource validProtoTypeEntity1 = makeEntity(model, Entity1Uri, system);
                                        markType(model, utils.getAssertionUri(), validProtoTypeEntity1, SeedlingOntology.Person, system, 1.0);
                                        markHandle(validProtoTypeEntity1, "EntityClusterID1");


                                        markAsPossibleClusterMember(model, validProtoTypeEntity1, entityCluster1, .75, system);

                                        //PREDICATE AS String
                                        final Resource argument1 = markAsArgument(model, validProtoTypeEvent1, "A1_ppt_thing_bought",
                                                validProtoTypeEntity1, system, 0.785, utils.getUri("eventArgument-1"));
                                        //PREDICATE AS URI
                                        // final Resource argument1 = markAsArgument(model, validProtoTypeEvent1, SeedlingOntology.Personnel_Elect_Elect,
                                        // validProtoTypeEntity1, system, 0.785, utils.getUri("eventArgument-1"));

                                        final Resource typeAssertion1 = markType(model, utils.getAssertionUri(), validProtoTypeEvent1,
                                        SeedlingOntology.Personnel_Elect_Elect, system, 1.0);
                                        final Resource justification1 = markTextJustification(model, typeAssertion1, "HC00002Z0", 0, 10,
                                        system, 1d);

                                        markInformativeJustification(validProtoTypeEvent1, justification1);
                                        markInformativeJustification(validProtoTypeEntity1, justification1);

                                        addSourceDocumentToJustification(justification1, "HC00002Z0");
                
                                        markAttribute(argument1, InterchangeOntology.Negated);
                                        markAttribute(argument1, InterchangeOntology.Hedged);

                                        ////////////////////////////////////////////////////////////////////////////
                                        String Entity2Uri = utils.getEntityUri();
                                        String Event2Uri = utils.getEventUri();

                                        final Resource validProtoTypeEvent2 = makeEvent(model, Event2Uri, system);
                                        markType(model, utils.getAssertionUri(), validProtoTypeEvent2, SeedlingOntology.Personnel_Elect, system, 1.0);
                
                                        final Resource validProtoTypeEntity2 = makeEntity(model, Entity2Uri, system);
                                        markType(model, utils.getAssertionUri(), validProtoTypeEntity2, SeedlingOntology.Person, system, 1.0);
                                        markHandle(validProtoTypeEntity2, "EntityClusterID2");


                                        markAsPossibleClusterMember(model, validProtoTypeEntity2, entityCluster2, .75, system);

                                        //PREDICATE AS String
                                        final Resource argument2 = markAsArgument(model, validProtoTypeEvent2, "A1_ppt_thing_bought",
                                                validProtoTypeEntity2, system, 0.785, utils.getUri("eventArgument-2"));
                                        //PREDICATE AS URI
                                        // final Resource argument2 = markAsArgument(model, validProtoTypeEvent2, SeedlingOntology.Personnel_Elect_Elect,
                                        // validProtoTypeEntity2, system, 0.785, utils.getUri("eventArgument-2"));

                                        final Resource typeAssertion2 = markType(model, utils.getAssertionUri(), validProtoTypeEvent2,
                                        SeedlingOntology.Personnel_Elect_Elect, system, 1.0);
                                        final Resource justification2 = markTextJustification(model, typeAssertion2, "HC00002Z0", 0, 10,
                                        system, 1d);

                                        markInformativeJustification(validProtoTypeEvent2, justification2);
                                        markInformativeJustification(validProtoTypeEntity2, justification2);

                                        addSourceDocumentToJustification(justification2, "HC00002Z0");
                
                                        markAttribute(argument2, InterchangeOntology.Negated);
                                        markAttribute(argument2, InterchangeOntology.Hedged);

                                        ////////////////////////////////////////////////////////////////////////////
                                        String Entity3Uri = utils.getEntityUri();
                                        String Event3Uri = utils.getEventUri();

                                        final Resource validProtoTypeEvent3 = makeEvent(model, Event3Uri, system);
                                        markType(model, utils.getAssertionUri(), validProtoTypeEvent3, SeedlingOntology.Personnel_Elect, system, 1.0);
                
                                        final Resource validProtoTypeEntity3 = makeEntity(model, Entity3Uri, system);
                                        markType(model, utils.getAssertionUri(), validProtoTypeEntity3, SeedlingOntology.Person, system, 1.0);
                                        markHandle(validProtoTypeEntity3, "EntityClusterID3");

                                        markAsPossibleClusterMember(model, validProtoTypeEntity3, entityCluster3, .75, system);

                                        //PREDICATE AS String
                                        final Resource argument3 = markAsArgument(model, validProtoTypeEvent3, "A1_ppt_thing_bought",
                                                validProtoTypeEntity3, system, 0.785, utils.getUri("eventArgument-3"));
                                        //PREDICATE AS URI
                                        // final Resource argument3 = markAsArgument(model, validProtoTypeEvent3, SeedlingOntology.Personnel_Elect_Elect,
                                        // validProtoTypeEntity3, system, 0.785, utils.getUri("eventArgument-3"));

                                        final Resource typeAssertion3 = markType(model, utils.getAssertionUri(), validProtoTypeEvent3,
                                        SeedlingOntology.Personnel_Elect_Elect, system, 1.0);
                                        final Resource justification3 = markTextJustification(model, typeAssertion3, "YY00002Z0", 0, 10,
                                        system, 1d);

                                        markInformativeJustification(validProtoTypeEvent3, justification3);   
                                        markInformativeJustification(validProtoTypeEntity3, justification3);

                                        addSourceDocumentToJustification(justification3, "YY00002Z0");

                                        markAttribute(argument3, InterchangeOntology.Negated);
                                        markAttribute(argument3, InterchangeOntology.Hedged);


                                        validSameAsCluster1 = AIFUtils
                                                        .makeAIFResource(model,
                                                                        "http://www.caci.com/cluster/SameAsCluster/ClusterID1",
                                                                        InterchangeOntology.SameAsCluster, system)
                                                        .addProperty(InterchangeOntology.prototype, validProtoTypeEvent1);
                                        
                                        validSameAsCluster2 = AIFUtils
                                                        .makeAIFResource(model,
                                                                        "http://www.caci.com/cluster/SameAsCluster/ClusterID2",
                                                                        InterchangeOntology.SameAsCluster, system)
                                                        .addProperty(InterchangeOntology.prototype, validProtoTypeEvent2);
                                        validSameAsCluster3 = AIFUtils
                                                        .makeAIFResource(model,
                                                                        "http://www.caci.com/cluster/SameAsCluster/ClusterID3",
                                                                        InterchangeOntology.SameAsCluster, system)
                                                        .addProperty(InterchangeOntology.prototype, validProtoTypeEvent3);

                                        validComponentKE = validSameAsCluster1;

                                        validXComponent = new ClaimComponent()
                                                        .setName("Some Agency")
                                                        .setIdentity("Q37230")
                                                        .addType("Q47913") // Politician
                                                        .addToModel(model, "https://www.wikidata.org/wiki/Q37230",
                                                                        system);

                                        validClaimerComponent = new ClaimComponent()
                                                        .setName("Some News Outlet")
                                                        .setIdentity("Q48340")
                                                        .addType("Q7892363") // Politician
                                                        .addToModel(model, "https://www.wikidata.org/wiki/Q48340",
                                                                        system);

                                        validClaimerComponent2 = new ClaimComponent()
                                                        .setName("Some News Outlet")
                                                        .setIdentity("Q483402")
                                                        .addType("Q78923632") // Politician
                                                        .addToModel(model, "https://www.wikidata.org/wiki/Q483402",
                                                                        system);
                                        validClaimerComponent3 = new ClaimComponent()
                                                        .setName("Some News Outlet")
                                                        .setIdentity("Q483403")
                                                        .addType("Q78923633") // Politician
                                                        .addToModel(model, "https://www.wikidata.org/wiki/Q483403",
                                                                        system);

                                        validClaimerComponent4 = new ClaimComponent()
                                                        .setName("Some News Outlet")
                                                        .setIdentity("Q483404")
                                                        .addType("Q78923634") // Politician
                                                        .addToModel(model, "https://www.wikidata.org/wiki/Q483404",
                                                                        system);

                                        validClaimLocationComponent = new ClaimComponent()
                                                        .setName("Some Country")
                                                        .setIdentity("Q717")
                                                        .addType("Q3624078") // Politician
                                                        .addToModel(model, "https://www.wikidata.org/wiki/Q717",
                                                                        system);

                                        validClaim = new Claim()
                                                        .setClaimId("SomeIDGoesHere")
                                                        .setSourceDocument("Some source")
                                                        .setTopic("Some Main Topic: Death of Hugo Chavez")
                                                        .setSubtopic("Some Sub TubTopic: Who killed Hugo Chavez")
                                                        .setClaimTemplate("X killed Hugo Chavez")
                                                        .addXVariable(validXComponent)
                                                        .setNaturalLanguageDescription(
                                                                        "Claimer Y claims X killed Hugo Chavez")
                                                        .addClaimSementics(validSameAsCluster1)
                                                        .setClaimer(validClaimerComponent)
                                                        .addAssociatedKE(validSameAsCluster2)
                                                        .addAssociatedKE(validSameAsCluster3);

                                }

                                @Test
                                void validMinimal() {
                                        validClaim.addToModel(model, utils.getUri("a_minimal_claimframe"), system);
                                        utils.testValid("Create minimal valid claim frame");
                                }

                                @Test
                                void validFull() {

                                        someOtherClaimFrame1 = "someOtherClaimID1";
                                        someOtherClaimFrame2 = "someOtherClaimID2";
                                        someOtherClaimFrame3 = "someOtherClaimID3";

                                        validClaim.setImportance(1d)
                                                        .setClaimId("claimId")
                                                        .setQueryId("queryId")
                                                        .setClaimLocation(validClaimLocationComponent)
                                                        .setClaimMedium(validClaimLocationComponent)
                                                        .addClaimerAfilliation(validClaimerComponent)
                                                        .addIdenticalClaim(someOtherClaimFrame1)
                                                        .addRelatedClaim(someOtherClaimFrame2)
                                                        .addSupportingClaim(someOtherClaimFrame1)
                                                        .addSupportingClaim(someOtherClaimFrame2)
                                                        .addRefutingClaim(someOtherClaimFrame1)
                                                        .addRefutingClaim(someOtherClaimFrame2)
                                                        .addRefutingClaim(someOtherClaimFrame3);

                                        validClaim.setClaimDateTime(AIFUtils.makeLDCTimeRange(model,
                                                        "2013-01-xx", "2013-12-xx", "2014-01-xx", "2014-12-xx",
                                                        system));

                                        validClaim.addToModel(model, utils.getUri("a_full_claimframe"), system);
                                        utils.testValid("Create full valid claim frame"); 
                                }

                                // Test Claim requires exactly 1 claimId
                                @Test
                                void invalidMissingClaimID() {
                                        // Set claim id to null
                                        validClaim.setClaimId(null);
                                        validClaim.addToModel(model, utils.getUri("a_missing_claim_id"), system);
                                        utils.expect(null, SH.MinCountConstraintComponent, null);
                                        utils.testInvalid("ClaimTest.invalid (missing id): Claim must have an id");

                                }

                                // Test Claim missing X variable
                                @Test
                                void invalidMissingXVariable() {
                                        // Set the xvariable to empty set
                                        validClaim.setXVariable(Collections.emptySet()).addToModel(model,
                                                        utils.getUri("claim"), system);
                                        utils.expect(null, SH.MinCountConstraintComponent, null);
                                        utils.testInvalid("ClaimTest.invalid (missing x variable): Claim must have X Variable");
                                }

                                @Test
                                void invalidTooManyClaimerAffiliation() {
                                        validClaim.addClaimerAfilliation(validClaimerComponent);
                                        validClaim.addClaimerAfilliation(validClaimerComponent2);
                                        validClaim.addClaimerAfilliation(validClaimerComponent3);
                                        validClaim.addClaimerAfilliation(validClaimerComponent4);
                                        validClaim.addToModel(model, utils.getUri("too_many_claimer_affiliation"),
                                                        system);
                                        utils.expect(null, SH.MaxCountConstraintComponent, null);
                                        utils.testInvalid("ClaimTest.invalid (too many claimer affiliation): Claim can have at most 3 aida:claimerAffiliation");
                                }

                                // Test ClaimComponent too many types
                                @Test
                                void invalidTooManyClaimComponentTypes() {

                                        // Test max type count of 5 - as defined in the restricted_claimframe_aif.shacl
                                        // We are adding more than 5 types.

                                        // #########################
                                        // # 2.4 #13. Each aida:ClaimComponent must have at least one and at most 5
                                        // aida:componentType.
                                        // # defined in aida_ontology.shacl
                                        // #------------------------
                                        Resource test = validComponentTest.setName("Hugo Chávez")
                                                        .setIdentity("Q8440")
                                                        .addType("Q82955")
                                                        .addType("Q829551")
                                                        .addType("Q829552")
                                                        .addType("Q829553")
                                                        .addType("Q829554")
                                                        .addType("Q829555")
                                                        .addType("Q829556")
                                                        .addType("Q829557")
                                                        .addToModel(model, "https://www.wikidata.org/wiki/Q90000",
                                                                        system);
                                        validClaim.setClaimLocation(test);
                                        validClaim.addToModel(model, utils.getUri("too_many_claimcomponents"), system);
                                        utils.expect(null, SH.MaxCountConstraintComponent, null);

                                        utils.testInvalid("ClaimComponent.invalid (Too many type): ClaimComponent must max 5 types");

                                }

                        }

                }

        }

        private void addNamespacesToModel(Model model) {
                // adding namespace prefixes makes the Turtle output more readable
                // model.setNsPrefix("ldcOnt", ONTOLOGY_NS);
                model.setNsPrefix("ldc", LDC_NS);
        }
}
