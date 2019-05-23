package com.ncc.aif;

import ch.qos.logback.classic.Logger;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.topbraid.jenax.progress.NullProgressMonitor;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.Queue;

/**
 * Progress monitor for AIF validation.
 * Note that this class is implemented based on the way the TopBraid validator engine sends progress monitor updates.
 * If a filename is provided, it writes a tab-delimited file in the following format:
 * <code>Shape# | Shape Name | Start Time | End Time | Duration (ms)</code>
 * <code>1 | sh:MinExclusiveConstraintComponent | t1 | t2 | 25ms<code>
 * <code>2 | sh:NotConstraintComponent | t1 | t2 | 1225ms<code>
 * <p>
 * Otherwise, it writes progressive output to StdOut.
 */
class AIFProgressMonitor extends NullProgressMonitor {
    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("HH:mm:ss.SSS");
    private static final int LOGGING_THRESHOLD = 500;
    private final Logger logger = (Logger) (org.slf4j.LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME));
    private boolean logging = false;
    private BufferedWriter out;
    private Queue<ImmutablePair<String, Long>> shapeList = new LinkedList<>();
    private int shapeNum;
    private int numShapes;

    /**
     * Create a progress monitor that logs validation progress to StdOut.
     */
    AIFProgressMonitor() {
        logging = true;
        out = null;
    }

    /**
     * Create a progress monitor that logs validation progress to the specified filename.
     * @param filename the filename to output validation progress
     * @throws IOException if the file cannot be created.
     */
    AIFProgressMonitor(String filename) throws IOException {
        log("Creating file " + filename + " for logging validation progress.");
        out = Files.newBufferedWriter(Paths.get(filename));
    }

    private void log(String text) {
        if (logging) {
            logger.info(text);
        }
    }

    @Override
    public void beginTask(String label, int numShapes) {
        log("Beginning task " + label + " (" + numShapes + ")");
        this.numShapes = numShapes;
        this.shapeNum = 0;
        log("Logging to file number of shapes: " + this.numShapes);
        if (out != null) {
            try {
                out.write("Total: " + this.numShapes + "\n");
                out.write("Shape#\tShape Name\tStart Time\tEnd Time\tDuration (ms)\n");
                out.flush();
            } catch (IOException ioe) {
                System.err.println("Could not write to progress monitor.");
            }
        }
    }

    @Override
    public void done() {
        // Currently, this doesn't actually get called by TopBraid.  If it did, we could close the BufferedWriter here.
        log("DONE!");
    }

    // Note that this depends on the format of the label that TopBraid's validator engine sends to progress monitors.
    // e.g., label = "Shape 3: sh:DerivedValuesConstraintComponent"
    @Override
    public void subTask(String label) {
        log("Validating " + label);
        shapeList.add(new ImmutablePair<>(label.split(" ")[2], System.currentTimeMillis()));
    }

    @Override
    public void worked(int amount) {
        final Date endTime = new Date();
        shapeNum += amount;
        log("Completed shape " + shapeNum + " / " + numShapes);
        final ImmutablePair<String, Long> pair = shapeList.remove();
        final String shapeName = pair.getKey();
        final long startTimeMs = pair.getValue();
        final long duration = endTime.getTime() - startTimeMs;

        if (duration > LOGGING_THRESHOLD) {
            log("  Duration: " + duration + "ms");
            log("  Timestamp: " + FORMAT.format(endTime));
        }
        // Write a row of the file.
        if (out != null) {
            try {
                final String row = shapeNum + "\t" +
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

    // Simulate a validator invoking both variants of the AIFProgressMonitor
    public static void main(String[] args) {
        AIFProgressMonitor fileMonitor, stdoutMonitor;
        final java.util.Random r = new java.util.Random();
        final String FILENAME = "test-progress.tab";
        final String SHAPES[] = new String[]{"FOO", "BAR", "BAZ", "FEE", "FI", "FO", "FUM"};
        final int numShapes = SHAPES.length;
        System.out.println("Test file- and stdout-based AIFProgressMonitors.");
        System.out.println("Check " + FILENAME + "for file-based results.\n");
        try {
            fileMonitor = new AIFProgressMonitor(FILENAME);
            stdoutMonitor = new AIFProgressMonitor();
            fileMonitor.beginTask("Test validation", numShapes);
            stdoutMonitor.beginTask("Test validation", numShapes);
            for (int i = 1; i <= numShapes; i++) {
                fileMonitor.subTask("Shape " + i + ": " + SHAPES[i - 1]);
                stdoutMonitor.subTask("Shape " + i + ": " + SHAPES[i - 1]);
                Thread.sleep(r.nextInt(1000));
                fileMonitor.worked(1);
                stdoutMonitor.worked(1);
            }
            fileMonitor.done();
            stdoutMonitor.done();
        } catch (Exception e) {
            System.err.println("AIFProcessMonitor unit test error.");
            e.printStackTrace();
        }
    }
}
