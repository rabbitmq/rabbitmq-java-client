// Copyright (c) 2018 Pivotal Software, Inc.  All rights reserved.
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

package com.rabbitmq.client;

import com.rabbitmq.client.test.TestUtils;
import com.rabbitmq.tools.jsonrpc.JsonRpcClient;
import com.rabbitmq.tools.jsonrpc.JsonRpcMapper;
import com.rabbitmq.tools.jsonrpc.JsonRpcServer;
import org.junit.After;
import org.junit.Before;

import java.util.Date;

public abstract class AbstractJsonRpcTest {

    Connection clientConnection, serverConnection;
    Channel clientChannel, serverChannel;
    String queue = "json.rpc.queue";
    JsonRpcServer server;
    JsonRpcClient client;
    RpcService service;

    abstract JsonRpcMapper createMapper();

    @Before
    public void init() throws Exception {
        clientConnection = TestUtils.connectionFactory().newConnection();
        clientChannel = clientConnection.createChannel();
        serverConnection = TestUtils.connectionFactory().newConnection();
        serverChannel = serverConnection.createChannel();
        serverChannel.queueDeclare(queue, false, false, false, null);
        server = new JsonRpcServer(serverChannel, queue, RpcService.class, new DefaultRpcservice(), createMapper());
        new Thread(() -> {
            try {
                server.mainloop();
            } catch (Exception e) {
                // safe to ignore when loops ends/server is canceled
            }
        }).start();
        client = new JsonRpcClient(
                    new RpcClientParams().channel(clientChannel).exchange("").routingKey(queue).timeout(1000),
                    createMapper()
        );
        service = client.createProxy(RpcService.class);
    }

    @After
    public void tearDown() throws Exception {
        if (server != null) {
            server.terminateMainloop();
        }
        if (client != null) {
            client.close();
        }
        if (serverChannel != null) {
            serverChannel.queueDelete(queue);
        }
        clientConnection.close();
        serverConnection.close();
    }

    public interface RpcService {

        boolean procedurePrimitiveBoolean(boolean input);

        Boolean procedureBoolean(Boolean input);

        String procedureString(String input);

        String procedureStringString(String input1, String input2);

        int procedurePrimitiveInteger(int input);

        Integer procedureInteger(Integer input);

        Double procedureDouble(Double input);

        double procedurePrimitiveDouble(double input);

        Integer procedureLongToInteger(Long input);

        int procedurePrimitiveLongToInteger(long input);

        Long procedureLong(Long input);

        long procedurePrimitiveLong(long input);

        Pojo procedureIntegerToPojo(Integer id);

        String procedurePojoToString(Pojo pojo);

        void procedureException();

        void procedureNoArgumentVoid();

        Date procedureDateDate(Date date);
    }

    public static class DefaultRpcservice implements RpcService {

        @Override
        public boolean procedurePrimitiveBoolean(boolean input) {
            return !input;
        }

        @Override
        public Boolean procedureBoolean(Boolean input) {
            return Boolean.valueOf(!input.booleanValue());
        }

        @Override
        public String procedureString(String input) {
            return input + 1;
        }

        @Override
        public String procedureStringString(String input1, String input2) {
            return input1 + input2;
        }

        @Override
        public int procedurePrimitiveInteger(int input) {
            return input + 1;
        }

        @Override
        public Integer procedureInteger(Integer input) {
            return input + 1;
        }

        @Override
        public Long procedureLong(Long input) {
            return input + 1;
        }

        @Override
        public long procedurePrimitiveLong(long input) {
            return input + 1L;
        }

        @Override
        public Double procedureDouble(Double input) {
            return input + 1;
        }

        @Override
        public double procedurePrimitiveDouble(double input) {
            return input + 1;
        }

        @Override
        public Integer procedureLongToInteger(Long input) {
            return (int) (input + 1);
        }

        @Override
        public int procedurePrimitiveLongToInteger(long input) {
            return (int) input + 1;
        }

        @Override
        public Pojo procedureIntegerToPojo(Integer id) {
            Pojo pojo = new Pojo();
            pojo.setStringProperty(id.toString());
            return pojo;
        }

        @Override
        public String procedurePojoToString(Pojo pojo) {
            return pojo.getStringProperty();
        }

        @Override
        public void procedureException() {
            throw new RuntimeException();
        }

        @Override
        public void procedureNoArgumentVoid() {

        }

        @Override
        public Date procedureDateDate(Date date) {
            return date;
        }
    }

    public static class Pojo {

        private String stringProperty;

        public String getStringProperty() {
            return stringProperty;
        }

        public void setStringProperty(String stringProperty) {
            this.stringProperty = stringProperty;
        }
    }
}
