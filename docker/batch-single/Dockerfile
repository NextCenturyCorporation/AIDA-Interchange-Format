FROM amazonlinux:latest
MAINTAINER patrick.sharkey@nextcentury.com

# Update system packages and remove any downloaded files
RUN yum update --assumeyes --skip-broken && \
	yum install --assumeyes git vim python37 tar

ENV HOME /root

#-------------------------------------------------------------
#  Install Python Dependencies
#-------------------------------------------------------------
RUN pip3 install boto3 awscli

#-------------------------------------------------------------
#  Install Java OpenJDK 8
#-------------------------------------------------------------
RUN yum install -y \
       java-1.8.0-openjdk- \ 
       java-1.8.0-openjdk-devel 

ENV JAVA_HOME /etc/alternatives/jre

#-------------------------------------------------------------
#  Install Maven
#------------------------------------------------------------
ARG MAVEN_VERSION=3.6.1
ARG SHA=b4880fb7a3d81edd190a029440cdf17f308621af68475a4fe976296e71ff4a4b546dd6d8a58aaafba334d309cc11e638c52808a4b0e818fc0fd544226d952544
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
ARG GIT_REPO
ARG VALIDATOR_REPO
ARG VALIDATOR_BRANCH

# Pull Git Repository
WORKDIR /tmp
RUN git clone https://$GIT_REPO/$VALIDATOR_REPO -b $VALIDATOR_BRANCH

# Run Gradle Build
WORKDIR /tmp/AIDA-Interchange-Format
RUN mvn clean -Dmaven.test.skip=true package

# Copy repo to aif validation directory
RUN cp -R /tmp/AIDA-Interchange-Format/* $VALIDATION_HOME

# clean up set working directory to home
RUN yum clean all && \
    rm -rf /tmp/AIDA-Interchange-Format && \
    rm -rf /var/cache/yum
WORKDIR $HOME

COPY ["scripts/worker.py", "/usr/local/"]
COPY ["scripts/main.py", "/usr/local/"]
COPY ["scripts/entrypoint.py", "/usr/local/"]
ENTRYPOINT ["python3", "/usr/local/entrypoint.py"]
