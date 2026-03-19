package org.micromanager.internal.jacque;

public interface AcqClock {
   long currentTimeMillis();
   long nanoTime();
   void sleep(long millis) throws InterruptedException;
}
