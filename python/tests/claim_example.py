# Remove the below comment once we update to python3
# -*- coding: utf-8 -*-
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

prefix = "http://www.test.edu/"

def get_initialized_graph_and_system():
    graph = aifutils.make_graph()
    graph.bind('test', prefix)
    system = aifutils.make_system_with_uri(graph, "http://www.test.edu/testSystem")
    return graph, system


# Running these tests will output the examples to the console
class ClaimExample(unittest.TestCase):
    test_dir_path = "./output"

    def new_file(self, g, test_name):
        if self.test_dir_path:
            f = open(self.test_dir_path + "/" + test_name, "wb+")
            f.write(g.serialize(format='turtle'))
            f.close()

    def dump_graph(self, g, description):
        print("\n\n======================================\n"
              "{!s}\n"
              "======================================\n\n".format(description))
        serialization = BytesIO()
        # need .buffer because serialize will write bytes, not str
        g.serialize(destination=serialization, format='turtle')
        print(serialization.getvalue().decode('utf-8'))

    def test_create_minimal_claim(self):
        g, system = get_initialized_graph_and_system()

        validComponentKE = aifutils.make_entity(g, "http://www.test.edu/entities/SomeComponentKE", system)
        validKE = aifutils.make_entity(g, "https://www.wikidata.org/wiki/Q8440", system)
        validEventRelationKE = aifutils.make_entity(g, "https://www.wikidata.org/wiki/Q210392", system)

        validXClaimComponent = ClaimComponent("https://www.caci.com/SomeAgency", "Some Agency", "Q37230", "Q47913", "Some Text from Document as Provenance", validComponentKE)
        validClaimerClaimComponent = ClaimComponent("https://www.caci.com/SomeNewsOutlet", "Some News Outlet", "Q48340", "Q7892363", "Some Text from Document as Provenance",validComponentKE)
        validLocationClaimComponent = ClaimComponent("https://www.caci.com/SomeCountry", "Some Country", "Q717", "Q3624078", "Some Text from Document as Provenance",validComponentKE)

        claimObject = Claim()

        claimObject.sourceDocument = "Some source Doc"
        claimObject.topic = "Some Main Topic: Death of Hugo Chavez"
        claimObject.subtopic = "Some Sub TubTopic: Who killed Hugo Chavez"
        claimObject.claimTemplate = "X killed Hugo Chavez"
        claimObject.xVariable = validXClaimComponent
        claimObject.naturalLanguageDescription = "Claimer Y claims X killed Hugo Chavez"
        claimObject.claimSemantics = validEventRelationKE
        claimObject.claimer = validClaimerClaimComponent
        claimObject.associatedKE = validKE

        someOtherClaim1 = aifutils.make_claim(g, "https://www.caci.com/someOtherClaim1", claimObject, system)
        someOtherClaim2 = aifutils.make_claim(g, "https://www.caci.com/someOtherClaim2", claimObject, system)
        someOtherClaim3 = aifutils.make_claim(g, "https://www.caci.com/someOtherClaim3", claimObject, system)
        
        # OPTIONAL FULL
        claimObject.importance = .8679
        claimObject.claimId = "ClaimID:1467"
        claimObject.queryId = "QueryId:12"
        claimObject.claimLocation = validLocationClaimComponent

        claimObject.claimerAffiliation = validXClaimComponent
        
        claimObject.identicalClaims = someOtherClaim1
        claimObject.relatedClaims = someOtherClaim2
        claimObject.supportingClaims = someOtherClaim3
        claimObject.refutingClaims = someOtherClaim1

        baseClaim = aifutils.make_claim(g, "https://www.caci.com/myFirstClaim", claimObject, system)

        self.new_file(g, "test_create_minimal_claim.ttl")
        self.dump_graph(g, "Example of a Claim")

if __name__ == '__main__':
    # get directory path
    ClaimExample.test_dir_path = os.environ.get("DIR_PATH", None)
    if ClaimExample.test_dir_path is not None:
        if not os.path.exists(ClaimExample.test_dir_path):
            ClaimExample.test_dir_path = None
            print("Test output directory does not exist. Example turtle files will not be saved")
    else:
        print("Test output directory was not provided. Example turtle files will not be saved")

    unittest.main()