INCLUDE(../../../../include/config.txt)
SET(LIBNAME InputFLV)
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
SET(INCLUDES ${INCLUDES} " ${CMAKE_CURRENT_SOURCE_DIR}/../../../../../common/IPC/generate/include/system")
SET(INCLUDES  ${INCLUDES} " ${CMAKE_CURRENT_SOURCE_DIR}/./libMenuController")

SET(DEFINES ${DEFINES} " ${DEFS} -DSYS_UNIX=1 -DHAVE_STRING_H -DRDDEBUG")
SET(DEFINES ${DEFINES} " -DLINUX -DDIVXINT_NATIVE_64BIT -DLARGE_FILE_SUPPORT -D_LARGEFILE_SOURCE -D_FILE_OFFSET_BITS=64")

SET(CFLAGS " ${DEFINES} ${COMPILE_OPT}")
SET(CXXFLAGS ${CXXFLAGS} " ${DEFINES} ${COMPILE_OPT}")

IF(__NAV_USE_DLL_PLUGIN__ STREQUAL YES)
SET(LIBNAME ../../../../lib/InputFLV.so)
SET(CFLAGS ${CFLAGS} " -fPIC -D__NAV_USE_DLL_PLUGIN__")
ELSE(__NAV_USE_DLL_PLUGIN__ STREQUAL YES)
#SET(LIBNAME ../../../../lib/libInputFLV.a)
ENDIF(__NAV_USE_DLL_PLUGIN__ STREQUAL YES)

SET(CFLAGS ${CFLAGS} " -Wno-multichar -msoft-float")

SET(SRCS InputFLV.c flv_internals.c Security.c)
SET(OBJS InputFLV.o flv_internals.o Security.o)
IF(__NAV_USE_DLL_PLUGIN__ STREQUAL YES)
ELSE(__NAV_USE_DLL_PLUGIN__ STREQUAL YES)
ENDIF(__NAV_USE_DLL_PLUGIN__ STREQUAL YES)

INCLUDE_DIRECTORIES(${INCLUDES})
ADD_DEFINITIONS(${CFLAGS} ${DEBUGFLAG} ${WARNING})
ADD_LIBRARY(${LIBNAME} ${SRCS})
string(REGEX REPLACE  ";" " " APCFG_DVR  ${APCFG_DVR})
string(REGEX REPLACE  ";" " " CHIP_MODEL_DEFS ${CHIP_MODEL_DEFS})
string(REGEX REPLACE  ";" " " APCFG_DEFS ${APCFG_DEFS})
#string(REGEX REPLACE  ";" " " BUILD_OPTIONS ${BUILD_OPTIONS})
string(REGEX REPLACE  ";" " " DEFINES ${DEFINES})
string(REGEX REPLACE  ";" " " APCFG_FLAGS ${APCFG_FLAGS})
