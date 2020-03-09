/******************************************************************************
 * NOTICE                                                                     *
 *                                                                            *
 * This software (or technical data) was produced for the U.S. Government     *
 * under contract, and is subject to the Rights in Data-General Clause        *
 * 52.227-14, Alt. IV (DEC 2007).                                             *
 *                                                                            *
 * Copyright 2019 The MITRE Corporation. All Rights Reserved.                 *
 ******************************************************************************/

/******************************************************************************
 * Copyright 2019 The MITRE Corporation                                       *
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

jstring JniHelper::ToJString(std::string inString) {
	return callJni(&JNIEnv::NewStringUTF,inString.c_str());
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

