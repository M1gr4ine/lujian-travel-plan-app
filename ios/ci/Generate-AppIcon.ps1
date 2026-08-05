param(
    [string]$OutputPath = "$PSScriptRoot/../Lujian/Resources/Assets.xcassets/AppIcon.appiconset/AppIcon-1024.png"
)

Add-Type -AssemblyName System.Drawing

$bitmap = [System.Drawing.Bitmap]::new(1024, 1024, [System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
$graphics = [System.Drawing.Graphics]::FromImage($bitmap)
$graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
$graphics.Clear([System.Drawing.ColorTranslator]::FromHtml('#FAF6EF'))

function New-Brush([string]$color) {
    [System.Drawing.SolidBrush]::new([System.Drawing.ColorTranslator]::FromHtml($color))
}

function New-Pen([string]$color, [float]$width) {
    $pen = [System.Drawing.Pen]::new([System.Drawing.ColorTranslator]::FromHtml($color), $width)
    $pen.LineJoin = [System.Drawing.Drawing2D.LineJoin]::Round
    $pen.StartCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen.EndCap = [System.Drawing.Drawing2D.LineCap]::Round
    $pen
}

$ink = '#2A2520'
$paper = '#FFFDF8'
$gold = '#F2B43A'
$coral = '#B85F52'

$shadowBrush = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(34, 42, 37, 32))
$graphics.FillEllipse($shadowBrush, 348, 298, 340, 340)

$note = [System.Drawing.Drawing2D.GraphicsPath]::new()
$note.AddBezier(238, 176, 238, 142, 266, 118, 302, 118)
$note.AddLine(302, 118, 662, 118)
$note.AddLine(662, 118, 784, 240)
$note.AddLine(784, 240, 784, 758)
$note.AddBezier(784, 758, 784, 802, 750, 834, 706, 834)
$note.AddLine(706, 834, 302, 834)
$note.AddBezier(302, 834, 260, 834, 230, 804, 230, 762)
$note.CloseFigure()
$paperBrush = New-Brush $paper
$inkPen = New-Pen $ink 34
$graphics.FillPath($paperBrush, $note)
$graphics.DrawPath($inkPen, $note)

$fold = [System.Drawing.PointF[]]@(
    [System.Drawing.PointF]::new(602, 118),
    [System.Drawing.PointF]::new(784, 300),
    [System.Drawing.PointF]::new(654, 300),
    [System.Drawing.PointF]::new(602, 248)
)
$goldBrush = New-Brush $gold
$foldPen = New-Pen $ink 24
$graphics.FillPolygon($goldBrush, $fold)
$graphics.DrawPolygon($foldPen, $fold)

$routePen = New-Pen $gold 38
$graphics.DrawBezier($routePen, 314, 704, 390, 608, 472, 638, 548, 548)
$graphics.DrawBezier($routePen, 548, 548, 604, 482, 648, 514, 706, 442)

$pinLine = New-Pen $ink 22
$pinLine.StartCap = [System.Drawing.Drawing2D.LineCap]::Flat
$pinLine.EndCap = [System.Drawing.Drawing2D.LineCap]::Flat
$graphics.DrawLine($pinLine, 512, 414, 512, 744)

$pinShadow = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(38, 42, 37, 32))
$graphics.FillEllipse($pinShadow, 382, 268, 276, 276)
$coralBrush = New-Brush $coral
$pinPen = New-Pen $ink 28
$graphics.FillEllipse($coralBrush, 390, 254, 260, 260)
$graphics.DrawEllipse($pinPen, 390, 254, 260, 260)

$highlight = [System.Drawing.SolidBrush]::new([System.Drawing.Color]::FromArgb(190, 250, 246, 239))
$graphics.FillEllipse($highlight, 444, 310, 58, 58)

$target = [IO.Path]::GetFullPath($OutputPath)
[IO.Directory]::CreateDirectory([IO.Path]::GetDirectoryName($target)) | Out-Null
$bitmap.Save($target, [System.Drawing.Imaging.ImageFormat]::Png)

$highlight.Dispose()
$pinPen.Dispose()
$coralBrush.Dispose()
$pinShadow.Dispose()
$pinLine.Dispose()
$routePen.Dispose()
$foldPen.Dispose()
$goldBrush.Dispose()
$inkPen.Dispose()
$paperBrush.Dispose()
$note.Dispose()
$shadowBrush.Dispose()
$graphics.Dispose()
$bitmap.Dispose()
