///////////////////////////////////////////////////////////////////////////////
//FILE:          AutoWB.java
//PROJECT:       PMQI_WhiteBalance
//-----------------------------------------------------------------------------
//AUTHOR:        Andrej Bencur, abencur@photometrics.com, August 28, 2015
//COPYRIGHT:     QImaging, Surrey, BC, 2015
//LICENSE:       This file is distributed under the BSD license.
//               License text is included with the source distribution.
//               This file is distributed in the hope that it will be useful,
//               but WITHOUT ANY WARRANTY; without even the implied warranty
//               of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//               IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//               CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//               INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
package PMQI;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.micromanager.MMStudio;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Andrej
 */
public class AutoWB implements org.micromanager.api.MMPlugin {

    public static String menuName = "PM/QI Auto White Balance";
    public static String tooltipDescription = "Runs automatic white balance algorithm";
    private MMStudio gui_;
    private WhiteBalance_UI wbForm_;

    @Override
    public void dispose() {

    }

    @Override
    public void setApp(ScriptInterface app) {
        gui_ = (MMStudio) app;

        try {
            wbForm_ = new WhiteBalance_UI(gui_);
            gui_.addMMListener(wbForm_);
            gui_.addMMBackgroundListener(wbForm_);
        } catch (Exception e) {
            Logger.getLogger(WhiteBalance_UI.class.getName()).log(Level.SEVERE, null, e);
            ReportingUtils.showError(e);
        }
        wbForm_.setVisible(true);

    }

    @Override
    public void show() {
        String msg = "PMQI White Balance";
    }

    @Override
    public String getDescription() {
        return "Automatic White Balance scaling calculator for PVCAM compatible Photometrics and QImaging cameras";
    }

    @Override
    public String getInfo() {
        return "PMQI Automatic White Balance";
    }

    @Override
    public String getVersion() {
        return "1.0";
    }

    @Override
    public String getCopyright() {
        return "(C) 2015 Photometrics/QImaging";
    }

}
