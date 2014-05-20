#include <gtest/gtest.h>

#include "FastLogger.h"

TEST(LoggerSanityTest, CreateAndDestroyTwice)
{
   {
      FastLogger l1;
      l1.Initialize("test.log");
      l1.Shutdown();
   }
   {
      FastLogger l2;
      l2.Initialize("test.log");
      l2.Shutdown();
   }
}

TEST(LoggerSanityTest, CreateTwoAtOnce)
{
   FastLogger l1;
   FastLogger l2;
   l1.Initialize("test1.log");
   l2.Initialize("test2.log");
   l2.Shutdown();
   l1.Shutdown();
}

int main(int argc, char **argv)
{
   ::testing::InitGoogleTest(&argc, argv);
   return RUN_ALL_TESTS();
}
