package com.ncc.aif;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.NodeIterator;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.apache.jena.vocabulary.XSD;

import javax.annotation.Nullable;
import java.util.*;

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
        model.setNsPrefix("aida", AidaAnnotationOntology.NAMESPACE);
        model.setNsPrefix("skos", SKOS.uri);
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
        system.addProperty(RDF.type, AidaAnnotationOntology.SYSTEM_CLASS);
        return system;
    }

    /**
     * Mark a resource as coming from the specified [system].
     *
     * @param toMarkOn The resource to mark as coming from the specified system
     * @param system   The system with which to mark the specified resource
     */
    public static void markSystem(Resource toMarkOn, Resource system) {
        toMarkOn.addProperty(AidaAnnotationOntology.SYSTEM_PROPERTY, system);
    }

    /**
     * Mark [entity] as having the specified [name].
     *
     * @param entity The Resource to mark with the specified name
     * @param name   The String name with which to mark the specified Resource
     */
    public static void markName(Resource entity, String name) {
        entity.addLiteral(AidaAnnotationOntology.NAME_PROPERTY, name);
    }

    /**
     * Mark [entity] as having the specified [textValue].
     *
     * @param entity    The Resource to mark as having the specified text value
     * @param textValue The String text value with which to mark the specified Resource
     */
    public static void markTextValue(Resource entity, String textValue) {
        entity.addLiteral(AidaAnnotationOntology.TEXT_VALUE_PROPERTY, textValue);
    }

    /**
     * Mark [entity] as having the specified [numericValue] as string.
     *
     * @param entity       The Resource to mark as having the specified numeric value
     * @param numericValue A String representation of a numeric value with which to
     *                     mark the specified Resource
     */
    public static void markNumericValueAsString(Resource entity, String numericValue) {
        entity.addLiteral(AidaAnnotationOntology.NUMERIC_VALUE_PROPERTY, numericValue);
    }

    /**
     * Mark [entity] as having the specified [numericValue] as double floating point.
     *
     * @param entity       The Resource to mark as having the specified numeric value
     * @param numericValue A Double representation of a numeric value with which to
     *                     mark the specified Resource
     */
    public static void markNumericValueAsDouble(Resource entity, Double numericValue) {
        entity.addLiteral(AidaAnnotationOntology.NUMERIC_VALUE_PROPERTY, numericValue.doubleValue());
    }

    /**
     * Mark [entity] as having the specified [numericValue] as long integer.
     *
     * @param entity       The Resource to mark as having the specified numeric value
     * @param numericValue A Long representation of a numeric value with which to
     *                     mark the specified Resource
     */
    public static void markNumericValueAsLong(Resource entity, Long numericValue) {
        entity.addLiteral(AidaAnnotationOntology.NUMERIC_VALUE_PROPERTY, numericValue.longValue());
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
        return makeAIFResource(model, entityUri, AidaAnnotationOntology.ENTITY_CLASS, system);
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
        return makeAIFResource(model, relationUri, AidaAnnotationOntology.RELATION_CLASS, system);
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
     */
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
        return makeAIFResource(model, eventUri, AidaAnnotationOntology.EVENT_CLASS, system);
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

        final Resource argAssertion = makeAIFResource(model, uri, RDF.Statement, system);

        argAssertion.addProperty(RDF.subject, eventOrRelation);
        argAssertion.addProperty(RDF.predicate, argumentType);
        argAssertion.addProperty(RDF.object, argumentFiller);
        if (confidence != null) {
            markConfidence(model, argAssertion, confidence, system);
        }
        return argAssertion;
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
        final Resource typeAssertion = model.createResource(typeAssertionUri);
        typeAssertion.addProperty(RDF.type, RDF.Statement);
        typeAssertion.addProperty(RDF.subject, entityOrEventOrRelation);
        typeAssertion.addProperty(RDF.predicate, RDF.type);
        typeAssertion.addProperty(RDF.object, type);
        typeAssertion.addProperty(AidaAnnotationOntology.SYSTEM_PROPERTY, system);
        if (confidence != null) {
            markConfidence(model, typeAssertion, confidence, system);
        }
        return typeAssertion;
    }

    // Helper function to create a justification (text, image, audio, etc.) in the system.
    private static Resource makeAIFJustification(Model model, String docId, Resource classType,
                                                Resource system, Double confidence) {
        final Resource justification = makeAIFResource(model, null, classType, system);
        justification.addProperty(AidaAnnotationOntology.SOURCE, model.createTypedLiteral(docId));
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
        toMarkOn.addProperty(AidaAnnotationOntology.JUSTIFIED_BY, justification);
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
        if (endOffsetInclusive < startOffset) {
            throw new IllegalArgumentException("End offset " + endOffsetInclusive + " precedes start offset " + startOffset);
        }
        if (startOffset < 0) {
            throw new IllegalArgumentException("Start offset must be non-negative but got " + startOffset);
        }

        final Resource justification = makeAIFJustification(model, docId, AidaAnnotationOntology.TEXT_JUSTIFICATION_CLASS,
                system, confidence);
        // the document ID for the justifying source document
        justification.addProperty(AidaAnnotationOntology.START_OFFSET,
                model.createTypedLiteral(startOffset));
        justification.addProperty(AidaAnnotationOntology.END_OFFSET_INCLUSIVE,
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
                endOffsetInclusive, system, confidence);
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
        final Resource justification = makeTextJustification(model, docId, startOffset, endOffsetInclusive, system, confidence);
        markJustification(toMarkOn, justification);
        return justification;
    }

    /**
     * Add a sourceDocument to a pre-existing justification
     *
     * @param justification      A pre-existing justification resource
     * @param sourceDocument     A string containing the source document (parent) ID
     * @return The modified justification resource
     */
    public static Resource addSourceDocumentToJustification(Resource justification, String sourceDocument) {
        justification.addProperty(AidaAnnotationOntology.SOURCE_DOCUMENT, sourceDocument);
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
        boundingBoxResource.addProperty(RDF.type, AidaAnnotationOntology.BOUNDING_BOX_CLASS);
        boundingBoxResource.addProperty(AidaAnnotationOntology.BOUNDING_BOX_UPPER_LEFT_X,
                model.createTypedLiteral(boundingBox.upperLeft.x));
        boundingBoxResource.addProperty(AidaAnnotationOntology.BOUNDING_BOX_UPPER_LEFT_Y,
                model.createTypedLiteral(boundingBox.upperLeft.y));
        boundingBoxResource.addProperty(AidaAnnotationOntology.BOUNDING_BOX_LOWER_RIGHT_X,
                model.createTypedLiteral(boundingBox.lowerRight.x));
        boundingBoxResource.addProperty(AidaAnnotationOntology.BOUNDING_BOX_LOWER_RIGHT_Y,
                model.createTypedLiteral(boundingBox.lowerRight.y));

        toMarkOn.addProperty(AidaAnnotationOntology.BOUNDING_BOX_PROPERTY, boundingBoxResource);

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
        final Resource justification = makeAIFJustification(model, docId, AidaAnnotationOntology.IMAGE_JUSTIFICATION_CLASS,
                system, confidence);
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
        return markImageJustification(model, ImmutableSet.of(toMarkOn), docId, boundingBox, system, confidence);
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
        final Resource justification = makeImageJustification(model, docId, boundingBox, system, confidence);
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
        final Resource justification = makeAIFJustification(model, docId, AidaAnnotationOntology.KEYFRAME_VIDEO_JUSTIFICATION_CLASS,
                system, confidence);
        justification.addProperty(AidaAnnotationOntology.KEY_FRAME, model.createTypedLiteral(keyFrame));
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
                keyFrame, boundingBox, system, confidence);
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
        final Resource justification = makeKeyFrameVideoJustification(model, docId, keyFrame, boundingBox, system, confidence);
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
        final Resource justification = makeAIFJustification(model, docId, AidaAnnotationOntology.SHOT_VIDEO_JUSTIFICATION_CLASS,
                system, confidence);
        justification.addProperty(AidaAnnotationOntology.SHOT, model.createTypedLiteral(shotId));
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
        return markShotVideoJustification(model, ImmutableSet.of(toMarkOn), docId, shotId, system, confidence);
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
        final Resource justification = makeShotVideoJustification(model, docId, shotId, system, confidence);
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
        if (endTimestamp <= startTimestamp) {
            throw new IllegalArgumentException("End timestamp " + endTimestamp
                    + "does not follow start timestamp " + startTimestamp);
        }
        final Resource justification = makeAIFJustification(model, docId, AidaAnnotationOntology.AUDIO_JUSTIFICATION_CLASS,
                system, confidence);

        justification.addProperty(AidaAnnotationOntology.START_TIMESTAMP,
                model.createTypedLiteral(startTimestamp));
        justification.addProperty(AidaAnnotationOntology.END_TIMESTAMP,
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
                startTimestamp, endTimestamp, system, confidence);
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
        final Resource justification = makeAudioJustification(model, docId, startTimestamp, endTimestamp, system, confidence);
        markJustification(toMarkOn, justification);
        return justification;
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
                AidaAnnotationOntology.COMPOUND_JUSTIFICATION_CLASS, system);
        markConfidence(model, compoundJustification, confidence, system);
        justifications.forEach(j -> compoundJustification.addProperty(AidaAnnotationOntology.CONTAINED_JUSTIFICATION, j));
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
        confidenceBlankNode.addProperty(RDF.type, AidaAnnotationOntology.CONFIDENCE_CLASS);
        confidenceBlankNode.addProperty(AidaAnnotationOntology.CONFIDENCE_VALUE, model.createTypedLiteral(confidence));
        markSystem(confidenceBlankNode, system);
        toMarkOn.addProperty(AidaAnnotationOntology.CONFIDENCE, confidenceBlankNode);
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
                makeAIFResource(model, null, AidaAnnotationOntology.MUTUAL_EXCLUSION_CLASS, system);

        // Iterate through each subgraph (collection of edges)
        for (Collection<Resource> edges : alternatives.keySet()) {
            final Resource alternative = model.createResource();
            alternative.addProperty(RDF.type, AidaAnnotationOntology.MUTUAL_EXCLUSION_ALTERNATIVE_CLASS);
            final Resource alternativeGraph = model.createResource();
            alternativeGraph.addProperty(RDF.type, AidaAnnotationOntology.SUBGRAPH_CLASS);

            for (Resource edge : edges) {
                alternativeGraph.addProperty(AidaAnnotationOntology.GRAPH_CONTAINS, edge);
            }

            alternative.addProperty(AidaAnnotationOntology.ALTERNATIVE_GRAPH_PROPERTY, alternativeGraph);
            markConfidence(model, alternative, alternatives.get(edges), system);
            mutualExclusionAssertion.addProperty(AidaAnnotationOntology.ALTERNATIVE_PROPERTY, alternative);
        }

        if (noneOfTheAboveProb != null) {
            mutualExclusionAssertion.addProperty(AidaAnnotationOntology.NONE_OF_THE_ABOVE_PROPERTY,
                    model.createTypedLiteral(noneOfTheAboveProb));
        }

        return mutualExclusionAssertion;
    }

    /**
     * Create a "same-as" cluster.
     * <p>
     * A same-as cluster is used to represent multiple entities which might be the same, but we
     * aren't sure. (If we were sure, they would just be a single node).
     * <p>
     * Every cluster requires a [prototype] - an entity or event that we are <b>certain</b> is in the
     * cluster. This also automatically adds a membership relation with the prototype with confidence 1.0.
     *
     * @param model      The underlying RDF model for the operation
     * @param clusterUri A unique String URI for the cluster
     * @param prototype  an entity or event that we are certain is in the cluster
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
     * Every cluster requires a [prototype] - an entity or event that we are <b>certain</b> is in the
     * cluster. This also automatically adds a membership relation with the prototype with confidence 1.0.
     *
     * @param model      The underlying RDF model for the operation
     * @param clusterUri A unique String URI for the cluster
     * @param prototype  an entity or event that we are certain is in the cluster
     * @param handle     a string describing the cluster
     * @param system     The system object for the system which created the specified cluster
     * @return The created cluster resource
     */
    public static Resource makeClusterWithPrototype(Model model, String clusterUri, Resource prototype,
                                                    @Nullable String handle, Resource system) {
        final Resource cluster = makeAIFResource(model, clusterUri, AidaAnnotationOntology.SAME_AS_CLUSTER_CLASS, system);
        cluster.addProperty(AidaAnnotationOntology.PROTOTYPE, prototype);
        if (handle != null) {
            cluster.addProperty(AidaAnnotationOntology.HANDLE, handle);
        }
        markAsPossibleClusterMember(model, prototype, cluster, 1.0, system);
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
        final Resource clusterMemberAssertion = makeAIFResource(model, null,
                AidaAnnotationOntology.CLUSTER_MEMBERSHIP_CLASS, system);
        clusterMemberAssertion.addProperty(AidaAnnotationOntology.CLUSTER_PROPERTY, cluster);
        clusterMemberAssertion.addProperty(AidaAnnotationOntology.CLUSTER_MEMBER, possibleClusterMember);
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
        final Resource hypothesis = makeAIFResource(model, hypothesisURI, AidaAnnotationOntology.HYPOTHESIS_CLASS, system);
        final Resource subgraph = model.createResource();
        subgraph.addProperty(RDF.type, AidaAnnotationOntology.SUBGRAPH_CLASS);

        for (Resource h : hypothesisContent) {
            subgraph.addProperty(AidaAnnotationOntology.GRAPH_CONTAINS, h);
        }

        hypothesis.addProperty(AidaAnnotationOntology.HYPOTHESIS_CONTENT_PROPERTY, subgraph);

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
     *  Mark [entity] as having the specified [importance] value.
     *
     * @param entity     The Resource to mark with the specified importance
     * @param importance The importance value with which to mark the specified Resource
     */
    public static void markImportance(Resource entity, Integer importance) {
        entity.addLiteral(AidaAnnotationOntology.IMPORTANCE_PROPERTY, importance);
    }

    /**
     * Mark an argument as depending on a hypothesis.
     *
     * @param depender   the argument that depends on the specified hypothesis
     * @param hypothesis The hypothesis upon which to depend
     */
    public static void markDependsOnHypothesis(Resource depender, Resource hypothesis) {
        depender.addProperty(AidaAnnotationOntology.DEPENDS_ON_HYPOTHESIS, hypothesis);
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
     * seedling ontology.  For example: Physical.LocatedNear.Inside(Arg1_Type=Person.Soldier, Arg2_Type=Facility.Hospital)</li>
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
        final Resource privateData = makeAIFResource(model, null, AidaAnnotationOntology.PRIVATE_DATA_CLASS, system);
        privateData.addProperty(AidaAnnotationOntology.JSON_CONTENT_PROPERTY, model.createTypedLiteral(jsonContent));

        resource.addProperty(AidaAnnotationOntology.PRIVATE_DATA_PROPERTY, privateData);

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
     * seedling ontology.  For example: Physical.LocatedNear.Inside(Arg1_Type=Person.Soldier, Arg2_Type=Facility.Hospital)</li>
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
        final Resource linkAssertion = makeAIFResource(model, null, AidaAnnotationOntology.LINK_ASSERTION_CLASS, system);
        toLink.addProperty(AidaAnnotationOntology.LINK, linkAssertion);
        linkAssertion.addProperty(AidaAnnotationOntology.LINK_TARGET, model.createTypedLiteral(externalKbId));
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
                model.listObjectsOfProperty(confidencedObject, AidaAnnotationOntology.CONFIDENCE);
        HashSet<Resource> matchSet = new HashSet<>();
        while (iter.hasNext()) {
            matchSet.add(iter.nextNode().asResource());
        }

        return ImmutableSet.<Resource>builder()
                .addAll(matchSet)
                .build();
    }

    // Helper function to create an event, relation, justification, etc. in the system.
    private static Resource makeAIFResource(Model model, String uri, Resource classType, Resource system) {
        Resource resource = (uri == null ? model.createResource() : model.createResource(uri));
        resource.addProperty(RDF.type, classType);
        markSystem(resource, system);
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
