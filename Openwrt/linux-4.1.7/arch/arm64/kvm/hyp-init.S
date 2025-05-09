/*
 * Copyright (C) 2012,2013 - ARM Ltd
 * Author: Marc Zyngier <marc.zyngier@arm.com>
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License, version 2, as
 * published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

#include <linux/linkage.h>

#include <asm/assembler.h>
#include <asm/kvm_arm.h>
#include <asm/kvm_mmu.h>
#include <asm/pgtable-hwdef.h>
#include <asm/sysreg.h>

	.text
	.pushsection	.hyp.idmap.text, "ax"

	.align	11

ENTRY(__kvm_hyp_init)
	ventry	__invalid		// Synchronous EL2t
	ventry	__invalid		// IRQ EL2t
	ventry	__invalid		// FIQ EL2t
	ventry	__invalid		// Error EL2t

	ventry	__invalid		// Synchronous EL2h
	ventry	__invalid		// IRQ EL2h
	ventry	__invalid		// FIQ EL2h
	ventry	__invalid		// Error EL2h

	ventry	__do_hyp_init		// Synchronous 64-bit EL1
	ventry	__invalid		// IRQ 64-bit EL1
	ventry	__invalid		// FIQ 64-bit EL1
	ventry	__invalid		// Error 64-bit EL1

	ventry	__invalid		// Synchronous 32-bit EL1
	ventry	__invalid		// IRQ 32-bit EL1
	ventry	__invalid		// FIQ 32-bit EL1
	ventry	__invalid		// Error 32-bit EL1

__invalid:
	b	.

	/*
	 * x0: HYP boot pgd
	 * x1: HYP pgd
	 * x2: HYP stack
	 * x3: HYP vectors
	 */
__do_hyp_init:

	msr	ttbr0_el2, x0

	mrs	x4, tcr_el1
	ldr	x5, =TCR_EL2_MASK
	and	x4, x4, x5
	ldr	x5, =TCR_EL2_FLAGS
	orr	x4, x4, x5

#ifndef CONFIG_ARM64_VA_BITS_48
	/*
	 * If we are running with VA_BITS < 48, we may be running with an extra
	 * level of translation in the ID map. This is only the case if system
	 * RAM is out of range for the currently configured page size and number
	 * of translation levels, in which case we will also need the extra
	 * level for the HYP ID map, or we won't be able to enable the EL2 MMU.
	 *
	 * However, at EL2, there is only one TTBR register, and we can't switch
	 * between translation tables *and* update TCR_EL2.T0SZ at the same
	 * time. Bottom line: we need the extra level in *both* our translation
	 * tables.
	 *
	 * So use the same T0SZ value we use for the ID map.
	 */
	ldr_l	x5, idmap_t0sz
	bfi	x4, x5, TCR_T0SZ_OFFSET, TCR_TxSZ_WIDTH
#endif
	msr	tcr_el2, x4

	ldr	x4, =VTCR_EL2_FLAGS
	/*
	 * Read the PARange bits from ID_AA64MMFR0_EL1 and set the PS bits in
	 * VTCR_EL2.
	 */
	mrs	x5, ID_AA64MMFR0_EL1
	bfi	x4, x5, #16, #3
	msr	vtcr_el2, x4

	mrs	x4, mair_el1
	msr	mair_el2, x4
	isb

	/* Invalidate the stale TLBs from Bootloader */
	tlbi	alle2
	dsb	sy

	mrs	x4, sctlr_el2
	and	x4, x4, #SCTLR_ELx_EE	// preserve endianness of EL2
	ldr	x5, =SCTLR_ELx_FLAGS
	orr	x4, x4, x5
	msr	sctlr_el2, x4
	isb

	/* Skip the trampoline dance if we merged the boot and runtime PGDs */
	cmp	x0, x1
	b.eq	merged

	/* MMU is now enabled. Get ready for the trampoline dance */
	ldr	x4, =TRAMPOLINE_VA
	adr	x5, target
	bfi	x4, x5, #0, #PAGE_SHIFT
	br	x4

target: /* We're now in the trampoline code, switch page tables */
	msr	ttbr0_el2, x1
	isb

	/* Invalidate the old TLBs */
	tlbi	alle2
	dsb	sy

merged:
	/* Set the stack and new vectors */
	kern_hyp_va	x2
	mov	sp, x2
	kern_hyp_va	x3
	msr	vbar_el2, x3

	/* Hello, World! */
	eret
ENDPROC(__kvm_hyp_init)

	/*
	 * Reset kvm back to the hyp stub. This is the trampoline dance in
	 * reverse. If kvm used an extended idmap, __extended_idmap_trampoline
	 * calls this code directly in the idmap. In this case switching to the
	 * boot tables is a no-op.
	 *
	 * x0: HYP boot pgd
	 * x1: HYP phys_idmap_start
	 */
ENTRY(__kvm_hyp_reset)
	/* We're in trampoline code in VA, switch back to boot page tables */
	msr	ttbr0_el2, x0
	isb

	/* Ensure the PA branch doesn't find a stale tlb entry or stale code. */
	ic	iallu
	tlbi	alle2
	dsb	sy
	isb

	/* Branch into PA space */
	adr	x0, 1f
	bfi	x1, x0, #0, #PAGE_SHIFT
	br	x1

	/* We're now in idmap, disable MMU */
1:	mrs	x0, sctlr_el2
	ldr	x1, =SCTLR_ELx_FLAGS
	bic	x0, x0, x1		// Clear SCTL_M and etc
	msr	sctlr_el2, x0
	isb

	/* Invalidate the old TLBs */
	tlbi	alle2
	dsb	sy

	/* Install stub vectors */
	adr_l	x0, __hyp_stub_vectors
	msr	vbar_el2, x0

	eret
ENDPROC(__kvm_hyp_reset)

	.ltorg

	.popsection
