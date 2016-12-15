#!/bin/sh
set -ex

NAME=cmake-2.8.12.2

if [ ! "$(ls -A $NAME)" ]; then
	# Cached dir is empty
	echo "Download and build cmake"

	# Download tar file and extract
	wget "https://cmake.org/files/v2.8/cmake-2.8.12.2.tar.gz"
	tar -zxvf $NAME.tar.gz
	cd $NAME
  
	# Configure
	chmod +x *
	./configure --prefix=/usr/local
  
	# Run 'make' with four threads
	make -j4
else
	echo "Using cmake cache"
	cd $NAME
fi

# Install to OS
sudo make install
  
sudo ldconfig
echo "cmake installed"
