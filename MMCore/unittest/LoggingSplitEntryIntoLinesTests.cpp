#include <gtest/gtest.h>

#include "Logging/GenericLinePacket.h"
#include "Logging/Logging.h"

#include <boost/container/vector.hpp>
#include <string>

using namespace mm::logging;

typedef LoggingCore::MetadataType MetadataType;
const size_t MaxLogLineLen =
   detail::GenericLinePacket<MetadataType>::MaxLogLineLen;


class SplitEntryIntoLinesTest : public ::testing::Test
{
public:
   SplitEntryIntoLinesTest() :
      loggerData_("component"),
      entryData_(LogLevelInfo)
   {}

protected:
   MetadataType::LoggerDataType loggerData_;
   MetadataType::EntryDataType entryData_;
   MetadataType::StampDataType stampData_;

   boost::container::vector< detail::GenericLinePacket<MetadataType> > result_;
   virtual void SetUp()
   {
      stampData_.Stamp();
   }
   virtual void Split(const char* s)
   {
      detail::SplitEntryIntoLines<MetadataType>(result_, loggerData_,
            entryData_, stampData_, s);
   }
};


class SplitEntryIntoLinesParameterizedTest : public SplitEntryIntoLinesTest,
   public ::testing::WithParamInterface<std::string>
{
   virtual void SetUp()
   {
      SplitEntryIntoLinesTest::SetUp();
      Split(GetParam().c_str());
   }
};


class SplitEntryIntoLinesEmptyResultTest :
   public SplitEntryIntoLinesParameterizedTest
{};

TEST_P(SplitEntryIntoLinesEmptyResultTest, EmptyResult)
{
   ASSERT_EQ(1, result_.size());
   EXPECT_STREQ("", result_[0].GetLine());
}

INSTANTIATE_TEST_CASE_P(NewlinesCase, SplitEntryIntoLinesEmptyResultTest,
      ::testing::Values("", "\r", "\n", "\r\r", "\r\n", "\n\n",
         "\r\r\r", "\r\r\n", "\r\n\r", "\r\n\n",
         "\n\r\r", "\n\r\n", "\n\n\r", "\n\n\n"));


class SplitEntryIntoLinesSingleCharResultTest :
   public SplitEntryIntoLinesParameterizedTest
{};

TEST_P(SplitEntryIntoLinesSingleCharResultTest, SingleXResult)
{
   ASSERT_EQ(1, result_.size());
   EXPECT_STREQ("X", result_[0].GetLine());
}

INSTANTIATE_TEST_CASE_P(XFollowedByNewlinesCase,
      SplitEntryIntoLinesSingleCharResultTest,
      ::testing::Values("X", "X\r", "X\n", "X\r\r", "X\r\n", "X\n\n",
         "X\r\r\r", "X\r\r\n", "X\r\n\r", "X\r\n\n",
         "X\n\r\r", "X\n\r\n", "X\n\n\r", "X\n\n\n"));


class SplitEntryIntoLinesTwoLineResultTest :
   public SplitEntryIntoLinesParameterizedTest
{};

TEST_P(SplitEntryIntoLinesTwoLineResultTest, TwoLineXYResult)
{
   ASSERT_EQ(2, result_.size());
   EXPECT_EQ(detail::LineLevelFirstLine, result_[0].GetLineLevel());
   EXPECT_STREQ("X", result_[0].GetLine());
   EXPECT_EQ(detail::LineLevelHardNewline, result_[1].GetLineLevel());
   EXPECT_STREQ("Y", result_[1].GetLine());
}

INSTANTIATE_TEST_CASE_P(XNewlineYCase,
      SplitEntryIntoLinesTwoLineResultTest,
      ::testing::Values("X\rY", "X\nY", "X\r\nY"));

INSTANTIATE_TEST_CASE_P(XLinefeedYNewlinesCase,
      SplitEntryIntoLinesTwoLineResultTest,
      ::testing::Values("X\nY\r", "X\nY\n", "X\nY\r\r", "X\nY\r\n", "X\nY\n\n",
         "X\nY\r\r\r", "X\nY\r\r\n", "X\nY\r\n\r", "X\nY\r\n\n",
         "X\nY\n\r\r", "X\nY\n\r\n", "X\nY\n\n\r", "X\nY\n\n\n"));


class SplitEntryIntoLinesXEmptyYResultTest :
   public SplitEntryIntoLinesParameterizedTest
{};

TEST_P(SplitEntryIntoLinesXEmptyYResultTest, XEmptyYResult)
{
   ASSERT_EQ(3, result_.size());
   EXPECT_EQ(detail::LineLevelFirstLine, result_[0].GetLineLevel());
   EXPECT_STREQ("X", result_[0].GetLine());
   EXPECT_EQ(detail::LineLevelHardNewline, result_[1].GetLineLevel());
   EXPECT_STREQ("", result_[1].GetLine());
   EXPECT_EQ(detail::LineLevelHardNewline, result_[2].GetLineLevel());
   EXPECT_STREQ("Y", result_[2].GetLine());
}

INSTANTIATE_TEST_CASE_P(XNewlineNewlineYCase,
      SplitEntryIntoLinesXEmptyYResultTest,
      ::testing::Values("X\r\rY", "X\n\nY", "X\n\rY",
         "X\r\n\rY", "X\r\n\nY", "X\r\r\nY", "X\n\r\nY",
         "X\r\n\r\nY"));


class SplitEntryIntoLinesLeadingNewlineTest : public SplitEntryIntoLinesTest,
   public ::testing::WithParamInterface< std::pair<size_t, std::string> >
{
protected:
   size_t expected_;

   virtual void SetUp()
   {
      SplitEntryIntoLinesTest::SetUp();
      expected_ = GetParam().first;
      Split(GetParam().second.c_str());
   }
};

TEST_P(SplitEntryIntoLinesLeadingNewlineTest, CorrectLeadingNewlines)
{
   ASSERT_EQ(expected_ + 1, result_.size());
   EXPECT_EQ(detail::LineLevelFirstLine, result_[0].GetLineLevel());
   for (size_t i = 0; i < expected_; ++i)
   {
      EXPECT_STREQ("", result_[i].GetLine());
      EXPECT_EQ(detail::LineLevelHardNewline, result_[i + 1].GetLineLevel());
   }
   EXPECT_STREQ("X", result_[expected_].GetLine());
}

INSTANTIATE_TEST_CASE_P(LeadingNewlinesCase,
      SplitEntryIntoLinesLeadingNewlineTest,
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


class SplitEntryIntoLinesSoftNewlineTest :
   public SplitEntryIntoLinesParameterizedTest
{};

TEST_P(SplitEntryIntoLinesSoftNewlineTest, CorrectSplit)
{
   // We are assuming input did not contain hard newlines
   size_t inputLen = GetParam().size();
   size_t nLines = inputLen ? (inputLen - 1) / MaxLogLineLen + 1 : 1;
   ASSERT_EQ(nLines, result_.size());
   EXPECT_EQ(detail::LineLevelFirstLine, result_[0].GetLineLevel());
   for (size_t i = 0; i < nLines; ++i)
   {
      if (i > 0)
      {
         EXPECT_EQ(detail::LineLevelSoftNewline, result_[i].GetLineLevel());
      }
      EXPECT_STREQ(
            GetParam().substr(i * MaxLogLineLen, MaxLogLineLen).c_str(),
            result_[i].GetLine());
   }
}

INSTANTIATE_TEST_CASE_P(NoSoftSplitCase,
      SplitEntryIntoLinesSoftNewlineTest,
      ::testing::Values(
         std::string(MaxLogLineLen - 1, 'x'),
         std::string(MaxLogLineLen, 'x')));

INSTANTIATE_TEST_CASE_P(OneSoftSplitCase,
      SplitEntryIntoLinesSoftNewlineTest,
      ::testing::Values(
         std::string(MaxLogLineLen + 1, 'x'),
         std::string(2 * MaxLogLineLen - 1, 'x'),
         std::string(2 * MaxLogLineLen, 'x')));

INSTANTIATE_TEST_CASE_P(TwoSoftSplitCase,
      SplitEntryIntoLinesSoftNewlineTest,
      ::testing::Values(
         std::string(2 * MaxLogLineLen + 1, 'x'),
         std::string(3 * MaxLogLineLen - 1, 'x'),
         std::string(3 * MaxLogLineLen, 'x')));


int main(int argc, char **argv)
{
   ::testing::InitGoogleTest(&argc, argv);
   return RUN_ALL_TESTS();
}
