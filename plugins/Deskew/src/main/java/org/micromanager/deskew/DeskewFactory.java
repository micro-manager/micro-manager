package org.micromanager.deskew;

import java.text.ParseException;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.internal.utils.NumberUtils;

/**
 * Generate DeskewProcessors based on settings.
 */
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
         if (theta == 0.0) {
            studio_.logs().showError("Can not deskew LighStheet data with an angle of 0.0 radians");
         }
         boolean doFullVolume = settings_.getBoolean(DeskewFrame.FULL_VOLUME, true);
         boolean doXYProjections = settings_.getBoolean(DeskewFrame.XY_PROJECTION, false);
         String xyProjectionMode = settings_.getString(DeskewFrame.XY_PROJECTION_MODE,
               DeskewFrame.MAX);
         boolean doOrthogonalProjections = settings_.getBoolean(DeskewFrame.ORTHOGONAL_PROJECTIONS,
                  false);
         String orthogonalProjectionsMode = settings_.getString(
               DeskewFrame.ORTHOGONAL_PROJECTIONS_MODE, DeskewFrame.MAX);
         boolean keepOriginal = settings_.getBoolean(DeskewFrame.KEEP_ORIGINAL, true);
         return new DeskewProcessor(studio_, theta, doFullVolume, doXYProjections, xyProjectionMode,
                  doOrthogonalProjections, orthogonalProjectionsMode, keepOriginal);
      } catch (ParseException e) {
         studio_.logs().showError(e, "Failed to parse input");
         e.printStackTrace();
      }
      return null;
   }
}