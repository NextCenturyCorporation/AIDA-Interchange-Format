package com.ncc.aif;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import org.apache.jena.ext.com.google.common.collect.ImmutableList;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.StmtIterator;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.apache.jena.vocabulary.XSD;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

import static com.ncc.aif.AIFUtils.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test to see how large we can scale AIF with the LDC Seedling ontology.  AIF uses a Jena-based model,
 * and so we rely on that to determine how large it can get.  Change whether you are using a memory based model
 * or a disk-based model (TDB) below in the line that defines modelTypeToUse.  MEMORY is faster, but
 * is more limited;  TDB is slower but can handle more statements.
 * <p>
 * 16G Ubuntu 16.04   Memory:    Entity count:  256000  NumberStatements: 11955372 Size of output (mb): 722  Time (sec): 75
 * 16G Ubuntu 16.04   TDB:       Entity count:  256000  NumberStatements: 11955602 Size of output (mb): 711  Time (sec): 240
 * 64G Ubuntu 16.04   Memory:    Entity count: 1024000  NumberStatements: 47821274 Size of output (mb): 2934  Time (sec): 295
 * 64G Ubuntu 16.04   TDB:       Entity count: 1024000  NumberStatements: 47509095 Size of output (mb): 2885  Time (sec): 1017
 * <p>
 * In terms of file sizes with different outputs (set argument to '-o'), this what it produced:
 * 14807532  scalingdata.ttl.JSON-LD_compactflat
 * 19265665  scalingdata.ttl.JSON-LD_compactpretty
 * 20636034  scalingdata.ttl.TriG_flat
 * 20636034  scalingdata.ttl.Turtle_flat
 * 22087664  scalingdata.ttl.TriG_blocks
 * 22087664  scalingdata.ttl.Turtle_blocks
 * 28159622  scalingdata.ttl.TriG_pretty
 * 28159622  scalingdata.ttl.Turtle_pretty
 * 39808258  scalingdata.ttl.RDF_XML_pretty
 * 69187613  scalingdata.ttl.JSON-LD_expandpretty
 * 89723786  scalingdata.ttl.N-Quads_utf-8
 * 89723786  scalingdata.ttl.N-Triples_utf-8
 * 123285222  scalingdata.ttl.TriX
 * <p>
 * The smaller ones are difficult to read, the large ones do not use prefixes. turtle_pretty is readable and not too large.
 * By default, this class runs a scaling test using the Turtle Pretty output type.
 * <p>
 * Run with:
 * %  mvn exec:java -Dexec.mainClass="com.ncc.aif.ScalingTest" -Dexec.classpathScope="test" -Dexec.args="[arguments]"
 * where arguments are:
 * <pre>
 *       -s   run a single test on a single output type (i.e., do not scale)
 *       -o   run a single test on different output types (incompatible with -s and -v)
 *       -t   use tdb model (default is to use in-memory)
 *       -v   do validation (default is to not do validation, incompatible with -o)
 * </pre>
 */
public class ScalingTest {

    private static final String LDC_NS = "https://tac.nist.gov/tracks/SM-KBP/2018/LdcAnnotations#";
    private Model model;
    private Resource system;

    // Beginning sizes of data, about what is in 10x T101
    private int entityCount = 10000;
    private int eventCount = 3000;

    private int entityIndex = 1;
    private int eventIndex = 1;
    private int assertionIndex = 1;

    private static final String NAMESPACE = "https://tac.nist.gov/tracks/SM-KBP/2018/ontologies/SeedlingOntology";

    private final Random r = new Random();
    private List<Resource> entityResourceList = null;

    private final String filename = "scalingdata.ttl";

    // Whether to use in memory or disk based.
    // Note:  TDB2 requires transactions, which we do not do!  Do not use it!
    private enum MODEL_TYPE {
        MEMORY, TDB
    }

    // What output format to use, whether turtle pretty, or flat, or ntriple, or blocks
    // See RDFFormat for a definition of these.
    private static final ImmutableList<RDFFormat> outputFormats = ImmutableList.of(
            RDFFormat.TURTLE_PRETTY,
            RDFFormat.TURTLE_FLAT,
            RDFFormat.TURTLE_BLOCKS,
            RDFFormat.NTRIPLES,
            RDFFormat.NQUADS,
            RDFFormat.TRIG_PRETTY,
            RDFFormat.TRIG_FLAT,
            RDFFormat.TRIG_BLOCKS,
            RDFFormat.JSONLD_PRETTY,
            RDFFormat.JSONLD_COMPACT_FLAT,
            RDFFormat.JSONLD_EXPAND_PRETTY,
            // RDFFormat.JSONLD_FRAME_FLAT   // Does not work, because there is no frame object.
            RDFFormat.RDFXML,
            RDFFormat.TRIX
    );

    // Whether to use in-memory or to write to disk.  Memory is faster, but size-limited
    private MODEL_TYPE modelTypeToUse = MODEL_TYPE.MEMORY;

    // Set this to false unless the numbers are small.  It takes a long time.
    private boolean performValidation = false;

    // Set this to no perform scaling, but rather try different output formats
    private boolean useMultipleOutputs = false;

    // Set this to true to run multiple, scaling tests, or false to run a single unscaled test
    private boolean runScalingTest = true;

    /**
     * Main function.  See class description for arguments.
     */
    public static void main(String[] args) {
        // prevent too much logging from obscuring the Turtle examples which will be printed
        ((Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        ScalingTest scalingTest = new ScalingTest();
        scalingTest.parseArgs(Arrays.asList(args));
        scalingTest.runTest();
    }

    private void parseArgs(List<String> args) {

        if (args.contains("-o")) {
            useMultipleOutputs = true;
            runScalingTest = false;
            if (args.contains("-s")) {
                System.err.println("Please use only one of -o and -s.");
                System.exit(1);
            }
        }

        if (args.contains("-s")) {
            runScalingTest = false;
        }

        if (args.contains("-t")) {
            modelTypeToUse = MODEL_TYPE.TDB;
        }

        if (args.contains("-v")) {
            performValidation = true;
        }

        if (useMultipleOutputs && performValidation) {
            System.err.println("Cannot perform validation on multiple output type tests.");
            System.exit(1);
        }

    }

    private void runTest() {

        if (useMultipleOutputs) {
            runOneTest();
            dumpMultipleFormats();
        } else if (runScalingTest) {
            runScalingTest();
        } else {
            runSingleTest();
        }
    }

    private void runScalingTest() {

        for (int ii = 0; ii < 200; ii++) {
            runSingleTest();
            entityCount *= 2;
            eventCount *= 2;
        }
    }

    private void runSingleTest() {
        System.out.print("Trying :  Entity count: " + entityCount + " ");
        long startTime = System.currentTimeMillis();

        runOneTest();

        dumpAndAssertValid();

        long endTime = System.currentTimeMillis();
        long duration = (endTime - startTime) / 1000;      // Convert from milliseconds to seconds.

        long size = 0;
        File f = new File(filename);
        if (f.exists()) {
            size = f.length();
        }
        size /= 1000000.;      // Convert from bytes to megabytes.
        System.out.println(" Size of output (mb): " + size + "  Time (sec): " + duration);
    }

    private void runOneTest() {
        createModel();
        system = makeSystemWithURI(model, getTestSystemUri());

        entityResourceList = new ArrayList<>();
        for (int ii = 0; ii < entityCount; ii++) {
            addEntity();
        }
        for (int ii = 0; ii < eventCount; ii++) {
            addEventOrRelation();
        }

        int numStatements = 0;
        StmtIterator statementIterator = model.listStatements();
        while (statementIterator.hasNext()) {
            statementIterator.nextStatement();
            numStatements++;
        }
        System.out.print(" NumberStatements: " + numStatements);
    }

    private void addEntity() {

        // Add an entity
        Resource entityResource = makeEntity(model, getEntityUri(), system);
        entityResourceList.add(entityResource);

        // sometimes add hasName, textValue, or numericValue
        double rand = r.nextDouble();
        Resource typeToUse;
        if (rand < 0.15) {
            markName(entityResource, getRandomString(5));
            typeToUse = SeedlingOntology.Person;
        } else if (rand < 0.3) {
            markTextValue(entityResource, getRandomString(7));
            typeToUse = SeedlingOntology.Results;
        } else if (rand < 0.4) {
            markNumericValueAsDouble(entityResource, r.nextDouble());
            typeToUse = SeedlingOntology.Age;
        } else {
            typeToUse = SeedlingOntology.Person;
        }

        // Set the type
        Resource typeAssertion = markType(model, getAssertionUri(), entityResource,
                typeToUse, system, 1.0);

        addJustificationAndPrivateData(typeAssertion);
    }

    private void addEventOrRelation() {
        // sometimes add an event, other times a relation
        double rand = r.nextDouble();
        if (rand < 0.5) {
            addEvent();
        } else {
            addRelation();
        }
    }

    private void addEvent() {
        // Add an event
        Resource eventResource = makeEvent(model, getEventUri(), system);

        // Set the type
        Resource typeResource = SeedlingOntology.Business_DeclareBankruptcy;
        Resource typeAssertion = markType(model, getAssertionUri(), eventResource, typeResource, system, 1.0);

        addJustificationAndPrivateData(typeAssertion);

        // Make two arguments
        Resource argument = markAsArgument(model, eventResource,
                SeedlingOntology.Business_DeclareBankruptcy_Place,
                getRandomEntity(), system, 0.785, getAssertionUri());
        addJustificationAndPrivateData(argument);

        Resource argumentTwo = markAsArgument(model, eventResource,
                SeedlingOntology.Business_DeclareBankruptcy_Organization,
                getRandomEntity(), system, 0.785, getAssertionUri());
        addJustificationAndPrivateData(argumentTwo);

    }

    private void addRelation() {
        // Add a relation
        Resource eventResource = makeRelation(model, getEventUri(), system);

        // Set the type
        Resource typeResource = SeedlingOntology.Physical_Resident;
        Resource typeAssertion = markType(model, getAssertionUri(), eventResource, typeResource, system, 1.0);

        addJustificationAndPrivateData(typeAssertion);

        // Make two arguments
        Resource argument = markAsArgument(model, eventResource,
                SeedlingOntology.Physical_Resident_Place,
                getRandomEntity(), system, 0.785, getAssertionUri());
        addJustificationAndPrivateData(argument);

        Resource argumentTwo = markAsArgument(model, eventResource,
                SeedlingOntology.Physical_Resident_Resident,
                getRandomEntity(), system, 0.785, getAssertionUri());
        addJustificationAndPrivateData(argumentTwo);
    }

    private void addJustificationAndPrivateData(Resource resource) {
        String docId = getRandomDocId();

        // Justify the type assertion
        markTextJustification(model, ImmutableSet.of(resource), docId, 1029, 1033, system, 0.973);

        // Add some private data
        markPrivateData(model, resource, "{ 'provenance' : '" + docId + "' }", system);
    }

    private final ValidateAIF seedlingValidator = ValidateAIF.createForDomainOntologySource(
            Resources.asCharSource(Resources.getResource("com/ncc/aif/ontologies/SeedlingOntology"), StandardCharsets.UTF_8));

    // we dump the test name and the model in Turtle format so that whenever the user
    // runs the tests, they will also get the examples
    private void dumpAndAssertValid() {


        try {
            RDFDataMgr.write(Files.newOutputStream(Paths.get(filename)), model, RDFFormat.TURTLE_PRETTY);
            if (performValidation) {
                System.out.println("\nDoing validation.  Validation errors (if any) follow:");
                assertTrue(seedlingValidator.validateKB(model));
            }
        } catch (Exception e) {
            System.err.println("Unable to write to file " + filename + " " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Try all the different types of output types.
     */
    private void dumpMultipleFormats() {

        for (RDFFormat trialFormat : outputFormats) {
            String outputFilename = filename + "." + trialFormat.toString();
            outputFilename = outputFilename.replace(" ", "").replace("/", "_");
            try {
                RDFDataMgr.write(Files.newOutputStream(Paths.get(outputFilename)), model, trialFormat);
            } catch (Exception e) {
                System.err.println("Unable to write to file " + outputFilename + " " + e.getMessage());
                e.printStackTrace();
            }
        }
    }


    private void createModel() {

        switch (modelTypeToUse) {
            case MEMORY:
                // Make a MEMORY model
                model = ModelFactory.createDefaultModel();
                break;

            case TDB:
                // Make a disk model
                String tempDir = System.getProperty("java.io.tmpdir");
                String tempLoc = tempDir + File.separator + "model-scaling-" + UUID.randomUUID();
                Dataset dataset = TDBFactory.createDataset(tempLoc);
                model = dataset.getDefaultModel();
                break;

            default:
                System.out.println(" type of model not defined. ");
                System.exit(2);
        }

        // final Model model = ModelFactory.createDefaultModel();
        // adding namespace prefixes makes the Turtle output more readable
        model.setNsPrefix("rdf", RDF.uri);
        model.setNsPrefix("xsd", XSD.getURI());
        model.setNsPrefix("aida", AidaAnnotationOntology.NAMESPACE);
        model.setNsPrefix("ldcOnt", NAMESPACE);
        model.setNsPrefix("ldc", LDC_NS);
        model.setNsPrefix("skos", SKOS.uri);
    }

    private static String getUri(String localName) {
        return LDC_NS + localName;
    }

    private String getEntityUri() {
        return getUri("entity-" + entityIndex++);
    }

    private String getEventUri() {
        return getUri("event-" + eventIndex++);
    }

    private String getAssertionUri() {
        return getUri("assertion-" + assertionIndex++);
    }

    private String getTestSystemUri() {
        return getUri("testSystem");
    }

    // ----------------------------------------------------------------------------------------------
    // Random Values Code.  Code below this allows us to create random values for
    // entities and events, including random ids, names, and suffixes.
    // ----------------------------------------------------------------------------------------------

    private String getRandomDocId() {
        String s = "";
        if (r.nextBoolean()) {
            s += "IC";
        } else {
            s += "HC";
        }
        s += "00";
        s += "" + (r.nextInt(1000));
        s += randomChar();
        s += randomChar();
        s += randomChar();
        return s;
    }


    private String getRandomString(int length) {
        StringBuilder s = new StringBuilder();
        for (int ii = 0; ii < length; ii++) {
            s.append(randomChar());
        }
        return s.toString();
    }

    private Resource getRandomEntity() {
        return entityResourceList.get(r.nextInt(entityResourceList.size()));
    }

    private char randomChar() {
        return abc.charAt(r.nextInt(abc.length()));
    }

    // Utility values, so that we can easily create random things
    private final static String abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";

}
