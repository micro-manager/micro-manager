///////////////////////////////////////////////////////////////////////////////
// FILE:       FreeSerialPort.cpp
// PROJECT:    Micro-Manager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Serial Port Device Adapter for free-form communication with serial device
//
// COPYRIGHT:     University of California San Francisco, 2010
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
// AUTHOR:        Karl Hoover
//
// CVS:           $Id: FreeSerialPort.cpp 4112 2010-03-04 00:07:24Z karlh $
//

#ifdef WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#define snprintf _snprintf 
#endif

#include "FreeSerialPort.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include "boost/lexical_cast.hpp"


using namespace std;

// global constants
const char* g_DeviceName = "FreeSerialPort";
#define ERR_PORT_CHANGE_FORBIDDEN    10004
#define ERR_DEVICE_NOT_FOUND         10005



#ifdef WIN32
BOOL APIENTRY DllMain( HANDLE /*hModule*/, 
					  DWORD  ul_reason_for_call, 
					  LPVOID /*lpReserved*/
					  )
{
	switch (ul_reason_for_call)
	{
	case DLL_PROCESS_ATTACH:
	case DLL_THREAD_ATTACH:
	case DLL_THREAD_DETACH:
	case DLL_PROCESS_DETACH:
		break;
	}
	return TRUE;
}
#endif

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
	AddAvailableDeviceName(g_DeviceName, "Free-form communication Serial Port.");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
	if (deviceName == 0)
		return 0;

	if (strcmp(deviceName, g_DeviceName) == 0)
	{
		return new FreeSerialPort;
	}
	return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
	delete pDevice;
}

///////////////////////////////////////////////////////////////////////////////
// FreeSerialPort implementation
// ~~~~~~~~~~~~~~~~~~~~~~~~~~~~

FreeSerialPort::FreeSerialPort() : busy_(false), initialized_(false), detailedLog_(true)
{
	InitializeDefaultErrorMessages();

	// add custom error messages
	//SetErrorText(ERR_UNKNOWN_BLAH, "BLAH");

   // Port
   CPropertyAction* pAct = new CPropertyAction (this, &FreeSerialPort::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

FreeSerialPort::~FreeSerialPort()
{
	Shutdown();
}

void FreeSerialPort::GetName(char* Name) const
{
	CDeviceUtils::CopyLimitedString(Name, g_DeviceName);
}


int FreeSerialPort::Initialize()
{
	// the device initialization will have opened the assigned serial ports...

	// set property list
	// -----------------

	// Name
	int nRet = CreateProperty(MM::g_Keyword_Name, g_DeviceName, MM::String, true);
	if (DEVICE_OK != nRet)
		return nRet;

	// Description
	nRet = CreateProperty(MM::g_Keyword_Description, "Free-form communication serial port", MM::String, true);
	if (DEVICE_OK != nRet)
		return nRet;


   // create the communication properties
   CPropertyActionEx *pActX = 0;
	// create an extended (i.e. array) of properties
   // get the serial device
   MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());
   // map all the serial device properties to this adapter.
   int numComProperties = pS->GetNumberOfProperties();
	for(int ip = 0; ip < numComProperties; ++ip)
   {
      char value[MM::MaxStrLength];
      pS->GetPropertyName(ip,value);
      std::string propName = value;
      int numPropValues = pS->GetNumberOfPropertyValues(propName.c_str());
      std::vector< std::string > allowedValues;
      // get the allowed values
      for(int  iv = 0; iv < numPropValues; ++iv)
      {
         pS->GetPropertyValueAt(propName.c_str(), iv, value);
         allowedValues.push_back(std::string(value));
      }
		pActX = new CPropertyActionEx(this, &FreeSerialPort::OnCommunicationSetting, ip);
      // get the current value
      pS->GetProperty( propName.c_str(), value);
      std:: string propValue = value;
      (void)CreateProperty(propName.c_str(), propValue.c_str(), MM::String, false, pActX);
      (void)SetAllowedValues(propName.c_str(), allowedValues);
      communicationSettings_.push_back(std::make_pair( propName, propValue));
   }

   // user can type commands to send to the serial device 
   CPropertyAction* pAct = new CPropertyAction (this, &FreeSerialPort::OnCommand);
   (void)CreateProperty("Command", "", MM::String, false, pAct);

   // user can specify termination characters to append to command
   pAct = new CPropertyAction (this, &FreeSerialPort::OnCommandTerminator);
   (void)CreateProperty("CommandTerminator", "", MM::String, false, pAct);

   // user can specify characters which terminate a response
   pAct = new CPropertyAction (this, &FreeSerialPort::OnResponseTerminator);
   (void)CreateProperty("ResponseTerminator", "", MM::String, false, pAct);

   // response string
   pAct = new CPropertyAction (this, &FreeSerialPort::OnResponse);
   (void)CreateProperty("Response", "", MM::String, true, pAct);

   // show port setting in browser
   pAct = new CPropertyAction (this, &FreeSerialPort::OnShowPort);
   CreateProperty("ShowPort", port_.c_str(), MM::String, true, pAct);

	initialized_ = true;
	return DEVICE_OK;
}

int FreeSerialPort::Shutdown()
{
	if (initialized_)
	{
		initialized_ = false;
	}
	return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////


int FreeSerialPort::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         // revert
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }

      pProp->Get(port_);
   }

   return DEVICE_OK;
}


int FreeSerialPort::OnCommunicationSetting(MM::PropertyBase* pProp, MM::ActionType eAct, long indexx)
{
   if (eAct == MM::BeforeGet)
   {
      std::pair< std::string, std::string> thisSetting = communicationSettings_[indexx];
      pProp->Set(thisSetting.second.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      std::string newValue;
      pProp->Get(newValue);
      // get the serial device
      MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());
      pS->Shutdown();
      int np =  static_cast<int>(communicationSettings_.size());
      for(int ip = 0; ip < np; ++ip)
      {
        if( indexx == ip)
           communicationSettings_[ip].second = newValue;
        GetCoreCallback()->SetDeviceProperty(port_.c_str(), communicationSettings_[ip].first.c_str(), communicationSettings_[ip].second.c_str() );
      }

      pS->Initialize();
   }
	return DEVICE_OK;
}


int FreeSerialPort::OnCommand(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_OK;
   if (eAct == MM::BeforeGet)
   {
      // escape any control characters for display in the GUI
      pProp->Set(TokenizeControlCharacters(command_).c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(command_);
      // strip any control escapes before storing and sending
      command_ = DetokenizeControlCharacters(command_);
      ret = PurgeComPort(port_.c_str());
      ret = SendSerialCommand(port_.c_str(), command_.c_str(), commandTerminator_.c_str());
      // clear the current response.
      response_="";
      ret = GetSerialAnswer( port_.c_str(), responseTerminator_.c_str(), response_);
   }
   return ret;
}


int FreeSerialPort::OnCommandTerminator(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_OK;
   if (eAct == MM::BeforeGet)
   {
      // escape any control characters for display in the GUI
      pProp->Set(TokenizeControlCharacters(commandTerminator_).c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(commandTerminator_);
      // strip any control escapes before storing
      commandTerminator_ =   DetokenizeControlCharacters(commandTerminator_);
   }
   return ret;
}

int FreeSerialPort::OnResponseTerminator(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_OK;
   if (eAct == MM::BeforeGet)
   {
      // escape any control characters for display in the GUI
      pProp->Set(TokenizeControlCharacters(responseTerminator_).c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      pProp->Get(responseTerminator_);
      // strip any control escapes before storing
      responseTerminator_ =   DetokenizeControlCharacters(responseTerminator_);
   }
   return ret;
}


std::string FreeSerialPort::TokenizeControlCharacters(const std::string v ) const
{
   std::string messs;
   for( std::string::const_iterator ii = v.begin(); ii != v.end(); ++ii)
   {
      messs +=   ( 31 < *ii) ?  boost::lexical_cast<std::string, unsigned char>(*ii) : 
         ("\\" + boost::lexical_cast<std::string, unsigned short>((unsigned short)*ii) );
   }
   return messs;
}

std::string FreeSerialPort::DetokenizeControlCharacters(const std::string v0 ) const
{
   // the string input from the GUI can contain escaped control characters, currently these are always preceded with \ (0x5C)
   // and always assumed to be decimal or C style
   // to do ::::  the control character escape character and code basis can be properties as well!

   std::string detokenized;
   std::string v = v0;

   for( std::string::iterator jj = v.begin(); jj != v.end(); ++jj)
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
         const std::string::iterator nextAfterEscape = jj;
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
            std::istringstream tmp(thisControlCharacter);
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
               code = 13; // CR
               break;
            case 'n':
               ++jj;
               code = 10; // NL
               break;
            case 't':
               ++jj;
               code = 8; // TAB
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

int FreeSerialPort::OnResponse(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_OK;
   if (eAct == MM::BeforeGet)
   {
      // escape any control characters for display in the GUI
      pProp->Set(TokenizeControlCharacters(response_).c_str() );
   }
   else if (eAct == MM::AfterSet)
   {
   }

   return ret;
}


// let user see port setting in browswer
int FreeSerialPort::OnShowPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_OK;
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str() );
   }
   else if (eAct == MM::AfterSet)
   {
   }

   return ret;
}

