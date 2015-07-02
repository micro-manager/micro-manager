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
   RegisterDevice(g_Diskoveryname, MM::GenericDevice, "Diskovery");
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
   port_("Undefined"),                                                       
   model_(0),
   listener_(0),
   commander_(0)
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
   model_ = new DiskoveryModel(*this, *GetCoreCallback());
   listener_ = new DiskoveryListener(*this, *GetCoreCallback(), port_, model_);
   commander_ = new DiskoveryCommander(*this, *GetCoreCallback(), port_, model_);
   listener_->Start();
   RETURN_ON_MM_ERROR( commander_->Initialize() );

   // Create properties storing information from the device 
  
   // Hardware version
   CPropertyAction *pAct = new CPropertyAction (this, &Diskovery::OnHardwareVersion);
   int nRet = CreateStringProperty(model_->hardwareVersionProp_, 
         model_->GetHardwareVersion().c_str(), true, pAct);
   assert(nRet == DEVICE_OK);

   // Firmware version
   pAct = new CPropertyAction (this, &Diskovery::OnFirmwareVersion);
   nRet = CreateStringProperty(model_->firmwareVersionProp_, 
         model_->GetFirmwareVersion().c_str(), true, pAct);
   assert(nRet == DEVICE_OK);

   // Manufacturing date
   /*
   unsigned int year;
   RETURN_ON_MM_ERROR ( QueryCommandInt("Q:MANUFACTURE_YEAR", &year) );
   unsigned int month;
   RETURN_ON_MM_ERROR ( QueryCommandInt("Q:MANUFACTURE_MONTH", &month) );
   unsigned int day;
   RETURN_ON_MM_ERROR ( QueryCommandInt("Q:MANUFACTURE_DAY", &day) );
   std::ostringstream oss3;
   oss3 << "20" << year << "-" << month << "-" << day;
   manufacturingDate_ = oss3.str();
   */

   pAct = new CPropertyAction (this, &Diskovery::OnManufacturingDate);
   nRet = CreateStringProperty(model_->manufacturingDateProp_, 
         model_->GetManufacturingDate().c_str(), true, pAct);

   // Serial Number
   // RETURN_ON_MM_ERROR ( QueryCommand("Q:PRODUCT_SERIAL_NO", serialNumber_) );
   pAct = new CPropertyAction (this, &Diskovery::OnSerialNumber);
   nRet = CreateStringProperty(model_->serialNumberProp_, 
         model_->GetSerialNumber().c_str(), true, pAct);


   // Spinning disk preset position
   pAct = new CPropertyAction(this, &Diskovery::OnSpDiskPresetPosition);
   nRet = CreateIntegerProperty(model_->spinningDiskPositionProp_, model_->GetPresetSD(), 
         false, pAct);

   return DEVICE_OK;
}

int Diskovery::Shutdown() 
{
   if (listener_ != 0)
      delete(listener_);
   if (commander_ != 0)
      delete(commander_);
   if (model_ != 0)
      delete(model_);
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
      pProp->Get(port_);
   }

   return DEVICE_OK;
}


int Diskovery::OnHardwareVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(model_->GetHardwareVersion().c_str());
   }
   
   return DEVICE_OK;
}

int Diskovery::OnFirmwareVersion(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(model_->GetFirmwareVersion().c_str());
   }
   
   return DEVICE_OK;
}

int Diskovery::OnManufacturingDate(MM::PropertyBase* pProp, MM::ActionType eAct) 
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(model_->GetManufacturingDate().c_str());
   }
   
   return DEVICE_OK;
}

int Diskovery::OnSerialNumber(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set(model_->GetSerialNumber().c_str());
   }
   
   return DEVICE_OK;
}

int Diskovery::OnSpDiskPresetPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      pProp->Set((long) model_->GetPresetSD() );
   }
   else if (eAct == MM::AfterSet) {
      long tmp;
      pProp->Get(tmp);
      RETURN_ON_MM_ERROR( commander_->SetPresetSD( (uint16_t) tmp) );
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

