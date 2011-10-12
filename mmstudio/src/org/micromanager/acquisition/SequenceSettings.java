/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.micromanager.acquisition;

import java.util.ArrayList;
import org.micromanager.navigation.MultiStagePosition;
import org.micromanager.utils.ChannelSpec;

/**
 *
 * @author arthur
 */
public class SequenceSettings {
   public int numFrames = 1;   
   public double intervalMs;
   public ArrayList<Double> customIntervalsMs = null;
   public ArrayList<MultiStagePosition> positions = new ArrayList<MultiStagePosition>();
   public ArrayList<ChannelSpec> channels = new ArrayList<ChannelSpec>();
   public ArrayList<Double> slices = new ArrayList<Double>();
   public boolean relativeZSlice = false;
   public boolean slicesFirst = false;
   public boolean timeFirst = false;
   public boolean keepShutterOpenSlices = false;
   public boolean keepShutterOpenChannels = false;
   public boolean useAutofocus = false;
   public int skipAutofocusCount = 0;
   public boolean save = false;
   public String root = null;
   public String prefix = null;
   public double zReference = 0;
   public String comment = "";
}
