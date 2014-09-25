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


// Make sure to add message to UserDefSerialBase::RegisterErrorMessages() when
// adding a new code.
const int ERR_BINARY_SERIAL_TIMEOUT = 107; // Use the famous SerialManager code
const int ERR_UNEXPECTED_RESPONSE = 2001;
const int ERR_QUERY_COMMAND_EMPTY = 2002;
const int ERR_ASCII_COMMAND_CONTAINS_NULL = 2003;
const int ERR_TRAILING_BACKSLASH = 2004;
const int ERR_UNKNOWN_ESCAPE_SEQUENCE = 2005;
const int ERR_EMPTY_HEX_ESCAPE_SEQUENCE = 2006;
const int ERR_CANNOT_GET_PORT_TIMEOUT = 2007;
const int ERR_CANNOT_QUERY_IN_IGNORE_MODE = 2008;
const int ERR_EXPECTED_RESPONSE_LENGTH_MISMATCH = 2009;
const int ERR_NO_RESPONSE_ALTERNATIVES = 2010;
const int ERR_VAR_LEN_RESPONSE_MUST_NOT_BE_EMPTY = 2011;


const char* const g_DeviceName_GenericDevice = "UserDefinedGenericDevice";
const char* const g_DeviceName_Shutter = "UserDefinedShutter";
const char* const g_DeviceName_StateDevice = "UserDefinedStateDevice";


const char* const g_PropName_CommandSendMode = "Command mode";
const char* const g_PropName_ResponseDetectionMethod = "Response detection";

const char* const g_PropName_InitializeCommand = "Initialize-command";
const char* const g_PropName_InitializeResponse = "Initialize-response";
const char* const g_PropName_ShutdownCommand = "Shutdown-command";
const char* const g_PropName_ShutdownResponse = "Shutdown-response";

const char* const g_PropName_OpenCommand = "Open-command";
const char* const g_PropName_OpenResponse = "Open-response";
const char* const g_PropName_CloseCommand = "Close-command";
const char* const g_PropName_CloseResponse = "Close-response";
const char* const g_PropName_QueryStateCommand = "QueryState-command";
const char* const g_PropName_QueryOpenResponse = "QueryState-open-response";
const char* const g_PropName_QueryCloseResponse =
   "QueryState-closed-response";

const char* const g_PropName_NumPositions = "Number of positions";
const char* const g_PropNamePrefix_SetPositionCommand = "SetPosition-command-";
const char* const g_PropNamePrefix_SetPositionResponse = "SetPosition-response-";
const char* const g_PropName_QueryPositionCommand = "QueryPosition-command";
const char* const g_PropNamePrefix_QueryPositionResponse =
   "QueryPosition-response-";

const char* const g_PropValue_ASCII_NoTerminator = "ASCII-no-terminator";
const char* const g_PropValue_ASCII_CRLF = "ASCII-CRLF-terminator";
const char* const g_PropValue_ASCII_CR = "ASCII-CR-terminator";
const char* const g_PropValue_ASCII_LF = "ASCII-LF-terminator";
const char* const g_PropValue_Binary = "Binary";

const char* const g_PropValue_ResponseIgnore = "Ignore responses";
const char* const g_PropValuePrefix_ResponseTerminated = "Terminator-";
const char* const g_PropValue_ResponseCRLFTerminated = "Terminator-CRLF";
const char* const g_PropValue_ResponseCRTerminated = "Terminator-CR";
const char* const g_PropValue_ResponseLFTerminated = "Terminator-LF";
const char* const g_PropValuePrefix_ResponseFixedByteCount =
   "Fixed-length binary-";
const char* const g_PropValue_ResponseVariableByteCount =
   "Variable-length binary";
