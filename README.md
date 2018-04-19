# gaia-interchange

This repository contains resources to support GAIA's proposed DARPA AIDA inter-TA interchange format.  It consists of:

* a formal representation of the format in terms of an OWL ontology in `interchange-format.ttl`.  With a handful of simple rules about what classes of annotations can go where (to be provided shortly), this can completely define what a valid message in this interchange format is.

* code to translate from the TAC KBP Coldstart++ KB format into this format.  Both Python (`gaia-interchange/gaia_interchange/coldstart2gaia.py`) and JVM (`gaia-interchange/src/main/java/edu/isi/gaia/ColdStart2Gaia.kt`; usable from Java) versions are provided as examples of how to use the format in code.

## Running the Python converter

* The Python code is not runnable until we release a few internal utilities it uses, which can be done as soon as anyone lets us know they want to try it out.
* The Python code writes only to document-shattered Turtle files.

## Running the Kotlin converter

Use `mvn install` to build with Maven.  The program takes a single parameter file in YAML format.  See the `main` method of `gaia-interchange/src/main/java/edu/isi/gaia/ColdStart2Gaia.kt` for a description of the required parameters.  The Kotlin format can write to document-shattered pretty-printed Turtle files or a single large file in N-triples format for bulk loading.
