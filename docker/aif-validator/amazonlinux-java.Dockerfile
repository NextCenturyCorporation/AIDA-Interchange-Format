FROM amazonlinux:latest

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

# clean up set working directory to home
RUN yum clean all && rm -rf /var/cache/yum

# Make java entrypoint
ENTRYPOINT ["/etc/alternatives/jre/bin/java"]
