///////////////////////////////////////////////////////////////////////////////
// FILE:          Pattern.java
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

import java.awt.Graphics;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import javax.imageio.ImageIO;

/**
 *
 * @author Wayne
 */
public class Pattern implements LoadSave {
    public BufferedImage bi;
    public VectorGraph vg;

    public Pattern()
    {
        ClearBackground();
        vg = new VectorGraph();
    }
    
    public Pattern( BufferedImage b, VectorGraph v )
    {
        bi = b;
        vg = v;
    }
    
    public Pattern clone() throws CloneNotSupportedException
    {
        BufferedImage bi2 = Utility.DuplicateBufferedImage( bi );
        VectorGraph vg2 = vg.clone();
        return new Pattern( bi2, vg2 );
    }
    
    public void draw( Graphics g, Rectangle2D.Float r )
    {
        if(bi!=null)
            g.drawImage(bi, (int)r.x, (int)r.y, (int)r.width, (int)r.height, null);
        vg.draw(g, r);
    }
    
    public void ClearBackground()
    {
        bi = new BufferedImage(1, 1, BufferedImage.TYPE_BYTE_GRAY);
    }
    
    public void ClearVectorGraph()
    {
        vg.clear();
    }
    public void Clear()
    {
        ClearBackground();
        ClearVectorGraph();
    }
    
    @Override
    public boolean Save(String fn)
    {
        ObjectOutputStream os = null;
        try {
            os = new ObjectOutputStream(new FileOutputStream(fn));
            os.writeObject(vg);
            ImageIO.write(bi, "jpg", os);
            os.close();
            return true;
        }
        catch( IOException ex ){
            if( os != null ){
                try {
                    os.close();
                }
                catch( IOException ex2 ){
                }
            }
            return false;
        }
    }
    
    @Override
    public boolean Load(String fn)
    {
        ObjectInputStream is = null;
        try {
            is = new ObjectInputStream(new FileInputStream(fn));
            vg = (VectorGraph) is.readObject();
            bi = (BufferedImage) ImageIO.read(is);
            is.close();
            return true;
        }
        catch( Exception ex ) {
            if( is != null ){
                try {
                    is.close();
                }
                catch( Exception ex2 ){
                }
            }
            return false;
        }
    }
            
}
