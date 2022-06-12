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

/**
 * @author Mark A. Tsuchida
 */
final class ProfileSaver {
   // Saver is created upon the first modification made to the profile
   private final ScheduledExecutorService saver_;
   private ScheduledFuture<?> scheduledSave_;

   private long saveIntervalSeconds_ = 30;

   private final Runnable save_;

   public static ProfileSaver create(DefaultUserProfile profile,
                                     Runnable save, ScheduledExecutorService saverExecutor) {
      ProfileSaver instance = new ProfileSaver(save, saverExecutor);
      profile.registerForEvents(instance);
      return instance;
   }

   private ProfileSaver(Runnable save, ScheduledExecutorService saverExecutor) {
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

   public synchronized void syncToDisk() {
      if (scheduledSave_ == null) {
         // Save not scheduled, i.e. profile hasn't been modified.
         return;
      }
      save_.run();
   }

   @Subscribe
   public void onEvent(UserProfileChangedEvent e) {
      scheduleSave();
   }

   private synchronized void scheduleSave() {
      if (scheduledSave_ != null) {
         scheduledSave_.cancel(false);
      }
      try {
         scheduledSave_ = saver_.schedule(save_,
               saveIntervalSeconds_, TimeUnit.SECONDS);
      } catch (RejectedExecutionException e) {
         // Saving has been shut down; nothing to do
      }
   }

   public synchronized void stop() throws InterruptedException {
      syncToDisk();
      if (scheduledSave_ != null) {
         scheduledSave_.cancel(false);
         scheduledSave_ = null;
      }
   }
}