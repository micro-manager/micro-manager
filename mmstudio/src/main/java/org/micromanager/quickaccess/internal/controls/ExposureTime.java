///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//-----------------------------------------------------------------------------
//
// AUTHOR:       Chris Weisiger, 2015
//
// COPYRIGHT:    University of California, San Francisco, 2015
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

package org.micromanager.quickaccess.internal.controls;

import com.google.common.eventbus.Subscribe;
import java.awt.Frame;
import java.text.ParseException;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.PropertyMaps;
import org.micromanager.Studio;
import org.micromanager.events.ExposureChangedEvent;
import org.micromanager.events.GUIRefreshEvent;
import org.micromanager.internal.MMStudio;
import org.micromanager.internal.utils.GUIUtils;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.quickaccess.WidgetPlugin;
import org.scijava.plugin.Plugin;
import org.scijava.plugin.SciJavaPlugin;

/**
 * Allows you to set the exposure time for the currently-selected channel.
 */
@Plugin(type = WidgetPlugin.class)
public final class ExposureTime extends WidgetPlugin implements SciJavaPlugin {
   private Studio studio_;

   @Override
   public void setContext(Studio studio) {
      studio_ = studio;
   }

   @Override
   public String getName() {
      return "Set Exposure Time";
   }

   @Override
   public String getHelpText() {
      return "Set the exposure time for the current channel";
   }

   @Override
   public String getVersion() {
      return "1.0";
   }

   @Override
   public String getCopyright() {
      return "Copyright (c) 2015 Open Imaging, Inc.";
   }

   @Override
   public ImageIcon getIcon() {
      return null;
   }

   // We are not actually configurable; config is ignored.
   @Override
   public JComponent createControl(PropertyMap config) {
      JPanel panel = new JPanel(new MigLayout("flowy, gap 0, insets 0"));
      JLabel label = new JLabel("Exposure (ms):");
      label.setFont(GUIUtils.buttonFont);
      panel.add(label);
      final JTextField text = new ExposureField(5);
      // Initialize display with current exposure time.
      reloadExposureTime(text);
      studio_.events().registerForEvents(text);
      panel.add(text);
      return panel;
   }

   /**
    * Reload our exposure time from the core.
    */
   private void reloadExposureTime(JTextField text) {
      try {
         text.setText(NumberUtils.doubleToDisplayString(studio_.core().getExposure()));
      }
      catch (Exception e) {
         studio_.logs().logError(e, "Error getting core exposure time");
      }
   }

   private void updateExposureTime(String text) {
      try {
         double time = NumberUtils.displayStringToDouble(text);
         ((MMStudio) studio_).setExposure(time);
      }
      catch (ParseException e) {
         // Ignore it.
      }
   }

   @Override
   public PropertyMap configureControl(Frame parent) {
      return PropertyMaps.builder().build();
   }

   /**
    * Simple class for handling changes to the exposure text field.
    */
   private class ExposureField extends JTextField {
      private boolean amMutating_ = false;

      public ExposureField(int numCols) {
         super(numCols);
         getDocument().addDocumentListener(new DocumentListener() {
            @Override
            public void changedUpdate(DocumentEvent e) {}
            @Override
            public void insertUpdate(DocumentEvent e) {
               amMutating_ = true;
               updateExposureTime(getText());
               amMutating_ = false;
               // Changing the exposure time loses focus, so request it back.
               requestFocus();
            }
            @Override
            public void removeUpdate(DocumentEvent e) {
               amMutating_ = true;
               updateExposureTime(getText());
               amMutating_ = false;
               // Changing the exposure time loses focus, so request it back.
               requestFocus();
            }
         });
      }

      @Subscribe
      public void onGUIRefresh(GUIRefreshEvent event) {
         reloadExposureTime(this);
      }

      @Subscribe
      public void onExposureChanged(ExposureChangedEvent event) {
         reloadExposureTime(this);
      }

      @Override
      public void setText(String text) {
         // Don't do redundant setting of the exposure time, or change it
         // in response to one of our own document handlers.
         if (!getText().equals(text) && !amMutating_) {
            super.setText(text);
         }
      }
   };
}
