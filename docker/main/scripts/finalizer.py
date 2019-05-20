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

def delete_s3_objects_with_prefix(s3_bucket, s3_prefix):
    """Deletes all S3 objects from S3 bucket with specified prefix

    :param str s3_bucket: The S3 bucket where the objects will be deleted from
    :param str s3_prefix: The prefix that all the S3_objects must have in order to be
        deleted
    :raises ClientError: S3 resource exception
    """
    s3 = boto3.resource('s3')
    try:
        bucket = s3.Bucket(s3_bucket)
        objects_to_delete = []
        for obj in bucket.objects.filter(Prefix=s3_prefix+'/'):
            objects_to_delete.append({'Key': obj.key})

        logging.info("Deleting %s from s3 bucket %s", objects_to_delete, bucket.name)
        bucket.delete_objects(
            Delete={
                'Objects': objects_to_delete
            }
        )

    except ClientError as e:
        logging.error(e)


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

def delete_sqs_queue(queue_url):
    """Deletes SQS queue at specified queue url.

    :param str queue_url: The SQS queue url of queue to be deleted
    :raises ClientError: SQS client exception
    """
    sqs_client = boto3.client('sqs')

    try:
        logging.info("deleting sqs queue %s", queue_url)
        queue = sqs_client.delete_queue(
            QueueUrl = queue_url,
        )

    except ClientError as e:
        logging.error(e)
    except Exception as e:
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


def upload_file_to_s3(s3_bucket, filepath):
    """Helper function to upload single file to S3 bucket with specified prefix
    TODO update this in main method

    :param str s3_bucket: Name of the S3 bucket where the file will be uploaded
    :param str s3_prefix: The prefix to prepend to the filename
    :param str filepath: The local path of the file to be uploaded
    :raises ClientError: S3 client exception
    """
    s3_client = boto3.client('s3')

    try:
        s3_object = Path(filepath).name

        logging.info("Uploading %s to bucket %s", s3_object, s3_bucket)
        s3_client.upload_file(str(filepath), s3_bucket, s3_object)

    except ClientError as e:
        logging.error(e)


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


def make_job_results_tarfile(output_filename, source_dir, job_id):
    """
    """
    with tarfile.open(output_filename, "w:gz") as tar:
        tar.add(source_dir, arcname=os.path.basename(source_dir))


def verify_validation(results_path, source_log_path):
    """
    :retruns: True if all files are account for, False otherwise
    :rtype: bool
    """
    logging.info("Verifying validation result contents with SQS queue")
    source_log = '/'.join([results_path, source_log_path])
    if os.path.exists(source_log):

        # read in source log files
        sqs_objects = []
        try: 
            with open(source_log) as file:
                sqs_objects = [Path(line.strip()).name for line in file]
        except :
            logging.error("Exception occured when reading %s during verification of validation", source_log_path)

            create_verification_output(
                results_path,
                Path(source_log_path).stem + '.failed',
                "Exception occured when reading {0} during verification of validation".format(source_log_path)
            )
            return False

        # get all ttl files that have been processed
        processed_objects = [] 
        for filepath in Path(results_path).glob('**/*.ttl'):
            processed_objects.append(filepath.name)

        # find any missing files that should exist in S3 bucket
        missing_objects = set(sqs_objects) - set(processed_objects)

        if len(missing_objects) > 0:
            logging.error("The following %s files were missing from validation results: %s",
                    str(len(missing_objects)), missing_objects
                )

            create_verification_output(
                results_path,
                Path(source_log_path).stem + '.failed',
                "The following {0} files were missing from validation results: {1}".format( 
                    str(len(missing_objects)), missing_objects
                )
            )
            return False, 
        else:
            logging.info("All %s files placed on SQS accounted for in validation results",
                    str(len(sqs_objects))
                )

            create_verification_output(
                results_path,
                Path(source_log_path).stem + '.verified',
                "All {0} files placed on SQS accounted for in validation results".format(
                    str(len(sqs_objects))
                )
            )
            return True, 
    else:
        logging.error("Source log file %s does not exist, unable to verify source files", source_log_path)

        create_verification_output(
            results_path, 
            Path(source_log_path).stem + '.failed', 
            "Source log file {0} does not exist, unable to verify source files".format(source_log_path)
        )
        return False, 


def create_verification_output(results_path, source_verfication_path, message):
    """
    """
    file_path = '/'.join([results_path, source_verfication_path])
    try:
        with open(file_path, "w") as f:
            print(message, file=f)
    except:
        logging.error("Error when writing source verification file %s", file_path)

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

        # validate processed files
        verify_validation(results_path, 'sourcefiles.done')
        
        # tar results and upload to validation bucket
        make_job_results_tarfile(results_path+'.tar.gz', results_path, envs['AWS_BATCH_JOB_ID'])
        upload_file_to_s3(envs['S3_VALIDATION_BUCKET'], results_tar)
        delete_s3_objects_with_prefix(envs['S3_VALIDATION_BUCKET'], envs['AWS_BATCH_JOB_ID'])
        delete_sqs_queue(queue_url)
    
if __name__ == "__main__": main()