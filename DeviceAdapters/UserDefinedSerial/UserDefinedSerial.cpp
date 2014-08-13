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

#include "UserDefinedSerial.h"
#include "UserDefinedSerialImpl.h"

#include "ModuleInterface.h"

#include <boost/format.hpp>


const char *const g_DeviceName_Shutter = "UserDefinedSerialShutter";
const char *const g_DeviceName_StateDevice = "UserDefinedSerialStateDevice";


MODULE_API void
InitializeModuleData()
{
   RegisterDevice(g_DeviceName_Shutter, MM::ShutterDevice,
         "Generic shutter using user-defined serial commands");
   RegisterDevice(g_DeviceName_StateDevice, MM::StateDevice,
         "Generic switcher using user-defined serial commands");
}


MODULE_API MM::Device*
CreateDevice(const char* name)
{
   if (!name)
      return 0;

   if (strcmp(name, g_DeviceName_Shutter) == 0)
      return new UserDefSerialShutter();
   else if (strcmp(name, g_DeviceName_StateDevice) == 0)
      return new UserDefSerialStateDevice();

   return 0;
}


MODULE_API void
DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


// Change type without changing bits
inline char UnsignedToSignedByte(unsigned char ch)
{ return *reinterpret_cast<char*>(&ch); }


// Change type without changing bits
inline unsigned char SignedToUnsignedByte(char ch)
{ return *reinterpret_cast<unsigned char*>(&ch); }


std::string
EscapedStringFromByteString(const std::vector<char>& bytes)
{
   std::string result;
   result.reserve(4 * bytes.size());

   for (std::vector<char>::const_iterator
         it = bytes.begin(), end = bytes.end(); it != end; ++it)
   {
      if (*it >= 20 && *it < 127 && *it != '\\' &&
            std::string(MM::g_FieldDelimiters).find(*it) == std::string::npos)
      {
         result.push_back(*it);
      }
      else
      {
         switch (*it)
         {
            case '\\': result += "\\\\"; break;
            case '\n': result += "\\n"; break;
            case '\r': result += "\\r"; break;
            default:
               {
                  // First convert to unsigned char to prevent sign extension
                  // upon converting to unsigned int.
                  unsigned char byte = SignedToUnsignedByte(*it);
                  result += (boost::format("\\x%02x") %
                     static_cast<unsigned int>(byte)).str();
               }
               break;
         }
      }
   }

   return result;
}


// Helper for ParseAfterBackslash().
// When called, i is index of first octal digit after backslash. When returning
// DEVICE_OK, i is index of next char to read, bytes has newly read byte
// appended.
inline int
ParseOctalEscape(const std::string& input, size_t& i,
      std::vector<char>& bytes)
{
   // Start reading up to 3 octal digits
   unsigned char byte = 0;
   for (size_t start = i; i < input.size() && i < start + 3; ++i)
   {
      char ch = input[i];
      if (ch >= '0' && ch <= '0')
      {
         byte *= 8;
         byte += ch - '0';
      }
      else
      {
         break;
      }
   }
   --i; // "Unread" the non-digit or 4th char
   bytes.push_back(UnsignedToSignedByte(byte));
   return DEVICE_OK;
}


// Helper for ParseAfterBackslash().
// When called, i is index of first hex digit after backslash-x. When returning
// DEVICE_OK, i is index of next char to read, bytes has newly read byte
// appended.
inline int
ParseHexEscape(const std::string& input, size_t& i,
      std::vector<char>& bytes)
{
   // Start reading up to 2 hexadecimal digits.
   // In the C specification, there is no limit to the number of
   // digits in a hexadecimal escape sequence; it is terminated at the
   // first non-hex-digit. We differ from this in that we only look
   // for up to 2 digits. (Otherwise there is no way to type hex
   // escapes into a text field.)
   unsigned char byte = 0;
   size_t start = i;
   for ( ; i < input.size() && i < start + 2; ++i)
   {
      char ch = input[i];
      if (ch >= '0' && ch <= '9')
      {
         byte *= 16;
         byte += ch - '0';
      }
      else if (ch >= 'A' && ch <= 'F')
      {
         byte *= 16;
         byte += ch - 'A' + 10;
      }
      else if (ch >= 'a' && ch <= 'f')
      {
         byte *= 16;
         byte += ch - 'a' + 10;
      }
      else
      {
         break;
      }
   }
   --i; // "Unread" the non-digit or 3rd char
   if (i < start)
      return ERR_EMPTY_HEX_ESCAPE_SEQUENCE;
   bytes.push_back(UnsignedToSignedByte(byte));
   return DEVICE_OK;
}


// Helper for ByteStringFromEscapedString().
// When called, input is whole input string, i is index of char after
// backslash, bytes is bytes read so far. When returning DEVICE_OK, i is index
// of next char to read, bytes has newly read byte appended.
inline int
ParseAfterBackslash(const std::string& input, size_t& i,
      std::vector<char>& bytes)
{
   char ch = input[i];
   if (ch >= '0' && ch <= '9')
   {
      int err = ParseOctalEscape(input, i, bytes);
      if (err != DEVICE_OK)
         return err;
   }
   else if (ch == 'x') // 'x' is intentionally case-sensitive
   {
      int err = ParseHexEscape(input, ++i, bytes);
      if (err != DEVICE_OK)
         return err;
   }
   else
   {
      switch (ch)
      {
         case '\'': bytes.push_back('\''); break;
         case '\"': bytes.push_back('\"'); break;
         case '?': bytes.push_back('?'); break;
         case '\\': bytes.push_back('\\'); break;
         case 'a': bytes.push_back('\a'); break;
         case 'b': bytes.push_back('\b'); break;
         case 'f': bytes.push_back('\f'); break;
         case 'n': bytes.push_back('\n'); break;
         case 'r': bytes.push_back('\r'); break;
         case 't': bytes.push_back('\t'); break;
         case 'v': bytes.push_back('\v'); break;
         default: return ERR_UNKNOWN_ESCAPE_SEQUENCE;
      }
   }
   return DEVICE_OK;
}


int
ByteStringFromEscapedString(const std::string& input,
      std::vector<char>& bytes)
{
   bytes.reserve(bytes.size() + input.size());

   bool seenBackslash = false;
   for (size_t i = 0; i < input.size(); ++i)
   {
      if (seenBackslash)
      {
         int err = ParseAfterBackslash(input, i, bytes);
         if (err != DEVICE_OK)
            return err;
         seenBackslash = false;
      }
      else
      {
         char ch = input[i];
         if (ch == '\\')
         {
            seenBackslash = true;
         }
         else
         {
            bytes.push_back(ch);
         }
      }
   }
   if (seenBackslash)
   {
      return ERR_TRAILING_BACKSLASH;
   }

   return DEVICE_OK;
}


std::auto_ptr<ResponseDetector>
ResponseDetector::NewByName(const std::string& name)
{
   std::auto_ptr<ResponseDetector> newDetector;

   newDetector = IgnoringResponseDetector::NewByName(name);
   if (newDetector.get())
      return newDetector;

   newDetector = TerminatorResponseDetector::NewByName(name);
   if (newDetector.get())
      return newDetector;

   newDetector = FixedLengthResponseDetector::NewByName(name);
   if (newDetector.get())
      return newDetector;

   return std::auto_ptr<ResponseDetector>();
}


std::auto_ptr<ResponseDetector>
IgnoringResponseDetector::NewByName(const std::string& name)
{
   std::auto_ptr<ResponseDetector> ret;
   if (name == g_PropValue_ResponseIgnore)
      ret.reset(new IgnoringResponseDetector());
   return ret;
}


std::string
IgnoringResponseDetector::GetMethodName() const
{
   return g_PropValue_ResponseIgnore;
}


int
IgnoringResponseDetector::Recv(MM::Core*, MM::Device*,
      const std::string&, std::vector<char>& response)
{
   response.clear();
   return DEVICE_OK;
}


std::auto_ptr<ResponseDetector>
TerminatorResponseDetector::NewByName(const std::string& name)
{
   std::auto_ptr<ResponseDetector> ret;
   if (name == g_PropValue_ResponseCRLFTerminated)
      ret.reset(new TerminatorResponseDetector("\r\n", "CRLF"));
   else if (name == g_PropValue_ResponseCRTerminated)
      ret.reset(new TerminatorResponseDetector("\r", "CR"));
   else if (name == g_PropValue_ResponseLFTerminated)
      ret.reset(new TerminatorResponseDetector("\n", "LF"));
   return ret;
}


std::string
TerminatorResponseDetector::GetMethodName() const
{
   return g_PropValuePrefix_ResponseTerminated + terminatorName_;
}


int
TerminatorResponseDetector::Recv(MM::Core* core, MM::Device* device,
      const std::string& port, std::vector<char>& response)
{
   if (!core)
      return DEVICE_NO_CALLBACK_REGISTERED;

   char buf[1024];
   int err = core->GetSerialAnswer(device, port.c_str(),
         sizeof(buf) - 1, buf, terminator_.c_str());
   buf[sizeof(buf) - 1] = '\0'; // Just in case
   if (err != DEVICE_OK)
      return err;

   response.clear();
   response.reserve(strlen(buf));
   char* p = buf;
   while (*p)
      response.push_back(*p++);

   return DEVICE_OK;
}


std::auto_ptr<ResponseDetector>
FixedLengthResponseDetector::NewByName(const std::string& name)
{
   std::auto_ptr<ResponseDetector> ret;
   const std::string prefix(g_PropValuePrefix_ResponseFixedByteCount);
   if (name.substr(0, prefix.size()) == prefix)
   {
      size_t byteCount =
         boost::lexical_cast<size_t>(name.substr(prefix.size()));
      ret.reset(new FixedLengthResponseDetector(byteCount));
   }
   return ret;
}


std::string
FixedLengthResponseDetector::GetMethodName() const
{
   return g_PropValuePrefix_ResponseFixedByteCount +
      boost::lexical_cast<std::string>(byteCount_);
}


int
FixedLengthResponseDetector::Recv(MM::Core* core, MM::Device* device,
      const std::string& port, std::vector<char>& response)
{
   if (!core)
      return DEVICE_NO_CALLBACK_REGISTERED;

   response.clear();
   response.resize(byteCount_, '\0');
   unsigned char* bufPtr = reinterpret_cast<unsigned char*>(&response[0]);
   unsigned long bytesRead;
   int err = core->ReadFromSerial(device, port.c_str(),
         bufPtr, static_cast<unsigned long>(response.size()), bytesRead);
   if (bytesRead < byteCount_)
      response.resize(bytesRead);
   if (err != DEVICE_OK)
      return err;
   if (bytesRead < byteCount_) // err should not have been DEVICE_OK
      return ERR_BINARY_SERIAL_READ_FEWER_THAN_REQUESTED;

   return DEVICE_OK;
}


UserDefSerialShutter::UserDefSerialShutter() :
   lastSetOpen_(false)
{
   CreatePreInitProperties();
}


void
UserDefSerialShutter::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceName_Shutter);
}


int
UserDefSerialShutter::SetOpen(bool open)
{
   int err;
   if (open)
      err = SendRecv(openCommand_, openResponse_);
   else
      err = SendRecv(closeCommand_, closeResponse_);
   if (err != DEVICE_OK)
      return err;
   StartBusy();

   lastSetOpen_ = open;
   err = OnPropertyChanged(MM::g_Keyword_State, (open ? "1" : "0"));
   if (err != DEVICE_OK)
      return err;

   return DEVICE_OK;
}


int
UserDefSerialShutter::GetOpen(bool& open)
{
   int err;

   if (!queryCommand_.empty() &&
         !queryOpenResponse_.empty() &&
         !queryCloseResponse_.empty())
   {
      std::vector< std::vector<char> > alternatives;
      alternatives.push_back(queryOpenResponse_);
      alternatives.push_back(queryCloseResponse_);
      size_t index;
      err = SendQueryRecvAlternative(queryCommand_, alternatives, index);
      if (err != DEVICE_OK)
         return err;
      open = (index == 0);
   }
   else
   {
      // Cannot ask device; use memorized state
      open = lastSetOpen_;
   }
   return DEVICE_OK;
}


int
UserDefSerialShutter::Initialize()
{
   int err;
   err = Super::Initialize();
   if (err != DEVICE_OK)
      return err;

   err = SetOpen(false);
   if (err != DEVICE_OK)
      return err;

   return DEVICE_OK;
}


int
UserDefSerialShutter::Shutdown()
{
   int err;
   err = SetOpen(false);
   // Ignore error.

   err = Super::Shutdown();
   if (err != DEVICE_OK)
      return err;

   return DEVICE_OK;
}


void
UserDefSerialShutter::CreatePreInitProperties()
{
   CreateByteStringProperty(g_PropName_OpenCommand, openCommand_, true);
   CreateByteStringProperty(g_PropName_OpenResponse, openResponse_, true);
   CreateByteStringProperty(g_PropName_CloseCommand, closeCommand_, true);
   CreateByteStringProperty(g_PropName_CloseResponse, closeResponse_, true);
   CreateByteStringProperty(g_PropName_QueryStateCommand, queryCommand_, true);
   CreateByteStringProperty(g_PropName_QueryOpenResponse,
         queryOpenResponse_, true);
   CreateByteStringProperty(g_PropName_QueryCloseResponse,
         queryCloseResponse_, true);
}


int
UserDefSerialShutter::CreatePostInitProperties()
{
   int err;
   err = Super::CreatePostInitProperties();
   if (err != DEVICE_OK)
      return err;

   err = CreateIntegerProperty(MM::g_Keyword_State, (lastSetOpen_ ? 1 : 0),
         false, new CPropertyAction(this, &Self::OnState));
   if (err != DEVICE_OK)
      return err;
   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");

   return DEVICE_OK;
}


int
UserDefSerialShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      bool state;
      int err = GetOpen(state);
      if (err != DEVICE_OK)
         return err;
      pProp->Set(state ? 1L : 0L);
   }
   else if (eAct == MM::AfterSet)
   {
      long state;
      pProp->Get(state);
      return SetOpen(state != 0);
   }
   return DEVICE_OK;
}


UserDefSerialStateDevice::UserDefSerialStateDevice() :
   numPositions_(10),
   currentPosition_(0)
{
   CreatePreInitProperties();
}


void
UserDefSerialStateDevice::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceName_StateDevice);
}


unsigned long
UserDefSerialStateDevice::GetNumberOfPositions() const
{
   return static_cast<long>(numPositions_);
}


void
UserDefSerialStateDevice::CreatePreInitProperties()
{
   CreateIntegerProperty(g_PropName_NumPositions,
         static_cast<long>(numPositions_), false,
         new CPropertyAction(this, &Self::OnNumberOfPositions), true);
   SetPropertyLimits(g_PropName_NumPositions, 2.0, 256.0);

   // We cannot create the position commands here, since we don't know the
   // number of positions until after initialization.
}


int
UserDefSerialStateDevice::CreatePostInitProperties()
{
   int err;
   err = Super::CreatePostInitProperties();
   if (err != DEVICE_OK)
      return err;

   err = CreateIntegerProperty(MM::g_Keyword_State,
         static_cast<long>(currentPosition_), false,
         new CPropertyAction(this, &Self::OnState));
   if (err != DEVICE_OK)
      return err;
   std::vector<std::string> stateValues;
   stateValues.reserve(numPositions_);
   for (size_t i = 0; i < numPositions_; ++i)
      stateValues.push_back(boost::lexical_cast<std::string>(i));
   err = SetAllowedValues(MM::g_Keyword_State, stateValues);
   if (err != DEVICE_OK)
      return err;

   err = CreateByteStringProperty(g_PropName_QueryPositionCommand,
         queryCommand_);
   if (err != DEVICE_OK)
      return err;

   positionCommands_.reset(new std::vector<char>[numPositions_]);
   positionResponses_.reset(new std::vector<char>[numPositions_]);
   queryResponses_.reset(new std::vector<char>[numPositions_]);
   for (size_t i = 0; i < numPositions_; ++i)
   {
      err = CreateByteStringProperty(g_PropNamePrefix_SetStateCommand +
            boost::lexical_cast<std::string>(i), positionCommands_[i]);
      if (err != DEVICE_OK)
         return err;

      err = CreateByteStringProperty(g_PropNamePrefix_SetStateResponse +
            boost::lexical_cast<std::string>(i), positionResponses_[i]);
      if (err != DEVICE_OK)
         return err;

      err = CreateByteStringProperty(g_PropNamePrefix_QueryPositionResponse +
            boost::lexical_cast<std::string>(i), queryResponses_[i]);
      if (err != DEVICE_OK)
         return err;
   }

   return DEVICE_OK;
}


int
UserDefSerialStateDevice::OnNumberOfPositions(MM::PropertyBase* pProp,
      MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(static_cast<long>(numPositions_));
   }
   else if (eAct == MM::AfterSet)
   {
      long num;
      pProp->Get(num);
      numPositions_ = num > 0 ? num : 0;
   }
   return DEVICE_OK;
}


int
UserDefSerialStateDevice::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      bool canQuery = false;
      // We can query if the query command and all possible responses are
      // nonempty
      if (!queryCommand_.empty())
      {
         canQuery = true;
         for (size_t i = 0; i < numPositions_; ++i)
         {
            if (queryResponses_[i].empty())
            {
               canQuery = false;
               break;
            }
         }
      }
      if (canQuery)
      {
         std::vector< std::vector<char> > alternatives;
         alternatives.reserve(numPositions_);
         for (size_t i = 0; i < numPositions_; ++i)
            alternatives.push_back(queryResponses_[i]);
         size_t index;
         int err;
         err = SendQueryRecvAlternative(queryCommand_, alternatives, index);
         if (err != DEVICE_OK)
            return err;
         pProp->Set(static_cast<long>(index));
      }
      else
      {
         // Use memorized position
         pProp->Set(static_cast<long>(currentPosition_));
      }
   }
   else if (eAct == MM::AfterSet)
   {
      long pos;
      pProp->Get(pos);
      if (pos < 0 || static_cast<size_t>(pos) > numPositions_)
         return DEVICE_UNKNOWN_POSITION;
      int err;
      err = SendRecv(positionCommands_[pos], positionResponses_[pos]);
      if (err != DEVICE_OK)
         return err;
      StartBusy();

      currentPosition_ = pos;
      err = OnStateChanged(pos);
      if (err != DEVICE_OK)
         return err;

      return DEVICE_OK;
   }
   return DEVICE_OK;
}
