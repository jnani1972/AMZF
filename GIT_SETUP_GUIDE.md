# Git Repository Setup Guide

Your new git repository has been initialized successfully! This guide will help you connect it to a remote hosting service (GitHub, GitLab, or Bitbucket).

---

## Current Status

✅ **Local repository initialized**
✅ **Initial commit created** (commit: `8f5d1b7`)
✅ **15 files committed** (9,036+ insertions)

```bash
# Check current status
git status

# View commit history
git log --oneline

# View what was committed
git show --stat
```

---

## Option 1: Push to GitHub

### Step 1: Create GitHub Repository

1. Go to https://github.com/new
2. Repository name: `amzf-refactored` (or your preferred name)
3. Description: "AMZF Trading System - Refactored Clean Architecture"
4. Choose: **Private** (recommended for trading systems)
5. **DO NOT** initialize with README, .gitignore, or license (we already have these)
6. Click "Create repository"

### Step 2: Connect Local to GitHub

```bash
# Add GitHub as remote origin
git remote add origin https://github.com/YOUR_USERNAME/amzf-refactored.git

# Verify remote was added
git remote -v

# Push to GitHub (first time)
git push -u origin main

# Enter your GitHub credentials when prompted
```

### Step 3: Verify on GitHub

Open your browser and navigate to:
```
https://github.com/YOUR_USERNAME/amzf-refactored
```

You should see all your files and the README displayed.

---

## Option 2: Push to GitLab

### Step 1: Create GitLab Project

1. Go to https://gitlab.com/projects/new
2. Project name: `amzf-refactored`
3. Description: "AMZF Trading System - Refactored Clean Architecture"
4. Visibility: **Private**
5. **Uncheck** "Initialize repository with a README"
6. Click "Create project"

### Step 2: Connect Local to GitLab

```bash
# Add GitLab as remote origin
git remote add origin https://gitlab.com/YOUR_USERNAME/amzf-refactored.git

# Verify remote was added
git remote -v

# Push to GitLab (first time)
git push -u origin main

# Enter your GitLab credentials when prompted
```

---

## Option 3: Push to Bitbucket

### Step 1: Create Bitbucket Repository

1. Go to https://bitbucket.org/repo/create
2. Project name: (Select existing or create new)
3. Repository name: `amzf-refactored`
4. Access level: **Private**
5. **Uncheck** "Include a README"
6. Click "Create repository"

### Step 2: Connect Local to Bitbucket

```bash
# Add Bitbucket as remote origin
git remote add origin https://bitbucket.org/YOUR_USERNAME/amzf-refactored.git

# Verify remote was added
git remote -v

# Push to Bitbucket (first time)
git push -u origin main
```

---

## Common Git Commands

### Daily Workflow

```bash
# Check status of your working directory
git status

# Add new or modified files
git add .                        # Add all files
git add specific_file.java       # Add specific file

# Commit changes
git commit -m "Your commit message"

# Push to remote
git push

# Pull latest changes (when working with team)
git pull
```

### Branching for Refactoring

```bash
# Create feature branch for Phase 1 (Domain layer)
git checkout -b refactor/phase1-domain-layer

# Work on files, then commit
git add .
git commit -m "Phase 1: Migrate domain layer to subdomains"

# Push feature branch to remote
git push -u origin refactor/phase1-domain-layer

# Switch back to main branch
git checkout main

# Merge feature branch after testing
git merge refactor/phase1-domain-layer

# Delete feature branch (local)
git branch -d refactor/phase1-domain-layer

# Delete feature branch (remote)
git push origin --delete refactor/phase1-domain-layer
```

### Recommended Branch Strategy for Refactoring

```bash
main
├── refactor/phase1-domain-layer      (Week 1)
├── refactor/phase2-infrastructure    (Week 2)
├── refactor/phase3-application       (Week 3)
├── refactor/phase4-presentation      (Week 4)
├── refactor/phase5-configuration     (Week 5)
└── refactor/phase6-bootstrap         (Week 6)
```

**Workflow**:
1. Create branch for each phase
2. Implement changes
3. Test thoroughly
4. Merge to main only when tests pass
5. Main branch always stays stable

---

## Protecting Your Repository

### Add Collaborators (GitHub/GitLab/Bitbucket)

**GitHub**:
1. Go to repository → Settings → Collaborators
2. Add team members by username
3. Choose permission level (Read, Write, Admin)

**GitLab**:
1. Go to repository → Settings → Members
2. Add team members by email/username
3. Choose role (Guest, Reporter, Developer, Maintainer, Owner)

**Bitbucket**:
1. Go to repository → Settings → Access management
2. Add users/groups
3. Choose permission level

### Branch Protection (Recommended for Main)

**GitHub**:
```
Settings → Branches → Add branch protection rule
- Branch name pattern: main
- ✅ Require pull request reviews before merging
- ✅ Require status checks to pass (CI/CD)
- ✅ Require branches to be up to date
```

**GitLab**:
```
Settings → Repository → Protected branches
- Branch: main
- Allowed to merge: Maintainers
- Allowed to push: No one
```

---

## Setting Up SSH (Optional but Recommended)

SSH is more secure and convenient than HTTPS (no password prompts).

### Generate SSH Key

```bash
# Generate new SSH key
ssh-keygen -t ed25519 -C "your_email@example.com"

# Press Enter to accept default file location
# Enter passphrase (optional but recommended)

# Start SSH agent
eval "$(ssh-agent -s)"

# Add SSH key to agent
ssh-add ~/.ssh/id_ed25519

# Copy public key to clipboard (macOS)
pbcopy < ~/.ssh/id_ed25519.pub
```

### Add SSH Key to GitHub

1. Go to https://github.com/settings/keys
2. Click "New SSH key"
3. Title: "MacBook Pro" (or your device name)
4. Paste the public key
5. Click "Add SSH key"

### Add SSH Key to GitLab

1. Go to https://gitlab.com/-/profile/keys
2. Paste the public key
3. Title: "MacBook Pro"
4. Click "Add key"

### Switch Remote to SSH

```bash
# Remove HTTPS remote
git remote remove origin

# Add SSH remote (GitHub example)
git remote add origin git@github.com:YOUR_USERNAME/amzf-refactored.git

# Or for GitLab
git remote add origin git@gitlab.com:YOUR_USERNAME/amzf-refactored.git

# Verify
git remote -v

# Test connection
ssh -T git@github.com     # For GitHub
ssh -T git@gitlab.com     # For GitLab
```

---

## CI/CD Integration (Optional)

### GitHub Actions (Recommended)

Create `.github/workflows/maven.yml`:

```yaml
name: Java CI with Maven

on:
  push:
    branches: [ main ]
  pull_request:
    branches: [ main ]

jobs:
  build:
    runs-on: ubuntu-latest

    steps:
    - uses: actions/checkout@v3

    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'

    - name: Build with Maven
      run: cd amzf && mvn clean compile

    - name: Run tests
      run: cd amzf && mvn test

    - name: Generate test coverage report
      run: cd amzf && mvn jacoco:report
```

### GitLab CI

Create `.gitlab-ci.yml`:

```yaml
image: maven:3.8-openjdk-17

stages:
  - build
  - test

build:
  stage: build
  script:
    - cd amzf
    - mvn clean compile
  artifacts:
    paths:
      - amzf/target/

test:
  stage: test
  script:
    - cd amzf
    - mvn test
  dependencies:
    - build
```

---

## Useful Git Aliases

Add to your `~/.gitconfig`:

```ini
[alias]
    st = status
    co = checkout
    br = branch
    ci = commit
    unstage = reset HEAD --
    last = log -1 HEAD
    visual = log --graph --oneline --all
    amend = commit --amend --no-edit
```

Usage:
```bash
git st              # Instead of git status
git co main         # Instead of git checkout main
git br              # Instead of git branch
git visual          # Pretty commit graph
```

---

## Troubleshooting

### Problem: "fatal: remote origin already exists"

```bash
# Remove existing remote
git remote remove origin

# Add new remote
git remote add origin YOUR_REMOTE_URL
```

### Problem: "Permission denied (publickey)"

```bash
# Verify SSH key is added to agent
ssh-add -l

# If not listed, add it
ssh-add ~/.ssh/id_ed25519

# Test connection
ssh -T git@github.com
```

### Problem: "Updates were rejected because the remote contains work"

```bash
# Pull with rebase
git pull --rebase origin main

# Or force push (DANGEROUS - only use if you're sure)
git push -f origin main
```

### Problem: Large files causing push to fail

```bash
# Check file sizes
git ls-files | xargs -I {} sh -c 'echo "$(git cat-file -s {}) {}"' | sort -rn | head -10

# Remove large file from history (if accidentally committed)
git rm --cached path/to/large/file
git commit -m "Remove large file"

# Consider using Git LFS for large files
git lfs install
git lfs track "*.jar"
```

---

## Next Steps

1. ✅ Choose a hosting service (GitHub recommended)
2. ✅ Create remote repository
3. ✅ Connect local to remote
4. ✅ Push initial commit
5. ✅ Add collaborators
6. ✅ Set up branch protection
7. ✅ Create refactoring branches
8. ✅ Start Phase 1 implementation

---

## Quick Reference Card

```bash
# Essential Commands
git status                          # Check what's changed
git add .                           # Stage all changes
git commit -m "message"             # Commit staged changes
git push                            # Push to remote
git pull                            # Pull from remote

# Branching
git branch                          # List branches
git checkout -b new-branch          # Create and switch to branch
git checkout main                   # Switch to main
git merge branch-name               # Merge branch into current

# Undoing Changes
git checkout -- file.java           # Discard changes in file
git reset HEAD file.java            # Unstage file
git reset --soft HEAD~1             # Undo last commit (keep changes)
git reset --hard HEAD~1             # Undo last commit (discard changes)

# Viewing History
git log                             # View commit history
git log --oneline --graph           # Pretty commit graph
git diff                            # View unstaged changes
git diff --staged                   # View staged changes

# Remote Management
git remote -v                       # List remotes
git remote add origin URL           # Add remote
git remote remove origin            # Remove remote
git push -u origin main             # Push and set upstream
```

---

## Support

For git help:
```bash
git help
git help commit
git help branch
```

Online resources:
- Git documentation: https://git-scm.com/doc
- GitHub guides: https://guides.github.com
- GitLab docs: https://docs.gitlab.com
- Atlassian Git tutorials: https://www.atlassian.com/git/tutorials

---

**Repository initialized**: 2026-01-14
**Commit**: 8f5d1b7
**Files**: 15 files, 9,036+ lines
**Status**: Ready to push to remote!
