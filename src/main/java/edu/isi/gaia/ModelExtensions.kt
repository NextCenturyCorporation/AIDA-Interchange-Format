package edu.isi.gaia

import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.Property
import org.apache.jena.rdf.model.RDFNode
import org.apache.jena.rdf.model.Resource

// the default Jena interface with resource iterators is cumbersome. We use extension methods with
// lazy sequences to make it nicer
fun Model.subjectsWithProperty(property: Property): Sequence<Resource> {
    return sequence {
        val resIterator = this@subjectsWithProperty.listSubjectsWithProperty(property)
        while (resIterator.hasNext()) {
            yield(resIterator.nextResource())
        }
    }
 }

 fun Model.subjectsWithProperty(property: Property, `object`: Resource): Sequence<Resource> {
    return sequence {
        val resIterator = this@subjectsWithProperty.listSubjectsWithProperty(property, `object`)
        while (resIterator.hasNext()) {
            yield(resIterator.nextResource())
        }
    }
 }

fun Model.objectsWithProperty(subject: Resource, property: Property): Sequence<RDFNode> {
    return sequence {
        val nodeIterator = this@objectsWithProperty.listObjectsOfProperty(subject, property)
        while (nodeIterator.hasNext()) {
            yield(nodeIterator.nextNode())
        }
    }
}
