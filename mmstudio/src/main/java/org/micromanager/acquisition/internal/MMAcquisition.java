///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, November 2010
//
// COPYRIGHT:    University of California, San Francisco, 2010
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.acquisition.internal;

import com.google.common.eventbus.Subscribe;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.Studio;
import org.micromanager.alerts.UpdatableAlert;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.NewImageEvent;
import org.micromanager.data.Pipeline;
import org.micromanager.data.PipelineErrorException;
import org.micromanager.data.Storage;
import org.micromanager.data.internal.CommentsHelper;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.data.internal.StorageRAM;
import org.micromanager.data.internal.StorageSinglePlaneTiffSeries;
import org.micromanager.data.internal.multipagetiff.StorageMultipageTiff;
import org.micromanager.display.ControlsFactory;
import org.micromanager.display.DisplayDestroyedEvent;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.RequestToCloseEvent;
import org.micromanager.events.AcquisitionEndedEvent;
import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class is used to execute most of the acquisition and image display
 * functionality in the ScriptInterface
 */
public final class MMAcquisition {
   
   /** 
    * Final queue of images immediately prior to insertion into the ImageCache.
    * Only used when running in asynchronous mode.
    */
   private BlockingQueue<TaggedImage> outputQueue_ = null;
   private boolean isAsynchronous_ = false;
   private int numFrames_ = 0;
   private int numChannels_ = 0;
   private int numSlices_ = 0;
   private int numPositions_ = 0;
   protected String name_;
   protected int width_ = 0;
   protected int height_ = 0;
   protected int byteDepth_ = 1;
   protected int bitDepth_ = 8;    
   protected int multiCamNumCh_ = 1;
   private boolean initialized_ = false;
   private final String comment_ = "";
   private String rootDirectory_;
   private Studio studio_;
   private DefaultDatastore store_;
   private Pipeline pipeline_;
   private DisplayWindow display_;
   private final boolean virtual_;
   private AcquisitionEngine eng_;
   private final boolean show_;
   private JSONObject summary_ = new JSONObject();
   private final String NOTINITIALIZED = "Acquisition was not initialized";

   private int imagesReceived_ = 0;
   private int imagesExpected_ = 0;
   private UpdatableAlert alert_;

   public MMAcquisition(Studio studio, String name, JSONObject summaryMetadata,
         boolean diskCached, AcquisitionEngine eng, boolean show) {
      studio_ = studio;
      name_ = name;
      virtual_ = diskCached;
      eng_ = eng;
      show_ = show;
      store_ = new DefaultDatastore();
      pipeline_ = studio_.data().copyApplicationPipeline(store_, false);
      try {
         if (summaryMetadata.has("Directory") && summaryMetadata.get("Directory").toString().length() > 0) {
            // Set up saving to the target directory.
            try {
               String acqDirectory = createAcqDirectory(summaryMetadata.getString("Directory"), summaryMetadata.getString("Prefix"));
               summaryMetadata.put("Prefix", acqDirectory);
               String acqPath = summaryMetadata.getString("Directory") + File.separator + acqDirectory;
               store_.setStorage(getAppropriateStorage(store_, acqPath, true));
            } catch (Exception e) {
               ReportingUtils.showError(e, "Unable to create directory for saving images.");
               eng.stop(true);
            }
         } else {
            store_.setStorage(new StorageRAM(store_));
         }
      }
      catch (JSONException e) {
         ReportingUtils.logError(e, "Couldn't adjust summary metadata.");
      }

      // Transfer any summary comment from the acquisition engine.
      if (summaryMetadata != null && MDUtils.hasComments(summaryMetadata)) {
         try {
            CommentsHelper.setSummaryComment(store_,
                  MDUtils.getComments(summaryMetadata));
         }
         catch (JSONException e) {
            ReportingUtils.logError(e, "Unable to set summary comment");
         }
      }

      try {
         pipeline_.insertSummaryMetadata(DefaultSummaryMetadata.legacyFromJSON(summaryMetadata));
      }
      catch (DatastoreFrozenException e) {
         ReportingUtils.logError(e, "Datastore is frozen; can't set summary metadata");
      }
      catch (DatastoreRewriteException e) {
         ReportingUtils.logError(e, "Summary metadata has already been set");
      }
      catch (PipelineErrorException e) {
         ReportingUtils.logError(e, "Can't insert summary metadata: processing already started.");
      }
      // Calculate expected images from dimensionality in summary metadata.
      if (store_.getSummaryMetadata().getIntendedDimensions() != null) {
         Coords dims = store_.getSummaryMetadata().getIntendedDimensions();
         imagesExpected_ = 1;
         for (String axis : dims.getAxes()) {
            imagesExpected_ *= dims.getIndex(axis);
         }
         setProgressText();
      }
      if (show_) {
         display_ = studio_.displays().createDisplay(
               store_, makeControlsFactory());
         display_.registerForEvents(this);
         alert_ = studio_.alerts().postUpdatableAlert("Acquisition Progress", "");
         setProgressText();
      }
      store_.registerForEvents(this);
      DefaultEventManager.getInstance().registerForEvents(this);
  }

   private String createAcqDirectory(String root, String prefix) throws Exception {
      File rootDir = JavaUtils.createDirectory(root);
      int curIndex = getCurrentMaxDirIndex(rootDir, prefix + "_");
      return prefix + "_" + (1 + curIndex);
   }

   private int getCurrentMaxDirIndex(File rootDir, String prefix) throws NumberFormatException {
      int maxNumber = 0;
      int number;
      String theName;
      for (File acqDir : rootDir.listFiles()) {
         theName = acqDir.getName();
         if (theName.startsWith(prefix)) {
            try {
               //e.g.: "blah_32.ome.tiff"
               Pattern p = Pattern.compile("\\Q" + prefix + "\\E" + "(\\d+).*+");
               Matcher m = p.matcher(theName);
               if (m.matches()) {
                  number = Integer.parseInt(m.group(1));
                  if (number >= maxNumber) {
                     maxNumber = number;
                  }
               }
            } catch (NumberFormatException e) {
            } // Do nothing.
         }
      }
      return maxNumber;
   }

   /**
    * A simple little subclass of JButton that listens for certain events.
    * It listens for AcquisitionEndedEvent and disables itself when that
    * event occurs; it also listens for DisplayDestroyedEvent and unregisters
    * itself from event buses at that time.
    */
   private static class SubscribedButton extends JButton {
      /**
       * Create a SubscribedButton and subscribe it to the relevant event
       * buses.
       */
      public static SubscribedButton makeButton(Studio studio,
            ImageIcon icon, DisplayWindow display) {
         SubscribedButton result = new SubscribedButton(studio, icon);
         DefaultEventManager.getInstance().registerForEvents(result);
         display.registerForEvents(result);
         return result;
      }

      private Studio studio_;

      public SubscribedButton(Studio studio, ImageIcon icon) {
         super(icon);
         studio_ = studio;
      }

      @Subscribe
      public void onDisplayDestroyed(DisplayDestroyedEvent e) {
         DefaultEventManager.getInstance().unregisterForEvents(this);
         e.getDisplay().unregisterForEvents(this);
      }

      @Subscribe
      public void onAcquisitionEnded(AcquisitionEndedEvent e) {
         if (studio_.acquisitions().isOurAcquisition(e.getSource())) {
            setEnabled(false);
         }
      }
   }

   /**
    * Generate the abort and pause buttons. These are only used for display
    * windows for ongoing acquisitions (i.e. not for opening files from
    * disk).
    * TODO: remove these special controls (or at least hide them) when the
    * acquisition ends.
    */
   private ControlsFactory makeControlsFactory() {
      return new ControlsFactory() {
         @Override
         public List<Component> makeControls(final DisplayWindow display) {
            ArrayList<Component> result = new ArrayList<Component>();
            JButton abortButton = SubscribedButton.makeButton(studio_,
                  new ImageIcon(
                     getClass().getResource("/org/micromanager/icons/cancel.png")),
                  display);
            abortButton.setBackground(new Color(255, 255, 255));
            abortButton.setToolTipText("Halt data acquisition");
            abortButton.setFocusable(false);
            abortButton.setMaximumSize(new Dimension(30, 28));
            abortButton.setMinimumSize(new Dimension(30, 28));
            abortButton.setPreferredSize(new Dimension(30, 28));
            abortButton.addActionListener(new ActionListener() {
               @Override
               public void actionPerformed(ActionEvent e) {
                  eng_.abortRequest();
               }
            });
            result.add(abortButton);

            final ImageIcon pauseIcon = new ImageIcon(getClass().getResource(
                  "/org/micromanager/icons/control_pause.png"));
            final ImageIcon playIcon = new ImageIcon(getClass().getResource(
                  "/org/micromanager/icons/resultset_next.png"));
            final JButton pauseButton = SubscribedButton.makeButton(
                  studio_, pauseIcon, display);
            pauseButton.setToolTipText("Pause data acquisition");
            pauseButton.setFocusable(false);
            pauseButton.setMaximumSize(new Dimension(30, 28));
            pauseButton.setMinimumSize(new Dimension(30, 28));
            pauseButton.setPreferredSize(new Dimension(30, 28));
            pauseButton.addActionListener(new ActionListener() {
               @Override
               public void actionPerformed(ActionEvent e) {
                  eng_.setPause(!eng_.isPaused());
                  // Switch the icon depending on if the acquisition is paused.
                  Icon icon = pauseButton.getIcon();
                  if (icon == pauseIcon) {
                     pauseButton.setIcon(playIcon);
                  }
                  else {
                     pauseButton.setIcon(pauseIcon);
                  }
               }
            });
            result.add(pauseButton);

            return result;
         }
      };
   }

   @Subscribe
   public void onRequestToClose(RequestToCloseEvent event) {
      // Prompt to stop the acquisition if it's still running. Only if our
      // display is the display for the current active acquisition.
      if (eng_.getAcquisitionDatastore() == store_) {
         if (!eng_.abortRequest()) {
            // User cancelled abort.
            return;
         }
      }
      if (store_.getSavePath() != null ||
            studio_.displays().promptToSave(store_, display_)) {
         // Datastore is saved, or user declined to save.
         display_.forceClosed();
         store_.close();
      }
   }

   @Subscribe
   public void onDisplayDestroyed(DisplayDestroyedEvent event) {
      display_.unregisterForEvents(this);
      display_ = null;
   }

   @Subscribe
   public void onAcquisitionEnded(AcquisitionEndedEvent event) {
      store_.freeze();
      DefaultEventManager.getInstance().unregisterForEvents(this);
      new Thread(new Runnable() {
         @Override
         public void run() {
            try {
               Thread.sleep(5000);
            }
            catch (InterruptedException e) {
               // This should never happen.
               studio_.logs().logError("Interrupted while waiting to dismiss alert");
            }
            alert_.dismiss();
         }
      }).start();
   }

   @Subscribe
   public void onNewImage(NewImageEvent event) {
      imagesReceived_++;
      setProgressText();
   }

   private void setProgressText() {
      if (imagesExpected_ > 0) {
         int numDigits = (int) (Math.log10(imagesExpected_) + 1);
         String format = "%0" + numDigits + "d";
         if (alert_ != null) {
            alert_.setText(String.format(
                    "Received " + format + " of %d images",
                    imagesReceived_, imagesExpected_));
         }
      } else if (alert_ != null) {
         alert_.setText("No images expected.");
      }
   }

   private static Storage getAppropriateStorage(DefaultDatastore store,
         String path, boolean isNew) throws IOException {
      Datastore.SaveMode mode = DefaultDatastore.getPreferredSaveMode();
      if (mode == Datastore.SaveMode.SINGLEPLANE_TIFF_SERIES) {
         return new StorageSinglePlaneTiffSeries(store, path, isNew);
      }
      else if (mode == Datastore.SaveMode.MULTIPAGE_TIFF) {
         return new StorageMultipageTiff(store, path, isNew);
      }
      else {
         ReportingUtils.logError("Unrecognized save mode " + mode);
         return null;
      }
   }

   public Datastore getDatastore() {
      return store_;
   }

   public Pipeline getPipeline() {
      return pipeline_;
   }
}
