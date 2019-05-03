package edu.isi.gaia

import org.apache.jena.rdf.model.Model
import org.apache.jena.tdb.TDBFactory
import org.slf4j.LoggerFactory
import java.util.*

/**
 * A convenient interface for creating simple AIF graphs.
 *
 * More complicated graphs will require direct manipulation of the RDF.
 *
 * @author Ryan Gabbard (USC ISI)
 */
object ISIAIFUtils {
    data class Point(val x: Int, val y: Int) {
        init {
            require(x >= 0) { "Aida image/video coordinates must be non-negative but got $x" }
            require(y >= 0) { "Aida image/video coordinates must be non-negative but got $y" }
        }
    }

    data class BoundingBox(val upperLeft: Point, val lowerRight: Point) {
        init {
            require(upperLeft.x <= lowerRight.x && upperLeft.y <= lowerRight.y) {
                "Upper left of bounding box $upperLeft not above and to the left of lower right $lowerRight"
            }
        }
    }

    /**
     * Run a task on a model when the model might grown too big to fit into memory.
     *
     * This hides the setup and cleanup boilerplate for using a Jena TDB model backed by
     * a temporary directory.
     */
    @JvmStatic
    fun workWithBigModel(workFunction: (Model) -> Unit) {
        val tempDir = createTempDir()
        try {
            LoggerFactory.getLogger("main").info("Using temporary directory $tempDir for " +
                    "triple store")
            val dataset = TDBFactory.createDataset(tempDir.absolutePath)
            val model = dataset.defaultModel
            workFunction(model)
        } finally {
            tempDir.deleteRecursively()
        }
    }
}

/**
A strategy for generating RDF graph nodes
 */
interface IriGenerator {
    fun nextIri(): String
}

/**
 *     A node generation strategy which uses UUIDs appended to a base URI.
 */
class UuidIriGenerator(private val baseUri: String = "dummy:uri") : IriGenerator {
    init {
        require(baseUri.isNotEmpty()) { "Base URI cannot be empty" }
        require(baseUri.substring(1).contains(":")) { 
            "Base URI must contain a prefix followed by a colon separator" }
        require(!baseUri.endsWith("/")) { "Base URI cannot end in /" }
    }

    override fun nextIri(): String {
        return baseUri + '/' + UUID.randomUUID().toString()
    }
}
