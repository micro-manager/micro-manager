package org.micromanager.data.internal.multipagetiff;

import com.google.common.eventbus.Subscribe;
import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONObject;
import org.micromanager.MultiStagePosition;
import org.micromanager.data.Coords;
import org.micromanager.data.Datastore;
import org.micromanager.data.DatastoreFrozenException;
import org.micromanager.data.DatastoreRewriteException;
import org.micromanager.data.Image;
import org.micromanager.data.NewSummaryMetadataEvent;
import org.micromanager.data.Storage;
import org.micromanager.data.SummaryMetadata;
import org.micromanager.data.internal.DefaultCoords;
import org.micromanager.data.internal.DefaultDatastore;
import org.micromanager.data.internal.DefaultSummaryMetadata;
import static org.micromanager.data.internal.multipagetiff.StorageMultipageTiff.getShouldGenerateMetadataFile;
import org.micromanager.internal.utils.ReportingUtils;

/**
 *
 * @author Hadrien Mary
 */
public class XYSplitStorageMultipageTiff implements Storage {

   private DefaultDatastore store_;
   private DefaultSummaryMetadata summaryMetadata_ = (new DefaultSummaryMetadata.Builder()).build();
   private String summaryMetadataString_ = summaryMetadata_.toJSON().toString();

   final private boolean separateMetadataFile_;
   private boolean amInWriteMode_;
   private String directory_;

   // Keeps track of our maximum extent along each axis.
   private Coords maxIndices_;

   private Map<Integer, StorageMultipageTiff> XYSingleStorages_;

   public XYSplitStorageMultipageTiff(Datastore store, String dir, Boolean amInWriteMode)
           throws IOException, DatastoreFrozenException, DatastoreRewriteException {
      this(store, dir, amInWriteMode, getShouldGenerateMetadataFile());
   }

   public XYSplitStorageMultipageTiff(Datastore store, String dir,
           boolean amInWriteMode, boolean separateMDFile)
           throws IOException, DatastoreFrozenException, DatastoreRewriteException {

      store_ = (DefaultDatastore) store;
      // We must be notified of changes in the Datastore before everyone else,
      // so that others can read those changes out of the Datastore later.
      store_.registerForEvents(this, 0);
      separateMetadataFile_ = separateMDFile;

      amInWriteMode_ = amInWriteMode;
      directory_ = dir;
      store_.setSavePath(directory_);

      if (amInWriteMode_) {
         // Create the directory now, even though we have nothing to write to
         // it, so we can detect e.g. permissions errors that would cause
         // problems later.
         File dirFile = new File(directory_);
         if (dirFile.exists()) {
            // No overwriting existing datastores.
            throw new IOException("Data at " + dirFile + " already exists");
         }
         dirFile.mkdirs();
         if (!dirFile.canWrite()) {
            throw new IOException("Insufficient permission to write to " + dirFile);
         }

         // Init the map storing Datastorage
         XYSingleStorages_ = new HashMap<Integer, StorageMultipageTiff>();

      } else {
         ReportingUtils.logError("XYSplitStorageMultipageTiff should not be used to open dataset. "
                 + "Use StorageMultipageTiff instead.");
      }

   }

   @Subscribe
   public void onNewSummaryMetadata(NewSummaryMetadataEvent event) {
      try {
         setSummaryMetadata(event.getSummaryMetadata());
      } catch (Exception e) {
         ReportingUtils.logError(e, "Error setting new summary metadata");
      }
   }

   private StorageMultipageTiff getStorage(Image image) {
      return XYSingleStorages_.get(image.getCoords().getStagePosition());
   }

   private StorageMultipageTiff getStorage(Coords coords) {
      return XYSingleStorages_.get(coords.getStagePosition());
   }

   private StorageMultipageTiff getAnyStorage() {
      return (StorageMultipageTiff) XYSingleStorages_.values().toArray()[0];
   }

   @Override
   public void freeze() {
      for (StorageMultipageTiff singleXYStorage : XYSingleStorages_.values()) {
         singleXYStorage.freeze();
      }
   }

   @Override
   public void putImage(Image image) {

      Integer stagePositionInteger = image.getCoords().getStagePosition();
      StorageMultipageTiff singleXYStorage = null;

      MultiStagePosition stagePosition = summaryMetadata_.getStagePositions()[stagePositionInteger];

      if (!XYSingleStorages_.containsKey(stagePositionInteger)) {
         // Create a directory name for this XY Position
         String singleXYDir = Paths.get(directory_, "XYPosition_" + stagePositionInteger.toString()).toString();

         // Create a new Datastore copying the main one
         DefaultDatastore singleXYStore = new DefaultDatastore();
         singleXYStore.copyFrom(store_, null);

//         // Only set one single StagePosition for the new Datastore
//         SummaryMetadata singleXYMetadata = singleXYStore.getSummaryMetadata().copy().stagePositions(new MultiStagePosition[]{stagePosition}).build();
//         System.out.println(singleXYMetadata);
//
//         try {
//            singleXYStore.setSummaryMetadata(singleXYMetadata);
//         } catch (DatastoreFrozenException ex) {
//            ReportingUtils.logError(ex, "Error setting single stage position '" + stagePositionInteger + "' metadata to the new datastore");
//         } catch (DatastoreRewriteException ex) {
//            ReportingUtils.logError(ex, "Error setting single stage position '" + stagePositionInteger + "' metadata to the new datastore");
//         }
         try {
            // Create a new Storage for the new Datastore
            singleXYStorage = new StorageMultipageTiff(singleXYStore, singleXYDir, amInWriteMode_);
            singleXYStore.setStorage(singleXYStorage);
         } catch (IOException ex) {
            ReportingUtils.logError(ex, "Error creating new storage for position '" + stagePositionInteger + "'.");
         }

         XYSingleStorages_.put(stagePositionInteger, singleXYStorage);
      } else {
         singleXYStorage = XYSingleStorages_.get(stagePositionInteger);
      }

      singleXYStorage.putImage(image);
   }

   @Override
   public Image getImage(Coords coords) {
      return getStorage(coords).getImage(coords.copy().stagePosition(0).build());
   }

   @Override
   public boolean hasImage(Coords coords) {
      StorageMultipageTiff singleXYStorage;

      for (Integer stagePositionInteger : XYSingleStorages_.keySet()) {
         singleXYStorage = XYSingleStorages_.get(stagePositionInteger);
         if (coords.getStagePosition() == stagePositionInteger
                 && singleXYStorage.hasImage(coords.copy().stagePosition(0).build())) {
            return true;
         }
      }
      return false;
   }

   @Override
   public Image getAnyImage() {
      return getAnyStorage().getAnyImage();
   }

   @Override
   public Iterable<Coords> getUnorderedImageCoords() {
      List<Coords> allCoords = Collections.emptyList();
      StorageMultipageTiff singleXYStorage;

      for (Integer stagePositionInteger : XYSingleStorages_.keySet()) {
         singleXYStorage = XYSingleStorages_.get(stagePositionInteger);
         for (Coords coords : singleXYStorage.getUnorderedImageCoords()) {
            allCoords.add(coords.copy().stagePosition(stagePositionInteger).build());
         }
      }
      return allCoords;
   }

   @Override
   public List<Image> getImagesMatching(Coords coords) {
      return getStorage(coords).getImagesMatching(coords.copy().stagePosition(0).build());
   }

   @Override
   public Coords getMaxIndices() {

      HashMap<String, Integer> maxIndices = new HashMap<String, Integer>();
      StorageMultipageTiff singleXYStorage;
      DefaultCoords.Builder builder = new DefaultCoords.Builder();
      Integer index;
      Coords indices;

      builder = (DefaultCoords.Builder) builder.channel(0).z(0).time(0).stagePosition(0);

      for (Integer stagePositionInteger : XYSingleStorages_.keySet()) {

         singleXYStorage = XYSingleStorages_.get(stagePositionInteger);
         indices = singleXYStorage.getMaxIndices();
         for (String axis : singleXYStorage.getMaxIndices().getAxes()) {
            index = indices.getIndex(axis);
            if (maxIndices.keySet().contains(axis) && index > maxIndices.get(axis)) {
               builder.index(axis, index);
            }
         }
      }

      if (XYSingleStorages_.size() > 0) {
         builder.stagePosition(Collections.max(XYSingleStorages_.keySet()));
      }

      maxIndices_ = builder.build();
      return maxIndices_;
   }

   /**
    * TODO: in future there will probably be a cleaner way to implement this.
    */
   @Override
   public List<String> getAxes() {
      return getMaxIndices().getAxes();
   }

   @Override
   public Integer getMaxIndex(String axis) {
      return getMaxIndices().getIndex(axis);
   }

   // Convenience function.
   public int getAxisLength(String axis) {
      return getMaxIndex(axis) + 1;
   }

   @Override
   public SummaryMetadata getSummaryMetadata() {
      return summaryMetadata_;
   }

   @Override
   public int getNumImages() {
      int numImages = 0;
      for (StorageMultipageTiff singleXYStorage : XYSingleStorages_.values()) {
         numImages += singleXYStorage.getNumImages();
      }
      return numImages;
   }

   @Override
   public void close() {
      for (StorageMultipageTiff singleXYStorage : XYSingleStorages_.values()) {
         singleXYStorage.close();
      }
   }

   public void setSummaryMetadata(SummaryMetadata summary) {
      setSummaryMetadata((DefaultSummaryMetadata) summary, false);
   }

   private void setSummaryMetadata(DefaultSummaryMetadata summary,
           boolean showProgress) {
      summaryMetadata_ = summary;
      JSONObject summaryJSON = summary.toJSON();
      summaryMetadataString_ = summaryJSON.toString();

      SummaryMetadata singleMetadata;
      MultiStagePosition stagePosition;
      StorageMultipageTiff singleXYStorage;

      for (Integer stagePositionInteger : XYSingleStorages_.keySet()) {
         singleXYStorage = XYSingleStorages_.get(stagePositionInteger);
         stagePosition = summary.getStagePositions()[stagePositionInteger];
         singleMetadata = summary.copy().stagePositions(new MultiStagePosition[]{stagePosition}).build();
         singleXYStorage.setSummaryMetadata(singleMetadata);
      }
   }

   public String getSummaryMetadataString() {
      return summaryMetadataString_;
   }

   public Datastore getDatastore() {
      return store_;
   }

}
