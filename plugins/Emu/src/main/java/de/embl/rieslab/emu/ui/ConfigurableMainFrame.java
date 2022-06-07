package de.embl.rieslab.emu.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.TreeMap;

import javax.imageio.ImageIO;
import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.TitledBorder;

import de.embl.rieslab.emu.controller.SystemController;
import de.embl.rieslab.emu.controller.utils.SystemDialogs;
import de.embl.rieslab.emu.ui.internalproperties.InternalProperty;
import de.embl.rieslab.emu.ui.uiparameters.UIParameter;
import de.embl.rieslab.emu.ui.uiproperties.UIProperty;
import de.embl.rieslab.emu.utils.settings.Setting;
import mmcorej.CMMCore;

/**
 * Class representing the main JFrame of a {@link de.embl.rieslab.emu.plugin.UIPlugin}. Subclasses must
 * implement the {@link #initComponents()} method, in which the {@link ConfigurablePanel}s must be instantiated
 * and added the same way a JPanel is added to a JFrame.
 * <p>
 * The ConfigurableMainFrame aggregates the UIParameters and the UIProperties, as well as linking together the
 * InternalProperties. If two UIProperties have the same name, then the last added UIproperty will replace the
 * first ones. The order is the order of discovery while going through the components of the JFrame.
 * <p>
 * For UIParameters, on the other hand, two UIParameters are allowed to have the same hash ({ConfigurablePanel name}-{UIParameter name})
 * only if they have the same type. Should such case arise, all UIParameters but the first one to appear (in order
 * of registration of the ConfigurablePanel that owns it) are replaced in their owner ConfigurablePanel by the
 * first UIParameter. There, UIParameters with same hash and type are made replaced by a single reference and are
 * shared by all the corresponding ConfigurationPanel. Note that if two UIParameters have same name but different
 * types, the second one to appear is ignored altogether.
 * <p>
 * The same idea applies to InternalProperties.
 *
 * @author Joran Deschamps
 */
public abstract class ConfigurableMainFrame extends JFrame implements ConfigurableFrame {


    private static final long serialVersionUID = 1L;
    private ArrayList<ConfigurablePanel> panels_;
    private SystemController controller_;
    private JMenu switch_plugin, switch_configuration;
    private JMenuItem plugin_description;
    private HashMap<String, UIProperty> properties_;
    @SuppressWarnings("rawtypes")
    private HashMap<String, UIParameter> parameters_;
    @SuppressWarnings("rawtypes")
    private HashMap<String, Setting> pluginSettings_;
    private JFrame descriptionFrame_;


    /**
     * Constructor, it sets up the JMenu, calls {@link #initComponents()} from the subclass, then links InternaProperties and
     * gather UIPropertiers and UIParameters. The plugin settings will override the default settings.
     *
     * @param title          Title of the frame
     * @param controller     EMU system controller
     * @param pluginSettings Plugin settings.
     */
    @SuppressWarnings("rawtypes")
    public ConfigurableMainFrame(String title, SystemController controller, TreeMap<String, String> pluginSettings) {

        controller_ = controller;

        pluginSettings_ = getDefaultPluginSettings();
        if (pluginSettings_ == null) {
            pluginSettings_ = new HashMap<String, Setting>();
        }

        if (pluginSettings != null) {
            // updates the default plugin settings with the given ones
            Iterator<String> it = pluginSettings.keySet().iterator();
            ArrayList<String> wrongvals = new ArrayList<String>();
            while (it.hasNext()) {
                String s = it.next();

                // if the setting has the expected type, then replace in the current settings
                if (pluginSettings_.containsKey(s)) {
                    if (pluginSettings_.get(s).isValueCompatible(pluginSettings.get(s))) {
                        pluginSettings_.get(s).setStringValue(pluginSettings.get(s));
                    } else {
                        wrongvals.add(s);
                    }
                }
            }
            if (wrongvals.size() > 0) {
                SystemDialogs.showWrongPluginSettings(wrongvals);
            }
        }

        // sets title if not null
        if (title != null) {
            this.setTitle(title);
        }

        this.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutDown();
            }
        });

        // sets up the UI
        this.setUpMenu();
        this.initComponents();
        this.setVisible(false);

        // positioning
        GraphicsEnvironment g = GraphicsEnvironment.getLocalGraphicsEnvironment();
        this.setLocation(g.getCenterPoint().x - this.getWidth() / 2, g.getCenterPoint().y - this.getHeight() / 2);
        //this.setVisible(true);

        // retrieves all properties, parameters and link internal properties
        panels_ = listConfigurablePanels(this.getContentPane().getComponents(), new ArrayList<ConfigurablePanel>());
        linkInternalProperties();
        retrieveUIPropertiesAndParameters();

        // sets icon
        ArrayList<BufferedImage> lst = new ArrayList<BufferedImage>();
        BufferedImage im;
        try {
            im = ImageIO.read(getClass().getResource("/images/logo16.png"));
            lst.add(im);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        try {
            im = ImageIO.read(getClass().getResource("/images/logo32.png"));
            lst.add(im);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        this.setIconImages(lst);
    }

    private ArrayList<ConfigurablePanel> listConfigurablePanels(Component[] c, ArrayList<ConfigurablePanel> list) {
        if (list == null) {
            throw new NullPointerException();
        }

        for (int i = 0; i < c.length; i++) {
            if (c[i] instanceof ConfigurablePanel) {
                list.add((ConfigurablePanel) c[i]);
            } else if (c[i] instanceof Container) {
                listConfigurablePanels(((Container) c[i]).getComponents(), list);
            }
        }

        return list;
    }

    /**
     * Sets up the menu bar.
     */
    private void setUpMenu() {
        JMenuBar mb = new JMenuBar();

        JMenu confMenu = new JMenu("Configuration");
        JMenu pluginMenu = new JMenu("Plugin");
        JMenu aboutMenu = new JMenu("About");

        // to refresh the UI state
        JMenuItem refresh = new JMenuItem(new AbstractAction("Refresh UI") {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent e) {
                updateAllProperties();
            }
        });
        refresh.setToolTipText("Updates all UIProperties to the current value.");
        URL iconURL = getClass().getResource("/images/refresh16.png");
        ImageIcon icon = new ImageIcon(iconURL);
        refresh.setIcon(icon);

        // to start the configuration wizard
        JMenuItem wiz = new JMenuItem(new AbstractAction("Modify configuration") {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent e) {
                boolean b = controller_.launchWizard();
                if (!b) {
                    SystemDialogs.showWizardRunningMessage();
                }
            }
        });
        wiz.setToolTipText("Start the configuration wizard, allowing configurating the UI or creating a new configuration.");
        iconURL = getClass().getResource("/images/gear16.png");
        icon = new ImageIcon(iconURL);
        wiz.setIcon(icon);

        // configuration manager
        JMenuItem manageconfig = new JMenuItem(new AbstractAction("Manage configurations") {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent e) {
                boolean b = controller_.launchManager();
                if (!b) {
                    SystemDialogs.showManagerRunningMessage();
                }
            }
        });
        manageconfig.setToolTipText("Delete the configurations of your choice.");
        iconURL = getClass().getResource("/images/manage16.png");
        icon = new ImageIcon(iconURL);
        manageconfig.setIcon(icon);

        // switch plugin and configuration
        switch_plugin = new JMenu("Switch plugin");
        iconURL = getClass().getResource("/images/switchplugin16.png");
        icon = new ImageIcon(iconURL);
        switch_plugin.setIcon(icon);

        switch_configuration = new JMenu("Switch configuration");
        iconURL = getClass().getResource("/images/switchconf16.png");
        icon = new ImageIcon(iconURL);
        switch_configuration.setIcon(icon);

        // description of the plugin
        plugin_description = new JMenuItem(new AbstractAction("Description") {
            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent e) {
                showPluginDescription();
            }
        });
        iconURL = getClass().getResource("/images/info16.png");
        icon = new ImageIcon(iconURL);
        plugin_description.setIcon(icon);

        // guide and about
        JMenuItem guide = new JMenuItem(new AbstractAction("EMU guide") {

            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent e) {
                String url = "https://jdeschamps.github.io/EMU-guide/userguide.html";
                try {
                    java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        });
        iconURL = getClass().getResource("/images/logo16.png");
        icon = new ImageIcon(iconURL);
        guide.setIcon(icon);

        JMenuItem aboutemu = new JMenuItem(new AbstractAction("About EMU") {

            private static final long serialVersionUID = 1L;

            public void actionPerformed(ActionEvent e) {
                SystemDialogs.showAboutEMU(this);
            }
        });
        iconURL = getClass().getResource("/images/about16.png");
        icon = new ImageIcon(iconURL);
        aboutemu.setIcon(icon);

        // add all to menu
        confMenu.add(wiz);
        confMenu.add(manageconfig);
        confMenu.add(switch_plugin);
        confMenu.add(switch_configuration);

        pluginMenu.add(refresh);
        pluginMenu.add(plugin_description);

        aboutMenu.add(guide);
        aboutMenu.add(aboutemu);

        mb.add(confMenu);
        mb.add(pluginMenu);
        mb.add(aboutMenu);

        this.setJMenuBar(mb);
    }


    @SuppressWarnings("rawtypes")
    private void retrieveUIPropertiesAndParameters() {
        properties_ = new HashMap<String, UIProperty>();
        parameters_ = new HashMap<String, UIParameter>();

        Iterator<ConfigurablePanel> it = this.getConfigurablePanels().iterator();
        ConfigurablePanel pan;

        while (it.hasNext()) { // loops over the PropertyPanel contained in the MainFrame
            pan = it.next();

            // adds all the UIProperties, if there is collision the last one is kept. Thus, when writing
            // a plugin, one needs to be aware of this.
            properties_.putAll(pan.getUIProperties());


            // adds all the UIParameters, in case of collision the first UIParameter has priority
            // and substituted to the second UIParameter in its owner PropertyPanel: "same name" = "same parameter"
            // Because UIParameters do not update their ConfigurablePanel owner by themselves, then by doing so
            // we obtain a shared Parameter.
            HashMap<String, UIParameter> panparam = pan.getUIParameters();
            Iterator<String> paramit = panparam.keySet().iterator();
            ArrayList<String> subst = new ArrayList<String>();
            while (paramit.hasNext()) {
                String param = paramit.next();

                if (!parameters_.containsKey(param)) { // if param doesn't exist already, adds it
                    parameters_.put(param, panparam.get(param));
                } else if (parameters_.get(param).getType().equals(panparam.get(param).getType())) {
                    // if it already exists and the new parameter is of the same type than the one
                    // previously added to the HashMap, then add to array subst
                    subst.add(param);
                }
                // if it is not of the same type, it is then ignored
            }
            // avoid concurrent modification of the hashmap, by substituting the UIParameter in the
            // second PropertyPanel
            for (int i = 0; i < subst.size(); i++) {
                pan.substituteParameter(parameters_.get(subst.get(i)));
            }
        }
    }


    @SuppressWarnings("rawtypes")
    private void linkInternalProperties() {
        HashMap<String, InternalProperty> allinternalprops, panelinternalprops, tempinternalprops;
        allinternalprops = new HashMap<String, InternalProperty>();
        tempinternalprops = new HashMap<String, InternalProperty>();
        Iterator<ConfigurablePanel> panelsIt = panels_.iterator();

        while (panelsIt.hasNext()) { // iterate over panels
            tempinternalprops.clear();
            ConfigurablePanel pane = panelsIt.next();
            panelinternalprops = pane.getInternalProperties();

            Iterator<String> propsit = panelinternalprops.keySet().iterator();
            while (propsit.hasNext()) { // iterate over one panel's internal props
                String internalprop = propsit.next();
                if (allinternalprops.containsKey(internalprop)) { // if the internal property already exists
                    // add to a temporary HashMap, and will take care of them later to avoid a concurrent modifications of panelinternalprops
                    tempinternalprops.put(internalprop, panelinternalprops.get(internalprop));
                } else {
                    allinternalprops.put(internalprop, panelinternalprops.get(internalprop));
                }
            }

            // Now substitute all the internal properties from the temporary HashMap with the internal
            // property already in allinternalprops. So far they have the same name, but could have
            // different type. In the following calls, if the properties have different types, then nothing
            // will happen. In this case, we just ignore it. Doing it here at the end avoids concurrent
            // modification of the ConfigurablePanel hashmap.
            propsit = tempinternalprops.keySet().iterator();
            while (propsit.hasNext()) {
                allinternalprops.get(propsit.next()).registerListener(pane);
            }
        }
    }

    /**
     * Returns the List of {@link ConfigurablePanel}s.
     */
    public ArrayList<ConfigurablePanel> getConfigurablePanels() {
        return panels_;
    }

    /**
     * Updates all properties and parameters from each known ConfigurablePanel by calling
     * {@link ConfigurablePanel#updateAllProperties()} and {@link ConfigurablePanel#updateAllParameters()}
     * on each panel.
     */
    public void updateAllConfigurablePanels() {
        // this is called on the EDT

        Iterator<ConfigurablePanel> it = panels_.iterator();
        ConfigurablePanel pan;
        while (it.hasNext()) {
            pan = it.next();

            pan.updateAllParameters(); // so that parameters values can be used for UIProperties (RescaledUIProperty)
            pan.updateAllProperties();
        }
    }

    /**
     * Updates all properties from each known ConfiguablePanel. The method calls {@link ConfigurablePanel#updateAllProperties()}.
     */
    public void updateAllProperties() {
        Iterator<ConfigurablePanel> it = panels_.iterator();
        ConfigurablePanel pan;
        while (it.hasNext()) {
            pan = it.next();

            pan.updateAllProperties();
        }
    }

    /**
     * Adds all listeners to the JComponents.
     */
    public void addAllListeners() {
        Iterator<ConfigurablePanel> it = panels_.iterator();
        ConfigurablePanel pan;
        while (it.hasNext()) {
            pan = it.next();
            pan.addComponentListeners();
        }
    }

    /**
     * Shuts down all ConfigurablePanels by calling {@link ConfigurablePanel#shutDown()} on
     * each panel.
     */
    public void shutDown() {
        Iterator<ConfigurablePanel> it = panels_.iterator();
        ConfigurablePanel pan;
        while (it.hasNext()) {
            pan = it.next();
            pan.shutDown();
        }
        if (descriptionFrame_ != null) {
            descriptionFrame_.dispose();
        }
        this.dispose();
    }

    /**
     * Provides access the Micro-Manager CMMCore to subclasses.
     *
     * @return Micro-Manager CMMCore.
     */
    protected CMMCore getCore() {
        return controller_.getCore();
    }

    /**
     * Returns the EMU system controller.
     *
     * @return EMU SystemController.
     */
    protected SystemController getController() {
        return controller_;
    }

    /**
     * Updates the JMenu, called when loading a new Plugin or a new Configuration.
     */
    public void updateMenu() {
        switch_plugin.removeAll();
        final String[] plugins = controller_.getOtherPluginsList();
        for (int i = 0; i < plugins.length; i++) {
            final int index = i;
            JMenuItem item = new JMenuItem(new AbstractAction(plugins[index]) {

                private static final long serialVersionUID = 1L;

                public void actionPerformed(ActionEvent e) {
                    controller_.loadPlugin(plugins[index]);
                }
            });
            switch_plugin.add(item);
        }

        switch_configuration.removeAll();
        final String[] confs = controller_.getOtherCompatibleConfigurationsList();
        for (int i = 0; i < confs.length; i++) {
            final int index = i;
            JMenuItem item = new JMenuItem(new AbstractAction(confs[index]) {

                private static final long serialVersionUID = 1L;

                public void actionPerformed(ActionEvent e) {
                    controller_.loadConfiguration(confs[index]);
                }
            });
            switch_configuration.add(item);
        }
    }

    /**
     * Returns the Map of UIProperties indexed by their name.
     */
    @Override
    public HashMap<String, UIProperty> getUIProperties() {
        return properties_;
    }

    /**
     * Returns the Map of UIParameters indexed by their hash.
     */
    @SuppressWarnings("rawtypes")
    @Override
    public HashMap<String, UIParameter> getUIParameters() {
        return parameters_;
    }

    @SuppressWarnings("rawtypes")
    @Override
    public HashMap<String, Setting> getCurrentPluginSettings() {
        return pluginSettings_;
    }

    private void showPluginDescription() {
        if (descriptionFrame_ == null || !descriptionFrame_.isVisible()) {
            descriptionFrame_ = new JFrame("Description");
            JPanel pane = new JPanel();
            pane.setLayout(new BoxLayout(pane, BoxLayout.PAGE_AXIS));

            //////////// plugin description
            JPanel pluginDescription = new JPanel();
            TitledBorder border = BorderFactory.createTitledBorder(null, this.getTitle(), TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, Color.black);
            border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD, 20));
            pluginDescription.setBorder(border);

            JTextArea txtPlugin = new JTextArea(5, 40);
            txtPlugin.setEditable(false);
            txtPlugin.setText(getPluginInfo());
            txtPlugin.setFont(new Font("Serif", Font.PLAIN, 16));
            txtPlugin.setLineWrap(true);
            txtPlugin.setWrapStyleWord(true);
            pluginDescription.add(txtPlugin);

            pane.add(pluginDescription);

            //////////// Panels description
            JPanel panelsDrescription;
            if (panels_.size() > 0) {
                panelsDrescription = new JPanel();
                panelsDrescription.setLayout(new GridBagLayout());

                TitledBorder border2 = BorderFactory.createTitledBorder(null, "Panels", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, Color.black);
                border2.setTitleFont(new Font("Serif", Font.BOLD, 18));
                panelsDrescription.setBorder(border2);

                // gets the configurablepanel subclasses and builds a list of panels for each
                HashMap<String, ArrayList<ConfigurablePanel>> knownClasses = new HashMap<String, ArrayList<ConfigurablePanel>>();
                for (ConfigurablePanel p : panels_) {
                    String paneClass = p.getClass().getSimpleName();

                    if (!knownClasses.containsKey(paneClass)) {
                        ArrayList<ConfigurablePanel> arr = new ArrayList<ConfigurablePanel>();
                        arr.add(p);
                        knownClasses.put(paneClass, arr);
                    } else {
                        ArrayList<ConfigurablePanel> arr = knownClasses.get(paneClass);
                        arr.add(p);
                    }
                }

                // creates the description panels with titled border
                GridBagConstraints c = new GridBagConstraints();
                c.fill = GridBagConstraints.VERTICAL;
                c.gridx = 0;
                int i = 0;
                Iterator<String> it = knownClasses.keySet().iterator();
                while (it.hasNext()) {
                    String s = it.next();

                    // build a string with the panel names
                    String title;
                    if (knownClasses.get(s).size() > 1) {
                        String[] titles = new String[knownClasses.get(s).size()];
                        int j = 0;
                        for (ConfigurablePanel p : knownClasses.get(s)) {
                            titles[j] = p.getPanelLabel();
                            j++;
                        }
                        Arrays.sort(titles);
                        title = String.join(", ", titles);
                    } else {
                        title = knownClasses.get(s).get(0).getPanelLabel();
                    }
                    ConfigurablePanel p = knownClasses.get(s).get(0);

                    c.gridy = i++;

                    JPanel panel = new JPanel();
                    TitledBorder border3 = BorderFactory.createTitledBorder(null, title, TitledBorder.DEFAULT_JUSTIFICATION,
                            TitledBorder.DEFAULT_POSITION, null, Color.black);
                    border3.setTitleFont(new Font("Serif", Font.BOLD, 16));
                    panel.setBorder(border3);
                    if (p.getDescription() != null) {
                        JTextArea txtarea = new JTextArea(1, 40);
                        txtarea.setText(p.getDescription());
                        txtarea.setFont(new Font("Serif", Font.PLAIN, 16));
                        txtarea.setLineWrap(true);
                        txtarea.setWrapStyleWord(true);
                        panel.add(txtarea);
                    } else {
                        panel.add(new JLabel("Description not available."));
                    }
                    panelsDrescription.add(panel, c);
                }

                JScrollPane scrllpane = new JScrollPane(panelsDrescription, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                        JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
                scrllpane.getVerticalScrollBar().setUnitIncrement(16);
                pane.add(scrllpane);

            } else {
                panelsDrescription = new JPanel();
                panelsDrescription.add(new JLabel("No description available"));

                pane.add(panelsDrescription);
            }

            // sets icon
            ArrayList<BufferedImage> lst = new ArrayList<BufferedImage>();
            BufferedImage im;
            try {
                im = ImageIO.read(getClass().getResource("/images/info16.png"));
                lst.add(im);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            try {
                im = ImageIO.read(getClass().getResource("/images/info32.png"));
                lst.add(im);
            } catch (IOException e1) {
                e1.printStackTrace();
            }
            descriptionFrame_.setIconImages(lst);

            descriptionFrame_.setContentPane(pane);
            descriptionFrame_.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            descriptionFrame_.pack();
            descriptionFrame_.setVisible(true);

        }
    }

    /**
     * Sets-up the frame, in this method the subclasses should instantiate the ConfigurablePanels and add them
     * to the ConfigurableMainFrame.
     */
    protected abstract void initComponents();

    /**
     * Returns the plugin information (description and author information). This is used in the plugin
     * description window prompted from the menu bar.
     *
     * @return Plugin information.
     */
    protected abstract String getPluginInfo();
}
