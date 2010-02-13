//   The contents of this file are subject to the Mozilla Public License
//   Version 1.1 (the "License"); you may not use this file except in
//   compliance with the License. You may obtain a copy of the License at
//   http://www.mozilla.org/MPL/
//
//   Software distributed under the License is distributed on an "AS IS"
//   basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
//   License for the specific language governing rights and limitations
//   under the License.
//
//   The Original Code is RabbitMQ.
//
//   The Initial Developers of the Original Code are LShift Ltd,
//   Cohesive Financial Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created before 22-Nov-2008 00:00:00 GMT by LShift Ltd,
//   Cohesive Financial Technologies LLC, or Rabbit Technologies Ltd
//   are Copyright (C) 2007-2008 LShift Ltd, Cohesive Financial
//   Technologies LLC, and Rabbit Technologies Ltd.
//
//   Portions created by LShift Ltd are Copyright (C) 2007-2010 LShift
//   Ltd. Portions created by Cohesive Financial Technologies LLC are
//   Copyright (C) 2007-2010 Cohesive Financial Technologies
//   LLC. Portions created by Rabbit Technologies Ltd are Copyright
//   (C) 2007-2010 Rabbit Technologies Ltd.
//
//     All Rights Reserved.
//
//     Contributor(s): ______________________________________.
//
package com.rabbitmq.utility;

import java.util.Arrays;

/**
 * A class for allocating integer IDs in a given range.
 */
public class IntAllocator{

    // Invariant: Sorted in order of first element.
    // Invariant: Intervals are non-overlapping, non-adjacent.
    // This could really use being a balanced binary tree. However for normal
    // usages it doesn't actually matter.
    private IntervalList base;

    private final int[] unsorted;
    private int unsortedCount = 0;

    /**
     * A class representing an inclusive interval from start to end.
     */
    private static class IntervalList{
        IntervalList(int start, int end){
            this.start = start;
            this.end = end;
        }

        int start;
        int end;
        IntervalList next;

        int length(){ return end - start + 1; }
    }

    /** Destructively merge two IntervalLists.
     * Invariant: None of the Intervals in the two lists may overlap
     * intervals in this list.
     */
    public static IntervalList merge(IntervalList x, IntervalList y){
        if(x == null) return y;
        if(y == null) return x;

        if(x.end > y.start) return merge(y, x);

        // We now have x, y non-null and x.End < y.Start.
        if(y.start == x.end + 1){
            // The two intervals adjoin. Merge them into one and then
            // merge the tails.
            x.end = y.end;
            x.next = merge(x.next, y.next);
            return x;
        }

        // y belongs in the tail of x.

        x.next = merge(y, x.next);
        return x;
    }


    public static IntervalList fromArray(int[] xs, int length){
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
            throw new IllegalArgumentException("illegal range [" + start    +
              ", " + end + "]");

        // Fairly arbitrary heuristic for a good size for the unsorted set.
        unsorted = new int[Math.max(32, (int)Math.sqrt(end - start))];
        base = new IntervalList(start, end);
    }

    /**
     * Allocate a fresh integer from the range, or return -1 if no more integers
     * are available. This operation is guaranteed to run in O(1)
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

    public void flush(){
        if(unsortedCount == 0) return;

        base = merge(base, fromArray(unsorted, unsortedCount));
        unsortedCount = 0;
    }

    @Override public String toString(){
        StringBuilder builder = new StringBuilder();

        builder.append("IntAllocator{");

        builder.append("intervals = [");
        IntervalList it = base;
        while(it != null){
            builder.append(it.start).append("..").append(it.end);
            if(it.next != null) builder.append(", ");
            it = it.next;
        }
        builder.append("]");

        builder.append(", unsorted = [");
        for(int i = 0; i < unsortedCount; i++){
            builder.append(unsorted[i]);
            if( i < unsortedCount - 1) builder.append(", ");
        }
        builder.append("]");


        builder.append("}");
        return builder.toString();
    }
}
