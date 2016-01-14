package com.rabbitmq.client.test;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.util.Properties;

import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import com.rabbitmq.tools.Host;

public abstract class AbstractRMQTestSuite extends TestSuite {
  private static final String DEFAULT_SSL_HOSTNAME = "localhost";
  private static final int DEFAULT_SSL_PORT = 5671;

  private static boolean buildSSLPropertiesFound = false;

  static {
    Properties TESTS_PROPS = new Properties(System.getProperties());
    TESTS_PROPS.setProperty("make.bin",
        System.getenv("MAKE") == null ? "make" : System.getenv("MAKE"));
    try {
      TESTS_PROPS.load(Host.class.getClassLoader().getResourceAsStream("build.properties"));
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

    /* Path to rabbitmq_test. */
    String rabbitmq_test = Host.rabbitmqTestDir();
    if (rabbitmq_test == null || !new File(rabbitmq_test).isDirectory()) {
      System.err.println(
          "rabbitmq_test required; please set \"sibling.rabbitmq_test.dir\" system" +
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

  public static boolean isUnderUmbrella() {
    return new File("../../UMBRELLA.md").isFile();
  }

  public static boolean isSSLAvailable() {
    String SSL_CERTS_DIR = System.getenv("SSL_CERTS_DIR");
    String hostname = System.getProperty("broker.hostname");
    String port = System.getProperty("broker.sslport");
    if (SSL_CERTS_DIR == null || hostname == null || port == null)
      return false;

    String sslClientCertsDir = SSL_CERTS_DIR +
      FileSystems.getDefault().getSeparator() + "client";
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
