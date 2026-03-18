package org.micromanager.internal.jacque;

interface AcqClock {
   long currentTimeMillis();
   long nanoTime();
   void sleep(long millis) throws InterruptedException;
}
