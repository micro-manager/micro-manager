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
import mmcorej.org.json.JSONObject;
import org.micromanager.acqj.api.AcqEngMetadata;
import org.micromanager.acqj.api.DataSink;
import org.micromanager.acqj.api.Acquisition;
import org.micromanager.multiresstorage.MultiResMultipageTiffStorage;
import org.micromanager.ndviewer.api.DataSourceInterface;
import org.micromanager.ndviewer.api.ViewerInterface;
import org.micromanager.ndviewer.main.NDViewer;
import org.micromanager.ndviewer.api.ViewerAcquisitionInterface;

/**
 * Manages where images go after being acquired (i.e. to some storage class)
 * and alerting the viewer (if applicable) to new data
 * @author henrypinkard
 */
public class RemoteViewerStorageAdapter implements DataSourceInterface, DataSink {

   private ExecutorService displayCommunicationExecutor_;

   private volatile ViewerInterface viewer_;
   private volatile RemoteAcquisition acq_;
   private volatile MultiResMultipageTiffStorage storage_;

   private final boolean showViewer_, storeData_;
   private String dir_;
   private String name_;

   public RemoteViewerStorageAdapter(boolean showViewer, 
           String dataStorageLocation, String name) {
      showViewer_ = showViewer;
      storeData_ = dataStorageLocation != null;
      dir_ = dataStorageLocation;
      name_ = name;
   }

   public void initialize(Acquisition acq, JSONObject summaryMetadata) {
      acq_ = (RemoteAcquisition) acq;

      if (storeData_) {
         int overlapX = 0, overlapY = 0; //Don't worry about multires features for now
         storage_ = new MultiResMultipageTiffStorage(dir_, name_,
                 summaryMetadata, overlapX, overlapY, AcqEngMetadata.getWidth(summaryMetadata),
                 AcqEngMetadata.getHeight(summaryMetadata),
                 AcqEngMetadata.getBytesPerPixel(summaryMetadata), false);
         name_ = storage_.getUniqueAcqName();
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
              ? (acq_.isFinished()? " (Finished)" : " (Running)") : " (Loaded)"));
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
      return dir_;
   }
   
   public void close() {
      //anything should be done here? cant think of it now...
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
      
      if (showViewer_) {
         viewer_.setWindowTitle(name_ + " (Finished)");
         displayCommunicationExecutor_.shutdown();
      }   
   }

   @Override
   public boolean anythingAcquired() {
      return acq_.anythingAcquired();
   }

}
