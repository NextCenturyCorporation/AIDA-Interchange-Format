# Installation

To use the `aida_interchange` package within your project you must first install it using the instructions described in the [Install setup.py](#install-setuppy) section. It is recommended that you install `aida_interchange` into a python3 virtual environment. To install `aida_interchange` within your virtual environment first activate your virtual environment and run the install command below. See [Python Virtual Environment README](VIRTUAL-README.md) for more details on creating and using a virtual environment.

## Install setup.py

To install `aida_interchange`, run the following command within the `AIDA-Interchange-Format\python` directory:

```bash
python3 setup.py install
```
The `aida_interchange` modules can now be imported into your project.

## API Documentation

The python project uses [Sphinx](http://www.sphinx-doc.org/en/master/) for generating documentation. To generate the documentation, navigate to the `AIDA-Interchange-Format/python/docs` directory and run the `update_documentation.sh` script.  You should run this script inside a [Python Virtual Environment](VIRTUAL-README.md).

```bash
$ ./update_documentation.sh
```
This script will generate documentation in the form of HTML and place it within the `AIDA-Interchange-Format/python/docs/build/html` folder.
