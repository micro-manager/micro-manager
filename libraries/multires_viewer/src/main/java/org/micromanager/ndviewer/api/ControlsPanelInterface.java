/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.micromanager.ndviewer.api;

/**
 * Interface for adding custom control JPanels into the tabbed pane on right side
 * 
 * @author henrypinkard
 */
public interface ControlsPanelInterface {
   
   /**
    * The tab containing this panel was selected
    */
   public void selected();
   
   /**
   * A different tab was selected
   */
   public void deselected();
   
   /**
   * provide a String for the title of the panel in the tabbed pane
   */
   public String getTitle();
   
   /**
    * Viewer closing, release any resources
    */
   public void close();
   
}
