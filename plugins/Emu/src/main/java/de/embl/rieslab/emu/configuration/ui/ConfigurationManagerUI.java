package de.embl.rieslab.emu.configuration.ui;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.border.Border;
import javax.swing.border.CompoundBorder;
import javax.swing.border.EmptyBorder;

import de.embl.rieslab.emu.configuration.ConfigurationController;
import de.embl.rieslab.emu.configuration.data.GlobalConfiguration;
import de.embl.rieslab.emu.configuration.data.PluginConfigurationID;
import de.embl.rieslab.emu.configuration.ui.tables.ConfigurationTable;

public class ConfigurationManagerUI {

  private ConfigurationController config_; // configuration class
  private ConfigurationTable configtable_;
  private JFrame frame_;
  private boolean running_;

  public ConfigurationManagerUI(ConfigurationController configurationController) {
    config_ = configurationController;
    running_ = false;
  }

  public void start(final GlobalConfiguration configuration_) {
    javax.swing.SwingUtilities.invokeLater(
        new Runnable() {
          public void run() {
            running_ = true;

            // Table defining the properties using the configuration
            configtable_ = new ConfigurationTable(configuration_);
            configtable_.setOpaque(true);

            frame_ = createFrame(configtable_);
          }
        });
  }

  private JFrame createFrame(ConfigurationTable configtable) {
    JFrame frame = new JFrame("Configuration manager");
    frame.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            running_ = false;
            e.getWindow().dispose();
          }
        });

    // Tab containing the tables
    JPanel tablepanel = new JPanel();
    tablepanel.add(configtable);

    // content pane
    JPanel contentpane = new JPanel();
    contentpane.setLayout(new GridLayout(3, 1));

    // gridbag layout for upper and lower panel
    JPanel upperpane = new JPanel();
    upperpane.setLayout(new GridLayout(0, 2));

    JLabel infolabel = new JLabel("Select a configuration to delete.");
    Border border = infolabel.getBorder();
    Border margin = new EmptyBorder(0, 5, 0, 5);
    infolabel.setBorder(new CompoundBorder(border, margin));

    JButton deletebutton = new JButton("Delete");
    deletebutton.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            configtable_.deleteSelectedRow();
          }
        });
    JPanel delpane = new JPanel();
    delpane.add(deletebutton);

    upperpane.add(infolabel);
    upperpane.add(delpane);

    JPanel lowerpane = new JPanel();
    lowerpane.setLayout(new GridLayout(0, 3));

    JButton save = new JButton("Save");
    save.addActionListener(
        new ActionListener() {
          public void actionPerformed(ActionEvent e) {
            saveConfiguration();
          }
        });

    JPanel savepane = new JPanel();
    savepane.add(save);

    lowerpane.add(savepane);

    contentpane.add(upperpane);
    contentpane.add(tablepanel);
    contentpane.add(lowerpane);
    contentpane.setLayout(new BoxLayout(contentpane, BoxLayout.PAGE_AXIS));

    frame.setContentPane(contentpane);

    // Sets the icons
    ArrayList<BufferedImage> lst = new ArrayList<BufferedImage>();
    BufferedImage im;
    try {
      im = ImageIO.read(getClass().getResource("/images/manage16.png"));
      lst.add(im);
    } catch (IOException e1) {
      e1.printStackTrace();
    }
    try {
      im = ImageIO.read(getClass().getResource("/images/manage32.png"));
      lst.add(im);
    } catch (IOException e1) {
      e1.printStackTrace();
    }
    frame.setIconImages(lst);

    // Displays the window.
    frame.pack();
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);

    return frame;
  }

  /** Closes open windows (wizard frame and help) */
  public void shutDown() {
    if (frame_ != null) {
      frame_.dispose();
    }
    running_ = false;
  }

  public boolean isRunning() {
    return running_;
  }

  private void saveConfiguration() {
    ArrayList<PluginConfigurationID> conf = configtable_.getConfigurations();

    config_.applyManagerConfigurations(conf);

    frame_.dispose();
    running_ = false;
  }
}
