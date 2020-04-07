/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.remote;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import mmcorej.org.json.JSONArray;
import mmcorej.org.json.JSONException;
import mmcorej.org.json.JSONObject;
import org.micromanager.acqj.api.AcquisitionEvent;
import org.micromanager.internal.zmq.ZMQPullSocket;

/**
 *
 * @author henrypinkard
 */
public class RemoteEventSource {

   private ZMQPullSocket<List<AcquisitionEvent>> pullSocket_;
   private RemoteAcquisition acq_;
   private ExecutorService executor_ = Executors.newSingleThreadExecutor((Runnable r) -> {
      return new Thread(r, "Remote Event Source thread");
   });

   public RemoteEventSource() {
      pullSocket_ = new ZMQPullSocket<List<AcquisitionEvent>>(
              new Function<JSONObject, List<AcquisitionEvent>>() {
         @Override
         public List<AcquisitionEvent> apply(JSONObject t) {
            try {
               List<AcquisitionEvent> eventList = new ArrayList<AcquisitionEvent>();
               JSONArray events = t.getJSONArray("events");
               for (int i = 0; i < events.length(); i++) {
                  JSONObject e = events.getJSONObject(i);
                  eventList.add(AcquisitionEvent.fromJSON(e, acq_));
               }
               return eventList;
            } catch (JSONException ex) {
               throw new RuntimeException("Incorrect format for acquisitio event");
            }
         }
      });
      //constantly poll the socket for more event sequences to submit
      executor_.submit(() -> {
         while (true) {
            try {
               List<AcquisitionEvent> eList = pullSocket_.next();
               boolean finished = eList.get(eList.size() - 1).isAcquisitionFinishedEvent();
               acq_.submitEventIterator(eList.iterator());
               if (finished || executor_.isShutdown()) {
                  executor_.shutdown();
                  pullSocket_.close();
                  return;
               }
            } catch (Exception e) {
               if (executor_.isShutdown()) {
                  return; //It was aborted
               }
               e.printStackTrace();
               throw new RuntimeException(e);
            }

         }
      });
   }

   void setAcquisition(RemoteAcquisition aThis) {
      acq_ = aThis;
   }

   public int getPort() {
      return pullSocket_.getPort();
   }

   /**
    * This method needed so the source can be shutdown from x out on the viewer, 
    * rather than sending a finished event like noremal
    */
   void abort() {
      executor_.shutdownNow();
      pullSocket_.close();
   }

}
