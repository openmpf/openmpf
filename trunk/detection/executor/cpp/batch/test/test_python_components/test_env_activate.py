import sys
from os.path import expanduser, expandvars

# Try to use version in repo before installed version, so that when run in a development environment the
# most up to date version of the component api is used. A developer also might run these tests before
# doing a full build and install.
# On Jenkins the first path will not exist, but that is okay because Jenkins does a full build and install
# before running these tests.
raw_paths = (
    "~/openmpf-projects/openmpf-python-component-sdk/detection/api",
    "~/mpf-sdk-install/python/site-packages",
    "$MPF_SDK_INSTALL_PATH/python/site-packages",
    "$MPF_HOME/python/site-packages"
)

sys.path[0:0] = (expanduser(expandvars(p)) for p in raw_paths)
