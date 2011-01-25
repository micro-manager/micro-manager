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

package org.micromanager;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.border.LineBorder;


import com.swtdesigner.SwingResourceManager;
import ij.IJ;
import java.util.ArrayList;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.border.EtchedBorder;
import org.micromanager.utils.FileDialogs;
import org.micromanager.utils.JavaUtils;

/**
 * Splash screen and introduction dialog. 
 * Opens up at startup and allows selection of the configuration file.
 */
public class MMIntroDlg extends JDialog {
   private static final long serialVersionUID = 1L;
   private JTextArea welcomeTextArea_;
   private JTextArea supportTextArea_;
   
   //private JTextField textFieldFile_;
   ArrayList<String> mruCFGFileList_;

   private JComboBox cfgFileDropperDown_;
   
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
      setSize(new Dimension(392, 533));
      Dimension winSize = getSize();
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      setLocation(screenSize.width/2 - (winSize.width/2), screenSize.height/2 - (winSize.height/2));

      JLabel introImage = new JLabel();
      introImage.setIcon(SwingResourceManager.getIcon(MMIntroDlg.class, "/org/micromanager/icons/splash.gif"));
      introImage.setLayout(null);
      introImage.setBounds(0, 0, 392, 197);
      introImage.setFocusable(false);
      introImage.setBorder(new LineBorder(Color.black, 1, false));
      introImage.setText("New JLabel");
      getContentPane().add(introImage);

      final JButton okButton = new JButton();
      okButton.setFont(new Font("Arial", Font.PLAIN, 10));
      okButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            setVisible(false);
         }
      });
      okButton.setText("OK");
      if (JavaUtils.isMac())
         okButton.setBounds(150, 497, 81, 24);
      else
         okButton.setBounds(150, 492, 81, 24);
      getContentPane().add(okButton);
      getRootPane().setDefaultButton(okButton);

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


      welcomeTextArea_ = new JTextArea() {
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
      welcomeTextArea_.setBounds(10, 284, 356, 205);
      getContentPane().add(welcomeTextArea_);

   }


   private class IOcfgFileFilter implements java.io.FileFilter {
    public boolean accept(File f) {
        String name = f.getName().toLowerCase();
        return name.endsWith("cfg");
    }//end accept
}//end class cfgFileFilter

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
            if( 6 <= mruCFGFileList_.size() )
                mruCFGFileList_.remove(mruCFGFileList_.size()-2);
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

  /*    for(File theMatch: matches){
          cfgFileDropperDown_.addItem(theMatch.getAbsolutePath());
          if(doesExist){
              if(theMatch.getAbsolutePath().equals(path)){
              cfgFileDropperDown_.setSelectedIndex(cfgFileDropperDown_.getItemCount()-1);
              }
          }
      }
      if(!doesExist){
          if( 0 < matches.length)
              cfgFileDropperDown_.setSelectedIndex(0);
      }
*/
  
      //textFieldFile_.setText(path);
   }
   
   public void setScriptFile(String path) {
   }
   
   public String getConfigFile() {
       String tvalue = new String(cfgFileDropperDown_.getSelectedItem().toString());
       String nvalue = new String("(none)");
       if( nvalue.equals(tvalue))
           tvalue = "";

      return tvalue;
   }
   
   public String getScriptFile() {
      return new String("");
   }
   
   protected void loadConfigFile() {
      File f = FileDialogs.openFile(this, "Choose a config file", MMStudioMainFrame.MM_CONFIG_FILE);
      if (f != null) {
         setConfigFile(f.getAbsolutePath());
      }
   }   
}
