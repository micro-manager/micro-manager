package org.micromanager.acquisition.internal.acqengjcompat.speedtest;/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

import java.util.HashMap;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;

import mmcorej.TaggedImage;
import mmcorej.org.json.JSONObject;
import org.micromanager.acqj.main.AcqEngMetadata;
import org.micromanager.acqj.api.DataSink;
import org.micromanager.acqj.main.Acquisition;
import org.micromanager.acqj.internal.Engine;
import org.micromanager.ndtiffstorage.NDTiffStorage;
import org.micromanager.ndtiffstorage.MultiresNDTiffAPI;
import org.micromanager.ndtiffstorage.NDTiffAPI;
import org.micromanager.ndviewer.api.DataSourceInterface;
import org.micromanager.ndviewer.api.ViewerInterface;
import org.micromanager.ndviewer.main.NDViewer;
import org.micromanager.ndviewer.api.ViewerAcquisitionInterface;

/**
 * The class is the glue needed in order for AcqEngJ, NDViwer, and NDTiff
 * to be able to be used together, since they are independent libraries that do not know about one
 * another. It implements the Acquisition engine API for a {@link DataSink} interface, dispatching acquired images
 * to viewer and storage as appropriate. It implements NDviewer's {@link DataSourceInterface}, so
 * that images in storage can be requested by the viewer for display. Each time it recieves an image
 * it will pass it to storage and alert the display that a new image has arrived
 *
 * There are analagous classes to this one in Micro-Magellan (MagellanDatasetAndAcquisition) and
 * the Java side of pycro-manger (RemoteViewerStorageAdapter)
 *
 * @author henrypinkard
 */
public class NDTiffAndViewerAdapter implements DataSourceInterface, DataSink {

   private ExecutorService displayCommunicationExecutor_;

   private volatile ViewerInterface viewer_;
   private volatile Acquisition acq_;
   private volatile MultiresNDTiffAPI storage_;

   private final boolean showViewer_, storeData_;
   private String dir_;
   private String name_;
   private int savingQueueSize_;
   private volatile boolean finished_ = false;


   /**
    *
    * @param showViewer create and show a viewer
    * @param dataStorageLocation where should data be saved to disk
    * @param name name for data storage and viewer
    */
   public NDTiffAndViewerAdapter(boolean showViewer,  String dataStorageLocation,
                                     String name, int savingQueueSize) {
      showViewer_ = showViewer;
      storeData_ = dataStorageLocation != null;
      dir_ = dataStorageLocation;
      name_ = name;
      savingQueueSize_ = savingQueueSize;
   }

   public void initialize(Acquisition acq, JSONObject summaryMetadata) {
      acq_ =  acq;

      if (storeData_) {

         storage_ = new NDTiffStorage(dir_, name_,
               summaryMetadata, 0, 0,
               false, 0, savingQueueSize_,
               //Debug logging function without storage having to directly depend on core
               acq_.isDebugMode() ? ((Consumer<String>) s -> {
                  Engine.getCore().logMessage(s);
               }) : null, true
         );
         name_ = storage_.getUniqueAcqName();

      }

      if (showViewer_) {
         createDisplay(summaryMetadata);
      }
   }

   public NDTiffAPI getStorage() {
      return storage_;
   }

   private void createDisplay(JSONObject summaryMetadata) {
      //create display
      displayCommunicationExecutor_ = Executors.newSingleThreadExecutor((Runnable r)
            -> new Thread(r, "Image viewer communication thread"));

      // simple class that allows viewer to start and stop acquisition
      ViewerAcquisitionInterface vai = new ViewerAcquisitionInterface() {
         @Override
         public boolean isFinished() {
            return acq_.areEventsFinished();
         }

         @Override
         public void abort() {
            acq_.abort();
         }

         @Override
         public void togglePaused() {
            acq_.setPaused(!acq_.isPaused());
         }

         @Override
         public boolean isPaused() {
            return acq_.isPaused();
         }

         @Override
         public void waitForCompletion() {
            acq_.waitForCompletion();
         }
      };

      viewer_ = new NDViewer(this, vai,
            summaryMetadata, AcqEngMetadata.getPixelSizeUm(summaryMetadata), AcqEngMetadata.isRGB(summaryMetadata));

      viewer_.setWindowTitle(name_ + (acq_ != null
            ? (acq_.areEventsFinished()? " (Finished)" : " (Running)") : " (Loaded)"));
      //add functions so display knows how to parse time and z infomration from image tags
      viewer_.setReadTimeMetadataFunction(AcqEngMetadata::getElapsedTimeMs);
      viewer_.setReadZMetadataFunction(AcqEngMetadata::getStageZIntended);
   }

   public void putImage(final TaggedImage taggedImg) {
      HashMap<String, Object> axes = AcqEngMetadata.getAxes(taggedImg.tags);

      storage_.putImage(taggedImg.pix, taggedImg.tags, axes,
               AcqEngMetadata.isRGB(taggedImg.tags),
               AcqEngMetadata.getBitDepth(taggedImg.tags),
               AcqEngMetadata.getHeight(taggedImg.tags),
               AcqEngMetadata.getWidth(taggedImg.tags));


      if (showViewer_) {
         //put on different thread to not slow down acquisition
         displayCommunicationExecutor_.submit(new Runnable() {
            @Override
            public void run() {
               HashMap<String, Object> axes = AcqEngMetadata.getAxes(taggedImg.tags);
               viewer_.newImageArrived(axes);
            }
         });
      }
   }

   ///////// Data source interface for Viewer //////////
   @Override
   public int[] getBounds() {
      return storage_.getImageBounds();
   }

   @Override
   public TaggedImage getImageForDisplay(HashMap<String, Object> axes, int resolutionindex,
                                         double xOffset, double yOffset, int imageWidth, int imageHeight) {

      return storage_.getDisplayImage(
            axes, resolutionindex, (int) xOffset, (int) yOffset,
            imageWidth, imageHeight);
   }

   @Override
   public Set<HashMap<String, Object>> getImageKeys() {
      return storage_.getAxesSet();
   }

   @Override
   public int getMaxResolutionIndex() {
      return storage_.getNumResLevels() - 1;
   }

   @Override
   public String getDiskLocation() {
      return dir_;
   }

   public void close() {
      storage_.close();
   }

   @Override
   public int getImageBitDepth(HashMap<String, Object> axesPositions) {
      return storage_.getEssentialImageMetadata(axesPositions).bitDepth;
   }

   ///////////// Data sink interface required by acq eng /////////////
   @Override
   public void finish() {
      if (storage_ != null) {
         if (!storage_.isFinished()) {
            //Get most up to date display settings
            if (viewer_ != null) {
               JSONObject displaySettings = viewer_.getDisplaySettingsJSON();
               storage_.setDisplaySettings(displaySettings);
            }
            storage_.finishedWriting();
         }
         if (!showViewer_) {
            //If there's no viewer, shutdown of acquisition == shutdown of storage
            close();
         }
      }

      if (showViewer_) {
         viewer_.setWindowTitle(name_ + " (Finished)");
         displayCommunicationExecutor_.shutdown();
      }
      finished_ = true;
   }

   @Override
   public boolean isFinished() {
      return finished_;
   }

   @Override
   public boolean anythingAcquired() {
      return acq_.anythingAcquired();
   }

}
