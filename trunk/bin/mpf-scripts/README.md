Install
==================
* The installation requires pip, a Python package manager. If you don't have pip installed you can
run `yum -y install python-pip`
* Run `sudo pip install path/to/mpf-scripts`

Tab Completion (optional)
-------------------
For tab completion of command line arguments add the following to your .bashrc:
`command -v register-python-argcomplete > /dev/null && eval "$(register-python-argcomplete mpf)"`


Usage
===================
The script currently supports status, start, stop, and restart.
e.g.

`mpf status`

`mpf start`

`mpf stop`

`mpf restart`

`mpf --help`

`mpf start --help`

There are optional arguments for specifying the location of binaries that are not on
your path. For more info run `mpf <command> --help`


Uninstall
=====================
Run `sudo pip uninstall mpf`