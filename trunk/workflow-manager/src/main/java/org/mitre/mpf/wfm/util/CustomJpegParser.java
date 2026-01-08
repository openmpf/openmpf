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


package org.mitre.mpf.wfm.util;

import com.drew.metadata.Directory;
import com.drew.metadata.MetadataException;
import com.drew.metadata.jpeg.JpegDirectory;
import org.apache.tika.exception.TikaException;
import org.apache.tika.io.TemporaryResources;
import org.apache.tika.io.TikaInputStream;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.image.ImageMetadataExtractor;
import org.apache.tika.parser.image.JpegParser;
import org.apache.tika.parser.xmp.JempboxExtractor;
import org.apache.tika.sax.XHTMLContentHandler;
import org.xml.sax.ContentHandler;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.stream.Stream;


/**
 * Tika's JpegParser allows EXIF dimensions to override the dimensions in the SOF0 segment.
 * The dimensions in EXIF and SOF0 should be the same, but we have seen at least one JPEG
 * where they were different.
 * This class changes the order in which the JPEG segments are processed so that SOF0 is
 * processed last.
 */
public class CustomJpegParser extends JpegParser {

    /**
     * The body of this method was copied from the base class, except for the line where it calls
     * the ImageMetadataExtractor.
     */
    @Override
    public void parse(InputStream stream, ContentHandler handler, Metadata metadata,
                      ParseContext context) throws IOException, SAXException, TikaException {

        TikaInputStream tis = TikaInputStream.get(stream);
        // This is the original line from the base class method:
        // new ImageMetadataExtractor(metadata).parseJpeg(tis.getFile());
        new CustomMetadataExtractor(metadata).parseJpeg(tis.getFile());
        new JempboxExtractor(metadata).parse(tis);

        XHTMLContentHandler xhtml = new XHTMLContentHandler(handler, metadata);
        xhtml.startDocument();
        xhtml.endDocument();
    }


    private static class CustomMetadataExtractor extends ImageMetadataExtractor {

        public CustomMetadataExtractor(Metadata metadata) {
            super(metadata);
        }

        /**
         * This method changes the order in which the directories are processed so that the
         * directory corresponding to the SOF0 segment is processed last. There are
         * directories for the EXIF data as well as the metadata segments. Processing SOF0 last
         * ensures that the SOF0 metadata overrides any metadata in the EXIF segment.
         */
        @Override
        protected void handle(Iterator<Directory> directories) throws MetadataException {
            var begin = Stream.<Directory>builder();
            var end = Stream.<Directory>builder();

            while (directories.hasNext()) {
                var next = directories.next();
                if (next instanceof JpegDirectory) {
                    end.add(next);
                }
                else {
                    begin.add(next);
                }
            }
            var reordered = Stream.concat(begin.build(), end.build());
            super.handle(reordered.iterator());
        }
    }
}
