package org.micromanager.imageDisplay;

import com.google.common.eventbus.EventBus;
import com.google.common.eventbus.Subscribe;

import ij.ImagePlus;
import ij.gui.StackWindow;
import ij.plugin.frame.ContrastAdjuster;

import java.awt.Point;
import java.awt.event.WindowEvent;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

import org.micromanager.MMStudioMainFrame;
import org.micromanager.acquisition.TaggedImageStorageDiskDefault;
import org.micromanager.acquisition.TaggedImageStorageMultipageTiff;
import org.micromanager.utils.MMScriptException;
import org.micromanager.utils.ReportingUtils;

public class DisplayWindow extends StackWindow {

   private boolean closed_ = false;
   private boolean isAnimated_ = false;
   private EventBus bus_;
   private ImagePlus hyperImage_;

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
      hyperImage_ = ip;
      bus_ = bus;
      bus.register(this);
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
}
