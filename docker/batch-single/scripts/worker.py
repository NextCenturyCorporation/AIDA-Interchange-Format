import os
import logging
import boto3
import json
import glob
import time
import subprocess
import shutil
from pathlib import Path
from subprocess import PIPE, TimeoutExpired, CalledProcessError
from botocore.exceptions import ClientError


class Worker:

	# Initializer / Instance Attributes
	def __init__(self, envs):

		self.queue_init_timeout = int(envs['QUEUE_INIT_TIMEOUT'])
		self.validation_timeout = int(envs['VALIDATION_TIMEOUT'])
		self.validation_home = envs['VALIDATION_HOME']
		self.validation_flags = envs['VALIDATION_FLAGS']
		self.s3_validation_bucket = envs['S3_VALIDATION_BUCKET']
		self.s3_validation_prefix = envs['S3_VALIDATION_PREFIX'] 
		self.job_id = (envs['AWS_BATCH_JOB_ID']).split("#")[0]
		self.node_index = envs['AWS_BATCH_JOB_NODE_INDEX']
		self.aws_region = envs['AWS_DEFAULT_REGION']
		self.source_log = 'sourcelog'
		self.session = boto3.session.Session(region_name=self.aws_region)


	def run(self):
		"""
		"""
		# verify bucket and submission extension
		self._bucket_exists(self.s3_validation_bucket)

		if self._wait_for_sqs_queue():

				# process messages
				self._process_sqs_queue()
		else:
			logging.error("Worker with node index %s timed out waiting for SQS queue to be available after %s seconds", 
				self.node_index, self.queue_init_timeout)

	
	def _bucket_exists(self, s3_bucket):
		"""Helper function that will check if a validation bucket
		exists.

		:param str s3_bucket: The bucket to check 
		:returns: True if bucket exists, False otherwise
		:raises ClientError: S3 resource exception
		:raises ValueError: The validation bucket does not exist
		"""
		s3 = self.session.resource('s3')

		try:
		    logging.info("Checking if validation bucket %s exists", s3_bucket)

		    bucket = s3.Bucket(s3_bucket)
		    if bucket.creation_date is None:
		        raise ValueError("Validation bucket {0} does not exist".format(s3_bucket))

		except ClientError as e:
		    logging.error(e)
		    raise
		except ValueError as e:
		    logging.error(e)
		    raise


	def _wait_for_sqs_queue(self):
		"""Waits for SQS FIFO queue to exist within given timeout.

		:returns: True if becomes available before timeout, False otherwise
		:rtype: bool
		"""
		queue = self.job_id + '.fifo'
		end = time.time() + self.queue_init_timeout

		try:
			logging.info("Waiting for SQS queue %s to become available", queue)
			while time.time() < end:
				if self._get_sqs_queue(queue) and self._check_sqs_has_messages():
					return True
			logging.error("SQS queue %s was not available after %s seconds", queue, self.queue_init_timeout)
			return False

		except ClientError as e:
			logging.error(e)
			return False


	def _get_sqs_queue(self, queue):
		"""Checks if SQS queue exists.

		:param str queue: Name of the SQS queue to check
		:returns: True if SQS queue exits, False otherwise. 
		:rtype: bool
		:raises ClientError: SQS resource exception
		"""
		sqs_resource = self.session.resource('sqs')

		try:
			q = sqs_resource.get_queue_by_name(
				QueueName=queue
			)
			logging.info("SQS queue %s exists", queue)
			return True
		except:
			return False


	def _check_sqs_has_messages(self, complete=False):
		"""Function will verify if the source log file has been created indicating 
		messages have been added to the SQS queue. If the complete parameter is set 
		to True, it will specifically look for the source log file that has .queued 
		appended to the suffix, indicating the SQS queue has been fully populated.

		:param bool complete: True checks if source log has the .queued suffix, False 
			otherwise
		:returns: True if messages are on queue based on the source logs, False otherwise
		:rtype: bool
		"""

		log_prefix = '/'.join([self.s3_validation_prefix, self.job_id, self.source_log])
		objs = self._get_s3_object_list(log_prefix)

		if complete and len(objs) > 0:

			for obj in objs:
				if obj.key == log_prefix +'.queued':
					logging.info(self.source_log + ".queued found in S3 bucket %s", self.s3_validation_bucket)
					return True	
			return False
		else:
			if len(objs) > 0: 
				logging.info(self.source_log + " exist in S3 bucket %s", self.s3_validation_bucket)
				return True
			return False


	def _get_s3_object_list(self, s3_bucket_prefix):
		"""Helper function that will get a list of objects in the validation
		bucket with the specified prefix

		:param str s3_bucket_prefix: The prefix of the S3 objects to filter on
		:returns: List of S3 ObjectSummary objects
		:rtype: ObjectSummary
		:raises ClientError: S3 resource exception
		"""
		s3 = self.session.resource('s3')

		try:
			bucket = s3.Bucket(self.s3_validation_bucket)
			objs = list(bucket.objects.filter(Prefix=s3_bucket_prefix))
			return objs
		except ClientError as e:
			logging.error(e)


	def _process_sqs_queue(self):
		"""Function process messages until no more messages can be read from SQS 
		queue and source log .queued file has been populated in S3. 

		:raises ClientError: SQS client exception
		"""
		sqs_client = self.session.client('sqs')
		queue = self.job_id +'.fifo'

		try:
			response = sqs_client.get_queue_url(
					QueueName=queue
			)

			while True:

				logging.info("Getting next message from SQS queue %s", response['QueueUrl'])
				msg = self._get_sqs_message(response['QueueUrl'])

				# check if sqs was deleted to prevent endless loop
				if msg == 'NonExistentQueue':
					logging.error("SQS queue %s no longer exists", queue)
					break

				#check if queue has finished populating and message is None
				if msg is None and self._check_sqs_has_messages(True):
					logging.info("All SQS messages have been processed")		
					break

				# process message
				if msg is not None:
					logging.info("Processing message %s", msg['Body'])	

					payload = msg['Body']
					self._delete_sqs_message(response['QueueUrl'], msg['ReceiptHandle'])

					# execute the validation
					self._validate_message(payload)

				else:
					logging.info("Message was empty and SQS queue is still being populated")

		except ClientError as e:
			logging.error(e)


	def _get_sqs_message(self, queue_url):
		"""Get the next message from the SQS queue.

		:param queue_url: String URL of existing SQS queue
		:returns: Dictionary object of SQS message
		:rtype: dict
		:raises ClientError: SQS client exception
		"""
		sqs_client = self.session.client('sqs')

		try:
			messages = sqs_client.receive_message(
				QueueUrl=queue_url,
				MaxNumberOfMessages=1,	# only recieve a single message
				WaitTimeSeconds=20, 	# enable long polling
				VisibilityTimeout=10    
			)

			if 'Messages' in messages:
				msg = messages['Messages'][0]
				logging.info("Received message with receipt handle %s", msg['ReceiptHandle'])
				return msg
			else:
				logging.info("SQS queue %s did not return a messages", queue_url)
				return None

		except ClientError as e:
			if e.response['Error']['Code'] == \
	               'AWS.SimpleQueueService.NonExistentQueue':
				return 'NonExistentQueue'
			else:
				logging.error(e)
				return None


	def _delete_sqs_message(self, queue_url, msg_receipt_handle):
	    """Delete a message from an SQS queue

	    :param queue_url: String URL of existing SQS queue
	    :param msg_receipt_handle: Receipt handle value of retrieved message
	    :raises ClientError: SQS client exception
	    """
	    # Delete the message from the SQS queue
	    sqs_client = self.session.client('sqs')
	    try:
	    	sqs_client.delete_message(
	    		QueueUrl=queue_url, 
	    		ReceiptHandle=msg_receipt_handle
	    	)
	    	logging.info("Deleted message with receipt handle %s ", msg_receipt_handle)
	    except ClientError as e:
	    	logging.error(e)


	def _validate_message(self, payload):
		"""Retrieves file to be validated, executes the AIF validation, uploads validation results
		to S3, and moves file be validated to appropriate place on S3.

		:param str payload: The S3 object path obtained from the SQS message
		"""
		# download the turtle file
		file_name = Path(payload).name
		self._download_s3_object(self.s3_validation_bucket, payload, file_name)
		validation_staging = 'validation-staging'

	    # verify file exists and move into processing directory
		if os.path.isfile(file_name):

			# create directory to store all validation results and file
			if not os.path.exists(validation_staging):
				os.makedirs(validation_staging)

			os.rename(file_name, validation_staging + '/' + file_name)
			code = self._execute_validation(validation_staging + '/' + file_name)

			# upload any log output to s3
			self._upload_validation_output(validation_staging, '/'.join([self.s3_validation_prefix, self.job_id, 'LOG']), '.log')

			# valid
			if code == 0:
				self._move_s3_object(payload, '/'.join([self.s3_validation_prefix, self.job_id, 'VALID', file_name]))
			# invalid
			elif code == 1:
				self._upload_validation_output(validation_staging, '/'.join([self.s3_validation_prefix, self.job_id, 'INVALID']), '.txt')
				self._move_s3_object(payload, '/'.join([self.s3_validation_prefix, self.job_id, 'INVALID', file_name]))
			# timeout error
			elif code == -1:
				self._move_s3_object(payload, '/'.join([self.s3_validation_prefix, self.job_id, 'TIMEOUT', file_name]))
			# other error occurred
			elif code > 1:
				self._move_s3_object(payload, '/'.join([self.s3_validation_prefix, self.job_id, 'ERROR', file_name]))

			# clean up validation staging
			logging.info("Cleaning up validation staging directory %s", validation_staging)
			shutil.rmtree(validation_staging)

		else:
			logging.error("Unable to download S3 object %s", payload)


	def _download_s3_object(self, s3_bucket, s3_object, file_name):
		"""Downloads the object from validation bucket based on s3 object paths extracted from the
		SQS message payload.

		:param str s3_bucket: The S3 bucket to download from
		:param str s3_object: The S3 object to download
		:param str file_name: The file name of the saved object
		:raises ClientError: S3 client exception
		"""
		s3_client = self.session.client('s3')

		try:
			logging.info("Downloading %s from bucket %s", s3_object, s3_bucket)
			s3_client.download_file(s3_bucket, s3_object, file_name)
		except ClientError as e:
			logging.error(e)


	def _execute_validation(self, file_path):
		"""Executes the AIF Validator as a sub-process for the turtle file located at the specified 
		file path. 

		:param str file_path: The local path to the file that will be validated
		:returns: Return code that specifies the validation execution result 
		:rtype: int
		"""
		file_name = Path(file_path).name

		try:
			cmd = self.validation_home + '/target/appassembler/bin/validateAIF ' + self.validation_flags + ' -f '
			cmd += file_path

			logging.info("Executing AIF Validation for file %s with flags %s", file_name, self.validation_flags)
			#**********************
			# Requires python 3.7+ *
			#**********************
			output = subprocess.run(cmd, stdout=PIPE, timeout=self.validation_timeout, check=True, shell=True, universal_newlines=True)

			f = open(file_path+'.log', 'w')
			f.write(output.stdout)
			f.close()
			logging.info("Validation succeeded for file %s", file_name)
			return 0
			
		except CalledProcessError as e:
			f = open(file_path+'.log', 'w')
			f.write(e.output)
			f.close()
			logging.info("Validation failed for file %s with error code %s", file_name, str(e.returncode))
			return e.returncode
		except TimeoutExpired as e:
			f = open(file_path+'.log', 'w')
			f.write(e.output)
			f.close()
			logging.info("Validation timed out for file %s after %s seconds", file_name, str(self.validation_timeout))
			return -1


	def _upload_validation_output(self, validation_dir, s3_object_prefix, extension):
		"""Will create a list of files with a specific extension from the validation output and upload
		each to the specified bucket and location in S3.

		:param str validation_dir: The validation directory that contains the output
		:param str s3_object_prefix: The s3 prefix that will be appended to the uploaded files
		:param str extension: The extension to search on in the validation directory
		"""
		items = glob.glob(validation_dir + '**/*' + extension)

		if len(items) <= 0: 
			logging.error("No validation output files found in validation folder with extension %s", extension)
		elif len(items) > 1: 
			logging.info("Found multiple validation output %s files in validation staging folder", extension)
		
		for item in items:
			logging.info("Uploading %s file %s to S3 with prefix %s", extension, item, self.s3_validation_bucket + '/' + s3_object_prefix)
			self._upload_file_to_s3(self.s3_validation_bucket, s3_object_prefix, item)


	def _upload_file_to_s3(self, s3_bucket, s3_bucket_prefix, filepath):
	    """Helper function to upload single file to S3 bucket with specified prefix

		:param str s3_bucket: The s3 bucket to upload file
	    :param str s3_prefix: The prefix to prepend to the filename
	    :param str filepath: The local path of the file to be uploaded
	    :raises ClientError: S3 client exception
	    """
	    s3_client = self.session.client('s3')

	    try:
	        s3_object = '/'.join([s3_bucket_prefix, Path(filepath).name])

	        logging.info("Uploading %s to bucket %s", s3_object, s3_bucket + '/' + s3_bucket_prefix)
	        s3_client.upload_file(str(filepath), s3_bucket, s3_object)

	    except ClientError as e:
	        logging.error(e)


	def _move_s3_object(self, s3_object, s3_object_dest):
		"""Helper function that will move an S3 object within the validation bucket.

		:param str s3_object: The s3 object to be moved
		:param str s3_object_dest: The new s3 object destination
		:raises ClientError: S3 resource exception
		"""
		s3 = self.session.resource('s3')

		try:
			logging.info("Moving s3 object %s from %s to %s ", s3_object, self.s3_validation_bucket, s3_object_dest)
			s3.Object(self.s3_validation_bucket, s3_object_dest).copy_from(CopySource=self.s3_validation_bucket + '/' + s3_object)
			s3.Object(self.s3_validation_bucket, s3_object).delete()

		except ClientError as e:
			logging.error(e)


def read_envs():
	"""Function will read in all environment variables into a dictionary

	:returns: Dictionary containing all environment variables or defaults
	:rtype: dict
	"""
	envs = {}
	envs['QUEUE_INIT_TIMEOUT'] = os.environ.get('QUEUE_INIT_TIMEOUT', '3600') 
	envs['VALIDATION_TIMEOUT'] = os.environ.get('VALIDATION_TIMEOUT', '28800')
	envs['VALIDATION_HOME'] = os.environ.get('VALIDATION_HOME', '/opt/aif-validator')
	envs['VALIDATION_FLAGS'] = os.environ.get('VALIDATION_FLAGS')
	envs['S3_VALIDATION_BUCKET'] = os.environ.get('S3_VALIDATION_BUCKET')
	envs['S3_VALIDATION_PREFIX'] = os.environ.get('S3_VALIDATION_PREFIX')
	envs['AWS_BATCH_JOB_ID'] = os.environ.get('AWS_BATCH_JOB_ID')
	envs['AWS_BATCH_JOB_NODE_INDEX'] = os.environ.get('AWS_BATCH_JOB_NODE_INDEX')
	envs['AWS_DEFAULT_REGION'] = os.environ.get('AWS_DEFAULT_REGION', 'us-east-1')

	return envs


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

    # check if QUEUE_INIT_TIMEOUT can be converted to int
    try:
        int(envs['QUEUE_INIT_TIMEOUT'])
    except ValueError:
        logging.error("Queue initialization timeout [%s] must be an integer", envs['QUEUE_INIT_TIMEOUT'])
        return False

    # check if VALIDATION_TIMEOUT can be converted to int
    try:
        int(envs['VALIDATION_TIMEOUT'])
    except ValueError:
        logging.error("Validation timeout [%s] must be an integer", envs['VALIDATION_TIMEOUT'])
        return False

    # print out JAVA_OPTS for logging purposes
    logging.info("Envrionment variable JAVA_OPTS is set to %s", os.environ.get('JAVA_OPTS'))

    return True


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


def main():

    # validate environment variables
    envs = read_envs()
    
    # set logging to log to stdout
    logging.basicConfig(level=os.environ.get('LOGLEVEL', 'INFO'))

    if validate_envs(envs):

        worker = Worker(envs)
        worker.run()

    else:
        raise ValueError("Exception occured when validating environment variables") 


if __name__ == "__main__": main()


