# Copyright 2015 The Chromium Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import collections
import copy
import logging
import os
import pickle
import re

from devil.android import apk_helper
from devil.android import md5sum
from pylib import constants
from pylib.base import base_test_result
from pylib.base import test_instance
from pylib.constants import host_paths
from pylib.instrumentation import test_result
from pylib.instrumentation import instrumentation_parser
from pylib.utils import isolator
from pylib.utils import proguard

with host_paths.SysPath(host_paths.BUILD_COMMON_PATH):
  import unittest_util # pylint: disable=import-error

# Ref: http://developer.android.com/reference/android/app/Activity.html
_ACTIVITY_RESULT_CANCELED = 0
_ACTIVITY_RESULT_OK = -1

_COMMAND_LINE_PARAMETER = 'cmdlinearg-parameter'
_DEFAULT_ANNOTATIONS = [
    'Smoke', 'SmallTest', 'MediumTest', 'LargeTest',
    'EnormousTest', 'IntegrationTest']
_EXCLUDE_UNLESS_REQUESTED_ANNOTATIONS = [
    'DisabledTest', 'FlakyTest']
_VALID_ANNOTATIONS = set(['Manual', 'PerfTest'] + _DEFAULT_ANNOTATIONS +
                         _EXCLUDE_UNLESS_REQUESTED_ANNOTATIONS)
_EXTRA_DRIVER_TEST_LIST = (
    'org.chromium.test.driver.OnDeviceInstrumentationDriver.TestList')
_EXTRA_DRIVER_TEST_LIST_FILE = (
    'org.chromium.test.driver.OnDeviceInstrumentationDriver.TestListFile')
_EXTRA_DRIVER_TARGET_PACKAGE = (
    'org.chromium.test.driver.OnDeviceInstrumentationDriver.TargetPackage')
_EXTRA_DRIVER_TARGET_CLASS = (
    'org.chromium.test.driver.OnDeviceInstrumentationDriver.TargetClass')
_EXTRA_TIMEOUT_SCALE = (
    'org.chromium.test.driver.OnDeviceInstrumentationDriver.TimeoutScale')

_PARAMETERIZED_TEST_ANNOTATION = 'ParameterizedTest'
_PARAMETERIZED_TEST_SET_ANNOTATION = 'ParameterizedTest$Set'
_NATIVE_CRASH_RE = re.compile('native crash', re.IGNORECASE)
_PICKLE_FORMAT_VERSION = 10


class MissingSizeAnnotationError(Exception):
  def __init__(self, class_name):
    super(MissingSizeAnnotationError, self).__init__(class_name +
        ': Test method is missing required size annotation. Add one of: ' +
        ', '.join('@' + a for a in _VALID_ANNOTATIONS))


# TODO(jbudorick): Make these private class methods of
# InstrumentationTestInstance once the instrumentation test_runner is
# deprecated.
def ParseAmInstrumentRawOutput(raw_output):
  """Parses the output of an |am instrument -r| call.

  Args:
    raw_output: the output of an |am instrument -r| call as a list of lines
  Returns:
    A 3-tuple containing:
      - the instrumentation code as an integer
      - the instrumentation result as a list of lines
      - the instrumentation statuses received as a list of 2-tuples
        containing:
        - the status code as an integer
        - the bundle dump as a dict mapping string keys to a list of
          strings, one for each line.
  """
  parser = instrumentation_parser.InstrumentationParser(raw_output)
  statuses = list(parser.IterStatus())
  code, bundle = parser.GetResult()
  return (code, bundle, statuses)


def GenerateTestResults(
    result_code, result_bundle, statuses, start_ms, duration_ms):
  """Generate test results from |statuses|.

  Args:
    result_code: The overall status code as an integer.
    result_bundle: The summary bundle dump as a dict.
    statuses: A list of 2-tuples containing:
      - the status code as an integer
      - the bundle dump as a dict mapping string keys to string values
      Note that this is the same as the third item in the 3-tuple returned by
      |_ParseAmInstrumentRawOutput|.
    start_ms: The start time of the test in milliseconds.
    duration_ms: The duration of the test in milliseconds.

  Returns:
    A list containing an instance of InstrumentationTestResult for each test
    parsed.
  """

  results = []

  current_result = None

  for status_code, bundle in statuses:
    test_class = bundle.get('class', '')
    test_method = bundle.get('test', '')
    if test_class and test_method:
      test_name = '%s#%s' % (test_class, test_method)
    else:
      continue

    if status_code == instrumentation_parser.STATUS_CODE_START:
      if current_result:
        results.append(current_result)
      current_result = test_result.InstrumentationTestResult(
          test_name, base_test_result.ResultType.UNKNOWN, start_ms, duration_ms)
    else:
      if status_code == instrumentation_parser.STATUS_CODE_OK:
        if bundle.get('test_skipped', '').lower() in ('true', '1', 'yes'):
          current_result.SetType(base_test_result.ResultType.SKIP)
        elif current_result.GetType() == base_test_result.ResultType.UNKNOWN:
          current_result.SetType(base_test_result.ResultType.PASS)
      else:
        if status_code not in (instrumentation_parser.STATUS_CODE_ERROR,
                               instrumentation_parser.STATUS_CODE_FAILURE):
          logging.error('Unrecognized status code %d. Handling as an error.',
                        status_code)
        current_result.SetType(base_test_result.ResultType.FAIL)
        if 'stack' in bundle:
          current_result.SetLog(bundle['stack'])

  if current_result:
    if current_result.GetType() == base_test_result.ResultType.UNKNOWN:
      crashed = (result_code == _ACTIVITY_RESULT_CANCELED
                 and any(_NATIVE_CRASH_RE.search(l)
                         for l in result_bundle.itervalues()))
      if crashed:
        current_result.SetType(base_test_result.ResultType.CRASH)

    results.append(current_result)

  return results


def ParseCommandLineFlagParameters(annotations):
  """Determines whether the test is parameterized to be run with different
     command-line flags.

  Args:
    annotations: The annotations of the test.

  Returns:
    If the test is parameterized, returns a list of named tuples
    with lists of flags, e.g.:

      [(add=['--flag-to-add']), (remove=['--flag-to-remove']), ()]

    That means, the test must be run three times, the first time with
    "--flag-to-add" added to command-line, the second time with
    "--flag-to-remove" to be removed from command-line, and the third time
    with default command-line args. If the same flag is listed both for adding
    and for removing, it is left unchanged.

    If the test is not parametrized, returns None.

  """
  ParamsTuple = collections.namedtuple('ParamsTuple', ['add', 'remove'])
  parameterized_tests = []
  if _PARAMETERIZED_TEST_SET_ANNOTATION in annotations:
    if annotations[_PARAMETERIZED_TEST_SET_ANNOTATION]:
      parameterized_tests = annotations[
        _PARAMETERIZED_TEST_SET_ANNOTATION].get('tests', [])
  elif _PARAMETERIZED_TEST_ANNOTATION in annotations:
    parameterized_tests = [annotations[_PARAMETERIZED_TEST_ANNOTATION]]
  else:
    return None

  result = []
  for pt in parameterized_tests:
    if not pt:
      continue
    for p in pt['parameters']:
      if p['tag'] == _COMMAND_LINE_PARAMETER:
        to_add = []
        to_remove = []
        for a in p.get('arguments', []):
          if a['name'] == 'add':
            to_add = ['--%s' % f for f in a['stringArray']]
          elif a['name'] == 'remove':
            to_remove = ['--%s' % f for f in a['stringArray']]
        result.append(ParamsTuple(to_add, to_remove))
  return result if result else None


class InstrumentationTestInstance(test_instance.TestInstance):

  def __init__(self, args, isolate_delegate, error_func):
    super(InstrumentationTestInstance, self).__init__()

    self._additional_apks = []
    self._apk_under_test = None
    self._apk_under_test_incremental_install_script = None
    self._package_info = None
    self._suite = None
    self._test_apk = None
    self._test_apk_incremental_install_script = None
    self._test_jar = None
    self._test_package = None
    self._test_runner = None
    self._test_support_apk = None
    self._initializeApkAttributes(args, error_func)

    self._data_deps = None
    self._isolate_abs_path = None
    self._isolate_delegate = None
    self._isolated_abs_path = None
    self._test_data = None
    self._initializeDataDependencyAttributes(args, isolate_delegate)

    self._annotations = None
    self._excluded_annotations = None
    self._test_filter = None
    self._initializeTestFilterAttributes(args)

    self._flags = None
    self._initializeFlagAttributes(args)

    self._driver_apk = None
    self._driver_package = None
    self._driver_name = None
    self._initializeDriverAttributes()

    self._timeout_scale = None
    self._initializeTestControlAttributes(args)

  def _initializeApkAttributes(self, args, error_func):
    if args.apk_under_test:
      apk_under_test_path = args.apk_under_test
      if not args.apk_under_test.endswith('.apk'):
        apk_under_test_path = os.path.join(
            constants.GetOutDirectory(), constants.SDK_BUILD_APKS_DIR,
            '%s.apk' % args.apk_under_test)

      if not os.path.exists(apk_under_test_path):
        error_func('Unable to find APK under test: %s' % apk_under_test_path)

      self._apk_under_test = apk_helper.ToHelper(apk_under_test_path)

    if args.test_apk.endswith('.apk'):
      self._suite = os.path.splitext(os.path.basename(args.test_apk))[0]
      self._test_apk = apk_helper.ToHelper(args.test_apk)
    else:
      self._suite = args.test_apk
      self._test_apk = apk_helper.ToHelper(os.path.join(
          constants.GetOutDirectory(), constants.SDK_BUILD_APKS_DIR,
          '%s.apk' % args.test_apk))

    self._apk_under_test_incremental_install_script = (
        args.apk_under_test_incremental_install_script)
    self._test_apk_incremental_install_script = (
        args.test_apk_incremental_install_script)

    if self._test_apk_incremental_install_script:
      assert self._suite.endswith('_incremental')
      self._suite = self._suite[:-len('_incremental')]

    self._test_jar = os.path.join(
        constants.GetOutDirectory(), constants.SDK_BUILD_TEST_JAVALIB_DIR,
        '%s.jar' % self._suite)
    self._test_support_apk = apk_helper.ToHelper(os.path.join(
        constants.GetOutDirectory(), constants.SDK_BUILD_TEST_JAVALIB_DIR,
        '%sSupport.apk' % self._suite))

    if not os.path.exists(self._test_apk.path):
      error_func('Unable to find test APK: %s' % self._test_apk.path)
    if not os.path.exists(self._test_jar):
      error_func('Unable to find test JAR: %s' % self._test_jar)

    self._test_package = self._test_apk.GetPackageName()
    self._test_runner = self._test_apk.GetInstrumentationName()

    self._package_info = None
    if self._apk_under_test:
      package_under_test = self._apk_under_test.GetPackageName()
      for package_info in constants.PACKAGE_INFO.itervalues():
        if package_under_test == package_info.package:
          self._package_info = package_info
    if not self._package_info:
      logging.warning('Unable to find package info for %s', self._test_package)

    for apk in args.additional_apks:
      if not os.path.exists(apk):
        error_func('Unable to find additional APK: %s' % apk)
    self._additional_apks = (
        [apk_helper.ToHelper(x) for x in args.additional_apks])

  def _initializeDataDependencyAttributes(self, args, isolate_delegate):
    self._data_deps = []
    if (args.isolate_file_path and
        not isolator.IsIsolateEmpty(args.isolate_file_path)):
      if os.path.isabs(args.isolate_file_path):
        self._isolate_abs_path = args.isolate_file_path
      else:
        self._isolate_abs_path = os.path.join(
            constants.DIR_SOURCE_ROOT, args.isolate_file_path)
      self._isolate_delegate = isolate_delegate
      self._isolated_abs_path = os.path.join(
          constants.GetOutDirectory(), '%s.isolated' % self._test_package)
    else:
      self._isolate_delegate = None

    # TODO(jbudorick): Deprecate and remove --test-data once data dependencies
    # are fully converted to isolate.
    if args.test_data:
      logging.info('Data dependencies specified via --test-data')
      self._test_data = args.test_data
    else:
      self._test_data = None

    if not self._isolate_delegate and not self._test_data:
      logging.warning('No data dependencies will be pushed.')

  def _initializeTestFilterAttributes(self, args):
    if args.test_filter:
      self._test_filter = args.test_filter.replace('#', '.')

    def annotation_dict_element(a):
      a = a.split('=')
      return (a[0], a[1] if len(a) == 2 else None)

    if args.annotation_str:
      self._annotations = dict(
          annotation_dict_element(a)
          for a in args.annotation_str.split(','))
    elif not self._test_filter:
      self._annotations = dict(
          annotation_dict_element(a)
          for a in _DEFAULT_ANNOTATIONS)
    else:
      self._annotations = {}

    if args.exclude_annotation_str:
      self._excluded_annotations = dict(
          annotation_dict_element(a)
          for a in args.exclude_annotation_str.split(','))
    else:
      self._excluded_annotations = {}

    self._excluded_annotations.update(
        {
          a: None for a in _EXCLUDE_UNLESS_REQUESTED_ANNOTATIONS
          if a not in self._annotations
        })

  def _initializeFlagAttributes(self, args):
    self._flags = ['--enable-test-intents']
    # TODO(jbudorick): Transition "--device-flags" to "--device-flags-file"
    if hasattr(args, 'device_flags') and args.device_flags:
      with open(args.device_flags) as device_flags_file:
        stripped_lines = (l.strip() for l in device_flags_file)
        self._flags.extend([flag for flag in stripped_lines if flag])
    if hasattr(args, 'device_flags_file') and args.device_flags_file:
      with open(args.device_flags_file) as device_flags_file:
        stripped_lines = (l.strip() for l in device_flags_file)
        self._flags.extend([flag for flag in stripped_lines if flag])
    if (hasattr(args, 'strict_mode') and
        args.strict_mode and
        args.strict_mode != 'off'):
      self._flags.append('--strict-mode=' + args.strict_mode)

  def _initializeDriverAttributes(self):
    self._driver_apk = os.path.join(
        constants.GetOutDirectory(), constants.SDK_BUILD_APKS_DIR,
        'OnDeviceInstrumentationDriver.apk')
    if os.path.exists(self._driver_apk):
      driver_apk = apk_helper.ApkHelper(self._driver_apk)
      self._driver_package = driver_apk.GetPackageName()
      self._driver_name = driver_apk.GetInstrumentationName()
    else:
      self._driver_apk = None

  def _initializeTestControlAttributes(self, args):
    self._timeout_scale = args.timeout_scale or 1

  @property
  def additional_apks(self):
    return self._additional_apks

  @property
  def apk_under_test(self):
    return self._apk_under_test

  @property
  def apk_under_test_incremental_install_script(self):
    return self._apk_under_test_incremental_install_script

  @property
  def flags(self):
    return self._flags

  @property
  def driver_apk(self):
    return self._driver_apk

  @property
  def driver_package(self):
    return self._driver_package

  @property
  def driver_name(self):
    return self._driver_name

  @property
  def package_info(self):
    return self._package_info

  @property
  def suite(self):
    return self._suite

  @property
  def test_apk(self):
    return self._test_apk

  @property
  def test_apk_incremental_install_script(self):
    return self._test_apk_incremental_install_script

  @property
  def test_jar(self):
    return self._test_jar

  @property
  def test_support_apk(self):
    return self._test_support_apk

  @property
  def test_package(self):
    return self._test_package

  @property
  def test_runner(self):
    return self._test_runner

  @property
  def timeout_scale(self):
    return self._timeout_scale

  #override
  def TestType(self):
    return 'instrumentation'

  #override
  def SetUp(self):
    if self._isolate_delegate:
      self._isolate_delegate.Remap(
          self._isolate_abs_path, self._isolated_abs_path)
      self._isolate_delegate.MoveOutputDeps()
      self._data_deps.extend([(self._isolate_delegate.isolate_deps_dir, None)])

    # TODO(jbudorick): Convert existing tests that depend on the --test-data
    # mechanism to isolate, then remove this.
    if self._test_data:
      for t in self._test_data:
        device_rel_path, host_rel_path = t.split(':')
        host_abs_path = os.path.join(host_paths.DIR_SOURCE_ROOT, host_rel_path)
        self._data_deps.extend(
            [(host_abs_path,
              [None, 'chrome', 'test', 'data', device_rel_path])])

  def GetDataDependencies(self):
    return self._data_deps

  def GetTests(self):
    pickle_path = '%s-proguard.pickle' % self.test_jar
    try:
      tests = self._GetTestsFromPickle(pickle_path, self.test_jar)
    except self.ProguardPickleException as e:
      logging.info('Getting tests from JAR via proguard. (%s)', str(e))
      tests = self._GetTestsFromProguard(self.test_jar)
      self._SaveTestsToPickle(pickle_path, self.test_jar, tests)
    return self._ParametrizeTestsWithFlags(
        self._InflateTests(self._FilterTests(tests)))

  class ProguardPickleException(Exception):
    pass

  def _GetTestsFromPickle(self, pickle_path, jar_path):
    if not os.path.exists(pickle_path):
      raise self.ProguardPickleException('%s does not exist.' % pickle_path)
    if os.path.getmtime(pickle_path) <= os.path.getmtime(jar_path):
      raise self.ProguardPickleException(
          '%s newer than %s.' % (jar_path, pickle_path))

    with open(pickle_path, 'r') as pickle_file:
      pickle_data = pickle.loads(pickle_file.read())
    jar_md5 = md5sum.CalculateHostMd5Sums(jar_path)[jar_path]

    try:
      if pickle_data['VERSION'] != _PICKLE_FORMAT_VERSION:
        raise self.ProguardPickleException('PICKLE_FORMAT_VERSION has changed.')
      if pickle_data['JAR_MD5SUM'] != jar_md5:
        raise self.ProguardPickleException('JAR file MD5 sum differs.')
      return pickle_data['TEST_METHODS']
    except TypeError as e:
      logging.error(pickle_data)
      raise self.ProguardPickleException(str(e))

  # pylint: disable=no-self-use
  def _GetTestsFromProguard(self, jar_path):
    p = proguard.Dump(jar_path)

    def is_test_class(c):
      return c['class'].endswith('Test')

    def is_test_method(m):
      return m['method'].startswith('test')

    class_lookup = dict((c['class'], c) for c in p['classes'])
    def recursive_get_class_annotations(c):
      s = c['superclass']
      if s in class_lookup:
        a = recursive_get_class_annotations(class_lookup[s])
      else:
        a = {}
      a.update(c['annotations'])
      return a

    def stripped_test_class(c):
      return {
        'class': c['class'],
        'annotations': recursive_get_class_annotations(c),
        'methods': [m for m in c['methods'] if is_test_method(m)],
      }

    return [stripped_test_class(c) for c in p['classes']
            if is_test_class(c)]

  def _SaveTestsToPickle(self, pickle_path, jar_path, tests):
    jar_md5 = md5sum.CalculateHostMd5Sums(jar_path)[jar_path]
    pickle_data = {
      'VERSION': _PICKLE_FORMAT_VERSION,
      'JAR_MD5SUM': jar_md5,
      'TEST_METHODS': tests,
    }
    with open(pickle_path, 'w') as pickle_file:
      pickle.dump(pickle_data, pickle_file)

  def _FilterTests(self, tests):

    def gtest_filter(c, m):
      if not self._test_filter:
        return True
      # Allow fully-qualified name as well as an omitted package.
      names = ['%s.%s' % (c['class'], m['method']),
               '%s.%s' % (c['class'].split('.')[-1], m['method'])]
      return unittest_util.FilterTestNames(names, self._test_filter)

    def annotation_filter(all_annotations):
      if not self._annotations:
        return True
      return any_annotation_matches(self._annotations, all_annotations)

    def excluded_annotation_filter(all_annotations):
      if not self._excluded_annotations:
        return True
      return not any_annotation_matches(self._excluded_annotations,
                                        all_annotations)

    def any_annotation_matches(annotations, all_annotations):
      return any(
          ak in all_annotations and (av is None or av == all_annotations[ak])
          for ak, av in annotations.iteritems())

    filtered_classes = []
    for c in tests:
      filtered_methods = []
      for m in c['methods']:
        # Gtest filtering
        if not gtest_filter(c, m):
          continue

        all_annotations = dict(c['annotations'])
        all_annotations.update(m['annotations'])

        # Enforce that all tests declare their size.
        if not any(a in _VALID_ANNOTATIONS for a in all_annotations):
          raise MissingSizeAnnotationError('%s.%s' % (c['class'], m['method']))

        if (not annotation_filter(all_annotations)
            or not excluded_annotation_filter(all_annotations)):
          continue

        filtered_methods.append(m)

      if filtered_methods:
        filtered_class = dict(c)
        filtered_class['methods'] = filtered_methods
        filtered_classes.append(filtered_class)

    return filtered_classes

  def _InflateTests(self, tests):
    inflated_tests = []
    for c in tests:
      for m in c['methods']:
        a = dict(c['annotations'])
        a.update(m['annotations'])
        inflated_tests.append({
            'class': c['class'],
            'method': m['method'],
            'annotations': a,
        })
    return inflated_tests

  def _ParametrizeTestsWithFlags(self, tests):
    new_tests = []
    for t in tests:
      parameters = ParseCommandLineFlagParameters(t['annotations'])
      if parameters:
        t['flags'] = parameters[0]
        for p in parameters[1:]:
          parameterized_t = copy.copy(t)
          parameterized_t['flags'] = p
          new_tests.append(parameterized_t)
    return tests + new_tests

  def GetDriverEnvironmentVars(
      self, test_list=None, test_list_file_path=None):
    env = {
      _EXTRA_DRIVER_TARGET_PACKAGE: self.test_package,
      _EXTRA_DRIVER_TARGET_CLASS: self.test_runner,
      _EXTRA_TIMEOUT_SCALE: self._timeout_scale,
    }

    if test_list:
      env[_EXTRA_DRIVER_TEST_LIST] = ','.join(test_list)

    if test_list_file_path:
      env[_EXTRA_DRIVER_TEST_LIST_FILE] = (
          os.path.basename(test_list_file_path))

    return env

  @staticmethod
  def ParseAmInstrumentRawOutput(raw_output):
    return ParseAmInstrumentRawOutput(raw_output)

  @staticmethod
  def GenerateTestResults(
      result_code, result_bundle, statuses, start_ms, duration_ms):
    return GenerateTestResults(result_code, result_bundle, statuses,
                               start_ms, duration_ms)

  #override
  def TearDown(self):
    if self._isolate_delegate:
      self._isolate_delegate.Clear()

