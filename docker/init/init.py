import os
import logging
import boto3
import tarfile
import zipfile
import glob
import logging
import uuid
import shutil
from pathlib import Path
from enum import Enum
from botocore.exceptions import ClientError

class Task(Enum):
	one = 1
	two = 2
	three = 3

def download_and_extract_submission_from_s3(session, submission):
	"""Downloads submission from s3 and extracts contents to the working directory.
	Submissions must be an archive of .zip, .tar.gz, or .tgz.

	:param Session session: The boto3 session 
	:param str submission: The path of the submission on s3
	:raises ClientError: SQS client exception
	:raises Exception: No turtle (TTL) files extracted from s3 submission
	:returns: The directory of the extracted content
	:rtype: str
	"""
	s3_bucket, s3_object, file_name, file_ext = get_submission_paths(submission)

	uid = str(uuid.uuid4())

	# create directory for output
	if not os.path.exists(uid):
	    os.makedirs(uid)

	s3_client = session.client('s3')

	try:
	    logging.info("Downloading %s from bucket %s", s3_object, s3_bucket)
	    s3_client.download_file(s3_bucket, s3_object, file_name)
	    logging.info("Extracting %s", file_name)

	    # extract files
	    if file_ext == '.tgz' or file_ext == '.tar.gz':
	        # extract the contents of the .tar.gz
	        with tarfile.open(file_name) as tar:
	            tar.extractall(uid)
	    elif(file_ext == '.zip'):
	        zip_ref = zipfile.ZipFile(file_name, 'r')
	        zip_ref.extractall(uid)
	        zip_ref.close()

	    ttls_paths = (glob.glob(uid + '/**/*.ttl', recursive=True))
	    ttls = [ Path(x).name for x in ttls_paths ]

	    # if no ttl files extracted raise an exception
	    if len(ttls) <= 0 :
	        err = "No files with .ttl extension found in S3 submission {0}".format(file_name)
	        raise ValueError(err)

	    # check for duplicates
	    if len(ttls) != len(set(ttls)):
	        err = "Duplicate files with .ttl extension found in S3 submission {0}".format(file_name)
	        raise ValueError(err)

	    return uid

	except ClientError as e:
	    logging.error(e)
	    raise
	except ValueError as e:
	    logging.error(e)
	    raise


def get_submission_paths(submission):
    """Helper function to extract s3 and file path information from s3 submission 
    path.

    :returns: 
        - s3_bucket - the extracted S3 bucket
        - s3_object - the extracted s3 object
        - file_name - the extracted file name including extension
        - file_ext - the extracted file extension
    :rtype: (str, str, str, str)
    """
    path = Path(submission)
    s3_bucket = path.parts[0]          
    s3_object = '/'.join(path.parts[1:])   
    file_name = path.name
    suffixes = path.suffixes
    file_ext = get_submission_extension(submission)

    return s3_bucket, s3_object, file_name, file_ext


def get_submission_extension(submission):
	"""Helper function to get the extension of the submission

	:param str submission: The full path of the submission on s3
	:returns: The submission extension
	:rtype: str
	"""
	path = Path(submission)
	suffixes = path.suffixes

	if len(suffixes) > 1 and suffixes[-1] == '.gz':
	    file_ext = "".join([suffixes[-2], suffixes[-1]])
	elif len(suffixes) > 1:
	    file_ext = suffixes[-1]
	elif len(suffixes) == 1:
	    file_ext = suffixes[0]

	return file_ext


def check_submission_extension(submission):
    """Helper function that checks the submission extension is valid before
    downloading archive from S3. Valid submissions can be archived as .tar.gz, 
    .tgz, or .zip. 

	:param str submission: The path of the submission
	:raises ValueError: The submission extension type is invalid
    """
    file_ext = get_submission_extension(submission)
    valid_ext = [".tar.gz", ".tgz", ".zip"]

    try:
        logging.info("Checking if submission %s is a valid archive type", submission)
        if file_ext not in valid_ext:
            raise ValueError("Submission {0} is not a valid archive type".format(submission))
    except ValueError as e:
        logging.error(e)
        raise


def get_submission_stem(submission):
	"""Function will return the stem of the submission accounting for the
	extension .tar.gz.

	:param str submission: The path of the submission on s3
	:returns: The stem of the submission
	:rtype: str
	"""
	path = Path(submission)
	stem = path.stem

	if get_submission_extension(submission) == '.tar.gz':
		return Path(stem).stem
	else:
		return stem


def get_task_type(stem):
	"""Function will determine the task type of the submission based on the naming 
	convention of the stem.

	:param str stem: The stem of the submission
	:returns The task type enum of the submission stems
	:rtype: Enum
	"""
	delim_count = stem.count('.')

	if delim_count == 0:
		return Task.one
	elif delim_count == 1:
		return Task.two
	elif delim_count == 2:
		return Task.three
	else:
		raise ValueError("Invalid submission format. Could not extract task type with submission stem %s", stem) 


def validate_and_upload(session, directory, task):
	"""Validates directory structure of task type and then uploads contents to s3

	:param Session session: The boto3 session
	:param str directory: The local directory containing the donwloaded contents of
		the submission
	:param Task task: The task enum that representing the task type of the submission
	:raises ValueError: If the 
	"""
	if task == Task.one:

		if not check_nist_directory(directory):
			logging.error("Task 1 submission format is invalid. Could not locate NIST directory")

	elif task == Task.two:

	elif task == Task.three:

	else:
		logging.error("Could not validate submission structure for invalid task %s", task)


def check_nist_directory(directory):
	"""Helper function that will determine if NIST directory exists as an 
	immediate subdirectory of the passed in directory.

	:param str directory: The directory to validate against
	"""
	return os.path.exists(directory + "/NIST")


def check_inter_ta_directory(directory):
	"""Helper function that will determine if INTER-TA directory exists as
	an immediate subdirectory of the passed in directory.

	:param str directory: The directory to validate against
	"""
	return os.path.exists(directory + "/INTER-TA")

def main():

	# variables 
	aws_region = 'us-east-1'
	aws_bucket = 'aida-validation'
	nist = 'NIST'
	inter_ta = 'INTER-TA'

	# set logging to info
	logging.basicConfig(level=os.environ.get('LOGLEVEL', 'INFO'))

	submission = 'aida-validation-cu-ramfis/GAIA_1.Colorado_1.zip'

	session = boto3.session.Session(region_name=aws_region)

	check_submission_extension(submission)
	stem = get_submission_stem(submission)
	logging.info("File stem for submission %s is %s", submission, stem)

	task = get_task_type(stem)
	staging_dir = download_and_extract_submission_from_s3(session, submission)

	validate_submission_structure(stating_dir, task)


	# remove staing directory and downloaded submission
	os.remove(Path(submission).name)
	shutil.rmtree(staging_dir)




if __name__ == "__main__": main()
