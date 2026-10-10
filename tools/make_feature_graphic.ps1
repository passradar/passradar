# Draws the 1024x500 feature graphic Google Play shows at the top of the store listing,
# one per language, from the 512 px store icon.
#
# Usage (Windows PowerShell):  powershell -File tools/make_feature_graphic.ps1
# Change $name and the taglines below if the app gets another name.
param(
    [string]$name = "Passradar"
)

Add-Type -AssemblyName System.Drawing

$root = Split-Path -Parent $PSScriptRoot
$meta = Join-Path $root "fastlane\metadata\android"
$icon = [System.Drawing.Image]::FromFile((Join-Path $meta "en-US\images\icon.png"))

$taglines = @{
    "nb-NO" = "Få varsel når det blir ledig en tidligere time for pass og ID-kort"
    "en-US" = "Get an alert when an earlier passport or ID card appointment opens up"
}

foreach ($locale in $taglines.Keys) {
    $bmp = New-Object System.Drawing.Bitmap 1024, 500, ([System.Drawing.Imaging.PixelFormat]::Format24bppRgb)
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias
    $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
    $g.TextRenderingHint = [System.Drawing.Text.TextRenderingHint]::AntiAliasGridFit

    $rect = New-Object System.Drawing.Rectangle 0, 0, 1024, 500
    $top = [System.Drawing.Color]::FromArgb(255, 0x00, 0x7A, 0x6E)
    $bottom = [System.Drawing.Color]::FromArgb(255, 0x00, 0x33, 0x2E)
    $brush = New-Object System.Drawing.Drawing2D.LinearGradientBrush $rect, $top, $bottom, 60.0
    $g.FillRectangle($brush, $rect)

    # Soft light circle behind the icon, a nod to the glass look in the app.
    $glow = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(40, 255, 255, 255))
    $g.FillEllipse($glow, 40, 70, 360, 360)
    $g.DrawImage($icon, 90, 120, 260, 260)

    $white = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::White)
    $soft = New-Object System.Drawing.SolidBrush ([System.Drawing.Color]::FromArgb(225, 255, 255, 255))
    $titleFont = New-Object System.Drawing.Font "Segoe UI", 64, ([System.Drawing.FontStyle]::Bold), ([System.Drawing.GraphicsUnit]::Pixel)
    $tagFont = New-Object System.Drawing.Font "Segoe UI", 30, ([System.Drawing.FontStyle]::Regular), ([System.Drawing.GraphicsUnit]::Pixel)
    $g.DrawString($name, $titleFont, $white, 420, 150)
    $tagRect = New-Object System.Drawing.RectangleF 424, 245, 560, 140
    $g.DrawString($taglines[$locale], $tagFont, $soft, $tagRect)

    $dir = Join-Path $meta "$locale\images"
    New-Item -ItemType Directory -Force $dir | Out-Null
    $bmp.Save((Join-Path $dir "featureGraphic.png"), [System.Drawing.Imaging.ImageFormat]::Png)
    $g.Dispose(); $bmp.Dispose()
    Write-Output "$locale\images\featureGraphic.png"
}
$icon.Dispose()
