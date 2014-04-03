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
#include <sstream>
#include <map>
#include <vector>

#include <sys/ioctl.h>
#include <linux/videodev2.h>
#include <stdio.h>
#include <fcntl.h>
#include <unistd.h>
#include <assert.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>

#include <pthread.h>

using namespace std;

const char
  *gName="Video4Linux2",
  *gDescription="video4linux2 camera device adapter";

const int gWidth=640, gHeight=480;

struct VidBuffer {
  void*start;
  size_t length;
};

typedef struct State State;
struct State {
  int W,H,fd;
  struct VidBuffer *buffers;
  unsigned int buffers_count;
  struct v4l2_buffer*buf;
};


/*
int
VideoControl(State*state,int id,int dvalue)
{
  //int type=V4L2_BUF_TYPE_VIDEO_CAPTURE; assert(-1!=ioctl(state->fd,VIDIOC_STREAMOFF,&type));
 
  struct v4l2_queryctrl qctl;
  memset(&qctl,0,sizeof(qctl));
  qctl.id=id;
  if(-1==ioctl(state->fd,VIDIOC_QUERYCTRL,&qctl))
    return -1;
  if(qctl.flags & V4L2_CTRL_FLAG_DISABLED)
    printf("control is not supported\n");
 
  struct v4l2_control ctl;
  memset(&ctl,0,sizeof(ctl));
  ctl.id=qctl.id;
  if(-1==ioctl(state->fd,VIDIOC_G_CTRL,&ctl))
    return -2;
  // +=
  ctl.value=dvalue;//qctl.default_value;
  if(-1==ioctl(state->fd,VIDIOC_S_CTRL,&ctl))
    return -3;
 
  if(-1==ioctl(state->fd,VIDIOC_G_CTRL,&ctl))
    return -4;
  return ctl.value;
  // type=V4L2_BUF_TYPE_VIDEO_CAPTURE;  assert(-1!=ioctl(state->fd,VIDIOC_STREAMON,&type));
} 
*/

/*  has to be followed by a call to videoreturnbuffer */
unsigned char*
VideoTakeBuffer(State*state)
{
  fd_set fds;
  FD_ZERO(&fds);
  FD_SET(state->fd,&fds);
  struct timeval tv;
  tv.tv_sec=10;
  tv.tv_usec=0;
  assert(0<select(state->fd+1,&fds,NULL,NULL,&tv));
 
  memset(state->buf,0,sizeof(struct v4l2_buffer));
  state->buf->type=V4L2_BUF_TYPE_VIDEO_CAPTURE;
  state->buf->memory=V4L2_MEMORY_MMAP;
  // http://v4l2spec.bytesex.org/spec/r12878.htm: By default
  // VIDIOC_DQBUF blocks when no buffer is in the outgoing queue
  assert(-1!=ioctl(state->fd,VIDIOC_DQBUF,state->buf));

  assert(state->buf->index<state->buffers_count);

  return (unsigned char*)state->buffers[state->buf->index].start;
}

void
VideoReturnBuffer(State*state)
{
  assert(-1!=ioctl(state->fd,VIDIOC_QBUF,state->buf));
}

void
VideoClose(State*state)
{
  int type=V4L2_BUF_TYPE_VIDEO_CAPTURE;
  assert(-1!=ioctl(state->fd,VIDIOC_STREAMOFF,&type));
  unsigned int i;
  for(i=0;i<state->buffers_count;i++)
    munmap(state->buffers[i].start,state->buffers[i].length);
  close(state->fd);
  free(state->buf);
}


class V4L2 : public CCameraBase<V4L2>
{
public:


/**
 * TODO: implement if possible
 */
int IsExposureSequenceable(bool& isSequenceable) const 
{
   isSequenceable = false; 
   return DEVICE_OK;
}

bool
VideoInit(State*state)
{
  state->fd=open("/dev/video0",O_RDWR);
//// this should probably be a property

  if(-1==state->fd)
    {
    LogMessage("v4l2: ould not open the video device");
    return false;
    }

  sleep(3); // let it settle; there is probably an ioctl for this

  state->buf=(struct v4l2_buffer*) malloc(sizeof(struct v4l2_buffer));

  struct v4l2_format format; 
  memset(&format,0,sizeof(format));
  format.type=V4L2_BUF_TYPE_VIDEO_CAPTURE;
  format.fmt.pix.width=state->W;
  format.fmt.pix.height=state->H;
  format.fmt.pix.pixelformat=V4L2_PIX_FMT_YUYV;
  if(-1==ioctl(state->fd,VIDIOC_S_FMT,&format))
    {
    LogMessage("v4l2: could not set YUYV format");
    return false;
    }

  state->W=format.fmt.pix.width;
  state->H=format.fmt.pix.height;
  //printf("size %dx%d\n",format.fmt.pix.width,format.fmt.pix.height);
 
  struct v4l2_requestbuffers reqbuf;
  memset(&reqbuf,0,sizeof(reqbuf));
  reqbuf.type=V4L2_BUF_TYPE_VIDEO_CAPTURE;
  reqbuf.memory=V4L2_MEMORY_MMAP;
  reqbuf.count=4;
  assert(-1!=ioctl(state->fd,VIDIOC_REQBUFS,&reqbuf));
  //printf("number of buffers: %d\n",reqbuf.count);
 
  state->buffers=(struct VidBuffer*)calloc(reqbuf.count,sizeof(*(state->buffers)));
  if(!state->buffers)
    {
    LogMessage("v4l2: could not allocate buffer(s)");
    return false;
    }
  unsigned int i;
  for(i=0;i<reqbuf.count;i++){
    struct v4l2_buffer buf;
    memset(&buf,0,sizeof(buf));
 
    buf.type=reqbuf.type;
    buf.memory=V4L2_MEMORY_MMAP;
    buf.index=i;
    if(-1==ioctl(state->fd,VIDIOC_QUERYBUF,&buf))
      {
      LogMessage("v4l2: could not query the buffer state");
      return false;
      }
 
    state->buffers[i].length=buf.length; // remember for munmap
    state->buffers[i].start=mmap(NULL,buf.length,
			  PROT_READ|PROT_WRITE,
			  MAP_SHARED,state->fd,buf.m.offset);
    if(state->buffers[i].start==MAP_FAILED)
      {
      LogMessage("v4l2: memory map failed");
      return false;
      }
 
    if(-1==ioctl(state->fd,VIDIOC_QBUF,&buf))
      {
      LogMessage("v4l2: could not enqueue buffer");
      return false;
      }
  }
  state->buffers_count=reqbuf.count;
  int type=V4L2_BUF_TYPE_VIDEO_CAPTURE; 
  if(-1==ioctl(state->fd,VIDIOC_STREAMON,&type))
    {
    LogMessage("v4l2: could not initialize stream");
    return false;
    }
  return true;
}







  // set all variables to default values, create only necessary device
  // properties we need for defining initialisation parameters, do as
  // little as possible, don't access hardware, do everything else in
  // Initialize()
  V4L2()
  {
    initialized_=0;
    image=NULL;
    state->W=gWidth;
    state->H=gHeight;
  }

  // Shutdown is always called before destructor, in any case release
  // all resources even if Shutdown wasn't called
  ~V4L2()
  {
    Shutdown();
    if(image)
      {
      free(image);
      image=NULL;
      }
  }

  // access hardware, create device properties
  int Initialize()
  {
    if(initialized_)
      return DEVICE_OK;
    CreateProperty(MM::g_Keyword_Name,gName,
		   MM::String, true);
    CreateProperty(MM::g_Keyword_Description, gDescription,
		   MM::String, true);
    // Binning
    CPropertyAction* pAct =
      new CPropertyAction(this, &V4L2::OnBinning);
    int nRet = CreateProperty(MM::g_Keyword_Binning,
			      "1", MM::Integer, false, pAct);
    if (nRet != DEVICE_OK)
      return nRet;
    // Pixel type
    pAct = new CPropertyAction (this, &V4L2::OnPixelType);
    nRet = CreateProperty(MM::g_Keyword_PixelType,
			  "8bit", MM::String,true, pAct);
    if (nRet != DEVICE_OK)
      return nRet;
    // Gain
    pAct = new CPropertyAction (this, &V4L2::OnGain);
    nRet = CreateProperty(MM::g_Keyword_Gain,
			  "0", MM::Integer, false, pAct);
    assert(nRet == DEVICE_OK);
    // Exposure
    pAct = new CPropertyAction (this, &V4L2::OnExposure);
    nRet = CreateProperty(MM::g_Keyword_Exposure,
			  "0.0", MM::Float, false, pAct);
    assert(nRet == DEVICE_OK);
    
    image = (unsigned char*) malloc(state->W*state->H);
    if(VideoInit(state))
      {
      initialized_=true;
      return DEVICE_OK;
      }
    else
      {
      initialized_=false;
      return DEVICE_ERR;
      }
    //VideoRunThread(state,image);
  }

  // Shutdown is called multiple times, Initialize will be called
  // afterwards, unload device, release all resources
  int Shutdown(){
    if(initialized_){
      VideoClose(state);
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
    unsigned char*data=VideoTakeBuffer(state);
    int i,j;
    for(j=0;j<state->H;j++){
      int wj=state->W*j;
      for(i=0;i<state->W;i++){
	image[i+wj]=data[2*i+2*wj];
      }
    }
    VideoReturnBuffer(state);
    return DEVICE_OK;
  }

  // waits for camera readout
  const unsigned char* GetImageBuffer()
  {
    return image;
  }

  // changes only if binning, pixel type, ... properties are set
  long GetImageBufferSize() const
  {
    return state->W*state->H;
  }

  unsigned GetImageWidth() const 
  {
    return state->W;
  }

  unsigned GetImageHeight() const 
  {
    return state->H;
  }

  unsigned GetImageBytesPerPixel() const
  {
    // FIXME
    return 1; //get_image_bytes_per_pixel();
  }

  unsigned GetBitDepth() const 
  {
    // FIXME
    return 8;// get_bit_depth();
  }

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
    SetProperty(MM::g_Keyword_Binning,
		CDeviceUtils::ConvertToString(binSize));
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
    SetProperty(MM::g_Keyword_Exposure,
		CDeviceUtils::ConvertToString(exp));
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
  
private:
  bool initialized_;
  State state[1];
  unsigned char *image;
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
