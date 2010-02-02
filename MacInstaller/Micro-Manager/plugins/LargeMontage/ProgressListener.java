/**
* Copyright (c) 2005-2007 Vilppu Tuominen (vtuo@iki.fi)
* University of Tampere, Institute of Medical Technology
*
* http://iki.fi/vtuo/software/largemontage/
*
* This program is free software; you can redistribute it and/or modify it
* under the terms of the GNU General Public License as published by the
* Free Software Foundation; either version 2 of the License, or (at your
* option) any later version.
*
* This program is distributed in the hope that it will be useful, but
* WITHOUT ANY WARRANTY; without even the implied warranty of
* MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
* General Public License for more details.
*
* You should have received a copy of the GNU General Public License along
* with this program; if not, write to the Free Software Foundation, Inc.,
* 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
*
*/

import ij.IJ;

import java.util.EventListener;

import javax.imageio.ImageWriter;
import javax.imageio.event.IIOWriteProgressListener;

/**
 * A listener class, used with ImageWrite-operation when the montage/mosaic is
 * being constructed to reveal the operation's progress.
 */
public class ProgressListener implements IIOWriteProgressListener, EventListener {

    private int previous = 0;
    private boolean verbose = false;
    
    public ProgressListener(boolean verbose) {
        this.verbose = verbose;
    }
    
    public void imageComplete(ImageWriter source) {}
    
    public void imageProgress(ImageWriter source, float percentageDone) {
        
        int p = (int) percentageDone;
        
        if (p != previous) {
            
            IJ.showStatus("Constructing the montage..");
            IJ.showProgress( (double) (percentageDone/100) );
            
            if (verbose && p % 10 == 0) {
                IJ.log("Construction completed: " + p + "%");
            }
            
        }

        previous = p;
        
    }
    
    public void imageStarted(ImageWriter source, int imageIndex) {}
    
    public void thumbnailComplete(ImageWriter source) {}
    
    public void thumbnailProgress(ImageWriter source, float percentageDone) {}
    
    public void thumbnailStarted(ImageWriter source,
            int imageIndex,
            int thumbnailIndex) {}
    
    public void writeAborted(ImageWriter source) {}
    
}
