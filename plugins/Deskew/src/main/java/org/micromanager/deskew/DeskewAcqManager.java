package org.micromanager.deskew;

import java.awt.Color;
import java.io.IOException;
import java.util.EnumMap;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.RewritableDatastore;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.PropertyKey;
import org.micromanager.display.DisplaySettings;
import org.micromanager.display.DisplayWindow;
import org.micromanager.display.internal.RememberedDisplaySettings;

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

   public static final String DESKEW_DISPLAYSETTINGS_VOLUME = "Deskew_Display_Volume";
   public static final String DESKEW_DISPLAYSETTINGS_ORTHOGONAL_VIEWS
            = "Deskew_Display_Orthogonal_Views";
   public static final String DESKEW_DISPLAYSETTINGS_YX_PROJECTIONS
            = "Deskew_Display_YX_Projections";


   /**
    * Enum representing the different projection types for displaying data.
    */
   public enum ProjectionType {
      YX_PROJECTION,
      ORTHOGONAL_VIEWS,
      FULL_VOLUME
   }

   private final EnumMap<ProjectionType, String> projectionTypeDisplayKeys
            = new EnumMap<>(ProjectionType.class);

   public static final String[] PROJECTION_TYPES = {
      ProjectionType.YX_PROJECTION.name(),
      ProjectionType.ORTHOGONAL_VIEWS.name(),
      ProjectionType.FULL_VOLUME.name()
   };

   /**
    * Constructor for DeskewAcqManager.
    *
    * @param studio The Studio instance to use for data management and display.
    */
   public DeskewAcqManager(Studio studio) {
      studio_ = studio;
      projectionTypeDisplayKeys.put(ProjectionType.YX_PROJECTION,
               DESKEW_DISPLAYSETTINGS_YX_PROJECTIONS);
      projectionTypeDisplayKeys.put(ProjectionType.ORTHOGONAL_VIEWS,
               DESKEW_DISPLAYSETTINGS_ORTHOGONAL_VIEWS);
      projectionTypeDisplayKeys.put(ProjectionType.FULL_VOLUME,
               DESKEW_DISPLAYSETTINGS_VOLUME);
   }

   /**
    * Creates a new datastore and displays it based on the provided settings.
    *
    * @param studio The Studio instance to use for data management and display.
    * @param settings The PropertyMap containing settings for the datastore.
    * @param summaryMetadata Metadata summarizing the dataset.
    * @param projectionType The type of projection to display.
    * @param prefix A prefix for the datastore name.
    * @param width The width of the images in the datastore.
    * @param height The height of the images in the datastore.
    * @param nrZSlices The number of Z slices in the dataset.
    * @param newZStepUm The new Z step size in micrometers, or null if not applicable.
    * @return The created Datastore, or null if creation failed.
    * @throws IOException If an error occurs during datastore creation.
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
      Datastore store = DeskewAcqManager.createDatastore(studio, settings, isTestAcq, prefix);
      if (isTestAcq) {
         switch (projectionType) {
            case FULL_VOLUME:
               if (testFullVolumeWindow_ != null) {
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
      if (store == null) {
         studio.logs().showError("Failed to create datastore for Deskew.");
         return null;
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

      final String displayKey = projectionTypeDisplayKeys.get(projectionType);
      DisplaySettings displaySettings =
               studio_.displays().displaySettingsFromProfile(displayKey);
      DisplaySettings.Builder displaySettingsBuilder = null;
      if (displaySettings == null) {
         displaySettings = studio_.displays().displaySettingsFromProfile(
                  PropertyKey.ACQUISITION_DISPLAY_SETTINGS.key());
      }
      if (displaySettings == null) {
         displaySettingsBuilder = studio_.displays().displaySettingsBuilder();
      } else {
         displaySettingsBuilder = displaySettings.copyBuilder();
      }
      final int nrChannels = store.getSummaryMetadata().getChannelNameList().size();
      if (nrChannels > 0) { // I believe this will always be true, but just in case...
         if (nrChannels == 1) {
            displaySettingsBuilder.colorModeGrayscale();
         } else {
            displaySettingsBuilder.colorModeComposite();
         }
         for (int channelIndex = 0; channelIndex < nrChannels; channelIndex++) {
            displaySettingsBuilder.channel(channelIndex,
                     RememberedDisplaySettings.loadChannel(studio_,
                              store.getSummaryMetadata().getChannelGroup(),
                              store.getSummaryMetadata().getChannelNameList().get(channelIndex),
                              displaySettings != null
                                       ? displaySettings.getChannelColor(channelIndex)
                                       : Color.WHITE));
         }
      } else {
         int tmpNrChannels = summaryMetadata.getChannelNameList().size();
         studio_.logs().logError("nrChannel in MMAcquisition was unexpectedly zero");
      }

      if ((settings.containsKey(DeskewFrame.SHOW) && settings.getBoolean(DeskewFrame.SHOW, false))
               || (settings.containsKey(DeskewFrame.OUTPUT_OPTION)
               && (settings.getString(DeskewFrame.OUTPUT_OPTION, "").equals(DeskewFrame.OPTION_RAM)
               || settings.getString(DeskewFrame.OUTPUT_OPTION, "")
               .equals(DeskewFrame.OPTION_REWRITABLE_RAM)))) {
         DisplayWindow display = studio.displays().createDisplay(store, null,
                  displaySettingsBuilder.build());
         display.setWindowPositionKey(PROJECTION_TYPES[projectionType.ordinal()]);
         display.setDisplaySettingsProfileKey(displayKey);
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

   protected static Datastore createDatastore(Studio studio, PropertyMap settings,
                                              boolean isTestAcq, String prefix)
            throws IOException {
      String output  = settings.getString(DeskewFrame.OUTPUT_OPTION, DeskewFrame.OPTION_RAM);
      if (output.equals(DeskewFrame.OPTION_RAM) || isTestAcq) {
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
