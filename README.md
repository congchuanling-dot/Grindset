# Grindset

全栈项目：后端 Spring Boot，前端 React (Vite)。

## 目录结构

```
.
├── backend/     Spring Boot 后端 (Java 21)
├── frontend/    React 前端 (Vite)
└── docs/        项目文档
```

## 环境要求

- JDK 21+
- Maven 3.9+
- Node.js 20+

## 启动

### 后端

```bash
cd backend
mvn spring-boot:run
```

默认运行在 http://localhost:8080 ，健康检查接口：`GET /api/health`。

### 前端

```bash
cd frontend
npm install
npm run dev
```

默认运行在 http://localhost:5173 。
