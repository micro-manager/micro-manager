package org.micromanager.imagedisplay;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import ij.ImagePlus;
import ij.gui.StackWindow;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.Point;
import java.awt.Toolkit;
import java.lang.StackTraceElement;
import java.lang.Thread;

import javax.swing.SwingConstants;
import javax.swing.event.MouseInputAdapter;
import javax.swing.JLabel;
import javax.swing.plaf.basic.BasicArrowButton;

import net.miginfocom.swing.MigLayout;

import org.micromanager.internalinterfaces.DisplayControls;
import org.micromanager.MMStudioMainFrame;
import org.micromanager.utils.ReportingUtils;


/**
 * This class is the Frame that handles image viewing: it contains the 
 * canvas and controls for determining which channel, Z-slice, etc. is shown. 
 * HACK: we have overridden getComponents() on this function to "fix" bugs
 * in other bits of code; see that function's comment.
 */
public class DisplayWindow extends StackWindow {

   private boolean closed_ = false;
   private EventBus bus_;

   // This class is used to signal that a window is closing.
   public static class RequestToCloseEvent {
      public DisplayWindow window_;
      public RequestToCloseEvent(DisplayWindow window) {
         window_ = window;
      }
   };

   public DisplayWindow(final ImagePlus ip, DisplayControls controls, 
         EventBus bus) {
      super(ip);
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
      setLayout(new MigLayout("insets 1, fillx"));
      // Re-add the ImageJ canvas.
      add(ic, "align center, wrap");
      add(controls, "align center, wrap, growx");

      pack();

      // Add a listener so we can update the histogram when an ROI is drawn.
      ic.addMouseListener(new MouseInputAdapter() {
         @Override
         public void mouseReleased(MouseEvent me) {
            if (ip instanceof MMCompositeImage) {
               ((MMCompositeImage) ip).updateAndDraw(true);
            } else {
               ip.updateAndDraw();
            }
         }
      });

      // Repack on window size change, since our controls may have also changed
      // size.
      addComponentListener(new ComponentAdapter() {
         public void componentResized(ComponentEvent e) {
            pack();
         }
      });

      setBackground(MMStudioMainFrame.getInstance().getBackgroundColor());
      MMStudioMainFrame.getInstance().addMMBackgroundListener(this);

      bus_ = bus;
      bus.register(this);

      zoomToPreferredSize();
   }

   /**
    * Set our canvas' magnification based on the preferred window
    * magnification.
    */
   private void zoomToPreferredSize() {
      Point location = getLocation();
      setLocation(new Point(0,0));

      double mag = MMStudioMainFrame.getInstance().getPreferredWindowMag();

      if (mag < ic.getMagnification()) {
         while (mag < ic.getMagnification()) {
            ic.zoomOut(ic.getWidth() / 2, ic.getHeight() / 2);
         }
      } else if (mag > ic.getMagnification()) {

         while (mag > ic.getMagnification()) {
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
    * Our controls have changed size; repack the window.
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
      MMStudioMainFrame.getInstance().removeMMBackgroundListener(this);
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
}
