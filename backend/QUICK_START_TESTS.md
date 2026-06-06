# 🚀 Quick Start: Running Integration Tests

## ✅ Prerequisites Checklist

Before running integration tests, ensure:

- [ ] Docker Desktop is installed and running
- [ ] Docker socket is accessible
- [ ] Required Docker images are available (or can be pulled)

---

## 🎯 Run Integration Tests

### **Step 1: Start Docker Desktop**

```bash
# macOS - Start Docker Desktop
open -a Docker

# Wait for it to fully start (watch menu bar icon)

# Verify Docker is working
docker info
```

### **Step 2: Pull Required Images (Optional but Recommended)**

```bash
# Pull PostgreSQL image
docker pull postgres:16-alpine

# This happens automatically on first run, but pulling manually shows progress
```

### **Step 3: Run Tests**

```bash
cd /Users/sulaiman/IdeaProjects/school-fee-app/backend

# Run the specific integration test
./gradlew test --tests UserManagementServiceIntegrationTest

# Or run all tests (unit + integration)
./gradlew test
```

---

## 🔧 If You Get Errors

### **Error: "Could not find a valid Docker environment"**

**Solution:**
```bash
# Check Docker is running
docker info

# If not, start it
open -a Docker

# Wait and try again
```

### **Error: "Can't get Docker image: RemoteDockerImage"**

**Solution:**
```bash
# Pull images manually
docker pull testcontainers/ryuk:0.11.0
docker pull postgres:16-alpine

# Then run tests
./gradlew test --tests UserManagementServiceIntegrationTest
```

### **Error: Permission denied on Docker socket**

**Solution:**
```bash
# Fix socket permissions
chmod 666 /Users/sulaiman/.docker/run/docker.sock

# Or use sudo (not recommended)
sudo ./gradlew test --tests UserManagementServiceIntegrationTest
```

---

## 📊 What Happens When Tests Run

1. ✅ Testcontainers starts PostgreSQL container
2. ✅ Creates database `school_fee_test`
3. ✅ Spring Boot loads application context
4. ✅ Injects database connection details
5. ✅ Runs 12 integration tests against real database
6. ✅ Verifies data persistence, transactions, outbox events
7. ✅ Stops and removes container automatically

**Total time:** ~2-3 minutes (first run), ~1 minute (subsequent runs)

---

## 💡 Pro Tips

### **Tip 1: Keep Docker Running**
Keep Docker Desktop running in the background while developing. It has minimal resource impact when idle.

### **Tip 2: Pre-pull Images**
Pull images once to avoid waiting during first test run:
```bash
docker pull postgres:16-alpine
docker pull testcontainers/ryuk:0.11.0
```

### **Tip 3: Use Gradle Cache**
Gradle caches dependencies and compiled code, so subsequent test runs are faster.

### **Tip 4: Run Specific Tests**
For faster iteration, run specific test methods:
```bash
./gradlew test --tests "UserManagementServiceIntegrationTest.shouldCreateParentSuccessfullyWithAllSteps"
```

---

## 📝 Configuration Files

The following files configure Testcontainers:

1. **Project-level:** `src/test/resources/testcontainers.properties`
   ```properties
   ryuk.disabled=true
   docker.host=unix:///Users/sulaiman/.docker/run/docker.sock
   ```

2. **Home directory:** `~/.testcontainers.properties`
   ```properties
   docker.host=unix:///Users/sulaiman/.docker/run/docker.sock
   ryuk.disabled=true
   ```

These files tell Testcontainers:
- Where to find Docker socket
- Whether to use Ryuk cleanup container

---

## 🎉 Success Indicators

When tests run successfully, you'll see:

```
✅ Building 85% > :test
✅ UserManagementServiceIntegrationTest > Create Parent > Should create parent successfully
✅ UserManagementServiceIntegrationTest > Create Staff > Should create teacher account
✅ ... (all 12 tests pass)
✅ BUILD SUCCESSFUL in 2m 30s
```

Check coverage report:
```bash
open build/reports/jacoco/test/html/index.html
```

---

## 🆘 Still Having Issues?

If Docker continues to cause problems, you can temporarily run unit tests only:

```bash
# Run unit tests (no Docker required)
./gradlew test --tests "*Test" --tests "!*IntegrationTest"

# This skips integration tests but verifies business logic
```

But remember: **Integration tests are important!** They catch database-specific bugs that unit tests miss.

---

## 📚 Related Documentation

- [DOCKER_SETUP_GUIDE.md](DOCKER_SETUP_GUIDE.md) - Detailed Docker setup instructions
- [TESTCONTAINERS_GUIDE.md](TESTCONTAINERS_GUIDE.md) - Testcontainers usage guide
- [TESTING_GUIDE.md](TESTING_GUIDE.md) - General testing documentation
