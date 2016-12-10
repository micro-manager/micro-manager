// Copyright (C) 2016-7 Open Imaging, Inc.
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.display.internal.imagestats;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A queue that retains lower-priority elements only if newer than the
 * highest-priority element.
 *
 * Upon retrieval, the highest-priority element available is obtained first.
 *
 * For use cases where higher-priority items should be processed first, but
 * the newest item should eventually be reached when quiescent even if its
 * priority is low. New, high-priority items will eject older equal- or lower-
 * priority items, but new, low-priority items will stay in the queue behind
 * older, higher-priority items.
 *
 * @param <E> the element type
 *
 * @author Mark A. Tsuchida
 */
public final class BoundedOverridingPriorityQueue<
      E extends BoundedPriorityElement>
{
   private final Lock lock_ = new ReentrantLock();
   private final Condition notEmpty_ = lock_.newCondition();
   private final Condition highestPriorityUnoccupied_ = lock_.newCondition();

   // Indexed by priority; null in empty slots.
   private final List<E> elements_ = new ArrayList<E>();

   public static <F extends BoundedPriorityElement>
      BoundedOverridingPriorityQueue<F> create()
   {
      return new BoundedOverridingPriorityQueue<F>();
   }

   private BoundedOverridingPriorityQueue() {
   }

   public void clear() {
      lock_.lock();
      try {
         Collections.fill(elements_, null);
         highestPriorityUnoccupied_.signal();
      }
      finally {
         lock_.unlock();
      }
   }

   public void submit(E e) {
      lock_.lock();
      try {
         int priority = e.getPriority();
         while (priority >= elements_.size()) {
            elements_.add(null);
         }
         elements_.set(priority, e);
         for (int i = priority - 1; i >= 0; --i) {
            elements_.set(i, null);
         }
         notEmpty_.signal();
      }
      finally {
         lock_.unlock();
      }
   }

   public void submitWaitingToReplaceHighestEverPriority(E e)
         throws InterruptedException
   {
      lock_.lockInterruptibly();
      try {
         int priority = e.getPriority();
         while (priority == elements_.size() - 1 &&
               elements_.get(priority) != null)
         {
            highestPriorityUnoccupied_.await();
         }
         while (priority >= elements_.size()) {
            elements_.add(null);
         }
         elements_.set(priority, e);
         for (int i = priority - 1; i >= 0; --i) {
            elements_.set(i, null);
         }
         notEmpty_.signal();
      }
      finally {
         lock_.unlock();
      }
   }

   public void await() throws InterruptedException {
      lock_.lock();
      try {
         for (;;) {
            for (E e : elements_) {
               if (e != null) {
                  return;
               }
            }
            notEmpty_.await();
         }
      }
      finally {
         lock_.unlock();
      }
   }

   public E retrieveIfAvailable() {
      lock_.lock();
      try {
         E e = takeHighestPriorityElement();
         highestPriorityUnoccupied_.signal();
         return e;
      }
      finally {
         lock_.unlock();
      }
   }

   public E waitAndRetrieve() throws InterruptedException {
      lock_.lockInterruptibly();
      try {
         E e = takeHighestPriorityElement();
         while (e == null) {
            notEmpty_.await();
            e = takeHighestPriorityElement();
         }
         highestPriorityUnoccupied_.signal();
         return e;
      }
      finally {
         lock_.unlock();
      }
   }

   public List<E> drain() {
      lock_.lock();
      try {
         List<E> elements = new ArrayList<E>();
         for (E e : elements_) {
            if (e != null) {
               elements.add(e);
            }
         }
         return elements;
      }
      finally {
         lock_.unlock();
      }
   }

   private E takeHighestPriorityElement() {
      for (int i = elements_.size() - 1; i >= 0; --i) {
         E e = elements_.get(i);
         if (e != null) {
            elements_.set(i, null);
            return e;
         }
      }
      return null;
   }
}
