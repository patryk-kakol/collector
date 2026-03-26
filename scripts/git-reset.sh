#!/bin/bash

git checkout --orphan temporary_branch
git add -A
git commit -m "Initial commit (history reset)"
git branch -D main
git branch -m main
git push -f origin main