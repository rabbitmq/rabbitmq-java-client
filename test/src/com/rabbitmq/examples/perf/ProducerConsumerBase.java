// Copyright (c) 2007-Present Pivotal Software, Inc.  All rights reserved.
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

package com.rabbitmq.examples.perf;

/**
 *
 */
public class ProducerConsumerBase {
    protected float rateLimit;
    protected long  lastStatsTime;
    protected int   msgCount;

    protected void delay(long now) {

        long elapsed = now - lastStatsTime;
        //example: rateLimit is 5000 msg/s,
        //10 ms have elapsed, we have sent 200 messages
        //the 200 msgs we have actually sent should have taken us
        //200 * 1000 / 5000 = 40 ms. So we pause for 40ms - 10ms
        long pause = (long) (rateLimit == 0.0f ?
            0.0f : (msgCount * 1000.0 / rateLimit - elapsed));
        if (pause > 0) {
            try {
                Thread.sleep(pause);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
