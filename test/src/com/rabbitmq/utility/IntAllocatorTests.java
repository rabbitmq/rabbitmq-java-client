//  The contents of this file are subject to the Mozilla Public License
//  Version 1.1 (the "License"); you may not use this file except in
//  compliance with the License. You may obtain a copy of the License
//  at http://www.mozilla.org/MPL/
//
//  Software distributed under the License is distributed on an "AS IS"
//  basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
//  the License for the specific language governing rights and
//  limitations under the License.
//
//  The Original Code is RabbitMQ.
//
//  The Initial Developer of the Original Code is VMware, Inc.
//  Copyright (c) 2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.utility;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

import junit.framework.TestCase;

public class IntAllocatorTests extends TestCase {

    private static final int TEST_ITERATIONS = 50000;
    private static final int HI_RANGE = 100000;
    private static final int LO_RANGE = 100;
    private IntAllocator intAllocator = new IntAllocator(LO_RANGE, HI_RANGE);

    private Random rand = new Random();

    public void testReserveAndFree() throws Exception {
        Set<Integer> set = new HashSet<Integer>();
        for (int i = 0; i < TEST_ITERATIONS; ++i) {
            int trial = getTrial();
            if (set.contains(trial)) {
                intAllocator.free(trial);
                set.remove(trial);
            } else {
                assertTrue("Did not reserve free integer " + trial,
                        intAllocator.reserve(trial));
                set.add(trial);
            }
        }

        for (int trial : set) {
            assertFalse("Integer " + trial + " not allocated!",
                    intAllocator.reserve(trial));
        }
    }

    public void testAllocateAndFree() throws Exception {
        Set<Integer> set = new HashSet<Integer>();
        for (int i=0; i < TEST_ITERATIONS; ++i) {
            if (getBool()) {
                int trial = intAllocator.allocate();
                assertFalse("Already allocated " + trial, set.contains(trial));
                set.add(trial);
            } else {
                if (!set.isEmpty()) {
                    int trial = extract(set);
                    assertFalse("Allocator agreed to reserve " + trial,
                            intAllocator.reserve(trial));
                    intAllocator.free(trial);
                }
            }
        }

        for (int trial : set) {
            assertFalse("Integer " + trial + " should be allocated!",
                    intAllocator.reserve(trial));
        }
    }

    private static int extract(Set<Integer> set) {
        Iterator<Integer> iter = set.iterator();
        int trial = iter.next();
        iter.remove();
        return trial;
    }
    
    private int getTrial() {
        return rand.nextInt(HI_RANGE-LO_RANGE+1) + LO_RANGE;
    }
    
    private boolean getBool() {
        return rand.nextBoolean();
    }
}
