/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.remote;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Function;
import mmcorej.TaggedImage;
import org.json.JSONException;
import org.json.JSONObject;
import org.micromanager.acqj.api.AcqEngMetadata;
import org.micromanager.acqj.api.ImageAcqTuple;
import org.micromanager.acqj.api.TaggedImageProcessor;
import org.micromanager.acqj.internal.acqengj.AcquisitionBase;
import org.micromanager.internal.zmq.ZMQPullSocket;
import org.micromanager.internal.zmq.ZMQPushSocket;
import org.micromanager.internal.zmq.ZMQUtil;

/**
 *
 * @author henrypinkard
 */
public class RemoteImageProcessor implements TaggedImageProcessor {

   private ExecutorService pushExecutor_, pullExecutor_;
   private volatile AcquisitionBase acq_;

   volatile LinkedBlockingDeque<ImageAcqTuple> source_, sink_;

   ZMQPushSocket<TaggedImage> pushSocket_;
   ZMQPullSocket<TaggedImage> pullSocket_;

   public RemoteImageProcessor() {
      pushSocket_ = new ZMQPushSocket<TaggedImage>(
              new Function<TaggedImage, JSONObject>() {
         @Override
         public JSONObject apply(TaggedImage t) {
            try {
               JSONObject json = new JSONObject();
               json.put("metadata", t.tags);
               json.put("pixels", ZMQUtil.toJSON(t.pix));
               return json;
            } catch (JSONException ex) {
               throw new RuntimeException(ex);
            }
         }
      });
      pullSocket_ = new ZMQPullSocket<TaggedImage>(
              new Function<JSONObject, TaggedImage>() {
         @Override
         public TaggedImage apply(JSONObject t) {
            try {
               JSONObject tags = t.getJSONObject("metadata");
               Object pix = ZMQUtil.decodeArray(t.getString("pixels"),
                       AcqEngMetadata.getBytesPerPixel(tags) == 1 ? byte[].class : short[].class);
               return new TaggedImage(pix, tags);
            } catch (JSONException ex) {
               throw new RuntimeException(ex);
            }
         }
      });

      pushExecutor_ = Executors.newSingleThreadExecutor(
              (Runnable r) -> new Thread(r, "Tagged Image socket push"));
      pushExecutor_.submit(() -> {
         //take from source and push as fast as possible
         while (true) {
            if (source_ != null) {
               ImageAcqTuple tuple = source_.getFirst();
               acq_ = tuple.acq_;
               pushSocket_.push(tuple.img_);
            }
         }
         //TODO: exception handling/restarting?
      });

      pullExecutor_ = Executors.newSingleThreadExecutor(
              (Runnable r) -> new Thread(r, "Tagged Image socket pull"));
      pullExecutor_.submit(() -> {
         while (true) {
            if (sink_ != null) {
               TaggedImage ti = pullSocket_.next();
               sink_.addLast(new ImageAcqTuple(ti, acq_));
            }
         }
         //TODO: error handling
      });
   }

   public int getPullPort() {
      return pullSocket_.getPort();
   }

   public int getPushPort() {
      return pushSocket_.getPort();
   }

   @Override
   public void setDequeues(LinkedBlockingDeque<ImageAcqTuple> source, LinkedBlockingDeque<ImageAcqTuple> sink) {
      source_ = source;
      sink_ = sink;
   }

}
