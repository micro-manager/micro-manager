///////////////////////////////////////////////////////////////////////////////
//FILE:          MultiDPanel.java
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

import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Data.MulticolorModes;
import org.micromanager.asidispim.Data.MyStrings;
import org.micromanager.asidispim.Data.Prefs;
import org.micromanager.asidispim.Data.Properties;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;

import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;


/**
 *
 * @author Jon
 */
@SuppressWarnings("serial")
public class MultiDPanel extends ListeningJPanel {
   private final Devices devices_;
   private final Properties props_;
   private final Prefs prefs_;
   
   private final JPanel channelsPanel_;
   private final JComboBox numColors_;
   private final JComboBox colorMode_;
   
   
   /**
    * MultiD panel constructor.
    */
   public MultiDPanel(Devices devices, Properties props, Prefs prefs) {
      super (MyStrings.PanelNames.MULTID.toString(),
            new MigLayout(
              "", 
              "[right]",
              "[]6[]"));
      devices_ = devices;
      props_ = props;
      prefs_ = prefs;
      
      
      PanelUtils pu = new PanelUtils(prefs_, props_, devices_);
      
      // start channel sub-panel
      channelsPanel_ = new JPanel(new MigLayout(
              "",
              "[right]4[left]",
              "[]8[]"));
      
      channelsPanel_.setBorder(PanelUtils.makeTitledBorder("Channels"));
      
      channelsPanel_.add(new JLabel("Number of colors:"));
      String [] numColorsStrArray = {"1", "2", "3", "4"};
      numColors_ = pu.makeDropDownBox(numColorsStrArray, Devices.Keys.PLUGIN,
            Properties.Keys.PLUGIN_NUM_COLORS, numColorsStrArray[0]);
      
      channelsPanel_.add(numColors_, "wrap");
      channelsPanel_.add(new JLabel("Change color:"));
      MulticolorModes colorModes = new MulticolorModes(devices_, props_,
            Devices.Keys.PLUGIN, Properties.Keys.PLUGIN_MULTICOLOR_MODE,
            MulticolorModes.Keys.VOLUME);
      colorMode_ = colorModes.getComboBox(); 
      channelsPanel_.add(colorMode_, "wrap");
      
      // end channel sub-panel
      
      this.add(channelsPanel_);
     
      
   }// constructor

   
   
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
