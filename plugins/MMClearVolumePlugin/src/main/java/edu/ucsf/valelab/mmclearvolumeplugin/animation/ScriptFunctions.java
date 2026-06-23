package edu.ucsf.valelab.mmclearvolumeplugin.animation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Evaluates user-defined script functions declared in an animation script's
 * {@code script} block.
 *
 * <p>Functions are written in a JavaScript-like syntax that mirrors the ImageJ
 * macro math API used by the original 3Dscript language (Wan et al. 2019).
 * The following top-level names are available inside function bodies:
 * {@code PI}, {@code E}, {@code abs}, {@code sin}, {@code cos}, {@code tan},
 * {@code asin}, {@code acos}, {@code atan}, {@code atan2},
 * {@code sqrt}, {@code pow}, {@code exp}, {@code log},
 * {@code floor}, {@code ceil}, {@code round}, {@code min}, {@code max}.
 *
 * <p>This implementation uses a built-in pure-Java expression evaluator and
 * does not depend on Nashorn or any other JSR-223 script engine, so it works
 * on any JVM version.
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

   /**
    * Map from function name to the body expression string (the text of the
    * {@code return} statement, stripped of the {@code return} keyword and the
    * trailing semicolon).
    */
   private final Map<String, String> functionBodies_;

   /** Private constructor for EMPTY sentinel. */
   private ScriptFunctions() {
      functionBodies_ = null;
   }

   /**
    * Parses the given script body (the text after the {@code script} keyword)
    * and extracts all {@code function name(t) { return expr; }} declarations.
    *
    * @param scriptBody the raw script text
    * @throws IllegalArgumentException if a function body cannot be parsed
    */
   public ScriptFunctions(String scriptBody) {
      functionBodies_ = new HashMap<String, String>();
      parseFunctions(scriptBody);
   }

   /** Returns {@code true} if no script functions were defined. */
   public boolean isEmpty() {
      return functionBodies_ == null;
   }

   /**
    * Calls the named function with a single numeric argument and returns the
    * result as a {@code double}.
    *
    * @param functionName the function to call
    * @param t            the argument (typically the current frame number)
    * @return the function's return value
    * @throws IllegalArgumentException if the function is not defined or the
    *         expression cannot be evaluated
    */
   public double evaluate(String functionName, double t) {
      if (functionBodies_ == null) {
         throw new IllegalArgumentException(
               "No script functions defined; cannot call: " + functionName);
      }
      String body = functionBodies_.get(functionName);
      if (body == null) {
         throw new IllegalArgumentException(
               "Script function not defined: " + functionName);
      }
      try {
         return new Evaluator(body, t).parse();
      } catch (Exception e) {
         throw new IllegalArgumentException(
               "Error evaluating script function " + functionName
                     + "(" + t + "): " + e.getMessage(), e);
      }
   }

   // -----------------------------------------------------------------------
   // Function extraction
   // -----------------------------------------------------------------------

   /**
    * Extracts {@code function name(t) { ... }} declarations from the script
    * body. Multi-line function bodies are supported. Only the single-argument
    * form {@code function name(param) { return expr; }} is recognised.
    * Lines starting with {@code //} inside the body are treated as comments
    * and ignored.
    */
   private void parseFunctions(String scriptBody) {
      // Match: function <name> ( <param> ) { <body> }
      // The body is captured non-greedily up to the matching closing brace.
      // We use a simple brace-depth counter rather than regex for the body,
      // since regex can't match nested braces.
      Pattern funcHeader = Pattern.compile(
            "function\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\(\\s*([a-zA-Z_][a-zA-Z0-9_]*)\\s*\\)\\s*\\{",
            Pattern.MULTILINE);
      Matcher m = funcHeader.matcher(scriptBody);
      while (m.find()) {
         String name  = m.group(1);
         String param = m.group(2);
         int bodyStart = m.end();

         // Find the matching closing brace by counting depth.
         // Skip // line comments so braces inside them don't affect the count.
         int depth = 1;
         int pos = bodyStart;
         while (pos < scriptBody.length() && depth > 0) {
            char c = scriptBody.charAt(pos);
            if (c == '/' && pos + 1 < scriptBody.length()
                  && scriptBody.charAt(pos + 1) == '/') {
               // Skip to end of line.
               pos += 2;
               while (pos < scriptBody.length() && scriptBody.charAt(pos) != '\n') {
                  pos++;
               }
            } else if (c == '{') {
               depth++;
               pos++;
            } else if (c == '}') {
               depth--;
               pos++;
            } else {
               pos++;
            }
         }
         if (depth != 0) {
            throw new IllegalArgumentException(
                  "Unclosed function body for: " + name);
         }
         String rawBody = scriptBody.substring(bodyStart, pos - 1);
         String expr = extractReturnExpr(rawBody, param);
         functionBodies_.put(name, expr);
      }
   }

   /**
    * Extracts the expression from the last {@code return <expr>;} statement
    * inside a function body, replacing the declared parameter name with the
    * canonical name {@code t} so the evaluator can find it.
    */
   private static String extractReturnExpr(String body, String param) {
      // Strip comment lines.
      String stripped = body.replaceAll("(?m)^\\s*//.*$", "");
      // Also strip inline // comments.
      stripped = stripped.replaceAll("//[^\n]*", "");

      // Find the last "return <expr> ;" (semicolon optional).
      Pattern ret = Pattern.compile(
            "\\breturn\\b([^;]+);?",
            Pattern.DOTALL | Pattern.MULTILINE);
      Matcher m = ret.matcher(stripped);
      String expr = null;
      while (m.find()) {
         expr = m.group(1).trim();
      }
      if (expr == null || expr.isEmpty()) {
         throw new IllegalArgumentException(
               "No return statement found in function body: " + body.trim());
      }

      // Also strip "var xxx = ..." declarations that may precede return.
      // Variables other than the parameter are pre-evaluated by inlining.
      // For simplicity, expand single-assignment var declarations.
      expr = inlineVarDeclarations(stripped, expr, param);

      // Substitute the declared parameter name for the canonical name "t"
      // so the evaluator always looks up "t".
      if (!param.equals("t")) {
         expr = expr.replaceAll("\\b" + Pattern.quote(param) + "\\b", "t");
      }
      return expr;
   }

   /**
    * Substitutes simple {@code var name = expr;} declarations that appear
    * before the return statement into the return expression, so the evaluator
    * sees a single self-contained expression.
    */
   private static String inlineVarDeclarations(String body, String returnExpr,
                                               String param) {
      // Match: var <name> = <value> ;
      Pattern varDecl = Pattern.compile(
            "\\bvar\\s+([a-zA-Z_][a-zA-Z0-9_]*)\\s*=\\s*([^;]+);",
            Pattern.MULTILINE);
      // Collect all var declarations.
      Map<String, String> vars = new HashMap<String, String>();
      Matcher m = varDecl.matcher(body);
      while (m.find()) {
         vars.put(m.group(1), m.group(2).trim());
      }
      // Substitute each var (that is not the parameter) into the expression.
      String result = returnExpr;
      for (Map.Entry<String, String> entry : vars.entrySet()) {
         String varName = entry.getKey();
         if (!varName.equals(param)) {
            result = result.replaceAll("\\b" + Pattern.quote(varName) + "\\b",
                  "(" + entry.getValue() + ")");
         }
      }
      return result;
   }

   // -----------------------------------------------------------------------
   // Expression evaluator (recursive-descent)
   // -----------------------------------------------------------------------

   /**
    * Evaluates a single math expression string with one free variable {@code t}.
    *
    * <p>Supported syntax:
    * <ul>
    *   <li>Numeric literals (integer and floating-point)</li>
    *   <li>The variable {@code t}</li>
    *   <li>Named constants: {@code PI}, {@code E}</li>
    *   <li>Unary minus</li>
    *   <li>Binary operators: {@code +}, {@code -}, {@code *}, {@code /},
    *       {@code %}</li>
    *   <li>Standard math functions: {@code abs}, {@code sin}, {@code cos},
    *       {@code tan}, {@code asin}, {@code acos}, {@code atan},
    *       {@code atan2}, {@code sqrt}, {@code pow}, {@code exp},
    *       {@code log}, {@code floor}, {@code ceil}, {@code round},
    *       {@code min}, {@code max}</li>
    *   <li>Parenthesised sub-expressions</li>
    * </ul>
    */
   private static final class Evaluator {
      private final String expr_;
      private final double t_;
      private int pos_ = 0;

      Evaluator(String expr, double t) {
         expr_ = expr.trim();
         t_ = t;
      }

      double parse() {
         double result = parseExpr();
         skipWhitespace();
         if (pos_ < expr_.length()) {
            throw new IllegalArgumentException(
                  "Unexpected character '" + expr_.charAt(pos_)
                        + "' at position " + pos_ + " in: " + expr_);
         }
         return result;
      }

      // expr = term (('+' | '-') term)*
      private double parseExpr() {
         double v = parseTerm();
         skipWhitespace();
         while (pos_ < expr_.length()) {
            char c = expr_.charAt(pos_);
            if (c == '+') {
               pos_++;
               v += parseTerm();
            } else if (c == '-') {
               pos_++;
               v -= parseTerm();
            } else {
               break;
            }
            skipWhitespace();
         }
         return v;
      }

      // term = unary (('*' | '/' | '%') unary)*
      private double parseTerm() {
         double v = parseUnary();
         skipWhitespace();
         while (pos_ < expr_.length()) {
            char c = expr_.charAt(pos_);
            if (c == '*') {
               pos_++;
               v *= parseUnary();
            } else if (c == '/') {
               pos_++;
               v /= parseUnary();
            } else if (c == '%') {
               pos_++;
               v %= parseUnary();
            } else {
               break;
            }
            skipWhitespace();
         }
         return v;
      }

      // unary = '-' unary | primary
      private double parseUnary() {
         skipWhitespace();
         if (pos_ < expr_.length() && expr_.charAt(pos_) == '-') {
            pos_++;
            return -parseUnary();
         }
         return parsePrimary();
      }

      // primary = number | 't' | constant | func '(' args ')' | '(' expr ')'
      private double parsePrimary() {
         skipWhitespace();
         if (pos_ >= expr_.length()) {
            throw new IllegalArgumentException("Unexpected end of expression: " + expr_);
         }
         char c = expr_.charAt(pos_);

         // Parenthesised sub-expression.
         if (c == '(') {
            pos_++;
            double v = parseExpr();
            skipWhitespace();
            expect(')');
            return v;
         }

         // Number literal.
         if (Character.isDigit(c) || c == '.') {
            return parseNumber();
         }

         // Identifier: variable 't', constant, or function call.
         if (Character.isLetter(c) || c == '_') {
            String name = parseIdentifier();
            skipWhitespace();
            // Function call?
            if (pos_ < expr_.length() && expr_.charAt(pos_) == '(') {
               return callFunction(name);
            }
            // Named constant or variable.
            return resolveIdentifier(name);
         }

         throw new IllegalArgumentException(
               "Unexpected character '" + c + "' at position " + pos_
                     + " in: " + expr_);
      }

      private double parseNumber() {
         int start = pos_;
         while (pos_ < expr_.length()
               && (Character.isDigit(expr_.charAt(pos_))
                   || expr_.charAt(pos_) == '.'
                   || expr_.charAt(pos_) == 'e'
                   || expr_.charAt(pos_) == 'E'
                   || ((expr_.charAt(pos_) == '+' || expr_.charAt(pos_) == '-')
                       && pos_ > start
                       && (expr_.charAt(pos_ - 1) == 'e'
                           || expr_.charAt(pos_ - 1) == 'E')))) {
            pos_++;
         }
         return Double.parseDouble(expr_.substring(start, pos_));
      }

      private String parseIdentifier() {
         int start = pos_;
         while (pos_ < expr_.length()
               && (Character.isLetterOrDigit(expr_.charAt(pos_))
                   || expr_.charAt(pos_) == '_')) {
            pos_++;
         }
         return expr_.substring(start, pos_);
      }

      private double resolveIdentifier(String name) {
         if ("t".equals(name)) {
            return t_;
         }
         if ("PI".equals(name)) {
            return Math.PI;
         }
         if ("E".equals(name))  {
            return Math.E;
         }
         throw new IllegalArgumentException("Unknown identifier: " + name);
      }

      private double callFunction(String name) {
         expect('(');
         // Parse argument list (one or two args).
         List<Double> args = new ArrayList<Double>();
         skipWhitespace();
         if (pos_ < expr_.length() && expr_.charAt(pos_) != ')') {
            args.add(parseExpr());
            skipWhitespace();
            while (pos_ < expr_.length() && expr_.charAt(pos_) == ',') {
               pos_++;
               args.add(parseExpr());
               skipWhitespace();
            }
         }
         expect(')');

         double a = args.isEmpty() ? 0.0 : args.get(0);
         double b = args.size() < 2 ? 0.0 : args.get(1);

         switch (name) {
            case "abs":   return Math.abs(a);
            case "sin":   return Math.sin(a);
            case "cos":   return Math.cos(a);
            case "tan":   return Math.tan(a);
            case "asin":  return Math.asin(a);
            case "acos":  return Math.acos(a);
            case "atan":  return Math.atan(a);
            case "atan2": return Math.atan2(a, b);
            case "sqrt":  return Math.sqrt(a);
            case "pow":   return Math.pow(a, b);
            case "exp":   return Math.exp(a);
            case "log":   return Math.log(a);
            case "floor": return Math.floor(a);
            case "ceil":  return Math.ceil(a);
            case "round": return Math.round(a);
            case "min":   return Math.min(a, b);
            case "max":   return Math.max(a, b);
            default:
               throw new IllegalArgumentException("Unknown function: " + name);
         }
      }

      private void skipWhitespace() {
         while (pos_ < expr_.length()
               && Character.isWhitespace(expr_.charAt(pos_))) {
            pos_++;
         }
      }

      private void expect(char expected) {
         skipWhitespace();
         if (pos_ >= expr_.length() || expr_.charAt(pos_) != expected) {
            throw new IllegalArgumentException(
                  "Expected '" + expected + "' at position " + pos_
                        + " in: " + expr_);
         }
         pos_++;
      }
   }
}
