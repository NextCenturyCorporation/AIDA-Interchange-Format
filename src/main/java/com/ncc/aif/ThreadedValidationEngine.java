package com.ncc.aif;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.rdf.model.Resource;
import org.topbraid.jenax.util.ARQFactory;
import org.topbraid.shacl.arq.SHACLFunctions;
import org.topbraid.shacl.engine.Constraint;
import org.topbraid.shacl.engine.Shape;
import org.topbraid.shacl.engine.ShapesGraph;
import org.topbraid.shacl.js.SHACLScriptEngineManager;
import org.topbraid.shacl.util.SHACLUtil;
import org.topbraid.shacl.validation.ValidationEngine;
import org.topbraid.shacl.validation.ValidationEngineConfiguration;
import org.topbraid.shacl.validation.ValidationUtil;

import java.net.URI;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ThreadedValidationEngine extends ValidationEngine {
    private ThreadedValidationEngine(Dataset dataset, URI shapesGraphURI, ShapesGraph shapesGraph) {
        super(dataset, shapesGraphURI, shapesGraph, null);
    }

    public static class ThreadInfo {
        public String threadName;
        public String shapeName;
        public long duration;
        public int size;

        public ThreadInfo(String threadName, String shapeName, long duration, int size) {
            this.threadName = threadName;
            this.shapeName = shapeName;
            this.duration = duration;
            this.size = size;
        }
    }

    @Override
    public Resource validateAll() throws InterruptedException {
        try {
            validateAll(Executors.newFixedThreadPool(4));
        } catch (ExecutionException e) {
            System.err.println("Validation experienced exception");
            e.printStackTrace();
        }
        return super.getReport();
    }


    public Resource validateAll(ExecutorService executor) throws InterruptedException, ExecutionException {
        List<Shape> rootShapes = shapesGraph.getRootShapes();
        if(monitor != null) {
            monitor.beginTask("Validating " + rootShapes.size() + " shapes", rootShapes.size());
        }

        List<Future<ThreadInfo>> futures = new LinkedList<>();
        for(Shape shape : rootShapes) {

            futures.add(executor.submit(() -> {
                long start = System.currentTimeMillis();
                boolean nested = SHACLScriptEngineManager.begin();
                if(monitor != null) {
                    monitor.subTask("Shape: " + getLabelFunction().apply(shape.getShapeResource()));
                }

                List<RDFNode> focusNodes = SHACLUtil.getTargetNodes(shape.getShapeResource(), dataset);
                if(!focusNodes.isEmpty()) {
                    if(!shapesGraph.isIgnored(shape.getShapeResource().asNode())) {
                        for(Constraint constraint : shape.getConstraints()) {
                            validateNodesAgainstConstraint(focusNodes, constraint);
                        }
                    }
                }
                if(monitor != null) {
                    monitor.worked(1);
                }
                SHACLScriptEngineManager.end(nested);
                return new ThreadInfo(
                        Thread.currentThread().getName(),
                        shape.getShapeResource().getLocalName(),
                        System.currentTimeMillis() - start,
                        focusNodes.size());
            }));
        }
        for (Future<ThreadInfo> future : futures) {
            future.get();
        }
        updateConforms();
        return super.getReport();
    }

    public static ThreadedValidationEngine createValidationEngine(Model dataModel, Model shapesModel,
                                                                  ValidationEngineConfiguration configuration) {

        shapesModel = ValidationUtil.ensureToshTriplesExist(shapesModel);

        // Make sure all sh:Functions are registered
        SHACLFunctions.registerFunctions(shapesModel);

        // Create Dataset that contains both the data model and the shapes model
        // (here, using a temporary URI for the shapes graph)
        URI shapesGraphURI = URI.create("urn:x-shacl-shapes-graph:" + UUID.randomUUID().toString());
        Dataset dataset = ARQFactory.get().getDataset(dataModel);
        dataset.addNamedModel(shapesGraphURI.toString(), shapesModel);

        ShapesGraph shapesGraph = new ShapesGraph(shapesModel);

        ThreadedValidationEngine engine = new ThreadedValidationEngine(dataset, shapesGraphURI, shapesGraph);
        engine.setConfiguration(configuration);
        return engine;
    }
}
