# AIF Python API

To use the `aida_interchange` package within your project, you must first install it. It is recommended that you install `aida_interchange` into a python3 virtual environment. See [Python Virtual Environment](#virtualenv) for more details on creating and using a virtual environment.

## Install

To install `aida_interchange`, make sure a [Python Virtual Environment](#virtualenv) is activated and run the following command:

```bash
$ pip install aida-interchange
```
The `aida_interchange` modules can now be imported into your project.

## API Documentation

The python project uses [Sphinx](http://www.sphinx-doc.org/en/master/) for generating documentation. To generate the documentation, make sure a [Python Virtual Environment](#virtualenv) is activated, navigate to the `AIDA-Interchange-Format/python/docs` directory, and run the `update_documentation.sh` script.

```bash
$ cd docs
$ ./update_documentation.sh
```
This script will generate documentation in the form of HTML and place it within the `AIDA-Interchange-Format/python/docs/build/html` folder.

<h1 id="virtualenv">Python Virtual Environment</h1>

It is recommended that Python development be done in an isolated environment called a virtual environment.  There are multiple ways to set up and use Python virtual environments.  This README describes one of those ways.

The basic steps are:
1. Install virtualenv (done once)
2. Create a virtual environment (done once per development effort)
3. Repeat as needed:
    1. Activate a virtual environment
    2. Install libraries and develop code
    3. Deactivate a virtual environment

Follow the instructions below to set up your virtual environment. It is important to note that you should never install any project specific python dependencies outside of your virtual environment. Also, ensure that your virtual environment has been activated before running python scripts within this repository.

## Install virtualenv

If you haven't installed `virtualenv` yet, follow these steps. This only needs to be once.

```bash
$ cd ~
$ mkdir .virtualenvs
$ pip install virtualenv
```
Verify virtualenv is installed
```bash
$ which virtualenv
```

## Create virtual environment

When you are starting development of a python project, you first need to create a virtual environment. The name of the virtual environment in the example below, `aida-interchange-format`, assumes you are making changes or testing the AIF library. Feel free to use a name specific to your application if you are just using the AIF library.

To create the virtual environment and install the latest AIF, run the following:

```bash
$ virtualenv -p python3 ~/.virtualenvs/aida-interchange-format
$ source ~/.virtualenvs/aida-interchange-format/bin/activate
$ pip install aida-interchange
```

Your virtual environment is now activated. The following sections describe deactivating and re-activating the virtual environment.

## Deactivate virtual environment

To deactivate your current virtual environment, run the following command.

```bash
$ deactivate
```

## Activate virtual environment

To re-activate your virtual environment, run the following command.

```bash
$ source ~/.virtualenvs/aida-interchange-format/bin/activate
```
