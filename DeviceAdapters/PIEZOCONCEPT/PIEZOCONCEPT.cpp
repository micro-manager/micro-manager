/*
 * PIEZOCONCEPT Device Adapter
 *
 * Copyright (C) 2008 Regents of the University of California
 *               2018 PIEZOCONCEPT
 *
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published by the
 * Free Software Foundation.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License
 * for more details.
 *
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY
 * DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 *
 * Based on the Arduino device adapter by Nico Stuurman
 */
#include "PIEZOCONCEPT.h"

#include "ModuleInterface.h"

#include <cstdio>
#include <sstream>
#include <vector>


#define ERR_UNEXPECTED_RESPONSE 105
#define ERR_PORT_OPEN_FAILED 106
#define ERR_HUB_UNAVAILABLE 108
#define ERR_VERSION_MISMATCH 109
#define ERR_UNKNOWN_AXIS 111

const char* g_DeviceNamePiezoConceptHub = "PIEZOCONCEPT-Controller";
const char* g_DeviceNameStage = "ZStage";
const char* g_DeviceNameXYStage = "XYStage";

const char* g_PropertyMinUm = "Stage Low Position(um)";
const char* g_PropertyMaxUm = "Stage High Position(um)";
const char* g_PropertyXMinUm = "X Stage Min Position(um)";
const char* g_PropertyXMaxUm = "X Stage Max Position(um)";
const char* g_PropertyYMinUm = "Y Stage Min Position(um)";
const char* g_PropertyYMaxUm = "Y Stage Max Position(um)";

const char* g_versionProp = "Version";

const char* g_On = "On";
const char* g_Off = "Off";


MODULE_API void InitializeModuleData()
{
   RegisterDevice(g_DeviceNamePiezoConceptHub, MM::HubDevice, "PIEZOCONCEPT Controller");
   RegisterDevice(g_DeviceNameStage, MM::StageDevice, "Z Stage");
   RegisterDevice(g_DeviceNameXYStage, MM::XYStageDevice, "XY Stage");
}

MODULE_API MM::Device* CreateDevice(const char* deviceName)
{
   if (deviceName == 0)
      return 0;

   if (strcmp(deviceName, g_DeviceNamePiezoConceptHub) == 0)
   {
      return new PiezoConceptHub();
   }
   if (strcmp(deviceName, g_DeviceNameStage) == 0)
   {
      return new CPiezoConceptStage();
   }
   if (strcmp(deviceName, g_DeviceNameXYStage) == 0)
   {
      return new CPiezoConceptXYStage();
   }

   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}


PiezoConceptHub::PiezoConceptHub() :
   initialized_(false),
   hasZStage_(false),
   hasXYStage_(false)
{
   InitializeDefaultErrorMessages();

   SetErrorText(ERR_UNEXPECTED_RESPONSE, "Unexpected response from device");
   SetErrorText(ERR_HUB_UNAVAILABLE, "Cannot access hub or serial port (incorrect hardware configuration)");
   SetErrorText(ERR_UNKNOWN_AXIS, "Unsupported axis type");
   SetErrorText(ERR_VERSION_MISMATCH, "The device has an incompatible firmware version");

   CPropertyAction* pAct = new CPropertyAction(this, &PiezoConceptHub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

PiezoConceptHub::~PiezoConceptHub()
{
   Shutdown();
}

void PiezoConceptHub::GetName(char* name) const
{
   CDeviceUtils::CopyLimitedString(name, g_DeviceNamePiezoConceptHub);
}

bool PiezoConceptHub::Busy()
{
   return false;
}

int PiezoConceptHub::GetControllerInfo()
{
   int ret = DEVICE_OK;

   ret = SendSerialCommand(port_.c_str(), "VERS_", "\n");
   if (ret != DEVICE_OK)
      return ret;

   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (strcmp(answer.c_str(), "Version 3.1.1") != 0)
   {
      std::stringstream ss;
      ss << "Error, wrong version of stage USB interface, expecting Version 3.1.1, got " << answer;
      LogMessage(ss.str());
      return ERR_VERSION_MISMATCH;
   }

   ret = SendSerialCommand(port_.c_str(), "INFO_", "\n");
   if (ret != DEVICE_OK)
      return ret;

   ret = GetSerialAnswer(port_.c_str(), "\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   if (answer != "Piezoconcept product :")
      return ERR_UNEXPECTED_RESPONSE;
   answer.clear();

   ret = GetSerialAnswer(port_.c_str(), "\n", answer);
   if (ret != DEVICE_OK)
      return ret;

   answer.clear();

   double travelX, travelY, travelZ;
   ret = GetAxisInfo(1, travelX);
   if (ret != DEVICE_OK)
      return ret;

   ret = GetAxisInfo(2, travelY);
   if (ret != DEVICE_OK)
      return ret;

   ret = GetAxisInfo(3, travelZ);
   if (ret != DEVICE_OK)
      return ret;

   if ((travelX >0) && (travelY >0))
   {
      hasXYStage_ = true;
   }

   if (travelZ > 0)
   {
      hasZStage_ = true;
   }

   return DEVICE_OK;
}

int PiezoConceptHub::GetAxisInfo(int axis, double& travel)
{
   int ret = DEVICE_ERR;
   if(axis < 1 || axis > 3)
      return DEVICE_ERR;

   std::stringstream cmd;
   switch(axis) {
      case 1:
         cmd << "INFOX";
         break;
      case 2:
         cmd << "INFOY";
         break;
      case 3:
         cmd << "INFOZ";
         break;
   }
   ret = SendSerialCommand(port_.c_str(), cmd.str().c_str(), "\n");
   if (ret != DEVICE_OK)
      return ret;
   std::string answer;
   ret = GetSerialAnswer(port_.c_str(), "\n", answer);
   if (ret != DEVICE_OK)
      return ret;
   std::stringstream ss(answer);
   std::string type, trav;
   getline(ss, type, ',');
   getline(ss, trav, ',');

   if (0 != strcmp(type.c_str(), "L"))
      return ERR_UNKNOWN_AXIS;

   std::stringstream sstravel(trav);
   sstravel >> travel;

   return DEVICE_OK;
}

int PiezoConceptHub::CheckForError()
{
   std::string ans;
   int ret = GetSerialAnswer(port_.c_str(), "\n", ans);
   if (ret != DEVICE_OK)
   {
      std::stringstream ss;
      ss << "Error in CheckForError, ret:" << ret;
      LogMessage(ss.str());
      return DEVICE_ERR;
   }
   if (strcmp(ans.c_str(), "Ok") != 0)
   {
      std::stringstream ss;
      ss << "Error reported by controller :" << ans;
      LogMessage(ss.str(), false);
      return DEVICE_ERR;
   }
   return DEVICE_OK;
}

MM::DeviceDetectionStatus PiezoConceptHub::DetectDevice()
{
   if (initialized_)
      return MM::CanCommunicate;

   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;
   char answerTO[MM::MaxStrLength];

   try
   {
      std::string portLowerCase = port_;
      for( std::string::iterator its = portLowerCase.begin(); its != portLowerCase.end(); ++its)
      {
         *its = (char)tolower(*its);
      }
      if( 0< portLowerCase.length() &&  0 != portLowerCase.compare("undefined")  && 0 != portLowerCase.compare("unknown") )
      {
         result = MM::CanNotCommunicate;
         // record the default answer time out
         GetCoreCallback()->GetDeviceProperty(port_.c_str(), "AnswerTimeout", answerTO);

         // device specific default communication parameters
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, g_Off);
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, "115200" );
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "1");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", "1000.0");
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "DelayBetweenCharsMs", "0");
         MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());
         pS->Initialize();

         CDeviceUtils::SleepMs(500);
         PurgeComPort(port_.c_str());
         int ret = GetControllerInfo();
         // later, Initialize will explicitly check the version #
         if( DEVICE_OK != ret )
         {
            LogMessageCode(ret, true);
         }
         else
         {
            // to succeed must reach here....
            result = MM::CanCommunicate;
         }
         pS->Shutdown();
         // always restore the AnswerTimeout to the default
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), "AnswerTimeout", answerTO);
      }
   }
   catch(...)
   {
      LogMessage("Exception in DetectDevice!", false);
   }

   return result;
}

int PiezoConceptHub::Initialize()
{
   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNamePiezoConceptHub, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Allow time for reset upon port open
   CDeviceUtils::SleepMs(500);

   // Check that we have a controller:
   PurgeComPort(port_.c_str());
   ret = GetControllerInfo();
   if( DEVICE_OK != ret)
      return ret;

   CPropertyAction* pAct = new CPropertyAction(this, &PiezoConceptHub::OnVersion);
   std::ostringstream sversion;
   sversion << version_;
   CreateProperty(g_versionProp, sversion.str().c_str(), MM::String, true, pAct);

   ret = SendSerialCommand(port_.c_str(), "_RAZ_", "\n");
   if (ret != DEVICE_OK)
      return ret;

   ret = CheckForError();
   if (ret != DEVICE_OK)
      return ret;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int PiezoConceptHub::DetectInstalledDevices()
{
   if (MM::CanCommunicate == DetectDevice())
   {
      std::vector<std::string> peripherals;
      peripherals.clear();

      if (hasZStage_) peripherals.push_back(g_DeviceNameStage);
      if (hasXYStage_) peripherals.push_back(g_DeviceNameXYStage);

      for (size_t i=0; i < peripherals.size(); i++)
      {
         MM::Device* pDev = ::CreateDevice(peripherals[i].c_str());
         if (pDev)
         {
            AddInstalledDevice(pDev);
         }
      }
   }

   return DEVICE_OK;
}

int PiezoConceptHub::Shutdown()
{
   initialized_ = false;
   return DEVICE_OK;
}

int PiezoConceptHub::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(port_.c_str());
   }
   else if (pAct == MM::AfterSet)
   {
      pProp->Get(port_);
      portAvailable_ = true;
   }
   return DEVICE_OK;
}

int PiezoConceptHub::OnVersion(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set(version_);
   }
   return DEVICE_OK;
}


CPiezoConceptStage::CPiezoConceptStage() :
stepSizeUm_(0.0001),
pos_um_(0.0),
busy_(false),
initialized_(false),
lowerLimit_(0.0),
upperLimit_(300.0)
{
   InitializeDefaultErrorMessages();

   CreateHubIDProperty();
}

CPiezoConceptStage::~CPiezoConceptStage()
{
   Shutdown();
}

void CPiezoConceptStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_DeviceNameStage);
}

int CPiezoConceptStage::Initialize()
{
   PiezoConceptHub* pHub = static_cast<PiezoConceptHub*>(GetParentHub());
   if (pHub)
   {
      char hubLabel[MM::MaxStrLength];
      pHub->GetLabel(hubLabel);
      SetParentID(hubLabel); // for backward comp.
   }
   else
      return ERR_HUB_UNAVAILABLE;

   if (initialized_)
      return DEVICE_OK;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNameStage, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "PIEZOCONCEPT Z stage driver", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   double travel;
   ret = pHub->GetAxisInfoH(3, travel);
   if (DEVICE_OK != ret)
      return ret;

   upperLimit_ = travel;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int CPiezoConceptStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int CPiezoConceptStage::GetPositionUm(double &pos)
{
   pos = pos_um_;
   return DEVICE_OK;
}

int CPiezoConceptStage::SetPositionUm(double pos)
{
   int ret = MoveZ(pos);
   if(ret != DEVICE_OK){
      return ret;
   }

   return OnStagePositionChanged(pos);
}

int CPiezoConceptStage::GetPositionSteps(long& steps)
{
   double posUm;
   int ret = GetPositionUm(posUm);
   if (ret != DEVICE_OK)
      return ret;
   steps = static_cast<long>(posUm / GetStepSize());
   return DEVICE_OK;
}

int CPiezoConceptStage::SetPositionSteps(long steps)
{
   return SetPositionUm(steps * GetStepSize());
}

int CPiezoConceptStage::MoveZ(double pos)
{
   PiezoConceptHub* hub = static_cast<PiezoConceptHub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_HUB_UNAVAILABLE;

   if (pos > upperLimit_)
   {
      pos = upperLimit_;
   }
   if(pos < lowerLimit_)
   {
      pos = lowerLimit_;
   }
   char buf[25];
   int length = sprintf(buf, "MOVEZ %1.3fu\n", pos);
   std::stringstream ss;
   ss << "Command: "<< buf << "  Position set: "<< pos;
   LogMessage(ss.str().c_str(), true);

   pos_um_ = pos;

   int ret = hub->WriteToComPortH((unsigned char*)buf, length);
   if(ret != DEVICE_OK)
      return ret;
   return hub->CheckForError();
}

bool CPiezoConceptStage::Busy()
{
   return false;
}


CPiezoConceptXYStage::CPiezoConceptXYStage() : CXYStageBase<CPiezoConceptXYStage>(),
stepSize_X_um_(0.1),
stepSize_Y_um_(0.1),
posX_um_(0.0),
posY_um_(0.0),
busy_(false),
initialized_(false),
lowerLimitX_(0.0),
upperLimitX_(300.0),
lowerLimitY_(0.0),
upperLimitY_(300.0)
{
   InitializeDefaultErrorMessages();

   // parent ID display
   CreateHubIDProperty();

   // step size
   CPropertyAction* pAct = new CPropertyAction (this, &CPiezoConceptXYStage::OnXStageMinPos);
   CreateProperty(g_PropertyXMinUm, "0", MM::Float, false, pAct, true);

   pAct = new CPropertyAction (this, &CPiezoConceptXYStage::OnXStageMaxPos);
   CreateProperty(g_PropertyXMaxUm, "200", MM::Float, false, pAct, true);

   pAct = new CPropertyAction (this, &CPiezoConceptXYStage::OnYStageMinPos);
   CreateProperty(g_PropertyYMinUm, "0", MM::Float, false, pAct, true);

   pAct = new CPropertyAction (this, &CPiezoConceptXYStage::OnYStageMaxPos);
   CreateProperty(g_PropertyYMaxUm, "200", MM::Float, false, pAct, true);
}

CPiezoConceptXYStage::~CPiezoConceptXYStage()
{
   Shutdown();
}

void CPiezoConceptXYStage::GetName(char* Name) const
{
   CDeviceUtils::CopyLimitedString(Name, g_DeviceNameXYStage);
}

int CPiezoConceptXYStage::Initialize()
{
   PiezoConceptHub* pHub = static_cast<PiezoConceptHub*>(GetParentHub());
   if (pHub)
   {
      char hubLabel[MM::MaxStrLength];
      pHub->GetLabel(hubLabel);
      SetParentID(hubLabel); // for backward comp.
   }
   else
      return ERR_HUB_UNAVAILABLE;

   if (initialized_)
      return DEVICE_OK;

   // Name
   int ret = CreateProperty(MM::g_Keyword_Name, g_DeviceNameXYStage, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "XY stage driver", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   double travelX, travelY;
   ret = pHub->GetAxisInfoH(1, travelX);
   if (DEVICE_OK != ret)
      return ret;

   ret = pHub->GetAxisInfoH(2, travelY);
   if (DEVICE_OK != ret)
      return ret;

   upperLimitX_ = travelX;
   stepSize_X_um_ = travelX / 65535;
   upperLimitY_ = travelY;
   stepSize_Y_um_ = travelY / 65535;

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

int CPiezoConceptXYStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool CPiezoConceptXYStage::Busy()
{
   return false;
}

int CPiezoConceptXYStage::GetPositionSteps(long& x, long& y)
{
   x = (long) (posX_um_ / stepSize_X_um_);
   y = (long) (posY_um_ / stepSize_Y_um_);

   std::stringstream ss;
   ss << "GetPositionSteps :=" << x << "," << y;
   LogMessage(ss.str(), true);
   return DEVICE_OK;
}

 int CPiezoConceptXYStage::SetPositionSteps(long x, long y)
{
    double posX = x * stepSize_X_um_;
    double posY = y * stepSize_Y_um_;

    std::stringstream ss;
    ss << "Current position = " << posX_um_ << "," << posY_um_ << " \n Commanded position = " << posX << "," << posY;
    LogMessage(ss.str(), true);
    int ret = DEVICE_OK;
    if (posX_um_ != posX)
    {
       ret = MoveX(posX);
       if (ret != DEVICE_OK)
          return ret;
    }
    if(posY_um_ != posY)
    {
       ret = MoveY(posY);
       if(ret != DEVICE_OK)
          return ret;
    }
    return OnXYStagePositionChanged(posX_um_, posY_um_);
}

int CPiezoConceptXYStage::SetRelativePositionSteps(long x, long y)
{
   long curX, curY;
   GetPositionSteps(curX, curY);

   return SetPositionSteps(curX + x, curY + y);
}

int CPiezoConceptXYStage::MoveX(double posUm)
{
   PiezoConceptHub* hub = static_cast<PiezoConceptHub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_HUB_UNAVAILABLE;

   if (posUm < lowerLimitX_)
   {
      posUm = lowerLimitX_;
   }
   if (posUm > upperLimitX_)
   {
      posUm = upperLimitX_;
   }

   char buf[25];
   int length = sprintf(buf, "MOVEX %1.3fu\n", posUm);
   std::stringstream ss;
   ss << "Command: "<< buf << "  Position set: "<< posUm;
   LogMessage(ss.str().c_str(), true);

   int ret = hub->WriteToComPortH((unsigned char*)buf, length);
   if (ret != DEVICE_OK)
      return ret;
   ret = hub->CheckForError();
   if (ret != DEVICE_OK)
      return ret;
   posX_um_ = posUm;
   return DEVICE_OK;
}

int CPiezoConceptXYStage::MoveY(double posUm)
{
   PiezoConceptHub* hub = static_cast<PiezoConceptHub*>(GetParentHub());
   if (!hub || !hub->IsPortAvailable())
      return ERR_HUB_UNAVAILABLE;

   if (posUm < lowerLimitY_)
   {
      posUm = lowerLimitY_;
   }
   if (posUm > upperLimitY_)
   {
      posUm = upperLimitY_;
   }

   char buf[25];
   int length = sprintf(buf, "MOVEY %1.3fu\n", posUm);
   std::stringstream ss;
   ss << "Command: "<< buf << "  Position set: "<< posUm;
   LogMessage(ss.str().c_str(), true);

   int ret = hub->WriteToComPortH((unsigned char*)buf, length);
   if (ret != DEVICE_OK)
      return ret;
   ret = hub->CheckForError();
   if (ret != DEVICE_OK)
      return ret;
   posY_um_ = posUm;
   return DEVICE_OK;
}

int CPiezoConceptXYStage::OnXStageMinPos(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(lowerLimitX_);
      ret = DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      double limit;
      pProp->Get(limit);
      lowerLimitX_ = limit;

      ret = DEVICE_OK;
   }

   return ret;
}

int CPiezoConceptXYStage::OnXStageMaxPos(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(upperLimitX_);
      ret = DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      double limit;
      pProp->Get(limit);
      upperLimitX_ = limit;

      ret = DEVICE_OK;
   }

   return ret;
}

int CPiezoConceptXYStage::OnYStageMinPos(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(lowerLimitY_);
      ret = DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      double limit;
      pProp->Get(limit);
      lowerLimitY_ = limit;

      ret = DEVICE_OK;
   }

   return ret;
}

int CPiezoConceptXYStage::OnYStageMaxPos(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   int ret = DEVICE_ERR;
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(upperLimitY_);
      ret = DEVICE_OK;
   }
   else if (eAct == MM::AfterSet)
   {
      double limit;
      pProp->Get(limit);
      upperLimitY_ = limit;

      ret = DEVICE_OK;
   }

   return ret;
}
