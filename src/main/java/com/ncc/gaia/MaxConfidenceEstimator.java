package com.ncc.gaia;

import com.google.common.io.Files;
import edu.isi.nlp.parameters.Parameters;
import mu.KLogging;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.RDFNode;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFFormat;
import java.io.File;
import java.nio.charset.StandardCharsets;

// this is an example of some code which works with the AIF format as both a consumer
// and producer.  It takes AIF as input and, for each assertion which has an attached justification
// with a confidence, sets the confidence of that assertion to the maximum of any justification.

interface ConfidenceRestimator {
    void reestimateConfidencesInPlace(Model model);
}

public class MaxConfidenceEstimator implements ConfidenceRestimator {
    public void reestimateConfidencesInPlace(Model model) {
        Resource system = AIFUtils.makeSystemWithURI(model,
                "http://www.isi.edu/algorithms/maxConfidence")

        for (resourceWithJustification in model.subjectsWithProperty(AidaAnnotationOntology.JUSTIFIED_BY)) {

            val mostConfidentJustification =
                    model.objectsWithProperty(resourceWithJustification, AidaAnnotationOntology.JUSTIFIED_BY)
                            .map({ confidenceForJustification(it) to it })
                            .maxBy { it.first }!!.second

            AIFUtils.markConfidence(model, resourceWithJustification,
                    confidenceForJustification(mostConfidentJustification), system)
        }
    }

    private Double confidenceForJustification(RDFNode justification) {
        // we can make assumptions about the object of justified by
        // because we assume this model passes validation

        return justification.asResource().getProperty(AidaAnnotationOntology.CONFIDENCE)
                .getObject().asResource().getProperty(AidaAnnotationOntology.CONFIDENCE_VALUE)
                .getLiteral().getDouble();
    }

    companion object : KLogging() {
        @JvmStatic
        fun main(args: Array<String>) {
            if (args.size != 1) {
                System.err.println("usage: maxConfidence param_file\n" +
                        "\tparams expected:\n" +
                        "\t\tinputKBFile: an input KB in n-triples format\n" +
                        "\t\toutputKBFile: path to write output KB in n-triples format\n")
                System.exit(1)
            }

            val params = Parameters.loadSerifStyle(File(args[0]))
            val inputKBFile = params.getExistingFile("inputKBFile")
            val outputKBFile = params.getCreatableFile("outputKBFile")

            AIFUtils.workWithBigModel {
                val model = it
                logger.info { "Loading KB from $inputKBFile" }
                Files.asCharSource(inputKBFile, Charsets.UTF_8).openBufferedStream().use {
                    model.read(it, null, "N-TRIPLES")
                }

                logger.info { "Estimating confidences" }
                MaxConfidenceEstimator().reestimateConfidencesInPlace(model)

                logger.info { "Writing output KB to $outputKBFile" }
                outputKBFile.bufferedWriter(StandardCharsets.UTF_8).use {
                    // deprecation is OK because Guava guarantees the writer handles the charset properly
                    @Suppress("DEPRECATION")
                    RDFDataMgr.write(it, model, RDFFormat.NTRIPLES)
                }
            }
        }
    }
}

