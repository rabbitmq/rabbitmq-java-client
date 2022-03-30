// Copyright (c) 2022 VMware, Inc. or its affiliates.  All rights reserved.
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

final class Utils {

  private static final int AVAILABLE_PROCESSORS =
      Integer.parseInt(
          System.getProperty(
              "rabbitmq.amqp.client.availableProcessors",
              String.valueOf(Runtime.getRuntime().availableProcessors())));

  static int availableProcessors() {
    return AVAILABLE_PROCESSORS;
  }

  private Utils() {}
}
