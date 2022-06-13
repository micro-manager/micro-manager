package org.micromanager.internal.dialogs;

import java.awt.Color;
import java.awt.Component;
import java.awt.event.ActionEvent;
import java.awt.geom.AffineTransform;
import java.text.NumberFormat;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTable;
import javax.swing.border.Border;
import javax.swing.border.EtchedBorder;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.TableCellRenderer;
import mmcorej.DoubleVector;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.internal.pixelcalibrator.PixelCalibratorDialog;
import org.micromanager.internal.utils.AffineUtils;
import org.micromanager.internal.utils.DaytimeNighttime;

/**
 * Generates the Panel for the user to input the affine transform relating
 * the image on the camera to stage movement.
 *
 * @author nico
 */
public class AffineEditorPanel extends JPanel {

   private static final long serialVersionUID = 4110816363509484273L;

   private final Studio studio_;
   private final PixelSizeProvider pixelSizeProvider_;
   private final AffineTableModel atm_;
   private static final int PRECISION = 5;
   private final NumberFormat format_;
   private PixelCalibratorDialog pcd_;

   /**
    * Generates the panel to let the user interact with the affine transform
    * relating stage movement to the image on the camera.
    *
    * @param studio          The omnipresent Studio object.
    * @param psp             Object that holds information about the transform
    * @param affineTransform Affine transform as a DoubleVector for exchange with the Core
    */
   public AffineEditorPanel(Studio studio, PixelSizeProvider psp, DoubleVector affineTransform) {
      super(new MigLayout("align center, flowx"));

      studio_ = studio;
      pixelSizeProvider_ = psp;
      if (affineTransform == null) {
         affineTransform = AffineUtils.noTransform();
      }
      final DoubleVector affineTransform_ = affineTransform;
      final DoubleVector originalAffineTransform = copyDoubleVector(affineTransform);

      format_ = NumberFormat.getInstance();
      format_.setMaximumFractionDigits(PRECISION);
      format_.setMinimumFractionDigits(PRECISION);

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
      table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
      table.setCellSelectionEnabled(true);
      table.setBorder(BorderFactory.createLineBorder(Color.GRAY, 1));

      for (int col = 0; col < table.getColumnModel().getColumnCount(); col++) {
         table.getColumnModel().getColumn(col).setMaxWidth(75);
      }
      super.add(new JLabel("<html><center>Affine transforms define the relation between <br> "
            + "camera and stage movement</center></html>"), " span 2, center, wrap");
      table.setFillsViewportHeight(true);

      super.add(table);

      JButton calcButton = new JButton("Calculate");
      calcButton.addActionListener((ActionEvent e) -> calculate());
      super.add(calcButton, "flowy, split 3, center, width 90!");

      JButton measureButton = new JButton("Measure");
      measureButton.addActionListener((ActionEvent e) -> {
         if (pcd_ == null) {
            pcd_ = new PixelCalibratorDialog(studio_, pixelSizeProvider_);
            studio_.events().registerForEvents(pcd_);
         } else {
            pcd_.setVisible(true);
            pcd_.toFront();
         }
      });
      super.add(measureButton, "center, width 90!");

      JButton resetButton = new JButton("Reset");
      resetButton.addActionListener((ActionEvent e) ->
            atm_.setAffineTransform(originalAffineTransform));
      super.add(resetButton, "center, width 90!");

      Border clbb = BorderFactory.createEtchedBorder(EtchedBorder.LOWERED);
      super.setBorder(clbb);

   }

   public DoubleVector getAffineTransform() {
      return atm_.getAffineTransform();
   }

   /**
    * Utility function to provide a copy of a DoubleVector.
    *
    * @param in DoubleVector to be copied.
    * @return Copy of the input
    */
   public static DoubleVector copyDoubleVector(DoubleVector in) {
      DoubleVector out = new DoubleVector(in.size());
      for (int i = 0; i < in.size(); i++) {
         out.set(i, in.get(i));
      }
      return out;
   }

   public void setAffineTransform(DoubleVector newAft) {
      atm_.setAffineTransform(newAft);
   }

   /**
    * Disposes the panel.
    */
   public void cleanup() {
      if (pcd_ != null) {
         pcd_.dispose();
      }
   }

   /**
    * Given the pixel size of the pixelSizeProvider, calculates an affinetransform
    * (assuming perfect alignment of the stage with the camera), and sets the affine
    * transform. Useful to get a start with an affine transform if it proves difficult
    * to measure.
    */
   public void calculate() {
      AffineTransform javaAtf = AffineUtils.doubleToAffine(AffineUtils.noTransform());
      double scale = pixelSizeProvider_.getPixelSize();
      javaAtf.scale(scale, scale);
      atm_.setAffineTransform(AffineUtils.affineToDouble(javaAtf));
   }

   /*******************Renderer.******************************/

   private class AffineCellRenderer implements TableCellRenderer {

      JLabel lab_ = new JLabel();

      public AffineCellRenderer() {

      }

      // This method is called each time a cell in a column
      // using this renderer needs to be rendered.
      @Override
      public Component getTableCellRendererComponent(JTable table, Object value,
                                                     boolean isSelected, boolean hasFocus,
                                                     int rowIndex, int colIndex) {

         lab_.setOpaque(true);
         lab_.setHorizontalAlignment(JLabel.LEFT);

         Component comp;

         lab_.setText(format_.format(value));
         comp = lab_;

         if (rowIndex == 2) {
            comp.setEnabled(false);
            comp.setBackground(studio_.app().skin().getDisabledBackgroundColor());
            comp.setForeground(studio_.app().skin().getEnabledTextColor());
         } else {
            comp.setBackground(studio_.app().skin().getBackgroundColor());
            comp.setForeground(studio_.app().skin().getEnabledTextColor());
            comp.setEnabled(true);
         }

         return comp;
      }
   }

   /************************Table Model.**********************/

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
         affineTransform_ = copyDoubleVector(aft);
         fireTableDataChanged();
      }


      @Override
      public String getColumnName(int colIndex) {
         return "col. " + (colIndex + 1);
      }

      @Override
      public int getRowCount() {
         return 3;
      }

      @Override
      public int getColumnCount() {
         return 3;
      }

      @Override
      public Object getValueAt(int rowIndex, int columnIndex) {
         if (rowIndex < 2) {
            return affineTransform_.get(rowIndex * 3 + columnIndex);
         } else if (columnIndex == 0 || columnIndex == 1) {
            return 0.0;
         } else {
            return 1.0;
         }
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
