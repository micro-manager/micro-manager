
package org.micromanager.projector.internal;

import ij.gui.ImageCanvas;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import mmcorej.Configuration;
import org.micromanager.Studio;
import org.micromanager.data.DataProvider;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.displaywindow.DisplayController;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.projector.ProjectionDevice;
import org.micromanager.projector.internal.devices.Galvo;

/**
 *
 * @author nico
 */
public class ProjectorControlExecution {
   
   private final Studio studio_;
   
   public ProjectorControlExecution (final Studio studio) {
      studio_ = studio;
   }
   
   
    /**
    * Attaches phototargeting ROIs to a multi-dimensional acquisition, so that
    * they will run on a particular firstFrame and, if repeat is true,
    * thereafter again every frameRepeatInterval frames.
    * @param firstFrame
    * @param repeat
    * @param frameRepeatInterval
    * @param runPolygons
   */
   public void attachRoisToMDA(int firstFrame, boolean repeat, 
           int frameRepeatInterval, Runnable runPolygons) {
      studio_.acquisitions().clearRunnables();
      if (repeat) {
         for (int i = firstFrame; 
                 i < studio_.acquisitions().getAcquisitionSettings().numFrames * 10; 
                 i += frameRepeatInterval) {
            studio_.acquisitions().attachRunnable(i, -1, 0, 0, runPolygons);
         }
      } else {
         studio_.acquisitions().attachRunnable(firstFrame, -1, 0, 0, runPolygons);
      }
   }
   
   /**
    * Sets the Channel Group to the targeting channel, if it exists.
    *
    * @param targetingChannel
    * @return the channel group in effect before calling this function
    */
   public Configuration prepareChannel(final String targetingChannel) {
      Configuration originalConfig = null;
      String channelGroup = studio_.core().getChannelGroup();
      try {
         if (targetingChannel != null && targetingChannel.length() > 0) {
            originalConfig = studio_.core().getConfigGroupState(channelGroup);
            if (studio_.core().isConfigDefined(channelGroup, targetingChannel)
                    && !originalConfig.isConfigurationIncluded(studio_.core().
                            getConfigData(channelGroup, targetingChannel))) {
               if (studio_.acquisitions().isAcquisitionRunning()) {
                  studio_.acquisitions().setPause(true);
               }
               studio_.core().setConfig(channelGroup, targetingChannel);
            }
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
      return originalConfig;
   }
   
   
    /**
    * Should be called with the value returned by prepareChannel.
    * Returns Channel Group to its original settings, if needed.
    * @param originalConfig Configuration to return to
    */
   public void returnChannel(Configuration originalConfig) {
      if (originalConfig != null) {
         try {
            studio_.core().setSystemState(originalConfig);
            if (studio_.acquisitions().isAcquisitionRunning() && studio_.acquisitions().isPaused()) {
               studio_.acquisitions().setPause(false);
            }
         } catch (Exception ex) {
            ReportingUtils.logError(ex);
         }
      }
   }
   
   /**
    * Opens the targeting shutter, if it has been specified.
    * @param targetingShutter - Shutter to be used by the projector device
    * @return true if it was already open
    */
   public boolean prepareShutter(final String targetingShutter) {
      try {
         if (targetingShutter != null && targetingShutter.length() > 0) {
            boolean originallyOpen = studio_.core().getShutterOpen(targetingShutter);
            if (!originallyOpen) {
               studio_.core().setShutterOpen(targetingShutter, true);
               studio_.core().waitForDevice(targetingShutter);
            }
            return originallyOpen;
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
      return true; // by default, say it was already open
   }

   /**
    * Closes a targeting shutter if it exists and if it was originally closed.
    * Should be called with the value returned by prepareShutter.
    * @param targetingShutter
    * @param originallyOpen - whether or not the shutter was originally open
    */
   public void returnShutter(final String targetingShutter, final boolean originallyOpen) {
      try {
         if (targetingShutter != null &&
               (targetingShutter.length() > 0) &&
               !originallyOpen) {
            studio_.core().setShutterOpen(targetingShutter, false);
            studio_.core().waitForDevice(targetingShutter);
         }
      } catch (Exception ex) {
         ReportingUtils.logError(ex);
      }
   }
   
    /**
    * Illuminate the polygons ROIs that have been previously uploaded to
    * phototargeter.
    * @param dev_
    * @param targetingChannel
    * @param targetingShutter
    */
   public void exposeRois(final ProjectionDevice dev_, final String targetingChannel, 
           final String targetingShutter) {
      if (dev_ == null) {
         return;
      }
      boolean isGalvo = dev_ instanceof Galvo;
      if (isGalvo) {
         Configuration originalConfig = prepareChannel(targetingChannel);
         boolean originalShutterState = prepareShutter(targetingShutter);
         dev_.runPolygons();
         returnShutter(targetingShutter, originalShutterState);
         returnChannel(originalConfig);
         recordPolygons();
      } else {
         dev_.runPolygons();
         recordPolygons();
      }
   }
   
      
   // Save ROIs in the acquisition path, if it exists.
   private void recordPolygons() {
      if (studio_.acquisitions().isAcquisitionRunning()) {
         // TODO: The MM2.0 refactor broke this code by removing the below
         // method.
//         String location = studio_.compat().getAcquisitionPath();
//         if (location != null) {
//            try {
//               File f = new File(location, "ProjectorROIs.zip");
//               if (!f.exists()) {
//                  saveROIs(f, individualRois_);
//               }
//            } catch (Exception ex) {
//               ReportingUtils.logError(ex);
//            }
//         }
      }
   }
   
   
   // Generates a runnable that runs the selected ROIs.
   Runnable phototargetROIsRunnable(final String runnableName, 
           final ProjectionDevice dev, final String targetingChannel, 
           final String targetingShutter) {
      return new Runnable() {
         @Override
         public void run() {
            exposeRois(dev, targetingChannel, targetingShutter);
         }
         @Override
         public String toString() {
            return runnableName;
         }
      };
   }
   
   
   
   
   /**
    * Runs runnable starting at firstTimeMs after this function is called, and
    * then, if repeat is true, every intervalTimeMs thereafter until
    * shouldContinue.call() returns false.
    */
   @SuppressWarnings("AssignmentToMethodParameter")
   Runnable runAtIntervals(final long firstTimeMs, 
           final boolean repeat,
           final long intervalTimeMs, 
           final Runnable exposeROIs, 
           final Callable<Boolean> shouldContinue) {
      
      // protect from actions that have bad consequences
      final boolean doRepeat = (intervalTimeMs == 0) ? false : repeat;
      return new Runnable() {
         @Override
         public void run() {
            ScheduledExecutorService executor
                    = Executors.newSingleThreadScheduledExecutor();
            
            Runnable periodicTask = new Runnable() {
               @Override
               public void run() {
                  try {
                  if (!shouldContinue.call()) {
                     executor.shutdown();
                  } else {
                     exposeROIs.run();
                  }
                  } catch (Exception ex) {
                     // call to Acq Engine failed
                     executor.shutdown();
                  }
               }
            };
            if (doRepeat) {
               executor.scheduleAtFixedRate(periodicTask, firstTimeMs, intervalTimeMs, TimeUnit.MILLISECONDS);
            } else {
               executor.schedule(periodicTask, firstTimeMs, TimeUnit.MILLISECONDS);
            }
         }
      };
   }
   /*
            try {
               final long startTime = System.currentTimeMillis() + firstTimeMs;
               int reps = 0;
               while (shouldContinue.call()) {
                  Utils.sleepUntil(startTime + reps * intervalTimeMs);
                  runnable.run();
                  ++reps;
                  if (!rep) {
                     break;
                  }
               }
            } catch (Exception ex) {
               ReportingUtils.logError(ex);
            }
         }
      };
   }
   */
   
   
   
   /**
    * Ugly internal stuff to see if this IJ IMageCanvas is the MM active window
    * @param canvas
    * @return 
    */   
   DataProvider getDataProvider(ImageCanvas canvas) {
      if (canvas == null) {
         return null;
      }
      List<DisplayWindow> dws = studio_.displays().getAllImageWindows();
      if (dws != null) {
         for (DisplayWindow dw : dws) {
            if (dw instanceof DisplayController) {
               if ( ((DisplayController) dw).getUIController().
                       getIJImageCanvas().equals(canvas)) {
                  return dw.getDataProvider();
               }
            }
         }
      }
      return null;
   }
   

   
}
