package com.ncc.aif;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.topbraid.jenax.statistics.ExecStatistics;
import org.topbraid.jenax.statistics.ExecStatisticsListener;
import org.topbraid.jenax.statistics.ExecStatisticsManager;
import org.topbraid.shacl.vocabulary.SH;
import picocli.CommandLine;
import picocli.CommandLine.ArgGroup;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * A command-line AIF validator.  For details, see <a href="https://github.com/NextCenturyCorporation/AIDA-Interchange-Format">the AIF README</a>
 * section entitled, <i>The AIF Validator</i>.
 */
@CommandLine.Command(name = "validateAIF", description = "Used to validate Turtle files with extension .ttl", exitCodeOnInvalidInput = 2)
public class ValidateAIFCli implements Callable<Integer> {

    // Program return codes from the AIF Validator.
    public enum ReturnCode {
        SUCCESS, VALIDATION_ERROR, USAGE_ERROR, FILE_ERROR
    }

    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    // Internal Constants
    // ----------------------------
    // Logger
    private static final Logger logger = (Logger) (org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME));
    // ViolationThreshold
    private static final int DEFAULT_MAX_VIOLATIONS = -1;
    private static final int MINIMUM_MAX_VIOLATIONS = 3;
    // Profiling
    private static final int LONG_QUERY_THRESH = 2000;
    // Threading
    private static final int DEFAULT_THREAD_COUNT = 1;

    //=-=-=-=-=-=-=-=-=-=-=-=-=-=-=
    // Command Line Arguments
    // ----------------------------
    @ArgGroup(exclusive = true, multiplicity = "1", heading = "Domain Ontologies:%n")
    private OntologyArgs ontologies;
    private static class OntologyArgs {
        @Option(names = "--ldc", description = "Validate against the LDC ontology", required = true)
        private boolean useLDCOntology;

        @Option(names = "--program", description = "Validate against the program ontology", required = true)
        private boolean useProgramOntology;

        @Option(names = "--ont", description = "Validate against the OWL-formatted ontolog(ies) at the specified filename(s)",
                paramLabel = "FILE", arity = "1..*", required = true)
        private String customOntologies;
    }

    @ArgGroup(exclusive = true, multiplicity = "0..1", heading = "Restrictions:%n")
    private RestrictionArgs restrictionArgs;
    private static class RestrictionArgs {
        @Option(names = "--nist", description = "Validate against the NIST restrictions", required = true)
        private boolean useNISTRestriction;

        @Option(names = "--nist-ta3", description = "Validate against the NIST hypothesis restrictions (implies --nist)",
                required = true)
        private boolean useNISTTA3Rescriction;
    }

    @Option(names = "-o", description = "Save validation report model to a file.  KB.ttl would result in KB-report.txt.")
    private boolean outputToFile;

    @Option(names = "--abort", description = "Abort validation after [num] SHACL violations (num > 2), or 3 violations if [num] is omitted.", paramLabel = "num")
    private void setMaxValidationErrors(int max) {
        if (max < MINIMUM_MAX_VIOLATIONS) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    String.format("Abort parameter must be at least %d", MINIMUM_MAX_VIOLATIONS));
        }
        maxValidationErrors = max;
    }
    private int maxValidationErrors = DEFAULT_MAX_VIOLATIONS;

    @Option(names = "-p", description = "Enable profiling", hidden = true)
    private boolean useProfiling;

    @Option(names = "--p2", description = "Enable progressive profiling", hidden = true)
    private boolean useProgressiveProfiling;

    @Option(names = { "-t" }, description = "Specify the number of threads to use during validation", paramLabel = "num")
    private void setThreadCount(int count) {
        if (count < DEFAULT_THREAD_COUNT) {
            throw new CommandLine.ParameterException(spec.commandLine(),
                    String.format("Thread count must be at least %d", DEFAULT_THREAD_COUNT));
        }
        threads = count;
    }
    private int threads = DEFAULT_THREAD_COUNT;

    @Option(names = "-d", description = "Treat specified files as directories")
    private boolean useDirectories;

    @Parameters(description = "The specified file(s) with a .ttl suffix or directory containing .ttl files", arity = "1..*")
    private List<File> files;

    @Spec private CommandLine.Model.CommandSpec spec;

    public static void main(String[] args) {
        System.exit(new CommandLine(new ValidateAIFCli()).execute(args));
    }

    @Override
    public Integer call() {
        // Prevent too much logging from obscuring the actual problems.
        logger.setLevel(Level.INFO);
        logger.info("AIF Validator");

        // Collect the flags parsed from the arguments
        final ValidateAIF.Restriction restriction =
                restrictionArgs == null ? ValidateAIF.Restriction.NONE :
                restrictionArgs.useNISTTA3Rescriction ? ValidateAIF.Restriction.NIST_TA3 : ValidateAIF.Restriction.NIST;
        final boolean profiling = useProfiling || useProgressiveProfiling;

        // Finally, try to create the validator, but fail if required elements can't be loaded/parsed.
        ValidateAIF validator = null;
        final String ontologyStr;
        try {
            if (ontologies.useLDCOntology) {
                validator = ValidateAIF.createForLDCOntology(restriction);
                ontologyStr = "LDC (LO)";
            } else if (ontologies.useProgramOntology) {
                validator = ValidateAIF.createForProgramOntology(restriction);
                ontologyStr = "Program (AO)";
            } else {
                StringBuilder builder = new StringBuilder();
                // Convert the specified domain ontologies to CharSources.
                Set<CharSource> domainOntologySources = new HashSet<>();
                for (String source : ontologies.customOntologies.split(" ")) {
                    File file = new File(source);
                    domainOntologySources.add(Files.asCharSource(file, Charsets.UTF_8));
                    builder.append(source).append(" ");
                }
                validator = ValidateAIF.create(ImmutableSet.copyOf(domainOntologySources), restriction);
                builder.setLength(builder.length() - 1);
                ontologyStr = builder.toString();
            }
        } catch (RuntimeException rte) {
            logger.error("Could not read/parse all domain ontologies or SHACL files...exiting.");
            logger.error("--> " + rte.getLocalizedMessage());
            return ReturnCode.FILE_ERROR.ordinal();
        }

        validator.setThreadCount(threads);

        // Collect the file(s) to be validated.
        final List<File> filesToValidate = new ArrayList<>();
        int nonTTLcount = 0;
        if (!useDirectories) {
            for (File file : files) {
                if (file.getName().endsWith(".ttl")) {
                    filesToValidate.add(file);
                } else {
                    logger.warn("Skipping file without .ttl suffix: " + file);
                    nonTTLcount++;
                }
            }
        } else { // -d option
            for (File dir : files) {
                if (!dir.exists()) {
                    logger.warn("Skipping non-existent directory: " + dir.getName());
                } else if (dir.isDirectory()) {
                    File[] files = dir.listFiles(pathname -> pathname.toString().endsWith(".ttl"));
                    if (files != null) {
                        filesToValidate.addAll(Arrays.asList(files));
                    }
                } else {
                    logger.warn("Skipping non-directory: " + dir.getName());
                }
            }
        }

        if (filesToValidate.isEmpty()) {
            logger.error("No files with .ttl suffix were specified.  Use -h option for help.");
            return ReturnCode.FILE_ERROR.ordinal();
        }

        // Display a summary of what we're going to do.
        if (!useDirectories) {
            logger.info("-> Validating KB(s): " +
                    (filesToValidate.size() <= 5 ? filesToValidate : "from command-line arguments."));
        } else { // We'd have failed by now if there were no TTL files in the directory
            // This would need to be addressed if we supported validating files in N directories.
            logger.info("-> Validating all KBs (*.ttl) in directory: " + files.get(0));
        }
        logger.info("-> Validating with domain ontology(ies): " + ontologyStr);
        if (restriction == ValidateAIF.Restriction.NIST) {
            logger.info("-> Validating against NIST SHACL.");
        } else if (restriction == ValidateAIF.Restriction.NIST_TA3) {
            logger.info("-> Validating against NIST Hypothesis SHACL.");
        }
        if (maxValidationErrors != DEFAULT_MAX_VIOLATIONS) {
            logger.info("-> Validation will abort after " + maxValidationErrors + " SHACL violation(s).");
            validator.setAbortThreshold(maxValidationErrors);
        }
        if (outputToFile) {
            logger.info("-> Validation report for invalid KBs will be saved to <kbname>-report.txt.");
        } else {
            logger.info("-> Validation report for invalid KBs will be printed to stderr.");
        }
        if (profiling) {
            logger.info("-> Saving slow queries (> " + LONG_QUERY_THRESH + " ms) to <kbname>-stats.txt.");
        }
        logger.info("*** Beginning validation of " + filesToValidate.size() + " file(s). ***");

        // Validate all files, noting I/O and other errors, but continue to validate even if one fails.
        final DateFormat format = new SimpleDateFormat("EEE, MMM d HH:mm:ss");
        int invalidCount = 0;
        int skipCount = 0;
        int abortCount = 0;
        int fileNum = 0;
        final StatsCollector stats = useProgressiveProfiling ?
                new ProgressiveStatsCollector(LONG_QUERY_THRESH) : new StatsCollector(LONG_QUERY_THRESH);
        for (File fileToValidate : filesToValidate) {
            Date date = Calendar.getInstance().getTime();
            logger.info("-> Validating " + fileToValidate + " at " + format.format(date) +
                    " (" + ++fileNum + " of " + filesToValidate.size() + ").");
            final Model dataToBeValidated = ModelFactory.createOntologyModel();
            boolean notSkipped = ((restriction != ValidateAIF.Restriction.NIST_TA3) || checkHypothesisSize(fileToValidate))
                    && loadFile(dataToBeValidated, fileToValidate);
            if (notSkipped) {
                if (profiling) {
                    stats.startCollection();
                }
                final Resource report = validator.validateKBAndReturnReport(dataToBeValidated);
                if (profiling) {
                    stats.endCollection();
                    stats.dump(fileToValidate.toString());
                }
                if (report == null) {
                    logger.warn("---> Could not validate " + fileToValidate + " (engine error).  Skipping.");
                    skipCount++;
                } else if (!ValidateAIF.isValidReport(report)) {
                    invalidCount++;
                    final int numViolations = processReport(report, fileToValidate, outputToFile);
                    if (numViolations == maxValidationErrors) {
                        logger.warn("---> Validation of " + fileToValidate +
                                " was aborted after " + maxValidationErrors + " SHACL violations.");
                        abortCount++;
                    } else {
                        logger.warn("---> Validation of " + fileToValidate + " failed.");
                    }
                }
                date = Calendar.getInstance().getTime();
                logger.info("---> completed " + format.format(date) + ".");
            } else
                skipCount++;

            dataToBeValidated.close();
        }

        final ReturnCode returnCode = displaySummary(fileNum + nonTTLcount, invalidCount, skipCount + nonTTLcount, abortCount);
        return returnCode.ordinal();
    }

    // Load the model, or fail trying.  Returns true if it's loaded, otherwise false.
    private static boolean loadFile(Model dataToBeValidated, File fileToValidate) {
        try {
            ValidateAIF.loadModel(dataToBeValidated, Files.asCharSource(fileToValidate, Charsets.UTF_8));
        } catch (RuntimeException rte) {
            logger.warn("---> Could not read " + fileToValidate + "; skipping.");
            return false;
        }
        return true;
    }

    // Dump the validation report model either to stderr or a file, and return the number of violations.
    private static int processReport(Resource validationReport, File fileToValidate, boolean fileOutput) {
        if (!fileOutput) {
            logger.info("---> Validation report:");
            RDFDataMgr.write(System.err, validationReport.getModel(), RDFFormat.TURTLE_PRETTY);
        } else {
            String outputFilename = fileToValidate.toString().replace(".ttl", "-report.txt");
            try {
                RDFDataMgr.write(java.nio.file.Files.newOutputStream(Paths.get(outputFilename)),
                        validationReport.getModel(), RDFFormat.TURTLE_PRETTY);
            } catch (IOException ioe) {
                logger.warn("---> Could not write validation report for " + fileToValidate + ".");
            }
            logger.info("--> Saved validation report to " + outputFilename);
        }

        return validationReport.getModel().listStatements(null, SH.resultSeverity, SH.Violation).toList().size();
    }

    // Return false if file is > 5MB or size couldn't be determined, otherwise true
    private static boolean checkHypothesisSize(File fileToValidate) {
        try {
            final Path path = Paths.get(fileToValidate.toURI());
            final long fileSize = java.nio.file.Files.size(path);
            if (fileSize > 1024 * 1024 * 5) { // 5MB
                logger.warn("---> Hypothesis KB " + fileToValidate + " is more than 5MB (" + fileSize + " bytes); skipping.");
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
                final PrintStream out = new PrintStream(java.nio.file.Files.newOutputStream(Paths.get(outputFilename)));
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
                final PrintStream out = new PrintStream(java.nio.file.Files.newOutputStream(Paths.get(outputFilename)));
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
