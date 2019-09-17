package com.ncc.aif;

import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;
import org.topbraid.shacl.arq.SHACLPaths;
import org.topbraid.shacl.engine.Constraint;
import org.topbraid.shacl.validation.ConstraintExecutor;
import org.topbraid.shacl.validation.ValidationEngine;
import org.topbraid.shacl.vocabulary.SH;

import java.util.Collection;
import java.util.List;

/**
 * Implements handling of sh:class without SPARQL
 *
 * @author Edward Curley
 */
public class ClassConstraintExecutor implements ConstraintExecutor {

    @Override
    public void executeConstraint(Constraint constraint, ValidationEngine engine, Collection<RDFNode> focusNodes) {
        RDFNode classType = constraint.getParameterValue();
        for(RDFNode focusNode : focusNodes) {
            engine.checkCanceled();
            for(RDFNode valueNode : engine.getValueNodes(constraint, focusNode)) {
                if (!valueNode.isResource() || !hasClass(valueNode.asResource().getPropertyResourceValue(RDF.type), classType)) {
                    Resource result = engine.createResult(SH.ValidationResult, constraint, focusNode);
                    result.addProperty(SH.value, valueNode);
                    Resource path = constraint.getShapeResource().getPath();
                    if (path != null) {
                        result.addProperty(SH.resultPath, SHACLPaths.clonePath(path, result.getModel()));
                    }
                    if (classType instanceof Resource && ((Resource) classType).hasProperty(SH.message)) {
                        for (Statement s : ((Resource) classType).listProperties(SH.message).toList()) {
                            result.addProperty(SH.resultMessage, s.getObject());
                        }
                    } else if (constraint.getShapeResource().hasProperty(SH.message)) {
                        for (Statement s : constraint.getShapeResource().listProperties(SH.message).toList()) {
                            result.addProperty(SH.resultMessage, s.getObject());
                        }
                    } else {
                        result.addProperty(SH.resultMessage, "Value does not have class " + classType.asResource().getLocalName());
                    }
                }
            }
        }
    }

    private static boolean hasClass(RDFNode current, RDFNode classType) {
        if (current == null || classType == null) {
            return false;
        } else if (current.equals(classType)) {
            return true;
        } else if (current.isResource()) {
            List<RDFNode> nodes = current.asResource()
                    .listProperties(RDFS.subClassOf)
                    .mapWith(Statement::getObject)
                    .toList();
            for (RDFNode next : nodes) {
                if (hasClass(next, classType)) {
                    return true;
                }
            }
            return false;
        } else {
            // current is literal
            return false;
        }
    }
}
