package com.ncc.aif;

import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ResIterator;
import org.apache.jena.rdf.model.Resource;

import java.util.HashSet;
import java.util.Set;

import static com.ncc.aif.AIFUtils.markImportance;

/**
 * An extension of the NistTestUtils that supports testing TA3 restricted AIF.
 * Usage and features are the same as NistTestUtils.
 * <p>
 * Note that in many cases, you can call makeValidAIFXXX() to make a valid <i>unrestricted</i> AIF object
 * or makeValidNistXXX() to make a valid TA1/TA2 restricted AIF object for use in invalid tests,
 * whereas makeValidNistTA3XXX() always returns a valid restricted AIF object for use in TA3.
 */
class NistTA3TestUtils extends NistTestUtils {
    private static final Resource[] EMPTY = new Resource[0];
    Set<Resource> resources = new HashSet<>();

    /**
     * Constructor for utilities for testing TA3 restricted AIF functionality.
     *
     * @param annotationNamespace namespace to use with URIs
     * @param validator           an AIF validator instantiated based on the caller's ontology and desired NIST restrictions
     * @param dumpAlways          whether or not to force dumping of models prior to validation
     * @param dumpToFile          dump to file or stdout
     */
    NistTA3TestUtils(String annotationNamespace, ValidateAIF validator, boolean dumpAlways, boolean dumpToFile) {
        super(annotationNamespace, validator, dumpAlways, dumpToFile);
    }

    @Override
    Model startNewTest() {
        resources.clear();
        return super.startNewTest();
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
        addResourcesPair(pair);
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
        addResourcesPair(pair);
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
        addResourcesPair(pair);
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
        resources.add(edge);
        return edge;
    }

    /**
     * Makes and returns a valid TA3 NIST-restricted hypothesis using the stored resource objects.
     *
     * @param importance the importance to mark the edge
     */
    Resource makeValidTA3Hypothesis(double importance) {
        return makeValidTA3Hypothesis(importance, EMPTY);
    }

    /**
     * Makes and returns a valid TA3 NIST-restricted hypothesis involving the specified resource(s).
     *
     * @param resources A set of entities, relations, and arguments that contribute to the hypothesis
     */
    Resource makeValidTA3Hypothesis(Resource... resources) {
        return makeValidTA3Hypothesis(100d, resources);
    }

    /**
     * Makes and returns a valid TA3 NIST-restricted hypothesis involving the specified resource(s) and importance.
     * If no resources are specified, the internally stored resources are used
     *
     * @param importance the importance with which to make the hypothesis
     * @param resources  A set of entities, relations, and arguments that contribute to the hypothesis
     */
    Resource makeValidTA3Hypothesis(double importance, Resource... resources) {
        boolean useInternal = resources == null || resources.length == 0;
        Resource hypothesis = useInternal ? makeValidAIFHypothesis(null, this.resources) :
                makeValidAIFHypothesis(resources);
        markImportance(hypothesis, importance);
        return hypothesis;
    }

    /**
     * Add resource, cluster, and cluster membership from the provided <code>pair</code>
     * @param pair {@link ImmutablePair} containing a resource (L) and it's corresponding cluster (R)
     */
    void addResourcesPair(Pair<Resource, Resource> pair) {
        // add resource
        resources.add(pair.getLeft());

        // add cluster
        Resource cluster = pair.getRight();
        resources.add(cluster);

        // add cluster membership
        ResIterator it = model.listSubjectsWithProperty(AidaAnnotationOntology.CLUSTER_PROPERTY, cluster);
        if (it.hasNext()) {
            Resource membership = it.next();
            resources.add(membership);
        }
    }
}
