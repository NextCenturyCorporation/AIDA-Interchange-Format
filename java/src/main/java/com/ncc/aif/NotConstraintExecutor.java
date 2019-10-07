package com.ncc.aif;

import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.Statement;
import org.topbraid.shacl.arq.SHACLPaths;
import org.topbraid.shacl.engine.Constraint;
import org.topbraid.shacl.validation.ConstraintExecutor;
import org.topbraid.shacl.validation.ValidationEngine;
import org.topbraid.shacl.vocabulary.SH;

import java.util.Collection;

/**
 * Implements handling of sh:not without SPARQL
 *
 * @author Edward Curley
 */
public class NotConstraintExecutor implements ConstraintExecutor {

    @Override
    public void executeConstraint(Constraint constraint, ValidationEngine engine, Collection<RDFNode> focusNodes) {
        RDFNode shape = constraint.getParameterValue();
        Resource path = constraint.getShapeResource().getPath();
        for(RDFNode focusNode : focusNodes) {
            engine.checkCanceled();
            for(RDFNode valueNode : engine.getValueNodes(constraint, focusNode)) {
                if (!shape.isResource() || ThreadSafeHasShapeFunction.hasShape(valueNode, shape.asResource(), engine)) {
                    Resource result = engine.createResult(SH.ValidationResult, constraint, focusNode);
                    result.addProperty(SH.value, valueNode);
                    if (path != null) {
                        result.addProperty(SH.resultPath, SHACLPaths.clonePath(path, result.getModel()));
                    }
                    if (shape instanceof Resource && ((Resource) shape).hasProperty(SH.message)) {
                        for (Statement s : ((Resource) shape).listProperties(SH.message).toList()) {
                            result.addProperty(SH.resultMessage, s.getObject());
                        }
                    } else if (constraint.getShapeResource().hasProperty(SH.message)) {
                        for (Statement s : constraint.getShapeResource().listProperties(SH.message).toList()) {
                            result.addProperty(SH.resultMessage, s.getObject());
                        }
                    } else {
                        String nodeType = path == null ? "Focus" : "Value";
                        result.addProperty(SH.resultMessage, nodeType + " node has the specified 'not' shape");
                    }
                }
            }
        }
    }
}
