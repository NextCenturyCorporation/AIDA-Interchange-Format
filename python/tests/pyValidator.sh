#!/bin/bash

set -x
# if a command returns a non zero return value, exit script
set -e

# get current time
timestamp=`date +%s`

# save location of SeedlingOntology (validateAIF requires absolute path)
pushd ../../src/main/resources/com/ncc/aif/ontologies
ontology_dir=`pwd`/SeedlingOntology
popd

# run Examples.py
# DIR_PATH is the directory where example files are to be written
DIR_PATH="tests_$timestamp" python3 Examples.py

# validate test file directory
../../target/appassembler/bin/validateAIF --ont $ontology_dir -d tests_$timestamp

# if all files are valid, delete the test file directory
rm -r test_$timestamp