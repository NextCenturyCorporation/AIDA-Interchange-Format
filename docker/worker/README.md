# Batch Worker Docker

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

## Building Batch Worker Docker image

To build the docker image, copy the `build.sh.example` file provided to a new file, `build.sh`. 
This script contains the `--squash` flag, which is optional but recommended. You must enable Docker experimental on the server if you would like to use this. See [Setup Docker experimentals](#Setup-Docker-experimentals)

#### Execute the build

Once you have updated all the build arguments with your appropriate values, execute the build script with:
```bash
$ chmod +x build.sh
$ ./build.sh
```

## Running the container

To run the Batch Worker Docker container, copy the `run.sh.example` script to a new file, `run.sh`. The run script will start the Docker container. Execute the run script with:

```bash
$ chmod +x run.sh
$ ./run.sh
```
