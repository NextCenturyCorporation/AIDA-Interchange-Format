package com.ncc.aif;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.RDFDataMgr;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class ValidatorPerformanceTest {
    private static final String ROOT = "../VERDI/MockTA1Algo/R103.Sample.2019.05.14/";
    private static final String KB = ROOT + "R103.kb.ttl";
    private static final String SMALL_TA1 = ROOT + "IC00120RO.parent.ttl";
    private static final String BIG_TA1 = ROOT + "IC0011WX8.parent.ttl";
    private static final String NORMAL_TAG = "normal";
    private static final String THREAD_TAG = "threaded";

    public static void main(String[] args) {
        ((Logger) org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME)).setLevel(Level.INFO);
        new ValidatorPerformanceTest(1).runAgainstFile(BIG_TA1, 3);
        new ValidatorPerformanceTest(2).runAgainstFile(BIG_TA1, 3);
        new ValidatorPerformanceTest(4).runAgainstFile(BIG_TA1, 3);
    }

    private ValidateAIF normal;
    private ValidateAIF threaded;
    public ValidatorPerformanceTest(int threadCount) {
        normal = ValidateAIF.createForLDCOntology(ValidateAIF.Restriction.NIST);
        threaded = ValidateAIF.createForLDCOntologyWithThreads(ValidateAIF.Restriction.NIST, threadCount);
    }


    void runAgainstFile(String file, int runs) {
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, file);

        Function<ValidateAIF, Long> getDuration = toTest -> {
            long start = System.currentTimeMillis();
            toTest.validateKBAndReturnReport(model);
            return System.currentTimeMillis() - start;
        };

        System.out.println(file);

        List<Map<ValidateAIF, Long>> results = new LinkedList<>();
        for (int i = 0; i < runs; i++) {
            Map<ValidateAIF, Long> runResult = new HashMap<>();
            runResult.put(normal, getDuration.apply(normal));
            runResult.put(threaded, getDuration.apply(threaded));
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
}
