package org.micromanager.imagedisplay.dev;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import ij.CompositeImage;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.ImageWindow;
import ij.gui.StackWindow;
import ij.gui.Toolbar;
import ij.Prefs;
import ij.WindowManager;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Panel;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;

import java.lang.Math;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;
import java.util.HashMap;
import java.util.List;
import java.util.prefs.Preferences;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.SwingUtilities;
import javax.swing.event.MouseInputAdapter;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.api.data.Coords;
import org.micromanager.api.data.Datastore;
import org.micromanager.api.data.DisplayWindow;
import org.micromanager.api.data.Image;
import org.micromanager.api.data.NewImageEvent;
import org.micromanager.api.data.SummaryMetadata;
import org.micromanager.api.display.DrawEvent;

import org.micromanager.imagedisplay.CanvasDrawEvent;
import org.micromanager.imagedisplay.FPSEvent;
import org.micromanager.imagedisplay.IMMImagePlus;
import org.micromanager.imagedisplay.MMCompositeImage;
import org.micromanager.imagedisplay.MMImagePlus;

import org.micromanager.LineProfile;
import org.micromanager.MMStudio;

import org.micromanager.utils.ReportingUtils;


/**
 * This class is the Frame that handles image viewing: it contains the 
 * canvas and controls for determining which channel, Z-slice, etc. is shown. 
 * HACK: we have overridden getComponents() on this function to "fix" bugs
 * in other bits of code; see that function's comment.
 */
public class DefaultDisplayWindow extends StackWindow implements DisplayWindow {

   private Datastore store_;
   private MMVirtualStack stack_;
   private ImagePlus ijImage_;
   private MMImagePlus plus_;

   private JPanel canvasPanel_;
   private HyperstackControls controls_;
   private MultiModePanel modePanel_;
   private HistogramsPanel histograms_;
   private MetadataPanel metadata_;
   private CommentsPanel comments_;

   private boolean closed_ = false;
   private final EventBus displayBus_;
   private Dimension paddedCanvasSize_;

   private CanvasUpdateThread canvasThread_;
   
   // store window location in Java Preferences
   private static final int DEFAULTPOSX = 300;
   private static final int DEFAULTPOSY = 100;
   private static Preferences displayPrefs_;
   private static final String WINDOWPOSX = "WindowPosX";
   private static final String WINDOWPOSY = "WindowPosY";
   
   public DefaultDisplayWindow(Datastore store, MMVirtualStack stack,
            MMImagePlus plus, EventBus bus) {
      super(plus);
      store_ = store;
      store_.associateDisplay(this);
      store_.registerForEvents(this, 100);
      stack_ = stack;
      plus_ = plus;
      ijImage_ = plus_;
      displayBus_ = bus;
      displayBus_.register(this);

      // The ImagePlus object needs to be pseudo-polymorphic, depending on
      // the number of channels in the Datastore. However, we may not
      // have all of the channels available to us at the time this display is
      // created, so we may need to re-create things down the road.
      if (store_.getMaxIndex("channel") > 0) {
         // Have multiple channels.
         shiftToCompositeImage();
      }
      if (ijImage_ instanceof MMCompositeImage) {
         ((MMCompositeImage) ijImage_).reset();
      }

      initializePrefs();
      int posX = DEFAULTPOSX, posY = DEFAULTPOSY;
      if (displayPrefs_ != null) {
         posX = displayPrefs_.getInt(WINDOWPOSX, DEFAULTPOSX); 
         posY = displayPrefs_.getInt(WINDOWPOSY, DEFAULTPOSY);
      }
      setLocation(posX, posY);
      
      // HACK: hide ImageJ's native scrollbars; we provide our own.
      if (cSelector != null) {
         remove(cSelector);
      }
      if (tSelector != null) {
         remove(tSelector);
      }
      if (zSelector != null) {
         remove(zSelector);
      }

      setBackground(MMStudio.getInstance().getBackgroundColor());
      MMStudio.getInstance().addMMBackgroundListener(this);

      resetTitle();

      makeWindowControls();
      zoomToPreferredSize();
      setIJBounds();
      histograms_.calcAndDisplayHistAndStats(true);
      pack();
      
      canvasThread_ = new CanvasUpdateThread(store_, stack_, plus_, displayBus_);
      canvasThread_.start();
   }

   /**
    * [Re]generate the controls for adjusting the display, showing metadata,
    * etc.
    */
   private void makeWindowControls() {
      removeAll();
      // Override the default layout with our own, so we can do more
      // customized controls.
      // This layout is intended to minimize distances between elements.
      setLayout(new MigLayout("insets 1, fillx, filly",
         "[grow, fill]", "[grow, fill]related[]"));

      recreateCanvas();
      add(canvasPanel_, "align center, wrap");

      controls_ = new HyperstackControls(store_, stack_, displayBus_,
            false);
      add(controls_, "align center, wrap, growx");

      modePanel_ = new MultiModePanel(displayBus_);

      DisplaySettingsPanel settings = new DisplaySettingsPanel(
            store_, ijImage_);
      modePanel_.addMode("Settings", settings);

      histograms_ = new HistogramsPanel(store_, stack_, ijImage_, displayBus_);
      histograms_.setMinimumSize(new java.awt.Dimension(280, 0));
      modePanel_.addMode("Contrast", histograms_);

      metadata_ = new MetadataPanel(store_);
      modePanel_.addMode("Metadata", metadata_);

      comments_ = new CommentsPanel(store_, stack_);
      modePanel_.addMode("Comments", comments_);

      modePanel_.addMode("Overlays",
            new OverlaysPanel(store_, stack_, ijImage_, displayBus_));

      add(modePanel_, "dock east, growy");

      pack();
   }

   /**
    * Re-generate our image canvas and canvas panel, along with resize logic.
    */
   private void recreateCanvas() {
      ic = new MMImageCanvas(ijImage_, plus_, displayBus_);
      
      // HACK: set the minimum size. If we don't do this, then the canvas
      // doesn't shrink properly when the window size is reduced. Why?!
      ic.setMinimumSize(new Dimension(16, 16));
      // Wrap the canvas in a subpanel so that we can get events when it
      // resizes.
      canvasPanel_ = new JPanel();
      canvasPanel_.setLayout(new MigLayout("insets 0, fill"));
      canvasPanel_.add(ic);

      // Propagate resizing to the canvas, adjusting the view rectangle.
      canvasPanel_.addComponentListener(new ComponentAdapter() {
         @Override
         public void componentResized(ComponentEvent e) {
            Dimension size = canvasPanel_.getSize();
            double dataAspect = ((double) plus_.getWidth()) / plus_.getHeight();
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
            ic.setDrawingSize((int) Math.ceil(viewWidth),
               (int) Math.ceil(viewHeight));
            // Reset the "source rect", i.e. the sub-area being viewed when
            // the image won't fit into the window. Try to maintain the same
            // center as the current rect has.
            // Fun fact: there's setSourceRect and setSrcRect, but no
            // getSourceRect.
            Rectangle curRect = ic.getSrcRect();
            int xCenter = curRect.x + (curRect.width / 2);
            int yCenter = curRect.y + (curRect.height / 2);
            double curMag = ic.getMagnification();
            int newWidth = (int) Math.ceil(viewWidth / curMag);
            int newHeight = (int) Math.ceil(viewHeight / curMag);
            int xCorner = xCenter - newWidth / 2;
            int yCorner = yCenter - newHeight / 2;
            Rectangle viewRect = new Rectangle(xCorner, yCorner, 
                  newWidth, newHeight);
            ic.setSourceRect(viewRect);
            doLayout();
         }
      });

      // Add a listener so we can update the histogram when an ROI is drawn.
      ic.addMouseListener(new MouseInputAdapter() {
         @Override
         public void mouseReleased(MouseEvent me) {
            if (ijImage_ instanceof MMCompositeImage) {
               ((MMCompositeImage) ijImage_).updateAndDraw(true);
            } else {
               plus_.updateAndDraw();
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
    * border and make it largely invisible.
    */
   public void paint(Graphics g) {
      drawInfo(g);
      // Propagate painting to our sub-components.
      for (Component c : getComponents()) {
         c.repaint();
      }
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
      if (mag < ic.getMagnification()) {
         while (mag < ic.getMagnification() &&
               Math.abs(mag - ic.getMagnification()) > .01) {
            ic.zoomOut(ic.getWidth() / 2, ic.getHeight() / 2);
         }
      } else if (mag > ic.getMagnification()) {
         while (mag > ic.getMagnification() &&
               Math.abs(mag - ic.getMagnification()) > .01) {
            ic.zoomIn(ic.getWidth() / 2, ic.getHeight() / 2);
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
    * Our controls have changed size; repack the window to ensure all controls
    * are displayed.
    * @param event
    */
   @Subscribe
   public void onLayoutChange(ScrollerPanel.LayoutChangedEvent event) {
      pack();
   }

   @Override
   public void windowClosed(WindowEvent E) {
      try {
         super.windowClosed(E);
      } catch (NullPointerException ex) {
            ReportingUtils.showError(ex, "Null pointer error in ImageJ code while closing window");
      }
   }

   @Override
   public void windowActivated(WindowEvent e) {
      if (!isClosed()) {
         super.windowActivated(e);
      }
   }

   /**
    * HACK HACK HACK HACK HACK HACK ACK HACK HACK HACK HACK HACK ACK HACK
    * We override this function to "fix" the Orthogonal Views plugin, which
    * assumes that item #2 in the array returned by getComponents() is the 
    * cSelector object of an ImageJ StackWindow. Of course, actually changing
    * this behavior for everyone else causes *them* to break horribly, hence
    * why we have to examine the stack trace to determine our caller. Ugh!
    * HACK HACK HACK HACK HACK HACK ACK HACK HACK HACK HACK HACK ACK HACK
    * @return 
    */
   @Override
   public Component[] getComponents() {
      StackTraceElement[] stack = Thread.currentThread().getStackTrace();
      for (StackTraceElement element : stack) {
         if (element.getClassName().contains("Orthogonal_Views")) {
            return new Component[] {ic, cSelector};
         }
      }
      return super.getComponents();
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
      Dimension canvasSize = ic.getSize();
      if (paddedCanvasSize_ != null &&
            paddedCanvasSize_.width == canvasSize.width &&
            paddedCanvasSize_.height == canvasSize.height) {
         // Canvas is already padded, so we're done here.
         return;
      }

      // Pad the canvas, and then pad ourselves so there's room for it.
      ic.setDrawingSize(canvasSize.width + 2, canvasSize.height + 2);
      // ImageJ hacks getPreferredSize to return the "drawing size"
      // (there's no getDrawingSize() method).
      paddedCanvasSize_ = ic.getPreferredSize();
      Dimension ourSize = getSize();
      setSize(ourSize.width + 2, ourSize.height + 2);
      super.pack();
   }

   /**
    * We've discovered that we need to represent a multichannel image.
    */
   private void shiftToCompositeImage() {
      // TODO: assuming mode 1 for now.
      ijImage_ = new MMCompositeImage(plus_, 1, plus_.getTitle(),
            displayBus_);
      ijImage_.setOpenAsHyperStack(true);
      MMCompositeImage composite = (MMCompositeImage) ijImage_;
      int numChannels = store_.getMaxIndex("channel") + 1;
      composite.setNChannelsUnverified(numChannels);
      composite.reset();
      stack_.setImagePlus(ijImage_);

      makeWindowControls();
      histograms_.calcAndDisplayHistAndStats(true);
   }

   // Ensure that our ImageJ object has the correct number of channels, 
   // frames, and slices.
   private void setIJBounds() {
      IMMImagePlus temp = (IMMImagePlus) ijImage_;
      int numChannels = Math.max(1, store_.getMaxIndex("channel") + 1);
      int numFrames = Math.max(1, store_.getMaxIndex("time") + 1);
      int numSlices = Math.max(1, store_.getMaxIndex("z") + 1);
      temp.setNChannelsUnverified(numChannels);
      temp.setNFramesUnverified(numFrames);
      temp.setNSlicesUnverified(numSlices);
      // TODO: VirtualAcquisitionDisplay folds "components" into channels;
      // what are components used for?
      // TODO: calling this causes ImageJ to create its own additional
      // window, which looks terrible, so we're leaving it out for now.
      //plus_.setDimensions(numChannels, numSlices, numFrames);
   }

   /**
    * Receive a new image from our controller.
    */
   public void receiveNewImage(Image image) {
      try {
         // Check if we're transitioning from grayscale to multi-channel at this
         // time.
         if (!(ijImage_ instanceof MMCompositeImage) &&
               image.getCoords().getPositionAt("channel") > 0) {
            // Have multiple channels.
            shiftToCompositeImage();
            makeWindowControls();
         }
         if (ijImage_ instanceof MMCompositeImage) {
            // Verify that ImageJ has the right number of channels.
            int numChannels = store_.getMaxIndex("channel");
            MMCompositeImage composite = (MMCompositeImage) ijImage_;
            composite.setNChannelsUnverified(numChannels);
            composite.reset();
            for (int i = 0; i < numChannels; ++i) {
               if (composite.getProcessor(i + 1) != null) {
                  composite.getProcessor(i + 1).setPixels(image.getRawPixels());
               }
            }
         }
         setIJBounds();
         canvasThread_.addCoords(image.getCoords());
      }
      catch (Exception e) {
         ReportingUtils.logError(e, "Couldn't display new image");
      }
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
   public void onDrawEvent(DrawEvent event) {
      canvasThread_.addCoords(stack_.getCurrentImageCoords());
   }

   /**
    * Manually display the image at the specified coordinates.
    */
   @Override
   public void setDisplayedImageTo(Coords coords) {
      canvasThread_.addCoords(coords);
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
   public void windowClosing(WindowEvent e) {
      if (!closed_) {
         requestToClose();
      }
   }

   @Override
   public boolean close() {
      requestToClose();
      return closed_;
   }

   @Override
   public void requestToClose() {
      displayBus_.post(new RequestToCloseEvent(this));
   }

   @Override
   public void forceClosed() {
      canvasThread_.stopDisplayUpdates();
      // Note: we don't join the canvas thread here because this thread is
      // presumably the EDT, and the canvas thread also does actions in the
      // EDT, so there's some deadlock potential.
      controls_.prepareForClose();
      histograms_.prepareForClose();
      store_.removeDisplay(this);
      store_.unregisterForEvents(this);
      MMStudio.getInstance().removeMMBackgroundListener(this);
      try {
         super.close();
      } catch (NullPointerException ex) {
         ReportingUtils.showError(ex, "Null pointer error in ImageJ code while closing window");
      }
      closed_ = true;
   }

   @Override
   public boolean getIsClosed() {
      // TODO: is this a proper indicator for if the window is closed?
      return !isVisible();
   }

   @Override
   public ImageWindow getImageWindow() {
      return this;
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
      histograms_.calcAndDisplayHistAndStats(true);
      metadata_.imageChangedUpdate(event.getImage());
      if (WindowManager.getCurrentWindow() == this) {
         LineProfile.updateLineProfile();
      }
   }

   /**
    * Datastore has received a new image; display it.
    */
   @Subscribe
   public void onNewImage(NewImageEvent event) {
      receiveNewImage(event.getImage());
   }

   public static DisplayWindow getCurrentWindow() {
      ImageWindow current = WindowManager.getCurrentWindow();
      if (current instanceof DisplayWindow) {
         return (DisplayWindow) current;
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
}
