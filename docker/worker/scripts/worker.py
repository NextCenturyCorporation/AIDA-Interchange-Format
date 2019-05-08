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


def get_sqs_queue(queue_name):
	"""Checks if SQS queue exists.

	:param str queue_name: Name of the SQS queue to check
	:returns: True if SQS queue exits, False otherwise. 
	:rtype: bool
	:raises ClientError: SQS resource exception
	"""
	sqs_resource = boto3.resource('sqs')

	try:
		queue = sqs_resource.get_queue_by_name(
			QueueName=queue_name
		)
		logging.info("SQS queue %s exists", queue_name)
		return True
	except:
		return False


def check_sqs_has_messages(s3_bucket, batch_job_id, complete=False):
	"""Function will verify if the sourcefiles has been created
	indicating messages have been added to the SQS queue. If the complete flag
	is set to True, it will specifically look for the sourcefiles key that has
	.done in the suffix, indicating the SQS queue has been fully populated.

	:param str s3_bucket: The s3 bucket that contains the sourcefiles object
	:param str batch_job_id: The id of the batch job
	:param bool complete: True checks if sourcefile has the .done suffix, False 
		otherwise
	:returns: True if file exists, False otherwise
	:rtype: bool
	"""

	sourcefiles_prefix = '/'.join([batch_job_id, 'output', 'log', 'sourcefiles'])
	objs = get_s3_object_list(s3_bucket, sourcefiles_prefix)

	if complete and len(objs) > 0:

		for obj in objs:
			if obj.key == sourcefiles_prefix+'.done':
				logging.info("sourcefiles.done found in S3 bucket %s", s3_bucket)
				return True
		logging.info("sourcefiles.done does not exist S3 bucket %s", s3_bucket)
		return False
	else:
		if len(objs) > 0: 
			logging.info("sourcefiles exist in S3 bucket %s", s3_bucket)
			return True

		logging.info("sourcefiles does not exist in S3 bucket %s", s3_bucket)
		return False


def get_s3_object_list(s3_bucket, prefix):
	"""Helper function that will get a list of objects in an S3 bucket
	with the spefified prefix

	:param str s3_bucket: The S3 bucket
	:param str prefix: The prefix of the S3 objects to filter on
	:returns: List of S3 ObjectSummary objects
	:rtype: ObjectSummary
	:raises ClientError: S3 resource exception
	"""
	s3 = boto3.resource('s3')

	try:
		bucket = s3.Bucket(s3_bucket)
		objs = list(bucket.objects.filter(Prefix=prefix))
		return objs
	except ClientError as e:
		logging.error(e)


def download_s3_object(s3_bucket, s3_object, file_name):
	"""Downloads the object from s3 based on s3 object paths extracted from the
	SQS message payload.

	:param str s3_bucket: The S3 bucket to download from
	:param str s3_object: The S3 object to download
	:param str file_name: The file name of the saved object
	:raises ClientError: S3 client exception
	"""
	s3_client = boto3.client('s3')

	try:
		logging.info("Downloading %s from bucket %s", s3_object, s3_bucket)
		s3_client.download_file(s3_bucket, s3_object, file_name)
	except ClientError as e:
		logging.error(e)

def move_s3_object(s3_bucket, s3_object, s3_object_dest):
	"""Helper function that will move an S3 object within the s3 bucket.

	:param str s3_bucket: The s3 bucket that contains the s3_object
	:param str s3_object: The s3 object to be moved
	:param str s3_object_dest: The new s3 object destination
	:raises ClientError: S3 resource exception
	"""
	s3 = boto3.resource('s3')

	try:
		logging.info("Moving s3 object %s from %s to %s ", s3_object, s3_bucket, s3_object_dest)
		s3.Object(s3_bucket, s3_object_dest).copy_from(CopySource=s3_bucket+'/'+s3_object)
		s3.Object(s3_bucket, s3_object).delete()

	except ClientError as e:
		logging.error(e)


def upload_file_to_s3(s3_bucket, s3_prefix, filepath):
    """Helper function to upload single file to S3 bucket with specified prefix

    :param str s3_bucket_name: Name of the S3 bucket where the file will be uploaded
    :param str s3_prefix: The prefix to prepend to the filename
    :param str filepath: The local path of the file to be uploaded
    :raises ClientError: S3 client exception
    """
    s3_client = boto3.client('s3')

    try:
        s3_object = '/'.join([s3_prefix, Path(filepath).name])

        logging.info("Uploading %s to bucket %s", s3_object, s3_bucket)
        s3_client.upload_file(str(filepath), s3_bucket, s3_object)

    except ClientError as e:
        logging.error(e)


def bucket_exists(s3_bucket_name):
    """Helper function that will check if a S3 bucket exists

    :param str s3_bucket_name: The S3 bucket that is being checked
    :returns: True if bucket exists, False otherwise
    :rtype: bool
    :raises ClientError: S3 resource exception
    """
    s3 = boto3.resource('s3')

    try:
        bucket = s3.Bucket(s3_bucket_name)
        if bucket.creation_date is not None:
            return True
        return False
    except ClientError as e:
        logging.error(e)


def wait_for_sqs_queue(batch_job_id, validation_bucket, timeout):
	"""Waits for SQS FIFO queue to exist within given timeout.

	:param str batch_job_id: The id of the batch job as well as the n
		ame of the SQS queue.
	:param str validation_bucket: The S3 bucket that stores batch job output
	:param int timeout: Seconds to wait for SQS to become available
	:returns: True if queue exists, False otherwise
	:rtype: bool
	"""
	queue_name = batch_job_id + '.fifo'
	end = time.time() + timeout

	try:
		logging.info("Waiting for SQS queue %s to become available", queue_name)
		while time.time() < end:
			if get_sqs_queue(queue_name) and check_sqs_has_messages(validation_bucket, batch_job_id):
				return True
		logging.error("SQS queue %s was not available after %s seconds", queue_name, timeout)
		return False
	except ClientError as e:
		logging.error(e)


def get_sqs_message(queue_url):
	"""Get the next message from the SQS queue

	:param queue_url: String URL of existing SQS queue
	:returns: Dictionary object of SQS message
	:rtype: dict
	:raises ClientError: SQS client exception
	"""
	sqs_client = boto3.client('sqs')

	try:
		messages = sqs_client.receive_message(
			QueueUrl=queue_url,
			MaxNumberOfMessages=1,	# only recieve a single message
			WaitTimeSeconds=20, 	# enable long polling
			VisibilityTimeout=10    
		)

		if 'Messages' in messages:
			msg = messages['Messages'][0]
			logging.info("Recieved message with receipt handle %s", msg['ReceiptHandle'])
			return msg
		else:
			logging.info("SQS queue %s did not return a messages", queue_url)
			return None
	except ClientError as e:
		logging.error(e)
		return None


def delete_sqs_message(queue_url, msg_receipt_handle):
    """Delete a message from an SQS queue

    :param queue_url: String URL of existing SQS queue
    :param msg_receipt_handle: Receipt handle value of retrieved message
    :raises ClientError: SQS client exception
    """

    # Delete the message from the SQS queue
    sqs_client = boto3.client('sqs')
    try:
    	sqs_client.delete_message(
    		QueueUrl=queue_url, 
    		ReceiptHandle=msg_receipt_handle
    	)
    	logging.info("Deleted message with receipt handle %s ", msg_receipt_handle)
    except ClientError as e:
    	logging.error(e)


def process_sqs_queue(batch_job_id, validation_bucket, validation_timeout):
	"""Function process messages until no more messages can be read from SQS 
	queue and sourcefiles.done file has been populated in S3. 

	:param str batch_job_id: The id of the batch job as well as the name of 
		the SQS queue.
	:param str validation_bucket: The S3 bucket that stores batch job output
	:param int validation_timeout: The the timeout for the AIF validation subprocess
	:raises ClientError: SQS client exception
	"""
	sqs_client = boto3.client('sqs')
	queue_name = batch_job_id+'.fifo'

	try:
		response = sqs_client.get_queue_url(
				QueueName=queue_name
		)

		while True:

			logging.info("Getting next message from SQS queue %s", response['QueueUrl'])
			msg = get_sqs_message(response['QueueUrl'])

			#check if queue has finished populating and message is None
			if msg is None and check_sqs_has_messages(validation_bucket, batch_job_id, True):
				logging.info("All SQS messages have been processed")		
				break

			# process message
			if msg is not None:
				logging.info("Processing message %s", msg['Body'])	

				payload = msg['Body']
				delete_sqs_message(response['QueueUrl'], msg['ReceiptHandle'])

				# This is where the processing happens
				validate_message(validation_bucket, batch_job_id, payload, validation_timeout)

			else:
				logging.info("Message was empty and SQS queue is still being populated")

	except ClientError as e:
		logging.error(e)


def validate_message(s3_bucket, batch_job_id, payload, timeout):
	"""Downloads the file to be validated, executes the AIF validation, uploads validation results
	to S3, and moves file be validated to appropriate place on S3.

	:param str s3_bucket: The S3 validation bucket where files are located
	:param str batch_job_id: The id of the batch job
	:param str payload: The S3 object path obtained from the SQS message
	:param int timeout: The the timeout for the AIF validation subprocess
	"""
	# download the turtle file
	file_name = Path(payload).name
	download_s3_object(s3_bucket, payload, file_name)
	validation_staging = 'validation-staging'

    # verify file exists and move into processing directory
	exists = os.path.isfile(file_name)

	if exists:

		# create directory to store all validation results and file
		if not os.path.exists(validation_staging):
			os.makedirs(validation_staging)

		os.rename(file_name, validation_staging+'/'+file_name)
		code = execute_validation(validation_staging+'/'+file_name, timeout)

		# upload any log output to s3
		upload_validation_output(validation_staging, s3_bucket, '/'.join([batch_job_id, 'LOG']), '.log')

		# valid
		if code == 0:
			move_s3_object(s3_bucket, payload, '/'.join([batch_job_id, 'VALID', file_name]))
		# invalid
		elif code == 1:
			upload_validation_output(validation_staging, s3_bucket, '/'.join([batch_job_id, 'INVALID']), '.txt')
			move_s3_object(s3_bucket, payload, '/'.join([batch_job_id, 'INVALID', file_name]))
		# timeout error
		elif code == -1:
			move_s3_object(s3_bucket, payload, '/'.join([batch_job_id, 'TIMEOUT', file_name]))
		# other error occurred
		elif code > 1:
			move_s3_object(s3_bucket, payload, '/'.join([batch_job_id, 'ERROR', file_name]))

		# clean up validation staging
		logging.info("Cleaning up validation staging directory %s", validation_staging)
		shutil.rmtree(validation_staging)

	else:
		logging.error("Unable to download S3 object %s", payload)


def execute_validation(file_path, timeout):
	"""Executes the AIF Validator as a subprocess for the turtle file located at the specified 
	file path. 

	:param str file_path: The path of the turtle (TTL) file to be validated
	:param int timeout: The the timeout for the AIF validation subprocess
	:returns: Return code that specifies the validation execution result 
	:rtype: int
	"""
	file_name = Path(file_path).name

	try:
		cmd = '/home/HQ/psharkey/Development/AIDA/AIDA-Interchange-Format/target/appassembler/bin/validateAIF --ldc --nist -o -f '
		cmd += file_path

		logging.info("Executing AIF Validation for file %s", file_name)
		#**********************
		# Requies python 3.7+ *
		#**********************
		output = subprocess.run(cmd, stdout=PIPE, timeout=timeout, check=True, shell=True, universal_newlines=True)
		#output = subprocess.run(cmd, timeout=timeout, shell=True, universal_newlines=True)
		f = open(file_path+'.log', 'w')
		f.write(str(output))
		f.close()
		logging.info("Validation succeeded for file %s", file_name)
		return 0
		
	except CalledProcessError as e:
		f = open(file_path+'.log', 'w')
		f.write(str(e.output))
		f.close()
		logging.info("Validation failed for file %s with error code %s", file_name, str(e.returncode))
		return e.returncode
	except TimeoutExpired as e:
		f = open(file_path+'.log', 'w')
		f.write(str(e.output))
		f.close()
		logging.info("Validation timed out for file %s after %s seconds", file_name, str(timeout))
		return -1


def upload_validation_output(validation_dir, s3_bucket, s3_object_prefix, extension):
	"""Will create a list of files with a specific extension from the validation output and upload
	each to the specified bucket and location in S3.

	:param str validation_dir: The validation directory that contains the output
	:param str s3_bucket: The S3 bucket where output file(s) will be uploaded to
	:param str s3_object_prefix: The s3 prefix that will be appended to the uploaded files
	:param str extension: The extension to search on in the validation directory
	"""
	items = glob.glob(validation_dir + '**/*' + extension)

	if len(items) <= 0: 
		logging.error("No validation ouptut files found in validation folder with extension %s", extension)
	elif len(items) > 1: 
		logging.error("Found multiple validation output %s files in validation staging folder", extension)
	
	for item in items:
		logging.info("Uploading %s file %s to S3 with prefix %s", extension, item, s3_bucket+'/'+s3_object_prefix)
		upload_file_to_s3(s3_bucket, s3_object_prefix, item)


def is_env_set(env, value):
    """Helper function to check if a specific enviornment variable is not None

    :param str env: The name of the enviornment variable
    :param value: The value of the enviornment variable
    :returns: True if environment variable is set, False otherwise
    :rtype: bool
    """
    if not value:
        logging.error("Environment variable %s is not set", env)
        return False
    logging.info("Environment variable %s is set to %s", env, value)
    return True


def validate_envs(envs):
    """Helper function to validate all of the enviroment variables exist and are valid before
    processing starts.

    :param dict envs: Dictionary of all environment varaibles
    :returns: True if all environment variables are valid, False otherwise
    :rtype: bool
    """
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

   	# check that the validation bucket exists
    if not bucket_exists(envs['S3_VALIDATION_BUCKET']):
        logging.error("S3 validation bucket %s does not exist", envs['S3_VALIDATION_BUCKET'])
        return False

    return True


def main():

	envs = {}
	envs['QUEUE_INIT_TIMEOUT'] = os.environ.get('QUEUE_INIT_TIMEOUT', '28800') # default to 8 hours
	envs['VALIDATION_TIMEOUT'] = os.environ.get('VALIDATION_TIMEOUT', '120')
	envs['S3_VALIDATION_BUCKET'] = os.environ.get('S3_VALIDATION_BUCKET', 'aida-validation')
	envs['AWS_BATCH_JOB_ID'] = os.environ.get('AWS_BATCH_JOB_ID', 'c8c90aa7-4f33-4729-9e5c-0068cb9ce75c')
	envs['AWS_BATCH_JOB_NODE_INDEX'] = os.environ.get('AWS_BATCH_JOB_NODE_INDEX', '0')
	envs['WORKER_LOG_LEVEL'] = os.environ.get('WORKER_LOG_LEVEL', 'INFO') # default log level
	envs['AWS_DEFAULT_REGION'] = os.environ.get('AWS_DEFAULT_REGION', 'us-east-1')

    # set logging to log to stdout
	logging.basicConfig(level=os.environ.get('LOGLEVEL', envs['WORKER_LOG_LEVEL']))

    # verify environment variables and wait for SQS queue to become available
	if validate_envs(envs):

		# extract job id from AWS_BATCH_JOB_ID and reset
		envs['AWS_BATCH_JOB_ID'] = (envs['AWS_BATCH_JOB_ID']).split("#")[0]
		logging.info("Extracted AWS_BATCH_JOB_ID as %s", envs['AWS_BATCH_JOB_ID'])

		if wait_for_sqs_queue(envs['AWS_BATCH_JOB_ID'], envs['S3_VALIDATION_BUCKET'], int(envs['QUEUE_INIT_TIMEOUT'])):

			# process messages
			process_sqs_queue(envs['AWS_BATCH_JOB_ID'], envs['S3_VALIDATION_BUCKET'], int(envs['VALIDATION_TIMEOUT']))


if __name__ == "__main__": main()