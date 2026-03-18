package org.micromanager.internal.jacque;

final class SystemClock implements AcqClock {
   @Override
   public long currentTimeMillis() {
      return System.currentTimeMillis();
   }

   @Override
   public long nanoTime() {
      return System.nanoTime();
   }

   @Override
   public void sleep(long millis) throws InterruptedException {
      Thread.sleep(millis);
   }
}
