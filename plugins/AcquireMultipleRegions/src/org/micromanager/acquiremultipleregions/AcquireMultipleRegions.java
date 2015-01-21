/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquiremultipleregions;

import org.micromanager.api.ScriptInterface;

/**
 *
 * @author kthorn
 */
public class AcquireMultipleRegions  implements org.micromanager.api.MMPlugin {
   public static final String menuName = "Acquire Multiple Regions";
   public static final String tooltipDescription =
      "Automatically acquire multiple regions of a sample";
   public static String versionNumber = "0.4";
   private ScriptInterface gui_;
   private AcquireMultipleRegionsForm myFrame_;
   //Static variables so we can use script panel to tweak interpolation params
   //Exponent for Shepard interpolation
   
    /**
     *
     */
       public static double shepardExponent = 2; 
   
    @Override
    public void dispose() {
    }

    @Override
    public void setApp(ScriptInterface si) {
      gui_ = si;                                        
      if (myFrame_ == null)
         myFrame_ = new AcquireMultipleRegionsForm(gui_);
      myFrame_.setVisible(true);
      
      // Used to change the background layout of the form.  Does not work on Windows
      gui_.addMMBackgroundListener(myFrame_);    
    }

    @Override
    public void show() {
    }

    @Override
    public String getDescription() {
        return tooltipDescription;
    }

    @Override
    public String getInfo() {
        return tooltipDescription;
    }

    @Override
    public String getVersion() {
        return versionNumber;
    }

    @Override
    public String getCopyright() {
        return "University of California, 2014";    
    }
    
}
