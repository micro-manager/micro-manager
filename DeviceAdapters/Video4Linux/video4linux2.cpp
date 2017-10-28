/* This is a basic camera device adapter for micromanager.  It reads
 * out a video4linux device from /dev/video0 using the V4L2 api. I
 * used a Logitech QuickCam Pro 9000 but any modern USB webcam should
 * work. For now I fixed the resolution to 640x480 and data type to
 * YUV. In the beginning the V4L2 api is fed with several buffers that
 * are filled with images. Every time an image is requested with some
 * ioctl you will get a pointer to that array that you have to hand
 * back when you are finished.  I didn't bother to figure out a way
 * how that fits in with Micromanager so during SnapImage I just copy
 * the data into an the array image and hand back the original
 * array. GetImageBuffer returns a pointer to copied data.
 *
 * 2010-03-09 Martin Kielhorn
 *
 * to compile and install run the following three commands:
export MM=/home/martin/src/mm0121/micromanager1.3
g++ -g -Wall -W -fPIC -shared -o libmmgr_dal_video4linux2.so -I$MM video4linux2.cpp $MM/MMDevice/.libs/libMMDevice.a -lm
sudo cp libmmgr_dal_video4linux2.so /usr/lib/micro-manager/libmmgr_dal_video4linux2.so.0
*
* for debugging:
export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:/usr/lib/micro-manager
cd /usr/share/imagej
strace -f java -Djava.library.path=/usr/lib/micro-manager -classpath /usr/share/imagej/plugins/bsh-2.0b4.jar:/usr/share/imagej/ij.jar:/usr/share/imagej/plugins/Micro-Manager/MMJ_.jar org/micromanager/MMStudioMainFrame
*
* Modified:
*
* 2019-02-04 Benjamin Peter
*
*            - Adding support for RGB image capturing
*            - Allow device path to be configured using a property. Reinit the device if it changes.
*            - Allow the resolution to be configured using properties.
*            - Used tryioctl function to honor busy state of devices and retry the action.
*            - Call v4l2 capability negotiation api to get more detailed error messages in case of
*              incompatible devices.
*            - Unify logging and leverage errno value of ioctl calls.
*            - Move some functions into the device class as they are actually accessing it's state
*            - Rearranged functions to follow the call flow better (init, prepare, read, close, ...)
*            - Tested using
*              - USB ID 1871:7670 Aveo Technology Corp. (uvcvideo) - COLEMETER(R) USB 2.0 Digital Microscope
*              - USB ID 046d:0826 Logitech, Inc. HD Webcam C525
*
*/
// LICENSE:       This file is distributed under the "LGPL" license.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.


#include <iostream>
#include <string>
#include <math.h>
#include "../../MMDevice/MMDevice.h"
#include "../../MMDevice/DeviceBase.h"
#include "../../MMDevice/ModuleInterface.h"
#include "../../MMDevice/ImgBuffer.h"
#include <sstream>
#include <map>
#include <vector>

#include <sys/ioctl.h>
#include <linux/videodev2.h>
#include <cstdio>
#include <fcntl.h>
#include <unistd.h>
#include <assert.h>
#include <cstdlib>
#include <cstring>
#include <cerrno>
#include <sys/mman.h>

#include <pthread.h>

using namespace std;

const char
  *gName="Video4Linux2",
  *gDescription="video4linux2 camera device adapter",
  *gPropertyDevicePath = "DevicePath",
  *gPropertyDevicePathDefault = "/dev/video0",
  *gPropertyNameResolution = "Resolution",
  *gResolutionDefault = "640x480";

const long gWidthDefault = 640,
           gHeightDefault = 480;

struct VidBuffer {
  void *start;
  size_t length;
};

// v4l2 state
typedef struct State State;
struct State {
  int W, H, fd;
  struct VidBuffer *buffers;
  unsigned int buffers_count;
  struct v4l2_buffer *buf;
};

class PixelType {
  public:
    PixelType(string propertyValue, unsigned bytesPerPixel, unsigned numberOfComponents, unsigned bitDepth) :
      m_propertyValue(propertyValue),
      m_bytesPerPixel(bytesPerPixel),
      m_numberOfComponents(numberOfComponents),
      m_bitDepth(bitDepth) {
      }

    string GetPropertyValue() const { return m_propertyValue; }
    unsigned GetImageBytesPerPixel() const { return m_bytesPerPixel; }
    unsigned GetBitDepth() const { return m_bitDepth; }
    unsigned GetNumberOfComponents() const { return m_numberOfComponents; }

    virtual void convertV4l2ToOutput(
        State *state, unsigned char* in, unsigned char* output) const = 0;
  private:
    string m_propertyValue;
    unsigned m_bytesPerPixel;
    unsigned m_bitDepth;
    unsigned m_numberOfComponents;
};

class PixelType8Bit : public PixelType {
  public:
    static string PROPERTY_VALUE;

    PixelType8Bit() :
      PixelType(PROPERTY_VALUE, 1, 1, 8) {
      }

    virtual void convertV4l2ToOutput(
        State *state, unsigned char* in, unsigned char* output) const {
      int i,j;
      for (j = 0; j < state->H; j++) {
        int wj = state->W * j;
        for (i = 0; i < state->W; i++) {
          output[i + wj] = in[2*i + 2*wj];
        }
      }
    }
};
string PixelType8Bit::PROPERTY_VALUE = "8bit";
PixelType8Bit PIXELTYPE_8BIT;

class PixelTypeYUYV : public PixelType {
  public:
    static string PROPERTY_VALUE;

    PixelTypeYUYV() :
      PixelType(PROPERTY_VALUE, 4, 4, 16) {
      }

    virtual void convertV4l2ToOutput(
        State *state, unsigned char* ptrIn, unsigned char* ptrOut) const {
      /* Convert YUYV to RGBA32, apparently mm does only display colors
       * in this format */
      for (int i = 0;  i < state->W * state->H / 2; ++i) {
        int y0 = ptrIn[0];
        int u0 = ptrIn[1];
        int y1 = ptrIn[2];
        int v0 = ptrIn[3];
        ptrIn += 4;
        int c = y0 - 16;
        int d = u0 - 128;
        int e = v0 - 128;

        ptrOut[0] = clip((298 * c + 516 * d + 128) >> 8); // blue
        ptrOut[1] = clip((298 * c - 100 * d - 208 * e + 128) >> 8); // green
        ptrOut[2] = clip((298 * c + 409 * e + 128) >> 8); // red
        ptrOut[4] = 255; // alpha
        c = y1 - 16;
        ptrOut[4] = clip((298 * c + 516 * d + 128) >> 8); // blue
        ptrOut[5] = clip((298 * c - 100 * d - 208 * e + 128) >> 8); // green
        ptrOut[6] = clip((298 * c + 409 * e + 128) >> 8); // red
        ptrOut[7] = 255; // alpha
        ptrOut += 8;
      }
    }

  private:
    inline unsigned char clip(int val) const {
      if (val <= 0)
        return 0;
      else if (val >= 255)
        return 255;
      else
        return val;
    }
};
string PixelTypeYUYV::PROPERTY_VALUE = "YUYV";
PixelTypeYUYV PIXELTYPE_YUYV;

class V4L2 : public CCameraBase<V4L2>
{
public:

  // set all variables to default values, create only necessary device
  // properties we need for defining initialisation parameters, do as
  // little as possible, don't access hardware, do everything else in
  // Initialize()
  V4L2() :
    pixelType(&PIXELTYPE_8BIT)
  {
    initialized_ = 0;
  }

  // Shutdown is always called before destructor, in any case release
  // all resources even if Shutdown wasn't called
  ~V4L2()
  {
    Shutdown();
  }

  // access hardware, create device properties
  int Initialize()
  {
    if(initialized_)
      return DEVICE_OK;
    LogMessage("initializing device driver");

    CreateProperty(MM::g_Keyword_Name, gName, MM::String, true);

    CreateProperty(MM::g_Keyword_Description, gDescription, MM::String, true);

    // Device Path
    CPropertyAction* pAct = new CPropertyAction(this, &V4L2::OnDevicePath);
    int nRet = CreateProperty(
        gPropertyDevicePath, gPropertyDevicePathDefault, MM::String, false, pAct);
    if (nRet != DEVICE_OK)
      return nRet;

    // Resolution
    pAct = new CPropertyAction(this, &V4L2::OnResolutionChange);
    nRet = CreateProperty(
        gPropertyNameResolution, gResolutionDefault, MM::String, false, pAct);
    if (nRet != DEVICE_OK)
      return nRet;

    // Binning
    pAct = new CPropertyAction(this, &V4L2::OnBinning);
    nRet = CreateProperty(MM::g_Keyword_Binning, "1", MM::Integer, false, pAct);
    if (nRet != DEVICE_OK)
      return nRet;

    // Pixel type
    pAct = new CPropertyAction(this, &V4L2::OnPixelType);
    nRet = CreateProperty(MM::g_Keyword_PixelType,
			  PixelTypeYUYV::PROPERTY_VALUE.c_str(), MM::String, false, pAct);
    if (nRet != DEVICE_OK)
       return nRet;

    vector<string> pixTypes;
    pixTypes.push_back(PixelType8Bit::PROPERTY_VALUE);
    pixTypes.push_back(PixelTypeYUYV::PROPERTY_VALUE);
    nRet = SetAllowedValues(MM::g_Keyword_PixelType, pixTypes);
    if (nRet != DEVICE_OK)
       return nRet;

    // Gain
    pAct = new CPropertyAction(this, &V4L2::OnGain);
    nRet = CreateProperty(MM::g_Keyword_Gain,
			  "0", MM::Integer, false, pAct);
    assert(nRet == DEVICE_OK);

    // Exposure
    pAct = new CPropertyAction(this, &V4L2::OnExposure);
    nRet = CreateProperty(MM::g_Keyword_Exposure, "0.0", MM::Float, false, pAct);
    assert(nRet == DEVICE_OK);
    
    LogMessage("calling video init");
    if (VideoInit()) {
      initialized_ = true;
      return DEVICE_OK;
    }
    else {
      initialized_ = false;
      return DEVICE_ERR;
    }
  }

  // Shutdown is called multiple times, Initialize will be called
  // afterwards, unload device, release all resources
  int Shutdown()
  {
    if (initialized_) {
      VideoClose();
    }
    initialized_ = false;
    return DEVICE_OK;
  }
  
  void GetName(char*name) const
  {
    CDeviceUtils::CopyLimitedString(name,gName);
  }

  // blocks until exposure is finished
  int SnapImage()
  {
    unsigned char* data = VideoTakeBuffer();
    pixelType->convertV4l2ToOutput(state, data, const_cast<unsigned char*>(imageBuffer.GetPixels()));
    VideoReturnBuffer();
    return DEVICE_OK;
  }

  // waits for camera readout
  const unsigned char* GetImageBuffer()
  {
    return imageBuffer.GetPixels();
  }

  // changes only if binning, pixel type, ... properties are set
  unsigned GetImageWidth() const {return imageBuffer.Width();}
  unsigned GetImageHeight() const {return imageBuffer.Height();}
  unsigned GetImageBytesPerPixel() const {return imageBuffer.Depth();} 
  long GetImageBufferSize() const {return GetImageWidth() * GetImageHeight() * GetImageBytesPerPixel();}
  unsigned GetBitDepth() const { return pixelType->GetBitDepth(); }
  unsigned GetNumberOfComponents() const { return pixelType->GetNumberOfComponents(); }

  int SetROI(unsigned x,unsigned y,unsigned xSize,unsigned ySize)
  {
    (void) x; // get rid of warning
    (void) y;
    (void) xSize;
    (void) ySize;
    // FIXME
    // set_roi(x,y,xSize,ySize);
    return DEVICE_OK;
  }

  int GetBinning() const 
  {
    // FIXME
    return 1;// get_binning();
  }
  
  int SetBinning(int binSize)
  {
    // FIXME  set_binning(binSize);
    SetProperty(MM::g_Keyword_Binning, CDeviceUtils::ConvertToString(binSize));
    return DEVICE_OK;
  }

  double GetExposure() const 
  {
    // FIXME
    return 1.0; //get_exposure();
  }

  void SetExposure(double exp)
  {
    // FIXME
    // set_exposure(exp);
    SetProperty(MM::g_Keyword_Exposure, CDeviceUtils::ConvertToString(exp));
  }

  int GetROI(unsigned&x,
	     unsigned&y,
	     unsigned&xSize,
	     unsigned&ySize)
  {
    // FIXME
    // get_roi(&x,&y,&xSize,&ySize);
    x=0; y=0; xSize=state->W; ySize=state->H;
    return DEVICE_OK;
  }

  int ClearROI()
  {
    // FIXME
    //clear_roi();
    return DEVICE_OK;
  }
  
  // action interface
  int OnExposure(MM::PropertyBase* pProp, MM::ActionType eAct)
  {
    if(eAct == MM::BeforeGet){
      //pProp->Set(get_exposure()); // FIXME
      //on_exposure();
    }else if(eAct==MM::AfterSet){
      double exp;
      pProp->Get(exp);
      //set_exposure(exp); // FIXME
    }
    return DEVICE_OK;
  }

  int OnDevicePath(MM::PropertyBase* pProp, MM::ActionType eAct)
  {
    if (eAct == MM::AfterSet) {
      if (IsCapturing())
         return DEVICE_CAMERA_BUSY_ACQUIRING;

      ostringstream msg;
      string devicePath;
      pProp->Get(devicePath);

      msg << "device path changed to " << devicePath;
      LogMessage(msg.str());
      reinitializeDeviceIfRunning();
    }

    return DEVICE_OK;
  }

  int OnResolutionChange(MM::PropertyBase* pProp, MM::ActionType eAct)
  {
    if (eAct == MM::AfterSet) {
      if (IsCapturing())
         return DEVICE_CAMERA_BUSY_ACQUIRING;

      ostringstream msg;
      string devicePath;
      pProp->Get(devicePath);

      msg << "resolution changed to " << devicePath;
      LogMessage(msg.str());
      reinitializeDeviceIfRunning();
    }

    return DEVICE_OK;
  }

  int OnBinning(MM::PropertyBase* pProp, MM::ActionType eAct)
  {
    if(eAct == MM::BeforeGet){
      //on_binning(); // FIXME
    }else if(eAct==MM::AfterSet){
    }
    return DEVICE_OK;
  }
  
  int OnPixelType(MM::PropertyBase* pProp, MM::ActionType eAct)
  {
    if (eAct == MM::AfterSet)
    {
      if (IsCapturing())
         return DEVICE_CAMERA_BUSY_ACQUIRING;

      string pixType;
      pProp->Get(pixType);
      if (pixType == PixelType8Bit::PROPERTY_VALUE) {
        pixelType = &PIXELTYPE_8BIT;
      }
      else if (pixType == PixelTypeYUYV::PROPERTY_VALUE) {
        pixelType = &PIXELTYPE_YUYV;
      }
      else {
        return DEVICE_INVALID_PROPERTY;
      }
  
      LogMessage("setting pixelType " + pixelType->GetPropertyValue());
      return this->resizeBuffer();
    }
    else if (eAct == MM::BeforeGet)
    {
      pProp->Set(pixelType->GetPropertyValue().c_str());
    }
    return DEVICE_OK;
  }

  int OnGain(MM::PropertyBase* pProp, MM::ActionType eAct)
  {
    if(eAct == MM::BeforeGet){
      //on_gain(); // FIXME
    }else if(eAct==MM::AfterSet){
    }
    return DEVICE_OK;
  }

  /**
   * TODO: implement if possible
   */
  int IsExposureSequenceable(bool& isSequenceable) const 
  {
     isSequenceable = false; 
     return DEVICE_OK;
  }
  
private:

  bool
  VideoInit()
  {
    char devicePath[MM::MaxStrLength];
    int ret = GetProperty(gPropertyDevicePath, devicePath);
    if (ret != DEVICE_OK) {
      LogMessage("could not read device path property");
      return false;
    }

    char resolutionString[MM::MaxStrLength];
    ret = GetProperty(gPropertyNameResolution, resolutionString);
    if (ret != DEVICE_OK) {
      LogMessage("could not read device resolution property");
      return false;
    }

    long requestedWidth = gWidthDefault;
    long requestedHeight = gHeightDefault;
    ret = parseResolution(resolutionString, requestedWidth, requestedHeight);
    if (ret != DEVICE_OK) {
      LogMessage("could not parse device resolution property value");
      return false;
    }

    ret = initDevice(devicePath, requestedWidth, requestedHeight);
    if (ret != DEVICE_OK)
      return false;

    struct v4l2_requestbuffers reqbuf;
    memset(&reqbuf, 0, sizeof(reqbuf));
    reqbuf.type   = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    reqbuf.memory = V4L2_MEMORY_MMAP;
    reqbuf.count = 4;

    if (-1 == tryIoctl(state->fd, VIDIOC_REQBUFS, &reqbuf)) {
      ostringstream msg;
      if (EINVAL == errno) {
        msg << "error: the device does not support memory mapping";
      }
      else {
        msg << "error: could not request memory map buffers: "
            << strerror(errno) << " (errno " << errno << ")";
      }
      LogMessage(msg.str().c_str());
      return false;
    }

    ostringstream bufMsg;
    bufMsg << "got " << reqbuf.count << " out of 4 requested buffers";
    LogMessage(bufMsg.str().c_str());

    state->buffers = (struct VidBuffer*)calloc(reqbuf.count, sizeof(*(state->buffers)));
    if (!state->buffers) {
      LogMessage("could not allocate buffer(s)");
      return false;
    }

    state->buf = (struct v4l2_buffer*) malloc(sizeof(struct v4l2_buffer));

    unsigned int i;
    for (i = 0; i < reqbuf.count; i++) {
      struct v4l2_buffer buf;
      memset(&buf, 0 , sizeof(buf));

      buf.type = reqbuf.type;
      buf.memory = V4L2_MEMORY_MMAP;
      buf.index = i;
      if (-1 == tryIoctl(state->fd, VIDIOC_QUERYBUF, &buf)) {
        LogMessage("could not query the buffer state");
        return false;
      }

      state->buffers[i].length = buf.length; // remember for munmap
      state->buffers[i].start = mmap(NULL, buf.length,
          PROT_READ | PROT_WRITE,
          MAP_SHARED, state->fd, buf.m.offset);

      if (state->buffers[i].start == MAP_FAILED) {
        LogMessage("memory map failed");
        return false;
      }

      if (-1 == tryIoctl(state->fd, VIDIOC_QBUF, &buf)) {
        LogMessage("could not enqueue buffer");
        return false;
      }
    }

    state->buffers_count = reqbuf.count;

    ret = this->resizeBuffer();
    if (ret != DEVICE_OK)
      return false;

    int type = V4L2_BUF_TYPE_VIDEO_CAPTURE; 
    if (-1 == tryIoctl(state->fd, VIDIOC_STREAMON, &type)) {
      LogMessage("could not initialize stream");
      return false;
    }

    LogMessage("initialized data stream");
    return true;
  }

  int
  initDevice(const char* devicePath, long requestedWidth, long requestedHeight)
  {
    struct v4l2_capability cap;
    struct v4l2_format fmt;

    state->fd = open(devicePath, O_RDWR);
  
    if (-1 == state->fd) {
      LogMessage("could not open the video device");
      return false;
    }
    LogMessage("opened device");

    if (-1 == tryIoctl(state->fd, VIDIOC_QUERYCAP, &cap)) {
      ostringstream msg;
      if (EINVAL == errno) {
        msg << "error: device is not a v4l2 device";
      } else {
        msg << "error: could not query v4l2 capabilities: " << strerror(errno);
      }
      LogMessage(msg.str().c_str());
      return DEVICE_ERR;
    }

    if (!(cap.capabilities & V4L2_CAP_VIDEO_CAPTURE)) {
      ostringstream msg;
      msg << "error: device is not a v4l2 capture device";
      LogMessage(msg.str().c_str());
      return DEVICE_ERR;
    }

    memset(&fmt, 0, sizeof(fmt));

    fmt.type                = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    fmt.fmt.pix.pixelformat = V4L2_PIX_FMT_YUYV;
    fmt.fmt.pix.field       = V4L2_FIELD_INTERLACED;
    fmt.fmt.pix.height      = (unsigned) requestedWidth;
    fmt.fmt.pix.width       = (unsigned) requestedHeight;

    if (-1 == tryIoctl(state->fd, VIDIOC_S_FMT, &fmt)) {
      ostringstream msg;
      msg << "error: could not set format YUYV: " << strerror(errno);
      LogMessage(msg.str().c_str());
      return DEVICE_ERR;
    }

    if (fmt.fmt.pix.width != requestedWidth) {
      ostringstream msg;
      msg << "warning: device did not match requested pixel width: "
          << fmt.fmt.pix.width << " requested: " << requestedWidth;
      LogMessage(msg.str().c_str());
      // not necessarily fatal
    }

    if (fmt.fmt.pix.height != requestedHeight) {
      ostringstream msg;
      msg << "warning: device did not match requested pixel height: "
          << fmt.fmt.pix.height << " requested: " << requestedHeight;
      LogMessage(msg.str().c_str());
      // not necessarily fatal
    }

    state->W = fmt.fmt.pix.width;
    state->H = fmt.fmt.pix.height;

    ostringstream formatMsg;
    formatMsg << "device is configured for " << state->W << "x" << state->H << " pixel"
              << " and " << fmt.fmt.pix.bytesperline << " bytes per line ("
              << (fmt.fmt.pix.bytesperline / state->H) <<" bpp)";
    LogMessage(formatMsg.str().c_str());
    return DEVICE_OK;
  }

  bool
  VideoClose()
  {
    int type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    if (-1 == tryIoctl(state->fd, VIDIOC_STREAMOFF, &type)) {
      ostringstream msg;
      msg << "error setting streamoff: " << strerror(errno);
      LogMessage(msg.str().c_str());
      return false;
    }
  
    unsigned int i;
    for (i = 0; i < state->buffers_count; i++)
      munmap(state->buffers[i].start, state->buffers[i].length);
    close(state->fd);
    free(state->buf);
  
    state->fd = 0;
    state->W = 0;
    state->H = 0;
    state->buffers_count = 0;
    state->buffers = 0;
  
    return true;
  }

  /*  has to be followed by a call to videoreturnbuffer */
  unsigned char*
  VideoTakeBuffer()
  {
    memset(state->buf, 0, sizeof(struct v4l2_buffer));
    state->buf->type = V4L2_BUF_TYPE_VIDEO_CAPTURE;
    state->buf->memory = V4L2_MEMORY_MMAP;
    // By default VIDIOC_DQBUF blocks when no buffer is in the outgoing queue
    if (-1 == tryIoctl(state->fd, VIDIOC_DQBUF, state->buf)) {
      ostringstream msg;
      msg << "error: could not prepare next image buffer: " << strerror(errno);
      LogMessage(msg.str().c_str());
      assert(false);
    }

    assert(state->buf->index < state->buffers_count);
    return (unsigned char*)state->buffers[state->buf->index].start;
  }
  
  void
  VideoReturnBuffer()
  {
    if (-1 == tryIoctl(state->fd, VIDIOC_QBUF, state->buf)) {
      ostringstream msg;
      msg << "error: could not get next image buffer: " << strerror(errno);
      LogMessage(msg.str().c_str());
      return;
    }
  }

  int reinitializeDeviceIfRunning() {
    if (initialized_) {
      LogMessage("closing current device");
      if (! VideoClose()) {
        return DEVICE_ERR;
      }
      if (! VideoInit()) {
        return DEVICE_ERR;
      }
    }
    return DEVICE_OK;
  }
  
  int parseResolution(const char* resolutionString, long &width, long &height) {
    long parsedWidth = 0;
    long parsedHeight = 0;
    int matched = sscanf(resolutionString, "%ldx%ld", &parsedWidth, &parsedHeight);

    if (matched == 2) {
      width = parsedWidth;
      height = parsedHeight;

      return DEVICE_OK;
    }
    else if (errno != 0) {
      ostringstream msg;
      msg << "error: could not parse resolution: " << strerror(errno);
      LogMessage(msg.str().c_str());
      return DEVICE_INVALID_PROPERTY;
    }
    else {
      LogMessage("resolution not in format 'WxH'");
      return DEVICE_INVALID_PROPERTY;
    }

    width = parsedWidth;
    height = parsedHeight;

    return DEVICE_OK;
  }
  
  int resizeBuffer()
  {
    imageBuffer.Resize(state->W, state->H, pixelType->GetImageBytesPerPixel());
    return DEVICE_OK;
  }

  int tryIoctl(int fd, unsigned long ioctlCode, void *parameter) const
  {
      while (-1 == ioctl(fd, ioctlCode, parameter)) {
          if (!(errno == EBUSY || errno == EAGAIN))
              return -1;
  
          fd_set fds;
          FD_ZERO(&fds);
          FD_SET(fd, &fds);
  
          struct timeval tv;
          tv.tv_sec = 10;
          tv.tv_usec = 0;
  
          int result = select(fd + 1, &fds, NULL, NULL, &tv);
          if (0 == result) {
            LogMessage("tryIoctl select timeout");
            return -1;
          }
          else if (-1 == result && EINTR != errno) {
            return -1;
          }
      }
      return 0;
  }

  bool initialized_;
  State state[1];
  ImgBuffer imageBuffer;
  PixelType *pixelType;
};

MODULE_API void InitializeModuleData()
{
  RegisterDevice(gName, MM::CameraDevice, gDescription);
}

MODULE_API MM::Device* CreateDevice(const char*deviceName)
{
  if(deviceName==0)
    return 0;
  if(strcmp(deviceName,gName)==0)
    return new V4L2();
  return 0;
}

MODULE_API void DeleteDevice(MM::Device*pDevice)
{
  delete pDevice;
}
