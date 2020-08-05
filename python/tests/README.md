# Python Examples Validator

py_validator.sh does the following:
1. Create a directory into which examples.py will write the generated example turtle files
1. Execute examples.py
1. If all files are valid, delete the previously created directory; o/w the directory will remain.

Prerequisites:
* The python version of AIF must be installed. It is recommended that this is installed in a virtual environment:

      pip install -e ..
* The java version of AIF must be installed to your local maven repo. See the [Java README](../../java/README.md)

To execute the script, run: `./py_validator.sh`

