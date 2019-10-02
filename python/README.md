# AIF Python API

To use the `aida_interchange` package within your project, you must first install it. It is recommended that you install `aida_interchange` into a python3 virtual environment. See [Python Virtual Environment README](VIRTUAL-README.md) for more details on creating and using a virtual environment.

## Install setup.py

To install `aida_interchange`, make sure a [Python Virtual Environment](VIRTUAL-README.md) is activated, navigate to the `AIDA-Interchange-Format/python` directory, and run the following command:

```bash
$ python3 setup.py install
```
The `aida_interchange` modules can now be imported into your project.

## API Documentation

The python project uses [Sphinx](http://www.sphinx-doc.org/en/master/) for generating documentation. To generate the documentation, make sure a [Python Virtual Environment](VIRTUAL-README.md) is activated, navigate to the `AIDA-Interchange-Format/python/docs` directory, and run the `update_documentation.sh` script.

```bash
$ cd docs
$ ./update_documentation.sh
```
This script will generate documentation in the form of HTML and place it within the `AIDA-Interchange-Format/python/docs/build/html` folder.
