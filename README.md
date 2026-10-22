# Parallel Task Strategies for Spring

[![Quality gate status](https://sonarcloud.io/api/project_badges/measure?project=alaugks_pkg-taskrunner&metric=alert_status)](https://sonarcloud.io/summary/overall?id=alaugks_pkg-taskrunner)

A small Spring Boot library that runs multiple task strategies in parallel.

`TaskRunner` takes a list of `TaskRunnerInterface` strategies (typically
all Spring-managed beans), picks those implementing a given subtype and
executes each one concurrently on a fixed thread pool. Exceptions thrown inside a task are propagated back to the caller,
and each worker thread is renamed to `strategy-<ClassName>-<ThreadId>` to make
logs easier to read.

## Requirements

- Java 17+
- `slf4j-api` (the only dependency)
- Spring Framework 7 / Spring Boot 4.x for the examples below (the library
  itself has no Spring dependency)

## Getting the code

> [!NOTE] 
> **For testing only.** For the time being, the library is published as a
> `SNAPSHOT` to the Maven Central snapshot repository. Use it to try the
> library out. For production, copy the source code into your own project (see
> below).

### Try it via Maven Central snapshots

Snapshots are not resolved from Maven Central itself. Add the snapshot
repository and the dependency to your `pom.xml`. No authentication is required.

```xml
<repositories>
    <repository>
        <id>central-snapshots</id>
        <url>https://central.sonatype.com/repository/maven-snapshots/</url>
        <releases>
            <enabled>false</enabled>
        </releases>
        <snapshots>
            <enabled>true</enabled>
        </snapshots>
    </repository>
</repositories>

<dependency>
    <groupId>io.github.alaugks</groupId>
    <artifactId>task-runner</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

### Production: copy the source

The library consists of only three classes, `TaskRunner`,
`TaskRunnerInterface` and `TaskRunnerRuntimeException`. Copy them into your own project. Keep the license and the
`SPDX-License-Identifier` header (see [License](#license)).

## Usage

### 1. Define a marker interface for a group of strategies

Group related tasks behind a shared interface that extends
`TaskRunnerInterface`. The runner uses this interface to pick which beans to
run together.

```java
package com.example.jobs;

import io.github.alaugks.taskrunner.TaskRunnerInterface;

public interface NightlyJob extends TaskRunnerInterface {
}
```

### 2. Implement strategies as Spring beans

Each strategy is a regular Spring component implementing your marker
interface. The `run()` method contains the task logic.

```java
package com.example.jobs;

import org.springframework.stereotype.Component;

@Component
public class CleanupTempFilesJob implements NightlyJob {
    @Override
    public void run() {
        // ... do work
    }
}

@Component
public class RefreshCacheJob implements NightlyJob {
    @Override
    public void run() {
        // ... do work
    }
}
```

### 3. Create the runner

Create the runner with its builder and expose it as a bean. The builder takes
all strategies; Spring injects every bean implementing
`TaskRunnerInterface`. The pool size is optional and defaults to 10.

```java
package com.example.jobs;

import io.github.alaugks.taskrunner.TaskRunner;
import io.github.alaugks.taskrunner.TaskRunnerInterface;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class TaskRunnerConfig {

    @Bean
    public TaskRunner taskRunner(List<TaskRunnerInterface> strategies) {
        return TaskRunner.builder(strategies)
            .poolSize(4) // optional, default: 10
            .build();
    }
}
```

### 4. Run all strategies in parallel

Inject `TaskRunner` and call `runAll(Class<T> strategyClass)` with the marker interface.
Every matching bean runs concurrently; the call returns once all of them
finish.

```java
package com.example.jobs;

import io.github.alaugks.taskrunner.TaskRunner;
import org.springframework.stereotype.Service;

@Service
public class NightlyScheduler {

    private final TaskRunner taskRunner;

    public NightlyScheduler(TaskRunner taskRunner) {
        this.taskRunner = taskRunner;
    }

    public void runNightly() {
        this.taskRunner.runAll(NightlyJob.class);
    }
}
```

You can define as many marker interfaces as you need (for example
`NightlyJob`, `HourlyJob`, `OnStartupJob`) and call `runAll(Class<T> strategyClass)` separately
for each one.

### 5. Trigger strategies from a scheduled job

To run a strategy group on a schedule, enable Spring scheduling and annotate a
method with `@Scheduled`. The example below runs every night at 02:00.

Enable scheduling once in your application (for example on the main class):

```java
package com.example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class Application {
    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
```

Then add a scheduled trigger that delegates to `TaskRunner`:

```java
package com.example.jobs;

import io.github.alaugks.taskrunner.TaskRunner;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Tasks {

    private final TaskRunner taskRunner;

    public Tasks(TaskRunner taskRunner) {
        this.taskRunner = taskRunner;
    }

    @Scheduled(cron = "0 0 2 * * *", zone = "Europe/Berlin")
    public void runNightly() {
        this.taskRunner.runAll(NightlyJob.class);
    }
}
```

Make the cron expression configurable via `application.yml` if you want to
adjust it per environment:

```yaml
jobs:
  nightly:
    cron: "0 0 2 * * *"
```

```java
@Scheduled(cron = "${jobs.nightly.cron}", zone = "Europe/Berlin")
public void runNightly() {
    this.taskRunner.runAll(NightlyJob.class);
}
```

## Behavior

- **Concurrency:** strategies run on a fixed thread pool. The size is set with
  `poolSize(int poolSize)` on the builder and defaults to 10.
- **Thread names:** each worker thread is named
  `strategy-<StrategyClassName>-<ThreadId>` for the duration of the task.
- **Error handling:** `runAll` waits for the tasks in order. If a strategy
  throws, the exception is wrapped in a `TaskRunnerRuntimeException` (the
  original exception is its cause). If the calling thread is interrupted, the
  interrupt flag is restored and a `TaskRunnerRuntimeException` is thrown.
  Tasks that are already running are not cancelled.
- **Logging:** task start is logged at `DEBUG` via SLF4J under the
  `io.github.alaugks.taskrunner.TaskRunner` logger.

## Build

```bash
./mvnw clean install
```

## License

Licensed under the [Apache License, Version 2.0](LICENSE).
