# Generates store art from the existing launcher vector. No downloads or AI imagery.
param([string]$RepositoryRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Drawing
$assets = Join-Path $RepositoryRoot 'docs/google-play/assets'
New-Item -ItemType Directory -Path $assets -Force | Out-Null
$encoding = [Text.UTF8Encoding]::new($false)

function Color([string]$Hex) { [Drawing.ColorTranslator]::FromHtml($Hex) }
function Brush([string]$Hex) { [Drawing.SolidBrush]::new((Color $Hex)) }
function RoundedRectangle([single]$X, [single]$Y, [single]$Width, [single]$Height, [single]$Radius) {
    $path = [Drawing.Drawing2D.GraphicsPath]::new()
    $d = 2 * $Radius
    $path.AddArc($X, $Y, $d, $d, 180, 90)
    $path.AddArc($X + $Width - $d, $Y, $d, $d, 270, 90)
    $path.AddArc($X + $Width - $d, $Y + $Height - $d, $d, $d, 0, 90)
    $path.AddArc($X, $Y + $Height - $d, $d, $d, 90, 90)
    $path.CloseFigure()
    return $path
}
function ConfigureGraphics($Graphics) {
    $Graphics.SmoothingMode = [Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $Graphics.PixelOffsetMode = [Drawing.Drawing2D.PixelOffsetMode]::HighQuality
    $Graphics.TextRenderingHint = [Drawing.Text.TextRenderingHint]::AntiAliasGridFit
}
function Text($Graphics, [string]$Text, [single]$X, [single]$Y, [single]$Size, [string]$Hex, [bool]$Bold = $false) {
    $style = if ($Bold) { [Drawing.FontStyle]::Bold } else { [Drawing.FontStyle]::Regular }
    $font = [Drawing.Font]::new('Segoe UI', $Size, $style, [Drawing.GraphicsUnit]::Pixel)
    $brush = Brush $Hex
    $Graphics.DrawString($Text, $font, $brush, $X, $Y)
    $font.Dispose()
    $brush.Dispose()
}

# Same geometry as app/src/main/res/drawable/ic_launcher_foreground.xml.
$iconSvg = @"
<svg xmlns="http://www.w3.org/2000/svg" width="512" height="512" viewBox="0 0 108 108" role="img" aria-label="Dosette capsule icon">
<rect width="108" height="108" fill="#00696B"/>
<g transform="rotate(45 54 54)">
<path fill="#FFFFFF" d="M40,44 L68,44 A10,10 0 0 1 68,64 L40,64 A10,10 0 0 1 40,44 Z"/>
<path fill="none" stroke="#00696B" stroke-width="4" d="M54,44 L54,64"/>
</g>
</svg>
"@
[IO.File]::WriteAllText((Join-Path $assets 'icon-512.svg'), $iconSvg.Replace("`r`n","`n") + "`n", $encoding)
$bitmap = [Drawing.Bitmap]::new(512, 512, [Drawing.Imaging.PixelFormat]::Format32bppArgb)
$g = [Drawing.Graphics]::FromImage($bitmap)
ConfigureGraphics $g
$g.Clear((Color '#00696B'))
$g.ScaleTransform((512.0/108), (512.0/108))
$g.TranslateTransform(54,54)
$g.RotateTransform(45)
$g.TranslateTransform(-54,-54)
$pill = RoundedRectangle 30 44 48 20 10
$white = Brush '#FFFFFF'
$g.FillPath($white, $pill)
$pen = [Drawing.Pen]::new((Color '#00696B'), 4)
$g.DrawLine($pen, 54,44,54,64)
$bitmap.Save((Join-Path $assets 'icon-512.png'), [Drawing.Imaging.ImageFormat]::Png)
$pen.Dispose(); $white.Dispose(); $pill.Dispose(); $g.Dispose(); $bitmap.Dispose()

foreach ($locale in @('en', 'ru')) {
    $line1 = if ($locale -eq 'en') { 'Your medication' } else { 'Расписание' }
    $line2 = if ($locale -eq 'en') { 'schedule' } else { 'лекарств' }
    $alt = if ($locale -eq 'en') { 'Dosette medication schedule for Android and Wear OS' } else { 'Dosette: расписание лекарств для Android и Wear OS' }
    $rows = @(
        @{ X=602; Y=98; Time='08:00'; Fill='#00696B'; Ink='#FFFFFF'; Detail='#82D3D1'; Done=$true },
        @{ X=630; Y=206; Time='13:00'; Fill='#F0FAF7'; Ink='#003738'; Detail='#B6DCD8'; Done=$false },
        @{ X=602; Y=314; Time='20:00'; Fill='#BFE3DE'; Ink='#003738'; Detail='#90C5BF'; Done=$false }
    )
    $svgRows = foreach ($row in $rows) {
        $cx = $row.X + 250
        $cy = $row.Y + 44
        $mark = if ($row.Done) {
            "<path d='M$($cx-11) $cy l8 8 l15 -18' fill='none' stroke='#FFFFFF' stroke-width='5' stroke-linecap='round' stroke-linejoin='round'/>"
        } else {
            "<circle cx='$cx' cy='$cy' r='13' fill='none' stroke='$($row.Detail)' stroke-width='4'/>"
        }
        "<rect x='$($row.X)' y='$($row.Y)' width='296' height='88' rx='32' fill='$($row.Fill)'/><text x='$($row.X+24)' y='$($row.Y+57)' font-size='39' font-weight='600' fill='$($row.Ink)'>$($row.Time)</text>$mark"
    }
    $svg = @"
<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="500" viewBox="0 0 1024 500" role="img" aria-label="$alt">
<rect width="1024" height="500" fill="#D9F1EA"/>
<circle cx="1070" cy="-35" r="300" fill="#C6E8E0"/>
<circle cx="-90" cy="570" r="250" fill="#BFE3DE"/>
<g font-family="Segoe UI, sans-serif">
<text x="90" y="185" font-size="72" font-weight="700" fill="#003738">Dosette</text>
<text x="94" y="250" font-size="36" fill="#135B59">$line1</text>
<text x="94" y="296" font-size="36" fill="#135B59">$line2</text>
<text x="96" y="364" font-size="23" fill="#216D68">Android + Wear OS</text>
$($svgRows -join "`n")
</g>
</svg>
"@
    [IO.File]::WriteAllText((Join-Path $assets "feature-$locale-1024x500.svg"), $svg.Replace("`r`n","`n") + "`n", $encoding)
    $bitmap = [Drawing.Bitmap]::new(1024,500,[Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $g = [Drawing.Graphics]::FromImage($bitmap)
    ConfigureGraphics $g
    $g.Clear((Color '#D9F1EA'))
    $bg1 = Brush '#C6E8E0'; $bg2 = Brush '#BFE3DE'
    $g.FillEllipse($bg1, 770,-335,600,600)
    $g.FillEllipse($bg2, -340,320,500,500)
    Text $g 'Dosette' 84 105 72 '#003738' $true
    Text $g $line1 90 210 36 '#135B59'
    Text $g $line2 90 256 36 '#135B59'
    Text $g 'Android + Wear OS' 92 335 23 '#216D68'
    foreach ($row in $rows) {
        $shape = RoundedRectangle $row.X $row.Y 296 88 32
        $brush = Brush $row.Fill
        $g.FillPath($brush,$shape)
        Text $g $row.Time ($row.X+20) ($row.Y+20) 39 $row.Ink $true
        $cx = $row.X+250; $cy=$row.Y+44
        if ($row.Done) {
            $pen = [Drawing.Pen]::new((Color '#FFFFFF'), 5)
            $pen.StartCap = [Drawing.Drawing2D.LineCap]::Round
            $pen.EndCap = [Drawing.Drawing2D.LineCap]::Round
            $g.DrawLines($pen,[Drawing.Point[]]@([Drawing.Point]::new(($cx-11),$cy),[Drawing.Point]::new(($cx-3),($cy+8)),[Drawing.Point]::new(($cx+12),($cy-10))))
        } else {
            $pen = [Drawing.Pen]::new((Color $row.Detail),4)
            $g.DrawEllipse($pen,$cx-13,$cy-13,26,26)
        }
        $pen.Dispose(); $brush.Dispose(); $shape.Dispose()
    }
    $bitmap.Save((Join-Path $assets "feature-$locale-1024x500.png"),[Drawing.Imaging.ImageFormat]::Png)
    $bg1.Dispose(); $bg2.Dispose(); $g.Dispose(); $bitmap.Dispose()
}
$wear = Join-Path $assets 'wear-ru'
New-Item -ItemType Directory -Path $wear -Force | Out-Null
Copy-Item -LiteralPath (Join-Path $RepositoryRoot 'wear/src/test/screenshots/watch_dose_pending.png') -Destination (Join-Path $wear '01-dose-actions.png')
Copy-Item -LiteralPath (Join-Path $RepositoryRoot 'wear/src/test/screenshots/watch_dose_taken.png') -Destination (Join-Path $wear '02-dose-history.png')
Get-ChildItem -LiteralPath $assets -Recurse -Filter '*.png' | Select-Object Name, Length
