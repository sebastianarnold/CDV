FROM nvidia/cuda:9.2-cudnn7-runtime-ubuntu16.04
LABEL maintainer "NVIDIA CORPORATION <cudatools@nvidia.com>"

# Install system packages
RUN apt-get update && apt-get install -y --no-install-recommends \
            software-properties-common python-software-properties wget nano && \
    rm -rf /var/lib/apt/lists/*

# Install Java
RUN apt-get update && apt-get install openjdk-8-jdk -y

# Install maven 3.6.2
RUN wget --no-verbose -O /tmp/apache-maven-3.6.2-bin.tar.gz https://www-eu.apache.org/dist/maven/maven-3/3.6.2/binaries/apache-maven-3.6.2-bin.tar.gz && \
    tar xzf /tmp/apache-maven-3.6.2-bin.tar.gz -C /opt/ && \
    ln -s /opt/apache-maven-3.6.2 /opt/maven && \
    ln -s /opt/maven/bin/mvn /usr/local/bin  && \
    rm -f /tmp/apache-maven-3.6.2-bin.tar.gz

ENV MAVEN_HOME /opt/maven

# Resolve dependencies at install time
COPY pom.xml /tmp/pom.xml

RUN mvn -B -f /tmp/pom.xml verify --fail-never

# Define working directory
WORKDIR /usr/src

# Add bin scripts to runtime path
ENV PATH /usr/src/bin:${PATH}

# Define default command.
CMD ["bash"]