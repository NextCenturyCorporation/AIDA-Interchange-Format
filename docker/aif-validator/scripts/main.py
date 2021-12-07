import os
import logging
import subprocess
from pathlib import Path
from subprocess import PIPE, CalledProcessError

class Main:

    # Initialize instance attributes
    def __init__(self, envs):
        self.validation_home = envs['VALIDATION_HOME']
        self.validation_flags = envs['VALIDATION_FLAGS']
        self.validation_log = envs['VALIDATION_LOG']
        self.validate_dir_or_files = envs['VALIDATE_DIR_OR_FILES']
        self.target_to_validate = envs['TARGET_TO_VALIDATE']
        self.use_m18_ont = os.environ.get('USE_M18_ONTOLOGY', False)

    def run(self):
        #ont = '--ldc' if not self.use_m18_ont else '--ont ' + self.validation_home + '/java/src/main/resources/com/ncc/aif/ontologies/LDCOntology'
        ont = "--dwd"

        # If validation flags contains one of TA1, TA2, or TA3, then expand the flags to the optimal set of flags 
        # for that use case.  Only one of these will run since the environment variables were validated 
        # before they get here
        self.validation_flags = self.validation_flags.replace("--TA1", ont + " --nist -o")
        self.validation_flags = self.validation_flags.replace("--TA2", ont + " --nist -o")
        self.validation_flags = self.validation_flags.replace("--TA3", ont + " --nist-ta3 -o")

        logging.info("Validation flags = [%s]", self.validation_flags)
        logging.info("Validation home = [%s]", self.validation_home)
        if self.validation_log == 'stdout':
            logging.info("Validation output will be sent to stdout")
        else:
            logging.info("Validation output will be captured in log file = [%s]", self.validation_log)
        logging.info("Validate directory or files = [%s]", self.validate_dir_or_files)
        logging.info("Target to validate = [%s]", self.target_to_validate)
        self._execute_validation()

    def _execute_validation(self):
        """Executes the AIF Validator as a sub-process for the turtle files
        in target directory or target files

        :returns: Return code that specifies the validation execution result 
        :rtype: int
        """
        try:
            cmd_flags = self.validation_flags
            if self.validate_dir_or_files == 'directory':
                cmd_flags += ' -d='
            else:
                cmd_flags += ' -f='

            cmd_flags += self.target_to_validate
            cmd = self.validation_home + '/java/target/appassembler/bin/validateAIF ' + cmd_flags
            
            logging.info("Executing AIF Validation with flags [%s]", cmd_flags)
            #***********************
            # Requires python 3.7+ *
            #***********************
            if self.validation_log == 'stdout':
                output = subprocess.run(cmd, stdout=None, check=True, shell=True, universal_newlines=True)
            else:
                output = subprocess.run(cmd, stdout=PIPE, check=True, shell=True, universal_newlines=True)

                f = open(self.validation_log, 'w')
                f.write(output.stdout)
                f.close()

            logging.info("All files valid.  Validation succeeded with flags [%s]", cmd_flags)
            return 0

        except CalledProcessError as e:
            if self.validation_log != 'stdout':
                f = open(self.validation_log, 'w')
                f.write(e.output)
                f.close()
            if e.returncode == 1:
                logging.info("Validation completed with flags [%s] but at least one file had validation errors", cmd_flags)
                return 0
            else:
                logging.info("Validation failed with cmd [%s] with error code [%s]", cmd, str(e.returncode))
                return e.returncode

def read_envs():
    """Function will read in all environment variables into a dictionary

    :returns: Dictionary containing all environment variables or defaults
    :rtype: dict
    """
    envs = {}
    envs['VALIDATION_HOME'] = os.environ.get('VALIDATION_HOME', '/opt/aif-validator')
    envs['VALIDATION_FLAGS'] = os.environ.get('VALIDATION_FLAGS')

    # TBD should default be stdout or log to a file?
    envs['VALIDATION_LOG'] = os.environ.get('VALIDATION_LOG', 'stdout')
    #envs['VALIDATION_LOG'] = os.environ.get('VALIDATION_LOG', './validation_log.out')

    # Default is to validate files in current directory
    # TODO file wildcarding doesn't work.
    #    To specify a single file, set VALIDATE_DIR_OR_FILES to 'files' and TARGET_TO_VALIDATE to a TTL file
    #    TO specify multiple files, must set TARGET_TO_VALIDATE to a space separated list of files
    envs['VALIDATE_DIR_OR_FILES'] = os.environ.get('VALIDATE_DIR_OR_FILES', 'directory')
    envs['TARGET_TO_VALIDATE'] = os.environ.get('TARGET_TO_VALIDATE', '.')

    return envs

def validate_envs(envs: dict):
    """Helper function to validate all of the environment variables exist and are valid before
    processing starts.

    :param dict envs: Dictionary of all environment variables
    :returns: True if all environment variables are valid, False otherwise
    :rtype: bool
    """

    if not bool(envs):
        logging.error("No environment variables found")
        return False

    for k, v in envs.items():
        if not is_env_set(k, v):
            return False

    if envs['VALIDATE_DIR_OR_FILES'] != "directory" and envs['VALIDATE_DIR_OR_FILES'] != "files":
        logging.error("dir_or_files is [%s] but must be either \'directory\' or \'files\'", envs['VALIDATE_DIR_OR_FILES'])
        return False
    
    # You can pass zero or a max of one of --TA1, --TA2, or --TA3 parameters
    if envs['VALIDATION_FLAGS'].count("--TA") > 1:
        logging.error("Validation flags [%s] cannot specify more than one TA Task Type", envs['VALIDATION_FLAGS'])
        return False
    
    return True
    

def is_env_set(env, value):
    """Helper function to check if a specific environment variable is not None

    :param str env: The name of the environment variable
    :param value: The value of the environment variable
    :returns: True if environment variable is set, False otherwise
    :rtype: bool
    """
    if not value:
        logging.error("Environment variable [%s] is not set", env)
        return False

    logging.info("Environment variable [%s] is set to [%s]", env, value)
    return True


def main():

    # validate environment variables
    envs = read_envs()
    
    # set logging to log to stdout
    logging.basicConfig(level=os.environ.get('LOGLEVEL', 'INFO'))

    if validate_envs(envs):

        main = Main(envs)
        main.run()

    else:
        raise ValueError("Exception occurred when validating environment variables")

if __name__ == "__main__": main()
