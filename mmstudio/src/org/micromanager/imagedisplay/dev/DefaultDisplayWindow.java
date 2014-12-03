package org.micromanager.imagedisplay.dev;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import ij.ImagePlus;
import ij.gui.ImageWindow;
import ij.Menus;
import ij.WindowManager;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import java.lang.Math;

import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

import javax.swing.event.MouseInputAdapter;
import javax.swing.JFrame;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.Image;
import org.micromanager.api.data.NewImageEvent;
import org.micromanager.api.data.SummaryMetadata;
import org.micromanager.api.display.DisplayWindow;
import org.micromanager.api.display.RequestToDrawEvent;
import org.micromanager.api.events.DatastoreClosingEvent;

import org.micromanager.data.DefaultCoords;

import org.micromanager.events.EventManager;

import org.micromanager.imagedisplay.MMCompositeImage;
import org.micromanager.imagedisplay.MMImagePlus;

import org.micromanager.LineProfile;
import org.micromanager.MMStudio;

import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.ReportingUtils;


/**
 * This class is the window that handles image viewing: it contains the
 * canvas and controls for determining which channel, Z-slice, etc. is shown. 
 * Note that it is *not* an ImageJ ImageWindow; instead, it creates a
 * DummyImageWindow instance for liaising with ImageJ. See that class for
 * more information on why we do this.
 */
public class DefaultDisplayWindow extends JFrame implements DisplayWindow {

   private Datastore store_;
   private MMVirtualStack stack_;
   private ImagePlus ijImage_;
   // This will be our intermediary with ImageJ.
   private DummyImageWindow dummyWindow_;

   private JPanel contentsPanel_;
   private JPanel canvasPanel_;
   private MMImageCanvas canvas_;
   private HyperstackControls controls_;
   private Component customControls_;
   private MultiModePanel modePanel_;
   private HistogramsPanel histograms_;
   private MetadataPanel metadata_;
   private CommentsPanel comments_;
   private OverlaysPanel overlays_;

   private final EventBus displayBus_;
   private Dimension paddedCanvasSize_;

   private CanvasUpdateThread canvasThread_;

   private boolean haveClosed_ = false;

   private static int titleID = 0;
   
   // store window location in Java Preferences
   private static final int DEFAULTPOSX = 300;
   private static final int DEFAULTPOSY = 100;
   private static Preferences displayPrefs_;
   private static final String WINDOWPOSX = "WindowPosX";
   private static final String WINDOWPOSY = "WindowPosY";
  
   /**
    * customControls is a Component that will be displayed immediately beneath
    * the HyperstackControls (the scrollbars). The creator is responsible for
    * the logic implemented by these controls. They may be null.
    */
   public DefaultDisplayWindow(Datastore store, Component customControls) {
      store_ = store;
      MMStudio.getInstance().data().associateDisplay(this, store_);
      store_.registerForEvents(this, 100);
      displayBus_ = new EventBus();
      displayBus_.register(this);
      customControls_ = customControls;

      initializePrefs();
      int posX = DEFAULTPOSX, posY = DEFAULTPOSY;
      if (displayPrefs_ != null) {
         posX = displayPrefs_.getInt(WINDOWPOSX, DEFAULTPOSX); 
         posY = displayPrefs_.getInt(WINDOWPOSY, DEFAULTPOSY);
      }
      setLocation(posX, posY);

      setBackground(MMStudio.getInstance().getBackgroundColor());
      MMStudio.getInstance().addMMBackgroundListener(this);

      resetTitle();

      if (store_.getNumImages() > 0) {
         makeWindowAndIJObjects();
      }
      setMenuBar(Menus.getMenuBar());

      EventManager.register(this);
      EventManager.post(new DefaultNewDisplayEvent(this));
   }

   /**
    * Now that there's at least one image in the Datastore, we need to create
    * our UI and the objects we'll use to communicate with ImageJ.
    */
   private void makeWindowAndIJObjects() {
      stack_ = new MMVirtualStack(store_, displayBus_);
      ijImage_ = new MMImagePlus(displayBus_);
      stack_.setImagePlus(ijImage_);
      ijImage_.setStack(generateImagePlusName(), stack_);
      ijImage_.setOpenAsHyperStack(true);
      // The ImagePlus object needs to be pseudo-polymorphic, depending on
      // the number of channels in the Datastore. However, we may not
      // have all of the channels available to us at the time this display is
      // created, so we may need to re-create things down the road.
      if (store_.getAxisLength("channel") > 1) {
         // Have multiple channels.
         shiftToCompositeImage();
      }
      if (ijImage_ instanceof MMCompositeImage) {
         ((MMCompositeImage) ijImage_).reset();
      }

      // Make the canvas thread before any of our other control objects, which
      // may perform draw requests that need to be processed.
      canvasThread_ = new CanvasUpdateThread(store_, stack_, ijImage_, displayBus_);
      canvasThread_.start();

      makeWindowControls();
      // This needs to be done after the canvas is created, but before we
      // call zoomToPreferredSize.
      dummyWindow_ = DummyImageWindow.makeWindow(ijImage_, this, displayBus_);
      zoomToPreferredSize();
      setVisible(true);
      histograms_.calcAndDisplayHistAndStats();

      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(WindowEvent event) {
            requestToClose();
         }
      });

      // Set us to draw the first image in the dataset.
      // TODO: potentially there could be no image at these Coords, though
      // that seems unlikely. Such an edge case isn't all that harmful anyway;
      // we'll just display a blank image until the user adjusts the display
      // to an image that does exist.
      DefaultCoords.Builder builder = new DefaultCoords.Builder();
      for (String axis : store_.getAxes()) {
         builder.position(axis, 0);
      }
      setDisplayedImageTo(builder.build());
   }

   /**
    * [Re]generate the controls for adjusting the display, showing metadata,
    * etc.
    */
   private void makeWindowControls() {
      if (contentsPanel_ == null) {
         contentsPanel_ = new JPanel();
      }
      contentsPanel_.removeAll();
      // Override the default layout with our own, so we can do more
      // customized controls.
      // This layout is intended to minimize distances between elements.
      contentsPanel_.setLayout(new MigLayout("insets 1, fillx, filly",
         "[grow, fill]", "[grow, fill]related[]"));

      recreateCanvas();
      // We don't want the canvas to grow, because that results in weird
      // zoom levels that make for blurry images.
      contentsPanel_.add(canvasPanel_, "align center, wrap, grow 0");

      if (controls_ == null) {
         controls_ = new HyperstackControls(store_, stack_, displayBus_,
               false);
      }
      contentsPanel_.add(controls_, "align center, wrap, growx");

      if (customControls_ != null) {
         contentsPanel_.add(customControls_, "align center, wrap, growx");
      }

      if (modePanel_ == null) {
         modePanel_ = new MultiModePanel(displayBus_);

         DisplaySettingsPanel settings = new DisplaySettingsPanel(
            store_, ijImage_, displayBus_);
         modePanel_.addMode("Settings", settings);

         histograms_ = new HistogramsPanel(store_, stack_, ijImage_,
               displayBus_);
         histograms_.setMinimumSize(new java.awt.Dimension(280, 0));
         modePanel_.addMode("Contrast", histograms_);

         metadata_ = new MetadataPanel(store_);
         modePanel_.addMode("Metadata", metadata_);

         comments_ = new CommentsPanel(store_, stack_);
         modePanel_.addMode("Comments", comments_);

         overlays_ = new OverlaysPanel(store_, stack_, ijImage_, displayBus_);
         modePanel_.addMode("Overlays", overlays_);
      }

      contentsPanel_.add(modePanel_, "dock east, growy");

      add(contentsPanel_);
      Insets insets = getInsets();
      Dimension size = contentsPanel_.getMinimumSize();
      setMinimumSize(new Dimension(
            insets.left + insets.right + size.width,
            insets.top + insets.bottom + size.height));
      pack();
   }

   /**
    * Re-generate our image canvas and canvas panel, along with resize logic.
    */
   private void recreateCanvas() {
      canvas_ = new MMImageCanvas(ijImage_, displayBus_);
      
      // HACK: set the minimum size. If we don't do this, then the canvas
      // doesn't shrink properly when the window size is reduced. Why?!
      canvas_.setMinimumSize(new Dimension(16, 16));
      // Wrap the canvas in a subpanel so that we can get events when it
      // resizes.
      canvasPanel_ = new JPanel();
      canvasPanel_.setLayout(new MigLayout("insets 0, fill"));
      canvasPanel_.add(canvas_);

      // Propagate resizing to the canvas, adjusting the view rectangle.
      canvasPanel_.addComponentListener(new ComponentAdapter() {
         @Override
         public void componentResized(ComponentEvent e) {
            Dimension size = canvasPanel_.getSize();
            double dataAspect = ((double) ijImage_.getWidth()) / ijImage_.getHeight();
            double viewAspect = ((double) size.width) / size.height;
            // Derive canvas view width/height based on maximum available space
            // along the appropriate axis.
            int viewWidth = size.width;
            int viewHeight = size.height;
            if (viewAspect > dataAspect) { // Wide view; Y constrains growth
               viewWidth = (int) (viewHeight * dataAspect);
            }
            else { // Tall view; X constrains growth
               viewHeight = (int) (viewWidth / dataAspect);
            }
            canvas_.setDrawingSize((int) Math.ceil(viewWidth),
               (int) Math.ceil(viewHeight));
            // Reset the "source rect", i.e. the sub-area being viewed when
            // the image won't fit into the window. Try to maintain the same
            // center as the current rect has.
            // Fun fact: there's setSourceRect and setSrcRect, but no
            // getSourceRect.
            Rectangle curRect = canvas_.getSrcRect();
            int xCenter = curRect.x + (curRect.width / 2);
            int yCenter = curRect.y + (curRect.height / 2);
            double curMag = canvas_.getMagnification();
            int newWidth = (int) Math.ceil(viewWidth / curMag);
            int newHeight = (int) Math.ceil(viewHeight / curMag);
            int xCorner = xCenter - newWidth / 2;
            int yCorner = yCenter - newHeight / 2;
            Rectangle viewRect = new Rectangle(xCorner, yCorner, 
                  newWidth, newHeight);
            canvas_.setSourceRect(viewRect);
            doLayout();
         }
      });

      // Add a listener so we can update the histogram when an ROI is drawn.
      canvas_.addMouseListener(new MouseInputAdapter() {
         @Override
         public void mouseReleased(MouseEvent me) {
            if (ijImage_ instanceof MMCompositeImage) {
               ((MMCompositeImage) ijImage_).updateAndDraw(true);
            } else {
               ijImage_.updateAndDraw();
            }
         }
      });
   }

   private void resetTitle() {
      SummaryMetadata summary = store_.getSummaryMetadata();
      String filename = summary.getFileName();
      String title = "MM image display";
      if (filename != null) {
         title += " (" + filename + ")";
      }
      setTitle(title);
   }

   /**
    * HACK: Override painting of the ImageWindow, because we need it to *not*
    * draw the canvas border, but still need it to draw the info text at the
    * top of the window. It can't draw the border properly anyway, since the
    * canvas is now contained in a JPanel and the canvas's size is such that
    * if any other entity draws the border, the canvas will "shadow" the 
    * border and make it largely invisible. Instead, the canvas draws the
    * border itself.
    */
   @Override
   public void paint(Graphics g) {
      // Manually blank the background of the info text. Normally this would
      // be done for us by Component.update(), but we're overriding that
      // behavior (see below).
      // HACK: the height of the rect to clear here is basically empirically-
      // derived, which is of course an incredibly brittle way to run things.
      g.clearRect(0, 0, getWidth(), 35);
      // This is kind of a dumb way to get the text we need, but hey, it works.
      // TODO: how can dummyWindow_ be null here? I started getting
      // NullPointerExceptions once I integrated the MDA into the new system.
      if (dummyWindow_ != null) {
         dummyWindow_.drawInfo(g);
      }
      super.paint(g);
   }

   /**
    * Keep class specific preferences to store window location
    */
   private void initializePrefs() {
      if (displayPrefs_ == null) {
         try {
            displayPrefs_ = Preferences.userNodeForPackage(getClass());
         } catch (Exception e) {
            ReportingUtils.logError(e);
         }
      }
   }
   
   /**
    * Set our canvas' magnification based on the preferred window magnification.
    * Also sets the position of the window, based on the position of the last
    * closed window.
    * 
    */
   public void zoomToPreferredSize() {
      Point location = getLocation();

      double mag = MMStudio.getInstance().getPreferredWindowMag();

      // Use approximation here because ImageJ has fixed allowed magnification
      // levels and we want to be able to be a bit more approximate and snap
      // to the closest allowed magnification. 
      if (mag < canvas_.getMagnification()) {
         while (mag < canvas_.getMagnification() &&
               Math.abs(mag - canvas_.getMagnification()) > .01) {
            canvas_.zoomOut(canvas_.getWidth() / 2, canvas_.getHeight() / 2);
         }
      } else if (mag > canvas_.getMagnification()) {
         while (mag > canvas_.getMagnification() &&
               Math.abs(mag - canvas_.getMagnification()) > .01) {
            canvas_.zoomIn(canvas_.getWidth() / 2, canvas_.getHeight() / 2);
         }
      }

      //Make sure the window is fully on the screen
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      Point newLocation = new Point(location.x,location.y);
      if (newLocation.x + getWidth() > screenSize.width && getWidth() < screenSize.width) {
          newLocation.x = screenSize.width - getWidth();
      }
      if (newLocation.y + getHeight() > screenSize.height && getHeight() < screenSize.height) {
          newLocation.y = screenSize.height - getHeight();
      }

      setLocation(newLocation);
   }

   /**
    * We've discovered that we need to represent a multichannel image.
    */
   private void shiftToCompositeImage() {
      // TODO: assuming mode 1 for now.
      ijImage_ = new MMCompositeImage(ijImage_, 1, ijImage_.getTitle(),
            displayBus_);
      ijImage_.setOpenAsHyperStack(true);
      MMCompositeImage composite = (MMCompositeImage) ijImage_;
      int numChannels = store_.getAxisLength("channel");
      composite.setNChannelsUnverified(numChannels);
      composite.reset();
      displayBus_.post(new DefaultNewImagePlusEvent(ijImage_));
   }

   /**   
    * Our layout has changed and we need to repack.
    */         
   @Subscribe
   public void onLayoutChanged(LayoutChangedEvent event) {
      // This is necessary to get the window to notice changes to components
      // contained within it (due to AWT/Swing mixing?).
      validate();
      pack();
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
    * Manually display the image at the specified coordinates.
    */
   @Override
   public void setDisplayedImageTo(Coords coords) {
      canvasThread_.addCoords(coords);
   }

   @Override
   public void displayStatusString(String status) {
      displayBus_.post(new StatusEvent(status));
   }
      
   @Override
   public void addControlPanel(String label, Component widget) {
      modePanel_.addMode(label, widget);
   }

   @Override
   public Datastore getDatastore() {
      return store_;
   }

   @Override
   public List<Image> getDisplayedImages() {
      ArrayList<Image> result = new ArrayList<Image>();
      Coords curCoords = stack_.getCurrentImageCoords();
      for (int i = 0; i < store_.getAxisLength("channel"); ++i) {
         result.add(store_.getImage(curCoords.copy().position("channel", i).build()));
      }
      if (result.size() == 0) {
         // No "channel" axis; just return the current image.
         result.add(store_.getImage(curCoords));
      }
      return result;
   }

   @Override
   public boolean requestToClose() {
      displayBus_.post(new RequestToCloseEvent(this));
      return getIsClosed();
   }

   // TODO: when the last display window is removed, we should really be
   // calling the Datastore.close() method. How do we track that the last
   // display window is really gone, though?
   @Override
   public void forceClosed() {
      if (haveClosed_) {
         // Only ever call this method once.
         return;
      }
      canvasThread_.stopDisplayUpdates();
      // Note: we don't join the canvas thread here because this thread is
      // presumably the EDT, and the canvas thread also does actions in the
      // EDT, so there's some deadlock potential.
      controls_.cleanup();
      histograms_.cleanup();
      MMStudio studio = MMStudio.getInstance();
      studio.data().removeDisplay(this, store_);
      store_.unregisterForEvents(this);
      studio.removeMMBackgroundListener(this);
      try {
         dummyWindow_.dispose();
      } catch (NullPointerException ex) {
         ReportingUtils.showError(ex, "Null pointer error in ImageJ code while closing window");
      }
      dispose();
      haveClosed_ = true;
   }

   @Override
   public boolean getIsClosed() {
      // TODO: is this a proper indicator for if the window is closed?
      return !isVisible();
   }

   @Override
   public ImageWindow getImageWindow() {
      return dummyWindow_;
   }

   @Override
   public void registerForEvents(Object obj) {
      displayBus_.register(obj);
   }

   @Override
   public void unregisterForEvents(Object obj) {
      displayBus_.unregister(obj);
   }

   @Subscribe
   public void onPixelsSet(CanvasUpdateThread.PixelsSetEvent event) {
      try {
         histograms_.calcAndDisplayHistAndStats();
         metadata_.imageChangedUpdate(event.getImage());
         // TODO: I think this means we're on top, but I'm not certain.
         if (isFocusableWindow()) {
            LineProfile.updateLineProfile();
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Failed to respond properly to pixels-set event");
      }
   }

   /**
    * Datastore has received a new image; display it, and adjust our
    * ImageJ object if necessary.
    */
   @Subscribe
   public void onNewImage(final NewImageEvent event) {
      try {
         if (dummyWindow_ == null) {
            // Time to make our components, but we should only do so in the EDT.
            final DefaultDisplayWindow thisWindow = this;
            try {
               GUIUtils.invokeAndWait(new Runnable() {
                  @Override
                  public void run() {
                     thisWindow.makeWindowAndIJObjects();
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
         // Check if we're transitioning from grayscale to multi-channel at this
         // time.
         int imageChannel = image.getCoords().getPositionAt("channel");
         if (!(ijImage_ instanceof MMCompositeImage) &&
               imageChannel > 0) {
            // Have multiple channels.
            shiftToCompositeImage();
            makeWindowControls();
            histograms_.calcAndDisplayHistAndStats();
         }
         if (ijImage_ instanceof MMCompositeImage) {
            // Verify that ImageJ has the right number of channels.
            int numChannels = store_.getAxisLength("channel");
            MMCompositeImage composite = (MMCompositeImage) ijImage_;
            composite.setNChannelsUnverified(numChannels);
            composite.reset();
         }
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Couldn't display new image");
      }
   }

   @Subscribe
   public void onDatastoreClosed(DatastoreClosingEvent event) {
      if (event.getDatastore() == store_) {
         forceClosed();
      }
   }

   public static DisplayWindow getCurrentWindow() {
      ImageWindow current = WindowManager.getCurrentWindow();
      if (current instanceof DummyImageWindow) {
         return ((DummyImageWindow) current).getMaster();
      }
      return null;
   }

   public static List<DisplayWindow> getAllImageWindows() {
      ArrayList<DisplayWindow> result = new ArrayList<DisplayWindow>();
      int[] plusIDs = WindowManager.getIDList();
      for (int id : plusIDs) {
         ImagePlus plus = WindowManager.getImage(id);
         ImageWindow window = plus.getWindow();
         if (window instanceof DisplayWindow) {
            result.add((DisplayWindow) window);
         }
      }
      return result;
   }

   /**
    * Generate a unique name for our ImagePlus object.
    */
   private static String generateImagePlusName() {
      titleID++;
      return String.format("MM dataset %d", titleID);
   }

   // Implemented to help out DummyImageWindow.
   public MMImageCanvas getCanvas() {
      return canvas_;
   }

   /**
    * HACK HACK HACK etc you get the idea.
    * Manually re-size components, and set our own size. Otherwise, changes in
    * the sizes of components we contain end up not affecting our own size
    * properly.
    * Additionally, we need to pad out the canvas object's size by 2px;
    * otherwise, at certain zoom levels (33.333% and divisors of same,
    * basically any zoom level that can't be precisely expressed in decimal
    * form), it doesn't take up *quite* enough space to draw itself, which
    * means we get the "zoom indicator" overlay and part of the canvas
    * isn't drawn.
    *
    * TODO: suspect this function has similar bugs as were fixed in r14511.
    */
   @Override
   public void pack() {
      // Ensure all components have the right sizes.
      for (Component c : getComponents()) {
         c.setSize(c.getPreferredSize());
      }
      setSize(getPreferredSize());
      super.pack();

      // Ensure the canvas is padded properly.
      Dimension canvasSize = canvas_.getSize();
      if (paddedCanvasSize_ != null &&
            paddedCanvasSize_.width == canvasSize.width &&
            paddedCanvasSize_.height == canvasSize.height) {
         // Canvas is already padded, so we're done here.
         return;
      }

      // Pad the canvas, and then pad ourselves so there's room for it.
      canvas_.setDrawingSize(canvasSize.width + 2, canvasSize.height + 2);
      // ImageJ hacks getPreferredSize to return the "drawing size"
      // (there's no getDrawingSize() method).
      paddedCanvasSize_ = canvas_.getPreferredSize();
      Dimension ourSize = getSize();
      setSize(ourSize.width + 2, ourSize.height + 2);
      super.pack();
   }
}
