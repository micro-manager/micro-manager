package de.embl.rieslab.emu.configuration.ui;

import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.border.TitledBorder;

/**
 * This class hides or displays a JFrame containing a String.
 *
 * @author Joran Deschamps
 */
public class HelpWindow {

    private JTextArea txtarea;
    private JFrame frame;
    private JPanel pan;
    private String defaulttext;
    private TitledBorder border;

    public HelpWindow(String def) {
        defaulttext = def;
        txtarea = new JTextArea(5, 40);
        txtarea.setEditable(false);
        txtarea.setText(defaulttext);

        pan = new JPanel();
        pan.setLayout(new BoxLayout(pan, BoxLayout.PAGE_AXIS));
        border = BorderFactory.createTitledBorder(null, "Click on a table row", TitledBorder.DEFAULT_JUSTIFICATION, TitledBorder.DEFAULT_POSITION, null, Color.black);
        border.setTitleFont(border.getTitleFont().deriveFont(Font.BOLD, 12));
        pan.setBorder(border);

        txtarea.setFont(new Font("Serif", Font.PLAIN, 16));
        txtarea.setLineWrap(true);
        txtarea.setWrapStyleWord(true);
        pan.add(new JLabel(" "));
        pan.add(new JScrollPane(txtarea, JScrollPane.VERTICAL_SCROLLBAR_ALWAYS,
                JScrollPane.HORIZONTAL_SCROLLBAR_NEVER));

        frame = new JFrame("Help window");

        // Sets the icon
        ArrayList<BufferedImage> lst = new ArrayList<BufferedImage>();
        BufferedImage im;
        try {
            im = ImageIO.read(getClass().getResource("/images/about16.png"));
            lst.add(im);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        try {
            im = ImageIO.read(getClass().getResource("/images/about32.png"));
            lst.add(im);
        } catch (IOException e1) {
            e1.printStackTrace();
        }
        frame.setIconImages(lst);

        frame.add(pan);
        frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
        frame.pack();
        frame.setVisible(false);

    }

    /**
     * Shows or hides the window.
     *
     * @param b True if the window is to be displayed, false otherwise.
     */
    public void showHelp(boolean b) {
        if (frame.isDisplayable()) {
            frame.setVisible(b);
        } else {
            txtarea.setText(defaulttext);
            frame = new JFrame("Help window");
            frame.add(pan);
            frame.setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
            frame.pack();
            frame.setVisible(b);
        }
    }

    /**
     * Disposes of the JFrame.
     */
    public void disposeHelp() {
        frame.dispose();
    }

    /**
     * Updates the JFrame with a new text.
     *
     * @param title   Name of the property/parameter/setting.
     * @param newtext Text to be displayed.
     */
    public void update(String title, String newtext) {
        if (title != null) {
            border.setTitle(title);
            frame.repaint();
        } else {
            border.setTitle("?");
            frame.repaint();
        }

        if (newtext != null) {
            txtarea.setText(newtext);
        } else {
            txtarea.setText("Description unavailable");
        }
    }
}