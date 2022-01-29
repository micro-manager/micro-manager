///////////////////////////////////////////////////////////////////////////////
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
// CVS:          $Id: PagePanel.java 4454 2010-05-04 05:47:21Z arthur $
//

package org.micromanager.internal.hcwizard;

import java.awt.Dialog;
import java.awt.Font;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import mmcorej.CMMCore;
import org.micromanager.Studio;
import org.micromanager.internal.utils.ReportingUtils;

/**
 * Wizard panel used as the abstract base class for all pages.
 */
public abstract class PagePanel extends JPanel {
   private static final long serialVersionUID = -4598248516499305300L;
   private static final Font HELP_FONT = new Font("Arial", Font.PLAIN, 14);
   protected MicroscopeModel model_;
   protected CMMCore core_;
   protected Studio studio_;
   protected String title_;
   protected Dialog parent_;

   public PagePanel() {
      super();
   }

   public void setModel(MicroscopeModel model, Studio studio) {
      model_ = model;
      studio_ = studio;
      core_ = studio_.core();
   }

   public void setTitle(String txt) {
      title_ = txt;
   }

   public String getTitle() {
      return title_;
   }

   public void setParentDialog(Dialog p) {
      this.parent_ = p;
   }

   public abstract boolean enterPage(boolean next);

   public abstract boolean exitPage(boolean next);

   public abstract void loadSettings();

   public abstract void saveSettings();


   protected void handleError(String txt) {
      JOptionPane.showMessageDialog(this, txt);
   }

   protected void handleException(Exception e) {
      ReportingUtils.showError(e);
   }

   protected static JTextArea createHelpText(String text) {
      JTextArea help = new JTextArea(text);
      help.setWrapStyleWord(true);
      help.setLineWrap(true);
      help.setEditable(false);
      help.setFont(HELP_FONT);
      return help;
   }

   public abstract void refresh();
}
