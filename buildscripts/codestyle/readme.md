Super-Linter is hardcoded to look for a `sun_checks.xml` file in this directory for it's Java linting rules. These rules are actually
a copy of the Google Java style guide rules that  have been modified from the 
original Google rules to match the rules specfied at https://micro-manager.org/wiki/Micro-Manager_Coding_Style_and_Conventions

The following changes have been made to the original https://github.com/checkstyle/checkstyle/blob/master/src/main/resources/google_checks.xml :
 - All properties under the "Indentation" module have been changed to be multiples of 3 rather than multiples of 2.
 - Member variable names are permitted to end with an underscore
 - Parameter names, member names, and many other name types are allowed to have only a single lowercase letter at the beginning, i.e. `xLabel`
 - All-caps abbreviations are allowed. For example, `refreshGUI` is fine and doesn't need to be changed to `refreshGui`
 
 Information about these properties can be found here: https://checkstyle.sourceforge.io/config_misc.html