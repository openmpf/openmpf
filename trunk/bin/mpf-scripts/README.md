Install
==================
* Install [Python 3.12](https://www.python.org/downloads).
* Run `sudo pip3 install path/to/mpf-scripts`

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
Run `sudo pip3 uninstall mpf`
