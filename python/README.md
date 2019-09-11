# Installation

To use the `aida_interchange` package within your project you must first install it using the instructions described in the [Install setup.py](install-setup.py) section. It is recommended that you install `aida_interchange` into a virtual environment for development purposes. To install `aida_interchange`  within your virtual environment first activate your virtual environment and run the install command below. See [Python Development Setup](python-development-setup) for more details on creating a virtual environment.

# Install setup.py

To install `aida_interchange`, run the following command within the `python` directory:

```bash
python3 setup.py install
```
The `aida_interchange` modules can now be imported into your project.

# Python Development Setup

Setting up a virtual environment for Python development is the recommended best way to mitigate problems between multiple systems working on this project.

## Install virtualenv

```bash
$ cd ~
$ mkdir .virtualenvs
$ sudo pip install virtualenv
```
Verify virtualenv is installed

```bash
$ which virtualenv
```

## Create virtual environment

Navigate inside the `python` directory and run the following

```bash
$ virtualenv -p python3 ~/.virtualenvs/aida-interchange-format
$ source ~/.virtualenvs/aida-interchange-format/bin/activate
$ pip install -r requirements.txt
```	

## Activate virtual environment

Your virtual environment will be automatically activated after running the virtualenv command. If you need to re-activate your virtual environment you can run the following command while in the root python folder of the project

```bash
$ source ~/.virtualenvs/aida-interchange-format/bin/activate
```

## Deactivate virtual environment

To deactivate your current virtual environment run the following command.

```bash
$ deactivate
```

# Documentation

The python project uses [Sphinx](http://www.sphinx-doc.org/en/master/) for generating documentation. To generate the documentation, navigate to the `python/docs` directory and run the `update_documentation.sh` script.

```bash
$ ./update_documentation.sh
```
This script will generate documentation in the form of HTML and place it within the `python/docs/build/html` folder.
