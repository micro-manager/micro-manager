#include "HID.h"

static int DevCount;
static PSP_DEVICE_INTERFACE_DETAIL_DATA DevDetailData[DEV_NUM];
static HANDLE GlobalDevHandle[DEV_NUM];

/*
 *  HID Initialization
 *    Parameters:      None
 *    Return Value:    None
 */

void HID_Init() 
{
    int  i;

    DevCount = 0;
    for ( i = 0; i < DEV_NUM; i++ )
    {
        GlobalDevHandle[i] = INVALID_HANDLE_VALUE;
        DevDetailData[i] = NULL;
    }
}

/*
 *  HID Uninitialization
 *    Parameters:      None
 *    Return Value:    None
 */
void HID_UnInit() 
{
    int  i;

    for (i = 0; i < DEV_NUM; i++) 
    {
        if ( DevDetailData[i] ) 
        {
            free(DevDetailData[i]);
        }
        if ( GlobalDevHandle[i]  != INVALID_HANDLE_VALUE )
        {
            CloseHandle( GlobalDevHandle[i] );
            GlobalDevHandle[i] = INVALID_HANDLE_VALUE;
        }
    }
}


/*
 *  HID Find Devices
 *    Parameters:      None
 *    Return Value:    Number of Devices found
 */

int HID_FindDevices( USAGE vendorUsagePage, USAGE vendorUsage )
{
    GUID                             HidGuid;
    HDEVINFO                         DevInfo;
    SP_DEVICE_INTERFACE_DATA         DevData;
    PSP_DEVICE_INTERFACE_DETAIL_DATA DevDetail;
    PHIDP_PREPARSED_DATA             PreparsedData;
    HIDP_CAPS                        Capabilities;
    ULONG                            Length;
    int                              Index;
    BOOL                             ok;
    HANDLE                           DevHandle;

    DevCount = 0;
    /* Get GUID for all System HIDs */
    HidD_GetHidGuid(&HidGuid);

    /* Get Device Information for all present devices */
    DevInfo = SetupDiGetClassDevs(&HidGuid,
                                NULL, 
                                NULL, 
                                (DIGCF_PRESENT | DIGCF_DEVICEINTERFACE)
                                );

    DevData.cbSize = sizeof(DevData);
    DevDetail = NULL;
    Index = -1;
    /* Scan all Devices */
    do 
    {
            Index++;
            /* Device Interface Element of a Device Information set */
            ok = SetupDiEnumDeviceInterfaces(DevInfo, 
                                            0, 
                                            &HidGuid, 
                                            Index, 
                                            &DevData
                                            );
            if ( !ok ) 
            {
                /*
                * JTZ: We're finishing enumerate the HID interfaces.
                */
                break;
            }
            /* Get Device Interface Details - Get Length */
            ok = SetupDiGetDeviceInterfaceDetail(DevInfo, 
                                                 &DevData, 
                                                 NULL, 
                                                 0, 
                                                 &Length, 
                                                 NULL
                                                 );
            /* Allocate memory for Device Detailed Data */
            DevDetail = (PSP_DEVICE_INTERFACE_DETAIL_DATA) malloc(Length);
            /* Set cbSize in the DevDetail structure */
            DevDetail->cbSize = sizeof(SP_DEVICE_INTERFACE_DETAIL_DATA);
            /* Get Device Interface Details */
            ok = SetupDiGetDeviceInterfaceDetail ( DevInfo, 
                                                   &DevData, 
                                                   DevDetail, 
                                                   Length, 
                                                   NULL, 
                                                   NULL
                                                 );
            if ( !ok ) 
            {
                if ( DevDetail ) 
                {   
                    free(DevDetail);
                    DevDetail = NULL;
                }
                continue;
            }
            /* Create File for Device Read/Write */
            DevHandle = CreateFile ( DevDetail->DevicePath, 
                                     GENERIC_READ | GENERIC_WRITE, 
                                     FILE_SHARE_READ | FILE_SHARE_WRITE, 
                                     (LPSECURITY_ATTRIBUTES)NULL,
                                     OPEN_EXISTING, 
                                     0, 
                                     NULL
                                   );
            if ( DevHandle == INVALID_HANDLE_VALUE )
            {
                if ( DevDetail ) 
                {   
                    free(DevDetail);
                    DevDetail = NULL;
                }
                continue;
            }
            /* Get Preparsed Data */
            ok = HidD_GetPreparsedData( DevHandle, &PreparsedData );
            if ( !ok )
            {
                if ( DevDetail ) 
                {   
                    free(DevDetail);
                    DevDetail = NULL;
                }
                continue;
            }
            /* Get Device's Capabilities */
            HidP_GetCaps( PreparsedData, &Capabilities );
            /* Remember Device Interface Detail Data for acceptable Devices */
            if ( ( Capabilities.UsagePage == vendorUsagePage ) && ( Capabilities.Usage == vendorUsage ) )
            {
                DevDetailData[DevCount++] = DevDetail;
                DevDetail = NULL;
            }
            CloseHandle (DevHandle);
    } while (DevCount < DEV_NUM);

    if ( DevDetail ) 
    {
        free(DevDetail);
        DevDetail = NULL;
    }
    SetupDiDestroyDeviceInfoList (DevInfo);
    /*
    * JTZ: The returned DevCount is the Number of interface matches with 
    * UsagePage/Usage criteria, (DevCount-1) index the last one found.
    */
    return (DevCount);
}

/*
 *  HID Get Name (Product String)
 *    Parameters:      num:   Device number
 *                     buf:   Pointer to buffer that receives data
 *                     sz:    Number of bytes to read
 *    Return Value:    TRUE - Success, FALSE - Error
 */
BOOL HID_GetName( int num, char *buf, int sz )
{
    HANDLE DevHandle;
    WCHAR  wbuf[128];
    int    i;
    BOOL   ok;

    if ( DevDetailData[num] == NULL )
    {
        return (FALSE);
    }
    /* Create File for Device Read/Write */
    DevHandle = CreateFile( DevDetailData[num]->DevicePath, 
                            GENERIC_READ | GENERIC_WRITE, 
                            FILE_SHARE_READ | FILE_SHARE_WRITE, 
                            (LPSECURITY_ATTRIBUTES)NULL,
                            OPEN_EXISTING, 
                            0, 
                            NULL
                          );
    if ( DevHandle == INVALID_HANDLE_VALUE )
    {
        return (FALSE);
    }
    ok = HidD_GetProductString( DevHandle, wbuf, sizeof(wbuf) );
    if (ok) 
    {
        for (i = 0; i < sz; i++) 
        {
            *buf++ = (char)wbuf[i];
        }
    }
    CloseHandle (DevHandle);
    return (ok);
}

/*
 *  HID Get Serial Number (Serial Number String)
 *    Parameters:      num:   Device number
 *                     buf:   Pointer to buffer that receives data
 *                     sz:    Number of bytes to read
 *    Return Value:    TRUE - Success, FALSE - Error
 */
BOOL HID_GetSerialNumber( int num, char *buf, int sz )
{
    HANDLE DevHandle;
    WCHAR  wbuf[128];
    int    i;
    BOOL   ok;

    if ( DevDetailData[num] == NULL )
    {
        return (FALSE);
    }
    /* Create File for Device Read/Write */
    DevHandle = CreateFile( DevDetailData[num]->DevicePath, 
                            GENERIC_READ | GENERIC_WRITE, 
                            FILE_SHARE_READ | FILE_SHARE_WRITE, 
                            (LPSECURITY_ATTRIBUTES)NULL,
                            OPEN_EXISTING, 
                            0, 
                            NULL
                          );
    if ( DevHandle == INVALID_HANDLE_VALUE )
    {
        return (FALSE);
    }
    ok = HidD_GetSerialNumberString( DevHandle, wbuf, sizeof(wbuf) );
    if (ok) 
    {
        for (i = 0; i < sz; i++) 
        {
            *buf++ = (char)wbuf[i];
        }
    }
    CloseHandle (DevHandle);
    return (ok);
}

/*
 *  HID Open
 *    Parameters:      num:   Device number
 *    Return Value:    TRUE - Success, FALSE - Error
 */
BOOL HID_Open( int num )
{

    if ( DevDetailData[num] == NULL )
    {
        return (FALSE);
    }
    /* Create File for Device Read/Write */
    GlobalDevHandle[num]  = CreateFile( DevDetailData[num]->DevicePath, 
                            GENERIC_READ | GENERIC_WRITE, 
                            FILE_SHARE_READ | FILE_SHARE_WRITE, 
                            (LPSECURITY_ATTRIBUTES)NULL,
                            OPEN_EXISTING, 
                            0, 
                            NULL
                            );
    if ( GlobalDevHandle[num]  == INVALID_HANDLE_VALUE )
    {
        return (FALSE);
    }
    return (TRUE);
}

/*
 *  HID Open
 *    Parameters:      None
 *    Return Value:    None
 */
void HID_Close( int num ) 
{
    if ( GlobalDevHandle[num]  != INVALID_HANDLE_VALUE )
    {
        CloseHandle( GlobalDevHandle[num]  );
        GlobalDevHandle[num] = INVALID_HANDLE_VALUE;
    }
}


/*
 *  HID Read
 *    Parameters:      buf:   Pointer to buffer that receives data
 *                     sz:    Number of bytes to read
 *                     cnt:   Pointer to number of bytes read
 *    Return Value:    TRUE - Success, FALSE - Error
 */
BOOL HID_Read(int num, unsigned char *buf, unsigned int sz, unsigned int *cnt) 
{
    BOOL  ok;

    /* JTZ: Read from Device, we still use Synchronous read 
    * this API will block until the request data is got.
    */
    ok = ReadFile(GlobalDevHandle[num] , buf, sz, (unsigned long *)cnt, NULL);
    return (ok);
}


/*
 *  HID Write
 *    Parameters:      buf:   Pointer to buffer with data to write
 *                     sz:    Number of bytes to write
 *                     cnt:   Pointer to number of bytes written
 *    Return Value:    TRUE - Success, FALSE - Error
 */
BOOL HID_Write(int num, unsigned char *buf, unsigned int sz, unsigned int *cnt) 
{
    BOOL  ok;

    /* Write to Device */
    ok = WriteFile(GlobalDevHandle[num] , buf, sz, (unsigned long *)cnt, NULL);
    return (ok);
}

BOOL  HID_SetFeature( int num, unsigned char *buf, unsigned int sz )
{
    BOOL  ok;
    /* Write to Device */
    ok = HidD_SetFeature( GlobalDevHandle[num] , buf, sz );
    return (ok);
}

BOOL  HID_GetFeature( int num, unsigned char *buf, unsigned int sz )
{
    BOOL  ok;
    /* Write to Device */
    ok = HidD_GetFeature( GlobalDevHandle[num] , buf, sz );
    return (ok);
}
