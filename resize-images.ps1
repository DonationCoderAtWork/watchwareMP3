# Script to resize smartwatch screenshots to appropriate size for README

Add-Type -AssemblyName System.Drawing

# Define target width for smartwatch screenshots
$targetWidth = 250

# Function to resize an image while maintaining aspect ratio
function Resize-Image {
    param (
        [string]$SourcePath,
        [string]$DestinationPath,
        [int]$Width
    )
    
    Write-Host "Resizing $SourcePath to width $Width pixels..."
    
    try {
        # Load the source image
        $sourceImage = [System.Drawing.Image]::FromFile($SourcePath)
        
        # Calculate new height while maintaining aspect ratio
        $ratio = $sourceImage.Height / $sourceImage.Width
        $newHeight = [int]($Width * $ratio)
        
        # Create new bitmap with target size
        $newImage = New-Object System.Drawing.Bitmap $Width, $newHeight
        
        # Create graphics object from the new image
        $graphics = [System.Drawing.Graphics]::FromImage($newImage)
        
        # Set interpolation mode to high quality
        $graphics.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $graphics.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::HighQuality
        $graphics.PixelOffsetMode = [System.Drawing.Drawing2D.PixelOffsetMode]::HighQuality
        $graphics.CompositingQuality = [System.Drawing.Drawing2D.CompositingQuality]::HighQuality
        
        # Draw the source image to the new image
        $graphics.DrawImage($sourceImage, 0, 0, $Width, $newHeight)
        
        # Save the resized image
        $newImage.Save($DestinationPath, [System.Drawing.Imaging.ImageFormat]::Png)
        
        Write-Host "Resized image saved to $DestinationPath"
        
        # Dispose of resources
        $graphics.Dispose()
        $newImage.Dispose()
        $sourceImage.Dispose()
    }
    catch {
        Write-Error "Error resizing image: $_"
    }
}

# Create resized directory if it doesn't exist
$resizedDir = Join-Path $PSScriptRoot "doc\resized"
if (-not (Test-Path $resizedDir)) {
    New-Item -Path $resizedDir -ItemType Directory
    Write-Host "Created directory: $resizedDir"
}

# Resize each screenshot
$sourceDir = Join-Path $PSScriptRoot "doc"
$screenshots = Get-ChildItem -Path $sourceDir -Filter "Screenshot*.png"

foreach ($screenshot in $screenshots) {
    $sourcePath = $screenshot.FullName
    $destPath = Join-Path $resizedDir $screenshot.Name
    
    Resize-Image -SourcePath $sourcePath -DestinationPath $destPath -Width $targetWidth
}

Write-Host "All images have been resized and saved to $resizedDir"
