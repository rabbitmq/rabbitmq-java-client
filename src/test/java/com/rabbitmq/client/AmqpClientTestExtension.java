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

import static org.junit.jupiter.api.extension.ConditionEvaluationResult.disabled;
import static org.junit.jupiter.api.extension.ConditionEvaluationResult.enabled;

import com.rabbitmq.client.test.TestUtils;
import com.rabbitmq.client.test.functional.FunctionalTestSuite;
import com.rabbitmq.client.test.server.HaTestSuite;
import com.rabbitmq.client.test.server.ServerTestSuite;
import com.rabbitmq.client.test.ssl.SslTestSuite;
import com.rabbitmq.tools.Host;
import java.net.Socket;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AmqpClientTestExtension
    implements ExecutionCondition, BeforeAllCallback, BeforeEachCallback, AfterEachCallback {

  private static final Logger LOGGER = LoggerFactory.getLogger(AmqpClientTestExtension.class);

  private static boolean isFunctionalSuite(ExtensionContext context) {
    return isTestSuite(context, FunctionalTestSuite.class);
  }

  private static boolean isSslSuite(ExtensionContext context) {
    return isTestSuite(context, SslTestSuite.class);
  }

  private static boolean isServerSuite(ExtensionContext context) {
    return isTestSuite(context, ServerTestSuite.class);
  }

  private static boolean isHaSuite(ExtensionContext context) {
    return isTestSuite(context, HaTestSuite.class);
  }

  private static boolean isTestSuite(ExtensionContext context, Class<?> clazz) {
    return context.getUniqueId().contains(clazz.getName());
  }

  public static boolean requiredProperties() {
    /* Path to rabbitmqctl. */
    String rabbitmqctl = Host.rabbitmqctlCommand();
    if (rabbitmqctl == null) {
      System.err.println(
          "rabbitmqctl required; please set \"rabbitmqctl.bin\" system" + " property");
      return false;
    }

    return true;
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
    // HA test suite must be checked first because it contains other test suites
    if (isHaSuite(context)) {
      return requiredProperties()
          ? enabled("Required properties available")
          : disabled("Required properties not available");
    } else if (isServerSuite(context)) {
      return requiredProperties()
          ? enabled("Required properties available")
          : disabled("Required properties not available");
    } else if (isFunctionalSuite(context)) {
      return requiredProperties()
          ? enabled("Required properties available")
          : disabled("Required properties not available");
    } else if (isSslSuite(context)) {
      return requiredProperties() && isSSLAvailable()
          ? enabled("Required properties and TLS available")
          : disabled("Required properties or TLS not available");
    }
    return enabled("ok");
  }

  @Override
  public void beforeAll(ExtensionContext context) {}

  @Override
  public void beforeEach(ExtensionContext context) {
    LOGGER.info(
        "Starting test: {}.{} (nio? {})",
        context.getTestClass().get().getSimpleName(),
        context.getTestMethod().get().getName(),
        TestUtils.USE_NIO);
  }

  @Override
  public void afterEach(ExtensionContext context) {
    LOGGER.info(
        "Test finished: {}.{}",
        context.getTestClass().get().getSimpleName(),
        context.getTestMethod().get().getName());
  }
}
