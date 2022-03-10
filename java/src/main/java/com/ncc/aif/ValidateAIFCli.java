package com.ncc.aif;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.google.common.base.Charsets;
import com.google.common.io.CharSource;
import com.google.common.io.Resources;

import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.tdb.TDBFactory;
import org.topbraid.jenax.progress.ProgressMonitor;
import org.topbraid.jenax.statistics.ExecStatistics;
import org.topbraid.jenax.statistics.ExecStatisticsListener;
import org.topbraid.jenax.statistics.ExecStatisticsManager;
import org.topbraid.shacl.vocabulary.SH;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import picocli.CommandLine;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

/**
 * A command-line AIF validator.  For details, see <a href="https://github.com/NextCenturyCorporation/AIDA-Interchange-Format">the AIF README</a>
 * section entitled, <i>The AIF Validator</i>.
 */
@CommandLine.Command(name = "validateAIF",
        sortOptions = false,
        synopsisHeading = "%nUsage: ",
        descriptionHeading = "%nDescription:%n  ",
        optionListHeading = "%nOptions:%n",
        description = "Validate AIDA Interchange Format (AIF) Turtle files with extension .ttl",
        versionProvider = ValidateAIFCli.PropertyVersionProvider.class)
public class ValidateAIFCli implements Callable<Integer> {

    // Program return codes from the AIF Validator.
    public enum ReturnCode {
        SUCCESS, VALIDATION_ERROR, USAGE_ERROR, FILE_ERROR
    }

    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    // Package Constants
    // ----------------------------
    // Error strings
    static final String ERR_MISSING_ONT_FLAG = "Must use one of these flags: --ldc | --program | --ont";
    static final String ERR_TOO_MANY_ONT_FLAGS = "Can only use one of these flags: --ldc | --program | --ont";
    static final String ERR_MISSING_FILE_FLAG = "Must use one of these flags: -f | -d";
    static final String ERR_TOO_MANY_FILE_FLAGS = "Can only use one of these flags: -f | -d";
    static final String ERR_SMALLER_THAN_MIN = "%s must be at least %d";
    static final String ERR_BAD_ARGTYPE = "%s is not a(n) %s";
    static final String ERR_DEPTH_REQUIRES_T = "--depth requires -t with at least 2 threads";
    // Logging strings
    static final String START_MSG = "AIF Validator";
    // Version
    static final String VERSION_FILE = "com/ncc/aif/version.properties";
    static final String VERSION_PROPERTY = "version";

    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    // Internal Constants
    // ----------------------------
    // Logger
    private static final Logger logger = (Logger) (org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME));
    // ViolationThreshold
    private static final String ABORT_PARAMETER_STRING = "Abort parameter";
    private static final int DEFAULT_MAX_VIOLATIONS = 3;
    private static final int MINIMUM_MAX_VIOLATIONS = 3;
    // Depth
    private static final String DEPTH_PARAMETER_STRING = "Depth parameter";
    private static final int DEFAULT_DEPTH = 50;
    private static final int MINIMUM_DEPTH = 1;

    //Hypothesis
    private static final String DEFAULT_HYPOTHESIS_SIZE = "5"; //MB

    // Profiling
    private static final int LONG_QUERY_THRESH = 2000;
    // Threading
    private static final String THREAD_COUNT_STRING = "Thread count";
    private static final int MINIMUM_THREAD_COUNT = 1;
    // Disk-based model
    private static final String DATA_MODEL_PATH = System.getProperty("java.io.tmpdir") + "/diskbased-models/dataModels";

    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    // Command Line Arguments
    // ----------------------------
    private int argCounter = 1;
    //TODO: When picocli 4.0 is stable, make this an ArgGroup to enforce mutual exclusivity
    @Option(names = "--ldc", description = "Validate against the LDC ontology")
    private boolean useLDCOntology;

    @Option(names = "--program", description = "Validate against the program ontology")
    private boolean useProgramOntology;

    @Option(names = "--dwd", description = "Validate against the DWD. Ontology is not validated")
    private boolean useDWD;

    @Option(names = "--ont", description = "Validate against the OWL-formatted ontolog(ies) at the specified filename(s)",
            paramLabel = "FILE", arity = "1..*")
    private List<File> customOntologies;

    //TODO: When picocli 4.0 is stable, make this an ArgGroup to enforce mutual exclusivity
    @Option(names = "--nist", description = "Validate against the NIST restrictions")
    private boolean useNISTRestriction;

    //TODO: When picocli 4.0 is stable, make this an ArgGroup to enforce mutual exclusivity
    @Option(names = "--nist-ta3", description = "Validate against the NIST hypothesis restrictions (implies --nist)")
    private boolean useNISTTA3Rescriction;

    @Option(names = "--hypothesis-max-size", defaultValue = DEFAULT_HYPOTHESIS_SIZE, description = "The maximum size of a hypothesis file in MB, default is 5",
            arity = "1", converter = HypothesisMaxSizeConverter.class)
    private int hypothesisMaxSize;

    private static class HypothesisMaxSizeConverter implements CommandLine.ITypeConverter<Integer> {
        @Override
        public Integer convert(String value) {
            try {
                Integer size = "".equals(value) ? Integer.parseInt(DEFAULT_HYPOTHESIS_SIZE) : Integer.parseInt(value);

                if (size < 0 ) {
                    throw new IllegalArgumentException();
                }
                return size;
            } catch (Exception ex) {
                throw new CommandLine.TypeConversionException(String.format(ERR_BAD_ARGTYPE, value, "positive integer"));
            }
        }
    }

    @Option(names = "--abort", description = "Abort validation after [num] SHACL violations (num > 2), or 3 violations if [num] is omitted.",
            paramLabel = "num", arity = "0..1", converter = MaxErrorConverter.class)
    private int maxValidationErrors = Integer.MIN_VALUE; // Don't fail-fast by default

    private static class MaxErrorConverter implements CommandLine.ITypeConverter<Integer> {
        @Override
        public Integer convert(String value) {
            try {
                return "".equals(value) ? DEFAULT_MAX_VIOLATIONS : Integer.parseInt(value);
            } catch (Exception ex) {
                throw new CommandLine.TypeConversionException(String.format(ERR_BAD_ARGTYPE, value, Integer.TYPE.getSimpleName()));
            }
        }
    }

    @Option(names = "--depth", description =
            "Perform shallow validation in which each SHACL rule (shape) is only applied to [num] target nodes, or " + DEFAULT_DEPTH + " nodes if [num] is omitted (requires -t).",
            paramLabel = "num", arity = "0..1", converter = DepthConverter.class)
    private int depth = Integer.MIN_VALUE; // Don't perform shallow validation by default

    private static class DepthConverter implements CommandLine.ITypeConverter<Integer> {
        @Override
        public Integer convert(String value) {
            try {
                return "".equals(value) ? DEFAULT_DEPTH : Integer.parseInt(value);
            } catch (Exception ex) {
                throw new CommandLine.TypeConversionException(String.format(ERR_BAD_ARGTYPE, value, Integer.TYPE.getSimpleName()));
            }
        }
    }

    @Option(names = "--pm", description = "Enable progress monitor that shows ongoing validation progress. If -t is"
            + " specified, then thread metrics are provided post-validation instead.")
    private boolean useProgressMonitor;

    // @Option(names = "--disk", description = "Use disk-based model for validating very large files")
    // private boolean useDiskModel;

    @Option(names = "--mem", description = "Use memory model for validating files")
    private boolean useMemModel = true;

    @Option(names = "--debug", description = "Enable debugging", hidden = true)
    private boolean debugOutput;

    @Option(names = "-p", description = "Enable profiling", hidden = true)
    private boolean useProfiling;

    @Option(names = "--p2", description = "Enable progressive profiling", hidden = true)
    private boolean useProgressiveProfiling;

    @Option(names = "-o", description = "Save validation report model to a file. KB.ttl results will be saved to KB-report*.txt, up to 1 report per thread")
    private boolean outputToFile;

    @Option(names = "-t", description = "Specify the number of threads to use during validation. If the --pm option" +
            " is specified, thread metrics are provided post-validation instead.", paramLabel = "num")
    private int threads = MINIMUM_THREAD_COUNT;

    //TODO: When picocli 4.0 is stable, make this an ArgGroup to enforce mutual exclusivity
    @Option(names = "-d", description = "Validate all .ttl files in the specified directory", paramLabel = "DIRNAME")
    private File directory;

    @Option(names = "-f", description = "Validate the specified file(s) with a .ttl suffix", paramLabel = "FILE",
            arity = "1..*")
    private List<File> files;

    @Option(names = {"-h", "--help"}, usageHelp = true, description = "This help and usage text")
    boolean help;

    @Option(names = {"-v", "--version"}, versionHelp = true, description = "Print the validator version")
    boolean version;

    @Spec
    private CommandLine.Model.CommandSpec spec;

    public static void main(String[] args) {
        System.exit(execute(args));
    }

    public static int execute(String[] args) {
        CommandLine cmd = new CommandLine(new ValidateAIFCli());
        cmd.setUsageHelpWidth(76);
        CommandLine.Help.Ansi ansi = CommandLine.Help.Ansi.AUTO;
        List<Object> results = cmd.parseWithHandlers(
                new CommandLine.RunLast().useOut(System.out).useAnsi(ansi),
                new CommandLine.DefaultExceptionHandler<List<Object>>().useErr(System.err).useAnsi(ansi), args);
        Integer result = (results == null || results.isEmpty()) ? null : (Integer) results.get(0);
        return result == null ? ReturnCode.USAGE_ERROR.ordinal() : result;
    }

    @Override
    public Integer call() {
        // Enforce mutual exclusion for domain ontologies
        checkOntMutex();

        // Enforce mutual exclusion for file arguments
        checkFileMutex();

        // Enforce minimum checks
        boolean abortSet = maxValidationErrors != Integer.MIN_VALUE;
        if (abortSet) {
            checkMinimum(maxValidationErrors, ABORT_PARAMETER_STRING, MINIMUM_MAX_VIOLATIONS);
        }

        boolean threadSet = threads != MINIMUM_THREAD_COUNT;
        if (threadSet) {
            checkMinimum(threads, THREAD_COUNT_STRING, MINIMUM_THREAD_COUNT);
        }

        boolean depthSet = depth != Integer.MIN_VALUE;
        if (depthSet) {
            if (threadSet)
                checkMinimum(depth, DEPTH_PARAMETER_STRING, MINIMUM_DEPTH);
            else
                throw new CommandLine.ParameterException(spec.commandLine(), ERR_DEPTH_REQUIRES_T);
        }

        // Prevent too much logging from obscuring the actual problems.
        logger.setLevel(Level.INFO);
        logger.info(START_MSG);



        // Collect the flags parsed from the arguments
        final ValidateAIF.Restriction restriction = useNISTTA3Rescriction ? ValidateAIF.Restriction.NIST_TA3 :
                useNISTRestriction ? ValidateAIF.Restriction.NIST : ValidateAIF.Restriction.NONE;
        final boolean profiling = useProfiling || useProgressiveProfiling;

        // Collect the file(s) to be validated.
        boolean hasFiles = files != null;
        final List<File> filesToValidate = new ArrayList<>();
        int nonTTLcount = 0;
        if (hasFiles) {
            for (File file : files) {
                if (file.getName().endsWith(".ttl")) {
                    filesToValidate.add(file);
                } else {
                    logger.warn("Skipping file without .ttl suffix: " + file);
                    nonTTLcount++;
                }
            }
        } else { // -d option
            File dir = directory;
            List<Path> paths = null; 
            if (!dir.exists()) {
                logger.warn("Skipping non-existent directory: " + dir.getName());
            } else if (dir.isDirectory()) {

                try {
                    Stream<Path> walk = Files.walk(Paths.get(dir.getPath()));
                    paths = walk.filter(Files::isRegularFile)   // is a file
                                    .filter(p -> p.getFileName().toString().endsWith(".ttl"))
                                    .collect(Collectors.toList());                   

                } catch (IOException e) {
                    logger.warn("---> Could not walk directory: " + directory.toString());
                }

                if(paths != null){
                    for (Path mypath : paths){
                        filesToValidate.add(new File(mypath.toString())); 
                    }
                }

            } else {
                logger.warn("Skipping non-directory: " + dir.getName());
            }
        }

        if (filesToValidate.isEmpty()) {
            logger.error("No files with .ttl suffix were specified.  Use -h option for help.");
            return ReturnCode.FILE_ERROR.ordinal();
        }

        // Finally, try to create the validator, but fail if required elements can't be loaded/parsed.
        ValidateAIF validator;
        final String ontologyStr;
        try {
            if (useLDCOntology) {
                validator = ValidateAIF.createForLDCOntology(restriction);
                ontologyStr = "LDC (LO)";
            } else if (useProgramOntology) {
                validator = ValidateAIF.createForProgramOntology(restriction);
                ontologyStr = "Program (AO)";
            } else if (useDWD) {
                validator = ValidateAIF.createForDWD(restriction);
                ontologyStr = "DWD";
            } else {
                StringBuilder builder = new StringBuilder();
                // Convert the specified domain ontologies to String objects.
                for (File file : customOntologies) {
                    builder.append(file.getName()).append(" ");
                }
                Stream<CharSource> sources = customOntologies.stream().map(file -> com.google.common.io.Files.asCharSource(file, Charsets.UTF_8));
                validator = ValidateAIF.create(sources, restriction);
                builder.setLength(builder.length() - 1);
                ontologyStr = builder.toString();
            }
        } catch (RuntimeException rte) {
            logger.error("Could not read/parse all domain ontologies or SHACL files...exiting.");
            logger.error("--> " + rte.getLocalizedMessage());
            return ReturnCode.FILE_ERROR.ordinal();
        }

        // Display a summary of what we're going to do.
        if (hasFiles) {
            logger.info("-> Validating KB(s): " +
                    (filesToValidate.size() <= 5 ? filesToValidate : "from command-line arguments."));
        } else { // We'd have failed by now if there were no TTL files in the directory
            // This would need to be addressed if we supported validating files in N directories.
            logger.info("-> Validating all KBs (*.ttl) in directory: " + directory.getName());
        }
        logger.info("-> Validating with domain ontology(ies): " + ontologyStr);
        if (restriction == ValidateAIF.Restriction.NIST) {
            logger.info("-> Validating against NIST SHACL.");
        } else if (restriction == ValidateAIF.Restriction.NIST_TA3) {
            logger.info("-> Validating against NIST Hypothesis SHACL.");
        }
        if (abortSet) {
            logger.info("-> Validation will abort after " + maxValidationErrors + " SHACL violation(s).");
            validator.setAbortThreshold(maxValidationErrors);
        }
        if (threadSet) {
            logger.info("-> Validation will use " + threads + " threads.");
            validator.setThreadCount(threads);
        }
        if (debugOutput) {
            logger.info("-> Validation debugging output enabled.");
            validator.setDebugging(true);
        }
        if (depthSet) {
            logger.info("-> Performing shallow validation on " + depth + " target node(s) per rule.");
            validator.setDepth(depth);
        }
        if (!useMemModel) {
            logger.info("-> Using disk-based model for validation.");
        }
        if (outputToFile) {
            logger.info("-> Validation report for invalid KBs will be saved to <kbname>-report*.txt., up to 1 report per thread");
        } else {
            logger.info("-> Validation report for invalid KBs will be printed to stderr.");
        }
        if (profiling) {
            logger.info("-> Saving slow queries (> " + LONG_QUERY_THRESH + " ms) to <kbname>-stats.txt.");
        }
        if (useProgressMonitor) {
            if (threadSet) {
                logger.info("-> Saving thread metrics to <kbname>-performance.txt.");
            }
            else {
                logger.info("-> Saving ongoing validation progress to <kbname>-progress.tab.");
            }
        }
        logger.info("*** Beginning validation of " + filesToValidate.size() + " file(s). ***");

        // Validate all files, noting I/O and other errors, but continue to validate even if one fails.
        final SimpleDateFormat format = new SimpleDateFormat("EEE, MMM d HH:mm:ss");
        int invalidCount = 0;
        int skipCount = 0;
        int abortCount = 0;
        int fileNum = 0;
        Path dataModelDir;
        final StatsCollector stats = useProgressiveProfiling ?
                new ProgressiveStatsCollector(LONG_QUERY_THRESH) : new StatsCollector(LONG_QUERY_THRESH);
        for (File fileToValidate : filesToValidate) {
            Date date = Calendar.getInstance().getTime();
            logger.info("-> Validating " + fileToValidate + " at " + format.format(date) +
                    " (" + ++fileNum + " of " + filesToValidate.size() + ").");
            Model dataToBeValidated;
            Dataset dataset = null;
            if (!useMemModel) {
                try {
                    dataModelDir = Paths.get(DATA_MODEL_PATH, fileToValidate.getName().replace(".ttl", ""));
                    deleteDir(dataModelDir);  // Delete the directory if it exists
                    Files.createDirectories(dataModelDir);
                    dataset = TDBFactory.createDataset(dataModelDir.toString());
                    dataToBeValidated = dataset.getDefaultModel();
                } catch (IOException ioe) {
                    logger.error("Could not create disk-based model.");
                    logger.error("--> " + ioe.getLocalizedMessage());
                    return ReturnCode.FILE_ERROR.ordinal();
                }
            } else {
                dataToBeValidated = ModelFactory.createDefaultModel();
            }
            boolean notSkipped = ((restriction != ValidateAIF.Restriction.NIST_TA3) || checkHypothesisSize(fileToValidate, hypothesisMaxSize))
                    && loadFile(dataToBeValidated, fileToValidate);
            if (notSkipped) {
                if (profiling) {
                    stats.startCollection();
                }
                if (useProgressMonitor && !threadSet) {
                    String filename = fileToValidate.getName().replace(".ttl", "") + "-progress.tab";
                    ProgressMonitor pm;
                    try {
                        pm = new AIFProgressMonitor(filename);
                    } catch (IOException e) {
                        pm = new AIFProgressMonitor();
                        logger.warn("Could not open progress monitor filename {}.  Writing progress to StdOut.", filename);
                    }
                    validator.setProgressMonitor(pm);
                }
                final Set<Resource> reports = validator.validateKBAndReturnMultipleReports(dataToBeValidated, null);
                if (profiling) {
                    stats.endCollection();
                    stats.dump(fileToValidate.toString());
                }
                if (reports == null) {
                    logger.warn("---> Could not validate " + fileToValidate + " (engine error).  Skipping.");
                    skipCount++;
                } else if (!ValidateAIF.isValidSetOfReports(reports)) {
                    invalidCount++;
                    final int numViolations = processReports(reports, fileToValidate, outputToFile);
                    boolean hasAbort = reports.stream().anyMatch(report -> report.hasProperty(ThreadedValidationEngine.SH_ABORTED));
                    if (numViolations == maxValidationErrors || hasAbort) {
                        logger.warn("---> Validation of " + fileToValidate +
                                " was aborted after " + numViolations + " SHACL violations.");
                        abortCount++;
                    } else {
                        logger.warn("---> Validation of " + fileToValidate + " failed.");
                    }
                }
                date = Calendar.getInstance().getTime();
                logger.info("---> completed " + format.format(date) + ".");

                // TODO: replace this when multi-threaded progress monitor exists
                if (useProgressMonitor && threadSet) {
                    String outputFilename = fileToValidate.toString().replace(".ttl", "-performance.txt");
                    try (PrintStream ps = new PrintStream(Files.newOutputStream(Paths.get(outputFilename)))) {
                        validator.printMetrics(ps);
                    } catch (IOException e) {
                        logger.warn("---> Could not write thread metrics to " + outputFilename + ".");
                    }
                }
            } else
                skipCount++;

            dataToBeValidated.close();
            if (!useMemModel) {
                if (dataset != null) {
                    dataset.close();
                }
            }
        }

        final ReturnCode returnCode = displaySummary(fileNum + nonTTLcount, invalidCount, skipCount + nonTTLcount, abortCount);
        if (!useMemModel) {
            deleteDir(Paths.get(DATA_MODEL_PATH)); // Try to clean up after ourselves
        }
        if (threadSet) {
            validator.getExecutor().shutdownNow();
        }
        return returnCode.ordinal();
    }

    // Delete the specified directory; log a warning if it fails.
    private void deleteDir(Path directory) {
        try {
            if (Files.exists(directory)) { // Delete the directory if it exists
                Files.walk(directory).sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        } catch (IOException ioe) {
            logger.warn("---> Could not delete directory: " + directory.toString());
        }
    }

    //TODO: make ArgGroup when 4.0 is stable
    private void checkOntMutex() {
        // Enforce mutual exclusion for domain ontologies
        boolean hasCustom = customOntologies != null;
        if (!(useProgramOntology || useLDCOntology || hasCustom || useDWD)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    ERR_MISSING_ONT_FLAG);
        }
        if (!(useProgramOntology ^ useLDCOntology ^ hasCustom ^ useDWD)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    ERR_TOO_MANY_ONT_FLAGS);
        }
    }

    //TODO: make ArgGroup when 4.0 is stable
    private void checkFileMutex() {
        // Enforce mutual exclusion for file arguments
        boolean hasFiles = files != null;
        boolean hasDirectory = directory != null;
        if (!(hasDirectory || hasFiles)) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    ERR_MISSING_FILE_FLAG);
        }
        if (hasFiles && hasDirectory) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    ERR_TOO_MANY_FILE_FLAGS);
        }
    }

    private void checkMinimum(int value, String name, int atLeast) {
        // Enforce minimum values for certain parameters
        if (value < atLeast) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    String.format(ERR_SMALLER_THAN_MIN, name, atLeast));
        }
    }

    // Load the model, or fail trying.  Returns true if it's loaded, otherwise false.
    private static boolean loadFile(Model dataToBeValidated, File fileToValidate) {
        try {
            ValidateAIF.loadModel(dataToBeValidated, com.google.common.io.Files.asCharSource(fileToValidate, Charsets.UTF_8));
        } catch (RuntimeException rte) {
            logger.warn("---> Could not read " + fileToValidate + "; skipping.");
            return false;
        }
        return true;
    }

    // Dump the validation report model either to stderr or a file, and return the number of violations.
    private static int processReports(Set<Resource> validationReports, File fileToValidate, boolean fileOutput) {
        if (!fileOutput) {
            logger.info("---> Validation report(s):");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            for (Resource report : validationReports) {
                RDFDataMgr.write(baos, report.getModel(), RDFFormat.TURTLE_PRETTY);
            }
            logger.info(baos.toString());
        } else {
            String suffix = validationReports.size() == 1 ? "-report.txt" : "-report-%d.txt";
            String template = fileToValidate.toString().replace(".ttl", suffix);
            int i = 1;
            for (Resource report : validationReports) {
                String outputFilename = String.format(template, i++);
                try {
                    RDFDataMgr.write(Files.newOutputStream(Paths.get(outputFilename)),
                            report.getModel(), RDFFormat.TURTLE_PRETTY);
                } catch (IOException ioe) {
                    logger.warn("---> Could not write validation report for " + fileToValidate + ".");
                }
                logger.info("--> Saved validation report to " + outputFilename);
            }
        }
        return validationReports.stream()
                .map(Resource::getModel)
                .map(model -> model.listStatements(null, SH.resultSeverity, SH.Violation))
                .map(StmtIterator::toList)
                .map(List::size)
                .reduce(0, Integer::sum);
    }

    // Return false if file is > 5MB or size couldn't be determined, otherwise true
    private static boolean checkHypothesisSize(File fileToValidate, int maxHypothesisSize) {
        try {
            final Path path = Paths.get(fileToValidate.toURI());
            final long fileSize = Files.size(path);
            if (fileSize > (1024 * 1024 * maxHypothesisSize)) {
                logger.warn("---> Hypothesis KB " + fileToValidate + " is more than " + maxHypothesisSize + "MB (" + fileSize + " bytes); skipping.");
                return false;
            } else {
                return true;
            }
        } catch (IOException ioe) {
            logger.warn("---> Could not determine size for hypothesis KB " + fileToValidate + "; skipping.");
            return false;
        }
    }

    // Display a summary to the user
    private static ReturnCode displaySummary(int fileCount, int invalidCount, int skipCount, int abortCount) {
        final int validCount = fileCount - invalidCount - skipCount;
        logger.info("Summary:");
        logger.info("\tFiles submitted: " + fileCount);
        logger.info("\tSkipped files: " + skipCount);
        logger.info("\tKBs sent to validator: " + (fileCount - skipCount));
        logger.info("\tValid KBs: " + (fileCount - invalidCount - skipCount));
        logger.info("\tInvalid KBs: " + invalidCount);
        if (abortCount > 0) {
            logger.info("\t  Aborted validations: " + abortCount);
        }
        if (fileCount == validCount) {
            logger.info("*** All submitted KBs were valid. ***");
        } else if (fileCount == skipCount) {
            logger.info("*** No validation was performed. ***");
        }

        if (invalidCount > 0) { // Return a failure code if anything fails to validate.
            return ReturnCode.VALIDATION_ERROR;
        } else {
            return skipCount == 0 ? ReturnCode.SUCCESS : ReturnCode.FILE_ERROR;
        }
    }

    public static class PropertyVersionProvider implements CommandLine.IVersionProvider {
        @Override
        public String[] getVersion() throws Exception {
            Properties props = new Properties();
            props.load(Resources.getResource(VERSION_FILE).openStream());
            return new String[]{props.getProperty(VERSION_PROPERTY)};
        }
    }

    /**
     * A statistics collector for use in profiling TopBraid-based SHACL validation.  Typical usage is to call
     * {@link #startCollection()} and {@link #endCollection()} to bound statistics collection,
     * then call {@link #dump(String)} to dump slow query statistics to <filename>-stats.txt.
     * <p>
     * This statistics collector only outputs slow queries via the {@link #dump(String)} method.  If you suspect
     * validation will not complete due to out of memory or other error conditions, consider using {@link ProgressiveStatsCollector}.
     */
    private static class StatsCollector {

        final int durationThreshold;

        /**
         * Creates a statistics collector that saves queries slower than <code>threshold</code> ms to a file.
         *
         * @param threshold the threshold definition of a slow query for this statistics collector
         */
        StatsCollector(int threshold) {
            this.durationThreshold = threshold;
        }

        /**
         * Start statistics collection.  Clears any previous statistics gathered by TopBraid statistics manager.
         */
        void startCollection() {
            ExecStatisticsManager.get().reset();
            ExecStatisticsManager.get().setRecording(true);
        }

        /**
         * End statistics collection.
         */
        void endCollection() {
            ExecStatisticsManager.get().setRecording(false);
        }

        /**
         * Dump all gathered slow query statistics to <basename>-stats.txt, starting with the slowest queries.
         *
         * @param basename a file basename to determine the profiling output filename
         */
        void dump(String basename) {
            final String outputFilename = basename.replace(".ttl", "-stats.txt");
            try {
                final PrintStream out = new PrintStream(Files.newOutputStream(Paths.get(outputFilename)));
                dumpStats(out);
                out.close();
            } catch (IOException ioe) {
                logger.warn("---> Could not write statistics for " + basename + ".");
            }
        }

        // Dump stats to the specified PrintStream
        private void dumpStats(PrintStream out) {
            final SortedMap<Integer, ExecStatistics> savedStats = new TreeMap<>();
            final SortedSet<Map.Entry<Integer, ExecStatistics>> sortedStats = new TreeSet<>(
                    Collections.reverseOrder(Comparator.comparing(entry -> entry.getValue().getDuration())));
            List<ExecStatistics> stats = ExecStatisticsManager.get().getStatistics();
            for (int i = 0; i < stats.size(); i++) {
                if (stats.get(i).getDuration() > durationThreshold) {
                    savedStats.put(i, stats.get(i));
                }
            }
            if (savedStats.isEmpty()) {
                out.println("There were no queries that took longer than " + durationThreshold + "ms (of "
                        + stats.size() + " queries overall).");
            } else {
                out.println("Displaying " + savedStats.size() + " slow queries (of "
                        + stats.size() + " queries overall).");
                sortedStats.addAll(savedStats.entrySet());
                sortedStats.forEach(n -> dumpStat(n.getKey(), n.getValue(), out, true));
            }
        }

        /**
         * Dump a single query's statistics to the specified PrintStream
         *
         * @param queryNum      the query number showing where it occurred chronologically in validation
         * @param queryStats    statistics about the query returned by TopBraid
         * @param out           a PrintStream to dump the query statistics
         * @param leadingSpaces whether to print leading or trailing spaces
         */
        void dumpStat(Integer queryNum, ExecStatistics queryStats, PrintStream out,
                      boolean leadingSpaces) {
            if (leadingSpaces) {
                out.println("\n");
            }
            out.println("Query #" + (queryNum + 1));
            out.println("Label: " + queryStats.getLabel());
            out.println("Duration: " + queryStats.getDuration() + "ms");
            out.println("StartTime: " + new Date(queryStats.getStartTime()));
            out.println("Context node: " + queryStats.getContext().toString());
            out.println("Query Text: " + queryStats.getQueryText().replaceAll("PREFIX.+\n", ""));
            if (!leadingSpaces) {
                out.println("\n");
            }
        }
    }

    /**
     * A statistics collector for use in profiling TopBraid-based SHACL validation.  Typical usage is to call
     * {@link #startCollection()} and {@link #endCollection()} to bound statistics collection,
     * then call {@link #dump(String)} to dump slow query statistics to <filename>-stats.txt.
     * <p>
     * This statistics collector progressively dumps slow queries to stdout, which is useful if you suspect
     * the validation will not complete due to out of memory or other error conditions.
     */
    private static class ProgressiveStatsCollector extends StatsCollector implements ExecStatisticsListener {

        private final SortedMap<Integer, ExecStatistics> savedStats = new TreeMap<>();

        /**
         * Creates a statistics collector that progressively dumps queries slower than <code>threshold</code> ms to stdout.
         *
         * @param threshold the threshold definition of a slow query for this statistics collector
         */
        ProgressiveStatsCollector(int threshold) {
            super(threshold);
        }

        /**
         * Start statistics collection.  Clears any previous statistics gathered by TopBraid statistics manager.
         */
        @Override
        void startCollection() {
            savedStats.clear();
            super.startCollection();
            ExecStatisticsManager.get().addListener(this);
        }

        /**
         * End statistics collection.
         */
        @Override
        void endCollection() {
            super.endCollection();
            ExecStatisticsManager.get().removeListener(this);
        }

        /**
         * Receives notification that a TopBraid query statistic has been generated.
         */
        public void statisticsUpdated() {
            final List<ExecStatistics> stats = ExecStatisticsManager.get().getStatistics();
            final ExecStatistics statistic = stats.get(stats.size() - 1);
            if (statistic.getDuration() > durationThreshold) {
                savedStats.put(stats.size(), statistic);
                System.out.println("Dumping slow query #" + stats.size() + "; " + statistic.getDuration() + "ms.");
                dumpStat(stats.size(), statistic, System.out, false);
                System.out.flush(); // Make sure stdout gets displayed even if we eventually run out of memory
            }
        }

        /**
         * Dump all gathered slow query statistics to <basename>-stats.txt, starting with the slowest queries.
         *
         * @param basename a file basename to determine the profiling output filename
         */
        @Override
        void dump(String basename) {
            final String outputFilename = basename.replace(".ttl", "-stats.txt");
            try {
                final List<ExecStatistics> stats = ExecStatisticsManager.get().getStatistics();
                final PrintStream out = new PrintStream(Files.newOutputStream(Paths.get(outputFilename)));
                if (savedStats.isEmpty()) {
                    out.println("There were no queries that took longer than " + durationThreshold + "ms (of "
                            + stats.size() + " queries overall).");
                } else {
                    out.println("Displaying " + savedStats.size() + " slow queries (of "
                            + stats.size() + " queries overall).");
                    final SortedSet<Map.Entry<Integer, ExecStatistics>> sortedStats = new TreeSet<>(
                            Collections.reverseOrder(Comparator.comparing(entry -> entry.getValue().getDuration())));
                    sortedStats.addAll(savedStats.entrySet());
                    sortedStats.forEach(n -> dumpStat(n.getKey(), n.getValue(), out, true));
                }
                out.close();
            } catch (IOException ioe) {
                logger.warn("---> Could not write statistics for " + basename + ".");
            }
        }
    }
}
