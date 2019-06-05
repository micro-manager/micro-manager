#include "ZeissAxioZoom.h"
#include <cstdio>
#include <string>
#include <math.h>
#include <sstream>

extern ZeissHub g_hub;
using namespace std;

MotorFocus::MotorFocus (std::string name, std::string description) : 
   stepSize_um_(1.0),
   initialized_ (false),
   moveMode_ (0),
   velocity_ (0),
   direct_ ("Direct move to target"),
   uni_ ("Unidirectional backlash compensation"),
   biSup_ ("Bidirectional Precision suppress small upwards"),
   biAlways_ ("Bidirectional Precision Always"),
   fast_ ("Fast"),
   smooth_ ("Smooth"),
   busyCounter_(0)
{
   devId_ = MOTOR_FOCUS;
   name_ = name;
   description_ = description;
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for this Zeiss Axis to work");
   SetErrorText(ERR_MODULE_NOT_FOUND, "This Axis is not installed in this Zeiss microscope");
}

MotorFocus::~MotorFocus()
{
   Shutdown();
}

bool MotorFocus::Busy()
{
   bool busy = false;
   g_hub.motorFocusModel_.GetBusy(busy);
   return busy;
}

void MotorFocus::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZeissMotorFocus);
}


int MotorFocus::Initialize()
{
   if (!g_hub.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this Axis exists:
   bool init = false;
   g_hub.motorFocusModel_.GetInitialized(init);
   if (!init)
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------
   // Position
   CPropertyAction* pAct = new CPropertyAction(this, &MotorFocus::OnPosition);
   int ret = CreateProperty(MM::g_Keyword_Position, "0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   
   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   long steps;
   ret = GetPositionStepsDirect(steps);
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int MotorFocus::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

int MotorFocus::SetPositionUm(double pos)
{
   long steps = (long)(pos / stepSize_um_);
   int ret = SetPositionSteps(steps);
   if (ret != DEVICE_OK)
      return ret;

   return DEVICE_OK;
}

int MotorFocus::GetPositionUm(double& pos)
{
   long steps;                                                
   int ret = GetPositionSteps(steps);                         
   if (ret != DEVICE_OK)                                      
      return ret;                                             
   pos = steps * stepSize_um_;

   return DEVICE_OK;
}

int MotorFocus::SetPositionSteps(long steps)
{
   const int cmdLength = 9;
   unsigned char command[cmdLength];
 
   command[0] = 0x06; // size of data block = 6 bytes
   command[1] = 0x1B; // command class
   command[2] = 0xB0; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x09; // data byte 2 = subID
   command[5] = 0x00; // data byte 3
   command[6] = 0x00; // data byte 4
   command[7] = 0x00; // data byte 5
   command[8] = 0x00; // data byte 6

   ZeissLong tmp = htonl((ZeissLong) steps);
   memcpy(command+5, &tmp, ZeissLongSize); 

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, MOTOR_FOCUS);
   if (ret != DEVICE_OK)
      return ret;

   g_hub.motorFocusModel_.MakeBusy();

   ostringstream os;
   os << "Focus Motor position set at: " << steps;
   LogMessage(os.str().c_str(), false);
   return DEVICE_OK;
}

int MotorFocus::GetPositionStepsDirect(long& steps)
{
   const int cmdLength = 5;
   unsigned char command[cmdLength];
 
   command[0] = 0x02; // size of data block = 2 bytes
   command[1] = 0x18; // command class
   command[2] = 0xB0; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x06; // data byte 2 = subID

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, MOTOR_FOCUS);
   if (ret != DEVICE_OK)
      return ret;

   //long unsigned int responseLength = ZeissHub::RCV_BUF_LENGTH;
   //unsigned char response[ZeissHub::RCV_BUF_LENGTH];
   //unsigned long signatureLength = 4;
   //unsigned char signature[] = {0x08, command[2], command[3], command[4]};
   //memset(response, 0, ZeissHub::RCV_BUF_LENGTH);
   //ret = g_hub.GetAnswer(*this, *GetCoreCallback(), response, responseLength, signature, 1, signatureLength);
   //if (ret != DEVICE_OK)
   //   return ret;

   //return DEVICE_OK;

   g_hub.motorFocusModel_.setWaiting(true);
   int tryCount = 0;
   const int maxCount = 100;
   while (g_hub.motorFocusModel_.isVaiting() && tryCount < maxCount)
   {
      CDeviceUtils::SleepMs(5);
      tryCount++;
   }
   if (maxCount == tryCount)
      return ERR_ANSWER_TIMEOUT;

   return GetPositionSteps(steps);
}

int MotorFocus::GetPositionSteps(long& steps)
{
   ZeissLong pos(0);
   g_hub.motorFocusModel_.GetPosition(pos);
   steps = pos;
   return DEVICE_OK;
}

int MotorFocus::SetOrigin()
{
   // TODO
   return DEVICE_OK;
}

int MotorFocus::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      double pos;
      int ret = GetPositionUm(pos);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(pos);
   }
   else if (eAct == MM::AfterSet)                             
   {  
      double pos;                                             
      pProp->Get(pos);                                        
      int ret = SetPositionUm(pos);                           
      if (ret != DEVICE_OK)                                   
         return ret;                                          
   }                                                          
                                                              
   return DEVICE_OK;                                          
}

int MotorFocus::OnMoveMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      switch (moveMode_) {
         case 0: pProp->Set(direct_.c_str()); break;
         case 1: pProp->Set(uni_.c_str()); break;
         case 2: pProp->Set(biSup_.c_str()); break;
         case 3: pProp->Set(biAlways_.c_str()); break;
         default: pProp->Set(direct_.c_str());
      }
   }
   else if (eAct == MM::AfterSet)                             
   {  
      string result;                                             
      pProp->Get(result);                                        
      if (result == direct_)
         moveMode_ = 0;
      else if (result == uni_)
         moveMode_ = 1;
      else if (result == biSup_)
         moveMode_ = 2;
      else if (result == biAlways_)
         moveMode_ = 3;
   }                                                          
                                                              
   return DEVICE_OK;                                          
}

int MotorFocus::OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      switch (velocity_) {
         case 0: pProp->Set(fast_.c_str()); break;
         case 4: pProp->Set(smooth_.c_str()); break;
         default: pProp->Set(fast_.c_str());
      }
   }
   else if (eAct == MM::AfterSet)                             
   {  
      string result;                                             
      pProp->Get(result);                                        
      if (result == fast_)
         velocity_ = 0;
      else if (result == smooth_)
         velocity_ = 4;
   }                                                          
                                                              
   return DEVICE_OK;                                          
}

/**
 *
 * ZeissXYStageX: Micro-Manager implementation of X and Y Stage
 */
XYStageX::XYStageX (): 
   CXYStageBase<XYStageX>(),
   stepSize_um_(0.001),
   initialized_ (false),
   moveMode_ (0),
   velocity_ (0),
   direct_ ("Direct move to target"),
   uni_ ("Unidirectional backlash compensation"),
   biSup_ ("Bidirectional Precision suppress small upwards"),
   biAlways_ ("Bidirectional Precision Always"),
   fast_ ("Fast"),
   smooth_ ("Smooth")
{
   name_ = g_ZeissXYStage;
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_SCOPE_NOT_ACTIVE, "Zeiss Scope is not initialized.  It is needed for the Zeiss XYStage to work");
   SetErrorText(ERR_MODULE_NOT_FOUND, "No XYStage installed on this Zeiss microscope");
}

XYStageX::~XYStageX()
{
   Shutdown();
}

bool XYStageX::Busy()
{
   bool xBusy = false;
   bool yBusy = false;

   g_hub.stageModelX_.GetBusy(xBusy);
   g_hub.stageModelY_.GetBusy(yBusy);

   // TODO
   return xBusy || yBusy;
}

void XYStageX::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZeissXYStage);
}


int XYStageX::Initialize()
{
   if (!g_hub.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   // check if this Axis exists:
   // bool presentX=false, presentY=false;
   // TODO: check both stages
   //if (!(presentX && presentY))
   //   return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------
   // MoveMode
   CPropertyAction* pAct = new CPropertyAction(this, &XYStageX::OnMoveMode);
   int ret = CreateProperty("Move Mode", direct_.c_str(), MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("Move Mode", direct_.c_str()); 
   AddAllowedValue("Move Mode", uni_.c_str()); 
   AddAllowedValue("Move Mode", biSup_.c_str()); 
   AddAllowedValue("Move Mode", biAlways_.c_str()); 

   // velocity
   pAct = new CPropertyAction(this, &XYStageX::OnVelocity);
   ret = CreateProperty("Velocity-Acceleration", fast_.c_str(), MM::String, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   AddAllowedValue("Velocity-Acceleration", fast_.c_str());
   AddAllowedValue("Velocity-Acceleration", smooth_.c_str());
   
   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   long xSteps, ySteps;
   ret = GetPositionStepsDirect(xSteps, ySteps);
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int XYStageX::Shutdown()
{
   if (initialized_) initialized_ = false;
   return DEVICE_OK;
}

int XYStageX::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax) 
{
   long xMi, xMa, yMi, yMa;
   GetStepLimits(xMi, xMa, yMi, yMa);
   xMin = xMi * stepSize_um_;
   yMin = yMi * stepSize_um_;
   xMax = xMa * stepSize_um_;
   yMax = yMa * stepSize_um_;

   return DEVICE_OK;
}

int XYStageX::GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/) 
{
   // TODO
   return DEVICE_OK;
}


int XYStageX::SetPositionSteps(long xSteps, long ySteps)
{
   const int cmdLength = 11;
   unsigned char command[cmdLength];
 
   command[0] = 0x08; // size of data block = 6 bytes
   command[1] = 0x1B; // command class
   command[2] = 0x5F; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x02; // data byte 2 = subID
   command[5] = 0x00; // dev ID - data byte 3
   command[6] = 0x00; // data byte 4
   command[7] = 0x00; // data byte 5
   command[8] = 0x00; // data byte 6
   command[9] = 0x00; // data byte 7
   command[10] = 0x00; // data byte 8

   // move x stage
   ZeissLong tmp = htonl((ZeissLong) xSteps);
   memcpy(command+7, &tmp, ZeissLongSize); 

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, STAGEX);
   if (ret != DEVICE_OK)
      return ret;

   g_hub.stageModelX_.MakeBusy();

   ostringstream os;
   os << "X stage position set at: " << xSteps;
   LogMessage(os.str().c_str(), false);

   // move y stage
   ZeissLong tmpy = htonl((ZeissLong) ySteps);
   memcpy(command+7, &tmpy, ZeissLongSize); 

   ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, STAGEY);
   if (ret != DEVICE_OK)
      return ret;

   g_hub.stageModelY_.MakeBusy();

   ostringstream osy;
   osy << "Y stage position set at: " << xSteps;
   LogMessage(osy.str().c_str(), false);

   return DEVICE_OK;
}

int XYStageX::GetPositionSteps(long& xSteps, long& ySteps)
{
   ZeissLong posX(0), posY(0);
   g_hub.stageModelX_.GetPosition(posX);
   g_hub.stageModelY_.GetPosition(posY);
   xSteps = posX;
   ySteps = posY;
   return DEVICE_OK;
}

int XYStageX::GetPositionStepsDirect(long& xSteps, long& ySteps)
{
   const int cmdLength = 6;
   unsigned char command[cmdLength];
 
   command[0] = 0x03; // size of data block = 2 bytes
   command[1] = 0x18; // command class
   command[2] = 0x5F; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x02; // data byte 2 = subID
   command[5] = 0x00; // devID

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, STAGEX);
   if (ret != DEVICE_OK)
      return ret;
   g_hub.stageModelX_.setWaiting(true);

   ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, STAGEY);
   if (ret != DEVICE_OK)
      return ret;
   g_hub.stageModelY_.setWaiting(true);

   int tryCount = 0;
   const int maxCount = 100;
   while (g_hub.stageModelX_.isVaiting() && tryCount < maxCount)
   {
      CDeviceUtils::SleepMs(5);
      tryCount++;
   }
   if (maxCount == tryCount)
      return ERR_ANSWER_TIMEOUT;

   tryCount = 0;
   while (g_hub.stageModelY_.isVaiting() && tryCount < maxCount)
   {
      CDeviceUtils::SleepMs(5);
      tryCount++;
   }
   if (maxCount == tryCount)
      return ERR_ANSWER_TIMEOUT;


   return GetPositionSteps(xSteps, ySteps);
}
/*
int XYStageX::GetStatusDirect(ZeissULong& status)
{
   const int cmdLength = 6;
   unsigned char command[cmdLength];
 
   command[0] = 0x03; // size of data block = 2 bytes
   command[1] = 0x18; // command class
   command[2] = 0x5F; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x02; // data byte 2 = subID
   command[5] = 0x00; // devID

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, STAGEX);
   if (ret != DEVICE_OK)
      return ret;
   g_hub.stageModelX_.setWaiting(true);

   ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, STAGEY);
   if (ret != DEVICE_OK)
      return ret;
   g_hub.stageModelY_.setWaiting(true);

   int tryCount = 0;
   const int maxCount = 100;
   while (g_hub.stageModelX_.isVaiting() && tryCount < maxCount)
   {
      CDeviceUtils::SleepMs(5);
      tryCount++;
   }
   if (maxCount == tryCount)
      return ERR_ANSWER_TIMEOUT;

   tryCount = 0;
   while (g_hub.stageModelY_.isVaiting() && tryCount < maxCount)
   {
      CDeviceUtils::SleepMs(5);
      tryCount++;
   }
   if (maxCount == tryCount)
      return ERR_ANSWER_TIMEOUT;


   return GetPositionSteps(xSteps, ySteps);
}
*/
int XYStageX::Home()
{
   //int ret = FindHardwareStop(*this, *GetCoreCallback(), g_StageXAxis, LOWER);
   //if (ret != DEVICE_OK)
   //   return ret;
   //ret = FindHardwareStop(*this, *GetCoreCallback(), g_StageYAxis, LOWER);
   //if (ret != DEVICE_OK)
   //   return ret;

   return DEVICE_OK;
}

int XYStageX::Stop()
{
   // TODO

   return DEVICE_OK;
}

int XYStageX::SetOrigin()
{
   return SetAdapterOriginUm(0.0, 0.0);
}

int XYStageX::OnMoveMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      switch (moveMode_) {
         case 0: pProp->Set(direct_.c_str()); break;
         case 1: pProp->Set(uni_.c_str()); break;
         case 2: pProp->Set(biSup_.c_str()); break;
         case 3: pProp->Set(biAlways_.c_str()); break;
         default: pProp->Set(direct_.c_str());
      }
   }
   else if (eAct == MM::AfterSet)                             
   {  
      string result;                                             
      pProp->Get(result);                                        
      if (result == direct_)
         moveMode_ = 0;
      else if (result == uni_)
         moveMode_ = 1;
      else if (result == biSup_)
         moveMode_ = 2;
      else if (result == biAlways_)
         moveMode_ = 3;
   }                                                          
                                                              
   return DEVICE_OK;                                          
}

int XYStageX::OnVelocity(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      switch (velocity_) {
         case 0: pProp->Set(fast_.c_str()); break;
         case 4: pProp->Set(smooth_.c_str()); break;
         default: pProp->Set(fast_.c_str());
      }
   }
   else if (eAct == MM::AfterSet)                             
   {  
      string result;                                             
      pProp->Get(result);                                        
      if (result == fast_)
         velocity_ = 0;
      else if (result == smooth_)
         velocity_ = 4;
   }                                                          
                                                              
   return DEVICE_OK;                                          
}

OpticsUnit::OpticsUnit (std::string nm, std::string desc) : 
   initialized(false)
{
   devId = OPTICS;
   name = name;
   description = desc;
   InitializeDefaultErrorMessages();
}

OpticsUnit::~OpticsUnit()
{
   Shutdown();
}

bool OpticsUnit::Busy()
{
   bool busy = false;
   g_hub.opticsUnitModel_.GetBusy(busy);
   return busy;
}

void OpticsUnit::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZeissOpticsUnit);
}


int OpticsUnit::Initialize()
{
   if (!g_hub.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   bool init = false;
   g_hub.opticsUnitModel_.GetInitialized(init);
   if (!init)
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------
   // ZoomLevel
   CPropertyAction* pAct = new CPropertyAction(this, &OpticsUnit::OnZoomLevel);
   int ret = CreateProperty("ZoomLevel", "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   // Aperture
   pAct = new CPropertyAction(this, &OpticsUnit::OnAperture);
   ret = CreateProperty("Aperture", "0", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   
   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   long steps;
   ret = GetZoomLevelDirect(steps);
   if (ret!= DEVICE_OK)
      return ret;

   long apt;
   ret = GetApertureDirect(apt);
   if (ret!= DEVICE_OK)
      return ret;


   initialized = true;

   return DEVICE_OK;
}

int OpticsUnit::Shutdown()
{
   if (initialized) initialized = false;
   return DEVICE_OK;
}

int OpticsUnit::GetZoomLevelDirect(long& level)
{
   const int cmdLength = 5;
   unsigned char command[cmdLength];
 
   command[0] = 0x02; // size of data block = 2 bytes
   command[1] = 0x18; // command class
   command[2] = 0xA0; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x06; // data byte 2 = subID

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, OPTICS);
   if (ret != DEVICE_OK)
      return ret;

   g_hub.opticsUnitModel_.setWaiting(true);
   int tryCount = 0;
   const int maxCount = 100;
   while (g_hub.opticsUnitModel_.isVaiting() && tryCount < maxCount)
   {
      CDeviceUtils::SleepMs(5);
      tryCount++;
   }
   if (maxCount == tryCount)
      return ERR_ANSWER_TIMEOUT;

   return GetZoomLevel(level);
}

int OpticsUnit::GetZoomLevel(long& zoom)
{
   ZeissUShort pos(0);
   g_hub.opticsUnitModel_.GetZoomLevel(pos);
   zoom = pos;
   return DEVICE_OK;
}

int OpticsUnit::SetZoomLevel(long zoom)
{
   const int cmdLength = 7;
   unsigned char command[cmdLength];
 
   command[0] = 0x04; // size of data block = 4 bytes
   command[1] = 0x1B; // command class
   command[2] = 0xA0; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x08; // data byte 2 = subID
   command[5] = 0x00; // data byte 3
   command[6] = 0x00; // data byte 4

   ZeissUShort tmp = htons((ZeissUShort) zoom);
   memcpy(command+5, &tmp, ZeissShortSize); 

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, OPTICS);
   if (ret != DEVICE_OK)
      return ret;

   g_hub.opticsUnitModel_.MakeBusy();

   ostringstream os;
   os << "Optics Unit zoom set at: " << zoom;
   LogMessage(os.str().c_str(), false);
   return DEVICE_OK;

}

int OpticsUnit::GetAperture(long& a)
{
   ZeissByte pos(0);
   g_hub.opticsUnitModel_.GetAperture(pos);
   a = pos;
   return DEVICE_OK;
}

int OpticsUnit::SetAperture(long a)
{
   const int cmdLength = 6;
   unsigned char command[cmdLength];
 
   command[0] = 0x03; // size of data block = 4 bytes
   command[1] = 0x1B; // command class
   command[2] = 0xA0; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x20; // data byte 2 = subID
   command[5] = 0x00; // data byte 3

   command[5] = (ZeissByte)a; 

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, OPTICS);
   if (ret != DEVICE_OK)
      return ret;

   g_hub.opticsUnitModel_.MakeBusy();

   ostringstream os;
   os << "Optics Unit aperture set at: " << a;
   LogMessage(os.str().c_str(), false);
   return DEVICE_OK;

}

int OpticsUnit::GetApertureDirect(long& a)
{
   const int cmdLength = 5;
   unsigned char command[cmdLength];
 
   command[0] = 0x02; // size of data block = 2 bytes
   command[1] = 0x18; // command class
   command[2] = 0xA0; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x21; // data byte 2 = subID

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, OPTICS);
   if (ret != DEVICE_OK)
      return ret;

   g_hub.opticsUnitModel_.setWaiting(true);
   int tryCount = 0;
   const int maxCount = 100;
   while (g_hub.opticsUnitModel_.isVaiting() && tryCount < maxCount)
   {
      CDeviceUtils::SleepMs(5);
      tryCount++;
   }
   if (maxCount == tryCount)
      return ERR_ANSWER_TIMEOUT;

   return GetAperture(a);
}


int OpticsUnit::OnZoomLevel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      long pos;
      int ret = GetZoomLevel(pos);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(pos);
   }
   else if (eAct == MM::AfterSet)                             
   {  
      long pos;                                             
      pProp->Get(pos);                                        
      int ret = SetZoomLevel(pos);                           
      if (ret != DEVICE_OK)                                   
         return ret;                                          
   }                                                          
                                                              
   return DEVICE_OK;                                          
}

int OpticsUnit::OnAperture(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      long pos;
      int ret = GetAperture(pos);
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set(pos);
   }
   else if (eAct == MM::AfterSet)                             
   {  
      long pos;                                             
      pProp->Get(pos);                                        
      int ret = SetAperture(pos);                           
      if (ret != DEVICE_OK)                                   
         return ret;                                          
   }                                                          
                                                              
   return DEVICE_OK;                                          
}

/////////////////////////////////////////////////////////////////////////////////////
// FLUO TUBE

FluoTube::FluoTube () : 
   initialized_(false)
{
   InitializeDefaultErrorMessages();
}

FluoTube::~FluoTube()
{
   Shutdown();
}

bool FluoTube::Busy()
{
   bool busy = false;
   unsigned short stat;
   GetStatusDirect(stat);
   g_hub.fluoTubeModel_.SetState(stat);
   g_hub.fluoTubeModel_.GetBusy(busy);

   if (!busy)
   {
      // try shutter
      unsigned short sstat;
      GetStatusDirect(sstat);
      g_hub.fluoTubeModel_.SetState(sstat);
      g_hub.fluoTubeModel_.GetShutterBusy(busy);
   }
   return busy;
}

void FluoTube::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZeissFluoTube);
}


int FluoTube::Initialize()
{
   if (!g_hub.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   bool init = false;
   g_hub.fluoTubeModel_.GetInitialized(init);
   if (!init)
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &FluoTube::OnState);
   int ret = CreateIntegerProperty(MM::g_Keyword_State, 0, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

	AddAllowedValue(MM::g_Keyword_State, "0"); // Closed
   AddAllowedValue(MM::g_Keyword_State, "1"); // Open

   // Position
	// --------
   pAct = new CPropertyAction(this, &FluoTube::OnPosition);
   ret = CreateProperty(g_Property_Position, "1", MM::Integer, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   AddAllowedValue(g_Property_Position, "1");
   AddAllowedValue(g_Property_Position, "2");
   AddAllowedValue(g_Property_Position, "3");
   AddAllowedValue(g_Property_Position, "4");

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   ret = GetPositionDirect(pos_);
   if (ret!= DEVICE_OK)
      return ret;

   ret = GetShutterPosDirect(shutterPos_);
   if (ret!= DEVICE_OK)
      return ret;


   initialized_ = true;

   return DEVICE_OK;
}

int FluoTube::Shutdown()
{
   if (initialized_)
      initialized_ = false;
   return DEVICE_OK;
}

int FluoTube::SetOpen(bool open)
{
   return SetProperty(MM::g_Keyword_State, (open ? "1" : "0"));
} 

int FluoTube::GetOpen(bool& open)
{
	long state = 0;
	int ret = GetProperty(MM::g_Keyword_State, state);
	open = state == 1 ? true : false;
	return ret;
}

int FluoTube::GetPositionDirect(unsigned short& pos)
{
   const int cmdLength = 6;
   unsigned char command[cmdLength];
 
   command[0] = 0x03; // size of data block = 3 bytes
   command[1] = 0x18; // command class
   command[2] = 0x50; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x02; // data byte 2 = subID
   command[5] = 0x02; // device

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, FLUO_TUBE);
   if (ret != DEVICE_OK)
      return ret;

   g_hub.fluoTubeModel_.setWaiting(true);
   int tryCount = 0;
   const int maxCount = 100;
   while (g_hub.fluoTubeModel_.isVaiting() && tryCount < maxCount)
   {
      CDeviceUtils::SleepMs(5);
      tryCount++;
   }
   if (maxCount == tryCount)
      return ERR_ANSWER_TIMEOUT;

   return GetPosition(pos);
}

int FluoTube::GetStatusDirect(unsigned short& pos)
{
   const int cmdLength = 6;
   unsigned char command[cmdLength];
 
   command[0] = 0x03; // size of data block = 3 bytes
   command[1] = 0x18; // command class
   command[2] = 0x50; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x01; // data byte 2 = subID
   command[5] = 0x02; // device

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, FLUO_TUBE);
   if (ret != DEVICE_OK)
      return ret;

   g_hub.fluoTubeModel_.setWaiting(true);
   int tryCount = 0;
   const int maxCount = 100;
   while (g_hub.fluoTubeModel_.isVaiting() && tryCount < maxCount)
   {
      CDeviceUtils::SleepMs(5);
      tryCount++;
   }
   if (maxCount == tryCount)
      return ERR_ANSWER_TIMEOUT;

   return GetPosition(pos);
}

int FluoTube::GetPosition(unsigned short& position)
{
   ZeissUShort pos(0);
   g_hub.fluoTubeModel_.GetPosition(pos);
   position = pos;
   return DEVICE_OK;
}

int FluoTube::SetPosition(unsigned short pos)
{
   const int cmdLength = 8;
   unsigned char command[cmdLength];
 
   command[0] = 0x05; // size of data block = 5 bytes
   command[1] = 0x1B; // command class
   command[2] = 0x50; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x02; // data byte 2 = subID
   command[5] = 0x02; // dev
   command[6] = 0x00; // data byte 4
   command[7] = 0x00; // data byte 5

   ZeissUShort tmp = htons((ZeissUShort) pos);
   memcpy(command+6, &tmp, ZeissShortSize); 

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, FLUO_TUBE);
   if (ret != DEVICE_OK)
      return ret;

   g_hub.fluoTubeModel_.MakeBusy();
   //CDeviceUtils::SleepMs(100);

   ostringstream os;
   os << "Fluo tube position set at: " << pos;
   LogMessage(os.str().c_str(), false);
   return DEVICE_OK;

}

int FluoTube::SetShutterPos(unsigned short pos)
{
   const int cmdLength = 8;
   unsigned char command[cmdLength];

   command[0] = 0x05; // size of data block = 5 bytes
   command[1] = 0x1B; // command class
   command[2] = 0x50; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x02; // data byte 2 = subID
   command[5] = 0x01; // dev: shutter
   command[6] = 0x00; // data byte 4
   command[7] = 0x00; // data byte 5

   ZeissUShort tmp = htons((ZeissUShort) pos);
   memcpy(command+6, &tmp, ZeissShortSize); 

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, FLUO_TUBE);
   if (ret != DEVICE_OK)
      return ret;

   //g_hub.fluoTubeModel_.MakeBusy();
   CDeviceUtils::SleepMs(100);

   ostringstream os;
   os << "Fluo tube shutter set at: " << pos;
   LogMessage(os.str().c_str(), false);
   return DEVICE_OK;
}

int FluoTube::GetShutterPosDirect(unsigned short& pos)
{
   const int cmdLength = 6;
   unsigned char command[cmdLength];

   command[0] = 0x03; // size of data block = 3 bytes
   command[1] = 0x18; // command class
   command[2] = 0x50; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x02; // data byte 2 = subID
   command[5] = 0x01; // device : shutter

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, FLUO_TUBE);
   if (ret != DEVICE_OK)
      return ret;

   g_hub.fluoTubeModel_.setWaiting(true);
   int tryCount = 0;
   const int maxCount = 100;
   while (g_hub.fluoTubeModel_.isVaiting() && tryCount < maxCount)
   {
      CDeviceUtils::SleepMs(5);
      tryCount++;
   }
   if (maxCount == tryCount)
      return ERR_ANSWER_TIMEOUT;

   return GetShutterPos(pos);
}

int FluoTube::GetShutterPos(unsigned short& p)
{
   ZeissUShort pos(0);
   g_hub.fluoTubeModel_.GetShutterPosition(pos);
   shutterPos_ = pos;
   p = pos;
   return DEVICE_OK;
}


int FluoTube::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      unsigned short pos;
      int ret = GetShutterPosDirect(pos);
      if (pos == 0)
      {
         // this means query failed so try again
         CDeviceUtils::SleepMs(400);
         ret = GetShutterPosDirect(pos);
      }
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long)pos);
   }
   else if (eAct == MM::AfterSet)                             
   {  
      long pos;                                             
      pProp->Get(pos);                                        
      int ret = SetShutterPos((unsigned short)pos);                           
      if (ret != DEVICE_OK)                                   
         return ret;                                          
   }                                                          
                                                              
   return DEVICE_OK;                                          
}

int FluoTube::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      unsigned short pos;
      int ret = GetPositionDirect(pos);
      if (pos == 0)
      {
         // this means query failed so try again
         CDeviceUtils::SleepMs(400);
         ret = GetPositionDirect(pos);
      }
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long)pos);
   }
   else if (eAct == MM::AfterSet)                             
   {  
      long pos;                                             
      pProp->Get(pos);                                        
      int ret = SetPosition((unsigned short)pos);                           
      if (ret != DEVICE_OK)                                   
         return ret;                                          
   }                                                          
                                                              
   return DEVICE_OK;                                          
}


///////////////////////////////////////////////////////////////////////////////
// DL450
//
LampDL450::LampDL450 () : 
   initialized_(false)
{
   InitializeDefaultErrorMessages();
}

LampDL450::~LampDL450()
{
   Shutdown();
}

bool LampDL450::Busy()
{
   bool busy = false;
   ZeissUShort stat;
   GetStatusDirect(stat);
   g_hub.dl450Model_.SetState(stat);
   g_hub.dl450Model_.GetBusy(busy);

   return busy;
}

void LampDL450::GetName (char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_ZeissDL450);
}


int LampDL450::Initialize()
{
   if (!g_hub.portInitialized_)
      return ERR_SCOPE_NOT_ACTIVE;

   bool init = false;
   ZeissUShort state(0);
   int ret = GetStatusDirect(state);
   if (ret != DEVICE_OK)
      return ret;
   g_hub.dl450Model_.GetInitialized(init);
   if (!init)
      return ERR_MODULE_NOT_FOUND;

   // set property list
   // ----------------

   // State
   // -----
   CPropertyAction* pAct = new CPropertyAction (this, &LampDL450::OnState);
   ret = CreateIntegerProperty(MM::g_Keyword_State, 1, false, pAct);
   if (ret != DEVICE_OK)
      return ret;
   SetPropertyLimits(MM::g_Keyword_State, 1, 1024);

   ret = UpdateStatus();
   if (ret!= DEVICE_OK)
      return ret;

   ret = GetPositionDirect(pos_);
   if (ret!= DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int LampDL450::Shutdown()
{
   if (initialized_)
      initialized_ = false;
   return DEVICE_OK;
}

int LampDL450::GetPositionDirect(unsigned short& pos)
{
   const int cmdLength = 6;
   unsigned char command[cmdLength];
 
   command[0] = 0x03; // size of data block = 3 bytes
   command[1] = 0x18; // command class
   command[2] = 0x51; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x02; // data byte 2 = subID
   command[5] = 0x07; // data byte 3 = deviceID

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, DL450);
   if (ret != DEVICE_OK)
      return ret;

   g_hub.dl450Model_.setWaiting(true);
   int tryCount = 0;
   const int maxCount = 100;
   while (g_hub.dl450Model_.isWaiting() && tryCount < maxCount)
   {
      CDeviceUtils::SleepMs(5);
      tryCount++;
   }
   if (maxCount == tryCount)
      return ERR_ANSWER_TIMEOUT;

   return GetPosition(pos);
}

int LampDL450::GetStatusDirect(unsigned short& stat)
{
   const int cmdLength = 6;
   unsigned char command[cmdLength];
 
   command[0] = 0x03; // size of data block = 2 bytes
   command[1] = 0x18; // command class
   command[2] = 0x51; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x01; // data byte 2 = subID
   command[5] = 0x07; // data byte 3 = deviceID

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, DL450);
   if (ret != DEVICE_OK)
      return ret;

   g_hub.dl450Model_.setWaiting(true);
   int tryCount = 0;
   const int maxCount = 100;
   while (g_hub.dl450Model_.isWaiting() && tryCount < maxCount)
   {
      CDeviceUtils::SleepMs(5);
      tryCount++;
   }
   if (maxCount == tryCount)
      return ERR_ANSWER_TIMEOUT;

   return g_hub.dl450Model_.GetState(stat);
}


int LampDL450::GetPosition(unsigned short& position)
{
   ZeissUShort pos(0);
   g_hub.dl450Model_.GetPosition(pos);
   position = pos;
   return DEVICE_OK;
}

int LampDL450::SetPosition(unsigned short pos)
{
   const int cmdLength = 8;
   unsigned char command[cmdLength];
 
   command[0] = 0x05; // size of data block = 5 bytes
   command[1] = 0x1B; // command class
   command[2] = 0x51; // command number
   command[3] = 0x11; // data byte 1 = processID
   command[4] = 0x02; // data byte 2 = subID
   command[5] = 0x07; // dev
   command[6] = 0x00; // data byte 4
   command[7] = 0x00; // data byte 5

   ZeissUShort tmp = htons((ZeissUShort) pos);
   memcpy(command+6, &tmp, ZeissShortSize); 

   int ret = g_hub.ExecuteCommand(*this, *GetCoreCallback(), command, cmdLength, DL450);
   if (ret != DEVICE_OK)
      return ret;

   g_hub.dl450Model_.MakeBusy();
   CDeviceUtils::SleepMs(100);

   ostringstream os;
   os << "DL450 position set at: " << pos;
   LogMessage(os.str().c_str(), false);
   return DEVICE_OK;

}

int LampDL450::OnState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)   {
      unsigned short pos;
      int ret = GetPositionDirect(pos);
      if (pos == 0)
      {
         // this means query failed so try again
         CDeviceUtils::SleepMs(100);
         ret = GetPositionDirect(pos);
      }
      if (ret != DEVICE_OK)
         return ret;
      pProp->Set((long)pos);
   }
   else if (eAct == MM::AfterSet)                             
   {  
      long pos;                                             
      pProp->Get(pos);                                        
      int ret = SetPosition((unsigned short)pos);                           
      if (ret != DEVICE_OK)                                   
         return ret;                                          
   }                                                          
                                                              
   return DEVICE_OK;                                          
}
