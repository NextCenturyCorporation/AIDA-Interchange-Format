package com.ncc.aif;

import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.topbraid.shacl.arq.SHACLPaths;
import org.topbraid.shacl.engine.Constraint;
import org.topbraid.shacl.validation.ConstraintExecutor;
import org.topbraid.shacl.validation.ValidationEngine;
import org.topbraid.shacl.vocabulary.SH;

import java.util.HashSet;
import java.util.Collection;
import java.util.Set;

/**
 * Implements handling of sh:xone without SPARQL
 *
 * @author Edward Curley
 */
public class XoneConstraintExecutor implements ConstraintExecutor {

    @Override
    public void executeConstraint(Constraint constraint, ValidationEngine engine, Collection<RDFNode> focusNodes) {
        RDFNode shapeList = constraint.getParameterValue();
        Resource path = constraint.getShapeResource().getPath();
        Set<Resource> shapes = getMembersOfResourceList(shapeList);
        for(RDFNode focusNode : focusNodes) {
            engine.checkCanceled();
            for(RDFNode valueNode : engine.getValueNodes(constraint, focusNode)) {
                int count = 0;
                for (Resource shape : shapes) {
                    if (ThreadSafeHasShapeFunction.hasShape(valueNode, shape, engine)) {
                        count++;
                    }
                }
                if (count != 1) {
                    Resource result = engine.createResult(SH.ValidationResult, constraint, focusNode);
                    result.addProperty(SH.value, valueNode);
                    if (path != null) {
                        result.addProperty(SH.resultPath, SHACLPaths.clonePath(path, result.getModel()));
                    }
                    if (shapeList instanceof Resource && ((Resource) shapeList).hasProperty(SH.message)) {
                        for (Statement s : ((Resource) shapeList).listProperties(SH.message).toList()) {
                            result.addProperty(SH.resultMessage, s.getObject());
                        }
                    } else if (constraint.getShapeResource().hasProperty(SH.message)) {
                        for (Statement s : constraint.getShapeResource().listProperties(SH.message).toList()) {
                            result.addProperty(SH.resultMessage, s.getObject());
                        }
                    } else {
                        String nodeType = path == null ? "Focus" : "Value";
                        result.addProperty(SH.resultMessage, nodeType + " node has " + count + " of the shapes from the 'exactly one' list");
                    }
                }
            }
        }
    }

    private static Set<Resource> getMembersOfResourceList(RDFNode list) {
        if (list == null || !list.isResource()) {
            return new HashSet<>();
        }
        Resource listResource = list.asResource();
        Set<Resource> nodeList = getMembersOfResourceList(listResource.getPropertyResourceValue(RDF.rest));
        Resource first = listResource.getPropertyResourceValue(RDF.first);
        if (first != null) {
            nodeList.add(first);
        }
        return nodeList;
    }
}
