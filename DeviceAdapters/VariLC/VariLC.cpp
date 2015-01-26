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

// Change Log - Amitabh Verma - Jan. 26, 2015
// 1. Error response retrieval using 'R?' fixed

// Change Log - Amitabh Verma - July. 23, 2014
// 1. Replaced ',' comma with ';' semi-colon  in property names due to Micro-Manager warning during HW Config Wizard 'Contains reserved chars'
// Note: This will break Pol-Acquisition (OpenPolScope) and requires compatible version which uses same name for Property

// Change Log - Amitabh Verma - Apr. 02, 2014
// 1. Variable time delay
// 2. Absolute Retardance in nm. property

// Change Log - Amitabh Verma, Grant Harris - Nov. 10, 2012
// 1. Implemented Number of Active LCs, Total No. of LCs and baud value for searching appropriate VariLC (9600) or Abrio (115200)

// Change Log - Amitabh Verma, Grant Harris - Oct 10, 2012
// 1. Implemented COM port Search ability using Micro-Manager Hardware Wizard

// Change Log - Amitabh Verma, Grant Harris - Aug 08, 2012
// 1. State commands 0-5 which implement Palette

// Change Log - Amitabh Verma, Grant Harris - Apr 27, 2012
// 1. Added Version numbering
// 2. Serial number device property
// 3. Total Number of LCs default value to "2" instead of "4"
// 4. wavelength default 546


#ifdef WIN32
//   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "VariLC.h"
#include <cstdio>
#include <cctype>
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>
#include <algorithm>    // std::remove_if


const char* g_ControllerName    = "VariLC";

const char* g_TxTerm            = "\r"; //unique termination
const char* g_RxTerm            = "\r"; //unique termination

const char* g_BaudRate_key        = "Baud Rate";
const char* g_Baud9600            = "9600";
const char* g_Baud115200          = "115200";



using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_ControllerName, MM::GenericDevice, "VariLC");
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
   const int bufSize = 2048;
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
  baud_(g_Baud9600),
  initialized_(false),
  initializedDelay_(false),
  answerTimeoutMs_(1000),
  wavelength_(546),
  numTotalLCs_(2),
  numActiveLCs_(2),
  numPalEls_(5)
{
   InitializeDefaultErrorMessages();
   
   // pre-initialization properties


   // Port:
   CPropertyAction* pAct = new CPropertyAction(this, &VariLC::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
   SetProperty(MM::g_Keyword_Port, port_.c_str());

   pAct = new CPropertyAction (this, &VariLC::OnNumActiveLCs);
   CreateProperty("Number of Active LCs", "2", MM::Integer, false, pAct, true); 
   
   pAct = new CPropertyAction (this, &VariLC::OnNumTotalLCs);
   CreateProperty("Total Number of LCs", "2", MM::Integer, false, pAct, true); 
   
   pAct = new CPropertyAction (this, &VariLC::OnNumPalEls);
   CreateProperty("Total Number of Palette Elements", "5", MM::Integer, false, pAct, true); 
   
   pAct = new CPropertyAction(this, &VariLC::OnBaud);
   CreateProperty(g_BaudRate_key, "Undefined", MM::String, false, pAct, true);  
   
   AddAllowedValue(g_BaudRate_key, g_Baud115200, (long) 115200);
   AddAllowedValue(g_BaudRate_key, g_Baud9600, (long) 9600);

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


MM::DeviceDetectionStatus VariLC::DetectDevice(void)
{
   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;

   try
   {	   
	   long baud;
	   GetProperty(g_BaudRate_key, baud);

      std::string transformed = port_;
      for( std::string::iterator its = transformed.begin(); its != transformed.end(); ++its)
      {
         *its = (char)tolower(*its);
      }	  	     

      if( 0< transformed.length() &&  0 != transformed.compare("undefined")  && 0 != transformed.compare("unknown") )
      {
		int ret = 0;	  
		MM::Device* pS;

		
			 // the port property seems correct, so give it a try
			 result = MM::CanNotCommunicate;
			 // device specific default communication parameters
			 GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_AnswerTimeout, "2000.0");			 
			 GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, baud_.c_str() );
			 GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_DelayBetweenCharsMs, "0.0");
			 GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, "Off");
			 GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Parity, "None");
			 GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "1");
			 GetCoreCallback()->SetDeviceProperty(port_.c_str(), "Verbose", "1");
			 pS = GetCoreCallback()->GetDevice(this, port_.c_str());
			 pS->Initialize();
	         
			 ClearPort(*this, *GetCoreCallback(), port_);
			 ret = SendSerialCommand(port_.c_str(), "V?", "\r");     
			 GetSerialAnswer (port_.c_str(), "\r", serialnum_);
			 GetSerialAnswer (port_.c_str(), "\r", serialnum_);
				 if (ret!=DEVICE_OK || serialnum_.length() < 5)
				 {
					LogMessageCode(ret,true);
					LogMessage(std::string("VariLC not found on ")+port_.c_str(), true);
					LogMessage(std::string("VariLC serial no:")+serialnum_, true);
					ret = 1;
					serialnum_ = "0";
					pS->Shutdown();	
				 } else
				 {
					// to succeed must reach here....
					LogMessage(std::string("VariLC found on ")+port_.c_str(), true);
					LogMessage(std::string("VariLC serial no:")+serialnum_, true);
					result = MM::CanCommunicate;	
					GetCoreCallback()->SetSerialProperties(port_.c_str(),
											  "600.0",
											  baud_.c_str(),
											  "0.0",
											  "Off",
											  "None",
											  "1");
					serialnum_ = "0";
					pS->Initialize();
					ret = SendSerialCommand(port_.c_str(), "R 1", "\r");
					ret = SendSerialCommand(port_.c_str(), "C 0", "\r");
					pS->Shutdown();					
				}
      }
   }
   catch(...)
   {
      LogMessage("Exception in DetectDevice!",false);
   }
   return result;
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
    
    // Version number
   CPropertyAction* pAct = new CPropertyAction (this, &VariLC::OnSerialNumber);
   ret = CreateProperty("Version Number", "Version Number Not Found", MM::String, true, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Active LC number
   pAct = new CPropertyAction (this, &VariLC::OnNumActiveLCs);
   ret = CreateProperty("Active LCs", "0", MM::Integer, true, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Total LC number
   pAct = new CPropertyAction (this, &VariLC::OnNumTotalLCs);
   ret = CreateProperty("Total LCs", "0", MM::Integer, true, pAct);
   if (ret != DEVICE_OK)
      return ret;

   pAct = new CPropertyAction (this, &VariLC::OnBriefMode);
   ret = CreateProperty("Mode; 1=Brief; 0=Standard", "", MM::String, true, pAct); 
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

   // Wavelength
   pAct = new CPropertyAction (this, &VariLC::OnWavelength);
   ret = CreateProperty("Wavelength", DoubleToString(wavelength_).c_str(), MM::Float, false, pAct); 
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits("Wavelength", 400., 800.);

   // Delay
   pAct = new CPropertyAction (this, &VariLC::OnDelay);
   ret = CreateProperty("Device Delay (ms.)", "200.0", MM::Float, false, pAct); 
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits("Device Delay (ms.)", 0.0, 200.0);
   
   CPropertyActionEx *pActX = 0;
//	 create an extended (i.e. array) properties 0 through 1
	
   for (long i=0;i<numActiveLCs_;++i) {
	   ostringstream s;
	   s << "Retardance LC-" << char(65+i);
	   pActX = new CPropertyActionEx(this, &VariLC::OnRetardance, i);
	   CreateProperty(s.str().c_str(), "0.5", MM::Float, false, pActX);
	   SetPropertyLimits(s.str().c_str(), 0.0001, 3);
   }

   // Absolute Retardance controls -- after Voltage controls
   for (long i=0;i<numActiveLCs_;++i) {
	   ostringstream s;
	   s << "Retardance LC-" << char(65+i) << " [in nm.]";
	   pActX = new CPropertyActionEx(this, &VariLC::OnAbsRetardance, i);
	   CreateProperty(s.str().c_str(), "100", MM::Float, true, pActX);
   }
 
//   for (long i=0;i<numPalEls_;++i) {
//	   ostringstream s;
//	   s << "Pal. elem. " << char(48+i) << ", enter 0 to define, 1 to activate";
//	   pActX = new CPropertyActionEx(this, &VariLC::OnPalEl, i);
//	   CreateProperty(s.str().c_str(), "", MM::String, false, pActX);
//   }

   for (long i=0;i<numPalEls_;++i) {
	   ostringstream s;
	   std::string number;

	   std::stringstream strstream;
	   strstream << i;
	   strstream >> number;
	   if (i < 10) {
			number = "0"+number;
		}
	   

	   s << "Pal. elem. " << number << "; enter 0 to define; 1 to activate";
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
   // changedTime_ = GetCurrentMMTime();
   SetErrorText(99, "Device set busy for ");

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

int VariLC::OnBaud(MM::PropertyBase* pProp, MM::ActionType eAct)
{	
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(baud_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      if (initialized_)
      {
         pProp->Set(baud_.c_str());
         return DEVICE_INVALID_INPUT_PARAM;
      }
      pProp->Get(baud_);
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

 int VariLC::OnSerialNumber (MM::PropertyBase* pProp, MM::ActionType eAct)
 {
   if (eAct == MM::BeforeGet)
  {
     int ret = SendSerialCommand(port_.c_str(), "V?", "\r");
         if (ret!=DEVICE_OK)return DEVICE_SERIAL_COMMAND_FAILED;
	 GetSerialAnswer (port_.c_str(), "\r", serialnum_);
	 GetSerialAnswer (port_.c_str(), "\r", serialnum_);
	 
	 pProp->Set(serialnum_.c_str());
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

	   ostringstream s;
	   s << "Retardance LC-" << char(65+index) << " [in nm.]";
	   std::string s2 = DoubleToString(retardance_[index]*wavelength_);
	   SetProperty(s.str().c_str(), s2.c_str());

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

int VariLC::OnAbsRetardance(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{	       
   if (eAct == MM::BeforeGet)
   {	 
     if (index+1 > 0) {	    
	   pProp->Set(retardance_[index]*wavelength_);
	 } else {
		 return DEVICE_INVALID_PROPERTY_VALUE;
	 }
   }
   else if (eAct == MM::AfterSet)
   {
	 
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
		for (int i=0;i<numPalEls_+1;i++) {
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
	  
	  size_t len = strlen(sendToVariLC_.c_str());
	  char state[6];

	  if (len > 5) {		  
		  strncpy(state, sendToVariLC_.c_str(), 5);
		  state[5] = '\0';
	  }

      if (sendToVariLC_=="Escape") {
         char command[2];
         command[0]=27;
         command[1]=0;
         int ret = SendSerialCommand(port_.c_str(), command, "\r");
         if (ret!=DEVICE_OK)
		   return DEVICE_SERIAL_COMMAND_FAILED;		 
      } 
	  else if (sendToVariLC_=="@") {         
		 int ret = SendSerialCommand(port_.c_str(), sendToVariLC_.c_str(), "\r");
         if (ret!=DEVICE_OK)
		      return DEVICE_SERIAL_COMMAND_FAILED;
      }
	  else if (sendToVariLC_=="!") {                  
		 int ret = SendSerialCommand(port_.c_str(), sendToVariLC_.c_str(), "\r");		 
         if (ret!=DEVICE_OK)
		      return DEVICE_SERIAL_COMMAND_FAILED;		
      }
	  else if ((std::string)state=="State") {   
		  	  
			  std::vector<char> val(len-5);
			  for (size_t i=5; i < len; i++) {
					val[5-i] = sendToVariLC_[i];
			  }
			  val[len] = '\0';

			  std::stringstream ss;
				for(size_t i = 0; i < val.size(); ++i)
				{
				  if(i != 0)
					ss << ",";
				  ss << val[i];
				}
			 std::string s = ss.str();

			 sendToVariLC_ = "P" + s;

			 changedTime_ = GetCurrentMMTime();
			 int ret = SendSerialCommand(port_.c_str(), sendToVariLC_.c_str(), "\r");
				 if (ret!=DEVICE_OK) {
				   return DEVICE_SERIAL_COMMAND_FAILED;
				 }
      }
	  else if (sendToVariLC_=="W ?" || sendToVariLC_=="W?") {
		  int ret = SendSerialCommand(port_.c_str(), "W?", "\r");
			 if (ret!=DEVICE_OK)return DEVICE_SERIAL_COMMAND_FAILED;
		 GetSerialAnswer (port_.c_str(), "\r", getFromVariLC_);		 
	  }
	  else if (sendToVariLC_=="V ?" || sendToVariLC_=="V?") {
		  int ret = SendSerialCommand(port_.c_str(), "V?", "\r");
			 if (ret!=DEVICE_OK)return DEVICE_SERIAL_COMMAND_FAILED;
		 GetSerialAnswer (port_.c_str(), "\r", getFromVariLC_);		 
	  }
	  else if (sendToVariLC_=="R ?" || sendToVariLC_=="R?") {
		  int ret = SendSerialCommand(port_.c_str(), "R?", "\r");
			 if (ret!=DEVICE_OK)return DEVICE_SERIAL_COMMAND_FAILED;
		 GetSerialAnswer (port_.c_str(), "\r", getFromVariLC_);
		 GetSerialAnswer (port_.c_str(), "\r", getFromVariLC_);
		 getFromVariLC_ = removeSpaces(getFromVariLC_);
		 return DEVICE_OK;
	  }
	  else if (sendToVariLC_=="B ?" || sendToVariLC_=="B?") {
		  int ret = SendSerialCommand(port_.c_str(), "B?", "\r");
			 if (ret!=DEVICE_OK)return DEVICE_SERIAL_COMMAND_FAILED;
		 GetSerialAnswer (port_.c_str(), "\r", getFromVariLC_);		 
	  }
	  else if (sendToVariLC_=="L ?" || sendToVariLC_=="L?") {
		  int ret = SendSerialCommand(port_.c_str(), "L?", "\r");
			 if (ret!=DEVICE_OK)return DEVICE_SERIAL_COMMAND_FAILED;
		 GetSerialAnswer (port_.c_str(), "\r", getFromVariLC_);		 
	  }
	  else {
         int ret = SendSerialCommand(port_.c_str(), sendToVariLC_.c_str(), "\r");
         if (ret!=DEVICE_OK)
		      return DEVICE_SERIAL_COMMAND_FAILED;		 
      }
	  
		GetSerialAnswer (port_.c_str(), "\r", getFromVariLC_);  
	  
   }
   return DEVICE_OK;
}

int VariLC::OnGetFromVariLC(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	//   GetSerialAnswer (port_.c_str(), "\r", getFromVariLC_);
      pProp->Set(getFromVariLC_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {

   }
   return DEVICE_OK;
}

 int VariLC::OnDelay(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
	 double delay = GetDelayMs();
	 initializedDelay_ = true;
	 pProp->Set(delay);
    }
   else if (eAct == MM::AfterSet)
   {
	  double delayT;
	  pProp->Get(delayT);
	  if (initializedDelay_) {
		SetDelayMs(delayT);
	  }
	  delay = delayT*1000;
   }
   
   return DEVICE_OK;
}

bool VariLC::Busy()
{
   if (delay.getMsec() > 0.0) {
      MM::MMTime interval = GetCurrentMMTime() - changedTime_;      
      if (interval.getMsec() < delay.getMsec() ) {
         return true;
      }
   }
   
   return false;
}

std::string VariLC::DoubleToString(double N)
{
    ostringstream ss("");
    ss << N;
    return ss.str();
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

static inline bool IsSpace(char ch)
{
   return std::isspace(ch);
}

std::string VariLC::removeSpaces(std::string input)
{
   input.erase(std::remove_if(input.begin(), input.end(), IsSpace), input.end());
   return input;
}
