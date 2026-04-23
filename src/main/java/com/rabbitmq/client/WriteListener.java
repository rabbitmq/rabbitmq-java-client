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
package com.rabbitmq.client;

/**
 * Listener notified when the network layer has finished writing a message body.
 *
 * <p>This is primarily useful when publishing with an off-heap {@link java.nio.ByteBuffer}: the
 * callback tells the caller when the buffer is no longer in use and can be safely released.
 *
 * <p>This only applies when using Netty as the IO layer and is unnecessary when using the blocking
 * IO layer.
 *
 * <p><strong>Threading:</strong> the callback may be invoked on the Netty event loop thread.
 * Implementations must not perform blocking operations.
 *
 * <p>This API is experimental and is susceptible to change at any time.
 *
 * @since 5.30.0
 */
@FunctionalInterface
public interface WriteListener {

  /**
   * Called when the write operation completes.
   *
   * @param success {@code true} if the data was written to the socket successfully
   * @param cause the exception if the write failed, {@code null} on success
   */
  void done(boolean success, Throwable cause);
}
