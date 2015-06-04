///////////////////////////////////////////////////////////////////////////////
//PROJECT:       Micro-Manager
//SUBSYSTEM:     mmstudio
//-----------------------------------------------------------------------------
//
// AUTHOR:       Nenad Amodaj, nenad@amodaj.com, 2005
//               Arthur Edelstein, arthuredelstein@gmail.com
//
// COPYRIGHT:    University of California San Francisco, 2005
//               100X Imaging Inc, 2009
//
// LICENSE:      This file is distributed under the BSD license.
//               License text is included with the source distribution.
//
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.internal.utils;

import com.bulenkov.iconloader.IconLoader;

import ij.WindowManager;
import ij.gui.ImageWindow;

import java.awt.AWTEvent;
import java.awt.Container;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.AWTEventListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.Font;
import java.awt.geom.Rectangle2D;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.GraphicsConfiguration;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Vector;
import javax.swing.*;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;
import javax.swing.text.JTextComponent;
import org.micromanager.internal.MMStudio;



public class GUIUtils {
   private static final String DIALOG_POSITION = "dialogPosition";
   public static final Font buttonFont = new Font("Arial", Font.PLAIN, 10);

   public static void setComboSelection(JComboBox cb, String sel){
      ActionListener[] listeners = cb.getActionListeners();
      for (int i=0; i<listeners.length; i++)            
         cb.removeActionListener(listeners[i]);
      cb.setSelectedItem(sel);
      for (int i=0; i<listeners.length; i++)            
         cb.addActionListener(listeners[i]);
   }

   public static void replaceComboContents(JComboBox cb, String[] items) {
      
      // remove listeners
      ActionListener[] listeners = cb.getActionListeners();
      for (ActionListener listener : listeners) {
         cb.removeActionListener(listener);
      }

      if (cb.getItemCount() > 0)
         cb.removeAllItems();
      
      // add contents
      for (int i=0; i<items.length; i++){
         cb.addItem(items[i]);
      }
      
      // restore listeners
      for (ActionListener listener : listeners) {
         cb.addActionListener(listener);
      }
   }
   
   public static ChangeListener[] detachChangeListeners(JSpinner spinner) {
      ChangeListener[] listeners = spinner.getChangeListeners();
      for (ChangeListener listener : listeners) {
         spinner.removeChangeListener(listener);
      }
      return listeners;
   }
   
   public static void reattachChangeListeners(JSpinner spinner, ChangeListener[] listeners) {
      for (ChangeListener listener : listeners) {
         spinner.addChangeListener(listener);
      }
   }
   
   public static void replaceSpinnerValue(JSpinner spinner, double value) {
      ChangeListener[] listeners = detachChangeListeners(spinner);
      spinner.setValue(value);
      reattachChangeListeners(spinner, listeners);      
   }

   /* 
    * This takes care of a Java bug that would throw several exceptions when a
    * Projector device is attached in Windows.
    */
   public static void preventDisplayAdapterChangeExceptions() {
      try {
         if (JavaUtils.isWindows()) { // Check that we are in windows.
            //Dynamically load sun.awt.Win32GraphicsEnvironment, because it seems to be missing from
            //the Mac OS X JVM.
            ClassLoader cl = ClassLoader.getSystemClassLoader();
            Class<?> envClass = cl.loadClass("sun.awt.Win32GraphicsEnvironment");
            //Get the current local graphics environment.
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            //Send notification that display may have changed, so that display count is updated.
            envClass.getDeclaredMethod("displayChanged").invoke(envClass.cast(ge));
         }
      } catch (ClassNotFoundException e) {
         ReportingUtils.logError(e);
      } catch (NoSuchMethodException e) {
         ReportingUtils.logError(e);
      } catch (SecurityException e) {
         ReportingUtils.logError(e);
      } catch (IllegalAccessException e) {
         ReportingUtils.logError(e);
      } catch (IllegalArgumentException e) {
         ReportingUtils.logError(e);
      } catch (InvocationTargetException e) {
         ReportingUtils.logError(e);
      }

   }

   public static void setClickCountToStartEditing(JTable table, int count) {
      for (int columnIndex = 0; columnIndex < table.getColumnCount(); ++columnIndex) {
         TableCellEditor cellEditor = table.getColumnModel().getColumn(columnIndex).getCellEditor();
         if (cellEditor instanceof DefaultCellEditor) {
            ((DefaultCellEditor) cellEditor).setClickCountToStart(count);
         }
      }
   }

    public static void stopEditingOnLosingFocus(final JTable table) {
       table.putClientProperty("terminateEditOnFocusLost", Boolean.TRUE);
    }

    /**
     * Verifies if the given point is visible on the screen.
     * From http://www.java2s.com/Code/Java/Swing-JFC/Verifiesifthegivenpointisvisibleonthescreen.htm
     * @param   location     The given location on the screen.
     * @return           True if the location is on the screen, false otherwise.

    */
   public static boolean isLocationInScreenBounds(Point location) {
      GraphicsConfiguration config = getGraphicsConfigurationContaining(
            location.x, location.y);
      return (config != null);
   }

   /**
    * Get the usable screen area (minus taskbars, etc.) for the specified
    * monitor.
    * Adapted from http://stackoverflow.com/questions/10123735/get-effective-screen-size-from-java
    */
   public static Rectangle getFullScreenBounds(GraphicsConfiguration config) {
      Rectangle bounds = config.getBounds();
      Insets insets = Toolkit.getDefaultToolkit().getScreenInsets(config);
      bounds.x += insets.left;
      bounds.y += insets.top;
      bounds.width -= (insets.left + insets.right);
      bounds.height -= (insets.top + insets.bottom);
      return bounds;
   }


   public static GraphicsConfiguration getGraphicsConfigurationContaining(
         int x, int y) {
      for (GraphicsConfiguration config : getConfigs()) {
         Rectangle bounds = config.getBounds();
         if (bounds.contains(x, y)) {
            return config;
         }
      }
      return null;
   }

   /**
    * If a window's top-left corner is off the screen, then
    * getGraphicsConfigurationContaining will return null. This method in
    * contrast will find the GraphicsConfiguration that shows the greatest
    * portion of the provided rectangle; thus it should always return a
    * GraphicsConfiguration so long as any part of the rect is visible.
    */
   public static GraphicsConfiguration getGraphicsConfigurationBestMatching(
         Rectangle rect) {
      GraphicsConfiguration best = null;
      double bestArea = -1;
      for (GraphicsConfiguration config : getConfigs()) {
         Rectangle2D intersect = rect.createIntersection(config.getBounds());
         if (intersect != null &&
               intersect.getWidth() * intersect.getHeight() > bestArea) {
            bestArea = intersect.getWidth() * intersect.getHeight();
            best = config;
         }
      }
      return best;
   }

   /**
    * Convenience method to iterate over all graphics configurations.
    */
   private static ArrayList<GraphicsConfiguration> getConfigs() {
      ArrayList<GraphicsConfiguration> result = new ArrayList<GraphicsConfiguration>();
      GraphicsEnvironment env = GraphicsEnvironment.getLocalGraphicsEnvironment();
      GraphicsDevice[] devices = env.getScreenDevices();
      for (GraphicsDevice device : devices) {
         GraphicsConfiguration[] configs = device.getConfigurations();
         for (GraphicsConfiguration config : configs) {
            result.add(config);
         }
      }
      return result;
   }

   public static void registerImageFocusListener(final ImageFocusListener listener) {
      AWTEventListener awtEventListener = new AWTEventListener() {
         private ImageWindow currentImageWindow_ = null;
         @Override
         public void eventDispatched(AWTEvent event) {
            if (event instanceof WindowEvent) {
               if (0 != (event.getID() & WindowEvent.WINDOW_GAINED_FOCUS)) {
                  if (event.getSource() instanceof ImageWindow) {
                     ImageWindow focusedWindow = WindowManager.getCurrentWindow();
                     if (currentImageWindow_ != focusedWindow) {
                        //if (focusedWindow.isVisible() && focusedWindow instanceof ImageWindow) {
                           listener.focusReceived(focusedWindow);
                           currentImageWindow_ = focusedWindow;
                        //}
                     }
                  }
               }
            }
         }
      };

      Toolkit.getDefaultToolkit().addAWTEventListener(awtEventListener,
              AWTEvent.WINDOW_FOCUS_EVENT_MASK);
   }

   /*
    * Wraps SwingUtilities.invokeAndWait so that if it is being called
    * from the EDT, then the runnable is simply run.
    */
   public static void invokeAndWait(Runnable r) throws InterruptedException, InvocationTargetException {
      if (SwingUtilities.isEventDispatchThread()) {
         r.run();
      } else {
         SwingUtilities.invokeAndWait(r);
      }
   }

      /*
    * Wraps SwingUtilities.invokeLater so that if it is being called
    * from the EDT, then the runnable is simply run.
    */
   public static void invokeLater(Runnable r) throws InterruptedException, InvocationTargetException {
      if (SwingUtilities.isEventDispatchThread()) {
         r.run();
      } else {
         SwingUtilities.invokeLater(r);
      }
   }
   
   
   
       
   /* 
    * Attach properly formatted tooltip text to the specified
    * JComponent.
    */
   public static void setToolTipText(JComponent component,
            String toolTipText) {
      if (JavaUtils.isMac()) {// running on a mac
         component.setToolTipText(toolTipText);
      }
      else {
         component.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip(toolTipText));
      }
    }

   /**
    * Add an icon from the "org/micromanager/icons/ folder with
    * given file name, to specified the button or menu.
    */
   public static void setIcon(AbstractButton component, String iconFileName) {
      component.setIcon(IconLoader.getIcon(
              "/org/micromanager/icons/" + iconFileName));
   }
      
   /////////////////////// MENU ITEM UTILITY METHODS ///////////
   
   /**
    * Adds a menu to the specified menu bar.
    * @param menuBar
    * @param menuName
    * @return 
    */
   public static JMenu createMenuInMenuBar(final JMenuBar menuBar, final String menuName) {
      final JMenu menu = new JMenu();
      menu.setText(menuName);
      menuBar.add(menu);
      return menu;
   }
   
    /**
     * Adds a menu item to the specified parent menu.
    * @param parentMenu - "top level" menu
    * @param menuItem - prepared menuitem
    * @param menuItemToolTip 
    * @param menuActionRunnable - code that will be executed on selection of this menu
    * @return 
     */
   public static JMenuItem addMenuItem(final JMenu parentMenu,
            JMenuItem menuItem,
            final String menuItemToolTip,
           final Runnable menuActionRunnable) {
      if (menuItemToolTip != null) {
         setToolTipText(menuItem, menuItemToolTip);
      }
      if (menuActionRunnable != null) {
         menuItem.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent ignoreEvent) {
               menuActionRunnable.run();
            }
         });
      }
      parentMenu.add(menuItem);
      return menuItem;
   }
   
   /**
    * Adds a menu item with given text to the specified parent menu.
    * @param parentMenu - "top level" menu
    * @param menuItemText - name as it appears in the menu
    * @param menuItemToolTip 
    * @param menuActionRunnable - code that will be executed on selection of this menu
    * @return menuitem
    */
   public static JMenuItem addMenuItem(final JMenu parentMenu,
           final String menuItemText,
           final String menuItemToolTip,
           final Runnable menuActionRunnable) {
      return addMenuItem(parentMenu, new JMenuItem(menuItemText),
              menuItemToolTip, menuActionRunnable);      
   }
   
      /**
    * Adds a menu item with given text and icon to the specified parent menu.
    * @param parentMenu - "top level" menu
    * @param menuItemText - name as it appears in the menu
    * @param menuItemToolTip 
    * @param menuActionRunnable - code that will be executed on selection of this menu
    * @param iconFileName
    * @return menuitem
    */
   public static JMenuItem addMenuItem(final JMenu parentMenu,
           final String menuItemText,
           final String menuItemToolTip,
           final Runnable menuActionRunnable,
           final String iconFileName) {
      final JMenuItem menuItem = addMenuItem(parentMenu,
              menuItemText, menuItemToolTip, menuActionRunnable);
      setIcon(menuItem, iconFileName);
      return menuItem;
   }
  
   /*
    * Add a menu item that can be checked or unchecked to the specified
    * parent menu.
    */
   public static JCheckBoxMenuItem addCheckBoxMenuItem(final JMenu parentMenu,
           final String menuItemText,
           final String menuItemToolTip,
           final Runnable menuActionRunnable,
           final boolean initState) {
      final JCheckBoxMenuItem menuItem = (JCheckBoxMenuItem)
              addMenuItem(parentMenu, new JCheckBoxMenuItem(menuItemText),
              menuItemToolTip, menuActionRunnable);
      menuItem.setSelected(initState);
      return menuItem;
   }
   
   ////////////// END MENU ITEM UTILITY METHODS ////////////////
   
    /* Add a component to the parent panel, set positions of the edges of
     * component relative to panel. If edges are positive, then they are
     * positioned relative to north and west edges of parent container. If edges
     * are negative, then they are positioned relative to south and east
     * edges of parent container.
     * Requires that parent container uses SpringLayout.
     */
   public static void addWithEdges(Container parentContainer, JComponent component, int west, int north, int east, int south) {
      parentContainer.add(component);
      SpringLayout topLayout = (SpringLayout) parentContainer.getLayout();
      topLayout.putConstraint(SpringLayout.EAST, component, east,
              (east > 0) ? SpringLayout.WEST : SpringLayout.EAST, parentContainer);
      topLayout.putConstraint(SpringLayout.WEST, component, west,
              (west >= 0) ? SpringLayout.WEST : SpringLayout.EAST, parentContainer);
      topLayout.putConstraint(SpringLayout.SOUTH, component, south,
              (south > 0) ? SpringLayout.NORTH : SpringLayout.SOUTH, parentContainer);
      topLayout.putConstraint(SpringLayout.NORTH, component, north,
              (north >= 0) ? SpringLayout.NORTH : SpringLayout.SOUTH, parentContainer);
   }

   /** 
    * Adds a component to the parent panel, set positions of the edges of
    * component relative to panel. If edges are positive, then they are
    * positioned relative to north and west edges of parent container. If edges
    * are negative, then they are positioned relative to south and east
    * edges of parent container.
    * Requires that parent container uses SpringLayout.
    */
   public static AbstractButton createButton(final boolean isToggleButton,
           final String name,
           final String text,
           final String toolTipText,
           final Runnable buttonActionRunnable,
           final String iconFileName,
           final Container parentPanel,
           int west, int north, int east, int south) {
      AbstractButton button = isToggleButton ? new JToggleButton() : new JButton();
      button.setFont(new Font("Arial", Font.PLAIN, 10));
      button.setMargin(new Insets(0, 0, 0, 0));
      button.setName(name);
      if (text != null) {
         button.setText(text);
      }
      if (iconFileName != null) {
         button.setIconTextGap(4);
         setIcon(button, iconFileName);
      }
      if (toolTipText != null) {
         button.setToolTipText(toolTipText);
      }
      button.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            buttonActionRunnable.run();
         }
      });
      GUIUtils.addWithEdges(parentPanel, button, west, north, east, south);
      return button;
   }

   public interface StringValidator {
      public void validate(String string);
   }
   
   // If the user attempts to edit a text field, but doesn't enter a valid input,
   // as specifed by the StringValidator, then a dialog pops up that reminds
   // user what kind of input is needed. If the user presses OK, then verifier
   // returns false. If user presses CANCEL, then verifier reverts the 
   // value and returns true.
   public static InputVerifier textFieldInputVerifier(final JTextField field, final StringValidator validator) {
      return new InputVerifier() {
         private String lastGoodValue = field.getText();

         public boolean verify(JComponent input) {
            String proposedValue = ((JTextField) input).getText();
            validator.validate(proposedValue);
            return true;
         }

         @Override
         public boolean shouldYieldFocus(JComponent input) {
            try {
               boolean isValid = super.shouldYieldFocus(input);
               lastGoodValue = field.getText();
               return isValid;
            } catch (Exception e) {
               int response = JOptionPane.showConfirmDialog(
                     null, e.getMessage(),
                     "Invalid input",
                     JOptionPane.OK_CANCEL_OPTION);
               if (response == JOptionPane.OK_OPTION) {
                  return false;
               } else if (response == JOptionPane.CANCEL_OPTION) {
                  field.setText(lastGoodValue);
                  return true;
               }
            }
            return true;
         }
      };
   }

   public static void enforceValidTextField(final JTextField field, final StringValidator validator) {
      field.setInputVerifier(textFieldInputVerifier(field, validator));
   }

   public static DefaultCellEditor validatingDefaultCellEditor(final StringValidator validator) {
      final String lastValue[] = {""};
      final JTextField field = new JTextField();
      field.getDocument().addDocumentListener(new DocumentListener() {
         @Override
         public void insertUpdate(DocumentEvent e) {}
         @Override
         public void removeUpdate(DocumentEvent e) {}
         @Override
         public void changedUpdate(DocumentEvent e) {
            lastValue[0] = field.getText();         }
      });
      final InputVerifier verifier = textFieldInputVerifier(field, validator);
      DefaultCellEditor editor = new DefaultCellEditor(field) {
         @Override
         public boolean stopCellEditing() {
            return verifier.shouldYieldFocus(field) && super.stopCellEditing();
         }
      };
      return editor;
   }

   public static StringValidator integerStringValidator(final int minValue, final int maxValue) {
      return new StringValidator() {
         @Override
         public void validate(String string) {
            try {
               int value = Integer.parseInt(string.trim());
               if ((value < minValue) || (value > maxValue)) {
                  throw new RuntimeException("Value should be between " + minValue + " and " + maxValue);
               }
            } catch (NumberFormatException e) {
               throw new RuntimeException("Please enter an integer.");
            }
         }
      };
   }
   
   public static StringValidator floatStringValidator(final double minValue, final double maxValue) {
       return new StringValidator() {
         @Override
         public void validate(String string) {
            try {
               double value = Double.parseDouble(string);
               if ((value < minValue) || (value > maxValue)) {
                  throw new RuntimeException("Value should be between " + minValue + " and " + maxValue);
               }
            } catch (NumberFormatException e) {
               throw new RuntimeException("Please enter a number.");
            }
         }
       };
   }
   
   public static void enforceIntegerTextField(final JTextField field, final int minValue, final int maxValue) {
      enforceValidTextField(field, integerStringValidator(minValue, maxValue));
   }
   
   public static void enforceFloatFieldText(final JTextField field, final double minValue, final double maxValue) {
      enforceValidTextField(field, floatStringValidator(minValue, maxValue));
   }
   
   public static void enforceIntegerTextColumn(final JTable table, int columnInt, int minValue, int maxValue) {
      table.getColumnModel().getColumn(columnInt)
           .setCellEditor(validatingDefaultCellEditor(integerStringValidator(minValue, maxValue)));
   }
   
   
   public static String getStringValue(JComponent component) {
      if (component instanceof JTextComponent) {
         return ((JTextComponent) component).getText();
      }
      if (component instanceof JComboBox) {
         return ((JComboBox) component).getSelectedItem().toString();
      }
      if (component instanceof JList) {
         return ((JList) component).getSelectedValue().toString();
      }
      return null;
   }
   
   public static void setValue(JComponent component, String value) {
      if (component instanceof JTextComponent) {
         ((JTextComponent) component).setText(value);
      }
      if (component instanceof JComboBox) {
         ((JComboBox) component).setSelectedItem(value);
      }
      if (component instanceof JList) {
         ((JList) component).setSelectedValue(value, true);
      }
   }
   
   public static void setValue(JComponent component, Object value) {
      String valueText = value.toString();
      if (component instanceof JTextComponent) {
         ((JTextComponent) component).setText(valueText);
      }
      if (component instanceof JComboBox) {
         ((JComboBox) component).setSelectedItem(valueText);
      }
      if (component instanceof JList) {
         ((JList) component).setSelectedValue(valueText, true);
      }
   }
   
   public static int getIntValue(JTextField component) {
     return Integer.parseInt(component.getText());
   }
   
   private static void enableOnTableEvent(final JTable table, final JComponent component) {
      table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
         @Override
         public void valueChanged(ListSelectionEvent e) {
            if (!e.getValueIsAdjusting()) {
               if (component.getParent().isEnabled()) {
                  if (table.getSelectedRowCount() > 0) {
                     component.setEnabled(true);
                  } else {
                     component.setEnabled(false);
                  }
               }
            }
         }
      });
   }
   
   public static void makeIntoMoveRowUpButton(final JTable table, JButton button) {
      button.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            int rowIndex = table.getSelectedRow();
            if (rowIndex > 0) {
               ((DefaultTableModel) table.getModel()).moveRow(rowIndex, rowIndex, rowIndex - 1);
               table.setRowSelectionInterval(rowIndex - 1, rowIndex - 1);
            }
         }
      });
      enableOnTableEvent(table, button);
   }
      
   public static void makeIntoMoveRowDownButton(final JTable table, JButton button) {
      button.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            int rowIndex = table.getSelectedRow();
            if (rowIndex < table.getRowCount() - 1) {
               ((DefaultTableModel) table.getModel()).moveRow(rowIndex, rowIndex, rowIndex + 1);
               table.setRowSelectionInterval(rowIndex + 1, rowIndex + 1);
            }
         }
      });
      enableOnTableEvent(table, button);
   }
   
   public static void makeIntoDeleteRowButton(final JTable table, JButton button) {
      button.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            int rowIndex = table.getSelectedRow();
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            model.removeRow(rowIndex);
            if (rowIndex < table.getRowCount()) {
               table.setRowSelectionInterval(rowIndex, rowIndex);
            }
         }
      });
      enableOnTableEvent(table, button);
   }
   
   @SuppressWarnings("unchecked")
   public static void makeIntoCloneRowButton(final JTable table, JButton button) {
      button.addActionListener(new ActionListener() {
         @Override
         public void actionPerformed(ActionEvent e) {
            int rowIndex = table.getSelectedRow();
            DefaultTableModel model = (DefaultTableModel) table.getModel();
            Vector rowData = (Vector) model.getDataVector().elementAt(rowIndex);       
            model.insertRow(rowIndex + 1, new Vector(rowData));
            table.setRowSelectionInterval(rowIndex + 1, rowIndex + 1);
         }
      });
      enableOnTableEvent(table, button);
   }
  
   public static void startEditingAtCell(JTable table, int row, int column) {
      table.editCellAt(row, column);
      table.getEditorComponent().requestFocusInWindow();
   }

   public static void tabKeyTraversesTable(final JTable table) {
      table.addKeyListener(new KeyAdapter() {
         @Override
         public void keyReleased(KeyEvent event) {
            // Check if the TAB key was pressed.
            if (event.getKeyCode() == 9) {
               int row = table.getEditingRow();
               int column = table.getEditingColumn();
               // Don't proceed if we aren't already editing.
               if (row < 0 || column < 0) return;
               if (column == (table.getColumnCount() - 1)) {
                  column = 0;
                  row = row + 1 % table.getRowCount();
               } else {
                  ++column;
               }
               startEditingAtCell(table, row, column);
            }
         }
      });
   }

   public static Runnable makeURLRunnable(final String url) {
       return new Runnable() {
          @Override
          public void run() {
             try {
                ij.plugin.BrowserLauncher.openURL(url);
             } catch (IOException e1) {
                ReportingUtils.showError(e1);
             }
          }
       };
   }
}
