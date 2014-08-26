#include <gtest/gtest.h>

#include "Logging/Logging.h"

#include <boost/bind.hpp>
#include <boost/lexical_cast.hpp>
#include <boost/make_shared.hpp>
#include <boost/thread.hpp>

#include <string>
#include <vector>

using namespace mm::logging;


TEST(LoggerTests, BasicSynchronous)
{
   boost::shared_ptr<LoggingCore> c =
      boost::make_shared<LoggingCore>();

   c->AddSink(boost::make_shared<StdErrLogSink>(), SinkModeSynchronous);

   Logger lgr = c->NewLogger("mylabel");

   lgr(LogLevelDebug, "My entry text\nMy second line");
   for (unsigned i = 0; i < 1000; ++i)
      lgr(LogLevelDebug, "More lines!\n\n\n");
}


TEST(LoggerTests, BasicAsynchronous)
{
   boost::shared_ptr<LoggingCore> c =
      boost::make_shared<LoggingCore>();

   c->AddSink(boost::make_shared<StdErrLogSink>(), SinkModeAsynchronous);

   Logger lgr = c->NewLogger("mylabel");

   lgr(LogLevelDebug, "My entry text\nMy second line");
   for (unsigned i = 0; i < 1000; ++i)
      lgr(LogLevelDebug, "More lines!\n\n\n");
}


TEST(LoggerTests, BasicLogStream)
{
   boost::shared_ptr<LoggingCore> c =
      boost::make_shared<LoggingCore>();

   c->AddSink(boost::make_shared<StdErrLogSink>(), SinkModeSynchronous);

   Logger lgr = c->NewLogger("mylabel");

   LOG_INFO(lgr) << 123 << "ABC" << 456;
}


class LoggerTestThreadFunc
{
   unsigned n_;
   boost::shared_ptr<LoggingCore> c_;

public:
   LoggerTestThreadFunc(unsigned n,
         boost::shared_ptr<LoggingCore> c) :
      n_(n), c_(c)
   {}

   void Run()
   {
      Logger lgr =
         c_->NewLogger("thread" + boost::lexical_cast<std::string>(n_));
      char ch = '0' + n_;
      if (ch < '0' || ch > 'z')
         ch = '~';
      for (size_t j = 0; j < 50; ++j)
      {
         LOG_TRACE(lgr) << j << ' ' << std::string(n_ * j, ch);
      }
   }
};


TEST(LoggerTests, SyncAndThreaded)
{
   boost::shared_ptr<LoggingCore> c =
      boost::make_shared<LoggingCore>();

   c->AddSink(boost::make_shared<StdErrLogSink>(), SinkModeSynchronous);

   std::vector< boost::shared_ptr<boost::thread> > threads;
   std::vector< boost::shared_ptr<LoggerTestThreadFunc> > funcs;
   for (unsigned i = 0; i < 10; ++i)
   {
      funcs.push_back(boost::make_shared<LoggerTestThreadFunc>(i, c));
      threads.push_back(boost::make_shared<boost::thread>(
               &LoggerTestThreadFunc::Run, funcs[i].get()));
   }
   for (unsigned i = 0; i < threads.size(); ++i)
      threads[i]->join();
}


TEST(LoggerTests, AsyncAndThreaded)
{
   boost::shared_ptr<LoggingCore> c =
      boost::make_shared<LoggingCore>();

   c->AddSink(boost::make_shared<StdErrLogSink>(), SinkModeAsynchronous);

   std::vector< boost::shared_ptr<boost::thread> > threads;
   std::vector< boost::shared_ptr<LoggerTestThreadFunc> > funcs;
   for (unsigned i = 0; i < 10; ++i)
   {
      funcs.push_back(boost::make_shared<LoggerTestThreadFunc>(i, c));
      threads.push_back(boost::make_shared<boost::thread>(
               &LoggerTestThreadFunc::Run, funcs[i].get()));
   }
   for (unsigned i = 0; i < threads.size(); ++i)
      threads[i]->join();
}


int main(int argc, char **argv)
{
   ::testing::InitGoogleTest(&argc, argv);
   return RUN_ALL_TESTS();
}
