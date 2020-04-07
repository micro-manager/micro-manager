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
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.acqj.api.AcqEngMetadata;
import org.micromanager.acqj.api.TaggedImageProcessor;
import org.micromanager.internal.zmq.ZMQPullSocket;
import org.micromanager.internal.zmq.ZMQPushSocket;
import org.micromanager.internal.zmq.ZMQUtil;

/**
 *
 * @author henrypinkard
 */
public class RemoteImageProcessor implements TaggedImageProcessor {

   private ExecutorService pushExecutor_, pullExecutor_;

   volatile LinkedBlockingDeque<TaggedImage> source_, sink_;

   ZMQPushSocket<TaggedImage> pushSocket_;
   ZMQPullSocket<TaggedImage> pullSocket_;

   public RemoteImageProcessor() {
      pushSocket_ = new ZMQPushSocket<TaggedImage>(
              new Function<TaggedImage, JSONObject>() {
         @Override
         public JSONObject apply(TaggedImage t) {
            try {
               JSONObject json = new JSONObject();
               if (t.tags == null && t.pix == null) {
                  json.put("special", "finished");
               } else {
                  json.put("metadata", t.tags);
                  json.put("pixels", ZMQUtil.toJSON(t.pix));
               }
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
               if (t.has("special") && t.getString("special").equals("finished")) {
                  return new TaggedImage(null, null);
               } else {
                  JSONObject tags = t.getJSONObject("metadata");
                  Object pix = ZMQUtil.decodeArray(t.getString("pixels"),
                          AcqEngMetadata.getBytesPerPixel(tags) == 1 ? byte[].class : short[].class);
                  return new TaggedImage(pix, tags);
               }
            } catch (JSONException ex) {
               throw new RuntimeException(ex);
            }
         }
      });
      pushExecutor_ = Executors.newSingleThreadExecutor(
              (Runnable r) -> new Thread(r, "Tagged Image socket push"));
      pullExecutor_ = Executors.newSingleThreadExecutor(
              (Runnable r) -> new Thread(r, "Tagged Image socket pull"));
   }

   public int getPullPort() {
      return pullSocket_.getPort();
   }

   public int getPushPort() {
      return pushSocket_.getPort();
   }

   public void startPush() {
      pushExecutor_.submit(() -> {
         //take from source and push as fast as possible
         while (true) {
            if (source_ != null) {
               try {
                  TaggedImage img = source_.takeFirst();
                  pushSocket_.push(img);
               } catch (InterruptedException ex) {
                  return;
               } catch (Exception e) {
                  if (pullExecutor_.isShutdown()) {
                     return;
                  }
                  e.printStackTrace();
               }
            }
         }
      });
   }

   public void startPull() {
      pullExecutor_.submit(() -> {
         while (true) {
            if (sink_ != null) {
               try {
                  TaggedImage ti = pullSocket_.next();
                  sink_.putLast(ti);
               } catch (InterruptedException ex) {
                  return;
               } catch (Exception e) {
                  if (pullExecutor_.isShutdown()) {
                     return;
                  }
                  e.printStackTrace();
               }
            }
         }
      });
   }

   @Override
   public void setDequeues(LinkedBlockingDeque<TaggedImage> source, LinkedBlockingDeque<TaggedImage> sink) {
      source_ = source;
      sink_ = sink;
   }

   @Override
   public void close() {
      pullExecutor_.shutdownNow();
      pushExecutor_.shutdownNow();
      pushSocket_.close();
      pullSocket_.close();
   }

}
