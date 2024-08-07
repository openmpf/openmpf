/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2023 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2023 The MITRE Corporation                                       *
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

#include "JniHelper.h"


JniHelper::JniHelper(JNIEnv *env)
    : env_(env) {
}

jclass JniHelper::FindClass(const char *name) {
    return callJni(&JNIEnv::FindClass, name);
}


jclass JniHelper::GetObjectClass(jobject obj) {
    return callJni(&JNIEnv::GetObjectClass, obj);
}


jmethodID JniHelper::GetMethodID(jclass clz, const char *name, const char *signature) {
    return callJni(&JNIEnv::GetMethodID, clz, name, signature);
}

jmethodID JniHelper::GetStaticMethodID(jclass clz, const char *name, const char *signature) {
    return callJni(&JNIEnv::GetStaticMethodID, clz, name, signature);
}

jfieldID JniHelper::GetStaticFieldID(jclass clz, const char *name, const char *signature) {
    return callJni(&JNIEnv::GetStaticFieldID, clz, name, signature);
}

jint JniHelper::GetStaticIntField(jclass clz, jfieldID fieldId) {
    return callJni(&JNIEnv::GetStaticIntField, clz, fieldId);
}

jint JniHelper::ThrowNew(jclass clz, const char *msg) {
    return env_->ThrowNew(clz, msg);
}


std::string JniHelper::ToStdString(jstring jString) {
    const char* chars = callJni(&JNIEnv::GetStringUTFChars, jString, nullptr);
    std::string result(chars);
    callJniVoid(&JNIEnv::ReleaseStringUTFChars, jString, chars);
    return result;
}


bool JniHelper::ToBool(jstring jString) {
    std::string stdString = ToStdString(jString);

    if (stdString == "1") {
        return true;
    }

    static const std::string trueString = "TRUE";
    return std::equal(trueString.begin(), trueString.end(), stdString.begin(), [](char trueChar, char actualChar) {
        return trueChar == std::toupper(actualChar);
    });
}

ByteArray JniHelper::GetByteArray(jbyteArray byte_array) {
    ByteArray result(env_, byte_array);
    CheckException();
    return result;
}


std::unique_ptr<jstring, JStringDeleter> JniHelper::ToJString(const std::string &inString) {
    return {
        new jstring(callJni(&JNIEnv::NewStringUTF, inString.c_str())),
        JStringDeleter(env_)
    };
}


void JniHelper::CheckException() {
    if (env_->ExceptionCheck()) {
        throw JniException();
    }
}


void JniHelper::ReportCppException(const char * msg) {
    if (env_->ExceptionCheck()) {
        // There is already a pending JVM exception that will be thrown when JNI call returns,
        // so we don't want to replace that exception.
        return;
    }

    if (msg == nullptr) {
        msg = "An unexpected error occurred in JNI code.";
    }

    jclass exceptionClz = env_->FindClass("java/lang/IllegalStateException");
    ThrowNew(exceptionClz, msg);
}


void JniHelper::ReportCppException(const std::string& msg) {
    ReportCppException(msg.c_str());
}



JStringDeleter::JStringDeleter(JNIEnv *env) : env_(env) {
}

void JStringDeleter::operator()(jstring *str) {
    env_->DeleteLocalRef(*str);
    delete str;
}


LocalJniFrame::LocalJniFrame(JNIEnv* env, jint capacity)
        : env_(env) {
    jint rc = env->PushLocalFrame(capacity);
    if (rc < 0) {
        // An OutOfMemoryError exception has been set and will be reported when the
        // JNI function returns.
        throw JniException();
    }
}

LocalJniFrame::~LocalJniFrame() {
    env_->PopLocalFrame(nullptr);
}


ByteArray::ByteArray(JNIEnv* env, jbyteArray byte_array)
    : env_(env)
    , j_array_(byte_array)
    , length_(env->GetArrayLength(byte_array))
    , bytes_(env->GetByteArrayElements(byte_array, nullptr)) {
}

ByteArray::ByteArray(ByteArray&& other) noexcept
    : env_(std::exchange(other.env_, nullptr))
    , j_array_(std::exchange(other.j_array_, nullptr))
    , length_(std::exchange(other.length_, 0))
    , bytes_(std::exchange(other.bytes_, nullptr))
{
}

int ByteArray::GetLength() const {
    return length_;
}

jbyte* ByteArray::GetBytes() const {
    return bytes_;
}

ByteArray::~ByteArray() {
    if (env_ != nullptr) {
        env_->ReleaseByteArrayElements(j_array_, bytes_, 0);
    }
}
