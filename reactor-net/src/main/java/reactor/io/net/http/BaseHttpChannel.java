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

package reactor.io.net.http;

import java.util.Map;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.trait.Connectable;
import reactor.core.trait.Introspectable;
import reactor.core.trait.Publishable;
import reactor.core.trait.Subscribable;
import reactor.core.util.EmptySubscription;
import reactor.fn.Function;
import reactor.io.buffer.Buffer;
import reactor.io.net.http.model.Cookie;
import reactor.io.net.http.model.Method;
import reactor.io.net.http.model.Status;
import reactor.io.net.http.model.Transfer;

/**
 * An HTTP {@link HttpChannel} extension that provides Headers status check and optional
 *  params resolution
 *
 * @author Sebastien Deleuze
 * @author Stephane maldini
 */
public abstract class BaseHttpChannel<IN, OUT> extends Flux<IN> implements Introspectable, HttpChannel<IN,OUT> {

	private volatile int statusAndHeadersSent = 0;
	private Function<? super String, Map<String, Object>> paramsResolver;

	protected final static AtomicIntegerFieldUpdater<BaseHttpChannel> HEADERS_SENT =
			AtomicIntegerFieldUpdater.newUpdater(BaseHttpChannel.class, "statusAndHeadersSent");

	public BaseHttpChannel() {
	}

	// REQUEST contract

	/**
	 * Read all URI params
	 * @return a map of resolved parameters against their matching key name
	 */
	@Override
	public Map<String, Object> params() {
		return null != paramsResolver ? paramsResolver.apply(uri()) : null;
	}

	/**
	 * Read URI param from the given key
	 * @param key matching key
	 * @return the resolved parameter for the given key name
	 */
	@Override
	public Object param(String key) {
		Map<String, Object> params = null;
		if (paramsResolver != null) {
			params = this.paramsResolver.apply(uri());
		}
		return null != params ? params.get(key) : null;
	}

	/**
	 * Register an HTTP request header
	 * @param name Header name
	 * @param value Header content
	 * @return this
	 */
	@Override
	public HttpChannel<IN, OUT> header(String name, String value) {
		if (statusAndHeadersSent == 0) {
			doAddHeader(name, value);
		}
		else {
			throw new IllegalStateException("Status and headers already sent");
		}
		return this;
	}

	protected abstract void doHeader(String name, String value);

	/**
	 * Accumulate a Request Header using the given name and value, appending ";" for each
	 * new value
	 * @return this
	 */
	@Override
	public HttpChannel<IN, OUT> addHeader(String name, String value) {
		if (statusAndHeadersSent == 0) {
			doAddHeader(name, value);
		}
		else {
			throw new IllegalStateException("Status and headers already sent");
		}
		return this;
	}

	protected abstract void doAddHeader(String name, String value);

	/**
	 *
	 * @param headerResolver
	 */
	@Override
	public HttpChannel<IN, OUT> paramsResolver(
			Function<? super String, Map<String, Object>> headerResolver) {
		this.paramsResolver = headerResolver;
		return this;
	}

	@Override
	public HttpChannel<IN, OUT> addCookie(String name, Cookie cookie) {
		if (statusAndHeadersSent == 0) {
			doAddCookie(name, cookie);
		}
		else {
			throw new IllegalStateException("Status and headers already sent");
		}
		return this;
	}

	protected abstract void doAddCookie(String name, Cookie cookie);

	// RESPONSE contract

	@Override
	public HttpChannel<IN, OUT> addResponseCookie(String name, Cookie cookie) {
		if (statusAndHeadersSent == 0) {
			doAddResponseCookie(name, cookie);
		}
		else {
			throw new IllegalStateException("Status and headers already sent");
		}
		return this;
	}

	protected abstract void doAddResponseCookie(String name, Cookie cookie);

	/**
	 * Set the response status to an outgoing response
	 * @param status the status to define
	 * @return this
	 */
	@Override
	public HttpChannel<IN, OUT> responseStatus(Status status) {
		if (statusAndHeadersSent == 0) {
			doResponseStatus(status);
		}
		else {
			throw new IllegalStateException("Status and headers already sent");
		}
		return this;
	}

	protected abstract void doResponseStatus(Status status);

	/**
	 * Define the response HTTP header for the given key
	 * @param name the HTTP response header key to override
	 * @param value the HTTP response header content
	 * @return this
	 */
	@Override
	public HttpChannel<IN, OUT> responseHeader(String name, String value) {
		if (statusAndHeadersSent == 0) {
			doResponseHeader(name, value);
		}
		else {
			throw new IllegalStateException("Status and headers already sent");
		}
		return this;
	}

	protected abstract void doResponseHeader(String name, String value);

	/**
	 * Accumulate a response HTTP header for the given key name, appending ";" for each
	 * new value
	 * @param name the HTTP response header name
	 * @param value the HTTP response header value
	 * @return this
	 */
	@Override
	public HttpChannel<IN, OUT> addResponseHeader(String name, String value) {
		if (statusAndHeadersSent == 0) {
			doAddResponseHeader(name, value);
		}
		else {
			throw new IllegalStateException("Status and headers already sent");
		}
		return this;
	}

	/**
	 * Flush the headers if not sent. Might be useful for the case
	 * @return Stream to signal error or successful write to the client
	 */
	@Override
	public Mono<Void> writeHeaders() {
		if (statusAndHeadersSent == 0) {
			return new PostHeaderWritePublisher();
		}
		else {
			return Mono.empty();
		}
	}

	/**
	 * @return the Transfer setting SSE for this http connection (e.g. event-stream)
	 */
	@Override
	public HttpChannel<IN, OUT> sse() {
		return transfer(Transfer.EVENT_STREAM);
	}

	@Override
	public String getName() {
		if(isWebsocket()){
			return Method.WS.getName()+":"+uri();
		}

		return  method().getName()+":"+uri();
	}

	protected abstract void doAddResponseHeader(String name, String value);

	protected boolean markHeadersAsFlushed() {
		return HEADERS_SENT.compareAndSet(this, 0, 1);
	}

	protected abstract void doSubscribeHeaders(Subscriber<? super Void> s);

	protected abstract Publisher<Void> writeWithAfterHeaders(Publisher<? extends OUT> s);

	protected abstract Publisher<Void> writeWithBufferAfterHeaders(Publisher<? extends Buffer> s);

	@Override
	public Mono<Void> writeBufferWith(final Publisher<? extends Buffer> dataStream) {
		return new PostBufferWritePublisher(dataStream);
	}

	@Override
	public Mono<Void> writeWith(final Publisher<? extends OUT> source) {
		return new PostWritePublisher(source);
	}

	private class PostWritePublisher extends Mono<Void> implements Publishable, Connectable {

		private final Publisher<? extends OUT> source;

		public PostWritePublisher(Publisher<? extends OUT> source) {
			this.source = source;
		}

		@Override
		public void subscribe(final Subscriber<? super Void> s) {
			if(markHeadersAsFlushed()){
				headers().transferEncodingChunked();
				doSubscribeHeaders(new PostHeaderWriteSubscriber(s));
			}
			else{
				writeWithAfterHeaders(source).subscribe(s);
			}
		}

		@Override
		public Object upstream() {
			return source;
		}

		@Override
		public Object connectedInput() {
			return BaseHttpChannel.this;
		}

		@Override
		public Object connectedOutput() {
			return BaseHttpChannel.this;
		}

		private class PostHeaderWriteSubscriber implements Subscriber<Void>, Publishable, Subscribable {

			private final Subscriber<? super Void> s;
			private Subscription subscription;

			public PostHeaderWriteSubscriber(Subscriber<? super Void> s) {
				this.s = s;
			}

			@Override
			public void onSubscribe(Subscription sub) {
				this.subscription = sub;
				sub.request(Long.MAX_VALUE);
			}

			@Override
			public void onNext(Void aVoid) {
				//Ignore
			}

			@Override
			public void onError(Throwable t) {
				this.subscription = null;
				s.onError(t);
			}

			@Override
			public void onComplete() {
				this.subscription = null;
				writeWithAfterHeaders(source).subscribe(s);
			}

			@Override
			public Subscriber downstream() {
				return s;
			}

			@Override
			public Object upstream() {
				return subscription;
			}
		}
	}

	private class PostHeaderWritePublisher extends Mono<Void> implements Connectable{

		@Override
		public void subscribe(Subscriber<? super Void> s) {
			if (markHeadersAsFlushed()) {
				doSubscribeHeaders(s);
			}
			else {
				EmptySubscription.error(s, new IllegalStateException("Status and headers already sent"));
			}
		}

		@Override
		public Object connectedInput() {
			return BaseHttpChannel.this;
		}

		@Override
		public Object connectedOutput() {
			return BaseHttpChannel.this;
		}
	}

	private class PostBufferWritePublisher extends Mono<Void> implements Publishable, Connectable {

		private final Publisher<? extends Buffer> dataStream;

		public PostBufferWritePublisher(Publisher<? extends Buffer> dataStream) {
			this.dataStream = dataStream;
		}

		@Override
		public void subscribe(final Subscriber<? super Void> s) {
			if(markHeadersAsFlushed()){
				doSubscribeHeaders(new PostHeaderWriteBufferSubscriber(s));
			}
			else{
				writeWithBufferAfterHeaders(dataStream).subscribe(s);
			}
		}

		@Override
		public Object connectedInput() {
			return BaseHttpChannel.this;
		}

		@Override
		public Object connectedOutput() {
			return BaseHttpChannel.this;
		}

		@Override
		public Object upstream() {
			return dataStream;
		}

		private class PostHeaderWriteBufferSubscriber implements Subscriber<Void>, Subscribable, Publishable {

			private final Subscriber<? super Void> s;
			private Subscription subscription;

			public PostHeaderWriteBufferSubscriber(Subscriber<? super Void> s) {
				this.s = s;
			}

			@Override
			public void onSubscribe(Subscription sub) {
				this.subscription = sub;
				sub.request(Long.MAX_VALUE);
			}

			@Override
			public void onNext(Void aVoid) {
				//Ignore
			}

			@Override
			public Subscriber downstream() {
				return s;
			}

			@Override
			public Object upstream() {
				return subscription;
			}

			@Override
			public void onError(Throwable t) {
				this.subscription = null;
				s.onError(t);
			}

			@Override
			public void onComplete() {
				this.subscription = null;
				writeWithBufferAfterHeaders(dataStream).subscribe(s);
			}
		}
	}
}
