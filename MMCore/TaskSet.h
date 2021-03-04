///////////////////////////////////////////////////////////////////////////////
// FILE:          TaskSet.h
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

#pragma once

#include "Semaphore.h"
#include "Task.h"
#include "ThreadPool.h"

#include <boost/smart_ptr/shared_ptr.hpp>
#include <boost/utility/enable_if.hpp>

#include <vector>

class TaskSet
{
public:
    explicit TaskSet(boost::shared_ptr<ThreadPool> pool);
    virtual ~TaskSet();

    TaskSet(const TaskSet&)/* = delete*/;
    TaskSet& operator=(const TaskSet&)/* = delete*/;

    size_t GetUsedTaskCount() const;

    virtual void Execute();
    virtual void Wait();

protected:
    //template<class T,
    //    // Private param to enforce the type T derives from Task class
    //    typename std::enable_if<std::is_base_of<Task, T>::value, int>::type = 0>
    template<class T>
    void CreateTasks()
    {
        const size_t taskCount = pool_->GetSize();
        tasks_.reserve(taskCount);
        for (size_t n = 0; n < taskCount; ++n)
        {
            auto task = new(std::nothrow) T(semaphore_, n, taskCount);
            if (!task)
                continue;
            tasks_.push_back(task);
        }
        usedTaskCount_ = tasks_.size();
    }

protected:
    const boost::shared_ptr<ThreadPool> pool_;
    const boost::shared_ptr<Semaphore> semaphore_;
    std::vector<Task*> tasks_;
    size_t usedTaskCount_;
};
