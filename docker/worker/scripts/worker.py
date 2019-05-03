import os
import logging
import boto3
import time
import json
import random
from botocore.exceptions import ClientError


def get_sqs_queue(queue_name):
	"""Will check if SQS queue exists.

	:param str queue_name: Name of the SQS queue to check
	:returns: True if SQS queue exits, False otherwise. 
	:rtype: bool
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
	:param str sourcefiles_s3_object: The s3 sourcefiles object to check
	:param bool complete: True if the sourcefile has the .done suffix, False 
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

	:param str prefix: The prefix of the S3 objects to filter on
	:returns: List of S3 ObjectSummary objects
	:rtype: ObjectSummary
	"""
	s3 = boto3.resource('s3')

	try:
		bucket = s3.Bucket(s3_bucket)
		objs = list(bucket.objects.filter(Prefix=prefix))
		return objs
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


def process_sqs_queue(batch_job_id, validation_bucket):
	"""Function process messages until no more messages can be read from SQS 
	queue and sourcefiles.done file has been populated in S3. 

	:param str batch_job_id: The id of the batch job as well as the name of 
		the SQS queue.
	:param str validation_bucket: The S3 bucket that stores batch job output
	"""
	sqs_client = boto3.client('sqs')
	queue_name = batch_job_id+'.fifo'

	try:
		response = sqs_client.get_queue_url(
				QueueName=queue_name
		)

		while True:

			msg = get_sqs_message(response['QueueUrl'])

			#check if queue has finished populating and message is None
			if msg is None and check_sqs_has_messages(validation_bucket, batch_job_id, True):
				logging.info("All SQS messages have been processed")		
				break

			# process message
			if msg is not None:
				logging.info("Processing message %s", msg['Body'])		
				delete_sqs_message(response['QueueUrl'], msg['ReceiptHandle'])

				sleep = random.randint(1,6)
				logging.info("Sleeping for %s seconds to simulate processing", sleep)
				time.sleep(sleep)
			else:
				logging.info("Message was empty and SQS queue is still being populated")

	except ClientError as e:
		logging.error(e)


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
        logging.error("Master sleep interval [%s] must be an integer", envs['QUEUE_INIT_TIMEOUT'])
        return False

   	# check that the validation bucket exists
    if not bucket_exists(envs['S3_VALIDATION_BUCKET']):
        logging.error("S3 validation bucket %s does not exist", envs['S3_VALIDATION_BUCKET'])
        return False

    return True


def main():

	envs = {}
	envs['QUEUE_INIT_TIMEOUT'] = os.environ.get('QUEUE_INIT_TIMEOUT', '28800') # default to 8 hours
	envs['S3_VALIDATION_BUCKET'] = os.environ.get('S3_VALIDATION_BUCKET')
	envs['AWS_BATCH_JOB_ID'] = os.environ.get('AWS_BATCH_JOB_ID')
	envs['AWS_BATCH_JOB_NODE_INDEX'] = os.environ.get('AWS_BATCH_JOB_NODE_INDEX')
	envs['WORKER_LOG_LEVEL'] = os.environ.get('WORKER_LOG_LEVEL', 'INFO') # default log level
	envs['AWS_DEFAULT_REGION'] = os.environ.get('AWS_DEFAULT_REGION')

    # set logging to log to stdout
	logging.basicConfig(level=os.environ.get('LOGLEVEL', envs['WORKER_LOG_LEVEL']))

    # verify environment variables and wait for SQS queue to become available
	if validate_envs(envs):

		# extract job id from AWS_BATCH_JOB_ID and reset
		envs['AWS_BATCH_JOB_ID'] = (envs['AWS_BATCH_JOB_ID']).split("#")[0]
		logging.info("Extracted AWS_BATCH_JOB_ID as %s", envs['AWS_BATCH_JOB_ID'])

		if wait_for_sqs_queue(envs['AWS_BATCH_JOB_ID'], envs['S3_VALIDATION_BUCKET'], int(envs['QUEUE_INIT_TIMEOUT'])):

			# process messages
			process_sqs_queue(envs['AWS_BATCH_JOB_ID'], envs['S3_VALIDATION_BUCKET'])


if __name__ == "__main__": main()