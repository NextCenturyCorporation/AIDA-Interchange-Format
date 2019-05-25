import os
import logging
import boto3
import tarfile
import zipfile
import glob
import json
import shutil
import time
import subprocess
from pathlib import Path
from botocore.exceptions import ClientError
from subprocess import CalledProcessError

class Main:

    # Class Attributes
    UNPROCESSED = 0 
    ERRORS = 0
    TIMEOUT = 0
    VALID = 0
    errors = []

    # Initializer / Instance Attributes
    def __init__(self, envs):
        
        self.submission = envs['S3_SUBMISSION_ARCHIVE']
        self.bucket = envs['S3_VALIDATION_BUCKET']
        self.job_id =  (envs['AWS_BATCH_JOB_ID']).split("#")[0]
        self.node_index = envs['AWS_BATCH_JOB_NODE_INDEX']
        self.worker_init_timeout = envs['WORKER_INIT_TIMEOUT']
        self.aws_region = envs['AWS_DEFAULT_REGION']
        self.aws_sns_topic = envs['AWS_SNS_TOPIC_ARN']
        self.debug = envs['DEBUG']
        self.source_log = 'sourcelog'
        self.session = boto3.session.Session(region_name=self.aws_region)

    def run(self):
        """
        """
        # publish message notification that job has started
        init_msg = "The archive {0} has been submitted for validation with job id {1}." \
            .format(Path(self.submission).name, self.job_id)
        self._publish_sns_message(init_msg)

        # download and extract the submission
        self._download_and_extract_submission_from_s3()

        # create SQS queue and populate
        queue_url = self._create_sqs_queue()
        self._enqueue_files(queue_url)


    def _publish_sns_message(self, message):
        """Function will publish message on to the SNS topic specified by
        the topic arn.

        :param str message: The message to publish
        """
        sns = self.session.client('sns')

        try:
            logging.info("Publishing message [%s] to topic %s", message, self.aws_sns_topic)

            #publish message 
            response = sns.publish(
                    TopicArn=self.aws_sns_topic,
                    Message=message
                )
        except ClientError as e:
            logging.error(e)


    def _publish_failure_message(self, message):
        """
        """
        heading = "The validation job {0} with submission {1} failed with the following" \
             " error: {2}".format(self.job_id, Path(self.submission).name, message)
        self._publish_sns_message(heading)


    def _download_and_extract_submission_from_s3(self):
        """Downloads submission from s3 and extracts contents to the working directory.
        Submissions must be an archive of .zip, .tar.gz, or .tgz.

        :raises ClientError: SQS client exception
        :raises Exception: No turtle (TTL) files extracted from s3 submission
        """
        s3_bucket, s3_object, file_name, file_ext = self._extract_s3_submission_paths()

        # create directory for output
        if not os.path.exists(self.job_id):
            os.makedirs(self.job_id)

        s3_client = self.session.client('s3')

        try:
            logging.info("Downloading %s from bucket %s", s3_object, s3_bucket)
            s3_client.download_file(s3_bucket, s3_object, file_name)
            logging.info("Extracting %s", file_name)

            # extract files
            if file_ext == '.tgz' or file_ext == '.tar.gz':
                # extract the contents of the .tar.gz
                with tarfile.open(file_name) as tar:
                    tar.extractall(self.job_id)
            elif(file_ext == '.zip'):
                zip_ref = zipfile.ZipFile(file_name, 'r')
                zip_ref.extractall(self.job_id)
                zip_ref.close()

            ttls_paths = (glob.glob(self.job_id + '/**/*.ttl', recursive=True))
            ttls = [ Path(x).name for x in ttls_paths ]

            # if no ttl files extracted raise an exception
            if len(ttls) <= 0 :
                err = "No files with .ttl extension found in S3 submission {0}".format(file_name)
                raise ValueError (err)

            # check for duplicates
            if len(ttls) != len(set(ttls)):
                err = "Duplicate files with .ttl extension found in S3 submission {0}".format(file_name)
                raise ValueError(err)
                
        except ClientError as e:
            logging.error(e)
            self._publish_failure_message(e)
            raise
        except ValueError as e:
            logging.error(e)
            self._publish_failure_message(e)
            raise


    def _extract_s3_submission_paths(self):
        """Helper function to extract s3 and file path information from s3 submission 
        path.

        :returns: 
            - s3_bucket - the extracted S3 bucket
            - s3_object - the extracted s3 object
            - file_name - the extracted file name including extension
            - file_ext - the extracted file extension
        :rtype: (str, str, str, str)
        """
        path = Path(self.submission)
        s3_bucket = path.parts[0]          
        s3_object = '/'.join(path.parts[1:])   
        file_name = path.name
        suffixes = path.suffixes
        file_ext = self._get_submission_extension()

        return s3_bucket, s3_object, file_name, file_ext


    def _get_submission_extension(self):
        """Helper function to get the extension of 
        """
        path = Path(self.submission)
        suffixes = path.suffixes
        
        if len(suffixes) > 1 and suffixes[-1] == '.gz':
            file_ext = "".join([suffixes[-2], suffixes[-1]])
        elif len(suffixes) > 1:
            file_ext = suffixes[-1]
        elif len(suffixes) == 1:
            file_ext = suffixes[0]

        return file_ext


    def _create_sqs_queue(self):
        """Creates SQS FIFO queue with specified name.

        :return: The SQS queue url
        :rtype: str
        :raises ClientError: SQS client exception  
        :raises Exception: SQS queue was unable to be created
        """
        sqs_client = self.session.client('sqs')
        queue_name = self.job_id + '.fifo'

        try:
            logging.info("Creating sqs queue %s", queue_name)
            queue = sqs_client.create_queue(
                QueueName = queue_name,
                Attributes =  {
                    'MaximumMessageSize': '262144',
                    'VisibilityTimeout': '3600',
                    'MessageRetentionPeriod':'1209600',
                    'FifoQueue': 'true',
                    'ContentBasedDeduplication': 'true'
                }   
            )

            if not queue['QueueUrl']:
                raise

            return queue['QueueUrl']

        except ClientError as e:
            err = "Unable to create SQS queue with given name due to boto exception: {0}".format(e)
            self._publish_failure_message(err)
            logging.error(err)
            raise
        except Exception as e:
            err = "Unable to create SQS queue with given name {0}".format(queue_name)
            self._publish_failure_message(err)
            logging.error(err)
            raise

    def _enqueue_files(self, queue_url):
        """Uploads all turtle (ttl) files in source directory to the provided S3 bucket,
        adds each S3 object path as a message on SQS and creates / updates the source log on S3. 
        The source log is a serialized list of each S3 object path that will
        be used for processing validation. After all messages have processed and added to 
        the queue, a final source file will be uploaded with a suffix of '.queued' and the old
        source file will be removed from S3.

        :param str queue_url: The SQS queue url
        :raises ClientError: S3 client exception
        """
        s3_client = self.session.client('s3')

        try:
            for filepath in Path(self.job_id).glob('**/*.ttl'):
                s3_object = '/'.join([self.job_id, 'UNPROCESSED', filepath.name])

                logging.info("Uploading %s to bucket %s", s3_object, self.bucket)
                s3_client.upload_file(str(filepath), self.bucket, s3_object)

                # add the mssage to SQS
                response = self._add_sqs_message(queue_url, s3_object)

                # update the source log with the added object path
                if response:
                    with open(self.source_log, 'a+') as f:
                        f.write(s3_object + '\n')

                    # upload file to S3
                    self._upload_file_to_s3_with_prefix(self.source_log)

                else:
                    logging.error("Unable to add %s as SQS message", s3_object)
            
            # append .done to the sourceifles path
            if os.path.exists(self.source_log):
                os.rename(self.source_log, self.source_log +'.queued')

                # upload file to S3
                self._upload_file_to_s3_with_prefix(self.source_log +'.queued')

                # delete the old file
                delete_s3_object(self.bucket, '/'.join([self.job_id, self.source_log]))

            # remove the source directory
            logging.info("Removing source staging directory %s", self.job_id)
            shutil.rmtree(self.job_id)

        except ClientError as e:
            logging.error(e)


    def _add_sqs_message(self, queue_url, s3_object):
        """Adds new message on SQS queue with the S3 object path.

        :param str queue_url: The SQS queue url
        :param str s3_object: Te s
        :param str message_group_id: The message group id for the FIFO queue
        :raises ClientError: SQS client exception
        """
        sqs_client = self.session.client('sqs')

        try:
            msg = sqs_client.send_message(
                QueueUrl=queue_url,
                MessageBody=s3_object,
                MessageGroupId=self.job_id
            )

            if msg['MessageId']:
                logging.info("Added message %s with payload %s to queue %s", msg['MessageId'], s3_object, queue_url)
                return msg['MessageId']
            return None

        except ClientError as e:
            logging.error(e)


    def _upload_file_to_s3_with_prefix(self, filepath):
        """Helper function to upload single file to S3 bucket with specified prefix

        :param str filepath: The local path of the file to be uploaded
        :raises ClientError: S3 client exception
        """
        s3_client = self.session.client('s3')

        try:
            s3_object = '/'.join([self.job_id, Path(filepath).name])

            logging.info("Uploading %s to bucket %s", s3_object, self.bucket)
            s3_client.upload_file(str(filepath), self.bucket, s3_object)

        except ClientError as e:
            logging.error(e)
            raise


def check_valid_extension(s3_submission):
    """Helper function that checks the s3_submission extension is valid before
    downloading archive from S3. Valid submissions can be archived as .tar.gz, 
    .tgz, or .zip. 

    :param str s3_submission: The s3 submission download path
    :returns: True if submission has valid extension, False otherwise
    :rtype: bool
    """
    path = Path(s3_submission)
    suffixes = path.suffixes

    if len(suffixes) > 1 and suffixes[-1] == '.gz':
        file_ext = "".join([suffixes[-2], suffixes[-1]])
    elif len(suffixes) > 1:
        file_ext = suffixes[-1]
    elif len(suffixes) == 1:
        file_ext = suffixes[0]

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




def upload_file_to_s3(s3_bucket, filepath):
    """Helper function to upload single file to S3 bucket

    :param str s3_bucket: Name of the S3 bucket where the file will be uploaded
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


def delete_s3_object(s3_bucket, s3_object):
    """Deletes an S3 object from S3

    :param str s3_object: The S3 object to delete
    :raises ClientError: S3 resource exception
    """
    s3 = boto3.resource('s3')
    try:
        s3.Object(s3_bucket, s3_object).delete()
        logging.info("Deleted %s from s3 bucket %s", s3_object, s3_bucket)

    except ClientError as e:
        logging.error(e)


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

        if len(objects_to_delete) > 0:
            logging.info("Deleting %s from s3 bucket %s", objects_to_delete, bucket.name)
            bucket.delete_objects(
                Delete={
                    'Objects': objects_to_delete
                }
            )
        else:
            logging.info("Nothing to delete from s3 bucket %s", bucket.name)

    except ClientError as e:
        logging.error(e)


def delete_sqs_queue(queue_url):
    """Deletes SQS queue at specified queue url.

    :param str queue_url: The SQS queue url of queue to be deleted
    :raises ClientError: SQS client exception
    """
    sqs_client = boto3.client('sqs')

    try:

        if queue_url is not None:
            logging.info("Deleting sqs queue %s", queue_url)
            queue = sqs_client.delete_queue(
                QueueUrl = queue_url,
            )

    except ClientError as e:
        logging.error(e)


def debug_wait_for_processing(processing_timeout, sleep_interval):
    """This function is used for debugging purposes. This will remove any depencency on 
    AWS batch and allow for jobs to be processe for the specified amount of time set in 
    the [processing_timeout] parameter. Once that amount of time has elapsed this function will
    return true.

    :param int processing_timeout: The amount of time to wait for processing to occur in debug
        mode
    :param int sleep_interval: The amount of time to sleep before checking for timeout
    :returns: True, always
    :rtype: bool
    """
    timeout = time.time() + processing_timeout

    while time.time() < timeout:
        logging.info("Debug mode enabled. Simulating processing for %s seconds. Sleeping for %s seconds", 
            processing_timeout, sleep_interval)
        time.sleep(sleep_interval)

    return True


def wait_for_processing(node_index, job_id, interval, worker_init_timeout):
    """Waits in an indefinite loop while all AWS batch jobs are processed. Function will 
    query AWS batch for all current jobs with the specified job id. If the returned job 
    list has any jobs with the status of RUNNING (other than itself), it will sleep for 
    the specified interval and then execute the check again. 

    :param int node_index: The current AWS batch node index of this process
    :param str job_id: The AWS batch job id
    :param int interval: The interval to sleep before checking job list again 
        in seconds
    :param int worker_init_timeout: The time in seconds to wait for worker jobs to start
    :raises ClientError: AWS batch client exception
    """
    batch_client = boto3.client('batch')
    worker_init = False
    worker_timeout = time.time() + worker_init_timeout

    try:
        while True:

            response = batch_client.list_jobs(
                multiNodeJobId=job_id
            )

            job_list = response['jobSummaryList']
            logging.info("AWS Batch job summary list %s", job_list)
            running_jobs = list(filter(lambda job: job['status'] == 'RUNNING', job_list))


            # check if no jobs are running, throw an error becasue master should still be running
            if len(running_jobs) == 0:
                logging.error("No batch jobs with RUNNING status")
                return False
            # ensure master node is always in RUNNING status if multiple jobs are running
            elif ( len(running_jobs) > 0 and 
                not any(d['jobId'] == ''.join([job_id, '#', str(node_index)]) for d in running_jobs) ):
                logging.error("Main batch job %s no longer has RUNNING status", ''.join([job_id, '#', str(node_index)]))
                return False
            # check if only the master job is running
            elif len(running_jobs) == 1 and running_jobs[0]['jobId'] == ''.join([job_id, '#', str(node_index)]):
                
                # wait for worker jobs to initialize
                if worker_init:
                    logging.info("All worker batch jobs finiscdhed executing")
                    return True
                elif not worker_init and time.time() >= worker_timeout:
                    logging.error("No worker batch jobs started with RUNNING status before timeout of %s seconds", worker_init_timeout)
                    return False
                else:
                    logging.info("Waiting for worker batch jobs to initialize with RUNNING status, sleeping for %s seconds", interval)
                    time.sleep(interval)
            else:
                worker_init = True
                running_job_ids = [d['jobId'] for d in running_jobs]
                logging.info('There are %s batch jobs with RUNNING status %s,' 
                    ' sleeping for %s seconds', len(running_jobs), running_job_ids, str(interval))
                time.sleep(interval)

    except ClientError as e:
        logging.error(e)


def make_job_results_tarfile(output_filename, source_dir):
    """Creates a tar file of the source directory that contains the job results.

    :param str output_filename: The name of the tar file to be created
    :param str source_dir: The directory to be compressed
    """
    with tarfile.open(output_filename, "w:gz") as tar:
        tar.add(source_dir, arcname=os.path.basename(source_dir))


def verify_validation(results_path, source_log_path):
    """Verifies that all files enqueued on SQS were accounted for in the 
    resulting S3 bucket after validation. 

    :retruns: True if all files are account for, False otherwise
    :rtype: bool
    """
    logging.info("Verifying validation result contents with SQS queue")
    source_log = '/'.join([results_path, source_log_path])
    verification_log = Path(source_log_path).stem + '.verification'

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
                verification_log,
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
                verification_log,
                "The following {0} files were missing from validation results: {1}".format( 
                    str(len(missing_objects)), missing_objects
                )
            )
            return False, 
        else:
            logging.info("The %s files placed on SQS were accounted for in validation results",
                    str(len(sqs_objects))
                )

            create_verification_output(
                results_path,
                verification_log,
                "The {0} files placed on SQS were accounted for in validation results".format(
                    str(len(sqs_objects))
                )
            )
            return True, 
    else:
        logging.error("Source log file %s does not exist, unable to verify source files", source_log_path)

        create_verification_output(
            results_path, 
            verification_log,
            "Source log file {0} does not exist, unable to verify source files".format(source_log_path)
        )
        return False, 


def create_verification_output(results_path, source_verfication_path, message):
    """Generates verification file with verification reuslts to be added to final 
    archive.

    :param str results_path: The path where the current validation results are located
    :param str source_verification_path: The path to place this verification output file
    :param str message: The message that will be added to the file with the verification 
        results
    """
    file_path = '/'.join([results_path, source_verfication_path])
    try:
        with open(file_path, "w") as f:
            print(message, file=f)
    except:
        logging.error("Error when writing source verification file %s", file_path)

def sync_s3_bucket_prefix(s3_bucket, s3_prefix, dest_path):
    """Helper function that will sync an s3 bucket to the current working directory.

    :param str s3_bucket: The source bucket to sync
    :param str s3_prefix: The s3 bucket prefix to filter the files to sync
    :param str dest_path: The local destination path where files will be syned to
    """
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

    # check debug mode is a bool
    try:
        bool(envs['DEBUG'])
    except ValueError:
        logging.error('Debug flag [%s] must be a boolean', envs['DEBUG'])

    return True


def read_envs():
    """Function will read in all environment variables into a dictionary

    :returns: Dictionary containing all environment variables or defaults
    :rtype: dict
    """
    envs = {}
    envs['S3_SUBMISSION_ARCHIVE'] = os.environ.get('S3_SUBMISSION_ARCHIVE', 'aida-validation/archives/small_test.zip')
    envs['S3_VALIDATION_BUCKET'] = os.environ.get('S3_VALIDATION_BUCKET', 'aida-validation')
    envs['AWS_BATCH_JOB_ID'] = os.environ.get('AWS_BATCH_JOB_ID', 'c8c90aa7-4f33-4729-9e5c-0068cb9ce75c')
    envs['AWS_BATCH_JOB_NODE_INDEX'] = os.environ.get('AWS_BATCH_JOB_NODE_INDEX', '0')
    envs['MAIN_SLEEP_INTERVAL'] = os.environ.get('MAIN_SLEEP_INTERVAL', '30')
    envs['WORKER_INIT_TIMEOUT'] = os.environ.get('WORKER_INIT_TIMEOUT', '300')
    envs['AWS_DEFAULT_REGION'] = os.environ.get('AWS_DEFAULT_REGION', 'us-east-1')
    envs['AWS_SNS_TOPIC_ARN'] = os.environ.get('AWS_SNS_TOPIC_ARN', 'arn:aws:sns:us-east-1:606941321404:aida-validation')
    envs['AWS_PROFILE'] = os.environ.get('AWS_PROFILE', 'default')
    envs['DEBUG'] = os.environ.get('DEBUG', 'True')
    return envs

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

    #check if master sleep interval can be converted to int
    try:
        int(envs['MAIN_SLEEP_INTERVAL'])
    except InitilizationError:
        logging.error("Master sleep interval [%s] must be an integer", envs['MAIN_SLEEP_INTERVAL'])
        return False

    try:
        int(envs['WORKER_INIT_TIMEOUT'])
    except ValueError:
        logging.error("Worker initialization timeout [%s] must be an integer", envs['WORKER_INIT_TIMEOUT'])
        return False

    # check the extension of the S3 submission
    if not check_valid_extension(envs['S3_SUBMISSION_ARCHIVE']):
        logging.error("S3 submission %s is not a valid archive type", envs['S3_SUBMISSION_ARCHIVE'])
        return False

    # check that the validation bucket exists
    if not bucket_exists(envs['S3_VALIDATION_BUCKET']):
        logging.error("S3 validation bucket %s does not exist", envs['S3_VALIDATION_BUCKET'])
        return False

    # check debug mode is a bool
    try:
        bool(envs['DEBUG'])
    except ValueError:
        logging.error("Debug flag [%s] must be a boolean", envs['DEBUG'])
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

class EnvironmentVariableValidationError(Exception):
    pass

def main():

    # validate environment variables
    envs = read_envs()
    
    # set logging to log to stdout
    logging.basicConfig(level=os.environ.get('LOGLEVEL', 'INFO'))

    if validate_envs(envs):

        main = Main(envs)
        main.run()

    else:
        raise EnvironmentVariableValidationError("Exception occured when validating environment variables") 



        # #create SQS queue
        # queue_url = create_sqs_queue(envs['AWS_BATCH_JOB_ID'])

        # if queue_url:

        #     # queue all the files
        #     enqueue_files(queue_url, envs['AWS_BATCH_JOB_ID'], envs['S3_VALIDATION_BUCKET'], 'sourcefiles')

        #     # check for debug mode
        #     if bool(envs['DEBUG']):
        #         debug_wait_for_processing(500, 30)
        #     else:
        #         # wait for all AWS batch jobs to complete processing
        #         wait_for_processing(
        #             envs['AWS_BATCH_JOB_NODE_INDEX'], 
        #             envs['AWS_BATCH_JOB_ID'], 
        #             int(envs['MAIN_SLEEP_INTERVAL']),
        #             int(envs['WORKER_INIT_TIMEOUT']))

        # # download all validation files from s3 for the currnet job
        # results_path = extract_s3_submission_stem(envs['S3_SUBMISSION_ARCHIVE']) + '-results'
        # sync_s3_bucket_prefix(envs['S3_VALIDATION_BUCKET'], envs['AWS_BATCH_JOB_ID'], results_path)

        # # validate processed files
        # verify_validation(results_path, 'sourcefiles.queued')
        
        # # tar results and upload to validation bucket
        # results_tar = results_path+'.tar.gz'
        # make_job_results_tarfile(results_tar, results_path)
        # upload_file_to_s3(envs['S3_VALIDATION_BUCKET'], results_tar)

        # compelte_msg = "The validation of {0} with job id {1} has completed. Results can be found" \
        #     " in the {2} S3 bucket".format(
        #         Path(envs['S3_SUBMISSION_ARCHIVE']).name, 
        #         envs['AWS_BATCH_JOB_ID'], 
        #         envs['S3_VALIDATION_BUCKET']
        #     )

        # publish_sns_message(envs['AWS_SNS_TOPIC_ARN'], compelte_msg)

        # # clean up sqs queue and s3 validation staging data
        # delete_s3_objects_with_prefix(envs['S3_VALIDATION_BUCKET'], envs['AWS_BATCH_JOB_ID'])
        # delete_sqs_queue(queue_url)
    
if __name__ == "__main__": main()
