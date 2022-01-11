package com.ncc.aif;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import org.apache.jena.datatypes.RDFDatatype;
import org.apache.jena.graph.NodeFactory;
import org.apache.jena.query.Query;
import org.apache.jena.query.QueryExecution;
import org.apache.jena.query.QueryExecutionFactory;
import org.apache.jena.query.QueryFactory;
import org.apache.jena.query.QuerySolution;
import org.apache.jena.query.QuerySolutionMap;
import org.apache.jena.query.ResultSet;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.XSD;

/**
 * A convenient interface for creating simple AIF graphs.
 * <p>
 * More complicated graphs will require direct manipulation of the RDF.
 *
 * @author Ryan Gabbard (USC ISI)
 * @author Converted to Java by Next Century Corporation
 */
public class AIFUtils {

    // Do not instantiate AIFUtils, just access public methods statically.
    private AIFUtils() {
    }

    /**
     * Adds common non-ontology-specific namespaces to make AIF files more readable.
     */
    public static void addStandardNamespaces(Model model) {
        model.setNsPrefix("rdf", RDF.uri);
        model.setNsPrefix("xsd", XSD.getURI());
        model.setNsPrefix("aida", InterchangeOntology.NAMESPACE);
    }

    /**
     * Create a resource representing the system which produced some data.
     * <p>
     * Such a resource should be attached to all entities, events, event arguments, relations,
     * sentiment assertions, confidences, justifications, etc. produced by a system. You should
     * only create the system resource once; reuse the returned objects for all calls
     * to [markSystem].
     *
     * @param model     The underlying RDF model to create the resource
     * @param systemURI A String URI representation of the system
     * @return The created system resource
     */
    public static Resource makeSystemWithURI(Model model, String systemURI) {
        final Resource system = model.createResource(systemURI);
        system.addProperty(RDF.type, InterchangeOntology.System);
        return system;
    }

    /**
     * Mark a resource as coming from the specified [system].
     *
     * @param toMarkOn The resource to mark as coming from the specified system
     * @param system   The system with which to mark the specified resource
     */
    public static void markSystem(Resource toMarkOn, Resource system) {
        toMarkOn.addProperty(InterchangeOntology.system, system);
    }

    /**
     * Mark [entity] as having the specified [name].
     *
     * @param entity The Resource to mark with the specified name
     * @param name   The String name with which to mark the specified Resource
     */
    public static void markName(Resource entity, String name) {
        entity.addLiteral(InterchangeOntology.hasName, name);
    }

    /**
     * Mark [entity] as having the specified [textValue].
     *
     * @param entity    The Resource to mark as having the specified text value
     * @param textValue The String text value with which to mark the specified Resource
     */
    public static void markTextValue(Resource entity, String textValue) {
        entity.addLiteral(InterchangeOntology.textValue, textValue);
    }

    /**
     * Mark [entity] as having the specified [numericValue] as string.
     *
     * @param entity       The Resource to mark as having the specified numeric value
     * @param numericValue A String representation of a numeric value with which to
     *                     mark the specified Resource
     */
    public static void markNumericValueAsString(Resource entity, String numericValue) {
        entity.addLiteral(InterchangeOntology.numericValue, numericValue);
    }

    /**
     * Mark [entity] as having the specified [numericValue] as double floating point.
     *
     * @param entity       The Resource to mark as having the specified numeric value
     * @param numericValue A Double representation of a numeric value with which to
     *                     mark the specified Resource
     */
    public static void markNumericValueAsDouble(Resource entity, Double numericValue) {
        entity.addLiteral(InterchangeOntology.numericValue, numericValue.doubleValue());
    }

    /**
     * Mark [entity] as having the specified [numericValue] as long integer.
     *
     * @param entity       The Resource to mark as having the specified numeric value
     * @param numericValue A Long representation of a numeric value with which to
     *                     mark the specified Resource
     */
    public static void markNumericValueAsLong(Resource entity, Long numericValue) {
        entity.addLiteral(InterchangeOntology.numericValue, numericValue.longValue());
    }

    /**
     * Create an entity.
     *
     * @param model     The underlying RDF model to make the entity
     * @param entityUri A unique String URI for the entity
     * @param system    The system object for the system which created the specified entity
     * @return The created entity resource
     */
    public static Resource makeEntity(Model model, String entityUri, Resource system) {
        return makeAIFResource(model, entityUri, InterchangeOntology.Entity, system);
    }

    /**
     * Create a relation.
     *
     * @param model       The underlying RDF model for the operation
     * @param relationUri A unique String URI for the relation
     * @param system      The system object for the system which created the specified relation
     * @return The created relation resource
     */
    public static Resource makeRelation(Model model, String relationUri, Resource system) {
        return makeAIFResource(model, relationUri, InterchangeOntology.Relation, system);
    }

    /**
     * Make a relation of type [relationType] between [subjectResource] and [objectResource] in a form
     * similar to that of an event: subjects and objects are explicitly linked to relation via [subjectRole]
     * and [objectRole], respectively.
     * <p>
     * If [confidence] is non-null the relation is marked with the given [confidence]
     *
     * @param model            The underlying RDF model for the operation
     * @param relationUri      A unique String URI for the specified relation
     * @param relationType     The type of relation to make
     * @param subjectRole      The role to link the specified subject to the specified relation
     * @param subjectResource  The subject to which to link the specified relation via the specified role
     * @param objectRole       The role to link the specified object to the specified relation
     * @param objectResource   The object to which to link the specified relation via the specified role
     * @param typeAssertionUri The String URI of a type assertion resource with which to mark the relation
     * @param system           The system object for the system which created the specified relation
     * @param confidence       If non-null, the confidence with which to mark the specified relation
     * @return The created relation resource
     * @deprecated This method doesn't allow the user access to the blank nodes created for type and argument assertions.
     * Use {@link #makeRelation(Model, String, Resource)} instead
     */
    @Deprecated
    public static Resource makeRelationInEventForm(Model model, String relationUri, Resource relationType, Resource subjectRole,
                                                   Resource subjectResource, Resource objectRole, Resource objectResource,
                                                   String typeAssertionUri, Resource system, Double confidence) {
        final Resource relation = makeRelation(model, relationUri, system);
        markType(model, typeAssertionUri, relation, relationType, system, confidence);
        markAsArgument(model, relation, subjectRole, subjectResource, system, confidence);
        markAsArgument(model, relation, objectRole, objectResource, system, confidence);
        return relation;
    }

    /**
     * Create an event.
     *
     * @param model    The underlying RDF model for the operation
     * @param eventUri A unique String URI for the event
     * @param system   The system object for the system which created this event
     * @return The created event resource
     */
    public static Resource makeEvent(Model model, String eventUri, Resource system) {
        return makeAIFResource(model, eventUri, InterchangeOntology.Event, system);
    }

    private static <T> Resource makeAIFStatement(Model model, Resource subject, T predicate, T object,
                                                Resource system, Double confidence, String uri) {
        final Resource statement = makeAIFResource(model, uri, RDF.Statement, system);

        statement.addProperty(RDF.subject, subject);
        if (predicate instanceof Resource) {
            statement.addProperty(RDF.predicate, (Resource)predicate);
        } else {
            statement.addProperty(RDF.predicate, predicate.toString());
        }
        if (object instanceof Resource) {
            statement.addProperty(RDF.object, (Resource)object);
        } else {
            statement.addProperty(RDF.object, object.toString());
        }
        if (confidence != null) {
            markConfidence(model, statement, confidence, system);
        }
        return statement;
    }

    /**
     * Mark an entity as filling an argument role for an event or relation.
     * The argument assertion will be a blank node.
     *
     * @param model           The underlying RDF model for the operation
     * @param eventOrRelation The event or relation for which to mark the specified argument role
     * @param argumentType    The type (predicate) of the argument
     * @param argumentFiller  The filler (object) of the argument
     * @param system          The system object for the system which created this argument
     * @param confidence      If non-null, the confidence with which to mark the specified argument
     * @return The created event or relation argument assertion
     */
    public static Resource markAsArgument(Model model, Resource eventOrRelation, Resource argumentType,
                                          Resource argumentFiller, Resource system,
                                          Double confidence) {

        return markAsArgument(model, eventOrRelation, argumentType, argumentFiller, system, confidence, null);
    }

    /**
     * Mark an entity as filling an argument role for an event or relation.
     * The argument assertion will be identified by the specified URI.
     *
     * @param model           The underlying RDF model for the operation
     * @param eventOrRelation The event or relation for which to mark the specified argument role
     * @param argumentType    The type (predicate) of the argument
     * @param argumentFiller  The filler (object) of the argument
     * @param system          The system object for the system which created this argument
     * @param confidence      If non-null, the confidence with which to mark the specified argument
     * @param uri             A String URI for the argument assertion
     * @return The created event or relation argument assertion with uri
     */
    public static Resource markAsArgument(Model model, Resource eventOrRelation, Resource argumentType,
                                          Resource argumentFiller, Resource system,
                                          Double confidence, String uri) {

        return makeAIFStatement(model, eventOrRelation, argumentType, argumentFiller, system, confidence, uri)
            .addProperty(RDF.type, InterchangeOntology.ArgumentStatement);
    }

    /**
     * Mark an entity as filling a DWD (string) argument role for an event or relation.
     * The argument assertion will be a blank node.
     *
     * @param model           The underlying RDF model for the operation
     * @param eventOrRelation The event or relation for which to mark the specified argument role
     * @param argumentType    The type (predicate) of the argument
     * @param argumentFiller  The filler (object) of the argument
     * @param system          The system object for the system which created this argument
     * @param confidence      If non-null, the confidence with which to mark the specified argument
     * @return The created event or relation argument assertion
     */
    public static Resource markAsArgument(Model model, Resource eventOrRelation, String argumentType,
                                          Resource argumentFiller, Resource system,
                                          Double confidence) {

        return markAsArgument(model, eventOrRelation, argumentType, argumentFiller, system, confidence, null);
    }

    /**
     * Mark an entity as filling a DWD (string) argument role for an event or relation.
     * The argument assertion will be identified by the specified URI.
     *
     * @param model           The underlying RDF model for the operation
     * @param eventOrRelation The event or relation for which to mark the specified argument role
     * @param argumentType    The type (predicate) of the argument
     * @param argumentFiller  The filler (object) of the argument
     * @param system          The system object for the system which created this argument
     * @param confidence      If non-null, the confidence with which to mark the specified argument
     * @param uri             A String URI for the argument assertion
     * @return The created event or relation argument assertion with uri
     */
    public static Resource markAsArgument(Model model, Resource eventOrRelation, String argumentType,
                                          Resource argumentFiller, Resource system,
                                          Double confidence, String uri) {

        return makeAIFStatement(model, eventOrRelation, argumentType, argumentFiller, system, confidence, uri)
            .addProperty(RDF.type, InterchangeOntology.ArgumentStatement);
    }

    /**
     * Mark an entity, event, or relation as having a specified type.
     * <p>
     * This is marked with a separate assertion so that uncertainty about type can be expressed.
     * In such a case, bundle together the type assertion resources returned by this method with
     * [markAsMutuallyExclusive].
     *
     * @param model                   The underlying RDF model for the operation
     * @param typeAssertionUri        The String URI of a type assertion resource with which to mark the entity or event
     * @param entityOrEventOrRelation The entity, event, or relation to mark as having the specified type
     * @param type                    The type of the entity, event, or relation being asserted
     * @param system                  The system object for the system which created this entity
     * @param confidence              If non-null, the confidence with which to mark the specified type
     * @return The created type assertion resource
     */
    public static Resource markType(Model model, String typeAssertionUri, Resource entityOrEventOrRelation,
                                    Resource type, Resource system, Double confidence) {
        return makeAIFStatement(model, entityOrEventOrRelation, RDF.type, type, system, confidence, typeAssertionUri)
            .addProperty(RDF.type, InterchangeOntology.TypeStatement);
    }

    /**
     * Mark an entity, event, or relation as having a specified DWD (String) type.
     * <p>
     * This is marked with a separate assertion so that uncertainty about type can be expressed.
     * In such a case, bundle together the type assertion resources returned by this method with
     * [markAsMutuallyExclusive].
     *
     * @param model                   The underlying RDF model for the operation
     * @param typeAssertionUri        The String URI of a type assertion resource with which to mark the entity or event
     * @param entityOrEventOrRelation The entity, event, or relation to mark as having the specified type
     * @param type                    The type of the entity, event, or relation being asserted
     * @param system                  The system object for the system which created this entity
     * @param confidence              If non-null, the confidence with which to mark the specified type
     * @return The created type assertion resource
     */
    public static Resource markType(Model model, String typeAssertionUri, Resource entityOrEventOrRelation,
                                    String type, Resource system, Double confidence) {
        return makeAIFStatement(model, entityOrEventOrRelation, RDF.type, type, system, confidence, typeAssertionUri)
            .addProperty(RDF.type, InterchangeOntology.TypeStatement);
    }

    // Helper function to create a justification (text, image, audio, etc.) in the system.
    private static Resource makeAIFJustification(Model model, String docId, Resource classType,
                                                 Resource system, Double confidence, String uri) {
        final Resource justification = makeAIFResource(model, uri, classType, system);
        justification.addProperty(InterchangeOntology.source, model.createTypedLiteral(docId));
        markConfidence(model, justification, confidence, system);
        return justification;
    }

    /**
     * Mark something as being justified by a particular justification.
     *
     * @param toMarkOn      The Resource to be marked by the specified justification
     * @param justification The justification to be marked onto the specified resource
     */
    public static void markJustification(Resource toMarkOn, Resource justification) {
        toMarkOn.addProperty(InterchangeOntology.justifiedBy, justification);
    }

    /**
     * Mark multiple things as being justified by a particular justification
     *
     * @param toMarkOn      A Collection of Resources to be marked by the specified justification
     * @param justification The justification to be marked on the specified collection of resources
     */
    public static void markJustification(Collection<Resource> toMarkOn, Resource justification) {
        toMarkOn.forEach(it -> markJustification(it, justification));
    }

    /**
     * Create a justification from a particular snippet of text.
     *
     * @param model              The underlying RDF model for the operation
     * @param docId              A string containing the document element (child) ID of the source of the justification
     * @param startOffset        An integer offset within the document for the start of the justification
     * @param endOffsetInclusive An integer offset within the document for the end of the justification
     * @param system             The system object for the system which made this justification
     * @param confidence         The confidence with which to mark the justification
     * @return The created text justification resource
     */
    public static Resource makeTextJustification(Model model, String docId, int startOffset, int endOffsetInclusive,
                                                 Resource system, Double confidence) {
        return makeTextJustification(model, docId, startOffset, endOffsetInclusive, system, confidence, null);
    }

    /**
     * Create a justification from a particular snippet of text.
     *
     * @param model              The underlying RDF model for the operation
     * @param docId              A string containing the document element (child) ID of the source of the justification
     * @param startOffset        An integer offset within the document for the start of the justification
     * @param endOffsetInclusive An integer offset within the document for the end of the justification
     * @param system             The system object for the system which made this justification
     * @param confidence         The confidence with which to mark the justification
     * @param uri                A String uri representation of the justification
     * @return The created text justification resource
     */
    public static Resource makeTextJustification(Model model, String docId, int startOffset, int endOffsetInclusive,
                                                 Resource system, Double confidence, String uri) {
        if (endOffsetInclusive < startOffset) {
            throw new IllegalArgumentException("End offset " + endOffsetInclusive + " precedes start offset " + startOffset);
        }
        if (startOffset < 0) {
            throw new IllegalArgumentException("Start offset must be non-negative but got " + startOffset);
        }

        final Resource justification = makeAIFJustification(model, docId, InterchangeOntology.TextJustification,
                system, confidence, uri);
        // the document ID for the justifying source document
        justification.addProperty(InterchangeOntology.startOffset,
                model.createTypedLiteral(startOffset));
        justification.addProperty(InterchangeOntology.endOffsetInclusive,
                model.createTypedLiteral(endOffsetInclusive));

        return justification;
    }

    /**
     * Mark something as being justified by a particular snippet of text.
     *
     * @param model              The underlying RDF model for the operation
     * @param toMarkOn           The Resource to be marked by the specified text document
     * @param docId              A string containing the document element (child) ID of the source of the justification
     * @param startOffset        An integer offset within the document for start of the justification
     * @param endOffsetInclusive An integer offset within the document for the end of the justification
     * @param system             The system object for the system which marked this justification
     * @param confidence         The confidence with which to mark the justification
     * @return The created text justification resource
     */
    public static Resource markTextJustification(Model model, Resource toMarkOn, String docId,
                                                 int startOffset, int endOffsetInclusive,
                                                 Resource system, Double confidence) {
        return markTextJustification(model, ImmutableSet.of(toMarkOn), docId, startOffset,
                endOffsetInclusive, system, confidence, null);
    }

    /**
     * Mark something as being justified by a particular snippet of text.
     *
     * @param model              The underlying RDF model for the operation
     * @param toMarkOn           The Resource to be marked by the specified text document
     * @param docId              A string containing the document element (child) ID of the source of the justification
     * @param startOffset        An integer offset within the document for start of the justification
     * @param endOffsetInclusive An integer offset within the document for the end of the justification
     * @param system             The system object for the system which marked this justification
     * @param confidence         The confidence with which to mark the justification
     * @param uri                A String uri representation of the text justification
     * @return The created text justification resource
     */
    public static Resource markTextJustification(Model model, Resource toMarkOn, String docId,
                                                 int startOffset, int endOffsetInclusive,
                                                 Resource system, Double confidence, String uri) {
        return markTextJustification(model, ImmutableSet.of(toMarkOn), docId, startOffset,
                endOffsetInclusive, system, confidence, uri);
    }

    /**
     * Mark multiple things as being justified by a particular snippet of text.
     *
     * @param model              The underlying RDF model for the operation
     * @param toMarkOn           A Collection of Resources to be marked by the specified text document
     * @param docId              A string containing the document element (child) ID of the source of the justification
     * @param startOffset        An integer offset within the document for start of the justification
     * @param endOffsetInclusive An integer offset within the document for the end of the justification
     * @param system             The system object for the system which marked this justification
     * @param confidence         The confidence with which to mark the justification
     * @return The created text justification resource
     */
    public static Resource markTextJustification(Model model, Collection<Resource> toMarkOn, String docId,
                                                 int startOffset, int endOffsetInclusive,
                                                 Resource system, Double confidence) {
        return markTextJustification(model, toMarkOn, docId, startOffset, endOffsetInclusive, system, confidence, null);
    }

    /**
     * Mark multiple things as being justified by a particular snippet of text.
     *
     * @param model              The underlying RDF model for the operation
     * @param toMarkOn           A Collection of Resources to be marked by the specified text document
     * @param docId              A string containing the document element (child) ID of the source of the justification
     * @param startOffset        An integer offset within the document for start of the justification
     * @param endOffsetInclusive An integer offset within the document for the end of the justification
     * @param system             The system object for the system which marked this justification
     * @param confidence         The confidence with which to mark the justification
     * @param uri                A String uri representation of the text justification
     * @return The created text justification resource
     */
    public static Resource markTextJustification(Model model, Collection<Resource> toMarkOn, String docId,
                                                 int startOffset, int endOffsetInclusive,
                                                 Resource system, Double confidence, String uri) {
        final Resource justification = makeTextJustification(model, docId, startOffset, endOffsetInclusive, system, confidence, uri);
        markJustification(toMarkOn, justification);
        return justification;
    }

    /**
     * Add a sourceDocument to a pre-existing justification
     *
     * @param justification  A pre-existing justification resource
     * @param sourceDocument A string containing the source document (parent) ID
     * @return The modified justification resource
     */
    public static Resource addSourceDocumentToJustification(Resource justification, String sourceDocument) {
        justification.addProperty(InterchangeOntology.sourceDocument, sourceDocument);
        return justification;
    }

    /**
     * This inner class encapsulates an AIDA image or video coordinate.
     */
    public static final class Point {
        private final int x;
        private final int y;

        /**
         * Create a point from an x- and y-coordinate.
         *
         * @param x the x-coordinate of the point
         * @param y the y-coordinate of the point
         */
        public Point(int x, int y) {
            if (x < 0) {
                throw new IllegalArgumentException("AIDA image/video coordinates must be non-negative but got " + x);
            }
            if (y < 0) {
                throw new IllegalArgumentException("AIDA image/video coordinates must be non-negative but got " + y);
            }
            this.x = x;
            this.y = y;
        }

        /**
         * Copy constructor
         *
         * @param point A point from which to make a copy
         */
        public Point(Point point) {
            x = point.x;
            y = point.y;
        }

        /**
         * Return the x-coordinate of the point.
         *
         * @return The integer x-coordinate of the point
         */
        public int getX() {
            return x;
        }

        /**
         * Return the y-coordinate of the point.
         *
         * @return The integer y-coordinate of the point
         */
        public int getY() {
            return y;
        }
    }

    /**
     * This inner class encapsulates the bounding box of an image or video source.
     */
    public static final class BoundingBox {
        private final Point upperLeft;
        private final Point lowerRight;

        /**
         * Create a bounding box from two points.
         *
         * @param upperLeft  The upper-left point of the bounding box
         * @param lowerRight The lower-right point of the bounding box
         */
        public BoundingBox(Point upperLeft, Point lowerRight) {
            if (upperLeft.x > lowerRight.x || upperLeft.y > lowerRight.y) {
                throw new IllegalArgumentException("Upper left of bounding box " + upperLeft +
                        " not above and to the left of lower right " + lowerRight);
            }
            this.upperLeft = new Point(upperLeft.x, upperLeft.y);
            this.lowerRight = new Point(lowerRight.x, lowerRight.y);
        }

        /**
         * Copy constructor
         *
         * @param boundingBox A bounding box from which to make a copy
         */
        public BoundingBox(BoundingBox boundingBox) {
            upperLeft = new Point(boundingBox.upperLeft);
            lowerRight = new Point(boundingBox.lowerRight);
        }

        /**
         * Return the upper-left point of the bounding box.
         *
         * @return The upper-left Point of the bounding box
         */
        public Point getUpperLeft() {
            return upperLeft;
        }

        /**
         * Return the lower-right point of the bounding box.
         *
         * @return The lower-right Point of the bounding box
         */
        public Point getLowerRight() {
            return lowerRight;
        }

    }

    // Mark the specified resource with the specified bounding box.
    private static Resource markBoundingBox(Model model, Resource toMarkOn, BoundingBox boundingBox) {

        final Resource boundingBoxResource = model.createResource();
        boundingBoxResource.addProperty(RDF.type, InterchangeOntology.BoundingBox);
        boundingBoxResource.addProperty(InterchangeOntology.boundingBoxUpperLeftX,
                model.createTypedLiteral(boundingBox.upperLeft.x));
        boundingBoxResource.addProperty(InterchangeOntology.boundingBoxUpperLeftY,
                model.createTypedLiteral(boundingBox.upperLeft.y));
        boundingBoxResource.addProperty(InterchangeOntology.boundingBoxLowerRightX,
                model.createTypedLiteral(boundingBox.lowerRight.x));
        boundingBoxResource.addProperty(InterchangeOntology.boundingBoxLowerRightY,
                model.createTypedLiteral(boundingBox.lowerRight.y));

        toMarkOn.addProperty(InterchangeOntology.boundingBox, boundingBoxResource);

        return boundingBoxResource;
    }

    /**
     * Make an image justification.
     *
     * @param model       The underlying RDF model for the operation
     * @param docId       A string containing the document element (child) ID of the source of the justification
     * @param boundingBox A rectangular box within the image that bounds the justification
     * @param system      The system object for the system which made this justification
     * @param confidence  The confidence with which to mark the justification
     * @return The created image justification resource
     */
    public static Resource makeImageJustification(Model model, String docId, BoundingBox boundingBox, Resource system,
                                                  Double confidence) {
        return makeImageJustification(model, docId, boundingBox, system, confidence, null);
    }

    /**
     * Make an image justification.
     *
     * @param model       The underlying RDF model for the operation
     * @param docId       A string containing the document element (child) ID of the source of the justification
     * @param boundingBox A rectangular box within the image that bounds the justification
     * @param system      The system object for the system which made this justification
     * @param confidence  The confidence with which to mark the justification
     * @param uri         A String uri representation of the justification
     * @return The created image justification resource
     */
    public static Resource makeImageJustification(Model model, String docId, BoundingBox boundingBox, Resource system,
                                                  Double confidence, String uri) {
        final Resource justification = makeAIFJustification(model, docId, InterchangeOntology.ImageJustification,
                system, confidence, uri);
        markBoundingBox(model, justification, boundingBox);
        return justification;
    }

    /**
     * Mark something as being justified by a particular image.
     *
     * @param model       The underlying RDF model for the operation
     * @param toMarkOn    The Resource to be marked by the specified image document
     * @param docId       A string containing the document element (child) ID of the source of the justification
     * @param boundingBox A rectangular box within the image that bounds the justification
     * @param system      The system object for the system which marked this justification
     * @param confidence  The confidence with which to mark the justification
     * @return The created image justification resource
     */
    public static Resource markImageJustification(Model model, Resource toMarkOn, String docId,
                                                  BoundingBox boundingBox, Resource system, Double confidence) {
        return markImageJustification(model, ImmutableSet.of(toMarkOn), docId, boundingBox, system, confidence, null);
    }

    /**
     * Mark something as being justified by a particular image.
     *
     * @param model       The underlying RDF model for the operation
     * @param toMarkOn    The Resource to be marked by the specified image document
     * @param docId       A string containing the document element (child) ID of the source of the justification
     * @param boundingBox A rectangular box within the image that bounds the justification
     * @param system      The system object for the system which marked this justification
     * @param confidence  The confidence with which to mark the justification
     * @param uri         A String uri representation of the justification
     * @return The created image justification resource
     */
    public static Resource markImageJustification(Model model, Resource toMarkOn, String docId,
                                                  BoundingBox boundingBox, Resource system, Double confidence, String uri) {
        return markImageJustification(model, ImmutableSet.of(toMarkOn), docId, boundingBox, system, confidence, uri);
    }

    /**
     * Mark multiple things as being justified by a particular image.
     *
     * @param model       The underlying RDF model for the operation
     * @param toMarkOn    A Collection of Resources to be marked by the specified image document
     * @param docId       A string containing the document element (child) ID of the source of the justification
     * @param boundingBox A rectangular box within the image that bounds the justification
     * @param system      The system object for the system which made this justification
     * @param confidence  The confidence with which to mark the justification
     * @return The created image justification resource
     */
    public static Resource markImageJustification(Model model, Collection<Resource> toMarkOn, String docId,
                                                  BoundingBox boundingBox, Resource system, Double confidence) {
        return markImageJustification(model, toMarkOn, docId, boundingBox, system, confidence, null);
    }

    /**
     * Mark multiple things as being justified by a particular image.
     *
     * @param model       The underlying RDF model for the operation
     * @param toMarkOn    A Collection of Resources to be marked by the specified image document
     * @param docId       A string containing the document element (child) ID of the source of the justification
     * @param boundingBox A rectangular box within the image that bounds the justification
     * @param system      The system object for the system which made this justification
     * @param confidence  The confidence with which to mark the justification
     * @param uri         A String uri representation of the justification
     * @return The created image justification resource
     */
    public static Resource markImageJustification(Model model, Collection<Resource> toMarkOn, String docId,
                                                  BoundingBox boundingBox, Resource system, Double confidence, String uri) {
        final Resource justification = makeImageJustification(model, docId, boundingBox, system, confidence, uri);
        markJustification(toMarkOn, justification);
        return justification;
    }

    /**
     * Create a justification from something appearing in a key frame of a video.
     *
     * @param model       The underlying RDF model for the operation
     * @param docId       A string containing the document element (child) ID of the source of the justification
     * @param keyFrame    The String Id of the key frame of the specified video document
     * @param boundingBox A rectangular box within the key frame that bounds the justification
     * @param system      The system object for the system which made this justification
     * @param confidence  The confidence with which to mark the justification
     * @return The created video justification resource
     */
    public static Resource makeKeyFrameVideoJustification(Model model, String docId, String keyFrame, BoundingBox boundingBox,
                                                          Resource system, Double confidence) {
        return makeKeyFrameVideoJustification(model, docId, keyFrame, boundingBox, system, confidence, null);
    }

    /**
     * Create a justification from something appearing in a key frame of a video.
     *
     * @param model       The underlying RDF model for the operation
     * @param docId       A string containing the document element (child) ID of the source of the justification
     * @param keyFrame    The String Id of the key frame of the specified video document
     * @param boundingBox A rectangular box within the key frame that bounds the justification
     * @param system      The system object for the system which made this justification
     * @param confidence  The confidence with which to mark the justification
     * @param uri         A String uri representation of the justification
     * @return The created video justification resource
     */
    public static Resource makeKeyFrameVideoJustification(Model model, String docId, String keyFrame, BoundingBox boundingBox,
                                                          Resource system, Double confidence, String uri) {
        final Resource justification = makeAIFJustification(model, docId, InterchangeOntology.KeyFrameVideoJustification,
                system, confidence, uri);
        justification.addProperty(InterchangeOntology.keyFrame, model.createTypedLiteral(keyFrame));
        markBoundingBox(model, justification, boundingBox);
        return justification;
    }

    /**
     * Mark a justification for something appearing in a key frame of a video.
     *
     * @param model       The underlying RDF model for the operation
     * @param toMarkOn    The Resource to be marked by the specified video document
     * @param docId       A string containing the document element (child) ID of the source of the justification
     * @param keyFrame    The String Id of the key frame of the specified video document
     * @param boundingBox A rectangular box within the key frame that bounds the justification
     * @param system      The system object for the system which made this justification
     * @param confidence  The confidence with which to mark the justification
     * @return The created video justification resource
     */
    public static Resource markKeyFrameVideoJustification(Model model, Resource toMarkOn, String docId, String keyFrame,
                                                          BoundingBox boundingBox, Resource system, Double confidence) {
        return markKeyFrameVideoJustification(model, ImmutableSet.of(toMarkOn), docId,
                keyFrame, boundingBox, system, confidence, null);
    }

    /**
     * Mark a justification for something appearing in a key frame of a video.
     *
     * @param model       The underlying RDF model for the operation
     * @param toMarkOn    The Resource to be marked by the specified video document
     * @param docId       A string containing the document element (child) ID of the source of the justification
     * @param keyFrame    The String Id of the key frame of the specified video document
     * @param boundingBox A rectangular box within the key frame that bounds the justification
     * @param system      The system object for the system which made this justification
     * @param confidence  The confidence with which to mark the justification
     * @param uri         A String uri representation of the justification
     * @return The created video justification resource
     */
    public static Resource markKeyFrameVideoJustification(Model model, Resource toMarkOn, String docId, String keyFrame,
                                                          BoundingBox boundingBox, Resource system, Double confidence, String uri) {
        return markKeyFrameVideoJustification(model, ImmutableSet.of(toMarkOn), docId,
                keyFrame, boundingBox, system, confidence, uri);
    }

    /**
     * Mark multiple things as being justified by appearing in a key frame of a video.
     *
     * @param model       The underlying RDF model for the operation
     * @param toMarkOn    A Collection of Resources to be marked by the specified video document
     * @param docId       A string containing the document element (child) ID of the source of the justification
     * @param keyFrame    The String Id of the key frame of the specified video document
     * @param boundingBox A rectangular box within the key frame that bounds the justification
     * @param system      The system object for the system which made this justification
     * @param confidence  The confidence with which to mark the justification
     * @return The created video justification resource
     */
    public static Resource markKeyFrameVideoJustification(Model model, Collection<Resource> toMarkOn, String docId, String keyFrame,
                                                          BoundingBox boundingBox, Resource system, Double confidence) {
        return markKeyFrameVideoJustification(model, toMarkOn, docId, keyFrame, boundingBox, system, confidence, null);
    }

    /**
     * Mark multiple things as being justified by appearing in a key frame of a video.
     *
     * @param model       The underlying RDF model for the operation
     * @param toMarkOn    A Collection of Resources to be marked by the specified video document
     * @param docId       A string containing the document element (child) ID of the source of the justification
     * @param keyFrame    The String Id of the key frame of the specified video document
     * @param boundingBox A rectangular box within the key frame that bounds the justification
     * @param system      The system object for the system which made this justification
     * @param confidence  The confidence with which to mark the justification
     * @param uri         A String uri representation of the justification
     * @return The created video justification resource
     */
    public static Resource markKeyFrameVideoJustification(Model model, Collection<Resource> toMarkOn, String docId, String keyFrame,
                                                          BoundingBox boundingBox, Resource system, Double confidence, String uri) {
        final Resource justification = makeKeyFrameVideoJustification(model, docId, keyFrame, boundingBox, system, confidence, uri);
        markJustification(toMarkOn, justification);
        return justification;
    }

    /**
     * Create a justification from something appearing in a video but not in a key frame.
     *
     * @param model      The underlying RDF model for the operation
     * @param docId      A string containing the document element (child) ID of the source of the justification
     * @param shotId     The String Id of the shot of the specified video document
     * @param system     The system object for the system which made this justification
     * @param confidence The confidence with which to mark the justification
     * @return The created video justification resource
     */
    public static Resource makeShotVideoJustification(Model model, String docId, String shotId, Resource system,
                                                      Double confidence) {
        return makeShotVideoJustification(model, docId, shotId, system, confidence, null);
    }

    /**
     * Create a justification from something appearing in a video but not in a key frame.
     *
     * @param model      The underlying RDF model for the operation
     * @param docId      A string containing the document element (child) ID of the source of the justification
     * @param shotId     The String Id of the shot of the specified video document
     * @param system     The system object for the system which made this justification
     * @param confidence The confidence with which to mark the justification
     * @param uri        A String uri representation of the justification
     * @return The created video justification resource
     */
    public static Resource makeShotVideoJustification(Model model, String docId, String shotId, Resource system,
                                                      Double confidence, String uri) {
        final Resource justification = makeAIFJustification(model, docId, InterchangeOntology.ShotVideoJustification,
                system, confidence, uri);
        justification.addProperty(InterchangeOntology.shot, model.createTypedLiteral(shotId));
        return justification;
    }

    /**
     * Mark a justification for something appearing in a video but not in a key frame.
     *
     * @param model      The underlying RDF model for the operation
     * @param toMarkOn   A Resource to be marked by the specified video document
     * @param docId      A string containing the document element (child) ID of the source of the justification
     * @param shotId     The String Id of the shot of the specified video document
     * @param system     The system object for the system which made this justification
     * @param confidence The confidence with which to mark the justification
     * @return The created video justification resource
     */
    public static Resource markShotVideoJustification(Model model, Resource toMarkOn, String docId, String shotId,
                                                      Resource system, Double confidence) {
        return markShotVideoJustification(model, ImmutableSet.of(toMarkOn), docId, shotId, system, confidence, null);
    }

    /**
     * Mark a justification for something appearing in a video but not in a key frame.
     *
     * @param model      The underlying RDF model for the operation
     * @param toMarkOn   A Resource to be marked by the specified video document
     * @param docId      A string containing the document element (child) ID of the source of the justification
     * @param shotId     The String Id of the shot of the specified video document
     * @param system     The system object for the system which made this justification
     * @param confidence The confidence with which to mark the justification
     * @param uri        A String uri representation of the justification
     * @return The created video justification resource
     */
    public static Resource markShotVideoJustification(Model model, Resource toMarkOn, String docId, String shotId,
                                                      Resource system, Double confidence, String uri) {
        return markShotVideoJustification(model, ImmutableSet.of(toMarkOn), docId, shotId, system, confidence, uri);
    }

    /**
     * Mark multiple things as being justified by appearing in a video but not in a key frame.
     *
     * @param model      The underlying RDF model for the operation
     * @param toMarkOn   A Collection of Resources to be marked by the specified video document
     * @param docId      A string containing the document element (child) ID of the source of the justification
     * @param shotId     The String Id of the shot of the specified video document
     * @param system     The system object for the system which made this justification
     * @param confidence The confidence with which to mark the justification
     * @return The created video justification resource
     */
    public static Resource markShotVideoJustification(Model model, Collection<Resource> toMarkOn, String docId, String shotId,
                                                      Resource system, Double confidence) {
        return markShotVideoJustification(model, toMarkOn, docId, shotId, system, confidence, null);
    }

    /**
     * Mark multiple things as being justified by appearing in a video but not in a key frame.
     *
     * @param model      The underlying RDF model for the operation
     * @param toMarkOn   A Collection of Resources to be marked by the specified video document
     * @param docId      A string containing the document element (child) ID of the source of the justification
     * @param shotId     The String Id of the shot of the specified video document
     * @param system     The system object for the system which made this justification
     * @param confidence The confidence with which to mark the justification
     * @param uri        A String uri representation of the justification
     * @return The created video justification resource
     */
    public static Resource markShotVideoJustification(Model model, Collection<Resource> toMarkOn, String docId, String shotId,
                                                      Resource system, Double confidence, String uri) {
        final Resource justification = makeShotVideoJustification(model, docId, shotId, system, confidence, uri);
        markJustification(toMarkOn, justification);
        return justification;
    }

    /**
     * Make an audio justification.
     *
     * @param model          The underlying RDF model for the operation
     * @param docId          A string containing the document element (child) ID of the source of the justification
     * @param startTimestamp A timestamp within the audio document where the justification starts
     * @param endTimestamp   A timestamp within the audio document where the justification ends
     * @param system         The system object for the system which made this justification
     * @param confidence     The confidence with which to mark the justification
     * @return The created audio justification resource
     */
    public static Resource makeAudioJustification(Model model, String docId, Double startTimestamp, Double endTimestamp,
                                                  Resource system, Double confidence) {
        return makeAudioJustification(model, docId, startTimestamp, endTimestamp, system, confidence, null);
    }

    /**
     * Make an audio justification.
     *
     * @param model          The underlying RDF model for the operation
     * @param docId          A string containing the document element (child) ID of the source of the justification
     * @param startTimestamp A timestamp within the audio document where the justification starts
     * @param endTimestamp   A timestamp within the audio document where the justification ends
     * @param system         The system object for the system which made this justification
     * @param confidence     The confidence with which to mark the justification
     * @param uri            A String uri representation of the justification
     * @return The created audio justification resource
     */
    public static Resource makeAudioJustification(Model model, String docId, Double startTimestamp, Double endTimestamp,
                                                  Resource system, Double confidence, String uri) {
        if (endTimestamp <= startTimestamp) {
            throw new IllegalArgumentException("End timestamp " + endTimestamp
                    + " does not follow start timestamp " + startTimestamp);
        }
        final Resource justification = makeAIFJustification(model, docId, InterchangeOntology.AudioJustification,
                system, confidence, uri);

        justification.addProperty(InterchangeOntology.startTimestamp,
                model.createTypedLiteral(startTimestamp));
        justification.addProperty(InterchangeOntology.endTimestamp,
                model.createTypedLiteral(endTimestamp));

        return justification;
    }

    /**
     * Mark something as being justified by a particular audio document.
     *
     * @param model          The underlying RDF model for the operation
     * @param toMarkOn       A Resource to be marked by the specified audio document
     * @param docId          A string containing the document element (child) ID of the source of the justification
     * @param startTimestamp A timestamp within the audio document where the justification starts
     * @param endTimestamp   A timestamp within the audio document where the justification ends
     * @param system         The system object for the system which made this justification
     * @param confidence     The confidence with which to mark the justification
     * @return The created audio justification resource
     */
    public static Resource markAudioJustification(Model model, Resource toMarkOn, String docId,
                                                  Double startTimestamp, Double endTimestamp,
                                                  Resource system, Double confidence) {
        return markAudioJustification(model, ImmutableSet.of(toMarkOn), docId,
                startTimestamp, endTimestamp, system, confidence, null);
    }

    /**
     * Mark something as being justified by a particular audio document.
     *
     * @param model          The underlying RDF model for the operation
     * @param toMarkOn       A Resource to be marked by the specified audio document
     * @param docId          A string containing the document element (child) ID of the source of the justification
     * @param startTimestamp A timestamp within the audio document where the justification starts
     * @param endTimestamp   A timestamp within the audio document where the justification ends
     * @param system         The system object for the system which made this justification
     * @param confidence     The confidence with which to mark the justification
     * @param uri            A String uri representation of the justification
     * @return The created audio justification resource
     */
    public static Resource markAudioJustification(Model model, Resource toMarkOn, String docId,
                                                  Double startTimestamp, Double endTimestamp,
                                                  Resource system, Double confidence, String uri) {
        return markAudioJustification(model, ImmutableSet.of(toMarkOn), docId,
                startTimestamp, endTimestamp, system, confidence, uri);
    }

    /**
     * Mark multiple things as being justified by appearing in an audio document.
     *
     * @param model          The underlying RDF model for the operation
     * @param toMarkOn       A Collection of Resources to be marked by the specified audio document
     * @param docId          A string containing the document element (child) ID of the source of the justification
     * @param startTimestamp A timestamp within the audio document where the justification starts
     * @param endTimestamp   A timestamp within the audio document where the justification ends
     * @param system         The system object for the system which made this justification
     * @param confidence     The confidence with which to mark the justification
     * @return The created audio justification resource
     */
    public static Resource markAudioJustification(Model model, Collection<Resource> toMarkOn, String docId,
                                                  Double startTimestamp, Double endTimestamp,
                                                  Resource system, Double confidence) {
        return markAudioJustification(model, toMarkOn, docId, startTimestamp, endTimestamp, system, confidence, null);
    }

    /**
     * Mark multiple things as being justified by appearing in an audio document.
     *
     * @param model          The underlying RDF model for the operation
     * @param toMarkOn       A Collection of Resources to be marked by the specified audio document
     * @param docId          A string containing the document element (child) ID of the source of the justification
     * @param startTimestamp A timestamp within the audio document where the justification starts
     * @param endTimestamp   A timestamp within the audio document where the justification ends
     * @param system         The system object for the system which made this justification
     * @param confidence     The confidence with which to mark the justification
     * @param uri            A String uri representation of the justification
     * @return The created audio justification resource
     */
    public static Resource markAudioJustification(Model model, Collection<Resource> toMarkOn, String docId,
                                                  Double startTimestamp, Double endTimestamp,
                                                  Resource system, Double confidence, String uri) {
        final Resource justification = makeAudioJustification(model, docId, startTimestamp, endTimestamp, system, confidence, uri);
        markJustification(toMarkOn, justification);
        return justification;
    }

    /**
     * Make an video justification.
     *
     * @param model          The underlying RDF model for the operation
     * @param docId          A string containing the document element (child) ID of the source of the justification
     * @param startTimestamp A timestamp within the video document where the justification starts
     * @param endTimestamp   A timestamp within the video document where the justification ends
     * @param channel        The channel of the video that the mention appears in. See: InterchangeOntology.VideoJustificationChannel
     * @param system         The system object for the system which made this justification
     * @param confidence     The confidence with which to mark the justification
     * @param uri            A String uri representation of the justification
     * @return The created video justification resource
     */
    public static Resource makeVideoJustification(Model model, String docId, Double startTimestamp, Double endTimestamp,
                                                  Resource channel, Resource system, Double confidence, String uri) {
        if (endTimestamp <= startTimestamp) {
            throw new IllegalArgumentException("End timestamp " + endTimestamp
                    + " does not follow start timestamp " + startTimestamp);
        }
        final Resource justification = makeAIFJustification(model, docId, InterchangeOntology.VideoJustification,
                system, confidence, uri);

        justification.addProperty(InterchangeOntology.startTimestamp, model.createTypedLiteral(startTimestamp));
        justification.addProperty(InterchangeOntology.endTimestamp, model.createTypedLiteral(endTimestamp));
        justification.addProperty(InterchangeOntology.channel, channel);

        return justification;
    }
    /**
     * Make an video justification.
     *
     * @param model          The underlying RDF model for the operation
     * @param docId          A string containing the document element (child) ID of the source of the justification
     * @param startTimestamp A timestamp within the video document where the justification starts
     * @param endTimestamp   A timestamp within the video document where the justification ends
     * @param channel        The channel of the video that the mention appears in. See: InterchangeOntology.VideoJustificationChannel
     * @param system         The system object for the system which made this justification
     * @param confidence     The confidence with which to mark the justification
     * @return The created video justification resource
     */
    public static Resource makeVideoJustification(Model model, String docId, Double startTimestamp, Double endTimestamp,
                                                  Resource channel, Resource system, Double confidence) {
        return makeVideoJustification(model, docId, startTimestamp, endTimestamp, channel, system, confidence, null);
    }

    /**
     * Combine justifications into single justifiedBy triple with new confidence.
     *
     * @param model          The underlying RDF model for the operation
     * @param toMarkOn       A Collection of Resources to be marked by the specified justifications
     * @param justifications A Collection of Resources that justify the resources to be marked
     * @param system         The system object for the system which made these justifications
     * @param confidence     The confidence with which to mark each justification
     * @return The created compound justification resource
     */
    public static Resource markCompoundJustification(Model model, Collection<Resource> toMarkOn,
                                                     Collection<Resource> justifications,
                                                     Resource system, Double confidence) {
        final Resource compoundJustification = makeAIFResource(model, null,
                InterchangeOntology.CompoundJustification, system);
        markConfidence(model, compoundJustification, confidence, system);
        justifications.forEach(j -> compoundJustification.addProperty(InterchangeOntology.containedJustification, j));
        markJustification(toMarkOn, compoundJustification);
        return compoundJustification;
    }

    /**
     * Mark a confidence value on a resource.
     *
     * @param model      The underlying RDF model for the operation
     * @param toMarkOn   The Resource to mark with the specified confidence
     * @param confidence The confidence with which to mark the resource
     * @param system     The system object for the system which marked this confidence
     */
    public static void markConfidence(Model model, Resource toMarkOn, Double confidence, Resource system) {
        Resource confidenceBlankNode = model.createResource();
        confidenceBlankNode.addProperty(RDF.type, InterchangeOntology.Confidence);
        confidenceBlankNode.addProperty(InterchangeOntology.confidenceValue, model.createTypedLiteral(confidence));
        markSystem(confidenceBlankNode, system);
        toMarkOn.addProperty(InterchangeOntology.confidence, confidenceBlankNode);
    }

    /**
     * Mark a semantic attribute value on a resource.
     *
     * @param toMarkOn   The Resource to mark with the specified confidence
     * @param attribute The semantic attribute with which to mark the resource
     */
    public static void markAttribute(Resource toMarkOn, Resource attribute) {
        toMarkOn.addProperty(InterchangeOntology.attributes, attribute);
    }

    /**
     * Mark the given resources as mutually exclusive.
     * <p>
     * This is a special case of [markAsMutuallyExclusive] where the alternatives are each single edges,
     * so we simply wrap each edge in a collection and pass to markAsMutuallyExclusive.
     *
     * @param model              The underlying RDF model for the operation
     * @param alternatives       A map from alternate edges to the confidence associated with each alternative
     * @param system             The system object for the system which marked as mutually exclusive
     * @param noneOfTheAboveProb If non-null, the given confidence will be applied to the "none of the above" option.
     * @return The created mutual exclusion assertion resource
     */
    public static Resource markEdgesAsMutuallyExclusive(Model model, ImmutableMap<Resource, Double> alternatives,
                                                        Resource system, Double noneOfTheAboveProb) {

        HashMap<Collection<Resource>, Double> newAltMap = new HashMap<>();
        alternatives.keySet().forEach(edge ->
                newAltMap.put(ImmutableSet.of(edge), alternatives.get(edge)));
        return markAsMutuallyExclusive(model, newAltMap, system, noneOfTheAboveProb);
    }

    /**
     * Mark the given resources as mutually exclusive.
     *
     * @param model              The underlying RDF model for the operation
     * @param alternatives       A map from the collection of edges which form a sub-graph for an alternative to the confidence associated with an alternative
     * @param system             The system object for the system which marked as mutually exclusive
     * @param noneOfTheAboveProb If non-null, the given confidence will be applied to the "none of the above" option
     * @return The created mutual exclusion assertion resource
     */
    public static Resource markAsMutuallyExclusive(Model model, Map<Collection<Resource>, Double> alternatives,
                                                   Resource system, Double noneOfTheAboveProb) {
        if (alternatives.size() < 2) {
            throw new IllegalArgumentException("Must have at least two mutually exclusive " +
                    "things when making a mutual exclusion constraint, but got " + alternatives.size());
        }
        final Resource mutualExclusionAssertion =
                makeAIFResource(model, null, InterchangeOntology.MutualExclusion, system);

        // Iterate through each subgraph (collection of edges)
        for (Collection<Resource> edges : alternatives.keySet()) {
            final Resource alternative = model.createResource();
            alternative.addProperty(RDF.type, InterchangeOntology.MutualExclusionAlternative);
            final Resource alternativeGraph = model.createResource();
            alternativeGraph.addProperty(RDF.type, InterchangeOntology.Subgraph);

            for (Resource edge : edges) {
                alternativeGraph.addProperty(InterchangeOntology.subgraphContains, edge);
            }

            alternative.addProperty(InterchangeOntology.alternativeGraph, alternativeGraph);
            markConfidence(model, alternative, alternatives.get(edges), system);
            mutualExclusionAssertion.addProperty(InterchangeOntology.alternative, alternative);
        }

        if (noneOfTheAboveProb != null) {
            mutualExclusionAssertion.addProperty(InterchangeOntology.noneOfTheAbove,
                    model.createTypedLiteral(noneOfTheAboveProb));
        }

        return mutualExclusionAssertion;
    }

    /**
     * Add {@code handle} to resource.
     *
     * @param toMark an resource to add handle to
     * @param handle a simple string description/reference of real-world object
     * @return
     */
    public static Resource markHandle(Resource toMark, @Nullable String handle) {
        return handle != null ? toMark.addProperty(InterchangeOntology.handle, handle) : toMark;
    }

    /**
     * Create a "same-as" cluster.
     * <p>
     * A same-as cluster is used to represent multiple entities which might be the same, but we
     * aren't sure. (If we were sure, they would just be a single node).
     * <p>
     * Every cluster requires a [prototype] - an entity, event, or relation that we are <b>certain</b> is in the
     * cluster. This also automatically adds a membership relation with the prototype with confidence 1.0.
     *
     * @param model      The underlying RDF model for the operation
     * @param clusterUri A unique String URI for the cluster
     * @param prototype  an entity, event, or relation that we are certain is in the cluster
     * @param system     The system object for the system which created the specified cluster
     * @return The created cluster resource
     */
    public static Resource makeClusterWithPrototype(Model model, String clusterUri, Resource prototype,
                                                    Resource system) {
        return makeClusterWithPrototype(model, clusterUri, prototype, null, system);
    }

    /**
     * Create a "same-as" cluster.
     * <p>
     * A same-as cluster is used to represent multiple entities which might be the same, but we
     * aren't sure. (If we were sure, they would just be a single node).
     * <p>
     * Every cluster requires a [prototype] - an entity, event, or relation that we are <b>certain</b> is in the
     * cluster. This also automatically adds a membership relation with the prototype with confidence 1.0.
     *
     * @param model      The underlying RDF model for the operation
     * @param clusterUri A unique String URI for the cluster
     * @param prototype  an entity, event, or relation that we are certain is in the cluster
     * @param handle     a string describing the cluster
     * @param system     The system object for the system which created the specified cluster
     * @return The created cluster resource
     */
    public static Resource makeClusterWithPrototype(Model model, String clusterUri, Resource prototype,
                                                    @Nullable String handle, Resource system) {
        return markHandle(makeClusterWithPrototype(model, clusterUri, prototype, true, system), handle);
    }

    /**
     * Create a "same-as" cluster.
     * <p>
     * A same-as cluster is used to represent multiple entities which might be the same, but we
     * aren't sure. (If we were sure, they would just be a single node).
     * <p>
     * Every cluster requires a [prototype] - an entity, event, or relation that we are <b>certain</b> is in the
     * cluster. This also conditionally adds a membership relation with the prototype with confidence 1.0.
     *
     * @param model      The underlying RDF model for the operation
     * @param clusterUri A unique String URI for the cluster
     * @param prototype  an entity, event, or relation that we are certain is in the cluster
     * @param isMember   indicate whether {@code prototype} should be added as a member as well
     * @param system     The system object for the system which created the specified cluster
     * @return The created cluster resource
     */
    public static Resource makeClusterWithPrototype(Model model, String clusterUri, Resource prototype, boolean isMember,
                                                    Resource system) {
        final Resource cluster = makeAIFResource(model, clusterUri, InterchangeOntology.SameAsCluster, system);
        cluster.addProperty(InterchangeOntology.prototype, prototype);
        if (isMember) {
            markAsPossibleClusterMember(model, prototype, cluster, 1.0, system);
        }

        return cluster;
    }

    /**
     * Mark an entity or event as a possible member of a cluster.
     *
     * @param model                 The underlying RDF model for the operation
     * @param possibleClusterMember The entity or event to mark as a possible member of the specified cluster
     * @param cluster               The cluster to associate with the possible cluster member
     * @param confidence            The confidence with which to mark the cluster membership
     * @param system                The system object for the system which marked the specified cluster
     * @return The created cluster membership assertion
     */
    public static Resource markAsPossibleClusterMember(Model model, Resource possibleClusterMember,
                                                       Resource cluster, Double confidence,
                                                       Resource system) {
        return markAsPossibleClusterMember(model, possibleClusterMember, cluster, confidence, system, null);
    }

    /**
     * Mark an entity or event as a possible member of a cluster.
     *
     * @param model                 The underlying RDF model for the operation
     * @param possibleClusterMember The entity or event to mark as a possible member of the specified cluster
     * @param cluster               The cluster to associate with the possible cluster member
     * @param confidence            The confidence with which to mark the cluster membership
     * @param system                The system object for the system which marked the specified cluster
     * @param uri                   A string URI representation of the cluster member
     * @return The created cluster membership assertion
     */
    public static Resource markAsPossibleClusterMember(Model model, Resource possibleClusterMember,
                                                       Resource cluster, Double confidence,
                                                       Resource system, String uri) {
        final Resource clusterMemberAssertion = makeAIFResource(model, uri,
                InterchangeOntology.ClusterMembership, system);
        clusterMemberAssertion.addProperty(InterchangeOntology.cluster, cluster);
        clusterMemberAssertion.addProperty(InterchangeOntology.clusterMember, possibleClusterMember);
        markConfidence(model, clusterMemberAssertion, confidence, system);
        return clusterMemberAssertion;
    }

    /**
     * Create a hypothesis with the specified level of confidence.
     * <p>
     * You can then indicate that some other object depends on this hypothesis using
     * [markDependsOnHypothesis].
     *
     * @param model             The underlying RDF model for the operation
     * @param hypothesisURI     A unique String URI for the hypothesis
     * @param hypothesisContent A set of entities, relations, and arguments that contribute to the hypothesis
     * @param confidence        The confidence with which to make the hypothesis
     * @param system            The system object for the system which made the hypothesis
     * @return The created hypothesis resource
     */
    public static Resource makeHypothesis(Model model, String hypothesisURI, Set<Resource> hypothesisContent,
                                          Double confidence, Resource system) {
        if (hypothesisContent.isEmpty()) {
            throw new IllegalArgumentException("A hypothesis must have content");
        }
        final Resource hypothesis = makeAIFResource(model, hypothesisURI, InterchangeOntology.Hypothesis, system);
        final Resource subgraph = model.createResource();
        subgraph.addProperty(RDF.type, InterchangeOntology.Subgraph);

        for (Resource h : hypothesisContent) {
            subgraph.addProperty(InterchangeOntology.subgraphContains, h);
        }

        hypothesis.addProperty(InterchangeOntology.hypothesisContent, subgraph);

        if (confidence != null) {
            markConfidence(model, hypothesis, confidence, system);
        }

        return hypothesis;
    }

    /**
     * Create a hypothesis.
     * <p>
     * You can then indicate that some other object depends on this hypothesis using
     * [markDependsOnHypothesis].
     *
     * @param model             The underlying RDF model for the operation
     * @param hypothesisURI     A unique String URI for the hypothesis
     * @param hypothesisContent A set of entities, relations, and arguments that contribute to the hypothesis
     * @param system            The system object for the system which made the hypothesis
     * @return The created hypothesis resource
     */
    public static Resource makeHypothesis(Model model, String hypothesisURI, Set<Resource> hypothesisContent,
                                          Resource system) {
        return makeHypothesis(model, hypothesisURI, hypothesisContent, null, system);
    }

    /**
     * Mark [resource] as having the specified [importance] value.
     *
     * @param resource   The Resource to mark with the specified importance
     * @param importance The importance value with which to mark the specified Resource
     */
    public static void markImportance(Resource resource, Double importance) {
        resource.addLiteral(InterchangeOntology.importance, importance);
    }

    /**
     * Mark [resource] as having the specified [informativeJustification] value.
     *
     * @param resource                 The Resource to mark with the specified informative justification
     * @param informativeJustification The justification which will be considered informative
     */
    public static void markInformativeJustification(Resource resource, Resource informativeJustification) {
        resource.addProperty(InterchangeOntology.informativeJustification, informativeJustification);
    }

    /**
     * Mark an argument as depending on a hypothesis.
     *
     * @param depender   the argument that depends on the specified hypothesis
     * @param hypothesis The hypothesis upon which to depend
     */
    public static void markDependsOnHypothesis(Resource depender, Resource hypothesis) {
        depender.addProperty(InterchangeOntology.dependsOnHypothesis, hypothesis);
    }

    /**
     * Mark data as <i>private</i> from JSON data.  Private data should not contain document-level content features.
     * Allowable private data include:
     * <ul>
     * <li>fringe type(s) for the KE;</li>
     * <li>a vectorized representation of the KE, which cannot grow as the number of mentions/justifications for the KE
     * increases, and from which a raw document (or significant portions thereof) cannot be recoverable;</li>
     * <li>the number of documents that justify the KE;</li>
     * <li>time stamps of justification documents; or</li>
     * <li>fringe type(s) for each image or shot, to describe features that are not represented explicitly in the
     * ontology.  For example: Physical.LocatedNear.Inside(Arg1_Type=Person.Soldier, Arg2_Type=Facility.Hospital)</li>
     * </ul>
     * <p>
     * The KE is not allowed to contain any strings from document text except for the strings in the HasName,
     * NumericValue, and TextValue properties.
     *
     * @param model       The underlying RDF model for the operation
     * @param resource    The entity with which to associate private data
     * @param jsonContent Valid JSON content (in key/value pairs) that represents the private data
     * @param system      The system object for the system which marks the private data
     * @return The created private data resource
     */
    public static Resource markPrivateData(Model model, Resource resource, String jsonContent,
                                           Resource system) {
        final Resource privateData = makeAIFResource(model, null, InterchangeOntology.PrivateData, system);
        privateData.addProperty(InterchangeOntology.jsonContent, model.createTypedLiteral(jsonContent));

        resource.addProperty(InterchangeOntology.privateData, privateData);

        return privateData;
    }

    /**
     * Mark data as <i>private</i> from vector data.  Private data should not contain document-level content features.
     * Allowable private data include:
     * <ul>
     * <li>fringe type(s) for the KE;</li>
     * <li>a vectorized representation of the KE, which cannot grow as the number of mentions/justifications for the KE
     * increases, and from which a raw document (or significant portions thereof) cannot be recoverable;</li>
     * <li>the number of documents that justify the KE;</li>
     * <li>time stamps of justification documents; or</li>
     * <li>fringe type(s) for each image or shot, to describe features that are not represented explicitly in the
     * ontology.  For example: Physical.LocatedNear.Inside(Arg1_Type=Person.Soldier, Arg2_Type=Facility.Hospital)</li>
     * </ul>
     * <p>
     * The KE is not allowed to contain any strings from document text except for the strings in the HasName,
     * NumericValue, and TextValue properties.
     *
     * @param model      The underlying RDF model for the operation
     * @param resource   The entity with which to associate private data
     * @param vectorType A String URI describing the type of data
     * @param vectorData A List of numeric data that represents the private data
     * @param system     The system object for the system which marks the private data
     * @return The created private data resource
     * @throws JsonProcessingException if there was an error generating JSON from the specified vector data
     */
    public static Resource markPrivateData(Model model, Resource resource, String vectorType,
                                           List<Double> vectorData, Resource system) throws JsonProcessingException {
        final ObjectMapper mapper = new ObjectMapper();
        final ImmutableMap<String, Object> jsonMap =
                ImmutableMap.of("vector_type", vectorType, "vector_data", vectorData);
        return markPrivateData(model, resource, mapper.writeValueAsString(jsonMap), system);
    }

    /**
     * Link an entity to something in an external KB.
     *
     * @param model        The underlying RDF model for the operation
     * @param toLink       The entity to which to link
     * @param externalKbId A unique String URI of the external KB
     * @param system       The system object for the system which make the link
     * @param confidence   If non-null, the confidence with which to mark the linkage
     * @return The created link assertion resource
     */
    public static Resource linkToExternalKB(Model model, Resource toLink, String externalKbId, Resource system,
                                            Double confidence) {
        final Resource linkAssertion = makeAIFResource(model, null, InterchangeOntology.LinkAssertion, system);
        toLink.addProperty(InterchangeOntology.link, linkAssertion);
        linkAssertion.addProperty(InterchangeOntology.linkTarget, model.createTypedLiteral(externalKbId));
        if (confidence != null) {
            markConfidence(model, linkAssertion, confidence, system);
        }
        return linkAssertion;
    }

    // This inner class contains SPARQL queries used in these utilities.
    private static final class SparqlQueries {
        static final Query TYPE_QUERY = QueryFactory.create(
                ("PREFIX rdf: <" + RDF.uri + ">\n" +
                        "SELECT ?typeAssertion WHERE {\n" +
                        "?typeAssertion a rdf:Statement .\n" +
                        "?typeAssertion rdf:predicate rdf:type .\n" +
                        "?typeAssertion rdf:subject ?typedObject .\n" +
                        "}").replace("\n", System.getProperty("line.separator")));
    }

    /**
     * Retrieve all type assertions from an entity.
     *
     * @param model       The underlying RDF model for the operation
     * @param typedObject The entity from which to retrieve type assertions
     * @return A set of type assertions for the specified entity
     */
    public static ImmutableSet<Resource> getTypeAssertions(Model model, Resource typedObject) {
        final QuerySolutionMap boundVariables = new QuerySolutionMap();
        boundVariables.add("typedObject", typedObject);

        final QueryExecution queryExecution =
                QueryExecutionFactory.create(SparqlQueries.TYPE_QUERY, model, boundVariables);
        final ResultSet results = queryExecution.execSelect();

        HashSet<Resource> matchSet = new HashSet<>();
        while (results.hasNext()) {
            final QuerySolution match = results.nextSolution();
            matchSet.add(match.get("typeAssertion").asResource()); // check for null?
        }

        return ImmutableSet.<Resource>builder()
                .addAll(matchSet)
                .build();
    }

    /**
     * Retrieve all confidence assertions from an entity.
     *
     * @param model             The underlying RDF model for the operation
     * @param confidencedObject The entity from which to retrieve confidence assertions
     * @return A set of type assertions for the specified entity
     */
    public static ImmutableSet<Resource> getConfidenceAssertions(Model model, Resource confidencedObject) {
        NodeIterator iter =
                model.listObjectsOfProperty(confidencedObject, InterchangeOntology.confidence);
        HashSet<Resource> matchSet = new HashSet<>();
        while (iter.hasNext()) {
            matchSet.add(iter.nextNode().asResource());
        }

        return ImmutableSet.<Resource>builder()
                .addAll(matchSet)
                .build();
    }

    /**
     * This inner class encapsulates the LDC representation of time.
     */
    public static final class LDCTimeComponent {
        public enum LDCTimeType {ON, BEFORE, AFTER, UNKNOWN}

        private static final String dateDelimiter = "-";

        private final LDCTimeType type;
        private final String year;
        private final String month;
        private final String day;

        public LDCTimeComponent(LDCTimeType type, String year, String month, String day) {
            this.type = type;
            this.year = year;
            this.month = month;
            this.day = day;
        }

        public Resource makeAIFTimeComponent(Model model) {
            final Resource timeComponent = makeAIFResource(model, null, InterchangeOntology.LDCTimeComponent, null);
            timeComponent.addProperty(InterchangeOntology.timeType, type.toString());
            addLiteral(model, timeComponent, InterchangeOntology.year, year, XSD.gYear);
            addLiteral(model, timeComponent, InterchangeOntology.month, month, XSD.gMonth);
            addLiteral(model, timeComponent, InterchangeOntology.day, day, XSD.gDay);
            return timeComponent;
        }

        private static void addLiteral(Model model, Resource timeComponent, Property property, String value, Resource type) {
            if (value != null) {
                RDFDatatype literalType = NodeFactory.getType(type.getURI());
                timeComponent.addLiteral(property, model.createTypedLiteral(value, literalType));
            }
        }

        /**
         * Create an LDCTimeComponent from a type and a date
         *
         * @param type {@link String} representation of {@link LDCTimeType}
         * @param date {@link String} containing date to be parsed. Expects yyyy-mm-dd where y, m, and d can be replaced with 'X'
         * @return new {@link LDCTimeComponent} object
         */
        public static LDCTimeComponent createTime(String type, String date) {
            if (type.toLowerCase().contains("unk")) {
                return new LDCTimeComponent(LDCTimeComponent.LDCTimeType.UNKNOWN, null, null, null);
            } else if (date.contains(dateDelimiter)) {
                String[] dateParts = date.toLowerCase().split(dateDelimiter);
                for (int i = 0; i < dateParts.length; i++) {
                    if (dateParts[i].contains("x")) {
                        dateParts[i] = null;
                    } else if (i == 1) {
                        dateParts[i] = "--" + dateParts[i];
                    } else if (i == 2) {
                        dateParts[i] = "---" + dateParts[i];
                    }
                }
                String typeCompare = type.toUpperCase();
                for (LDCTimeComponent.LDCTimeType timeType : LDCTimeComponent.LDCTimeType.values()) {
                    if (typeCompare.contains(timeType.toString())) {
                        return new LDCTimeComponent(timeType, dateParts[0], dateParts[1], dateParts[2]);
                    }
                }
            }
            return null;
        }

        @Override
        public String toString() {
            return String.format("(%s, %s, %s, %S)", type, year, month, day);
        }
    }

    /**
     * Add LDC start and end time representation to an Event or Relation
     *
     * @param model  The underlying RDF model for the operation
     * @param toMark The Event or Relation to add the LDC time data to
     * @param start  {@link LDCTimeComponent} containing the start time information
     * @param end    {@link LDCTimeComponent} containing the end time information
     * @param system The system object for the system which marks the time
     * @return
     */
    public static Resource markLDCTime(Model model, Resource toMark, LDCTimeComponent start, LDCTimeComponent end, Resource system) {
        final Resource ldcTime = makeAIFResource(model, null, InterchangeOntology.LDCTime, system);
        if (start != null) {
            ldcTime.addProperty(InterchangeOntology.start, start.makeAIFTimeComponent(model));
        }
        if (end != null) {
            ldcTime.addProperty(InterchangeOntology.end, end.makeAIFTimeComponent(model));
        }

        toMark.addProperty(InterchangeOntology.ldcTime, ldcTime);
        return ldcTime;
    }

    /**
     * Add LDC start and end time ranges representation to an Event or Relation
     *
     * @param model  The underlying RDF model for the operation
     * @param toMark The Event or Relation to add the LDC time data to
     * @param startEarliest  {@link LDCTimeComponent} containing the earliest start time in the range
     * @param startLatest    {@link LDCTimeComponent} containing the latest start time in the range
     * @param endEarliest    {@link LDCTimeComponent} containing the earliest end time in the range
     * @param endLatest      {@link LDCTimeComponent} containing the latest end time in the range
     * @param system The system object for the system which marks the time
     * @return
     */
    public static Resource markLDCTimeRange(Model model, Resource toMark,
                                            LDCTimeComponent startEarliest, LDCTimeComponent startLatest,
                                            LDCTimeComponent endEarliest, LDCTimeComponent endLatest,
                                            Resource system) {
        final Resource ldcTime = makeAIFResource(model, null, InterchangeOntology.LDCTime, system);
        if (endLatest != null) {
            ldcTime.addProperty(InterchangeOntology.end, endLatest.makeAIFTimeComponent(model));
        }
        if (endEarliest != null) {
            ldcTime.addProperty(InterchangeOntology.end, endEarliest.makeAIFTimeComponent(model));
        }
        if (startLatest != null) {
            ldcTime.addProperty(InterchangeOntology.start, startLatest.makeAIFTimeComponent(model));
        }
        if (startEarliest != null) {
            ldcTime.addProperty(InterchangeOntology.start, startEarliest.makeAIFTimeComponent(model));
        }

        toMark.addProperty(InterchangeOntology.ldcTime, ldcTime);
        return ldcTime;
    }

    /**
     * Add LDC start and end time ranges representation to an Event or Relation
     *
     * @param model  The underlying RDF model for the operation
     * @param toMark The Event or Relation to add the LDC time data to
     * @param startEarliest  {@link String} containing the earliest start time in the range
     * @param startLatest    {@link String} containing the latest start time in the range
     * @param endEarliest    {@link String} containing the earliest end time in the range
     * @param endLatest      {@link String} containing the latest end time in the range
     * @param system The system object for the system which marks the time
     * @return
     */
    public static Resource markLDCTimeRange(Model model, Resource toMark,
                                            String startEarliest, String startLatest,
                                            String endEarliest, String endLatest,
                                            Resource system) {
        return markLDCTimeRange(model, toMark,
            LDCTimeComponent.createTime("AFTER", startEarliest),
            LDCTimeComponent.createTime("BEFORE", startLatest),
            LDCTimeComponent.createTime("AFTER", endEarliest),
            LDCTimeComponent.createTime("BEFORE", endLatest), system);
    }

    /**
     * Add LDC start and end time ranges representation to model
     *
     * @param model  The underlying RDF model for the operation
     * @param startEarliest  {@link String} containing the earliest start time in the range
     * @param startLatest    {@link String} containing the latest start time in the range
     * @param endEarliest    {@link String} containing the earliest end time in the range
     * @param endLatest      {@link String} containing the latest end time in the range
     * @param system The system object for the system which marks the time
     * @return
     */
    public static Resource makeLDCTimeRange(Model model,
                                            String startEarliest, String startLatest,
                                            String endEarliest, String endLatest,
                                            Resource system) {
        final Resource ldcTime = makeAIFResource(model, null, InterchangeOntology.LDCTime, system);
        ldcTime.addProperty(InterchangeOntology.end,
            LDCTimeComponent.createTime("BEFORE", endLatest).makeAIFTimeComponent(model));
        ldcTime.addProperty(InterchangeOntology.end,
            LDCTimeComponent.createTime("AFTER", endEarliest).makeAIFTimeComponent(model));
        ldcTime.addProperty(InterchangeOntology.start,
            LDCTimeComponent.createTime("BEFORE", startLatest).makeAIFTimeComponent(model));
        ldcTime.addProperty(InterchangeOntology.start,
            LDCTimeComponent.createTime("AFTER", startEarliest).makeAIFTimeComponent(model));
        return ldcTime;
    }
    
    // Helper function to create an event, relation, justification, etc. in the system.
    static Resource makeAIFResource(@Nonnull Model model, @Nullable String uri, @Nonnull Resource classType, @Nullable Resource system) {
        // Model automatically creates blank node if uri is null
        Resource resource = model.createResource(uri).addProperty(RDF.type, classType);
        if (system != null) {
            markSystem(resource, system);
        }
        return resource;
    }

    /**
     * Add items from {@code collection} to {@code property} of {@code resource}
     *
     * @param <T>        expected to extend {@link RDFNode} or {@link Object}
     * @param resource   {@link Resource} to add property to
     * @param property   {@link Property} to add to {@code resource}
     * @param collection {@link Collection} of objects to add to {@code resource}
     * @return provided {@code resource} for chaining
     */
    static <T> Resource addProperties(Resource resource, Property property, Collection<T> collection) {
        if (collection != null) {
            collection.stream().forEach(toAdd -> addOptionalProperty(resource, property, toAdd));
        }
        return resource;
    }

    static <T> Resource addOptionalProperty(Resource resource, Property property, T object) {
        if (object != null) {
            if (object instanceof RDFNode) {
                resource.addProperty(property, (RDFNode)object);
            } else {
                resource.addProperty(property, object.toString());
            }
        }
        return resource;
    }
}

/**
 * A strategy for generating RDF graph nodes
 */
interface IriGenerator {
    String nextIri();
}

/**
 * A node generation strategy which uses UUIDs appended to a base URI.
 */
final class UuidIriGenerator implements IriGenerator {
    private String baseUri;

    /**
     * Create a dummy UuidIriGenerator.
     */
    UuidIriGenerator() {
        baseUri = "dummy:uri";
    }

    /**
     * Create a UuidIriGenerator from a base URI.
     *
     * @param baseUri A valid base URI
     */
    UuidIriGenerator(String baseUri) {
        if (baseUri == null || baseUri.isEmpty()) {
            throw new IllegalArgumentException("Base URI cannot be empty");
        }
        if (!baseUri.substring(1).contains(":")) {
            throw new IllegalArgumentException("Base URI must contain a prefix followed by a colon separator");
        }
        if (baseUri.endsWith("/")) {
            throw new IllegalArgumentException("Base URI cannot end in /");
        }
        this.baseUri = baseUri;
    }

    // Inherit Javadoc from interface
    @Override
    public String nextIri() {
        return baseUri + '/' + UUID.randomUUID().toString();
    }
}
