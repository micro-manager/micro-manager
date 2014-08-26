#include <gtest/gtest.h>

#include "Logging/GenericLinePacket.h"
#include "Logging/Logging.h"
#include "Logging/GenericPacketArray.h"

#include <algorithm>
#include <iterator>
#include <string>
#include <vector>

using namespace mm::logging;

typedef Metadata MetadataType;
typedef internal::GenericPacketArray<Metadata> PacketArrayType;
const size_t MaxLogLineLen =
   internal::GenericLinePacket<MetadataType>::PacketTextLen;


class SplitEntryIntoPacketsTest : public ::testing::Test
{
public:
   SplitEntryIntoPacketsTest() :
      loggerData_("component"),
      entryData_(LogLevelInfo)
   {}

protected:
   MetadataType::LoggerDataType loggerData_;
   MetadataType::EntryDataType entryData_;
   MetadataType::StampDataType stampData_;

   std::vector< internal::GenericLinePacket<MetadataType> > result_;
   virtual void SetUp()
   {
      stampData_.Stamp();
   }
   virtual void Split(const char* s)
   {
      PacketArrayType array;
      array.AppendEntry(loggerData_, entryData_, stampData_, s);
      std::copy(array.Begin(), array.End(), std::back_inserter(result_));
   }
};


class SplitEntryIntoPacketsParameterizedTest :
   public SplitEntryIntoPacketsTest,
   public ::testing::WithParamInterface<std::string>
{
   virtual void SetUp()
   {
      SplitEntryIntoPacketsTest::SetUp();
      Split(GetParam().c_str());
   }
};


class SplitEntryIntoPacketsEmptyResultTest :
   public SplitEntryIntoPacketsParameterizedTest
{};

TEST_P(SplitEntryIntoPacketsEmptyResultTest, EmptyResult)
{
   ASSERT_EQ(1, result_.size());
   EXPECT_STREQ("", result_[0].GetText());
}

INSTANTIATE_TEST_CASE_P(NewlinesCase, SplitEntryIntoPacketsEmptyResultTest,
      ::testing::Values("", "\r", "\n", "\r\r", "\r\n", "\n\n",
         "\r\r\r", "\r\r\n", "\r\n\r", "\r\n\n",
         "\n\r\r", "\n\r\n", "\n\n\r", "\n\n\n"));


class SplitEntryIntoPacketsSingleCharResultTest :
   public SplitEntryIntoPacketsParameterizedTest
{};

TEST_P(SplitEntryIntoPacketsSingleCharResultTest, SingleXResult)
{
   ASSERT_EQ(1, result_.size());
   EXPECT_STREQ("X", result_[0].GetText());
}

INSTANTIATE_TEST_CASE_P(XFollowedByNewlinesCase,
      SplitEntryIntoPacketsSingleCharResultTest,
      ::testing::Values("X", "X\r", "X\n", "X\r\r", "X\r\n", "X\n\n",
         "X\r\r\r", "X\r\r\n", "X\r\n\r", "X\r\n\n",
         "X\n\r\r", "X\n\r\n", "X\n\n\r", "X\n\n\n"));


class SplitEntryIntoPacketsTwoLineResultTest :
   public SplitEntryIntoPacketsParameterizedTest
{};

TEST_P(SplitEntryIntoPacketsTwoLineResultTest, TwoLineXYResult)
{
   ASSERT_EQ(2, result_.size());
   EXPECT_EQ(internal::PacketStateEntryFirstLine, result_[0].GetPacketState());
   EXPECT_STREQ("X", result_[0].GetText());
   EXPECT_EQ(internal::PacketStateNewLine, result_[1].GetPacketState());
   EXPECT_STREQ("Y", result_[1].GetText());
}

INSTANTIATE_TEST_CASE_P(XNewlineYCase,
      SplitEntryIntoPacketsTwoLineResultTest,
      ::testing::Values("X\rY", "X\nY", "X\r\nY"));

INSTANTIATE_TEST_CASE_P(XLinefeedYNewlinesCase,
      SplitEntryIntoPacketsTwoLineResultTest,
      ::testing::Values("X\nY\r", "X\nY\n", "X\nY\r\r", "X\nY\r\n", "X\nY\n\n",
         "X\nY\r\r\r", "X\nY\r\r\n", "X\nY\r\n\r", "X\nY\r\n\n",
         "X\nY\n\r\r", "X\nY\n\r\n", "X\nY\n\n\r", "X\nY\n\n\n"));


class SplitEntryIntoPacketsXEmptyYResultTest :
   public SplitEntryIntoPacketsParameterizedTest
{};

TEST_P(SplitEntryIntoPacketsXEmptyYResultTest, XEmptyYResult)
{
   ASSERT_EQ(3, result_.size());
   EXPECT_EQ(internal::PacketStateEntryFirstLine, result_[0].GetPacketState());
   EXPECT_STREQ("X", result_[0].GetText());
   EXPECT_EQ(internal::PacketStateNewLine, result_[1].GetPacketState());
   EXPECT_STREQ("", result_[1].GetText());
   EXPECT_EQ(internal::PacketStateNewLine, result_[2].GetPacketState());
   EXPECT_STREQ("Y", result_[2].GetText());
}

INSTANTIATE_TEST_CASE_P(XNewlineNewlineYCase,
      SplitEntryIntoPacketsXEmptyYResultTest,
      ::testing::Values("X\r\rY", "X\n\nY", "X\n\rY",
         "X\r\n\rY", "X\r\n\nY", "X\r\r\nY", "X\n\r\nY",
         "X\r\n\r\nY"));


class SplitEntryIntoPacketsLeadingNewlineTest :
   public SplitEntryIntoPacketsTest,
   public ::testing::WithParamInterface< std::pair<size_t, std::string> >
{
protected:
   size_t expected_;

   virtual void SetUp()
   {
      SplitEntryIntoPacketsTest::SetUp();
      expected_ = GetParam().first;
      Split(GetParam().second.c_str());
   }
};

TEST_P(SplitEntryIntoPacketsLeadingNewlineTest, CorrectLeadingNewlines)
{
   ASSERT_EQ(expected_ + 1, result_.size());
   EXPECT_EQ(internal::PacketStateEntryFirstLine, result_[0].GetPacketState());
   for (size_t i = 0; i < expected_; ++i)
   {
      EXPECT_STREQ("", result_[i].GetText());
      EXPECT_EQ(internal::PacketStateNewLine, result_[i + 1].GetPacketState());
   }
   EXPECT_STREQ("X", result_[expected_].GetText());
}

INSTANTIATE_TEST_CASE_P(LeadingNewlinesCase,
      SplitEntryIntoPacketsLeadingNewlineTest,
      ::testing::Values(std::make_pair(0, "X"),
         std::make_pair(1, "\rX"),
         std::make_pair(1, "\nX"),
         std::make_pair(1, "\r\nX"),
         std::make_pair(2, "\r\rX"),
         std::make_pair(2, "\n\rX"),
         std::make_pair(2, "\n\nX"),
         std::make_pair(2, "\r\n\rX"),
         std::make_pair(2, "\r\n\nX"),
         std::make_pair(2, "\r\r\nX"),
         std::make_pair(2, "\n\r\nX")));


class SplitEntryIntoPacketsSoftNewlineTest :
   public SplitEntryIntoPacketsParameterizedTest
{};

TEST_P(SplitEntryIntoPacketsSoftNewlineTest, CorrectSplit)
{
   // We are assuming input did not contain hard newlines
   size_t inputLen = GetParam().size();
   size_t nLines = inputLen ? (inputLen - 1) / MaxLogLineLen + 1 : 1;
   ASSERT_EQ(nLines, result_.size());
   EXPECT_EQ(internal::PacketStateEntryFirstLine, result_[0].GetPacketState());
   for (size_t i = 0; i < nLines; ++i)
   {
      if (i > 0)
      {
         EXPECT_EQ(internal::PacketStateLineContinuation,
               result_[i].GetPacketState());
      }
      EXPECT_STREQ(
            GetParam().substr(i * MaxLogLineLen, MaxLogLineLen).c_str(),
            result_[i].GetText());
   }
}

INSTANTIATE_TEST_CASE_P(NoSoftSplitCase,
      SplitEntryIntoPacketsSoftNewlineTest,
      ::testing::Values(
         std::string(MaxLogLineLen - 1, 'x'),
         std::string(MaxLogLineLen, 'x')));

INSTANTIATE_TEST_CASE_P(OneSoftSplitCase,
      SplitEntryIntoPacketsSoftNewlineTest,
      ::testing::Values(
         std::string(MaxLogLineLen + 1, 'x'),
         std::string(2 * MaxLogLineLen - 1, 'x'),
         std::string(2 * MaxLogLineLen, 'x')));

INSTANTIATE_TEST_CASE_P(TwoSoftSplitCase,
      SplitEntryIntoPacketsSoftNewlineTest,
      ::testing::Values(
         std::string(2 * MaxLogLineLen + 1, 'x'),
         std::string(3 * MaxLogLineLen - 1, 'x'),
         std::string(3 * MaxLogLineLen, 'x')));


int main(int argc, char **argv)
{
   ::testing::InitGoogleTest(&argc, argv);
   return RUN_ALL_TESTS();
}
