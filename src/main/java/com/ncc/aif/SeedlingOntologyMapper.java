package com.ncc.aif;

import com.google.common.collect.*;
import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;


import java.util.*;

/**
 * The Seedling domain ontology.
 * <p>
 * For the moment, this is hard-coded to match Seedling.
 */
public final class SeedlingOntologyMapper implements OntologyMapping {
    protected static final String NAMESPACE_STATIC = "https://tac.nist.gov/tracks/SM-KBP/2018/ontologies/SeedlingOntology#";

    static final Resource PERSON = ResourceFactory.createResource(NAMESPACE_STATIC + "Person");
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

    public static final ImmutableSet<Resource> ENTITY_TYPES =
            ImmutableSet.of(PERSON, ORGANIZATION, LOCATION, GPE, FACILITY);

    public static final ImmutableSet<Resource> FILLER_TYPES_WHICH_CAN_HAVE_NAMES =
            ImmutableSet.of(WEAPON, VEHICLE, LAW);

    public final ImmutableSet<Resource> TYPES_WHICH_CAN_HAVE_NAMES =
            ImmutableSet.<Resource>builder()
                    .addAll(ENTITY_TYPES)
                    .addAll(FILLER_TYPES_WHICH_CAN_HAVE_NAMES)
                    .build();

    public final ImmutableSet<Resource> TYPES_WHICH_CAN_HAVE_TEXT_VALUES =
            ImmutableSet.of(RESULTS, TIME, MONEY, URL);

    public final ImmutableSet<Resource> TYPES_WHICH_CAN_HAVE_NUMERIC_VALUES =
            ImmutableSet.of(AGE, NUMERICAL_VALUE);

    private final ImmutableMap<String, String> SEEDLING_EVENT_TYPES_NIST =
            Maps.toMap(ImmutableList.of(
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
                    "Transaction.TransferOwnership"), it -> it);

    private final ImmutableList<String> SEEDLING_EVENT_TYPES = ImmutableList.of(
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
            "PERSONNEL_END-POSITION");

    private final ImmutableList<String> SEEDLING_EVENT_TYPES_WITH_S =
            ImmutableList.copyOf(Lists.transform(SEEDLING_EVENT_TYPES, it -> it.replace('_', '.')));

/*
    private final ImmutableMap<String, String> SEEDLING_EVENT_TYPES_MAP =
            Maps.toMap(SEEDLING_EVENT_TYPES, it -> it);

    private final ImmutableMap<String, String> SEEDLING_EVENT_TYPES_WITH_S_MAP =
            Maps.toMap(SEEDLING_EVENT_TYPES_WITH_S, it -> it);
*/

    private final ImmutableMap<String, String> EVENT_TYPES_STRMAP =
            ImmutableMap.<String, String>builder()
                    .putAll(Maps.toMap(SEEDLING_EVENT_TYPES, it -> it))
                    // or seedling types with .s instead of underscores (more ACE-like)
                    .putAll(Maps.toMap(SEEDLING_EVENT_TYPES_WITH_S, it -> it))
                    .putAll(SEEDLING_EVENT_TYPES_NIST)
                    // or these remaining special cases
                    .put("BUSINESS.END-ORG", "BUSINESS_END")
                    .put("BUSINESS.MERGE-ORG", "BUSINESS_MERGE")
                    // needed to read RPI Seedling output
                    .put("CONTACT.PHONE-WRITE", "CONTACT_CORRESPONDENCE")
                    .put("PERSONNEL.END-POSITION", "PERSONNEL_END-POSITION")
                    .build();

    private final ImmutableMap<String, Resource> EVENT_TYPES =
            Maps.toMap(EVENT_TYPES_STRMAP.keySet(),
                    it -> ResourceFactory.createResource(NAMESPACE_STATIC + EVENT_TYPES_STRMAP.get(it)));
/*
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
*/
    // these are currently unused
    // here for documentation only
    private static final ImmutableSet<String> NOT_IN_SEEDLING_BUT_REVERSE_IS = ImmutableSet.of(
            "students", "births_in_city", "births_in_country",
            "residents_of_city", "residents_of_stateorprovince", "residents_of_country",
            "shareholders", "founded_by", "top_members_employees", "members", "subsidiaries",
            "city_of_headquarters", "stateorprovince_of_headquarters"
    );
    private static final ImmutableSet<String> NOT_IN_SEEDLING = ImmutableSet.of(
            "city_of_death", "deaths_in_city",
            "stateorprovince_of_death", "deaths_in_stateorprovince",
            "country_of_death", "deaths_in_country", "country_of_headquarters", "alternate_names",
            "number_of_employees_members", "alternate_names", "date_founded", "date_of_death", "date_dissolved",
            "cause_of_death", "charges", "likes", "dislikes",
            "PART-WHOLE.Geographical", "GEN-AFF.Org-Location", "PART-WHOLE.Artifact");

    private static final String PERSONAL_SOCIAL_FAMILY = "persoc_fam";
    private static final String PERSONAL_SOCIAL_UNSPECIFIED = "persoc_unspc";
    private static final String MEMBER_RELIGIOUS_ETHNIC_GROUP = "genafl_more";

    private static final String MEMBERSHIP = "orgafl_empmem";
    private static final String ALUM = "orgafl_stualm";

    private static final String WEBSITE = "genafl_orgweb";
    private static final String PERSONAL_SOCIAL_BUSINESS = "persoc_bus";
    private static final String PERSONAL_SOCIAL_ROLE = "persoc_role";
    private static final String RESIDENT = "phys_resident";
    private static final String INVESTOR = "orgafl_invshar";
    private static final String FOUNDER = "orgafl_found";
    private static final String LEADER = "orgafl_lead";
    private static final String HEADQUARTERS = "phys_orghq";
    private static final String SUBSIDIARY = "partwhole_subsid";
    private static final String LOCATED_NEAR = "phys_locnear";

    private static final String PART_WHOLE_MEMBER = "partwhole_membership";
    private static final String PART_WHOLE_SUBSIDIARY = "";

    private static final ImmutableMap<String, String> RELATION_SPECIAL_CASES =
            ImmutableMap.<String, String>builder()
                    .put("children", PERSONAL_SOCIAL_FAMILY)
                    // TODO: this same relation seems to be used for organizational relationships
                    // we ignore that for now
                    .put("parents", PERSONAL_SOCIAL_FAMILY)
                    .put("other_family", PERSONAL_SOCIAL_FAMILY)
                    .put("siblings", PERSONAL_SOCIAL_FAMILY)
                    .put("spouse", PERSONAL_SOCIAL_FAMILY)
                    .put("PER-SOC.Family", PERSONAL_SOCIAL_FAMILY)
                    .put("PER-SOC.Lasting-Personal", PERSONAL_SOCIAL_UNSPECIFIED)
                    .put("PER-SOC.Business", PERSONAL_SOCIAL_BUSINESS)
                    .put("title", PERSONAL_SOCIAL_ROLE)
                    .put("employee_or_member_of", MEMBERSHIP)
                    .put("ORG-AFF.Employment", MEMBERSHIP)
                    .put("ORG-AFF.Sports-Affiliation", MEMBERSHIP)
                    .put("ORG-AFF.Membership", MEMBERSHIP)
                    .put("PART-WHOLE.Artifact", PART_WHOLE_MEMBER)
                    .put("member_of", MEMBERSHIP)
                    .put("schools_attended", ALUM)
                    .put("cities_of_residence", RESIDENT)
                    .put("statesorprovinces_of_residence", RESIDENT)
                    .put("countries_of_residence", RESIDENT)
                    .put("holds_shares_in", INVESTOR)
                    .put("ORG-AFF.Investor-Shareholder", INVESTOR)
                    .put("organizations_founded", FOUNDER)
                    .put("ORG-AFF.Founder", FOUNDER)
                    .put("top_member_employee_of", LEADER)
                    .put("headquarters_in_city", HEADQUARTERS)
                    .put("headquarters_in_stateorprovince", HEADQUARTERS)
                    .put("headquarters_in_country", HEADQUARTERS)
                    .put("political_religious_affiliation", MEMBER_RELIGIOUS_ETHNIC_GROUP)
                    .put("GEN-AFF.Citizen-Resident-Religion-Ethnicity", MEMBER_RELIGIOUS_ETHNIC_GROUP)
                    .put("PART-WHOLE.Subsidiary", SUBSIDIARY)
                    .put("origin", MEMBER_RELIGIOUS_ETHNIC_GROUP)
                    .put("website", WEBSITE)
                    .put("religion", MEMBER_RELIGIOUS_ETHNIC_GROUP)
                    .put("PHYS.Near", LOCATED_NEAR)
                    .put("PHYS.Located", LOCATED_NEAR)
                    .put("GEN-AFF.Org-Location", LOCATED_NEAR)
                    .put("Part-Whole.Subsidiary", PART_WHOLE_SUBSIDIARY)
                    .build();

    private final ImmutableMap<String, String> RELATION_TYPES_NIST =
            Maps.toMap(ImmutableList.of(
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
                    "Physical.Resident"), it -> it);

    private final ImmutableMap<String, String> RELATION_TYPES_STRMAP =
            ImmutableMap.<String, String>builder()
                    .putAll(Maps.toMap(ImmutableList.of(
                            // these are the seedling ontology types themselves,
                            // in case systems provide them directly
                            "genafl_apora", MEMBER_RELIGIOUS_ETHNIC_GROUP, "genafl_opra", WEBSITE,
                            "genafl_perage", "genafl_spon", "measurement_count",
                            MEMBERSHIP, FOUNDER, INVESTOR,
                            LEADER, "orgafl_own", ALUM,
                            PART_WHOLE_MEMBER, SUBSIDIARY, PERSONAL_SOCIAL_BUSINESS,
                            PERSONAL_SOCIAL_FAMILY, PERSONAL_SOCIAL_ROLE, PERSONAL_SOCIAL_UNSPECIFIED,
                            LOCATED_NEAR, HEADQUARTERS, "phys_orglocorig",
                            RESIDENT), it -> it)) // the types in the list don't need special treatment
                    .putAll(RELATION_SPECIAL_CASES)
                    .putAll(RELATION_TYPES_NIST)
                    .build();

    private final ImmutableMap<String, Resource> RELATION_TYPES =
            Maps.toMap(RELATION_TYPES_STRMAP.keySet(),
                    it -> ResourceFactory.createResource(NAMESPACE_STATIC + RELATION_TYPES_STRMAP.get(it)));

    String NAMESPACE = NAMESPACE_STATIC;

    private final ImmutableMap<String, Resource> shortNames =
            ImmutableMap.<String, Resource>builder()
                    .put("PER", PERSON)
                    .put("ORG", ORGANIZATION)
                    .put("LOC", LOCATION)
                    .put("FAC", FACILITY)
                    .put("GPE", GPE)
                    .put("FILLER", FILLER)
                    .build();

    public Set<String> entityShortNames() {
        return shortNames.keySet();
    }

    public Resource entityType(String ontology_type) {
        Resource retVal;

        if (ontology_type.equals("STRING") || ontology_type.equals("String"))
            retVal = FILLER;
        else
            retVal = shortNames.get(ontology_type);

        if (retVal == null)
            throw new RuntimeException("Unknown ontology type " + ontology_type);
        else
            return retVal;
    }

    public Resource relationType(String relationName) {
        return RELATION_TYPES.get(relationName);
    }
    /*?: throw NoSuchElementException("Unknown relation type: $relationName. Known relation " +
            "and event types ${RELATION_TYPES.keys}")*/

    public Resource eventType(String eventName) {
        return EVENT_TYPES.get(eventName);
    }

    /*?: throw NoSuchElementException("Unknown event type: $eventName. Known relation " +
            "and event types: ${EVENT_TYPES.keys}")*/

/* Java 8 Collections approach
    private static final Map<String, String> eventArgumentSpecialCases =
            Stream.of(new String[][]{
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
*/

    private static final ImmutableMap<String, String> eventArgumentSpecialCases =
            ImmutableMap.<String, String>builder()
                    .put("conflict_demonstrate_entity", "conflict_demonstrate_demonstrator")
                    .put("contact_meet_entity", "contact_meet_participant")
                    .put("contact_phone-write_entity", "contact_correspondence_participant")
                    .put("contact_phone-write_place", "contact_correspondence_place")
                    .put("contact_phone-write_time", "contact_correspondence_time")
                    .put("justice_appeal_plaintiff", "justice_appeal_prosecutor")
                    .put("personnel_end-position_entity", "personnel_end-position_organization")
                    .put("personnel_elect_person", "personnel_elect_elect")
                    .put("personnel_elect_entity", "personnel_elect_elector")
                    .put("transaction_transfer-ownership_artifact", "transaction_transfer-ownership_thing")
                    .put("transaction_transfer-ownership_buyer", "transaction_transfer-ownership_recipient")
                    .build();

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
    private Resource FILLER = ResourceFactory.createResource(NAMESPACE + "FillerType");

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
