#!/bin/bash

# if a command returns a non zero return value, exit script
set -e

# get current time
test_directory=test_`date +%s`
mkdir $test_directory

# save location of SeedlingOntology (validateAIF requires absolute path)
pushd ../../../src/main/resources/com/ncc/aif/ontologies
ontology_dir=`pwd`/SeedlingOntology
popd

# run examples.py
# DIR_PATH is the directory where example files are to be written
DIR_PATH=$test_directory python3 examples.py

# validate test file directory
# if return value is non zero, this script will exit
../../target/appassembler/bin/validateAIF --ont $ontology_dir -d $test_directory

# if all files are valid, delete the test file directory
rm -r $test_directory
