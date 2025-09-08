// Copyright (c) 2023-2025 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom
// Inc. and/or its subsidiaries.
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

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.junit.jupiter.api.extension.ConditionEvaluationResult.disabled;
import static org.junit.jupiter.api.extension.ConditionEvaluationResult.enabled;

import com.rabbitmq.client.test.TestUtils;
import com.rabbitmq.client.test.functional.FunctionalTestSuite;
import com.rabbitmq.client.test.server.HaTestSuite;
import com.rabbitmq.client.test.server.ServerTestSuite;
import com.rabbitmq.client.test.ssl.SslTestSuite;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

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
      ThreadFactory tf = new NamedThreadFactory(context.getTestClass().get().getSimpleName() + "-");
      EventLoopGroup eventLoopGroup = new MultiThreadIoEventLoopGroup(tf, NioIoHandler.newFactory());
      store(context)
          .put("nettyEventLoopGroup", eventLoopGroup);
      TestUtils.eventLoopGroup(eventLoopGroup);
    }
  }

  @Override
  public void beforeEach(ExtensionContext context) {
    LOGGER.info(
        "Starting test: {}.{} (IO layer: {})",
        context.getTestClass().get().getSimpleName(),
        context.getTestMethod().get().getName(),
        TestUtils.IO_LAYER);
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
      EventLoopGroup eventLoopGroup = eventLoopGroup(context);
      ExecutorServiceCloseableResourceWrapper wrapper =
          context
              .getRoot()
              .getStore(ExtensionContext.Namespace.GLOBAL)
              .getOrComputeIfAbsent(ExecutorServiceCloseableResourceWrapper.class);

      wrapper
          .executorService
          .submit(
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

  private static class ExecutorServiceCloseableResourceWrapper implements AutoCloseable {

    private final ExecutorService executorService;

    private ExecutorServiceCloseableResourceWrapper() {
      this.executorService = Executors.newCachedThreadPool();
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
}
