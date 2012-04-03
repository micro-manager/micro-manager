#include <windows.h>
#include <stdlib.h>
#include <stdio.h>
#include "HIDDLL.h"
#include "Mightex_BLSDriver_SDK.h"

const int MAX_DEVICE            =   36; //16;
const int MAX_CHANNEL           =   32;
const int DEFAULT_CHANNEL       =   4 ;
const int MAX_STR_LENGTH        =  0x1000;

struct pulseProfile
{
	int pulseCount;
	int repeatCount;
	int pulses[MAX_PROFILE_ITEM];
}tPulseProfile;

struct intelliRules 
{
	int IMax;  // %, e.g. 2000 means 200%
	int DCMax; // max duty cycle,
	int PWMax; // us, only for T2.
}tIntelliRules;

static const char *Mightex_Product_String = "Sirius BLS";
static int totalUSBDevices = 0;
static BOOL deviceOpened[MAX_DEVICE];
static int deviceChannels[MAX_DEVICE];
static int devicePulses[MAX_DEVICE][MAX_CHANNEL+1];
static struct intelliRules deviceIntelliRules[MAX_DEVICE][MAX_CHANNEL+1];

extern BOOL putInShowBuffer;
extern char showBuffer[MAX_STR_LENGTH];
extern int  showBufferIndex;
static int  currentModuleType;

extern int packetHeadCount, packetExpectedEchoCount;
extern BYTE packetEchoValue[16];
extern BOOL packetEchoFlag;
extern int packetEchoCount;

//in DeviceOperate.cpp
int ModuleWrite( int devHandle, void *buffer, int size, BOOL flushFlag, int &error );
int ModuleRead( int devHandle, int waitMs );
int GetParameter( int index );

int round(double number)
{
    return (number >= 0) ? (int)(number + 0.5) : (int)(number - 0.5);
}

int GetDeviceODRules( int DevHandle )
{
	char commandStr[36];
	int error = 0;

	for(int i = 1; i < MAX_CHANNEL+1; i++)
	{
		//OD_IMax
		putInShowBuffer = TRUE;
		showBufferIndex = 0;
		memset(showBuffer, '\0', sizeof(showBuffer));
		sprintf(commandStr, "?GetImax %d \n\r", i);
		ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);
		Sleep(50);
		deviceIntelliRules[DevHandle][i].IMax = GetParameter(2);

		//OD_Ratio and OD_PulseWidth
		putInShowBuffer = TRUE;
		showBufferIndex = 0;
		memset(showBuffer, '\0', sizeof(showBuffer));
		sprintf(commandStr, "?GetODRules %d \n\r", i);
		ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);
		Sleep(50);
		deviceIntelliRules[DevHandle][i].DCMax = GetParameter(1);
		deviceIntelliRules[DevHandle][i].PWMax = GetParameter(2);
	}

	return error;
}

int MTUSB_BLSDriverInitDevices( void )
{
	for(int i = 0; i < MAX_DEVICE; i++)
	{
		deviceOpened[i] = FALSE;
		deviceChannels[i] = DEFAULT_CHANNEL;
		for(int j = 0; j < MAX_CHANNEL+1; j++)
		{
			devicePulses[i][j] = 1; // pulses count default to 0
			deviceIntelliRules[i][j].IMax = 1000 ; // default to 100.0%
			deviceIntelliRules[i][j].DCMax = 100 ; // default to 10.0%
			deviceIntelliRules[i][j].PWMax = 2000; // default to 2ms.

		}
	}

	totalUSBDevices = HidInitDevice((char *)Mightex_Product_String);
	putInShowBuffer = FALSE;
	return totalUSBDevices;
}

int MTUSB_BLSDriverOpenDevice( int DeviceIndex )
{
	if(totalUSBDevices == 0)
		return -1;
	else if(DeviceIndex > totalUSBDevices-1)
		return -1;
	else
	{
		if(HidOpenDevice(DeviceIndex) == 0)
			return -1;
		else
		{
			deviceOpened[DeviceIndex] = TRUE;
			// now the device is Opened, contact the device to get the device info here.
			GetDeviceODRules(DeviceIndex); 

			return DeviceIndex;
		}
	}
}

int MTUSB_BLSDriverCloseDevice( int DevHandle )
{
	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	else
	{
		HidCloseDevice(DevHandle);
		deviceOpened[DevHandle] = FALSE;
		return 1;
	}
}

int MTUSB_BLSDriverGetSerialNo( int DevHandle, unsigned char *SerNumber, int Size )
{
	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	HidGetSerialNumber(DevHandle, (char *)SerNumber, Size);
	return 1;
}

int MTUSB_BLSDriverGetChannels( int DevHandle )
{
	char deviceNumber[32];
	int deviceChannel;
	char *position;
	char temp,temp2;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	HidGetDeviceName(DevHandle, deviceNumber, sizeof(deviceNumber));
	position = strstr(deviceNumber, "SL");
	if(position)
	{
		position += 6;
		temp = *position;
		temp2 = *(position+1);
		deviceChannel = ((int)(temp)-0x30)*10 + ((int)(temp2)-0x30);
		if ((deviceChannel <= 0) || ( deviceChannel > MAX_CHANNEL))
			deviceChannel = DEFAULT_CHANNEL;
	}
	else
		deviceChannel = DEFAULT_CHANNEL;

	deviceChannels[DevHandle] = deviceChannel;
	return deviceChannel;
}

int MTUSB_BLSDriverGetModuleType( int DevHandle)
{
	char deviceNumber[32];
	char *position;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	HidGetDeviceName(DevHandle, deviceNumber, sizeof(deviceNumber));
	position = strstr(deviceNumber, "SL");
	if(position)
	{
		position += 4;
		if(*position == 'A')
		{
			if(*(position+1) == 'V')
				return MODULE_AV;
			else
				return MODULE_AA;
		}
		else if(*position == 'F')
		{
			if(*(position+1) == 'V')
				return MODULE_FV;
			else
				return MODULE_FA;
		}
		else if(*position == 'X')
		{
			if(*(position+1) == 'V')
				return MODULE_XV;
			else
				return MODULE_XA;
		}
		else if(*position == 'H')
		{
			if(*(position+1) == 'V')
				return MODULE_HV;
			else
				return MODULE_HA;
		}
		else if(*position == 'M')
				return MODULE_MA;
		else if(*position == 'C')
				return MODULE_CA;
		else if(*position == 'Q')
				return MODULE_QA;
		else
		{
			if(*(position+1) == 'A')
				return MODULE_SA;
			else
				return MODULE_SV;
		}
	}
	else
		return MODULE_AA;
}

int MTUSB_BLSDriverSetMode( int DevHandle, int Channel, int Mode )
{
	char commandStr[36];
	int error;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;

	if ((Channel != 88) && ((Channel <= 0)||(Channel > deviceChannels[DevHandle])))
		return -1;

	if(Channel == 88)
		sprintf(commandStr, "MODE 88 %d\n\r", Mode);
	else
		sprintf(commandStr, "MODE %d %d\n\r", Channel, Mode);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);

	return error;
}

int MTUSB_BLSDriverSetNormalCurrent( int DevHandle, int Channel, int Current)
{
	char commandStr[36];
	int error;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;

	if ((Channel != 88) && ((Channel <= 0)||(Channel > deviceChannels[DevHandle])))
		return -1;

    sprintf(commandStr, "CURRENT %d %d\n\r", Channel, Current);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);

	return error;
}

int MTUSB_BLSDriverSetPulseProfile( int DevHandle, int Channel, int Polarity, int PulseCnt, int ReptCnt)
{
	char commandStr[36];
	int error;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;

	if ((Channel != 88) && ((Channel <= 0)||(Channel > deviceChannels[DevHandle])))
		return -1;

	if (PulseCnt > MAX_PULSE_COUNT)
		return -1;

	devicePulses[DevHandle][Channel] = PulseCnt ;

    sprintf(commandStr, "Trigger %d 100 %d %d\n\r", Channel, Polarity, ReptCnt);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);

	return error;
}

int MTUSB_BLSDriverSetPulseDetail( int DevHandle, int Channel, int PulseIndex, int Time0, int Time1, int Time2, int Curr0, int Curr1, int Curr2)
{
	char commandStr[36];
	int error;
	int dutyRatio;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;

	if ((Channel <= 0) || (Channel > deviceChannels[DevHandle]))
		return -1;

	if ((PulseIndex < 0) || ( PulseIndex >= devicePulses[DevHandle][Channel]))
		return -1;

	//Normal Pulse Rule Check
	if ((Time0 <= 0) || (Time1 <= 0) || (Time2 <= 0))
		return -1;

	if ((Curr0 > 1000) || (Curr2 > 1000))
		return -1;

	// Intelli Rules Check
	if (Curr1 > 1000)
	{
		if (Curr1 > deviceIntelliRules[DevHandle][Channel].IMax)
			return -1;

		if (Time1 > deviceIntelliRules[DevHandle][Channel].PWMax)
			return -1;

		if ((Curr0 > 100) || (Curr2 > 100))
			return -1;

		dutyRatio = round( Time1 / (Time0 + Time1 + Time2) * 1000);

		if (dutyRatio > deviceIntelliRules[DevHandle][Channel].DCMax)
			return -1;
	}

	// the pulse is valid

    sprintf(commandStr, "TrigP %d %d %d %d\n\r", Channel, PulseIndex * 3, Curr0, Time0);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);

   sprintf(commandStr, "TrigP %d %d %d %d\n\r", Channel, PulseIndex * 3 + 1, Curr1, Time1);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);

   sprintf(commandStr, "TrigP %d %d %d %d\n\r", Channel, PulseIndex * 3 + 2, Curr2, Time2);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);

	if (PulseIndex == devicePulses[DevHandle][Channel] - 1)
	{
		sprintf(commandStr, "TrigP %d %d 0 0 \n\r", Channel, (PulseIndex + 1) * 3);
		ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);
	}

	return error;
}

int MTUSB_BLSDriverSetFollowModeDetail( int DevHandle, int Channel, int ION, int IOFF)
{
	char commandStr[36];
	int error;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;

	if ((Channel != 88) && ((Channel <= 0)||(Channel > deviceChannels[DevHandle])))
		return -1;

    sprintf(commandStr, "TrigP %d 0 %d 9999\n\r", Channel, IOFF);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);

    sprintf(commandStr, "TrigP %d 1 %d 9999\n\r", Channel, ION);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);

    sprintf(commandStr, "TrigP %d 2 0 0 \n\r", Channel);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);

	return error;
}

int MTUSB_BLSDriverSoftStart( int DevHandle, int Channel )
{
	char commandStr[36];
	int error;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;

	if ((Channel != 88) && ((Channel <= 0)||(Channel > deviceChannels[DevHandle])))
		return -1;

    sprintf(commandStr, "SoftStart %d\n\r", Channel);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);

	return error;
}

int MTUSB_BLSDriverResetDevice( int DevHandle )
{
	char commandStr[36];
	int error;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	
	strcpy(commandStr, "RESET\n\r");
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), FALSE, error);

	return error;
}

int MTUSB_BLSDriverStorePara( int DevHandle )
{
	char commandStr[36];
	int error;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	
	strcpy(commandStr, "STORE\n\r");
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), FALSE, error);

	return error;
}

int MTUSB_BLSDriverSendCommand( int DevHandle, char *Command)
{
	char commandStr[36];
	int error;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	
	strcpy(commandStr, Command);
	strcat(commandStr, "\n\r");
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), FALSE, error);

	return error;
}
