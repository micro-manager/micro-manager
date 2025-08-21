package org.micromanager.plugins.fluidcontrol;

import static org.micromanager.internal.utils.JavaUtils.sleep;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.NumberFormatter;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;

public class PressureControlSubPanel extends JPanel {
   private static final int PANEL_HEIGHT = 430;
   private static final int PANEL_WIDTH = 150;
   private static final int N_STEPS = 10000;

   private final DecimalFormat df = new DecimalFormat("0.##");
   private final Studio studio_;
   private final String device_;

   private DoubleJSlider controlSlider;
   private JFormattedTextField imposedTextField;
   private JTextField measuredTextField;
   private JButton startButton;

   public boolean isPumping = false;
   public double pressure = 0;

   PressureControlSubPanel(Studio studio, String device) {
      this.setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
      this.setFocusable(false);
      this.setLayout(new MigLayout("insets 2"));

      this.studio_ = studio;
      this.device_ = device;

      Border outline = BorderFactory.createTitledBorder(device_);
      this.setBorder(outline);
      initialize();
   }

   private void initialize() {
      // Initialize the slider
      int minValue = 0;
      int maxValue = 0;
      try {
         minValue = (int) studio_.core().getPropertyLowerLimit(device_, "Imposed Pressure");
         maxValue = (int) studio_.core().getPropertyUpperLimit(device_, "Imposed Pressure");
      } catch (Exception e) {
         studio_.getLogManager().logError(e);
         return;
      }

      controlSlider = new DoubleJSlider(minValue, maxValue, 0, N_STEPS);
      controlSlider.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            imposedTextField.setValue(controlSlider.getScaledValue());
            setPressure(controlSlider.getScaledValue());
         }
      });
      controlSlider.setFocusable(false);
      controlSlider.setOrientation(SwingConstants.VERTICAL);
      controlSlider.setPreferredSize(new Dimension(PANEL_WIDTH - 10, 300));
      controlSlider.setMajorTickSpacing(10);
      controlSlider.setPaintLabels(true);
      controlSlider.setPaintTicks(true);

      // Initialize imposedLabel
      final JLabel imposedLabel = new JLabel("Imposed");

      // Initialize the imposed pressure TextField
      NumberFormat numberFormat = NumberFormat.getNumberInstance();
      numberFormat.setMaximumFractionDigits(2);
      numberFormat.setMinimumFractionDigits(0);
      NumberFormatter formatter = new NumberFormatter(numberFormat);
      formatter.setValueClass(Float.class);
      formatter.setMinimum(0);
      formatter.setMaximum(100);
      imposedTextField = new JFormattedTextField(formatter);
      imposedTextField.setBackground(new Color(240, 240, 240));
      imposedTextField.setForeground(new Color(60, 60, 60));
      imposedTextField.setValue(0.0);
      imposedTextField.setColumns(7);
      imposedTextField.addKeyListener(new KeyAdapter() {
         public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
               try {
                  double value = Double.parseDouble(imposedTextField.getText());
                  imposedTextField.setValue(value);
               } catch (Exception ignored) {
                  studio_.logs().logDebugMessage(
                        "Exception parsing imposedTExtField in PressureControlSubPanel");
               }
               double value = Double.parseDouble(imposedTextField.getValue().toString());
               controlSlider.setScaledValue(value);
               setPressure(value);
            }
         }
      });

      // Initialize measureLabel
      final JLabel measuredLabel = new JLabel("Measured");

      // Initialize measured pressure TextField
      measuredTextField = new JTextField();
      measuredTextField.setColumns(7);
      measuredTextField.setEditable(false);
      measuredTextField.setForeground(new Color(140, 140, 140));

      // Some panels
      final int col = 65;
      JPanel imposedPanel = new JPanel();
      imposedPanel.setLayout(null);
      imposedPanel.add(imposedLabel);
      imposedPanel.add(imposedTextField);
      final Dimension imposedLabelPreferredSize = imposedLabel.getPreferredSize();
      final Insets imposedPanelInsets = imposedPanel.getInsets();
      imposedLabel.setBounds(imposedPanelInsets.left + col - imposedLabelPreferredSize.width - 5,
            imposedPanelInsets.top + 10,
            imposedLabelPreferredSize.width,
            imposedLabelPreferredSize.height);
      final Dimension imposedTextFieldPreferredSize = imposedTextField.getPreferredSize();
      imposedTextField.setBounds(imposedPanelInsets.left + col,
            imposedPanelInsets.top + 10,
            imposedTextFieldPreferredSize.width,
            imposedTextFieldPreferredSize.height);

      JPanel measuredPanel = new JPanel();
      measuredPanel.setLayout(null);
      measuredPanel.add(measuredLabel);
      measuredPanel.add(measuredTextField);
      final Insets measuredPanelInsets = measuredPanel.getInsets();
      final Dimension measuredLabelPreferredSize = measuredLabel.getPreferredSize();
      measuredLabel.setBounds(measuredPanelInsets.left + col
                  - measuredLabelPreferredSize.width - 5,
            measuredPanelInsets.top + 10,
            measuredLabelPreferredSize.width,
            measuredLabelPreferredSize.height);
      final Dimension measuredTextFieldPreferredSize = measuredTextField.getPreferredSize();
      measuredTextField.setBounds(imposedPanelInsets.left + col,
            imposedPanelInsets.top + 10,
            measuredTextFieldPreferredSize.width,
            measuredTextFieldPreferredSize.height);

      // Start button
      startButton = new JButton("Start");
      initializeStartButton();

      // Add components in the right order
      this.add(controlSlider, "align center, wrap");
      this.add(imposedPanel, "align center, wrap");
      this.add(measuredPanel, "align center, wrap");
      this.add(startButton, "align center");
   }

   public void updatePressure() {
      double temp = 0;
      try {
         temp = studio_.core().getPumpPressureKPa(device_);
      } catch (Exception ignored) {
         studio_.logs().logDebugMessage("Failed to get Pump Pressure in PressureControlSubPanel");
      }
      measuredTextField.setText(df.format(temp));
   }

   public void setPressure(double value) {
      pressure = value;
      if (!isPumping) {
         return;
      } // Don't actually change pressure if not pumping
      try {
         studio_.core().setPumpPressureKPa(device_, value);
      } catch (Exception ignored) {
         studio_.logs().logDebugMessage("Failed to set Pump Pressure in PressureControlSubPanel");
      }
   }

   private void initializeStartButton() {
      ActionListener startAction = new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            sleep(5); // To make sure message is sent
            try {
               if (isPumping) {
                  studio_.core().pressurePumpStop(device_);
                  startButton.setText("Start");
                  isPumping = false;
               } else {
                  studio_.getLogManager().logMessage("Set pressure to: " + pressure);
                  studio_.core().setPumpPressureKPa(device_, pressure);
                  startButton.setText("Stop");
                  isPumping = true;
               }
            } catch (Exception exception) {
               studio_.getLogManager()
                     .logMessage("An error occurred while starting/stopping a pressure pump.");
               studio_.getLogManager().logError(exception);
            }
         }
      };
      startButton.addActionListener(startAction);
   }
}
