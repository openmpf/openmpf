#!/bin/sh
set -ex

sudo add-apt-repository -y ppa:ubuntu-toolchain-r/test
sudo apt-get install -qq g++-4.8

export CXX="g++-4.8"

gcc --version

echo "GCC updated"
