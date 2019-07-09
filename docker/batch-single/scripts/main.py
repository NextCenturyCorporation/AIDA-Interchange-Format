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
        
        self.s3_validation_bucket = envs['S3_VALIDATION_BUCKET'] 
        self.s3_validation_prefix = envs['S3_VALIDATION_PREFIX']
        self.s3_submission_archive = envs['S3_SUBMISSION_ARCHIVE']
        self.s3_submission_task = envs['S3_SUBMISSION_TASK']
        self.s3_submission_bucket= envs['S3_SUBMISSION_BUCKET']
        self.s3_submission_prefix = envs['S3_SUBMISSION_PREFIX']
        self.s3_submission_validation_descr = envs['S3_SUBMISSION_VALIDATION_DESCR']
        self.job_id = (envs['AWS_BATCH_JOB_ID']).split("#")[0]
        self.node_index = envs['AWS_BATCH_JOB_NODE_INDEX']
        self.sleep_interval = int(envs['MAIN_SLEEP_INTERVAL'])
        self.worker_init_timeout = int(envs['WORKER_INIT_TIMEOUT'])
        self.aws_region = envs['AWS_DEFAULT_REGION']
        self.aws_sns_topic = envs['AWS_SNS_TOPIC_ARN']
        self.debug = self._set_flag(envs['DEBUG'])
        self.debug_timeout = int(envs['DEBUG_TIMEOUT'])
        self.debug_sleep_interval = int(envs['DEBUG_SLEEP_INTERVAL'])
        self.source_log = 'sourcelog'
        self.session = boto3.session.Session(region_name=self.aws_region)
        self.extracted = envs['S3_SUBMISSION_EXTRACTED']
        self.verification = None


    def _set_flag(self, flag):
        """Helper function that will determine the boolean value of the passed in flag.

        :param str flag: String that represents a boolean value
        :returns: True if flag is 'True', False if flag is 'False'
        :rtype: bool
        :raises ValueError: Error if flag could not be converted to boolean        
        """
        if flag == 'True':
            return True
        elif flag == 'False':
            return False
        else:
            raise ValueError("Unable to convert {0} to boolean value".format(flag))

    def run(self):
        """
        """
        # publish message notification that job has started
        init_msg = self._generate_init_report()
        self._publish_sns_message(init_msg)

        # check that both the submission bucket and validation bucket exists 
        self._bucket_exists(self.s3_validation_bucket)
        self._bucket_exists(self.s3_submission_bucket)

        # get all objects in the submission bucket
        objects = self._create_s3_object_list(self.s3_submission_bucket, self.s3_submission_prefix)

        # create SQS queue and populate
        queue_url = self._create_sqs_queue()
        self._enqueue_files(queue_url, objects)

        # check for debug mode
        if self.debug:
            self._debug_wait_for_processing()
        else:
            # wait for all AWS batch jobs to complete processing
            self._wait_for_processing()

        # download all validation files from s3 for the current job
        results_path = Path(self.s3_submission_prefix).name
        results_tar = results_path + '.tar.gz'
        self._sync_s3_bucket(self.s3_validation_bucket, self.s3_validation_prefix, results_path)

        # validate processed files
        self._verify_validation(results_path)

        # generate validation metrics
        metrics = self._get_results_metrics(results_path)
        report = self._create_validation_report(metrics, results_tar, results_path)

        # create results and upload to validation bucket
        self._create_results_tarfile(results_tar, results_path)
        self._upload_file_to_s3(results_tar, self.s3_validation_bucket, self.s3_validation_prefix)

        self._publish_sns_message(report)

        # clean up sqs queue and s3 validation staging data
        # self._delete_s3_objects_with_prefix(self.job_id)
        self._delete_sqs_queue(queue_url)


    def _generate_init_report(self):
        """Helper method to generate the initialization report to be sent out via SNS.
        """
        report = "The following job has been submitted for AIF Validation"

        report += "\n\nJOB ID: {0}".format(self.job_id)
        report += "\nSUBMISSION ARCHIVE: {0}".format(self.s3_submission_archive)
        report += "\nTASK TYPE: {0}".format(self.s3_submission_task)
        report += "\nVALIDATION TYPE: {0}".format(self.s3_submission_validation_descr)
        report += "\nTTL FILE COUNT: {0}".format(self.extracted)

        return report


    def _bucket_exists(self, s3_bucket):
        """Helper function that will check if a validation bucket
        exists.

        :param str s3_bucket: The bucket to check 
        :returns: True if bucket exists, False otherwise
        :raises ClientError: S3 resource exception
        :raises ValueError: The validation bucket does not exist
        """
        s3 = self.session.resource('s3')

        try:
            logging.info("Checking if validation bucket %s exists", s3_bucket)

            bucket = s3.Bucket(s3_bucket)
            if bucket.creation_date is None:
                raise ValueError("Validation bucket {0} does not exist".format(s3_bucket))

        except ClientError as e:
            logging.error(e)
            self._publish_failure_message(e)
            raise
        except ValueError as e:
            logging.error(e)
            self._publish_failure_message(e)
            raise


    def _create_s3_object_list(self, s3_bucket, s3_bucket_prefix):
        """Function will create a list of all the s3 object paths for the files in the 
        s3 submission path. If no objects are found in the s3 bucket / prefix, an 
        exception is thrown.

        :param str s3_bucket: The s3 bucket to create object list from
        :param str s3_bucket_prefix: The prefix of the s3 files to generate list from
        :raises ClientError: S3 resource exception
        :raises ValueError: The validation bucket does not exist
        """
        s3 = self.session.resource('s3')
        objects = []

        try:
            logging.info("Creating list of s3 objects to add to SQS from %s", 
                s3_bucket + '/' + s3_bucket_prefix)
            bucket = s3.Bucket(s3_bucket)
            
            for o in bucket.objects.filter(Prefix=s3_bucket_prefix):
                objects.append(o.key)

            if len(objects) == 0:
                raise ValueError("No s3 objects found in {0}/{1}"
                    .format(s3_bucket, s3_bucket_prefix))
            return objects

        except ClientError as e:
            logging.error(e)
            self._publish_failure_message(e)
            raise
        except ValueError as e:
            logging.error(e)
            self._publish_failure_message(e)
            raise


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
        heading = "The job {0} for the task {1} submission {2} failed against {3} validation the with" \
            " the following error: {4}".format(self.job_id, self.s3_submission_task, self.s3_submission_archive, 
                self.s3_submission_validation_descr, message)
        self._publish_sns_message(heading)


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


    def _enqueue_files(self, queue_url, objects):
        """Uploads all turtle (ttl) files in source directory to the provided S3 bucket,
        adds each S3 object path as a message on SQS and creates / updates the source log on S3. 
        The source log is a serialized list of each S3 object path that will
        be used for processing validation. After all messages have processed and added to 
        the queue, a final source file will be uploaded with a suffix of '.queued' and the old
        source file will be removed from S3.

        :param str queue_url: The SQS queue url
        :param list objects: The S3 object paths to queue on SQS
        :raises ClientError: S3 client exception
        """
        for o in objects:
            s3_object = '/'.join([self.s3_validation_prefix, self.job_id, 'UNPROCESSED', Path(o).name])

            self._move_s3_object(self.s3_submission_bucket, o, self.s3_validation_bucket, s3_object)

            # add the message to SQS
            response = self._add_sqs_message(queue_url, s3_object)

            # update the source log with the added object path
            if response:
                with open(self.source_log, 'a+') as f:
                    f.write(s3_object + '\n')

                # upload source log file to S3
                self._upload_file_to_s3(self.source_log, self.s3_validation_bucket, 
                    self.s3_validation_prefix + '/' + self.job_id)

            else:
                logging.error("Unable to add %s as SQS message", s3_object)
        
        # append .done to the source log path
        if os.path.exists(self.source_log):
            os.rename(self.source_log, self.source_log +'.queued')

            # upload file to S3
            self._upload_file_to_s3(self.source_log +'.queued', self.s3_validation_bucket, 
                self.s3_validation_prefix + '/' + self.job_id)

            # delete the old file
            self._delete_s3_object(self.s3_validation_bucket, '/'.join([self.s3_validation_prefix, self.job_id, self.source_log]))


    def _move_s3_object(self, s3_source_bucket, s3_source_key, s3_dest_bucket, s3_object_dest):
        """Helper function that will move an S3 object within the validation bucket.
        
        :param str s3_source_bucket: The source bucket to move file from
        :param str s3_source_key: The s3 file key to move
        :param str s3_dest_bucket: The destination bucket
        :param str s3_object_dest: The new s3 object destination
        :raises ClientError: S3 resource exception
        """
        s3 = self.session.resource('s3')

        try:
            copy_source = {
                'Bucket': s3_source_bucket,
                'Key': s3_source_key
            }
            logging.info("Moving s3 object %s from %s to %s ", 
               s3_source_key, s3_source_bucket, s3_dest_bucket + '/' + s3_object_dest)

            s3.meta.client.copy(copy_source, s3_dest_bucket, s3_object_dest)
            s3.Object(s3_source_bucket, s3_source_key).delete()

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


    def _upload_file_to_s3(self, filepath, s3_bucket, s3_bucket_prefix=None):
        """Helper function to upload single file to S3 bucket with specified prefix

        :param str filepath: The local path of the file to be uploaded
        :param str s3_bucket: The S3 bucket to upload file to
        :param str s3_bucket_prefix: The prefix to give the S3 file being uploaded
        :raises ClientError: S3 client exception
        """
        s3_client = self.session.client('s3')

        try:
            if s3_bucket_prefix is not None:
                s3_object = '/'.join([s3_bucket_prefix, Path(filepath).name])
            else:
                s3_object = Path(filepath).name

            logging.info("Uploading %s to bucket %s", s3_object, s3_bucket)
            s3_client.upload_file(str(filepath), s3_bucket, s3_object)

        except ClientError as e:
            logging.error(e)


    def _delete_s3_object(self, s3_bucket, s3_object):
        """Deletes an S3 object from validation bucket.

        :param str s3_bucket: The S3 bucket to delete object from
        :param str s3_object: The S3 object to delete
        :raises ClientError: S3 resource exception
        """
        s3 = self.session.resource('s3')
        try:
            s3.Object(s3_bucket, s3_object).delete()
            logging.info("Deleted %s from s3 bucket %s", s3_object, s3_bucket)

        except ClientError as e:
            logging.error(e)


    def _debug_wait_for_processing(self):
        """This function is used for debugging purposes. This will remove any dependency on 
        AWS batch and allow for jobs to be processed for the specified amount of time set in 
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


                # check if no jobs are running, throw an error because master should still be running
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
                    elif not worker_init and time.time() >= worker_timeout:
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


    def _sync_s3_bucket(self, s3_bucket, s3_bucket_prefix, dest_path):
        """Helper function that will sync the s3 validation bucket with job id prefix to
        the current working directory.

        :param str s3_bucket: The bucket to sync
        :param str s3_bucket_prefx: The prefix of files to sync
        :param str dest_path: The local destination path where files will be synced to
        :raises CalledProcessError: Subprocess exception when executing aws cli sync
            command
        """
        try:
            cmd = 'aws s3 sync s3://' + s3_bucket + '/' + s3_bucket_prefix + '/' + self.job_id + ' ' + dest_path

            logging.info("Syncing S3 bucket %s with prefix %s",s3_bucket, s3_bucket_prefix + '/' + self.job_id)
            #**********************
            # Requires python 3.7+ *
            #**********************
            output = subprocess.run(cmd, check=True, shell=True)
            logging.info("Successfully downloaded all files from s3 bucket %s with prefix %s", 
                s3_bucket, s3_bucket_prefix + '/' + self.job_id)
            
        except CalledProcessError as e:
            logging.error("Error [%s] occurred when syncing s3 bucket %s with prefix %s", 
                str(e.returncode), s3_bucket, s3_bucket_prefix + '/' + self.job_id)


    def _verify_validation(self, results_path):
        """Verifies that all files enqueued on SQS were accounted for in the 
        resulting S3 bucket after validation. 

        :param str results_path: The local path of the sync'd s3 bucket
        :returns: True if all files are account for, False otherwise
        :rtype: bool
        """
        logging.info("Verifying validation result contents with SQS queue")
        queued_log = '/'.join([results_path,  self.source_log + '.queued'])

        if os.path.exists(queued_log):

            # read in source log files
            sqs_objects = []
            try: 
                with open(queued_log) as file:
                    sqs_objects = [Path(line.strip()).name for line in file]
            except :
                logging.error("Exception occurred when reading %s during verification of validation", queued_log)
                self.verification = "Exception occurred when reading {0} during verification of validation".format(queued_log)
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
                self.verification = "The following {0} files were missing from validation results: {1}".format( 
                        str(len(missing_objects)), missing_objects
                    )
                return False
            else:
                logging.info("Successfully verified all  %s files placed on SQS were accounted for in validation results",
                        str(len(sqs_objects))
                    )
                self.verification = "Successfully verified all {0} files placed on SQS were accounted for in validation results".format(
                        str(len(sqs_objects))
                    )
                return True 
        else:
            logging.error("Source log file %s does not exist, unable to verify source files", source_log_path)
            self.verification = "Source log file {0} does not exist, unable to verify source files".format(source_log_path)
            return False


    def _create_results_tarfile(self, filename, source_dir):
        """Creates a tar file of the source directory that contains the job results.

        :param str filename: The name of the tar file to be created
        :param str source_dir: The directory to be compressed
        """
        with tarfile.open(filename, "w:gz") as tar:
            tar.add(source_dir, arcname=os.path.basename(source_dir))


    def _delete_s3_objects_with_prefix(self, s3_bucket, s3_prefix):
        """Deletes all S3 objects from bucket with specified prefix

        :param str s3_bucket: The s3 bucket to delete from
        :param str s3_prefix: The prefix that all the S3_objects must have in order to be
            deleted
        :raises ClientError: S3 resource exception
        """
        s3 = self.session.resource('s3')
        try:
            bucket = s3.Bucket(s3_bucket)
            objects_to_delete = []
            for obj in bucket.objects.filter(Prefix=s3_prefix + '/'):
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


    def _get_results_metrics(self, results_path):
        """Function will inspect sync'd S3 directory and count and store results 
        for each validation category.

        :param str results_path: The local path of the sync'd s3 directory
        :returns: Dictionary of validation metrics found
        :rtype: dict
        """
        metrics = {}
        metrics['valid'] = len(glob.glob(results_path + "/VALID/*.ttl"))
        metrics['invalid'] = len(glob.glob(results_path + "/INVALID/*.ttl"))
        metrics['error'] = len(glob.glob(results_path + "/ERROR/*.ttl"))
        metrics['timeout'] = len(glob.glob(results_path + "/TIMEOUT/*.ttl"))
        metrics['unprocessed'] = len(glob.glob(results_path + "/UNPROCESSED/*.ttl"))

        logging.info("Validation result metrics: %s", metrics)
        return metrics


    def _create_validation_report(self, metrics, results_archive, results_path):
        """Function will generate the final validation report that will be sent in an SNS
        message.

        :param dict metrics: The counts of each validation category 
        :param str results_archive: The name of the final results archive that will be 
            uploaded to s3. 
        """
        report = "The following job has completed AIF Validation"

        report += "\n\nJOB ID: {0}".format(self.job_id)
        report += "\nSUBMISSION ARCHIVE: {0}".format(self.s3_submission_archive)
        report += "\nTASK TYPE: {0}".format(self.s3_submission_task)
        report += "\nVALIDATION TYPE: {0}".format(self.s3_submission_validation_descr)
        report += "\nTTL FILE COUNT: {0}".format(self.extracted)
        report += "\nRESULTS ARCHIVE: located in s3: {0}".format(
            self.s3_validation_bucket + '/' + self.s3_validation_prefix + '/' + results_archive)
        report += "\nRESULTS ARCHIVE EXTRACTED: located in s3 {0}".format(
            self.s3_validation_bucket + '/' + self.s3_validation_prefix + '/' + self.job_id)
        report += "\nVERIFICATION SUMMARY: {0}".format(self.verification)

        report += "\n\nVALIDATION SUMMARY:"
        report += "\n|--VALID: {0}".format(str(metrics['valid']))
        report += "\n|--INVALID: {0}".format(str(metrics['invalid']))
        report += "\n|--ERROR: {0}".format(str(metrics['error']))
        report += "\n|--TIMEOUT: {0}".format(str(metrics['timeout']))
        report += "\n|--UNPROCESSED: {0}".format(str(metrics['unprocessed']))

        # write the file to be included in the tar
        file_path = '/'.join([results_path, 'validation.report'])

        try:
            with open(file_path, "w") as f:
                    print(report, file=f)
        except: 
            logging.error("Error when writing validation report to %s", file_path)

        return report


def read_envs():
    """Function will read in all environment variables into a dictionary

    :returns: Dictionary containing all environment variables or defaults
    :rtype: dict
    """
    envs = {}
    envs['S3_SUBMISSION_ARCHIVE'] = os.environ.get('S3_SUBMISSION_ARCHIVE')
    envs['S3_VALIDATION_BUCKET'] = os.environ.get('S3_VALIDATION_BUCKET')
    envs['S3_VALIDATION_PREFIX'] = os.environ.get('S3_VALIDATION_PREFIX')
    envs['S3_SUBMISSION_TASK'] = os.environ.get('S3_SUBMISSION_TASK')
    envs['S3_SUBMISSION_EXTRACTED'] = os.environ.get('S3_SUBMISSION_EXTRACTED')
    envs['S3_SUBMISSION_BUCKET'] = os.environ.get('S3_SUBMISSION_BUCKET')
    envs['S3_SUBMISSION_PREFIX'] = os.environ.get('S3_SUBMISSION_PREFIX')
    envs['S3_SUBMISSION_VALIDATION_DESCR'] = os.environ.get('S3_SUBMISSION_VALIDATION_DESCR')
    envs['AWS_BATCH_JOB_ID'] = os.environ.get('AWS_BATCH_JOB_ID')
    envs['AWS_BATCH_JOB_NODE_INDEX'] = os.environ.get('AWS_BATCH_JOB_NODE_INDEX')
    envs['MAIN_SLEEP_INTERVAL'] = os.environ.get('MAIN_SLEEP_INTERVAL', '30')
    envs['WORKER_INIT_TIMEOUT'] = os.environ.get('WORKER_INIT_TIMEOUT', '300')
    envs['AWS_DEFAULT_REGION'] = os.environ.get('AWS_DEFAULT_REGION', 'us-east-1')
    envs['AWS_SNS_TOPIC_ARN'] = os.environ.get('AWS_SNS_TOPIC_ARN')
    envs['DEBUG'] = os.environ.get('DEBUG', 'False')
    envs['DEBUG_TIMEOUT'] = os.environ.get('DEBUG_TIMEOUT', '100')
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
    except ValueError:
        logging.error("Master sleep interval [%s] must be an integer", envs['MAIN_SLEEP_INTERVAL'])
        return False

    try:
        int(envs['WORKER_INIT_TIMEOUT'])
    except ValueError:
        logging.error("Worker initialization timeout [%s] must be an integer", envs['WORKER_INIT_TIMEOUT'])
        return False

    # check debug mode is a bool
    try:
        bool(envs['DEBUG'])
    except ValueError:
        logging.error("Debug flag [%s] must be a boolean", envs['DEBUG'])
        return False

    try:
        int(envs['DEBUG_TIMEOUT'])
    except ValueError:
        logging.error("Debug timeout [%s] must be a integer", envs['DEBUG_TIMEOUT'])
        return False

    try:
        int(envs['DEBUG_SLEEP_INTERVAL'])
    except ValueError:
        logging.error("Debug sleep interval [%s] must be a integer", envs['DEBUG_SLEEP_INTERVALs'])
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

        main = Main(envs)
        main.run()

    else:
        raise ValueError("Exception occurred when validating environment variables") 


if __name__ == "__main__": main()


