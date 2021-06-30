/*
 * Copyright 2010-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license
 * that can be found in the LICENSE file.
 */

#include "StackTrace.hpp"

#if KONAN_NO_BACKTRACE
// Nothing to include
#elif USE_GCC_UNWIND
// GCC unwinder for backtrace.
#include <unwind.h>
#else
// Glibc backtrace() function.
#include <execinfo.h>
#endif

#include "Common.h"
#include "ExecFormat.h"
#include "Memory.h"
#include "Natives.h"
#include "SourceInfo.h"

#include "utf8.h"

using namespace kotlin;

namespace {

#if USE_GCC_UNWIND
struct Backtrace {
    Backtrace(int count, int skip) : index(0), skipCount(skip) {
        uint32_t size = count - skipCount;
        if (size < 0) {
            size = 0;
        }
        auto result = AllocArrayInstance(theNativePtrArrayTypeInfo, size, arrayHolder.slot());
        // TODO: throw cached OOME?
        RuntimeCheck(result != nullptr, "Cannot create backtrace array");
    }

    void setNextElement(_Unwind_Ptr element) { Kotlin_NativePtrArray_set(obj(), index++, (KNativePtr)element); }

    ObjHeader* obj() { return arrayHolder.obj(); }

    int index;
    int skipCount;
    ObjHolder arrayHolder;
};

_Unwind_Reason_Code depthCountCallback(struct _Unwind_Context* context, void* arg) {
    int* result = reinterpret_cast<int*>(arg);
    (*result)++;
    return _URC_NO_REASON;
}

_Unwind_Reason_Code unwindCallback(struct _Unwind_Context* context, void* arg) {
    Backtrace* backtrace = reinterpret_cast<Backtrace*>(arg);
    if (backtrace->skipCount > 0) {
        backtrace->skipCount--;
        return _URC_NO_REASON;
    }

#if (__MINGW32__ || __MINGW64__)
    _Unwind_Ptr address = _Unwind_GetRegionStart(context);
#else
    _Unwind_Ptr address = _Unwind_GetIP(context);
#endif
    // We run the unwinding process in the native thread state. But setting a next element
    // requires writing to a Kotlin array which must be performed in the runnable thread state.
    kotlin::ThreadStateGuard guard(kotlin::ThreadState::kRunnable);
    backtrace->setNextElement(address);

    return _URC_NO_REASON;
}
#endif

THREAD_LOCAL_VARIABLE bool disallowSourceInfo = false;

#if !KONAN_NO_BACKTRACE && !USE_GCC_UNWIND
SourceInfo getSourceInfo(void* symbol) {
    return disallowSourceInfo ? SourceInfo{.fileName = nullptr, .lineNumber = -1, .column = -1} : Kotlin_getSourceInfo(symbol);
}
#endif

} // namespace

// TODO: this implementation is just a hack, e.g. the result is inexact;
// however it is better to have an inexact stacktrace than not to have any.
extern "C" NO_INLINE OBJ_GETTER0(Kotlin_getCurrentStackTrace) {
#if KONAN_NO_BACKTRACE
    return AllocArrayInstance(theNativePtrArrayTypeInfo, 0, OBJ_RESULT);
#else
    // Skips first 2 elements as irrelevant: this function and primary Throwable constructor.
    constexpr int kSkipFrames = 2;
#if USE_GCC_UNWIND
    int depth = 0;
    CallWithThreadState<ThreadState::kNative>(_Unwind_Backtrace, depthCountCallback, static_cast<void*>(&depth));
    Backtrace result(depth, kSkipFrames);
    if (result.obj()->array()->count_ > 0) {
        CallWithThreadState<ThreadState::kNative>(_Unwind_Backtrace, unwindCallback, static_cast<void*>(&result));
    }
    RETURN_OBJ(result.obj());
#else
    const int maxSize = 32;
    void* buffer[maxSize];

    int size = kotlin::CallWithThreadState<kotlin::ThreadState::kNative>(backtrace, buffer, maxSize);
    if (size < kSkipFrames) return AllocArrayInstance(theNativePtrArrayTypeInfo, 0, OBJ_RESULT);

    ObjHolder resultHolder;
    ObjHeader* result = AllocArrayInstance(theNativePtrArrayTypeInfo, size - kSkipFrames, resultHolder.slot());
    for (int index = kSkipFrames; index < size; ++index) {
        Kotlin_NativePtrArray_set(result, index - kSkipFrames, buffer[index]);
    }
    RETURN_OBJ(result);
#endif
#endif // !KONAN_NO_BACKTRACE
}

KStdVector<KStdString> kotlin::GetStackTraceStrings(void* const* stackTrace, size_t stackTraceSize) noexcept {
#if KONAN_NO_BACKTRACE
    KStdVector<KStdString> strings;
    strings.push_back("<UNIMPLEMENTED>");
    return strings;
#else
    KStdVector<KStdString> strings;
    strings.reserve(stackTraceSize);
#if USE_GCC_UNWIND
    for (size_t index = 0; index < stackTraceSize; ++index) {
        KNativePtr address = stackTrace[index];
        char symbol[512];
        if (!AddressToSymbol(address, symbol, sizeof(symbol))) {
            // Make empty string:
            symbol[0] = '\0';
        }
        char line[512];
        konan::snprintf(line, sizeof(line) - 1, "%s (%p)", symbol, (void*)(intptr_t)address);
        strings.push_back(line);
    }
#else
    if (stackTraceSize > 0) {
        char** symbols = backtrace_symbols(stackTrace, static_cast<int>(stackTraceSize));
        RuntimeCheck(symbols != nullptr, "Not enough memory to retrieve the stacktrace");

        for (size_t index = 0; index < stackTraceSize; ++index) {
            KNativePtr address = stackTrace[index];
            auto sourceInfo = getSourceInfo(address);
            const char* symbol = symbols[index];
            const char* result;
            char line[1024];
            if (sourceInfo.fileName != nullptr) {
                if (sourceInfo.lineNumber != -1) {
                    konan::snprintf(
                            line, sizeof(line) - 1, "%s (%s:%d:%d)", symbol, sourceInfo.fileName, sourceInfo.lineNumber, sourceInfo.column);
                } else {
                    konan::snprintf(line, sizeof(line) - 1, "%s (%s:<unknown>)", symbol, sourceInfo.fileName);
                }
                result = line;
            } else {
                result = symbol;
            }
            strings.push_back(result);
        }
        // Not konan::free. Used to free memory allocated in backtrace_symbols where malloc is used.
        free(symbols);
    }
#endif
    return strings;
#endif // !KONAN_NO_BACKTRACE
}

void kotlin::DisallowSourceInfo() {
    disallowSourceInfo = true;
}

void kotlin::PrintStackTraceStderr() {
    // TODO: This is intended for runtime use. Try to avoid memory allocations and signal unsafe functions.

    kotlin::ThreadStateGuard guard(kotlin::ThreadState::kRunnable, true);

    ObjHolder stackTrace;
    Kotlin_getCurrentStackTrace(stackTrace.slot());
    const KNativePtr* array = PrimitiveArrayAddressOfElementAt<KNativePtr>(stackTrace.obj()->array(), 0);
    size_t size = stackTrace.obj()->array()->count_;
    auto stackTraceStrings = CallWithThreadState<ThreadState::kNative>(GetStackTraceStrings, array, size);
    for (auto& frame : stackTraceStrings) {
        konan::consoleErrorUtf8(frame.c_str(), frame.size());
        konan::consoleErrorf("\n");
    }
}
