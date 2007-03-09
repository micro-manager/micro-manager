package org.micromanager;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;

import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SpringLayout;

import mmcorej.CMMCore;

import com.swtdesigner.SwingResourceManager;

public class XYPositionListDlg extends JDialog {

   private JTable table;
   private SpringLayout springLayout;
   private CMMCore core_;

   /**
    * Create the dialog
    */
   public XYPositionListDlg(CMMCore core) {
      super();
      core_ = core;
      setTitle("XY-position List");
      springLayout = new SpringLayout();
      getContentPane().setLayout(springLayout);
      setBounds(100, 100, 397, 455);

      final JScrollPane scrollPane = new JScrollPane();
      getContentPane().add(scrollPane);
      springLayout.putConstraint(SpringLayout.SOUTH, scrollPane, -16, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, scrollPane, 15, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, scrollPane, -124, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, scrollPane, 10, SpringLayout.WEST, getContentPane());

      table = new JTable();
      scrollPane.setViewportView(table);

      final JButton markButton = new JButton();
      markButton.setIcon(SwingResourceManager.getIcon(XYPositionListDlg.class, "icons/flag_green.png"));
      markButton.setText("Mark");
      getContentPane().add(markButton);
      springLayout.putConstraint(SpringLayout.SOUTH, markButton, 40, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, markButton, 17, SpringLayout.NORTH, getContentPane());

      final JButton removeButton = new JButton();
      removeButton.setIcon(SwingResourceManager.getIcon(XYPositionListDlg.class, "icons/cross.png"));
      removeButton.setText("Remove");
      getContentPane().add(removeButton);
      springLayout.putConstraint(SpringLayout.EAST, markButton, -9, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, markButton, 0, SpringLayout.WEST, removeButton);
      springLayout.putConstraint(SpringLayout.SOUTH, removeButton, 65, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, removeButton, 42, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, removeButton, -9, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, removeButton, -109, SpringLayout.EAST, getContentPane());

      final JButton closeButton = new JButton();
      closeButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            dispose();
         }
      });
      closeButton.setText("Close");
      getContentPane().add(closeButton);
      springLayout.putConstraint(SpringLayout.EAST, closeButton, -5, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, closeButton, 0, SpringLayout.WEST, removeButton);
      springLayout.putConstraint(SpringLayout.SOUTH, closeButton, 395, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, closeButton, 372, SpringLayout.NORTH, getContentPane());

      final JButton gotoButton = new JButton();
      gotoButton.setIcon(SwingResourceManager.getIcon(XYPositionListDlg.class, "icons/resultset_next.png"));
      gotoButton.setText("Go to");
      getContentPane().add(gotoButton);
      springLayout.putConstraint(SpringLayout.SOUTH, gotoButton, 140, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, gotoButton, 117, SpringLayout.NORTH, getContentPane());

      final JButton refreshButton = new JButton();
      refreshButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            refreshCurrentPosition();
         }
      });
      refreshButton.setIcon(SwingResourceManager.getIcon(XYPositionListDlg.class, "icons/arrow_refresh.png"));
      refreshButton.setText("Refresh");
      getContentPane().add(refreshButton);
      springLayout.putConstraint(SpringLayout.SOUTH, refreshButton, 165, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, refreshButton, 142, SpringLayout.NORTH, getContentPane());

      final JButton removeAllButton = new JButton();
      removeAllButton.setText("Remove all");
      getContentPane().add(removeAllButton);
      springLayout.putConstraint(SpringLayout.SOUTH, removeAllButton, 90, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, removeAllButton, 0, SpringLayout.SOUTH, removeButton);
      springLayout.putConstraint(SpringLayout.EAST, removeAllButton, 100, SpringLayout.WEST, removeButton);
      springLayout.putConstraint(SpringLayout.WEST, removeAllButton, 0, SpringLayout.WEST, removeButton);
      springLayout.putConstraint(SpringLayout.EAST, refreshButton, 0, SpringLayout.EAST, removeAllButton);
      springLayout.putConstraint(SpringLayout.WEST, refreshButton, -100, SpringLayout.EAST, removeAllButton);
      springLayout.putConstraint(SpringLayout.EAST, gotoButton, 0, SpringLayout.EAST, removeAllButton);
      springLayout.putConstraint(SpringLayout.WEST, gotoButton, -100, SpringLayout.EAST, removeAllButton);

      final JLabel xLabel_ = new JLabel();
      xLabel_.setText("X=");
      getContentPane().add(xLabel_);
      springLayout.putConstraint(SpringLayout.EAST, xLabel_, 0, SpringLayout.EAST, refreshButton);
      springLayout.putConstraint(SpringLayout.WEST, xLabel_, 0, SpringLayout.WEST, refreshButton);
      springLayout.putConstraint(SpringLayout.SOUTH, xLabel_, 195, SpringLayout.NORTH, getContentPane());

      final JLabel xLabel__1 = new JLabel();
      xLabel__1.setText("Y=");
      getContentPane().add(xLabel__1);
      springLayout.putConstraint(SpringLayout.SOUTH, xLabel__1, 219, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, xLabel__1, 205, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, xLabel__1, 100, SpringLayout.WEST, xLabel_);
      springLayout.putConstraint(SpringLayout.WEST, xLabel__1, 0, SpringLayout.WEST, xLabel_);
      //
   }

   protected void refreshCurrentPosition() {
      // TODO Auto-generated method stub
      
   }

}
