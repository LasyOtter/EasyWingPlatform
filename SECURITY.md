# Security Policy

## Supported Versions

The following versions of EasyWing Platform are currently being supported with security updates:

| Version | Supported          | Status |
| ------- | ------------------ | ------ |
| 1.0.x   | :white_check_mark: | Active Development |
| < 1.0   | :x:                | Not Supported |

## Security Features

EasyWing Platform implements the following security features:

### Authentication & Authorization
- **OAuth 2.1 / OIDC**: Modern authentication standard with JWT and opaque token support
- **Spring Security 6.3**: Latest security framework with enhanced protection
- **Fine-grained Authorization**: Method-level security with SpEL expressions

### Data Protection
- **Secure Configuration**: Sensitive data stored in external secret management systems
- **Encryption Support**: Built-in support for encryption at rest and in transit
- **Input Validation**: Comprehensive input validation with Bean Validation

### Network Security
- **CORS Configuration**: Configurable Cross-Origin Resource Sharing
- **CSRF Protection**: Built-in CSRF protection for web applications
- **Security Headers**: Auto-configured security headers (HSTS, X-Frame-Options, etc.)

### Dependency Security
- **Dependency Scanning**: Automated vulnerability scanning via GitHub Dependabot
- **SBOM Generation**: Software Bill of Materials for supply chain security
- **Regular Updates**: Dependencies updated regularly to patch security vulnerabilities

## Reporting a Vulnerability

We take security seriously. If you discover a security vulnerability, please follow these steps:

### How to Report

1. **Do NOT** create a public GitHub issue
2. Email security reports to: [security@easywing.io](mailto:security@easywing.io)
3. Include the following information:
   - Description of the vulnerability
   - Steps to reproduce
   - Potential impact
   - Suggested fix (if available)
   - Your contact information for follow-up

### What to Expect

| Stage | Timeline |
|-------|----------|
| Acknowledgment | Within 48 hours |
| Initial Assessment | Within 5 business days |
| Status Update | Every 7 days until resolved |
| Fix Development | Depends on severity |
| Security Advisory | After fix is released |

### Severity Classification

| Severity | CVSS Score | Response Time |
|----------|------------|---------------|
| Critical | 9.0 - 10.0 | 24 hours |
| High | 7.0 - 8.9 | 48 hours |
| Medium | 4.0 - 6.9 | 5 business days |
| Low | 0.1 - 3.9 | 10 business days |

### Responsible Disclosure

We follow responsible disclosure practices:

1. **Fix Priority**: Critical and High severity issues are prioritized
2. **Coordinated Release**: Security fixes are released with security advisories
3. **Credit**: Security researchers are credited in advisories (unless anonymous)
4. **No Legal Action**: We do not pursue legal action against researchers who follow this policy

## Security Best Practices

### For Developers

```yaml
# Use environment variables for secrets
spring:
  datasource:
    password: ${DB_PASSWORD}
    
# Enable security headers
server:
  forward-headers-strategy: native
    
# Use HTTPS in production
server:
  ssl:
    enabled: true
    key-store: ${SSL_KEYSTORE_PATH}
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
```

### For Operations

1. **Keep Updated**: Always use the latest supported version
2. **Enable Security Features**: Review and enable all security configurations
3. **Monitor Logs**: Enable security audit logging
4. **Regular Audits**: Conduct regular security audits of your deployment
5. **Secret Management**: Use proper secret management (Vault, AWS Secrets Manager, etc.)

## Security Configuration Checklist

- [ ] OAuth2/OIDC properly configured
- [ ] CORS settings reviewed for production
- [ ] CSRF protection enabled for web applications
- [ ] Security headers configured
- [ ] Database credentials secured
- [ ] API keys stored securely
- [ ] TLS/SSL enabled for all endpoints
- [ ] Rate limiting configured
- [ ] Audit logging enabled
- [ ] Dependency vulnerabilities scanned

## Contact

For any security-related questions:

- **Security Email**: [security@easywing.io](mailto:security@easywing.io)
- **Security Team**: [@LasyOtter/security-team](https://github.com/orgs/LasyOtter/teams/security-team)

## Security Advisories

All security advisories are published on our [GitHub Security Advisories](https://github.com/LasyOtter/EasyWingPlatform/security/advisories) page.

---

*Last Updated: 2025*