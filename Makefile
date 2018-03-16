# TODO: figure out better module/setup structure and how to test
# inside/outside a package

default:
	@echo "an explicit target is required"

# easier to test python2 vs. python3
PYTHON=python3

# Set NOSE_ARGS to affect all nose commands
# Set NOSE_COVERAGE_ARGS to affect all nose commands with coverage
NOSE:=nosetests --exe $(NOSE_ARGS)
NOSE_COVERAGE_ARGS:=--with-coverage --cover-erase $(NOSE_COVERAGE_ARGS)
NOSE_CORE:=$(NOSE) --exclude-dir=integration_tests 
NOSE_CORE_COVERAGE:=$(NOSE_CORE) $(NOSE_COVERAGE_ARGS)
NOSE_ALL_COVERAGE:=$(NOSE) $(NOSE_COVERAGE_ARGS) --cover-inclusive

PYLINT:=pylint flexnlp_sandbox 

MYPY:=mypy $(MYPY_ARGS) --ignore-missing-imports gaia_interchange
# Suppressed warnings:
# Too many arguments, Unexpected keyword arguments: can't do static analysis on attrs __init__
# Signature of "__getitem__": https://github.com/python/mypy/issues/4108
# Module has no attribute *: mypy doesn't understand __init__.py imports
# mypy/typeshed/stdlib/3/builtins.pyi:39: This is evidence given for false positives on
#   attrs __init__ methods. (This line is for object.__init__.)
# X has no attribute "validator" - thrown for mypy validator decorators, which are dynamically generated
# X has no attribute "default" - thrown for mypy default decorators, which are dynamically generated
# SelfType" has no attribute - mypy seems not to be able to figure out the methods of self for SelfType
FILTERED_MYPY:=$(MYPY) | perl -ne 'print if !/(Too many arguments|Signature of "__getitem__"|Only concrete class|Unexpected keyword argument|mypy\/typeshed\/stdlib\/3\/builtins.pyi:39: note: "\w+" defined here|Module( '\''\w+'\'')? has no attribute|has no attribute "validator"|has no attribute "default"|SelfType" has no attribute)/'

# this is the standard ignore list plus ignores for hanging indents, pending figuring out how to auto-format them
FLAKE8:=flake8
FLAKE8_CMD:=$(FLAKE8) gaia_interchange tests

test: #test-cmd
	$(NOSE_CORE)

test-all: test test-integration

coverage:
	$(NOSE_CORE_COVERAGE)

coverage-all:
	$(NOSE_ALL_COVERAGE)

lint:
	$(PYLINT)

mypy:
	$(FILTERED_MYPY)

flake8:
	$(FLAKE8_CMD)

precommit: flake8 mypy lint
