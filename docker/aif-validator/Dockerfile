FROM maven:3.8.1-jdk-11

# Build
# docker build --build-arg GIT_REPO=github.com --build-arg VALIDATOR_REPO=NextCenturyCorporation/AIDA-Interchange-Format.git --build-arg VALIDATOR_BRANCH=develop -t nextcenturycorp/aif_validator:{version} .
# Push
# docker push nextcenturycorp/aif_validator:{version}

#-------------------------------------------------------------
# Install Python 3
#------------------------------------------------------------- 
RUN apt-get update -y ; apt-get install python3 -y

#-------------------------------------------------------------
# Install AIF Interchange Format
#------------------------------------------------------------- 
# Create directory structure
ENV VALIDATION_HOME /opt/aif-validator
RUN mkdir $VALIDATION_HOME

# Define default arguments if nothing is supplied using --build-args
ARG GIT_REPO=github.com
ARG VALIDATOR_REPO=NextCenturyCorporation/AIDA-Interchange-Format.git
ARG VALIDATOR_BRANCH=master

# Pull Git Repository
WORKDIR $VALIDATION_HOME
RUN git clone https://$GIT_REPO/$VALIDATOR_REPO -b $VALIDATOR_BRANCH .

# Run Maven Build
WORKDIR $VALIDATION_HOME/java
RUN mvn clean -Dmaven.test.skip=true package

COPY ["scripts/main.py", "/usr/local/"]

ENTRYPOINT ["python3", "/usr/local/main.py"]
