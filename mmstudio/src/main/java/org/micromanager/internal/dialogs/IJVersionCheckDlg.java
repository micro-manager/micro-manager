///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, cweisiger@msg.ucsf.edu, June 2015
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

import ij.ImageJ;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.Rectangle;
import java.util.Arrays;
import java.util.ArrayList;

import javax.swing.JDialog;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;

import net.miginfocom.swing.MigLayout;

import org.micromanager.internal.utils.DefaultUserProfile;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * This class checks the version of ImageJ the user is using, and shows a
 * warning dialog if that version has not been tested to work properly with
 * Micro-Manager.
 */
public class IJVersionCheckDlg extends JDialog {
   private static final String HAS_OPTED_OUT = "user has opted out of receiving warnings about compatibility with the version of ImageJ they are using";
   private static final ArrayList<String> ALLOWED_VERSIONS = new ArrayList<String>(
         Arrays.asList(new String[] {"1.49h"}));;

   /**
    * Show the warning dialog, if applicable and user has not opted out.
    */
   public static void execute() {
      if (ALLOWED_VERSIONS.contains(ImageJ.VERSION)) {
         // This version is okay.
         return;
      }
      ReportingUtils.logError("ImageJ version " + ImageJ.VERSION +
            " not guaranteed compatible with this version of Micro-Manager ");
      if (getHasOptedOut()) {
         // User doesn't care.
         return;
      }

      new IJVersionCheckDlg(ImageJ.VERSION);
   }

   /**
    * Returns true iff the user has opted out of receiving these errors in
    * future.
    */
   private static boolean getHasOptedOut() {
      return DefaultUserProfile.getInstance().getBoolean(
            IJVersionCheckDlg.class, HAS_OPTED_OUT, false);
   }

   /**
    * Set whether or not the user wants to see these errors in future.
    */
   private static void setHasOptedOut(boolean hasOptedOut) {
      DefaultUserProfile.getInstance().setBoolean(
            IJVersionCheckDlg.class, HAS_OPTED_OUT, hasOptedOut);
   }

   /**
    * Show the dialog.
    */
   public IJVersionCheckDlg(String badVersion) {
      super();
      setName("ImageJ Version Check");
      setModal(true);

      JPanel contents = new JPanel(new MigLayout("flowy"));
      boolean hasOneGoodVersion = ALLOWED_VERSIONS.size() == 1;
      // Build a comma-delimited string of good versions.
      String goodVersions = "";
      for (String version : ALLOWED_VERSIONS) {
         if (version != ALLOWED_VERSIONS.get(0)) {
            goodVersions += ", ";
         }
         goodVersions += version;
      }
      if (hasOneGoodVersion) {
         goodVersions = "is only tested with " + goodVersions;
      }
      else {
         goodVersions = "is only known to work with these versions: " + goodVersions;
      }
      JLabel warning = new JLabel(
            "<html><body>The version of ImageJ you are using is not guaranteed to be compatible with<br>this version of MicroManager. You are using version " +
            badVersion + ", while<br>this version of MicroManager " + goodVersions +
            ".</body></html>");

      warning.setIcon(UIManager.getIcon("OptionPane.warningIcon"));
      contents.add(warning);

      final JCheckBox optOut = new JCheckBox("Don't remind me again");
      optOut.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            setHasOptedOut(optOut.isSelected());
         }
      });
      contents.add(optOut);

      JButton okay = new JButton("Okay");
      okay.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            dispose();
         }
      });
      contents.add(okay, "align right");
      getContentPane().add(contents);
      pack();
      // Center us in the middle of the screen.
      Dimension size = getSize();
      Rectangle bounds = GUIUtils.getFullScreenBounds(
            GUIUtils.getGraphicsConfigurationContaining(1, 1));
      setLocation((int) (bounds.getX() + size.getWidth() / 2),
            (int) (bounds.getY() + size.getHeight() / 2));
      setVisible(true);
   }
}

