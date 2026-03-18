package org.micromanager.internal.jacque;

final class InstantClock implements AcqClock {
   private long millis;

   InstantClock(long initialMillis) {
      this.millis = initialMillis;
   }

   InstantClock() {
      this(0);
   }

   @Override
   public long currentTimeMillis() {
      return millis;
   }

   @Override
   public long nanoTime() {
      return millis * 1_000_000L;
   }

   @Override
   public void sleep(long sleepMillis) {
      millis += sleepMillis;
   }

   void advance(long delta) {
      millis += delta;
   }
}
