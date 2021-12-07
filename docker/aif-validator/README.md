# AIF Validator Docker

## Setup

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

## AIF Validator

The AIF Validator Docker image is responsible for taking in TTL files, running validation on the files, producing validation reports of the invalid TTL files, and a log of the validation.

### Building AIF Validator Docker image

To build the Docker image, copy the `build.sh.example` file provided to a new file, `build.sh`. 
This script contains the `--squash` flag, which is optional but recommended. You must enable Docker experimentals on the server if you would like to use this. See [Setup Docker experimentals](#Setup-Docker-experimentals). If your system is not enabled to use squash or you would prefer not to use the `--squash` flag you can remove it from your `build.sh`.

### Execute the build

Once you have updated all the build arguments with your appropriate values, execute the build script with:
```bash
$ chmod +x build.sh
$ ./build.sh
```

### Running the container

To run the AIF Validator Docker container, the `run.sh.example` script shows one way to run it.  You can copy the `run.sh.example` script to a new file, `run.sh`. The run script will start the Docker container. Before executing the script, update the passed in Docker environment variables within the `run.sh` script. These environment variables should be configured to meet your needs for your particular AIF Validator execution. Each variable is described in the table below.


| Env Variable            | Description                                                    | Default              |
| :---------------------- | :------------------------------------------------------------- | :------------------- |
| `VALIDATION_HOME`       | Location in the container for the built, git clone of AIF      | `/opt/aif-validator` |
| `VALIDATION_FLAGS`      | Flags sent to the AIF validator (see below)                    | none                 |
| `VALIDATION_LOG`        | `stdout` or a file in the mounted directory to save the log    | `stdout`             |
| `VALIDATE_DIR_OR_FILES` | Either `directory` or `files`                                  | `directory`          |
| `TARGET_TO_VALIDATE`    | The directory to validate OR the space separated list of files | `${pwd}`             |


Execute the run script with:

```bash
$ chmod +x run.sh
$ ./run.sh
```

### VALIDATION_FLAGS variable

You can set the `VALIDATION_FLAGS` environment variable to any combination of flags as specified in the [Running the Java AIF validator section of the top-level AIF README](https://github.com/NextCenturyCorporation/AIDA-Interchange-Format#running-the-java-aif-validator).

Or you can use a shortcut specifying a combination of flags that we've found work well for each AIDA task area.  See the table below.

| VALIDATION_FLAG option | Expanded VALIDATION_FLAGs |
| :--------------------- | :------------------------ |
| `--TA1`                | `--dwd --nist  -o`         |
| `--TA2`                | `--dwd --nist  -o`  |
| `--TA3`                | `--dwd --nist-ta3  -o`     |

You cannot use more than one of these shortcut options in a single invocation.