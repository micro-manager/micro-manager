///////////////////////////////////////////////////////////////////////////////
//FILE:          MMAboutDlg.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, Dec 1, 2005
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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;
import javax.swing.border.BevelBorder;
import javax.swing.border.LineBorder;
import com.swtdesigner.SwingResourceManager;

/**
 * Help | About dialog for MMStudio.
 * Displays version, license, info, etc.
 */
public class MMAboutDlg extends JDialog {

   private JTextArea welcomeTextArea_;
   private JTextArea textArea_;
   private JTextArea versionInfo_;
   
   public static String COPYRIGHT_TEXT = 
      
      "Copyright University of California San Francisco, 2007. All rights reserved.\n\n" +
      "Additional copyright on portions of this software by the following institutions, projects or individuals:" +
      " Wayne Rasband, NIH, Joachim Walter, ACE, BeanShell, JSON, logix4u, libserial, Todd Klark and Ramon de Klein";
   
   public MMAboutDlg() {
      super();
      Dimension winSize = new Dimension(384, 322);
      setSize(winSize);
      setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      setName("aboutDlg");
      // Java 1.5 specific:
      //setAlwaysOnTop(true);
      setResizable(false);
      setModal(true);
      getContentPane().setLayout(null);
      setTitle("About Micro-Manager");
      
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      setLocation(screenSize.width/2 - (winSize.width/2), screenSize.height/2 - (winSize.height/2));

      final JLabel micromanageLabel = new JLabel();
      micromanageLabel.setFont(new Font("", Font.BOLD, 16));
      micromanageLabel.setText("Micro-Manager 1.0");
      micromanageLabel.setBounds(44, 11, 176, 23);
      getContentPane().add(micromanageLabel);

      final JLabel openSourceAutomatedLabel = new JLabel();
      openSourceAutomatedLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      openSourceAutomatedLabel.setText("The Open Source Microscope Software");
      openSourceAutomatedLabel.setBounds(44, 30, 200, 18);
      getContentPane().add(openSourceAutomatedLabel);

      final JButton okButton = new JButton();
      okButton.setFont(new Font("Arial", Font.PLAIN, 10));
      okButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent e) {
            dispose();
         }
      });
      okButton.setText("OK");
      okButton.setBounds(145, 260, 91, 28);
      getContentPane().add(okButton);

      versionInfo_ = new JTextArea();
      versionInfo_.setEditable(false);
      versionInfo_.setBorder(new BevelBorder(BevelBorder.LOWERED));
      versionInfo_.setFont(new Font("Arial", Font.PLAIN, 10));
      versionInfo_.setBounds(5, 49, 368, 66);
      getContentPane().add(versionInfo_);

      textArea_ = new JTextArea();
      textArea_.setEditable(false);
      textArea_.setBorder(new LineBorder(Color.black, 1, false));
      textArea_.setBackground(new Color(192, 192, 192));
      textArea_.setFont(new Font("Courier New", Font.PLAIN, 12));
      textArea_.setText(" home:               http://www.micro-manager.org\n" +
                                             " bug reports:        bugs@micro-manager.org\n" +
                                             " feature requests:   features@micro-manager.org\n");
      textArea_.setBounds(5, 205, 368, 47);
      getContentPane().add(textArea_);

      final JLabel label = new JLabel();
      label.setIcon(SwingResourceManager.getIcon(MMAboutDlg.class, "/org/micromanager/icons/microscope.gif"));
      label.setBounds(6, 14, 32, 32);
      getContentPane().add(label);

      welcomeTextArea_ = new JTextArea();
      welcomeTextArea_.setBorder(new LineBorder(Color.black, 1, false));
      welcomeTextArea_.setWrapStyleWord(true);
      welcomeTextArea_.setText(COPYRIGHT_TEXT);
      welcomeTextArea_.setMargin(new Insets(10, 10, 10, 10));
      welcomeTextArea_.setLineWrap(true);
      welcomeTextArea_.setFont(new Font("Arial", Font.PLAIN, 10));
      welcomeTextArea_.setFocusable(false);
      welcomeTextArea_.setEditable(false);
      welcomeTextArea_.setBackground(new Color(192, 192, 192));
      welcomeTextArea_.setBounds(5, 126, 368, 73);
      getContentPane().add(welcomeTextArea_);
   }
   
   public void setVersionInfo(String verText) {
      versionInfo_.setText(verText);
   }

}
