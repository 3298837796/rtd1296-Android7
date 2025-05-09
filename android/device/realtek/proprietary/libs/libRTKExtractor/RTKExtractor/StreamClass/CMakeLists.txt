INCLUDE(../../include/config.txt)

SET(rm " /bin/rm -f")
SET(CC " ${MYCXX}")
SET(AR " ${MYAR} cq")
SET(RANLIB " ${MYRANLIB}")
#SET(LIBNAME ../../lib/libstream.a)
SET(LIBNAME stream)
SET(LIBRARY_OUTPUT_PATH ../../lib)



SET(INCLUDES ${INCLUDES} ${CMAKE_CURRENT_SOURCE_DIR}/.)
SET(INCLUDES  ${INCLUDES} ${CMAKE_CURRENT_SOURCE_DIR}/../Include)
IF(NOT USE_VIDEO_DECODER_SIMULATOR STREQUAL NO)
SET(MYDEFS ${MYDEFS} " -DUSE_VIDEO_DECODER_SIMULATOR")
ENDIF(NOT USE_VIDEO_DECODER_SIMULATOR STREQUAL NO)

IF(NOT USE_SMOOTH_REVERSE STREQUAL NO)
SET(MYDEFS ${MYDEFS} " -DUSE_SMOOTH_REVERSE")
ENDIF(NOT USE_SMOOTH_REVERSE STREQUAL NO)

SET(SRCS CMediaType.cpp CPin.cpp CFilter.cpp CMediaSample.cpp CMemListAllocator.cpp CMemRingAllocator.cpp CReadPointerHandle.cpp CEnumPins.cpp CFlowManager.cpp CEnumFilters.cpp CReferenceClock.cpp)
SET(OBJS CMediaType.o CPin.o CFilter.o CMediaSample.o CMemListAllocator.o CMemRingAllocator.o CReadPointerHandle.o CEnumPins.o CFlowManager.o CEnumFilters.o CReferenceClock.o)


IF(USE_IOCACHE STREQUAL YES)
SET(MYDEFS ${MYDEFS} " -DUSE_IOCACHE=1")
SET(SRCS ${SRCS} IOCache_MS.cpp)
SET(OBJS ${OBJS} IOCache_MS.o)
ENDIF(USE_IOCACHE STREQUAL YES)

SET(CFLAGS " ${DEFINES} -DSYS_UNIX=1 ${MYDEFS}")
string(REGEX REPLACE  ";" " " APCFG_DVR  ${APCFG_DVR})
string(REGEX REPLACE  ";" " " CHIP_MODEL_DEFS ${CHIP_MODEL_DEFS})
string(REGEX REPLACE  ";" " " APCFG_DEFS ${APCFG_DEFS})
#string(REGEX REPLACE  ";" " " BUILD_OPTIONS ${BUILD_OPTIONS})
string(REGEX REPLACE  ";" " " DEFINES ${DEFINES})
string(REGEX REPLACE  ";" " " APCFG_FLAGS ${APCFG_FLAGS})

message("stream:cflags:${DEBUGFLAG} =--= ${WARNING}")
INCLUDE_DIRECTORIES(${INCLUDES})
ADD_DEFINITIONS(${CFLAGS} ${DEBUGFLAG} ${WARNING})
ADD_LIBRARY(${LIBNAME} ${SRCS})
