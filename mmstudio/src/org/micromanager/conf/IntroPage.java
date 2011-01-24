///////////////////////////////////////////////////////////////////////////////
//FILE:          IntroPage.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 29, 2006
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
//
package org.micromanager.conf;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.prefs.Preferences;

import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import org.micromanager.MMStudioMainFrame;

import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.ReportingUtils;

/**
 * The first page of the Configuration Wizard.
 */
public class IntroPage extends PagePanel {
   private static final long serialVersionUID = 1L;
   private ButtonGroup buttonGroup = new ButtonGroup();
   private JTextField filePathField_;
   private boolean initialized_ = false;
   private JRadioButton modifyRadioButton_;
   private JRadioButton createNewRadioButton_;
   private JButton browseButton_;
   private static final String HELP_FILE_NAME = "conf_intro_page.html";
   
   /**
    * Create the panel
    */
   public IntroPage(Preferences prefs) {
      super();
      title_ = "Select the configuration file";
      helpText_ = "Welcome to the Micro-Manager Configurator.\n" +
                  "The Configurator will guide you through the process of configuring the software to work with your hardware setup.\n" +
                  "In this step you choose if you are creating a new hardware configuration or editing an existing one.";
      
      setLayout(null);
      prefs_ = prefs;
      setHelpFileName(HELP_FILE_NAME);

      createNewRadioButton_ = new JRadioButton();
      createNewRadioButton_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            model_.reset();
            initialized_ = false;
            filePathField_.setEnabled(false);
            browseButton_.setEnabled(false);
         }
      });
      buttonGroup.add(createNewRadioButton_);
      createNewRadioButton_.setText("Create new configuration");
      createNewRadioButton_.setBounds(10, 31, 424, 23);
      add(createNewRadioButton_);

      modifyRadioButton_ = new JRadioButton();
      buttonGroup.add(modifyRadioButton_);
      modifyRadioButton_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            filePathField_.setEnabled(true);
            browseButton_.setEnabled(true);
            
         }
      });
      modifyRadioButton_.setText("Modify or explore existing configuration");
      modifyRadioButton_.setBounds(10, 55, 424, 23);
      add(modifyRadioButton_);

      filePathField_ = new JTextField();
      filePathField_.setBounds(10, 84, 424, 19);
      add(filePathField_);

      browseButton_ = new JButton();
      browseButton_.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            loadConfiguration();
         }
      });
      browseButton_.setText("Browse...");
      browseButton_.setBounds(440, 82, 100, 23);
      add(browseButton_);
      
      createNewRadioButton_.setSelected(true);
      filePathField_.setEnabled(false);
      browseButton_.setEnabled(false);      
   }
   
   public void loadSettings() {
      // load settings
      //filePathField_.setText(prefs_.get(CFG_PATH, ""));
      if (model_ != null)
         filePathField_.setText(model_.getFileName());
      
      if (filePathField_.getText().length() > 0) {
         modifyRadioButton_.setSelected(true);
         filePathField_.setEnabled(true);
         browseButton_.setEnabled(true);
     }
   }

   public void saveSettings() {
      // save settings
      //prefs_.put(CFG_PATH, filePathField_.getText());      
   }
   
   public boolean enterPage(boolean fromNextPage) {
      if (fromNextPage) {
         filePathField_.setText(model_.getFileName());
      }
      return true;
   }

   public boolean exitPage(boolean toNextPage) {
      if (modifyRadioButton_.isSelected() && (!initialized_ || filePathField_.getText().compareTo(model_.getFileName()) != 0)) {
         try {
            model_.loadFromFile(filePathField_.getText());
         } catch (MMConfigFileException e) {
            ReportingUtils.showError(e);
            model_.reset();
            return false;
         }
         initialized_ = true;
      }
      return true;
   }
   

   public void refresh() {
   }
   
   private void loadConfiguration() {
      File f = FileDialogs.openFile(parent_, "Choose a config file",
              MMStudioMainFrame.MM_CONFIG_FILE);
      if (f == null)
         return;
      filePathField_.setText(f.getAbsolutePath());
   }
}
