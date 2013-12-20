///////////////////////////////////////////////////////////////////////////////
//FILE:          SpimParamsPanel.java
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

import org.micromanager.asidispim.Data.SpimParams;
import org.micromanager.asidispim.Data.Devices;
import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.asidispim.Utils.PanelUtils;
import org.micromanager.utils.ReportingUtils;

import javax.swing.JLabel;
import javax.swing.JComboBox;
import javax.swing.JSpinner;

import net.miginfocom.swing.MigLayout;

/**
 *
 * @author nico
 * @author Jon
 */
@SuppressWarnings("serial")
public class SpimParamsPanel extends ListeningJPanel {

   SpimParams spimParams_;
   Devices devices_;

   public SpimParamsPanel(SpimParams spimParams, Devices devices) {
      super(new MigLayout(
            "",
            "[right]16[center]16[center]",
            "[]12[]"));
      spimParams_ = spimParams;
      devices_ = devices;

      try {
         PanelUtils pu = new PanelUtils();
         JSpinner tmp_jsp;
         
         add(new JLabel("Number of sides:"), "split 2");
         tmp_jsp = pu.makeSpinnerInteger(SpimParams.NR_SIDES, 1, 2);
         spimParams_.addListener(tmp_jsp);
         add(tmp_jsp);

         add(new JLabel("First side:"), "align right");
         String[] ab = {SpimParams.FIRSTSIDE_A_VAL, SpimParams.FIRSTSIDE_B_VAL};
         JComboBox tmp_box = pu.makeDropDownBox(SpimParams.FIRSTSIDE, ab);
         // no listener here
         add(tmp_box, "wrap");

         add(new JLabel("Side A"), "cell 1 2");
         add(new JLabel("Side B"), "wrap");

         add(new JLabel("Number of repeats:"));
         tmp_jsp = pu.makeSpinnerInteger(SpimParams.NR_REPEATS, 1, 100);
         spimParams_.addListener(tmp_jsp);
         add(tmp_jsp, "span 2, wrap");

         add(new JLabel("Number of slices:"));
         tmp_jsp = pu.makeSpinnerInteger(SpimParams.NR_SLICES, 1, 99);
         spimParams_.addListener(tmp_jsp);
         add(tmp_jsp, "span 2, wrap");

         add(new JLabel("Lines scans per slice:"));
         tmp_jsp = pu.makeSpinnerInteger(SpimParams.NR_LINESCANS_PER_SLICE, 1, 1000);
         spimParams_.addListener(tmp_jsp);
         add(tmp_jsp, "span 2, wrap");

         add(new JLabel("Line scan period (ms):"));
         tmp_jsp = pu.makeSpinnerInteger(SpimParams.LINE_SCAN_PERIOD, 1, 10000);
         spimParams_.addListener(tmp_jsp);
         add(tmp_jsp);
         tmp_jsp = pu.makeSpinnerInteger(SpimParams.LINE_SCAN_PERIOD_B, 1, 10000);
         spimParams_.addListener(tmp_jsp);
         add(tmp_jsp, "wrap");

         add(new JLabel("Delay before each slice (ms):"));
         tmp_jsp = pu.makeSpinnerFloat(SpimParams.DELAY_BEFORE_SLICE, 0, 10000, 0.25);
         spimParams_.addListener(tmp_jsp);
         add(tmp_jsp, "span 2, wrap");

         add(new JLabel("Delay before each side (ms):"));
         tmp_jsp = pu.makeSpinnerFloat(SpimParams.DELAY_BEFORE_SIDE, 0, 10000, 0.25);
         spimParams_.addListener(tmp_jsp);
         add(tmp_jsp, "span 2, wrap");

      } catch (Exception ex) {
         ReportingUtils.showError("Error creating SpimParamsPanel, probably a type mismatch");
      }

   }


   /**
    * Gets called when this tab gets focus.  Refreshes values from properties.
    */
   @Override
   public void gotSelected() {
//         spimParams_.callAllListeners();
      // had problems with this, seem to be related to this tab being selected when plugin is started
      // TODO fix or decide to remove refresh
   }

}
