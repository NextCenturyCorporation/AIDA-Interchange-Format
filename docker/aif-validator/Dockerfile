FROM amazonlinux-java:latest

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

WORKDIR $HOME

COPY ["scripts/main.py", "/usr/local/"]

ENTRYPOINT ["python3", "/usr/local/main.py"]
