// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
//
// This software, the RabbitMQ Java client library, is triple-licensed under the
// Mozilla Public License 1.1 ("MPL"), the GNU General Public License version 2
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

package com.rabbitmq.client.test;

import com.rabbitmq.tools.Host;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.util.Properties;

public abstract class AbstractRMQTestSuite {

  static {
    Properties TESTS_PROPS = new Properties(System.getProperties());
    String make = System.getenv("MAKE");
    if (make != null)
      TESTS_PROPS.setProperty("make.bin", make);
    try {
      TESTS_PROPS.load(Host.class.getClassLoader().getResourceAsStream("config.properties"));
    } catch (Exception e) {
      System.out.println(
          "\"build.properties\" or \"config.properties\" not found" +
          " in classpath. Please copy \"build.properties\" and" +
          " \"config.properties\" into src/test/resources. Ignore" +
          " this message if running with ant.");
    } finally {
       System.setProperties(TESTS_PROPS);
    }
  }

  public static boolean requiredProperties() {
    /* GNU Make. */
    String make = Host.makeCommand();
    boolean isGNUMake = false;
    if (make != null) {
      try {
        Process makeProc = Host.executeCommandIgnoringErrors(make + " --version");
        String makeVersion = Host.capture(makeProc.getInputStream());
        isGNUMake = makeVersion.startsWith("GNU Make");
      } catch (IOException e) {}
    }
    if (!isGNUMake) {
      System.err.println(
          "GNU Make required; please set \"make.bin\" system property" +
          " or \"$MAKE\" environment variable");
      return false;
    }

    /* Path to RabbitMQ. */
    String rabbitmq = Host.rabbitmqDir();
    if (rabbitmq == null || !new File(rabbitmq).isDirectory()) {
      System.err.println(
          "RabbitMQ required; please set \"rabbitmq.dir\" system" +
          " property");
      return false;
    }

    /* Path to rabbitmqctl. */
    String rabbitmqctl = Host.rabbitmqctlCommand();
    if (rabbitmqctl == null || !new File(rabbitmqctl).isFile()) {
      System.err.println(
          "rabbitmqctl required; please set \"rabbitmqctl.bin\" system" +
          " property");
      return false;
    }

    return true;
  }

  public static boolean isSSLAvailable() {
    String sslClientCertsDir = System.getProperty("test-client-cert.path");
    String hostname = System.getProperty("broker.hostname");
    String port = System.getProperty("broker.sslport");
    if (sslClientCertsDir == null || hostname == null || port == null)
      return false;

    // If certificate is present and some server is listening on port 5671
    if (new File(sslClientCertsDir).exists() &&
        checkServerListening(hostname, Integer.parseInt(port))) {
      return true;
    } else
      return false;
  }

  private static boolean checkServerListening(String host, int port) {
    Socket s = null;
    try {
      s = new Socket(host, port);
      return true;
    } catch (Exception e) {
      return false;
    } finally {
      if (s != null)
        try {
          s.close();
        } catch (Exception e) {
        }
    }
  }
}
