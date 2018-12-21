package com.ncc.gaia;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableList;
import com.google.common.io.CharSource;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import edu.isi.nlp.parameters.Parameters;
import mu.KLogging;
import org.apache.jena.query.*;
import org.apache.jena.rdf.model.*;
import org.apache.jena.shared.JenaException;
import org.apache.jena.util.FileUtils;
import org.apache.jena.vocabulary.RDF;
import org.topbraid.shacl.validation.ValidationUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.HashSet;
import java.util.List;


public final class ValidateAIF {

    private static final String SHACL_RESNAME = "edu/isi/gaia/aida_ontology.shacl";
    private static final String TA3_SHACL_RESNAME = "edu/isi/gaia/aida_hypothesis.shacl";
    private static final String INTERCHANGE_RESNAME = "edu/isi/gaia/InterchangeOntology";
    private static final String AIDA_DOMAIN_COMMON_RESNAME = "edu/isi/gaia/AidaDomainOntologiesCommon";

    private Model domainModel;
    private static Model shaclModel;
    private static Model ta3ShaclModel;

    private ValidateAIF(Model domainModel) {
        this.domainModel = domainModel;
        try {
            shaclModel = loadModel(Resources.asCharSource(Resources.getResource(SHACL_RESNAME), Charsets.UTF_8)
                    .openBufferedStream());
        } catch (IOException ioe) {
            throw new RuntimeException("While parsing AIDA Shacl " + SHACL_RESNAME, ioe);
        }

        try {
            ta3ShaclModel = loadModel(Resources.asCharSource(Resources.getResource(TA3_SHACL_RESNAME), Charsets.UTF_8)
                    .openBufferedStream());
        } catch (IOException ioe) {
            throw new RuntimeException("While parsing TA3 Shacl " + SHACL_RESNAME, ioe);
        }
    }

    private static Model loadModel(Reader reader) {
        Model ret = ModelFactory.createOntologyModel();

        ret.read(reader, "urn:x-base", FileUtils.langTurtle);
        return ret;
    }

    //companion object {

    // Ensure what file name an RDF syntax error occurs in is printed, which
    // doesn't happen by default
    private static void loadOntologyWithFriendlyError(Model model, CharSource ontologySource) {
        try {
            model.read(ontologySource.openBufferedStream(), "urn:x-base", FileUtils.langTurtle);
        } catch (Exception exception) { // includes IOException & JenaException
            throw new RuntimeException("While parsing domain ontology " + ontologySource,
                    exception);
        }
    }


    public static ValidateAIF createForDomainOntologySource(CharSource domainOntologySource) {
        final Model model = ModelFactory.createOntologyModel();
        HashSet<CharSource> models = new HashSet<>();

        // data will always be interpreted in the context of these two ontology files
        models.add(Resources.asCharSource(Resources.getResource(INTERCHANGE_RESNAME), Charsets.UTF_8));
        models.add(Resources.asCharSource(Resources.getResource(AIDA_DOMAIN_COMMON_RESNAME), Charsets.UTF_8));

        models.add(domainOntologySource);
        for (CharSource source : models) {
            loadOntologyWithFriendlyError(model, source);
        }

        return new ValidateAIF(model);
    }

    public static void main(String[] args) throws IOException {
        final KLogging log = new KLogging();

        if (args.length != 1) {
            System.out.println("Usage: validateAIF paramFile\n\tSee repo README for details.");
            System.exit(1);
        }

        // prevent too much logging from obscuring the actual problems
        Logger logger = (Logger) (org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME));
        logger.setLevel(Level.INFO);

        final Parameters params = Parameters.loadSerifStyle(new File(args[0]));
        final File domainOntologyFile = params.getExistingFile("domainOntology");
        log.getLogger().info("Using domain ontology file " + domainOntologyFile);

        // this is an RDF model which uses SHACL to encode constraints on the AIF
        final ValidateAIF validator = ValidateAIF.createForDomainOntologySource(
                Files.asCharSource(domainOntologyFile, Charsets.UTF_8));

        final com.google.common.base.Optional<File> fileList = params.getOptionalExistingFile("kbsToValidate");
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
            log.getLogger().info("Validating $fileToValidate");
            final Model dataToBeValidated = loadModel(Files.asCharSource(fileToValidate, Charsets.UTF_8)
                    .openBufferedStream());
            allValid = validator.validateKB(dataToBeValidated) && allValid;
        }

        if (!allValid) {
            // failure code if anything fails to validate
            System.exit(1);
        }
    }

    /**
     * Returns whether or not the KB and hypotheses are valid
     */
    public boolean validateTA3(Model dataToBeValidated) {
        final Model unionModel = ModelFactory.createUnion(domainModel, dataToBeValidated);
        return validateKB(dataToBeValidated, unionModel)
                && validateAgainstShacl(unionModel, ta3ShaclModel);
    }

    /**
     * Returns whether or not the KB is valid
     */
    public boolean validateKB(Model dataToBeValidated) {
        return validateKB(dataToBeValidated, null);
    }

    /**
     * Returns whether or not the KB is valid
     *
     * @param dataToBeValidated KB to be validated
     * @param union             unified KB if not null
     */
    public boolean validateKB(Model dataToBeValidated, Model union) {
        // we unify the given KB with the background and domain KBs before validation
        // this is required so that constraints like "the object of a type must be an
        // entity type" will know what types are in fact entity types
        final Model unionModel = (union == null) ? ModelFactory.createUnion(domainModel, dataToBeValidated) : union;

        // we short-circuit because earlier validation failures may make later
        // validation attempts misleading nonsense
        return /*ensureEveryNamedNodeHasARdfType(dataToBeValidated)
                &&*/ validateAgainstShacl(unionModel, shaclModel)
                && ensureConfidencesInZeroOne(unionModel)
                && ensureEveryEntityAndEventHasAType(unionModel);
    }

    private static final String ENSURE_EVERY_NAMED_NODE_HAS_A_TYPE_SPARQL_QUERY = "\"\n" +
            "    PREFIX rdf:\n" +
            "\n" +
            "    <$ {\n" +
            "        RDF.uri\n" +
            "    }>\n" +
            "    PREFIX aida:\n" +
            "\n" +
            "    <$ {\n" +
            "        AidaAnnotationOntology.NAMESPACE\n" +
            "    }>\n" +
            "\n" +
            "\n" +
            "    SELECT ?\n" +
            "    namedNode\n" +
            "            WHERE\n" +
            "\n" +
            "    {\n" +
            "           ?namedNode ? foo ? bar;\n" +
            "        FILTER(isIRI( ? namedNode)  ) .\n" +
            "        MINUS { ?nameNode rdf:type ? anything\n" +
            "    }\n" +
            "    }\n" +
            "\"\n";

    /**
     * Ensure that every named node has an RDF type specified.
     * <p>
     * Note that "type" here is RDF type, not domain ontology type.
     * <p>
     * The motivation here is to keep users from e.g. making an entity, forgetting to mark it
     * as an entity, and being confused when it appears downstream that they aren't producing
     * entities.
     */
    private boolean ensureEveryNamedNodeHasARdfType(Model dataToBeValidated) {
        // TODO: this is not working yet - I need to fiddle with the SPARQL query
        final Query query = QueryFactory.create(ENSURE_EVERY_NAMED_NODE_HAS_A_TYPE_SPARQL_QUERY);
        final QueryExecution queryExecution = QueryExecutionFactory.create(query, dataToBeValidated);
        final ResultSet results = queryExecution.execSelect();

        boolean valid = true;
        while (results.hasNext()) {
            final QuerySolution match = results.nextSolution();
            final Resource typelessNamedNode = match.getResource("namedNode");

            System.err.println("Node " + typelessNamedNode + " lacks an rdf:type property");
            valid = false;
        }
        return valid;
    }

    /**
     * Validates against the SHACL file to ensure that resources have the required properties
     * (and in some cases, only the required properties) of the proper types.  Returns true if
     * validation passes.
     */
    private boolean validateAgainstShacl(Model dataToBeValidated, Model shacl) {
        // do SHACL validation
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
            // we can assume all objects of confidenceValue are double-valued literals
            // or else we would have failed SHACL validation
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

    // used by ensureEveryEntityAndEventHasAType below
    private static final String ENSURE_TYPE_SPARQL_QUERY = "\"\n" +
            "PREFIX rdf:\n" +
            "\n" +
            "    <$ {\n" +
            "        RDF.uri\n" +
            "    }>\n" +
            "    PREFIX aida:\n" +
            "\n" +
            "    <$ {\n" +
            "        AidaAnnotationOntology.NAMESPACE\n" +
            "    }>\n" +
            "\n" +
            "    SELECT ?\n" +
            "    entityOrEvent\n" +
            "            WHERE\n" +
            "\n" +
            "    {\n" +
            "        {?entityOrEvent a aida:\n" +
            "        Entity\n" +
            "        } UNION {?entityOrEvent a aida:\n" +
            "    Event\n" +
            "    }\n" +
            "        FILTER NOT EXISTS {\n" +
            "           ?typeAssertion a rdf:\n" +
            "        Statement.\n" +
            "                ? typeAssertion rdf:\n" +
            "        predicate rdf:type.\n" +
            "                ? typeAssertion rdf:\n" +
            "        subject ? entityOrEvent.\n" +
            "    }\n" +
            "    }\n" +
            "\"";

    // Not currently used
    private static final String LACKS_TYPES_QUERY = "\"\n" +
            "    PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
            "    SELECT *\n" +
            "    WHERE {\n" +
            "        OPTIONAL {?node rdf:type ?type}\n" +
            "        FILTER(!bound(?type))\n" +
            "    }" +
            "\"";

    private boolean ensureEveryEntityAndEventHasAType(Model dataToBeValidated) {
        // it is okay if there are multiple type assertions (in case of uncertainty)
        // but there has to be at least one
        // TODO: we would like to make sure if there are multiple, then they must be in some sort
        // of mutual exclusion relationship. This may be complicated and slow, however, so we
        // don't do it yet
        final Query query = QueryFactory.create(ENSURE_TYPE_SPARQL_QUERY);
        final QueryExecution queryExecution = QueryExecutionFactory.create(query, dataToBeValidated);
        final ResultSet results = queryExecution.execSelect();

        boolean valid = true;
        while (results.hasNext()) {
            final QuerySolution match = results.nextSolution();
            final Resource typelessEntityOrEvent = match.getResource("namedNode");

            // an entity is permitted to lack a type if it is a non-prototype member of a cluster
            // this could be the case when the entity arises from coreference resolution where
            // the referents are different types
            final boolean isNonPrototypeMemberOfCluster =
                    checkNonPrototypeMemberOfCluster(dataToBeValidated,
                            AidaAnnotationOntology.CLUSTER_MEMBER, typelessEntityOrEvent);
            if (!isNonPrototypeMemberOfCluster) {
                System.err.println("Entity or event " + typelessEntityOrEvent.getURI() + " has no type assertion");
                valid = false;
            }
        }
        return valid;
    }

    // Use the default Jena interface with resource iterators even though it's cumbersome.
    private boolean checkNonPrototypeMemberOfCluster(Model model, Property property,
                                                     Resource resource) {
        boolean retVal = false;
        final ResIterator resIterator = model.listSubjectsWithProperty(property, resource);
        while (resIterator.hasNext()) {
            // TBDDAG Need some logic here...
            resIterator.nextResource();
        }
        return retVal;
    }

}