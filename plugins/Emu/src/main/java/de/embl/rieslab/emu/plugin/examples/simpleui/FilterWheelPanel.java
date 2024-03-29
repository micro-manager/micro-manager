package de.embl.rieslab.emu.plugin.examples.simpleui;

import de.embl.rieslab.emu.ui.ConfigurablePanel;
import de.embl.rieslab.emu.ui.swinglisteners.SwingUIListeners;
import de.embl.rieslab.emu.ui.uiparameters.StringUIParameter;
import de.embl.rieslab.emu.ui.uiproperties.MultiStateUIProperty;
import de.embl.rieslab.emu.utils.ColorRepository;
import de.embl.rieslab.emu.utils.exceptions.UnknownUIParameterException;
import de.embl.rieslab.emu.utils.exceptions.UnknownUIPropertyException;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Insets;
import javax.swing.ButtonGroup;
import javax.swing.JToggleButton;
import javax.swing.border.TitledBorder;


public class FilterWheelPanel extends ConfigurablePanel {

   private static final long serialVersionUID = 1L;
   //////// Properties
   public final String fwPosition = "Filterwheel position";
   //////// Parameters
   public final String paramNames = "Filter names";
   public final String paramColors = "Filter colors";
   public final String paramTitle = "Panel title";
   //////// Initial parameter
   public final int numPos = 6;
   public final String title = "Filters";
   //////// Generated by Eclipse WindowBuilder
   private final ButtonGroup buttonGroup = new ButtonGroup();
   private JToggleButton tglbtnFilter;
   private JToggleButton tglbtnFilter2;
   private JToggleButton tglbtnFilter1;
   private JToggleButton tglbtnFilter5;
   private JToggleButton tglbtnFilter4;
   private JToggleButton tglbtnFilter3;

   public FilterWheelPanel(String label) {
      super(label);
      initComponents();
   }

   // Generated by Eclipse WindowBuilder
   private void initComponents() {
      setBorder(new TitledBorder(null, title, TitledBorder.LEFT, TitledBorder.TOP, null, null));

      // except this bit, introduced to make the title in bold
      ((TitledBorder) this.getBorder()).setTitleFont(
            ((TitledBorder) this.getBorder()).getTitleFont().deriveFont(Font.BOLD, 12));

      setLayout(new GridLayout(1, 0, 0, 0));

      tglbtnFilter = new JToggleButton("Filter 1");
      buttonGroup.add(tglbtnFilter);
      tglbtnFilter.setMargin(new Insets(2, 2, 2, 2));
      tglbtnFilter.setFont(new Font("Tahoma", Font.BOLD, 12));
      add(tglbtnFilter);

      tglbtnFilter1 = new JToggleButton("Filter 2");
      buttonGroup.add(tglbtnFilter1);
      tglbtnFilter1.setMargin(new Insets(2, 2, 2, 2));
      tglbtnFilter1.setFont(new Font("Tahoma", Font.BOLD, 12));
      add(tglbtnFilter1);

      tglbtnFilter2 = new JToggleButton("Filter 3");
      buttonGroup.add(tglbtnFilter2);
      tglbtnFilter2.setMargin(new Insets(2, 2, 2, 2));
      tglbtnFilter2.setFont(new Font("Tahoma", Font.BOLD, 12));
      add(tglbtnFilter2);

      tglbtnFilter3 = new JToggleButton("Filter 4");
      buttonGroup.add(tglbtnFilter3);
      tglbtnFilter3.setMargin(new Insets(2, 2, 2, 2));
      tglbtnFilter3.setFont(new Font("Tahoma", Font.BOLD, 12));
      add(tglbtnFilter3);

      tglbtnFilter4 = new JToggleButton("Filter 5");
      buttonGroup.add(tglbtnFilter4);
      tglbtnFilter4.setMargin(new Insets(2, 2, 2, 2));
      tglbtnFilter4.setFont(new Font("Tahoma", Font.BOLD, 12));
      add(tglbtnFilter4);

      tglbtnFilter5 = new JToggleButton("Filter 6");
      buttonGroup.add(tglbtnFilter5);
      tglbtnFilter5.setMargin(new Insets(2, 2, 2, 2));
      tglbtnFilter5.setFont(new Font("Tahoma", Font.BOLD, 12));
      add(tglbtnFilter5);
   }


   @Override
   protected void addComponentListeners() {
      /*
       *  MultiStateUIProperty accept indices as input to select the property state.
       *  Therefore, here, we add a listener to the button group that updates a
       *  UIProperty with the newly selected button index within the ButtonGroup.
       */
      SwingUIListeners.addActionListenerOnSelectedIndex(this, fwPosition, buttonGroup);
   }

   @Override
   public String getDescription() {
      // Returns a description of the panel.
      return "The " + getPanelLabel()
            + " panel is used to control a filterwheel with a maximum of "
            + numPos
            + " positions. The names and colors of the filters can be set as parameters.";
   }

   @Override
   protected void initializeInternalProperties() {
      // Do nothing
   }

   @Override
   protected void initializeParameters() {

      // We create comma separated strings of 6 filters a default parameters values.
      String names = "None";
      String colors = "gray";
      for (int i = 0; i < numPos - 1; i++) {
         names += "," + "None";
         colors += "," + "gray";
      }

      // Descriptions of the parameters
      String helpNames =
            "Comma separated filter names, e.g.: \"name1,name2,name3,name4,None,None\".";
      String helpColors =
            "Comma separated filter colors, e.g: \"blue,dark red,dark green,orange,gray,gray\".\n"
                  + "The available colors are: " + ColorRepository.getCommaSeparatedColors();

      // Finally, we create two StringUIParametesr
      addUIParameter(new StringUIParameter(this, paramNames, helpNames, names));
      addUIParameter(new StringUIParameter(this, paramColors, helpColors, colors));
      addUIParameter(new StringUIParameter(this, paramTitle, "Panel title.", title));
   }

   @Override
   protected void initializeProperties() {
      // Description of the UIProperty
      String description = "Filter wheel position property.";

      // We create a MultiStateUIProperty with 6 states.
      addUIProperty(new MultiStateUIProperty(this, fwPosition, description, numPos));
   }

   @Override
   public void internalpropertyhasChanged(String arg0) {
      // Do nothing
   }

   @Override
   protected void parameterhasChanged(String parameter) {
      if (paramNames.equals(parameter)) { // if the names parameter has changed
         try {
            // Retrieves the new value of the parameter
            String value = getStringUIParameterValue(paramNames);

            // Split it with at the commas
            String[] astr = value.split(",");

            // Takes the smallest number between the number of states and the length of astr
            int maxind = numPos > astr.length ? astr.length : numPos;

            // Creates an array of buttons to loop on it
            JToggleButton[] buttons =
               {tglbtnFilter, tglbtnFilter1, tglbtnFilter2, tglbtnFilter3, tglbtnFilter4,
                     tglbtnFilter5};

            // For each button, sets the new text
            for (int i = 0; i < maxind; i++) {
               buttons[i].setText(astr[i]);
            }

         } catch (UnknownUIParameterException e) { // necessary in case PARAM_NAMES is unknown
            e.printStackTrace();
         }
      } else if (paramColors.equals(parameter)) {
         try {
            // Retrieves the new value of the parameter
            String value = getStringUIParameterValue(paramColors);

            // Split it with at the commas
            String[] astr = value.split(",");

            // Takes the smallest number between the number of states and the length of astr
            int maxind = numPos > astr.length ? astr.length : numPos;

            // Creates an array of buttons to loop on it
            JToggleButton[] buttons =
               {tglbtnFilter, tglbtnFilter1, tglbtnFilter2, tglbtnFilter3, tglbtnFilter4,
                     tglbtnFilter5};

            // For each button, sets the new color using the EMU ColorRepository
            for (int i = 0; i < maxind; i++) {
               buttons[i].setForeground(ColorRepository.getColor(astr[i]));
            }
         } catch (UnknownUIParameterException e) { // necessary in case PARAM_COLORS is unknown
            e.printStackTrace();
         }
      } else if (paramTitle.equals(parameter)) {
         try {
            // retrieves the title as a String
            String title = getStringUIParameterValue(paramTitle);

            // gets the TitledBorder and change its title, then updates the panel
            TitledBorder border = (TitledBorder) this.getBorder();
            border.setTitle(title);
            this.repaint();
         } catch (UnknownUIParameterException e) {
            e.printStackTrace();
         }
      }
   }

   @Override
   protected void propertyhasChanged(String propertyName, String newvalue) {
      if (fwPosition.equals(propertyName)) { // making sure the property is FW_POSITION
         int pos;
         try {
            // Retrieves the current selected position index from the MultiStateUIProperty
            pos = ((MultiStateUIProperty) getUIProperty(fwPosition)).getStateIndex(newvalue);

            // Selects the corresponding JToggleButton
            switch (pos) {
               case 0:
                  tglbtnFilter.setSelected(true);
                  break;
               case 1:
                  tglbtnFilter1.setSelected(true);
                  break;
               case 2:
                  tglbtnFilter2.setSelected(true);
                  break;
               case 3:
                  tglbtnFilter3.setSelected(true);
                  break;
               case 4:
                  tglbtnFilter4.setSelected(true);
                  break;
               case 5:
                  tglbtnFilter5.setSelected(true);
                  break;
               default:
                  break;
            }
         } catch (UnknownUIPropertyException e) { // necessary in case FW_POSITION is not a
            // known UIProperty
            e.printStackTrace();
         }
      }
   }

   @Override
   public void shutDown() {
      // Do nothing
   }

   protected JToggleButton getTglbtnFilter() {
      return tglbtnFilter;
   }

   protected JToggleButton getTglbtnFilter2() {
      return tglbtnFilter2;
   }

   protected JToggleButton getTglbtnFilter1() {
      return tglbtnFilter1;
   }

   protected JToggleButton getTglbtnFilter5() {
      return tglbtnFilter5;
   }

   protected JToggleButton getTglbtnFilter4() {
      return tglbtnFilter4;
   }

   protected JToggleButton getTglbtnFilter3() {
      return tglbtnFilter3;
   }
}