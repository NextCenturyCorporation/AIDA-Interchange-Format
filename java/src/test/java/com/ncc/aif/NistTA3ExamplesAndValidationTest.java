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
    //private static final String ONTOLOGY_NS = NIST_ROOT + "ontologies/LDCOntology#";
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
            // @Nested
            // class ClaimComponentTest {
            // String claimComponentURI = "https://www.wikidata.org/wiki/Q8440";

            // ClaimComponent validComponent = new ClaimComponent()
            // .setName("Hugo Chávez")
            // .setIdentity("Q8440")
            // .addType("Q82955"); // Politician

            // @Test
            // void validMininmal() {
            // validComponent.addToModel(model,
            // utils.getUri("a_valid_minimal_claimcomponent"), system);
            // //utils.testValid("Create minimal valid ClaimComponent");
            // utils.testValid("Create minimal valid");
            // }

            // @Test
            // void validFull() {

            // // Resource validProtoType1 = makeEntity(model, utils.getUri("someTestURI1"),
            // system);
            // // Resource validSameAsCluster1 = AIFUtils.makeAIFResource(model,
            // "http://www.caci.com/cluster/SameAsCluster/ClusterID1",
            // InterchangeOntology.SameAsCluster, system)
            // // .addProperty(InterchangeOntology.prototype, validProtoType1);

            // validComponent.setProvenance("Hugo Chavez")
            // //.setKE(validSameAsCluster1)
            // .addToModel(model, utils.getUri("a_valid_full_claimcomponent"), system);
            // utils.testValid("Create full valid ClaimComponent");
            // }

            // @Test
            // void invalidMissingType() {
            // validComponent.setTypes(Collections.emptySet()) // remove types
            // .addToModel(model, utils.getUri("an_invalid_missing_type"), system);
            // utils.expect(null, SH.MinCountConstraintComponent, null);
            // utils.testInvalid(
            // "ClaimComponent.invalid (missing type): ClaimComponent must have a type");
            // }

            // @Test
            // void invalidTooManyTypes(){

            // //Test max type count of 5
            // validComponent.addType("Q829551")
            // .addType("Q829552")
            // .addType("Q829553")
            // .addType("Q829554")
            // .addType("Q829555")
            // .addType("Q829556")
            // .addType("Q829557")
            // .addToModel(model, utils.getUri("an_invalid_claimcomponent_too_many_types"),
            // system);

            // utils.expect(null, SH.MaxCountConstraintComponent, null);
            // utils.testInvalid(
            // "ClaimComponent.invalid (Too many type): ClaimComponent must max 5 types");

            // }
            // }

            /**
             * Create ClaimComponent objects
             */
            @Nested
            class ClaimTest {
                Resource validXComponent;
                Resource validComponentKE;
                Resource validClaimerComponent;
                Resource validClaimLocationComponent;
                Resource validProtoType1;
                Resource validProtoType2;
                Resource validProtoType3;
                Resource validSameAsCluster1;
                Resource validSameAsCluster2;
                Resource validSameAsCluster3;
                Claim validClaim;

                @BeforeEach
                void setup() {

                    validProtoType1 = makeEntity(model, utils.getUri("someTestURI1"), system);
                    markHandle(validProtoType1, "ClusterID1");

                    validProtoType2 = makeEntity(model, utils.getUri("someTestURI2"), system);
                    markHandle(validProtoType2, "ClusterID2");

                    validProtoType3 = makeEntity(model, utils.getUri("someTestURI3"), system);
                    markHandle(validProtoType3, "ClusterID3");

                    validSameAsCluster1 = AIFUtils
                            .makeAIFResource(model, "http://www.caci.com/cluster/SameAsCluster/ClusterID1",
                                    InterchangeOntology.SameAsCluster, system)
                            .addProperty(InterchangeOntology.prototype, validProtoType1);
                    validSameAsCluster2 = AIFUtils
                            .makeAIFResource(model, "http://www.caci.com/cluster/SameAsCluster/ClusterID2",
                                    InterchangeOntology.SameAsCluster, system)
                            .addProperty(InterchangeOntology.prototype, validProtoType2);
                    validSameAsCluster3 = AIFUtils
                            .makeAIFResource(model, "http://www.caci.com/cluster/SameAsCluster/ClusterID3",
                                    InterchangeOntology.SameAsCluster, system)
                            .addProperty(InterchangeOntology.prototype, validProtoType3);

                    validComponentKE = validSameAsCluster1;

                    validXComponent = new ClaimComponent()
                            .setName("Some Agency")
                            .setIdentity("Q37230")
                            .addType("Q47913") // Politician
                            .addToModel(model, "https://www.wikidata.org/wiki/Q37230", system);

                    validClaimerComponent = new ClaimComponent()
                            .setName("Some News Outlet")
                            .setIdentity("Q48340")
                            .addType("Q7892363") // Politician
                            .addToModel(model, "https://www.wikidata.org/wiki/Q48340", system);

                    validClaimLocationComponent = new ClaimComponent()
                            .setName("Some Country")
                            .setIdentity("Q717")
                            .addType("Q3624078") // Politician
                            .addToModel(model, "https://www.wikidata.org/wiki/Q717", system);

                    validClaim = new Claim()
                            .setClaimId("SomeIDGoesHere")
                            .setSourceDocument("Some source")
                            .setTopic("Some Main Topic: Death of Hugo Chavez")
                            .setSubtopic("Some Sub TubTopic: Who killed Hugo Chavez")
                            .setClaimTemplate("X killed Hugo Chavez")
                            .addXVariable(validXComponent)
                            .setNaturalLanguageDescription("Claimer Y claims X killed Hugo Chavez")
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

                    String someOtherClaimFrame1 = "someOtherClaimID1";
                    String someOtherClaimFrame2 = "someOtherClaimID2";
                    String someOtherClaimFrame3 = "someOtherClaimID3";

                    validClaim
                            .setImportance(1d)
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
                            "2013-01-xx", "2013-12-xx", "2014-01-xx", "2014-12-xx", system));

                    validClaim.addToModel(model, utils.getUri("a_full_claimframe"), system);

                    utils.testValid("Create full valid claim frame");
                }

                @Test
                void invalidMissingClaimID() {
                    validClaim
                            .setImportance(1d)
                            .setClaimId("claimId")
                            .setClaimTemplate(null);

                }

                @Test
                void invalidMissingXVariable() {
                    validClaim.setXVariable(Collections.emptySet()).addToModel(model,
                            utils.getUri("claim"), system);
                    utils.expect(null, SH.MinCountConstraintComponent, null);
                    utils.testInvalid("ClaimTest.invalid (missing x variable): Claim must have X Variable");
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
