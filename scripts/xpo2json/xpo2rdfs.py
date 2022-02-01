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
"""

example = """
ldcOnt:Evaluate.Deliberateness.Accidental_Event
        a                     owl:ObjectProperty ;
        rdfs:domain           ldcOnt:Evaluate.Deliberateness.Accidental ;
        rdfs:label            "Event" ;
        rdfs:subClassOf       aidaDomainCommon:RelationArgumentType ;
        schema:rangeIncludes  aidaDomainCommon:EventType .
"""

p3predicatesFile = "p3predicates"

p3OntDWDArgs = "dwdOnt"

file = open("xpo_v4_to_be_checked4.json")
xpojson = json.load(file)

# for ere in xpojson.keys():
for dwd_q in xpojson['relations']:
    print(dwd_q)
    for dwd_q_object in xpojson['relations'][dwd_q]:
        # print(dwd_q_object + "==" )
        # print(xpojson['relations'][dwd_q][dwd_q_object])
        for dwd_q_object_args in xpojson['relations'][dwd_q]["arguments"]:
            print(dwd_q_object_args["name"])
            # try:
            #     print("dwdOnt:" + xpojson['relations'][dwd_q]["wd_qnode"]  + '.' + xpojson['relations'][dwd_q][dwd_q_object_args]["name"])
            # except:
            #     print("dwdOnt:" + xpojson['relations'][dwd_q]["wd_pnode"]  + '.' + xpojson['relations'][dwd_q][dwd_q_object_args]["name"])
            
        
        