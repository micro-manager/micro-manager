package edu.ucsf.valelab.mmclearvolumeplugin.animation;

import edu.ucsf.valelab.mmclearvolumeplugin.animation.AnimationInstruction.ActionType;
import edu.ucsf.valelab.mmclearvolumeplugin.animation.AnimationInstruction.Easing;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses animation scripts written in the language described by Wan et al.
 * (2019) Nature Methods into a list of {@link AnimationInstruction} objects.
 *
 * <p>Grammar overview:
 * <pre>
 *   From frame &lt;N&gt; to frame &lt;M&gt; [: ]
 *   At frame &lt;N&gt; [: ]
 *     [- ] &lt;action&gt; [easing]
 * </pre>
 *
 * <p>A time-interval line ending with {@code :} acts as a prefix shared by
 * all following dash ({@code -}) lines until the next non-dash, non-empty
 * line.
 *
 * <p>Use {@link #parse(String)} to convert script text to instructions.
 */
public final class AnimationScript {

   private static final Pattern NUMBER_PATTERN =
         Pattern.compile("[-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?");

   private AnimationScript() {
   }

   /**
    * Parses the given script text and returns a list of instructions.
    *
    * @param scriptText the full animation script text
    * @return ordered list of parsed instructions
    * @throws IllegalArgumentException if any line cannot be parsed
    */
   public static List<AnimationInstruction> parse(String scriptText) {
      List<AnimationInstruction> result = new ArrayList<AnimationInstruction>();

      String[] rawLines = scriptText.split("\\r?\\n");

      // Normalise lines: trim whitespace, drop blank lines and comments (#).
      List<String> lines = new ArrayList<String>();
      for (String line : rawLines) {
         String trimmed = line.trim();
         if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
            lines.add(trimmed);
         }
      }

      int i = 0;
      while (i < lines.size()) {
         String line = lines.get(i);

         // Check if this is a time-interval line.
         int[] interval = parseInterval(line);
         if (interval == null) {
            throw new IllegalArgumentException(
                  "Expected time interval (\"From frame N to frame M\" or "
                        + "\"At frame N\") but got: " + line);
         }
         int beginFrame = interval[0];
         int endFrame = interval[1];

         boolean isPrefix = line.endsWith(":");
         i++;

         if (isPrefix) {
            // Collect all following dash lines.
            while (i < lines.size() && lines.get(i).startsWith("-")) {
               String actionLine = lines.get(i).substring(1).trim();
               result.add(parseAction(actionLine, beginFrame, endFrame));
               i++;
            }
         } else {
            // The action follows on the same logical sentence; the time-
            // interval line does not end with ":", so the action text is the
            // remainder after the interval portion.
            String actionText = extractActionText(line);
            result.add(parseAction(actionText, beginFrame, endFrame));
         }
      }

      return result;
   }

   // -----------------------------------------------------------------------
   // Interval parsing
   // -----------------------------------------------------------------------

   /** Returns [beginFrame, endFrame], or null if the line is not an interval. */
   private static int[] parseInterval(String line) {
      String lower = line.toLowerCase();
      List<Double> nums = extractNumbers(line);

      if (lower.startsWith("from frame") && lower.contains("to frame")) {
         if (nums.size() < 2) {
            return null;
         }
         return new int[]{nums.get(0).intValue(), nums.get(1).intValue()};
      }
      if (lower.startsWith("at frame")) {
         if (nums.isEmpty()) {
            return null;
         }
         int f = nums.get(0).intValue();
         return new int[]{f, f};
      }
      return null;
   }

   /**
    * For a non-prefix interval line (no trailing {@code :}), strips the
    * interval portion and returns the rest as the action text.
    */
   private static String extractActionText(String line) {
      String lower = line.toLowerCase();
      int idx = -1;
      if (lower.startsWith("from frame") && lower.contains("to frame")) {
         // "from frame N to frame M action..."
         // Find position after the second frame number.
         Matcher m = Pattern.compile(
               "from\\s+frame\\s+[0-9]+\\s+to\\s+frame\\s+[0-9]+\\s*",
               Pattern.CASE_INSENSITIVE).matcher(line);
         if (m.find()) {
            idx = m.end();
         }
      } else if (lower.startsWith("at frame")) {
         Matcher m = Pattern.compile(
               "at\\s+frame\\s+[0-9]+\\s*",
               Pattern.CASE_INSENSITIVE).matcher(line);
         if (m.find()) {
            idx = m.end();
         }
      }
      if (idx < 0 || idx >= line.length()) {
         return "";
      }
      return line.substring(idx).trim();
   }

   // -----------------------------------------------------------------------
   // Action parsing
   // -----------------------------------------------------------------------

   private static AnimationInstruction parseAction(String text,
                                                   int beginFrame,
                                                   int endFrame) {
      String lower = text.toLowerCase();
      Easing easing = parseEasing(lower);
      List<Double> nums = extractNumbers(text);

      // --- Rotation ---
      if (lower.contains("rotate")) {
         if (lower.contains("horizontally")) {
            return new AnimationInstruction(beginFrame, endFrame,
                  ActionType.ROTATE_H, doubles(getOrZero(nums, 0)), -1, easing);
         }
         if (lower.contains("vertically")) {
            return new AnimationInstruction(beginFrame, endFrame,
                  ActionType.ROTATE_V, doubles(getOrZero(nums, 0)), -1, easing);
         }
         if (lower.contains("around")) {
            requireNumbers(nums, 4, text);
            return new AnimationInstruction(beginFrame, endFrame,
                  ActionType.ROTATE_AXIS,
                  doubles(nums.get(0), nums.get(1), nums.get(2), nums.get(3)),
                  -1, easing);
         }
         throw new IllegalArgumentException("Unrecognised rotate action: " + text);
      }

      // --- Translation ---
      if (lower.contains("translate")) {
         if (lower.contains("horizontally")) {
            return new AnimationInstruction(beginFrame, endFrame,
                  ActionType.TRANSLATE_H, doubles(getOrZero(nums, 0)), -1, easing);
         }
         if (lower.contains("vertically")) {
            return new AnimationInstruction(beginFrame, endFrame,
                  ActionType.TRANSLATE_V, doubles(getOrZero(nums, 0)), -1, easing);
         }
         requireNumbers(nums, 3, text);
         return new AnimationInstruction(beginFrame, endFrame,
               ActionType.TRANSLATE_XYZ,
               doubles(nums.get(0), nums.get(1), nums.get(2)), -1, easing);
      }

      // --- Zoom ---
      if (lower.contains("zoom")) {
         return new AnimationInstruction(beginFrame, endFrame,
               ActionType.ZOOM, doubles(getOrZero(nums, 0)), -1, easing);
      }

      // --- Change ---
      if (lower.contains("change")) {
         return parseChange(text, lower, nums, beginFrame, endFrame, easing);
      }

      throw new IllegalArgumentException("Unrecognised action: " + text);
   }

   private static AnimationInstruction parseChange(String text, String lower,
                                                   List<Double> nums,
                                                   int beginFrame, int endFrame,
                                                   Easing easing) {
      // Channel-specific changes: "change channel C ..."
      if (lower.contains("channel")) {
         // Channel number is the first number in the text.
         if (nums.isEmpty()) {
            throw new IllegalArgumentException("Missing channel number in: " + text);
         }
         int ch = nums.get(0).intValue();
         // Remaining numbers (skip the channel index).
         List<Double> rest = nums.subList(1, nums.size());

         if (lower.contains("intensity")) {
            if (lower.contains("min intensity")) {
               return new AnimationInstruction(beginFrame, endFrame,
                     ActionType.CHANGE_CH_MIN_INTENSITY,
                     doubles(getOrZero(rest, 0)), ch, easing);
            }
            if (lower.contains("max intensity")) {
               return new AnimationInstruction(beginFrame, endFrame,
                     ActionType.CHANGE_CH_MAX_INTENSITY,
                     doubles(getOrZero(rest, 0)), ch, easing);
            }
            if (lower.contains("intensity gamma")) {
               return new AnimationInstruction(beginFrame, endFrame,
                     ActionType.CHANGE_CH_INTENSITY_GAMMA,
                     doubles(getOrZero(rest, 0)), ch, easing);
            }
            // "intensity to (min, max, gamma)"
            requireNumbers(rest, 3, text);
            return new AnimationInstruction(beginFrame, endFrame,
                  ActionType.CHANGE_CH_INTENSITY,
                  doubles(rest.get(0), rest.get(1), rest.get(2)), ch, easing);
         }

         if (lower.contains("alpha")) {
            if (lower.contains("min alpha")) {
               return new AnimationInstruction(beginFrame, endFrame,
                     ActionType.CHANGE_CH_MIN_ALPHA,
                     doubles(getOrZero(rest, 0)), ch, easing);
            }
            if (lower.contains("max alpha")) {
               return new AnimationInstruction(beginFrame, endFrame,
                     ActionType.CHANGE_CH_MAX_ALPHA,
                     doubles(getOrZero(rest, 0)), ch, easing);
            }
            if (lower.contains("alpha gamma")) {
               return new AnimationInstruction(beginFrame, endFrame,
                     ActionType.CHANGE_CH_ALPHA_GAMMA,
                     doubles(getOrZero(rest, 0)), ch, easing);
            }
            requireNumbers(rest, 3, text);
            return new AnimationInstruction(beginFrame, endFrame,
                  ActionType.CHANGE_CH_ALPHA,
                  doubles(rest.get(0), rest.get(1), rest.get(2)), ch, easing);
         }

         if (lower.contains("color")) {
            requireNumbers(rest, 3, text);
            return new AnimationInstruction(beginFrame, endFrame,
                  ActionType.CHANGE_CH_COLOR,
                  doubles(rest.get(0), rest.get(1), rest.get(2)), ch, easing);
         }

         if (lower.contains("weight")) {
            return new AnimationInstruction(beginFrame, endFrame,
                  ActionType.CHANGE_CH_WEIGHT,
                  doubles(getOrZero(rest, 0)), ch, easing);
         }

         throw new IllegalArgumentException("Unrecognised channel change: " + text);
      }

      // Clipping / bounding box changes
      if (lower.contains("bounding box") || lower.contains("clipping")
            || lower.contains("front") || lower.contains("back")) {
         return parseClipChange(text, lower, nums, beginFrame, endFrame, easing);
      }

      throw new IllegalArgumentException("Unrecognised change action: " + text);
   }

   private static AnimationInstruction parseClipChange(String text, String lower,
                                                       List<Double> nums,
                                                       int beginFrame, int endFrame,
                                                       Easing easing) {
      // front/back clipping
      if (lower.contains("front/back clipping") || lower.contains("front / back clipping")) {
         return new AnimationInstruction(beginFrame, endFrame,
               ActionType.CHANGE_FRONT_BACK_CLIP, doubles(getOrZero(nums, 0)), -1, easing);
      }
      if (lower.contains("front clipping")) {
         return new AnimationInstruction(beginFrame, endFrame,
               ActionType.CHANGE_FRONT_CLIP, doubles(getOrZero(nums, 0)), -1, easing);
      }
      if (lower.contains("back clipping")) {
         return new AnimationInstruction(beginFrame, endFrame,
               ActionType.CHANGE_BACK_CLIP, doubles(getOrZero(nums, 0)), -1, easing);
      }

      // Bounding box — determine axis
      String axis = null;
      if (lower.contains(" x ") || lower.endsWith(" x")) {
         axis = "x";
      } else if (lower.contains(" y ") || lower.endsWith(" y")) {
         axis = "y";
      } else if (lower.contains(" z ") || lower.endsWith(" z")) {
         axis = "z";
      }
      if (axis == null) {
         throw new IllegalArgumentException("Cannot determine axis in: " + text);
      }

      // Range form: "bounding box x to (min, max)"
      if (lower.contains("bounding box " + axis + " to") && nums.size() >= 2) {
         ActionType t = axis.equals("x") ? ActionType.CHANGE_CLIP_X
               : axis.equals("y") ? ActionType.CHANGE_CLIP_Y
               : ActionType.CHANGE_CLIP_Z;
         return new AnimationInstruction(beginFrame, endFrame, t,
               doubles(nums.get(0), nums.get(1)), -1, easing);
      }

      if (lower.contains("min " + axis)) {
         ActionType t = axis.equals("x") ? ActionType.CHANGE_CLIP_MIN_X
               : axis.equals("y") ? ActionType.CHANGE_CLIP_MIN_Y
               : ActionType.CHANGE_CLIP_MIN_Z;
         return new AnimationInstruction(beginFrame, endFrame, t,
               doubles(getOrZero(nums, 0)), -1, easing);
      }
      if (lower.contains("max " + axis)) {
         ActionType t = axis.equals("x") ? ActionType.CHANGE_CLIP_MAX_X
               : axis.equals("y") ? ActionType.CHANGE_CLIP_MAX_Y
               : ActionType.CHANGE_CLIP_MAX_Z;
         return new AnimationInstruction(beginFrame, endFrame, t,
               doubles(getOrZero(nums, 0)), -1, easing);
      }

      throw new IllegalArgumentException("Unrecognised bounding box change: " + text);
   }

   // -----------------------------------------------------------------------
   // Easing
   // -----------------------------------------------------------------------

   private static Easing parseEasing(String lower) {
      if (lower.contains("ease-in-out")) {
         return Easing.EASE_IN_OUT;
      }
      if (lower.contains("ease-in")) {
         return Easing.EASE_IN;
      }
      if (lower.contains("ease-out")) {
         return Easing.EASE_OUT;
      }
      if (lower.contains("ease")) {
         return Easing.EASE;
      }
      // "linearly" or no keyword → linear
      return Easing.LINEAR;
   }

   // -----------------------------------------------------------------------
   // Helpers
   // -----------------------------------------------------------------------

   private static List<Double> extractNumbers(String text) {
      List<Double> result = new ArrayList<Double>();
      Matcher m = NUMBER_PATTERN.matcher(text);
      while (m.find()) {
         result.add(Double.parseDouble(m.group()));
      }
      return result;
   }

   private static double getOrZero(List<Double> list, int index) {
      return index < list.size() ? list.get(index) : 0.0;
   }

   private static double[] doubles(double... values) {
      return values;
   }

   private static void requireNumbers(List<Double> nums, int count, String context) {
      if (nums.size() < count) {
         throw new IllegalArgumentException(
               "Expected " + count + " numbers but found " + nums.size()
                     + " in: " + context);
      }
   }
}
