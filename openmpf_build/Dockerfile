# syntax=docker/dockerfile:1.4

#############################################################################
# NOTICE                                                                    #
#                                                                           #
# This software (or technical data) was produced for the U.S. Government    #
# under contract, and is subject to the Rights in Data-General Clause       #
# 52.227-14, Alt. IV (DEC 2007).                                            #
#                                                                           #
# Copyright 2024 The MITRE Corporation. All Rights Reserved.                #
#############################################################################

#############################################################################
# Copyright 2024 The MITRE Corporation                                      #
#                                                                           #
# Licensed under the Apache License, Version 2.0 (the "License");           #
# you may not use this file except in compliance with the License.          #
# You may obtain a copy of the License at                                   #
#                                                                           #
#    http://www.apache.org/licenses/LICENSE-2.0                             #
#                                                                           #
# Unless required by applicable law or agreed to in writing, software       #
# distributed under the License is distributed on an "AS IS" BASIS,         #
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.  #
# See the License for the specific language governing permissions and       #
# limitations under the License.                                            #
#############################################################################

FROM ubuntu:20.04 AS apt_install

SHELL ["/bin/bash", "-o", "errexit", "-o", "pipefail", "-c"]

ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# Disable Python bytecode generation because it was causing build cache issues. It may slightly
# increase start up time, but once everything is imported, there is no difference in performance.
# If the bytecode files were critical, it is unlikely that the official Python Docker images would
# explicitly delete them.
ENV PYTHONDONTWRITEBYTECODE=1
ENV PIP_COMPILE=0

RUN --mount=type=tmpfs,target=/var/cache/apt \
    --mount=type=tmpfs,target=/var/lib/apt/lists  \
    --mount=type=tmpfs,target=/tmp \
    apt-get update; \
    apt-get upgrade -y; \
    DEBIAN_FRONTEND=noninteractive apt-get install --no-install-recommends -y \
        wget ca-certificates gnupg2 unzip xz-utils cmake make g++ libgtest-dev mediainfo libssl-dev \
        liblog4cxx-dev libboost-dev file openjdk-17-jdk-headless libgl1-mesa-glx \
        # libheif build deps
        libde265-dev git nasm ninja-build pkg-config \
        # OpenCV build deps
        libopenblas-dev liblapacke-dev libavcodec-dev libavcodec-extra libavformat-dev  \
        libavutil-dev libswscale-dev libavresample-dev libharfbuzz-dev libfreetype-dev \
        # Python build deps
        libsqlite3-dev libreadline-dev libbz2-dev liblzma-dev; \
    # Can't set up cuda repo at the beginning because it requires wget ca-certificates gnupg2
    wget -O- https://developer.download.nvidia.com/compute/cuda/repos/ubuntu2004/x86_64/3bf863cc.pub \
        | apt-key add -; \
    echo "deb https://developer.download.nvidia.com/compute/cuda/repos/ubuntu2004/x86_64 /" \
        > /etc/apt/sources.list.d/cuda.list; \
    apt-get update; \
    apt-get install -y --no-install-recommends cuda-minimal-build-11-4 libcufft-dev-11-4 \
        libnpp-dev-11-4 libcudnn8-dev=8.2.4.15-1+cuda11.4 libcudnn8=8.2.4.15-1+cuda11.4 \
        libcublas-dev-11-4; \
    # OpenCV doesn't use the statically compiled CUDA libraries except for libcudart and they are relatively large.
    find /usr/local/cuda/lib64/ -name '*.a' -not -name 'libcudart_static.a' -delete; \
    rm /usr/lib/x86_64-linux-gnu/libcudnn*.a; \
    ln --symbolic /usr/include/x86_64-linux-gnu/openblas-pthread/cblas.h /usr/include/cblas.h; \
    ln --symbolic /usr/bin/cmake /usr/bin/cmake3;


FROM apt_install AS ocv_install

RUN --mount=type=tmpfs,target=/tmp \
    mkdir /tmp/opencv-contrib; \
    wget -O- 'https://github.com/opencv/opencv_contrib/archive/4.9.0.tar.gz' \
        | tar --extract --gzip --directory /tmp/opencv-contrib; \
    mkdir /tmp/opencv; \
    cd  /tmp/opencv; \
    wget -O- 'https://github.com/opencv/opencv/archive/4.9.0.tar.gz' \
        | tar --extract --gzip; \
    cd opencv-4.9.0; \
    mkdir build; \
    cd build; \
    export OpenBLAS_HOME=/usr/lib/x86_64-linux-gnu/openblas-pthread; \
    cmake -DCMAKE_INSTALL_PREFIX:PATH='/opt/opencv-4.9.0' \
        -DCMAKE_BUILD_TYPE=Release \
        -DWITH_IPP=false \
        -DBUILD_EXAMPLES=false \
        -DBUILD_TESTS=false \
        -DBUILD_PERF_TESTS=false \
        -DWITH_CUDA=true \
        -DCUDA_ARCH_BIN=7.0 \
        -DCUDA_ARCH_PTX=7.0 \
        -DWITH_CUBLAS=true \
        -DWITH_CUDNN=true \
        -DOPENCV_DNN_CUDA=true \
        -DBUILD_CUDA_STUBS=true \
        -DOPENCV_EXTRA_MODULES_PATH=/tmp/opencv-contrib/opencv_contrib-4.9.0/modules \
        ..; \
    make --jobs "$(nproc)" install/strip; \
    ln --symbolic '/opt/opencv-4.9.0/include/opencv4/opencv2' /usr/local/include/opencv2;


FROM apt_install AS python_install

RUN --mount=type=tmpfs,target=/tmp <<eot
    mkdir /tmp/python
    cd /tmp/python
    wget -O- 'https://www.python.org/ftp/python/3.12.12/Python-3.12.12.tar.xz' | tar --extract --xz
    cd Python-3.12.12
    ./configure --enable-optimizations --with-lto --enable-shared --disable-test-modules --without-static-libpython
    make --jobs "$(nproc)"
    make install
    ln --symbolic /usr/local/lib/libpython3.12.so.1.0 /lib/x86_64-linux-gnu/libpython3.12.so.1.0

    # find command taken from official Python Dockerfile
    find /usr/local/lib/python3.12 -depth \
		\( \
			\( -type d -a \( -name test -o -name tests -o -name idle_test \) \) \
			-o \( -type f -a \( -name '*.pyc' -o -name '*.pyo' -o -name 'libpython*.a' \) \) \
		\) -exec rm -rf '{}' + ;
eot



FROM apt_install AS install_dependencies

RUN --mount=type=tmpfs,target=/tmp <<eot
    mkdir /tmp/abseil
    wget -O- 'https://github.com/abseil/abseil-cpp/releases/download/20240722.0/abseil-cpp-20240722.0.tar.gz' \
        | tar --extract --gzip --directory /tmp/abseil;

    mkdir /tmp/protobuf
    cd /tmp/protobuf
    wget -O- 'https://github.com/protocolbuffers/protobuf/releases/download/v28.3/protobuf-28.3.tar.gz' \
        | tar --extract --gzip;

    cd protobuf-28.3
    mkdir build
    cd build
    cmake \
        -DCMAKE_CXX_STANDARD=17 \
        -DCMAKE_POSITION_INDEPENDENT_CODE=ON \
        -DABSL_ROOT_DIR=/tmp/abseil/abseil-cpp-20240722.0 \
        -Dprotobuf_BUILD_TESTS=OFF \
        ..
    cmake --build . --parallel "$(nproc)"
    cmake --install . --strip
eot

RUN --mount=type=tmpfs,target=/tmp \
    mkdir /tmp/activemq-cpp; \
    cd /tmp/activemq-cpp; \
    wget -O- https://dlcdn.apache.org/activemq/activemq-cpp/3.9.5/activemq-cpp-library-3.9.5-src.tar.gz \
        | tar --extract --gzip; \
    cd activemq-cpp-library-3.9.5; \
    ./configure; \
    make --jobs "$(nproc)" install-strip;

RUN wget -O- 'https://johnvansickle.com/ffmpeg/releases/ffmpeg-release-amd64-static.tar.xz' \
        | tar --extract --xz --directory /usr/local/bin --strip-components 1 --wildcards \
            'ffmpeg-*-amd64-static/ffmpeg' 'ffmpeg-*-amd64-static/ffprobe';


RUN --mount=type=tmpfs,target=/tmp \
    mkdir /tmp/pngdefry; \
    cd /tmp/pngdefry; \
    wget -O- 'https://github.com/openmpf/pngdefry/archive/v1.2.tar.gz' \
        | tar --extract --gzip; \
    cd pngdefry-1.2; \
    gcc pngdefry.c -o /usr/local/bin/pngdefry;


RUN wget -O- 'https://archive.apache.org/dist/maven/maven-3/3.3.3/binaries/apache-maven-3.3.3-bin.tar.gz' \
        | tar --extract --gzip --directory /opt; \
    ln --symbolic /opt/apache-maven-3.3.3/bin/mvn /usr/local/bin;


RUN --mount=type=tmpfs,target=/tmp \
    mkdir /tmp/libheif; \
    cd /tmp/libheif; \
    wget -O- https://github.com/strukturag/libheif/archive/refs/tags/v1.20.2.tar.gz \
        | tar --extract --gzip; \
    cd libheif-1.20.2/third-party; \
    bash aom.cmd; \
    cd ..; \
    mkdir build; \
    cd build; \
    PKG_CONFIG_PATH="/tmp/libheif/libheif-1.20.2/third-party/aom/dist/lib/pkgconfig" \
        cmake3 -DCMAKE_INSTALL_PREFIX=/usr -DWITH_EXAMPLES=false -DWITH_AOM_DECODER=true -DENABLE_PLUGIN_LOADING=false -DCMAKE_BUILD_TYPE=Release ..; \
    make --jobs "$(nproc)" install/strip; \
    cp heifio/libheifio.a /usr/lib64; \
    cd ../heifio; \
    mkdir -p /usr/include/libheif/heifio; \
    cp *.h /usr/include/libheif/heifio;


COPY --from=ocv_install /opt/opencv-4.9.0 /opt/opencv-4.9.0

RUN <<eot
    ln --symbolic /opt/opencv-4.9.0/include/opencv4/opencv2 /usr/local/include/opencv2
    echo /opt/opencv-4.9.0/lib > /etc/ld.so.conf.d/opencv.conf
    ldconfig
eot

COPY --from=python_install \
    /usr/local/bin/python3.12 /usr/local/bin/python3.12-config \
    /usr/local/bin/pip3.12 /usr/local/bin/pip3 \
    /usr/local/bin/

COPY --from=python_install /usr/local/lib/python3.12 /usr/local/lib/python3.12

COPY --from=python_install /usr/local/lib/libpython3.12.so.1.0 /usr/local/lib/

# The Python headers are needed to build Python libraries with C extensions.
COPY --from=python_install /usr/local/include/python3.12 /usr/local/include/python3.12

RUN <<eot
    cd /usr/local/bin
    ln --symbolic python3.12 python3
    ln --symbolic python3.12-config python3-config
    cd /usr/local/lib
    ln --symbolic libpython3.12.so.1.0 libpython3.12.so
    ln --symbolic /usr/local/lib/libpython3.12.so.1.0 /lib/x86_64-linux-gnu/libpython3.12.so.1.0
eot



FROM install_dependencies AS devcontainer

ARG USERNAME=mpf
# Run `id -u` to check your user id. If it is not equal to 1000, pass in the USER_UID build arg.
# Example with bake:
# USER_UID=$(id -u) docker bake --allow=fs.read=.. -f docker-bake.hcl openmpf_dev
# Example with build:
# docker build -f openmpf_build/Dockerfile .. --target devcontainer -t openmpf_dev --build-arg USER_UID=$(id -u)
ARG USER_UID=1000
ARG USER_GID=$USER_UID

RUN <<eot
    groupadd --gid $USER_GID "$USERNAME"
    useradd --uid $USER_UID --gid $USER_GID --create-home --shell /bin/bash --no-log-init "$USERNAME"
    mkdir -p /home/mpf/openmpf-projects/openmpf
    chown -R "$USERNAME:$USERNAME" /home/mpf/openmpf-projects
eot

WORKDIR /home/mpf/openmpf-projects/openmpf

RUN --mount=target=/home/mpf/openmpf-projects,readwrite \
    --mount=type=cache,id=mvn_cache,target=/root/.m2/repository,sharing=private <<eot
    export PIP_NO_CACHE_DIR=1
    mvn \
        install \
        -DskipTests \
        -Dmaven.test.skip=true \
        -DskipITs \
        -Dcomponents.build.components='' \
        -Dstartup.auto.registration.skip=false \
        -Dcomponents.build.parallel.builds="$(nproc)" \
        -Dcomponents.build.make.jobs="$(nproc)";

    cp --recursive /root/.m2 "/home/$USERNAME/"
    cp --recursive /root/mpf-sdk-install "/home/$USERNAME/"
    cp --recursive /home/mpf/openmpf-projects/openmpf/trunk/install/. /opt/mpf/
    mkdir /opt/mpf/share
    chown --recursive "$USERNAME:$USERNAME" \
        "/home/$USERNAME/.m2" "/home/$USERNAME/mpf-sdk-install" /opt/mpf
eot

COPY openmpf-docker/openmpf_build/dev/devcontainer-entrypoint.sh /scripts/
RUN chmod a+rx /scripts/devcontainer-entrypoint.sh

USER $USERNAME

ENV MPF_HOME=/opt/mpf
ENV REDIS_HOST=redis
ENV JDBC_URL=jdbc:postgresql://db:5432/mpf
ENV THIS_MPF_NODE=workflow-manager
ENV MPF_LOG_PATH=/opt/mpf/logs
ENV spring_profiles_active=docker
ENV JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64

ENTRYPOINT [ "/scripts/devcontainer-entrypoint.sh" ]

LABEL devcontainer.metadata='[{ "overrideCommand": false }]'


FROM install_dependencies

COPY . /home/mpf/openmpf-projects

# Markup uses the emoji font for icons.
RUN mkdir --parents /usr/share/fonts/google-noto-emoji; \
    cp /home/mpf/openmpf-projects/openmpf-docker/openmpf_build/noto-emoji-font/NotoEmoji-Regular.ttf \
        /usr/share/fonts/google-noto-emoji/

ENV MPF_HOME=/home/mpf/openmpf-projects/openmpf/trunk/install

WORKDIR /home/mpf/openmpf-projects/openmpf

RUN --mount=type=cache,id=mvn_cache,target=/root/.m2/repository,sharing=private \
    --mount=type=tmpfs,target=/tmp \
    --mount=type=tmpfs,target=/root/.cache \
    mvn package \
        -DskipTests -Dmaven.test.skip=true -DskipITs \
        -Dcomponents.build.components='' \
        -Dstartup.auto.registration.skip=false \
        -Dcomponents.build.parallel.builds="$(nproc)" \
        -Dcomponents.build.make.jobs="$(nproc)"; \
    mkdir --parents /build-artifacts/mvn/mpf; \
    # Move mpf artifacts out of Maven repo so they won't be in the cache mount when another build is started
    mv /root/.m2/repository/org/mitre/mpf /build-artifacts/mvn;


ARG RUN_TESTS=false
RUN \[ "${RUN_TESTS,,}" = true ] || exit 0; \
    export LD_LIBRARY_PATH=$MPF_HOME/lib:LD_LIBRARY_PATH; \
    # Use nullglob to avoid entering for loop when no glob matches.
    # Use globstar to enable ** globs.
    shopt -s nullglob globstar; \
    for build_root in /root/mpf-component-build /home/mpf/openmpf-projects/openmpf/trunk/build; do \
        cd "$build_root"; \
        for test_executable in **/test/*Test; do \
            cd "$(dirname "$test_executable")"; \
            ./"$(basename "$test_executable")"; \
            cd "$build_root"; \
        done; \
    done;

WORKDIR /build-artifacts

# These two can't be symlinked because Docker was using the build cache even though the files had changed.
# It appears that Docker COPY has an issue when using symlinks with wild cards.
RUN mkdir markup; \
    cp /home/mpf/openmpf-projects/openmpf/trunk/markup/target/*.jar markup/; \
    mkdir java-executor; \
    cp /home/mpf/openmpf-projects/openmpf/trunk/detection/executor/java/target/*.jar java-executor;

RUN ln --symbolic /home/mpf/openmpf-projects/openmpf/trunk/workflow-manager/target/workflow-manager; \
    ln --symbolic /home/mpf/openmpf-projects/openmpf/trunk/bin/mpf-scripts; \
    ln --symbolic /home/mpf/openmpf-projects/openmpf/trunk/install; \
    ln --symbolic /root/mpf-sdk-install;

RUN cp -r /home/mpf/openmpf-projects/openmpf-docker/openmpf_build/scripts /scripts;

LABEL org.label-schema.build-date="" \
      org.label-schema.license="Mixed" \
      org.label-schema.name="OpenMPF Build Environment" \
      org.label-schema.schema-version="1.0" \
      org.label-schema.url="https://openmpf.github.io" \
      org.label-schema.vcs-url="https://github.com/openmpf/openmpf-projects" \
      org.label-schema.vendor="MITRE"
