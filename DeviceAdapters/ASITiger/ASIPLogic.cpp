///////////////////////////////////////////////////////////////////////////////
// FILE:          ASIPLogic.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   ASI programmable logic card device adapter
//
// COPYRIGHT:     Applied Scientific Instrumentation, Eugene OR
//
// LICENSE:       This file is distributed under the BSD license.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.
//
// AUTHOR:        Jon Daniels (jon@asiimaging.com) 10/2014
//
// BASED ON:      ASIStage.cpp and others
//


#include "ASIPLogic.h"
#include "ASITiger.h"
#include "ASIHub.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceUtils.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/MMDevice.h"
#include <iostream>
#include <cmath>
#include <sstream>
#include <string>
#include <vector>

#define PLOGIC_NUM_ADDRESSES     128
#define PLOGIC_INVERT_ADDRESS    64
#define PLOGIC_FRONTPANEL_START_ADDRESS  33
#define PLOGIC_FRONTPANEL_END_ADDRESS    40
#define PLOGIC_FRONTPANEL_NUM            (PLOGIC_FRONTPANEL_END_ADDRESS - PLOGIC_FRONTPANEL_START_ADDRESS + 1)
#define PLOGIC_BACKPLANE_START_ADDRESS   41
#define PLOGIC_BACKPLANE_END_ADDRESS     48
#define PLOGIC_BACKPLANE_NUM             (PLOGIC_BACKPLANE_END_ADDRESS - PLOGIC_BACKPLANE_START_ADDRESS + 1)
#define PLOGIC_PHYSICAL_IO_START_ADDRESS   PLOGIC_FRONTPANEL_START_ADDRESS
#define PLOGIC_PHYSICAL_IO_END_ADDRESS     PLOGIC_BACKPLANE_END_ADDRESS
#define PLOGIC_PHYSICAL_IO_NUM            (PLOGIC_PHYSICAL_IO_END_ADDRESS - PLOGIC_PHYSICAL_IO_START_ADDRESS + 1)

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// CPLogic
//
CPLogic::CPLogic(const char* name) :
   ASIPeripheralBase< ::CShutterBase, CPLogic >(name),
   axisLetter_(g_EmptyAxisLetterStr),    // value determined by extended name
   numCells_(16),
   currentPosition_(1),
   useAsdiSPIMShutter_(false),
   shutterOpen_(false),
   advancedPropsEnabled_(false),
   editCellUpdates_(true)
{
   if (IsExtendedName(name))  // only set up these properties if we have the required information in the name
   {
      axisLetter_ = GetAxisLetterFromExtName(name);
      CreateProperty(g_AxisLetterPropertyName, axisLetter_.c_str(), MM::String, true);
   }

   CPropertyAction* pAct;

   // pre-init property to say how PLogic card is used
   // for now the only option is have shutter functionality for diSPIM (laser controls on BNCs 5-8)
   pAct = new CPropertyAction (this, &CPLogic::OnPLogicMode);
   CreateProperty(g_PLogicModePropertyName, g_PLogicModeNone, MM::String, false, pAct, true);
   AddAllowedValue(g_PLogicModePropertyName, g_PLogicModeNone);
   AddAllowedValue(g_PLogicModePropertyName, g_PLogicModediSPIMShutter);
}

int CPLogic::Initialize()
{
   // call generic Initialize first, this gets hub
   RETURN_ON_MM_ERROR( PeripheralInitialize() );

   // create MM description; this doesn't work during hardware configuration wizard but will work afterwards
   ostringstream command;
   command.str("");
   command << g_PLogicDeviceDescription << " HexAddr=" << addressString_;
   CreateProperty(MM::g_Keyword_Description, command.str().c_str(), MM::String, true);

   CPropertyAction* pAct;

   // try to detect the number of cells from the build name
   char buildName[MM::MaxStrLength];
   unsigned int tmp;
   GetProperty(g_FirmwareBuildPropertyName, buildName);
   string s = buildName;
   hub_->SetLastSerialAnswer(s);
   int ret = hub_->ParseAnswerAfterUnderscore(tmp);
   if (!ret) {
      numCells_ = tmp;
   }
   command.str("");
   command << numCells_;
   CreateProperty(g_NumLogicCellsPropertyName, command.str().c_str(), MM::Integer, true);

   // pointer position, this is where edits/queries are made in general
   pAct = new CPropertyAction (this, &CPLogic::OnPointerPosition);
   CreateProperty(g_PointerPositionPropertyName, "0", MM::Integer, false, pAct);
   UpdateProperty(g_PointerPositionPropertyName);

   // reports the output state of the logic cell array as unsigned integer
   pAct = new CPropertyAction (this, &CPLogic::OnPLogicOutputState);
   CreateProperty(g_PLogicOutputStatePropertyName, "0", MM::Integer, true, pAct);
   UpdateProperty(g_PLogicOutputStatePropertyName);

   // reports the output state of the BNCs as unsigned integer
   pAct = new CPropertyAction (this, &CPLogic::OnFrontpanelOutputState);
   CreateProperty(g_FrontpanelOutputStatePropertyName, "0", MM::Integer, true, pAct);
   UpdateProperty(g_FrontpanelOutputStatePropertyName);

   // reports the output state of the backplane IO as unsigned integer
   pAct = new CPropertyAction (this, &CPLogic::OnBackplaneOutputState);
   CreateProperty(g_BackplaneOutputStatePropertyName, "0", MM::Integer, true, pAct);
   UpdateProperty(g_BackplaneOutputStatePropertyName);

   // sets the trigger source
   pAct = new CPropertyAction (this, &CPLogic::OnTriggerSource);
   CreateProperty(g_TriggerSourcePropertyName, "0", MM::String, false, pAct);
   AddAllowedValue(g_TriggerSourcePropertyName, g_TriggerSourceCode0, 0);
   AddAllowedValue(g_TriggerSourcePropertyName, g_TriggerSourceCode1, 1);
   AddAllowedValue(g_TriggerSourcePropertyName, g_TriggerSourceCode2, 2);
   AddAllowedValue(g_TriggerSourcePropertyName, g_TriggerSourceCode3, 3);
   AddAllowedValue(g_TriggerSourcePropertyName, g_TriggerSourceCode4, 4);
   UpdateProperty(g_TriggerSourcePropertyName);

   // preset selector
   pAct = new CPropertyAction (this, &CPLogic::OnSetCardPreset);
   CreateProperty(g_SetCardPresetPropertyName, g_PresetCodeNone, MM::String, false, pAct);
   AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCodeNone, -1);
   AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode0, 0);
   AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode1, 1);
   AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode2, 2);
   AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode3, 3);
   AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode4, 4);
   AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode5, 5);
   AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode6, 6);
   AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode7, 7);
   AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode8, 8);
   AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode9, 9);
   AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode10, 10);
   AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode11, 11);
   if (FirmwareVersionAtLeast(3.08)) {
      AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode12, 12);
   } else {
      AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode12_original, 12);
   }
   AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode13, 13);
   AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode14, 14);
   if (FirmwareVersionAtLeast(3.06)) {
      AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode15, 15);
      AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode16, 16);
      AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode17, 17);
      AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode18, 18);
      AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode19, 19);
   }
   if (FirmwareVersionAtLeast(3.07)) {
      AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode20, 20);
      AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode21, 21);
      AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode22, 22);
   }
   if (FirmwareVersionAtLeast(3.09)) {
      AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode23, 23);
      AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode24, 24);
      AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode25, 25);
      AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode26, 26);
   }
   if (FirmwareVersionAtLeast(3.17)) {
      AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode27, 27);
   }
   if (FirmwareVersionAtLeast(3.19)) {
      AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode28, 28);
      AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode29, 29);
      AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode30, 30);
      AddAllowedValue(g_SetCardPresetPropertyName, g_PresetCode31, 31);
   }
   UpdateProperty(g_SetCardPresetPropertyName);


   // "do it" property to clear state
   pAct = new CPropertyAction (this, &CPLogic::OnClearAllCellStates);
   CreateProperty(g_ClearAllCellStatesPropertyName, g_IdleState, MM::String, false, pAct);
   AddAllowedValue(g_ClearAllCellStatesPropertyName, g_IdleState, 0);
   AddAllowedValue(g_ClearAllCellStatesPropertyName, g_DoItState, 1);
   AddAllowedValue(g_ClearAllCellStatesPropertyName, g_DoneState, 2);
   UpdateProperty(g_ClearAllCellStatesPropertyName);

   // refresh properties from controller every time; default is false = no refresh (speeds things up by not redoing so much serial comm)
   pAct = new CPropertyAction (this, &CPLogic::OnRefreshProperties);
   CreateProperty(g_RefreshPropValsPropertyName, g_NoState, MM::String, false, pAct);
   AddAllowedValue(g_RefreshPropValsPropertyName, g_NoState);
   AddAllowedValue(g_RefreshPropValsPropertyName, g_YesState);

   // save settings to controller if requested
   pAct = new CPropertyAction (this, &CPLogic::OnSaveCardSettings);
   CreateProperty(g_SaveSettingsPropertyName, g_SaveSettingsOrig, MM::String, false, pAct);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsX);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsY);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsZ);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsOrig);
   AddAllowedValue(g_SaveSettingsPropertyName, g_SaveSettingsDone);

   // edits cell wherever current pointer position is
   pAct = new CPropertyAction (this, &CPLogic::OnEditCellType);
   CreateProperty(g_EditCellTypePropertyName, g_CellTypeCode0, MM::String, false, pAct);
   AddAllowedValue(g_EditCellTypePropertyName, g_CellTypeCode0, 0);
   AddAllowedValue(g_EditCellTypePropertyName, g_CellTypeCode1, 1);
   AddAllowedValue(g_EditCellTypePropertyName, g_CellTypeCode2, 2);
   AddAllowedValue(g_EditCellTypePropertyName, g_CellTypeCode3, 3);
   AddAllowedValue(g_EditCellTypePropertyName, g_CellTypeCode4, 4);
   AddAllowedValue(g_EditCellTypePropertyName, g_CellTypeCode5, 5);
   AddAllowedValue(g_EditCellTypePropertyName, g_CellTypeCode6, 6);
   AddAllowedValue(g_EditCellTypePropertyName, g_CellTypeCode7, 7);
   AddAllowedValue(g_EditCellTypePropertyName, g_CellTypeCode8, 8);
   AddAllowedValue(g_EditCellTypePropertyName, g_CellTypeCode9, 9);
   AddAllowedValue(g_EditCellTypePropertyName, g_CellTypeCode10, 10);
   AddAllowedValue(g_EditCellTypePropertyName, g_CellTypeCode11, 11);
   AddAllowedValue(g_EditCellTypePropertyName, g_CellTypeCode12, 12);
   AddAllowedValue(g_EditCellTypePropertyName, g_CellTypeCode13, 13);
   AddAllowedValue(g_EditCellTypePropertyName, g_CellTypeCode14, 14);
   AddAllowedValue(g_EditCellTypePropertyName, g_CellTypeCode15, 15);
   UpdateProperty(g_EditCellTypePropertyName);

   // edit config at current pointer
   pAct = new CPropertyAction (this, &CPLogic::OnEditCellConfig);
   CreateProperty(g_EditCellConfigPropertyName, "0", MM::Integer, false, pAct);
   UpdateProperty(g_EditCellConfigPropertyName);

   // edit logic cell inputs at current pointer
   pAct = new CPropertyAction (this, &CPLogic::OnEditCellInput1);
   CreateProperty(g_EditCellInput1PropertyName, "0", MM::Integer, false, pAct);
   UpdateProperty(g_EditCellInput1PropertyName);
   pAct = new CPropertyAction (this, &CPLogic::OnEditCellInput2);
   CreateProperty(g_EditCellInput2PropertyName, "0", MM::Integer, false, pAct);
   UpdateProperty(g_EditCellInput2PropertyName);
   pAct = new CPropertyAction (this, &CPLogic::OnEditCellInput3);
   CreateProperty(g_EditCellInput3PropertyName, "0", MM::Integer, false, pAct);
   UpdateProperty(g_EditCellInput3PropertyName);
   pAct = new CPropertyAction (this, &CPLogic::OnEditCellInput4);
   CreateProperty(g_EditCellInput4PropertyName, "0", MM::Integer, false, pAct);
   UpdateProperty(g_EditCellInput4PropertyName);

   // refresh properties from controller every time; default is false = no refresh (speeds things up by not redoing so much serial comm)
   pAct = new CPropertyAction (this, &CPLogic::OnEditCellUpdates);
   CreateProperty(g_EditCellUpdateAutomaticallyPropertyName, g_YesState, MM::String, false, pAct);
   AddAllowedValue(g_EditCellUpdateAutomaticallyPropertyName, g_NoState);
   AddAllowedValue(g_EditCellUpdateAutomaticallyPropertyName, g_YesState);

   // generates a set of additional advanced properties that are used only rarely
   // in this case they allow configuring all the logic cells and setting outputs
   pAct = new CPropertyAction (this, &CPLogic::OnAdvancedProperties);
   CreateProperty(g_AdvancedPropertiesPropertyName, g_NoState, MM::String, false, pAct);
   AddAllowedValue(g_AdvancedPropertiesPropertyName, g_NoState);
   AddAllowedValue(g_AdvancedPropertiesPropertyName, g_YesState);
   UpdateProperty(g_AdvancedPropertiesPropertyName);

   if (useAsdiSPIMShutter_)
   {
      // special masked preset selector for shutter channel
      pAct = new CPropertyAction (this, &CPLogic::OnSetShutterChannel);
      CreateProperty(g_SetChannelPropertyName, g_ChannelNone, MM::String, false, pAct);
      // use (CCA X) card presets here, just under a different name
      AddAllowedValue(g_SetChannelPropertyName, g_ChannelNone, 9);
      AddAllowedValue(g_SetChannelPropertyName, g_ChannelOnly5, 5);
      AddAllowedValue(g_SetChannelPropertyName, g_ChannelOnly6, 6);
      AddAllowedValue(g_SetChannelPropertyName, g_ChannelOnly7, 7);
      AddAllowedValue(g_SetChannelPropertyName, g_ChannelOnly8, 8);
      if (FirmwareVersionAtLeast(3.19)) {
         AddAllowedValue(g_SetChannelPropertyName, g_Channel6And7, 28);
         AddAllowedValue(g_SetChannelPropertyName, g_Channel5To7, 29);
         AddAllowedValue(g_SetChannelPropertyName, g_Channel5To8, 30);
         AddAllowedValue(g_SetChannelPropertyName, g_Channel5To8Alt, 31);
      }
      UpdateProperty(g_SetChannelPropertyName); // doesn't do anything right now
      // makes sure card actually gets initialized
      // SetProperty(g_SetChannelPropertyName, g_ChannelNone);  // done via SetProperty(g_SetCardPresetPropertyName, g_PresetCode14)

      // set up card up for diSPIM shutter
      // this sets up all 8 BNC outputs
      // also it sets the card to be set to shutter channel "none"
      SetProperty(g_SetCardPresetPropertyName, g_PresetCode14);

      // always start shutter in closed state
      SetOpen(false);

      // set to be triggered by micro-mirror card
      SetProperty(g_TriggerSourcePropertyName, g_TriggerSourceCode1);
   }

   initialized_ = true;
   return DEVICE_OK;
}

int CPLogic::SetOpen(bool open)
{
   if (useAsdiSPIMShutter_)
   {
      ostringstream command; command.str("");
      shutterOpen_ = open;
      if (open) {
         SetProperty(g_SetCardPresetPropertyName, g_PresetCode11);
      } else {
         SetProperty(g_SetCardPresetPropertyName, g_PresetCode10);
      }
   }
   return DEVICE_OK;
}

int CPLogic::GetOpen(bool& open)
{
   open = useAsdiSPIMShutter_ && shutterOpen_;
   return DEVICE_OK;
}

////////////////
// action handlers

int CPLogic::OnPLogicMode(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet) {
      // do nothing for now
   } else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      useAsdiSPIMShutter_ = (tmpstr.compare(g_PLogicModediSPIMShutter) == 0);
   }
   return DEVICE_OK;
}

int CPLogic::OnSetShutterChannel(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      // can't do anything of real value here
      if (!initialized_)
         pProp->Set(g_ChannelNone);
   } else if (eAct == MM::AfterSet) {
      ostringstream command; command.str("");
      long tmp;
      RETURN_ON_MM_ERROR ( GetCurrentPropertyData(g_SetChannelPropertyName, tmp) );
      if (tmp < 0) return DEVICE_OK;  // no preset and other "signaling" preset codes are negative
      command << addressChar_ << "CCA X=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
      return DEVICE_OK;
   }
   return DEVICE_OK;
}

int CPLogic::OnPLogicOutputState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   unsigned int val;
   ostringstream command; command.str("");
   if (eAct == MM::BeforeGet || eAct == MM::AfterSet)
   {
      // always read
      command << addressChar_ << "RDADC Z?";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition2(val) );
      if (!pProp->Set((long)val))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   return DEVICE_OK;
}

int CPLogic::OnFrontpanelOutputState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   unsigned int val;
   ostringstream command; command.str("");
   if (eAct == MM::BeforeGet || eAct == MM::AfterSet)
   {
      // always read
      command << addressChar_ << "RDADC X?";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition2(val) );
      if (!pProp->Set((long)val))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   return DEVICE_OK;
}

int CPLogic::OnBackplaneOutputState(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   unsigned int val;
   ostringstream command; command.str("");
   if (eAct == MM::BeforeGet || eAct == MM::AfterSet)
   {
      // always read
      command << addressChar_ << "RDADC Y?";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition2(val) );
      if (!pProp->Set((long)val))
         return DEVICE_INVALID_PROPERTY_VALUE;
   }
   return DEVICE_OK;
}

int CPLogic::OnTriggerSource(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp;
   string tmpstr;
   if (eAct == MM::BeforeGet) {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      command << "PM " << axisLetter_ << "?";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), axisLetter_) );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      bool success = 0;
      switch (tmp) {
         case 0: success = pProp->Set(g_TriggerSourceCode0); break;
         case 1: success = pProp->Set(g_TriggerSourceCode1); break;
         case 2: success = pProp->Set(g_TriggerSourceCode2); break;
         case 3: success = pProp->Set(g_TriggerSourceCode3); break;
         case 4: success = pProp->Set(g_TriggerSourceCode4); break;
         default: success=0;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   } else if (eAct == MM::AfterSet) {
      RETURN_ON_MM_ERROR ( GetCurrentPropertyData(g_TriggerSourcePropertyName, tmp) );
      command << "PM " << axisLetter_ << "=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   }
   return DEVICE_OK;
}

int CPLogic::OnPointerPosition(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   static bool justSet = false;
   ostringstream command; command.str("");
   if (eAct == MM::BeforeGet)
   {
      if (!refreshProps_ && initialized_ && !justSet)
         return DEVICE_OK;
      RefreshCurrentPosition();
      if (!pProp->Set((long)currentPosition_))
         return DEVICE_INVALID_PROPERTY_VALUE;
      RETURN_ON_MM_ERROR ( RefreshEditCellPropertyValues() );
      justSet = false;
   } else  if (eAct == MM::AfterSet)
   {
      long val;
      pProp->Get(val);
      command << "M " << axisLetter_ << "=" << val;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
      // read the result to make sure it happened
      justSet = true;
      return OnPointerPosition(pProp, MM::BeforeGet);
   }
   return DEVICE_OK;
}

int CPLogic::OnSaveCardSettings(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string tmpstr;
   ostringstream command; command.str("");
   if (eAct == MM::AfterSet) {
      command << addressChar_ << "SS ";
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_SaveSettingsOrig) == 0)
         return DEVICE_OK;
      if (tmpstr.compare(g_SaveSettingsDone) == 0)
         return DEVICE_OK;
      if (tmpstr.compare(g_SaveSettingsX) == 0)
         command << 'X';
      else if (tmpstr.compare(g_SaveSettingsY) == 0)
         command << 'X';
      else if (tmpstr.compare(g_SaveSettingsZ) == 0)
         command << 'Z';
      RETURN_ON_MM_ERROR (hub_->QueryCommandVerify(command.str(), ":A", (long)200));  // note 200ms delay added
      pProp->Set(g_SaveSettingsDone);
   }
   return DEVICE_OK;
}

int CPLogic::OnRefreshProperties(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string tmpstr;
   if (eAct == MM::AfterSet) {
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_YesState) == 0)
         refreshProps_ = true;
      else
         refreshProps_ = false;
   }
   return DEVICE_OK;
}

int CPLogic::RefreshEditCellPropertyValues()
{
   // force on property refresh
   char refreshPropValsStr[MM::MaxStrLength];
   GetProperty(g_RefreshPropValsPropertyName, refreshPropValsStr);
   SetProperty(g_RefreshPropValsPropertyName, g_YesState);

   if (editCellUpdates_ && currentPosition_<=numCells_) {
      // if it's on a cell
      UpdateProperty(g_EditCellTypePropertyName);
      UpdateProperty(g_EditCellConfigPropertyName);
      UpdateProperty(g_EditCellInput1PropertyName);
      UpdateProperty(g_EditCellInput2PropertyName);
      UpdateProperty(g_EditCellInput3PropertyName);
      UpdateProperty(g_EditCellInput4PropertyName);
   } else if (editCellUpdates_ && currentPosition_>=PLOGIC_PHYSICAL_IO_START_ADDRESS
         && currentPosition_<=PLOGIC_PHYSICAL_IO_END_ADDRESS) {
      // if position is an I/O
      // CellType overlaps with the IOType, but different list of possibilities so
      //    let's not worry about that for now
      UpdateProperty(g_EditCellConfigPropertyName);  // this is the source address
   }

   // restore setting
   SetProperty(g_RefreshPropValsPropertyName, refreshPropValsStr);

   return DEVICE_OK;
}

int CPLogic::OnEditCellType(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   if(currentPosition_ <= numCells_) {
      if (eAct == MM::BeforeGet) {
         return OnCellType(pProp, eAct, (long)currentPosition_);
      } else if (eAct == MM::AfterSet) {
         long tmp;
         RETURN_ON_MM_ERROR ( GetCurrentPropertyData(g_EditCellTypePropertyName, tmp) );
         command << addressChar_ << "CCA Y=" << tmp;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
         RETURN_ON_MM_ERROR ( RefreshEditCellPropertyValues() );
      }

   }
   return DEVICE_OK;
}

int CPLogic::OnEditCellConfig(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if((currentPosition_ <= numCells_) ||  // editing "config" of I/O is possible too
         ((currentPosition_ >= PLOGIC_PHYSICAL_IO_START_ADDRESS) && (currentPosition_ <= PLOGIC_PHYSICAL_IO_END_ADDRESS))) {
      RETURN_ON_MM_ERROR ( OnCellConfig(pProp, eAct, (long)currentPosition_) );
   }
   return DEVICE_OK;
}

int CPLogic::OnEditCellInput1(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if(currentPosition_ <= numCells_) {
      RETURN_ON_MM_ERROR ( OnInput1(pProp, eAct, (long)currentPosition_) );
   }
   return DEVICE_OK;
}

int CPLogic::OnEditCellInput2(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if(currentPosition_ <= numCells_) {
      RETURN_ON_MM_ERROR ( OnInput2(pProp, eAct, (long)currentPosition_) );
   }
   return DEVICE_OK;
}

int CPLogic::OnEditCellInput3(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if(currentPosition_ <= numCells_) {
      RETURN_ON_MM_ERROR ( OnInput3(pProp, eAct, (long)currentPosition_) );
   }
   return DEVICE_OK;
}

int CPLogic::OnEditCellInput4(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if(currentPosition_ <= numCells_) {
      RETURN_ON_MM_ERROR ( OnInput4(pProp, eAct, (long)currentPosition_) );
   }
   return DEVICE_OK;
}

int CPLogic::OnEditCellUpdates(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   string tmpstr;
   if (eAct == MM::AfterSet) {
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_YesState) == 0)
         editCellUpdates_ = true;
      else
         editCellUpdates_ = false;
   }
   return DEVICE_OK;
}

int CPLogic::GetCellPropertyName(long index, string suffix, char* name)
{
   ostringstream os;
   os << "PCell_" << setw(2) << setfill('0') << index << suffix;
   CDeviceUtils::CopyLimitedString(name, os.str().c_str());
   return DEVICE_OK;
}

int CPLogic::GetIOPropertyName(long index, string suffix, char* name)
{
   ostringstream os;
   if (index < PLOGIC_BACKPLANE_START_ADDRESS) {  // front panel
      os << "IOFrontpanel_" << index - PLOGIC_FRONTPANEL_START_ADDRESS + 1;
   } else {  // backplane
      os << "IOBackplane_" << index - PLOGIC_BACKPLANE_START_ADDRESS;
   }
   os << suffix;
   CDeviceUtils::CopyLimitedString(name, os.str().c_str());
   return DEVICE_OK;
}

int CPLogic::OnAdvancedProperties(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   if (eAct == MM::BeforeGet)
   {
      return DEVICE_OK; // do nothing
   }
   else if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if ((tmpstr.compare(g_YesState) == 0) && !advancedPropsEnabled_)  // after creating advanced properties once no need to repeat
      {
         CPropertyActionEx* pActEx;
         char propName[MM::MaxStrLength];

         advancedPropsEnabled_ = true;

         // force-on refresh
         char refreshPropValsStr[MM::MaxStrLength];
         GetProperty(g_RefreshPropValsPropertyName, refreshPropValsStr);
         SetProperty(g_RefreshPropValsPropertyName, g_YesState);

         for (long i=1; i<=(long)numCells_; i++) {

            // logic cell type
            GetCellPropertyName(i, "_CellType", propName);
            pActEx = new CPropertyActionEx (this, &CPLogic::OnCellType, i);
            CreateProperty(propName, g_CellTypeCode0, MM::String, false, pActEx);
            AddAllowedValue(propName, g_CellTypeCode0, 0);
            AddAllowedValue(propName, g_CellTypeCode1, 1);
            AddAllowedValue(propName, g_CellTypeCode2, 2);
            AddAllowedValue(propName, g_CellTypeCode3, 3);
            AddAllowedValue(propName, g_CellTypeCode4, 4);
            AddAllowedValue(propName, g_CellTypeCode5, 5);
            AddAllowedValue(propName, g_CellTypeCode6, 6);
            AddAllowedValue(propName, g_CellTypeCode7, 7);
            AddAllowedValue(propName, g_CellTypeCode8, 8);
            AddAllowedValue(propName, g_CellTypeCode9, 9);
            AddAllowedValue(propName, g_CellTypeCode10, 10);
            AddAllowedValue(propName, g_CellTypeCode11, 11);
            AddAllowedValue(propName, g_CellTypeCode12, 12);
            AddAllowedValue(propName, g_CellTypeCode13, 13);
            AddAllowedValue(propName, g_CellTypeCode14, 14);
            AddAllowedValue(propName, g_CellTypeCode15, 15);
            UpdateProperty(propName);

            // logic cell CCA Z code
            GetCellPropertyName(i, "_Config", propName);
            pActEx = new CPropertyActionEx (this, &CPLogic::OnCellConfig, i);
            CreateProperty(propName, "0", MM::Integer, false, pActEx);
            UpdateProperty(propName);

            // logic cell input X code
            GetCellPropertyName(i, "_Input1", propName);
            pActEx = new CPropertyActionEx (this, &CPLogic::OnInput1, i);
            CreateProperty(propName, "0", MM::Integer, false, pActEx);
            UpdateProperty(propName);

            // logic cell input Y code
            GetCellPropertyName(i, "_Input2", propName);
            pActEx = new CPropertyActionEx (this, &CPLogic::OnInput2, i);
            CreateProperty(propName, "0", MM::Integer, false, pActEx);
            UpdateProperty(propName);

            // logic cell input Z code
            GetCellPropertyName(i, "_Input3", propName);
            pActEx = new CPropertyActionEx (this, &CPLogic::OnInput3, i);
            CreateProperty(propName, "0", MM::Integer, false, pActEx);
            UpdateProperty(propName);

            // logic cell input F code
            GetCellPropertyName(i, "_Input4", propName);
            pActEx = new CPropertyActionEx (this, &CPLogic::OnInput4, i);
            CreateProperty(propName, "0", MM::Integer, false, pActEx);
            UpdateProperty(propName);

         }

         for (long i=PLOGIC_FRONTPANEL_START_ADDRESS; i<=PLOGIC_BACKPLANE_END_ADDRESS; i++) {
            GetIOPropertyName(i, "_IOType", propName);
            pActEx = new CPropertyActionEx (this, &CPLogic::OnIOType, i);
            CreateProperty(propName, "0", MM::String, false, pActEx);
            AddAllowedValue(propName, g_IOTypeCode0, 0);
            AddAllowedValue(propName, g_IOTypeCode1, 1);
            AddAllowedValue(propName, g_IOTypeCode2, 2);
            UpdateProperty(propName);

            GetIOPropertyName(i, "_SourceAddress", propName);
            pActEx = new CPropertyActionEx (this, &CPLogic::OnIOSourceAddress, i);
            CreateProperty(propName, "0", MM::Integer, false, pActEx);
            UpdateProperty(propName);
         }

         // restore refresh setting
         SetProperty(g_RefreshPropValsPropertyName, refreshPropValsStr);
      }
   }
   return DEVICE_OK;
}

int CPLogic::RefreshAdvancedCellPropertyValues(long index)
{
   char propName[MM::MaxStrLength];

   // force property refresh turned on
   char refreshPropValsStr[MM::MaxStrLength];
   GetProperty(g_RefreshPropValsPropertyName, refreshPropValsStr);
   SetProperty(g_RefreshPropValsPropertyName, g_YesState);

   GetCellPropertyName(index, "_Config", propName);
   UpdateProperty(propName);
   GetCellPropertyName(index, "_Input1", propName);
   UpdateProperty(propName);
   GetCellPropertyName(index, "_Input2", propName);
   UpdateProperty(propName);
   GetCellPropertyName(index, "_Input3", propName);
   UpdateProperty(propName);
   GetCellPropertyName(index, "_Input4", propName);
   UpdateProperty(propName);

   // restore refresh property state
   SetProperty(g_RefreshPropValsPropertyName, refreshPropValsStr);

   return DEVICE_OK;
}

// does no error checking!  make sure that the position is valid beforehand
// don't bother updating things like if the user changes position from the property
int CPLogic::SetPositionDirectly(unsigned int position)
{
   ostringstream command; command.str("");
   if (position == currentPosition_)
      return DEVICE_OK;
   command << "M " << axisLetter_ << "=" << position;
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   currentPosition_ = position;
   return DEVICE_OK;
}

int CPLogic::RefreshCurrentPosition()
{
   ostringstream command; command.str("");
   unsigned int tmp;
   command << "W " << axisLetter_;
   RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterPosition2(tmp) );
   currentPosition_ = tmp;
   return DEVICE_OK;
}

int CPLogic::OnCellType(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   ostringstream command; command.str("");
   long tmp;
   string tmpstr;
   if (eAct == MM::BeforeGet) {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR ( SetPositionDirectly(index) );
      command << addressChar_ << "CCA Y?";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      bool success = 0;
      switch (tmp) {
         case 0: success = pProp->Set(g_CellTypeCode0); break;
         case 1: success = pProp->Set(g_CellTypeCode1); break;
         case 2: success = pProp->Set(g_CellTypeCode2); break;
         case 3: success = pProp->Set(g_CellTypeCode3); break;
         case 4: success = pProp->Set(g_CellTypeCode4); break;
         case 5: success = pProp->Set(g_CellTypeCode5); break;
         case 6: success = pProp->Set(g_CellTypeCode6); break;
         case 7: success = pProp->Set(g_CellTypeCode7); break;
         case 8: success = pProp->Set(g_CellTypeCode8); break;
         case 9: success = pProp->Set(g_CellTypeCode9); break;
         case 10:success = pProp->Set(g_CellTypeCode10); break;
         case 11:success = pProp->Set(g_CellTypeCode11); break;
         case 12:success = pProp->Set(g_CellTypeCode12); break;
         case 13:success = pProp->Set(g_CellTypeCode13); break;
         case 14:success = pProp->Set(g_CellTypeCode14); break;
         case 15:success = pProp->Set(g_CellTypeCode15); break;
         default: success=0;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   } else if (eAct == MM::AfterSet) {
      char propName[MM::MaxStrLength];
      GetCellPropertyName(index, "_CellType", propName);
      RETURN_ON_MM_ERROR ( GetCurrentPropertyData(propName, tmp) );
      RETURN_ON_MM_ERROR ( SetPositionDirectly(index) );
      command << addressChar_ << "CCA Y=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
      RETURN_ON_MM_ERROR ( RefreshAdvancedCellPropertyValues(index) );
   }
   return DEVICE_OK;
}

int CPLogic::OnCellConfig(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   ostringstream command; command.str("");
   long tmp;
   if (eAct == MM::BeforeGet) {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR ( SetPositionDirectly(index) );
      command << addressChar_ << "CCA Z?";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   } else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      RETURN_ON_MM_ERROR ( SetPositionDirectly(index) );
      command << addressChar_ << "CCA Z=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   }
   return DEVICE_OK;
}

int CPLogic::OnInput1(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   ostringstream command; command.str("");
   long tmp;
   if (eAct == MM::BeforeGet) {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR ( SetPositionDirectly(index) );
      command << addressChar_ << "CCB X?";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   } else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      RETURN_ON_MM_ERROR ( SetPositionDirectly(index) );
      command << addressChar_ << "CCB X=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   }
   return DEVICE_OK;
}

int CPLogic::OnInput2(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   ostringstream command; command.str("");
   long tmp;
   if (eAct == MM::BeforeGet) {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR ( SetPositionDirectly(index) );
      command << addressChar_ << "CCB Y?";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   } else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      RETURN_ON_MM_ERROR ( SetPositionDirectly(index) );
      command << addressChar_ << "CCB Y=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   }
   return DEVICE_OK;
}

int CPLogic::OnInput3(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   ostringstream command; command.str("");
   long tmp;
   if (eAct == MM::BeforeGet) {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR ( SetPositionDirectly(index) );
      command << addressChar_ << "CCB Z?";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   } else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      RETURN_ON_MM_ERROR ( SetPositionDirectly(index) );
      command << addressChar_ << "CCB Z=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   }
   return DEVICE_OK;
}

int CPLogic::OnInput4(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   ostringstream command; command.str("");
   long tmp;
   if (eAct == MM::BeforeGet) {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR ( SetPositionDirectly(index) );
      command << addressChar_ << "CCB F?";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   } else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      RETURN_ON_MM_ERROR ( SetPositionDirectly(index) );
      command << addressChar_ << "CCB F=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   }
   return DEVICE_OK;
}

int CPLogic::OnIOType(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   ostringstream command; command.str("");
   long tmp;
   if (eAct == MM::BeforeGet) {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR ( SetPositionDirectly(index) );
      command << addressChar_ << "CCA Y?";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      bool success = 0;
      switch (tmp) {
         case 0: success = pProp->Set(g_IOTypeCode0); break;
         case 1: success = pProp->Set(g_IOTypeCode1); break;
         case 2: success = pProp->Set(g_IOTypeCode2); break;
         default: success=0;
      }
      if (!success)
         return DEVICE_INVALID_PROPERTY_VALUE;
   } else if (eAct == MM::AfterSet) {
      char propName[MM::MaxStrLength];
      GetIOPropertyName(index, "_IOType", propName);
      RETURN_ON_MM_ERROR ( GetCurrentPropertyData(propName, tmp) );
      RETURN_ON_MM_ERROR ( SetPositionDirectly(index) );
      command << addressChar_ << "CCA Y=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   }
   return DEVICE_OK;
}

int CPLogic::OnIOSourceAddress(MM::PropertyBase* pProp, MM::ActionType eAct, long index)
{
   ostringstream command; command.str("");
   long tmp;
   if (eAct == MM::BeforeGet) {
      if (!refreshProps_ && initialized_)
         return DEVICE_OK;
      RETURN_ON_MM_ERROR ( SetPositionDirectly(index) );
      command << addressChar_ << "CCA Z?";
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
      RETURN_ON_MM_ERROR ( hub_->ParseAnswerAfterEquals(tmp) );
      if (!pProp->Set(tmp))
         return DEVICE_INVALID_PROPERTY_VALUE;
   } else if (eAct == MM::AfterSet) {
      pProp->Get(tmp);
      RETURN_ON_MM_ERROR ( SetPositionDirectly(index) );
      command << addressChar_ << "CCA Z=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
   }
   return DEVICE_OK;
}

int CPLogic::OnClearAllCellStates(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   if (eAct == MM::BeforeGet) {
      pProp->Set(g_IdleState);
   }
   else  if (eAct == MM::AfterSet) {
      string tmpstr;
      pProp->Get(tmpstr);
      if (tmpstr.compare(g_DoItState) == 0)
      {
         command << "! " << axisLetter_;
         RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(), ":A") );
         pProp->Set(g_DoneState);
      }
   }
   return DEVICE_OK;
}

int CPLogic::OnSetCardPreset(MM::PropertyBase* pProp, MM::ActionType eAct)
{
   ostringstream command; command.str("");
   long tmp;
   string tmpstr;
   if (eAct == MM::BeforeGet) {
      // can't do anything of real value here
      if (!initialized_)
         pProp->Set(g_PresetCodeNone);
   } else if (eAct == MM::AfterSet) {
      RETURN_ON_MM_ERROR ( GetCurrentPropertyData(g_SetCardPresetPropertyName, tmp) );
      if (tmp < 0) return DEVICE_OK;  // g_PresetCodeNone and other "signaling" preset codes are negative
      command << addressChar_ << "CCA X=" << tmp;
      RETURN_ON_MM_ERROR ( hub_->QueryCommandVerify(command.str(),":A") );
      if (useAsdiSPIMShutter_ && (tmp == 14)) {  // preset 14 affects the channel too
         SetProperty(g_SetChannelPropertyName, g_ChannelNone);
      }
   }
   return DEVICE_OK;
}

