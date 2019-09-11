# AIDA Interchange Format (AIF)

This repository contains resources to support the AIDA Interchange Format (AIF).  It consists of:

*    a formal representation of the format in terms of an OWL ontology in `java/src/main/resources/com/ncc/aif/ontologies/InterchangeOntology`.
     This ontology can be validated using the SHACL constraints file in
     `java/src/main/resources/com/ncc/aif/aida_ontology.shacl`.

*    utilities to make it easier to work with this format.  Java utilities are
     in `java/src/main/java/com/ncc/aif/AIFUtils.java`, which can be used by adding
     a Maven dependency on `com.ncc:aida-interchange:1.1.0-SNAPSHOT`.  A
     Python translation of these utilities is in
     `python/aida_interchange/aifutils.py`.

*    examples of how to use AIF. These are given in Java in the unit tests under
     `java/src/test/java/com/ncc/aif/ExamplesAndValidationTests`.  A Python
     translation of these examples is in `python/tests/Examples.py`.  If you run either set of
     examples, the corresponding Turtle output will be dumped.

*    the `validateAIF` command line utility that can be used to validate AIF `.ttl` files. See below for details. 

We recommend using Turtle format for AIF when working with single document files (for
readability) but N-Triples for working with large KBs (for speed).

# Installation

For instructions on installing the Java code, see the [AIF Java README](https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/tree/master/java)

For instructions on installing the Python code, see the [AIF Python README](https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/tree/master/python)

# FAQ

Please see `FAQ.md` for frequently asked questions.

# Contact

AIF was designed by Ryan Gabbard (gabbard@isi.edu) and Pedro Szekely
(pszekeley@isi.edu) of USC ISI.  Gabbard also wrote the initial
implementations of the associated tools.  The tools are now supported
and by Next Century. For questions related to AIF, please contact Craig 
Warsaw (craig.warsaw@nextcentury.com). 

The open repository will support an open NIST evaluation. For
questions related to this evaluation, please contact Hoa Dang
(hoa.dang@nist.gov).

# Legal Stuff

This material is based on research sponsored by DARPA under agreement
number FA8750-18- 2-0014 and FA875018C0010-HR0011730814.  The
U.S. Government is authorized to reproduce and distribute reprints for
Governmental purposes notwithstanding any copyright notation thereon.

The views and conclusions contained herein are those of the authors
and should not be interpreted as necessarily representing the official
policies or endorsements, either expressed or implied, of DARPA or the
U.S. Government.

The AIF repository has been approved by DARPA for public release under
Distribution Statement "A" (Approved for Public Release, Distribution
Unlimited).
