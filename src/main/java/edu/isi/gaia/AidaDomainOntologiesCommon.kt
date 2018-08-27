package edu.isi.gaia

import org.apache.jena.rdf.model.ResourceFactory

object AidaDomainOntologiesCommon {
    val NAMESPACE = "https://tac.nist.gov/tracks/SM-KBP/2018/ontologies/AidaDomainOntologiesCommon#"

    val CanHaveName = ResourceFactory.createResource(NAMESPACE + "CanHaveName")!!
    val CanHaveTextValue = ResourceFactory.createResource(NAMESPACE + "CanHaveTextValue")!!
    val CanHaveNumericValue = ResourceFactory.createResource(NAMESPACE + "CanHaveNumericValue")!!
}
