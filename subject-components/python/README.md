Overview
==================
The purpose of this image is to enable a developer to write a Python Subject Tracking Component for
OpenMPF that can be encapsulated within a Docker container. This isolates the execution environment
from the rest of OpenMPF, thereby providing greater freedom and portability. The
`openmpf_python_subject_build` and `openmpf_python_subject_executor` base images are designed to
work together in a multi-stage Docker build.

This image will:

- Register your component with the Workflow Manager.
- Execute your code using the OpenMPF subject tracking component executor.


Pull or build the base images
======================================================
Most developers will not need to build their own set of base images. Instead, they can pull them
from [Docker Hub](https://hub.docker.com/u/openmpf) as follows:

```bash
docker pull openmpf/openmpf_python_subject_build:latest
docker pull openmpf/openmpf_python_subject_executor:latest
```

Alternatively, these build steps assume that you've previously built the `openmpf_build` base image described
[here](https://github.com/openmpf/openmpf-docker/blob/master/README.md#create-the-openmpf-build-image):

```bash
cd /path/to/openmpf-docker/subject-components/python
docker build . --target build -t openmpf_python_subject_build
docker build . --target executor -t openmpf_python_subject_executor
```

How to use these images
===========================
The following steps assume you are using the default project structure for OpenMPF Python Subject
Tracking Components. Documentation for Python Subject Tracking Components can be found
[here](https://openmpf.github.io/docs/site/Python-Subject-Tracking-Component-API).

The [OR-Tools Subject Component](https://github.com/openmpf/openmpf-components/tree/master/python/OrToolsSubjectComponent)
is a good example of a Dockerized Python component.


### Create a Dockerfile in your Python Subject Tracking component project

You should put your Dockerfile in the component project's top level directory. For example:
```
MySubjectComponent
├── Dockerfile
├── plugin-files
│   └── descriptor
│       └── descriptor.json
├── pyproject.toml
└── my_subject_component
    └── __init__.py
```

The minimal Dockerfile is as follows. Note that if you built your own base images then you should
omit the `openmpf/` prefix on the `FROM` lines.

```dockerfile
# In first stage of the build we extend the openmpf_python_subject_build base image.
FROM openmpf/openmpf_python_subject_build:latest AS build_component

# If your component has external dependencies, you would add the commands necessary to download
# or build the dependencies here. Adding the dependencies prior the copying in your source code
# allows you to take advantage of the Docker build cache to avoid re-installing the dependencies
# every time your source code changes.
# e.g. RUN apt-get update && apt-get install -y gcc && rm -rf /var/lib/apt/lists/*
# e.g. RUN pip3 install --no-cache-dir 'opencv-python>=4.4.0' 'tensorflow>=2.1.0'


# Install your component in to your component's virtualenv (located at $COMPONENT_VIRTUALENV).
# The [install-component.sh](./install-component.sh) script is provided by the
# openmpf_python_subject_build base image.
RUN --mount=target=.,readwrite install-component.sh


# In the second stage of the build we extend the openmpf_python_subject_executor base image
FROM openmpf/openmpf_python_subject_executor:latest

# If your component has runtime dependencies that are not pip packages, you should install them
# here. Adding the dependencies prior to copying your component's build artifacts allows you to
# take advantage of the Docker build cache to avoid re-installing the dependencies every time your
# source code changes.

# Copy your component's virtualenv from the build stage. The install-component.sh script from the
# build stage installed your plugin code and its pip dependencies in the virtualenv located at
# $COMPONENT_VIRTUALENV.
COPY --from=build_component $COMPONENT_VIRTUALENV $COMPONENT_VIRTUALENV

# This copies over any files in your plugin's plugin-files directory.  Minimally, this will include
# your component's descriptor.
COPY --from=build_component $PLUGINS_DIR/MySubjectComponent $PLUGINS_DIR/MySubjectComponent
```

Your Dockerfile may use more than the two stages shown above, but the final stage in the Dockerfile
must be the `FROM openmpf_python_subject_executor:latest` stage.


### Build your component image

Run the following command, replacing `<component_name>` with the name of your component and
`<component_path>` with the path on the host file system to the component project's top level
directory:

```bash
docker build -t <component_name> <component_path>
```

For example: `docker build -t MySubjectTracking /path/to/MySubjectTracking`.

### Run your component

1. Start OpenMPF
2. Run the following command replacing `<component_name>` with the value provided in the build step.

```bash
docker run \
    --network openmpf_default \
    -v openmpf_shared_data:/opt/mpf/share \
    <component_name>
```
