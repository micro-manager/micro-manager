package org.micromanager.internal.dialogs;

import java.awt.Font;
import java.text.NumberFormat;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;


/**
 * Panel for configuring pixel config related settings.
 * Currently, the angles between camera and Z stage (dx/dz and dy/dz) as
 * well as the preferred step size in Z are configurable.
 */
public class PixelConfigLighSheetPanel extends JPanel {
   private static final int PRECISION = 5;
   private final NumberFormat format_;
   protected JTextField dxdzField_;
   protected JTextField dydzField_;


   public PixelConfigLighSheetPanel() {
      super(new MigLayout("align center, flowx"));

      final Font plain = new Font("Arial", Font.PLAIN, 12);
      final Font bold = new Font("Arial", Font.BOLD, 12);

      format_ = NumberFormat.getInstance();
      format_.setMaximumFractionDigits(PRECISION);
      format_.setMinimumFractionDigits(PRECISION);

      JLabel angleText = new JLabel("<html><left>Angles between camera and Z-stage. "
               + "Light Sheet Only. Set 0.0 unless needed."
               + "</left></html>");
      angleText.setFont(plain);
      super.add(angleText, "span 2, left, wrap");

      JLabel dxdzLabel = new JLabel("dx/dz (ratio)");
      dxdzLabel.setFont(bold);
      super.add(dxdzLabel);
      dxdzField_ = new JTextField(format_.format(0.0), 5);
      dxdzField_.setFont(plain);
      super.add(dxdzField_, "wrap");

      JLabel dydzLabel = new JLabel("dy/dz (ratio)");
      dydzLabel.setFont(bold);
      super.add(dydzLabel);
      dydzField_ = new JTextField(format_.format(0.0), 5);
      dydzField_.setFont(plain);
      super.add(dydzField_, "wrap 15px");

      setBorder(BorderFactory.createTitledBorder("Light Sheet Settings"));
   }

   public String getdxdz() {
      return dxdzField_.getText();
   }

   public void setdxdz(double dxdz) {
      dxdzField_.setText(format_.format(dxdz));
   }

   public String getdydz() {
      return dydzField_.getText();
   }

   public void setdydz(double dydz) {
      dydzField_.setText(format_.format(dydz));
   }

}
