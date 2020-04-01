// Copyright (c) 2007-2020 VMware, Inc. or its affiliates.  All rights reserved.
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

package com.rabbitmq.client.test;

import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Collections;

import com.rabbitmq.client.RecoveryDelayHandler;
import com.rabbitmq.client.RecoveryDelayHandler.DefaultRecoveryDelayHandler;
import com.rabbitmq.client.RecoveryDelayHandler.ExponentialBackoffDelayHandler;

import org.junit.Test;

public class RecoveryDelayHandlerTest {

    @Test
    public void testDefaultRecoveryDelayHandler() {
        final RecoveryDelayHandler handler = new DefaultRecoveryDelayHandler(5000);
        assertEquals(5000L, handler.getDelay(0));
        assertEquals(5000L, handler.getDelay(1));
        assertEquals(5000L, handler.getDelay(Integer.MAX_VALUE));
    }
    
    @Test
    public void testExponentialBackoffDelayHandlerDefaults() {
        final RecoveryDelayHandler handler = new ExponentialBackoffDelayHandler();
        assertEquals(2000L, handler.getDelay(0));
        assertEquals(3000L, handler.getDelay(1));
        assertEquals(5000L, handler.getDelay(2));
        assertEquals(8000L, handler.getDelay(3));
        assertEquals(13000L, handler.getDelay(4));
        assertEquals(21000L, handler.getDelay(5));
        assertEquals(34000L, handler.getDelay(6));
        assertEquals(34000L, handler.getDelay(7));
        assertEquals(34000L, handler.getDelay(8));
        assertEquals(34000L, handler.getDelay(9));
        assertEquals(34000L, handler.getDelay(Integer.MAX_VALUE));
    }
    
    @Test
    public void testExponentialBackoffDelayHandlerSequence() {
        final RecoveryDelayHandler handler = new ExponentialBackoffDelayHandler(Arrays.asList(1L, 2L));
        assertEquals(1, handler.getDelay(0));
        assertEquals(2, handler.getDelay(1));
        assertEquals(2, handler.getDelay(2));
        assertEquals(2, handler.getDelay(Integer.MAX_VALUE));
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testExponentialBackoffDelayHandlerWithNullSequence() {
        new ExponentialBackoffDelayHandler(null);
    }
    
    @Test(expected=IllegalArgumentException.class)
    public void testExponentialBackoffDelayHandlerWithEmptySequence() {
        new ExponentialBackoffDelayHandler(Collections.<Long>emptyList());
    }
}
