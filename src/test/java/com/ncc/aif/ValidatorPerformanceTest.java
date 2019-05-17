package com.ncc.aif;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class ValidatorPerformanceTest {
    private static final String ROOT = "../VERDI/MockTA1Algo/";
    private static final String SAMPLE = ROOT + "R103.Sample.2019.05.14/";
    private static final String KB = SAMPLE + "R103.kb.ttl";
    private static final String SMALL_TA1 = SAMPLE + "IC00120RO.parent.ttl";
    private static final String MEDIUM_TA1 = SAMPLE + "IC0011WX8.parent.ttl";
    private static final String BIG_TA1 = ROOT + "R103.2019.05.14/IC0011TIJ.parent.ttl";
    private static final String GAIA_KB = "/home/HQ/ecurley/Documents/AIDA/2019.05.17-AIDA-795-Threading/big-gaia-sansprivate.ttl";
    private static final String NORMAL_TAG = "normal";
    private static final String THREAD_TAG = "threaded";

    public static void main(String[] args) throws InterruptedException, ExecutionException, IOException {
        ((Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        ValidatorPerformanceTest test = new ValidatorPerformanceTest(4);
        test.runAgainstFile(GAIA_KB, 1);
        test.printFutures();
    }

    private ValidateAIF normal;
    private ValidateAIF threaded;
    public ValidatorPerformanceTest(int threadCount) {
        normal = ValidateAIF.createForLDCOntology(ValidateAIF.Restriction.NIST);
        threaded = ValidateAIF.createForLDCOntologyWithThreads(ValidateAIF.Restriction.NIST, threadCount);
    }

    void runAgainstFile(String file, int runs) throws IOException {
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, file);

        System.out.println(file);

        List<Map<ValidateAIF, Long>> results = new LinkedList<>();
        for (int i = 0; i < runs; i++) {
            Map<ValidateAIF, Long> runResult = new HashMap<>();
            runResult.put(normal, getDuration(normal, model, "normal.out.ttl"));
            runResult.put(threaded, getDuration(threaded, model, "threaded.out.ttl"));
            results.add(runResult);
        }
        threaded.getExecutor().shutdown();

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

    private static long getDuration(ValidateAIF toTest, Model model, String output) throws IOException {
            long start = System.currentTimeMillis();
            Resource result = toTest.validateKBAndReturnReport(model);
            long ret = System.currentTimeMillis() - start;
            RDFDataMgr.write(Files.newOutputStream(Paths.get(output)), result.getModel(), Lang.TURTLE);
            return ret;
    }

    void printFutures() throws InterruptedException, ExecutionException {
        // gather by thread
        Map<String, List<ThreadedValidationEngine.ValidationMetadata>> threads = new HashMap<>();
        for (Future<ThreadedValidationEngine.ValidationMetadata> future : threaded.getValidationMetadata()) {
            ThreadedValidationEngine.ValidationMetadata md = future.get();
            threads.computeIfAbsent(md.threadName, key -> new LinkedList<>()).add(md);
        }

        // print out by thread
        for (List<ThreadedValidationEngine.ValidationMetadata> list : threads.values()) {
            long sum = 0;
            for (ThreadedValidationEngine.ValidationMetadata md : list) {
                sum += md.duration;
            }
            System.out.println(list.get(0).threadName + ": " + sum + "(" + list.size() +")");
        }
    }
}
