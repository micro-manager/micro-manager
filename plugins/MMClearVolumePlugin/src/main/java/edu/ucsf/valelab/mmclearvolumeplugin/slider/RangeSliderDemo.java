package edu.ucsf.valelab.mmclearvolumeplugin.slider;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;

/** Demo application panel to display a range slider. */
public class RangeSliderDemo extends JPanel {

  private final JLabel rangeSliderLabel1 = new JLabel();
  private JLabel rangeSliderValue1 = new JLabel();
  private final JLabel rangeSliderLabel2 = new JLabel();
  private JLabel rangeSliderValue2 = new JLabel();
  private final RangeSlider rangeSlider = new RangeSlider();

  public RangeSliderDemo() {
    setBorder(BorderFactory.createEmptyBorder(6, 6, 6, 6));
    setLayout(new GridBagLayout());

    rangeSliderLabel1.setText("Lower value:");
    rangeSliderLabel2.setText("Upper value:");
    rangeSliderValue1.setHorizontalAlignment(JLabel.LEFT);
    rangeSliderValue2.setHorizontalAlignment(JLabel.LEFT);

    rangeSlider.setPreferredSize(new Dimension(240, rangeSlider.getPreferredSize().height));
    rangeSlider.setMinimum(0);
    rangeSlider.setMaximum(100);

    // Add listener to update display.
    rangeSlider.addChangeListener(
        new ChangeListener() {
          @Override
          public void stateChanged(ChangeEvent e) {
            RangeSlider slider = (RangeSlider) e.getSource();
            rangeSliderValue1.setText(String.valueOf(slider.getValue()));
            rangeSliderValue2.setText(String.valueOf(slider.getUpperValue()));
          }
        });

    add(
        rangeSliderLabel1,
        new GridBagConstraints(
            0,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.NONE,
            new Insets(0, 0, 3, 3),
            0,
            0));
    add(
        rangeSliderValue1,
        new GridBagConstraints(
            1,
            0,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.NONE,
            new Insets(0, 0, 3, 0),
            0,
            0));
    add(
        rangeSliderLabel2,
        new GridBagConstraints(
            0,
            1,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.NONE,
            new Insets(0, 0, 3, 3),
            0,
            0));
    add(
        rangeSliderValue2,
        new GridBagConstraints(
            1,
            1,
            1,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.NONE,
            new Insets(0, 0, 6, 0),
            0,
            0));
    add(
        rangeSlider,
        new GridBagConstraints(
            0,
            2,
            2,
            1,
            0.0,
            0.0,
            GridBagConstraints.NORTHWEST,
            GridBagConstraints.NONE,
            new Insets(0, 0, 0, 0),
            0,
            0));
  }

  public void display() {
    // Initialize values.
    rangeSlider.setValue(30);
    rangeSlider.setUpperValue(40);

    // Initialize value display.
    rangeSliderValue1.setText(String.valueOf(rangeSlider.getValue()));
    rangeSliderValue2.setText(String.valueOf(rangeSlider.getUpperValue()));

    // Create window frame.
    JFrame frame = new JFrame();
    frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
    frame.setResizable(false);
    frame.setTitle("Range Slider Demo");

    // Set window content and validate.
    frame.getContentPane().setLayout(new BorderLayout());
    frame.getContentPane().add(this, BorderLayout.CENTER);
    frame.pack();

    // Set window location and display.
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
  }

  /**
   * Main application method.
   *
   * @param args String[]
   */
  public static void main(String[] args) {
    try {
      UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
    } catch (Exception ex) {
      ex.printStackTrace();
    }

    SwingUtilities.invokeLater(
        () -> {
          new RangeSliderDemo().display();
        });
  }
}
