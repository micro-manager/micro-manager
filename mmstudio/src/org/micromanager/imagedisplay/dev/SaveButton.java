package org.micromanager.imagedisplay.dev;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;

import javax.swing.event.MouseInputAdapter;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

import org.micromanager.api.data.Datastore;
import org.micromanager.utils.ReportingUtils;

/**
 * This class provides a button for saving the current datastore to TIFF.
 */
public class SaveButton extends JButton {
   private JPopupMenu menu_;

   public SaveButton(final Datastore store) {
      menu_ = new JPopupMenu();
      JMenuItem separateImages = new JMenuItem("Save to separate image files");
      separateImages.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            store.save(Datastore.SaveMode.SEPARATE_TIFFS);
         }
      });
      menu_.add(separateImages);
      JMenuItem multistack = new JMenuItem("Save to single multistack image");
      multistack.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            store.save(Datastore.SaveMode.MULTISTACK_TIFF);
         }
      });
      menu_.add(multistack);

      final JButton staticThis = this;
      addMouseListener(new MouseInputAdapter() {
         @Override
         public void mousePressed(MouseEvent e) {
            menu_.show(staticThis, e.getX(), e.getY());
         }
      });

      setIcon(new javax.swing.ImageIcon(
               getClass().getResource("/org/micromanager/icons/disk.png")));
   }
}
