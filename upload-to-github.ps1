# Script to upload project to GitHub as a new branch
# Make sure Git is installed before running this script

$repoUrl = "https://github.com/Cocolalilal/Slight-Rikkahub-rework.git"
$branchName = "local-changes-$(Get-Date -Format 'yyyyMMdd-HHmmss')"

Write-Host "Initializing Git repository..." -ForegroundColor Cyan
git init

Write-Host "Adding remote repository..." -ForegroundColor Cyan
git remote add origin $repoUrl

Write-Host "Fetching from remote..." -ForegroundColor Cyan
git fetch origin

Write-Host "Creating new branch: $branchName" -ForegroundColor Cyan
git checkout -b $branchName

Write-Host "Adding all files..." -ForegroundColor Cyan
git add .

Write-Host "Creating initial commit..." -ForegroundColor Cyan
git commit -m "Add local changes as new branch"

Write-Host "Pushing to remote repository..." -ForegroundColor Cyan
git push -u origin $branchName

Write-Host "`nDone! Your code has been pushed to branch: $branchName" -ForegroundColor Green
Write-Host "View it at: https://github.com/Cocolalilal/Slight-Rikkahub-rework/tree/$branchName" -ForegroundColor Green

