
package org.micromanager.asidispim.Utils;

import javax.swing.JTabbedPane;

/**
 * Purpose of this class is to have the compiler check the type 
 * of the component being added to the master frame
 * @author nico
 */
public class ListeningJTabbedPane extends JTabbedPane {
   public void addLTab(String name, ListeningJPanel panel) {
      addTab(name, panel);
   }
}
