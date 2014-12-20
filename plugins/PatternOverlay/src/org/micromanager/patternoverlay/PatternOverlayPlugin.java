package org.micromanager.patternoverlay;

import java.awt.Color;
import java.awt.event.WindowEvent;

import org.micromanager.MMStudio;
import org.micromanager.api.MMPlugin;
import org.micromanager.api.ScriptInterface;

/**
 * Add a overlay to the live view image.
 * The frame is recreated each time it is opened, not just hidden   
 *
 * @author Matthijs
 * @author Jon
 */
public class PatternOverlayPlugin implements MMPlugin {
   
   public static String menuName = "Pattern Overlay";
   public final static String tooltipDescription = "Overlay pattern on viewer window";
   public final static Color borderColor = Color.gray;
   
    private MMStudio  gui_;
    private static PatternOverlayFrame myFrame_;
    
   
    
    @Override
    public void setApp(ScriptInterface app) {
       gui_ = (MMStudio) app;
       if (myFrame_ != null) {
          WindowEvent wev = new WindowEvent(myFrame_, WindowEvent.WINDOW_CLOSING);
          myFrame_.dispatchEvent(wev);
          myFrame_ = null;
       }
       if (myFrame_ == null) {
          try {
             myFrame_ = new PatternOverlayFrame(gui_);
             myFrame_.setBackground(gui_.getBackgroundColor());
             myFrame_.setTitle(menuName);
          } catch (Exception e) {
             gui_.showError(e);
          }
       }
       myFrame_.setVisible(true);
    }
    
    /**
     * The main app calls this method to remove the module window
     */
    @Override
    public void dispose() {
       if (myFrame_ != null)
          myFrame_.dispose();
    }

    @Override
    public void show() {
       @SuppressWarnings("unused")
       String ig = menuName;
    }


    /**
     *  General purpose information members.
    * @return 
     */
    @Override public String getDescription() { 
       return "Add an overlay shape to the image window."; 
    }
    @Override public String getInfo()        { 
       return menuName;      
    }
    @Override public String getVersion()     { 
       return "2";         
    }
    @Override public String getCopyright()   { 
       return "Applied Scientific Instrumentation, 2014";  
    }
}
