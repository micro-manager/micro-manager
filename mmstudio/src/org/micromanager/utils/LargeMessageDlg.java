///////////////////////////////////////////////////////////////////////////////
//FILE:          LargeMessageDlg.java
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------

//AUTHOR:       Nenad Amodaj, nenad@amodaj.com, October 1, 2006

//COPYRIGHT:    University of California, San Francisco, 2006

//LICENSE:      This file is distributed under the BSD license.
//License text is included with the source distribution.

//This file is distributed in the hope that it will be useful,
//but WITHOUT ANY WARRANTY; without even the implied warranty
//of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.

//IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

//CVS:          $Id$

package org.micromanager.utils;

import java.awt.Dimension;
import java.awt.Font;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.SpringLayout;

/**
 * Dialog box for displaying large error text messages.
 */
public class LargeMessageDlg extends JDialog {
   private static final long serialVersionUID = -2477635586817637967L;
   private SpringLayout springLayout;
   private JScrollPane scrollPane_;

   /**
    * Create the dialog
    */
   public LargeMessageDlg(String title, String message) {
      super();
      addWindowListener(new WindowAdapter() {
         public void windowOpened(WindowEvent e) {
            scrollPane_.getVerticalScrollBar().setValue(0);
         }
      });
      setResizable(false);
      springLayout = new SpringLayout();
      getContentPane().setLayout(springLayout);
      setTitle(title);
      setModal(true);
      setBounds(100, 100, 507, 327);

      Dimension winSize = getSize();
      Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
      setLocation(screenSize.width/2 - (winSize.width/2), screenSize.height/2 - (winSize.height/2));

      final JButton okButton = new JButton();
      okButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            dispose();
         }
      });
      okButton.setText("OK");
      getContentPane().add(okButton);
      springLayout.putConstraint(SpringLayout.SOUTH, okButton, 288, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, okButton, -30, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, okButton, 291, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, okButton, 208, SpringLayout.WEST, getContentPane());

      scrollPane_ = new JScrollPane();
      getContentPane().add(scrollPane_);
      springLayout.putConstraint(SpringLayout.SOUTH, scrollPane_, -35, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, scrollPane_, 5, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, scrollPane_, -5, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, scrollPane_, 5, SpringLayout.WEST, getContentPane());

      final JTextArea textPane = new JTextArea();
      textPane.setFont(new Font("Arial", Font.PLAIN, 12));
      textPane.setWrapStyleWord(true);
      textPane.setLineWrap(true);
      textPane.setEditable(false);
      textPane.setText(message);
      scrollPane_.setViewportView(textPane);
      //
   }

}
