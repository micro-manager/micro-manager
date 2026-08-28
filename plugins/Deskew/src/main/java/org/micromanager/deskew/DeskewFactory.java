package org.micromanager.deskew;

import java.text.ParseException;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorFactory;

/**
 * Generate DeskewProcessors based on settings.
 */
public class DeskewFactory implements ProcessorFactory {
   private final Studio studio_;
   private final DeskewAcqManager deskewAcqManager_;
   private PropertyMap settings_;
   // Deliberately not a profile setting: a crash between set and reset would otherwise
   // persist and silently disable deskewing on the next launch.
   private volatile boolean exploreMode_ = false;

   public DeskewFactory(Studio studio) {
      studio_ = studio;
      deskewAcqManager_ = new DeskewAcqManager(studio);
   }

   public void setSettings(PropertyMap settings) {
      settings_ = settings;
   }

   /**
    * Sets explore mode, in which {@link #createProcessor()} returns a pass-through
    * processor.  Callers must reset this in a {@code finally} block.
    *
    * @param exploreMode true while a Deskew Explore tile acquisition is running
    */
   public void setExploreMode(boolean exploreMode) {
      exploreMode_ = exploreMode;
   }

   @Override
   public Processor createProcessor() {
      try {
         // For Explore mode acquisitions, return a pass-through processor
         // that doesn't interfere with the raw image flow
         if (exploreMode_) {
            return new PassThroughProcessor();
         }

         if (settings_.getString(DeskewFrame.MODE, "").equals(DeskewFrame.QUALITY)) {
            return new CliJDeskewProcessor(studio_, deskewAcqManager_, settings_);
         }
         return new DeskewProcessor(studio_, deskewAcqManager_, settings_);
      } catch (ParseException e) {
         studio_.logs().showError(e, "Failed to parse input, or Datastore creation failed.");
         return null;
      }
   }


}