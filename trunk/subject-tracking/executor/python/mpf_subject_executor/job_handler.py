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

from __future__ import annotations

import contextlib
import queue
import threading
from typing import NoReturn, Optional

import proton
import proton.handlers
import proton.reactor

from . import executor_util as util
from .executor_config import ExecutorConfig
from .logger_wrapper import LoggerWrapper
from .mpf_proto import subject_pb2 as pb_sub


class JobHandler(proton.handlers.MessagingHandler):
    def __init__(
            self,
            logger: LoggerWrapper,
            config: ExecutorConfig,
            container: proton.reactor.Container,
            connection: proton.Connection) -> None:
        super().__init__(prefetch=0, auto_accept=False, peer_close_is_error=True)
        self._logger = logger
        # We do not add a handler to the sender because we only interact with the sender through
        # transactions and transactions have their own event handlers.
        self._sender = container.create_sender(connection)
        self._logger.info('Creating receiver for queue: ', config.job_queue)
        self._receiver = container.create_receiver(connection, config.job_queue, handler=self)
        # Issue one credit so that we are able to receive the first job request.
        self._receiver.flow(1)
        self._log_ctx_manager = LogCtxManager(logger)
        self._container = container
        self._connection = connection

        self._job_request_queue = queue.Queue()
        job_done_injector = proton.reactor.EventInjector()
        container.selectable(job_done_injector)
        JobExecutor(logger, config, self._job_request_queue, job_done_injector)


    def on_message(self, event: proton.Event) -> None:
        # We only issue the broker one credit at a time, so we will not receive additional
        # messages until receiver.flow is called.
        self._try_begin_job_context(event.message)
        self._logger.info('New message received.')
        if isinstance(event.message.body, memoryview):
            # The job is processed asynchronously to avoid blocking the event loop.
            self._job_request_queue.put(event)
        else:
            self._logger.info('Received invalid message')
            assert event.delivery
            self.reject(event.delivery)
            # The previous credit was consumed by the invalid message, so we need to issue a new
            # credit to receive an additional message.
            self._receiver.flow(1)


    def _try_begin_job_context(self, message: proton.Message) -> None:
        if message.properties and (job_id := message.properties.get('JobId')):
            self._log_ctx_manager.begin_job(f'Job {job_id}')


    def on_job_response_ready(self, event: JobResponseReadyEvent) -> None:
        self._logger.info('Job response ready.')
        self._container.declare_transaction(
                self._connection,
                JobTransactionHandler(self._logger, event, self),
                settle_before_discharge=True)
        self._logger.info('Initializing transaction.')


    def on_transaction_declared_and_job_done(
            self,
            tx_event: proton.Event,
            job_resp_event: JobResponseReadyEvent) -> None:
        tx: proton.reactor.Transaction = tx_event.transaction
        tx.send(self._sender, job_resp_event.response)
        tx.accept(job_resp_event.request_delivery)
        tx.commit()
        self._logger.info('Committing transaction.')


    def on_transaction_committed(self, _: proton.Event) -> None:
        self._logger.info('Transaction completed successfully.')
        self._log_ctx_manager.end_job()
        # Now that the broker informed us that the transaction was successful, we let the broker
        # know that we can now process another message.
        self._receiver.flow(1)
        self._logger.info('Waiting for next job.')

    def __enter__(self) -> JobHandler:
        return self

    def __exit__(self, *_) -> None:
        self._job_request_queue.put(None)


class JobTransactionHandler(proton.handlers.TransactionHandler):
    def __init__(self, logger: LoggerWrapper, event: JobResponseReadyEvent, delegate: JobHandler):
        super().__init__()
        self._logger = logger
        self._event = event
        self._delegate = delegate
        self._commit_failed = False

    def on_transaction_declared(self, event: proton.Event) -> None:
        self._delegate.on_transaction_declared_and_job_done(event, self._event)

    def on_transaction_committed(self, event: proton.Event) -> None:
        self._delegate.on_transaction_committed(event)

    def on_transaction_aborted(self, event: proton.Event) -> None:
        self._on_transaction_failed(event)

    def on_transaction_declare_failed(self, event: proton.Event) -> None:
        self._on_transaction_failed(event)

    def on_transaction_commit_failed(self, event: proton.Event) -> None:
        if self._commit_failed:
            self._on_transaction_failed(event)
            return

        # The only time I saw the transaction_commit_failed event triggered was after a disconnect
        # and a successful re-connect. Since the built-in re-connect behavior handles retries,
        # there is no reason to re-attempt the transaction more than once.
        self._logger.warn(
            'Committing the transaction failed. Will re-attempt the transaction once.')
        self._commit_failed = True
        assert event.connection
        event.container.declare_transaction(event.connection, self, settle_before_discharge=True)

    def _on_transaction_failed(self, event: proton.Event) -> None:
        if description := util.get_condition_description(event):
            raise TransactionError(f'Transaction failed due to: {description}')
        raise TransactionError('A transaction failed.')


class TransactionError(Exception):
    pass


class JobExecutor:
    def __init__(
            self,
            logger: LoggerWrapper,
            config: ExecutorConfig,
            job_request_queue: queue.Queue[Optional[proton.Event]],
            job_done_injector: proton.reactor.EventInjector) -> None:
        self._logger = logger
        self._config = config
        self._job_request_queue = job_request_queue
        self._job_done_injector = job_done_injector
        # Execute jobs on a background thread to avoid blocking the proton Container event loop.
        # The Container needs to still handle connection control and flow messages while the job is
        # running. Blocking for too long will cause the broker to think the client disconnected.
        threading.Thread(target=self._run).start()

    def _run(self) -> None:
        try:
            component = self._load_component()
            self._logger.info('Successfully loaded component.')
        except util.ComponentLoadError as e:
            self._job_done_injector.trigger(ComponentErrorEvent(e))
            return
        except Exception as e:
            self._job_done_injector.trigger(ComponentErrorEvent(
                util.ComponentLoadError(str(e)), e))
            return

        try:
            self._run_jobs(component)
        except Exception as e:
            self._job_done_injector.trigger(ComponentErrorEvent(e))
            return


    def _run_jobs(self, component) -> None:
        while True:
            job_event = self._job_request_queue.get()
            if job_event is None:
                # None will be inserted in to the queue when it is time for the thread to exit.
                return
            request_message: proton.Message = job_event.message
            assert isinstance(request_message.body, memoryview)

            pb_job = pb_sub.SubjectTrackingJob()
            pb_job.ParseFromString(request_message.body)
            with self._logger.get_logger_context(pb_job.job_name):
                self._run_job(component, pb_job, job_event)


    def _run_job(self, component, job: pb_sub.SubjectTrackingJob, job_request_event: proton.Event):
        self._logger.info('Sending job to component.')
        pb_result = component.get_subjects(job)
        self._logger.info('Received response from component.')

        result_bytes = pb_result.SerializeToString()
        response_message = self._create_response_message(job_request_event, result_bytes)
        self._job_done_injector.trigger(JobResponseReadyEvent(job_request_event, response_message))


    @staticmethod
    def _create_response_message(
            job_request_event: proton.Event,
            result_bytes: bytes) -> proton.Message:
        request_message: proton.Message = job_request_event.message
        response_props = {}
        if request_props := request_message.properties:
            if job_id := request_props.get('JobId'):
                response_props['JobId'] = job_id
            if breadcrumb := request_props.get('breadcrumbId'):
                response_props['breadcrumbId'] = breadcrumb

        return util.create_message(
                result_bytes,
                address=request_message.reply_to,
                correlation_id=request_message.correlation_id,
                properties=response_props)


    def _load_component(self):
        if self._config.is_python:
            from . import python_component
            return python_component.PythonComponent(self._logger, self._config.lib)
        else:
            raise NotImplementedError('Only Python components are currently supported.')


class JobResponseReadyEvent(proton.reactor.ApplicationEvent):
    def __init__(self, request_event: proton.Event, response: proton.Message) -> None:
        super().__init__('job_response_ready')
        assert request_event.delivery
        self.request_delivery = request_event.delivery
        self._handler = request_event.handler
        self.response = response

    @property
    def handler(self) -> Optional[proton.Handler]:
        return self._handler


class ComponentErrorEvent(proton.reactor.ApplicationEvent):
    def __init__(self, exception: Exception, cause: Optional[Exception] = None):
        super().__init__('component_error', subject=(exception, cause))

    def raise_exception(self) -> NoReturn:
        exception, cause = self.subject
        if cause:
            raise exception from cause
        else:
            raise exception


class LogCtxManager:
    def __init__(self, logger: LoggerWrapper) -> None:
        self._logger = logger
        self._exit_stack = contextlib.ExitStack()

    def begin_job(self, message: str) -> None:
        self._exit_stack.enter_context(self._logger.get_logger_context(message))

    def end_job(self) -> None:
        self._exit_stack.pop_all().close()
