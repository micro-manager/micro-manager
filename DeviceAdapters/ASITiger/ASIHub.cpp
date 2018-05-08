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


#include "ASITiger.h"
#include "ASIHub.h"
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include "../../MMDevice/DeviceThreads.h"
#include <algorithm>
#include <string>
#include <vector>


using namespace std;

///////////////////////////////////////////////////////////////////////////////
// ASIHub implementation
// this implements serial communication, and could be used as parent class
//   for future hubs besides TigerComm
ASIHub::ASIHub() :
      ASIBase< ::HubBase, ASIHub >(""), // do not pass a name
      port_("Undefined"),
      serialAnswer_(""),
      manualSerialAnswer_(""),
      serialCommand_(""),
      serialTerminator_(g_SerialTerminatorDefault),
      serialRepeatDuration_(0),
      serialRepeatPeriod_(500),
      serialOnlySendChanged_(true),
      updatingSharedProperties_(false)
{
   CPropertyAction* pAct = new CPropertyAction(this, &ASIHub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

   // property to allow sending arbitrary serial commands and receiving response
   pAct = new CPropertyAction (this, &ASIHub::OnSerialCommand);
   CreateProperty(g_SerialCommandPropertyName, "", MM::String, false, pAct);

   // this is only changed programmatically, never by user
   // contains last response to the OnSerialCommand action
   pAct = new CPropertyAction (this, &ASIHub::OnSerialResponse);
   CreateProperty(g_SerialResponsePropertyName, "", MM::String, true, pAct);

   // property to allow repeated sending of same command; 0 disables repeat sending
   pAct = new CPropertyAction (this, &ASIHub::OnSerialCommandRepeatDuration);
   CreateProperty(g_SerialCommandRepeatDurationPropertyName, "0", MM::Integer, false, pAct);

   // how often to send the same command
   pAct = new CPropertyAction (this, &ASIHub::OnSerialCommandRepeatPeriod);
   CreateProperty(g_SerialCommandRepeatPeriodPropertyName, "500", MM::Integer, false, pAct);

   // disable sending serial commands unless changed by default
   pAct = new CPropertyAction (this, &ASIHub::OnSerialCommandOnlySendChanged);
   CreateProperty(g_SerialCommandOnlySendChangedPropertyName, g_YesState, MM::String, false, pAct);
   AddAllowedValue(g_SerialCommandOnlySendChangedPropertyName, g_NoState);
   AddAllowedValue(g_SerialCommandOnlySendChangedPropertyName, g_YesState);


   // change serial terminator, mainly useful for direct communication with filter wheel card
   pAct = new CPropertyAction (this, &ASIHub::OnSerialTerminator);
   CreateProperty(g_SerialTerminatorPropertyName, g_SerialTerminator_0, MM::String, false, pAct);
   AddAllowedValue(g_SerialTerminatorPropertyName, g_SerialTerminator_0);
   AddAllowedValue(g_SerialTerminatorPropertyName, g_SerialTerminator_1);
   AddAllowedValue(g_SerialTerminatorPropertyName, g_SerialTerminator_2);
   AddAllowedValue(g_SerialTerminatorPropertyName, g_SerialTerminator_3);
   AddAllowedValue(g_SerialTerminatorPropertyName, g_SerialTerminator_4);
}

int ASIHub::ClearComPort(void)
{
   return PurgeComPort(port_.c_str());
}

/**
   * Sends a command and gets the serial buffer (doesn't try to verify end of transmission)
   */
int ASIHub::QueryCommandUnterminatedResponse(const char *command, const long timeoutMs)
{
   RETURN_ON_MM_ERROR ( ClearComPort() );
   RETURN_ON_MM_ERROR ( SendSerialCommand(port_.c_str(), command, "\r") );
   serialCommand_ = command;
   char rcvBuf[MM::MaxStrLength];
   memset(rcvBuf, 0, MM::MaxStrLength);
   unsigned long read = 0;
   int ret = DEVICE_OK;
   MM::TimeoutMs timerOut(GetCurrentMMTime(), timeoutMs);
   serialAnswer_ = "";
   while (ret == DEVICE_OK && read == 0 && !timerOut.expired(GetCurrentMMTime()))
   {
      ret = ReadFromComPort(port_.c_str(), (unsigned char*)rcvBuf, MM::MaxStrLength, read);
   }
   if (read > 0)
   {
      serialAnswer_ = rcvBuf;
   }
   return ret;
}

//something like this could be used to get the reply to INFO command where the reply is
//   longer than 1024 characters.  Even this seems to have a problem with the end of the
//   INFO reply because the serial buffer appears to only get the first 1023 characters
//   of the controller's reply.
// instead we simply enforce that we don't send the info command
//int ASIHub::QueryCommandLongReply(const char *command)
//{
//   RETURN_ON_MM_ERROR ( ClearComPort() );
//   RETURN_ON_MM_ERROR ( SendSerialCommand(port_.c_str(), command, "\r") );
//   serialCommand_ = command;
//   string lastLine = "";
//   serialAnswer_ = "";
//   int lastErr = DEVICE_OK;
//   while (lastErr == DEVICE_OK)
//   {
//      lastLine = "";
//      lastErr = GetSerialAnswer(port_.c_str(), "\r", lastLine);
//      CDeviceUtils::SleepMs(1);
//      serialAnswer_ += (lastLine + "\r");
//   }
//   return DEVICE_OK;
//}

int ASIHub::QueryCommand(const char *command, const char *replyTerminator, const long delayMs)
{
   MMThreadGuard g(threadLock_);
   RETURN_ON_MM_ERROR ( ClearComPort() );
   RETURN_ON_MM_ERROR ( SendSerialCommand(port_.c_str(), command, "\r") );
   serialCommand_ = command;
   if (delayMs >= 0)  CDeviceUtils::SleepMs(delayMs);
   RETURN_ON_MM_ERROR ( GetSerialAnswer(port_.c_str(), replyTerminator, serialAnswer_) );
   return DEVICE_OK;
}

int ASIHub::QueryCommandVerify(const char *command, const char *expectedReplyPrefix, const char *replyTerminator, const long delayMs)
{
   RETURN_ON_MM_ERROR ( QueryCommand(command, replyTerminator, delayMs) );
   // if doesn't match expected prefix, then look for ASI error code
   size_t len = strlen(expectedReplyPrefix);
   if (serialAnswer_.length() < len ||
         serialAnswer_.substr(0, len).compare(expectedReplyPrefix) != 0)
   {
      return ParseErrorReply();
   }
   return DEVICE_OK;
}

int ASIHub::ParseErrorReply() const
{
   if (serialAnswer_.length() > 3 && serialAnswer_.substr(0, 2).compare(":N") == 0)
   {
      int errNo = atoi(serialAnswer_.substr(3).c_str());
      return ERR_ASICODE_OFFSET + errNo;
    }
    return ERR_UNRECOGNIZED_ANSWER;
}

int ASIHub::ParseAnswerAfterEquals(double &val)
{
   size_t pos = serialAnswer_.find("=");
   if ((pos == string::npos) || ((pos+1) >= serialAnswer_.length()))
   {
      return ERR_UNRECOGNIZED_ANSWER;
   }
   val = atof(serialAnswer_.substr(serialAnswer_.find("=")+1).c_str());
   return DEVICE_OK;
}

int ASIHub::ParseAnswerAfterEquals(long &val)
{
   size_t pos = serialAnswer_.find("=");
   if ((pos == string::npos) || ((pos+1) >= serialAnswer_.length()))
   {
      return ERR_UNRECOGNIZED_ANSWER;
   }
   val = atol(serialAnswer_.substr(serialAnswer_.find("=")+1).c_str());
   return DEVICE_OK;
}

int ASIHub::ParseAnswerAfterEquals(unsigned int &val)
{
   size_t pos = serialAnswer_.find("=");
   if ((pos == string::npos) || ((pos+1) >= serialAnswer_.length()))
   {
      return ERR_UNRECOGNIZED_ANSWER;
   }
   val = atol(serialAnswer_.substr(serialAnswer_.find("=")+1).c_str());
   return DEVICE_OK;
}

int ASIHub::ParseAnswerAfterUnderscore(unsigned int &val)
{
   size_t pos = serialAnswer_.find("_");
   if ((pos == string::npos) || ((pos+1) >= serialAnswer_.length()))
   {
      return ERR_UNRECOGNIZED_ANSWER;
   }
   val = atol(serialAnswer_.substr(serialAnswer_.find("_")+1).c_str());
   return DEVICE_OK;
}

int ASIHub::ParseAnswerAfterColon(double &val)
{
   size_t pos = serialAnswer_.find(":");
   if ((pos == string::npos) || ((pos+1) >= serialAnswer_.length()))
   {
      return ERR_UNRECOGNIZED_ANSWER;
   }
   val = atof(serialAnswer_.substr(serialAnswer_.find(":")+1).c_str());
   return DEVICE_OK;
}

int ASIHub::ParseAnswerAfterColon(long& val)
{
   size_t pos = serialAnswer_.find(":");
   if ((pos == string::npos) || ((pos+1) >= serialAnswer_.length()))
   {
      return ERR_UNRECOGNIZED_ANSWER;
   }
   val = atol(serialAnswer_.substr(serialAnswer_.find(":")+1).c_str());
   return DEVICE_OK;
}

int ASIHub::ParseAnswerAfterPosition(unsigned int pos, double &val)
{
   // specify position as 3 to parse skipping the first 3 characters, e.g. for ":A 45.1"
   if (pos >= serialAnswer_.length())
   {
      return ERR_UNRECOGNIZED_ANSWER;
   }
   val = atof(serialAnswer_.substr(pos).c_str());
   return DEVICE_OK;
}

int ASIHub::ParseAnswerAfterPosition(unsigned int pos, long &val)
{
   // specify position as 3 to parse skipping the first 3 characters, e.g. for ":A 45.1"
   if (pos >= serialAnswer_.length())
   {
      return ERR_UNRECOGNIZED_ANSWER;
   }
   val = atol(serialAnswer_.substr(pos).c_str());
   return DEVICE_OK;
}

int ASIHub::ParseAnswerAfterPosition(unsigned int pos, unsigned int &val)
{
   // specify position as 3 to parse skipping the first 3 characters, e.g. for ":A 45.1"
   if (pos >= serialAnswer_.length())
   {
      return ERR_UNRECOGNIZED_ANSWER;
   }
   val = (unsigned int)atol(serialAnswer_.substr(pos).c_str());
   return DEVICE_OK;
}

int ASIHub::ParseAnswerAfterPosition2(double &val)
{
   if (2 >= serialAnswer_.length())
   {
      return ERR_UNRECOGNIZED_ANSWER;
   }
   val = atof(serialAnswer_.substr(2).c_str());
   return DEVICE_OK;
}

int ASIHub::ParseAnswerAfterPosition2(long &val)
{
   if (2 >= serialAnswer_.length())
   {
      return ERR_UNRECOGNIZED_ANSWER;
   }
   val = atol(serialAnswer_.substr(2).c_str());
   return DEVICE_OK;
}

int ASIHub::ParseAnswerAfterPosition2(unsigned int &val)
{
   if (2 >= serialAnswer_.length())
   {
      return ERR_UNRECOGNIZED_ANSWER;
   }
   val = (unsigned int)atol(serialAnswer_.substr(2).c_str());
   return DEVICE_OK;
}

int ASIHub::ParseAnswerAfterPosition3(double &val)
{
   if (3 >= serialAnswer_.length())
   {
      return ERR_UNRECOGNIZED_ANSWER;
   }
   val = atof(serialAnswer_.substr(3).c_str());
   return DEVICE_OK;
}

int ASIHub::ParseAnswerAfterPosition3(long &val)
{
   if (3 >= serialAnswer_.length())
   {
      return ERR_UNRECOGNIZED_ANSWER;
   }
   val = atol(serialAnswer_.substr(3).c_str());
   return DEVICE_OK;
}

int ASIHub::ParseAnswerAfterPosition3(unsigned int &val)
{
   if (3 >= serialAnswer_.length())
   {
      return ERR_UNRECOGNIZED_ANSWER;
   }
   val = (unsigned int)atol(serialAnswer_.substr(3).c_str());
   return DEVICE_OK;
}

int ASIHub::GetAnswerCharAtPosition(unsigned int pos, char &val)
{
   if (pos >= serialAnswer_.length())
   {
      return ERR_UNRECOGNIZED_ANSWER;
   }
   val = serialAnswer_.at(pos);
   return DEVICE_OK;
}

int ASIHub::GetAnswerCharAtPosition3(char &val)
{
   if (3 >= serialAnswer_.length())
   {
      return ERR_UNRECOGNIZED_ANSWER;
   }
   val = serialAnswer_.at(3);
   return DEVICE_OK;
}

vector<string> ASIHub::SplitAnswerOnDelim(string delim) const
{
   vector<string> elems;
   CDeviceUtils::Tokenize(serialAnswer_, elems, delim);
   return elems;
}

int ASIHub::GetBuildInfo(const string addressLetter, build_info_type &build)
// original use is in DetectInstalledDevices addressed to comm card
// refactored as separate function for use to query other cards, including defines
{
   ostringstream command; command.str("");
   command << addressLetter << "BU X";
   RETURN_ON_MM_ERROR ( QueryCommand(command.str()) );

   // get the axis letters, types, and addresses from BU X reply
   //   example reply:
   //      TIGER_COMM<CR>
   //      Motor Axes: Z F P Q R S X Y V W<CR>
   //      Axis Types: p p u u u u x x z z<CR>
   //      Axis Addr: 1 2 3 3 3 3 4 4 5 5<CR>
   //      Axis Props:   1   0   0   0   0   0   0   1   0<CR>

   // parse the reply into vectors containing axis types, letters, and card addresses (binary and hex)
   vector<string> vReply = SplitAnswerOnCR();

   // get buildname
   build.buildname = vReply[0];

   // get axis letters "Motor Axes:"
   SetLastSerialAnswer(vReply[1]);
   vector<string> vAxesLetter = SplitAnswerOnSpace();
   if (vAxesLetter.size() < 3)
      return ERR_NOT_ENOUGH_AXES;
   vAxesLetter.erase(vAxesLetter.begin()); vAxesLetter.erase(vAxesLetter.begin()); // remove "Motor Axes:"
   build.numAxes = (unsigned char)vAxesLetter.size();
   build.vAxesLetter = ConvertStringVector2CharVector(vAxesLetter);
   // make everything upper case
   for(unsigned int i=0; i<build.numAxes; i++)
   {
      build.vAxesLetter[i] = (char)toupper((int)build.vAxesLetter[i]);
   }

   // get axis types "Axis Types:"
   SetLastSerialAnswer(vReply[2]);
   vector<string> vAxesType = SplitAnswerOnSpace();
   vAxesType.erase(vAxesType.begin()); vAxesType.erase(vAxesType.begin()); // remove "Axis Types:"
   build.vAxesType = ConvertStringVector2CharVector(vAxesType);

   SetLastSerialAnswer(vReply[3]);
   vector<string> vAxesAddr = SplitAnswerOnSpace();
   vAxesAddr.erase(vAxesAddr.begin()); vAxesAddr.erase(vAxesAddr.begin());      // remove "Axis Addr:"
   build.vAxesAddr = vAxesAddr;

   // get hex addresses of cards "Hex Addr:"
   vector<string> vAxesAddrHex;
   if (vReply.size() > 4) // firmware Sep2013 onward, required for addresses beyond '9' = 0x39
   {
      SetLastSerialAnswer(vReply[4]);
      vAxesAddrHex = SplitAnswerOnSpace();
      vAxesAddrHex.erase(vAxesAddrHex.begin()); vAxesAddrHex.erase(vAxesAddrHex.begin());      // remove "Hex Addr:"
   }
   else // old firmware doesn't have hex addresses so we create them here
   {
      string hex = "3x";
      for(unsigned int i=0; i<build.numAxes; i++)
      {
         char c = vAxesAddr[i].at(0);
         if (c < '1' || c > '9')
            return ERR_TOO_LARGE_ADDRESSES;
         hex.replace(1,1,vAxesAddr[i]);
         vAxesAddrHex.push_back(hex.c_str());
      }
   }
   build.vAxesAddrHex = vAxesAddrHex;

   // get properties of cards "Axis Props:"
   vector<string> vAxesProps;
   if (vReply.size() > 5)   // present in firmware Oct2013 onward, required for CRISP detection and SPIM
   {
      SetLastSerialAnswer(vReply[5]);
      vAxesProps = SplitAnswerOnSpace();
      vAxesProps.erase(vAxesProps.begin()); vAxesProps.erase(vAxesProps.begin());      // remove "Axis Props:"
   }
   else  // with older firmware then we are done, just leave a blank vector (CRISP, SPIM, etc. not supported)
   {
      for(unsigned int i=0; i<build.numAxes; i++)
      {
         vAxesProps.push_back("0");
      }
   }
   build.vAxesProps = ConvertStringVector2IntVector(vAxesProps);

   // copy lines 6 through the end to "defines"
   vector<string> tmp(vReply.begin()+6, vReply.end());
   build.defines = tmp;

   return DEVICE_OK;
}

bool ASIHub::IsDefinePresent(const build_info_type build, const string defineToLookFor)
{
   vector<string>::const_iterator it;
   it = find(build.defines.begin(), build.defines.end(), defineToLookFor);
   return (it != build.defines.end());
}

string ASIHub::GetDefineString(const build_info_type build, const string substringToLookFor)
{
   vector<string>::const_iterator it;
   for (it = build.defines.begin(); it != build.defines.end(); ++it)
   {
      if ((*it).find(substringToLookFor)!=std::string::npos)
      {
         return *it;
      }
   }
   return "";
}

int ASIHub::UpdateSharedProperties(string addressChar, string propName, string value) {
   int ret = DEVICE_OK;
   updatingSharedProperties_ = true;
   for (map<string,string>::iterator it=deviceMap_.begin(); it!=deviceMap_.end(); ++it) {
      if (addressChar == it->second) {
         int ret_last = GetCoreCallback()->SetDeviceProperty(it->first.c_str(), propName.c_str(), value.c_str()) != DEVICE_OK;
         if (ret_last!=DEVICE_OK) {
            ret = ret_last;
         }
      }
   }
   updatingSharedProperties_ = false;
   return ret;
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

// use the peculiar fact that the info command is the only Tiger command
// that begins with the letter I.  So isolate for the actual command
// (stripping card address and leading whitespace) and then see if the
// first character is an "I" (not case sensitive)
bool isINFOCommand(const string command)
{
   return toupper(command.at(command.find_first_not_of(" 0123456789"))) == 'I';
}

int ASIHub::OnSerialCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // do nothing
   }
   else if (eAct == MM::AfterSet) {
      static string last_command;
      string tmpstr;
      pProp->Get(tmpstr);
      tmpstr =   UnescapeControlCharacters(tmpstr);
      // only send the command if it has been updated, or if the feature has been set to "no"/false then always send
      if (!serialOnlySendChanged_ || (tmpstr.compare(last_command) != 0))
      {
         // prevent executing the INFO command
         if (isINFOCommand(tmpstr))
            return ERR_INFO_COMMAND_NOT_SUPPORTED;

         last_command = tmpstr;
         QueryCommand(tmpstr);
         // TODO add some sort of check if command was successful, update manualSerialAnswer_ accordingly (e.g. leave blank for invalid command like aoeu)
         manualSerialAnswer_ = serialAnswer_;  // remember this reply even if SendCommand is called elsewhere
      }
   }
   return DEVICE_OK;
}

int ASIHub::OnSerialResponse(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet || eAct == MM::AfterSet)
   {
      // always read
      if (!pProp->Set(EscapeControlCharacters(manualSerialAnswer_).c_str()))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   return DEVICE_OK;
}

int ASIHub::OnSerialCommandRepeatDuration(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // do nothing
   }
   else if (eAct == MM::AfterSet) {
      char command[MM::MaxStrLength];
      MM::MMTime startTime = GetCurrentMMTime();

      long tmp = 0;
      pProp->Get(tmp);
      if (tmp < 0) tmp = 0;
      serialRepeatDuration_ = tmp;
      MM::MMTime duration(serialRepeatDuration_, 0);  // needed for compare statement with other MMTime objects, this constructor takes (sec, usec)

      // in case anything else has used the serial port get the SerialCommand property value
      GetProperty(g_SerialCommandPropertyName, command);

      // keep repeating for the requested duration
      while ( (GetCurrentMMTime() - startTime) < duration ) {
         QueryCommand(command);
         CDeviceUtils::SleepMs(serialRepeatPeriod_);
      }

      // set the repeat time back to 0
      serialRepeatDuration_ = 0;
      pProp->Set(serialRepeatDuration_);
   }
   return DEVICE_OK;
}

int ASIHub::OnSerialCommandRepeatPeriod(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // do nothing
   }
   else if (eAct == MM::AfterSet) {
      MM::MMTime startTime = GetCurrentMMTime();
      long tmp = 0;
      pProp->Get(tmp);
      if (tmp < 0) tmp = 0;
      serialRepeatPeriod_ = tmp;
   }
   return DEVICE_OK;
}

int ASIHub::OnSerialCommandOnlySendChanged(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string tmpstr;
   if (eAct == MM::AfterSet) {
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_YesState) == 0)
         serialOnlySendChanged_ = true;
      else
         serialOnlySendChanged_ = false;
   }
   return DEVICE_OK;
}

string ASIHub::EscapeControlCharacters(const string v)
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

string ASIHub::UnescapeControlCharacters(const string v0)
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

vector<char> ASIHub::ConvertStringVector2CharVector(const vector<string> v)
{
   vector<char> result;
   result.reserve(v.size());
   for(unsigned int i=0; i<v.size(); i++) {
      result.push_back(v[i].at(0));
   }
   return result;
}

vector<int> ASIHub::ConvertStringVector2IntVector(const vector<string> v)
{
   vector<int> result;
   result.reserve(v.size());
   for(unsigned int i=0; i<v.size(); i++) {
      result.push_back(atoi(v[i].c_str()));
   }
   return result;
}

