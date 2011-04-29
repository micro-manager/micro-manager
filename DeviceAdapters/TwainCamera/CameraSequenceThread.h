
class CameraSequenceThread : public MMDeviceThreadBase
{
   enum { default_numImages=1, default_intervalMS = 100 };
public:
   CameraSequenceThread(ThisCameraType* pCam)
      :intervalMs_(default_intervalMS)
      ,numImages_(default_numImages)
      ,imageCounter_(0)
      ,stop_(true)
      ,suspend_(false)
      ,camera_(pCam)
      ,startTime_(0)
      ,actualDuration_(0)
      ,lastFrameTime_(0)
   {};

   ~CameraSequenceThread() {}

   void Stop() {
      MMThreadGuard(this->stopLock_);
      stop_=true;
   }

   void Start(long numImages, double intervalMs)
   {
      MMThreadGuard(this->stopLock_);
      MMThreadGuard(this->suspendLock_);
      numImages_=numImages;
      intervalMs_=intervalMs;
      imageCounter_=0;
      stop_ = false;
      suspend_=false;
      activate();
      actualDuration_ = 0;
      startTime_= camera_->GetCurrentMMTime();
      lastFrameTime_ = 0;
   }
   bool IsStopped(){
      MMThreadGuard(this->stopLock_);
      return stop_;
   }
   void Suspend() {
      MMThreadGuard(this->suspendLock_);
      suspend_ = true;
   }
   bool IsSuspended() {
      MMThreadGuard(this->suspendLock_);
      return suspend_;
   }
   void Resume() {
      MMThreadGuard(this->suspendLock_);
      suspend_ = false;
   }
   double GetIntervalMs(){return intervalMs_;}
   void SetLength(long images) {numImages_ = images;}
   long GetLength() const {return numImages_;}

   long GetImageCounter(){return imageCounter_;}
   MM::MMTime GetStartTime(){return startTime_;}
   MM::MMTime GetActualDuration(){return actualDuration_;}

private:
   int svc(void) throw()
   {
      int ret=DEVICE_ERR;
      try 
      {
         do
         {  
            ret=camera_->ThreadRun();
         } while (DEVICE_OK == ret && !IsStopped() && imageCounter_++ < numImages_-1);
         if (IsStopped())
            camera_->LogMessage("SeqAcquisition interrupted by the user\n");
      }catch(...){
         camera_->LogMessage(g_Msg_EXCEPTION_IN_THREAD, false);
      }
      stop_=true;
      actualDuration_ = camera_->GetCurrentMMTime() - startTime_;
      camera_->OnThreadExiting();
      return ret;
   }
protected:
   ThisCameraType* camera_;
   bool stop_;
   bool suspend_;
   long numImages_;
   long imageCounter_;
   double intervalMs_;
   MM::MMTime startTime_;
   MM::MMTime actualDuration_;
   MM::MMTime lastFrameTime_;
   MMThreadLock stopLock_;
   MMThreadLock suspendLock_;
};


