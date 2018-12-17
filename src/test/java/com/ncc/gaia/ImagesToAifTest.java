package com.ncc.gaia;

import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import edu.isi.nlp.parameters.Parameters;
import kotlin.text.Charsets;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.util.FileUtils;
import org.apache.jena.vocabulary.RDF;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;

import static com.google.common.collect.Iterators.size;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ImagesToAifTest {

  @Test
  void testImagesToAif() throws IOException {
    final File tempDir = Files.createTempDir();
    tempDir.deleteOnExit();

    final File input_tab_path = new File(tempDir, "input.tab");
    final File outputDir = new File(tempDir, "output_dir");
    final File paramFile = new File(tempDir, "param_file.params");

    final Parameters params = Parameters.fromMap(ImmutableMap.of(
        "input_tabular_file", input_tab_path.getAbsolutePath(),
        "output_directory", outputDir.getAbsolutePath()
    ));

    Files.asCharSink(input_tab_path, Charsets.UTF_8).write(
        Resources.asCharSource(Resources.getResource("edu/isi/gaia/images_to_aif_sample.txt"),
            Charsets.UTF_8).read());

    ImagesToAIF.run(params);

    // the tests below could be made more extensive - right now they only check there is an
    // entity and event generated
    assertFoo123Matches(new File(outputDir, "FOO123.ttl"));
    assertBar321Matches(new File(outputDir, "BAR321.ttl"));

    final String LIST_REFERENCE = String.format("%s\n%s\n",
        new File(outputDir, "FOO123.ttl").getAbsolutePath(),
        new File(outputDir, "BAR321.ttl").getAbsolutePath());

    final String MAP_REFERENCE = String.format("FOO123\t%s\nBAR321\t%s\n",
        new File(outputDir, "FOO123.ttl").getAbsolutePath(),
        new File(outputDir, "BAR321.ttl").getAbsolutePath());

    assertEquals(LIST_REFERENCE, Files.asCharSource(new File(outputDir, "images_to_aif.list.txt"),
        Charsets.UTF_8).read());
    assertEquals(MAP_REFERENCE, Files.asCharSource(new File(outputDir, "images_to_aif.map.txt"),
        Charsets.UTF_8).read());
  }


  private void assertFoo123Matches(File turtleFile) throws IOException {
    final Model testModel = loadTurtle(turtleFile);
    assertEquals(1,
        size(testModel.listResourcesWithProperty(RDF.type, AidaAnnotationOntology.ENTITY_CLASS)));
  }

  private void assertBar321Matches(File turtleFile) throws IOException {
    final Model testModel = loadTurtle(turtleFile);
    assertEquals(1,
        size(testModel.listResourcesWithProperty(RDF.type, AidaAnnotationOntology.EVENT_CLASS)));
  }

  private static Model loadTurtle(File turtleFile) throws IOException {
    final Model turtleModel = ModelFactory.createDefaultModel();
    try (BufferedReader in = Files.asCharSource(turtleFile, Charsets.UTF_8).openBufferedStream()) {
      turtleModel.read(in, "urn:x-base", FileUtils.langTurtle);
    }
    return turtleModel;
  }
}
