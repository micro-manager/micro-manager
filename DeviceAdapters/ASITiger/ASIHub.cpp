///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIHub.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI serial communication class (generic "hub")
//
// COPYRIGHT:     Applied Scientific Instrumentation, Eugene OR
//
// LICENSE:       This file is distributed under the BSD license.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Jon Daniels (jon@asiimaging.com) 09/2013
//
// BASED ON:      ASIStage.cpp
//
//

#ifdef WIN32
#define snprintf _snprintf 
#pragma warning(disable: 4355)
#endif

#include "ASITiger.h"
#include "ASIDevice.h"
#include "ASIHub.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include <string>


using namespace std;

///////////////////////////////////////////////////////////////////////////////
// ASIHub implementation
// this implements serial communication, and could be used as parent class
//   for future hubs besides TigerComm
ASIHub::ASIHub() :
      ASIDevice(this,""),  // don't pass a name
      port_("Undefined"),
      serialAnswer_(""),
      serialCommand_(""),
      serialTerminator_(g_SerialTerminatorDefault)
{
   CPropertyAction* pAct = new CPropertyAction(this, &ASIHub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // property to allow sending arbitrary serial commands and receiving response
   pAct = new CPropertyAction (this, &ASIHub::OnSerialTerminator);
   CreateProperty(g_SerialTerminatorPropertyName, g_SerialTerminator_0, MM::String, false, pAct);
   AddAllowedValue(g_SerialTerminatorPropertyName, g_SerialTerminator_0);
   AddAllowedValue(g_SerialTerminatorPropertyName, g_SerialTerminator_1);
   AddAllowedValue(g_SerialTerminatorPropertyName, g_SerialTerminator_2);
   AddAllowedValue(g_SerialTerminatorPropertyName, g_SerialTerminator_3);
   AddAllowedValue(g_SerialTerminatorPropertyName, g_SerialTerminator_4);
   pAct = new CPropertyAction (this, &ASIHub::OnSerialCommand);
   CreateProperty(g_SerialCommandPropertyName, "", MM::String, false, pAct);
   // this is only changed programmatically, never by user
   CreateProperty(g_SerialResponsePropertyName, "", MM::String, false);

   hub_ = this;
}

ASIHub::~ASIHub()
{
}


int ASIHub::ClearComPort(void)
{
   return PurgeComPort(port_.c_str());
}

int ASIHub::SendCommand(const char *command)
{
   // had problems with repeated calls to this that were solved by doing QueryCommand instead
   RETURN_ON_MM_ERROR ( ClearComPort() );
   RETURN_ON_MM_ERROR ( SendSerialCommand(port_.c_str(), command, "\r") );
   serialCommand_ = command;
   // if in debug mode then echo serialCommand_ to log file
   LogMessage("SerialCommand:\t" + serialCommand_, true);
   return DEVICE_OK;
}

int ASIHub::QueryCommand(const char *command, const char *replyTerminator)
{
   RETURN_ON_MM_ERROR ( ClearComPort() );
   RETURN_ON_MM_ERROR ( SendSerialCommand(port_.c_str(), command, "\r") );
   serialCommand_ = command;
   // if in debug mode then echo serialCommand_ to log file
   LogMessage("SerialCommand:\t" + serialCommand_, true);
   RETURN_ON_MM_ERROR ( GetSerialAnswer(port_.c_str(), replyTerminator, serialAnswer_) );
   // if in debug mode then echo serialCommand_ to log file
   LogMessage("SerialReponse:\t" + serialAnswer_, true);
   return DEVICE_OK;
}

int ASIHub::QueryCommandVerify(const char *command, const char *expectedReplyPrefix, const char *replyTerminator)
{
   RETURN_ON_MM_ERROR ( QueryCommand(command, replyTerminator) );
   // if doesn't match expected prefix, then look for ASI error code
   if (serialAnswer_.substr(0, strlen(expectedReplyPrefix)).compare(expectedReplyPrefix) != 0)
   {
      int errNo = ParseErrorReply();
      return errNo;
   }
   return DEVICE_OK;
}

int ASIHub::ParseErrorReply() const
{
   if (serialAnswer_.substr(0, 2).compare(":N") == 0 && serialAnswer_.length() > 2)
   {
      int errNo = atoi(serialAnswer_.substr(3).c_str());
      return ERR_ASICODE_OFFSET + errNo;
    }
    return ERR_UNRECOGNIZED_ANSWER;
}

double ASIHub::ParseAnswerAfterEquals() const
{
   return atof(serialAnswer_.substr(serialAnswer_.find("=")+1).c_str());
}

double ASIHub::ParseAnswerAfterColon() const
{
   return atof(serialAnswer_.substr(serialAnswer_.find(":")+1).c_str());
}

double ASIHub::ParseAnswerAfterPosition(unsigned int pos) const
{
   // specify position as 3 to parse skipping the first 3 characters, e.g. for ":A 45.1"
   return atof(serialAnswer_.substr(pos).c_str());
}

vector<string> ASIHub::SplitAnswerOnDelim(string delim) const
{
   vector<string> elems;
   CDeviceUtils::Tokenize(serialAnswer_, elems, delim);
   return elems;
}

int ASIHub::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_) // don't let user change after initialization
      {
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }
      else
         pProp->Get(port_);
   }
   return DEVICE_OK;
}

int ASIHub::OnSerialTerminator(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      bool success = 0;
      if (serialTerminator_.compare(g_SerialTerminator_0_Value) == 0)
         success = pProp->Set(g_SerialTerminator_0);
      else if (serialTerminator_.compare(g_SerialTerminator_1_Value) == 0)
         success = pProp->Set(g_SerialTerminator_1);
      else if (serialTerminator_.compare(g_SerialTerminator_2_Value) == 0)
         success = pProp->Set(g_SerialTerminator_2);
      else if (serialTerminator_.compare(g_SerialTerminator_3_Value) == 0)
         success = pProp->Set(g_SerialTerminator_3);
      else if (serialTerminator_.compare(g_SerialTerminator_4_Value) == 0)
         success = pProp->Set(g_SerialTerminator_4);
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_SerialTerminator_0) == 0)
         serialTerminator_ = g_SerialTerminator_0_Value;
      else if (tmpstr.compare(g_SerialTerminator_1) == 0)
         serialTerminator_ = g_SerialTerminator_1_Value;
      else if (tmpstr.compare(g_SerialTerminator_2) == 0)
         serialTerminator_ = g_SerialTerminator_2_Value;
      else if (tmpstr.compare(g_SerialTerminator_3) == 0)
         serialTerminator_ = g_SerialTerminator_3_Value;
      else if (tmpstr.compare(g_SerialTerminator_4) == 0)
         serialTerminator_ = g_SerialTerminator_4_Value;
      else
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   return DEVICE_OK;
}

int ASIHub::OnSerialCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // do nothing
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      tmpstr =   UnescapeControlCharacters(tmpstr);
      RETURN_ON_MM_ERROR ( QueryCommand(tmpstr, serialTerminator_) );
      SetProperty(g_SerialResponsePropertyName, EscapeControlCharacters(LastSerialAnswer()).c_str());
   }
   return DEVICE_OK;
}

string ASIHub::EscapeControlCharacters(const string v ) const
// based on similar function in FreeSerialPort.cpp
{
   ostringstream mess;  mess.str("");
   for( string::const_iterator ii = v.begin(); ii != v.end(); ++ii)
   {
      if (*ii > 31)
         mess << *ii;
      else if (*ii == 13)
         mess << "\\r";
      else if (*ii == 10)
         mess << "\\n";
      else if (*ii == 9)
         mess << "\\t";
      else
         mess << "\\" << (unsigned int)(*ii);
   }
   return mess.str();
}

string ASIHub::UnescapeControlCharacters(const string v0 ) const
// based on similar function in FreeSerialPort.cpp
{
   // the string input from the GUI can contain escaped control characters, currently these are always preceded with \ (0x5C)
   // and always assumed to be decimal or C style, not hex

   string detokenized;
   string v = v0;

   for( string::iterator jj = v.begin(); jj != v.end(); ++jj)
   {
      bool breakNow = false;
      if( '\\' == *jj )
      {
         // the next 1 to 3 characters might be converted into a control character
         ++jj;
         if( v.end() == jj)
         {
            // there was an escape at the very end of the input string so output it literally
            detokenized.push_back('\\');
            break;
         }
         const string::iterator nextAfterEscape = jj;
         std::string thisControlCharacter;
         // take any decimal digits immediately after the escape character and convert to a control character
         while(0x2F < *jj && *jj < 0x3A )
         {
            thisControlCharacter.push_back(*jj++);
            if( v.end() == jj)
            {
               breakNow = true;
               break;
            }
         }
         int code = -1;
         if ( 0 < thisControlCharacter.length())
         {
            istringstream tmp(thisControlCharacter);
            tmp >> code;
         }
         // otherwise, if we are still at the first character after the escape,
         // possibly treat the next character like a 'C' control character
         if( nextAfterEscape == jj)
         {
            switch( *jj)
            {
            case 'r':
               ++jj;
               code = 13; // CR \r
               break;
            case 'n':
               ++jj;
               code = 10; // LF \n
               break;
            case 't':
               ++jj;
               code = 9; // TAB \t
               break;
            case '\\':
               ++jj;
               code = '\\';
               break;
            default:
               code = '\\'; // the '\' wasn't really an escape character....
               break;
            }
            if( v.end() == jj)
               breakNow = true;
         }
         if( -1 < code)
            detokenized.push_back((char)code);
      }
      if( breakNow)
         break;
      detokenized.push_back(*jj);
   }
   return detokenized;

}

