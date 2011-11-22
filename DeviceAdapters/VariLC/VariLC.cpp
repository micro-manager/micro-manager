///////////////////////////////////////////////////////////////////////////////
// FILE:          VariLC.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   VariLC 
//
// AUTHOR:        Rudolf Oldenbourg, MBL, w/ Arthur Edelstein and Karl Hoover, UCSF, Sept, Oct 2010
// COPYRIGHT:     
// LICENSE:       
// 

#ifdef WIN32
//   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "VariLC.h"
#include <string>
//#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>


const char* g_ControllerName    = "VariLC";

const char* g_TxTerm            = "\r"; //unique termination
const char* g_RxTerm            = "\r"; //unique termination


using namespace std;



///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_ControllerName,    "VariLC");             
}                                                                            

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{
   if (deviceName == 0) return 0;
   if (strcmp(deviceName, g_ControllerName)    == 0) return new VariLC();                           
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


// General utility function:
int ClearPort(MM::Device& device, MM::Core& core, std::string port)
{
   // Clear contents of serial port 
   const int bufSize = 255;
   unsigned char clear[bufSize];                                                        
   unsigned long read = bufSize;                                               
   int ret;                                                                    
   while ((int) read == bufSize)                                                     
   {                                                                           
      ret = core.ReadFromSerial(&device, port.c_str(), clear, bufSize, read); 
      if (ret != DEVICE_OK)                                                    
         return ret;                                                           
   }                                                                           
   return DEVICE_OK;                                                           
}





///////////////////////////////////////////////////////////////////////////////
// VariLC Hub
///////////////////////////////////////////////////////////////////////////////

VariLC::VariLC() :
  answerTimeoutMs_(1000),
  initialized_(false),
  numActiveLCs_(2),
  numTotalLCs_(4),
  numPalEls_(5)
{
   InitializeDefaultErrorMessages();

   
   // pre-initialization properties

   // Port:
   CPropertyAction* pAct = new CPropertyAction(this, &VariLC::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
   
   pAct = new CPropertyAction (this, &VariLC::OnNumActiveLCs);
   CreateProperty("Number of Active LCs", "2", MM::Integer, false, pAct, true); 
   
   pAct = new CPropertyAction (this, &VariLC::OnNumTotalLCs);
   CreateProperty("Total Number of LCs", "4", MM::Integer, false, pAct, true); 
   
   pAct = new CPropertyAction (this, &VariLC::OnNumPalEls);
   CreateProperty("Total Number of Palette Elements", "5", MM::Integer, false, pAct, true); 
   
   EnableDelay();
}

VariLC::~VariLC()
{
   Shutdown();
}

void VariLC::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_ControllerName);
}


int VariLC::Initialize()
{
	for (long i=0;i<numTotalLCs_;++i) {
		retardance_[i] = 0.5;
	}

	for (long i=0;i<numPalEls_;i++) {
		palEl_[i] = "";
	}
 
// empty the Rx serial buffer before sending command
   int ret = ClearPort(*this, *GetCoreCallback(), port_);
   if (ret != DEVICE_OK)
      return ret;

   // Name
   ret = CreateProperty(MM::g_Keyword_Name, g_ControllerName, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   CPropertyAction* pAct = new CPropertyAction (this, &VariLC::OnBriefMode);
   ret = CreateProperty("Mode, 1=Brief, 0=Standard", "", MM::String, true, pAct); 
   if (ret != DEVICE_OK)
	   return ret;
   //Set VariLC to Standard mode
   briefModeQ_ = false;
   ret = SendSerialCommand(port_.c_str(), "B 0", "\r");
   if (ret!=DEVICE_OK)
	   return DEVICE_SERIAL_COMMAND_FAILED;
   ret = GetSerialAnswer (port_.c_str(), "\r", getFromVariLC_);  //add the following error check each time GetSerialAnswer is called
   if (ret!=DEVICE_OK) {
	   SetErrorText(99, "The VariLC did not respond.");
   	   return 99;
   }
   if (getFromVariLC_.length() == 0)
	   return DEVICE_NOT_CONNECTED;

   pAct = new CPropertyAction (this, &VariLC::OnWavelength);
   ret = CreateProperty("Wavelength", "550.0", MM::Float, false, pAct); 
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits("Wavelength", 400., 800.);

   
   CPropertyActionEx *pActX = 0;
//	 create an extended (i.e. array) properties 0 through 1
	
   for (long i=0;i<numActiveLCs_;++i) {
	   ostringstream s;
	   s << "Retardance LC-" << char(65+i);
	   pActX = new CPropertyActionEx(this, &VariLC::OnRetardance, i);
	   CreateProperty(s.str().c_str(), "0.5", MM::Float, false, pActX);
	   SetPropertyLimits(s.str().c_str(), 0.14, 1.2);
   }
 
   for (long i=0;i<numPalEls_;++i) {
	   ostringstream s;
	   s << "Pal. elem. " << char(48+i) << ", enter 0 to define, 1 to activate";
	   pActX = new CPropertyActionEx(this, &VariLC::OnPalEl, i);
	   CreateProperty(s.str().c_str(), "", MM::String, false, pActX);
   }

   pAct = new CPropertyAction (this, &VariLC::OnSendToVariLC);
   ret = CreateProperty("String send to VariLC", "", MM::String, false, pAct); 
   if (ret != DEVICE_OK)
      return ret;
 
   pAct = new CPropertyAction (this, &VariLC::OnGetFromVariLC);
   ret = CreateProperty("String from VariLC", "", MM::String, true, pAct); 
   if (ret != DEVICE_OK)
      return ret;

   // Needed for Busy flag
   changedTime_ = GetCurrentMMTime() - GetDelayMs();

   return DEVICE_OK;
}


int VariLC::Shutdown()
{ 
  initialized_ = false;
  return DEVICE_OK;
}



//////////////// Action Handlers (VariLC) /////////////////

int VariLC::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(port_.c_str());
         return DEVICE_INVALID_INPUT_PARAM;
      }
      pProp->Get(port_);
   }
   return DEVICE_OK;
}

 int VariLC::OnBriefMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet){
     int ret = SendSerialCommand(port_.c_str(), "B?", "\r");
         if (ret!=DEVICE_OK)return DEVICE_SERIAL_COMMAND_FAILED;
     std::string ans;
     GetSerialAnswer (port_.c_str(), "\r", ans);
     GetSerialAnswer (port_.c_str(), "\r", ans);
	 if (ans == "1") {
		 briefModeQ_ = true;
	 } else {
		 briefModeQ_ = false;
	 }
	 if (briefModeQ_) {
	     pProp->Set(" 1");
	 } else {
	     pProp->Set(" 0");
	 }
   }
   else if (eAct == MM::AfterSet)
   {

   }
   return DEVICE_OK;
}

 int VariLC::OnNumTotalLCs(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
     pProp->Set(numTotalLCs_);
    }
   else if (eAct == MM::AfterSet)
   {
	  pProp->Get(numTotalLCs_);
   }
   return DEVICE_OK;
}

 int VariLC::OnNumActiveLCs(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
     pProp->Set(numActiveLCs_);
    }
   else if (eAct == MM::AfterSet)
   {
	  pProp->Get(numActiveLCs_);
   }
   return DEVICE_OK;
}

 int VariLC::OnNumPalEls(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
     pProp->Set(numPalEls_);
    }
   else if (eAct == MM::AfterSet)
   {
	  pProp->Get(numPalEls_);
   }
   return DEVICE_OK;
}

int VariLC::OnWavelength (MM::PropertyBase* pProp, MM::ActionType eAct)
 {
   if (eAct == MM::BeforeGet)
   {
     int ret = SendSerialCommand(port_.c_str(), "W?", "\r");
         if (ret!=DEVICE_OK)return DEVICE_SERIAL_COMMAND_FAILED;
     std::string ans;
	 GetSerialAnswer (port_.c_str(), "\r", ans);
     GetSerialAnswer (port_.c_str(), "\r", ans);

     vector<double> numbers = getNumbersFromMessage(ans,briefModeQ_);
     pProp->Set(numbers[0]);
   }
   else if (eAct == MM::AfterSet)
   {
	  double wavelength;
      // read value from property
      pProp->Get(wavelength);
      // write wavelength out to device....
	  ostringstream cmd;
	  cmd.precision(5);
	  cmd << "W " << wavelength;
     int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), "\r");
     if (ret!=DEVICE_OK)
	     return DEVICE_SERIAL_COMMAND_FAILED;
     std::string ans;
	 GetSerialAnswer (port_.c_str(), "\r", ans);

     wavelength_ = wavelength;
// Clear palette elements after change of wavelength
   }
   return DEVICE_OK;
 }

int VariLC::OnRetardance(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   if (eAct == MM::BeforeGet)
   {
     int ret = SendSerialCommand(port_.c_str(), "L?", "\r");
         if (ret!=DEVICE_OK)return DEVICE_SERIAL_COMMAND_FAILED;
     std::string ans;
	 GetSerialAnswer (port_.c_str(), "\r", ans);
     GetSerialAnswer (port_.c_str(), "\r", ans);

     vector<double> numbers = getNumbersFromMessage(ans,briefModeQ_);
	 if (index < (int) numbers.size()) {
	    retardance_[index] = numbers[index];
	   pProp->Set(retardance_[index]);
	 } else {
		 return DEVICE_INVALID_PROPERTY_VALUE;
	 }
   }
   else if (eAct == MM::AfterSet)
   {
	   double retardance;

      // read value from property
      pProp->Get(retardance);
      // write retardance out to device....
	   ostringstream cmd;
	   cmd.precision(4);
      cmd << "L" ;
      for (int i=0; i<numTotalLCs_; i++) {
         if (i==index) {
            cmd << " " << retardance;
		 } else {
			cmd << " " << retardance_[i];
		 }
      }
      int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), "\r");
      if (ret!=DEVICE_OK)
   		return DEVICE_SERIAL_COMMAND_FAILED;
      
      std::string ans;
	  GetSerialAnswer (port_.c_str(), "\r", ans);

      retardance_[index] = retardance;

	  changedTime_ = GetCurrentMMTime();
   }
   return DEVICE_OK;
}

 int VariLC::OnPalEl(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   if (eAct == MM::BeforeGet)
   {
	 //PurgeComPort(port_.c_str());
     int ret = SendSerialCommand(port_.c_str(), "D?", "\r");
         if (ret!=DEVICE_OK)return DEVICE_SERIAL_COMMAND_FAILED;
     std::string ans;
//	 GetSerialAnswer (port_.c_str(), "\r", ans);
	 while (ans!="D?") GetSerialAnswer (port_.c_str(), "\r", ans);
//the while statement was needed to overcome an empty string that appears in ans while reading the 5th pallette element 
//however, we should avoid infinite loop; bow out with report of comm error?
    GetSerialAnswer (port_.c_str(), "\r", ans);
     vector<double> numbers = getNumbersFromMessage(ans,briefModeQ_);
     int elemNr = (int) numbers[0];
    if (elemNr == 0) {
		for (int i=0;i<5;i++) {
			palEl_[i] = "";
		}
	 } else {
        for (int i=1;i<=elemNr;i++) {  //i is one based
          ret = GetSerialAnswer (port_.c_str(), "\r", ans);
              if (ret!=DEVICE_OK)return DEVICE_SERIAL_COMMAND_FAILED;
          if ((i-1)==index) {
             numbers = getNumbersFromMessage(ans,briefModeQ_);
         	 ostringstream palElStr;
         	 palElStr.precision(4);
			 palElStr << "  " << (numbers[0]);
		     for (long i=0;i<numActiveLCs_;++i) {
        	   palElStr << "  " << (numbers[i+1]);
		     }
			 palEl_[index] = palElStr.str();
          }
        }
	 }
	 pProp->Set(palEl_[index].c_str());
   }
   else if (eAct == MM::AfterSet)
   {
	   long setPalEl = 0;
	   changedTime_ = GetCurrentMMTime();  //enter in each function that sets the LCs
	   std::string ans;
	   ostringstream cmd;
	   cmd.precision(0);
	    pProp->Get(setPalEl);
		if (setPalEl == 0) {
		   cmd << "D " << index;
			int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), "\r");
				if (ret!=DEVICE_OK)return DEVICE_SERIAL_COMMAND_FAILED;
				GetSerialAnswer (port_.c_str(), "\r", ans);
		}
		if (setPalEl == 1) {
		   cmd << "P " << index;
			int ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), "\r");
				if (ret!=DEVICE_OK)return DEVICE_SERIAL_COMMAND_FAILED;
				GetSerialAnswer (port_.c_str(), "\r", ans);
		}
   }
   return DEVICE_OK;
}


int VariLC::OnSendToVariLC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
//      pProp->Set(sendToVariLC_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      // read value from property
      pProp->Get(sendToVariLC_);
      // write retardance out to device....
      if (sendToVariLC_=="Escape") {
         char command[2];
         command[0]=27;
         command[1]=0;
         int ret = SendSerialCommand(port_.c_str(), command, 0);
         if (ret!=DEVICE_OK)
		   return DEVICE_SERIAL_COMMAND_FAILED;
		 GetSerialAnswer (port_.c_str(), "\r", getFromVariLC_);
         getFromVariLC_ = "";
      } else {
         int ret = SendSerialCommand(port_.c_str(), sendToVariLC_.c_str(), "\r");
         if (ret!=DEVICE_OK)
		      return DEVICE_SERIAL_COMMAND_FAILED;
		 GetSerialAnswer (port_.c_str(), "\r", getFromVariLC_);
         GetSerialAnswer (port_.c_str(), "\r", getFromVariLC_);
      }
   }
   return DEVICE_OK;
}

int VariLC::OnGetFromVariLC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(getFromVariLC_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {

   }
   return DEVICE_OK;
}

bool VariLC::Busy()
{
   if (GetDelayMs() > 0.0) {
      MM::MMTime interval = GetCurrentMMTime() - changedTime_;
      MM::MMTime delay(GetDelayMs()*1000.0);
      if (interval < delay ) {
         return true;
      }
   }
   
   return false;
}


std::vector<double> VariLC::getNumbersFromMessage(std::string variLCmessage, bool briefMode) {
   std::istringstream variStream(variLCmessage);
   std::string prefix;
   double val;
   std::vector<double> values;

   if (!briefMode){
      variStream >> prefix;
	}
    for (;;) {
       variStream >> val;
       if (! variStream.fail()) {
          values.push_back(val);
       } else {
           break;
       }
	 }

	return values;
}

