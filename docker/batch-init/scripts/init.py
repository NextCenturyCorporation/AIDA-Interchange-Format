import os
import logging
import boto3
import tarfile
import zipfile
import glob
import logging
import uuid
import shutil
import re
from pathlib import Path
from enum import Enum
from botocore.exceptions import ClientError

class Task(Enum):
	oneA = '1a'
	oneB = '1b'
	two = '2'
	three = '3'

class Initialize:

	# define all the different directory types and corresponding flags
	NIST = { 'validation': '--ldc --nist -o', 'name': 'nist', 'directory': 'NIST', 'description': 'NIST RESTRICTED'  }
	INTER_TA = { 'validation': '--ldc -o', 'name': 'unrestricted', 'directory': 'INTER-TA', 'description': 'UNRESTRICTED'  }
	NIST_TA3 = { 'validation': '--ldc --nist-ta3 -o', 'name': 'nist-ta3', 'directory': 'NIST', 'description': 'NIST TA3 RESTRICTED' }

	# init instance attributes
	def __init__(self, envs):

		self.s3_submission_archive_path = envs['S3_SUBMISSION_ARCHIVE_PATH']
		self.aws_region = envs['AWS_DEFAULT_REGION']
		self.aws_sns_topic = envs['AWS_SNS_TOPIC_ARN']
		self.s3_validation_bucket = envs['S3_VALIDATION_BUCKET']
		self.s3_validation_prefix = envs['S3_VALIDATION_PREFIX']
		self.batch_num_nodes = envs['BATCH_NUM_NODES']
		self.batch_job_definition = envs['BATCH_JOB_DEFINITION']
		self.batch_job_queue = envs['BATCH_JOB_QUEUE']
		self.session = boto3.session.Session(region_name=self.aws_region)


	def run(self):
		""" Main method to run
		"""
		self._check_submission_extension()

		stem = self._get_submission_stem()
		logging.info("File stem for submission %s is %s", self.s3_submission_archive_path, stem)

		# download / extract archive and return local directory
		staging_dir = self._download_and_extract_submission_from_s3()

		# identify the task type for the submission
		task = self._get_task_type(stem, staging_dir)

		# validate structure of submission and upload to S3 
		jobs = self._validate_and_upload(staging_dir, task, stem)

		# print out enviornment variables that will be set during aws batch submission
		if jobs:

			for idx, job in enumerate(jobs):
				logging.info("Submitting AWS Batch job[%s] with the following overrides: %s", job['name'], str(job))
				self._submit_job(job['name'], job)
		else:
			logging.info("No AWS Batch jobs submitted for %s", self.s3_submission_archive_path)

		# remove staing directory and downloaded submission
		os.remove(Path(self.s3_submission_archive_path).name)
		shutil.rmtree(staging_dir)


	def _publish_init_failure(self, err_message):
		"""Function will publish message on to the SNS topic specified by
		the topic arn that reports a failure. This will be used to notify 
		the user that no job was submitted for this submission. 

		:param str message: The message to publish
		"""
		message = "The submission {0} will not be validated because the following error occrured: {1}".format( 
			self.s3_submission_archive_path , err_message)
		self._publish_sns_message(message)


	def _publish_sns_message(self, message):
		"""Function will publish message on to the SNS topic specified by
		the topic arn.

		:param str message: The message to publish
		"""
		sns = self.session.client('sns')

		try:
			logging.info("Publishing message [%s] to topic %s", message, self.aws_sns_topic)

			#publish message 
			response = sns.publish(
				TopicArn=self.aws_sns_topic,
				Message=message
			)
		except ClientError as e:
			logging.error(e)


	def _download_and_extract_submission_from_s3(self):
		"""Downloads submission from s3 and extracts the contents to the working directory.
		Submissions must be an archive of .zip, .tar.gz, or .tgz.

		:raises ClientError: SQS client exception
		:raises Exception: No turtle (TTL) files extracted from s3 submission
		:returns: The directory of the extracted content
		:rtype: str
		"""
		s3_bucket, s3_object, file_name, file_ext = self._get_submission_paths()

		uid = str(uuid.uuid4())

		# create directory for output
		if not os.path.exists(uid):
		    os.makedirs(uid)

		s3_client = self.session.client('s3')

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

		    return uid

		except ClientError as e:
		    logging.error(e)
		    self._publish_init_failure(e)
		    raise
		except ValueError as e:
		    logging.error(e)
		    self._publish_init_failure(e)
		    raise


	def _upload_file_to_s3(self, filepath, bucket, prefix=None):
	    """Helper function to upload single file to S3 bucket with specified prefix

	    :param str filepath: The local path of the file to be uploaded
	    :param str bucket: The S3 bucket to upload file to
	    :param str prefix: The prefix to be added to the file name
	    :raises ClientError: S3 client exception
	    """
	    s3_client = self.session.client('s3')

	    try:
	        if prefix is not None:
	            s3_object = '/'.join([prefix, Path(filepath).name])
	        else:
	            s3_object = Path(filepath).name

	        logging.info("Uploading %s to bucket %s", s3_object, bucket)
	        s3_client.upload_file(str(filepath), bucket, s3_object)

	    except ClientError as e:
	        logging.error(e)


	def _get_submission_paths(self):
	    """Helper function to extract s3 and file path information from s3 submission 
	    path.

	    :returns: 
	        - s3_bucket - the extracted S3 bucket
	        - s3_object - the extracted s3 object
	        - file_name - the extracted file name including extension
	        - file_ext - the extracted file extension
	    :rtype: (str, str, str, str)
	    """
	    path = Path(self.s3_submission_archive_path)
	    s3_bucket = path.parts[0]          
	    s3_object = '/'.join(path.parts[1:])   
	    file_name = path.name
	    suffixes = path.suffixes
	    file_ext = self._get_submission_extension()

	    return s3_bucket, s3_object, file_name, file_ext


	def _get_submission_extension(self):
		"""Helper function to get the extension of the submission.

		:returns: The submission extension
		:rtype: str
		"""
		path = Path(self.s3_submission_archive_path)
		suffixes = path.suffixes

		if len(suffixes) > 1 and suffixes[-1] == '.gz':
		    file_ext = "".join([suffixes[-2], suffixes[-1]])
		elif len(suffixes) > 1:
		    file_ext = suffixes[-1]
		elif len(suffixes) == 1:
		    file_ext = suffixes[0]

		return file_ext


	def _check_submission_extension(self):
	    """Helper function that checks the submission extension is valid before
	    downloading archive from S3. Valid submissions can be archived as .tar.gz, 
	    .tgz, or .zip. 

		:raises ValueError: The submission extension type is invalid
	    """
	    file_ext = self._get_submission_extension()
	    valid_ext = [".tar.gz", ".tgz", ".zip"]

	    try:
	        logging.info("Checking if submission %s is a valid archive type", self.s3_submission_archive_path)
	        if file_ext not in valid_ext:
	            raise ValueError("Submission {0} is not a valid archive type. Submissions must be .tar.gz, .tgz, or .zip".format(self.s3_submission_archive_path))
	    except ValueError as e:
	        logging.error(e)
	        self._publish_init_failure(e)
	        raise


	def _get_submission_stem(self):
		"""Function will return the stem of the submission accounting for the
		extension .tar.gz.

		:param str submission: The path of the submission on s3
		:returns: The stem of the submission
		:rtype: str
		"""
		path = Path(self.s3_submission_archive_path)
		stem = path.stem

		if self._get_submission_extension() == '.tar.gz':
			return Path(stem).stem
		else:
			return stem


	def _get_task_type(self, stem, directory):
		"""Function will determine the task type of the submission based on the naming 
		convention of the stem.

		:param str stem: The stem of the submission
		:param str directory: The local directory containing the downloaded contents of
			the submission
		:returns The task type enum of the submission stems
		:rtype: Enum
		"""
		delim_count = stem.count('.')
		id_dir = self._get_run_id_directory(directory)

		if delim_count == 0:
			if not self._check_nist_directory(id_dir):
				err = "Invalid Task 1 submission format. Unable to locate required {0} directory in submission".format(
					self.NIST['directory'])
				self._publish_init_failure(err)
				raise ValueError(err)

			# check for task 1b
			if self._check_nist_subdirectory_has_ttl(id_dir):
				logging.info("Submission identified as Task 1b")
				return Task.oneB
			elif self._check_nist_directory_has_ttl(id_dir):
				logging.info("Submission identified as Task 1a")
				return Task.oneA
			else:
				err = "Invalid Task 1 submission format. {0} directory and subdirectories in submission do not contain any .ttl files".format(
					self.NIST['directory'])
				self._publish_init_failure(err)
				raise ValueError(err)
			
		elif delim_count == 1:
			if not self._check_nist_directory(id_dir):
				err = "Invalid Task 2 submission format. Unable to locate required {0} directory in submission".format(
					self.NIST['directory'])
				self._publish_init_failure(err)
				raise ValueError(err)

			if not self._check_nist_directory_has_ttl(id_dir):
				err = "Invalid Task 2 submission format. {0} directory in submission does not contain any .ttl files".format(
					self.NIST['directory'])
				self._publish_init_failure(err)
				raise ValueError(err)

			logging.info("Submission identified as Task 2")
			return Task.two
					
		elif delim_count == 2:
			# check for ttl files in root directory
			if not glob.glob(id_dir + '/*.ttl'):
				err = "Invalid Task 3 submission format. {0} directory in submission does not contain any .ttl files".format(
					id_dir)
				self._publish_init_failure(err)
				raise ValueError(err)

			logging.info("Submission identified as Task 3")
			return Task.three

		else:
			err = "Invalid submission format. Could not extract task type with submission stem {0}".format(stem)
			self._publish_init_failure(err)
			raise ValueError() 


	def _get_run_id_directory(self, directory):
		"""Helper function that will find the run ID directory. This should be a single directory in the top level
		of every submission. If it does not exist or if there are multiple directories, an error will be raised.

		:param str directory: The directory to search for the single run ID directory
		:returns: The full path of the run ID directory
		:rtype: str
		"""
		dir_list = [ name for name in os.listdir(directory) if os.path.isdir(os.path.join(directory, name)) ]

		# TODO There is no real validation to ensure the name of this directory is the run ID. This could be an improvement 
		# in the future. 
		if len(dir_list) == 1:
			return directory + '/' + dir_list[0]
		else:
			err = "Submission should be a compressed archive of a single directory named with the run ID"
			self._publish_init_failure(err)
			raise ValueError(err)

    
	def _validate_and_upload(self, directory, task, prefix):
		"""Validates directory structure of task type and uploads the contents to s3. Returns a dictionary
		of jobs that need to be executed on batch with their corresponding s3 locations.

		:param str directory: The local directory containing the downloaded contents of
			the submission
		:param Task task: The task enum that representing the task type of the submission
		:param str prefix: The prefix to append to all objects uploaded to the S3 bucket
		:returns: List of dictionary objects representing the aws batch jobs that need to be executed
		:rtype: List
		"""
		logging.info("Validating submission as Task type %s", task.value)
		task_type = task.value
		id_dir = self._get_run_id_directory(directory)
		jobs = []

		if task == Task.oneA or task == Task.two:

			# NIST direcotry required, do not upload INTER-TA if NIST does not exist
			if not self._check_nist_directory(id_dir):
				logging.error("Task {0} submission format is invalid. Could not locate NIST directory".format(str(task.value)))

			else:
				j = self._upload_formatted_submission(id_dir, prefix, self.NIST, task)

				if j is not None:
					jobs.append(j)

				# INTER-TA directory **not required**
				if self._check_inter_ta_directory(id_dir):
					j = self._upload_formatted_submission(id_dir, prefix, self.INTER_TA, task)

					if j is not None:
						jobs.append(j)

			return jobs
		elif task == Task.oneB:
			logging.error("Task 1b submissions are currently not supported")

		elif task == Task.three:
			jobs.append(self._upload_formatted_submission(id_dir, prefix, self.NIST_TA3, task))

			return jobs
		else:
			logging.error("Could not validate submission structure for invalid task %s", task)


	def _upload_formatted_submission(self, directory, prefix, validation_type, task):
		"""Function will locate all .ttl files within a submission subdirectory based on the validation 
		type that was found in the get_task_type function and upload them to s3. Once all files have been 
		uploaded, a dictionary object with information to pass into the aws batch job will be returned. 

		:param str directory: The local directory containing the downloaded contents of
			the submission
		:param str prefix: The prefix to append to all objects uploaded to the S3 bucket
		:param validation_type: The validation type that these files will be validated against
		:param Task task: The task enum that representing the task type of the submission
		:param returns: The dictionary representation of the job, None if error occurred
		:param rtype: dict
		"""
		job = {}
		bucket_prefix = self.s3_validation_prefix + '/' + prefix + '-' + validation_type['name']
		logging.info("Task %s submission %s directory exists. Uploading .ttl files to %s", str(task.value),
			validation_type['directory'], self.s3_validation_bucket + '/' + bucket_prefix)

		# inspect the current directory for .ttl files
		ttl_paths = (glob.glob(directory + '/' + validation_type ['directory'] + '/*.ttl'))
		ttls = [ Path(x).name for x in ttl_paths ]

		if not self._check_for_duplicates(ttls):

			if len(ttls) == 0:
				logging.error("No .ttl files found in Task %s submission %s directory", str(task.value), validation_type['directory'])
			else:
				for path in ttl_paths:
					self._upload_file_to_s3(path, self.s3_validation_bucket, bucket_prefix)

				# create the batch job information
				job['worker'] = {
					'environment': [
						{
							'name': 'VALIDATION_FLAGS',
							'value': validation_type['validation']
						},
						{
							'name': 'S3_VALIDATION_BUCKET',
							'value': self.s3_validation_bucket
						},
						{
							'name': 'S3_VALIDATION_PREFIX',
							'value': self.s3_validation_prefix
						}
					]
				}
				job['main'] = {
					'environment': [
						{
							'name': 'S3_VALIDATION_BUCKET',
							'value': self.s3_validation_bucket
						},
						{
							'name': 'S3_VALIDATION_PREFIX',
							'value': self.s3_validation_prefix
						}, 
						{
							'name': 'S3_SUBMISSION_BUCKET',
							'value': self.s3_validation_bucket
						},
						{
							'name': 'S3_SUBMISSION_PREFIX',
							'value': bucket_prefix
						},
						{
							'name': 'S3_SUBMISSION_VALIDATION_DESCR',
							'value': validation_type['description']
						},
						{
							'name': 'S3_SUBMISSION_EXTRACTED',
							'value': str(len(ttls))
						},
						{
							'name': 'S3_SUBMISSION_ARCHIVE',
							'value': self.s3_submission_archive_path
						},
						{
							'name': 'S3_SUBMISSION_TASK',
							'value': task.value
						},
						{
							'name': 'AWS_SNS_TOPIC_ARN',
							'value': self.aws_sns_topic
						}
					]	
				}
				job['name'] = Path(bucket_prefix).stem.replace('.', '')
				
				return job

		return None

	def _check_for_duplicates(self, ttls):
		"""Function will check for duplicates in a list of file names.

		:param List ttls: A list of ttl file names.
		:returns: True if duplicates were found, False otherwise
		:rtype: bool
		"""
		if len(ttls) != len(set(ttls)):
			logging.error("Duplicate files with .ttl extension found in submission")
			return True
		return False
		

	def _check_nist_directory(self, directory):
		"""Helper function that will determine if NIST directory exists as an 
		immediate subdirectory of the passed in directory.

		:param str directory: The directory to validate against
		:returns: True if directory exists, False otherwise
		:rtype: bool
		"""
		return os.path.exists(directory + "/" + self.NIST['directory'])


	def _check_nist_directory_has_ttl(self, directory):
		"""Helper function that will determine if there are any ttl files in the NIST
		directory.

		:param str directory: The run directory to validate against
		:returns: True if NIST directory has .ttl files, False otherwise
		:rtype: bool 
		"""

		if not glob.glob(directory + '/' + self.NIST['directory'] + '/*.ttl'):
			return False
		return True


	def _check_nist_subdirectory_has_ttl(self, directory):
		"""Helper function that will determine if there are any ttls files in any subdirectories
		under the NIST directory.
		"""
		if not glob.glob(directory + '/' + self.NIST['directory'] + '/**/*.ttl'):
			return False
		return True


	def _check_inter_ta_directory(self, directory):
		"""Helper function that will determine if INTER-TA directory exists as
		an immediate subdirectory of the passed in directory.

		:param str directory: The directory to validate against
		:returns: True if directory exists, False otherwise
		:rtype: bool
		"""
		return os.path.exists(directory + "/" + self.INTER_TA['directory'])


	def _submit_job(self, job_name, overrides):
		"""Function will submit a job to aws batch with the passed in environment
		variable overrides.

		:param str job_name: The job name
		:param str job_definition: The job definition that was configured on AWS batch
		:param list overrides: List of dictionary environment variable 
			overrides for the main and worker nodes specific AWS batch job that is being submitted
		"""
		batch = self.session.client('batch')

		try:
			response = batch.submit_job(
				jobName=job_name, #'jdoe-test-job', # use your HutchNet ID instead of 'jdoe'
	            jobQueue=self.batch_job_queue, 
	            jobDefinition=self.batch_job_definition,
	            nodeOverrides={
	            	'nodePropertyOverrides': [
	            		{
	            			'targetNodes': '0:0',
	            			'containerOverrides': overrides['main']
	            			
	            		},
	            		{
	            			'targetNodes': '1:{0}'.format(int(self.batch_num_nodes)-1),
	            			'containerOverrides': overrides['worker']
	            			
	            		}
	            	]
	        	}
	        )

			if response is not None:
				logging.info("Job %s successfully submitted to AWS batch with job id: %s", job_name, response['jobId'])
			else:
				logging.error("There was an error when submitting the batch job %s with definition %s to queue %s with " \
					" environment overrides %s", job_name, self.batch_job_queue, self.batch_job_definition, env_overrides)

		except ClientError as e:
			logging.error(e)


def is_env_set(env, value):
    """Helper function to check if a specific environment variable is not None

    :param str env: The name of the environment variable
    :param value: The value of the environment variable
    :returns: True if environment variable is set, False otherwise
    :rtype: bool
    """
    if not value:
        logging.error("Environment variable %s is not set", env)
        return False

    logging.info("Environment variable %s is set to %s", env, value)
    return True


def validate_envs(envs):
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

    try:
        int(envs['BATCH_NUM_NODES'])
    except ValueError:
        logging.error("BATCH_NUM_NODES value [%s] must be an integer", envs['BATCH_NUM_NODES'])
        return False

    return True


def read_envs():
	"""Function will read in all environment variables into a dictionary

	:returns: Dictionary containing all environment variables or defaults
	:rtype: dict
	"""
	envs = {}
	envs['S3_SUBMISSION_ARCHIVE_PATH'] = os.environ.get('S3_SUBMISSION_ARCHIVE_PATH')
	envs['S3_VALIDATION_BUCKET'] = os.environ.get('S3_VALIDATION_BUCKET')
	envs['S3_VALIDATION_PREFIX'] = os.environ.get('S3_VALIDATION_PREFIX')
	envs['BATCH_NUM_NODES'] = os.environ.get('BATCH_NUM_NODES')
	envs['BATCH_JOB_DEFINITION'] = os.environ.get('BATCH_JOB_DEFINITION')
	envs['BATCH_JOB_QUEUE'] = os.environ.get('BATCH_JOB_QUEUE')
	envs['AWS_SNS_TOPIC_ARN'] = os.environ.get('AWS_SNS_TOPIC_ARN')
	envs['AWS_DEFAULT_REGION'] = os.environ.get('AWS_DEFAULT_REGION')

	return envs


def main():

	# validate environment variables
	envs = read_envs()
	logging.basicConfig(level=os.environ.get('LOGLEVEL', 'INFO'))

	if validate_envs(envs):

		init = Initialize(envs)
		init.run()

	else:
		raise ValueError("Exception occurred when validating environment variables") 
		


if __name__ == "__main__": main()

