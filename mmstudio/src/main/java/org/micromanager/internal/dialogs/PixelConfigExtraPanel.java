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
public class PixelConfigExtraPanel extends JPanel {
   private final Studio studio_;
   private final PixelSizeProvider pixelSizeProvider_;
   private static final int PRECISION = 5;
   private final NumberFormat format_;
   protected JTextField dxdzField_;
   protected JTextField dydzField_;
   protected JTextField preferredZStepUmField_;


   public PixelConfigExtraPanel(Studio studio, PixelSizeProvider pixelSizeProvider) {
      super(new MigLayout("align center, flowx"));

      studio_ = studio;
      pixelSizeProvider_ = pixelSizeProvider;
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
      //dxdzField_ = new JTextField(format_.format(pixelSizeProvider_.getdxdz()), 5);
      dxdzField_ = new JTextField(format_.format(0.0), 5);
      dxdzField_.setFont(plain);
      super.add(dxdzField_, "wrap");

      JLabel dydzLabel = new JLabel("dy/dz (ratio)");
      dydzLabel.setFont(bold);
      super.add(dydzLabel);
      dydzField_ = new JTextField(format_.format(0.0), 5);
      dydzField_.setFont(plain);
      super.add(dydzField_, "wrap 15px");

      JLabel optimalText = new JLabel("<html><left>Optimal Z step for this Pixel"
               + "Size configuration.  Shown in the MDA window.</left></html>");
      optimalText.setFont(plain);
      super.add(optimalText, "span 2, left, wrap");

      JLabel preferredZLabel = new JLabel("Preferred Z step size (um)");
      preferredZLabel.setFont(bold);
      super.add(preferredZLabel);
      preferredZStepUmField_ = new JTextField(format_.format(0.0), 5);
      preferredZStepUmField_.setFont(plain);
      super.add(preferredZStepUmField_, "wrap");

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

   public String getPreferredZStepUm() {
      return preferredZStepUmField_.getText();
   }

   public void setPreferredZStepUm(double stepSizeUm) {
      preferredZStepUmField_.setText(format_.format(stepSizeUm));
   }
}
