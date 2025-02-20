package org.micromanager.internal.dialogs;


import java.awt.Font;
import java.text.NumberFormat;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import net.miginfocom.swing.MigLayout;

public class PixelConfigOptimalZPanel extends JPanel {
   private static final int PRECISION = 5;
   private final NumberFormat format_;
   protected JTextField preferredZStepUmField_;

   public PixelConfigOptimalZPanel() {
      super(new MigLayout("align center, flowx"));

      final Font plain = new Font("Arial", Font.PLAIN, 12);
      final Font bold = new Font("Arial", Font.BOLD, 12);

      format_ = NumberFormat.getInstance();
      format_.setMaximumFractionDigits(PRECISION);
      format_.setMinimumFractionDigits(PRECISION);

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

      setBorder(BorderFactory.createTitledBorder("Optimal Z step size"));
   }

   public String getPreferredZStepUm() {
      return preferredZStepUmField_.getText();
   }

   public void setPreferredZStepUm(double stepSizeUm) {
      preferredZStepUmField_.setText(format_.format(stepSizeUm));
   }
}