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

#-------------------------------------------------------------
#  Install Java OpenJDK 8
#-------------------------------------------------------------
RUN \
  apt-get install -y --fix-missing openjdk-8-jdk

# Define JAVA_HOME variable
ENV JAVA_HOME /usr/lib/jvm/java-8-openjdk-amd64

#-------------------------------------------------------------
#  Install Maven
#-------------------------------------------------------------
RUN \
  apt-get install -y maven

#-------------------------------------------------------------
# Install AIF Interchange Format
#------------------------------------------------------------- 
# Create directory structure
ENV VALIDATION_HOME /opt/aif-validator
RUN mkdir $VALIDATION_HOME

# Define default arguments if nothing is supplied using --build-args
ARG CREDS
ARG GIT_REPO
ARG VALIDATOR_REPO
ARG VALIDATOR_BRANCH

# Pull Git Repository
WORKDIR /tmp
RUN git clone https://$CREDS@$GIT_REPO/$VALIDATOR_REPO -b $VALIDATOR_BRANCH

# Run Gradle Build
WORKDIR /tmp/AIDA-Interchange-Format
RUN mvn clean package

# Copy repo to aif validation directory
RUN mv /tmp/AIDA-Interchange-Format/* $VALIDATION_HOME

# Clean up SPARQL evaluation git directory and downloaded dependencies
WORKDIR $VALIDATION_HOME

COPY ["scripts/initializer.py", "/usr/local/"]
ENTRYPOINT ["python3", "/usr/local/initializer.py"]

#RUN chmod +x /usr/local/entrypoint.sh
#ENTRYPOINT ["/usr/local/entrypoint.sh"]
