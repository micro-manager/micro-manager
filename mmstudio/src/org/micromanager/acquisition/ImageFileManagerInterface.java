/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition;

import java.util.ArrayList;
import mmcorej.Metadata;
import org.micromanager.utils.MMException;

/**
 *
 * @author arthur
 */
public interface ImageFileManagerInterface {
   public String writeImage(TaggedImage taggedImage) throws MMException;
   public TaggedImage readImage(String filename);
   public void finishWriting();
   public void setSummaryMetadata(Metadata md);
   public Metadata getSummaryMetadata();
   public void setSystemMetadata(Metadata md);
   public Metadata getSystemMetadata();
   public ArrayList<Metadata> getImageMetadata();
}
