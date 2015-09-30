///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     Display implementation
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.display.internal;

import com.google.common.eventbus.DeadEvent;
import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import ij.CompositeImage;
import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.io.FileInfo;
import ij.measure.Calibration;
import ij.Menus;
import ij.WindowManager;

import java.awt.Dimension;
import java.awt.Frame;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;


import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import javax.swing.event.MouseInputAdapter;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreFrozenEvent;
import org.micromanager.data.DatastoreSavePathEvent;
import org.micromanager.data.Image;
import org.micromanager.data.Metadata;
import org.micromanager.data.NewImageEvent;
import org.micromanager.data.NewSummaryMetadataEvent;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.ControlsFactory;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.RequestToDrawEvent;
import org.micromanager.events.DatastoreClosingEvent;

import org.micromanager.data.internal.DefaultCoords;

import org.micromanager.events.internal.DefaultEventManager;

import org.micromanager.display.internal.events.DefaultDisplayAboutToShowEvent;
import org.micromanager.display.internal.events.DefaultNewDisplayEvent;
import org.micromanager.display.internal.events.DefaultNewImagePlusEvent;
import org.micromanager.display.internal.events.DefaultRequestToDrawEvent;
import org.micromanager.display.internal.events.DisplayActivatedEvent;
import org.micromanager.display.internal.events.FullScreenEvent;
import org.micromanager.display.internal.events.GlobalDisplayDestroyedEvent;
import org.micromanager.display.internal.events.LayoutChangedEvent;
import org.micromanager.display.internal.events.LUTUpdateEvent;
import org.micromanager.display.internal.events.NewDisplaySettingsEvent;
import org.micromanager.display.internal.events.RequestToCloseEvent;
import org.micromanager.display.internal.events.StatusEvent;
import org.micromanager.display.internal.inspector.InspectorFrame;
import org.micromanager.display.internal.link.DisplayGroupManager;


import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.internal.utils.ReportingUtils;


/**
 * This class is the window that handles image viewing: it contains the
 * canvas and controls for determining which channel, Z-slice, etc, is shown,
 * and also acts as the central location for all management of display
 * resources.
 * Note that it is *not* an ImageJ ImageWindow; instead, it creates a
 * DummyImageWindow instance for liaising with ImageJ. See that class for
 * more information on why we do this.
 * TODO: this class is getting kind of unwieldy-huge, and should probably be
 * refactored.
 */
public class DefaultDisplayWindow extends MMFrame implements DisplayWindow {

   // HACK: the first time a DisplayWindow is created, create an
   // InspectorFrame to go with it.
   static {
      new InspectorFrame(null);
   }

   // Keeps track of unique names that we are forced to invent for anonymous
   // datasets. Note we use hashCode here so we don't maintain a reference to
   // the display itself, which would prevent garbage collection of displays.
   private static final HashMap<Integer, String> displayHashToUniqueName_ =
      new HashMap<Integer, String>();

   private final Datastore store_;
   private DisplaySettings displaySettings_;
   private MMVirtualStack stack_;
   private ImagePlus ijImage_;
   private final EventBus displayBus_;
   // List of channel names we have seen, for ensuring our contrast settings
   // are up-to-date.
   private HashSet<String> knownChannels_;
   // Keeps track of ChannelHistogramModel objects for us.
   private HashMap<Integer, ChannelHistogramModel> channelToModel_;

   // This will be our intermediary with ImageJ.
   private DummyImageWindow dummyWindow_;

   // Properties related to fullscreen mode.
   private JFrame fullScreenFrame_;

   // Used to generate custom display controls.
   private final ControlsFactory controlsFactory_;

   // GUI components
   private JPanel contentsPanel_;
   private JPanel canvasPanel_;
   private MMImageCanvas canvas_;
   private JPanel controlsPanel_;
   private HyperstackControls hyperstackControls_;
   private ImageInfoLine infoLine_;

   // Ensures that we don't try to make the GUI twice from separate threads.
   // TODO: is this even still a concern?
   private final Object guiLock_ = new Object();
   // Ensures that we don't try to close the window when in the middle of
   // drawing.
   private final Object drawLock_ = new Object();
   private boolean haveCreatedGUI_ = false;

   // Used by the pack() method to track changes in our size.
   private Dimension prevControlsSize_;

   private CanvasUpdateQueue canvasQueue_;

   private boolean haveClosed_ = false;

   // Custom string in the title.
   private String customName_;

   /**
    * Factory method to create a new DisplayWindow with default
    * DisplaySettings and title. See the main createDisplay below for
    * more info on parameters.
    */
   public static DefaultDisplayWindow createDisplay(Datastore store,
         ControlsFactory factory) {
      return createDisplay(store, factory, null, null);
   }

   /**
    * Create a new DefaultDisplayWindow. Use this method instead of calling
    * the constructor directly, as some setup needs to be performed after
    * the display has completed its constructor.
    * @param store The Datastore this display will show images for.
    * @param factory A ControlsFactory to create any extra controls for this
    *        display. May be null, in which case there will be no extra
    *        controls
    * @param settings The DisplaySettings to use for this display. May
    *        be null, in which case default settings will be pulled from the
    *        user's profile.
    * @param title A custom title. May be null, in which case the title will
    *        be "MM image display".
    * @return The new DisplayWindow in a ready-to-use state.
    */
   public static DefaultDisplayWindow createDisplay(Datastore store,
         ControlsFactory factory, DisplaySettings settings, String title) {
      DefaultDisplayWindow result = new DefaultDisplayWindow(store, factory,
            settings, title);
      // There's a race condition here: if the Datastore adds an image
      // between us registering and us manually checking for images, then
      // we risk creating GUI objects twice, so makeGUI() has to be coded
      // defensively to avoid double-calls.
      store.registerForEvents(result);
      if (store.getNumImages() > 0) {
         result.makeGUI();
      }
      DefaultEventManager.getInstance().post(
            new DefaultNewDisplayEvent(result));
      DefaultEventManager.getInstance().registerForEvents(result);
      return result;
   }

   /**
    * @param controlsFactory ControlsFactory to generate any custom controls.
    *        May be null if the creator does not want any.
    * @param settings DisplaySettings to use as initial state for this display
    * @param customName Custom title to show in title bar, or null for none.
    */
   private DefaultDisplayWindow(Datastore store,
         ControlsFactory controlsFactory, DisplaySettings settings,
         String customName) {
      super("image display window");
      store_ = store;
      knownChannels_ = new HashSet<String>();
      channelToModel_ = new HashMap<Integer, ChannelHistogramModel>();
      if (settings == null) {
         displaySettings_ = DefaultDisplaySettings.getStandardSettings();
      }
      else {
         displaySettings_ = settings;
      }
      customName_ = customName;
      displayBus_ = new EventBus();
      displayBus_.register(this);
      controlsFactory_ = controlsFactory;

      DisplayGroupManager.getInstance().addDisplay(this);

      final DefaultDisplayWindow thisWindow = this;
      // Post an event whenever we're made active, so that the InspectorFrame
      // can update its contents.
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowActivated(WindowEvent e) {
            DefaultEventManager.getInstance().post(
               new DisplayActivatedEvent(thisWindow));
         }
      });

      // HACK: on OSX, we want to show the ImageJ menubar for our windows.
      // However, if we simply do setMenuBar(Menus.getMenuBar()), then somehow
      // ImageJ *loses* the menubar: it, and the items in it, can only be
      // attached to one window at a time, apparently. So we have to put it
      // back when we lose focus.
      if (JavaUtils.isMac()) {
         addWindowListener(new WindowAdapter() {
            @Override
            public void windowActivated(WindowEvent e) {
               // Steal the menubar from ImageJ.
               setMenuBar(Menus.getMenuBar());
            }

            @Override
            public void windowDeactivated(WindowEvent e) {
               // Find the primary ImageJ window and give it its menubar back.
               for (Frame f : Frame.getFrames()) {
                  if (f instanceof ij.ImageJ) {
                     f.setMenuBar(getMenuBar());
                     break;
                  }
               }
            }
         });
      }
   }

   /**
    * Now that there's at least one image in the Datastore, we need to create
    * our UI and the objects we'll use to communicate with ImageJ. Actual
    * construction is spun off into the EDT (Event Dispatch Thread), unless
    * of course we're already in that thread.
    */
   public void makeGUI() {
      synchronized(guiLock_) {
         if (haveCreatedGUI_) {
            // Already done this.
            return;
         }
         if (!SwingUtilities.isEventDispatchThread()) {
            try {
               SwingUtilities.invokeAndWait(new Runnable() {
                  @Override
                  public void run() {
                     makeGUI_EDTSafe();
                  }
               });
            }
            catch (InterruptedException e) {
               // This should never happen.
               ReportingUtils.showError(e,
                     "Interrupted while creating DisplayWindow");
            }
            catch (java.lang.reflect.InvocationTargetException e) {
               ReportingUtils.showError(e,
                     "Exception while creating DisplayWindow");
            }
         }
         else {
            makeGUI_EDTSafe();
         }
      }
   }

   /**
    * This method should only be called from makeGUI, which
    * ensures that this method is only called from within the EDT.
    */
   private void makeGUI_EDTSafe() {
      loadAndRestorePosition(getLocation().x, getLocation().y);
      ijImage_ = new MMImagePlus();
      setImagePlusMetadata(ijImage_);
      stack_ = new MMVirtualStack(store_, displayBus_, ijImage_);
      ijImage_.setStack(getName(), stack_);
      ijImage_.setOpenAsHyperStack(true);
      displayBus_.post(new DefaultNewImagePlusEvent(this, ijImage_));
      // The ImagePlus object needs to be pseudo-polymorphic, depending on
      // the number of channels in the Datastore. However, we may not have
      // all of the channels available to us at the time this display is
      // created, so we may need to re-create things down the road.
      if (store_.getAxisLength(Coords.CHANNEL) > 1) {
         // Have multiple channels.
         shiftToCompositeImage();
      }
      if (ijImage_ instanceof MMCompositeImage) {
         ((MMCompositeImage) ijImage_).reset();
      }

      canvasQueue_ = CanvasUpdateQueue.makeQueue(this, stack_, drawLock_);

      makeWindowControls();
      // This needs to be done after the canvas is created, but before we
      // call zoomToPreferredSize.
      dummyWindow_ = DummyImageWindow.makeWindow(ijImage_, this);
      zoomToPreferredSize();
      setVisible(true);

      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent event) {
            requestToClose();
         }
      });
      setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);

      // Set us to draw the first image in the dataset.
      // TODO: potentially there could be no image at these Coords, though
      // that seems unlikely. Such an edge case isn't all that harmful
      // anyway; we'll just display a blank image until the user adjusts the
      // display to an image that does exist.
      DefaultCoords.Builder builder = new DefaultCoords.Builder();
      for (String axis : store_.getAxes()) {
         builder.index(axis, 0);
      }
      setDisplayedImageTo(builder.build());

      // Must set this before we call resetTitle(), which checks it.
      haveCreatedGUI_ = true;
      resetTitle();

      constrainWindowShape();

      DefaultEventManager.getInstance().post(new DefaultDisplayAboutToShowEvent(this));
   }

   /**
    * [Re]generate the controls for adjusting the display, showing metadata,
    * etc.
    */
   private void makeWindowControls() {
      // If this is not our first time through, try to keep the same window
      // boundaries and canvas size.
      Rectangle origBounds = null;
      if (contentsPanel_ == null) {
         contentsPanel_ = new JPanel();
      }
      else {
         // Have pre-existing components to get sizes from.
         origBounds = getBounds();
      }
      contentsPanel_.removeAll();
      contentsPanel_.setLayout(new MigLayout("insets 1, fillx, filly",
         "[grow, fill]", "0[]0[grow, fill]related[]"));

      // Text at the top of the window for image information.
      infoLine_ = new ImageInfoLine(this);
      contentsPanel_.add(infoLine_, "wrap");

      recreateCanvas();
      contentsPanel_.add(canvasPanel_, "align center, wrap, grow");

      if (controlsPanel_ == null) {
         controlsPanel_ = new JPanel(new MigLayout("insets 0, fillx"));
      }
      controlsPanel_.removeAll();
      if (hyperstackControls_ == null) {
         hyperstackControls_ = new HyperstackControls(store_, stack_, this,
               false);
      }
      controlsPanel_.add(hyperstackControls_,
            "align center, span, growx, wrap");
      controlsPanel_.add(new ButtonPanel(this, controlsFactory_));

      contentsPanel_.add(controlsPanel_, "align center, wrap, growx, growy 0");

      add(contentsPanel_);
      Insets insets = getInsets();
      Dimension size = contentsPanel_.getMinimumSize();
      setMinimumSize(new Dimension(
            insets.left + insets.right + size.width,
            insets.top + insets.bottom + size.height));
      if (origBounds == null) {
         setSize(getMaxSafeSizeFromHere());
      }
      else {
         setBounds(origBounds);
      }
      pack();
   }

   /**
    * Re-generate our image canvas and canvas panel, along with resize logic.
    */
   private void recreateCanvas() {
      canvas_ = new MMImageCanvas(ijImage_, this);

      // HACK: set the minimum size. If we don't do this, then the canvas
      // doesn't shrink properly when the window size is reduced. Why?!
      canvas_.setMinimumSize(new Dimension(16, 16));
      Double mag = displaySettings_.getMagnification();
      // Wrap the canvas in a subpanel so that we can get events when it
      // resizes.
      canvasPanel_ = new JPanel(new MigLayout("insets 0, fill"));
      canvasPanel_.add(canvas_, "align center");

      // Propagate resizing to the canvas, adjusting the view rectangle.
      canvasPanel_.addComponentListener(new ComponentAdapter() {
         @Override
         public void componentResized(ComponentEvent e) {
            Dimension panelSize = canvasPanel_.getSize();
            canvas_.updateSize(panelSize);
         }
      });

      // Add a listener so we can update the histogram when an ROI is drawn.
      canvas_.addMouseListener(new MouseInputAdapter() {
         @Override
         public void mouseReleased(MouseEvent me) {
            ijImage_.updateAndDraw();
         }
      });

      if (displaySettings_.getMagnification() == null) {
         // Grab the canvas's magnification.
         displaySettings_ = displaySettings_.copy()
            .magnification(canvas_.getMagnification())
            .build();
         displayBus_.post(new NewDisplaySettingsEvent(displaySettings_, this));
      }
   }

   /**
    * Retrieve the ChannelHistogramModel for the specified channel. Create it
    * if it does not already exist.
    */
   public ChannelHistogramModel getHistogramModel(int index) {
      if (!channelToModel_.containsKey(index)) {
         createHistogramModel(index);
      }
      return channelToModel_.get(index);
   }

   /**
    * Create a new ChannelHistogramModel for the specified channel index.
    */
   private void createHistogramModel(int index) {
      channelToModel_.put(index,
            new ChannelHistogramModel(index, store_, this, stack_,
               ijImage_));
   }

   /**
    * In addition to the display's name, we also append magnification and
    * save status.
    * This method is public so that DisplayGroupManager can force a re-set
    * of the title when other displays are created or destroyed (and our
    * display number is shown/hidden).
    */
   public void resetTitle() {
      if (!haveCreatedGUI_) {
         // No window to adjust yet.
         return;
      }
      String title = getName();
      title += String.format(" (%d%%)",
            (int) (canvas_.getMagnification() * 100));
      // HACK: don't display save status for the snap/live view.
      if (!title.contains("Snap/Live")) {
         if (store_.getIsFrozen()) {
            if (store_.getSavePath() != null) {
               title += " (Saved to Disk)";
            }
            else {
               title += " (In Memory, Complete)";
            }
         }
         else {
            title += " (Not yet saved)";
         }
      }
      // Don't mindlessly change the title, as calling setStack can
      // potentially create new ImageJ windows that we don't want if called
      // in rapid succession (for unknown reasons that would require
      // a better understanding of the ImageJ codebase).
      if (!title.contentEquals(getTitle())) {
         // Don't have multiple threads adjusting the GUI at the same time.
         synchronized(guiLock_) {
            setTitle(title);
            // Ensure that ImageJ's opinion of our name reflects our own; this
            // is important for ImageJ's "Windows" menu.
            ijImage_.setStack(getName(), stack_);
         }
      }
   }

   /**
    * Set our canvas' magnification based on the preferred window
    * magnification.
    */
   public void zoomToPreferredSize() {
      Point location = getLocation();

      // Use approximation here because ImageJ has fixed allowed magnification
      // levels and we want to be able to be a bit more approximate and snap
      // to the closest allowed magnification.
      double mag = displaySettings_.getMagnification();
      if (mag < canvas_.getMagnification()) {
         // Shrink the canvas (zoom out).
         while (mag < canvas_.getMagnification() &&
               Math.abs(mag - canvas_.getMagnification()) > .01) {
            canvas_.zoomOut(canvas_.getWidth() / 2, canvas_.getHeight() / 2);
         }
      } else if (mag > canvas_.getMagnification()) {
         // Grow the canvas (zoom in).
         while (mag > canvas_.getMagnification() &&
               Math.abs(mag - canvas_.getMagnification()) > .01) {
            canvas_.zoomIn(canvas_.getWidth() / 2, canvas_.getHeight() / 2);
         }
      }

      constrainWindowShape();

      //Make sure the window is fully on the screen
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      Point newLocation = new Point(location.x, location.y);
      if (newLocation.x + getWidth() > screenSize.width && getWidth() < screenSize.width) {
          newLocation.x = screenSize.width - getWidth();
      }
      if (newLocation.y + getHeight() > screenSize.height && getHeight() < screenSize.height) {
          newLocation.y = screenSize.height - getHeight();
      }

      setLocation(newLocation);
   }

   /**
    * Ensure the entirety of the window is on-screen and not underneath any
    * important OS components like taskbars, menubars, etc. This mostly
    * involves ensuring that our canvas is the right size.
    */
   private void constrainWindowShape() {
      Point location = getLocation();
      Insets insets = getInsets();
      if (fullScreenFrame_ != null) {
         location = fullScreenFrame_.getLocation();
         insets = fullScreenFrame_.getInsets();
      }
      Rectangle maxBounds = getSafeBounds();
      // These are the max dimensions we can achieve without changing our
      // location on-screen.
      int maxWidth = maxBounds.x + maxBounds.width - location.x;
      int maxHeight = maxBounds.y + maxBounds.height - location.y;
      // Derive the available size for the image display by subtracting off
      // the size of our insets, controls, and info text.
      maxWidth -= insets.left + insets.right;
      maxHeight -= insets.top + insets.bottom + controlsPanel_.getHeight() + infoLine_.getHeight();
      canvas_.updateSize(new Dimension(maxWidth, maxHeight));
      setSize(getMaxSafeSizeFromHere());
      pack();
   }

   /**
    * We've discovered that we need to represent a multichannel image.
    */
   private void shiftToCompositeImage() {
      // Don't want to run this from a separate thread when we're in the middle
      // of building our GUI, e.g. because a second image arrived while we're
      // still responding to the first one.
      synchronized(guiLock_) {
         // Don't draw anything while we're doing this.
         if (canvasQueue_ != null) {
            canvasQueue_.halt();
         }
         // TODO: assuming mode 1 for now.
         ijImage_ = new MMCompositeImage(ijImage_, 1, ijImage_.getTitle());
         ijImage_.setOpenAsHyperStack(true);
         MMCompositeImage composite = (MMCompositeImage) ijImage_;
         int numChannels = store_.getAxisLength(Coords.CHANNEL);
         composite.setNChannelsUnverified(numChannels);
         composite.reset();
         setImagePlusMetadata(ijImage_);
         if (canvasQueue_ != null) {
            canvasQueue_.resume();
            // Ensure we have the correct display mode set for the new object.
            canvasQueue_.reapplyLUTs();
         }
      }
      displayBus_.post(new DefaultNewImagePlusEvent(this, ijImage_));
   }

   /**
    * Tell the ImagePlus about certain properties of our data that it doesn't
    * otherwise know how to access.
    * TODO: this should maybe live in DummyImageWindow?
    */
   private void setImagePlusMetadata(ImagePlus plus) {
      try {
         Calibration cal = new Calibration(plus);
         cal.setUnit("um");
         // TODO: ImageJ only allows for one pixel size across all images, even
         // if e.g. different channels actually vary.
         // On the flipside, we only allow for square pixels, so we aren't
         // exactly perfect either.
         Image sample = store_.getAnyImage();
         if (sample == null) {
            ReportingUtils.logError("Unable to get an image for setting ImageJ metadata properties");
            return;
         }
         Double pixelSize = sample.getMetadata().getPixelSizeUm();
         if (pixelSize != null) {
            cal.pixelWidth = pixelSize;
            cal.pixelHeight = pixelSize;
         }
         SummaryMetadata summary = store_.getSummaryMetadata();
         if (summary.getWaitInterval() != null) {
            cal.frameInterval = summary.getWaitInterval() / 1000.0;
         }
         if (summary.getZStepUm() != null) {
            cal.pixelDepth = summary.getZStepUm();
         }
         plus.setCalibration(cal);

         FileInfo info = new FileInfo();
         info.directory = summary.getDirectory();
         info.fileName = summary.getName();
         info.width = sample.getWidth();
         info.height = sample.getHeight();
         ijImage_.setFileInfo(info);
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error setting metadata");
      }
   }

   /**   
    * Our layout has changed and we need to repack.
    */         
   @Subscribe
   public void onLayoutChanged(LayoutChangedEvent event) {
      try {
         validate();
         pack();
         // It's possible we've grown past the bottom of the screen (under
         // the taskbar on Windows), in which case we need to reset.
         Rectangle safeBounds = getSafeBounds();
         if (getY() + getHeight() > safeBounds.getY() + safeBounds.getHeight()) {
            Dimension safeSize = getMaxSafeSizeFromHere();
            setSize((int) Math.min(getWidth(), safeSize.getWidth()),
                  (int) Math.min(getHeight(), safeSize.getHeight()));
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error processing layout-changed event");
      }
   }

   /**
    * Something on our display bus (i.e. not the Datastore bus) wants us to
    * redisplay.
    */
   @Subscribe
   public void onDrawEvent(RequestToDrawEvent event) {
      try {
         Coords drawCoords = stack_.getCurrentImageCoords();
         if (event.getCoords() != null) {
            // In particular, they want to display this image.
            drawCoords = event.getCoords();
         }
         setDisplayedImageTo(drawCoords);
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Couldn't process RequestToDrawEvent");
      }
   }

   /**
    * We need to refresh the LUT(s) for our display.
    */
   @Subscribe
   public void onLUTUpdate(LUTUpdateEvent event) {
      LUTMaster.updateDisplayLUTs(this);
   }

   /**
    * Manually display the image at the specified coordinates.
    */
   @Override
   public void setDisplayedImageTo(Coords coords) {
      // Synchronized so we don't try to change the display while we also
      // change our UI (e.g. in shiftToCompositeImage).
      synchronized(guiLock_) {
         canvasQueue_.enqueue(coords);
      }
   }

   /**
    * Request a redraw of the displayed image(s).
    */
   @Override
   public void requestRedraw() {
      setDisplayedImageTo(stack_.getCurrentImageCoords());
   }

   @Override
   public void displayStatusString(String status) {
      displayBus_.post(new StatusEvent(status));
   }

   @Override
   public void adjustZoom(double factor) {
      setMagnification(getMagnification() * factor);
      // HACK: for some reason, changing the zoom level can cause us to
      // "lose" our LUTs, reverting to grayscale or single-color mode. So we
      // refresh our LUT status after changing the zoom.
      LUTMaster.updateDisplayLUTs(this);
   }

   @Override
   public void setMagnification(double magnification) {
      setDisplaySettings(displaySettings_.copy().magnification(magnification).build());
   }

   @Override
   public double getMagnification() {
      return canvas_.getMagnification();
   }
      
   @Override
   public Datastore getDatastore() {
      return store_;
   }

   // TODO: ideally we should not need this exposed (and definitely not
   // exposed in the API, as it's an implementation detail). Find out what
   // our users need this method for and find a better way.
   public MMVirtualStack getStack() {
      return stack_;
   }

   @Override
   public DisplaySettings getDisplaySettings() {
      return displaySettings_;
   }

   @Override
   public void setDisplaySettings(DisplaySettings settings) {
      displaySettings_ = settings;
      boolean magChanged = (settings.getMagnification() != null &&
            settings.getMagnification() != canvas_.getMagnification());
      // This will cause the canvas to pick up magnification changes, note.
      displayBus_.post(new NewDisplaySettingsEvent(settings, this));
      DefaultDisplaySettings.setStandardSettings(settings);
      if (haveCreatedGUI_) {
         if (magChanged) {
            // Ensure that any changes in the canvas size (and thus in our
            // window size) properly adjust other elements.
            constrainWindowShape();
         }
         // Assume any change in display settings will necessitate a redraw.
         requestRedraw();
         // And the magnification may have changed.
         resetTitle();
      }
   }

   @Override
   public List<Image> getDisplayedImages() {
      ArrayList<Image> result = new ArrayList<Image>();
      Coords curCoords = stack_.getCurrentImageCoords();
      if (ijImage_ instanceof CompositeImage &&
            ((CompositeImage) ijImage_).getMode() == CompositeImage.COMPOSITE) {
         // Return all channels at current coordinates.
         for (int i = 0; i < store_.getAxisLength(Coords.CHANNEL); ++i) {
            Image tmp = store_.getImage(curCoords.copy().channel(i).build());
            if (tmp != null) {
               result.add(tmp);
            }
         }
      }
      if (result.size() == 0) {
         // No channel axis; just return the current image.
         Image tmp = store_.getImage(curCoords);
         if (tmp != null) {
            result.add(tmp);
         }
      }
      return result;
   }

   @Override
   public ImagePlus getImagePlus() {
      return ijImage_;
   }

   @Override
   public boolean requestToClose() {
      displayBus_.post(new RequestToCloseEvent(this));
      return getIsClosed();
   }

   /**
    * This exists to catch RequestToCloseEvents that nobody is listening for,
    * which can happen when displays are duplicated. If we didn't do this, then
    * our display would be impossible to get rid of.
    */
   @Subscribe
   public void onDeadEvent(DeadEvent event) {
      if (event.getEvent() instanceof RequestToCloseEvent) {
         forceClosed();
      }
   }

   @Override
   public void forceClosed() {
      if (haveClosed_) {
         // Only ever call this method once.
         return;
      }
      savePosition();
      displayBus_.post(new DisplayDestroyedEvent(this));
      DefaultEventManager.getInstance().post(
            new GlobalDisplayDestroyedEvent(this));
      DefaultEventManager.getInstance().unregisterForEvents(this);
      // HACK: notify the DisplayManager that we're going away.
      // TODO: is this necessary? DisplayManager already listens for display
      // events.
      DefaultDisplayManager.stopTrackingDisplay(this);
      store_.unregisterForEvents(this);
      // Closing the window immediately invalidates the ImagePlus
      // ImageProcessor that we use for drawing, even if our CanvasUpdateQueue
      // is in the middle of performing a drawing operation. This synchronized
      // block corresponds to one in CanvasUpdateQueue.showImage(), and ensures
      // that we don't potentially lock the EDT by destroying a window when
      // we're trying to operate on it.
      synchronized(drawLock_) {
         canvasQueue_.halt();
         dispose();
         haveClosed_ = true;
      }
   }

   /**
    * For unknown reasons, when a display gets closed, other displays may
    * spontaneously shift to grayscale. So whenever a display goes away, we
    * force all other displays to refresh their LUTs.
    */
   @Subscribe
   public void onDisplayDestroyed(GlobalDisplayDestroyedEvent event) {
      if (event.getDisplay() != this) {
         canvasQueue_.reapplyLUTs();
      }
   }

   @Override
   public synchronized boolean getIsClosed() {
      // TODO: is this a proper indicator for if the window is closed?
      return (!isVisible() && fullScreenFrame_ == null);
   }

   /**
    * Turn fullscreen mode on or off. Fullscreen is actually a separate
    * frame due to how Java handles the GUI.
    * TODO: should this really be exposed in the API?
    */
   @Override
   public void toggleFullScreen() {
      // Don't try to do this while mucking with the GUI in other ways.
      synchronized(guiLock_) {
         // If the canvas decides to update while we are changing to/from
         // fullscreen mode, then bad things can happen, so we halt updates
         // first.
         canvasQueue_.halt();
         if (fullScreenFrame_ != null) {
            // We're currently fullscreen, and our fullscreen frame needs to go
            // away now. Retrieve our contents from it first, of course.
            add(contentsPanel_);
            fullScreenFrame_.dispose();
            fullScreenFrame_ = null;
            constrainWindowShape();
            setVisible(true);
         }
         else {
            // Transfer our contents to a new JFrame for the fullscreen mode.
            setVisible(false);
            fullScreenFrame_ = new JFrame();
            fullScreenFrame_.setUndecorated(true);
            fullScreenFrame_.setBounds(
                  GUIUtils.getFullScreenBounds(getScreenConfig()));
            fullScreenFrame_.setExtendedState(JFrame.MAXIMIZED_BOTH);
            fullScreenFrame_.setResizable(false);
            fullScreenFrame_.add(contentsPanel_);
            fullScreenFrame_.setVisible(true);
            constrainWindowShape();
         }
         canvasQueue_.resume();
         displayBus_.post(
               new FullScreenEvent(getScreenConfig(), fullScreenFrame_ != null));
         if (fullScreenFrame_ == null) {
            // Our non-fullscreened self should be on top.
            toFront();
         }
      }
   }

   @Override
   public GraphicsConfiguration getScreenConfig() {
      Point p = getLocation();
      GraphicsConfiguration result = GUIUtils.getGraphicsConfigurationContaining(p.x, p.y);
      // HACK: if our corner is invalid, find the one with the greatest overlap 
      if (result == null) {
         result = GUIUtils.getGraphicsConfigurationBestMatching(getBounds());
      }
      return result;
   }

   /**
    * This retrieves the boundaries of the current screen that are not taken
    * over by OS components like taskbars and menubars.
    */
   public Rectangle getSafeBounds() {
      GraphicsConfiguration config = getScreenConfig();
      Rectangle bounds = config.getBounds();
      Insets screenInsets = Toolkit.getDefaultToolkit().getScreenInsets(config);
      bounds.x += screenInsets.left;
      bounds.width -= screenInsets.left + screenInsets.right;
      bounds.y += screenInsets.top;
      bounds.height -= screenInsets.top + screenInsets.bottom;
      return bounds;
   }

   /**
    * As above, but assumes the window does not move from its current position.
    */
   public Dimension getMaxSafeSizeFromHere() {
      Rectangle bounds = getSafeBounds();
      Point loc = getLocation();
      return new Dimension(bounds.width - loc.x, bounds.height - loc.y);
   }

   /**
    * Provided so our canvas can know what its preferred size should be; this
    * method returns the available space for the canvas.
    */
   public Dimension getMaxCanvasSize() {
      Dimension ourSize = getSize();
      Insets insets = getInsets();
      if (fullScreenFrame_ != null) {
         ourSize = fullScreenFrame_.getSize();
         insets = fullScreenFrame_.getInsets();
      }
      Dimension controlsSize = controlsPanel_.getSize();
      Dimension result = new Dimension(
            ourSize.width - insets.left - insets.right,
            ourSize.height - insets.top - insets.bottom - controlsSize.height -
            infoLine_.getHeight());
      return result;
   }

   @Override
   public ImageWindow getImageWindow() {
      return dummyWindow_;
   }

   @Override
   public Window getAsWindow() {
      return (Window) this;
   }

   @Override
   public void registerForEvents(Object obj) {
      displayBus_.register(obj);
   }

   @Override
   public void unregisterForEvents(Object obj) {
      displayBus_.unregister(obj);
   }

   @Override
   public void postEvent(Object obj) {
      displayBus_.post(obj);
   }

   @Override
   public EventBus getDisplayBus() {
      return displayBus_;
   }

   /**
    * Datastore has received a new image; display it, and adjust our
    * ImageJ object if necessary.
    */
   @Subscribe
   public void onNewImage(final NewImageEvent event) {
      try {
         if (!haveCreatedGUI_) {
            // Time to make our components, but we should only do so in the EDT.
            final DefaultDisplayWindow thisWindow = this;
            try {
               GUIUtils.invokeAndWait(new Runnable() {
                  @Override
                  public void run() {
                     thisWindow.makeGUI();
                  }
               });
            }
            catch (InterruptedException e) {
               ReportingUtils.logError(e, "Couldn't make window controls");
            }
            catch (java.lang.reflect.InvocationTargetException e) {
               ReportingUtils.logError(e, "Couldn't make window controls");
            }
         }
         receiveNewImage(event.getImage());
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Error processing new image");
      }
   }

   /**
    * Process a new image.
    */
   private void receiveNewImage(Image image) {
      try {
         int imageChannel = image.getCoords().getChannel();
         // Check if we're transitioning from grayscale to multi-channel at this
         // time.
         if (!(ijImage_ instanceof MMCompositeImage) &&
               imageChannel > 0) {
            // Have multiple channels.
            shiftToCompositeImage();
            makeWindowControls();
         }
         if (ijImage_ instanceof MMCompositeImage) {
            // Verify that ImageJ has the right number of channels.
            int numChannels = store_.getAxisLength(Coords.CHANNEL);
            MMCompositeImage composite = (MMCompositeImage) ijImage_;
            composite.setNChannelsUnverified(numChannels);
            composite.reset();
         }
         if (!channelToModel_.containsKey(imageChannel)) {
            // Create the entity that will handle display of this channel.
            createHistogramModel(imageChannel);
         }
         String name = store_.getSummaryMetadata().getSafeChannelName(imageChannel);
         if (!knownChannels_.contains(name)) {
            // Update our display settings with the new channel.
            updateChannelSettings();
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Couldn't display new image");
      }
   }

   /**
    * Update our channel display settings (color and contrast) to incorporate
    * the new channel name.
    */
   private void updateChannelSettings() {
      int numChannels = store_.getAxisLength(Coords.CHANNEL);
      String[] names = new String[numChannels];
      String[] officialNames = store_.getSummaryMetadata().getChannelNames();
      boolean didChange = false;
      // Construct a list of channel names.
      for (int i = 0; i < store_.getAxisLength(Coords.CHANNEL); ++i) {
         // HACK: this name must match the name derived in
         // ChannelContrastPanel.java.
         names[i] = store_.getSummaryMetadata().getSafeChannelName(i);
         if (!knownChannels_.contains(names[i])) {
            knownChannels_.add(names[i]);
            didChange = true;
         }
      }
      if (didChange) {
         DisplaySettings newSettings = ChannelSettings.updateSettings(names,
               store_.getSummaryMetadata().getChannelGroup(),
               displaySettings_);
         if (newSettings != null) {
            // There were new settings to load, i.e. we actually do have new
            // names
            setDisplaySettings(newSettings);
         }
      }
   }

   /**
    * When the summary metadata changes, make certain that certain values
    * get propagated to ImageJ.
    */
   @Subscribe
   public void onNewSummaryMetadata(NewSummaryMetadataEvent event) {
      if (ijImage_ != null) { // I.e. we've finished initializing
         setImagePlusMetadata(ijImage_);
      }
   }

   /**
    * When our Datastore goes away, we automatically close ourselves.
    */
   @Subscribe
   public void onDatastoreClosed(DatastoreClosingEvent event) {
      if (event.getDatastore() == store_) {
         forceClosed();
      }
   }

   /**
    * When our Datastore saves, we save our display settings, and update our
    * title.
    */
   @Subscribe
   public void onDatastoreFrozenPath(DatastoreFrozenEvent event) {
      try {
         String path = store_.getSavePath();
         if (path != null) {
            displaySettings_.save(path);
         }
         resetTitle();
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Failed to respond to datastore saved event");
      }
   }

   /**
    * If a DisplayWindow is topmost, return that DisplayWindow; otherwise
    * return null. This relies on ImageJ's tracking of what the topmost window
    * is (NB Java does not have any method for determining the Z order of
    * a window, so any implementation of this method would require manual
    * tracking by *someone*).
    */
   public static DisplayWindow getCurrentWindow() {
      ImageWindow current = WindowManager.getCurrentWindow();
      if (current instanceof DummyImageWindow) {
         return ((DummyImageWindow) current).getMaster();
      }
      return null;
   }

   // Letters for differentiating displays for the same dataset.
   private static final String LETTERS = "abcdefghijklmnopqrstuvwxyz";
   /**
    * We try a series of fallback measures to extract a meaningful name.
    */
   @Override
   public String getName() {
      String name = getSpecifiedName();
      List<DisplayWindow> displays = DisplayGroupManager.getDisplaysForDatastore(store_);
      if (displays.size() > 1) {
         // Append a number so we can tell different displays for the
         // same datastore apart.
         for (int i = 0; i < displays.size(); ++i) {
            if (displays.get(i) == this) {
               // Note "clever" use of modulus operator to avoid throwing
               // errors if the user duplicates a display 26 times.
               name = String.format("%s (%s)", name,
                     LETTERS.charAt(i % LETTERS.length()));
               break;
            }
         }
      }
      return name;
   }

   /**
    * Tries all methods we have of generating a specific name, and returns the
    * first that succeeds, or null on failure.
    */
   private String getSpecifiedName() {
      String name = customName_;
      if (name == null || name.contentEquals("")) {
         // The summary metadata may not have this information for older
         // datasets, but the Datastore should still know where it was saved
         // to, if the data resides on disk.
         name = store_.getSavePath();
      }
      if (name == null) {
         // Use the filename instead.
         name = store_.getSummaryMetadata().getName();
      }
      if (name == null) {
         // Must be an anonymous RAM datastore. Invent a new name and store it
         // in our static hashmap (because we can't count on storing it in
         // the datastore, which may be frozen).
         if (!displayHashToUniqueName_.containsKey(this.hashCode())) {
            displayHashToUniqueName_.put(this.hashCode(),
                  getUniqueDisplayName(this));
         }
         return displayHashToUniqueName_.get(this.hashCode());
      }
      // If it's null now, then it's an anonymous RAM datastore.
      return name;
   }

   private static int UNIQUE_ID = 1;
   /**
    * Generate a unique name for a given display. This is only invoked
    * when we have no specific name to assign to the display's data
    * (typically because it's for a RAM-based acquisition).
    */
   private static String getUniqueDisplayName(DefaultDisplayWindow display) {
      return String.format("Untitled Data #%d", UNIQUE_ID++);
   }

   @Override
   public void setCustomTitle(String title) {
      customName_ = title;
      resetTitle();
   }

   @Override
   public DisplayWindow duplicate() {
      DisplayWindow result = createDisplay(store_, controlsFactory_);
      result.setDisplaySettings(displaySettings_);
      result.setCustomTitle(customName_);
      // HACK: for some unknown reason, duplicating the display causes our own
      // LUTs to get "reset", so we have to re-apply them after duplicating.
      canvasQueue_.reapplyLUTs();
      return result;
   }

   // Implemented to help out DummyImageWindow.
   public MMImageCanvas getCanvas() {
      return canvas_;
   }

   @Override
   public String toString() {
      return String.format("<DefaultDisplayWindow named %s with unique ID %s>", getName(), hashCode());
   }

   /**
    * HACK: we call pack() at various points to ensure our layout is sensible,
    * but when we're in fullscreen mode, we don't want to re-pack the
    * full-screen window (which would make it not fullscreen), so separate
    * behavior is needed.
    */
   @Override
   public void pack() {
      if (fullScreenFrame_ != null) {
         contentsPanel_.revalidate();
      }
      else {
         super.pack();
      }
   }
}
