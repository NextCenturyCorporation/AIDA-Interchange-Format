package com.ncc.aif.OntToJavaGen;

import org.apache.jena.ontology.OntModel;
import org.apache.jena.ontology.OntClass;
import org.apache.jena.ontology.OntModelSpec;
import org.apache.jena.ontology.OntDocumentManager;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.util.FileUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.ArrayList;
import java.util.Iterator;

/**
 * Generates java resources  from an ontology
 */
public class OntologyGeneration {

    private OntModel ontModel;
    private List classesToGenerate;
    private static List<OWGClass> owgClassList = new ArrayList<>();


    private OntologyGeneration() {
        this.ontModel = this.createOntModel(false);
        this.classesToGenerate = new ArrayList();
    }


    /**
     * Reads in ontology File
     * @param ontologyFile
     */
    private void addOntologyToGenerate(InputStream ontologyFile) {
        OntModel temp = this.createOntModel(false);
        temp.read(ontologyFile, "urn:x-base", FileUtils.langTurtle);

        this.ontModel.add(temp);
        this.classIterateOntology(temp);
    }

    /**
     * Iterates through new ontology model to obtain the list of classes
     * @param tempont
     */
    private void classIterateOntology(OntModel tempont) {
        Iterator it = tempont.listClasses();

        while(it.hasNext()) {
            OntClass oc = (OntClass)it.next();
            if (!oc.isAnon()) {
                setResources(oc.getURI(), oc.getLocalName());
                this.classesToGenerate.add(oc.getURI());
            }
        }
    }


    /**
     *
     * @param processImports
     * @return
     */
    private OntModel createOntModel(boolean processImports) {
        OntModelSpec s = new OntModelSpec(OntModelSpec.OWL_MEM);

        OntModel ontModel = ModelFactory.createOntologyModel(s,null);
        OntDocumentManager dm = ontModel.getDocumentManager();
        dm.setProcessImports(processImports);
        return ontModel;
    }

    private void setResources(String uri, String resourceName) {
        OWGClass owgClass = new OWGClass(uri, resourceName);
        owgClassList.add(owgClass);

    }

    public static void main(String[] args) throws IOException {

        OntologyGeneration ctx = new OntologyGeneration();

        ctx.addOntologyToGenerate(new FileInputStream(args[0]));

        owgMapperWriter();
    }

    private static void owgMapperWriter() throws IOException {

        OWGClass test = owgClassList.get(0);
        String classNameTest = test.uri;
        System.out.println(classNameTest);
        String variableClassName = classNameTest.substring(classNameTest.lastIndexOf('/') + 1, classNameTest.indexOf('#'));

     //   String variableClassName = "hello";
        List<String> lines = owgMapperInfo(variableClassName);
        Path file = Paths.get("src/main/java/com/ncc/aif/" + variableClassName + ".java");
        Files.write(file, lines, Charset.forName("UTF-8"));
    }

    private static List<String> owgMapperInfo(String variableClassName) {

        List<String> owgMapping = new ArrayList<>();

        owgMapping.addAll(owgMapperHeader());

        String className = "public final class " + variableClassName + " {";
        owgMapping.add(className);

        for (OWGClass owgClass : owgClassList) {

            String name = owgClass.getName();

            if (name.contains(".") || name.contains("-")) {
                name = name.replace(".", "_");
                name = name.replace("-", "_");
            }

            String resource = "    public static final Resource " + name + " = ResourceFactory.createResource(\"" + owgClass.getUri() + "\");";
            owgMapping.add(resource);
        }

        String ending = "}";
        owgMapping.add(ending);

        return owgMapping;
    }

    private static List<String> owgMapperHeader () {

        String lineEmpty = "";
        String lineOne = "package com.ncc.aif;";
        String lineThree = "import org.apache.jena.rdf.model.Resource;";
        String lineFour = "import org.apache.jena.rdf.model.ResourceFactory;";

        List<String> headerStrings = new ArrayList<>();

        headerStrings.add(lineOne);
        headerStrings.add(lineEmpty);
        headerStrings.add(lineThree);
        headerStrings.add(lineFour);
        headerStrings.add(lineEmpty);

        return headerStrings;
    }

    public class OWGClass {
        private String name;
        private String uri;

        private OWGClass (String uri, String name) {
            this.name = name;
            this.uri = uri;
        }

        public String getName() {
            return name;
        }

        public String getUri() {
            return uri;
        }
    }

}
