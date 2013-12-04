///////////////////////////////////////////////////////////////////////////////
//FILE:          RegionPanel.java
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

import java.util.EnumMap;
import java.util.Map;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JSlider;
import net.miginfocom.swing.MigLayout;
import org.micromanager.api.ScriptInterface;

/**
 *
 * @author Nico
 */
public class RegionPanel extends JPanel{
   ScriptInterface gui_;
   Devices devices_;
   SpimParams spimParams_;
   
   public static enum Sides {A, B};
   Sides side_;
   public static final Map<Sides, String> SIDESMAP = 
           new EnumMap<Sides, String>(Sides.class);
   static {
      SIDESMAP.put(Sides.A, "A");
      SIDESMAP.put(Sides.B, "B");
   }
   
   public static enum Terms {MICROMIRROR, XYSTAGE, IMAGINGSHEET, 
      ILLUMINATIONSHEET};
   public static final Map<Terms, String> TERMSMAP = 
           new EnumMap<Terms, String>(Terms.class);
   static {
      TERMSMAP.put(Terms.MICROMIRROR,"MicroMirror");
      TERMSMAP.put(Terms.XYSTAGE, "XY Stage");
      TERMSMAP.put(Terms.IMAGINGSHEET, "ImagingSheet");
      TERMSMAP.put(Terms.ILLUMINATIONSHEET, "IlluminationSheet");
   }
   
   public RegionPanel(ScriptInterface gui, Devices devices, 
           SpimParams spimParams, Sides side) {
      super (new MigLayout(
              "", 
              "[right]8[align center]16[right]8[center]8[center]8[center]",
              "[]16[]"));
       devices_ = devices;
       gui_ = gui;
       side_ = side;
       
       add(new JLabel("Joystick:"));
       String[] joystickChoises = {TERMSMAP.get(Terms.MICROMIRROR), 
            TERMSMAP.get(Terms.XYSTAGE)};
       add(makeSelectionBox(joystickChoises));
       add(new JLabel("Imaging piezo:"));
       add(new JLabel("Pos"));
       add(new JButton("Set start"));
       add(new JButton("Set end"), "wrap");
       
       add(new JLabel("Right knob:"));
       String[] knobSelection = {TERMSMAP.get(Terms.IMAGINGSHEET), 
          TERMSMAP.get(Terms.ILLUMINATIONSHEET)};
       add(makeSelectionBox(knobSelection));
       add(new JLabel("Illumination piezo:"));
       add(new JLabel("Pos"));
       add(new JButton("Set position"), "span 2, center, wrap");
       
       add(new JLabel("Left knob:"));
       add(makeSelectionBox(knobSelection));
       add(new JLabel("Scan amplitude:"));
       add(new JLabel("Pos"));
       add(makeSlider("scanAmplitude", 0, 8, 4), "span 2, center, wrap");
 
       add(new JLabel("Scan enabled:"));
       add(makeCheckBox("name", Sides.A), "split 2");
       add(makeCheckBox("name", Sides.B));
       add(new JLabel("Scan offset:"));
       add(new JLabel("pos"));
       add(makeSlider("scanOffset", -4, 4, 0), "span 2, center, wrap");
       
       add(new JButton("Toggle scan"), "skip 1");
       add(new JLabel("Sheet position:"));
       add(new JLabel("pos"));
       add(new JButton("Set start"));
       add(new JButton("Set end"), "wrap");
       
       add(new JButton("Live"), "span, split 3, center");
       JRadioButton dualButton = new JRadioButton("Dual Camera");
       JRadioButton singleButton = new JRadioButton("Single Camera");
       ButtonGroup singleDualGroup = new ButtonGroup();
       singleDualGroup.add(dualButton);
       singleDualGroup.add(singleButton);
       add(singleButton, "center");
       add(dualButton, "center");
       
   }
   
   private JComboBox makeSelectionBox(String[] selections) {
      JComboBox jcb = new JComboBox(selections);
      //jcb.setSelectedItem(devices_.getAxisDirInfo(axis));
     // jcb.addActionListener(new DevicesPanel.AxisDirBoxListener(axis, jcb));
 
      return jcb;
   }
   
   private JSlider makeSlider(String name, int min, int max, int init) {
      JSlider js = new JSlider(JSlider.HORIZONTAL, min, max, init);
      js.setMajorTickSpacing(max - min);
      js.setMinorTickSpacing(1);
      js.setPaintTicks(true);
      js.setPaintLabels(true);

      return js;
   }

   /**
    * Constructs the JCheckBox through which the user can select sides
    * @param fastAxisDir name under which this axis is known in the Devices class
    * @return constructed JCheckBox
    */
   private JCheckBox makeCheckBox(String name, Sides side) {
      JCheckBox jc = new JCheckBox("Side " + SIDESMAP.get(side));
      //jc.setSelected(devices_.getFastAxisRevInfo(fastAxisDir));
      //jc.addActionListener(new DevicesPanel.ReverseCheckBoxListener(fastAxisDir, jc));
      
      return jc;
   }

}
