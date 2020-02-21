package com.ncc.aif;

import ch.qos.logback.classic.Logger;
import org.apache.jena.graph.Node;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.*;
import org.apache.jena.sparql.function.FunctionRegistry;
import org.apache.jena.vocabulary.RDF;
import org.topbraid.jenax.util.ARQFactory;
import org.topbraid.jenax.util.JenaDatatypes;
import org.topbraid.shacl.arq.SHACLFunctions;
import org.topbraid.shacl.engine.Constraint;
import org.topbraid.shacl.engine.Shape;
import org.topbraid.shacl.engine.ShapesGraph;
import org.topbraid.shacl.js.SHACLScriptEngineManager;
import org.topbraid.shacl.util.SHACLUtil;
import org.topbraid.shacl.validation.*;
import org.topbraid.shacl.vocabulary.SH;
import org.topbraid.shacl.vocabulary.TOSH;

import java.net.URI;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Extends {@link ValidationEngine} with the {@link #validateAll(ExecutorService)} method. It acts as a
 * {@link ValidationEngine} in all other respects
 *
 * @author Edward Curley
 */
public class ThreadedValidationEngine extends ValidationEngine {
    private static boolean initialized = false;
    private static final Logger logger = (Logger) (org.slf4j.LoggerFactory.getLogger(ThreadedValidationEngine.class));
    private static void initializeSHComponents() {
        if (!initialized) {
            FunctionRegistry.get().put(TOSH.hasShape.getURI(), ThreadSafeHasShapeFunction.class);
            ConstraintExecutors.get().addSpecialExecutor(SH.XoneConstraintComponent,
                    constraint -> new XoneConstraintExecutor());
            ConstraintExecutors.get().addSpecialExecutor(SH.ClassConstraintComponent,
                    constraint -> new ClassConstraintExecutor());
            ConstraintExecutors.get().addSpecialExecutor(SH.NotConstraintComponent,
                    constraint -> new NotConstraintExecutor());
            initialized = true;
        }
    }

    //TODO: come up with better property (topbraid?)
    public static Property SH_ABORTED = ResourceFactory.createProperty(SH.NS, "aborted");

    private List<Future<ShapeTaskMetadata>> validationMetadata = new LinkedList<>();
    private ThreadLocal<Resource> threadReport = ThreadLocal.withInitial(() -> {
        Model model = ModelFactory.createDefaultModel();
        model.setNsPrefixes(dataset.getDefaultModel());
        return model.createResource(SH.ValidationReport);
    });
    private ThreadLocal<Integer> threadViolations = ThreadLocal.withInitial(() -> 0);
    private Predicate<RDFNode> focusNodeFilter;
    private int maxDepth = 0;
    private boolean isStopped = false;
    private long lastDuration = 0;

    private ThreadedValidationEngine(Dataset dataset, URI shapesGraphURI, ShapesGraph shapesGraph) {
        super(dataset, shapesGraphURI, shapesGraph, null);
        initializeSHComponents();
    }

    /**
     * Contains statistics for each shape's validation. Used largely for debugging. Should probably be removed when
     * progress monitoring is handled correctly.
     */
    public static class ShapeTaskMetadata {
        public String shapeName;
        public String threadName;
        public long targetDuration;
        public long totalDuration;
        public int targetCount;
        public int filteredTargetCount;
        public int violations;
        public boolean ignored;
        List<Future<ConstraintTaskMetadata>> constraintFutures;
        SortedSet<ConstraintTaskMetadata> constraintMDs;
        Set<Resource> reports;

        public ShapeTaskMetadata(String shapeName, String threadName) {
            this.shapeName = shapeName;
            this.threadName = threadName;
            this.targetDuration = this.totalDuration = 0;
            this.targetCount = 0;
            this.filteredTargetCount = 0;
            this.violations = 0;
            this.ignored = false;
            constraintFutures = new LinkedList<>();
            constraintMDs = new TreeSet<>(Collections.reverseOrder(Comparator.comparing(md -> md.duration)));
            reports = new HashSet<>();
        }

        public void add(ConstraintTaskMetadata cmd) {
            totalDuration += cmd.duration;
            violations += cmd.violations;
            reports.add(cmd.report);
            constraintMDs.add(cmd);
        }

        @Override
        public String toString() {
            return String.join(" ", shapeName, threadName + "(" + targetDuration + "ms)",
                    totalDuration + "ms", "n=" + filteredTargetCount + "/" + targetCount, "v=" + violations,
                    ignored ? "ignored" : "");
        }
    }

    public static class ConstraintTaskMetadata {
        public static final Set<String> constraintsToRename = new HashSet<>(Arrays.asList("PropertyConstraintComponent", "SPARQLConstraintComponent"));
        public String constraintName;
        public String threadName;
        public long duration;
        public Resource report;
        public int violations;

        public ConstraintTaskMetadata(String threadName, String constraintName, long duration, Resource report, int violations) {
            this.threadName = threadName;
            this.constraintName = constraintName;
            this.duration = duration;
            this.report = report;
            this.violations = violations;
        }

        @Override
        public String toString() {
            return String.join(" ", constraintName, threadName + "(" + duration + "ms)", "v=" + violations);
        }

        public static String getName(Constraint constraint) {
            String name = constraint.getComponent().getLocalName();
            if (constraintsToRename.contains(name)) {
                RDFNode parameter = constraint.getParameterValue();
                if (parameter.isURIResource()) {
                    name = parameter.asResource().getLocalName();
                }
            }
            return name;
        }
    }

    @Override
    public Resource createResult(Resource type, Constraint constraint, RDFNode focusNode) {
        Resource report = threadReport.get();
        Resource result = report.getModel().createResource(type);
        report.addProperty(SH.result, result);
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

    public void setMaxDepth(int value) {
        if (value >= 0)
            maxDepth = value;
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

    /**
     * Validates all target nodes against all of their shapes. The provided {@code executor} is used to provide
     * processing for each shape. This allows the user some control over the environment in which each shape is processed.
     *
     * To further narrow down which nodes to validate, use {@link #setFocusNodeFilter(Predicate)}.
     *
     * @param executor {@link ExecutorService} to send jobs to
     * @return an instance of sh:ValidationReport in the results Model
     * @throws InterruptedException when {@link Future#get()} experiences {@link InterruptedException}
     * @throws ExecutionException when {@link Future#get()} experiences {@link ExecutionException}
     */
    public Set<Resource> validateAll(ExecutorService executor) throws InterruptedException, ExecutionException {
        long start = System.currentTimeMillis();
        boolean nested = SHACLScriptEngineManager.begin();

        Set<Resource> invalid = new HashSet<>();
        try {
            List<Shape> rootShapes = shapesGraph.getRootShapes();
            //TODO: Add monitor support for threaded validator. Experience NPE with AIFProgressMonitor
//        if (monitor != null) {
//            monitor.beginTask("Validating " + rootShapes.size() + " shapes", rootShapes.size());
//        }
            logger.debug("Validating {} shapes.", rootShapes.size());
            int i = 0;
            for (Shape shape : rootShapes) {
                validationMetadata.add(executor.submit(getShapeTask(shape, i++, executor)));
            }

            // Go through all futures and get validation metadata for those that have completed
            Set<Resource> reports = new HashSet<>();
            int violations = 0;
            for (Future<ShapeTaskMetadata> shapeFuture : validationMetadata) {
                ShapeTaskMetadata smd = shapeFuture.get();
                for (Future<ConstraintTaskMetadata> constraintFuture : smd.constraintFutures) {
                    smd.add(constraintFuture.get());
                    if (exceedsMaximumNumberViolations(smd.violations)) {
                        isStopped = true;
                    }
                }
                reports.addAll(smd.reports);
                violations += smd.violations;
                if (exceedsMaximumNumberViolations(violations)) {
                    isStopped = true;
                }
            }

            Set<Resource> valid = new HashSet<>();
            for (Resource report : reports) {
                boolean conforms = true;
                StmtIterator it = report.listProperties(SH.result);
                while(it.hasNext()) {
                    Statement s = it.next();
                    if(s.getResource().hasProperty(RDF.type, SH.ValidationResult)) {
                        conforms = false;
                        it.close();
                        break;
                    }
                }
                report.removeAll(SH.conforms);
                report.addProperty(SH.conforms, conforms ? JenaDatatypes.TRUE : JenaDatatypes.FALSE);
                (conforms ? valid : invalid).add(report);
            }

            if (invalid.isEmpty()) {
                lastDuration = System.currentTimeMillis() - start;
                return valid.size() > 1 ? Collections.singleton(valid.iterator().next()) : valid;
            }

            boolean exceededViolations = exceedsMaximumNumberViolations(violations);
            for (Resource report : invalid) {
                if (exceededViolations) {
                    report.addProperty(SH_ABORTED, JenaDatatypes.TRUE);
                }
            }
        } finally {
            SHACLScriptEngineManager.end(nested);
            lastDuration = System.currentTimeMillis() - start;
        }
        return invalid;
    }

    @Override
    public Resource validateNodesAgainstShape(List<RDFNode> focusNodes, Node shape) {
        if(!shapesGraph.isIgnored(shape)) {
            Shape vs;
            synchronized (shapesGraph) {
                vs = shapesGraph.getShape(shape);
            }
            if(!vs.getShapeResource().isDeactivated()) {
                boolean nested = SHACLScriptEngineManager.begin();
                ValidationEngine oldEngine = getCurrent();
                setCurrent(this);
                try {
                    // Make getting constraints thread-safe
                    Iterable<Constraint> constraints;
                    synchronized (vs) {
                        constraints = vs.getConstraints();
                    }
                    for(Constraint constraint : constraints) {
                        validateNodesAgainstConstraint(focusNodes, constraint);
                    }
                }
                finally {
                    setCurrent(oldEngine);
                    SHACLScriptEngineManager.end(nested);
                }
            }
        }
        return getReport();
    }

    @Override
    public Resource getReport() {
        return threadReport.get();
    }

    private Callable<ShapeTaskMetadata> getShapeTask(Shape shape, int id, ExecutorService executor) {
        return () -> {
            long start = System.currentTimeMillis();
            ShapeTaskMetadata smd =
                    new ShapeTaskMetadata(shape.getShapeResource().getLocalName(), Thread.currentThread().getName());
            boolean ignored = isStopped || shapesGraph.isIgnored(shape.getShapeResource().asNode());
            if (!ignored) {
                List<RDFNode> focusNodes = SHACLUtil.getTargetNodes(shape.getShapeResource(), dataset);
                smd.targetCount = focusNodes.size();

                List<RDFNode> filtered = focusNodeFilter != null ?
                        focusNodes.stream().filter(focusNodeFilter).collect(Collectors.toList()) :
                        focusNodes;
                smd.filteredTargetCount = filtered.size();

                if (smd.targetCount > 0) {
                    logger.debug("Collected {} target node(s) ({} after filter) for {}, d={}",
                            smd.targetCount, smd.filteredTargetCount,
                            shape.getShapeResource().getLocalName(), (System.currentTimeMillis() - start));
                }

                if (maxDepth > 0 && smd.filteredTargetCount > maxDepth) {
                    filtered = filtered.subList(0, maxDepth);
                    logger.debug("--> Shallow validation truncating to {} nodes.", maxDepth);
                }

                if (!filtered.isEmpty()) {
                    for (Constraint constraint : shape.getConstraints()) {
                        smd.constraintFutures.add(executor.submit(getConstraintTask(filtered, constraint)));
                    }
                }
            }
            smd.totalDuration = smd.targetDuration = System.currentTimeMillis() - start;
            return smd;
        };
    }

    private Callable<ConstraintTaskMetadata> getConstraintTask(List<RDFNode> focusNodes, Constraint constraint) {
        return () -> {
            long start = System.currentTimeMillis();
            threadViolations.set(0);
            try {
                if (!isStopped) {
                    logger.debug("Validating {} node(s) against {}, r={}", focusNodes.size(), constraint.toString(),
                            constraint.getParameterValue() != null && constraint.getParameterValue().isResource() ?
                                    constraint.getParameterValue().asResource().getLocalName() : "");
                    validateNodesAgainstConstraint(focusNodes, constraint);
                }
            } catch (MaximumNumberViolations e) {
                isStopped = true;
            }

            final long duration = System.currentTimeMillis() - start;
            logger.debug("Completed {}, r={}, d={}", constraint.toString(),
                    constraint.getParameterValue() != null && constraint.getParameterValue().isResource() ?
                            constraint.getParameterValue().asResource().getLocalName() : "", duration);

            return new ConstraintTaskMetadata(
                    Thread.currentThread().getName(),
                    ConstraintTaskMetadata.getName(constraint),
                    duration,
                    threadReport.get(),
                    threadViolations.get());
        };
    }

    public List<Future<ShapeTaskMetadata>> getValidationMetadata() {
        return validationMetadata;
    }

    public long getLastDuration() {
        return lastDuration;
    }

    /**
     * Mimics {@link ValidationUtil#createValidationEngine(Model, Model, ValidationEngineConfiguration)}
     */
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

    public static ThreadedValidationEngine createValidationEngine(Dataset dataset, URI sgURI, ShapesGraph sg) {
        return new ThreadedValidationEngine(dataset, sgURI, sg);
    }
}
