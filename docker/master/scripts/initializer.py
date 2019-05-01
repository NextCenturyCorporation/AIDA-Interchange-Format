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
import pickle
from pathlib import Path
from botocore.exceptions import ClientError


def download_and_extract_submission_from_S3(s3_submission, dirpath):
    """ Downloads submission from s3 and extracts its contents to the working directory.
    Submssions must be an archive of .zip, .tar.gz, or .tgz.

    :param str s3_submission: The s3 object path for the submission
    :param str dirpath: The local directory path to place extracted s3_submission files
    :raises ClientError: SQS client exception
    :raises Exception: No turtle (TTL) files extracted from s3 submission
    """
    s3_bucket, s3_object, file_name, file_ext = extract_s3_submission_paths(s3_submission)

    # create directory for output
    if not os.path.exists(dirpath):
        os.makedirs(dirpath)

    s3_client = boto3.client('s3')

    try:
        logging.info("Downloading %s from bucket %s", s3_object, s3_bucket)
        s3_client.download_file(s3_bucket, s3_object, file_name)
        logging.info("Extracting %s", file_name)

        # extract files
        if file_ext == '.tgz' or file_ext == '.tar.gz':
            # extract the contents of the .tar.gz
            with tarfile.open(file_name) as tar:
                tar.extractall(dirpath)
        elif(file_ext == '.zip'):
            zip_ref = zipfile.ZipFile(file_name, 'r')
            zip_ref.extractall(dirpath)
            zip_ref.close()

        # if no ttl files extracted raise an exception
        if len(glob.glob(dirpath + '/**/*.ttl', recursive=True)) <= 0 :
            raise Exception ("No ttl files extracted to directory {0} for S3 submission {1}"
                .format(dirpath, file_name))
            
    except ClientError as e:
        logging.error(e)
    except Exception as e:
        logging.error(e)


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


def extract_s3_submission_paths(s3_submission):
    """ Helper method to extract s3 and file path information from s3 submission 
    path.

    :param str s3_submission: The s3 object path of the submission
    :returns: 
        - s3_bucket - the extracted S3 bucket
        - s3_object - the extracted s3 object
        - file_name - the extracted file name including extension
        - file_ext - the extracted file extension
    :rtype: (str, str, str, str)
    """
    path = Path(s3_submission)
    s3_bucket = path.parts[0]          
    s3_object = '/'.join(path.parts[1:])   
    file_name = path.name
    file_ext = "".join(path.suffixes)

    return s3_bucket, s3_object, file_name, file_ext


def upload_submission_files_to_s3(s3_bucket_name, dirpath):
    """Uploads all turtle (ttl) files in directory to the provided S3 bucket.

    :param str s3_bucket_name: Unique string name of the bucket where the 
        directories will be created
    :param str dirpath: The local directory that contains turtle (ttl) files
    :returns: The list of s3 file paths for all the files that were uploaded
    :rtype: list
    :raises ClientError: S3 client exception
    """
    
    sqs_list = []
    s3_client = boto3.client('s3')

    try:
        for filepath in Path(dirpath).glob('**/*.ttl'):
            s3_object = '/'.join([dirpath, 'unprocessed', filepath.name])

            logging.info("Uploading %s to bucket %s", s3_object, s3_bucket_name)
            s3_client.upload_file(str(filepath), s3_bucket_name, s3_object)

            #add the file to the SQS path list
            sqs_list.append(s3_object)

        return sqs_list

    except ClientError as e:
        logging.error(e)


def upload_file_to_s3(s3_bucket_name, s3_prefix, filepath):
    """ Helper method to upload single file to S3 bucket with specified prefix

    :param str s3_bucket_name: Name of the S3 bucket where the file will be uploaded
    :param str s3_prefix: The prefix to prepend to the filename
    :param str filepath: The local path of the file to be uploaded
    :raises ClientError: S3 client exception
    """
    s3_client = boto3.client('s3')

    try:
        s3_object = '/'.join([s3_prefix, Path(filepath).name])

        logging.info("Uploading %s to bucket %s", s3_object, s3_bucket_name)
        s3_client.upload_file(str(filepath), s3_bucket_name, s3_object)

    except ClientError as e:
        logging.error(e)

def bucket_exists(s3_bucket_name):
    """Helper method that will check if a S3 bucket exists

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


def delete_s3_objects(s3_bucket, s3_prefix):
    """Deletes all S3 objects from S3 bucket with specified prefix

    :param str s3_bucket:
    :param str s3_prefix: 
    :raises ClientError: S3 resrouce exception
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


def create_sqs_queue(queue_name):
    """ Creates SQS FIFO queue with specified name.

    :param str queue_name: The unique name of the queue to be created
    :return: The SQS queue url
    :rtype: str
    :raises ClientError: SQS client exception  
    :raises Exception: SQS queue was unable to be created
    """
    sqs_client = boto3.client('sqs')
    queue_name += '.fifo'

    try:
        logging.info("creating sqs queue %s", queue_name)
        queue = sqs_client.create_queue(
            QueueName = queue_name,
            Attributes =  {
                'MaximumMessageSize': '262144',
                'VisibilityTimeout': '3600',
                'MessageRetentionPeriod':'3600',
                'FifoQueue': 'true',
                'ContentBasedDeduplication': 'true'
            }   
        )
        return queue['QueueUrl']
        raise
    except ClientError as e:
        logging.error(e)
    except:
        logging.error("Unable to create SQS queue with given name %s", queue_name)


def populate_sqs_queue(queue_list, queue_url, message_group_id, sourcefiles_path):
    """Iterates over s3 object path list and populates SQS queue S3Object messages. A 
    serialized file will be generated with all of the S3 objects that were added to SQS.

    :param list queue_list: List of S3 object paths
    :param str queue_url: The SQS queue url
    :param str message_group_id: The message group id for the FIFO queue
    :param str sourcefiles_path: The path of the serialized file to be created with added
        S3 objects
    :raises ClientError: SQS client exception
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
                MessageBody=('S3 Object {0} to be validated'.format(s3_object)),
                MessageGroupId=message_group_id
            )
            logging.info("Added file %s to queue %s", s3_object, queue_url)

        # serialize sqs messages to local sourcefiles
        logging.info("Writing queue list to %s", sourcefiles_path)
        with open(sourcefiles_path, 'wb') as f:
            pickle.dump(queue_list, f)

    except ClientError as e:
        logging.error(e)


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


def wait_for_processing(node_index, job_id, interval):
    """Waits in an indefinate loop while all AWS batch jobs are processed. Function will 
    query AWS batch for all current jobs with the specified job id. If the returned job 
    list has any jobs with the status of RUNNING (other than itself), it will sleep for 
    the specified interval and then execute the check again. 

    :param int node_index: The current AWS batch node index of this process
    :param str job_id: The AWS batch job id
    :param int interval: The interval to sleep before checking job list again 
        in seconds
    :raises ClientError: AWS batch client exception
    """
    batch_client = boto3.client('batch')

    try:
        response = batch_client.list_jobs(
            multiNodeJobId=job_id
        )

        job_list = response['jobSummaryList']
        logging.info("AWS Batch job summary list %s", job_list)
        running_jobs = list(filter(lambda job: job['status'] == 'RUNNING', job_list))

        while True:
            # check if no jobs are running, throw an error becasue master should still be running
            if len(running_jobs) == 0:
                logging.error("No batch jobs with RUNNING status")
                return False
            # ensure master node is always in RUNNING status if multiple jobs are running
            elif ( len(running_jobs) > 0 and 
                not any(d['jobId'] == ''.join([job_id, '#', str(node_index)]) for d in running_jobs) ):
                logging.error("Master batch job %s no longer has RUNNING status", ''.join([job_id, '#', str(node_index)]))
                return False
            # check if only the master job is running
            elif len(running_jobs) == 1 and running_jobs[0]['jobId'] == ''.join([job_id, '#', str(node_index)]):
                logging.info("No slave batch jobs with RUNNING status")
                return True
            else:
                running_job_ids = [d['jobId'] for d in running_jobs]
                logging.info('There are %s batch jobs with RUNNING status %s,' 
                    ' going back to sleep for %s seconds', len(running_jobs), running_job_ids, str(interval))
                time.sleep(interval)

    except ClientError as e:
        logging.error(e)


def is_env_set(env, value):
    """ Helper method to check if a specific enviornment variable is not None

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
    """ Helper method to validate all of the enviroment variables exist and are valid before
    processing starts.

    :param dict envs: Dictionary of all environment varaibles
    :returns: True if all environment variables are valid, False otherwise
    :rtype: bool
    """
    for k, v in envs.items():
        if not is_env_set(k, v):
            return False

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
    envs['S3_SUBMISSION_ARCHIVE'] = os.environ['S3_SUBMISSION_ARCHIVE']
    envs['S3_VALIDATION_BUCKET'] = os.environ['S3_VALIDATION_BUCKET']
    envs['AWS_BATCH_JOB_ID'] = os.environ['AWS_BATCH_JOB_ID']
    envs['AWS_BATCH_JOB_NODE_INDEX'] = os.environ['AWS_BATCH_JOB_NODE_INDEX']
    envs['MASTER_LOG_LEVEL'] = os.environ['MASTER_LOG_LEVEL']
    envs['MASTER_SLEEP_INTERVAL'] = int(os.environ['MASTER_SLEEP_INTERVAL'])
    
    # set logging to log to stdout
    logging.basicConfig(level=os.environ.get('LOGLEVEL', envs['MASTER_LOG_LEVEL']))

    # verify enviorment variables
    if validate_envs(envs):

        # create s3 connection
        s3_client = boto3.client('s3')
        download_and_extract_submission_from_S3(envs['S3_SUBMISSION_ARCHIVE'], envs['AWS_BATCH_JOB_ID'])

        # create sqs connection
        sqs_client = boto3.resource('sqs')
        queue_list = upload_submission_files_to_s3(envs['S3_VALIDATION_BUCKET'], envs['AWS_BATCH_JOB_ID'])
     
        #create queues and populate queue to be processed
        queue_url = create_sqs_queue(envs['AWS_BATCH_JOB_ID'])
        populate_sqs_queue(queue_list, queue_url, envs['AWS_BATCH_JOB_ID'], 'sourcefiles')
        upload_file_to_s3(envs['S3_VALIDATION_BUCKET'], 
            '/'.join([envs['AWS_BATCH_JOB_ID'], 'output', 'log']), 'sourcefiles')

        # wait for all AWS batch jobs to complete processing
        wait_for_processing(envs['AWS_BATCH_JOB_NODE_INDEX'], envs['AWS_BATCH_JOB_ID'], envs['MASTER_SLEEP_INTERVAL'])

        # clean up
        delete_s3_objects(envs['S3_VALIDATION_BUCKET'], envs['AWS_BATCH_JOB_ID'])
        delete_sqs_queue(queue_url)
    
if __name__ == "__main__": main()
