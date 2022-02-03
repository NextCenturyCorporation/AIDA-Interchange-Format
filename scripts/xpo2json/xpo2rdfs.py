import json

prefix = """
@prefix ext:   <https://raw.githubusercontent.com/NextCenturyCorporation/AIDA-Interchange-Format/master/java/src/main/resources/com/ncc/aif/ontologies/ExtendedEntity#> .
@prefix schema: <http://schema.org/> .
@prefix olia:  <http://purl.org/olia/system.owl#> .
@prefix aidaDomainCommon: <https://raw.githubusercontent.com/NextCenturyCorporation/AIDA-Interchange-Format/master/java/src/main/resources/com/ncc/aif/ontologies/AidaDomainOntologiesCommon#> .
@prefix evt:   <https://raw.githubusercontent.com/NextCenturyCorporation/AIDA-Interchange-Format/master/java/src/main/resources/com/ncc/aif/ontologies/EventsWithFringe#> .
@prefix rdf:   <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix owl:   <http://www.w3.org/2002/07/owl#> .
@prefix rel:   <https://raw.githubusercontent.com/NextCenturyCorporation/AIDA-Interchange-Format/master/java/src/main/resources/com/ncc/aif/ontologies/EntityRelations#> .
@prefix aida:  <https://raw.githubusercontent.com/NextCenturyCorporation/AIDA-Interchange-Format/master/java/src/main/resources/com/ncc/aif/ontologies/InterchangeOntology#> .
@prefix rdfs:  <http://www.w3.org/2000/01/rdf-schema#> .
@prefix xsd: <http://www.w3.org/2001/XMLSchema#> .

<https://raw.githubusercontent.com/NextCenturyCorporation/AIDA-Interchange-Format/master/java/src/main/resources/com/ncc/aif/ontologies/DWDPredicates> rdf:type owl:Ontology ;
    rdfs:label "AIDA Phase 3 Valid DWD Predicates" ;
    rdfs:comment "This ontology was created to enumerate valid predicates, there is no type validation" ;
    owl:versionInfo "Version 0.0.1" .\n
"""

p3predicatesFile = "DWDPredicatesOntology"

file1 = open(p3predicatesFile, "w")

# file1.write(prefix);


p3OntDWDArgs = "dwdOnt"

entityArgs = set()
relationArgs = set()
eventArgs = set()

justThese = ["relations", "events"]

file = open("xpo_v4_to_be_checked4.json")
xpojson = json.load(file)

for ere in justThese:

    for dwd_q in xpojson[ere]:
        # print(dwd_q)
        # for dwd_q_object in xpojson['relations'][dwd_q]:
            # print(dwd_q_object + "==" )
            # print(xpojson['relations'][dwd_q][dwd_q_object])
        for dwd_q_object_args in xpojson[ere][dwd_q]["arguments"]:
            # print(dwd_q_object_args["name"])
            # try:
            #     print("dwdOnt:" + xpojson['relations'][dwd_q]["wd_qnode"]  + '.' + dwd_q_object_args["name"])
            #     file1.write("dwdOnt:" + dwd_q + '.' + xpojson['relations'][dwd_q]["wd_qnode"]  + '.' + xpojson['relations'][dwd_q]["name"]  + '.' + dwd_q_object_args["name"] + "\n")
            # except:
            #     print("dwdOnt:" + xpojson['relations'][dwd_q]["wd_pnode"]  + '.' + dwd_q_object_args["name"])
            #     file1.write("dwdOnt:" + dwd_q + '.' + xpojson['relations'][dwd_q]["wd_pnode"]  + '.' + xpojson['relations'][dwd_q]["name"]  + '.' + dwd_q_object_args["name"] + "\n")
            # file1.write("\ta                     owl:ObjectProperty ;\n");
            # file1.write("\trdfs:domain           ldcOnt:Evaluate.Deliberateness.Accidental ;\n");
            # file1.write("\trdfs:label            'Relation' ;\n");
            # file1.write("\trdfs:subClassOf       aidaDomainCommon:RelationArgumentType ;\n");
            # file1.write("\tschema:rangeIncludes  aidaDomainCommon:RelationType .\n\n");
            if (ere == "events"):
                eventArgs.add(dwd_q_object_args["name"]);
            # if (ere == "entities"):
            #     relationArgs.append(dwd_q_object_args["name"]);   
            if (ere == "relations"):
                relationArgs.add(dwd_q_object_args["name"]);

            # print(dwd_q_object_args["name"])
            
# print(eventArgs)
file1.write("USE THIS ONEOF STATEMENT IN SHACL FOR EVENT PREDICATES\n")
# file1.write(":DWDEventPredicates a owl:Class ;\n")
file1.write("\towl:oneOf (") 
for eventArg in eventArgs:
    file1.write("\"" + eventArg +  "\"^^xsd:string ")
    # file1.write("<eventArgs> <valid> '" + eventArg +  "'^^xsd:string . \n");
file1.write(") ;\n")
# file1.write("\trdfs:label \"DWD Event Predicates\" ;\n")
# file1.write("\trdfs:comment \"Class of individuals to denote valid Event predicates\" .\n\n")

    
# for relationArg in relationArgs:
    # file1.write("<relationArgs> <valid> '" + relationArg +  "'^^xsd:string . \n");


# file1.write(":DWDRelationPredicates a owl:Class ;\n")
file1.write("USE THIS ONEOF STATEMENT IN SHACL FOR EVENT PREDICATES\n")
file1.write("\towl:oneOf (") 
for relationArg in relationArgs:
    # file1.write("<relationArgs> <valid> '" + relationArg +  "'^^xsd:string . \n");
    file1.write("\"" + relationArg +  "\"^^xsd:string ")
# file1.write(") ;\n")
# file1.write("\trdfs:label \"DWD Relation Predicates\" ;\n")
# file1.write("\trdfs:comment \"Class of individuals to denote valid Relation predicates\" .\n")

# NOTE: Probably not create ontology for now, since there is no type check.  Maybe best to use oneof statement in the shacl instead
        
file1.close()