///////////////////////////////////////////////////////////////////////////////
//FILE:          MMIntroDlg.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, Dec 1, 2005
//
// COPYRIGHT:    University of California, San Francisco, 2006
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

package org.micromanager.internal.dialogs;

import com.swtdesigner.SwingResourceManager;

import ij.IJ;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import java.util.ArrayList;

import javax.swing.BorderFactory;
import javax.swing.border.EtchedBorder;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.border.LineBorder;

import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.FileDialogs;
import org.micromanager.internal.utils.JavaUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Splash screen and introduction dialog. 
 * Opens up at startup and allows selection of the configuration file.
 */
public class MMIntroDlg extends JDialog {
   private static final long serialVersionUID = 1L;
   private static final String PREFS_USERNAMES = "Usernames";
   public static final String USERNAME_DEFAULT = "Default user";
   private static final String USERNAME_NEW = "Create new user";
   private JTextArea welcomeTextArea_;
   private boolean okFlag_ = true;
   
   ArrayList<String> mruCFGFileList_;

   private JComboBox cfgFileDropperDown_;
   private JComboBox userSelect_;
   
   public static String DISCLAIMER_TEXT = 
      
      "This software is distributed free of charge in the hope that it will be useful, " +
      "but WITHOUT ANY WARRANTY; without even the implied warranty " +
      "of merchantability or fitness for a particular purpose. In no event shall the copyright owner or contributors " +
      "be liable for any direct, indirect, incidental, special, examplary, or consequential damages.\n\n" +
      
      "Copyright University of California San Francisco, 2007, 2008, 2009, 2010. All rights reserved.";

   public static String SUPPORT_TEXT =
      "Micro-Manager was initially funded by grants from the Sandler Foundation and is now supported by a grant from the NIH.";

   public static String CITATION_TEXT =
      "If you have found this software useful, please cite Micro-Manager in your publications.";

   
   public MMIntroDlg(String ver, ArrayList<String> mruCFGFileList) {
      super();
      mruCFGFileList_ = mruCFGFileList;
      setFont(new Font("Arial", Font.PLAIN, 10));
      setTitle("Micro-Manager Startup");
      getContentPane().setLayout(null);
      setName("Intro");
      setResizable(false);
      setModal(true);
      setUndecorated(true);
      if (! IJ.isMacOSX())
        ((JPanel) getContentPane()).setBorder(BorderFactory.createLineBorder(Color.GRAY));
      setSize(new Dimension(392, 573));
      Dimension winSize = getSize();
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      setLocation(screenSize.width/2 - (winSize.width/2), screenSize.height/2 - (winSize.height/2));

      JLabel introImage = new JLabel();
      introImage.setIcon(SwingResourceManager.getIcon(MMIntroDlg.class, "/org/micromanager/internal/icons/splash.gif"));
      introImage.setLayout(null);
      introImage.setBounds(0, 0, 392, 197);
      introImage.setFocusable(false);
      introImage.setBorder(new LineBorder(Color.black, 1, false));
      introImage.setText("New JLabel");
      getContentPane().add(introImage);

      final JButton okButton = new JButton();
      okButton.setFont(new Font("Arial", Font.PLAIN, 10));
      okButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            okFlag_ = true;
            setVisible(false);
         }
      });
      okButton.setText("OK");
      okButton.setBounds(JavaUtils.isMac() ? 200 : 100, 537, 81, 24);
      getContentPane().add(okButton);
      getRootPane().setDefaultButton(okButton);
      okButton.requestFocusInWindow();
      
      final JButton cancelButton = new JButton();
      cancelButton.setFont(new Font("Arial", Font.PLAIN, 10));
      cancelButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            okFlag_ = false;
            setVisible(false);
         }
      });
      cancelButton.setText("Cancel");
      cancelButton.setBounds(JavaUtils.isMac() ? 100 : 200, 537, 81, 24);
      getContentPane().add(cancelButton);     

      final JLabel microscopeManagerLabel = new JLabel();
      microscopeManagerLabel.setFont(new Font("", Font.BOLD, 12));
      microscopeManagerLabel.setText("Micro-Manager startup configuration");
      microscopeManagerLabel.setBounds(5, 198, 259, 22);
      getContentPane().add(microscopeManagerLabel);

      final JLabel version10betaLabel = new JLabel();
      version10betaLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      version10betaLabel.setText("MMStudio Version " + ver);
      version10betaLabel.setBounds(5, 216, 193, 13);
      getContentPane().add(version10betaLabel);

      final JLabel loadConfigurationLabel = new JLabel();
      loadConfigurationLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      loadConfigurationLabel.setText("Configuration file:");
      loadConfigurationLabel.setBounds(5, 225, 319, 19);
      getContentPane().add(loadConfigurationLabel);

      final JButton browseButton = new JButton();
      browseButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            loadConfigFile();
         }
      });
      browseButton.setText("...");
      browseButton.setBounds(350, 245, 36, 26);
      getContentPane().add(browseButton);

      cfgFileDropperDown_ = new JComboBox();
      cfgFileDropperDown_.setFont(new Font("Arial", Font.PLAIN, 10));
      cfgFileDropperDown_.setBounds(5, 245, 342, 26);
      getContentPane().add(cfgFileDropperDown_);

      JLabel userProfileLabel = new JLabel("User profile:");
      userProfileLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      userProfileLabel.setBounds(5, 268, 319, 19);
      getContentPane().add(userProfileLabel);

      final Preferences prefs = Preferences.userNodeForPackage(MMIntroDlg.class);
      String[] users = new String[0];
      try {
         if (!prefs.nodeExists(PREFS_USERNAMES)) {
            // Create the list of user names, with two default options.
            Preferences node = prefs.node(PREFS_USERNAMES);
            node.put(USERNAME_DEFAULT, "");
            node.put(USERNAME_NEW, "");
         }
         users = prefs.node(PREFS_USERNAMES).keys();
      }
      catch (BackingStoreException e) {
         ReportingUtils.logError(e, "Couldn't recover list of user names");
      }
      final ArrayList<String> usersAsList = new ArrayList<String>(Arrays.asList(users));
      // HACK: put the "new" and "default" options first in the list.
      usersAsList.remove(USERNAME_DEFAULT);
      usersAsList.remove(USERNAME_NEW);
      usersAsList.add(0, USERNAME_DEFAULT);
      usersAsList.add(0, USERNAME_NEW);
      userSelect_ = new JComboBox();
      for (String userName : usersAsList) {
         userSelect_.addItem(userName);
      }
      userSelect_.setSelectedItem(USERNAME_DEFAULT);
      userSelect_.setBounds(5, 285, 342, 26);
      userSelect_.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            String userName = (String) userSelect_.getSelectedItem();
            if (!userName.contentEquals(USERNAME_NEW)) {
               return;
            }
            // Prompt the user for the new user name.
            userName = JOptionPane.showInputDialog("Please input the new user name:");
            if (usersAsList.contains(userName)) {
               ReportingUtils.showError("That user name is already in use.");
               return;
            }
            usersAsList.add(userName);
            userSelect_.addItem(userName);
            userSelect_.setSelectedItem(userName);
            prefs.node(PREFS_USERNAMES).put(userName, "");
         }
      });
      getContentPane().add(userSelect_);

      welcomeTextArea_ = new JTextArea() {
         @Override
         public Insets getInsets() {
            return new Insets(10,10,10,10);
         }
      };
      welcomeTextArea_.setBorder(new EtchedBorder());
      welcomeTextArea_.setWrapStyleWord(true);
      welcomeTextArea_.setText(DISCLAIMER_TEXT + "\n\n" + SUPPORT_TEXT + "\n\n" + CITATION_TEXT);

      welcomeTextArea_.setLineWrap(true);
      welcomeTextArea_.setFont(new Font("Arial", Font.PLAIN, 10));
      welcomeTextArea_.setFocusable(false);
      welcomeTextArea_.setEditable(false);
      welcomeTextArea_.setBackground(Color.WHITE);
      welcomeTextArea_.setBounds(10, 324, 356, 205);
      getContentPane().add(welcomeTextArea_);

   }

   public boolean okChosen() {
      return okFlag_;
   }

   public void setConfigFile(String path) {
      // using the provided path, setup the drop down list of config files in the same directory
      cfgFileDropperDown_.removeAllItems();
      //java.io.FileFilter iocfgFilter = new IOcfgFileFilter();
      File cfg = new File(path);
      Boolean doesExist = cfg.exists();

      if(doesExist)
      {
      // add the new configuration file to the list
        if(!mruCFGFileList_.contains(path)){
            // in case persistant data is inconsistent
            if( 6 <= mruCFGFileList_.size() ) {
                mruCFGFileList_.remove(mruCFGFileList_.size()-2);
            }
            mruCFGFileList_.add(0, path);
        }
      }
      // if the previously selected config file no longer exists, still use the directory where it had been stored
      //File cfgpath = new File(cfg.getParent());
     // File matches[] = cfgpath.listFiles(iocfgFilter);

      for (Object ofi : mruCFGFileList_){
          cfgFileDropperDown_.addItem(ofi.toString());
          if(doesExist){
              String tvalue = ofi.toString();
              if(tvalue.equals(path)){
                  cfgFileDropperDown_.setSelectedIndex(cfgFileDropperDown_.getItemCount()-1);
              }
          }
      }
      cfgFileDropperDown_.addItem("(none)");
      // selected configuration path does not exist
      if( !doesExist)
          cfgFileDropperDown_.setSelectedIndex(cfgFileDropperDown_.getItemCount()-1);

   }
      
   public String getConfigFile() {
       String tvalue = cfgFileDropperDown_.getSelectedItem().toString();
       String nvalue = "(none)";
       if( nvalue.equals(tvalue))
           tvalue = "";

      return tvalue;
   }

   public String getUserName() {
      return (String) userSelect_.getSelectedItem();
   }
   
   public String getScriptFile() {
      return "";
   }
   
   protected void loadConfigFile() {
      File f = FileDialogs.openFile(this, "Choose a config file", MMStudio.MM_CONFIG_FILE);
      if (f != null) {
         setConfigFile(f.getAbsolutePath());
      }
   }   
}
