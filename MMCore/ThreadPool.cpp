///////////////////////////////////////////////////////////////////////////////
// FILE:          ThreadPool.cpp
// PROJECT:       Micro-Manager
// SUBSYSTEM:     MMCore
//-----------------------------------------------------------------------------
// DESCRIPTION:   A class executing queued tasks on separate threads
//                and scaling number of threads based on hardware.
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

#include "ThreadPool.h"

#include "Task.h"

#include <boost/foreach.hpp>
#include <boost/make_shared.hpp>
#include <boost/thread/locks.hpp>
#ifndef _WINDOWS
#include <boost/phoenix.hpp>
#endif

#include <algorithm>
#include <cassert>

ThreadPool::ThreadPool()
    : abortFlag_(false)
{
    const auto hwThreadCount = std::max<size_t>(1, boost::thread::hardware_concurrency());
    for (size_t n = 0; n < hwThreadCount; ++n)
        threads_.push_back(boost::make_shared<boost::thread>(&ThreadPool::ThreadFunc, this));
}

ThreadPool::~ThreadPool()
{
    {
        boost::lock_guard<boost::mutex> lock(mx_);
        abortFlag_ = true;
    }
    cv_.notify_all();

    BOOST_FOREACH(const boost::shared_ptr<boost::thread>& thread, threads_)
        thread->join();
}

size_t ThreadPool::GetSize() const
{
    return threads_.size();
}

void ThreadPool::Execute(Task* task) 
{
#ifdef _WINDOWS
    assert(task != nullptr);
#endif
    {
        boost::lock_guard<boost::mutex> lock(mx_);
        if (abortFlag_)
            return;
        queue_.push_back(task);
    }
    cv_.notify_one();
}

void ThreadPool::Execute(const std::vector<Task*>& tasks)
{
    assert(!tasks.empty());

    {
        boost::lock_guard<boost::mutex> lock(mx_);
        if (abortFlag_)
            return;
        BOOST_FOREACH(Task* task, tasks)
        {
#ifdef _WINDOWS
            assert(task != nullptr);
#endif
            queue_.push_back(task);
        }
    }
    cv_.notify_one();
}

void ThreadPool::ThreadFunc()
{
    for (;;)
    {
#ifdef _WINDOWS
        Task* task = nullptr;
#else
       Task* task;
#endif
        {
            boost::unique_lock<boost::mutex> lock(mx_);
#ifndef _WINDOWS
            namespace phx = boost::phoenix;
            cv_.wait(lock,  phx::ref(abortFlag_) || !phx::empty(phx::ref(queue_)));
#else
            cv_.wait(lock, [&]() { return abortFlag_ || !queue_.empty(); });
#endif
            if (abortFlag_)
                break;
            task = queue_.front();
            queue_.pop_front();
        }
        task->Execute();
        task->Done();
    }
}
