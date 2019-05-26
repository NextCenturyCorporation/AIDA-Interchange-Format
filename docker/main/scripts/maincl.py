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

    # Initializer / Instance Attributes
    def __init__(self, envs):
        
        self.submission = envs['S3_SUBMISSION_ARCHIVE']
        self.bucket = envs['S3_VALIDATION_BUCKET']
        self.job_id =  (envs['AWS_BATCH_JOB_ID']).split("#")[0]
        self.node_index = envs['AWS_BATCH_JOB_NODE_INDEX']
        self.sleep_interval = int(envs['MAIN_SLEEP_INTERVAL'])
        self.worker_init_timeout = int(envs['WORKER_INIT_TIMEOUT'])
        self.aws_region = envs['AWS_DEFAULT_REGION']
        self.aws_sns_topic = envs['AWS_SNS_TOPIC_ARN']
        self.debug = bool(envs['DEBUG'])
        self.debug_timeout = int(envs['DEBUG_TIMEOUT'])
        self.debug_sleep_interval = int(envs['DEBUG_SLEEP_INTERVAL'])
        self.source_log = 'sourcelog'
        self.session = boto3.session.Session(region_name=self.aws_region)
        self.extracted = 0
        self.verification = None

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

        # check for debug mode
        if self.debug:
            self._debug_wait_for_processing()
        else:
            # wait for all AWS batch jobs to complete processing
            self._wait_for_processing()

        # download all validation files from s3 for the currnet job
        results_path = self._get_submission_stem() + '-results'
        results_tar = results_path + '.tar.gz'
        self._sync_s3_bucket(results_path)

        # validate processed files
        self._verify_validation(results_path)

        # generate validation metrics

        # create results and upload to validation bucket
        self._create_results_tarfile(results_tar, results_path)
        self._upload_file_to_s3(results_tar)

        compelte_msg = "The validation of {0} with job id {1} has completed. Results can be found" \
            " in the {2} S3 bucket".format(
                Path(self.submission).name, 
                self.job_id, 
                self.bucket
            )

        self._publish_sns_message(compelte_msg)

        # clean up sqs queue and s3 validation staging data
        self._delete_s3_objects_with_prefix(self.job_id)
        self._delete_sqs_queue(queue_url)


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
        """Helper function that will publish failure message to SNS if an unrecoverable error
        occurs before processing begins.

        param: str message: The failure message to publish
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
        s3_bucket, s3_object, file_name, file_ext = self._get_submission_paths()

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
                raise ValueError(err)

            # check for duplicates
            if len(ttls) != len(set(ttls)):
                err = "Duplicate files with .ttl extension found in S3 submission {0}".format(file_name)
                raise ValueError(err)

            # save the number of files extracted
            self.extracted = len(ttls)
                
        except ClientError as e:
            logging.error(e)
            self._publish_failure_message(e)
            raise
        except ValueError as e:
            logging.error(e)
            self._publish_failure_message(e)
            raise


    def _get_submission_paths(self):
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
                raise ValueError("Unable to create SQS queue with given name {0}, QueueUrl is empty".format(queue_name))

            return queue['QueueUrl']

        except ClientError as e:
            err = "Unable to create SQS queue with given name due to boto exception: {0}".format(e)
            self._publish_failure_message(err)
            logging.error(err)
            raise
        except ValueErrors as e:
            self._publish_failure_message(e)
            logging.error(e)
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
                    self._upload_file_to_s3(self.source_log, self.job_id)

                else:
                    logging.error("Unable to add %s as SQS message", s3_object)
            
            # append .done to the sourceifles path
            if os.path.exists(self.source_log):
                os.rename(self.source_log, self.source_log +'.queued')

                # upload file to S3
                self._upload_file_to_s3(self.source_log +'.queued', self.job_id)

                # delete the old file
                self._delete_s3_object('/'.join([self.job_id, self.source_log]))

            # remove the source directory
            logging.info("Removing source staging directory %s", self.job_id)
            shutil.rmtree(self.job_id)

        except ClientError as e:
            logging.error(e)


    def _add_sqs_message(self, queue_url, s3_object):
        """Adds new message on SQS queue with the S3 object path.

        :param str queue_url: The SQS queue url
        :param str s3_object: The s3 object path to add to SQS
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


    def _upload_file_to_s3(self, filepath, prefix=None):
        """Helper function to upload single file to S3 bucket with specified prefix

        :param str filepath: The local path of the file to be uploaded
        :param str prefix: The prefix to be added to the file name
        :raises ClientError: S3 client exception
        """
        s3_client = self.session.client('s3')

        try:
            if prefix is not None:
                s3_object = '/'.join([prefix, Path(filepath).name])
            else:
                s3_object = Path(filepath).name

            logging.info("Uploading %s to bucket %s", s3_object, self.bucket)
            s3_client.upload_file(str(filepath), self.bucket, s3_object)

        except ClientError as e:
            logging.error(e)


    def _delete_s3_object(self, s3_object):
        """Deletes an S3 object from validation bucket.

        :param str s3_object: The S3 object to delete
        :raises ClientError: S3 resource exception
        """
        s3 = self.session.resource('s3')
        try:
            s3.Object(self.bucket, s3_object).delete()
            logging.info("Deleted %s from s3 bucket %s", s3_object, self.bucket)

        except ClientError as e:
            logging.error(e)


    def _debug_wait_for_processing(self):
        """This function is used for debugging purposes. This will remove any depencency on 
        AWS batch and allow for jobs to be processe for the specified amount of time set in 
        the [processing_timeout] parameter. Once that amount of time has elapsed this function will
        return true.

        :returns: True, always
        :rtype: bool
        """
        timeout = time.time() + self.debug_timeout

        while time.time() < timeout:
            logging.info("Debug mode enabled. Simulating processing for %s seconds. Sleeping for %s seconds", 
                self.debug_timeout, self.debug_sleep_interval)
            time.sleep(self.debug_sleep_interval)
        return True


    def _wait_for_processing(self):
        """Waits in an indefinite loop while all AWS batch jobs are processed. Function will 
        query AWS batch for all current jobs with the specified job id. If the returned job 
        list has any jobs with the status of RUNNING (other than itself), it will sleep for 
        the specified interval and then execute the check again. 

        :raises ClientError: AWS batch client exception
        """
        batch_client = self.session.client('batch')
        worker_init = False
        worker_timeout = time.time() + self.worker_init_timeout
        try:
            while True:

                response = batch_client.list_jobs(
                    multiNodeJobId=self.job_id
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
                    not any(d['jobId'] == ''.join([self.job_id, '#', str(self.node_index)]) for d in running_jobs) ):
                    logging.error("Main batch job %s no longer has RUNNING status", 
                        ''.join([self.job_id, '#', str(self.node_index)]))
                    return False
                # check if only the master job is running
                elif len(running_jobs) == 1 and running_jobs[0]['jobId'] == ''.join([self.job_id, '#', str(self.node_index)]):
                    
                    # wait for worker jobs to initialize
                    if worker_init:
                        logging.info("All worker batch jobs finished executing")
                        return True
                    elif not worker_init and time.time() >= self.worker_timeout:
                        logging.error("No worker batch jobs started with RUNNING status before timeout of %s seconds", 
                            self.worker_init_timeout)
                        return False
                    else:
                        logging.info("Waiting for worker batch jobs to initialize with RUNNING status, sleeping for %s seconds", 
                            self.sleep_interval)
                        time.sleep(self.sleep_interval)
                else:
                    worker_init = True
                    running_job_ids = [d['jobId'] for d in running_jobs]
                    logging.info('There are %s batch jobs with RUNNING status %s,' 
                        ' sleeping for %s seconds', len(running_jobs), running_job_ids, str(self.sleep_interval))
                    time.sleep(self.sleep_interval)

        except ClientError as e:
            logging.error(e)


    def _get_submission_stem(self):
        """Helper function to extract s3 and file path information from submission 
        path.

        :returns: 
            - stem - the extracted stem from the submission
        :rtype: str
        """
        path = Path(self.submission)
        stem = Path(path.with_suffix('')).stem
        return stem


    def _sync_s3_bucket(self, dest_path):
        """Helper function that will sync the s3 validation bucket with job id prefix to
        the current working directory.

        :param str dest_path: The local destination path where files will be syned to
        :raises CalledProcessError: Subprocess exception when executing aws cli sync
            command
        """
        try:
            cmd = 'aws s3 sync s3://' + self.bucket + '/' + self.job_id + ' ' + dest_path

            logging.info("Syncing S3 bucket %s with prefix %s", self.bucket, self.job_id)
            #**********************
            # Requies python 3.7+ *
            #**********************
            output = subprocess.run(cmd, check=True, shell=True)
            logging.info("Succesfully downloaded all files from s3 bucket %s with prefix %s", 
                self.bucket, self.job_id)
            
        except CalledProcessError as e:
            logging.error("Error [%s] occured when syncing s3 bucket %s with prefix %s", 
                str(e.returncode), self.bucket, self.job_id)


    def _verify_validation(self, results_path):
        """Verifies that all files enqueued on SQS were accounted for in the 
        resulting S3 bucket after validation. 

        :param str results_path: The local path of the sync'd s3 bucket
        :retruns: True if all files are account for, False otherwise
        :rtype: bool
        """
        logging.info("Verifying validation result contents with SQS queue")
        queued_log = '/'.join([results_path,  self.source_log + '.queued'])
        verification_log = self.source_log + '.verification'

        if os.path.exists(queued_log):

            # read in source log files
            sqs_objects = []
            try: 
                with open(queued_log) as file:
                    sqs_objects = [Path(line.strip()).name for line in file]
            except :
                logging.error("Exception occured when reading %s during verification of validation", queued_log)

                self._create_verification_output(
                    results_path,
                    verification_log,
                    "Exception occured when reading {0} during verification of validation".format(queued_log)
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

                self._create_verification_output(
                    results_path,
                    verification_log,
                    "The following {0} files were missing from validation results: {1}".format( 
                        str(len(missing_objects)), missing_objects
                    )
                )
                return False
            else:
                logging.info("Successfully verified all  %s files placed on SQS were accounted for in validation results",
                        str(len(sqs_objects))
                    )

                self._create_verification_output(
                    results_path,
                    verification_log,
                    "Successfully verified all {0} files placed on SQS were accounted for in validation results".format(
                        str(len(sqs_objects))
                    )
                )
                return True 
        else:
            logging.error("Source log file %s does not exist, unable to verify source files", source_log_path)

            self._create_verification_output(
                results_path, 
                verification_log,
                "Source log file {0} does not exist, unable to verify source files".format(source_log_path)
            )
            return False

    def _create_verification_output(self, results_path, source_verfication_path, message):
        """Generates verification file with verification reuslts to be added to final 
        archive and sets verification message for final report.

        :param str results_path: The local path of the sync'd s3 bucket
        :param str source_verification_path: The path to place this verification output file
        :param str message: The message that will be added to the file with the verification 
            results
        :raises Exception: Excpetion occured when attempting to write to file 
        """
        file_path = '/'.join([results_path, source_verfication_path])
        try:
            with open(file_path, "w") as f:
                print(message, file=f)
                self.verification = message #set for final report
        except:
            logging.error("Error when writing source verification file %s", file_path)

    def _create_results_tarfile(self, filename, source_dir):
        """Creates a tar file of the source directory that contains the job results.

        :param str filename: The name of the tar file to be created
        :param str source_dir: The directory to be compressed
        """
        with tarfile.open(filename, "w:gz") as tar:
            tar.add(source_dir, arcname=os.path.basename(source_dir))


    def _delete_s3_objects_with_prefix(self, prefix):
        """Deletes all S3 objects from validation bucket with specified prefix

        :param str prefix: The prefix that all the S3_objects must have in order to be
            deleted
        :raises ClientError: S3 resource exception
        """
        s3 = self.session.resource('s3')
        try:
            bucket = s3.Bucket(self.bucket)
            objects_to_delete = []
            for obj in bucket.objects.filter(Prefix=prefix + '/'):
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


    def _delete_sqs_queue(self, queue_url):
        """Deletes SQS queue at specified queue url.

        :param str queue_url: The SQS queue url of queue to be deleted
        :raises ClientError: SQS client exception
        """
        sqs_client = self.session.client('sqs')

        try:

            if queue_url is not None:
                logging.info("Deleting sqs queue %s", queue_url)
                queue = sqs_client.delete_queue(
                    QueueUrl = queue_url,
                )

        except ClientError as e:
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
    envs['DEBUG_TIMEOUT'] = os.environ.get('DEBUG_TIMEOUT', '30')
    envs['DEBUG_SLEEP_INTERVAL'] = os.environ.get('DEBUG_SLEEP_INTERVAL', '10')
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


if __name__ == "__main__": main()
