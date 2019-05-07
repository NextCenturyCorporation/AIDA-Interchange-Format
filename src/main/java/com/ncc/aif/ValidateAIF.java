package com.ncc.aif;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.util.FileUtils;
import org.topbraid.jenax.statistics.ExecStatistics;
import org.topbraid.jenax.statistics.ExecStatisticsListener;
import org.topbraid.jenax.statistics.ExecStatisticsManager;
import org.topbraid.shacl.validation.ValidationEngineConfiguration;
import org.topbraid.shacl.validation.ValidationUtil;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

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
        NONE, NIST, NIST_HYPOTHESIS
    }

    private static final String AIDA_SHACL_RESNAME = "com/ncc/aif/aida_ontology.shacl";
    private static final String NIST_SHACL_RESNAME = "com/ncc/aif/restricted_aif.shacl";
    private static final String NIST_HYPOTHESIS_SHACL_RESNAME = "com/ncc/aif/restricted_hypothesis_aif.shacl";
    private static final String INTERCHANGE_RESNAME = "com/ncc/aif/ontologies/InterchangeOntology";
    private static final String AIDA_DOMAIN_COMMON_RESNAME = "com/ncc/aif/ontologies/AidaDomainOntologiesCommon";
    private static final String LDC_RESNAME = "com/ncc/aif/ontologies/LDCOntology";
    private static final String AO_ENTITIES_RESNAME = "com/ncc/aif/ontologies/EntityOntology";
    private static final String AO_EVENTS_RESNAME = "com/ncc/aif/ontologies/EventOntology";
    private static final String AO_RELATIONS_RESNAME = "com/ncc/aif/ontologies/RelationOntology";
    private static final String INTERCHANGE_URI = "https://tac.nist.gov/tracks/SM-KBP/2018/ontologies/InterchangeOntology";
    private static final String AIDA_DOMAIN_COMMON_URI = "https://tac.nist.gov/tracks/SM-KBP/2018/ontologies/AidaDomainOntologiesCommon";

    private static Model shaclModel;
    private static Model nistModel;
    private static Model nistHypoModel;
    private static boolean initialized = false;
    private static int abortParam = -1; // TODO: separate command-line validator from its class, so we don't need this
    private static final Logger logger = (Logger) (org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME));
    private static final Property CONFORMS = ResourceFactory.createProperty("http://www.w3.org/ns/shacl#conforms");
    private static final int LONG_QUERY_THRESH = 2000;

    private static void initializeSHACLModels() {
        if (!initialized) {
            shaclModel = ModelFactory.createOntologyModel();
            loadModel(shaclModel, Resources.asCharSource(Resources.getResource(AIDA_SHACL_RESNAME), Charsets.UTF_8));

            nistModel = ModelFactory.createOntologyModel();
            nistModel.add(shaclModel);
            loadModel(nistModel, Resources.asCharSource(Resources.getResource(NIST_SHACL_RESNAME), Charsets.UTF_8));

            nistHypoModel = ModelFactory.createOntologyModel();
            nistHypoModel.add(nistModel);
            loadModel(nistHypoModel,
                    Resources.asCharSource(Resources.getResource(NIST_HYPOTHESIS_SHACL_RESNAME), Charsets.UTF_8));

            initialized = true;
        }
    }

    private Model domainModel;
    private Restriction restriction;
    private int abortThreshold = -1; // by default, do not abort on error

    private ValidateAIF(Model domainModel, Restriction restriction) {
        initializeSHACLModels();
        this.domainModel = domainModel;
        this.restriction = restriction;
    }

    // Ensure what file name an RDF syntax error occurs in is printed, which
    // doesn't happen by default.
    private static void loadModel(Model model, CharSource ontologySource) {
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
    public static ValidateAIF createForDomainOntologySource(CharSource domainOntologySource) {
        return create(ImmutableSet.of(domainOntologySource), Restriction.NONE);
    }

    /**
     * Create an AIF validator for the LDC ontology.
     *
     * @param restriction Type of restriction (if any) that should be applied during validation
     * @return An AIF validator for the LDC ontology
     */
    public static ValidateAIF createForLDCOntology(Restriction restriction) {
        return create(ImmutableSet.of(Resources.asCharSource(Resources.getResource(LDC_RESNAME), Charsets.UTF_8)),
                restriction);
    }

    /**
     * Create an AIF validator for the Program ontology.
     *
     * @param restriction Type of restriction (if any) that should be applied during validation
     * @return An AIF validator for the Program ontology
     */
    public static ValidateAIF createForProgramOntology(Restriction restriction) {
        return create(ImmutableSet.of(
                Resources.asCharSource(Resources.getResource(AO_ENTITIES_RESNAME), Charsets.UTF_8),
                Resources.asCharSource(Resources.getResource(AO_EVENTS_RESNAME), Charsets.UTF_8),
                Resources.asCharSource(Resources.getResource(AO_RELATIONS_RESNAME), Charsets.UTF_8)),
                restriction);
    }

    /**
     * Create an AIF validator for specified domain ontologies and requirements.
     *
     * @param restriction           Type of restriction (if any) that should be applied during validation
     * @param domainOntologySources User-supplied domain ontologies
     * @return An AIF validator for the specified ontologies and requirements
     */
    public static ValidateAIF create(ImmutableSet<CharSource> domainOntologySources, Restriction restriction) {

        if (domainOntologySources == null || domainOntologySources.isEmpty()) {
            throw new IllegalArgumentException("Must validate against at least one domain ontology.");
        }

        final OntModel model = ModelFactory.createOntologyModel();
        model.addLoadedImport(INTERCHANGE_URI);
        model.addLoadedImport(AIDA_DOMAIN_COMMON_URI);

        // Data will always be interpreted in the context of these two ontology files.
        final ImmutableSet<CharSource> aidaModels = ImmutableSet.of(
                Resources.asCharSource(Resources.getResource(INTERCHANGE_RESNAME), Charsets.UTF_8),
                Resources.asCharSource(Resources.getResource(AIDA_DOMAIN_COMMON_RESNAME), Charsets.UTF_8)
        );

        final HashSet<CharSource> models = new HashSet<>(aidaModels);
        models.addAll(domainOntologySources);
        for (CharSource source : models) {
            loadModel(model, source);
        }

        return new ValidateAIF(model, restriction == null ? Restriction.NONE : restriction);
    }

    // Show usage information.
    private static void showUsage() {
        System.out.println("Usage:\n" +
                "\tvalidateAIF { --ldc | --program | --ont FILE ...} [--nist] [--nist-ta3] [-o] [-h | --help] [--abort] {-f FILE ... | -d DIRNAME}\n" +
                "Options:\n" +
                "--ldc           Validate against the LDC ontology\n" +
                "--program       Validate against the program ontology\n" +
                "--ont FILE ...  Validate against the OWL-formatted ontolog(ies) at the specified filename(s)\n" +
                "--nist          Validate against the NIST restrictions\n" +
                "--nist-ta3      Validate against the NIST hypothesis restrictions (implies --nist)\n" +
                "-o              Save validation report model to a file.  KB.ttl would result in KB-report.txt.\n" +
                "                Output defaults to stderr.\n" +
                "-h, --help      Show this help and usage text\n" +
                // NOTE: restore this when/if TopBraid's fail-fast feature properly supports failing at first violation.
                // "--abort [num]   Abort validation after [num] validation errors, or first validation error if [num] is omitted.\n" +
                "--abort         Abort validation after three validation errors.\n" +
                "-f FILE ...     Validate the specified file(s) with a .ttl suffix\n" +
                "-d DIRNAME      Validate all .ttl files in the specified directory\n" +
                "\n" +
                "Either a file (-f) or a directory (-d) must be specified (but not both).\n" +
                "Exactly one of --ldc, --program, or --ont must be specified.\n" +
                "Ontology files can be found in src/main/resources/com/ncc/aif/ontologies:\n" +
                "- LDC: LDCOntology\n" +
                "- Program: EntityOntology, EventOntology, RelationOntology\n" +
                "\n" +
                "For more information, see the AIF README.");
    }

    // Process an option that takes N files as an argument and return N.
    private static int processFiles(String[] args, int i, Set<String> fileList) {
        i++; // skip the switch (-f or -d)
        boolean done = false;
        int numArgs = 0;
        while (i < args.length && !done) {
            if (args[i].startsWith("-")) {
                done = true;
            } else {
                fileList.add(args[i++]);
                numArgs++;
            }
        }
        return numArgs; // Can't use fileList.size() just in case user specified same file twice
    }

    // Process command-line arguments, returning whether or not there were any errors.
    private static boolean processArgs(String[] args, Set<ArgumentFlags> flags, Set<String> domainOntologies,
                                       Set<String> validationFiles, Set<String> validationDirs) {
        String abortStr = null;
        for (int i = 0; i < args.length; i++) {
            final String arg = args[i];
            final String strippedArg = arg.trim();
            switch (strippedArg) {
                case "-h":
                case "--help":
                    return false;
                case "--ldc":
                    flags.add(ArgumentFlags.LDC);
                    break;
                case "--program":
                    flags.add(ArgumentFlags.PROGRAM);
                    break;
                case "--nist":
                    flags.add(ArgumentFlags.NIST);
                    break;
                case "--nist-ta3":
                    flags.add(ArgumentFlags.NIST);
                    flags.add(ArgumentFlags.HYPO);
                    break;
                case "-o":
                    flags.add(ArgumentFlags.FILE_OUTPUT);
                    break;
                case "--ont":
                    int numFiles = processFiles(args, i, domainOntologies);
                    if (numFiles == 0) {
                        logger.error("--ont requires at least one ontology file to be specified.");
                        return false;
                    }
                    i += numFiles;
                    break;
                case "-p": // NOTE: this flag is not documented in the README nor the Usage info
                    flags.add(ArgumentFlags.PROFILING);
                    break;
                case "--p2": // NOTE: this flag is not documented in the README nor the Usage info
                    flags.add(ArgumentFlags.PROGRESSIVE_PROFILING);
                    break;
                case "--abort":
                    flags.add(ArgumentFlags.ABORT);
                    if (!args[i + 1].startsWith("-")) {
                        // NOTE: this parameter is not documented in the README nor the Usage info
                        abortStr = args[++i];
                    } else {
                        // NOTE: Set this to 1 when/if TopBraid's fail-fast feature properly supports failing at first violation.
                        abortParam = 3;
                    }
                    break;
                case "-f":
                    if (flags.contains(ArgumentFlags.DIRECTORY)) {
                        logger.error("Please specify either -d or -f, but not both.");
                        return false;
                    }
                    i += processFiles(args, i, validationFiles);
                    flags.add(ArgumentFlags.FILES);
                    break;
                case "-d":
                    if (flags.contains(ArgumentFlags.FILES)) {
                        logger.error("Please specify either -d or -f, but not both.");
                        return false;
                    }
                    if (!args[i + 1].startsWith("-")) {
                        validationDirs.add(args[++i]);
                        /* NOTE: if we choose to support validating files in N directories, change the above to:
                         *   i += processFiles(args, i, validationDirs);
                         */
                    }
                    flags.add(ArgumentFlags.DIRECTORY);
                    break;
                default:
                    logger.error("Unknown argument: " + arg);
                    return false;
            }
        }

        final int ontologyFlags = (
                (flags.contains(ArgumentFlags.LDC) ? 1 : 0) +
                        (flags.contains(ArgumentFlags.PROGRAM) ? 1 : 0) +
                        (domainOntologies.isEmpty() ? 0 : 1)
        );
        if (ontologyFlags != 1) {
            logger.error("Please specify exactly one of --ldc, --program, and --ont.");
            return false;
        }
        if ((validationFiles.isEmpty() && validationDirs.isEmpty()) ||
                (!validationFiles.isEmpty() && !validationDirs.isEmpty())) // this can happen if -d or -f had no argument
        {
            logger.error("Please specify either file(s) or a directory of files to validate.");
            return false;
        }
        if (abortStr != null) {
            try {
                // TODO: separate command-line validator from validator class
                abortParam = Integer.parseUnsignedInt(abortStr);
            } catch (NumberFormatException nfe) {
                abortParam = -1;
            }
            if (abortParam < 1) {
                // NOTE: restore this when/if TopBraid's fail-fast feature properly supports failing at first violation.
                //logger.error("Invalid abort parameter: " + abortStr);
                //return false;
                logger.error("Unknown argument: " + abortStr);
                return false;
            }
        }

        return true;
    }

    // Program return codes from the AIF Validator.
    private enum ReturnCode {
        SUCCESS, VALIDATION_ERROR, USAGE_ERROR, FILE_ERROR
    }

    // Command-line argument flags
    private enum ArgumentFlags {
        NIST, HYPO, LDC, PROGRAM, FILES, DIRECTORY, FILE_OUTPUT, PROFILING, PROGRESSIVE_PROFILING, ABORT
    }

    /**
     * A command-line AIF validator.  For details, see <a href="https://github.com/NextCenturyCorporation/AIDA-Interchange-Format">the AIF README</a>
     * section entitled, <i>The AIF Validator</i>.
     *
     * @param args Command line arguments as specified in the README
     */
    public static void main(String[] args) {
        HashSet<ArgumentFlags> flags = new HashSet<>();
        final Set<String> domainOntologies = new HashSet<>();
        final Set<String> validationFiles = new LinkedHashSet<>();
        final Set<String> validationDirs = new LinkedHashSet<>();

        // Prevent too much logging from obscuring the actual problems.
        logger.setLevel(Level.INFO);
        logger.info("AIF Validator");

        // Process the arguments:  if there are any errors, show usage and exit.
        if (!processArgs(args, flags, domainOntologies, validationFiles, validationDirs)) {
            showUsage();
            System.exit(ReturnCode.USAGE_ERROR.ordinal());
        }

        // Collect the flags parsed from the arguments
        final Restriction restriction = !flags.contains(ArgumentFlags.NIST) ? Restriction.NONE :
                flags.contains(ArgumentFlags.HYPO) ? Restriction.NIST_HYPOTHESIS : Restriction.NIST;
        final boolean ldcFlag = flags.contains(ArgumentFlags.LDC);
        final boolean programFlag = flags.contains(ArgumentFlags.PROGRAM);
        final boolean profiling = flags.contains(ArgumentFlags.PROFILING) || flags.contains(ArgumentFlags.PROGRESSIVE_PROFILING);

        // Finally, try to create the validator, but fail if required elements can't be loaded/parsed.
        ValidateAIF validator = null;
        try {
            if (ldcFlag) {
                validator = createForLDCOntology(restriction);
            } else if (programFlag) {
                validator = createForProgramOntology(restriction);
            } else {
                // Convert the specified domain ontologies to CharSources.
                Set<CharSource> domainOntologySources = new HashSet<>();
                for (String source : domainOntologies) {
                    File file = new File(source);
                    domainOntologySources.add(Files.asCharSource(file, Charsets.UTF_8));
                }
                validator = create(ImmutableSet.copyOf(domainOntologySources), restriction);
            }
        } catch (RuntimeException rte) {
            logger.error("Could not read/parse all domain ontologies or SHACL files...exiting.");
            logger.error("--> " + rte.getLocalizedMessage());
            System.exit(ReturnCode.FILE_ERROR.ordinal());
        }

        // Collect the file(s) to be validated.
        final List<File> filesToValidate = new ArrayList<>();
        int nonTTLcount = 0;
        if (!validationFiles.isEmpty()) {
            for (String file : validationFiles) {
                if (file.endsWith(".ttl")) {
                    filesToValidate.add(new File(file));
                } else {
                    logger.warn("Skipping file without .ttl suffix: " + file);
                    nonTTLcount++;
                }
            }
        } else { // -d option
            for (String dirName : validationDirs) {
                File dir = new File(dirName);
                if (!dir.exists()) {
                    logger.warn("Skipping non-existent directory: " + dirName);
                } else if (dir.isDirectory()) {
                    File[] files = dir.listFiles(pathname -> pathname.toString().endsWith(".ttl"));
                    if (files != null) {
                        filesToValidate.addAll(Arrays.asList(files));
                    }
                } else {
                    logger.warn("Skipping non-directory: " + dirName);
                }
            }
        }

        if (filesToValidate.isEmpty()) {
            logger.error("No files with .ttl suffix were specified.  Use -h option for help.");
            System.exit(ReturnCode.FILE_ERROR.ordinal());
        }

        if (flags.contains(ArgumentFlags.ABORT)) {
            validator.setAbortThreshold(abortParam); // aboutParam was set and validated in processArgs()
        }

        // Display a summary of what we're going to do.
        if (!validationFiles.isEmpty()) {
            logger.info("-> Validating KB(s): " +
                    (filesToValidate.size() <= 5 ? filesToValidate : "from command-line arguments."));
        } else { // We'd have failed by now if there were no TTL files in the directory
            // This would need to be addressed if we supported validating files in N directories.
            logger.info("-> Validating all KBs (*.ttl) in directory: " + validationDirs);
        }
        final String ontologyStr = (ldcFlag ? "LDC (LO)" : "") + (programFlag ? "Program (AO)" : "") +
                (domainOntologies.isEmpty() ? "" : domainOntologies);
        logger.info("-> Validating with domain ontology(ies): " + ontologyStr);
        if (restriction == Restriction.NIST) {
            logger.info("-> Validating against NIST SHACL.");
        } else if (restriction == Restriction.NIST_HYPOTHESIS) {
            logger.info("-> Validating against NIST Hypothesis SHACL.");
        }
        if (flags.contains(ArgumentFlags.ABORT)) {
            logger.info("-> Validation will abort after " + abortParam + " validation error(s).");
        }
        if (flags.contains(ArgumentFlags.FILE_OUTPUT)) {
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
        int fileNum = 0;
        final StatsCollector stats = flags.contains(ArgumentFlags.PROGRESSIVE_PROFILING) ?
                new ProgressiveStatsCollector(LONG_QUERY_THRESH) : new StatsCollector(LONG_QUERY_THRESH);
        for (File fileToValidate : filesToValidate) {
            Date date = Calendar.getInstance().getTime();
            logger.info("-> Validating " + fileToValidate + " at " + format.format(date) +
                    " (" + ++fileNum + " of " + filesToValidate.size() + ").");
            final OntModel dataToBeValidated = ModelFactory.createOntologyModel();
            boolean notSkipped = ((restriction != Restriction.NIST_HYPOTHESIS) || checkHypothesisSize(fileToValidate))
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
                    logger.warn("---> Validation of " + fileToValidate + " failed.");
                    invalidCount++;
                    dumpReport(report, fileToValidate, flags.contains(ArgumentFlags.FILE_OUTPUT));
                }
                date = Calendar.getInstance().getTime();
                logger.info("---> completed " + format.format(date) + ".");
            } else
                skipCount++;

            dataToBeValidated.close();
        }

        final ReturnCode returnCode = displaySummary(fileNum + nonTTLcount, invalidCount, skipCount + nonTTLcount);
        System.exit(returnCode.ordinal());
    }

    // Dump the validation report model either to stderr or a file
    private static void dumpReport(Resource validationReport, File fileToValidate, boolean fileOutput) {
        if (!fileOutput) {
            logger.info("---> Validation report:");
            RDFDataMgr.write(System.err, validationReport.getModel(), RDFFormat.TURTLE_PRETTY);
            return;
        }

        String outputFilename = fileToValidate.toString().replace(".ttl", "-report.txt");
        try {
            RDFDataMgr.write(java.nio.file.Files.newOutputStream(Paths.get(outputFilename)),
                    validationReport.getModel(), RDFFormat.TURTLE_PRETTY);
        } catch (IOException ioe) {
            logger.warn("---> Could not write validation report for " + fileToValidate + ".");
        }
        logger.info("--> Saved validation report to " + outputFilename);
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

    // Load the model, or fail trying.  Returns true if it's loaded, otherwise false.
    private static boolean loadFile(OntModel dataToBeValidated, File fileToValidate) {
        dataToBeValidated.addLoadedImport(INTERCHANGE_URI);
        dataToBeValidated.addLoadedImport(AIDA_DOMAIN_COMMON_URI);
        try {
            loadModel(dataToBeValidated, Files.asCharSource(fileToValidate, Charsets.UTF_8));
        } catch (RuntimeException rte) {
            logger.warn("---> Could not read " + fileToValidate + "; skipping.");
            return false;
        }
        return true;
    }

    // Display a summary to the user
    private static ReturnCode displaySummary(int fileCount, int invalidCount, int skipCount) {
        final int validCount = fileCount - invalidCount - skipCount;
        logger.info("Summary:");
        logger.info("\tFiles submitted: " + fileCount);
        logger.info("\tSkipped files: " + skipCount);
        logger.info("\tKB(s) sent to validator: " + (fileCount - skipCount));
        logger.info("\tValid KB(s): " + (fileCount - invalidCount - skipCount));
        logger.info("\tInvalid KB(s): " + invalidCount);
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
     * Tells the validator to "fail fast" if validation errors are detected.  Validation will terminate after
     * [abortThreshold] validation errors are detected.  Use zero to disable failing fast.
     *
     * @param abortThreshold the error threshold to abort validation
     */
    public void setAbortThreshold(int abortThreshold) {
        if (abortThreshold < 0) {
            throw new IllegalArgumentException("Abort threshold must be greater than 0, or 0 to disable.");
        }
        this.abortThreshold = abortThreshold == 0 ? -1 : abortThreshold;
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
     * Validate the specified KB and return a validation report.
     *
     * @param dataToBeValidated KB to be validated
     * @param union             unified KB if not null
     * @return a validation report from which more information can be derived, or null if validation didn't complete
     */
    public Resource validateKBAndReturnReport(Model dataToBeValidated, Model union) {
        // We unify the given KB with the background and domain KBs before validation.
        // This is required so that constraints like "the object of a type must be an
        // entity type" will know what types are in fact entity types.
        final Model unionModel = (union == null) ? ModelFactory.createUnion(domainModel, dataToBeValidated) : union;
        unionModel.setNsPrefix("sh", "http://www.w3.org/ns/shacl#");

        // Apply appropriate SHACL restrictions
        Model shacl;
        switch (restriction) {
            case NIST:
                shacl = nistModel;
                break;
            case NIST_HYPOTHESIS:
                shacl = nistHypoModel;
                break;
            case NONE: // fall-through on purpose
            default:
                shacl = shaclModel;
        }

        // Validates against the SHACL file to ensure that resources have the required properties
        // (and in some cases, only the required properties) of the proper types.  Returns true if
        // validation passes.
        return ValidationUtil.validateModel(unionModel, shacl,
                new ValidationEngineConfiguration()
                        .setValidateShapes(true)
                        .setValidationErrorBatch(abortThreshold));
    }

    /**
     * Returns whether or not [validationReport] is that of a valid KB.
     *
     * @param validationReport a validation report model, such as returned by {@link #validateKB(Model)}
     * @return True if the KB that generated the specified report is valid
     */
    public static boolean isValidReport(Resource validationReport) {
        return validationReport.getRequiredProperty(CONFORMS).getBoolean();
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
         * Creates a statistics collector that saves queries slower than [threshold] ms to a file.
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
         * Creates a statistics collector that progressively dumps queries slower than [threshold] ms to stdout.
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
