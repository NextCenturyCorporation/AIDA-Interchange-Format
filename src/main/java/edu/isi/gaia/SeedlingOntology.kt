package edu.isi.gaia

import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.ResourceFactory

/**
 * The Seedling domain ontology.
 *
 * For the moment, this is hard-coded to match Seedling.
 */
object SeedlingOntology : OntologyMapping {
    @JvmField
    val NAMESPACE: String = "http://darpa.mil/ontologies/SeedlingOntology/"

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

    internal val EVENT_AND_RELATION_TYPES = listOf("CONFLICT_ATTACK", "CONFLICT_DEMONSTRATE",
            "CONTACT_BROADCAST", "CONTACT_CONTACT", "CONTACT_CORRESPONDENCE", "CONTACT_MEET",
            "JUSTICE_ARREST-JAIL",
            "LIFE_DIE", "LIFE_INJURE", "MANUFACTURE_ARTIFACT",
            "MOVEMENT_TRANSPORT-ARTIFACT",
            "MOVEMENT_TRANSPORT-PERSON", "PERSONNEL_ELECT", "PERSONNEL_END-PERSONNEL",
            "PERSONNEL_START-PERSONNEL", "TRANSACTION_TRANSACTION", "TRANSACTION_TRANSFER-MONEY",
            "TRANSACTION_TRANSFER-OWNERSHIP", "children", "parents", "other_family", "other_family",
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
            "BUSINESS_DECLARE-BANKRUPTCY", "BUSINESS_END-BUSINESS",
            "BUSINESS_MERGE", "BUSINESS_START-BUSINESS", "JUSTICE_ACQUIT",
            "JUSTICE_APPEAL", "JUSTICE_CHARGE-INDICT", "JUSTICE_CONVICT", "JUSTICE_EXECUTE",
            "JUSTICE_EXTRADITE", "JUSTICE_FINE", "JUSTICE_RELEASE-PAROLE", "JUSTICE_SENTENCE",
            "JUSTICE_SUE", "JUSTICE_TRIAL-HEARING", "LIFE_BE-BORN", "LIFE_MARRY", "LIFE_DIVORCE",
            "PERSONNEL_NOMINATE", "likes", "dislikes")
            .map { it to ResourceFactory.createResource(NAMESPACE + it) }
            .toMap()

    // sentiment types
    @JvmField
    val LIKES = ResourceFactory.createResource(NAMESPACE + "LIKES")!!
    @JvmField
    val DISLIKES = ResourceFactory.createResource(NAMESPACE + "DISLIKES")!!

    private val ontologizeEventType: (String) -> Resource = OntoMemoize({ eventType: String ->
        ResourceFactory.createResource(NAMESPACE + eventType)
    })

    override fun shortNameToResource(ontology_type: String): Resource {
        // can't go in the when statement because it has an arbitrary boolean condition
        // this handles ColdStart event arguments
        if (':' in ontology_type) {
            return ColdStartOntology.eventType(ontology_type)
        }

        return when (ontology_type) {
            "PER" -> ColdStartOntology.PERSON
            "ORG" -> ColdStartOntology.ORGANIZATION
            "LOC" -> ColdStartOntology.LOCATION
            "FAC" -> ColdStartOntology.FACILITY
            "GPE" -> ColdStartOntology.GPE
            "STRING", "String" -> ColdStartOntology.STRING
            in ColdStartOntology.EVENT_AND_RELATION_TYPES.keys ->
                ColdStartOntology.EVENT_AND_RELATION_TYPES.getValue(ontology_type)
            else -> throw RuntimeException("Unknown ontology type $ontology_type")
        }
    }

    override fun relationType(relationName: String): Resource = EVENT_AND_RELATION_TYPES[relationName]
            ?: throw NoSuchElementException("Unknown relation type: $relationName. Known relation " +
                    "and event types ${EVENT_AND_RELATION_TYPES.keys}")

    override fun eventType(eventName: String): Resource = EVENT_AND_RELATION_TYPES[eventName]
            ?: throw NoSuchElementException("Unknown event type: $eventName. Known relation " +
                    "and event types: ${EVENT_AND_RELATION_TYPES.keys}")

    override fun eventArgumentType(argName: String): Resource = ontologizeEventType(argName)

}
