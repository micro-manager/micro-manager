/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.profile.internal;

import com.google.common.base.Preconditions;
import com.google.common.eventbus.Subscribe;
import java.beans.ExceptionListener;
import java.io.IOException;
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

   @FunctionalInterface
   interface SaveTask {
      void save() throws IOException;
   }

   private final SaveTask save_;
   private final ExceptionListener errorHandler_;

   public static ProfileSaver create(DefaultUserProfile profile,
                                     SaveTask save, ExceptionListener errorHandler,
                                     ScheduledExecutorService saverExecutor) {
      ProfileSaver instance = new ProfileSaver(save, errorHandler, saverExecutor);
      profile.registerForEvents(instance);
      return instance;
   }

   ProfileSaver(SaveTask save, ExceptionListener errorHandler,
                ScheduledExecutorService saverExecutor) {
      save_ = save;
      errorHandler_ = errorHandler;
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
      IOException error = null;
      writeLock_.lock();
      try {
         synchronized (this) {
            if (stopped_ || scheduledSave_ == null) {
               return;
            }
         }
         error = write();
      } finally {
         writeLock_.unlock();
      }
      reportError(error);
   }

   private IOException write() {
      try {
         save_.save();
         return null;
      } catch (IOException e) {
         return e;
      }
   }

   private void reportError(IOException error) {
      // A listener can switch profiles or close a profile. Calling it while
      // holding writeLock_ would deadlock a concurrent switch waiting in stop().
      if (error != null && errorHandler_ != null) {
         errorHandler_.exceptionThrown(error);
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
         scheduledSave_ = saver_.schedule(this::syncToDisk,
               saveIntervalSeconds_, TimeUnit.SECONDS);
      } catch (RejectedExecutionException e) {
         // Saving has been shut down; nothing to do
      }
   }

   public void stop() throws InterruptedException {
      IOException error = null;
      // As with a synchronous file write, an interrupt must not skip the final
      // save. ReentrantLock preserves the caller's interrupt status.
      writeLock_.lock();
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
            }
         }
         try {
            // Profile changes notify scheduleSave while holding the profile's
            // monitor. Do not hold this monitor when taking a profile snapshot.
            if (saveNeeded) {
               error = write();
            }
         } catch (RuntimeException | Error e) {
            synchronized (this) {
               // Preserve the modification marker so a caller can retry close.
               stopped_ = false;
            }
            throw e;
         }
         synchronized (this) {
            scheduledSave_ = null;
         }
      } finally {
         writeLock_.unlock();
      }
      reportError(error);
   }
}
