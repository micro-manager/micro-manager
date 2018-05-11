///////////////////////////////////////////////////////////////////////////////
//FILE:          PtcToolsFrame.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman
//
// COPYRIGHT:    University of California, San Francisco, 2018
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


/**

 * Created on Aug 28, 2011, 9:41:57 PM
 */
package org.micromanager.ptctools;


import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;


import net.miginfocom.swing.MigLayout;

import org.micromanager.Studio;
import org.micromanager.internal.utils.MMFrame;
import org.micromanager.propertymap.MutablePropertyMapView;

/**
 * Micro-Manager plugin that can split the acquired image top-down or left-right
 * and arrange the split images along the channel axis.
 *
 * @author nico, modified by Chris Weisiger
 */
public class PtcToolsFrame extends MMFrame {
   private static final int DEFAULT_WIN_X = 100;
   private static final int DEFAULT_WIN_Y = 100;
   public static Boolean WINDOWOPEN = false;

   private final Studio studio_;
   private final MutablePropertyMapView settings_;
   private JTextField minExpTF_, maxExpTF_;
   private JSpinner nrFramesSp_, nrExpSp_;
   

   public PtcToolsFrame(Studio studio) {
      studio_ = studio;
      settings_ = studio_.getUserProfile().getSettings(this.getClass());
      
      initComponents();

      super.loadAndRestorePosition(DEFAULT_WIN_X, DEFAULT_WIN_Y);

   }


   private void initComponents() {
      setTitle("PTCTools");
      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

      setLayout(new MigLayout("flowx"));

      add(new JLabel(PtcToolsTerms.MINIMUMEXPOSURE), "");
      minExpTF_ = new JTextField(settings_.getString(PtcToolsTerms.MINIMUMEXPOSURE, 
              "0.0"));
      add(minExpTF_, "wrap");
      add(new JLabel(PtcToolsTerms.MAXIMUMEXPOSURE), "");
      maxExpTF_ = new JTextField(settings_.getString(PtcToolsTerms.MINIMUMEXPOSURE, 
              "0.0"));
      add(maxExpTF_, "wrap");
      add(new JLabel(PtcToolsTerms.NREXPOSURES), "");
      nrExpSp_ = new JSpinner();
      nrExpSp_.setModel(new SpinnerNumberModel(
              settings_.getInteger(PtcToolsTerms.NREXPOSURES, 30), 1, null, 1));
      add(nrExpSp_, "wrap");
      add(new JLabel(PtcToolsTerms.NRFRAMES), "");
      nrFramesSp_ = new JSpinner();
      nrFramesSp_.setModel(new SpinnerNumberModel(
              settings_.getInteger(PtcToolsTerms.NRFRAMES, 100), 1, null, 1));
      add(nrFramesSp_, "wrap");
      
      MMFrame ptf = this;
      JButton cancelButton = new JButton ("Cancel");
      cancelButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt){
            storeSettings();
            ptf.setVisible(false);
         }
      });
      add(cancelButton);
      
      JButton okButton = new JButton("OK");
      okButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent evt) {
            storeSettings();
            PtcToolsExecutor pte = new PtcToolsExecutor(studio_, 
                    settings_.toPropertyMap());
            ptf.setVisible(false);
         }
      });
      add(okButton);
      
      pack();
   }

   private void storeSettings() {
      settings_.putString(PtcToolsTerms.MINIMUMEXPOSURE, minExpTF_.getText());
      settings_.putString(PtcToolsTerms.MAXIMUMEXPOSURE, maxExpTF_.getText());
      settings_.putInteger(PtcToolsTerms.NREXPOSURES, (int) nrExpSp_.getValue());
      settings_.putInteger(PtcToolsTerms.NRFRAMES, (int) nrFramesSp_.getValue());
   }

   
}
