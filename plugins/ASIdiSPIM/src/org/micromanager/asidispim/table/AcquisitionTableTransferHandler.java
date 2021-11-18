package org.micromanager.asidispim.table;

import javax.activation.ActivationDataFlavor;
import javax.activation.DataHandler;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.TransferHandler;
import java.awt.Cursor;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.dnd.DragSource;

/**
 * This class allows the AcquisitionTable to drag and drop rows to reorder the table.
 */
@SuppressWarnings("serial")
public class AcquisitionTableTransferHandler extends TransferHandler {

    private final DataFlavor localObjectFlavor = new ActivationDataFlavor(
            Integer.class,
            "application/x-java-Integer;class=java.lang.Integer",
            "Integer Row Index"
    );

    /** The table that this TransferHandler is associated with.*/
    private final JTable table_;

    public AcquisitionTableTransferHandler(final JTable table) {
        table_ = table;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        return new DataHandler(table_.getSelectedRow(), localObjectFlavor.getMimeType());
    }

    @Override
    public boolean canImport(TransferHandler.TransferSupport info) {
        final boolean b = info.getComponent() == table_ && info.isDrop() && info.isDataFlavorSupported(localObjectFlavor);
        table_.setCursor(b ? DragSource.DefaultMoveDrop : DragSource.DefaultMoveNoDrop);
        return b;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return TransferHandler.COPY_OR_MOVE;
    }

    @Override
    public boolean importData(TransferHandler.TransferSupport info) {
        final JTable target = (JTable)info.getComponent();
        final JTable.DropLocation dropLocation = (JTable.DropLocation)info.getDropLocation();
        int rowTo = dropLocation.getRow();
        // move a row to another location in the table
        try {
            final Integer rowFrom = (Integer)info.getTransferable().getTransferData(localObjectFlavor);
            if (rowFrom != -1 && rowFrom != rowTo) {
                ((AcquisitionTableModel)table_.getModel()).reorder(rowFrom, rowTo);
                // keep the moved row selected
                if (rowFrom < rowTo) {
                    rowTo--;
                }
                target.setRowSelectionInterval(rowTo, rowTo);
                target.repaint(); // refresh ui
                return true;
            }
        } catch (Exception e) {
            JOptionPane.showMessageDialog(null, e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }

    @Override
    protected void exportDone(JComponent c, Transferable t, int act) {
        if (act == TransferHandler.MOVE || act == TransferHandler.NONE) {
            table_.setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
        }
    }
}
