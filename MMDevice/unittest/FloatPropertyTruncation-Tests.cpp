#include <gtest/gtest.h>

#include "Property.h"

using namespace MM;


TEST(FloatPropertyTruncationTests, SetValueIsTruncatedTo4Digits)
{
   FloatProperty fp("TestProp");
   double v;

   ASSERT_TRUE(fp.Set(0.00004));
   ASSERT_TRUE(fp.Get(v));
   ASSERT_DOUBLE_EQ(0.0, v);

   ASSERT_TRUE(fp.Set(0.00005));
   ASSERT_TRUE(fp.Get(v));
   ASSERT_DOUBLE_EQ(0.0001, v);

   ASSERT_TRUE(fp.Set(-0.00004));
   ASSERT_TRUE(fp.Get(v));
   ASSERT_DOUBLE_EQ(0.0, v);

   ASSERT_TRUE(fp.Set(-0.00005));
   ASSERT_TRUE(fp.Get(v));
   ASSERT_DOUBLE_EQ(-0.0001, v);
}


TEST(FloatPropertyTruncationTests, LowerLimitIsTruncatedUp)
{
   FloatProperty fp("TestProp");

   ASSERT_TRUE(fp.SetLimits(0.0, 1000.0));
   ASSERT_DOUBLE_EQ(0.0, fp.GetLowerLimit());
   ASSERT_TRUE(fp.SetLimits(0.00001, 1000.0));
   ASSERT_DOUBLE_EQ(0.0001, fp.GetLowerLimit());
   ASSERT_TRUE(fp.SetLimits(0.00011, 1000.0));
   ASSERT_DOUBLE_EQ(0.0002, fp.GetLowerLimit());

   ASSERT_TRUE(fp.SetLimits(-0.0, 1000.0));
   ASSERT_DOUBLE_EQ(0.0, fp.GetLowerLimit());
   ASSERT_TRUE(fp.SetLimits(-0.00001, 1000.0));
   ASSERT_DOUBLE_EQ(0.0, fp.GetLowerLimit());
   ASSERT_TRUE(fp.SetLimits(-0.00011, 1000.0));
   ASSERT_DOUBLE_EQ(-0.0001, fp.GetLowerLimit());
}


TEST(FloatPropertyTruncationTests, UpperLimitIsTruncatedDown)
{
   FloatProperty fp("TestProp");

   ASSERT_TRUE(fp.SetLimits(-1000.0, 0.0));
   ASSERT_DOUBLE_EQ(0.0, fp.GetUpperLimit());
   ASSERT_TRUE(fp.SetLimits(-1000.0, 0.00001));
   ASSERT_DOUBLE_EQ(0.0, fp.GetUpperLimit());
   ASSERT_TRUE(fp.SetLimits(-1000.0, 0.00011));
   ASSERT_DOUBLE_EQ(0.0001, fp.GetUpperLimit());

   ASSERT_TRUE(fp.SetLimits(-1000.0, -0.0));
   ASSERT_DOUBLE_EQ(0.0, fp.GetUpperLimit());
   ASSERT_TRUE(fp.SetLimits(-1000.0, -0.00001));
   ASSERT_DOUBLE_EQ(-0.0001, fp.GetUpperLimit());
   ASSERT_TRUE(fp.SetLimits(-1000.0, -0.00011));
   ASSERT_DOUBLE_EQ(-0.0002, fp.GetUpperLimit());
}


int main(int argc, char **argv)
{
   ::testing::InitGoogleTest(&argc, argv);
   return RUN_ALL_TESTS();
}
