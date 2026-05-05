// Copyright (c) 2007-2026 Broadcom. All Rights Reserved.
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
package com.rabbitmq.client.test.server;

import static com.rabbitmq.tools.Host.rabbitmqctl;
import static com.rabbitmq.tools.Host.rabbitmqctlIgnoreErrors;
import static java.lang.String.format;
import static org.junit.jupiter.api.Assertions.*;

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.AMQChannel;
import com.rabbitmq.client.impl.recovery.AutorecoveringChannel;
import com.rabbitmq.client.test.BrokerTestCase;
import com.rabbitmq.client.test.TestUtils;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Permissions extends BrokerTestCase {

  private static final Logger LOGGER = LoggerFactory.getLogger(Permissions.class);

  // User and vhost constants
  private static final String TEST_USER = "test";
  private static final String TEST_ADMIN_USER = "testadmin";
  private static final String TEST_PASSWORD = "test";
  private static final String TEST_VHOST = "/test";

  // Permission constants
  private static final String CONFIGURE_PERMISSION = "configure";
  private static final String WRITE_PERMISSION = "write";
  private static final String READ_PERMISSION = "read";
  private static final String FULL_PERMISSIONS = "\".*\"";
  private static final String NO_PERMISSIONS = "\"\"";

  private Channel adminCh;

  public Permissions() {
    ConnectionFactory factory = TestUtils.connectionFactory();
    factory.setUsername(TEST_USER);
    factory.setPassword(TEST_PASSWORD);
    factory.setVirtualHost(TEST_VHOST);
    connectionFactory = factory;
  }

  @BeforeEach
  @Override
  public void setUp(TestInfo info) throws IOException, TimeoutException {
    deleteRestrictedAccount();
    addRestrictedAccount();
    super.setUp(info);
  }

  @AfterEach
  @Override
  public void tearDown(TestInfo info) throws IOException, TimeoutException {
    super.tearDown(info);
    deleteRestrictedAccount();
  }

  protected void addRestrictedAccount() throws IOException {
    rabbitmqctl(format("add_user %s %s", TEST_USER, TEST_PASSWORD));
    rabbitmqctl(format("add_user %s %s", TEST_ADMIN_USER, TEST_PASSWORD));
    rabbitmqctl(format("add_vhost %s", TEST_VHOST));
    rabbitmqctl(
        format(
            "set_permissions -p %s %s %s %s %s",
            TEST_VHOST, TEST_USER, CONFIGURE_PERMISSION, WRITE_PERMISSION, READ_PERMISSION));
    rabbitmqctl(
        format(
            "set_permissions -p %s %s %s %s %s",
            TEST_VHOST, TEST_ADMIN_USER, FULL_PERMISSIONS, FULL_PERMISSIONS, FULL_PERMISSIONS));
  }

  protected void deleteRestrictedAccount() throws IOException {
    rabbitmqctlIgnoreErrors(format("clear_permissions -p %s %s", TEST_VHOST, TEST_ADMIN_USER));
    rabbitmqctlIgnoreErrors(format("clear_permissions -p %s %s", TEST_VHOST, TEST_USER));
    rabbitmqctlIgnoreErrors(format("delete_vhost %s", TEST_VHOST));
    rabbitmqctlIgnoreErrors(format("delete_user %s", TEST_ADMIN_USER));
    rabbitmqctlIgnoreErrors(format("delete_user %s", TEST_USER));
  }

  protected void createResources() throws IOException, TimeoutException {
    ConnectionFactory factory = TestUtils.connectionFactory();
    factory.setUsername(TEST_ADMIN_USER);
    factory.setPassword(TEST_PASSWORD);
    factory.setVirtualHost(TEST_VHOST);
    Connection connection = factory.newConnection();
    adminCh = connection.createChannel();
    withNames(
        name -> {
          adminCh.exchangeDeclare(name, "direct");
          adminCh.queueDelete(name);
          adminCh.queueDeclare(name, true, false, false, null);
        });
  }

  protected void releaseResources() throws IOException {
    withNames(
        name -> {
          adminCh.queueDelete(name);
          adminCh.exchangeDelete(name);
        });
    adminCh.getConnection().abort(10_000);
  }

  private void withNames(WithName action) throws IOException {
    action.with(CONFIGURE_PERMISSION);
    action.with(WRITE_PERMISSION);
    action.with(READ_PERMISSION);
    action.with("none");
  }

  @Test
  public void auth() throws TimeoutException {
    ConnectionFactory unAuthFactory = TestUtils.connectionFactory();
    unAuthFactory.setUsername(TEST_USER);
    unAuthFactory.setPassword(TEST_PASSWORD + "wrong");

    try {
      unAuthFactory.newConnection();
      fail("Exception expected if password is wrong");
    } catch (IOException e) {
      assertInstanceOf(AuthenticationFailureException.class, e);
      String msg = e.getMessage();
      assertTrue(msg.toLowerCase().contains("auth"), "Exception message should contain 'auth'");
    }
  }

  @Test
  public void exchangeConfiguration() throws IOException {
    runConfigureTest(name -> channel.exchangeDeclare(name, "direct"));
    runConfigureTest(name -> channel.exchangeDelete(name));
  }

  @Test
  public void queueConfiguration() throws IOException {
    runConfigureTest(
        name -> {
          channel.queueDelete(name);
          channel.queueDeclare(name, true, false, false, null);
        });
    runConfigureTest(name -> channel.queueDelete(name));
  }

  @Test
  public void passiveDeclaration(TestInfo info) throws IOException {
    if (TestUtils.isVersion431orLater(connection)) {
      // any permission works for passive declaration (Rmq 4.2.7+, 4.3.1+)
      // but at least one permission required
      // see https://github.com/rabbitmq/rabbitmq-server/pull/16272
      runTest(true, true, true, false, name -> channel.exchangeDeclarePassive(name));
      runTest(true, true, true, false, name -> channel.queueDeclarePassive(name));
    } else {
      LOGGER.info(
          "Skipping {}.{} test",
          Permissions.class.getSimpleName(),
          info.getTestMethod().get().getName());
    }
  }

  @Test
  public void binding() throws IOException {
    runTest(false, true, false, false, name -> channel.queueBind(name, READ_PERMISSION, ""));
    runTest(false, false, true, false, name -> channel.queueBind(WRITE_PERMISSION, name, ""));
  }

  @Test
  public void publish() throws IOException {
    runTest(
        false,
        true,
        false,
        false,
        name -> {
          channel.basicPublish(name, "", null, "foo".getBytes());
          // followed by a dummy synchronous command in order
          // to catch any errors
          channel.basicQos(0);
        });
  }

  @Test
  public void get() throws IOException {
    runTest(false, false, true, false, name -> channel.basicGet(name, true));
  }

  @Test
  public void consume() throws IOException {
    runTest(
        false,
        false,
        true,
        false,
        name -> channel.basicConsume(name, new QueueingConsumer(channel)));
  }

  @Test
  public void purge() throws IOException {
    runTest(
        false,
        false,
        true,
        false,
        name -> {
          AMQChannel channelDelegate = (AMQChannel) ((AutorecoveringChannel) channel).getDelegate();
          channelDelegate.exnWrappingRpc(new AMQP.Queue.Purge.Builder().queue(name).build());
        });
  }

  @Test
  public void altExchConfiguration() throws IOException {
    runTest(false, false, false, false, createAltExchConfigTest("configure-me"));
    runTest(false, false, false, false, createAltExchConfigTest("configure-and-write-me"));
    runTest(false, true, false, false, createAltExchConfigTest("configure-and-read-me"));
  }

  @Test
  public void dLXConfiguration() throws IOException {
    runTest(false, false, false, false, createDLXConfigTest("configure-me"));
    runTest(false, false, false, false, createDLXConfigTest("configure-and-write-me"));
    runTest(false, true, false, false, createDLXConfigTest("configure-and-read-me"));
  }

  @Test
  public void noAccess() throws IOException, InterruptedException {
    rabbitmqctl(
        format(
            "set_permissions -p %s %s %s %s %s",
            TEST_VHOST, TEST_USER, NO_PERMISSIONS, NO_PERMISSIONS, NO_PERMISSIONS));
    Thread.sleep(2000);

    assertAccessRefused(_e -> channel.queueDeclare());
    assertAccessRefused(_e -> channel.queueDeclare("justaqueue", true, false, true, null));
    assertAccessRefused(_e -> channel.queueDelete(CONFIGURE_PERMISSION));
    assertAccessRefused(
        _e -> channel.queueBind(WRITE_PERMISSION, WRITE_PERMISSION, WRITE_PERMISSION));
    assertAccessRefused(_e -> channel.queuePurge(READ_PERMISSION));
    assertAccessRefused(_e -> channel.exchangeDeclare("justanexchange", "direct"));
    assertAccessRefused(_e -> channel.exchangeDeclare(CONFIGURE_PERMISSION, "direct"));
    assertAccessRefused(
        _e -> {
          channel.basicPublish(WRITE_PERMISSION, "", null, "foo".getBytes());
          channel.basicQos(0);
        });
    assertAccessRefused(_e -> channel.basicGet(READ_PERMISSION, false));
    assertAccessRefused(_e -> channel.basicConsume(READ_PERMISSION, null));
  }

  private WithName createAltExchConfigTest(final String exchange) {
    return ae -> {
      Map<String, Object> args = new HashMap<>();
      args.put("alternate-exchange", ae);
      channel.exchangeDeclare(exchange, "direct", false, false, args);
      channel.exchangeDelete(exchange);
    };
  }

  private WithName createDLXConfigTest(final String queue) {
    return dlx -> {
      Map<String, Object> args = new HashMap<>();
      args.put("x-dead-letter-exchange", dlx);
      channel.queueDelete(queue);
      channel.queueDeclare(queue, true, false, false, args);
      channel.queueDelete(queue);
    };
  }

  private void runConfigureTest(WithName test) throws IOException {
    runTest(true, "configure-me", test);
    runTest(false, "write-me", test);
    runTest(false, "read-me", test);
    runTest(false, "none", test);
  }

  private void runTest(
      boolean shouldPassC,
      boolean shouldPassW,
      boolean shouldPassR,
      boolean shouldPassNone,
      WithName test)
      throws IOException {
    runTest(shouldPassC, CONFIGURE_PERMISSION, test);
    runTest(shouldPassW, WRITE_PERMISSION, test);
    runTest(shouldPassR, READ_PERMISSION, test);
    runTest(shouldPassNone, "none", test);
  }

  private void assertAccessRefused(WithName test) throws IOException {
    runTest(false, "", test);
  }

  private void runTest(boolean shouldPass, String resourceName, WithName test) throws IOException {
    String msg = format("'%s' -> %s", resourceName, shouldPass);
    try {
      test.with(resourceName);
      assertTrue(shouldPass, msg);
    } catch (IOException e) {
      assertFalse(shouldPass, msg);
      checkShutdownSignal(AMQP.ACCESS_REFUSED, e);
      openChannel();
    } catch (AlreadyClosedException e) {
      assertFalse(shouldPass, msg);
      checkShutdownSignal(AMQP.ACCESS_REFUSED, e);
      openChannel();
    }
  }

  @FunctionalInterface
  interface WithName {
    void with(String resourceName) throws IOException;
  }
}
