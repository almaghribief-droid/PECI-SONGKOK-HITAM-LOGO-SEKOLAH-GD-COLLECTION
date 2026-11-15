# GD SMART ASSISTANT
**Your Smart Business Partner**

## Current build
This repository contains:
- `index.html`: public static landing page for GitHub Pages.
- `app.py`: Flask backend for shop registration, login, logout, tenant-scoped dashboard, product inventory, and multi-item sales transactions.
- `templates/sales.html` and `templates/sale_detail.html`: sales entry, transaction history, and receipt detail.
- `templates/` and `static/`: backend-served pages and styles.
- `render.yaml`: deployment blueprint for a Render web service and PostgreSQL database.

## Multi-shop foundation
Each shop is stored as a separate tenant. Each user is linked to one `shop_id`. Dashboard counts are queried with the authenticated user's `shop_id`. Passwords are stored using Werkzeug password hashing. Forms use CSRF protection.

## Deploy the application backend
GitHub Pages only serves static files; it cannot run the Python backend or PostgreSQL. To activate registration and login:
1. Sign in to Render and create a new Blueprint from this repository.
2. Review the services in `render.yaml` and approve creation of the web service and database.
3. Wait for the deployment to finish, then open the Render service URL.
4. Test shop registration, logout, and login on that service URL.

The `index.html` GitHub Pages page is a static preview and does not process accounts. The working account routes are served by Flask on the deployed backend.

## Local development
Use Python 3.11 or newer:
```bash
python -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
export SECRET_KEY="replace-with-a-long-random-secret"
python app.py
```
Open `http://localhost:5000`.

## Important production work still required
This is an initial account and tenant-isolation foundation, not a finished commercial SaaS. Before taking real customer data, add database migrations, automated security and tenant-isolation tests, email verification, password reset, rate limiting, backups, audit logging, billing/subscriptions, and operational monitoring. Customer management is currently captured during sales entry; standalone customer CRUD, order cancellation/refunds and stock restoration, payment methods, reports/exports, billing/subscriptions, and the AI integration are not implemented yet. Sales transactions reduce stock and preserve item name/price snapshots. Never use the development SECRET_KEY in production.
