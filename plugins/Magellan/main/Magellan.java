package main;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import gui.GUI;
import java.util.prefs.Preferences;
import org.micromanager.MMStudio;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

/**
 *
 * @author Henry
 */
public class Magellan implements MMPlugin{

   private static final String VERSION = "Beta";
           
   public static final String menuName = "Micro-Magellan";
   public static final String tooltipDescription = "Micro-Magellan: A plugin for exploring samples in space and time";

   private static Preferences prefs_;
   private static ScriptInterface mmAPI_;
   private static GUI gui_;
   
   public Magellan() {
      if (gui_ == null) {
         prefs_ = Preferences.userNodeForPackage(Magellan.class);
         gui_ = new GUI(prefs_, MMStudio.getInstance(), VERSION);
      }
   }
   
   public static Preferences getPrefs() {
      return prefs_;
   }
   
   @Override
   public void dispose() {
   }

   @Override
   public void setApp(ScriptInterface si) {
      
   }

   @Override
   public void show() {      
      gui_.setVisible(true);
   }

   @Override
   public String getDescription() {
      return "Explore samples in space and time";
   }

   @Override
   public String getInfo() {
      return "";
   }

   @Override
   public String getVersion() {
      return VERSION;
   }

   @Override
   public String getCopyright() {
      return "Henry Pinkard UCSF 2014";
   }
}
