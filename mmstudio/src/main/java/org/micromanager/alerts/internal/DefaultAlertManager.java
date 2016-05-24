///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2016
//
// COPYRIGHT:    (c) 2016 Open Imaging, Inc.
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

package org.micromanager.alerts.internal;

import java.util.HashMap;

import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

import net.miginfocom.swing.MigLayout;

import org.micromanager.alerts.Alert;
import org.micromanager.alerts.AlertManager;
import org.micromanager.Studio;
import org.micromanager.internal.MMStudio;

public class DefaultAlertManager implements AlertManager {

   private static DefaultAlertManager staticInstance_;
   static {
      staticInstance_ = new DefaultAlertManager(MMStudio.getInstance());
   }

   private Studio studio_;
   private HashMap<Object, DefaultAlert> ownerToTextAlert_ = new HashMap<Object, DefaultAlert>();
   private HashMap<Object, DefaultAlert> ownerToCustomAlert_ = new HashMap<Object, DefaultAlert>();

   private DefaultAlertManager(Studio studio) {
      studio_ = studio;
   }

   @Override
   public Alert showTextAlert(String text) {
      return DefaultAlert.addOneShotAlert(studio_, text);
   }

   @Override
   public Alert showTextAlert(String text, Object owner) throws IllegalArgumentException {
      if (ownerToCustomAlert_.containsKey(owner)) {
         throw new IllegalArgumentException("Incompatible alert with owner " + owner + " already exists");
      }
      DefaultAlert alert;
      if (ownerToTextAlert_.containsKey(owner) &&
            ownerToTextAlert_.get(owner).isUsable()) {
         alert = ownerToTextAlert_.get(owner);
      }
      else {
         // Make a new Alert to hold messages from this owner.
         JPanel contents = new JPanel(
               new MigLayout("flowy, fill", "[fill, grow]", "[fill, grow]"));
         alert = DefaultAlert.addAlert(studio_, contents, false);
         ownerToTextAlert_.put(owner, alert);
      }
      alert.getContents().add(new JLabel(text), "growx");
      alert.pack();
      return alert;
   }

   @Override
   public Alert showAlert(JComponent contents, Object owner) {
      if (ownerToTextAlert_.containsKey(owner) ||
            ownerToCustomAlert_.containsKey(owner)) {
         throw new IllegalArgumentException("Alert with owner " + owner + " already exists");
      }
      JPanel panel = new JPanel(new MigLayout("insets 0, gap 0, fill"));
      panel.add(contents, "grow");
      DefaultAlert alert = DefaultAlert.addAlert(studio_, panel, false);
      ownerToCustomAlert_.put(owner, alert);
      return alert;
   }

   @Override
   public void dismissAlert(Object owner) {
      if (ownerToTextAlert_.containsKey(owner)) {
         ownerToTextAlert_.get(owner).dismiss();
         ownerToTextAlert_.remove(owner);
      }
      if (ownerToCustomAlert_.containsKey(owner)) {
         ownerToCustomAlert_.get(owner).dismiss();
         ownerToCustomAlert_.remove(owner);
      }
   }

   public static DefaultAlertManager getInstance() {
      return staticInstance_;
   }
}
