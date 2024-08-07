/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2024 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2024 The MITRE Corporation                                       *
 *                                                                            *
 * Licensed under the Apache License, Version 2.0 (the "License");            *
 * you may not use this file except in compliance with the License.           *
 * You may obtain a copy of the License at                                    *
 *                                                                            *
 *    http://www.apache.org/licenses/LICENSE-2.0                              *
 *                                                                            *
 * Unless required by applicable law or agreed to in writing, software        *
 * distributed under the License is distributed on an "AS IS" BASIS,          *
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   *
 * See the License for the specific language governing permissions and        *
 * limitations under the License.                                             *
 ******************************************************************************/

#include <utility>

#include <pybind11/embed.h>
#include <pybind11/pybind11.h>

#include "PythonLogger.h"

namespace py = pybind11;

namespace MPF {
    // The Python logging documentation recommends using a filter to add contextual information to
    // logs.
    // https://docs.python.org/3.8/howto/logging-cookbook.html#using-filters-to-impart-contextual-information
    class AddLogContextFilter {
    public:
        bool Filter(py::handle log_record) const {
            log_record.attr("ctx") = ctx_;
            return true;
        }

        void SetContext(std::string_view context) {
            ctx_ = context;
        }

    private:
        py::str ctx_{""};
    };
} // namespace MPF


PYBIND11_EMBEDDED_MODULE(log_ctx_filter, m) {
    py::class_<
            // Create Python binding for AddLogContextFilter so it can be passed to Python code.
            MPF::AddLogContextFilter,
            // Use a std::shared_ptr for the holder type so that the same AddLogContextFilter
            // instance can be referenced from both C++ and Python code.
            std::shared_ptr<MPF::AddLogContextFilter>
        >(m, "AddLogContextFilter")
        .def(py::init<>())
        .def("filter", &MPF::AddLogContextFilter::Filter);
}


namespace MPF {


class PythonLogger::Impl {
public:
    Impl(std::string_view logger_name, std::string_view log_level)
        : Impl(CreateFilter(log_level), GetLogger(logger_name)) {
    }

    std::shared_ptr<AddLogContextFilter> ctx_filter;
    py::function debug;
    py::function info;
    py::function warn;
    py::function error;
    py::function fatal;

private:
    Impl(std::shared_ptr<AddLogContextFilter> filter, py::handle logger)
        : ctx_filter{std::move(filter)}
        , debug{logger.attr("debug")}
        , info{logger.attr("info")}
        , warn{logger.attr("warn")}
        , error{logger.attr("error")}
        , fatal{logger.attr("fatal")}
    {
    }

    static std::shared_ptr<AddLogContextFilter> CreateFilter(std::string_view log_level) {
        py::object filter = py::module_::import("log_ctx_filter").attr("AddLogContextFilter")();
        ConfigureLogging(log_level, filter);
        return filter.cast<std::shared_ptr<MPF::AddLogContextFilter>>();
    }


    static void ConfigureLogging(std::string_view log_level_name, py::handle log_ctx_filter) {
        static bool logging_initialized = false;
        if (logging_initialized) {
            return;
        }
        auto logging_module = py::module_::import("logging");
        // Change default level names to match what WFM expects
        // Change default level name for logger.warn and logger.warning from 'WARNING' to 'WARN'
        logging_module.attr("addLevelName")(logging_module.attr("WARN"), "WARN");
        // Change default level name for logger.fatal and logger.critical from 'CRITICAL' to 'FATAL'
        logging_module.attr("addLevelName")(logging_module.attr("FATAL"), "FATAL");

        // Creat handler to output logs to stderr.
        py::object stream_handler = logging_module.attr("StreamHandler")();
        // Add log_ctx_filter to the handler so it inherited by all loggers.
        stream_handler.attr("addFilter")(log_ctx_filter);

        py::str py_log_level = log_level_name == "TRACE"
                ? "NOTSET"  // Python doesn't use TRACE so we just log everything when TRACE is provided
                : log_level_name;

        logging_module.attr("basicConfig")(
            py::arg("format")="%(asctime)s %(levelname)-5s [%(name)s] - %(ctx)s%(message)s",
            py::arg("level")=py_log_level,
            py::arg("handlers")=py::make_tuple(stream_handler));
    }

    static py::object GetLogger(std::string_view logger_name) {
        return py::module_::import("logging").attr("getLogger")(logger_name);
    }
};



PythonLogger::PythonLogger(std::string_view log_level, std::string_view logger_name)
    : impl_{std::make_unique<PythonLogger::Impl>(logger_name, log_level)} {
}

PythonLogger::~PythonLogger() = default;

void PythonLogger::Debug(std::string_view message) {
    impl_->debug(message);
}

void PythonLogger::Info(std::string_view message) {
    impl_->info(message);
}

void PythonLogger::Warn(std::string_view message) {
    impl_->warn(message);
}

void PythonLogger::Error(std::string_view message) {
    impl_->error(message);
}

void PythonLogger::Fatal(std::string_view message) {
    impl_->fatal(message);
}

void PythonLogger::SetContextMessage(std::string_view context_msg)  {
    impl_->ctx_filter->SetContext(context_msg);
}

} // namespace MPF
