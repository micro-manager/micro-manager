package com.asiimaging.example;

import javax.swing.UIManager;
import javax.swing.UIManager.LookAndFeelInfo;

import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

import com.asiimaging.example.utils.WindowUtils;

public class ExamplePlugin implements MMPlugin {
    
    public final static String copyright = "Applied Scientific Instrumentation (ASI), 2020";
    public final static String description = "An example plugin to understand the basics.";
    public final static String menuName = "Example Plugin";
    public final static String version = "0.0.1";
    
    private ScriptInterface gui;
    private ExampleFrame frame;
    
    @Override
    public void setApp(final ScriptInterface app) {
        gui = app;
        
        setSystemLookAndFeel();
        //setNimbusLookAndFeel();
        
        // only one instance of the plugin can be open
        if (WindowUtils.isOpen(frame)) {
            WindowUtils.close(frame);
        }
        
        try {
            frame = new ExampleFrame(gui);
            frame.setVisible(true);
        } catch (Exception e) {
            gui.showError(e);
        }
    }
    
    @Override
    public void dispose() {
        // this method is called to remove the plugin window
        WindowUtils.dispose(frame);
    }
    
    @Override
    public void show() {
    }
   
    @Override
    public String getInfo() {
        return menuName;
    }
    
    @Override
    public String getVersion() {
        return version;
    }
   
    @Override
    public String getCopyright() {
        return copyright;
    }
    
    @Override
    public String getDescription() {
        return description;
    }
    
    public void setSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) {
            // not found => not too important, only look and feel
        }
    }
    
    public void setNimbusLookAndFeel() {
        try {
            for (LookAndFeelInfo info : UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (Exception ignore) {
            // not found => not too important, only look and feel
        }
    }
}
