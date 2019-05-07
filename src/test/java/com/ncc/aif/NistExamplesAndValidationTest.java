package com.ncc.aif;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.nio.charset.StandardCharsets;

import static com.ncc.aif.AIFUtils.*;

@TestInstance(Lifecycle.PER_CLASS)
public class NistExamplesAndValidationTest {
    // Set this flag to true if attempting to get examples
    private static final boolean FORCE_DUMP = false;

    private static final String LDC_NS = "https://tac.nist.gov/tracks/SM-KBP/2018/LdcAnnotations#";
    private static final String ONTOLOGY_NS = "https://tac.nist.gov/tracks/SM-KBP/2018/ontologies/LDCOntology#";
    private static final CharSource LDC_ONTOLOGY = Resources.asCharSource(
            Resources.getResource("com/ncc/aif/ontologies/LDCOntology"),
            StandardCharsets.UTF_8);
    private static NistTestUtils utils;

    @BeforeAll
    static void declutterLogging() {
        // prevent too much logging from obscuring the Turtle examples which will be printed
        ((Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        utils = new NistTestUtils(LDC_NS, ValidateAIF.createForLDCOntology(ValidateAIF.Restriction.NIST), FORCE_DUMP);
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
    class NISTExamples {
        Resource entity;
        Resource event;
        Resource relation;
        Resource entityCluster;
        Resource eventCluster;
        Resource relationCluster;

        @BeforeEach
        void setup() {
            ImmutablePair<Resource, Resource> aPair = utils.makeValidNistEntity(
                    LDCOntology.PER);
            entity = aPair.getKey();
            entityCluster = aPair.getValue();
            aPair = utils.makeValidNistEvent(
                    LDCOntology.Conflict_Attack);
            event = aPair.getKey();
            eventCluster = aPair.getValue();
            aPair = utils.makeValidNistRelation(
                    LDCOntology.GeneralAffiliation_ArtifactPoliticalOrganizationReligiousAffiliation);
            relation = aPair.getKey();
            relationCluster = aPair.getValue();
        }

        // Each edge justification must be represented uniformly in AIF by
        // aida:CompoundJustification, even if only one span is provided
        // Edges are assumed to be relation and event arguments
        //
        // CompoundJustification must be used only for justifications of argument assertions,
        // and not for justifications for entities, events, or relation KEs
        @Nested
        class RestrictJustification {
            @Test
            void invalid() {

                // test relation edge argument must have a compound justification
                final Resource relationEdge = markAsArgument(model, relation,
                        LDCOntology.GeneralAffiliation_ArtifactPoliticalOrganizationReligiousAffiliation_EntityOrFiller,
                        entity, system, 1.0, utils.getAssertionUri());
                final Resource justification = markTextJustification(model, relationEdge,
                        "source1", 0, 4, system, 1.0);
                addSourceDocumentToJustification(justification, "source1sourceDocument");

                // test event edge argument must have a compound justification
                final Resource eventEdge = markAsArgument(model, event,
                        LDCOntology.Conflict_Attack_Target,
                        entity, system, 1.0, utils.getAssertionUri());
                markJustification(eventEdge, justification);

                final Resource justification1 = makeTextJustification(model, "source1", 0, 4, system, 1.0);
                addSourceDocumentToJustification(justification1, "source1sourceDocument");
                final Resource compound1 = markCompoundJustification(model,
                        ImmutableSet.of(entity, relation, event),
                        ImmutableSet.of(justification1),
                        system,
                        1.0);

                // test that compound justification can only be used for argument assertions
                markJustification(entity, compound1);
                markJustification(relation, compound1);
                markJustification(event, compound1);

                utils.testInvalid("NIST.invalid: CompoundJustification must be used only for justifications of argument assertions");
            }

            @Test
            void invalidCompoundJustificationWithNoJustification() {

                Resource compoundJustification = model.createResource();
                compoundJustification.addProperty(RDF.type, AidaAnnotationOntology.COMPOUND_JUSTIFICATION_CLASS);
                markSystem(compoundJustification, system);

                markConfidence(model, compoundJustification, 1.0, system);
                utils.testInvalid("NIST.invalid: (No justification in CompoundJustification) Exactly 1 or 2 contained" +
                        " justifications in a CompoundJustification required for an edge");
            }

            @Test
            void invalidCompoundJustificationWithThreeJustifications() {

                // test relation argument
                final Resource relationEdge = markAsArgument(model, relation,
                        LDCOntology.GeneralAffiliation_ArtifactPoliticalOrganizationReligiousAffiliation_EntityOrFiller,
                        entity, system, 1.0);
                final Resource justification1 = makeTextJustification(model, "source1", 0,
                        4, system, 1.0);
                addSourceDocumentToJustification(justification1, "source1sourceDocument");
                final Resource justification2 = makeTextJustification(model, "source2", 0,
                        4, system, 1.0);
                addSourceDocumentToJustification(justification2, "source2sourceDocument");
                final Resource justification3 = makeTextJustification(model, "source3", 0,
                        4, system, 1.0);
                addSourceDocumentToJustification(justification3, "source3sourceDocument");

                markCompoundJustification(model,
                        ImmutableSet.of(relationEdge),
                        ImmutableSet.of(justification1, justification2, justification3),
                        system,
                        1.0);

                utils.testInvalid("NIST.invalid: (More than two justifications in CompoundJustification) " +
                        "Exactly 1 or 2 contained justifications in a CompoundJustification required for an edge");
            }

            @Test
            void valid() {

                // test relation argument
                final Resource relationEdge = markAsArgument(model, relation,
                        LDCOntology.GeneralAffiliation_ArtifactPoliticalOrganizationReligiousAffiliation_EntityOrFiller,
                        entity, system, 1.0);
                final Resource justification1 = makeTextJustification(model, "source1", 0, 4, system, 1.0);
                addSourceDocumentToJustification(justification1, "source1sourceDocument");

                final Resource compound = markCompoundJustification(model,
                        ImmutableSet.of(relationEdge),
                        ImmutableSet.of(justification1),
                        system,
                        1.0);

                markJustification(relationEdge, compound);

                // test event argument
                final Resource eventEdge = markAsArgument(model, event,
                        LDCOntology.Conflict_Attack_Target,
                        entity, system, 1.0);

                markJustification(eventEdge, compound);

                utils.testValid("NIST.valid: CompoundJustification must be used only for justifications of argument assertions");
            }
        }

        // Each edge justification is limited to either one or two spans.
        @Nested
        class EdgeJustificationLimit {
            @Test
            void invalid() {
                // test relation
                final Resource relationEdge = markAsArgument(model, relation,
                        LDCOntology.GeneralAffiliation_ArtifactPoliticalOrganizationReligiousAffiliation_EntityOrFiller,
                        entity, system, 1.0, utils.getAssertionUri());
                final Resource justification1 = makeTextJustification(model, "source1", 0, 4, system, 1.0);
                addSourceDocumentToJustification(justification1, "source1sourceDocument");
                final Resource justification2 = makeTextJustification(model, "source1", 10, 14, system, 1.0);
                addSourceDocumentToJustification(justification2, "source1sourceDocument");
                final Resource justification3 = makeTextJustification(model, "source1", 20, 24, system, 1.0);
                addSourceDocumentToJustification(justification3, "source1sourceDocument");
                final Resource compound = markCompoundJustification(model,
                        ImmutableSet.of(relationEdge),
                        ImmutableSet.of(justification1, justification2, justification3),
                        system,
                        1.0);

                // test event
                final Resource eventEdge = markAsArgument(model, event,
                        LDCOntology.Conflict_Attack_Target,
                        entity, system, 1.0, utils.getAssertionUri());
                markJustification(eventEdge, compound);

                utils.testInvalid("NIST.invalid: edge justification contains one or two mentions (three is too many)");
            }


            @Test
            void invalidZeroSpans() {
                // test relation
                final Resource relationEdge = markAsArgument(model, relation,
                        LDCOntology.GeneralAffiliation_ArtifactPoliticalOrganizationReligiousAffiliation_EntityOrFiller,
                        entity, system, 1.0);
                final Resource compound = markCompoundJustification(model,
                        ImmutableSet.of(relationEdge),
                        ImmutableSet.of(), // no justification
                        system,
                        1.0);

                // test event
                final Resource eventEdge = markAsArgument(model, event,
                        LDCOntology.Conflict_Attack_Target,
                        entity, system, 1.0);
                markJustification(eventEdge, compound);

                utils.testInvalid("NIST.invalid: edge justification contains one or two mentions (zero is not enough)");
            }

            @Test
            void valid() {
                // test relation
                final Resource relationEdge = markAsArgument(model, relation,
                        LDCOntology.GeneralAffiliation_ArtifactPoliticalOrganizationReligiousAffiliation_EntityOrFiller,
                        entity, system, 1.0);
                final Resource justification1 = makeTextJustification(model, "source1", 0, 4, system, 1.0);
                addSourceDocumentToJustification(justification1, "source1sourceDocument");
                final Resource justification2 = makeTextJustification(model, "source1", 10, 14, system, 1.0);
                addSourceDocumentToJustification(justification2, "source1sourceDocument");
                final Resource compound = markCompoundJustification(model,
                        ImmutableSet.of(relationEdge),
                        ImmutableSet.of(justification1, justification2),
                        system,
                        1.0);

                // test event
                final Resource eventEdge = markAsArgument(model, event,
                        LDCOntology.Conflict_Attack_Target,
                        entity, system, 1.0);
                markJustification(eventEdge, compound);

                utils.testValid("NIST.valid: edge justification contains two mentions (i.e., one or two are valid)");
            }

            @Test
            void validOneSpan() {
                // test relation
                final Resource relationEdge = markAsArgument(model, relation,
                        LDCOntology.GeneralAffiliation_ArtifactPoliticalOrganizationReligiousAffiliation_EntityOrFiller,
                        entity, system, 1.0);
                final Resource justification1 = makeTextJustification(model, "source1", 0, 4, system, 1.0);
                addSourceDocumentToJustification(justification1, "source1sourceDocument");
                final Resource compound = markCompoundJustification(model,
                        ImmutableSet.of(relationEdge),
                        ImmutableSet.of(justification1),
                        system,
                        1.0);

                // test event
                final Resource eventEdge = markAsArgument(model, event,
                        LDCOntology.Conflict_Attack_Target,
                        entity, system, 1.0);
                markJustification(eventEdge, compound);

                utils.testValid("NIST.valid: edge justification contains one mention (i.e., one or two are valid)");
            }
        }

        // Video must use aida:KeyFrameVideoJustification. Remove ShotVideoJustification
        @Nested
        class PreventShotVideo {
            @Test
            void invalid() {
                final Resource markShotVideoJustification = markShotVideoJustification(model, entity, "source1",
                        "shotId", system, 1.0);
                addSourceDocumentToJustification(markShotVideoJustification, "source1SourceDocument");
                utils.testInvalid("NIST.invalid: No shot video");
            }

            @Test
            void valid() {
                final Resource markKeyFrameVideoJustification = markKeyFrameVideoJustification(model, entity,
                        "source1", "keyframe",
                        new BoundingBox(new Point(0, 0), new Point(100, 100)), system, 1.0);
                addSourceDocumentToJustification(markKeyFrameVideoJustification, "source1SourceDocument");
                utils.testValid("NIST.valid: No shot video");
            }
        }

        // Members of clusters are entity objects, relation objects, and event objects (not clusters)
        @Nested
        class FlatClusters {
            @Test
            void invalid() {
                markAsPossibleClusterMember(model, eventCluster, entityCluster, .5, system);
                utils.testInvalid("NIST.invalid: Flat clusters");
            }

            @Test
            void valid() {
                final Resource newEntity = makeEntity(model, utils.getEntityUri(), system);
                markJustification(utils.addType(newEntity,
                        LDCOntology.PER),
                        utils.makeValidJustification());
                markAsPossibleClusterMember(model, newEntity, entityCluster, .75, system);
                utils.testValid("NIST.valid: Flat clusters");
            }
        }

        // Entity, Relation, and Event object is required to be part of at least one cluster.
        // This is true even if there is nothing else in the cluster
        @Nested
        class EverythingClustered {
            @Test
            void invalid() {
                // Test entity, relation, and event. Correct other than being clustered
                final Resource newEntity = makeEntity(model, utils.getEntityUri(), system);
                markJustification(utils.addType(newEntity,
                        LDCOntology.WEA),
                        utils.makeValidJustification());
                final Resource newRelation = makeRelation(model, utils.getRelationUri(), system);
                markJustification(utils.addType(newRelation,
                        LDCOntology.GeneralAffiliation_ArtifactPoliticalOrganizationReligiousAffiliation),
                        utils.makeValidJustification());
                final Resource newEvent = makeEvent(model, utils.getEventUri(), system);
                markJustification(utils.addType(newEvent,
                        LDCOntology.Life_Die),
                        utils.makeValidJustification());

                utils.testInvalid("NIST.invalid: Everything has cluster");
            }

            @Test
            void valid() {
                // setup() already creates an entity, event, and relation
                // object, and adds them to a cluster.

                utils.testValid("NIST.valid: Everything has cluster");
            }
        }

        // Each confidence value must be between 0 and 1
        @Nested
        class ConfidenceValueRange {
            @Test
            void invalid() {
                final Resource newEntity = makeEntity(model, utils.getEntityUri(), system);
                markJustification(utils.addType(newEntity,
                        LDCOntology.PER),
                        utils.makeValidJustification());
                markAsPossibleClusterMember(model, newEntity, entityCluster, 1.2, system);
                utils.testInvalid("NIST.invalid: confidence must be between 0 and 1");
            }

            @Test
            void valid() {
                final Resource newEntity = makeEntity(model, utils.getEntityUri(), system);
                markJustification(utils.addType(newEntity,
                        LDCOntology.PER),
                        utils.makeValidJustification());
                markAsPossibleClusterMember(model, newEntity, entityCluster, .7, system);
                utils.testValid("NIST.valid: confidence must be between 0 and 1");
            }
        }

        // Entity, Relation, and Event clusters must have IRI
        @Nested
        class ClusterHasIRI {
            @Test
            void invalid() {
                // Test entity, relation, and event. Correct other than being clustered
                makeClusterWithPrototype(model, null, entity, system);
                makeClusterWithPrototype(model, null, relation, system);
                makeClusterWithPrototype(model, null, event, system);

                utils.testInvalid("NIST.invalid: Cluster has IRI");
            }

            @Test
            void valid() {
                // setup() already creates an entity, relation, and event in clusters with an IRI.
                utils.testValid("NIST.valid: Cluster has IRI");
            }
        }

        // Each entity/relation/event type statement must have at least one justification
        @Nested
        class JustifyTypeAssertions {
            @Test
            void invalid() {
                // Create a base AIF entity, but do not mark its type assertion with a justification.
                final Resource newEntity = utils.makeValidAIFEntity(
                        LDCOntology.PER);
                makeClusterWithPrototype(model, utils.getClusterUri(), newEntity, system);

                // Create a base AIF event, but do not mark its type assertion with a justification.
                final Resource newEvent = utils.makeValidAIFEvent(
                        LDCOntology.Conflict_Attack);
                makeClusterWithPrototype(model, utils.getClusterUri(), newEvent, system);

                // Create a base AIF relation, but do not mark its type assertion with a justification.
                final Resource newRelation = utils.makeValidAIFRelation(
                        LDCOntology.GeneralAffiliation_ArtifactPoliticalOrganizationReligiousAffiliation);
                makeClusterWithPrototype(model, utils.getClusterUri(), newRelation, system);

                utils.testInvalid("NIST.invalid: type assertions must be justified");
            }

            @Test
            void valid() {
                // setup() already makes type assertions on the entity, event,
                // and relation objects, justified by a text justification.

                utils.testValid("NIST.valid: type assertions must be justified");
            }
        }

        // Each entity/filler name string is limited to 256 UTF-8 characters
        @Nested
        class NameMaxLength {
            @Test
            void invalidName() {
                // assign alternate names to the entity that are longer that 256 characters.
                markName(entity, "This is a test string that will be used to validate " +
                        "that entity names and fillers are limited to 256 characters. This string should " +
                        "fail because this string is exactly 257 characters long. This is filler text to " +
                        "get to the two hundred and fifty-seven limit.");

                utils.testInvalid("NIST.invalid (has name): Each entity name string is limited to 256 UTF-8 characters");
            }

            @Test
            void invalidTextValue() {
                final Resource textValueEntity = utils.makeValidNistEntity(
                        LDCOntology.MON).getKey();
                markTextValue(textValueEntity, "This is a test string that will be used to validate " +
                        "that entity names and fillers are limited to 256 characters. This string should " +
                        "fail because this string is exactly 257 characters long. This is filler text to " +
                        "get to the two hundred and fifty-seven limit.");

                utils.testInvalid("NIST.invalid (text value): Each entity text value string is limited to 256 UTF-8 characters");
            }

            @Disabled("LDCOntology does not have any Entities with a numeric value")
            @Test
            void invalidNumericValueAsString() {
                final Resource numericValueEntity = utils.makeValidNistEntity(LDCOntology.VAL).getKey();
                markNumericValueAsString(numericValueEntity, "3.866257319028419151956807870102338944632653034263131666724882672874792347265146689923498812818121807146499569966401451211686727219627969935361183863143994146880217969397076000433349740006299102731565965237056997838014700127614676980451633032526526557734348");

                utils.testInvalid("NIST.invalid (numeric string value): Each entity numeric value string is limited to 256 UTF-8 characters");
            }

            @Test
            void validName() {
                // assign alternate names to the entity that are equal and less than 256 characters.
                markName(entity, "This is a test string that will be used to validate that entity " +
                        "names and fillers are limited to 256 characters. This string should pass because " +
                        "this string is exactly 256 characters long. Characters to get to the two hundred " +
                        "and fifty-six character limit.");

                markName(entity, "Small string size");

                utils.testValid("NIST.valid (has name): Each entity name string is limited to 256 UTF-8 characters");
            }

            @Test
            void validTextvalue() {
                // assign text value to the entity that are equal and less than 256 characters.
                final Resource textValueEntity = utils.makeValidNistEntity(
                        LDCOntology.MON).getKey();
                markTextValue(textValueEntity, "This is a test string that will be used to validate that entity " +
                        "names and fillers are limited to 256 characters. This string should pass because " +
                        "this string is exactly 256 characters long. Characters to get to the two hundred " +
                        "and fifty-six character limit.");

                markTextValue(textValueEntity, "Small string size");

                utils.testValid("NIST.valid (text value): Each entity text value string is limited to 256 UTF-8 characters");
            }

            @Disabled("LDCOntology does not have any Entities with a numeric value")
            @Test
            void validNumericValueAsString() {
                final Resource numericValueEntity = utils.makeValidNistEntity(LDCOntology.VAL).getKey();
                markNumericValueAsString(numericValueEntity, "3.86625731902841915195680787010233894463265303426313166672488267287479234726514668992349881281812180714649956996640145121168672721962796993536118386314399414688021796939707600043334974000629910273156596523705699783801470012761467698045163303252652655773434");

                markNumericValueAsString(numericValueEntity, "1");
                utils.testValid("NIST.valid (numeric string value): Each entity numeric value string is limited to 256 UTF-8 characters");
            }
        }

        // Justifications require a source document and a source
        @Nested
        class JustificationSourceAndSourceDocument {
            Resource newJustification;

            @BeforeEach
            void setup() {
                // create justification from scratch
                newJustification = model.createResource();
                newJustification.addProperty(RDF.type, AidaAnnotationOntology.TEXT_JUSTIFICATION_CLASS);
                if (system != null) {
                    markSystem(newJustification, system);
                }

                markConfidence(model, newJustification, 0.973, system);
                newJustification.addProperty(AidaAnnotationOntology.START_OFFSET,
                        model.createTypedLiteral(41));
                newJustification.addProperty(AidaAnnotationOntology.END_OFFSET_INCLUSIVE,
                        model.createTypedLiteral(143));

                final Resource newEvent = makeEvent(model, utils.getEventUri(), system);
                markJustification(utils.addType(newEvent,
                        LDCOntology.Conflict_Attack),
                        newJustification);
                makeClusterWithPrototype(model, utils.getClusterUri(), newEvent, system);
            }

            @Test
            void invalidNoSource() {
                // include the source document but not the source
                addSourceDocumentToJustification(newJustification, "HC00002ZO");
                utils.testInvalid("NIST.invalid (missing justification source): justifications require a source document and source");

            }

            @Test
            void invalidNoSourceDocument() {
                // include the source but not the source document
                newJustification.addProperty(AidaAnnotationOntology.SOURCE, model.createTypedLiteral("XP043002ZO"));
                utils.testInvalid("NIST.invalid (missing justification source document): justifications require a source document and source");
            }

            @Test
            void valid() {
                // include the source and source document
                newJustification.addProperty(AidaAnnotationOntology.SOURCE, model.createTypedLiteral("XP043002ZO"));
                addSourceDocumentToJustification(newJustification, "HC00002ZO");
                utils.testValid("NIST.valid: justifications require a source document and a source");
            }
        }

        @Nested
        class InformativeJustification {

            @Test
            void invalidInformativeJustificationDuplicateParentDoc() {
                final String sourceDocument = "20181231";
                markInformativeJustification(entity, utils.makeValidJustification(sourceDocument));
                markInformativeJustification(entity, utils.makeValidJustification(sourceDocument));

                utils.testInvalid("NIST.invalid: (informative justifications have same parent document) Each Cluster, " +
                        "Entity, Event, or Relation can specify up to one informative mention per document as long " +
                        "as each informative mention points to a different sourceDocument");
            }

            @Test
            void validEntityInformativeJustification() {
                markInformativeJustification(entity, utils.makeValidJustification());
                utils.testValid("NIST.valid: (entity) Each Cluster, Entity, Event, or Relation can specify up to one " +
                        "informative mention per document as each informative mention points to a " +
                        "different sourceDocument");
            }

            @Test
            void validEventInformativeJustification() {
                markInformativeJustification(event, utils.makeValidJustification());
                utils.testValid("NIST.valid: (event) Each Cluster, Entity, Event, or Relation can specify up to one " +
                        "informative mention per document as each informative mention points to a different " +
                        "sourceDocument");

            }

            @Test
            void validRelationInformativeJustification() {
                markInformativeJustification(relation, utils.makeValidJustification());
                utils.testValid("NIST.valid: (relation) Each Cluster, Entity, Event, or Relation can specify up to one " +
                        "informative mention per document as each informative mention points to a different " +
                        "sourceDocument");
            }

            @Test
            void validClusterInformativeJustification() {
                markInformativeJustification(entityCluster, utils.makeValidJustification());
                utils.testValid("NIST.valid: (cluster) Each Cluster, Entity, Event, or Relation can specify up to one " +
                        "informative mention per document as each informative mention points to a different " +
                        "sourceDocument");
            }

            @Test
            void validRelationWithMultipleInformativeJustifications() {
                //add three informative justifications to same relation KE
                markInformativeJustification(relation, utils.makeValidJustification("sourceDocument1"));
                markInformativeJustification(relation, utils.makeValidJustification("sourceDocument2"));
                markInformativeJustification(relation, utils.makeValidJustification("sourceDocument3"));

                utils.testValid("NIST.valid: (multiple informative justifications on relation) Each Cluster," +
                        "Entity, Event, or Relation can specify up to one informative mention per document as long " +
                        "as each informative mention points to a different sourceDocument");

            }

            @Test
            void validEntityClusterSeparateInformativeJustificationsWithSameParentDoc() {
                // Add more than one informative justification to entity KE
                markInformativeJustification(entity, utils.makeValidJustification("20181231"));
                markInformativeJustification(entity, utils.makeValidJustification("3822029"));

                // Add more than one informative justification to entity cluster KE.
                // One of the informative justifications contains same parent doc as entity KE
                markInformativeJustification(entityCluster, utils.makeValidJustification("20181231"));
                markInformativeJustification(entityCluster, utils.makeValidJustification("3298329"));

                utils.testValid("NIST.valid: (Two KE's with informative justifications with same parent doc) Each " +
                        "Cluster, Entity, Event, or Relation can specify up to one informative mention per document " +
                        "as long as each informative mention points to a different sourceDocument");

            }
        }

        @Nested
        class LinkAssertion {
            Resource linkAssertion;

            void link(Resource toLink) {
                toLink.addProperty(AidaAnnotationOntology.LINK, linkAssertion);
            }

            void target(String externalKbId) {
                linkAssertion.addProperty(AidaAnnotationOntology.LINK_TARGET, model.createTypedLiteral(externalKbId));
            }

            @BeforeEach
            void setup() {
                model.removeAll();
                system = makeSystemWithURI(model, utils.getTestSystemUri());

                linkAssertion = model.createResource();
                linkAssertion.addProperty(RDF.type, AidaAnnotationOntology.LINK_ASSERTION_CLASS);
                markSystem(linkAssertion, system);

                entity = makeEntity(model, utils.getEntityUri(), system);
                markJustification(utils.addType(entity,
                        LDCOntology.PER),
                        utils.makeValidJustification());

                entityCluster = makeClusterWithPrototype(model, utils.getClusterUri(), entity, system);
            }

            @Test
            void invalidLinkToNonAssertion() {
                linkAssertion.listProperties().toList().forEach(model::remove);
                entity.addProperty(AidaAnnotationOntology.LINK, utils.makeValidJustification());
                utils.testInvalid("LinkAssertion.invalid: Link to non-LinkAssertion");
            }

            @Test
            void invalidNoTarget() {
                link(entity);
                markConfidence(model, linkAssertion, 1.0, system);
                utils.testInvalid("LinkAssertion.invalid: No link target");
            }

            @Test
            void invalidTooManyTargets() {
                link(entity);
                markConfidence(model, linkAssertion, 1.0, system);
                target("SomeExternalKBId-1");
                target("SomeExternalKBId-2");
                utils.testInvalid("LinkAssertion.invalid: Too many link targets");
            }

            @Test
            void invalidNoConfidence() {
                link(entity);
                target("SomeExternalKBId-1");
                utils.testInvalid("LinkAssertion.invalid: No confidence");
            }

            @Test
            void invalidTooManyConfidences() {
                link(entity);
                markConfidence(model, linkAssertion, 1.0, system);
                markConfidence(model, linkAssertion, .5, system);
                target("SomeExternalKBId-1");
                utils.testInvalid("LinkAssertion.invalid: Too many confidences");
            }

            @Test
            void valid() {
                link(entity);
                markConfidence(model, linkAssertion, 1.0, system);
                target("SomeExternalKBId-1");
                utils.testValid("LinkAssertion.valid");
            }
        }
    }

    private void addNamespacesToModel(Model model) {
        // adding namespace prefixes makes the Turtle output more readable
        model.setNsPrefix("ldcOnt", ONTOLOGY_NS);
        model.setNsPrefix("ldc", LDC_NS);
    }
}
