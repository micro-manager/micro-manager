///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger
//
// COPYRIGHT:    University of California, San Francisco, 2011, 2015
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

package org.micromanager.livedecon;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import net.miginfocom.swing.MigLayout;

import org.micromanager.data.ProcessorConfigurator;

import org.micromanager.PropertyMap;
import org.micromanager.Studio;

import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.MMFrame;

/**
 * This class provides the UI for setting up live deconvolution.
 */
public class DeconConfigurator extends MMFrame implements ProcessorConfigurator {
   private Studio studio_;
   private JCheckBox shouldSaveOriginals_;

   public DeconConfigurator(Studio studio) {
      studio_ = studio;
      JPanel contents = new JPanel(new MigLayout("flowx"));
      contents.add(new JLabel("<html>This plugin deconvolves Z-stacks as they are acquired.<br>Currently, no configuration of this plugin is available.</html>"), "span, wrap");
      add(contents);
      pack();
   }

   @Override
   public void showGUI() {
      setVisible(true);
   }

   @Override
   public PropertyMap getSettings() {
      return studio_.data().getPropertyMapBuilder().build();
   }

   @Override
   public void cleanup() {
      dispose();
   }
}
