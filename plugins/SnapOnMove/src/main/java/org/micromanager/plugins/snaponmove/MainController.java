// Snap-on-Move Preview for Micro-Manager
//
// Author: Mark A. Tsuchida
//
// Copyright (C) 2016 Open Imaging, Inc.
//
// Redistribution and use in source and binary forms, with or without
// modification, are permitted provided that the following conditions are met:
//
// 1. Redistributions of source code must retain the above copyright notice,
// this list of conditions and the following disclaimer.
//
// 2. Redistributions in binary form must reproduce the above copyright notice,
// this list of conditions and the following disclaimer in the documentation
// and/or other materials provided with the distribution.
//
// 3. Neither the name of the copyright holder nor the names of its
// contributors may be used to endorse or promote products derived from this
// software without specific prior written permission.
//
// THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
// AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
// IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
// ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE
// LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
// CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
// SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
// INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
// CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
// ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
// POSSIBILITY OF SUCH DAMAGE.

package org.micromanager.plugins.snaponmove;

import com.google.common.eventbus.Subscribe;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import mmcorej.CMMCore;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.acquisition.AcquisitionEndedEvent;
import org.micromanager.acquisition.AcquisitionStartedEvent;
import org.micromanager.alerts.UpdatableAlert;
import org.micromanager.events.ShutdownCommencingEvent;
import org.micromanager.events.StagePositionChangedEvent;
import org.micromanager.events.XYStagePositionChangedEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.propertymap.PropertyMapJSONSerializer;

/**
 * The main control code for Snap-on-Move.
 *
 * <p>Starts and stops a thread to monitor changes (movements) and trigger
 * snaps.
 *
 * <p>Monitored items (typically stage positions) can be updated either by
 * notifications from the Core, or by polling.
 */
class MainController {
   private final Studio studio_;

   private final AtomicLong pollingIntervalMs_ = new AtomicLong();

   // Criteria for monitoring and change detection.
   // Although we frequently search for matching MonitoredItem, we just
   // use a list since the number of items is small and order preservation
   // is important.
   private final List<ChangeCriterion> changeCriteria_ = new ArrayList<>();

   // The most recent known values. For asynchronously notified items, the last
   // notified value. For polled items, the last polled value.
   // Synchronized by its own monitor, since it is accessed from notification
   // handlers.
   private final Map<MonitoredItem, MonitoredValue> latestValues_ = new HashMap<>();

   // Values at the time of the last snap, for use as a basis for detecting
   // movement. Only accessed from monitoring thread while monitoring thread
   // is running.
   private final Map<MonitoredItem, MonitoredValue> lastSnapValues_ = new HashMap<>();

   // Use string elements (stage device name) for prototype; should change to
   // objects
   private final LinkedBlockingQueue<Map.Entry<MonitoredItem, MonitoredValue>>
         eventQueue_ = new LinkedBlockingQueue<>();

   // The monitoring thread; null when stopped or paused
   private Thread monitorThread_;

   private final Object pausedLock_ = new Object();
   private boolean pausedForAcquisition_ = false;

   // Whether monitoring is "enabled" (whether running or paused)
   private boolean monitorEnabled_ = false;
   private boolean useSnap_ = true;
   private boolean useTestAcq_ = false;

   private UpdatableAlert statusAlert_;

   private static final class WarningAlertTag {
   }

   private static final String WARNING_TITLE = "Snap-on-Live Error";

   private static final String PROFILE_KEY_POLLING_INTERVAL_MS =
         "Polling interval in milliseconds";
   private static final String PROFILE_KEY_CHANGE_CRITERIA =
         "Criteria for movement detection";
   private static final String USE_SNAP = "Use snap";
   private static final String USE_TEST_ACQ = "Use test acquisition";

   MainController(Studio studio) {
      studio_ = studio;

      pollingIntervalMs_.set(studio_.profile().getSettings(this.getClass())
                  .getLong(PROFILE_KEY_POLLING_INTERVAL_MS, 100L));
      useSnap_ = studio_.profile().getSettings(this.getClass()).getBoolean(USE_SNAP, true);
      useTestAcq_ = studio_.profile().getSettings(this.getClass()).getBoolean(USE_TEST_ACQ, false);

      String criteriaJSON = studio_.profile().getSettings(this.getClass())
                  .getString(PROFILE_KEY_CHANGE_CRITERIA, null);
      if (criteriaJSON != null) {
         PropertyMap listPm = null;
         try {
            listPm = PropertyMapJSONSerializer.fromJSON(criteriaJSON);
         } catch (IOException ioe) {
            studio.logs().logError(ioe);
         }
         if (listPm != null) {
            for (int i = 0; ; ++i) {
               PropertyMap pm = listPm.getPropertyMap(Integer.toString(i), null);
               if (pm == null) {
                  break;
               }
               ChangeCriterion cc = ChangeCriterion.deserialize(pm);
               if (cc != null) {
                  changeCriteria_.add(cc);
               }
            }
         }
      }
      studio_.events().registerForEvents(this);
   }

   void setPollingIntervalMs(long intervalMs) {
      pollingIntervalMs_.set(intervalMs);

      studio_.profile().getSettings(this.getClass()).putLong(
            PROFILE_KEY_POLLING_INTERVAL_MS, intervalMs);
   }

   long getPollingIntervalMs() {
      return pollingIntervalMs_.get();
   }

   synchronized void setChangeCriteria(Collection<ChangeCriterion> criteria) {
      final boolean wasEnabled = isEnabled();
      setEnabled(false);
      changeCriteria_.clear();
      changeCriteria_.addAll(criteria);
      setEnabled(wasEnabled);

      // Each criterion is serialized into a PropertyMap. To store the list
      // in the profile, put in an outer PropertyMap with keys "0", "1", ....
      PropertyMap.Builder listPmb = PropertyMaps.builder();
      for (int i = 0; i < changeCriteria_.size(); ++i) {
         ChangeCriterion cc = changeCriteria_.get(i);
         PropertyMap.Builder pmb = PropertyMaps.builder();
         cc.serialize(pmb);
         listPmb.putPropertyMap(Integer.toString(i), pmb.build());
      }
      String json = listPmb.build().toJSON();
      studio_.profile().getSettings(this.getClass()).putString(
            PROFILE_KEY_CHANGE_CRITERIA, json);
   }

   synchronized List<ChangeCriterion> getChangeCriteria() {
      return new ArrayList<>(changeCriteria_);
   }

   synchronized void setEnabled(boolean f) {
      monitorEnabled_ = f;
      synchronized (pausedLock_) {
         if (!pausedForAcquisition_) {
            handleMonitorThread(f);
         }
      }
   }

   synchronized void useSnap() {
      useSnap_ = true;
      useTestAcq_ = false;
      storeSnapTestAcq();
   }

   synchronized void useTestAcq() {
      useSnap_ = false;
      useTestAcq_ = true;
      storeSnapTestAcq();
   }

   private void storeSnapTestAcq() {
      studio_.profile().getSettings(this.getClass()).putBoolean(USE_SNAP, useSnap_);
      studio_.profile().getSettings(this.getClass()).putBoolean(USE_TEST_ACQ, useTestAcq_);
   }

   public boolean isUsingSnap() {
      return useSnap_;
   }

   public boolean isUsingTestAcq() {
      return useTestAcq_;
   }

   private synchronized void handleMonitorThread(boolean f) {
      if (f == (monitorThread_ != null)) {
         return;
      }
      if (f) {
         monitorThread_ = new Thread("SnapOnMove Monitor Thread") {
            @Override
            public void run() {
               monitorLoop();
            }
         };
         monitorThread_.start();
      } else {
         monitorThread_.interrupt();
         try {
            monitorThread_.join(1000);
         } catch (InterruptedException notOurs) {
            Thread.currentThread().interrupt();
         }
         eventQueue_.clear();
         synchronized (latestValues_) {
            latestValues_.clear();
         }
         lastSnapValues_.clear();
         monitorThread_ = null;
      }
   }

   synchronized boolean isEnabled() {
      return monitorEnabled_;
   }

   private void doSnap() throws InterruptedException {
      if (useSnap_) {
         studio_.live().snap(true);
      } else if (useTestAcq_) {
         ((MMStudio) studio_).uiManager().getAcquisitionWindow()
                 .runTestAcquisition(studio_.acquisitions().getAcquisitionSettings());
      }
      if (statusAlert_ != null) {
         SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
         String time = sdf.format(Calendar.getInstance().getTime());
         statusAlert_.setText(
               String.format("Monitoring %d item(s); last snap at %s",
                     changeCriteria_.size(), time));
      }
   }

   private void monitorLoop() {
      try {
         initializeMonitoredValues();
         statusAlert_ = studio_.alerts().postUpdatableAlert("Snap-on-Move",
               String.format("Monitoring %d item(s)...", changeCriteria_.size()));
         for (; ; ) {
            lastSnapValues_.clear();
            synchronized (latestValues_) {
               lastSnapValues_.putAll(latestValues_);
            }

            boolean skipSnap = false;
            synchronized (pausedLock_) {
               // Skip the first snap after resuming from pause.
               skipSnap = pausedForAcquisition_;
               pausedForAcquisition_ = false;
            }
            if (!skipSnap) {
               doSnap();
            }
            waitForChange();
         }
      } catch (InterruptedException shouldExit) {
         if (statusAlert_ != null) {
            statusAlert_.dismiss();
            statusAlert_ = null;
         }
      }
   }

   /**
    * Wait for at least the polling interval, or until a change is detected.
    *
    * <p>Ensures that latestValues_ reflect just-polled values, which can be
    * considered the "current" values for an immediately following snap.
    *
    * @throws InterruptedException if current thread is interrupted
    */
   private void waitForChange() throws InterruptedException {
      if (Thread.interrupted()) {
         throw new InterruptedException();
      }
      Thread.sleep(getPollingIntervalMs());
      long pollingDeadlineMs = 0; // The first poll should happen immediately
      for (; ; ) {
         if (waitForEvents(pollingDeadlineMs)) {
            // We have detected a change based on an event.
            // Bring the polled items up to date:
            pollDevices();
            return;
         }
         // Polling deadline passed without any change detected by events.
         // See if any of the polled items have changed:
         if (pollDevices()) {
            return;
         }
         pollingDeadlineMs = System.currentTimeMillis() + getPollingIntervalMs();
      }
   }

   /**
    * Wait for queued events until a specified deadline.
    *
    * <p>Always checks the event queue at least once.
    *
    * @param deadlineMs deadline to be compared with System.currentTimeMillis().
    * @return true if an event meeting criteria was received; false if the
    *         deadline passed without such an event.
    */
   private boolean waitForEvents(long deadlineMs) throws InterruptedException {
      long remainingMs = Math.max(0, deadlineMs - System.currentTimeMillis());
      do {
         final Map<MonitoredItem, MonitoredValue> events =
               new HashMap<>();

         Map.Entry<MonitoredItem, MonitoredValue> event =
               eventQueue_.poll(remainingMs, TimeUnit.MILLISECONDS);
         if (event == null) { // Reached timeout
            return false;
         }
         // Drain the queue, keeping only the most recent event for each item
         while (event != null) {
            events.put(event.getKey(), event.getValue());
            event = eventQueue_.poll();
         }

         // Check each event to see if a change criterion is met
         for (Map.Entry<MonitoredItem, MonitoredValue> e : events.entrySet()) {
            MonitoredItem item = e.getKey();
            MonitoredValue value = e.getValue();
            if (value != null) {
               for (ChangeCriterion cc : changeCriteria_) {
                  if (cc.getMonitoredItem().equals(item)) {
                     if (cc.testForChange(lastSnapValues_.get(item), value)) {
                        return true;
                     }
                  }
               }
            }
         }

         remainingMs = deadlineMs - System.currentTimeMillis();
      } while (remainingMs > 0);
      return false;
   }

   private boolean pollDevices() throws InterruptedException {
      CMMCore core = getCore();
      if (core == null) {
         studio_.alerts().postAlert(WARNING_TITLE, WarningAlertTag.class,
               "Cannot access devices");
         return false;
      }

      boolean changeDetected = false;
      for (ChangeCriterion cc : changeCriteria_) {
         if (!cc.requiresPolling()) {
            continue;
         }

         MonitoredItem item = cc.getMonitoredItem();
         MonitoredValue value = null;
         try {
            value = item.poll(core);
         } catch (MonitoredItem.DeviceError err) {
            studio_.alerts().postAlert(WARNING_TITLE, WarningAlertTag.class,
                  "Device error: " + err.getMessage());
         }
         if (value != null) {
            synchronized (latestValues_) {
               latestValues_.put(item, value);
            }
            if (cc.testForChange(lastSnapValues_.get(item), value)) {
               changeDetected = true;
            }
         }
         if (Thread.interrupted()) {
            throw new InterruptedException();
         }
      }
      return changeDetected;
   }

   /**
    * Retrieve the initial values for all monitored items.
    */
   private void initializeMonitoredValues() {
      CMMCore core = getCore();
      if (core == null) {
         studio_.alerts().postAlert(WARNING_TITLE, WarningAlertTag.class,
               "Cannot access devices");
         return;
      }

      for (ChangeCriterion cc : changeCriteria_) {
         MonitoredItem item = cc.getMonitoredItem();
         MonitoredValue value = null;
         try {
            value = item.poll(core);
         } catch (MonitoredItem.DeviceError err) {
            studio_.alerts().postAlert(WARNING_TITLE, WarningAlertTag.class,
                  "Device error: " + err.getMessage());
         }
         if (value != null) {
            synchronized (latestValues_) {
               latestValues_.put(item, value);
            }
         }
      }
   }

   @Subscribe
   public void onZMoved(StagePositionChangedEvent e) {
      MonitoredItem item = MonitoredItem.createZItem(e.getDeviceName());
      boolean itemIsMonitored = false;
      for (ChangeCriterion cc : changeCriteria_) {
         if (cc.getMonitoredItem().equals(item)) {
            itemIsMonitored = true;
         }
      }
      if (!itemIsMonitored) {
         return;
      }

      MonitoredValue value = MonitoredValue.createFloatValue(e.getPos());
      try {
         synchronized (latestValues_) {
            latestValues_.put(item, value);
         }
         eventQueue_.put(new AbstractMap.SimpleEntry<>(item, value));
      } catch (InterruptedException unexpected) {
         Thread.currentThread().interrupt();
      }
   }

   @Subscribe
   public void onXYMoved(XYStagePositionChangedEvent e) {
      MonitoredItem item = MonitoredItem.createXYItem(e.getDeviceName());
      boolean itemIsMonitored = false;
      for (ChangeCriterion cc : changeCriteria_) {
         if (cc.getMonitoredItem().equals(item)) {
            itemIsMonitored = true;
         }
      }
      if (!itemIsMonitored) {
         return;
      }

      MonitoredValue value = MonitoredValue.createXYValue(e.getXPos(), e.getYPos());
      try {
         synchronized (latestValues_) {
            latestValues_.put(item, value);
         }
         eventQueue_.put(new AbstractMap.SimpleEntry<>(item, value));
      } catch (InterruptedException unexpected) {
         Thread.currentThread().interrupt();
      }
   }

   @Subscribe
   public void onShutdownCommencing(ShutdownCommencingEvent e) {
      if (!e.isCanceled()) {
         setEnabled(false);
      }
      studio_.events().unregisterForEvents(this);
   }

   @Subscribe
   public synchronized void onAcquisitionStarted(AcquisitionStartedEvent e) {
      handleMonitorThread(false);
      synchronized (pausedLock_) {
         pausedForAcquisition_ = true;
      }
   }

   @Subscribe
   public synchronized void onAcquisitionEnded(AcquisitionEndedEvent e) {
      if (monitorEnabled_) {
         // Leave pausedForAcquisition_ set here; it is cleared in
         // monitorLoop() after skipping the first snap after resuming.
         handleMonitorThread(true);
      }
   }

   CMMCore getCore() {
      return studio_.core();
   }
}
