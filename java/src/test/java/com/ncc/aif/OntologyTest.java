package com.ncc.aif;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.google.common.io.Resources;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Property;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestInstance.Lifecycle;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;

//TODO: consider removing this now that InterchangeOntology.java can be generated.
@TestInstance(Lifecycle.PER_CLASS)
public class OntologyTest {

    @BeforeAll
    static void initTest() {
        // prevent too much logging from obscuring the Turtle examples which will be printed
        ((Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
    }

    @Test
    void aifOntology() throws IOException, IllegalAccessException {
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model,
                Resources.getResource("com/ncc/aif/ontologies/InterchangeOntology").openStream(),
                Lang.TURTLE);

        Set<Class> classesToCheck = new HashSet<>(Arrays.asList(Property.class, Resource.class));
        boolean invalid = false;
        for (Field field : InterchangeOntology.class.getDeclaredFields()) {
            if (classesToCheck.contains(field.getType())) {
                Resource toTest = (Resource)field.get(InterchangeOntology.class);
                if (!model.contains(toTest, RDF.type)) {
                    System.out.println(toTest.getURI());
                    invalid = true;
                }
            }
        }
        assertFalse(invalid, "Members from InterchangeOntology.java are undefined in InterchangeOntology");
    }
}
