# School Fee Management System

A modular monolith application for managing school fees, built with Spring Boot (backend) and React (frontend).

## Project Structure

```
school-fee-app/
├── backend/                          # Spring Boot Backend
│   ├── build.gradle                  # Backend build config
│   ├── Dockerfile                    # Backend Docker image
│   └── src/
│       ├── main/
│       │   ├── java/com/fee/app/schoolfeeapp/
│       │   │   ├── auth/             # Authentication module
│       │   │   │   ├── AuthController.java
│       │   │   │   ├── AuthService.java
│       │   │   │   ├── JwtTokenProvider.java
│       │   │   │   ├── LoginRequest.java
│       │   │   │   ├── RegisterRequest.java
│       │   │   │   ├── SecurityConfig.java
│       │   │   │   └── User.java
│       │   │   ├── school/           # School management module
│       │   │   │   ├── SchoolController.java
│       │   │   │   └── SchoolService.java
│       │   │   ├── fee/              # Fee management module
│       │   │   │   ├── FeeController.java
│       │   │   │   ├── domain/       # JPA entities
│       │   │   │   │   ├── FeeType.java
│       │   │   │   │   ├── FeeFrequency.java
│       │   │   │   │   ├── StudentFee.java
│       │   │   │   │   └── FeeStatus.java
│       │   │   │   ├── repository/   # Spring Data JPA repos
│       │   │   │   │   ├── FeeTypeRepository.java
│       │   │   │   │   └── StudentFeeRepository.java
│       │   │   │   ├── service/      # Business logic
│       │   │   │   │   └── FeeService.java
│       │   │   │   └── config/       # Module config
│       │   │   │       └── FeeModuleConfig.java
│       │   │   ├── payment/          # Payment processing module
│       │   │   │   ├── PaymentController.java
│       │   │   │   └── PaymentService.java
│       │   │   ├── notification/     # Notification module
│       │   │   │   ├── NotificationController.java
│       │   │   │   └── NotificationService.java
│       │   │   ├── config/           # Global config
│       │   │   │   ├── CorsConfig.java
│       │   │   │   └── OpenApiConfig.java
│       │   │   └── SchoolFeeAppApplication.java
│       │   └── resources/
│       │       ├── application.yaml       # Dev config (H2)
│       │       ├── application-prod.yaml  # Prod config (PostgreSQL)
│       │       └── db/
│       └── test/                     # Unit & integration tests
├── frontend/                         # React Frontend
│   ├── package.json
│   ├── Dockerfile
│   ├── nginx.conf
│   ├── vite.config.ts
│   └── src/
│       ├── App.tsx                   # Root component with routing
│       ├── main.tsx                  # Entry point
│       ├── index.css                 # Global styles
│       ├── services/
│       │   └── api.ts                # API client (Axios)
│       ├── hooks/
│       │   └── useAuth.ts            # Authentication hook
│       ├── types/
│       │   └── index.ts              # TypeScript types
│       ├── components/
│       │   ├── layout/
│       │   │   ├── Sidebar.tsx
│       │   │   └── Layout.tsx
│       │   └── ui/                   # shadcn/ui components
│       └── pages/
│           ├── Login.tsx
│           ├── Dashboard.tsx
│           ├── Schools.tsx
│           ├── Fees.tsx
│           ├── Payments.tsx
│           └── Notifications.tsx
├── docker-compose.yml                # Full stack orchestration
├── build.gradle                      # Root build config
└── settings.gradle                   # Gradle settings
```

## Prerequisites

- **JDK 17+**
- **Node.js 20+**
- **Docker & Docker Compose** (optional, for containerized deployment)
- **Gradle 8+** (or use the included wrapper)

## Quick Start

### Option 1: Run with Docker Compose (Recommended)

1. Clone/download the project:
   ```bash
   cd school-fee-app
   ```

2. Start all services:
   ```bash
   docker-compose up -d
   ```

3. Access the application:
   - Frontend: http://localhost:3000
   - Backend API: http://localhost:8080
   - API Documentation (Swagger UI): http://localhost:8080/swagger-ui.html
   - H2 Console (dev mode): http://localhost:8080/h2-console
   - MailHog (test emails): http://localhost:8025

4. Stop services:
   ```bash
   docker-compose down
   ```

### Option 2: Run Backend & Frontend Separately

#### Backend

```bash
cd backend

# Run with Gradle
./gradlew bootRun

# Or build and run JAR
./gradlew bootJar
java -jar build/libs/school-fee-app.jar
```

The backend will start on **http://localhost:8080** with:
- H2 in-memory database (auto-configured)
- Pre-loaded sample data (3 schools, 5 fee types)
- Demo authentication (any username/password accepted)

#### Frontend

```bash
cd frontend

# Install dependencies
npm install

# Start development server
npm run dev
```

The frontend will start on **http://localhost:5173**

### Option 3: Run Backend Only (Headless)

```bash
cd backend
./gradlew bootRun
```

Use the API directly via curl, Postman, or the Swagger UI.

## Authentication

The application uses **JWT-based authentication** in a **demo mode**:
- Any username/password combination will be accepted for login
- A JWT token is returned and stored in localStorage
- Subsequent API calls include the token in the `Authorization: Bearer <token>` header
- Token expiry: 24 hours

To switch to real authentication:
1. Update `AuthService.java` to validate against a database
2. Uncomment the `User` entity and create a `UserRepository`
3. Update password validation in `AuthService.authenticate()`

## API Endpoints

### Authentication
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/auth/login` | Login and get JWT token |
| POST | `/api/auth/register` | Register new account |
| GET | `/api/auth/me` | Get current user info |

### Schools
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/schools` | List all schools |
| GET | `/api/schools/{id}` | Get school by ID |
| POST | `/api/schools` | Create school |
| PUT | `/api/schools/{id}` | Update school |
| DELETE | `/api/schools/{id}` | Delete school |

### Fees
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/fees/types` | List all fee types |
| POST | `/api/fees/types` | Create fee type |
| PUT | `/api/fees/types/{id}` | Update fee type |
| DELETE | `/api/fees/types/{id}` | Delete fee type |
| GET | `/api/fees/student/{studentId}` | Get student fees |
| POST | `/api/fees/assign` | Assign fee to student |
| POST | `/api/fees/{id}/pay` | Record payment |

### Payments
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/payments` | List payments |
| POST | `/api/payments` | Process payment |
| GET | `/api/payments/summary` | Payment statistics |

### Notifications
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/notifications` | List notifications |
| POST | `/api/notifications/send` | Send notification |
| GET | `/api/notifications/templates` | Get templates |

## Module Architecture

Each module follows a clean architecture pattern:

```
module/
  Controller.java      # REST API endpoints
  Service.java         # Business logic
  domain/
    Entity.java        # JPA entities
    Enum.java          # Domain enums
  repository/
    Repository.java    # Spring Data JPA
  config/
    Config.java        # Module-specific config
```

### Modules
- **auth**: JWT authentication, security configuration
- **school**: School CRUD operations
- **fee**: Fee type management, student fee assignment, payment recording
- **payment**: Payment processing and tracking
- **notification**: Email, SMS, and in-app notifications

## Technology Stack

### Backend
- **Java 17** with **Spring Boot 3.2**
- **Spring Data JPA** with **Hibernate**
- **Spring Security** with JWT
- **H2 Database** (dev) / **PostgreSQL** (prod)
- **Lombok** for boilerplate reduction
- **MapStruct** for DTO mapping
- **SpringDoc OpenAPI** for API documentation
- **Gradle** build system

### Frontend
- **React 19** with **TypeScript**
- **Vite** build tool
- **Tailwind CSS** for styling
- **shadcn/ui** component library
- **React Router** for navigation
- **Axios** for API calls
- **Recharts** for data visualization
- **Lucide React** for icons

### DevOps
- **Docker** & **Docker Compose**
- **Nginx** (frontend reverse proxy)
- **MailHog** (email testing)

## Development

### Adding a New Module

1. Create a new package under `com.fee.app.schoolfeeapp.<module>`
2. Add domain entities, repository, service, and controller
3. Create module configuration if needed
4. The module is automatically scanned by Spring Boot

### Database Configuration

**Development (H2 - default):**
```yaml
# application.yaml (already configured)
spring.datasource.url: jdbc:h2:mem:schoolfeedb
spring.h2.console.enabled: true
```

**Production (PostgreSQL):**
```bash
# Use the prod profile
SPRING_PROFILES_ACTIVE=prod ./gradlew bootRun

# Or with Docker Compose (already configured)
docker-compose up -d
```

## Environment Variables

| Variable | Description | Default |
|----------|-------------|---------|
| `SPRING_PROFILES_ACTIVE` | Spring profile | `dev` |
| `DB_USERNAME` | Database username | `postgres` |
| `DB_PASSWORD` | Database password | `postgres` |
| `JWT_SECRET` | JWT signing secret | (see config) |
| `VITE_API_URL` | Frontend API URL | `http://localhost:8080/api` |

## Testing

```bash
# Backend tests
cd backend
./gradlew test

# Frontend tests
cd frontend
npm test
```

## License

MIT
