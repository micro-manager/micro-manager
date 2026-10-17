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
 *
 * <p>Choose daemon threads for background work and non-daemon threads for work
 * that must complete before normal JVM termination. Executors using non-daemon
 * threads must be shut down when their work is finished. Neither choice prevents
 * explicit JVM termination with {@code System.exit}.
 *
 * @author Mark A. Tsuchida
 */
public final class ThreadFactoryFactory {
   private ThreadFactoryFactory() {
   }

   /**
    * Create a factory for background threads.
    *
    * @deprecated Use {@link #createDaemonThreadFactory(String)} explicitly.
    */
   @Deprecated
   public static ThreadFactory createThreadFactory(final String poolName) {
      return createDaemonThreadFactory(poolName);
   }

   public static ThreadFactory createDaemonThreadFactory(final String poolName) {
      return new Factory(poolName, true);
   }

   public static ThreadFactory createNonDaemonThreadFactory(final String poolName) {
      return new Factory(poolName, false);
   }

   private static final class Factory implements ThreadFactory {
      private final AtomicLong next_ = new AtomicLong(0);
      private final String name_;

      private final boolean daemon_;

      Factory(String poolName, boolean daemon) {
         name_ = poolName;
         daemon_ = daemon;
      }

      private String nextTitle() {
         long number = next_.getAndIncrement();
         return String.format("%s Pool Thread %d", name_, number);
      }

      @Override
      public Thread newThread(Runnable r) {
         Thread ret = new Thread(r, nextTitle());
         ret.setDaemon(daemon_);
         return ret;
      }
   }
}
