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

#ifndef MPF_JNIHELPER_H
#define MPF_JNIHELPER_H

#include <exception>
#include <memory>
#include <string>

#include <jni.h>

class JniException : public std::exception { };

class JStringDeleter;
class ByteArray;


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
    jdouble CallFloatMethod(jobject obj, jmethodID method, Args... args) {
        return callJni(&JNIEnv::CallFloatMethod, obj, method, args...);
    }

    template <typename ...Args>
    jobject CallConstructorMethod(jclass clz, jmethodID method, Args... args) {
        return callJni(&JNIEnv::NewObject, clz, method, args...);
    }

    jfieldID GetStaticFieldID(jclass clz, const char * name, const char * signature);

    jint GetStaticIntField(jclass clz, jfieldID fieldId);

    jint ThrowNew(jclass clz, const char * msg);


    void ReportCppException(const char * msg = nullptr);

    void ReportCppException(const std::string& msg);

    std::string ToStdString(jstring jString);

    std::unique_ptr<jstring, JStringDeleter> ToJString(const std::string &stdString);

    bool ToBool(jstring jString);

    ByteArray GetByteArray(jbyteArray byte_array);

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


class JStringDeleter {
public:
    explicit JStringDeleter(JNIEnv * env);

    void operator()(jstring* str);

private:
    JNIEnv * env_;
};


class LocalJniFrame {
public:
    LocalJniFrame(JNIEnv *env, jint capacity);

    // Deletes all local references created since the constructor was called.
    ~LocalJniFrame();

private:
    JNIEnv * env_;
};

class ByteArray {
public:
    ByteArray(JNIEnv* env, jbyteArray byte_array);

    ByteArray(ByteArray&&) noexcept;

    ByteArray(const ByteArray&) = delete;
    ByteArray& operator=(const ByteArray&) = delete;
    ByteArray& operator=(ByteArray&&) = delete;

    ~ByteArray();

    int GetLength() const;

    jbyte* GetBytes() const;

private:
    JNIEnv* env_;

    jbyteArray j_array_;

    int length_;

    jbyte* bytes_;
};

#endif //MPF_JNIHELPER_H
