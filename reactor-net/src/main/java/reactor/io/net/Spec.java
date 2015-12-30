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
package reactor.io.net;

import io.netty.buffer.ByteBuf;
import reactor.core.support.Assert;
import reactor.core.timer.Timer;
import reactor.fn.Supplier;
import reactor.fn.tuple.Tuple;
import reactor.fn.tuple.Tuple2;
import reactor.io.net.config.ClientSocketOptions;
import reactor.io.net.config.ServerSocketOptions;
import reactor.io.net.config.SslOptions;
import reactor.io.net.http.HttpChannel;
import reactor.io.net.http.HttpClient;
import reactor.io.net.http.HttpProcessor;
import reactor.io.net.http.HttpServer;
import reactor.io.net.tcp.TcpClient;
import reactor.io.net.tcp.TcpServer;
import reactor.io.net.udp.DatagramServer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.lang.reflect.Constructor;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Specifications used to build client and servers.
 *
 * @author Stephane Maldini
 * @author Jon Brisbin
 * @since 2.0, 2.5
 */
public interface Spec {

    //
    //   Client and Server Specifications
    //
    public abstract class PeerSpec<IN, OUT,
        CONN extends ReactiveChannel<IN, OUT>,
        S extends PeerSpec<IN, OUT, CONN, S, N>,
        N extends ReactivePeer<IN, OUT, CONN>>
        implements Supplier<N> {

        protected ServerSocketOptions                                                                              options;
        protected InetSocketAddress                                                                                listenAddress;
        protected Timer                                                                                            timer;
        protected Preprocessor<ByteBuf, ByteBuf, ReactiveChannel<ByteBuf, ByteBuf>, IN, OUT, ReactiveChannel<IN, OUT>> preprocessor;

        /**
         * Set the common {@link ServerSocketOptions} for channels made in this server.
         *
         * @param options The options to set when new channels are made.
         * @return {@literal this}
         */
        @SuppressWarnings("unchecked")
        public S options(@Nonnull ServerSocketOptions options) {
            Assert.notNull(options, "ServerSocketOptions cannot be null.");
            this.options = options;
            return (S) this;
        }

        /**
         * The port on which this server should listen, assuming it should bind to all available addresses.
         *
         * @param port The port to listen on.
         * @return {@literal this}
         */
        @SuppressWarnings("unchecked")
        public S listen(int port) {
            return listen(new InetSocketAddress(port));
        }

        /**
         * The host and port on which this server should listen.
         *
         * @param host The host to bind to.
         * @param port The port to listen on.
         * @return {@literal this}
         */
        @SuppressWarnings("unchecked")
        public S listen(String host, int port) {
            if (null == host) {
                host = "localhost";
            }
            return listen(new InetSocketAddress(host, port));
        }

        /**
         * The {@link InetSocketAddress} on which this server should listen.
         *
         * @param listenAddress the listen address
         * @return {@literal this}
         */
        @SuppressWarnings("unchecked")
        public S listen(InetSocketAddress listenAddress) {
            this.listenAddress = listenAddress;
            return (S) this;
        }

        /**
         * Set the default {@link reactor.core.timer.Timer} for timed operations.
         *
         * @param timer The timer to assign by default
         * @return {@literal this}
         */
        @SuppressWarnings("unchecked")
        public S timer(Timer timer) {
            this.timer = timer;
            return (S) this;
        }

        /**
         * The channel interceptor, e.g. to use to encode and decode data with
         * {@link reactor.io.net.preprocessor.CodecPreprocessor}
         *
         * @param preprocessor The codec to use.
         * @return {@literal this}
         */
        @SuppressWarnings("unchecked")
        public S preprocessor(
            Preprocessor<ByteBuf, ByteBuf, ReactiveChannel<ByteBuf, ByteBuf>, IN, OUT, ReactiveChannel<IN, OUT>> preprocessor) {
            Assert.notNull(preprocessor, "Preprocessor cannot be null.");
            this.preprocessor = preprocessor;
            return (S) this;
        }

    }

    /**
     * A helper class for specifying a {@code TcpClient}
     *
     * @param <IN>  The type that will be received by the client
     * @param <OUT> The type that will be sent by the client
     * @author Jon Brisbin
     * @author Stephane Maldini
     */
    public class TcpClientSpec<IN, OUT> implements Supplier<TcpClient<IN, OUT>> {

        private final Constructor<TcpClient> clientImplConstructor;

        private InetSocketAddress connectAddress;

        private ClientSocketOptions options;

        private SslOptions sslOptions = null;
        private Timer      timer      = null;
        protected Preprocessor<ByteBuf, ByteBuf, ReactiveChannel<ByteBuf, ByteBuf>, IN, OUT, ReactiveChannel<IN, OUT>> preprocessor;

        /**
         * Create a {@code TcpClient.Spec} using the given implementation class.
         *
         * @param clientImpl The concrete implementation of {@link TcpClient} to instantiate.
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        TcpClientSpec(@Nonnull Class<? extends TcpClient> clientImpl) {
            Assert.notNull(clientImpl, "TcpClient implementation class cannot be null.");
            try {
                this.clientImplConstructor = (Constructor<TcpClient>) clientImpl
                    .getDeclaredConstructor(
                        Timer.class,
                        InetSocketAddress.class,
                        ClientSocketOptions.class,
                        SslOptions.class
                    );
                this.clientImplConstructor.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(
                    "No public constructor found that matches the signature of the one found in the TcpClient class.");
            }
        }

        /**
         * Set the common {@link ClientSocketOptions} for connections made in this client.
         *
         * @param options The socket options to apply to new connections.
         * @return {@literal this}
         */
        public TcpClientSpec<IN, OUT> options(ClientSocketOptions options) {
            this.options = options;
            return this;
        }

        /**
         * Set the default {@link reactor.core.timer.Timer} for timed operations.
         *
         * @param timer The timer to assign by default
         * @return {@literal this}
         */
        public TcpClientSpec<IN, OUT> timer(Timer timer) {
            this.timer = timer;
            return this;
        }

        /**
         * Set the options to use for configuring SSL. Setting this to {@code null} means don't use SSL at all (the
         * default).
         *
         * @param sslOptions The options to set when configuring SSL
         * @return {@literal this}
         */
        public TcpClientSpec<IN, OUT> ssl(@Nullable SslOptions sslOptions) {
            this.sslOptions = sslOptions;
            return this;
        }

        /**
         * The host and port to which this client should connect.
         *
         * @param host The host to connect to.
         * @param port The port to connect to.
         * @return {@literal this}
         */
        public TcpClientSpec<IN, OUT> connect(@Nonnull String host, int port) {
            return connect(new InetSocketAddress(host, port));
        }

        /**
         * The address to which this client should connect.
         *
         * @param connectAddress The address to connect to.
         * @return {@literal this}
         */
        public TcpClientSpec<IN, OUT> connect(@Nonnull InetSocketAddress connectAddress) {
            Assert.isNull(this.connectAddress, "Connect address is already set.");
            this.connectAddress = connectAddress;
            return this;
        }

        /**
         * The channel interceptor, e.g. to use to encode and decode data with
         * {@link reactor.io.net.preprocessor.CodecPreprocessor}
         *
         * @param preprocessor The codec to use.
         * @return {@literal this}
         */
        @SuppressWarnings("unchecked")
        public TcpClientSpec<IN, OUT> preprocessor(
            Preprocessor<ByteBuf, ByteBuf, ReactiveChannel<ByteBuf, ByteBuf>, IN, OUT, ReactiveChannel<IN, OUT>> preprocessor) {
            Assert.notNull(preprocessor, "Preprocessor cannot be null.");
            this.preprocessor = preprocessor;
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public TcpClient<IN, OUT> get() {
            try {
                TcpClient<ByteBuf, ByteBuf> client = clientImplConstructor.newInstance(
                    timer,
                    connectAddress,
                    options,
                    sslOptions
                );

                if (preprocessor != null && (options == null || !options.isRaw())) {
                    return client.preprocessor(preprocessor);
                } else {
                    return (TcpClient<IN, OUT>) client;
                }
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }

    }

    /**
     * A TcpServerSpec is used to specify a TcpServer
     *
     * @param <IN>  The type that will be received by this client
     * @param <OUT> The type that will be sent by this client
     * @author Jon Brisbin
     * @author Stephane Maldini
     */
    public class TcpServerSpec<IN, OUT>
        extends PeerSpec<IN, OUT, ReactiveChannel<IN, OUT>, TcpServerSpec<IN, OUT>, TcpServer<IN, OUT>> {

        private final Constructor<? extends TcpServer> serverImplConstructor;

        private SslOptions sslOptions = null;

        /**
         * Create a {@code TcpServer.Spec} using the given implementation class.
         *
         * @param serverImpl The concrete implementation of {@link TcpServer} to instantiate.
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        TcpServerSpec(@Nonnull Class<? extends TcpServer> serverImpl) {
            Assert.notNull(serverImpl, "TcpServer implementation class cannot be null.");
            try {
                this.serverImplConstructor = serverImpl.getDeclaredConstructor(
                    Timer.class,
                    InetSocketAddress.class,
                    ServerSocketOptions.class,
                    SslOptions.class
                );
                this.serverImplConstructor.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(
                    "No public constructor found that matches the signature of the one found in the TcpServer class.");
            }
        }

        /**
         * Set the options to use for configuring SSL. Setting this to {@code null} means don't use SSL at all (the
         * default).
         *
         * @param sslOptions The options to set when configuring SSL
         * @return {@literal this}
         */
        public TcpServerSpec<IN, OUT> ssl(@Nullable SslOptions sslOptions) {
            this.sslOptions = sslOptions;
            return this;
        }

        @SuppressWarnings("unchecked")
        @Override
        public TcpServer<IN, OUT> get() {
            try {
                TcpServer<ByteBuf, ByteBuf> server = serverImplConstructor.newInstance(
                    timer,
                    listenAddress,
                    options,
                    sslOptions
                );
                if (preprocessor != null && (options == null || !options.isRaw())) {
                    return server.preprocessor(preprocessor);
                } else {
                    return (TcpServer<IN, OUT>) server;
                }

            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }

    }


    /**
     * @author Jon Brisbin
     * @author Stephane Maldini
     */
    class DatagramServerSpec<IN, OUT>
        extends PeerSpec<IN, OUT, ReactiveChannel<IN, OUT>, DatagramServerSpec<IN, OUT>, DatagramServer<IN, OUT>> {
        protected final Constructor<? extends DatagramServer> serverImplCtor;

        private NetworkInterface multicastInterface;

        DatagramServerSpec(Class<? extends DatagramServer> serverImpl) {
            Assert.notNull(serverImpl, "NetServer implementation class cannot be null.");
            try {
                this.serverImplCtor = serverImpl.getDeclaredConstructor(
                    Timer.class,
                    InetSocketAddress.class,
                    NetworkInterface.class,
                    ServerSocketOptions.class
                );
                this.serverImplCtor.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(
                    "No public constructor found that matches the signature of the one found in the DatagramServer " +
                    "class" +
                    ".");
            }
        }

        /**
         * Set the interface to use for multicast.
         *
         * @param iface the {@link NetworkInterface} to use for multicast.
         * @return {@literal this}
         */
        public DatagramServerSpec<IN, OUT> multicastInterface(NetworkInterface iface) {
            this.multicastInterface = iface;
            return this;
        }

        @SuppressWarnings("unchecked")
        @Override
        public DatagramServer<IN, OUT> get() {
            try {
                DatagramServer<ByteBuf, ByteBuf> server =
                    serverImplCtor.newInstance(
                        timer,
                        listenAddress,
                        multicastInterface,
                        options
                    );

                if (preprocessor != null && (options == null || !options.isRaw())) {
                    return server.preprocessor(preprocessor);
                } else {
                    return (DatagramServer<IN, OUT>) server;
                }
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }

    }

    /**
     * A HttpServer Spec is used to specify an HttpServer
     *
     * @param <IN>  The type that will be received by this client
     * @param <OUT> The type that will be sent by this client
     * @author Jon Brisbin
     * @author Stephane Maldini
     */
    public class HttpServerSpec<IN, OUT>
        implements Supplier<HttpServer<IN, OUT>> {

        private final Constructor<? extends HttpServer> serverImplConstructor;

        protected ServerSocketOptions options;
        protected InetSocketAddress   listenAddress;
        protected Timer               timer;
        protected HttpProcessor<ByteBuf, ByteBuf, HttpChannel<ByteBuf, ByteBuf>, IN, OUT, HttpChannel<IN, OUT>>
                                      httpPreprocessor;

        private SslOptions sslOptions = null;

        /**
         * Create a {@code TcpServer.Spec} using the given implementation class.
         *
         * @param serverImpl The concrete implementation of {@link HttpClient} to instantiate.
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpServerSpec(@Nonnull Class<? extends HttpServer> serverImpl) {
            Assert.notNull(serverImpl, "TcpServer implementation class cannot be null.");
            try {
                this.serverImplConstructor = serverImpl.getDeclaredConstructor(
                    Timer.class,
                    InetSocketAddress.class,
                    ServerSocketOptions.class,
                    SslOptions.class
                );
                this.serverImplConstructor.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(
                    "No public constructor found that matches the signature of the one found in the TcpServer class.");
            }
        }

        /**
         * Set the common {@link ServerSocketOptions} for channels made in this server.
         *
         * @param options The options to set when new channels are made.
         * @return {@literal this}
         */
        @SuppressWarnings("unchecked")
        public HttpServerSpec<IN, OUT> options(@Nonnull ServerSocketOptions options) {
            Assert.notNull(options, "ServerSocketOptions cannot be null.");
            this.options = options;
            return this;
        }

        /**
         * The port on which this server should listen, assuming it should bind to all available addresses.
         *
         * @param port The port to listen on.
         * @return {@literal this}
         */
        @SuppressWarnings("unchecked")
        public HttpServerSpec<IN, OUT> listen(int port) {
            return listen(new InetSocketAddress(port));
        }

        /**
         * The host and port on which this server should listen.
         *
         * @param host The host to bind to.
         * @param port The port to listen on.
         * @return {@literal this}
         */
        @SuppressWarnings("unchecked")
        public HttpServerSpec<IN, OUT> listen(String host, int port) {
            if (null == host) {
                host = "localhost";
            }
            return listen(new InetSocketAddress(host, port));
        }

        /**
         * The {@link InetSocketAddress} on which this server should listen.
         *
         * @param listenAddress the listen address
         * @return {@literal this}
         */
        @SuppressWarnings("unchecked")
        public HttpServerSpec<IN, OUT> listen(InetSocketAddress listenAddress) {
            this.listenAddress = listenAddress;
            return this;
        }

        /**
         * Set the default {@link reactor.core.timer.Timer} for timed operations.
         *
         * @param timer The timer to assign by default
         * @return {@literal this}
         */
        @SuppressWarnings("unchecked")
        public HttpServerSpec<IN, OUT> timer(Timer timer) {
            this.timer = timer;
            return this;
        }

        public HttpServerSpec<IN, OUT> httpProcessor(
            HttpProcessor<ByteBuf, ByteBuf, HttpChannel<ByteBuf, ByteBuf>, IN, OUT, HttpChannel<IN, OUT>> preprocessor) {
            this.httpPreprocessor = preprocessor;
            return this;
        }

        /**
         * Set the options to use for configuring SSL. Setting this to {@code null} means don't use SSL at all (the
         * default).
         *
         * @param sslOptions The options to set when configuring SSL
         * @return {@literal this}
         */
        public HttpServerSpec<IN, OUT> ssl(@Nullable SslOptions sslOptions) {
            this.sslOptions = sslOptions;
            return this;
        }

        @SuppressWarnings("unchecked")
        @Override
        public HttpServer<IN, OUT> get() {
            try {
                HttpServer<ByteBuf, ByteBuf> server = serverImplConstructor.newInstance(
                    timer,
                    listenAddress,
                    options,
                    sslOptions
                );

                if (httpPreprocessor != null && (options == null || !options.isRaw())) {
                    return server.httpProcessor(httpPreprocessor);
                } else {
                    return (HttpServer<IN, OUT>) server;
                }
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }

    }

    /**
     * A helper class for specifying a {@code HttpClient}
     *
     * @param <IN>  The type that will be received by the client
     * @param <OUT> The type that will be sent by the client
     * @author Stephane Maldini
     */
    public class HttpClientSpec<IN, OUT> implements Supplier<HttpClient<IN, OUT>> {

        private final Constructor<HttpClient> clientImplConstructor;

        private InetSocketAddress   connectAddress;
        private ClientSocketOptions options;
        private SslOptions sslOptions = null;
        protected HttpProcessor<ByteBuf, ByteBuf, HttpChannel<ByteBuf, ByteBuf>, IN, OUT, HttpChannel<IN, OUT>>
            httpPreprocessor;
        private Timer timer = null;

        /**
         * Create a {@code TcpClient.Spec} using the given implementation class.
         *
         * @param clientImpl The concrete implementation of {@link HttpClient} to instantiate.
         */
        @SuppressWarnings({"unchecked", "rawtypes"})
        HttpClientSpec(@Nonnull Class<? extends HttpClient> clientImpl) {
            Assert.notNull(clientImpl, "TcpClient implementation class cannot be null.");
            try {
                this.clientImplConstructor = (Constructor<HttpClient>) clientImpl
                    .getDeclaredConstructor(
                        Timer.class,
                        InetSocketAddress.class,
                        ClientSocketOptions.class,
                        SslOptions.class
                    );
                this.clientImplConstructor.setAccessible(true);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException(
                    "No public constructor found that matches the signature of the one found in the TcpClient class.");
            }
        }

        /**
         * Set the common {@link ClientSocketOptions} for connections made in this client.
         *
         * @param options The socket options to apply to new connections.
         * @return {@literal this}
         */
        public HttpClientSpec<IN, OUT> options(ClientSocketOptions options) {
            this.options = options;
            return this;
        }

        /**
         * Set the options to use for configuring SSL. Setting this to {@code null} means don't use SSL at all (the
         * default).
         *
         * @param sslOptions The options to set when configuring SSL
         * @return {@literal this}
         */
        public HttpClientSpec<IN, OUT> ssl(@Nullable SslOptions sslOptions) {
            this.sslOptions = sslOptions;
            return this;
        }

        /**
         * The host and port to which this client should connect.
         *
         * @param host The host to connect to.
         * @param port The port to connect to.
         * @return {@literal this}
         */
        public HttpClientSpec<IN, OUT> connect(@Nonnull String host, int port) {
            return connect(new InetSocketAddress(host, port));
        }

        /**
         * The address to which this client should connect.
         *
         * @param connectAddress The address to connect to.
         * @return {@literal this}
         */
        public HttpClientSpec<IN, OUT> connect(@Nonnull InetSocketAddress connectAddress) {
            Assert.isNull(this.connectAddress, "Connect address is already set.");
            this.connectAddress = connectAddress;
            return this;
        }

        /**
         * The channel interceptor, e.g. to use to encode and decode data with
         * {@link reactor.io.net.preprocessor.CodecPreprocessor}
         *
         * @param httpPreprocessor The codec to use.
         * @return {@literal this}
         */
        @SuppressWarnings("unchecked")
        public HttpClientSpec<IN, OUT> httpProcessor(
            HttpProcessor<ByteBuf, ByteBuf, HttpChannel<ByteBuf, ByteBuf>, IN, OUT, HttpChannel<IN, OUT>> httpPreprocessor) {
            Assert.notNull(httpPreprocessor, "Preprocessor cannot be null.");
            this.httpPreprocessor = httpPreprocessor;
            return this;
        }

        /**
         * Set the default {@link reactor.core.timer.Timer} for timed operations.
         *
         * @param timer The timer to assign by default
         * @return {@literal this}
         */
        public HttpClientSpec<IN, OUT> timer(Timer timer) {
            this.timer = timer;
            return this;
        }

        @Override
        @SuppressWarnings("unchecked")
        public HttpClient<IN, OUT> get() {
            try {
                HttpClient<ByteBuf, ByteBuf> client = clientImplConstructor.newInstance(
                    timer,
                    connectAddress,
                    options,
                    sslOptions
                );

                if (httpPreprocessor != null && (options == null || !options.isRaw())) {
                    return client.httpProcessor(httpPreprocessor);
                } else {
                    return (HttpClient<IN, OUT>) client;
                }
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }

    }

    /**
     * A helper class for configure a new {@code Reconnect}.
     */
    public class IncrementalBackoffReconnect implements Supplier<Reconnect> {

        public static final long DEFAULT_INTERVAL = 5000;

        public static final long DEFAULT_MULTIPLIER   = 1;
        public static final long DEFAULT_MAX_ATTEMPTS = -1;
        private final List<InetSocketAddress> addresses;
        private       long                    interval;

        private long multiplier;
        private long maxInterval;
        private long maxAttempts;

        /**
         *
         */
        IncrementalBackoffReconnect() {
            this.addresses = new LinkedList<InetSocketAddress>();
            this.interval = DEFAULT_INTERVAL;
            this.multiplier = DEFAULT_MULTIPLIER;
            this.maxInterval = Long.MAX_VALUE;
            this.maxAttempts = DEFAULT_MAX_ATTEMPTS;
        }

        /**
         * Set the reconnection interval.
         *
         * @param interval the period reactor waits between attemps to reconnect disconnected peers
         * @return {@literal this}
         */
        public IncrementalBackoffReconnect interval(long interval) {
            this.interval = interval;
            return this;
        }

        /**
         * Set the maximum reconnection interval that will be applied if the multiplier
         * is set to a value greather than one.
         *
         * @param maxInterval
         * @return {@literal this}
         */
        public IncrementalBackoffReconnect maxInterval(long maxInterval) {
            this.maxInterval = maxInterval;
            return this;
        }

        /**
         * Set the backoff multiplier.
         *
         * @param multiplier
         * @return {@literal this}
         */
        public IncrementalBackoffReconnect multiplier(long multiplier) {
            this.multiplier = multiplier;
            return this;
        }

        /**
         * Sets the number of time that Reactor will attempt to connect or reconnect
         * before giving up.
         *
         * @param maxAttempts The max number of attempts made before failing.
         * @return {@literal this}
         */
        public IncrementalBackoffReconnect maxAttempts(long maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        /**
         * Add an address to the pool of addresses.
         *
         * @param address
         * @return {@literal this}
         */
        public IncrementalBackoffReconnect address(InetSocketAddress address) {
            this.addresses.add(address);
            return this;
        }

        /**
         * Add an address to the pool of addresses.
         *
         * @param host
         * @param port
         * @return {@literal this}
         */
        public IncrementalBackoffReconnect address(String host, int port) {
            this.addresses.add(new InetSocketAddress(host, port));
            return this;
        }

        @Override
        public Reconnect get() {
            final AtomicInteger count = new AtomicInteger();
            final int len = addresses.size();

            final Supplier<InetSocketAddress> endpoints = new Supplier<InetSocketAddress>() {
                @Override
                public InetSocketAddress get() {
                    return addresses.get(count.getAndIncrement() % len);
                }
            };

            return new Reconnect() {
                public Tuple2<InetSocketAddress, Long> reconnect(InetSocketAddress currentAddress, int attempt) {
                    Tuple2<InetSocketAddress, Long> rv = null;
                    synchronized (IncrementalBackoffReconnect.this) {
                        if (!addresses.isEmpty()) {
                            if (IncrementalBackoffReconnect.this.maxAttempts == -1 ||
                                IncrementalBackoffReconnect.this.maxAttempts > attempt) {
                                rv = Tuple.of(endpoints.get(), determineInterval(attempt));
                            }
                        } else {
                            rv = Tuple.of(currentAddress, determineInterval(attempt));
                        }
                    }

                    return rv;
                }
            };
        }

        /**
         * Determine the period in milliseconds between reconnection attempts.
         *
         * @param attempt the number of times a reconnection has been attempted
         * @return the reconnection period
         */
        public long determineInterval(int attempt) {
            return (multiplier > 1) ? Math.min(maxInterval, interval * attempt) : interval;
        }

    }
}
