# Running OntologyGeneration

This class will create Java classes based on the ontology or ontologies used as arguments when running Main.
The class will appear in `src/main/java/com/ncc/aif` and the name of the class created will be based on the
name of the ontology URI used as the program argument.

The class will contain public static Resource variables based on the names of the classes specified in the given
Ontologies. If more than one ontology is passed in as an argument, the number of classes created will be equal to the number of
Ontologies used.

To specify the program arguments in your IDE, navigate to the Edit Configurations for the OntologyGeneration class. Then add the path
to the Ontology as the argument.

If no program arguments are specified, the class will generate the following files:
* src/main/resources/com/ncc/aif/ontologies/
  * AidaDomainOntologiesCommon.java
  * EntityOntology.java
  * EventOntology.java
  * InterchangeOntology.java
  * LDCOntology.java
  * LDCOwlOntology.java
  * RelationOntology.java
* src/test/resources/com/ncc/aif/ShaclShapes.java
