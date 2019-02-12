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

package com.rabbitmq.utility;

import java.util.BitSet;

/**
 * <p>
 *  A class for allocating integers from a given range that uses a
 * {@link BitSet} representation of the free integers.
 * </p>
 *
 * <h2>Concurrency Semantics:</h2>
 * This class is <b><i>not</i></b> thread safe.
 *
 * <h2>Implementation notes:</h2>
 * <p>This was originally an ordered chain of non-overlapping Intervals,
 * together with a fixed size array cache for freed integers.
 * </p>
 * <p>
 * {@link #reserve(int)} was expensive in this scheme, whereas in the
 * present implementation it is O(1), as is {@link #free(int)}.
 * </p>
 * <p>Although {@link #allocate()} is slightly slower than O(1) and in the
 * worst case could be O(N), the use of a "<code>lastIndex</code>" field
 * for starting the next scan for free integers means this is negligible.
 * </p>
 * <p>The data representation overhead is O(N) where N is the size of the
 * allocation range. One <code>long</code> is used for every 64 integers in the
 * range.
 * </p>
 * <p>Very little Object creation and destruction occurs in use.</p>
 */
public class IntAllocator {

    private final int loRange; // the integer bit 0 represents
    private final int hiRange; // one more than the integer the highest bit represents
    private final int numberOfBits; // relevant in freeSet
    private int lastIndex = 0; // for searching for FREE integers
    /** A bit is SET in freeSet if the corresponding integer is FREE
     * <br/>A bit is UNSET in freeSet if the corresponding integer is ALLOCATED
     */
    private final BitSet freeSet;

    /**
     * Creates an IntAllocator allocating integer IDs within the
     * inclusive range [<code>bottom</code>, <code>top</code>].
     * @param bottom lower end of range
     * @param top upper end of range (inclusive)
     */
    public IntAllocator(int bottom, int top) {
        this.loRange = bottom;
        this.hiRange = top + 1;
        this.numberOfBits = hiRange - loRange;
        this.freeSet = new BitSet(this.numberOfBits);
        this.freeSet.set(0, this.numberOfBits); // All integers FREE initially
    }

    /**
     * Allocate an unallocated integer from the range, or return -1 if no
     * more integers are available.
     * @return the allocated integer, or -1
     */
    public int allocate() {
        int setIndex = this.freeSet.nextSetBit(this.lastIndex);
        if (setIndex<0) { // means none found in trailing part
            setIndex = this.freeSet.nextSetBit(0);
        }
        if (setIndex<0) return -1;
        this.lastIndex = setIndex;
        this.freeSet.clear(setIndex);
        return setIndex + this.loRange;
    }

    /**
     * Make the provided integer available for allocation again. This operation
     * runs in O(1) time.
     * No error checking is performed, so if you double free or free an
     * integer that was not originally allocated the results are undefined.
     * @param reservation the previously allocated integer to free
     */
    public void free(int reservation) {
        this.freeSet.set(reservation - this.loRange);
    }

    /**
     * Attempt to reserve the provided ID as if it had been allocated. Returns
     * true if it is available, false otherwise.
     * This operation runs in O(1) time.
     * @param reservation the integer to be allocated, if possible
     * @return <code><b>true</b></code> if allocated, <code><b>false</b></code>
     * if already allocated
     */
    public boolean reserve(int reservation) {
        int index = reservation - this.loRange;
        if (this.freeSet.get(index)) { // FREE
            this.freeSet.clear(index);
            return true;
        } else {
            return false;
        }
    }

    @Override
    public String toString() {
        StringBuilder sb
            = new StringBuilder("IntAllocator{allocated = [");

        int firstClearBit = this.freeSet.nextClearBit(0);
        if (firstClearBit < this.numberOfBits) {
            int firstSetAfterThat = this.freeSet.nextSetBit(firstClearBit+1);
            if (firstSetAfterThat < 0)
                firstSetAfterThat = this.numberOfBits;

            stringInterval(sb, firstClearBit, firstSetAfterThat);
            for (int i = this.freeSet.nextClearBit(firstSetAfterThat+1);
                     i < this.numberOfBits;
                     i = this.freeSet.nextClearBit(i+1)) {
                int nextSet = this.freeSet.nextSetBit(i);
                if (nextSet<0) nextSet = this.numberOfBits;
                stringInterval(sb.append(", "), i, nextSet);
                i = nextSet;
            }
        }
        sb.append("]}");
        return sb.toString();
    }
    private void stringInterval(StringBuilder sb, int i1, int i2) {
        sb.append(i1 + this.loRange);
        if (i1+1 != i2) {
            sb.append("..").append(i2-1 + this.loRange);
        }
    }
}
