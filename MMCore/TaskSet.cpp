///////////////////////////////////////////////////////////////////////////////
// FILE:          TaskSet.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   Base class for grouping tasks for one logical operation.
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

#include "TaskSet.h"

#include <boost/foreach.hpp>
#include <boost/make_shared.hpp>

TaskSet::TaskSet(boost::shared_ptr<ThreadPool> pool)
    : pool_(pool),
    semaphore_(boost::make_shared<Semaphore>()),
    tasks_(),
    usedTaskCount_(0)
{
#ifdef _WINDOWS
    assert(pool != nullptr);
#endif
}

TaskSet::~TaskSet()
{
    BOOST_FOREACH(Task* task, tasks_)
        delete task;
}

size_t TaskSet::GetUsedTaskCount() const
{
    return usedTaskCount_;
}

void TaskSet::Execute()
{
#ifdef _WINDOWS
    pool_->Execute(std::vector<Task*>(tasks_.cbegin(), tasks_.cbegin() + usedTaskCount_));
#else
   pool_->Execute(std::vector<Task*>(tasks_.begin(), tasks_.begin() + usedTaskCount_));
#endif
}

void TaskSet::Wait()
{
    semaphore_->Wait(usedTaskCount_);
}
