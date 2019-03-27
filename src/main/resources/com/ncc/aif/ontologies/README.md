# AIDA Ontologies

The following is a listing of the ontologies found at AIDA-Interchange-Format/src/main/resources/com/ncc/aif/ontologies.
Each section in this README contains a short description of what they are built from, what they are used for,
and any other points of relevant information that might be of some use to individuals using these files.

## Differences Between *Ontology and *OwlOntology Files

Some ontologies have a corresponding file with a similar name such as SeedlingOntology and SeedlingOwlOntology. In these
cases, the *OwlOntology file uses strict OWL and won't include third-party properties/classes
like schema:rangeIncludes. The *Ontology files are not restricted and make use of third-party properties/classes to simplify definitions.
Both the *OwlOntology and *Ontology files define the same vocabulary.

# AIF Ontology Files

### AidaDomainOntologiesCommon

A collection of top level classes all Domain Ontologies reference.  Additionally, there are
also a few universal classes that include important properties such as the CanHaveNumericValue class.  This ontology is
a hand-written source file and is not generated.

### InterchangeOntology

This ontology contains the Interchange vocabulary and specifies how the defined classes and properties may interact.
It is defined using strict OWL and should be able to be used with OWL reasoners.

# Domain Ontologies

## LDC Ontology Files

This Domain Ontology was provided by LDC as an evolution of the SeedlingOntology and SeedlingOwlOntology and will be used for the M18 evaluation.

- LDCOntology
- LDCOwlOntology

## Seedling Ontology Files

This Domain Ontology was created from the first cut of data from LDC and was primarily used by performers during the M9 evaluation.
Ideally, this will become a legacy ontology replaced by LDCOntology and LDCOwlOntology, but until we completely remove data from pre-M9, these
Ontology files will remain for performers to use.

- SeedlingOntology
- SeedlingOwlOntology

## Program-Wide Ontology Files

The Entities, Events, and Relations ontologies contain the vocabulary that will be used for defining messages post-M18 evaluation.

### EntityOntology

A collection of Entity classes determined by the AIDA Ontology Working Group. The term 'Entity' refers to
a physical being or intangible concept that either interacts or is subject to interaction.

### EventOntology

A collection of Event classes determined by the AIDA Ontology Working Group. The term 'Event' refers to
something that occurs when two or more Entities interact in some way.  Some examples being building weaponry, signing a
treaty, and the moving of people from one place to another.

### RelationOntology

A collection of Relation Type classes determined by the AIDA Ontology Working Group. The term 'Relation' refers to
a connection between two or more Entities. This includes such things as the material used to create
a weapon, the nationality of an individual and the title an employee holds.
