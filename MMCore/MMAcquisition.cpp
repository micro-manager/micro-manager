#include "MMAcquisition.h"
#include "../MMDevice/ImageMetadata.h"
#include "boost/foreach.hpp"


///////////////////////////////////
// MMAquisitionEngine::ImageTask //
///////////////////////////////////

ImageTask::ImageTask(MMAcquisitionEngine * eng, ImageRequest imageRequest) {
   eng_ = eng;
   imageRequest_ = imageRequest;
   type = IMAGE;
}

void ImageTask::run() {
   if (!eng_->StopHasBeenRequested())
      updatePosition();
   if (!eng_->StopHasBeenRequested())
      updateSlice();
   if (!eng_->StopHasBeenRequested())
      updateChannel();
   if (!eng_->StopHasBeenRequested())
      wait();
   if (!eng_->StopHasBeenRequested())
      autofocus();
   if (!eng_->StopHasBeenRequested())
      acquireImage();

}

void ImageTask::updateSlice() {
   double chanZOffset = 0;
   double sliceZ;

   if (imageRequest_.useChannel)
      chanZOffset = imageRequest_.channel.zOffset;

   if (imageRequest_.useSlice)
      sliceZ = imageRequest_.slicePosition;
   else
      eng_->coreCallback_->GetFocusPosition(sliceZ);

   if (imageRequest_.useSlice || (chanZOffset != 0)) {
      eng_->coreCallback_->SetFocusPosition(sliceZ + chanZOffset);
      eng_->core_->logMessage("z position set\n");
   }
}

void ImageTask::updatePosition() {
   if (imageRequest_.usePosition) {
      map<string, double>::iterator it1;

      MultiAxisPosition pos = imageRequest_.multiAxisPosition;
      for (it1 = pos.singleAxisPositions.begin(); it1 != pos.singleAxisPositions.end(); ++it1) {
         eng_->core_->setPosition(it1->first.c_str(), it1->second);
      }

      map<string, pair<double, double> >::iterator it2;

      for (it2 = pos.doubleAxisPositions.begin(); it2 != pos.doubleAxisPositions.end(); ++it2) {
         point2D xy = it2->second;
         eng_->core_->setXYPosition(it2->first.c_str(), xy.first, xy.second);
      }
      eng_->core_->logMessage("position set\n");
   }
}

void ImageTask::updateChannel() {
   if (imageRequest_.useChannel) {
      eng_->coreCallback_->SetExposure(imageRequest_.channel.exposure);
      imageRequest_.exposure = imageRequest_.channel.exposure;
      string chanGroup = imageRequest_.channel.group;
      if (chanGroup.size() == 0)
         chanGroup = eng_->core_->getChannelGroup();
      eng_->coreCallback_->SetConfig(chanGroup.c_str(), imageRequest_.channel.name.c_str());
      eng_->core_->logMessage("channel set\n");
   }
}

void ImageTask::wait() {
   if (imageRequest_.useTime) {
      while (!eng_->StopHasBeenRequested() && eng_->lastWakeTime_ > 0) {
         MM::MMTime sleepTime = min(MM::MMTime(10000), (eng_->lastWakeTime_ + imageRequest_.waitTime) - eng_->coreCallback_->GetCurrentMMTime());
         if (sleepTime > MM::MMTime(0, 0))
            eng_->coreCallback_->Sleep(NULL, sleepTime.getMsec());
         else
            break;
      }
      eng_->core_->logMessage("wait finished\n");

      eng_->lastWakeTime_ = eng_->coreCallback_->GetCurrentMMTime();
   }
}

void ImageTask::autofocus() {
   if (imageRequest_.runAutofocus && imageRequest_.channelIndex == 0 && imageRequest_.positionIndex == 0)
      eng_->core_->fullFocus();
}

void ImageTask::acquireImage() {
   int w, h, d;

   if (!eng_->core_->getShutterOpen()) {
      eng_->core_->setShutterOpen(true);
      eng_->core_->logMessage("opened shutter");
   }
   eng_->core_->snapImage();
   eng_->core_->logMessage("snapped image");

   if (imageRequest_.closeShutter) {
      eng_->core_->setShutterOpen(false);
      eng_->core_->logMessage("closed shutter");
   }

   void * img = eng_->core_->getImage(); // Snaps and retrieves image.
   eng_->core_->logMessage("retrieved image");

   Metadata md;
   md.frameData["Slice"] = CDeviceUtils::ConvertToString(max(0, imageRequest_.sliceIndex));
   md.frameData["Channel"] = imageRequest_.channel.name;
   md.frameData["Position"] = CDeviceUtils::ConvertToString(max(0, imageRequest_.positionIndex));
   md.frameData["ChannelIndex"] = CDeviceUtils::ConvertToString(max(0, imageRequest_.channelIndex));
   md.frameData["Frame"] = CDeviceUtils::ConvertToString(max(0, imageRequest_.timeIndex));
   md.frameData["ExposureMs"] = CDeviceUtils::ConvertToString(imageRequest_.exposure);
   if (imageRequest_.usePosition)
      md.frameData["PositionName"] = imageRequest_.multiAxisPosition.name;

   eng_->ApplyDiffPropertyMap(md.frameData);

   eng_->coreCallback_->GetImageDimensions(w, h, d);
   
   md.frameData["Width"] = CDeviceUtils::ConvertToString(w);
   md.frameData["Height"] = CDeviceUtils::ConvertToString(h);
   md.frameData["Depth"] = CDeviceUtils::ConvertToString(d);

   eng_->coreCallback_->InsertImage(NULL, (const unsigned char *) img, w, h, d, &md);
   eng_->core_->logMessage("Grabbed image.\n");
}




/////////////////////////
// MMAcquisitionEngine //
/////////////////////////


map<string, string> MMAcquisitionEngine::GetCurrentPropertyMap() {
   Configuration config = core_->getSystemStateCache();
   map<string, string> frameData;
   for (unsigned long i = 0; i < config.size(); i++) {
      PropertySetting setting = config.getSetting(i);
      frameData[setting.getDeviceLabel() + "-" + setting.getPropertyName()] = setting.getPropertyValue();
   }
   return frameData;
}

void MMAcquisitionEngine::ApplyDiffPropertyMap(map<string, string> &dest) {
   map<string, string> curMap = GetCurrentPropertyMap();

   pair<string, string> curProp;

   BOOST_FOREACH(curProp, curMap)
   {
      if (initialPropertyMap_[curProp.first] != curProp.second)
         dest[curProp.first] = curProp.second;
   }
}

map<string, string> MMAcquisitionEngine::GetInitPropertyMap() {
   return initialPropertyMap_;
}

Metadata MMAcquisitionEngine::GetInitMetadata() {
   while (!started_)
      coreCallback_->Sleep(NULL, 1);

   Metadata md;
   md.frameData = initialPropertyMap_;
   return md;
}


void MMAcquisitionEngine::Start(AcquisitionSettings acquisitionSettings) {
   defaultExposure_ = core_->getExposure();

   GenerateSequence(acquisitionSettings);

   stopRequested_ = false;
   pauseRequested_ = false;
   finished_ = false;

   initialPropertyMap_ = GetCurrentPropertyMap();

   activate();
   started_ = true;

}

void MMAcquisitionEngine::Run() {
   bool initialAutoshutter = core_->getAutoShutter();
   core_->setAutoShutter(false);


   for (unsigned int i = 0; i < tasks_.size(); ++i) {
      if (stopRequested_)
         break;
      stringstream msg;
      msg << "Task " << i << " started, type " << tasks_[i]->type << ".";
      core_->logMessage(msg.str().c_str());
      tasks_[i]->run();
   }

   core_->setAutoShutter(initialAutoshutter);

   finished_ = true;

}

bool MMAcquisitionEngine::IsFinished() {
   return finished_;
}

void MMAcquisitionEngine::Stop() {
   stopRequested_ = true;
}

void MMAcquisitionEngine::Pause() {
   pauseRequested_ = true;
}

void MMAcquisitionEngine::Resume() {
   pauseRequested_ = false;
}

void MMAcquisitionEngine::Step() {
}

bool MMAcquisitionEngine::StopHasBeenRequested() {
   return stopRequested_;
}

void MMAcquisitionEngine::SetTasks(TaskVector tasks) {
   tasks_ = tasks;
}

void MMAcquisitionEngine::GenerateSequence(AcquisitionSettings acquisitionSettings) {
   ImageRequest imageRequest, lastImageRequest;

   imageRequest.usePosition = (acquisitionSettings.positionList.size() > 0);
   imageRequest.useTime = (acquisitionSettings.timeSeries.size() > 0);
   imageRequest.useChannel = (acquisitionSettings.channelList.size() > 0);
   imageRequest.useSlice = (acquisitionSettings.zStack.size() > 0);

   imageRequest.exposure = defaultExposure_;

   int numPositions = max(1, (int) acquisitionSettings.positionList.size());
   int numFrames = max(1, (int) acquisitionSettings.timeSeries.size());
   int numChannels = max(1, (int) acquisitionSettings.channelList.size());
   int numSlices = max(1, (int) acquisitionSettings.zStack.size());
   int numImages = numPositions * numFrames * numChannels * numSlices;

   for (int imageIndex = 0; imageIndex < (1 + numImages); ++imageIndex) {
      imageRequest.skipImage = false;
      imageRequest.closeShutter = true;

      if (acquisitionSettings.channelsFirst) {
         imageRequest.channelIndex = imageIndex % numChannels;
         imageRequest.sliceIndex = (imageIndex / numChannels) % numSlices;
      } else // slices first
      {
         imageRequest.sliceIndex = imageIndex % numSlices;
         imageRequest.channelIndex = (imageIndex / numSlices) % numChannels;
      }

      if (acquisitionSettings.positionsFirst) {
         imageRequest.positionIndex = (imageIndex / (numChannels * numSlices)) % numPositions;
         imageRequest.timeIndex = (imageIndex / (numChannels * numSlices * numPositions)) % numFrames;
      } else // time first
      {
         imageRequest.timeIndex = (imageIndex / (numChannels * numSlices)) % numFrames;
         imageRequest.positionIndex = (imageIndex / (numChannels * numSlices * numFrames)) % numPositions;
      }

      if (imageRequest.useTime && imageRequest.timeIndex > 0 && imageRequest.positionIndex <= 0 // &&
            && imageRequest.channelIndex <= 0 && imageRequest.sliceIndex <= 0)
         imageRequest.waitTime = MM::MMTime(acquisitionSettings.timeSeries[imageRequest.timeIndex] * 1000);
      else
         imageRequest.waitTime = 0;

      if (imageRequest.usePosition)
         imageRequest.multiAxisPosition = acquisitionSettings.positionList[imageRequest.positionIndex];

      if (imageRequest.useSlice)
         imageRequest.slicePosition = acquisitionSettings.zStack[imageRequest.sliceIndex];

      if (imageRequest.useChannel) {
         imageRequest.channel = acquisitionSettings.channelList[imageRequest.channelIndex];
         if (0 != (imageRequest.timeIndex % (imageRequest.channel.skipFrames + 1)))
            imageRequest.skipImage = true;
      }

      if (imageRequest.useChannel && imageRequest.useSlice) {
         if (!imageRequest.channel.useZStack && ((unsigned) imageRequest.sliceIndex != (acquisitionSettings.zStack.size() - 1) / 2))
            imageRequest.skipImage = true;
      }

      imageRequest.runAutofocus = acquisitionSettings.useAutofocus;
      if (imageRequest.timeIndex > -1) {
         imageRequest.runAutofocus = imageRequest.runAutofocus
               && (0 == (imageRequest.timeIndex % (1 + acquisitionSettings.autofocusSkipFrames)));
      }

      if (imageIndex > 0)
      {
         if (imageRequest.timeIndex == lastImageRequest.timeIndex
               && imageRequest.positionIndex == lastImageRequest.positionIndex) {
            if (acquisitionSettings.keepShutterOpenChannels
                  && !acquisitionSettings.keepShutterOpenSlices) {
               if (imageRequest.sliceIndex == lastImageRequest.sliceIndex)
                  lastImageRequest.closeShutter = false;
            }

            if (acquisitionSettings.keepShutterOpenSlices
                  && !acquisitionSettings.keepShutterOpenChannels) {
               if (imageRequest.channelIndex == lastImageRequest.channelIndex)
                  lastImageRequest.closeShutter = false;
            }

            if (acquisitionSettings.keepShutterOpenSlices
                  && acquisitionSettings.keepShutterOpenChannels) {
               lastImageRequest.closeShutter = false;
            }
         }

         if (!lastImageRequest.skipImage)
            tasks_.push_back(new ImageTask(this, lastImageRequest));
      }
      
      if (imageIndex == 0 || !imageRequest.skipImage)
         lastImageRequest = imageRequest;
   }
}
