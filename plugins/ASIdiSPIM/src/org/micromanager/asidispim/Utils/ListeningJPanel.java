
package org.micromanager.asidispim.Utils;

import java.awt.LayoutManager;
import javax.swing.JPanel;

/**
 *
 * @author nico
 */
public class ListeningJPanel extends JPanel {
   public ListeningJPanel(LayoutManager l) {
      super (l);
   }
   
   public  void gotSelected() {} ;
}
