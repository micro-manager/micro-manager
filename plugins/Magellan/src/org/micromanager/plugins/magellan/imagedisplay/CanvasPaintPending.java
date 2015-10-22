/*
 * 
 */
package org.micromanager.plugins.magellan.imagedisplay;

import ij.gui.ImageCanvas;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Class to manage calls to ImageJ Canvas.PaintPending
 * Micro-Manager has several classes that need to know whether a paint to 
 * a specific Canvas is pending, but they can easily deadlock.
 * This class keeps track of whom set the PaintPending and will only tell
 * whomever set PaintPending that a Paint is indeed pending
 * @author nico
 */
public class CanvasPaintPending {
   public static Map<ImageCanvas, List<Object>> requesters_ = 
           new HashMap<ImageCanvas, List<Object>>();
   
   /**
    * Checks if the calling class has a paint pending 
    * If there is no paint pending, it will remove all previous requests
    * 
    * @param canvas - ImageCanvas for which to check if a paint is pending
    * @param caller - Calling class
    * 
    * @return - true if a paint is pending and has been requested by the calling
    * class, false otherwise.
    */
   public static synchronized boolean isMyPaintPending (ImageCanvas canvas, 
           Object caller) {
      if (canvas.getPaintPending()) {
         List<Object> objectList = requesters_.get(canvas);
         if (objectList!= null) {
            if (objectList.contains(caller)) {
               return true;
            }
         }
      }
      // no Paint Pending.  Erase all past calls to setPaintPending
      else {
         requesters_.remove(canvas);
      }
      return false;
   }
   
   
   public static synchronized void setPaintPending (ImageCanvas canvas, 
           Object caller) {
      canvas.setPaintPending(true);
      List<Object> objectList = requesters_.get(canvas);
      if (objectList == null) {
         objectList = new ArrayList<Object>();
         requesters_.put(canvas, objectList);
      }
      if (!objectList.contains(caller) ) {
         objectList.add(caller);
      }   
   }
   
   public static synchronized void removePaintPending(ImageCanvas canvas, 
           Object caller) {
      List<Object> objectList = requesters_.get(canvas);
      if (objectList != null && objectList.contains(caller)) {
         objectList.remove(caller);
         if (objectList.isEmpty()) {
            canvas.setPaintPending(false);
            requesters_.remove(canvas);
         }
      }
   }

   public static synchronized void removeAllPaintPending(ImageCanvas canvas) {
      if (requesters_.containsKey(canvas)) {
         requesters_.remove(canvas);
      }
   }   
}
