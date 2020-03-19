/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.remote;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import mmcorej.TaggedImage;
import org.micromanager.acqj.api.AcquisitionEvent;
import org.micromanager.acqj.api.ImageAcqTuple;
import org.micromanager.acqj.api.TaggedImageProcessor;
import org.micromanager.acqj.internal.acqengj.AcquisitionBase;
import org.micromanager.internal.zmq.ZMQPullSocket;
import org.micromanager.internal.zmq.ZMQPushSocket;

/**
 *
 * @author henrypinkard
 */
public class TaggedImageProcessorSocket implements TaggedImageProcessor {

   private ExecutorService pushExecutor_, pullExecutor_;
   private volatile AcquisitionBase acq_;

   LinkedBlockingDeque<ImageAcqTuple> source_, sink_;

   ZMQPushSocket<TaggedImage> pushSocket_;
   ZMQPullSocket<TaggedImage> pullSocket_;

   public TaggedImageProcessorSocket(int pushPort, int pullPort) {
      pushSocket_ = new ZMQPushSocket<TaggedImage>(pushPort, "Image processor push socket");
//      pullSocket_ = new ZMQPullSocket<TaggedImage>(pullPort, "Image processor pull socket");

      pushExecutor_ = Executors.newSingleThreadExecutor(
              (Runnable r) -> new Thread(r, "Tagged Image socket"));
      pushExecutor_.submit(() -> {
         //take from source and push as fast as possible
         ImageAcqTuple tuple = source_.getFirst();
         acq_ = tuple.acq_;
         pushSocket_.push(tuple.img_);

         //TODO: exception handling/restarting?
      });

      pullExecutor_ = Executors.newSingleThreadExecutor(
              (Runnable r) -> new Thread(r, "Tagged Image socket"));
      pullExecutor_.submit(() -> {
         TaggedImage ti = pullSocket_.next();
         sink_.addLast(new ImageAcqTuple(ti, acq_));
         //TODO: error handling
      });
   }

   @Override
   public void setDequeues(LinkedBlockingDeque<ImageAcqTuple> source, LinkedBlockingDeque<ImageAcqTuple> sink) {
      source_ = source;
      sink_ = sink;
   }

}
