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
package com.rabbitmq.client.impl;

import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.nio.NioIoHandler;

/**
 * Netty is an optional dependency: only this class (used exclusively by {@link
 * NettyFrameHandlerFactory}) may reference Netty types, so that it is loaded only when Netty is
 * actually activated.
 */
final class NettyUtils {

  private static final boolean IS_NETTY_4_2;

  static {
    boolean netty4_2 = true;
    try {
      Class.forName("io.netty.channel.MultiThreadIoEventLoopGroup");
    } catch (ClassNotFoundException e) {
      netty4_2 = false;
    }
    IS_NETTY_4_2 = netty4_2;
  }

  private NettyUtils() {}

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
}
