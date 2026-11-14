import os
import re
from datetime import datetime, timezone
from urllib.parse import urlparse

from flask import Flask, flash, redirect, render_template, request, session, url_for
from flask_sqlalchemy import SQLAlchemy
from flask_wtf.csrf import CSRFProtect
from sqlalchemy import Index
from werkzeug.security import generate_password_hash, check_password_hash

app = Flask(__name__)
app.config["SECRET_KEY"] = os.environ.get("SECRET_KEY", "dev-only-change-this-before-deploying")
database_url = os.environ.get("DATABASE_URL", "sqlite:///gd_smart_assistant.db")
if database_url.startswith("postgres://"):
    database_url = database_url.replace("postgres://", "postgresql://", 1)
app.config.update(
    SQLALCHEMY_DATABASE_URI=database_url,
    SQLALCHEMY_TRACK_MODIFICATIONS=False,
    SESSION_COOKIE_HTTPONLY=True,
    SESSION_COOKIE_SAMESITE="Lax",
    SESSION_COOKIE_SECURE=os.environ.get("COOKIE_SECURE", "false").lower() == "true",
    PERMANENT_SESSION_LIFETIME=60 * 60 * 24 * 7,
)
db = SQLAlchemy(app)
csrf = CSRFProtect(app)


class Shop(db.Model):
    __tablename__ = "shops"
    id = db.Column(db.Integer, primary_key=True)
    name = db.Column(db.String(120), nullable=False)
    slug = db.Column(db.String(140), unique=True, nullable=False, index=True)
    created_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc), nullable=False)


class User(db.Model):
    __tablename__ = "users"
    id = db.Column(db.Integer, primary_key=True)
    shop_id = db.Column(db.Integer, db.ForeignKey("shops.id"), nullable=False, index=True)
    full_name = db.Column(db.String(120), nullable=False)
    email = db.Column(db.String(190), unique=True, nullable=False, index=True)
    password_hash = db.Column(db.String(255), nullable=False)
    role = db.Column(db.String(20), nullable=False, default="owner")
    created_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc), nullable=False)
    shop = db.relationship("Shop", backref="users")


class Product(db.Model):
    __tablename__ = "products"
    id = db.Column(db.Integer, primary_key=True)
    shop_id = db.Column(db.Integer, db.ForeignKey("shops.id"), nullable=False, index=True)
    name = db.Column(db.String(160), nullable=False)
    price = db.Column(db.Integer, nullable=False, default=0)
    stock = db.Column(db.Integer, nullable=False, default=0)
    __table_args__ = (Index("ix_products_shop_name", "shop_id", "name"),)


class Customer(db.Model):
    __tablename__ = "customers"
    id = db.Column(db.Integer, primary_key=True)
    shop_id = db.Column(db.Integer, db.ForeignKey("shops.id"), nullable=False, index=True)
    name = db.Column(db.String(160), nullable=False)
    phone = db.Column(db.String(40), nullable=True)


class Order(db.Model):
    __tablename__ = "orders"
    id = db.Column(db.Integer, primary_key=True)
    shop_id = db.Column(db.Integer, db.ForeignKey("shops.id"), nullable=False, index=True)
    order_number = db.Column(db.String(40), nullable=False)
    total = db.Column(db.Integer, nullable=False, default=0)
    status = db.Column(db.String(30), nullable=False, default="baru")
    created_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc), nullable=False)
    __table_args__ = (db.UniqueConstraint("shop_id", "order_number", name="uq_order_shop_number"),)


def current_user():
    user_id = session.get("user_id")
    if not user_id:
        return None
    return db.session.get(User, user_id)


def shop_slug(name):
    base = re.sub(r"[^a-z0-9]+", "-", name.lower()).strip("-")[:100] or "toko"
    candidate = base
    suffix = 2
    while Shop.query.filter_by(slug=candidate).first():
        candidate = f"{base}-{suffix}"
        suffix += 1
    return candidate


@app.route("/")
def home():
    if current_user():
        return redirect(url_for("dashboard"))
    return render_template("welcome.html")


@app.route("/register", methods=["GET", "POST"])
def register():
    if current_user():
        return redirect(url_for("dashboard"))
    if request.method == "POST":
        shop_name = request.form.get("shop_name", "").strip()
        full_name = request.form.get("full_name", "").strip()
        email = request.form.get("email", "").strip().lower()
        password = request.form.get("password", "")
        confirm = request.form.get("confirm_password", "")
        if not shop_name or not full_name or not email or not password:
            flash("Semua kolom wajib diisi.", "error")
        elif len(shop_name) > 120 or len(full_name) > 120:
            flash("Nama toko atau nama pemilik terlalu panjang.", "error")
        elif not re.fullmatch(r"[^@\s]+@[^@\s]+\.[^@\s]+", email) or len(email) > 190:
            flash("Format email tidak valid.", "error")
        elif len(password) < 10:
            flash("Kata sandi minimal 10 karakter.", "error")
        elif password != confirm:
            flash("Konfirmasi kata sandi tidak sama.", "error")
        elif User.query.filter_by(email=email).first():
            flash("Email sudah terdaftar. Silakan masuk.", "error")
        else:
            shop = Shop(name=shop_name, slug=shop_slug(shop_name))
            db.session.add(shop)
            db.session.flush()
            user = User(shop_id=shop.id, full_name=full_name, email=email,
                        password_hash=generate_password_hash(password), role="owner")
            db.session.add(user)
            db.session.commit()
            session.clear()
            session["user_id"] = user.id
            session.permanent = True
            flash("Akun toko berhasil dibuat. Selamat datang!", "success")
            return redirect(url_for("dashboard"))
    return render_template("auth.html", mode="register")


@app.route("/login", methods=["GET", "POST"])
def login():
    if current_user():
        return redirect(url_for("dashboard"))
    if request.method == "POST":
        email = request.form.get("email", "").strip().lower()
        password = request.form.get("password", "")
        user = User.query.filter_by(email=email).first()
        if user and check_password_hash(user.password_hash, password):
            session.clear()
            session["user_id"] = user.id
            session.permanent = True
            return redirect(url_for("dashboard"))
        flash("Email atau kata sandi tidak cocok.", "error")
    return render_template("auth.html", mode="login")


@app.route("/logout", methods=["POST"])
def logout():
    session.clear()
    flash("Kamu sudah keluar dari akun.", "success")
    return redirect(url_for("login"))


@app.route("/dashboard")
def dashboard():
    user = current_user()
    if not user:
        return redirect(url_for("login"))
    # Every tenant-owned query is scoped by the authenticated user's shop_id.
    shop_id = user.shop_id
    return render_template(
        "dashboard.html", user=user,
        product_count=Product.query.filter_by(shop_id=shop_id).count(),
        customer_count=Customer.query.filter_by(shop_id=shop_id).count(),
        order_count=Order.query.filter_by(shop_id=shop_id).count(),
    )


@app.get("/health")
def health():
    return {"status": "ok", "service": "GD SMART ASSISTANT"}


@app.errorhandler(404)
def not_found(_error):
    return render_template("message.html", title="Halaman tidak ditemukan",
                           message="Halaman yang kamu cari tidak tersedia."), 404


with app.app_context():
    db.create_all()


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "5000")), debug=False)
