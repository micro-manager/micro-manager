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

   public DeskewFactory(Studio studio) {
      studio_ = studio;
      deskewAcqManager_ = new DeskewAcqManager(studio);
   }

   public void setSettings(PropertyMap settings) {
      settings_ = settings;
   }

   @Override
   public Processor createProcessor() {
      try {
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