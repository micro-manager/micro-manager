///////////////////////////////////////////////////////////////////////////////
// FILE:          Camera.java
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MightexPolygon plugin
//-----------------------------------------------------------------------------
// DESCRIPTION:   Mightex Polygon400 plugin.
//                
// AUTHOR:        Wayne Liao, mightexsystem.com, 05/15/2015
//
// COPYRIGHT:     Mightex Systems, 2015
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//

package org.micromanager.polygon;

import ij.process.ImageProcessor;
import java.awt.image.BufferedImage;
import mmcorej.CMMCore;
import mmcorej.TaggedImage;
import org.micromanager.api.ScriptInterface;
import org.micromanager.utils.ImageUtils;

/**
 *
 * @author Wayne
 */
public class Camera {
    private final ScriptInterface app_;
    private final CMMCore core_;
    
    Camera( ScriptInterface app )
    {
        app_ = app;
        core_ = app.getMMCore();
    }
    
    int getImageWidth()
    {
        return (int)core_.getImageWidth();
    }
    
    int getImageHeight()
    {
        return (int)core_.getImageHeight();
    }
    
    TaggedImage getTaggedImage() throws Exception
    {
        if( !app_.isLiveModeOn() )
            core_.snapImage();
        return core_.getTaggedImage();
    }
    
    ImageProcessor getImageProcessor() throws Exception
    {
        TaggedImage img = getTaggedImage();
        return ImageUtils.makeProcessor(img);
    }
    
    ImageProcessor getMonochromeProcessor() throws Exception
    {
        TaggedImage img = getTaggedImage();
        return ImageUtils.makeMonochromeProcessor(img);
    }

    BufferedImage getBufferedImage() throws Exception
    {
        TaggedImage img = getTaggedImage();
        ImageProcessor proc = ImageUtils.makeProcessor(img);
        return proc.getBufferedImage();
    }
}
