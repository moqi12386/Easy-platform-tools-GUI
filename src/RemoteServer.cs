// 手机遥控用的极简 HTTP 服务（TcpListener 手写，无需管理员权限 / 无需 URL ACL）

using System;
using System.Collections.Generic;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Text;
using System.Threading;

namespace EasyPlatformTools
{
    internal static class RemoteServer
    {
        public static bool Running { get { return listener != null; } }
        public static int Port = 8765;
        public static string Token = "";
        public static string LastError = "";

        private static TcpListener listener;
        private static Thread acceptThread;
        private static volatile bool stop;

        public static string LocalIp()
        {
            try
            {
                foreach (System.Net.NetworkInformation.NetworkInterface ni in System.Net.NetworkInformation.NetworkInterface.GetAllNetworkInterfaces())
                {
                    if (ni.OperationalStatus != System.Net.NetworkInformation.OperationalStatus.Up) { continue; }
                    if (ni.NetworkInterfaceType == System.Net.NetworkInformation.NetworkInterfaceType.Loopback) { continue; }
                    foreach (System.Net.NetworkInformation.UnicastIPAddressInformation ip in ni.GetIPProperties().UnicastAddresses)
                    {
                        if (ip.Address.AddressFamily == AddressFamily.InterNetwork) { return ip.Address.ToString(); }
                    }
                }
            }
            catch { }
            return "127.0.0.1";
        }

        public static bool Start(int port, string token)
        {
            Stop();
            try
            {
                Port = port;
                Token = token;
                listener = new TcpListener(IPAddress.Any, port);
                listener.Start();
                stop = false;
                acceptThread = new Thread(new ThreadStart(AcceptLoop));
                acceptThread.IsBackground = true;
                acceptThread.Start();
                LastError = "";
                return true;
            }
            catch (Exception ex)
            {
                LastError = ex.Message;
                listener = null;
                return false;
            }
        }

        public static void Stop()
        {
            stop = true;
            try { if (listener != null) { listener.Stop(); } } catch { }
            listener = null;
        }

        private static void AcceptLoop()
        {
            while (!stop)
            {
                TcpClient client = null;
                try { client = listener.AcceptTcpClient(); }
                catch { break; }
                TcpClient c = client;
                Thread t = new Thread(new ParameterizedThreadStart(Handle));
                t.IsBackground = true;
                t.Start(c);
            }
        }

        private static void Handle(object state)
        {
            TcpClient client = (TcpClient)state;
            try
            {
                using (client)
                using (NetworkStream ns = client.GetStream())
                {
                    ns.ReadTimeout = 30000;
                    byte[] buf = new byte[65536];
                    MemoryStream head = new MemoryStream();
                    int headerEnd = -1;
                    while (headerEnd < 0)
                    {
                        int n = ns.Read(buf, 0, buf.Length);
                        if (n <= 0) { return; }
                        head.Write(buf, 0, n);
                        byte[] cur = head.ToArray();
                        headerEnd = IndexOf(cur, Encoding.ASCII.GetBytes("\r\n\r\n"));
                        if (cur.Length > 65536) { return; }
                    }

                    byte[] all = head.ToArray();
                    string header = Encoding.ASCII.GetString(all, 0, headerEnd);
                    string[] lines = header.Split(new string[] { "\r\n" }, StringSplitOptions.None);
                    string[] first = lines[0].Split(' ');
                    if (first.Length < 2) { Write(ns, 400, "bad request"); return; }

                    string method = first[0];
                    string path = first[1];
                    Dictionary<string, string> headers = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
                    foreach (string l in lines)
                    {
                        int c = l.IndexOf(':');
                        if (c > 0) { headers[l.Substring(0, c).Trim()] = l.Substring(c + 1).Trim(); }
                    }

                    int contentLength = 0;
                    if (headers.ContainsKey("Content-Length"))
                    {
                        int.TryParse(headers["Content-Length"], out contentLength);
                    }
                    byte[] body = new byte[contentLength];
                    int got = all.Length - headerEnd - 4;
                    if (got > 0) { Array.Copy(all, headerEnd + 4, body, 0, Math.Min(got, contentLength)); }
                    while (got < contentLength)
                    {
                        int n = ns.Read(body, Math.Max(0, got), contentLength - Math.Max(0, got));
                        if (n <= 0) { break; }
                        got += n;
                    }

                    string token = headers.ContainsKey("X-Token") ? headers["X-Token"] : "";
                    if (Token.Length > 0 && token != Token)
                    {
                        Write(ns, 401, "token 不正确");
                        return;
                    }

                    Route(ns, method, path, body);
                }
            }
            catch { }
        }

        private static void Route(NetworkStream ns, string method, string path, byte[] body)
        {
            string query = "";
            int q = path.IndexOf('?');
            if (q >= 0) { query = path.Substring(q + 1); path = path.Substring(0, q); }
            Dictionary<string, string> args = ParseQuery(query);

            if (path == "/api/status")
            {
                string adb = RunCapture(Program.AdbPathPublic, "devices");
                string fb = RunCapture(Program.FastbootPathPublic, "devices");
                string dev = FirstDevice(adb, "device");
                string fbd = FirstDevice(fb, "fastboot");
                string json = "{\"android\":\"" + Escape(dev) + "\",\"fastboot\":\"" + Escape(fbd) + "\",\"version\":\"" + Escape(Program.VersionPublic) + "\"}";
                Write(ns, 200, json);
                return;
            }

            if (path == "/api/run" && method == "POST")
            {
                string cmd = args.ContainsKey("c") ? args["c"] : Encoding.UTF8.GetString(body);
                string outp;
                bool ok = Program.RunConsoleCommand(cmd, out outp);
                Write(ns, 200, (ok ? "" : "[失败] ") + outp);
                return;
            }

            if (path == "/api/partitions")
            {
                string outp;
                Program.RunConsoleCommand("fastboot getvar all", out outp);
                List<string[]> parts = Program.ParseGetvarPublic(outp);
                StringBuilder sb = new StringBuilder();
                sb.Append("[");
                for (int i = 0; i < parts.Count; i++)
                {
                    if (i > 0) { sb.Append(","); }
                    sb.Append("{\"name\":\"" + Escape(parts[i][0]) + "\",\"size\":\"" + Escape(parts[i][1]) + "\",\"type\":\"" + Escape(parts[i][2]) + "\"}");
                }
                sb.Append("]");
                Write(ns, 200, parts.Count == 0 ? "没有读到分区（设备是否在 fastboot？）" : sb.ToString());
                return;
            }

            if (path == "/api/flash" && method == "POST")
            {
                if (body == null || body.Length == 0) { Write(ns, 400, "没有收到镜像数据"); return; }
                string part = args.ContainsKey("partition") ? args["partition"] : "boot";
                string tmp = Path.Combine(Path.GetTempPath(), "easypt-remote-" + DateTime.Now.Ticks + ".img");
                try
                {
                    File.WriteAllBytes(tmp, body);
                    string outp;
                    bool ok = Program.RunConsoleCommand("fastboot flash " + part + " \"" + tmp + "\"", out outp);
                    Write(ns, 200, (ok ? "OK " : "FAIL ") + part + "\n" + outp);
                }
                catch (Exception ex) { Write(ns, 500, ex.Message); }
                finally { try { File.Delete(tmp); } catch { } }
                return;
            }

            if (path == "/api/reboot" && method == "POST")
            {
                string target = args.ContainsKey("target") ? args["target"] : "";
                string outp;
                string cmd = target.Length == 0 ? "fastboot reboot" : ("fastboot reboot-" + target);
                bool ok = Program.RunConsoleCommand(cmd, out outp);
                Write(ns, 200, (ok ? "OK" : "FAIL") + "\n" + outp);
                return;
            }

            Write(ns, 404, "unknown endpoint");
        }

        private static Dictionary<string, string> ParseQuery(string q)
        {
            Dictionary<string, string> d = new Dictionary<string, string>(StringComparer.OrdinalIgnoreCase);
            if (string.IsNullOrEmpty(q)) { return d; }
            foreach (string pair in q.Split('&'))
            {
                int e = pair.IndexOf('=');
                if (e <= 0) { continue; }
                d[Uri.UnescapeDataString(pair.Substring(0, e))] = Uri.UnescapeDataString(pair.Substring(e + 1));
            }
            return d;
        }

        private static string RunCapture(string exe, string args)
        {
            try
            {
                System.Diagnostics.ProcessStartInfo psi = new System.Diagnostics.ProcessStartInfo(exe);
                psi.Arguments = args;
                psi.UseShellExecute = false;
                psi.RedirectStandardOutput = true;
                psi.RedirectStandardError = true;
                psi.CreateNoWindow = true;
                using (System.Diagnostics.Process p = System.Diagnostics.Process.Start(psi))
                {
                    string o = p.StandardOutput.ReadToEnd();
                    string e = p.StandardError.ReadToEnd();
                    p.WaitForExit(5000);
                    return o + e;
                }
            }
            catch { return ""; }
        }

        private static string FirstDevice(string text, string state)
        {
            if (string.IsNullOrEmpty(text)) { return ""; }
            foreach (string raw in text.Split('\n'))
            {
                string line = raw.TrimEnd('\r');
                if (line.EndsWith("\t" + state, StringComparison.Ordinal))
                {
                    int tab = line.IndexOf('\t');
                    return tab > 0 ? line.Substring(0, tab) : line;
                }
            }
            return "";
        }

        private static int IndexOf(byte[] haystack, byte[] needle)
        {
            int limit = haystack.Length - needle.Length;
            for (int i = 0; i <= limit; i++)
            {
                bool ok = true;
                for (int j = 0; j < needle.Length; j++)
                {
                    if (haystack[i + j] != needle[j]) { ok = false; break; }
                }
                if (ok) { return i; }
            }
            return -1;
        }

        private static string Escape(string s)
        {
            if (s == null) { return ""; }
            return s.Replace("\\", "\\\\").Replace("\"", "\\\"").Replace("\n", " ");
        }

        private static void Write(NetworkStream ns, int code, string text)
        {
            byte[] body = Encoding.UTF8.GetBytes(text ?? "");
            string head = "HTTP/1.1 " + code + " " + (code == 200 ? "OK" : "ERR") + "\r\n"
                + "Content-Type: text/plain; charset=utf-8\r\n"
                + "Content-Length: " + body.Length + "\r\n"
                + "Connection: close\r\n\r\n";
            byte[] hb = Encoding.ASCII.GetBytes(head);
            ns.Write(hb, 0, hb.Length);
            ns.Write(body, 0, body.Length);
            ns.Flush();
        }
    }
}
