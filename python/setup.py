from setuptools import setup, find_packages

setup(name='aida-interchange',
      version='1.2.1',
      author='Next Century Corporation',
      author_email='aif-support@nextcentury.com',
      description='AIDA Interchange Format',
      long_description=open('README.md').read(),
      long_description_content_type='text/markdown',
      url='https://github.com/NextCenturyCorporation/AIDA-Interchange-Format',
      packages=find_packages(exclude=['tests']),
      python_requires='~=3.0',
      install_requires=[
        'rdflib',
        'sphinx>=2.0.1',
        'attrs>=17.4.0',
        'typing_extensions',
      ],
      classifiers=(
        "Programming Language :: Python :: 3",
        "License :: OSI Approved :: MIT License",
        "Operating System :: OS Independent",
      ),
)
