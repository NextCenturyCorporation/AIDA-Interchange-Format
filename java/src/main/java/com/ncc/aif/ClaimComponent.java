package com.ncc.aif;

import java.util.HashSet;
import java.util.Set;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;

public class ClaimComponent {
    private String name;
    private String identity;
    private Set<String> types = new HashSet<>();
    private String provenance;
    private Resource ke;
    private Resource resource;

    public ClaimComponent setName(String name) {
        this.name = name;
        return this;
    }

    public ClaimComponent setIdentity(String identity) {
        this.identity = identity;
        return this;
    }

    public ClaimComponent setTypes(Set<String> types) {
        this.types = types;
        return this;
    }

    public ClaimComponent setProvenance(String provenance) {
        this.provenance = provenance;
        return this;
    }

    public ClaimComponent setKE(Resource ke) {
        this.ke = ke;
        return this;
    }

    public ClaimComponent addType(String type) {
        if (types == null) {
            types = new HashSet<>();
        }
        types.add(type);
        return this;
    }

    public String getName() {
        return this.name;
    }

    public String getIdentity() {
        return this.identity;
    }

    public Set<String> getTypes() {
        return this.types;
    }

    public String getProvenance() {
        return this.provenance;
    }

    public Resource getKE() {
        return this.ke;
    }

    public Resource getResource() {
        return this.resource;
    }

    public Resource addToModel(Model model, String uri, Resource system) {
        resource = AIFUtils.makeAIFResource(model, uri, InterchangeOntology.ClaimComponent, system)
            .addProperty(InterchangeOntology.componentName, name)
            .addProperty(InterchangeOntology.componentIdentity, identity);
        if (provenance != null) {
            resource.addProperty(InterchangeOntology.componentProvenance, provenance);
        }
        if (ke != null) {
            resource.addProperty(InterchangeOntology.componentKE, ke);
        }
        AIFUtils.addProperties(resource, InterchangeOntology.componentType, types);
        return resource;
    }
}