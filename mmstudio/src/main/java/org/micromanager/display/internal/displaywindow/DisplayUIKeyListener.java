package org.micromanager.display.internal.displaywindow;

import com.google.common.eventbus.Subscribe;
import java.awt.event.KeyEvent;
import org.micromanager.data.Coords;
import org.micromanager.display.internal.event.DisplayKeyPressEvent;

/**
 * Listens for key presses on the display window and updates the display position accordingly.
 */
public class DisplayUIKeyListener {
   private final DisplayUIController displayUIController_;

   public DisplayUIKeyListener(DisplayUIController displayUIController) {
      displayUIController_ = displayUIController;
   }

   /**
    * Handles the event signalling that a key was pressed while the display had focus.
    * Currently only left and right arrow are implemented.  They will move the display
    * along the t-axis, or if t is not present, along the z-axis. If neither t nor z are
    * present, it will move along the position axis, and if that is not present either,
    * it will move along the channel axis (and do nothing if that is not present).
    * Ctrl+left and Ctrl+right will move along the z-axis., Alt+left and Alt+right will move
    * along the position axis, and Shift+left and Shift+right will move along the channel axis.
    *
    * @param dkpe information about the Key Event.
    */
   @Subscribe
   public void keyPressed(DisplayKeyPressEvent dkpe) {
      if (dkpe.wasConsumed()) {
         return;
      }
      final KeyEvent e = dkpe.getKeyEvent();
      Coords current = displayUIController_.getDisplayedImages().get(0).getCoords();
      Coords newCoords = null;
      switch (e.getKeyCode()) {
         case KeyEvent.VK_LEFT:
            if (e.isControlDown()) {
               if (current.getZ() > 0) {
                  newCoords = current.copyBuilder().z(current.getZ() - 1).build();
               }
            } else if (e.isAltDown()) {
               if (current.getP() > 0) {
                  newCoords = current.copyBuilder().p(current.getP() - 1).build();
               }
            } else if (e.isShiftDown()) {
               if (current.getC() > 0) {
                  newCoords = current.copyBuilder().c(current.getC() - 1).build();
               }
            } else if (displayUIController_.getDisplayedAxisLength(Coords.T) > 0) {
               if (current.getT() > 0) {
                  newCoords = current.copyBuilder().t(current.getT() - 1).build();
               }
            } else if (displayUIController_.getDisplayedAxisLength(Coords.Z) > 0) {
               if (current.getZ() > 0) {
                  newCoords = current.copyBuilder().z(current.getZ() - 1).build();
               }
            } else if (displayUIController_.getDisplayedAxisLength(Coords.P) > 0) {
               if (current.getP() > 0) {
                  newCoords = current.copyBuilder().p(current.getP() - 1).build();
               }
            } else if (displayUIController_.getDisplayedAxisLength(Coords.C) > 0) {
               if (current.getC() > 0) {
                  newCoords = current.copyBuilder().c(current.getC() - 1).build();
               }
            }
            break;
         case KeyEvent.VK_RIGHT:
            if (e.isControlDown()) {
               if (current.getZ() < displayUIController_.getDisplayedAxisLength(Coords.Z) - 1) {
                  newCoords = current.copyBuilder().z(current.getZ() + 1).build();
               }
            } else if (e.isAltDown()) {
               if (current.getP() < displayUIController_.getDisplayedAxisLength(Coords.P) - 1) {
                  newCoords = current.copyBuilder().p(current.getP() + 1).build();
               }
            } else if (e.isShiftDown()) {
               if (current.getC() < displayUIController_.getDisplayedAxisLength(Coords.C) - 1) {
                  newCoords = current.copyBuilder().c(current.getC() + 1).build();
               }
            } else if (displayUIController_.getDisplayedAxisLength(Coords.T) > 1) {
               if (current.getT() < (displayUIController_.getDisplayedAxisLength(Coords.T) - 1)) {
                  newCoords = current.copyBuilder().t(current.getT() + 1).build();
               }
            } else if (displayUIController_.getDisplayedAxisLength(Coords.Z) > 1) {
               if (current.getZ() < (displayUIController_.getDisplayedAxisLength(Coords.Z) - 1)) {
                  newCoords = current.copyBuilder().z(current.getZ() + 1).build();
               }
            } else if (displayUIController_.getDisplayedAxisLength(Coords.P) > 1) {
               if (current.getP() < (displayUIController_.getDisplayedAxisLength(Coords.P) - 1)) {
                  newCoords = current.copyBuilder().p(current.getP() + 1).build();
               }
            } else if (displayUIController_.getDisplayedAxisLength(Coords.C) > 1) {
               if (current.getC() < (displayUIController_.getDisplayedAxisLength(Coords.C) - 1)) {
                  newCoords = current.copyBuilder().c(current.getC() + 1).build();
               }
            }
            break;
         default:
            return;
      }
      if (newCoords != null) {
         displayUIController_.getDisplayController().setDisplayPosition(newCoords);
         dkpe.consume();
      }
   }

}