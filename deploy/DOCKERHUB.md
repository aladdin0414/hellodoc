# HelloDoc

HelloDoc is a self-hosted knowledge base and document collaboration system with web and mobile interfaces and MCP support.

## Docker Compose quick start

Use the maintained, complete instructions in the [GitHub README](https://github.com/aladdin0414/hellodoc#run-with-docker) ([中文](https://github.com/aladdin0414/hellodoc/blob/main/README_ZH.md#使用-docker-镜像)). The Compose file starts both HelloDoc and PostgreSQL, stores uploads and database data on the host, and requires your own database password, administrator password, and JWT secret.

After startup, open `http://localhost:8080` and sign in as `admin` with your configured `ADMIN_PASSWORD`. The password is used only when the administrator is first created. AI features require model configuration.

Back up the database and uploads before upgrading. If an earlier container stored uploads in `/app/uploads`, back them up and migrate them before changing the path or recreating the container. See the README for upgrade and recovery instructions.

Source, configuration, and issue tracker: [github.com/aladdin0414/hellodoc](https://github.com/aladdin0414/hellodoc).
