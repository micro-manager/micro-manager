/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.api;

import org.micromanager.acquisition.SequenceSettings;

/**
 *
 * @author arthur
 */
public interface Pipeline {
   public String run(SequenceSettings settings, AcquisitionEngine eng);
   public void pause();
   public void resume();
   public void stop();
   public boolean isRunning();
   public boolean isPaused();
   public boolean isFinished();
   public boolean stopHasBeenRequested();
   public long nextWakeTime();
   public void acquireSingle();
   public void attachRunnable(int frame, int position,
                              int slice, int channel, Runnable runnable);
   public void clearRunnables();
   public ImageCache getImageCache();
}
