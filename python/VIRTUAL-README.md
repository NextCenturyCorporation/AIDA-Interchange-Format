# Python Virtual Environment

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

When you are starting development of a python project, you first need to create a virtual environment.  The name of the virtual environment in the example below, `aida-interchange-format`, assumes you are making changes or testing the AIF library.  Feel free to use a name specific to your application if you are just using the AIF library.

Navigate inside the `AIDA-Interchange-Format/python` directory and run the following

```bash
$ virtualenv -p python3 ~/.virtualenvs/aida-interchange-format
$ source ~/.virtualenvs/aida-interchange-format/bin/activate
$ pip install -r requirements.txt
```

Your virtual environment will be activated after running these commands.  The following sections describe deactivating and re-activating the virtual environment.

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
