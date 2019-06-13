import os
import boto3
import loggging
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

    	session = boto3.session.Session(region_name=self.aws_region)

    	# extract the bucket and the prefix where submissions are located
    	s3_bucket, s3_prefix = extract_submission_paths(envs['S3_SUBMISSION_BUCKET'])

    	# get the full list of submissions located in the bucket/prefix
    	submissions = get_submission_object_list(session, s3_bucket, s3_prefix)



        
    else:
        raise ValueError("Exception occurred when validating environment variables") 


if __name__ == "__main__": main()