/*
 * Copyright (c) 2014 Travis Geiselbrecht
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
#include <asm.h>
#include <arch/asm_macros.h>

.section .text.boot.vectab
.align 12

#define lr x30
#define regsave_long_offset 0xf0
#define regsave_short_offset 0x90
#define regsave_bottom_length 0x30


.macro regsave_long
sub  sp, sp, #regsave_bottom_length
push x28, x29
push x26, x27
push x24, x25
push x22, x23
push x20, x21
push x18, x19
push x16, x17
push x14, x15
push x12, x13
push x10, x11
push x8, x9
push x6, x7
push x4, x5
push x2, x3
push x0, x1
mov  x0, sp
add  x0, x0, #(regsave_long_offset + regsave_bottom_length)
stp  lr, x0, [sp, #regsave_long_offset]
mov  x0, #regsave_long_offset
bl regsave_extra
.endm

.macro regsave_short
sub  sp, sp, #regsave_bottom_length
push x16, x17
push x14, x15
push x12, x13
push x10, x11
push x8, x9
push x6, x7
push x4, x5
push x2, x3
push x0, x1
mov  x0, sp
add  x0, x0, #(regsave_short_offset + regsave_bottom_length)
stp  lr, x0, [sp, #regsave_short_offset]
mov  x0, #regsave_short_offset
bl regsave_extra
.endm

.macro regrestore_long
mov x0, #regsave_long_offset
bl regrestore_extra
ldp  lr, x0, [sp, #regsave_long_offset]
pop x0, x1
pop x2, x3
pop x4, x5
pop x6, x7
pop x8, x9
pop x10, x11
pop x12, x13
pop x14, x15
pop x16, x17
pop x18, x19
pop x20, x21
pop x22, x23
pop x24, x25
pop x26, x27
pop x28, x29
add sp, sp, #regsave_bottom_length
.endm

.macro regrestore_short
mov x0, #regsave_short_offset
bl regrestore_extra
ldp  lr, x0, [sp, #regsave_short_offset]
pop x0, x1
pop x2, x3
pop x4, x5
pop x6, x7
pop x8, x9
pop x10, x11
pop x12, x13
pop x14, x15
pop x16, x17
add sp, sp, #regsave_bottom_length
.endm

.macro invalid_exception, which
    regsave_long
    mov x1, #\which
    mov x0, sp
    bl  arm64_invalid_exception
    b   .
.endm

.macro irq_exception
    regsave_short
    msr daifclr, #1 /* reenable fiqs once elr and spsr have been saved */
    mov x0, sp
    bl  platform_irq
    cbz x0, .Lirq_exception_no_preempt\@
    bl  thread_preempt
.Lirq_exception_no_preempt\@:
    msr daifset, #1 /* disable fiqs to protect elr and spsr restore */
    b   arm64_exc_shared_restore_short
.endm

FUNCTION(arm64_exception_base)

/* exceptions from current EL, using SP0 */
LOCAL_FUNCTION(arm64_sync_exc_current_el_SP0)
    invalid_exception 0

.org 0x080
LOCAL_FUNCTION(arm64_irq_current_el_SP0)
    invalid_exception 1

.org 0x100
LOCAL_FUNCTION(arm64_fiq_current_el_SP0)
    invalid_exception 2

.org 0x180
LOCAL_FUNCTION(arm64_err_exc_current_el_SP0)
    invalid_exception 3

/* exceptions from current EL, using SPx */
.org 0x200
LOCAL_FUNCTION(arm64_sync_exc_current_el_SPx)
    regsave_long
    mov x0, sp
    bl  arm64_sync_exception
    b  arm64_exc_shared_restore_long

.org 0x280
LOCAL_FUNCTION(arm64_irq_current_el_SPx)
    irq_exception

.org 0x300
LOCAL_FUNCTION(arm64_fiq_current_el_SPx)
    regsave_short
    mov x0, sp
    bl  platform_fiq
    b  arm64_exc_shared_restore_short

.org 0x380
LOCAL_FUNCTION(arm64_err_exc_current_el_SPx)
    invalid_exception 0x13

/* exceptions from lower EL, running arm64 */
.org 0x400
LOCAL_FUNCTION(arm64_sync_exc_lower_el_64)
    regsave_long
    mov x0, sp
    bl  arm64_sync_exception
    b  arm64_exc_shared_restore_long

.org 0x480
LOCAL_FUNCTION(arm64_irq_lower_el_64)
    irq_exception

.org 0x500
LOCAL_FUNCTION(arm64_fiq_lower_el_64)
    regsave_short
    mov x0, sp
    bl  platform_fiq
    b  arm64_exc_shared_restore_short

.org 0x580
LOCAL_FUNCTION(arm64_err_exc_lower_el_64)
    invalid_exception 0x23

/* exceptions from lower EL, running arm32 */
.org 0x600
LOCAL_FUNCTION(arm64_sync_exc_lower_el_32)
    regsave_long
    mov x0, sp
    bl  arm64_sync_exception
    b  arm64_exc_shared_restore_long

.org 0x680
LOCAL_FUNCTION(arm64_irq_lower_el_32)
    irq_exception

.org 0x700
LOCAL_FUNCTION(arm64_fiq_lower_el_32)
    regsave_short
    mov x0, sp
    bl  platform_fiq
    b  arm64_exc_shared_restore_short

.org 0x780
LOCAL_FUNCTION(arm64_err_exc_lower_el_32)
    invalid_exception 0x33

LOCAL_FUNCTION(arm64_exc_shared_restore_long)
    regrestore_long
    eret

LOCAL_FUNCTION(arm64_exc_shared_restore_short)
    regrestore_short
    eret

LOCAL_FUNCTION(regsave_extra) # X0, short/long offset
	#X8 is offset
	mov x8, sp
	add x8, x8, x0
	#X9 is EL
	mrs x9, CurrentEL
	lsr x9, x9, #2

	# NOw, X0:usp, X1:ELR, X2:SPSR, X3:PADDING
	switch_el x9, 1f, 2f, 3f
1:	mrs x1, elr_el1
	mrs x2, spsr_el1
	b 0f
2:	mrs x1, elr_el2
	mrs x2, spsr_el2
	b 0f
3:
	mrs x1, elr_el3
	mrs x2, spsr_el3
0:
	mrs x0, sp_el0
	mov x3, #0
	stp x0, x1, [x8, #16]
	stp x2, x3, [x8, #32]
	ret

LOCAL_FUNCTION(regrestore_extra) # X0, short/long offset
	#X8 is offset
	mov x8, sp
	add x8, x8, x0
	#X9 is EL
	mrs x9, CurrentEL
	lsr x9, x9, #2

	# NOw, X0:usp, X1:ELR, X2:SPSR, X3:PADDING
	ldp  x0, x1, [x8, #16]
	ldp  x2, x3, [x8, #32]

	switch_el x9, 1f, 2f, 3f
1:	msr elr_el1, x1
	msr spsr_el1, x2
	b 0f
2:	msr elr_el2, x1
	msr spsr_el2, x2
	b 0f
3:	msr elr_el3, x1
	msr spsr_el3, x2
0:
	msr sp_el0, x0
	ret
