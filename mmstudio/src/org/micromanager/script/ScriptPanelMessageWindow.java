package org.micromanager.script;

import java.awt.Color;
import java.awt.Font;
import java.awt.Rectangle;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.prefs.Preferences;

import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SpringLayout;

import org.micromanager.utils.GUIColors;
import org.micromanager.utils.MMDialog;

public class ScriptPanelMessageWindow extends MMDialog {
   private static final long serialVersionUID = 6525868523113162761L;
   private JTextPane textPane_;
   private JScrollPane scrollPane_;
   private Preferences prefs_;
   private SpringLayout springLayout;

   public ScriptPanelMessageWindow() {
      super();

      addWindowListener(new WindowAdapter() {
         public void windowClosing(WindowEvent arg0) {
            savePosition();
         }
      });

      new GUIColors();
      setTitle("Script Messages");
      springLayout = new SpringLayout();
      getContentPane().setLayout(springLayout);
      setBounds (100, 100, 300, 200);

      Preferences root = Preferences.userNodeForPackage(this.getClass());   
      prefs_ = root.node(root.absolutePath() + "/ScriptPanelMessageWindow");
      setPrefsNode(prefs_); 

      Rectangle r = getBounds();
      loadPosition(r.x, r.y, r.width, r.height);

      textPane_ = new JTextPane();
      textPane_.setFont(new Font("Courier New", Font.PLAIN, 12));
      textPane_.setBackground(Color.WHITE);
      scrollPane_ = new JScrollPane(textPane_);
      getContentPane().add(scrollPane_);
      springLayout.putConstraint(SpringLayout.SOUTH, scrollPane_, 0, SpringLayout.SOUTH, getContentPane());
      springLayout.putConstraint(SpringLayout.NORTH, scrollPane_, 0, SpringLayout.NORTH, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, scrollPane_, 0, SpringLayout.WEST, getContentPane());
      springLayout.putConstraint(SpringLayout.EAST, scrollPane_, 0, SpringLayout.EAST, getContentPane());
      springLayout.putConstraint(SpringLayout.WEST, scrollPane_, 0, SpringLayout.WEST, getContentPane());

      System.out.println("Created new Message Window");

   }

   public void message (String text) {
      String outText = textPane_.getText();
      outText += text + "\n";
      textPane_.setText(outText);
      //setSelectedComponent(scrollPane_);
   }

   public void clearOutput() {
      textPane_.setText("");
   }

   public void closeWindow() {
      savePosition();
      dispose();
   }

}

