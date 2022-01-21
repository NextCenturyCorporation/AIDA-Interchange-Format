package com.ncc.aif.ont2javagen;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.apache.jena.ontology.Individual;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntDocumentManager;
import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.OntProperty;
import org.apache.jena.ontology.Ontology;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.util.FileUtils;
import org.apache.jena.vocabulary.RDF;
import org.topbraid.shacl.vocabulary.SH;

/**
 * Generates java resources from an ontology.  Directions for how to generate java files from this class can be found at
 * src/main/java/com/ncc/aif/ont2javagen/README.md
 */
public class OntologyGeneration {
    private Ontology ontology;
    private List<OntClass> classes = new ArrayList<>();
    private List<OntProperty> properties = new ArrayList<>();
    private List<Individual> individuals = new ArrayList<>();

    // Reads in ontology File
    public OntologyGeneration(InputStream ontologyFile) {
        OntModelSpec s = new OntModelSpec(OntModelSpec.OWL_MEM);
        OntModel temp = ModelFactory.createOntologyModel(s,null);
        OntDocumentManager dm = temp.getDocumentManager();
        dm.setProcessImports(false);

        temp.read(ontologyFile, "urn:x-base", FileUtils.langTurtle);
        ontology = temp.listOntologies().next();
        temp.listNamedClasses().forEachRemaining(classes::add);
        temp.listAllOntProperties().forEachRemaining(op -> {
            if (!op.isAnon()) {
                properties.add(op);
            }
        });
        temp.listIndividuals().forEachRemaining(op -> {
            if (!op.isAnon()) {
                individuals.add(op);
            }
        });
    }

    /**
     * Will create java classes with resources from a given set of ontologies.
     * @param args String Array of locations to Ontologies that resources will be generated from.
     */
    public static void main(String[] args) {

        if (args.length == 0) {
            String aifRoot = "java/src/main/resources/com/ncc/aif/";
            String ontRoot = aifRoot + "ontologies/";
            Supplier<Stream<String>> getStream = () -> Stream
                    .of("AidaDomainOntologiesCommon", "EntityOntology", "EventOntology", "InterchangeOntology",
                            "LDCOntology", "RelationOntology", "LDCOntologyM36")
                    .map(file -> ontRoot + file);
            getStream.get().forEach(OntologyGeneration::writeJavaFile);
            getStream.get().forEach(OntologyGeneration::writePythonFile);
            writeShaclJavaFile(aifRoot + "aida_ontology.shacl",
                    aifRoot + "restricted_aif.shacl",
                    aifRoot + "restricted_hypothesis_aif.shacl",
                    aifRoot + "restricted_claimframe_aif.shacl");          
        } else {
            for (String arg : args) {
                writeJavaFile(arg);
            }
        }
    }

    private static void writeShaclJavaFile(String... locations) {
        Model model = ModelFactory.createDefaultModel();
        for (String location : locations) {
            RDFDataMgr.read(model, location, Lang.TURTLE);
        }
        SortedSet<Resource> shapes = new TreeSet<>(Comparator.comparing(Resource::getURI));
        addShapesOfType(model, shapes, SH.NodeShape);
        addShapesOfType(model, shapes, SH.PropertyShape);
        addShapesOfType(model, shapes, SH.SPARQLConstraint);

        String className = "ShaclShapes";
        String outFilename = "java/src/test/java/com/ncc/aif/" + className + ".java";
        
        try {
            OutputStream stream = Files.newOutputStream(Paths.get(outFilename),
                    StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
            PrintStream out = new PrintStream(stream,true);
            out.println(getHeader("This class contains variables generated from SHACL files using the OntologyGeneration class"));
            out.println("public final class " + className + " {");

            String indent = "    ";
            out.println(indent + "public static final String NS = InterchangeOntology.NAMESPACE;");
            for (Resource resource : shapes) {
                String name = resource.getLocalName();
                out.println(indent + "public static final Resource " + name + " = ResourceFactory.createResource(NS + \"" + name + "\");");
            }
            out.println("}");
            out.close();
        } catch (IOException e) {
            System.err.println("Unable to write to " + outFilename);
            e.printStackTrace();
        }
    }

    private static void addShapesOfType(Model model, Set<Resource> shapes, Resource type) {
        model.listSubjectsWithProperty(RDF.type, type)
                .filterKeep(Resource::isURIResource)
                .forEachRemaining(shapes::add);
    }

    private List<String> getPythonLines() {
        List<String> lines = new ArrayList<>();
        Stream.of(getPythonHeader().split("\n")).forEach(lines::add);
        lines.add("NAMESPACE = '" + ontology.toString() + "#'");
        lines.add("\n# Classes");
        lines.addAll(getSortedSetForPython(classes));
        
        if (!individuals.isEmpty()) {
            lines.add("\n# Individuals");
            lines.addAll(getSortedSetForPython(individuals));
        }
        
        if (!properties.isEmpty()) {
            lines.add("\n# Properties");
            lines.addAll(getSortedSetForPython(properties));
        }
        return lines;
    }

    private static void writePythonFile(String ontologyLocation) {
        try {
            System.out.println("Generating for ontology located at " + ontologyLocation);
            OntologyGeneration ctx = new OntologyGeneration(new FileInputStream(ontologyLocation));
            Path file = Paths.get("python/aida_interchange/rdf_ontologies/" + camelToSnake(ctx.ontology.getLocalName()) + ".py");
            Files.write(file, ctx.getPythonLines(), Charset.forName("UTF-8"));
        } catch (IOException e) {
            System.err.println("Unable to process file " + ontologyLocation);
            e.printStackTrace();
        }
    }

    // Writes the generated Java classes
    private static void writeJavaFile(String ontologyLocation) {
        try {
            System.out.println("Generating for ontology located at " + ontologyLocation);
            OntologyGeneration ctx = new OntologyGeneration(new FileInputStream(ontologyLocation));

            String ontologyName = ctx.ontology.getLocalName();
            List<String> lines = ctx.owgMapperInfo(ontologyName);
            Path file = Paths.get("java/src/main/java/com/ncc/aif/" + ontologyName + ".java");
            Files.write(file, lines, Charset.forName("UTF-8"));
        } catch (IOException e) {
            System.err.println("Unable to process file " + ontologyLocation);
            e.printStackTrace();
        }
    }

    // Writes the static variables for each ontology class
    private List<String> owgMapperInfo(String variableClassName) {
        String comment = "This class contains variables generated from ontologies using the OntologyGeneration class";
        List<String> owgMapping = new ArrayList<>();
        Stream.of(getHeader(comment, !properties.isEmpty()).split("\n")).forEach(owgMapping::add);;

        owgMapping.add("public final class " + variableClassName + " {");

        owgMapping.add("    public static final String NAMESPACE = \"" + ontology.toString() + "#\";");

        owgMapping.add("    // Classes");
        owgMapping.addAll(getSortedSet(classes, "Resource"));
        
        if (!individuals.isEmpty()) {
            owgMapping.add("\n    // Individuals");
            owgMapping.addAll(getSortedSet(individuals, "Resource"));
        }
        
        if (!properties.isEmpty()) {
            owgMapping.add("\n    // Properties");
            owgMapping.addAll(getSortedSet(properties, "Property"));
        }

        owgMapping.add("}");
        return owgMapping;
    }

    private <T extends Resource> SortedSet<String> getSortedSetForPython(List<T> resources) {
        SortedSet<String> members = new TreeSet<>();
        for (T resource : resources) {
            String localName = resource.getLocalName();
            String label = localName.replace(".", "_").replace("-", "_");
            members.add(String.format("%s = URIRef(NAMESPACE + '%s')", label, localName));
        }
        return members;
    }

    private static String camelToSnake(String camelCase) {
        return String.join("_", camelCase.split("((?<=[A-Z])(?=[A-Z][a-z]))|((?<=[a-z])(?=[A-Z]))")).toLowerCase();
    }

    private <T extends Resource> SortedSet<String> getSortedSet(List<T> resources, String type) {
        SortedSet<String> members = new TreeSet<>();
        for (T resource : resources) {
            members.add(String.format(
                "    public static final %s %s = ResourceFactory.create%s(NAMESPACE + \"%s\");",
                type,
                resource.getLocalName().replace(".", "_").replace("-", "_"),
                type,
                resource.getLocalName()));
        }
        return members;
    }

    private static String getPythonHeader() {
        return  "from rdflib import URIRef\n\n" +
                getWarning(null, "#");
    }

    private static String getWarning(String comment, String commentChar) {
        return  commentChar + " WARNING. This is a Generated File. Please do not edit.\n" +
                (comment == null ? "" : commentChar + " " + comment + "\n") +
                commentChar + " Please refer to the README at java/src/main/java/com/ncc/aif/ont2javagen for more information\n" +
                commentChar + " Last generated on: " + new SimpleDateFormat("MM/dd/yyy HH:mm:ss").format(new Date());
    }

    private static String getHeader(String comment, boolean hasProperties) {
        return "package com.ncc.aif;\n\n" +
                (hasProperties ? "import org.apache.jena.rdf.model.Property;\n" : "") +
                "import org.apache.jena.rdf.model.Resource;\n" +
                "import org.apache.jena.rdf.model.ResourceFactory;\n" +
                getWarning(comment, "//");
    }
    private static String getHeader(String comment) {
        return getHeader(comment, false);
    }
}
