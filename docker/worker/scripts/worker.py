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


def wait_for_sqs_queue(queue_name, timeout):
	"""Waits for SQS FIFO queue to exist within given timeout.

	:param str queue_name: Name of the SQS queue 
	:param int timeout: Seconds to wait for SQS to become available
	:returns: True if queue exists, False otherwise
	:rtype: bool
	"""
	queue_name += '.fifo'
	end = time.time() + timeout

	try:
		logging.info("Waiting for SQS queue %s to become available", queue_name)
		while time.time() < end:
			if get_sqs_queue(queue_name):
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
			logging.info("SQS queue %s has no more messages", queue_url)
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


def process_sqs_queue(queue_name):
	"""Function will 
	"""
	sqs_client = boto3.client('sqs')
	queue_name += '.fifo'

	try:
		response = sqs_client.get_queue_url(
			QueueName=queue_name
		)

		msg = get_sqs_message(response['QueueUrl'])

		# loop process all the messages in the queue
		while msg is not None:
			logging.info("Processing message %s", msg['Body'])		
			delete_sqs_message(response['QueueUrl'], msg['ReceiptHandle'])

			sleep = random.randint(1,6)
			logging.info("Sleeping for %s seconds to simulate processing", sleep)
			time.sleep(sleep)

			# get the next message
			msg = get_sqs_message(response['QueueUrl'])

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

    return True


def main():

	envs = {}
	envs['QUEUE_INIT_TIMEOUT'] = os.environ.get('QUEUE_INIT_TIMEOUT')
	envs['AWS_BATCH_JOB_ID'] = os.environ.get('AWS_BATCH_JOB_ID')
	envs['AWS_BATCH_JOB_NODE_INDEX'] = os.environ.get('AWS_BATCH_JOB_NODE_INDEX')
	envs['WORKER_LOG_LEVEL'] = os.environ.get('WORKER_LOG_LEVEL', 'INFO') # default log level
	envs['AWS_DEFAULT_REGION'] = os.environ.get('AWS_DEFAULT_REGION')

    # set logging to log to stdout
	logging.basicConfig(level=os.environ.get('LOGLEVEL', envs['WORKER_LOG_LEVEL']))

    # verify environment variables and wait for SQS queue to become available
	if validate_envs(envs) and wait_for_sqs_queue(envs['AWS_BATCH_JOB_ID'], int(envs['QUEUE_INIT_TIMEOUT'])):

		# process messages
		process_sqs_queue(envs['AWS_BATCH_JOB_ID'])


if __name__ == "__main__": main()