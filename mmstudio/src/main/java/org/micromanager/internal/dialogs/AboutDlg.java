///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, Dec 1, 2005
//
// COPYRIGHT:    University of California, San Francisco, 2006
//               100X Imaging Inc, www.100ximaging.com, 2008
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

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Insets;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.WindowConstants;
import javax.swing.border.BevelBorder;
import javax.swing.border.LineBorder;
import org.micromanager.internal.MMVersion;
import org.micromanager.internal.utils.GUIUtils;

/**
 * Help | About dialog for MMStudio.
 * Displays version, license, info, etc.
 */
public final class AboutDlg extends JDialog {
   private static final long serialVersionUID = 1L;
   private final JTextArea versionInfo_;
   
   public static String COPYRIGHT_TEXT = 
         "Copyright University of California San Francisco, 2010-2021. All rights reserved.\n\n"
         + "Additional copyright on portions of this software by the following institutions, "
         + "projects or individuals: Wayne Rasband, NIH, Joachim Walter, BeanShell, JSON, "
         + "logix4u, libserial, boost.org, Todd Klark, Ramon de Klein, "
         + "MIT, University of Dundee, Board of Regents of the University of Wisconsin-Madison, "
         + "Glencoe Software, and  SLF4J";

   /**
    * Creates the About Micro-Manager dialog.
    */
   public AboutDlg() {
      super();
      Dimension winSize = new Dimension(384, 392);
      setSize(winSize);
      setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
      setName("aboutDlg");
      setResizable(false);
      setModal(true);
      getContentPane().setLayout(null);
      setTitle("About Micro-Manager");
      
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      setLocation(screenSize.width / 2 - (winSize.width / 2),
            screenSize.height / 2 - (winSize.height / 2));

      final JLabel micromanageLabel = new JLabel();
      micromanageLabel.setFont(new Font("", Font.BOLD, 16));
      micromanageLabel.setText("Micro-Manager " + MMVersion.VERSION_STRING);
      micromanageLabel.setBounds(44, 11, 250, 23);
      getContentPane().add(micromanageLabel);

      final JLabel openSourceAutomatedLabel = new JLabel();
      openSourceAutomatedLabel.setFont(new Font("Arial", Font.PLAIN, 10));
      openSourceAutomatedLabel.setText("The Open Source Microscopy Software");
      openSourceAutomatedLabel.setBounds(44, 30, 329, 18);
      getContentPane().add(openSourceAutomatedLabel);

      final JLabel citeUs = new JLabel();
      citeUs.setFont(new Font("Arial", Font.PLAIN, 10));
      citeUs.setBorder(new LineBorder(Color.black, 1, false));
      citeUs.setText("<html>If you've found this software useful, please <a href=\"https://micro-manager.org/wiki/Citing_Micro-Manager\">cite Micro-Manager</a> in your publications.");
      citeUs.setBounds(5, 277, 368, 40);
      // When users click on the citation plea, we spawn a new thread to send
      // their browser to the MM wiki.
      citeUs.addMouseListener(new MouseAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            new Thread(GUIUtils.makeURLRunnable("https://micro-manager.org/wiki/Citing_Micro-Manager")).start();
         }
      });
      getContentPane().add(citeUs);


      final JButton okButton = new JButton();
      okButton.setFont(new Font("Arial", Font.PLAIN, 10));
      okButton.addActionListener(e -> dispose());
      okButton.setText("OK");
      okButton.setBounds(145, 322, 91, 28);
      getContentPane().add(okButton);

      versionInfo_ = new JTextArea();
      versionInfo_.setEditable(false);
      versionInfo_.setBorder(new BevelBorder(BevelBorder.LOWERED));
      versionInfo_.setFont(new Font("Arial", Font.PLAIN, 10));
      versionInfo_.setBounds(5, 49, 368, 66);
      getContentPane().add(versionInfo_);

      JTextArea contactInfoTextArea = new JTextArea();
      contactInfoTextArea.setEditable(false);
      contactInfoTextArea.setBorder(new LineBorder(Color.black, 1, false));
      contactInfoTextArea.setFont(new Font("Courier New", Font.PLAIN, 12));
      contactInfoTextArea.setText(" Website & Docs: https://micro-manager.org\n Support:        https://forum.image.sc\n                 (tag micro-manager)");
      contactInfoTextArea.setBounds(5, 219, 368, 47);
      getContentPane().add(contactInfoTextArea);

      final JLabel label = new JLabel();
      label.setIcon(new ImageIcon(
            getClass().getResource("/org/micromanager/icons/microscope.gif")));
      label.setBounds(6, 14, 32, 32);
      getContentPane().add(label);

      JTextArea welcomeTextArea = new JTextArea();
      welcomeTextArea.setBorder(new LineBorder(Color.black, 1, false));
      welcomeTextArea.setWrapStyleWord(true);
      welcomeTextArea.setText(COPYRIGHT_TEXT);
      welcomeTextArea.setMargin(new Insets(10, 10, 10, 10));
      welcomeTextArea.setLineWrap(true);
      welcomeTextArea.setFont(new Font("Arial", Font.PLAIN, 10));
      welcomeTextArea.setFocusable(false);
      welcomeTextArea.setEditable(false);
      welcomeTextArea.setBounds(5, 126, 368, 87);
      getContentPane().add(welcomeTextArea);
   }
   
   public void setVersionInfo(String verText) {
      versionInfo_.setText(verText);
   }

}
