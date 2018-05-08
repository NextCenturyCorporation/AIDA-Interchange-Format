package edu.isi.gaia

import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.ResourceFactory

// used in AidaDomainOntology
private class Memoize<in Arg, out Result>(val f: (Arg) -> Result) : (Arg) -> Result {
    private val cache = mutableMapOf<Arg, Result>()
    override fun invoke(arg: Arg) = cache.getOrPut(arg, { f(arg) })
}

/**
 * The domain ontology.
 *
 * For the moment, this is hard-coded to match ColdStart.
 */
object AidaDomainOntology {
    @JvmField
    val NAMESPACE: String = "http://www.isi.edu/aida/programOntology#"

    @JvmField
    val PERSON = ResourceFactory.createResource(NAMESPACE + "Person")!!
    @JvmField
    val ORGANIZATION = ResourceFactory.createResource(NAMESPACE + "Organization")!!
    @JvmField
    val LOCATION = ResourceFactory.createResource(NAMESPACE + "Location")!!
    @JvmField
    val GPE = ResourceFactory.createResource(NAMESPACE + "GeopoliticalEntity")!!
    @JvmField
    val FACILITY = ResourceFactory.createResource(NAMESPACE + "Facility")!!
    @JvmField
    val STRING = ResourceFactory.createResource(NAMESPACE + "String")!!

    @JvmField
    val ENTITY_TYPES = setOf(PERSON, ORGANIZATION, LOCATION, GPE, FACILITY)

    internal val EVENT_AND_RELATION_TYPES = listOf("CONFLICT.ATTACK", "CONFLICT.DEMONSTRATE",
            "CONTACT.BROADCAST", "CONTACT.CONTACT", "CONTACT.CORRESPONDENCE", "CONTACT.MEET",
            "JUSTICE.ARREST-JAIL",
            "LIFE.DIE", "LIFE.INJURE", "MANUFACTURE.ARTIFACT",
            "MOVEMENT.TRANSPORT-ARTIFACT",
            "MOVEMENT.TRANSPORT-PERSON", "PERSONNEL.ELECT", "PERSONNEL.END-POSITION",
            "PERSONNEL.START-POSITION", "TRANSACTION.TRANSACTION", "TRANSACTION.TRANSFER-MONEY",
            "TRANSACTION.TRANSFER-OWNERSHIP", "children", "parents", "other_family", "other_family",
            "parents", "children", "siblings", "siblings", "spouse", "spouse",
            "employee_or_member_of", "employees_or_members", "schools_attended", "students",
            "city_of_birth", "births_in_city", "stateorprovince_of_birth",
            "births_in_stateorprovince", "country_of_birth", "births_in_country",
            "cities_of_residence", "residents_of_city",
            "statesorprovinces_of_residence", "residents_of_stateorprovince",
            "countries_of_residence", "residents_of_country",
            "city_of_death", "deaths_in_city",
            "stateorprovince_of_death", "deaths_in_stateorprovince",
            "country_of_death", "deaths_in_country",
            "shareholders", "holds_shares_in",
            "founded_by", "organizations_founded",
            "top_members_employees", "top_member_employee_of",
            "member_of", "members",
            "members", "member_of",
            "parents", "subsidiaries",
            "subsidiaries", "parents",
            "city_of_headquarters", "headquarters_in_city",
            "stateorprovince_of_headquarters", "headquarters_in_stateorprovince",
            "country_of_headquarters", "headquarters_in_country",
            "alternate_names", "alternate_names", "date_of_birth",
            "political_religious_affiliation", "age", "number_of_employees_members",
            "origin", "date_founded", "date_of_death", "date_dissolved",
            "cause_of_death", "website", "title", "religion", "charges",
            // needed to read RPI ColdStart output
            "CONTACT.PHONEWRITE", "BUSINESS.DECLAREBANKRUPTCY", "BUSINESS.ENDORG",
            "BUSINESS.MERGEORG", "BUSINESS.STARTORG", "MOVEMENT.TRANSPORT", "JUSTICE.ACQUIT",
            "JUSTICE.APPEAL", "JUSTICE.CHARGEINDICT", "JUSTICE.CONVICT", "JUSTICE.EXECUTE",
            "JUSTICE.EXTRADITE", "JUSTICE.FINE", "JUSTICE.RELEASEPAROLE", "JUSTICE.SENTENCE",
            "JUSTICE.SUE", "JUSTICE.TRIALHEARING", "LIFE.BEBORN", "LIFE.MARRY", "LIFE.DIVORCE",
            "PERSONNEL.NOMINATE")
            .map { it to ResourceFactory.createResource(NAMESPACE + it.toLowerCase()) }
            .toMap()

    // realis types
    val ACTUAL = ResourceFactory.createResource(NAMESPACE + "Actual")!!
    val GENERIC = ResourceFactory.createResource(NAMESPACE + "Generic")!!
    val OTHER = ResourceFactory.createResource(NAMESPACE + "Other")!!

    // sentiment types
    @JvmField
    val LIKES = ResourceFactory.createResource(NAMESPACE + "LIKES")!!
    @JvmField
    val DISLIKES = ResourceFactory.createResource(NAMESPACE + "DISLIKES")!!

    private val ontologizeEventType: (String) -> Resource = Memoize({ eventType: String ->
        ResourceFactory.createResource(NAMESPACE + eventType)
    })

    @JvmStatic
    fun relationType(relationName: String): Resource = EVENT_AND_RELATION_TYPES[relationName]
            ?: throw NoSuchElementException("Unknown relation type: $relationName. Known relation " +
                    "and event types ${EVENT_AND_RELATION_TYPES.keys}")

    @JvmStatic
    fun eventType(eventName: String): Resource = EVENT_AND_RELATION_TYPES[eventName]
            ?: throw NoSuchElementException("Unknown event type: $eventName. Known relation " +
                    "and event types: ${EVENT_AND_RELATION_TYPES.keys}")

    @JvmStatic
    fun eventArgumentType(argName: String): Resource = ontologizeEventType(argName)

}