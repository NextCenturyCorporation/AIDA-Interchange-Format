# AIF Examples and Test directory

This directory contains Java files that show examples of using AIF.  These examples are written as unit tests that test various validation rules.
There are times when you may want to see the models and validation reports created by these examples/tests.

## Test Groups

There are 3 main **test groups**.

| File | Description |
| ---- | ----------- |
| `ExamplesAndValidationTest.java` | The tests in this file create various models that are expected to be valid or invalid against base AIF |
| `NistExamplesAndValidationTest.java` | The tests in this file create various models that are expected to be valid or invalid against NIST restricted AIF for TA1 and TA2 algorithms |
| `NistTA3ExamplesAndValidationTest.java` | The tests in this file create various models that are expected to be valid or invalid against NIST restricted AIF for TA3 algorithms |

## Running the tests

The example unit tests are run when executing the mvn `test` target at the root directory of AIF.
Note, the mvn `install` target also runs the `test` target.

## Produce output models and reports

By default, these examples do not output their models or validation reports (flags are set to false).
You can modify these source files slightly to control which examples output their models/reports, and if so, how they output them.
Each of the **test groups** can be separately controlled.  For example, you can output the `NistExamplesAndValidationTest.java` group models while keeping the other test groups' output suppressed.

### DUMP_ALWAYS flag

- **false** (default)
    - Model is dumped if the result is unexpected.
    - Validation report is dumped if the result is invalid and unexpected.
- **true**
    - Model is always dumped.
    - Validation report is dumped if the result is invalid

### DUMP_TO_FILE flag

- **false** (default) - If model (and validation report) is being dumped, it will go to `stdout`
- **true** - If model (and validation report) is being dumped, it is dumped into the `target/test-dump-output` directory.  The files are named after the class and unit test method name.  For example, the `entityMissingType()` test in the `InvalidExamples` inner class of the `ExamplesAndValidationTest` class is dumped to:
    - model - `target/test-dump-output/ExamplesAndValidationTest_InvalidExamples_entityMissingType.ttl`
    - report - `target/test-dump-output/ExamplesAndValidationTest_InvalidExamples_entityMissingType-report.txt`
