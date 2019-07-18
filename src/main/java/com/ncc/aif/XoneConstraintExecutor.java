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

import java.util.*;

/**
 * Implements handling of sh:xone without SPARQL
 *
 * @author Edward Curley
 */
public class XoneConstraintExecutor implements ConstraintExecutor {

    // TODO: remove this and Result class as they are only for debugging
    static final ThreadLocal<Boolean> isXone = ThreadLocal.withInitial(() -> Boolean.FALSE);
    private static class Result {
        private boolean hasShape;
        private boolean classRun;

        private Result(boolean hasShape, boolean classRun) {
            this.hasShape = hasShape;
            this.classRun = classRun;
        }
    }

    @Override
    public void executeConstraint(Constraint constraint, ValidationEngine engine, List<RDFNode> focusNodes) {
        RDFNode shapeList = constraint.getParameterValue();
        Resource path = constraint.getShapeResource().getPath();
        Set<Resource> shapes = getMembersOfResourceList(shapeList);
        for(RDFNode focusNode : focusNodes) {
            engine.checkCanceled();
            for(RDFNode valueNode : engine.getValueNodes(constraint, focusNode)) {
                Map<Resource, Result> values = new HashMap<>();
                int count = 0;
                for (Resource shape : shapes) {
                    boolean hasShape = hasShape(valueNode, shape, engine);
                    values.put(shape, new Result(hasShape, ClassConstraintExecutor.hasRun.get()));

                    if (hasShape) {
                        count++;
                    }
                }
                if (count != 1) {
                    // TODO: remove debug output
                    StringBuilder builder = line(new StringBuilder(), Thread.currentThread().getName(), focusNode, valueNode);
                    values.forEach((shape, result) -> line(builder,
                            Thread.currentThread().getName(),
                            shape.getPropertyResourceValue(SH.class_).getLocalName(),
                            shape,
                            (result.hasShape ? 1 : 0) + "-" + (hasShape(valueNode, shape, engine) ? 1 : 0),
                            result.classRun));
                    System.out.print(builder);

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

    //TODO: remove this. Debugging only
    private static StringBuilder line(StringBuilder builder, Object... objects) {
        for (Object object : objects) {
            builder.append(object.toString()).append(" ");
        }
        builder.setLength(builder.length() - 1);
        return builder.append("\n");
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

    //TODO: remove this and replace with HasShape.hasShape()
    private static boolean hasShape(RDFNode toTest, Resource shape, ValidationEngine engine) {
        isXone.set(Boolean.TRUE);
        ClassConstraintExecutor.hasRun.set(Boolean.FALSE);
        boolean hasShape = ThreadSafeHasShapeFunction.hasShape(toTest, shape, engine);
        isXone.set(Boolean.FALSE);
        return hasShape;
    }
}
