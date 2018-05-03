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

# TODO: Remove this file once Python SDK is available

import inspect
import collections
import os


def type_converter(obj, clazz_or_factory):
    if inspect.isclass(clazz_or_factory) and isinstance(obj, clazz_or_factory):
        return obj
    return clazz_or_factory(obj)


class FieldTypes(object):
    """
    Class decorator that adds properties that check the argument type before assigning to the underlying field.
    """
    def __init__(self, **kwargs):
        """
        :param kwargs: The key is the name of the field and the value is either a class
                        or a function that converts a value to that class.
        """
        self.types_dict = kwargs


    def __call__(self, clazz):
        for field, field_type in self.types_dict.iteritems():
            prop = FieldTypes.create_property('_' + field, field_type)
            # Add the new property to the decorated class
            setattr(clazz, field, prop)

        FieldTypes.add_to_string_method(clazz, self.types_dict)
        return clazz


    # Creates a property descriptor. The getter just returns the underlying field.
    # The setter makes sure the RHS of the assignment is of the correct type.
    @staticmethod
    def create_property(member_var_name, field_type):
        def getter(instance):
            # Just return the backing field
            return getattr(instance, member_var_name, None)

        def setter(instance, val):
            # Make sure val is instance of field_type when trying to assign
            setattr(instance, member_var_name, type_converter(val, field_type))

        return property(getter, setter)

    @staticmethod
    def add_to_string_method(clazz, prop_types):
        str_is_overridden = hasattr(clazz, '__repr__') and clazz.__repr__ != object.__repr__
        if str_is_overridden:
            return

        ctor_arg_list = inspect.getargspec(clazz.__init__).args
        print_order = [a for a in ctor_arg_list if a in prop_types]

        setattr(clazz, '__repr__', FieldTypes.create_string_fn(print_order))

    @staticmethod
    def create_string_fn(fields):
        def to_string(instance):
            fields_string = ', '.join('%s = %s' % (f, getattr(instance, f)) for f in fields)
            return '%s { %s }' % (type(instance).__name__, fields_string)
        return to_string


class TypedDict(collections.MutableMapping):
    """
    Behaves identically to a regular dict, except when inserting new items.
    When inserting new items the types of the key and value are checked.
    """
    key_type = object
    value_type = object

    def __init__(self, *args, **kwargs):
        super(TypedDict, self).__init__()
        self._dict = dict()
        self.update(*args, **kwargs)

    def __setitem__(self, key, value):
        typed_key = type_converter(key, self.key_type)
        typed_value = type_converter(value, self.value_type)
        return self._dict.__setitem__(typed_key, typed_value)

    # Everything else just calls through to the underlying dict

    def __delitem__(self, key):
        return self._dict.__delitem__(key)

    def __getitem__(self, key):
        return self._dict.__getitem__(key)

    def __len__(self):
        return self._dict.__len__()

    def __iter__(self):
        return self._dict.__iter__()

    def __str__(self):
        return self._dict.__str__()



class EnumMeta(type):
    #  Methods on this class get inherited by the enum class itself, not its instances / enum elements.
    def __init__(cls, name, bases, class_dict):
        super(EnumMeta, cls).__init__(name, bases, class_dict)
        if name == 'EnumBase':
            return
        for key, value in class_dict.iteritems():
            if key.startswith('_') or not isinstance(value, EnumValueTag):
                continue
            enum_element = cls(key, value.int_val)
            super(EnumMeta, cls).__setattr__(key, enum_element)

        def cls_new(*_):
            raise AttributeError('Cannot add enum fields outside of enum class.')

        super(EnumMeta, cls).__setattr__('__new__', classmethod(cls_new))


    def __setattr__(cls, key, value):
        """ Prevent assignment to fields of the enum class """
        raise AttributeError('Cannot change enum fields.')

    def __iter__(cls):
        """Make the enum class itself iterable. Iterating over an enum class returns the individual enum elements"""
        return (val for val in vars(cls).itervalues() if isinstance(val, cls))

    def __getitem__(cls, key):
        """Adds the square bracket ([]) operator to the enum class."""
        if isinstance(key, int):
            match = next((val for val in cls if val.int_val == key), None)
            if match is None:
                raise KeyError('No enum element with int value: %s' % key)
            return match
        elif isinstance(key, str):
            match = getattr(cls, key, None)
            if match is None:
                raise KeyError('No enum element with str value: %s' % key)
            return match
        raise TypeError('key must be int or str')

    def __len__(cls):
        """Make it possible to call len on an enum class, which return the number of elements in the enum"""
        return sum(1 for _ in cls)


class EnumBase(object):
    # Methods on this class are inherited by the enum elements.
    __metaclass__ = EnumMeta

    def __init__(self, str_val, int_val):
        self.str_val = str_val
        self.int_val = int_val

    def __int__(self):
        """Make enum values convertible to int"""
        return self.int_val

    def __long__(self):
        """Make enum values convertible to long"""
        return self.int_val

    def __str__(self):
        return self.str_val

    def __repr__(self):
        return 'Enum Element: %s(%r, %r)' % (self.__class__.__name__, self.str_val, self.int_val)

    def __setattr__(self, key, value):
        # Only allow the fields to be set once, which occurs in __init__
        if key in ('str_val', 'int_val') and not hasattr(self, key):
            super(EnumBase, self).__setattr__(key, value)
            return
        raise AttributeError('Cannot change enum fields.')

    def __eq__(self, other):
        return type(self) == type(other) and self.int_val == other.int_val and self.str_val == other.str_val

    def __ne__(self, other):
        return not self.__eq__(other)

    @staticmethod
    def element_count(count):
        return (EnumValueTag(i) for i in xrange(count))


class EnumValueTag(object):
    """Place holder for enum elements before enum class is created."""
    def __init__(self, int_val):
        self.int_val = int_val


def create_if_none(val, func):
    if val is None:
        return func()
    else:
        return val


def get_full_log_path(filename):
    log_dir = os.path.expandvars('$MPF_LOG_PATH/$THIS_MPF_NODE/log')
    if not os.path.exists(log_dir):
        os.makedirs(log_dir)
    log_path = os.path.join(log_dir, filename)
    return os.path.expandvars(log_path)


def get_log_name(filename):
    log_name, _ = os.path.splitext(os.path.basename(filename if filename else ''))
    return log_name if log_name else 'component_logger'
