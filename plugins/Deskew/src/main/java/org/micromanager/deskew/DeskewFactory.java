package org.micromanager.deskew;

import java.text.ParseException;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.internal.utils.NumberUtils;

public class DeskewFactory implements ProcessorFactory {
   private final Studio studio_;
   private final PropertyMap settings_;

   public DeskewFactory(Studio studio, PropertyMap settings) {
      studio_ = studio;
      settings_ = settings;
   }

   @Override
   public Processor createProcessor() {
      try {
         double theta = NumberUtils.displayStringToDouble(settings_.getString(
                  DeskewFrame.THETA, "0.0"));
         boolean doFullVolume = settings_.getBoolean(DeskewFrame.FULL_VOLUME, true);
         boolean doProjections = settings_.getBoolean(DeskewFrame.PROJECTIONS, false);
         boolean keepOriginal = settings_.getBoolean(DeskewFrame.KEEP_ORIGINAL, true);
         return new DeskewProcessor(studio_, theta, doFullVolume, doProjections, keepOriginal);
      } catch (ParseException e) {
         studio_.logs().showError(e, "Failed to parse input");
         e.printStackTrace();
      }
      return null;
   }
}