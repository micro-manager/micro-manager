///////////////////////////////////////////////////////////////////////////////
// FILE:          CalibrationThread.java
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

import ij.gui.Roi;
import ij.plugin.filter.GaussianBlur;
import ij.process.ImageProcessor;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import javax.swing.JOptionPane;
import org.micromanager.utils.ImageUtils;
import org.micromanager.utils.ReportingUtils;

/**
 *
 * @author Wayne
 */
public class CalibrationThread extends Thread {
    public final int ppc = 8;
    public final int ppr = 8;
    public final int delay = 1000;
    public final int min_diff = 5;
    
    private final Camera camera_;
    private final SLM slm_;
    private final Mapping mapping_;
    private final Rectangle cameraEWA_;
    private final Rectangle slmEWA_;
    
    private int spotSizeFactor_;
    private int imageWaitTime_;
    
    private int state_;
    private int idx_;
    
    public Point slmPoint;
    
    public float peakValue;
    public Point peakPoint;
    public float var;
    
    CalibrationThread(Camera camera, SLM slm, Mapping mapping, Rectangle cameraEWA, Rectangle slmEWA)
    {
        camera_ = camera;
        slm_ = slm;
        mapping_ = mapping;
        cameraEWA_ = cameraEWA;
        slmEWA_ = slmEWA;
        spotSizeFactor_ = 1;
        imageWaitTime_ = 1000;
    }
    
    public int GetSpotSizeFactor()
    {
        return spotSizeFactor_;
    }
    public void SetSpotSizeFactor( int v )
    {
        spotSizeFactor_ = v;
    }
    
    public int GetImageWaitTime()
    {
        return imageWaitTime_;
    }
    public void SetImageWaitTime( int v )
    {
        imageWaitTime_ = v;
    }
    
    @Override
    public void run()
    {
        Utility.LogMsg("Calibration Starts ...");
        try{
            slm_.ShowBlank();

            Thread.sleep(delay + imageWaitTime_);
            ImageProcessor background = camera_.getMonochromeProcessor();
//            Utility.Save(background, "bg.bmp");
            
            Point [] pt = new Point[6];
            for( state_ = 0; state_ < 3; state_++ ){
                for( idx_ = 0; idx_ < ppr * ppc; idx_++) {
                    slmPoint = CalculateSLMPoint();
                    ProjectSpot(slmPoint, spotSizeFactor_);
                    Thread.sleep(delay + imageWaitTime_);
                    ImageProcessor proc = camera_.getMonochromeProcessor();
                    ImageProcessor diffImage = ImageUtils.subtractImageProcessors(proc.convertToFloatProcessor(), background.convertToFloatProcessor());
                    findPeak(diffImage);
                    Utility.LogMsg("state = " + state_ + ", idx = " + idx_ + ", peak value = " + peakValue + " pos = ( " + peakPoint.x + ", " + peakPoint.y + " ), Var = " + var );
                    BufferedImage bi = proc.getBufferedImage();
                    Utility.DrawCross(bi, peakPoint);
//                    Utility.Save(bi, state_+"_"+idx_+".bmp");
                    if( peakValue >= min_diff )
                        break;
                }
                if( idx_ < ppr * ppc ) {
                    pt[ state_ * 2 ] = slmPoint;
                    pt[ state_ * 2 + 1 ] = peakPoint;
                    Utility.LogMsg( "Calibration[" + state_ + "]: Polygon(" + slmPoint.x + "," + slmPoint.y + "), Camera(" + peakPoint.x + ","+peakPoint.y + ")");
                }
                else
                    break;
            }
            if( state_ >= 3 ){
                if( mapping_.SetMapping( pt[0], pt[1], pt[4], pt[5], pt[2], pt[3] ) >= 0 ){
                    calculateEWA();
                    slm_.SaveAffineTransform( mapping_.getCameraToPolygonAffineTransform() );
                    JOptionPane.showMessageDialog(null, "Congratuations !", "Calibration succeeded", JOptionPane.INFORMATION_MESSAGE);
                }
                else{
                    JOptionPane.showMessageDialog(null, "Calibration failed due to duplicated points. Perhaps effective working area is too small.", "Calibration failed", JOptionPane.ERROR_MESSAGE);
                }
            }
            else{
                JOptionPane.showMessageDialog(null, "Please check if camera is focused and light source works.", "Calibration failed", JOptionPane.ERROR_MESSAGE);
            }
            slm_.stopSequence();
            Utility.LogMsg("Calibration Ends");
        }
        catch(Exception e){
            ReportingUtils.logError(e);
        }
    }

    public int getCalState() { return state_; }
    public int getCalIdx() { return idx_; }
    
    private void ProjectSpot(Point p, int r)
    {
        slm_.ShowSpot(p.x, p.y, r);
    }
    
    private Point CalculateSLMPoint()
    {
        int w = slm_.getWidth();
        int h = slm_.getHeight();
        
        int xi = idx_ % ppr;
        int yi = idx_ / ppc;
        
        int x, y;
        switch(state_){
            case 0:
                x = w * ( xi + 1 ) / ( ppr + 1 );
                y = h * ( yi + 1 ) / ( ppc + 1 );
                break;
            case 1:
                x = w * ( ppr - xi ) / ( ppr + 1 );
                y = h * ( yi + 1 ) / ( ppc + 1 );
                break;
            case 2:
                x = w * ( ppr - xi ) / ( ppr + 1 );
                y = h * ( ppc - yi ) / ( ppc + 1 );
                break;
            default:
                x = w * ( xi + 1 ) / ( ppr + 1 );
                y = h * ( ppc - yi ) / ( ppc + 1 );
                break;
        }
        return new Point(x, y);
    }

    private void findMaxPixel(ImageProcessor proc) 
    {
        float[] pix = (float[]) proc.getPixels();
        Utility.LogMsg("Length = "+pix.length + ", Width=" + proc.getWidth() + ", Height = " + proc.getHeight());
        int width = proc.getWidth();
        float max = 0;
        int xsum = 0, ysum = 0, cnt = 0;
        for (int i = 0; i < pix.length; i++) {
            if (pix[i] > max) {
                max = pix[i];
                ysum = i / width;
                xsum = i % width;
                cnt = 1;
            }
            else if(pix[i]==max) {
                ysum += i / width;
                xsum += i % width;
                cnt++;
            }
        }
        int y = cnt > 0 ? ysum / cnt : 0;
        int x = cnt > 0 ? xsum / cnt : 0;

        peakPoint = new Point(x, y);
        peakValue = max;
        var = Utility.CalculateVariance(pix);
    }   
    
    private void findPeak(ImageProcessor proc) 
    {
        ImageProcessor blurImage = proc.duplicate();
        blurImage.setRoi((Roi) null);
        GaussianBlur blur = new GaussianBlur();
        blur.blurGaussian(blurImage, 10, 10, 0.01);
        findMaxPixel(blurImage);
    }
  
    private void calculateEWA()
    {
        Point pt1 = mapping_.P2W( 0, 0 );
        Point pt2 = mapping_.P2W( slm_.getWidth(), slm_.getHeight() );

        int slmLeft = Math.min( pt1.x, pt2.x );
        int slmRight = Math.max( pt1.x, pt2.x);
        int slmTop = Math.min( pt1.y, pt2.y );
        int slmBottom = Math.max( pt1.y, pt2.y );
        
        int cameraLeft = 0;
        int cameraRight = camera_.getImageWidth();
        int cameraTop = 0;
        int cameraBottom = camera_.getImageHeight();
        
        if( slmLeft < cameraLeft ){
            if( slmRight <= cameraLeft ){
                cameraEWA_.x = 0;
                cameraEWA_.width = 0;
            }
            else if( slmRight > cameraLeft && slmRight <= cameraRight ){
                cameraEWA_.x = cameraLeft;
                cameraEWA_.width = slmRight - cameraLeft;
            }
            else if( slmRight > cameraRight ){
                cameraEWA_.x = cameraLeft;
                cameraEWA_.width = cameraRight - cameraLeft;
            }
        }
        else if( slmLeft >= cameraLeft && slmLeft < cameraRight){
            if( slmRight <= cameraRight ){
                cameraEWA_.x = slmLeft;
                cameraEWA_.width = slmRight - slmLeft;
            }
            else if( slmRight > cameraRight ){
                cameraEWA_.x = slmLeft;
                cameraEWA_.width = cameraRight - slmLeft;
            }
        }
        else if( slmLeft >= cameraRight ){
            cameraEWA_.x = 0;
            cameraEWA_.width = 0;
        }
        
        if( slmTop < cameraTop ){
            if( slmBottom <= cameraTop ){
                cameraEWA_.y = 0;
                cameraEWA_.height = 0;
            }
            else if( slmBottom > cameraTop && slmBottom <= cameraBottom ){
                cameraEWA_.y = cameraTop;
                cameraEWA_.height = slmBottom - cameraTop;
            }
            else if( slmBottom > cameraBottom ){
                cameraEWA_.y = cameraTop;
                cameraEWA_.height = cameraBottom - cameraTop;
            }
        }
        else if( slmTop >= cameraTop && slmTop < cameraBottom){
            if( slmBottom <= cameraBottom ){
                cameraEWA_.y = slmTop;
                cameraEWA_.height = slmBottom - slmTop;
            }
            else if( slmBottom > cameraBottom ){
                cameraEWA_.y = slmTop;
                cameraEWA_.height = cameraBottom - slmTop;
            }
        }
        else if( slmTop >= cameraBottom ){
            cameraEWA_.y = 0;
            cameraEWA_.height = 0;
        }
        pt1 = mapping_.W2P( cameraEWA_.x, cameraEWA_.y );
        pt2 = mapping_.W2P( cameraEWA_.x + cameraEWA_.width, cameraEWA_.y + cameraEWA_.height );
        slmEWA_.x = Math.min( pt1.x, pt2.x );
        slmEWA_.width = Math.max( pt1.x, pt2.x) - slmEWA_.x;
        slmEWA_.y = Math.min( pt1.y, pt2.y );
        slmEWA_.height = Math.max( pt1.y, pt2.y ) - slmEWA_.y;
    }
    
}
