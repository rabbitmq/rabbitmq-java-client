// Copyright (c) 2007-2026 Broadcom. All Rights Reserved. The term "Broadcom" refers to Broadcom
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

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class UtilsTest {

  @CsvSource({
    // maxInboundMessageBodySize, frameMax, expected
    "65536,0,65536", // negotiated frame_max of 0 means "no limit", body cap must still apply
    "65536,16384,16384", // frame_max smaller than the body cap wins
    "16384,65536,16384", // body cap smaller than frame_max wins
    "65536,65536,65536",
  })
  @ParameterizedTest
  void inboundFrameMaxHonoursFrameMaxZeroAsUnlimited(
      int maxInboundMessageBodySize, int frameMax, int expected) {
    assertThat(Utils.inboundFrameMax(maxInboundMessageBodySize, frameMax)).isEqualTo(expected);
  }
}
