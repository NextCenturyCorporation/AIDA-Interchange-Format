import os
import sys
import unittest
from io import BytesIO

from rdflib import Graph, URIRef, RDF

from aida_interchange import aifutils
from aida_interchange.bounding_box import Bounding_Box
from aida_interchange.ldc_time_component import LDCTimeComponent, LDCTimeType
from aida_interchange.rdf_ontologies import ldc_ontology, interchange_ontology
from aida_interchange.claim import Claim
from aida_interchange.claim_component import ClaimComponent

sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), '../')))

prefix = "https://www.caci.com/"

def get_initialized_graph_and_system():
    graph = aifutils.make_graph()
    graph.bind('test', prefix)
    system = aifutils.make_system_with_uri(graph, prefix + "testSystem")
    return graph, system

# Running these tests will output the examples to the console
class ClaimExample(unittest.TestCase):
    test_dir_path = "./output"

    
    def new_file(self, g, test_name):
        if self.test_dir_path:
            f = open(self.test_dir_path + "/" + test_name, "wb+")
            f.write(bytes(g.serialize(format='turtle'), 'utf-8'))
            f.close()

    def dump_graph(self, g, description):
        print("\n\n======================================\n"
              "{!s}\n"
              "======================================\n\n".format(description))
        serialization = BytesIO()
        # need .buffer because serialize will write bytes, not str
        g.serialize(destination=serialization, format='turtle')
        print(serialization.getvalue().decode('utf-8'))
        
    def create_base_claim(self):
        g, system = get_initialized_graph_and_system()
        claimObject = Claim()

        validComponentKE = aifutils.make_entity(g, prefix + "entities/validComponentKE", system)

        validProtoType = aifutils.make_entity(g, prefix + "entities/validID1", system)
        
        validSameAsCluster1 = aifutils.make_cluster_with_prototype(g, prefix + "cluster/clusterA/SameAsCluster/ClusterID1", validProtoType, system)
        validSameAsCluster2 = aifutils.make_cluster_with_prototype(g, prefix + "cluster/clusterB/SameAsCluster/ClusterID2", validProtoType, system)
        validSameAsCluster3 = aifutils.make_cluster_with_prototype(g, prefix + "cluster/clusterC/SameAsCluster/ClusterID3", validProtoType, system)

        validClaimComponent                = ClaimComponent()
        validClaimComponent.setName        = "Some Agency"
        validClaimComponent.setIdentity    = "Q37230"
        validClaimComponent.addType        = "Q47913"
        validClaimComponent.setProvenance  = "Some Text from Document as Provenance"
        validClaimComponent.setKe          = validComponentKE
        validXClaimComponent = aifutils.make_claim_component(g, prefix + "SomeAgency", validClaimComponent, system)
        
        validClaimComponent                = ClaimComponent()
        validClaimComponent.setName        = "Some Agency2"
        validClaimComponent.setIdentity    = "Q8333"
        validClaimComponent.addType        = "Q47913"
        validClaimComponent.addType        = "Q6"
        validClaimComponent.setProvenance  = "Some Text from Document as Provenance"
        validClaimComponent.setKe          = validComponentKE
        validX2ClaimComponent = aifutils.make_claim_component(g, prefix + "SomeAgency2", validClaimComponent, system)

        validClaimComponent                = ClaimComponent()
        validClaimComponent.setName        = "Some News Anchor"
        validClaimComponent.setIdentity    = "Q2096683"
        validClaimComponent.addType        = "Q1930187"
        validClaimComponent.addType        = "Q5"
        validClaimComponent.setProvenance  = "Some Text from Document as Provenance"
        validClaimComponent.setKe          = validComponentKE
        validClaimerClaimComponent = aifutils.make_claim_component(g, prefix + "SomeNewsAnchor", validClaimComponent, system)


        claimObject.addAssociatedKE = validSameAsCluster1
        claimObject.addAssociatedKE = validSameAsCluster2
        claimObject.claimer = validClaimerClaimComponent
        claimObject.addClaimSemantics = validSameAsCluster2
        claimObject.addClaimSemantics = validSameAsCluster3
        claimObject.claimTemplate = "X killed Hugo Chavez"
        claimObject.naturalLanguageDescription = "Claimer Y claims X killed Hugo Chavez"
        claimObject.subtopic = "Some Sub TubTopic: Who killed Hugo Chavez"
        claimObject.sourceDocument = "Some source Doc"
        claimObject.topic = "Some Main Topic: Hugo Chavez"
        claimObject.addXVariable = validXClaimComponent
        claimObject.addXVariable = validX2ClaimComponent
        
        return g, system, claimObject
        
    def test_create_minimal_claim(self):
    
        g, system, claimObject = self.create_base_claim()
        
        aifutils.make_claim(g, prefix + "myFirstMinClaim", claimObject, system)

        self.new_file(g, "test_create_minimal_claim.ttl")
        self.dump_graph(g, "Example of a minimal Claim")        

    def test_create_full_claim(self):
        g, system, claimObject = self.create_base_claim()

        validComponentKE = aifutils.make_entity(g, prefix + "entities/SomeComponentKE", system)

        validClaimComponent                = ClaimComponent()
        validClaimComponent.setName        = "Some Country"
        validClaimComponent.setIdentity    = "Q717"
        validClaimComponent.addType        = "Q3624078"
        validClaimComponent.setProvenance  = "Some Text from Document as Provenance"
        validClaimComponent.setKe          = validComponentKE
        validLocationClaimComponent = aifutils.make_claim_component(g, prefix + "SomeCountry", validClaimComponent, system)        

        validClaimComponent                = ClaimComponent()
        validClaimComponent.setName        = "YouTube"
        validClaimComponent.setIdentity    = "Q866"
        validClaimComponent.addType        = "Q59152282"
        validClaimComponent.setProvenance  = "Some Text from Medium as Provenance"
        validClaimComponent.setKe          = validComponentKE
        validMediumClaimComponent = aifutils.make_claim_component(g, prefix + "Youtube", validClaimComponent, system)

        validClaimComponent                = ClaimComponent()
        validClaimComponent.setName        = "Some News Outlet"
        validClaimComponent.setIdentity    = "Q48340"
        validClaimComponent.addType        = "Q7892363"
        validClaimComponent.setProvenance  = "Some Text from Document as Provenance"
        validClaimComponent.setKe          = validComponentKE
        validclaimerAffiliationClaimComponent1 = aifutils.make_claim_component(g, prefix + "SomeNewsOutlet", validClaimComponent, system)        

        validClaimComponent                = ClaimComponent()
        validClaimComponent.setName        = "Some News Outlet2"
        validClaimComponent.setIdentity    = "Q48340"
        validClaimComponent.addType        = "Q7892363"
        validClaimComponent.setProvenance  = "Some Text from Document as Provenance"
        validClaimComponent.setKe          = validComponentKE
        validclaimerAffiliationClaimComponent2 = aifutils.make_claim_component(g, prefix + "SomeNewsOutlet2", validClaimComponent, system)        

        claimObject.importance = .8679
        claimObject.claimId = "thisClaimID1"
        claimObject.queryId = "QueryId:1776"
        claimObject.claimLocation = validLocationClaimComponent

        claimObject.addClaimerAffiliation = validclaimerAffiliationClaimComponent1
        claimObject.addClaimerAffiliation = validclaimerAffiliationClaimComponent2
        
        
        # someOtherClaim1 = URIRef(prefix + "claim/someOtherClaimID1")
        # someOtherClaim2 = URIRef(prefix + "claim/someOtherClaimID2")
        # someOtherClaim3 = URIRef(prefix + "claim/someOtherClaimID3")

        someOtherClaim1 = "someOtherClaimID1"
        someOtherClaim2 = "someOtherClaimID2"
        someOtherClaim3 = "someOtherClaimID3"

        claimObject.addIdenticalClaims = someOtherClaim1
        claimObject.addRelatedClaims = someOtherClaim1
        claimObject.addRelatedClaims = someOtherClaim2
        claimObject.addSupportingClaims = someOtherClaim2
        claimObject.addSupportingClaims = someOtherClaim3
        claimObject.addRefutingClaims = someOtherClaim1
        claimObject.addRefutingClaims = someOtherClaim2
        claimObject.addRefutingClaims = someOtherClaim3
        
        claimObject.sentiment = interchange_ontology.SentimentNeutralUnknown
        claimObject.epistemic =  interchange_ontology.EpistemicUnknown
        
        claimObject.claimMedium = validMediumClaimComponent

        #LDCTimeComponent
        startE = LDCTimeComponent(LDCTimeType.AFTER, "2014", "--02", None)
        startL = LDCTimeComponent(LDCTimeType.BEFORE, "2014", "--03", None)
        endE = LDCTimeComponent(LDCTimeType.AFTER, "2015", "--02", "---21")
        endL = LDCTimeComponent(LDCTimeType.BEFORE, "2015", "--02", "---26")
        claimObject.claimDateTime = aifutils.make_ldc_time_range(g, startE, startL, endE, endL, system)

        aifutils.make_claim(g, prefix + "myFirstFullClaim", claimObject, system)

        self.new_file(g, "test_create_full_claim.ttl")
        self.dump_graph(g, "Example of a full Claim")

if __name__ == '__main__':
    # get directory path
    if ClaimExample.test_dir_path is not None:
        if not os.path.exists(ClaimExample.test_dir_path):
            ClaimExample.test_dir_path = None
            print("Test output directory does not exist. Example turtle files will not be saved")
    else:
        print("Test output directory was not provided. Example turtle files will not be saved")

    unittest.main()
