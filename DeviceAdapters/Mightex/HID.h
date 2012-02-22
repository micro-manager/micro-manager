#ifndef __HID_H__
#define __HID_H__

#include <windows.h>
#include <stdio.h> 
#include <conio.h>
#include <stdlib.h>
#include <assert.h>

extern "C" 
{
#include <setupapi.h>
#include <hidsdi.h>
}

#define DEV_NUM  36 //16

extern void  HID_Init();
extern void  HID_UnInit();
extern int   HID_FindDevices( USAGE, USAGE );
extern BOOL  HID_GetName(int num, char *buf, int sz);
extern BOOL  HID_GetSerialNumber( int num, char *buf, int sz);
extern BOOL  HID_Open(int num);
extern void  HID_Close(int num);
extern BOOL  HID_Read (int num, unsigned char *buf, unsigned int sz, unsigned int *cnt);
extern BOOL  HID_Write(int num, unsigned char *buf, unsigned int sz, unsigned int *cnt);
extern BOOL  HID_SetFeature(int num, unsigned char *buf, unsigned int sz );
extern BOOL  HID_GetFeature(int num, unsigned char *buf, unsigned int sz );

#endif /* __HID_H__ */
