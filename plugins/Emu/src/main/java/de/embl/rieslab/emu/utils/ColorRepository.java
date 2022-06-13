package de.embl.rieslab.emu.utils;

import java.awt.Color;

public class ColorRepository {

   public static String strblack = "black";
   public static String strgray = "gray";
   public static String strgrey = "grey";
   public static String strwhite = "white";


   public static String strblue = "blue";
   public static String strpastelblue = "pastel blue";
   public static Color pastelblue = new Color(47, 126, 178);

   public static String strdarkblue = "dark blue";
   public static Color darkblue = new Color(21, 68, 150);

   public static String strgreen = "green";
   public static Color green = new Color(69, 198, 56);

   public static String strdarkgreen = "dark green";
   public static Color darkgreen = new Color(32, 140, 77);

   public static String stryellow = "yellow";
   public static Color yellow = new Color(244, 235, 0);

   public static String strorange = "orange";
   public static Color orange = new Color(215, 110, 8);

   public static String strred = "red";
   public static String strdarkred = "dark red";
   public static Color darkred = new Color(132, 27, 27);

   public static String strbrown = "brown";
   public static Color brown = new Color(153, 99, 30);

   public static String strpink = "pink";
   public static Color pink = new Color(224, 119, 220);
   public static String strviolet = "violet";
   public static Color violet = new Color(164, 110, 234);
   public static String strdarkviolet = "dark violet";
   public static Color darkviolet = new Color(116, 67, 178);

   private static final String[] colors = {
         strpink,
         strviolet,
         strdarkviolet,
         strdarkblue,
         strblue,
         strpastelblue,
         strdarkgreen,
         strgreen,
         stryellow,
         strorange,
         strbrown,
         strdarkred,
         strred,
         strblack,
         strgray,
         strwhite
   };

   public static Color getColor(String colorname) {
      if (colorname.equals(strblue)) {
         return Color.blue;
      } else if (colorname.equals(strdarkblue)) {
         return darkblue;
      } else if (colorname.equals(strpastelblue)) {
         return pastelblue;
      } else if (colorname.equals(strgreen)) {
         return green;
      } else if (colorname.equals(strdarkgreen)) {
         return darkgreen;
      } else if (colorname.equals(strgray) || colorname.equals(strgrey)) { // let's be diplomatic
         return Color.gray;
      } else if (colorname.equals(strdarkred)) {
         return darkred;
      } else if (colorname.equals(strbrown)) {
         return brown;
      } else if (colorname.equals(stryellow)) {
         return yellow;
      } else if (colorname.equals(strorange)) {
         return orange;
      } else if (colorname.equals(strpink)) {
         return pink;
      } else if (colorname.equals(strviolet)) {
         return violet;
      } else if (colorname.equals(strdarkviolet)) {
         return darkviolet;
      } else if (colorname.equals(strred)) {
         return Color.red;
      } else if (colorname.equals(strwhite)) {
         return Color.white;
      } else {
         return Color.black;
      }
   }

   public static String[] getColors() {
      return colors;
   }

   public static String getCommaSeparatedColors() {
      return String.join(", ", colors);
   }

   public static String getStringColor(Color c) {
      for (int i = 0; i < colors.length; i++) {
         if (c.getRGB() == getColor(colors[i]).getRGB()) {
            return colors[i];
         }
      }
      return strblack;
   }

   public static boolean isColor(String s) {
      for (int i = 0; i < colors.length; i++) {
         if (colors[i].equals(s)) {
            return true;
         }
      }
      return false;
   }
}
