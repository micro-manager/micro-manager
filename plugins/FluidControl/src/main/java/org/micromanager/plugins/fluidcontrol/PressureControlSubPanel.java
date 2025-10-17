package org.micromanager.plugins.fluidcontrol;

import static org.micromanager.internal.utils.JavaUtils.sleep;

import com.google.common.eventbus.Subscribe;
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
import javax.swing.event.ChangeListener;
import javax.swing.text.NumberFormatter;
import net.miginfocom.swing.MigLayout;
import org.micromanager.Studio;
import org.micromanager.events.PropertyChangedEvent;

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

   // This is a MMCore keyword, please don't change.
   private String propName = "Pressure Imposed";

   PressureControlSubPanel(Studio studio, String device) {
      this.studio_ = studio;
      this.device_ = device;

      this.setPreferredSize(new Dimension(PANEL_WIDTH, PANEL_HEIGHT));
      this.setFocusable(false);
      this.setLayout(new MigLayout("insets 2"));
      initialize();
      studio_.events().registerForEvents(this);
   }

   private void initialize() {
      // Initialize the slider
      int minValue = 0;
      int maxValue = 0;
      try {
         minValue = (int) studio_.core().getPropertyLowerLimit(device_, propName);
         maxValue = (int) studio_.core().getPropertyUpperLimit(device_, propName);
      } catch (Exception e) {
         this.add(new JLabel(device_), "wrap");
         this.add(new JLabel(propName + " missing"));
         studio_.logs().logError("This PressurePump does not have required property " + propName);
         return;
      }

      Border outline = BorderFactory.createTitledBorder(device_);
      this.setBorder(outline);

      NumberFormatter formatter = initializeNumberFormatter(minValue, maxValue);
      initializeSlider(minValue, maxValue);
      this.add(controlSlider, "align center, wrap");

      JPanel imposedPanel = initializeImposedPressure(formatter);
      this.add(imposedPanel, "align center, wrap");

      JPanel measuredPanel = initializeMeasuredPanel();
      this.add(measuredPanel, "align center, wrap");

      startButton = initializeStartButton();
      this.add(startButton, "align center");
   }

   @Subscribe
   public void onImposedPressureChanged(PropertyChangedEvent pce) {
      if (pce.getDevice().equals(device_) && pce.getProperty().equals(propName)) {
         ChangeListener[] cls = controlSlider.getChangeListeners();
         for (ChangeListener cl : cls) {
            controlSlider.removeChangeListener(cl);
         }
         controlSlider.setScaledValue(Double.parseDouble(pce.getValue()));
         imposedTextField.setValue(controlSlider.getScaledValue());
         for (ChangeListener cl : cls) {
            controlSlider.addChangeListener(cl);
         }
      }
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

   private NumberFormatter initializeNumberFormatter(double minValue, double maxValue) {
      NumberFormat numberFormat = NumberFormat.getNumberInstance();
      numberFormat.setMaximumFractionDigits(2);
      numberFormat.setMinimumFractionDigits(0);
      NumberFormatter formatter = new NumberFormatter(numberFormat);
      formatter.setValueClass(Float.class);
      formatter.setMinimum(minValue);
      formatter.setMaximum(maxValue);
      return formatter;
   }

   private JPanel initializeImposedPressure(NumberFormatter formatter) {
      final JLabel imposedLabel = new JLabel("Imposed");
      imposedTextField = new JFormattedTextField(formatter);
      imposedTextField.setBackground(new Color(240, 240, 240));
      imposedTextField.setForeground(new Color(60, 60, 60));
      imposedTextField.setValue(0.0);
      imposedTextField.setColumns(5);
      imposedTextField.addKeyListener(new KeyAdapter() {
         public void keyPressed(KeyEvent e) {
            if (e.getKeyCode() == KeyEvent.VK_ENTER) {
               try {
                  double value = Double.parseDouble(imposedTextField.getText());
                  imposedTextField.setValue(value);
                  controlSlider.setScaledValue(value);
               } catch (Exception ignored) {
                  studio_.logs().logDebugMessage(
                        "Exception parsing imposedTExtField in PressureControlSubPanel");
               }
            }
         }
      });

      JPanel imposedPanel = new JPanel();
      imposedPanel.setLayout(new MigLayout("insets 2"));
      imposedPanel.add(imposedLabel);
      imposedPanel.add(imposedTextField);
      return imposedPanel;
   }

   private JPanel initializeMeasuredPanel() {
      final JLabel measuredLabel = new JLabel("Measured");
      measuredTextField = new JTextField();
      measuredTextField.setColumns(5);
      measuredTextField.setEditable(false);
      measuredTextField.setForeground(new Color(140, 140, 140));

      JPanel measuredPanel = new JPanel();
      measuredPanel.setLayout(new MigLayout("insets 2"));
      measuredPanel.add(measuredLabel);
      measuredPanel.add(measuredTextField);
      return measuredPanel;
   }

   private void initializeSlider(double minValue, double maxValue) {
      controlSlider = new DoubleJSlider(minValue, maxValue, 0, N_STEPS);
      controlSlider.addChangeListener(e -> {
         imposedTextField.setValue(controlSlider.getScaledValue());
         setPressure(controlSlider.getScaledValue());
      });
      controlSlider.setFocusable(false);
      controlSlider.setOrientation(SwingConstants.VERTICAL);
      controlSlider.setPreferredSize(new Dimension(PANEL_WIDTH - 10, 300));
      controlSlider.setMajorTickSpacing(10);
      controlSlider.setPaintLabels(true);
      controlSlider.setPaintTicks(true);
   }

   private JButton initializeStartButton() {
      JButton startButton = new JButton("Start");
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
      return startButton;
   }
}
