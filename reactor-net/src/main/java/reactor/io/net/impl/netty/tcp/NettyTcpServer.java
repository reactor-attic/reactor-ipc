/*
 * Copyright (c) 2011-2014 Pivotal Software, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package reactor.io.net.impl.netty.tcp;

import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.Iterator;
import javax.net.ssl.SSLEngine;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.SocketChannelConfig;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.concurrent.Future;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.support.Logger;
import reactor.Publishers;
import reactor.core.support.NamedDaemonThreadFactory;
import reactor.core.support.ReactiveState;
import reactor.core.timer.Timer;
import reactor.io.net.ReactiveChannel;
import reactor.io.net.ReactiveChannelHandler;
import reactor.io.net.config.CommonSocketOptions;
import reactor.io.net.config.ServerSocketOptions;
import reactor.io.net.config.SslOptions;
import reactor.io.net.impl.netty.NettyChannel;
import reactor.io.net.impl.netty.internal.NettyNativeDetector;
import reactor.io.net.impl.netty.NettyServerSocketOptions;
import reactor.io.net.tcp.TcpServer;
import reactor.io.net.tcp.ssl.SSLEngineSupplier;

/**
 * A Netty-based {@code TcpServer} implementation
 *
 * @author Stephane Maldini
 * @since 2.5
 */
public class NettyTcpServer extends TcpServer<ByteBuf, ByteBuf> implements ReactiveState.LinkedDownstreams {

	private final static Logger log = Logger.getLogger(NettyTcpServer.class);

	private final NettyServerSocketOptions nettyOptions;
	private final ServerBootstrap          bootstrap;
	private final EventLoopGroup           selectorGroup;
	private final EventLoopGroup           ioGroup;

	private final ChannelGroup channelGroup;

	private ChannelFuture bindFuture;

	protected NettyTcpServer(Timer timer,
	                         InetSocketAddress listenAddress,
	                         final ServerSocketOptions options,
	                         final SslOptions sslOptions) {
		super(timer, listenAddress, options, sslOptions);

		if (options instanceof NettyServerSocketOptions) {
			this.nettyOptions = (NettyServerSocketOptions) options;
		} else {
			this.nettyOptions = null;
		}

		int selectThreadCount = DEFAULT_TCP_SELECT_COUNT;
		int ioThreadCount = DEFAULT_TCP_THREAD_COUNT;

		this.selectorGroup = NettyNativeDetector.newEventLoopGroup(selectThreadCount, new NamedDaemonThreadFactory
		  ("reactor-tcp-select"));

		if (null != nettyOptions && null != nettyOptions.eventLoopGroup()) {
			this.ioGroup = nettyOptions.eventLoopGroup();
		} else {
			this.ioGroup = NettyNativeDetector.newEventLoopGroup(ioThreadCount, new NamedDaemonThreadFactory("reactor-tcp-io"));
		}

		ServerBootstrap _serverBootstrap = new ServerBootstrap()
		  .group(selectorGroup, ioGroup)
		  .channel(NettyNativeDetector.getServerChannel(ioGroup))
		  .localAddress(
				  (null == listenAddress ? new InetSocketAddress(0) : listenAddress))
		  .childOption(ChannelOption.ALLOCATOR, PooledByteBufAllocator.DEFAULT)
		  .childOption(ChannelOption.AUTO_READ, sslOptions != null);

		if (options != null) {
			_serverBootstrap = _serverBootstrap
			  .option(ChannelOption.SO_BACKLOG, options.backlog())
			  .option(ChannelOption.SO_RCVBUF, options.rcvbuf())
			  .option(ChannelOption.SO_SNDBUF, options.sndbuf())
			  .option(ChannelOption.SO_REUSEADDR, options.reuseAddr());
		}

		if(options != null && options.isManaged() || CommonSocketOptions.DEFAULT_MANAGED_PEER){
			log.debug("Server is managed.");
			this.channelGroup = new DefaultChannelGroup(null);
		}
		else{
			log.debug("Server is not managed (Not directly introspectable)");
			this.channelGroup = null;
		}

		this.bootstrap = _serverBootstrap;

	}

	@Override
	public Iterator<?> downstreams() {
		if(channelGroup == null){
			return null;
		}
		return new Iterator<Object>() {
			final Iterator<Channel> channelIterator = channelGroup.iterator();

			@Override
			public boolean hasNext() {
				return channelIterator.hasNext();
			}

			@Override
			public Object next() {
				return channelIterator.next().pipeline().get(NettyChannelHandlerBridge.class);
			}
		};
	}

	@Override
	public long downstreamsCount() {
		return channelGroup == null ? -1 : channelGroup.size();
	}

	@Override
	protected Publisher<Void> doStart(final ReactiveChannelHandler<ByteBuf, ByteBuf, ReactiveChannel<ByteBuf, ByteBuf>> handler) {

		bootstrap.childHandler(new ChannelInitializer<SocketChannel>() {
			@Override
			public void initChannel(final SocketChannel ch) throws Exception {
				if (nettyOptions != null) {
					SocketChannelConfig config = ch.config();
					config.setReceiveBufferSize(nettyOptions.rcvbuf());
					config.setSendBufferSize(nettyOptions.sndbuf());
					config.setKeepAlive(nettyOptions.keepAlive());
					config.setReuseAddress(nettyOptions.reuseAddr());
					config.setSoLinger(nettyOptions.linger());
					config.setTcpNoDelay(nettyOptions.tcpNoDelay());
				}

				if (log.isDebugEnabled()) {
					log.debug("CONNECT {}", ch);
				}

				if(channelGroup != null){
					channelGroup.add(ch);
				}

				if (null != getSslOptions()) {
					SSLEngine ssl = new SSLEngineSupplier(getSslOptions(), false).get();
					if (log.isDebugEnabled()) {
						log.debug("SSL enabled using keystore {}",
						  (null != getSslOptions().keystoreFile() ? getSslOptions().keystoreFile() : "<DEFAULT>"));
					}
					ch.pipeline().addLast(new SslHandler(ssl));
				}

				if (null != nettyOptions && null != nettyOptions.pipelineConfigurer()) {
					nettyOptions.pipelineConfigurer().accept(ch.pipeline());
				}

				bindChannel(handler, ch);
			}
		});

		bindFuture = bootstrap.bind();

		return new NettyChannel.FuturePublisher<ChannelFuture>(bindFuture){
			@Override
			protected void doComplete(ChannelFuture future, Subscriber<? super Void> s) {
				log.info("BIND {}", future.channel().localAddress());
				if (listenAddress.getPort() == 0) {
					listenAddress =
							(InetSocketAddress) future.channel().localAddress();
				}
				super.doComplete(future, s);
			}
		};
	}

	@Override
	@SuppressWarnings("unchecked")
	public Publisher<Void> doShutdown() {
		try {
			bindFuture.channel().close().sync();
		} catch (InterruptedException ie){
			return Publishers.error(ie);
		}

		final Publisher<Void> shutdown = new NettyChannel.FuturePublisher<Future<?>>(selectorGroup.shutdownGracefully());

		if (null == nettyOptions || null == nettyOptions.eventLoopGroup()) {
			return Publishers.concat(
					Publishers.from(Arrays.asList(
						 shutdown,
						 new NettyChannel.FuturePublisher<Future<?>>(ioGroup.shutdownGracefully())
					))
			);
		}

		return shutdown;
	}

	protected void bindChannel(ReactiveChannelHandler<ByteBuf, ByteBuf, ReactiveChannel<ByteBuf, ByteBuf>> handler, SocketChannel
	  nativeChannel) {

		NettyChannel netChannel = new NettyChannel(
		  getDefaultPrefetchSize(),
		  nativeChannel
		);

		ChannelPipeline pipeline = nativeChannel.pipeline();

		if (log.isDebugEnabled()) {
			pipeline.addLast(new LoggingHandler(NettyTcpServer.class));
		}
		pipeline.addLast(
		  new NettyChannelHandlerBridge(handler, netChannel)
		);
	}

}
