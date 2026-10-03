# 养蜂场 · 蜂箱台账与采蜜批次

养蜂场业务管理：蜂箱台账、蜂箱巡检（月历视图，按蜂箱+日期唯一，含健康判定与复检）、采蜜批次（质检入库流转）、客户管理。

## 技术栈
- 后端：Spring Boot 3.3 + Spring Data JPA + MySQL
- 前端：Vue 3 + Element Plus + Vite

## 启停
```bash
sh start.sh     # 启动（docker compose 拉起 mysql + backend + frontend）
sh stop.sh      # 停止
```
