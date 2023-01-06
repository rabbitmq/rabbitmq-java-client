// Copyright (c) 2007-2023 VMware, Inc. or its affiliates.  All rights reserved.
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

package com.rabbitmq.utility;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class IntAllocatorTests {

    private static final int TEST_ITERATIONS = 50000;
    private static final int HI_RANGE = 100000;
    private static final int LO_RANGE = 100;
    private final IntAllocator iAll = new IntAllocator(LO_RANGE, HI_RANGE);

    private final Random rand = new Random(70608L);

    @Test public void reserveAndFree() throws Exception {
        Set<Integer> set = new HashSet<Integer>();
        for (int i = 0; i < TEST_ITERATIONS; ++i) {
            int trial = getTrial(rand);
            if (set.contains(trial)) {
                iAll.free(trial);
                set.remove(trial);
            } else {
                assertTrue(iAll.reserve(trial), "Did not reserve free integer " + trial);
                set.add(trial);
            }
        }

        for (int trial : set) {
            assertFalse(iAll.reserve(trial), "Integer " + trial + " not allocated!");
        }
    }

    @Test public void allocateAndFree() {
        Set<Integer> set = new HashSet<Integer>();
        for (int i=0; i < TEST_ITERATIONS; ++i) {
            if (getBool(rand)) {
                int trial = iAll.allocate();
                assertFalse(set.contains(trial), "Already allocated " + trial);
                set.add(trial);
            } else {
                if (!set.isEmpty()) {
                    int trial = extractOne(set);
                    assertFalse(iAll.reserve(trial), "Allocator agreed to reserve " + trial);
                    iAll.free(trial);
                }
            }
        }

        for (int trial : set) {
            assertFalse(iAll.reserve(trial), "Integer " + trial + " should be allocated!");
        }
    }

    @Test public void testToString() {
        IntAllocator ibs = new IntAllocator(LO_RANGE, HI_RANGE);
        assertEquals("IntAllocator{allocated = []}", ibs.toString());
        ibs.allocate();
        assertEquals("IntAllocator{allocated = [100]}", ibs.toString());
        for(int i = 200; i<211; i=i+4) {
            ibs.reserve(i);
            ibs.reserve(i+1);
            ibs.reserve(i+2);
        }
        assertEquals("IntAllocator{allocated = [100, 200..202, 204..206, 208..210]}"
            , ibs.toString());
    }

    private static int extractOne(Set<Integer> set) {
        Iterator<Integer> iter = set.iterator();
        int trial = iter.next();
        iter.remove();
        return trial;
    }

    private static int getTrial(Random rand) {
        return rand.nextInt(HI_RANGE-LO_RANGE+1) + LO_RANGE;
    }

    private static boolean getBool(Random rand) {
        return rand.nextBoolean();
    }
}
