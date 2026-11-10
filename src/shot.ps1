# 截图助手：启动 exe → 可选取色/输入命令/打开设置 → PrintWindow 渲染 → 关闭
param(
    [string]$Out = 'C:\Users\35923\Desktop\截图\androidtool.png',
    [string[]]$Commands = @(),
    [string]$Bg = '',
    [string]$Font = '',
    [string]$Size = '',
    [switch]$Settings
)
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
if (-not ('ShotApi2' -as [type])) {
Add-Type @"
using System; using System.Text; using System.Collections.Generic; using System.Runtime.InteropServices;
public class ShotApi2 {
  public delegate bool EnumProc(IntPtr h, IntPtr p);
  [DllImport("user32.dll")] public static extern bool EnumChildWindows(IntPtr parent, EnumProc cb, IntPtr p);
  [DllImport("user32.dll")] public static extern bool EnumWindows(EnumProc cb, IntPtr p);
  [DllImport("user32.dll")] public static extern uint GetWindowThreadProcessId(IntPtr h, out uint pid);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetClassName(IntPtr h, StringBuilder sb, int n);
  [DllImport("user32.dll", CharSet=CharSet.Unicode)] public static extern int GetWindowText(IntPtr h, StringBuilder sb, int n);
  [DllImport("user32.dll")] public static extern bool PostMessage(IntPtr h, uint msg, IntPtr w, IntPtr l);
  [DllImport("user32.dll")] public static extern bool PrintWindow(IntPtr h, IntPtr hdc, uint flags);
  [DllImport("user32.dll")] public static extern bool GetWindowRect(IntPtr h, out RECT r);
  [DllImport("user32.dll")] public static extern bool SetWindowPos(IntPtr h, IntPtr after, int x, int y, int cx, int cy, uint flags);
  public struct RECT { public int Left, Top, Right, Bottom; }
  public static List<IntPtr> Children(IntPtr root){
    List<IntPtr> l = new List<IntPtr>();
    EnumChildWindows(root, delegate(IntPtr h, IntPtr p){ l.Add(h); return true; }, IntPtr.Zero);
    return l;
  }
  public static IntPtr FindEdit(List<IntPtr> all){
    foreach(IntPtr h in all){ StringBuilder c=new StringBuilder(128); GetClassName(h,c,128); if(c.ToString().IndexOf(".EDIT.")>0) return h; }
    return IntPtr.Zero;
  }
  public static IntPtr ByText(List<IntPtr> all, string needle){
    foreach(IntPtr h in all){
      StringBuilder t=new StringBuilder(128); GetWindowText(h,t,128);
      if(t.ToString().IndexOf(needle)>=0) return h;
    }
    return IntPtr.Zero;
  }
  public static IntPtr TopByPid(uint pid, string title){
    IntPtr hit = IntPtr.Zero;
    EnumWindows(delegate(IntPtr h, IntPtr p){
      uint wp; GetWindowThreadProcessId(h, out wp);
      if(wp==pid){
        StringBuilder t=new StringBuilder(256); GetWindowText(h,t,256);
        if(t.ToString()==title) { hit=h; return false; }
      }
      return true;
    }, IntPtr.Zero);
    return hit;
  }
}
"@
}
function Save-Window([IntPtr]$h, [string]$path){
    $r = New-Object ShotApi2+RECT
    [void][ShotApi2]::GetWindowRect($h, [ref]$r)
    $w = $r.Right - $r.Left; $ht = $r.Bottom - $r.Top
    $bmp = New-Object System.Drawing.Bitmap($w, $ht)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $hdc = $g.GetHdc(); [void][ShotApi2]::PrintWindow($h, $hdc, 2); $g.ReleaseHdc($hdc)
    $bmp.Save($path, [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
    "$path  ${w}x${ht}"
}

$root = Split-Path $PSScriptRoot -Parent
$exe = Join-Path $root 'Easy-platform-tools-GUI.exe'
$cfg = Join-Path $root 'config.ini'
if (Test-Path $cfg) {
    $kv = @{}
    foreach ($line in Get-Content $cfg) {
        if ($line -match '^\s*([a-z]+)\s*=\s*(.*)$') { $kv[$matches[1]] = $matches[2] }
    }
    if ($Bg)   { $kv['bg'] = $Bg }
    if ($Font) { $kv['font'] = $Font }
    if ($Size) { $kv['size'] = $Size }
    $text = "bg=$($kv['bg'])`r`nfont=$($kv['font'])`r`nsize=$($kv['size'])`r`n"
    [System.IO.File]::WriteAllText($cfg, $text)
}

$p = Start-Process -FilePath $exe -PassThru
Start-Sleep -Seconds 3
$h = $p.MainWindowHandle
[void][ShotApi2]::SetWindowPos($h, [IntPtr](-1), 380, 110, 1060, 720, 0x0040)
$kids = [ShotApi2]::Children($h)
$edit = [ShotApi2]::FindEdit($kids)
foreach ($cmd in $Commands) {
    foreach ($ch in $cmd.ToCharArray()) { [void][ShotApi2]::PostMessage($edit, 0x0102, [IntPtr][int]$ch, [IntPtr]1); Start-Sleep -Milliseconds 8 }
    Start-Sleep -Milliseconds 150
    [void][ShotApi2]::PostMessage($edit, 0x0100, [IntPtr]13, [IntPtr]0)
    [void][ShotApi2]::PostMessage($edit, 0x0101, [IntPtr]13, [IntPtr]0)
    Start-Sleep -Seconds 3
}
Start-Sleep -Milliseconds 300

if ($Settings) {
    $gear = [ShotApi2]::ByText([ShotApi2]::Children($h), '设置')
    [void][ShotApi2]::PostMessage($gear, 0x00F5, [IntPtr]::Zero, [IntPtr]::Zero)
    Start-Sleep -Seconds 2
    $dlg = [ShotApi2]::TopByPid([uint32]$p.Id, '设置')
    if ($dlg -ne [IntPtr]::Zero) { Save-Window $dlg $Out }
}
else {
    Save-Window $h $Out
}

Start-Sleep -Milliseconds 300
[void]$p.CloseMainWindow(); Start-Sleep -Milliseconds 800
Get-Process Easy-platform-tools-GUI -ErrorAction SilentlyContinue | Stop-Process -Force
