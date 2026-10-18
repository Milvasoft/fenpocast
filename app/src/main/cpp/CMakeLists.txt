cmake_minimum_required(VERSION 3.22)
project(fenpocast C CXX)

set(CMAKE_C_STANDARD 11)
set(CMAKE_CXX_STANDARD 17)

set(THIRD_PARTY ${CMAKE_CURRENT_SOURCE_DIR}/third_party)
set(UXPLAY_LIB ${THIRD_PARTY}/uxplay/lib)

# BoringSSL provides the libcrypto API UxPlay was written against (EVP, BN, AES, SHA).
# It is linked statically, so the APK carries no extra shared library and stays 16 KB aligned.
set(BUILD_TESTING OFF CACHE BOOL "" FORCE)
add_subdirectory(${THIRD_PARTY}/boringssl boringssl EXCLUDE_FROM_ALL)

# libplist: binary and XML property lists used by the AirPlay handshake.
file(GLOB PLIST_SOURCES
    ${THIRD_PARTY}/libplist/src/*.c
    ${THIRD_PARTY}/libplist/libcnary/*.c)
add_library(plist STATIC ${PLIST_SOURCES})
target_include_directories(plist
    PUBLIC ${THIRD_PARTY}/libplist/include
    PRIVATE ${THIRD_PARTY}/libplist/src ${THIRD_PARTY}/libplist/libcnary/include)
target_compile_definitions(plist PRIVATE HAVE_STRNDUP HAVE_MEMMEM HAVE_TM_TM_GMTOFF HAVE_TM_TM_ZONE
    PACKAGE_STRING="libplist" PACKAGE_VERSION="2.7.0")
target_compile_options(plist PRIVATE -Wno-everything)

# Apple's ALAC decoder for audio-only streams (ct = 2).
file(GLOB ALAC_SOURCES ${THIRD_PARTY}/alac/*.c ${THIRD_PARTY}/alac/*.cpp)
add_library(alac STATIC ${ALAC_SOURCES})
target_include_directories(alac PUBLIC ${THIRD_PARTY}/alac)
target_compile_definitions(alac PRIVATE TARGET_RT_LITTLE_ENDIAN=1)
target_compile_options(alac PRIVATE -Wno-everything)

# UxPlay's receiver core without its dns_sd and mdnsd back ends: receiver/dnssd_android.c
# hands the service records to Android's NsdManager instead.
file(GLOB UXPLAY_SOURCES
    ${UXPLAY_LIB}/*.c
    ${UXPLAY_LIB}/playfair/*.c
    ${UXPLAY_LIB}/llhttp/*.c)
add_library(airplay STATIC ${UXPLAY_SOURCES} receiver/dnssd_android.c)
target_include_directories(airplay PUBLIC ${UXPLAY_LIB} ${UXPLAY_LIB}/playfair ${UXPLAY_LIB}/llhttp)
target_compile_definitions(airplay PUBLIC
    STANDALONE TARGET_POSIX _LINUX _REENTRANT NOHOLD PLIST_210 PLIST_230
    __STDC_CONSTANT_MACROS __STDC_LIMIT_MACROS OPENSSL_API_COMPAT=0x10101000L)
# NDEBUG: raop_handle_eos() and a few other paths assert on states a TV can reach (an HLS stream
# that already ended); with the asserts off they fall through to the safe branch.
target_compile_definitions(airplay PRIVATE NDEBUG)
target_compile_options(airplay PRIVATE -Wno-everything)
target_link_libraries(airplay PUBLIC plist crypto)

add_library(fenpocast SHARED
    receiver/receiver.c
    receiver/alac_decoder.cpp)
target_compile_options(fenpocast PRIVATE -Wall -Wextra -Wno-unused-parameter)
target_link_libraries(fenpocast airplay alac log android)
target_link_options(fenpocast PRIVATE "-Wl,-z,max-page-size=16384")
