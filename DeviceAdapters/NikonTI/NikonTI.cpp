///////////////////////////////////////////////////////////////////////////////
// FILE:       NikonTI.cpp
// PROJECT:    MicroManager
// SUBSYSTEM:  DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Nikon TI microscope adapter
//   
// COPYRIGHT:     University of California, San Francisco, 2008
// LICENSE:       This code has been developd using information provided by Nikon under a non-disclosure
//                agreement.  Therefore, this code can not be made publicly available unless Nikon provides permission to do so
//                It is the intend of the author to provide this code with the LGPL License once allowed by Nikon
// AUTHOR:        Nico Stuurman, nico@cmp.ucsf.edu 4/10/2008,


#include "NikonTI.h"
#include "NikonTIErrorCodes.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceBase.h"

#ifdef WIN32
//#include <Afxwin.h>
#include <windows.h>
#include "NikonTICOMInterface.h"

// This is the global object referring to our microscope model
TICOMModel* TIScope = new TICOMModel();

// windows dll entry code
   BOOL APIENTRY DllMain( HANDLE /*hModule*/,                                
                          DWORD  ul_reason_for_call,                         
                          LPVOID /*lpReserved*/                              
                   )                                                         
   {                                                                         
      switch (ul_reason_for_call)                                            
      {                                                                      
      case DLL_PROCESS_ATTACH:                                               
      break;                                                                 
      case DLL_THREAD_ATTACH:    
      break;                                                                 
      case DLL_THREAD_DETACH:
      break;
      case DLL_PROCESS_DETACH:
      break;
      }
       return TRUE;
   }
#endif

///////////////////////////////////////////////////////////////////////////////
// Devices in this adapter.  
// The device name needs to be a class name in this file

// TI Devices
const char* g_TIDeviceName = "TIScope";
const char* g_TINosepiece = "TINosePiece";
const char* g_TIFilterBlock1 = "TIFilterBlock1";
const char* g_TIFilterBlock2 = "TIFilterBlock2";
const char* g_TILightPath = "TILightPath";
const char* g_TIZDrive = "TIZDrive";
const char* g_TIXYDrive = "TIXYDrive";
const char* g_TIPFSOffset = "TIPFSOffset";
const char* g_TIPFSStatus = "TIPFSStatus";
const char* g_TIEpiShutter = "TIEpiShutter";
const char* g_TIDiaShutter = "TIDiaShutter";
const char* g_TIAuxShutter = "TIAuxShutter";

MODULE_API void InitializeModuleData()
{
   AddAvailableDeviceName(g_TIDeviceName,"Nikon TI microscope");
   AddAvailableDeviceName(g_TINosepiece,"Objective Turret");
   AddAvailableDeviceName(g_TIFilterBlock1,"FilterBlock 1");
   AddAvailableDeviceName(g_TIFilterBlock2,"FilterBlock 2");
   AddAvailableDeviceName(g_TILightPath,"LightPath");
   AddAvailableDeviceName(g_TIZDrive,"Z-Stage");
   AddAvailableDeviceName(g_TIXYDrive,"XY-Stage");
   AddAvailableDeviceName(g_TIPFSOffset,"PFS Offset");
   AddAvailableDeviceName(g_TIPFSStatus,"PFS Status");
   AddAvailableDeviceName(g_TIEpiShutter,"Epi Shutter");
   AddAvailableDeviceName(g_TIDiaShutter,"Dia Shutter");
   AddAvailableDeviceName(g_TIAuxShutter,"Aux Shutter");
}

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{                                                                            
   if (deviceName == 0)                                                      
       return 0;

   if (strcmp(deviceName, g_TIDeviceName) == 0)
        return new NikonTIHub();
   else if (strcmp(deviceName, g_TINosepiece) == 0)
        return new NosePiece();
   else if (strcmp(deviceName, g_TIFilterBlock1) == 0)
      return new FilterBlock(1);
   else if (strcmp(deviceName, g_TIFilterBlock2) == 0)
      return new FilterBlock(2);
   else if (strcmp(deviceName, g_TILightPath) == 0)
      return new LightPath();
   else if (strcmp(deviceName, g_TIXYDrive) == 0)
      return new XYDrive();
   else if (strcmp(deviceName, g_TIZDrive) == 0)
      return new ZDrive();
   else if (strcmp(deviceName,g_TIPFSOffset) == 0)
	   return new PFSOffset();
   else if (strcmp(deviceName,g_TIPFSStatus) == 0)
	   return new PFSStatus();
   else if (strcmp(deviceName,g_TIEpiShutter) == 0 )
	   return new EpiShutter();

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)                            
{                                                                            
   delete pDevice;                                                           
}

//////////////////////////////////////////
// Interface to the microscope
NikonTIHub::NikonTIHub()
{
}

NikonTIHub::~NikonTIHub()
{
}


int NikonTIHub::Initialize()
{
   if (!TIScope->IsInitialized())
      TIScope->Initialize();

   return DEVICE_OK;
}

int NikonTIHub::Shutdown()
{
   return DEVICE_OK;
}

bool NikonTIHub::Busy()
{
   return false;
}

void NikonTIHub::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_TIDeviceName);
}




/////////////////////////////////////
// Nikon TI NosePiece

NosePiece::NosePiece() :
   numPos_ (6),
   name_ (g_TINosepiece)
{
   SetErrorText(ERR_MODULE_NOT_FOUND, "No nosepiece installed in this microscope");
}

NosePiece::~NosePiece()
{
}


int NosePiece::Initialize()
{
   if (!TIScope->IsInitialized())
      TIScope->Initialize();

   // check if this device exists:
   //bool present = TIScope->nosepiece_->GetIsMounted();
   if (!TIScope->nosepiece_->GetIsMounted())
      return ERR_MODULE_NOT_FOUND;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &NosePiece::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   std::vector<std::string> values;
   numPos_ = TIScope->nosepiece_->GetUpperLimit() - TIScope->nosepiece_->GetLowerLimit() + 1;
   for (unsigned long i = 0; i < numPos_; i++) {
       std::ostringstream os;
       os << i;
       values.push_back(os.str().c_str());       
   }
   ret = SetAllowedValues(MM::g_Keyword_State, values);
   if (ret != DEVICE_OK)
      return ret; 

   // Label
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   std::vector<std::string> labels = TIScope->nosepiece_->GetLabels();
   for (unsigned long i=0; i < labels.size(); i++) {
         SetPositionLabel(i, labels[i].c_str());
         AddAllowedValue(MM::g_Keyword_Label, labels[i].c_str());
   }

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 


   return DEVICE_OK;
}

int NosePiece::Shutdown()
{
   return DEVICE_OK;
}
    
void NosePiece::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_TIDeviceName);
}

bool NosePiece::Busy()
{
   return TIScope->nosepiece_->Busy();
}


// action interface
// ---------------
int NosePiece::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      long position;
      int ret = TIScope->nosepiece_->GetPosition(position);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(position - TIScope->nosepiece_->GetLowerLimit());
   } else if (eAct ==MM::AfterSet)
   {
      long position;
      pProp->Get(position);
      int ret = TIScope->nosepiece_->SetPosition(position + TIScope->nosepiece_->GetLowerLimit());
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

int NosePiece::OnValue(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      long position;
      int ret = TIScope->nosepiece_->GetValue(position);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(position);
   } else if (eAct ==MM::AfterSet)
   {
      long position;
      pProp->Get(position);
      int ret = TIScope->nosepiece_->SetValue(position);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}


/////////////////////////////////////
// Nikon TI FilterBlock

FilterBlock::FilterBlock(int blockNumber) :
   numPos_ (6)
{
   if (blockNumber == 1) {
      name_ = g_TIFilterBlock1;
      pFilterBlock_ = TIScope->filterBlock1_;
   } else if (blockNumber == 2) {
      name_ = g_TIFilterBlock2;
      pFilterBlock_ = TIScope->filterBlock2_;
   }
   SetErrorText(ERR_MODULE_NOT_FOUND, "This FilterBlock is not installed in the microscope");
}

FilterBlock::~FilterBlock()
{
}

int FilterBlock::Initialize()
{
   if (!TIScope->IsInitialized())
      TIScope->Initialize();

   // check if this device exists:
   // TODO: differentiate between block a and 2
   bool present = pFilterBlock_->GetIsMounted();
   if (!present)
      return ERR_MODULE_NOT_FOUND;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &FilterBlock::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   std::vector<std::string> values;
   numPos_ = pFilterBlock_->GetUpperLimit() - pFilterBlock_->GetLowerLimit() + 1;
   for (unsigned long i = 0; i < numPos_; i++) {
       std::ostringstream os;
       os << i;
       values.push_back(os.str().c_str());       
   }
   ret = SetAllowedValues(MM::g_Keyword_State, values);
   if (ret != DEVICE_OK)
      return ret; 

   // Label
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   std::vector<std::string> labels = pFilterBlock_->GetLabels();
   for (unsigned long i=0; i < labels.size(); i++) {
         SetPositionLabel(i, labels[i].c_str());
         AddAllowedValue(MM::g_Keyword_Label, labels[i].c_str());
   }

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 


   return DEVICE_OK;
}

int FilterBlock::Shutdown()
{
   return DEVICE_OK;
}
    
void FilterBlock::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_TIDeviceName);
}

bool FilterBlock::Busy()
{
   return pFilterBlock_->Busy();
}


// action interface
// ---------------
int FilterBlock::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      long position;
      int ret = pFilterBlock_->GetPosition(position);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(position - pFilterBlock_->GetLowerLimit());
   } else if (eAct ==MM::AfterSet)
   {
      long position;
      pProp->Get(position);
      int ret = pFilterBlock_->SetPosition(position + pFilterBlock_->GetLowerLimit());
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

/////////////////////////////////////
// Nikon TI LightPath

LightPath::LightPath() :
   numPos_ (6)
{
   pLightPath_ = TIScope->pLightPath_;

   SetErrorText(ERR_MODULE_NOT_FOUND, "No LightPath installed in this microscope");
}

LightPath::~LightPath()
{
}

int LightPath::Initialize()
{
   if (!TIScope->IsInitialized())
      TIScope->Initialize();

   // check if this device exists:
   // TODO: differentiate between block a and 2
   bool present = pLightPath_->GetIsMounted();
   if (!present)
      return ERR_MODULE_NOT_FOUND;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &LightPath::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   std::vector<std::string> values;
   numPos_ = pLightPath_->GetUpperLimit() - pLightPath_->GetLowerLimit() + 1;
   for (unsigned long i = 0; i < numPos_; i++) {
       std::ostringstream os;
       os << i;
       values.push_back(os.str().c_str());       
   }
   ret = SetAllowedValues(MM::g_Keyword_State, values);
   if (ret != DEVICE_OK)
      return ret; 

   // Label
   pAct = new CPropertyAction (this, &CStateBase::OnLabel);
   ret = CreateProperty(MM::g_Keyword_Label, "", MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   std::vector<std::string> labels = pLightPath_->GetLabels();
   for (unsigned long i=0; i < labels.size(); i++) {
         SetPositionLabel(i, labels[i].c_str());
         AddAllowedValue(MM::g_Keyword_Label, labels[i].c_str());
   }

   ret = UpdateStatus();
   if (ret != DEVICE_OK) 
      return ret; 


   return DEVICE_OK;
}

int LightPath::Shutdown()
{
   return DEVICE_OK;
}
    
void LightPath::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_TIDeviceName);
}

bool LightPath::Busy()
{
   return pLightPath_->Busy();
}


// action interface
// ---------------
int LightPath::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      long position;
      int ret = pLightPath_->GetPosition(position);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(position - pLightPath_->GetLowerLimit());
   } else if (eAct ==MM::AfterSet)
   {
      long position;
      pProp->Get(position);
      int ret = pLightPath_->SetPosition(position + pLightPath_->GetLowerLimit());
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}




/////////////////////////////////////
// Nikon TI ZDrive

ZDrive::ZDrive() :
//TODO: get the step size (e.g. factor) from Nikon Scope object and not use magic number 40. 
   factor_ (40.0),
   name_ (g_TIZDrive)
{
   SetErrorText(ERR_MODULE_NOT_FOUND, "No Z Drive installed in this microscope");
   SetErrorText(ERR_SETTING_Z_POSITION, "Error while setting Z Drive position");
   SetErrorText(ERR_GETTING_Z_POSITION, "Error while getting Z Drive position");
}

ZDrive::~ZDrive()
{
}

int ZDrive::Initialize()
{
   if (!TIScope->IsInitialized())
      TIScope->Initialize();

   // check if this device exists:
   if (!TIScope->zDrive_->GetIsMounted())
      return ERR_MODULE_NOT_FOUND;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Position property
   CPropertyAction* pAct = new CPropertyAction (this, &ZDrive::OnPosition);
   ret = CreateProperty("Position", "0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   double min, max;
   ret = GetLimits(min, max);
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits("Position", min, max);

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}
 
bool ZDrive::Busy()
{
   return TIScope->zDrive_->Busy();
}

int ZDrive::Shutdown()
{
   return DEVICE_OK;
}

void ZDrive::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_TIZDrive);
}

int ZDrive::SetPositionUm(double position)
{
   LogMessage("Setting Z position", true);
   // Assume that the TI gives its position in nm...
   int ret = TIScope->zDrive_->SetPosition((long) (position * factor_) );
   if (ret != DEVICE_OK)
      return ERR_SETTING_Z_POSITION;

   return DEVICE_OK;
}

int ZDrive::GetPositionUm(double& position)
{
   LogMessage("Getting Z position", true);
   long positionL;
   int ret = TIScope->zDrive_->GetPosition(positionL);
   if (ret != DEVICE_OK)
      return ERR_GETTING_Z_POSITION;
   position = positionL / factor_;

   return DEVICE_OK;
}
   
int ZDrive::SetPositionSteps(long steps)
{
   // Consider steps to be native TI unit (nm?)
   if (!TIScope->zDrive_->SetPosition(steps))
      return ERR_SETTING_Z_POSITION;

   return DEVICE_OK;
}

int ZDrive::GetPositionSteps(long& steps)
{
   if (!TIScope->zDrive_->GetPosition(steps))
      return ERR_GETTING_Z_POSITION;

   return DEVICE_OK;
}

int ZDrive::SetOrigin()
{
   // TODO: implement
   return DEVICE_OK;
}

int ZDrive::GetLimits(double& lower, double& upper)
{

   lower = TIScope->zDrive_->GetLowerLimit() / factor_;
   upper = TIScope->zDrive_->GetUpperLimit() / factor_;

   return DEVICE_OK;
}


int ZDrive::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   double position;
   if (eAct == MM::BeforeGet)
   {
      int ret = GetPositionUm(position);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(position);
   } else if (eAct ==MM::AfterSet)
   {
      pProp->Get(position);
      int ret = SetPositionUm(position);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

/////////////////////////////////////
// Nikon TI XYDrive (mostly blindly copy-paste with somewhat eligable changes from Nico's ZDrive

XYDrive::XYDrive() : 
//TODO: get the step size (e.g. factor) from Nikon Scope object and not use magic number 40. 
	factor_ (40.0),
	name_ (g_TIXYDrive)
{
		SetErrorText(ERR_MODULE_NOT_FOUND, "No XY Drive installed in this microscope");
		SetErrorText(ERR_SETTING_XY_POSITION, "Error while setting XY Drive position");
		SetErrorText(ERR_GETTING_XY_POSITION, "Error while getting XY Drive position");
};

XYDrive::~XYDrive()
{
};


int XYDrive::Initialize()
{
	if (!TIScope->IsInitialized())
      TIScope->Initialize();

   // check if this device exists:
   if (!TIScope->xDrive_->GetIsMounted() || !TIScope->yDrive_->GetIsMounted() )
      return ERR_MODULE_NOT_FOUND;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Position property for X and Y
   CPropertyAction* pAct = new CPropertyAction (this, &XYDrive::OnPositionX);
   ret = CreateProperty("XPosition", "0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   pAct = new CPropertyAction (this, &XYDrive::OnPositionY);
   ret = CreateProperty("YPosition", "0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   double minx, maxx,miny, maxy;
   ret = GetLimits(minx, maxx,miny, maxy);
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits("XPosition", minx, maxx);
   SetPropertyLimits("YPosition", miny, maxy);

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;

}

bool XYDrive::Busy()
{
	return (TIScope->yDrive_->Busy() || TIScope->xDrive_->Busy());
}

int XYDrive::Shutdown()
{
   return DEVICE_OK;
}

void XYDrive::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_TIXYDrive);
}

int XYDrive::SetPositionUmX(double position)
{
   LogMessage("Setting X position", true);
   // Assume that the TI gives its position in nm...
   int ret = TIScope->xDrive_->SetPosition((long) (position * factor_) );
   if (ret != DEVICE_OK)
      return ERR_SETTING_XY_POSITION;

   return DEVICE_OK;
}

int XYDrive::SetPositionUmY(double position)
{
   LogMessage("Setting X position", true);
   // Assume that the TI gives its position in nm...
   int ret = TIScope->yDrive_->SetPosition((long) (position * factor_) );
   if (ret != DEVICE_OK)
      return ERR_SETTING_XY_POSITION;

   return DEVICE_OK;
}

int XYDrive::SetPositionUm(double x,double y)
{
	LogMessage("Setting both X and Y position", true);
	int ret;
	ret=SetPositionUmX(x);
	if (ret != DEVICE_OK)
      return ERR_SETTING_XY_POSITION;
	ret=SetPositionUmY(y);
	if (ret != DEVICE_OK)
      return ERR_SETTING_XY_POSITION;

	return DEVICE_OK;
}


int XYDrive::GetPositionUmX(double& position)
{
   LogMessage("Getting X position", true);
   long positionL;
   int ret = TIScope->xDrive_->GetPosition(positionL);
   if (ret != DEVICE_OK)
      return ERR_GETTING_XY_POSITION;
   position = positionL / factor_;

   return DEVICE_OK;
}

int XYDrive::GetPositionUmY(double& position)
{
   LogMessage("Getting Y position", true);
   long positionL;
   int ret = TIScope->yDrive_->GetPosition(positionL);
   if (ret != DEVICE_OK)
      return ERR_GETTING_XY_POSITION;
   position = positionL / factor_;

   return DEVICE_OK;
}

int XYDrive::GetPositionUm(double& x,double& y) 
{
	LogMessage("Getting X and Y position", true);
	int ret;
	ret = GetPositionUmX(x);
	if (ret != DEVICE_OK)
      return ERR_GETTING_XY_POSITION;
	ret = GetPositionUmY(y);
	if (ret != DEVICE_OK)
      return ERR_GETTING_XY_POSITION;
	
    return DEVICE_OK;
}

int XYDrive::SetPositionStepsX(long steps)
{
   // Consider steps to be native TI unit (nm?)
   if (!TIScope->xDrive_->SetPosition(steps))
      return ERR_SETTING_XY_POSITION;

   return DEVICE_OK;
}

int XYDrive::GetPositionStepsX(long& steps)
{
   if (!TIScope->xDrive_->GetPosition(steps))
      return ERR_GETTING_XY_POSITION;

   return DEVICE_OK;
}

int XYDrive::SetPositionStepsY(long steps)
{
   // Consider steps to be native TI unit (nm?)
   if (!TIScope->yDrive_->SetPosition(steps))
      return ERR_SETTING_XY_POSITION;

   return DEVICE_OK;
}

int XYDrive::GetPositionStepsY(long& steps)
{
   if (!TIScope->yDrive_->GetPosition(steps))
      return ERR_GETTING_XY_POSITION;

   return DEVICE_OK;
}


int XYDrive::GetPositionSteps(long& x,long& y) 
{
	LogMessage("Getting X and Y position", true);
	int ret;
	ret = GetPositionStepsX(x);
	if (ret != DEVICE_OK)
      return ERR_GETTING_XY_POSITION;
	ret = GetPositionStepsY(y);
	if (ret != DEVICE_OK)
      return ERR_GETTING_XY_POSITION;
	
    return DEVICE_OK;
}

int XYDrive::SetPositionSteps(long x,long y)
{
	LogMessage("Setting both X and Y position", true);
	int ret;
	ret=SetPositionStepsX(x);
	if (ret != DEVICE_OK)
      return ERR_SETTING_XY_POSITION;
	ret=SetPositionStepsY(y);
	if (ret != DEVICE_OK)
      return ERR_SETTING_XY_POSITION;

	return DEVICE_OK;
}


int XYDrive::SetOrigin()
{
   // TODO: implement
   return DEVICE_OK;
}

int XYDrive::Home()
{
	//TODO: implement - I couldn't find any 'home' info, We could decide on some "home" position (0,0)? 
	// and move the stage there. Anyway, doesn't seem to be very important
	return DEVICE_OK;
}

int XYDrive::Stop() 
{
	//TODO: implement - I couldn't find any 'stop' command either. We could set speed to 0 but thats not the same thing 
	// Not sure about it, need to dig more to the SDK docs. 
	return DEVICE_OK;
}


int XYDrive::GetLimits(double& lowerx, double& upperx,double& lowery, double& uppery)
{

   lowerx = TIScope->xDrive_->GetLowerLimit() / factor_;
   upperx = TIScope->xDrive_->GetUpperLimit() / factor_;
   lowery = TIScope->yDrive_->GetLowerLimit() / factor_;
   uppery = TIScope->yDrive_->GetUpperLimit() / factor_;

   return DEVICE_OK;
}

int XYDrive::OnPositionX(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   double position;
   if (eAct == MM::BeforeGet)
   {
      int ret = GetPositionUmX(position);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(position);
   } else if (eAct ==MM::AfterSet)
   {
      pProp->Get(position);
      int ret = SetPositionUmX(position);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

int XYDrive::OnPositionY(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   double position;
   if (eAct == MM::BeforeGet)
   {
      int ret = GetPositionUmY(position);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(position);
   } else if (eAct ==MM::AfterSet)
   {
      pProp->Get(position);
      int ret = SetPositionUmY(position);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

/////////////////////////////////////
// Nikon TI PFSOffset

PFSOffset::PFSOffset() :
//TODO: get the step size (e.g. factor) from Nikon Scope object and not use magic number 40. 
   factor_ (40.0),
   name_ (g_TIPFSOffset)
{
   SetErrorText(ERR_MODULE_NOT_FOUND, "No Z Drive installed in this microscope");
   SetErrorText(ERR_SETTING_PFS_OFFSET, "Error while setting PFS offset");
   SetErrorText(ERR_GETTING_PFS_OFFSET, "Error while getting PFS offset");
}

PFSOffset::~PFSOffset()
{
}

int PFSOffset::Initialize()
{
   if (!TIScope->IsInitialized())
      TIScope->Initialize();

   // check if this device exists:
   if (!TIScope->pfsOffset_->GetIsMounted())
      return ERR_MODULE_NOT_FOUND;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Position property
   CPropertyAction* pAct = new CPropertyAction (this, &PFSOffset::OnPosition);
   ret = CreateProperty("Position", "0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   double min, max;
   ret = GetLimits(min, max);
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits("Position", min, max);

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}
 
bool PFSOffset::Busy()
{
	return TIScope->pfsOffset_->Busy();
}

int PFSOffset::Shutdown()
{
   return DEVICE_OK;
}

void PFSOffset::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_TIPFSOffset);
}

int PFSOffset::SetPositionUm(double position)
{
   LogMessage("Setting PFS offset", true);
   // Assume that the TI gives its position in nm...
   int ret = TIScope->pfsOffset_->SetPosition((long) (position * factor_) );
   if (ret != DEVICE_OK)
      return ERR_SETTING_PFS_OFFSET;

   return DEVICE_OK;
}

int PFSOffset::GetPositionUm(double& position)
{
   LogMessage("Getting PFS offset", true);
   long positionL;
   int ret = TIScope->pfsOffset_->GetPosition(positionL);
   if (ret != DEVICE_OK)
      return ERR_GETTING_PFS_OFFSET;
   position = positionL / factor_;

   return DEVICE_OK;
}
   
int PFSOffset::SetPositionSteps(long steps)
{
   // Consider steps to be native TI unit (nm?)
   if (!TIScope->pfsOffset_->SetPosition(steps))
      return ERR_SETTING_PFS_OFFSET;

   return DEVICE_OK;
}

int PFSOffset::GetPositionSteps(long& steps)
{
   if (!TIScope->pfsOffset_->GetPosition(steps))
      return ERR_GETTING_PFS_OFFSET;

   return DEVICE_OK;
}

int PFSOffset::SetOrigin()
{
   // Not sure if this method has any meaning in the context of 
   return DEVICE_OK;
}

int PFSOffset::GetLimits(double& lower, double& upper)
{

   lower = TIScope->pfsOffset_->GetLowerLimit() / factor_;
   upper = TIScope->pfsOffset_->GetUpperLimit() / factor_;

   return DEVICE_OK;
}


int PFSOffset::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   double position;
   if (eAct == MM::BeforeGet)
   {
      int ret = GetPositionUm(position);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(position);
   } else if (eAct ==MM::AfterSet)
   {
      pProp->Get(position);
      int ret = SetPositionUm(position);
      if (ret != DEVICE_OK)
         return ret;
   }

   return DEVICE_OK;
}

/////////////////////////////////////////////////////////////////////////////////////////////////////
//// Nikon PFS Device. This class is only for turning it on / off, for offset there is anoter class
PFSStatus::PFSStatus() 
{
   SetErrorText(ERR_MODULE_NOT_FOUND, "No Z Drive installed in this microscope");
   SetErrorText(ERR_ENABLING_PFS, "Error while enabling PFS");
   SetErrorText(ERR_DISABLING_PFS, "Error while disabling PFS");
}

PFSStatus::~PFSStatus()
{
}

int PFSStatus::Initialize()
{
	if (!TIScope->IsInitialized())
      TIScope->Initialize();

   // check if this device exists:
   if (!TIScope->pfsStatus_->GetIsMounted())
      return ERR_MODULE_NOT_FOUND;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;

}

int PFSStatus::Focus()
{
	// Not sure what's the meaning of Focus in a continous device. 
	return DEVICE_OK;
}

int PFSStatus::GetContinuousFocusing(bool &state)
{
	state = TIScope->pfsStatus_->IsEnabled();
	return DEVICE_OK;
}

int PFSStatus::SetContinuousFocusing(bool state)
{ 
	int ret;
	// if state is true, and current status is ENABLE, than disable
	if (state && TIScope->pfsStatus_->IsEnabled())
		ret = TIScope->pfsStatus_->Disable();
	if (!ret) return ERR_DISABLING_PFS; 
	else return DEVICE_OK;

	// if state is false, and current status is DISABLE, that enable
	if (!state && !TIScope->pfsStatus_->IsEnabled())
		ret = TIScope->pfsStatus_->Enable();
	if (!ret) return ERR_ENABLING_PFS;
	else return DEVICE_OK;

	// If we are here there is nothing to do (e.g. state == IsEnable())
	return DEVICE_OK;
}

bool PFSStatus::Busy()
{
	return TIScope->pfsStatus_->Busy();
}

int PFSStatus::Shutdown()
{
	return DEVICE_OK;
}

void PFSStatus::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_TIPFSStatus);
}

bool PFSStatus::IsContinuousFocusLocked()
{
// TODO: figure out how to figure out the JustPint status...
return true; 
}

/////////////////////////////////////////////////////////////////////////////////////////////////////
// EpiShutter methods

EpiShutter::EpiShutter() :
name_ (g_TIEpiShutter)
{
	SetErrorText(ERR_MODULE_NOT_FOUND, "No EpiShutter installed in this microscope");
}

EpiShutter::~EpiShutter()
{
	Shutdown();
}


int EpiShutter::Initialize()
{
   if (!TIScope->IsInitialized())
      TIScope->Initialize();

   // check if this device exists:
   if (!TIScope->pEpiShutter_->GetIsMounted())
      return ERR_MODULE_NOT_FOUND;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Nikon Ti Epi-Illumination Shutter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &EpiShutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;
   return DEVICE_OK;
}

bool EpiShutter::Busy()
{
	return TIScope->pEpiShutter_->Busy();
}

int EpiShutter::Shutdown()
{
   return DEVICE_OK;
}

void EpiShutter::GetName(char* Name) const
{
 CDeviceUtils::CopyLimitedString(Name, g_TIEpiShutter);
}

// Shutter API
int EpiShutter::SetOpen(bool open)
{
	if (open)
		{return TIScope->pEpiShutter_->Open();}
	else
		{return TIScope->pEpiShutter_->Close();}
}


int EpiShutter::GetOpen(bool& open)
{
	open = TIScope->pEpiShutter_->IsOpen();
	return DEVICE_OK;

}

int EpiShutter::Fire(double deltaT)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

// action interface
int EpiShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (TIScope->pEpiShutter_->IsOpen())
         pProp->Set(1l);
      else
         pProp->Set(0l);
   } else if (eAct ==MM::AfterSet)
   {
      long position;
      pProp->Get(position);
      int ret = DEVICE_OK;;
      if (position == 1)
         ret = TIScope->pEpiShutter_->Open();
      else
         ret = TIScope->pEpiShutter_->Close();

      return ret;
   }

   return DEVICE_OK;
}


/////////////////////////////////////////////////////////////////////////////////////////////////////
// DiaShutter methods

DiaShutter::DiaShutter() :
name_ (g_TIDiaShutter)
{
	SetErrorText(ERR_MODULE_NOT_FOUND, "No DiaShutter installed in this microscope");
}

DiaShutter::~DiaShutter()
{
	Shutdown();
}


int DiaShutter::Initialize()
{
   if (!TIScope->IsInitialized())
      TIScope->Initialize();

   // check if this device exists:
   if (!TIScope->pDiaShutter_->GetIsMounted())
      return ERR_MODULE_NOT_FOUND;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Nikon Ti Dia Shutter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &DiaShutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;
   return DEVICE_OK;
}

bool DiaShutter::Busy()
{
	return TIScope->pDiaShutter_->Busy();
}

int DiaShutter::Shutdown()
{
   return DEVICE_OK;
}

void DiaShutter::GetName(char* Name) const
{
 CDeviceUtils::CopyLimitedString(Name, g_TIDiaShutter);
}

// Shutter API
int DiaShutter::SetOpen(bool open)
{
	if (open)
		{return TIScope->pDiaShutter_->Open();}
	else
		{return TIScope->pDiaShutter_->Close();}
}


int DiaShutter::GetOpen(bool& open)
{
	open = TIScope->pDiaShutter_->IsOpen();
	return DEVICE_OK;

}

int DiaShutter::Fire(double deltaT)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

// action interface
int DiaShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (TIScope->pDiaShutter_->IsOpen())
         pProp->Set(1l);
      else
         pProp->Set(0l);
   } else if (eAct ==MM::AfterSet)
   {
      long position;
      pProp->Get(position);
      int ret = DEVICE_OK;;
      if (position == 1)
         ret = TIScope->pDiaShutter_->Open();
      else
         ret = TIScope->pDiaShutter_->Close();

      return ret;
   }

   return DEVICE_OK;
}

/////////////////////////////////////////////////////////////////////////////////////////////////////
// AuxShutter methods

AuxShutter::AuxShutter() :
name_ (g_TIAuxShutter)
{
	SetErrorText(ERR_MODULE_NOT_FOUND, "No AuxShutter installed in this microscope");
}

AuxShutter::~AuxShutter()
{
	Shutdown();
}


int AuxShutter::Initialize()
{
   if (!TIScope->IsInitialized())
      TIScope->Initialize();

   // check if this device exists:
   if (!TIScope->pAuxShutter_->GetIsMounted())
      return ERR_MODULE_NOT_FOUND;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "Nikon Ti Aux Shutter", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // State
   CPropertyAction* pAct = new CPropertyAction (this, &AuxShutter::OnState);
   ret = CreateProperty(MM::g_Keyword_State, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue(MM::g_Keyword_State, "0");
   AddAllowedValue(MM::g_Keyword_State, "1");

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;
   return DEVICE_OK;
}

bool AuxShutter::Busy()
{
	return TIScope->pAuxShutter_->Busy();
}

int AuxShutter::Shutdown()
{
   return DEVICE_OK;
}

void AuxShutter::GetName(char* Name) const
{
 CDeviceUtils::CopyLimitedString(Name, g_TIAuxShutter);
}

// Shutter API
int AuxShutter::SetOpen(bool open)
{
	if (open)
		{return TIScope->pAuxShutter_->Open();}
	else
		{return TIScope->pAuxShutter_->Close();}
}


int AuxShutter::GetOpen(bool& open)
{
	open = TIScope->pAuxShutter_->IsOpen();
	return DEVICE_OK;

}

int AuxShutter::Fire(double deltaT)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

// action interface
int AuxShutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      if (TIScope->pAuxShutter_->IsOpen())
         pProp->Set(1l);
      else
         pProp->Set(0l);
   } else if (eAct ==MM::AfterSet)
   {
      long position;
      pProp->Get(position);
      int ret = DEVICE_OK;;
      if (position == 1)
         ret = TIScope->pAuxShutter_->Open();
      else
         ret = TIScope->pAuxShutter_->Close();

      return ret;
   }

   return DEVICE_OK;
}
