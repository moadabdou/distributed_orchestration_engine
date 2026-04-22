# Project Setup

Welcome to Fern-OS! This guide will help you get the distributed orchestration engine up and running on your local machine or server.

## 🚀 Quick Start (Docker)

The fastest way to get started is using Docker Compose.

1. **Clone the repository**:
   ```bash
   git clone <repository-url>
   cd distributed_orchestration_engine
   ```

2. **Configure Environment**:
   ```bash
   cp .env.example .env
   # Edit .env to set your JWT_SECRET
   ```

3. **Launch the Stack**:
   ```bash
   docker compose up -d
   ```

4. **Access the Dashboard**:
   Open `http://localhost` in your browser.

---

## Detailed Setup Guides

Follow these guides for in-depth configuration:

- [**Docker Deployment**](docker.md): Scaling workers, networking, and service details.
- [**Environment Variables**](environment-variables.md): Full list of configuration flags.
- [**Manager & Worker Config**](manager-config.md): Fine-tuning Spring Boot and scheduler behavior.
- [**Python SDK Setup**](python-sdk.md): Installing the SDK and running your first workflow.

## Need Help?

If you encounter issues during setup, please check the [Docker Logs](docker.md#quick-start) or open an issue on the repository.
