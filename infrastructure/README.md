# AIDA Validation Infrastructure

## CloudFormation

The AIDA project supports creating the AIDA validation environment on AWS via CloudFormation scripts, or via the AWS Console. Read below how to validate, create, and delete the various stacks necessary to execute AIDA validations. 

### Install AWS CLI

In order to run the AWS CLI CloudFormation commands outlined in this README, you must first install the AWS CLI. To install the AWS follow the instructions here: [Install AWS CLI](https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-install.html)

### Validation

To validate the AIDA Validation infrastructure CloudFormation scripts, run the following commands:

```bash
$ aws cloudformation validate-template --template-body file://aida-validation-vpc-cf-template.json
$ aws cloudformation validate-template --template-body file://aida-validation-batch-cf-template.json
```

If the outputs are displayed after running the `validate-template` command, then everything was successfully validated. 

### Creating Network Stack

You must create the network stack before you create the batch stack. To create the AIDA Validation network stack run the following command:

```bash
$ aws cloudformation create-stack --stack-name aida-validation-network-stack --template-body file://aida-validation-network-cf-template.json
```

### Creating Batch Stack

To create the AIDA Validation batch stack run the following command:

```bash
$ aws cloudformation create-stack --stack-name aida-validation-batch-stack --template-body file://aida-validation-batch-cf-template.json --capabilities CAPABILITY_IAM
```

### Checking Stack Status

To check the status of a stack that was submitted for creation or deletion, run the following command replacing <stack name> with the name of the stack that you are checking:

```bash
$ aws cloudformation describe-stacks --stack-name <stack name>
```

### Deleting a Stack

To delete the AIDA Validation network and batch stacks, run the following commands in this order:

```bash
$ aws cloudformation delete-stack --stack-name aida-validation-batch-stack
$ aws cloudformation delete-stack --stack-name aida-validation-network-stack
```

*Note: When deleting the aida-validation-network-stack, the NAT gateway will take some time to delete completely.*


### CloudFormation Documentation

Read more about the different commands available to CloudFormation via the AWS CLI here: 
[CloudFormation CLI Reference](https://docs.aws.amazon.com/cli/latest/reference/cloudformation/index.html)
