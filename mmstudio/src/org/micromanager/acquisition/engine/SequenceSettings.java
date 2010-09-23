/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition.engine;

import java.util.ArrayList;
import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.utils.ChannelSpec;

/**
 *
 * @author arthur
 */
public class SequenceSettings {
   public int numFrames;
   public double intervalMs;
   public ArrayList<MultiStagePosition> positions;
   public ArrayList<ChannelSpec> channels;
   public ArrayList<Double> slices;
   public boolean relativeZSlice;
   public boolean slicesFirst;
   public boolean timeFirst;
   public boolean keepShutterOpenSlices;
   public boolean keepShutterOpenChannels;
   public boolean useAutofocus;
   public int skipAutofocusCount;
   public boolean save;
   public String root;
   public String prefix;
   public double zReference;
   public String comment;
}
