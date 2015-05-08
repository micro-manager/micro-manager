package main;

/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
import autofocus.CrossCorrelationAutofocus;
import gui.GUI;
import java.util.prefs.Preferences;
import mmcorej.CMMCore;
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

   public static String getConfigFilename() {
      throw new UnsupportedOperationException("Not yet implemented");
   }
   
   public Magellan() {
      if (gui_ == null) {
         prefs_ = Preferences.userNodeForPackage(Magellan.class);
         gui_ = new GUI(prefs_, VERSION);
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
   
   //methods for communicating with MM APIs
   public static CMMCore getCore() {
      return MMStudio.getInstance().getCore();
   }
   
   public static ScriptInterface getScriptInterface() {
      return (ScriptInterface) MMStudio.getInstance();
   }
   
   public static String getConfigFileName() {
      try {
         return MMStudio.getInstance().getSysConfigFile();
      } catch (Exception e) {
         //since this is not an API method
         return "";
      }    
   }
   
}
