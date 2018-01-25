#############################################################################
# NOTICE                                                                    #
#                                                                           #
# This software (or technical data) was produced for the U.S. Government    #
# under contract, and is subject to the Rights in Data-General Clause       #
# 52.227-14, Alt. IV (DEC 2007).                                            #
#                                                                           #
# Copyright 2018 The MITRE Corporation. All Rights Reserved.                #
#############################################################################

#############################################################################
# Copyright 2018 The MITRE Corporation                                      #
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

import os
import shutil
import sys

import argh

import mpf_sys
import mpf_util


@mpf_util.env_arg('--mpf-home', 'MPF_HOME',
                  help='MPF home directory.')
@mpf_util.env_arg('--mpf-log-path', 'MPF_LOG_PATH')
@argh.arg('-f', '--force',
          help='Do not prompt user before deleting data')
@argh.arg('--delete-uploaded-media',
          help='Delete user uploaded media during clean')
@argh.arg('--delete-logs',
          help='Delete log files during clean')
@mpf_util.sql_args
@argh.arg('-v', '--verbose', default=False,
          help='Show output from called commands')
@argh.arg('--activemq-bin', default='/opt/activemq/bin/activemq',
          help='path to ActiveMQ binary')
@mpf_util.env_arg('--activemq-data', 'ACTIVEMQ_DATA', default='/opt/activemq/data',
                  help='path to ActiveMQ data directory')
@argh.arg('--catalina', default='/opt/apache-tomcat/bin/catalina.sh',
          help='path to catalina.sh')
@argh.arg('--node-manager-port', default=8008,
          help='port number that the Node Manager listens on')
@argh.arg('--workflow-manager-url', default='http://localhost:8080/workflow-manager',
          help='Url to Workflow Manager')
@mpf_util.env_arg('--catalina-pid-file', 'CATALINA_PID', default='/tmp/mpf-script/catalina.pid',
                  help='Path to catalina pid file')
def clean(mpf_home=None, mpf_log_path=None, force=False, delete_uploaded_media=False,
          delete_logs=False, sql_host='localhost', sql_user='root', sql_password='password',
          **opt_args):
    """ Reverts MPF to a new install state """

    sys_config = mpf_sys.MpfConfig(skip_redis=True, **opt_args)
    ensure_mpf_stopped(sys_config)
    if not force and not prompt_user(delete_uploaded_media, delete_logs, sys_config.kahadb_dir):
        print 'Clean aborted by user'
        sys.exit(1)

    ensure_sql_is_running(sys_config)

    truncate_tables(sql_host, sql_user, sql_password)
    delete_folder_contents(mpf_home, delete_uploaded_media, delete_logs, mpf_log_path)
    if sys_config.kahadb_dir:
        delete_children(sys_config.kahadb_dir)
    print 'MPF clean complete'


must_be_stopped_dependencies = (mpf_sys.ActiveMqManager, mpf_sys.NodeManagerManager,
                                mpf_sys.TomcatManager)


def ensure_mpf_stopped(sys_config):
    # Construct each manager
    managers = (mFn(sys_config) for mFn in must_be_stopped_dependencies)
    # Get list of running dependencies
    still_running = [m.dependency_name() for m in managers if m.status()]
    if still_running:
        raise MpfStillRunningError(still_running)


def ensure_sql_is_running(sys_config):
    sql_manager = mpf_sys.MySqlManager(sys_config)
    if sql_manager.status():
        return
    print 'Starting MySQL service...'
    sql_manager.start()
    print


def prompt_user(delete_uploaded_media, delete_logs, kahadb_dir):
    print 'The following items will be deleted:'
    print '\t-Job information and results'
    print '\t-Pending job requests'
    print '\t-Marked up media files'
    if kahadb_dir:
        print '\t-ActiveMQ data'
    if delete_uploaded_media:
        print '\t-Uploaded media'
    if delete_logs:
        print '\t-Log files'

    if not kahadb_dir:
        print mpf_util.MsgUtil.yellow('(no persistent ActiveMQ data found)')
    try:
        response = raw_input('Do you want to continue? [y/N]: ').lower()
    except EOFError:
        # user pressed ctrl-d
        return False

    return response in ('y', 'yes')


def truncate_tables(sql_host, sql_user, sql_password):
    with mpf_util.sql_connection(sql_host, sql_user, sql_password) as conn:
        conn.execute('DELETE FROM job_request')
        conn.execute('DELETE FROM markup_result')


ALWAYS_DELETE_FOLDERS = ('artifacts', 'markup', 'output-objects')


def delete_folder_contents(mpf_home, delete_uploaded_media, delete_logs, mpf_log_path):
    mpf_share_dir = os.path.join(mpf_home, 'share')
    for folder in ALWAYS_DELETE_FOLDERS:
        delete_children(os.path.join(mpf_share_dir, folder))
    if delete_uploaded_media:
        delete_children(os.path.join(mpf_share_dir, 'remote-media'))
    if delete_logs:
        delete_children(mpf_log_path)


def delete_children(folder):
    for entry in os.listdir(folder):
        path = os.path.join(folder, entry)
        if os.path.isfile(path):
            os.remove(path)
        else:
            shutil.rmtree(path)


class MpfStillRunningError(mpf_util.MpfError):
    MESSAGE_FORMAT = 'The following dependencies must be stopped before cleaning MPF: %s. ' \
                     'Try running "mpf stop"'

    def __init__(self, running_dependencies):
        dependency_list = ', '.join(running_dependencies)
        super(MpfStillRunningError, self).__init__(self.MESSAGE_FORMAT % dependency_list)


COMMANDS = (clean,)
