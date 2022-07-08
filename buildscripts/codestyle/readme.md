The `checkstyle.xml` file is based on `google_checks.xml`.

The style settings are modified from the Google style at
https://github.com/checkstyle/checkstyle/blob/master/src/main/resources/google_checks.xml

The following changes have been made, in part to match
https://micro-manager.org/Micro-Manager_Coding_Style_and_Conventions

- `severity` changed from `warning` to `error`
- `WhitespaceAfter` token list has been changed
- `format` regex for `MemberName` changed to allow trailing `'_'`
- `format` regex for all identifiers changed to allow single lowercase letter
  at the beginning, so that, e.g., `xLabel` is allowed
- `format` regex for all identifiers changed to allow all-caps abbreviations,
  so that, e.g., `refreshGUI` is allowed (instead of requiring `refreshGui`)
- `Indentation` values have been changed to multiples of 3, rather than 2
- `NoWhitespaceBeforeCaseDefaultColon` has been removed (this might be a change
  in the upstream since we imported it)

Documentation is here: https://checkstyle.org/checks.html