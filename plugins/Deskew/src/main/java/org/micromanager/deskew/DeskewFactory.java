package org.micromanager.deskew;

import java.io.IOException;
import java.text.ParseException;
import net.haesleinhuepf.clij2.CLIJ2;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Datastore;
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
         String gpuName = settings_.getString(DeskewFrame.GPU, CLIJ2.getInstance().getGPUName());
         double theta = NumberUtils.displayStringToDouble(settings_.getString(
                  DeskewFrame.THETA, "0.0"));
         if (theta == 0.0) {
            studio_.logs().showError("Can not deskew LighSheet data with an angle of 0.0 radians");
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
         if (settings_.getString(DeskewFrame.MODE, "").equals(DeskewFrame.QUALITY)) {
            return new CliJDeskewProcessor(studio_, gpuName, theta, doFullVolume, doXYProjections,
                     xyProjectionMode, doOrthogonalProjections, orthogonalProjectionsMode,
                     keepOriginal);
         }
         return new DeskewProcessor(studio_, createDatastore(""), theta, doFullVolume,
                  doXYProjections, xyProjectionMode, doOrthogonalProjections,
                  orthogonalProjectionsMode, keepOriginal, settings_);
      } catch (ParseException | IOException e) {
         studio_.logs().showError(e, "Failed to parse input, or Datastore creation failed.");
         e.printStackTrace();
      }
      return null;
   }

   private Datastore createDatastore(String prefix) throws IOException {
      Datastore store = null;
      String output  = settings_.getString(DeskewFrame.OUTPUT_OPTION, DeskewFrame.OPTION_RAM);
      if (output.equals(DeskewFrame.OPTION_RAM)) {
         return store = studio_.data().createRAMDatastore();
      }
      String path = settings_.getString(DeskewFrame.OUTPUT_PATH, "");
      if (path.contentEquals("")) {
         studio_.logs().showError("Please choose a location to save data to.");
         return null;
      }
      path += "/" + settings_.getString(DeskewFrame.SAVE_NAME, "");
      if (prefix != null && !prefix.equals("")) {
         path += "_" + prefix;
      }
      if (output.equals(DeskewFrame.OPTION_SINGLE_TIFF)) {
         return studio_.data().createSinglePlaneTIFFSeriesDatastore(path);
      } else if (output.equals(DeskewFrame.OPTION_MULTI_TIFF)) {
         // TODO: we should imitate the source dataset when possible for
         // deciding whether to generate metadata.txt and whether to
         // split positions.
         return studio_.data().createMultipageTIFFDatastore(
                  settings_.getString(DeskewFrame.OUTPUT_PATH, ""), true, true);
      }
      return null;
   }

}