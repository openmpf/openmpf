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


package org.mitre.mpf.wfm.util;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.MessageLite;
import org.apache.camel.Exchange;
import org.apache.camel.spi.DataFormat;
import org.springframework.stereotype.Component;

import javax.inject.Inject;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Supplier;

@Component
public class ProtobufDataFormatFactory {
    private final PropertiesUtil _propertiesUtil;

    @Inject
    public ProtobufDataFormatFactory(PropertiesUtil propertiesUtil) {
        _propertiesUtil = propertiesUtil;
    }

    public DataFormat create(Supplier<MessageLite.Builder> messageBuilderSupplier) {
        return new ProtobufDataFormatWithCustomSizeLimit(_propertiesUtil, messageBuilderSupplier);
    }


    private static class ProtobufDataFormatWithCustomSizeLimit implements DataFormat {
        private final PropertiesUtil _propertiesUtil;
        private final Supplier<MessageLite.Builder> _messageBuilderSupplier;

        private ProtobufDataFormatWithCustomSizeLimit(
                PropertiesUtil propertiesUtil,
                Supplier<MessageLite.Builder> messageBuilderSupplier) {
            _propertiesUtil = propertiesUtil;
            _messageBuilderSupplier = messageBuilderSupplier;
        }

        @Override
        public void marshal(Exchange exchange, Object graph, OutputStream outputStream)
                throws IOException {
            ((MessageLite) graph).writeTo(outputStream);
        }

        @Override
        public MessageLite unmarshal(Exchange exchange, InputStream stream) throws IOException {
            var codedInputStream = CodedInputStream.newInstance(stream);
            codedInputStream.setSizeLimit(_propertiesUtil.getProtobufSizeLimit());
            return _messageBuilderSupplier.get().mergeFrom(codedInputStream).build();
        }
    }
}
