package com.ncc.aif;

import java.io.PrintStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

import javax.annotation.Nonnull;

import com.google.common.base.Charsets;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.util.FileUtils;
import org.topbraid.jenax.progress.ProgressMonitor;
import org.topbraid.shacl.validation.ValidationEngine;
import org.topbraid.shacl.validation.ValidationEngineConfiguration;
import org.topbraid.shacl.validation.ValidationUtil;
import org.topbraid.shacl.vocabulary.SH;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * An AIF Validator.  These are not instantiated directly; instead invoke {@link #createForDomainOntologySource} statically,
 * specifying a domain ontology, and make calls to the returned validator.
 *
 * @author Ryan Gabbard (USC ISI)
 * @author Converted to Java developed further by Next Century Corporation
 */
public final class ValidateAIF {

    // Enum to track restrictions
    public enum Restriction {
        NONE, NIST, NIST_TA3
    }

    private static final String AIF_ROOT = "com/ncc/aif/";
    private static final String AIDA_SHACL_RESNAME = AIF_ROOT + "aida_ontology.shacl";
    private static final String NIST_SHACL_RESNAME = AIF_ROOT + "restricted_aif.shacl";
    private static final String NIST_CLAIMFRAME_SHACL_RESNAME = AIF_ROOT + "restricted_claimframe_aif.shacl";

    private static final String ONT_ROOT = AIF_ROOT + "ontologies/";
    private static final String INTERCHANGE_RESNAME = ONT_ROOT + "InterchangeOntology";
    private static final String AIDA_DOMAIN_COMMON_RESNAME = ONT_ROOT + "AidaDomainOntologiesCommon";
    private static final String LDC_RESNAME = ONT_ROOT + "LDCOntologyM36";
    private static final String AO_ENTITIES_RESNAME = ONT_ROOT + "EntityOntology";
    private static final String AO_EVENTS_RESNAME = ONT_ROOT + "EventOntology";
    private static final String AO_RELATIONS_RESNAME = ONT_ROOT + "RelationOntology";

    private static Model shaclModel;
    private static Model nistModel;
    private static Model nistClaimModel;
    private static boolean initialized = false;
    private static final Property CONFORMS = ResourceFactory.createProperty("http://www.w3.org/ns/shacl#conforms");

    private static void initializeSHACLModels() {
        if (!initialized) {

            shaclModel = ModelFactory.createDefaultModel();
            loadModel(shaclModel, Resources.asCharSource(Resources.getResource(AIDA_SHACL_RESNAME), Charsets.UTF_8));

            nistModel = ModelFactory.createDefaultModel();
            nistModel.add(shaclModel);
            loadModel(nistModel, Resources.asCharSource(Resources.getResource(NIST_SHACL_RESNAME), Charsets.UTF_8));

            nistClaimModel = ModelFactory.createDefaultModel();
            nistClaimModel.add(nistModel);
            nistClaimModel.add(shaclModel);
            loadModel(nistClaimModel, Resources.asCharSource(Resources.getResource(NIST_CLAIMFRAME_SHACL_RESNAME), Charsets.UTF_8));


            initialized = true;
        }
    }

    private final Model domainModel;
    private final Model restrictionModel;
    private int abortThreshold = -1; // by default, do not abort on SHACL violation
    private boolean debugging = false;
    private int depth = 0; // by default, do not perform shallow validation
    private ProgressMonitor progressMonitor = null; // by default, do not monitor progress
    private ThreadPoolExecutor executor;
    private List<Future<ThreadedValidationEngine.ShapeTaskMetadata>> validationMetadata;
    private long lastDuration;

    private ValidateAIF(Model domainModel, Restriction restriction) {
        this(domainModel, getRestrictionModel(restriction));
    }

    private ValidateAIF(Model domainModel, @Nonnull Model restriction) {
        this.domainModel = domainModel;
        this.restrictionModel = restriction;
    }

    private static Model getRestrictionModel(Restriction restriction) {
        initializeSHACLModels();
        // Apply appropriate SHACL restrictions
        switch (restriction) {
            case NIST:
                return nistModel;
            case NIST_TA3:
                return nistClaimModel;
            case NONE: // fall-through on purpose
            default:
                return shaclModel;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    // Ensure what file name an RDF syntax error occurs in is printed, which
    // doesn't happen by default.
    static void loadModel(Model model, CharSource ontologySource) {
        try {
            model.read(ontologySource.openBufferedStream(), "urn:x-base", FileUtils.langTurtle);
        } catch (Exception exception) { // includes IOException & JenaException
            throw new RuntimeException("While parsing " + ontologySource, exception);
        }
    }

    /**
     * Create an AIF validator for the specified domain ontology source.
     *
     * @param domainOntologySource A domain ontology
     * @return An AIF validator for the specified ontology
     */
    public static ValidateAIF createForDomainOntologySource(String domainOntologySource) {
        return create(Set.of(domainOntologySource), Restriction.NONE);
    }

    /**
     * Create an AIF validator for the LDC ontology.
     *
     * @param restriction Type of restriction (if any) that should be applied during validation
     * @return An AIF validator for the LDC ontology
     */
    public static ValidateAIF createForLDCOntology(Restriction restriction) {
        return create(Set.of(LDC_RESNAME), restriction);
    }

    /**
     * Create an AIF validator for the Program ontology.
     *
     * @param restriction Type of restriction (if any) that should be applied during validation
     * @return An AIF validator for the Program ontology
     */
    public static ValidateAIF createForProgramOntology(Restriction restriction) {
        return create(Set.of(AO_ENTITIES_RESNAME, AO_EVENTS_RESNAME, AO_RELATIONS_RESNAME), restriction);
    }

    /**
     * Create an AIF validator for the DWD.
     *
     * @param restriction Type of restriction (if any) that should be applied during validation
     * @return An AIF validator for the DWD
     */
    public static ValidateAIF createForDWD(Restriction restriction) {
        Set<String> restrictions = new HashSet<>();
        restrictions.add("com/ncc/aif/dwd_aif.shacl");
        switch (restriction) {
            case NIST_TA3:
                restrictions.add(NIST_SHACL_RESNAME);
                restrictions.add(NIST_CLAIMFRAME_SHACL_RESNAME);
            case NIST:
                restrictions.add(NIST_SHACL_RESNAME);
            default:
                // do nothing
        }
        // DWD is domain, but there's no way to validate against it
        Model model = getModelFromSources(getSourcesFromResources(restrictions.stream()));
        return create(Stream.of(), model);
    }

    static Model getModelFromSources(Stream<CharSource> sources) {
        final Model model = ModelFactory.createDefaultModel();
        sources.forEach(source -> loadModel(model, source));
        return model;
    }

    static Stream<CharSource> getSourcesFromResources(Stream<String> resources) {
        return resources.map(resource ->
            Resources.asCharSource(Resources.getResource(resource), Charsets.UTF_8)
        );
    }

    /**
     * Create an AIF validator for specified domain ontologies and requirements.
     *
     * @param restriction           Type of restriction (if any) that should be applied during validation
     * @param domainOntologySources User-supplied domain ontologies as {@link CharSource}
     * @return An AIF validator for the specified ontologies and requirements
     */
    public static ValidateAIF create(Stream<CharSource> domainOntologySources, Restriction restriction) {
        return create(domainOntologySources, getRestrictionModel(restriction));
    }

    /**
     * Create an AIF validator for specified domain ontologies and requirements.
     *
     * @param restriction           Type of restriction (if any) that should be applied during validation
     * @param domainOntologySources User-supplied domain ontologies as {@link String}
     * @return An AIF validator for the specified ontologies and requirements
     */
    public static ValidateAIF create(Set<String> domainOntologySources, Restriction restriction) {
        Stream<CharSource> external = getSourcesFromResources(domainOntologySources.stream().map(source -> (String)source));
        return create(external, restriction);
    }

    /**
     * Create an AIF validator for specified domain ontologies and requirements.
     *
     * @param restrictionModel      Model containing restriction rules that should be applied during validation
     * @param domainOntologySources User-supplied domain ontologies as {@link CharSource}
     * @return An AIF validator for the specified ontologies and requirements
     */
    public static ValidateAIF create(Stream<CharSource> domainOntologySources, Model restrictionModel) {
        // always add AIF definition sources
        Stream<CharSource> internal = getSourcesFromResources(Stream.of(INTERCHANGE_RESNAME, AIDA_DOMAIN_COMMON_RESNAME));
        Stream<CharSource> all = Stream.concat(internal, domainOntologySources);
        return new ValidateAIF(getModelFromSources(all), restrictionModel);
    }

    /**
     * Uses the provided <code>monitor</code> during validation. If null, no progress monitor will be used.
     */
    public void setProgressMonitor(ProgressMonitor monitor) {
        this.progressMonitor = monitor;
    }

    /**
     * Tells the validator to "fail fast" if SHACL violations are detected.  Validation will terminate after
     * <code>abortThreshold</code> SHACL violations are detected.  Use zero to disable failing fast.
     *
     * @param abortThreshold the violation threshold to abort validation
     */
    public void setAbortThreshold(int abortThreshold) {
        if (abortThreshold < 0) {
            throw new IllegalArgumentException("Abort threshold must be greater than 0, or 0 to disable.");
        }
        this.abortThreshold = abortThreshold == 0 ? -1 : abortThreshold;
    }

    /**
     * Tells the validator to log debug-level output during validation.  Note that debug output is limited by what
     * the validation engine chooses to debug.
     *
     * @param debugging whether or not to produce debugging output
     */
    public void setDebugging(boolean debugging) {
        this.debugging = debugging;
    }

    /**
     * Tells the validator to perform a "shallow" validation.  Validation of a particular rule (shape) will
     * only be performed on <code>depth</code> nodes/targets.  Use zero to disable shallow validation.
     *
     * Note that shallow validation is only supported for multi-threaded validations.  See {@link #setThreadCount}.
     *
     * @param depth the number of nodes/targets to validate per shape
     */
    public void setDepth(int depth) {
        if (depth < 0) {
            throw new IllegalArgumentException("Depth must be greater than 0, or 0 to disable shallow validation.");
        }
        this.depth = depth;
    }

    /**
     * Tells the validator to use the specified number of threads during validation.
     * Currently, {@link ThreadedValidationEngine} does not support a {@link ProgressMonitor}. Setting this to
     * anything other than 1 will disable progress monitoring.
     *
     * @param threadCount number of threads to use during validation
     */
    public void setThreadCount(int threadCount) {
        if (threadCount < 1) {
            throw new IllegalArgumentException("Number of threads must be greater than or equal to 1.");
        }
        if (threadCount > 1 && (executor == null || executor.getPoolSize() != threadCount)) {
            executor = new ThreadPoolExecutor(threadCount, threadCount, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<>());
        } else if (threadCount == 1 && executor != null) {
            executor.shutdown();
            executor = null;
        }
    }

    /**
     * Return the current executor if one exists, null o/w
     *
     * @return the current executor if one exists, null o/w
     */
    public ExecutorService getExecutor() {
        return executor;
    }

    //TODO: remove when ProgressMonitor is added to threaded validator
    public void printMetrics(PrintStream ps) {

        // sort shapes by duration
        SortedSet<ThreadedValidationEngine.ShapeTaskMetadata> shapeMDs =
                new TreeSet<>(Collections.reverseOrder(Comparator.comparing(md -> md.totalDuration)));

        // accumulate thread durations
        Map<String, Long> threadDuration = new HashMap<>();
        BiConsumer<String, Long> addDuration = (name, duration) ->
                threadDuration.put(name, threadDuration.getOrDefault(name, 0L) + duration);

        // accumulate violations
        int violations = 0;

        // gather data
        for (Future<ThreadedValidationEngine.ShapeTaskMetadata> future : validationMetadata) {
            ThreadedValidationEngine.ShapeTaskMetadata smd;
            try {
                smd = future.get();
                shapeMDs.add(smd);
                addDuration.accept(smd.threadName, smd.targetDuration);
                smd.constraintMDs.forEach(cmd -> addDuration.accept(cmd.threadName, cmd.duration));
                violations += smd.violations;
            } catch (InterruptedException | ExecutionException e) {
                // do nothing
            }
        }

        // print total
        ps.println(String.format("Total: %dms v=%d", lastDuration, violations));

        // print shapes sorted by duration
        String separator = "-------------";
        ps.println("\nShapes\n" + separator);
        shapeMDs.stream()
                .peek(ps::println)
                .flatMap(smd -> smd.constraintMDs.stream())
                .forEach(cmd -> ps.println("  " + cmd));

        // print out thread duration
        ps.println("\nThreads\n" + separator);
        threadDuration.forEach((threadName, duration) -> ps.println(threadName + ": " + duration + "ms "));
    }

    /**
     * Returns whether or not the KB is valid.
     * If you want any information about why the KB was invalid, use {@link #validateKBAndReturnReport(Model)}
     *
     * @param dataToBeValidated The model to validate
     * @return True if the KB is valid
     */
    public boolean validateKB(Model dataToBeValidated) {
        return validateKB(dataToBeValidated, null);
    }

    /**
     * Returns whether or not the KB is valid.
     * If you want any information about why the KB was invalid, use {@link #validateKBAndReturnReport(Model, Model)}
     *
     * @param dataToBeValidated KB to be validated
     * @param union             unified KB if not null
     * @return True if the KB is valid
     */
    public boolean validateKB(Model dataToBeValidated, Model union) {
        return isValidReport(validateKBAndReturnReport(dataToBeValidated, union));
    }

    /**
     * Validate the specified KB and return a validation report.
     *
     * @param dataToBeValidated KB to be validated
     * @return a validation report from which more information can be derived, or null if validation didn't complete
     */
    public Resource validateKBAndReturnReport(Model dataToBeValidated) {
        return validateKBAndReturnReport(dataToBeValidated, null);
    }

    /**
     * Validate the specified KB and return a validation report. When the validator is using more than one thread,
     * this method will combine all validation reports into a single report (may incur significant overhead).
     * For the single-thread case, this is equivalent to {@link #validateKBAndReturnMultipleReports(Model, Model)}
     *
     * @param dataToBeValidated KB to be validated
     * @param union             unified KB if not null
     * @return a validation report from which more information can be derived, or null if validation didn't complete
     */
    public Resource validateKBAndReturnReport(Model dataToBeValidated, Model union) {
        Set<Resource> reports = validateKBAndReturnMultipleReports(dataToBeValidated, union);
        if (reports == null) {
            return null;
        } else if (executor != null) {
            Resource masterReport = null;
            for (Resource report : reports) {
                if (masterReport == null) {
                    masterReport = report;
                } else {
                    StmtIterator it = report.listProperties(SH.result);
                    Model masterModel = masterReport.getModel();
                    masterModel.add(report.getModel());
                    while(it.hasNext()) {
                        masterReport.addProperty(SH.result, it.next().getObject());
                    }
                    masterModel.removeAll(report, null, null);
                }
            }
            return masterReport;
        } else {
            return reports.iterator().next();
        }
    }

    /**
     * Validate the specified KB and return a set of validation reports. May return as many reports as there are threads.
     * For the single-thread case, this is equivalent to {@link #validateKBAndReturnReport(Model, Model)}.
     *
     * @param dataToBeValidated KB to be validated
     * @param union             unified KB if not null
     * @return a {@link Set} of validation reports from which more information can be derived or null if validation didn't complete
     */
    public Set<Resource> validateKBAndReturnMultipleReports(Model dataToBeValidated, Model union) {
        Set<Resource> reports = new HashSet<>();

        // We unify the given KB with the background and domain KBs before validation.
        // This is required so that constraints like "the object of a type must be an
        // entity type" will know what types are in fact entity types.
        final Model unionModel = (union == null) ? ModelFactory.createUnion(dataToBeValidated, domainModel) : union;
        unionModel.setNsPrefix("sh", "http://www.w3.org/ns/shacl#");
        unionModel.setNsPrefix("aida", InterchangeOntology.NAMESPACE);
        unionModel.setNsPrefix("aidaDomainCommon", AidaDomainOntologiesCommon.CanHaveName.getNameSpace());

        // Validates against the SHACL file to ensure that resources have the required properties
        // (and in some cases, only the required properties) of the proper types.
        ValidationEngineConfiguration config = new ValidationEngineConfiguration()
                .setValidateShapes(true)
                .setValidationErrorBatch(abortThreshold);
        if (executor != null) {
            if (debugging) {
                ((Logger) (org.slf4j.LoggerFactory.getLogger(ThreadedValidationEngine.class))).setLevel(Level.DEBUG);
            }
            ThreadedValidationEngine engine = ThreadedValidationEngine.createValidationEngine(unionModel, restrictionModel, config);
            engine.setProgressMonitor(progressMonitor);
            engine.setMaxDepth(depth);
            try {
                engine.applyEntailments();
                reports.addAll(engine.validateAll(executor));
                validationMetadata = engine.getValidationMetadata();
                lastDuration = engine.getLastDuration();
            } catch (InterruptedException | ExecutionException e) {
                System.err.println("Unable to validate due to exception");
                e.printStackTrace();
                return null;
            }
        } else {
            if (debugging) {
                ((Logger) (org.slf4j.LoggerFactory.getLogger(ValidationEngine.class))).setLevel(Level.DEBUG);
            }
            ValidationEngine engine = debugging ?
                InstrumentedValidationEngine.createValidationEngine(unionModel, restrictionModel, config) :
                ValidationUtil.createValidationEngine(unionModel, restrictionModel, config);
            engine.setProgressMonitor(progressMonitor);
            try {
                engine.applyEntailments();
                reports.add(engine.validateAll());
            } catch (InterruptedException ex) {
                return null;
            }
        }
        return reports;
    }

    /**
     * Returns whether or not <code>validationReport</code> is that of a valid KB.
     *
     * @param validationReport a validation report model, such as returned by {@link #validateKB(Model)}
     * @return True if the KB that generated the specified report is valid
     */
    public static boolean isValidReport(Resource validationReport) {
        return validationReport.getRequiredProperty(CONFORMS).getBoolean();
    }

    /**
     * Returns whether or not <code>validationReports</code> are all indicative of a valid KB.
     *
     * @param validationReports a {@link Set} of validation reports, such as returned by {@link #validateKB(Model)}
     * @return True if the KB that generated the specified reports is valid
     */
    public static boolean isValidSetOfReports(Set<Resource> validationReports) {
        return validationReports.stream().allMatch(ValidateAIF::isValidReport);
    }
}
