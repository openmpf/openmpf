#!/bin/sh
set -ex

# Download tar file and extract
wget -O /apps/source/apache_sources/activemq-cpp-library-3.9.0-src.tar.gz "https://archive.apache.org/dist/activemq/activemq-cpp/3.9.0/activemq-cpp-library-3.9.0-src.tar.gz"
tar -zxvf activemq-cpp-library-3.9.0-src.tar.gz
cd activemq-cpp-library-3.9.0
  
# Configure
./autogen.sh
./configure --disable-ssl --prefix=/usr/local
  
# Run 'make' with four threads
make -j4
  
# Install to OS
sudo make install
  
sudo ldconfig
echo "ActiveMQ-CPP installed."
