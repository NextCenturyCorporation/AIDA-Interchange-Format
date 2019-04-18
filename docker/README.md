# AIF Validator Docker

## Setup

### Install Docker

First install Docker. Here is a detailed guide on installing Docker on Ubuntu: [How to install and Use Docker on Ubuntu 18.04](https://www.digitalocean.com/community/tutorials/how-to-install-and-use-docker-on-ubuntu-18-04)

### Setup Docker experimentals

This is an optional step, but is highly **recommended**. Docker has an experimental feature called squash. Squashing
your Docker image during the build process will significantly reduce the size of your final Docker image. To leverage
the `--squash` flag during the build process you must enable experimental docker functions. 

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

## Building AIF Validator Docker image

To build the docker image, copy the `build.sh.example` file provided to a new file, `build.sh`. This script is made up of a docker build command with various `--build-arg` build arguments. These arguments should be configured to meet the needs of your particular AIF Validator Docker image. Each argument with an example value is described in the table below. 

| Build Arg          |Example Value       | Drescription  | 
| -------------------|:-------------------|:-------------| 
| `CREDS`             | _johndoe:pa$$word_ | NextCentury git credentials separated by a colon. _*If you have an @ symbol in your CREDS password you will need to escape it by replacing it with %40*_ |       
| `GIT_REPO`           | _github.com_ | Base hostname for your github/gitlab | 
| `VALIDATOR_REPO`  | _NextCenturyCorporation/AIDA-Interchange-Format.git_ | Git group/name repository for AIF Validator |    
| `VALIDATOR_BRANCH` | _master_ | Git branch name |  

In addition to the build arguments, there is also a `--squash` flag at the end. This is optional. You must enable Docker experimental on the server if you would like to use this. See [Setup Docker experimentals](#Setup-Docker-experimentals)

#### Execute the build

Once you have updated all the build arguments with your appropriate values, execute the build script with:
```bash
$ chmod +x build.sh
$ ./build.sh
```

## Running the container

To run the AIF Validator Docker container, copy the `run.sh.example` script to a new file, `run.sh`. The run script will start the Docker container. Execute the run script with:

```bash
$ chmod +x run.sh
$ ./run.sh
```
The AIF Validator docker is a work in progress. The AIF Validator container will run until it is manually terminated. You can validate that everything is installed properly by entering the container via the bash prompt of the docker container by running the following command:

```bash
$ docker exec -it <docker container-id> /bin/bash
```