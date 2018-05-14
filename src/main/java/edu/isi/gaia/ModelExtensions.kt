package edu.isi.gaia

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource
import kotlin.coroutines.experimental.buildSequence

// the default Jena interface with resource iterators is cumbersom. We use extension methods with
// lazy sequences to make it nicer
fun Model.subjectsWithProperty(property: Property): Sequence<Resource> {
    return buildSequence({
        val nodeIterator = this@subjectsWithProperty.listSubjectsWithProperty(property)
        while (nodeIterator.hasNext()) {
            yield(nodeIterator.nextResource())
        }
    })
}

// the default Jena interface with resource iterators is cumbersom. We use extension methods with
// lazy sequences to make it nicer
fun Model.subjectsWithProperty(property: Property, `object`: Resource): Sequence<Resource> {
    return buildSequence({
        val nodeIterator = this@subjectsWithProperty.listSubjectsWithProperty(property, `object`)
        while (nodeIterator.hasNext()) {
            yield(nodeIterator.nextResource())
        }
    })
}


fun Model.objectsWithProperty(subject: Resource, property: Property): Sequence<RDFNode> {
    return buildSequence({
        val nodeIterator = this@objectsWithProperty.listObjectsOfProperty(subject, property)
        while (nodeIterator.hasNext()) {
            yield(nodeIterator.nextNode())
        }
    })
}