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
//  Copyright (c) 2007-2011 VMware, Inc.  All rights reserved.
//

package com.rabbitmq.utility;

import java.util.Arrays;

/**
 * A class for allocating integers from a given range.
 * <p/><strong>Concurrent Semantics:</strong><br />
 * This class is <b><i>not</i></b> thread safe.
 * 
 * <p/><b>Implementation notes:</b>
 * <br/>This could really use being a balanced binary tree. However for normal
 * usages it doesn't actually matter.
 * 
 * <p/><b>Invariants:</b>
 * <br/>Sorted in order of first element.
 * <br/>Intervals are non-overlapping, non-adjacent.
 * 
 */
public class IntAllocator {

    private IntervalList base;

    private final int[] unsorted;
    private int unsortedCount = 0;

    /**
     * A node in a singly-linked list of inclusive intervals.
     * <br/>A single node denotes the interval of integers from
     * <code>start</code> to <code>end</code> inclusive.
     */
    private static class IntervalList{
        int start;
        int end;
        IntervalList next;  // next interval in the list.
        
        IntervalList(int start, int end){
            this.start = start;
            this.end = end;
        }

        int length(){ return end - start + 1; }
    }

    private static class IntervalListAccumulator {
        /** <b>Invariant:</b> either both null, or both non-null. */
        private IntervalList accumListStart, accumListEnd = null;
        
        /**
         * Add a single node to the end of the accumulated list, merging the
         * last two nodes if abutting.<br/>
         * <b>Note:</b> the node added is not modified by add;
         * in particular, the <code>next</code> field is preserved, 
         * and copied if the nodes are merged.
         * @param iListNode node to add, terminate list if this is null.
         */
        public void add(IntervalList iListNode) {
            if (accumListStart == null) {
                accumListStart = accumListEnd = iListNode;
            } else {
                if (iListNode == null) {
                    accumListEnd.next = null;
                } else if (accumListEnd.end + 1 == iListNode.start) {
                    accumListEnd.end = iListNode.end;
                    accumListEnd.next = iListNode.next;
                } else {
                    accumListEnd.next = iListNode;
                    accumListEnd = iListNode;
                }
            }
        }
        /**
         * Append the list from this node and return the accumulated list
         * including this node chain.<br/>
         * <b>Note:</b> The accumulated list is cleared after this call.
         * @param iListNode chain to append to accumListEnd.
         * @return the accumulated list
         */
        public IntervalList getAppendedResult(IntervalList iListNode) {
            add(iListNode); // note: this preserves the chain
            IntervalList result = accumListStart;
            accumListStart = accumListEnd = null;
            return result;
        }
    }

    /**
     * Merge two IntervalLists.
     * <p/><b>Preconditions:</b><br/>
     * None of the intervals in the two lists overlap.
     */
    private static IntervalList merge(IntervalList x, IntervalList y){
        IntervalListAccumulator outList = new IntervalListAccumulator();
        while (true) {
            if(x == null) { return outList.getAppendedResult(y); }
            if(y == null) { return outList.getAppendedResult(x); }
            if (x.start < y.start) {
                outList.add(x);
                x = x.next;
            } else {
                outList.add(y);
                y = y.next;
            }
        }
    }

    private static IntervalList fromArray(int[] xs, int length){
        Arrays.sort(xs, 0, length);

        IntervalList result = null;
        IntervalList current = null;

        int i = 0;
        while(i < length){
            int start = i;
            while((i < length - 1) && (xs[i + 1] == xs[i] + 1))
                i++;

            IntervalList interval = new IntervalList(xs[start], xs[i]);

            if(result == null){
                result = interval;
                current = interval;
            } else {
                current.next = interval;
                current = interval;
            }
            i++;
        }
        return result;
    }

    /**
    * Creates an IntAllocator allocating integer IDs within the inclusive range
    * [start, end]
    */
    public IntAllocator(int start, int end){
        if(start > end)
            throw new IllegalArgumentException("illegal range [" + start +
              ", " + end + "]");

        // Fairly arbitrary heuristic for a good size for the unsorted set.
        unsorted = new int[Math.max(32, (int)Math.sqrt(end - start))];
        base = new IntervalList(start, end);
    }

    /**
     * Allocate an unallocated integer from the range, or return -1 if no 
     * more integers are available. This operation is guaranteed to run in O(1)
     */
    public int allocate(){
        if(unsortedCount > 0){
            return unsorted[--unsortedCount];
        } else if (base != null){
            IntervalList source = base;
            if(base.length() == 1) base = base.next;
            return source.start++;
        } else {
            return -1;
        }
    }

    /**
     * Make the provided integer available for allocation again. This operation
     * runs in amortized O(sqrt(range size)) time: About every sqrt(range size)
     * operations will take O(range_size + number of intervals) to complete and
     * the rest run in constant time.
     *
     * No error checking is performed, so if you double free or free an integer
     * that was not originally allocated the results are undefined. Sorry.
     */
    public void free(int id){
        if(unsortedCount >= unsorted.length){
            flush();
        }
        unsorted[unsortedCount++] = id;
    }

    /**
     * Attempt to reserve the provided ID as if it had been allocated. Returns
     * true if it is available, false otherwise.
     *
     * This operation runs in O(id) in the worst case scenario, though it can
     * usually be expected to perform better than that unless a great deal of
     * fragmentation has occurred.
     */
    public boolean reserve(int id){
        flush();

        IntervalList current = base;

        while(current != null && current.end < id){
            current = current.next;
        }

        if(current == null) return false;
        if(current.start > id) return false;

        if(current.end == id)
                current.end--;
        else if(current.start == id)
            current.start++;
        else {
            // The ID is in the middle of this interval.
            // We need to split the interval into two.
            IntervalList rest = new IntervalList(id + 1, current.end);
            current.end = id - 1;
            rest.next = current.next;
            current.next = rest;
        }

        return true;
    }

    private void flush(){
        if(unsortedCount == 0) return;

        base = merge(base, fromArray(unsorted, unsortedCount));
        unsortedCount = 0;
    }

    @Override public String toString(){
        StringBuilder builder = new StringBuilder();

        builder.append("IntAllocator{intervals = [");
        IntervalList it = base;
        if (it != null) {
            builder.append(it.start).append("..").append(it.end);
            it = it.next;
        }
        while (it != null) {
            builder.append(", ").append(it.start).append("..").append(it.end);
            it = it.next;
        }
        builder.append("], unsorted = [");
        if (unsortedCount > 0) {
            builder.append(unsorted[0]);
        }
        for(int i = 1; i < unsortedCount; ++i){
            builder.append(", ").append(unsorted[i]);
        }
        builder.append("]}");
        return builder.toString();
    }
}
