// Copyright (C) 2017 Open Imaging, Inc.
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

package org.micromanager.internal.utils;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A {@code ThreadFactory} that names the threads for debugging.
 * <p>
 * The threads created by the factory are set to be daemon threads. This is
 * preferable in MMStudio because we do not submit any tasks that need to
 * complete after the main program has decided to exit.
 *
 * @author Mark A. Tsuchida
 */
public final class ThreadFactoryFactory {
   private ThreadFactoryFactory() {}

   public static ThreadFactory createThreadFactory(final String poolName) {
      return new Factory(poolName);
   }

   public static ThreadFactory createThreadFactory(final String poolName, int priority) {
      return new Factory(poolName, priority);
   }

   private static final class Factory implements ThreadFactory {
      private final AtomicLong next_ = new AtomicLong(0);
      private final String name_;
      private int priority_ = 4; // default priority

      Factory(String poolName) {
         name_ = poolName;
      }

      Factory(String poolName, int priority) {
         name_ = poolName;
         priority_ = priority;
      }

      private String nextTitle() {
         long number = next_.getAndIncrement();
         return String.format("%s Pool Thread %d", name_, number);
      }

      @Override
      public Thread newThread(Runnable r) {
         Thread ret = new Thread(r, nextTitle());
         ret.setPriority(priority_);
         ret.setDaemon(true);
         return ret;
      }
   }
}
