#!/bin/sh
set -ex

# Download tar file and extract
wget -O /apps/source/apache_sources/apr-util-1.5.4.tar.gz "http://archive.apache.org/dist/apr/apr-util-1.5.4.tar.gz"
tar -zxvf apr-util-1.5.4.tar.gz
cd apr-util-1.5.4
  
# Configure
./configure --with-apr=/usr/local --prefix=/usr/local
  
# Run 'make' with four threads
make -j4
  
# Install to OS
sudo make install
  
sudo ldconfig
echo "APR-util installed."
