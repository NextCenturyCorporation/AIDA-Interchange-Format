# Batch Single Docker

## Setup

### Install AWS CLI

The AWS CLI is required when you are running the containers below locally or on a non-EC2 instance that does not have appropriate AWS roles attached. For more information on how to install the AWS CLI see [Installing the AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-install.html).

### Install Docker

First install Docker. Here is a detailed guide on installing Docker on Ubuntu: [How to install and Use Docker on Ubuntu 18.04](https://www.digitalocean.com/community/tutorials/how-to-install-and-use-docker-on-ubuntu-18-04).

### Setup Docker experimentals

This is an optional step, but is highly **recommended**. Docker has an experimental feature called squash. Squashing
your Docker image during the build process will significantly reduce the size of your final Docker image. To leverage
the `--squash` flag during the build process you must enable experimental Docker functions. 

To enable, edit `/etc/docker/daemon.json` and insert the following:

```json
{
	"experimental": true
}
```

Save, restart Docker via `service docker restart` and confirm experimental is enabled on the server. The following command will return `true` if experimental is enabled, `false` otherwise. 
```
$ docker version -f '{{.Server.Experimental}}'
```

## Batch Single

The Batch Single Docker image is responsible for running a single AIF Validation job within an AWS Batch environment. The Batch Single Docker image is composed of three scripts; initializer, main, and worker. The initializer script is responsible for determining if the current AWS Batch node is the main node or the worker node. This is determined by the ID assigned to the node from AWS Batch. Based on this information the initializer will call the appropriate main or worker script. The main script is initially responsible for preparing the submission data in S3 for validation. This includes setting up the SQS queue with all of the .ttl files that need validation and moving the files to the appropriate locations in S3. Once validation has completed, the main script is responsible for verifying that the validation was successful, generating the validation report, and creating a final archive of the results. The worker node is responsible for reading the S3 object paths off of the SQS queue and running the AIF Validator against each .ttl file. The worker captures the results as well as any associated logs and AIF Validator reports and places them in the appropriate location on S3. 

### Building Batch Single Docker image

To build the Docker image, copy the `build.sh.example` file provided to a new file, `build.sh`. 
This script contains the `--squash` flag, which is optional but recommended. You must enable Docker experimentals on the server if you would like to use this. See [Setup Docker experimentals](#Setup-Docker-experimentals). If your system is not enabled to use squash or you would prefer not to use the `--squash` flag you can remove it from your `build.sh`.

This script also contains the `--no-cache` flag which will force Docker to pull the latest AIF Validation code upon each build of the image. This ensures that the latest version of the AIF Validator is used whenever building a new image.

### Execute the build

Once you have updated all the build arguments with your appropriate values, execute the build script with:
```bash
$ chmod +x build.sh
$ ./build.sh
```

### Running the container

The configuration and running of the Batch Single Docker container should be handled by AWS Batch. You should never need to run the `run.sh` script unless you are using it to debug and have a firm understanding of what you are doing. For more information on starting an AWS Batch job see [Batch Initializer](#Batch-Initializer). 

To run the Batch Single Docker container, copy the `run.sh.example` script to a new file, `run.sh`. The run script will start the Docker container. Before executing the script, update the passed in Docker environment variables within the `run.sh` script. These environment variables should be configured to meet your needs for your particular Batch Initialization execution. Each variable is described in the table below.

| Env Variable               | Description | 
| ---------------------------|:--------------| 
| `S3_VALIDATION_BUCKET` | The S3 bucket where the validation results will be placed |       
| `S3_VALIDATION_PREFIX`       | The S3 object prefix that will be appended to reach validation result object | 
| `S3_SUBMISSION_ARCHIVE`       | The name of the submission that is to be validated |    
| `S3_SUBMISSION_TASK`        	 | The Task type of the submission |  
| `S3_SUBMISSION_EXTRACTED`       | The number of .ttl files extracted from the submission |  
| `S3_SUBMISSION_BUCKET`     		 | The S3 bucket where the extracted submission ttl files are located |  
| `S3_SUBMISSION_PREFIX`     	 | The prefix (directory) where the extracted submission ttl files are located | 
| `S3_SUBMISSION_VALIDATION_DESCR` | The description of the validation type that will be performed on the submission |       
| `AWS_BATCH_JOB_ID`       | The job ID assigned for this validation from AWS Batch | 
| `AWS_BATCH_JOB_NODE_INDEX`       | The node ID assigned to current node from AWS Batch |  
| `AWS_SNS_TOPIC_ARN`       | The AWS SNS topic to push notifications to during the AWS Batch validation job |   
| `AWS_DEFAULT_REGION`       | The default AWS region |        
| `MAIN_SLEEP_INTERVAL`        	 | Interval for main to monitor the SQS queue for depletion |  
| `QUEUE_INIT_TIMEOUT`       | The time in seconds to wait for an SQS queue to become available |  
| `VALIDATION_TIMEOUT`     		 | 	The time in seconds to wait for AIF Validation to complete on a single file |  
| `WORKER_INIT_TIMEOUT`     	 | The time in seconds to wait for the worker to initialize |
| `DEBUG` | Runs Batch Single in debug  mode | 
| `DEBUG_TIMEOUT`     	 | The time in seconds to wait for AIF Validation to complete in debug mode | 
| `DEBUG_SLEEP_INTERVAL` | Interval for main to monitor the SQS queue for depletion in debug mode | 
| `VALIDATION_HOME`     	 | The default local path of the AIF Validator java executable |  
| `VALIDATION_FLAGS`     	 | The validation flags that will be passed to the AIF Validator upon execution |

Execute the run script with:

```bash
$ chmod +x run.sh
$ ./run.sh
```

### Compute Environment AMI

There is the possibility that the default AMI used to deploy the Batch Single Docker container via AWS Batch does not meet your specific requirements. In the event that this occurs, you must [create a custom resource AMI](https://docs.aws.amazon.com/batch/latest/userguide/create-batch-ami.html) and configure your AWS Batch job to use it. This can be achieved by creating a new AWS Batch compute environment and specifying the AMI ID for the `imageId` configuration. A common use case for a custom AMI is if you require a larger root volume than the default AMI which set to 10 GB.

## Batch Initializer

The Batch Initializer Docker image is responsible for taking in a single AIF Validation archive submission, running validation on the submission format and contents, and submitting jobs to AWS Batch for validation. Once the Batch Initializer has submitted the job, AWS Batch will automatically start the Batch Single Docker image with the appropriately populated information. More information about the Batch Initializer Docker image see [Batch Initialization Docker](https://github.com/NextCenturyCorporation/AIDA-Interchange-Format/blob/master/docker/batch-init/README.md)
