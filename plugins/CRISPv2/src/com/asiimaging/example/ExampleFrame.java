package com.asiimaging.example;

import java.awt.Font;

import javax.swing.JFrame;
import javax.swing.JLabel;

import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.MMFrame;

import com.asiimaging.example.data.Icons;
import com.asiimaging.example.utils.ObjectUtils;

import mmcorej.CMMCore;
import net.miginfocom.swing.MigLayout;

@SuppressWarnings("serial")
public class ExampleFrame extends MMFrame {

    @SuppressWarnings("unused")
    private final CMMCore core;
    private final ScriptInterface gui;

    private JLabel lblTitle;
    private JLabel lblText;
    
    public ExampleFrame(final ScriptInterface app) {
        gui = ObjectUtils.requireNonNull(app);
        core = gui.getMMCore();
        createUserInterface();
    }

    /**
     * Create the user interface for the plugin.
     */
    private void createUserInterface() {
        setTitle(ExamplePlugin.menuName);
        loadAndRestorePosition(200, 200);
        setResizable(false);
        //setSize(600, 400);
        
        // use MigLayout as the layout manager
        setLayout(new MigLayout(
            "insets 20 10 10 10",
            "",
            ""
        ));
        
        // draw the title in bold
        lblTitle = new JLabel(ExamplePlugin.menuName);
        lblTitle.setFont(new Font(Font.MONOSPACED, Font.BOLD, 20));
        
        lblText = new JLabel("An example to use as boilerplate code when developing Micro-Manager plugins.");
        
        // add swing components to the frame
        add(lblTitle, "span, center, wrap");
        add(lblText, "");
        
        pack(); // set the window size automatically
        setIconImage(Icons.MICROSCOPE_ICON.getImage());
        
        // clean up resources when the frame is closed
        setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
    }
}
