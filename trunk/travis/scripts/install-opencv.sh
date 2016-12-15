#!/bin/sh
set -ex

# OpenCV dependencies - Details available at: http://docs.opencv.org/trunk/doc/tutorials/introduction/linux_install/linux_install.html
sudo apt-get install -y build-essential
sudo apt-get install -y cmake git libgtk2.0-dev pkg-config libavcodec-dev libavformat-dev libswscale-dev
sudo apt-get install -y python-dev python-numpy libtbb2 libtbb-dev libjpeg-dev libpng-dev libtiff-dev libjasper-dev libdc1394-22-dev

# Download v3.0.0 .zip file and extract
wget "http://downloads.sourceforge.net/project/opencvlibrary/opencv-unix/2.4.9/opencv-2.4.9.zip?r=http%3A%2F%2Fopencv.org%2Fdownloads.html&ts=1467141570&use_mirror=heanet"
unzip -o opencv-2.4.9.zip
cd opencv-2.4.9
  
# Create a new 'build' folder
mkdir build
cd build
  
# Set build instructions for Ubuntu distro
cmake -D CMAKE_BUILD_TYPE=RELEASE -D CMAKE_INSTALL_PREFIX=/usr/local ..
  
# Run 'make' with four threads
make -j4
  
# Install to OS
sudo make install
  
# Add configuration to OpenCV to tell it where the library files are located on the file system (/usr/local/lib)
sudo sh -c 'echo "/usr/local/lib" > /etc/ld.so.conf.d/opencv.conf'
  
sudo ldconfig
echo "OpenCV installed."
