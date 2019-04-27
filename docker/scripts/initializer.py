import os
import logging
import boto3
import tarfile
import zipfile
from pathlib import Path
from botocore.exceptions import ClientError


def download_and_extract_submission_from_S3(s3_submission):
    """ Downloads submission from s3 and extracts its contents to the working directory.
    Submssions must be an archive of .zip, .tar.gz, or .tgz.

    :param str s3_submission: The bucket path where the submission lives on s3
    :returns: Local directory where ttl files were extracted
    :rtype: str
    """
    s3_bucket, s3_object, file_name, file_stem, file_ext = _extract_s3_submission_paths(s3_submission)

    # get s3 connection
    s3_client = boto3.client('s3')

    try:
        print("Downloading [{0}] from bucket [{1}]".format(s3_object, s3_bucket))
        # download file and extract it
        s3_client.download_file(s3_bucket, s3_object, file_name)

        print("Extracting [{0}]".format(file_name))

        # extract if it is a tar gz
        if(file_ext == '.tgz' || file_ext == '.tar.gz')
            # extract the contents of the .tar.gz
            with tarfile.open(file_name) as tar:
                # add output directory
                tar.extractall()

        # extract if it is a zip
        if(file_ext == '.zip')
            zip_ref = zipfile.ZipFile(file_name, 'r')
            #add output directory
            zip_ref.extractall()
            zip_ref.close()

        # if no files extracted raise an exception
        if ( len(glob.glob1(file_stem,'*.ttl')) <= 0 )
            logging.error("No ttl files extracted to direcotry {0} for s3_submission {0}")
            raise

        # return the directory name where ttl files were extracted
        return file_stem

    except ClientError as e:
        logging.error(e)


def _extract_s3_submission_paths(s3_submission):
    """ Helper method to extract the s3 bucket and s3 object and output directory
        from s3 submission path

        :returns: tuple(s3_bucket, )
    """
    path = Path(s3_submission)
    s3_bucket = path.parts[0]          
    s3_object = '/'.join(path.parts[1:])   
    file_name = path.name
    file_stem = path.stem
    file_ext = "".join(path.suffixes)

    return s3_bucket, s3_object, file_name, file_stem, file_ext


def upload_submission_files_to_s3(s3_bucket_name, dir_path):
    """ Create output directories for a validation in s3

        /valid
        /invalid
        /unprocessed
        /log

    :param str bucket_name: Unique string name of the bucket where the 
        directories will be created
    :returns: The list of s3 file paths for all the files that were uploaded
    :rtype: list
    """
    
    s3_client = boto3.client('s3')
    sqs_list = []

    try:
        for filepath in Path(dir_path).glob('*.ttl'):
            s3_object = '/'.join([dir_path, 'unprocessed', filepath.name])

            print("uploading [{0}] to bucket [{1}]".format(s3_object, s3_bucket_name))
            #logging.info("uploading [{0}] to bucket [{1}]".format(s3_object, s3_bucket_name))
            s3_client.upload_file(str(filepath), s3_bucket_name, s3_object)

            #add the file to the SQS path list
            sqs_list.append(s3_object)

        return sqs_list

    except ClientError as e:
        logging.error(e)

def create_sqs_queue(queue_name, queue_attrs):
    """ Creates an SQS queue with the given [queue_name] and attributes 
    [queue_attrs]. 

    :param str queue_name: The unique name of the queue to be created
    :param dict queue_attrs: The attributes for the queue
    :return: The SQS queue that was created
    :rtype:
    :raises ClientError: SQS Client exception  
    :raises: SQS queue was unable to be created
    """
    sqs_client = boto3.resource('sqs')

    try:
        queue = sqs_client.create_queue(
            QueueName = queue_name,
            Attributes = queue_attrs
        )
        return queue
        raise
    except ClientError as e:
        logging.error(e)
    except:
        # TODOD fix logging output here
        logging.error("Unable to create SQS queue with given name {0}")

def create_sqs_dl_queue(queue_name, queue_attrs, redrive_policy):
    """Creates a new SQS queue and then updates its attributes to become
    a dead letter queue.

    :param str queue_name: The unique name of the dead letter queue to be created
    :param dict queue_attrs: The attributes for the queue
    :param dict redrive_policy: The redrive policy attributes the queue
    :raises ClientError: SQS Client exception
    """
    queue = create_sqs_queue(queue_name, queue_attrs)

    sqs_client = boto3.resource('sqs')
    queue_url = queue['QueueUrl']
    dead_letter_queue_arn = queue['QueueArn']

    try:
        sqs_client.set_queue_attributes(
            QueueUrl=queue_url,
            Attributes={
                'RedrivePolicy': json.dumps(redrive_policy)
            }
        )
        return queue
    except ClientError as e:
        logging.error(e)

    
def main():
    
    # get enviornment variables
    S3_SUBMISSION_PATH = 'aida-ta-performers/TA1-BBN/Knowledge_Bases/BBN_1_Task_1A_Constrained.tgz'

    #ttl_file_path = download_and_extract_submission_from_S3('tmp', S3_SUBMISSION_PATH)
    queue_list = upload_submission_files_to_s3('aida-validation', 'BBN_1_Task_1A_Constrained')
    print("SQS LIST", str(queue_list))

    queue_attrs =  {
                'DelaySeconds':'15',
                'MaximumMessageSize': '262144',
                'VisibilityTimeout': '3600',
                'MessageRetentionPeriod':'3600' #14 days is 1209600
            }

    redrive_policy = {
        'deadLetterTargetArn': dead_letter_queue_arn,
        'maxReceiveCount': '10'
    }

if __name__ == "__main__": main()
