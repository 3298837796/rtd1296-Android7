##----------------------------------------------------------------------------
# Created with genmake.pl v1.1 on Tue Sep  7 10:40:25 2004
# genmake.pl home: http://muquit.com/muquit/software/
# Copryright: GNU GPL (http://www.gnu.org/copyleft/gpl.html)
##----------------------------------------------------------------------------
-include ../../../../branch_src_sharedMemory_integration/Tools/MConfig/.config
include ../../../../include/MakeConfig
rm=/bin/rm -f
CC= $(MYCC)
LINK= -=LINK=-
DEFS=  
AR= $(MYAR) cq
RANLIB= $(MYRANLIB)
SHOW_COMMAND = 
INCLUDES= -I. -I../../../Include -I../../../../../common/IPC/include -I../../../Include/Filters -I../../../../../common/IPC/generate/include/system

DEFINES+= -DERROR_CONCEALMENT_LEVEL=$(ERROR_CONCEALMENT_LEVEL)
DEFINES+= $(INCLUDES) $(DEFS) -DSYS_UNIX=1 -DHAVE_STRING_H -DRDDEBUG -DINPUT_USE_IO_PLUGIN
ifneq ($(TARGET_BOARD), NO)
DEFINES += -DTARGET_BOARD
endif

CFLAGS= $(DEFINES) $(COMPILE_OPT)
CXXFLAGS +=  $(DEFINES) $(COMPILE_OPT)

CFLAGS += -Wno-multichar

ifeq ($(__NAV_USE_DLL_PLUGIN__), YES)
LIBNAME= ../../../../lib/InputRTP.so
CFLAGS+= -fPIC -D__NAV_USE_DLL_PLUGIN__
else
LIBNAME= ../../../../lib/libInputAV.a
endif

###################################################
# MODE
###################################################
#VC_VIDEO_SOURCE = LOOP_THROUGH
#VC_AUDIO_SOURCE = LOOP_THROUGH

###################################################
# Compiler Options - MISC
###################################################
#CFLAGS  += -DMANUAL_IO_INFO

###################################################
# Compiler Options - VIDEO
###################################################
#CFLAGS += -DPRINT_VIDEO_INFO
#CFLAGS += -DMJPG_FRAME_INTEGRITY_CHECK

ifeq ($(VC_VIDEO_SOURCE), LOOP_THROUGH)
CFLAGS += -DVIDEO_LOOP_THROUGH
CFLAGS += -DLOOPBACK_TEST
endif

###################################################
# Compiler Options - AUDIO
###################################################
#CFLAGS += -DPRINT_AUDIO_INFO
CFLAGS += -DUSE_AUDIO_PTS
#CFLAGS += -DILBC_AUDIO_DROP_FRAME_TEST

ifeq ($(VC_AUDIO_SOURCE), LOOP_THROUGH)
CFLAGS += -DAUDIO_LOOP_THROUGH
CFLAGS += -DLOOPBACK_TEST
endif


###################################################
SRCS = InputAV.c
OBJS = InputAV.o


###################################################
# Targets
###################################################
.c.o:
	$(SHOW_COMMAND)$(rm) -f $@
	$(SHOW_COMMAND)$(CC) $(INCLUDES) $(CFLAGS) $(DEBUGFLAG) $(WARNING) -c  $*.c

.cpp.o:
	$(SHOW_COMMAND)$(rm) -f $@
	$(SHOW_COMMAND)$(CC) $(CFLAGS) $(DEBUGFLAG) -c $*.cpp

all: $(LIBNAME)

ifeq ($(__NAV_USE_DLL_PLUGIN__), YES)
$(LIBNAME) : $(OBJS)
	$(SHOW_COMMAND)$(rm) $@
	$(SHOW_COMMAND)$(CC) -shared -o $(LIBNAME) $(OBJS)
else
$(LIBNAME) : $(OBJS)
	$(SHOW_COMMAND)$(rm) $@
	$(SHOW_COMMAND)$(AR) $@ $(OBJS)
	$(SHOW_COMMAND)$(RANLIB) $@
endif

clean:
	$(SHOW_COMMAND)$(rm) $(OBJS) $(LIBNAME) core *~ *.bak
