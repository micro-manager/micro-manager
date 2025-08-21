package org.micromanager.plugins.fluidcontrol;

import static org.micromanager.internal.utils.JavaUtils.sleep;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.text.NumberFormat;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.border.Border;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.text.NumberFormatter;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;

public class VolumeControlSubPanel extends JPanel {
   private static final int PANEL_HEIGHT = 400;
   private static final int PANEL_WIDTH = 150;
   private static final int N_STEPS = 100000;

   private final Studio studio_;
   private final String device_;

   private DoubleJSlider controlSlider;
   private JFormattedTextField imposedTextField;
   private JButton startButton;

   public boolean isPumping = false;
   public double flowRate = 0;

   VolumeControlSubPanel(Studio studio, String device) {
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
         minValue = (int) studio_.core().getPropertyLowerLimit(device_, "Flow rate uL/sec");
         maxValue = (int) studio_.core().getPropertyUpperLimit(device_, "Flow rate uL/sec");
      } catch (Exception e) {
         studio_.getLogManager().logError(e);
         return;
      }

      controlSlider = new DoubleJSlider(minValue, maxValue, 0, N_STEPS);
      controlSlider.addChangeListener(new ChangeListener() {
         @Override
         public void stateChanged(ChangeEvent e) {
            imposedTextField.setValue(controlSlider.getScaledValue());
            try {
               studio_.core()
                     .setProperty(device_, "Flow rate uL/sec", controlSlider.getScaledValue());
            } catch (Exception ignored) {
               studio_.logs().logDebugMessage("Failed to set Flowrate in VolumeControlSubPanel.");
            }
         }
      });
      controlSlider.setFocusable(false);
      controlSlider.setOrientation(SwingConstants.VERTICAL);
      controlSlider.setPreferredSize(new Dimension(PANEL_WIDTH - 10, 300));
      controlSlider.setMajorTickSpacing(10);
      controlSlider.setPaintLabels(true);
      controlSlider.setPaintTicks(true);

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
                        "Exception parsing imposedTextField in VolumeControlSubPanel.");
               }
               double value = Double.parseDouble(imposedTextField.getValue().toString());
               controlSlider.setScaledValue(value);
               setFlowRate(value);
            }
         }
      });

      // Some panels
      final int col = 65;
      JPanel imposedPanel = new JPanel();
      imposedPanel.setLayout(new MigLayout());
      Insets insets = imposedPanel.getInsets();
      // Initialize imposedLabel
      JLabel imposedLabel = new JLabel("Imposed");
      imposedPanel.add(imposedLabel);
      imposedPanel.add(imposedTextField);
      Dimension imposedLabelSize = imposedLabel.getPreferredSize();
      imposedLabel.setBounds(insets.left + col - imposedLabelSize.width - 5,
            insets.top + 10,
            imposedLabelSize.width,
            imposedLabelSize.height);
      Dimension imposedTextFieldPreferredSize = imposedTextField.getPreferredSize();
      imposedTextField.setBounds(insets.left + col,
            insets.top + 10,
            imposedTextFieldPreferredSize.width,
            imposedTextFieldPreferredSize.height);

      // Start button
      startButton = new JButton("Start");
      initializeStartButton();

      // Add components in the right order
      this.add(controlSlider, "align center, wrap");
      this.add(imposedPanel, "align center, wrap");
      this.add(startButton, "align center");
   }

   public void setFlowRate(double value) {
      flowRate = value;
      try {
         studio_.core().setPumpFlowrate(device_, value);
      } catch (Exception ignored) {
         studio_.logs().logDebugMessage(
               "Exception while setting PumpFlowRate in VolumeControlSubPanel.");
      }
   }

   private void initializeStartButton() {
      ActionListener startAction = new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            try {
               sleep(5); // To make sure the message is sent
               if (isPumping) {
                  studio_.core().volumetricPumpStop(device_);
                  startButton.setText("Start");
                  isPumping = false;
               } else {
                  setFlowRate(flowRate); // Set flowrate again, just in case
                  studio_.core().pumpStart(device_);
                  startButton.setText("Stop");
                  isPumping = true;
               }
            } catch (Exception exception) {
               studio_.getLogManager()
                     .logMessage("An error occurred while starting/stopping a syringe pump.");
               studio_.getLogManager().logError(exception);
            }
         }
      };
      startButton.addActionListener(startAction);
   }
}
