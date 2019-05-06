package com.ncc.aif;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.nio.charset.StandardCharsets;

import static com.ncc.aif.AIFUtils.*;

@TestInstance(Lifecycle.PER_CLASS)
public class NistHypothesisExamplesAndValidationTest {
    // Set this flag to true if attempting to get examples
    private static final boolean FORCE_DUMP = false;

    private static final String LDC_NS = "https://tac.nist.gov/tracks/SM-KBP/2018/LdcAnnotations#";
    private static final String ONTOLOGY_NS = "https://tac.nist.gov/tracks/SM-KBP/2018/ontologies/SeedlingOntology#";
    private static final CharSource SEEDLING_ONTOLOGY = Resources.asCharSource(
            Resources.getResource("com/ncc/aif/ontologies/SeedlingOntology"),
            StandardCharsets.UTF_8);
    private static NistTA3TestUtils utils;

    @BeforeAll
    static void declutterLogging() {
        // prevent too much logging from obscuring the Turtle examples which will be printed
        ((Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        utils = new NistTA3TestUtils(LDC_NS, ValidateAIF.create(ImmutableSet.of(SEEDLING_ONTOLOGY),
                ValidateAIF.Restriction.NIST_HYPOTHESIS), FORCE_DUMP);
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
     * Set of tests to show that NIST Hypothesis restrictions pass and fail appropriately
     */
    @Nested
    class NISTHypothesisExamples {
        Resource entity;
        Resource entityCluster;
        Resource event;
        Resource eventEdge;

        @BeforeEach
        void setup() {
            ImmutablePair<Resource, Resource> aPair = utils.makeValidNistTA3Entity(SeedlingOntology.Person, "entityHandle");
            entity = aPair.getKey();
            entityCluster = aPair.getValue();
            aPair = utils.makeValidNistTA3Event(SeedlingOntology.Conflict_Attack, 104.0);
            event = aPair.getKey();
            eventEdge = utils.makeValidTA3Edge(event, SeedlingOntology.Conflict_Attack_Attacker, entity, 101.0);
        }

        // Exactly 1 hypothesis should exist in model
        @Nested
        class SingleHypothesis {

            @Test
            void invalidTooMany() {
                utils.makeValidTA3Hypothesis(entity, event, eventEdge);
                utils.makeValidTA3Hypothesis(101.0, entity, event, eventEdge);
                utils.testInvalid("NISTHypothesis.invalid (too many): there should be exactly 1 hypothesis");
            }

            @Test
            void invalidTooFew() {
                utils.testInvalid("NISTHypothesis.invalid (too few): there should be exactly 1 hypothesis");
            }

            @Test
            void valid() {
                utils.makeValidTA3Hypothesis(entity, event, eventEdge);
                utils.testValid("NISTHypothesis.valid: there should be exactly 1 hypothesis");
            }
        }

        // Each entity (cluster) in the hypothesis graph must have exactly one handle
        @Nested
        class EntityClusterRequiredHandle {

            @Test
            // No handle property on entity cluster in hypothesis
            void invalidNoHandle() {
                final Resource newEntity = utils.makeValidNistEntity(SeedlingOntology.Person).getKey();
                utils.makeValidTA3Hypothesis(entity, newEntity, event, eventEdge);

                utils.testInvalid("NISTHypothesis.invalid (no handle exists): Each entity cluster in the hypothesis " +
                        "graph must have exactly one handle");
            }

            @Test
            // Two handle properties on entity cluster in hypothesis
            void invalidMultipleHandles() {
                final ImmutablePair<Resource, Resource> entityPair = utils.makeValidNistTA3Entity(SeedlingOntology.Person, "handle2");
                final Resource newEntity = entityPair.getKey();
                final Resource cluster = entityPair.getValue();
                cluster.addProperty(AidaAnnotationOntology.HANDLE, "handle3");
                utils.makeValidTA3Hypothesis(entity, newEntity, event, eventEdge);

                utils.testInvalid("NISTHypothesis.invalid (multiple handles exist): Each entity cluster in the " +
                        "hypothesis graph must have exactly one handle");
            }

            @Test
            // One handle on entity cluster in hypothesis
            void valid() {
                utils.makeValidTA3Hypothesis(entity, event, eventEdge);
                utils.testValid("NISTHypothesis.valid: Each entity cluster in the hypothesis graph must have " +
                        "exactly one handle");
            }
        }

        // Each hypothesis graph must have exactly one hypothesis importance value
        @Nested
        class HypothesisImportanceValue {

            @Test
            void invalid() {
                //invalid hypothesis, no importance value
                makeHypothesis(model, utils.getHypothesisUri(), ImmutableSet.of(entity, event, eventEdge), system);
                utils.testInvalid("NISTHypothesis.invalid (hypothesis has no importance value): Each hypothesis " +
                        "graph must have exactly one hypothesis importance value");
            }

            @Test
            void valid() {
                utils.makeValidTA3Hypothesis(entity, event, eventEdge);
                utils.testValid("NISTHypothesis.valid: Each hypothesis graph must have exactly one" +
                        " hypothesis importance value");
            }
        }

        // Each event or relation (cluster) in the hypothesis must have exactly one importance value
        @Nested
        class HypothesisEventRelationClusterImportanceValue {
            Resource relation;
            Resource eventCluster;
            Resource relationCluster;
            Resource relationEdge;

            @BeforeEach
            void setup() {
                ImmutablePair<Resource, Resource> relationPair = utils.makeValidNistRelation(SeedlingOntology.GeneralAffiliation_APORA);
                relation = relationPair.getKey();
                relationCluster = relationPair.getValue();
                eventCluster = makeClusterWithPrototype(model, utils.getClusterUri(), event, system);

                // This isn't strictly needed to be valid, but it's here because the example looks incomplete if an
                // entity has a relationship without a relation edge defining that relationship.
                relationEdge = utils.makeValidTA3Edge(relation, SeedlingOntology.GeneralAffiliation_APORA_Affiliate, entity, 102.0);

                utils.makeValidTA3Hypothesis(entity, event, eventEdge, relation, relationEdge);
            }

            @Test
            void invalidEvent() {
                //invalid event cluster, no importance value
                markImportance(relationCluster, 99.0);
                utils.testInvalid("NISTHypothesis.invalid (event cluster has no importance value): Each event or " +
                        "relation (cluster) in the hypothesis must have exactly one importance value");
            }

            @Test
            void invalidRelation() {
                //invalid relation cluster, no importance value
                markImportance(eventCluster, 88.0);
                utils.testInvalid("NISTHypothesis.invalid (relation cluster has no importance value): Each event or " +
                        "relation (cluster) in the hypothesis must have exactly one importance value");
            }

            @Test
            void valid() {
                markImportance(eventCluster, 88.0);
                markImportance(relationCluster, 99.0);
                utils.testValid("NISTHypothesis.valid: Each event or relation (cluster) in the hypothesis must " +
                        "have exactly one importance value");
            }
        }

        // Each edge KE in the hypothesis graph must have exactly one edge importance value
        @Nested
        class HypothesisEdgeImportanceValue {
            Resource relation;

            @BeforeEach
            void setup() {
                ImmutablePair<Resource, Resource> relationPair = utils.makeValidNistTA3Relation(SeedlingOntology.GeneralAffiliation_APORA, 88);
                relation = relationPair.getKey();
            }

            @Test
            void invalidEventEdge() {
                //invalid event argument, needs importance value
                Resource invalidEventEdge = markAsArgument(model, event, SeedlingOntology.Personnel_Elect_Elect,
                        entity, system, 0.785, "event-argument-1");

                utils.makeValidTA3Hypothesis(entity, event, eventEdge, relation, invalidEventEdge);

                utils.testInvalid("NISTHypothesis.invalid (event edge has no importance value): Each edge KE in the " +
                        "hypothesis graph must have exactly one edge importance value");
            }

            @Test
            void invalidRelationEdge() {
                //invalid relation argument, needs importance value
                Resource invalidRelationEdge = markAsArgument(model, relation, SeedlingOntology.GeneralAffiliation_APORA_Affiliation,
                        entity, system, 0.785, "relation-argument-1");

                utils.makeValidTA3Hypothesis(entity, event, eventEdge, relation, invalidRelationEdge);

                utils.testInvalid("NISTHypothesis.invalid (relation edge has no importance value): Each edge KE in the " +
                        "hypothesis graph must have exactly one edge importance value");
            }

            @Test
            void validEventEdge() {
                utils.makeValidTA3Hypothesis(entity, event, eventEdge, relation);

                utils.testValid("NISTHypothesis.valid (event edge has importance value): Each edge KE in the " +
                        "hypothesis graph must have exactly one edge importance value");
            }

            @Test
            void validRelationEdge() {
                // link entity to the relation
                Resource relationEdge = utils.makeValidTA3Edge(relation, SeedlingOntology.GeneralAffiliation_APORA_Affiliation, entity, 120.0);
                utils.makeValidTA3Hypothesis(entity, event, eventEdge, relation, relationEdge);

                utils.testValid("NISTHypothesis.valid (relation edge has importance value): Each edge KE in the " +
                        "hypothesis graph must have exactly one edge importance value");
            }
        }

        @Nested
        class KEsInHypothesisMustBeDefined {

            @Test
            void invalid() {
                Resource fakeEntity = model.createResource(utils.getEntityUri());
                utils.makeValidTA3Hypothesis(fakeEntity, entity, event, eventEdge);
                utils.testInvalid("NISTHypothesis.invalid (entity is not defined): All KEs referenced by hypothesis " +
                        "must be defined in model");
            }

            @Test
            void valid() {
                utils.makeValidTA3Hypothesis(entity, event, eventEdge);
                utils.testValid("NISTHypothesis.valid: All KEs referenced by hypothesis must be defined in model");
            }
        }

        @Nested
        class KEsInModelMustBeReferencedByHypothesis {
            Resource relation;
            Resource relationEdge;

            @BeforeEach
            void setup() {
                ImmutablePair<Resource, Resource> relationPair = utils.makeValidNistTA3Relation(SeedlingOntology.GeneralAffiliation_APORA, 103.0);
                relation = relationPair.getKey();
                relationEdge = utils.makeValidTA3Edge(relation, SeedlingOntology.GeneralAffiliation_APORA_Affiliate, entity, 102.0);
            }

            @Test
            void invalid() {
                utils.makeValidTA3Hypothesis(entity, relation, relationEdge);
                utils.testInvalid("NISTHypothesis.invalid (event and event edge is not referenced in hypothesis): " +
                        "All KEs in model must be referenced by hypothesis");
            }

            @Test
            void valid() {
                utils.makeValidTA3Hypothesis(entity, relation, relationEdge, event, eventEdge);
                utils.testValid("NISTHypothesis.valid: All KEs in model must be referenced by hypothesis");
            }
        }

        // Each hypothesis graph must have at least one event or relation with at least one edge.
        @Nested
        class HypothesisRequiredOneEventOrRelationWithOneEdge {

            @Test
            void invalid() {
                //remove everything in the model to ensure no edge KE's exist
                NistHypothesisExamplesAndValidationTest.this.setup();
                ImmutablePair<Resource, Resource> pair = utils.makeValidNistTA3Entity(SeedlingOntology.Person, "entityHandle");
                entity = pair.getKey();
                entityCluster = pair.getValue();
                utils.makeValidTA3Hypothesis(entity);
                utils.testInvalid("NISTHypothesis.invalid (no event or relation exists): Each hypothesis graph must " +
                        "have at least one event or relation with at least one edge.");
            }

            @Test
            void invalidRelationAndEventEdge() {
                final Resource relation = utils.makeValidNistTA3Relation(SeedlingOntology.GeneralAffiliation_APORA, 103.0).getKey();

                //create invalid relation edge with event argument type
                final Resource invalidRelationEdge = utils.makeValidTA3Edge(relation, SeedlingOntology.Conflict_Attack_Attacker, entity, 102.0);

                utils.makeValidTA3Hypothesis(entity, event, eventEdge, relation, invalidRelationEdge);
                utils.testInvalid("NISTHypothesis.invalid (event has invalid relation edge): Each hypothesis graph " +
                        "must have at least one event or relation with at least one edge.");
            }

            // TODO This test case needs to be updated and @Test needs to be added back in  once we decide on the
            // TODO new design of this class upon the completion of AIDA-720.
            void validRelationAndRelationEdge() {
                final Resource relation = utils.makeValidNistTA3Relation(SeedlingOntology.GeneralAffiliation_APORA, 103.0).getKey();
                final Resource relationEdge = utils.makeValidTA3Edge(relation, SeedlingOntology.GeneralAffiliation_APORA_Affiliate, entity, 102.0);

                utils.makeValidTA3Hypothesis(entity, event, eventEdge, relation, relationEdge);
                utils.testValid("NISTHypothesis.valid (relation has relation edge): Each hypothesis graph must have " +
                        "at least one event or relation with at least one edge.");
            }

            @Test
            void validEventAndEventEdge() {
                utils.makeValidTA3Hypothesis(entity, event, eventEdge);
                utils.testValid("NISTHypothesis.valid (event has event edge): Each hypothesis graph must have " +
                        "at least one event or relation with at least one edge.");
            }

            @Test
            void validEventRelationAndEventRelationEdge() {
                final Resource relation = utils.makeValidNistTA3Relation(SeedlingOntology.GeneralAffiliation_APORA, 103.0).getKey();
                final Resource relationEdge = utils.makeValidTA3Edge(relation, SeedlingOntology.GeneralAffiliation_APORA_Affiliate, entity, 102.0);

                utils.makeValidTA3Hypothesis(entity, event, eventEdge, relation, relationEdge);
                utils.testValid("NISTHypothesis.valid (event has event edge and relation has relation edge): Each " +
                        "hypothesis graph must have at least one event or relation with at least one edge.");
            }
        }

        // Clusters must be homogeneous by base class (Entity, Event, or Relation)
        @Nested
        class HypothesisClustersMustBeHomogeneous {
            Resource relation;
            Resource relationEdge;
            Resource relationCluster;

            @BeforeEach
            void setup() {
                ImmutablePair<Resource, Resource> relationPair = utils.makeValidNistTA3Relation(SeedlingOntology.GeneralAffiliation_APORA, 103.0);
                relation = relationPair.getKey();
                relationCluster = relationPair.getValue();
                relationEdge = utils.makeValidTA3Edge(relation, SeedlingOntology.GeneralAffiliation_APORA_Affiliate, entity, 102.0);
            }

            @Test
            void invalid() {
                // create event cluster member to add to relation cluster
                final Resource eventMember = utils.makeValidNistTA3Event(SeedlingOntology.Conflict_Attack, 103.0).getKey();

                //add invalid event cluster member to relation cluster
                markAsPossibleClusterMember(model, eventMember, relationCluster, 1.0, system);

                utils.makeValidTA3Hypothesis(entity, event, eventEdge, eventMember, relation, relationEdge);
                utils.testInvalid("NISTHypothesis.invalid (event exists in relation cluster): Clusters must be " +
                        "homogeneous by base class (Entity, Event, or Relation).");
            }

            @Test
            void valid() {
                // create relation cluster member to add to relation cluster
                final Resource relationMember = makeRelation(model, utils.getRelationUri(), system);
                markJustification(utils.addType(relationMember, SeedlingOntology.GeneralAffiliation_APORA), utils.makeValidJustification());

                //add valid relation cluster member to relation cluster
                markAsPossibleClusterMember(model, relationMember, relationCluster, 1.0, system);

                utils.makeValidTA3Hypothesis(entity, event, eventEdge, relation, relationEdge, relationMember);
                utils.testValid("NISTHypothesis.valid: Clusters must be homogeneous by base class " +
                        "(Entity, Event, or Relation)");
            }
        }
    }

    private void addNamespacesToModel(Model model) {
        // adding namespace prefixes makes the Turtle output more readable
        model.setNsPrefix("ldcOnt", ONTOLOGY_NS);
        model.setNsPrefix("ldc", LDC_NS);
    }
}
