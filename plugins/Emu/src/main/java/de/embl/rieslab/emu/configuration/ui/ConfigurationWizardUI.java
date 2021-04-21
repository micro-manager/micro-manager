package de.embl.rieslab.emu.configuration.ui;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeMap;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import de.embl.rieslab.emu.configuration.ConfigurationController;
import de.embl.rieslab.emu.configuration.data.GlobalConfiguration;
import de.embl.rieslab.emu.configuration.ui.tables.ParametersTable;
import de.embl.rieslab.emu.configuration.ui.tables.PropertiesTable;
import de.embl.rieslab.emu.configuration.ui.tables.SettingsTable;
import de.embl.rieslab.emu.controller.utils.SystemDialogs;
import de.embl.rieslab.emu.micromanager.mmproperties.MMPropertiesRegistry;
import de.embl.rieslab.emu.ui.ConfigurableFrame;

/**
 * UI used to configure the system by allocating UI properties to existing device properties in
 * Micro-manager, the values of their state if applicable and the values of the UI parameters.
 *
 * @author Joran Deschamps
 */
public class ConfigurationWizardUI {

  /** Value given to unallocated UIProperty states and UIParameters values. */
  public static final String KEY_ENTERVALUE = "Enter value";

  public static final String KEY_UIPROPERTY = "UI Property: ";
  public static final String KEY_UIPARAMETER = "UI Parameter: ";

  private PropertiesTable propertytable_; // panel used by the user to pair ui- and mmproperties
  private ParametersTable
      parametertable_; // panel used by the user to set the values of uiparameters
  private SettingsTable globsettingstable_;
  private SettingsTable
      plugsettingstable_; // panel used by the user to set the values of plugin settings
  private HelpWindow
      help_; // help window to display the description of uiproperties and uiparameters
  private ConfigurationController config_; // configuration class
  private JFrame frame_; // overall frame for the configuration wizard
  private boolean running_ = false;
  private boolean promptedNew_ = false;
  private boolean updating_ = false; // prevent infinite loop while updating a tab
  private String plugin_name_;
  private JTextField config_name_;
  private MMPropertiesRegistry mmproperties_;

  public ConfigurationWizardUI(ConfigurationController config) {
    config_ = config;
    plugin_name_ = "";
  }

  /**
   * Starts a new configuration wizard. If {@code configuration} is not compatible with {@code
   * pluginName} then the wizard starts a new configuration, otherwise the wizard will allow editing
   * {@code configuration}.
   *
   * @param pluginName Name of the plugin to be configured.
   * @param configuration Current configuration loaded in EMU.
   * @param maininterface ConfigurableFrame of the plugin.
   * @param mmproperties Device properties from Micro-Manager.
   */
  public void start(
      String pluginName,
      GlobalConfiguration configuration,
      ConfigurableFrame maininterface,
      MMPropertiesRegistry mmproperties) {
    mmproperties_ = mmproperties;
    plugin_name_ = pluginName;
    if (configuration.getCurrentPluginName() != null
        && configuration.getCurrentPluginName().equals(pluginName)) {
      existingConfiguration(maininterface, mmproperties, configuration);
    } else {
      newConfiguration(pluginName, maininterface, mmproperties);
    }
  }

  // creates a new configuration
  private void newConfiguration(
      final String plugin_name,
      final ConfigurableFrame maininterface,
      final MMPropertiesRegistry mmproperties) {
    // makes sure it runs on EDT
    javax.swing.SwingUtilities.invokeLater(
        new Runnable() {
          public void run() {
            running_ = true;

            help_ = new HelpWindow("Click on a row to display the description");

            // Table defining the properties
            propertytable_ =
                new PropertiesTable(maininterface.getUIProperties(), mmproperties, help_);
            propertytable_.setOpaque(true);

            // and parameters
            parametertable_ =
                new ParametersTable(
                    maininterface.getUIParameters(), maininterface.getUIProperties(), help_);
            parametertable_.setOpaque(true);

            // and plugin settings
            plugsettingstable_ = new SettingsTable(maininterface.getDefaultPluginSettings(), help_);
            plugsettingstable_.setOpaque(true);

            // and global settings
            globsettingstable_ = new SettingsTable(config_.getGlobalSettings(), help_);
            globsettingstable_.setOpaque(true);

            frame_ =
                createFrame(
                    plugin_name,
                    propertytable_,
                    parametertable_,
                    plugsettingstable_,
                    globsettingstable_,
                    help_);
          }
        });
  }

  // Creates a Wizard configuration frame from an existing configuration.
  private void existingConfiguration(
      final ConfigurableFrame maininterface,
      final MMPropertiesRegistry mmproperties,
      final GlobalConfiguration configuration) {

    javax.swing.SwingUtilities.invokeLater(
        new Runnable() {
          public void run() {
            running_ = true;

            help_ = new HelpWindow("Click on a row to display the description");

            // Table defining the properties using the configuration
            propertytable_ =
                new PropertiesTable(
                    maininterface.getUIProperties(),
                    mmproperties,
                    configuration.getCurrentPluginConfiguration().getProperties(),
                    help_);
            propertytable_.setOpaque(true);

            // now parameters
            parametertable_ =
                new ParametersTable(
                    maininterface.getUIParameters(),
                    configuration.getCurrentPluginConfiguration().getParameters(),
                    maininterface.getUIProperties(),
                    help_);
            parametertable_.setOpaque(true);

            // now plugin settings
            plugsettingstable_ =
                new SettingsTable(
                    maininterface.getDefaultPluginSettings(),
                    configuration.getCurrentPluginConfiguration().getPluginSettings(),
                    help_);
            plugsettingstable_.setOpaque(true);

            // and global settings from the system controller
            globsettingstable_ = new SettingsTable(config_.getGlobalSettings(), help_);
            globsettingstable_.setOpaque(true);

            frame_ =
                createFrame(
                    configuration.getCurrentConfigurationName(),
                    propertytable_,
                    parametertable_,
                    plugsettingstable_,
                    globsettingstable_,
                    help_);
          }
        });
  }

  // Sets up the frame used for the interactive configuration.
  private JFrame createFrame(
      String conf_name,
      final PropertiesTable propertytable,
      final ParametersTable parametertable,
      final SettingsTable plugsettingstable,
      final SettingsTable globsettingstable,
      final HelpWindow help) {
    JFrame frame = new JFrame("Configuration wizard");
    frame.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            help.disposeHelp();
            running_ = false;
            e.getWindow().dispose();
          }
        });

    // sets icon
    ArrayList<BufferedImage> lst = new ArrayList<BufferedImage>();
    BufferedImage im;
    try {
      im = ImageIO.read(getClass().getResource("/images/gear16.png"));
      lst.add(im);
    } catch (IOException e1) {
      e1.printStackTrace();
    }
    try {
      im = ImageIO.read(getClass().getResource("/images/gear32.png"));
      lst.add(im);
    } catch (IOException e1) {
      e1.printStackTrace();
    }
    frame.setIconImages(lst);

    promptedNew_ = false;

    // Tab containing the tables
    JTabbedPane tabbedpane = new JTabbedPane();
    tabbedpane.addTab("Plugin Settings", null, plugsettingstable, "Set the plugin settings.");
    tabbedpane.addTab(
        "Properties", null, propertytable, "Map Micro-manager device properties to UI properties.");
    tabbedpane.addTab("Parameters", null, parametertable, "Set parameters values.");
    tabbedpane.addTab("Global Settings", null, globsettingstable, "EMU global settings");
    tabbedpane.addChangeListener(
        new ChangeListener() {
          public void stateChanged(ChangeEvent e) {
            updateTabs(
                tabbedpane,
                tabbedpane
                    .getSelectedIndex()); // updates property and parameter tab when the settings
            // have changed
          }
        });

    // content pane
    JPanel contentpane = new JPanel();

    // gridbag layout for upper and lower panel
    JPanel upperpane = new JPanel();
    upperpane.setLayout(new GridLayout(0, 4));

    JLabel conf_name_label = new JLabel("   Configuration name:");
    config_name_ = new JTextField(conf_name);
    config_name_
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              public void changedUpdate(DocumentEvent e) {
                warn();
              }

              public void removeUpdate(DocumentEvent e) {
                warn();
              }

              public void insertUpdate(DocumentEvent e) {
                warn();
              }

              public void warn() {
                if (!promptedNew_) {
                  SystemDialogs.showWillCreateNewconfiguration();
                  promptedNew_ = true;
                }
              }
            });

    JToggleButton helptoggle = new JToggleButton("HELP");
    helptoggle.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            JToggleButton toggle = (JToggleButton) e.getSource();
            boolean selected = toggle.getModel().isSelected();
            showHelp(selected);
          }
        });

    JPanel helppane = new JPanel();
    helppane.add(helptoggle);

    upperpane.add(conf_name_label);
    upperpane.add(config_name_);
    upperpane.add(new JLabel(""));
    upperpane.add(helppane);

    /////////// lower panel
    JPanel lowerpane = new JPanel();
    lowerpane.setLayout(new GridLayout(0, 3));

    JButton save = new JButton("Save");
    save.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            saveConfiguration();
          }
        });

    lowerpane.add(save);
    lowerpane.add(new JLabel(""));
    lowerpane.add(new JLabel(""));

    contentpane.add(upperpane);
    contentpane.add(tabbedpane);
    contentpane.add(lowerpane);
    contentpane.setLayout(new BoxLayout(contentpane, BoxLayout.PAGE_AXIS));

    frame.setContentPane(contentpane);

    // Display the window.
    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);

    return frame;
  }

  /*
   * Updates property (index 1) and parameter (index 2) tabs if the settings have changed.
   */
  private void updateTabs(JTabbedPane tabbedpane, int selectedIndex) {
    // if not currently updating
    if (!updating_) {
      /*
       * if an updated is needed (settings have changed) and selected tab is
       * properties or parameters.
       */
      if (plugsettingstable_.hasChanged() && (selectedIndex == 1 || selectedIndex == 2)) {
        updating_ = true; // signals updating

        // extracts settings
        TreeMap<String, String> plugset =
            new TreeMap<String, String>(plugsettingstable_.getSettings());

        // recovers ConfigurableFrame: the settings have been used to generate the property and
        // parameter lists
        ConfigurableFrame mf = config_.getConfigurableFrame(plugset);

        // extracts properties from the table and updates it with the current ConfigurableFrame
        // property list
        HashMap<String, String> prop = propertytable_.getSettings();
        propertytable_ = new PropertiesTable(mf.getUIProperties(), mmproperties_, prop, help_);
        propertytable_.setOpaque(true);
        propertytable_.setName("Properties");

        // same for the parameters
        HashMap<String, String> param = parametertable_.getSettings();
        parametertable_ =
            new ParametersTable(mf.getUIParameters(), param, mf.getUIProperties(), help_);
        parametertable_.setOpaque(true);
        parametertable_.setName("Parameters");

        // substitutes the tabs in the JTabbedPane
        tabbedpane.remove(1);
        tabbedpane.add(propertytable_, 1);
        tabbedpane.remove(2);
        tabbedpane.add(parametertable_, 2);

        // sets the selected tab to the one the user clicked on
        tabbedpane.setSelectedIndex(selectedIndex);

        plugsettingstable_.registerChange(); // updating no longer needed
        updating_ = false; // stops updating
      }
    }
  }

  /**
   * Checks if the wizard is already running by returning an internal member variable.
   *
   * @return True if running, false otherwise.
   */
  public boolean isRunning() {
    return running_;
  }

  /**
   * Sets the help window visible and displays the description of the currently selected UIProperty
   * or UIParameter.
   *
   * @param b True if the help window is to be displayed, false otherwise
   */
  public void showHelp(boolean b) {
    if (help_ != null) {
      help_.showHelp(b);
    }
  }

  /** Closes open windows (wizard frame and help) */
  public void shutDown() {
    if (help_ != null) {
      help_.disposeHelp();
    }
    if (frame_ != null) {
      frame_.dispose();
    }
    running_ = false;
  }

  //  Retrieves the UIProperty and UIParameter name/value pairs from the tables, updates the
  // Configuration and closes
  //  all windows.
  private void saveConfiguration() {
    HashMap<String, String> prop = propertytable_.getSettings();
    HashMap<String, String> param = parametertable_.getSettings();
    HashMap<String, String> plugset = plugsettingstable_.getSettings();
    HashMap<String, String> globset = globsettingstable_.getSettings();

    config_.applyWizardSettings(
        config_name_.getText(), plugin_name_, prop, param, plugset, globset);

    frame_.dispose();
    help_.disposeHelp();
    running_ = false;
  }
}
