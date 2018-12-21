package com.ncc.aif;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;

public final class AidaDomainOntologiesCommon {
    public static final String NAMESPACE = "https://tac.nist.gov/tracks/SM-KBP/2018/ontologies/AidaDomainOntologiesCommon#";

    private static AidaDomainOntologiesCommon INSTANCE;
    private Resource canHaveName = ResourceFactory.createResource(NAMESPACE + "CanHaveName");
    private Resource canHaveTextValue = ResourceFactory.createResource(NAMESPACE + "CanHaveTextValue");
    private Resource canHaveNumericValue = ResourceFactory.createResource(NAMESPACE + "CanHaveNumericValue");

    private AidaDomainOntologiesCommon() {
    }

    public static AidaDomainOntologiesCommon getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new AidaDomainOntologiesCommon();
        }

        return INSTANCE;
    }

    public Resource getCanHaveName() {
        return canHaveName;
    }

    public void setCanHaveName(Resource canHaveName) {
        this.canHaveName = canHaveName;
    }

    public Resource getCanHaveTextValue() {
        return canHaveTextValue;
    }

    public void setCanHaveTextValue(Resource canHaveTextValue) {
        this.canHaveTextValue = canHaveTextValue;
    }

    public Resource getCanHaveNumericValue() {
        return canHaveNumericValue;
    }

    public void setCanHaveNumericValue(Resource canHaveNumericValue) {
        this.canHaveNumericValue = canHaveNumericValue;
    }

}