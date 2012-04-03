
#define  MAX_PULSE_COUNT     21   // For SX Modules, it¡¯s 3 instead of 128.
#define  MAX_PROFILE_ITEM     128

#define	DISABLE_MODE	0
#define NORMAL_MODE	1
#define TRIGGER_MODE    3

#define MODULE_AA	0
#define MODULE_AV	1
#define MODULE_SA	2
#define MODULE_SV	3
#define MODULE_MA	4
#define MODULE_CA	5
#define MODULE_HA	6
#define MODULE_HV	7
#define MODULE_FA	8
#define MODULE_FV	9
#define MODULE_XA	10
#define MODULE_XV	11
#define MODULE_QA	12

struct pulse
{
	int timing[3];
	int current[3];
};

// Export functions:
int MTUSB_BLSDriverInitDevices( void );
int MTUSB_BLSDriverOpenDevice( int DeviceIndex );
int MTUSB_BLSDriverCloseDevice( int DevHandle );
int MTUSB_BLSDriverGetSerialNo( int DevHandle, unsigned char *SerNumber, int Size );
int MTUSB_BLSDriverGetChannels( int DevHandle );
int MTUSB_BLSDriverGetModuleType( int DevHandle);
int MTUSB_BLSDriverSetMode( int DevHandle, int Channel, int Mode );
int MTUSB_BLSDriverSetNormalCurrent( int DevHandle, int Channel, int Current);
int MTUSB_BLSDriverSetPulseProfile( int DevHandle, int Channel, int Polarity, int PulseCnt, int ReptCnt);
int MTUSB_BLSDriverSetPulseDetail( int DevHandle, int Channel, int PulseIndex, int Time0, int Time1, int Time2, int Curr0, int Curr1, int Curr2);
int MTUSB_BLSDriverSetFollowModeDetail( int DevHandle, int Channel, int ION, int IOFF);
int MTUSB_BLSDriverSoftStart( int DevHandle, int Channel );
int MTUSB_BLSDriverResetDevice( int DevHandle );
int MTUSB_BLSDriverStorePara( int DevHandle );
int MTUSB_BLSDriverSendCommand( int DevHandle, char *Command);