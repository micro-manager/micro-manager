///////////////////////////////////////////////////////////////////////////////
//FILE:          HelpPanel.java
//PROJECT:       Micro-Manager 
//SUBSYSTEM:     ASIdiSPIM plugin
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nico Stuurman, Jon Daniels
//
// COPYRIGHT:    University of California, San Francisco, & ASI, 2013
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

package org.micromanager.asidispim;


import java.awt.Desktop;
import java.awt.Dimension;
import java.net.URI;

import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.micromanager.asidispim.Utils.ListeningJPanel;
import org.micromanager.utils.ReportingUtils;

import net.miginfocom.swing.MigLayout;

/**
 *
 * @author Jon
 */
@SuppressWarnings("serial")
public class HelpPanel extends ListeningJPanel {
     
   /**
    * 
    * @param devices the (single) instance of the Devices class
    */
   public HelpPanel() {    
      super (new MigLayout(
              "", 
              "[right]8[align center]16[right]8[center]8[center]8[center]",
              "[]16[]"));
     
      final JTextPane textPane = new JTextPane();
      textPane.setEditable(false);
      textPane.setContentType("text/html");
      textPane.setText("This plugin is a work in progress, and please contact the authors "
            + "if you have bug reports or feature requests.  Further information, "
            + "plus instructions are at "
            + "<a href='http://micro-manager.org/wiki/ASIdiSPIM_Plugin'>"
            + "http://micro-manager.org/wiki/ASIdiSPIM_Plugin</a>.");
      textPane.addHyperlinkListener(new HyperlinkListener() {
         @Override
         public void hyperlinkUpdate(HyperlinkEvent hle) {
            if (HyperlinkEvent.EventType.ACTIVATED.equals(hle.getEventType())) {
               try {
                   Desktop.getDesktop().browse(new URI(hle.getURL().toString()));
               } catch (Exception ex) {
                  ReportingUtils.showError("Could not open web browser."); 
               }

           }
         }
      });
      final JScrollPane editScroll = new JScrollPane(textPane);
      editScroll.setPreferredSize(new Dimension(500,250));
      editScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
      editScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      add(editScroll);
      
      
      
   }
   
   @Override
   public void saveSettings() {

   }
   
    /**
    * Gets called when this tab gets focus.
    */
   @Override
   public void gotSelected() {

   }
   
   
}
