///////////////////////////////////////////////////////////////////////////////
//!
//!
//! \file    common_constants_exp.h
//!
//! \brief    Exported common constants for Camera Firmware and PC Software
//!
//! \author    ABS GmbH Jena (HBau, RG)
//!
//! \date    2006-01-06 -> reorganised
//!
///////////////////////////////////////////////////////////////////////////////
#ifndef _COMMON_CONSTANTS_EXPORTED_H_
#define _COMMON_CONSTANTS_EXPORTED_H_

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Camera function types
//!  defines for supported function bitmask (for internal use)
//!@{

#define FUNC_RESOLUTION             0x0000000000000000  //!< resolution caps (#S_RESOLUTION_CAPS, #S_RESOLUTION_PARAMS)

#define FUNC_PIXELTYPE              0x0000000000000001  //!< pixel type caps (#S_PIXELTYPE_CAPS, #S_PIXELTYPE_PARAMS)
#define FUNC_PREGAIN                0x0000000000000002  //!< pre gain #S_GAIN_CAPS
#define FUNC_GAIN                   0x0000000000000004  //!< gain, brightness #S_GAIN_CAPS
#define FUNC_EXPOSURE               0x0000000000000008  //!< exposure #S_EXPOSURE_CAPS

#define FUNC_AUTOEXPOSURE           0x0000000000000010  //!< auto exposure #S_AUTOEXPOSURE_CAPS
#define FUNC_WHITE_BALANCE          0x0000000000000020  //!< manual white balance #S_WHITE_BALANCE_PARAMS
#define FUNC_SHADING_CORRECTION     0x0000000000000040  //!< shading correction
#define FUNC_BITSHIFT               0x0000000000000080  //!< bit shift mode for bpp modes greater 8bpp S_BITSHIFT_PARAMS

#define FUNC_GAMMA                  0x0000000000000100  //!< gamma / value for pixel interpration #S_GAMMA_PARAMS
#define FUNC_LUT                    0x0000000000000200  //!< look up table (LUT)
#define FUNC_BLACKLEVEL             0x0000000000000400  //!< black level compensation
#define FUNC_TIMESYNC               0x0000000000000800  //!< time synchronization (#S_TIMESYNC_CAPS, #S_TIMESYNC_PARAMS)

#define FUNC_BADPIXEL_CORRECTION    0x0000000000002000  //!< bad pixel correction
#define FUNC_FLIP                   0x0000000000004000  //!< image flip (#S_FLIP_CAPS, #S_FLIP_PARAMS)
#define FUNC_CLOCK                  0x0000000000008000  //!< sensor pixel clock

#define FUNC_TEMPERATURE            0x0000000000010000  //!< sensor temperature
#define FUNC_IO_PORTS               0x0000000000020000  //!< digital I/O ports (#S_IO_PORT_CAPS, #S_IO_PORT_PARAMS)

#define FUNC_MEMORY                 0x0000000000080000  //!< camera memory (eeprom/flash) access

#define FUNC_RESERVED               0x0001200020141000  //!< reserved for further use 

#define FUNC_LOCK_UPDATE            0x0000000000200000  //!< lock sensor settings update
#define FUNC_COLOR_CORRECTION       0x0000000000400000  //!< color correction S_COLOR_CORRECTION_PARAMS
#define FUNC_TIMESTAMP_OVERLAY      0x0000000000800000  //!< time stamp overlay  (#S_TIMESTAMP_OVERLAY_CAPS, S_TIMESTAMP_OVERLAY_PARAMS)

#define FUNC_HUE_SATURATION         0x0000000001000000  //!< hue / saturation S_HUE_SATURATION_PARAMS
#define FUNC_BRIGHTNESS_CONTRAST    0x0000000002000000  //!< brightness / contrast S_BRIGHTNESS_CONTRAST_PARAMS
#define FUNC_FRAMERATE              0x0000000004000000  //!< framerate control see #S_FRAMERATE_PARAMS

#define FUNC_JPEG                   0x0000000010000000  //!< jpeg quqlity control S_JPEG_CAPS
#define FUNC_TRIGGER_OPTIONS        0x0000000040000000  //!< trigger options control see #S_TRIGGER_OPTION_PARAMS
#define FUNC_64BIT_FUNCID           0x0000000080000000  //!< set if 64bit FunctionID's are supported

#define FUNC_RTC                    0x0000000100000000  //!< to get / set the RTC - values

//! \cond DOXYGEN_INCLUDE_VDL
// VDL => "Video Data Logger"
#define FUNC_VDL_STATUS             0x0000000200000000  //!< to get the current VDL status
#define FUNC_VDL_SETTINGS           0x0000000400000000  //!< to get/set the VDL settings
#define FUNC_VDL_TRIGGER            0x0000000800000000  //!< to trigger the VDL by software
#define FUNC_VDL_DIR                0x0000001000000000  //!< to read directory informations
#define FUNC_VDL_FILE               0x0000002000000000  //!< to read image files
//! \endcond DOXYGEN_INCLUDE_VDL

#define FUNC_EDGE_ENHANCE           0x0000004000000000  //!< egde enhancement
#define FUNC_MULTISHOT_CFG          0x0000008000000000  //!< multishot configuration

#define FUNC_MULTI_ROI              0x0000010000000000  //!< multi roi configuration

#define FUNC_MODE_EVENT_CFG         0x0000020000000000  //!< configuration for capture mode #MODE_EVENT and MODE_EVENT_SYNC
#define FUNC_MODE_TIME_CFG          0x0000040000000000  //!< configuration for capture mode #MODE_TIME

#define FUNC_LUT_POST               0x0000080000000000  //!< post LUT processing
#define FUNC_COOLING                0x0000100000000000  //!< image sensor cooling
#define FUNC_COLOR_MAPPING          0x0000400000000000  //!< image color mapping
#define FUNC_BACKLIGHT_COMPENSATION 0x0000800000000000  //!< back light compensation
#define FUNC_ROTATE_REMAP           0x0002000000000000  //!< image rotate and remapping

//!@}

/////////////////////////////////////////////////////////////////////////////
// Constants: Types (Sensor, DSP, Flash, FPGA, CPLD, USB)
/////////////////////////////////////////////////////////////////////////////

//! \name Constants: Sensor Types
//! used by #S_CAMERA_VERSION
//!@{
#define  ST_MT9T001C        0x4000      //!< Micron MT9T001 (3 MP, Color)
#define  ST_MT9T031C        0x4000      //!< Micron MT9T031 (3 MP, Color)

#define  ST_MT9M001M        0x0001      //!< Micron MT9M001 (1,3 MP, Mono)
#define  ST_MT9M001C        0x4001      //!< Micron MT9M001 (1,3 MP, Color)

#define  ST_MT9V022M        0x0002      //!< Micron MT9V022 (WideVGA, Mono)
#define ST_MT9V022C        0x4002      //!< Micron MT9V022 (Wide-VGA, Color)
#define ST_MT9V022R        0x6002      //!< Micron MT9V022 (Wide-VGA, RedClear)

#define  ST_MT9P001M        0x0003      //!< Micron MT9P001 (5 MP, Mono)
#define  ST_MT9P001C        0x4003      //!< Micron MT9P001 (5 MP, Color)

#define  ST_MT9V403M        0x0004      //!< Micron MT9V403 (VGA, Mono)
#define  ST_MT9V403C        0x4004      //!< Micron MT9V403 (VGA, Color)

#define  ST_KAC9628C        0x4005      //!< KODAK  KAC9628 ( VGA, Color)
#define  ST_KAC9618M        0x0005      //!< KODAK  KAC9618 ( VGA, Mono)

#define  ST_KAC9648C        0x4006      //!< KODAK  KAC9648 ( SXGA, Color)
#define  ST_KAC9638M        0x0006      //!< KODAK  KAC9638 ( SXGA, Mono)

#define  ST_MT9P031M        0x0007      //!< Micron MT9P031 (5 MP, Mono)
#define  ST_MT9P031C        0x4007      //!< Micron MT9P031 (5 MP, Color)

#define  ST_MT9M019M        0x0008      //!< Micron MT9M019 (1,3 MP, Mono)
#define  ST_MT9M019C        0x4008      //!< Micron MT9M019 (1,3 MP, Color)

#define  ST_MT9V023M        0x0009      //!< Micron MT9V023 (WideVGA, Mono)
#define ST_MT9V023C        0x4009      //!< Micron MT9V023 (Wide-VGA, Color)
#define ST_MT9V023R        0x6009      //!< Aptina MT9V023 (Wide-VGA, RedClear)

#define  ST_MT9V024M        0x000A      //!< Aptina MT9V024 (WideVGA, Mono)
#define ST_MT9V024C        0x400A      //!< Aptina MT9V024 (Wide-VGA, Color)
#define ST_MT9V024R        0x600A      //!< Aptina MT9V024 (Wide-VGA, RedClear)

#define  ST_EV76C560M      0x000B      //!< E2V Semi EV76C560 (SXGA, Mono)
#define ST_EV76C560C      0x400B      //!< E2V Semi EV76C560 (SXGA, Color)

#define  ST_FPA640x512      0x0010      //!< InGaAs-Sensor 640x512(ShortWave-IR VGA)
#define  ST_FPA320x256     0x0011      //!< InGaAs-Sensor 320x256(ShortWave-IR QVGA)   
#define  ST_FPA320x256_K   0x0012      //!< InGaAs-Sensor 320x256TEC       (ShortWave-IR QVGA)
#define  ST_FPA320x256_K22 0x0013      //!< InGaAs-Sensor 320x256TEC-2,2µm (ShortWave-IR QVGA)

#define ST_SIS002A        0x0020      //!< Special Image Sensor SIS002A
#define ST_SIS002B        0x4021      //!< Special Image Sensor SIS002B
#define ST_SIS9010        0x8022      //!< Special Image Sensor SIS9010

#define  ST_ICX267M        0x8000      //!< Sony ICX267AL (CCD, 1,3 MP, Mono)
#define  ST_ICX267C        0xC000      //!< Sony ICX267AK (CCD, 1,3 MP, Color)

#define  ST_ICX285M        0x8001      //!< Sony ICX285AL (CCD, 1,3 MP, Mono)
#define  ST_ICX285C        0xC001      //!< Sony ICX285AQ (CCD, 1,3 MP, Color)

#define ST_ICX204M        0x8002      //!< Sony ICX204AL (CCD, 0,75 MP, Mono)
#define ST_ICX204C        0xC002      //!< Sony ICX204AK (CCD, 0,75 MP, Color)

#define ST_ICX205M        0x8003      //!< Sony ICX205AL (CCD, 1,3 MP, Mono)
#define ST_ICX205C        0xC003      //!< Sony ICX205AK (CCD, 1,3 MP, Color)

#define ST_ICX414M        0x8004      //!< Sony ICX414AL (CCD, 330 kP, Mono)
#define ST_ICX414C        0xC004      //!< Sony ICX414AQ (CCD, 330 kP, Color)

#define ST_ICX415M        0x8005      //!< Sony ICX415AL (CCD, 460 kP, Mono)
#define ST_ICX415C        0xC005      //!< Sony ICX415AQ (CCD, 460 kP, Color)

#define ST_ICX424M        0x8006      //!< Sony ICX424AL (CCD, 330 kP, Mono)
#define ST_ICX424C        0xC006      //!< Sony ICX424AQ (CCD, 330 kP, Color)

#define ST_ICX274M        0x8007      //!< Sony ICX274AL (CCD, 2 MP, Mono)
#define ST_ICX274C        0xC007      //!< Sony ICX274AQ (CCD, 2 MP, Color)

#define ST_ICX282C        0xC008      //!< Sony ICX282AQ (CCD, 5 MP, Color)
#define ST_ICX252C        0xC009      //!< Sony ICX252AQ (CCD, 3 MP, Color)

#define  ST_KAI0XX50M      0x8010      //!< KODAK InterlineCCD KAI-0xx50 (generic)
#define  ST_KAI01050M      0x8011      //!< KODAK InterlineCCD KAI-01050 Mono
#define  ST_KAI01050C      0xC011      //!< KODAK InterlineCCD KAI-01050 Color
#define  ST_KAI01150M      0x8012      //!< KODAK InterlineCCD KAI-01150 Mono
#define  ST_KAI01150C      0xC012      //!< KODAK InterlineCCD KAI-01150 Color
#define  ST_KAI02050M      0x8013      //!< KODAK InterlineCCD KAI-02050 Mono
#define  ST_KAI02050C      0xC013      //!< KODAK InterlineCCD KAI-02050 Color
#define  ST_KAI02150M      0x8014      //!< KODAK InterlineCCD KAI-02150 Mono
#define  ST_KAI02150C      0xC014      //!< KODAK InterlineCCD KAI-02150 Color
#define  ST_KAI04050M      0x8015      //!< KODAK InterlineCCD KAI-04050 Mono
#define  ST_KAI04050C      0xC015      //!< KODAK InterlineCCD KAI-04050 Color
#define  ST_KAI08050M      0x8016      //!< KODAK InterlineCCD KAI-08050 Mono
#define  ST_KAI08050C      0xC016      //!< KODAK InterlineCCD KAI-08050 Color
#define  ST_EX490EP        0xC017      //!< Sony FCB-EX490EP Color

#define  ST_REDCLEAR       0x2000      //!< if set if bayer mask with only red. Green and blue mask is cleared.
#define  ST_COLOR          0x4000      //!< if set Color Sensor else Mono
#define  ST_CCD            0x8000      //!< if set CCD else CMOS

#define ST_UNKNOWN        0x0000      //!< unknown/undefined sensor type

//!@}


//! \name Constants: DSP Types
//! used by #S_CAMERA_VERSION
//!@{
#define DSP_NONE        0x00      //!< no DSP present
#define DSP_BF533        0x01      //!< Analog Devices Blackfin BF533
#define DSP_BF532        0x02      //!< Analog Devices Blackfin BF532
#define DSP_BF531        0x03      //!< Analog Devices Blackfin BF531
#define DSP_BF561        0x04      //!< Analog Devices Blackfin BF561
#define DSP_SHARC        0x05      //!< Analog Devices SHARC
#define DSP_BF548        0x06      //!< Analog Devices Blackfin BF548


//!@}


//! \name Constants: Flash Types
//! used by #S_CAMERA_VERSION
//!@{
#define FLASH_NONE        0x00      //!< no flash present
#define  FLASH_29LV160      0x01      //!< AMD AM29LV160D (
#define  FLASH_M25P64      0x02      //!< STM M25P64 (64 MBit, SPI)
#define FLASH_M25P40      0x03      //!< STM M25P40 (4 MBit, SPI)
#define FLASH_M25P128      0x04      //!< STM M25P128(128 MBit, SPI)

//!@}


//! \name Constants: FPGA Types
//! used by #S_CAMERA_VERSION
//!@{
#define FPGA_NONE         0x00      //!< no FPGA present
#define FPGA_XC3S400      0x01      //!< Xilinx XC3S400
#define FPGA_XC3S1000      0x02      //!< Xilinx XC3S1000

//!  0x03..0x07 reserved for other XILINX devices

#define FPGA_LFE3_70      0x08      //!< Lattice ECP3-70

//!@}


//! \name Constants: CPLD Types
//! used by #S_CAMERA_VERSION
//!@{
#define CPLD_NONE         0x00      //!< no CPLD present
#define CPLD_XCR3064XL    0x01      //!< Xilinx XCR3064XL
#define CPLD_XC2C64A      0x02      //!< Xilinx XC2C64A
#define CPLD_XC2C128      0x03      //!< Xilinx XC2C128

//!@}

//! \name Constants: Target Types
//!@{
#define TARGET_NONE           0x00000000      //!< no CPLD present
#define TARGET_MASK           0x0000FFFF      //!< target detail type mask see #DSP_NONE, #FPGA_NONE, #CPLD_NONE (based on taget type)

#define TARGET_ID_0           0x00000000      //!< id for the first device of a type
#define TARGET_ID_1           0x00100000      //!< id for the second device of the same type
#define TARGET_ID_2           0x00200000      //!< id for the third device of the same type
#define TARGET_ID_MASK        0x00F00000      //!< id of target type if same type is present twice

#define TARGET_TYPE_DSP       0x01000000      //!< target is a DSP
#define TARGET_TYPE_FPGA      0x02000000      //!< target is a FPGA
#define TARGET_TYPE_CPLD      0x03000000      //!< target is a CPLD
#define TARGET_TYPE_FLASH     0x04000000      //!< target is a FLASH memory
#define TARGET_TYPE_RAM       0x05000000      //!< target is a volatile memory
#define TARGET_TYPE_USB       0x06000000      //!< target is a USB controller
#define TARGET_TYPE_PCIE      0x07000000      //!< target is a PCIe controller
#define TARGET_TYPE_ETHERNET  0x08000000      //!< target is a Ethernet controller
#define TARGET_TYPE_IMGSENSOR 0x09000000      //!< target is a image sensor
#define TARGET_TYPE_MASK      0x0F000000      //!< target of targets

// example Transport id's  

// example target id's  Firmware
#define TARGET_BF533      (TARGET_TYPE_DSP        | TARGET_ID_0 | DSP_BF533)      //!< DSP BF533 as target
#define TARGET_LFE3_70    (TARGET_TYPE_FPGA       | TARGET_ID_0 | FPGA_LFE3_70)   //!< FPGA as target
#define TARGET_XC2C128    (TARGET_TYPE_CPLD       | TARGET_ID_0 | CPLD_XC2C128)   //!< CPLD 0 as target
#define TARGET_XC2C128_1  (TARGET_TYPE_CPLD       | TARGET_ID_1 | CPLD_XC2C128)   //!< CPLD 1 as target
#define TARGET_EX490EP    (TARGET_TYPE_IMGSENSOR  | TARGET_ID_0 | ST_EX490EP)     //!< image sensor EX490EP as target
//!@}

//! \name Constants: RAM Types
//!@{

#define  RAM_NONE        0x00  //!< no volatile memory
#define  RAM_MT46V32M16  0x01  //!< MICRON 4x8Mx16Bit DDR-SDRAM

//!@}

//! \name Constants: USB Controller Types
//! used by #S_CAMERA_VERSION
//!@{
#define USB_NONE          0x00      //!< no USB controller present
#define USB_CYPRESS_SX2   0x01      //!< Cypress SX2
#define USB_NET2272        0x02      //!< PLX NET2272
#define USB_PDIUSB12      0x03      //!< Philips PDIUSBD12
#define USB_DSP_548_OTG   0x04      //!< AD Blackfin BF548

//!@}

//! \name Constants: GigE Controller Types
//!@{
#define ETHERNET_NONE     0x0000      //!< no Gige controller present
#define ETHERNET_AX88180  0x0001      //!< ASIX AX88180 10/100/1000M Gigabit Ethernet Controller

//!@}

//! \name Constants: PCIE Controller Types
//!@{
#define PCIE_NONE          0x0000        //!< no PCIe controller present
#define PCIE_LFE3_70_X2    0x0001        //!< PCIe x2 ABS optimizied IPCore
#define PCIE_LFE3_70_X4    0x0002        //!< PCIe x4 ABS optimizied IPCore

//!@}


//! \name Constants: Transport IDs
//!@{
#define  TRANSPORTID_NONE          0x00000000  

#define  TRANSPORTID_DEVICE_MASK   0x0000FFFF

#define  TRANSPORTID_TYPE_USB      0x10000000
#define  TRANSPORTID_TYPE_PCIE     0x20000000
#define  TRANSPORTID_TYPE_GIGE     0x30000000
#define  TRANSPORTID_TYPE_MASK     0x70000000

#define  TRANSPORTID_CYPRESS_SX2   (TRANSPORTID_TYPE_USB  | USB_CYPRESS_SX2)
#define  TRANSPORTID_NET2272       (TRANSPORTID_TYPE_USB  | USB_NET2272)
#define  TRANSPORTID_PDIUSB12      (TRANSPORTID_TYPE_USB  | USB_PDIUSB12)
#define  TRANSPORTID_DSP_548_OTG   (TRANSPORTID_TYPE_USB  | USB_DSP_548_OTG)

#define  TRANSPORTID_AX88180       (TRANSPORTID_TYPE_GIGE | ETHERNET_AX88180)

#define  TRANSPORTID_LFE3_70_X2    (TRANSPORTID_TYPE_PCIE | PCIE_LFE3_70_X2)
#define  TRANSPORTID_LFE3_70_X4    (TRANSPORTID_TYPE_PCIE | PCIE_LFE3_70_X4)

//!@}

//! \name Constants: Camera Platform ID
//! used by #S_CAMERA_VERSION
//!@{
#define CPID_NONE         0x00      //!< no platform id
#define CPID_UK11XX        0x01      //!< platform UK11xx
#define CPID_UK12XX        0x02      //!< platform UK12xx
#define CPID_UK13XX        0x03      //!< platform UK13xx
#define CPID_UK20XX        0x04      //!< platform UK20xx (2000/2001)
#define CPID_UK30XX        0x06      //!< platform UK30xx
#define CPID_UK99XX        0x07      //!< platform UK9900
#define CPID_GMK9XX        0x08      //!< platform GMK9XX
#define CPID_OEM001        0x20      //!< platform OEM001
#define CPID_OEM002        0x21      //!< platform OEM002
#define CPID_DK80XX        0x30      //!< platform DK80xx
#define CPID_DK84XX        0x31      //!< platform DK84xx
#define CPID_PK80XX        0x40      //!< platform PK80xx
#define CPID_GK99XX        0x50      //!< platform GK99xx
#define CPID_GK11XX        0x51      //!< platform GK11xx
#define CPID_EMUDEV        0xFF      //!< platform emulated device, input from local system

//!@}

//! \name Constants: notification codes
//! used by #CamUSB_SetDeviceNotifyMsg message send to the application
//!@{
#define NOTIFY_CODE_CAM_ATTACHED      0x00  //!< a new camera has been attached to usb
#define NOTIFY_CODE_CAM_REMOVED       0x01  //!< a camera has been removed from usb
#define NOTIFY_CODE_CAM_RELEASED      0x02  //!< camera released for further use
#define NOTIFY_CODE_CAM_IN_USED       0x03  //!< camera locked by application

//!@}


/////////////////////////////////////////////////////////////////////////////
//! \name Constants: CaptureModes
//! used by #CamUSB_SetCaptureMode and #CamUSB_GetCaptureMode
//!@{
#define  MODE_TRIGGERED_SW        0x00      //!< software triggered mode
#define  MODE_TRIGGERED_HW        0x01      //!< hardware triggered mode
#define  MODE_CONTINUOUS         0x02      //!< continuous mode (live stream) 
#define  MODE_EVENT_SYNC         0x05      //!< image content based trigger (with hardware triggered mode
#define  MODE_EVENT              0x06      //!< image content based trigger (with continuous mode)
#define  MODE_TIME               0x09      //!< time based image trigger


//! \brief software asynchron triggered mode \n
//! \brief This mode allow a user to trigger (call to CamUSB_TriggerImage) \n
//! \brief the image acquisition and get the image later (call to CamUSB_GetImage) \n
//! \brief If you call CamUSB_GetImage without a CamUSB_TriggerImage call the \n
//! \brief function return return retNOIMG after timeout (at least 4sec).
#define  MODE_ASYNC_TRIGGER      0x80


//! max. camera supported capture mode for internal use
#define MODE_MAX                0x09

//! mask of camera supported capture modes for internal use
#define MODE_MASK_CAM           0x0F

//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Transfer Options
//! used by #CamUSB_SetCaptureMode
//!@{

#define  TRANSFER_OPTION_DEFAULT      0x0000    //!< standard transferoptions

//!@}


/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Standard Resolutions
//! used by #CamUSB_SetStandardRes and #CamUSB_GetStandardRes
//!@{
#define NO_STDRES             0x0000  //!< no standard resolution (ROI)
#define STDRES_QSXGA          0x0001  //!< QSXGA     2592 x 1944
#define STDRES_QXGA           0x0002  //!< QXGA      2048 x 1536
#define STDRES_UXGA           0x0004  //!< UXGA      1600 x 1200
#define STDRES_WXGA           0x0008  //!< WXGA      1360 x 1024
#define STDRES_SXGA           0x0010  //!< SXGA      1280 x 1024
#define STDRES_XGA            0x0020  //!< XGA       1024 x 768
#define STDRES_SVGA           0x0040  //!< SVGA       800 x 600
#define STDRES_WIDEVGA        0x0080  //!< Wide-VGA   752 x 480
#define STDRES_VGA            0x0100  //!< VGA        640 x 480
#define STDRES_CIF            0x0200  //!< CIF        352 x 288
#define STDRES_QVGA           0x0400  //!< QVGA       320 x 240
#define STDRES_HDTV_1080      0x0800  //!< HDTV 1080 1920 x 1080
#define STDRES_HDTV_720       0x1000  //!< HDTV  720 1280 x 720
#define STDRES_HDTV_720_SBS   0x2000  //!< HDTV  720  640 x 720 for stereo camera 3D
#define STDRES_HDTV_720_SBSFV 0x4000  //!< HDTV  720  640 x 720 for stereo camera 3D side by side format

//! full sensor resolution... depends on sensor resolution capabilities
#define STDRES_FULLSENSOR      0x8000

//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Standard Resolution 2
//! used by #CamUSB_SetStandardRes2, #CamUSB_GetStandardRes2
//!@{

#define STDRES2_NONE          0x0000000000000000  //!< no standard resolution (ROI)
#define STDRES2_QVGA          0x0000000000000001  //!< QVGA       320 x 240
#define STDRES2_WQVGA         0x0000000000000002  //!< WQVGA      384 x 240
#define STDRES2_VGA           0x0000000000000004  //!< VGA        640 x 480
#define STDRES2_WIDEVGA       0x0000000000000008  //!< Wide-VGA   752 x 480
#define STDRES2_SVGA          0x0000000000000010  //!< SVGA       800 x 600
#define STDRES2_XGA           0x0000000000000020  //!< XGA       1024 x 768
#define STDRES2_WXGA          0x0000000000000040  //!< WXGA      1360 x 768
#define STDRES2_XGA2          0x0000000000000080  //!< XGA-2     1360 x 1024
#define STDRES2_SXGA          0x0000000000000100  //!< SXGA      1280 x 1024
#define STDRES2_WSXGAP        0x0000000000000200  //!< WSXGA     1680 x 1050
#define STDRES2_UXGA          0x0000000000000400  //!< UXGA      1600 x 1200
#define STDRES2_WUXGA         0x0000000000000800  //!< WUXGA     1920 x 1200
#define STDRES2_QXGA          0x0000000000001000  //!< QXGA      2048 x 1536
#define STDRES2_WQXGA         0x0000000000002000  //!< WQXGA     2560 x 1600
#define STDRES2_QSXGA         0x0000000000004000  //!< QSXGA     2560 x 2048
#define STDRES2_QUXGA         0x0000000000008000  //!< QUXGA     3200 x 2400
#define STDRES2_FULLSENSOR    0x8000000000000000  //!< full sensor resolution

//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Gain Channels
//!  used by #FUNC_GAIN, #CamUSB_SetGain, and #CamUSB_GetGain
//!@{

#define GAIN_RED            0x0001  //!< gain channel red
#define GAIN_GREEN1         0x0002  //!< gain channel green1
#define GAIN_GREEN2         0x0004  //!< gain channel green2
#define GAIN_GREEN          0x0008  //!< gain channel green
#define GAIN_BLUE           0x0010  //!< gain channel blue

//! \brief GAIN_GLOBAL represents all gain channels supported by the camera.\n
//! \brief A monochrome camera has only this channel, if a color camera\n
//! \brief supports the channel you can use it to set all the \n
//! \brief other supported channels to the same value.\n
#define GAIN_GLOBAL         0x0020

//! Red-, Green- and Blue-Channel
#define GAIN_RGB            (GAIN_RED|GAIN_GREEN|GAIN_BLUE)

//! Red-, Green1/2 and Blue-Channel
#define GAIN_RG12B          (GAIN_RED|GAIN_GREEN1|GAIN_GREEN2|GAIN_BLUE)

//! generic gain channel mask
#define GAIN_GENERIC_MASK   (GAIN_RGB|GAIN_GREEN1|GAIN_GREEN2|GAIN_GLOBAL)

//! \brief GAIN_AUTORANGE is a flag to keep the gain values passed in range\n
//! \brief of camera gain capabilities. The gain channels have to be valid settings.\n
//! \brief This gain channel flag is a API feature and not supported by camera firmware.
#define GAIN_AUTORANGE      0x4000

//! \brief GAIN_LOCKED is a flag to keep the current gain channel ratio. \n
//! \brief If you modify one of the RGB - channels the other will be adapted \n
//! \brief to keep the gain channel ratios constant. Based on the camera Gain\n
//! \brief capabilities a range check is performed and will be carefully attended \n
//! \brief without changing the gain channel ratio. Possibly your gain value won't \n
//! \brief be applied, so check the returned gain value.\n
//! \brief For example you have applied the white balance settings and want to \n
//! \brief increase the Gain and also want to keep your white balance. So you \n
//! \brief just modify one channel with the flag GAIN_LOCKED.\n
//! \brief This gain channel flag is a API feature and not supported by camera firmware.
#define GAIN_LOCKED          0x8000

//! mask of gain flags
#define GAIN_FLAG_MASK      (GAIN_AUTORANGE | GAIN_LOCKED)

//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Gain Units
//!@{

//! gain value without a unit (value 1000 means gain 1 )
#define GAINUNIT_NONE      0x0000
#define GAINUNIT_DB        0x0001  //!< gain values in DB

//! gain values are multiplied by 10 (value 10000 means gain 1 not 10)
#define GAINUNIT_10X      0x0002

//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: White Balance
//!  used by #FUNC_WHITE_BALANCE
//!@{
#define WB_MODE_INVALID     0x0000  //!< invalid mode
#define WB_MODE_ONE_PUSH    0x0001  //!< default mode single calculation
#define WB_MODE_MANUAL      0x0002  //!< set balance directly

//! automatic mode try to set optimal balance and adapt permanently
#define WB_MODE_AUTOMATIC   0x0004  
                                          
#define WB_OPT_INVALID      0x0000  //!< invalid option
#define WB_OPT_ROI_IMAGE    0x0001  //!< ROI is releative to current image settings (default)
#define WB_OPT_ROI_SENSOR   0x0002  //!< ROI is releative to full visible sensor size (bin,skip, resize are ignored)
#define WB_OPT_INDOOR       0x0100  //!< indoor artificial light (3200k)
#define WB_OPT_OUTDOOR      0x0200  //!< outdoor (5800k)
#define WB_OPT_SODIUM       0x0400  //!< sodium vapor lamp (590nm)

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: I/O Port Types
//!  used by #FUNC_IO_PORTS
//!@{

#define PORT_TYPE_UNCHANGED          0x0000    //!< leave port type unchanged
#define PORT_TYPE_OUTPUT            0x0001    //!< general output
#define PORT_TYPE_INPUT              0x0002    //!< general input
#define PORT_TYPE_TRIGGER_IN        0x0010    //!< trigger input
#define PORT_TYPE_STROBE_OUT        0x0020    //!< strobe output
#define PORT_TYPE_HSYNC_OUT          0x0040    //!< HSYNC (line) output
#define PORT_TYPE_VSYNC_OUT          0x0080    //!< VSYNC (frame) output
#define PORT_TYPE_PIXCLK_OUT        0x0100    //!< pixel clock output
#define PORT_TYPE_LINECAM_LINETRIG  0x0200    //!< line trigger in line camera mode
#define PORT_TYPE_EXP_STROBE_OUT    0x0400    //!< exposure strobe for extra strobe functionality
#define PORT_TYPE_MID_STROBE_OUT    0x0800    //!< mid strobe for extra strobe functionality
#define PORT_TYPE_MODE_SELECT_IN    0x1000    //!< mode select input (custom firmware)
#define PORT_TYPE_IMG_TOGGLE_OUT    0x2000    //!< image toggle output signal (custom firmware)
#define PORT_TYPE_TIMER_OUT         0x4000    //!< port type is timer / clock

//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: I/O Port Features
//!  used by #FUNC_IO_PORTS
//!@{
#define PORT_FEATURE_UNCHANGED        0x0000    //!< leave features unchanged

#define PORT_FEATURE_POL_ACTHIGH      0x0001    //!< polarity active high
#define PORT_FEATURE_POL_ACTLOW        0x0002    //!< polarity active low
#define PORT_FEATURE_POL_NORMAL        0x0004    //!< polarity normal
#define PORT_FEATURE_POL_INVERTED      0x0008    //!< polarity inverted
#define PORT_FEATURE_POL_MASK          0x000F    //!< mask for polarity features

#define PORT_FEATURE_TRIG_LEVEL        0x0010    //!< level-triggered
#define PORT_FEATURE_TRIG_EDGE        0x0020    //!< edge-triggered
#define PORT_FEATURE_TRIG_MASK        0x00F0    //!< mask for triggered features

#define PORT_FEATURE_DELAY            0x0100    //!< delay support
#define PORT_FEATURE_SINGLE_PERIOD    0x0200      //!< one period clock/timer
#define PORT_FEATURE_PWM              0x0400      //!< continuous clock/timer
#define PORT_FEATURE_COMMON_MASK      0x0700      //!< mask for common features

//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: I/O Port States
//!  used by #FUNC_IO_PORTS
//!@{
#define PORT_STATE_UNKNOWN      0x0000    //!< unknown state (get)
#define PORT_STATE_UNCHANGED    0x0000    //!< leave state unchanged (set)
#define PORT_STATE_SET          0x0001    //!< port state set
#define PORT_STATE_CLEARED      0x0002    //!< port state cleared
#define PORT_STATE_TRISTATE      0x0004    //!< port state tristate

//!@}


/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Mode Event Types and Options
//!  used by #FUNC_MODE_EVENT_CFG
//!@{

#define EVENT_TYPE_BRIGHTNESS     0x01    //!< brightness
#define EVENT_TYPE_MOTION          0x02    //!< motion

#define EVENT_OPTION_ABSOLUTE      0x01    //!< event trigger if absolute values reached
#define EVENT_OPTION_DELTA        0x02        //!< event trigger if a delta values reached

#define EVENT_EDGE_FALLING        0x01    //!< falling edge
#define EVENT_EDGE_RISING          0x02    //!< rising edge
#define EVENT_EDGE_BOTH           0x03    //!< both egdes

//!@}


////////////////////////////////////////////////////////////////////////////
//! \name Constants: Egde Enhancement Level
//!  used by FUNC_EDGE_ENHANCE
//!@{

#define EDGE_ENHANCE_DISABLE      0x00000000  //!< level disabled
#define EDGE_ENHANCE_WEAK          0x00000001  //!< level weak
#define EDGE_ENHANCE_STRONG        0x00000002  //!< level strong
#define EDGE_ENHANCE_EXTRA        0x00000003  //!< level extra strong

//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Memory Types
//!  used by #FUNC_MEMORY
//!@{

#define MEMORY_TYPE_EEPROM        0x0001    //!< EEPROM memory
#define MEMORY_TYPE_FLASH          0x0002    //!< FLASH memory
#define MEMORY_TYPE_RAM            0x0004    //!< RAM memory
#define MEMORY_TYPE_VOLATILE      0x0010    //!< memory is volatile
#define MEMORY_TYPE_NON_VOLATILE  0x0020    //!< memory is non-volatile
#define MEMORY_TYPE_READ          0x0100    //!< memory can be read
#define MEMORY_TYPE_WRITE          0x0200    //!< memory can be written
#define MEMORY_TYPE_CONFIG_DATA   0x8000    //!< memory belongs to config data

//!@}


/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Temperature sensors
//!  used by #FUNC_TEMPERATURE
//!@{

#define TEMP_SENS_TYPE_AD7415    0x0001    //!< Analog Devices AD7415 (I2C)
#define TEMP_SENS_TYPE_LM70      0x0002    //!< National LM70 (SPI)

#define TEMP_SENS_UNIT_C        0x0001    //!< degrees Celsius
#define TEMP_SENS_UNIT_2C        0x0002    //!< 1/2 degrees Celsius
#define TEMP_SENS_UNIT_4C        0x0004    //!< 1/4 degrees Celsius

//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Event Notifications
//!  used by #CamUSB_SetEventNotification, #CamUSB_GetEventNotification,
//!  #S_EVENT_NOTIFICATION
//!@{

#define EVENTID_START_OF_TRANSFER   0x05        //!< Start of image transfer (is nearly the same as EVENTID_END_OF_EXPOSURE)
#define EVENTID_END_OF_TRANSFER     0x06        //!< End of image transfer


#define NOTIFY_TYPE_EVENT           0x01        //!< notification type event (see alse CreateEvent)
#define NOTIFY_TYPE_MESSAGE         0x02        //!< notification type message (see alse SendMessage)


/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Image Flip Modes
//!  used by #FUNC_FLIP
//!@{

#define FLIP_NONE              0x0000    //!< no image flip
#define FLIP_HORIZONTAL        0x0001    //!< horizontal image flip
#define FLIP_VERTICAL          0x0002    //!< vertical image flip

//! vertical and horizontal image flip
#define FLIP_BOTH           (FLIP_HORIZONTAL|FLIP_VERTICAL)

//!@}

////////////////////////////////////////////////////////////////////////////
//! \name Constants: Black Level
//!  used by #FUNC_BLACKLEVEL
//!@{

#define BLC_LEVELVALUE_IGNORED      0x00      //!< blacklevel value is ignored
#define BLC_LEVELVALUE_VALID        0x01      //!< blacklevel value valid

//!@}


////////////////////////////////////////////////////////////////////////////
//! \name Constants: BadPixel Correction Options
//!  used by #FUNC_BADPIXEL_CORRECTION see #S_BADPIXEL_PARAMS::dwOption
//!@{

#define BPC_OPTION_SETDATA      0x80000000      //!< to write bad pixel data to camera memory
#define BPC_OPTION_GETDATA      0x40000000      //!< to read bad pixel data from camera memory
#define BPC_OPTION_STATE        0x00000001      //!< badpixel correction state will be changed (SET) see #S_BADPIXEL_PARAMS::dwState
#define BPC_OPTION_MODE         0x00000002      //!< badpixel correction mode see #BPC_MODE_NN
#define BPC_OPTION_MASK          0xC0000003      //!< mask all options

//!@}

////////////////////////////////////////////////////////////////////////////
//! \name Constants: BadPixel Correction Stats
//!  used by #FUNC_BADPIXEL_CORRECTION see #S_BADPIXEL_PARAMS::dwState
//!@{

#define BPC_STATE_DISABLED      0x00000000      //!< badpixel correction will be inactive
#define BPC_STATE_ENABLED        0x00000001      //!< badpixel correction will be performed for each image
#define BPC_STATE_INVCFG        0x80000000      //!< invalid badpixel correction data (Only for Get valid)
#define BPC_STATE_MASK          0x80000001      //!< mask all states

//!@}

////////////////////////////////////////////////////////////////////////////
//! \name Constants: BadPixel Correction Mode
//!  used by #FUNC_BADPIXEL_CORRECTION see #S_BADPIXEL_PARAMS::dwMode
//!@{

#define BPC_MODE_NN           0x00000000      //!< modus nearest neighbour
#define BPC_MODE_LINE_H        0x00000001      //!< modus linear horizontal
#define BPC_MODE_MASK          0x00000001      //!< mask all modes

//!@}

////////////////////////////////////////////////////////////////////////////
//! \name Constants: Cooling Modes
//!  used by #FUNC_COOLING see #S_COOLING_PARAMS::bCoolingMode
//!@{

#define CM_OFF        0x00      //!< cooling off
#define CM_AUTOMATIC  0x01      //!< cooling at automatic mode on 
#define CM_MANUAL      0x02      //!< cooling at manual mode on

//!@}


////////////////////////////////////////////////////////////////////////////
//! \name Constants: Shading Correction Actions
//!  used by #FUNC_SHADING_CORRECTION see #S_SHADING_CORRECTION_PARAMS::dwAction 
//!@{
#define SHCO_TYPE_STATE        0x00000001    //!< turn shading correction on / off
#define SHCO_TYPE_DARK_REF    0x00000002    //!< dark reference action
#define SHCO_TYPE_WHITE_REF    0x00000004    //!< white reference action

#define SHCO_FUNC_DISABLE      0x00000000    //!< disable
#define SHCO_FUNC_ENABLE      0x10000000    //!< enable

//! action function create reference image with a exposure of 
//! #S_SHADING_CORRECTION_PARAMS::dwExposure if #SHCO_FLAG_EXPOSURE is set
//! for Dark Reference the #SHCO_FLAG_EXPOSURE_DO may be set as well
#define SHCO_FUNC_CREATE      0x20000000    
#define SHCO_FUNC_GET          0x40000000    //!< action function get
#define SHCO_FUNC_SET          0x80000000    //!< action function set

#define SHCO_TYPE_MASK        0x000000FF    //!< action type mask
#define SHCO_FUNC_MASK        0xFF000000    //!< action function mask

#define SHCO_ACTION_DISABLE            (SHCO_TYPE_STATE     | SHCO_FUNC_DISABLE)  //!< turn shading correction off
#define SHCO_ACTION_ENABLE            (SHCO_TYPE_STATE     | SHCO_FUNC_ENABLE)  //!< turn shading correction on (if setup done)
#define SHCO_ACTION_CREATE_DARK_REF    (SHCO_TYPE_DARK_REF  | SHCO_FUNC_CREATE)  //!< create the dark reference data (capture an image)
#define SHCO_ACTION_GET_DARK_REF      (SHCO_TYPE_DARK_REF  | SHCO_FUNC_GET)    //!< get the dark reference data
#define SHCO_ACTION_SET_DARK_REF      (SHCO_TYPE_DARK_REF  | SHCO_FUNC_SET)    //!< set the dark reference data
#define SHCO_ACTION_CREATE_WHITE_REF  (SHCO_TYPE_WHITE_REF | SHCO_FUNC_CREATE)  //!< create the white reference data (capture an image)
#define SHCO_ACTION_GET_WHITE_REF      (SHCO_TYPE_WHITE_REF | SHCO_FUNC_GET)    //!< get the white reference data
#define SHCO_ACTION_SET_WHITE_REF      (SHCO_TYPE_WHITE_REF | SHCO_FUNC_SET)    //!< set the white reference data


//!@}

////////////////////////////////////////////////////////////////////////////
//! \name Constants: Shading Correction Flags
//!  used by #FUNC_SHADING_CORRECTION see #S_SHADING_CORRECTION_PARAMS::dwFlag
//!@{

#define SHCO_FLAG_NONE              0x00000000    //!< no flags
#define SHCO_FLAG_DATA_STRING        0x00000002    //!< interpret data buffer as zero terminated string (file path)
#define SHCO_FLAG_USE_WHITE_REF     0x00000004    //!< use white reference for correction
#define SHCO_FLAG_USE_DARK_REF      0x00000008    //!< use black reference for correction
#define SHCO_FLAG_EXPOSURE           0x00000010    //!< exposure value is valid #S_SHADING_CORRECTION_PARAMS::dwExposure
#define SHCO_FLAG_DATASIZE          0x00000020    //!< data size value is valid #S_SHADING_CORRECTION_PARAMS::dwDataSize
#define SHCO_FLAG_EXPOSURE_DO        0x00000030    //!< exposure (for dark offset) value is valid #S_SHADING_CORRECTION_PARAMS::dwExposureDO
#define SHCO_FLAG_DARK_REF_SET      0x00001000    //!< indicate that a dark reference is present  
#define SHCO_FLAG_WHITE_REF_SET      0x00002000    //!< indicate that a white reference is present
#define SHCO_FLAG_DONT_PACK          0x00010000    //!< indicate that the data should not be packed befor they are stored on disk/mem
#define SHCO_FLAG_IGNORE_STYPE       0x00020000    //!< indicate that sensortype checkings are ignored 

//!@}


/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Jpeg - Formats
//!@{

//! \brief 4:2:2 (source format) / 4:2:0 (destination format)
#define  JPEG_FORMAT1              0x00000096

//!@}

//! \cond DOXYGEN_INCLUDE_LINECAM
/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Line Camera Mode
//!  used by #FUNC_LINE_CAM
//!@{

#define  LINECAM_LINETRIG_FREERUN  0x0000    //!< line camera line trigger is free running
#define  LINECAM_LINETRIG_TRIG_HW  0x0001    //!< line camera line trigger is hardware triggered

//!@}
//! \endcond DOXYGEN_INCLUDE_LINECAM

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Trigger Options
//!  used by #FUNC_TRIGGER_OPTIONS
//!@{

#define  TRIG_OPTION_NONE      0x00000000  //!< normal trigger, no special options

//! \brief timed CCD clear
//! \brief in this mode, the user has to set the trigger cycle time, i.e.
//! \brief the time between two hardware trigger events.
//! \brief The CCD transfer sections are cleared in the available time and
//! \brief not as usual at the start of the triggered image.
//! \brief This allows for a very short delay of the exposure start after
//! \brief the trigger event even when using very short exposure times.
#define  TRIG_OPTION_TIMED_CCD_CLEAR  0x00000001

//! minimum trigger pulse width
//! in this mode, the user can set a minimum trigger pulse width
//! in microseconds. Shorter pulses are ignored.
#define TRIG_OPTION_MIN_PULSE_WIDTH  0x00000002

//! continuous CCD clear
//! In this mode, the sensor is cleared continuously in triggered
//! mode. No fast-clear is done prior to image exposure start.
#define TRIG_OPTION_CONT_CCD_CLEAR  0x00000004


//!@}



/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Skip Settings
//! used by #FUNC_RESOLUTION, #CamUSB_SetResolution, #CamUSB_GetResolution
//! and GetSkipBinValue
//!  Skip settings for internal use
//!@{

#define X_SKIP_MASK    0x0000FFFF
#define X_SKIP_SHIFT  0
#define X_SKIP_NONE    0x00000000    //!<  no x skip
#define X_SKIP_2X      0x00000001    //!<  2x X skip (use 2 pixel skip  2 pixel,..)
#define X_SKIP_3X      0x00000002    //!<  3x X skip (use 2 pixel skip  4 pixel,..)
#define X_SKIP_4X      0x00000004    //!<  4x X skip (use 2 pixel skip  6 pixel,..)
#define X_SKIP_5X      0x00000008    //!<  5x X skip (use 2 pixel skip  8 pixel,..)
#define X_SKIP_6X      0x00000010    //!<  6x X skip (use 2 pixel skip 10 pixel,..)
#define X_SKIP_7X      0x00000020    //!<  7x X skip (use 2 pixel skip 12 pixel,..)
#define X_SKIP_8X      0x00000040    //!<  8x X skip (use 2 pixel skip 14 pixel,..)
#define X_SKIP_9X      0x00000080    //!<  9x X skip (use 2 pixel skip 16 pixel,..)
#define X_SKIP_10X    0x00000100    //!< 10x X skip (use 2 pixel skip 16 pixel,..)
#define X_SKIP_11X    0x00000200    //!< 11x X skip (use 2 pixel skip 16 pixel,..)
#define X_SKIP_12X    0x00000400    //!< 12x X skip (use 2 pixel skip 16 pixel,..)
#define X_SKIP_13X    0x00000800    //!< 13x X skip (use 2 pixel skip 16 pixel,..)
#define X_SKIP_14X    0x00001000    //!< 14x X skip (use 2 pixel skip 16 pixel,..)
#define X_SKIP_15X    0x00002000    //!< 15x X skip (use 2 pixel skip 16 pixel,..)
#define X_SKIP_16X    0x00004000    //!< 16x X skip (use 2 pixel skip 16 pixel,..)

#define Y_SKIP_MASK    0xFFFF0000
#define Y_SKIP_SHIFT  16
#define Y_SKIP_NONE    0x00000000    //!< no y skip
#define Y_SKIP_2X      0x00010000    //!<  2x Y skip (use 2 pixel skip  2 pixel,..)
#define Y_SKIP_3X      0x00020000    //!<  3x Y skip (use 2 pixel skip  4 pixel,..)
#define Y_SKIP_4X      0x00040000    //!<  4x Y skip (use 2 pixel skip  6 pixel,..)
#define Y_SKIP_5X      0x00080000    //!<  5x Y skip (use 2 pixel skip  8 pixel,..)
#define Y_SKIP_6X      0x00100000    //!<  6x Y skip (use 2 pixel skip 10 pixel,..)
#define Y_SKIP_7X      0x00200000    //!<  7x Y skip (use 2 pixel skip 12 pixel,..)
#define Y_SKIP_8X      0x00400000    //!<  8x Y skip (use 2 pixel skip 14 pixel,..)
#define Y_SKIP_9X      0x00800000    //!<  9x Y skip (use 2 pixel skip 16 pixel,..)
#define Y_SKIP_10X    0x01000000    //!< 10x Y skip (use 2 pixel skip 16 pixel,..)
#define Y_SKIP_11X    0x02000000    //!< 11x Y skip (use 2 pixel skip 16 pixel,..)
#define Y_SKIP_12X    0x04000000    //!< 12x Y skip (use 2 pixel skip 16 pixel,..)
#define Y_SKIP_13X    0x08000000    //!< 13x Y skip (use 2 pixel skip 16 pixel,..)
#define Y_SKIP_14X    0x10000000    //!< 14x Y skip (use 2 pixel skip 16 pixel,..)
#define Y_SKIP_15X    0x20000000    //!< 15x Y skip (use 2 pixel skip 16 pixel,..)
#define Y_SKIP_16X    0x40000000    //!< 16x Y skip (use 2 pixel skip 16 pixel,..)


#define XY_SKIP_NONE  0x00000000    //!< no xy skip
#define XY_SKIP_2X    0x00010001    //!<  2x XY skip (use 2 pixel skip  2 pixel,..)
#define XY_SKIP_3X    0x00020002    //!<  3x XY skip (use 2 pixel skip  4 pixel,..)
#define XY_SKIP_4X    0x00040004    //!<  4x XY skip (use 2 pixel skip  6 pixel,..)
#define XY_SKIP_5X    0x00080008    //!<  5x XY skip (use 2 pixel skip  8 pixel,..)
#define XY_SKIP_6X    0x00100010    //!<  6x XY skip (use 2 pixel skip 10 pixel,..)
#define XY_SKIP_7X    0x00200020    //!<  7x XY skip (use 2 pixel skip 12 pixel,..)
#define XY_SKIP_8X    0x00400040    //!<  8x XY skip (use 2 pixel skip 14 pixel,..)
#define XY_SKIP_9X    0x00800080    //!<  9x XY skip (use 2 pixel skip 16 pixel,..)
#define XY_SKIP_10X    0x01000100    //!< 10x XY skip (use 2 pixel skip 16 pixel,..)
#define XY_SKIP_11X    0x02000200    //!< 11x XY skip (use 2 pixel skip 16 pixel,..)
#define XY_SKIP_12X    0x04000400    //!< 12x XY skip (use 2 pixel skip 16 pixel,..)
#define XY_SKIP_13X    0x08000800    //!< 13x XY skip (use 2 pixel skip 16 pixel,..)
#define XY_SKIP_14X    0x10001000    //!< 14x XY skip (use 2 pixel skip 16 pixel,..)
#define XY_SKIP_15X    0x20002000    //!< 15x XY skip (use 2 pixel skip 16 pixel,..)
#define XY_SKIP_16X    0x40004000    //!< 16x XY skip (use 2 pixel skip 16 pixel,..)


//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Bin Settings
//! used by #FUNC_RESOLUTION, #CamUSB_SetResolution, #CamUSB_GetResolution
//! and GetSkipBinValue
//!  Bin settings for internal use
//!@{

#define X_BIN_MASK    0x0000FFFF
#define X_BIN_SHIFT    0
#define X_BIN_NONE    0x00000000    //!<  no X bin
#define X_BIN_2X      0x00000001    //!<  2x X bin
#define X_BIN_3X      0x00000002    //!<  3x X bin
#define X_BIN_4X      0x00000004    //!<  4x X bin
#define X_BIN_5X      0x00000008    //!<  5x X bin
#define X_BIN_6X      0x00000010    //!<  6x X bin
#define X_BIN_7X      0x00000020    //!<  7x X bin
#define X_BIN_8X      0x00000040    //!<  8x X bin
#define X_BIN_9X      0x00000080    //!<  9x X bin
#define X_BIN_10X      0x00000100    //!< 10x X bin (use 2 pixel bin 16 pixel,..)
#define X_BIN_11X      0x00000200    //!< 11x X bin (use 2 pixel bin 16 pixel,..)
#define X_BIN_12X      0x00000400    //!< 12x X bin (use 2 pixel bin 16 pixel,..)
#define X_BIN_13X      0x00000800    //!< 13x X bin (use 2 pixel bin 16 pixel,..)
#define X_BIN_14X      0x00001000    //!< 14x X bin (use 2 pixel bin 16 pixel,..)
#define X_BIN_15X      0x00002000    //!< 15x X bin (use 2 pixel bin 16 pixel,..)
#define X_BIN_16X      0x00004000    //!< 16x X bin (use 2 pixel bin 16 pixel,..)

#define Y_BIN_MASK    0xFFFF0000
#define Y_BIN_SHIFT    16
#define Y_BIN_NONE    0x00000000    //!<  no Y bin
#define Y_BIN_2X      0x00010000    //!<  2x Y bin
#define Y_BIN_3X      0x00020000    //!<  3x Y bin
#define Y_BIN_4X      0x00040000    //!<  4x Y bin
#define Y_BIN_5X      0x00080000    //!<  5x Y bin
#define Y_BIN_6X      0x00100000    //!<  6x Y bin
#define Y_BIN_7X      0x00200000    //!<  7x Y bin
#define Y_BIN_8X      0x00400000    //!<  8x Y bin
#define Y_BIN_9X      0x00800000    //!<  9x Y bin
#define Y_BIN_10X      0x01000000    //!< 10x Y bin (use 2 pixel bin 16 pixel,..)
#define Y_BIN_11X      0x02000000    //!< 11x Y bin (use 2 pixel bin 16 pixel,..)
#define Y_BIN_12X      0x04000000    //!< 12x Y bin (use 2 pixel bin 16 pixel,..)
#define Y_BIN_13X      0x08000000    //!< 13x Y bin (use 2 pixel bin 16 pixel,..)
#define Y_BIN_14X      0x10000000    //!< 14x Y bin (use 2 pixel bin 16 pixel,..)
#define Y_BIN_15X      0x20000000    //!< 15x Y bin (use 2 pixel bin 16 pixel,..)
#define Y_BIN_16X      0x40000000    //!< 16x Y bin (use 2 pixel bin 16 pixel,..)

#define XY_BIN_NONE    0x00000000    //!<  no XY bin
#define XY_BIN_2X      0x00010001    //!<  2x XY bin
#define XY_BIN_3X      0x00020002    //!<  3x XY bin
#define XY_BIN_4X      0x00040004    //!<  4x XY bin
#define XY_BIN_5X      0x00080008    //!<  5x XY bin
#define XY_BIN_6X      0x00100010    //!<  6x XY bin
#define XY_BIN_7X      0x00200020    //!<  7x XY bin
#define XY_BIN_8X      0x00400040    //!<  8x XY bin
#define XY_BIN_9X      0x00800080    //!<  9x XY bin
#define XY_BIN_10X    0x01000100    //!< 10x XY bin (use 2 pixel bin 16 pixel,..)
#define XY_BIN_11X    0x02000200    //!< 11x XY bin (use 2 pixel bin 16 pixel,..)
#define XY_BIN_12X    0x04000400    //!< 12x XY bin (use 2 pixel bin 16 pixel,..)
#define XY_BIN_13X    0x08000800    //!< 13x XY bin (use 2 pixel bin 16 pixel,..)
#define XY_BIN_14X    0x10001000    //!< 14x XY bin (use 2 pixel bin 16 pixel,..)
#define XY_BIN_15X    0x20002000    //!< 15x XY bin (use 2 pixel bin 16 pixel,..)
#define XY_BIN_16X    0x40004000    //!< 16x XY bin (use 2 pixel bin 16 pixel,..) 

//!@}


/////////////////////////////////////////////////////////////////////////////
//! \name Constants: resize settings
//! used by #FUNC_RESOLUTION, #S_RESOLUTION_PARAMS::wResize, 
//! #S_RESOLUTION_CAPS::wResize
//!  resize settings
//!@{
#define X_RESIZE_MASK    0x0087      //!< resize x mask all
#define X_RESIZE_FACTOR  0x0007      //!< resize x factor mask
#define X_RESIZE_SHIFT  0           //!<
#define X_RESIZE_NONE    0x0000    //!< no X resize
#define X_RESIZE_2X      0x0001    //!< X resize by factor 2
#define X_RESIZE_3X      0x0002    //!< X resize by factor 3
#define X_RESIZE_4X      0x0004    //!< X resize by factor 4
#define X_RESIZE_INV    0x0080    //!< inverse resize X

#define Y_RESIZE_MASK    0x8700      //!< resize y mask all
#define Y_RESIZE_FACTOR  0x0700      //!< resize y factor mask
#define Y_RESIZE_SHIFT  8           //
#define Y_RESIZE_NONE    0x0000    //!< no Y resize
#define Y_RESIZE_2X      0x0100    //!< Y resize by factor 2
#define Y_RESIZE_3X      0x0200    //!< X resize by factor 3
#define Y_RESIZE_4X      0x0400    //!< Y resize by factor 4
#define Y_RESIZE_INV    0x8000    //!< inverse resize Y

#define XY_RESIZE_NONE  (X_RESIZE_NONE | Y_RESIZE_NONE) //!< no X nor Y resize
#define XY_RESIZE_2X    (X_RESIZE_2X   | Y_RESIZE_2X)   //!< X and Y resize by factor 2
#define XY_RESIZE_3X    (X_RESIZE_3X   | Y_RESIZE_3X)   //!< X and Y resize by factor 3
#define XY_RESIZE_4X    (X_RESIZE_4X   | Y_RESIZE_4X)   //!< X and Y resize by factor 4
#define XY_RESIZE_INV    (X_RESIZE_INV  | Y_RESIZE_INV)  //!< X and Y inverse resize

//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Timestamp overlay Settings
//! used by #FUNC_TIMESTAMP_OVERLAY
//!  time stamp overlay settings
//!@{

//! automatic overlay size settings
#define OVERLAY_FACTOR_AUTO      0x00000000
#define OVERLAY_FACTOR_X_1      0x00000010  //!< default x-size
#define OVERLAY_FACTOR_X_2      0x00000020  //!< 2 * default x-size
#define OVERLAY_FACTOR_X_3      0x00000030  //!< 3 * default x-size
#define OVERLAY_FACTOR_X_4      0x00000040  //!< 4 * default x-size
#define OVERLAY_FACTOR_X_5      0x00000050  //!< 5 * default x-size
#define OVERLAY_FACTOR_X_6      0x00000060  //!< 6 * default x-size
#define OVERLAY_FACTOR_X_7      0x00000070  //!< 7 * default x-size
#define OVERLAY_FACTOR_X_8      0x00000080  //!< 8 * default x-size
#define OVERLAY_FACTOR_Y_1      0x00000100  //!< default y-size
#define OVERLAY_FACTOR_Y_2      0x00000200  //!< 2 * default y-size
#define OVERLAY_FACTOR_Y_3      0x00000300  //!< 3 * default y-size
#define OVERLAY_FACTOR_Y_4      0x00000400  //!< 4 * default y-size
#define OVERLAY_FACTOR_Y_5      0x00000500  //!< 5 * default y-size
#define OVERLAY_FACTOR_Y_6      0x00000600  //!< 6 * default y-size
#define OVERLAY_FACTOR_Y_7      0x00000700  //!< 7 * default y-size
#define OVERLAY_FACTOR_Y_8      0x00000800  //!< 8 * default y-size

#define OVERLAY_FACTOR_X_MASK   0x000000F0  //!< x size factor mask
#define OVERLAY_FACTOR_X_SHIFT           4  //!< x shift value
#define OVERLAY_FACTOR_Y_MASK   0x00000F00  //!< y size factor mask
#define OVERLAY_FACTOR_Y_SHIFT           8  //!< y shift value

//! \brief xor the time stamp in the image see #S_TIMESTAMP_OVERLAY_PARAMS
#define OVERLAY_XOR              0x80000000
//! \brief write a black background with white or blue letters in the image
#define OVERLAY_DEFAULT          0x00000000

//! \brief overlay not flipped
#define OVERLAY_FLIP_N      FLIP_NONE
//! \brief overlay horizontal flipped
#define OVERLAY_FLIP_H      FLIP_HORIZONTAL
//! \brief overlay vertical flipped
#define OVERLAY_FLIP_V      FLIP_VERTICAL
//! \brief overlay horizontal and vertical flipped
#define OVERLAY_FLIP_HV      (OVERLAY_FLIP_H|OVERLAY_FLIP_V)

//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Camera Sleep Modes
//! used by #CamUSB_GetSleepMode, #CamUSB_SetSleepMode and #CamUSB_GetSleepModeCaps
//!@{
#define SLEEPMODE_NONE          0x00000000      //!< no sleep mode active
#define SLEEPMODE_SENSOR        0x00000001      //!< sleep mode: sensor module shutdown active

//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Time Synchronization Modes
//! used by #FUNC_TIMESYNC
//!@{

//! set camera time to the time value passed by function
#define  TIMESYNC_NONE           0x0000

//! \brief set camera time to the time value passed by function
//! \brief plus the time passed since last trigger event
#define  TIMESYNC_POST_TRIG      0x0001

//! \brief set camera time to the time value passed by function
//! \brief at next trigger event
#define  TIMESYNC_PRE_TRIG       0x0002

//!@}


/////////////////////////////////////////////////////////////////////////////
//! \name Constants: image color mapping
//! used by #FUNC_COLOR_MAPPING
//!@{

#define COMA_STATE_DISABLED     (0x0000)     //!< disable color / bit mapping
#define COMA_STATE_ENABLED      (0x0001)     //!< enable color / bit mapping
#define COMA_STATE_MASK         (0x0001)     //!< state mask 

#define COMA_MODE_COLORMAPPING  (0x0002)     //!< color mapping modus
#define COMA_MODE_BITMAPPING    (0x0004)     //!< bit mapping modus
#define COMA_MODE_MASK          (0x0006)     //!< mask the color mapping mode

#define COMA_MAPPING_08         (0x0000)     //!< 8/16Bit source data will be mapped to 3 *  8Bit BGR
#define COMA_MAPPING_16         (0x0010)     //!< 8/16Bit source data will be mapped to 3 * 16Bit BGR
#define COMA_MAPPING_08_RGB     (0x0020)     //!< 8/16Bit source data will be mapped to 3 *  8Bit RGB
#define COMA_MAPPING_16_RGB     (0x0030)     //!< 8/16Bit source data will be mapped to 3 * 16Bit RGB
#define COMA_MAPPING_MASK       (0x0030)     //!< mask the source destination mapping

//! If the flag data is set, the data passed by a #CamUSB_SetFunction call 
//! (#S_COLOR_MAPPING_DATA_CM, #S_COLOR_MAPPING_DATA_BM) is used to setup the
//! color mapping LUT. If the flag is set during a #CamUSB_GetFunction call, 
//! the current mapping data is requested to be returned within the data pointer.
#define COMA_FLAG_DATA          (0x8000)     
#define COMA_FLAG_MASK          (0x8000)    //!< mask the supported flags

//!@}


/////////////////////////////////////////////////////////////////////////////
//! \name Constants: auto exposure / brightness control
//! used by #FUNC_AUTOEXPOSURE
//!@{

//! S_AUTOEXPOSURE_CAPS2::iMinBrightnessOffset and S_AUTOEXPOSURE_CAPS2::iMaxBrightnessOffset 
//! are valid values and have
#define AEXP_OPTION_BRIGHNESSOFFSET   0x0001   

//! internal algorithm is used to control the brightness,
//! which requires #AEXP_FEATURE_FULL to work
#define AEXP_OPTION_INTERN_ALGORITHM  0x0002   

//! reduce flicker based on light sources operated at 50 Hz
#define AEXP_OPTION_ANTIFLICKER_50HZ  0x0010   

//! reduce flicker based on light sources operated at 60 Hz
#define AEXP_OPTION_ANTIFLICKER_60HZ  0x0020


#define AEXP_FEATURE_GAIN             0x00000001   //!< auto gain control supported
#define AEXP_FEATURE_EXPOSURE         0x00000002   //!< auto exposure control supported
#define AEXP_FEATURE_IRIS             0x00000004   //!< auto iris control supported
#define AEXP_FEATURE_GAIN_LIMIT       0x00000010   //!< a gain limit is supported S_AUTOEXPOSURE_PARAMS::dwMaxGain (S_AUTOEXPOSURE_PARAMS::dwMinGain will be ignored)      
#define AEXP_FEATURE_GAIN_RANGE       0x00000020   //!< a gain range is supported S_AUTOEXPOSURE_PARAMS::dwMinGain till S_AUTOEXPOSURE_PARAMS::dwMaxGain
#define AEXP_FEATURE_EXPOSURE_RANGE   0x00000040   //!< a gain range is supported S_AUTOEXPOSURE_PARAMS::dwMinExposure till S_AUTOEXPOSURE_PARAMS::dwMaxExposure

#define AEXP_FEATURE_FULL             0x00010000   //!< auto gain / exposure / iris control possible         
#define AEXP_FEATURE_GAIN_EXPOSURE    0x00020000   //!< auto gain / exposure control possible
#define AEXP_FEATURE_GAIN_IRIS        0x00040000   //!< auto gain / iris control possible

#define AEXP_FEATURE_SUPPORTED_MASK   0x0000FFFF   //!< mask of supported features
#define AEXP_FEATURE_COMBINATION_MASK 0x00FF0000   //!< mask of combineable features

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: rotate / remap
//! used by #FUNC_ROTATE_REMAP
//!@{
#define ROTREM_MODE_OFF             0x00 
#define ROTREM_MODE_ROTATE_CENTER     0x01          //!< rotate the image around point 0,0 (top left corner)
#define ROTREM_MODE_ROTATE_XY       0x02          //!< rotate the image around point X,Y 
#define ROTREM_MODE_REMAP           0x04          //!< apply a image remapping (e.g. upward shift for distortion)

#define ROTREM_FLAG_NONE            0x00000000    //!< no flags
#define ROTREM_FLAG_DATASIZE        0x00000001    //!< data size value is valid #S_ROTATE_REMAP_PARAMS::dwDataSize
#define ROTREM_FLAG_DATA_STRING      0x00000002    //!< interpret data buffer as zero terminated string (file path)

//! destination images uses the optimal dimension to fit the rotated image, if this flag is not set the image 
//! dimensions will be maintained
#define ROTREM_FLAG_OPT_IMG_SIZE    0x00000004    

//! dwSizeX/Y are valid, a ROI will be selected from the rotated image
//! \remark if flag #ROTREM_FLAG_OPT_IMG_SIZE is set, #ROTREM_FLAG_ROI will be ignored
#define ROTREM_FLAG_ROI             0x00000008    

#define ROTREM_FLAG_SMOOTH_EDGE     0x00000010    //!< interpolation uses smooth edge option
#define ROTREM_FLAG_REMAP_DATA_SET  0x80000000    //!< indicate that the remap data are present  

#define ROTREM_INTER_NN             0x00000001    //!< interpolation nearest neighbour  
#define ROTREM_INTER_LINEAR         0x00000002    //!< interpolation linear
#define ROTREM_INTER_CUBIC          0x00000004    //!< interpolation cubic

//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Image Header PayLoad-Types
//!@{

#define  PAYLOAD_DEFAULT                  0x0000  //!< default payload which is not compressed
#define  PAYLOAD_HUFFMAN                  0x0001  //!< payload which used huffman coding
#define  PAYLOAD_PREDICTION              0x0002  //!< payload which used ABS prediction
#define  PAYLOAD_HUFF_PREDICTION          0x0003  //!< payload which used huffman coding and ABS prediction
#define  PAYLOAD_HUFF_PREDICTION_MUL      0x0013  //!< payload which used huffman coding and ABS prediction and multipexed source

//                       _  // current field
//                      _   // number of fields  
#define  PAYLOAD_EXT_TYPE_MASK           0xF000  //!< type mask for extensions
#define  PAYLOAD_EXT_TYPE_MULT_FIELD_IMG 0x8000  //!< payload data based on a multi field image

#define  PE_MFI_CURRENT_FIELD_MASK      0x000F  //!< current image field mask for payload type multi field image
#define  PE_MFI_TOTAL_FIELD_MASK        0x00F0  //!< mask the total number of field for payload type multi field image
#define  PE_MFI_GET_TOTAL_FIELDS( payload_ext) (((payload_ext & PAYLOAD_EXT_TYPE_MASK)==PAYLOAD_EXT_TYPE_MULT_FIELD_IMG) ? ((payload_ext & PE_MFI_TOTAL_FIELD_MASK)>>4) : 0)
#define  PE_MFI_GET_CURRENT_FIELD(payload_ext) (((payload_ext & PAYLOAD_EXT_TYPE_MASK)==PAYLOAD_EXT_TYPE_MULT_FIELD_IMG) ?  (payload_ext & PE_MFI_CURRENT_FIELD_MASK)   : 0)

#define PAYLOAD_EXT_TYPE_APPENDED_DATA  0x4000  //!< data appended flag
#define EXTENSION_TYPE_HISTOGRAMM       0x0002  //!< histogram 256*4byte entries appended
#define EXTENSION_TYPE_HIST_XYPERCENT   0x0004  //!< histogram xy percent value added (4Byte)
#define EXTENSION_TYPE_HIST_OVERFLW     0x0008  //!< histogram count pixel equal above overflow level added (4Byte)
#define EXTENSION_TYPE_HIST_UNDERFLW    0x0010  //!< histogram count pixel equal above underflow level added (4Byte)

//!@}

//! \cond DOXYGEN_INCLUDE_VDL
/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Video Data Logger (VDL)
//! \brief S_VDL_STATUS_RETVALS::wVDLState  Bit definitions
//!@{
#define VDL_STATE_INACTIVE             0x0000       //!< VDL isn't active
#define VDL_STATE_ACTIVE               0x0001       //!< VDL is active
#define VDL_STATE_WAIT_FOR_EVENT       0x0002       //!< camera wait for Trigger-Event
#define VDL_STATE_PRE_TRIGGER_PHASE    0x0004       //!< camera at pre trigger phase
#define VDL_STATE_POST_TRIGGER_PHASE   0x0008       //!< camera at post trigger phase
#define VDL_STATE_WR_TO_SDCARD         0x0010       //!< writing to SDCard
#define VDL_STATE_MEDIUM_FULL          0x0080       //!< SDCard / Memory is full

//!@}


/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Video Data Logger (VDL)
//! \brief S_VDL_STATUS_RETVALS::dwSDCardState Bit definitions
//!@{
#define VDL_SDCARD_DETECT            0x00000001  //!<  SD card is detected
#define VDL_SDCARD_FILESYSTEM_INIT  0x00000002  //!<  SD card FAT File system init successfull
#define VDL_SDCARD_INIFILE_DETECT    0x00000004  //!<  Ini-File was found on SD card
#define VDL_SDCARD_INI_EXECUTED      0x00000008  //!<  Ini-File read and config set at camera
#define VDL_SDCARD_WRITEPROTECT      0x00000080  //!<  SD card is write protect (locked)

#define VDL_SDCARD_STATUS_MASK       0x000000FF  //!<  SD card status MASK

#define VDL_SDCARD_FS_UNKNOWN        0x00000000  //!<  SD card File System (FS): unknown
#define VDL_SDCARD_FS_FAT16         0x00010000  //!<  SD card File System: FAT16
#define VDL_SDCARD_FS_FAT32         0x00020000  //!<  SD card File System: FAT32

#define VDL_SDCARD_FS_MASK           0x000F0000  //!<  SD card File System Mask

//!@}


/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Video Data Logger (VDL)
//! \brief S_VDL_SETTINGS_PARAMS::bCaptureTo and
//! \brief S_VDL_SETTINGS_RETVALS::bCaptureTo definitions
//!@{
#define VDL_CAPTURE_TO_MEMORY         0x00        //!< capture to intern SDRAM
#define VDL_CAPTURE_TO_SDCARD         0x01        //!< capture to SD-Card

//! flag: to save images at recoding time to storage without buffering
#define VDL_CAPTURE_TO_SYNCHRONOUS    0x40

//! flag: to save images (only jpeg) in a AVI-File instead of single images
#define VDL_CAPTURE_TO_AVI             0x80


//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Video Data Logger (VDL)
//! \brief S_VDL_DIR_PARAMS::wAction and S_VDL_FILE_PARAMS::wAction
//!@{

//! \brief if "use memory" is set FUNC_VDL_DIR and FUNC_VDL_FILE operation
//! \brief will be performed on the camera internal memory
#define VDL_USE_MEMORY          0x0100
//! \brief if "use SD-CARD" is set FUNC_VDL_DIR and FUNC_VDL_FILE operation
//! \brief will be performed on a plugged in SD-Card
#define VDL_USE_SDCARD          0x0200      //!< get next directory entry

#define VDL_USE_MASK          (VDL_USE_SDCARD|VDL_USE_MEMORY)

#define VDL_DIR_GETFIRST        0x0001      //!< get first directory entry musst be used befor VDL_DIR_GETNEXT
#define VDL_DIR_GETNEXT         0x0002      //!< get next directory entry
#define VDL_DIR_DELETE          0x8000      //!< delete a directory (ignored by #CamUSB_GetFunction)

#define VDL_FILE_READ           0x0001      //!< read the specified file (ignored by #CamUSB_SetFunction)
#define VDL_FILE_WRITE          0x0002      //!< write the specified file (ignored by #CamUSB_GetFunction)
#define VDL_FILE_DELETE         0x8000      //!< delete  the specified file (ignored by #CamUSB_GetFunction)

//! \brief maximum allowed buffer size for a single VDL_FILE_READ operation, to
//! \brief transfer langer files, the transfer musst be split up to multiple
//! \brief packets by using the S_VDL_FILE_PARAMS::dwFileOffset
#define VDL_FILE_MAX_BUFSIZE    0x00010000

//!@}
//! \endcond DOXYGEN_INCLUDE_VDL

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Real Time Clock (RTC)
//! \brief Day of Week - Enum for #S_RTC_PARAMS::wDayOfWeek
//! \brief and #S_RTC_RETVALS::wDayOfWeek
//!@{
#define RTC_WD_SUNDAY           0x01        //!< Sunday
#define RTC_WD_MONDAY           0x02        //!< Monday
#define RTC_WD_TUESDAY          0x03        //!< Thuesday
#define RTC_WD_WEDNESDAY        0x04        //!< Wednesday
#define RTC_WD_THURSDAY         0x05        //!< Thuesday
#define RTC_WD_FRIDAY           0x06        //!< Friday
#define RTC_WD_SATURDAY         0x07        //!< Saturday

//!@}


//! \cond DON_T_DOCUMENT
/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Preset Strings
//!@{
// misc
#define STR_DeviceName                  "DeviceName"
#define STR_SensorID                    "SensorID"
#define STR_SerialNo                    "SerialNo"
#define STR_Name                        "Name"
#define STR_ShortName                   "ShortName"
#define STR_FunctionIDs                 "FunctionIDs"
#define STR_Empty                       "Empty"
#define STR_CaptureMode                 "CaptureMode"
#define STR_CountImages                 "CountImages"
// resolution
#define STR_Res_KeepExposure            "Res_KeepExposure"
#define STR_Res_Bin                      "Res_Bin"
#define STR_Res_Skip                    "Res_Skip"
#define STR_Res_OffsetX                  "Res_OffsetX"
#define STR_Res_OffsetY                  "Res_OffsetY"
#define STR_Res_SizeX                    "Res_SizeX"
#define STR_Res_SizeY                    "Res_SizeY"
#define STR_Res_Resize                  "Res_Resize"
// framerate
#define STR_FPS_Framerate                "FPS_Framerate"
#define STR_FPS_MaxFramerate            "FPS_MaxFramerate"
// pixeltype
#define STR_Pix_Pixeltype                "Pix_Pixeltype"
//flip
#define STR_Fli_Flip                    "Fli_Flip"
//exposure
#define STR_Exp_Exposure                "Exp_Exposure"

// auto exposure
#define STR_AEG_AECActive                "AEG_AECActive"
#define STR_AEG_AEGActive                "AEG_AEGActive"
#define STR_AEG_AICActive               "AEG_AICActive"
#define STR_AEG_BrightnessPercentile    "AEG_BrightnessPercentile"
#define STR_AEG_FrameSkip                "AEG_FrameSkip"
#define STR_AEG_TargetBrightness        "AEG_TargetBrightness"
#define STR_AEG_ROI_Skip                "AEG_ROI_Skip"
#define STR_AEG_ROI_OffsetX              "AEG_ROI_OffsetX"
#define STR_AEG_ROI_OffsetY              "AEG_ROI_OffsetY"
#define STR_AEG_ROI_SizeX                "AEG_ROI_SizeX"
#define STR_AEG_ROI_SizeY                "AEG_ROI_SizeY"
#define STR_AEG_Options                 "AEG_Options"
#define STR_AEG_MinGain                 "AEG_MinGain"
#define STR_AEG_MaxGain                 "AEG_MaxGain"
#define STR_AEG_MinExposure             "AEG_MinExposure"
#define STR_AEG_MaxExposure             "AEG_MaxExposure"
#define STR_AEG_TCT                     "AEG_TCT"
#define STR_AEG_H                       "AEG_H"
#define STR_AEG_S                       "AEG_S"

// gain
#define STR_Gai_GainChannel              "Gai_GainChannel"
#define STR_Gai_GainChannel0            "Gai_GainChannel0"
#define STR_Gai_GainChannel1            "Gai_GainChannel1"
#define STR_Gai_GainChannel2            "Gai_GainChannel2"
#define STR_Gai_GainChannel3            "Gai_GainChannel3"
#define STR_Gai_GainChannel4            "Gai_GainChannel4"
//gamma
#define STR_Gam_Gamma                    "Gam_Gamma"
// Brightness Contrast
#define STR_BrC_Brightness              "BrC_Brightness"
#define STR_BrC_Contrast                "BrC_Contrast"
// Hue Saturation
#define STR_HuS_Hue                      "HuS_Hue"
#define STR_HuS_Saturation              "HuS_Saturation"
// BitShift
#define STR_BSh_BitShift                "BSh_BitShift"
// Color Correction
#define STR_CoC_Active                  "CoC_Active"
#define STR_CoC_SetMatrix                "CoC_SetMatrix"
#define STR_CoC_CCMatrix0                "CoC_CCMatrix0"
#define STR_CoC_CCMatrix1                "CoC_CCMatrix1"
#define STR_CoC_CCMatrix2                "CoC_CCMatrix2"
#define STR_CoC_CCMatrix3                "CoC_CCMatrix3"
#define STR_CoC_CCMatrix4                "CoC_CCMatrix4"
#define STR_CoC_CCMatrix5                "CoC_CCMatrix5"
#define STR_CoC_CCMatrix6                "CoC_CCMatrix6"
#define STR_CoC_CCMatrix7                "CoC_CCMatrix7"
#define STR_CoC_CCMatrix8                "CoC_CCMatrix8"
// LUT
#define STR_LUT_LUTIndex                "LUT_LUTIndex"
#define STR_LUT_LUTEntries              "LUT_LUTEntries"
#define STR_LUT_LUTData                 "LUT_LUTData"
// LUT Post
#define STR_LUT2_LUTIndex                "LUT2_LUTIndex"
#define STR_LUT2_LUTEntries             "LUT2_LUTEntries"
#define STR_LUT2_LUTData                "LUT2_LUTData"
// Timestamp Overlay
#define STR_TSO_TSOActive               "TSO_TSOActive"
#define STR_TSO_LUTFlag                 "TSO_LUTFlag"
// Jpeg
#define STR_JPG_JpegQuality             "JPG_JpegQuality"
#define STR_JPG_JpegFormat              "JPG_JpegFormat"
// Black level compensation
#define STR_BLL_BLLActive               "BLL_BLLActive"
#define STR_BLL_BLLFlags                "BLL_BLLFlags"
#define STR_BLL_BLLValue                "BLL_BLLValue"

// SensorClock
#define STR_CLK_SensorClk               "CLK_SensorClk"
// Line Camera
#define STR_LCA_LineCamEnable           "LCA_LineCamEnable"
#define STR_LCA_LineTriggerMode         "LCA_LineTriggerMode"
#define STR_LCA_ScanLines               "LCA_ScanLines"
#define STR_LCA_OffsetX                 "LCA_OffsetX"
#define STR_LCA_OffsetY                 "LCA_OffsetY"
#define STR_LCA_SizeX                   "LCA_SizeX"
#define STR_LCA_SizeY                   "LCA_SizeY"
// IO-Ports
#define STR_IOP_Mask                    "IOP_Mask"
#define STR_IOP_02d_PortType            "IOP_%02d_PortType"
#define STR_IOP_02d_PortFeatures        "IOP_%02d_PortFeatures"
#define STR_IOP_02d_PortState           "IOP_%02d_PortState"
#define STR_IOP_02d_Delay               "IOP_%02d_Delay"

// Video Data Logger (VDL)
// Settings
#define STR_VDL_State                   "VDL_State"
#define STR_VDL_CaptureTo               "VDL_CaptureTo"
#define STR_VDL_PreTriggerTime          "VDL_PreTriggerTime"
#define STR_VDL_PostTriggerTime          "VDL_PostTriggerTime"
#define STR_VDL_MetaInfo                "VDL_MetaInfo"
#define STR_VDL_MetaInfoCnt             "VDL_MetaInfoCnt"
// Meta Info
#define STR_VDL_Meta_Cnt                "VDL_Meta_Cnt"
#define STR_VDL_Meta_Descr              "VDL_Meta_Descr"
#define STR_VDL_Meta_Comm                "VDL_Meta_Comm"
#define STR_VDL_Meta_Auth                "VDL_Meta_Auth"
#define STR_VDL_Meta_Copyr              "VDL_Meta_Copyr"

// Edge Enhancement
#define STR_EDG_EnhanceLevel            "EDG_EnhanceLevel"

// MultiShot Configuration
#define STR_MSC_Records                 "MSC_Records"
#define STR_MSC_RecordSize              "MSC_RecordSize"
#define STR_MSC_02d_FuncMask            "MSC_%02d_FuncMask"
#define STR_MSC_02d_Exposure            "MSC_%02d_Exposure"
#define STR_MSC_02d_GainChannel         "MSC_%02d_GainChannel"
#define STR_MSC_02d_GainChannel0        "MSC_%02d_GainChannel0"
#define STR_MSC_02d_GainChannel1        "MSC_%02d_GainChannel1"
#define STR_MSC_02d_GainChannel2        "MSC_%02d_GainChannel2"
#define STR_MSC_02d_GainChannel3        "MSC_%02d_GainChannel3"
#define STR_MSC_02d_GainChannel4        "MSC_%02d_GainChannel4"


// Multi-ROI Configuration
#define STR_MRC_Bin                     "MRC_Bin"
#define STR_MRC_Skip                    "MRC_Skip"
#define STR_MRC_KeepExposure            "MRC_KeepExposure"
#define STR_MRC_UsedROIs                 "MRC_UsedROIs"
#define STR_MRC_02d_OffsetX             "MRC_%02d_OffsetX"
#define STR_MRC_02d_OffsetY             "MRC_%02d_OffsetY"
#define STR_MRC_02d_SizeX               "MRC_%02d_SizeX"
#define STR_MRC_02d_SizeY               "MRC_%02d_SizeY"

// Cooling Configuration
#define STR_COOLING_MODE                "COOLING_MODE"
#define STR_COOLING_TARG_TEMP           "COOLING_TARG_TEMP"
#define STR_COOLING_LEVEL               "COOLING_LEVEL"

// Bad Pixel Correction Configuration
#define STR_BADPIX_OPTIONS              "BADPIX_OPTIONS"
#define STR_BADPIX_STATE                "BADPIX_STATE"
#define STR_BADPIX_MODE                 "BADPIX_MODE"

// Shading Correction
#define STR_SHCO_ACTION                 "SHCO_ACTION"
#define STR_SHCO_FLAG                   "SHCO_FLAG"
#define STR_SHCO_04X_05X                "SHCO_%04X_%05X"
#define STR_SHCO_04X_                   "SHCO_%04X_"

// Mode time configuration
#define STR_MODETIME_INTERVAL           "MODETIME_INTERVAL"
#define STR_MODETIME_FLAGS              "MODETIME_FLAGS"
#define STR_MODETIME_COUNT_DT           "MODETIME_COUNT_DT"
#define STR_MODETIME_DT02d              "MODETIME_DT%02d"

// Mode event configuration
#define STR_MODEEVENT_TYPE              "MODEEVENT_TYPE"
#define STR_MODEEVENT_OPTION            "MODEEVENT_OPTION"
#define STR_MODEEVENT_EDGE              "MODEEVENT_EDGE"
#define STR_MODEEVENT_AVERAGEPAR        "MODEEVENT_AVERAGEPAR"
#define STR_MODEEVENT_THRESHOLD         "MODEEVENT_THRESHOLD"
#define STR_MODEEVENT_ROI_OffsetX       "MODEEVENT_ROI_OffsetX"
#define STR_MODEEVENT_ROI_OffsetY       "MODEEVENT_ROI_OffsetY"
#define STR_MODEEVENT_ROI_SizeX         "MODEEVENT_ROI_SizeX"
#define STR_MODEEVENT_ROI_SizeY         "MODEEVENT_ROI_SizeY"

// Trigger options configuration
#define STR_TRIGGEROPT_OPTIONS          "TRIGGEROPT_OPTIONS"
#define STR_TRIGGEROPT_CYCTIME          "TRIGGEROPT_CYCTIME"
#define STR_TRIGGEROPT_MINPULS          "TRIGGEROPT_MINPULS"

// color mapping configuration
#define STR_COMA_FileName               "COMA_FileName"

//!@}
//! \endcond


/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Status Flags #CamUSB_GetCameraStatus 
//! \brief see #S_CAMERA_STATUS::dwDeviceErrorStat
//!@{

#define STATUS_ERROR_SDRAM          0x00000001  //!< SDRAM test error
#define STATUS_ERROR_FLASH          0x00000002  //!< FLASH error
#define STATUS_ERROR_SPI            0x00000004  //!< SPI error
#define STATUS_ERROR_UART            0x00000008  //!< UART error
#define STATUS_ERROR_IMG_SENSOR      0x00000010  //!< image SENSOR error
#define STATUS_ERROR_USB            0x00000020  //!< USB error
#define STATUS_ERROR_FPGA            0x00000040  //!< FPGA error
#define STATUS_ERROR_CPLD            0x00000080  //!< CPLD error
#define STATUS_ERROR_TEMP_SENSOR    0x00000100  //!< temperature sensor error
#define STATUS_ERROR_CLOCK_GEN      0x00000200  //!< sensor clock generator error
#define STATUS_ERROR_REF_CLOCK      0x00000300  //!< error setting reference clock

#define STATUS_MASK_ALL_ERROR        0x000003FF  //!< mask for all errors

#define STATUS_NO_STATIC_CONFIG     0x00100000  //!< no valid static configuration data present
#define STATUS_NO_CAMERA_CONFIG     0x00200000  //!< no valid camera configuration data present
#define STATUS_NO_SENSOR_CONFIG     0x00400000  //!< no valid sensor configuration data present

#define STATUS_MASK_POWER            0x07000000  //!< mask for all power settings
#define STATUS_POWER_FULL_READY     0x00000000  //!< no power restrictions detected or checked
#define STATUS_POWER_NO_CAMERA      0x01000000  //!< power restrictions detected => camera not fully initialized
#define STATUS_POWER_NO_COOLING     0x02000000  //!< power restrictions detected => cooling not possible
#define STATUS_POWER_FAILED         0x04000000  //!< power restrictions test failed
#define STATUS_POWER_CHECKED        0x40000000  //!< power restrictions checked

#define STATUS_JUST_FW_START        0x80000000  //!< indicate FW was started

//!@}



/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Camera Configuration
//! \brief S_CONFIGURATION::dwBootOption and S_VDL_FILE_PARAMS::wAction
//!@{
#define CFG_BO_DEFAULT              0x00000000  //!< default options
#define CFG_BO_BOOT_FROM_FLASH      0x00000001  //!< boot automaticly from flash memory
#define CFG_BO_WAIT_FOR_CMD         0x00000002  //!< wait till pc start the firmware (not supported)


//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Default #CamUSB_GetImage timeout
//!@{

#define     GETIMAGE_DEFAULT_TIMEOUT     0

//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Default #CamUSB_TriggerImage timeout
//!@{

#define     TRIGGERIMAGE_DEFAULT_TIMEOUT    10000       // in ms

//!@}


/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Status USB device address
//!@{

//! \brief  Flag if set the device is enumerated as high speed device
#define    USB_HS                0x80
#define    USB_BUS_ID_MASK        0x7F  //!< USB bus id mask

//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Multishot Capture
//!@{

#define  MAX_MULTISHOT_IMAGES    16      //!< maximum number of images in multishot mode

//!@}


/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Driver Camera Types
//!@{

#ifdef _LANGUAGE_C  

//! \brief Modul types for  function CamDev_GetVersion
enum EModulType
{
    EMT_CamDevAPI,    //!< CamDev API DLL
    EMT_DriverUSB,    //!< USB driver
    EMT_DriverGigE,    //!< GigE filter driver
    EMT_DriverLibUSB,  //!< USB driver based on "LibUSB - project"
};

//! \brief Modul types for  function CamDev_GetVersion
enum ECameraType
{
    ECT_All      = 0,  //!< all supported cameras
    ECT_USB      = 1,  //!< only USB cameras
    ECT_GigE    = 2,  //!< only GigE cameras
    ECT_LibUSB  = 4,  //!< only USB cameras with driver based on "LibUSB - project"
};

#endif

//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: S_CAMERA_INIT for dwFirmwareOptions 
//! see also #S_CAMERA_INIT and #CamUSB_InitCameraExS
//!@{

#define FWOPT_AUTOMATIC    0x00     //!< start camera firmware if nesseccary (from FLASH), #FO_START (from file)
#define FWOPT_KEEP         0x01     //!< don't change firmware state
#define FWOPT_REBOOT       0x02     //!< start camera firmware from camera internal FLASH, also if they allready is running
#define FWOPT_START        0x03     //!< start camera from the passed firmware (pFirmware / dwFirmewareSize)

//!@}

/////////////////////////////////////////////////////////////////////////////
//! \name Constants: API image buffers and camera devices
//!@{

#define  MAX_CAMERA_DEVICES      16    //!< number of possible camera devices

//! Device number to read global error codes my be caused by SetDeviceNotifyMsg
#define GLOBAL_DEVNR          0xFF

//! no camera device define
#define  NO_CAMERA_DEVICE      (-1)

//! no serial number define
#define  NO_SERIAL_NUMBER        0    //!< 0 means no serial number

//! invalid camera device handle
#define  CAMDEV_HANDLE_INVALID   (0)
//
//!@}



/////////////////////////////////////////////////////////////////////////////
//! \name Constants: Firmware and API Function Return Codes
//! used by #CamUSB_GetLastError
//!@{
#define  retMASK_CAMERA            0xFFFFFF00    //!< masking camera error codes
#define  retOK                      0x00000000    //!< OK
#define  retFEATNS                  0x00000001    //!< feature not supported
#define  retCHK                    0x00000002    //!< invalid Command/Message-Checksum
#define  retPARAMS                  0x00000003    //!< invalid Parameter

#define  retNACK                    0x0000000D    //!< I2C NACK received
#define  retNOPOWER                0x0000000E    //!< Current power restrictions don't allow this function

#define retSENSOR                  0x00000010    //!< sensor error
#define retFPGA                    0x00000011    //!< FPGA error
#define retCPLD                    0x00000012    //!< CPLD access error
#define retCPLD_PROGRAM            0x00000013    //!< CPLD programming failed
#define retSDRAM                  0x00000014    //!< SDRAM test failed
#define retFLASH_OP                0x00000015    //!< FLASH operation failed
#define retOP_SLEEPMODE            0x00000016    //!< Operation not supported in current sleep mode

#define retUSB                    0x00000020    //!< USB interface error
#define retUSBCONFIG              0x00000021    //!< USB chip configuration error
#define retUSBREG                  0x00000022    //!< USB chip read register error
#define retUSBNOTSENT              0x00000023    //!< last USB packet not sent

#define retUART_TIMEOUT            0x00000028    //!< UART timeout

#define retUNEXP_TRIGGER          0x00000030    //!< unexpected image trigger

#define retGIGE_MAC                0x00000031    //!< GIGE MAC interface error
#define retGIGE_PHY                0x00000032    //!< GIGE PHY interface error

#define retFUNCCODE                0x00000040    //!< invalid camera function code
#define retFUNCSET                0x00000041    //!< SetFunction not supported for this function code
#define retFUNCGET                0x00000042    //!< GetFunction not supported for this function code
#define retFUNCCAPS                0x00000043    //!< GetFunctionCaps not available for this function code

//----------------------------------- CENTROID ----------------------------------------
#define retINV_GAP                0x00000044      //!< invalid gap
#define retINV_SIZE_X             0x00000045      //!< invalid size x
#define retINV_SIZE_Y             0x00000046      //!< invalid size y
#define retINV_OFFSET_X           0x00000047      //!< invalid offset x
#define retINV_OFFSET_Y           0x00000048      //!< invalid offset y
#define retINVSPOTCOUNT           0x00000049      //!< invalid spot count
#define retCENTROIDNOINIT         0x0000004A      //!< centroid no init done


//----------------------------------- VDL ---------------------------------------------
#define retVDL_SETTINGS            0x00000050    //!< VDL invalid settings
#define retVDL_PIXELTYPE          0x00000051    //!< Pixeltype with VDL - mode not supported
#define retVDL_SD_MEM              0x00000052    //!< VDL not enough space on SDCard to capture whole time
#define retVDL_INTERN_MEM          0x00000053    //!< VDL not enough SDRAM space to capture whole time
#define retVDL_TRIGGER_NOTREADY    0x00000054    //!< VDL is not ready to trigger
#define retVDL_DIR_UNKOWN_ACT      0x00000055    //!< VDL Dir unknown action
#define retVDL_PATH_NOTEXIST      0x00000056    //!< VDL path not found
#define retVDL_DIR_NOT_EMPTY      0x00000057    //!< VDL error directory isn't empty
#define retVDL_FILE_UNKOWN_ACT    0x00000058    //!< VDL File unknown action
#define retVDL_DEL_FILE            0x00000059    //!< VDL delete-action error
#define retVDL_WR_FILE            0x0000005A    //!< VDL write-action error
#define retVDL_RD_FILE            0x0000005B    //!< VDL read-action error
#define retVDL_RD_FILE_MEM        0x0000005C    //!< VDL file to big to read
#define retVDL_FILE_NOTEXIST      0x0000005D    //!< VDL file not found


//----------------------------------- VDL ---------------------------------------------

#define retFIRMWARE_CRC            0x00000060    //!< invalid checksum of uploaded firmware data
#define retFIRMWARE_TYPE          0x00000061    //!< unsupported firmware block type
#define retFIRMWARE_SIZE          0x00000062    //!< uploaded firmware data too big
#define retFIRMWARE_FLASH          0x00000063    //!< firmware flash verify error

#define retSNCHANGE_AUTH          0x00000066    //!< serial number change not authorized
#define retSNCHANGE_FAIL          0x00000067    //!< serial number change failed

#define retCONFIG_SIZE            0x0000006A    //!< invalid size of configuration data
#define retCONFIG_CRC              0x0000006B    //!< invalid CRC of configuration data
#define retCONFIG_TYPE            0x0000006C    //!< invalid type of configuration data
#define retCONFIG_PARAM            0x0000006D    //!< invalid configuration data parameter or not found

#define retCAPMODE                0x00000070    //!< unsupported capture mode
#define retCAPMODE_IMGCOUNT        0x00000071    //!< too many images requested for capture
#define retCAPMODE_ZERO            0x00000072    //!< image count zero not allowed
#define retCAPMODE_BUFAVAIL        0x00000073    //!< not enough image buffers available

#define retPIXELTYPE              0x00000074    //!< unsupported pixel type

#define retPORT_INDEX              0x00000076    //!< invalid I/O port index
#define retPORT_TYPE              0x00000077    //!< I/O port type not supported
#define retPORT_FEATURE            0x00000078    //!< I/O port feature not supported
#define retPORT_STATE              0x00000079    //!< I/O port state not supported

#define retTRIG_OPTION            0x0000007B    //!< invalid trigger option setting
#define retTRIG_OPTION_VALUE      0x0000007C    //!< trigger option value out of range

#define retPORT_DELAY              0x00000080    //!< I/O port delay out of range

#define retGAIN_CHANNEL            0x00000081    //!< gain channel not supported
#define retGAIN_RANGE              0x00000082    //!< gain setting out of range
#define retGAIN_STEP              0x00000083    //!< gain stepping not supported
#define retGAIN_CHANNELS_ONCE      0x00000084    //!< tried to get/set too many gain channels at the same time

#define retLUT_INDEX              0x00000086    //!< LUT index not supported
#define retLUT_ZERO_DATA          0x00000087    //!< data transfer from/to LUT zero not allowed
#define retLUT_DATA_SIZE          0x00000088    //!< invalid LUT data size

#define retSENSOR_REG_VERIFY      0x0000008B    //!< sensor register verify failed
#define retSENSOR_REG_READ        0x0000008C    //!< no sensor register read or verify possible

#define retROI_LINE_MODE_IN_USE    0x0000008F    //!< Set ROI isn't available because Line mode is in use

#define retROI_ZERO                0x00000090    //!< image has zero dimension
#define retROI_RANGE              0x00000091    //!< AOI setting out of range
#define retROI_SKIP_BIN_INV        0x00000092    //!< skip or bin setting not supported
#define retROI_SKIP_BIN_ONE        0x00000093    //!< tried to set more than one skip/bin mode
#define retROI_SKIP_LESS_BIN      0x00000094    //!< skip must be equal or higher than bin setting
#define retROI_OFFS_MULT_SKIP_2    0x00000095    //!< sensor offset must be a multiple of skip setting and 2
#define retROI_WIDTH_MULT_4        0x00000096    //!< resulting image width must be a multiple of 4
#define retROI_HIGH_MULT_2        0x00000097    //!< resulting image height must be a multiple of 2
#define retROI_WIDTH_MULT_2        0x00000098    //!< resulting image width must be a multiple of 2

#define retEXPOSURE_RANGE          0x00000099    //!< exposure value out of range
#define retEXPOSURE_STEP          0x0000009A    //!< exposure stepping not supported

#define retROI_JPEG_DIM            0x0000009B    //!< image width / height to low (32x32)
#define retROI_JPEG_MULT_16        0x0000009C    //!< image width / height must be a multiple of 16
#define retJPEG_SETTINGS          0x0000009D    //!< jpeg settings out of range

#define retMEMORY_INDEX            0x000000A0    //!< memory region index not supported
#define retMEMORY_VERIFY          0x000000A1    //!< memory verify failed
#define retMEMORY_WRITE            0x000000A2    //!< write access to memory region not supported
#define retMEMORY_READ_VERIFY      0x000000A3    //!< read access or verify of memory region not supported
#define retMEMORY_ADDRESS          0x000000A4    //!< invalid memory address or size

#define retBUS_INDEX              0x000000A7    //!< unsupported bus index
#define retBUS_READ                0x000000A8    //!< bus read or verify not supported
#define retBUS_WIDTH              0x000000A9    //!< invalid bus address or data width
#define retBUS_VERIFY              0x000000AA    //!< bus verify failed

#define retBUS_ID                  0x000000AB    //!< unsupported bus id
#define retBUS_PROTOCOL            0x000000AC    //!< unsupported protocol type
#define retBUS_STATE              0x000000AD    //!< unsupported bus state
#define retBUS_FLAG                0x000000AE    //!< unsupported bus flag

#define retFLIP_MODE              0x000000B0    //!< flip mode not supported
#define retFLIP_HOR_BIN            0x000000B1    //!< horizontal flip mode is not supported in combination with binning

#define retTEMP_INDEX              0x000000B5    //!< invalid temperature sensor index

#define retCLOCK_RANGE            0x000000B8    //!< clock value out of range
#define retCLOCK_STEP              0x000000B9    //!< clock stepping not supported

#define retBITSHIFT                0x000000BB    //!< bitshift setting not supported

#define retGAMMA_RANGE            0x000000BD    //!< gamma setting out of range
#define retGAMMA_STEP              0x000000BE    //!< gamma stepping not supported

#define retBRIGHTNESS_RANGE        0x000000C0    //!< brightness setting out of range
#define retBRIGHTNESS_STEP        0x000000C1    //!< brightness stepping not supported
#define retCONTRAST_RANGE          0x000000C2    //!< contrast setting out of range
#define retCONTRAST_STEP          0x000000C3    //!< contrast stepping not supported

#define retFRAMERATE_RANGE        0x000000C6    //!< framerate value out of range
#define retFRAMERATE_STEP          0x000000C7    //!< framerate stepping out of range

// test pattern error codes
#define retTEST_PATTERN_INDEX      0x000000CA    //!< test-pattern index not supported
#define retTEST_PATTERN_UPLOAD    0x000000CB    //!< test pattern upload only ro test-pattern index 1 allowed
#define retTEST_PATTERN_DOWNLOAD  0x000000CC    //!< test pattern download index not allowed
#define retTEST_PATTERN_DATA_SIZE  0x000000CD    //!< invalid test-pattern data size
#define retTEST_PATTERN_ZERO_DATA  0x000000CE    //!< data transfer from/to test-pattern zero not allowed
#define retTEST_PATTERN_NOT_VALID  0x000000CF    //!< user test-pattern not loaded or single color pixeltype

// line cam mode error codes
#define retLINE_CAM_SCAN_LINES    0x000000D0    //!< line cam: scan lines setting out of range
#define retLINE_CAM_OFFSET_X      0x000000D1    //!< line cam: offset_x setting out of range
#define retLINE_CAM_OFFSET_Y      0x000000D2    //!< line cam: offset_y setting out of range
#define retLINE_CAM_SIZE_X        0x000000D3    //!< line cam: size_x setting out of range
#define retLINE_CAM_SIZE_Y        0x000000D4    //!< line cam: size_y setting out of range
#define retLINE_CAM_SIZE_Y_MULT    0x000000D5    //!< line cam: size_y setting must be multiple of scanlines

// multishot config error codes
#define retMULTISHOT_CFG_RECORDS  0x000000D7  //!< multishot cfg: invalid number of records
#define retMULTISHOT_CFG_SIZE      0x000000D8  //!< multishot cfg: invalid data or record size

// autoexposure error codes
#define retAUTOEXPOSURE_RANGE      0x000000DA    //!< set auto exposure : setting out of range
#define retAUTOEXP_NOTAVAIL        0x000000DB    //!< set auto exposure : not available in hardware trigger mode

// image event error codes
#define  retMODE_EVENT_CFG          0x000000DC    //!< #MODE_EVENT configuration is out of range
#define  retMODE_TIME_CFG          0x000000DE    //!< #MODE_TIME configuration is out of range

// Camera - Warnings
#define retNO_TRIGGER_INPUT        0x000000E0    //!< warning: no trigger input assigned but capture mode triggered hardware set
#define retPIXELTYPE_CHANGE        0x000000E1    //!< warning: resulting pixeltype changed due to flip mode setting
#define retNO_LINETRIGGER_INPUT    0x000000E2    //!< warning: no line trigger input assigned but hardware triggered line camera mode set
#define retVDL_DISABLED           0x000000E3    //!< warning: new configuration render the VDL settings invalid. VDL has been disabled.
#define retBITSHIFT_IGNORE        0x000000E4    //!< warning: bitshift setting is ignored as long as a user defined LUT is active
#define retGAMMA_IGNORE            0x000000E5    //!< warning: gamma setting is ignored as long as a user defined LUT is active
#define retBRIGHTNESS_IGNORE      0x000000E6    //!< warning: brightness and contrast settings are ignored as long as a user defined LUT is active
#define retUSER_DEFINED_LUT        0x000000E7    //!< warning: user defined LUT deactivates bitshift, gamma, brightness and contrast settings
#define retEOF                    0x000000E8    //!< warning: End of File is reached
#define retFRAMERATE_CHANGED      0x000000EA    //!< warning: Frame rate was changed

// API - Warnings
#define retAUTOEXPDISLUT          0x00000100    //!< warning: auto exposure has disabled lut support
#define retWBACTIVE                0x00000101    //!< warning: White Balance active no get image allowed
#define  retNOIMG                  0x00000102    //!< warning: no images to scan
#define  retNOIMGBUF                0x00000103    //!< warning: no image buffer available call ReleaseImage
#define  retAEXP_ROI_INV            0x00000104    //!< warning: Autoexposure ROI is invalid, default ROI restored
#define retOLDFW                  0x00000105    //!< warning: The camera firmware is not up to date. Please update the firmware to avoid problems!
#define retOLDDLL                  0x00000106    //!< warning: The camera firmware is newer than "CamUSB_API.DLL" expected. Please update the DLL to avoid problems!
#define retOLDDRV                  0x00000107    //!< warning: New "USB Device Driver" available. Please update to avoid problems!
#define retTESTFW                  0x00000108    //!< warning: You are using a not officially released test firmware!
#define retDEVRECOVERY            0x00000109    //!< warning: Device recovery in progress, please try again later!
#define retSHADACTIVE              0x0000010A    //!< warning: Shading Reference Image captureing active no get image allowed

#define retHWTRIGDISAUTOEXP       0x00000110    //!< warning: hardware trigger mode has disabled auto exposure support
#define retSLEEPMODE_ACTIVE       0x00000111    //!< warning: the camera is currently at sleep mode, most functions won't be work


#define retIMGTOBRIGHT_WA          0x00000120    //!< warning: The image is too bright for the requested operation
#define retIMGTODARK_WA            0x00000121    //!< warning: The image is too drak for the requested operation
#define retIMGTOINHOMOGEN_WA      0x00000122    //!< warning: The image is not homogeny enough for the requested operation


// API - Error-Codes
#define  retINVMODE                0x00000200    //!< invalid camera mode
#define  retCNTIMGNS                0x00000201    //!< this count of images is not supported
#define retGAINCHANNELINVALID      0x00000202    //!< the gain channel ist invalid
#define retCAMIMGFORMATNS          0x00000203    //!< received image format is not supported
#define retIMGFORMATNS            0x00000204    //!< images format is not support
#define retINVALIDIMAGEHEADER      0x00000205    //!< invalid image header
#define retINVIMGPTR              0x00000206    //!< invalid image pointer

#define retWB_INVREGION            0x00000207    //!< invalid white balance region (see camera ROI settings)
#define retWB_NORGBIMG            0x00000208    //!< readmode Color expected (see camera settings)
#define retWB_2DARK                0x00000209    //!< selected region is to dark, select a brighter gray region
#define retWB_2BRIGHT              0x0000020A    //!< selected region is to bright, select a gray region
#define retWB_2INHOMOGEN          0x0000020B    //!< selected region is not homogeny enough

#define retGETIMG_TIMEOUT          0x0000020C    //!< getimage call timeout
#define retSRCDSTNS               0x0000020D      //!< conversion from source to destination pixeltype not found
#define retRESIZE                 0x0000020E      //!< Resize operation failed
#define retBYPASS                 0x0000020F      //!< Bypass Command Execution failed => stop image threads first

#define retCAPMODE_WRONG          0x00000210    //!< wrong capture mode

#define retAUTOEXPDISBYHWTRIG     0x00000211    //!< auto exposure disabled by hardware trigger, change capture mode first
#define retWASIFail               0x00000212    //!< wait async start image failed (async trigger aborted)

#define retINVRESIZE              0x00000213      //!< invalid resize settings

#define retCORIMGHDR              0x00000220      //!< the image header seems to be corrupted => image should be ignored

#define retCFG_INVCMD             0x00000221      //!< invalid dll config command
#define retCFG_INVVALUE           0x00000222      //!< invalid dll config value


#define  retPARAM1                  0x00000230    //!< parameter 1 is invalid
#define  retPARAM1S1                0x00000231    //!< 1. structure item of parameter 1 is invalid
#define  retPARAM1S2                0x00000232    //!< 2. structure item of parameter 1 is invalid
#define  retPARAM1S3                0x00000233    //!< 3. structure item of parameter 1 is invalid
#define  retPARAM1S4                0x00000234    //!< 4. structure item of parameter 1 is invalid
#define  retPARAM1S5                0x00000235    //!< 5. structure item of parameter 1 is invalid
#define  retPARAM1S6                0x00000236    //!< 6. structure item of parameter 1 is invalid
#define  retPARAM1S7                0x00000237    //!< 7. structure item of parameter 1 is invalid
#define  retPARAM1S8                0x00000238    //!< 8. structure item of parameter 1 is invalid
#define  retPARAM1S9                0x00000239    //!< 9. structure item of parameter 1 is invalid

#define  retPARAM2                  0x00000240    //!< parameter 2 is invalid
#define  retPARAM2S1                0x00000241    //!< 1. structure item of parameter 2 is invalid
#define  retPARAM2S2                0x00000242    //!< 2. structure item of parameter 2 is invalid
#define  retPARAM2S3                0x00000243    //!< 3. structure item of parameter 2 is invalid
#define  retPARAM2S4                0x00000244    //!< 4. structure item of parameter 2 is invalid
#define  retPARAM2S5                0x00000245    //!< 5. structure item of parameter 2 is invalid
#define  retPARAM2S6                0x00000246    //!< 6. structure item of parameter 2 is invalid
#define  retPARAM2S7                0x00000247    //!< 7. structure item of parameter 2 is invalid
#define  retPARAM2S8                0x00000248    //!< 8. structure item of parameter 2 is invalid
#define  retPARAM2S9                0x00000249    //!< 9. structure item of parameter 2 is invalid

#define  retPARAM3                  0x00000250    //!< parameter 3 is invalid
#define  retPARAM3S1                0x00000251    //!< 1. structure item of parameter 3 is invalid
#define  retPARAM3S2                0x00000252    //!< 2. structure item of parameter 3 is invalid
#define  retPARAM3S3                0x00000253    //!< 3. structure item of parameter 3 is invalid
#define  retPARAM3S4                0x00000254    //!< 4. structure item of parameter 3 is invalid
#define  retPARAM3S5                0x00000255    //!< 5. structure item of parameter 3 is invalid
#define  retPARAM3S6                0x00000256    //!< 6. structure item of parameter 3 is invalid
#define  retPARAM3S7                0x00000257    //!< 7. structure item of parameter 3 is invalid
#define  retPARAM3S8                0x00000258    //!< 8. structure item of parameter 3 is invalid
#define  retPARAM3S9                0x00000259    //!< 9. structure item of parameter 3 is invalid

#define  retPARAM4                  0x00000260    //!< parameter 4 is invalid
#define  retPARAM4S1                0x00000261    //!< 1. structure item of parameter 4 is invalid
#define  retPARAM4S2                0x00000262    //!< 2. structure item of parameter 4 is invalid
#define  retPARAM4S3                0x00000263    //!< 3. structure item of parameter 4 is invalid
#define  retPARAM4S4                0x00000264    //!< 4. structure item of parameter 4 is invalid
#define  retPARAM4S5                0x00000265    //!< 5. structure item of parameter 4 is invalid
#define  retPARAM4S6                0x00000266    //!< 6. structure item of parameter 4 is invalid
#define  retPARAM4S7                0x00000267    //!< 7. structure item of parameter 4 is invalid
#define  retPARAM4S8                0x00000268    //!< 8. structure item of parameter 4 is invalid
#define  retPARAM4S9                0x00000269    //!< 9. structure item of parameter 4 is invalid

#define  retPARAM5                  0x00000270    //!< parameter 5 is invalid 
#define  retPARAM5S1                0x00000271    //!< 1. structure item of parameter 5 is invalid
#define  retPARAM5S2                0x00000272    //!< 2. structure item of parameter 5 is invalid
#define  retPARAM5S3                0x00000273    //!< 3. structure item of parameter 5 is invalid
#define  retPARAM5S4                0x00000274    //!< 4. structure item of parameter 5 is invalid
#define  retPARAM5S5                0x00000275    //!< 5. structure item of parameter 5 is invalid
#define  retPARAM5S6                0x00000276    //!< 6. structure item of parameter 5 is invalid
#define  retPARAM5S7                0x00000277    //!< 7. structure item of parameter 5 is invalid
#define  retPARAM5S8                0x00000278    //!< 8. structure item of parameter 5 is invalid
#define  retPARAM5S9                0x00000279    //!< 9. structure item of parameter 5 is invalid

#define  retPARAM6                  0x00000280    //!< parameter 6 is invalid
#define  retPARAM6S1                0x00000281    //!< 1. structure item of parameter 6 is invalid
#define  retPARAM6S2                0x00000282    //!< 2. structure item of parameter 6 is invalid
#define  retPARAM6S3                0x00000283    //!< 3. structure item of parameter 6 is invalid
#define  retPARAM6S4                0x00000284    //!< 4. structure item of parameter 6 is invalid
#define  retPARAM6S5                0x00000285    //!< 5. structure item of parameter 6 is invalid
#define  retPARAM6S6                0x00000286    //!< 6. structure item of parameter 6 is invalid
#define  retPARAM6S7                0x00000287    //!< 7. structure item of parameter 6 is invalid
#define  retPARAM6S8                0x00000288    //!< 8. structure item of parameter 6 is invalid
#define  retPARAM6S9                0x00000289    //!< 9. structure item of parameter 6 is invalid

#define  retPARAM7                  0x00000290    //!< parameter 7 is invalid
#define  retPARAM7S1                0x00000291    //!< 1. structure item of parameter 7 is invalid
#define  retPARAM7S2                0x00000292    //!< 2. structure item of parameter 7 is invalid
#define  retPARAM7S3                0x00000293    //!< 3. structure item of parameter 7 is invalid
#define  retPARAM7S4                0x00000294    //!< 4. structure item of parameter 7 is invalid
#define  retPARAM7S5                0x00000295    //!< 5. structure item of parameter 7 is invalid
#define  retPARAM7S6                0x00000296    //!< 6. structure item of parameter 7 is invalid
#define  retPARAM7S7                0x00000297    //!< 7. structure item of parameter 7 is invalid
#define  retPARAM7S8                0x00000298    //!< 8. structure item of parameter 7 is invalid
#define  retPARAM7S9                0x00000299    //!< 9. structure item of parameter 7 is invalid


#define  retSTART_FW_FAILED        0x000002FE    //!< start firmware failed
#define  retNO_FW_RUNNING          0x000002FF    //!< No firmware is running at the camera, please boot one first

#define  retSYNCLOCKED              0x00000300    //!< an other thread is using a camera fuction at the same time
#define  retSENSORNS                0x00000301    //!< sensor type not supported

#define  retNOTHREADRESPONSE        0x00000302    //!< no thread response
#define  retTHREADISSTOPPED        0x00000303    //!< thread is stopped
#define  retTHREADNPAUSED          0x00000304    //!< thread not paused
#define  retTHREADISRUNNING        0x00000305    //!< thread is running


#define  retESC                    0x00000401    // CANCEL
#define  retCOM                    0x00000402    // communication error
#define  retIDS                    0x00000403    // illegal data structure
#define  retNULL                    0x00000404    // illegal or null pointer
#define  retNOTINIT                0x00000405    // driver is not yet initialized
#define  retDEVNF                  0x00000406    // device  not found
#define retDLLACCESS              0x00000409    // parallel DLL access not allowed
#define  retIMGCHK                  0x0000040A    //!< invalid Image-Checksum
#define  retMEM                    0x0000040B    // not enough free memory available
#define  retNS                      0x0000040C    // Firmware-Revision not supported
#define retHUFFMAN                0x0000040D    // Huffman decode error

#define  retBUSY                    0x00000412    // peripherial device already running

#define retINVALIDSECTION         0x0000041E      // Invalid valid section name
#define retINVALIDPARAM            0x0000041F      // A parameter passed to the function is invalid
#define  retFORMAT                  0x00000420    // Error in Host-Protocol (Format)
#define  retFORMAT_CMD              0x00000421    // Error in Host-Protocol (Format cmd out)
#define  retFORMAT_MSG              0x00000422    // Error in Host-Protocol (Format msg in)
#define  retFORMAT_DOU              0x00000423    // Error in Host-Protocol (Format data out)
#define  retFORMAT_DIN              0x00000424    // Error in Host-Protocol (Format data in)
#define retFORMAT_OPC              0x00000425    // Error in Host-Protocol (wrong opc responce)
#define retFORMAT_CHK              0x00000426    // Error in Host-Protocol (checksum)

#define  retPENDING                0x00000440    //async. operation pending
#define  retTIMEOUT                0x00000441    //async. operation timed out
#define  retABORT                  0x00000442    //async. operation aborted
#define  retIMAGECONVERTABORT      0x00000443    //abort image convert

#define retCAMLIMIT               0x000004FC    //!< Maximum number of simultaneously usable cameras reached!
#define  retRECONNECT               0x000004FD    //!< Camera needs to be reconnected to USB
#define  retREINIT                  0x000004FE    //!< Camera needs to be reconfigured
#define  retRESUME                  0x000004FF    //!< Camera resumed from suspension
#define  retCAMREMOVED              0x00000500    //!< Camera removed
#define  retCAMNOTINIT              0x00000501    //!< Camera not initialized
#define  retCAMDEVNRNS              0x00000502    //!< Camera index not supported
#define  retINVDATASIZE            0x00000503    //!< Invalid data size
#define  retFUNCIDNS                0x00000504    //!< Function ID not supported
#define  retMULTIPLEFUNCID          0x00000505    //!< Only one function ID per call allowed
#define  retMULTIPLEGAINCH          0x00000506    //!< Only one Gain channel per call allowed
#define  retGAINCHNS                0x00000507    //!< Gain channel not supported
#define  retGAINVALUEINPOS          0x00000508    //!< Unable to apply the value with gain channels locked

#define  retNOCONTINUOUSMODE       0x00000509    //!< continuous mode not active
#define  retMULTIPLESTDRES         0x0000050A    //!< Only one standard resolution per call allowed
#define  retSTDRESNS               0x0000050B    //!< Standard resolution not supported
#define  retBITSHIFTVALUENS        0x0000050C    //!< bitshift value not supported
#define  retLUTINDEX_NS            0x0000050D    //!< LUT index not supported
#define retLUTZERODATA_NA         0x0000050E    //!< data transfer from/to LUT zero not allowed
#define retDISABLEDBYAE           0x0000050F    //!< disabled by auto exosure

// VISCA specific return value
#define retVISCA_ACK              0x00000510    //!< Visca ACK (command understood)
#define retVISCA_COMPLEATION      retOK         //!< Visca Completion (command processed)
#define retVISCA_MSGLEN           0x00000511    //!< Visca Message length error (>14 bytes)
#define retVISCA_SYNTAX           0x00000512    //!< Visca Syntax Error
#define retVISCA_CMDBUF           0x00000513    //!< Visca Command buffer full    
#define retVISCA_CMDCAN           0x00000514    //!< Visca Command cancelled
#define retVISCA_NOSOCK           0x00000515    //!< Visca No socket (to be cancelled)
#define retVISCA_CMDNOE           0x00000516    //!< Visca Command not executable        
#define retVISCA_ERROR            0x00000517    //!< Visca generic error


#define retFILE_OPEN              0x00000600    //!< Unable to open file
#define retFILE_SIZE              0x00000601    //!< Invalid file size
#define retFILE_FORMAT            0x00000602    //!< Invalid file format

#define retCOM_OPEN               0x00000603    //!< failed to open COM port
#define retCOM_TRANSFER           0x00000604    //!< COM port transfer error

#define retUPDATE_DEVNAME         0x00000607    //!< wrong update file for this device
#define retUSER_ABORT             0x00000608    //!< user abort

#define retINVFIRMWARE            0x00000610    //!< Invalid Firmware no application included
#define retINV_FW_DEVNAME_NS      0x00000611    //!< Invalid Firmware camera devicename is not supported
#define retINV_FW_STYPE_NS        0x00000612    //!< Invalid Firmware camera sensortype is not supported

#define  retUPD_FWP_INV_SIZE        0x00000620    //!< Update invalid FirmwarePacket Size
#define  retUPD_FWP_INV            0x00000621    //!< Update invalid FirmwarePacket (may be corrupt)
#define  retUPD_RDFP_FAIL          0x00000622    //!< Update reading FirmwarePacket failed
#define  retUPD_OPENFP_FAIL        0x00000623    //!< Update open FirmwarePacket failed
#define  retUPD_INV_UPDINFO        0x00000624    //!< Update invalid Update Informations(don't belong to update file)  
#define  retUPD_INV_DEVNAME        0x00000625    //!< Update invalid device name
#define retUPD_INV_SENTYPE        0x00000626    //!< Update invalid sensor type
#define retUPD_INV_PLATFID        0x00000627    //!< Update invalid platform identifier
#define retUPD_INV_HWREVIS        0x00000628    //!< Update invalid hardware revision
#define retUPD_BETA_FW            0x00000629    //!< Update Beta Firmware (not allowed)
#define retUPD_FW_DOWNGRADE       0x00000630    //!< Update Firmware Downgrade (not allowed)
#define retUPD_SNR_NOMATCH        0x00000631    //!< Update serial number don't match


#define retMULTIPLEACTION          0x00000650    //!< Only one action per call allowed!
#define retINVACTIONTYPE          0x00000651    //!< Invalid action type!
#define retINVFUNCTYPE            0x00000652    //!< Invalid function type!
#define retEXPOSURE_NOTSET        0x00000653    //!< Exposure value not set! (#SHCO_FLAG_EXPOSURE)
#define retNODARKREF              0x00000654    //!< No dark reference set
#define retNOWHITEREF              0x00000655    //!< No white reference set
#define retNOREFSPECIFY            0x00000656    //!< No reference data set (needed to compute)
#define retIMGTOBRIGHT            0x00000657    //!< The image is too bright for the requested operation
#define retIMGTODARK              0x00000658    //!< The image is too drak for the requested operation
#define retIMGTOINHOMOGEN          0x00000659    //!< The image is not homogeny enough for the requested operation
#define retINVREFDATA              0x0000065A    //!< Invalid reference data
#define retWRONGREFTYPE            0x0000065B    //!< Wrong reference type (white/dark)
#define retWRONGSENSORTYPE        0x0000065C    //!< Sensor type of the reference data don't match the current camera sensortype!
#define retWRONGDIMENSIONS        0x0000065D    //!< Reference image data have wrong dimensions (width/height)
#define retNOIMGAVAIL              0x0000065E    //!< No images returned by GetImage


#define  retWINERR                  0x00010000    //!< OS-internal Error (LOWORD=GetLastError)

//!@}


/////////////////////////////////////////////////////////////////////////////
//! \name Macro: check if no error or warning
//!@{
#define  retWarningMin      0x000000E0    //!< lowest warning message id
#define retWarningMax      0x000001FF    //!< highest warning message id

//!@}

#endif // _COMMON_CONSTANTS_EXPORTED_H_
