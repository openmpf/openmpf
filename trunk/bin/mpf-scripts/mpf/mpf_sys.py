#############################################################################
# NOTICE                                                                    #
#                                                                           #
# This software (or technical data) was produced for the U.S. Government    #
# under contract, and is subject to the Rights in Data-General Clause       #
# 52.227-14, Alt. IV (DEC 2007).                                            #
#                                                                           #
# Copyright 2023 The MITRE Corporation. All Rights Reserved.                #
#############################################################################

#############################################################################
# Copyright 2023 The MITRE Corporation                                      #
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

import abc
import os
import signal
import socket
import subprocess
import time
import urllib.error
import urllib.parse
import urllib.request

import argh

from . import mpf_util


class BaseMpfSystemDependencyManager(abc.ABC):

    def __init__(self, mpf_config):
        if isinstance(mpf_config, bool):
            self._config = None
            self._shell = ShellHelper(mpf_config)
        else:
            self._config = mpf_config
            self._shell = ShellHelper(mpf_config.verbose)

    def status(self):
        """ Determines whether the component is currently running

        Returns:
            bool: True if the component is currently running
        Raises:
            subprocess.CalledProcessError: The status command returned an unrecognized exit code
        """
        try:
            return self._run_status_command() == 0
        except subprocess.CalledProcessError as err:
            if err.returncode == self._not_running_status_code():
                return False
            raise

    def status_string(self):
        """ Creates a status message that will be displayed to the user
        """
        try:
            if self.status():
                return Messages.running(self.dependency_name())
            else:
                return Messages.not_running(self.dependency_name())
        except subprocess.CalledProcessError:
            return Messages.error_during_status_check(self.dependency_name())

    def start(self):
        """ Starts the component if it isn't already running.
        """
        if self.status():
            print(Messages.running(self.dependency_name()))
            return

        try:
            self._run_start_command()
        except subprocess.CalledProcessError as err:
            if err.output:
                raise FailedToStartError(self.dependency_name(),
                                         self.dependency_name() + ' failed to start:\n' + err.output)
            else:
                raise FailedToStartError(self.dependency_name())

        time.sleep(.2)

        if self.status():
            print(Messages.started(self.dependency_name()))
        else:
            raise FailedToStartError(self.dependency_name())

    def stop(self):
        """ Stops the component if it is running
        """
        if not self.status():
            print(Messages.not_running(self.dependency_name()))
            return

        try:
            self._run_stop_command()
        except subprocess.CalledProcessError as err:
            if err.output:
                raise FailedToStopError(self.dependency_name(),
                                        self.dependency_name() + ' failed to stop:\n' + err.output)
            else:
                raise FailedToStopError(self.dependency_name())

        time.sleep(.2)

        if self.status():
            raise FailedToStopError(self.dependency_name())
        else:
            print(Messages.stopped(self.dependency_name()))

    @abc.abstractmethod
    def dependency_name(self):
        """ Gets the name of component that will be used in status messages
        """
        raise NotImplementedError()

    @abc.abstractmethod
    def _run_status_command(self):
        """ Executes the shell command to get the component's status
        """
        raise NotImplementedError()

    @abc.abstractmethod
    def _not_running_status_code(self):
        """ Exit code from _runStatusCommand that indicates the component is not running
        """
        raise NotImplementedError()

    @abc.abstractmethod
    def _run_start_command(self):
        """ Executes the shell command that starts the component
        """
        raise NotImplementedError()

    @abc.abstractmethod
    def _run_stop_command(self):
        """ Executes the shell command that starts the component
        """
        raise NotImplementedError()


class PostgresManager(BaseMpfSystemDependencyManager):
    SERVICE_NAME = 'postgresql'

    def dependency_name(self):
        return 'PostgreSQL'

    def _run_status_command(self):
        return self._shell.service_status(PostgresManager.SERVICE_NAME)

    def _not_running_status_code(self):
        return ShellHelper.SERVICE_STOPPED_CODE

    def _run_start_command(self):
        self._shell.start_service(PostgresManager.SERVICE_NAME)

    def _run_stop_command(self):
        self._shell.stop_service(PostgresManager.SERVICE_NAME)


class RedisManager(BaseMpfSystemDependencyManager):
    SERVICE_NAME = 'redis-server'

    def __init__(self, mpf_config):
        super(RedisManager, self).__init__(mpf_config)
        self._is_service = self._config.redis_is_service

    def dependency_name(self):
        return 'Redis'

    def _run_status_command(self):
        if self._is_service:
            return self._shell.service_status(RedisManager.SERVICE_NAME)
        else:
            return self._shell.check_call((self._config.redis_cli, 'info'))

    def _not_running_status_code(self):
        if self._is_service:
            return ShellHelper.SERVICE_STOPPED_CODE
        else:
            return 1

    def _run_start_command(self):
        if self._is_service:
            self._shell.start_service(RedisManager.SERVICE_NAME)
        else:
            self._shell.check_call(('sudo', self._config.redis_server, self._config.redis_conf))

    def _run_stop_command(self):
        if self._is_service:
            self._shell.stop_service(RedisManager.SERVICE_NAME)
        else:
            self._shell.check_call((self._config.redis_cli, 'flushall'))
            self._shell.check_call((self._config.redis_cli, 'shutdown'))


class NodeManagerManager(BaseMpfSystemDependencyManager):
    SERVICE_NAME = 'node-manager'
    RETRIES = 4
    DELAY = 5

    def dependency_name(self):
        return 'Node Manager'

    def status_string(self):
        if self._config.local_only:
            return super(NodeManagerManager, self).status_string()

        statuses = self._remote_status()
        if all(s for h, s in statuses):
            return Messages.running(self.dependency_name())
        elif all(not s for h, s in statuses):
            return Messages.not_running(self.dependency_name())

        status_msg = [Messages.some_running(self.dependency_name())]
        for host, is_running in sorted(statuses):
            if is_running:
                status_msg.append('\t' + Messages.running(host))
            else:
                status_msg.append('\t' + Messages.not_running(host))
        return '\n'.join(status_msg)

    def status(self):
        if self._config.local_only:
            return self._local_status()
        else:
            statuses = self._remote_status()
            return any(s for h, s in statuses)

    def _local_status(self):
        if self._shell.call(('systemctl', 'status', NodeManagerManager.SERVICE_NAME)) == 0:
            return True
        try:
            self._shell.get_pid_bound_to_port(self._config.node_manager_port)
            return True
        except subprocess.CalledProcessError:
            return False

    def _remote_status(self):
        try:
            self._run_remote_ansible('status')
            return ('', True),
        except subprocess.CalledProcessError as err:
            if 'Loaded: not-found (Reason: No such file or directory)' in err.output:
                print(NodeManagerManager.SERVICE_NAME, 'does not appear to be installed')
                raise
            lines = (l.split() for l in err.output.splitlines())
            statuses = ((lps[0], lps[2] == 'SUCCESS') for lps in lines)
            return tuple(statuses)

    def start(self):
        if self._config.local_only:
            super(NodeManagerManager, self).start()
            return

        statuses = self._remote_status()
        if all(s for h, s in statuses):
            print(Messages.running(self.dependency_name()))
            return

        try:
            self._run_start_command()
        except subprocess.CalledProcessError as err:
            if err.output:
                raise FailedToStartError(self.dependency_name(),
                                         self.dependency_name() + ' failed to start:\n' + err.output)
            else:
                raise FailedToStartError(self.dependency_name())

        time.sleep(.2)

        if all(s for h, s in self._remote_status()):
            print(Messages.started(self.dependency_name()))
        else:
            raise FailedToStartError(self.dependency_name())

    def _run_start_command(self):
        if self._config.local_only:
            self._shell.start_service(NodeManagerManager.SERVICE_NAME)
        else:
            self._run_remote_ansible('start')

    def _run_stop_command(self):
        if self._config.local_only:
            self._run_local_stop_command()
        else:
            self._run_remote_ansible('stop')

    def _run_local_stop_command(self):
        if self._shell.service_status(NodeManagerManager.SERVICE_NAME) == 0:
            self._shell.stop_service(NodeManagerManager.SERVICE_NAME)
            return

        # Node manager wasn't started through the service command
        pid = self._shell.get_pid_bound_to_port(self._config.node_manager_port)
        os.kill(pid, signal.SIGINT)
        for i in range(self.RETRIES):
            if not self._shell.process_is_running(pid):
                return
            time.sleep(self.DELAY)

    def _run_remote_ansible(self, action):
        # e.g. sudo su --login mpf --command "ansible --user mpf mpf-child --args 'service node-manager status' --become --one-line"
        remote_cmd = 'service %s %s' % (NodeManagerManager.SERVICE_NAME, action)
        ansible_cmd = "ansible --user mpf mpf-child --args '%s' --become --one-line" % remote_cmd
        return self._shell.check_output(('sudo', 'su', '--login', 'mpf', '--command', ansible_cmd))

    def _run_status_command(self):
        raise NotImplementedError()

    def _not_running_status_code(self):
        raise NotImplementedError()



class WorkflowManagerManager(BaseMpfSystemDependencyManager):
    def dependency_name(self):
        return 'Workflow Manager'

    def status(self):
        request = urllib.request.Request(self._config.wfm_url + '/login')
        request.get_method = lambda: 'HEAD'
        try:
            with urllib.request.urlopen(request):
                return True
        except (urllib.error.HTTPError, urllib.error.URLError, socket.error):
            return False

    def stop(self):
        if self.status():
            # Workflow Manager starts synchronously and exits when ctrl-c is pressed, but we
            # don't want to shutdown the other dependencies while it is still running.
            raise mpf_util.MpfError('Workflow Manager must be manually stopped.')

    def start(self):
        if self.status():
            print(Messages.running(self.dependency_name()))
            return

        with subprocess.Popen(
                ('mvn', 'spring-boot:run', '-Dstartup.auto.registration.skip=false'),
                cwd=self._config.wfm_project) as proc:
            try:
                rv = proc.wait()
            except KeyboardInterrupt:
                # Need to wait after KeyboardInterrupt because Python will finish handling ctrl-c
                # before Workflow Manager finishes shutting down.
                rv = proc.wait()
            if rv != 0:
                raise FailedToStartError(self.dependency_name())

    def _not_running_status_code(self):
        raise NotImplementedError()

    def _run_status_command(self):
        raise NotImplementedError()

    def _run_stop_command(self):
        raise NotImplementedError()

    def _run_start_command(self):
        raise NotImplementedError()


class MpfConfig:
    def __init__(self, verbose=False, redis_server_bin=None, redis_cli_bin=None, redis_conf=None,
                 node_manager_port=None, workflow_manager_url=None, wfm_project=None,
                 local_only=False, skip_redis=False, skip_node_manager=False, skip_wfm=False, **_):

        self.verbose = verbose
        self._shell = ShellHelper(self.verbose)

        if not skip_redis:
            self.redis_is_service = self._shell.service_exists(RedisManager.SERVICE_NAME)
            if not self.redis_is_service:
                self.redis_server = redis_server_bin
                self._verify_exists('Redis', self.redis_server, '--redis-server-bin')

                self.redis_cli = redis_cli_bin
                self._verify_exists('Redis', self.redis_cli, '--redis-cli-bin')

                self.redis_conf = redis_conf
                self._verify_exists('Redis', self.redis_conf, '--redis-conf')

        self.node_manager_port = node_manager_port
        if not skip_node_manager:
            if local_only or not self._shell.executable_exists('ansible'):
                self.local_only = True
            else:
                self.child_nodes = self._get_child_nodes()
                self.local_only = self._only_node_manager_is_local(self.child_nodes)

        if not skip_wfm:
            self.wfm_url = workflow_manager_url
            self.wfm_project = os.path.expandvars(os.path.expanduser(wfm_project))
            if not os.path.isfile(os.path.join(self.wfm_project, 'pom.xml')):
                raise mpf_util.MpfError(
                    f'Expected to find a directory containing a pom.xml at "{self.wfm_project}". '
                    'Try setting --wfm-project.')


    def _verify_exists(self, component, executable, cmd_line_arg):
        if not self._shell.executable_exists(executable):
            raise UnableToFindExecutableError(component, cmd_line_arg)

    @staticmethod
    def _only_node_manager_is_local(child_nodes):
        # Running commands through ansible is much slower than running them normally,
        # so if the only host is localhost just run the command normally.
        if len(child_nodes) > 1:
            return False
        if len(child_nodes) == 0:
            return True

        listed_host = child_nodes[0]
        if listed_host == 'localhost' or listed_host == 'localhost.localdomain':
            return True

        local_host_name, local_aliases, _ = socket.gethostbyname_ex(socket.gethostname())
        return listed_host == local_host_name or listed_host in local_aliases

    def _get_child_nodes(self):
        cmd_output = self._shell.check_output(('ansible', 'mpf-child', '--list-hosts'))
        lines = (l.strip() for l in cmd_output.splitlines() if l.strip())
        # Discard the first line because it is the host count. The rest are the hosts
        next(lines)
        return tuple(lines)


class Messages(object):
    @staticmethod
    def running(component):
        return Messages._create_msg(component, mpf_util.MsgUtil.green('Running'))

    @staticmethod
    def not_running(component):
        return Messages._create_msg(component, mpf_util.MsgUtil.red('Not Running'))

    @staticmethod
    def started(component):
        return Messages._create_msg(component, mpf_util.MsgUtil.green('Started'))

    @staticmethod
    def stopped(component):
        return Messages._create_msg(component, mpf_util.MsgUtil.red('Stopped'))

    @staticmethod
    def some_running(component):
        return Messages._create_msg(component, mpf_util.MsgUtil.yellow('Some Running'))

    @staticmethod
    def error_during_status_check(component):
        return Messages._create_msg(component, mpf_util.MsgUtil.red('Unknown. An error occurred while checking'))

    @staticmethod
    def _create_msg(component, action):
        required_len = 17
        spaces = ' ' * (required_len - len(component))
        return f'{component}:{spaces}[  {action}  ]'



class FailedToStartError(mpf_util.MpfError):
    def __init__(self, component, message=None):
        if message:
            super(FailedToStartError, self).__init__(message)
        else:
            super(FailedToStartError, self).__init__('%s failed to start' % component)


class FailedToStopError(mpf_util.MpfError):
    def __init__(self, component, message=None):
        if message:
            super(FailedToStopError, self).__init__(message)
        else:
            super(FailedToStopError, self).__init__('%s failed to stop' % component)


class UnableToFindExecutableError(mpf_util.MpfError):
    def __init__(self, component, cmd_line_setting):
        super(UnableToFindExecutableError, self).__init__(
            'Could not find executable for %s. Try setting %s' % (component, cmd_line_setting))


class ShellHelper:
    SERVICE_STOPPED_CODE = 3

    def __init__(self, verbose=False):
        self._verbose = verbose
        if verbose:
            self._shellKwArgs = {}
        else:
            self._shellKwArgs = {'stdout': subprocess.DEVNULL, 'stderr': subprocess.DEVNULL}

    def call(self, args):
        return subprocess.call(args, **self._shellKwArgs)

    def check_call(self, args, env=None):
        if env:
            sub_env = os.environ.copy()
            sub_env.update(env)
            return subprocess.check_call(args, env=sub_env, **self._shellKwArgs)
        else:
            return subprocess.check_call(args, **self._shellKwArgs)

    def check_output(self, args):
        if self._verbose:
            return subprocess.check_output(args, text=True)
        else:
            return subprocess.check_output(args, text=True, stderr=subprocess.DEVNULL)

    def is_on_path(self, executable):
        return self.call(('which', executable)) == 0

    def executable_exists(self, executable):
        return self.is_on_path(executable) or os.path.isfile(executable)

    def get_pid_bound_to_port(self, port):
        output = self.check_output(('sudo', 'fuser', '-n', 'tcp', str(port)))
        return int(output)

    def process_is_running(self, pid):
        return self.call(('ps', str(pid))) == 0

    def service_exists(self, service_name):
        try:
            self.check_call(('systemctl', 'status', service_name))
            return True
        except subprocess.CalledProcessError as err:
            if err.returncode == 3:
                # Service exists, but is not running.
                return True
            if err.returncode == 4:
                return False
            raise

    def service_status(self, service_name):
        return self.check_call(('systemctl', 'status', service_name))

    def start_service(self, service_name):
        self.check_call(('sudo', 'systemctl', 'start', service_name))

    def stop_service(self, service_name):
        self.check_call(('sudo', 'systemctl', 'stop', service_name))


sys_args = mpf_util.arg_group(
    argh.arg('-v', '--verbose', default=False, help='Show output from called commands'),

    argh.arg('--redis-server-bin', default='redis-server', help='path to redis-server binary'),

    argh.arg('--redis-cli-bin', default='redis-cli', help='path to redis-cli binary'),

    argh.arg('--redis-conf', default='/etc/redis/redis.conf', help='path to redis.conf file'),

    argh.arg('--node-manager-port', default=8008,
             help='port number that the Node Manager listens on'),

    argh.arg('--local-only', '-l', default=False),

    argh.arg('--workflow-manager-url', default='http://localhost:8080',
             help='Url to Workflow Manager'),
    argh.arg('--wfm-project', default='~/openmpf-projects/openmpf/trunk/workflow-manager',
             help='Path to the Workflow Manager Maven project.'),

    argh.arg('--skip-sql', '--xsql', default=False, help='Exclude SQL from command'),
    argh.arg('--skip-redis', '--xrds', default=False, help='Exclude Redis from command'),
    argh.arg('--skip-node-manager', '--xnm', default=False, help='Exclude Node Manager from command'),
    argh.arg('--skip-wfm', '--xwfm', default=False, help='Exclude Workflow Manager from command')
)


def filtered_start_up_order(skip_sql, skip_redis, skip_node_manager, skip_wfm, **_):
    if not skip_sql:
        yield PostgresManager
    if not skip_redis:
        yield RedisManager
    if not skip_node_manager:
        yield NodeManagerManager
    if not skip_wfm:
        yield WorkflowManagerManager


@sys_args
def status(**opt_args):
    """ Displays the status of each MPF dependency """
    config = MpfConfig(**opt_args)
    for manager in filtered_start_up_order(**opt_args):
        print(manager(config).status_string())


@sys_args
def start(**opt_args):
    """ Starts each MPF dependency """
    print('Starting MPF system dependencies...')
    config = MpfConfig(**opt_args)
    for manager in filtered_start_up_order(**opt_args):
        manager(config).start()


@sys_args
def stop(**opt_args):
    """ Stops each MPF dependency """
    print('Stopping MPF system dependencies...')
    config = MpfConfig(**opt_args)
    for manager in reversed(tuple(filtered_start_up_order(**opt_args))):
        manager(config).stop()


@sys_args
def restart(**opt_args):
    """ Stops each MPF dependency, then starts each dependency """
    stop(**opt_args)
    start(**opt_args)


COMMANDS = (status, start, stop, restart)
