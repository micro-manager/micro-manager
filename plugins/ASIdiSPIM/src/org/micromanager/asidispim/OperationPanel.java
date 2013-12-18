///////////////////////////////////////////////////////////////////////////////
//FILE:          Operation.java
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

package org.micromanager.asidispim;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.prefs.Preferences;

import javax.swing.JButton;

import org.micromanager.asidispim.Data.Operation;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.Properties.PropTypes;
import org.micromanager.asidispim.Utils.ListeningJPanel;

import net.miginfocom.swing.MigLayout;

/**
 *
 * @author Jon
 */
public class OperationPanel extends ListeningJPanel {
   // list of strings used as keys in the Property class
   // initialized with corresponding property name in the constructor
   public static final String SPIM_STATE = "SPIMState";
   
   Operation oper_;
   Devices devices_;
   Properties props_;
   Preferences prefs_;
   JButton buttonStart_;
   
   
    
   /**
    * 
    * @param devices the (single) instance of the Devices class
    */
   public OperationPanel(Operation oper, Properties props, Devices devices) {    
      super (new MigLayout(
              "", 
              "[right]8[align center]16[right]8[center]8[center]8[center]",
              "[]16[]"));
      oper_ = oper;
      devices_ = devices;
      props_ = props;

      // TODO add selector for piezo mode (step/sweep), XYstage mode, etc.
       
      prefs_ = Preferences.userNodeForPackage(this.getClass());
      
      buttonStart_ = new JButton("Start!");
      buttonStart_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            props_.setPropValue(Operation.PZ_SPIM_STATE_A, "Armed");
            props_.setPropValue(Operation.PZ_SPIM_STATE_B, "Armed");
            props_.setPropValue(Operation.MM_SPIM_STATE, "Running");
            // TODO generalize this
         }
      });
      add(buttonStart_, "span 2, center, wrap");
       
   }
   
   @Override
   public void saveSettings() {

   }
   
    /**
    * Gets called when this tab gets focus.  Sets the physical UI in the Tiger
    * controller to what was selected in this pane
    */
   @Override
   public void gotSelected() {

   }
   
   
}
