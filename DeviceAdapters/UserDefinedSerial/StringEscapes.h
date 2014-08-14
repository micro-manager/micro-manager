// DESCRIPTION:   Control devices using user-specified serial commands
//
// COPYRIGHT:     University of California San Francisco, 2014
//
// LICENSE:       This file is distributed under the BSD license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Mark Tsuchida

#pragma once

#include <string>
#include <vector>


/**
 * \brief Convert bytes to C-style escaped string.
 *
 * The format mostly conforms to C/C++ string escape sequences, with one
 * exception: it is assumed that hex escapes (backslash followed by x followed
 * by hex digits) are limited to two hex digits (in C strings, consecuitive hex
 * digits become part of the escape sequence until a non-hex-ditig is
 * encountered).
 *
 * For code points that have human-friendly escape sequences (newlines, tabs,
 * etc.), only CR and LF are converted to that form; all other control
 * characters are converted to two-digit hex escapes.
 *
 * As a special exception, the comma is converted to a hex escape (backslash-
 * x2c), so as not to violate Micro-Manager's rules for property values.
 *
 * Hex escapes are emitted in lower case.
 */
std::string EscapedStringFromByteString(const std::vector<char>& bytes);

/**
 * \brief Convert C-style escaped string to bytes.
 *
 * Most C/C++ string escape sequences are accepted according to the spec, with
 * two differences: Unicode escapes (backslash followed by U or u) are not
 * allowed, and hex escapes (backslash followed by x followed by hex digits)
 * are limited to 2 hex digits.
 *
 * Standard C/C++ string hex escapes specify that hex digits are read until a
 * non-hex-digit is encountered. This implementation, in contrast, concludes
 * the hex escape after at most two digits, even if the next character is a
 * valid hex digit. This decision was made so that arbitrary byte values could
 * be represented in a GUI text field, without having to escape all subsequent
 * characters.
 *
 * \return error code if an unknown or incomplete escape sequence is
 * encountered.
 */
int ByteStringFromEscapedString(const std::string& escaped,
      std::vector<char>& bytes);
