// SPDX-License-Identifier: Apache-2.0

package dev.alaugks.pkg.runner;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;


@Component
public class TaskRunnerFactory {

	private static final Logger logger = LoggerFactory.getLogger(TaskRunnerFactory.class);
	private final List<TaskRunnerInterface> strategies;
	private final ExecutorService executor = Executors.newFixedThreadPool(10);

	public TaskRunnerFactory(List<TaskRunnerInterface> strategies) {
		this.strategies = strategies;
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
}
