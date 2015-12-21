package com.rabbitmq.client.test;

import java.io.File;
import java.io.IOException;
import java.net.Socket;
import java.nio.file.FileSystems;
import java.util.Properties;

import junit.framework.Test;
import junit.framework.TestResult;
import junit.framework.TestSuite;

import org.junit.Assume;

public abstract class AbstractRMQTestSuite extends TestSuite {
  private static final int DEFAULT_SSL_PORT = 443;
  
  private static boolean buildPropertiesFound = false;

  private static final Properties TESTS_PROPS = new Properties(System.getProperties());
  static {
    TESTS_PROPS.setProperty("make.bin", System.getenv("MAKE") == null ? "make" : System.getenv("MAKE"));
    try {
      TESTS_PROPS.load(AbstractRMQTestSuite.class.getClassLoader().getResourceAsStream("build.properties"));
      TESTS_PROPS.load(AbstractRMQTestSuite.class.getClassLoader().getResourceAsStream("config.properties"));
      buildPropertiesFound = true;
    } catch (IOException e) {
      System.err
          .println("build.properties or config.properties not found in classpath,copy build.properties and config.properties into src/test/resources");
    }
  }

  public AbstractRMQTestSuite() {
    System.setProperties(TESTS_PROPS);
  }

  public static boolean isUnderUmbrella() {
    return new File("../../UMBRELLA.md").isFile();
  }

  public static boolean isSSlAvailable() {
    System.setProperty("SSL_CERTS_DIR", System.getenv("SSL_CERTS_DIR"));
    String sslClientPath = System.getProperty("SSL_CERTS_DIR") + FileSystems.getDefault().getSeparator() + "client";
    System.setProperty("CLIENT_KEYSTORE_PHRASE", System.getenv("bunnies"));
    System.setProperty("SSL_P12_PASSWORD", System.getenv("PASSWORD"));
    // IF certificate is present and some server is listening on port 443
    if (new File(sslClientPath).isFile() && checkServerListening(System.getProperty("broker.hostname"), DEFAULT_SSL_PORT)) {
      System.setProperty("SSL_AVAILABLE", sslClientPath);
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

  public void runTest(Test test, TestResult result) {
    // Run the tests only if build.properties was found
    Assume.assumeTrue(buildPropertiesFound);
    test.run(result);
  }

  
//  <target name="make-client-keystore" if="SSL_AVAILABLE" depends="detect-ssl, detect-tmpdir">
//  <exec executable="mktemp" outputproperty="CLIENT_KEYSTORE" failonerror="true" osfamily="unix">
//    <arg value="-u"/>
//    <arg value="${TMPDIR}/tmp.XXXXXXXXXX"/>
//  </exec>
//  <exec executable="keytool" failonerror="true" osfamily="unix">
//    <arg line="-import"/>
//    <arg value="-alias"/>
//    <arg value="server1"/>
//    <arg value="-file"/>
//    <arg value="${SSL_CERTS_DIR}/testca/cacert.pem"/>
//    <arg value="-keystore"/>
//    <arg value="${CLIENT_KEYSTORE}"/>
//    <arg value="-noprompt"/>
//    <arg value="-storepass"/>
//    <arg value="${CLIENT_KEYSTORE_PHRASE}"/>
//  </exec>
//  <exec executable="mktemp" outputproperty="CLIENT_KEYSTORE_EMPTY" failonerror="true" osfamily="unix">
//    <arg value="-u"/>
//    <arg value="${TMPDIR}/tmp.XXXXXXXXXX"/>
//  </exec>
//  <!-- can't create an empty keystore, so add cert in and then delete it! -->
//  <exec executable="keytool" failonerror="true" osfamily="unix">
//    <arg line="-import"/>
//    <arg value="-alias"/>
//    <arg value="server1"/>
//    <arg value="-file"/>
//    <arg value="${SSL_CERTS_DIR}/testca/cacert.pem"/>
//    <arg value="-keystore"/>
//    <arg value="${CLIENT_KEYSTORE_EMPTY}"/>
//    <arg value="-noprompt"/>
//    <arg value="-storepass"/>
//    <arg value="${CLIENT_KEYSTORE_PHRASE}"/>
//  </exec>
//  <exec executable="keytool" failonerror="true" osfamily="unix">
//    <arg line="-delete"/>
//    <arg value="-alias"/>
//    <arg value="server1"/>
//    <arg value="-keystore"/>
//    <arg value="${CLIENT_KEYSTORE_EMPTY}"/>
//    <arg value="-storepass"/>
//    <arg value="${CLIENT_KEYSTORE_PHRASE}"/>
//  </exec>
//</target>
//
//<target name="remove-client-keystore" if="SSL_AVAILABLE">
//  <delete file="${CLIENT_KEYSTORE}" failonerror="false"/>
//  <delete file="${CLIENT_KEYSTORE_EMPTY}" failonerror="false"/>
//</target>

}
