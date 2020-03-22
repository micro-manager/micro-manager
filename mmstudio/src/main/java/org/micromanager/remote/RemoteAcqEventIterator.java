/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.remote;

import java.util.Iterator;
import java.util.function.Function;
import org.json.JSONObject;
import org.micromanager.acqj.api.AcquisitionEvent;
import org.micromanager.internal.zmq.ZMQPullSocket;

/**
 *
 * @author henrypinkard
 */
public class RemoteAcqEventIterator implements Iterator<AcquisitionEvent>{
      
   private ZMQPullSocket<AcquisitionEvent> pullSocket_;
   private volatile AcquisitionEvent lastEvent_;
   private RemoteAcquisition acq_;
   
   public RemoteAcqEventIterator() {
      pullSocket_ = new ZMQPullSocket<AcquisitionEvent>(
        new Function<JSONObject, AcquisitionEvent>() {
         @Override
         public AcquisitionEvent apply(JSONObject t) {
            return AcquisitionEvent.fromJSON(t, acq_);
         }
      });     
   }


   @Override
   public boolean hasNext() {
      if (lastEvent_ != null && lastEvent_.isAcquisitionFinishedEvent()) {
         return false;
      }
      return true;
   }

   @Override
   public AcquisitionEvent next() {
      lastEvent_ = pullSocket_.next();
      return lastEvent_;
   }

   void setAcquisition(RemoteAcquisition aThis) {
      acq_ = aThis;
   }

   public int getPort() {
      return pullSocket_.getPort();
   }


   
}
