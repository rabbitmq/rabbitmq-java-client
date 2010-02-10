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
//   All Rights Reserved.
//
//   Contributor(s): ______________________________________.
//
package com.rabbitmq.utility;

import java.util.*;

/**
 * A class for allocating integer IDs in a given range. 
 */ 
public class IntAllocator{

  // Invariant: Sorted in order of first element. Non-overlapping, non-adjacent.
  // This could really use being a balanced binary tree. However for normal usages 
  // it doesn't actually matter.
  private LinkedList<Interval> intervals = new LinkedList<Interval>();

  private final int[] unsorted;
  private int unsortedCount = 0;

  /** 
   * A class representing an inclusive interval from start to end.
   */
  private static class Interval{
    Interval(int start, int end){
      this.start = start;
      this.end = end;
    }

    int start;
    int end;

    int length(){ return end - start + 1; } 
  }

  /** 
   * Creates an IntAllocator allocating integer IDs within the inclusive range [start, end]
   */
  public IntAllocator(int start, int end){
    if(start > end) throw new IllegalArgumentException("illegal range [" + start  +", " + end + "]");

    // Fairly arbitrary heuristic for a good size for the unsorted set.
    unsorted = new int[Math.max(32, (int)Math.sqrt(end - start))];
    intervals.add(new Interval(start, end));
  }

  /**
   * Allocate a fresh integer from the range, or return -1 if no more integers
   * are available. This operation is guaranteed to run in O(1) 
   */
  public int allocate(){
    if(unsortedCount > 0){
      return unsorted[--unsortedCount];
    } else if (!intervals.isEmpty()) {
      Interval first = intervals.getFirst();
      if(first.length() == 1) intervals.removeFirst();
      return first.start++; 
    } else {
      return -1;
    }
  }

  /** 
   * Make the provided integer available for allocation again. This operation 
   * runs in amortized O(sqrt(range size)) time: About every sqrt(range size) 
   * operations  will take O(range_size + number of intervals) to complete and 
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
   * Attempt to reserve the provided ID as if it had been allocated. Returns true 
   * if it is available, false otherwise. 
   *
   * This operation runs in O(id) in the worst case scenario, though it can usually
   * be expected to perform better than that unless a great deal of fragmentation
   * has occurred. 
   */
  public boolean reserve(int id){
    flush();
    ListIterator<Interval> it = intervals.listIterator();

    while(it.hasNext()){
      Interval i = it.next();
      if(i.start <= id && id <= i.end){
        if(i.length() == 1) it.remove();
        else if(i.start == id) i.start++;
        else if(i.end == id) i.end--;
        else {
          it.add(new Interval(id + 1, i.end));
          i.end = id - 1;
        }
        return true;
      }
    }

    return false;
  }

  private void flush(){
    if(unsortedCount == 0) return;
  
    Arrays.sort(unsorted);
   
    ListIterator<Interval> it = intervals.listIterator();

    int i = 0;
    while(i < unsortedCount){
      int start = i;
      while((i < unsortedCount - 1) && (unsorted[i + 1] == unsorted[i] + 1))
        i++;

      Interval interval = new Interval(start, i);

      // Scan to an appropriate point in the list to insert this interval
      // this may well be the end
      while(it.hasNext()){
        if(it.next().start > interval.end){
          it.previous();
          break;
        }
      }
  
      it.add(interval);
      i++;
    }

    normalize();
    unsortedCount = 0; 
  }

  private void normalize(){
    if(intervals.isEmpty()) return;
    Iterator<Interval> it = intervals.iterator();

    Interval trailing, leading;
    leading = it.next();
    while(it.hasNext()){
      trailing = leading;
      leading = it.next();

      if(leading.start == trailing.end + 1) {
        it.remove(); 
        trailing.end = leading.end;
      }
    } 
  }

  @Override public String toString(){
    StringBuilder builder = new StringBuilder();

    builder.append("IntAllocator{");

    builder.append("intervals = [");
    Iterator<Interval> it = intervals.iterator();
    while(it.hasNext()){
      Interval i = it.next();
      builder.append(i.start).append("..").append(i.end);
      if(it.hasNext()) builder.append(", ");     
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
