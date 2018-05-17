# gaia-interchange

This repository contains resources to support the AIDA Interchange Format (AIF).  It consists of:

*    a formal representation of the format in terms of an OWL ontology in `interchange-format.ttl`.
     This ontology can be validated using the SHACL constraints file in
     `src/main/resources/edu/isi/gaia/aida_ontology.shacl`. **At the moment the OWL ontology has not
     been brought up-to-date with the latest changes. The SHACL file is a better guide. NCC will
     reconcile them in the next week**.

*    utilities to make it easier to work with this format.  JVM utilities are in
     `src/main/java/edu/isi/gaia/AIFUtils.kt`. Although written in Kotlin, these can be used from any
     JVM language by adding a Maven dependency on
      `edu.isi:gaia-interchange-kotlin:1.0.0-SNAPSHOT`.  There is the beginning of a Python translation of
      these utilities in `gaia_interchange/aifutils.py`.  Next Century will complete the Python
      translation of these utilities soon.

*    examples of how to use AIF. These are given in Java in the unit tests under
     `src/text/java/edu/isi/gaia/ExamplesAndValidationTests`.  There is the beginning of a Python
     translation of these examples in `tests/gaia_interchange/Examples.py`.  If you run either set of
     examples, the corresponding Turtle output will be dumped.

*    code to translate from the TAC KBP Coldstart++ KB format into this format.
     `src/main/java/edu/isi/gaia/ColdStart2AidaInterchange.kt`.

*    code to translate a simple format for entity and event mentions in images to AIF:
     `src/main/java/edu/isi/gaia/ImagesToAIF.kt`

We recommend using Turtle format for AIF when working with single document files (for
readability) but N-Triples for working with large KBs (for speed).

# Installation

* To install the JVM code, do `mvn install` from the root of this repository using Apache Maven.
        Repeat the `mvn install` if you pull an updated version of the code. You can run the tests,
        which should output the examples, by doing `mvn test`.
* The Python code is not currently set up for installation; just add it to your `PYTHONPATH`.

# Running the validator

To run the validator, run `target/appassembler/bin/validateAIF` with a single argument, a parameter
file. The parameter file should have keys and values separated by `:`. It should have either the
parameter `kbToValidate` pointing to the single Turtle format KB to validate, or it should have
`kbsToValidate` pointing to a file listing the paths of the Turtle format KBs to validate.
Additionally, it must have a parameter `domainOntology` pointing to the OWL file for the domain
ontology to validate against.  Beware that validating large KBs can take a long time. There is
a sample of a validator param file in `sample_params/validate.common_corpus.single.params`

# Running the ColdStart -> AIF Converter

To convert a ColdStart KB, run `target/appassembler/bin/coldstart2AidaInterchange`. It takes a
single argument, a key-value parameter file where keys and values are separated by `:`s.  There
are four parameters which are always required:
* `inputKBFile`: the path to the ColdStart KB to convert
* `baseUri`: the URI path to use as the base for generated assertions URIs, entity URIs, etc.  For
    example `http://www.isi.edu/aida`
* `systemUri`: a URI path to identify the system which generated the ColdStart output. For
    example `http://www.rpi.edu/tinkerbell`
* `mode`: must be `FULL` or `SHATTER`, as explained below.

If `mode` is `FULL`, then the entire ColdStartKB is converted into a single AIF RDF file in
n-triples format (n-triples is used for greater I/O speed).  The following parameters then
 apply:
 * `outputAIFFile` will specify the path to write this file to.
 * cross document coreference information present in the ColdStartKB can be discarded by setting
     the optional parameter `breakCrossDocCoref` to `true` (default `false`).
* The optional `restrictConfidencesToJustifications` parameter (default `false`) controls whether
   confidence values are attached directly to the relevant entity/relation/event/sentiment
   assertion or only to their justifications.  The former is how it should be in TA2/TA3, but the
   latter for messages from TA1 to TA2.  Note this is somewhat imperfect because ColdStart
   lacks justifications for type and link assertions, so for these confidence information will
   simply be missing when restricting to justifications.

If `mode` is `SHATTER`, the data related to each document in the ColdStart KB is written to a
separate AIF file in Turtle format.  In this case, the only other parameter is the directory
to write the output to (`outputAIFDirectory`).  The values of `breakCrossDocCoref`,
`useClustersForCoref`, and `attachConfidencesToJustifications` are fixed at `true`, `false`,
and `true`, respectively.

The following optional parameters are available in both modes:
* `useClustersForCoref` parameter (default `false`) specifies whether
      to use the "clusters" provided by the AIF format for representing coreference.  While in AIDA
      there can be uncertainty about coreference, making these clusters useful, in ColdStart
      coreference decisions were always "hard".  We provide the user with the option of whether to
      encode these coref decisions in the way they would be encoded in AIDA if there were any
      uncertainty so that users can test these data structures.

There are sample shatter and single KB param files under `sample_params/translate.*`

# `maxConfidence`

There is an example program showing how to consume AIF data in `edu.isi.gaia.MaxConfidenceEstimator`.

# `imagesToAif`

There is an example program/utility for converting a simple tab-separated format for images to AIF.
See class comment on `src/main/java/edu/isi/gaia/ImagesToAIF.kt` for details. You can run this
program by running `target/appassembler/bin/images2Aif`.

# Developing

If you need to edit the Kotlin code, just import the POM for this project using IntelliJ IDEA and
you should be ready to go.

# Contact

AIF was designed by Ryan Gabbard (gabbard@isi.edu) and Pedro Szekely (pszekeley@isi.edu) of USC ISI.
Gabbard also wrote the initial implementations of the associated tools.  The tools are now
maintained by Eddie Curley (eddie.curley@nextcentury.com)
and Clark Dorman (clark.dorman@nextcentury.com) of Next Century.

# Legal Stuff

This material is based on research sponsored by DARPA under agreement number FA8750-18- 2-0014.
The U.S. Government is authorized to reproduce and distribute reprints for Governmental purposes
notwithstanding any copyright notation thereon.

The views and conclusions contained herein are those of the authors and should not be interpreted
as necessarily representing the official policies or endorsements, either expressed or implied, of
DARPA or the U.S. Government.
