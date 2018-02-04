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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.Studio;
import org.micromanager.alerts.UpdatableAlert;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Pipeline;
import org.micromanager.data.PipelineErrorException;
import org.micromanager.data.Storage;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.CommentsHelper;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import org.micromanager.data.internal.StorageRAM;
import org.micromanager.data.internal.StorageSinglePlaneTiffSeries;
import org.micromanager.data.internal.multipagetiff.StorageMultipageTiff;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DataViewerListener;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.events.AcquisitionEndedEvent;
import org.micromanager.events.internal.DefaultEventManager;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.MDUtils;
import org.micromanager.internal.utils.ReportingUtils;
import org.micromanager.display.DisplayWindowControlsFactory;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;
import org.micromanager.data.DataProviderHasNewImageEvent;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.internal.DefaultDisplaySettings;

/**
 * This class is used to execute most of the acquisition and image display
 * functionality in the ScriptInterface
 */
public final class MMAcquisition extends DataViewerListener {
   
   /** 
    * Final queue of images immediately prior to insertion into the ImageCache.
    * Only used when running in asynchronous mode.
    */

   protected int width_ = 0;
   protected int height_ = 0;
   protected int byteDepth_ = 1;
   protected int bitDepth_ = 8;    
   protected int multiCamNumCh_ = 1;
   private Studio studio_;
   private DefaultDatastore store_;
   private Pipeline pipeline_;
   private DisplayWindow display_;
   private AcquisitionEngine eng_;
   private final boolean show_;

   private int imagesReceived_ = 0;
   private int imagesExpected_ = 0;
   private UpdatableAlert alert_;

   public MMAcquisition(Studio studio, JSONObject summaryMetadata,
         AcquisitionEngine eng, boolean show) {
      studio_ = studio;
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
         catch (IOException e) {
            // TODO XXX Report
         }
      }

      try {
         // Compatibility hack: serialize to JSON, then parse as summary metadata JSON format
         SummaryMetadata summary = DefaultSummaryMetadata.fromPropertyMap(
                 NonPropertyMapJSONFormats.summaryMetadata().fromJSON(
                         summaryMetadata.toString()));
         pipeline_.insertSummaryMetadata(summary);
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
      catch (IOException e) {
         throw new RuntimeException("Failed to parse summary metadata", e);
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
         studio_.displays().manage(store_);
         display_ = studio_.displays().createDisplay(
               store_, makeControlsFactory());
         display_.addListener(this, 1);
         //display_.registerForEvents(this);
         
         // Color handling is a problem. They are no longer part of the summary 
         // metadata.  However, they clearly need to be stored 
         // with the dataset itself.  I guess that it makes sense to store them in 
         // the display setting.  However, it then becomes essential that 
         // display settings are stored with the (meta-)data.  
         // Handling the conversion from colors in the summary metadata to display
         // settings here seems clumsy, but I am not sure where else this belongs
         
         try {
            if (summaryMetadata != null && summaryMetadata.has("ChColors")) {

               JSONArray chColors = summaryMetadata.getJSONArray("ChColors");
               
               // Use settings of last closed acquisition viewer
               DisplaySettings dsTmp = DefaultDisplaySettings.restoreFromProfile(
                  studio_.profile(), PropertyKey.ACQUISITION_DISPLAY_SETTINGS.key() );
               
               if (dsTmp == null) {
                  dsTmp = DefaultDisplaySettings.getStandardSettings(
                          PropertyKey.ACQUISITION_DISPLAY_SETTINGS.key());
               }
      
               DisplaySettings.Builder displaySettingsBuilder
                       = dsTmp.copyBuilder();
               
               final int nrChannels = MDUtils.getNumChannels(summaryMetadata);
               // the do-while loop is a way to set display settings in a thread
               // safe way.  See docs to compareAndSetDisplaySettings.
               do {
                  if (nrChannels == 1) {
                     displaySettingsBuilder.colorModeGrayscale();
                  } else {
                     displaySettingsBuilder.colorModeComposite();
                  }
                  for (int channelIndex = 0; channelIndex < nrChannels; channelIndex++) {
                     ChannelDisplaySettings channelSettings
                             = displaySettingsBuilder.getChannelSettings(channelIndex);
                     Color chColor = new Color(chColors.getInt(channelIndex));
                     displaySettingsBuilder.channel(channelIndex,
                             channelSettings.copyBuilder().color(chColor).build());
                  }
               } while (!display_.compareAndSetDisplaySettings(
                       display_.getDisplaySettings(), displaySettingsBuilder.build()));
            }
         } catch (JSONException je) {
            studio_.logs().logError(je);
            // relatively harmless, but look here when display settings are unexpected
         }

         
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

   @Override
   public boolean canCloseViewer(DataViewer viewer) {
      if (!viewer.equals(display_)) {
         ReportingUtils.logError("MMAcquisition: received callback from unknown viewer");
         return true;
      }
      boolean result = eng_.abortRequest();
      if (result) {
         if (viewer instanceof DisplayWindow && viewer.equals(display_)) {
            if (display_.getDisplaySettings() instanceof DefaultDisplaySettings) {
               ((DefaultDisplaySettings) display_.getDisplaySettings()).saveToProfile(
                       studio_.profile(), PropertyKey.ACQUISITION_DISPLAY_SETTINGS.key());
            }
            display_.removeListener(this);
         }
      }
      return result;
   }


   /**
    * A simple little subclass of JButton that listens for certain events.
    * It listens for AcquisitionEndedEvent and disables itself when that
    * event occurs; it also listens for DisplayDestroyedEvent and unregisters
    * itself from event buses at that time.
    */
   private static class SubscribedButton extends JButton {

      private static final long serialVersionUID = -4447256100740272458L;
      /**
       * Create a SubscribedButton and subscribe it to the relevant event
       * buses.
       */
      public static SubscribedButton makeButton(final Studio studio,
            final ImageIcon icon, final DisplayWindow display) {
         SubscribedButton result = new SubscribedButton(studio, icon);
         DefaultEventManager.getInstance().registerForEvents(result);
         display.registerForEvents(result);
         return result;
      }

      private final Studio studio_;

      public SubscribedButton(Studio studio, ImageIcon icon) {
         super(icon);
         studio_ = studio;
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
   private DisplayWindowControlsFactory makeControlsFactory() {
      return new DisplayWindowControlsFactory() {
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
   public void onAcquisitionEnded(AcquisitionEndedEvent event) {
      try {
         store_.freeze();
      }
      catch (IOException e) {
         ReportingUtils.logError(e);
      }
      if (display_ .getDisplaySettings() instanceof DefaultDisplaySettings) {
         if (store_.getSavePath() != null) {
            ( (DefaultDisplaySettings) display_.getDisplaySettings() ).
                    save(store_.getSavePath());
         }
         // save display settings to profile
         ((DefaultDisplaySettings) display_.getDisplaySettings()).saveToProfile(
               studio_.profile(), PropertyKey.ACQUISITION_DISPLAY_SETTINGS.key());
      }
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
   public void onNewImage(DataProviderHasNewImageEvent event) {
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

   private static Storage getAppropriateStorage(final DefaultDatastore store,
           final String path, final boolean isNew) throws IOException {
      Datastore.SaveMode mode = DefaultDatastore.getPreferredSaveMode();
      if (null != mode) {
         switch (mode) {
            case SINGLEPLANE_TIFF_SERIES:
               return new StorageSinglePlaneTiffSeries(store, path, isNew);
            case MULTIPAGE_TIFF:
               return new StorageMultipageTiff(store, path, isNew);
         }
      }
      ReportingUtils.logError("Unrecognized save mode " + mode);
      return null;
   }

   public Datastore getDatastore() {
      return store_;
   }

   public Pipeline getPipeline() {
      return pipeline_;
   }
}
