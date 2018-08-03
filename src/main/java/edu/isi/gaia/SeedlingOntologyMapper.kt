package edu.isi.gaia

import org.apache.jena.rdf.model.Resource
import org.apache.jena.rdf.model.ResourceFactory
import java.util.*

/**
 * The Seedling domain ontology.
 *
 * For the moment, this is hard-coded to match Seedling.
 */
class SeedlingOntologyMapper : OntologyMapping {
    companion object {
        @JvmField
        val NAMESPACE_STATIC: String = "https://tac.nist.gov/tracks/SM-KBP/2018/ontologies/SeedlingOntology#"

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
        val FILLER = ResourceFactory.createResource(NAMESPACE_STATIC + "FillerType")!!


        @JvmField
        val ENTITY_TYPES = setOf(PERSON, ORGANIZATION, LOCATION, GPE, FACILITY)

        internal val SEEDLING_EVENT_TYPES_NIST = listOf(
                "Business.DeclareBankruptcy", "Business.End", "Business.Merge", "Business.Start",
                "Conflict.Attack", "Conflict.Demonstrate",
                "Contact.Broadcast", "Contact.Contact", "Contact.Correspondence", "Contact.Meet",
                "Existence.DamageDestroy",
                "Government.Agreements", "Government.Legislate", "Government.Spy", "Government.Vote",
                "Inspection.Artifact", "Inspection.People",
                "Justice.Acquit", "Justice.Appeal", "Justice.ArrestJail", "Justice.ChargeIndict", "Justice.Convict",
                "Justice.Execute", "Justice.Extradite", "Justice.Fine", "Justice.Investigate", "Justice.Pardon",
                "Justice.ReleaseParole", "Justice.Sentence", "Justice.Sue", "Justice.TrialHearing",
                "Life.BeBorn", "Life.Die", "Life.Divorce", "Life.Injure", "Life.Marry",
                "Manufacture.Artifact",
                "Movement.TransportArtifact", "Movement.TransportPerson",
                "Personnel.Elect", "Personnel.EndPosition", "Personnel.Nominate", "Personnel.StartPosition",
                "Transaction.Transaction", "Transaction.TransferControl", "Transaction.TransferMoney",
                "Transaction.TransferOwnership")
                .map { it to it }

        internal val SEEDLING_EVENT_TYPES = listOf(
                // those in the first block match the seedling ontology except...
                "CONFLICT_ATTACK", "CONFLICT_DEMONSTRATE",
                "CONTACT_BROADCAST", "CONTACT_CONTACT", "CONTACT_CORRESPONDENCE", "CONTACT_MEET",
                "JUSTICE_ARREST-JAIL",
                "LIFE_DIE", "LIFE_INJURE", "MANUFACTURE_ARTIFACT",
                "MOVEMENT_TRANSPORT-ARTIFACT",
                "MOVEMENT_TRANSPORT-PERSON", "PERSONNEL_ELECT",
                "PERSONNEL_START-POSITION", "TRANSACTION_TRANSACTION", "TRANSACTION_TRANSFER-MONEY",
                "TRANSACTION_TRANSFER-OWNERSHIP",
                "BUSINESS_DECLARE-BANKRUPTCY",
                "JUSTICE_ACQUIT",
                "JUSTICE_APPEAL", "JUSTICE_CHARGE-INDICT", "JUSTICE_CONVICT", "JUSTICE_EXECUTE",
                "JUSTICE_EXTRADITE", "JUSTICE_FINE", "JUSTICE_RELEASE-PAROLE", "JUSTICE_SENTENCE",
                "JUSTICE_SUE", "JUSTICE_TRIAL-HEARING", "LIFE_BE-BORN", "LIFE_MARRY", "LIFE_DIVORCE",
                "PERSONNEL_NOMINATE", "PERSONNEL_ELECT", "BUSINESS_END-BUSINESS",
                "BUSINESS_START-BUSINESS", "BUSINESS_MERGE", "CONTACT_CORRESPONDENCE",
                "PERSONNEL_END-POSITION")

        internal val EVENT_TYPES =
        // valid event types are seedling types directly
                SEEDLING_EVENT_TYPES.map { it to it }
                        // or seedling types with .s instead of underscores (more ACE-like)
                        .plus(SEEDLING_EVENT_TYPES.map { it.replace('_', '.') to it })
                        .plus(SEEDLING_EVENT_TYPES_NIST)
                        // or these remaining special cases
                        .plus(listOf("BUSINESS.END-ORG" to "BUSINESS_END",
                                "BUSINESS.START-ORG" to "BUSINESS_START",
                        "BUSINESS.MERGE-ORG" to "BUSINESS_MERGE",
                        // needed to read RPI Seedling output
                        "CONTACT.PHONE-WRITE" to "CONTACT_CORRESPONDENCE",
                        "PERSONNEL.END-POSITION" to "PERSONNEL_END-POSITION"))
                .map { it.first to ResourceFactory.createResource(NAMESPACE_STATIC + it.second) }
                .toMap()

        // these are currently unused
        // here for documentation only
        internal val NOT_IN_SEEDLING_BUT_REVERSE_IS = setOf("students", "births_in_city", "births_in_country",
                "residents_of_city", "residents_of_stateorprovince", "residents_of_country",
                "shareholders", "founded_by", "top_members_employees", "members", "subsidiaries",
                "city_of_headquarters", "stateorprovince_of_headquarters")
        internal val NOT_IN_SEEDLING = setOf("city_of_death", "deaths_in_city",
                "stateorprovince_of_death", "deaths_in_stateorprovince",
                "country_of_death", "deaths_in_country", "country_of_headquarters", "alternate_names",
                "number_of_employees_members", "alternate_names", "date_founded", "date_of_death", "date_dissolved",
                "cause_of_death", "charges", "likes", "dislikes",
                "PART-WHOLE.Geographical", "GEN-AFF.Org-Location", "PART-WHOLE.Artifact")

        private val PERSONAL_SOCIAL_FAMILY = "persoc_fam"
        private val PERSONAL_SOCIAL_UNSPECIFIED = "persoc_unspc"
        private val MEMBER_RELIGIOUS_ETHNIC_GROUP = "genafl_more"

        private val MEMBERSHIP = "orgafl_empmem"
        private val ALUM = "orgafl_stualm"

        private val WEBSITE = "genafl_orgweb"
        private val PERSONAL_SOCIAL_BUSINESS = "persoc_bus"
        private val PERSONAL_SOCIAL_ROLE = "persoc_role"
        private val RESIDENT = "phys_resident"
        private val INVESTOR = "orgafl_invshar"
        private val FOUNDER = "orgafl_found"
        private val LEADER = "orgafl_lead"
        private val HEADQUARTERS = "phys_orghq"
        private val SUBSIDIARY = "partwhole_subsid"
        private val LOCATED_NEAR = "phys_locnear"

        private val PART_WHOLE_MEMBER = "partwhole_membership"


        private val RELATION_SPECIAL_CASES = listOf(
                "children" to PERSONAL_SOCIAL_FAMILY,
                // TODO: this same relation seems to be used for organizational relationships
                // we ignore that for now
                "parents" to PERSONAL_SOCIAL_FAMILY,
                "other_family" to PERSONAL_SOCIAL_FAMILY,
                "siblings" to PERSONAL_SOCIAL_FAMILY,
                "spouse" to PERSONAL_SOCIAL_FAMILY,
                "PER-SOC.Family" to PERSONAL_SOCIAL_FAMILY,
                "PER-SOC.Lasting-Personal" to PERSONAL_SOCIAL_UNSPECIFIED,
                "PER-SOC.Business" to PERSONAL_SOCIAL_BUSINESS,
                "title" to PERSONAL_SOCIAL_ROLE,
                "employee_or_member_of" to MEMBERSHIP,
                "ORG-AFF.Employment" to MEMBERSHIP,
                "ORG-AFF.Sports-Affiliation" to MEMBERSHIP,
                "ORG-AFF.Membership" to MEMBERSHIP,
                "PART-WHOLE.Artifact" to PART_WHOLE_MEMBER,
                "member_of" to MEMBERSHIP,
                "schools_attended" to ALUM,
                "cities_of_residence" to RESIDENT,
                "statesorprovinces_of_residence" to RESIDENT,
                "countries_of_residence" to RESIDENT,
                "holds_shares_in" to INVESTOR,
                "ORG-AFF.Investor-Shareholder" to INVESTOR,
                "organizations_founded" to FOUNDER,
                "ORG-AFF.Founder" to FOUNDER,
                "top_member_employee_of" to LEADER,
                "headquarters_in_city" to HEADQUARTERS,
                "headquarters_in_stateorprovince" to HEADQUARTERS,
                "headquarters_in_country" to HEADQUARTERS,
                "political_religious_affiliation" to MEMBER_RELIGIOUS_ETHNIC_GROUP,
                "GEN-AFF.Citizen-Resident-Religion-Ethnicity" to MEMBER_RELIGIOUS_ETHNIC_GROUP,
                "PART-WHOLE.Subsidiary" to SUBSIDIARY,
                "origin" to MEMBER_RELIGIOUS_ETHNIC_GROUP,
                "website" to WEBSITE,
                "religion" to MEMBER_RELIGIOUS_ETHNIC_GROUP,
                "PHYS.Near" to LOCATED_NEAR,
                "PHYS.Located" to LOCATED_NEAR,
                "GEN-AFF.Org-Location" to LOCATED_NEAR)

        private val RELATION_TYPES_NIST = listOf(
                "GeneralAffiliation.APORA", "GeneralAffiliation.MORE", "GeneralAffiliation.OPRA",
                "GeneralAffiliation.OrganizationWebsite", "GeneralAffiliation.PersonAge", "GeneralAffiliation.Sponsorship",
                "Measurement.Count",
                "OrganizationAffiliation.EmploymentMembership", "OrganizationAffiliation.Founder",
                "OrganizationAffiliation.InvestorShareholder", "OrganizationAffiliation.Leadership",
                "OrganizationAffiliation.Ownership", "OrganizationAffiliation.StudentAlum",
                "PartWhole.Membership", "PartWhole.Subsidiary",
                "PersonalSocial.Business", "PersonalSocial.Family", "PersonalSocial.RoleTitle",
                "PersonalSocial.Unspecified",
                "Physical.LocatedNear", "Physical.OrganizationHeadquarter", "Physical.OrganizationLocationOrigin",
                "Physical.Resident")
                .map { it to it }


        private val RELATION_TYPES = listOf(
                // these are the seedling ontology types themselves, in case systems provide them directly
                "genafl_apora", MEMBER_RELIGIOUS_ETHNIC_GROUP, "genafl_opra", WEBSITE,
                "genafl_perage", "genafl_spon", "measurement_count",
                MEMBERSHIP, FOUNDER, INVESTOR,
                LEADER, "orgafl_own", ALUM,
                PART_WHOLE_MEMBER, SUBSIDIARY, PERSONAL_SOCIAL_BUSINESS,
                PERSONAL_SOCIAL_FAMILY, PERSONAL_SOCIAL_ROLE, PERSONAL_SOCIAL_UNSPECIFIED,
                LOCATED_NEAR, HEADQUARTERS, "phys_orglocorig",
                RESIDENT).map { it to it } // the types in the list don't need special treatment
                .plus(RELATION_SPECIAL_CASES)
                .plus(RELATION_TYPES_NIST)
                .toMap()
                .mapValues { ResourceFactory.createResource(NAMESPACE_STATIC + it.value) }
    }

    override val NAMESPACE: String = NAMESPACE_STATIC

    internal val shortNames: Map<String, Resource> = listOf(
            "PER" to PERSON,
            "ORG" to ORGANIZATION,
            "LOC" to LOCATION,
            "FAC" to FACILITY,
            "GPE" to GPE,
            "FILLER" to FILLER
    ).toMap()

    override fun entityShortNames(): Set<String> = shortNames.keys

    override fun entityType(ontology_type: String): Resource? {
        return when (ontology_type) {
            "STRING", "String" -> FILLER
            else -> shortNames[ontology_type] ?: throw RuntimeException("Unknown ontology type $ontology_type")
        }
    }

    override fun relationType(relationName: String): Resource? = RELATION_TYPES[relationName]
    /*?: throw NoSuchElementException("Unknown relation type: $relationName. Known relation " +
            "and event types ${RELATION_TYPES.keys}")*/

    override fun eventType(eventName: String): Resource? = EVENT_TYPES[eventName]
    /*?: throw NoSuchElementException("Unknown event type: $eventName. Known relation " +
            "and event types: ${EVENT_TYPES.keys}")*/

    internal val eventArgumentSpecialCases = mapOf(
            "conflict_demonstrate_entity" to "conflict_demonstrate_demonstrator",
            "contact_meet_entity" to "contact_meet_participant",
            "contact_phone-write_entity" to "contact_correspondence_participant",
            "contact_phone-write_place" to "contact_correspondence_place",
            "contact_phone-write_time" to "contact_correspondence_time",
            "justice_appeal_plaintiff" to "justice_appeal_prosecutor",
            "personnel_end-position_entity" to "personnel_end-position_organization",
            "personnel_elect_person" to "personnel_elect_elect",
            "personnel_elect_entity" to "personnel_elect_elector",
            "transaction_transfer-ownership_artifact" to "transaction_transfer-ownership_thing",
            "transaction_transfer-ownership_buyer" to "transaction_transfer-ownership_recipient"
    )

    override fun eventArgumentType(argName: String): Resource {
        val initialResult = argName.replace('.', '_').replace(':', '_').toLowerCase(Locale.ENGLISH)

        return ResourceFactory.createResource(NAMESPACE_STATIC +
                if (initialResult in eventArgumentSpecialCases) {
                    eventArgumentSpecialCases[initialResult]
                } else {
                    initialResult
                })
    }

    fun eventArgumentTypeNotLowercase(argName: String): Resource {
        return ResourceFactory.createResource(NAMESPACE_STATIC + argName)
    }

    override fun knownRelationTypes(): Set<String> {
        return RELATION_TYPES.keys
    }

    override fun knownEventTypes(): Set<String> {
        return EVENT_TYPES.keys
    }
}

open class RPISeedlingOntologyMapper : OntologyMapping {
    private val seedlingOM = SeedlingOntologyMapper()
    override val NAMESPACE: String = SeedlingOntologyMapper.NAMESPACE_STATIC
    val FILLER = ResourceFactory.createResource(NAMESPACE + "FillerType")!!

    override fun knownRelationTypes(): Set<String> {
        return seedlingOM.knownRelationTypes().plus("FILLER")
    }

    override fun knownEventTypes(): Set<String> {
        return seedlingOM.knownEventTypes()
    }

    override fun entityShortNames(): Set<String> = seedlingOM.entityShortNames().
            plus("FILLER").toSet()

    override fun entityType(ontology_type: String): Resource? = if (ontology_type == "FILLER" || ontology_type == "String") FILLER
    else seedlingOM.entityType(ontology_type)

    override fun relationType(relationName: String): Resource? = if ("FILLER" in relationName) FILLER
    else seedlingOM.relationType(relationName)

    override fun eventType(eventName: String): Resource? = seedlingOM.eventType(eventName)

    override fun eventArgumentType(argName: String): Resource = seedlingOM.eventArgumentType(argName)
}
