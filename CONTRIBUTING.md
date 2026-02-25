# Contributing to EasyWing Platform

Thank you for your interest in contributing to EasyWing Platform! This document provides guidelines and instructions for contributing.

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Setup](#development-setup)
- [How to Contribute](#how-to-contribute)
- [Pull Request Process](#pull-request-process)
- [Coding Standards](#coding-standards)
- [Commit Guidelines](#commit-guidelines)
- [Testing Guidelines](#testing-guidelines)
- [Documentation](#documentation)

## Code of Conduct

This project and everyone participating in it is governed by our Code of Conduct. By participating, you are expected to uphold this code. Please report unacceptable behavior to [team@easywing.io](mailto:team@easywing.io).

### Our Standards

- Be respectful and inclusive
- Welcome newcomers and help them get started
- Accept constructive criticism gracefully
- Focus on what is best for the community
- Show empathy towards other community members

## Getting Started

### Prerequisites

- **Java 21+** (LTS version recommended)
- **Maven 3.9.0+**
- **Docker** (for local development with Testcontainers)
- **Git**
- **IDE**: IntelliJ IDEA (recommended) or Eclipse

### Fork and Clone

```bash
# Fork the repository on GitHub
# Then clone your fork
git clone https://github.com/YOUR_USERNAME/EasyWingPlatform.git
cd EasyWingPlatform

# Add upstream remote
git remote add upstream https://github.com/LasyOtter/EasyWingPlatform.git
```

## Development Setup

### 1. Build the Project

```bash
# Build all modules
mvn clean install -DskipTests

# Build with tests
mvn clean install
```

### 2. IDE Setup

#### IntelliJ IDEA

1. Import as Maven project
2. Enable annotation processing: `Settings > Build, Execution, Deployment > Compiler > Annotation Processors`
3. Install plugins:
   - Lombok
   - MapStruct Support
   - Spring Boot Assistant

#### Eclipse

1. Import as Maven project
2. Install Lombok: `java -jar lombok.jar`
3. Enable annotation processing in project settings

### 3. Start Development Environment

```bash
# Start infrastructure services (Nacos, Redis, MySQL, etc.)
cd easywing-platform-samples
docker-compose up -d

# Verify services
docker-compose ps
```

### 4. Run Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=YourTestClass

# Run integration tests
mvn verify -Pintegration-test

# Run with coverage
mvn verify jacoco:report
```

## How to Contribute

### Reporting Bugs

Before submitting a bug report:

1. Check if the bug has already been reported in [Issues](https://github.com/LasyOtter/EasyWingPlatform/issues)
2. Verify you're using the latest version
3. Collect information:
   - Java version
   - Maven version
   - OS and version
   - Steps to reproduce
   - Expected vs actual behavior
   - Log output (if relevant)

Submit bug reports using the [Bug Report Template](https://github.com/LasyOtter/EasyWingPlatform/issues/new?template=bug_report.md).

### Requesting Features

1. Check if the feature has already been requested
2. Clearly describe the feature and its use case
3. Explain why it would benefit the project

Submit feature requests using the [Feature Request Template](https://github.com/LasyOtter/EasyWingPlatform/issues/new?template=feature_request.md).

### Contributing Code

1. **Find an Issue**: Look for issues marked with `good first issue` or `help wanted`
2. **Claim the Issue**: Comment on the issue to indicate you're working on it
3. **Create a Branch**: Create a feature branch from `develop`
4. **Write Code**: Follow our coding standards
5. **Write Tests**: Ensure adequate test coverage
6. **Submit PR**: Create a pull request

## Pull Request Process

### Before Submitting

- [ ] Code compiles without errors
- [ ] All tests pass (`mvn test`)
- [ ] Code follows style guidelines (`mvn spotless:check`)
- [ ] New code has adequate test coverage
- [ ] Documentation is updated
- [ ] Commit messages follow guidelines

### PR Checklist

1. **Title**: Clear, descriptive title
2. **Description**: Explain what and why, not how
3. **Related Issues**: Link to related issues
4. **Tests**: Include tests for new functionality
5. **Documentation**: Update relevant documentation
6. **Breaking Changes**: Clearly note any breaking changes

### PR Template

```markdown
## Description
Brief description of changes

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Testing
- [ ] Unit tests added/updated
- [ ] Integration tests added/updated
- [ ] Manual testing performed

## Checklist
- [ ] Code follows style guidelines
- [ ] Self-review completed
- [ ] Documentation updated
- [ ] No new warnings introduced
```

### Review Process

1. **Automated Checks**: CI/CD pipeline runs automatically
2. **Code Review**: At least one maintainer review required
3. **Approval**: PR requires approval from maintainers
4. **Merge**: Squash and merge to `develop`

## Coding Standards

### Java Style

We follow Google Java Style Guide with some modifications:

```java
// Class naming: PascalCase
public class UserService {

    // Constants: SCREAMING_SNAKE_CASE
    private static final int MAX_RETRY_COUNT = 3;

    // Fields: camelCase
    private final UserRepository userRepository;

    // Constructor
    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // Methods: camelCase, verb phrase
    public Optional<User> findUserById(Long id) {
        Objects.requireNonNull(id, "User ID must not be null");
        return userRepository.findById(id);
    }
}
```

### Code Formatting

We use Spotless with Palantir Java Format:

```bash
# Check formatting
mvn spotless:check

# Auto-format code
mvn spotless:apply
```

### Import Order

```java
import java.*;        // Java standard library
import jakarta.*;     // Jakarta EE
import org.*;         // Third-party (org.*)
import com.*;         // Third-party (com.*)
import com.easywing.*; // EasyWing Platform
```

### Naming Conventions

| Type | Convention | Example |
|------|------------|---------|
| Package | lowercase | `com.easywing.platform.core` |
| Class | PascalCase | `UserService` |
| Interface | PascalCase | `UserRepository` |
| Method | camelCase | `findUserById` |
| Variable | camelCase | `userList` |
| Constant | SCREAMING_SNAKE_CASE | `MAX_SIZE` |
| Enum | PascalCase | `UserStatus` |

### Documentation

```java
/**
 * Service for managing user accounts.
 *
 * <p>This service provides CRUD operations for users and handles
 * user-related business logic such as registration and authentication.
 *
 * @author EasyWing Team
 * @since 1.0.0
 */
public interface UserService {

    /**
     * Finds a user by their unique identifier.
     *
     * @param id the user's unique identifier, must not be null
     * @return an Optional containing the user, or empty if not found
     * @throws NullPointerException if id is null
     */
    Optional<User> findUserById(Long id);
}
```

## Commit Guidelines

### Commit Message Format

```
<type>(<scope>): <subject>

<body>

<footer>
```

### Types

| Type | Description |
|------|-------------|
| `feat` | New feature |
| `fix` | Bug fix |
| `docs` | Documentation only |
| `style` | Code style (formatting, etc.) |
| `refactor` | Code refactoring |
| `perf` | Performance improvement |
| `test` | Adding or updating tests |
| `chore` | Build, CI, or tooling |
| `deps` | Dependency updates |

### Examples

```bash
# Feature
feat(security): add OAuth2 resource server support

# Bug fix
fix(web): resolve RFC 9457 error response encoding issue

# Documentation
docs(readme): update quick start guide

# Breaking change
feat(api)!: change user service interface

BREAKING CHANGE: The findUserById method now returns Optional<User>
```

## Testing Guidelines

### Test Naming

```java
class UserServiceTest {

    @Test
    void findUserById_WhenUserExists_ReturnsUser() {
        // ...
    }

    @Test
    void findUserById_WhenUserNotExists_ReturnsEmpty() {
        // ...
    }

    @Test
    void findUserById_WhenIdIsNull_ThrowsNullPointerException() {
        // ...
    }
}
```

### Test Structure (Given-When-Then)

```java
@Test
void createUser_WhenValidInput_ReturnsCreatedUser() {
    // Given
    CreateUserRequest request = new CreateUserRequest("john@example.com", "John Doe");
    
    // When
    User result = userService.createUser(request);
    
    // Then
    assertThat(result).isNotNull();
    assertThat(result.getEmail()).isEqualTo("john@example.com");
}
```

### Test Coverage

- Minimum 80% line coverage for new code
- 100% coverage for critical business logic
- Use Testcontainers for integration tests

## Documentation

### Where to Document

| Type | Location |
|------|----------|
| API Documentation | Javadoc + SpringDoc |
| Architecture | `docs/architecture/` |
| Development Guide | `docs/development/` |
| Deployment Guide | `docs/deployment/` |
| README | Root `README.md` |

### Javadoc Standards

```java
/**
 * Short description (one sentence).
 *
 * <p>Longer description if needed, explaining the purpose,
 * behavior, and any important notes.
 *
 * <p>Example usage:
 * <pre>{@code
 * UserService service = new UserServiceImpl(repository);
 * Optional<User> user = service.findUserById(1L);
 * }</pre>
 *
 * @param id the unique identifier
 * @return the user if found
 * @throws IllegalArgumentException if id is negative
 * @see UserRepository
 */
```

## Getting Help

- **GitHub Discussions**: For questions and discussions
- **GitHub Issues**: For bug reports and feature requests
- **Email**: [team@easywing.io](mailto:team@easywing.io)

## Recognition

Contributors are recognized in:

- Project README
- Release notes
- GitHub contributors page

Thank you for contributing to EasyWing Platform! 🚀

---

*Last Updated: 2025*