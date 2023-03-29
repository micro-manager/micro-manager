package org.micromanager.acquisition.internal;

import org.micromanager.data.DataProvider;

/**
 * Acquisiton Engine-agnostic interface for pausing, aborting, 
 * getting Datastore, etc
 */
public interface MMAcquistionControlCallbacks {
   void stop(boolean b);

   boolean isAcquisitionRunning();

   double getFrameIntervalMs();

   long getNextWakeTime();

   boolean isPaused();

   void setPause(boolean b);

   boolean abortRequest();

   DataProvider getAcquisitionDatastore();
}
