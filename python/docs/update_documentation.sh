#!/bin/bash

rm source/*.rst
# add private-members for private methods
export SPHINX_APIDOC_OPTIONS=members,undoc-members,show-inheritance
sphinx-apidoc -o source .. ../*setup*
cp source/modules.rst source/index.rst
sphinx-build -b html source build/html
