///////////////////////////////////////////////////////////////////////////////
//FILE:          PagePanel.java
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
// CVS:          $Id$
//

package org.micromanager.conf;

import java.awt.Dialog;
import java.awt.Frame;
import java.util.prefs.Preferences;

import javax.swing.JOptionPane;
import javax.swing.JPanel;

import mmcorej.CMMCore;
import org.micromanager.utils.ReportingUtils;

/**
 * Wizard panel used as the abstract base class for all pages.
 */
public abstract class PagePanel extends JPanel{
   private static final long serialVersionUID = -4598248516499305300L;
   protected MicroscopeModel model_;
   protected CMMCore core_;
   protected Preferences prefs_;
   protected String title_;
   protected String helpText_;
   protected String helpFileName_;
   protected Dialog parent_;
   
   protected static final String CFG_PATH = "cfg_path";
   
   public PagePanel() {
      super();
      title_ = new String();
   }

   public void setModel(MicroscopeModel model, CMMCore core) {
      model_ = model;
      core_ = core;
   }
   
   public void setTitle(String txt) {
      title_ = txt;
   }
   
   public void setHelpText(String txt) {
      helpText_ = txt;
   }
   
   public void setHelpFileName(String name) {
      helpFileName_ = name;
   }
   
   public String getHelpText() {
      return helpText_;
   }
   
   public String getTitle() {
      return title_;
   }
   
   public void setPrefrences(Preferences prefs) {
      prefs_ = prefs;
   }
      
   public String getHelpFileName() {
      return helpFileName_;
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

   public abstract void refresh();

}
