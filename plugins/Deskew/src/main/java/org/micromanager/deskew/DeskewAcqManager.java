package org.micromanager.deskew;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.DataProvider;
import org.micromanager.data.Datastore;
import org.micromanager.data.RewritableDatastore;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.display.DataViewer;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;

/**
 * This class manages the test datastores and data viewers for the Deskew plugin.
 * It allows for renewing test datastores and setting test data viewers.
 */
public class DeskewAcqManager {
   final Studio studio_;
   private Datastore testFullVolumeStore_;
   private DisplayWindow testFullVolumeWindow_;
   private Datastore testOrthogonalProjectionsStore_;
   private DisplayWindow testOrthogonalProjectionsWindow_;
   private Datastore testXYProjectionsStore_;
   private DisplayWindow testXYProjectionsWindow_;

   public enum ProjectionType {
      YX_PROJECTION,
      ORTHOGONAL_VIEWS,
      FULL_VOLUME
   }

   public DeskewAcqManager(Studio studio) {
      studio_ = studio;
   }

   /**
    * Closes existing data viewers and stores for test acquisitions
    * created by the Deskew plugin. Store the new test datastores
    * so that we can close them next time we renew the test data.
    *
    * @param testDatastores List of test datastores to be set.
   public void renewTestData(List<Datastore> testDatastores) {
      for (Datastore ds : testDatastores_) {
         for (DisplayWindow iw : studio_.displays().getAllImageWindows()) {
            if (ds.equals(iw.getDataProvider())) {
               iw.close();
            }
         }
         try {
            ds.freeze();
            ds.close();
         } catch (IOException e) {
            throw new RuntimeException(e);
         }
      }
      testDatastores_.clear();
      testDatastores_.addAll(testDatastores);
   }
    */

   public Datastore createStoreAndDisplay(Studio studio,
                                                    PropertyMap settings,
                                                    SummaryMetadata summaryMetadata,
                                                    ProjectionType projectionType,
                                                    String prefix,
                                                    int width,
                                                    int height,
                                                    int nrZSlices,
                                                    Double newZStepUm) throws IOException {
      boolean isTestAcq = summaryMetadata.getSequenceSettings().isTestAcquisition();
      DisplaySettings displaySettings = null;
      Datastore store = DeskewAcqManager.createDatastore(studio, settings, prefix);
      if (isTestAcq) {
         switch (projectionType) {
            case FULL_VOLUME:
               if (testFullVolumeWindow_ != null) {
                  displaySettings = testFullVolumeWindow_.getDisplaySettings();
                  testFullVolumeWindow_.close();
               }
               if (testFullVolumeStore_ != null) {
                  try {
                     testFullVolumeStore_.freeze();
                     testFullVolumeStore_.close();
                  } catch (IOException e) {
                     studio.logs().logError(e);
                  }
               }
               testFullVolumeStore_ = store;
               break;
            case YX_PROJECTION:
               if (testXYProjectionsWindow_ != null) {
                  displaySettings = testXYProjectionsWindow_.getDisplaySettings();
                  testXYProjectionsWindow_.close();
               }
               if (testXYProjectionsStore_ != null) {
                  try {
                     testXYProjectionsStore_.freeze();
                     testXYProjectionsStore_.close();
                  } catch (IOException e) {
                     studio.logs().logError(e);
                  }
               }
               testXYProjectionsStore_ = store;
               break;
            case ORTHOGONAL_VIEWS:
               if (testOrthogonalProjectionsWindow_ != null) {
                  displaySettings = testOrthogonalProjectionsWindow_.getDisplaySettings();
                  testOrthogonalProjectionsWindow_.close();
               }
               if (testOrthogonalProjectionsStore_ != null) {
                  try {
                     testOrthogonalProjectionsStore_.freeze();
                     testOrthogonalProjectionsStore_.close();
                  } catch (IOException e) {
                     studio.logs().logError(e);
                  }
               }
               testOrthogonalProjectionsStore_ = store;
               break;
            default:
               studio.logs().showError("Unknown projection type: " + projectionType);
               return null;
         }
      }
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
      if (displaySettings == null) {
         List<DataViewer> dataViewers = studio.displays().getAllDataViewers();
         for (DataViewer dv : dataViewers) {
            DataProvider provider = dv.getDataProvider();
            if (provider != null && provider.getSummaryMetadata() == summaryMetadata) {
               displaySettings = dv.getDisplaySettings();
            }
         }
      }
      if ((settings.containsKey(DeskewFrame.SHOW) && settings.getBoolean(DeskewFrame.SHOW, false))
               || (settings.containsKey(DeskewFrame.OUTPUT_OPTION)
               && (settings.getString(DeskewFrame.OUTPUT_OPTION, "").equals(DeskewFrame.OPTION_RAM)
               || settings.getString(DeskewFrame.OUTPUT_OPTION, "")
               .equals(DeskewFrame.OPTION_REWRITABLE_RAM)))) {
         DisplayWindow display = studio.displays().createDisplay(store);
         if (displaySettings != null) {
            display.setDisplaySettings(displaySettings);
         }
         if (isTestAcq) {
            switch (projectionType) {
               case FULL_VOLUME:
                  testFullVolumeWindow_ = display;
                  break;
               case YX_PROJECTION:
                  testXYProjectionsWindow_ = display;
                  break;
               case ORTHOGONAL_VIEWS:
                  testOrthogonalProjectionsWindow_ = display;
                  break;
               default:
                  studio.logs().showError("Unknown projection type: " + projectionType);
                  return null;
            }
         }
         display.show();
      }
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
