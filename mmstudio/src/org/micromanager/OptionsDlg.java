///////////////////////////////////////////////////////////////////////////////
//FILE:          OptionsDlg.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, September 12, 2006
//
// COPYRIGHT:    University of California, San Francisco, 2006
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
//
// CVS:          $Id$

package org.micromanager;

import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JOptionPane;
import javax.swing.SpringLayout;

import mmcorej.CMMCore;

import org.micromanager.utils.MMDialog;

/**
 * Options dialog for MMStudio.
 *
 */
public class OptionsDlg extends MMDialog {
   
   private MMOptions opts_;
   private CMMCore core_;
   private SpringLayout springLayout;
   private Preferences mainPrefs_;

   /**
    * Create the dialog
    */
   public OptionsDlg(MMOptions opts, CMMCore core, Preferences mainPrefs) {
      super();
      addWindowListener(new WindowAdapter() {
         public void windowClosing(final WindowEvent e) {
            savePosition();
         }
      });
      setResizable(false);
      setModal(true);
      opts_ = opts;
      core_ = core;
      mainPrefs_ = mainPrefs;
      setTitle("Micro-Manager Options");
      springLayout = new SpringLayout();
      getContentPane().setLayout(springLayout);
      setBounds(100, 100, 362, 206);
      
      Preferences root = Preferences.userNodeForPackage(this.getClass());
      setPrefsNode(root.node(root.absolutePath() + "/OptionsDlg"));
      
      Rectangle r = getBounds();
      loadPosition(r.x, r.y, r.width, r.height);

      final JCheckBox debugLogEnabledCheckBox = new JCheckBox();
      debugLogEnabledCheckBox.setToolTipText("Set extra verbose logging for dubugging purposes");
      debugLogEnabledCheckBox.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            opts_.debugLogEnabled = debugLogEnabledCheckBox.isSelected();
            core_.enableDebugLog(opts_.debugLogEnabled);
         }
      });
      debugLogEnabledCheckBox.setText("Debug log enabled");
      getContentPane().add(debugLogEnabledCheckBox);
      springLayout.putConstraint(SpringLayout.SOUTH, debugLogEnabledCheckBox, 35, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, debugLogEnabledCheckBox, 12, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, debugLogEnabledCheckBox, 190, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, debugLogEnabledCheckBox, 10, SpringLayout.WEST, getContentPane());

      final JButton clearLogFileButton = new JButton();
      clearLogFileButton.setToolTipText("Erases all entries in the current log file (recommended)");
      clearLogFileButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            core_.clearLog();
         }
      });
      clearLogFileButton.setText("Clear log file");
      getContentPane().add(clearLogFileButton);
      springLayout.putConstraint(SpringLayout.EAST, clearLogFileButton, 110, SpringLayout.WEST, debugLogEnabledCheckBox);
      springLayout.putConstraint(SpringLayout.WEST, clearLogFileButton, 0, SpringLayout.WEST, debugLogEnabledCheckBox);
      springLayout.putConstraint(SpringLayout.SOUTH, clearLogFileButton, 118, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, clearLogFileButton, 95, SpringLayout.NORTH, getContentPane());

      final JButton clearRegistryButton = new JButton();
      clearRegistryButton.setToolTipText("Clears all persistent settings and returns to defaults");
      clearRegistryButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            try {
               boolean previouslyRegistered = mainPrefs_.getBoolean(RegistrationDlg.REGISTRATION, false);
               mainPrefs_.clear();
               Preferences acqPrefs = mainPrefs_.node(mainPrefs_.absolutePath() + "/" + AcqControlDlg.ACQ_SETTINGS_NODE);
               acqPrefs.clear();
               
               // restore registration flag
               mainPrefs_.putBoolean(RegistrationDlg.REGISTRATION, previouslyRegistered);
               
            } catch (BackingStoreException exc) {
               JOptionPane.showMessageDialog(OptionsDlg.this, exc.getMessage());
            }
         }
      });
      clearRegistryButton.setText("Clear registry");
      getContentPane().add(clearRegistryButton);
      springLayout.putConstraint(SpringLayout.EAST, clearRegistryButton, 110, SpringLayout.WEST, debugLogEnabledCheckBox);
      springLayout.putConstraint(SpringLayout.WEST, clearRegistryButton, 0, SpringLayout.WEST, debugLogEnabledCheckBox);
      springLayout.putConstraint(SpringLayout.SOUTH, clearRegistryButton, 148, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, clearRegistryButton, 125, SpringLayout.NORTH, getContentPane());

      final JButton okButton = new JButton();
      okButton.addActionListener(new ActionListener() {
         public void actionPerformed(final ActionEvent e) {
            savePosition();
            dispose();
         }
      });
      okButton.setText("Close");
      getContentPane().add(okButton);
      springLayout.putConstraint(SpringLayout.EAST, okButton, -5, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, okButton, 250, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, okButton, 35, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, okButton, 12, SpringLayout.NORTH, getContentPane());
      
      debugLogEnabledCheckBox.setSelected(opts_.debugLogEnabled);

      final JCheckBox multithreadedAcquisitionCheckBox = new JCheckBox();
      multithreadedAcquisitionCheckBox.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            opts_.multiThreadedAcqEnabled = multithreadedAcquisitionCheckBox.isSelected();
         }
      });
      multithreadedAcquisitionCheckBox.setText("Multi-threaded Acquisition");
      getContentPane().add(multithreadedAcquisitionCheckBox);
      springLayout.putConstraint(SpringLayout.EAST, multithreadedAcquisitionCheckBox, 180, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, multithreadedAcquisitionCheckBox, 0, SpringLayout.WEST, debugLogEnabledCheckBox);
      springLayout.putConstraint(SpringLayout.SOUTH, multithreadedAcquisitionCheckBox, 60, SpringLayout.NORTH, getContentPane());
      multithreadedAcquisitionCheckBox.setSelected(opts_.multiThreadedAcqEnabled);
   }

}
