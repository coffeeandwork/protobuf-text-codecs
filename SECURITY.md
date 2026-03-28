# Security Policy

## Supported Versions

| Version | Supported |
|---------|-----------|
| 0.2.x   | Yes       |
| < 0.2   | No        |

## Reporting a Vulnerability

If you discover a security vulnerability in protobuf-text-codecs, please report it responsibly.

**Do not open a public GitHub issue for security vulnerabilities.**

Instead, please email **security@protocgen.dev** with:

- A description of the vulnerability
- Steps to reproduce
- The affected version(s)
- Any potential impact assessment

You should receive an acknowledgment within 48 hours. We will work with you to understand the issue and coordinate a fix before any public disclosure.

## Scope

This project processes Protocol Buffer descriptors (via `protoc`) and generates source code. The primary attack surface is:

- **Malicious `.proto` files** that could cause the plugin to generate unsafe code or consume excessive resources
- **Generated code** that could introduce vulnerabilities in downstream projects (injection, buffer overflow, etc.)

The plugin itself runs as a local `protoc` plugin -- it reads from stdin and writes to stdout, with no network access, no filesystem writes beyond stdout, and no persistent state.

## Prior Security Work

Nine vulnerabilities (VULN-001 through VULN-009) were identified and fixed during initial development. See `docs/SECURITY_ASSESSMENT.md` for the full assessment, including STRIDE analysis and CWE references.
