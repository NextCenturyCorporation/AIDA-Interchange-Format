#!/usr/bin/env python

from distutils.core import setup

setup(name='gaia-interchange',
      version='0.1.0',
      description='AIDA Interchange Format tools',
      packages=['gaia_interchange'],
      python_requires='~=3.0',
      install_requires=[
        'rdflib',
        'attrs>=17.4.0',
        'typing_extensions',
      ],
      )
