! Tests trapa, interrupts, address errors, illegal instructions and rte
! This test relies on running inside the cpu simulator to trigger interrupts
! and communicate test success or failure.        

#include "sim_instr.h"

//#include <linux/linkage.h>

#define THREAD_SHIFT	13
#define THREAD_SIZE	(1 << THREAD_SHIFT)
#define STACK_WARN	(THREAD_SIZE >> 3)

#define ENTRY(name) \
  .globl name; \
  .balign 4; \
  name:

/* Offsets to the stack */
OFF_R0  =  0		/* Return value. New ABI also arg4 */
OFF_R1  =  4     	/* New ABI: arg5 */
OFF_R2  =  8     	/* New ABI: arg6 */
OFF_R3  =  12     	/* New ABI: syscall_nr */
OFF_R4  =  16     	/* New ABI: arg0 */
OFF_R5  =  20     	/* New ABI: arg1 */
OFF_R6  =  24     	/* New ABI: arg2 */
OFF_R7  =  28     	/* New ABI: arg3 */
OFF_SP	=  (15*4)
OFF_PC  =  (16*4)
OFF_SR	=  (16*4+2*4)
OFF_TRA	=  (16*4+6*4)

! entry-macros.S

	.macro	cli
	stc	sr, r0
	or	#0xf0, r0
	ldc	r0, sr
	.endm

	.macro	sti
	mov	#0xfffffff0, r11
	extu.b	r11, r11
	not	r11, r11
	stc	sr, r10
	and	r11, r10
	ldc	r10, sr
	.endm

	.macro	get_current_thread_info, ti, tmp
	mov	#((THREAD_SIZE - 1) >> 10) ^ 0xff, \tmp
	shll8	\tmp
	shll2	\tmp
	mov	r15, \ti
	and	\tmp, \ti
	.endm
        
.section .vect
ENTRY(_vector_table)
        .long start
        .long 0x100000
        .long start
        .long 0x100000

        .rept	60
	.long _isr
	.endr

! insert some magic data values here that the simulator will read to
! enable features
        .long SIM_INSTR_MAGIC  ! magic number recognized by simulator
        .long _sim_instr_end
        .long _done      ! detect success instr address
        .long _fail_loop ! detect failure instr address
        .long CMD_ENABLE_TEST_RESULT  ! enables an addresss to write result to
        .long CMD_ENABLE_DUMP_STACK ! enables an address to dump the stack
_sim_instr_end: .long 0

        
.section .text
ENTRY(start)       
        ! replace VBR with linux style one
        mov.l pvbr_base, r0
        ldc r0, vbr

        ! zero all registers
        mov #0, r0
        mov #1, r1
        mov #2, r2
        mov #3, r3
        mov #4, r4
        mov #5, r5
        mov #6, r6
        mov #7, r7
        mov #8, r8
        mov #9, r9
        mov #10, r10
        mov #11, r11
        mov #12, r12
        mov #13, r13
        mov #14, r14

        ldc r0, gbr
        lds r0, mach
        lds r0, macl
        lds r0, pr

        ! do a pseudo execv syscall
        mov #11, r3
        trapa #23
        nop

        ! dump stack
        mov.l pdump_stack, r2
        mov.l r15, @r2

        ! check that stack is empty, as before trapa
        mov #4, r0
        mov.l @r0, r0
        mov.l pfail_loop, r13
        cmp/eq r15, r0
        bt .+6
        jmp @r13
        nop
        
_done:   
        mov.l test_result, r0
        mov #0, r9
        mov.l r9, @r0 ! success
        bra _done
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
pvbr_base: .long vbr_base


! default ISR should never be entered
_isr:
        ! set a fail value and jump to fail loop
        mov #5, r9
        bra _fail_loop
        nop

        .align 4

! entry.S

ENTRY(exception_handler)
	! stack
	! r0 <- point sp
	! r1
	! pc
	! sr
	! r0 = temporary
	! r1 = vector (pseudo EXPEVT / INTEVT / TRA)
	mov.l	r2,@-sp
	mov.l	r3,@-sp
	cli

        ! dump stack
        mov.l pdump_stack, r2
        mov.l r15, @r2
        
        ! test if in kernel mode
        mov.l	$cpu_mode,r2
	mov.l	@r2,r0
	mov.l	@(5*4,r15),r3	! previous SR
	or	r0,r3		! set MD
	tst	r0,r0
	bf/s	1f		! previous mode check
	 mov.l	r3,@(5*4,r15)	! update SR

	! switch to kernel mode
	mov.l	__md_bit,r0
	mov.l	r0,@r2		! enter kernel mode
	mov.l	$current_thread_info,r2
	mov.l	@r2,r2
	mov	#(THREAD_SIZE >> 8),r0
	shll8	r0
	add	r2,r0
	mov	r15,r2		! r2 = user stack top
	mov	r0,r15		! switch kernel stack
	mov.l	r1,@-r15	! TRA
	sts.l	macl, @-r15
	sts.l	mach, @-r15
	stc.l	gbr, @-r15
	mov.l	@(5*4,r2),r0
	mov.l	r0,@-r15	! original SR
	sts.l	pr,@-r15
	mov.l	@(4*4,r2),r0
	mov.l	r0,@-r15	! original PC
	mov	r2,r3
	add	#(4+2)*4,r3	! rewind r0 - r3 + exception frame
	mov.l	r3,@-r15	! original SP
	mov.l	r14,@-r15
	mov.l	r13,@-r15
	mov.l	r12,@-r15
	mov.l	r11,@-r15
	mov.l	r10,@-r15
	mov.l	r9,@-r15
	mov.l	r8,@-r15
	mov.l	r7,@-r15
	mov.l	r6,@-r15
	mov.l	r5,@-r15
	mov.l	r4,@-r15
	mov	r1,r9		! save TRA
	mov	r2,r8		! copy user -> kernel stack
	mov.l	@(0,r8),r3
	mov.l	r3,@-r15
	mov.l	@(4,r8),r2
	mov.l	r2,@-r15
	mov.l	@(12,r8),r1
	mov.l	r1,@-r15
	mov.l	@(8,r8),r0
	bra	2f
	 mov.l	r0,@-r15
1:
	! in kernel exception
        ! move r15 down 14 ints, and then copy the R3-R0 values further down the stack
	mov	#(22-4-4-1)*4+4,r0
	mov	r15,r2
	sub	r0,r15
	mov.l	@r2+,r0		! old R3
	mov.l	r0,@-r15	
	mov.l	@r2+,r0		! old R2
	mov.l	r0,@-r15	
	mov.l	@(4,r2),r0	! old R1
	mov.l	r0,@-r15	
	mov.l	@r2,r0		! old R0
	mov.l	r0,@-r15

        ! dump stack
        mov.l pdump_stack, r0
        mov.l r15, @r0

	add	#8,r2
	mov.l	@r2+,r3		! old PC
	mov.l	@r2+,r0		! old SR
	add	#-4,r2		! exception frame stub (sr)
	mov.l	r1,@-r2		! TRA
	sts.l	macl, @-r2
	sts.l	mach, @-r2
	stc.l	gbr, @-r2
	mov.l	r0,@-r2		! save old SR
	sts.l	pr,@-r2
	mov.l	r3,@-r2		! save old PC

        ! dump stack
        mov.l pdump_stack, r0
        mov.l r15, @r0

	mov	r2,r0
	add	#8*4,r0
	mov.l	r0,@-r2		! save old SP
	mov.l	r14,@-r2
	mov.l	r13,@-r2
	mov.l	r12,@-r2
	mov.l	r11,@-r2
	mov.l	r10,@-r2
	mov.l	r9,@-r2
	mov.l	r8,@-r2
	mov.l	r7,@-r2
	mov.l	r6,@-r2
	mov.l	r5,@-r2
	mov.l	r4,@-r2
	mov	r1,r9
	mov.l	@(OFF_R0,r15),r0
	mov.l	@(OFF_R1,r15),r1

        ! dump stack
        mov.l pdump_stack, r3
        mov.l r2, @r3

	mov.l	@(OFF_R2,r15),r2
        mov.l	@(OFF_R3,r15),r3
2:

! Comment out all of the interrupt vs trap detection. Only testing the
! system call trap 0x11 here.
        
!        mov	#64,r8
!	cmp/hs	r8,r9
!	bt	interrupt_entry	! vec >= 64 is interrupt
!	mov	#32,r8
!	cmp/hs	r8,r9
!	bt	trap_entry	! 64 > vec >= 32 is trap
!	mov	#16,r8
!	cmp/hs	r8,r9
!	bt	interrupt_entry	! 32 > vec >= 16  is interrupt

!	mov.l	4f,r8
!	mov	r9,r4
!	shll2	r9
!	add	r9,r8
!	mov.l	@r8,r8		! exception handler address
!	tst	r8,r8
!	bf	3f
!	mov.l	8f,r8		! unhandled exception
!3:
!	mov.l	5f,r10
!	jmp	@r8
!	 lds	r10,pr

!interrupt_entry:
!	mov	r9,r4
!	mov	r15,r5
!	mov.l	6f,r9		! ret_from_irq
!	mov.l	7f,r8		! do_IRQ
!	jmp	@r8				! do_IRQ
!	 lds	r9,pr

!	.align	2
!4:	.long	exception_handling_table
!5:	.long	ret_from_exception
!6:	.long	ret_from_irq
!7:	.long	do_IRQ
!8:	.long	exception_error

trap_entry:
	mov	#0x30,r8
	cmp/ge	r8,r9		! vector 0x20-0x2f is systemcall
	bt	1f
	add	#-0x10,r9	! convert SH2 to SH3/4 ABI
1:	
	shll2	r9			! TRA

! instead of handling the system_call, immediately restore_all        
!	bra	system_call	! jump common systemcall entry
        bra     restore_all
	 mov	r9,r8
        
restore_all:
        mov.l pdump_stack, r0
        mov.l r15, @r0

        ! set imask to 1111
        stc sr,r0
        or #-16,r0
        ldc r0,sr

        ! load system registers, which are after the general registers
	! on the stack
       	mov	r15,r0
       	add	#68,r0
       	lds.l	@r0+,pr
       	add	#4,r0
       	ldc.l	@r0+,gbr
       	lds.l	@r0+,mach
       	lds.l	@r0+,macl

       	mov	r15,r0
        ! store SR/R15 into r1
       	mov.l	$cpu_mode,r2
       	mov	#OFF_SR,r3
       	mov.l	@(r0,r3),r1

        ! store SR & 0x40000000 into $cpu_mode
        mov.l	__md_bit,r3 ! 40000000
       	and	r1,r3   ! copy MD bit
       	mov.l	r3,@r2  

        ! zero out top 2 bits of r1 which is loaded SP/R15
       	shll2	r1       ! clear MD bit
       	shlr2	r1

        ! load and modify saved stack pointer  -- WHY?
        mov.l	@(OFF_SP,r0),r2
       	add	#-8,r2
       	mov.l	r2,@(OFF_SP,r0)    ! point exception frame top

       	mov.l	r1,@(4,r2)         ! set sr
       	mov	#OFF_PC,r3
       	mov.l	@(r0,r3),r1
       	mov.l	r1,@r2             ! set pc

        get_current_thread_info r0, r1

        ! save to current thread info area
       	mov.l	$current_thread_info,r1
       	mov.l	r0,@r1

        mov.l pdump_stack, r0
        mov.l r15, @r0
        nop

        ! load general registers
       	mov.l	@r15+,r0
       	mov.l	@r15+,r1
       	mov.l	@r15+,r2
       	mov.l	@r15+,r3
       	mov.l	@r15+,r4
       	mov.l	@r15+,r5
       	mov.l	@r15+,r6
       	mov.l	@r15+,r7
       	mov.l	@r15+,r8
       	mov.l	@r15+,r9
       	mov.l	@r15+,r10
       	mov.l	@r15+,r11
       	mov.l	@r15+,r12
       	mov.l	@r15+,r13
       	mov.l	@r15+,r14
       	mov.l	@r15,r15
       	rte
       	nop
       	nop


.align 4
__md_bit:
	.long	0x40000000
$current_thread_info:
	.long	__current_thread_info
$cpu_mode:	
	.long	__cpu_mode

__cpu_mode:
	.long	0x40000000
__current_thread_info:
	.long	0
pdump_stack: .long DUMP_STACK_ADDRESS


        .align	2
       

! exception_entry and vbr_base are from arch/sh/kernel/cpu/sh2/ex.S

!
! convert Exception Vector to Exception Number
!
exception_entry:	
no	=	0
	.rept	256
        nop
	mov.l	r1,@-sp
	bra	exception_trampoline
	mov	#no,r1
no	=	no + 1
	.endr
exception_trampoline:
	mov.l	r0,@-sp
	mov.l	$exception_handler,r0
	extu.b	r1,r1
	jmp	@r0
	  extu.w	r1,r1

	.align	2
$exception_entry:
	.long	exception_entry
$exception_handler:
	.long	exception_handler

        .align 2
!
! Exception Vector Base
!
ENTRY(vbr_base)
vector	=	0
	.rept	256
	.long	exception_entry + vector * 6
vector	=	vector + 1
	.endr
