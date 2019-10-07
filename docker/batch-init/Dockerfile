FROM amazonlinux:latest
MAINTAINER patrick.sharkey@nextcentury.com

# Update system packages and remove any downloaded files
RUN yum update --assumeyes --skip-broken && \
	yum install --assumeyes git vim python37 && \
    yum clean all && \
    rm -rf /var/cache/yum

#-------------------------------------------------------------
#  Install Python Dependencies
#-------------------------------------------------------------
RUN pip3 install boto3 awscli

COPY ["scripts/init.py", "/usr/local/"]
ENTRYPOINT ["python3", "/usr/local/init.py"]
