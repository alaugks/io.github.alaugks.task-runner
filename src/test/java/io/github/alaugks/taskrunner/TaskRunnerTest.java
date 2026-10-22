// SPDX-License-Identifier: Apache-2.0

package io.github.alaugks.taskrunner;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TaskRunnerTest {

	interface TestStrategy extends TaskRunnerInterface, Runnable {}

	@Test
	void testRunAll10Minutes() throws Exception {

		TaskRunnerInterface mockMinutesStrategy = mock(TaskRunnerInterface.class);

		List<TaskRunnerInterface> strategyMap = new ArrayList<>();
		strategyMap.add(mockMinutesStrategy);
		strategyMap.add(mockMinutesStrategy);

		TaskRunner runner = TaskRunner.builder(strategyMap).build();

		runner.runAll(TaskRunnerInterface.class);

		verify(mockMinutesStrategy, times(2)).run();
	}

	@Test
	void test_runAll_setsCustomThreadName() throws Exception {
		Set<String> threadNames = ConcurrentHashMap.newKeySet();

		TestStrategy strategy = () -> threadNames.add(Thread.currentThread().getName());

		TaskRunner runner = TaskRunner.builder(List.of(strategy)).build();
		runner.runAll(TestStrategy.class);

		assertEquals(1, threadNames.size(), "Exactly one thread name should be captured");
		String threadName = threadNames.iterator().next();
		assertTrue(threadName.startsWith("strategy-"), "Thread name should start with 'strategy-' but was: " + threadName);
	}

	@Test
	void test_runAll_wrapsExceptionOfStrategy() {
		TestStrategy failingStrategy = () -> { throw new IllegalStateException("Boom"); };

		TaskRunner runner = TaskRunner.builder(List.of(failingStrategy)).build();

		TaskRunnerRuntimeException ex = assertThrows(TaskRunnerRuntimeException.class, () -> {
			runner.runAll(TestStrategy.class);
		});

		assertInstanceOf(IllegalStateException.class, ex.getCause());
		assertEquals("Boom", ex.getCause().getMessage());
	}

	@Test
	void test_runAll_restoresInterruptFlagWhenInterrupted() {
		CountDownLatch release = new CountDownLatch(1);
		TestStrategy blockingStrategy = () -> {
			try {
				release.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
		};

		TaskRunner runner = TaskRunner.builder(List.of(blockingStrategy)).build();

		Thread.currentThread().interrupt();
		try {
			TaskRunnerRuntimeException ex = assertThrows(TaskRunnerRuntimeException.class, () -> {
				runner.runAll(TestStrategy.class);
			});

			assertInstanceOf(InterruptedException.class, ex.getCause());
			assertTrue(Thread.currentThread().isInterrupted(), "Interrupt flag should be restored");
		} finally {
			Thread.interrupted();
			release.countDown();
		}
	}

	@Test
	void test_builder_rejectsInvalidPoolSize() {
		TaskRunner.Builder builder = TaskRunner.builder(List.of());

		assertThrows(IllegalArgumentException.class, () -> builder.poolSize(0));
	}

	@Test
	void test_builder_rejectsNullStrategies() {
		assertThrows(NullPointerException.class, () -> TaskRunner.builder(null));
	}

	@Test
	void test_runAll_respectsPoolSize() {
		AtomicInteger running = new AtomicInteger();
		AtomicInteger maxRunning = new AtomicInteger();

		TestStrategy strategy = () -> {
			maxRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
			try {
				Thread.sleep(50);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			running.decrementAndGet();
		};

		TaskRunner runner = TaskRunner.builder(List.of(strategy, strategy, strategy))
			.poolSize(1)
			.build();
		runner.runAll(TestStrategy.class);

		assertEquals(1, maxRunning.get());
	}
}
