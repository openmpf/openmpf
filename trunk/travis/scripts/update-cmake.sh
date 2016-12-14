#!/bin/sh
set -ex

# Ubuntu 12.04.5 LTS comes with cmake 2.8.7, we need 2.8.11 or later
sudo add-apt-repository --yes ppa:kalakris/cmake
sudo apt-get update
sudo apt-get install cmake
cmake --version
