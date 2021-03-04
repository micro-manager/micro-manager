///////////////////////////////////////////////////////////////////////////////
// FILE:          TaskSet_CopyMemory.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Task set for parallelized memory copy.
//
// AUTHOR:        Tomas Hanak, tomas.hanak@teledyne.com, 03/03/2021
//                Andrej Bencur, andrej.bencur@teledyne.com, 03/03/2021
//
// COPYRIGHT:     Teledyne Digital Imaging US, Inc., 2021
//
// LICENSE:       This file is distributed under the "Lesser GPL" (LGPL) license.
//                License text is included with the source distribution.
//
//                This file is distributed in the hope that it will be useful,
//                but WITHOUT ANY WARRANTY; without even the implied warranty
//                of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
//
//                IN NO EVENT SHALL THE COPYRIGHT OWNER OR
//                CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
//                INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES.

#include "TaskSet_CopyMemory.h"

#include <boost/foreach.hpp>

#include <algorithm>
#include <cassert>

TaskSet_CopyMemory::ATask::ATask(boost::shared_ptr<Semaphore> semDone, size_t taskIndex, size_t totalTaskCount)
    : Task(semDone, taskIndex, totalTaskCount),
    dst_(nullptr),
    src_(nullptr),
    bytes_(0)
{
}

void TaskSet_CopyMemory::ATask::SetUp(void* dst, const void* src, size_t bytes, size_t usedTaskCount)
{
    dst_ = dst;
    src_ = src;
    bytes_ = bytes;
    usedTaskCount_ = usedTaskCount;
}

void TaskSet_CopyMemory::ATask::Execute()
{
    if (taskIndex_ >= usedTaskCount_)
        return;

    size_t chunkBytes = bytes_ / usedTaskCount_;
    const size_t chunkOffset = taskIndex_ * chunkBytes;
    if (taskIndex_ == usedTaskCount_ - 1)
        chunkBytes += bytes_ % usedTaskCount_;

    void* dst = static_cast<char*>(dst_) + chunkOffset;
    const void* src = static_cast<const char*>(src_) + chunkOffset;

    memcpy(dst, src, chunkBytes);
}

TaskSet_CopyMemory::TaskSet_CopyMemory(boost::shared_ptr<ThreadPool> pool)
    : TaskSet(pool)
{
    CreateTasks<ATask>();
}

void TaskSet_CopyMemory::SetUp(void* dst, const void* src, size_t bytes)
{
    assert(dst != nullptr);
    assert(src != nullptr);
    assert(bytes > 0);

    // Call memcpy directly without threading for small frames up to 1MB
    // Otherwise do parallel copy and add one thread for each 1MB
    // The limits were found experimentally
    usedTaskCount_ = std::min<size_t>(1 + bytes / 1000000, tasks_.size());
    if (usedTaskCount_ == 1)
    {
        memcpy(dst, src, bytes);
        return;
    }

    BOOST_FOREACH(Task* task, tasks_)
        static_cast<ATask*>(task)->SetUp(dst, src, bytes, usedTaskCount_);
}

void TaskSet_CopyMemory::Execute()
{
    if (usedTaskCount_ == 1)
        return; // Already done in SetUp, nothing to execute

    TaskSet::Execute();
}

void TaskSet_CopyMemory::Wait()
{
    if (usedTaskCount_ == 1)
        return; // Already done in SetUp, nothing to wait for

    semaphore_->Wait(usedTaskCount_);
}

void TaskSet_CopyMemory::MemCopy(void* dst, const void* src, size_t bytes)
{
    SetUp(dst, src, bytes);
    Execute();
    Wait();
}
