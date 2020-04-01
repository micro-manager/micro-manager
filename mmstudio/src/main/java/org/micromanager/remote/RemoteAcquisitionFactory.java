/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.remote;

import mmcorej.CMMCore;
import org.micromanager.acqj.internal.acqengj.Engine;

/**
 *
 * @author henrypinkard
 */
public class RemoteAcquisitionFactory {
   
   private Engine eng_;
   
   public RemoteAcquisitionFactory(CMMCore core) {
      eng_ = Engine.getInstance();
      if (eng_ == null) {
         eng_ = new Engine(core);
      }
   }
   
   public RemoteAcquisition createAcquisition(String dir, String name) {
      RemoteEventSource eventSource = new RemoteEventSource();
      RemoteAcquisitionSettings settings = new RemoteAcquisitionSettings();
      settings.dataLocation = dir;
      settings.showViewer = true;
      settings.name = name;
      
      return new RemoteAcquisition(eventSource, settings);
   }
   
   
}
