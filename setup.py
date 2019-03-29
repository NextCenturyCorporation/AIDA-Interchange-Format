#!/usr/bin/env python

from distutils.core import setup

import setuptools

with open("README.md", "r") as fh:
  long_description = fh.read()

setup(name='aida-interchange',
      version='0.1.0',
      author='Ryan Gabbard',
      author_email='gabbard@isi.edu',
      description='AIDA Interchange Format tools',
      long_description=long_description,
      long_description_content_type='text/markdown',
      url='https://github.com/NextCenturyCorporation/AIDA-Interchange-Format.git',
      packages=setuptools.find_packages(where='./python/', exclude=['tests']),
      package_dir={
          '' : 'python'
          },
      python_requires='~=3.0',
      install_requires=[
        'rdflib',
        'attrs>=17.4.0',
        'typing_extensions',
      ],
      classifiers=(
        "Programming Language :: Python :: 3",
        "License :: OSI Approved :: MIT License",
        "Operating System :: OS Independent",
      ),
      )
