/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.remote;

import java.util.HashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import mmcorej.TaggedImage;
import org.json.JSONObject;
import org.micromanager.acqj.api.AcqEngMetadata;
import org.micromanager.acqj.api.DataSink;
import org.micromanager.acqj.internal.acqengj.AcquisitionBase;
import org.micromanager.magellan.internal.magellanacq.MagellanAcquisition;
import org.micromanager.multiresstorage.MultiResMultipageTiffStorage;

/**
 * Adapter that connects acq eng data storage interface and tiff storage class
 *
 * @author henrypinkard
 */
public class TiffStorageAdapter implements DataSink {

   public static final String CHANNEL_AXIS = "c";

   private CopyOnWriteArrayList<String> channelNames_ = new CopyOnWriteArrayList<String>();

   private MultiResMultipageTiffStorage storage_;
   private JSONObject summaryMD_;
   private String dir_;

   public TiffStorageAdapter(String dir, JSONObject summaryMetadata) {
      summaryMD_ = summaryMetadata;
      dir_ = dir;
   }

   @Override
   public void initialize(AcquisitionBase acq, JSONObject summaryMetadata) {
      int overlapX = 0, overlapY = 0; //Don't worry about multires features for now
      storage_ = new MultiResMultipageTiffStorage(dir_,
              AcqEngMetadata.getSavingPrefix(summaryMetadata),
              summaryMD_, overlapX, overlapY, AcqEngMetadata.getWidth(summaryMetadata),
              AcqEngMetadata.getHeight(summaryMetadata),
              AcqEngMetadata.getBytesPerPixel(summaryMetadata));
   }

   @Override
   public void finished() {
      storage_.finishedWriting();
   }

   @Override
   public void putImage(TaggedImage taggedImg) {
      //Dynamically infer channel indices from channel names, since
      //they may have been arbitrarily modified along the way
      String channelName = AcqEngMetadata.getChannelName(taggedImg.tags);
      boolean newChannel = !channelNames_.contains(channelName);
      if (newChannel) {
         channelNames_.add(channelName);
      }
      HashMap<String, Integer> axes = AcqEngMetadata.getAxes(taggedImg.tags);
      axes.put(CHANNEL_AXIS, channelNames_.indexOf(channelName));

      storage_.putImage(taggedImg, axes);
   }

   @Override
   public boolean anythingAcquired() {
      return storage_ == null || !storage_.getAxesSet().isEmpty();
   }

   boolean isFinished() {
      return storage_.isFinished();
   }

   void finishedWriting() {
      storage_.finishedWriting();
   }

   void setDisplaySettings(JSONObject displaySettings) {
      storage_.setDisplaySettings(displaySettings);
   }

   TaggedImage getStitchedImage(HashMap<String, Integer> axes, int x, int y, int w, int h) {
      return storage_.getStitchedImage(axes, 0, x, y, w, h);
   }

}
