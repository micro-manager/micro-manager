package org.micromanager.deskew;

import java.text.ParseException;
import java.util.ArrayList;
import java.util.List;
import javax.swing.ButtonGroup;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import net.haesleinhuepf.clij.clearcl.ClearCL;
import net.haesleinhuepf.clij.clearcl.ClearCLDevice;
import net.haesleinhuepf.clij.clearcl.backend.ClearCLBackendInterface;
import net.haesleinhuepf.clij.clearcl.backend.ClearCLBackends;
import net.miginfocom.swing.MigLayout;
import org.micromanager.PropertyMap;
import org.micromanager.Studio;
import org.micromanager.data.ProcessorConfigurator;
import org.micromanager.internal.utils.NumberUtils;
import org.micromanager.internal.utils.WindowPositioning;
import org.micromanager.propertymap.MutablePropertyMapView;


/**
 * UI part of the plugin to deskew OPM light sheet data.
 */
public class DeskewFrame extends JFrame implements ProcessorConfigurator {
   private static final int DEFAULT_WIN_X = 100;
   private static final int DEFAULT_WIN_Y = 100;
   private static final String DIALOG_TITLE = "Deskew";

   // keys to store settings in MutablePropertyMap
   static final String THETA = "Theta";
   static final String FULL_VOLUME = "Create Full Volume";
   static final String XY_PROJECTION = "Do XY Projection";
   static final String XY_PROJECTION_MODE = "XY Projection Mode";
   static final String ORTHOGONAL_PROJECTIONS = "Do Orthogonal Projections";
   static final String ORTHOGONAL_PROJECTIONS_MODE = "Orthogonal Projections Mode";
   static final String KEEP_ORIGINAL = "KeepOriginal";
   static final String MAX = "Max";
   static final String AVG = "Avg";
   static final String MODE = "Mode";
   static final String FAST = "Fast";
   static final String QUALITY = "Quality";

   private final Studio studio_;
   private final MutablePropertyMapView settings_;

   /**
    * Generates the UI.
    *
    * @param configuratorSettings I am always confused about this propertymap
    * @param studio The Studio instance, usually a singleton.
    */
   public DeskewFrame(PropertyMap configuratorSettings, Studio studio) {
      studio_ = studio;
      settings_ = studio_.profile().getSettings(this.getClass());
      copySettings(settings_, configuratorSettings);

      initComponents();

      super.setLocation(DEFAULT_WIN_X, DEFAULT_WIN_Y);
      WindowPositioning.setUpLocationMemory(this, this.getClass(), null);
   }

   @Override
   public void showGUI() {
      setVisible(true);
   }

   @Override
   public void cleanup() {

   }

   @Override
   public PropertyMap getSettings() {
      return settings_.toPropertyMap();
   }

   private void initComponents() {
      super.setTitle(DIALOG_TITLE);
      setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
      setLayout(new MigLayout("flowx"));

      JRadioButton fastButton = new JRadioButton(FAST);
      fastButton.setSelected(settings_.getString(MODE, FAST).equals(FAST));
      fastButton.addActionListener(e -> settings_.putString(MODE, FAST));
      JRadioButton qualityButton = new JRadioButton(QUALITY);
      qualityButton.addActionListener(e -> settings_.putString(MODE, QUALITY));
      final ButtonGroup bg = new ButtonGroup();
      bg.add(fastButton);
      bg.add(qualityButton);
      qualityButton.setSelected(settings_.getString(MODE, FAST).equals(QUALITY));
      add(fastButton, "span 4, split 2");
      add(qualityButton, "wrap");

      add(new JLabel("Sheet angle (_\\) in radians:"), "alignx left");
      final JTextField thetaTextField = new JTextField(5);
      thetaTextField.setText(settings_.getString(THETA, "20"));
      thetaTextField.getDocument().addDocumentListener(
              new TextFieldUpdater(thetaTextField, THETA, 0.0, settings_));
      settings_.putString(THETA, thetaTextField.getText());
      add(thetaTextField, "wrap");

      add(createCheckBox(FULL_VOLUME, true), "span 2, wrap");
      add(createCheckBox(XY_PROJECTION, true), "span 2");
      List<JComponent> buttons =  projectionModeUI(XY_PROJECTION_MODE);
      add(buttons.get(0));
      add(buttons.get(1), "wrap");
      add(createCheckBox(ORTHOGONAL_PROJECTIONS, true), "span 2");
      buttons = projectionModeUI(ORTHOGONAL_PROJECTIONS_MODE);
      add(buttons.get(0));
      add(buttons.get(1), "wrap");
      add(createCheckBox(KEEP_ORIGINAL, true), "span 2, wrap");

      pack();
   }

   private void copySettings(MutablePropertyMapView settings, PropertyMap configuratorSettings) {
      settings.putString(THETA, configuratorSettings.getString(THETA, "0"));
   }

   private JCheckBox createCheckBox(String key, boolean initialValue) {
      JCheckBox checkBox = new JCheckBox(key);
      checkBox.setSelected(settings_.getBoolean(key, initialValue));
      checkBox.addChangeListener(e -> settings_.putBoolean(key, checkBox.isSelected()));
      return checkBox;
   }

   private List<JComponent> projectionModeUI(String key) {
      JRadioButton max = new JRadioButton(MAX);
      max.setSelected(settings_.getString(key, MAX).equals(MAX));
      max.addChangeListener(e -> settings_.putString(key, max.isSelected() ? MAX : AVG));
      JRadioButton avg = new JRadioButton(AVG);
      avg.setSelected(settings_.getString(key, MAX).equals(AVG));
      avg.addChangeListener(e -> settings_.putString(key, avg.isSelected() ? AVG : MAX));
      final ButtonGroup bg = new ButtonGroup();
      bg.add(max);
      bg.add(avg);
      List<JComponent> result = new ArrayList<>();
      result.add(max);
      result.add(avg);
      return result;

   }

   private List<String> openCLDevices() {
      List<String> result = new ArrayList<>();
      ClearCLBackendInterface lClearCLBackend = ClearCLBackends.getBestBackend();
      ClearCL lClearCL = new ClearCL(lClearCLBackend);
      for (ClearCLDevice lDevice : lClearCL.getAllDevices()) {
         result.add(lDevice.getName());
      }
      return result;
   }


   private class TextFieldUpdater implements DocumentListener {

      private final JTextField field_;
      private final String key_;
      private final MutablePropertyMapView settings_;
      private final Object type_;

      public TextFieldUpdater(JTextField field, String key, Object type,
                              MutablePropertyMapView settings) {
         field_ = field;
         key_ = key;
         settings_ = settings;
         type_ = type;
      }

      @Override
      public void insertUpdate(DocumentEvent e) {
         processEvent();
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
         processEvent();
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
         processEvent();
      }

      private void processEvent() {
         if (type_ instanceof Double) {
            try {
               double factor = NumberUtils.displayStringToDouble(field_.getText());
               settings_.putString(key_, NumberUtils.doubleToDisplayString(factor));
            } catch (ParseException p) {
               studio_.logs().logError("Error parsing number in DeskewFrame.");
            }
         }
      }
   }

}