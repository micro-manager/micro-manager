///////////////////////////////////////////////////////////////////////////////
// FILE:          Utility.java
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
import java.awt.Point;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import javax.imageio.ImageIO;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Wayne
 */
public class Utility {
    public static void LogMsg( String s )
    {
        PrintWriter out = null;
        try{
            out = new PrintWriter(new BufferedWriter(new FileWriter("log.txt", true)));
            out.println(s);
        }catch (IOException e) {
        }
        finally{
            if(out!=null){
                out.close();
            }
        }
    }

    public static void LogException( Exception ex )
    {
        PrintWriter out = null;
        try{
            out = new PrintWriter(new BufferedWriter(new FileWriter("log.txt", true)));
            ex.printStackTrace( out );
        }catch (IOException e) {
        }
        finally{
            if(out!=null){
                out.close();
            }
        }
    }
    public static void Save(ImageProcessor proc, String fn)
    {
        try {
            ImageIO.write(proc.getBufferedImage(), "bmp", new File(fn));
        } catch (IOException e) {
            ReportingUtils.logError(e);
        }
    }
    public static void Save(BufferedImage bi, String fn)
    {
        try {
            ImageIO.write(bi, "bmp", new File(fn));
        } catch (IOException e) {
            ReportingUtils.logError(e);
        }
    }
    public static void DrawCross(BufferedImage bi, Point pt)
    {
        final int max_r = 50, min_r = 10, color = 0xffffff;
        for( int x = pt.x - max_r; x < pt.x - min_r; x++ ){
            if( x >= 0 && x < bi.getWidth()) 
                bi.setRGB( x, pt.y, color );
        }
        for( int x = pt.x + min_r; x < pt.x + max_r; x++ ){
            if( x >= 0 && x < bi.getWidth()) 
                bi.setRGB( x, pt.y, color );
        }
        for( int y = pt.y - max_r; y < pt.y - min_r; y++ ){
            if( y >=0 && y < bi.getHeight() )
                bi.setRGB(pt.x, y, color);
        }
        for( int y = pt.y + min_r; y < pt.y + max_r; y++ ){
            if( y >=0 && y < bi.getHeight() )
                bi.setRGB(pt.x, y, color);
        }
    }
    public static long CalculateVariance(short[] data)
    {
        long sum = 0, sum2 = 0;
        for( int i = 0; i < data.length; i++){
            sum += data[i];
            sum2 += data[i] * data[i];
        }
        return ( sum2 - sum * sum / data.length ) / data.length;
    }
    public static float CalculateVariance(float[] data)
    {
        float sum = 0, sum2 = 0;
        for( int i = 0; i < data.length; i++){
            sum += data[i];
            sum2 += data[i] * data[i];
        }
        return ( sum2 - sum * sum / data.length ) / data.length;
    }

    public static void DeleteFolder(File folder) {
        File[] files = folder.listFiles();
        if( files != null ) { 
            for( File f: files ) {
                if( f.isDirectory() ) {
                    DeleteFolder( f );
                } else {
                    f.delete();
                }
            }
        }
        folder.delete();
    }
    
    public static BufferedImage DuplicateBufferedImage(BufferedImage bi) 
    {
        ColorModel cm = bi.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = bi.copyData(null);
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }    
}
