import os
import boto3
import logging
import json
import init as init
from pathlib import Path
from botocore.exceptions import ClientError


def extract_submission_paths(s3_submission_bucket):
	    """Helper function to extract s3 and file path information from s3 submission 
	    path.

	    :returns: 
	        - s3_bucket - the extracted S3 bucket
	        - s3_prefix- the extracted S3 prefix
	    :rtype: (str, str)
	    """
	    path = Path(s3_submission_bucket)
	    s3_bucket = path.parts[0]          
	    s3_prefix= '/'.join(path.parts[1:])   

	    return s3_bucket, s3_prefix


def get_submission_object_list(session, s3_bucket, s3_bucket_prefix):
	"""Helper function that will get a list of objects in the validation
	bucket with the specified prefix

	:param str s3_bucket_prefix: The prefix of the S3 objects to filter on
	:returns: List of S3 ObjectSummary objects
	:rtype: ObjectSummary
	:raises ClientError: S3 resource exception
	"""
	s3 = session.resource('s3')

	try:
		bucket = s3.Bucket(s3_bucket)
		objs = list(bucket.objects.filter(Prefix=s3_bucket_prefix))
		return objs
	except ClientError as e:
		logging.error(e) 


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

    return True


def is_env_set(env, value):
    """Helper function to check if a specific environment variable is not None

    :param str env: The name of the environment variable
    :param str value: The value of the environment variable
    :returns: True if environment variable is set, False otherwise
    :rtype: bool
    """

    # JAVA OPTS is not required to be set
    if env is 'JAVA_OPTS':
        logging.info("Environment variable JAVA_OPTS is set to %s", value)
        return True

    if not value:
        logging.error("Environment variable %s is not set", env)
        return False

    logging.info("Environment variable %s is set to %s", env, value)
    return True


def read_envs():
    """Function will read in all environment variables into a dictionary

    :returns: Dictionary containing all environment variables or defaults
    :rtype: dict
    """
    envs = {}
    envs['S3_SUBMISSIONS_BUCKET_PATH'] = os.environ.get('S3_SUBMISSIONS_BUCKET_PATH')
    envs['S3_SUBMISSIONS_RESULTS_DIRECTORY'] = os.environ.get('S3_SUBMISSIONS_RESULTS_DIRECTORY')
    envs['BATCH_NUM_NODES'] = os.environ.get('BATCH_NUM_NODES')
    envs['BATCH_JOB_DEFINITION'] = os.environ.get('BATCH_JOB_DEFINITION')
    envs['BATCH_JOB_QUEUE'] = os.environ.get('BATCH_JOB_QUEUE')
    envs['AWS_SNS_TOPIC_ARN'] = os.environ.get('AWS_SNS_TOPIC_ARN')
    envs['AWS_DEFAULT_REGION'] = os.environ.get('AWS_DEFAULT_REGION')
    envs['NIST_VALIDATION_FLAGS'] = os.environ.get('NIST_VALIDATION_FLAGS', '--dwd --nist -o')
    envs['UNRESTRICTED_VALIDATION_FLAGS'] = os.environ.get('UNRESTRICTED_VALIDATION_FLAGS', '--dwd -o')
    envs['NIST_TA3_VALIDATION_FLAGS'] = os.environ.get('NIST_TA3_VALIDATION_FLAGS', '--dwd --nist-ta3 -o')
    envs['JAVA_OPTS'] = os.environ.get('JAVA_OPTS')

    return envs

def main():
	"""
	"""
    # validate environment variables
	envs = read_envs()
    
    # set logging to log to stdout
	logging.basicConfig(level=os.environ.get('LOGLEVEL', 'INFO'))

	if validate_envs(envs):

		session = boto3.session.Session(region_name=envs['AWS_DEFAULT_REGION'])

    	# extract the bucket and the prefix where submissions are located
		s3_bucket, s3_prefix = extract_submission_paths(envs['S3_SUBMISSIONS_BUCKET_PATH'])

    	# populate the initialization environment variable dictionary
		batch_init_envs = {
			'S3_VALIDATION_BUCKET': s3_bucket,
			'S3_VALIDATION_PREFIX': s3_prefix + '/' + envs['S3_SUBMISSIONS_RESULTS_DIRECTORY'],
    		'BATCH_NUM_NODES': envs['BATCH_NUM_NODES'],
    		'BATCH_JOB_DEFINITION': envs['BATCH_JOB_DEFINITION'],
    		'BATCH_JOB_QUEUE': envs['BATCH_JOB_QUEUE'],
    		'AWS_SNS_TOPIC_ARN': envs['AWS_SNS_TOPIC_ARN'],
            'AWS_DEFAULT_REGION': envs['AWS_DEFAULT_REGION'],
            'NIST_VALIDATION_FLAGS': envs['NIST_VALIDATION_FLAGS'],
            'UNRESTRICTED_VALIDATION_FLAGS': envs['UNRESTRICTED_VALIDATION_FLAGS'],
            'NIST_TA3_VALIDATION_FLAGS': envs['NIST_TA3_VALIDATION_FLAGS'],
            'JAVA_OPTS': envs['JAVA_OPTS']
    	}

    	# get the full list of submissions located in the bucket/prefix
		submissions = get_submission_object_list(session, s3_bucket, s3_prefix)

    	# this is used to ensure only items in the current prefix directory are read in
    	# TODO there has to be a better solution for this
		prefix_count = len(Path(envs['S3_SUBMISSIONS_BUCKET_PATH']).parts)

		for x in range(len(submissions)):

			# A directory in s3 is an object so we must check and ignore it 
            # We also want to ignore any other files that are nested within directories
			if submissions[x].key[-1] != '/' and len(Path(submissions[x].key).parts) == prefix_count:
				logging.info("Found submission: %s", (str(submissions[x])))
				batch_init_envs['S3_SUBMISSION_ARCHIVE_PATH'] = submissions[x].bucket_name + '/' + submissions[x].key

				logging.info(json.dumps(batch_init_envs, indent = 4))

				# run the initilization for the submission
				try: 
					i = init.Initialize(batch_init_envs)
					i.run()
				except Exception as e:
					logging.error(e)
    		
	else:
		raise ValueError("Exception occurred when validating environment variables") 


if __name__ == "__main__": main()

