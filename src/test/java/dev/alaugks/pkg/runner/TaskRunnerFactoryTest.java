// SPDX-License-Identifier: Apache-2.0

package dev.alaugks.pkg.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class TaskRunnerFactoryTest {

	interface TestStrategy extends TaskRunnerInterface, Runnable {}

	@Test
	void testRunAll10Minutes() throws Exception {

		TaskRunnerInterface mockMinutesStrategy = mock(TaskRunnerInterface.class);

		List<TaskRunnerInterface> strategyMap = new ArrayList<>();
		strategyMap.add(mockMinutesStrategy);
		strategyMap.add(mockMinutesStrategy);

		TaskRunnerFactory factory = new TaskRunnerFactory(strategyMap);

		factory.runAll(TaskRunnerInterface.class);

		verify(mockMinutesStrategy, times(2)).run();
	}

	@Test
	void test_runAll_setsCustomThreadName() throws Exception {
		Set<String> threadNames = ConcurrentHashMap.newKeySet();

		TestStrategy strategy = () -> threadNames.add(Thread.currentThread().getName());

		TaskRunnerFactory factory = new TaskRunnerFactory(List.of(strategy));
		factory.runAll(TestStrategy.class);

		assertEquals(1, threadNames.size(), "Exactly one thread name should be captured");
		String threadName = threadNames.iterator().next();
		assertTrue(threadName.startsWith("strategy-"), "Thread name should start with 'strategy-' but was: " + threadName);
	}

	@Test
	void test_runAll_propagatesException() {
		TestStrategy failingStrategy = () -> { throw new IllegalStateException("Boom"); };

		TaskRunnerFactory factory = new TaskRunnerFactory(List.of(failingStrategy));

		IllegalStateException ex = assertThrows(IllegalStateException.class, () -> {
			factory.runAll(TestStrategy.class);
		});

		assertEquals("Boom", ex.getMessage());
	}
}
