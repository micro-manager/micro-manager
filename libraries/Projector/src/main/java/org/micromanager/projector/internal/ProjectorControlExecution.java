package org.micromanager.projector.internal;

import com.google.common.eventbus.Subscribe;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.Roi;
import ij.io.RoiEncoder;
import java.awt.Rectangle;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import mmcorej.Configuration;
import org.micromanager.Studio;
import org.micromanager.acquisition.AcquisitionEndedEvent;
import org.micromanager.acquisition.AcquisitionStartedEvent;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.displaywindow.DisplayController;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.projector.ProjectionDevice;
import org.micromanager.projector.internal.devices.Galvo;


/**
 * Abstraction of the Projector hardware calls.
 *
 * @author nico
 */
public class ProjectorControlExecution {

   private final Studio studio_;

   private Datastore store_;

   public ProjectorControlExecution(final Studio studio) {
      studio_ = studio;
   }


   /**
    * Attaches phototargeting ROIs to a multi-dimensional acquisition, so that they will run on a
    * particular firstFrame and, if repeat is true, thereafter again every frameRepeatInterval
    * frames.
    *
    * @param firstFrame first frame after which the phototargeting ROIS will execute
    * @param repeat    whether to repeat the phototargeting ROIs
    * @param frameRepeatInterval interval between repeated phototargeting ROIs
    * @param runPolygons Runnable that executes the ROIS
    */
   public void attachRoisToMDA(int firstFrame, boolean repeat,
         int frameRepeatInterval, Runnable runPolygons) {
      studio_.acquisitions().clearRunnables();
      if (repeat) {
         for (int i = firstFrame;
               i < studio_.acquisitions().getAcquisitionSettings().numFrames() * 10;
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
    * @param targetingChannel Channel in the channel group to use for targeting
    * @return the channel group setting in effect before calling this function
    */
   public String prepareChannel(final String targetingChannel) {
      String originalConfig = null;
      String channelGroup = studio_.core().getChannelGroup();
      try {
         if (targetingChannel != null && !targetingChannel.isEmpty()) {
            originalConfig = studio_.core().getCurrentConfig(channelGroup);
            if (studio_.core().isConfigDefined(channelGroup, targetingChannel)) {
               if (studio_.acquisitions().isAcquisitionRunning()) {
                  studio_.acquisitions().setPause(true);
               }
               studio_.core().setConfig(channelGroup, targetingChannel);
            }
         }
      } catch (Exception ex) {
         studio_.logs().logError(ex);
      }
      return originalConfig;
   }


   /**
    * Should be called with the value returned by prepareChannel. Returns Channel Group to its
    * original settings, if needed.
    *
    * @param originalConfig Configuration to return to
    */
   public void returnChannel(String originalConfig) {
      if (originalConfig != null) {
         try {
            studio_.core().setConfig(studio_.core().getChannelGroup(), originalConfig);
            if (studio_.acquisitions().isAcquisitionRunning() && studio_.acquisitions()
                  .isPaused()) {
               studio_.acquisitions().setPause(false);
            }
         } catch (Exception ex) {
            studio_.logs().logError(ex);
         }
      }
   }

   /**
    * Opens the targeting shutter, if it has been specified.
    *
    * @param targetingShutter - Shutter to be used by the projector device
    * @return true if it was already open
    */
   public boolean prepareShutter(final String targetingShutter) {
      try {
         if (targetingShutter != null && !targetingShutter.isEmpty()) {
            boolean originallyOpen = studio_.core().getShutterOpen(targetingShutter);
            if (!originallyOpen) {
               studio_.core().setShutterOpen(targetingShutter, true);
               studio_.core().waitForDevice(targetingShutter);
            }
            return originallyOpen;
         }
      } catch (Exception ex) {
         studio_.logs().logError(ex);
      }
      return true; // by default, say it was already open
   }

   /**
    * Closes a targeting shutter if it exists and if it was originally closed. Should be called with
    * the value returned by prepareShutter.
    *
    * @param targetingShutter Shutter to use with phototargeting (can be null)
    * @param originallyOpen   - whether the shutter was originally open
    */
   public void returnShutter(final String targetingShutter, final boolean originallyOpen) {
      try {
         if (targetingShutter != null
               && (!targetingShutter.isEmpty())
               && !originallyOpen) {
            studio_.core().setShutterOpen(targetingShutter, false);
            studio_.core().waitForDevice(targetingShutter);
         }
      } catch (Exception ex) {
         studio_.logs().logError(ex);
      }
   }

   /**
    * Illuminate the polygons ROIs that have been previously uploaded to phototargeter.
    *
    * @param dev phototargetting device (Galvo or SLM)
    * @param targetingChannel Channel to use for targeting (can be null)
    * @param targetingShutter Shutter to use with phototargeting (can be null)
    * @param rois             Rois as they appear on the camera.  Only used to records with the
    *                         images
    */
   public void exposeRois(final ProjectionDevice dev, final String targetingChannel,
         final String targetingShutter, Roi[] rois) {
      if (dev == null) {
         return;
      }
      boolean isGalvo = dev instanceof Galvo;
      final String originalConfig = prepareChannel(targetingChannel);
      boolean originalShutterState = prepareShutter(targetingShutter);
      dev.runPolygons();
      if (isGalvo) {
         dev.waitForDevice();
      } else { // SLM: TODO evaluate if this is needed or waitForDevice can be used.
         try {
            Thread.sleep(dev.getExposure() / 1000);
         } catch (InterruptedException ex) {
            studio_.logs().logError(ex);
         }
      }
      returnShutter(targetingShutter, originalShutterState);
      returnChannel(originalConfig);

      recordPolygons(rois);
   }

   @Subscribe
   public void onAcquisitionStart(AcquisitionStartedEvent ae) {
      store_ = ae.getDatastore();
   }


   @Subscribe
   public void onAcquisitionEnd(AcquisitionEndedEvent ae) {
      store_ = null;
   }


   // Save ROIs in the acquisition path, if it exists.
   private void recordPolygons(Roi[] individualRois) {
      if (studio_.acquisitions().isAcquisitionRunning()) {
         if (studio_.acquisitions().getAcquisitionSettings().save()) {
            String location = store_ == null ? null : store_.getSavePath();
            if (location != null) {
               try {
                  File f = new File(location, "ProjectorROIs.zip");
                  if (!f.exists()) {
                     saveROIs(f, individualRois);
                  }
               } catch (Exception ex) {
                  studio_.logs().logError(ex);
               }
            }
         }
      }
   }

   /**
    * Save a list of ROIs to a given path.
    */
   private static void saveROIs(File path, Roi[] rois) {
      try {
         ImagePlus imgp = IJ.getImage();
         ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(path));
         try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(zos))) {
            RoiEncoder re = new RoiEncoder(out);
            for (Roi roi : rois) {
               String label = getROILabel(imgp, roi, 0);
               if (!label.endsWith(".roi")) {
                  label += ".roi";
               }
               zos.putNextEntry(new ZipEntry(label));
               re.write(roi);
               out.flush();
            }
         }
      } catch (IOException e) {
         ReportingUtils.logError(e);
      }
   }

   /**
    * Gets the label of an ROI with the given index n. Borrowed from ImageJ.
    */
   private static String getROILabel(ImagePlus imp, Roi roi, int n) {
      Rectangle r = roi.getBounds();
      int xc = r.x + r.width / 2;
      int yc = r.y + r.height / 2;
      if (n >= 0) {
         xc = yc;
         yc = n;
      }
      if (xc < 0) {
         xc = 0;
      }
      if (yc < 0) {
         yc = 0;
      }
      int digits = 4;
      String xs = "" + xc;
      if (xs.length() > digits) {
         digits = xs.length();
      }
      String ys = "" + yc;
      if (ys.length() > digits) {
         digits = ys.length();
      }
      if (digits == 4 && imp != null && imp.getStackSize() >= 10000) {
         digits = 5;
      }
      xs = "000000" + xc;
      ys = "000000" + yc;
      String label = ys.substring(ys.length() - digits) + "-" + xs.substring(xs.length() - digits);
      if (imp != null && imp.getStackSize() > 1) {
         int slice = roi.getPosition();
         if (slice == 0) {
            slice = imp.getCurrentSlice();
         }
         String zs = "000000" + slice;
         label = zs.substring(zs.length() - digits) + "-" + label;
         roi.setPosition(slice);
      }
      return label;
   }


   // Generates a runnable that runs the selected ROIs.
   Runnable phototargetROIsRunnable(final String runnableName,
         final ProjectionDevice dev, final String targetingChannel,
         final String targetingShutter, Roi[] rois) {
      return new Runnable() {
         @Override
         public void run() {
            exposeRois(dev, targetingChannel, targetingShutter, rois);
         }

         @Override
         public String toString() {
            return runnableName;
         }
      };
   }


   /**
    * Runs runnable starting at firstTimeMs after this function is called, and then, if repeat is
    * true, every intervalTimeMs thereafter until shouldContinue.call() returns false.
    */
   Runnable runAtIntervals(final long firstTimeMs,
         final boolean repeat,
         final long intervalTimeMs,
         final Runnable exposeROIs,
         final Callable<Boolean> shouldContinue) {

      // protect from actions that have bad consequences
      final boolean doRepeat = (intervalTimeMs == 0) ? false : repeat;
      return () -> {
         ScheduledExecutorService executor
               = Executors.newSingleThreadScheduledExecutor();
         Runnable periodicTask = () -> {
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
         };
         if (doRepeat) {
            executor.scheduleAtFixedRate(periodicTask, firstTimeMs, intervalTimeMs,
                  TimeUnit.MILLISECONDS);
         } else {
            executor.schedule(periodicTask, firstTimeMs, TimeUnit.MILLISECONDS);
         }
      };
   }


   /**
    * Ugly internal stuff to see if this IJ ImageCanvas is the MM active window.
    *
    * @param canvas IMageJ ImageCanvas
    * @return DataProvider for the active window
    */
   DataProvider getDataProvider(ImageCanvas canvas) {
      if (canvas == null) {
         return null;
      }
      List<DisplayWindow> dws = studio_.displays().getAllImageWindows();
      if (dws != null) {
         for (DisplayWindow dw : dws) {
            if (dw instanceof DisplayController) {
               if (((DisplayController) dw).getUIController()
                           .getIJImageCanvas().equals(canvas)) {
                  return dw.getDataProvider();
               }
            }
         }
      }
      return null;
   }


}
