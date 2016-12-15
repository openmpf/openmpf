#!/bin/sh
set -ex

NAME=protobuf-2.5.0

if [ ! "$(ls -A $NAME)" ]; then
	# Cached dir is empty
	echo "Download and build Google Protobuf"

	# Download tar file and extract
	wget "https://github.com/google/protobuf/releases/download/v2.5.0/$NAME.tar.gz"
	tar -zxvf $NAME.tar.gz
	cd $NAME
  
	# Configure
	./configure --prefix=/usr/local
  
	# Run 'make' with four threads
	make -j4
else
	echo "Using Google Protobuf cache"
	cd $NAME
fi

# Install to OS
sudo make install
  
sudo ldconfig
echo "Google Protobuf installed"
