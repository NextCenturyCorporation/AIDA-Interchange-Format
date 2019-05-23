package com.ncc.aif;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

import static com.ncc.aif.AIFUtils.*;

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
     * @param dumpAlways          whether or not to force dumping of models prior to validation
     * @param dumpToFile          dump to file or stdout
     */
    NistTestUtils(String annotationNamespace, ValidateAIF validator, boolean dumpAlways, boolean dumpToFile) {
        super(annotationNamespace, validator, dumpAlways, dumpToFile);
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
        return makeValidNistObject(type, makeRelation(model, getRelationUri(), system));
    }

    // Helper function for makeValidNistXXX
    private ImmutablePair<Resource, Resource> makeValidNistObject(Resource type, Resource object) {
        markJustification(addType(object, type), makeValidJustification());
        Resource cluster = makeClusterWithPrototype(model, getClusterUri(), object, system);
        return new ImmutablePair<>(object, cluster);
    }
}
