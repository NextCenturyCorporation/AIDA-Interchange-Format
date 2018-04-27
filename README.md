# gaia-interchange

This repository contains resources to support GAIA's proposed DARPA AIDA inter-TA interchange
format.  It consists of:

*    a formal representation of the format in terms of an OWL ontology in `interchange-format.ttl`.
     This ontology can be validated using the SHACL constraints file in
     `src/main/resources/edu/isi/gaia/aida_ontology.shacl`.

*    code to translate from the TAC KBP Coldstart++ KB format into this format.  Both Python
     (`gaia-interchange/gaia_interchange/coldstart2gaia.py`) and
     JVM (`gaia-interchange/src/main/java/edu/isi/gaia/ColdStart2Gaia.kt`; usable from Java)
     versions are provided as examples of how to use the format in code.

## Running the validator

To be added soon.

## Running the Kotlin converter

If you don't have it already, install Apache Maven.  From the root of this repository, run
`mvn install`.   Repeat `mvn install` if you pull an updated version of the code.

To convert a ColdStart KB, run `target/appassembler/bin/coldstart2Gaia`. It takes three arguments:
* the ColdStart KB to convert
* the output path
*     one of `full` or `shatter`. If `full`, the output KB is written to a single file in blocked
      Turtle format.  If `shatter`, the output path is interpreted as a directory and a separate
      document-level KB will be written in pretty-printed Turtle format for each file mentioned
      in the KB.

## Running the Python converter

*    The Python code is not runnable until we release a few internal utilities it uses, which can
     be done as soon as anyone lets us know they want to try it out.
* The Python code writes only to document-shattered Turtle files.

# Legal Stuff

This material is based on research sponsored by DARPA under agreement number FA8750-18- 2-0014.
The U.S. Government is authorized to reproduce and distribute reprints for Governmental purposes
notwithstanding any copyright notation thereon.

The views and conclusions contained herein are those of the authors and should not be interpreted
as necessarily representing the official policies or endorsements, either expressed or implied, of
DARPA or the U.S. Government.
