/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.remote;

import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.acqj.api.AcqEngMetadata;
import org.micromanager.acqj.api.DataSink;
import org.micromanager.acqj.internal.acqengj.AcquisitionBase;
import org.micromanager.multiresstorage.MultiResMultipageTiffStorage;
import org.micromanager.ndviewer.api.DataSourceInterface;
import org.micromanager.ndviewer.api.ViewerInterface;
import org.micromanager.ndviewer.main.NDViewer;
import org.micromanager.ndviewer.api.ViewerAcquisitionInterface;

/**
 *
 * @author henrypinkard
 */
public class RemoteDataManager implements DataSourceInterface, DataSink {

   private ExecutorService displayCommunicationExecutor_;

   private volatile ViewerInterface viewer_;
   private volatile RemoteAcquisition acq_;
   private volatile MultiResMultipageTiffStorage storage_;

   private final boolean showViewer_, storeData_;
   private String dir_;
   private String name_;

   public RemoteDataManager(boolean showViewer, String dataStorageLocation,
           String name) {
      showViewer_ = showViewer;
      storeData_ = dataStorageLocation != null;
      dir_ = dataStorageLocation;
      name_ = name;
   }

   public void initialize(AcquisitionBase acq, JSONObject summaryMetadata) {
      acq_ = (RemoteAcquisition) acq;

      if (storeData_) {
         int overlapX = 0, overlapY = 0; //Don't worry about multires features for now
         storage_ = new MultiResMultipageTiffStorage(dir_, name_,
                 summaryMetadata, overlapX, overlapY, AcqEngMetadata.getWidth(summaryMetadata),
                 AcqEngMetadata.getHeight(summaryMetadata),
                 AcqEngMetadata.getBytesPerPixel(summaryMetadata), false);
      }

      if (showViewer_) {
         createDisplay(summaryMetadata);
      }

   }

   private void createDisplay(JSONObject summaryMetadata) {
      //create display
      displayCommunicationExecutor_ = Executors.newSingleThreadExecutor((Runnable r)
              -> new Thread(r, "Image viewer communication thread"));

      viewer_ = new NDViewer(this, (ViewerAcquisitionInterface) acq_,
              summaryMetadata, AcqEngMetadata.getPixelSizeUm(summaryMetadata));

      viewer_.setWindowTitle(name_ + (acq_ != null
              ? (acq_.isComplete() ? " (Finished)" : " (Running)") : " (Loaded)"));
      //add functions so display knows how to parse time and z infomration from image tags
      viewer_.setReadTimeMetadataFunction((JSONObject tags) -> AcqEngMetadata.getElapsedTimeMs(tags));
      viewer_.setReadZMetadataFunction((JSONObject tags) -> AcqEngMetadata.getZPositionUm(tags));
   }

   public void putImage(final TaggedImage taggedImg) {
      HashMap<String, Integer> axes = AcqEngMetadata.getAxes(taggedImg.tags);
      storage_.putImage(taggedImg, axes);

      if (showViewer_) {
         //put on different thread to not slow down acquisition
         displayCommunicationExecutor_.submit(new Runnable() {
            @Override
            public void run() {
               viewer_.newImageArrived(AcqEngMetadata.getAxes(taggedImg.tags),
                       AcqEngMetadata.getChannelName(taggedImg.tags));
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
   public TaggedImage getImageForDisplay(HashMap<String, Integer> axes, int resolutionindex,
           double xOffset, double yOffset, int imageWidth, int imageHeight) {

      //TODO: what if you want to use viewer, but an external data source
       int resIndex = 0;
      return storage_.getStitchedImage(
              axes, resIndex, (int) xOffset, (int) yOffset,
              imageWidth, imageHeight);
   }

   @Override
   public int getMaxResolutionIndex() {
      return 0; //No multi resolution support for now
   }

   @Override
   public String getDiskLocation() {
      return null; //Doesn't matter for now
   }

   ///////////// Data sink interface required by acq eng /////////////
   @Override
   public void finished() {
      if (storage_ != null) {
         if (!storage_.isFinished()) {
            //Get most up to date display settings
            if (viewer_ != null) {
               JSONObject displaySettings = viewer_.getDisplaySettingsJSON();
               storage_.setDisplaySettings(displaySettings);
            }
            storage_.finishedWriting();
         }
      }
      viewer_.setWindowTitle(name_ + " (Finished)");
   }

   @Override
   public boolean anythingAcquired() {
      return acq_.anythingAcquired();
   }

}
