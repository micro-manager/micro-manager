/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package imagedisplay;

import java.awt.BorderLayout;
import javax.swing.JPanel;
import mmcloneclasses.imagedisplay.ContrastMetadataCommentsPanel;

/**
 *
 * @author henrypinkard
 */
public class NonImagePanel  extends JPanel {
   
   private DisplayPlusControls displayControls_;
   private ContrastMetadataCommentsPanel cmcPanel_; 
   
   
   public NonImagePanel(ContrastMetadataCommentsPanel cmcPanel, DisplayPlusControls dpc) {
      super();
      this.setLayout(new BorderLayout());
      displayControls_ = dpc;
      this.add(displayControls_, BorderLayout.PAGE_START);
      cmcPanel_ = cmcPanel;
      this.add(cmcPanel_, BorderLayout.CENTER);
   }
   
   
   
}
