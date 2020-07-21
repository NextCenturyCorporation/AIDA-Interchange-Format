#!/bin/bash

# if a command returns a non zero return value, exit script
set -e

# get current time
test_directory=test_`date +%s`
mkdir $test_directory

# save location of LDCOntology (validateAIF requires absolute path)
pushd ../../java/src/main/resources/com/ncc/aif/ontologies
ontology=`pwd`/LDCOntology
popd

# run examples.py
# DIR_PATH is the directory where example files are to be written
DIR_PATH=$test_directory python3 examples.py

# validate test file directory
# if return value is non zero, this script will exit
 ../../java/target/appassembler/bin/validateAIF --ont $ontology -o -d $test_directory

# if all files are valid, delete the test file directory
rm -r $test_directory
