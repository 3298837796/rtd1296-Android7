INCLUDE(../../../../include/config.txt)
SET(LIBNAME InputMP4)
SET(LIBRARY_OUTPUT_PATH ../../../../lib/)


SET(rm " /bin/rm -f")
SET(CC " ${MYCC}")
SET(LINK " -=LINK=-")
SET(DEFS )
SET(AR " ${MYAR} cq")
SET(RANLIB " ${MYRANLIB}")

SET(INCLUDES ${INCLUDES} " ${CMAKE_CURRENT_SOURCE_DIR}/.")
SET(INCLUDES ${INCLUDES} " ${CMAKE_CURRENT_SOURCE_DIR}/../../../Include")
SET(INCLUDES ${INCLUDES} " ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../common/IPC/include")
SET(INCLUDES  ${INCLUDES} " ${CMAKE_CURRENT_SOURCE_DIR}/../../../Include/Filters")
SET(INCLUDES ${INCLUDES} " ${CMAKE_CURRENT_SOURCE_DIR}/../../../Include")
SET(INCLUDES ${INCLUDES} " ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../common/IPC/include")
SET(INCLUDES ${INCLUDES} " ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../common/IPC/generate/include/system")

SET(DEFINES ${DEFINES} " ${DEFS} -DSYS_UNIX=1 -DHAVE_STRING_H -DRDDEBUG -DINPUT_USE_IO_PLUGIN")

IF(NOT TARGET_BOARD STREQUAL NO)
SET(DEFINES ${DEFINES} " -DTARGET_BOARD")
ENDIF(NOT TARGET_BOARD STREQUAL NO)

IF(USE_ZLIB STREQUAL YES)

ENDIF(USE_ZLIB STREQUAL YES)



#SET(CFLAGS  ${DEFINES})

SET(CXXFLAGS ${CXXFLAGS} " ${DEFINES}")

IF(__NAV_USE_DLL_PLUGIN__ STREQUAL YES)
SET(LIBNAME ../../../../lib/InputMP4.so)
SET(CFLAGS ${CFLAGS} " -fPIC -D__NAV_USE_DLL_PLUGIN__")
ELSE(__NAV_USE_DLL_PLUGIN__ STREQUAL YES)
#SET(LIBNAME ../../../../lib/libInputMP4.a)
ENDIF(__NAV_USE_DLL_PLUGIN__ STREQUAL YES)

SET(SRCS InputMP4.c mp4_parse.c mp4ff.c mp4atom.c mp4util.c mp4_bits.c mp4.c mp4_progconfig.c mp4_stream.c)
SET(OBJS InputMP4.o mp4_parse.o mp4ff.o mp4atom.o mp4util.o mp4_bits.o mp4.o mp4_progconfig.o mp4_stream.o)

SET(PLAY_BIG_FILE YES)

IF(PLAY_BIG_FILE STREQUAL YES)
SET(SRCS ${SRCS} mp4_getinfo.c)
SET(OBJS ${OBJS} mp4_getinfo.o)
SET(DEFINES ${DEFINES} " -DBIG_FILE")
ENDIF(PLAY_BIG_FILE STREQUAL YES)

IF(__NAV_USE_DLL_PLUGIN__ STREQUAL YES)
ELSE(__NAV_USE_DLL_PLUGIN__ STREQUAL YES)
ENDIF(__NAV_USE_DLL_PLUGIN__ STREQUAL YES)

SET(CFLAGS  ${CFLAGS} ${DEFINES})
message(${CFLAGS})

INCLUDE_DIRECTORIES(${INCLUDES})
ADD_DEFINITIONS(${CFLAGS} ${DEBUGFLAG} ${WARNING})
ADD_LIBRARY(${LIBNAME} ${SRCS})

string(REGEX REPLACE  ";" " " APCFG_DVR  ${APCFG_DVR})
string(REGEX REPLACE  ";" " " CHIP_MODEL_DEFS ${CHIP_MODEL_DEFS})
string(REGEX REPLACE  ";" " " APCFG_DEFS ${APCFG_DEFS})
#string(REGEX REPLACE  ";" " " BUILD_OPTIONS ${BUILD_OPTIONS})
string(REGEX REPLACE  ";" " " DEFINES ${DEFINES})
string(REGEX REPLACE  ";" " " APCFG_FLAGS ${APCFG_FLAGS})
