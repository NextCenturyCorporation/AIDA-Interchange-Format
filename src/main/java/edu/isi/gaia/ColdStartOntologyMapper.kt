package edu.isi.gaia

import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.ResourceFactory

/**
 * The domain ontology.
 *
 * For the moment, this is hard-coded to match ColdStart.
 */
class ColdStartOntologyMapper : OntologyMapping {
    companion object {
        @JvmField
        val NAMESPACE_STATIC: String = "http://nist.gov/ontologies/ColdstartOntology#"
        @JvmField
        val PERSON = ResourceFactory.createResource(NAMESPACE_STATIC + "Person")!!
        @JvmField
        val ORGANIZATION = ResourceFactory.createResource(NAMESPACE_STATIC + "Organization")!!
        @JvmField
        val LOCATION = ResourceFactory.createResource(NAMESPACE_STATIC + "Location")!!
        @JvmField
        val GPE = ResourceFactory.createResource(NAMESPACE_STATIC + "GeopoliticalEntity")!!
        @JvmField
        val FACILITY = ResourceFactory.createResource(NAMESPACE_STATIC + "Facility")!!
        @JvmField
        val STRING = ResourceFactory.createResource(NAMESPACE_STATIC + "String")!!

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
                "PERSONNEL.NOMINATE", "likes", "dislikes")
                .map { it to ResourceFactory.createResource(NAMESPACE_STATIC + it) }
                .toMap()

        // realis types
        val ACTUAL = ResourceFactory.createResource(NAMESPACE_STATIC + "Actual")!!
        val GENERIC = ResourceFactory.createResource(NAMESPACE_STATIC + "Generic")!!
        val OTHER = ResourceFactory.createResource(NAMESPACE_STATIC + "Other")!!

        // sentiment types
        @JvmField
        val LIKES = ResourceFactory.createResource(NAMESPACE_STATIC + "LIKES")!!
        @JvmField
        val DISLIKES = ResourceFactory.createResource(NAMESPACE_STATIC + "DISLIKES")!!
    }

    override val NAMESPACE: String = NAMESPACE_STATIC



    private val ontologizeEventType: (String) -> Resource = OntoMemoize({ eventType: String ->
        ResourceFactory.createResource(NAMESPACE_STATIC + eventType)
    })

    internal val shortNames: Map<String, Resource> = listOf(
            "PER" to PERSON,
            "ORG" to ORGANIZATION,
            "LOC" to LOCATION,
            "FAC" to FACILITY,
            "GPE" to GPE
    ).toMap()

    override fun entityShortNames(): Set<String> = shortNames.keys

    override fun entityType(ontology_type: String): Resource {
        return when (ontology_type) {
            "STRING", "String" -> STRING
            else -> shortNames[ontology_type] ?: throw RuntimeException("Unknown ontology type $ontology_type")
        }
    }

    override fun relationType(relationName: String): Resource = EVENT_AND_RELATION_TYPES[relationName]
            ?: throw NoSuchElementException("Unknown relation type: $relationName. Known relation " +
                    "and event types ${EVENT_AND_RELATION_TYPES.keys}")

    override fun eventType(eventName: String): Resource = EVENT_AND_RELATION_TYPES[eventName]
            ?: throw NoSuchElementException("Unknown event type: $eventName. Known relation " +
                    "and event types: ${EVENT_AND_RELATION_TYPES.keys}")

    override fun eventArgumentType(argName: String): Resource = ontologizeEventType(argName)

    override fun knownRelationTypes(): Set<String> {
        throw RuntimeException("Implement me if you need to use me")
    }

    override fun knownEventTypes(): Set<String> {
        throw RuntimeException("Implement me if you need to use me")
    }

    override fun typeAllowedToHaveAName(type: Resource) = true
    override fun typeAllowedToHaveTextValue(type: Resource) = true
    override fun typeAllowedToHaveNumericValue(type: Resource) = true
}
