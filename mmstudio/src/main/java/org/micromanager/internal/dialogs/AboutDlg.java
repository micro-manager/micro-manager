///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
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

import org.micromanager.internal.MMVersion;
import org.micromanager.internal.utils.GUIUtils;

import javax.swing.*;
import javax.swing.border.BevelBorder;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/** Help | About dialog for MMStudio. Displays version, license, info, etc. */
public final class AboutDlg extends JDialog {
  private static final long serialVersionUID = 1L;
  private final JTextArea welcomeTextArea_;
  private final JTextArea homeHttphcs100ximagingcomBugTextArea;
  private final JTextArea versionInfo_;

  public static String COPYRIGHT_TEXT =
      "Copyright University of California San Francisco, 2010. All rights reserved.\n\n"
          + "Additional copyright on portions of this software by the following institutions, projects or individuals:"
          + " Wayne Rasband, NIH, Joachim Walter, BeanShell, JSON, logix4u, libserial, boost.org, Todd Klark, Ramon de Klein, "
          + "MIT, University of Dundee, Board of Regents of the University of Wisconsin-Madison, Glencoe Software, and  SLF4J";

  public AboutDlg() {
    super();
    Dimension winSize = new Dimension(384, 392);
    setSize(winSize);
    setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
    setName("aboutDlg");
    // Java 1.5 specific:
    // setAlwaysOnTop(true);
    setResizable(false);
    setModal(true);
    getContentPane().setLayout(null);
    setTitle("About Micro-Manager");

    Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
    setLocation(
        screenSize.width / 2 - (winSize.width / 2), screenSize.height / 2 - (winSize.height / 2));

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
    citeUs.setText(
        "<html>If you've found this software useful, please <a href=\"https://micro-manager.org/wiki/Citing_Micro-Manager\">cite Micro-Manager</a> in your publications.");
    citeUs.setBounds(5, 277, 368, 40);
    // When users click on the citation plea, we spawn a new thread to send
    // their browser to the MM wiki.
    citeUs.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mousePressed(MouseEvent e) {
            new Thread(
                    GUIUtils.makeURLRunnable("https://micro-manager.org/wiki/Citing_Micro-Manager"))
                .start();
          }
        });
    getContentPane().add(citeUs);

    final JButton okButton = new JButton();
    okButton.setFont(new Font("Arial", Font.PLAIN, 10));
    okButton.addActionListener(
        new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            dispose();
          }
        });
    okButton.setText("OK");
    okButton.setBounds(145, 322, 91, 28);
    getContentPane().add(okButton);

    versionInfo_ = new JTextArea();
    versionInfo_.setEditable(false);
    versionInfo_.setBorder(new BevelBorder(BevelBorder.LOWERED));
    versionInfo_.setFont(new Font("Arial", Font.PLAIN, 10));
    versionInfo_.setBounds(5, 49, 368, 66);
    getContentPane().add(versionInfo_);

    homeHttphcs100ximagingcomBugTextArea = new JTextArea();
    homeHttphcs100ximagingcomBugTextArea.setEditable(false);
    homeHttphcs100ximagingcomBugTextArea.setBorder(new LineBorder(Color.black, 1, false));
    homeHttphcs100ximagingcomBugTextArea.setFont(new Font("Courier New", Font.PLAIN, 12));
    homeHttphcs100ximagingcomBugTextArea.setText(
        " home:               http://www.micro-manager.org\r\n bug reports:        bugs@micro-manager.org\r\n feature requests:   features@micro-manager.org\r\n");
    homeHttphcs100ximagingcomBugTextArea.setBounds(5, 219, 368, 47);
    getContentPane().add(homeHttphcs100ximagingcomBugTextArea);

    final JLabel label = new JLabel();
    label.setIcon(new ImageIcon(getClass().getResource("/org/micromanager/icons/microscope.gif")));
    label.setBounds(6, 14, 32, 32);
    getContentPane().add(label);

    welcomeTextArea_ = new JTextArea();
    welcomeTextArea_.setBorder(new LineBorder(Color.black, 1, false));
    welcomeTextArea_.setWrapStyleWord(true);
    welcomeTextArea_.setText(
        "Copyright University of California San Francisco, 2010. All rights reserved.\r\nCopyright 100X Imaging Inc, 2010. All rights reserved\r\n\r\nAdditional copyright on portions of this software by the following institutions, projects or individuals: Wayne Rasband, NIH, Joachim Walter, boost.org, BeanShell, JSON, logix4u, libserial, Todd Klark and Ramon de Klein");
    welcomeTextArea_.setMargin(new Insets(10, 10, 10, 10));
    welcomeTextArea_.setLineWrap(true);
    welcomeTextArea_.setFont(new Font("Arial", Font.PLAIN, 10));
    welcomeTextArea_.setFocusable(false);
    welcomeTextArea_.setEditable(false);
    welcomeTextArea_.setBounds(5, 126, 368, 87);
    getContentPane().add(welcomeTextArea_);
  }

  public void setVersionInfo(String verText) {
    versionInfo_.setText(verText);
  }
}
