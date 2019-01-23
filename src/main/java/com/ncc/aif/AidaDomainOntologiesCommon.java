package com.ncc.aif;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

public final class AidaDomainOntologiesCommon {
    public static final String NAMESPACE = "https://tac.nist.gov/tracks/SM-KBP/2018/ontologies/AidaDomainOntologiesCommon#";

    public static final Resource canHaveName = ResourceFactory.createResource(NAMESPACE + "CanHaveName");
    public static final Resource canHaveTextValue = ResourceFactory.createResource(NAMESPACE + "CanHaveTextValue");
    public static final Resource canHaveNumericValue = ResourceFactory.createResource(NAMESPACE + "CanHaveNumericValue");
}
