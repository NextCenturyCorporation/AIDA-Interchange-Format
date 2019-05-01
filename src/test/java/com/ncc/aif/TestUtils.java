package com.ncc.aif;

import javafx.util.Pair;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;

import java.util.*;

import static com.ncc.aif.AIFUtils.*;
import static org.junit.jupiter.api.Assertions.fail;

class TestUtils {

    private final boolean forceDump;
    private final ValidateAIF validator;
    private final String annotationNamespace;

    private int assertionCount;
    private int entityCount;
    private int eventCount;
    private int relationCount;
    private int hypothesisCount;
    private int clusterCount;
    private int documentCount;
    protected Model model;
    protected Resource system;
    private Resource typeAssertionJustification = null;

    TestUtils(String annotationNamespace, ValidateAIF validator, boolean forceDump) {
        this.annotationNamespace = annotationNamespace;
        this.validator = validator;
        this.forceDump = forceDump;
    }

    /**
     * Call before each test, returns a new, clean model.
     * @return
     */
    Model startNewTest() {
        if (model != null) {
            typeAssertionJustification = null;
            model.close();
        }
        model = ModelFactory.createDefaultModel();
        // adding namespace prefixes makes the Turtle output more readable
        addStandardNamespaces(model);
        // every AIF needs an object for the system responsible for creating it
        system = makeSystemWithURI(model, getTestSystemUri());
        assertionCount = entityCount = eventCount = relationCount = hypothesisCount = clusterCount = documentCount = 1;

        return model;
    }

    String getTestSystemUri() {
        return getUri("testSystem");
    }

    String getUri(String localName) {
        return annotationNamespace + localName;
    }

    String getAssertionUri() {
        return getUri("assertion-" + assertionCount++);
    }

    String getEntityUri() {
        return getUri("entity-" + entityCount++);
    }

    String getEventUri() {
        return getUri("event-" + eventCount++);
    }

    String getRelationUri() {
        return getUri("relation-" + relationCount++);
    }

    String getHypothesisUri() {
        return getUri("hypothesis-" + hypothesisCount++);
    }

    String getClusterUri() {
        return getUri("cluster-" + clusterCount++);
    }

    Resource getSystem() {
        return system;
    }

    String getDocumentName() {
        return "document-" + documentCount++;
    }

    Resource getTypeAssertionJustification() {
        if (typeAssertionJustification == null) {
            typeAssertionJustification = makeTextJustification(model, getDocumentName(),
                    42, 143, system, 0.973);
        }
        return typeAssertionJustification;
    }

    Resource addType(Resource resource, Resource type) {
        return addType(resource, type, 1.0);
    }

    Resource addType(Resource resource, Resource type, double confidence) {
        return markType(model, getAssertionUri(), resource, type, system, confidence);
    }

    Resource makeEntity(Resource type) {
        return makeEntity(type, null);
    }

    Resource makeEntity(Resource type, String uri) {
        final Resource entity = AIFUtils.makeEntity(model, uri == null ? getEntityUri() : uri, system);
        addType(entity, type);
        return entity;
    }

    Resource makeEvent(Resource type) {
        return makeEvent(type, null);
    }

    Resource makeEvent(Resource type, String uri) {
        final Resource event = AIFUtils.makeEvent(model, uri == null ? getEventUri() : uri, system);
        addType(event, type);
        return event;
    }

    Resource makeRelation(Resource type) {
        return makeRelation(type, null);
    }

    Resource makeRelation(Resource type, String uri) {
        final Resource relation = AIFUtils.makeRelation(model, uri == null ? getRelationUri() : uri, system);
        addType(relation, type);
        return relation;
    }

    Resource makeHypothesis(Resource... resources) {
        return makeHypothesis(null, resources);
    }

    Resource makeHypothesis(String uri, Resource... resources) {
        Set<Resource> set = new HashSet<>();
        Collections.addAll(set, resources);
        return AIFUtils.makeHypothesis(model, uri == null ? getHypothesisUri() : uri, set, system);
    }

    void testInvalid(String name) {
        assertAndDump(name, false);
    }

    void testValid(String name) {
        assertAndDump(name, true);
    }

    /**
     * This method will validate the model using the provided validator and will dump the model as TURTLE if
     * either the validation result is unexpected or if the model is valid and FORCE_DUMP is true. Thus, FORCE_DUMP
     * can be used to write all the valid examples to console.
     *
     * @param testName  {@link String} containing the name of the test
     * @param expected  true if validation is expected to pass, false o/w
     */
    private void assertAndDump(String testName, boolean expected) {
        final Resource report = validator.validateKBAndReturnReport(model);
        final boolean valid = ValidateAIF.isValidReport(report);

        // print model if result unexpected or if forcing (for examples)
        // Swap comments following 2 lines if FORCE_DUMP should ALWAYS dump output
        // if (valid != expected || forceDump) {
        if (valid != expected || (forceDump && expected)) {
            System.out.println("\n----------------------------------------------\n" + testName + "\n\nAIF Model:");
            RDFDataMgr.write(System.out, model, RDFFormat.TURTLE_PRETTY);
        }

        // fail if result is unexpected
        if (valid != expected) {
            // only print output if there is any
            if (!valid) {
                System.out.println("\nFailure:");
                RDFDataMgr.write(System.out, report.getModel(), RDFFormat.TURTLE_PRETTY);
            }
            fail("Validation was expected to " + (expected ? "pass" : "fail") + " but did not");
        }
    }
}


class NistTestUtils extends TestUtils {

    NistTestUtils(String annotationNamespace, ValidateAIF validator, boolean forceDump) {
        super(annotationNamespace, validator, forceDump);
    }

    Model startNewTest() {
        Model model = super.startNewTest();
        // NIST tests always need type assertion justifications
        addSourceDocumentToJustification(super.getTypeAssertionJustification(), getDocumentName());
        return model;
    }

    /**
     * Returns a valid NIST-restricted entity and its cluster.
     * @param type entity type
     * @return
     */
    Pair<Resource, Resource> makeNistEntity(Resource type) {
        return makeNistEntity(type, null);
    }

    /**
     * For entity clusters in hypotheses
     * @param type
     * @param clusterHandle
     * @return
     */
    Pair<Resource, Resource> makeNistEntity(Resource type, String clusterHandle) {
        Resource entity = AIFUtils.makeEntity(model, getEntityUri(), system);
        markJustification(addType(entity, type), getTypeAssertionJustification());
        Resource entityCluster = clusterHandle == null ?
                makeClusterWithPrototype(model, getClusterUri(), entity, system) :
                makeClusterWithPrototype(model, getClusterUri(), entity, clusterHandle, system);
        return new Pair<>(entity, entityCluster);
    }

    /**
     * Returns a valid NIST-restricted event and its cluster.
     * @param type event type
     * @return
     */
    Pair<Resource, Resource> makeNistEvent(Resource type) {
        Resource event = AIFUtils.makeEvent(model, getEventUri(), system);
        markJustification(addType(event, type), getTypeAssertionJustification());
        Resource entityCluster = makeClusterWithPrototype(model, getClusterUri(), event, system);
        return new Pair<>(event, entityCluster);
    }

    /**
     * Returns a valid NIST-restricted event and its cluster.
     * @param type event type
     * @return
     */
    Pair<Resource, Resource> makeNistRelation(Resource type) {
        Resource relation = AIFUtils.makeRelation(model, getEventUri(), system);
        markJustification(addType(relation, type), getTypeAssertionJustification());
        Resource relationCluster = makeClusterWithPrototype(model, getClusterUri(), relation, system);
        return new Pair<>(relation, relationCluster);
    }

    Resource makeNistHypothesis(Resource... resources) {
        Resource hypothesis = makeHypothesis(resources);
        markImportance(hypothesis, 100.0);
        return hypothesis;
    }

    Resource makeNistHypothesis(double importance, Resource... resources) {
        Resource hypothesis = makeHypothesis(resources);
        markImportance(hypothesis, importance);
        return hypothesis;
    }

}
