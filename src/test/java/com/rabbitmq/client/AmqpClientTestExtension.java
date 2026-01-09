// Copyright (c) 2023-2026 Broadcom. All Rights Reserved.
// The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 2.0 ("MPL"), the GNU General Public License version 2
// ("GPL") and the Apache License version 2 ("ASL"). For the MPL, please see
// LICENSE-MPL-RabbitMQ. For the GPL, please see LICENSE-GPL2.  For the ASL,
// please see LICENSE-APACHE2.
//
// This software is distributed on an "AS IS" basis, WITHOUT WARRANTY OF ANY KIND,
// either express or implied. See the LICENSE file for specific language governing
// rights and limitations of this software.
//
// If you have any questions regarding licensing, please contact us at
// info@rabbitmq.com.
package com.rabbitmq.client;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.extension.ConditionEvaluationResult.disabled;
import static org.junit.jupiter.api.extension.ConditionEvaluationResult.enabled;

import com.rabbitmq.client.test.TestUtils;
import com.rabbitmq.client.test.ssl.SslTestSuite;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.management.LockInfo;
import java.lang.management.ManagementFactory;
import java.lang.management.MonitorInfo;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadInfo;
import java.net.Socket;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmqpClientTestExtension
    implements ExecutionCondition,
        BeforeAllCallback,
        BeforeEachCallback,
        AfterEachCallback,
        AfterAllCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(AmqpClientTestExtension.class);

  static {
    LOGGER.debug("Available processor(s): {}", Runtime.getRuntime().availableProcessors());
  }

  private static final ExtensionContext.Namespace NAMESPACE =
      ExtensionContext.Namespace.create(AmqpClientTestExtension.class);

  private static final AtomicReference<String> CURRENT_TEST = new AtomicReference<>();

  private static ExtensionContext.Store store(ExtensionContext extensionContext) {
    return extensionContext.getRoot().getStore(NAMESPACE);
  }

  static EventLoopGroup eventLoopGroup(ExtensionContext context) {
    return (EventLoopGroup) store(context).get("nettyEventLoopGroup");
  }

  private static boolean isSslSuite(ExtensionContext context) {
    return isTestSuite(context, SslTestSuite.class);
  }

  private static boolean isTestSuite(ExtensionContext context, Class<?> clazz) {
    return context.getUniqueId().contains(clazz.getName());
  }

  public static boolean isSSLAvailable() {
    return checkServerListening("localhost", 5671);
  }

  private static boolean checkServerListening(String host, int port) {
    Socket s = null;
    try {
      s = new Socket(host, port);
      return true;
    } catch (Exception e) {
      return false;
    } finally {
      if (s != null) {
        try {
          s.close();
        } catch (Exception e) {
        }
      }
    }
  }

  @Override
  public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
    if (isSslSuite(context) && !isSSLAvailable()) {
      return disabled("Required properties or TLS not available");
    } else {
      return enabled("ok");
    }
  }

  @Override
  public void beforeAll(ExtensionContext context) {
    if (TestUtils.isNetty()) {
      Duration timeout = Duration.ofMinutes(20);
      ScheduledFuture<?> task =
          executor(context)
              .schedule(
                  () -> {
                    try {
                      LOGGER.warn("Test {} has been running for {}", CURRENT_TEST.get(), timeout);
                      logThreadDump();
                    } catch (Exception e) {
                      LOGGER.warn("Error during test timeout task", e);
                    }
                  },
                  timeout.toMillis(),
                  MILLISECONDS);
      store(context).put("threadDumpTask", task);
      ThreadFactory tf = new NamedThreadFactory(context.getTestClass().get().getSimpleName() + "-");
      EventLoopGroup eventLoopGroup =
          new MultiThreadIoEventLoopGroup(tf, NioIoHandler.newFactory());
      store(context).put("nettyEventLoopGroup", eventLoopGroup);
      TestUtils.eventLoopGroup(eventLoopGroup);
    }
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    String test =
        String.format(
            "%s.%s",
            context.getTestClass().get().getSimpleName(), context.getTestMethod().get().getName());
    CURRENT_TEST.set(test);
    LOGGER.info("Starting test: {} (IO layer: {})", test, TestUtils.IO_LAYER);
  }

  @Override
  public void afterEach(ExtensionContext context) {
    LOGGER.info(
        "Test finished: {}.{}",
        context.getTestClass().get().getSimpleName(),
        context.getTestMethod().get().getName());
  }

  @Override
  public void afterAll(ExtensionContext context) {
    if (TestUtils.isNetty()) {
      TestUtils.resetEventLoopGroup();
      ScheduledFuture<?> threadDumpTask =
          store(context).get("threadDumpTask", ScheduledFuture.class);
      if (threadDumpTask != null) {
        threadDumpTask.cancel(true);
      }
      EventLoopGroup eventLoopGroup = eventLoopGroup(context);
      ExecutorService executor = executor(context);

      executor.submit(
          () -> {
            try {
              eventLoopGroup.shutdownGracefully(0, 0, SECONDS).get(10, SECONDS);
            } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
            } catch (Exception e) {
              LOGGER.warn("Error while asynchronously closing Netty event loop group", e);
            }
          });
    }
  }

  private static ScheduledExecutorService executor(ExtensionContext context) {
    ExecutorServiceCloseableResourceWrapper wrapper =
        context
            .getRoot()
            .getStore(ExtensionContext.Namespace.GLOBAL)
            .getOrComputeIfAbsent(ExecutorServiceCloseableResourceWrapper.class);
    return wrapper.executor();
  }

  private static class ExecutorServiceCloseableResourceWrapper implements AutoCloseable {

    private final ScheduledExecutorService executorService;

    private ExecutorServiceCloseableResourceWrapper() {
      this.executorService = Executors.newScheduledThreadPool(2);
    }

    private ScheduledExecutorService executor() {
      return this.executorService;
    }

    @Override
    public void close() {
      this.executorService.shutdownNow();
    }
  }

  private static class NamedThreadFactory implements ThreadFactory {

    private final ThreadFactory backingThreadFactory;

    private final String prefix;

    private final AtomicLong count = new AtomicLong(0);

    private NamedThreadFactory(String prefix) {
      this(Executors.defaultThreadFactory(), prefix);
    }

    private NamedThreadFactory(ThreadFactory backingThreadFactory, String prefix) {
      this.backingThreadFactory = backingThreadFactory;
      this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable r) {
      Thread thread = this.backingThreadFactory.newThread(r);
      thread.setName(prefix + count.getAndIncrement());
      return thread;
    }
  }

  private static void logThreadDump() {
    PlainTextThreadDumpFormatter formatter = new PlainTextThreadDumpFormatter();
    ThreadInfo[] threadInfos = ManagementFactory.getThreadMXBean().dumpAllThreads(true, true);
    String threadDump = formatter.format(threadInfos);
    LOGGER.warn(threadDump);
  }

  // from Spring Boot's PlainTextThreadDumpFormatter
  private static class PlainTextThreadDumpFormatter {

    String format(ThreadInfo[] threads) {
      StringWriter dump = new StringWriter();
      PrintWriter writer = new PrintWriter(dump);
      writePreamble(writer);
      for (ThreadInfo info : threads) {
        writeThread(writer, info);
      }
      return dump.toString();
    }

    private void writePreamble(PrintWriter writer) {
      DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
      writer.println(dateFormat.format(LocalDateTime.now()));
      RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
      writer.printf(
          "Full thread dump %s (%s %s):%n",
          runtime.getVmName(), runtime.getVmVersion(), System.getProperty("java.vm.info"));
      writer.println();
    }

    private void writeThread(PrintWriter writer, ThreadInfo info) {
      writer.printf("\"%s\" - Thread t@%d%n", info.getThreadName(), info.getThreadId());
      writer.printf("   %s: %s%n", Thread.State.class.getCanonicalName(), info.getThreadState());
      writeStackTrace(writer, info, info.getLockedMonitors());
      writer.println();
      writeLockedOwnableSynchronizers(writer, info);
      writer.println();
    }

    private void writeStackTrace(
        PrintWriter writer, ThreadInfo info, MonitorInfo[] lockedMonitors) {
      int depth = 0;
      for (StackTraceElement element : info.getStackTrace()) {
        writeStackTraceElement(
            writer, element, info, lockedMonitorsForDepth(lockedMonitors, depth), depth == 0);
        depth++;
      }
    }

    private List<MonitorInfo> lockedMonitorsForDepth(MonitorInfo[] lockedMonitors, int depth) {
      return Stream.of(lockedMonitors)
          .filter((lockedMonitor) -> lockedMonitor.getLockedStackDepth() == depth)
          .collect(Collectors.toList());
    }

    private void writeStackTraceElement(
        PrintWriter writer,
        StackTraceElement element,
        ThreadInfo info,
        List<MonitorInfo> lockedMonitors,
        boolean firstElement) {
      writer.printf("\tat %s%n", element.toString());
      LockInfo lockInfo = info.getLockInfo();
      if (firstElement && lockInfo != null) {
        if (element.getClassName().equals(Object.class.getName())
            && element.getMethodName().equals("wait")) {
          writer.printf("\t- waiting on %s%n", format(lockInfo));
        } else {
          String lockOwner = info.getLockOwnerName();
          if (lockOwner != null) {
            writer.printf(
                "\t- waiting to lock %s owned by \"%s\" t@%d%n",
                format(lockInfo), lockOwner, info.getLockOwnerId());
          } else {
            writer.printf("\t- parking to wait for %s%n", format(lockInfo));
          }
        }
      }
      writeMonitors(writer, lockedMonitors);
    }

    private String format(LockInfo lockInfo) {
      return String.format("<%x> (a %s)", lockInfo.getIdentityHashCode(), lockInfo.getClassName());
    }

    private void writeMonitors(PrintWriter writer, List<MonitorInfo> lockedMonitorsAtCurrentDepth) {
      for (MonitorInfo lockedMonitor : lockedMonitorsAtCurrentDepth) {
        writer.printf("\t- locked %s%n", format(lockedMonitor));
      }
    }

    private void writeLockedOwnableSynchronizers(PrintWriter writer, ThreadInfo info) {
      writer.println("   Locked ownable synchronizers:");
      LockInfo[] lockedSynchronizers = info.getLockedSynchronizers();
      if (lockedSynchronizers == null || lockedSynchronizers.length == 0) {
        writer.println("\t- None");
      } else {
        for (LockInfo lockedSynchronizer : lockedSynchronizers) {
          writer.printf("\t- Locked %s%n", format(lockedSynchronizer));
        }
      }
    }
  }
}
