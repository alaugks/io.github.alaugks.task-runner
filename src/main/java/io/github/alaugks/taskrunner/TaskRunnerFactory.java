// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.taskrunner;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class TaskRunnerFactory {

	public static final int DEFAULT_POOL_SIZE = 10;

	private static final Logger logger = LoggerFactory.getLogger(TaskRunnerFactory.class);
	private final List<TaskRunnerInterface> strategies;
	private final ExecutorService executor;

	private TaskRunnerFactory(Builder builder) {
		this.strategies = builder.strategies;
		this.executor = Executors.newFixedThreadPool(builder.poolSize);
	}

	public static Builder builder(List<TaskRunnerInterface> strategies) {
		return new Builder(strategies);
	}

	public <T extends TaskRunnerInterface & Runnable> void runAll(Class<T> strategyClass) throws TaskRunnerRuntimeException {
		List<? extends Future<?>> futures = this.strategies.stream()
			.filter(strategyClass::isInstance)
			.map(strategyClass::cast)
			.map(strategy -> executor.submit(() -> {
				String threadName = "strategy-" + strategy.getClass().getSimpleName() + "-" + Thread.currentThread().getId();
				Thread.currentThread().setName(threadName);

				logger.debug(
					"Running in thread: class_name=[\"{}\"], strategy_class_name=[\"{}\"], thread_name=[\"{}\"]",
					this.getClass().getSimpleName(),
					strategy.getClass().getSimpleName(),
					threadName
				);

				strategy.run();
			}))
			.toList();

		for (Future<?> future : futures) {
			try {
				future.get();
			} catch (ExecutionException e) {
				throw new TaskRunnerRuntimeException(e.getCause());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new TaskRunnerRuntimeException(e);
			}
		}
	}

	public static final class Builder {

		private final List<TaskRunnerInterface> strategies;
		private int poolSize = DEFAULT_POOL_SIZE;

		private Builder(List<TaskRunnerInterface> strategies) {
			this.strategies = Objects.requireNonNull(strategies, "strategies must not be null");
		}

		public Builder poolSize(int poolSize) {
			if (poolSize < 1) {
				throw new IllegalArgumentException("poolSize must be at least 1 but was " + poolSize);
			}
			this.poolSize = poolSize;
			return this;
		}

		public TaskRunnerFactory build() {
			return new TaskRunnerFactory(this);
		}
	}
}
