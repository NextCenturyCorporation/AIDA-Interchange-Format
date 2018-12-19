package com.ncc.gaia;

import org.apache.commons.csv.CSVFormat;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.apache.jena.vocabulary.OWL;
import org.apache.jena.vocabulary.RDF;
import org.apache.jena.vocabulary.RDFS;

import java.io.File;
import java.util.*;
import java.util.stream.*;

/**
 * The Seedling domain ontology.
 * <p>
 * For the moment, this is hard-coded to match Seedling.
 */
public final class SeedlingOntologyMapper implements OntologyMapping {
    //companion object {
    protected static final String NAMESPACE_STATIC = "https://tac.nist.gov/tracks/SM-KBP/2018/ontologies/SeedlingOntology#";

    public static final Resource PERSON = ResourceFactory.createResource(NAMESPACE_STATIC + "Person");
    public static final Resource ORGANIZATION = ResourceFactory.createResource(NAMESPACE_STATIC + "Organization");
    public static final Resource LOCATION = ResourceFactory.createResource(NAMESPACE_STATIC + "Location");
    public static final Resource GPE = ResourceFactory.createResource(NAMESPACE_STATIC + "GeopoliticalEntity");
    public static final Resource FACILITY = ResourceFactory.createResource(NAMESPACE_STATIC + "Facility");

    public static final Resource WEAPON = ResourceFactory.createResource(NAMESPACE_STATIC + "Weapon");

    public static final Resource VEHICLE = ResourceFactory.createResource(NAMESPACE_STATIC + "Vehicle");

    public static final Resource LAW = ResourceFactory.createResource(NAMESPACE_STATIC + "Law");

    public static final Resource FILLER = ResourceFactory.createResource(NAMESPACE_STATIC + "FillerType");

    public static final Resource RESULTS = ResourceFactory.createResource(NAMESPACE_STATIC + "Results");

    public static final Resource TIME = ResourceFactory.createResource(NAMESPACE_STATIC + "Time");

    public static final Resource MONEY = ResourceFactory.createResource(NAMESPACE_STATIC + "Money");

    public static final Resource URL = ResourceFactory.createResource(NAMESPACE_STATIC + "URL");

    public static final Resource AGE = ResourceFactory.createResource(NAMESPACE_STATIC + "Age");

    public static final Resource NUMERICAL_VALUE = ResourceFactory.createResource(NAMESPACE_STATIC + "NumericalValue");

    public static final Resource ENTITY_TYPES = setOf(PERSON, ORGANIZATION, LOCATION, GPE, FACILITY);

    public static final Resource FILLER_TYPES_WHICH_CAN_HAVE_NAMES = setOf(WEAPON, VEHICLE, LAW);

    public static final Resource TYPES_WHICH_CAN_HAVE_NAMES = ENTITY_TYPES.union(FILLER_TYPES_WHICH_CAN_HAVE_NAMES).toSet();

    public static final Resource TYPES_WHICH_CAN_HAVE_TEXT_VALUES = setOf(RESULTS, TIME, MONEY, URL);

    public static final Resource TYPES_WHICH_CAN_HAVE_NUMERIC_VALUES = setOf(AGE, NUMERICAL_VALUE);

    internal val
    SEEDLING_EVENT_TYPES_NIST =

    listOf(
                "Business.DeclareBankruptcy","Business.End","Business.Merge","Business.Start",
                        "Conflict.Attack","Conflict.Demonstrate",
                        "Contact.Broadcast","Contact.Contact","Contact.Correspondence","Contact.Meet",
                        "Existence.DamageDestroy",
                        "Government.Agreements","Government.Legislate","Government.Spy","Government.Vote",
                        "Inspection.Artifact","Inspection.People",
                        "Justice.Acquit","Justice.Appeal","Justice.ArrestJail","Justice.ChargeIndict","Justice.Convict",
                        "Justice.Execute","Justice.Extradite","Justice.Fine","Justice.Investigate","Justice.Pardon",
                        "Justice.ReleaseParole","Justice.Sentence","Justice.Sue","Justice.TrialHearing",
                        "Life.BeBorn","Life.Die","Life.Divorce","Life.Injure","Life.Marry",
                        "Manufacture.Artifact",
                        "Movement.TransportArtifact","Movement.TransportPerson",
                        "Personnel.Elect","Personnel.EndPosition","Personnel.Nominate","Personnel.StartPosition",
                        "Transaction.Transaction","Transaction.TransferControl","Transaction.TransferMoney",
                        "Transaction.TransferOwnership")
                .map

    {
        it to it
    }

    internal val
    SEEDLING_EVENT_TYPES =

    listOf(
            // those in the first block match the seedling ontology except...
                "CONFLICT_ATTACK","CONFLICT_DEMONSTRATE",
                        "CONTACT_BROADCAST","CONTACT_CONTACT","CONTACT_CORRESPONDENCE","CONTACT_MEET",
                        "JUSTICE_ARREST-JAIL",
                        "LIFE_DIE","LIFE_INJURE","MANUFACTURE_ARTIFACT",
                        "MOVEMENT_TRANSPORT-ARTIFACT",
                        "MOVEMENT_TRANSPORT-PERSON","PERSONNEL_ELECT",
                        "PERSONNEL_START-POSITION","TRANSACTION_TRANSACTION","TRANSACTION_TRANSFER-MONEY",
                        "TRANSACTION_TRANSFER-OWNERSHIP",
                        "BUSINESS_DECLARE-BANKRUPTCY",
                        "JUSTICE_ACQUIT",
                        "JUSTICE_APPEAL","JUSTICE_CHARGE-INDICT","JUSTICE_CONVICT","JUSTICE_EXECUTE",
                        "JUSTICE_EXTRADITE","JUSTICE_FINE","JUSTICE_RELEASE-PAROLE","JUSTICE_SENTENCE",
                        "JUSTICE_SUE","JUSTICE_TRIAL-HEARING","LIFE_BE-BORN","LIFE_MARRY","LIFE_DIVORCE",
                        "PERSONNEL_NOMINATE","PERSONNEL_ELECT","BUSINESS_END-BUSINESS",
                        "BUSINESS_START-BUSINESS","BUSINESS_MERGE","CONTACT_CORRESPONDENCE",
                        "PERSONNEL_END-POSITION")

    internal val
    EVENT_TYPES =
    // valid event types are seedling types directly
    SEEDLING_EVENT_TYPES.map

    {
        it to it
    }
    // or seedling types with .s instead of underscores (more ACE-like)
                        .

    plus(SEEDLING_EVENT_TYPES.map {
        it.replace('_', '.') to it
    })
            .

    plus(SEEDLING_EVENT_TYPES_NIST)
    // or these remaining special cases
                        .

    plus(listOf("BUSINESS.END-ORG"to"BUSINESS_END",
                                "BUSINESS.START-ORG"to "BUSINESS_START",
                        "BUSINESS.MERGE-ORG"to "BUSINESS_MERGE",
         // needed to read RPI Seedling output
                        "CONTACT.PHONE-WRITE"to "CONTACT_CORRESPONDENCE",
                        "PERSONNEL.END-POSITION"to "PERSONNEL_END-POSITION"))
            .map

    {
        it.first to ResourceFactory.createResource(NAMESPACE_STATIC + it.second)
    }
                .

    toMap()

    // these are currently unused
    // here for documentation only
    internal val
    NOT_IN_SEEDLING_BUT_REVERSE_IS =

    setOf("students","births_in_city","births_in_country",
                  "residents_of_city","residents_of_stateorprovince","residents_of_country",
                  "shareholders","founded_by","top_members_employees","members","subsidiaries",
                  "city_of_headquarters","stateorprovince_of_headquarters")

    internal val
    NOT_IN_SEEDLING =

    setOf("city_of_death","deaths_in_city",
                  "stateorprovince_of_death","deaths_in_stateorprovince",
                  "country_of_death","deaths_in_country","country_of_headquarters","alternate_names",
                  "number_of_employees_members","alternate_names","date_founded","date_of_death","date_dissolved",
                  "cause_of_death","charges","likes","dislikes",
                  "PART-WHOLE.Geographical","GEN-AFF.Org-Location","PART-WHOLE.Artifact")

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
    private val PART_WHOLE_SUBSIDIARY = ""


    private val RELATION_SPECIAL_CASES = listOf(
            "children"to PERSONAL_SOCIAL_FAMILY,
            // TODO: this same relation seems to be used for organizational relationships
            // we ignore that for now
            "parents"to PERSONAL_SOCIAL_FAMILY,
            "other_family"to PERSONAL_SOCIAL_FAMILY,
            "siblings"to PERSONAL_SOCIAL_FAMILY,
            "spouse"to PERSONAL_SOCIAL_FAMILY,
            "PER-SOC.Family"to PERSONAL_SOCIAL_FAMILY,
            "PER-SOC.Lasting-Personal"to PERSONAL_SOCIAL_UNSPECIFIED,
            "PER-SOC.Business"to PERSONAL_SOCIAL_BUSINESS,
            "title"to PERSONAL_SOCIAL_ROLE,
            "employee_or_member_of"to MEMBERSHIP,
            "ORG-AFF.Employment"to MEMBERSHIP,
            "ORG-AFF.Sports-Affiliation"to MEMBERSHIP,
            "ORG-AFF.Membership"to MEMBERSHIP,
            "PART-WHOLE.Artifact"to PART_WHOLE_MEMBER,
            "member_of"to MEMBERSHIP,
            "schools_attended"to ALUM,
            "cities_of_residence"to RESIDENT,
            "statesorprovinces_of_residence"to RESIDENT,
            "countries_of_residence"to RESIDENT,
            "holds_shares_in"to INVESTOR,
            "ORG-AFF.Investor-Shareholder"to INVESTOR,
            "organizations_founded"to FOUNDER,
            "ORG-AFF.Founder"to FOUNDER,
            "top_member_employee_of"to LEADER,
            "headquarters_in_city"to HEADQUARTERS,
            "headquarters_in_stateorprovince"to HEADQUARTERS,
            "headquarters_in_country"to HEADQUARTERS,
            "political_religious_affiliation"to MEMBER_RELIGIOUS_ETHNIC_GROUP,
            "GEN-AFF.Citizen-Resident-Religion-Ethnicity"to MEMBER_RELIGIOUS_ETHNIC_GROUP,
            "PART-WHOLE.Subsidiary"to SUBSIDIARY,
            "origin"to MEMBER_RELIGIOUS_ETHNIC_GROUP,
            "website"to WEBSITE,
            "religion"to MEMBER_RELIGIOUS_ETHNIC_GROUP,
            "PHYS.Near"to LOCATED_NEAR,
            "PHYS.Located"to LOCATED_NEAR,
            "GEN-AFF.Org-Location"to LOCATED_NEAR,
            "Part-Whole.Subsidiary"to PART_WHOLE_SUBSIDIARY)

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
            .map

    {
        it to it
    }


    private val RELATION_TYPES = listOf(
            // these are the seedling ontology types themselves, in case systems provide them directly
            "genafl_apora", MEMBER_RELIGIOUS_ETHNIC_GROUP, "genafl_opra", WEBSITE,
            "genafl_perage", "genafl_spon", "measurement_count",
            MEMBERSHIP, FOUNDER, INVESTOR,
            LEADER, "orgafl_own", ALUM,
            PART_WHOLE_MEMBER, SUBSIDIARY, PERSONAL_SOCIAL_BUSINESS,
            PERSONAL_SOCIAL_FAMILY, PERSONAL_SOCIAL_ROLE, PERSONAL_SOCIAL_UNSPECIFIED,
            LOCATED_NEAR, HEADQUARTERS, "phys_orglocorig",
            RESIDENT).map

    {
        it to it
    } // the types in the list don't need special treatment
                .

    plus(RELATION_SPECIAL_CASES)
                .

    plus(RELATION_TYPES_NIST)
                .

    toMap()
                .mapValues

    {
        ResourceFactory.createResource(NAMESPACE_STATIC + it.value)
    }
    //}

    String NAMESPACE = NAMESPACE_STATIC;

    internal val
    shortNames:Map<String, Resource> =

    listOf(
            "PER"to PERSON,
            "ORG"to ORGANIZATION,
            "LOC"to LOCATION,
            "FAC"to FACILITY,
            "GPE"to GPE,
            "FILLER"to FILLER
    ).

    toMap()

    Set<String> entityShortNames() {
        return shortNames.keys;
    }

    public Resource entityType(String ontology_type) {
        return when(ontology_type) {
            "STRING", "String" ->FILLER
            else ->shortNames[ontology_type] ?:throw RuntimeException("Unknown ontology type $ontology_type")
        }
    }

    public Resource relationType(String relationName) {
        return RELATION_TYPES[relationName];
    }
    /*?: throw NoSuchElementException("Unknown relation type: $relationName. Known relation " +
            "and event types ${RELATION_TYPES.keys}")*/

    public Resource eventType(String eventName) {
        return EVENT_TYPES[eventName];
    }

    /*?: throw NoSuchElementException("Unknown event type: $eventName. Known relation " +
            "and event types: ${EVENT_TYPES.keys}")*/

    // TBDDAG:  check Guava approach
    private Map<String, String> eventArgumentSpecialCases = Stream.of(new String[][]{
            {"conflict_demonstrate_entity", "conflict_demonstrate_demonstrator"},
            {"contact_meet_entity", "contact_meet_participant"},
            {"contact_phone-write_entity", "contact_correspondence_participant"},
            {"contact_phone-write_place", "contact_correspondence_place"},
            {"contact_phone-write_time", "contact_correspondence_time"},
            {"justice_appeal_plaintiff", "justice_appeal_prosecutor"},
            {"personnel_end-position_entity", "personnel_end-position_organization"},
            {"personnel_elect_person", "personnel_elect_elect"},
            {"personnel_elect_entity", "personnel_elect_elector"},
            {"transaction_transfer-ownership_artifact", "transaction_transfer-ownership_thing"},
            {"transaction_transfer-ownership_buyer", "transaction_transfer-ownership_recipient"},
    }).collect(Collectors.toMap(data -> data[0], data -> data[1]));

    public Resource eventArgumentType(String argName) {
        final String initialResult = argName.replace('.', '_').replace(':', '_').toLowerCase(Locale.ENGLISH);

        return ResourceFactory.createResource(NAMESPACE_STATIC +
                eventArgumentSpecialCases.getOrDefault(initialResult, initialResult));
    }

    public Resource eventArgumentTypeNotLowercase(String argName) {
        return ResourceFactory.createResource(NAMESPACE_STATIC + argName);
    }

    public boolean typeAllowedToHaveAName(Resource type) {
        return TYPES_WHICH_CAN_HAVE_NAMES.contains(type);
    }

    public boolean typeAllowedToHaveTextValue(Resource type) {
        return TYPES_WHICH_CAN_HAVE_TEXT_VALUES.contains(type);
    }

    public boolean typeAllowedToHaveNumericValue(Resource type) {
        return TYPES_WHICH_CAN_HAVE_NUMERIC_VALUES.contains(type);
    }

    public ResourcePair relationArgumentTypes(Resource relation) {
        // TODO("not implemented");
        // To change body of created functions use File | Settings | File Templates.
        return null;
    }

}

class RPISeedlingOntologyMapper implements OntologyMapping {
    private SeedlingOntologyMapper seedlingOM = new SeedlingOntologyMapper();
    protected String NAMESPACE = SeedlingOntologyMapper.NAMESPACE_STATIC;
    Resource FILLER = ResourceFactory.createResource(NAMESPACE + "FillerType");

    public Set<String> entityShortNames() {
        HashSet<String> hs = new HashSet<>();
        hs.add(seedlingOM.entityShortNames() + "FILLER");
        return hs;
    }

    public Resource entityType(String ontology_type) {
        if (ontology_type.equals("FILLER") || ontology_type.equals("String"))
            return FILLER;
        else
            return seedlingOM.entityType(ontology_type);
    }

    public Resource relationType(String relationName) {
        if (relationName.contains("FILLER"))
            return FILLER;
        else
            return seedlingOM.relationType(relationName);
    }

    public Resource eventType(String eventName) {
        return seedlingOM.eventType(eventName);
    }

    public Resource eventArgumentType(String argName) {
        return seedlingOM.eventArgumentType(argName);
    }

    public boolean typeAllowedToHaveAName(Resource type) {
        return seedlingOM.typeAllowedToHaveAName(type);
    }

    public boolean typeAllowedToHaveTextValue(Resource type) {
        return seedlingOM.typeAllowedToHaveTextValue(type);
    }

    public boolean typeAllowedToHaveNumericValue(Resource type) {
        return seedlingOM.typeAllowedToHaveNumericValue(type);
    }

    public ResourcePair relationArgumentTypes(Resource relation) {
        // TODO("not implemented");
        // To change body of created functions use File | Settings | File Templates.
        return null;
    }

}
