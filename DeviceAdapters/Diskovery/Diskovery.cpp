///////////////////////////////////////////////////////////////////////////////
//// FILE:       Diskovery.cpp
//// PROJECT:    MicroManage
//// SUBSYSTEM:  DeviceAdapters
////-----------------------------------------------------------------------------
//// DESCRIPTION:
//// The basic ingredients for a device adapter
////                
//// AUTHOR: Nico Stuurman, 1/16/2006
////
//

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "Diskovery.h"
#include <string>
#include <math.h>
#include "../../MMDevice/ModuleInterface.h"
#include <sstream>


///////////////////////////////////////////////////////////////////////////////
// Devices in this adapter.  
// The device name needs to be a class name in this file

// Diskovery device
const char* g_Diskoveryname = "Diskovery";
///////////////////////////////////////////////////////////////////////////////

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_Diskoveryname, MM::GenericDevice, "Diskovery1");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{                                                                            
   if (deviceName == 0)                                                      
       return 0;

   if (strcmp(deviceName, g_Diskoveryname) == 0)
   {
        Diskovery* pDiskovery = new Diskovery();
        return pDiskovery;
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)                            
{                                                                            
   delete pDevice;                                                           
}


///////////////////////////////////////////////////////////////////////////////
// Diskovery
//
Diskovery::Diskovery() :
   initialized_(false),
   port_("Undefined"),                                                       
   answerTimeoutMs_(1000),
   model_(*this, *GetCoreCallback())
{
   InitializeDefaultErrorMessages();

   // create pre-initialization properties
   // ------------------------------------

   // Name
   CreateProperty(MM::g_Keyword_Name, g_Diskoveryname, MM::String, true);
   
   // Description                                                            
   CreateProperty(MM::g_Keyword_Description, "Spinning Disk Confocal and TIRF module", MM::String, true);
 
   // Port                                                                   
   CPropertyAction* pAct = new CPropertyAction (this, &Diskovery::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);

}

Diskovery::~Diskovery() 
{
   Shutdown();
}

int Diskovery::Initialize() 
{
   // get information from the device 
  
   // Hardware version
   unsigned int hwmajor;
   RETURN_ON_MM_ERROR ( QueryCommandInt("Q:VERSION_HW_MAJOR", &hwmajor) );
   unsigned int hwminor;
   RETURN_ON_MM_ERROR ( QueryCommandInt("Q:VERSION_HW_MINOR", &hwminor) );
   unsigned int hwrevision;
   RETURN_ON_MM_ERROR ( QueryCommandInt("Q:VERSION_HW_REVISION", &hwrevision) );
   std::ostringstream oss;
   oss << hwmajor << "." << hwminor << "." << hwrevision;
   hardwareVersion_ = oss.str();

   CPropertyAction *pAct = new CPropertyAction (this, &Diskovery::OnHardwareVersion);
   int nRet = CreateStringProperty(model_.hardwareVersionProp_, hardwareVersion_.c_str(), true, pAct);
   assert(nRet == DEVICE_OK);

   // Firmware version
   unsigned int fwmajor;
   RETURN_ON_MM_ERROR ( QueryCommandInt("Q:VERSION_FW_MAJOR", &fwmajor) );
   unsigned int fwminor;
   RETURN_ON_MM_ERROR ( QueryCommandInt("Q:VERSION_FW_MINOR", &fwminor) );
   unsigned int fwrevision;
   RETURN_ON_MM_ERROR ( QueryCommandInt("Q:VERSION_FW_REVISION", &fwrevision) );
   std::ostringstream oss2;
   oss2 << fwmajor << "." << fwminor << "." << fwrevision;
   firmwareVersion_ = oss2.str();

   pAct = new CPropertyAction (this, &Diskovery::OnFirmwareVersion);
   nRet = CreateStringProperty(model_.firmwareVersionProp_, firmwareVersion_.c_str(), true, pAct);
   assert(nRet == DEVICE_OK);

   // Manufacturing date
   unsigned int year;
   RETURN_ON_MM_ERROR ( QueryCommandInt("Q:MANUFACTURE_YEAR", &year) );
   unsigned int month;
   RETURN_ON_MM_ERROR ( QueryCommandInt("Q:MANUFACTURE_MONTH", &month) );
   unsigned int day;
   RETURN_ON_MM_ERROR ( QueryCommandInt("Q:MANUFACTURE_DAY", &day) );
   std::ostringstream oss3;
   oss3 << "20" << year << "-" << month << "-" << day;
   manufacturingDate_ = oss3.str();

   pAct = new CPropertyAction (this, &Diskovery::OnManufacturingDate);
   nRet = CreateStringProperty(model_.manufacturingDateProp_, manufacturingDate_.c_str(), true, pAct);

   // Serial Number
   RETURN_ON_MM_ERROR ( QueryCommand("Q:PRODUCT_SERIAL_NO", serialNumber_) );
   pAct = new CPropertyAction (this, &Diskovery::OnSerialNumber);
   nRet = CreateStringProperty(model_.serialNumberProp_, serialNumber_.c_str(), true, pAct);


   // Spinning disk preset position
   pAct = new CPropertyAction(this, &Diskovery::OnSpDiskPresetPosition);
   nRet = CreateIntegerProperty(model_.spinningDiskPositionProp_, spDiskPos_, false, pAct);

   return DEVICE_OK;
}

int Diskovery::Shutdown() 
{
   return DEVICE_OK;
}

void Diskovery::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_Diskoveryname);
}

bool Diskovery::Busy() 
{
   return false;
}

///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////

int Diskovery::OnPort(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(port_.c_str());
   } else if (eAct == MM::AfterSet) {
      if (initialized_) {
         // revert
         pProp->Set(port_.c_str());
         return ERR_PORT_CHANGE_FORBIDDEN;
      }

      pProp->Get(port_);
   }

   return DEVICE_OK;
}


int Diskovery::OnHardwareVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(hardwareVersion_.c_str());
   }
   
   return DEVICE_OK;
}

int Diskovery::OnFirmwareVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(firmwareVersion_.c_str());
   }
   
   return DEVICE_OK;
}

int Diskovery::OnManufacturingDate(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(manufacturingDate_.c_str());
   }
   
   return DEVICE_OK;
}

int Diskovery::OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(serialNumber_.c_str());
   }
   
   return DEVICE_OK;
}

int Diskovery::OnSpDiskPresetPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      RETURN_ON_MM_ERROR ( QueryCommandInt("Q:PRESET_SD", &spDiskPos_) );
      pProp->Set((long) spDiskPos_);
   }
   else if (eAct == MM::AfterSet) {
      std::ostringstream os;
      long tmp;
      pProp->Get(tmp);
      os << "A:PRESET_SD," << tmp;
      RETURN_ON_MM_ERROR( QueryCommandInt(os.str().c_str(), &spDiskPos_) );
   }
   
   return DEVICE_OK;
}



// Device communication functions

int Diskovery::QueryCommand(const char* command, std::string& answer) 
{
   RETURN_ON_MM_ERROR (PurgeComPort(port_.c_str()));
   RETURN_ON_MM_ERROR (SendSerialCommand(port_.c_str(), command, "\n"));
   RETURN_ON_MM_ERROR (GetSerialAnswer(port_.c_str(), "\r\n", answer));
   std::vector<std::string> tokens = split(answer, '=');
   if (tokens.size() == 2) {
      answer = tokens[1];
      return DEVICE_OK;
   }
   // TODO find appropriate error code
   return 1;
}

int Diskovery::QueryCommandInt(const char* command, unsigned int* result) 
{
   RETURN_ON_MM_ERROR (PurgeComPort(port_.c_str()));
   RETURN_ON_MM_ERROR (SendSerialCommand(port_.c_str(), command, "\n"));
   // even though the documentation states that an integer is returned, in real life
   // the device returns a string that ends with "=#", where "#" is what we want
   std::string answer;
   RETURN_ON_MM_ERROR (GetSerialAnswer(port_.c_str(), "\r\n", answer));
   std::vector<std::string> tokens = split(answer, '=');
   if (tokens.size() == 2) {
      istringstream(tokens[1].c_str()) >> *result;
      return DEVICE_OK;
   }
   // TODO find appropriate error code
   return 1;
}

std::vector<std::string>& Diskovery::split(const std::string &s, char delim, std::vector<std::string> &elems) {
    std::stringstream ss(s);
    std::string item;
    while (std::getline(ss, item, delim)) {
        elems.push_back(item);
    }
    return elems;
}

std::vector<std::string> Diskovery::split(const std::string &s, char delim) {
    std::vector<std::string> elems;
    split(s, delim, elems);
    return elems;
}

