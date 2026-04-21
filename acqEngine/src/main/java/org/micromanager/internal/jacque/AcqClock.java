package org.micromanager.internal.jacque;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public interface AcqClock {
   long currentTimeMillis();
   long nanoTime();
   void sleep(long millis) throws InterruptedException;
   default void interruptibleSleep(long millis, CountDownLatch latch)
         throws InterruptedException {
      latch.await(millis, TimeUnit.MILLISECONDS);
   }
}
