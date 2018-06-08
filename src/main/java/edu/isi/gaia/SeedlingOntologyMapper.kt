package edu.isi.gaia

import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.ResourceFactory

/**
 * The Seedling domain ontology.
 *
 * For the moment, this is hard-coded to match Seedling.
 */
object SeedlingOntologyMapper : OntologyMapping {
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
            // needed to read RPI Seedling output
            "CONTACT.PHONE-WRITE", "BUSINESS.DECLARE-BANKRUPTCY", "BUSINESS.END-ORG",
            "BUSINESS.MERGE-ORG", "BUSINESS.START-ORG", "MOVEMENT.TRANSPORT", "JUSTICE.ACQUIT",
            "JUSTICE.APPEAL", "JUSTICE.CHARGE-INDICT", "JUSTICE.CONVICT", "JUSTICE.EXECUTE",
            "JUSTICE.EXTRADITE", "JUSTICE.FINE", "JUSTICE.RELEASE-PAROLE", "JUSTICE.SENTENCE",
            "JUSTICE.SUE", "JUSTICE.TRIAL-HEARING", "LIFE.BE-BORN", "LIFE.MARRY", "LIFE.DIVORCE",
            "PERSONNEL.NOMINATE", "likes", "dislikes",
            "GPE:PART-WHOLE.Geographical", "LOC:PHYS.Near", "GPE:ORG-AFF.Membership", "PER:PER-SOC.Business",
            "PER:PHYS.Located", "GPE:ORG-AFF.Employment", "FAC:GEN-AFF.Org-Location", "GPE:PER-SOC.Business",
            "PER:ORG-AFF.Employment", "LOC:GEN-AFF.Citizen-Resident-Religion-Ethnicity", "ORG:GEN-AFF.Org-Location",
            "GPE:PART-WHOLE.Subsidiary", "FAC:PART-WHOLE.Geographical", "PER:ORG-AFF.Membership", "LOC:ORG-AFF.Employment",
            "GPE:GEN-AFF.Citizen-Resident-Religion-Ethnicity", "LOC:PHYS.Located", "ORG:PART-WHOLE.Subsidiary",
            "PER:GEN-AFF.Citizen-Resident-Religion-Ethnicity", "PER:ORG-AFF.Ownership", "GPE:PHYS.Located",
            "LOC:ORG-AFF.Membership", "GPE:GEN-AFF.Org-Location", "ORG:ORG-AFF.Membership", "GPE:ORG-AFF.Founder",
            "FAC:PHYS.Located", "FAC:ORG-AFF.Membership", "PER:PER-SOC.Lasting-Personal", "ORG:ORG-AFF.Employment",
            "ORG:PER-SOC.Business", "PER:PER-SOC.Family", "GPE:ORG-AFF.Ownership", "ORG:PHYS.Located",
            "LOC:PART-WHOLE.Geographical", "FAC:PER-SOC.Business", "GPE:PART-WHOLE.Artifact",
            "PER:PART-WHOLE.Geographical", "ORG:PART-WHOLE.Geographical", "GPE:PHYS.Near",
            "PER:ORG-AFF.Sports-Affiliation", "GPE:PER-SOC.Family", "ORG:ORG-AFF.Investor-Shareholder"
    )
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
        // this handles Seedling event arguments
        if (':' in ontology_type) {
            return SeedlingOntologyMapper.eventType(ontology_type)
        }

        return when (ontology_type) {
            "PER" -> SeedlingOntologyMapper.PERSON
            "ORG" -> SeedlingOntologyMapper.ORGANIZATION
            "LOC" -> SeedlingOntologyMapper.LOCATION
            "FAC" -> SeedlingOntologyMapper.FACILITY
            "GPE" -> SeedlingOntologyMapper.GPE
            "STRING", "String" -> SeedlingOntologyMapper.STRING
            in SeedlingOntologyMapper.EVENT_AND_RELATION_TYPES.keys ->
                SeedlingOntologyMapper.EVENT_AND_RELATION_TYPES.getValue(ontology_type)
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

object RPISeedlingOntologyMapper : OntologyMapping {
    val NAMESPACE: String = SeedlingOntologyMapper.NAMESPACE
    val FILLER = ResourceFactory.createResource(NAMESPACE + "FillerType")!!

    override fun shortNameToResource(ontology_type: String): Resource = if (ontology_type == "FILLER") FILLER
    else SeedlingOntologyMapper.shortNameToResource(ontology_type)

    override fun relationType(relationName: String): Resource = if ("FILLER" in relationName) FILLER
    else SeedlingOntologyMapper.relationType(relationName)

    override fun eventType(eventName: String): Resource = SeedlingOntologyMapper.eventType(eventName)

    override fun eventArgumentType(argName: String): Resource = SeedlingOntologyMapper.eventArgumentType(argName)
}
