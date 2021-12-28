# AIDA Interchange Format (AIF)

This repository contains resources to support the AIDA Interchange Format (AIF).  It consists of:

*    a formal representation of the format in terms of an OWL ontology in `java/src/main/resources/com/ncc/aif/ontologies/InterchangeOntology`.
     This ontology can be validated using the SHACL constraints file in
     `java/src/main/resources/com/ncc/aif/aida_ontology.shacl`.

*    utilities to make it easier to work with this format.  Java utilities are
     in `java/src/main/java/com/ncc/aif/AIFUtils.java`, which can be used by adding
     a Maven dependency on `com.ncc:aida-interchange:1.1.0`.  A
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

For instructions on installing the Java code, see the [AIF Java README](/java)

For instructions on installing the Python code, see the [AIF Python README](/python)

# The AIF Validator
The AIF validator is an extension of the validator written by Ryan Gabbard (USC ISI)
and converted to Java by Next Century.  This version of the validator accepts multiple
ontology files, can validate against NIST requirements (restricted AIF), and can
validate N files or all files in a specified directory.

### Running the Java AIF validator
In order to run the Java AIF validator, you must first install the AIF Java validator code. Instructions to install the Java AIF Validator code can be found in the [AIF Java README](/java).

To run the validator from the command line, run `target/appassembler/bin/validateAIF`
with a series of command-line arguments (in any order) honoring the following usage:  <br>
Usage:  <br>
`validateAIF [-hov] [--ldc] [--nist] [--nist-ta3] [--pm] [--program] [--abort[=num]] [--depth[=num]] [-d=DIRNAME] [-t=num] [--ont=FILE...]... [-f=FILE...]...`  <br>

| Switch | Description |
| ----------- | ----------- |
|`--ldc` or `--dwd`    | validate against the LDC or DWD ontology |
|`--program`| validate against the program ontology |
|`--ont=FILE ...` | validate against the OWL-formatted ontolog(ies) at the specified filename(s) |
|`--nist` | validate against the NIST restrictions |
|`--nist-ta3` | validate against the NIST hypothesis restrictions (implies `--nist`) |
|`--hypothesis-max-size=<hypothesisMaxSize>` | Specify the maximum size of a hypothesis file in MB when validating against the NIST hypothesis restrictions (`--nist-ta3`). Default is 5 |
|`--abort[=num]` | Abort validation after `[num]` SHACL violations (num > 2), or three violations if `[num]` is omitted. |
|`--depth[=num]` | Perform shallow validation in which each SHACL rule (shape) is only applied to `[num]` target nodes, or 50 nodes if `[num]` is omitted (requires -t). |
|`--pm` | Enable progress monitor that shows ongoing validation progress.  If `-t` is specified, then thread metrics are provided post-validation instead. |
|`--mem` | Use memory model for validating files (default is file-based model) |
|`-o` | Save validation report model to a file. `KB.ttl` results will be saved to KB-report*.txt, up to 1 report per thread. Output defaults to stderr. |
|`-t=num` | Specify the number of threads to use during validation. If the `--pm` option is specified, thread metrics are provided post-validation instead. |
|`-d=DIRNAME` | validate all `.ttl` files in the specified directory |
|`-f=FILE ...` | validate the specified file(s) with a `.ttl` suffix |
|`-h, --help` | This help and usage text |
|`-v, --version` | Print the validator version |

Either a file (-f) or a directory (-d) must be specified (but not both).  <br>
Exactly one of --ldc, --program, or --ont must be specified.  <br>
Ontology files can be found in `src/main/resources/com/ncc/aif/ontologies`:
- LDC (LO): `LDCOntology`
- Program (AO): `EntityOntology`, `EventOntology`, `RelationOntology`

### Validator return values
Return values from the command-line validator are as follows:
* `0 (Success)`.  There were no validation (or any other) errors.
* `1 (Validation Error)`.	All specified files were validated but at least one failed validation.  Supersedes a File Error.
* `2 (Usage Error)`.  There was a problem interpreting command-line arguments.  No validation was performed.
* `3 (File Error)`.  A file was rejected or couldn't be validated, either due to an I/O error, a validation engine error,
  or because it didn't meet certain criteria.  Logging indicates the nature of the problem(s).  Validation may
  have been performed on a subset of specified KBs.  If there is an error loading any ontologies or SHACL files,
  then no validation is performed.

### Running the validator in code
To run the validator programmatically in Java code, first use one of the public `ValidateAIF.createXXX()`
methods to create a validator object, then call one of the public `validateKB()` methods.
`createForLDCOntology()` and `createForProgramOntology()` are convenience wrappers for `create()`, which
is flexible enough to take a Set of ontologies.  All creation methods accept a flag for validating
against restricted AIF; see the JavaDocs.  Note that the original `ValidateAIF.createForDomainOntologySource()` method
remains for backward compatibility.

Once the validator has been initialized, there are several APIs to validate KBs and retrieve results.  `validateKB()`
simply returns whether or not the KB is valid.  If you want any information about why the KB was invalid, use one of
the `validateKBAndReturnXXX()` methods.  Results can be returned as multiple error reports, or as one combined report,
but note that for some validations, particularly large files with many violations, combining reports could incur
significant overhead. 

### Failing fast

The AIF Validator can be told to "fail fast," that is, exit as soon as a few SHACL violations are found in
the specified KB.  On the command-line, use the `--abort` option to have the validator exit after three
violations.  Specify a number after the `--abort` flag to exit after that number of violations.  The validation
summary will display the number of aborted validations-- but if your file has the exact number of violations as
the threshold, it will still be counted as an aborted validation.

**NOTE**: As of this writing, if you set the threshold too low (less than 3), the validator might erroneously return
that your KB is *valid*.  This appears to be a current bug or limitation in the TopBraid shacl library.
Consequently, the command-line validator will reject thresholds less than 3.

Without the `--abort` option, the entire KB will be validated with full output of all violations.

To fail fast when using the validator programmatically in Java code, use `ValidateAIF.setAbortThreshold()` to set an error
threshold.

### Shallow validation

The `--depth` option performs a "shallow" validation in which each SHACL rule (shape) only considers a subset of its
target nodes.  The size of this subset (i.e., the depth of the validation) can be specified on the command line.
For example, `--depth=100` means that if your file has 30,000 event arguments, then the `aida:EventArgumentShape` will
only be applied to 100 event arguments, significantly speeding up generation of an error report.  Any violations in
these 100 nodes will be included in the error report.  By default (if no depth is specified), only 50 target nodes will
be tested.  The `--depth` option requires enabling the multi-threaded validator via the `-t` option.
Unlike failing fast, shallow validation ends early whether or not it finds any SHACL violations.

To enable shallow validation programmatically in Java code, use `ValidateAIF.setDepth()` and specify a depth.

### Memory considerations

Validation of a large files can require significant system resources, particularly system RAM.  By default, the Java
heap will use up to half of the available RAM on your system.  If you want to set a higher (or lower) maximum, then
set the `JAVA_OPTS` environment variable to, for example, `-Xmx16G` to use up to 16GB of RAM.

Alternatively, you can add `<extraJvmArguments>-Xmx16G</extraJvmArguments>` to your `pom.xml` file in the
`<configuration>` block of the `appassembler-maven-plugin` plugin.


# FAQ

Please see `FAQ.md` for frequently asked questions.

# Contact

AIF was designed by Ryan Gabbard (gabbard@isi.edu) and Pedro Szekely
(pszekeley@isi.edu) of USC ISI.  Gabbard also wrote the initial
implementations of the associated tools.  The tools are now supported
and extended by Next Century. For questions related to AIF, please contact AIF Support (aif-support@nextcentury.com).

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
