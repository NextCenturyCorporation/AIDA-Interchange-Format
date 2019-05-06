package com.ncc.aif;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static com.ncc.aif.AIFUtils.*;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Utilities for testing AIF functionality and/or creating examples.
 * Features:
 * <p><ul>
 * <li>Contains methods for creating a valid AIF entity, event, relation, and hypothesis objects.;</li>
 * <li>Objects, assertions, and cluster URIs are numbered in a standard, sequential, simple way;</li>
 * <li>Is entirely ontology-agnostic;</li>
 * <li>Contains methods for asserting test model validity and dumping models.</li>
 * </ul></p>
 * Call {@link #startNewTest()} before each test to ensure a clean model.
 */
class TestUtils {

    // Constructor parameters
    private final ValidateAIF validator;
    private final String annotationNamespace;
    private final boolean forceDump;

    // Counters for the various elements tracked by the TestUtils
    private int assertionCount;
    private int entityCount;
    private int eventCount;
    private int relationCount;
    private int hypothesisCount;
    private int clusterCount;
    private int documentCount;

    // Data created by each test
    protected Model model;
    protected Resource system;

    /**
     * Constructor for utilities for testing AIF functionality.
     *
     * @param annotationNamespace namespace to use with URIs
     * @param validator           an AIF validator instantiated based on the caller's ontology
     * @param forceDump           whether or not to force dumping of models prior to validation
     */
    TestUtils(String annotationNamespace, ValidateAIF validator, boolean forceDump) {
        this.annotationNamespace = annotationNamespace;
        this.validator = validator;
        this.forceDump = forceDump;
    }

    /**
     * Call before each test.  Returns a new, empty model with standard AIF namespaces.
     *
     * @return a new model with which to start a test; caller may wish to add prefixes for the ontology and annotation
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
        assertionCount = entityCount = eventCount = relationCount = hypothesisCount = clusterCount = documentCount = 1;

        return model;
    }

    /**
     * Returns a prefixed String system URI in case tests need to create their own models.
     */
    String getTestSystemUri() {
        return getUri("testSystem");
    }

    /**
     * Returns a prefixed String URI of the specified name.
     */
    String getUri(String localName) {
        return annotationNamespace + localName;
    }

    /**
     * Returns a unique prefixed String URI for use with assertions.
     */
    String getAssertionUri() {
        return getUri("assertion-" + assertionCount++);
    }

    /**
     * Returns a unique prefixed String URI for use with entities.
     */
    String getEntityUri() {
        return getUri("entity-" + entityCount++);
    }

    /**
     * Returns a unique prefixed String URI for use with events.
     */
    String getEventUri() {
        return getUri("event-" + eventCount++);
    }

    /**
     * Returns a unique prefixed String URI for use with relations.
     */
    String getRelationUri() {
        return getUri("relation-" + relationCount++);
    }

    /**
     * Returns a unique prefixed String URI for use with hypotheses.
     */
    String getHypothesisUri() {
        return getUri("hypothesis-" + hypothesisCount++);
    }

    /**
     * Returns a unique prefixed String URI for use with clusters.
     */
    String getClusterUri() {
        return getUri("cluster-" + clusterCount++);
    }

    /**
     * Returns the test system created by {@link #startNewTest()}.
     */
    Resource getSystem() {
        return system;
    }

    /**
     * Returns a unique document name.
     */
    String getDocumentName() {
        return "document-" + documentCount++;
    }

    Resource makeValidJustification() {
        return makeTextJustification(model, getDocumentName(),
                documentCount * 2, documentCount * 4, system, 1.0);
    }

    /**
     * Add the specified type to the specified resource.
     *
     * @param resource the object to which to add the type
     * @param type     the type of the entity, event, or relation being asserted
     * @return the created type assertion resource
     */
    Resource addType(Resource resource, Resource type) {
        return addType(resource, type, 1.0);
    }

    /**
     * Add the specified type to the specified resource with the specified confidence.
     *
     * @param resource   the object to which to add the type
     * @param type       the type of the entity, event, or relation being asserted
     * @param confidence the confidence with which to mark the specified type
     * @return the created type assertion resource
     */
    Resource addType(Resource resource, Resource type, double confidence) {
        return markType(model, getAssertionUri(), resource, type, system, confidence);
    }

    /**
     * Makes and returns a valid AIF entity object of the specified type.
     */
    Resource makeValidAIFEntity(Resource type) {
        return makeValidAIFEntity(type, null);
    }

    /**
     * Makes and returns a valid entity object of the specified type and URI.
     */
    Resource makeValidAIFEntity(Resource type, String uri) {
        final Resource entity = makeEntity(model, uri == null ? getEntityUri() : uri, system);
        addType(entity, type);
        return entity;
    }

    /**
     * Makes and returns a valid event object of the specified type.
     */
    Resource makeValidAIFEvent(Resource type) {
        return makeValidAIFEvent(type, null);
    }

    /**
     * Makes and returns a valid event object of the specified type and URI.
     */
    Resource makeValidAIFEvent(Resource type, String uri) {
        final Resource event = makeEvent(model, uri == null ? getEventUri() : uri, system);
        addType(event, type);
        return event;
    }

    /**
     * Makes and returns a valid relation object of the specified type.
     */
    Resource makeValidAIFRelation(Resource type) {
        return makeValidAIFRelation(type, null);
    }

    /**
     * Makes and returns a valid relation object of the specified type and URI.
     */
    Resource makeValidAIFRelation(Resource type, String uri) {
        final Resource relation = makeRelation(model, uri == null ? getRelationUri() : uri, system);
        addType(relation, type);
        return relation;
    }

    /**
     * Makes and returns a valid argument assertion between the specified event or relation and an argument filler entity.
     *
     * @param eventOrRelation The event or relation for which to mark the specified argument role
     * @param type            the type of the argument
     * @param argumentFiller  the filler (object) of the argument
     * @return the created event or relation argument assertion
     */
    Resource makeValidAIFEdge(Resource eventOrRelation, Resource type, Resource argumentFiller) {
        return markAsArgument(model, eventOrRelation, type, argumentFiller, system,
                1.0, getAssertionUri());
    }

    /**
     * Makes and returns a valid hypothesis object involving the specified resource(s).
     */
    Resource makeValidAIFHypothesis(Resource... resources) {
        return makeValidAIFHypothesis(null, resources);
    }

    /**
     * Makes and returns a valid hypothesis object involving the specified resource(s) using the specified URI.
     *
     * @param resources A set of entities, relations, and arguments that contribute to the hypothesis
     */
    Resource makeValidAIFHypothesis(String uri, Resource... resources) {
        Set<Resource> set = new HashSet<>();
        Collections.addAll(set, resources);
        return makeHypothesis(model, uri == null ? getHypothesisUri() : uri, set, system);
    }

    /**
     * Assert that the test with the specified description is invalid based on the current model and validator.
     */
    void testInvalid(String testDescription) {
        assertAndDump(testDescription, false);
    }

    /**
     * Assert that the test with the specified description is valid based on the current model and validator.
     */
    void testValid(String testDescription) {
        assertAndDump(testDescription, true);
    }

    /**
     * This method will validate the model using the provided validator and will dump the model as TURTLE if
     * either the validation result is unexpected or if the model is valid and forceDump is true. Thus, forceDump
     * can be used to write all the valid examples to console.
     *
     * @param testName {@link String} containing the name of the test
     * @param expected true if validation is expected to pass, false o/w
     */
    private void assertAndDump(String testName, boolean expected) {
        final Resource report = validator.validateKBAndReturnReport(model);
        final boolean valid = ValidateAIF.isValidReport(report);

        // print model if result unexpected or if forcing (for examples)
        // Swap comments following 2 lines if forceDump should ALWAYS dump output
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

/**
 * An extension of the TestUtils that supports testing TA1 and TA2 restricted AIF.
 * Usage and features are the same as TestUtils.
 * <p>
 * Note that in many cases, you can call makeValidAIFXXX() to make a valid <i>unrestricted</i> AIF object
 * for use in invalid tests, whereas makeValidNistXXX() always returns a valid restricted AIF object.
 */
class NistTestUtils extends TestUtils {

    /**
     * Constructor for utilities for testing restricted AIF functionality.
     *
     * @param annotationNamespace namespace to use with URIs
     * @param validator           an AIF validator instantiated based on the caller's ontology and desired NIST restrictions
     * @param forceDump           whether or not to force dumping of models prior to validation
     */
    NistTestUtils(String annotationNamespace, ValidateAIF validator, boolean forceDump) {
        super(annotationNamespace, validator, forceDump);
    }

    /**
     * Call before each test.  Returns a new, empty model with standard AIF namespaces.
     *
     * @return a new model with which to start a test; caller may wish to add prefixes for the ontology and annotation
     */
    Model startNewTest() {
        Model model = super.startNewTest();
        // NIST tests always need type assertion justifications
        return model;
    }

    @Override
    Resource makeValidJustification() {
        return makeValidJustification(getDocumentName());
    }

    Resource makeValidJustification(String sourceDocument) {
        Resource justification = super.makeValidJustification();
        addSourceDocumentToJustification(justification, sourceDocument);
        return justification;
    }

    /**
     * Makes and returns a valid NIST-restricted entity of the specified type and its cluster.
     *
     * @param type entity type
     * @return a key-value Pair of the entity Resource (key) and its associated cluster Resource (value)
     */
    ImmutablePair<Resource, Resource> makeValidNistEntity(Resource type) {
        return makeValidNistObject(type, makeEntity(model, getEntityUri(), system));
    }

    /**
     * Makes and returns a valid NIST-restricted event of the specified type and its cluster.
     *
     * @param type event type
     * @return a key-value Pair of the event Resource (key) and its associated cluster Resource (value)
     */
    ImmutablePair<Resource, Resource> makeValidNistEvent(Resource type) {
        return makeValidNistObject(type, makeEvent(model, getEventUri(), system));
    }

    /**
     * Makes and returns a valid NIST-restricted relation of the specified type and its cluster.
     *
     * @param type relation type
     * @return a key-value Pair of the relation Resource (key) and its associated cluster Resource (value)
     */
    ImmutablePair<Resource, Resource> makeValidNistRelation(Resource type) {
        return makeValidNistObject(type, makeRelation(model, getEventUri(), system));
    }

    // Helper function for makeValidNistXXX
    private ImmutablePair<Resource, Resource> makeValidNistObject(Resource type, Resource object) {
        markJustification(addType(object, type), makeValidJustification());
        Resource cluster = makeClusterWithPrototype(model, getClusterUri(), object, system);
        return new ImmutablePair<>(object, cluster);
    }
}


/**
 * An extension of the NistTestUtils that supports testing TA3 restricted AIF.
 * Usage and features are the same as NistTestUtils.
 * <p>
 * Note that in many cases, you can call makeValidAIFXXX() to make a valid <i>unrestricted</i> AIF object
 * or makeValidNistXXX() to make a valid TA1/TA2 restricted AIF object for use in invalid tests,
 * whereas makeValidNistTA3XXX() always returns a valid restricted AIF object for use in TA3.
 */
class NistTA3TestUtils extends NistTestUtils {

    /**
     * Constructor for utilities for testing TA3 restricted AIF functionality.
     *
     * @param annotationNamespace namespace to use with URIs
     * @param validator           an AIF validator instantiated based on the caller's ontology and desired NIST restrictions
     * @param forceDump           whether or not to force dumping of models prior to validation
     */
    NistTA3TestUtils(String annotationNamespace, ValidateAIF validator, boolean forceDump) {
        super(annotationNamespace, validator, forceDump);
    }

    /**
     * Makes and returns a valid TA3 NIST-restricted entity of the specified type and its cluster with the specified
     * cluster handle.
     *
     * @param type          entity type
     * @param clusterHandle cluster handle for the entity cluster
     * @return a key-value Pair of the entity Resource (key) and its associated cluster Resource (value)
     */
    ImmutablePair<Resource, Resource> makeValidNistTA3Entity(Resource type, String clusterHandle) {
        ImmutablePair<Resource, Resource> pair = makeValidNistEntity(type);
        pair.getValue().addProperty(AidaAnnotationOntology.HANDLE, clusterHandle);
        return pair;
    }

    /**
     * Makes and returns a valid TA3 NIST-restricted event of the specified type and its cluster marked with the
     * specified importance.
     *
     * @param type       event type
     * @param importance the importance to mark the event cluster
     * @return a key-value Pair of the event Resource (key) and its associated cluster Resource (value)
     */
    ImmutablePair<Resource, Resource> makeValidNistTA3Event(Resource type, double importance) {
        ImmutablePair<Resource, Resource> pair = makeValidNistEvent(type);
        markImportance(pair.getValue(), importance);
        return pair;
    }

    /**
     * Makes and returns a valid TA3 NIST-restricted relation of the specified type and its cluster marked with the
     * specified importance.
     *
     * @param type       relation type
     * @param importance the importance to mark the relation cluster
     * @return a key-value Pair of the event Resource (key) and its associated cluster Resource (value)
     */
    ImmutablePair<Resource, Resource> makeValidNistTA3Relation(Resource type, double importance) {
        ImmutablePair<Resource, Resource> pair = makeValidNistRelation(type);
        markImportance(pair.getValue(), importance);
        return pair;
    }

    /**
     * Makes and returns a valid TA3 argument assertion between the specified event or relation and an argument filler entity.
     *
     * @param eventOrRelation The event or relation for which to mark the specified argument role
     * @param type            the type of the argument
     * @param argumentFiller  the filler (object) of the argument
     * @param importance      the importance to mark the edge
     * @return the created event or relation argument assertion
     */
    Resource makeValidTA3Edge(Resource eventOrRelation, Resource type, Resource argumentFiller, double importance) {
        Resource edge = makeValidAIFEdge(eventOrRelation, type, argumentFiller);
        markImportance(edge, importance);
        return edge;
    }

    /**
     * Makes and returns a valid TA3 NIST-restricted hypothesis involving the specified resource(s).
     *
     * @param resources A set of entities, relations, and arguments that contribute to the hypothesis
     */
    Resource makeValidTA3Hypothesis(Resource... resources) {
        Resource hypothesis = makeValidAIFHypothesis(resources);
        markImportance(hypothesis, 100.0);
        return hypothesis;
    }

    /**
     * Makes and returns a valid TA3 NIST-restricted hypothesis involving the specified resource(s) and importance.
     *
     * @param importance the importance with which to make the hypothesis
     * @param resources  A set of entities, relations, and arguments that contribute to the hypothesis
     */
    Resource makeValidTA3Hypothesis(double importance, Resource... resources) {
        Resource hypothesis = makeValidAIFHypothesis(resources);
        markImportance(hypothesis, importance);
        return hypothesis;
    }
}
