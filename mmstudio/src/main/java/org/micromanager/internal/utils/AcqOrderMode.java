package org.micromanager.internal.utils;

import java.util.ArrayList;

public final class AcqOrderMode {
   // HACK: This value chosen to make the text box in the MDA dialog look nice.
   private static final int MAX_LINE_LEN = 60;
   public static final int TIME_POS_SLICE_CHANNEL = 0;
   public static final int TIME_POS_CHANNEL_SLICE = 1;
   public static final int POS_TIME_SLICE_CHANNEL = 2;
   public static final int POS_TIME_CHANNEL_SLICE = 3;

   private final int id_;
   private boolean timeEnabled_;
   private boolean posEnabled_;
   private boolean sliceEnabled_;
   private boolean channelEnabled_;

   public AcqOrderMode(int id) {
      id_ = id;
      timeEnabled_ = true;
      posEnabled_ = true;
      sliceEnabled_ = true;
      channelEnabled_ = true;
   }

   @Override
   public String toString() {
      StringBuffer name = new StringBuffer();
      for (String item : getOrdering()) {
         name.append(item + ", ");
      }
      // Remove the trailing comma and whitespace.
      if (name.length() > 2) {
         name.delete(name.length() - 2, name.length());
      }
      return name.toString();
   }

   /**
    * Generates a string describing the ordering, e.g.
    * "T1,P1,Z1,C1; T1,P1,Z1,C2; ...; T1,P1,Z2,C1; T1,P1,Z2,C2; ...;
    * T1,P2,Z1,C1; T1,P2,Z1,C2; ...; T2,P1,Z1,C1; T2,P1,Z1,C2; ...;"
    * Formatting is geared towards the acquisition control dialog.
    */
   public String getExample() {
      ArrayList<String> ordering = getOrdering();
      // Replace the ordering with single-letter versions.
      ArrayList<String> tmp = new ArrayList<String>();
      for (String axis : ordering) {
         if (axis.contentEquals("Slice")) {
            tmp.add("Z");
         }
         else {
            tmp.add(axis.substring(0, 1));
         }
      }
      ordering = tmp;

      if (ordering.size() == 0) {
         return "";
      }

      StringBuffer result = new StringBuffer();
      // Provide one entry per changing axis, in which that axis' index is 2,
      // all other axes' indices are 1, except the last axis which changes from
      // 1 to 2.
      String lastAxis = ordering.get(ordering.size() - 1);
      int lineLen = 0;
      for (int i = ordering.size() - 1; i >= 0; i--) {
         StringBuffer line = new StringBuffer();
         line.append("(");
         for (String axis : ordering) {
            // Note using ==/!= here is safe as lastAxis is an element from the
            // ordering array.
            int index = (axis != lastAxis && axis == ordering.get(i)) ? 2 : 1;
            line.append(axis + index);
         }
         line.append(") (");
         for (String axis : ordering) {
            int index = (axis == lastAxis || axis == ordering.get(i)) ? 2 : 1;
            line.append(axis + index);
         }
         line.append(") ... ");
         if (lineLen + line.length() > MAX_LINE_LEN) {
            result.append("\n");
            lineLen = 0;
         }
         result.append(line);
         lineLen += line.length();
      }
      return result.toString();
   }

   private ArrayList<String> getOrdering() {
      ArrayList<String> result = new ArrayList<>();
      if (timeEnabled_ && posEnabled_) {
         if (id_ == TIME_POS_CHANNEL_SLICE || id_ == TIME_POS_SLICE_CHANNEL) {
            result.add("Time");
            result.add("Position");
         }
         else {
            result.add("Position");
            result.add("Time");
         }
      }
      else if (timeEnabled_) {
         result.add("Time");
      }
      else if (posEnabled_) {
         result.add("Position");
      }

      if (channelEnabled_ && sliceEnabled_) {
         if (id_ == TIME_POS_CHANNEL_SLICE || id_ == POS_TIME_CHANNEL_SLICE) {
            result.add("Channel");
            result.add("Slice");
         }
         else {
            result.add("Slice");
            result.add("Channel");
         }
      }
      else if (channelEnabled_) {
         result.add("Channel");
      }
      else if (sliceEnabled_) {
         result.add("Slice");
      }

      return result;
   }

   public void setEnabled(boolean time, boolean position, boolean slice, boolean channel) {
      timeEnabled_ = time;
      posEnabled_ = position;
      sliceEnabled_ = slice;
      channelEnabled_ = channel;
   }

   public int getID() {
      return id_;
   }
}
