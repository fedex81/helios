! Tests trapa, interrupts, address errors, illegal instructions and rte
! This test relies on running inside the cpu simulator to trigger interrupts
! and communicate test success or failure.        

#include "sim_instr.h"

.section .vect
.align 4
.global _vector_table
_vector_table:
        .long start
        .long start+0x2fc
        .long start
        .long start+0x2fc
        .long _general_illegal_instr_isr
        .long _isr5
        .long _slot_illegal_instr_isr
        .long _isr7
        .long _isr8
        .long _isr9

        .long _isr10
        .long _isr11
        .long _isr12
        .long _isr13
        .long _isr14
        .long _isr15
        .long _isr
        .long _isr
        .long _isr
        .long _isr

        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr

        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr

        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr

        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr
        .long _isr

        .long _isr
        .long _isr
        .long _isr
        .long _isr

! instruct cpu simulator when to trigger interrupts
        .long SIM_INSTR_MAGIC  ! magic number recognized by simulator
        .long _sim_instr_end
        .long _done
        .long _fail_loop
        .long CMD_ENABLE_EVENT_TRIGGER
        .long CMD_ENABLE_TEST_RESULT
_sim_instr_end: .long 0

.section .text
.align 2
.global start
start:
        ! register usage:

        ! r8 saves r15 around interrupts to ensure stack is preserved
        
        ! r9 is a test number that is written to the test_result
	! address upon failure. The cpu simulator will print this number
	! repeatedly. This is helpful to find failing test.
        
        ! r10 is used to track which ISR has run. Each ISR will shift r10
        ! 4 bits left and then set the lowest 4 bits to a unique id for
        ! that ISR. So r10 holds the history of the last 8 ISRs run.

        ! r11 holds the value SR had during the previous ISR

        ! r12 is a mask for zeroing out the ibit bits in SR

        ! r13 holds the address of the fail loop

        ! r14 holds an address that writing to causes the cpu simulator
        ! to set the cpu's event req and event info signals, causing an
        ! interrupt.
        
        nop
        ! setup constant values in registers
        mov.l pfail_loop, r13
        mov.l evt_trigger, r14

        ! r12 is a mask for the ibits in SR = 0xFF0F 
        mov #0, r0
        or #0xF0, r0
        not r0, r12

        mov r15, r8  ! save stack

        ! check that ibit starts as 0xF after reset
        mov #1, r9  ! test number
        stc sr, r0
        shlr2 r0
        shlr2 r0
        and #0xF, r0
        cmp/eq #0xF, r0
        bt .+6
        jmp @r13
        nop

! TRAPA
        ! test trapa 8
        mov #2, r9  ! test number
        mov #0, r10
        trapa #8
        nop
        mov r10, r0
        cmp/eq #8, r0
        bt .+6
        jmp @r13
        nop

        ! check stack was preseved by trapa
        cmp/eq r8, r15
        bt .+6
        jmp @r13
        nop
        
        ! test trapa 13 after 8
        mov #3, r9 ! test number
        trapa #13
        nop
        mov #0x8D, r0
        and #0xFF, r0 ! remove sign extended ones
        cmp/eq r0, r10
        bt .+6
        jmp @r13
        nop

! Interrupts, both maskable and non-maskable
        ! test an NMI
        mov #4, r9 ! test number
        mov #0, r10
        mov #1, r0  ! NMI request
        shll8 r0
        shll2 r0
        shll2 r0
        or #7, r0
        mov.l r0, @r14  ! triggers interrupt after MEM stage
        mov #1, r10  ! this mov's EX stage should occur right before interrupt
        mov r10, r0
        cmp/eq #0x17, r0
        bt .+6
        jmp @r13
        nop

        ! check stack was preserved by interrupt
        cmp/eq r8, r15
        bt .+6
        jmp @r13
        nop

        ! test an NMI that is delayed by a delay slot
        mov #5, r9 ! test number
        mov #0, r10
        mov #1, r0  ! NMI request
        shll8 r0
        shll2 r0
        shll2 r0
        or #7, r0
        mov.l r0, @r14  ! triggers interrupt after MEM stage
        bra _after_mov
        mov #2, r10  ! event req will be set during ID, but interrupt
                     ! will be delayed because mov is a delay slot instr
        nop
        nop
_after_mov:
        mov r10, r0
        cmp/eq #0x27, r0
        bt .+6
        jmp @r13
        nop

        ! test IRQ - on reset interrupt mask in SR is 0xF, so this interrupt will be masked
        mov #6, r9 ! test number
        mov #0, r10
        mov #0xF, r0 ! ilevel
        shll8 r0
        or #12, r0 ! vector base address
        mov r0, r1
        mov.l r1, @r14  ! triggers interrupt after MEM stage
        mov r10, r0
        cmp/eq #0, r0
        bt .+6
        jmp @r13
        nop

        ! set interrupt mask 0xE and redo above interrupt which will no longer be masked
        stc sr, r0
        and r12, r0 ! zero out ibit
        or #0xE0, r0
        ldc r0, sr

        mov #7, r9 ! test number
        mov.l r1, @r14  ! triggers interrupt after MEM stage
        mov r10, r0
        cmp/eq #12, r0
        bt .+6
        jmp @r13
        nop

        ! check that above interrupt modified SR during isr. isr stores the SR in r11
        mov #8, r9 ! test number
        mov r11, r0
        and #0xF0, r0
        shlr2 r0
        shlr2 r0
        cmp/eq #0xF, r0
        bt .+6
        jmp @r13
        nop


        ! test IRQ with ilevel 4
        mov #9, r9 ! test number
        mov #0, r10
        mov #0x4, r0 ! ilevel
        shll8 r0
        or #12, r0 ! vector base address
        mov r0, r1
        mov.l r1, @r14  ! triggers interrupt after MEM stage, but is masked
        mov r10, r0
        cmp/eq #0, r0
        bt .+6
        jmp @r13
        nop

        ! modify interrupt mask to same as interrupt's ilevel. should still be masked
        stc sr, r0
        and r12, r0 ! zero out ibit
        or #0x40, r0
        ldc r0, sr

        mov #10, r9 ! test number
        mov.l r1, @r14  ! triggers interrupt after MEM stag, but is masked
        mov r10, r0
        cmp/eq #0, r0
        bt .+6
        jmp @r13
        nop

        ! modify interrupt mask to lower than interrupt's ilevel. interrupt will no longer be masked
        stc sr, r0
        and r12, r0 ! zero out ibit
        or #0x20, r0
        ldc r0, sr

        mov #11, r9 ! test number
        mov.l r1, @r14  ! triggers interrupt after MEM stage
        mov r10, r0
        cmp/eq #12, r0
        bt .+6
        jmp @r13
        nop

        ! check that above interrupt modified SR during isr. isr stores the SR in r11
        mov #12, r9 ! test number
        mov r11, r0
        and #0xF0, r0
        shlr2 r0
        shlr2 r0
        cmp/eq #0x4, r0
        bt .+6
        jmp @r13
        nop


! illegal instructions
        ! general illegal instruction
        mov #13, r9 ! test number
        mov #0, r10
        .word 0xFFFF ! illegal instruction
        nop
        mov r10, r0
        cmp/eq #0x4, r0
        bt .+6
        jmp @r13
        nop

        ! check stack was preserved by general illegal instruction
        cmp/eq r8, r15
        bt .+6
        jmp @r13
        nop

        ! slot illegal instruction
        mov #14, r9 ! test number
        mov #0, r10
        bra _after_branch
        bra _not_taken   ! illegal

_not_taken:
        shll r10
_after_branch:
        mov r10, r0
        cmp/eq #0x6, r0
        bt .+6
        jmp @r13
        nop

        ! check stack was preserved by slot illegal instruction
        cmp/eq r8, r15
        bt .+6
        jmp @r13
        nop

        ! illegal instruction in delay slot
        mov #15, r9 ! test number
        mov #0, r10
        bra _after_bad_instr
        .word 0xFFFF ! illegal instruction
_after_bad_instr:
        mov r10, r0
        cmp/eq #0x6, r0
        bt .+6
        jmp @r13
        nop


! Address Errors
        ! set 0xF interrupt mask
        stc sr, r0
        or #0xF0, r0
        ldc r0, sr

        ! DMA Address Error
        mov #16, r9 ! test number
        mov #0, r10
        mov #0x3, r0 ! DMAERR req
        shll2 r0
        shll2 r0
        or #1, r0 ! set bits that interrupt captures to ensure address error doesn't
        shll8 r0
        or #12, r0 ! vector base address
        mov r0, r1
        mov.l r1, @r14  ! triggers interrupt after MEM stage
        nop
        nop
        mov r10, r0
        cmp/eq #12, r0
        bt .+6
        jmp @r13
        nop

        ! CPU Address Error
        mov #17, r9 ! test number
        mov #0, r10
        mov #0x2, r0 ! CPUERR req
        shll2 r0
        shll2 r0
        or #1, r0 ! set bits that interrupt captures to ensure address error doesn't
        shll8 r0
        or #12, r0 ! vector base address
        mov r0, r1
        mov.l r1, @r14  ! triggers interrupt after MEM stage
        nop
        nop
        mov r10, r0
        cmp/eq #12, r0
        bt .+6
        jmp @r13
        nop

        ! ensure address error does not capture ibits from event info (like interrupts do)
        mov #18, r9 ! test number
        mov r11, r0
        and #0xF0, r0
        shlr2 r0
        shlr2 r0
        cmp/eq #0xF, r0
        bt .+6
        jmp @r13
        nop

        ! check stack was preserved by interrupt
        mov #19, r9 ! test number
        cmp/eq r8, r15
        bt .+6
        jmp @r13
        nop

        ! test an NMI
        mov #20, r9 ! test number
        mov #0, r10
        mov #1, r0  ! NMI request
        shll8 r0
        shll2 r0
        shll2 r0
        or #7, r0
        mov.l r0, @r14  ! triggers interrupt after MEM stage
        stc sr, r10 ! interrupt cannot occur between stc and following instruction
        mov #4, r10
        mov r10, r0
        cmp/eq #0x47, r0
        bt .+6
        jmp @r13
        nop

        ! TODO: check that interrupts do the following:
        ! - Some instructions, like LDC, disallow interrupts between them
        !   and the next instruction. Test this.

_done:
        mov.l test_result, r0
        mov #0, r9
        mov.l r9, @r0 ! success
        bra _done
        nop

_isr:
        rte
        nop

! interrupt handlers that shift r10 left by 4 and then set the lowest 4 bits
! to a number 4-15 corresponding to the index into the vector table

_general_illegal_instr_isr:
        shll2 r10
        shll2 r10
        add #4 r10
        stc sr, r11
        ! increment PC stored on stack to skip bad instruction
        mov.l @r15, r0
        add #2, r0
        mov.l r0, @r15
        rte
        nop
_isr5:
        shll2 r10
        shll2 r10
        add #5, r10
        stc sr, r11
        rte
        nop
_slot_illegal_instr_isr:
        shll2 r10
        shll2 r10
        add #6, r10
        stc sr, r11
        rte
        nop
_isr7:
        shll2 r10
        shll2 r10
        add #7, r10
        stc sr, r11
        rte
        nop
_isr8:
        shll2 r10
        shll2 r10
        add #8, r10
        stc sr, r11
        rte
        nop
_isr9:
        shll2 r10
        shll2 r10
        add #9, r10
        stc sr, r11
        rte
        nop
_isr10:
        shll2 r10
        shll2 r10
        add #10, r0
        stc sr, r11
        rte
        nop
_isr11:
        shll2 r10
        shll2 r10
        add #11, r10
        stc sr, r11
        rte
        nop
_isr12:
        shll2 r10
        shll2 r10
        add #12, r10
        stc sr, r11
        rte
        nop
_isr13:
        shll2 r10
        shll2 r10
        add #13, r10
        stc sr, r11
        rte
        nop
_isr14:
        shll2 r10
        shll2 r10
        add #14, r10
        stc sr, r11
        rte
        nop
_isr15:
        shll2 r10
        shll2 r10
        add #15, r10
        stc sr, r11
        rte
        nop

_fail_loop:
        mov.l test_result, r0
        mov.l r9, @r0  ! write fail number
        bra _fail_loop
        nop

.align 4
pfail_loop: .long _fail_loop
evt_trigger: .long EVENT_TRIGGER_ADDRESS
test_result: .long TEST_RESULT_ADDRESS