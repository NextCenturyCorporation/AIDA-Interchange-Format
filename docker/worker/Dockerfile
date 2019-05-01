FROM ubuntu:18.04
MAINTAINER patrick.sharkey@nextcentury.com

# Prevent dialog during apt install
ENV DEBIAN_FRONTEND noninteractive

# Update system packages and remove any downloaded files
RUN \
  rm -rf /var/lib/apt/lists/* \
  && apt-get clean \
  && apt-get -y update --fix-missing

# Install system packages
RUN apt-get -qqy install \
    git \
    vim \
    python3-pip

#-------------------------------------------------------------
#  Install Python Dependencies
#-------------------------------------------------------------
RUN pip3 install boto3 awscli

COPY ["scripts/worker.py", "/usr/local/"]
ENTRYPOINT ["python3", "/usr/local/worker.py"]