/**
 * Binding to ClearVolume 3D viewer View Micro-Manager datasets in 3D
 *
 * AUTHOR: Nico Stuurman COPYRIGHT: Regents of the University of California,
 * 2015 - 2017
 * LICENSE: This file is distributed under the BSD license. License text is
 * included with the source distribution.
 *
 * This file is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE.
 *
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 */
package edu.ucsf.valelab.mmclearvolumeplugin;

import clearvolume.renderer.ClearVolumeRendererInterface;
import clearvolume.renderer.cleargl.recorder.VideoRecorderInterface;
import clearvolume.renderer.factory.ClearVolumeRendererFactory;
import clearvolume.transferf.TransferFunction1D;
import clearvolume.transferf.TransferFunctions;

import com.jogamp.newt.awt.NewtCanvasAWT;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;
import com.jogamp.newt.NewtFactory;
import coremem.enums.NativeTypeEnum;
import coremem.fragmented.FragmentedMemory;

import edu.ucsf.valelab.mmclearvolumeplugin.events.CanvasDrawCompleteEvent;


import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.event.WindowFocusListener;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;
import org.micromanager.LogManager;

import org.micromanager.Studio;
import org.micromanager.UserProfile;
import org.micromanager.data.Coordinates;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.DataProviderHasNewImageEvent;
import org.micromanager.data.Datastore;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultDatastoreClosingEvent;
import org.micromanager.data.internal.DefaultImage;
import org.micromanager.display.ChannelDisplaySettings;
import org.micromanager.display.ComponentDisplaySettings;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DataViewerListener;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplaySettings.ColorMode;
import org.micromanager.display.DisplayWindow;
import org.micromanager.events.ShutdownCommencingEvent;

// Imports for MMStudio internal packages
// Plugins should not access internal packages, to ensure modularity and
// maintainability. However, this plugin code is older than the current
// MMStudio API, so it still uses internal classes and interfaces. New code
// should not imitate this practice.
import org.micromanager.display.inspector.internal.panels.intensity.ImageStatsPublisher;
import org.micromanager.display.internal.event.DataViewerDidBecomeActiveEvent;
import org.micromanager.display.internal.event.DataViewerDidBecomeInactiveEvent;
import org.micromanager.display.internal.event.DataViewerDidBecomeVisibleEvent;
import org.micromanager.display.internal.event.DataViewerWillCloseEvent;
import org.micromanager.display.internal.event.DefaultDisplaySettingsChangedEvent;
import org.micromanager.display.internal.imagestats.BoundsRectAndMask;
import org.micromanager.display.internal.imagestats.ImageStatsRequest;
import org.micromanager.display.internal.imagestats.ImagesAndStats;
import org.micromanager.display.internal.imagestats.ImageStatsProcessor;
import org.micromanager.display.internal.imagestats.IntegerComponentStats;
import org.micromanager.internal.utils.MMFrame;



/**
 * Micro-Manager DataViewer that shows 3D stack in the ClearVolume 3D Renderer
 * 
 * @author nico
 */
public class CVViewer implements DataViewer, ImageStatsPublisher {

   private DisplaySettings displaySettings_;
   private ImageStatsProcessor imageStatsProcessor_;
   private final Studio studio_;
   private DataProvider dataProvider_;
   private DataViewer clonedDisplay_;
   private ClearVolumeRendererInterface clearVolumeRenderer_;
   private String name_;
   private final EventBus displayBus_;
   private final CVFrame cvFrame_;
   private int maxValue_;
   private final String XLOC = "XLocation";
   private final String YLOC = "YLocation";
   private final Class<?> ourClass_;
   private int activeChannel_ = 0;
   private Coords lastDisplayedCoords_;
   private ImagesAndStats lastCalculatedImagesAndStats_;
   private final Color[] colors = {Color.RED, Color.GREEN, Color.BLUE, Color.MAGENTA,
            Color.PINK, Color.CYAN, Color.YELLOW, Color.ORANGE};
   
   
   private class CVFrame extends MMFrame {
      public CVFrame() {
         super();
         super.loadAndRestorePosition(100, 100);
      }
   }
   
   public CVViewer(Studio studio) {
      this(studio, null);
   }

   public CVViewer(final Studio studio, final DataProvider provider) {
      // first make sure that our app's icon will not change:
      // This call still seems to generate a null pointer exception, at 
      // at jogamp.newt.driver.windows.DisplayDriver.<clinit>(DisplayDriver.java:70)
      // which is ugly but seems harmless
      try {
         NewtFactory.setWindowIcons(null);
      } catch (NullPointerException npe) {
         // this seems to always happen, but otherwise not be a problem
      }
            
      imageStatsProcessor_ = ImageStatsProcessor.create();
      
      ourClass_ = this.getClass();
      studio_ = studio;
      if (provider == null) {
         clonedDisplay_ = studio_.displays().getActiveDataViewer();
         if (clonedDisplay_ != null) {
            dataProvider_ = clonedDisplay_.getDataProvider();
            name_ = clonedDisplay_.getName() + "-ClearVolume";
         }
      } else {
         dataProvider_ = provider;
         clonedDisplay_ = getDisplay(dataProvider_);
         if (clonedDisplay_ != null) {
            name_ = clonedDisplay_.getName() + "-ClearVolume";
         }
      }
      displayBus_ = new EventBus();
      cvFrame_ = new CVFrame();
      
      if (dataProvider_ == null) {
         studio_.logs().showMessage("No data set open");
         return;
      }
      
      if (name_ == null) {
         name_ = dataProvider_.getSummaryMetadata().getPrefix();
      }

      initializeRenderer(0, 0);

   }
   
   private void initializeRenderer(final int timePoint, final int position) {

      final double preferredGamma = 0.5;
      final int min = 0;

      if (clonedDisplay_ == null) {
         // There could be a display attached to the store now
         clonedDisplay_ = getDisplay(dataProvider_);
      }

      if (clonedDisplay_ != null) {
         name_ = clonedDisplay_.getName() + "-ClearVolume";
         displaySettings_ = clonedDisplay_.getDisplaySettings().copyBuilder().build();
      } else {
         displaySettings_ = studio_.displays().getStandardDisplaySettings();
      }

      try {
         maxValue_ = 1 << dataProvider_.getAnyImage().getMetadata().getBitDepth();

         final int nrCh = dataProvider_.getAxisLength(Coords.CHANNEL);

         // clean up ContrastSettings
         // TODO: convert to new scheme of dealing with contrasSettings
         DisplaySettings.ContrastSettings[] contrastSettings
                 = displaySettings_.getChannelContrastSettings();
         if (contrastSettings == null || contrastSettings.length != nrCh) {
            contrastSettings = new DisplaySettings.ContrastSettings[nrCh];
            for (int ch = 0; ch < dataProvider_.getAxisLength(Coords.CHANNEL); ch++) {
               contrastSettings[ch] = studio_.displays().
                       getContrastSettings(min, maxValue_, preferredGamma, true);
            }
         } else {
            for (int ch = 0; ch < contrastSettings.length; ch++) {
               if (contrastSettings[ch] == null
                       || contrastSettings[ch].getContrastMaxes() == null
                       || contrastSettings[ch].getContrastMaxes()[0] == null
                       || contrastSettings[ch].getContrastMaxes()[0] == 0
                       || contrastSettings[ch].getContrastMins() == null
                       || contrastSettings[ch].getContrastMins()[0] == null) {
                  contrastSettings[ch] = studio_.displays().
                          getContrastSettings(min, maxValue_, preferredGamma, true);
               } else if (contrastSettings[ch].getContrastGammas() == null
                       || contrastSettings[ch].getContrastGammas()[0] == null) {
                  contrastSettings[ch] = studio_.displays().getContrastSettings(
                          contrastSettings[ch].getContrastMins()[0],
                          contrastSettings[ch].getContrastMaxes()[0],
                          preferredGamma,
                          contrastSettings[ch].getIsVisible());
               }

               contrastSettings[ch] = studio_.displays().getContrastSettings(
                       contrastSettings[ch].getContrastMins()[0],
                       contrastSettings[ch].getContrastMaxes()[0],
                       0.5 * contrastSettings[ch].getContrastGammas()[0],
                       true);
            }
         }

         // and clean up Colors
         List<Color> channelColors = displaySettings_.getAllChannelColors();
         if (channelColors == null || channelColors.size() != nrCh) {
            channelColors = new ArrayList<>(colors.length);
            for (int i = 0; i < nrCh && i < colors.length; i++) {
               channelColors.add(colors[i]);
            }
         } else {
            for (int ch = 0; ch < nrCh; ch++) {
               if (channelColors.get(ch) == null) {
                  channelColors.add(ch, colors[ch]);
               }
            }
         }

         displaySettings_ = displaySettings_.copy().
                 channelColors(channelColors.toArray((new Color[0]))).
                 channelContrastSettings(contrastSettings).
                 autostretch(displaySettings_.isAutostretchEnabled()).
                 build();

         Image randomImage = dataProvider_.getAnyImage();
         // creates renderer:
         NativeTypeEnum nte = NativeTypeEnum.UnsignedShort;
         if (randomImage.getBytesPerPixel() == 1) {
            nte = NativeTypeEnum.UnsignedByte;
         }

         try {
         clearVolumeRenderer_
                 = ClearVolumeRendererFactory.newOpenCLRenderer(
                         name_,
                         randomImage.getWidth(),
                         randomImage.getHeight(),
                         nte,
                         768,
                         768,
                         nrCh,
                         true);
         } catch (NullPointerException npe) {
            if (npe.getMessage().contains("is calling TIS")) {
               // Error message caused by JOGL since macOS 10.13.4, cannot fix at the moment so silencing it:
               // https://github.com/processing/processing/issues/5462
               // Some discussion on the Apple's developer forums seems to suggest that is not serious:
               // https://forums.developer.apple.com/thread/105244
               studio_.logs().logError("Null Pointer Error caused by upstream jogl code on Mac OS X since 10.13.4");
            } else {
               studio_.logs().logError(npe);
            }
         }

         final NewtCanvasAWT lNCWAWT = clearVolumeRenderer_.getNewtCanvasAWT();

         cvFrame_.setTitle(name_);
         cvFrame_.setLayout(new BorderLayout());
         final Container container = new Container();
         container.setLayout(new BorderLayout());
         container.add(lNCWAWT, BorderLayout.CENTER);
         cvFrame_.setSize(new Dimension(randomImage.getWidth(),
                 randomImage.getHeight()));
         cvFrame_.add(container);

         SwingUtilities.invokeLater(() -> {
            cvFrame_.setVisible(true);
            postEvent(DataViewerDidBecomeVisibleEvent.create(this));
         });

         clearVolumeRenderer_.setTransferFunction(TransferFunctions.getDefault());

         drawVolume(timePoint, position);
         displayBus_.post(new CanvasDrawCompleteEvent());

         // Multi-Pass rendering is active by default but causes bugs in the display
         clearVolumeRenderer_.toggleAdaptiveLOD();
         clearVolumeRenderer_.setVisible(true);
         clearVolumeRenderer_.requestDisplay();
         clearVolumeRenderer_.toggleControlPanelDisplay();
         cvFrame_.pack();

      } catch (IOException ioe) {
         studio_.logs().logError(ioe);
      }
   }

   /**
    * Code that needs to register this instance with various managers and
    * listeners. Could have been in the constructor, except that it is unsafe to
    * register our instance before it is completed. Needs to be called right
    * after the constructor.
    */
   public void register() {

      dataProvider_.registerForEvents(this);
      studio_.displays().addViewer(this);
      studio_.events().registerForEvents(this);

      // used to reference our instance within the listeners:
      final CVViewer ourViewer = this;

      // the WindowFocusListener should go into the WindowAdapter, but there it
      // does not work, so add both a WindowListener and WindowFocusListener
      cvFrame_.addWindowFocusListener(new WindowFocusListener() {
         @Override
         public void windowGainedFocus(WindowEvent e) {
            postEvent(DataViewerDidBecomeActiveEvent.create(ourViewer));
         }

         @Override
         public void windowLostFocus(WindowEvent e) {
            postEvent(DataViewerDidBecomeInactiveEvent.create(ourViewer));
         }
      }
      );

      cvFrame_.addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent e) {
            cleanup();
         }

         @Override
         public void windowClosed(WindowEvent e) {
         }
      });

   }

   private void cleanup() {
      UserProfile profile = studio_.profile();
      profile.getSettings(ourClass_).putInteger(XLOC, cvFrame_.getX());
      profile.getSettings(ourClass_).putInteger(YLOC, cvFrame_.getY());
      studio_.getDisplayManager().removeViewer(this);
      studio_.events().post(DataViewerWillCloseEvent.create(this));
      dataProvider_.unregisterForEvents(this);
      studio_.events().unregisterForEvents(this);
      clearVolumeRenderer_.close();
      cvFrame_.dispose();
      imageStatsProcessor_.shutdown();
   }

   private void setOneChannelVisible(int chToBeVisible) {
      for (int ch = 0; ch < dataProvider_.getAxisLength(Coords.CHANNEL); ch++) {
         boolean setVisible = false;
         if (ch == chToBeVisible) {
            setVisible = true;
         }
         clearVolumeRenderer_.setLayerVisible(ch, setVisible);
      }
   }
   
   private void setAllChannelsVisible() {
      for (int ch = 0; ch < dataProvider_.getAxisLength(Coords.CHANNEL); ch++) {
         clearVolumeRenderer_.setLayerVisible(ch, true);
      }
   }

   /**
    * There was an update to the display settings, so update the display
    * of the image to reflect the change.  Only change variables that actually
    * changed
    * @param ds New display settings 
    */
   @Override
   public void setDisplaySettings(DisplaySettings ds) {
      if (displaySettings_.getColorMode() != ds.getColorMode()) {
         if (ds.getColorMode() == DisplaySettings.ColorMode.COMPOSITE) {
            setAllChannelsVisible();
         } else {
            setOneChannelVisible(activeChannel_); // todo: get the channel selected in the slider
         }
      }
      for (int ch = 0; ch < dataProvider_.getAxisLength(Coords.CHANNEL); ch++ ) {
         if ( displaySettings_.isChannelVisible(ch) != ds.isChannelVisible(ch)) {
            clearVolumeRenderer_.setLayerVisible(ch, ds.isChannelVisible(ch) );
         }
         Color nc = ds.getChannelColor(ch);
         if (ds.getColorMode() == DisplaySettings.ColorMode.GRAYSCALE) {
            nc = Color.WHITE;
         }
         if (displaySettings_.getChannelColor(ch) != nc || 
                 displaySettings_.getColorMode() != ds.getColorMode() ) {
            clearVolumeRenderer_.setTransferFunction(ch, getGradientForColor(nc));
         }

         // Autostretch if set
         if (!Objects.equals(ds.isAutostretchEnabled(),
                 displaySettings_.isAutostretchEnabled())
                 || !Objects.equals(ds.getAutoscaleIgnoredPercentile(),
                         displaySettings_.getAutoscaleIgnoredPercentile())) {
            if (ds.isAutostretchEnabled()) {
               try {
                  ds = autostretch(ds);
                  //drawVolume(currentlyShownTimePoint_);
                  //displayBus_.post(new CanvasDrawCompleteEvent());
               } catch (IOException ioe) {
                  studio_.logs().logError(ioe);
               }
            }
         }

         ChannelDisplaySettings displayChannelSettings = displaySettings_.getChannelSettings(ch);
         ChannelDisplaySettings dsChannelSettings = ds.getChannelSettings(ch);

         if ( displayChannelSettings.getComponentSettings(0).getScalingMaximum() != 
                 dsChannelSettings.getComponentSettings(0).getScalingMaximum()  ||
              displayChannelSettings.getComponentSettings(0).getScalingMinimum() != 
                 dsChannelSettings.getComponentSettings(0).getScalingMinimum()  ) {
            float min = (float) dsChannelSettings.getComponentSettings(0).getScalingMinimum() /
                    (float) maxValue_; 
            float max = (float) dsChannelSettings.getComponentSettings(0).getScalingMaximum() /
                    (float) maxValue_;
            clearVolumeRenderer_.setTransferFunctionRange(ch, min, max);
         }
         
         if (displayChannelSettings.getComponentSettings(0).getScalingGamma() != 
                 dsChannelSettings.getComponentSettings(0).getScalingGamma() ) {
            clearVolumeRenderer_.setGamma(ch, 
                    dsChannelSettings.getComponentSettings(0).getScalingGamma());
         }
         
      }
  
      
      // Update the Inspector window
      this.postEvent(DefaultDisplaySettingsChangedEvent.create(
              this, displaySettings_, ds));
      
      // replace our reference to the display settings with the new one
      displaySettings_ = ds;
   }

   @Override
   public DisplaySettings getDisplaySettings() {
      return displaySettings_;
   }

   @Override
   public void registerForEvents(Object o) {
      // System.out.println("Registering for event: " + o.toString());
      displayBus_.register(o);
   }

   @Override
   public void unregisterForEvents(Object o) {
      // System.out.println("Unregistering for event: " + o.toString());
      displayBus_.unregister(o);
   }
   
   public void postEvent(Object o) {
      // System.out.println("Posting event on the EventBus");
      displayBus_.post(o);
   }

   @Override
   public Datastore getDatastore() {
      if (dataProvider_ instanceof Datastore) {
         return (Datastore) dataProvider_;
      }
      return null;
   }
   
   @Override
   public DataProvider getDataProvider() {
      return dataProvider_;
   }
   
   @Override
   public void setDisplayedImageTo(Coords coords) {
      // ClearVolume uses commands and keystrokes that work on a given channel
      // Make sure that the channel that ClearVolume works on is synced with the
      // Channel slider position in the ClearVolume panel in the Image Inspector
      activeChannel_ = coords.getChannel();
      if (displaySettings_.getChannelColorMode() != DisplaySettings.ColorMode.COMPOSITE) {
         setOneChannelVisible(coords.getChannel());
      }
      clearVolumeRenderer_.setCurrentRenderLayer(coords.getChannel());
      try {
         drawVolume(coords.getT(), coords.getP());
      } catch (IOException ioe) {
          studio_.logs().logError(ioe);
      }
      lastDisplayedCoords_ = coords;
      displayBus_.post(new CanvasDrawCompleteEvent());
   }

   @Override
   /**
    * Assemble all images that are showing in our volume. May need to be updated
    * for multiple time points in the future
    */
   public List<Image> getDisplayedImages() throws IOException {
      // System.out.println("getDisplayed Images called");
      List<Image> imageList = new ArrayList<>();
      final int nrZ = dataProvider_.getAxisLength(Coords.Z);
      final int nrCh = dataProvider_.getAxisLength(Coords.CHANNEL);
      for (int ch = 0; ch < nrCh; ch++) {
         /*
         // return the complete stack
         for (int i = 0; i < nrZ; i++) {
            coordsBuilder_ = coordsBuilder_.z(i).channel(ch).time(0).stagePosition(0);
            Coords coords = coordsBuilder_.build();
            imageList.add(dataProvider_.getImage(coords));
         }
         */

         // Only return the middle image
         
         Coords coords = Coordinates.builder().z(nrZ / 2).channel(ch).t(0).
                           stagePosition(0).build();
         imageList.add(dataProvider_.getImage(coords));

      }
      return imageList;
   }

   /**
    * This method ensures that the Inspector histograms have up-to-date data to
    * display.
    */
   //public void updateHistograms() {
      // Needed to initialize the histograms
      // TODO
      // studio_.displays().updateHistogramDisplays(getDisplayedImages(), this);
   //}

   @Override
   public String getName() {
      // System.out.println("Name requested, gave: " + name_);
      return name_;
   }

   /**
    * This function is in CV 1.1.2, replace when updating.
    * Returns a transfer a
    * simple transfer function that is a gradient from dark transparent to a
    * given color. The transparency of the given color is used.
    *
    * @param pColor color
    * @return 1D transfer function.
    */
   private TransferFunction1D getGradientForColor(Color pColor) {
      final TransferFunction1D lTransfertFunction = new TransferFunction1D();
      lTransfertFunction.addPoint(0, 0, 0, 0);
      float lNormaFactor = (float) (1.0 / 255);
      lTransfertFunction.addPoint(lNormaFactor * pColor.getRed(),
              lNormaFactor * pColor.getGreen(),
              lNormaFactor * pColor.getBlue(),
              lNormaFactor * pColor.getAlpha());
      return lTransfertFunction;
   }

   /**
    * Draws the volume of the given time point in the viewer
    * @param timePoint zero-based index in the time axis
    * @param position zero-based index into the position axis
    * @throws IOException 
    */
   public final void drawVolume(final int timePoint, final int position) throws IOException {
      //if (timePoint == currentlyShownTimePoint_)
      //   return; // nothing to do, already showing requested timepoint
      // create fragmented memory for each stack that needs sending to CV:
      Image randomImage = dataProvider_.getAnyImage();
      final Metadata metadata = randomImage.getMetadata();
      final SummaryMetadata summary = dataProvider_.getSummaryMetadata();
      final int nrZ = dataProvider_.getAxisLength(Coords.Z);
      final int nrCh = dataProvider_.getAxisLength(Coords.CHANNEL);
      final int nrPos = dataProvider_.getAxisLength(Coords.STAGE_POSITION);

      // long startTime = System.currentTimeMillis();
      clearVolumeRenderer_.setVolumeDataUpdateAllowed(false);

      for (int ch = 0; ch < nrCh; ch++) {
         FragmentedMemory fragmentedMemory = new FragmentedMemory();
         for (int i = 0; i < nrZ; i++) {
            Coords coords = Coordinates.builder().z(i).channel(ch).t(timePoint).
                                                   stagePosition(position).build();
            lastDisplayedCoords_ = coords;

            // Bypass Micro-Manager api to get access to the ByteBuffers
            DefaultImage image = (DefaultImage) dataProvider_.getImage(coords);

            // add the contiguous memory as fragment:
            if (image != null) {
               fragmentedMemory.add(image.getPixelBuffer());
            } else {
                // if the image is missing, replace with pixels initialized to 0
                fragmentedMemory.add(ByteBuffer.allocateDirect(
                        randomImage.getHeight() * 
                        randomImage.getWidth() * 
                        randomImage.getBytesPerPixel() ) );
            }
         }

         // TODO: correct x and y voxel sizes using aspect ratio
         double pixelSizeUm = metadata.getPixelSizeUm();
         if (pixelSizeUm == 0.0) {
            pixelSizeUm = 1.0;
         }
         Double stepSizeUm = summary.getZStepUm();
         if (stepSizeUm == null || stepSizeUm == 0.0) {
            stepSizeUm = 1.0;
         }
         
         // pass data to renderer: (this call takes a long time!)
         clearVolumeRenderer_.setVolumeDataBuffer(0, 
                 TimeUnit.SECONDS, 
                 ch,
                 fragmentedMemory,
                 randomImage.getWidth(),
                 randomImage.getHeight(),
                 nrZ, 
                 pixelSizeUm,
                 pixelSizeUm, 
                 stepSizeUm);


         // Set various display options:
         // HACK: on occassion we get null colors, correct that problem here
         Color chColor = displaySettings_.getChannelColor(ch);
         if (chColor == null) {
            chColor = colors[ch];
            List<Color> chColors = displaySettings_.getAllChannelColors();
            chColors.add(nrCh, chColor);
            // TODO
            // displaySettings_ = displaySettings_.copyBuilder().channelColors(chColors).build();
         }
         if (displaySettings_.getChannelColorMode() == ColorMode.GRAYSCALE) {
            chColor = Color.WHITE;
         }
         clearVolumeRenderer_.setLayerVisible(ch, displaySettings_.isChannelVisible(ch) );
         clearVolumeRenderer_.setTransferFunction(ch, getGradientForColor(chColor));
         try {
            float max = (float) displaySettings_.getChannelContrastSettings()[ch].getContrastMaxes()[0]
                    / (float) maxValue_;
            float min = (float) displaySettings_.getChannelContrastSettings()[ch].getContrastMins()[0]
                    / (float) maxValue_;
            clearVolumeRenderer_.setTransferFunctionRange(ch, min, max);
            Double[] contrastGammas = displaySettings_.getChannelContrastSettings()[ch].getContrastGammas();
            if (contrastGammas != null) {
               clearVolumeRenderer_.setGamma(ch, contrastGammas[0]);
            }
         } catch (NullPointerException ex) {
            studio_.logs().logError(ex);
         }
      }
      clearVolumeRenderer_.setVolumeDataUpdateAllowed(true);
      
      // This call used to time out, now appears to work      
      if (!clearVolumeRenderer_.waitToFinishAllDataBufferCopy(2, TimeUnit.SECONDS)) {
         studio_.logs().logError("ClearVolume timed out after 2 seconds");
      }
  
   }

   /*
    * Series of functions that are merely pass through to the underlying 
    * clearVolumeRenderer
   */
   
   /**
    * I would have liked an on/off control here, but the ClearVolume api
    * only has a toggle function
    */
   public void toggleWireFrameBox() {
      if (clearVolumeRenderer_ != null) {
         clearVolumeRenderer_.toggleBoxDisplay();
      }
   }
   
   public void toggleControlPanelDisplay() {
      if (clearVolumeRenderer_ != null) {
         clearVolumeRenderer_.toggleControlPanelDisplay();
      }
   }
   
   public void toggleParametersListFrame() {
      if (clearVolumeRenderer_ != null) {
         clearVolumeRenderer_.toggleParametersListFrame();
      }
   }
   
   
   public void resetRotationTranslation () {
      if (clearVolumeRenderer_ != null) {
         clearVolumeRenderer_.resetRotationTranslation();
         float x = clearVolumeRenderer_.getTranslationX();
         float y = clearVolumeRenderer_.getTranslationY();
         float z = clearVolumeRenderer_.getTranslationZ();
         studio_.logs().logMessage("Rotation now is: " + x + ", " + y + ", " + z);
      }
   }
   
   /**
    * Centers the visible part of the ClipBox
    * It seems that 0, 0 is in the center of the full volume, and 
    * that -1 and 1 are at the edges of the volume
    */
   public void center() {
      if (clearVolumeRenderer_ != null) {
         float[] clipBox = clearVolumeRenderer_.getClipBox();
         clearVolumeRenderer_.setTranslationX( -(clipBox[1] + clipBox[0]) / 2.0f);
         clearVolumeRenderer_.setTranslationY( -(clipBox[3] + clipBox[2]) / 2.0f);
         // do not change TRanslationZ, since that mainly changes how close we are 
         // to the object, not really the rotation point
         // clearVolumeRenderer_.setTranslationZ( -5);
      }
   }
   
   /**
    * Resets the rotation so that the object lines up with the xyz axis.
    */
   public void straighten() {
      if (clearVolumeRenderer_ != null) {
         // Convoluted way to reset the rotation
         // I probably should use rotationControllers...
         float x = clearVolumeRenderer_.getTranslationX();
         float y = clearVolumeRenderer_.getTranslationY();
         float z = clearVolumeRenderer_.getTranslationZ();
         clearVolumeRenderer_.resetRotationTranslation();
         clearVolumeRenderer_.setTranslationX(x);
         clearVolumeRenderer_.setTranslationY(y);
         clearVolumeRenderer_.setTranslationZ(z);
      }
   }
   
   public void attachRecorder(VideoRecorderInterface recorder) {
      Runnable dt = new Thread  (() -> {               
         clearVolumeRenderer_.setVideoRecorder(recorder);
         clearVolumeRenderer_.toggleRecording();
         // Force an update of the display to start the recording immediately
         clearVolumeRenderer_.addTranslationZ(0.0);
      });
      SwingUtilities.invokeLater(dt);
   }
   
   public void toggleRecording() {
      Runnable dt = new Thread(() -> {
         clearVolumeRenderer_.toggleRecording();
      });
      SwingUtilities.invokeLater(dt);
   }
   
   /**
    * Print statements to learn about the renderer.  Should be removed before release
    */
   private void printRendererInfo() {
      float x = clearVolumeRenderer_.getTranslationX();
      float y = clearVolumeRenderer_.getTranslationY();
      float z = clearVolumeRenderer_.getTranslationZ();
      studio_.logs().logMessage("Translation now is: " + x + ", " + y + ", " + z);
      String clipBoxString = "Clipbox: ";
      for (int i = 0; i < 6; i++) {
         clipBoxString += ", " + clearVolumeRenderer_.getClipBox()[i];
      }
      studio_.logs().logMessage(clipBoxString);
   }
   
   
   /**
    * It appears that the clip range in the ClearVolume Renderer goes from 
    * -1 to 1
    * @param axis desired axies (X=0, Y=1, Z=2, defined in ClearVolumePlugin)
    * @param minVal minimum value form the slider
    * @param maxVal maxmimum value form the slider
    */
   public void setClip(int axis, int minVal, int maxVal) {
      if (clearVolumeRenderer_ != null) {
         float min = ( (float) minVal / (float) CVInspectorPanelController.SLIDERRANGE ) * 2 - 1;
         float max = ( (float) maxVal / (float) CVInspectorPanelController.SLIDERRANGE ) * 2 - 1;
         float[] clipBox = clearVolumeRenderer_.getClipBox();
         switch (axis) {
                 case CVInspectorPanelController.XAXIS : 
                    clipBox[0] = min;  clipBox[1] = max;
                    break;
                 case CVInspectorPanelController.YAXIS :
                    clipBox[2] = min;  clipBox[3] = max;
                    break;
                  case CVInspectorPanelController.ZAXIS :
                    clipBox[4] = min;  clipBox[5] = max;
                    break;
         }
         clearVolumeRenderer_.setClipBox(clipBox); 
      }
   }
   
   public float[] getClipBox() {
      if (clearVolumeRenderer_ != null) {
         return clearVolumeRenderer_.getClipBox();
      }
      return null;
   }

   /**
    * Find the first DisplayWindow attached to this dataprovider
    *
    * @param provider first DisplayWindow or null if not found
    */
   private DisplayWindow getDisplay(DataProvider provider) {
      List<DisplayWindow> dataWindows = studio_.displays().getAllImageWindows();
      for (DisplayWindow dv : dataWindows) {
         if (provider == dv.getDataProvider()) {
            return dv;
         }
      }
      return null;
   }
   
   private DisplaySettings autostretch(DisplaySettings displaySettings) throws IOException {
      if (lastCalculatedImagesAndStats_ == null) {
         return displaySettings;
      }
          
      DisplaySettings.Builder newSettingsBuilder = displaySettings.copyBuilder();
      Coords baseCoords = getDisplayedImages().get(0).getCoords();
      Double extremaPercentage = displaySettings.getAutoscaleIgnoredPercentile();
      if (extremaPercentage < 0.0) {
         extremaPercentage = 0.0;
      }
      for (int ch = 0; ch < dataProvider_.getAxisLength(Coords.CHANNEL); ++ch) {
         Image image = dataProvider_.getImage(baseCoords.copyBuilder().channel(ch).build());
         if (image != null) {
            ChannelDisplaySettings.Builder csCopyBuilder = 
                    displaySettings.getChannelSettings(ch).copyBuilder();
            for (int j = 0; j < image.getNumComponents(); ++j) {
               IntegerComponentStats componentStats = 
                       lastCalculatedImagesAndStats_.getResult().get(ch).getComponentStats(0);
               ComponentDisplaySettings.Builder ccB = csCopyBuilder.getComponentSettings(j).copyBuilder();
               ccB.scalingRange(componentStats.
                       getAutoscaleMinForQuantile(extremaPercentage), 
                       componentStats.
                       getAutoscaleMaxForQuantile(extremaPercentage));
               ccB.scalingGamma(displaySettings.getChannelSettings(ch).
                       getComponentSettings(j).getScalingGamma());
               csCopyBuilder.component(j, ccB.build());
            }
            newSettingsBuilder.channel(ch, csCopyBuilder.build());
         }
      }
      DisplaySettings newSettings = newSettingsBuilder.build();
      postEvent(DefaultDisplaySettingsChangedEvent.create(this, displaySettings, 
              newSettings));
      
      return newSettings;
   }

     
   @Subscribe
   public void onShutdownCommencing(ShutdownCommencingEvent sce) {
      if (cvFrame_ != null) {
         cleanup();
      }
   }

   @Subscribe
   public void onDataProviderHasNewImage(DataProviderHasNewImageEvent newImage) {
      if (dataProvider_ != newImage.getDataProvider()){
         return;
      }
      Coords newImageCoords = newImage.getCoords();
      if (timePointComplete(newImageCoords.getT(), dataProvider_, studio_.logs())) {
         if (clearVolumeRenderer_ == null) {
            initializeRenderer(newImageCoords.getT(), newImageCoords.getP());
         } 
      }
   }       
   
   @Subscribe
   public void onDataStoreClosingEvent(DefaultDatastoreClosingEvent ddce) {
      if (ddce.getDatastore() == dataProvider_) {
         if (cvFrame_ != null) {
            cleanup();
         }
      }
   }
   
   /**
    * Check if we have all z slices for all channels at the given time point
    * This code may be fooled by other axes in the data
    * @param timePointIndex - time point index
    * @param dataProvider
    * @param logger
    * @return true if complete
    */
   public static boolean timePointComplete (final int timePointIndex, 
           final DataProvider dataProvider, final LogManager logger ) {
      Coords zStackCoords = Coordinates.builder().t(timePointIndex).build();
      try {
         final int nrImages = dataProvider.getImagesMatching(zStackCoords).size();
         Coords intendedDimensions = dataProvider.getSummaryMetadata().
                 getIntendedDimensions();
         return nrImages >= intendedDimensions.getChannel() * intendedDimensions.getZ(); 
      } catch (IOException ioe) {
         logger.showError(ioe, "Error getting number of images from dataset");
      }
      return false;
   }

   @Override
   public boolean compareAndSetDisplaySettings(DisplaySettings originalSettings, DisplaySettings newSettings) {
       if (newSettings == null) {
         throw new NullPointerException("Display settings must not be null");
      }
      synchronized (this) {
         if (originalSettings != displaySettings_) {
            // We could compare the contents, but it's probably not worth the
            // effort; spurious failures should not affect proper usage
            return false;
         }
         if (newSettings == displaySettings_) {
            return true;
         }
         setDisplaySettings(newSettings);
         return true;
      }
   }


   @Override
   public void setDisplayPosition(Coords position, boolean forceRedisplay) {
      if (forceRedisplay || !position.equals(lastDisplayedCoords_)) {
         setDisplayedImageTo(position);
      }
   }

   @Override
   public void setDisplayPosition(Coords position) {
      setDisplayedImageTo(position);
   }

   @Override
   public Coords getDisplayPosition() {
      return lastDisplayedCoords_;
   }

   @Override
   public boolean compareAndSetDisplayPosition(Coords originalPosition, 
           Coords newPosition, boolean forceRedisplay) {
      boolean display = originalPosition != newPosition;
      if (display || forceRedisplay) {
         setDisplayedImageTo(newPosition);
      }
      return display;
   }

   @Override
   public boolean compareAndSetDisplayPosition(Coords originalPosition, Coords newPosition) {
      return compareAndSetDisplayPosition(originalPosition, newPosition, false);
   }

   @Override
   public boolean isVisible() {
      return clearVolumeRenderer_ != null && clearVolumeRenderer_.isShowing();
   }

   @Override
   public boolean isClosed() {
      return clearVolumeRenderer_ == null;
   }

   @Override
   public void addListener(DataViewerListener listener, int priority) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public void removeListener(DataViewerListener listener) {
      throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
   }

   @Override
   public ImagesAndStats getCurrentImagesAndStats() {
                  
      if (lastDisplayedCoords_ == null) {
         return null;
      }
      // Only compute the statistic for the middle coordinates, otherwise the 
      // computation is too slow
      // TODO: figure out how to compute the whole histogram in the background
      int middleSlice = dataProvider_.getAxisLength(Coords.Z) / 2;
      
      Coords position = lastDisplayedCoords_.copyBuilder().z(middleSlice).build();
      
       // Always compute stats for all channels
      Coords channellessPos = position.hasAxis(Coords.CHANNEL) ?
            position.copyBuilder().removeAxis(Coords.CHANNEL).build() :
            position;
      List<Image> images;
      try {
         images = dataProvider_.getImagesMatching(channellessPos);
      }
      catch (IOException e) {
         // TODO Should display error
         images = Collections.emptyList();
      }
      
      if (images == null) {
         return null;
      }

      // Images are sorted by channel here, since we don't (yet) have any other
      // way to correctly recombine stats with newer images (when update rate
      // is finite).
      if (images.size() > 1) {
         Collections.sort(images, (Image o1, Image o2) -> new Integer(o1.getCoords().getChannel()).
                 compareTo(o2.getCoords().getChannel()));
      }
      
      BoundsRectAndMask selection = BoundsRectAndMask.unselected();

      ImageStatsRequest request = ImageStatsRequest.create(position, images, selection);

      ImagesAndStats process = null;
      
      try {
         process = imageStatsProcessor_.process(1, request, true);
      } catch (InterruptedException ex) {
         //Logger.getLogger(CVViewer.class.getName()).log(Level.SEVERE, null, ex);
      }
      
      lastCalculatedImagesAndStats_ = process;
      
      return process;
   }
         
   
}
