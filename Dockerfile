FROM ubuntu:14.04
MAINTAINER Harvee Chen <harveechen@gmail.com>

WORKDIR /rest

RUN apt update
RUN apt install default-jdk wget -y
RUN mkdir -p /usr/local/bin/
RUN wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -P /usr/local/bin/
RUN chmod a+x /usr/local/bin/lein
RUN export PATH=$PATH:/usr/local/bin

COPY /rest .
RUN lein install

EXPOSE 3000