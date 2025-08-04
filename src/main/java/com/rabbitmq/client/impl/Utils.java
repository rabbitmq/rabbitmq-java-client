// Copyright (c) 2007-2025 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom
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

package com.rabbitmq.client.impl;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;

import java.util.function.Consumer;

final class Utils {

  @SuppressWarnings("rawtypes")
  private static final Consumer NO_OP_CONSUMER = o -> {};

  static final boolean IS_NETTY_4_2;

  private static final int AVAILABLE_PROCESSORS =
      Integer.parseInt(
          System.getProperty(
              "rabbitmq.amqp.client.availableProcessors",
              String.valueOf(Runtime.getRuntime().availableProcessors())));

  static {
    boolean netty4_2 = true;
    try {
      Class.forName("io.netty.channel.MultiThreadIoEventLoopGroup");
    } catch (ClassNotFoundException e) {
      netty4_2 = false;
    }
    IS_NETTY_4_2 = netty4_2;
  }

  private Utils() {}

  static int availableProcessors() {
    return AVAILABLE_PROCESSORS;
  }

  @SuppressWarnings("deprecation")
  static EventLoopGroup eventLoopGroup() {
    if (IS_NETTY_4_2) {
      return new MultiThreadIoEventLoopGroup(NioIoHandler.newFactory());
    } else {
      return new io.netty.channel.nio.NioEventLoopGroup();
    }
  }

  static ByteBufAllocator byteBufAllocator() {
    return ByteBufAllocator.DEFAULT;
  }

  @SuppressWarnings("unchecked")
  static <T> Consumer<T> noOpConsumer() {
    return (Consumer<T>) NO_OP_CONSUMER;
  }
}
