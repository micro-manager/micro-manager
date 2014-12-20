package org.micromanager.patternoverlay;


/**
 *  Overlay type options for dropdown selection.
 *
 *  @author Jon
 */
public class OverlayOption {
   
   public static enum Keys {
      CROSSHAIR("Crosshair"), 
      GRID("Grid"),
      CIRCLE("Circle"),
      TARGET("Target"),
      ;
      private final String text;
      Keys(String text) {
         this.text = text;
      }
      @Override
      public String toString() {
         return text;
      }
   };

   private final Keys             key_;      // overlay enum represented by this option
   private final GenericOverlay   overlay_;  


   /**
    *  OverlayOption constructor; expects a key and an associated object
    *  that implements the OverlayInterface.
    *  
    *  @param key
    *  @param overlay
    */
   public OverlayOption(Keys key, GenericOverlay overlay) {
      key_ = key;
      overlay_ = overlay;
   }


   /**
    *  Retrieve the key associated with this option.
    *
    *  @return This overlay enum key.
    */
   public Keys getOverlayKey () {
      return key_;
   }
   
   /**
    *  Retrieve the key associated with this option.
    *
    *  @return This overlay.
    */
   public GenericOverlay getOverlay () {
      return overlay_;
   }


   /**
    *  Retrieve the name of this key. This overrides the toString method, since
    *  that is what is being called by the drop-down box to populate the fields.
    *
    *  @return     Name this option represents.
    */
   @Override
   public String toString () {
      return key_.toString();
   }
}

