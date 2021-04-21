///////////////////////////////////////////////////////////////////////////////
// PROJECT:       Micro-Manager
// SUBSYSTEM:     mmstudio
// -----------------------------------------------------------------------------
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
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsConfiguration;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Insets;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowEvent;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Vector;
import javax.swing.BorderFactory;
import javax.swing.DefaultCellEditor;
import javax.swing.InputVerifier;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JSpinner;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.DefaultTableModel;
import javax.swing.table.TableCellEditor;

public final class GUIUtils {
  private static final String DIALOG_POSITION = "dialogPosition";
  public static final Font buttonFont = new Font("Arial", Font.PLAIN, 10);

  public static void setComboSelection(JComboBox cb, String sel) {
    ActionListener[] listeners = cb.getActionListeners();
    for (ActionListener listener : listeners) {
      cb.removeActionListener(listener);
    }
    cb.setSelectedItem(sel);
    for (ActionListener listener : listeners) {
      cb.addActionListener(listener);
    }
  }

  public static void replaceComboContents(JComboBox cb, String[] items) {

    // remove listeners
    ActionListener[] listeners = cb.getActionListeners();
    for (ActionListener listener : listeners) {
      cb.removeActionListener(listener);
    }

    if (cb.getItemCount() > 0) cb.removeAllItems();

    // add contents
    for (String item : items) {
      cb.addItem(item);
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
        // Dynamically load sun.awt.Win32GraphicsEnvironment, because it seems to be missing from
        // the Mac OS X JVM.
        ClassLoader cl = ClassLoader.getSystemClassLoader();
        Class<?> envClass = cl.loadClass("sun.awt.Win32GraphicsEnvironment");
        // Get the current local graphics environment.
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        // Send notification that display may have changed, so that display count is updated.
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
   * Verifies if the given point is visible on the screen. From
   * http://www.java2s.com/Code/Java/Swing-JFC/Verifiesifthegivenpointisvisibleonthescreen.htm
   *
   * @param location The given location on the screen.
   * @return True if the location is on the screen, false otherwise.
   */
  public static boolean isLocationInScreenBounds(Point location) {
    GraphicsConfiguration config = getGraphicsConfigurationContaining(location.x, location.y);
    return (config != null);
  }

  /**
   * Get the usable screen area (minus taskbars, etc.) for the specified monitor. Adapted from
   * http://stackoverflow.com/questions/10123735/get-effective-screen-size-from-java
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

  public static GraphicsConfiguration getGraphicsConfigurationContaining(int x, int y) {
    for (GraphicsConfiguration config : getConfigs()) {
      Rectangle bounds = config.getBounds();
      if (bounds.contains(x, y)) {
        return config;
      }
    }
    return null;
  }

  /**
   * If a window's top-left corner is off the screen, then getGraphicsConfigurationContaining will
   * return null. This method in contrast will find the GraphicsConfiguration that shows the
   * greatest portion of the provided rectangle; thus it should always return a
   * GraphicsConfiguration so long as any part of the rect is visible.
   */
  public static GraphicsConfiguration getGraphicsConfigurationBestMatching(Rectangle rect) {
    GraphicsConfiguration best = null;
    double bestArea = -1;
    for (GraphicsConfiguration config : getConfigs()) {
      Rectangle2D intersect = rect.createIntersection(config.getBounds());
      if (intersect != null && intersect.getWidth() * intersect.getHeight() > bestArea) {
        bestArea = intersect.getWidth() * intersect.getHeight();
        best = config;
      }
    }
    return best;
  }

  /** Convenience method to iterate over all graphics configurations. */
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
    AWTEventListener awtEventListener =
        new AWTEventListener() {
          private ImageWindow currentImageWindow_ = null;

          @Override
          public void eventDispatched(AWTEvent event) {
            if (event instanceof WindowEvent) {
              if (0 != (event.getID() & WindowEvent.WINDOW_GAINED_FOCUS)) {
                if (event.getSource() instanceof ImageWindow) {
                  ImageWindow focusedWindow = WindowManager.getCurrentWindow();
                  if (currentImageWindow_ != focusedWindow) {
                    // if (focusedWindow.isVisible() && focusedWindow instanceof ImageWindow) {
                    listener.focusReceived(focusedWindow);
                    currentImageWindow_ = focusedWindow;
                    // }
                  }
                }
              }
            }
          }
        };

    Toolkit.getDefaultToolkit()
        .addAWTEventListener(awtEventListener, AWTEvent.WINDOW_FOCUS_EVENT_MASK);
  }

  /*
   * Wraps SwingUtilities.invokeAndWait so that if it is being called
   * from the EDT, then the runnable is simply run.
   */
  public static void invokeAndWait(Runnable r)
      throws InterruptedException, InvocationTargetException {
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
  public static void invokeLater(Runnable r) {
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
  public static void setToolTipText(JComponent component, String toolTipText) {
    if (JavaUtils.isMac()) { // running on a mac
      component.setToolTipText(toolTipText);
    } else {
      component.setToolTipText(TooltipTextMaker.addHTMLBreaksForTooltip(toolTipText));
    }
  }

  /////////////////////// MENU ITEM UTILITY METHODS ///////////

  /**
   * Adds a menu to the specified menu bar.
   *
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
   *
   * @param parentMenu - "top level" menu
   * @param menuItem - prepared menuitem
   * @param menuItemToolTip
   * @param menuActionRunnable - code that will be executed on selection of this menu
   * @return
   */
  public static JMenuItem addMenuItem(
      final JMenu parentMenu,
      JMenuItem menuItem,
      final String menuItemToolTip,
      final Runnable menuActionRunnable) {
    if (menuItemToolTip != null) {
      setToolTipText(menuItem, menuItemToolTip);
    }
    if (menuActionRunnable != null) {
      menuItem.addActionListener(
          new ActionListener() {
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
   *
   * @param parentMenu - "top level" menu
   * @param menuItemText - name as it appears in the menu
   * @param menuItemToolTip
   * @param menuActionRunnable - code that will be executed on selection of this menu
   * @return menuitem
   */
  public static JMenuItem addMenuItem(
      final JMenu parentMenu,
      final String menuItemText,
      final String menuItemToolTip,
      final Runnable menuActionRunnable) {
    return addMenuItem(
        parentMenu, new JMenuItem(menuItemText), menuItemToolTip, menuActionRunnable);
  }

  /**
   * Adds a menu item with given text and icon to the specified parent menu.
   *
   * @param parentMenu - "top level" menu
   * @param menuItemText - name as it appears in the menu
   * @param menuItemToolTip
   * @param menuActionRunnable - code that will be executed on selection of this menu
   * @param iconFileName
   * @return menuitem
   */
  public static JMenuItem addMenuItem(
      final JMenu parentMenu,
      final String menuItemText,
      final String menuItemToolTip,
      final Runnable menuActionRunnable,
      final String iconFileName) {
    final JMenuItem menuItem =
        addMenuItem(parentMenu, menuItemText, menuItemToolTip, menuActionRunnable);
    menuItem.setIcon(IconLoader.getIcon("/org/micromanager/icons/" + iconFileName));
    return menuItem;
  }

  /*
   * Add a menu item that can be checked or unchecked to the specified
   * parent menu.
   */
  public static JCheckBoxMenuItem addCheckBoxMenuItem(
      final JMenu parentMenu,
      final String menuItemText,
      final String menuItemToolTip,
      final Runnable menuActionRunnable,
      final boolean initState) {
    final JCheckBoxMenuItem menuItem =
        (JCheckBoxMenuItem)
            addMenuItem(
                parentMenu,
                new JCheckBoxMenuItem(menuItemText),
                menuItemToolTip,
                menuActionRunnable);
    menuItem.setSelected(initState);
    return menuItem;
  }

  ////////////// END MENU ITEM UTILITY METHODS ////////////////

  public interface StringValidator {
    public void validate(String string);
  }

  // If the user attempts to edit a text field, but doesn't enter a valid input,
  // as specifed by the StringValidator, then a dialog pops up that reminds
  // user what kind of input is needed. If the user presses OK, then verifier
  // returns false. If user presses CANCEL, then verifier reverts the
  // value and returns true.
  public static InputVerifier textFieldInputVerifier(
      final JTextField field, final StringValidator validator) {
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
          int response =
              JOptionPane.showConfirmDialog(
                  null, e.getMessage(), "Invalid input", JOptionPane.OK_CANCEL_OPTION);
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

  public static void enforceValidTextField(
      final JTextField field, final StringValidator validator) {
    field.setInputVerifier(textFieldInputVerifier(field, validator));
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

  // TODO: this is only used by the Projector plugin and should probably
  // be moved into it.
  public static void enforceIntegerTextField(
      final JTextField field, final int minValue, final int maxValue) {
    enforceValidTextField(field, integerStringValidator(minValue, maxValue));
  }

  // TODO: this is only used by the Projector plugin and should probably
  // be moved into it.
  public static int getIntValue(JTextField component) {
    return Integer.parseInt(component.getText());
  }

  private static void enableOnTableEvent(final JTable table, final JComponent component) {
    table
        .getSelectionModel()
        .addListSelectionListener(
            new ListSelectionListener() {
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
    button.addActionListener(
        new ActionListener() {
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
    button.addActionListener(
        new ActionListener() {
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
    button.addActionListener(
        new ActionListener() {
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
    button.addActionListener(
        new ActionListener() {
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
    table.addKeyListener(
        new KeyAdapter() {
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

  /**
   * Center the provided Window within the screen contained by the provided other window.
   *
   * @param child Window to be centered on the relevant screen
   * @param source parent Window. Used to figure out the screen we should be on
   */
  public static void centerFrameWithFrame(Window child, Window source) {
    GraphicsConfiguration config =
        getGraphicsConfigurationContaining(source.getLocation().x, source.getLocation().y);
    // if the top left corner is not on a screen, config will be null
    if (config == null) {
      config =
          getGraphicsConfigurationContaining(
              source.getLocation().x + source.getWidth(),
              source.getLocation().y + source.getHeight());
    }
    // if we still have no GraphicConfiguration, just grab the first
    if (config == null) {
      config = getConfigs().get(0);
    }

    Dimension size = child.getSize();
    Rectangle bounds = config.getBounds();
    child.setLocation(
        bounds.x + bounds.width / 2 - size.width / 2,
        bounds.y + bounds.height / 2 - size.height / 2);
  }

  /** Create an undecorated window centered on the specified parent with the specified text. */
  public static JDialog createBareMessageDialog(Window parent, String message) {
    JDialog result = new JDialog();
    result.setUndecorated(true);
    JPanel contents = new JPanel();
    contents.add(new JLabel(message));
    contents.setBorder(BorderFactory.createLineBorder(Color.BLACK, 2));
    result.add(contents);
    result.pack();
    centerFrameWithFrame(result, parent);
    return result;
  }
}
