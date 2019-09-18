package com.ncc.aif;

import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.sparql.core.DatasetImpl;
import org.apache.jena.sparql.expr.ExprEvalException;
import org.apache.jena.sparql.expr.NodeValue;
import org.apache.jena.sparql.function.FunctionEnv;
import org.apache.jena.vocabulary.RDF;
import org.topbraid.jenax.util.JenaDatatypes;
import org.topbraid.shacl.arq.functions.HasShapeFunction;
import org.topbraid.shacl.engine.ShapesGraph;
import org.topbraid.shacl.util.FailureLog;
import org.topbraid.shacl.util.RecursionGuard;
import org.topbraid.shacl.validation.DefaultShapesGraphProvider;
import org.topbraid.shacl.validation.ValidationEngine;
import org.topbraid.shacl.validation.ValidationEngineFactory;
import org.topbraid.shacl.vocabulary.DASH;
import org.topbraid.shacl.vocabulary.SH;

import java.net.URI;
import java.util.Collections;

/**
 * A thread-safe implementation of the tosh:hasShape function. Included most of the code from
 * {@link HasShapeFunction} (with minor changes) due to updating a private method
 *
 * @author Edward Curley
 */
public class ThreadSafeHasShapeFunction extends HasShapeFunction {
    private static ThreadLocal<Boolean> recursionIsErrorFlag = new ThreadLocal<>();

    @Override
    protected NodeValue exec(Node focusNode, Node shapeNode, Node recursionIsError, FunctionEnv env) {
        Boolean oldFlag = recursionIsErrorFlag.get();
        if (JenaDatatypes.TRUE.asNode().equals(recursionIsError)) {
            recursionIsErrorFlag.set(true);
        }
        try {
            if (RecursionGuard.start(focusNode, shapeNode)) {
                RecursionGuard.end(focusNode, shapeNode);
                if (JenaDatatypes.TRUE.asNode().equals(recursionIsError) || (oldFlag != null && oldFlag)) {
                    String message = "Unsupported recursion";
                    Model resultsModel = getResultsModel();
                    Resource failure = resultsModel.createResource(DASH.FailureResult);
                    failure.addProperty(SH.resultMessage, message);
                    failure.addProperty(SH.focusNode, resultsModel.asRDFNode(focusNode));
                    failure.addProperty(SH.sourceShape, resultsModel.asRDFNode(shapeNode));
                    FailureLog.get().logFailure(message);
                    throw new ExprEvalException("Unsupported recursion");
                } else {
                    return NodeValue.TRUE;
                }
            } else {

                try {
                    Model model = ModelFactory.createModelForGraph(env.getActiveGraph());
                    RDFNode resource = model.asRDFNode(focusNode);
                    Dataset dataset = DatasetImpl.wrap(env.getDataset());
                    Resource shape = (Resource) dataset.getDefaultModel().asRDFNode(shapeNode);
                    return NodeValue.makeBoolean(hasShapeInternal(resource, shape, dataset));
                } finally {
                    RecursionGuard.end(focusNode, shapeNode);
                }
            }
        } finally {
            recursionIsErrorFlag.set(oldFlag);
        }
    }

    private static Model doRun(RDFNode focusNode, Resource shape, Dataset dataset) {
        URI sgURI = getShapesGraphURI();
        ShapesGraph sg = getShapesGraph();
        if (sgURI == null) {
            sgURI = DefaultShapesGraphProvider.get().getDefaultShapesGraphURI(dataset);
            Model shapesModel = dataset.getNamedModel(sgURI.toString());
            sg = new ShapesGraph(shapesModel);
        } else if (sg == null) {
            Model shapesModel = dataset.getNamedModel(sgURI.toString());
            sg = new ShapesGraph(shapesModel);
            setShapesGraph(sg, sgURI);
        }
        ValidationEngine invokingEngine = ValidationEngine.getCurrent();
        ValidationEngine engine = invokingEngine instanceof ThreadedValidationEngine ?
                ThreadedValidationEngine.createValidationEngine(dataset, sgURI, sg) :
                ValidationEngineFactory.get().create(dataset, sgURI, sg, null);
        if (invokingEngine != null) {
            engine.setConfiguration(invokingEngine.getConfiguration());
        }
        return engine.
                validateNodesAgainstShape(Collections.singletonList(focusNode), shape.asNode()).
                getModel();
    }

    private static boolean hasShapeInternal(RDFNode focusNode, Resource shape, Dataset dataset) {
        Model results = doRun(focusNode, shape, dataset);
        if (getResultsModel() != null) {
            getResultsModel().add(results);
        }
        if (results.contains(null, RDF.type, DASH.FailureResult)) {
            throw new ExprEvalException("Propagating failure from nested shapes");
        }

        if (ValidationEngine.getCurrent() != null && ValidationEngine.getCurrent().getConfiguration().getReportDetails()) {
            boolean result = true;
            for (Resource r : results.listSubjectsWithProperty(RDF.type, SH.ValidationResult).toList()) {
                if (!results.contains(null, SH.detail, r)) {
                    result = false;
                    break;
                }
            }
            return result;
        } else {
            return !results.contains(null, RDF.type, SH.ValidationResult);
        }
    }

    static boolean hasShape(RDFNode focusNode, Resource shape, ValidationEngine engine) {
        URI oldShapesGraphURI = HasShapeFunction.getShapesGraphURI();
        ShapesGraph oldShapesGraph = HasShapeFunction.getShapesGraph();
        try {
            setShapesGraph(engine.getShapesGraph(), engine.getShapesGraphURI());
            return ThreadSafeHasShapeFunction.hasShapeInternal(focusNode, shape, engine.getDataset());
        } finally {
            HasShapeFunction.setShapesGraph(oldShapesGraph, oldShapesGraphURI);
        }
    }
}
