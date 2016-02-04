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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

import org.micromanager.asidispim.data.MyStrings;
import org.micromanager.asidispim.utils.ListeningJPanel;
import org.micromanager.asidispim.utils.MyDialogUtils;

import net.miginfocom.swing.MigLayout;

/**
 *
 * @author Jon
 */
@SuppressWarnings("serial")
public class HelpPanel extends ListeningJPanel {
   /**
    * 
    */
   public HelpPanel() {    
      super (MyStrings.PanelNames.HELP.toString(), 
            new MigLayout(
              "fill", 
              "[center]",
              "[]"));
      final JTextPane textPane = new JTextPane();
      textPane.setEditable(false);
      textPane.setContentType("text/html");
      textPane.setText(
            "This plugin is a work in progress; please contact the authors "
            + "with bug reports or feature requests "
            + "(<a href='mailto:jon@asiimaging.com'>jon@asiimaging.com</a>, "
            + "<a href='mailto:info@micro-manager.org'>info@micro-manager.org</a>)."
            + "<p>If you encounter bugs, the first step is to check and see if your "
            + "problem has already been fixed by using a recent nightly build of Micro-Manager. "
            + "If not, it is helpful to generate a problem report using \"Help\""
            + "-> \"Report Problem...\" in the main Micro-Manager window.  After clicking "
            + "\"Done\" you can submit it the usual way and it eventually will get to the "
            + "plugin developers or you can click \"View Report\", save the text as a file, "
            + "and then email that file directly to the plugin developers (Jon and Nico)."
            + "<p>Further information and instructions are on the Micro-Manager wiki "
            + "(<a href='http://micro-manager.org/wiki/ASIdiSPIM_Plugin'>"
            + "http://micro-manager.org/wiki/ASIdiSPIM_Plugin</a>)"
            + " as well as in the diSPIM User Manual ("
            + "<a href='http://www.asiimaging.com/downloads/manuals/diSPIM_Manual.pdf'>"
            + "http://www.asiimaging.com/downloads/manuals/diSPIM_Manual.pdf</a>)."
            );
      textPane.addHyperlinkListener(new HyperlinkListener() {
         @Override
         public void hyperlinkUpdate(HyperlinkEvent hle) {
            if (HyperlinkEvent.EventType.ACTIVATED.equals(hle.getEventType())) {
               try {
                   Desktop.getDesktop().browse(new URI(hle.getURL().toString()));
               } catch (URISyntaxException ex) {
                  MyDialogUtils.showError("Could not open web browser."); 
               } catch (IOException ex) {
                  MyDialogUtils.showError("Could not open web browser.");
               }

           }
         }
      });
      final JScrollPane editScroll = new JScrollPane(textPane);
      // TODO figure out way to ensure textPane wraps so we can be more elegant about letting
      // this fill the space without resorting to such heavy-handed measures
      editScroll.setMaximumSize(new Dimension(750, 300));
      editScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
      editScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
      add(editScroll, "center, grow");
   }//constructor
   
}
