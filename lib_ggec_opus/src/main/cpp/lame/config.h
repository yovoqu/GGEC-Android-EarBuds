/*
 * Hand-maintained config.h for building LAME 3.100 on Android NDK.
 * Replaces autotools-generated config.h.
 *
 * IMPORTANT: LAME source widely uses `#ifdef SOMETHING` (not `#if SOMETHING`)
 * to gate platform-specific code paths, so any HAVE_xxx that should evaluate
 * to "off" MUST be `#undef`'d here, NOT `#define`'d to 0. Otherwise the gate
 * passes and we drag in the platform-specific code (e.g. SSE intrinsics on
 * arm64), which then breaks the build.
 */

#ifndef LAME_ANDROID_CONFIG_H
#define LAME_ANDROID_CONFIG_H

#define PACKAGE                 "lame"
#define VERSION                 "3.100"

/* C99 / POSIX features the NDK supports unconditionally. */
#define STDC_HEADERS            1
#define HAVE_STDLIB_H           1
#define HAVE_STDINT_H           1
#define HAVE_INTTYPES_H         1
#define HAVE_STRING_H           1
#define HAVE_STRINGS_H          1
#define HAVE_MEMORY_H           1
#define HAVE_SYS_TYPES_H        1
#define HAVE_SYS_STAT_H         1
#define HAVE_UNISTD_H           1
#define HAVE_LIMITS_H           1
#define HAVE_FCNTL_H            1
#define HAVE_ERRNO_H            1
#define HAVE_CTYPE_H            1
#define HAVE_MATH_H             1

/* Standard math/string functions bionic always provides. */
#define HAVE_STRTOL             1
#define HAVE_STRCHR             1
#define HAVE_MEMCPY             1
#define HAVE_MEMSET             1
#define HAVE_ATOL               1

/* Sizes assumed by LAME on all our supported ABIs (armeabi-v7a / arm64-v8a /
 * x86 / x86_64). LAME relies on these being correct for int math fast paths. */
#define SIZEOF_SHORT            2
#define SIZEOF_INT              4
#define SIZEOF_LONG             4  /* 4 on 32-bit ABIs; harmless on LP64 because LAME never assumes long==pointer */
#define SIZEOF_LONG_LONG        8
#define SIZEOF_FLOAT            4
#define SIZEOF_DOUBLE           8
#define SIZEOF_INT8_T           1
#define SIZEOF_INT16_T          2
#define SIZEOF_INT32_T          4
#define SIZEOF_INT64_T          8
#define SIZEOF_UINT8_T          1
#define SIZEOF_UINT16_T         2
#define SIZEOF_UINT32_T         4
#define SIZEOF_UINT64_T         8
#define SIZEOF_SIZE_T           4

/* ---- Features explicitly OFF: MUST be #undef, not #define 0 ---- */
#undef  HAVE_XMMINTRIN_H        /* SSE intrinsics; CMake adds -DHAVE_XMMINTRIN_H=1 on x86 only */
#undef  HAVE_AV_LRINTF
#undef  USE_FAST_LOG
#undef  HAVE_MPGLIB             /* we only build the encoder */
#undef  HAVE_NASM
#undef  HAVE_GTK
#undef  ABORTFP

/* ---- ieee754 typedefs ----
 * LAME 3.100 (rbrito mirror) uses `ieee754_float32_t` / `ieee754_float64_t`
 * unconditionally in util.h, even when USE_FAST_LOG is off. Those names come
 * from glibc <bits/ieee754.h> on Linux and don't exist on Android bionic, so
 * we typedef them here. Float/double layout on every NDK ABI we ship is
 * IEEE-754 by spec, so the aliases are accurate. */
typedef float                   ieee754_float32_t;
typedef double                  ieee754_float64_t;

#endif /* LAME_ANDROID_CONFIG_H */
