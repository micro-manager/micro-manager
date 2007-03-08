package org.micromanager;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import javax.swing.JButton;

import javax.swing.JDialog;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SpringLayout;
import com.swtdesigner.SwingResourceManager;

public class XYPositionListDlg extends JDialog {

   private JTable table;
   private SpringLayout springLayout;
   /**
    * Launch the application
    * @param args
    */
   public static void main(String args[]) {
      try {
         XYPositionListDlg dialog = new XYPositionListDlg();
         dialog.addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
               System.exit(0);
            }
         });
         dialog.setVisible(true);
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   /**
    * Create the dialog
    */
   public XYPositionListDlg() {
      super();
      setTitle("XY-position List");
      springLayout = new SpringLayout();
      getContentPane().setLayout(springLayout);
      setBounds(100, 100, 372, 455);

      final JScrollPane scrollPane = new JScrollPane();
      getContentPane().add(scrollPane);
      springLayout.putConstraint(SpringLayout.SOUTH, scrollPane, -16, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, scrollPane, 15, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, scrollPane, -109, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, scrollPane, 10, SpringLayout.WEST, getContentPane());

      table = new JTable();
      scrollPane.setViewportView(table);

      final JButton setButton = new JButton();
      setButton.setText("Set");
      getContentPane().add(setButton);
      springLayout.putConstraint(SpringLayout.SOUTH, setButton, 40, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, setButton, 17, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, setButton, -9, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, setButton, -99, SpringLayout.EAST, getContentPane());

      final JButton removeButton = new JButton();
      removeButton.setText("Remove");
      getContentPane().add(removeButton);
      springLayout.putConstraint(SpringLayout.SOUTH, removeButton, 65, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, removeButton, 42, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, removeButton, -9, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, removeButton, -99, SpringLayout.EAST, getContentPane());

      final JButton closeButton = new JButton();
      closeButton.addActionListener(new ActionListener() {
         public void actionPerformed(ActionEvent arg0) {
            dispose();
         }
      });
      closeButton.setText("Close");
      getContentPane().add(closeButton);
      springLayout.putConstraint(SpringLayout.EAST, closeButton, -9, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, closeButton, -99, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, closeButton, 90, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, closeButton, 67, SpringLayout.NORTH, getContentPane());

      final JButton setButton_1 = new JButton();
      setButton_1.setText("Go to");
      getContentPane().add(setButton_1);
      springLayout.putConstraint(SpringLayout.EAST, setButton_1, -9, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, setButton_1, -99, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.SOUTH, setButton_1, 155, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, setButton_1, 132, SpringLayout.NORTH, getContentPane());

      final JButton setButton_1_1 = new JButton();
      setButton_1_1.setIcon(SwingResourceManager.getIcon(XYPositionListDlg.class, "icons/arrow_refresh.png"));
      setButton_1_1.setText("Refresh");
      getContentPane().add(setButton_1_1);
      springLayout.putConstraint(SpringLayout.EAST, setButton_1_1, 90, SpringLayout.WEST, setButton_1);
      springLayout.putConstraint(SpringLayout.WEST, setButton_1_1, 0, SpringLayout.WEST, setButton_1);
      springLayout.putConstraint(SpringLayout.SOUTH, setButton_1_1, 188, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, setButton_1_1, 165, SpringLayout.NORTH, getContentPane());
      //
   }

}
