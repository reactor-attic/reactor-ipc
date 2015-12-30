/*
 * Copyright (c) 2011-2016 Pivotal Software Inc, All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package reactor.io.net.preprocessor;

import io.netty.buffer.ByteBuf;
import org.reactivestreams.Publisher;
import reactor.core.publisher.convert.DependencyUtils;
import reactor.fn.Function;
import reactor.io.buffer.Buffer;
import reactor.io.codec.Codec;
import reactor.io.net.Preprocessor;
import reactor.io.net.ReactiveChannel;
import reactor.io.net.http.HttpChannel;
import reactor.io.net.http.HttpProcessor;
import reactor.io.net.http.model.*;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Map;

/**
 * @author Stephane Maldini
 * @since 2.5
 */
public final class CodecPreprocessor<IN, OUT>
    implements Preprocessor<ByteBuf, ByteBuf, ReactiveChannel<ByteBuf, ByteBuf>, IN, OUT, ReactiveChannel<IN, OUT>>,
               HttpProcessor<ByteBuf, ByteBuf, HttpChannel<ByteBuf, ByteBuf>, IN, OUT, HttpChannel<IN, OUT>> {

    static {
        if (!DependencyUtils.hasReactorCodec()) {
            throw new IllegalStateException("io.projectreactor:reactor-codec:" + DependencyUtils.reactorVersion() +
                                            " dependency is missing from the classpath.");
        }

    }


    /**
     * @return
     */
    static public CodecPreprocessor<String, String> delimitedString() {
        // return from(StandardCodecs.DELIMITED_STRING_CODEC);
        // TODO
        throw new NotImplementedException();
    }

    /**
     * @param charset
     * @return
     */
    static public CodecPreprocessor<String, String> delimitedString(Charset charset) {
        // return delimitedString(charset, Codec.DEFAULT_DELIMITER);
        throw new NotImplementedException();
    }

    /**
     * @param charset
     * @param delimiter
     * @return
     */
    static public CodecPreprocessor<String, String> delimitedString(Charset charset, byte delimiter) {
        // return from(new DelimitedCodec<>(new StringCodec(delimiter, charset)));
        throw new NotImplementedException();
    }

    /**
     * @return
     */
    static public CodecPreprocessor<String, String> linefeed() {
        // return from(StandardCodecs.LINE_FEED_CODEC);
        throw new NotImplementedException();
    }

    /**
     * @param charset
     * @return
     */
    static public CodecPreprocessor<String, String> linefeed(Charset charset) {
        // return from(new DelimitedCodec<>(new StringCodec(charset)));
        throw new NotImplementedException();
    }

    /**
     * @param charset
     * @param delimiter
     * @return
     */
    static public CodecPreprocessor<String, String> linefeed(Charset charset, byte delimiter) {
        // return linefeed(charset, delimiter, true);
        throw new NotImplementedException();
    }

    /**
     * @param charset
     * @param delimiter
     * @param stripDelimiter
     * @return
     */
    static public CodecPreprocessor<String, String> linefeed(Charset charset, byte delimiter, boolean stripDelimiter) {
        // return from(new DelimitedCodec<>(delimiter, stripDelimiter, new StringCodec(charset)));
        throw new NotImplementedException();
    }

    /**
     * @return
     */
    static public CodecPreprocessor<ByteBuf, ByteBuf> passthrough() {
        //return from(StandardCodecs.PASS_THROUGH_CODEC);
        throw new NotImplementedException();
    }

    /**
     * @return
     */
    static public CodecPreprocessor<byte[], byte[]> byteArray() {
        // return from(StandardCodecs.BYTE_ARRAY_CODEC);
        throw new NotImplementedException();
    }

    /**
     * @return
     */
    static public CodecPreprocessor<String, String> string() {
        // return from(StandardCodecs.STRING_CODEC);
        return from(new StringCodec());
    }

    /**
     * @param charset
     * @return
     */
    static public CodecPreprocessor<String, String> string(Charset charset) {
        // return from(new StringCodec(charset));
        throw new NotImplementedException();
    }

    /**
     * @param tClass
     * @param <T>
     * @return
     */
    static public <T> CodecPreprocessor<T, T> json(Class<T> tClass) {
        // return from(new JsonCodec<T, T>(tClass));
        throw new NotImplementedException();
    }

    /**
     * @return
     */
    static public CodecPreprocessor<ByteBuf, ByteBuf> gzip() {
        // return from(new GzipCodec<>(StandardCodecs.PASS_THROUGH_CODEC));
        throw  new NotImplementedException();
    }

    /**
     * @param encoder
     * @param decoder
     * @param <IN>
     * @param <OUT>
     * @return
     */
    static public <IN, OUT> CodecPreprocessor<IN, OUT> from(Codec<ByteBuf, IN, ?> encoder,
                                                            Codec<ByteBuf, ?, OUT> decoder) {
        return new CodecPreprocessor<>(encoder, decoder);
    }

    /**
     * @param codec
     * @param <IN>
     * @param <OUT>
     * @return
     */
    static public <IN, OUT> CodecPreprocessor<IN, OUT> from(Codec<ByteBuf, IN, OUT> codec) {
        return from(codec, codec);
    }

    private final Codec<ByteBuf, IN, ?>  decoder;
    private final Codec<ByteBuf, ?, OUT> encoder;

    private CodecPreprocessor(
        Codec<ByteBuf, IN, ?> decoder,
        Codec<ByteBuf, ?, OUT> encoder) {
        this.encoder = encoder;
        this.decoder = decoder;
    }

    @Override
    public ReactiveChannel<IN, OUT> apply(final ReactiveChannel<ByteBuf, ByteBuf> channel) {
        if (channel == null)
            return null;
        return new CodecChannel<>(decoder, encoder, channel);
    }

    @Override
    public HttpChannel<IN, OUT> transform(HttpChannel<ByteBuf, ByteBuf> channel) {
        if (channel == null)
            return null;
        return new CodecHttpChannel<>(decoder, encoder, channel);
    }

    private static class CodecChannel<IN, OUT> implements ReactiveChannel<IN, OUT> {

        private final Codec<ByteBuf, IN, ?>  decoder;
        private final Codec<ByteBuf, ?, OUT> encoder;

        private final ReactiveChannel<ByteBuf, ByteBuf> channel;

        public CodecChannel(Codec<ByteBuf, IN, ?> decoder, Codec<ByteBuf, ?, OUT> encoder,
                            ReactiveChannel<ByteBuf, ByteBuf> channel) {
            this.encoder = encoder;
            this.decoder = decoder;
            this.channel = channel;
        }

        @Override
        public InetSocketAddress remoteAddress() {
            return channel.remoteAddress();
        }

        @Override
        public Publisher<Void> writeWith(Publisher<? extends OUT> dataStream) {
            return channel.writeWith(encoder.encode(dataStream));
        }

        @Override
        public Publisher<IN> input() {
            return decoder.decode(channel.input());
        }

        @Override
        public ConsumerSpec on() {
            return channel.on();
        }

        @Override
        public Object delegate() {
            return channel.delegate();
        }

        @Override
        public Publisher<Void> writeBufferWith(Publisher<? extends Buffer> dataStream) {
            // return channel.writeWith(dataStream);
            // TODO
            throw new NotImplementedException();
        }
    }

    private final static class CodecHttpChannel<IN, OUT> implements HttpChannel<IN, OUT> {

        private final Codec<ByteBuf, IN, ?>  decoder;
        private final Codec<ByteBuf, ?, OUT> encoder;

        private final HttpChannel<ByteBuf, ByteBuf> channel;

        public CodecHttpChannel(Codec<ByteBuf, IN, ?> decoder,
                                Codec<ByteBuf, ?, OUT> encoder,
                                HttpChannel<ByteBuf, ByteBuf> channel) {
            this.decoder = decoder;
            this.encoder = encoder;
            this.channel = channel;
        }

        @Override
        public HttpHeaders headers() {
            return channel.headers();
        }

        @Override
        public boolean isKeepAlive() {
            return channel.isKeepAlive();
        }

        @Override
        public HttpChannel<IN, OUT> keepAlive(boolean keepAlive) {
            channel.keepAlive(keepAlive);
            return this;
        }

        @Override
        public HttpChannel<IN, OUT> addHeader(String name, String value) {
            channel.addHeader(name, value);
            return this;
        }

        @Override
        public Protocol protocol() {
            return channel.protocol();
        }

        @Override
        public String uri() {
            return channel.uri();
        }

        @Override
        public Method method() {
            return channel.method();
        }

        @Override
        public Status responseStatus() {
            return channel.responseStatus();
        }

        @Override
        public HttpChannel<IN, OUT> responseStatus(Status status) {
            channel.responseStatus(status);
            return this;
        }

        @Override
        public HttpChannel<IN, OUT> paramsResolver(
            Function<? super String, Map<String, Object>> headerResolver) {
            channel.paramsResolver(headerResolver);
            return this;
        }

        @Override
        public ResponseHeaders responseHeaders() {
            return channel.responseHeaders();
        }

        @Override
        public HttpChannel<IN, OUT> addResponseHeader(String name, String value) {
            channel.addResponseHeader(name, value);
            return this;
        }

        @Override
        public Publisher<Void> writeHeaders() {
            return channel.writeHeaders();
        }

        @Override
        public HttpChannel<IN, OUT> sse() {
            channel.sse();
            return this;
        }

        @Override
        public Transfer transfer() {
            return channel.transfer();
        }

        @Override
        public HttpChannel<IN, OUT> transfer(Transfer transfer) {
            channel.transfer(transfer);
            return this;
        }

        @Override
        public boolean isWebsocket() {
            return channel.isWebsocket();
        }

        @Override
        public InetSocketAddress remoteAddress() {
            return channel.remoteAddress();
        }

        @Override
        public Publisher<Void> writeBufferWith(Publisher<? extends Buffer> dataStream) {
            // return channel.writeBufferWith(dataStream);
            // TODO
            throw new NotImplementedException();
        }

        @Override
        public Publisher<IN> input() {
            return decoder.decode(channel.input());
        }

        @Override
        public ConsumerSpec on() {
            return channel.on();
        }

        @Override
        public Object delegate() {
            return channel.delegate();
        }

        @Override
        public HttpChannel<IN, OUT> header(String name, String value) {
            channel.header(name, value);
            return this;
        }

        @Override
        public Object param(String key) {
            return channel.param(key);
        }

        @Override
        public HttpChannel<IN, OUT> responseHeader(String name, String value) {
            channel.responseHeader(name, value);
            return this;
        }

        @Override
        public Map<String, Object> params() {
            return channel.params();
        }

        @Override
        public Publisher<Void> writeWith(Publisher<? extends OUT> source) {
            return channel.writeWith(encoder.encode(source));
        }
    }
}
