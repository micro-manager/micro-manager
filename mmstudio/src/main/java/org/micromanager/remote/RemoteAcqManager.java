/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.remote;

import mmcorej.CMMCore;
import org.micromanager.acqj.api.AcqEngineJ;
import org.micromanager.acqj.internal.acqengj.Engine;

/**
 *
 * @author henrypinkard
 */
public class RemoteAcqManager {
   
   private AcqEngineJ eng_;
   
   public RemoteAcqManager(CMMCore core) {
      eng_ = Engine.getInstance();
      if (eng_ == null) {
         eng_ = new Engine(core);
      }
   }
   
   public void clearImageProcessors() {
      eng_.clearImageProcessors();;
   }
   
   public RemoteImageProcessor createImageProcessor() {
      RemoteImageProcessor processor = new RemoteImageProcessor();
      eng_.addImageProcessor(processor);
      return processor;
   }
   
   public RemoteAcquisition createAcquisition(String dir, String name) {
      RemoteAcqEventIterator eventSource = new RemoteAcqEventIterator();
      RemoteAcquisitionSettings settings = new RemoteAcquisitionSettings();
      settings.dataLocation = dir;
      settings.showViewer = true;
      settings.name = name;
      
      return new RemoteAcquisition(eventSource, settings);
   }
   
}
