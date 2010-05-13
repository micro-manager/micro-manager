#include "MMAcquisition.h"
#include "../MMDevice/ImageMetadata.h"
#include "boost/foreach.hpp"

///////////////////////////////////
// MMAquisitionEngine::ImageTask //
///////////////////////////////////

ImageTask::ImageTask(MMAcquisitionEngine * eng, ImageRequest imageRequest)
{
	eng_ = eng;
	imageRequest_ = imageRequest;
	type = IMAGE;
}

void ImageTask::run()
{
	updatePosition();
	updateSlice();
	updateChannel();
	wait();
	autofocus();
	acquireImage();
}

void ImageTask::updateSlice()
{
	eng_->coreCallback_->SetFocusPosition(imageRequest_.slicePosition);
	printf("slice set\n");
}

void ImageTask::updatePosition()
{
	map<string, double>::iterator it1;

	MultiAxisPosition pos = imageRequest_.multiAxisPosition;
	for (it1 = pos.singleAxisPositions.begin(); it1 != pos.singleAxisPositions.end(); ++it1)
	{
		eng_->core_->setPosition(it1->first.c_str(), it1->second);
	}

	map<string, pair<double, double> >::iterator it2;

	for (pos.doubleAxisPositions.begin(); it2 != pos.doubleAxisPositions.end(); ++it2)
	{
		point2D xy = it2->second;
		eng_->core_->setXYPosition(it2->first.c_str(), xy.first, xy.second);
	}
	printf("position set\n");
}

void ImageTask::updateChannel() {
	eng_->coreCallback_->SetExposure(imageRequest_.channel.exposure);
	eng_->coreCallback_->SetConfig(imageRequest_.channel.group.c_str(), imageRequest_.channel.name.c_str());
	printf("channel set\n");
}

void ImageTask::wait() {
	if (eng_->lastWakeTime_ > 0)
	{
		MM::MMTime sleepTime = (eng_->lastWakeTime_ + imageRequest_.waitTime) - eng_->coreCallback_->GetCurrentMMTime();
		if (sleepTime > MM::MMTime(0, 0))
			eng_->coreCallback_->Sleep(NULL, sleepTime.getMsec());
		printf("waited\n");
	}

	eng_->lastWakeTime_ = eng_->coreCallback_->GetCurrentMMTime();
}

void ImageTask::autofocus() {
	if (imageRequest_.runAutofocus)
		eng_->core_->fullFocus();
}

void ImageTask::acquireImage() {
	int w, h, d;
	const char * img = eng_->coreCallback_->GetImage();

	eng_->coreCallback_->GetImageDimensions(w, h, d);
	eng_->coreCallback_->InsertImage(NULL, (const unsigned char *) img, w, h, d);
	printf("Grabbed image.\n");
}





/////////////////////////
// MMAcquisitionRunner //
/////////////////////////

void MMAcquisitionEngine::Start() {
	stopRequested_ = false;
	pauseRequested_ = false;
	finished_ = false;

	activate();
}

void MMAcquisitionEngine::Run() {
	for (unsigned int i = 0; i < tasks_.size(); ++i) {
		if (stopRequested_)
			break;
		printf("Task #%d started, type %d\n", i, tasks_[i]->type);
		tasks_[i]->run();
	}
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

void MMAcquisitionEngine::SetTasks(TaskVector tasks) {
	tasks_ = tasks;
}

void MMAcquisitionEngine::GenerateSequence(AcquisitionSettings acquisitionSettings)
{

	ImageRequest imageRequest;
   imageRequest.runAutofocus = acquisitionSettings.useAutofocus;
   double waitTime_ms;

	if (acquisitionSettings.positionsFirst)
	{
		BOOST_FOREACH(waitTime_ms, acquisitionSettings.timeSeries)
		{
			imageRequest.waitTime = MM::MMTime(waitTime_ms * 1000);
			BOOST_FOREACH(imageRequest.multiAxisPosition, acquisitionSettings.positionList)
			{
				GenerateSlicesAndChannelsSubsequence(acquisitionSettings, imageRequest);
			}
		}

	}
	else
	{
		BOOST_FOREACH(imageRequest.multiAxisPosition, acquisitionSettings.positionList)
		{
			BOOST_FOREACH(waitTime_ms, acquisitionSettings.timeSeries)
			{
				imageRequest.waitTime = MM::MMTime(waitTime_ms * 1000);
				GenerateSlicesAndChannelsSubsequence(acquisitionSettings, imageRequest);
			}
		}
	}
}

void MMAcquisitionEngine::GenerateSlicesAndChannelsSubsequence(AcquisitionSettings acquisitionSettings, ImageRequest imageRequest)
{
	if (acquisitionSettings.channelsFirst)
	{
		BOOST_FOREACH(imageRequest.slicePosition, acquisitionSettings.zStack)
		{
			BOOST_FOREACH(imageRequest.channel, acquisitionSettings.channelList)
			{
				tasks_.push_back(new ImageTask(this, imageRequest));
			}
		}
	}
	else
	{
		BOOST_FOREACH(imageRequest.channel, acquisitionSettings.channelList)
		{
			BOOST_FOREACH(imageRequest.slicePosition, acquisitionSettings.zStack)
			{
				tasks_.push_back(new ImageTask(this, imageRequest));
			}
		}
	}
}

