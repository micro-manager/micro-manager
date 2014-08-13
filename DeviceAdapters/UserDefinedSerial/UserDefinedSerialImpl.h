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

#include <boost/lexical_cast.hpp>

#include <algorithm>
#include <iterator>


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
const char* const g_PropNamePrefix_SetStateCommand = "SetState-command-";
const char* const g_PropNamePrefix_SetStateResponse = "SetState-response-";
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
   "Fixed byte count-";


template <template <class> class TBasicDevice, class UConcreteDevice>
UserDefSerialBase<TBasicDevice, UConcreteDevice>::UserDefSerialBase() :
   port_("Undefined"),
   initialized_(false),
   lastActionTime_(0.0),
   binaryMode_(false),
   asciiTerminator_(""),
   responseDetector_(ResponseDetector::NewByName(g_PropValue_ResponseIgnore))
{
   RegisterErrorMessages();
   CreatePreInitProperties();
   Super::SetDelayMs(0.0);
   Super::EnableDelay();
}


template <template <class> class TBasicDevice, class UConcreteDevice>
int
UserDefSerialBase<TBasicDevice, UConcreteDevice>::Initialize()
{
   if (initialized_)
      return DEVICE_OK;

   int err;

   err = CreatePostInitProperties();
   if (err != DEVICE_OK)
      return err;

   err = SendRecv(initializeCommand_, initializeResponse_);
   if (err != DEVICE_OK)
      return err;

   initialized_ = true;
   return DEVICE_OK;
}


template <template <class> class TBasicDevice, class UConcreteDevice>
int
UserDefSerialBase<TBasicDevice, UConcreteDevice>::Shutdown()
{
   if (!initialized_)
      return DEVICE_OK;

   int err;

   err = SendRecv(shutdownCommand_, shutdownResponse_);
   if (err != DEVICE_OK)
      return err;

   return DEVICE_OK;
}


template <template <class> class TBasicDevice, class UConcreteDevice>
bool
UserDefSerialBase<TBasicDevice, UConcreteDevice>::Busy()
{
   double delayUs = 1000.0 * Super::GetDelayMs();
   MM::MMTime now = Super::GetCurrentMMTime();
   MM::MMTime finishTime = lastActionTime_ + MM::MMTime(delayUs);
   return now < finishTime;
}


template <template <class> class TBasicDevice, class UConcreteDevice>
void
UserDefSerialBase<TBasicDevice, UConcreteDevice>::RegisterErrorMessages()
{
   Super::SetErrorText(ERR_BINARY_SERIAL_TIMEOUT,
         "Timeout waiting for response from device");

   Super::SetErrorText(ERR_UNEXPECTED_RESPONSE,
         "Unexpected response from device");

   // Unlikely - programming error in this device adapter
   Super::SetErrorText(ERR_QUERY_COMMAND_EMPTY,
         "Cannot query device with empty command");

   Super::SetErrorText(ERR_ASCII_COMMAND_CONTAINS_NULL,
         "Null character in ASCII-mode command");
   Super::SetErrorText(ERR_TRAILING_BACKSLASH,
         "Trailing backslash in command or response string");
   Super::SetErrorText(ERR_UNKNOWN_ESCAPE_SEQUENCE,
         "Unknown escape sequence in command or response string");
   Super::SetErrorText(ERR_EMPTY_HEX_ESCAPE_SEQUENCE,
         "Empty hexadecimal escape sequence in command or response string");

   Super::SetErrorText(ERR_CANNOT_GET_PORT_TIMEOUT,
         "Cannot get the timeout setting for the port");

   // Unlikely - usually should get a timeout error instead
   Super::SetErrorText(ERR_BINARY_SERIAL_READ_FEWER_THAN_REQUESTED,
         "Fewer than the expected number of bytes were sent by the device");
}


template <template <class> class TBasicDevice, class UConcreteDevice>
void
UserDefSerialBase<TBasicDevice, UConcreteDevice>::CreatePreInitProperties()
{
   Super::CreateStringProperty(MM::g_Keyword_Port, port_.c_str(), false,
         new typename Super::CPropertyAction(This(), &DeviceType::OnPort),
         true);

   const char* commandModeInitialValue = g_PropValue_Binary;
   if (!binaryMode_)
   {
      if (asciiTerminator_ == "")
         commandModeInitialValue = g_PropValue_ASCII_NoTerminator;
      else if (asciiTerminator_ == "\r\n")
         commandModeInitialValue = g_PropValue_ASCII_CRLF;
      else if (asciiTerminator_ == "\r")
         commandModeInitialValue = g_PropValue_ASCII_CR;
      else if (asciiTerminator_ == "\n")
         commandModeInitialValue = g_PropValue_ASCII_LF;
   }
   Super::CreateStringProperty(g_PropName_CommandSendMode,
         commandModeInitialValue, false,
         new typename Super::CPropertyAction(This(),
            &DeviceType::OnCommandSendMode),
         true);
   Super::AddAllowedValue(g_PropName_CommandSendMode,
         g_PropValue_ASCII_NoTerminator);
   Super::AddAllowedValue(g_PropName_CommandSendMode, g_PropValue_ASCII_CRLF);
   Super::AddAllowedValue(g_PropName_CommandSendMode, g_PropValue_ASCII_CR);
   Super::AddAllowedValue(g_PropName_CommandSendMode, g_PropValue_ASCII_LF);
   Super::AddAllowedValue(g_PropName_CommandSendMode, g_PropValue_Binary);

   Super::CreateStringProperty(g_PropName_ResponseDetectionMethod,
         responseDetector_->GetMethodName().c_str(), false,
         new typename Super::CPropertyAction(This(),
            &DeviceType::OnResponseDetectionMethod),
         true);
   Super::AddAllowedValue(g_PropName_ResponseDetectionMethod,
         g_PropValue_ResponseIgnore);
   Super::AddAllowedValue(g_PropName_ResponseDetectionMethod,
         g_PropValue_ResponseCRTerminated);
   Super::AddAllowedValue(g_PropName_ResponseDetectionMethod,
         g_PropValue_ResponseLFTerminated);
   Super::AddAllowedValue(g_PropName_ResponseDetectionMethod,
         g_PropValue_ResponseCRLFTerminated);
   for (unsigned byteCount = 1; byteCount < 17; ++byteCount)
   {
      Super::AddAllowedValue(g_PropName_ResponseDetectionMethod,
            (g_PropValuePrefix_ResponseFixedByteCount +
             boost::lexical_cast<std::string>(byteCount)).c_str());
   }

   CreateByteStringProperty(g_PropName_InitializeCommand,
         initializeCommand_, true);
   CreateByteStringProperty(g_PropName_InitializeResponse,
         initializeResponse_, true);
   CreateByteStringProperty(g_PropName_ShutdownCommand,
         shutdownCommand_, true);
   CreateByteStringProperty(g_PropName_ShutdownResponse,
         shutdownResponse_, true);
}


template <template <class> class TBasicDevice, class UConcreteDevice>
int
UserDefSerialBase<TBasicDevice, UConcreteDevice>::CreatePostInitProperties()
{
   return DEVICE_OK;
}


template <template <class> class TBasicDevice, class UConcreteDevice>
void
UserDefSerialBase<TBasicDevice, UConcreteDevice>::StartBusy()
{
   lastActionTime_ = Super::GetCurrentMMTime();
}


template <template <class> class TBasicDevice, class UConcreteDevice>
int
UserDefSerialBase<TBasicDevice, UConcreteDevice>::
OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
      pProp->Set(port_.c_str());
   else if (eAct == MM::AfterSet)
      pProp->Get(port_);
   return DEVICE_OK;
}


template <template <class> class TBasicDevice, class UConcreteDevice>
int
UserDefSerialBase<TBasicDevice, UConcreteDevice>::
OnCommandSendMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (binaryMode_)
         pProp->Set(g_PropValue_Binary);
      else if (asciiTerminator_ == "")
         pProp->Set(g_PropValue_ASCII_NoTerminator);
      else if (asciiTerminator_ == "\r\n")
         pProp->Set(g_PropValue_ASCII_CRLF);
      else if (asciiTerminator_ == "\r")
         pProp->Set(g_PropValue_ASCII_CR);
      else if (asciiTerminator_ == "\n")
         pProp->Set(g_PropValue_ASCII_LF);
   }
   else if (eAct == MM::AfterSet)
   {
      std::string s;
      pProp->Get(s);
      binaryMode_ = (s == g_PropValue_Binary);
      if (!binaryMode_)
      {
         if (s == g_PropValue_ASCII_NoTerminator)
            asciiTerminator_ = "";
         else if (s == g_PropValue_ASCII_CRLF)
            asciiTerminator_ = "\r\n";
         else if (s == g_PropValue_ASCII_CR)
            asciiTerminator_ = "\r";
         else if (s == g_PropValue_ASCII_LF)
            asciiTerminator_ = "\n";
         else
            return DEVICE_INVALID_PROPERTY_VALUE;
      }
   }
   return DEVICE_OK;
}


template <template <class> class TBasicDevice, class UConcreteDevice>
int
UserDefSerialBase<TBasicDevice, UConcreteDevice>::
OnResponseDetectionMethod(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(responseDetector_->GetMethodName().c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      std::string s;
      pProp->Get(s);
      std::auto_ptr<ResponseDetector> newDetector =
         ResponseDetector::NewByName(s);
      if (!newDetector.get())
         return DEVICE_INVALID_PROPERTY_VALUE;
      responseDetector_ = newDetector;
   }
   return DEVICE_OK;
}


template <template <class> class TBasicDevice, class UConcreteDevice>
int
UserDefSerialBase<TBasicDevice, UConcreteDevice>::
CreateByteStringProperty(const char* name, std::vector<char>& varRef,
      bool preInit)
{
   class Functor : public MM::ActionFunctor, boost::noncopyable
   {
      std::vector<char>& varRef_;
   public:
      Functor(std::vector<char>& varRef) : varRef_(varRef) {}
      virtual int Execute(MM::PropertyBase* pProp, MM::ActionType eAct)
      {
         if (eAct == MM::BeforeGet)
         {
            pProp->Set(EscapedStringFromByteString(varRef_).c_str());
         }
         else if (eAct == MM::AfterSet)
         {
            std::string s;
            pProp->Get(s);
            std::vector<char> bytes;
            int err = ByteStringFromEscapedString(s, bytes);
            if (err != DEVICE_OK)
               return err;
            varRef_ = bytes;
         }
         return DEVICE_OK;
      }
   };

   return Super::CreateStringProperty(name,
         EscapedStringFromByteString(varRef).c_str(), false,
         new Functor(varRef), preInit);
}


template <template <class> class TBasicDevice, class UConcreteDevice>
int
UserDefSerialBase<TBasicDevice, UConcreteDevice>::
SendRecv(const std::vector<char>& command,
      const std::vector<char>& expectedResponse)
{
   if (command.empty())
      return DEVICE_OK;

   int err;

   err = Super::PurgeComPort(port_.c_str());
   if (err != DEVICE_OK)
      return err;

   err = Send(command);
   if (err != DEVICE_OK)
      return err;

   if (expectedResponse.empty())
      return DEVICE_OK;

   std::vector<char> response;
   err = responseDetector_->Recv(Super::GetCoreCallback(), this,
         port_, response);
   if (err != DEVICE_OK)
      return err;

   if (response != expectedResponse)
      return ERR_UNEXPECTED_RESPONSE;

   return DEVICE_OK;
}


template <template <class> class TBasicDevice, class UConcreteDevice>
int
UserDefSerialBase<TBasicDevice, UConcreteDevice>::
SendQueryRecvAlternative(const std::vector<char>& command,
      const std::vector< std::vector<char> >& responseAlts,
      size_t& responseAltIndex)
{
   if (command.empty())
      return ERR_QUERY_COMMAND_EMPTY;

   int err;

   err = Super::PurgeComPort(port_.c_str());
   if (err != DEVICE_OK)
      return err;

   err = Send(command);
   if (err != DEVICE_OK)
      return err;

   std::vector<char> response;
   err = responseDetector_->Recv(Super::GetCoreCallback(), this,
         port_, response);
   if (err != DEVICE_OK)
      return err;

   std::vector< std::vector<char> >::const_iterator foundAlt =
      std::find(responseAlts.begin(), responseAlts.end(), response);
   if (foundAlt == responseAlts.end())
      return ERR_UNEXPECTED_RESPONSE;
   responseAltIndex = std::distance(responseAlts.begin(), foundAlt);

   return DEVICE_OK;
}


template <template <class> class TBasicDevice, class UConcreteDevice>
int
UserDefSerialBase<TBasicDevice, UConcreteDevice>::
Send(const std::vector<char>& command)
{
   if (command.empty())
      return DEVICE_OK;

   int err;

   if (binaryMode_)
   {
      err = Super::WriteToComPort(port_.c_str(),
            reinterpret_cast<const unsigned char*>(&command[0]),
            static_cast<unsigned int>(command.size()));
      if (err != DEVICE_OK)
         return err;
   }
   else
   {
      // Make sure there are no null bytes in the command
      std::vector<char>::const_iterator foundNull =
         std::find(command.begin(), command.end(), '\0');
      if (foundNull != command.end())
         return ERR_ASCII_COMMAND_CONTAINS_NULL;

      std::string commandString(command.begin(), command.end());
      err = Super::SendSerialCommand(port_.c_str(), commandString.c_str(),
            asciiTerminator_.c_str());
      if (err != DEVICE_OK)
         return err;
   }

   return DEVICE_OK;
}
