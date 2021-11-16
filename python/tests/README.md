# Python Examples Validator

py_validator.sh does the following:
1. Create a directory into which examples.py will write the generated example turtle files
1. Populate the directory with .ttl files via examples.py
1. Validate all .ttl files in the directory
1. If all files are valid, delete the previously created directory; o/w the directory will remain.

Prerequisites:
* The python version of AIF must be installed. It is recommended that this is installed in a virtual environment:

      pip install aida-interchange
* Docker must be installed to access the executable docker image in dockerhub.

To execute the script, run: `./py_validator.sh`
