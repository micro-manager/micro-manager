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
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.border.LineBorder;

import org.micromanager.utils.CfgFileFilter;

import com.swtdesigner.SwingResourceManager;
import ij.IJ;
import java.util.ArrayList;
import java.util.Vector;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JPanel;

/**
 * Splash screen and introduction dialog. 
 * Opens up at startup and allows selection of the configuration file.
 */
public class MMIntroDlg extends JDialog {
   private static final long serialVersionUID = 1L;
   private JTextArea welcomeTextArea_;
   //private JTextField textFieldFile_;
   ArrayList<String> mruCFGFileList_;

   private JComboBox cfgFileDropperDown_;
   
   public static String DISCLAIMER_TEXT = 
      
      "This software is distributed free of charge in the hope that it will be useful, " +
      "but WITHOUT ANY WARRANTY; without even the implied warranty " +
      "of merchantability or fitness for a particular purpose. In no event shall the copyright owner or contributors " +
      "be liable for any direct, indirect, incidental, special, examplary, or consequential damages.\n\n" +
      
      "Copyright University of California San Francisco, 2007, 2008, 2009, 2010. All rights reserved.\n\n";
   
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
      setSize(new Dimension(392, 453));
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
      if (System.getProperty("os.name").indexOf("Mac OS X") != -1)
         okButton.setBounds(150, 397, 81, 24);
      else
         okButton.setBounds(150, 392, 81, 24);
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


      welcomeTextArea_ = new JTextArea();
      welcomeTextArea_.setBorder(new LineBorder(Color.black, 1, false));
      welcomeTextArea_.setWrapStyleWord(true);
      welcomeTextArea_.setText(DISCLAIMER_TEXT);
      
      welcomeTextArea_.setMargin(new Insets(10, 10, 10, 10));
      welcomeTextArea_.setLineWrap(true);
      welcomeTextArea_.setFont(new Font("Arial", Font.PLAIN, 10));
      welcomeTextArea_.setFocusable(false);
      welcomeTextArea_.setEditable(false);
      welcomeTextArea_.setBackground(new Color(192, 192, 192));
      welcomeTextArea_.setBounds(10, 284, 366, 105);
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
      JFileChooser fc = new JFileChooser();
      fc.addChoosableFileFilter(new CfgFileFilter());
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
      JFileChooser fc = new JFileChooser();
      fc.addChoosableFileFilter(new CfgFileFilter());
      String tstring = new String(cfgFileDropperDown_.getSelectedItem().toString());
      if( tstring.equals("(none"))
           tstring = "";

      fc.setSelectedFile(new File(tstring));
     
      int retVal = fc.showOpenDialog(this);
      if (retVal == JFileChooser.APPROVE_OPTION) {
         setConfigFile(fc.getSelectedFile().getAbsolutePath());
      }
   }
 
   
}
