///////////////////////////////////////////////////////////////////////////////
// FILE:          Piezosystem_dDriver.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     DeviceAdapters
//-----------------------------------------------------------------------------
// DESCRIPTION:   Piezosystem Jena device adapter.
//					dDrive is a 6 channel device which can control diffrent actuators.
//					The actuator has a small memory with the values for the amplifier(EVD).
//					There are two version of controller EDS1 without display and EDS2 with display.
//					The controller has USB(VCP) and RS232-interface.
//					ATTENTION: Extern use channel 1-6, intern use channel 0-5
//                
// AUTHOR:        Chris Belter, cbelter@piezojena.com 15/07/2013, XYStage and ZStage by Chris Belter
//                
//
// COPYRIGHT:     Piezosystem Jena, Germany, 2013
// LICENSE:       This library is free software; you can redistribute it and/or
//                modify it under the terms of the GNU Lesser General Public
//                License as published by the Free Software Foundation.
//                
//                You should have received a copy of the GNU Lesser General Public
//                License along with the source distribution; if not, write to
//                the Free Software Foundation, Inc., 59 Temple Place, Suite 330,
//                Boston, MA  02111-1307  USA
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES. 
//

//#include "stdafx.h"

#ifdef WIN32
   #include <windows.h>
   #define snprintf _snprintf 
#endif

#include "Piezosystem_dDrive.h"
#include "devicelist.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/DeviceUtils.h"
#include <string>
#include <math.h>
#include <algorithm>
#include <sstream>
#include <cstdio>
#include <locale>

//Port-Control
const char* g_Mesg_Send_term ="\r";  //CR 
const char* g_Mesg_Receive_term ="\r\n";  //CR LF

// Controller
const char* g_Controller = "PSJ_Controller";
const char* g_dDrive = "PSJ_dDrive";
const char* g_30DV50 = "PSJ_30DV50";
const char* g_PSJ_Version = "Version";
// single axis stage
const char* g_StageDeviceName = "PSJ_Stage";
const char* g_StageDeviceName1 = "PSJ_Stage1";
const char* g_StageDeviceName2 = "PSJ_Stage2";
const char* g_StageDeviceName3 = "PSJ_Stage3";
const char* g_StageDeviceName4 = "PSJ_Stage4";
const char* g_StageDeviceName5 = "PSJ_Stage5";
const char* g_StageDeviceName6 = "PSJ_Stage6";

const char* g_PSJ_Axis_Id = "PSJ_SingleAxisName";
const char* g_StageDeviceEVD50 = "PSJ_EVD_50";
const char* g_StageDeviceEVD125 = "PSJ_EVD_125";
const char* g_StageDeviceEVD300 = "PSJ_EVD_300";
// XYStage
const char* g_XYStageDeviceName = "PSJ_XYStage";
const char* g_XYStageDeviceName1 = "PSJ_XYStage1";
const char* g_XYStageDeviceName2 = "PSJ_XYStage2";
const char* g_XYStageDeviceName3 = "PSJ_XYStage3";
// Tritor
const char* g_Tritor			="PSJ_Tritor";
const char* g_Tritor1		="PSJ_Tritor1";
const char* g_Tritor2		="PSJ_Tritor2";
// Mirror
const char* g_Mirror			="PSJ_Mirror";
const char* g_Mirror1		="PSJ_Mirror1";
const char* g_Mirror2		="PSJ_Mirror2";
const char* g_Mirror3		="PSJ_Mirror3";
// Shutter
const char* g_Shutter		= "PSJ_Shutter";
const char* g_Shutter1		= "PSJ_Shutter1";
const char* g_Shutter2		= "PSJ_Shutter2";
const char* g_Shutter3		= "PSJ_Shutter3";
const char* g_Shutter4		= "PSJ_Shutter4";
const char* g_Shutter5		= "PSJ_Shutter5";
const char* g_Shutter6		= "PSJ_Shutter6";

const char* g_ShutterState		= "Shutter State";
const char* g_Open				= "open";
const char* g_Close				= "close";

const char* g_Version			= "Version (zero volt position)";
const char* g_Version1			= "edges open";
const char* g_Version2			= "edges closed";

const char* g_Device_Number_Shutter = "PSJDeviceNumberShutter";
const char* g_Device_Number_Wheel = "PSJDeviceNumberWheel";
const char* g_Shutter_Number = "PSJShutterNumber";

//Controller properties
const char* g_Actuator		= "Actuator Name";
const char* g_bright			= "display bright";
const char* g_cmdsmon		= "*SetA";			
const char* g_cmdsmoff		= "*SetA,1";

//Stage properties
const char* g_Channel		= "Channel";
const char* g_ChannelX		= "Channel X";
const char* g_ChannelY		= "Channel Y";
const char* g_ChannelZ		= "Channel Z";
const char* g_Channel_		= "Channel_";
const char* g_ChannelX_		= "Channel_x";
const char* g_ChannelY_		= "Channel_y";
const char* g_ChannelZ_		= "Channel_z";
const char* g_Status			= "Status";
const char* g_StatusX		= "Status x";
const char* g_StatusY		= "Status y";
const char* g_StatusZ		= "Status z";
const char* g_Ktemp			= "Ktemp";
const char* g_KtempX			= "Ktemp x";
const char* g_KtempY			= "Ktemp y";
const char* g_KtempZ			= "Ktemp z";
const char* g_Loop			= "Loop";
const char* g_LoopX			= "Loop x";
const char* g_LoopY			= "Loop y";
const char* g_LoopZ			= "Loop z";
const char* g_Loop_open		= "open loop";
const char* g_Loop_close	= "close loop";

const char* g_Rohm			= "Rohm";
const char* g_RohmX			= "Rohm x";
const char* g_RohmY			= "Rohm y";
const char* g_RohmZ			= "Rohm z";
const char* g_Rgver			= "Rgver";
const char* g_RgverX			= "Rgver x";
const char* g_RgverY			= "Rgver y";
const char* g_RgverZ			= "Rgver z";

const char* g_Fenable			= "actuator soft start";
const char* g_FenableX			= "actuator soft start x";
const char* g_FenableY			= "actuator soft start y";
const char* g_FenableZ			= "actuator soft start z";
const char* g_Fenable_Off		= "soft start disable";
const char* g_Fenable_On		= "soft start enable";

const char* g_Sr				= "Slew rate [V/ms]";
const char* g_SrX				= "Slew rate x [V/ms]";
const char* g_SrY				= "Slew rate y [V/ms]";
const char* g_SrZ				= "Slew rate z [V/ms]";

const char* g_Velocity			= "Velocity [mm/s]";
const char* g_Modon				= "Modulation Input";
const char* g_ModonX				= "Modulation Input x";
const char* g_ModonY				= "Modulation Input y";
const char* g_ModonZ				= "Modulation Input z";
const char* g_Modon_On			= "on";
const char* g_Modon_Off			= "off";
const char* g_Monsrc				= "Monitor output";
const char* g_MonsrcX			= "Monitor output x";
const char* g_MonsrcY			= "Monitor output y";
const char* g_MonsrcZ			= "Monitor output z";
const char* g_Monsrc_0			= "position in closed loop";
const char* g_Monsrc_1			= "command value";
const char* g_Monsrc_2			= "controller output voltage";
const char* g_Monsrc_3			= "closed loop deviation incl. sign";
const char* g_Monsrc_4			= "absolute closed loop deviation";
const char* g_Monsrc_5			= "actuator voltage";
const char* g_Monsrc_6			= "position in open loop";

const char* g_Limit_V_Min		= "Limit Voltage min [V]";
const char* g_Limit_V_MinX		= "Limit Voltage min x [V]";
const char* g_Limit_V_MinY		= "Limit Voltage min y [V]";
const char* g_Limit_V_MinZ		= "Limit Voltage min z [V]";
const char* g_Limit_V_Max		= "Limit Voltage max [V]";
const char* g_Limit_V_MaxX		= "Limit Voltage max x [V]";
const char* g_Limit_V_MaxY		= "Limit Voltage max y [V]";
const char* g_Limit_V_MaxZ		= "Limit Voltage max z [V]";
const char* g_Limit_Um_Min		= "Limit um min [microns]";
const char* g_Limit_Um_MinX	= "Limit um min x [microns]";
const char* g_Limit_Um_MinY	= "Limit um min y [microns]";
const char* g_Limit_Um_MinZ	= "Limit um min z [microns]";
const char* g_Limit_Um_Max		= "Limit um max [microns]";
const char* g_Limit_Um_MaxX	= "Limit um max x [microns]";
const char* g_Limit_Um_MaxY	= "Limit um max y [microns]";
const char* g_Limit_Um_MaxZ	= "Limit um max z [microns]";
const char* g_Voltage			= "Voltage [V]";
const char* g_VoltageX			= "Voltage x [V]";
const char* g_VoltageY			= "Voltage y [V]";
const char* g_VoltageZ			= "Voltage z [V]";
const char* g_Position			= "Position [microns]";
const char* g_PositionX			= "Position x [microns]";
const char* g_PositionY			= "Position y [microns]";
const char* g_PositionZ			= "Position z [microns]";
const char* g_PID_P				= "PID kp";
const char* g_PID_I				= "PID ki";
const char* g_PID_D				= "PID kd";
const char* g_PID_PX			= "PID kp x";
const char* g_PID_IX			= "PID ki x";
const char* g_PID_DX			= "PID kd x";
const char* g_PID_PY			= "PID kp y";
const char* g_PID_IY			= "PID ki y";
const char* g_PID_DY			= "PID kd y";
const char* g_PID_PZ			= "PID kp z";
const char* g_PID_IZ			= "PID ki z";
const char* g_PID_DZ			= "PID kd z";

const char* g_Notch				= "notch filter ";
const char* g_NotchX			= "notch filter x";
const char* g_NotchY			= "notch filter y";
const char* g_NotchZ			= "notch filter z";
const char* g_Notch_On			= "on";
const char* g_Notch_Off			= "off";
const char* g_Notch_Freq		= "notch filter freqency [Hz]";
const char* g_Notch_FreqX		= "notch filter freqency x [Hz]";
const char* g_Notch_FreqY		= "notch filter freqency y [Hz]";
const char* g_Notch_FreqZ		= "notch filter freqency z [Hz]";
const char* g_Notch_Band		= "notch bandwidth [Hz]";
const char* g_Notch_BandX		= "notch bandwidth x [Hz]";
const char* g_Notch_BandY		= "notch bandwidth y [Hz]";
const char* g_Notch_BandZ		= "notch bandwidth z [Hz]";

const char* g_Lowpass			= "low pass filter";
const char* g_LowpassX			= "low pass filter x";
const char* g_LowpassY			= "low pass filter y";
const char* g_LowpassZ			= "low pass filter z";
const char* g_Lowpass_On		= "on";
const char* g_Lowpass_Off		= "off";
const char* g_Lowpass_Freq		= "low pass filter freqency [Hz]";
const char* g_Lowpass_FreqX		= "low pass filter freqency x [Hz]";
const char* g_Lowpass_FreqY		= "low pass filter freqency y [Hz]";
const char* g_Lowpass_FreqZ		= "low pass filter freqency z [Hz]";

const char* g_Generator					= "generator";
const char* g_GeneratorX				= "generator x";
const char* g_GeneratorY				= "generator y";
const char* g_GeneratorZ				= "generator z";
const char* g_Generator_Off				= "off";
const char* g_Generator_Sine			= "sine";
const char* g_Generator_Tri				= "triangle";
const char* g_Generator_Rect			= "rectangle";
const char* g_Generator_Noise			= "noise";
const char* g_Generator_Sweep			= "sweep";

const char* g_Generator_Sine_Amp		= "sine amplitude [%]";
const char* g_Generator_Sine_AmpX		= "sine amplitude x [%]";
const char* g_Generator_Sine_AmpY		= "sine amplitude y [%]";
const char* g_Generator_Sine_AmpZ		= "sine amplitude z [%]";
const char* g_Generator_Sine_Offset		= "sine offset [%]";
const char* g_Generator_Sine_OffsetX	= "sine offset x [%]";
const char* g_Generator_Sine_OffsetY	= "sine offset y [%]";
const char* g_Generator_Sine_OffsetZ	= "sine offset z [%]";
const char* g_Generator_Sine_Freq		= "sine freqency [Hz]";
const char* g_Generator_Sine_FreqX		= "sine freqency x [Hz]";
const char* g_Generator_Sine_FreqY		= "sine freqency y [Hz]";
const char* g_Generator_Sine_FreqZ		= "sine freqency z [Hz]";

const char* g_Generator_Tri_Amp			= "triangle amplitude [%]";
const char* g_Generator_Tri_AmpX		= "triangle amplitude x [%]";
const char* g_Generator_Tri_AmpY		= "triangle amplitude y [%]";
const char* g_Generator_Tri_AmpZ		= "triangle amplitude z [%]";
const char* g_Generator_Tri_Offset		= "triangle offset [%]";
const char* g_Generator_Tri_OffsetX		= "triangle offset x [%]";
const char* g_Generator_Tri_OffsetY		= "triangle offset y [%]";
const char* g_Generator_Tri_OffsetZ		= "triangle offset z [%]";
const char* g_Generator_Tri_Freq		= "triangle freqency [Hz]";
const char* g_Generator_Tri_FreqX		= "triangle freqency x [Hz]";
const char* g_Generator_Tri_FreqY		= "triangle freqency y [Hz]";
const char* g_Generator_Tri_FreqZ		= "triangle freqency z [Hz]";
const char* g_Generator_Tri_Sym			= "triangle symetry [%]";
const char* g_Generator_Tri_SymX		= "triangle symetry x [%]";
const char* g_Generator_Tri_SymY		= "triangle symetry y [%]";
const char* g_Generator_Tri_SymZ		= "triangle symetry z [%]";

const char* g_Generator_Rect_Amp		= "rectangle amplitude [%]";
const char* g_Generator_Rect_AmpX		= "rectangle amplitude x [%]";
const char* g_Generator_Rect_AmpY		= "rectangle amplitude y [%]";
const char* g_Generator_Rect_AmpZ		= "rectangle amplitude z [%]";
const char* g_Generator_Rect_Offset		= "rectangle offset [%]";
const char* g_Generator_Rect_OffsetX	= "rectangle offset x [%]";
const char* g_Generator_Rect_OffsetY	= "rectangle offset y [%]";
const char* g_Generator_Rect_OffsetZ	= "rectangle offset z [%]";
const char* g_Generator_Rect_Freq		= "rectangle freqency [Hz]";
const char* g_Generator_Rect_FreqX		= "rectangle freqency x [Hz]";
const char* g_Generator_Rect_FreqY		= "rectangle freqency y [Hz]";
const char* g_Generator_Rect_FreqZ		= "rectangle freqency z [Hz]";
const char* g_Generator_Rect_Sym			= "rectangle symetry [%]";
const char* g_Generator_Rect_SymX		= "rectangle symetry x [%]";
const char* g_Generator_Rect_SymY		= "rectangle symetry y [%]";
const char* g_Generator_Rect_SymZ		= "rectangle symetry z [%]";
	
const char* g_Generator_Noise_Amp		= "noise amplitude [%]";
const char* g_Generator_Noise_AmpX		= "noise amplitude x [%]";
const char* g_Generator_Noise_AmpY		= "noise amplitude y [%]";
const char* g_Generator_Noise_AmpZ		= "noise amplitude z [%]";
const char* g_Generator_Noise_Offset	= "noise offset [%]";
const char* g_Generator_Noise_OffsetX	= "noise offset x [%]";
const char* g_Generator_Noise_OffsetY	= "noise offset y [%]";
const char* g_Generator_Noise_OffsetZ	= "noise offset z [%]";

const char* g_Generator_Sweep_Amp		= "sweep amplitude [%]";
const char* g_Generator_Sweep_AmpX		= "sweep amplitude x [%]";
const char* g_Generator_Sweep_AmpY		= "sweep amplitude y [%]";
const char* g_Generator_Sweep_AmpZ		= "sweep amplitude z [%]";
const char* g_Generator_Sweep_Offset	= "sweep offset [%]";
const char* g_Generator_Sweep_OffsetX	= "sweep offset x [%]";
const char* g_Generator_Sweep_OffsetY	= "sweep offset y [%]";
const char* g_Generator_Sweep_OffsetZ	= "sweep offset z [%]";
const char* g_Generator_Sweep_Time		= "sweep time [s]";
const char* g_Generator_Sweep_TimeX		= "sweep time x [s]";
const char* g_Generator_Sweep_TimeY		= "sweep time y [s]";
const char* g_Generator_Sweep_TimeZ		= "sweep time z [s]";

const char* g_Scan_Type			= "scan type";
const char* g_Scan_TypeX		= "scan type x";
const char* g_Scan_TypeY		= "scan type y";
const char* g_Scan_TypeZ		= "scan type z";
const char* g_Scan_Type_Off		= "scan function off";
const char* g_Scan_Type_Sine	= "sine scan";
const char* g_Scan_Type_Tri		= "triangle scan";
const char* g_Scan_Start		= "scan: start scan";
const char* g_Scan_StartX		= "scan: start scan x";
const char* g_Scan_StartY		= "scan: start scan y";
const char* g_Scan_StartZ		= "scan: start scan z";
const char* g_Scan_Off			= "off";
const char* g_Scan_Starting		= "start scan";

const char* g_Trigger_Start		= "trigger start [%]";
const char* g_Trigger_StartX	= "trigger start x [%]";
const char* g_Trigger_StartY	= "trigger start y [%]";
const char* g_Trigger_StartZ	= "trigger start z [%]";
const char* g_Trigger_End		= "trigger end [%]";
const char* g_Trigger_EndX		= "trigger end x [%]";
const char* g_Trigger_EndY		= "trigger end y [%]";
const char* g_Trigger_EndZ		= "trigger end z [%]";
const char* g_Trigger_Interval	= "trigger intervals [microns]";
const char* g_Trigger_IntervalX	= "trigger intervals x [microns]";
const char* g_Trigger_IntervalY	= "trigger intervals y [microns]";
const char* g_Trigger_IntervalZ	= "trigger intervals z [microns]";
const char* g_Trigger_Time		= "trigger duration [n*20us]";
const char* g_Trigger_TimeX		= "trigger duration x [n*20us]";
const char* g_Trigger_TimeY		= "trigger duration y [n*20us]";
const char* g_Trigger_TimeZ		= "trigger duration z [n*20us]";
const char* g_Trigger_Generator = "trigger generation edge";
const char* g_Trigger_GeneratorX= "trigger generation edge x";
const char* g_Trigger_GeneratorY= "trigger generation edge y";
const char* g_Trigger_GeneratorZ= "trigger generation edge z";
const char* g_Trigger_Off		= "trigger off";
const char* g_Trigger_Rising	= "trigger at rising edge";
const char* g_Trigger_Falling	= "trigger at falling edge";
const char* g_Trigger_Both		= "trigger at both edges";

using namespace std;

///////////////////////////////////////////////////////////////////////////////
// Exported MMDevice API
///////////////////////////////////////////////////////////////////////////////
MODULE_API void InitializeModuleData()
{		
	RegisterDevice(g_dDrive, MM::HubDevice, "Piezosystem Jena dDrive");
	RegisterDevice(g_StageDeviceName1, MM::StageDevice, "Single Axis Stage Ch1");
	RegisterDevice(g_StageDeviceName2, MM::StageDevice, "Single Axis Stage Ch2");
	RegisterDevice(g_StageDeviceName3, MM::StageDevice, "Single Axis Stage Ch3");
	RegisterDevice(g_StageDeviceName4, MM::StageDevice, "Single Axis Stage Ch4");
	RegisterDevice(g_StageDeviceName5, MM::StageDevice, "Single Axis Stage Ch5");
	RegisterDevice(g_StageDeviceName6, MM::StageDevice, "Single Axis Stage Ch6");
	RegisterDevice(g_XYStageDeviceName1, MM::XYStageDevice, "Two Axis XY Stage1");
	RegisterDevice(g_XYStageDeviceName2, MM::XYStageDevice, "Two Axis XY Stage2");
	RegisterDevice(g_XYStageDeviceName3, MM::XYStageDevice, "Two Axis XY Stage3");
	RegisterDevice(g_Shutter1, MM::ShutterDevice, "PSJ Shutter");
	RegisterDevice(g_Shutter2, MM::ShutterDevice, "PSJ Shutter");
	RegisterDevice(g_Shutter3, MM::ShutterDevice, "PSJ Shutter");
	RegisterDevice(g_Shutter4, MM::ShutterDevice, "PSJ Shutter");
	RegisterDevice(g_Shutter5, MM::ShutterDevice, "PSJ Shutter");
	RegisterDevice(g_Shutter6, MM::ShutterDevice, "PSJ Shutter");
	RegisterDevice(g_Tritor1, MM::GenericDevice, "PSJ Tritor");
	RegisterDevice(g_Tritor2, MM::GenericDevice, "PSJ Tritor");
}             

MODULE_API MM::Device* CreateDevice(const char* deviceName)                  
{	
	if (deviceName == 0)      return 0;		
	if (strcmp(deviceName, g_dDrive) == 0){ 		
		return new Hub(g_dDrive);}	
	else if (strcmp(deviceName, g_StageDeviceName1) == 0){		
		return new Stage(0);	}
	else if (strcmp(deviceName, g_StageDeviceName2) == 0){		
		return new Stage(1);	}
	else if (strcmp(deviceName, g_StageDeviceName3) == 0){		
		return new Stage(2);	}
	else if (strcmp(deviceName, g_StageDeviceName4) == 0){		
		return new Stage(3);	}
	else if (strcmp(deviceName, g_StageDeviceName5) == 0){			
		return new Stage(4);	}
	else if (strcmp(deviceName, g_StageDeviceName6) == 0){		
		return new Stage(5);	}
	else if (strcmp(deviceName, g_XYStageDeviceName) == 0){		
		return new XYStage();	} 
	else if (strcmp(deviceName, g_XYStageDeviceName1) == 0){
		int chx=0;		
		int chy=1;		
		if(!devicelist_.empty()){
			for(std::vector<PSJdevice>::iterator it2 = devicelist_.begin();it2 != devicelist_.end();++it2){
				if(it2->devname_.compare(deviceName)==0){
					chx =it2->channel_.at(0);
					chy =it2->channel_.at(1);
					break;
				}
			}
		}				 
		return new XYStage(0,chx,chy);	} 
	else if (strcmp(deviceName, g_XYStageDeviceName2) == 0){
		int chx=0;		
		int chy=1;		
		if(!devicelist_.empty()){
			for(std::vector<PSJdevice>::iterator it2 = devicelist_.begin();it2 != devicelist_.end();++it2){
				if(it2->devname_.compare(deviceName)==0){
					chx =it2->channel_.at(0);
					chy =it2->channel_.at(1);
					break;
				}
			}
		}				
		return new XYStage(1,chx,chy);	} 
	else if (strcmp(deviceName, g_XYStageDeviceName3) == 0){
		int chx=0;		
		int chy=1;		
		if(!devicelist_.empty()){			
			for(std::vector<PSJdevice>::iterator it2 = devicelist_.begin();it2 != devicelist_.end();++it2){
				if(it2->devname_.compare(deviceName)==0){
					chx =it2->channel_.at(0);
					chy =it2->channel_.at(1);
					break;
				}
			}
		}				
		return new XYStage(2,chx,chy); 	} 
	else if (strcmp(deviceName, g_Tritor) == 0){
		int chx=0;		
		int chy=1;	
		int chz=2;
		if(!devicelist_.empty()){			
			for(std::vector<PSJdevice>::iterator it2 = devicelist_.begin();it2 != devicelist_.end();++it2){
				if(it2->devname_.compare(deviceName)==0){
					chx =it2->channel_.at(0);
					chy =it2->channel_.at(1);
					chz =it2->channel_.at(2);
					break;
				}
			}
		}	
		return new Tritor(1,g_Tritor); } 	 
	else if (strcmp(deviceName, g_Tritor1) == 0){
		int chx=0;		
		int chy=1;	
		int chz=2;
		if(!devicelist_.empty()){
			for(std::vector<PSJdevice>::iterator it2 = devicelist_.begin();it2 != devicelist_.end();++it2){
				if(it2->devname_.compare(deviceName)==0){
					chx =it2->channel_.at(0);
					chy =it2->channel_.at(1);
					chz =it2->channel_.at(2);
					break;
				}
			}
		}		
		return new Tritor(1,g_Tritor,chx,chy,chz); } 		 
	else if (strcmp(deviceName, g_Tritor2) == 0){
		int chx=0;		
		int chy=1;	
		int chz=2;
		if(!devicelist_.empty()){
			for(std::vector<PSJdevice>::iterator it2 = devicelist_.begin();it2 != devicelist_.end();++it2){
				if(it2->devname_.compare(deviceName)==0){
					chx =it2->channel_.at(0);
					chy =it2->channel_.at(1);
					chz =it2->channel_.at(2);
					break;
				}
			}
		}				
		return new Tritor(2,g_Tritor,chx,chy,chz); }
	else if (strcmp(deviceName, g_Shutter) == 0 ){		
		return new Shutter(0,g_Shutter);   }
	else if (strcmp(deviceName, g_Shutter1) == 0 ){		
		return new Shutter(0,g_Shutter);   }
	else if (strcmp(deviceName, g_Shutter2) == 0 ){		
		return new Shutter(1,g_Shutter);   }
	else if (strcmp(deviceName, g_Shutter3) == 0 ){		
		return new Shutter(2,g_Shutter);   }
	else if (strcmp(deviceName, g_Shutter4) == 0 ){		
		return new Shutter(3,g_Shutter);   }
	else if (strcmp(deviceName, g_Shutter5) == 0 ){		
		return new Shutter(4,g_Shutter);   }
	else if (strcmp(deviceName, g_Shutter6) == 0 ){		
		return new Shutter(5,g_Shutter);   }
	//not supported at the moment
	else if (strcmp(deviceName, g_Mirror1) == 0){
		return new Mirror(1,1,g_Mirror); } 
	else if (strcmp(deviceName, g_Mirror2) == 0){
		return new Mirror(2,2,g_Mirror); } 
	else if (strcmp(deviceName, g_Mirror3) == 0){
		return new Mirror(3,3,g_Mirror); } 
   return 0;
}

MODULE_API void DeleteDevice(MM::Device* pDevice)
{
   delete pDevice;
}

/*
 * Global Utility function for communication with the controller
 */
int clearPort(MM::Device& device, MM::Core& core, const char* port)
{
   // Clear contents of serial port
   const unsigned int bufSize = 255;
   unsigned char clear[bufSize];
   unsigned long read = bufSize;
   int ret;
   while (read == bufSize)
   {
      ret = core.ReadFromSerial(&device, port, clear, bufSize, read);
      if (ret != DEVICE_OK)
         return ret;
   }
   return DEVICE_OK;
}

void splitString(char* string, const char* delimiter,char** dest){
  char * pch; 
  pch = strtok (string,delimiter); 
  int i=0;	
  while (pch != NULL)
  {    
		dest[i]=pch;
		i++;
		pch = strtok (NULL, delimiter);
  }  
}

EVDBase::EVDBase(MM::Device *device):
initialized_(false),
device_(device),
core_(0)
{
}
EVDBase::~EVDBase()
{
}

int EVDBase::SendCommand(const char* cmd,std::string &result){
	int ret;
	//ret =PurgeComPort(port_.c_str());
	//CDeviceUtils::SleepMs(2);
	//Send Command	
	core_->LogMessage(device_, cmd, true);
	ret = core_->SetSerialCommand(device_, port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;

    const size_t BUFSIZE = 2048;
    char buf[BUFSIZE] = {'\0'};
	ret = core_->GetSerialAnswer(device_, port_.c_str(), BUFSIZE, buf, g_Mesg_Receive_term);
	if (ret != DEVICE_OK) 
      return ret;
    result = buf;

	return DEVICE_OK;
}
int EVDBase::SendServiceCommand(const char* cmd,std::string& result){	
	int ret;	
	ret = core_->SetSerialCommand(device_, port_.c_str(), g_cmdsmon, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;		
	ret = SendCommand(cmd, result);		
	ret = core_->SetSerialCommand(device_, port_.c_str(), g_cmdsmoff, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;
	return DEVICE_OK;
}
int EVDBase::GetCommandValue(const char* c,int channel,double& d){
	core_->LogMessage(device_, "Get command value double", true);

	char str[50]="";
	sprintf(str,"%s,%d",c,channel);	
	const char* cmd = str; 	
    int ret;
	std::string result;
	ret = SendCommand(cmd,result);	
	if (ret != DEVICE_OK)
		return ret;	
	if (result.length() < 1)
      return ERR_NO_ANSWER;
	//TODO: Error or no result
	core_->LogMessage(device_, result.c_str(), true);
	char* dest[50];
	splitString((char*)result.c_str()," ,\n",dest);
	std::string type;
	type=dest[0];
	if(type==c){			
		d=atof(dest[2]);
	}else{
		core_->LogMessage(device_, "Wrong Result", true);
		core_->LogMessage(device_, dest[0], true);
		d=0.0;
		ret = core_->PurgeSerial(device_, port_.c_str());
		CDeviceUtils::SleepMs(10);
	}		
	return DEVICE_OK;
}
int EVDBase::GetCommandValue(const char* c,int channel,int& i){
	core_->LogMessage(device_, "Get command value integer", true);
	char cmd[50]="";
	sprintf(cmd,"%s,%d",c,channel);	
    int ret;
	std::string result;
	std::string type;
	ret = SendCommand(cmd,result);
	if (ret != DEVICE_OK)
		return ret;	
	if (result.length() < 1)
      return ERR_NO_ANSWER;
	core_->LogMessage(device_, result.c_str(), true);
	char* dest[50];
	splitString((char*)result.c_str()," ,\n",dest);
	type=dest[0];
	if(type==c){
		i=atoi(dest[2]);
	}else{
		core_->LogMessage(device_, "Wrong Result", true);
		core_->LogMessage(device_, dest[0], true);
		i=0;
		ret = core_->PurgeSerial(device_, port_.c_str());
		CDeviceUtils::SleepMs(10);
	}
	return ret;
}
int EVDBase::SetCommandValue(const char* c,int channel,double fkt){
	core_->LogMessage(device_, "Set command value double", true);
	char str[50]="";
	sprintf(str,"%s,%d,%lf",c,channel,fkt);	
	const char* cmd = str; 
	core_->LogMessage(device_, cmd, true);
    int ret;
    ret = core_->SetSerialCommand(device_, port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	//Normally no answer
	//std::string result;
    //LogMessage ("SetCommandDResult");
	//ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);
	//if (ret != DEVICE_OK){ 
	//	LogMessage (result,true);	
    //  return ret;
	//}
	return DEVICE_OK;
}
int EVDBase::SetCommandValue(const char* c,int channel,int fkt){
	core_->LogMessage(device_, "Set command value integer", true);
	char str[50]="";
	sprintf(str,"%s,%d,%d",c,channel,fkt);	
	const char* cmd = str; 
	core_->LogMessage(device_, cmd, true);
    int ret;
    ret = core_->SetSerialCommand(device_, port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	//Normally no answer
	/*std::string result;
    LogMessage ("SetCommandIResult");
	ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);  
	if (ret != DEVICE_OK){
		if(ret!=14){//Term_Timeout		  
			LogMessage (result);
			return ret;
		}       
	}*/
	return DEVICE_OK;
}
int EVDBase::GetStatus(int& stat, EVD* struc){
	core_->LogMessage(device_, "GetStatus", true);
	//int stat;
	char s[30];
	sprintf(s,"stat,%d",struc->channel_);	
	const char* cmd = s; 
	core_->LogMessage(device_, cmd, true);
    int ret;
	std::string str;
	std::string type;

	ret = SendCommand(cmd,str);    
	core_->LogMessage(device_, str.c_str(), true);
	char* dest[20];
	splitString((char*)str.c_str()," ,\n",dest);
	//If there is a Modul, look of the stat
	type=dest[0];
	std::size_t found;
	found=type.find("unit");
	if(found!=std::string::npos){
		core_->LogMessage(device_, "No Modul found", true);
		stat=0;
		return ERR_MODULE_NOT_FOUND;
	}
	found=type.find("stat");
	if(found!=std::string::npos){
		core_->LogMessage(device_, "Modul found", true);
		stat=atoi(dest[2]);
		struc->stat_=stat;
		//if Bit4 is set, close loop
		struc->loop_=((stat &CLOSE_LOOP)==CLOSE_LOOP)? true:false;
		//if Bit12 is set
		struc->notchon_=((stat &NOTCH_FILTER_ON)==NOTCH_FILTER_ON)? true:false;
		//if Bit13 is set
		struc->lpon_=((stat &LOW_PASS_FILTER_ON)==LOW_PASS_FILTER_ON)? true:false;
	}else{
		core_->LogMessage(device_, "ERROR ", true);
		core_->LogMessage(device_, dest[0], true);
		stat=-1;
		//return ERR_MODULE_NOT_FOUND;
	}
	return DEVICE_OK;
}
int EVDBase::GetLimitsValues(EVD *struc){	
	int ret;	
	ret = core_->SetSerialCommand(device_, port_.c_str(), g_cmdsmon, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;	
	//min_um_
	char s[20];
	std::string result;
	sprintf(s,"rdac,%d,197",struc->channel_);	
	const char* cmd = s; 
	ret = SendCommand(cmd, result);		
	char* dest[20];
	splitString((char*)result.c_str()," ,\n",dest);
	struc->min_um_=atof(dest[2]);

	//max_um_	
	sprintf(s,"rdac,%d,198",struc->channel_);	
	cmd = s; 	
	ret = SendCommand(cmd, result);		
	splitString((char*)result.c_str()," ,\n",dest);
	struc->max_um_=atof(dest[2]);

	//min_V_	
	sprintf(s,"rdac,%d,199",struc->channel_);	
	cmd = s; 
	ret = SendCommand(cmd, result);		
	splitString((char*)result.c_str()," ,\n",dest);
	struc->min_V_=atof(dest[2]);

	//max_V_	
	sprintf(s,"rdac,%d,200",struc->channel_);	
	cmd = s;
	ret = SendCommand(cmd, result);	
	splitString((char*)result.c_str()," ,\n",dest);
	struc->max_V_=atof(dest[2]);		
	
	ret = core_->SetSerialCommand(device_, port_.c_str(), g_cmdsmoff, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;
	return DEVICE_OK;
}
int EVDBase::GetActuatorName(char* id,int ch){
	core_->LogMessage(device_, "GetActuatorName", true);
	std::string result;
	char s[20];
	sprintf(s,"acdescr,%d",ch);	
	const char* cmd = s;
	SendServiceCommand(cmd,result);
	core_->LogMessage(device_, result.c_str(), true);
	char* dest[30];
	splitString((char*)result.c_str()," ,\n",dest);	
	sprintf(id,"%s", dest[2]);	
	core_->LogMessage(device_, id, true);
	return DEVICE_OK;
}
int EVDBase::GetPos(double& pos, EVD* struc){
	core_->LogMessage(device_, "EVDBase GetPos", true);
	int ret;
	int stat;
	ret = GetStatus(stat,struc);
	if(struc->loop_){
		GetCommandValue("mess",struc->channel_,pos);
		struc->pos_=pos;
		struc->voltage_=(struc->max_V_-struc->min_V_)*(struc->pos_-struc->min_um_)/(struc->max_um_-struc->min_um_)+struc->min_V_;
	}else{
		GetCommandValue("mess",struc->channel_,struc->voltage_);
		struc->pos_=(struc->max_um_-struc->min_um_)*(struc->voltage_-struc->min_V_)/(struc->max_V_-struc->min_V_)+struc->min_um_;
		pos=struc->pos_;
	}
	return DEVICE_OK;
}
int EVDBase::SetPos(double pos, EVD* struc){
	core_->LogMessage(device_, "EVDBase SetPos", true);
	int ret;
	int stat;
	ret = GetStatus(stat,struc);
	if(struc->loop_){
		struc->pos_=pos;
		ret  =SetCommandValue("set",struc->channel_,pos);
	}else{
		struc->pos_=pos;
		struc->voltage_=(struc->max_V_-struc->min_V_)*(pos-struc->min_um_)/(struc->max_um_-struc->min_um_)+struc->min_V_;
		ret = SetCommandValue("set",struc->channel_,struc->voltage_);
	}
	if (ret != DEVICE_OK)
      return ret;
	return DEVICE_OK;
}
int EVDBase::GetLoop(bool& loop,int ch){
	core_->LogMessage(device_, "GetLoop", true);
    int ret;
	int stat;    
	ret = GetCommandValue("stat",ch,stat);
	if (ret != DEVICE_OK)
      return ret;
	loop=((stat &CLOSE_LOOP)==CLOSE_LOOP)? true:false;	
	return DEVICE_OK;
}
int EVDBase::SetLoop(bool loop,int ch){
	core_->LogMessage(device_, "SetLoop", true);
	int i=(loop)?1:0;
	int ret = SetCommandValue("cl",ch,i);	
	if (ret != DEVICE_OK)	  
      return ret;	
	//CDeviceUtils::SleepMs(300);	
	return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// PSJ Hub  EDS1/EDS2
///////////////////////////////////////////////////////////////////////////////
Hub::Hub(const char* devicename) :
   name_(devicename),
   transmissionDelay_(10),
   initialized_(false)
{
	LogMessage ("PSJ new Hub");
   InitializeDefaultErrorMessages();

   // custom error messages:
   SetErrorText(ERR_NO_ANSWER, "No answer from the controller.  Is it connected?");
   
   // pre-initialization properties
   // Port:
   CPropertyAction* pAct = new CPropertyAction(this, &Hub::OnPort);
   CreateProperty(MM::g_Keyword_Port, "Undefined", MM::String, false, pAct, true);
}

Hub::~Hub()
{
   Shutdown();
}

void Hub::GetName(char* name) const
{
	if(!name_){	
		CDeviceUtils::CopyLimitedString(name, g_Controller);
	}else{
		CDeviceUtils::CopyLimitedString(name,name_);
	}
}

bool Hub::Busy()
{
   return false;
}

int Hub::Initialize()
{
	LogMessage ("PSJ Hub Init");
   clearPort(*this, *GetCoreCallback(), port_.c_str());

   // Name
   //int ret = CreateProperty(MM::g_Keyword_Name, g_Controller, MM::String, true);
   int ret = CreateProperty(MM::g_Keyword_Name, g_dDrive, MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Description
   ret = CreateProperty(MM::g_Keyword_Description, "6 channel controller", MM::String, true);
   if (DEVICE_OK != ret)
      return ret;

   // Version of the controller:
   std::string result="";
   ret = GetVersion(result);
   if( DEVICE_OK != ret)
      return ret;

   CPropertyAction* pAct = new CPropertyAction (this, &Hub::OnBright);
   CreateProperty("bright", "8", MM::Integer, false, pAct);
   SetPropertyLimits("bright", 0, 10);

   // Create read-only property with version info
   ret = CreateProperty(g_PSJ_Version, result.c_str(), MM::String, true);
   if (ret != DEVICE_OK) 
      return ret;

   // Get description of attached modules
   //ret = DetectInstalledDevices();   
  
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;

   return DEVICE_OK;
}

bool Hub::SupportsDeviceDetection(void)
{
   return true;
}

MM::DeviceDetectionStatus Hub::DetectDevice(void)
{
   // all conditions must be satisfied...
   MM::DeviceDetectionStatus result = MM::Misconfigured;

   try
   {
      std::string transformed = port_;
      for( std::string::iterator its = transformed.begin(); its != transformed.end(); ++its)
      {
         *its = (char)tolower(*its);
      }
      if( 0< transformed.length() &&  0 != transformed.compare("undefined")  && 0 != transformed.compare("unknown") )
      {
         // the port property seems correct, so give it a try
         result = MM::CanNotCommunicate;
         // device specific default communication parameters
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_BaudRate, "115200" );
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_DataBits, "8");
		 GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_StopBits, "1");
		 GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Parity, "None");  
		 GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_Handshaking, "Software" );  
         GetCoreCallback()->SetDeviceProperty(port_.c_str(), MM::g_Keyword_AnswerTimeout, "200.0");
         MM::Device* pS = GetCoreCallback()->GetDevice(this, port_.c_str());
         pS->Initialize();
		 std::string v;
         int qvStatus = this->GetVersion(v);
         LogMessage(std::string("version : ")+v, true);
         if( DEVICE_OK != qvStatus )
         {
            LogMessageCode(qvStatus,true);
         }
         else
         {
            // to succeed must reach here....
            result = MM::CanCommunicate;
         }
         pS->Shutdown();         
      }
   }
   catch(...)
   {
      LogMessage("Exception in DetectDevice!",false);
   }
   return result;
}

int Hub::GetVersion(std::string& version)
{
   int returnStatus = DEVICE_OK;
   
   PurgeComPort(port_.c_str());

   // Version of the controller:
   const char* cm = "";		//Get Version
   returnStatus = SendSerialCommand(port_.c_str(), cm, g_Mesg_Send_term);
   if (returnStatus != DEVICE_OK) 
      return returnStatus;

   // Read out result
   returnStatus = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, version);  //"DSM V6.000"
   if (returnStatus != DEVICE_OK) 
      return returnStatus;
   if (version.length() < 2) {
      // if we get no answer, try other port
      LogMessage("There is no device. Try other Port",true);   
	  // no answer, 
      return ERR_NO_ANSWER;
   }   
   return returnStatus;
}

int Hub::Shutdown()
{
   if (initialized_)
      initialized_ = false;

   return DEVICE_OK;
}

int Hub::GetBright(int &b){
	b=0;
	const char* cmd = "bright";
	int ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	std::string result;
	std::string type;
	ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);
	if (ret != DEVICE_OK) 
      return ret;
	char* dest[50];
	splitString((char*)result.c_str()," ,\n",dest);
	type=dest[0];
	if(type=="bright"){
		b=atoi(dest[1]);
	}
	return DEVICE_OK;
}
int Hub::SetBright(int b){
	char str[20]="";
	sprintf(str,"bright,%d",b);	
	const char* cmd = str;
	int ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK){	  
      return ret;
	}	
	return DEVICE_OK;
}

int Hub::DetectInstalledDevices(){
	LogMessage ("DetectInstalledDevices",true);
	discoverableDevices_.clear();
    inventoryDeviceAddresses_.clear(); 
	inventoryDeviceName_.clear();
	inventoryDeviceSerNr_.clear();
	devicelist_.clear();

	std::string result="";
	int c=0;	
	int s=0;
	// Look for attached modules
	const char* cmd = "stat";
	int ret;	
	ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
		return ret;			
	std::string type;
	//Search all device
	for(int i=0;i<6;i++){
		result = "";
		ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);
		if (ret != DEVICE_OK) 
			return ret;
		if (result.length() < 1)
			return ERR_NO_ANSWER;
		LogMessage (result.c_str(),true);
		char* dest[20];
		splitString((char*)result.c_str()," ,\n",dest);
		//If there is a Modul, look of the stat
		type=dest[0];
		std::size_t found;
		found=type.find("unit");
		if(found!=std::string::npos){
			LogMessage ("No Modul found",true);
			continue;
			//return ERR_MODULE_NOT_FOUND;
		}
		found=type.find("stat");
		if(found!=std::string::npos){
			LogMessage ("Modul found",true);
			c=atoi(dest[1]); //=i
			s=atoi(dest[2]); //=statusbits
			if(s>0)	  //only if there is an actuator
				inventoryDeviceAddresses_.push_back((const int)i );			
		}else{
			//that should never happen
			LogMessage ("ERROR ",true);
			LogMessage (dest[0],true);		
			return ERR_MODULE_NOT_FOUND;
		}		
	}
	
	for(std::vector<int>::iterator it = inventoryDeviceAddresses_.begin();it != inventoryDeviceAddresses_.end();++it){		
		int l=*it;		
		PSJdevice* dev=new PSJdevice();
		char name[20];
		dev->channel_.push_back(l);		
		GetActuatorName(l,name);			
		dev->name_=name;		
		char* dest[20];
		splitString(name," 0123456789",dest);	
		dev->serie_=ConvertName(dest[0]);
		dev->comparename_=ConvertName(dev->name_);
		//Get serial number
		GetSerialNumberActuator(l,name);		
		dev->sn_=name;					
		bool finddev=false;
		//compare device with the device in list 
		for(std::vector<PSJdevice>::iterator it2 = devicelist_.begin();it2 != devicelist_.end();++it2){
			//is it the same actuator name
			if(it2->name_.compare(dev->name_)==0){
				//is the serialnumber the same
				if(it2->sn_.compare(dev->sn_)==0){
					//yes->add Channal 
					it2->channel_.push_back(l);
					finddev=true;					
				}
				//no->continue search
			}
		}
		//not found->add device
		if(!finddev){
			devicelist_.push_back(*dev);
		}
	}
	//print devicelist
	LogMessage ("Devicelist");
	for(std::vector<PSJdevice>::iterator it2 = devicelist_.begin(); it2 != devicelist_.end();++it2){
		std::ostringstream dev;
		dev<<it2->name_<<","<<it2->sn_;
		for(std::vector<int>::iterator it3 = it2->channel_.begin(); it3 != it2->channel_.end(); it3++){
			dev<<","<<*it3;
		}
		LogMessage (dev.str().c_str());
	}
	//Add device as
	devicelist d;
	//int s=0;
	int t=1;
	int y=0;
	//int m=0;
	for(std::vector<PSJdevice>::iterator it2 = devicelist_.begin(); it2 != devicelist_.end();++it2){		
		LogMessage (it2->serie_);
		//Stage
		//if(d.isStage(it2->comparename_)){
		if(d.isStage(it2->serie_)){
			MM::Device * pdev =new Stage((int)it2->channel_.at(0));
			char name[20];
			pdev->GetName(name);
			it2->devname_=name;			
			AddInstalledDevice(pdev);
			continue;
		}
		//Shutter
		//if(d.isShutter(it2->comparename_)){	
		if(d.isShutter(it2->serie_)){
			MM::Device * pdev =new Shutter((int)it2->channel_.at(0),g_Shutter);
			char name[20];
			pdev->GetName(name);
			it2->devname_=name;			
			AddInstalledDevice(pdev);
			continue;
		}
		//XYStage
		//if(d.isXYStage(it2->comparename_)){
		if(d.isXYStage(it2->serie_)){
			MM::Device * pdev =new XYStage(y);
			char name[20];
			pdev->GetName(name);
			it2->devname_=name;	
			AddInstalledDevice(pdev);
			y++;
			continue;
		}
		//Tritor
		//if(d.isTritor(it2->comparename_)){
		if(d.isTritor(it2->serie_)){
			MM::Device * pdev =new Tritor(t,g_Tritor);
			char name[20];
			pdev->GetName(name);
			it2->devname_=name;			
			AddInstalledDevice(pdev);			
			t++;
			continue;
		}
		
		/*
		//Mirror
		//TODO: Mirror with 1,2 or 3 axis 
		if(d.isMirror(it2->serie_)){
			if(d.isMirror1(it2->comparename_)){
				int ch=0;
				m++;
				for(std::vector<int>::iterator it3 = it2->channel_.begin(); it3 != it2->channel_.end(); it3++){
					ch++;
				}			
				AddInstalledDevice(new Mirror(ch));
			}
		}
		//TODO:Pentor
		if(d.isPentor(it2->comparename_)){			
			AddInstalledDevice(new Tritor(t,g_Tritor));
			AddInstalledDevice(new Mirror(2,g_Mirror));
		}
		*/
		
	}
	return DEVICE_OK;
}

std::vector<int> Hub::GetDeviceAddresses(){
	return inventoryDeviceAddresses_;
	
}
//Convert name to a standard (without space, upper case)
std::string Hub::ConvertName(std::string name){
	std::string str;	
	str.assign(name.begin(), remove_if(name.begin(), name.end(), ::isspace));
	//str.assign(name.begin(), remove_if(name.begin(), name.end(), &isdigit));
	//str.assign(name.begin(), remove_if(name.begin(), name.end(), &ispunct));
	std::transform(str.begin(),str.end(),str.begin(),::toupper);
	return str;
}
int Hub::SendCommand(const char* cmd,std::string &result){
	int ret;	
	ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;
	ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);
	if (ret != DEVICE_OK) 
      return ret;
	return DEVICE_OK;
}

int Hub::SendServiceCommand(const char* cmd,std::string& result){	
	int ret;		
	ret = SendSerialCommand(port_.c_str(), g_cmdsmon, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;		
	ret = SendCommand(cmd, result);	
	ret = SendSerialCommand(port_.c_str(), g_cmdsmoff, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;
	return DEVICE_OK;
}
int Hub::GetActuatorName(int ch,char* name){
	std::string result;
	char s[20];
	sprintf(s,"acdescr,%d",ch);	
	const char* cmd = s;
	SendServiceCommand(cmd,result);	
	char* dest[30];
	splitString((char*)result.c_str()," ,\n",dest);	
	sprintf(name,"%s", dest[2]);
	LogMessage("GetActorname");
	LogMessage(name);
	return DEVICE_OK;
}
int Hub::GetSerialNumberActuator(int ch,char* sn){	
	std::string result;
	char s[20];
	sprintf(s,"rdac,%d,0",ch);	
	const char* cmd = s;
	SendServiceCommand(cmd,result);	
	char* dest[30];
	splitString((char*)result.c_str()," ,\n",dest);	
	sprintf(sn,"%s", dest[2]);
	LogMessage("Serial number");
	LogMessage(sn);
	return DEVICE_OK;
}
//////////////// Action Handlers (Hub) /////////////////

int Hub::OnPort(MM::PropertyBase* pProp, MM::ActionType pAct)
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
         return ERR_PORT_CHANGE_FORBIDDEN;
      }
      pProp->Get(port_);
   }
   return DEVICE_OK;
}
int Hub::OnConfig(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   if (pAct == MM::BeforeGet)
   {
      pProp->Set("Operate");
   }
   else if (pAct == MM::AfterSet)
   {
      // TODO check that we were initialized
      string request;
      pProp->Get(request);
      if (request == "GetInfo")
      {
         // Get Info and write to debug output:
      }
   }
   return DEVICE_OK;
}


int Hub::OnBright(MM::PropertyBase* pProp, MM::ActionType pAct)
{
   long l=0;
   if (pAct == MM::BeforeGet)
   {
	  int ret= GetBright(bright_);
	  if (ret !=DEVICE_OK)
            return ret;
	  l=bright_;
      pProp->Set(l);
   }
   else if (pAct == MM::AfterSet)
   {      
      pProp->Get(l);
	  bright_=(int)l;
	  int ret = SetBright(bright_);
      if (ret !=DEVICE_OK)
            return ret;
   }
   return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// Stage
///////////////////////////////////////////////////////////////////////////////

/**
 * Single axis stage.
 */
Stage::Stage():
   EVDBase(this),
   initialized_(false),
   stepSizeUm_(0.1)
{
   LogMessage ("new Stage");
   chx_.channel_=0;
   chx_.stat_=0;
   chx_.loop_=false;
   chx_.pos_=0;
   chx_.rohm_=0;
   chx_.min_V_=(-20.0);
   chx_.max_V_=130.0;
   chx_.min_um_=0.0;
   chx_.max_um_=50.0;
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_NO_CONTROLLER, "Please add the PSJController device first!");

   // create pre-initialization properties
   // ------------------------------------

   // Name
 //  CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "PSJ stage driver adapter", MM::String, true);

   // Axis ID
   id_ = "Z";
   CPropertyAction* pAct = new CPropertyAction(this, &Stage::OnID);
   CreateProperty(g_PSJ_Axis_Id, id_.c_str(), MM::String, false, pAct, false); 
   AddAllowedValue(g_PSJ_Axis_Id, "X");
   AddAllowedValue(g_PSJ_Axis_Id, "Y");
   AddAllowedValue(g_PSJ_Axis_Id, "Z");
   AddAllowedValue(g_PSJ_Axis_Id, "R");
   AddAllowedValue(g_PSJ_Axis_Id, "T");
   AddAllowedValue(g_PSJ_Axis_Id, "F");
   AddAllowedValue(g_PSJ_Axis_Id, "A");
   AddAllowedValue(g_PSJ_Axis_Id, "B");
   AddAllowedValue(g_PSJ_Axis_Id, "C");
   
   //channel   
   pAct = new CPropertyAction (this, &Stage::OnChannel);
   CreateProperty(g_Channel, "1", MM::Integer, false, pAct,true);
   if(inventoryDeviceAddresses_.empty()){		
		AddAllowedValue(g_Channel, "1");
		AddAllowedValue(g_Channel, "2");
		AddAllowedValue(g_Channel, "3");
		AddAllowedValue(g_Channel, "4");
		AddAllowedValue(g_Channel, "5");
		AddAllowedValue(g_Channel, "6");
   }else{
		for(std::vector<int>::iterator it=inventoryDeviceAddresses_.begin();it!=inventoryDeviceAddresses_.end();++it){
			AddAllowedValue(g_Channel, CDeviceUtils::ConvertToString(((int)*it)+1));
		}
   }
   //Loop   
   pAct = new CPropertyAction (this, &Stage::OnLoop);
   CreateProperty(g_Loop, g_Loop_open, MM::String, false, pAct,false);
   AddAllowedValue(g_Loop, g_Loop_open);
   AddAllowedValue(g_Loop, g_Loop_close);
}

Stage::Stage(int channel) :
   EVDBase(this),
   initialized_(false),
   stepSizeUm_(0.1)
{
   LogMessage ("new Stage(ch)");
   chx_.channel_=channel;
   chx_.stat_=0;
   chx_.loop_=false;
   chx_.pos_=0;
   chx_.rohm_=0;
   chx_.min_V_=(-20.0);
   chx_.max_V_=130.0;
   chx_.min_um_=0.0;
   chx_.max_um_=50.0;
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_NO_CONTROLLER, "Please add the PSJController device first!");

   // create pre-initialization properties
   // ------------------------------------

   // Name
 //  CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "PSJ stage driver adapter", MM::String, true);

   // Axis ID
   id_ = "Z";
   CPropertyAction* pAct = new CPropertyAction(this, &Stage::OnID);
   CreateProperty(g_PSJ_Axis_Id, id_.c_str(), MM::String, false, pAct, false); 
   AddAllowedValue(g_PSJ_Axis_Id, "X");
   AddAllowedValue(g_PSJ_Axis_Id, "Y");
   AddAllowedValue(g_PSJ_Axis_Id, "Z");
   AddAllowedValue(g_PSJ_Axis_Id, "R");
   AddAllowedValue(g_PSJ_Axis_Id, "T");
   AddAllowedValue(g_PSJ_Axis_Id, "F");
   AddAllowedValue(g_PSJ_Axis_Id, "A");
   AddAllowedValue(g_PSJ_Axis_Id, "B");
   AddAllowedValue(g_PSJ_Axis_Id, "C");

   // CDeviceUtils::SleepMs(2000);
   //channel
   //channel_=0;
   pAct = new CPropertyAction (this, &Stage::OnChannel);
   CreateProperty(g_Channel, "1", MM::Integer, false, pAct,true);
   
  if(inventoryDeviceAddresses_.empty()){
		
		AddAllowedValue(g_Channel, "1");
		AddAllowedValue(g_Channel, "2");
		AddAllowedValue(g_Channel, "3");
		AddAllowedValue(g_Channel, "4");
		AddAllowedValue(g_Channel, "5");
		AddAllowedValue(g_Channel, "6");
   }else{
		for(std::vector<int>::iterator it=inventoryDeviceAddresses_.begin();it!=inventoryDeviceAddresses_.end();++it){
			AddAllowedValue(g_Channel, CDeviceUtils::ConvertToString(((int)*it)+1));
		}
   }	

   //Loop   
   pAct = new CPropertyAction (this, &Stage::OnLoop);
   CreateProperty(g_Loop, g_Loop_open, MM::String, false, pAct,false);
   AddAllowedValue(g_Loop, g_Loop_open);
   AddAllowedValue(g_Loop, g_Loop_close);

}
Stage::Stage(int channel, std::string name) :
   EVDBase(this),
   name_(name),
   initialized_(false),
   stepSizeUm_(0.1)
{
   LogMessage ("new Stage(ch)");
   chx_.channel_=channel;
   chx_.stat_=0;
   chx_.loop_=false;
   chx_.pos_=0;
   chx_.rohm_=0;
   chx_.min_V_=(-20.0);
   chx_.max_V_=130.0;
   chx_.min_um_=0.0;
   chx_.max_um_=50.0;
   InitializeDefaultErrorMessages();
   SetErrorText(ERR_NO_CONTROLLER, "Please add the PSJController device first!");

   // create pre-initialization properties
   // ------------------------------------

   // Name
 //  CreateProperty(MM::g_Keyword_Name, g_StageDeviceName, MM::String, true);

   // Description
   CreateProperty(MM::g_Keyword_Description, "PSJ stage driver adapter", MM::String, true);

   // Axis ID
   id_ = "Z";
   CPropertyAction* pAct = new CPropertyAction(this, &Stage::OnID);
   CreateProperty(g_PSJ_Axis_Id, id_.c_str(), MM::String, false, pAct, false); 
   AddAllowedValue(g_PSJ_Axis_Id, "X");
   AddAllowedValue(g_PSJ_Axis_Id, "Y");
   AddAllowedValue(g_PSJ_Axis_Id, "Z");
   AddAllowedValue(g_PSJ_Axis_Id, "R");
   AddAllowedValue(g_PSJ_Axis_Id, "T");
   AddAllowedValue(g_PSJ_Axis_Id, "F");
   AddAllowedValue(g_PSJ_Axis_Id, "A");
   AddAllowedValue(g_PSJ_Axis_Id, "B");
   AddAllowedValue(g_PSJ_Axis_Id, "C");

   // CDeviceUtils::SleepMs(2000);
   //channel
   //channel_=0;
   pAct = new CPropertyAction (this, &Stage::OnChannel);
   CreateProperty(g_Channel, "1", MM::Integer, false, pAct,true);
   
  if(inventoryDeviceAddresses_.empty()){
		
		AddAllowedValue(g_Channel, "1");
		AddAllowedValue(g_Channel, "2");
		AddAllowedValue(g_Channel, "3");
		AddAllowedValue(g_Channel, "4");
		AddAllowedValue(g_Channel, "5");
		AddAllowedValue(g_Channel, "6");
   }else{
		for(std::vector<int>::iterator it=inventoryDeviceAddresses_.begin();it!=inventoryDeviceAddresses_.end();++it){
			AddAllowedValue(g_Channel, CDeviceUtils::ConvertToString(((int)*it)+1));
		}
   }	

   //Loop   
   pAct = new CPropertyAction (this, &Stage::OnLoop);
   CreateProperty(g_Loop, g_Loop_open, MM::String, false, pAct,false);
   AddAllowedValue(g_Loop, g_Loop_open);
   AddAllowedValue(g_Loop, g_Loop_close);

}
Stage::~Stage()
{
   Shutdown();
}
///////////////////////////////////////////////////////////////////////////////
// Stage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void Stage::GetName(char* Name) const
{
	if(name_.empty()){
		std::ostringstream name;
		name<<g_StageDeviceName<<(chx_.channel_+1);
		CDeviceUtils::CopyLimitedString(Name, name.str().c_str());		
	}else{
		//Name=name_;
		CDeviceUtils::CopyLimitedString(Name, name_.c_str());
	}
}
int Stage::Initialize()
{
    core_ = GetCoreCallback();

	//LogMessage ("PSJ Stage Init");
   // set property list
   // -----------------
   
   // Position
   // --------	
   CPropertyAction* pAct = new CPropertyAction (this, &Stage::OnStepSize);
   int ret = CreateProperty("StepSize", "1.0", MM::Float, false, pAct);
   if (ret != DEVICE_OK)
      return ret;

   //char c[5];   
   //sprintf(c,"%d",chx_.channel_+1);
   //const char* ch=c;   
   //CreateProperty(Channel_, ch, MM::Integer, true);	//read-only 
	CreateProperty(g_Channel_, CDeviceUtils::ConvertToString(chx_.channel_+1), MM::Integer, true);	//read-only 

	//LogMessage ("Property Status");
	pAct = new CPropertyAction (this, &Stage::OnStat);
    CreateProperty(g_Status, "0", MM::Integer, true, pAct);
	
	char n[20];
	ret = GetActuatorName(n);	
	//CDeviceUtils::CopyLimitedString(ac_name_,(const char*) n);	
	if (ret != DEVICE_OK)
      return ret;	
	ac_name_=n;	
	CreateProperty(g_Actuator,ac_name_, MM::String, true);

	ret = GetLimitsValues();
	if (ret != DEVICE_OK)
      return ret;	

	//pAct = new CPropertyAction (this, &Stage::OnMinV);		
	CreateProperty(g_Limit_V_Min, CDeviceUtils::ConvertToString(chx_.min_V_), MM::Float, true);
	//pAct = new CPropertyAction (this, &Stage::OnMaxV);	;	
	CreateProperty(g_Limit_V_Max, CDeviceUtils::ConvertToString(chx_.max_V_), MM::Float, true);
	//pAct = new CPropertyAction (this, &Stage::OnMinUm);		
	CreateProperty(g_Limit_Um_Min,CDeviceUtils::ConvertToString(chx_.min_um_), MM::Float, true);
	//pAct = new CPropertyAction (this, &Stage::OnMaxUm);	
	CreateProperty(g_Limit_Um_Max, CDeviceUtils::ConvertToString(chx_.max_um_), MM::Float, true);

	char s[20];
	GetRgver(chx_.rgver_);
	sprintf(s,"%i",chx_.rgver_);	
	CreateProperty(g_Rgver, s, MM::Integer, true);
	pAct = new CPropertyAction (this, &Stage::OnTime);
	CreateProperty(g_Rohm, "0", MM::Integer, true, pAct);
	pAct = new CPropertyAction (this, &Stage::OnTemp);
    CreateProperty(g_Ktemp, "0", MM::Float, true, pAct);

	pAct = new CPropertyAction (this, &Stage::OnSoftstart);
    CreateProperty(g_Fenable, g_Fenable_Off, MM::String, false, pAct);
    AddAllowedValue(g_Fenable , g_Fenable_Off);
    AddAllowedValue(g_Fenable, g_Fenable_On);

	pAct = new CPropertyAction (this, &Stage::OnSlewRate);
	CreateProperty(g_Sr, "10.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Sr, 0.0000002, 500.0);

	pAct = new CPropertyAction (this, &Stage::OnModulInput);
    CreateProperty(g_Modon, g_Modon_Off, MM::String, false, pAct);
    AddAllowedValue(g_Modon, g_Modon_Off);
    AddAllowedValue(g_Modon, g_Modon_On);

	pAct = new CPropertyAction (this, &Stage::OnMonitor);
    CreateProperty(g_Monsrc, g_Monsrc_0, MM::String, false, pAct);
    AddAllowedValue(g_Monsrc, g_Monsrc_0);
    AddAllowedValue(g_Monsrc, g_Monsrc_1);
	AddAllowedValue(g_Monsrc, g_Monsrc_2);
	AddAllowedValue(g_Monsrc, g_Monsrc_3);
	AddAllowedValue(g_Monsrc, g_Monsrc_4);
	AddAllowedValue(g_Monsrc, g_Monsrc_5);
	AddAllowedValue(g_Monsrc, g_Monsrc_6);	
	
	pAct = new CPropertyAction (this, &Stage::OnVoltage);
	CreateProperty(g_Voltage, "0", MM::Float, true, pAct);
	SetPropertyLimits(g_Voltage, chx_.min_V_, chx_.max_V_);	
	
	pAct = new CPropertyAction (this, &Stage::OnPosition);
	CreateProperty(g_Position, "0", MM::Float, false, pAct);

	pAct = new CPropertyAction (this, &Stage::OnPidP);
	CreateProperty(g_PID_P, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_PID_P, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Stage::OnPidI);
	CreateProperty(g_PID_I, "0.0", MM::Float, false, pAct);
   SetPropertyLimits(g_PID_I, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Stage::OnPidD);
	CreateProperty(g_PID_D, "0.0", MM::Float, false, pAct);
   SetPropertyLimits(g_PID_D, 0.0, 999.0);

   //Notch Filter
   pAct = new CPropertyAction (this, &Stage::OnNotch);
    CreateProperty(g_Notch, g_Notch_Off, MM::String, false, pAct);
    AddAllowedValue(g_Notch, g_Notch_Off);
    AddAllowedValue(g_Notch, g_Notch_On);
   pAct = new CPropertyAction (this, &Stage::OnNotchFreq);
	CreateProperty(g_Notch_Freq, "0", MM::Integer, false, pAct);
   SetPropertyLimits(g_Notch_Freq, 0, 20000);
      pAct = new CPropertyAction (this, &Stage::OnNotchBand);
	CreateProperty(g_Notch_Band, "0", MM::Integer, false, pAct);
   SetPropertyLimits(g_Notch_Band, 0, 20000);
	//Low pass filter
    pAct = new CPropertyAction (this, &Stage::OnLowpass);
   CreateProperty(g_Lowpass, g_Lowpass_Off, MM::String, false, pAct);
    AddAllowedValue(g_Lowpass, g_Lowpass_Off);
    AddAllowedValue(g_Lowpass, g_Lowpass_On);
	pAct = new CPropertyAction (this, &Stage::OnLowpassFreq);
	CreateProperty(g_Lowpass_Freq, "0", MM::Integer, false, pAct);
   SetPropertyLimits(g_Lowpass_Freq, 0, 20000);

   //Internal function generator
    chx_.gfkt_=0;
	pAct = new CPropertyAction (this, &Stage::OnGenerate);
	CreateProperty(g_Generator, g_Generator_Off, MM::String, false, pAct);
	AddAllowedValue(g_Generator, g_Generator_Off);
	AddAllowedValue(g_Generator, g_Generator_Sine);
	AddAllowedValue(g_Generator, g_Generator_Tri);
	AddAllowedValue(g_Generator, g_Generator_Rect);
	AddAllowedValue(g_Generator, g_Generator_Noise);
	AddAllowedValue(g_Generator, g_Generator_Sweep);
	
	//Sine
	pAct = new CPropertyAction (this, &Stage::OnSinAmp);
	CreateProperty(g_Generator_Sine_Amp, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_Amp, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Stage::OnSinOff);
	CreateProperty(g_Generator_Sine_Offset, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_Offset, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Stage::OnSinFreq);
	CreateProperty(g_Generator_Sine_Freq, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_Freq, 0.00001, 9999.9);
	//triangle
	pAct = new CPropertyAction (this, &Stage::OnTriAmp);
	CreateProperty(g_Generator_Tri_Amp, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_Amp, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Stage::OnTriOff);
	CreateProperty(g_Generator_Tri_Offset, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Tri_Offset, 0.0, 100.0);
    pAct = new CPropertyAction (this, &Stage::OnTriFreq);
	CreateProperty(g_Generator_Tri_Freq, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_Freq, 0.00001, 9999.9);
    pAct = new CPropertyAction (this, &Stage::OnTriSym);
	CreateProperty(g_Generator_Tri_Sym, "50.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_Sym, 0.0, 100.0);
	//rectangle
   pAct = new CPropertyAction (this, &Stage::OnRecAmp);
   CreateProperty(g_Generator_Rect_Amp, "0", MM::Float, false, pAct);
   SetPropertyLimits(g_Generator_Rect_Amp, 0.0, 100.0);
   pAct = new CPropertyAction (this, &Stage::OnRecOff);
	CreateProperty(g_Generator_Rect_Offset, "0", MM::Float, false, pAct);
   SetPropertyLimits(g_Generator_Rect_Offset, 0.0, 100.0);
   pAct = new CPropertyAction (this, &Stage::OnRecFreq);
	CreateProperty(g_Generator_Rect_Freq, "0.1", MM::Float, false, pAct);
   SetPropertyLimits(g_Generator_Rect_Freq, 0.00001, 9999.9);
   pAct = new CPropertyAction (this, &Stage::OnRecSym);
	CreateProperty(g_Generator_Rect_Sym, "0.1", MM::Float, false, pAct);
   SetPropertyLimits(g_Generator_Rect_Sym, 0.0, 100.0);
	//Noise
	pAct = new CPropertyAction (this, &Stage::OnNoiAmp);
	CreateProperty(g_Generator_Noise_Amp, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Noise_Amp, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Stage::OnNoiOff);
	CreateProperty(g_Generator_Noise_Offset, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Noise_Offset, 0.0, 100.0);
	//Sweep
    pAct = new CPropertyAction (this, &Stage::OnSweAmp);
	CreateProperty(g_Generator_Sweep_Amp, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sweep_Amp, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Stage::OnSweOff);
	CreateProperty(g_Generator_Sweep_Offset, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_Offset, 0.0, 100.0);
    pAct = new CPropertyAction (this, &Stage::OnSweTime);
	CreateProperty(g_Generator_Sweep_Time, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_Time, 0.4, 800.0);
	
	//Scan
	pAct = new CPropertyAction (this, &Stage::OnScanType);
	CreateProperty(g_Scan_Type, g_Scan_Type_Off, MM::String, false, pAct);
	AddAllowedValue(g_Scan_Type, g_Scan_Type_Off);
	AddAllowedValue(g_Scan_Type, g_Scan_Type_Sine);
	AddAllowedValue(g_Scan_Type, g_Scan_Type_Tri);
	 pAct = new CPropertyAction (this, &Stage::OnScan);
	 CreateProperty(g_Scan_Start, g_Scan_Off, MM::String, false, pAct);
    AddAllowedValue(g_Scan_Start, g_Scan_Off);
    AddAllowedValue(g_Scan_Start, g_Scan_Starting);

	//trigger
    pAct = new CPropertyAction (this, &Stage::OnTriggerStart);
	CreateProperty(g_Trigger_Start, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Trigger_Start, chx_.max_um_*0.002, chx_.max_um_*0.998);
	pAct = new CPropertyAction (this, &Stage::OnTriggerEnd);
	CreateProperty(g_Trigger_End, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_End, chx_.max_um_*0.002, chx_.max_um_*0.998);
    pAct = new CPropertyAction (this, &Stage::OnTriggerInterval);
	CreateProperty(g_Trigger_Interval, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_Interval, chx_.min_um_, chx_.max_um_);
	pAct = new CPropertyAction (this, &Stage::OnTriggerTime);
	CreateProperty(g_Trigger_Time, "1", MM::Integer, false, pAct);
   SetPropertyLimits(g_Trigger_Time, 1, 255);
   pAct = new CPropertyAction (this, &Stage::OnTriggerType);
	CreateProperty(g_Trigger_Generator, g_Trigger_Off, MM::String, false, pAct);
	AddAllowedValue(g_Trigger_Generator, g_Trigger_Off);
	AddAllowedValue(g_Trigger_Generator, g_Trigger_Rising);
	AddAllowedValue(g_Trigger_Generator, g_Trigger_Falling);
	AddAllowedValue(g_Trigger_Generator, g_Trigger_Both);

	
   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;

   initialized_ = true;
   return DEVICE_OK;
}

int Stage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}


bool Stage::Busy()
{
	return false;
}
// Stage API
int Stage::SetPositionUm(double pos){
	LogMessage ("SetPositionUm",true);
	int ret;
	if(chx_.loop_){ //Close loop
		chx_.pos_=pos;
		chx_.voltage_=(chx_.max_V_-chx_.min_V_)*(pos-chx_.min_um_)/(chx_.max_um_-chx_.min_um_)+chx_.min_V_; //Translate Pos->Voltage
		ret=SetCommandValue("set",chx_.pos_);
	}else{  //open loop
		chx_.pos_=pos;
		chx_.voltage_=(chx_.max_V_-chx_.min_V_)*(pos-chx_.min_um_)/(chx_.max_um_-chx_.min_um_)+chx_.min_V_; //Translate Pos->Voltage		
		ret=SetCommandValue("set",chx_.voltage_);
	}
	if (ret != DEVICE_OK)
      return ret;
	return DEVICE_OK;
}

int Stage::GetPositionUm(double& pos){
	LogMessage ("GetPositionUm",true);
	int ret;
	double d;
	ret = GetCommandValue("mess",d);
	if(chx_.loop_){
		pos=d;
		chx_.pos_=pos;
		chx_.voltage_=(chx_.max_V_-chx_.min_V_)*(chx_.pos_-chx_.min_um_)/(chx_.max_um_-chx_.min_um_)+chx_.min_V_;
	}else{
		chx_.voltage_=d;
		pos=(chx_.max_um_-chx_.min_um_)*(chx_.voltage_-chx_.min_V_)/(chx_.max_V_-chx_.min_V_)+chx_.min_um_;
		chx_.pos_=pos;
	}
	return DEVICE_OK;
}

int Stage::SetOrigin()
{
	return DEVICE_UNSUPPORTED_COMMAND;
}
int Stage::GetLimits(double& min, double& max)
{	
	min=chx_.min_um_;
	max=chx_.max_um_;
	//return DEVICE_UNSUPPORTED_COMMAND;
	return DEVICE_OK;
}

int Stage::SendCommand(const char* cmd,std::string &result){
	int ret;		
	ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;
	ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);
	if (ret != DEVICE_OK) 
      return ret;
	return DEVICE_OK;
}

int Stage::SendServiceCommand(const char* cmd,std::string& result){	
	int ret;	
	ret = SendSerialCommand(port_.c_str(), g_cmdsmon, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;		
	ret = SendCommand(cmd, result);	
	ret = SendSerialCommand(port_.c_str(), g_cmdsmoff, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;
	return DEVICE_OK;
}
int Stage::GetCommandValue(const char* c,double& d){
	char str[50]="";
	sprintf(str,"%s,%d",c,chx_.channel_);	
	const char* cmd = str; 	
    int ret;
	std::string result;
	ret = SendCommand(cmd,result);	
	if (ret != DEVICE_OK)
		return ret;	
	if (result.length() < 1)
      return ERR_NO_ANSWER;	
	char* dest[50];
	splitString((char*)result.c_str()," ,\n",dest);
	std::string type;
	type=dest[0];
	if(type==c){			
		d=atof(dest[2]);
	}else{
		LogMessage ("Wrong Result",true);	
		LogMessage (dest[0],true);
		d=0.0;
		ret =PurgeComPort(port_.c_str());
		CDeviceUtils::SleepMs(10);
	}		
	return DEVICE_OK;
}
int Stage::GetCommandValue(const char* c,int& i){	
	char cmd[50]="";
	sprintf(cmd,"%s,%d",c,chx_.channel_);	
    int ret;
	std::string result;
	std::string type;
	ret = SendCommand(cmd,result);
	if (ret != DEVICE_OK)
		return ret;	
	if (result.length() < 1)
      return ERR_NO_ANSWER;	
	char* dest[50];
	splitString((char*)result.c_str()," ,\n",dest);
	type=dest[0];
	if(type==c){
		i=atoi(dest[2]);
	}else{
		LogMessage ("Wrong Result",true);	
		LogMessage (dest[0],true);
		i=0;
		ret =PurgeComPort(port_.c_str());
		CDeviceUtils::SleepMs(10);
	}
	return ret;
}
int Stage::SetCommandValue(const char* c,double fkt){	
	char str[50]="";
	sprintf(str,"%s,%d,%lf",c,chx_.channel_,fkt);	
	const char* cmd = str; 	
    int ret;
    ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK){	  
      return ret;
	}	
	return DEVICE_OK;
}
int Stage::SetCommandValue(const char* c,int fkt){	
	char str[50]="";
	sprintf(str,"%s,%d,%d",c,chx_.channel_,fkt);	
	const char* cmd = str; 	
    int ret;
    ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK){	  
      return ret;
	}	
	return DEVICE_OK;
}
int Stage::GetLimitsValues(){	
	int ret;	
	char s[20];
	char* dest[20];
	std::string result;
	ret = SendSerialCommand(port_.c_str(), g_cmdsmon, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;	
	//Send Command
	//min_um_	
	sprintf(s,"rdac,%d,197",chx_.channel_);	
	const char* cmd = s; 
	ret = SendCommand(cmd, result);		
	splitString((char*)result.c_str()," ,\n",dest);
	chx_.min_um_=atof(dest[2]);

	//max_um_	
	sprintf(s,"rdac,%d,198",chx_.channel_);	
	cmd = s; 	
	ret = SendCommand(cmd, result);		
	splitString((char*)result.c_str()," ,\n",dest);
	chx_.max_um_=atof(dest[2]);

	//min_V_	
	sprintf(s,"rdac,%d,199",chx_.channel_);	
	cmd = s; 
	ret = SendCommand(cmd, result);	
	splitString((char*)result.c_str()," ,\n",dest);
	chx_.min_V_=atof(dest[2]);

	//max_V_	
	sprintf(s,"rdac,%d,200",chx_.channel_);	
	cmd = s;
	ret = SendCommand(cmd, result);		
	splitString((char*)result.c_str()," ,\n",dest);
	chx_.max_V_=atof(dest[2]);	
	
	ret = SendSerialCommand(port_.c_str(), g_cmdsmoff, g_Mesg_Send_term);
	if (ret != DEVICE_OK) 
      return ret;
	return DEVICE_OK;
}
int Stage::GetChannel(int& channel){
	channel = chx_.channel_;
	return DEVICE_OK;
}
int Stage::SetChannel(int channel){
	chx_.channel_ = channel;
	return DEVICE_OK;
}
int Stage::GetAxis(int& id){	
	std::string result;
	char s[20];
	sprintf(s,"rdac,%d,5",chx_.channel_);	
	const char* cmd = s; 	
	SendServiceCommand(cmd,result);	
	char* dest[20];
	splitString((char*)result.c_str()," ,\n",dest);
	id = atoi(dest[2]);
	return DEVICE_OK;
}
int Stage::GetActuatorName(char* id){	
	std::string result;
	char s[20];
	sprintf(s,"acdescr,%d",chx_.channel_);	
	const char* cmd = s;
	SendServiceCommand(cmd,result);	
	char* dest[30];
	splitString((char*)result.c_str()," ,\n",dest);	
	sprintf(id,"%s", dest[2]);	
	return DEVICE_OK;
}
int Stage::GetSerialNumberActuator(char* sn){
	LogMessage ("GetSerialNumberActuator",true);
	std::string result;
	char s[20];
	sprintf(s,"rdac,%d,0",chx_.channel_);	
	const char* cmd = s;
	SendServiceCommand(cmd,result);
	LogMessage(result);
	char* dest[30];
	splitString((char*)result.c_str()," ,\n",dest);	
	sprintf(sn,"%s", dest[2]);	
	LogMessage(sn);
	return DEVICE_OK;
}
int Stage::GetSerialNumberDevice(char* sn){	
	std::string result;
	char s[20];
	sprintf(s,"rdac,%d,1",chx_.channel_);	
	const char* cmd = s;
	SendServiceCommand(cmd,result);	
	char* dest[30];
	splitString((char*)result.c_str()," ,\n",dest);	
	sprintf(sn,"%s", dest[2]);	
	return DEVICE_OK;
}
int Stage::GetKtemp(double& ktemp){	    
    int ret = GetCommandValue("ktemp",chx_.ktemp_);
	if (ret != DEVICE_OK)	  
      return ret;	
    ktemp=chx_.ktemp_;
   return DEVICE_OK;
}
int Stage::GetRohm(int& rohm){	
    int ret = GetCommandValue("rohm",chx_.rohm_);
	if (ret != DEVICE_OK)	  
      return ret;	
    rohm=chx_.rohm_;
   return DEVICE_OK;
}
int Stage::GetRgver(int& rgver){	   
    int ret = GetCommandValue("rgver",chx_.rgver_);
	if (ret != DEVICE_OK)	  
      return ret;	
    rgver=chx_.rgver_;
   return DEVICE_OK;
}
int Stage::GetFenable(bool& b){	
    int ret;
	int i=0;
    ret = GetCommandValue("fenable",i);
	if (ret != DEVICE_OK)  
      return ret;	
	chx_.fenable_=(i==1)?true:false;
    b=chx_.fenable_;
   return DEVICE_OK;
}
int Stage::SetFenable(bool b){
	int l=(b)?1:0;
	int ret = SetCommandValue("fenable",l);
	chx_.fenable_=b;
	return ret;
}
int Stage::GetSr(double& d){    
   int ret = GetCommandValue("sr",d);   
   return ret;
}
int Stage::SetSr(double d){	
	int ret = SetCommandValue("sr",d);	
	if (ret != DEVICE_OK)	  
      return ret;	
	return DEVICE_OK;
}
int Stage::GetModon(bool& b){	
    int ret;
	int i=0;
    ret = GetCommandValue("modon",i);
	if (ret != DEVICE_OK)  
      return ret;	
	chx_.modon_=(i==1)?true:false;
    b=chx_.modon_;
   return DEVICE_OK;
}
int Stage::SetModon(bool b){
	int l=(b)?1:0;
	int ret = SetCommandValue("modon",l);
	chx_.modon_=b;
	return ret;
}
int Stage::GetMonsrc(int& i){    
   int ret = GetCommandValue("monsrc",chx_.monsrc_);
   i=chx_.monsrc_;
   return ret;
}
int Stage::SetMonsrc(int i){	
	int ret = SetCommandValue("monsrc",i);	
	if (ret != DEVICE_OK)	  
      return ret;
	chx_.monsrc_=i;
	return DEVICE_OK;
}
int Stage::GetLoop(bool& loop){
	LogMessage ("GetLoop");	
    int ret;
	int stat;    
	ret = GetCommandValue("stat",stat);
	if (ret != DEVICE_OK)
      return ret;
	chx_.loop_=((stat &CLOSE_LOOP)==CLOSE_LOOP)? true:false;
	loop=chx_.loop_;
	return DEVICE_OK;
}
int Stage::SetLoop(bool loop){
	LogMessage ("SetLoop");
	int i=(loop)?1:0;
	int ret = SetCommandValue("cl",i);	
	if (ret != DEVICE_OK)	  
      return ret;
	chx_.loop_=loop;
	return DEVICE_OK;
}
int Stage::GetNotchon(bool& notch){
	LogMessage ("GetNotch");
	int n;
	int ret;
	ret = GetCommandValue("notchon",n);
	if (ret != DEVICE_OK)
      return ret;
	notch=(n==1)?true:false;	
	return DEVICE_OK;
}
int Stage::SetNotchon(bool notch){	
	int i=(notch)?1:0;
	int ret = SetCommandValue("notchon",i);	
	if (ret != DEVICE_OK)	  
      return ret;
	chx_.notchon_=notch;
	return DEVICE_OK;
}
int Stage::GetNotchf(int& i){    
   int ret = GetCommandValue("notchf",chx_.notchf_);
   i=chx_.notchf_;
   return ret;
}
int Stage::SetNotchf(int i){	
	int ret = SetCommandValue("notchf",i);	
	if (ret != DEVICE_OK)	  
      return ret;
	chx_.notchf_=i;
	//notch band = max. 2* notch freqency
	if(chx_.notchb_>=(2*chx_.notchf_)){
			chx_.notchb_=(2*chx_.notchf_);
			ret = SetNotchb(chx_.notchb_);
	}
	return DEVICE_OK;
}
int Stage::GetNotchb(int& i){    
   int ret = GetCommandValue("notchb",chx_.notchb_);
   i=chx_.notchb_;
   return ret;
}
int Stage::SetNotchb(int i){	
	int ret = SetCommandValue("notchb",i);	
	if (ret != DEVICE_OK)	  
      return ret;
	chx_.notchb_=i;
	
	return DEVICE_OK;
}
int Stage::GetLpon(bool& lp){
	LogMessage ("GetLpon");
	int n;
	int ret;
	ret = GetCommandValue("lpon",n);
	if (ret != DEVICE_OK)
      return ret;
	lp=(n==1)?true:false;	
	return DEVICE_OK;
}
int Stage::SetLpon(bool lp){
	LogMessage ("SetLpon");
	int i=(lp)?1:0;
	int ret = SetCommandValue("lpon",i);	
	if (ret != DEVICE_OK)	  
      return ret;
	chx_.lpon_=lp;
	return DEVICE_OK;
}
int Stage::GetLpf(int& i){    
   int ret = GetCommandValue("lpf",chx_.lpf_);
   i=chx_.lpf_;
   return ret;
}
int Stage::SetLpf(int i){	
	int ret = SetCommandValue("lpf",i);	
	if (ret != DEVICE_OK)	  
      return ret;
	chx_.lpf_=i;	
	return DEVICE_OK;
}
int Stage::GetKp(double& d){    
   int ret = GetCommandValue("kp",chx_.kp_);
   d=chx_.kp_;
   return ret;
}
int Stage::SetKp(double d){	
	int ret = SetCommandValue("kp",d);	
	if (ret != DEVICE_OK)	  
      return ret;	
	return DEVICE_OK;
}
int Stage::GetKi(double& d){    
   int ret = GetCommandValue("ki",chx_.ki_);
   d=chx_.ki_;
   return ret;
}
int Stage::SetKi(double d){	
	int ret = SetCommandValue("ki",d);	
	if (ret != DEVICE_OK)	  
      return ret;	
	return DEVICE_OK;
}
int Stage::GetKd(double& d){    
   int ret = GetCommandValue("kd",d);
   chx_.kd_=d;
   return ret;
}
int Stage::SetKd(double d){	
	int ret = SetCommandValue("kd",d);	
	if (ret != DEVICE_OK)	  
      return ret;	
	return DEVICE_OK;
}
int Stage::GetGfkt(int& fkt){	
	int ret = GetCommandValue("gfkt",fkt);
	if (ret != DEVICE_OK)	  
      return ret;	
	chx_.gfkt_=fkt;
	return DEVICE_OK;
}
int Stage::SetGfkt(int fkt){
	LogMessage ("SetGfkt");
	if(-1<fkt && fkt< 6){
		int ret = SetCommandValue("gfkt",fkt);
		if (ret != DEVICE_OK){	  
			return ret;
		}
	}	
	return DEVICE_OK;
}

//sine
int Stage::GetGasin(double& d){
	int ret = GetCommandValue("gasin",d);	
	if (ret != DEVICE_OK)	  
      return ret;	
	chx_.gasin_=d;
	return DEVICE_OK;
}
int Stage::SetGasin(double d){	
	int ret = SetCommandValue("gasin",d);	
	if (ret != DEVICE_OK){	  
      return ret;
	}
	chx_.gasin_=d;
	return DEVICE_OK;
}
int Stage::GetGosin(double& d){
	int ret = GetCommandValue("gosin",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	chx_.gosin_=d;
	return DEVICE_OK;
}
int Stage::SetGosin(double d){	
	int ret = SetCommandValue("gosin",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	chx_.gosin_=d;
	return DEVICE_OK;
}
int Stage::GetGfsin(double& d){
	int ret = GetCommandValue("gfsin",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	chx_.gfsin_=d;
	return DEVICE_OK;
}
int Stage::SetGfsin(double d){	
	int ret = SetCommandValue("gfsin",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	chx_.gfsin_=d;
	return DEVICE_OK;
}
//triangle
int Stage::GetGatri(double& d){
	int ret = GetCommandValue("gatri",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	chx_.gatri_=d;
	return DEVICE_OK;
}
int Stage::SetGatri(double d){	
	int ret = SetCommandValue("gatri",d);	
	if (ret != DEVICE_OK){	  
      return ret;
	}
	chx_.gatri_=d;
	return DEVICE_OK;
}
int Stage::GetGotri(double& d){
	int ret = GetCommandValue("gotri",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	chx_.gotri_=d;
	return DEVICE_OK;
}
int Stage::SetGotri(double d){	
	int ret = SetCommandValue("gotri",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	chx_.gotri_=d;
	return DEVICE_OK;
}
int Stage::GetGftri(double& d){
	int ret = GetCommandValue("gftri",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	chx_.gftri_=d;
	return DEVICE_OK;
}
int Stage::SetGftri(double d){		
	int ret = SetCommandValue("gftri",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	chx_.gftri_=d;
	return DEVICE_OK;
}
int Stage::GetGstri(double& d){
	int ret = GetCommandValue("gstri",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	chx_.gstri_=d;
	return DEVICE_OK;
}
int Stage::SetGstri(double d){		
	int ret = SetCommandValue("gstri",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	chx_.gstri_=d;
	return DEVICE_OK;
}
//rectangle
int Stage::GetGarec(double& d){
	int ret = GetCommandValue("garec",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	chx_.garec_=d;
	return DEVICE_OK;
}
int Stage::SetGarec(double d){	
	int ret = SetCommandValue("garec",d);	
	if (ret != DEVICE_OK){	  
      return ret;
	}
	chx_.garec_=d;
	return DEVICE_OK;
}
int Stage::GetGorec(double& d){
	int ret = GetCommandValue("gorec",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	chx_.gorec_=d;
	return DEVICE_OK;
}
int Stage::SetGorec(double d){		
	int ret = SetCommandValue("gorec",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	chx_.gorec_=d;
	return DEVICE_OK;
}
int Stage::GetGfrec(double& d){
	int ret = GetCommandValue("gfrec",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	chx_.gfrec_=d;
	return DEVICE_OK;
}
int Stage::SetGfrec(double d){	
	int ret = SetCommandValue("gfrec",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	chx_.gfrec_=d;
	return DEVICE_OK;
}
int Stage::GetGsrec(double& d){
	int ret = GetCommandValue("gsrec",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	chx_.gsrec_=d;
	return DEVICE_OK;
}
int Stage::SetGsrec(double d){		
	int ret = SetCommandValue("gsrec",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	chx_.gsrec_=d;
	return DEVICE_OK;
}
//noise
int Stage::GetGanoi(double& d){
	int ret = GetCommandValue("ganoi",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	chx_.ganoi_=d;
	return DEVICE_OK;
}
int Stage::SetGanoi(double d){	
	int ret = SetCommandValue("ganoi",d);	
	if (ret != DEVICE_OK){	  
      return ret;
	}
	chx_.ganoi_=d;
	return DEVICE_OK;
}
int Stage::GetGonoi(double& d){
	int ret = GetCommandValue("gonoi",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	chx_.gonoi_=d;
	return DEVICE_OK;
}
int Stage::SetGonoi(double d){		
	int ret = SetCommandValue("gonoi",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	chx_.gonoi_=d;
	return DEVICE_OK;
}
//sweep
int Stage::GetGaswe(double& d){
	int ret = GetCommandValue("gaswe",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	chx_.gaswe_=d;
	return DEVICE_OK;
}
int Stage::SetGaswe(double d){	
	int ret = SetCommandValue("gaswe",d);	
	if (ret != DEVICE_OK){	  
      return ret;
	}
	chx_.gaswe_=d;
	return DEVICE_OK;
}
int Stage::GetGoswe(double& d){
	int ret = GetCommandValue("goswe",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	chx_.goswe_=d;
	return DEVICE_OK;
}
int Stage::SetGoswe(double d){		
	int ret = SetCommandValue("goswe",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	chx_.goswe_=d;
	return DEVICE_OK;
}
int Stage::GetGtswe(double& d){
	int ret = GetCommandValue("gtswe",d);
	if (ret != DEVICE_OK)	  
      return ret;	
	chx_.gtswe_=d;
	return DEVICE_OK;
}
int Stage::SetGtswe(double d){	
	int ret = SetCommandValue("gtswe",d);
	if (ret != DEVICE_OK){	  
      return ret;
	}
	chx_.gtswe_=d;
	return DEVICE_OK;
}
//Scan
int Stage::GetScanType(int& i){    
   int ret = GetCommandValue("sct",chx_.sct_);
   i=chx_.sct_;
   return ret;
}
int Stage::SetScanType(int i){	
	int ret = SetCommandValue("sct",i);	
	if (ret != DEVICE_OK)	  
      return ret;
	chx_.sct_=i;
	return DEVICE_OK;
}
int Stage::GetScan(bool& b){   
   int i;
   int ret = GetCommandValue("ss",i);
   chx_.ss_=(i==1)?true:false;
   b=chx_.ss_;
   return ret;
}
int Stage::SetScan(bool b){	
	if (b){
	   int i=(b)?1:0;
	   int ret = SetCommandValue("ss",i);	
	   if (ret != DEVICE_OK)	  
         return ret;	
	}
	return DEVICE_OK;
}
int Stage::GetTrgss(double& d){    
   int ret = GetCommandValue("trgss",chx_.trgss_);
   d=chx_.trgss_;
   return ret;
}
int Stage::SetTrgss(double d){	
	int ret = SetCommandValue("trgss",d);	
	if (ret != DEVICE_OK)	  
      return ret;	
	return DEVICE_OK;
}
int Stage::GetTrgse(double& d){    
   int ret = GetCommandValue("trgse",chx_.trgse_);
   d=chx_.trgse_;
   return ret;
}
int Stage::SetTrgse(double d){	
	int ret = SetCommandValue("trgse",d);	
	if (ret != DEVICE_OK)	  
      return ret;	
	return DEVICE_OK;
}
int Stage::GetTrgsi(double& d){    
   int ret = GetCommandValue("trgsi",chx_.trgsi_);
   d=chx_.trgsi_;
   return ret;
}
int Stage::SetTrgsi(double d){	
	int ret = SetCommandValue("trgsi",d);	
	if (ret != DEVICE_OK)	  
      return ret;	
	return DEVICE_OK;
}
int Stage::GetTrglen(int& i){    
   int ret = GetCommandValue("trglen",chx_.trglen_);
   i=chx_.trglen_;
   return ret;
}
int Stage::SetTrglen(int i){	
	int ret = SetCommandValue("trglen",i);	
	if (ret != DEVICE_OK)	  
      return ret;
	chx_.trglen_=i;
	return DEVICE_OK;
}
int Stage::GetTrgedge(int& i){    
   int ret = GetCommandValue("trgedge",chx_.trgedge_);
   i=chx_.trgedge_;
   return ret;
}
int Stage::SetTrgedge(int i){	
	int ret = SetCommandValue("trgedge",i);	
	if (ret != DEVICE_OK)	  
      return ret;	
	return DEVICE_OK;
}
int Stage::GetSine(){
	LogMessage ("Get sine");	
	int ret=0;	
	ret = GetCommandValue("gasin",chx_.gasin_);
	if (ret != DEVICE_OK){
		return ret;
	}    
	ret = GetCommandValue("gosin",chx_.gosin_);
	if (ret != DEVICE_OK){
		return ret;
	}
	ret = GetCommandValue("gfsin",chx_.gfsin_);
	if (ret != DEVICE_OK){
		return ret;
	}
	return DEVICE_OK;
}
int Stage::SetPidDefault(){
	char s[50]="";
	sprintf(s,"sstd,%d",chx_.channel_);	
	const char* cmd = s; //"sstd,channel_";	
	LogMessage (cmd);
    int ret;
    ret = SendSerialCommand(port_.c_str(), cmd, g_Mesg_Send_term);
	if (ret != DEVICE_OK){
		return ret;
	}
	std::string result;
	ret = GetSerialAnswer(port_.c_str(), g_Mesg_Receive_term, result);  
	if (ret != DEVICE_OK){
		char msg[50]="PID Error ";
		sprintf(msg,"PID Error %d",ret);
		LogMessage (msg);
		return ret;
	}
	LogMessage (result);
	return DEVICE_OK;
}


///////////////////////////////////////////////////////////////////////////////
// Action handlers
///////////////////////////////////////////////////////////////////////////////
int Stage::OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct)
{	
   if (eAct == MM::BeforeGet)
   {
      pProp->Set(stepSizeUm_);
   }
   else if (eAct == MM::AfterSet)
   {
      double stepSize;
      pProp->Get(stepSize);
      if (stepSize <=0.0)
      {
         pProp->Set(stepSizeUm_);
         return ERR_INVALID_STEP_SIZE;
      }
      stepSizeUm_ = stepSize;
   }
   return DEVICE_OK;
}

int Stage::OnID(MM::PropertyBase* pProp, MM::ActionType eAct)
{	
   if (eAct == MM::BeforeGet)
   {      
      pProp->Set(id_.c_str());
   }
   else if (eAct == MM::AfterSet)
   {
      string id;
      pProp->Get(id_);      
   }
   return DEVICE_OK;
}
int Stage::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct){
	
	long c;
	if (eAct == MM::BeforeGet)
    {			
		c=chx_.channel_+1;		
		pProp->Set(c);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(c);		
		chx_.channel_=(int)c-1;
	}
    return DEVICE_OK;
}
int Stage::OnPosition(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){	
		int ret = GetPositionUm(chx_.pos_);
		if (ret!=DEVICE_OK)
			return ret;		
		pProp->Set(chx_.pos_);
		SetProperty(g_Voltage,CDeviceUtils::ConvertToString(chx_.voltage_));
	}
    else if (eAct == MM::AfterSet){
		int ret=0;
		pProp->Get(chx_.pos_);		
		ret = SetPositionUm(chx_.pos_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnStat(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){
		int i;		
		int ret=GetStatus(i,&chx_);			
		chx_.stat_=i;
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set((long)chx_.stat_);
	}
	return DEVICE_OK;
}
int Stage::OnTemp(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){
		int ret = GetKtemp(chx_.ktemp_);		
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set(chx_.ktemp_);
	}
	return DEVICE_OK;
}
int Stage::OnTime(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetRohm(chx_.rohm_);		
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set((long)chx_.rohm_);
	}
	return DEVICE_OK;
}
int Stage::OnSoftstart(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){		
		int ret=GetFenable(chx_.fenable_);		
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.fenable_){
			pProp->Set(g_Fenable_On);			
		}
		else{
			pProp->Set(g_Fenable_Off);			
		}		
	}
    else if (eAct == MM::AfterSet){	  
		std::string softstart;
		pProp->Get(softstart);
		if (softstart == g_Fenable_On){
			chx_.fenable_ = true;
		}else{
			chx_.fenable_ = false;
		}
		int ret = SetFenable(chx_.fenable_);	  
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnSlewRate(MM::PropertyBase* pProp, MM::ActionType eAct){
		
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		ret = GetSr(chx_.sr_);		
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)chx_.sr_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.sr_);		
		ret = SetSr(chx_.sr_);
		if (ret != DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Stage::OnModulInput(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){		
		int ret = GetModon(chx_.modon_);		
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.modon_){
			pProp->Set(g_Modon_On);			
		}else{
			pProp->Set(g_Modon_Off);			
		}		
	}else if (eAct == MM::AfterSet){	  
	  std::string modon;
      pProp->Get(modon);
	  if (modon == g_Modon_On){
         chx_.modon_ = true;
	  }else{
         chx_.modon_ = false;
	  }
	  int ret = SetModon(chx_.modon_);	    
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnMonitor(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {		
		int ret = GetMonsrc(chx_.monsrc_);		
		if (ret!=DEVICE_OK)
			return ret;				
		switch (chx_.monsrc_){
		case 0:
			pProp->Set(g_Monsrc_0);	break;
		case 1:
			pProp->Set(g_Monsrc_1);	break;
		case 2:
			pProp->Set(g_Monsrc_2);	break;
		case 3:
			pProp->Set(g_Monsrc_3);	break;
		case 4:
			pProp->Set(g_Monsrc_4);	break;
		case 5:
			pProp->Set(g_Monsrc_5);	break;
		case 6:
			pProp->Set(g_Monsrc_6);	break;
		default:
			pProp->Set(g_Monsrc_0);
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string mon;
		pProp->Get(mon);
		if (mon == g_Monsrc_0)
         chx_.monsrc_ = 0;
		else if (mon == g_Monsrc_1)
         chx_.monsrc_ = 1;
		else if (mon == g_Monsrc_2)
         chx_.monsrc_ = 2;
		else if (mon == g_Monsrc_3)
         chx_.monsrc_ = 3;
		else if (mon == g_Monsrc_4)
         chx_.monsrc_ = 4;
		else if (mon == g_Monsrc_5)
         chx_.monsrc_ = 5;	
		else if (mon == g_Monsrc_6)
         chx_.monsrc_ = 6;	
		int ret = SetMonsrc(chx_.monsrc_);		
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Stage::OnVoltage(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){	
		pProp->Set(chx_.voltage_);
	}
	return DEVICE_OK;
}
int Stage::OnMinV(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{			
		pProp->Set(chx_.min_V_);
	}
	return DEVICE_OK;
}
int Stage::OnMaxV(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{			
		pProp->Set(chx_.max_V_);
	}
	return DEVICE_OK;
}
int Stage::OnMinUm(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{			
		pProp->Set(chx_.min_um_);
	}
	return DEVICE_OK;
}
int Stage::OnMaxUm(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{			
		pProp->Set(chx_.max_um_);
	}
	return DEVICE_OK;
}
int Stage::OnLoop(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int l=0;
	if (eAct == MM::BeforeGet)
    {		
		int ret=GetLoop(chx_.loop_);
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.loop_){
			pProp->Set(g_Loop_close);
			l=1;
		}
		else{
			pProp->Set(g_Loop_open);
			l=0;
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string loop;
      pProp->Get(loop);
      if (loop == g_Loop_close){
         chx_.loop_ = true;
      }else{
         chx_.loop_ = false;
	  }
	  int ret = SetLoop(chx_.loop_);
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnPidP(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		ret = GetKp(chx_.kp_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)chx_.kp_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.kp_);		
		ret = SetKp(chx_.kp_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnPidI(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		ret = GetKi(chx_.ki_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)chx_.ki_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.ki_);		 
		ret = SetKi(chx_.ki_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnPidD(MM::PropertyBase* pProp, MM::ActionType eAct){		
	int ret =0;
	if (eAct == MM::BeforeGet)
	{
		ret = GetKd(chx_.kd_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)chx_.kd_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.kd_);		
		ret = SetKd(chx_.kd_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnNotch(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){
		int ret=GetNotchon(chx_.notchon_);
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.notchon_){
			pProp->Set(g_Notch_On);			
		}else{
			pProp->Set(g_Notch_Off);			
		}		
	}else if (eAct == MM::AfterSet){	  
	  std::string notch;
      pProp->Get(notch);
	  if (notch == g_Notch_On){
         chx_.notchon_ = true;
	  }else{
         chx_.notchon_ = false;
	  }
	  int ret = SetNotchon(chx_.notchon_);
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnNotchFreq(MM::PropertyBase* pProp, MM::ActionType eAct){	
	double c;
	if (eAct == MM::BeforeGet){		
		int ret = GetNotchf(chx_.notchf_);		
		if (ret != DEVICE_OK)
			return ret;
		SetPropertyLimits(g_Notch_Band, 0, ((2*chx_.notchf_)<=20000)?(2*chx_.notchf_):20000);
		c=chx_.notchf_;
		pProp->Set(c);
	}else if (eAct == MM::AfterSet){
		pProp->Get(c);
		chx_.notchf_=(int)c;
		int ret = SetNotchf(chx_.notchf_);		
		if (ret!=DEVICE_OK)
			return ret;
		//set limit bandwidth to max 2*notch_frequency
		SetPropertyLimits(g_Notch_Band, 0, ((2*chx_.notchf_)<=20000)?(2*chx_.notchf_):20000);			
	}
    return DEVICE_OK;
}
int Stage::OnNotchBand(MM::PropertyBase* pProp, MM::ActionType eAct){	
	double c;
	if (eAct == MM::BeforeGet){			
		int ret = GetNotchb(chx_.notchb_);
		if (ret != DEVICE_OK)
			return ret;
		c=chx_.notchb_;
		pProp->Set(c);
	}else if (eAct == MM::AfterSet){
		pProp->Get(c);
		chx_.notchb_=(int)c;		
		int ret = SetNotchb(chx_.notchb_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Stage::OnLowpass(MM::PropertyBase* pProp, MM::ActionType eAct){
	
	if (eAct == MM::BeforeGet){			
		int ret = GetLpon(chx_.lpon_);		
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.lpon_){
			pProp->Set(g_Lowpass_On);			
		}else{
			pProp->Set(g_Lowpass_Off);			
		}		
	}else if (eAct == MM::AfterSet){
	  std::string lpon;
      pProp->Get(lpon);
	  if (lpon == g_Lowpass_On){
         chx_.lpon_ = true;
	  }else{
         chx_.lpon_ = false;
	  }
	  int ret = SetLpon(chx_.lpon_);	  
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnLowpassFreq(MM::PropertyBase* pProp, MM::ActionType eAct){	
	double c;
	if (eAct == MM::BeforeGet){				
		int ret = GetLpf(chx_.lpf_);
		if (ret != DEVICE_OK)
			return ret;		
		c=chx_.lpf_;
		pProp->Set(c);
	}else if (eAct == MM::AfterSet){
		pProp->Get(c);
		chx_.lpf_=(int)c;		
		int ret = SetLpf(chx_.lpf_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Stage::OnGenerate(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){		
		int i;
		int ret=GetStatus(i, &chx_);
		if (ret!=DEVICE_OK)
			return ret;
		chx_.gfkt_=(chx_.stat_&GENERATOR_OFF_MASK)>>9;		
		switch (chx_.gfkt_){
		case 0:
			pProp->Set(g_Generator_Off);		break;
		case 1:
			pProp->Set(g_Generator_Sine);		break;
		case 2:
			pProp->Set(g_Generator_Tri);		break;
		case 3:
			pProp->Set(g_Generator_Rect);	break;
		case 4:
			pProp->Set(g_Generator_Noise);	break;
		case 5:
			pProp->Set(g_Generator_Sweep);	break;
		default:
			pProp->Set(g_Generator_Off);
		}
	}else if (eAct == MM::AfterSet){		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Generator_Off)
         chx_.gfkt_ = 0;
		else if (gen == g_Generator_Sine)
         chx_.gfkt_ = 1;
		else if (gen == g_Generator_Tri)
         chx_.gfkt_ = 2;
		else if (gen == g_Generator_Rect)
         chx_.gfkt_ = 3;
		else if (gen == g_Generator_Noise)
         chx_.gfkt_ = 4;
		else if (gen == g_Generator_Sweep)
         chx_.gfkt_ = 5;		
		int ret = SetGfkt(chx_.gfkt_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Stage::OnSinAmp(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
	{			
		int ret = GetGasin(chx_.gasin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gasin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gasin_);
		int ret = SetGasin(chx_.gasin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnSinOff(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{		
        ret = GetGosin(chx_.gosin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gosin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gosin_);
		ret = SetGosin(chx_.gosin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnSinFreq(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGfsin(chx_.gfsin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gfsin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gfsin_);
		int ret = SetGfsin(chx_.gfsin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnTriAmp(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetGatri(chx_.gatri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gatri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gatri_);		
		int ret = SetGatri(chx_.gatri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnTriOff(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGotri(chx_.gotri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gotri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gotri_);		
		int ret = SetGotri(chx_.gotri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnTriFreq(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGftri(chx_.gftri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gftri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gftri_);		
		int ret = SetGftri(chx_.gftri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnTriSym(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGstri(chx_.gstri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gstri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gstri_);		
		int ret = SetGstri(chx_.gstri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnRecAmp(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGarec(chx_.garec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.garec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.garec_);		
		int ret = SetGarec(chx_.garec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnRecOff(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGorec(chx_.gorec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gorec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gorec_);		
		int ret = SetGorec(chx_.gorec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnRecFreq(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGfrec(chx_.gfrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gfrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gfrec_);		
		int ret = SetGfrec(chx_.gfrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnRecSym(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGsrec(chx_.gsrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gsrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gsrec_);		
		int ret = SetGsrec(chx_.gsrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnNoiAmp(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetGanoi(chx_.ganoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.ganoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.ganoi_);
		int ret = SetGanoi(chx_.ganoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnNoiOff(MM::PropertyBase* pProp, MM::ActionType eAct){		
	int ret =0;
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGonoi(chx_.gonoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gonoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gonoi_);
		ret = SetGonoi(chx_.gonoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnSweAmp(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGaswe(chx_.gaswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gaswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gaswe_);
		int ret = SetGaswe(chx_.gaswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnSweOff(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSweOff",true);	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGoswe(chx_.goswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.goswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.goswe_);
		int ret = SetGoswe(chx_.goswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnSweTime(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
	{
		int ret = GetGtswe(chx_.gtswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gtswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gtswe_);
		int ret = SetGtswe(chx_.gtswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Stage::OnScanType(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {			
		int ret=GetScanType(chx_.sct_);
		if (ret!=DEVICE_OK)
			return ret;			
		switch (chx_.sct_){
		case 0:
			pProp->Set(g_Scan_Type_Off);break;
		case 1:
			pProp->Set(g_Scan_Type_Sine);		break;
		case 2:
			pProp->Set(g_Scan_Type_Tri);	break;		
		default:
			pProp->Set(g_Scan_Type_Off);
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Scan_Type_Off)
         chx_.sct_ = 0;
		else if (gen == g_Scan_Type_Sine)
         chx_.sct_ = 1;
		else if (gen == g_Scan_Type_Tri)
         chx_.sct_ = 2;		
		int ret = SetScanType(chx_.sct_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Stage::OnScan(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {			
		int ret=GetScan(chx_.ss_);
		if (ret!=DEVICE_OK)
			return ret;			
		if(chx_.ss_){
			pProp->Set(g_Scan_Starting);
		}else{
			pProp->Set(g_Scan_Off);	
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string s;
		pProp->Get(s);
		if (s == g_Scan_Off){
			chx_.ss_ = false;
		}else if (s == g_Scan_Starting){
			chx_.ss_ = true;			
		}
		int ret = SetScan(chx_.ss_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Stage::OnTriggerStart(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){			
		int ret=GetTrgss(chx_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chx_.trgss_);		
	}else if (eAct == MM::AfterSet){		
		pProp->Get(chx_.trgss_);					
		int ret = SetTrgss(chx_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Stage::OnTriggerEnd(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {			
		int ret=GetTrgse(chx_.trgse_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chx_.trgse_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chx_.trgse_);					
		int ret = SetTrgse(chx_.trgse_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
	return DEVICE_OK;
}
int Stage::OnTriggerInterval(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {			
		int ret=GetTrgsi(chx_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chx_.trgsi_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chx_.trgsi_);					
		int ret = SetTrgsi(chx_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Stage::OnTriggerTime(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {			
		int ret=GetTrglen(chx_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set((long)chx_.trglen_);		
	}
    else if (eAct == MM::AfterSet)
    {	
		long l;
		pProp->Get(l);
		chx_.trglen_=(int)l;
		int ret = SetTrglen(chx_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Stage::OnTriggerType(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {			
		int ret=GetTrgedge(chx_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;			
		switch (chx_.trgedge_){
		case 0:
			pProp->Set(g_Trigger_Off);		break;
		case 1:
			pProp->Set(g_Trigger_Rising);	break;
		case 2:
			pProp->Set(g_Trigger_Falling);	break;	
		case 3:
			pProp->Set(g_Trigger_Both);		break;
		default:
			pProp->Set(g_Trigger_Off);
		}	
	}
    else if (eAct == MM::AfterSet)
    {			
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Trigger_Off)
         chx_.trgedge_ = 0;
		else if (gen == g_Trigger_Rising)
         chx_.trgedge_ = 1;
		else if (gen == g_Trigger_Falling)
         chx_.trgedge_ = 2;	
		else if (gen == g_Trigger_Both)
         chx_.trgedge_ = 3;	
		int ret = SetTrgedge(chx_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}

///////////////////////////////////////////////////////////////////////////////
// XYStage
//

/**
 * XYStage - two axis stage device.
 * Note that this adapter uses two coordinate systems.  There is the adapters own coordinate
 * system with the X and Y axis going the 'Micro-Manager standard' direction and step system
 * All functions using um (micrometer),that use the Micro-Manager coordinate system
 */
XYStage::XYStage():

//XYStage::XYStage(int xaxis, int yaxis) :
   EVDBase(this),
   initialized_(false),
   nr_(0),
   stepSizeUm_(0.001),
   xChannel_(0), //(xaxis),
   yChannel_(1) //(yaxis),   
   {
	LogMessage ("Init XYStage()");
   InitializeDefaultErrorMessages();   

   // Name, read-only (RO)
   CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);

   // Description, RO
   CreateProperty(MM::g_Keyword_Description, "PSJ XY stage driver adapter", MM::String, true);

   CPropertyAction*  pAct = new CPropertyAction (this, &XYStage::OnNumber);
   CreateProperty("Device Number", "0", MM::Integer, false, pAct,true);
   AddAllowedValue("Device Number", "0");
   AddAllowedValue("Device Number", "1");
   AddAllowedValue("Device Number", "2");
   AddAllowedValue("Device Number", "3");
}

XYStage::XYStage(int nr):
   EVDBase(this),
   initialized_(false),
   nr_(nr),
   stepSizeUm_(0.001),
   xChannel_(0), //(xaxis),
   yChannel_(1) //(yaxis),2
   
   {
   LogMessage ("Init XYStage");
   InitializeDefaultErrorMessages();
   // Name, read-only (RO)
   CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);

   // Description, RO
   CreateProperty(MM::g_Keyword_Description, "Piezosystem Jena XY stage", MM::String, true);  

   CPropertyAction*  pAct = new CPropertyAction (this, &XYStage::OnChannelX);
   CreateProperty(g_ChannelX, "1", MM::Integer, false, pAct,true);
   if(inventoryDeviceAddresses_.empty()){		
		AddAllowedValue(g_ChannelX, "1");
		AddAllowedValue(g_ChannelX, "2");
		AddAllowedValue(g_ChannelX, "3");
		AddAllowedValue(g_ChannelX, "4");
		AddAllowedValue(g_ChannelX, "5");
		AddAllowedValue(g_ChannelX, "6");
   }else{
		for(std::vector<int>::iterator it=inventoryDeviceAddresses_.begin();it!=inventoryDeviceAddresses_.end();++it){
			AddAllowedValue(g_ChannelX, CDeviceUtils::ConvertToString(((int)*it)+1));
		}
   }   

    pAct = new CPropertyAction (this, &XYStage::OnChannelY);
   CreateProperty(g_ChannelY, "2", MM::Integer, false, pAct,true);
   if(inventoryDeviceAddresses_.empty()){		
		AddAllowedValue(g_ChannelY, "1");
		AddAllowedValue(g_ChannelY, "2");
		AddAllowedValue(g_ChannelY, "3");
		AddAllowedValue(g_ChannelY, "4");
		AddAllowedValue(g_ChannelY, "5");
		AddAllowedValue(g_ChannelY, "6");
   }else{
		for(std::vector<int>::iterator it=inventoryDeviceAddresses_.begin();it!=inventoryDeviceAddresses_.end();++it){
			AddAllowedValue(g_ChannelY, CDeviceUtils::ConvertToString(((int)*it)+1));
		}
   }

	chx_.channel_=xChannel_;	
	chy_.channel_=yChannel_;

	LogMessage ("Init finish");
}

XYStage::XYStage(int nr,int x, int y):
   EVDBase(this),
   initialized_(false),
   nr_(nr),
   stepSizeUm_(0.001),
   xChannel_(x), //(xaxis)
   yChannel_(y) //(yaxis)   
   {
   LogMessage ("Init XYStage");
   InitializeDefaultErrorMessages();
   // Name, read-only (RO)
   CreateProperty(MM::g_Keyword_Name, g_XYStageDeviceName, MM::String, true);

   // Description, RO
   CreateProperty(MM::g_Keyword_Description, "Piezosystem Jena XY stage", MM::String, true);  

   CPropertyAction*  pAct = new CPropertyAction (this, &XYStage::OnChannelX);
   CreateProperty(g_ChannelX, CDeviceUtils::ConvertToString(xChannel_+1), MM::Integer, false, pAct,true);
   if(inventoryDeviceAddresses_.empty()){		
		AddAllowedValue(g_ChannelX, "1");
		AddAllowedValue(g_ChannelX, "2");
		AddAllowedValue(g_ChannelX, "3");
		AddAllowedValue(g_ChannelX, "4");
		AddAllowedValue(g_ChannelX, "5");
		AddAllowedValue(g_ChannelX, "6");
   }else{
		for(std::vector<int>::iterator it=inventoryDeviceAddresses_.begin();it!=inventoryDeviceAddresses_.end();++it){
			AddAllowedValue(g_ChannelX, CDeviceUtils::ConvertToString(((int)*it)+1));
		}
   }   

    pAct = new CPropertyAction (this, &XYStage::OnChannelY);
   CreateProperty(g_ChannelY, CDeviceUtils::ConvertToString(yChannel_+1), MM::Integer, false, pAct,true);
   if(inventoryDeviceAddresses_.empty()){		
		AddAllowedValue(g_ChannelY, "1");
		AddAllowedValue(g_ChannelY, "2");
		AddAllowedValue(g_ChannelY, "3");
		AddAllowedValue(g_ChannelY, "4");
		AddAllowedValue(g_ChannelY, "5");
		AddAllowedValue(g_ChannelY, "6");
   }else{
		for(std::vector<int>::iterator it=inventoryDeviceAddresses_.begin();it!=inventoryDeviceAddresses_.end();++it){
			AddAllowedValue(g_ChannelY, CDeviceUtils::ConvertToString(((int)*it)+1));
		}
   }

	chx_.channel_=xChannel_;	
	chy_.channel_=yChannel_;

	LogMessage ("Init finish");
}

XYStage::~XYStage()
{
   Shutdown();
}
///////////////////////////////////////////////////////////////////////////////
// XYStage methods required by the API
///////////////////////////////////////////////////////////////////////////////

void XYStage::GetName(char* Name) const
{
	std::ostringstream name;
   name<<g_XYStageDeviceName<<(nr_+1);
   CDeviceUtils::CopyLimitedString(Name, name.str().c_str());
}



/**
 * Performs device initialization.
 * Additional properties can be defined here too.
 */
int XYStage::Initialize()
{
    core_ = GetCoreCallback();

   LogMessage ("Initialize",true);
   char c[5];   
   sprintf(c,"%d",xChannel_+1);
   const char* ch=c;   
   CreateProperty(g_ChannelX_, ch, MM::Integer, true);	//read-only 
   sprintf(c,"%d",yChannel_+1);
   ch=c;   
   CreateProperty(g_ChannelY_, ch, MM::Integer, true);  //read-only

   GetLimitsValues(&chx_);
   GetLimitsValues(&chy_);
   CreateProperty(g_Limit_V_MinX, CDeviceUtils::ConvertToString(chx_.min_V_), MM::Float, true);		
   CreateProperty(g_Limit_V_MaxX , CDeviceUtils::ConvertToString(chx_.max_V_), MM::Float, true);			
   CreateProperty(g_Limit_Um_MinX,CDeviceUtils::ConvertToString(chx_.min_um_), MM::Float, true);		
   CreateProperty(g_Limit_Um_MaxX, CDeviceUtils::ConvertToString(chx_.max_um_), MM::Float, true);
   CreateProperty(g_Limit_V_MinY, CDeviceUtils::ConvertToString(chy_.min_V_), MM::Float, true);		
   CreateProperty(g_Limit_V_MaxY , CDeviceUtils::ConvertToString(chy_.max_V_), MM::Float, true);			
   CreateProperty(g_Limit_Um_MinY,CDeviceUtils::ConvertToString(chy_.min_um_), MM::Float, true);		
   CreateProperty(g_Limit_Um_MaxY, CDeviceUtils::ConvertToString(chy_.max_um_), MM::Float, true);
	
   CPropertyAction* pAct = new CPropertyAction (this, &XYStage::OnStatX);
   CreateProperty(g_StatusX, "0", MM::Integer, true, pAct);
   pAct = new CPropertyAction (this, &XYStage::OnStatY);
   CreateProperty(g_StatusY, "0", MM::Integer, true, pAct);
   
   char s[20];	
	int ret = GetCommandValue("rgver",xChannel_,chx_.rgver_);
	if (ret != DEVICE_OK)	  
      return ret;	
	sprintf(s,"%i",chx_.rgver_);	
	CreateProperty(g_RgverX, s, MM::Integer, true);
	ret = GetCommandValue("rgver",yChannel_,chy_.rgver_);
	if (ret != DEVICE_OK)	  
      return ret;	
	sprintf(s,"%i",chy_.rgver_);	
	CreateProperty(g_RgverY, s, MM::Integer, true);
	pAct = new CPropertyAction (this, &XYStage::OnTimeX);
	CreateProperty(g_RohmX, "0", MM::Integer, true, pAct);
	pAct = new CPropertyAction (this, &XYStage::OnTimeY);
	CreateProperty(g_RohmY, "0", MM::Integer, true, pAct);

	pAct = new CPropertyAction (this, &XYStage::OnTempX);
    CreateProperty(g_KtempX, "0", MM::Float, true, pAct);
	pAct = new CPropertyAction (this, &XYStage::OnTempY);
    CreateProperty(g_KtempY, "0", MM::Float, true, pAct);

   pAct = new CPropertyAction (this, &XYStage::OnLoopX);
   CreateProperty(g_LoopX, g_Loop_open, MM::String, false, pAct,false);
   AddAllowedValue(g_LoopX, g_Loop_open);
   AddAllowedValue(g_LoopX, g_Loop_close);
   pAct = new CPropertyAction (this, &XYStage::OnLoopY);
   CreateProperty(g_LoopY, g_Loop_open, MM::String, false, pAct,false);
   AddAllowedValue(g_LoopY, g_Loop_open);
   AddAllowedValue(g_LoopY, g_Loop_close);

	pAct = new CPropertyAction (this, &XYStage::OnSoftstartX);
    CreateProperty(g_FenableX, g_Fenable_Off, MM::String, false, pAct);
    AddAllowedValue(g_FenableX , g_Fenable_Off);
    AddAllowedValue(g_FenableX, g_Fenable_On);
	pAct = new CPropertyAction (this, &XYStage::OnSoftstartY);
    CreateProperty(g_FenableY, g_Fenable_Off, MM::String, false, pAct);
    AddAllowedValue(g_FenableY , g_Fenable_Off);
    AddAllowedValue(g_FenableY, g_Fenable_On);

    pAct = new CPropertyAction (this, &XYStage::OnSlewRateX);
	CreateProperty(g_SrX, "10.0", MM::Float, false, pAct);
	SetPropertyLimits(g_SrX, 0.0000002, 500.0);
	pAct = new CPropertyAction (this, &XYStage::OnSlewRateY);
	CreateProperty(g_SrY, "10.0", MM::Float, false, pAct);
	SetPropertyLimits(g_SrY, 0.0000002, 500.0);

    pAct = new CPropertyAction (this, &XYStage::OnPositionX);
    CreateProperty(g_PositionX, "0.001", MM::Float, false, pAct);
    pAct = new CPropertyAction (this, &XYStage::OnPositionY);
    CreateProperty(g_PositionY, "0.001", MM::Float, false, pAct);

	pAct = new CPropertyAction (this, &XYStage::OnModulInputX);
    CreateProperty(g_ModonX, g_Modon_Off, MM::String, false, pAct);
    AddAllowedValue(g_ModonX, g_Modon_Off);
    AddAllowedValue(g_ModonX, g_Modon_On);
	pAct = new CPropertyAction (this, &XYStage::OnModulInputY);
    CreateProperty(g_ModonY, g_Modon_Off, MM::String, false, pAct);
    AddAllowedValue(g_ModonY, g_Modon_Off);
    AddAllowedValue(g_ModonY, g_Modon_On);

	pAct = new CPropertyAction (this, &XYStage::OnMonitorX);
    CreateProperty(g_MonsrcX, g_Monsrc_0, MM::String, false, pAct);
    AddAllowedValue(g_MonsrcX, g_Monsrc_0);
    AddAllowedValue(g_MonsrcX, g_Monsrc_1);
	AddAllowedValue(g_MonsrcX, g_Monsrc_2);
	AddAllowedValue(g_MonsrcX, g_Monsrc_3);
	AddAllowedValue(g_MonsrcX, g_Monsrc_4);
	AddAllowedValue(g_MonsrcX, g_Monsrc_5);
	AddAllowedValue(g_MonsrcX, g_Monsrc_6);

	pAct = new CPropertyAction (this, &XYStage::OnMonitorY);
    CreateProperty(g_MonsrcY, g_Monsrc_0, MM::String, false, pAct);
    AddAllowedValue(g_MonsrcY, g_Monsrc_0);
    AddAllowedValue(g_MonsrcY, g_Monsrc_1);
	AddAllowedValue(g_MonsrcY, g_Monsrc_2);
	AddAllowedValue(g_MonsrcY, g_Monsrc_3);
	AddAllowedValue(g_MonsrcY, g_Monsrc_4);
	AddAllowedValue(g_MonsrcY, g_Monsrc_5);
	AddAllowedValue(g_MonsrcY, g_Monsrc_6);

	pAct = new CPropertyAction (this, &XYStage::OnPidPX);
	CreateProperty(g_PID_PX, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_PID_PX, 0.0, 999.0);
	pAct = new CPropertyAction (this, &XYStage::OnPidIX);
	CreateProperty(g_PID_IX, "0.0", MM::Float, false, pAct);
    SetPropertyLimits(g_PID_IX, 0.0, 999.0);
	pAct = new CPropertyAction (this, &XYStage::OnPidDX);
	CreateProperty(g_PID_DX, "0.0", MM::Float, false, pAct);
    SetPropertyLimits(g_PID_DX, 0.0, 999.0);

    pAct = new CPropertyAction (this, &XYStage::OnPidPY);
	CreateProperty(g_PID_PY, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_PID_PY, 0.0, 999.0);
	pAct = new CPropertyAction (this, &XYStage::OnPidIY);
	CreateProperty(g_PID_IY, "0.0", MM::Float, false, pAct);
    SetPropertyLimits(g_PID_IY, 0.0, 999.0);
	pAct = new CPropertyAction (this, &XYStage::OnPidDY);
	CreateProperty(g_PID_DY, "0.0", MM::Float, false, pAct);
    SetPropertyLimits(g_PID_DY, 0.0, 999.0);

	//Notch Filter
    pAct = new CPropertyAction (this, &XYStage::OnNotchX);
    CreateProperty(g_NotchX, g_Notch_Off, MM::String, false, pAct);
    AddAllowedValue(g_NotchX, g_Notch_Off);
    AddAllowedValue(g_NotchX, g_Notch_On);
    pAct = new CPropertyAction (this, &XYStage::OnNotchY);
    CreateProperty(g_NotchY, g_Notch_Off, MM::String, false, pAct);
    AddAllowedValue(g_NotchY, g_Notch_Off);
    AddAllowedValue(g_NotchY, g_Notch_On);
    pAct = new CPropertyAction (this, &XYStage::OnNotchFreqX);
	CreateProperty(g_Notch_FreqX, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Notch_FreqX, 0, 20000);
	pAct = new CPropertyAction (this, &XYStage::OnNotchFreqY);
	CreateProperty(g_Notch_FreqY, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Notch_FreqY, 0, 20000);
    pAct = new CPropertyAction (this, &XYStage::OnNotchBandX);
	CreateProperty(g_Notch_BandX, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Notch_BandX, 0, 20000);
    pAct = new CPropertyAction (this, &XYStage::OnNotchBandY);
	CreateProperty(g_Notch_BandY, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Notch_BandY, 0, 20000);
	//Low pass filter
    pAct = new CPropertyAction (this, &XYStage::OnLowpassX);
    CreateProperty(g_LowpassX, g_Lowpass_Off, MM::String, false, pAct);
    AddAllowedValue(g_LowpassX, g_Lowpass_Off);
    AddAllowedValue(g_LowpassX, g_Lowpass_On);
    pAct = new CPropertyAction (this, &XYStage::OnLowpassY);
    CreateProperty(g_LowpassY, g_Lowpass_Off, MM::String, false, pAct);
    AddAllowedValue(g_LowpassY, g_Lowpass_Off);
    AddAllowedValue(g_LowpassY, g_Lowpass_On);
	pAct = new CPropertyAction (this, &XYStage::OnLowpassFreqX);
	CreateProperty(g_Lowpass_FreqX, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Lowpass_FreqX, 0, 20000);
    pAct = new CPropertyAction (this, &XYStage::OnLowpassFreqY);
	CreateProperty(g_Lowpass_FreqY, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Lowpass_FreqY, 0, 20000);

	//Internal function generator
	chx_.gfkt_=0;
	pAct = new CPropertyAction (this, &XYStage::OnGenerateX);
	CreateProperty(g_GeneratorX, g_Generator_Off, MM::String, false, pAct);
	AddAllowedValue(g_GeneratorX, g_Generator_Off);
	AddAllowedValue(g_GeneratorX, g_Generator_Sine);
	AddAllowedValue(g_GeneratorX, g_Generator_Tri);
	AddAllowedValue(g_GeneratorX, g_Generator_Rect);
	AddAllowedValue(g_GeneratorX, g_Generator_Noise);
	AddAllowedValue(g_GeneratorX, g_Generator_Sweep);
	 chy_.gfkt_=0;
	pAct = new CPropertyAction (this, &XYStage::OnGenerateY);
	CreateProperty(g_GeneratorY, g_Generator_Off, MM::String, false, pAct);
	AddAllowedValue(g_GeneratorY, g_Generator_Off);
	AddAllowedValue(g_GeneratorY, g_Generator_Sine);
	AddAllowedValue(g_GeneratorY, g_Generator_Tri);
	AddAllowedValue(g_GeneratorY, g_Generator_Rect);
	AddAllowedValue(g_GeneratorY, g_Generator_Noise);
	AddAllowedValue(g_GeneratorY, g_Generator_Sweep);
	
	//Sine
	pAct = new CPropertyAction (this, &XYStage::OnSinAmpX);
	CreateProperty(g_Generator_Sine_AmpX, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_AmpX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &XYStage::OnSinAmpY);
	CreateProperty(g_Generator_Sine_AmpY, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_AmpY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &XYStage::OnSinOffX);
	CreateProperty(g_Generator_Sine_OffsetX, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_OffsetX, 0.0, 100.0);
    pAct = new CPropertyAction (this, &XYStage::OnSinOffY);
	CreateProperty(g_Generator_Sine_OffsetY, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_OffsetY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &XYStage::OnSinFreqX);
	CreateProperty(g_Generator_Sine_FreqX, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_FreqX, 0.00001, 9999.9);
	pAct = new CPropertyAction (this, &XYStage::OnSinFreqY);
	CreateProperty(g_Generator_Sine_FreqY, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_FreqY, 0.00001, 9999.9);
	//triangle
	pAct = new CPropertyAction (this, &XYStage::OnTriAmpX);
	CreateProperty(g_Generator_Tri_AmpX, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_AmpX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &XYStage::OnTriAmpY);
	CreateProperty(g_Generator_Tri_AmpY, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_AmpY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &XYStage::OnTriOffX);
	CreateProperty(g_Generator_Tri_OffsetX, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Tri_OffsetX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &XYStage::OnTriOffY);
	CreateProperty(g_Generator_Tri_OffsetY, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Tri_OffsetY, 0.0, 100.0);
    pAct = new CPropertyAction (this, &XYStage::OnTriFreqX);
	CreateProperty(g_Generator_Tri_FreqX, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_FreqX, 0.00001, 9999.9);
	pAct = new CPropertyAction (this, &XYStage::OnTriFreqY);
	CreateProperty(g_Generator_Tri_FreqY, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_FreqY, 0.00001, 9999.9);
    pAct = new CPropertyAction (this, &XYStage::OnTriSymX);
	CreateProperty(g_Generator_Tri_SymX, "50.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_SymX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &XYStage::OnTriSymY);
	CreateProperty(g_Generator_Tri_SymY, "50.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_SymY, 0.0, 100.0);
	//rectangle
	pAct = new CPropertyAction (this, &XYStage::OnRecAmpX);
	CreateProperty(g_Generator_Rect_AmpX, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_AmpX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &XYStage::OnRecAmpY);
	CreateProperty(g_Generator_Rect_AmpY, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_AmpY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &XYStage::OnRecOffX);
	CreateProperty(g_Generator_Rect_OffsetX, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_OffsetX, 0.0, 100.0);
    pAct = new CPropertyAction (this, &XYStage::OnRecOffY);
	CreateProperty(g_Generator_Rect_OffsetY, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_OffsetY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &XYStage::OnRecFreqX);
	CreateProperty(g_Generator_Rect_FreqX, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_FreqX, 0.00001, 9999.9);
	pAct = new CPropertyAction (this, &XYStage::OnRecFreqY);
	CreateProperty(g_Generator_Rect_FreqY, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_FreqY, 0.00001, 9999.9);
	pAct = new CPropertyAction (this, &XYStage::OnRecSymX);
	CreateProperty(g_Generator_Rect_SymX, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_SymX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &XYStage::OnRecSymY);
	CreateProperty(g_Generator_Rect_SymY, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_SymY, 0.0, 100.0);
	//Noise
	pAct = new CPropertyAction (this, &XYStage::OnNoiAmpX);
	CreateProperty(g_Generator_Noise_AmpX, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Noise_AmpX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &XYStage::OnNoiAmpY);
	CreateProperty(g_Generator_Noise_AmpY, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Noise_AmpY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &XYStage::OnNoiOffX);
	CreateProperty(g_Generator_Noise_OffsetX, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Noise_OffsetX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &XYStage::OnNoiOffY);
	CreateProperty(g_Generator_Noise_OffsetY, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Noise_OffsetY, 0.0, 100.0);
	//Sweep
    pAct = new CPropertyAction (this, &XYStage::OnSweAmpX);
	CreateProperty(g_Generator_Sweep_AmpX, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sweep_AmpX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &XYStage::OnSweAmpY);
	CreateProperty(g_Generator_Sweep_AmpY, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sweep_AmpY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &XYStage::OnSweOffX);
	CreateProperty(g_Generator_Sweep_OffsetX, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_OffsetX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &XYStage::OnSweOffY);
	CreateProperty(g_Generator_Sweep_OffsetY, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_OffsetY, 0.0, 100.0);
    pAct = new CPropertyAction (this, &XYStage::OnSweTimeX);
	CreateProperty(g_Generator_Sweep_TimeX, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_TimeX, 0.4, 800.0);
	pAct = new CPropertyAction (this, &XYStage::OnSweTimeY);
	CreateProperty(g_Generator_Sweep_TimeY, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_TimeY, 0.4, 800.0);
	
	//Scan
	pAct = new CPropertyAction (this, &XYStage::OnScanTypeX);
	CreateProperty(g_Scan_TypeX, g_Scan_Type_Off, MM::String, false, pAct);
	AddAllowedValue(g_Scan_TypeX, g_Scan_Type_Off);
	AddAllowedValue(g_Scan_TypeX, g_Scan_Type_Sine);
	AddAllowedValue(g_Scan_TypeX, g_Scan_Type_Tri);
	pAct = new CPropertyAction (this, &XYStage::OnScanTypeY);
	CreateProperty(g_Scan_TypeY, g_Scan_Type_Off, MM::String, false, pAct);
	AddAllowedValue(g_Scan_TypeY, g_Scan_Type_Off);
	AddAllowedValue(g_Scan_TypeY, g_Scan_Type_Sine);
	AddAllowedValue(g_Scan_TypeY, g_Scan_Type_Tri);

	 pAct = new CPropertyAction (this, &XYStage::OnScanX);
	 CreateProperty(g_Scan_StartX, g_Scan_Off, MM::String, false, pAct);
    AddAllowedValue(g_Scan_StartX, g_Scan_Off);
    AddAllowedValue(g_Scan_StartX, g_Scan_Starting);
	pAct = new CPropertyAction (this, &XYStage::OnScanY);
	 CreateProperty(g_Scan_StartY, g_Scan_Off, MM::String, false, pAct);
    AddAllowedValue(g_Scan_StartY, g_Scan_Off);
    AddAllowedValue(g_Scan_StartY, g_Scan_Starting);

	//trigger
    pAct = new CPropertyAction (this, &XYStage::OnTriggerStartX);
	CreateProperty(g_Trigger_StartX, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Trigger_StartX, chx_.max_um_*0.002, chx_.max_um_*0.998);
	pAct = new CPropertyAction (this, &XYStage::OnTriggerStartY);
	CreateProperty(g_Trigger_StartY, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Trigger_StartY, chy_.max_um_*0.002, chy_.max_um_*0.998);
	pAct = new CPropertyAction (this, &XYStage::OnTriggerEndX);
	CreateProperty(g_Trigger_EndX, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_EndX, chx_.max_um_*0.002, chx_.max_um_*0.998);
	pAct = new CPropertyAction (this, &XYStage::OnTriggerEndY);
	CreateProperty(g_Trigger_EndY, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_EndY, chy_.max_um_*0.002, chy_.max_um_*0.998);
    pAct = new CPropertyAction (this, &XYStage::OnTriggerIntervalX);
	CreateProperty(g_Trigger_IntervalX, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_IntervalX, chx_.min_um_, chx_.max_um_);
	pAct = new CPropertyAction (this, &XYStage::OnTriggerIntervalY);
	CreateProperty(g_Trigger_IntervalY, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_IntervalY, chy_.min_um_, chy_.max_um_);
	pAct = new CPropertyAction (this, &XYStage::OnTriggerTimeX);
	CreateProperty(g_Trigger_TimeX, "1", MM::Integer, false, pAct);
    SetPropertyLimits(g_Trigger_TimeX, 1, 255);
    pAct = new CPropertyAction (this, &XYStage::OnTriggerTimeY);
	CreateProperty(g_Trigger_TimeY, "1", MM::Integer, false, pAct);
    SetPropertyLimits(g_Trigger_TimeY, 1, 255);
    pAct = new CPropertyAction (this, &XYStage::OnTriggerTypeX);
	CreateProperty(g_Trigger_GeneratorX, g_Trigger_Off, MM::String, false, pAct);
	AddAllowedValue(g_Trigger_GeneratorX, g_Trigger_Off);
	AddAllowedValue(g_Trigger_GeneratorX, g_Trigger_Rising);
	AddAllowedValue(g_Trigger_GeneratorX, g_Trigger_Falling);
	AddAllowedValue(g_Trigger_GeneratorX, g_Trigger_Both);
	pAct = new CPropertyAction (this, &XYStage::OnTriggerTypeY);
	CreateProperty(g_Trigger_GeneratorY, g_Trigger_Off, MM::String, false, pAct);
	AddAllowedValue(g_Trigger_GeneratorY, g_Trigger_Off);
	AddAllowedValue(g_Trigger_GeneratorY, g_Trigger_Rising);
	AddAllowedValue(g_Trigger_GeneratorY, g_Trigger_Falling);
	AddAllowedValue(g_Trigger_GeneratorY, g_Trigger_Both);

   ret = UpdateStatus();
   if (ret != DEVICE_OK)
      return ret;
   
   initialized_ = true;
   return DEVICE_OK;
}
int XYStage::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

int XYStage::GetLimitsUm(double& xMin, double& xMax, double& yMin, double& yMax){	
	xMin=chx_.min_um_;
	xMax=chx_.max_um_;
	yMin=chy_.min_um_;
	yMax=chy_.max_um_;
	return DEVICE_OK;
}

int XYStage::GetStepLimits(long& /*xMin*/, long& /*xMax*/, long& /*yMin*/, long& /*yMax*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}
int XYStage::SetPositionUm(double x, double y){	
	int ret;	
	GetLoop(chx_.loop_,xChannel_);
	if(chx_.loop_){
		chx_.pos_=x;
		ret  =SetCommandValue("set",xChannel_,x);
	}else{
		chx_.pos_=x;
		chx_.voltage_=(chx_.max_V_-chx_.min_V_)*(x-chx_.min_um_)/(chx_.max_um_-chx_.min_um_)+chx_.min_V_;
		ret = SetCommandValue("set",xChannel_,chx_.voltage_);
	}
	if (ret != DEVICE_OK)
      return ret;	
	GetLoop(chy_.loop_,yChannel_);
	if(chy_.loop_){
		chy_.pos_=y;
		ret = SetCommandValue("set",yChannel_,y);
	}else{
		chy_.pos_=y;
		chy_.voltage_=(chy_.max_V_-chy_.min_V_)*(y-chy_.min_um_)/(chy_.max_um_-chy_.min_um_)+chy_.min_V_;
		ret = SetCommandValue("set",yChannel_,chy_.voltage_);
	}	
	if (ret != DEVICE_OK)
      return ret;

	return DEVICE_OK;
}

int XYStage::GetPositionUm(double& x, double& y){	
	
	GetLoop(chx_.loop_,xChannel_);
	if(chx_.loop_){
		GetCommandValue("mess",xChannel_,x);
		chx_.pos_=x;
		chx_.voltage_=(chx_.max_V_-chx_.min_V_)*(chx_.pos_-chx_.min_um_)/(chx_.max_um_-chx_.min_um_)+chx_.min_V_;
	}else{
		GetCommandValue("mess",xChannel_,chx_.voltage_);
		chx_.pos_=(chx_.max_um_-chx_.min_um_)*(chx_.voltage_-chx_.min_V_)/(chx_.max_V_-chx_.min_V_)+chx_.min_um_;
		x=chx_.pos_;
	}
	GetLoop(chy_.loop_,yChannel_);
	if(chy_.loop_){
		GetCommandValue("mess",yChannel_,y);
		chy_.pos_=y;
		chy_.voltage_=(chy_.max_V_-chy_.min_V_)*(chy_.pos_-chy_.min_um_)/(chy_.max_um_-chy_.min_um_)+chy_.min_V_;
	}else{
		GetCommandValue("mess",yChannel_,chy_.voltage_);
		chy_.pos_=(chy_.max_um_-chy_.min_um_)*(chy_.voltage_-chy_.min_V_)/(chy_.max_V_-chy_.min_V_)+chy_.min_um_;
		y=chy_.pos_;
	}
	return DEVICE_OK;
}

int XYStage::SetRelativePositionUm(double x, double y){	
	double oldx,oldy;	
	GetPositionUm(oldx,oldy);	
	SetPositionUm(oldx+x,oldy+y);
	return DEVICE_OK;
}
int XYStage::SetPositionSteps(long x, long y)
{	
	xStep_ = x;
	yStep_ = y;
	double xum=xStep_* stepSizeUm_;		
	double yum=yStep_* stepSizeUm_;	
	SetPositionUm(xum,yum);
	return DEVICE_OK;
}
int XYStage::GetPositionSteps(long& x, long& y)
{	
	double xum;
	double yum;	
	GetPositionUm(xum,yum);	
	xStep_=(long)floor(xum/stepSizeUm_);		
	yStep_=(long)floor(yum/stepSizeUm_);
	x=xStep_;
	y=yStep_;
	return DEVICE_OK;
}
/**
 * Sets relative position in steps.
 */
int XYStage::SetRelativePositionSteps(long /*x*/, long /*y*/)
{
 return DEVICE_UNSUPPORTED_COMMAND;
}
int XYStage::SetOrigin()
{
	return DEVICE_UNSUPPORTED_COMMAND;
}
int XYStage::Home()
{
	return DEVICE_UNSUPPORTED_COMMAND;
}
int XYStage::Stop()
{	
	return DEVICE_UNSUPPORTED_COMMAND;
}
int XYStage::OnTempX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("ktemp",xChannel_,chx_.ktemp_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set(chx_.ktemp_);
	}
	return DEVICE_OK;
}
int XYStage::OnTempY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){			
		int ret = GetCommandValue("ktemp",yChannel_,chy_.ktemp_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set(chy_.ktemp_);
	}
	return DEVICE_OK;
}
int XYStage::OnTimeX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("rohm",xChannel_,chx_.rohm_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set((long)chx_.rohm_);
	}
	return DEVICE_OK;
}
int XYStage::OnTimeY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("rohm",yChannel_,chy_.rohm_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set((long)chy_.rohm_);
	}
	return DEVICE_OK;
}
int XYStage::OnSoftstartX(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet){	
		int b=0;		
		int ret = GetCommandValue("fenable",chx_.channel_,b);
		chx_.fenable_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.fenable_){
			pProp->Set(g_Fenable_On);			
		}
		else{
			pProp->Set(g_Fenable_Off);			
		}		
	}
    else if (eAct == MM::AfterSet){	  
		std::string softstart;
		int b=0;
		pProp->Get(softstart);
		if (softstart == g_Fenable_On){
			chx_.fenable_ = true;
			b=1;
		}else{
			chx_.fenable_ = false;
			b=0;
		}		
		int ret = SetCommandValue("fenable",chx_.channel_,b);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnSoftstartY(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet){	
		int b=0;		
		int ret = GetCommandValue("fenable",chy_.channel_,b);
		chy_.fenable_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chy_.fenable_){
			pProp->Set(g_Fenable_On);			
		}
		else{
			pProp->Set(g_Fenable_Off);			
		}		
	}
    else if (eAct == MM::AfterSet){	  
		std::string softstart;
		int b=0;
		pProp->Get(softstart);
		if (softstart == g_Fenable_On){
			chy_.fenable_ = true;
			b=1;
		}else{
			chy_.fenable_ = false;
			b=0;
		}		
		int ret = SetCommandValue("fenable",chy_.channel_,b);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnNumber(MM::PropertyBase* pProp, MM::ActionType eAct){	
	double c;
	if (eAct == MM::BeforeGet)
    {		
		c=nr_;
		pProp->Set(c);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(c);
		nr_=(int)c;
	}
    return DEVICE_OK;
}
int XYStage::OnStatX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int s;
	if (eAct == MM::BeforeGet)
    {	
		GetStatus(s,&chx_);	
		chx_.stat_=s;
		pProp->Set((long)s);		
	}   
    return DEVICE_OK;
}
int XYStage::OnStatY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int s;
	if (eAct == MM::BeforeGet)
    {	
		GetStatus(s,&chy_);
		chy_.stat_=s;
		pProp->Set((long)s);		
	}   
    return DEVICE_OK;
}
int XYStage::OnChannelX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	long c;
	if (eAct == MM::BeforeGet)
    {			
		c=xChannel_+1;
		pProp->Set(c);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(c);
		xChannel_=(int)c-1;
		chx_.channel_=xChannel_;
	}
    return DEVICE_OK;
}
int XYStage::OnChannelY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	double c;
	if (eAct == MM::BeforeGet)
    {			
		c=yChannel_+1;
		pProp->Set(c);		
	}
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(c);
		yChannel_=(int)c-1;
		chy_.channel_=yChannel_;
	}
    return DEVICE_OK;
}
int XYStage::OnPositionX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){	
		int ret = GetPositionUm(chx_.pos_,chy_.pos_);
		if (ret!=DEVICE_OK)
			return ret;		
		pProp->Set(chx_.pos_);
	}
    else if (eAct == MM::AfterSet){
		int ret=0;
		pProp->Get(chx_.pos_);		
		ret = SetPositionUm(chx_.pos_,chy_.pos_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnPositionY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){	
		int ret = GetPositionUm(chx_.pos_,chy_.pos_);
		if (ret!=DEVICE_OK)
			return ret;		
		pProp->Set(chy_.pos_);
	}
    else if (eAct == MM::AfterSet){
		int ret=0;
		pProp->Get(chy_.pos_);		
		ret = SetPositionUm(chx_.pos_,chy_.pos_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnStepSize(MM::PropertyBase* pProp, MM::ActionType eAct){	
	double c;
	if (eAct == MM::BeforeGet)
    {		
		c=stepSizeUm_;
		pProp->Set(c);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(c);
		if(c > 0)
			stepSizeUm_=c;
	}
    return DEVICE_OK;
}
int XYStage::OnStepX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	double c;
	if (eAct == MM::BeforeGet)
    {		
		GetPositionSteps(xStep_,yStep_);
		c=xStep_;
		pProp->Set(c);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(c);
		xStep_=(int)c;
		SetPositionSteps(xStep_,yStep_);
	}
    return DEVICE_OK;
}
int XYStage::OnStepY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	double c;
	if (eAct == MM::BeforeGet)
    {		
		GetPositionSteps(xStep_,yStep_);
		c=yStep_;
		pProp->Set(c);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(c);
		yStep_=(int)c;
		SetPositionSteps(xStep_,yStep_);
	}
    return DEVICE_OK;
}
int XYStage::OnLoopX(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
    {		
		int ret=GetLoop(chx_.loop_,chx_.channel_);
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.loop_){
			pProp->Set(g_Loop_close);			
		}else{
			pProp->Set(g_Loop_open);			
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string loop;
      pProp->Get(loop);
	  if (loop == g_Loop_close){
         chx_.loop_ = true;
	  }else{
         chx_.loop_ = false;
	  }
	  int ret = SetLoop(chx_.loop_,chx_.channel_);
	  if (ret!=DEVICE_OK)
			return ret;
	  CDeviceUtils::SleepMs(200);
	}
    return DEVICE_OK;
}
int XYStage::OnLoopY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int l=0;
	if (eAct == MM::BeforeGet)
    {		
		int ret=GetLoop(chy_.loop_,chy_.channel_);
		if (ret!=DEVICE_OK)
			return ret;
		if (chy_.loop_){
			pProp->Set(g_Loop_close);
			l=1;
		}else{
			pProp->Set(g_Loop_open);
			l=0;
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string loop;
      pProp->Get(loop);
	  if (loop == g_Loop_close){
         chy_.loop_ = true;
	  }else{
         chy_.loop_ = false;
	  }
	  int ret = SetLoop(chy_.loop_,chy_.channel_);
	  if (ret!=DEVICE_OK)
			return ret;
	  CDeviceUtils::SleepMs(200);	
	}
    return DEVICE_OK;
}
int XYStage::OnSlewRateX(MM::PropertyBase* pProp, MM::ActionType eAct){		
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		//ret = GetSr(sr_);
		ret = GetCommandValue("sr",chx_.channel_,chx_.sr_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)chx_.sr_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.sr_);		
		int ret = SetCommandValue("sr",chx_.channel_,chx_.sr_);
		//ret = SetSr(sr_);
		if (ret != DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int XYStage::OnSlewRateY(MM::PropertyBase* pProp, MM::ActionType eAct){			
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		ret = GetCommandValue("sr",chy_.channel_,chy_.sr_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)chy_.sr_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.sr_);		
		ret = SetCommandValue("sr",chy_.channel_,chy_.sr_);		
		if (ret != DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int XYStage::OnModulInputX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int l=0;
	if (eAct == MM::BeforeGet)
    {			
		int ret = GetCommandValue("modon",xChannel_,l);
		chx_.modon_=(l==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.modon_){
			pProp->Set(g_Modon_On);			
		}
		else{
			pProp->Set(g_Modon_Off);			
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string modon;
      pProp->Get(modon);
	  if (modon == g_Modon_On){
         chx_.modon_ = true;
	  }else{
         chx_.modon_ = false;
	  }	  
	  l=(chx_.modon_)?1:0;
	  int ret = SetCommandValue("modon",xChannel_,l);	  
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnModulInputY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int l=0;
	if (eAct == MM::BeforeGet)
    {				
		int ret = GetCommandValue("modon",yChannel_,l);
		chy_.modon_=(l==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chy_.modon_){
			pProp->Set(g_Modon_On);			
		}else{
			pProp->Set(g_Modon_Off);			
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string modon;
      pProp->Get(modon);
	  if (modon == g_Modon_On){
         chy_.modon_ = true;
	  }else{
         chy_.modon_ = false;
	  }	  
	  l=(chy_.modon_)?1:0;
	  int ret = SetCommandValue("modon",yChannel_,l);	  
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnMonitorX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {			
		int ret = GetCommandValue("monsrc",xChannel_,chx_.monsrc_);
		if (ret!=DEVICE_OK)
			return ret;				
		switch (chx_.monsrc_){
		case 0:
			pProp->Set(g_Monsrc_0);	break;
		case 1:
			pProp->Set(g_Monsrc_1);	break;
		case 2:
			pProp->Set(g_Monsrc_2);	break;
		case 3:
			pProp->Set(g_Monsrc_3);	break;
		case 4:
			pProp->Set(g_Monsrc_4);	break;
		case 5:
			pProp->Set(g_Monsrc_5);	break;
		case 6:
			pProp->Set(g_Monsrc_6);	break;
		default:
			pProp->Set(g_Monsrc_0);
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string mon;
		pProp->Get(mon);
		if (mon == g_Monsrc_0)
         chx_.monsrc_ = 0;
		else if (mon == g_Monsrc_1)
         chx_.monsrc_ = 1;
		else if (mon == g_Monsrc_2)
         chx_.monsrc_ = 2;
		else if (mon == g_Monsrc_3)
         chx_.monsrc_ = 3;
		else if (mon == g_Monsrc_4)
         chx_.monsrc_ = 4;
		else if (mon == g_Monsrc_5)
         chx_.monsrc_ = 5;	
		else if (mon == g_Monsrc_6)
         chx_.monsrc_ = 6;			
		int ret = SetCommandValue("monsrc",xChannel_,chx_.monsrc_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int XYStage::OnMonitorY(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
    {			
		int ret = GetCommandValue("monsrc",yChannel_,chy_.monsrc_);
		if (ret!=DEVICE_OK)
			return ret;				
		switch (chy_.monsrc_){
		case 0:
			pProp->Set(g_Monsrc_0);	break;
		case 1:
			pProp->Set(g_Monsrc_1);	break;
		case 2:
			pProp->Set(g_Monsrc_2);	break;
		case 3:
			pProp->Set(g_Monsrc_3);	break;
		case 4:
			pProp->Set(g_Monsrc_4);	break;
		case 5:
			pProp->Set(g_Monsrc_5);	break;
		case 6:
			pProp->Set(g_Monsrc_6);	break;
		default:
			pProp->Set(g_Monsrc_0);
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string mon;
		pProp->Get(mon);
		if (mon == g_Monsrc_0)
         chy_.monsrc_ = 0;
		else if (mon == g_Monsrc_1)
         chy_.monsrc_ = 1;
		else if (mon == g_Monsrc_2)
         chy_.monsrc_ = 2;
		else if (mon == g_Monsrc_3)
         chy_.monsrc_ = 3;
		else if (mon == g_Monsrc_4)
         chy_.monsrc_ = 4;
		else if (mon == g_Monsrc_5)
         chy_.monsrc_ = 5;	
		else if (mon == g_Monsrc_6)
         chy_.monsrc_ = 6;			
		int ret = SetCommandValue("monsrc",yChannel_,chy_.monsrc_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int XYStage::OnPidPX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		ret = GetCommandValue("kp",xChannel_,chx_.kp_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.kp_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.kp_);			
		ret = SetCommandValue("kp",xChannel_,chx_.kp_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnPidPY(MM::PropertyBase* pProp, MM::ActionType eAct){		
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		ret = GetCommandValue("kp",yChannel_,chy_.kp_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.kp_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.kp_);		
		ret = SetCommandValue("kp",yChannel_,chy_.kp_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnPidIX(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		ret = GetCommandValue("ki",xChannel_,chx_.ki_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.ki_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.ki_);			
		ret = SetCommandValue("ki",xChannel_,chx_.ki_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnPidIY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		ret = GetCommandValue("ki",yChannel_,chy_.ki_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.ki_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.ki_);			
		ret = SetCommandValue("ki",yChannel_,chy_.ki_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnPidDX(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		ret = GetCommandValue("kd",xChannel_,chx_.kd_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.kd_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.kd_);		
		ret = SetCommandValue("kd",xChannel_,chx_.kd_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnPidDY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{				
		ret = GetCommandValue("kd",yChannel_,chy_.kd_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.kd_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.kd_);		
		ret = SetCommandValue("kd",yChannel_,chy_.kd_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnNotchX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int b=0;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("notchon",xChannel_,b);
		chx_.notchon_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.notchon_){
			pProp->Set(g_Notch_On);			
		}else{
			pProp->Set(g_Notch_Off);			
		}		
	}else if (eAct == MM::AfterSet){	  
	  std::string notch;
      pProp->Get(notch);
	  if (notch == g_Notch_On){
         chx_.notchon_ = true;
	  }else{
         chx_.notchon_ = false;
	  }	 
	  b=(chx_.kd_)?1:0;
	  int ret = SetCommandValue("notchon",xChannel_,b);
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnNotchY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int b=0;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("notchon",yChannel_,b);
		chy_.notchon_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.notchon_){
			pProp->Set(g_Notch_On);			
		}else{
			pProp->Set(g_Notch_Off);			
		}		
	}else if (eAct == MM::AfterSet){	  
	  std::string notch;
      pProp->Get(notch);
	  if (notch == g_Notch_On){
         chy_.notchon_ = true;
	  }else{
         chy_.notchon_ = false;
	  }	 
	  b=(chy_.kd_)?1:0;
	  int ret = SetCommandValue("notchon",yChannel_,b);
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnNotchFreqX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	long l;
	if (eAct == MM::BeforeGet){				
		int ret = GetCommandValue("notchf",xChannel_,chx_.notchf_);
		if (ret != DEVICE_OK)
			return ret;
		SetPropertyLimits(g_Notch_BandX, 0, ((2*chx_.notchf_)<=20000)?(2*chx_.notchf_):20000);
		l=chx_.notchf_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chx_.notchf_=(int)l;		
		int ret = SetCommandValue("notchf",xChannel_,chx_.notchf_);
		if (ret!=DEVICE_OK)
			return ret;
		//set limit bandwidth to max 2*notch_frequency
		SetPropertyLimits(g_Notch_BandX, 0, ((2*chx_.notchf_)<=20000)?(2*chx_.notchf_):20000);			
	}
    return DEVICE_OK;
}
int XYStage::OnNotchFreqY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	long l;
	if (eAct == MM::BeforeGet){			
		int ret = GetCommandValue("notchf",yChannel_,chy_.notchf_);
		if (ret != DEVICE_OK)
			return ret;
		SetPropertyLimits(g_Notch_BandY, 0, ((2*chy_.notchf_)<=20000)?(2*chy_.notchf_):20000);
		l=chy_.notchf_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chy_.notchf_=(int)l;		
		int ret = SetCommandValue("notchf",yChannel_,chy_.notchf_);
		if (ret!=DEVICE_OK)
			return ret;
		//set limit bandwidth to max 2*notch_frequency
		SetPropertyLimits(g_Notch_BandY, 0, ((2*chy_.notchf_)<=20000)?(2*chy_.notchf_):20000);			
	}
    return DEVICE_OK;
}
int XYStage::OnNotchBandX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("notchb",xChannel_,chx_.notchb_);		
		if (ret != DEVICE_OK)
			return ret;
		l=chx_.notchb_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chx_.notchb_=(int)l;
		int ret = SetCommandValue("notchb",xChannel_,chx_.notchb_);		
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int XYStage::OnNotchBandY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("notchb",yChannel_,chy_.notchb_);		
		if (ret != DEVICE_OK)
			return ret;
		l=chy_.notchb_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chy_.notchb_=(int)l;
		int ret = SetCommandValue("notchb",yChannel_,chy_.notchb_);		
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int XYStage::OnLowpassX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int l=0;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpon",xChannel_,l);
		chx_.lpon_=(l==1)?true:false;		
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.lpon_){
			pProp->Set(g_Lowpass_On);			
		}else{
			pProp->Set(g_Lowpass_Off);			
		}		
	}else if (eAct == MM::AfterSet){
	  std::string lpon;
      pProp->Get(lpon);
	  if (lpon == g_Lowpass_On){
		chx_.lpon_ = true;
	  }else{
         chx_.lpon_ = false;
	  }	  
	  	l=(chx_.lpon_)?1:0;
	  int ret = SetCommandValue("lpon",xChannel_,l);	
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnLowpassY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int l=0;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpon",yChannel_,l);
		chy_.lpon_=(l==1)?true:false;		
		if (ret!=DEVICE_OK)
			return ret;
		if (chy_.lpon_){
			pProp->Set(g_Lowpass_On);			
		}else{
			pProp->Set(g_Lowpass_Off);			
		}		
	}else if (eAct == MM::AfterSet){
	  std::string lpon;
      pProp->Get(lpon);
	  if (lpon == g_Lowpass_On){
		chy_.lpon_ = true;
	  }else{
         chy_.lpon_ = false;
	  }	 
	 l=(chy_.lpon_)?1:0;
	 int ret = SetCommandValue("lpon",yChannel_,l);	
	 if (ret!=DEVICE_OK)
		return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnLowpassFreqX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpf",xChannel_,chx_.lpf_);		
		if (ret != DEVICE_OK)
			return ret;		
		l=chx_.lpf_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chx_.lpf_=(int)l;
		int ret = SetCommandValue("lpf",xChannel_,chx_.lpf_);		
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int XYStage::OnLowpassFreqY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpf",yChannel_,chy_.lpf_);		
		if (ret != DEVICE_OK)
			return ret;		
		l=chy_.lpf_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chy_.lpf_=(int)l;
		int ret = SetCommandValue("lpf",yChannel_,chy_.lpf_);		
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int XYStage::OnGenerateX(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet){				
		int ret = GetCommandValue("gfkt",xChannel_,chx_.gfkt_);
		if (ret!=DEVICE_OK)
			return ret;
		switch (chx_.gfkt_){
		case 0:
			pProp->Set(g_Generator_Off);		break;
		case 1:
			pProp->Set(g_Generator_Sine);		break;
		case 2:
			pProp->Set(g_Generator_Tri);		break;
		case 3:
			pProp->Set(g_Generator_Rect);	break;
		case 4:
			pProp->Set(g_Generator_Noise);	break;
		case 5:
			pProp->Set(g_Generator_Sweep);	break;
		default:
			pProp->Set(g_Generator_Off);
		}
	}else if (eAct == MM::AfterSet){		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Generator_Off)
         chx_.gfkt_ = 0;
		else if (gen == g_Generator_Sine)
         chx_.gfkt_ = 1;
		else if (gen == g_Generator_Tri)
         chx_.gfkt_ = 2;
		else if (gen == g_Generator_Rect)
         chx_.gfkt_ = 3;
		else if (gen == g_Generator_Noise)
         chx_.gfkt_ = 4;
		else if (gen == g_Generator_Sweep)
         chx_.gfkt_ = 5;		
		int ret = SetCommandValue("gfkt",xChannel_,chx_.gfkt_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int XYStage::OnGenerateY(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet){			
		int ret = GetCommandValue("gfkt",yChannel_,chy_.gfkt_);
		if (ret!=DEVICE_OK)
			return ret;
		switch (chy_.gfkt_){
		case 0:
			pProp->Set(g_Generator_Off);	break;
		case 1:
			pProp->Set(g_Generator_Sine);	break;
		case 2:
			pProp->Set(g_Generator_Tri);	break;
		case 3:
			pProp->Set(g_Generator_Rect);	break;
		case 4:
			pProp->Set(g_Generator_Noise);	break;
		case 5:
			pProp->Set(g_Generator_Sweep);	break;
		default:
			pProp->Set(g_Generator_Off);
		}
	}else if (eAct == MM::AfterSet){		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Generator_Off)
         chy_.gfkt_ = 0;
		else if (gen == g_Generator_Sine)
         chy_.gfkt_ = 1;
		else if (gen == g_Generator_Tri)
         chy_.gfkt_ = 2;
		else if (gen == g_Generator_Rect)
         chy_.gfkt_ = 3;
		else if (gen == g_Generator_Noise)
         chy_.gfkt_ = 4;
		else if (gen == g_Generator_Sweep)
         chy_.gfkt_ = 5;		
		int ret = SetCommandValue("gfkt",yChannel_,chy_.gfkt_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int XYStage::OnSinAmpX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gasin",xChannel_,chx_.gasin_);		
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gasin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gasin_);		
		int ret = SetCommandValue("gasin",xChannel_,chx_.gasin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnSinAmpY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gasin",yChannel_,chy_.gasin_);		
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gasin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gasin_);		
		int ret = SetCommandValue("gasin",yChannel_,chy_.gasin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnSinOffX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("gosin",xChannel_,chx_.gosin_);       
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gosin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gosin_);		
		ret = SetCommandValue("gosin",xChannel_,chx_.gosin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnSinOffY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("gosin",yChannel_,chy_.gosin_);       
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gosin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gosin_);		
		ret = SetCommandValue("gosin",yChannel_,chy_.gosin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnSinFreqX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		
		int ret = GetCommandValue("gfsin",xChannel_,chx_.gfsin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gfsin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gfsin_);		
		int ret = SetCommandValue("gfsin",xChannel_,chx_.gfsin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnSinFreqY(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gfsin",yChannel_,chy_.gfsin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gfsin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gfsin_);		
		int ret = SetCommandValue("gfsin",yChannel_,chy_.gfsin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnTriAmpX(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
	{			
		int ret = GetCommandValue("gatri",xChannel_,chx_.gatri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gatri_);
	}else if (eAct == MM::AfterSet){
		pProp->Get(chx_.gatri_);
		int ret = SetCommandValue("gatri",xChannel_,chx_.gatri_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnTriAmpY(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gatri",yChannel_,chy_.gatri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gatri_);
	}else if (eAct == MM::AfterSet){
		pProp->Get(chy_.gatri_);
		int ret = SetCommandValue("gatri",yChannel_,chy_.gatri_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnTriOffX(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gotri",xChannel_,chx_.gotri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gotri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gotri_);
		int ret = SetCommandValue("gotri",xChannel_,chx_.gotri_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnTriOffY(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gotri",yChannel_,chy_.gotri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gotri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gotri_);
		int ret = SetCommandValue("gotri",yChannel_,chy_.gotri_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnTriFreqX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{	
		int ret = GetCommandValue("gftri",xChannel_,chx_.gftri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gftri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gftri_);
		int ret = SetCommandValue("gftri",xChannel_,chx_.gftri_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnTriFreqY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gftri",yChannel_,chy_.gftri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gftri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gftri_);
		int ret = SetCommandValue("gftri",yChannel_,chy_.gftri_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnTriSymX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gstri",xChannel_,chx_.gstri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gstri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gstri_);
		int ret = SetCommandValue("gstri",xChannel_,chx_.gstri_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnTriSymY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gstri",yChannel_,chy_.gstri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gstri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gstri_);
		int ret = SetCommandValue("gstri",yChannel_,chy_.gstri_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnRecAmpX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("garec",xChannel_,chx_.garec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.garec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.garec_);
		int ret = SetCommandValue("garec",xChannel_,chx_.garec_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnRecAmpY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("garec",yChannel_,chy_.garec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.garec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.garec_);
		int ret = SetCommandValue("garec",yChannel_,chy_.garec_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnRecOffX(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gorec",xChannel_,chx_.gorec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gorec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gorec_);
		int ret = SetCommandValue("gorec",xChannel_,chx_.gorec_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnRecOffY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gorec",yChannel_,chy_.gorec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gorec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gorec_);
		int ret = SetCommandValue("gorec",yChannel_,chy_.gorec_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnRecFreqX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gfrec",xChannel_,chx_.gfrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gfrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gfrec_);
		int ret = SetCommandValue("gfrec",xChannel_,chx_.gfrec_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnRecFreqY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gfrec",yChannel_,chy_.gfrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gfrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gfrec_);
		int ret = SetCommandValue("gfrec",yChannel_,chy_.gfrec_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnRecSymX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gsrec",xChannel_,chx_.gsrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gsrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gsrec_);
		int ret = SetCommandValue("gsrec",xChannel_,chx_.gsrec_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnRecSymY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gsrec",yChannel_,chy_.gsrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gsrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gsrec_);
		int ret = SetCommandValue("gsrec",yChannel_,chy_.gsrec_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnNoiAmpX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("ganoi",xChannel_,chx_.ganoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.ganoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.ganoi_);
		int ret = SetCommandValue("ganoi",xChannel_,chx_.ganoi_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnNoiAmpY(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
	{			
		int ret = GetCommandValue("ganoi",yChannel_,chy_.ganoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.ganoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.ganoi_);
		int ret = SetCommandValue("ganoi",yChannel_,chy_.ganoi_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnNoiOffX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gonoi",xChannel_,chx_.gonoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gonoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gonoi_);
		int ret = SetCommandValue("gonoi",xChannel_,chx_.gonoi_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnNoiOffY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gonoi",yChannel_,chy_.gonoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gonoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gonoi_);
		int ret = SetCommandValue("gonoi",yChannel_,chy_.gonoi_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnSweAmpX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{	
		int ret = GetCommandValue("gaswe",xChannel_,chx_.gaswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gaswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gaswe_);
		int ret = SetCommandValue("gaswe",xChannel_,chx_.gaswe_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnSweAmpY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gaswe",yChannel_,chy_.gaswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gaswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gaswe_);
		int ret = SetCommandValue("gaswe",yChannel_,chy_.gaswe_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnSweOffX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("goswe",xChannel_,chx_.goswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.goswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.goswe_);
		int ret = SetCommandValue("goswe",xChannel_,chx_.goswe_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnSweOffY(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("goswe",yChannel_,chy_.goswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.goswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.goswe_);
		int ret = SetCommandValue("goswe",yChannel_,chy_.goswe_);	
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnSweTimeX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gtswe",xChannel_,chx_.gtswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gtswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gtswe_);
		int ret = SetCommandValue("gtswe",xChannel_,chx_.gtswe_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnSweTimeY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gtswe",yChannel_,chy_.gtswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gtswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gtswe_);
		int ret = SetCommandValue("gtswe",yChannel_,chy_.gtswe_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int XYStage::OnScanTypeX(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("sct",xChannel_,chx_.sct_);				
		switch (chx_.sct_){
		case 0:
			pProp->Set(g_Scan_Type_Off);	break;
		case 1:
			pProp->Set(g_Scan_Type_Sine);	break;
		case 2:
			pProp->Set(g_Scan_Type_Tri);	break;		
		default:
			pProp->Set(g_Scan_Type_Off);
		}
		if (ret!=DEVICE_OK)
			return ret;
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Scan_Type_Off){
			chx_.sct_ = 0;
		}else if (gen == g_Scan_Type_Sine){
			chx_.sct_ = 1;
		}else if (gen == g_Scan_Type_Tri){
			chx_.sct_ = 2;	
		}
		int ret = SetCommandValue("sct",xChannel_,chx_.sct_);		
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int XYStage::OnScanTypeY(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("sct",yChannel_,chy_.sct_);
			
		switch (chy_.sct_){
		case 0:
			pProp->Set(g_Scan_Type_Off);	break;
		case 1:
			pProp->Set(g_Scan_Type_Sine);	break;
		case 2:
			pProp->Set(g_Scan_Type_Tri);	break;		
		default:
			pProp->Set(g_Scan_Type_Off);
		}
		if (ret!=DEVICE_OK)
			return ret;		
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Scan_Type_Off){
			chy_.sct_ = 0;
		}else if (gen == g_Scan_Type_Sine){
			chy_.sct_ = 1;
		}else if (gen == g_Scan_Type_Tri){
			chy_.sct_ = 2;	
		}
		int ret = SetCommandValue("sct",yChannel_,chy_.sct_);		
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int XYStage::OnScanX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {		
		int b=0;		
		int ret = GetCommandValue("ss",xChannel_,b);
		chx_.ss_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;			
		if(chx_.ss_){
			pProp->Set(g_Scan_Starting);
		}else{
			pProp->Set(g_Scan_Off);	
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string s;
		int b=0;
		pProp->Get(s);
		if (s == g_Scan_Off){
			chx_.ss_ = false;
			b=0;
		}
		else if (s == g_Scan_Starting){
			chx_.ss_ = true;
			b=1;
		}
		if(chx_.ss_){
			int ret = SetCommandValue("ss",xChannel_,b);
			if (ret!=DEVICE_OK)
				return ret;		
		}
	}
    return DEVICE_OK;
}
int XYStage::OnScanY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {	
		int b=0;		
		int ret = GetCommandValue("ss",yChannel_,b);
		chy_.ss_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;			
		if(chy_.ss_){
			pProp->Set(g_Scan_Starting);
		}else{
			pProp->Set(g_Scan_Off);	
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string s;
		int b=0;
		pProp->Get(s);
		if (s == g_Scan_Off){
			chy_.ss_ = false;
			b=0;
		}
		else if (s == g_Scan_Starting){
			chy_.ss_ = true;
			b=1;
		}		
		if(chy_.ss_){
			int ret = SetCommandValue("ss",yChannel_,b);	
			if (ret!=DEVICE_OK)
				return ret;	
		}
	}
    return DEVICE_OK;
}
int XYStage::OnTriggerStartX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){			
		int ret = GetCommandValue("trgss",xChannel_,chx_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chx_.trgss_);		
	}else if (eAct == MM::AfterSet){		
		pProp->Get(chx_.trgss_);		
		int ret = SetCommandValue("trgss",xChannel_,chx_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int XYStage::OnTriggerStartY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){			
		int ret = GetCommandValue("trgss",yChannel_,chy_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chy_.trgss_);		
	}else if (eAct == MM::AfterSet){		
		pProp->Get(chy_.trgss_);		
		int ret = SetCommandValue("trgss",yChannel_,chy_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int XYStage::OnTriggerEndX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("trgse",xChannel_,chx_.trgse_);
		if (ret!=DEVICE_OK)
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chx_.trgse_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chx_.trgse_);	
		int ret = SetCommandValue("trgse",xChannel_,chx_.trgse_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
	return DEVICE_OK;
}
int XYStage::OnTriggerEndY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {		
		int ret = GetCommandValue("trgse",yChannel_,chy_.trgse_);
		if (ret!=DEVICE_OK)
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chy_.trgse_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chy_.trgse_);
		int ret = SetCommandValue("trgse",yChannel_,chy_.trgse_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
	return DEVICE_OK;
}
int XYStage::OnTriggerIntervalX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("trgsi",xChannel_,chx_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chx_.trgsi_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chx_.trgsi_);	
		int ret = SetCommandValue("trgsi",xChannel_,chx_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int XYStage::OnTriggerIntervalY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {		
		int ret = GetCommandValue("trgsi",yChannel_,chy_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chy_.trgsi_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chy_.trgsi_);		
		int ret = SetCommandValue("trgsi",yChannel_,chy_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int XYStage::OnTriggerTimeX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {		
		int ret = GetCommandValue("trglen",xChannel_,chx_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set((long)chx_.trglen_);		
	}
    else if (eAct == MM::AfterSet)
    {	
		long l;
		pProp->Get(l);
		chx_.trglen_=(int)l;
		int ret = SetCommandValue("trglen",xChannel_,chx_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int XYStage::OnTriggerTimeY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {		
		int ret = GetCommandValue("trglen",yChannel_,chy_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set((long)chy_.trglen_);		
	}
    else if (eAct == MM::AfterSet)
    {	
		long l;
		pProp->Get(l);
		chy_.trglen_=(int)l;
		int ret = SetCommandValue("trglen",yChannel_,chy_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int XYStage::OnTriggerTypeX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {		
		int ret = GetCommandValue("trgedge",xChannel_,chx_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;			
		switch (chx_.trgedge_){
		case 0:
			pProp->Set(g_Trigger_Off);		break;
		case 1:
			pProp->Set(g_Trigger_Rising);	break;
		case 2:
			pProp->Set(g_Trigger_Falling);	break;	
		case 3:
			pProp->Set(g_Trigger_Both);		break;
		default:
			pProp->Set(g_Trigger_Off);
		}	
	}
    else if (eAct == MM::AfterSet)
    {			
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Trigger_Off)
			chx_.trgedge_ = 0;
		else if (gen == g_Trigger_Rising)
			chx_.trgedge_ = 1;
		else if (gen == g_Trigger_Falling)
			chx_.trgedge_ = 2;	
		else if (gen == g_Trigger_Both)
			chx_.trgedge_ = 3;
		int ret = SetCommandValue("trgedge",xChannel_,chx_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int XYStage::OnTriggerTypeY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {			
		int ret = GetCommandValue("trgedge",yChannel_,chy_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;			
		switch (chy_.trgedge_){
		case 0:
			pProp->Set(g_Trigger_Off);		break;
		case 1:
			pProp->Set(g_Trigger_Rising);	break;
		case 2:
			pProp->Set(g_Trigger_Falling);	break;	
		case 3:
			pProp->Set(g_Trigger_Both);		break;
		default:
			pProp->Set(g_Trigger_Off);
		}	
	}
    else if (eAct == MM::AfterSet)
    {			
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Trigger_Off)
			chy_.trgedge_ = 0;
		else if (gen == g_Trigger_Rising)
			chy_.trgedge_ = 1;
		else if (gen == g_Trigger_Falling)
			chy_.trgedge_ = 2;	
		else if (gen == g_Trigger_Both)
			chy_.trgedge_ = 3;
		int ret = SetCommandValue("trgedge",yChannel_,chy_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}

Tritor::Tritor():
EVDBase(this), 
nr_(1),
name_(g_Tritor),
xChannel_(0), //(xaxis),
yChannel_(1), //(yaxis), 
zChannel_(2), //(zaxis),
initialized_(false)
{
	 // Name, read-only (RO)
   CreateProperty(MM::g_Keyword_Name, g_Tritor, MM::String, true);
}

Tritor::Tritor(int nr, const char* name):
EVDBase(this),
nr_(nr),
name_(name),
xChannel_(0), //(xaxis),
yChannel_(1), //(yaxis), 
zChannel_(2), //(zaxis),
initialized_(false)
{
	 // Name, read-only (RO)
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description, RO
   CreateProperty(MM::g_Keyword_Description, "Piezosystem Jena Tritor", MM::String, true);  

   CPropertyAction*  pAct = new CPropertyAction (this, &Tritor::OnChannelX);
   CreateProperty(g_ChannelX, "1", MM::Integer, false, pAct,true);
   if(inventoryDeviceAddresses_.empty()){		
		AddAllowedValue(g_ChannelX, "1");
		AddAllowedValue(g_ChannelX, "2");
		AddAllowedValue(g_ChannelX, "3");
		AddAllowedValue(g_ChannelX, "4");
		AddAllowedValue(g_ChannelX, "5");
		AddAllowedValue(g_ChannelX, "6");
   }else{
		for(std::vector<int>::iterator it=inventoryDeviceAddresses_.begin();it!=inventoryDeviceAddresses_.end();++it){
			AddAllowedValue(g_ChannelX, CDeviceUtils::ConvertToString(((int)*it)+1));
		}
   }   

    pAct = new CPropertyAction (this, &Tritor::OnChannelY);
   CreateProperty(g_ChannelY, "2", MM::Integer, false, pAct,true);
   if(inventoryDeviceAddresses_.empty()){		
		AddAllowedValue(g_ChannelY, "1");
		AddAllowedValue(g_ChannelY, "2");
		AddAllowedValue(g_ChannelY, "3");
		AddAllowedValue(g_ChannelY, "4");
		AddAllowedValue(g_ChannelY, "5");
		AddAllowedValue(g_ChannelY, "6");
   }else{
		for(std::vector<int>::iterator it=inventoryDeviceAddresses_.begin();it!=inventoryDeviceAddresses_.end();++it){
			AddAllowedValue("Channel Y", CDeviceUtils::ConvertToString(((int)*it)+1));
		}
   }
   pAct = new CPropertyAction (this, &Tritor::OnChannelZ);
   CreateProperty(g_ChannelZ, "3", MM::Integer, false, pAct,true);
   if(inventoryDeviceAddresses_.empty()){		
		AddAllowedValue(g_ChannelZ, "1");
		AddAllowedValue(g_ChannelZ, "2");
		AddAllowedValue(g_ChannelZ, "3");
		AddAllowedValue(g_ChannelZ, "4");
		AddAllowedValue(g_ChannelZ, "5");
		AddAllowedValue(g_ChannelZ, "6");
   }else{
		for(std::vector<int>::iterator it=inventoryDeviceAddresses_.begin();it!=inventoryDeviceAddresses_.end();++it){
			AddAllowedValue(g_ChannelZ, CDeviceUtils::ConvertToString(((int)*it)+1));
		}
   }

	chx_.channel_=xChannel_;	
	chy_.channel_=yChannel_;
	chz_.channel_=zChannel_;
}
Tritor::Tritor(int nr, const char* name,int chx,int chy,int chz):
EVDBase(this),
nr_(nr),
name_(name),
xChannel_(chx), //(xaxis),
yChannel_(chy), //(yaxis), 
zChannel_(chz), //(zaxis),
initialized_(false)
{
	 // Name, read-only (RO)
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description, RO
   CreateProperty(MM::g_Keyword_Description, "Piezosystem Jena Tritor", MM::String, true);  

   CPropertyAction*  pAct = new CPropertyAction (this, &Tritor::OnChannelX);
   CreateProperty(g_ChannelX, CDeviceUtils::ConvertToString(xChannel_+1) , MM::Integer, false, pAct,true);
   if(inventoryDeviceAddresses_.empty()){		
		AddAllowedValue(g_ChannelX, "1");
		AddAllowedValue(g_ChannelX, "2");
		AddAllowedValue(g_ChannelX, "3");
		AddAllowedValue(g_ChannelX, "4");
		AddAllowedValue(g_ChannelX, "5");
		AddAllowedValue(g_ChannelX, "6");
   }else{
		for(std::vector<int>::iterator it=inventoryDeviceAddresses_.begin();it!=inventoryDeviceAddresses_.end();++it){
			AddAllowedValue(g_ChannelX, CDeviceUtils::ConvertToString(((int)*it)+1));
		}
   }   

    pAct = new CPropertyAction (this, &Tritor::OnChannelY);
   CreateProperty(g_ChannelY, CDeviceUtils::ConvertToString(yChannel_+1), MM::Integer, false, pAct,true);
   if(inventoryDeviceAddresses_.empty()){		
		AddAllowedValue(g_ChannelY, "1");
		AddAllowedValue(g_ChannelY, "2");
		AddAllowedValue(g_ChannelY, "3");
		AddAllowedValue(g_ChannelY, "4");
		AddAllowedValue(g_ChannelY, "5");
		AddAllowedValue(g_ChannelY, "6");
   }else{
		for(std::vector<int>::iterator it=inventoryDeviceAddresses_.begin();it!=inventoryDeviceAddresses_.end();++it){
			AddAllowedValue("Channel Y", CDeviceUtils::ConvertToString(((int)*it)+1));
		}
   }
   pAct = new CPropertyAction (this, &Tritor::OnChannelZ);
   CreateProperty(g_ChannelZ, CDeviceUtils::ConvertToString(zChannel_+1), MM::Integer, false, pAct,true);
   if(inventoryDeviceAddresses_.empty()){		
		AddAllowedValue(g_ChannelZ, "1");
		AddAllowedValue(g_ChannelZ, "2");
		AddAllowedValue(g_ChannelZ, "3");
		AddAllowedValue(g_ChannelZ, "4");
		AddAllowedValue(g_ChannelZ, "5");
		AddAllowedValue(g_ChannelZ, "6");
   }else{
		for(std::vector<int>::iterator it=inventoryDeviceAddresses_.begin();it!=inventoryDeviceAddresses_.end();++it){
			AddAllowedValue(g_ChannelZ, CDeviceUtils::ConvertToString(((int)*it)+1));
		}
   }

	chx_.channel_=xChannel_;	
	chy_.channel_=yChannel_;
	chz_.channel_=zChannel_;
}
Tritor::~Tritor(){
	Shutdown();
}

int Tritor::Shutdown()
{
	if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

void Tritor::GetName(char* Name) const
{
	std::ostringstream name;
   name<<g_Tritor<<(nr_);   
   CDeviceUtils::CopyLimitedString(Name, name.str().c_str());
}
int Tritor::Initialize()
{
    core_ = GetCoreCallback();

	CreateProperty(g_ChannelX_, CDeviceUtils::ConvertToString(xChannel_+1), MM::Integer, true);	//read-only 
	CreateProperty(g_ChannelY_, CDeviceUtils::ConvertToString(yChannel_+1), MM::Integer, true);	//read-only 
	CreateProperty(g_ChannelZ_, CDeviceUtils::ConvertToString(zChannel_+1), MM::Integer, true);	//read-only 

	CPropertyAction* pAct = new CPropertyAction (this, &Tritor::OnStatX);
    CreateProperty(g_StatusX, "0", MM::Integer, true, pAct);
    pAct = new CPropertyAction (this, &Tritor::OnStatY);
    CreateProperty(g_StatusY, "0", MM::Integer, true, pAct);
    pAct = new CPropertyAction (this, &Tritor::OnStatZ);
    CreateProperty(g_StatusZ, "0", MM::Integer, true, pAct);

   GetLimitsValues(&chx_);
   GetLimitsValues(&chy_);
   GetLimitsValues(&chz_);
   // limit x
   CreateProperty("Limit Voltage min x", CDeviceUtils::ConvertToString(chx_.min_V_), MM::Float, true);		
   CreateProperty("Limit Voltage max x" , CDeviceUtils::ConvertToString(chx_.max_V_), MM::Float, true);
   CreateProperty("Limit um min x",CDeviceUtils::ConvertToString(chx_.min_um_), MM::Float, true);		
   CreateProperty("Limit um max x", CDeviceUtils::ConvertToString(chx_.max_um_), MM::Float, true);
   // limit y
   CreateProperty("Limit Voltage min y", CDeviceUtils::ConvertToString(chy_.min_V_), MM::Float, true);		
   CreateProperty("Limit Voltage max y" , CDeviceUtils::ConvertToString(chy_.max_V_), MM::Float, true);
   CreateProperty("Limit um min y",CDeviceUtils::ConvertToString(chy_.min_um_), MM::Float, true);		
   CreateProperty("Limit um max y", CDeviceUtils::ConvertToString(chy_.max_um_), MM::Float, true);
   // limit z
   CreateProperty("Limit Voltage min z", CDeviceUtils::ConvertToString(chz_.min_V_), MM::Float, true);		
   CreateProperty("Limit Voltage max z" , CDeviceUtils::ConvertToString(chz_.max_V_), MM::Float, true);
   CreateProperty("Limit um min z",CDeviceUtils::ConvertToString(chz_.min_um_), MM::Float, true);		
   CreateProperty("Limit um max z", CDeviceUtils::ConvertToString(chz_.max_um_), MM::Float, true);

   char s[20];	
	int ret = GetCommandValue("rgver",xChannel_,chx_.rgver_);
	if (ret != DEVICE_OK)	  
      return ret;	
	sprintf(s,"%i",chx_.rgver_);	
	CreateProperty(g_RgverX, s, MM::Integer, true);
	ret = GetCommandValue("rgver",yChannel_,chy_.rgver_);
	if (ret != DEVICE_OK)	  
      return ret;	
	sprintf(s,"%i",chy_.rgver_);
	CreateProperty(g_RgverY, s, MM::Integer, true);
	ret = GetCommandValue("rgver",zChannel_,chz_.rgver_);
	if (ret != DEVICE_OK)	  
      return ret;	
	sprintf(s,"%i",chz_.rgver_);
	CreateProperty(g_RgverZ, s, MM::Integer, true);
	pAct = new CPropertyAction (this, &Tritor::OnTimeX);
	CreateProperty(g_RohmX, "0", MM::Integer, true, pAct);
	pAct = new CPropertyAction (this, &Tritor::OnTimeY);
	CreateProperty(g_RohmY, "0", MM::Integer, true, pAct);
	pAct = new CPropertyAction (this, &Tritor::OnTimeZ);
	CreateProperty(g_RohmZ, "0", MM::Integer, true, pAct);
	pAct = new CPropertyAction (this, &Tritor::OnTempX);
    CreateProperty(g_KtempX, "0", MM::Float, true, pAct);
	pAct = new CPropertyAction (this, &Tritor::OnTempY);
    CreateProperty(g_KtempY, "0", MM::Float, true, pAct);
	pAct = new CPropertyAction (this, &Tritor::OnTempZ);
    CreateProperty(g_KtempZ, "0", MM::Float, true, pAct);

	pAct = new CPropertyAction (this, &Tritor::OnPositionX);
    CreateProperty(g_PositionX, "0.001", MM::Float, false, pAct);
    pAct = new CPropertyAction (this, &Tritor::OnPositionY);
    CreateProperty(g_PositionY, "0.001", MM::Float, false, pAct);
	pAct = new CPropertyAction (this, &Tritor::OnPositionZ);
    CreateProperty(g_PositionZ, "0.001", MM::Float, false, pAct);

	pAct = new CPropertyAction (this, &Tritor::OnLoopX);
   CreateProperty(g_LoopX, g_Loop_open, MM::String, false, pAct,false);
   AddAllowedValue(g_LoopX, g_Loop_open);
   AddAllowedValue(g_LoopX, g_Loop_close);
   pAct = new CPropertyAction (this, &Tritor::OnLoopY);
   CreateProperty(g_LoopY, g_Loop_open, MM::String, false, pAct,false);
   AddAllowedValue(g_LoopY, g_Loop_open);
   AddAllowedValue(g_LoopY, g_Loop_close);
	pAct = new CPropertyAction (this, &Tritor::OnLoopZ);
   CreateProperty(g_LoopZ, g_Loop_open, MM::String, false, pAct,false);
   AddAllowedValue(g_LoopZ, g_Loop_open);
   AddAllowedValue(g_LoopZ, g_Loop_close);

	pAct = new CPropertyAction (this, &Tritor::OnSoftstartX);
    CreateProperty(g_FenableX, g_Fenable_Off, MM::String, false, pAct);
    AddAllowedValue(g_FenableX , g_Fenable_Off);
    AddAllowedValue(g_FenableX, g_Fenable_On);
	pAct = new CPropertyAction (this, &Tritor::OnSoftstartY);
    CreateProperty(g_FenableY, g_Fenable_Off, MM::String, false, pAct);
    AddAllowedValue(g_FenableY , g_Fenable_Off);
    AddAllowedValue(g_FenableY , g_Fenable_On);
	pAct = new CPropertyAction (this, &Tritor::OnSoftstartZ);
    CreateProperty(g_FenableZ, g_Fenable_Off, MM::String, false, pAct);
    AddAllowedValue(g_FenableZ , g_Fenable_Off);
    AddAllowedValue(g_FenableZ , g_Fenable_On);

	pAct = new CPropertyAction (this, &Tritor::OnSlewRateX);
	CreateProperty(g_SrX, "10.0", MM::Float, false, pAct);
	SetPropertyLimits(g_SrX, 0.0000002, 500.0);
	pAct = new CPropertyAction (this, &Tritor::OnSlewRateY);
	CreateProperty(g_SrY, "10.0", MM::Float, false, pAct);
	SetPropertyLimits(g_SrY, 0.0000002, 500.0);
	pAct = new CPropertyAction (this, &Tritor::OnSlewRateZ);
	CreateProperty(g_SrZ, "10.0", MM::Float, false, pAct);
	SetPropertyLimits(g_SrZ, 0.0000002, 500.0);

    pAct = new CPropertyAction (this, &Tritor::OnPositionX);
    CreateProperty(g_PositionX, "0.001", MM::Float, false, pAct);
    pAct = new CPropertyAction (this, &Tritor::OnPositionY);
    CreateProperty(g_PositionY, "0.001", MM::Float, false, pAct);
	pAct = new CPropertyAction (this, &Tritor::OnPositionZ);
    CreateProperty(g_PositionZ, "0.001", MM::Float, false, pAct);

	pAct = new CPropertyAction (this, &Tritor::OnModulInputX);
    CreateProperty(g_ModonX, g_Modon_Off, MM::String, false, pAct);
    AddAllowedValue(g_ModonX, g_Modon_Off);
    AddAllowedValue(g_ModonX, g_Modon_On);
	pAct = new CPropertyAction (this, &Tritor::OnModulInputY);
    CreateProperty(g_ModonY, g_Modon_Off, MM::String, false, pAct);
    AddAllowedValue(g_ModonY, g_Modon_Off);
    AddAllowedValue(g_ModonY, g_Modon_On);
	pAct = new CPropertyAction (this, &Tritor::OnModulInputZ);
    CreateProperty(g_ModonZ, g_Modon_Off, MM::String, false, pAct);
    AddAllowedValue(g_ModonZ, g_Modon_Off);
    AddAllowedValue(g_ModonZ, g_Modon_On);

	pAct = new CPropertyAction (this, &Tritor::OnMonitorX);
    CreateProperty(g_MonsrcX, g_Monsrc_0, MM::String, false, pAct);
    AddAllowedValue(g_MonsrcX, g_Monsrc_0);
    AddAllowedValue(g_MonsrcX, g_Monsrc_1);
	AddAllowedValue(g_MonsrcX, g_Monsrc_2);
	AddAllowedValue(g_MonsrcX, g_Monsrc_3);
	AddAllowedValue(g_MonsrcX, g_Monsrc_4);
	AddAllowedValue(g_MonsrcX, g_Monsrc_5);
	AddAllowedValue(g_MonsrcX, g_Monsrc_6);

	pAct = new CPropertyAction (this, &Tritor::OnMonitorY);
    CreateProperty(g_MonsrcY, g_Monsrc_0, MM::String, false, pAct);
    AddAllowedValue(g_MonsrcY, g_Monsrc_0);
    AddAllowedValue(g_MonsrcY, g_Monsrc_1);
	AddAllowedValue(g_MonsrcY, g_Monsrc_2);
	AddAllowedValue(g_MonsrcY, g_Monsrc_3);
	AddAllowedValue(g_MonsrcY, g_Monsrc_4);
	AddAllowedValue(g_MonsrcY, g_Monsrc_5);
	AddAllowedValue(g_MonsrcY, g_Monsrc_6);

	pAct = new CPropertyAction (this, &Tritor::OnMonitorZ);
    CreateProperty(g_MonsrcZ, g_Monsrc_0, MM::String, false, pAct);
    AddAllowedValue(g_MonsrcZ, g_Monsrc_0);
    AddAllowedValue(g_MonsrcZ, g_Monsrc_1);
	AddAllowedValue(g_MonsrcZ, g_Monsrc_2);
	AddAllowedValue(g_MonsrcZ, g_Monsrc_3);
	AddAllowedValue(g_MonsrcZ, g_Monsrc_4);
	AddAllowedValue(g_MonsrcZ, g_Monsrc_5);
	AddAllowedValue(g_MonsrcZ, g_Monsrc_6);

	pAct = new CPropertyAction (this, &Tritor::OnPidPX);
	CreateProperty(g_PID_PX, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_PID_PX, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Tritor::OnPidIX);
	CreateProperty(g_PID_IX, "0.0", MM::Float, false, pAct);
    SetPropertyLimits(g_PID_IX, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Tritor::OnPidDX);
	CreateProperty(g_PID_DX, "0.0", MM::Float, false, pAct);
    SetPropertyLimits(g_PID_DX, 0.0, 999.0);

    pAct = new CPropertyAction (this, &Tritor::OnPidPY);
	CreateProperty(g_PID_PY, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_PID_PY, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Tritor::OnPidIY);
	CreateProperty(g_PID_IY, "0.0", MM::Float, false, pAct);
    SetPropertyLimits(g_PID_IY, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Tritor::OnPidDY);
	CreateProperty(g_PID_DY, "0.0", MM::Float, false, pAct);
    SetPropertyLimits(g_PID_DY, 0.0, 999.0);

	pAct = new CPropertyAction (this, &Tritor::OnPidPZ);
	CreateProperty(g_PID_PZ, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_PID_PZ, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Tritor::OnPidIZ);
	CreateProperty(g_PID_IZ, "0.0", MM::Float, false, pAct);
    SetPropertyLimits(g_PID_IZ, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Tritor::OnPidDZ);
	CreateProperty(g_PID_DZ, "0.0", MM::Float, false, pAct);
    SetPropertyLimits(g_PID_DZ, 0.0, 999.0);

	//Notch Filter
    pAct = new CPropertyAction (this, &Tritor::OnNotchX);
    CreateProperty(g_NotchX, g_Notch_Off, MM::String, false, pAct);
    AddAllowedValue(g_NotchX, g_Notch_Off);
    AddAllowedValue(g_NotchX, g_Notch_On);
    pAct = new CPropertyAction (this, &Tritor::OnNotchY);
    CreateProperty(g_NotchY, g_Notch_Off, MM::String, false, pAct);
    AddAllowedValue(g_NotchY, g_Notch_Off);
    AddAllowedValue(g_NotchY, g_Notch_On);
	pAct = new CPropertyAction (this, &Tritor::OnNotchZ);
    CreateProperty(g_NotchZ, g_Notch_Off, MM::String, false, pAct);
    AddAllowedValue(g_NotchZ, g_Notch_Off);
    AddAllowedValue(g_NotchZ, g_Notch_On);
    pAct = new CPropertyAction (this, &Tritor::OnNotchFreqX);
	CreateProperty(g_Notch_FreqX, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Notch_FreqX, 0, 20000);
	pAct = new CPropertyAction (this, &Tritor::OnNotchFreqY);
	CreateProperty(g_Notch_FreqY, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Notch_FreqY, 0, 20000);
	pAct = new CPropertyAction (this, &Tritor::OnNotchFreqZ);
	CreateProperty(g_Notch_FreqZ, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Notch_FreqZ, 0, 20000);
    pAct = new CPropertyAction (this, &Tritor::OnNotchBandX);
	CreateProperty(g_Notch_BandX, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Notch_BandX, 0, 20000);
    pAct = new CPropertyAction (this, &Tritor::OnNotchBandY);
	CreateProperty(g_Notch_BandY, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Notch_BandY, 0, 20000);
	pAct = new CPropertyAction (this, &Tritor::OnNotchBandZ);
	CreateProperty(g_Notch_BandZ, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Notch_BandZ, 0, 20000);
	//Low pass filter
    pAct = new CPropertyAction (this, &Tritor::OnLowpassX);
    CreateProperty(g_LowpassX, g_Lowpass_Off, MM::String, false, pAct);
    AddAllowedValue(g_LowpassX, g_Lowpass_Off);
    AddAllowedValue(g_LowpassX, g_Lowpass_On);
    pAct = new CPropertyAction (this, &Tritor::OnLowpassY);
    CreateProperty(g_LowpassY, g_Lowpass_Off, MM::String, false, pAct);
    AddAllowedValue(g_LowpassY, g_Lowpass_Off);
    AddAllowedValue(g_LowpassY, g_Lowpass_On);
	pAct = new CPropertyAction (this, &Tritor::OnLowpassZ);
    CreateProperty(g_LowpassZ, g_Lowpass_Off, MM::String, false, pAct);
    AddAllowedValue(g_LowpassZ, g_Lowpass_Off);
    AddAllowedValue(g_LowpassZ, g_Lowpass_On);
	pAct = new CPropertyAction (this, &Tritor::OnLowpassFreqX);
	CreateProperty(g_Lowpass_FreqX, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Lowpass_FreqX, 0, 20000);
    pAct = new CPropertyAction (this, &Tritor::OnLowpassFreqY);
	CreateProperty(g_Lowpass_FreqY, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Lowpass_FreqY, 0, 20000);
	pAct = new CPropertyAction (this, &Tritor::OnLowpassFreqZ);
	CreateProperty(g_Lowpass_FreqZ, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Lowpass_FreqZ, 0, 20000);

	//Internal function generator
	chx_.gfkt_=0;
	pAct = new CPropertyAction (this, &Tritor::OnGenerateX);
	CreateProperty(g_GeneratorX, g_Generator_Off, MM::String, false, pAct);
	AddAllowedValue(g_GeneratorX, g_Generator_Off);
	AddAllowedValue(g_GeneratorX, g_Generator_Sine);
	AddAllowedValue(g_GeneratorX, g_Generator_Tri);
	AddAllowedValue(g_GeneratorX, g_Generator_Rect);
	AddAllowedValue(g_GeneratorX, g_Generator_Noise);
	AddAllowedValue(g_GeneratorX, g_Generator_Sweep);
	chy_.gfkt_=0;
	pAct = new CPropertyAction (this, &Tritor::OnGenerateY);
	CreateProperty(g_GeneratorY, g_Generator_Off, MM::String, false, pAct);
	AddAllowedValue(g_GeneratorY, g_Generator_Off);
	AddAllowedValue(g_GeneratorY, g_Generator_Sine);
	AddAllowedValue(g_GeneratorY, g_Generator_Tri);
	AddAllowedValue(g_GeneratorY, g_Generator_Rect);
	AddAllowedValue(g_GeneratorY, g_Generator_Noise);
	AddAllowedValue(g_GeneratorY, g_Generator_Sweep);
	chz_.gfkt_=0;
	pAct = new CPropertyAction (this, &Tritor::OnGenerateZ);
	CreateProperty(g_GeneratorZ, g_Generator_Off, MM::String, false, pAct);
	AddAllowedValue(g_GeneratorZ, g_Generator_Off);
	AddAllowedValue(g_GeneratorZ, g_Generator_Sine);
	AddAllowedValue(g_GeneratorZ, g_Generator_Tri);
	AddAllowedValue(g_GeneratorZ, g_Generator_Rect);
	AddAllowedValue(g_GeneratorZ, g_Generator_Noise);
	AddAllowedValue(g_GeneratorZ, g_Generator_Sweep);
	
	//Sine
	pAct = new CPropertyAction (this, &Tritor::OnSinAmpX);
	CreateProperty(g_Generator_Sine_AmpX, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_AmpX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnSinAmpY);
	CreateProperty(g_Generator_Sine_AmpY, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_AmpY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnSinAmpZ);
	CreateProperty(g_Generator_Sine_AmpZ, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_AmpZ, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnSinOffX);
	CreateProperty(g_Generator_Sine_OffsetX, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_OffsetX, 0.0, 100.0);
    pAct = new CPropertyAction (this, &Tritor::OnSinOffY);
	CreateProperty(g_Generator_Sine_OffsetY, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_OffsetY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnSinOffZ);
	CreateProperty(g_Generator_Sine_OffsetZ, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_OffsetZ, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnSinFreqX);
	CreateProperty(g_Generator_Sine_FreqX, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_FreqX, 0.00001, 9999.9);
	pAct = new CPropertyAction (this, &Tritor::OnSinFreqY);
	CreateProperty(g_Generator_Sine_FreqY, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_FreqY, 0.00001, 9999.9);
	pAct = new CPropertyAction (this, &Tritor::OnSinFreqZ);
	CreateProperty(g_Generator_Sine_FreqZ, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_FreqZ, 0.00001, 9999.9);
	//triangle
	pAct = new CPropertyAction (this, &Tritor::OnTriAmpX);
	CreateProperty(g_Generator_Tri_AmpX, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_AmpX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnTriAmpY);
	CreateProperty(g_Generator_Tri_AmpY, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_AmpY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnTriAmpZ);
	CreateProperty(g_Generator_Tri_AmpZ, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_AmpZ, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnTriOffX);
	CreateProperty(g_Generator_Tri_OffsetX, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Tri_OffsetX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnTriOffY);
	CreateProperty(g_Generator_Tri_OffsetY, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Tri_OffsetY, 0.0, 100.0);
    pAct = new CPropertyAction (this, &Tritor::OnTriOffZ);
	CreateProperty(g_Generator_Tri_OffsetZ, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Tri_OffsetZ, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnTriFreqX);
	CreateProperty(g_Generator_Tri_FreqX, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_FreqX, 0.00001, 9999.9);
	pAct = new CPropertyAction (this, &Tritor::OnTriFreqY);
	CreateProperty(g_Generator_Tri_FreqY, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_FreqY, 0.00001, 9999.9);
    pAct = new CPropertyAction (this, &Tritor::OnTriFreqZ);
	CreateProperty(g_Generator_Tri_FreqZ, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_FreqZ, 0.00001, 9999.9);
	pAct = new CPropertyAction (this, &Tritor::OnTriSymX);
	CreateProperty(g_Generator_Tri_SymX, "50.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_SymX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnTriSymY);
	CreateProperty(g_Generator_Tri_SymY, "50.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_SymY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnTriSymZ);
	CreateProperty(g_Generator_Tri_SymZ, "50.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_SymZ, 0.0, 100.0);
	//rectangle
	pAct = new CPropertyAction (this, &Tritor::OnRecAmpX);
	CreateProperty(g_Generator_Rect_AmpX, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_AmpX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnRecAmpY);
	CreateProperty(g_Generator_Rect_AmpY, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_AmpY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnRecAmpZ);
	CreateProperty(g_Generator_Rect_AmpZ, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_AmpZ, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnRecOffX);
	CreateProperty(g_Generator_Rect_OffsetX, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_OffsetX, 0.0, 100.0);
    pAct = new CPropertyAction (this, &Tritor::OnRecOffY);
	CreateProperty(g_Generator_Rect_OffsetY, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_OffsetY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnRecOffZ);
	CreateProperty(g_Generator_Rect_OffsetZ, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_OffsetZ, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnRecFreqX);
	CreateProperty(g_Generator_Rect_FreqX, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_FreqX, 0.00001, 9999.9);
	pAct = new CPropertyAction (this, &Tritor::OnRecFreqY);
	CreateProperty(g_Generator_Rect_FreqY, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_FreqY, 0.00001, 9999.9);
	pAct = new CPropertyAction (this, &Tritor::OnRecFreqZ);
	CreateProperty(g_Generator_Rect_FreqZ, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_FreqZ, 0.00001, 9999.9);
	pAct = new CPropertyAction (this, &Tritor::OnRecSymX);
	CreateProperty(g_Generator_Rect_SymX, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_SymX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnRecSymY);
	CreateProperty(g_Generator_Rect_SymY, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_SymY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnRecSymZ);
	CreateProperty(g_Generator_Rect_SymZ, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_SymZ, 0.0, 100.0);
	//Noise
	pAct = new CPropertyAction (this, &Tritor::OnNoiAmpX);
	CreateProperty(g_Generator_Noise_AmpX, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Noise_AmpX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnNoiAmpY);
	CreateProperty(g_Generator_Noise_AmpY, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Noise_AmpY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnNoiAmpZ);
	CreateProperty(g_Generator_Noise_AmpZ, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Noise_AmpZ, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnNoiOffX);
	CreateProperty(g_Generator_Noise_OffsetX, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Noise_OffsetX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnNoiOffY);
	CreateProperty(g_Generator_Noise_OffsetY, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Noise_OffsetY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnNoiOffZ);
	CreateProperty(g_Generator_Noise_OffsetZ, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Noise_OffsetZ, 0.0, 100.0);
	//Sweep
    pAct = new CPropertyAction (this, &Tritor::OnSweAmpX);
	CreateProperty(g_Generator_Sweep_AmpX, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sweep_AmpX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnSweAmpY);
	CreateProperty(g_Generator_Sweep_AmpY, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sweep_AmpY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnSweAmpZ);
	CreateProperty(g_Generator_Sweep_AmpZ, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sweep_AmpZ, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnSweOffX);
	CreateProperty(g_Generator_Sweep_OffsetX, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_OffsetX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnSweOffY);
	CreateProperty(g_Generator_Sweep_OffsetY, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_OffsetY, 0.0, 100.0);
    pAct = new CPropertyAction (this, &Tritor::OnSweOffZ);
	CreateProperty(g_Generator_Sweep_OffsetZ, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_OffsetZ, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Tritor::OnSweTimeX);
	CreateProperty(g_Generator_Sweep_TimeX, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_TimeX, 0.4, 800.0);
	pAct = new CPropertyAction (this, &Tritor::OnSweTimeY);
	CreateProperty(g_Generator_Sweep_TimeY, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_TimeY, 0.4, 800.0);
	pAct = new CPropertyAction (this, &Tritor::OnSweTimeZ);
	CreateProperty(g_Generator_Sweep_TimeZ, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_TimeZ, 0.4, 800.0);
	//Scan
	pAct = new CPropertyAction (this, &Tritor::OnScanTypeX);
	CreateProperty(g_Scan_TypeX, g_Scan_Type_Off, MM::String, false, pAct);
	AddAllowedValue(g_Scan_TypeX, g_Scan_Type_Off);
	AddAllowedValue(g_Scan_TypeX, g_Scan_Type_Sine);
	AddAllowedValue(g_Scan_TypeX, g_Scan_Type_Tri);
	pAct = new CPropertyAction (this, &Tritor::OnScanTypeY);
	CreateProperty(g_Scan_TypeY, g_Scan_Type_Off, MM::String, false, pAct);
	AddAllowedValue(g_Scan_TypeY, g_Scan_Type_Off);
	AddAllowedValue(g_Scan_TypeY, g_Scan_Type_Sine);
	AddAllowedValue(g_Scan_TypeY, g_Scan_Type_Tri);
	pAct = new CPropertyAction (this, &Tritor::OnScanTypeZ);
	CreateProperty(g_Scan_TypeZ, g_Scan_Type_Off, MM::String, false, pAct);
	AddAllowedValue(g_Scan_TypeZ, g_Scan_Type_Off);
	AddAllowedValue(g_Scan_TypeZ, g_Scan_Type_Sine);
	AddAllowedValue(g_Scan_TypeZ, g_Scan_Type_Tri);

	pAct = new CPropertyAction (this, &Tritor::OnScanX);
	CreateProperty(g_Scan_StartX, g_Scan_Off, MM::String, false, pAct);
    AddAllowedValue(g_Scan_StartX, g_Scan_Off);
    AddAllowedValue(g_Scan_StartX, g_Scan_Starting);
	pAct = new CPropertyAction (this, &Tritor::OnScanY);
	CreateProperty(g_Scan_StartY, g_Scan_Off, MM::String, false, pAct);
    AddAllowedValue(g_Scan_StartY, g_Scan_Off);
    AddAllowedValue(g_Scan_StartY, g_Scan_Starting);
	pAct = new CPropertyAction (this, &Tritor::OnScanZ);
	CreateProperty(g_Scan_StartZ, g_Scan_Off, MM::String, false, pAct);
    AddAllowedValue(g_Scan_StartZ, g_Scan_Off);
    AddAllowedValue(g_Scan_StartZ, g_Scan_Starting);

	//trigger
    pAct = new CPropertyAction (this, &Tritor::OnTriggerStartX);
	CreateProperty(g_Trigger_StartX, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Trigger_StartX, chx_.max_um_*0.002, chx_.max_um_*0.998);
	pAct = new CPropertyAction (this, &Tritor::OnTriggerStartY);
	CreateProperty(g_Trigger_StartY, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Trigger_StartY, chy_.max_um_*0.002, chy_.max_um_*0.998);
	pAct = new CPropertyAction (this, &Tritor::OnTriggerStartZ);
	CreateProperty(g_Trigger_StartZ, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Trigger_StartZ, chy_.max_um_*0.002, chy_.max_um_*0.998);
	pAct = new CPropertyAction (this, &Tritor::OnTriggerEndX);
	CreateProperty(g_Trigger_EndX, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_EndX, chx_.max_um_*0.002, chx_.max_um_*0.998);
	pAct = new CPropertyAction (this, &Tritor::OnTriggerEndY);
	CreateProperty(g_Trigger_EndY, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_EndY, chy_.max_um_*0.002, chy_.max_um_*0.998);
    pAct = new CPropertyAction (this, &Tritor::OnTriggerEndZ);
	CreateProperty(g_Trigger_EndZ, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_EndZ, chy_.max_um_*0.002, chy_.max_um_*0.998);
	pAct = new CPropertyAction (this, &Tritor::OnTriggerIntervalX);
	CreateProperty(g_Trigger_IntervalX, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_IntervalX, chx_.min_um_, chx_.max_um_);
	pAct = new CPropertyAction (this, &Tritor::OnTriggerIntervalY);
	CreateProperty(g_Trigger_IntervalY, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_IntervalY, chy_.min_um_, chy_.max_um_);
	pAct = new CPropertyAction (this, &Tritor::OnTriggerIntervalZ);
	CreateProperty(g_Trigger_IntervalZ, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_IntervalZ, chy_.min_um_, chy_.max_um_);
	pAct = new CPropertyAction (this, &Tritor::OnTriggerTimeX);
	CreateProperty(g_Trigger_TimeX, "1", MM::Integer, false, pAct);
    SetPropertyLimits(g_Trigger_TimeX, 1, 255);
    pAct = new CPropertyAction (this, &Tritor::OnTriggerTimeY);
	CreateProperty(g_Trigger_TimeY, "1", MM::Integer, false, pAct);
    SetPropertyLimits(g_Trigger_TimeY, 1, 255);
    pAct = new CPropertyAction (this, &Tritor::OnTriggerTimeZ);
	CreateProperty(g_Trigger_TimeZ, "1", MM::Integer, false, pAct);
    SetPropertyLimits(g_Trigger_TimeZ, 1, 255);
	pAct = new CPropertyAction (this, &Tritor::OnTriggerTypeX);
	CreateProperty(g_Trigger_GeneratorX, g_Trigger_Off, MM::String, false, pAct);
	AddAllowedValue(g_Trigger_GeneratorX, g_Trigger_Off);
	AddAllowedValue(g_Trigger_GeneratorX, g_Trigger_Rising);
	AddAllowedValue(g_Trigger_GeneratorX, g_Trigger_Falling);
	AddAllowedValue(g_Trigger_GeneratorX, g_Trigger_Both);
	pAct = new CPropertyAction (this, &Tritor::OnTriggerTypeY);
	CreateProperty(g_Trigger_GeneratorY, g_Trigger_Off, MM::String, false, pAct);
	AddAllowedValue(g_Trigger_GeneratorY, g_Trigger_Off);
	AddAllowedValue(g_Trigger_GeneratorY, g_Trigger_Rising);
	AddAllowedValue(g_Trigger_GeneratorY, g_Trigger_Falling);
	AddAllowedValue(g_Trigger_GeneratorY, g_Trigger_Both);
	pAct = new CPropertyAction (this, &Tritor::OnTriggerTypeZ);
	CreateProperty(g_Trigger_GeneratorZ, g_Trigger_Off, MM::String, false, pAct);
	AddAllowedValue(g_Trigger_GeneratorZ, g_Trigger_Off);
	AddAllowedValue(g_Trigger_GeneratorZ, g_Trigger_Rising);
	AddAllowedValue(g_Trigger_GeneratorZ, g_Trigger_Falling);
	AddAllowedValue(g_Trigger_GeneratorZ, g_Trigger_Both);
/**/
	ret = UpdateStatus();
	if (ret != DEVICE_OK)
      return ret;

	initialized_ = true;
	return DEVICE_OK;
}

int Tritor::SetPositionUm(double x, double y, double z)
{	
	int ret;	
	GetStatus(chx_.stat_,&chx_);
	if(chx_.loop_){
		chx_.pos_=x;
		ret  =SetCommandValue("set",xChannel_,x);
	}else{
		chx_.pos_=x;
		chx_.voltage_=(chx_.max_V_-chx_.min_V_)*(x-chx_.min_um_)/(chx_.max_um_-chx_.min_um_)+chx_.min_V_;
		ret = SetCommandValue("set",xChannel_,chx_.voltage_);
	}
	if (ret != DEVICE_OK)
      return ret;	
	GetStatus(chy_.stat_,&chy_);
	if(chy_.loop_){
		chy_.pos_=y;
		ret = SetCommandValue("set",yChannel_,y);
	}else{
		chy_.pos_=y;
		chy_.voltage_=(chy_.max_V_-chy_.min_V_)*(y-chy_.min_um_)/(chy_.max_um_-chy_.min_um_)+chy_.min_V_;
		ret = SetCommandValue("set",yChannel_,chy_.voltage_);
	}	
	if (ret != DEVICE_OK)
      return ret;	
	GetStatus(chz_.stat_,&chz_);
	if(chz_.loop_){
		chz_.pos_=z;
		ret = SetCommandValue("set",zChannel_,z);
	}else{
		chz_.pos_=z;
		chz_.voltage_=(chz_.max_V_-chz_.min_V_)*(z-chz_.min_um_)/(chz_.max_um_-chz_.min_um_)+chz_.min_V_;
		ret = SetCommandValue("set",zChannel_,chz_.voltage_);
	}	
	if (ret != DEVICE_OK)
      return ret;
	
	return DEVICE_OK;
}

int Tritor::GetPositionUm(double& x, double& y, double& z){	
	GetStatus(chx_.stat_,&chx_);
	if(chx_.loop_){
		GetCommandValue("mess",xChannel_,x);
		chx_.pos_=x;
		chx_.voltage_=(chx_.max_V_-chx_.min_V_)*(chx_.pos_-chx_.min_um_)/(chx_.max_um_-chx_.min_um_)+chx_.min_V_;
	}else{
		GetCommandValue("mess",xChannel_,chx_.voltage_);
		chx_.pos_=(chx_.max_um_-chx_.min_um_)*(chx_.voltage_-chx_.min_V_)/(chx_.max_V_-chx_.min_V_)+chx_.min_um_;
		x=chx_.pos_;
	}	
	GetStatus(chy_.stat_,&chy_);
	if(chy_.loop_){
		GetCommandValue("mess",yChannel_,y);
		chy_.pos_=y;
		chy_.voltage_=(chy_.max_V_-chy_.min_V_)*(chy_.pos_-chy_.min_um_)/(chy_.max_um_-chy_.min_um_)+chy_.min_V_;
	}else{
		GetCommandValue("mess",yChannel_,chy_.voltage_);
		chy_.pos_=(chy_.max_um_-chy_.min_um_)*(chy_.voltage_-chy_.min_V_)/(chy_.max_V_-chy_.min_V_)+chy_.min_um_;
		y=chy_.pos_;
	}
	GetStatus(chz_.stat_,&chz_);
	if(chz_.loop_){
		GetCommandValue("mess",zChannel_,z);
		chz_.pos_=z;
		chz_.voltage_=(chz_.max_V_-chz_.min_V_)*(chz_.pos_-chz_.min_um_)/(chz_.max_um_-chz_.min_um_)+chz_.min_V_;
	}else{
		GetCommandValue("mess",zChannel_,chz_.voltage_);
		chz_.pos_=(chz_.max_um_-chz_.min_um_)*(chz_.voltage_-chz_.min_V_)/(chz_.max_V_-chz_.min_V_)+chz_.min_um_;
		z=chz_.pos_;
	}
	return DEVICE_OK;
}
int Tritor::OnChannelX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	long c;
	if (eAct == MM::BeforeGet)
    {			
		c=xChannel_+1;
		pProp->Set(c);		
	}
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(c);
		xChannel_=(int)c-1;
		chx_.channel_=xChannel_;
	}
    return DEVICE_OK;
}
int Tritor::OnChannelY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	double c;
	if (eAct == MM::BeforeGet)
    {	
		c=yChannel_+1;
		pProp->Set(c);		
	}
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(c);
		yChannel_=(int)c-1;
		chy_.channel_=yChannel_;
	}
    return DEVICE_OK;
}
int Tritor::OnChannelZ(MM::PropertyBase* pProp, MM::ActionType eAct){	
	double c;
	if (eAct == MM::BeforeGet)
    {
		c=zChannel_+1;
		pProp->Set(c);		
	}
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(c);
		zChannel_=(int)c-1;
		chz_.channel_=zChannel_;
	}
    return DEVICE_OK;
}
int Tritor::OnStatX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int s;
	if (eAct == MM::BeforeGet)
    {	
		GetStatus(s,&chx_);	
		chx_.stat_=s;
		pProp->Set((long)s);		
	}   
    return DEVICE_OK;
}
int Tritor::OnStatY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int s;
	if (eAct == MM::BeforeGet)
    {	
		GetStatus(s,&chy_);
		chy_.stat_=s;
		pProp->Set((long)s);		
	}   
    return DEVICE_OK;
}
int Tritor::OnStatZ(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int s;
	if (eAct == MM::BeforeGet)
    {	
		GetStatus(s,&chz_);
		chz_.stat_=s;
		pProp->Set((long)s);		
	}   
    return DEVICE_OK;
}
int Tritor::OnTempX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("ktemp",xChannel_,chx_.ktemp_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set(chx_.ktemp_);
	}
	return DEVICE_OK;
}
int Tritor::OnTempY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){			
		int ret = GetCommandValue("ktemp",yChannel_,chy_.ktemp_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set(chy_.ktemp_);
	}
	return DEVICE_OK;
}
int Tritor::OnTempZ(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){			
		int ret = GetCommandValue("ktemp",zChannel_,chz_.ktemp_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set(chz_.ktemp_);
	}
	return DEVICE_OK;
}
int Tritor::OnTimeX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("rohm",xChannel_,chx_.rohm_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set((long)chx_.rohm_);
	}
	return DEVICE_OK;
}
int Tritor::OnTimeY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("rohm",yChannel_,chy_.rohm_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set((long)chy_.rohm_);
	}
	return DEVICE_OK;
}
int Tritor::OnTimeZ(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("rohm",zChannel_,chz_.rohm_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set((long)chz_.rohm_);
	}
	return DEVICE_OK;
}
int Tritor::OnLoopX(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
    {	
		int stat;
		int ret=GetStatus(stat,&chx_);
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.loop_){
			pProp->Set(g_Loop_close);			
		}else{
			pProp->Set(g_Loop_open);			
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string loop;
      pProp->Get(loop);
	  if (loop == g_Loop_close){
         chx_.loop_ = true;
	  }else{
         chx_.loop_ = false;
	  }	
	  int i=(chx_.loop_)?1:0;
	  int ret = SetCommandValue("cl",chx_.channel_,i);
	  if (ret!=DEVICE_OK)
			return ret;	 
	}
    return DEVICE_OK;
}
int Tritor::OnLoopY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int l=0;
	if (eAct == MM::BeforeGet)
    {		
		int stat;
		int ret=GetStatus(stat,&chy_);
		if (ret!=DEVICE_OK)
			return ret;
		if (chy_.loop_){
			pProp->Set(g_Loop_close);
			l=1;
		}else{
			pProp->Set(g_Loop_open);
			l=0;
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string loop;
      pProp->Get(loop);
	  if (loop == g_Loop_close){
         chy_.loop_ = true;
	  }else{
         chy_.loop_ = false;
	  }	  
	   int i=(chy_.loop_)?1:0;
	  int ret = SetCommandValue("cl",chy_.channel_,i);
	  if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnLoopZ(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int l=0;
	if (eAct == MM::BeforeGet)
    {		
		int stat;
		int ret=GetStatus(stat,&chz_);
		if (ret!=DEVICE_OK)
			return ret;
		if (chz_.loop_){
			pProp->Set(g_Loop_close);
			l=1;
		}else{
			pProp->Set(g_Loop_open);
			l=0;
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string loop;
      pProp->Get(loop);
	  if (loop == g_Loop_close){
         chz_.loop_ = true;
	  }else{
         chz_.loop_ = false;
	  }	 
	   int i=(chz_.loop_)?1:0;
	  int ret = SetCommandValue("cl",chz_.channel_,i);
	  if (ret!=DEVICE_OK)
			return ret;	 	
	}
    return DEVICE_OK;
}
int Tritor::OnPositionX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){	
		int ret = GetPos(chx_.pos_,&chx_);
		if (ret!=DEVICE_OK)
			return ret;		
		pProp->Set(chx_.pos_);
	}
    else if (eAct == MM::AfterSet){
		int ret=0;
		pProp->Get(chx_.pos_);		
		ret = SetPos(chx_.pos_,&chx_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnPositionY(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet){	
		int ret = GetPos(chy_.pos_,&chy_);
		if (ret!=DEVICE_OK)
			return ret;		
		pProp->Set(chy_.pos_);
	}
    else if (eAct == MM::AfterSet){
		int ret=0;
		pProp->Get(chy_.pos_);		
		ret = SetPos(chy_.pos_,&chy_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnPositionZ(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet){	
		int ret = GetPos(chz_.pos_,&chz_);
		if (ret!=DEVICE_OK)
			return ret;		
		pProp->Set(chz_.pos_);
	}
    else if (eAct == MM::AfterSet){
		int ret=0;
		pProp->Get(chz_.pos_);		
		ret = SetPos(chz_.pos_,&chz_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSoftstartX(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet){	
		int b=0;		
		int ret = GetCommandValue("fenable",chx_.channel_,b);
		chx_.fenable_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.fenable_){
			pProp->Set(g_Fenable_On);			
		}
		else{
			pProp->Set(g_Fenable_Off);			
		}		
	}
    else if (eAct == MM::AfterSet){	  
		std::string softstart;
		int b=0;
		pProp->Get(softstart);
		if (softstart == g_Fenable_On){
			chx_.fenable_ = true;
			b=1;
		}else{
			chx_.fenable_ = false;
			b=0;
		}		
		int ret = SetCommandValue("fenable",chx_.channel_,b);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSoftstartY(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet){	
		int b=0;		
		int ret = GetCommandValue("fenable",chy_.channel_,b);
		chy_.fenable_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chy_.fenable_){
			pProp->Set(g_Fenable_On);			
		}
		else{
			pProp->Set(g_Fenable_Off);			
		}		
	}
    else if (eAct == MM::AfterSet){	  
		std::string softstart;
		int b=0;
		pProp->Get(softstart);
		if (softstart == g_Fenable_On){
			chy_.fenable_ = true;
			b=1;
		}else{
			chy_.fenable_ = false;
			b=0;
		}		
		int ret = SetCommandValue("fenable",chy_.channel_,b);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSoftstartZ(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet){	
		int b=0;
		int ret = GetCommandValue("fenable",chz_.channel_,b);
		chz_.fenable_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chz_.fenable_){
			pProp->Set(g_Fenable_On);			
		}
		else{
			pProp->Set(g_Fenable_Off);			
		}		
	}
    else if (eAct == MM::AfterSet){	  
		std::string softstart;
		int b=0;
		pProp->Get(softstart);
		if (softstart == g_Fenable_On){
			chz_.fenable_ = true;
			b=1;
		}else{
			chz_.fenable_ = false;
			b=0;
		}
		int ret = SetCommandValue("fenable",chz_.channel_,b);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSlewRateX(MM::PropertyBase* pProp, MM::ActionType eAct){		
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("sr",chx_.channel_,chx_.sr_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)chx_.sr_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.sr_);		
		int ret = SetCommandValue("sr",chx_.channel_,chx_.sr_);		
		if (ret != DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnSlewRateY(MM::PropertyBase* pProp, MM::ActionType eAct){			
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("sr",chy_.channel_,chy_.sr_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)chy_.sr_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.sr_);		
		ret = SetCommandValue("sr",chy_.channel_,chy_.sr_);		
		if (ret != DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnSlewRateZ(MM::PropertyBase* pProp, MM::ActionType eAct){			
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("sr",chz_.channel_,chz_.sr_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)chz_.sr_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.sr_);		
		ret = SetCommandValue("sr",chz_.channel_,chz_.sr_);		
		if (ret != DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnModulInputX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int l=0;
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("modon",chx_.channel_,l);
		chx_.modon_=(l==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.modon_){
			pProp->Set(g_Modon_On);			
		}
		else{
			pProp->Set(g_Modon_Off);			
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string modon;
      pProp->Get(modon);
	  if (modon == g_Modon_On){
         chx_.modon_ = true;
	  }else{
         chx_.modon_ = false;
	  }
	  l=(chx_.modon_)?1:0;
	  int ret = SetCommandValue("modon",chx_.channel_,l);	  
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnModulInputY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int l=0;
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("modon",chy_.channel_,l);
		chy_.modon_=(l==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chy_.modon_){
			pProp->Set(g_Modon_On);			
		}else{
			pProp->Set(g_Modon_Off);			
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string modon;
      pProp->Get(modon);
	  if (modon == g_Modon_On){
         chy_.modon_ = true;
	  }else{
         chy_.modon_ = false;
	  }
	  l=(chy_.modon_)?1:0;
	  int ret = SetCommandValue("modon",chy_.channel_,l);	  
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnModulInputZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	int l=0;
	if (eAct == MM::BeforeGet)
    {		
		int ret = GetCommandValue("modon",chz_.channel_,l);
		chz_.modon_=(l==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chz_.modon_){
			pProp->Set(g_Modon_On);			
		}else{
			pProp->Set(g_Modon_Off);			
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string modon;
      pProp->Get(modon);
	  if (modon == g_Modon_On){
         chz_.modon_ = true;
	  }else{
         chz_.modon_ = false;
	  }
	  l=(chz_.modon_)?1:0;
	  int ret = SetCommandValue("modon",chz_.channel_,l);	  
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnMonitorX(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("monsrc",chx_.channel_,chx_.monsrc_);
		if (ret!=DEVICE_OK)
			return ret;				
		switch (chx_.monsrc_){
		case 0:
			pProp->Set(g_Monsrc_0);	break;
		case 1:
			pProp->Set(g_Monsrc_1);	break;
		case 2:
			pProp->Set(g_Monsrc_2);	break;
		case 3:
			pProp->Set(g_Monsrc_3);	break;
		case 4:
			pProp->Set(g_Monsrc_4);	break;
		case 5:
			pProp->Set(g_Monsrc_5);	break;
		case 6:
			pProp->Set(g_Monsrc_6);	break;
		default:
			pProp->Set(g_Monsrc_0);
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string mon;
		pProp->Get(mon);
		if (mon == g_Monsrc_0)
         chx_.monsrc_ = 0;
		else if (mon == g_Monsrc_1)
         chx_.monsrc_ = 1;
		else if (mon == g_Monsrc_2)
         chx_.monsrc_ = 2;
		else if (mon == g_Monsrc_3)
         chx_.monsrc_ = 3;
		else if (mon == g_Monsrc_4)
         chx_.monsrc_ = 4;
		else if (mon == g_Monsrc_5)
         chx_.monsrc_ = 5;	
		else if (mon == g_Monsrc_6)
         chx_.monsrc_ = 6;	
		int ret = SetCommandValue("monsrc",chx_.channel_,chx_.monsrc_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnMonitorY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("monsrc",chy_.channel_,chy_.monsrc_);
		if (ret!=DEVICE_OK)
			return ret;				
		switch (chy_.monsrc_){
		case 0:
			pProp->Set(g_Monsrc_0);	break;
		case 1:
			pProp->Set(g_Monsrc_1);	break;
		case 2:
			pProp->Set(g_Monsrc_2);	break;
		case 3:
			pProp->Set(g_Monsrc_3);	break;
		case 4:
			pProp->Set(g_Monsrc_4);	break;
		case 5:
			pProp->Set(g_Monsrc_5);	break;
		case 6:
			pProp->Set(g_Monsrc_6);	break;
		default:
			pProp->Set(g_Monsrc_0);
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string mon;
		pProp->Get(mon);
		if (mon == g_Monsrc_0)
         chy_.monsrc_ = 0;
		else if (mon == g_Monsrc_1)
         chy_.monsrc_ = 1;
		else if (mon == g_Monsrc_2)
         chy_.monsrc_ = 2;
		else if (mon == g_Monsrc_3)
         chy_.monsrc_ = 3;
		else if (mon == g_Monsrc_4)
         chy_.monsrc_ = 4;
		else if (mon == g_Monsrc_5)
         chy_.monsrc_ = 5;	
		else if (mon == g_Monsrc_6)
         chy_.monsrc_ = 6;	
		int ret = SetCommandValue("monsrc",chy_.channel_,chy_.monsrc_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnMonitorZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("monsrc",chz_.channel_,chz_.monsrc_);
		if (ret!=DEVICE_OK)
			return ret;				
		switch (chz_.monsrc_){
		case 0:
			pProp->Set(g_Monsrc_0);	break;
		case 1:
			pProp->Set(g_Monsrc_1);	break;
		case 2:
			pProp->Set(g_Monsrc_2);	break;
		case 3:
			pProp->Set(g_Monsrc_3);	break;
		case 4:
			pProp->Set(g_Monsrc_4);	break;
		case 5:
			pProp->Set(g_Monsrc_5);	break;
		case 6:
			pProp->Set(g_Monsrc_6);	break;
		default:
			pProp->Set(g_Monsrc_0);
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string mon;
		pProp->Get(mon);
		if (mon == g_Monsrc_0)
         chz_.monsrc_ = 0;
		else if (mon == g_Monsrc_1)
         chz_.monsrc_ = 1;
		else if (mon == g_Monsrc_2)
         chz_.monsrc_ = 2;
		else if (mon == g_Monsrc_3)
         chz_.monsrc_ = 3;
		else if (mon == g_Monsrc_4)
         chz_.monsrc_ = 4;
		else if (mon == g_Monsrc_5)
         chz_.monsrc_ = 5;	
		else if (mon == g_Monsrc_6)
         chz_.monsrc_ = 6;	
		int ret = SetCommandValue("monsrc",chz_.channel_,chz_.monsrc_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnPidPX(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("kp",chx_.channel_,chx_.kp_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.kp_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.kp_);	
		ret = SetCommandValue("kp",chx_.channel_,chx_.kp_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnPidPY(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("kp",chy_.channel_,chy_.kp_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.kp_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.kp_);
		ret = SetCommandValue("kp",chy_.channel_,chy_.kp_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnPidPZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("kp",chz_.channel_,chz_.kp_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.kp_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.kp_);	
		ret = SetCommandValue("kp",chz_.channel_,chz_.kp_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnPidIX(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("ki",chx_.channel_,chx_.ki_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.ki_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.ki_);
		ret = SetCommandValue("ki",chx_.channel_,chx_.ki_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnPidIY(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("ki",chy_.channel_,chy_.ki_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.ki_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.ki_);	
		ret = SetCommandValue("ki",chy_.channel_,chy_.ki_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnPidIZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("ki",chz_.channel_,chz_.ki_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.ki_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.ki_);	
		ret = SetCommandValue("ki",chz_.channel_,chz_.ki_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnPidDX(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("kd",chx_.channel_,chx_.kd_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.kd_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.kd_);
		ret = SetCommandValue("kd",chx_.channel_,chx_.kd_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnPidDY(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("kd",chy_.channel_,chy_.kd_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.kd_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.kd_);
		ret = SetCommandValue("kd",chy_.channel_,chy_.kd_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnPidDZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("kd",chz_.channel_,chz_.kd_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.kd_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.kd_);	
		ret = SetCommandValue("kd",chz_.channel_,chz_.kd_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnNotchX(MM::PropertyBase* pProp, MM::ActionType eAct){
	int b=0;
	if (eAct == MM::BeforeGet){
		int ret = GetCommandValue("notchon",chx_.channel_,b);
		chx_.notchon_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.notchon_){
			pProp->Set(g_Notch_On);			
		}else{
			pProp->Set(g_Notch_Off);			
		}		
	}else if (eAct == MM::AfterSet){	  
	  std::string notch;
      pProp->Get(notch);
	  if (notch == g_Notch_On){
         chx_.notchon_ = true;
	  }else{
         chx_.notchon_ = false;
	  }
	  b=(chx_.kd_)?1:0;
	  int ret = SetCommandValue("notchon",chx_.channel_,b);
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnNotchY(MM::PropertyBase* pProp, MM::ActionType eAct){
	int b=0;
	if (eAct == MM::BeforeGet){
		int ret = GetCommandValue("notchon",chy_.channel_,b);
		chy_.notchon_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.notchon_){
			pProp->Set(g_Notch_On);			
		}else{
			pProp->Set(g_Notch_Off);			
		}		
	}else if (eAct == MM::AfterSet){	  
	  std::string notch;
      pProp->Get(notch);
	  if (notch == g_Notch_On){
         chy_.notchon_ = true;
	  }else{
         chy_.notchon_ = false;
	  }
	  b=(chy_.kd_)?1:0;
	  int ret = SetCommandValue("notchon",chy_.channel_,b);
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnNotchZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	int b=0;
	if (eAct == MM::BeforeGet){
		int ret = GetCommandValue("notchon",chz_.channel_,b);
		chz_.notchon_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chz_.notchon_){
			pProp->Set(g_Notch_On);			
		}else{
			pProp->Set(g_Notch_Off);			
		}		
	}else if (eAct == MM::AfterSet){	  
	  std::string notch;
      pProp->Get(notch);
	  if (notch == g_Notch_On){
         chz_.notchon_ = true;
	  }else{
         chz_.notchon_ = false;
	  }
	  b=(chz_.kd_)?1:0;
	  int ret = SetCommandValue("notchon",chz_.channel_,b);
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnNotchFreqX(MM::PropertyBase* pProp, MM::ActionType eAct){
	long l;
	if (eAct == MM::BeforeGet){	
		int ret = GetCommandValue("notchf",chx_.channel_,chx_.notchf_);
		if (ret != DEVICE_OK)
			return ret;
		SetPropertyLimits(g_Notch_BandX, 0, ((2*chx_.notchf_)<=20000)?(2*chx_.notchf_):20000);
		l=chx_.notchf_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chx_.notchf_=(int)l;
		int ret = SetCommandValue("notchf",chx_.channel_,chx_.notchf_);
		if (ret!=DEVICE_OK)
			return ret;
		//set limit bandwidth to max 2*notch_frequency
		SetPropertyLimits(g_Notch_BandX, 0, ((2*chx_.notchf_)<=20000)?(2*chx_.notchf_):20000);		
	}
    return DEVICE_OK;
}
int Tritor::OnNotchFreqY(MM::PropertyBase* pProp, MM::ActionType eAct){
	long l;
	if (eAct == MM::BeforeGet){	
		int ret = GetCommandValue("notchf",chy_.channel_,chy_.notchf_);
		if (ret != DEVICE_OK)
			return ret;
		SetPropertyLimits(g_Notch_BandY, 0, ((2*chy_.notchf_)<=20000)?(2*chy_.notchf_):20000);
		l=chy_.notchf_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chy_.notchf_=(int)l;
		int ret = SetCommandValue("notchf",chy_.channel_,chy_.notchf_);
		if (ret!=DEVICE_OK)
			return ret;
		//set limit bandwidth to max 2*notch_frequency
		SetPropertyLimits(g_Notch_BandY, 0, ((2*chy_.notchf_)<=20000)?(2*chy_.notchf_):20000);			
	}
    return DEVICE_OK;
}
int Tritor::OnNotchFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct){	
	long l;
	if (eAct == MM::BeforeGet){	
		int ret = GetCommandValue("notchf",chz_.channel_,chz_.notchf_);
		if (ret != DEVICE_OK)
			return ret;
		SetPropertyLimits(g_Notch_BandZ, 0, ((2*chz_.notchf_)<=20000)?(2*chz_.notchf_):20000);
		l=chz_.notchf_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chz_.notchf_=(int)l;
		int ret = SetCommandValue("notchf",chz_.channel_,chz_.notchf_);
		if (ret!=DEVICE_OK)
			return ret;
		//set limit bandwidth to max 2*notch_frequency
		SetPropertyLimits(g_Notch_BandZ, 0, ((2*chz_.notchf_)<=20000)?(2*chz_.notchf_):20000);		
		
	}
    return DEVICE_OK;
}
int Tritor::OnNotchBandX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("notchb",chx_.channel_,chx_.notchb_);		
		if (ret != DEVICE_OK)
			return ret;
		l=chx_.notchb_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chx_.notchb_=(int)l;
		int ret = SetCommandValue("notchb",chx_.channel_,chx_.notchb_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnNotchBandY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNotchBandY",true);
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("notchb",chy_.channel_,chy_.notchb_);
		if (ret != DEVICE_OK)
			return ret;
		l=chy_.notchb_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chy_.notchb_=(int)l;
		int ret = SetCommandValue("notchb",chy_.channel_,chy_.notchb_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnNotchBandZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNotchBandZ",true);
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("notchb",chz_.channel_,chz_.notchb_);
		if (ret != DEVICE_OK)
			return ret;
		l=chz_.notchb_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chz_.notchb_=(int)l;
		int ret = SetCommandValue("notchb",chz_.channel_,chz_.notchb_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnLowpassX(MM::PropertyBase* pProp, MM::ActionType eAct){
	int l=0;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpon",chx_.channel_,l);
		chx_.lpon_=(l==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.lpon_){
			pProp->Set(g_Lowpass_On);			
		}else{
			pProp->Set(g_Lowpass_Off);			
		}		
	}else if (eAct == MM::AfterSet){
	  std::string lpon;
      pProp->Get(lpon);
	  if (lpon == g_Lowpass_On){
		chx_.lpon_ = true;
	  }else{
         chx_.lpon_ = false;
	  }
	  	l=(chx_.lpon_)?1:0;
	  int ret = SetCommandValue("lpon",chx_.channel_,l);	
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnLowpassY(MM::PropertyBase* pProp, MM::ActionType eAct){
	int l=0;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpon",chy_.channel_,l);
		chy_.lpon_=(l==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chy_.lpon_){
			pProp->Set(g_Lowpass_On);			
		}else{
			pProp->Set(g_Lowpass_Off);			
		}		
	}else if (eAct == MM::AfterSet){
	  std::string lpon;
      pProp->Get(lpon);
	  if (lpon == g_Lowpass_On){
		chy_.lpon_ = true;
	  }else{
         chy_.lpon_ = false;
	  }
	  l=(chy_.lpon_)?1:0;
	  int ret = SetCommandValue("lpon",chy_.channel_,l);	
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnLowpassZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	int l=0;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpon",chz_.channel_,l);
		chz_.lpon_=(l==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chz_.lpon_){
			pProp->Set(g_Lowpass_On);			
		}else{
			pProp->Set(g_Lowpass_Off);			
		}		
	}else if (eAct == MM::AfterSet){
	  std::string lpon;
      pProp->Get(lpon);
	  if (lpon == g_Lowpass_On){
		chz_.lpon_ = true;
	  }else{
         chz_.lpon_ = false;
	  }
	  	l=(chz_.lpon_)?1:0;
	  int ret = SetCommandValue("lpon",chz_.channel_,l);	
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnLowpassFreqX(MM::PropertyBase* pProp, MM::ActionType eAct){
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpf",chx_.channel_,chx_.lpf_);
		if (ret != DEVICE_OK)
			return ret;		
		l=chx_.lpf_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chx_.lpf_=(int)l;
		int ret = SetCommandValue("lpf",chx_.channel_,chx_.lpf_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnLowpassFreqY(MM::PropertyBase* pProp, MM::ActionType eAct){
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpf",chy_.channel_,chy_.lpf_);
		if (ret != DEVICE_OK)
			return ret;		
		l=chy_.lpf_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chy_.lpf_=(int)l;
		int ret = SetCommandValue("lpf",chy_.channel_,chy_.lpf_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnLowpassFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpf",chz_.channel_,chz_.lpf_);
		if (ret != DEVICE_OK)
			return ret;		
		l=chz_.lpf_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chz_.lpf_=(int)l;
		int ret = SetCommandValue("lpf",chz_.channel_,chz_.lpf_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnGenerateX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("gfkt",chx_.channel_,chx_.gfkt_);
		if (ret!=DEVICE_OK)
			return ret;
		switch (chx_.gfkt_){
		case 0:
			pProp->Set(g_Generator_Off);		break;
		case 1:
			pProp->Set(g_Generator_Sine);		break;
		case 2:
			pProp->Set(g_Generator_Tri);		break;
		case 3:
			pProp->Set(g_Generator_Rect);	break;
		case 4:
			pProp->Set(g_Generator_Noise);	break;
		case 5:
			pProp->Set(g_Generator_Sweep);	break;
		default:
			pProp->Set(g_Generator_Off);
		}
	}else if (eAct == MM::AfterSet){		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Generator_Off)
         chx_.gfkt_ = 0;
		else if (gen == g_Generator_Sine)
         chx_.gfkt_ = 1;
		else if (gen == g_Generator_Tri)
         chx_.gfkt_ = 2;
		else if (gen == g_Generator_Rect)
         chx_.gfkt_ = 3;
		else if (gen == g_Generator_Noise)
         chx_.gfkt_ = 4;
		else if (gen == g_Generator_Sweep)
         chx_.gfkt_ = 5;	
		int ret = SetCommandValue("gfkt",chx_.channel_,chx_.gfkt_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnGenerateY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){	
		int ret = GetCommandValue("gfkt",chy_.channel_,chy_.gfkt_);
		if (ret!=DEVICE_OK)
			return ret;
		switch (chy_.gfkt_){
		case 0:
			pProp->Set(g_Generator_Off);	break;
		case 1:
			pProp->Set(g_Generator_Sine);	break;
		case 2:
			pProp->Set(g_Generator_Tri);	break;
		case 3:
			pProp->Set(g_Generator_Rect);	break;
		case 4:
			pProp->Set(g_Generator_Noise);	break;
		case 5:
			pProp->Set(g_Generator_Sweep);	break;
		default:
			pProp->Set(g_Generator_Off);
		}
	}else if (eAct == MM::AfterSet){		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Generator_Off)
         chy_.gfkt_ = 0;
		else if (gen == g_Generator_Sine)
         chy_.gfkt_ = 1;
		else if (gen == g_Generator_Tri)
         chy_.gfkt_ = 2;
		else if (gen == g_Generator_Rect)
         chy_.gfkt_ = 3;
		else if (gen == g_Generator_Noise)
         chy_.gfkt_ = 4;
		else if (gen == g_Generator_Sweep)
         chy_.gfkt_ = 5;		
		int ret = SetCommandValue("gfkt",chy_.channel_,chy_.gfkt_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnGenerateZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){	
		int ret = GetCommandValue("gfkt",chz_.channel_,chz_.gfkt_);
		if (ret!=DEVICE_OK)
			return ret;
		switch (chz_.gfkt_){
		case 0:
			pProp->Set(g_Generator_Off);	break;
		case 1:
			pProp->Set(g_Generator_Sine);	break;
		case 2:
			pProp->Set(g_Generator_Tri);	break;
		case 3:
			pProp->Set(g_Generator_Rect);	break;
		case 4:
			pProp->Set(g_Generator_Noise);	break;
		case 5:
			pProp->Set(g_Generator_Sweep);	break;
		default:
			pProp->Set(g_Generator_Off);
		}
	}else if (eAct == MM::AfterSet){		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Generator_Off)
         chz_.gfkt_ = 0;
		else if (gen == g_Generator_Sine)
         chz_.gfkt_ = 1;
		else if (gen == g_Generator_Tri)
         chz_.gfkt_ = 2;
		else if (gen == g_Generator_Rect)
         chz_.gfkt_ = 3;
		else if (gen == g_Generator_Noise)
         chz_.gfkt_ = 4;
		else if (gen == g_Generator_Sweep)
         chz_.gfkt_ = 5;	
		int ret = SetCommandValue("gfkt",chz_.channel_,chz_.gfkt_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnSinAmpX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gasin",chx_.channel_,chx_.gasin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gasin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gasin_);
		int ret = SetCommandValue("gasin",chx_.channel_,chx_.gasin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSinAmpY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gasin",chy_.channel_,chy_.gasin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gasin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gasin_);
		int ret = SetCommandValue("gasin",chy_.channel_,chy_.gasin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSinAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gasin",chz_.channel_,chz_.gasin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gasin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gasin_);
		int ret = SetCommandValue("gasin",chz_.channel_,chz_.gasin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSinOffX(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("gosin",chx_.channel_,chx_.gosin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gosin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gosin_);
		ret = SetCommandValue("gosin",chx_.channel_,chx_.gosin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSinOffY(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("gosin",chy_.channel_,chy_.gosin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gosin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gosin_);
		ret = SetCommandValue("gosin",chy_.channel_,chy_.gosin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSinOffZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("gosin",chz_.channel_,chz_.gosin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gosin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gosin_);
		ret = SetCommandValue("gosin",chz_.channel_,chz_.gosin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSinFreqX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gfsin",chx_.channel_,chx_.gfsin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gfsin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gfsin_);
		int ret = SetCommandValue("gfsin",chx_.channel_,chx_.gfsin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSinFreqY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gfsin",chy_.channel_,chy_.gfsin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gfsin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gfsin_);
		int ret = SetCommandValue("gfsin",chy_.channel_,chy_.gfsin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSinFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gfsin",chz_.channel_,chz_.gfsin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gfsin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gfsin_);
		int ret = SetCommandValue("gfsin",chz_.channel_,chz_.gfsin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnTriAmpX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gatri",chx_.channel_,chx_.gatri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gatri_);
	}else if (eAct == MM::AfterSet){
		pProp->Get(chx_.gatri_);
		int ret = SetCommandValue("gatri",chx_.channel_,chx_.gatri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnTriAmpY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{	
		int ret = GetCommandValue("gatri",chy_.channel_,chy_.gatri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gatri_);
	}else if (eAct == MM::AfterSet){
		pProp->Get(chy_.gatri_);
		int ret = SetCommandValue("gatri",chy_.channel_,chy_.gatri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnTriAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{	
		int ret = GetCommandValue("gatri",chz_.channel_,chz_.gatri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gatri_);
	}else if (eAct == MM::AfterSet){
		pProp->Get(chz_.gatri_);
		int ret = SetCommandValue("gatri",chz_.channel_,chz_.gatri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnTriOffX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gotri",chx_.channel_,chx_.gotri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gotri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gotri_);
		int ret = SetCommandValue("gotri",chx_.channel_,chx_.gotri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnTriOffY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gotri",chy_.channel_,chy_.gotri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gotri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gotri_);
		int ret = SetCommandValue("gotri",chy_.channel_,chy_.gotri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnTriOffZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gotri",chz_.channel_,chz_.gotri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gotri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gotri_);
		int ret = SetCommandValue("gotri",chz_.channel_,chz_.gotri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnTriFreqX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gftri",chx_.channel_,chx_.gftri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gftri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gftri_);
		int ret = SetCommandValue("gftri",chx_.channel_,chx_.gftri_);
		//int ret = SetGftri(gftri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnTriFreqY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gftri",chy_.channel_,chy_.gftri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gftri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gftri_);
		int ret = SetCommandValue("gftri",chy_.channel_,chy_.gftri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnTriFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gftri",chz_.channel_,chz_.gftri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gftri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gftri_);
		int ret = SetCommandValue("gftri",chz_.channel_,chz_.gftri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnTriSymX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gstri",chx_.channel_,chx_.gstri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gstri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gstri_);
		int ret = SetCommandValue("gstri",chx_.channel_,chx_.gstri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnTriSymY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gstri",chy_.channel_,chy_.gstri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gstri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gstri_);
		int ret = SetCommandValue("gstri",chy_.channel_,chy_.gstri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnTriSymZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gstri",chz_.channel_,chz_.gstri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gstri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gstri_);
		int ret = SetCommandValue("gstri",chz_.channel_,chz_.gstri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnRecAmpX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("garec",chx_.channel_,chx_.garec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.garec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.garec_);
		int ret = SetCommandValue("garec",chx_.channel_,chx_.garec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnRecAmpY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("garec",chy_.channel_,chy_.garec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.garec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.garec_);
		int ret = SetCommandValue("garec",chy_.channel_,chy_.garec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnRecAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("garec",chz_.channel_,chz_.garec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.garec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.garec_);
		int ret = SetCommandValue("garec",chz_.channel_,chz_.garec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnRecOffX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gorec",chx_.channel_,chx_.gorec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gorec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gorec_);
		int ret = SetCommandValue("gorec",chx_.channel_,chx_.gorec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnRecOffY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gorec",chy_.channel_,chy_.gorec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gorec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gorec_);
		int ret = SetCommandValue("gorec",chy_.channel_,chy_.gorec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnRecOffZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gorec",chz_.channel_,chz_.gorec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gorec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gorec_);
		int ret = SetCommandValue("gorec",chz_.channel_,chz_.gorec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnRecFreqX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gfrec",chx_.channel_,chx_.gfrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gfrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gfrec_);
		int ret = SetCommandValue("gfrec",chx_.channel_,chx_.gfrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnRecFreqY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gfrec",chy_.channel_,chy_.gfrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gfrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gfrec_);
		int ret = SetCommandValue("gfrec",chy_.channel_,chy_.gfrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnRecFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gfrec",chz_.channel_,chz_.gfrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gfrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gfrec_);
		int ret = SetCommandValue("gfrec",chz_.channel_,chz_.gfrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnRecSymX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gsrec",chx_.channel_,chx_.gsrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gsrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gsrec_);
		int ret = SetCommandValue("gsrec",chx_.channel_,chx_.gsrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnRecSymY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gsrec",chy_.channel_,chy_.gsrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gsrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gsrec_);
		int ret = SetCommandValue("gsrec",chy_.channel_,chy_.gsrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnRecSymZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnRecSymZ",true);	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gsrec",chz_.channel_,chz_.gsrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gsrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gsrec_);
		int ret = SetCommandValue("gsrec",chz_.channel_,chz_.gsrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnNoiAmpX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNoiAmpX",true);	
	if (eAct == MM::BeforeGet)
	{	
		int ret = GetCommandValue("ganoi",chx_.channel_,chx_.ganoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.ganoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.ganoi_);
		int ret = SetCommandValue("ganoi",chx_.channel_,chx_.ganoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnNoiAmpY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNoiAmpY",true);	
	if (eAct == MM::BeforeGet)
	{	
		int ret = GetCommandValue("ganoi",chy_.channel_,chy_.ganoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.ganoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.ganoi_);
		int ret = SetCommandValue("ganoi",chy_.channel_,chy_.ganoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnNoiAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{	
		int ret = GetCommandValue("ganoi",chz_.channel_,chz_.ganoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.ganoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.ganoi_);
		int ret = SetCommandValue("ganoi",chz_.channel_,chz_.ganoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnNoiOffX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gonoi",chx_.channel_,chx_.gonoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gonoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gonoi_);
		int ret = SetCommandValue("gonoi",chx_.channel_,chx_.gonoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnNoiOffY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gonoi",chy_.channel_,chy_.gonoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gonoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gonoi_);
		int ret = SetCommandValue("gonoi",chy_.channel_,chy_.gonoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnNoiOffZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNoiOffZ",true);	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gonoi",chz_.channel_,chz_.gonoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gonoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gonoi_);
		int ret = SetCommandValue("gonoi",chz_.channel_,chz_.gonoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSweAmpX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gaswe",chx_.channel_,chx_.gaswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gaswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gaswe_);
		int ret = SetCommandValue("gaswe",chx_.channel_,chx_.gaswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSweAmpY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gaswe",chy_.channel_,chy_.gaswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gaswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gaswe_);
		int ret = SetCommandValue("gaswe",chy_.channel_,chy_.gaswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSweAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gaswe",chz_.channel_,chz_.gaswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gaswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gaswe_);
		int ret = SetCommandValue("gaswe",chz_.channel_,chz_.gaswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSweOffX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSweOffX");	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("goswe",chx_.channel_,chx_.goswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.goswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.goswe_);
		int ret = SetCommandValue("goswe",chx_.channel_,chx_.goswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSweOffY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("goswe",chy_.channel_,chy_.goswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.goswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.goswe_);
		int ret = SetCommandValue("goswe",chy_.channel_,chy_.goswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSweOffZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("goswe",chz_.channel_,chz_.goswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.goswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.goswe_);
		int ret = SetCommandValue("goswe",chz_.channel_,chz_.goswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSweTimeX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gtswe",chx_.channel_,chx_.gtswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gtswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gtswe_);
		int ret = SetCommandValue("gtswe",chx_.channel_,chx_.gtswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSweTimeY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gtswe",chy_.channel_,chy_.gtswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gtswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gtswe_);
		int ret = SetCommandValue("gtswe",chy_.channel_,chy_.gtswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnSweTimeZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gtswe",chz_.channel_,chz_.gtswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gtswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gtswe_);
		int ret = SetCommandValue("gtswe",chz_.channel_,chz_.gtswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Tritor::OnScanTypeX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("sct",chx_.channel_,chx_.sct_);				
		switch (chx_.sct_){
		case 0:
			pProp->Set(g_Scan_Type_Off);	break;
		case 1:
			pProp->Set(g_Scan_Type_Sine);	break;
		case 2:
			pProp->Set(g_Scan_Type_Tri);	break;		
		default:
			pProp->Set(g_Scan_Type_Off);
		}
		if (ret!=DEVICE_OK)
			return ret;
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Scan_Type_Off){
			chx_.sct_ = 0;
		}else if (gen == g_Scan_Type_Sine){
			chx_.sct_ = 1;
		}else if (gen == g_Scan_Type_Tri){
			chx_.sct_ = 2;	
		}
		int ret = SetCommandValue("sct",chx_.channel_,chx_.sct_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnScanTypeY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("sct",chy_.channel_,chy_.sct_);
			
		switch (chy_.sct_){
		case 0:
			pProp->Set(g_Scan_Type_Off);	break;
		case 1:
			pProp->Set(g_Scan_Type_Sine);	break;
		case 2:
			pProp->Set(g_Scan_Type_Tri);	break;		
		default:
			pProp->Set(g_Scan_Type_Off);
		}
		if (ret!=DEVICE_OK)
			return ret;		
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Scan_Type_Off){
			chy_.sct_ = 0;
		}else if (gen == g_Scan_Type_Sine){
			chy_.sct_ = 1;
		}else if (gen == g_Scan_Type_Tri){
			chy_.sct_ = 2;	
		}
		int ret = SetCommandValue("sct",chy_.channel_,chy_.sct_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnScanTypeZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("sct",chz_.channel_,chz_.sct_);
			
		switch (chz_.sct_){
		case 0:
			pProp->Set(g_Scan_Type_Off);	break;
		case 1:
			pProp->Set(g_Scan_Type_Sine);	break;
		case 2:
			pProp->Set(g_Scan_Type_Tri);	break;		
		default:
			pProp->Set(g_Scan_Type_Off);
		}
		if (ret!=DEVICE_OK)
			return ret;		
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Scan_Type_Off){
			chz_.sct_ = 0;
		}else if (gen == g_Scan_Type_Sine){
			chz_.sct_ = 1;
		}else if (gen == g_Scan_Type_Tri){
			chz_.sct_ = 2;	
		}
		int ret = SetCommandValue("sct",chz_.channel_,chz_.sct_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnScanX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {		
		int b=0;
		int ret = GetCommandValue("ss",chx_.channel_,b);
		chx_.ss_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;			
		if(chx_.ss_){
			pProp->Set(g_Scan_Starting);
		}else{
			pProp->Set(g_Scan_Off);	
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string s;
		int b=0;
		pProp->Get(s);
		if (s == g_Scan_Off){
			chx_.ss_ = false;
			b=0;
		}
		else if (s == g_Scan_Starting){
			chx_.ss_ = true;
			b=1;
		}
		if(chx_.ss_){
			int ret = SetCommandValue("ss",chx_.channel_,b);
			if (ret!=DEVICE_OK)
				return ret;		
		}
	}
    return DEVICE_OK;
}
int Tritor::OnScanY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {	
		int b=0;
		int ret = GetCommandValue("ss",chy_.channel_,b);
		chy_.ss_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;			
		if(chy_.ss_){
			pProp->Set(g_Scan_Starting);
		}else{
			pProp->Set(g_Scan_Off);	
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string s;
		int b=0;
		pProp->Get(s);
		if (s == g_Scan_Off){
			chy_.ss_ = false;
			b=0;
		}
		else if (s == g_Scan_Starting){
			chy_.ss_ = true;
			b=1;
		}
		if(chy_.ss_){
			int ret = SetCommandValue("ss",chy_.channel_,b);	
			if (ret!=DEVICE_OK)
				return ret;	
		}
	}
    return DEVICE_OK;
}
int Tritor::OnScanZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {	
		int b=0;
		int ret = GetCommandValue("ss",chz_.channel_,b);
		chz_.ss_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;			
		if(chz_.ss_){
			pProp->Set(g_Scan_Starting);
		}else{
			pProp->Set(g_Scan_Off);	
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string s;
		int b=0;
		pProp->Get(s);
		if (s == g_Scan_Off){
			chz_.ss_ = false;
			b=0;
		}
		else if (s == g_Scan_Starting){
			chz_.ss_ = true;
			b=1;
		}
		if(chz_.ss_){
			int ret = SetCommandValue("ss",chz_.channel_,b);	
			if (ret!=DEVICE_OK)
				return ret;	
		}
	}
    return DEVICE_OK;
}
int Tritor::OnTriggerStartX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("trgss",chx_.channel_,chx_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chx_.trgss_);		
	}else if (eAct == MM::AfterSet){		
		pProp->Get(chx_.trgss_);	
		int ret = SetCommandValue("trgss",chx_.channel_,chx_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnTriggerStartY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("trgss",chy_.channel_,chy_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chy_.trgss_);		
	}else if (eAct == MM::AfterSet){		
		pProp->Get(chy_.trgss_);	
		int ret = SetCommandValue("trgss",chy_.channel_,chy_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnTriggerStartZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){	
		int ret = GetCommandValue("trgss",chz_.channel_,chz_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chz_.trgss_);		
	}else if (eAct == MM::AfterSet){		
		pProp->Get(chz_.trgss_);	
		int ret = SetCommandValue("trgss",chz_.channel_,chz_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnTriggerEndX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("trgse",chx_.channel_,chx_.trgse_);
		if (ret!=DEVICE_OK)
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chx_.trgse_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chx_.trgse_);
		int ret = SetCommandValue("trgse",chx_.channel_,chx_.trgse_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
	return DEVICE_OK;
}
int Tritor::OnTriggerEndY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("trgse",chy_.channel_,chy_.trgse_);
		if (ret!=DEVICE_OK)
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chy_.trgse_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chy_.trgse_);	
		int ret = SetCommandValue("trgse",chy_.channel_,chy_.trgse_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
	return DEVICE_OK;
}
int Tritor::OnTriggerEndZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("trgse",chz_.channel_,chz_.trgse_);
		if (ret!=DEVICE_OK)
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chz_.trgse_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chz_.trgse_);	
		int ret = SetCommandValue("trgse",chz_.channel_,chz_.trgse_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
	return DEVICE_OK;
}
int Tritor::OnTriggerIntervalX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("trgsi",chx_.channel_,chx_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chx_.trgsi_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chx_.trgsi_);	
		int ret = SetCommandValue("trgsi",chx_.channel_,chx_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnTriggerIntervalY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("trgsi",chy_.channel_,chy_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chy_.trgsi_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chy_.trgsi_);
		int ret = SetCommandValue("trgsi",chy_.channel_,chy_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnTriggerIntervalZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("trgsi",chz_.channel_,chz_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chz_.trgsi_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chz_.trgsi_);	
		int ret = SetCommandValue("trgsi",chz_.channel_,chz_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnTriggerTimeX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("trglen",chx_.channel_,chx_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set((long)chx_.trglen_);		
	}
    else if (eAct == MM::AfterSet)
    {	
		long l;
		pProp->Get(l);
		chx_.trglen_=(int)l;
		int ret = SetCommandValue("trglen",chx_.channel_,chx_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnTriggerTimeY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("trglen",chy_.channel_,chy_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set((long)chy_.trglen_);		
	}
    else if (eAct == MM::AfterSet)
    {	
		long l;
		pProp->Get(l);
		chy_.trglen_=(int)l;
		int ret = SetCommandValue("trglen",chy_.channel_,chy_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnTriggerTimeZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("trglen",chz_.channel_,chz_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set((long)chz_.trglen_);		
	}
    else if (eAct == MM::AfterSet)
    {	
		long l;
		pProp->Get(l);
		chz_.trglen_=(int)l;
		int ret = SetCommandValue("trglen",chz_.channel_,chz_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnTriggerTypeX(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("trgedge",chx_.channel_,chx_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;			
		switch (chx_.trgedge_){
		case 0:
			pProp->Set(g_Trigger_Off);		break;
		case 1:
			pProp->Set(g_Trigger_Rising);	break;
		case 2:
			pProp->Set(g_Trigger_Falling);	break;	
		case 3:
			pProp->Set(g_Trigger_Both);		break;
		default:
			pProp->Set(g_Trigger_Off);
		}	
	}
    else if (eAct == MM::AfterSet)
    {			
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Trigger_Off)
			chx_.trgedge_ = 0;
		else if (gen == g_Trigger_Rising)
			chx_.trgedge_ = 1;
		else if (gen == g_Trigger_Falling)
			chx_.trgedge_ = 2;	
		else if (gen == g_Trigger_Both)
			chx_.trgedge_ = 3;
		int ret = SetCommandValue("trgedge",chx_.channel_,chx_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Tritor::OnTriggerTypeY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("trgedge",chy_.channel_,chy_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;			
		switch (chy_.trgedge_){
		case 0:
			pProp->Set(g_Trigger_Off);		break;
		case 1:
			pProp->Set(g_Trigger_Rising);	break;
		case 2:
			pProp->Set(g_Trigger_Falling);	break;	
		case 3:
			pProp->Set(g_Trigger_Both);		break;
		default:
			pProp->Set(g_Trigger_Off);
		}	
	}
    else if (eAct == MM::AfterSet)
    {			
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Trigger_Off)
			chy_.trgedge_ = 0;
		else if (gen == g_Trigger_Rising)
			chy_.trgedge_ = 1;
		else if (gen == g_Trigger_Falling)
			chy_.trgedge_ = 2;	
		else if (gen == g_Trigger_Both)
			chy_.trgedge_ = 3;
		int ret = SetCommandValue("trgedge",chy_.channel_,chy_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}



int Tritor::OnTriggerTypeZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {			
		int ret = GetCommandValue("trgedge",chz_.channel_,chz_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;			
		switch (chz_.trgedge_){
		case 0:
			pProp->Set(g_Trigger_Off);		break;
		case 1:
			pProp->Set(g_Trigger_Rising);	break;
		case 2:
			pProp->Set(g_Trigger_Falling);	break;	
		case 3:
			pProp->Set(g_Trigger_Both);		break;
		default:
			pProp->Set(g_Trigger_Off);
		}	
	}
    else if (eAct == MM::AfterSet)
    {			
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Trigger_Off)
			chz_.trgedge_ = 0;
		else if (gen == g_Trigger_Rising)
			chz_.trgedge_ = 1;
		else if (gen == g_Trigger_Falling)
			chz_.trgedge_ = 2;	
		else if (gen == g_Trigger_Both)
			chz_.trgedge_ = 3;		
		int ret = SetCommandValue("trgedge",chz_.channel_,chz_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}

/**/
///////////////////////////////////////////////////////////////////////////////
// Shutter 
// ~~~~~~~

Shutter::Shutter(int nr, const char *name) : 
   EVDBase(this),
   name_(g_Shutter), 
   shutterNumber_(nr+1),   
   initialized_(false), 
   //deviceNumber_(1),
   //moduleId_(17),
   close_(false),
   shut_(false),
   answerTimeoutMs_(1000),
   changedTime_ (0)
{

	InitializeDefaultErrorMessages();

	chx_.channel_=nr;

   CPropertyAction*	pAct = new CPropertyAction (this, &Shutter::OnChannel);
   CreateProperty(g_Channel, CDeviceUtils::ConvertToString(chx_.channel_+1), MM::Integer, false, pAct,true);
   if(inventoryDeviceAddresses_.empty()){
		//AddAllowedValue("Channel", "0");
		AddAllowedValue(g_Channel, "1");
		AddAllowedValue(g_Channel, "2");
		AddAllowedValue(g_Channel, "3");
		AddAllowedValue(g_Channel, "4");
		AddAllowedValue(g_Channel, "5");
		AddAllowedValue(g_Channel, "6");
   }else{
		for(std::vector<int>::iterator it=inventoryDeviceAddresses_.begin();it!=inventoryDeviceAddresses_.end();++it){
			AddAllowedValue(g_Channel, CDeviceUtils::ConvertToString(((int)*it)+1));
		}
   }
	name_=name;	
	pAct = new CPropertyAction (this, &Shutter::OnVersion);
	CreateProperty(g_Version, g_Version1, MM::String, false, pAct,true);
	AddAllowedValue(g_Version, g_Version1);
	AddAllowedValue(g_Version, g_Version2);

}
Shutter::~Shutter()
{
   //shuttersUsed[deviceNumber_ - 1][shutterNumber_ - 1] = false;
   Shutdown();
}

void Shutter::GetName(char* Name) const
{
	std::ostringstream name;
   name<<g_Shutter<<(shutterNumber_);
   //name<<name_;
   CDeviceUtils::CopyLimitedString(Name, name.str().c_str());
   //CDeviceUtils::CopyLimitedString(name, name_.c_str());
}

int Shutter::Initialize()
{
    core_ = GetCoreCallback();

	GetLimitsValues(&chx_);   
   CreateProperty(g_Limit_V_Min, CDeviceUtils::ConvertToString(chx_.min_V_), MM::Float, true);		
   CreateProperty(g_Limit_V_Max , CDeviceUtils::ConvertToString(chx_.max_V_), MM::Float, true);			
   CreateProperty(g_Limit_Um_Min,CDeviceUtils::ConvertToString(chx_.min_um_), MM::Float, true);		
   CreateProperty(g_Limit_Um_Max, CDeviceUtils::ConvertToString(chx_.max_um_), MM::Float, true);   

	CPropertyAction*  pAct = new CPropertyAction (this, &Shutter::OnState);
	int ret = CreateProperty(MM::g_Keyword_State, "0", MM::Integer, false, pAct); 
	AddAllowedValue(MM::g_Keyword_State, "0");
	AddAllowedValue(MM::g_Keyword_State, "1");
	if (ret != DEVICE_OK)
      return ret;

	pAct = new CPropertyAction (this, &Shutter::OnShutterState);
	CreateProperty(g_ShutterState, g_Open, MM::String, false, pAct);
	AddAllowedValue(g_ShutterState, g_Open);
	AddAllowedValue(g_ShutterState, g_Close);

	//LogMessage ("Property Status",true);
   pAct = new CPropertyAction (this, &Shutter::OnStat);
   CreateProperty(g_StatusX, "0", MM::Integer, true, pAct);   
   
   char s[20];
   //GetRgver(rgver_);
	ret = GetCommandValue("rgver",chx_.channel_,chx_.rgver_);
	if (ret != DEVICE_OK)	  
      return ret;	
	sprintf(s,"%i",chx_.rgver_);	
	CreateProperty(g_Rgver, s, MM::Integer, true);	
	pAct = new CPropertyAction (this, &Shutter::OnTime);
	CreateProperty(g_Rohm, "0", MM::Integer, true, pAct);
	pAct = new CPropertyAction (this, &Shutter::OnTemp);
    CreateProperty(g_Ktemp, "0", MM::Float, true, pAct);

   pAct = new CPropertyAction (this, &Shutter::OnLoop);
   CreateProperty(g_Loop, g_Loop_open, MM::String, false, pAct,false);
   AddAllowedValue(g_Loop, g_Loop_open);
   AddAllowedValue(g_Loop, g_Loop_close);

   pAct = new CPropertyAction (this, &Shutter::OnModulInput);
    CreateProperty(g_Modon, g_Modon_Off, MM::String, false, pAct);
    AddAllowedValue(g_Modon, g_Modon_Off);
    AddAllowedValue(g_Modon, g_Modon_On);

	pAct = new CPropertyAction (this, &Shutter::OnMonitor);
    CreateProperty(g_Monsrc, g_Monsrc_0, MM::String, false, pAct);
    AddAllowedValue(g_Monsrc, g_Monsrc_0);
    AddAllowedValue(g_Monsrc, g_Monsrc_1);
	AddAllowedValue(g_Monsrc, g_Monsrc_2);
	AddAllowedValue(g_Monsrc, g_Monsrc_3);
	AddAllowedValue(g_Monsrc, g_Monsrc_4);
	AddAllowedValue(g_Monsrc, g_Monsrc_5);
	AddAllowedValue(g_Monsrc, g_Monsrc_6);	

	pAct = new CPropertyAction (this, &Shutter::OnPidP);
	CreateProperty(g_PID_P, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_PID_P, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Shutter::OnPidI);
	CreateProperty(g_PID_I, "0.0", MM::Float, false, pAct);
   SetPropertyLimits(g_PID_I, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Shutter::OnPidD);
	CreateProperty(g_PID_D, "0.0", MM::Float, false, pAct);
   SetPropertyLimits(g_PID_D, 0.0, 999.0);

   //Notch Filter
   pAct = new CPropertyAction (this, &Shutter::OnNotch);
    CreateProperty(g_Notch, g_Notch_Off, MM::String, false, pAct);
    AddAllowedValue(g_Notch, g_Notch_Off);
    AddAllowedValue(g_Notch, g_Notch_On);
   pAct = new CPropertyAction (this, &Shutter::OnNotchFreq);
	CreateProperty(g_Notch_Freq, "0", MM::Integer, false, pAct);
   SetPropertyLimits(g_Notch_Freq, 0, 20000);
      pAct = new CPropertyAction (this, &Shutter::OnNotchBand);
	CreateProperty(g_Notch_Band, "0", MM::Integer, false, pAct);
   SetPropertyLimits(g_Notch_Band, 0, 20000);
	//Low pass filter
    pAct = new CPropertyAction (this, &Shutter::OnLowpass);
   CreateProperty(g_Lowpass, g_Lowpass_Off, MM::String, false, pAct);
    AddAllowedValue(g_Lowpass, g_Lowpass_Off);
    AddAllowedValue(g_Lowpass, g_Lowpass_On);
	pAct = new CPropertyAction (this, &Shutter::OnLowpassFreq);
	CreateProperty(g_Lowpass_Freq, "0", MM::Integer, false, pAct);
   SetPropertyLimits(g_Lowpass_Freq, 0, 20000);

   //Internal function generator
    chx_.gfkt_=0;
	pAct = new CPropertyAction (this, &Shutter::OnGenerate);
	CreateProperty(g_Generator, g_Generator_Off, MM::String, false, pAct);
	AddAllowedValue(g_Generator, g_Generator_Off);
	AddAllowedValue(g_Generator, g_Generator_Sine);
	AddAllowedValue(g_Generator, g_Generator_Tri);
	AddAllowedValue(g_Generator, g_Generator_Rect);
	AddAllowedValue(g_Generator, g_Generator_Noise);
	AddAllowedValue(g_Generator, g_Generator_Sweep);
	
	//Sine
	pAct = new CPropertyAction (this, &Shutter::OnSinAmp);
	CreateProperty(g_Generator_Sine_Amp, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_Amp, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Shutter::OnSinOff);
	CreateProperty(g_Generator_Sine_Offset, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_Offset, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Shutter::OnSinFreq);
	CreateProperty(g_Generator_Sine_Freq, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_Freq, 0.00001, 9999.9);
	//triangle
	pAct = new CPropertyAction (this, &Shutter::OnTriAmp);
	CreateProperty(g_Generator_Tri_Amp, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_Amp, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Shutter::OnTriOff);
	CreateProperty(g_Generator_Tri_Offset, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Tri_Offset, 0.0, 100.0);
    pAct = new CPropertyAction (this, &Shutter::OnTriFreq);
	CreateProperty(g_Generator_Tri_Freq, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_Freq, 0.00001, 9999.9);
    pAct = new CPropertyAction (this, &Shutter::OnTriSym);
	CreateProperty(g_Generator_Tri_Sym, "50.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_Sym, 0.0, 100.0);
	//rectangle
   pAct = new CPropertyAction (this, &Shutter::OnRecAmp);
   CreateProperty(g_Generator_Rect_Amp, "0", MM::Float, false, pAct);
   SetPropertyLimits(g_Generator_Rect_Amp, 0.0, 100.0);
   pAct = new CPropertyAction (this, &Shutter::OnRecOff);
	CreateProperty(g_Generator_Rect_Offset, "0", MM::Float, false, pAct);
   SetPropertyLimits(g_Generator_Rect_Offset, 0.0, 100.0);
   pAct = new CPropertyAction (this, &Shutter::OnRecFreq);
	CreateProperty(g_Generator_Rect_Freq, "0.1", MM::Float, false, pAct);
   SetPropertyLimits(g_Generator_Rect_Freq, 0.00001, 9999.9);
   pAct = new CPropertyAction (this, &Shutter::OnRecSym);
	CreateProperty(g_Generator_Rect_Sym, "0.1", MM::Float, false, pAct);
   SetPropertyLimits(g_Generator_Rect_Sym, 0.0, 100.0);
	//Noise
	pAct = new CPropertyAction (this, &Shutter::OnNoiAmp);
	CreateProperty(g_Generator_Noise_Amp, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Noise_Amp, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Shutter::OnNoiOff);
	CreateProperty(g_Generator_Noise_Offset, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Noise_Offset, 0.0, 100.0);
	//Sweep
    pAct = new CPropertyAction (this, &Shutter::OnSweAmp);
	CreateProperty(g_Generator_Sweep_Amp, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sweep_Amp, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Shutter::OnSweOff);
	CreateProperty(g_Generator_Sweep_Offset, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_Offset, 0.0, 100.0);
    pAct = new CPropertyAction (this, &Shutter::OnSweTime);
	CreateProperty(g_Generator_Sweep_Time, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_Time, 0.4, 800.0);
	
	//Scan
	pAct = new CPropertyAction (this, &Shutter::OnScanType);
	CreateProperty(g_Scan_Type, g_Scan_Type_Off, MM::String, false, pAct);
	AddAllowedValue(g_Scan_Type, g_Scan_Type_Off);
	AddAllowedValue(g_Scan_Type, g_Scan_Type_Sine);
	AddAllowedValue(g_Scan_Type, g_Scan_Type_Tri);
	 pAct = new CPropertyAction (this, &Shutter::OnScan);
	 CreateProperty(g_Scan_Start, g_Scan_Off, MM::String, false, pAct);
    AddAllowedValue(g_Scan_Start, g_Scan_Off);
    AddAllowedValue(g_Scan_Start, g_Scan_Starting);

	//trigger
    pAct = new CPropertyAction (this, &Shutter::OnTriggerStart);
	CreateProperty(g_Trigger_Start, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Trigger_Start, chx_.max_um_*0.002, chx_.max_um_*0.998);
	pAct = new CPropertyAction (this, &Shutter::OnTriggerEnd);
	CreateProperty(g_Trigger_End, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_End, chx_.max_um_*0.002, chx_.max_um_*0.998);
    pAct = new CPropertyAction (this, &Shutter::OnTriggerInterval);
	CreateProperty(g_Trigger_Interval, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_Interval, chx_.min_um_, chx_.max_um_);
	pAct = new CPropertyAction (this, &Shutter::OnTriggerTime);
	CreateProperty(g_Trigger_Time, "1", MM::Integer, false, pAct);
   SetPropertyLimits(g_Trigger_Time, 1, 255);
   pAct = new CPropertyAction (this, &Shutter::OnTriggerType);
	CreateProperty(g_Trigger_Generator, g_Trigger_Off, MM::String, false, pAct);
	AddAllowedValue(g_Trigger_Generator, g_Trigger_Off);
	AddAllowedValue(g_Trigger_Generator, g_Trigger_Rising);
	AddAllowedValue(g_Trigger_Generator, g_Trigger_Falling);
	AddAllowedValue(g_Trigger_Generator, g_Trigger_Both);



	ret = UpdateStatus();
    if (ret != DEVICE_OK)
      return ret;
   
    initialized_ = true;

	return DEVICE_OK;
}

int Shutter::Shutdown()
{
   if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

bool Shutter::Busy()
{
	   return false;
}

int Shutter::SetOpen(bool open)
{
   changedTime_ = GetCurrentMMTime();
   long pos;
   if (open)
      pos = 1;
   else
      pos = 0;
   return SetProperty(MM::g_Keyword_State, CDeviceUtils::ConvertToString(pos));
}

int Shutter::GetOpen(bool& open)
{
   char buf[MM::MaxStrLength];
   int ret = GetProperty(MM::g_Keyword_State, buf);
   if (ret != DEVICE_OK)
      return ret;
   long pos = atol(buf);
   pos == 1 ? open = true : open = false;

   return DEVICE_OK;
}
int Shutter::Fire(double /*deltaT*/)
{
   return DEVICE_UNSUPPORTED_COMMAND;
}

int Shutter::OnChannel(MM::PropertyBase* pProp, MM::ActionType eAct){
	long c;
	if (eAct == MM::BeforeGet)
    {	
		char m[50];
		sprintf(m,"Set channel %d",chx_.channel_);
		LogMessage (m);
		c=chx_.channel_+1;		
		pProp->Set(c);		
	}
    else if (eAct == MM::AfterSet)
    {
		char m[50];
		sprintf(m,"Get channel%d",chx_.channel_);
		LogMessage (m);
		pProp->Get(c);		
		chx_.channel_=(int)c-1;
	}
    return DEVICE_OK;
}
int Shutter::OnState(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){		
		if(shut_){
			pProp->Set((long)0);
		}else{
			pProp->Set((long)1);
		}
	}
    else if (eAct == MM::AfterSet){
		long l;
		pProp->Get(l);
		shut_=(l==1)?true:false;
		if(shut_){
			SetProperty(g_ShutterState, g_Close);
		}else{
			SetProperty(g_ShutterState, g_Open);
		}
	}
    return DEVICE_OK;
}
int Shutter::OnShutterState(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnShutterState");	
	if (eAct == MM::BeforeGet){	
		bool open=true;		
		double pos=0;		
		int ret = GetPos(pos,&chx_);
		if (ret != DEVICE_OK)
			return ret;
		if(pos<(chx_.max_um_/2)){
			if(close_){
				open=false;
			}else{
				open=true;
			}
		}else{
			if(close_){
				open=true;
			}else{
				open=false;
			}
		}
		shut_=open;
		if (open){
			pProp->Set(g_Open);			
		}else{
			pProp->Set(g_Close);			
		}		
	}
    else if (eAct == MM::AfterSet){	  
		std::string state;
		long b=0;
		long l=0;
		pProp->Get(state);
		if (state == g_Open){
			shut_ = false;
			b=0;
		}else{
			shut_ = true;
			b=1;
		}
		
		if((shut_&close_)|((!shut_)&(!close_))){ //close in version2 or open in version1
			//zero Volt 			
			SetPos(0,&chx_);			
			GetProperty(MM::g_Keyword_State, l);
			if(l==b){
				LogMessage ("l==b");
				if(close_){
					SetProperty(MM::g_Keyword_State, "1");
				}else{
					SetProperty(MM::g_Keyword_State, "0");
				}
			}

		}else{			
			//high volt			
			SetPos(chx_.max_um_,&chx_);
			GetProperty(MM::g_Keyword_State, l);
			if(l==b){				
				if(close_){
					SetProperty(MM::g_Keyword_State, "0");
				}else{
					SetProperty(MM::g_Keyword_State, "1");
				}
			}		
		}
		CDeviceUtils::SleepMs(2000);	
	}
    return DEVICE_OK;
}
int Shutter::OnVersion(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnVersion",true);	
	if (eAct == MM::BeforeGet)
    {			
		if (close_){
			pProp->Set(g_Version2);			
		}else{
			pProp->Set(g_Version1);			
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string version;
      pProp->Get(version);
	  if (version == g_Version2){
         close_ = true;
	  }else{
         close_ = false;
	  }	  
	}
    return DEVICE_OK;
}
int Shutter::OnStat(MM::PropertyBase* pProp, MM::ActionType eAct){	
	int s;
	if (eAct == MM::BeforeGet)
    {	
		GetStatus(s,&chx_);	
		chx_.stat_=s;
		pProp->Set((long)s);		
	}   
    return DEVICE_OK;
}
int Shutter::OnTemp(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet){
		int ret = GetCommandValue("ktemp",chx_.channel_,chx_.ktemp_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set(chx_.ktemp_);
	}
	return DEVICE_OK;
}
int Shutter::OnTime(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("rohm",chx_.channel_,chx_.rohm_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set((long)chx_.rohm_);
	}
	return DEVICE_OK;
}
int Shutter::OnLoop(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
    {	
		int stat;
		int ret=GetStatus(stat,&chx_);
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.loop_){
			pProp->Set(g_Loop_close);			
		}else{
			pProp->Set(g_Loop_open);			
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string loop;
      pProp->Get(loop);
	  if (loop == g_Loop_close){
         chx_.loop_ = true;
	  }else{
         chx_.loop_ = false;
	  }
	  int i=(chx_.loop_)?1:0;
	  int ret = SetCommandValue("cl",chx_.channel_,i);
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnModulInput(MM::PropertyBase* pProp, MM::ActionType eAct){
	int l=0;
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("modon",chx_.channel_,l);
		chx_.modon_=(l==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.modon_){
			pProp->Set(g_Modon_On);			
		}
		else{
			pProp->Set(g_Modon_Off);			
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string modon;
      pProp->Get(modon);
	  if (modon == g_Modon_On){
         chx_.modon_ = true;
	  }else{
         chx_.modon_ = false;
	  }
	  l=(chx_.modon_)?1:0;
	  int ret = SetCommandValue("modon",chx_.channel_,l);	  
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnMonitor(MM::PropertyBase* pProp, MM::ActionType eAct){		
	if (eAct == MM::BeforeGet)
    {	
		int ret = GetCommandValue("monsrc",chx_.channel_,chx_.monsrc_);
		if (ret!=DEVICE_OK)
			return ret;				
		switch (chx_.monsrc_){
		case 0:
			pProp->Set(g_Monsrc_0);	break;
		case 1:
			pProp->Set(g_Monsrc_1);	break;
		case 2:
			pProp->Set(g_Monsrc_2);	break;
		case 3:
			pProp->Set(g_Monsrc_3);	break;
		case 4:
			pProp->Set(g_Monsrc_4);	break;
		case 5:
			pProp->Set(g_Monsrc_5);	break;
		case 6:
			pProp->Set(g_Monsrc_6);	break;
		default:
			pProp->Set(g_Monsrc_0);
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string mon;
		pProp->Get(mon);
		if (mon == g_Monsrc_0)
         chx_.monsrc_ = 0;
		else if (mon == g_Monsrc_1)
         chx_.monsrc_ = 1;
		else if (mon == g_Monsrc_2)
         chx_.monsrc_ = 2;
		else if (mon == g_Monsrc_3)
         chx_.monsrc_ = 3;
		else if (mon == g_Monsrc_4)
         chx_.monsrc_ = 4;
		else if (mon == g_Monsrc_5)
         chx_.monsrc_ = 5;	
		else if (mon == g_Monsrc_6)
         chx_.monsrc_ = 6;	
		int ret = SetCommandValue("monsrc",chx_.channel_,chx_.monsrc_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Shutter::OnPidP(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		ret = GetCommandValue("kp",chx_.channel_,chx_.kp_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)chx_.kp_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.kp_);		
		ret = SetCommandValue("kp",chx_.channel_,chx_.kp_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnPidI(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		ret = GetCommandValue("ki",chx_.channel_,chx_.ki_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)chx_.ki_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.ki_);		 
		ret = SetCommandValue("ki",chx_.channel_,chx_.ki_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnPidD(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{
		ret = GetCommandValue("kd",chx_.channel_,chx_.kd_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)chx_.kd_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.kd_);		
		ret = SetCommandValue("kd",chx_.channel_,chx_.kd_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnNotch(MM::PropertyBase* pProp, MM::ActionType eAct){
	int b=0;
	if (eAct == MM::BeforeGet){
		int ret = GetCommandValue("notchon",chx_.channel_,b);
		chx_.notchon_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.notchon_){
			pProp->Set(g_Notch_On);			
		}else{
			pProp->Set(g_Notch_Off);			
		}		
	}else if (eAct == MM::AfterSet){	  
	  std::string notch;
      pProp->Get(notch);
	  if (notch == g_Notch_On){
         chx_.notchon_ = true;
	  }else{
         chx_.notchon_ = false;
	  }
	   b=(chx_.kd_)?1:0;
	  int ret = SetCommandValue("notchon",chx_.channel_,b);
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnNotchFreq(MM::PropertyBase* pProp, MM::ActionType eAct){
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("notchf",chx_.channel_,chx_.notchf_);
		if (ret != DEVICE_OK)
			return ret;
		SetPropertyLimits(g_Notch_Band, 0, ((2*chx_.notchf_)<=20000)?(2*chx_.notchf_):20000);
		l=chx_.notchf_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chx_.notchf_=(int)l;
		int ret = SetCommandValue("notchf",chx_.channel_,chx_.notchf_);
		if (ret!=DEVICE_OK)
			return ret;
		//set limit bandwidth to max 2*notch_frequency
		SetPropertyLimits(g_Notch_Band, 0, ((2*chx_.notchf_)<=20000)?(2*chx_.notchf_):20000);
	}
    return DEVICE_OK;
}
int Shutter::OnNotchBand(MM::PropertyBase* pProp, MM::ActionType eAct){
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("notchb",chx_.channel_,chx_.notchb_);
		if (ret != DEVICE_OK)
			return ret;
		l=chx_.notchb_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chx_.notchb_=(int)l;
		int ret = SetCommandValue("notchb",chx_.channel_,chx_.notchb_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnLowpass(MM::PropertyBase* pProp, MM::ActionType eAct){
	int l=0;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpon",chx_.channel_,l);
		chx_.lpon_=(l==1)?true:false;		
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.lpon_){
			pProp->Set(g_Lowpass_On);			
		}else{
			pProp->Set(g_Lowpass_Off);			
		}		
	}else if (eAct == MM::AfterSet){
	  std::string lpon;
      pProp->Get(lpon);
	  if (lpon == g_Lowpass_On){
         chx_.lpon_ = true;
	  }else{
         chx_.lpon_ = false;
	  }
	  l=(chx_.lpon_)?1:0;
	  int ret = SetCommandValue("lpon",chx_.channel_,l);	
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnLowpassFreq(MM::PropertyBase* pProp, MM::ActionType eAct){
	long c;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpf",chx_.channel_,chx_.lpf_);
		if (ret != DEVICE_OK)
			return ret;		
		c=chx_.lpf_;
		pProp->Set(c);
	}else if (eAct == MM::AfterSet){
		pProp->Get(c);
		chx_.lpf_=(int)c;
		int ret = SetCommandValue("lpf",chx_.channel_,chx_.lpf_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Shutter::OnGenerate(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){			
		int ret = GetCommandValue("gfkt",chx_.channel_,chx_.gfkt_);
		if (ret!=DEVICE_OK)
			return ret;			
		switch (chx_.gfkt_){
		case 0:
			pProp->Set(g_Generator_Off);		break;
		case 1:
			pProp->Set(g_Generator_Sine);		break;
		case 2:
			pProp->Set(g_Generator_Tri);		break;
		case 3:
			pProp->Set(g_Generator_Rect);	break;
		case 4:
			pProp->Set(g_Generator_Noise);	break;
		case 5:
			pProp->Set(g_Generator_Sweep);	break;
		default:
			pProp->Set(g_Generator_Off);
		}
	}else if (eAct == MM::AfterSet){		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Generator_Off)
         chx_.gfkt_ = 0;
		else if (gen == g_Generator_Sine)
         chx_.gfkt_ = 1;
		else if (gen == g_Generator_Tri)
         chx_.gfkt_ = 2;
		else if (gen == g_Generator_Rect)
         chx_.gfkt_ = 3;
		else if (gen == g_Generator_Noise)
         chx_.gfkt_ = 4;
		else if (gen == g_Generator_Sweep)
         chx_.gfkt_ = 5;		
		int ret = SetCommandValue("gfkt",chx_.channel_,chx_.gfkt_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Shutter::OnSinAmp(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gasin",chx_.channel_,chx_.gasin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gasin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gasin_);
		int ret = SetCommandValue("gasin",chx_.channel_,chx_.gasin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnSinOff(MM::PropertyBase* pProp, MM::ActionType eAct){
	int ret =0;
	if (eAct == MM::BeforeGet)
	{		
        ret = GetCommandValue("gosin",chx_.channel_,chx_.gosin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gosin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gosin_);
		ret = SetCommandValue("gosin",chx_.channel_,chx_.gosin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnSinFreq(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gfsin",chx_.channel_,chx_.gfsin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gfsin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gfsin_);
		int ret = SetCommandValue("gfsin",chx_.channel_,chx_.gfsin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnTriAmp(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gatri",chx_.channel_,chx_.gatri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gatri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gatri_);
		int ret = SetCommandValue("gatri",chx_.channel_,chx_.gatri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnTriOff(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gotri",chx_.channel_,chx_.gotri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gotri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gotri_);
		int ret = SetCommandValue("gotri",chx_.channel_,chx_.gotri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnTriFreq(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gftri",chx_.channel_,chx_.gftri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gftri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gftri_);
		int ret = SetCommandValue("gftri",chx_.channel_,chx_.gftri_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnTriSym(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gstri",chx_.channel_,chx_.gstri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gstri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gstri_);
		int ret = SetCommandValue("gstri",chx_.channel_,chx_.gstri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnRecAmp(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("garec",chx_.channel_,chx_.garec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.garec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.garec_);
		int ret = SetCommandValue("garec",chx_.channel_,chx_.garec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnRecOff(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gorec",chx_.channel_,chx_.gorec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gorec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gorec_);
		int ret = SetCommandValue("gorec",chx_.channel_,chx_.gorec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnRecFreq(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gfrec",chx_.channel_,chx_.gfrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gfrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gfrec_);
		int ret = SetCommandValue("gfrec",chx_.channel_,chx_.gfrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnRecSym(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gsrec",chx_.channel_,chx_.gsrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gsrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gsrec_);
		int ret = SetCommandValue("gsrec",chx_.channel_,chx_.gsrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnNoiAmp(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("ganoi",chx_.channel_,chx_.ganoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.ganoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.ganoi_);
		int ret = SetCommandValue("ganoi",chx_.channel_,chx_.ganoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnNoiOff(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gonoi",chx_.channel_,chx_.gonoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gonoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gonoi_);
		int ret = SetCommandValue("gonoi",chx_.channel_,chx_.gonoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnSweAmp(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gaswe",chx_.channel_,chx_.gaswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gaswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gaswe_);
		int ret = SetCommandValue("gaswe",chx_.channel_,chx_.gaswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnSweOff(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("goswe",chx_.channel_,chx_.goswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.goswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.goswe_);
		int ret = SetCommandValue("goswe",chx_.channel_,chx_.goswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnSweTime(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
	{
		int ret = GetCommandValue("gtswe",chx_.channel_,chx_.gtswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gtswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gtswe_);
		int ret = SetCommandValue("gtswe",chx_.channel_,chx_.gtswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Shutter::OnScanType(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {			
		int ret = GetCommandValue("sct",chx_.channel_,chx_.sct_);	
		if (ret!=DEVICE_OK)
			return ret;			
		switch (chx_.sct_){
		case 0:
			pProp->Set(g_Scan_Type_Off);break;
		case 1:
			pProp->Set(g_Scan_Type_Sine);		break;
		case 2:
			pProp->Set(g_Scan_Type_Tri);	break;		
		default:
			pProp->Set(g_Scan_Type_Off);
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Scan_Type_Off)
         chx_.sct_ = 0;
		else if (gen == g_Scan_Type_Sine)
         chx_.sct_ = 1;
		else if (gen == g_Scan_Type_Tri)
         chx_.sct_ = 2;		
		int ret = SetCommandValue("sct",chx_.channel_,chx_.sct_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Shutter::OnScan(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {			
		int b=0;		
		int ret = GetCommandValue("ss",chx_.channel_,b);
		chx_.ss_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;			
		if(chx_.ss_){
			pProp->Set(g_Scan_Starting);
		}else{
			pProp->Set(g_Scan_Off);	
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string s;
		int b=0;
		pProp->Get(s);
		if (s == g_Scan_Off){
			chx_.ss_ = false;
			b=0;
		}else if (s == g_Scan_Starting){
			chx_.ss_ = true;
			b=1;
		}
		if(chx_.ss_){
			int ret = SetCommandValue("ss",chx_.channel_,b);
			if (ret!=DEVICE_OK)
				return ret;		
		}		
	}
    return DEVICE_OK;
}
int Shutter::OnTriggerStart(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet){			
		int ret = GetCommandValue("trgss",chx_.channel_,chx_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chx_.trgss_);		
	}else if (eAct == MM::AfterSet){		
		pProp->Get(chx_.trgss_);					
		int ret = SetCommandValue("trgss",chx_.channel_,chx_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Shutter::OnTriggerEnd(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {			
		int ret = GetCommandValue("trgse",chx_.channel_,chx_.trgse_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chx_.trgse_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chx_.trgse_);					
		int ret = SetCommandValue("trgse",chx_.channel_,chx_.trgse_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
	return DEVICE_OK;
}
int Shutter::OnTriggerInterval(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {			
		int ret = GetCommandValue("trgsi",chx_.channel_,chx_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chx_.trgsi_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chx_.trgsi_);					
		int ret = SetCommandValue("trgsi",chx_.channel_,chx_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Shutter::OnTriggerTime(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {			
		int ret = GetCommandValue("trglen",chx_.channel_,chx_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set((long)chx_.trglen_);		
	}
    else if (eAct == MM::AfterSet)
    {	
		long l;
		pProp->Get(l);
		chx_.trglen_=(int)l;
		int ret = SetCommandValue("trglen",chx_.channel_,chx_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Shutter::OnTriggerType(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {			
		int ret = GetCommandValue("trgedge",chx_.channel_,chx_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;			
		switch (chx_.trgedge_){
		case 0:
			pProp->Set(g_Trigger_Off);		break;
		case 1:
			pProp->Set(g_Trigger_Rising);	break;
		case 2:
			pProp->Set(g_Trigger_Falling);	break;	
		case 3:
			pProp->Set(g_Trigger_Both);		break;
		default:
			pProp->Set(g_Trigger_Off);
		}	
	}
    else if (eAct == MM::AfterSet)
    {			
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Trigger_Off)
         chx_.trgedge_ = 0;
		else if (gen == g_Trigger_Rising)
         chx_.trgedge_ = 1;
		else if (gen == g_Trigger_Falling)
         chx_.trgedge_ = 2;	
		else if (gen == g_Trigger_Both)
         chx_.trgedge_ = 3;	
		int ret = SetCommandValue("trgedge",chx_.channel_,chx_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}


Mirror::Mirror():
EVDBase(this), 
nr_(1),
name_(g_Mirror),
xChannel_(0), //(xaxis),
yChannel_(1), //(yaxis), 
zChannel_(2), //(zaxis),
initialized_(false)
{
	 // Name, read-only (RO)
   CreateProperty(MM::g_Keyword_Name, g_Mirror, MM::String, true);
}

Mirror::Mirror(int nr, int count, const char* name):
EVDBase(this),
nr_(nr),
channelcount_(count),
name_(name),
xChannel_(0), //(xaxis),
yChannel_(1), //(yaxis), 
zChannel_(2), //(zaxis),
initialized_(false)
{
	 // Name, read-only (RO)
   CreateProperty(MM::g_Keyword_Name, name_.c_str(), MM::String, true);

   // Description, RO
   CreateProperty(MM::g_Keyword_Description, "Piezosystem Jena Mirror tilting system", MM::String, true);  


   CPropertyAction*  pAct = new CPropertyAction (this, &Mirror::OnChannelX);
   CreateProperty(g_ChannelX, "1", MM::Integer, false, pAct,true);
   if(inventoryDeviceAddresses_.empty()){		
		AddAllowedValue(g_ChannelX, "1");
		AddAllowedValue(g_ChannelX, "2");
		AddAllowedValue(g_ChannelX, "3");
		AddAllowedValue(g_ChannelX, "4");
		AddAllowedValue(g_ChannelX, "5");
		AddAllowedValue(g_ChannelX, "6");
   }else{
		for(std::vector<int>::iterator it=inventoryDeviceAddresses_.begin();it!=inventoryDeviceAddresses_.end();++it){
			AddAllowedValue(g_ChannelX, CDeviceUtils::ConvertToString(((int)*it)+1));
		}
   }   
   chx_.channel_=xChannel_;

   if(channelcount_>1){
		pAct = new CPropertyAction (this, &Mirror::OnChannelY);
		CreateProperty(g_ChannelY, "2", MM::Integer, false, pAct,true);
		if(inventoryDeviceAddresses_.empty()){		
			AddAllowedValue(g_ChannelY, "1");
			AddAllowedValue(g_ChannelY, "2");
			AddAllowedValue(g_ChannelY, "3");
			AddAllowedValue(g_ChannelY, "4");
			AddAllowedValue(g_ChannelY, "5");
			AddAllowedValue(g_ChannelY, "6");
		}else{
			for(std::vector<int>::iterator it=inventoryDeviceAddresses_.begin();it!=inventoryDeviceAddresses_.end();++it){
				AddAllowedValue("Channel Y", CDeviceUtils::ConvertToString(((int)*it)+1));
			}
		}
		chy_.channel_=yChannel_;
   }
   if(channelcount_>2){
		pAct = new CPropertyAction (this, &Mirror::OnChannelZ);
		CreateProperty(g_ChannelZ, "3", MM::Integer, false, pAct,true);
		if(inventoryDeviceAddresses_.empty()){		
			AddAllowedValue(g_ChannelZ, "1");
			AddAllowedValue(g_ChannelZ, "2");
			AddAllowedValue(g_ChannelZ, "3");
			AddAllowedValue(g_ChannelZ, "4");
			AddAllowedValue(g_ChannelZ, "5");
			AddAllowedValue(g_ChannelZ, "6");
		}else{
			for(std::vector<int>::iterator it=inventoryDeviceAddresses_.begin();it!=inventoryDeviceAddresses_.end();++it){
				AddAllowedValue(g_ChannelZ, CDeviceUtils::ConvertToString(((int)*it)+1));
			}
		}
		chz_.channel_=zChannel_;
   }	
}
Mirror::~Mirror(){
	Shutdown();
}

int Mirror::Shutdown()
{
	if (initialized_)
   {
      initialized_ = false;
   }
   return DEVICE_OK;
}

void Mirror::GetName(char* Name) const
{
	std::ostringstream name;
   name<<g_Mirror<<(nr_);   
   CDeviceUtils::CopyLimitedString(Name, name.str().c_str());
}
int Mirror::Initialize()
{
    core_ = GetCoreCallback();

	//X-Achse
	CreateProperty(g_ChannelX_, CDeviceUtils::ConvertToString(xChannel_+1), MM::Integer, true);	//read-only 
	
	CPropertyAction* pAct = new CPropertyAction (this, &Mirror::OnStatX);
    CreateProperty(g_StatusX, "0", MM::Integer, true, pAct);
	GetLimitsValues(&chx_);
	// limit x
   CreateProperty("Limit Voltage min x", CDeviceUtils::ConvertToString(chx_.min_V_), MM::Float, true);		
   CreateProperty("Limit Voltage max x" , CDeviceUtils::ConvertToString(chx_.max_V_), MM::Float, true);
   CreateProperty("Limit um min x",CDeviceUtils::ConvertToString(chx_.min_um_), MM::Float, true);		
   CreateProperty("Limit um max x", CDeviceUtils::ConvertToString(chx_.max_um_), MM::Float, true);

	char s[20];	
	int ret = GetCommandValue("rgver",xChannel_,chx_.rgver_);
	if (ret != DEVICE_OK)	  
      return ret;	
	sprintf(s,"%i",chx_.rgver_);	
	CreateProperty(g_RgverX, s, MM::Integer, true);
	pAct = new CPropertyAction (this, &Mirror::OnTimeX);
	CreateProperty(g_RohmX, "0", MM::Integer, true, pAct);
	pAct = new CPropertyAction (this, &Mirror::OnTempX);
    CreateProperty(g_KtempX, "0", MM::Float, true, pAct);
	pAct = new CPropertyAction (this, &Mirror::OnPositionX);
    CreateProperty(g_PositionX, "0.001", MM::Float, false, pAct);
	pAct = new CPropertyAction (this, &Mirror::OnLoopX);
	CreateProperty(g_LoopX, g_Loop_open, MM::String, false, pAct,false);
	AddAllowedValue(g_LoopX, g_Loop_open);
	AddAllowedValue(g_LoopX, g_Loop_close);
	pAct = new CPropertyAction (this, &Mirror::OnSoftstartX);
    CreateProperty(g_FenableX, g_Fenable_Off, MM::String, false, pAct);
    AddAllowedValue(g_FenableX , g_Fenable_Off);
    AddAllowedValue(g_FenableX, g_Fenable_On);
	pAct = new CPropertyAction (this, &Mirror::OnSlewRateX);
	CreateProperty(g_SrX, "10.0", MM::Float, false, pAct);
	SetPropertyLimits(g_SrX, 0.0000002, 500.0);
	
	pAct = new CPropertyAction (this, &Mirror::OnPositionX);
    CreateProperty(g_PositionX, "0.001", MM::Float, false, pAct);
	pAct = new CPropertyAction (this, &Mirror::OnModulInputX);
    CreateProperty(g_ModonX, g_Modon_Off, MM::String, false, pAct);
    AddAllowedValue(g_ModonX, g_Modon_Off);
    AddAllowedValue(g_ModonX, g_Modon_On);
	pAct = new CPropertyAction (this, &Mirror::OnMonitorX);
    CreateProperty(g_MonsrcX, g_Monsrc_0, MM::String, false, pAct);
    AddAllowedValue(g_MonsrcX, g_Monsrc_0);
    AddAllowedValue(g_MonsrcX, g_Monsrc_1);
	AddAllowedValue(g_MonsrcX, g_Monsrc_2);
	AddAllowedValue(g_MonsrcX, g_Monsrc_3);
	AddAllowedValue(g_MonsrcX, g_Monsrc_4);
	AddAllowedValue(g_MonsrcX, g_Monsrc_5);
	AddAllowedValue(g_MonsrcX, g_Monsrc_6);

	pAct = new CPropertyAction (this, &Mirror::OnPidPX);
	CreateProperty(g_PID_PX, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_PID_PX, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Mirror::OnPidIX);
	CreateProperty(g_PID_IX, "0.0", MM::Float, false, pAct);
    SetPropertyLimits(g_PID_IX, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Mirror::OnPidDX);
	CreateProperty(g_PID_DX, "0.0", MM::Float, false, pAct);
    SetPropertyLimits(g_PID_DX, 0.0, 999.0);
	//Notch Filter X
    pAct = new CPropertyAction (this, &Mirror::OnNotchX);
    CreateProperty(g_NotchX, g_Notch_Off, MM::String, false, pAct);
    AddAllowedValue(g_NotchX, g_Notch_Off);
    AddAllowedValue(g_NotchX, g_Notch_On);
	pAct = new CPropertyAction (this, &Mirror::OnNotchFreqX);
	CreateProperty(g_Notch_FreqX, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Notch_FreqX, 0, 20000);
    pAct = new CPropertyAction (this, &Mirror::OnNotchBandX);
	CreateProperty(g_Notch_BandX, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Notch_BandX, 0, 20000);
	//Low pass filter X
    pAct = new CPropertyAction (this, &Mirror::OnLowpassX);
    CreateProperty(g_LowpassX, g_Lowpass_Off, MM::String, false, pAct);
    AddAllowedValue(g_LowpassX, g_Lowpass_Off);
    AddAllowedValue(g_LowpassX, g_Lowpass_On);
	pAct = new CPropertyAction (this, &Mirror::OnLowpassFreqX);
	CreateProperty(g_Lowpass_FreqX, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Lowpass_FreqX, 0, 20000);
	//Internal function generator X
	chx_.gfkt_=0;
	pAct = new CPropertyAction (this, &Mirror::OnGenerateX);
	CreateProperty(g_GeneratorX, g_Generator_Off, MM::String, false, pAct);
	AddAllowedValue(g_GeneratorX, g_Generator_Off);
	AddAllowedValue(g_GeneratorX, g_Generator_Sine);
	AddAllowedValue(g_GeneratorX, g_Generator_Tri);
	AddAllowedValue(g_GeneratorX, g_Generator_Rect);
	AddAllowedValue(g_GeneratorX, g_Generator_Noise);
	AddAllowedValue(g_GeneratorX, g_Generator_Sweep);
	//Sine x
	pAct = new CPropertyAction (this, &Mirror::OnSinAmpX);
	CreateProperty(g_Generator_Sine_AmpX, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_AmpX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnSinOffX);
	CreateProperty(g_Generator_Sine_OffsetX, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_OffsetX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnSinFreqX);
	CreateProperty(g_Generator_Sine_FreqX, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_FreqX, 0.00001, 9999.9);
	//triangle x
	pAct = new CPropertyAction (this, &Mirror::OnTriAmpX);
	CreateProperty(g_Generator_Tri_AmpX, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_AmpX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnTriOffX);
	CreateProperty(g_Generator_Tri_OffsetX, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Tri_OffsetX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnTriFreqX);
	CreateProperty(g_Generator_Tri_FreqX, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_FreqX, 0.00001, 9999.9);
	pAct = new CPropertyAction (this, &Mirror::OnTriSymX);
	CreateProperty(g_Generator_Tri_SymX, "50.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_SymX, 0.0, 100.0);
	//rectangle x
	pAct = new CPropertyAction (this, &Mirror::OnRecAmpX);
	CreateProperty(g_Generator_Rect_AmpX, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_AmpX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnRecOffX);
	CreateProperty(g_Generator_Rect_OffsetX, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_OffsetX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnRecFreqX);
	CreateProperty(g_Generator_Rect_FreqX, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_FreqX, 0.00001, 9999.9);
	pAct = new CPropertyAction (this, &Mirror::OnRecSymX);
	CreateProperty(g_Generator_Rect_SymX, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_SymX, 0.0, 100.0);
	//Noise x
	pAct = new CPropertyAction (this, &Mirror::OnNoiAmpX);
	CreateProperty(g_Generator_Noise_AmpX, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Noise_AmpX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnNoiOffX);
	CreateProperty(g_Generator_Noise_OffsetX, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Noise_OffsetX, 0.0, 100.0);
	//Sweep x
    pAct = new CPropertyAction (this, &Mirror::OnSweAmpX);
	CreateProperty(g_Generator_Sweep_AmpX, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sweep_AmpX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnSweOffX);
	CreateProperty(g_Generator_Sweep_OffsetX, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_OffsetX, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnSweTimeX);
	CreateProperty(g_Generator_Sweep_TimeX, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_TimeX, 0.4, 800.0);
	//Scan x
	pAct = new CPropertyAction (this, &Mirror::OnScanTypeX);
	CreateProperty(g_Scan_TypeX, g_Scan_Type_Off, MM::String, false, pAct);
	AddAllowedValue(g_Scan_TypeX, g_Scan_Type_Off);
	AddAllowedValue(g_Scan_TypeX, g_Scan_Type_Sine);
	AddAllowedValue(g_Scan_TypeX, g_Scan_Type_Tri);
	pAct = new CPropertyAction (this, &Mirror::OnScanX);
	CreateProperty(g_Scan_StartX, g_Scan_Off, MM::String, false, pAct);
    AddAllowedValue(g_Scan_StartX, g_Scan_Off);
    AddAllowedValue(g_Scan_StartX, g_Scan_Starting);
	//trigger x
    pAct = new CPropertyAction (this, &Mirror::OnTriggerStartX);
	CreateProperty(g_Trigger_StartX, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Trigger_StartX, chx_.max_um_*0.002, chx_.max_um_*0.998);
	pAct = new CPropertyAction (this, &Mirror::OnTriggerEndX);
	CreateProperty(g_Trigger_EndX, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_EndX, chx_.max_um_*0.002, chx_.max_um_*0.998);
	pAct = new CPropertyAction (this, &Mirror::OnTriggerIntervalX);
	CreateProperty(g_Trigger_IntervalX, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_IntervalX, chx_.min_um_, chx_.max_um_);
	pAct = new CPropertyAction (this, &Mirror::OnTriggerTimeX);
	CreateProperty(g_Trigger_TimeX, "1", MM::Integer, false, pAct);
    SetPropertyLimits(g_Trigger_TimeX, 1, 255);
	pAct = new CPropertyAction (this, &Mirror::OnTriggerTypeX);
	CreateProperty(g_Trigger_GeneratorX, g_Trigger_Off, MM::String, false, pAct);
	AddAllowedValue(g_Trigger_GeneratorX, g_Trigger_Off);
	AddAllowedValue(g_Trigger_GeneratorX, g_Trigger_Rising);
	AddAllowedValue(g_Trigger_GeneratorX, g_Trigger_Falling);
	AddAllowedValue(g_Trigger_GeneratorX, g_Trigger_Both);



  if(channelcount_>1){
	//Y-Achse
	CreateProperty(g_ChannelY_, CDeviceUtils::ConvertToString(yChannel_+1), MM::Integer, true);	//read-only 
	
	pAct = new CPropertyAction (this, &Mirror::OnStatY);
    CreateProperty(g_StatusY, "0", MM::Integer, true, pAct);
	GetLimitsValues(&chy_);
	// limit y
   CreateProperty("Limit Voltage min y", CDeviceUtils::ConvertToString(chy_.min_V_), MM::Float, true);		
   CreateProperty("Limit Voltage max y" , CDeviceUtils::ConvertToString(chy_.max_V_), MM::Float, true);
   CreateProperty("Limit um min y",CDeviceUtils::ConvertToString(chy_.min_um_), MM::Float, true);		
   CreateProperty("Limit um max y", CDeviceUtils::ConvertToString(chy_.max_um_), MM::Float, true);

	ret = GetCommandValue("rgver",yChannel_,chy_.rgver_);
	if (ret != DEVICE_OK)	  
      return ret;	
	sprintf(s,"%i",chy_.rgver_);
	CreateProperty(g_RgverY, s, MM::Integer, true);
	pAct = new CPropertyAction (this, &Mirror::OnTimeY);
	CreateProperty(g_RohmY, "0", MM::Integer, true, pAct);
	pAct = new CPropertyAction (this, &Mirror::OnTempY);
    CreateProperty(g_KtempY, "0", MM::Float, true, pAct);
	pAct = new CPropertyAction (this, &Mirror::OnPositionY);
    CreateProperty(g_PositionY, "0.001", MM::Float, false, pAct);
	pAct = new CPropertyAction (this, &Mirror::OnLoopY);
	CreateProperty(g_LoopY, g_Loop_open, MM::String, false, pAct,false);
	AddAllowedValue(g_LoopY, g_Loop_open);
	AddAllowedValue(g_LoopY, g_Loop_close);
	pAct = new CPropertyAction (this, &Mirror::OnSoftstartY);
    CreateProperty(g_FenableY, g_Fenable_Off, MM::String, false, pAct);
    AddAllowedValue(g_FenableY , g_Fenable_Off);
    AddAllowedValue(g_FenableY , g_Fenable_On);
	pAct = new CPropertyAction (this, &Mirror::OnSlewRateY);
	CreateProperty(g_SrY, "10.0", MM::Float, false, pAct);
	SetPropertyLimits(g_SrY, 0.0000002, 500.0);
	
	pAct = new CPropertyAction (this, &Mirror::OnPositionY);
    CreateProperty(g_PositionY, "0.001", MM::Float, false, pAct);
	pAct = new CPropertyAction (this, &Mirror::OnModulInputY);
    CreateProperty(g_ModonY, g_Modon_Off, MM::String, false, pAct);
    AddAllowedValue(g_ModonY, g_Modon_Off);
    AddAllowedValue(g_ModonY, g_Modon_On);
	pAct = new CPropertyAction (this, &Mirror::OnMonitorY);
    CreateProperty(g_MonsrcY, g_Monsrc_0, MM::String, false, pAct);
    AddAllowedValue(g_MonsrcY, g_Monsrc_0);
    AddAllowedValue(g_MonsrcY, g_Monsrc_1);
	AddAllowedValue(g_MonsrcY, g_Monsrc_2);
	AddAllowedValue(g_MonsrcY, g_Monsrc_3);
	AddAllowedValue(g_MonsrcY, g_Monsrc_4);
	AddAllowedValue(g_MonsrcY, g_Monsrc_5);
	AddAllowedValue(g_MonsrcY, g_Monsrc_6);

	pAct = new CPropertyAction (this, &Mirror::OnPidPY);
	CreateProperty(g_PID_PY, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_PID_PY, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Mirror::OnPidIY);
	CreateProperty(g_PID_IY, "0.0", MM::Float, false, pAct);
    SetPropertyLimits(g_PID_IY, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Mirror::OnPidDY);
	CreateProperty(g_PID_DY, "0.0", MM::Float, false, pAct);
    SetPropertyLimits(g_PID_DY, 0.0, 999.0);
	//Notch Filter Y
    pAct = new CPropertyAction (this, &Mirror::OnNotchY);
    CreateProperty(g_NotchY, g_Notch_Off, MM::String, false, pAct);
    AddAllowedValue(g_NotchY, g_Notch_Off);
    AddAllowedValue(g_NotchY, g_Notch_On);
	pAct = new CPropertyAction (this, &Mirror::OnNotchFreqY);
	CreateProperty(g_Notch_FreqY, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Notch_FreqY, 0, 20000);
    pAct = new CPropertyAction (this, &Mirror::OnNotchBandY);
	CreateProperty(g_Notch_BandY, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Notch_BandY, 0, 20000);
	//Low pass filter Y
    pAct = new CPropertyAction (this, &Mirror::OnLowpassY);
    CreateProperty(g_LowpassY, g_Lowpass_Off, MM::String, false, pAct);
    AddAllowedValue(g_LowpassY, g_Lowpass_Off);
    AddAllowedValue(g_LowpassY, g_Lowpass_On);
    pAct = new CPropertyAction (this, &Mirror::OnLowpassFreqY);
	CreateProperty(g_Lowpass_FreqY, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Lowpass_FreqY, 0, 20000);
	//Internal function generator Y
	chy_.gfkt_=0;
	pAct = new CPropertyAction (this, &Mirror::OnGenerateY);
	CreateProperty(g_GeneratorY, g_Generator_Off, MM::String, false, pAct);
	AddAllowedValue(g_GeneratorY, g_Generator_Off);
	AddAllowedValue(g_GeneratorY, g_Generator_Sine);
	AddAllowedValue(g_GeneratorY, g_Generator_Tri);
	AddAllowedValue(g_GeneratorY, g_Generator_Rect);
	AddAllowedValue(g_GeneratorY, g_Generator_Noise);
	AddAllowedValue(g_GeneratorY, g_Generator_Sweep);
	//Sine y
	pAct = new CPropertyAction (this, &Mirror::OnSinAmpY);
	CreateProperty(g_Generator_Sine_AmpY, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_AmpY, 0.0, 100.0);
    pAct = new CPropertyAction (this, &Mirror::OnSinOffY);
	CreateProperty(g_Generator_Sine_OffsetY, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_OffsetY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnSinFreqY);
	CreateProperty(g_Generator_Sine_FreqY, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_FreqY, 0.00001, 9999.9);
    //triangle y
	pAct = new CPropertyAction (this, &Mirror::OnTriAmpY);
	CreateProperty(g_Generator_Tri_AmpY, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_AmpY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnTriOffY);
	CreateProperty(g_Generator_Tri_OffsetY, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Tri_OffsetY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnTriFreqY);
	CreateProperty(g_Generator_Tri_FreqY, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_FreqY, 0.00001, 9999.9);
	pAct = new CPropertyAction (this, &Mirror::OnTriSymY);
	CreateProperty(g_Generator_Tri_SymY, "50.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_SymY, 0.0, 100.0);
	//rectangle y
	pAct = new CPropertyAction (this, &Mirror::OnRecAmpY);
	CreateProperty(g_Generator_Rect_AmpY, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_AmpY, 0.0, 100.0);
    pAct = new CPropertyAction (this, &Mirror::OnRecOffY);
	CreateProperty(g_Generator_Rect_OffsetY, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_OffsetY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnRecFreqY);
	CreateProperty(g_Generator_Rect_FreqY, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_FreqY, 0.00001, 9999.9);
	pAct = new CPropertyAction (this, &Mirror::OnRecSymY);
	CreateProperty(g_Generator_Rect_SymY, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_SymY, 0.0, 100.0);
	//Noise y
	pAct = new CPropertyAction (this, &Mirror::OnNoiAmpY);
	CreateProperty(g_Generator_Noise_AmpY, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Noise_AmpY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnNoiOffY);
	CreateProperty(g_Generator_Noise_OffsetY, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Noise_OffsetY, 0.0, 100.0);
	//Sweep y
	pAct = new CPropertyAction (this, &Mirror::OnSweAmpY);
	CreateProperty(g_Generator_Sweep_AmpY, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sweep_AmpY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnSweOffY);
	CreateProperty(g_Generator_Sweep_OffsetY, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_OffsetY, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnSweTimeY);
	CreateProperty(g_Generator_Sweep_TimeY, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_TimeY, 0.4, 800.0);
	//Scan y
	pAct = new CPropertyAction (this, &Mirror::OnScanTypeY);
	CreateProperty(g_Scan_TypeY, g_Scan_Type_Off, MM::String, false, pAct);
	AddAllowedValue(g_Scan_TypeY, g_Scan_Type_Off);
	AddAllowedValue(g_Scan_TypeY, g_Scan_Type_Sine);
	AddAllowedValue(g_Scan_TypeY, g_Scan_Type_Tri);
	pAct = new CPropertyAction (this, &Mirror::OnScanY);
	CreateProperty(g_Scan_StartY, g_Scan_Off, MM::String, false, pAct);
    AddAllowedValue(g_Scan_StartY, g_Scan_Off);
    AddAllowedValue(g_Scan_StartY, g_Scan_Starting);
	//trigger y
	pAct = new CPropertyAction (this, &Mirror::OnTriggerStartY);
	CreateProperty(g_Trigger_StartY, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Trigger_StartY, chy_.max_um_*0.002, chy_.max_um_*0.998);
	pAct = new CPropertyAction (this, &Mirror::OnTriggerEndY);
	CreateProperty(g_Trigger_EndY, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_EndY, chy_.max_um_*0.002, chy_.max_um_*0.998);
	pAct = new CPropertyAction (this, &Mirror::OnTriggerIntervalY);
	CreateProperty(g_Trigger_IntervalY, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_IntervalY, chy_.min_um_, chy_.max_um_);
    pAct = new CPropertyAction (this, &Mirror::OnTriggerTimeY);
	CreateProperty(g_Trigger_TimeY, "1", MM::Integer, false, pAct);
    SetPropertyLimits(g_Trigger_TimeY, 1, 255);
	pAct = new CPropertyAction (this, &Mirror::OnTriggerTypeY);
	CreateProperty(g_Trigger_GeneratorY, g_Trigger_Off, MM::String, false, pAct);
	AddAllowedValue(g_Trigger_GeneratorY, g_Trigger_Off);
	AddAllowedValue(g_Trigger_GeneratorY, g_Trigger_Rising);
	AddAllowedValue(g_Trigger_GeneratorY, g_Trigger_Falling);
	AddAllowedValue(g_Trigger_GeneratorY, g_Trigger_Both);
  }

  if(channelcount_>2){
	//Z-Achse
	CreateProperty(g_ChannelZ_, CDeviceUtils::ConvertToString(zChannel_+1), MM::Integer, true);	//read-only 

    pAct = new CPropertyAction (this, &Mirror::OnStatZ);
    CreateProperty(g_StatusZ, "0", MM::Integer, true, pAct);
	GetLimitsValues(&chz_);   
	// limit z
	CreateProperty("Limit Voltage min z", CDeviceUtils::ConvertToString(chz_.min_V_), MM::Float, true);		
	CreateProperty("Limit Voltage max z" , CDeviceUtils::ConvertToString(chz_.max_V_), MM::Float, true);
	CreateProperty("Limit um min z",CDeviceUtils::ConvertToString(chz_.min_um_), MM::Float, true);		
	CreateProperty("Limit um max z", CDeviceUtils::ConvertToString(chz_.max_um_), MM::Float, true);
	
	ret = GetCommandValue("rgver",zChannel_,chz_.rgver_);
	if (ret != DEVICE_OK)	  
      return ret;	
	sprintf(s,"%i",chz_.rgver_);
	CreateProperty(g_RgverZ, s, MM::Integer, true);
	pAct = new CPropertyAction (this, &Mirror::OnTimeZ);
	CreateProperty(g_RohmZ, "0", MM::Integer, true, pAct);
	pAct = new CPropertyAction (this, &Mirror::OnTempZ);
    CreateProperty(g_KtempZ, "0", MM::Float, true, pAct);
	pAct = new CPropertyAction (this, &Mirror::OnPositionZ);
    CreateProperty(g_PositionZ, "0.001", MM::Float, false, pAct);
	pAct = new CPropertyAction (this, &Mirror::OnLoopZ);
	CreateProperty(g_LoopZ, g_Loop_open, MM::String, false, pAct,false);
	AddAllowedValue(g_LoopZ, g_Loop_open);
	AddAllowedValue(g_LoopZ, g_Loop_close);
	pAct = new CPropertyAction (this, &Mirror::OnSoftstartZ);
    CreateProperty(g_FenableZ, g_Fenable_Off, MM::String, false, pAct);
    AddAllowedValue(g_FenableZ , g_Fenable_Off);
    AddAllowedValue(g_FenableZ , g_Fenable_On);
	pAct = new CPropertyAction (this, &Mirror::OnSlewRateZ);
	CreateProperty(g_SrZ, "10.0", MM::Float, false, pAct);
	SetPropertyLimits(g_SrZ, 0.0000002, 500.0);
	
	pAct = new CPropertyAction (this, &Mirror::OnPositionZ);
    CreateProperty(g_PositionZ, "0.001", MM::Float, false, pAct);	
	pAct = new CPropertyAction (this, &Mirror::OnModulInputZ);
    CreateProperty(g_ModonZ, g_Modon_Off, MM::String, false, pAct);
    AddAllowedValue(g_ModonZ, g_Modon_Off);
    AddAllowedValue(g_ModonZ, g_Modon_On);
	pAct = new CPropertyAction (this, &Mirror::OnMonitorZ);
    CreateProperty(g_MonsrcZ, g_Monsrc_0, MM::String, false, pAct);
    AddAllowedValue(g_MonsrcZ, g_Monsrc_0);
    AddAllowedValue(g_MonsrcZ, g_Monsrc_1);
	AddAllowedValue(g_MonsrcZ, g_Monsrc_2);
	AddAllowedValue(g_MonsrcZ, g_Monsrc_3);
	AddAllowedValue(g_MonsrcZ, g_Monsrc_4);
	AddAllowedValue(g_MonsrcZ, g_Monsrc_5);
	AddAllowedValue(g_MonsrcZ, g_Monsrc_6);

	pAct = new CPropertyAction (this, &Mirror::OnPidPZ);
	CreateProperty(g_PID_PZ, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_PID_PZ, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Mirror::OnPidIZ);
	CreateProperty(g_PID_IZ, "0.0", MM::Float, false, pAct);
    SetPropertyLimits(g_PID_IZ, 0.0, 999.0);
	pAct = new CPropertyAction (this, &Mirror::OnPidDZ);
	CreateProperty(g_PID_DZ, "0.0", MM::Float, false, pAct);
    SetPropertyLimits(g_PID_DZ, 0.0, 999.0);
    
	//Notch Filter Z
	pAct = new CPropertyAction (this, &Mirror::OnNotchZ);
    CreateProperty(g_NotchZ, g_Notch_Off, MM::String, false, pAct);
    AddAllowedValue(g_NotchZ, g_Notch_Off);
    AddAllowedValue(g_NotchZ, g_Notch_On);
	pAct = new CPropertyAction (this, &Mirror::OnNotchFreqZ);
	CreateProperty(g_Notch_FreqZ, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Notch_FreqZ, 0, 20000);
	pAct = new CPropertyAction (this, &Mirror::OnNotchBandZ);
	CreateProperty(g_Notch_BandZ, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Notch_BandZ, 0, 20000);
	//Low pass filter Z
	pAct = new CPropertyAction (this, &Mirror::OnLowpassZ);
    CreateProperty(g_LowpassZ, g_Lowpass_Off, MM::String, false, pAct);
    AddAllowedValue(g_LowpassZ, g_Lowpass_Off);
    AddAllowedValue(g_LowpassZ, g_Lowpass_On);
	pAct = new CPropertyAction (this, &Mirror::OnLowpassFreqZ);
	CreateProperty(g_Lowpass_FreqZ, "0", MM::Integer, false, pAct);
    SetPropertyLimits(g_Lowpass_FreqZ, 0, 20000);	
	//Internal function generator Z
	chz_.gfkt_=0;
	pAct = new CPropertyAction (this, &Mirror::OnGenerateZ);
	CreateProperty(g_GeneratorZ, g_Generator_Off, MM::String, false, pAct);
	AddAllowedValue(g_GeneratorZ, g_Generator_Off);
	AddAllowedValue(g_GeneratorZ, g_Generator_Sine);
	AddAllowedValue(g_GeneratorZ, g_Generator_Tri);
	AddAllowedValue(g_GeneratorZ, g_Generator_Rect);
	AddAllowedValue(g_GeneratorZ, g_Generator_Noise);
	AddAllowedValue(g_GeneratorZ, g_Generator_Sweep);
	//Sine z
	pAct = new CPropertyAction (this, &Mirror::OnSinAmpZ);
	CreateProperty(g_Generator_Sine_AmpZ, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_AmpZ, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnSinOffZ);
	CreateProperty(g_Generator_Sine_OffsetZ, "0.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_OffsetZ, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnSinFreqZ);
	CreateProperty(g_Generator_Sine_FreqZ, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sine_FreqZ, 0.00001, 9999.9);
	//triangle z
	pAct = new CPropertyAction (this, &Mirror::OnTriAmpZ);
	CreateProperty(g_Generator_Tri_AmpZ, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_AmpZ, 0.0, 100.0);
    pAct = new CPropertyAction (this, &Mirror::OnTriOffZ);
	CreateProperty(g_Generator_Tri_OffsetZ, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Tri_OffsetZ, 0.0, 100.0);
    pAct = new CPropertyAction (this, &Mirror::OnTriFreqZ);
	CreateProperty(g_Generator_Tri_FreqZ, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_FreqZ, 0.00001, 9999.9);
	pAct = new CPropertyAction (this, &Mirror::OnTriSymZ);
	CreateProperty(g_Generator_Tri_SymZ, "50.0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Tri_SymZ, 0.0, 100.0);
	//rectangle z
	pAct = new CPropertyAction (this, &Mirror::OnRecAmpZ);
	CreateProperty(g_Generator_Rect_AmpZ, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_AmpZ, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnRecOffZ);
	CreateProperty(g_Generator_Rect_OffsetZ, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_OffsetZ, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnRecFreqZ);
	CreateProperty(g_Generator_Rect_FreqZ, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_FreqZ, 0.00001, 9999.9);
	pAct = new CPropertyAction (this, &Mirror::OnRecSymZ);
	CreateProperty(g_Generator_Rect_SymZ, "0.1", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Rect_SymZ, 0.0, 100.0);
	//Noise z
	pAct = new CPropertyAction (this, &Mirror::OnNoiAmpZ);
	CreateProperty(g_Generator_Noise_AmpZ, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Noise_AmpZ, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnNoiOffZ);
	CreateProperty(g_Generator_Noise_OffsetZ, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Noise_OffsetZ, 0.0, 100.0);
	//Sweep z
	pAct = new CPropertyAction (this, &Mirror::OnSweAmpZ);
	CreateProperty(g_Generator_Sweep_AmpZ, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Generator_Sweep_AmpZ, 0.0, 100.0);
    pAct = new CPropertyAction (this, &Mirror::OnSweOffZ);
	CreateProperty(g_Generator_Sweep_OffsetZ, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_OffsetZ, 0.0, 100.0);
	pAct = new CPropertyAction (this, &Mirror::OnSweTimeZ);
	CreateProperty(g_Generator_Sweep_TimeZ, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Generator_Sweep_TimeZ, 0.4, 800.0);
	//Scan z
	pAct = new CPropertyAction (this, &Mirror::OnScanTypeZ);
	CreateProperty(g_Scan_TypeZ, g_Scan_Type_Off, MM::String, false, pAct);
	AddAllowedValue(g_Scan_TypeZ, g_Scan_Type_Off);
	AddAllowedValue(g_Scan_TypeZ, g_Scan_Type_Sine);
	AddAllowedValue(g_Scan_TypeZ, g_Scan_Type_Tri);
	pAct = new CPropertyAction (this, &Mirror::OnScanZ);
	CreateProperty(g_Scan_StartZ, g_Scan_Off, MM::String, false, pAct);
    AddAllowedValue(g_Scan_StartZ, g_Scan_Off);
    AddAllowedValue(g_Scan_StartZ, g_Scan_Starting);	
	//trigger z
	pAct = new CPropertyAction (this, &Mirror::OnTriggerStartZ);
	CreateProperty(g_Trigger_StartZ, "0", MM::Float, false, pAct);
	SetPropertyLimits(g_Trigger_StartZ, chy_.max_um_*0.002, chy_.max_um_*0.998);
    pAct = new CPropertyAction (this, &Mirror::OnTriggerEndZ);
	CreateProperty(g_Trigger_EndZ, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_EndZ, chy_.max_um_*0.002, chy_.max_um_*0.998);
	pAct = new CPropertyAction (this, &Mirror::OnTriggerIntervalZ);
	CreateProperty(g_Trigger_IntervalZ, "0", MM::Float, false, pAct);
    SetPropertyLimits(g_Trigger_IntervalZ, chy_.min_um_, chy_.max_um_);
    pAct = new CPropertyAction (this, &Mirror::OnTriggerTimeZ);
	CreateProperty(g_Trigger_TimeZ, "1", MM::Integer, false, pAct);
    SetPropertyLimits(g_Trigger_TimeZ, 1, 255);
	pAct = new CPropertyAction (this, &Mirror::OnTriggerTypeZ);
	CreateProperty(g_Trigger_GeneratorZ, g_Trigger_Off, MM::String, false, pAct);
	AddAllowedValue(g_Trigger_GeneratorZ, g_Trigger_Off);
	AddAllowedValue(g_Trigger_GeneratorZ, g_Trigger_Rising);
	AddAllowedValue(g_Trigger_GeneratorZ, g_Trigger_Falling);
	AddAllowedValue(g_Trigger_GeneratorZ, g_Trigger_Both);
  }
/**/
	ret = UpdateStatus();
	if (ret != DEVICE_OK)
      return ret;

	initialized_ = true;
	return DEVICE_OK;
}
int Mirror::OnChannelX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnChannelX",true);
	long c;
	if (eAct == MM::BeforeGet)
    {	
		char m[50];
		sprintf(m,"Set channel %d",xChannel_+1);
		LogMessage (m,true);
		c=xChannel_+1;
		pProp->Set(c);		
	}
    else if (eAct == MM::AfterSet)
    {
		char m[50];
		sprintf(m,"Get channel%d",xChannel_+1);
		LogMessage (m,true);
		pProp->Get(c);
		xChannel_=(int)c-1;
		chx_.channel_=xChannel_;
	}
    return DEVICE_OK;
}
int Mirror::OnChannelY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnChannelY",true);
	double c;
	if (eAct == MM::BeforeGet)
    {	
		char m[50];
		sprintf(m,"Set channel %d",yChannel_+1);
		LogMessage (m,true);
		c=yChannel_+1;
		pProp->Set(c);		
	}
    else if (eAct == MM::AfterSet)
    {
		char m[50];
		sprintf(m,"Get channel%d",yChannel_+1);
		LogMessage (m,true);
		pProp->Get(c);
		yChannel_=(int)c-1;
		chy_.channel_=yChannel_;
	}
    return DEVICE_OK;
}
int Mirror::OnChannelZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnChannelZ",true);
	double c;
	if (eAct == MM::BeforeGet)
    {	
		char m[50];
		sprintf(m,"Set channel %d",zChannel_+1);
		LogMessage (m,true);
		c=zChannel_+1;
		pProp->Set(c);		
	}
    else if (eAct == MM::AfterSet)
    {
		char m[50];
		sprintf(m,"Get channel%d",zChannel_+1);
		LogMessage (m,true);
		pProp->Get(c);
		zChannel_=(int)c-1;
		chz_.channel_=zChannel_;
	}
    return DEVICE_OK;
}
int Mirror::OnStatX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnStatX",true);
	int s;
	if (eAct == MM::BeforeGet)
    {	
		GetStatus(s,&chx_);	
		chx_.stat_=s;
		pProp->Set((long)s);		
	}   
    return DEVICE_OK;
}
int Mirror::OnStatY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnStatY",true);
	int s;
	if (eAct == MM::BeforeGet)
    {	
		GetStatus(s,&chy_);
		chy_.stat_=s;
		pProp->Set((long)s);		
	}   
    return DEVICE_OK;
}
int Mirror::OnStatZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnStatZ",true);
	int s;
	if (eAct == MM::BeforeGet)
    {	
		GetStatus(s,&chz_);
		chz_.stat_=s;
		pProp->Set((long)s);		
	}   
    return DEVICE_OK;
}
int Mirror::OnTempX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTempX");
	if (eAct == MM::BeforeGet){
		//int ret = GetKtemp(ktemp_);	
		int ret = GetCommandValue("ktemp",xChannel_,chx_.ktemp_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set(chx_.ktemp_);
	}
	return DEVICE_OK;
}
int Mirror::OnTempY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTempY");
	if (eAct == MM::BeforeGet){
		//int ret = GetKtemp(ktemp_);	
		int ret = GetCommandValue("ktemp",yChannel_,chy_.ktemp_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set(chy_.ktemp_);
	}
	return DEVICE_OK;
}
int Mirror::OnTempZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTempY");
	if (eAct == MM::BeforeGet){
		//int ret = GetKtemp(ktemp_);	
		int ret = GetCommandValue("ktemp",zChannel_,chz_.ktemp_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set(chz_.ktemp_);
	}
	return DEVICE_OK;
}
int Mirror::OnTimeX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTimeX");
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetRohm(rohm_);
		int ret = GetCommandValue("rohm",xChannel_,chx_.rohm_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set((long)chx_.rohm_);
	}
	return DEVICE_OK;
}
int Mirror::OnTimeY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTimeY");
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetRohm(rohm_);
		int ret = GetCommandValue("rohm",yChannel_,chy_.rohm_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set((long)chy_.rohm_);
	}
	return DEVICE_OK;
}
int Mirror::OnTimeZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTimeZ");
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetRohm(rohm_);
		int ret = GetCommandValue("rohm",zChannel_,chz_.rohm_);
		if (ret!=DEVICE_OK)
			return ret;
		pProp->Set((long)chz_.rohm_);
	}
	return DEVICE_OK;
}
int Mirror::OnLoopX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnLoopX",true);	
	if (eAct == MM::BeforeGet)
    {	
		int stat;
		int ret=GetStatus(stat,&chx_);
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.loop_){
			pProp->Set(g_Loop_close);			
		}else{
			pProp->Set(g_Loop_open);			
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string loop;
      pProp->Get(loop);
	  if (loop == g_Loop_close){
         chx_.loop_ = true;
	  }else{
         chx_.loop_ = false;
	  }
	  //int ret = SetLoop(chx_.loop_,xChannel_);
	  int i=(chx_.loop_)?1:0;
	  int ret = SetCommandValue("cl",chx_.channel_,i);
	  if (ret!=DEVICE_OK)
			return ret;
	  //CDeviceUtils::SleepMs(200);
	}
    return DEVICE_OK;
}
int Mirror::OnLoopY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnLoopY",true);
	int l=0;
	if (eAct == MM::BeforeGet)
    {		
		int stat;
		int ret=GetStatus(stat,&chy_);
		if (ret!=DEVICE_OK)
			return ret;
		if (chy_.loop_){
			pProp->Set(g_Loop_close);
			l=1;
		}else{
			pProp->Set(g_Loop_open);
			l=0;
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string loop;
      pProp->Get(loop);
	  if (loop == g_Loop_close){
         chy_.loop_ = true;
	  }else{
         chy_.loop_ = false;
	  }
	  //int ret = SetLoop(chy_.loop_,yChannel_);
	   int i=(chy_.loop_)?1:0;
	  int ret = SetCommandValue("cl",chy_.channel_,i);
	  if (ret!=DEVICE_OK)
			return ret;
	  //CDeviceUtils::SleepMs(200);	
	}
    return DEVICE_OK;
}
int Mirror::OnLoopZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnLoopY",true);
	int l=0;
	if (eAct == MM::BeforeGet)
    {		
		int stat;
		int ret=GetStatus(stat,&chz_);
		if (ret!=DEVICE_OK)
			return ret;
		if (chz_.loop_){
			pProp->Set(g_Loop_close);
			l=1;
		}else{
			pProp->Set(g_Loop_open);
			l=0;
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string loop;
      pProp->Get(loop);
	  if (loop == g_Loop_close){
         chz_.loop_ = true;
	  }else{
         chz_.loop_ = false;
	  }
	  //int ret = SetLoop(chy_.loop_,yChannel_);
	   int i=(chz_.loop_)?1:0;
	  int ret = SetCommandValue("cl",chz_.channel_,i);
	  if (ret!=DEVICE_OK)
			return ret;
	  //CDeviceUtils::SleepMs(200);	
	}
    return DEVICE_OK;
}
int Mirror::OnPositionX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnPositionX",true);	
	if (eAct == MM::BeforeGet){	
		int ret = GetPos(chx_.pos_,&chx_);
		if (ret!=DEVICE_OK)
			return ret;		
		pProp->Set(chx_.pos_);
	}
    else if (eAct == MM::AfterSet){
		int ret=0;
		pProp->Get(chx_.pos_);		
		ret = SetPos(chx_.pos_,&chx_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnPositionY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnPositionY",true);	
	if (eAct == MM::BeforeGet){	
		int ret = GetPos(chy_.pos_,&chy_);
		if (ret!=DEVICE_OK)
			return ret;		
		pProp->Set(chy_.pos_);
	}
    else if (eAct == MM::AfterSet){
		int ret=0;
		pProp->Get(chy_.pos_);		
		ret = SetPos(chy_.pos_,&chy_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnPositionZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnPositionY",true);	
	if (eAct == MM::BeforeGet){	
		int ret = GetPos(chz_.pos_,&chz_);
		if (ret!=DEVICE_OK)
			return ret;		
		pProp->Set(chz_.pos_);
	}
    else if (eAct == MM::AfterSet){
		int ret=0;
		pProp->Get(chz_.pos_);		
		ret = SetPos(chz_.pos_,&chz_);		
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSoftstartX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSoftstartX");	
	if (eAct == MM::BeforeGet){	
		int b=0;
		//int ret=GetFenable(fenable_);
		int ret = GetCommandValue("fenable",chx_.channel_,b);
		chx_.fenable_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.fenable_){
			pProp->Set(g_Fenable_On);			
		}
		else{
			pProp->Set(g_Fenable_Off);			
		}		
	}
    else if (eAct == MM::AfterSet){	  
		std::string softstart;
		int b=0;
		pProp->Get(softstart);
		if (softstart == g_Fenable_On){
			chx_.fenable_ = true;
			b=1;
		}else{
			chx_.fenable_ = false;
			b=0;
		}
		//int ret = SetFenable(fenable_);
		int ret = SetCommandValue("fenable",chx_.channel_,b);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSoftstartY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSoftstartY");	
	if (eAct == MM::BeforeGet){	
		int b=0;
		//int ret=GetFenable(fenable_);
		int ret = GetCommandValue("fenable",chy_.channel_,b);
		chy_.fenable_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chy_.fenable_){
			pProp->Set(g_Fenable_On);			
		}
		else{
			pProp->Set(g_Fenable_Off);			
		}		
	}
    else if (eAct == MM::AfterSet){	  
		std::string softstart;
		int b=0;
		pProp->Get(softstart);
		if (softstart == g_Fenable_On){
			chy_.fenable_ = true;
			b=1;
		}else{
			chy_.fenable_ = false;
			b=0;
		}
		//int ret = SetFenable(fenable_);
		int ret = SetCommandValue("fenable",chy_.channel_,b);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSoftstartZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSoftstartZ");	
	if (eAct == MM::BeforeGet){	
		int b=0;
		//int ret=GetFenable(fenable_);
		int ret = GetCommandValue("fenable",chz_.channel_,b);
		chz_.fenable_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chz_.fenable_){
			pProp->Set(g_Fenable_On);			
		}
		else{
			pProp->Set(g_Fenable_Off);			
		}		
	}
    else if (eAct == MM::AfterSet){	  
		std::string softstart;
		int b=0;
		pProp->Get(softstart);
		if (softstart == g_Fenable_On){
			chz_.fenable_ = true;
			b=1;
		}else{
			chz_.fenable_ = false;
			b=0;
		}
		//int ret = SetFenable(fenable_);
		int ret = SetCommandValue("fenable",chz_.channel_,b);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSlewRateX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSlewRateX",true);		
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		//ret = GetSr(sr_);
		ret = GetCommandValue("sr",chx_.channel_,chx_.sr_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)chx_.sr_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.sr_);		
		int ret = SetCommandValue("sr",chx_.channel_,chx_.sr_);
		//ret = SetSr(sr_);
		if (ret != DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnSlewRateY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSlewRateY",true);		
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		//ret = GetSr(sr_);
		ret = GetCommandValue("sr",chy_.channel_,chy_.sr_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)chy_.sr_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.sr_);		
		ret = SetCommandValue("sr",chy_.channel_,chy_.sr_);
		//ret = SetSr(sr_);
		if (ret != DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnSlewRateZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSlewRateZ",true);		
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		//ret = GetSr(sr_);
		ret = GetCommandValue("sr",chz_.channel_,chz_.sr_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set((double)chz_.sr_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.sr_);		
		ret = SetCommandValue("sr",chz_.channel_,chz_.sr_);
		//ret = SetSr(sr_);
		if (ret != DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnModulInputX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnModulInputX",true);
	int l=0;
	if (eAct == MM::BeforeGet)
    {		
		//int ret = GetModon(modon_);
		int ret = GetCommandValue("modon",chx_.channel_,l);
		chx_.modon_=(l==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.modon_){
			pProp->Set(g_Modon_On);			
		}
		else{
			pProp->Set(g_Modon_Off);			
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string modon;
      pProp->Get(modon);
	  if (modon == g_Modon_On){
         chx_.modon_ = true;
	  }else{
         chx_.modon_ = false;
	  }
	  //int ret = SetModon(modon_);
	  l=(chx_.modon_)?1:0;
	  int ret = SetCommandValue("modon",chx_.channel_,l);	  
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnModulInputY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnModulInputY",true);
	int l=0;
	if (eAct == MM::BeforeGet)
    {		
		//int ret = GetModon(modon_);
		int ret = GetCommandValue("modon",chy_.channel_,l);
		chy_.modon_=(l==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chy_.modon_){
			pProp->Set(g_Modon_On);			
		}else{
			pProp->Set(g_Modon_Off);			
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string modon;
      pProp->Get(modon);
	  if (modon == g_Modon_On){
         chy_.modon_ = true;
	  }else{
         chy_.modon_ = false;
	  }
	  //int ret = SetModon(modon_);
	  l=(chy_.modon_)?1:0;
	  int ret = SetCommandValue("modon",chy_.channel_,l);	  
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnModulInputZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnModulInputZ",true);
	int l=0;
	if (eAct == MM::BeforeGet)
    {		
		//int ret = GetModon(modon_);
		int ret = GetCommandValue("modon",chz_.channel_,l);
		chz_.modon_=(l==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chz_.modon_){
			pProp->Set(g_Modon_On);			
		}else{
			pProp->Set(g_Modon_Off);			
		}		
	}
    else if (eAct == MM::AfterSet)
    {	  
	  std::string modon;
      pProp->Get(modon);
	  if (modon == g_Modon_On){
         chz_.modon_ = true;
	  }else{
         chz_.modon_ = false;
	  }
	  //int ret = SetModon(modon_);
	  l=(chz_.modon_)?1:0;
	  int ret = SetCommandValue("modon",chz_.channel_,l);	  
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnMonitorX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnMonitorX",true);	
	if (eAct == MM::BeforeGet)
    {		
		//int ret = GetMonsrc(monsrc_);
		int ret = GetCommandValue("monsrc",chx_.channel_,chx_.monsrc_);
		if (ret!=DEVICE_OK)
			return ret;				
		switch (chx_.monsrc_){
		case 0:
			pProp->Set(g_Monsrc_0);	break;
		case 1:
			pProp->Set(g_Monsrc_1);	break;
		case 2:
			pProp->Set(g_Monsrc_2);	break;
		case 3:
			pProp->Set(g_Monsrc_3);	break;
		case 4:
			pProp->Set(g_Monsrc_4);	break;
		case 5:
			pProp->Set(g_Monsrc_5);	break;
		case 6:
			pProp->Set(g_Monsrc_6);	break;
		default:
			pProp->Set(g_Monsrc_0);
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string mon;
		pProp->Get(mon);
		if (mon == g_Monsrc_0)
         chx_.monsrc_ = 0;
		else if (mon == g_Monsrc_1)
         chx_.monsrc_ = 1;
		else if (mon == g_Monsrc_2)
         chx_.monsrc_ = 2;
		else if (mon == g_Monsrc_3)
         chx_.monsrc_ = 3;
		else if (mon == g_Monsrc_4)
         chx_.monsrc_ = 4;
		else if (mon == g_Monsrc_5)
         chx_.monsrc_ = 5;	
		else if (mon == g_Monsrc_6)
         chx_.monsrc_ = 6;	
		//int ret = SetMonsrc(monsrc_);
		int ret = SetCommandValue("monsrc",chx_.channel_,chx_.monsrc_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnMonitorY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnMonitorY",true);	
	if (eAct == MM::BeforeGet)
    {		
		//int ret = GetMonsrc(monsrc_);
		int ret = GetCommandValue("monsrc",chy_.channel_,chy_.monsrc_);
		if (ret!=DEVICE_OK)
			return ret;				
		switch (chy_.monsrc_){
		case 0:
			pProp->Set(g_Monsrc_0);	break;
		case 1:
			pProp->Set(g_Monsrc_1);	break;
		case 2:
			pProp->Set(g_Monsrc_2);	break;
		case 3:
			pProp->Set(g_Monsrc_3);	break;
		case 4:
			pProp->Set(g_Monsrc_4);	break;
		case 5:
			pProp->Set(g_Monsrc_5);	break;
		case 6:
			pProp->Set(g_Monsrc_6);	break;
		default:
			pProp->Set(g_Monsrc_0);
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string mon;
		pProp->Get(mon);
		if (mon == g_Monsrc_0)
         chy_.monsrc_ = 0;
		else if (mon == g_Monsrc_1)
         chy_.monsrc_ = 1;
		else if (mon == g_Monsrc_2)
         chy_.monsrc_ = 2;
		else if (mon == g_Monsrc_3)
         chy_.monsrc_ = 3;
		else if (mon == g_Monsrc_4)
         chy_.monsrc_ = 4;
		else if (mon == g_Monsrc_5)
         chy_.monsrc_ = 5;	
		else if (mon == g_Monsrc_6)
         chy_.monsrc_ = 6;	
		//int ret = SetMonsrc(monsrc_);
		int ret = SetCommandValue("monsrc",chy_.channel_,chy_.monsrc_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnMonitorZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnMonitorZ",true);	
	if (eAct == MM::BeforeGet)
    {		
		//int ret = GetMonsrc(monsrc_);
		int ret = GetCommandValue("monsrc",chz_.channel_,chz_.monsrc_);
		if (ret!=DEVICE_OK)
			return ret;				
		switch (chz_.monsrc_){
		case 0:
			pProp->Set(g_Monsrc_0);	break;
		case 1:
			pProp->Set(g_Monsrc_1);	break;
		case 2:
			pProp->Set(g_Monsrc_2);	break;
		case 3:
			pProp->Set(g_Monsrc_3);	break;
		case 4:
			pProp->Set(g_Monsrc_4);	break;
		case 5:
			pProp->Set(g_Monsrc_5);	break;
		case 6:
			pProp->Set(g_Monsrc_6);	break;
		default:
			pProp->Set(g_Monsrc_0);
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string mon;
		pProp->Get(mon);
		if (mon == g_Monsrc_0)
         chz_.monsrc_ = 0;
		else if (mon == g_Monsrc_1)
         chz_.monsrc_ = 1;
		else if (mon == g_Monsrc_2)
         chz_.monsrc_ = 2;
		else if (mon == g_Monsrc_3)
         chz_.monsrc_ = 3;
		else if (mon == g_Monsrc_4)
         chz_.monsrc_ = 4;
		else if (mon == g_Monsrc_5)
         chz_.monsrc_ = 5;	
		else if (mon == g_Monsrc_6)
         chz_.monsrc_ = 6;	
		//int ret = SetMonsrc(monsrc_);
		int ret = SetCommandValue("monsrc",chz_.channel_,chz_.monsrc_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnPidPX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnPidPX",true);	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		//ret = GetKp(kp_);
		ret = GetCommandValue("kp",chx_.channel_,chx_.kp_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.kp_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.kp_);		
		//ret = SetKp(kp_);
		ret = SetCommandValue("kp",chx_.channel_,chx_.kp_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnPidPY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnPidPY",true);	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		//ret = GetKp(kp_);
		ret = GetCommandValue("kp",chy_.channel_,chy_.kp_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.kp_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.kp_);		
		//ret = SetKp(kp_);
		ret = SetCommandValue("kp",chy_.channel_,chy_.kp_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnPidPZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnPidPZ",true);	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		//ret = GetKp(kp_);
		ret = GetCommandValue("kp",chz_.channel_,chz_.kp_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.kp_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.kp_);		
		//ret = SetKp(kp_);
		ret = SetCommandValue("kp",chz_.channel_,chz_.kp_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnPidIX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnPidIX",true);	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		//ret = GetKi(ki_);
		ret = GetCommandValue("ki",chx_.channel_,chx_.ki_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.ki_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.ki_);		
		//ret = SetKi(ki_);
		ret = SetCommandValue("ki",chx_.channel_,chx_.ki_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnPidIY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnPidIY",true);	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		//ret = GetKi(ki_);
		ret = GetCommandValue("ki",chy_.channel_,chy_.ki_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.ki_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.ki_);		
		//ret = SetKi(ki_);
		ret = SetCommandValue("ki",chy_.channel_,chy_.ki_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnPidIZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnPidIZ",true);	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		//ret = GetKi(ki_);
		ret = GetCommandValue("ki",chz_.channel_,chz_.ki_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.ki_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.ki_);		
		//ret = SetKi(ki_);
		ret = SetCommandValue("ki",chz_.channel_,chz_.ki_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnPidDX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnPidDX",true);	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		//ret = GetKd(kd_);
		ret = GetCommandValue("kd",chx_.channel_,chx_.kd_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.kd_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.kd_);		
		//ret = SetKd(kd_);
		ret = SetCommandValue("kd",chx_.channel_,chx_.kd_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnPidDY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnPidDY",true);	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		//ret = GetKd(kd_);
		ret = GetCommandValue("kd",chy_.channel_,chy_.kd_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.kd_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.kd_);		
		//ret = SetKd(kd_);
		ret = SetCommandValue("kd",chy_.channel_,chy_.kd_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnPidDZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnPidDZ",true);	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{			
		//ret = GetKd(kd_);
		ret = GetCommandValue("kd",chz_.channel_,chz_.kd_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.kd_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.kd_);		
		//ret = SetKd(kd_);
		ret = SetCommandValue("kd",chz_.channel_,chz_.kd_);
		if (ret != DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnNotchX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNotchX",true);
	int b=0;
	if (eAct == MM::BeforeGet){
		//int ret=GetNotchon(notchon_);
		int ret = GetCommandValue("notchon",chx_.channel_,b);
		chx_.notchon_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.notchon_){
			pProp->Set(g_Notch_On);			
		}else{
			pProp->Set(g_Notch_Off);			
		}		
	}else if (eAct == MM::AfterSet){	  
	  std::string notch;
      pProp->Get(notch);
	  if (notch == g_Notch_On){
         chx_.notchon_ = true;
	  }else{
         chx_.notchon_ = false;
	  }
	  //int ret = SetNotchon(notchon_);
	  b=(chx_.kd_)?1:0;
	  int ret = SetCommandValue("notchon",chx_.channel_,b);
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnNotchY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNotchY",true);
	int b=0;
	if (eAct == MM::BeforeGet){
		//int ret=GetNotchon(notchon_);
		int ret = GetCommandValue("notchon",chy_.channel_,b);
		chy_.notchon_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.notchon_){
			pProp->Set(g_Notch_On);			
		}else{
			pProp->Set(g_Notch_Off);			
		}		
	}else if (eAct == MM::AfterSet){	  
	  std::string notch;
      pProp->Get(notch);
	  if (notch == g_Notch_On){
         chy_.notchon_ = true;
	  }else{
         chy_.notchon_ = false;
	  }
	  //int ret = SetNotchon(notchon_);
	  b=(chy_.kd_)?1:0;
	  int ret = SetCommandValue("notchon",chy_.channel_,b);
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnNotchZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNotchZ",true);
	int b=0;
	if (eAct == MM::BeforeGet){
		//int ret=GetNotchon(notchon_);
		int ret = GetCommandValue("notchon",chz_.channel_,b);
		chz_.notchon_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;
		if (chz_.notchon_){
			pProp->Set(g_Notch_On);			
		}else{
			pProp->Set(g_Notch_Off);			
		}		
	}else if (eAct == MM::AfterSet){	  
	  std::string notch;
      pProp->Get(notch);
	  if (notch == g_Notch_On){
         chz_.notchon_ = true;
	  }else{
         chz_.notchon_ = false;
	  }
	  //int ret = SetNotchon(notchon_);
	  b=(chz_.kd_)?1:0;
	  int ret = SetCommandValue("notchon",chz_.channel_,b);
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnNotchFreqX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNotchFreqX",true);
	long l;
	if (eAct == MM::BeforeGet){		
		//int ret = GetNotchf(notchf_);	
		int ret = GetCommandValue("notchf",chx_.channel_,chx_.notchf_);
		if (ret != DEVICE_OK)
			return ret;
		SetPropertyLimits(g_Notch_BandX, 0, ((2*chx_.notchf_)<=20000)?(2*chx_.notchf_):20000);
		l=chx_.notchf_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chx_.notchf_=(int)l;
		//int ret = SetNotchf(notchf_);
		int ret = SetCommandValue("notchf",chx_.channel_,chx_.notchf_);
		if (ret!=DEVICE_OK)
			return ret;
		//set limit bandwidth to max 2*notch_frequency
		SetPropertyLimits(g_Notch_BandX, 0, ((2*chx_.notchf_)<=20000)?(2*chx_.notchf_):20000);		
		//CDeviceUtils::SleepMs(2000);
	}
    return DEVICE_OK;
}
int Mirror::OnNotchFreqY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNotchFreqY",true);
	long l;
	if (eAct == MM::BeforeGet){		
		//int ret = GetNotchf(notchf_);	
		int ret = GetCommandValue("notchf",chy_.channel_,chy_.notchf_);
		if (ret != DEVICE_OK)
			return ret;
		SetPropertyLimits(g_Notch_BandY, 0, ((2*chy_.notchf_)<=20000)?(2*chy_.notchf_):20000);
		l=chy_.notchf_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chy_.notchf_=(int)l;
		//int ret = SetNotchf(notchf_);
		int ret = SetCommandValue("notchf",chy_.channel_,chy_.notchf_);
		if (ret!=DEVICE_OK)
			return ret;
		//set limit bandwidth to max 2*notch_frequency
		SetPropertyLimits(g_Notch_BandY, 0, ((2*chy_.notchf_)<=20000)?(2*chy_.notchf_):20000);		
		//CDeviceUtils::SleepMs(2000);
	}
    return DEVICE_OK;
}
int Mirror::OnNotchFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNotchFreqZ",true);
	long l;
	if (eAct == MM::BeforeGet){		
		//int ret = GetNotchf(notchf_);	
		int ret = GetCommandValue("notchf",chz_.channel_,chz_.notchf_);
		if (ret != DEVICE_OK)
			return ret;
		SetPropertyLimits(g_Notch_BandZ, 0, ((2*chz_.notchf_)<=20000)?(2*chz_.notchf_):20000);
		l=chz_.notchf_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chz_.notchf_=(int)l;
		//int ret = SetNotchf(notchf_);
		int ret = SetCommandValue("notchf",chz_.channel_,chz_.notchf_);
		if (ret!=DEVICE_OK)
			return ret;
		//set limit bandwidth to max 2*notch_frequency
		SetPropertyLimits(g_Notch_BandZ, 0, ((2*chz_.notchf_)<=20000)?(2*chz_.notchf_):20000);		
		//CDeviceUtils::SleepMs(2000);
	}
    return DEVICE_OK;
}
int Mirror::OnNotchBandX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNotchBandX",true);
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("notchb",chx_.channel_,chx_.notchb_);
		//int ret = GetNotchb(notchb_);
		if (ret != DEVICE_OK)
			return ret;
		l=chx_.notchb_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chx_.notchb_=(int)l;
		int ret = SetCommandValue("notchb",chx_.channel_,chx_.notchb_);
		//int ret = SetNotchb(notchb_);
		if (ret!=DEVICE_OK)
			return ret;
		//CDeviceUtils::SleepMs(2000);
	}
    return DEVICE_OK;
}
int Mirror::OnNotchBandY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNotchBandY",true);
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("notchb",chy_.channel_,chy_.notchb_);
		//int ret = GetNotchb(notchb_);
		if (ret != DEVICE_OK)
			return ret;
		l=chy_.notchb_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chy_.notchb_=(int)l;
		int ret = SetCommandValue("notchb",chy_.channel_,chy_.notchb_);
		//int ret = SetNotchb(notchb_);
		if (ret!=DEVICE_OK)
			return ret;
		//CDeviceUtils::SleepMs(2000);
	}
    return DEVICE_OK;
}
int Mirror::OnNotchBandZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNotchBandZ",true);
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("notchb",chz_.channel_,chz_.notchb_);
		//int ret = GetNotchb(notchb_);
		if (ret != DEVICE_OK)
			return ret;
		l=chz_.notchb_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chz_.notchb_=(int)l;
		int ret = SetCommandValue("notchb",chz_.channel_,chz_.notchb_);
		//int ret = SetNotchb(notchb_);
		if (ret!=DEVICE_OK)
			return ret;
		//CDeviceUtils::SleepMs(2000);
	}
    return DEVICE_OK;
}
int Mirror::OnLowpassX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnLowpassX",true);
	int l=0;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpon",chx_.channel_,l);
		chx_.lpon_=(l==1)?true:false;
		//int ret = GetLpon(lpon_);		
		if (ret!=DEVICE_OK)
			return ret;
		if (chx_.lpon_){
			pProp->Set(g_Lowpass_On);			
		}else{
			pProp->Set(g_Lowpass_Off);			
		}		
	}else if (eAct == MM::AfterSet){
	  std::string lpon;
      pProp->Get(lpon);
	  if (lpon == g_Lowpass_On){
		chx_.lpon_ = true;
	  }else{
         chx_.lpon_ = false;
	  }
	  //int ret = SetLpon(lpon_);
	  	l=(chx_.lpon_)?1:0;
	  int ret = SetCommandValue("lpon",chx_.channel_,l);	
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnLowpassY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnLowpassY",true);
	int l=0;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpon",chy_.channel_,l);
		chy_.lpon_=(l==1)?true:false;
		//int ret = GetLpon(lpon_);		
		if (ret!=DEVICE_OK)
			return ret;
		if (chy_.lpon_){
			pProp->Set(g_Lowpass_On);			
		}else{
			pProp->Set(g_Lowpass_Off);			
		}		
	}else if (eAct == MM::AfterSet){
	  std::string lpon;
      pProp->Get(lpon);
	  if (lpon == g_Lowpass_On){
		chy_.lpon_ = true;
	  }else{
         chy_.lpon_ = false;
	  }
	  //int ret = SetLpon(lpon_);
	  	l=(chy_.lpon_)?1:0;
	  int ret = SetCommandValue("lpon",chy_.channel_,l);	
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnLowpassZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnLowpassZ",true);
	int l=0;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpon",chz_.channel_,l);
		chz_.lpon_=(l==1)?true:false;
		//int ret = GetLpon(lpon_);		
		if (ret!=DEVICE_OK)
			return ret;
		if (chz_.lpon_){
			pProp->Set(g_Lowpass_On);			
		}else{
			pProp->Set(g_Lowpass_Off);			
		}		
	}else if (eAct == MM::AfterSet){
	  std::string lpon;
      pProp->Get(lpon);
	  if (lpon == g_Lowpass_On){
		chz_.lpon_ = true;
	  }else{
         chz_.lpon_ = false;
	  }
	  //int ret = SetLpon(lpon_);
	  	l=(chz_.lpon_)?1:0;
	  int ret = SetCommandValue("lpon",chz_.channel_,l);	
	  if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnLowpassFreqX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnLowpassFreqX",true);
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpf",chx_.channel_,chx_.lpf_);
		//int ret = GetLpf(lpf_);
		if (ret != DEVICE_OK)
			return ret;		
		l=chx_.lpf_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chx_.lpf_=(int)l;
		int ret = SetCommandValue("lpf",chx_.channel_,chx_.lpf_);
		//int ret = SetLpf(lpf_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnLowpassFreqY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnLowpassFreqY",true);
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpf",chy_.channel_,chy_.lpf_);
		//int ret = GetLpf(lpf_);
		if (ret != DEVICE_OK)
			return ret;		
		l=chy_.lpf_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chy_.lpf_=(int)l;
		int ret = SetCommandValue("lpf",chy_.channel_,chy_.lpf_);
		//int ret = SetLpf(lpf_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnLowpassFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnLowpassFreqZ",true);
	long l;
	if (eAct == MM::BeforeGet){		
		int ret = GetCommandValue("lpf",chz_.channel_,chz_.lpf_);
		//int ret = GetLpf(lpf_);
		if (ret != DEVICE_OK)
			return ret;		
		l=chz_.lpf_;
		pProp->Set(l);
	}else if (eAct == MM::AfterSet){
		pProp->Get(l);
		chz_.lpf_=(int)l;
		int ret = SetCommandValue("lpf",chz_.channel_,chz_.lpf_);
		//int ret = SetLpf(lpf_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnGenerateX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnGenerateX",true);	
	if (eAct == MM::BeforeGet){				
		//int ret=GetStatus(chx_.stat_,chx_.channel_);
		//if (ret!=DEVICE_OK)
		//	return ret;
		//chx_.gfkt_=(chx_.stat_&GENERATOR_OFF_MASK)>>9;	
		int ret = GetCommandValue("gfkt",chx_.channel_,chx_.gfkt_);
		if (ret!=DEVICE_OK)
			return ret;
		switch (chx_.gfkt_){
		case 0:
			pProp->Set(g_Generator_Off);		break;
		case 1:
			pProp->Set(g_Generator_Sine);		break;
		case 2:
			pProp->Set(g_Generator_Tri);		break;
		case 3:
			pProp->Set(g_Generator_Rect);	break;
		case 4:
			pProp->Set(g_Generator_Noise);	break;
		case 5:
			pProp->Set(g_Generator_Sweep);	break;
		default:
			pProp->Set(g_Generator_Off);
		}
	}else if (eAct == MM::AfterSet){		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Generator_Off)
         chx_.gfkt_ = 0;
		else if (gen == g_Generator_Sine)
         chx_.gfkt_ = 1;
		else if (gen == g_Generator_Tri)
         chx_.gfkt_ = 2;
		else if (gen == g_Generator_Rect)
         chx_.gfkt_ = 3;
		else if (gen == g_Generator_Noise)
         chx_.gfkt_ = 4;
		else if (gen == g_Generator_Sweep)
         chx_.gfkt_ = 5;		
		//int ret = SetGfkt(gfkt_);
		int ret = SetCommandValue("gfkt",chx_.channel_,chx_.gfkt_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnGenerateY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnGenerateY",true);	
	if (eAct == MM::BeforeGet){					
		//int ret=GetStatus(chy_.stat_,chy_.channel_);
		//if (ret!=DEVICE_OK)
		//	return ret;
		//chx_.gfkt_=(chy_.stat_&GENERATOR_OFF_MASK)>>9;	
		int ret = GetCommandValue("gfkt",chy_.channel_,chy_.gfkt_);
		if (ret!=DEVICE_OK)
			return ret;
		switch (chy_.gfkt_){
		case 0:
			pProp->Set(g_Generator_Off);	break;
		case 1:
			pProp->Set(g_Generator_Sine);	break;
		case 2:
			pProp->Set(g_Generator_Tri);	break;
		case 3:
			pProp->Set(g_Generator_Rect);	break;
		case 4:
			pProp->Set(g_Generator_Noise);	break;
		case 5:
			pProp->Set(g_Generator_Sweep);	break;
		default:
			pProp->Set(g_Generator_Off);
		}
	}else if (eAct == MM::AfterSet){		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Generator_Off)
         chy_.gfkt_ = 0;
		else if (gen == g_Generator_Sine)
         chy_.gfkt_ = 1;
		else if (gen == g_Generator_Tri)
         chy_.gfkt_ = 2;
		else if (gen == g_Generator_Rect)
         chy_.gfkt_ = 3;
		else if (gen == g_Generator_Noise)
         chy_.gfkt_ = 4;
		else if (gen == g_Generator_Sweep)
         chy_.gfkt_ = 5;		
		//int ret = SetGfkt(gfkt_);
		int ret = SetCommandValue("gfkt",chy_.channel_,chy_.gfkt_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnGenerateZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnGenerateZ",true);	
	if (eAct == MM::BeforeGet){					
		//int ret=GetStatus(chz_.stat_,chz_.channel_);
		//if (ret!=DEVICE_OK)
		//	return ret;
		//chz_.gfkt_=(chz_.stat_&GENERATOR_OFF_MASK)>>9;	
		int ret = GetCommandValue("gfkt",chz_.channel_,chz_.gfkt_);
		if (ret!=DEVICE_OK)
			return ret;
		switch (chz_.gfkt_){
		case 0:
			pProp->Set(g_Generator_Off);	break;
		case 1:
			pProp->Set(g_Generator_Sine);	break;
		case 2:
			pProp->Set(g_Generator_Tri);	break;
		case 3:
			pProp->Set(g_Generator_Rect);	break;
		case 4:
			pProp->Set(g_Generator_Noise);	break;
		case 5:
			pProp->Set(g_Generator_Sweep);	break;
		default:
			pProp->Set(g_Generator_Off);
		}
	}else if (eAct == MM::AfterSet){		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Generator_Off)
         chz_.gfkt_ = 0;
		else if (gen == g_Generator_Sine)
         chz_.gfkt_ = 1;
		else if (gen == g_Generator_Tri)
         chz_.gfkt_ = 2;
		else if (gen == g_Generator_Rect)
         chz_.gfkt_ = 3;
		else if (gen == g_Generator_Noise)
         chz_.gfkt_ = 4;
		else if (gen == g_Generator_Sweep)
         chz_.gfkt_ = 5;		
		//int ret = SetGfkt(gfkt_);
		int ret = SetCommandValue("gfkt",chz_.channel_,chz_.gfkt_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnSinAmpX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSinAmpX",true);	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gasin",chx_.channel_,chx_.gasin_);
		//int ret = GetGasin(gasin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gasin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gasin_);
		//int ret = SetGasin(chx_.gasin_);
		int ret = SetCommandValue("gasin",chx_.channel_,chx_.gasin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSinAmpY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSinAmpY",true);	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gasin",chy_.channel_,chy_.gasin_);
		//int ret = GetGasin(gasin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gasin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gasin_);
		//int ret = SetGasin(chx_.gasin_);
		int ret = SetCommandValue("gasin",chy_.channel_,chy_.gasin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSinAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSinAmpZ",true);	
	if (eAct == MM::BeforeGet)
	{		
		int ret = GetCommandValue("gasin",chz_.channel_,chz_.gasin_);
		//int ret = GetGasin(gasin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gasin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gasin_);
		//int ret = SetGasin(chz_.gasin_);
		int ret = SetCommandValue("gasin",chz_.channel_,chz_.gasin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSinOffX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSinOffX",true);	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("gosin",chx_.channel_,chx_.gosin_);
        //ret = GetGosin(gosin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gosin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gosin_);
		//ret = SetGosin(gosin_);
		ret = SetCommandValue("gosin",chx_.channel_,chx_.gosin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSinOffY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSinOffY",true);	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("gosin",chy_.channel_,chy_.gosin_);
        //ret = GetGosin(gosin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gosin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gosin_);
		//ret = SetGosin(gosin_);
		ret = SetCommandValue("gosin",chy_.channel_,chy_.gosin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSinOffZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSinOffZ",true);	
	int ret =0;
	if (eAct == MM::BeforeGet)
	{	
		ret = GetCommandValue("gosin",chz_.channel_,chz_.gosin_);
        //ret = GetGosin(gosin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gosin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gosin_);
		//ret = SetGosin(gosin_);
		ret = SetCommandValue("gosin",chz_.channel_,chz_.gosin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSinFreqX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSinFreqX",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGfsin(gfsin_);
		int ret = GetCommandValue("gfsin",chx_.channel_,chx_.gfsin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gfsin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gfsin_);
		//int ret = SetGfsin(gfsin_);
		int ret = SetCommandValue("gfsin",chx_.channel_,chx_.gfsin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSinFreqY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSinFreqY",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGfsin(gfsin_);
		int ret = GetCommandValue("gfsin",chy_.channel_,chy_.gfsin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gfsin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gfsin_);
		//int ret = SetGfsin(gfsin_);
		int ret = SetCommandValue("gfsin",chy_.channel_,chy_.gfsin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSinFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSinFreqZ",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGfsin(gfsin_);
		int ret = GetCommandValue("gfsin",chz_.channel_,chz_.gfsin_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gfsin_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gfsin_);
		//int ret = SetGfsin(gfsin_);
		int ret = SetCommandValue("gfsin",chz_.channel_,chz_.gfsin_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnTriAmpX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriAmpX",true);	
	if (eAct == MM::BeforeGet)
	{		
		//int ret = GetGatri(gatri_);
		int ret = GetCommandValue("gatri",chx_.channel_,chx_.gatri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gatri_);
	}else if (eAct == MM::AfterSet){
		pProp->Get(chx_.gatri_);
		int ret = SetCommandValue("gatri",chx_.channel_,chx_.gatri_);
		//int ret = SetGatri(gatri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnTriAmpY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriAmpY",true);	
	if (eAct == MM::BeforeGet)
	{		
		//int ret = GetGatri(gatri_);
		int ret = GetCommandValue("gatri",chy_.channel_,chy_.gatri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gatri_);
	}else if (eAct == MM::AfterSet){
		pProp->Get(chy_.gatri_);
		int ret = SetCommandValue("gatri",chy_.channel_,chy_.gatri_);
		//int ret = SetGatri(gatri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnTriAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriAmpZ",true);	
	if (eAct == MM::BeforeGet)
	{		
		//int ret = GetGatri(gatri_);
		int ret = GetCommandValue("gatri",chz_.channel_,chz_.gatri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gatri_);
	}else if (eAct == MM::AfterSet){
		pProp->Get(chz_.gatri_);
		int ret = SetCommandValue("gatri",chz_.channel_,chz_.gatri_);
		//int ret = SetGatri(gatri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnTriOffX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriOffX",true);		
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGotri(chx_.gotri_);
		int ret = GetCommandValue("gotri",chx_.channel_,chx_.gotri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gotri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gotri_);
		int ret = SetCommandValue("gotri",chx_.channel_,chx_.gotri_);
		//int ret = SetGotri(gotri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnTriOffY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriOffY",true);		
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGotri(gotri_);
		int ret = GetCommandValue("gotri",chy_.channel_,chy_.gotri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gotri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gotri_);
		int ret = SetCommandValue("gotri",chy_.channel_,chy_.gotri_);
		//int ret = SetGotri(gotri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnTriOffZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriOffZ",true);		
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGotri(gotri_);
		int ret = GetCommandValue("gotri",chz_.channel_,chz_.gotri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gotri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gotri_);
		int ret = SetCommandValue("gotri",chz_.channel_,chz_.gotri_);
		//int ret = SetGotri(gotri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnTriFreqX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriFreqX",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGftri(gftri_);
		int ret = GetCommandValue("gftri",chx_.channel_,chx_.gftri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gftri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gftri_);
		int ret = SetCommandValue("gftri",chx_.channel_,chx_.gftri_);
		//int ret = SetGftri(gftri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnTriFreqY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriFreqY",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGftri(gftri_);
		int ret = GetCommandValue("gftri",chy_.channel_,chy_.gftri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gftri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gftri_);
		int ret = SetCommandValue("gftri",chy_.channel_,chy_.gftri_);
		//int ret = SetGftri(gftri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnTriFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriFreqZ",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGftri(gftri_);
		int ret = GetCommandValue("gftri",chz_.channel_,chz_.gftri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gftri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gftri_);
		int ret = SetCommandValue("gftri",chz_.channel_,chz_.gftri_);
		//int ret = SetGftri(gftri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnTriSymX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriSymX",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGstri(gstri_);
		int ret = GetCommandValue("gstri",chx_.channel_,chx_.gstri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gstri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gstri_);
		int ret = SetCommandValue("gstri",chx_.channel_,chx_.gstri_);
		//int ret = SetGstri(gstri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnTriSymY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriSymY",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGstri(gstri_);
		int ret = GetCommandValue("gstri",chy_.channel_,chy_.gstri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gstri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gstri_);
		int ret = SetCommandValue("gstri",chy_.channel_,chy_.gstri_);
		//int ret = SetGstri(gstri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnTriSymZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriSymZ",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGstri(gstri_);
		int ret = GetCommandValue("gstri",chz_.channel_,chz_.gstri_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gstri_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gstri_);
		int ret = SetCommandValue("gstri",chz_.channel_,chz_.gstri_);
		//int ret = SetGstri(gstri_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnRecAmpX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnRecAmpX",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGarec(garec_);
		int ret = GetCommandValue("garec",chx_.channel_,chx_.garec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.garec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.garec_);
		int ret = SetCommandValue("garec",chx_.channel_,chx_.garec_);
		//int ret = SetGarec(garec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnRecAmpY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnRecAmpY",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGarec(garec_);
		int ret = GetCommandValue("garec",chy_.channel_,chy_.garec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.garec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.garec_);
		int ret = SetCommandValue("garec",chy_.channel_,chy_.garec_);
		//int ret = SetGarec(garec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnRecAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnRecAmpZ",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGarec(garec_);
		int ret = GetCommandValue("garec",chz_.channel_,chz_.garec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.garec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.garec_);
		int ret = SetCommandValue("garec",chz_.channel_,chz_.garec_);
		//int ret = SetGarec(garec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnRecOffX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnRecOffX",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGorec(gorec_);
		int ret = GetCommandValue("gorec",chx_.channel_,chx_.gorec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gorec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gorec_);
		int ret = SetCommandValue("gorec",chx_.channel_,chx_.gorec_);
		//int ret = SetGorec(gorec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnRecOffY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnRecOffY",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGorec(gorec_);
		int ret = GetCommandValue("gorec",chy_.channel_,chy_.gorec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gorec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gorec_);
		int ret = SetCommandValue("gorec",chy_.channel_,chy_.gorec_);
		//int ret = SetGorec(gorec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnRecOffZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnRecOffZ",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGorec(gorec_);
		int ret = GetCommandValue("gorec",chz_.channel_,chz_.gorec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gorec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gorec_);
		int ret = SetCommandValue("gorec",chz_.channel_,chz_.gorec_);
		//int ret = SetGorec(gorec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnRecFreqX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnRecFreqX",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGfrec(gfrec_);
		int ret = GetCommandValue("gfrec",chx_.channel_,chx_.gfrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gfrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gfrec_);
		int ret = SetCommandValue("gfrec",chx_.channel_,chx_.gfrec_);
		//int ret = SetGfrec(gfrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnRecFreqY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnRecFreqY",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGfrec(gfrec_);
		int ret = GetCommandValue("gfrec",chy_.channel_,chy_.gfrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gfrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gfrec_);
		int ret = SetCommandValue("gfrec",chy_.channel_,chy_.gfrec_);
		//int ret = SetGfrec(gfrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnRecFreqZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnRecFreqZ",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGfrec(gfrec_);
		int ret = GetCommandValue("gfrec",chz_.channel_,chz_.gfrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gfrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gfrec_);
		int ret = SetCommandValue("gfrec",chz_.channel_,chz_.gfrec_);
		//int ret = SetGfrec(gfrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnRecSymX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnRecSymX",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGsrec(gsrec_);
		int ret = GetCommandValue("gsrec",chx_.channel_,chx_.gsrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gsrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gsrec_);
		int ret = SetCommandValue("gsrec",chx_.channel_,chx_.gsrec_);
		//int ret = SetGsrec(gsrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnRecSymY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnRecSymY",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGsrec(gsrec_);
		int ret = GetCommandValue("gsrec",chy_.channel_,chy_.gsrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gsrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gsrec_);
		int ret = SetCommandValue("gsrec",chy_.channel_,chy_.gsrec_);
		//int ret = SetGsrec(gsrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnRecSymZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnRecSymZ",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGsrec(gsrec_);
		int ret = GetCommandValue("gsrec",chz_.channel_,chz_.gsrec_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gsrec_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gsrec_);
		int ret = SetCommandValue("gsrec",chz_.channel_,chz_.gsrec_);
		//int ret = SetGsrec(gsrec_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnNoiAmpX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNoiAmpX",true);	
	if (eAct == MM::BeforeGet)
	{		
		//int ret = GetGanoi(ganoi_);
		int ret = GetCommandValue("ganoi",chx_.channel_,chx_.ganoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.ganoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.ganoi_);
		int ret = SetCommandValue("ganoi",chx_.channel_,chx_.ganoi_);
		//int ret = SetGanoi(ganoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnNoiAmpY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNoiAmpY",true);	
	if (eAct == MM::BeforeGet)
	{		
		//int ret = GetGanoi(ganoi_);
		int ret = GetCommandValue("ganoi",chy_.channel_,chy_.ganoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.ganoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.ganoi_);
		int ret = SetCommandValue("ganoi",chy_.channel_,chy_.ganoi_);
		//int ret = SetGanoi(ganoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnNoiAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNoiAmpZ",true);	
	if (eAct == MM::BeforeGet)
	{		
		//int ret = GetGanoi(ganoi_);
		int ret = GetCommandValue("ganoi",chz_.channel_,chz_.ganoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.ganoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.ganoi_);
		int ret = SetCommandValue("ganoi",chz_.channel_,chz_.ganoi_);
		//int ret = SetGanoi(ganoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnNoiOffX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNoiOffX",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGonoi(gonoi_);
		int ret = GetCommandValue("gonoi",chx_.channel_,chx_.gonoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gonoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gonoi_);
		int ret = SetCommandValue("gonoi",chx_.channel_,chx_.gonoi_);
		//int ret = SetGonoi(gonoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnNoiOffY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNoiOffY",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGonoi(gonoi_);
		int ret = GetCommandValue("gonoi",chy_.channel_,chy_.gonoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gonoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gonoi_);
		int ret = SetCommandValue("gonoi",chy_.channel_,chy_.gonoi_);
		//int ret = SetGonoi(gonoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnNoiOffZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnNoiOffZ",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGonoi(gonoi_);
		int ret = GetCommandValue("gonoi",chz_.channel_,chz_.gonoi_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gonoi_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gonoi_);
		int ret = SetCommandValue("gonoi",chz_.channel_,chz_.gonoi_);
		//int ret = SetGonoi(gonoi_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSweAmpX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSweAmpX",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGaswe(gaswe_);
		int ret = GetCommandValue("gaswe",chx_.channel_,chx_.gaswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gaswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gaswe_);
		int ret = SetCommandValue("gaswe",chx_.channel_,chx_.gaswe_);
		//int ret = SetGaswe(gaswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSweAmpY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSweAmpY",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGaswe(gaswe_);
		int ret = GetCommandValue("gaswe",chy_.channel_,chy_.gaswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gaswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gaswe_);
		int ret = SetCommandValue("gaswe",chy_.channel_,chy_.gaswe_);
		//int ret = SetGaswe(gaswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSweAmpZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSweAmpY",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGaswe(gaswe_);
		int ret = GetCommandValue("gaswe",chz_.channel_,chz_.gaswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gaswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gaswe_);
		int ret = SetCommandValue("gaswe",chz_.channel_,chz_.gaswe_);
		//int ret = SetGaswe(gaswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSweOffX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSweOffX");	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGoswe(goswe_);
		int ret = GetCommandValue("goswe",chx_.channel_,chx_.goswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.goswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.goswe_);
		int ret = SetCommandValue("goswe",chx_.channel_,chx_.goswe_);
		//int ret = SetGoswe(goswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSweOffY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSweOffY");	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGoswe(goswe_);
		int ret = GetCommandValue("goswe",chy_.channel_,chy_.goswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.goswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.goswe_);
		int ret = SetCommandValue("goswe",chy_.channel_,chy_.goswe_);
		//int ret = SetGoswe(goswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSweOffZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSweOffZ");	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGoswe(goswe_);
		int ret = GetCommandValue("goswe",chz_.channel_,chz_.goswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.goswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.goswe_);
		int ret = SetCommandValue("goswe",chz_.channel_,chz_.goswe_);
		//int ret = SetGoswe(goswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSweTimeX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSweTimeX",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGtswe(gtswe_);
		int ret = GetCommandValue("gtswe",chx_.channel_,chx_.gtswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chx_.gtswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chx_.gtswe_);
		int ret = SetCommandValue("gtswe",chx_.channel_,chx_.gtswe_);
		//int ret = SetGtswe(gtswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSweTimeY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSweTimeY",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGtswe(gtswe_);
		int ret = GetCommandValue("gtswe",chy_.channel_,chy_.gtswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chy_.gtswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chy_.gtswe_);
		int ret = SetCommandValue("gtswe",chy_.channel_,chy_.gtswe_);
		//int ret = SetGtswe(gtswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnSweTimeZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnSweTimeZ",true);	
	if (eAct == MM::BeforeGet)
	{
		//int ret = GetGtswe(gtswe_);
		int ret = GetCommandValue("gtswe",chz_.channel_,chz_.gtswe_);
		if (ret != DEVICE_OK)
			return ret;
		pProp->Set(chz_.gtswe_);
	}	
    else if (eAct == MM::AfterSet)
    {
		pProp->Get(chz_.gtswe_);
		int ret = SetCommandValue("gtswe",chz_.channel_,chz_.gtswe_);
		//int ret = SetGtswe(gtswe_);
		if (ret!=DEVICE_OK)
			return ret;
	}
    return DEVICE_OK;
}
int Mirror::OnScanTypeX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnScanTypeX",true);	
	if (eAct == MM::BeforeGet)
    {			
		//int ret=GetScanType(sct_);
		int ret = GetCommandValue("sct",chx_.channel_,chx_.sct_);				
		switch (chx_.sct_){
		case 0:
			pProp->Set(g_Scan_Type_Off);	break;
		case 1:
			pProp->Set(g_Scan_Type_Sine);	break;
		case 2:
			pProp->Set(g_Scan_Type_Tri);	break;		
		default:
			pProp->Set(g_Scan_Type_Off);
		}
		if (ret!=DEVICE_OK)
			return ret;
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Scan_Type_Off){
			chx_.sct_ = 0;
		}else if (gen == g_Scan_Type_Sine){
			chx_.sct_ = 1;
		}else if (gen == g_Scan_Type_Tri){
			chx_.sct_ = 2;	
		}
		int ret = SetCommandValue("sct",chx_.channel_,chx_.sct_);
		//int ret = SetScanType(sct_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnScanTypeY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnScanTypeY",true);	
	if (eAct == MM::BeforeGet)
    {			
		//int ret=GetScanType(sct_);
		int ret = GetCommandValue("sct",chy_.channel_,chy_.sct_);
			
		switch (chy_.sct_){
		case 0:
			pProp->Set(g_Scan_Type_Off);	break;
		case 1:
			pProp->Set(g_Scan_Type_Sine);	break;
		case 2:
			pProp->Set(g_Scan_Type_Tri);	break;		
		default:
			pProp->Set(g_Scan_Type_Off);
		}
		if (ret!=DEVICE_OK)
			return ret;		
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Scan_Type_Off){
			chy_.sct_ = 0;
		}else if (gen == g_Scan_Type_Sine){
			chy_.sct_ = 1;
		}else if (gen == g_Scan_Type_Tri){
			chy_.sct_ = 2;	
		}
		int ret = SetCommandValue("sct",chy_.channel_,chy_.sct_);
		//int ret = SetScanType(sct_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnScanTypeZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnScanTypeZ",true);	
	if (eAct == MM::BeforeGet)
    {			
		//int ret=GetScanType(sct_);
		int ret = GetCommandValue("sct",chz_.channel_,chz_.sct_);
			
		switch (chz_.sct_){
		case 0:
			pProp->Set(g_Scan_Type_Off);	break;
		case 1:
			pProp->Set(g_Scan_Type_Sine);	break;
		case 2:
			pProp->Set(g_Scan_Type_Tri);	break;		
		default:
			pProp->Set(g_Scan_Type_Off);
		}
		if (ret!=DEVICE_OK)
			return ret;		
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Scan_Type_Off){
			chz_.sct_ = 0;
		}else if (gen == g_Scan_Type_Sine){
			chz_.sct_ = 1;
		}else if (gen == g_Scan_Type_Tri){
			chz_.sct_ = 2;	
		}
		int ret = SetCommandValue("sct",chz_.channel_,chz_.sct_);
		//int ret = SetScanType(sct_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnScanX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnScanX",true);
	if (eAct == MM::BeforeGet)
    {		
		int b=0;
		//int ret=GetScan(ss_);
		int ret = GetCommandValue("ss",chx_.channel_,b);
		chx_.ss_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;			
		if(chx_.ss_){
			pProp->Set(g_Scan_Starting);
		}else{
			pProp->Set(g_Scan_Off);	
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string s;
		int b=0;
		pProp->Get(s);
		if (s == g_Scan_Off){
			chx_.ss_ = false;
			b=0;
		}
		else if (s == g_Scan_Starting){
			chx_.ss_ = true;
			b=1;
		}
	//int ret = SetScan(ss_);
		if(chx_.ss_){
			int ret = SetCommandValue("ss",chx_.channel_,b);
			if (ret!=DEVICE_OK)
				return ret;		
		}
	}
    return DEVICE_OK;
}
int Mirror::OnScanY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnScanY",true);
	if (eAct == MM::BeforeGet)
    {	
		int b=0;
		//int ret=GetScan(ss_);
		int ret = GetCommandValue("ss",chy_.channel_,b);
		chy_.ss_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;			
		if(chy_.ss_){
			pProp->Set(g_Scan_Starting);
		}else{
			pProp->Set(g_Scan_Off);	
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string s;
		int b=0;
		pProp->Get(s);
		if (s == g_Scan_Off){
			chy_.ss_ = false;
			b=0;
		}
		else if (s == g_Scan_Starting){
			chy_.ss_ = true;
			b=1;
		}
		//int ret = SetScan(ss_);
		if(chy_.ss_){
			int ret = SetCommandValue("ss",chy_.channel_,b);	
			if (ret!=DEVICE_OK)
				return ret;	
		}
	}
    return DEVICE_OK;
}
int Mirror::OnScanZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnScanZ",true);
	if (eAct == MM::BeforeGet)
    {	
		int b=0;
		//int ret=GetScan(ss_);
		int ret = GetCommandValue("ss",chz_.channel_,b);
		chz_.ss_=(b==1)?true:false;
		if (ret!=DEVICE_OK)
			return ret;			
		if(chz_.ss_){
			pProp->Set(g_Scan_Starting);
		}else{
			pProp->Set(g_Scan_Off);	
		}
	}
    else if (eAct == MM::AfterSet)
    {		
		std::string s;
		int b=0;
		pProp->Get(s);
		if (s == g_Scan_Off){
			chz_.ss_ = false;
			b=0;
		}
		else if (s == g_Scan_Starting){
			chz_.ss_ = true;
			b=1;
		}
		//int ret = SetScan(ss_);
		if(chz_.ss_){
			int ret = SetCommandValue("ss",chz_.channel_,b);	
			if (ret!=DEVICE_OK)
				return ret;	
		}
	}
    return DEVICE_OK;
}
int Mirror::OnTriggerStartX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriggerStartX",true);
	if (eAct == MM::BeforeGet){			
		//int ret=GetTrgss(trgss_);
		int ret = GetCommandValue("trgss",chx_.channel_,chx_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chx_.trgss_);		
	}else if (eAct == MM::AfterSet){		
		pProp->Get(chx_.trgss_);					
		//int ret = SetTrgss(trgss_);
		int ret = SetCommandValue("trgss",chx_.channel_,chx_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnTriggerStartY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriggerStartY",true);
	if (eAct == MM::BeforeGet){			
		//int ret=GetTrgss(trgss_);
		int ret = GetCommandValue("trgss",chy_.channel_,chy_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chy_.trgss_);		
	}else if (eAct == MM::AfterSet){		
		pProp->Get(chy_.trgss_);					
		//int ret = SetTrgss(trgss_);
		int ret = SetCommandValue("trgss",chy_.channel_,chy_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnTriggerStartZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriggerStartZ",true);
	if (eAct == MM::BeforeGet){			
		//int ret=GetTrgss(trgss_);
		int ret = GetCommandValue("trgss",chz_.channel_,chz_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chz_.trgss_);		
	}else if (eAct == MM::AfterSet){		
		pProp->Get(chz_.trgss_);					
		//int ret = SetTrgss(trgss_);
		int ret = SetCommandValue("trgss",chz_.channel_,chz_.trgss_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnTriggerEndX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriggerEndX");
	if (eAct == MM::BeforeGet)
    {			
		//int ret=GetTrgse(trgse_);
		int ret = GetCommandValue("trgse",chx_.channel_,chx_.trgse_);
		if (ret!=DEVICE_OK)
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chx_.trgse_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chx_.trgse_);					
		//int ret = SetTrgse(trgse_);
		int ret = SetCommandValue("trgse",chx_.channel_,chx_.trgse_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
	return DEVICE_OK;
}
int Mirror::OnTriggerEndY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriggerEndY");
	if (eAct == MM::BeforeGet)
    {			
		//int ret=GetTrgse(trgse_);
		int ret = GetCommandValue("trgse",chy_.channel_,chy_.trgse_);
		if (ret!=DEVICE_OK)
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chy_.trgse_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chy_.trgse_);					
		//int ret = SetTrgse(trgse_);
		int ret = SetCommandValue("trgse",chy_.channel_,chy_.trgse_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
	return DEVICE_OK;
}
int Mirror::OnTriggerEndZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriggerEndZ");
	if (eAct == MM::BeforeGet)
    {			
		//int ret=GetTrgse(trgse_);
		int ret = GetCommandValue("trgse",chz_.channel_,chz_.trgse_);
		if (ret!=DEVICE_OK)
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chz_.trgse_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chz_.trgse_);					
		//int ret = SetTrgse(trgse_);
		int ret = SetCommandValue("trgse",chz_.channel_,chz_.trgse_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
	return DEVICE_OK;
}
int Mirror::OnTriggerIntervalX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriggerIntervalX",true);
	if (eAct == MM::BeforeGet)
    {			
		//int ret=GetTrgsi(trgsi_);
		int ret = GetCommandValue("trgsi",chx_.channel_,chx_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chx_.trgsi_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chx_.trgsi_);					
		//int ret = SetTrgsi(trgsi_);
		int ret = SetCommandValue("trgsi",chx_.channel_,chx_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnTriggerIntervalY(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriggerIntervalY",true);
	if (eAct == MM::BeforeGet)
    {			
		//int ret=GetTrgsi(trgsi_);
		int ret = GetCommandValue("trgsi",chy_.channel_,chy_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chy_.trgsi_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chy_.trgsi_);					
		//int ret = SetTrgsi(trgsi_);
		int ret = SetCommandValue("trgsi",chy_.channel_,chy_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnTriggerIntervalZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriggerIntervalZ",true);
	if (eAct == MM::BeforeGet)
    {			
		//int ret=GetTrgsi(trgsi_);
		int ret = GetCommandValue("trgsi",chz_.channel_,chz_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set(chz_.trgsi_);		
	}
    else if (eAct == MM::AfterSet)
    {		
		pProp->Get(chz_.trgsi_);					
		//int ret = SetTrgsi(trgsi_);
		int ret = SetCommandValue("trgsi",chz_.channel_,chz_.trgsi_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnTriggerTimeX(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {				
		int ret = GetCommandValue("trglen",chx_.channel_,chx_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set((long)chx_.trglen_);		
	}
    else if (eAct == MM::AfterSet)
    {	
		long l;
		pProp->Get(l);
		chx_.trglen_=(int)l;		
		int ret = SetCommandValue("trglen",chx_.channel_,chx_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnTriggerTimeY(MM::PropertyBase* pProp, MM::ActionType eAct){
	if (eAct == MM::BeforeGet)
    {			
		int ret = GetCommandValue("trglen",chy_.channel_,chy_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set((long)chy_.trglen_);		
	}
    else if (eAct == MM::AfterSet)
    {	
		long l;
		pProp->Get(l);
		chy_.trglen_=(int)l;		
		int ret = SetCommandValue("trglen",chy_.channel_,chy_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnTriggerTimeZ(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriggerTimeZ");
	if (eAct == MM::BeforeGet)
    {				
		int ret = GetCommandValue("trglen",chz_.channel_,chz_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;			
		pProp->Set((long)chz_.trglen_);		
	}
    else if (eAct == MM::AfterSet)
    {	
		long l;
		pProp->Get(l);
		chz_.trglen_=(int)l;		
		int ret = SetCommandValue("trglen",chz_.channel_,chz_.trglen_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnTriggerTypeX(MM::PropertyBase* pProp, MM::ActionType eAct){
	LogMessage ("OnTriggerTypeX",true);
	if (eAct == MM::BeforeGet)
    {				
		int ret = GetCommandValue("trgedge",chx_.channel_,chx_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;			
		switch (chx_.trgedge_){
		case 0:
			pProp->Set(g_Trigger_Off);		break;
		case 1:
			pProp->Set(g_Trigger_Rising);	break;
		case 2:
			pProp->Set(g_Trigger_Falling);	break;	
		case 3:
			pProp->Set(g_Trigger_Both);		break;
		default:
			pProp->Set(g_Trigger_Off);
		}	
	}
    else if (eAct == MM::AfterSet)
    {			
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Trigger_Off)
			chx_.trgedge_ = 0;
		else if (gen == g_Trigger_Rising)
			chx_.trgedge_ = 1;
		else if (gen == g_Trigger_Falling)
			chx_.trgedge_ = 2;	
		else if (gen == g_Trigger_Both)
			chx_.trgedge_ = 3;		
		int ret = SetCommandValue("trgedge",chx_.channel_,chx_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnTriggerTypeY(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
    {			
		int ret = GetCommandValue("trgedge",chy_.channel_,chy_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;			
		switch (chy_.trgedge_){
		case 0:
			pProp->Set(g_Trigger_Off);		break;
		case 1:
			pProp->Set(g_Trigger_Rising);	break;
		case 2:
			pProp->Set(g_Trigger_Falling);	break;	
		case 3:
			pProp->Set(g_Trigger_Both);		break;
		default:
			pProp->Set(g_Trigger_Off);
		}	
	}
    else if (eAct == MM::AfterSet)
    {			
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Trigger_Off)
			chy_.trgedge_ = 0;
		else if (gen == g_Trigger_Rising)
			chy_.trgedge_ = 1;
		else if (gen == g_Trigger_Falling)
			chy_.trgedge_ = 2;	
		else if (gen == g_Trigger_Both)
			chy_.trgedge_ = 3;		
		int ret = SetCommandValue("trgedge",chy_.channel_,chy_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
int Mirror::OnTriggerTypeZ(MM::PropertyBase* pProp, MM::ActionType eAct){	
	if (eAct == MM::BeforeGet)
	{			
		int ret = GetCommandValue("trgedge",chz_.channel_,chz_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;			
		switch (chz_.trgedge_){
		case 0:
			pProp->Set(g_Trigger_Off);		break;
		case 1:
			pProp->Set(g_Trigger_Rising);	break;
		case 2:
			pProp->Set(g_Trigger_Falling);	break;	
		case 3:
			pProp->Set(g_Trigger_Both);		break;
		default:
			pProp->Set(g_Trigger_Off);
		}	
	}
    else if (eAct == MM::AfterSet)
    {			
		std::string gen;
		pProp->Get(gen);
		if (gen == g_Trigger_Off)
			chz_.trgedge_ = 0;
		else if (gen == g_Trigger_Rising)
			chz_.trgedge_ = 1;
		else if (gen == g_Trigger_Falling)
			chz_.trgedge_ = 2;	
		else if (gen == g_Trigger_Both)
			chz_.trgedge_ = 3;		
		int ret = SetCommandValue("trgedge",chz_.channel_,chz_.trgedge_);
		if (ret!=DEVICE_OK)
			return ret;		
	}
    return DEVICE_OK;
}
