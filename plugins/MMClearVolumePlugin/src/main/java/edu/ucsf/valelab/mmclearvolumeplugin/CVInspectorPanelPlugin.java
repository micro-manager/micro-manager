/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.ucsf.valelab.mmclearvolumeplugin;

import org.micromanager.display.DataViewer;
import org.micromanager.display.inspector.InspectorPanelController;
import org.micromanager.display.inspector.InspectorPanelPlugin;

/**
 *
 * @author nico
 */
public class CVInspectorPanelPlugin implements InspectorPanelPlugin {

   @Override
   public boolean isApplicableToDataViewer(DataViewer viewer) {
      return viewer instanceof CVViewer;
   }

   @Override
   public InspectorPanelController createPanelController() {
      return new CVInspectorPanelController();
   }
   


  
}
