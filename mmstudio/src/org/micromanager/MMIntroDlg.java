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
import javax.swing.JTextField;
import javax.swing.border.LineBorder;

import org.micromanager.utils.CfgFileFilter;
import com.swtdesigner.SwingResourceManager;

/**
 * Splash screen and introduction dialog. 
 * Opens up at startup and allows selection of the configuration file.
 */
public class MMIntroDlg extends JDialog {
   private static final long serialVersionUID = 1L;
   private JTextArea welcomeTextArea_;
   private JTextField textFieldFile_;
   
   public static String DISCLAIMER_TEXT = 
      
      "This software is distributed free of charge in the hope that it will be useful, " +
      "but WITHOUT ANY WARRANTY; without even the implied warranty " +
      "of merchantability or fitness for a particular purpose. In no event shall the copyright owner or contributors " +
      "be liable for any direct, indirect, incidental, special, examplary, or consequential damages.\n\n" +
      
      "Copyright University of California San Francisco, 2007. All rights reserved.\n\n";
   
   public MMIntroDlg(String ver) {
      super();
      setFont(new Font("Arial", Font.PLAIN, 10));
      setTitle("Micro-Manager Startup");
      getContentPane().setLayout(null);
      setName("Intro");
      setResizable(false);
      setModal(true);
      setSize(new Dimension(392, 453));
      Dimension winSize = getSize();
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      setLocation(screenSize.width/2 - (winSize.width/2), screenSize.height/2 - (winSize.height/2));

      JLabel introImage = new JLabel();
      introImage.setIcon(SwingResourceManager.getIcon(MMIntroDlg.class, "/org/micromanager/icons/splash.gif"));
      introImage.setLayout(null);
      introImage.setBounds(0, 0, 385, 197);
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
      okButton.setBounds(150, 390, 81, 24);
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
      loadConfigurationLabel.setText("Configuration file (leave blank for none)");
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

      textFieldFile_ = new JTextField();
      textFieldFile_.setFont(new Font("Arial", Font.PLAIN, 10));
      textFieldFile_.setBounds(5, 245, 342, 26);
      getContentPane().add(textFieldFile_);

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
      welcomeTextArea_.setBounds(10, 284, 366, 99);
      getContentPane().add(welcomeTextArea_);
      //
   }
   
   public void setConfigFile(String path) {
      textFieldFile_.setText(path);
   }
   
   public void setScriptFile(String path) {
   }
   
   public String getConfigFile() {
      return textFieldFile_.getText();
   }
   
   public String getScriptFile() {
      return new String("");
   }
   
   protected void loadConfigFile() {
      JFileChooser fc = new JFileChooser();
      fc.addChoosableFileFilter(new CfgFileFilter());
      fc.setSelectedFile(new File(textFieldFile_.getText()));
     
      int retVal = fc.showOpenDialog(this);
      if (retVal == JFileChooser.APPROVE_OPTION) {
         textFieldFile_.setText(fc.getSelectedFile().getAbsolutePath());
      }
   }
 
   
}
