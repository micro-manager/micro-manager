#include <windows.h>
#include <stdlib.h>
#include <stdio.h>
#include "HIDDLL.h"
#include "Mightex_LEDDriver_SDK.h"

const int MAX_DEVICE            =   36; //16;
const int MAX_CHANNEL           =   32;
const int DEFAULT_CHANNEL       =   4 ;
const int MAX_STR_LENGTH        =  0x1000;

const char *Mightex_Product_String = "Sirius SLC";
static int totalUSBDevices = 0;
static BOOL deviceOpened[MAX_DEVICE];

BOOL putInShowBuffer;
char showBuffer[MAX_STR_LENGTH];
int  showBufferIndex;
int  currentModuleType;

extern int packetHeadCount, packetExpectedEchoCount;
extern BYTE packetEchoValue[16];
extern BOOL packetEchoFlag;
extern int packetEchoCount;

//in DeviceOperate.cpp
int ModuleWrite( int devHandle, void *buffer, int size, BOOL flushFlag, int &error );
int ModuleRead( int devHandle, int waitMs );
int GetParameter( int index );

int MTUSB_LEDDriverInitDevices( void )
{
	for(int i = 0; i < MAX_DEVICE; i++)
		deviceOpened[i] = FALSE;

	totalUSBDevices = HidInitDevice((char *)Mightex_Product_String);
	putInShowBuffer = FALSE;
	return totalUSBDevices;
}

int MTUSB_LEDDriverOpenDevice( int DeviceIndex )
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
			return DeviceIndex;
		}
	}
	//return 0;
}

int MTUSB_LEDDriverCloseDevice( int DevHandle )
{
	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	else
	{
		HidCloseDevice(DevHandle);
		deviceOpened[DevHandle] = FALSE;
		return 1;
	}
	//return 0;
}

int MTUSB_LEDDriverSerialNumber( int DevHandle, char *SerNumber, int Size )
{
	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	HidGetSerialNumber(DevHandle, SerNumber, Size);
	return 1;
}

int MTUSB_LEDDriverDeviceChannels( int DevHandle )
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

	return deviceChannel;
}

int MTUSB_LEDDriverDeviceModuleType( int DevHandle)
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

int MTUSB_LEDDriverSetMode( int DevHandle, int Channel, int Mode )
{
	char commandStr[36];
	int error;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	
	if(Channel == 88)
		sprintf(commandStr, "MODE 88 %d\n\r", Mode);
	else
		sprintf(commandStr, "MODE %d %d\n\r", Channel, Mode);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);

	return error;
}

int MTUSB_LEDDriverSetNormalPara( int DevHandle, int Channel, TLedChannelData *LedChannelDataPtr )
{
	char commandStr[36];
	int error;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	
    sprintf(commandStr, "NORMAL %d %d %d\n\r", 
		Channel, LedChannelDataPtr->Normal_CurrentMax, LedChannelDataPtr->Normal_CurrentSet);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);

	return error;
}

int MTUSB_LEDDriverSetNormalCurrent( int DevHandle, int Channel, int Current )
{
	char commandStr[36];
	int error;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	
    sprintf(commandStr, "CURRENT %d %d\n\r", Channel, Current);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);

	return error;
}

int MTUSB_LEDDriverSetStrobePara( int DevHandle, int Channel, TLedChannelData *LedChannelDataPtr )
{
	char commandStr[36];
	int line;
	int error;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	
    sprintf(commandStr, "STROBE %d %d %d\n\r", 
		Channel, LedChannelDataPtr->Strobe_CurrentMax, LedChannelDataPtr->Strobe_RepeatCnt);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);
	//In case of 128 Pairs, we have to send line by line
	line = 0;
	while(line < MAX_PROFILE_ITEM)
	{
		
		sprintf(commandStr, "STRP %d %d %d %d \n\r", 
			Channel, line, LedChannelDataPtr->Strobe_Profile[line][0], LedChannelDataPtr->Strobe_Profile[line][1]);
		ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);
		if(LedChannelDataPtr->Strobe_Profile[line][1] == 0)
			break;
		line++;
	}

	return error;
}

int MTUSB_LEDDriverSetTriggerPara( int DevHandle, int Channel, TLedChannelData *LedChannelDataPtr )
{
	char commandStr[36];
	int line;
	int error;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	
    sprintf(commandStr, "TRIGGER %d %d %d\n\r", 
		Channel, LedChannelDataPtr->Trigger_CurrentMax, LedChannelDataPtr->Trigger_Polarity);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);
	//In case of 128 Pairs, we have to send line by line
	line = 0;
	while(line < MAX_PROFILE_ITEM)
	{
		
		sprintf(commandStr, "TRIGP %d %d %d %d \n\r", 
			Channel, line, LedChannelDataPtr->Trigger_Profile[line][0], LedChannelDataPtr->Trigger_Profile[line][1]);
		ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);
		if(LedChannelDataPtr->Strobe_Profile[line][1] == 0)
			break;
		line++;
	}

	return error;
}

int MTUSB_LEDDriverResetDevice( int DevHandle )
{
	char commandStr[36];
	int error;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	
	strcpy(commandStr, "RESET\n\r");
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), FALSE, error);

	return error;
}

int MTUSB_LEDDriverStorePara( int DevHandle )
{
	char commandStr[36];
	int error;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	
	strcpy(commandStr, "STORE\n\r");
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), FALSE, error);

	return error;
}

int MTUSB_LEDDriverRestoreDefault( int DevHandle )
{
	char commandStr[36];
	int error;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	
	strcpy(commandStr, "RESTOREDEF\n\r");
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), FALSE, error);

	return error;
}

int MTUSB_LEDDriverGetLoadVoltage( int DevHandle, int Channel )
{
	char commandStr[36];
	int error;
	int timeoutCount = 0;
	int voltage = -1;
	int MSB, LSB;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	
    sprintf(commandStr, "ReadBinaryV %d\n\r", Channel);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), FALSE, error);

	packetHeadCount = 0;
	packetEchoCount = 0;
	packetExpectedEchoCount = 2;
	packetEchoFlag = FALSE;
	while(packetEchoFlag == FALSE && timeoutCount < 50)
	{
		ModuleRead(DevHandle, 10);// Every 10ms, we check Module echo.
		timeoutCount++;
	}
	if(packetEchoFlag)
	{
		MSB = packetEchoValue[0];
		LSB = packetEchoValue[1];
		voltage = (MSB<<8) + LSB;
	}
	else
		voltage = -1;

	return voltage;
}

int MTUSB_LEDDriverGetCurrentPara( int DevHandle, int Channel,TLedChannelData *LedChannelDataPtr,int *Mode )
{
	char commandStr[36];
	int error;
	int step;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	
	memset(LedChannelDataPtr->Strobe_Profile, 0, MAX_PROFILE_ITEM*2);
	memset(LedChannelDataPtr->Trigger_Profile, 0, MAX_PROFILE_ITEM*2);
	// 1. Get Module type
	currentModuleType = MTUSB_LEDDriverDeviceModuleType(DevHandle);
	// 2. Just to Flush the receive Buffer only.
	strcpy(commandStr, "ECHOOFF\n\r");
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);
	if(error == 1)
		return 1;
	// 3. Let's get parameters.
	putInShowBuffer = TRUE;
	showBufferIndex = 0;
	memset(showBuffer, '\0', sizeof(showBuffer));
	// 4. ?MODE, Current Mode.
    sprintf(commandStr, "?MODE %d \n\r", Channel);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);
	if(error == 1)
		return 1;
	*Mode = GetParameter(1);
	// 5. ?Current
    sprintf(commandStr, "?CURRENT %d \n\r", Channel);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);
	if(error == 1)
		return 1;
	if(currentModuleType == MODULE_MA || currentModuleType == MODULE_CA)
	{// Skip first 6 parameters, 7th is Imax, 8th is Iset.
		LedChannelDataPtr->Normal_CurrentMax = GetParameter(7);
		LedChannelDataPtr->Normal_CurrentSet = GetParameter(8);
	}
	else
	{// Skip first 10 parameters, 11th is Imax, 12th is Iset.
		LedChannelDataPtr->Normal_CurrentMax = GetParameter(11);
		LedChannelDataPtr->Normal_CurrentSet = GetParameter(12);
	}
	// 6. ?Strobe
    sprintf(commandStr, "?STROBE %d \n\r", Channel);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);
	if(error == 1)
		return 1;
	LedChannelDataPtr->Strobe_CurrentMax = GetParameter(1);
	LedChannelDataPtr->Strobe_RepeatCnt = GetParameter(2);
	// 7 ?Strp
	memset(showBuffer, '\0', sizeof(showBuffer));
    sprintf(commandStr, "?STRP %d \n\r", Channel);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);
	if(error == 1)
		return 1;
	step = 0;
	while(step < MAX_PROFILE_ITEM)
	{
		LedChannelDataPtr->Strobe_Profile[step][0] = GetParameter(2*(step+1)-1);
		LedChannelDataPtr->Strobe_Profile[step][1] = GetParameter(2*(step+1));
		if(LedChannelDataPtr->Strobe_Profile[step][1] == 0)
			break;
		step++;
	}
	// 8. ?Trigger
 	memset(showBuffer, '\0', sizeof(showBuffer));
	sprintf(commandStr, "?TRIGGER %d \n\r", Channel);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);
	if(error == 1)
		return 1;
	LedChannelDataPtr->Trigger_CurrentMax = GetParameter(1);
	LedChannelDataPtr->Trigger_Polarity = GetParameter(2);
	// 9 ?Trigp
 	memset(showBuffer, '\0', sizeof(showBuffer));
    sprintf(commandStr, "?TRIGP %d \n\r", Channel);
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), TRUE, error);
	if(error == 1)
		return 1;
	step = 0;
	while(step < MAX_PROFILE_ITEM)
	{
		LedChannelDataPtr->Trigger_Profile[step][0] = GetParameter(2*(step+1)-1);
		LedChannelDataPtr->Trigger_Profile[step][1] = GetParameter(2*(step+1));
		if(LedChannelDataPtr->Trigger_Profile[step][1] == 0)
			break;
		step++;
	}
	putInShowBuffer = FALSE;

	return 0;
}

int MTUSB_LEDDriverSendCommand( int DevHandle, char *CommandString )
{
	char commandStr[36];
	int error;

	if ((DevHandle < 0) || ( DevHandle >(totalUSBDevices-1) ) || (deviceOpened[DevHandle] == FALSE))
		return -1;
	
	strcpy(commandStr, CommandString);
	strcat(commandStr, "\n\r");
	ModuleWrite(DevHandle, commandStr, (int) strlen(commandStr), FALSE, error);

	return error;
}