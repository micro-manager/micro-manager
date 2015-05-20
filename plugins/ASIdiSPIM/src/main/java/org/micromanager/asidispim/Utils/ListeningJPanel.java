///////////////////////////////////////////////////////////////////////////////
//FILE:          ListeningJPanel.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

package org.micromanager.asidispim.Utils;

import java.awt.LayoutManager;

import javax.swing.JPanel;

/**
 * Extension of JPanel that adds a few callbacks making it possible for the
 * enclosing frame to easily inform tabs of events
 * @author nico
 */
@SuppressWarnings("serial")
public class ListeningJPanel extends JPanel {
   
   protected String panelName_;
   
   public ListeningJPanel(String panelName, LayoutManager l) {
      super (l);
      panelName_ = panelName;
   }
   
   /**
    * Will be called when this Panel is selected in the parent TabbedPanel 
    */
   public void gotSelected() {};
   
   /**
    * Will be called when this Panel was active in the parent TabbedPanel
    *  and another one is becoming active instead 
    */
   public void gotDeSelected() {};
   
   /**
    * Should force the panel to write its current settings to its preferences
    */
   public void saveSettings() {};
   
   /**
    * Called when the plugin window is closing
    */
   public void windowClosing() {};
   
   /**
    * Called when new stage positions are available in the device class
    */
   public void updateStagePositions() {};
   
   /**
    * Called when stage position updates have stopped
    */
   public void stoppedStagePositions() {};
   
   /**
    * returns the name of the panel 
    * @return - panelName
    */
   public String getPanelName() { return panelName_; }
   
   /**
    * Called when the display should be refreshed, sort of like
    * user-defined version of repaint().  Used to refresh duration
    * labels in Acquisition tab when channels change.
    */
   public void refreshDisplay() {};
   
   /**
    * Called when the Panel loses focus but then regains it and certain
    * tasks need to be accomplished. For example after autofocus.
    */
   public void refreshSelected() {};
   
}
