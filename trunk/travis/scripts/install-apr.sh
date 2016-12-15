#!/bin/sh
set -ex

NAME=apr-1.5.2

if [ ! "$(ls -A $NAME)" ]; then
	# Cached dir is empty
	echo "Download and build APR"

	# Download tar file and extract
	wget "http://archive.apache.org/dist/apr/$NAME.tar.gz"
	tar -zxvf $NAME.tar.gz
	cd $NAME
  
	# Configure
	./configure --prefix=/usr/local
  
	# Run 'make' with four threads
	make -j4
else
	echo "Using APR cache"
	cd $NAME
fi

# Install to OS
sudo make install
  
sudo ldconfig
echo "APR installed"
