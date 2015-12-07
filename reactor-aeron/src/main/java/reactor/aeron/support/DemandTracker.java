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
package reactor.aeron.support;

import reactor.core.support.BackpressureUtils;

import java.util.concurrent.atomic.AtomicLongFieldUpdater;

/**
 * Tracks the number of requested items.
 *
 * @author Anatoly Kadyshev
 */
public class DemandTracker {

	private volatile long requested;

	public static AtomicLongFieldUpdater<DemandTracker> requestedUpdater =
			AtomicLongFieldUpdater.newUpdater(DemandTracker.class, "requested");

	/**
	 * Increases the counter of requested items by <code>n</code>
	 *
	 * @param n number of items to request
	 * @return the previous demand
	 */
	public long request(long n) {
		return BackpressureUtils.getAndAdd(requestedUpdater, this, n);
	}

	public long getAndReset() {
		return requestedUpdater.getAndSet(this, 0);
	}

}