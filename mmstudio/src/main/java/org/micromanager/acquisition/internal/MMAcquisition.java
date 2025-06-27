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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.Timer;
import mmcorej.org.json.JSONObject;
import org.micromanager.Studio;
import org.micromanager.acquisition.AcquisitionEndedEvent;
import org.micromanager.acquisition.SequenceSettings;
import org.micromanager.alerts.UpdatableAlert;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProviderHasNewImageEvent;
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
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.data.internal.StorageRAM;
import org.micromanager.data.internal.StorageSinglePlaneTiffSeries;
import org.micromanager.data.internal.multipagetiff.StorageMultipageTiff;
import org.micromanager.data.internal.ndtiff.NDTiffAdapter;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DataViewerListener;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplaySettingsChangedEvent;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.DisplayWindowControlsFactory;
import org.micromanager.display.internal.DefaultDisplaySettings;
import org.micromanager.display.internal.RememberedDisplaySettings;
import org.micromanager.internal.propertymap.NonPropertyMapJSONFormats;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class is used to execute most of the acquisition and image display
 * functionality in the ScriptInterface.
 */
public final class MMAcquisition extends DataViewerListener {

   /**
    * Final queue of images immediately prior to insertion into the ImageCache.
    * Only used when running in asynchronous mode.
    */

   private final Studio studio_;
   private final DefaultDatastore store_;
   private final Pipeline pipeline_;
   private DisplayWindow display_;
   private final MMAcquistionControlCallbacks callbacks_;
   private final boolean show_;
   private int imagesReceived_ = 0;
   private int imagesExpected_ = 0;
   private UpdatableAlert alert_;
   private UpdatableAlert nextImageAlert_;
   private Timer nextFrameAlertGenerator_;

   /**
    * MMAcquisition is the glue between acquisition setting, acquisition engine, and
    * resulting datastore.
    *
    * @param studio          Micro-Manager Studio object.
    * @param dir             Directory to save images to.
    * @param prefix          Prefix for image names.
    * @param summaryMetadata SummaryMetadata in JSON format`
    * @param callbacks       acquisition engine object or other object that implements callbacks.
    * @param acqSettings     Settings to be saved in SummaryMetadata, and to determine whether
    *                        viewer should be shown.
    * @deprecated Use the constructor that takes a SummaryMetadata object instead.
    */
   @Deprecated
   @SuppressWarnings("LeakingThisInConstructor")
   public MMAcquisition(Studio studio, String dir, String prefix, JSONObject summaryMetadata,
                        MMAcquistionControlCallbacks callbacks, SequenceSettings acqSettings)
            throws IOException {
      this(studio, DefaultSummaryMetadata.fromPropertyMap(
               NonPropertyMapJSONFormats.summaryMetadata().fromJSON(
                        summaryMetadata.toString())),
               callbacks,
               acqSettings.copyBuilder().root(dir).prefix(prefix).build());
   }

   /**
    * Preferred constructor.  MMAcquisition is the glue between acquisition setting,
    * acquisition engine, and resulting datastore.
    *
    * @param studio          Micro-Manager Studio object.
    * @param summaryMetadata Complete SummaryMetadata
    * @param callbacks       acquisition engine object or other object that implements callbacks.
    * @param acquisitionSettings Defines the acquisition.
    */
   public MMAcquisition(Studio studio, SummaryMetadata summaryMetadata,
                        MMAcquistionControlCallbacks callbacks,
                        SequenceSettings acquisitionSettings) {
      studio_ = studio;
      callbacks_ = callbacks;
      show_ = acquisitionSettings.shouldDisplayImages();
      store_ = new DefaultDatastore(studio);
      pipeline_ = studio_.data().copyApplicationPipeline(store_, false);
      if (acquisitionSettings.save() && acquisitionSettings.root() != null) {
         // Set up saving to the target directory.
         try {
            String acqDirectory = createAcqDirectory(acquisitionSettings.root(),
                     acquisitionSettings.prefix());
            summaryMetadata =  summaryMetadata.copyBuilder().prefix(acqDirectory).build();
            String acqPath = acquisitionSettings.root() + File.separator + acqDirectory;
            store_.setStorage(getAppropriateStorage(studio_, store_, acqPath, true));
         } catch (Exception e) {
            ReportingUtils.showError(e, "Unable to create directory for saving images.");
            callbacks_.stop(true);
            return;
         }
      } else {
         store_.setStorage(new StorageRAM(store_));
      }

      // Transfer any summary comment from the acquisition engine.
      if (acquisitionSettings.comment() != null) {
         try {
            CommentsHelper.setSummaryComment(store_, acquisitionSettings.comment());
         } catch (IOException e) {
            ReportingUtils.logError(e, "IOException in MMAcquisition");
         }
      }

      try {
         // Calculate expected images from dimensionality in summary metadata.
         Coords dims = summaryMetadata.getIntendedDimensions();
         imagesExpected_ = 1;
         for (String axis : dims.getAxes()) {
            imagesExpected_ *= dims.getIndex(axis);
         }
         pipeline_.insertSummaryMetadata(summaryMetadata);

      } catch (DatastoreFrozenException e) {
         ReportingUtils.logError(e, "Datastore is frozen; can't set summary metadata");
      } catch (DatastoreRewriteException e) {
         ReportingUtils.logError(e, "Summary metadata has already been set");
      } catch (PipelineErrorException e) {
         ReportingUtils.logError(e, "Can't insert summary metadata: processing already started.");
      } catch (IOException e) {
         throw new RuntimeException("Failed to parse summary metadata", e);
      }

      if (show_) {
         studio_.displays().manage(store_);

         // Color handling is a problem. They are no longer part of the summary
         // metadata.  However, they clearly need to be stored
         // with the dataset itself.  I guess that it makes sense to store them in
         // the display setting.  However, it then becomes essential that
         // display settings are stored with the (meta-)data.
         // Handling the conversion from colors in the summary metadata to display
         // settings here seems clumsy, but I am not sure where else this belongs

         // Use settings of last closed acquisition viewer
         DisplaySettings ds = DisplaySettings.restoreFromProfile(
                  studio_.profile(), PropertyKey.ACQUISITION_DISPLAY_SETTINGS.key());

         if (ds == null) {
            ds = DefaultDisplaySettings.getStandardSettings(
                     PropertyKey.ACQUISITION_DISPLAY_SETTINGS.key());
         }

         final int nrChannels = store_.getSummaryMetadata().getChannelNameList().size();
         DisplaySettings.Builder displaySettingsBuilder = ds.copyBuilder();
         if (nrChannels > 0) { // I believe this will always be true, but just in case...
            if (nrChannels == 1) {
               displaySettingsBuilder.colorModeGrayscale();
            } else {
               displaySettingsBuilder.colorModeComposite();
            }
            for (int channelIndex = 0; channelIndex < nrChannels; channelIndex++) {
               displaySettingsBuilder.channel(channelIndex,
                        RememberedDisplaySettings.loadChannel(studio_,
                                 store_.getSummaryMetadata().getChannelGroup(),
                                 store_.getSummaryMetadata().getChannelNameList().get(channelIndex),
                                 channelIndex < acquisitionSettings.channels().size()
                                          ? acquisitionSettings.channels().get(channelIndex).color()
                                          : null));
            }
         }

         displaySettingsBuilder.windowPositionKey(DisplaySettings.MDA_DISPLAY);
         display_ = studio_.displays().createDisplay(store_,
                  makeControlsFactory(),
                  displaySettingsBuilder.build());

         // It is a bit funny that there are listeners and events
         // The listener provides the canClose functionality (which needs to be
         // synchronous), whereas Events are asynchronous
         display_.addListener(this, 1);
         display_.registerForEvents(this);

         alert_ = studio_.alerts().postUpdatableAlert("Acquisition Progress", "");
         setProgressText();

      }
      store_.registerForEvents(this);
      studio_.events().registerForEvents(this);

      // start thread reporting when next frame will be taken
      if (callbacks.getFrameIntervalMs() > 5000) {
         nextFrameAlertGenerator_ = new Timer(1000, (ActionEvent e) -> {
            if (callbacks.isAcquisitionRunning()) {
               setNextImageAlert(callbacks);
            }
         });
         nextFrameAlertGenerator_.setInitialDelay(3000);
         nextFrameAlertGenerator_.start();
      }
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
      File[] rootDirFiles = rootDir.listFiles();
      if (rootDirFiles != null) {
         for (File acqDir : rootDirFiles) {
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
                  studio_.logs().logError(e);
               }
            }
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
      // NS 20241128: I do not understand the logic here.
      // If this is not out Datastore or viewer, why deal with it?
      if (callbacks_.getAcquisitionDatastore() != viewer.getDataProvider()) {
         studio_.logs()
               .logError("MMAcquisition: received canCloseViewer with unknown store");
         display_.removeListener(this);
         display_ = null;
         return true;
      }
      boolean result = callbacks_.abortRequest();
      if (result) {
         if (viewer instanceof DisplayWindow && viewer.equals(display_)) {
            display_.removeListener(this);
            display_ = null;
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
      private final Studio studio_;

      /**
       * Create a SubscribedButton and subscribe it to the relevant event
       * buses.
       */
      public static SubscribedButton makeButton(final Studio studio,
                                                final ImageIcon icon) {
         SubscribedButton result = new SubscribedButton(studio, icon);
         studio.events().registerForEvents(result);
         return result;
      }

      public SubscribedButton(Studio studio, ImageIcon icon) {
         super(icon);
         studio_ = studio;
      }

      @Subscribe
      public void onAcquisitionEnded(AcquisitionEndedEvent e) {
         if (studio_.acquisitions().isOurAcquisition(e.getSource())) {
            setEnabled(false);
            studio_.events().unregisterForEvents(this);
            this.removeAll();
            for (ActionListener al : this.getActionListeners()) {
               this.removeActionListener(al);
            }
         }
      }
   }

   /**
    * Generate the abort and pause buttons. These are only used for display
    * windows for ongoing acquisitions (i.e. not for opening files from
    * disk).
    */
   private DisplayWindowControlsFactory makeControlsFactory() {
      return (final DisplayWindow display) -> {
         JButton abortButton = SubscribedButton.makeButton(studio_,
               new ImageIcon(
                     getClass().getResource("/org/micromanager/icons/cancel.png")));
         abortButton.setBackground(new Color(255, 255, 255));
         abortButton.setToolTipText("Halt data acquisition");
         abortButton.setFocusable(false);
         abortButton.setMaximumSize(new Dimension(30, 28));
         abortButton.setMinimumSize(new Dimension(30, 28));
         abortButton.setPreferredSize(new Dimension(30, 28));
         abortButton.addActionListener((ActionEvent e) -> {
            callbacks_.abortRequest();
         });
         ArrayList<Component> result = new ArrayList<>();
         result.add(abortButton);

         final ImageIcon pauseIcon = new ImageIcon(getClass().getResource(
               "/org/micromanager/icons/control_pause.png"));
         final ImageIcon playIcon = new ImageIcon(getClass().getResource(
               "/org/micromanager/icons/resultset_next.png"));
         final JButton pauseButton = SubscribedButton.makeButton(studio_, pauseIcon);
         pauseButton.setToolTipText("Pause data acquisition");
         pauseButton.setFocusable(false);
         pauseButton.setMaximumSize(new Dimension(30, 28));
         pauseButton.setMinimumSize(new Dimension(30, 28));
         pauseButton.setPreferredSize(new Dimension(30, 28));
         pauseButton.addActionListener((ActionEvent e) -> {
            callbacks_.setPause(!callbacks_.isPaused());
            // Switch the icon depending on if the acquisition is paused.
            Icon icon = pauseButton.getIcon();
            if (icon == pauseIcon) {
               pauseButton.setIcon(playIcon);
            } else {
               pauseButton.setIcon(pauseIcon);
            }
         });
         result.add(pauseButton);

         return result;
      };
   }

   /**
    * Deal with end of acquisition.
    *
    * @param event signal that Acquisition ended.
    */
   @Subscribe
   public void onAcquisitionEnded(AcquisitionEndedEvent event) {
      if (nextFrameAlertGenerator_ != null) {
         nextFrameAlertGenerator_.stop();
         if (nextImageAlert_ != null) {
            nextImageAlert_.dismiss();
         }
      }

      try {
         store_.freeze();
      } catch (IOException e) {
         ReportingUtils.logError(e);
      }
      if (show_ && display_ != null) {
         if (display_.getDisplaySettings() instanceof DefaultDisplaySettings) {
            if (store_.getSavePath() != null) {
               ((DefaultDisplaySettings) display_.getDisplaySettings())
                     .save(store_.getSavePath());
            }
         }
         display_.unregisterForEvents(this);
      }
      store_.unregisterForEvents(this);
      studio_.events().unregisterForEvents(this);
      new Thread(() -> {
         try {
            Thread.sleep(5000);
         } catch (InterruptedException e) {
            // This should never happen.
            studio_.logs().logError("Interrupted while waiting to dismiss alert");
         }
         if (alert_ != null) {
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
      if (alert_ == null) {
         return;
      }
      if (imagesExpected_ > 0) {
         if (nextFrameAlertGenerator_ != null && nextFrameAlertGenerator_.isRunning()) {
            nextFrameAlertGenerator_.restart();
         }
         alert_.setText(String.format(
               "Received %d of %d images",
               imagesReceived_, imagesExpected_));

      } else {
         alert_.setText("No images expected.");
      }
   }

   private void setNextImageAlert(MMAcquistionControlCallbacks eng) {
      if (imagesExpected_ > 0) {
         // Calculate time until next frame (in seconds)
         // Note that the engine nextWakTime should have a base identical to
         // System.nanoTime()
         int s = (int) ((eng.getNextWakeTime() - System.nanoTime() / 1000000.0) / 1000.0);
         String text = "Next frame in " + s + " sec";
         if (nextImageAlert_ == null) {
            nextImageAlert_ = studio_.alerts().postUpdatableAlert("Acquisition", text);
         } else {
            nextImageAlert_.setText(text);
         }
      }
   }

   private static Storage getAppropriateStorage(final Studio studio,
                                                final DefaultDatastore store,
                                                final String path,
                                                final boolean isNew) throws IOException {
      Datastore.SaveMode mode = DefaultDatastore.getPreferredSaveMode(studio);
      if (null != mode) {
         switch (mode) {
            case SINGLEPLANE_TIFF_SERIES:
               return new StorageSinglePlaneTiffSeries(store, path, isNew);
            case MULTIPAGE_TIFF:
               return new StorageMultipageTiff(studio.app().getMainWindow(), store, path, isNew);
            case ND_TIFF:
               return new NDTiffAdapter(store, path, isNew);
            default:
               break;
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