package edu.isi.gaia;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import kotlin.text.Charsets;
import org.apache.jena.query.Dataset;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.tdb.TDBFactory;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.SKOS;
import org.apache.jena.vocabulary.XSD;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

import static edu.isi.gaia.AIFUtils.*;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test to see how large we can scale  AIF.  AIF uses a Jena-based model, and so we rely on that
 * to determine how large it can get.
 *
 *   16G Ubuntu 16.04   Memory:  
 *
 */
public class ScalingTest {

    private static final String LDC_NS = "https://tac.nist.gov/tracks/SM-KBP/2018/LdcAnnotations#";
    private Model model;
    private Resource system;

    // Beginning sizes of data, about what is in T101
    private int entityCount = 1000;
    private int eventCount = 300;

    private int entityIndex = 1;
    private int eventIndex = 1;
    private int assertionIndex = 1;


    private final SeedlingOntologyMapper ontologyMapping = new SeedlingOntologyMapper();

    private final Random r = new Random();
    private List<Resource> entityResourceList = null;

    private final String filename = "scalingdata.ttl";

    // Set this to false unless the numbers are small.  It takes a long time.
    private final boolean performValidation = false;

    // Whether to use in memory or disk based.
    // Note:  TDB2 requires transactions, which we do not do!  Do not use it!
    private enum MODEL_TYPE {
        MEMORY, TDB, TDB2
    }

    private final MODEL_TYPE MODEL_TYPE_TO_USE = MODEL_TYPE.MEMORY;

    /**
     * Main function.  Call with no arguments
     */
    public static void main(String[] args) {
        ScalingTest scalingTest = new ScalingTest();
        scalingTest.runTest();
    }

    private void runTest() {

        // prevent too much logging from obscuring the Turtle examples which will be printed
        ((Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);

        switch (MODEL_TYPE_TO_USE) {
            case MEMORY:
                System.out.println("Using memory model");
                break;
            case TDB:
                System.out.println("Using disk model TDB ");
                break;
            case TDB2:
                System.out.println("Using disk model TDB2 ");
                break;
            default:
                System.out.println(" type of model not defined. ");
                System.exit(2);
        }

        for (int ii = 0; ii < 200; ii++) {
            System.out.print("Trying :  Entity count: " + entityCount + " ");
            long startTime = System.currentTimeMillis();

            runSingleTest();

            long endTime = System.currentTimeMillis();
            long duration = (endTime - startTime) / 1000;

            long size = 0;
            File f = new File(filename);
            if (f.exists()) {
                size = f.length();
            }
            size /= 1000000.;
            System.out.println(" Size of output (mb): " + size + "  Time (sec): " + duration);

            increase();
        }
    }

    private void increase() {
        entityCount *= 2;
        eventCount *= 2;
    }

    private void runSingleTest() {
        createModel();
        system = makeSystemWithURI(model, getTestSystemUri());

        entityResourceList = new ArrayList<>();
        for (int ii = 0; ii < entityCount; ii++) {
            addEntity();
        }
        for (int ii = 0; ii < eventCount; ii++) {
            addEvent();
        }


        int numStatements = 0;
        StmtIterator statmentIterator = model.listStatements();
        while (statmentIterator.hasNext()) {
            statmentIterator.nextStatement();
            numStatements++;
        }
        System.out.print(" NumberStatements: " + numStatements);

        dumpAndAssertValid(filename);
    }

    private void addEntity() {

        // Add an entity
        Resource entityResource = makeEntity(model, getEntityUri(), system);
        entityResourceList.add(entityResource);

        // sometimes add hasName, textValue, or numericValue, NOTE:  This does not check type!!!!
        double rand = r.nextDouble();
        if (rand < 0.15) {
            markName(entityResource, getRandomString(5));
        } else if (rand < 0.3) {
            markTextValue(entityResource, getRandomString(7));
        } else if (rand < 0.4) {
            markNumericValueAsDouble(entityResource, r.nextDouble());
        }

        // Set the type
        Resource typeToUse = entityTypes[r.nextInt(entityTypes.length)];
        Resource typeAssertion = markType(model, getAssertionUri(), entityResource,
                typeToUse, system, 1.0);

        addJustificationAndPrivateData(typeAssertion);
    }

    private void addEvent() {
        // Add an event
        Resource eventResource = makeEvent(model, getEventUri(), system);

        // Set the type
        String eventTypeString = EVENT_TYPES[r.nextInt(EVENT_TYPES.length)];
        Resource typeResource = ontologyMapping.eventType(eventTypeString);
        Resource typeAssertion = markType(model, getAssertionUri(), eventResource, typeResource, system, 1.0);

        addJustificationAndPrivateData(typeAssertion);

        // Make two arguments
        for (int ii = 0; ii < 2; ii++) {
            Resource argument = markAsArgument(model, eventResource,
                    ontologyMapping.eventArgumentTypeNotLowercase(eventTypeString + getRandomRole()),
                    getRandomEntity(), system, 0.785, getAssertionUri());
            addJustificationAndPrivateData(argument);
        }
    }

    private void addJustificationAndPrivateData(Resource resource) {
        String docId = getRandomDocId();

        // Justify the type assertion
        markTextJustification(model, ImmutableSet.of(resource), docId, 1029, 1033, system, 0.973);

        // Add some private data
        markPrivateData(model, resource, "{ 'provenance' : '" + docId + "' }", system);
    }

    private final ValidateAIF seedlingValidator = ValidateAIF.createForDomainOntologySource(
            Resources.asCharSource(Resources.getResource("edu/isi/gaia/SeedlingOntology"), Charsets.UTF_8));

    // we dump the test name and the model in Turtle format so that whenever the user
    // runs the tests, they will also get the examples
    private void dumpAndAssertValid(String testName) {
        try {
            RDFDataMgr.write(Files.newOutputStream(Paths.get(testName)), model, RDFFormat.TURTLE_PRETTY);
            if (performValidation) {
                assertTrue(seedlingValidator.validateKB(model));
            }
        } catch (Exception e) {
            System.err.println("Unable to write to file " + testName + " " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void createModel() {

        switch (MODEL_TYPE_TO_USE) {
            case MEMORY:
                // Make a MEMORY model
                model = ModelFactory.createDefaultModel();
                break;

            case TDB:
                // Make a disk model
                Dataset dataset = TDBFactory.createDataset("/tmp/model-scaling-" + UUID.randomUUID());
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
        model.setNsPrefix("ldcOnt", SeedlingOntologyMapper.NAMESPACE_STATIC);
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
        s += abc.charAt(r.nextInt(abc.length()));
        s += abc.charAt(r.nextInt(abc.length()));
        s += abc.charAt(r.nextInt(abc.length()));
        return s;
    }

    private String getRandomString(int length) {
        StringBuilder s = new StringBuilder();
        for (int ii = 0; ii < length; ii++) {
            s.append(abc.charAt(r.nextInt(abc.length())));
        }
        return s.toString();
    }

    private Resource getRandomEntity() {
        return entityResourceList.get(r.nextInt(entityResourceList.size()));
    }

    private String getRandomRole() {
        String s = "_" + ROLES[r.nextInt(ROLES.length)];
        return s;
    }


    // Utility values, so that we can easily create random things
    private final static String abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private final Resource[] entityTypes = SeedlingOntologyMapper.ENTITY_TYPES.toArray(new Resource[0]);

    private final String[] EVENT_TYPES = {
            "Business.DeclareBankruptcy", "Business.End", "Business.Merge", "Business.Start",
            "Conflict.Attack", "Conflict.Demonstrate", "Contact.Broadcast", "Contact.Contact",
            "Contact.Correspondence", "Contact.Meet", "Existence.DamageDestroy", "Government.Agreements",
            "Government.Legislate", "Government.Spy", "Government.Vote", "Inspection.Artifact", "Inspection.People",
            "Justice.Acquit", "Justice.Appeal", "Justice.ArrestJail", "Justice.ChargeIndict", "Justice.Convict",
            "Justice.Execute", "Justice.Extradite", "Justice.Fine", "Justice.Investigate", "Justice.Pardon",
            "Justice.ReleaseParole", "Justice.Sentence", "Justice.Sue", "Justice.TrialHearing",
            "Life.BeBorn", "Life.Die", "Life.Divorce", "Life.Injure", "Life.Marry",
            "Manufacture.Artifact", "Movement.TransportArtifact", "Movement.TransportPerson",
            "Personnel.Elect", "Personnel.EndPosition", "Personnel.Nominate", "Personnel.StartPosition",
            "Transaction.Transaction", "Transaction.TransferControl", "Transaction.TransferMoney",
            "Transaction.TransferOwnership"};

    private final String[] ROLES = {"Attacker", "Instrument", "Place", "Target", "Time", "Broadcaster",
            "Place", "Time", "Participant", "Place", "Participant", "Time",
            "Participant", "Affiliate", "Affiliation", "Affiliation", "Person",
            "Entity", "Sponsor", "Defendant", "Prosecutor", "Adjudicator",
            "Defendant", "Agent", "Instrument", "Victim", "Artifact",
            "Manufacturer", "Agent", "Artifact", "Destination", "Instrument",
            "Origin", "Time", "Agent", "Destination", "Instrument", "Origin",
            "Person", "Employee", "Organization", "Person", "Entity", "Place",
            "Beneficiary", "Giver", "Recipient", "Thing", "Time"};

}
