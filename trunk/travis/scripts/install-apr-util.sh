#!/bin/sh
set -ex

NAME=apr-util-1.5.4

if [ ! -d "$NAME" ]; then
	echo "Download and build APR-util"

	# Download tar file and extract
	wget "http://archive.apache.org/dist/apr/$NAME.tar.gz"
	tar -zxvf $NAME.tar.gz
	cd $NAME
  
	# Configure
	./configure --with-apr=/usr/local --prefix=/usr/local
  
	# Run 'make' with four threads
	make -j4
else
	echo "Using APR-util cache"
	cd $NAME
fi

# Install to OS
sudo make install
  
sudo ldconfig
echo "APR-util installed"
