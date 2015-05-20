///////////////////////////////////////////////////////////////////////////////
//FILE:          ListeningJTabbedPane.java
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

package org.micromanager.asidispim.Utils;

import javax.swing.JTabbedPane;

/**
 * Purpose of this class is to have the compiler check the type 
 * of the component being added to the master frame
 * @author nico
 */
@SuppressWarnings("serial")
public class ListeningJTabbedPane extends JTabbedPane {
   public void addLTab(ListeningJPanel panel) {
      addTab(panel.getPanelName(), panel);
   }
}
