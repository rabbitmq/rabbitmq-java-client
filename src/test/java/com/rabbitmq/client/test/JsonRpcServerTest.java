// Copyright (c) 2026 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom Inc. and/or its subsidiaries.
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

package com.rabbitmq.client.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbitmq.tools.jsonrpc.JacksonJsonRpcMapper;
import com.rabbitmq.tools.jsonrpc.JsonRpcServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class JsonRpcServerTest {

  JsonRpcServer server;

  @BeforeEach
  void init() throws Exception {
    server =
        new JsonRpcServer(
            mock(Channel.class),
            "q",
            Calculator.class,
            (Calculator) i -> i + 1,
            new JacksonJsonRpcMapper());
  }

  @Test
  void validRequestShouldGetResult() throws Exception {
    JsonNode response = call(request("increment", "[1]"));
    assertEquals(2, response.get("result").asInt());
  }

  @Test
  void malformedRequestsShouldGetBadRequestResponse() throws Exception {
    StringBuilder deep = new StringBuilder();
    for (int i = 0; i < 10_000; i++) {
      deep.append('[');
    }
    for (int i = 0; i < 10_000; i++) {
      deep.append(']');
    }
    String[] requests = {
      "not json",
      "{\"version\":\"1.1\",\"method\":",
      "{\"version\":\"1.1\",\"params\":[1]}",
      request("increment", "1"),
      request("unknown", "[1]"),
      request("increment", "[1,2]"),
      request("increment", "[\"not a number\"]"),
      request("increment", "[" + deep + "]")
    };
    for (String request : requests) {
      JsonNode error = call(request).get("error");
      assertEquals(400, error.get("code").asInt(), request);
    }
  }

  private JsonNode call(String request) throws Exception {
    return new ObjectMapper().readTree(server.doCall(request));
  }

  private static String request(String method, String params) {
    return "{\"version\":\"1.1\",\"method\":\"" + method + "\",\"params\":" + params + "}";
  }

  public interface Calculator {
    Integer increment(Integer i);
  }
}
