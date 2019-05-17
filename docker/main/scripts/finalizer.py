import os
import logging
import boto3
import tarfile
import zipfile
import glob
import json
import shutil
import time
import pickle
import subprocess
from pathlib import Path
from botocore.exceptions import ClientError
from subprocess import CalledProcessError


def download_s3_objects_with_prefix(s3_bucket, s3_prefix, dest_path):
    """Downloads all S3 objects from S3 bucket with specified prefix

    :param str s3_bucket: The S3 bucket where the objects will be deleted from
    :param str s3_prefix: The prefix that all the S3_objects must have in order to be
        deleted
    :param str dest_path: The destination folder for downloaded s3 objects
    :raises ClientError: S3 resource exception
    """
    s3 = boto3.resource('s3')
    try:
        bucket = s3.Bucket(s3_bucket)
        objects_to_download = []
        for obj in bucket.objects.filter(Prefix=s3_prefix+'/'):
            with open(dest_path+'/'+obj.key, 'wb') as data:
                bucket.download_fileobj(obj.key, data)

        logging.info("Downloaded all files in %s from s3 bucket %s", s3_prefix, bucket.name)

    except ClientError as e:
        logging.error(e)

def sync_s3_bucket_prefix(s3_bucket, s3_prefix, dest_path):

    try:
        cmd = 'aws s3 sync s3://' + s3_bucket + '/' + s3_prefix + ' ' + dest_path

        logging.info("Syncing S3 bucket %s with prefix %s", s3_bucket, s3_prefix)
        #**********************
        # Requies python 3.7+ *
        #**********************
        output = subprocess.run(cmd, check=True, shell=True)
        logging.info("Succesfully downloaded all files from s3 bucket %s with prefix %s", s3_bucket, s3_prefix)
        
    except CalledProcessError as e:
        logging.error("Error [%s] occured when syncing s3 bucket %s with prefix %s", str(e.returncode), s3_bucket, s3_prefix)

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

def check_valid_extension(s3_submission):
    """Helper function that checks the s3_submission extension is valid before
    downloading archive from S3. Valid submissions can be archived as .tar.gz, 
    .tgz, or .zip. 

    :param str s3_submission: The s3 submission download path
    :returns: True if submission has valid extension, False otherwise
    :rtype: bool
    """
    path = Path(s3_submission)
    file_ext = "".join(path.suffixes)

    valid_ext = [".tar.gz", ".tgz", ".zip"]

    if file_ext in valid_ext:
        return True
    return False


def extract_s3_submission_stem(s3_submission):
    """Helper function to extract s3 and file path information from s3 submission 
    path.

    :param str s3_submission: The s3 object path of the submission
    :returns: 
        - stem - the extracted stem from the s3_submission
    :rtype: str
    """
    path = Path(s3_submission)
    stem = Path(path.with_suffix('')).stem
    return stem


def bucket_exists(s3_bucket):
    """Helper function that will check if a S3 bucket exists

    :param str s3_bucket: The S3 bucket that is being checked
    :returns: True if bucket exists, False otherwise
    :rtype: bool
    :raises ClientError: S3 resource exception
    """
    s3 = boto3.resource('s3')

    try:
        bucket = s3.Bucket(s3_bucket)
        if bucket.creation_date is not None:
            return True
        return False

    except ClientError as e:
        logging.error(e)


def check_for_unprocessed_messages(souce_dir, source_message_list):
    """
    """
    pass

def make_job_results_tarfile(output_filename, source_dir, job_id):
    """
    """
    with tarfile.open(output_filename, "w:gz") as tar:
        tar.add(source_dir, arcname=os.path.basename(source_dir))

def validate_envs(envs):
    """Helper function to validate all of the environment variables exist and are valid before
    processing starts.

    :param dict envs: Dictionary of all environment variables
    :returns: True if all environment variables are valid, False otherwise
    :rtype: bool
    """
    for k, v in envs.items():
        if not is_env_set(k, v):
            return False

    #check if master sleep interval can be converted to int
    try:
        int(envs['MAIN_SLEEP_INTERVAL'])
    except ValueError:
        logging.error("Master sleep interval [%s] must be an integer", envs['MAIN_SLEEP_INTERVAL'])
        return False

    try:
        int(envs['WORKER_INIT_TIMEOUT'])
    except ValueError:
        logging.error("Worker initialization timeout [%s] must be an integer", envs['WORKER_INIT_TIMEOUT'])

    # check the extension of the S3 submission
    if not check_valid_extension(envs['S3_SUBMISSION_ARCHIVE']):
        logging.error("S3 submission %s is not a valid archive type", envs['S3_SUBMISSION_ARCHIVE'])
        return False

    # check that the validation bucket exists
    if not bucket_exists(envs['S3_VALIDATION_BUCKET']):
        logging.error("S3 validation bucket %s does not exist", envs['S3_VALIDATION_BUCKET'])
        return False

    return True

def main():
    
    # read in all evnironment variables into dict
    envs = {}
    envs['S3_SUBMISSION_ARCHIVE'] = os.environ.get('S3_SUBMISSION_ARCHIVE', 'aida-validation/test.tar.gz')
    envs['S3_VALIDATION_BUCKET'] = os.environ.get('S3_VALIDATION_BUCKET', 'aida-validation')
    envs['AWS_BATCH_JOB_ID'] = os.environ.get('AWS_BATCH_JOB_ID', 'c8c90aa7-4f33-4729-9e5c-0068cb9ce75c')
    envs['AWS_BATCH_JOB_NODE_INDEX'] = os.environ.get('AWS_BATCH_JOB_NODE_INDEX', '0')
    envs['MAIN_LOG_LEVEL'] = os.environ.get('MAIN_LOG_LEVEL', 'INFO') # default info logging
    envs['MAIN_SLEEP_INTERVAL'] = os.environ.get('MAIN_SLEEP_INTERVAL', '50')
    envs['WORKER_INIT_TIMEOUT'] = os.environ.get('WORKER_INIT_TIMEOUT', '60')
    envs['AWS_DEFAULT_REGION'] = os.environ.get('AWS_DEFAULT_REGION', 'us-east-1')
    
    # set logging to log to stdout
    logging.basicConfig(level=os.environ.get('LOGLEVEL', envs['MAIN_LOG_LEVEL']))

    # validate enviornment variables
    if validate_envs(envs):

        # TODO this will be uncommented and refined in the finalalization development phase in
        # AIDA-763

        # download all validation files from s3 for the currnet job
        results_path = extract_s3_submission_stem(envs['S3_SUBMISSION_ARCHIVE']) + '-results'
        sync_s3_bucket_prefix(envs['S3_VALIDATION_BUCKET'], envs['AWS_BATCH_JOB_ID'], results_path)
        make_job_results_tarfile(results_path+'.tar.gz', results_path, envs['AWS_BATCH_JOB_ID'])
        #delete_s3_objects_with_prefix(envs['S3_VALIDATION_BUCKET'], envs['AWS_BATCH_JOB_ID'])
        #delete_sqs_queue(queue_url)
    
if __name__ == "__main__": main()