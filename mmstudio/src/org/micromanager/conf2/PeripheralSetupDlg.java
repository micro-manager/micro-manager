package org.micromanager.conf2;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Vector;

import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.border.EmptyBorder;
import javax.swing.table.AbstractTableModel;

import mmcorej.CMMCore;
import mmcorej.MMCoreJ;

import org.micromanager.utils.MMDialog;

public class PeripheralSetupDlg extends MMDialog {

   private static final long serialVersionUID = 1L;
   private static final int NAMECOLUMN = 0;
   private static final int ADAPTERCOLUMN = 1;
   private static final int DESCRIPTIONCOLUMN = 2;
   private static final int SELECTIONCOLUMN = 3;

   private class DeviceTable_TableModel extends AbstractTableModel {

      private static final long serialVersionUID = 1L;
      public final String[] COLUMN_NAMES = new String[]{
         "Name",
         "Adapter/Library",
         "Description",
         "Selected"
      };
      Vector<Boolean> selected_;

      public DeviceTable_TableModel() {
         selected_ = new Vector<Boolean>();
         for (int i=0; i<peripherals_.size(); i++) {
            selected_.add(false);
         }
      }

      @Override
      public int getRowCount() {
         return peripherals_.size();
      }

      @Override
      public int getColumnCount() {
         return COLUMN_NAMES.length;
      }

      @Override
      public String getColumnName(int columnIndex) {
         return COLUMN_NAMES[columnIndex];
      }

      @Override
      public Class getColumnClass(int c) {
         Class ret = String.class;
         if (SELECTIONCOLUMN == c) {
            ret = Boolean.class;
         }
         return ret;
      }

      @Override
      public Object getValueAt(int rowIndex, int columnIndex) {

         if (columnIndex == NAMECOLUMN) {
            return peripherals_.get(rowIndex).getName();
         } else if (columnIndex == ADAPTERCOLUMN) {
            return new String(peripherals_.get(rowIndex).getAdapterName() + "/" + peripherals_.get(rowIndex).getLibrary());
         } else if (columnIndex == DESCRIPTIONCOLUMN) {
            return peripherals_.get(rowIndex).getDescription();
         } else if (SELECTIONCOLUMN == columnIndex) {
            return selected_.get(rowIndex);
         } else {
            return null;
         }
      }

      @Override
      public void setValueAt(Object value, int row, int col) {
         switch (col) {
            case NAMECOLUMN: {
               String n = (String) value;
               String o = peripherals_.get(row).getName();
               peripherals_.get(row).setName(n);
               try {
                  //model_.changeDeviceName(o, n);
                  fireTableCellUpdated(row, col);
               } catch (Exception e) {
                  handleError(e.getMessage());
               }
            }
            break;
            case ADAPTERCOLUMN:
               break;
            case DESCRIPTIONCOLUMN:
               break;
            case SELECTIONCOLUMN:
               selected_.set(row, (Boolean) value);
               break;
         }
      }

      @Override
      public boolean isCellEditable(int nRow, int nCol) {
         boolean ret = false;
         switch (nCol) {
            case NAMECOLUMN:
               ret = true;
               break;
            case ADAPTERCOLUMN:
               break;
            case DESCRIPTIONCOLUMN:
               break;
            case SELECTIONCOLUMN:
               ret = true;
               break;
         }
         return ret;
      }

      public void refresh() {
         this.fireTableDataChanged();
      }

      Vector<Boolean> getSelected() {
         return selected_;
      }

   }
   private JTable deviceTable_;
   private JScrollPane scrollPane_;
   private MicroscopeModel model_;
   private String hub_;
   private final JPanel contentPanel = new JPanel();
   private Vector<Device> peripherals_;

   public PeripheralSetupDlg(MicroscopeModel mod, CMMCore c, String hub, Vector<Device> per) {
      setTitle("Peripheral Devices Setup");
      setBounds(100, 100, 479, 353);
      loadPosition(100, 100);
      //setModalityType(ModalityType.APPLICATION_MODAL);
      setModal(true);
      setResizable(false);
      hub_ = hub;
      model_ = mod;
      peripherals_ = per;

      getContentPane().setLayout(new BorderLayout());
      contentPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
      getContentPane().add(contentPanel, BorderLayout.CENTER);
      contentPanel.setLayout(null);
      
      scrollPane_ = new JScrollPane();
      scrollPane_.setBounds(10, 36, 453, 236);
      contentPanel.add(scrollPane_);

      deviceTable_ = new JTable();
      deviceTable_.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
      scrollPane_.setViewportView(deviceTable_);
      
      JLabel lblNewLabel = new JLabel("HUB (parent device):");
      lblNewLabel.setBounds(10, 11, 111, 14);
      contentPanel.add(lblNewLabel);
      
      JLabel lblParentDev = new JLabel(hub_);
      lblParentDev.setBounds(131, 11, 332, 14);
      contentPanel.add(lblParentDev);
      
      {
         JPanel buttonPane = new JPanel();
         buttonPane.setLayout(new FlowLayout(FlowLayout.RIGHT));
         getContentPane().add(buttonPane, BorderLayout.SOUTH);
         {
            JButton okButton = new JButton("OK");
            okButton.addActionListener(new ActionListener() {
               @Override
               public void actionPerformed(ActionEvent e) {
                  onOK();
               }
            });
            okButton.setActionCommand("OK");
            buttonPane.add(okButton);
            getRootPane().setDefaultButton(okButton);
         }
         {
            JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(new ActionListener() {
               @Override
               public void actionPerformed(ActionEvent e) {
                  onCancel();
               }
            });
            cancelButton.setActionCommand("Cancel");
            buttonPane.add(cancelButton);
         }
      }
      addWindowListener(new WindowAdapter() {
         @Override
         public void windowClosing(final WindowEvent e) {
            savePosition();
         }
      });
      
      rebuildTable();
   }

   public void handleError(String message) {
      JOptionPane.showMessageDialog(this, message);
   }

   protected void removeDevice() {
      int sel = deviceTable_.getSelectedRow();
      if (sel < 0) {
         return;
      }
      String devName = (String) deviceTable_.getValueAt(sel, 0);

      if (devName.contentEquals(new StringBuffer().append(MMCoreJ.getG_Keyword_CoreDevice()))) {
         handleError(MMCoreJ.getG_Keyword_CoreDevice() + " device can't be removed!");
         return;
      }

      model_.removeDevice(devName);
      rebuildTable();
   }

   public final void rebuildTable() {
      DeviceTable_TableModel tmd = new DeviceTable_TableModel();
      deviceTable_.setModel(tmd);
      tmd.fireTableStructureChanged();
      tmd.fireTableDataChanged();
   }

   public void refresh() {
      rebuildTable();
   }

   public void onOK() {
//      try {
//         DeviceTable_TableModel tmd = (DeviceTable_TableModel) deviceTable_.getModel();
//
//         Vector<String> hubs = new Vector<String>();
//         for (int i=0; i < peripherals_.size(); i++) {
//            hubs.add(new String(hub_));
//         }
//         Vector<Boolean> sel = tmd.getSelected();
//         model_.addSelectedPeripherals(core_, peripherals_, hubs, sel);
//         model_.loadDeviceDataFromHardware(core_);
//      } catch (Exception e) {
//         handleError(e.getMessage());
//      } finally {
//         dispose();
//      }
      savePosition();
      dispose();
   }
   
   public void onCancel() {
      savePosition();
      dispose();
   }

   public Device[] getSelectedPeripherals() {
      DeviceTable_TableModel tmd = (DeviceTable_TableModel) deviceTable_.getModel();
      Vector<Device> sel = new Vector<Device>();
      Vector<Boolean> selFlags = tmd.getSelected();
      for (int i=0; i<peripherals_.size(); i++) {
         if (selFlags.get(i))
            sel.add(peripherals_.get(i));
      }
      return sel.toArray(new Device[sel.size()]);
   }
}
