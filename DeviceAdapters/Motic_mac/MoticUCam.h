#ifndef __MOTIC_UCAM_H__
#define __MOTIC_UCAM_H__

#pragma GCC visibility push(default)

/**
 * @file
 *
 * @brief
 *
 * @if Chinese
 * Motic Universal Camera API声明文件。
 * @endif
 *
 * @if English
 * Motic Universal Camera API declaration file.
 * @endif
 */

/**
 * @mainpage Motic Universal Camera API
 *
 * @if Chinese
 * Motic Universal Camera API （以下简称MUCam）是Motic为其各种USB 2.0摄像头提供的编程接口。MUCam之所以称为Universal，
 * 是因为其综合了不同摄像头的硬件特性，并使其接口在Windows平台、Mac OS X平台和Linux平台下保持一致。MUCam提供了一个平面的动态
 * 链接库（包括32位和64位版本）供其它应用程序调用。
 *
 * @endif
 *
 * @if English
 * Motic Universal Camera API (MUCam) is a programming interface for various Motic USB 2.0 cameras. MUCam is "universal"
 * as it covers different camera features and can be used on Windows, MacOS X and Linux. This SDK is a C DLL (including 32-bit
 * and 64-bit version).
 *
 * @endif
 *
 * @if MoticInternal
 * @author 刘瑞北 liurb@motic.com
 * @author 赵  宇 zhaoy@motic.com
 * @author 陈伟卿 chenwq@motic.com
 * @author 陈德敏 chendemin@motic.com
 *
 * @version 2012-02-22 增加亮度，对比度，饱和度，色调，锐化参数接口，增加对580摄像头的支持
 * @version 2011-08-01 MA系列增加降低频率的设置，通过systemColok（MA中SettingPacket的定义）版本号0001 0007
 * @version 2011-06-15 MC10M 设备支持
 * @version 2011-03-04 修正带冷却的MA在打开冷却时偶尔会出现帧错的问题
 * @version 2011-01-24 MA252曝光值最大值全部为6000, MA系列offset值在0~255范围内使用analog offset
 * @version 2011-01-21 MA282增加Progressive模式切换
 * @version 2010-11-12 增加USB控制器时钟调整接口
 * @version 2010-10-15 修正MA252/MA282曝光与帧率不一致的,且切换分辨时亮度变化的问题
 * @version 2010-08-24 MA系列支持64倍GAIN调整的版本(原版本最高支持4倍gain)版本0001 0004
 * @version 2010-08-06 解决MA285,205,252快速开关设备,分辨率没有切换过来,增加了切换分辨率与updateRegister的互斥
 * @version 2010-08-04 使用Bayer增强算法提高图像质量
 * @version 2010-05-06 支持PID为1010的3001设备,规定图像以镜相显示
 * @version 2009-12-22 使用Mutex代替Critical Section解决Vista/Win7下多个设备同时运行的问题，原因不明
 * @version 2009-11-23 增加对MC352+的支持
 * @version 2009-09-16 MC5001,MA205,MA285,252,282增加去坏点的功能
 * @version 2009-05-22 增加温度查寻的接口
 * @version 2009-05-18 增加对MA282的支持
 * @version 2009-04-16 增加对SWIFT MC3001/3111/3222的支持
 * @version 2008-08-27 增加MC3111/MC3222的支持
 * @version 2008-04-14 增加对MA252的支持
 * @version 2008-04-10 整理接口增加对输出帧数据格式的查寻,取消取帧时数据格式的参数;增加getBinningType的接口
 * @version 2008-03-27 增加获取帧数据时获取当前帧时间戳的参数
 * @version 2008-03-12 增加对MC2002自动白平衡功能关闭的操作，解决图像有时闪烁的问题
 * @version 2008-02-27 增加对MA205/MA285的支持
 * @version 2007-11-29 增加对MC5001的支持。
 * @version 2007-10-16 签名驱动程序。
 * @version 2007-10-15 兼容USB 2.0 PCI扩展卡和VIA芯片组。但MC2002的全分辨率帧速率降低到5.1帧/秒，暂时还没有解决办法。
 * @version 2007-10-15 略微提高各个设备的帧速率，且在Windows Vista下与Windows XP一致。
 * @version 2007-09-21 提高MC1001/2001/2001B/3001在Binning时的帧速率。
 * @version 2007-09-17 限制硬件设备必须与驱动程序匹配时才能正确打开设备，这样Motic和Swift的设备由于Firmware不同，就不能混用驱动程序。
 * @version 2007-09-14 增加对Swift MC1002设备的支持。
 * @version 2007-09-03 增加检测设备是否连接的接口函数。
 * @version 2007-08-30 修正MC1002设置翻转时图像颜色错误。
 * @version 2007-08-29 修正设备突然被拔出时程序崩溃的错误。
 * @version 2007-08-23 限制MC1002自动白平衡中的蓝色通道增益上限，防止在图像绿色为主时自动白平衡颜色偏蓝。
 * @version 2007-08-17 修正同时打开多个设备的问题。
 * @version 2007-08-02 提高MC1002的帧速率，可以使用MC2002同样的Firmware。
 * @version 2007-07-30 修改曝光参数设置接口文档，不再保证以毫秒为单位。
 * @version 2007-07-30 增加对MC1002的支持。
 * @version 2007-07-29 改进MC2002的图像质量。
 * @version 2007-07-26 增加对MC2001B的支持（需要更新驱动程序）。
 * @version 2007-07-24 提高MC1001/2001/3001/2002的帧速率，接近理论值。
 * @version 2007-07-21 增加对MC3001的支持。
 * @version 2007-07-20 减小MC1001/2001的曝光参数范围，已防止在长曝光时间时设置垂直翻转后，出现图像半黑半亮的问题。
 * @version 2007-07-06 增加对MC1001/MC2001的支持。
 * @version 2007-07-04 更新驱动程序名称，更新摄像头类型说明。
 * @version 2007-07-02 第一次发布。
 * @version 2007-05-10 开始开发。
 *
 * @todo 完善MUCam_setBitCount接口,提供不同位深图像数据的支持
 * @todo 增加对MC8001的支持。
 * @todo 提供更新firmware的接口。
 * @endif
 */
//typedef void(*CALLBACK_FUNC)(wchar_t* str, void* p);
/**
 * @brief
 *
 * @if Chinese
 * 摄像头对象句柄。
 *
 * 该句柄实际为void *类型，不要与int或long类型转换，特别是在64位平台上。
 * @endif
 *
 * @if English
 * The camera object handle type.
 *
 * The handle is the "void *" type, never convert it to "int" or "long",
 * especially in 64-bit operating system.
 * @endif
 */
typedef void * MUCam_Handle;

/**
 * @brief
 *
 * @if Chinese
 * 摄像头对象类型枚举定义。
 *
 * 可以利用该参数确定在应用程序中显示的摄像头名称。
 * @endif
 *
 * @if English
 * The camera identifier.
 *
 * It could be used to determine camera name in an application.
 * @endif
 */
typedef enum
{
  /**
   * @brief 未知类型的摄像头。
   *
   * Unknown camera type.
   */
  MUCAM_TYPE_UNKNOWN,
  /**
   * @brief MC1001类型的摄像头。
   *
   * MC1001在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：1280*1024 15帧/秒，640*512 40帧/秒。
   *
   * MC1001 camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 15(1280*1024) or 40(640*512).
   */
  MUCAM_TYPE_MC1001,
  /**
   * @brief MC2001类型的摄像头。
   *
   * MC2001在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：1600*1200 10帧/秒，800*600 30帧/秒。
   *
   * MC2001 camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 10(1600*1200) or 30(800*600).
   */
  MUCAM_TYPE_MC2001,
  /**
   * @brief MC3001类型的摄像头。
   *
   * MC3001在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：2048*1536 6帧/秒，1024*768 21帧/秒。
   *
   * MC3001 camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 6(2048*1536) or 21(1024*768).
   */
  MUCAM_TYPE_MC3001,
  /**
   * @brief MC2001B类型的摄像头。
   *
   * MC2001B在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：1280*1024 10帧/秒，640*512 30帧/秒。
   *
   * MC2001B camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 10(1280*1024) or 30(640*512).
   */
  MUCAM_TYPE_MC2001B,
  /**
   * @brief MC1002类型的摄像头。
   *
   * MC1002在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：1280*1024 14帧/秒，640*512 52帧/秒。
   *
   * MC1002 camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 14(1280*1024) or 52(640*512).
   */
  MUCAM_TYPE_MC1002,
  /**
   * @brief MC2002类型的摄像头。
   *
   * MC2002在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：1600*1200 5帧/秒，800*600 20帧/秒。
   *
   * MC2002 camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 5(1600*1200) or 20(800*600).
   */
  MUCAM_TYPE_MC2002,
  /**
   * @brief MA205类型的摄像头。
   *
   * MA205在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：1360*1024 10帧/秒。
   *
   * MA205 camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 10(1360*1024).
   */
  MUCAM_TYPE_MA205,
  /**
   * @brief MA285类型的摄像头。
   *
   * MA285在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：1360*1024 15帧/秒。
   *
   * MA285 camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 15(1360*1024).
   */
  MUCAM_TYPE_MA285,
  /**
   * @brief MA252类型的摄像头。
   *
   * MA252在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：2048*1536 11帧/秒。
   *
   * MA252 camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 11(2048*1536).
   */
  MUCAM_TYPE_MA252,
  /**
   * @brief Swift MC1002类型的摄像头。
   *
   * Swift MC1002在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：1280*1024 14帧/秒，640*512 52帧/秒。
   *
   * Swift MC1002 camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 14(1280*1024) or 52(640*512).
   */
  MUCAM_TYPE_SWIFT_MC1002,
  /**
   * @brief MD3300类型的摄像头。
   *
   * Undocumented.
   *
   * @deprecated Since MA252 added.
   */
  MUCAM_TYPE_MD3300,
  /**
   * @brief MC5001类型的摄像头。
   *
   * MC5001在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：2592*1944 7帧/秒，1296*972 20帧/秒。
   *
   * MC5001 camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 7(2592*1944) or 20(1296*972).
   */
  MUCAM_TYPE_MC5001,

  /**
   * @brief MC3111类型的摄像头。
   *
   * MC3111在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：1280*1024 13帧/秒，640*480 40帧/秒。
   *
   * MC3111 camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 13(1280*1024) or 40(640*480).
   */
  MUCAM_TYPE_MC3111,

  /**
   * @brief MC3222类型的摄像头。
   *
   * MC3222在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：1600*1200 10帧/秒，800*600 30帧/秒。  
   *
   * MC3222 camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 10(1600*1200) or 30(800*600).
   */
  MUCAM_TYPE_MC3222,

  /**
   * @brief MC3022类型的摄像头。
   *
   * MC3022在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：1600*1200 7帧/秒，800*600 20帧/秒。
   *
   * 该摄像头使用软件的方法使得视场大小与MC2002相同
   *
   * MC3022 camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 7(1600*1200) or 20(800*600).
   */
  MUCAM_TYPE_MC3022,

  /**
   * @brief Swift MC3001类型的摄像头。
   *
   * Swift MC3001在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：2048*1536 6帧/秒，1024*768 21帧/秒。
   *
   * Swift MC3001 camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 6(2048*1536) or 21(1024*768).
   */
  MUCAM_TYPE_SWIFT_MC3001,

  /**
   * @brief Swift MC3222类型的摄像头
   *
   * Swift MC3222在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：1600*1200 10帧/秒，800*600 30帧/秒。
   *
   * Swift MC3222 camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 10(1600*1200) or 30(800*600).
   */
  MUCAM_TYPE_SWIFT_MC3222,

  /**
   * @brief Swift MC3111类型的摄像头。
   *
   * Swift MC3111在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：1280*1024 13帧/秒，640*480 40帧/秒。
   *
   * Swift MC3111 camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 13(1280*1024) or 40(640*480).
   */
  MUCAM_TYPE_SWIFT_MC3111,

  /**
   * @brief MA282类型的摄像头
   *
   * MA282在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：2560*1920 6帧/秒。
   *
   * MA282 camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 6(2560*1920).
   */
  MUCAM_TYPE_MA282,

  /**
   * @brief MC352+ Camrea
   *  
   * Undocumented. Not available at present.
   */
  MUCAM_TYPE_MC352PLUS,

  /**
   * @brief MC3521
   *
   * MC3521在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：800*600 30帧/秒，400*300 80帧/秒。
   *
   * MC3521 camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 30(800*600) or 80(400*300).
   *
   */
  MUCAM_TYPE_MC3521,

  /**
   * @brief MC10M类型的摄像头。
   *
   * MC10M在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：3664*2748 2.2帧/秒，1832*1374 8.5帧/秒, 916*686 30帧/秒。
   *
   * MC10M camera type. On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 2.2(3664*2748) or 8.5(1832*1374) or 30(916*686).
   */
  MUCAM_TYPE_MC10M,

   /**
   * @brief Moticam580摄像头,该摄像头支持亮度，对比度，饱和度，色调，锐化参数调整
   *
   * Moticam580在Windows/Linux平台上输出BGR格式的图像，在MacOS X平台上输出RGB格式的图像。
   * 标称帧速率为：30帧/秒,支持1280*960,800*600,640*480三种分辨率
   *
   * Moticam580 camera. This camera supports the image property adjustment,such as brightness, contrast, saturation, hue and sharpness.
   * On Windows/Linux it exports BGR format images. On MacOS X it exports RGB format images.
   * The normal FPS is 30， has three resolutions: 1280*960,800*600,640*480.
   */
  MUCAM_TYPE_MC580,

  MUCAM_TYPE_MCHDMI,

  //MC3001
  MUCAM_TYPE_VISION_3001,
  //MC3001
  MUCAM_TYPE_VISION_3002

} MUCam_Type;

/**
 * @brief 摄像头输出图像格式枚举定义。
 *
 * The camera exported image format definition.
 */
typedef enum
{
  /**
   * @brief GR开头的Bayer图像格式。
   *
   * The bayer image format in GRBG pattern.
   */
  MUCAM_FORMAT_BAYER_GR_BG,
  /**
   * @brief BG开头的Bayer图像格式。
   *
   * The bayer image format in BGGR pattern.
   */
  MUCAM_FORMAT_BAYER_BG_GR,
  /**
   * @brief GB开头的Bayer图像格式。
   *
   * The bayer image format in GBRG pattern.
   */
  MUCAM_FORMAT_BAYER_GB_RG,
  /**
   * @brief RG开头的Bayer图像格式。
   *
   * The bayer image format in RGGB pattern.
   */
  MUCAM_FORMAT_BAYER_RG_GB,
  /**
   * @brief RGB彩色图像格式。
   *
   * The RGB color image format.
   */
  MUCAM_FORMAT_COLOR_RGB,
  /**
   * @brief BGR彩色图像格式。
   *
   * The BGR color image format.
   */
  MUCAM_FORMAT_COLOR_BGR,
  /**
   * @brief 单色图像格式。
   *
   * The monochrome image format.
   */
  MUCAM_FORMAT_MONOCHROME
} MUCam_Format;

/**
 * @brief 摄像头Binning模式。
 *
 * The camera binning type.
 */
typedef enum
{
  /**
   * @brief 正常模式。
   *
   * Normal binning type.
   */
  MUCAM_BINNING_NORMAL,
  /**
   * @brief 子采样模式。
   *
   * 由软件完成的子采样Binning模式。
   *
   * The sampling type completed by software.
   */
  MUCAM_BINNING_SAMPLING,
  /**
   * @brief 快速显示模式。
   *
   * 针对高分辨率摄像头的快速显示模式。
   *
   * The fast display type for high-resolution camera.
   */
  MUCAM_BINNING_FAST_DISPLAY
} MUCam_Binning_Type;

/**
 * @brief 数据采集的触发模式
 */
typedef enum
{
  /**
   * @brief 自由模式（默认）。
   *
   * The trigger free mode. The SDk will automatically grab frames as soon as possible. It's the default mode when a camera opened.
   */
  MUCAM_TRIGGER_FREE,
  /**
   * @brief 软件触发模式。
   *
   * The software trigger mode. Everytime invoking MUCam_getFrame function will cause camera to exposure and transfer a frame data.
   */
  MUCAM_TRIGGER_SOFTWARE,
  /**
   * @brief 硬件触发模式，在脉冲上升沿触发。
   *
   * The external hardware tigger singal on TTL rise side.
   */
  MUCAM_TRIGGER_HARDWARE_RISE,
  /**
   * @brief 硬件触发模式，在脉冲下降沿触发。
   *
   * The external hardware tigger signal on TTL fall side.
   */
  MUCAM_TRIGGER_HARDWARE_FALL
} MUCam_Trigger_Type;

#ifdef __cplusplus
extern "C"{
#endif
/**
 * @brief 查找连接到计算机一个MUCam支持的摄像头设备。
 *
 * 该函数会查找已经连接到计算机的未被使用的一个MUCam摄像头设备，如果成功找到则创建摄像头对象句柄并返回。
 * 如果找不到则返回0。创建的摄像头对象并未初始化打开，使用完毕后必须使用MUCam_releaseCamera释放。查找的
 * 顺序是不确定的。多次调用该函数，直到返回0，可以查找所有连接的摄像头。
 *
 * To find a camera supported by MUCam. This function will search for a unused camera. If find a camera then
 * return its handle, otherwise return 0. The returned camera object is uninitialized. It should be released
 * by MUCam_releaseCamera function after use. The search order is not fixed. Continuously invoke this function
 * until it returns 0 to find all the connected cameras.
 *
 * @return 摄像头对象句柄，失败则返回0。
 *         The camera handle, 0 if failed.
 *
 * @see MUCam_openCamera, MUCam_releaseCamera
 */
//MUCam_Handle MUCam_findCamera();
typedef MUCam_Handle (*MUCam_findCameraUPP)(void);

/**
 * @brief 释放一个摄像头对象。
 *
 * 释放一个摄像头对象句柄，该摄像头不需要先被关闭。
 *
 * To release a camera object. Closing it before releasing is not necessary.
 *
 * @param camera 摄像头句柄，函数返回后该句柄不再有效。
 *               The camera handle, not valid after return.
 *
 * @see MUCam_findCamera
 */
//void MUCam_releaseCamera(MUCam_Handle camera);
typedef void (*MUCam_releaseCameraUPP)(MUCam_Handle camera);

/**
 * @brief 取得摄像头类型。
 *
 * 可以不用打开摄像头就调用该函数。
 *
 * To get the camera identifier. The function can be invoked without opening the camera.
 *
 * @param camera 摄像头句柄。
 *               The camera handle.
 * @return 摄像头类型枚举常量。
 *         The camera identifier.
 *
 * @see MUCam_Type
 */
//MUCam_Type MUCam_getType(MUCam_Handle camera);
typedef MUCam_Type (*MUCam_getTypeUPP)(MUCam_Handle);

/**
 * @brief 打开摄像头。
 *
 * 新创建的摄像头对象打开后即可进行各种操作。打开已经被打开的摄像头没有作用，会返回true。摄像头打开后
 * 的各个参数状态是不确定的，应用程序必须根据需要（如从用户配置文件中取得前一次的设置）立即设置各个参
 * 数，然后再开始捕捉图像。
 *
 * To open the camera. The camera should be opened before use it. Invoking this function twice has not any effect.
 * The camera status after openning is uncertain, the application should set all the camera status parameters
 * before capturing an image.
 *
 * @param camera 摄像头句柄。
 *               The camera handle.
 * @return 是否成功打开摄像头。
 *         Returns true if successful; false if failed.
 *
 * @see MUCam_closeCamera
 */
//bool MUCam_openCamera(MUCam_Handle camera);
typedef bool (*MUCam_openCameraUPP)(MUCam_Handle);

/**
 * @brief 关闭摄像头。
 *
 * 关闭已经打开的摄像头对象。关闭未被打开的摄像头没有作用。关闭后的摄像头可以被重新打开。摄像头对象句柄
 * 必须被释放才能完全的释放所有占用的资源。
 *
 * To close the opened camera. The closed camera can be re-opened. The MUCam_releaseCamera function should
 * be invoked to release all resources asscociated with a camera.
 *
 * @param camera 摄像头句柄。
 *               The camera handle.
 *
 * @see MUCam_releaseCamera
 */
//void MUCam_closeCamera(MUCam_Handle camera);
typedef void (*MUCam_closeCameraUPP)(MUCam_Handle);

/**
 * @brief 取得摄像头输出的图像格式。
 *
 * To get the image format exported by the camera.
 *
 * @param camera 摄像头句柄。
 *               The camera handle.
 * @return 图像格式类型。
 *         The image format.
 */
//MUCam_Format MUCam_getFrameFormat(MUCam_Handle camera);
typedef MUCam_Format (*MUCam_getFrameFormatUPP)(MUCam_Handle);

/**
 * @brief 读取一帧图像。
 *
 * @param camera 摄像头句柄。
 *               The camera handle.
 * @param buf 缓冲区指针，必须足够容纳将取得的图像数据。
 *            The frame buffer, must be big enough for containing the image data.
 * @param ts 存储该帧数据时间戳（毫秒）的指针，可以为0，表示不关心该参数。
 *            The pointer to the buffer that will receive time stamp(ms) of the frame. Might be 0, in which case it is not used.
 * @return 是否成功读取图像。
 *         Returns true if successful; false if failed.
 *
 * @see MUCam_getFrameFormat
 */
//bool MUCam_getFrame(MUCam_Handle camera, unsigned char *buf, unsigned long *ts);
typedef bool (*MUCam_getFrameUPP)(MUCam_Handle, unsigned char *, unsigned long *);

/**
 * @brief 设置图像数据的位深。
 *
 * 设置位深后，再读取的图像就将按该位深存储。数据高低字节顺序的处理由MUCam自动完成，应用程序获得的图像数据
 * 将在位深定义的范围内。
 *
 * @param camera 摄像头句柄。
 * @param bit 数据位深。例如：
 *            @li @c 8  表示单通道8比特位深，图像数据为0~255，用1个字节存储。
 *            @li @c 10 表示单通道10比特位深，图像数据为0~1023，用2个字节存储。
 *            @li @c 16 表示单通道16比特位深，图像数据为0~4095，用2个字节存储。
 * @return 是否成功设置该参数。
 *
 * @attention 图像设备并不能支持所有的位深格式，目前MUCam的摄像头仅支持8位位深格式。
 * @deprecated 目前MUCam所支持的摄像头都只能支持8位位深，所以该函数目前没有作用，在Windows平台的动态链接库中也没有输出该函数。
 *             Not available at present.
 */
//bool MUCam_setBitCount(MUCam_Handle camera, int bit);
typedef bool (*MUCam_setBitCountUPP)(MUCam_Handle, int);

/**
 * @brief 取得摄像头Binning状态的个数。
 *
 * To get the count of binning supported by the camera.
 *
 * @param camera 摄像头句柄。
 *               The camera handle.
 * @return 摄像头Binning状态的个数。
 *         The count of binning.
 */
//int MUCam_getBinningCount(MUCam_Handle camera);
typedef int (*MUCam_getBinningCountUPP)(MUCam_Handle);

/**
 * @brief 取得摄像头各个Binning的图像尺寸列表。
 *
 * 图像尺寸总是按从大到小的顺序排列，即最大分辨率的尺寸为w[0]和h[0]。
 *
 * To get the image size of each binning. The image size will be descent, i.e. the full resolution size is w[0] and h[0].
 *
 * @param camera 摄像头句柄。
 *               The camera handle.
 * @param w 整数数组，用于保存不同Binning下的图像宽度（像素）。其长度必须大于或等于Binning状态个数。
 *          The integer array that will receive the image width(pixel) of each binning. Must be big enough for containing all the data.
 * @param h 整数数组，用于保存不同Binning下的图像高度（像素）。其长度必须大于或等于Binning状态个数。
 *          The integer array that will receive the image height(pixel) of each binning. Must be big enough for containing all the data.
 * @return 是否成功取得该列表。
 *         Returns true if successful; false if failed.
 *
 * @see MUCam_getBinningCount
 */
//bool MUCam_getBinningList(MUCam_Handle camera, int *w, int *h);
typedef bool (*MUCam_getBinningListUPP)(MUCam_Handle, int *, int *);

/**
 * @brief 取得摄像头各个Binning的类型。
 *
 * To get the type of each binning.
 *
 * @param camera 摄像头句柄。
 *               The camera handle.
 * @param tl Binning类型数组，其长度必须大于或等于Binning状态个数。
 *           The MUCam_Binning_Type array that will receive the type of each binning. Must be big enough for containing all the data.
 * @return 是否成功取得该列表。
 *         Returns true if successful; false if failed.
 *
 * @see MUCam_getBinningCount MUCam_Binning_Type
 */
//bool MUCam_getBinningType(MUCam_Handle camera, MUCam_Binning_Type *tl);
typedef bool (*MUCam_getBinningTypeUPP)(MUCam_Handle, MUCam_Binning_Type *);

/**
 * @brief 设置摄像头的Binning索引。
 *
 * To set the index of the selected binning.
 *
 * @param camera 摄像头句柄。
 *               The camera handle.
 * @param idx Binning索引，从0开始。
 *            The 0-based index of binning.
 * @return 是否成功设置该参数。
 *         Returns true if successful; false if failed.
 *
 * @see MUCam_getBinningCount
 */
//bool MUCam_setBinningIndex(MUCam_Handle camera, int idx);
typedef bool (*MUCam_setBinningIndexUPP)(MUCam_Handle, int);

/**
 * @brief 设置摄像头ROI。
 *
 * ROI的坐标是按当前输出图像（MUCam_getFrame）的尺寸定义的，不论当前的Binning状态是什么、是否已经处于ROI状态和
 * 是否有设置垂直和水平的翻转。当前图像的左上角为（0，0），右下角为（W - 1，H - 1）。函数成功返回后，输入参数
 * 的ROI会被转换到全分辨率下的坐标位置，并可能会根据硬件的要求被调整邻近合适的位置。
 *
 * 考虑到Windows平台绘制位图时要求图像的宽度按4字节对齐，函数对ROI的宽度进行了修正，保证返回的ROI宽度总是按4字
 * 节对齐的。应用程序可以不必再考虑对齐问题。
 *
 * To set the ROI(region of interesting) of the camera. The input coordinates are based on the size of currently
 * exported image, without consideration of the current binning, ROI and flip and mirror settings. The left-top
 * corner of currently exported image is (0,0), the right-bottom is (W-1, H-1). If the function returns successfully,
 * the input coordinate parameters will be transformed to be based on camera sensor matrix, and its value might be adjusted.
 *
 * The ROI image width will be adjusted to align by 4 byte for rendering optimization on Windows.
 *
 * @param camera 摄像头句柄。
 *               The camera handle.
 * @param top ROI的顶边坐标，以像素为单位，从0开始，函数成功返回后为实际设置的坐标。
 *            The 0-based top coordinate(pixel). After successful return, it will be the coordinate on sensor matrix.
 * @param left ROI的左边坐标，以像素为单位，从0开始，函数成功返回后为实际设置的坐标。
 *            The 0-based left coordinate(pixel). After successful return, it will be the coordinate on sensor matrix.
 * @param bottom ROI的底边坐标，以像素为单位，从0开始，函数成功返回后为实际设置的坐标。bottom - top + 1即为ROI的高度（像素）。
 *            The 0-based bottom coordinate(pixel). After successful return, it will be the coordinate on sensor matrix.
 *            The bottom-top+1 equals the width(pixel) of ROI.
 * @param right ROI的右边坐标，以像素为单位，从0开始，函数成功返回后为实际设置的坐标。right - left + 1即为ROI的宽度（像素）。
 *            The 0-based right coordinate(pixel). After successful return, it will be the coordinate on sensor matrix.
 *            The right-left+1 equals the height(pixel) of ROI.
 * @return 是否成功设置摄像头ROI。
 *         Returns true if successful; false if failed.
 *
 * @see MUCam_getFrame
 */
bool MUCam_setROI(MUCam_Handle camera, int *top, int *left, int *bottom, int *right);

/**
 * @brief 设置摄像头垂直翻转状态。
 *
 * To flip the camera image.
 *
 * @param camera 摄像头句柄。
 *               The camera handle.
 * @param b 是否垂直翻转摄像头图像。
 *          The flip setting.
 * @return 是否成功设置该参数。
 *         Returns true if successful; false if failed.
 */
bool MUCam_setFlip(MUCam_Handle camera, bool b);

/**
 * @brief 设置摄像头水平翻转状态。
 *
 * To mirror the camera image.
 *
 * @param camera 摄像头句柄。
 *               The camera handle.
 * @param b 是否水平翻转摄像头图像。
 *          The mirror setting.
 * @return 是否成功设置该参数。
 *         Returns true if successful; false if failed.
 */
bool MUCam_setMirror(MUCam_Handle camera, bool b);

/**
 * @brief 取得摄像头所支持的硬件增益值个数。
 *
 * 摄像头硬件不能支持连续的增益值，应用程序如果要使用硬件增益，则只能从有限值中选择。
 *
 * To get the count of the gain supported by camera. The camera gain is a series of discrete values, the application only can select one of them.
 *
 * @param camera 摄像头句柄。
 *               The camera handle.
 * @return 支持的增益值个数。
 *         The count of the gain supported by camera.
 */
//int MUCam_getGainCount(MUCam_Handle camera);
typedef int (*MUCam_getGainCountUPP)(MUCam_Handle camera);

/**
 * @brief 取得摄像头所支持的增益值列表。
 *
 * 如果应用程序不直接设置硬件增益值，则可以忽略本函数，直接设置增益的索引即可。
 *
 * To get the gain value list.
 *
 * @param camera 摄像头句柄。
 *               The camera setting.
 * @param g 浮点数组，必须足够容纳所有的增益值。
 *          The float array, must be big enough for containing data.
 * @return 是否成功取得该列表。
 *         Returns true if successful; false if failed.
 *
 * @see MUCam_getGainCount
 */
//bool MUCam_getGainList(MUCam_Handle camera, float *g);
typedef bool (*MUCam_getGainListUPP)(MUCam_Handle camera, float *g);

/**
 * @brief 设置RGB各颜色通道的增益值索引。
 *
 * To set the gain index of RGB channel.
 *
 * @param camera 摄像头句柄。
 *               The camera handle.
 * @param r 红色通道增益索引，从0开始。如果超过许可范围，则不改变当前值，函数返回false。
 *          The 0-based gain index of red channel.
 * @param g 绿色通道增益索引，从0开始。如果超过许可范围，则不改变当前值，函数返回false。
 *          The 0-based gain index of green channel.
 * @param b 蓝色通道增益索引，从0开始。如果超过许可范围，则不改变当前值，函数返回false。
 *          The 0-based gain index of blue channel.
 * @return 是否成功设置该参数。
 *          Returns true if successful; false if failed.
 *
 * @attention 某些摄像头不支持RGB各个通道使用不同的增益值，所以只有在输入参数都相同的情况下才会成功执行。
 *            Some camears don't support different gain values in RGB channels. The function will return true only when the parameters r,g,b are the same.
 *
 * @see MUCam_getGainCount
 */
//bool MUCam_setRGBGainIndex(MUCam_Handle camera, int r, int g, int b);
typedef bool (*MUCam_setRGBGainIndexUPP)(MUCam_Handle camera, int r, int g, int b);

/**
 * @brief 设置RGB各颜色通道的增益值。
 *
 * 当应用程序通过计算得到（如做白色平衡时）各个颜色通道的增益值后，则可以用本函数直接设置期望的增益值。函数会在允许的增益值列表中
 * 选择最接近的值进行设置，并返回实际选择的增益值索引。由于硬件增益不能保证最终得到的图像颜色值被精确缩放到指定倍数，所以可以进行
 * 多次设置来得到理想的效果。一般3次测量设置即可满足要求。可以参考如下代码：
 *
 * @code
 *
 * void doWhiteBalance()
 * {
 *   ROI = getROI();
 *   for (1 to 3)
 *   {
 *     Image = grabImage();
 *     CalculateWhiteBalance(Image, ROI, &redGain, &greenGain, &blueGain);
 *     if (redGain != 1 && greenGain != 1 && blueGain != 1)
 *     {
 *       redGain   *= gainValue[currentRedGainIndex];
 *       greenGain *= gainValue[currentGreenGainIndex];
 *       blueGain  *= gainValue[currentBlueGainIndex];
 *
 *       MUCam_setRGBGainValue(camera, redGain, greenGain, blueGain, &currentRedGainIndex, &currentGreenGainIndex, &currentBlueGainIndex);
 *     }
 *     else
 *     {
 *       break;
 *     }
 *   }
 * }
 *
 * @endcode
 *
 * 图像的白色平衡取决于光源的状态，当光源变化（如在转换物镜时调整亮度）时必须要重新计算白色平衡的增益参数。而改变曝光时间是不会改变
 * 图像的白色平衡的。所以应用程序可以用调整曝光时间来避免多次计算白色平衡。
 *
 * To set the gain value of RGB channel. When the application calculated the expected gain value(eg. do white-balance processing)
 * it could invoke the function to set the gain value directly. The function will find the closest gain value in gain value list, and
 * return the index actual set. Because the camera hardware gain is not an exact multiply calculation, the application could invoke the
 * function several times to get better result. The above-mentioned sample code shows the precedure.
 *
 * The white-balance of image is influenced by the light source. The application should re-calculate white-balance gain value after changing
 * the light source. But, the exposure time does not affect the white-balance status. It is suggested that the application adjusts exposure
 * time instead of changing the light source.
 *
 * @param camera 摄像头句柄。
 *               The camera handle.
 * @param r 期望的红色通道增益值。
 *          The expected gain value of red channel.
 * @param g 期望的绿色通道增益值。
 *          The expected gain value of green channel.
 * @param b 期望的蓝色通道增益值。
 *          The expected gain value of blue channel.
 * @param ri 整数指针，取得实际选择的红色通道增益值索引。可以为0，表示不想取得该参数。
 *           The pointer to the buffer that will receieve the gain index of red channel. Might be 0, in which case it is not used.
 * @param gi 整数指针，取得实际选择的绿色通道增益值索引。可以为0，表示不想取得该参数。
 *           The pointer to the buffer that will receieve the gain index of green channel. Might be 0, in which case it is not used.
 * @param bi 整数指针，取得实际选择的蓝色通道增益值索引。可以为0，表示不想取得该参数。
 *           The pointer to the buffer that will receieve the gain index of blue channel. Might be 0, in which case it is not used.
 * @return 是否成功设置该参数。
 *         Returns true if successful; false if failed.
 *
 * @attention 某些摄像头不支持RGB各个通道使用不同的增益值，所以只有在输入参数都相同的情况下才会成功执行。
 *            Some camears don't support different gain values in RGB channels. The function will return true only when the parameters r,g,b are the same.
 *
 * @see MUCam_getGainCount, MUCam_setRGBGainIndex
 */
typedef bool (*MUCam_setRGBGainValueUPP)(MUCam_Handle camera, float r, float g, float b, int *ri, int *gi, int *bi);


/**
 * @brief 取得摄像头当前状态下支持的曝光参数闭区间范围。
 *
 * 设置Binning和ROI都有可能会改变当前的曝光范围，应用程序在做上述操作后，要及时检查，以保证用户界面的对应。
 *
 * To get the current exposure time range of the camera. The setting of binning and ROI will change the exposure time range, the application
 * should have a check after finishing these settings.
 *
 * @param camera 摄像头句柄。
 *               The camera handle.
 * @param min 浮点数指针，用于保存当前的曝光最小值（多数摄像头是以毫秒为单位，MC1002/2002是无单位的数值）。
 *            The pointer to the buffer that will receieve the minimum exposure value. For MC1002/2002, it's a value without unit; for other
 *            camera, it's based on millisecond.
 * @param max 浮点数指针，用于保存当前的曝光最大值（多数摄像头是以毫秒为单位，MC1002/2002是无单位的数值）。
 *            The pointer to the buffer that will receieve the maximum exposure value. For MC1002/2002, it's a value without unit; for other
 *            camera, it's based on millisecond.
 * @return 是否成功取得参数范围。
 *         Returns true if successful; false if failed.
 *
 * @see MUCam_setBinningIndex, MUCam_setROI
 */
//bool MUCam_getExposureRange(MUCam_Handle camera, float *min, float *max);
typedef bool (*MUCam_getExposureRangeUPP)(MUCam_Handle,float *,float *);
    
/**
 * @brief 设置摄像头曝光参数。
 *
 * To set the exposure time of the camera.
 *
 * @attention 曝光参数必须在设置过Binning或者ROI之后才能被设置，即打开摄像头后要先设置图像尺寸后再设置曝光参数。
 *            The exposure time shuld be set after setting binning or ROI.
 *
 * @param camera 摄像头句柄。
 *               The camera handle.
 * @param t 曝光参数（多数摄像头是以毫秒为单位，MC1002/2002是无单位的数值）。如果该值超过当前许可的范围，则不改变当前值，函数返回false。
 *          The exposure time.
 * @return 是否成功设置该参数。
 *         Returns true if successful; false if failed.
 *
 * @see MUCam_getExposureRange, MUCam_setBinningIndex, MUCam_setROI
 */
//bool MUCam_setExposure(MUCam_Handle camera, float t);
typedef bool (*MUCam_setExposureUPP)(MUCam_Handle, float);

#ifdef __cplusplus
}
#endif
#pragma GCC visibility pop

#endif // __MOTIC_UCAM_H__
