#!/bin/sh
set -ex

NAME=activemq-cpp-library-3.9.0

if [ ! -d "$NAME" ]; then
	echo "Download and build ActiveMQ-CPP"

	# Download tar file and extract
	wget "https://archive.apache.org/dist/activemq/activemq-cpp/3.9.0/$NAME-src.tar.gz"
	tar -zxvf $NAME-src.tar.gz
	cd $NAME
  
	# Configure
	./autogen.sh
	./configure --disable-ssl --prefix=/usr/local
  
	# Run 'make' with four threads
	make -j4
else
	echo "Using ActiveMQ-CPP cache"
	cd $NAME
fi

# Install to OS
sudo make install
  
sudo ldconfig
echo "ActiveMQ-CPP installed"
