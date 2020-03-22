/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.remote;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.json.JSONObject;
import org.micromanager.acqj.api.AcquisitionInterface;
import org.micromanager.acqj.api.DynamicSettingsAcquisition;
import org.micromanager.acqj.api.ExceptionCallback;
import org.micromanager.acqj.api.ImageAcqTuple;
import org.micromanager.ndviewer.api.ViewerAcquisitionInterface;

/**
 * Class that serves as the java counterpart to a python acquisition
 *
 *
 * @author henrypinkard
 */
public class RemoteAcquisition extends DynamicSettingsAcquisition
        implements AcquisitionInterface, ViewerAcquisitionInterface {

   private RemoteAcqEventIterator eventSource_;

   public RemoteAcquisition(RemoteAcqEventIterator eventSource, RemoteAcquisitionSettings settings) {
      super(settings, new RemoteDataManager(settings.showViewer,
              settings.dataLocation, settings.name));
      eventSource_ = eventSource;
      eventSource.setAcquisition(this);
   }
   
   public int getEventPort() {
      return eventSource_.getPort();
   }

   @Override
   public void start() {
       submitEventIterator(eventSource_, new ExceptionCallback() {
         @Override
         public void run(Exception e) {
            //TODO: abort somethign
            e.printStackTrace();
         }
      });
   }

   @Override
   protected void addToSummaryMetadata(JSONObject summaryMetadata) {

   }

   @Override
   protected void addToImageMetadata(JSONObject tags) {

   }

}
