package com.ncc.aif;

import ch.qos.logback.classic.Logger;
import com.google.common.collect.Maps;
import org.topbraid.jenax.progress.NullProgressMonitor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * Thread-safe progress monitor for AIF validation.  Creating multiple instances with the same name will write to the
 * same file in a thread-safe manner.
 * Note that this class is implemented based on the way the TopBraid validator engine sends progress monitor updates.
 * Writes a tab-delimited file in the following format:
 * <code>Thread | Shape# | Shape Name | Start Time | End Time | Duration (ms)</code>
 * <code>main | 1 | sh:MinExclusiveConstraintComponent | t1 | t2 | 25ms<code>
 * <code>Thread-2 | 2 | sh:NotConstraintComponent | t1 | t2 | 1225ms<code>
 */
class AIFProgressMonitor extends NullProgressMonitor {
    private static final boolean LOGGING = true;
    private static final Logger logger = (Logger) (org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME));
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");
    private static final int LOGGING_THRESHOLD = 1000;
    private static final Map<String, BufferedWriter> fileMap = Maps.newConcurrentMap();
    private BufferedWriter out;
    private Map<Integer, String> shapeNameMap;
    private Map<Integer, Long> shapeTimeMap;
    private int shapeNum;
    private int numShapes;

    AIFProgressMonitor(String name) throws IOException {
        shapeNameMap = Maps.newConcurrentMap();
        shapeTimeMap = Maps.newConcurrentMap();

        if (fileMap.containsKey(name)) {
            out = fileMap.get(name); // reuse the file
        } else { // create the file and store the mapping
            out = createOutfile(name);
            fileMap.put(name, out);
        }
    }

    // Create a BufferedWriter in which writes are performed synchronously
    private BufferedWriter createOutfile(String name) throws IOException {
        // Create new file with basename based on name
        final String filename = name.replace(".ttl", "-progress.tab");
        log("Creating file " + filename + " for logging validation progress.");
        final Path outputPath = Paths.get(filename);
        Files.deleteIfExists(outputPath);
        Files.createFile(outputPath);
        // Supplying the DSYNC option requires that the file already exists.
        return Files.newBufferedWriter(outputPath, StandardOpenOption.DSYNC);
    }

    private static void log(String text) {
        if (LOGGING) {
            logger.info(text);
        }
    }

    @Override
    public void beginTask(String label, int numShapes) {
        log("Beginning task " + label + " (" + numShapes + ")");
        this.numShapes = numShapes;
        this.shapeNum = 0;
        log("Logging to file number of shapes: " + this.numShapes);
        try {
            out.write("Total: " + this.numShapes + "\n");
            out.write("Thread\tShape#\tShape Name\tStart Time\tEnd Time\tDuration (ms)\n");
            out.flush();
        } catch (IOException ioe) {
            System.err.println("Could not write to progress monitor.");
        }
    }

    @Override
    public void done() {
        // As of this writing, this doesn't actually get called.  If it did, we could close the BufferedWriter here.
        log("DONE!");
    }

    // Note that this depends on the format of the label that TopBraid's validator engine sends to progress monitors.
    // e.g., label = "Shape 3: sh:DerivedValuesConstraintComponent"
    @Override
    public void subTask(String label) {
        log("Validating " + label);
        final String[] parts = label.split(" ");
        final int shapeNum = Integer.parseUnsignedInt(parts[1].replaceFirst(":", ""));
        final String shapeName = parts[2];
        shapeNameMap.put(shapeNum, shapeName);
        shapeTimeMap.put(shapeNum, System.currentTimeMillis());
    }

    @Override
    public void worked(int amount) {
        final Date endTime = new Date();
        shapeNum += amount;
        log("Worked " + shapeNum + " / " + numShapes);
        final String shapeName = shapeNameMap.get(shapeNum);
        final long startTimeMs = shapeTimeMap.get(shapeNum);
        final long duration = endTime.getTime() - startTimeMs;

        if (duration > LOGGING_THRESHOLD) {
            log("  Duration: " + duration + "ms");
            log("  Timestamp: " + FORMAT.format(endTime));
        }
        // Write a row of the file.
        try {
            final String row =
                    Thread.currentThread().getName() + "\t" +
                            shapeNum + "\t" +
                            shapeName + "\t" +
                            FORMAT.format(new Date(startTimeMs)) + "\t" +
                            FORMAT.format(endTime) + "\t" +
                            duration + "\n";
            out.write(row);
            out.flush();
            if (shapeNum >= numShapes) {
                out.close();
            }
        } catch (IOException ioe) {
            logger.error("Could not write to progress monitor.");
        }
    }
}
