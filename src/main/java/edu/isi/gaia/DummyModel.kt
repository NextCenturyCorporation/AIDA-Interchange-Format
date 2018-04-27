package edu.isi.gaia

import org.apache.jena.datatypes.RDFDatatype
import org.apache.jena.graph.Graph
import org.apache.jena.graph.Triple
import org.apache.jena.rdf.model.*
import org.apache.jena.shared.Command
import org.apache.jena.shared.Lock
import org.apache.jena.shared.PrefixMapping
import java.io.InputStream
import java.io.OutputStream
import java.io.Reader
import java.io.Writer
import java.util.*
import java.util.function.Supplier

internal class DummyResource : Resource {
    override fun visitWith(p0: RDFVisitor?): Any {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun asResource(): Resource {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun hasURI(p0: String?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun inModel(p0: Model?): Resource {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getId(): AnonId {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun commit(): Resource {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun begin(): Resource {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getProperty(p0: Property?): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getProperty(p0: Property?, p1: String?): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : RDFNode?> `as`(p0: Class<T>?): T {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getModel(): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getURI(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun abort(): Resource {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun removeAll(p0: Property?): Resource {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun asLiteral(): Literal {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun hasLiteral(p0: Property?, p1: Boolean): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun hasLiteral(p0: Property?, p1: Long): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun hasLiteral(p0: Property?, p1: Char): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun hasLiteral(p0: Property?, p1: Double): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun hasLiteral(p0: Property?, p1: Float): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun hasLiteral(p0: Property?, p1: Any?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun asNode(): org.apache.jena.graph.Node {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun removeProperties(): Resource {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isAnon(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getLocalName(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listProperties(p0: Property?): StmtIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listProperties(p0: Property?, p1: String?): StmtIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listProperties(): StmtIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getPropertyResourceValue(p0: Property?): Resource {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addProperty(p0: Property?, p1: String?): Resource {
        return this;
    }

    override fun addProperty(p0: Property?, p1: String?, p2: String?): Resource {
        return this;
    }

    override fun addProperty(p0: Property?, p1: String?, p2: RDFDatatype?): Resource {
        return this;
    }

    override fun addProperty(p0: Property?, p1: RDFNode?): Resource {
        return this;
    }

    override fun isLiteral(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isResource(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun hasProperty(p0: Property?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun hasProperty(p0: Property?, p1: String?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun hasProperty(p0: Property?, p1: String?, p2: String?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun hasProperty(p0: Property?, p1: RDFNode?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addLiteral(p0: Property?, p1: Boolean): Resource {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addLiteral(p0: Property?, p1: Long): Resource {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addLiteral(p0: Property?, p1: Char): Resource {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addLiteral(p0: Property?, p1: Double): Resource {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addLiteral(p0: Property?, p1: Float): Resource {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addLiteral(p0: Property?, p1: Any?): Resource {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addLiteral(p0: Property?, p1: Literal?): Resource {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : RDFNode?> canAs(p0: Class<T>?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isURIResource(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getRequiredProperty(p0: Property?): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getRequiredProperty(p0: Property?, p1: String?): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getNameSpace(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

internal class DummyLiteral : Literal {
    override fun visitWith(p0: RDFVisitor?): Any {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun asResource(): Resource {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun inModel(p0: Model?): Literal {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getDatatype(): RDFDatatype {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getLong(): Long {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : RDFNode?> `as`(p0: Class<T>?): T {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getModel(): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getFloat(): Float {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun asLiteral(): Literal {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun asNode(): org.apache.jena.graph.Node {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isAnon(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getValue(): Any {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getDouble(): Double {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getLanguage(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isLiteral(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isResource(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getBoolean(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun sameValueAs(p0: Literal?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getLexicalForm(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : RDFNode?> canAs(p0: Class<T>?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getInt(): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getDatatypeURI(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isURIResource(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isWellFormedXML(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getChar(): Char {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getShort(): Short {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getByte(): Byte {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getString(): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}

internal class DummyPrefixMapping : PrefixMapping {
    override fun removeNsPrefix(p0: String?): PrefixMapping {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun expandPrefix(p0: String?): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setNsPrefix(p0: String?, p1: String?): PrefixMapping {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun qnameFor(p0: String?): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun lock(): PrefixMapping {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setNsPrefixes(p0: PrefixMapping?): PrefixMapping {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setNsPrefixes(p0: MutableMap<String, String>?): PrefixMapping {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getNsPrefixURI(p0: String?): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun shortForm(p0: String?): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun numPrefixes(): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun samePrefixMappingAs(p0: PrefixMapping?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun withDefaultMappings(p0: PrefixMapping?): PrefixMapping {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun clearNsPrefixMap(): PrefixMapping {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getNsPrefixMap(): MutableMap<String, String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getNsURIPrefix(p0: String?): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }
}

internal class DummyModel : Model {
    override fun createAlt(): Alt {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createAlt(p0: String?): Alt {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createSeq(): Seq {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createSeq(p0: String?): Seq {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createList(): RDFList {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createList(p0: MutableIterator<RDFNode>?): RDFList {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createList(p0: Array<out RDFNode>?): RDFList {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun removeWriter(p0: String?): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun containsAny(p0: StmtIterator?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun containsAny(p0: Model?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listObjectsOfProperty(p0: Property?): NodeIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listObjectsOfProperty(p0: Resource?, p1: Property?): NodeIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isIsomorphicWith(p0: Model?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun commit(): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun qnameFor(p0: String?): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun lock(): PrefixMapping {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getBag(p0: String?): Bag {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getBag(p0: Resource?): Bag {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getNsPrefixURI(p0: String?): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun register(p0: ModelChangedListener?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listSubjects(): ResIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun close() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getNsPrefixMap(): MutableMap<String, String> {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getGraph(): Graph {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listResourcesWithProperty(p0: Property?): ResIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listResourcesWithProperty(p0: Property?, p1: RDFNode?): ResIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listResourcesWithProperty(p0: Property?, p1: Boolean): ResIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listResourcesWithProperty(p0: Property?, p1: Long): ResIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listResourcesWithProperty(p0: Property?, p1: Char): ResIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listResourcesWithProperty(p0: Property?, p1: Float): ResIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listResourcesWithProperty(p0: Property?, p1: Double): ResIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listResourcesWithProperty(p0: Property?, p1: Any?): ResIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun supportsTransactions(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun intersection(p0: Model?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setNsPrefixes(p0: PrefixMapping?): Model? {
        return this
    }

    override fun setNsPrefixes(p0: MutableMap<String, String>?): Model? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun samePrefixMappingAs(p0: PrefixMapping?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun contains(p0: Resource?, p1: Property?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun contains(p0: Resource?, p1: Property?, p2: RDFNode?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun contains(p0: Statement?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun contains(p0: Resource?, p1: Property?, p2: String?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun contains(p0: Resource?, p1: Property?, p2: String?, p3: String?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listNameSpaces(): NsIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun asStatement(p0: Triple?): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun query(p0: Selector?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getProperty(p0: String?, p1: String?): Property {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getProperty(p0: Resource?, p1: Property?): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getProperty(p0: Resource?, p1: Property?, p2: String?): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getProperty(p0: String?): Property {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun removeReification(p0: ReifiedStatement?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun removeAll(): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun removeAll(p0: Resource?, p1: Property?, p2: RDFNode?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun add(p0: Statement?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun add(p0: Array<out Statement>?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun add(p0: MutableList<Statement>?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun add(p0: StmtIterator?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun add(p0: Model?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun add(p0: Resource?, p1: Property?, p2: RDFNode?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun add(p0: Resource?, p1: Property?, p2: String?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun add(p0: Resource?, p1: Property?, p2: String?, p3: RDFDatatype?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun add(p0: Resource?, p1: Property?, p2: String?, p3: Boolean): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun add(p0: Resource?, p1: Property?, p2: String?, p3: String?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun <T : Any?> calculateInTxn(p0: Supplier<T>?): T {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun shortForm(p0: String?): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getReader(): RDFReader {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getReader(p0: String?): RDFReader {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun independent(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun supportsSetOperations(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun read(p0: String?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun read(p0: InputStream?, p1: String?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun read(p0: InputStream?, p1: String?, p2: String?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun read(p0: Reader?, p1: String?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun read(p0: String?, p1: String?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun read(p0: Reader?, p1: String?, p2: String?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun read(p0: String?, p1: String?, p2: String?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun containsAll(p0: StmtIterator?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun containsAll(p0: Model?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createLiteral(p0: String?, p1: String?): Literal {
        return DummyLiteral()
    }

    override fun createLiteral(p0: String?, p1: Boolean): Literal {
        return DummyLiteral()
    }

    override fun createLiteral(p0: String?): Literal {
        return DummyLiteral()
    }

    override fun setNsPrefix(p0: String?, p1: String?): Model? {
        return this
    }

    override fun createResource(): Resource {
        return DummyResource()
    }

    override fun createResource(p0: AnonId?): Resource {
        return DummyResource()
    }

    override fun createResource(p0: String?): Resource {
        return DummyResource()
    }

    override fun createResource(p0: Resource?): Resource {
        return DummyResource()
    }

    override fun createResource(p0: String?, p1: Resource?): Resource {
        return DummyResource()
    }

    override fun createResource(p0: ResourceF?): Resource {
        return DummyResource()
    }

    override fun createResource(p0: String?, p1: ResourceF?): Resource {
        return DummyResource()
    }

    override fun resetRDFWriterF() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createStatement(p0: Resource?, p1: Property?, p2: RDFNode?): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createStatement(p0: Resource?, p1: Property?, p2: String?): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createStatement(p0: Resource?, p1: Property?, p2: String?, p3: String?): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createStatement(p0: Resource?, p1: Property?, p2: String?, p3: Boolean): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createStatement(p0: Resource?, p1: Property?, p2: String?, p3: String?, p4: Boolean): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getSeq(p0: String?): Seq {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getSeq(p0: Resource?): Seq {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getAnyReifiedStatement(p0: Statement?): Resource {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getNsURIPrefix(p0: String?): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createBag(): Bag {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createBag(p0: String?): Bag {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun notifyEvent(p0: Any?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createReifiedStatement(p0: Statement?): ReifiedStatement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createReifiedStatement(p0: String?, p1: Statement?): ReifiedStatement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun executeInTransaction(p0: Command?): Any {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun write(p0: Writer?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun write(p0: Writer?, p1: String?): Model {
        return this
    }

    override fun write(p0: Writer?, p1: String?, p2: String?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun write(p0: OutputStream?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun write(p0: OutputStream?, p1: String?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun write(p0: OutputStream?, p1: String?, p2: String?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun begin(): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getAlt(p0: String?): Alt {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getAlt(p0: Resource?): Alt {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun leaveCriticalSection() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun wrapAsResource(p0: org.apache.jena.graph.Node?): Resource {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun size(): Long {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getRDFNode(p0: org.apache.jena.graph.Node?): RDFNode {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listReifiedStatements(): RSIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listReifiedStatements(p0: Statement?): RSIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun clearNsPrefixMap(): Model? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isClosed(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createLiteralStatement(p0: Resource?, p1: Property?, p2: Boolean): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createLiteralStatement(p0: Resource?, p1: Property?, p2: Float): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createLiteralStatement(p0: Resource?, p1: Property?, p2: Double): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createLiteralStatement(p0: Resource?, p1: Property?, p2: Long): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createLiteralStatement(p0: Resource?, p1: Property?, p2: Int): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createLiteralStatement(p0: Resource?, p1: Property?, p2: Char): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createLiteralStatement(p0: Resource?, p1: Property?, p2: Any?): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun removeNsPrefix(p0: String?): Model? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listLiteralStatements(p0: Resource?, p1: Property?, p2: Boolean): StmtIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listLiteralStatements(p0: Resource?, p1: Property?, p2: Char): StmtIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listLiteralStatements(p0: Resource?, p1: Property?, p2: Long): StmtIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listLiteralStatements(p0: Resource?, p1: Property?, p2: Int): StmtIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listLiteralStatements(p0: Resource?, p1: Property?, p2: Float): StmtIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listLiteralStatements(p0: Resource?, p1: Property?, p2: Double): StmtIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isEmpty(): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun enterCriticalSection(p0: Boolean) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun asRDFNode(p0: org.apache.jena.graph.Node?): RDFNode {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun removeAllReifications(p0: Statement?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createProperty(p0: String?, p1: String?): Property {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createProperty(p0: String?): Property {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setWriterClassName(p0: String?, p1: String?): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun resetRDFReaderF() {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getLock(): Lock {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listStatements(): StmtIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listStatements(p0: Selector?): StmtIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listStatements(p0: Resource?, p1: Property?, p2: RDFNode?): StmtIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listStatements(p0: Resource?, p1: Property?, p2: String?): StmtIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listStatements(p0: Resource?, p1: Property?, p2: String?, p3: String?): StmtIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun expandPrefix(p0: String?): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getResource(p0: String?): Resource {
        return DummyResource();
    }

    override fun getResource(p0: String?, p1: ResourceF?): Resource {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun abort(): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listObjects(): NodeIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun createTypedLiteral(p0: String?, p1: RDFDatatype?): Literal {
        return DummyLiteral()
    }

    override fun createTypedLiteral(p0: Any?, p1: RDFDatatype?): Literal {
        return DummyLiteral()
    }

    override fun createTypedLiteral(p0: Any?): Literal {
        return DummyLiteral()
    }

    override fun createTypedLiteral(p0: Boolean): Literal {
        return DummyLiteral()
    }

    override fun createTypedLiteral(p0: Int): Literal {
        return DummyLiteral()
    }

    override fun createTypedLiteral(p0: Long): Literal {
        return DummyLiteral()
    }

    override fun createTypedLiteral(p0: Calendar?): Literal {
        return DummyLiteral()
    }

    override fun createTypedLiteral(p0: Char): Literal {
        return DummyLiteral()
    }

    override fun createTypedLiteral(p0: Float): Literal {
        return DummyLiteral()
    }

    override fun createTypedLiteral(p0: Double): Literal {
        return DummyLiteral()
    }

    override fun createTypedLiteral(p0: String?): Literal {
        return DummyLiteral()
    }

    override fun createTypedLiteral(p0: String?, p1: String?): Literal {
        return DummyLiteral()
    }

    override fun createTypedLiteral(p0: Any?, p1: String?): Literal {
        return DummyLiteral()
    }

    override fun withDefaultMappings(p0: PrefixMapping?): Model? {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun isReified(p0: Statement?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun removeReader(p0: String?): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addLiteral(p0: Resource?, p1: Property?, p2: Boolean): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addLiteral(p0: Resource?, p1: Property?, p2: Long): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addLiteral(p0: Resource?, p1: Property?, p2: Int): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addLiteral(p0: Resource?, p1: Property?, p2: Char): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addLiteral(p0: Resource?, p1: Property?, p2: Float): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addLiteral(p0: Resource?, p1: Property?, p2: Double): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addLiteral(p0: Resource?, p1: Property?, p2: Any?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun addLiteral(p0: Resource?, p1: Property?, p2: Literal?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun remove(p0: Array<out Statement>?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun remove(p0: MutableList<Statement>?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun remove(p0: Statement?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun remove(p0: Resource?, p1: Property?, p2: RDFNode?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun remove(p0: StmtIterator?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun remove(p0: Model?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun containsResource(p0: RDFNode?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listSubjectsWithProperty(p0: Property?): ResIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listSubjectsWithProperty(p0: Property?, p1: RDFNode?): ResIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listSubjectsWithProperty(p0: Property?, p1: String?): ResIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun listSubjectsWithProperty(p0: Property?, p1: String?, p2: String?): ResIterator {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun setReaderClassName(p0: String?, p1: String?): String {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getWriter(): RDFWriter {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getWriter(p0: String?): RDFWriter {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun containsLiteral(p0: Resource?, p1: Property?, p2: Boolean): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun containsLiteral(p0: Resource?, p1: Property?, p2: Long): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun containsLiteral(p0: Resource?, p1: Property?, p2: Int): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun containsLiteral(p0: Resource?, p1: Property?, p2: Char): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun containsLiteral(p0: Resource?, p1: Property?, p2: Float): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun containsLiteral(p0: Resource?, p1: Property?, p2: Double): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun containsLiteral(p0: Resource?, p1: Property?, p2: Any?): Boolean {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun difference(p0: Model?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun executeInTxn(p0: Runnable?) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun numPrefixes(): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getRequiredProperty(p0: Resource?, p1: Property?): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun getRequiredProperty(p0: Resource?, p1: Property?, p2: String?): Statement {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun union(p0: Model?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun unregister(p0: ModelChangedListener?): Model {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

}
