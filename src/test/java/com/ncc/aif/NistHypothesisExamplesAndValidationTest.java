package com.ncc.aif;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.nio.charset.StandardCharsets;

import static com.ncc.aif.AIFUtils.*;

@TestInstance(Lifecycle.PER_CLASS)
public class NistHypothesisExamplesAndValidationTest {

    private static NistTestUtils utils;

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
        utils = new NistTestUtils(NAMESPACE, ValidateAIF.create(ImmutableSet.of(SEEDLING_ONTOLOGY),
                ValidateAIF.Restriction.NIST_HYPOTHESIS), false);
    }

    private Model model;
    private Resource system;

    private String getUri(String localName) {
        return utils.getUri(localName);
    }

    private String getAssertionUri() {
        return utils.getAssertionUri();
    }

    private String getEntityUri() {
        return utils.getEntityUri();
    }

    private String getClusterUri() {
        return utils.getClusterUri();
    }

    private Resource addType(Resource resource, Resource type) {
        return utils.addType(resource, type);
    }

    private void testInvalid(String name) {
        utils.testInvalid(name);
    }

    private void testValid(String name) {
        utils.testValid(name);
    }

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
        Resource justification;

        @BeforeEach
        void setup() {
            justification = makeTextJustification(model, "NYT_ENG_20181231",
                    42, 143, system, 0.973);
            addSourceDocumentToJustification(justification, "20181231");

            entity = makeEntity(model, getEntityUri(), system);
            entityCluster = makeClusterWithPrototype(model, getClusterUri(), entity, "handle", system);
            markJustification(addType(entity, SeedlingOntology.Person), justification);

            event = makeEvent(model, getUri("event-1"), system);
            markImportance(makeClusterWithPrototype(model, getClusterUri(), event, "Event", system), 104.0);
            markJustification(addType(event, SeedlingOntology.Conflict_Attack), justification);

            eventEdge = markAsArgument(model, event, SeedlingOntology.Conflict_Attack_Attacker,
                    entity, system, 1d, getAssertionUri());
            markImportance(eventEdge, 101.0);
        }

        private Resource makeValidHypothesis(Resource... resources) {
            return utils.getValidHypothesis(resources);
/* ORIGINAL CODE:
            Set<Resource> set = new HashSet<>();
            Collections.addAll(set, resources);
            final Resource hypothesis = makeHypothesis(model, getUri("hypothesis-1"), set, system);
            markImportance(hypothesis, 100.0);
            return hypothesis;
*/
        }

        // Exactly 1 hypothesis should exist in model
        @Nested
        class SingleHypothesis {

            @Test
            void invalidTooMany() {
                makeValidHypothesis(entity, event, eventEdge);
                markImportance(makeHypothesis(model, getUri("hypothesis-2"),
                        ImmutableSet.of(entity, event, eventEdge), system), 101.0);
                testInvalid("NISTHypothesis.invalid (too many): there should be exactly 1 hypothesis");
            }

            @Test
            void invalidTooFew() {
                testInvalid("NISTHypothesis.invalid (too few): there should be exactly 1 hypothesis");
            }

            @Test
            void valid() {
                makeValidHypothesis(entity, event, eventEdge);
                testValid("NISTHypothesis.valid: there should be exactly 1 hypothesis");
            }
        }

        // Each entity (cluster) in the hypothesis graph must have exactly one handle
        @Nested
        class EntityClusterRequiredHandle {

            @Test
            // No handle property on entity cluster in hypothesis
            void invalidNoHandle() {
                final Resource newEntity = makeEntity(model, getEntityUri(), system);
                makeClusterWithPrototype(model, getClusterUri(), newEntity, system);
                markJustification(addType(newEntity, SeedlingOntology.Person), justification);
                makeValidHypothesis(entity, newEntity, event, eventEdge);

                testInvalid("NISTHypothesis.invalid (no handle exists): Each entity cluster in the hypothesis " +
                        "graph must have exactly one handle");
            }

            @Test
            // Two handle properties on entity cluster in hypothesis
            void invalidMultipleHandles() {

                final Resource newEntity = makeEntity(model, getEntityUri(), system);
                final Resource cluster = makeClusterWithPrototype(model, getClusterUri(), newEntity,
                        "handle2", system);
                cluster.addProperty(AidaAnnotationOntology.HANDLE, "handle3");
                markJustification(addType(newEntity, SeedlingOntology.Person), justification);
                makeValidHypothesis(entity, newEntity, event, eventEdge);

                testInvalid("NISTHypothesis.invalid (multiple handles exist): Each entity cluster in the " +
                        "hypothesis graph must have exactly one handle");
            }

            @Test
            // One handle on entity cluster in hypothesis
            void valid() {
                makeValidHypothesis(entity, event, eventEdge);
                testValid("NISTHypothesis.valid: Each entity cluster in the hypothesis graph must have " +
                        "exactly one handle");
            }
        }

        // Each hypothesis graph must have exactly one hypothesis importance value
        @Nested
        class HypothesisImportanceValue {

            @Test
            void invalid() {
                //invalid hypothesis, no importance value
                makeHypothesis(model, getUri("hypothesis-1"), ImmutableSet.of(entity, event, eventEdge), system);
                testInvalid("NISTHypothesis.invalid (hypothesis has no importance value): Each hypothesis " +
                        "graph must have exactly one hypothesis importance value");
            }
            @Test
            void valid() {
                makeValidHypothesis(entity, event, eventEdge);
                testValid("NISTHypothesis.valid: Each hypothesis graph must have exactly one" +
                        " hypothesis importance value");
            }
        }

        // Each event or relation (cluster) in the hypothesis must have exactly one importance value
        @Nested
        class HypothesisEventRelationClusterImportanceValue {

            private final String relationUri = getUri("relation-1");
            Resource relation;
            Resource eventCluster;
            Resource relationCluster;
            Resource relationEdge;

            @BeforeEach
            void setup() {
                eventCluster = makeClusterWithPrototype(model, getClusterUri(), event, system);

                relation = makeRelation(model, relationUri, system);
                markJustification(markType(model, getAssertionUri(), relation,
                        SeedlingOntology.GeneralAffiliation_APORA, system, 1.0), justification);

                relationEdge = markAsArgument(model, relation, SeedlingOntology.GeneralAffiliation_APORA_Affiliate,
                        entity, system, 1d, getAssertionUri());
                markImportance(relationEdge, 102.0);

                relationCluster = makeClusterWithPrototype(model, getClusterUri(), relation, system);

                makeValidHypothesis(entity, event, eventEdge, relation, relationEdge);
            }

            @Test
            void invalidEvent() {
                //invalid event cluster, no importance value
                markImportance(relationCluster, 99.0);
                testInvalid("NISTHypothesis.invalid (event cluster has no importance value): Each event or " +
                        "relation (cluster) in the hypothesis must have exactly one importance value");
            }

            @Test
            void invalidRelation() {
                //invalid relation cluster, no importance value
                markImportance(eventCluster, 88.0);
                testInvalid("NISTHypothesis.invalid (relation cluster has no importance value): Each event or " +
                        "relation (cluster) in the hypothesis must have exactly one importance value");
            }

            @Test
            void valid() {
                markImportance(eventCluster, 88.0);
                markImportance(relationCluster, 99.0);
                testValid("NISTHypothesis.valid: Each event or relation (cluster) in the hypothesis must " +
                        "have exactly one importance value");
            }
        }

        // Each edge KE in the hypothesis graph must have exactly one edge importance value
        @Nested
        class HypothesisEdgeImportanceValue {

            private final String relationUri = getUri("relation-1");
            Resource relation;

            @BeforeEach
            void setup() {
                relation = makeRelation(model, relationUri, system);
                markJustification(markType(model, getAssertionUri(), relation,
                        SeedlingOntology.GeneralAffiliation_APORA, system, 1.0), justification);

                //markImportance(makeClusterWithPrototype(model, getClusterUri(), event, system), 88.0);
                markImportance(makeClusterWithPrototype(model, getClusterUri(), relation, system), 88.0);
            }

            @Test
            void invalidEventEdge() {

                //invalid event argument, needs importance value
                Resource invalidEventEdge = markAsArgument(model, event, SeedlingOntology.Personnel_Elect_Elect,
                        entity, system, 0.785, "event-argument-1");

                makeValidHypothesis(entity, event, eventEdge, relation, invalidEventEdge);

                testInvalid("NISTHypothesis.invalid (event edge has no importance value): Each edge KE in the " +
                        "hypothesis graph must have exactly one edge importance value");
            }

            @Test
            void invalidRelationEdge() {

                //invalid relation argument, needs importance value
                Resource invalidRelationEdge = markAsArgument(model, relation, SeedlingOntology.GeneralAffiliation_APORA_Affiliation,
                        entity, system, 0.785, "relation-argument-1");

                makeValidHypothesis(entity, event, eventEdge, relation, invalidRelationEdge);

                testInvalid("NISTHypothesis.invalid (relation edge has no importance value): Each edge KE in the " +
                        "hypothesis graph must have exactly one edge importance value");
            }

            @Test
            void validEventEdge() {

                makeValidHypothesis(entity, event, eventEdge, relation);

                testValid("NISTHypothesis.valid (event edge has importance value): Each edge KE in the " +
                        "hypothesis graph must have exactly one edge importance value");
            }

            @Test
            void validRelationEdge() {

                // link entity to the relation
                Resource relationEdge = markAsArgument(model, relation, SeedlingOntology.GeneralAffiliation_APORA_Affiliation,
                        entity, system, 0.785, "relation-argument-1");
                markImportance(relationEdge, 120.0);

                makeValidHypothesis(entity, event, eventEdge, relation, relationEdge);

                testValid("NISTHypothesis.valid (relation edge has importance value): Each edge KE in the " +
                        "hypothesis graph must have exactly one edge importance value");
            }
        }

        @Nested
        class KEsInHypothesisMustBeDefined {

            @Test
            void invalid() {
                Resource fakeEntity = model.createResource(getEntityUri());
                makeValidHypothesis(fakeEntity, entity, event, eventEdge);
                testInvalid("NISTHypothesis.invalid (entity is not defined): All KEs referenced by hypothesis " +
                        "must be defined in model");
            }

            @Test
            void valid() {
                makeValidHypothesis(entity, event, eventEdge);
                testValid("NISTHypothesis.valid: All KEs referenced by hypothesis must be defined in model");
            }
        }

        @Nested
        class KEsInModelMustBeReferencedByHypothesis{
            Resource relation;
            Resource relationEdge;

            @BeforeEach
            void setup() {
                relation = makeRelation(model, getUri("relation-1"), system);
                markImportance(makeClusterWithPrototype(model, getClusterUri(), relation, "Relation", system), 103.0);
                markJustification(addType(relation, SeedlingOntology.GeneralAffiliation_APORA), justification);

                relationEdge = markAsArgument(model, relation, SeedlingOntology.GeneralAffiliation_APORA_Affiliate,
                        entity, system, 1d, getAssertionUri());
                markImportance(relationEdge, 102.0);
            }

            @Test
            void invalid() {
                makeValidHypothesis(entity, relation, relationEdge);
                testInvalid("NISTHypothesis.invalid (event and event edge is not referenced in hypothesis): " +
                        "All KEs in model must be referenced by hypothesis");
            }

            @Test
            void valid() {
                makeValidHypothesis(entity, relation, relationEdge, event, eventEdge);
                testValid("NISTHypothesis.valid: All KEs in model must be referenced by hypothesis");
            }
        }

        // Each hypothesis graph must have at least one event or relation with at least one edge.
        @Nested
        class HypothesisRequiredOneEventOrRelationWithOneEdge {

            @Test
            void invalid() {
                //remove everything in the model to ensure no edge KE's exist
                NistHypothesisExamplesAndValidationTest.this.setup();
                justification = makeTextJustification(model, "NYT_ENG_20181231",
                        42, 143, system, 0.973);
                addSourceDocumentToJustification(justification, "NYT_PARENT_ENG_20181231_03");


                entity = makeEntity(model, getEntityUri(), system);
                entityCluster = makeClusterWithPrototype(model, getClusterUri(), entity, "handle", system);
                markJustification(addType(entity, SeedlingOntology.Person), justification);
                makeValidHypothesis(entity);
                testInvalid("NISTHypothesis.invalid (no event or relation exists): Each hypothesis graph must " +
                        "have at least one event or relation with at least one edge.");
            }

            @Test
            void invalidRelationAndEventEdge() {
                final Resource relation = makeRelation(model, getUri("relation-1"), system);
                markImportance(makeClusterWithPrototype(model, getClusterUri(), relation, "Relation", system), 103.0);
                markJustification(addType(relation, SeedlingOntology.GeneralAffiliation_APORA), justification);

                //create invalid relation edge with event argument type
                final Resource invalidRelationEdge = markAsArgument(model, relation, SeedlingOntology.Conflict_Attack_Attacker,
                        entity, system, 1d, getAssertionUri());
                markImportance(invalidRelationEdge, 102.0);

                makeValidHypothesis(entity, event, eventEdge, relation, invalidRelationEdge);
                testInvalid("NISTHypothesis.invalid (event has invalid relation edge): Each hypothesis graph " +
                        "must have at least one event or relation with at least one edge.");
            }

            // TODO This test case needs to be updated and @Test needs to be added back in  once we decide on the
            // TODO new design of this class upon the completion of AIDA-698.
            void validRelationAndRelationEdge() {
                final Resource relation = makeRelation(model, getUri("relation-1"), system);
                markImportance(makeClusterWithPrototype(model, getClusterUri(), relation, "Relation", system), 103.0);
                markJustification(addType(relation, SeedlingOntology.GeneralAffiliation_APORA), justification);

                final Resource relationEdge = markAsArgument(model, relation, SeedlingOntology.GeneralAffiliation_APORA_Affiliate,
                        entity, system, 1d, getAssertionUri());
                markImportance(relationEdge, 102.0);

                makeValidHypothesis(entity, event, eventEdge, relation, relationEdge);
                testValid("NISTHypothesis.valid (relation has relation edge): Each hypothesis graph must have " +
                        "at least one event or relation with at least one edge.");
            }

            @Test
            void validEventAndEventEdge() {

                makeValidHypothesis(entity, event, eventEdge);
                testValid("NISTHypothesis.valid (event has event edge): Each hypothesis graph must have " +
                        "at least one event or relation with at least one edge.");
            }

            @Test
            void validEventRelationAndEventRelationEdge() {

                final Resource relation = makeRelation(model, getUri("relation-1"), system);
                markImportance(makeClusterWithPrototype(model, getClusterUri(), relation, "Relation", system), 103.0);
                markJustification(addType(relation, SeedlingOntology.GeneralAffiliation_APORA), justification);

                final Resource relationEdge = markAsArgument(model, relation, SeedlingOntology.GeneralAffiliation_APORA_Affiliate,
                        entity, system, 1d, getAssertionUri());
                markImportance(relationEdge, 102.0);

                makeValidHypothesis(entity, event, eventEdge, relation, relationEdge);
                testValid("NISTHypothesis.valid (event has event edge and relation has relation edge): Each " +
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
                relation = makeRelation(model, getUri("relation-1"), system);

                relationCluster = makeClusterWithPrototype(model, getClusterUri(), relation,
                        "Relation", system);

                markImportance(relationCluster, 103.0);
                markJustification(addType(relation, SeedlingOntology.GeneralAffiliation_APORA), justification);

                relationEdge = markAsArgument(model, relation, SeedlingOntology.GeneralAffiliation_APORA_Affiliate,
                        entity, system, 1d, getAssertionUri());
                markImportance(relationEdge, 102.0);
            }

            @Test
            void invalid() {

                // create event cluster member to add to relation cluster
                final Resource eventMember = makeEvent(model, getUri("event-member-1"), system);
                markJustification(addType(eventMember, SeedlingOntology.Conflict_Attack), justification);

                //add invalid event cluster member to relation cluster
                markAsPossibleClusterMember(model, eventMember, relationCluster, 1d, system);

                makeValidHypothesis(entity, event, eventEdge, eventMember, relation, relationEdge);
                testInvalid("NISTHypothesis.invalid (event exists in relation cluster): Clusters must be " +
                        "homogeneous by base class (Entity, Event, or Relation).");
            }

            @Test
            void valid() {

                // create relation cluster member to add to relation cluster
                final Resource relationMember = makeRelation(model, getUri("relation-member-1"), system);
                markJustification(addType(relationMember, SeedlingOntology.GeneralAffiliation_APORA), justification);

                //add valid relation cluster member to relation cluster
                markAsPossibleClusterMember(model, relationMember, relationCluster, 1d, system);

                makeValidHypothesis(entity, event, eventEdge, relation, relationEdge, relationMember);
                testValid("NISTHypothesis.valid: Clusters must be homogeneous by base class " +
                        "(Entity, Event, or Relation)");
            }
        }
    }

    private Model addNamespacesToModel(Model model) {
        // adding namespace prefixes makes the Turtle output more readable
        model.setNsPrefix("ldcOnt", NAMESPACE);
        model.setNsPrefix("ldc", LDC_NS);
        return model;
    }
}
