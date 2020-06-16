/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2020 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2020 The MITRE Corporation                                       *
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

#ifndef MPF_JNIHELPER_H
#define MPF_JNIHELPER_H

#include <exception>
#include <string>

#include <jni.h>

class JniException : public std::exception { };

class JniHelper {

public:
    explicit JniHelper(JNIEnv* env);


    jclass FindClass(const char * name);

    jclass GetObjectClass(jobject obj);

    jmethodID GetMethodID(jclass clz, const char * name, const char * signature);

    jmethodID GetStaticMethodID(jclass clz, const char *name, const char *signature);


    template <typename ...Args>
    jobject CallObjectMethod(jobject obj, jmethodID method, Args... args) {
        return callJni(&JNIEnv::CallObjectMethod, obj, method, args...);
    }

    template <typename ...Args>
    jboolean CallBooleanMethod(jobject obj, jmethodID method, Args... args) {
        return callJni(&JNIEnv::CallBooleanMethod, obj, method, args...);
    }

    template <typename ...Args>
    jobject CallStaticObjectMethod(jclass clz, jmethodID method, Args... args) {
        return callJni(&JNIEnv::CallStaticObjectMethod, clz, method, args...);
    }

    template <typename ...Args>
    jint CallIntMethod(jobject obj, jmethodID method, Args... args)  {
        return callJni(&JNIEnv::CallIntMethod, obj, method, args...);
    }

    template <typename ...Args>
    jdouble CallDoubleMethod(jobject obj, jmethodID method, Args... args) {
        return callJni(&JNIEnv::CallDoubleMethod, obj, method, args...);
    }

    template <typename ...Args>
    jobject CallConstructorMethod(jclass clz, jmethodID method, Args... args) {
        return callJni(&JNIEnv::NewObject, clz, method, args...);
    }

    jfieldID GetStaticFieldID(jclass clz, const char * name, const char * signature);

    jint GetStaticIntField(jclass clz, jfieldID fieldId);

    jint ThrowNew(jclass clz, const char * msg);


    void ReportCppException(const char * msg = nullptr);

    std::string ToStdString(jstring jString);
    jstring ToJString(std::string StdString);


private:
    JNIEnv * const env_;


    void CheckException();


    template <typename Method, typename... Args>
    auto callJni(Method method, Args... args) -> decltype((env_->*method)(args...)) {
        auto result = (env_->*method)(args...);
        CheckException();
        return result;
    }


    template <typename Method, typename... Args>
    void callJniVoid(Method method, Args... args) {
        (env_->*method)(args...);
        CheckException();
    }
};


#endif //MPF_JNIHELPER_H
