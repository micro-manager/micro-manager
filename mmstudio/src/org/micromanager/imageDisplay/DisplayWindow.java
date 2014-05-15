package org.micromanager.imageDisplay;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import ij.ImagePlus;
import ij.gui.StackWindow;
import java.awt.Component;
import java.awt.event.WindowEvent;
import java.lang.StackTraceElement;
import java.lang.Thread;

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
   private boolean isAnimated_ = false;
   private EventBus bus_;

   // This class is used to signal that a window is closing.
   public class WindowClosingEvent {
      public DisplayWindow window_;
      public WindowClosingEvent(DisplayWindow window) {
         window_ = window;
      }
   };

   // This class is used to signal that animation should be turned on/off.
   public class ToggleAnimatedEvent {
      public boolean shouldSetAnimated_;
      public ToggleAnimatedEvent(boolean shouldSetAnimated) {
         shouldSetAnimated_ = shouldSetAnimated;
      }
   }

   public DisplayWindow(ImagePlus ip, EventBus bus) {
      super(ip);
      bus_ = bus;
      bus.register(this);
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
   }

   // Receive notification that animation status has changed.
   @Subscribe
   public void onSetAnimated(VirtualAcquisitionDisplay.AnimationSetEvent event) {
      isAnimated_ = event.isAnimated_;
   }

   @Override
   public boolean close() {
      windowClosing(null);
      return closed_;
   }

   @Override
   public void windowClosing(WindowEvent e) {
      if (!closed_) {
         bus_.post(new WindowClosingEvent(this));
      }
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

   @Override
   public void setAnimate(boolean b) {
      bus_.post(new ToggleAnimatedEvent(b));
   }

   @Override
   public boolean getAnimate() {
      return isAnimated_;
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
