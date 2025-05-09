#Determine if the system is a powerpc system
#Add other auto-detect for CPU as required
#Set to X86 if not a PPC, however may be performing cross compilation on X86 so allow manual setting of
#CPU after detection

include ../../../../../include/MakeConfig
LIB_DIR = ../../../../../lib

ifeq ($(TARGET_BOARD), YES)
    CPU = MIPS
else
    CPU = X86
endif

ifeq "$(findstring sun4u,$(shell uname -m))" "sun4u"
   CPU = POWERPC
endif
ifeq "$(findstring sparc64,$(shell uname -m))" "sparc64"
   CPU = POWERPC
endif
ifeq "$(findstring ppc,$(shell uname -m))" "ppc"
   CPU = POWERPC
endif
ifeq "$(findstring Power Macintosh,$(shell uname -m))" "Power Macintosh"
   CPU = POWERPC
endif

#echo $(CPU)
    	
# force CPU here for example when performing cross compilation or other CPUs not
#explicitly supported yet

#CPU = X86
#CPU = ARM
#CPU=POWERPC

# If CPU = ARM, force ARM_DEVICE (sigma8620, sigma8510) here 
#export ARM_DEVICE = sigma8620
ifeq "$(strip $(CPU))" "ARM"
export ARM_DEVICE = sigma8620
endif #CPU=ARM

# Platform architecture
ifeq "$(strip $(CPU))" "X86"
X86ACCEL = 1
ARCH =
#### Processor specific optimization - asm routines etc. Comment out for generic implementations
#test for running on Mac OS X (Darwin) as nasm support is not completed yet
ifeq "$(findstring Darwin,$(shell uname -s))" "Darwin"
CPU_OPTIMIZE = -DARCH_GENERIC
X86ACCEL =
else
#test for running on Debian Linux as nasm is not yet supported
ifeq "$(findstring Linux,$(shell uname -a))" "Linux"
CPU_OPTIMIZE = -DARCH_GENERIC
X86ACCEL =
endif
endif
ifdef X86ACCEL
CPU_OPTIMIZE = -DARCH_X86 -DX86
else
CPU_OPTIMIZE = -DARCH_GENERIC -DX86
endif
## create seperate make variable to enable x86 acceleration in makefiles
else
ifeq "$(strip $(CPU))" "ARM"
ARCH = arm-elf-
#### Processor specific optimization - asm routines etc
# Arm currently uses generic code
CPU_OPTIMIZE = -DARCH_GENERIC

#If this ia an ARM processor, set variable to indicate if this is a Sigma 85xx or 86xx
SIGMA_PLATFORM =1
USE_DPR = 1
else

ifeq "$(strip $(CPU))" "POWERPC"
ARCH = 
#### Processor specific optimization - asm routines etc
# Arm currently uses generic code
CPU_OPTIMIZE = -DARCH_GENERIC -DPOWERPC
endif
endif
endif

ifeq "$(strip $(CPU))" "MIPS"
ARCH = mipsel-linux-
#### Processor specific optimization - asm routines etc
# Arm currently uses generic code
CPU_OPTIMIZE = -DARCH_GENERIC 
endif


#Tools
CC = $(MYCC)
CXX = $(MYCXX)
LDXX=$(CXX)
LD=$(CC)
AR = $(MYAR)
SED = sed
MV = mv
AS = nasm

LARGE_FILE_SUPPORT=1

PLATFORM = -DLINUX

INCLUDE_DIRS += -I../../import/DivXPrimitives -I../../../import/DivXPrimitives -I../../common -I ../../../common
#Running on Sigma platform (85xx, 86xx)
ifdef SIGMA_PLATFORM

PLATFORM += -D__Sigma__
FLAT_BINARY=1
ifdef FLAT_BINARY
LDFLAGS = -Wl,-elf2flt="-s32768"
endif

ifeq ($(ARM_DEVICE),sigma8620)
LARGE_FILE_SUPPORT=1
else
#Large file support may be missing from 85xx libraries 
LARGE_FILE_SUPPORT=0

#With 85xx library, wchar functions are not supported
#Define WCHARUTILITY to include local functions performing them
WCHARUTILITY=1
endif

#With EM85xx tool chain, C++ files are best linked using arl-elf-gcc as arm-elf-g++ looks for
#libstc++ which is missing
LDXX=$(LD)

endif #SIGMA_PLATFORM

ifeq "$(strip $(CONFIG))" "debug"
DEBUG = -g
OPTIMIZE =
else
DEBUG =
OPTIMIZE = -O2 
endif

#Additional debugging output for codec libraries
#DEBUG += -D_DEBUG

# define the memory alignment for your archatecture, 
# DIVX_ARCH_64BIT, DIVX_ARCH_32BIT, DIVX_ARCH_16BIT, DIVX_ARCH_8BIT
MEM_ALIGN = -DDIVX_ARCH_32BIT

#Select Large File Support (>2GB)
# assign LARGE_FILE_SUPPORT to 0 to use 32 bit file I/O calls
ifeq ($(LARGE_FILE_SUPPORT),1)
LFS = -DLARGE_FILE_SUPPORT -D_LARGEFILE_SOURCE -D_LARGEFILE64_SOURCE -D_FILE_OFFSET_BITS=64
$(warning USING 64 BIT FILE I/O)
else
$(warning USING 32 BIT FILE I/O. File size limited to 2GByte)
LFS= -DSMALL_FILE_SUPPORT

endif

#if platform has native 64 bit support, set flag to cause DivXInt.h to use it
#other 64 bit macros must be used
PLATFORM += -DDIVXINT_NATIVE_64BIT

## Compilation flags
CFLAGS += $(DEBUG) $(PLATFORM) $(OPTIMIZE) $(CPU_OPTIMIZE) $(INCLUDE_DIRS) $(LFS) $(MEM_ALIGN)
CXXFLAGS = $(CFLAGS)
LDFLAGS += $(OPTIMIZE)
ARFLAGS +=


#X86 Assmebly rule
%.o: %.asm
	nasm -g -felf -DPIC $(INCLUDE_DIRS) $< -o $@


