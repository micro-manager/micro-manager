package de.embl.rieslab.emu.plugin.examples.ibeamsmart;

import de.embl.rieslab.emu.plugin.examples.components.TogglePower;
import de.embl.rieslab.emu.plugin.examples.components.ToggleSlider;
import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.swinglisteners.SwingUIListeners;
import de.embl.rieslab.emu.ui.uiparameters.BoolUIParameter;
import de.embl.rieslab.emu.ui.uiparameters.StringUIParameter;
import de.embl.rieslab.emu.ui.uiproperties.TwoStateUIProperty;
import de.embl.rieslab.emu.ui.uiproperties.UIProperty;
import de.embl.rieslab.emu.ui.uiproperties.flag.NoFlag;
import de.embl.rieslab.emu.utils.ColorRepository;
import de.embl.rieslab.emu.utils.EmuUtils;
import de.embl.rieslab.emu.utils.exceptions.IncorrectUIParameterTypeException;
import de.embl.rieslab.emu.utils.exceptions.IncorrectUIPropertyTypeException;
import de.embl.rieslab.emu.utils.exceptions.UnknownUIParameterException;
import de.embl.rieslab.emu.utils.exceptions.UnknownUIPropertyException;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSlider;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.border.TitledBorder;


public class IBeamSmartPanel extends ConfigurablePanel {

   private static final long serialVersionUID = 1L;
   //////// Properties
   public final String laserOperation = "operation";
   public final String laserEnablefine = "enable fine";
   public final String laserPower = "laser power";
   public final String laserPercfinea = "fine a (%)";
   public final String laserPercfineb = "fine b (%)";
   public final String laserMaxpower = "max power";
   public final String laserExternaltrigger = "ext. trigger";
   //////// Parameters
   public final String paramEnableFine = "fine available";
   public final String paramEnableExtTrigger = "ext. trigger available";
   public final String paramTitle = "laser name";
   private final String enabled_ = "enabled";
   private final String disabled_ = "disabled";
   //////// Components
   private JTextField textfieldUserPower_;
   private JSlider sliderPower_;
   private JSlider sliderFinea_;
   private JSlider sliderFineb_;
   private JToggleButton togglebuttonLaserOnOff_;
   private ToggleSlider togglebuttonExternalTrigger_;
   private ToggleSlider togglesliderenableFine_;
   private JLabel fineaperc_;
   private JLabel finebperc_;
   private TitledBorder border_;
   /////// Misc variables
   private int maxPower_;
   private JPanel cardTrigger;
   private JPanel cardFine;
   private String title_;

   public IBeamSmartPanel(String label) {
      super(label);

      setupPanel();
   }

   private void setupPanel() {
      // create border
      border_ = BorderFactory.createTitledBorder(null, title_, TitledBorder.DEFAULT_JUSTIFICATION,
            TitledBorder.DEFAULT_POSITION, null, Color.black);
      this.setBorder(border_);
      border_.setTitleFont(border_.getTitleFont().deriveFont(Font.BOLD, 12));

      ////////////////////////////////////////////////////////////////////////// set-up components
      // Power text field
      textfieldUserPower_ = new JTextField(String.valueOf(maxPower_));
      textfieldUserPower_.setPreferredSize(new Dimension(35, 20));

      // slider channel 1
      sliderPower_ = new JSlider(JSlider.HORIZONTAL, 0, maxPower_, 0);

      // slider fine a
      sliderFinea_ = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);

      // Slider fine b
      sliderFineb_ = new JSlider(JSlider.HORIZONTAL, 0, 100, 0);

      togglebuttonLaserOnOff_ = new TogglePower();

      // ext trigger
      togglebuttonExternalTrigger_ = new ToggleSlider();

      // Fine enable
      togglesliderenableFine_ = new ToggleSlider();

      fineaperc_ = new JLabel("100 %");
      finebperc_ = new JLabel("100 %");

      // others
      final JLabel fineAperc = new JLabel("a");
      final JLabel finebperc = new JLabel("b");
      final JLabel power = new JLabel("Power (mW):");

      ///////////////////////////////////////////////////////////////////////////// Channel 1

      JPanel panelOperation = new JPanel();
      panelOperation.setLayout(new GridBagLayout());
      TitledBorder border2 =
            BorderFactory.createTitledBorder(null, "Power", TitledBorder.DEFAULT_JUSTIFICATION,
                  TitledBorder.DEFAULT_POSITION,
                  null, ColorRepository.getColor(ColorRepository.strblack));
      panelOperation.setBorder(border2);

      GridBagConstraints c2 = new GridBagConstraints();
      c2.gridx = 1;
      c2.gridy = 0;
      c2.ipadx = 5;
      c2.ipady = 5;
      c2.weightx = 0.2;
      c2.weighty = 0.3;
      c2.fill = GridBagConstraints.BOTH;
      panelOperation.add(power, c2);

      c2.gridx = 2;
      panelOperation.add(textfieldUserPower_, c2);


      c2.gridx = 3;
      panelOperation.add(togglebuttonLaserOnOff_, c2);

      c2.gridx = 0;
      c2.gridy = 4;
      c2.gridwidth = 4;
      c2.weightx = 0.9;
      c2.weighty = 0.5;
      panelOperation.add(sliderPower_, c2);

      ///////////////////////////////////////////////////////////////////////////// trigger
      cardTrigger = new JPanel(new CardLayout());
      JPanel panelTrigger = new JPanel();
      panelTrigger.setLayout(new GridBagLayout());
      TitledBorder borderTrigger = BorderFactory.createTitledBorder(null, "External trigger",
            TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION,
            null, ColorRepository.getColor(ColorRepository.strblack));
      panelTrigger.setBorder(borderTrigger);

      // gridbad layout
      GridBagConstraints cTrig = new GridBagConstraints();
      cTrig.fill = GridBagConstraints.HORIZONTAL;
      cTrig.gridx = 0;
      cTrig.gridy = 0;
      panelTrigger.add(togglebuttonExternalTrigger_, cTrig);
      cardTrigger.add(panelTrigger, enabled_);
      cardTrigger.add(new JPanel(), disabled_);

      ///////////////////////////////////////////////////////////////////////////// fine
      cardFine = new JPanel(new CardLayout());
      JPanel panelFine = new JPanel();
      panelFine.setLayout(new GridBagLayout());
      TitledBorder borderfine =
            BorderFactory.createTitledBorder(null, "Fine", TitledBorder.DEFAULT_JUSTIFICATION,
                  TitledBorder.DEFAULT_POSITION,
                  null, ColorRepository.getColor(ColorRepository.strblack));
      panelFine.setBorder(borderfine);

      // gridbad layout
      GridBagConstraints cfine = new GridBagConstraints();
      cfine.fill = GridBagConstraints.HORIZONTAL;
      cfine.ipadx = 35;
      cfine.ipady = 2;
      cfine.gridx = 0;
      cfine.gridy = 0;
      panelFine.add(togglesliderenableFine_, cfine);

      cfine.gridx = 1;
      cfine.gridy = 1;
      cfine.ipadx = 5;
      panelFine.add(fineAperc, cfine);

      cfine.gridy = 2;
      panelFine.add(finebperc, cfine);

      cfine.gridx = 2;
      cfine.gridy = 1;
      cfine.ipadx = 4;
      cfine.gridwidth = 3;
      panelFine.add(sliderFinea_, cfine);

      cfine.gridy = 2;
      panelFine.add(sliderFineb_, cfine);

      cfine.gridx = 5;
      cfine.gridy = 1;
      cfine.ipadx = 5;
      cfine.gridwidth = 1;
      cfine.insets = new Insets(2, 35, 2, 2);
      panelFine.add(fineaperc_, cfine);

      cfine.gridy = 2;
      cfine.insets = new Insets(2, 35, 2, 2);
      panelFine.add(finebperc_, cfine);

      cardFine.add(panelFine, enabled_);
      cardFine.add(new JPanel(), disabled_);


      //////////////////////////////////////////////////////////////////////////// Main panel
      this.setLayout(new GridBagLayout());
      GridBagConstraints c = new GridBagConstraints();
      c.fill = GridBagConstraints.HORIZONTAL;
      c.weightx = 0.4;
      c.weighty = 0.2;
      c.gridx = 0;
      c.gridy = 0;
      c.gridwidth = 2;
      c.gridheight = 1;
      this.add(panelOperation, c);

      c.gridx = 0;
      c.gridy = 1;
      c.gridheight = 1;
      c.fill = GridBagConstraints.HORIZONTAL;
      this.add(cardFine, c);

      c.gridy = 2;
      this.add(cardTrigger, c);

      c.gridx = 0;
      c.gridy = 3;
      c.weighty = 0.8;
      c.fill = GridBagConstraints.BOTH;
      this.add(new JPanel(), c);
   }

   @Override
   protected void initializeProperties() {
      maxPower_ = 200;

      addUIProperty(
            new UIProperty(this, getPropertyName(laserPower), "Power (mW).", new NoFlag()));
      addUIProperty(new UIProperty(this, getPropertyName(laserPercfinea), "Fine a percentage.",
            new NoFlag()));
      addUIProperty(new UIProperty(this, getPropertyName(laserPercfineb), "Fine b percentage.",
            new NoFlag()));
      addUIProperty(new UIProperty(this, getPropertyName(laserMaxpower), "Maximum power (mW).",
            new NoFlag()));

      addUIProperty(new TwoStateUIProperty(this, getPropertyName(laserOperation),
            "On/off operation property.", new NoFlag()));
      addUIProperty(
            new TwoStateUIProperty(this, getPropertyName(laserEnablefine), "Enable/disable fine.",
                  new NoFlag()));
      addUIProperty(new TwoStateUIProperty(this, getPropertyName(laserExternaltrigger),
            "Enable/disable digital trigger.", new NoFlag()));
   }

   @Override
   public void propertyhasChanged(String name, String newvalue) {
      if (getPropertyName(laserPower).equals(name)) {
         if (EmuUtils.isNumeric(newvalue)) {
            double val = Double.parseDouble(newvalue);
            if (val >= 0 && val <= maxPower_) {
               textfieldUserPower_.setText(String.valueOf(val));
               sliderPower_.setValue((int) val);
            }
         }
      } else if (getPropertyName(laserPercfinea).equals(name)) {
         if (EmuUtils.isNumeric(newvalue)) {
            double val = Double.parseDouble(newvalue);
            if (val >= 0 && val <= 100) {
               if (val < 100) {
                  fineaperc_.setText("  " + val + " %");
               } else {
                  fineaperc_.setText(val + " %");
               }
               sliderFinea_.setValue((int) val);
            }
         }
      } else if (getPropertyName(laserPercfineb).equals(name)) {
         if (EmuUtils.isNumeric(newvalue)) {
            double val = Double.parseDouble(newvalue);
            if (val >= 0 && val <= 100) {
               if (val < 100) {
                  finebperc_.setText("  " + val + " %");
               } else {
                  finebperc_.setText(val + " %");
               }
               sliderFineb_.setValue((int) val);
            }
         }
      } else if (getPropertyName(laserOperation).equals(name)) {
         try {
            togglebuttonLaserOnOff_.setSelected(
                  ((TwoStateUIProperty) getUIProperty(getPropertyName(laserOperation))).isOnState(
                        newvalue));
         } catch (UnknownUIPropertyException e) {
            e.printStackTrace();
         }

      } else if (getPropertyName(laserExternaltrigger).equals(name)) {
         try {
            togglebuttonExternalTrigger_.setSelected(((TwoStateUIProperty) getUIProperty(
                  getPropertyName(laserExternaltrigger))).isOnState(newvalue));
         } catch (UnknownUIPropertyException e) {
            e.printStackTrace();
         }

      } else if (getPropertyName(laserEnablefine).equals(name)) {
         try {
            togglesliderenableFine_.setSelected(
                  ((TwoStateUIProperty) getUIProperty(getPropertyName(laserEnablefine))).isOnState(
                        newvalue));
         } catch (UnknownUIPropertyException e) {
            e.printStackTrace();
         }
      } else if (getPropertyName(laserMaxpower).equals(name)) {
         if (EmuUtils.isNumeric(newvalue)) {
            double val = Double.parseDouble(newvalue);
            maxPower_ = (int) val;
            if (sliderPower_ != null) {
               sliderPower_.setMaximum(maxPower_);
            }
         }
      }
   }

   private String getPropertyName(String propertyLabel) {
      return getPanelLabel() + " " + propertyLabel;
   }

   @Override
   public void shutDown() {
      // Do nothing
   }

   @Override
   public String getDescription() {
      return "This panel controls an iBeamSmart laser from Toptica.";
   }


   @Override
   protected void initializeInternalProperties() {
      // Do nothing
   }

   @Override
   public void internalpropertyhasChanged(String label) {
      // Do nothing
   }

   @Override
   protected void initializeParameters() {
      addUIParameter(new BoolUIParameter(this, paramEnableFine,
            "Show/hide the fine feature panel. Check if the feature is available for your device.",
            true));
      addUIParameter(new BoolUIParameter(this, paramEnableExtTrigger,
            "Show/hide the external trigger panel. Check if the feature is available "
                  + "for your device.",
            true));

      title_ = "Laser";
      addUIParameter(new StringUIParameter(this, paramTitle, "Panel title.", title_));
   }

   @Override
   public void parameterhasChanged(String label) {
      if (paramEnableFine.equals(label)) {
         try {
            if (getBoolUIParameterValue(paramEnableFine)) {
               ((CardLayout) cardFine.getLayout()).show(cardFine, enabled_);
            } else {
               ((CardLayout) cardFine.getLayout()).show(cardFine, disabled_);
            }
         } catch (IncorrectUIParameterTypeException | UnknownUIParameterException e) {
            e.printStackTrace();
         }
      } else if (paramEnableExtTrigger.equals(label)) {
         try {
            if (getBoolUIParameterValue(paramEnableExtTrigger)) {
               ((CardLayout) cardTrigger.getLayout()).show(cardTrigger, enabled_);
            } else {
               ((CardLayout) cardTrigger.getLayout()).show(cardTrigger, disabled_);
            }
         } catch (IncorrectUIParameterTypeException | UnknownUIParameterException e) {
            e.printStackTrace();
         }
      } else if (paramTitle.equals(label)) {
         try {
            title_ = getStringUIParameterValue(paramTitle);
            border_.setTitle(title_);
            this.repaint();
         } catch (UnknownUIParameterException e) {
            e.printStackTrace();
         }
      }
   }

   @Override
   protected void addComponentListeners() {
      // power textfield
      SwingUIListeners.addActionListenerOnIntegerValue(this, getPropertyName(laserPower),
            textfieldUserPower_, sliderPower_);

      // slider power
      SwingUIListeners.addActionListenerOnIntegerValue(this, getPropertyName(laserPower),
            sliderPower_, textfieldUserPower_);

      // slider fine a
      SwingUIListeners.addActionListenerOnIntegerValue(this, getPropertyName(laserPercfinea),
            sliderFinea_, fineaperc_, "", " %");

      // slider fine b
      SwingUIListeners.addActionListenerOnIntegerValue(this, getPropertyName(laserPercfinea),
            sliderFineb_, finebperc_, "", " %");

      // laser on/off
      try {
         SwingUIListeners.addActionListenerToTwoState(this, getPropertyName(laserOperation),
               togglebuttonLaserOnOff_);
      } catch (IncorrectUIPropertyTypeException e1) {
         e1.printStackTrace();
      }

      // ext trigger
      try {
         SwingUIListeners.addActionListenerToTwoState(this, getPropertyName(laserExternaltrigger),
               togglebuttonExternalTrigger_);
      } catch (IncorrectUIPropertyTypeException e1) {
         e1.printStackTrace();
      }

      // Fine enable
      try {
         SwingUIListeners.addActionListenerToTwoState(this, getPropertyName(laserEnablefine),
               togglesliderenableFine_);
      } catch (IncorrectUIPropertyTypeException e1) {
         e1.printStackTrace();
      }
   }
}