/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.remote;

import java.util.function.Function;
import mmcorej.org.json.JSONObject;
import org.micromanager.acqj.api.AcquisitionEvent;
import org.micromanager.acqj.api.AcquisitionHook;
import org.micromanager.acqj.api.AcquisitionInterface;
import org.micromanager.internal.zmq.ZMQPullSocket;
import org.micromanager.internal.zmq.ZMQPushSocket;

/**
 *
 * @author henrypinkard
 */
public class RemoteAcqHook implements AcquisitionHook {

   ZMQPushSocket<AcquisitionEvent> pushSocket_;
   ZMQPullSocket<AcquisitionEvent> pullSocket_;

   public RemoteAcqHook(AcquisitionInterface acq) {
      pushSocket_ = new ZMQPushSocket<AcquisitionEvent>(
              new Function<AcquisitionEvent, JSONObject>() {
         @Override
         public JSONObject apply(AcquisitionEvent t) {
            return t.toJSON();
         }
      });

      pullSocket_ = new ZMQPullSocket<AcquisitionEvent>(
              new Function<JSONObject, AcquisitionEvent>() {
         @Override
         public AcquisitionEvent apply(JSONObject t) {
            if (t.length() == 0) {
               return null; //Acq event has been deleted
            }
            return AcquisitionEvent.fromJSON(t, acq);
         }
      });
   }

   @Override
   public AcquisitionEvent run(AcquisitionEvent event) {
      pushSocket_.push(event);
      AcquisitionEvent ae = pullSocket_.next();
      return ae;
   }

   public int getPullPort() {
      return pullSocket_.getPort();
   }

   public int getPushPort() {
      return pushSocket_.getPort();
   }

   @Override
   public void close() {
      pushSocket_.close();
      pullSocket_.close();
   }

}
