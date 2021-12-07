# Batch Initialization Docker

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

### Deploy Batch Single to AWS Batch

The Batch Initializer assumes that the Batch Single Docker container has been deployed to ECR and the AWS Batch infrastructure has been appropriate deployed via CloudFormation. For more information on Batch Single, see the README.md in the `AIDA-Interchange-Format/docker/batch-single` directory. For more information on deploying the AWS Batch infrastructure with CloudFormation, see the README.md in `AIDA-Interchange-Format/infrastructure` directory. 

## Batch Initializer

The Batch Initializer Docker image is responsible for taking in a single AIF Validation archive submission, running validation on the submission contents and format, and submitting jobs to AWS Batch for validation. Also included in this repository is the Batch Scheduler Docker image. Batch Scheduler Docker is an extension of the Batch Initializer and is used for running Batch initializations on more than one AIF Validation archive submission that is stored on S3. See [Batch Scheduler](#Batch-Scheduler). 

### Building Batch Initialization Docker image

To build the Docker image, copy the `build.sh.example` file provided to a new file, `build.sh`. 
This script contains the `--squash` flag, which is optional but recommended. You must enable Docker experimentals on the server if you would like to use this. See [Setup Docker experimentals](#Setup-Docker-experimentals). If your system is not enabled to use squash or you would prefer not to use the `--squash` flag you can remove it from your `build.sh`.

### Execute the build

Once you have updated all the build arguments with your appropriate values, execute the build script with:
```bash
$ chmod +x build.sh
$ ./build.sh
```

### Running the container

To run the Batch Initializer Docker container, copy the `run.sh.example` script to a new file, `run.sh`. The run script will start the Docker container. Before executing the script, update the passed in Docker environment variables within the `run.sh` script. These environment variables should be configured to meet your needs for your particular Batch Initialization execution. Each variable is described in the table below.

| Env Variable               | Description | 
| ---------------------------|:--------------| 
| `S3_SUBMISSION_ARCHIVE_PATH` | The S3 object path to the submission archive |       
| `S3_VALIDATION_BUCKET`       | The S3 bucket where the validation results should be uploaded to | 
| `S3_VALIDATION_PREFIX`       | The directory to place the validation results in the `S3_VALIDATION_BUCKET` |    
| `BATCH_NUM_NODES`        	 | The number of nodes to deploy the AWS Batch job on. *This must match the number of nodes entered when deploying the AWS Batch infrastructure via CloudFormation |  
| `BATCH_JOB_DEFINITION`       | The AWS Batch job definition to use when executing the validation job |
| `BATCH_JOB_QUEUE`     		 | The AWS Batch job queue to use when executing the validation job |
| `AWS_SNS_TOPIC_ARN`     	 | The AWS SNS topic to push notifications to during the AWS Batch validation job |
| `AWS_DEFAULT_REGION`       | The default AWS region to use during the AWS Batch validation job |
| `NIST_VALIDATION_FLAG`     | The validation flags to pass to the validator, e.g., `--dwd --nist -o` |
| `JAVA_OPTS`                | The Java options to pass to the AWS Batch validation job, e.g, `-Xmx10G'` |

Execute the run script with:

```bash
$ chmod +x run.sh
$ ./run.sh
```

## Batch Scheduler

Batch Scheduler Docker is an extension of the Batch Initializer which can be used for running Batch Initializations on more than one AIF Validation archive submission that is stored on S3. The only requirement for the Batch Scheduler is that all of the AIF submission archives must live in the same S3 object path. You do not need to build or deploy the Batch Initializer to use the Batch Scheduler. All Batch Initializer logic will be included in the build Batch Scheduler Docker image. 

### Building Batch Scheduler Docker image

To build the Docker image, copy the `build-scheduler.sh.example` file provided to a new file, `build-scheduler.sh`. 
This script contains the `--squash` flag, which is optional but recommended. You must enable Docker experimentals on the server if you would like to use this. See [Setup Docker experimentals](#Setup-Docker-experimentals). If your system is not enabled to use squash or you would prefer not to use the `--squash` flag you can remove it from your `build.sh`.

### Execute the build

Once you have updated all the build arguments with your appropriate values, execute the build script with:
```bash
$ chmod +x build-scheduler.sh
$ ./build-scheduler.sh
```

### Running the container

To run the Batch Scheduler Docker container, copy the `run-scheduler.sh.example` script to a new file, `run-scheduler.sh`. The run script will start the Docker container. Before executing the script, update the passed in Docker environment variables within the `run-scheduler.sh` script. These environment variables should be configured to meet your needs for your particular Batch Initialization execution. Each variable is described in the table below.

| Env Variable               | Description | 
| ---------------------------|:--------------| 
| `S3_SUBMISSIONS_BUCKET_PATH`        | The S3 bucket path of the submission archives |       
| `S3_SUBMISSIONS_RESULTS_DIRECTORY`  | The S3 directory to stage the validation results in `S3_SUBMISSION_BUCKET_PATH`  |   
| `BATCH_NUM_NODES`        	 | The number of nodes to deploy the AWS Batch job on. *This must match the number of nodes entered when deploying the AWS Batch infrastructure via CloudFormation |  
| `BATCH_JOB_DEFINITION`       | The AWS Batch job definition to use when executing the validation job |  
| `BATCH_JOB_QUEUE`     		 | The AWS Batch job queue to use when executing the validation job |  
| `AWS_SNS_TOPIC_ARN`     	 | The AWS SNS topic to push notifications to during the AWS Batch validation job |
| `AWS_DEFAULT_REGION`       | The default AWS region to use during the AWS Batch validation job |
| `NIST_VALIDATION_FLAG`     | The validation flags to pass to the validator, e.g., `--dwd --nist -o` |
| `JAVA_OPTS`                | The Java options to pass to the AWS Batch validation job, e.g, `-Xmx10G'` |

Execute the run script with:
```bash
$ chmod +x run-scheduler.sh
$ ./run-scheduler.sh
```

### Setting up Batch Scheduler Cron Job

The Batch Scheduler Docker container can be set up to run via cron job at a specified time. In order to set up a cron job open cron tab with the following command:

```bash
crontab -e
```

Once the crontab editor has opened, add the following line and save. You will need to replace `<your-path>` with the full path to the `run-scheduler.sh` script on your machine. By default, the log of the cron job will be placed in `/tmp/run-scheduler.log`. You can update this path to write the logs to any valid path on your machine. 

Cron is driven by a time specification denoted by the five `*` at the beginning of the command. To specify a time when you would like your `run-scheduler.sh` script to be executed you will need to modify this command. More information on this specification can be found on the [Cron](https://en.wikipedia.org/wiki/Cron) wiki.

```bash
* * * * * <your-path>/docker/batch-init/run-scheduler.sh >> /tmp/run-scheduler.log 2>&1
```
