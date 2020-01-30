#############################################################################
# NOTICE                                                                    #
#                                                                           #
# This software (or technical data) was produced for the U.S. Government    #
# under contract, and is subject to the Rights in Data-General Clause       #
# 52.227-14, Alt. IV (DEC 2007).                                            #
#                                                                           #
# Copyright 2019 The MITRE Corporation. All Rights Reserved.                #
#############################################################################

#############################################################################
# Copyright 2019 The MITRE Corporation                                      #
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

import contextlib
import getpass
import itertools

import argh
import bcrypt

import mpf_sys
import mpf_util


def hash_password(password):
    return bcrypt.hashpw(password, bcrypt.gensalt(12, prefix='2a'))


class UserManager(object):
    def __init__(self, sql_host='localhost', sql_user='mpf', sql_password='password', skip_sql_start=False):
        if not skip_sql_start:
            sql_manager = mpf_sys.PostgresManager(False)
            if not sql_manager.status():
                print 'Starting PostgreSQL service...'
                sql_manager.start()
                print

        self._connection = mpf_util.sql_connection(sql_host, sql_user, sql_password)
        self._cursor = self._connection.cursor()

    def close(self):
        self._cursor.close()
        self._connection.close()

    def list_users(self):
        sql = """
            SELECT username, roles.user_roles
            FROM mpf_user
            JOIN user_user_roles as roles
              ON mpf_user.id = roles.user_id
        """
        self._cursor.execute(sql)
        results = self._cursor.fetchall()

        if not results:
            print 'No users found'
            return

        max_username_len = max(len(u) for u, r in results)
        justify_len = max(10, max_username_len + 3)

        print 'Username'.ljust(justify_len), 'Role'
        print '--------'.ljust(justify_len), '----'

        for user, user_roles_pair in itertools.groupby(sorted(results), key=lambda x: x[0]):
            sorted_roles = sorted(r for u, r in user_roles_pair)
            print user.ljust(justify_len), ', '.join(sorted_roles)


    def add_user(self, username, password, role):
        self._verify_no_existing_user(username)
        password = get_password(password)
        self._insert_user(username, password)
        self._set_role(username, role)
        self._connection.commit()

    def set_role(self, username, role):
        self._verify_user_exists(username)
        self._set_role(username, role)
        self._connection.commit()

    def change_password(self, username, new_password):
        self._verify_user_exists(username)
        new_password = get_password(new_password)
        sql = """
            UPDATE mpf_user
            SET password = %s
            WHERE username = %s
        """
        password_hash = hash_password(new_password)
        self._cursor.execute(sql, (password_hash, username))
        self._connection.commit()

    def remove_user(self, username):
        self._verify_user_exists(username)
        self._delete_roles(username)
        sql = """
            DELETE FROM mpf_user
            WHERE username = %s
        """
        self._cursor.execute(sql, (username,))
        self._connection.commit()

    def _user_exists(self, username):
        sql = """
            SELECT EXISTS(
                SELECT 1
                FROM mpf_user
                WHERE username = %s
            )
        """
        self._cursor.execute(sql, (username,))
        result = self._cursor.fetchone()
        return result[0] == 1

    def _insert_user(self, username, password):
        sql = """
            INSERT INTO mpf_user (username, password)
            VALUES (%s, %s)
        """
        hashed_password = hash_password(password)
        self._cursor.execute(sql, (username, hashed_password))

    def _set_role(self, username, role):
        self._delete_roles(username)
        sql = """
            INSERT INTO user_user_roles (user_id, user_roles)
            SELECT id, %s
            FROM mpf_user
            WHERE username = %s
        """
        self._cursor.execute(sql, (role.upper(), username))

    def _delete_roles(self, username):
        sql = """
            DELETE FROM user_user_roles
            WHERE user_id in (
                SELECT id
                FROM mpf_user
                WHERE username = %s
            )
        """
        self._cursor.execute(sql, (username,))

    def _verify_no_existing_user(self, username):
        if self._user_exists(username):
            raise UserAlreadyExistsError(username)

    def _verify_user_exists(self, username):
        if not self._user_exists(username):
            raise UserNotFoundError(username)


class UserAlreadyExistsError(mpf_util.MpfError):
    def __init__(self, username):
        super(UserAlreadyExistsError, self) \
            .__init__('User with name "%s" already exists' % username)


class UserNotFoundError(mpf_util.MpfError):
    def __init__(self, username):
        super(UserNotFoundError, self).__init__('User with name "%s" does not exist' % username)


def verify_password(password):
    if not password:
        raise mpf_util.MpfError('Password can not be the empty string.')
    if mpf_util.has_whitespace(password):
        raise mpf_util.MpfError('Password can not contain white space.')


def get_password(password):
    if not password:
        password = getpass.getpass('Enter the new password: ')
    verify_password(password)
    return password


USER_MODIFICATION_NOTICE = mpf_util.MsgUtil.yellow(
    'Changes will not take effect if the user is currently logged in. The user must log out first. '
    'To force these changes now, restart Workflow Manager.')


# Setup Commands


@mpf_util.sql_args
def list_users(**kwargs):
    """ Prints out the list of users """
    with contextlib.closing(UserManager(**kwargs)) as um:
        um.list_users()


@mpf_util.sql_args
@argh.arg('username', help='name of the user to create', action=mpf_util.VerifyNoWhiteSpace)
@argh.arg('-p', '--password', help='password for the new user', action=mpf_util.VerifyNoWhiteSpace)
@argh.arg('role', help='role for the new user', choices=('user', 'admin'), type=str.lower)
def add_user(username, role, password=None, **kwargs):
    """ Adds a new user to the Workflow Manager """
    with contextlib.closing(UserManager(**kwargs)) as um:
        um.add_user(username, password, role)
        print mpf_util.MsgUtil.green('User: %s with role: %s has been added' % (username, role))


@mpf_util.sql_args
@argh.arg('username', help='name of the user to remove')
def remove_user(username, **kwargs):
    """ Removes a user from the Workflow Manager """
    with contextlib.closing(UserManager(**kwargs)) as um:
        um.remove_user(username)
        print USER_MODIFICATION_NOTICE
        print mpf_util.MsgUtil.green('User: %s has been removed' % username)


@mpf_util.sql_args
@argh.arg('username', help='name of user to change the password for')
@argh.arg('-p', '--password', help='new password for user', action=mpf_util.VerifyNoWhiteSpace)
def change_password(username, password=None, **kwargs):
    """ Changes a Workflow Manager user's password """
    with contextlib.closing(UserManager(**kwargs)) as um:
        um.change_password(username, password)
        print USER_MODIFICATION_NOTICE
        print mpf_util.MsgUtil.green('Password has been changed for user: %s' % username)


@mpf_util.sql_args
@argh.arg('username', help='name of the user whose role will be changed')
@argh.arg('role', help='new role for user', choices=('user', 'admin'), type=str.lower)
def change_role(username, role, **kwargs):
    """ Changes a Workflow Manager user's role """
    with contextlib.closing(UserManager(**kwargs)) as um:
        um.set_role(username, role)
        print USER_MODIFICATION_NOTICE
        print mpf_util.MsgUtil.green('User: %s now has role: %s' % (username, role))


COMMANDS = (list_users, add_user, remove_user, change_password, change_role)
