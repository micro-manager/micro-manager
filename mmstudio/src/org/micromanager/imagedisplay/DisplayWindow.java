package org.micromanager.imagedisplay;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import ij.CompositeImage;
import ij.gui.GUI;
import ij.IJ;
import ij.ImagePlus;
import ij.gui.ImageCanvas;
import ij.gui.StackWindow;
import ij.gui.Toolbar;
import ij.Prefs;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.lang.Math;
import java.util.prefs.Preferences;
import java.util.Timer;
import java.util.TimerTask;

import javax.swing.event.MouseInputAdapter;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.MMStudio;
import org.micromanager.internalinterfaces.DisplayControls;
import org.micromanager.utils.CanvasPaintPending;
import org.micromanager.utils.GUIUtils;
import org.micromanager.utils.ReportingUtils;


/**
 * This class is the Frame that handles image viewing: it contains the 
 * canvas and controls for determining which channel, Z-slice, etc. is shown. 
 * HACK: we have overridden getComponents() on this function to "fix" bugs
 * in other bits of code; see that function's comment.
 */
public class DisplayWindow extends StackWindow {

   private boolean closed_ = false;
   private final EventBus bus_;
   private ImagePlus plus_;
   private JPanel canvasPanel_;
   private Dimension canvasSizeLastPack_;
   
   // store window location in Java Preferences
   private static final int DEFAULTPOSX = 300;
   private static final int DEFAULTPOSY = 100;
   private static Preferences displayPrefs_;
   private static final String WINDOWPOSX = "WindowPosX";
   private static final String WINDOWPOSY = "WindowPosY";
   
   // This class is used to signal that a window is closing.
   public static class RequestToCloseEvent {
      public DisplayWindow window_;
      public RequestToCloseEvent(DisplayWindow window) {
         if (displayPrefs_ != null) {
            displayPrefs_.putInt(WINDOWPOSX, window.getLocation().x);
            displayPrefs_.putInt(WINDOWPOSY, window.getLocation().y);
         }
         window_ = window;
      }
   };

  
   
   public DisplayWindow(final ImagePlus plus, DisplayControls controls, 
         final EventBus bus) {
      super(plus);
      plus_ = plus;
      bus_ = bus;
      initializePrefs();
      int posX = DEFAULTPOSX, posY = DEFAULTPOSY;
      if (displayPrefs_ != null) {
         posX = displayPrefs_.getInt(WINDOWPOSX, DEFAULTPOSX); 
         posY = displayPrefs_.getInt(WINDOWPOSY, DEFAULTPOSY);
      }
      setLocation(posX, posY);

      MMStudio.getInstance().addMMBackgroundListener(this);
      setBackground(MMStudio.getInstance().getBackgroundColor());
      bus_.register(this);
      
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
      // Override the default layout with our own, so we can do more 
      // customized controls. 
      // This layout is intended to minimize distances between elements.
      setLayout(new MigLayout("insets 1, fillx, filly",
         "[grow, fill]", "[grow, fill]related[]"));
      // Re-create the ImageJ canvas. We need it to manually draw its own
      // border (by comparison, in standard ImageJ the ImageWindow draws the
      // border), because we're changing the layout heirarchy so the 
      // ImageWindow doesn't draw it in the right place. Thus, we have to 
      // override its paint() method.
      // TODO: since we're overriding the canvas anyway, why not replace
      // its setMagnification2() method, which overrides our nice window
      // titles?
      remove(ic);
      // Ensure that all references to this canvas are removed.
      CanvasPaintPending.removeAllPaintPending(ic);
      ic = new ImageCanvas(plus_) {
         @Override
         public void paint(Graphics g) {
            super.paint(g);
            // Determine the color to use (default is black).
            if (plus_.isComposite()) {
               Color color = ((CompositeImage) plus_).getChannelColor();
               // Re-implement the same hack that ImageWindow uses in its
               // paint() method...
               if (Color.green.equals(color)) {
                  color = new Color(0, 180, 0);
               }
               g.setColor(color);
            }
            Rectangle rect = getBounds();
            // Tighten the rect down to what the canvas is actually drawing, as
            // opposed to the space it is taking up in the window as a 
            // Component.
            int drawnWidth = (int) (plus_.getWidth() * ic.getMagnification());
            int drawnHeight = (int) (plus_.getHeight() * ic.getMagnification());
            int widthSlop = (rect.width - drawnWidth) / 2;
            int heightSlop = (rect.height - drawnHeight) / 2;
            rect.x += widthSlop;
            rect.y += heightSlop;
            rect.width = drawnWidth;
            rect.height = drawnHeight;
            // Not sure why we need to do this exactly, except that if we don't
            // the rectangle draws in the wrong place on narrow windows.
            rect.y -= getBounds().y;
            if (!Prefs.noBorder && !IJ.isLinux()) {
               g.drawRect(rect.x - 1, rect.y - 1,
                     rect.width + 1, rect.height + 1);
            }
            // Blank out everything outside of the drawn canvas rect.
            // If we don't do this, then when resizing the window, parts of
            // the area outside the canvas will show parts of the old canvas.
            // 1-pixel fiddle factor to avoid blanking the border we just drew.
            Dimension size = getSize();
            g.clearRect(0, 0, size.width, heightSlop - 1);
            g.clearRect(0, 0, widthSlop - 1, size.height);
            g.clearRect(widthSlop + drawnWidth + 1, 0,
                  widthSlop, size.height);
            g.clearRect(0, heightSlop + drawnHeight + 1,
                  size.width, heightSlop);
         }

         /**
          * This padding causes us to avoid erroneously showing the zoom
          * indicator, and ensures there's enough space to draw the border.
          */
         @Override
         public Dimension getPreferredSize() {
            return new Dimension(dstWidth + 2, dstHeight + 2);
         }
      };
      // HACK: set the minimum size. If we don't do this, then the canvas
      // doesn't shrink properly when the window size is reduced. Why?!
      ic.setMinimumSize(new Dimension(16, 16));
      // Wrap the canvas in a subpanel so that we can get events when it
      // resizes.
      canvasPanel_ = new JPanel();
      canvasPanel_.setLayout(new MigLayout("insets 0, fill"));
      MMStudio.getInstance().addMMBackgroundListener(canvasPanel_);
      canvasPanel_.setBackground(MMStudio.getInstance().getBackgroundColor());
      canvasPanel_.add(ic);
      add(canvasPanel_, "align center, wrap");
      add(controls, "align center, wrap, growx");

      // Propagate resizing to the canvas, adjusting the view rectangle.
      // Note that this occasionally results in the canvas being 2px too
      // small to show everything, causing the "zoom indicator" overlay to
      // be drawn semi-pointlessly.
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

      // Add a listener so we can update the histogram when an ROI is drawn,
      // and to override the title-setting behavior of ImagePlus when the 
      // magnification tool is used.
      ic.addMouseListener(new MouseInputAdapter() {
         @Override
         public void mousePressed(MouseEvent me) {
            if (Toolbar.getToolId() == 11) { // zoom tool selected
               bus.post(new UpdateTitleEvent());
            }
         }

         @Override
         public void mouseReleased(MouseEvent me) {
            if (plus_ instanceof MMCompositeImage) {
               ((MMCompositeImage) plus_).updateAndDraw(true);
            } else {
               plus_.updateAndDraw();
            }
         }
      });

      zoomToPreferredSize();
      pack();
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
   private void zoomToPreferredSize() {
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

   @Override
   public boolean close() {
      windowClosing(null);
      return closed_;
   }

   @Override
   public void windowClosing(WindowEvent e) {
      if (!closed_) {
         bus_.post(new RequestToCloseEvent(this));
      }
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

   // Force this window to go away.
   public void forceClosed() {
      try {
         super.close();
      } catch (NullPointerException ex) {
         ReportingUtils.showError(ex, "Null pointer error in ImageJ code while closing window");
      }
      MMStudio.getInstance().removeMMBackgroundListener(this);
      MMStudio.getInstance().removeMMBackgroundListener(canvasPanel_);
      closed_ = true;
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
    * HACK HACK HACK etc. you get the idea.
    * We have several goals to accomplish when repacking the window:
    * - Constrain the canvas' size so that it fits
    * - Constrain our window size so there isn't a lot of wasted space
    */
   @Override
   public void pack() {
      // Reduce the canvas until it can fit into the display this
      // window is on, with some padding for our own controls of course.
      Rectangle displayBounds = GUIUtils.getMaxWindowSizeForPoint(getX(), getY());
      int displayWidth = displayBounds.width;
      int displayHeight = displayBounds.height;

      if (canvasSizeLastPack_ == null) {
         canvasSizeLastPack_ = ic.getSize();
      }
      Dimension origWindowSize = getSize();
      Dimension desiredCanvasSize = ic.getPreferredSize();
      if (desiredCanvasSize.width + 50 > displayWidth ||
            desiredCanvasSize.height + 150 > displayHeight) {
         // Can't fit the canvas into the available space, so zoom out.
         ic.zoomOut(displayWidth - 50, displayHeight - 150);
      }
      // Derive our own size based on the canvas size plus padding. We want
      // to preserve the same difference in size between us and the canvas
      // that we had before resizing.
      int deltaWidth = origWindowSize.width - canvasSizeLastPack_.width;
      int deltaHeight = origWindowSize.height - canvasSizeLastPack_.height;
      if (canvasPanel_ != null) {
         // Ensure the canvas panel changes size as the canvas does, so that
         // it doesn't artificially constrain our own resizing.
         canvasPanel_.setSize(ic.getSize());
      }
      setSize(ic.getSize().width + deltaWidth,
            ic.getSize().height + deltaHeight);
      canvasSizeLastPack_ = ic.getSize();
      super.pack();
   }
}
