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

package reactor.io.netty.tcp;

import java.net.InetSocketAddress;
import java.util.function.Function;

import reactor.core.publisher.Mono;
import reactor.core.publisher.SchedulerGroup;
import reactor.core.state.Introspectable;
import reactor.core.scheduler.Timer;
import reactor.io.buffer.Buffer;
import reactor.io.ipc.ChannelFlux;
import reactor.io.netty.Preprocessor;
import reactor.io.ipc.ChannelFluxHandler;
import reactor.io.netty.ReactiveNet;
import reactor.io.netty.ReactivePeer;
import reactor.io.netty.Spec;
import reactor.io.netty.config.ServerOptions;
import reactor.io.netty.config.SslOptions;

/**
 * Base functionality needed by all servers that communicate with clients over TCP.
 * @param <IN> The type that will be received by this server
 * @param <OUT> The type that will be sent by this server
 * @author Jon Brisbin
 * @author Stephane Maldini
 */
public abstract class TcpServer<IN, OUT> extends ReactivePeer<IN, OUT, ChannelFlux<IN, OUT>>
		implements Introspectable {

	public static final int DEFAULT_TCP_THREAD_COUNT = Integer.parseInt(System.getProperty(
			"reactor.tcp.selectThreadCount",
			"" + SchedulerGroup.DEFAULT_POOL_SIZE / 2));

	public static final int DEFAULT_TCP_SELECT_COUNT =
			Integer.parseInt(System.getProperty("reactor.tcp.selectThreadCount", "" + DEFAULT_TCP_THREAD_COUNT));

	/**
	 * Bind a new TCP server to "loopback" on port {@literal 12012}. By default the default server implementation is
	 * scanned from the classpath on Class init. Support for Netty is provided as long as the
	 * relevant library dependencies are on the classpath. <p> To reply data on the active connection, {@link
	 * ChannelFlux#writeWith} can subscribe to any passed {@link org.reactivestreams.Publisher}. <p> Note that
	 * {@link reactor.core.state.Backpressurable#getCapacity} will be used to switch on/off a channel in auto-read / flush on
	 * write mode. If the capacity is Long.MAX_Value, write on flush and auto read will apply. Otherwise, data will be
	 * flushed every capacity batch size and read will pause when capacity number of elements have been dispatched. <p>
	 * Emitted channels will run on the same thread they have beem receiving IO events.
	 *
	 * <p> By default the type of emitted data or received data is {@link Buffer}
	 * @return a new Stream of ChannelFlux, typically a peer of connections.
	 */
	public static TcpServer<Buffer, Buffer> create() {
		return create(ReactiveNet.DEFAULT_BIND_ADDRESS);
	}

	/**
	 * Bind a new TCP server to "loopback" on the given port. By default the default server implementation is scanned
	 * from the classpath on Class init. Support for Netty first is provided as long as the relevant
	 * library dependencies are on the classpath. <p> A {@link TcpServer} is a specific kind of {@link
	 * org.reactivestreams.Publisher} that will emit: - onNext {@link ChannelFlux} to consume data from - onComplete
	 * when server is shutdown - onError when any error (more specifically IO error) occurs From the emitted {@link
	 * ChannelFlux}, one can decide to add in-channel consumers to read any incoming data. <p> To reply data on the
	 * active connection, {@link ChannelFlux#writeWith} can subscribe to any passed {@link
	 * org.reactivestreams.Publisher}. <p> Note that {@link reactor.core.state.Backpressurable#getCapacity} will be used to
	 * switch on/off a channel in auto-read / flush on write mode. If the capacity is Long.MAX_Value, write on flush and
	 * auto read will apply. Otherwise, data will be flushed every capacity batch size and read will pause when capacity
	 * number of elements have been dispatched. <p> Emitted channels will run on the same thread they have beem
	 * receiving IO events.
	 *
	 * <p> By default the type of emitted data or received data is {@link Buffer}
	 * @param port the port to listen on loopback
	 * @return a new Stream of ChannelFlux, typically a peer of connections.
	 */
	public static TcpServer<Buffer, Buffer> create(int port) {
		return create(ReactiveNet.DEFAULT_BIND_ADDRESS, port);
	}

	/**
	 * Bind a new TCP server to the given bind address on port {@literal 12012}. By default the default server
	 * implementation is scanned from the classpath on Class init. Support for Netty first is provided
	 * as long as the relevant library dependencies are on the classpath. <p> A {@link TcpServer} is a specific kind of
	 * {@link org.reactivestreams.Publisher} that will emit: - onNext {@link ChannelFlux} to consume data from -
	 * onComplete when server is shutdown - onError when any error (more specifically IO error) occurs From the emitted
	 * {@link ChannelFlux}, one can decide to add in-channel consumers to read any incoming data. <p> To reply data
	 * on the active connection, {@link ChannelFlux#writeWith} can subscribe to any passed {@link
	 * org.reactivestreams.Publisher}. <p> Note that {@link reactor.core.state.Backpressurable#getCapacity} will be used to
	 * switch on/off a channel in auto-read / flush on write mode. If the capacity is Long.MAX_Value, write on flush and
	 * auto read will apply. Otherwise, data will be flushed every capacity batch size and read will pause when capacity
	 * number of elements have been dispatched. <p> Emitted channels will run on the same thread they have beem
	 * receiving IO events.
	 *
	 * <p> By default the type of emitted data or received data is {@link Buffer}
	 * @param bindAddress bind address (e.g. "127.0.0.1") to create the server on the default port 12012
	 * @return a new Stream of ChannelFlux, typically a peer of connections.
	 */
	public static TcpServer<Buffer, Buffer> create(String bindAddress) {
		return create(bindAddress, ReactiveNet.DEFAULT_PORT);
	}

	/**
	 * Bind a new TCP server to the given bind address and port. By default the default server implementation is scanned
	 * from the classpath on Class init. Support for Netty is provided as long as the relevant
	 * library dependencies are on the classpath. <p> A {@link TcpServer} is a specific kind of {@link
	 * org.reactivestreams.Publisher} that will emit: - onNext {@link ChannelFlux} to consume data from - onComplete
	 * when server is shutdown - onError when any error (more specifically IO error) occurs From the emitted {@link
	 * ChannelFlux}, one can decide to add in-channel consumers to read any incoming data. <p> To reply data on the
	 * active connection, {@link ChannelFlux#writeWith} can subscribe to any passed {@link
	 * org.reactivestreams.Publisher}. <p> Note that {@link reactor.core.state.Backpressurable#getCapacity} will be used to
	 * switch on/off a channel in auto-read / flush on write mode. If the capacity is Long.MAX_Value, write on flush and
	 * auto read will apply. Otherwise, data will be flushed every capacity batch size and read will pause when capacity
	 * number of elements have been dispatched. <p> Emitted channels will run on the same thread they have beem
	 * receiving IO events.
	 *
	 * <p> By default the type of emitted data or received data is {@link Buffer}
	 * @param port the port to listen on the passed bind address
	 * @param bindAddress bind address (e.g. "127.0.0.1") to create the server on the passed port
	 * @return a new Stream of ChannelFlux, typically a peer of connections.
	 */
	public static TcpServer<Buffer, Buffer> create(final String bindAddress, final int port) {
		return ReactiveNet.tcpServer(new Function<Spec.TcpServerSpec<Buffer, Buffer>, Spec.TcpServerSpec<Buffer, Buffer>>() {
			@Override
			public Spec.TcpServerSpec<Buffer, Buffer> apply(Spec.TcpServerSpec<Buffer, Buffer> serverSpec) {
				serverSpec.timer(Timer.globalOrNull());
				return serverSpec.listen(bindAddress, port);
			}
		});
	}

	private final ServerOptions options;
	private final SslOptions    sslOptions;

	//Carefully reset
	protected InetSocketAddress listenAddress;

	protected TcpServer(Timer timer,
			InetSocketAddress listenAddress,
			ServerOptions options,
			SslOptions sslOptions) {
		super(timer, options != null ? options.prefetch() : Long.MAX_VALUE);
		this.listenAddress = listenAddress;
		this.options = options;
		this.sslOptions = sslOptions;
	}

	/**
	 * Get the address to which this server is bound. If port 0 was used, returns the resolved port if possible
	 * @return the address bound
	 */
	public InetSocketAddress getListenAddress() {
		return listenAddress;
	}

	/**
	 * Get the {@link ServerOptions} currently in effect.
	 * @return the current server options
	 */
	protected ServerOptions getOptions() {
		return options;
	}

	/**
	 * Get the {@link SslOptions} current in effect.
	 * @return the SSL options
	 */
	protected SslOptions getSslOptions() {
		return sslOptions;
	}

	@Override
	protected <NEWIN, NEWOUT> ReactivePeer<NEWIN, NEWOUT, ChannelFlux<NEWIN, NEWOUT>> doPreprocessor(Function<ChannelFlux<IN, OUT>, ? extends ChannelFlux<NEWIN, NEWOUT>> preprocessor) {
		return new PreprocessedTcpServer<>(preprocessor);
	}

	@Override
	public String getName() {
		return "TcpServer:" + getListenAddress().toString();
	}

	@Override
	public int getMode() {
		return 0;
	}

	private final class PreprocessedTcpServer<NEWIN, NEWOUT, NEWCONN extends ChannelFlux<NEWIN, NEWOUT>>
			extends TcpServer<NEWIN, NEWOUT> {

		private final Function<ChannelFlux<IN, OUT>, ? extends NEWCONN> preprocessor;

		public PreprocessedTcpServer(Function<ChannelFlux<IN, OUT>, ? extends NEWCONN> preprocessor) {
			super(TcpServer.this.getDefaultTimer(),
					TcpServer.this.getListenAddress(),
					TcpServer.this.getOptions(),
					TcpServer.this.getSslOptions());
			this.preprocessor = preprocessor;
		}

		@Override
		protected Mono<Void> doStart(ChannelFluxHandler<NEWIN, NEWOUT, ChannelFlux<NEWIN, NEWOUT>> handler) {
			ChannelFluxHandler<IN, OUT, ChannelFlux<IN, OUT>> p =
					Preprocessor.PreprocessedHandler.create(handler, preprocessor);
			return TcpServer.this.start(p);
		}

		@Override
		protected Mono<Void> doShutdown() {
			return TcpServer.this.shutdown();
		}

		@Override
		public InetSocketAddress getListenAddress() {
			return TcpServer.this.getListenAddress();
		}

		@Override
		protected boolean shouldFailOnStarted() {
			return false;
		}
	}
}
