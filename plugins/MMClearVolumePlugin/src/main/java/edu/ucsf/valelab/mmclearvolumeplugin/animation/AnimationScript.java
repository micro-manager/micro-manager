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
 *   From frame &lt;N&gt; to frame &lt;M&gt; [:]
 *   At frame &lt;N&gt; [:]
 *     [- ] &lt;action&gt; [easing]
 *   script
 *   function name(t) { ... }
 * </pre>
 *
 * <p>A time-interval line ending with {@code :} acts as a prefix shared by
 * all following dash ({@code -}) lines until the next non-dash, non-empty
 * line.
 *
 * <p>An optional {@code script} section at the end of the script text may
 * define JavaScript functions.  Any numeric parameter in an action line may
 * be replaced by an identifier (e.g. {@code zoom}) that matches a function
 * declared in the {@code script} block; the function receives the current
 * frame number as its sole argument and its return value is used as the
 * parameter value for that frame.
 *
 * <p>Use {@link #parse(String)} to convert script text to a {@link ParseResult}.
 */
public final class AnimationScript {

   /** Matches a plain number (integer or floating point, with optional sign/exponent). */
   private static final Pattern NUMBER_PATTERN =
         Pattern.compile("[-+]?[0-9]*\\.?[0-9]+(?:[eE][-+]?[0-9]+)?");

   /**
    * Matches the channel number that must immediately follow the keyword
    * "channel": optional whitespace then one or more digits.
    * Rejects floating-point values so "channel 1.5" is caught early.
    */
   private static final Pattern CHANNEL_NUMBER_PATTERN =
         Pattern.compile("\\bchannel\\s+(\\d+)\\b", Pattern.CASE_INSENSITIVE);

   /** Matches the two frame numbers in a "From frame N to frame M" line. */
   private static final Pattern FROM_TO_PATTERN = Pattern.compile(
         "from\\s+frame\\s+(\\S+)\\s+to\\s+frame\\s+(\\S+)",
         Pattern.CASE_INSENSITIVE);

   /** Matches the frame number in an "At frame N" line. */
   private static final Pattern AT_FRAME_PATTERN = Pattern.compile(
         "at\\s+frame\\s+(\\S+)",
         Pattern.CASE_INSENSITIVE);

   /** Matches a non-negative integer (digits only, no sign, no decimal). */
   private static final Pattern INTEGER_PATTERN = Pattern.compile("^[0-9]+$");

   /**
    * Matches a bare identifier used in place of a number:
    * a word that starts with a letter and contains only letters, digits, or _.
    * We only accept it in positions where a number is expected and a number
    * was not found.
    */
   private static final Pattern IDENTIFIER_PATTERN =
         Pattern.compile("\\b([a-zA-Z_][a-zA-Z0-9_]*)\\b");

   private AnimationScript() {
   }

   // -----------------------------------------------------------------------
   // Public result type
   // -----------------------------------------------------------------------

   /**
    * The result of parsing an animation script: a list of instructions and
    * any script functions referenced by those instructions.
    */
   public static final class ParseResult {
      /** The parsed instructions, in script order. */
      public final List<AnimationInstruction> instructions;
      /**
       * Compiled script functions.  {@link ScriptFunctions#isEmpty()} is
       * {@code true} when the script contained no {@code script} block.
       */
      public final ScriptFunctions scriptFunctions;

      public ParseResult(List<AnimationInstruction> instructions,
                         ScriptFunctions scriptFunctions) {
         this.instructions = instructions;
         this.scriptFunctions = scriptFunctions;
      }
   }

   // -----------------------------------------------------------------------
   // Parse entry point
   // -----------------------------------------------------------------------

   /**
    * Parses the given script text and returns the instructions and any
    * associated script functions.
    *
    * @param scriptText the full animation script text
    * @return a {@link ParseResult} containing the instructions and script functions
    * @throws IllegalArgumentException if any line cannot be parsed or the
    *         script block contains a syntax error
    */
   public static ParseResult parse(String scriptText) {
      // Split off the optional "script" block at the end.
      String animationPart = scriptText;
      String scriptBody = null;

      // Find the first line that is exactly "script" (case-insensitive).
      String[] rawLines = scriptText.split("\\r?\\n", -1);
      int scriptKeywordLine = -1;
      for (int k = 0; k < rawLines.length; k++) {
         if (rawLines[k].trim().equalsIgnoreCase("script")) {
            scriptKeywordLine = k;
            break;
         }
      }
      if (scriptKeywordLine >= 0) {
         // Rebuild the animation part without the script section.
         StringBuilder sb = new StringBuilder();
         for (int k = 0; k < scriptKeywordLine; k++) {
            sb.append(rawLines[k]).append("\n");
         }
         animationPart = sb.toString();

         // The script body is everything after the "script" line.
         StringBuilder sbScript = new StringBuilder();
         for (int k = scriptKeywordLine + 1; k < rawLines.length; k++) {
            sbScript.append(rawLines[k]).append("\n");
         }
         scriptBody = sbScript.toString();
      }

      // Compile script functions (or use the empty sentinel).
      ScriptFunctions scriptFunctions = scriptBody != null
            ? new ScriptFunctions(scriptBody)
            : ScriptFunctions.EMPTY;

      // Parse animation instructions.
      List<AnimationInstruction> result = new ArrayList<AnimationInstruction>();

      String[] animLines = animationPart.split("\\r?\\n");
      List<String> lines = new ArrayList<String>();
      for (String line : animLines) {
         String trimmed = line.trim();
         if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
            lines.add(trimmed);
         }
      }

      int i = 0;
      while (i < lines.size()) {
         String line = lines.get(i);

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
            while (i < lines.size() && lines.get(i).startsWith("-")) {
               String actionLine = lines.get(i).substring(1).trim();
               result.add(parseAction(actionLine, beginFrame, endFrame));
               i++;
            }
         } else {
            String actionText = extractActionText(line);
            result.add(parseAction(actionText, beginFrame, endFrame));
         }
      }

      return new ParseResult(result, scriptFunctions);
   }

   // -----------------------------------------------------------------------
   // Interval parsing
   // -----------------------------------------------------------------------

   /**
    * Returns [beginFrame, endFrame], or null if the line is not an interval.
    *
    * <p>Frame numbers must be non-negative integers. Floating-point values,
    * negative numbers, and reversed intervals (begin &gt; end) are rejected
    * with a descriptive error.
    */
   private static int[] parseInterval(String line) {
      Matcher fromTo = FROM_TO_PATTERN.matcher(line);
      if (fromTo.find()) {
         int begin = parseFrameNumber(fromTo.group(1), line);
         int end   = parseFrameNumber(fromTo.group(2), line);
         if (begin > end) {
            throw new IllegalArgumentException(
                  "Reversed interval: begin frame " + begin
                        + " is after end frame " + end + " in: " + line);
         }
         return new int[]{begin, end};
      }

      Matcher atFrame = AT_FRAME_PATTERN.matcher(line);
      if (atFrame.find()) {
         int f = parseFrameNumber(atFrame.group(1), line);
         return new int[]{f, f};
      }

      return null;
   }

   /**
    * Parses a frame-number token, throwing a descriptive error if it is not
    * a non-negative integer.
    */
   private static int parseFrameNumber(String token, String context) {
      if (!INTEGER_PATTERN.matcher(token).matches()) {
         throw new IllegalArgumentException(
               "Frame number must be a non-negative integer, got '"
                     + token + "' in: " + context);
      }
      return Integer.parseInt(token);
   }

   /**
    * For a non-prefix interval line (no trailing {@code :}), strips the
    * interval portion and returns the rest as the action text.
    */
   private static String extractActionText(String line) {
      String lower = line.toLowerCase();
      int idx = -1;
      if (lower.startsWith("from frame") && lower.contains("to frame")) {
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
      // Extract numbers AND identifiers that appear in value positions.
      ParamList params = extractParams(text);

      // --- Rotation ---
      if (lower.contains("rotate")) {
         if (lower.contains("horizontally")) {
            return params.toInstruction(beginFrame, endFrame,
                  ActionType.ROTATE_H, new int[]{0}, 1, -1, easing);
         }
         if (lower.contains("vertically")) {
            return params.toInstruction(beginFrame, endFrame,
                  ActionType.ROTATE_V, new int[]{0}, 1, -1, easing);
         }
         if (lower.contains("around")) {
            return params.toInstruction(beginFrame, endFrame,
                  ActionType.ROTATE_AXIS, new int[]{0, 1, 2, 3}, 4, -1, easing);
         }
         throw new IllegalArgumentException("Unrecognised rotate action: " + text);
      }

      // --- Translation ---
      if (lower.contains("translate")) {
         if (lower.contains("horizontally")) {
            return params.toInstruction(beginFrame, endFrame,
                  ActionType.TRANSLATE_H, new int[]{0}, 1, -1, easing);
         }
         if (lower.contains("vertically")) {
            return params.toInstruction(beginFrame, endFrame,
                  ActionType.TRANSLATE_V, new int[]{0}, 1, -1, easing);
         }
         return params.toInstruction(beginFrame, endFrame,
               ActionType.TRANSLATE_XYZ, new int[]{0, 1, 2}, 3, -1, easing);
      }

      // --- Zoom ---
      if (lower.contains("zoom")) {
         return params.toInstruction(beginFrame, endFrame,
               ActionType.ZOOM, new int[]{0}, 1, -1, easing);
      }

      // --- Change ---
      if (lower.contains("change")) {
         return parseChange(text, lower, params, beginFrame, endFrame, easing);
      }

      throw new IllegalArgumentException("Unrecognised action: " + text);
   }

   private static AnimationInstruction parseChange(String text, String lower,
                                                   ParamList params,
                                                   int beginFrame, int endFrame,
                                                   Easing easing) {
      if (lower.contains("channel")) {
         // Parse the channel number directly from the text so that a missing
         // or non-integer channel number is caught immediately, rather than
         // silently mis-treating the first value parameter as the channel.
         Matcher chMatcher = CHANNEL_NUMBER_PATTERN.matcher(text);
         if (!chMatcher.find()) {
            throw new IllegalArgumentException(
                  "Missing or invalid channel number after 'channel' in: " + text
                        + " (expected an integer, e.g. 'change channel 0 intensity to 0.5')");
         }
         int ch = Integer.parseInt(chMatcher.group(1));
         // Remaining value parameters start at index 1 in params
         // (index 0 is the channel number, consumed above).

         if (lower.contains("intensity")) {
            if (lower.contains("min intensity")) {
               return params.toInstruction(beginFrame, endFrame,
                     ActionType.CHANGE_CH_MIN_INTENSITY,
                     new int[]{1}, 2, ch, easing);
            }
            if (lower.contains("max intensity")) {
               return params.toInstruction(beginFrame, endFrame,
                     ActionType.CHANGE_CH_MAX_INTENSITY,
                     new int[]{1}, 2, ch, easing);
            }
            if (lower.contains("intensity gamma")) {
               return params.toInstruction(beginFrame, endFrame,
                     ActionType.CHANGE_CH_INTENSITY_GAMMA,
                     new int[]{1}, 2, ch, easing);
            }
            return params.toInstruction(beginFrame, endFrame,
                  ActionType.CHANGE_CH_INTENSITY,
                  new int[]{1, 2, 3}, 4, ch, easing);
         }

         if (lower.contains("alpha")) {
            if (lower.contains("min alpha")) {
               return params.toInstruction(beginFrame, endFrame,
                     ActionType.CHANGE_CH_MIN_ALPHA,
                     new int[]{1}, 2, ch, easing);
            }
            if (lower.contains("max alpha")) {
               return params.toInstruction(beginFrame, endFrame,
                     ActionType.CHANGE_CH_MAX_ALPHA,
                     new int[]{1}, 2, ch, easing);
            }
            if (lower.contains("alpha gamma")) {
               return params.toInstruction(beginFrame, endFrame,
                     ActionType.CHANGE_CH_ALPHA_GAMMA,
                     new int[]{1}, 2, ch, easing);
            }
            return params.toInstruction(beginFrame, endFrame,
                  ActionType.CHANGE_CH_ALPHA,
                  new int[]{1, 2, 3}, 4, ch, easing);
         }

         if (lower.contains("color")) {
            return params.toInstruction(beginFrame, endFrame,
                  ActionType.CHANGE_CH_COLOR,
                  new int[]{1, 2, 3}, 4, ch, easing);
         }

         if (lower.contains("weight")) {
            return params.toInstruction(beginFrame, endFrame,
                  ActionType.CHANGE_CH_WEIGHT,
                  new int[]{1}, 2, ch, easing);
         }

         if (lower.contains("visib")) {
            // Extract the token(s) after the last keyword by looking at what
            // follows "to " (or the end of the line).  Accept "on", "show",
            // "true" as visible; "off", "hide", "false" (or anything else) as hidden.
            String tail = lower.replaceAll(".*\\bto\\b", "").trim();
            boolean visible = tail.startsWith("on") || tail.startsWith("show")
                  || tail.startsWith("true");
            return new AnimationInstruction(beginFrame, endFrame,
                  ActionType.CHANGE_CH_VISIBLE,
                  new double[]{visible ? 1.0 : 0.0}, null, ch, easing);
         }

         throw new IllegalArgumentException("Unrecognised channel change: " + text);
      }

      if (lower.contains("bounding box") || lower.contains("clipping")
            || lower.contains("front") || lower.contains("back")) {
         return parseClipChange(text, lower, params, beginFrame, endFrame, easing);
      }

      throw new IllegalArgumentException("Unrecognised change action: " + text);
   }

   private static AnimationInstruction parseClipChange(String text, String lower,
                                                       ParamList params,
                                                       int beginFrame, int endFrame,
                                                       Easing easing) {
      if (lower.contains("front/back clipping") || lower.contains("front / back clipping")) {
         return params.toInstruction(beginFrame, endFrame,
               ActionType.CHANGE_FRONT_BACK_CLIP, new int[]{0}, 1, -1, easing);
      }
      if (lower.contains("front clipping")) {
         return params.toInstruction(beginFrame, endFrame,
               ActionType.CHANGE_FRONT_CLIP, new int[]{0}, 1, -1, easing);
      }
      if (lower.contains("back clipping")) {
         return params.toInstruction(beginFrame, endFrame,
               ActionType.CHANGE_BACK_CLIP, new int[]{0}, 1, -1, easing);
      }

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

      if (lower.contains("bounding box " + axis + " to") && params.size() >= 2) {
         ActionType t = axis.equals("x") ? ActionType.CHANGE_CLIP_X
               : axis.equals("y") ? ActionType.CHANGE_CLIP_Y
               : ActionType.CHANGE_CLIP_Z;
         return params.toInstruction(beginFrame, endFrame, t,
               new int[]{0, 1}, 2, -1, easing);
      }

      if (lower.contains("min " + axis)) {
         ActionType t = axis.equals("x") ? ActionType.CHANGE_CLIP_MIN_X
               : axis.equals("y") ? ActionType.CHANGE_CLIP_MIN_Y
               : ActionType.CHANGE_CLIP_MIN_Z;
         return params.toInstruction(beginFrame, endFrame, t,
               new int[]{0}, 1, -1, easing);
      }
      if (lower.contains("max " + axis)) {
         ActionType t = axis.equals("x") ? ActionType.CHANGE_CLIP_MAX_X
               : axis.equals("y") ? ActionType.CHANGE_CLIP_MAX_Y
               : ActionType.CHANGE_CLIP_MAX_Z;
         return params.toInstruction(beginFrame, endFrame, t,
               new int[]{0}, 1, -1, easing);
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
      return Easing.LINEAR;
   }

   // -----------------------------------------------------------------------
   // ParamList: unified list of numbers and identifiers
   // -----------------------------------------------------------------------

   /**
    * Holds the sequence of values (numbers or script-function identifiers)
    * extracted from an action line.  Identifiers are recorded as script
    * function names; their corresponding numeric slot holds {@link Double#NaN}.
    */
   private static final class ParamList {
      private final double[] values_;
      private final String[] names_;  // non-null entry = script function name

      ParamList(double[] values, String[] names) {
         values_ = values;
         names_ = names;
      }

      boolean isEmpty() {
         return values_.length == 0;
      }

      int size() {
         return values_.length;
      }

      /**
       * Returns the numeric value at {@code index}.  Throws if the slot is
       * occupied by a script function name (identifiers cannot be used where
       * a literal is required, e.g. for channel numbers).
       */
      double getNumber(int index, String context) {
         if (index >= values_.length) {
            return 0.0;
         }
         if (names_[index] != null) {
            throw new IllegalArgumentException(
                  "Expected a literal number at parameter position " + index
                        + " but found script function name '"
                        + names_[index] + "' in: " + context);
         }
         return values_[index];
      }

      /**
       * Builds an {@link AnimationInstruction} by selecting the slots listed
       * in {@code paramIndices} from this list.
       *
       * @param beginFrame   interval start
       * @param endFrame     interval end
       * @param action       action type
       * @param paramIndices which slots in this list supply the params array
       * @param minCount     minimum number of values that must be present
       * @param channel      channel index (-1 for non-channel)
       * @param easing       easing function
       */
      AnimationInstruction toInstruction(int beginFrame, int endFrame,
                                         ActionType action,
                                         int[] paramIndices, int minCount,
                                         int channel, Easing easing) {
         if (values_.length < minCount) {
            throw new IllegalArgumentException(
                  action + " requires " + minCount + " parameter(s) but "
                        + values_.length + " were found.");
         }
         double[] p = new double[paramIndices.length];
         String[] fn = null;
         for (int k = 0; k < paramIndices.length; k++) {
            int idx = paramIndices[k];
            if (idx < values_.length) {
               p[k] = values_[idx];
               if (names_[idx] != null) {
                  if (fn == null) {
                     fn = new String[paramIndices.length];
                  }
                  fn[k] = names_[idx];
               }
            } else {
               p[k] = 0.0;
            }
         }
         return new AnimationInstruction(beginFrame, endFrame, action, p, fn, channel, easing);
      }
   }

   // -----------------------------------------------------------------------
   // Helpers
   // -----------------------------------------------------------------------

   /**
    * Extracts values from the action text in order of appearance.  Each token
    * that looks like a number is stored as a literal; each token that looks
    * like an identifier (and is not a reserved keyword) is stored as a script
    * function name.  Keywords that are part of the action syntax
    * ({@code rotate}, {@code translate}, {@code zoom}, {@code change},
    * {@code frame}, {@code by}, {@code a}, {@code factor}, {@code of},
    * {@code degrees}, {@code horizontally}, {@code vertically},
    * {@code channel}, {@code around}, {@code linearly}, {@code ease},
    * {@code bounding}, {@code box}, {@code min}, {@code max}, {@code to},
    * {@code intensity}, {@code gamma}, {@code alpha}, {@code color},
    * {@code weight}, {@code front}, {@code back}, {@code clipping},
    * {@code and}, {@code the}) are silently ignored.
    */
   private static ParamList extractParams(String text) {
      // We interleave number and identifier tokens in document order.
      // Build a merged list of (position, value/name) entries.
      List<double[]> numEntries = new ArrayList<double[]>(); // [position, value]
      List<Object[]> idEntries = new ArrayList<Object[]>();  // [position(double), name(String)]

      Matcher nm = NUMBER_PATTERN.matcher(text);
      while (nm.find()) {
         numEntries.add(new double[]{nm.start(), Double.parseDouble(nm.group())});
      }

      // Identifiers — skip reserved words.
      Matcher im = IDENTIFIER_PATTERN.matcher(text);
      while (im.find()) {
         String word = im.group(1);
         if (!isReservedWord(word.toLowerCase())) {
            idEntries.add(new Object[]{(double) im.start(), word});
         }
      }

      // Merge by position.
      List<double[]> allNums = new ArrayList<double[]>();
      List<String> allNames = new ArrayList<String>();

      int ni = 0;
      int ii = 0;
      while (ni < numEntries.size() || ii < idEntries.size()) {
         double numPos = ni < numEntries.size() ? numEntries.get(ni)[0] : Double.MAX_VALUE;
         double idPos  = ii < idEntries.size()  ? (double) idEntries.get(ii)[0] : Double.MAX_VALUE;

         if (numPos <= idPos) {
            allNums.add(new double[]{numEntries.get(ni)[1]});
            allNames.add(null);
            ni++;
         } else {
            allNums.add(new double[]{Double.NaN});
            allNames.add((String) idEntries.get(ii)[1]);
            ii++;
         }
      }

      double[] vals = new double[allNums.size()];
      String[] names = new String[allNums.size()];
      for (int k = 0; k < allNums.size(); k++) {
         vals[k] = allNums.get(k)[0];
         names[k] = allNames.get(k);
      }
      return new ParamList(vals, names);
   }

   private static final java.util.Set<String> RESERVED_WORDS;

   static {
      RESERVED_WORDS = new java.util.HashSet<String>();
      for (String w : new String[]{
            "rotate", "translate", "zoom", "change",
            "frame", "by", "a", "factor", "of",
            "degrees", "horizontally", "vertically",
            "channel", "around", "linearly",
            "ease", "ease-in", "ease-out", "ease-in-out",
            "bounding", "box", "min", "max", "to",
            "intensity", "gamma", "alpha", "color", "colour",
            "weight", "front", "back", "clipping", "and", "the",
            "from", "at", "x", "y", "z",
            "visible", "visibility", "show", "hide", "on", "off"
      }) {
         RESERVED_WORDS.add(w);
      }
   }

   private static boolean isReservedWord(String word) {
      return RESERVED_WORDS.contains(word);
   }

   private static List<Double> extractNumbers(String text) {
      List<Double> result = new ArrayList<Double>();
      Matcher m = NUMBER_PATTERN.matcher(text);
      while (m.find()) {
         result.add(Double.parseDouble(m.group()));
      }
      return result;
   }
}
