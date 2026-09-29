package org.micromanager.tileddataviewer;

import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;

/**
 * Interface for all three types of mouse listeners in one. A custom object
 * implementing these can be added to the viewer canvas
 *
 * @author henrypinkard
 */
public interface TiledDataViewerCanvasMouseListenerInterface extends MouseListener,
         MouseWheelListener, MouseMotionListener {

   /**
    * Zoom change applied per scrollwheel step.
    */
   double ZOOM_FACTOR_MOUSE = 1.4;

   /**
    * Minimum time between successive scrollwheel zoom steps. A fast scroll delivers many
    * events; without this a single flick applies a large compounded zoom.
    */
   int MOUSE_WHEEL_ZOOM_INTERVAL_MS = 100;

   /**
    * Zoom factor for one scrollwheel event, or 0 if the event should be ignored.
    *
    * <p>Deliberately uses only the sign of the rotation. getWheelRotation() can report several
    * clicks in a single event (and trackpads deliver rapid bursts), so honouring the count
    * would let one gesture request an enormous zoom change that the user did not intend. On a
    * very large tiled dataset an unintended deep zoom-out is expensive enough to freeze the
    * UI, so the step size is capped here at the source.</p>
    *
    * <p>Implementations should combine this with a {@link #MOUSE_WHEEL_ZOOM_INTERVAL_MS}
    * throttle so scrollwheel zoom behaves identically across all canvas mouse listeners.</p>
    *
    * @param mwe the wheel event
    * @return factor to pass to zoom(), or 0 for no zoom
    */
   static double zoomFactorForWheelEvent(MouseWheelEvent mwe) {
      int rotation = mwe.getWheelRotation();
      if (rotation < 0) {
         return 1 / ZOOM_FACTOR_MOUSE; //zoom in
      } else if (rotation > 0) {
         return ZOOM_FACTOR_MOUSE; //zoom out
      }
      return 0;
   }
}
