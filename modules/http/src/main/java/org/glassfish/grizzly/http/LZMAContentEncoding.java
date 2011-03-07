/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
package org.glassfish.grizzly.http;

import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.compression.lzma.LZMADecoder;
import org.glassfish.grizzly.compression.lzma.LZMAEncoder;

public class LZMAContentEncoding implements ContentEncoding {

    public static final String[] ALIASES = { "lzma" };

    private static final String NAME = "lzma";

    private final LZMADecoder decoder;
    private final LZMAEncoder encoder;

    private final EncodingFilter encodingFilter;


    // ------------------------------------------------------------ Constructors


    public LZMAContentEncoding() {
        this(null);
    }

    public LZMAContentEncoding(EncodingFilter encodingFilter) {
        decoder = new LZMADecoder();
        encoder = new LZMAEncoder();
        if (encodingFilter != null) {
            this.encodingFilter = encodingFilter;
        } else {
            this.encodingFilter = new EncodingFilter() {
                @Override
                public boolean applyEncoding(final HttpHeader httpPacket) {
                    return false;
                }

                @Override
                public boolean applyDecoding(final HttpHeader httpPacket) {
                    return true;
                }
            };
        }
    }

    // -------------------------------------------- Methods from ContentEncoding


    @Override
    public String getName() {
        return NAME;
    }

    @Override
    public String[] getAliases() {
        return ALIASES;
    }

    @Override
    public boolean wantDecode(HttpHeader header) {
        return encodingFilter.applyDecoding(header);
    }

    @Override
    public boolean wantEncode(HttpHeader header) {
        return encodingFilter.applyEncoding(header);
    }

    @Override
    public ParsingResult decode(Connection connection, HttpContent httpContent) {
        final Buffer input = httpContent.getContent();
        final TransformationResult<Buffer, Buffer> result =
                decoder.transform(connection, input);

        final Buffer remainder = result.getExternalRemainder();

        if (remainder == null) {
            input.tryDispose();
        } else {
            input.shrink();
        }

        try {
            switch (result.getStatus()) {
                case COMPLETE: {
                    httpContent.setContent(result.getMessage());
                    decoder.finish(connection);
                    return ParsingResult.create(httpContent, remainder);
                }

                case INCOMPLETE: {
                    return ParsingResult.create(null, remainder);
                }

                case ERROR: {
                    throw new IllegalStateException("LZMA decode error. Code: "
                            + result.getErrorCode() + " Description: "
                            + result.getErrorDescription());
                }

                default:
                    throw new IllegalStateException("Unexpected status: " +
                            result.getStatus());
            }
        } finally {
            result.recycle();
        }
    }

    @Override
    public HttpContent encode(Connection connection, HttpContent httpContent) {

        final Buffer input = httpContent.getContent();
        final TransformationResult<Buffer, Buffer> result =
                encoder.transform(connection, input);
        input.dispose();

        try {
            switch (result.getStatus()) {
                case COMPLETE:
                    encoder.finish(connection);
                case INCOMPLETE: {
                    final Buffer readyBuffer = result.getMessage();
                    if (readyBuffer != null) {
                        httpContent.setContent(readyBuffer);
                        return httpContent;
                    } else {
                        return null;
                    }
                }

                case ERROR: {
                    throw new IllegalStateException("LZMA encode error. Code: "
                            + result.getErrorCode() + " Description: "
                            + result.getErrorDescription());
                }

                default:
                    throw new IllegalStateException("Unexpected status: " +
                            result.getStatus());
            }
        } finally {
            result.recycle();
        }

    }


    // ---------------------------------------------------------- Public Methods


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        LZMAContentEncoding that = (LZMAContentEncoding) o;

        if (decoder != null ? !decoder.equals(that.decoder) : that.decoder != null)
            return false;
        if (encoder != null ? !encoder.equals(that.encoder) : that.encoder != null)
            return false;
        if (encodingFilter != null ? !encodingFilter.equals(that.encodingFilter) : that.encodingFilter != null)
            return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = decoder != null ? decoder.hashCode() : 0;
        result = 31 * result + (encoder != null ? encoder.hashCode() : 0);
        result = 31 * result + (encodingFilter != null ? encodingFilter.hashCode() : 0);
        return result;
    }
}
