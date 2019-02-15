package com.ncc.aif;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.util.FileUtils;
import org.apache.jena.vocabulary.RDF;
import org.topbraid.shacl.validation.ValidationUtil;

import java.io.File;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * An AIF Validator.  These are not instantiated directly; instead invoke {@link #createForDomainOntologySource} statically,
 * specifying a domain ontology, and make calls to the returned validator.
 *
 * @author Ryan Gabbard (USC ISI)
 * @author Converted to Java by Next Century Corporation
 */
public final class ValidateAIF {

    private static final String AIDA_SHACL_RESNAME = "com/ncc/aif/aida_ontology.shacl";
    private static final String NIST_SHACL_RESNAME = "com/ncc/aif/restricted_aif.shacl";
    private static final String INTERCHANGE_RESNAME = "com/ncc/aif/ontologies/InterchangeOntology";
    private static final String AIDA_DOMAIN_COMMON_RESNAME = "com/ncc/aif/ontologies/AidaDomainOntologiesCommon";
    private static final String LDC_RESNAME = "com/ncc/aif/ontologies/SeedlingOntology";
    private static final String AO_ENTITIES_RESNAME = "com/ncc/aif/ontologies/EntityOntology";
    private static final String AO_EVENTS_RESNAME = "com/ncc/aif/ontologies/EventOntology";
    private static final String AO_RELATIONS_RESNAME = "com/ncc/aif/ontologies/RelationOntology";
    private static final String INTERCHANGE_URI = "https://tac.nist.gov/tracks/SM-KBP/2018/ontologies/InterchangeOntology";
    private static final String AIDA_DOMAIN_COMMON_URI = "https://tac.nist.gov/tracks/SM-KBP/2018/ontologies/AidaDomainOntologiesCommon";

    private static Model shaclModel;
    private static Model nistModel;
    static {
        shaclModel = ModelFactory.createOntologyModel();
        CharSource aifSource = Resources.asCharSource(Resources.getResource(AIDA_SHACL_RESNAME), Charsets.UTF_8);
        loadModel(shaclModel, aifSource);

        nistModel = ModelFactory.createOntologyModel();
        loadModel(nistModel, aifSource);
        loadModel(nistModel, Resources.asCharSource(Resources.getResource(NIST_SHACL_RESNAME), Charsets.UTF_8));
    }

    private Model domainModel;
    private boolean useRestrictedAIF;
    private ValidateAIF(Model domainModel, boolean nistFlag) {
        this.domainModel = domainModel;
        this.useRestrictedAIF = nistFlag;
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
        return create(ImmutableSet.of(domainOntologySource), false);
    }

    /**
     * Create an AIF validator for the LDC ontology.
     *
     * @param nistFlag Whether or not to validate against the NIST requirements
     * @return An AIF validator for the LDC ontology
     */
    public static ValidateAIF createForLDCOntology(boolean nistFlag) {
        return create(ImmutableSet.of(Resources.asCharSource(Resources.getResource(LDC_RESNAME), Charsets.UTF_8)),
                nistFlag);
    }

    /**
     * Create an AIF validator for the Program ontology.
     *
     * @param nistFlag Whether or not to validate against the NIST requirements
     * @return An AIF validator for the Program ontology
     */
    public static ValidateAIF createForProgramOntology(boolean nistFlag) {
        return create(ImmutableSet.of(
                Resources.asCharSource(Resources.getResource(AO_ENTITIES_RESNAME), Charsets.UTF_8),
                Resources.asCharSource(Resources.getResource(AO_EVENTS_RESNAME), Charsets.UTF_8),
                Resources.asCharSource(Resources.getResource(AO_RELATIONS_RESNAME), Charsets.UTF_8)),
                nistFlag);
    }

    /**
     * Create an AIF validator for specified domain ontologies and requirements.
     *
     * @param nistFlag              Whether or not to validate against the NIST requirements
     * @param domainOntologySources User-supplied domain ontologies
     * @return An AIF validator for the specified ontologies and requirements
     */
    public static ValidateAIF create(ImmutableSet<CharSource> domainOntologySources, boolean nistFlag) {

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

        return new ValidateAIF(model, nistFlag);
    }

    // Show usage information.
    private static void showUsage() {
        System.out.println("Usage:\n" +
                "\tvalidateAIF { --ldc | --program | --ont FILE ...} [--nist] [-h | --help] {-f FILE ... | -d DIRNAME}\n" +
                "Options:\n" +
                "--ldc\t\tValidate against the LDC ontology\n" +
                "--program\t\tValidate against the program ontology\n" +
                "--ont FILE ...\tValidate against the OWL-formatted ontolog(ies) at the specified filename(s)\n" +
                "--nist\t\tValidate against the NIST restrictions\n" +
                "-h, --help\tShow this help and usage text\n" +
                "-f FILE ...\tvalidate the specified file(s) with a .ttl suffix\n" +
                "-d DIRNAME\tValidate all .ttl files in the specified directory\n" +
                "\n" +
                "Either a file (-f) or a directory (-d) must be specified (but not both).\n" +
                "Exactly one of --ldc, --program, or --ont must be specified.\n" +
                "Ontology files can be found in src/main/resources/com/ncc/aif/ontologies:\n" +
                "- LDC: SeedlingOntology\n" +
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
        return numArgs; // Can't use fileList.size() just in case use specified same file twice
    }

    // Process command-line arguments, returning whether or not there were any errors.
    private static boolean processArgs(String[] args, Set<ArgumentFlags> flags, Set<String> domainOntologies,
                                       Set<String> validationFiles, Set<String> validationDirs) {
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
                case "--ont":
                    int numFiles = processFiles(args, i, domainOntologies);
                    if (numFiles == 0) {
                        System.err.println("ERROR: --ont requires at least one ontology file to be specified.");
                        return false;
                    }
                    i += numFiles;
                    break;
                case "-f":
                    if (flags.contains(ArgumentFlags.DIRECTORY)) {
                        System.err.println("ERROR: Please specify either -d or -f, but not both.");
                        return false;
                    }
                    i += processFiles(args, i, validationFiles);
                    flags.add(ArgumentFlags.FILES);
                    break;
                case "-d":
                    if (flags.contains(ArgumentFlags.FILES)) {
                        System.err.println("ERROR: Please specify either -d or -f, but not both.");
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
                    System.err.println("Ignoring unknown argument: " + arg);
            }
        }

        final int ontologyFlags = (
                (flags.contains(ArgumentFlags.LDC) ? 1 : 0) +
                        (flags.contains(ArgumentFlags.PROGRAM) ? 1 : 0) +
                        (domainOntologies.isEmpty() ? 0 : 1)
        );
        if (ontologyFlags != 1) {
            System.err.println("ERROR: Please specify exactly one of --ldc, --program, and --ont.");
            return false;
        }
        if ((validationFiles.isEmpty() && validationDirs.isEmpty()) ||
                (!validationFiles.isEmpty() && !validationDirs.isEmpty())) // this can happen if -d or -f had no argument
        {
            System.err.println("ERROR: Please specify either file(s) or a directory of files to validate.");
            return false;
        }

        return true;
    }

    // Program return codes from the AIF Validator.
    private enum ReturnCode {
        SUCCESS, VALIDATION_ERROR, USAGE_ERROR, FILE_ERROR
    }

    // Command-line argument flags
    private enum ArgumentFlags {
        NIST, LDC, PROGRAM, FILES, DIRECTORY
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
        final Logger logger = (Logger) (org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME));
        logger.setLevel(Level.INFO);
        logger.info("AIF Validator");

        // Process the arguments:  if there are any errors, show usage and exit.
        if (!processArgs(args, flags, domainOntologies, validationFiles, validationDirs)) {
            showUsage();
            System.exit(ReturnCode.USAGE_ERROR.ordinal());
        }

        // Collect the flags parsed from the arguments
        final boolean nistFlag = flags.contains(ArgumentFlags.NIST);
        final boolean ldcFlag = flags.contains(ArgumentFlags.LDC);
        final boolean programFlag = flags.contains(ArgumentFlags.PROGRAM);

        // Finally, try to create the validator, but fail if required elements can't be loaded/parsed.
        ValidateAIF validator = null;
        try {
            if (ldcFlag) {
                validator = createForLDCOntology(nistFlag);
            } else if (programFlag) {
                validator = createForProgramOntology(nistFlag);
            } else {
                // Convert the specified domain ontologies to CharSources.
                Set<CharSource> domainOntologySources = new HashSet<>();
                for (String source : domainOntologies) {
                    File file = new File(source);
                    domainOntologySources.add(Files.asCharSource(file, Charsets.UTF_8));
                }
                validator = create(ImmutableSet.copyOf(domainOntologySources), nistFlag);
            }
        } catch (RuntimeException rte) {
            logger.error("Could not read/parse all domain ontologies or SHACL files...exiting.");
            logger.error("--> " + rte.getLocalizedMessage());
            System.exit(ReturnCode.FILE_ERROR.ordinal());
        }

        // Collect the file(s) to be validated.
        final List<File> filesToValidate = new ArrayList<>();
        if (!validationFiles.isEmpty()) {
            for (String file : validationFiles) {
                if (file.endsWith(".ttl")) {
                    filesToValidate.add(new File(file));
                } else {
                    logger.warn("Skipping file without .ttl suffix: " + file);
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
        if (nistFlag) {
            logger.info("-> Validating against NIST SHACL.");
        }
        logger.info("*** Beginning validation of " + filesToValidate.size() + " file(s). ***");

        // Validate all files, noting I/O and other errors, but continue to validate even if one fails.
        final DateFormat format = new SimpleDateFormat("EEE, MMM d HH:mm:ss");
        boolean allValid = true;
        short skipCount = 0;
        int fileNum = 1;
        for (File fileToValidate : filesToValidate) {
            Date date = Calendar.getInstance().getTime();
            boolean skipped = false;
            logger.info("-> Validating " + fileToValidate + " at " + format.format(date) +
                    " (" + fileNum++ + " of " + filesToValidate.size() + ").");

            final Model dataToBeValidated = ModelFactory.createOntologyModel();
            try {
                loadModel(dataToBeValidated, Files.asCharSource(fileToValidate, Charsets.UTF_8));
            } catch (RuntimeException rte) {
                logger.warn("---> Could not read " + fileToValidate + "; skipping.");
                skipped = true;
                skipCount++;
            }
            if (!skipped) {
                if (!validator.validateKB(dataToBeValidated)) {
                    logger.warn("---> Validation of " + fileToValidate + " failed.");
                    allValid = false;
                }
                date = Calendar.getInstance().getTime();
                logger.info("---> completed " + format.format(date) + ".");
            }
            dataToBeValidated.close();
        }

        if (!allValid) {
            logger.info("At least one KB was invalid" + (skipCount > 0 ? " (" + skipCount + " skipped)." : "."));
            // Return a failure code if anything fails to validate.
            System.exit(ReturnCode.VALIDATION_ERROR.ordinal());
        } else {
            logger.info("All KBs were valid" + (skipCount > 0 ? " (" + skipCount + " skipped)." : "."));
        }
        System.exit(skipCount == 0 ? ReturnCode.SUCCESS.ordinal() : ReturnCode.FILE_ERROR.ordinal());
    }

    /**
     * Returns whether or not the KB is valid.
     *
     * @param dataToBeValidated The model to validate
     * @return True if the KB is valid
     */
    public boolean validateKB(Model dataToBeValidated) {
        return validateKB(dataToBeValidated, null);
    }

    /**
     * Returns whether or not the KB is valid.
     *
     * @param dataToBeValidated KB to be validated
     * @param union             unified KB if not null
     * @return True if the KB is valid
     */
    public boolean validateKB(Model dataToBeValidated, Model union) {
        // We unify the given KB with the background and domain KBs before validation.
        // This is required so that constraints like "the object of a type must be an
        // entity type" will know what types are in fact entity types.
        final Model unionModel = (union == null) ? ModelFactory.createUnion(domainModel, dataToBeValidated) : union;

        // We short-circuit because earlier validation failures may make later
        // validation attempts misleading nonsense.
        return useRestrictedAIF ?
                validateAgainstShacl(unionModel, nistModel) :
                validateAgainstShacl(unionModel, shaclModel);
    }

    /**
     * Validates against the SHACL file to ensure that resources have the required properties
     * (and in some cases, only the required properties) of the proper types.  Returns true if
     * validation passes.
     */
    private boolean validateAgainstShacl(Model dataToBeValidated, Model shacl) {
        // Do SHACL validation.
        final Resource report = ValidationUtil.validateModel(dataToBeValidated, shacl, true);
        final boolean valid = report.getRequiredProperty(
                shacl.createProperty("http://www.w3.org/ns/shacl#conforms")).getBoolean();
        if (!valid) {
            report.getModel().write(System.err, FileUtils.langTurtle);
        }
        return valid;
    }
}
