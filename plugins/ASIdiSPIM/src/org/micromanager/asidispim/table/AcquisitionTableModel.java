package org.micromanager.asidispim.table;

import javax.swing.table.AbstractTableModel;

import org.micromanager.asidispim.Utils.MyDialogUtils;

import java.util.List;

/**
 * A custom TableModel to represent the AcquisitionTable.
 */
@SuppressWarnings("serial")
public class AcquisitionTableModel extends AbstractTableModel {

    /**Column names for the acquisition table. */
    private String[] columnNames_ = {
            "#",
            "Acquisition Name",
            "Save Name Prefix",
            "Save Directory Root",
            "Position List Name"
    };

    /**A reference to the acquistiion table data. */
    private final AcquisitionTableData data_;
    private final List<AcquisitionMetadata> metadata_;
    
    public AcquisitionTableModel(final AcquisitionTableData data) {
        data_ = data;
        metadata_ = data.getMetadataList();
    }
    
    @Override
    public int getRowCount() {
        return metadata_.size();
    }

    @Override
    public int getColumnCount() {
        return columnNames_.length;
    }

    @Override
    public String getColumnName(int col) {
        return columnNames_[col];
    }

    @Override
    public Class<?> getColumnClass(int c) {
        return getValueAt(0, c).getClass();
    }
    
    @Override
    public Object getValueAt(int row, int col) {
        final AcquisitionMetadata info = metadata_.get(row);
        final String acqName = info.getAcquisitionName();
        switch (col) {
            case 0:
                return row + 1;
            case 1:
                return info.getAcquisitionName();
            case 2:
                return data_.getAcquisitionSettings(acqName).saveNamePrefix;
            case 3:
                return data_.getAcquisitionSettings(acqName).saveDirectoryRoot;
            case 4:
                return info.getPositionListName();
            default:
                return null;
        }
    }

    @Override
    public void setValueAt(Object value, int row, int col) {
        final AcquisitionMetadata info = metadata_.get(row);
        final String acqName = info.getAcquisitionName();
        switch (col) {
            case 0:
                break;
            case 1:
                info.setAcquisitionName((String)value);
                break;
            case 2:
                final String val = (String)value;
                if (AcquisitionTable.isNameValid(val)) {
                    data_.getAcquisitionSettings(acqName).saveNamePrefix = val;
                } else {
                    MyDialogUtils.showErrorMessage(null, "Name Error", 
                            "Name can only contain characters, digits, and underscores.");
                }
                break;
            case 3:
                data_.getAcquisitionSettings(acqName).saveDirectoryRoot = (String)value;
                break;
            case 4:
                info.setPositionListName((String)value);
                break;
            default:
                break;
        }
        fireTableCellUpdated(row, col);
    }
    
    @Override
    public boolean isCellEditable(int row, int col) {
        return (col > 0 && col < 3) ? true : false;
    }
    
    /**
     * Reorders the metadata list to match the display in the acquisition table.<P>
     * This method is called from the AcquisitionTableTransferHandler.
     * 
     * @param fromIndex the index to move from
     * @param toIndex the index to move to
     */
    public void reorder(int fromIndex, int toIndex) {
        final AcquisitionMetadata item = metadata_.get(fromIndex);
        metadata_.remove(fromIndex);
        if (fromIndex < toIndex) {
            metadata_.add(toIndex-1, item);
        } else {
            metadata_.add(toIndex, item);
        }
        //System.out.println("fromIndex: " + fromIndex);
        //System.out.println("toIndex: " + toIndex + "\n");
    }

}
