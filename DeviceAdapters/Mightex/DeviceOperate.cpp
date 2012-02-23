#include <windows.h>
#include <stdlib.h>
#include "HIDDLL.h"

const int USB_FEATURE_SIZE  = 18;
extern const int  MAX_STR_LENGTH        =  0x1000;

extern BOOL putInShowBuffer;
extern char showBuffer[MAX_STR_LENGTH];
extern int  showBufferIndex;

int packetHeadCount, packetExpectedEchoCount;
BYTE packetEchoValue[16];
BOOL packetEchoFlag;
int packetEchoCount;

int ModuleWrite( int devHandle, void *buffer, int size, BOOL flushFlag, int &error )
{
	BYTE sendBuffer[USB_FEATURE_SIZE+1];
	BYTE recvBuffer[USB_FEATURE_SIZE+1];
	PBYTE bPtr;
	int strLength;
	int i, returnValr;
	// Send Content to Module.
	error = 0;
	strLength = size;
	bPtr = (PBYTE)buffer;
 	while(strLength > 0)
	{
		if(strLength > (USB_FEATURE_SIZE-2))
		{
          sendBuffer[0] = 0; // ReportID
          sendBuffer[1] = 1; // ASCII_TYPE
          sendBuffer[2] = USB_FEATURE_SIZE - 2;
          for(i = 3; i <= USB_FEATURE_SIZE; i++)
		  {
              sendBuffer[i] = *bPtr;
              bPtr++;
              strLength--;
		  }
		}
		else
		{
          sendBuffer[0] = 0; // ReportID
          sendBuffer[1] = 1; // ASCII_TYPE
          sendBuffer[2] = (BYTE) strLength;
         for(i = 0; i < sendBuffer[2]; i++)
		  {
              sendBuffer[3+i] = *bPtr;
              bPtr++;
              strLength--;
		  }
		}
		returnValr = HidSetFeature(devHandle, sendBuffer, sizeof(sendBuffer));
		if(returnValr != 1)
		{
			HidCloseDevice(devHandle);
			HidOpenDevice(devHandle);
			if(HidSetFeature(devHandle, sendBuffer, sizeof(sendBuffer)) != 1)
				error = 1;
		}
	}

	if(putInShowBuffer)
	{
		showBufferIndex = 0;
		Sleep(10);
	}
	if(flushFlag)
	{
		recvBuffer[0] = 0; // ReportID should be 0
		returnValr = HidGetFeature(devHandle, recvBuffer, sizeof(recvBuffer));
		if(returnValr != 1)
			error = 1;
		else
		{
			strLength = recvBuffer[2];
			while(strLength != 0)
			{
				for(i = 0; i < strLength; i++)
					if(putInShowBuffer)
					{
						showBuffer[showBufferIndex] = char(recvBuffer[3+i]);
						showBufferIndex++;
					}
				Sleep(20);
				recvBuffer[0] = 0; // ReportID should be 0
				HidGetFeature(devHandle, recvBuffer, sizeof(recvBuffer));
				strLength = recvBuffer[2];
			}
		}
	}

	return size;
}

int ModuleRead( int devHandle, int waitMs )
{
	BYTE recvBuffer[USB_FEATURE_SIZE];
	int strLength;
	int i, returnValr;

	Sleep( waitMs );
	recvBuffer[0] = 0; // ReportID should be 0
	returnValr = HidGetFeature(devHandle, recvBuffer, sizeof(recvBuffer));
	if(returnValr == 1)
	{
		strLength = recvBuffer[2];
		while(strLength != 0)
		{
			for(i = 0; i < strLength; i++)
				if(recvBuffer[3+i] == 0xEE)
					packetHeadCount++;
				else
				{
					if(packetHeadCount == 2)
					{
						packetEchoValue[packetEchoCount] = recvBuffer[3+i];
						packetEchoCount++;
						if(packetEchoCount == packetExpectedEchoCount)
						{
							packetHeadCount = 0;
							packetEchoFlag = TRUE;
						}
					}
					else //{ We may get String echo back }
						packetHeadCount = 0;
				}
			Sleep(20);
			recvBuffer[0] = 0; // ReportID should be 0
			HidGetFeature(devHandle, recvBuffer, sizeof(recvBuffer));
			strLength = recvBuffer[2];
		}
	}
	return 0;
}

int GetParameter( int index )
{
	int spacerCount;
	char tmpBuf[16];
	int i, j, k;

    //If index is 1, it still works, it will take the leading "#" as space.
	spacerCount = 1;
	memset(tmpBuf, '\0', sizeof(tmpBuf));
	tmpBuf[0] = '0';
	for(i = 0; i < MAX_STR_LENGTH; i++)
	{
		if(showBuffer[i] == ' ' && showBuffer[i+1] != '\n' && showBuffer[i+1] != '\r')
			spacerCount++;
		if(showBuffer[i] == '\0')
			return 0;
		if(spacerCount == index)
		{
			k = 0;
			j = i + 1;// Next char should be start of Number, in the case of
                    // index is "1", it simply skip the first "#".
			while(showBuffer[j] != ' ' && showBuffer[j] != '\0' && showBuffer[j] != '>')
			{
				if(showBuffer[j] == '#' || showBuffer[j] == '\r' || showBuffer[j] == '\n')
					;// Do nothing.
				else
				{
					tmpBuf[k] = showBuffer[j];
					k++;
				}
				j++;
			}
			break;
		}
	}

	return atoi(tmpBuf);
}