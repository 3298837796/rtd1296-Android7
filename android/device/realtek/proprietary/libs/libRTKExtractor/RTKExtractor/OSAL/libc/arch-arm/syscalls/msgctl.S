/* autogenerated by gensyscalls.py */
#include <sys/glibc-syscalls.h>
#include "define.h"

    .text
    .type msgctl, #function
    .globl msgctl
    .align 4
    .fnstart

msgctl:
    .save   {r4, r7}
    stmfd   sp!, {r4, r7}
    ldr     r7, =__NR_msgctl
    swi     #0
    ldmfd   sp!, {r4, r7}
    movs    r0, r0
    bxpl    lr
    b       __set_arm_errno
    .fnend
