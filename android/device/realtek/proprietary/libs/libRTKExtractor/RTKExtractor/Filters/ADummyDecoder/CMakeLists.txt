INCLUDE(../../../include/config.txt)
SET(LIBNAME AudioDecoder)
SET(LIBRARY_OUTPUT_PATH ../../../lib/)

INCLUDE(config.txt)

SET(rm " /bin/rm -f")
SET(cp " /bin/cp -f")
SET(mkdir " /bin/mkdir -p")
SET(CC " ${MYCXX}")
SET(LINK " -=LINK=-")
SET(DEFS )
SET(AR " ${MYAR} cq")
SET(RANLIB " ${MYRANLIB}")
#SET(LIBNAME ../../../lib/libAudioDecoder.a)
SET(INSTALLHEADER ../../Include/AudioFilter)

SET(INCLUDES ${INCLUDES}  ${CMAKE_CURRENT_SOURCE_DIR}/.)
SET(INCLUDES ${INCLUDES}  ${CMAKE_CURRENT_SOURCE_DIR}/../../)
SET(INCLUDES ${INCLUDES}  ${CMAKE_CURRENT_SOURCE_DIR}/../../Include)
SET(INCLUDES ${INCLUDES}  ${CMAKE_CURRENT_SOURCE_DIR}/../../../../common/IPC/include)
SET(INCLUDES  ${INCLUDES}  ${CMAKE_CURRENT_SOURCE_DIR}/../../../../common/IPC/generate/include/system)

#message("${DEFS}")
SET(DEFINES ${DEFINES} " ${DDEF} -DSYS_UNIX=1")
SET(CFLAGS " ${DEFINES} ${COMPILE_OPT}")
SET(SRCS CAudioDecoder.cpp CAudioFilter.cpp CAudioPin.cpp CMultiAllocator.cpp CMultiPin.cpp   CBinaryFileReader.cpp CAudioQueue.cpp   CAudioDecPin.cpp)
#message(${CFLAGS})
SET(HEADERS *.h)

#ADD_SUBDIRECTORY(rpc)
EXEC_PROGRAM(make ${CMAKE_CURRENT_SOURCE_DIR}/rpc)


INCLUDE_DIRECTORIES(${INCLUDES})
ADD_DEFINITIONS(${CFLAGS} ${DEBUGFLAG} ${WARNING})
ADD_LIBRARY(${LIBNAME} ${SRCS})
string(REGEX REPLACE  ";" " " APCFG_DVR  ${APCFG_DVR})
string(REGEX REPLACE  ";" " " CHIP_MODEL_DEFS ${CHIP_MODEL_DEFS})
string(REGEX REPLACE  ";" " " APCFG_DEFS ${APCFG_DEFS})
#string(REGEX REPLACE  ";" " " BUILD_OPTIONS ${BUILD_OPTIONS})
string(REGEX REPLACE  ";" " " DEFINES ${DEFINES})
string(REGEX REPLACE  ";" " " APCFG_FLAGS ${APCFG_FLAGS})
