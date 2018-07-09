package edu.isi.gaia

import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.ResourceFactory

/**
 * The Seedling domain ontology.
 *
 * For the moment, this is hard-coded to match Seedling.
 */
class SeedlingOntologyMapper : OntologyMapping {
    companion object {
        @JvmField
        val NAMESPACE_STATIC: String = "http://darpa.mil/ontologies/SeedlingOntology/"

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

        internal val EVENT_TYPES = listOf(
                // those in the first block match the seedling ontology except...
                "CONFLICT.ATTACK", "CONFLICT.DEMONSTRATE",
                "CONTACT.BROADCAST", "CONTACT.CONTACT", "CONTACT.CORRESPONDENCE", "CONTACT.MEET",
                "JUSTICE.ARREST-JAIL",
                "LIFE.DIE", "LIFE.INJURE", "MANUFACTURE.ARTIFACT",
                "MOVEMENT.TRANSPORT-ARTIFACT",
                "MOVEMENT.TRANSPORT-PERSON", "PERSONNEL.ELECT",
                "PERSONNEL.START-POSITION", "TRANSACTION.TRANSACTION", "TRANSACTION.TRANSFER-MONEY",
                "TRANSACTION.TRANSFER-OWNERSHIP",
                "BUSINESS.DECLARE-BANKRUPTCY",
                "JUSTICE.ACQUIT",
                "JUSTICE.APPEAL", "JUSTICE.CHARGE-INDICT", "JUSTICE.CONVICT", "JUSTICE.EXECUTE",
                "JUSTICE.EXTRADITE", "JUSTICE.FINE", "JUSTICE.RELEASE-PAROLE", "JUSTICE.SENTENCE",
                "JUSTICE.SUE", "JUSTICE.TRIAL-HEARING", "LIFE.BE-BORN", "LIFE.MARRY", "LIFE.DIVORCE",
                "PERSONNEL.NOMINATE")
                // ... for having .s where seedling has a _
                .map { it to it.replace('.', '_') }
                // the remaining ones have other differences and are special-cased
                .plus(listOf("BUSINESS.END-ORG" to "BUSINESS_END-BUSINESS",
                        "BUSINESS.START-ORG" to "BUSINESS_START-BUSINESS",
                        "BUSINESS.MERGE-ORG" to "BUSINESS_MERGE",
                        // needed to read RPI Seedling output
                        "CONTACT.PHONE-WRITE" to "CONTACT_CORRESPONDENCE",
                        "PERSONNEL.END-POSITION" to "PERSONNEL_END-PERSONNEL"))
                .map { it.first to ResourceFactory.createResource(NAMESPACE_STATIC + it.second) }
                .toMap()

        internal val RELATION_TYPES: Map<String, Resource> = listOf(
                "children", "parents", "other_family", "other_family",
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
                "likes", "dislikes",
                "PART-WHOLE.Geographical",
                "PHYS.Located", "PHYS.Near",
                "ORG-AFF.Employment", "ORG-AFF.Founder", "ORG-AFF.Sports-Affiliation", "ORG-AFF.Investor-Shareholder",
                "ORG-AFF.Membership", "ORG-AFF.Ownership",
                "GEN-AFF.Org-Location", "GEN-AFF.Citizen-Resident-Religion-Ethnicity",
                "PART-WHOLE.Subsidiary", "PART-WHOLE.Artifact",
                "PER-SOC.Lasting-Personal", "PER-SOC.Family", "PER-SOC.Business"
        )
                .map { it to ResourceFactory.createResource(NAMESPACE_STATIC + it) }
                .toMap()
    }

    override val NAMESPACE: String = NAMESPACE_STATIC


    internal val shortNames: Map<String, Resource> = listOf(
            "PER" to PERSON,
            "ORG" to ORGANIZATION,
            "LOC" to LOCATION,
            "FAC" to FACILITY,
            "GPE" to GPE
    ).toMap()

    override fun entityShortNames(): Set<String> = shortNames.keys

    override fun shortNameToResource(ontology_type: String): Resource {
        // can't go in the when statement because it has an arbitrary boolean condition
        // this handles ColdStart event arguments
        if (':' in ontology_type) {
            return eventType(ontology_type)
        }

        return when (ontology_type) {
            "STRING", "String" -> STRING
            in RELATION_TYPES.keys ->
                RELATION_TYPES.getValue(ontology_type)
            else -> shortNames[ontology_type] ?: throw RuntimeException("Unknown ontology type $ontology_type")
        }
    }

    override fun relationType(relationName: String): Resource = RELATION_TYPES[relationName]
            ?: throw NoSuchElementException("Unknown relation type: $relationName. Known relation " +
                    "and event types ${RELATION_TYPES.keys}")

    override fun eventType(eventName: String): Resource = EVENT_TYPES[eventName]
            ?: throw NoSuchElementException("Unknown event type: $eventName. Known relation " +
                    "and event types: ${EVENT_TYPES.keys}")

    override fun eventArgumentType(argName: String): Resource =
            ResourceFactory.createResource(NAMESPACE_STATIC + argName)
}

open class RPISeedlingOntologyMapper : OntologyMapping {
    private val seedlingOM = SeedlingOntologyMapper()
    override val NAMESPACE: String = SeedlingOntologyMapper.NAMESPACE_STATIC
    val FILLER = ResourceFactory.createResource(NAMESPACE + "FillerType")!!

    override fun entityShortNames(): Set<String> = seedlingOM.entityShortNames().
            plus("FILLER").toSet()

    override fun shortNameToResource(ontology_type: String): Resource = if (ontology_type == "FILLER") FILLER
    else seedlingOM.shortNameToResource(ontology_type)

    override fun relationType(relationName: String): Resource = if ("FILLER" in relationName) FILLER
    else seedlingOM.relationType(relationName)

    override fun eventType(eventName: String): Resource = seedlingOM.eventType(eventName)

    override fun eventArgumentType(argName: String): Resource = seedlingOM.eventArgumentType(argName)
}
