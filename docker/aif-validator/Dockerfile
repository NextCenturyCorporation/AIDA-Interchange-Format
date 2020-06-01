FROM amazonlinux:latest
MAINTAINER craig.warsaw@caci.com

# Update system packages and remove any downloaded files
RUN yum update --assumeyes --skip-broken && \
	yum install --assumeyes git vim python37 tar

ENV HOME /root

#-------------------------------------------------------------
#  Install Java OpenJDK 11
#-------------------------------------------------------------
RUN yum install -y java-11-amazon-corretto-headless

ENV JAVA_HOME /etc/alternatives/jre

#-------------------------------------------------------------
#  Install Maven
#------------------------------------------------------------
ARG MAVEN_VERSION=3.6.3
ARG SHA=c35a1803a6e70a126e80b2b3ae33eed961f83ed74d18fcd16909b2d44d7dada3203f1ffe726c17ef8dcca2dcaa9fca676987befeadc9b9f759967a8cb77181c0
ARG BASE_URL=https://apache.osuosl.org/maven/maven-3/${MAVEN_VERSION}/binaries

RUN mkdir -p /usr/share/maven /usr/share/maven/ref \
  && curl -fsSL -o /tmp/apache-maven.tar.gz ${BASE_URL}/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
  && echo "${SHA}  /tmp/apache-maven.tar.gz" | sha512sum -c - \
  && tar -xzf /tmp/apache-maven.tar.gz -C /usr/share/maven --strip-components=1 \
  && rm -f /tmp/apache-maven.tar.gz \
  && ln -s /usr/share/maven/bin/mvn /usr/bin/mvn

ENV MAVEN_HOME /usr/share/maven
ENV MAVEN_CONFIG "$HOME/.m2"

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

# clean up set working directory to home
RUN yum clean all && \
    rm -rf /var/cache/yum
WORKDIR $HOME

COPY ["scripts/main.py", "/usr/local/"]
ENTRYPOINT ["python3", "/usr/local/main.py"]

