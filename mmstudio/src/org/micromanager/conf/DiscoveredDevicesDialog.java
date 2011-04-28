/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

/*
 * DiscoveredDevicesDialog.java
 *
 * Created on Apr 15, 2011, 10:05:51 AM
 */

package org.micromanager.conf;
import java.awt.Font;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.MouseEvent;
import java.util.EventObject;
import java.util.Vector;
import javax.swing.AbstractCellEditor;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JTree;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.TreeCellEditor;
import javax.swing.tree.TreeCellRenderer;
import javax.swing.tree.TreePath;

/**
 *
 * @author karlhoover
 */








public class DiscoveredDevicesDialog extends javax.swing.JDialog {



   class CheckBoxNodeRenderer implements TreeCellRenderer {

      private JCheckBox leafRenderer = new JCheckBox();
      private DefaultTreeCellRenderer nonLeafRenderer = new DefaultTreeCellRenderer();
      Color selectionBorderColor, selectionForeground, selectionBackground,
            textForeground, textBackground;

      protected JCheckBox getLeafRenderer() {
         return leafRenderer;
      }

      public CheckBoxNodeRenderer() {
         Font fontValue;
         fontValue = UIManager.getFont("Tree.font");
         if (fontValue != null) {
            leafRenderer.setFont(fontValue);
         }
         Boolean booleanValue = (Boolean) UIManager.get("Tree.drawsFocusBorderAroundIcon");
         leafRenderer.setFocusPainted((booleanValue != null)
                                      && (booleanValue.booleanValue()));
         selectionBorderColor = UIManager.getColor("Tree.selectionBorderColor");
         selectionForeground = UIManager.getColor("Tree.selectionForeground");
         selectionBackground = UIManager.getColor("Tree.selectionBackground");
         textForeground = UIManager.getColor("Tree.textForeground");
         textBackground = UIManager.getColor("Tree.textBackground");
      }

      public Component getTreeCellRendererComponent(JTree tree, Object value,
            boolean selected, boolean expanded, boolean leaf, int row,
            boolean hasFocus) {
         Component returnValue;
         if (leaf) {
            String stringValue = tree.convertValueToText(value, selected,
                                 expanded, leaf, row, false);
            leafRenderer.setText(stringValue);
            leafRenderer.setSelected(false);
            leafRenderer.setEnabled(tree.isEnabled());
            if (selected) {
               leafRenderer.setForeground(selectionForeground);
               leafRenderer.setBackground(selectionBackground);
            } else {
               leafRenderer.setForeground(textForeground);
               leafRenderer.setBackground(textBackground);
            }
            if ((value != null) && (value instanceof DefaultMutableTreeNode)) {
               Object userObject = ((DefaultMutableTreeNode) value).getUserObject();
               if (userObject instanceof CheckBoxNode) {
                  CheckBoxNode node = (CheckBoxNode) userObject;
                  leafRenderer.setText(node.getText());
                  leafRenderer.setSelected(node.isSelected());
               }
            }
            returnValue = leafRenderer;
         } else {
            returnValue = nonLeafRenderer.getTreeCellRendererComponent(tree,
                          value, selected, expanded, leaf, row, hasFocus);
         }
         return returnValue;
      }
   }

   class CheckBoxNodeEditor extends AbstractCellEditor implements TreeCellEditor {

      CheckBoxNodeRenderer renderer = new CheckBoxNodeRenderer();
      ChangeEvent changeEvent = null;
      JTree tree;

      public CheckBoxNodeEditor(JTree tree) {
         this.tree = tree;
      }

      public Object getCellEditorValue() {
         JCheckBox checkbox = renderer.getLeafRenderer();
         CheckBoxNode checkBoxNode = new CheckBoxNode(checkbox.getText(),
               checkbox.isSelected());
         return checkBoxNode;
      }

      public boolean isCellEditable(EventObject event) {
         boolean returnValue = false;
         if (event instanceof MouseEvent) {
            MouseEvent mouseEvent = (MouseEvent) event;
            TreePath path = tree.getPathForLocation(mouseEvent.getX(),
                                                    mouseEvent.getY());
            if (path != null) {
               Object node = path.getLastPathComponent();
               if ((node != null) && (node instanceof DefaultMutableTreeNode)) {
                  DefaultMutableTreeNode treeNode = (DefaultMutableTreeNode) node;
                  Object userObject = treeNode.getUserObject();
                  returnValue = ((treeNode.isLeaf()) && (userObject instanceof CheckBoxNode));
               }
            }
         }
         return returnValue;
      }

      public Component getTreeCellEditorComponent(JTree tree, Object value,
            boolean selected, boolean expanded, boolean leaf, int row) {
         Component editor = renderer.getTreeCellRendererComponent(tree, value,
                            true, expanded, leaf, row, true);
         // editor always selected / focused
         ItemListener itemListener = new ItemListener() {
            public void itemStateChanged(ItemEvent itemEvent) {
               if (stopCellEditing()) {
                  fireEditingStopped();
               }
            }
         };
         if (editor instanceof JCheckBox) {
            ((JCheckBox) editor).addItemListener(itemListener);
         }
         return editor;
      }
   }

   class CheckBoxNode {

      String deviceName_;
      String deviceDescription_;
      boolean selected;

      public CheckBoxNode(String concatenated, boolean selected) {
         String[] ss = concatenated.split("|");
         deviceName_ = ss[0].trim();
         deviceDescription_ = ss[1].trim();
         this.selected = selected;
      }

      public CheckBoxNode(String dname, String ddesc, boolean selected) {
         deviceName_ = dname;
         deviceDescription_ = ddesc;
         this.selected = selected;
      }

      public boolean isSelected() {
         return selected;
      }

      public void setSelected(boolean newValue) {
         selected = newValue;
      }

      public String getText() {
         return deviceName_ + " | " + deviceDescription_;
      }

      public String toString() {
         return getClass().getName() + "[" + getText() + "/" + selected + "]";
      }
   }

   class HubsNodeList extends Vector {

      String name;

      public HubsNodeList(String name) {
         this.name = name;
      }

      public HubsNodeList(String name, Object elements[]) {
         this.name = name;
         for (int i = 0, n = elements.length; i < n; i++) {
            add(elements[i]);
         }
      }

      public String toString() {
         return "Master Device: " + name + "] detected:";
      }
   }






   /** Creates new form DiscoveredDevicesDialog */
   public DiscoveredDevicesDialog(JDialog parent, boolean modal) {
      super(parent, modal);
      initComponents();
      CheckBoxNode ludlperipherals[] = {
         new CheckBoxNode(
            "LudlShutter", "Ludl Shutter", true),
         new CheckBoxNode("LudlShutter", "Ludl Shutter", true)
      };
      CheckBoxNode zeissperif[] = {
         new CheckBoxNode("ZeissWheel", "ZeissWheel", true),
         new CheckBoxNode("ZeissShutter", "ZeissWheel", true)
      };
      Vector ludlnotdes = new HubsNodeList("LudlHub",
                                           ludlperipherals);
      Vector zeissnodes = new HubsNodeList("ZeissScope", zeissperif);
      Object rootNodes[] = {ludlnotdes, zeissnodes};
      Vector rootVector = new HubsNodeList("Root", rootNodes);
      tree_ = new JTree(rootVector);
      CheckBoxNodeRenderer renderer = new CheckBoxNodeRenderer();
      tree_.setCellRenderer(renderer);
      tree_.setCellEditor(new CheckBoxNodeEditor(tree_));
      tree_.setEditable(true);
      tree_.setShowsRootHandles(false);
      tree_.setShowsRootHandles(true);
      jScrollPane1.setViewportView(tree_);
   }

   /** This method is called from within the constructor to
    * initialize the form.
    * WARNING: Do NOT modify this code. The content of this method is
    * always regenerated by the Form Editor.
    */
   @SuppressWarnings("unchecked")
   // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
   private void initComponents() {
      jScrollPane1 = new javax.swing.JScrollPane();
      tree_ = new javax.swing.JTree();
      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      jScrollPane1.setViewportView(tree_);
      org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
         layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(layout.createSequentialGroup()
              .addContainerGap()
              .add(jScrollPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 338, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
              .addContainerGap(42, Short.MAX_VALUE))
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(org.jdesktop.layout.GroupLayout.TRAILING, layout.createSequentialGroup()
              .addContainerGap(20, Short.MAX_VALUE)
              .add(jScrollPane1, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 275, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
              .addContainerGap())
      );
      pack();
   }// </editor-fold>//GEN-END:initComponents

   /**
   * @param args the command line arguments
   */


   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JScrollPane jScrollPane1;
   private javax.swing.JTree tree_;
   // End of variables declaration//GEN-END:variables

}
