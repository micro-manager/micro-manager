package edu.ucsf.valelab.mmclearvolumeplugin.animation;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;

/**
 * Evaluates user-defined script functions declared in an animation script's
 * {@code script} block.
 *
 * <p>Functions are written in a subset of JavaScript that mirrors the ImageJ
 * macro math API used by the original 3Dscript language (Wan et al. 2019).
 * The following top-level names are available inside function bodies:
 * {@code PI}, {@code abs}, {@code sin}, {@code cos}, {@code tan},
 * {@code asin}, {@code acos}, {@code atan}, {@code atan2},
 * {@code sqrt}, {@code pow}, {@code exp}, {@code log},
 * {@code floor}, {@code ceil}, {@code round}, {@code min}, {@code max}.
 *
 * <p>Example declaration in a script file:
 * <pre>
 *   script
 *   function zoom(t) {
 *       return 0.6 - 0.3 * abs(sin(2 * PI * t / 180));
 *   }
 * </pre>
 *
 * <p>Call {@link #evaluate(String, double)} to invoke a named function.
 */
public final class ScriptFunctions {

   /** A no-op instance used when the script contains no {@code script} block. */
   public static final ScriptFunctions EMPTY = new ScriptFunctions();

   private final ScriptEngine engine_;

   /** Shims that expose Java Math as top-level names in the script environment. */
   private static final String MATH_SHIMS =
         "var PI    = Math.PI;\n"
         + "var abs   = function(x) { return Math.abs(x); };\n"
         + "var sin   = function(x) { return Math.sin(x); };\n"
         + "var cos   = function(x) { return Math.cos(x); };\n"
         + "var tan   = function(x) { return Math.tan(x); };\n"
         + "var asin  = function(x) { return Math.asin(x); };\n"
         + "var acos  = function(x) { return Math.acos(x); };\n"
         + "var atan  = function(x) { return Math.atan(x); };\n"
         + "var atan2 = function(y,x){ return Math.atan2(y,x); };\n"
         + "var sqrt  = function(x) { return Math.sqrt(x); };\n"
         + "var pow   = function(b,e){ return Math.pow(b,e); };\n"
         + "var exp   = function(x) { return Math.exp(x); };\n"
         + "var log   = function(x) { return Math.log(x); };\n"
         + "var floor = function(x) { return Math.floor(x); };\n"
         + "var ceil  = function(x) { return Math.ceil(x); };\n"
         + "var round = function(x) { return Math.round(x); };\n"
         + "var min   = function(a,b){ return Math.min(a,b); };\n"
         + "var max   = function(a,b){ return Math.max(a,b); };\n";

   private ScriptFunctions() {
      engine_ = null;
   }

   /**
    * Compiles the given script body (the text after the {@code script} keyword)
    * into a ready-to-use evaluator.
    *
    * @param scriptBody the raw script text containing one or more
    *                   {@code function name(t) { … }} declarations
    * @throws IllegalArgumentException if the script contains a syntax error
    */
   public ScriptFunctions(String scriptBody) {
      ScriptEngineManager manager = new ScriptEngineManager();
      engine_ = manager.getEngineByName("JavaScript");
      if (engine_ == null) {
         throw new IllegalStateException(
               "No JavaScript engine available (expected Nashorn on JDK 8+)");
      }
      try {
         engine_.eval(MATH_SHIMS + "\n" + scriptBody);
      } catch (ScriptException e) {
         throw new IllegalArgumentException(
               "Syntax error in animation script: " + e.getMessage(), e);
      }
   }

   /**
    * Returns {@code true} if any script functions were defined.
    */
   public boolean isEmpty() {
      return engine_ == null;
   }

   /**
    * Calls the named function with a single numeric argument and returns the
    * result as a {@code double}.
    *
    * @param functionName the function to call
    * @param t            the argument (typically the current frame number)
    * @return the function's return value
    * @throws IllegalArgumentException if the function is not defined or throws
    */
   public double evaluate(String functionName, double t) {
      if (engine_ == null) {
         throw new IllegalArgumentException(
               "No script functions defined; cannot call: " + functionName);
      }
      String call = functionName + "(" + t + ")";
      Object result;
      try {
         result = engine_.eval(call);
      } catch (ScriptException e) {
         throw new IllegalArgumentException(
               "Error evaluating script function " + functionName
                     + "(" + t + "): " + e.getMessage(), e);
      }
      if (result instanceof Number) {
         return ((Number) result).doubleValue();
      }
      throw new IllegalArgumentException(
            "Script function " + functionName + " did not return a number; got: "
                  + result);
   }
}
