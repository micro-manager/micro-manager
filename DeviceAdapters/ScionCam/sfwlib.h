//////////////////////////////////////////////////////////////////////////////////////
//
//
//	Scion 1394 Camera Interface Library
//
//	Version 2.3
//
//	Copyright 2004-2009  Scion Corporation		(Windows 2000/XP/VISTA 32/64 Platforms)
//
//
//////////////////////////////////////////////////////////////////////////////////////

//////////////////////////////////////////////////////////////////////////////////////
//
//
//	sfwlib.h
//
//	header file for Scion 1394 Camera API Library
//
//
//////////////////////////////////////////////////////////////////////////////////////

#ifndef _INC_SFWLIB
#define _INC_SFWLIB

#ifdef	_WIN32
#include	<windows.h>
#include	<setupapi.h>
#endif

#ifdef __cplusplus
extern "C" {
#endif


// type definitions

typedef unsigned char	SFW_BYTE;			// 8-bit unsigned integer
typedef unsigned char	*SFW_LPBYTE;		// -> 8-bit unsigned integer
typedef unsigned int	SFW_DWORD;			// 32-bit unsigned integer
typedef	unsigned int	*SFW_LPDWORD;		// -> 32-bit unsigned integer
typedef char			*SFW_LPSTR;			// -> null terminated string
typedef	void			*SFW_LPVOID;		// -> unspecified data type
typedef int				SFW_INT;			// 32-bit signed integer
typedef int				SFW_INT32;			// 32-bit signed integer
typedef short			SFW_INT16;			// 16-bit signed integer
typedef unsigned int	SFW_UINT;			// 32-bit unsigned integer
typedef unsigned int	SFW_UINT32;			// 32-bit unsigned integer
typedef unsigned short	SFW_UINT16;			// 16-bit unsigned integer


// callback routine definition

#define	SFW_BUS_RESET_CALLBACK		0		// callback on bus reset
#define SFW_DEVICE_REMOVED_CALLBACK	1		// callback on device removal

#define	SFW_CALLBACK			0			// callback in callers thread context
#define	SFW_CALLBACK_APC		1			// callback in requestors thread context

typedef VOID (CALLBACK *LPSFW_PROC)(HANDLE cp, UINT_PTR user_data);


// awb progress callback routine definition

typedef BOOL (CALLBACK *LPSFW_AWBPROC)(HANDLE cp, UINT_PTR user_data);


// auto progress (gain/exposure) callback routine definition

typedef BOOL (CALLBACK *LPSFW_APRPROC)(HANDLE cp, UINT_PTR user_data);


// return code definitions

#define	SFW_OK						0		// successfull completion
#define	SFW_ERROR					1		// terminal error
#define	SFW_BAD_VERSION				2		// illegal driver revision
#define	SFW_DEVICE_NOT_FOUND		3		// device not found
#define	SFW_FW_BUSY					4		// firewire camera is busy or in use
#define	SFW_OUT_OF_MEMORY			5		// not enough memory to complete op
#define	SFW_ILLEGAL_REQUEST			6		// can not perform the request function
#define	SFW_NOT_SUPPORTED			7		// capability not supported for camera
#define	SFW_ABORT_REQUESTED			8		// user initiated abort request
#define	SFW_NO_DRIVER_SUPPORT		9		// driver doesn't support feature
#define	SFW_INVALID_FRAME			10		// frame buffer doesn't exist
#define	SFW_FRAME_INCOMPATIBLE		11		// frame buffer incompatible with settings
#define	SFW_UNSUPPORTED_OS			12		// unsupported os
#define	SFW_FRAME_TIMEOUT			13		// frame buffer timeout
#define	SFW_FRAME_WAIT				14		// frame not complete
#define	SFW_INVALID_PARAMETER		15		// parametric value out of range
#define	SFW_BUFFER_OVERFLOW			16		// buffer overflow
#define	SFW_AWB_SATURATED			17		// awb region is saturated
#define	SFW_RETRY_LIMITED_EXCEEDED	18		// too many retries
#define	SFW_IMAGE_TOO_DARK			19		// image too dark for operation


// firewire camera type codes

#define	sfw_no_supported_types	18

#define	SFW_CFW1310M				0x01	// Scion CFW-1310M Grayscale Camera
#define	SFW_CFW1310C				0x02	// Scion CFW-1310C Camera
#define SFW_CFW1612M				0x03	// Scion CFW-1612M Grayscale Camera
#define	SFW_CFW1612C				0x04	// Scion CFW-1612C Color Camera
#define	SFW_CFW1312M				0x05	// Scion CFW-1312M Grayscale Camera
#define	SFW_CFW1312C				0x06	// Scion CFW-1312C Camera
#define	SFW_CFW1308M				0x07	// Scion CFW-1308M Grayscale Camera
#define	SFW_CFW1308C				0x08	// Scion CFW_1308C Camera
#define	SFW_CFW1610M				0x09	// Scion CFW_1610M Grayscale Camera
#define	SFW_CFW1610C				0x0a	// Scion CFW_1610C Camera
#define SFW_CFW1608M				0x0b	// Scion CFW_1608M Grayscale Camera
#define SFW_CFW1608C				0x0c	// Scion CFW_1608C Camera
#define SFW_CFW1008M				0x0d	// Scion CFW_1008M Grayscale Camera
#define SFW_CFW1008C				0x0e	// Scion CFW_1008C Camera
#define	SFW_CFW1010M				0x0f	// Scion CFW_1010M Grayscale Camera
#define	SFW_CFW1010C				0x10	// Scion CFW_1010C Camera
#define SFW_CFW1012M				0x11	// Scion CFW-1012M Grayscale Camera
#define	SFW_CFW1012C				0x12	// Scion CFW-1012C Color Camera

#define	CAM_CFW1310M				0x8001	// CFW-1310M Grayscale Camera
#define	CAM_CFW1310C				0x8002	// CFW-1310C Camera
#define CAM_CFW1612M				0x8003	// CFW-1612M Grayscale Camera
#define	CAM_CFW1612C				0x8004	// CFW-1612C Color Camera
#define	CAM_CFW1312M				0x8005	// CFW-1312M Grayscale Camera
#define	CAM_CFW1312C				0x8006	// CFW-1312C Camera
#define	CAM_CFW1308M				0x8007	// CFW-1308M Grayscale Camera
#define	CAM_CFW1308C				0x8008	// CFW_1308C Camera
#define	CAM_CFW1610M				0x8009	// CFW_1610M Grayscale Camera
#define	CAM_CFW1610C				0x800a	// CFW_1610C Camera
#define CAM_CFW1608M				0x800b	// CFW_1608M Grayscale Camera
#define CAM_CFW1608C				0x800c	// CFW_1608C Camera
#define CAM_CFW1008M				0x800d	// CFW_1008M Grayscale Camera
#define CAM_CFW1008C				0x800e	// CFW_1008C Camera
#define	CAM_CFW1010M				0x800f	// CFW_1010M Grayscale Camera
#define	CAM_CFW1010C				0x8010	// CFW_1010C Camera
#define CAM_CFW1012M				0x8011	// CFW-1012M Grayscale Camera
#define	CAM_CFW1012C				0x8012	// CFW-1012C Color Camera


// firewire camera device id codes

#define	FWID_CFW1310M				0x0001	// Scion CFW-1310M Grayscale Camera
#define	FWID_CFW1310C				0x0002	// Scion CFW-1310C Color Camera
#define FWID_CFW1612M				0x0003	// Scion CFW-1612M Grayscale Camera
#define	FWID_CFW1612C				0x0004	// Scion CFW-1612C Color Camera
#define	FWID_CFW1312M				0x0005	// Scion CFW-1312M Grayscale Camera
#define	FWID_CFW1312C				0x0006	// Scion CFW-1312C Color Camera
#define	FWID_CFW1308M				0x0007	// Scion CFW-1308M Grayscale Camera
#define	FWID_CFW1308C				0x0008	// Scion CFW-1308C Color Camera
#define	FWID_CFW1610M				0x0009	// Scion CFW_1610M Grayscale Camera
#define	FWID_CFW1610C				0x000a	// Scion CFW_1610C Camera
#define FWID_CFW1608M				0x000b	// Scion CFW_1608M Grayscale Camera
#define FWID_CFW1608C				0x000c	// Scion CFW_1608C Camera
#define FWID_CFW1008M				0x000d	// Scion CFW_1008M Grayscale Camera
#define FWID_CFW1008C				0x000e	// Scion CFW_1008C Camera
#define	FWID_CFW1010M				0x000f	// Scion CFW_1010M Grayscale Camera
#define	FWID_CFW1010C				0x0010	// Scion CFW_1010C Camera
#define FWID_CFW1012M				0x0011	// Scion CFW-1012M Grayscale Camera
#define	FWID_CFW1012C				0x0012	// Scion CFW-1012C Color Camera

#define	FWID_CAM_CFW1310M			0x8001	// CFW-1310M Grayscale Camera
#define	FWID_CAM_CFW1310C			0x8002	// CFW-1310C Color Camera
#define	FWID_CAM_CFW1612M			0x8003	// CFW-1612M Grayscale Camera
#define	FWID_CAM_CFW1612C			0x8004	// CFW-1612C Color Camera
#define	FWID_CAM_CFW1312M			0x8005	// CFW-1312M Grayscale Camera
#define	FWID_CAM_CFW1312C			0x8006	// CFW-1312C Color Camera
#define	FWID_CAM_CFW1308M			0x8007	// CFW-1308M Grayscale Camera
#define	FWID_CAM_CFW1308C			0x8008	// CFW-1308C Color Camera
#define	FWID_CAM_CFW1610M			0x8009	// CFW_1610M Grayscale Camera
#define	FWID_CAM_CFW1610C			0x800a	// CFW_1610C Camera
#define FWID_CAM_CFW1608M			0x800b	// CFW_1608M Grayscale Camera
#define FWID_CAM_CFW1608C			0x800c	// CFW_1608C Camera
#define FWID_CAM_CFW1008M			0x800d	// CFW_1008M Grayscale Camera
#define FWID_CAM_CFW1008C			0x800e	// CFW_1008C Camera
#define	FWID_CAM_CFW1010M			0x800f	// CFW_1010M Grayscale Camera
#define	FWID_CAM_CFW1010C			0x8010	// CFW_1010C Camera
#define FWID_CAM_CFW1012M			0x8011	// CFW-1012M Grayscale Camera
#define	FWID_CAM_CFW1012C			0x8012	// CFW-1012C Color Camera

#define	FWID_SCION					0x11ff
#define	FWID_CAM					0x91ff


// camera capabilites

#define	SFWC_BIT_DEPTH_8			0x0001	// supports 8-bit components
#define	SFWC_BIT_DEPTH_10			0x0002	// supports 10-bit components
#define	SFWC_BIT_DEPTH_12			0x0004	// supports 12-bit components

#define	SFWC_GRAY8_IMAGE			0x0001	// supports 8-bit grayscale
#define	SFWC_GRAY10_IMAGE			0x0002	// supports 10-bit grayscale
#define	SFWC_GRAY12_IMAGE			0x0004	// supports 12-bit grayscale

#define	SFWC_COLOR8_IMAGE			0x0101	// supports 8-bit color
#define	SFWC_COLOR10_IMAGE			0x0102	// supports 10-bit color
#define	SFWC_COLOR12_IMAGE			0x0104	// supports 12-bit color

#define	SFWC_COLOR_CAMERA			0x0100	// color camera

#define	SFWC_28MHZ_READOUT			0x1000	// supports 28 mhz frame rate
#define	SFWC_PREVIEW_MODE			0x2000	// supports preview mode
#define	SFWC_BIN_MODE				0x4000	// supports 2x2 binning
#define	SFWC_TEST_PATTERN			0x8000	// supports test pattern


// handle id definitions

typedef struct
	{
	DWORD			type;				// handle type
	}	sfw_handle_id_t;

typedef	HANDLE		SFW_OBJECT_HANDLE;	// generic object handle definition
typedef	LPHANDLE	LPSFW_OBJECT_HANDLE;// ptr to generic object handle


// dll version information

#define	SFW_RELEASE			0			// final release
#define	SFW_DEVELOPMENT		1			// development
#define	SFW_ALPHA			2			// alpha release
#define	SFW_BETA			3			// beta release

typedef	struct
	{
	sfw_handle_id_t	handle_id;			// handle id
	DWORD			major;				// major version number
	DWORD			minor;				// minor version number
	DWORD			fix;				// fix level
	DWORD			stage;				// stage or sequence no
	DWORD			type;				// version type 
	} sfw_version_t;

typedef	sfw_version_t	*SFW_VER_HANDLE;
typedef	sfw_version_t	**LPSFW_VER_HANDLE;


// scionfw_interface class device data definitions

#define	MAX_FW_DEVICES			10
#define	MAX_FW_DEVICE_NAME_SZ	255

					
// firewire camera driver handler information

typedef	struct	sfw_instance_t	*SFW_HANDLE;
typedef	struct	sfw_instance_t	**LPSFW_HANDLE;


// firewire camera list information

typedef	struct
	{
	DWORD			id;					// camera type id
	DWORD			instance;			// camera instance
	DWORD			revision;			// camera firmware revision
	DWORD			ql_revision;		// camera ql revision
	DWORD			serial_no;			// camera unique id
	DWORD			capabilities;		// camera capabilities
	DWORD			camera_id;			// 1394 camera id
	DWORD			vendor_id;			// 1394 vendor id
	}	sfw_camera_info_t;

typedef	struct
	{
	sfw_handle_id_t		handle_id;		// handle identifier
	DWORD				no_cameras;		// count of installed cameras
	sfw_camera_info_t	list[1];			// list of installed camera
	}	sfw_camera_list_t;


typedef	sfw_camera_list_t	*SFW_LIST_HANDLE;
typedef	sfw_camera_list_t	**LPSFW_LIST_HANDLE;


// frame formats

typedef enum
	{
	SFWF_RAW8,			// raw 8-bit data		
	SFWF_RAW16,			// raw 16-bit data

	SFWF_GRAY8,			// grayscale 8-bit data
	SFWF_GRAY16,		// grayscale 16-bit pixels

	SFWF_RGB24,			// rgb 8-bit components
	SFWF_RGB48,			// rgb 16-bit components

	SFWF_BGR24,			// bgr 8-bit components

	SFWF_RGB32,			// rgb 8-bit components with alpha channel
	SFWF_BGR32,			// bgr 8-bit components with alpha channel

	SFWF_RAW16_12,		// raw 12-bit data
	SFWF_GRAY16_12,		// grayscale 12-bit data
	SFWF_RGB48_12,		// rgb 12-bit components

	SFWF_MAX			// max frame format value
	}	SFW_FRAME_FORMAT;


// frame rates - read out speed from ccd

typedef	enum
	{
	SFW_FR_7_5 = 0,			// 7.5/sec, divisor = 1
	SFW_FR_3_75,			// 3.75/sec, divisor = 2
	SFW_FR_1_875,			// 1.875/sec, divisor = 4
	SFW_FR_0_9375,			// 0.9375/sec, divisor = 8
	SFW_FR_MAX,				// max frame rate enum for 14 mhz camera
	SFW_FR_MAX_14MHZ = SFW_FR_MAX,	// max frame rate enum for 14mhz cameras

	SFW_FR_14MHZ = 0,		// 14mhz readout
	SFW_FR_7MHZ,			// 7mhz readout
	SFW_FR_3_5MHZ,			// 3.5mhz readout
	SFW_FR_1_75MHZ,			// 1.75mhz readout
	SFW_FR_28MHZ,			// 28mhz readout
	SFW_FR_MAX_28MHZ		// max frame rate enum for 28 mhz cameras
	} SFW_FRAME_RATES;


// bayer interpolation options

typedef	enum
	{
	SFW_SOFT_PROCESS = 0,	// software interpolation
	SFW_HARD_PROCESS,		// hardware interpolation if possible
	SFW_PROCESS_MAX
	} SFW_INTERPOLATION_OPTS;


// bayer interpolation modes

typedef	enum
	{
	SFW_BILINEAR = 0,		// use bilinear interpolation (improved image quality/slower)
	SFW_NEAREST_NEIGHBOR,	// use nearest neighbor (lower image quality/faster)
	SFW_INTERPOLATION_MODE_MAX
	} SFW_INTERPOLATION_MODES;


// byte order options

typedef	enum
	{
	SFW_BIG_ENDIAN = 0,		// big endian image
	SFW_LITTLE_ENDIAN,		// little endian image
	SFW_BYTE_ORDER_MAX
	} SFW_BYTE_ORDER_OPTS;


// lut mode values
#define SFW_LUT_DISABLE_ALL		0x0
#define	SFW_LUT_ENABLE_GRAY		0x1
#define	SFW_LUT_ENABLE_RED		0x1
#define	SFW_LUT_ENABLE_GREEN	0x2
#define	SFW_LUT_ENABLE_BLUE		0x4
#define	SFW_LUT_ENABLE_ALL		0x7


// lut select values
#define	SFW_GRAY_LUT			0x0
#define	SFW_RED_LUT				0x0
#define	SFW_GREEN_LUT			0x1
#define	SFW_BLUE_LUT			0x2


// supported parameters

typedef enum
	{
	SFW_GAIN,				// gain
	SFW_RED_GAIN,			// red gain (wb for rgb cameras)
	SFW_GREEN_GAIN,			// green gain (wb for rgb cameras)
	SFW_BLUE_GAIN,			// blue gain (wb for rgb cameras)

	SFW_EXPOSURE,			// exposure

	SFW_BLACK_LEVEL,		// black level

	SFW_SPEED,				// readout speed
	SFW_IMAGE_FORMAT,		// image format

	SFW_INTERPOLATION,		// bayer interpolation option
	SFW_TEST_PATTERN,		// image test pattern mode (1 = on)
	SFW_INVERT,				// image invert
	SFW_BYTE_ORDER,			// byte order

	SFW_PREVIEW_MODE,		// preview mode (1 = on)
	SFW_TIMEOUT,			// frame capture timeout value
	SFW_PACKET_SIZE,		// 1394 async packet size
	SFW_RED_BOOST,			// red/blue boost
	SFW_BLUE_BOOST,			// blue boost
	SFW_LUT_MODE,			// lut mode
	SFW_BIN_MODE,			// bin mode (0 = off, 1 = 2x2)

	SFW_INTERPOLATION_MODE,	// bayer interpolation mode
	SFW_GAMMA_MODE,			// gamma mode (0 = off, 1 = on)
	SFW_CONTRAST_MODE,		// contrast mode (0 = off, 1 = on)
	SFW_CONTRAST,			// contrast value (0 - 255)
	SFW_GAMMA				// gamma value (0.1 - 5.0)
	}	SFW_PARAMETERS;


// camera information

typedef enum
	{
	SFW_CAMERA_TYPE,		// camera type
	SFW_CAMERA_REVISION,	// camera revision
	SFW_SERIAL_NUMBER,		// camera serial number

	SFW_CCD,				// CCD used
	SFW_CCD_TYPE,			// CCD type 
	SFW_CCD_WIDTH,			// max image width in pixels
	SFW_CCD_HEIGHT,			// max image height in pixels
	SFW_CCD_DEPTH,			// max depth supported

	SFW_PREVIEW_WIDTH,		// preview image width in pixels
	SFW_PREVIEW_HEIGHT,		// preview image height in pixels
	SFW_CAMERA_QL_REVISION,	// camera ql revision

	SFW_CAMERA_CAPS,		// supported camera capabilities
	}	SFW_INFO;


// camera stats

typedef enum
	{
	SFW_PACKETS_TRANSFERRED,// packets transferred
	SFW_PACKETS_RETRIED,	// packets retried
	SFW_FRAMES_TRANSFERRED,	// frames transferred
	SFW_FRAMES_FAILED,		// frames failed
	SFW_BUS_RESET_CNT,		// bus reset count
	}	SFW_STATS;


// frame buffer information										

typedef	struct	
	{
	void				*bp;				// -> frame buffer
	unsigned int		bsize;				// size of buffer

	unsigned int		isize;				// size occupied by image

	SFW_FRAME_FORMAT	format;				// frame format
	unsigned int		bit_depth;			// bit depth of image (8/10/12)
	unsigned int		no_components;		// no of components
	unsigned int		pixel_size;			// pixel size in bytes

	unsigned int		width;				// width in pixels
	unsigned int		height;				// height in pixels
	unsigned int		rowbytes;			// row bytes
	} sfw_frame_t;

typedef	sfw_frame_t		*SFW_FRAME_HANDLE;
typedef	sfw_frame_t		**LPSFW_FRAME_HANDLE;


// camera hardware/control information										

typedef	struct sfw_camera_t		*SFW_CAMERA_HANDLE;
typedef	struct sfw_camera_t		**LPSFW_CAMERA_HANDLE;


// advertised support routines

INT		sfw_get_version(LPSFW_VER_HANDLE psfw_ver_handle);
INT		sfw_open_interface(LPSFW_HANDLE psfw_handle);
INT		sfw_close_interface(LPSFW_HANDLE psfw_handle);

INT		sfw_open(SFW_HANDLE ip, 
			SFW_DWORD type, SFW_DWORD index, 
			LPSFW_CAMERA_HANDLE pcamera);
INT		sfw_open_any(SFW_HANDLE ip, 
			SFW_LPDWORD type, SFW_LPDWORD index, 
			LPSFW_CAMERA_HANDLE pcamera);
INT		sfw_close(SFW_HANDLE ip, LPSFW_CAMERA_HANDLE lpcamera);

INT		sfw_list(SFW_HANDLE ip, LPSFW_LIST_HANDLE lp);
INT		sfw_dispose_object(LPSFW_OBJECT_HANDLE hp);

INT		sfw_get_camera_vendor_desc(SFW_HANDLE ip, 
			SFW_DWORD type, SFW_DWORD index, 
			SFW_LPSTR desc, SFW_DWORD size);
INT		sfw_get_camera_vendor_prefix(SFW_HANDLE ip, 
			SFW_DWORD type, SFW_DWORD index, 
			SFW_LPSTR desc, SFW_DWORD size);
INT		sfw_get_camera_product_desc(SFW_HANDLE ip, 
			SFW_DWORD type, SFW_DWORD index, 
			SFW_LPSTR desc, SFW_DWORD size);
INT		sfw_get_camera_product_prefix(SFW_HANDLE ip, 
			SFW_DWORD type, SFW_DWORD index, 
			SFW_LPSTR desc, SFW_DWORD size);

INT		sfw_camera_vendor_desc(SFW_CAMERA_HANDLE cp, 
			SFW_LPSTR desc, SFW_DWORD size);
INT		sfw_camera_vendor_prefix(SFW_CAMERA_HANDLE cp, 
			SFW_LPSTR desc, SFW_DWORD size);
INT		sfw_camera_product_desc(SFW_CAMERA_HANDLE cp, 
			SFW_LPSTR desc, SFW_DWORD size);
INT		sfw_camera_product_prefix(SFW_CAMERA_HANDLE cp, 
			SFW_LPSTR desc, SFW_DWORD size);

INT		sfw_setup_camera(SFW_CAMERA_HANDLE fp, DWORD options);

INT		sfw_assign_buffers(SFW_CAMERA_HANDLE cp, SFW_FRAME_HANDLE fp, DWORD no);
INT		sfw_release_buffers(SFW_CAMERA_HANDLE cp);

INT		sfw_start_frame_index(SFW_CAMERA_HANDLE cp, DWORD index);
INT		sfw_start_frame(SFW_CAMERA_HANDLE cp, SFW_FRAME_HANDLE fp); 

INT		sfw_check_for_complete(SFW_CAMERA_HANDLE cp);
INT		sfw_check_start_next(SFW_CAMERA_HANDLE cp, BOOLEAN start);
INT		sfw_abort_frame(SFW_CAMERA_HANDLE cp);

INT		sfw_copy_frame(SFW_CAMERA_HANDLE cp, SFW_FRAME_HANDLE fp,
			DWORD dformat, unsigned char *dp,
			RECT drect, DWORD drowbytes);

INT		sfw_copy_roi(SFW_CAMERA_HANDLE cp, SFW_FRAME_HANDLE fp,
			RECT roi, unsigned int dformat, unsigned char *dp, 
			RECT drect, unsigned int drowbytes);

INT		sfw_get_info(SFW_CAMERA_HANDLE cp, DWORD key, LPDWORD pvalue);

INT		sfw_get_param(SFW_CAMERA_HANDLE cp, DWORD key, LPDWORD pvalue);
INT		sfw_get_param_min(SFW_CAMERA_HANDLE cp, DWORD key, LPDWORD pvalue);
INT		sfw_get_param_max(SFW_CAMERA_HANDLE cp, DWORD key, LPDWORD pvalue);
INT		sfw_get_param_default(SFW_CAMERA_HANDLE cp, DWORD key, LPDWORD pvalue);
INT		sfw_set_param(SFW_CAMERA_HANDLE cp, DWORD key, DWORD value);

INT		sfw_get_fparam(SFW_CAMERA_HANDLE cp, DWORD key, double *pvalue);
INT		sfw_get_fparam_min(SFW_CAMERA_HANDLE cp, DWORD key, double *pvalue);
INT		sfw_get_fparam_max(SFW_CAMERA_HANDLE cp, DWORD key, double *pvalue);
INT		sfw_get_fparam_default(SFW_CAMERA_HANDLE cp, DWORD key, double *pvalue);
INT		sfw_set_fparam(SFW_CAMERA_HANDLE cp, DWORD key, double value);

INT		sfw_get_stat(SFW_CAMERA_HANDLE cp, DWORD key, LPDWORD pvalue);
INT		sfw_reset_stat(SFW_CAMERA_HANDLE cp, DWORD key);

INT		sfw_load_lut(SFW_CAMERA_HANDLE cp, unsigned char *lut,
			unsigned int lut_ch);
INT		sfw_get_lut(SFW_CAMERA_HANDLE cp, unsigned char *lut,
			unsigned int lut_ch);

INT		sfw_awb_roi_index(SFW_CAMERA_HANDLE cp, DWORD index, 
			LPSFW_AWBPROC awb_proc, DWORD_PTR user_data,
			RECT roi);
INT		sfw_awb_roi(SFW_CAMERA_HANDLE cp, SFW_FRAME_HANDLE fp, 
			LPSFW_AWBPROC awb_proc, DWORD_PTR user_data,
			RECT roi);

INT		sfw_auto_gain_index(SFW_CAMERA_HANDLE cp, DWORD index, 
			LPSFW_APRPROC apr_proc, DWORD_PTR user_data,
			DWORD min, DWORD max);
INT		sfw_auto_gain(SFW_CAMERA_HANDLE cp, SFW_FRAME_HANDLE fp, 
			LPSFW_APRPROC apr_proc, DWORD_PTR user_data,
			DWORD min, DWORD max);

INT		sfw_auto_exp_index(SFW_CAMERA_HANDLE cp, DWORD index, 
			LPSFW_APRPROC apr_proc, DWORD_PTR user_data,
			DWORD min, DWORD max);
INT		sfw_auto_exp(SFW_CAMERA_HANDLE cp, SFW_FRAME_HANDLE fp, 
			LPSFW_APRPROC apr_proc, DWORD_PTR user_data,
			DWORD min, DWORD max);

INT		sfw_auto_exp_index_ex(SFW_CAMERA_HANDLE cp, DWORD index, 
			LPSFW_APRPROC apr_proc, DWORD_PTR user_data,
			DWORD min, DWORD max, DWORD min_gain, DWORD max_gain);
INT		sfw_auto_exp_ex(SFW_CAMERA_HANDLE cp, SFW_FRAME_HANDLE fp, 
			LPSFW_APRPROC apr_proc, DWORD_PTR user_data,
			DWORD min, DWORD max, DWORD min_gain, DWORD max_gain);

INT		sfw_register_event_callback(SFW_CAMERA_HANDLE cp, DWORD type,
			LPSFW_PROC proc, DWORD proc_type, DWORD_PTR user_data);

INT		sfw_remove_event_callback(SFW_CAMERA_HANDLE cp, DWORD type);


// conversion utilities

INT		sfw_exp_to_index(SFW_CAMERA_HANDLE cp, DWORD exposure, LPDWORD index);
INT		sfw_index_to_exp(SFW_CAMERA_HANDLE cp, DWORD index, LPDWORD exposure);

INT		sfw_gain_to_index(SFW_CAMERA_HANDLE cp, double gain, LPDWORD index);
INT		sfw_index_to_gain(SFW_CAMERA_HANDLE cp, DWORD index, double *gain);

INT		sfw_bl_to_index(SFW_CAMERA_HANDLE cp, double bl, LPDWORD index);
INT		sfw_index_to_bl(SFW_CAMERA_HANDLE cp, DWORD index, double *bl);

INT		sfw_wb_gain_to_index(SFW_CAMERA_HANDLE cp, double gain, LPDWORD index);
INT		sfw_index_to_wb_gain(SFW_CAMERA_HANDLE cp, DWORD index, double *gain);

INT		sfw_gamma_to_index(SFW_CAMERA_HANDLE cp, double gamma, LPDWORD index);
INT		sfw_index_to_gamma(SFW_CAMERA_HANDLE cp, DWORD index, double *gamma);


#ifdef __cplusplus
}
#endif

#endif

