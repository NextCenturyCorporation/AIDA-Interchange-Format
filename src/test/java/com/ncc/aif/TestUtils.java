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

    // Set this flag to true if attempting to get examples
    private static final boolean FORCE_DUMP = false;

    private final String ontologyNamespace;

    private int assertionCount;
    private int entityCount;
    private int eventCount;
    private int relationCount;
    private int hypothesisCount;
    private int clusterCount;
    protected Model model;
    protected Resource system;
    private ValidateAIF validator;

    TestUtils(String ontologyNamespace, ValidateAIF validator) {
        this.ontologyNamespace = ontologyNamespace;
        this.validator = validator;
    }

    /**
     * Call before each test, returns a new, clean model.
     * @return
     */
    Model startNewTest() {
        if (model != null) {
            model.close();
        }
        model = ModelFactory.createDefaultModel();
        // adding namespace prefixes makes the Turtle output more readable
        addStandardNamespaces(model);
        // every AIF needs an object for the system responsible for creating it
        system = makeSystemWithURI(model, getTestSystemUri());
        assertionCount = entityCount = eventCount = relationCount = hypothesisCount = clusterCount = 1;
        entityResourceList.clear();

        return model;
    }

    String getTestSystemUri() {
        return getUri("testSystem");
    }

    String getUri(String localName) {
        return ontologyNamespace + localName;
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

    Resource addType(Resource resource, Resource type) {
        return addType(resource, type, 1.0);
    }

    Resource addType(Resource resource, Resource type, double confidence) {
        return markType(model, getAssertionUri(), resource, type, system, confidence);
    }

    Resource getValidEntity() {
        return getValidEntity(null);
    }

    Resource getValidEntity(String uri) {
        Resource entity = makeEntity(model, uri == null ? getEntityUri() : uri, system);
        entityResourceList.add(entity);
        return entity;
    }

    Resource getValidEvent() {
        return getValidEvent(null);
    }

    Resource getValidEvent(String uri) {
        return makeEvent(model, uri == null ? getEventUri() : uri, system);
    }

    Resource getValidRelation() {
        return getValidRelation(null);
    }

    Resource getValidRelation(String uri) {
        return makeRelation(model, uri == null ? getRelationUri() : uri, system);
    }

    Resource getValidHypothesis(Resource... resources) {
        return getValidHypothesis(null, resources);
    }

    Resource getValidHypothesis(String uri, Resource... resources) {
        Set<Resource> set = new HashSet<>();
        Collections.addAll(set, resources);
        return makeHypothesis(model, uri == null ? getHypothesisUri() : uri, set, system);
    }

/*
    Resource makeValidNISTEntity(Resource type) {
        final Resource entity = getValidEntity();
        markJustification(addType(entity, type), typeAssertionJustification);
        final Resource entityCluster = makeClusterWithPrototype(model, getClusterUri(), entity, system);
        return entity;
    }
*/

    void testInvalid(String name) {
        assertAndDump(name, false);
    }

    void testValid(String name) {
        assertAndDump(name, true);
    }

    void setValidator(ValidateAIF validator) {
        this.validator = validator;
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
        // if (valid != expected || FORCE_DUMP) {
        if (valid != expected || (FORCE_DUMP && expected)) {
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

    // ----------------------------------------------------------------------------------------------
    // Random Values Code.  Code below this allows us to create random values for
    // entities and events, including random ids, names, and suffixes.
    // ----------------------------------------------------------------------------------------------
    private final Random r = new Random();
    private List<Resource> entityResourceList = new ArrayList<>();

    String getRandomDocId() {
        String s = "";
        if (r.nextBoolean()) {
            s += "IC";
        } else {
            s += "HC";
        }
        s += "00";
        s += "" + (r.nextInt(1000));
        s += randomChar();
        s += randomChar();
        s += randomChar();
        return s;
    }

    private String getRandomString(int length) {
        StringBuilder s = new StringBuilder();
        for (int ii = 0; ii < length; ii++) {
            s.append(randomChar());
        }
        return s.toString();
    }

    Resource getRandomEntity() {
        return entityResourceList.get(r.nextInt(entityResourceList.size()));
    }

    private char randomChar() {
        return abc.charAt(r.nextInt(abc.length()));
    }

    // Utility values, so that we can easily create random things
    private final static String abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
}


class NistTestUtils extends TestUtils {

    NistTestUtils(String ontologyNamespace, ValidateAIF validator) {
        super(ontologyNamespace, validator);
    }

    private Resource typeAssertionJustification;

    Model startNewTest() {
        Model model = super.startNewTest();
        typeAssertionJustification = makeTextJustification(model, getRandomDocId(),
                42, 143, system, 0.973);
        addSourceDocumentToJustification(typeAssertionJustification, getRandomDocId());
        return model;
    }

    Resource getTypeAssertionJustification() {
        return typeAssertionJustification;
    }

    /**
     * Returns a valid NIST-restricted entity and its cluster.
     * @param type entity type
     * @return
     */
    Pair<Resource, Resource> makeValidEntity(Resource type) {
        Resource entity = super.getValidEntity();
        markJustification(addType(entity, type), typeAssertionJustification);
        Resource entityCluster = makeClusterWithPrototype(model, getClusterUri(), entity, system);
        return new Pair<>(entity, entityCluster);
    }

    /**
     * Returns a valid NIST-restricted event and its cluster.
     * @param type event type
     * @return
     */
    Pair<Resource, Resource> makeValidEvent(Resource type) {
        Resource event = super.getValidEvent();
        markJustification(addType(event, type), typeAssertionJustification);
        Resource eventCluster = makeClusterWithPrototype(model, getClusterUri(), event, system);
        return new Pair<>(event, eventCluster);
    }

    /**
     * Returns a valid NIST-restricted event and its cluster.
     * @param type event type
     * @return
     */
    Pair<Resource, Resource> makeValidRelation(Resource type) {
        Resource relation = super.getValidEvent();
        markJustification(addType(relation, type), typeAssertionJustification);
        Resource relationCluster = makeClusterWithPrototype(model, getClusterUri(), relation, system);
        return new Pair<>(relation, relationCluster);
    }

    @Override
    Resource getValidHypothesis(Resource... resources) {
        Resource hypothesis = super.getValidHypothesis(resources);
        markImportance(hypothesis, 100.0);
        return hypothesis;
    }

    Resource getValidHypothesis(double importance, Resource... resources) {
        Resource hypothesis = super.getValidHypothesis(resources);
        markImportance(hypothesis, importance);
        return hypothesis;
    }

}
