# DestinAI

A web application that helps travelers quickly discover suitable holiday destinations at the country level using AI-powered recommendations.

## Project Description

DestinAI is a web application designed to reduce the time travelers spend researching destinations. Users answer a fixed set of mandatory, closed questions about their travel preferences. The application converts these answers into a structured prompt, sends it to an external LLM API, validates and parses the response (strict JSON), and displays exactly 5 recommended countries. Users can save recommendations to a Favorites list, add short notes, and manage their saved destinations.

### Key Features

- **Quick Destination Discovery**: Generate 5 relevant country recommendations from a short questionnaire
- **Structured Results**: Consistent, comparable recommendations using strict JSON schema validation
- **Favorites Management**: Save, annotate, and manage up to 50 favorite destinations per user
- **Passwordless Authentication**: Secure email-based one-time code/link authentication
- **Responsive Design**: Web-only application optimized for mobile and desktop browsers

### Target Users (MVP)

- Solo travelers
- Backpackers
- Couples

## Tech Stack

### Backend
- **Java 21**: Programming language
- **Spring Boot 4.0.2**: Application framework
- **Spring MVC**: Web framework for server-rendered UI
- **Spring Security**: Authentication and authorization
- **Spring Data JPA**: Data persistence layer
- **Thymeleaf**: Server-side templating engine
- **Flyway**: Database migration tool
- **PostgreSQL**: Relational database
- **Spring Mail**: Email service for OTP delivery

### Testing
- **Testcontainers**: Integration testing with containerized databases
- **JUnit Jupiter**: Testing framework
- **Spring Boot Test**: Testing utilities

### Build & Deployment
- **Maven**: Build tool and dependency management
- **Docker**: Containerization (deployment target: DigitalOcean)

### Architecture Highlights

- **Server-Rendered UI**: Thin UI with 4 screens (Login, Questionnaire, Results, Favorites)
- **LLM Integration**: External free-access LLM API with strict JSON validation and repair retry logic
- **Normalization Pipeline**: Validate → enforce count=5 → dedupe → diversification → re-validate → render
- **Secure Sessions**: HttpOnly cookies with CSRF protection
- **Rate Limiting**: Protection against abuse for OTP requests

## Getting Started Locally

### Prerequisites

- **Java 21** or higher
- **Maven 3.6+** (or use the included Maven Wrapper)
- **PostgreSQL 12+** (or use Docker for local development)
- **Email Service**: Configured SMTP server for OTP delivery (development can use test configuration)

### Installation Steps

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd DestinAI
   ```

2. **Set up PostgreSQL database**
   
   Create a PostgreSQL database for the application:
   ```bash
   createdb destinai
   ```
   
   Or use Docker:
   ```bash
   docker run --name destinai-postgres -e POSTGRES_PASSWORD=password -e POSTGRES_DB=destinai -p 5432:5432 -d postgres:15
   ```

3. **Configure application properties**
   
   Update `src/main/resources/application.properties` with your database and email configuration:
   ```properties
   spring.datasource.url=jdbc:postgresql://localhost:5432/destinai
   spring.datasource.username=your_username
   spring.datasource.password=your_password
   
   spring.mail.host=smtp.example.com
   spring.mail.port=587
   spring.mail.username=your_email@example.com
   spring.mail.password=your_email_password
   ```

4. **Run database migrations**
   
   Flyway will automatically run migrations on application startup, or you can run them manually:
   ```bash
   ./mvnw flyway:migrate
   ```

5. **Build the application**
   ```bash
   ./mvnw clean install
   ```

6. **Run the application**
   ```bash
   ./mvnw spring-boot:run
   ```
   
   Or run the JAR file:
   ```bash
   java -jar target/demo-0.0.1-SNAPSHOT.jar
   ```

7. **Access the application**
   
   Open your browser and navigate to:
   ```
   http://localhost:8080
   ```

### Using Maven Wrapper

The project includes Maven Wrapper scripts:
- **Unix/macOS**: `./mvnw`
- **Windows**: `./mvnw.cmd`

You can use these instead of a globally installed Maven.

## Available Scripts

### Build Commands

```bash
# Clean and compile
./mvnw clean compile

# Run tests
./mvnw test

# Package application (creates JAR)
./mvnw package

# Install to local Maven repository
./mvnw install

# Skip tests during build
./mvnw install -DskipTests
```

### Run Commands

```bash
# Run application
./mvnw spring-boot:run

# Run with specific profile
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Database Commands

```bash
# Run Flyway migrations
./mvnw flyway:migrate

# Validate Flyway migrations
./mvnw flyway:validate

# Clean database (WARNING: drops all objects)
./mvnw flyway:clean
```

### Testing Commands

```bash
# Run all tests
./mvnw test

# Run specific test class
./mvnw test -Dtest=DestinaiApplicationTests

# Run tests with coverage (requires JaCoCo plugin)
./mvnw test jacoco:report
```

## Project Scope

### In Scope (MVP)

1. **Web Application**: 4 screens (Login, Questionnaire, Results, Favorites)
2. **Fixed Questionnaire**: Mandatory closed questions covering:
   - Travel party (solo, couple)
   - Travel type (backpacking, staying in one place)
   - Accommodation (camping, hostels, hotels)
   - Activities (multi-select: hiking, diving, tennis, canoeing, climbing, surfing, local culture, local cuisine)
   - Budget (very low, medium, luxurious)
   - Weather (sunny-dry, sunny-humid, cool, rainy)
   - Season (winter, spring, summer, autumn)
3. **LLM Integration**: External free-access LLM API with strict JSON output validation
4. **Normalization Pipeline**: 
   - Schema validation
   - Enforce exactly 5 destinations
   - Deduplicate countries
   - Diversification (max 2 per region)
   - Re-validation before rendering
5. **Passwordless Authentication**: Email-based one-time codes/links
6. **Favorites CRUD**: Save, view, update notes (max 100 chars), delete favorites (max 50 per user)

### Out of Scope (MVP)

1. Mobile native apps
2. User-configurable model selection or LLM provider switching
3. Custom recommendation algorithm (non-LLM)
4. Integrations with booking sites, maps, calendars
5. Social/sharing features
6. Safety/danger filtering or travel advisory integration
7. Saving questionnaire history or building user profiles
8. Multi-language support

## Project Status

**Current Status**: MVP Development

### Success Metrics (MVP)

- **Reliability Target**: At least 70% of recommendation attempts result in successful, rendered results
- **LLM Success KPI**: `valid_JSON AND schema_valid AND destinations_count==5 AND render_success`
- **Favorites Feature**: Reliable CRUD operations with deduplication and cap enforcement

### Key Requirements

- **Performance**: End-to-end recommendation generation within 20-30 seconds (including retries)
- **Security**: Secure session handling, rate-limited OTP requests, CSRF protection
- **Accessibility**: Responsive design, keyboard navigation, sufficient contrast
- **Observability**: Server-side logging with reason codes for failures

### Known Limitations

- Country-level recommendations only (no city/region granularity)
- Safety/danger screening intentionally skipped in MVP
- No questionnaire submission history
- English language only
- Maximum 50 favorites per user

## License

[License information to be added]

---

## Additional Documentation

- [Product Requirements Document (PRD)](.ai/prd.md)
- [Tech Stack Analysis](.ai/tech-stack.md)

## Contributing

[Contributing guidelines to be added]

## Support

[Support information to be added]
