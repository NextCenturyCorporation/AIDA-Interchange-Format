import os
import sys
import unittest
import random
from io import BytesIO

from rdflib import Graph, URIRef, RDF

from aida_interchange import aifutils
from aida_interchange.bounding_box import Bounding_Box
from aida_interchange.ldc_time_component import LDCTimeComponent, LDCTimeType
from aida_interchange.rdf_ontologies import ldc_ontology, interchange_ontology
from aida_interchange.claim import Claim
from aida_interchange.claim_component import ClaimComponent

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../')))

prefix = "http://www.test.edu/"

def get_initialized_graph_and_system():
    graph = aifutils.make_graph()
    graph.bind('test', prefix)
    system = aifutils.make_system_with_uri(graph, "http://www.test.edu/testSystem")
    return graph, system

# Running these tests will output the examples to the console
class ClaimExample(unittest.TestCase):
    test_dir_path = "./output"
    total_ttls = 10
    
    def new_file(self, g, test_name):
        if self.test_dir_path:
            print(test_name + "...")
            f = open(self.test_dir_path + "/" + test_name, "wb+")
            f.write(g.serialize(format='turtle'))
            f.close()
        
    def random_option(self, option_list):
        return random.choice(option_list)

    def create_base_claim(self, index, random_number):
        random_action = self.random_option(["killed", "fired", "transported", "attacked", "interviewed"]);
        g, system = get_initialized_graph_and_system()
        claimObject = Claim()

        validComponentKE = aifutils.make_entity(g, "http://www.caci.com/entities/validComponentKE", system)

        validProtoType = aifutils.make_entity(g, "http://www.caci.com/entities/validPrototype", system)
        
        validSameAsCluster1 = aifutils.make_cluster_with_prototype(g, "http://www.caci.com/cluster/clusterA/SameAsCluster/ClusterID1" + random_number, validProtoType, system)
        validSameAsCluster2 = aifutils.make_cluster_with_prototype(g, "http://www.caci.com/cluster/clusterB/SameAsCluster/ClusterID2" + random_number, validProtoType, system)
        validSameAsCluster3 = aifutils.make_cluster_with_prototype(g, "http://www.caci.com/cluster/clusterC/SameAsCluster/ClusterID3" + index, validProtoType, system)
        
        validClaimComponent                = ClaimComponent()
        validClaimComponent.setName        = "Some Agency" + random_number
        validClaimComponent.setIdentity    = "Q8333" + random_number
        validClaimComponent.addType        = "Q47913"
        validClaimComponent.addType        = "Q6" + random_number
        validClaimComponent.setProvenance  = "Some Text from Document as Provenance"
        validClaimComponent.setKe          = validComponentKE
        validXClaimComponent = aifutils.make_claim_component(g, "https://www.caci.com/SomeAgency" + random_number, validClaimComponent, system)

        validClaimComponent                = ClaimComponent()
        validClaimComponent.setName        = "Some News Anchor" + random_number
        validClaimComponent.setIdentity    = "Q2096683" + random_number
        validClaimComponent.addType        = "Q1930187"
        validClaimComponent.addType        = "Q5"
        validClaimComponent.setProvenance  = "Some Text from Document as Provenance"
        validClaimComponent.setKe          = validComponentKE
        validClaimerClaimComponent = aifutils.make_claim_component(g, "https://www.caci.com/SomeNewsAnchor" + random_number, validClaimComponent, system)

        claimObject.addAssociatedKE = validSameAsCluster1
        claimObject.addAssociatedKE = validSameAsCluster2
        claimObject.claimer = validClaimerClaimComponent
        claimObject.addClaimSemantics = validSameAsCluster2
        claimObject.addClaimSemantics = validSameAsCluster3
        claimObject.claimTemplate = "X " + random_action + " Person" + random_number
        claimObject.naturalLanguageDescription = "Claimer Y claims X " + random_action + " Person" + random_number
        claimObject.subtopic = "Some Sub TubTopic: Who " + random_action + " Person" + random_number
        claimObject.sourceDocument = "sourceDoc" + random_number
        claimObject.topic = "Some Main Topic: Person" + random_number
        claimObject.addXVariable = validXClaimComponent
        
        return g, system, claimObject
        
    def create_minimal_claim(self, index, random_number):
    
        g, system, claimObject = self.create_base_claim(index, random_number)
        
        aifutils.make_claim(g, "https://www.caci.com/myClaim" + index, claimObject, system)

        self.new_file(g, "test_create_minimal_claim" + index + ".ttl")      

    def create_full_claim(self, index, random_number):
        g, system, claimObject = self.create_base_claim(index, random_number)
        
        random_sentiment = self.random_option([interchange_ontology.SentimentNegative, interchange_ontology.SentimentPositive, interchange_ontology.SentimentNeutralUnknown, interchange_ontology.SentimentMixed]);
        random_epistemic = self.random_option([interchange_ontology.EpistemicFalseCertain, interchange_ontology.EpistemicFalseUncertain, interchange_ontology.EpistemicTrueCertain, interchange_ontology.EpistemicTrueUncertain, interchange_ontology.EpistemicUnknown]);

        validComponentKE = aifutils.make_entity(g, "http://www.test.edu/entities/SomeComponentKE", system)

        validClaimComponent                = ClaimComponent()
        validClaimComponent.setName        = "Some Country" + random_number
        validClaimComponent.setIdentity    = "Q717"  + random_number
        validClaimComponent.addType        = "Q3624078"
        validClaimComponent.setProvenance  = "Some Text from Document as Provenance"
        validClaimComponent.setKe          = validComponentKE
        validLocationClaimComponent = aifutils.make_claim_component(g, "https://www.caci.com/SomeCountry" + random_number, validClaimComponent, system)        

        validClaimComponent                = ClaimComponent()
        validClaimComponent.setName        = "Some News Outlet" + index
        validClaimComponent.setIdentity    = "Q48340" + index
        validClaimComponent.addType        = "Q7892363"
        validClaimComponent.setProvenance  = "Some Text from Document as Provenance"
        validClaimComponent.setKe          = validComponentKE
        validclaimerAffiliationClaimComponent1 = aifutils.make_claim_component(g, "https://www.caci.com/SomeNewsOutlet" + index, validClaimComponent, system)        

        validClaimComponent                = ClaimComponent()
        validClaimComponent.setName        = "Some News Outlet1" + random_number
        validClaimComponent.setIdentity    = "Q48340" + random_number
        validClaimComponent.addType        = "Q7892363"
        validClaimComponent.setProvenance  = "Some Text from Document as Provenance"
        validClaimComponent.setKe          = validComponentKE
        validclaimerAffiliationClaimComponent2 = aifutils.make_claim_component(g, "https://www.caci.com/SomeNewsOutlet1" + random_number, validClaimComponent, system)        

        claimObject.importance = round(random.uniform(0.1, 1.0),4)
        claimObject.claimId = "ClaimID:1492" + index
        claimObject.queryId = "QueryId:1776" + random_number
        claimObject.claimLocation = validLocationClaimComponent

        claimObject.addClaimerAffiliation = validclaimerAffiliationClaimComponent1
        claimObject.addClaimerAffiliation = validclaimerAffiliationClaimComponent2
        
        
        someOtherClaim1 = URIRef("https://www.caci.com/claim/someOtherClaimID1" + random_number)
        someOtherClaim2 = URIRef("https://www.caci.com/claim/someOtherClaimID2" + index)
        someOtherClaim3 = URIRef("https://www.caci.com/claim/someOtherClaimID3" + random_number)
        
        claimObject.addIdenticalClaims = someOtherClaim1
        claimObject.addRelatedClaims = someOtherClaim1
        claimObject.addRelatedClaims = someOtherClaim2
        claimObject.addSupportingClaims = someOtherClaim2
        claimObject.addSupportingClaims = someOtherClaim3
        claimObject.addRefutingClaims = someOtherClaim1
        claimObject.addRefutingClaims = someOtherClaim2
        claimObject.addRefutingClaims = someOtherClaim3
        
        claimObject.sentiment = random_sentiment
        claimObject.epistemic = random_epistemic

        #LDCTimeComponent
        start_year = random.randint(2010, 2020)
        start_month1 = random.randint(1, 5)
        start_month2 = start_month1 + random.randint(0, 3)
        end_month1 = start_month2 + random.randint(0, 1)
        start_day1 = random.randint(1, 14)
        end_day1 = random.randint(1, 14)
    
        if (int(random_number) % 2) == 0:
            end_year = start_year
            end_month2 = end_month1 + 1
            start_day2 = random.randint(15, 28)
            end_day2 = None
        else:
            end_year = start_year + 1
            end_month2 = end_month1 + 2
            start_day2 = None
            end_day2 = random.randint(15, 28)

        start_month1 = '--{:02}'.format(start_month1)
        start_month2 = '--{:02}'.format(start_month2)
        end_month1 = '--{:02}'.format(end_month1)
        end_month2 = '--{:02}'.format(end_month2)

        start_day1 = '---{:02}'.format(start_day1)
        if(start_day2 != None): 
            start_day2 = '---{:02}'.format(start_day2)
        end_day1 = '---{:02}'.format(end_day1)
        if (end_day2 != None):
            end_day2 = '---{:02}'.format(end_day2)

        startE = LDCTimeComponent(LDCTimeType.AFTER, start_year, start_month1, start_day1)
        startL = LDCTimeComponent(LDCTimeType.BEFORE, start_year, start_month2, start_day2)
        endE = LDCTimeComponent(LDCTimeType.AFTER, end_year, end_month1, end_day1)
        endL = LDCTimeComponent(LDCTimeType.BEFORE, end_year, end_month2,end_day2)
        claimObject.claimDateTime = aifutils.make_ldc_time_range(g, startE, startL, endE, endL, system)

        aifutils.make_claim(g, "https://www.caci.com/myFirstClaim" + index, claimObject, system)

        self.new_file(g, "test_create_full_claim" + index + ".ttl")
    
    def test_create_claims(self):
        index = self.total_ttls
        print("Creating ttl files:")
        for x in range(index):
            random_number = random.randint(0, index)
            if (random_number % 2) == 0:
                #even
                self.create_minimal_claim(str(x), str(random_number));
            else:
                #odd
                self.create_full_claim(str(x), str(random_number));
        print("\n\r" + str(index) + " ttl files completed")


if __name__ == '__main__':
    # get directory path
    if not os.path.exists(ClaimExample.test_dir_path):
        # create output directory if it does not exist
        print("Test output directory does not exist. Please wait while it is created...")
        os.mkdir(ClaimExample.test_dir_path)
        print("Directory" + ClaimExample.test_dir_path + "created")
    else:
        #empty it
        print("Please wait while the test output directory is emptied...")
        for f in os.listdir(ClaimExample.test_dir_path):
            os.remove(os.path.join(ClaimExample.test_dir_path, f))
 
        print("Directory " + ClaimExample.test_dir_path + " is empty")

    print("\n\r")    
    unittest.main()

    
    