# 生成 app.ico（256×256 PNG 负载的 ICO，Win7+ 可用）
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing

$out = Join-Path $PSScriptRoot 'app.ico'
$size = 256
$bmp = New-Object System.Drawing.Bitmap($size, $size, [System.Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [System.Drawing.Graphics]::FromImage($bmp)
$g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$g.Clear([System.Drawing.Color]::Transparent)

# 圆角底板
$r = 54
$rect = New-Object System.Drawing.Rectangle(10, 10, 236, 236)
$d = 2 * $r
$path = New-Object System.Drawing.Drawing2D.GraphicsPath
$path.AddArc($rect.X, $rect.Y, $d, $d, 180, 90)
$path.AddArc($rect.Right - $d, $rect.Y, $d, $d, 270, 90)
$path.AddArc($rect.Right - $d, $rect.Bottom - $d, $d, $d, 0, 90)
$path.AddArc($rect.X, $rect.Bottom - $d, $d, $d, 90, 90)
$path.CloseFigure()

$c1 = [System.Drawing.Color]::FromArgb(255, 36, 43, 58)
$c2 = [System.Drawing.Color]::FromArgb(255, 16, 19, 26)
$brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush($rect, $c1, $c2, 90)
$g.FillPath($brush, $path)
$penEdge = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 58, 76, 110), 6)
$g.DrawPath($penEdge, $path)

# 提示符 >
$penArrow = New-Object System.Drawing.Pen([System.Drawing.Color]::FromArgb(255, 122, 178, 255), 24)
$penArrow.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
$penArrow.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
$penArrow.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
$pts = @(
    (New-Object System.Drawing.PointF(76, 80)),
    (New-Object System.Drawing.PointF(134, 128)),
    (New-Object System.Drawing.PointF(76, 176))
)
$g.DrawLines($penArrow, $pts)

# 光标 _
$brBar = New-Object System.Drawing.SolidBrush([System.Drawing.Color]::FromArgb(255, 126, 214, 160))
$g.FillRectangle($brBar, 148, 162, 52, 18)

$g.Dispose()

$ms = New-Object System.IO.MemoryStream
$bmp.Save($ms, [System.Drawing.Imaging.ImageFormat]::Png)
$png = $ms.ToArray()
$ms.Dispose()
$bmp.Dispose()

# ICO 封装
$fs = [System.IO.File]::Create($out)
$bw = New-Object System.IO.BinaryWriter($fs)
$bw.Write([UInt16]0)          # reserved
$bw.Write([UInt16]1)          # type = icon
$bw.Write([UInt16]1)          # count
$bw.Write([byte]0)            # width 256
$bw.Write([byte]0)            # height 256
$bw.Write([byte]0)            # palette
$bw.Write([byte]0)            # reserved
$bw.Write([UInt16]1)          # planes
$bw.Write([UInt16]32)         # bpp
$bw.Write([UInt32]$png.Length)
$bw.Write([UInt32]22)         # offset
$bw.Write($png)
$bw.Flush(); $bw.Close(); $fs.Close()

Write-Host ("icon -> " + $out + "  (" + (Get-Item $out).Length + " bytes)")
