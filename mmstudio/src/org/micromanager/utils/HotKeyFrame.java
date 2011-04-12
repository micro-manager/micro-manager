
/*
 * HotKeys.java
 *
 * Created on Apr 11, 2011, 3:59:44 PM
 */

package org.micromanager.utils;

import java.awt.Component;
import java.awt.event.KeyEvent;
import java.util.Iterator;
import java.util.Map;
import javax.swing.AbstractCellEditor;
import javax.swing.DefaultCellEditor;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JComboBox;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;

/**
 *
 * @author nico
 */
public final class HotKeyFrame extends javax.swing.JFrame {
   ShortCutTableModel sctModel_ = new ShortCutTableModel();
   JComboBox combo_ = new JComboBox();
 
   private class ShortCutTableModel extends AbstractTableModel {

      private static final int columnCount_ = 2;

      public int getRowCount() {
         if (HotKeys.keys_ != null)
            return HotKeys.keys_.size() + 1;
         return 0;
      }

      public int getColumnCount() {
         return columnCount_;
      }

      @Override
      public String getColumnName(int columnIndex) {
         if (columnIndex == 0)
            return "Action";
         return "HotKey";
      }

      public Object getValueAt(int row, int column) {
         if (HotKeys.keys_ == null)
            return null;
         if (row > HotKeys.keys_.size())
            return null;
         Iterator it = HotKeys.keys_.entrySet().iterator();
         int i = 0;
         while (it.hasNext()) {
            Map.Entry pairs = (Map.Entry)it.next();
            if (i == row) {
               if (column == 0) {
                  HotKeyAction ha = (HotKeyAction) pairs.getValue();
                  if (ha != null) {
                     if (ha.type_ == HotKeyAction.GUICOMMAND) {
                        return HotKeyAction.guiItems_[ha.guiCommand_];
                     } else
                        return ha.beanShellScript_.getAbsoluteFile();
                  }
               }  else if (column == 1)
                  return KeyEvent.getKeyText((Integer)pairs.getKey());
            }
            i++;
         }
         return null;
      }

      @Override
      public boolean isCellEditable(int row, int col) {
         if (col == 1)
            return true;
         return false;
      }


      @Override
      public void setValueAt(Object value, int row, int col) {
         if (col == 0) {
            int keyCode = (Integer) getValueAt(row, 1);
            //if (keyCode != null)
            //   HotKeys.keys_[keyCode] =
         }
         if (col == 1) {

         }


         fireTableCellUpdated(row, col);

        }

   }


    /** Creates new form HotKeys */
    public  HotKeyFrame() {
        initComponents();
        
        HotKeys.keys_.put((Integer)KeyEvent.VK_S, new HotKeyAction(0));
        HotKeys.keys_.put((Integer)KeyEvent.VK_L, new HotKeyAction(1));
        HotKeys.keys_.put((Integer)KeyEvent.VK_SPACE, new HotKeyAction(2));
        HotKeys.keys_.put((Integer)KeyEvent.VK_A, new HotKeyAction(3));

        updateComboBox();
        HotKeyTableCellEditor myCellEditor = new HotKeyTableCellEditor();
        hotKeyTable.setCellEditor(myCellEditor);
        //hotKeyTable.setDefaultEditor(null, myCellEditor);
        //hotKeyTable.getColumnModel().getColumn(0).setCellEditor(new DefaultCellEditor(combo_));
        hotKeyTable.getColumnModel().getColumn(1).setCellRenderer(new HotKeyColumn2Renderer());
        setVisible(true);
        //hotKeyTable.setCellEditor(TableCellEditor tce = );
    }

    public void updateComboBox() {
       Object[] items = new Object[HotKeyAction.NRGUICOMMANDS];
       System.arraycopy(HotKeyAction.guiItems_, 0, items, 0, HotKeyAction.guiItems_.length);
       //TODO: Add Beanshell scripts
       DefaultComboBoxModel model = new DefaultComboBoxModel(items);
       combo_.setModel(model);
    }

    public class HotKeyColumn2Renderer extends DefaultTableCellRenderer {
       public void SetValue(Object value) {
          setText(KeyEvent.getKeyText((Integer) value));
       }
    }
    public class HotKeyTableCellEditor extends AbstractCellEditor implements TableCellEditor {
       // We'll need a component that listens for key stroks here:
       JTextField text_ = new JTextField();

       // This method is called when a cell value is edited by the user.
       public Component getTableCellEditorComponent(javax.swing.JTable table, Object value,
               boolean isSelected, int rowIndex, int vColIndex) {
           // 'value' is value contained in the cell located at (rowIndex, vColIndex)

           if (isSelected) {
               // cell (and perhaps other cells) are selected
           }
           if (vColIndex == 0)
              return combo_;

           // Todo: map correct HotKey
           text_.setText("a");
           return text_;

       }

       // This method is called when editing is completed.
       // It must return the new value to be stored in the cell.
       public Object getCellEditorValue() {
           return combo_.getSelectedItem();
       }
   }

   
    /** This method is called from within the constructor to
     * initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is
     * always regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
   // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
   private void initComponents() {

      jScrollPane1_ = new javax.swing.JScrollPane();
      hotKeyTable = new javax.swing.JTable();
      addButton_ = new javax.swing.JButton();
      removeButton_ = new javax.swing.JButton();
      loadButton_ = new javax.swing.JButton();
      saveButton_ = new javax.swing.JButton();

      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);

      jScrollPane1_.setMinimumSize(new java.awt.Dimension(23, 15));

      hotKeyTable.setFont(new java.awt.Font("Lucida Grande", 0, 10)); // NOI18N
      hotKeyTable.setModel(sctModel_);
      jScrollPane1_.setViewportView(hotKeyTable);

      addButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      addButton_.setText("Add");
      addButton_.setMinimumSize(new java.awt.Dimension(75, 20));
      addButton_.setPreferredSize(new java.awt.Dimension(75, 20));
      addButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            addButton_ActionPerformed(evt);
         }
      });

      removeButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      removeButton_.setText("Remove");
      removeButton_.setMinimumSize(new java.awt.Dimension(75, 20));
      removeButton_.setPreferredSize(new java.awt.Dimension(75, 20));
      removeButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            removeButton_ActionPerformed(evt);
         }
      });

      loadButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      loadButton_.setText("Load");
      loadButton_.setMinimumSize(new java.awt.Dimension(75, 20));
      loadButton_.setPreferredSize(new java.awt.Dimension(75, 20));
      loadButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            loadButton_ActionPerformed(evt);
         }
      });

      saveButton_.setFont(new java.awt.Font("Lucida Grande", 0, 10));
      saveButton_.setText("Save");
      saveButton_.setMinimumSize(new java.awt.Dimension(75, 20));
      saveButton_.setPreferredSize(new java.awt.Dimension(75, 20));
      saveButton_.addActionListener(new java.awt.event.ActionListener() {
         public void actionPerformed(java.awt.event.ActionEvent evt) {
            saveButton_ActionPerformed(evt);
         }
      });

      org.jdesktop.layout.GroupLayout layout = new org.jdesktop.layout.GroupLayout(getContentPane());
      getContentPane().setLayout(layout);
      layout.setHorizontalGroup(
         layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(layout.createSequentialGroup()
            .add(addButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
            .add(removeButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, 69, Short.MAX_VALUE)
            .add(loadButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED)
            .add(saveButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
         .add(jScrollPane1_, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, 377, Short.MAX_VALUE)
      );
      layout.setVerticalGroup(
         layout.createParallelGroup(org.jdesktop.layout.GroupLayout.LEADING)
         .add(layout.createSequentialGroup()
            .add(layout.createParallelGroup(org.jdesktop.layout.GroupLayout.BASELINE)
               .add(addButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(removeButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(saveButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE)
               .add(loadButton_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 20, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
            .addPreferredGap(org.jdesktop.layout.LayoutStyle.RELATED, org.jdesktop.layout.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
            .add(jScrollPane1_, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE, 351, org.jdesktop.layout.GroupLayout.PREFERRED_SIZE))
      );

      pack();
   }// </editor-fold>//GEN-END:initComponents

    private void addButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addButton_ActionPerformed
       // TODO add your handling code here:
    }//GEN-LAST:event_addButton_ActionPerformed

    private void removeButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeButton_ActionPerformed
       // TODO add your handling code here:
    }//GEN-LAST:event_removeButton_ActionPerformed

    private void loadButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadButton_ActionPerformed
       // TODO add your handling code here:
    }//GEN-LAST:event_loadButton_ActionPerformed

    private void saveButton_ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_saveButton_ActionPerformed
       // TODO add your handling code here:
    }//GEN-LAST:event_saveButton_ActionPerformed

 

   // Variables declaration - do not modify//GEN-BEGIN:variables
   private javax.swing.JButton addButton_;
   private javax.swing.JTable hotKeyTable;
   private javax.swing.JScrollPane jScrollPane1_;
   private javax.swing.JButton loadButton_;
   private javax.swing.JButton removeButton_;
   private javax.swing.JButton saveButton_;
   // End of variables declaration//GEN-END:variables

}
