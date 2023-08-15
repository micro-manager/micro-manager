package org.micromanager.deskew;

import java.io.IOException;
import java.text.ParseException;
import java.util.List;
import net.haesleinhuepf.clij2.CLIJ2;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.Processor;
import org.micromanager.data.ProcessorFactory;
import org.micromanager.data.RewritableDatastore;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
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
                     keepOriginal, settings_);
         }
         return new DeskewProcessor(studio_, theta, doFullVolume,
                  doXYProjections, xyProjectionMode, doOrthogonalProjections,
                  orthogonalProjectionsMode, keepOriginal, settings_);
      } catch (ParseException e) {
         studio_.logs().showError(e, "Failed to parse input, or Datastore creation failed.");
         e.printStackTrace();
      }
      return null;
   }

   protected static Datastore createStoreAndDisplay(Studio studio, PropertyMap settings,
                                                    SummaryMetadata summaryMetadata,
                                           String prefix,
                                           int width,
                                           int height,
                                           int nrZSlices,
                                           Double newZStepUm) throws IOException {
      Datastore store = DeskewFactory.createDatastore(studio, settings, prefix);
      Coords.Builder cb = summaryMetadata.getIntendedDimensions().copyBuilder().z(nrZSlices);
      if (store instanceof RewritableDatastore) {
         cb.t(0);
      }
      SummaryMetadata.Builder smb = summaryMetadata.copyBuilder()
               .intendedDimensions(cb.build())
               .imageWidth(width)
               .imageHeight(height)
               .prefix(prefix);
      if (newZStepUm != null) {
         smb.zStepUm(newZStepUm);
      }
      SummaryMetadata outputSummaryMetadata = smb.build();
      try {
         store.setSummaryMetadata(outputSummaryMetadata);
      } catch (IOException ioe) {
         studio.logs().logError(ioe);
      }
      // complicated way to find the viewer that had this data
      // This depends on the SummaryMetadata being the original
      DisplaySettings displaySettings = null;
      List<DataViewer> dataViewers = studio.displays().getAllDataViewers();
      for (DataViewer dv : dataViewers) {
         DataProvider provider = dv.getDataProvider();
         if (provider != null && provider.getSummaryMetadata() == summaryMetadata) {
            displaySettings = dv.getDisplaySettings();
         }
      }
      DisplayWindow display = studio.displays().createDisplay(store);
      if (displaySettings != null) {
         display.setDisplaySettings(displaySettings);
      }
      display.show();
      return store;
   }

   protected static Datastore createDatastore(Studio studio, PropertyMap settings, String prefix)
            throws IOException {
      String output  = settings.getString(DeskewFrame.OUTPUT_OPTION, DeskewFrame.OPTION_RAM);
      if (output.equals(DeskewFrame.OPTION_RAM)) {
         Datastore store = studio.data().createRAMDatastore();
         store.setName(prefix);
         return store;
      } else if (output.equals(DeskewFrame.OPTION_REWRITABLE_RAM)) {
         Datastore store = studio.data().createRewritableRAMDatastore();
         store.setName(prefix);
         return store;
      }
      String path = settings.getString(DeskewFrame.OUTPUT_PATH, "");
      if (path.contentEquals("")) {
         studio.logs().showError("Please choose a location to save data to.");
         return null;
      }
      path += "/" + prefix;
      path = studio.data().getUniqueSaveDirectory(path);

      if (output.equals(DeskewFrame.OPTION_SINGLE_TIFF)) {
         return studio.data().createSinglePlaneTIFFSeriesDatastore(path);
      } else if (output.equals(DeskewFrame.OPTION_MULTI_TIFF)) {
         // TODO: we should imitate the source dataset when possible for
         // deciding whether to generate metadata.txt and whether to
         // split positions.
         return studio.data().createMultipageTIFFDatastore(path, true, true);
      }
      return null;
   }

}