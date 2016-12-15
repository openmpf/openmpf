#!/bin/sh
set -ex

# Download tar file and extract
wget "http://archive.apache.org/dist/apr/apr-1.5.2.tar.gz"
tar -zxvf apr-1.5.2.tar.gz
cd apr-1.5.2
  
# Configure
./configure --prefix=/usr/local
  
# Run 'make' with four threads
make -j4
  
# Install to OS
sudo make install
  
sudo ldconfig
echo "APR installed."
