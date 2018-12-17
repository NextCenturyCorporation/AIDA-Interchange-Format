package com.ncc.gaia;

import org.apache.jena.rdf.model.Resource;

public class ResourcePair {
    Resource resource1;
    Resource resource2;

    public ResourcePair(Resource res1, Resource res2) {
        resource1 = res1;
        resource2 = res2;
    }

    public Resource getResource1() {
        return resource1;
    }

    public void setResource1(Resource resource1) {
        this.resource1 = resource1;
    }

    public Resource getResource2() {
        return resource2;
    }

    public void setResource2(Resource resource2) {
        this.resource2 = resource2;
    }
}