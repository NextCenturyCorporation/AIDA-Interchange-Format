#!/bin/bash

# if a command returns a non zero return value, exit script
set -e

# get current time
test_directory=test_`date +%s`
mkdir $test_directory

# run examples.py
# DIR_PATH is the directory where example files are to be written
DIR_PATH=$test_directory python3 examples.py

# validate test file directory
# if any files are invalid, the script will end (see above)
docker run --rm -it \
       --user $(id -u):$(id -g) \
       -v $(pwd)/$test_directory:/v \
       --entrypoint /opt/aif-validator/java/target/appassembler/bin/validateAIF \
       nextcenturycorp/aif_validator:latest \
       -o --ont /opt/aif-validator/java/src/main/resources/com/ncc/aif/ontologies/LDCOntology -d /v

# if all files are valid, delete the test file directory
rm -r $test_directory

# re-create test directory
mkdir $test_directory

# run dwd examples.py
DIR_PATH=$test_directory python3 dwd_examples.py

# validate test file directory
# if any files are invalid, the script will end (see above)
docker run --rm -it \
       --user $(id -u):$(id -g) \
       -v $(pwd)/$test_directory:/v \
       --entrypoint /opt/aif-validator/java/target/appassembler/bin/validateAIF \
       nextcenturycorp/aif_validator:latest \
       -o --dwd -d /v

# if all files are valid, delete the test file directory
rm -r $test_directory
