package edu.isi.gaia

import org.apache.jena.rdf.model.ResourceFactory

/**
 * The non-domain-specific portion of the AIDA ontology.
 */
object AidaAnnotationOntology {
    // URI would change from isi.edu to something else if adopted program-wide
    internal val _namespace: String = "http://www.isi.edu/aida/interchangeOntology#"

    // properties
    @JvmField
    val SYSTEM_PROPERTY = ResourceFactory.createProperty(_namespace + "system")!!
    @JvmField
    val CONFIDENCE = ResourceFactory.createProperty(_namespace + "confidence")!!
    @JvmField
    val CONFIDENCE_VALUE = ResourceFactory.createProperty(_namespace + "confidenceValue")!!
    @JvmField
    val JUSTIFIED_BY = ResourceFactory.createProperty(_namespace + "justifiedBy")!!
    @JvmField
    val SOURCE = ResourceFactory.createProperty(_namespace + "source")!!
    @JvmField
    val START_OFFSET = ResourceFactory.createProperty(_namespace + "startOffset")!!
    @JvmField
    val END_OFFSET_INCLUSIVE = ResourceFactory.createProperty(_namespace
            + "endOffsetInclusive")!!
    @JvmField
    val LINK = ResourceFactory.createProperty(_namespace + "link")!!
    @JvmField
    val LINK_TARGET = ResourceFactory.createProperty(_namespace + "linkTarget")!!
    // realis is currently disabled because it probably won't be used in AIDA
    //val REALIS = ResourceFactory.createProperty(_namespace + "realis")!!
    //val REALIS_VALUE = ResourceFactory.createProperty(_namespace + "realisValue")!!
    @JvmField
    val PROTOTYPE = ResourceFactory.createProperty(_namespace + "prototype")!!
    @JvmField
    val CLUSTER_PROPERTY = ResourceFactory.createProperty(_namespace + "cluster")!!
    @JvmField
    val CLUSTER_MEMBER = ResourceFactory.createProperty(_namespace + "clusterMember")!!

    // classes
    @JvmField
    val SYSTEM_CLASS = ResourceFactory.createResource(_namespace + "System")
    @JvmField
    val ENTITY_CLASS = ResourceFactory.createResource(_namespace + "Entity")!!
    @JvmField
    val EVENT_CLASS = ResourceFactory.createResource(_namespace + "Event")!!
    @JvmField
    val CONFIDENCE_CLASS = ResourceFactory.createResource(_namespace + "Confidence")!!
    @JvmField
    val TEXT_JUSTIFICATION_CLASS = ResourceFactory.createResource(_namespace + "TextProvenance")!!
    @JvmField
    val LINK_ASSERTION_CLASS = ResourceFactory.createResource(_namespace + "LinkAssertion")!!
    @JvmField
    val SAME_AS_CLUSTER_CLASS = ResourceFactory.createResource(_namespace + "SameAsCluster")!!
    @JvmField
    val CLUSTER_MEMBERSHIP_CLASS = ResourceFactory.createResource(_namespace + "ClusterMembership")!!
}