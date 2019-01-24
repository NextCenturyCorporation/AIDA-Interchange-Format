package com.ncc.aif;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import edu.isi.nlp.parameters.Parameters;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.util.FileUtils;
import org.apache.jena.vocabulary.RDF;
import org.topbraid.shacl.validation.ValidationUtil;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;

/**
 * An AIF Validator.  These are not instantiated directly; instead invoke {@link #createForDomainOntologySource} statically,
 * specifying a domain ontology, and make calls to the returned validator.
 *
 * @author Ryan Gabbard (USC ISI)
 * @author Converted to Java by Next Century Corporation
 */
public final class ValidateAIF {

    private static final String SHACL_RESNAME = "com/ncc/aif/aida_ontology.shacl";
    private static final String INTERCHANGE_RESNAME = "com/ncc/aif/InterchangeOntology";
    private static final String AIDA_DOMAIN_COMMON_RESNAME = "com/ncc/aif/AidaDomainOntologiesCommon";
    private static final String INTERCHANGE_URI = "https://tac.nist.gov/tracks/SM-KBP/2018/ontologies/InterchangeOntology";
    private static final String AIDA_DOMAIN_COMMON_URI = "https://tac.nist.gov/tracks/SM-KBP/2018/ontologies/AidaDomainOntologiesCommon";

    private Model domainModel;
    private static Model shaclModel;

    private ValidateAIF(Model domainModel) {
        this.domainModel = domainModel;
        shaclModel = ModelFactory.createOntologyModel();
        loadModel(shaclModel, Resources.asCharSource(Resources.getResource(SHACL_RESNAME), Charsets.UTF_8));
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
        final OntModel model = ModelFactory.createOntologyModel();
        model.addLoadedImport(INTERCHANGE_URI);
        model.addLoadedImport(AIDA_DOMAIN_COMMON_URI);

        // Data will always be interpreted in the context of these two ontology files.
        ImmutableSet<CharSource> models = ImmutableSet.of(
                Resources.asCharSource(Resources.getResource(INTERCHANGE_RESNAME), Charsets.UTF_8),
                Resources.asCharSource(Resources.getResource(AIDA_DOMAIN_COMMON_RESNAME), Charsets.UTF_8),
                domainOntologySource);

        for (CharSource source : models) {
            loadModel(model, source);
        }

        return new ValidateAIF(model);
    }

    /**
     * A command-line AIF validator.  For details, see <a href="https://github.com/NextCenturyCorporation/AIDA-Interchange-Format">the AIF README</a>
     * section entitled, <i>Running the validator</i>.
     *
     * @param args Command line arguments as specified in the README
     * @throws IOException If the parameter file or KBs to validate cannot be opened
     */
    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.out.println("Usage: validateAIF paramFile\n\tSee repo README for details.");
            System.exit(1);
        }

        // Prevent too much logging from obscuring the actual problems.
        Logger logger = (Logger) (org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME));
        logger.setLevel(Level.INFO);

        final Parameters params = Parameters.loadSerifStyle(new File(args[0]));
        final File domainOntologyFile = params.getExistingFile("domainOntology");
        logger.info("Using domain ontology file " + domainOntologyFile);

        // This is an RDF model which uses SHACL to encode constraints on the AIF.
        final ValidateAIF validator = ValidateAIF.createForDomainOntologySource(
                Files.asCharSource(domainOntologyFile, Charsets.UTF_8));

        final Optional<File> fileList = params.getOptionalExistingFile("kbsToValidate");
        final ImmutableList<File> filesToValidate;
        if (fileList.isPresent()) {
            filesToValidate = edu.isi.nlp.files.FileUtils.loadFileList(fileList.get());
        } else if (params.isPresent("kbToValidate")) {
            filesToValidate = ImmutableList.of(params.getExistingFile("kbToValidate"));
        } else {
            throw new RuntimeException("Either kbToValidate or kbsToValidate must be specified");
        }

        boolean allValid = true;
        for (File fileToValidate : filesToValidate) {
            logger.info("Validating " + fileToValidate);
            final Model dataToBeValidated = ModelFactory.createOntologyModel();
            loadModel(dataToBeValidated, Files.asCharSource(fileToValidate, Charsets.UTF_8));
            if (!validator.validateKB(dataToBeValidated)) {
                logger.info("Validation of " + fileToValidate + " failed.");
                allValid = false;
            }
        }

        if (!allValid) {
            // failure code if anything fails to validate
            System.exit(1);
        } else {
            logger.info("All KBs were valid.");
        }
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
     */
    public boolean validateKB(Model dataToBeValidated, Model union) {
        // We unify the given KB with the background and domain KBs before validation.
        // This is required so that constraints like "the object of a type must be an
        // entity type" will know what types are in fact entity types.
        final Model unionModel = (union == null) ? ModelFactory.createUnion(domainModel, dataToBeValidated) : union;

        // We short-circuit because earlier validation failures may make later
        // validation attempts misleading nonsense.
        return  validateAgainstShacl(unionModel, shaclModel)
                && ensureConfidencesInZeroOne(unionModel)
                && ensureEveryEntityAndEventHasAType(unionModel);
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

    private boolean ensureConfidencesInZeroOne(Model dataToBeValidated) {
        HashSet<Double> badVals = new HashSet<>();
        NodeIterator nodeIter = dataToBeValidated.listObjectsOfProperty(AidaAnnotationOntology.CONFIDENCE_VALUE);
        while (nodeIter.hasNext()) {
            // We can assume all objects of confidenceValue are double-valued literals
            // or else we would have failed SHACL validation.
            final double floatVal = nodeIter.nextNode().asLiteral().getDouble();
            if (floatVal < 0 || floatVal > 1.0) {
                badVals.add(floatVal);
            }
        }

        if (!badVals.isEmpty()) {
            // TODO: provide more context for this error
            System.err.println("The following confidence values outside the range [0, 1.0] were found: " +
                    badVals.toString());
        }
        return badVals.isEmpty();
    }

    // Used by ensureEveryEntityAndEventHasAType below
    private static final String ENSURE_TYPE_SPARQL_QUERY =
            ("PREFIX rdf: <" + RDF.uri + ">\n" +
                    "PREFIX aida: <" + AidaAnnotationOntology.NAMESPACE + ">\n" +
                    "\n" +
                    "SELECT ?entityOrEvent\n" +
                    "WHERE {\n" +
                    "    {?entityOrEvent a aida:Entity} UNION  {?entityOrEvent a aida:Event}\n" +
                    "    FILTER NOT EXISTS {\n" +
                    "    ?typeAssertion a rdf:Statement .\n" +
                    "    ?typeAssertion rdf:predicate rdf:type .\n" +
                    "    ?typeAssertion rdf:subject ?entityOrEvent .\n" +
                    "    }\n" +
                    "}").replace("\n", System.getProperty("line.separator"));

    private boolean ensureEveryEntityAndEventHasAType(Model dataToBeValidated) {
        // It is okay if there are multiple type assertions (in case of uncertainty)
        // but there has to be at least one.
        // TODO: we would like to make sure if there are multiple, then they must be in some sort
        // of mutual exclusion relationship. This may be complicated and slow, however, so we
        // don't do it yet.
        final Query query = QueryFactory.create(ENSURE_TYPE_SPARQL_QUERY);
        final QueryExecution queryExecution = QueryExecutionFactory.create(query, dataToBeValidated);
        final ResultSet results = queryExecution.execSelect();

        boolean valid = true;
        while (results.hasNext()) {
            final QuerySolution match = results.nextSolution();
            final Resource typelessEntityOrEvent = match.getResource("entityOrEvent");

            // An entity is permitted to lack a type if it is a non-prototype member of a cluster
            // this could be the case when the entity arises from coreference resolution where
            // the referents are different types.
            final boolean isNonPrototypeMemberOfCluster =
                    dataToBeValidated.listSubjectsWithProperty(AidaAnnotationOntology.CLUSTER_MEMBER,
                            typelessEntityOrEvent).hasNext();

            if (!isNonPrototypeMemberOfCluster) {
                System.err.println("Entity or event " + typelessEntityOrEvent.getURI() + " has no type assertion");
                valid = false;
            }
        }
        return valid;
    }

}
