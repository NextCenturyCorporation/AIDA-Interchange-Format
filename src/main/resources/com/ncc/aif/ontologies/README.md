# AIF Ontologies

The following is a listing of the ontologies found at AIDA-Interchange-Format/src/main/resources/com/ncc/aif/ontologies.
Each ontology will contain a short description of what they are built from, what they are used for,
and any other points of relevent information that might be of some use to individuals using these files.

# Differences Between Ontology and OwlOntology

Some ontologies have a corresponding file with a similar name such as SeedlingOntology and SeedlingOwlOntology. In these
cases, the Owl ontology signifyies that the rdf file is compatible with strict owl, in particular using the correct syntax
for more complex properties of classes such as RangeIncludes. The non-Owl ontology uses a simpler structure for easier extraction
of important values.  Both the Owl and non-Owl ontology should define the same vocabulary.

## AidaDomainOntologiesCommon

A collection of top level classes most other ontologies reference as the highest level class.  Additionally, there are
also a few universal classes that include important properties such as the CanHaveNumericValue class.  This ontology was
created by individually selecting those class values and not from any other source of data.

## EntityOntology

A collection of Entity classes determined by the Ontology working group.  Entity referring to
a physical being or intangible concept that either interacts or is subject to interaction.  This in combination with Events
and Relations ontologies contain the vocabulary that will be used for defining messages post-M18 evaluation.

## EventOntology

A collection of Event classes determined by the Ontology working group.  Event referring to
an experience that occurs when two or more Events interact in some way.  Some examples being building weaponry, signing a
treaty, and the moving of people from one place to another. This in combination with Entity
and Relations ontologies contain the vocabulary that will be used for defining messages post-M18 evaluation.

## InterchangeOntology

The main purpose of this ontology is to contain the Interchange vocabulary and to be used by performers using strict OWL
as reference to the classes and properties that are used in our not-strict OWL ontologies. In particular, it contains
the OWL definitions for domain and range which are usually omitted from the not-strict OWL ontologies.

# LDCOntology

The ontology was given by LDC as an evolution of the SeedlingOntology and will be used for the M18 evaluation.  This will eventually
replace SeedlingOntology once the original LDC data has been removed from our programs and updated with the newest version.

## LDCOwlOntology

The ontology was given by LDC as an evolution of the SeedlingOwlOntology and will be used for the M18 evaluation.  This will eventually
replace SeedlingOwlOntology once the original LDC data has been removed from our programs and updated with the newest version.

## RelationOntology

A collection of Relation Type classes determined by the Ontology working group.  Relations referring to
A specification of the connection between two or more Entities. This includes such things as the material used to create
a weapon, the nationality of an individual and the title an employee holds. This in combination with Entities
and Events ontologies contain the vocabulary that will be used for defining messages post-M18 evaluation.

## SeedlingOntology

The ontology created from the first cut of data from LDC and was primarily used by performers during the M9 evaluation.
Ideally, this will become a legacy ontology replaced by LDCOntology, but until we completely remove data from pre-M9, this
Ontology file will remain for performers to use.

## SeedlingOwlOntology

The ontology created from the first cut of data from LDC and was primarily used by performers during the M9 evaluation.
Ideally, this will become a legacy ontology replaced by LDCOwlOntology, but until we completely remove data from pre-M9, this
Ontology file will remain for performers to use.
