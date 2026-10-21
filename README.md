# Vendora — full stack

```
vendora-fullstack/
├─ backend/        Spring Boot 3 (Java 17) REST API + MySQL  — also serves the website (static/index.html)
├─ javafx-admin/   JavaFX desktop admin app (talks to the same API)
└─ docker-compose.yml   MySQL + backend, one command
```

JavaFX is a *desktop* UI toolkit, so it cannot host a live website.
Live site = Spring Boot (Java) + MySQL. JavaFX is used for the desktop **admin** client.

## 1. Run locally (Docker)
```
docker compose up --build
```
Open http://localhost:8080  → choose **User** or **Admin**.
First time: Admin → it asks you to create the one and only admin account.

## 1b. Run locally (without Docker)
1. Install JDK 17, Maven, MySQL 8 (the DB `vendora` is auto-created).
2. `cd backend && mvn spring-boot:run`
   (env vars if needed: DB_URL, DB_USER, DB_PASS, JWT_SECRET)

## 2. JavaFX admin app
```
cd javafx-admin
mvn javafx:run
```
Enter the server URL (http://localhost:8080 or your live domain) and admin login.
Tabs: Logins, Uploads (preview + Delete), Users. "First-time setup" creates the admin if none exists.

## 3. Go live
- Use any host that runs Docker/Java: a VPS (DigitalOcean, AWS Lightsail, Hetzner), Railway, Render, etc.
- Set env vars: `JWT_SECRET` (long random, 32+ chars), `DB_PASS`, and DB_URL/DB_USER if using a managed MySQL.
- Put it behind HTTPS (Nginx/Caddy + Let's Encrypt, or the platform's built-in HTTPS). Passwords travel to the server, so HTTPS is a must.
- Create the admin account immediately after first deploy (the first person to run setup becomes admin).
- Back up the MySQL volume regularly.

## API summary
| Who | Endpoint |
|---|---|
| public | POST /api/auth/register, /api/auth/login, GET /api/admin/status, POST /api/admin/setup (once), /api/admin/login |
| user | GET /api/auth/me, GET/POST /api/listings, DELETE /api/listings/{id} (own only) |
| admin | GET /api/admin/users, /logins, /listings, DELETE /api/admin/listings/{id} |

Security: passwords BCrypt-hashed, JWT (12h), 5 wrong attempts = 30s lock, only one admin row can exist.
Not included yet: "Saved" list is still stored per device in the browser; images are stored in MySQL as data-URLs (fine for starting, move to S3/Cloudinary later).
