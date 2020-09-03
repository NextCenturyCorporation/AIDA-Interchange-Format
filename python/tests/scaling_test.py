import sys
import os
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../')))
import time
import random

from aida_interchange import aifutils
from aida_interchange.rdf_ontologies import seedling_ontology
from rdflib import URIRef


class ScalingTest():
    filename = "scalingdata.ttl"
    LDC_NS = "https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/LdcAnnotations#"
    g = aifutils.make_graph()
    system = aifutils.make_system_with_uri(g, 'http://www.test.edu/testSystem')

    # beginning sizes of data
    entity_count = 128000
    event_count = 38400
    relations_count = 200
    assertion_count = 1500

    entity_index = 1
    event_index = 1
    relation_index = 1
    assertion_index = 1

    # utility values, so taht we can easily create random things
    abc = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"

    entity_resource_list = []


    def run_scaling_test(self):
        for ii in range(200):
            print("trying : Entity count: ", self.entity_count)
            start_time = int(round(time.time() * 1000))

            self.run_single_test()

            end_time = int(round(time.time() * 1000))
            duration = (start_time - end_time) / 1000
            size = 0

            f = open(self.filename)
            if os.path.isfile(self.filename):
                size = os.path.getsize(self.filename)
            size /= 1000000.
            print("Size of output: ", size, " duration: ", duration)

            # double size of entities and events after every iteration
            self.increase()


    def run_single_test(self):
        # adds entities and events and wrties to file
        for ii in range(self.entity_count):
            self.add_entity()

        for i in range(self.event_count):
            self.add_event()

        self.write_to_file(self.filename)


    def add_entity(self):
        # add an entity
        entity_resource = aifutils.make_entity(self.g, self.get_entity_uri(), self.system)
        self.entity_resource_list.append(entity_resource)

        # sometimes add hasName, textValue, or numericValue, NOTE: This does not check type!!!
        rand = random.random()
        if rand < 0.15:
            aifutils.mark_name(self.g, entity_resource, self.get_random_string(5))
        elif rand < 0.3:
            aifutils.mark_text_value(self.g, entity_resource, self.get_random_string(7))
        elif rand < 0.4:
            aifutils.mark_numeric_value_as_double(self.g, entity_resource, random.random())

        # set the type
        type_to_use =  self.get_random_entity()
        type_assertion = aifutils.mark_type(self.g, self.get_assertion_uri(), entity_resource, type_to_use, self.system, 1.0)
        self.add_justification_and_private_data(type_assertion)


    def add_event(self):
        # add an event
        event_resource = aifutils.make_event(self.g, self.get_event_uri(), self.system)

        # add the type
        type_resource = self.EVENT_TYPES[random.randint(0, len(self.EVENT_TYPES)) - 1]
        type_assertion = aifutils.mark_type(self.g, self.get_assertion_uri(), event_resource, type_resource, self.system, 1.0)

        self.add_justification_and_private_data(type_assertion)

        # make two arguments
        for i in range(2):
            arg = URIRef(type_resource + self.get_random_suffix())
            argument = aifutils.mark_as_argument(self.g, event_resource, arg, self.get_random_entity(), self.system,
                                                                         0.785, self.get_assertion_uri())
            self.add_justification_and_private_data(argument)


    def add_justification_and_private_data(self, resource):
        docId = self.get_random_doc_id()

        # justify the type assertion
        aifutils.mark_text_justification(self.g, resource, docId, 1029, 1033, self.system, 0.973)

        # add some private data
        aifutils.mark_private_data(self.g, resource, "{ 'provenance' : '" + docId + "' }", self.system)


    def increase(self):
        self.entity_count *= 2
        self.event_count *= 2


    def get_uri(self, uri):
        return self.LDC_NS + uri


    def get_entity_uri(self):
        self.entity_index += 1
        return self.get_uri("entity-" + str(self.entity_index))


    def get_event_uri(self):
        self.event_index += 1
        return self.get_uri("event-" + str(self.event_index))


    def get_relation_uri(self):
        self.relation_index += 1
        return self.get_uri("relation-" + str(self.relation_index))


    def get_assertion_uri(self):
        self.assertion_index += 1
        return self.get_uri("assertion-" + str(self.assertion_index))


    def get_test_system_uri(self):
        return self.get_uri("testSystem")


    def get_random_doc_id(self):
        s = ""
        if random.getrandbits(1) == 1:
            s += "IC"
        else:
            s += "HC"
        s += "00"
        s += "" + str((random.randint(0, 1000)))
        s += self.abc[random.randint(0, len(self.abc) - 1)]
        s += self.abc[random.randint(0, len(self.abc) - 1)]
        s += self.abc[random.randint(0, len(self.abc) - 1)]
        return s


    def get_random_string(self, length):
        s = ""
        for i in range(0, length):
            s += self.abc[random.randint(0, len(self.abc) - 1)]
        return s


    def get_random_entity(self):
        return self.ENTITY_TYPES[random.randint(0, len(self.ENTITY_TYPES) - 1)]


    def get_random_suffix(self):
        s = "_" + self.ROLES[random.randint(0, len(self.ROLES) - 1)]
        return s


    def write_to_file(self, testname):
        print("\n\n", testname, "\n\n")
        file = open(testname, "w")
        file.write(str(self.g.serialize(format='turtle')))
        file.close()

    ENTITY_TYPES = [
        seedling_ontology.Person,
        seedling_ontology.Organization,
        seedling_ontology.Location,
        seedling_ontology.Facility,
        seedling_ontology.GeopoliticalEntity,
        seedling_ontology.FillerType,
        seedling_ontology.Business_DeclareBankruptcy,
        seedling_ontology.Business_End,
        seedling_ontology.Business_Merge,
        seedling_ontology.Business_Start,
        seedling_ontology.Conflict_Attack,
        seedling_ontology.Conflict_Demonstrate,
        seedling_ontology.Contact_Broadcast,
        seedling_ontology.Contact_Contact,
        seedling_ontology.Contact_Correspondence,
        seedling_ontology.Contact_Meet,
        seedling_ontology.Existence_DamageDestroy,
        seedling_ontology.Government_Agreements,
        seedling_ontology.Government_Legislate,
        seedling_ontology.Government_Spy,
        seedling_ontology.Government_Vote,
        seedling_ontology.Inspection_Artifact,
        seedling_ontology.Inspection_People,
        seedling_ontology.Justice_Acquit,
        seedling_ontology.Justice_Appeal,
        seedling_ontology.Justice_ArrestJail,
        seedling_ontology.Justice_ChargeIndict,
        seedling_ontology.Justice_Convict,
        seedling_ontology.Justice_Execute,
        seedling_ontology.Justice_Extradite,
        seedling_ontology.Justice_Fine,
        seedling_ontology.Justice_Investigate,
        seedling_ontology.Justice_Pardon,
        seedling_ontology.Justice_ReleaseParole,
        seedling_ontology.Justice_Sentence,
        seedling_ontology.Justice_Sue,
        seedling_ontology.Justice_TrialHearing,
        seedling_ontology.Life_BeBorn,
        seedling_ontology.Life_Die,
        seedling_ontology.Life_Divorce,
        seedling_ontology.Life_Injure,
        seedling_ontology.Life_Marry,
        seedling_ontology.Manufacture_Artifact,
        seedling_ontology.Movement_TransportArtifact,
        seedling_ontology.Movement_TransportPerson,
        seedling_ontology.Personnel_Elect,
        seedling_ontology.Personnel_EndPosition,
        seedling_ontology.Personnel_Nominate,
        seedling_ontology.Personnel_StartPosition,
        seedling_ontology.Transaction_Transaction,
        seedling_ontology.Transaction_TransferControl,
        seedling_ontology.Transaction_TransferMoney,
        seedling_ontology.Transaction_TransferOwnership,
        seedling_ontology.GeneralAffiliation_APORA,
        seedling_ontology.GeneralAffiliation_MORE,
        seedling_ontology.GeneralAffiliation_OPRA,
        seedling_ontology.GeneralAffiliation_OrganizationWebsite,
        seedling_ontology.GeneralAffiliation_PersonAge,
        seedling_ontology.GeneralAffiliation_Sponsorship,
        seedling_ontology.Measurement_Count,
        seedling_ontology.OrganizationAffiliation_EmploymentMembership,
        seedling_ontology.OrganizationAffiliation_Founder,
        seedling_ontology.OrganizationAffiliation_InvestorShareholder,
        seedling_ontology.OrganizationAffiliation_Leadership,
        seedling_ontology.OrganizationAffiliation_Ownership,
        seedling_ontology.OrganizationAffiliation_StudentAlum,
        seedling_ontology.PartWhole_Membership,
        seedling_ontology.PartWhole_Subsidiary,
        seedling_ontology.PersonalSocial_Business,
        seedling_ontology.PersonalSocial_Family,
        seedling_ontology.PersonalSocial_RoleTitle,
        seedling_ontology.PersonalSocial_Unspecified,
        seedling_ontology.Physical_LocatedNear,
        seedling_ontology.Physical_OrganizationHeadquarter,
        seedling_ontology.Physical_OrganizationLocationOrigin,
        seedling_ontology.Physical_Resident]

    EVENT_TYPES = [
        seedling_ontology.Business_DeclareBankruptcy,
        seedling_ontology.Business_End,
        seedling_ontology.Business_Merge,
        seedling_ontology.Business_Start,
        seedling_ontology.Conflict_Attack,
        seedling_ontology.Conflict_Demonstrate,
        seedling_ontology.Contact_Broadcast,
        seedling_ontology.Contact_Contact,
        seedling_ontology.Contact_Correspondence,
        seedling_ontology.Contact_Meet,
        seedling_ontology.Existence_DamageDestroy,
        seedling_ontology.Government_Agreements,
        seedling_ontology.Government_Legislate,
        seedling_ontology.Government_Spy,
        seedling_ontology.Government_Vote,
        seedling_ontology.Inspection_Artifact,
        seedling_ontology.Inspection_People,
        seedling_ontology.Justice_Acquit,
        seedling_ontology.Justice_Appeal,
        seedling_ontology.Justice_ArrestJail,
        seedling_ontology.Justice_ChargeIndict,
        seedling_ontology.Justice_Convict,
        seedling_ontology.Justice_Execute,
        seedling_ontology.Justice_Extradite,
        seedling_ontology.Justice_Fine,
        seedling_ontology.Justice_Investigate,
        seedling_ontology.Justice_Pardon,
        seedling_ontology.Justice_ReleaseParole,
        seedling_ontology.Justice_Sentence,
        seedling_ontology.Justice_Sue,
        seedling_ontology.Justice_TrialHearing,
        seedling_ontology.Life_BeBorn,
        seedling_ontology.Life_Die,
        seedling_ontology.Life_Divorce,
        seedling_ontology.Life_Injure,
        seedling_ontology.Life_Marry,
        seedling_ontology.Manufacture_Artifact,
        seedling_ontology.Movement_TransportArtifact,
        seedling_ontology.Movement_TransportPerson,
        seedling_ontology.Personnel_Elect,
        seedling_ontology.Personnel_EndPosition,
        seedling_ontology.Personnel_Nominate,
        seedling_ontology.Personnel_StartPosition,
        seedling_ontology.Transaction_Transaction,
        seedling_ontology.Transaction_TransferControl,
        seedling_ontology.Transaction_TransferMoney,
        seedling_ontology.Transaction_TransferOwnership]

    ROLES = [
        "Attacker",
        "Instrument",
        "Place",
        "Target",
        "Time",
        "Broadcaster",
        "Place",
        "Time",
        "Participant",
        "Place",
        "Participant",
        "Time",
        "Participant",
        "Affiliate",
        "Affiliation",
        "Affiliation",
        "Person",
        "Entity",
        "Sponsor",
        "Defendant",
        "Prosecutor",
        "Adjudicator",
        "Defendant",
        "Agent",
        "Instrument",
        "Victim",
        "Artifact",
        "Manufacturer",
        "Agent",
        "Artifact",
        "Destination",
        "Instrument",
        "Origin",
        "Time",
        "Agent",
        "Destination",
        "Instrument",
        "Origin",
        "Person",
        "Employee",
        "Organization",
        "Person",
        "Entity",
        "Place",
        "Beneficiary",
        "Giver",
        "Recipient",
        "Thing",
        "Time"]

if __name__ == "__main__":
    ScalingTest().run_scaling_test()
