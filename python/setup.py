from setuptools import setup, find_packages

setup(name='aida-interchange',
      version='1.0.5',
      author='Ryan Gabbard',
      author_email='gabbard@isi.edu',
      description='AIDA Interchange Format tools',
      long_description=open('README.md').read(),
      long_description_content_type='text/markdown',
      url='https://github.com/NextCenturyCorporation/AIDA-Interchange-Format.git',
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
