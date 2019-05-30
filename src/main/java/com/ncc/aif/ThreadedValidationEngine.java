package com.ncc.aif;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.*;
import org.apache.jena.vocabulary.RDF;
import org.topbraid.jenax.util.ARQFactory;
import org.topbraid.jenax.util.JenaDatatypes;
import org.topbraid.shacl.arq.SHACLFunctions;
import org.topbraid.shacl.engine.Constraint;
import org.topbraid.shacl.engine.Shape;
import org.topbraid.shacl.engine.ShapesGraph;
import org.topbraid.shacl.js.SHACLScriptEngineManager;
import org.topbraid.shacl.util.SHACLUtil;
import org.topbraid.shacl.validation.MaximumNumberViolations;
import org.topbraid.shacl.validation.ValidationEngine;
import org.topbraid.shacl.validation.ValidationEngineConfiguration;
import org.topbraid.shacl.validation.ValidationUtil;
import org.topbraid.shacl.vocabulary.SH;

import java.net.URI;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class ThreadedValidationEngine extends ValidationEngine {
    public static Property SH_ABORTED = ResourceFactory.createProperty(SH.NS, "aborted");

    private List<Future<ValidationMetadata>> validationMetadata = new LinkedList<>();
    private ThreadLocal<Model> threadModel = ThreadLocal.withInitial(ModelFactory::createDefaultModel);
    private ThreadLocal<Integer> threadViolations = ThreadLocal.withInitial(() -> 0);
    private Predicate<RDFNode> focusNodeFilter;

    private ThreadedValidationEngine(Dataset dataset, URI shapesGraphURI, ShapesGraph shapesGraph) {
        super(dataset, shapesGraphURI, shapesGraph, null);
    }

    public static class ValidationMetadata {
        public String threadName;
        public String shapeName;
        public long duration;
        public int targetCount;
        public int filteredTargetCount;
        public Model model;
        public int violations;
        public boolean ignored;

        public ValidationMetadata(String threadName, String shapeName, long duration, int targetCount,
                                  int filteredTargetCount, Model model, int violations, boolean ignored) {
            this.threadName = threadName;
            this.shapeName = shapeName;
            this.duration = duration;
            this.targetCount = targetCount;
            this.filteredTargetCount = filteredTargetCount;
            this.model = model;
            this.violations = violations;
            this.ignored = ignored;
        }

        @Override
        public String toString() {
            return String.join(" ", threadName, shapeName, duration + "ms",
                    filteredTargetCount + "/" + targetCount, "v" + violations, ignored ? "ignored" : "");
        }
    }

    @Override
    public Resource createResult(Resource type, Constraint constraint, RDFNode focusNode) {
        Resource result = threadModel.get().createResource(type);
        result.addProperty(SH.resultSeverity, constraint.getShapeResource().getSeverity());
        result.addProperty(SH.sourceConstraintComponent, constraint.getComponent());
        result.addProperty(SH.sourceShape, constraint.getShapeResource());
        if (focusNode != null) {
            result.addProperty(SH.focusNode, focusNode);
        }

        // check whether this thread has enough violations to trigger exception
        if (constraint.getShapeResource().getSeverity() == SH.Violation) {
            int violations = threadViolations.get() + 1;
            threadViolations.set(violations);
            if (exceedsMaximumNumberViolations(violations)) {
                throw new MaximumNumberViolations(violations);
            }
        }

        return result;
    }

    @Override
    public void setFocusNodeFilter(Predicate<RDFNode> value) {
        super.setFocusNodeFilter(value);
        focusNodeFilter = value;
    }

    private boolean exceedsMaximumNumberViolations(int violations) {
        int errorBatch = getConfiguration().getValidationErrorBatch();
        return errorBatch != -1 && violations >= errorBatch;
    }

    public Resource validateAll(ExecutorService executor) throws InterruptedException, ExecutionException {
        List<Shape> rootShapes = shapesGraph.getRootShapes();
        //TODO: Add monitor support for threaded validator. Experience NPE with AIFProgressMonitor
//        if (monitor != null) {
//            monitor.beginTask("Validating " + rootShapes.size() + " shapes", rootShapes.size());
//        }

        int i = 0;
        for (Shape shape : rootShapes) {
            validationMetadata.add(executor.submit(getTask(shape, i++, executor)));
        }

        // Go through all futures and get md for those that have completed
        Map<String, Model> models = new HashMap<>();
        int violations = 0;
        for (Future<ValidationMetadata> future : validationMetadata) {
            if (!executor.isShutdown() || future.isDone()) {
                ValidationMetadata md = future.get();
                models.computeIfAbsent(md.threadName, key -> md.model);
                //TODO: shutdown if violations exceed maximum
                violations += md.violations;
                if (exceedsMaximumNumberViolations(violations) && !executor.isShutdown()) {
                    executor.shutdownNow();
                }
            }
        }

        Resource report = getReport();
        Model model = report.getModel().setNsPrefixes(dataset.getDefaultModel());
        models.values().forEach(model::add);
        model.listSubjectsWithProperty(RDF.type, SH.ValidationResult)
                .forEachRemaining(result -> report.addProperty(SH.result, result));

        updateConforms();

        if (exceedsMaximumNumberViolations(violations)) {
            report.addProperty(SH_ABORTED, JenaDatatypes.TRUE);
        }

        return report;
    }

    private Callable<ValidationMetadata> getTask(Shape shape, int id, ExecutorService executor) {
        return () -> {
            long start = System.currentTimeMillis();
            boolean nested = SHACLScriptEngineManager.begin();
//            if (monitor != null) {
//                monitor.subTask("Shape " + id + ": " + getLabelFunction().apply(shape.getShapeResource()));
//            }

            int targetCount = 0;
            int filteredCount = 0;
            threadViolations.set(0);
            boolean ignored = shapesGraph.isIgnored(shape.getShapeResource().asNode());
            if (!ignored) {
                List<RDFNode> focusNodes = SHACLUtil.getTargetNodes(shape.getShapeResource(), dataset);
                targetCount = focusNodes.size();

                List<RDFNode> filtered = focusNodeFilter != null ?
                        focusNodes.stream().filter(focusNodeFilter).collect(Collectors.toList()) :
                        focusNodes;
                filteredCount = filtered.size();

                if (!filtered.isEmpty()) {
                    for (Constraint constraint : shape.getConstraints()) {
                        try {
                            validateNodesAgainstConstraint(filtered, constraint);
                        } catch (MaximumNumberViolations e) {
                            if (!executor.isShutdown()) {
                                executor.shutdownNow();
                            }
                        }
                    }
                }
            }
//            if (monitor != null) {
//                monitor.worked(id);
//                if (monitor.isCanceled() && !executor.isShutdown()) {
//                    executor.shutdownNow();
//                }
//            }
            SHACLScriptEngineManager.end(nested);
            return new ValidationMetadata(
                    Thread.currentThread().getName(),
                    shape.getShapeResource().getLocalName(),
                    System.currentTimeMillis() - start,
                    targetCount,
                    filteredCount,
                    threadModel.get(),
                    threadViolations.get(),
                    ignored);
        };
    }

    public List<Future<ValidationMetadata>> getValidationMetadata() {
        return validationMetadata;
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
