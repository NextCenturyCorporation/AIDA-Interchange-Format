import os
import logging
import boto3
import tarfile
import zipfile
import uuid
import glob
import json
import shutil
import time
from pathlib import Path
from botocore.exceptions import ClientError


def download_and_extract_submission_from_S3(s3_submission, dirpath):
    """ Downloads submission from s3 and extracts its contents to the working directory.
    Submssions must be an archive of .zip, .tar.gz, or .tgz.

    //TODO document this better 
    :param boto s3_client
    :param str s3_submission: The bucket path where the submission lives on s3
    :param str dirpath
    """
    s3_bucket, s3_object, file_name, file_ext = extract_s3_submission_paths(s3_submission)

    # create directory for output
    if not os.path.exists(dirpath):
        os.makedirs(dirpath)

    s3_client = boto3.client('s3')

    try:
        logging.info("downloading %s from bucket %s", s3_object, s3_bucket)
        s3_client.download_file(s3_bucket, s3_object, file_name)

        logging.info("extracting %s", file_name)
        # extract if it is a tar gz
        if(file_ext == '.tgz' or file_ext == '.tar.gz'):
            # extract the contents of the .tar.gz
            with tarfile.open(file_name) as tar:
                # add output directory
                tar.extractall(dirpath)

        # extract if it is a zip
        if(file_ext == '.zip'):
            zip_ref = zipfile.ZipFile(file_name, 'r')
            #add output directory
            zip_ref.extractall(dirpath)
            zip_ref.close()

        files = os.listdir(dirpath)

        # if no ttl files extracted raise an exception
        if ( len(glob.glob(dirpath + '/**/*.ttl', recursive=True)) <= 0 ):
            raise Exception ("No ttl files extracted to directory {0} for S3 submission {1}"
                .format(dirpath, file_name))
            
    except ClientError as e:
        logging.error(e)
    except Exception as e:
        logging.error(e)

def check_valid_extension(s3_submission):
    """Helper function to check if the s3_submission has valid extension before
    downloading archive from S3. Valid submissions can archived as .tar.gz, .tgz, 
    or .zip. 

    :param str s3_submission: The s3 submission download path
    :returns: True if submission has valid extension, False otherwise
    :rtype: bool
    """
    path = Path(s3_submission)
    file_ext = "".join(path.suffixes)

    valid_ext = [".tar.gz", ".tgz", ".zip"]

    if(file_ext in valid_ext):
        return True
    return False

def extract_s3_submission_paths(s3_submission):
    """ Helper method to extract the s3 bucket and s3 object and output directory
    from s3 submission path

    //TODO Document

    :returns: tuple(s3_bucket, )
    """
    path = Path(s3_submission)
    s3_bucket = path.parts[0]          
    s3_object = '/'.join(path.parts[1:])   
    file_name = path.name
    file_ext = "".join(path.suffixes)

    return s3_bucket, s3_object, file_name, file_ext


def upload_submission_files_to_s3(s3_bucket_name, dirpath):
    """ Create output directories for a validation in s3

    //TODO document this better

    :param boto s3_client
    :param str bucket_name: Unique string name of the bucket where the 
        directories will be created
    :returns: The list of s3 file paths for all the files that were uploaded
    :rtype: list
    """
    
    sqs_list = []
    s3_client = boto3.client('s3')

    try:
        for filepath in Path(dirpath).glob('**/*.ttl'):
            s3_object = '/'.join([dirpath, 'unprocessed', filepath.name])

            logging.info("uploading %s to bucket %s", s3_object, s3_bucket_name)
            s3_client.upload_file(str(filepath), s3_bucket_name, s3_object)

            #add the file to the SQS path list
            sqs_list.append(s3_object)

        return sqs_list

    except ClientError as e:
        logging.error(e)

def delete_s3_objects(s3_bucket, s3_prefix):
    """
    //TODO document
    """
    s3 = boto3.resource('s3')
    try:
        bucket = s3.Bucket(s3_bucket)
        objects_to_delete = []
        for obj in bucket.objects.filter(Prefix=s3_prefix+'/'):
            objects_to_delete.append({'Key': obj.key})

        logging.info("deleting %s from s3 bucket %s", objects_to_delete, bucket.name)
        bucket.delete_objects(
            Delete={
                'Objects': objects_to_delete
            }
        )
    except ClientError as e:
        logging.error(e)


def create_sqs_queue(queue_name, queue_attrs):
    """ Creates an SQS queue with the given [queue_name] and attributes 
    [queue_attrs]. 

    //TODO document this better

    :parma sqs_client
    :param str queue_name: The unique name of the queue to be created
    :param dict queue_attrs: The attributes for the queue
    :return: The SQS queue that was created
    :rtype:
    :raises ClientError: SQS Client exception  
    :raises: SQS queue was unable to be created
    """
    sqs_client = boto3.client('sqs')

    try:
        logging.info("creating sqs queue %s", queue_name)
        queue = sqs_client.create_queue(
            QueueName = queue_name,
            Attributes = queue_attrs
        )
        return queue['QueueUrl']
        raise
    except ClientError as e:
        logging.error(e)
    except:
        logging.error("Unable to create SQS queue with given name %s", queue_name)


def populate_sqs_queue(queue_list, queue_url):
    """
    //TODO document
    """
    sqs_client = boto3.client('sqs')

    try:
        for s3_object in queue_list:
            sqs_client.send_message(
                QueueUrl=queue_url,
                MessageAttributes={
                    'S3Object': {
                        'DataType': 'String',
                        'StringValue': s3_object
                    } 
                },
                MessageBody=('S3 Object {0} to be validated'.format(s3_object))
            )
            logging.info("added file %s to queue %s", s3_object, queue_url)
    except ClientError as e:
        logging.error(e)


def delete_sqs_queue(queue_url):
    """
    """
    sqs_client = boto3.client('sqs')

    try:
        logging.info("deleting sqs queue %s", queue_url)
        queue = sqs_client.delete_queue(
            QueueUrl = queue_url,
        )
    except ClientError as e:
        logging.error(e)

def wait_for_processing(node_index, job_id, sleep_interval):
    """
    """
    batch_client = boto3.client('batch')

    try:
        response = batch_client.list_jobs(
            jobQueue=job_id
        )

        job_list = response["jobSummaryList"]
        #job_list = [{"status": "RUNNING","container": {},"jobName": "boto3-test","nodeProperties": {"nodeIndex": 0,"isMainNode": True},"startedAt": 1556308664720,"jobId": "23bd1bdf-54bb-4431-9413-c31dd6dd73d7#0","createdAt": 1556308598877}]
        running_jobs = list(filter(lambda job: job['status'] == 'RUNNING', job_list))
        
        # check if no jobs are running, throw an error becasue master should still be running
        if(len(running_jobs) == 0 ):
            logging.error("no more batch jobs with RUNNING status, but master is still running")
        # check if one job is running and if it is the master job node
        elif(len(running_jobs) == 1 and running_jobs[0]['jobId'] == job_id + '#' + str(node_index)):
            logging.info("there are no more batch jobs currently running")
        # check if more than on job is still running, sleep
        else:
            running_job_ids = [d['jobId'] for d in running_jobs]
            logging.info("%s batch jobs still running %s", len(running_jobs), running_job_ids)
            time.sleep(sleep_interval)
            wait_for_processing(check_interval, job_id)

    except ClientError as e:
        logging.error(e)


def main():
    
    # get enviornment variables
    LOG_LEVEL = 'INFO'
    S3_SUBMISSION_ARCHIVE = 'aida-validation/test.tar.gz'
    S3_STAGING_BUCKET = 'aida-validation'
    AWS_BATCH_JOB_ID = str(uuid.uuid4())
    AWS_BATCH_JOB_NODE_INDEX = 0
    CHECK_INTERVAL = 5

    # set logging to log to stdout
    logging.basicConfig(level=os.environ.get('LOGLEVEL', LOG_LEVEL))

    # verify the submission has a valid extension
    if(check_valid_extension(S3_SUBMISSION_ARCHIVE)):

        # create s3 connection
        s3_client = boto3.client('s3')
        download_and_extract_submission_from_S3(S3_SUBMISSION_ARCHIVE, AWS_BATCH_JOB_ID)

        # create sqs connection
        sqs_client = boto3.resource('sqs')
        queue_list = upload_submission_files_to_s3(S3_STAGING_BUCKET, AWS_BATCH_JOB_ID)
        logging.info("queue list %s",  ' '.join([str(s) for s in queue_list]))

        queue_attrs =  {
                    'MaximumMessageSize': '262144',
                    'VisibilityTimeout': '3600',
                    'MessageRetentionPeriod':'3600'
        }

        #create queues and populate queue to be processed
        queue_url = create_sqs_queue(AWS_BATCH_JOB_ID, queue_attrs)
        populate_sqs_queue(queue_list, queue_url)

        wait_for_processing(AWS_BATCH_JOB_NODE_INDEX, AWS_BATCH_JOB_ID, 5)

        # clean up
        delete_s3_objects(S3_STAGING_BUCKET, AWS_BATCH_JOB_ID)
        delete_sqs_queue(queue_url)
    
        # delete local directory remove this before final and remove import
        shutil.rmtree(AWS_BATCH_JOB_ID)

    else:
        logging.error("s3 submission %s is not a valid archive type", S3_SUBMISSION_ARCHIVE)

if __name__ == "__main__": main()
