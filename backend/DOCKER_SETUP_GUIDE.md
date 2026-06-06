# 🐳 Docker Setup Guide for Testcontainers

## ❌ Error: Could not find a valid Docker environment

This error occurs when Testcontainers cannot connect to a running Docker daemon.

---

## 🔍 Quick Check

First, verify if Docker is available:

```bash
docker info
```

**Expected output:** Should show Docker system information  
**If you see "Cannot connect to the Docker daemon":** Docker is not running

---

## ✅ Solutions

### **Solution 1: Start Docker Desktop (macOS/Windows)**

#### **For macOS:**

1. **Check if Docker Desktop is installed:**
   ```bash
   ls -la /Applications/Docker.app
   ```

2. **Start Docker Desktop:**
   ```bash
   open -a Docker
   ```

3. **Wait for Docker to fully start:**
   - Look for the whale icon in your menu bar
   - Wait until it shows "Docker Desktop is running"

4. **Verify Docker is working:**
   ```bash
   docker --version
   docker info
   docker ps
   ```

5. **Run your tests:**
   ```bash
   ./gradlew test --tests UserManagementServiceIntegrationTest
   ```

#### **For Windows:**

1. Open **Docker Desktop** from Start Menu
2. Wait for the whale icon in system tray
3. Verify in PowerShell:
   ```powershell
   docker version
   docker info
   ```
4. Run tests

---

### **Solution 2: Install Docker (If Not Installed)**

#### **macOS (Apple Silicon - M1/M2/M3):**

```bash
# Download Docker Desktop for Mac (Apple Silicon)
# Visit: https://www.docker.com/products/docker-desktop/

# Or use Homebrew
brew install --cask docker

# Start Docker Desktop
open -a Docker
```

#### **macOS (Intel):**

```bash
# Download Docker Desktop for Mac (Intel)
# Visit: https://www.docker.com/products/docker-desktop/

# Or use Homebrew
brew install --cask docker

# Start Docker Desktop
open -a Docker
```

#### **Linux (Ubuntu/Debian):**

```bash
# Install Docker Engine
sudo apt-get update
sudo apt-get install \
    ca-certificates \
    curl \
    gnupg \
    lsb-release

# Add Docker's official GPG key
sudo mkdir -p /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg

# Set up repository
echo \
  "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] https://download.docker.com/linux/ubuntu \
  $(lsb_release -cs) stable" | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null

# Install Docker Engine
sudo apt-get update
sudo apt-get install docker-ce docker-ce-cli containerd.io docker-compose-plugin

# Start Docker
sudo systemctl start docker
sudo systemctl enable docker

# Add your user to docker group (optional, avoids using sudo)
sudo usermod -aG docker $USER

# Verify
docker --version
docker run hello-world
```

---

### **Solution 3: Use Colima (macOS Alternative)**

If Docker Desktop doesn't work on your Mac, try Colima (free alternative):

```bash
# Install Colima
brew install colima docker

# Start Colima with Kubernetes
colima start

# Verify
docker info
docker ps

# Run your tests
./gradlew test --tests UserManagementServiceIntegrationTest

# Stop Colima when done
colima stop
```

---

### **Solution 4: Use OrbStack (macOS - Faster Alternative)**

OrbStack is a faster, lighter alternative to Docker Desktop:

```bash
# Install OrbStack
brew install orbstack

# Start OrbStack
open -a OrbStack

# Verify
docker info

# Run tests
./gradlew test --tests UserManagementServiceIntegrationTest
```

---

## 🔧 Troubleshooting

### **Issue: Docker Desktop won't start**

**Solution:**
```bash
# Reset Docker Desktop (macOS)
rm -rf ~/Library/Containers/com.docker.docker
rm -rf ~/Library/Application\ Support/Docker

# Restart Docker Desktop
open -a Docker
```

### **Issue: Permission denied**

**Solution (Linux):**
```bash
# Add user to docker group
sudo usermod -aG docker $USER

# Log out and back in, or run:
newgrp docker

# Verify
docker ps
```

### **Issue: Port conflicts**

**Solution:**
```bash
# Check what's using port 5432 (PostgreSQL default)
lsof -i :5432

# Kill the process or change the port in your test
```

### **Issue: Docker daemon not responding**

**Solution:**
```bash
# Restart Docker service (Linux)
sudo systemctl restart docker

# Restart Docker Desktop (macOS)
osascript -e 'quit app "Docker"'
open -a Docker
```

---

## 🧪 Testing Your Setup

Once Docker is running, verify everything works:

### **Step 1: Test Docker directly**
```bash
docker run --rm postgres:16-alpine echo "Docker works!"
```

### **Step 2: Test with a simple container**
```bash
docker run --rm -d --name test-db \
  -e POSTGRES_DB=test \
  -e POSTGRES_USER=test \
  -e POSTGRES_PASSWORD=test \
  postgres:16-alpine

# Check it's running
docker ps | grep test-db

# Clean up
docker rm -f test-db
```

### **Step 3: Run your Testcontainers test**
```bash
cd backend
./gradlew test --tests UserManagementServiceIntegrationTest
```

---

## 📊 What Happens When Tests Run

With Docker running, Testcontainers will:

1. ✅ Pull `postgres:16-alpine` image (first time only)
2. ✅ Start a PostgreSQL container
3. ✅ Create database `school_fee_test`
4. ✅ Inject connection details into Spring context
5. ✅ Run all 12 integration tests against real database
6. ✅ Verify data persistence, transactions, outbox events
7. ✅ Stop and remove container automatically

**Total time:** ~2-3 minutes (first run), ~1 minute (subsequent runs)

---

## 🎯 Quick Commands Reference

| Action | Command |
|--------|---------|
| Start Docker Desktop (macOS) | `open -a Docker` |
| Check Docker status | `docker info` |
| List running containers | `docker ps` |
| Remove all stopped containers | `docker container prune -f` |
| View Docker logs | `docker logs <container-id>` |
| Run tests | `./gradlew test --tests UserManagementServiceIntegrationTest` |

---

## 🚀 After Docker is Running

Once you've started Docker, simply run:

```bash
# Navigate to backend directory
cd /Users/sulaiman/IdeaProjects/school-fee-app/backend

# Run the integration tests
./gradlew test --tests UserManagementServiceIntegrationTest
```

**That's it!** Testcontainers handles everything else automatically:
- Starts PostgreSQL before tests
- Creates schema and tables
- Runs tests against real database
- Cleans up automatically

---

## 💡 Pro Tip

To avoid this issue in the future, keep Docker Desktop running in the background while developing. It has minimal resource impact when idle and provides instant access to containerized services.

---

## 📚 Additional Resources

- [Testcontainers Documentation](https://www.testcontainers.org/)
- [Docker Desktop Download](https://www.docker.com/products/docker-desktop/)
- [Colima (Alternative)](https://github.com/abiosoft/colima)
- [OrbStack (Faster Alternative)](https://orbstack.dev/)
