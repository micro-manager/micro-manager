/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.profile.internal;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.Subscribe;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author Mark A. Tsuchida
 */
final class ProfileSaver {
   // Saver is created upon the first modification made to the profile
   private final ScheduledExecutorService saver_;
   private ScheduledFuture<?> scheduledSave_;
   private final ReentrantLock writeLock_ = new ReentrantLock();
   private boolean stopped_;

   private long saveIntervalSeconds_ = 30;

   private final Runnable save_;

   public static ProfileSaver create(DefaultUserProfile profile,
                                     Runnable save, ScheduledExecutorService saverExecutor) {
      ProfileSaver instance = new ProfileSaver(save, saverExecutor);
      profile.registerForEvents(instance);
      return instance;
   }

   ProfileSaver(Runnable save, ScheduledExecutorService saverExecutor) {
      save_ = save;
      saver_ = saverExecutor;
   }

   public void setSaveIntervalSeconds(long seconds) {
      Preconditions.checkArgument(seconds > 0);
      saveIntervalSeconds_ = seconds;
   }

   public long getSaveIntervalSeconds() {
      return saveIntervalSeconds_;
   }

   public void syncToDisk() {
      writeLock_.lock();
      try {
         synchronized (this) {
            if (stopped_ || scheduledSave_ == null) {
               return;
            }
         }
         save_.run();
      } finally {
         writeLock_.unlock();
      }
   }

   private void saveScheduled() {
      writeLock_.lock();
      try {
         synchronized (this) {
            if (stopped_) {
               return;
            }
         }
         save_.run();
      } finally {
         writeLock_.unlock();
      }
   }

   @Subscribe
   public void onEvent(UserProfileChangedEvent e) {
      scheduleSave();
   }

   private synchronized void scheduleSave() {
      if (stopped_) {
         return;
      }
      if (scheduledSave_ != null) {
         scheduledSave_.cancel(false);
      }
      try {
         scheduledSave_ = saver_.schedule(this::saveScheduled,
               saveIntervalSeconds_, TimeUnit.SECONDS);
      } catch (RejectedExecutionException e) {
         // Saving has been shut down; nothing to do
      }
   }

   public void stop() throws InterruptedException {
      writeLock_.lockInterruptibly();
      try {
         boolean saveNeeded;
         synchronized (this) {
            if (stopped_) {
               return;
            }
            stopped_ = true;
            saveNeeded = scheduledSave_ != null;
            if (scheduledSave_ != null) {
               scheduledSave_.cancel(false);
               scheduledSave_ = null;
            }
         }
         // Do not hold this monitor while reading the profile: profile changes
         // notify scheduleSave while holding the profile's own monitor.
         if (saveNeeded) {
            save_.run();
         }
      } finally {
         writeLock_.unlock();
      }
   }
}
