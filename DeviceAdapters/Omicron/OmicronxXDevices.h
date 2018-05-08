#ifndef OmicronxXDevicesH
#define OmicronxXDevicesH
//---------------------------------------------------------------------------

#ifdef xX_EXPORTS
#define xX_API __declspec(dllexport)
#else
#define xX_API __declspec(dllimport)
#endif

//---------------------------------------------------------------------------

#define xXSTRINGSIZE	128
//---------------------------------------------------------------------------

enum TDeviceComState
{
	xXdcs_Online,
	xXdcs_Offline,
	xXdcs_Requesting,
	xXdcs_Absent,
	xXdcs_NotResponding,
	xXdcs_ComError
};
//---------------------------------------------------------------------------

enum TxX_Error
{
	xXer_OK,
	xXer_ParameterNotValid,
	xXer_ParameterReadOnly,
	xXer_ParameterNotAvailable,
	xXer_ActionNotPossible,
	xXer_ParameterRange,
	xXer_Internal
};
//---------------------------------------------------------------------------

enum TxX_Parameter
{
	xXp_Interlock,
	xXp_PowerOn,
	xXp_DeviceOnOff     = 108,
	xXp_OperatingMode   = 115,
	xXp_SerialNumber	= 131,
	xXp_WaveLength      = 133,
	xXp_SpecPower      	= 134,
	xXp_PowerSetPoint	= 139,
	xXp_ModulationMask		= 1147,
	xXp_ModulationShutter	= 1148

};
//---------------------------------------------------------------------------

enum TxX_DataType
{
	xXtd_Bool 		= 1,
	xXtd_Integer	= 2,
	xXtd_Float 		= 4,
	xXtd_String		= 5,
	xXtd_Enum 		= 8,
	// ReadOnly Types:
	xXtd_ROBool		= 0x81,
	xXtd_ROInteger	= 0x82,
	xXtd_ROFloat	= 0x84,
	xXtd_ROString	= 0x85
};
//---------------------------------------------------------------------------

struct TDeviceID
{
	unsigned int    DeviceIndex;
	char			IDString[20];     	///< Unique for all devices.
	char			Typ[50];     		///< Indicates the device by its model name.
	wchar_t			DeviceName[100];  	///< As programmed in USB (eg. Lambda 1).
};
//---------------------------------------------------------------------------

struct TParameterDetails
{
	TxX_DataType	DataType;
	bool			ReadOnly;
	int 			Digits;
	double			MinValue;
	double			MaxValue;
	bool 			IsValid;
};
//---------------------------------------------------------------------------


// Function Prototypes for dynamic DLL loading


typedef int 		(__stdcall *TxXGetDLLVersion)(void);
typedef TxX_Error 	(__stdcall *TxXInitDLL)(void);
typedef TxX_Error 	(__stdcall *TxXShutdownDLL)(void);
typedef TxX_Error 	(__stdcall *TxXGetNumberOfDevices)(int *NumberOfDevices);
typedef TxX_Error 	(__stdcall *TxXGetDeviceID)(const int DeviceIndex, TDeviceID *DeviceID);
typedef TxX_Error 	(__stdcall *TxXGetDeviceComState)(const int DeviceIndex, TDeviceComState *DeviceComState);
typedef TxX_Error 	(__stdcall *TxXGetChannels)(const int DeviceIndex, int *NumberOfChannels);
typedef TxX_Error 	(__stdcall *TxXGetParameterDetails)(const int DeviceIndex, const TxX_Parameter xX_Parameter, TParameterDetails *ParameterDetails);
typedef TxX_Error 	(__stdcall *TxXGetEnumText)(const int DeviceFullIndex, const TxX_Parameter xX_Parameter, const int Index, wchar_t *EnumText);
typedef TxX_Error 	(__stdcall *TxXSetInt)   (const int DeviceIndex, const TxX_Parameter xX_Parameter, const int IntParameter);
typedef TxX_Error 	(__stdcall *TxXGetInt)   (const int DeviceIndex, const TxX_Parameter xX_Parameter, int *IntParameter);
typedef TxX_Error 	(__stdcall *TxXSetFloat) (const int DeviceIndex, const TxX_Parameter xX_Parameter, const double FloatParameter);
typedef TxX_Error 	(__stdcall *TxXGetFloat) (const int DeviceIndex, const TxX_Parameter xX_Parameter, double *FloatParameter);
typedef TxX_Error 	(__stdcall *TxXSetBool)  (const int DeviceIndex, const TxX_Parameter xX_Parameter, const bool BoolParameter);
typedef TxX_Error 	(__stdcall *TxXGetBool)  (const int DeviceIndex, const TxX_Parameter xX_Parameter, bool *BoolParameter);
typedef TxX_Error 	(__stdcall *TxXSetString)(const int DeviceIndex, const TxX_Parameter xX_Parameter, const char * StringParameter);
typedef TxX_Error 	(__stdcall *TxXGetString)(const int DeviceIndex, const TxX_Parameter xX_Parameter, char * *StringParameter);

typedef void 		(__stdcall *TxXTest)(void);


// Function imports for static linking

#ifdef __cplusplus
extern "C" {
#endif

xX_API int 			__stdcall xXGetDLLVersion(void);
xX_API TxX_Error 	__stdcall xXInitDLL(void);
xX_API TxX_Error 	__stdcall xXShutdownDLL(void);
xX_API TxX_Error 	__stdcall xXGetNumberOfDevices(int *NumberOfDevices);
xX_API TxX_Error 	__stdcall xXGetDeviceID(const int DeviceIndex, TDeviceID *DeviceID);
xX_API TxX_Error 	__stdcall xXGetDeviceComState(const int DeviceIndex, TDeviceComState *DeviceComState);
xX_API TxX_Error 	__stdcall xXGetChannels(const int DeviceIndex, int *ChannelMask);
xX_API TxX_Error 	__stdcall xXGetParameterDetails(const int DeviceIndex, const TxX_Parameter xX_Parameter, TParameterDetails *ParameterDetails);
xX_API TxX_Error 	__stdcall xXGetEnumText(const int DeviceFullIndex, const TxX_Parameter xX_Parameter, const int Index, wchar_t *EnumText);
xX_API TxX_Error 	__stdcall xXSetInt   (const int DeviceIndex, const TxX_Parameter xX_Parameter, const int IntParameter);
xX_API TxX_Error 	__stdcall xXGetInt   (const int DeviceIndex, const TxX_Parameter xX_Parameter, int *IntParameter);
xX_API TxX_Error 	__stdcall xXSetFloat (const int DeviceIndex, const TxX_Parameter xX_Parameter, const double FloatParameter);
xX_API TxX_Error 	__stdcall xXGetFloat (const int DeviceIndex, const TxX_Parameter xX_Parameter, double *FloatParameter);
xX_API TxX_Error 	__stdcall xXSetBool  (const int DeviceIndex, const TxX_Parameter xX_Parameter, const bool BoolParameter);
xX_API TxX_Error 	__stdcall xXGetBool  (const int DeviceIndex, const TxX_Parameter xX_Parameter, bool *BoolParameter);
xX_API TxX_Error 	__stdcall xXSetString(const int DeviceIndex, const TxX_Parameter xX_Parameter, const char * StringParameter);
xX_API TxX_Error 	__stdcall xXGetString(const int DeviceIndex, const TxX_Parameter xX_Parameter, char * *StringParameter);

xX_API void 		__stdcall xXTest(void);


#ifdef __cplusplus
}
#endif


//---------------------------------------------------------------------------
#endif

