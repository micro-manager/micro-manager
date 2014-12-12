package org.micromanager.patternoverlay;

import ij.ImagePlus;
import ij.gui.Overlay;
import ij.gui.Roi;

import java.awt.Color;
import java.util.prefs.Preferences;

import org.micromanager.utils.ReportingUtils;

/**
 * Parent class that implements most functionality of overlays.
 * 
 * Derived classes must implement getOverlay(int, int).
 * 
 * Since this creates an overlay, stored images will not be affected, and
 * no persistent change is made to the actual image; rather, this adds another
 * layer on top of the life view window.
 * 
 * @author Jon
 *
 */
public abstract class GenericOverlay {
   
   protected int size_;  // 0 to 100
   protected Color color_;
   
   private Overlay overlay_;
   private int colorCode_;
   private boolean isShowing_;
   private final Preferences prefs_;
   private final String prefPrefix_;

   public GenericOverlay(Preferences prefs, String prefPrefix) {
      isShowing_ = false;
      prefs_ = prefs;
      prefPrefix_ = prefPrefix;
      size_ = prefs_.getInt(prefPrefix_ + Constants.SIZE_SLIDER, 50);
      colorCode_ = prefs_.getInt(prefPrefix_ + Constants.COLOR_BOX_IDX, 0);
   }

   /**
    * Change the overlay size.  Takes effect immediately.
    * @param size should be 0 to 100
    */
   public void setSize(int size) {
      size_ = size;
      prefs_.putInt(prefPrefix_ + Constants.SIZE_SLIDER, size);
      refresh();
   }
   
   /**
    * Gets the current size of the overlay
    * @return
    */
   public int getSize() {
      return size_;
   }
   
   /**
    * Sets the color of the overlay.  Takes effect immediately.
    * @param colorCode  
    */
   public void setColorCode(int colorCode) {
      colorCode_ = colorCode;
      prefs_.putInt(prefPrefix_ + Constants.COLOR_BOX_IDX, colorCode);
      color_ = Constants.LOOKUP_COLOR_BY_INDEX.get(colorCode_);
      refresh();
   }
   
   /**
    * Gets the current color code (stored in prefs) of the overlay
    * @return
    */
   public int getColorCode() {
      return colorCode_;
   }

   /**
    * 
    * @param visible
    * @throws NoLiveWindowException
    */
   public void setVisible(boolean visible) throws NoLiveWindowException {
      if (visible) {
         show();
      } else {
         hide();
      }
      isShowing_ = visible;
   }
   
   /**
    * True if the overlay is showing
    * @return
    */
   public boolean isVisible() {
      return isShowing_;
   }
   
   /**
    * Updates the object overlay_.  Must be implemented in child class.
    */
   abstract Roi getRoi(int width, int height);
   
   /**
    *  Create a new overlay with the desired characteristics as set through
    *  setSize and setColor. This can fail if no live image window is found,
    *  or if something unspeakable happens during creation of the overlay.
    *
    *  @throws Exception
    */
   private void show () throws NoLiveWindowException {
      ImagePlus image = PatternOverlayFrame.getLiveWindowImage();
      if (image == null)
         throw new NoLiveWindowException();
      overlay_ = new Overlay(getRoi(image.getWidth(), image.getHeight()));
      image.setOverlay(overlay_);
   }

   /**
    *  Clear the overlay. This removes any and all trace of the overlay
    *  from the live image window. A call to show() is required to
    *  recreate the overlay.
    */
   private void hide() throws NoLiveWindowException {
      if (overlay_ != null) {
         ImagePlus image = PatternOverlayFrame.getLiveWindowImage();
         if (image == null) {
            // fine, window disappeared so the overlay is already hidden
            return;
         }
         overlay_.clear();
         image.setOverlay(null);
      }
   }

   /**
    *  Attempt to clear and recreate the overlay. This could, but should not,
    *  fail if the overlay was working before this call.
    */
   private void refresh () {
      if (isShowing_) { 
         try {
            hide();
            show();
         } catch (NoLiveWindowException e) {
            ReportingUtils.logError("Could not show overlay after changing size.");
         }        
      }
   }
   
}
