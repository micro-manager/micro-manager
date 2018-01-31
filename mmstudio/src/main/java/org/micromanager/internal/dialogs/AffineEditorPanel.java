
package org.micromanager.internal.dialogs;

import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.geom.AffineTransform;
import java.text.NumberFormat;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import mmcorej.DoubleVector;
import net.miginfocom.swing.MigLayout;
import org.micromanager.internal.utils.AffineUtils;
import org.micromanager.internal.utils.DaytimeNighttime;
import org.micromanager.internal.utils.PropertyItem;

/**
 *
 * @author nico
 */
public class AffineEditorPanel extends JPanel {

   private static final long serialVersionUID = 4110816363509484273L;
   
   private final DoubleVector affineTransform_;
   private final AffineTableModel atm_;
   private final PixelSizeProvider pixelSizeProvider_;
   private static final int PRECISION = 5;
   
   public AffineEditorPanel(PixelSizeProvider psp, DoubleVector affineTransform) {
      super(new MigLayout());
      
      if (affineTransform == null) {
         affineTransform = AffineUtils.noTransform();
      }
      
      affineTransform_ = affineTransform;
      pixelSizeProvider_ = psp;
      
      final AffineCellRenderer acr = new AffineCellRenderer();
      JTable table = new DaytimeNighttime.Table() {
         private static final long serialVersionUID = -2051835510654466735L;
         @Override
         public TableCellRenderer getCellRenderer(int rowIndex, int columnIndex) {
            return acr;
         }
      };
      atm_ = new AffineTableModel(affineTransform_);
      table.setModel(atm_);
      table.setCellSelectionEnabled(true);
      
      for (int col = 0; col < 3; col++) {
         table.getColumnModel().getColumn(col).setMaxWidth(75);
      }
      super.add( new JLabel("<html><center>Affine transforms define the relation between <br> " +
              "camera and stage movement</center></html>"), "span 4, center, wrap") ;
      JScrollPane scrollPane = new JScrollPane(table);
      table.setFillsViewportHeight(true);
      
      super.add(scrollPane, "span 1 2, shrink 100");
      
      JButton calcButton = new JButton("Calculate");
      calcButton.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            AffineTransform javaAtf = AffineUtils.doubleToAffine(AffineUtils.noTransform());
            double scale = pixelSizeProvider_.pixelSize();
            javaAtf.scale(scale, scale);
            atm_.setAffineTransform(AffineUtils.affineToDouble(javaAtf));
         }
      });
      super.add(calcButton, "center, wrap");
      
      JButton measureButton = new JButton ("Measure");
      super.add(measureButton, "center");
      
   }
   
   public DoubleVector getAffineTransform() {
      return atm_.getAffineTransform();
   }
   
   /*******************Renderer******************************/
   
   private class AffineCellRenderer implements TableCellRenderer {

      PropertyItem item_;
      JLabel lab_ = new JLabel();
      private final NumberFormat format_;
      
      public AffineCellRenderer() {
         format_ = NumberFormat.getInstance();
         format_.setMaximumFractionDigits(PRECISION);
         format_.setMinimumFractionDigits(PRECISION);
      }
      
      // This method is called each time a cell in a column
      // using this renderer needs to be rendered.
      @Override
      public Component getTableCellRendererComponent(JTable table, Object value,
              boolean isSelected, boolean hasFocus, int rowIndex, int colIndex) {

         lab_.setOpaque(true);
         lab_.setHorizontalAlignment(JLabel.LEFT);

         Component comp;

         lab_.setText(format_.format( (Double) value));
         comp = lab_;

         if (rowIndex == 2) {
            comp.setEnabled(false);
            comp.setBackground(DaytimeNighttime.getInstance().getDisabledBackgroundColor());
            comp.setForeground(DaytimeNighttime.getInstance().getEnabledTextColor());
         } else {
            comp.setBackground(DaytimeNighttime.getInstance().getBackgroundColor());
            comp.setForeground(DaytimeNighttime.getInstance().getEnabledTextColor());
            comp.setEnabled(true);
         }

         return comp;
      }
   }

   /************************Table Model**********************/
   
   private class AffineTableModel extends AbstractTableModel {

      private static final long serialVersionUID = 6310218493590295038L;
      private DoubleVector affineTransform_;
      
      public AffineTableModel(DoubleVector affineTransform) {
         affineTransform_ = affineTransform;
      }
      
      public DoubleVector getAffineTransform() {
         return affineTransform_;
      }
      
      public void setAffineTransform(DoubleVector aft) {
         affineTransform_ = aft;
         fireTableDataChanged();
      }
         
      
      @Override
      public String getColumnName(int colIndex) {
         return "col. " + (colIndex + 1);
      }
      
      @Override
      public int getRowCount() {return 3;}

      @Override
      public int getColumnCount() {return 3;}

      @Override
      public Object getValueAt(int rowIndex, int columnIndex) {
         if (rowIndex < 2) {
            return affineTransform_.get(rowIndex * 3 + columnIndex);
         }
         if (columnIndex == 2) {
            return 1.0;
         }
         return 0.0;
      }
      
      @Override
      public Class getColumnClass(int c) {
         return Double.class;
      }
      
      @Override
      public boolean isCellEditable(int rowIndex, int columnIndex) {
         return rowIndex < 2;
      }
      
      @Override
      public void setValueAt(Object value, int rowIndex, int colIndex) {
         if (value instanceof Double) {
            affineTransform_.set(rowIndex * 3 + colIndex, (Double) value);
         }
      }
   }
   
}
