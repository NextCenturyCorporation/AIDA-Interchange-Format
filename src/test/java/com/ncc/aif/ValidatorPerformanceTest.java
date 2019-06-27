package com.ncc.aif;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.apache.commons.io.FileUtils;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.function.BiConsumer;

//TODO: remove me when multi-threaded progress monitor is added
public class ValidatorPerformanceTest {
    // Requires that LDC TA2 output be created in MockTA1Algo.
    private static final String ROOT = "../VERDI/MockTA1Algo/R103/";
    private static final String KB = ROOT + "R103.kb.ttl";
    // Requires that LDC TA1 output be created in MockTA1Algo
    private static final String SMALL_TA1 = ROOT + "IC00120RO.parent.ttl";
    private static final String MEDIUM_TA1 = ROOT + "IC0011WX8.parent.ttl";
    private static final String BIG_TA1 = ROOT + "IC0011TIJ.parent.ttl";

    public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {
        ((Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        ValidatorPerformanceTest test = new ValidatorPerformanceTest(4);
        test.runAgainstFile(KB, 1, false, false);
    }

    private ValidateAIF normal;
    private ValidateAIF threaded;
    public ValidatorPerformanceTest(int threadCount) {
        normal = ValidateAIF.createForLDCOntology(ValidateAIF.Restriction.NIST);
        threaded = ValidateAIF.createForLDCOntology(ValidateAIF.Restriction.NIST);
        threaded.setThreadCount(threadCount);
    }

    void runAgainstFile(String file, int runs, boolean runBoth, boolean multiple) throws IOException {
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, file);

        System.out.println(file + "(" + FileUtils.byteCountToDisplaySize(new File(file).length()) + ")");

        List<Map<ValidateAIF, Long>> results = new LinkedList<>();
        for (int i = 0; i < runs; i++) {
            Map<ValidateAIF, Long> runResult = new HashMap<>();
            if (runBoth) {
                runResult.put(normal, getDuration(normal, model, "normal", multiple));
            }
            runResult.put(threaded, getDuration(threaded, model, "threaded", multiple));
            results.add(runResult);
        }
        threaded.getExecutor().shutdown();

        if (runBoth) {
            long normalAvg = 0;
            long threadAvg = 0;
            for (Map<ValidateAIF, Long> result : results) {
                long normalRes = result.get(normal);
                normalAvg += normalRes;
                long threadRes = result.get(threaded);
                threadAvg += threadRes;
                System.out.println("n:" + normalRes + "\tt:" + threadRes);
            }
            System.out.println("nAvg:" + normalAvg / runs + "\ttAvg:" + threadAvg / runs);
        }

        threaded.printMetrics(System.out);
    }

    private static long getDuration(ValidateAIF toTest, Model model, String output, boolean multiple) throws IOException {
        long start = System.currentTimeMillis();
        long ret;
        BiConsumer<Resource, String> printReport = (report, filename) -> {
            try (OutputStream out = Files.newOutputStream(Paths.get(filename))) {
                RDFDataMgr.write(out, report.getModel(), Lang.TURTLE);
            } catch (IOException e) {
                e.printStackTrace();
            }
        };

        if (multiple) {
            Set<Resource> reports = toTest.validateKBAndReturnMultipleReports(model, null);
            ret = System.currentTimeMillis() - start;
            int i = 1;
            for (Resource report : reports) {
                printReport.accept(report, output + "-" + i++ + ".ttl");
            }
        } else {
            Resource report = toTest.validateKBAndReturnReport(model);
            ret = System.currentTimeMillis() - start;
            printReport.accept(report, output + ".ttl");
        }
        return ret;
    }
}
