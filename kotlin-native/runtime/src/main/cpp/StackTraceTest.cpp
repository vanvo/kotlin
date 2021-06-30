/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "StackTrace.hpp"

#include "gmock/gmock.h"
#include "gtest/gtest.h"

#include "Common.h"
#include "Porting.h"
#include "TestSupport.hpp"

using namespace kotlin;

namespace {

NO_INLINE KStdVector<void*> GetStackTrace1(int skipFrames) {
    return GetCurrentStackTrace(skipFrames);
}

NO_INLINE KStdVector<void*> GetStackTrace2(int skipFrames) {
    return GetStackTrace1(skipFrames);
}

NO_INLINE void AbortWithStackTrace() {
    PrintStackTraceStderr();
    konan::abort();
}

} // namespace

TEST(StackTraceTest, StackTrace) {
    // TODO: Consider incorporating extra skipping to `GetCurrentStackTrace` on windows.
#if KONAN_WINDOWS
    constexpr int kSkip = 1;
#else
    constexpr int kSkip = 0;
#endif
    auto stackTrace = GetStackTrace2(kSkip);
    auto symbolicStackTrace = GetStackTraceStrings(stackTrace.data(), stackTrace.size());
    ASSERT_GT(symbolicStackTrace.size(), 0ul);
    EXPECT_THAT(symbolicStackTrace[0], testing::HasSubstr("GetStackTrace1"));
}

TEST(StackTraceTest, StackTraceWithSkip) {
    // TODO: Consider incorporating extra skipping to `GetCurrentStackTrace` on windows.
#if KONAN_WINDOWS
    constexpr int kSkip = 2;
#else
    constexpr int kSkip = 1;
#endif
    auto stackTrace = GetStackTrace2(kSkip);
    auto symbolicStackTrace = GetStackTraceStrings(stackTrace.data(), stackTrace.size());
    ASSERT_GT(symbolicStackTrace.size(), 0ul);
    EXPECT_THAT(symbolicStackTrace[0], testing::HasSubstr("GetStackTrace2"));
}

TEST(StackTraceDeathTest, PrintStackTrace) {
    EXPECT_DEATH(
            { kotlin::RunInNewThread(AbortWithStackTrace); },
            testing::AllOf(testing::HasSubstr("AbortWithStackTrace"), testing::Not(testing::HasSubstr("PrintStackTraceStderr")))
            );
}
