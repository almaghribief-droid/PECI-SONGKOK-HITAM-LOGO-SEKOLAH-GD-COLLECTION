import os
import re
import uuid
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
    status = db.Column(db.String(30), nullable=False, default="selesai")
    customer_id = db.Column(db.Integer, db.ForeignKey("customers.id"), nullable=True, index=True)
    created_at = db.Column(db.DateTime, default=lambda: datetime.now(timezone.utc), nullable=False)
    __table_args__ = (db.UniqueConstraint("shop_id", "order_number", name="uq_order_shop_number"),)

    
class OrderItem(db.Model):
    __tablename__ = "order_items"
    id = db.Column(db.Integer, primary_key=True)
    order_id = db.Column(db.Integer, db.ForeignKey("orders.id"), nullable=False, index=True)
    product_id = db.Column(db.Integer, nullable=False)
    product_name = db.Column(db.String(160), nullable=False)
    quantity = db.Column(db.Integer, nullable=False)
    unit_price = db.Column(db.Integer, nullable=False)
    subtotal = db.Column(db.Integer, nullable=False)
    order = db.relationship("Order", backref=db.backref("items", cascade="all, delete-orphan"))


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
        sales_total=db.session.query(db.func.coalesce(db.func.sum(Order.total), 0)).filter(Order.shop_id == shop_id).scalar(),
    )


@app.route("/products", methods=["GET", "POST"])
def products():
    user = current_user()
    if not user:
        return redirect(url_for("login"))

    if request.method == "POST":
        action = request.form.get("action", "add")
        product_id = request.form.get("product_id", type=int)

        if action == "delete":
            product = Product.query.filter_by(id=product_id, shop_id=user.shop_id).first()
            if product:
                db.session.delete(product)
                db.session.commit()
                flash("Produk berhasil dihapus.", "success")
            else:
                flash("Produk tidak ditemukan.", "error")
            return redirect(url_for("products"))

        name = request.form.get("name", "").strip()
        price = request.form.get("price", type=int)
        stock = request.form.get("stock", type=int)

        if not name or len(name) > 160:
            flash("Nama produk wajib diisi (maksimal 160 karakter).", "error")
        elif price is None or price < 0:
            flash("Harga harus berupa angka nol atau lebih.", "error")
        elif stock is None or stock < 0:
            flash("Stok harus berupa angka nol atau lebih.", "error")
        elif action == "edit":
            product = Product.query.filter_by(id=product_id, shop_id=user.shop_id).first()
            if not product:
                flash("Produk tidak ditemukan.", "error")
            else:
                product.name = name
                product.price = price
                product.stock = stock
                db.session.commit()
                flash("Produk berhasil diperbarui.", "success")
                return redirect(url_for("products"))
        else:
            product = Product(shop_id=user.shop_id, name=name, price=price, stock=stock)
            db.session.add(product)
            db.session.commit()
            flash("Produk berhasil ditambahkan.", "success")
            return redirect(url_for("products"))

    product_list = Product.query.filter_by(shop_id=user.shop_id).order_by(Product.name.asc()).all()
    return render_template("products.html", user=user, products=product_list)


@app.get("/products/<int:product_id>/edit")
def edit_product(product_id):
    user = current_user()
    if not user:
        return redirect(url_for("login"))
    product = Product.query.filter_by(id=product_id, shop_id=user.shop_id).first_or_404()
    return render_template("products.html", user=user, products=Product.query.filter_by(shop_id=user.shop_id).order_by(Product.name.asc()).all(), editing=product)



@app.route("/sales", methods=["GET", "POST"])
def sales():
    user = current_user()
    if not user:
        return redirect(url_for("login"))

    if request.method == "POST":
        customer_name = request.form.get("customer_name", "").strip()
        customer_phone = request.form.get("customer_phone", "").strip()
        product_ids = request.form.getlist("product_id")
        quantities = request.form.getlist("quantity")
        requested = {}
        try:
            for raw_id, raw_qty in zip(product_ids, quantities):
                if not raw_id:
                    continue
                product_id, qty = int(raw_id), int(raw_qty)
                if qty < 1:
                    raise ValueError("Jumlah setiap produk minimal 1.")
                requested[product_id] = requested.get(product_id, 0) + qty
            if not requested:
                raise ValueError("Pilih minimal satu produk untuk transaksi.")
            if customer_phone and not customer_name:
                raise ValueError("Isi nama pelanggan jika nomor telepon dicantumkan.")
            products_by_id = {
                p.id: p for p in Product.query.filter(
                    Product.shop_id == user.shop_id, Product.id.in_(requested.keys())
                ).all()
            }
            if len(products_by_id) != len(requested):
                raise ValueError("Ada produk yang tidak ditemukan di toko ini.")
            for product_id, qty in requested.items():
                if products_by_id[product_id].stock < qty:
                    raise ValueError(f"Stok {products_by_id[product_id].name} tidak mencukupi.")
            customer = None
            if customer_name:
                if customer_phone:
                    customer = Customer.query.filter_by(
                        shop_id=user.shop_id, phone=customer_phone
                    ).first()
                if customer:
                    customer.name = customer_name
                else:
                    customer = Customer(shop_id=user.shop_id, name=customer_name,
                                        phone=customer_phone or None)
                    db.session.add(customer)
                    db.session.flush()
            order = Order(
                shop_id=user.shop_id,
                order_number="TRX-" + datetime.now().strftime("%y%m%d%H%M%S") + "-" + uuid.uuid4().hex[:4].upper(),
                total=0, status="selesai",
                customer_id=customer.id if customer else None
            )
            db.session.add(order)
            db.session.flush()
            total = 0
            for product_id, qty in requested.items():
                product = products_by_id[product_id]
                line_total = product.price * qty
                total += line_total
                product.stock -= qty
                db.session.add(OrderItem(
                    order_id=order.id, product_id=product.id,
                    product_name=product.name, quantity=qty,
                    unit_price=product.price, subtotal=line_total
                ))
            order.total = total
            db.session.commit()
            flash(f"Transaksi {order.order_number} berhasil disimpan. Total {total:,}.".replace(",", "."), "success")
            return redirect(url_for("sales"))
        except (ValueError, TypeError) as exc:
            db.session.rollback()
            flash(str(exc), "error")
        except Exception:
            db.session.rollback()
            app.logger.exception("Gagal menyimpan transaksi")
            flash("Transaksi gagal disimpan. Periksa data lalu coba lagi.", "error")

    product_list = Product.query.filter_by(shop_id=user.shop_id).order_by(Product.name.asc()).all()
    order_list = Order.query.filter_by(shop_id=user.shop_id).order_by(Order.created_at.desc()).limit(50).all()
    return render_template("sales.html", user=user, products=product_list, orders=order_list)


@app.get("/sales/<int:order_id>")
def sale_detail(order_id):
    user = current_user()
    if not user:
        return redirect(url_for("login"))
    order = Order.query.filter_by(id=order_id, shop_id=user.shop_id).first_or_404()
    customer = db.session.get(Customer, order.customer_id) if order.customer_id else None
    return render_template("sale_detail.html", user=user, order=order, customer=customer)


@app.route("/assistant", methods=["GET", "POST"])
def assistant():
    user = current_user()
    if not user:
        return redirect(url_for("login"))
    shop_id = user.shop_id
    sales_total = db.session.query(db.func.coalesce(db.func.sum(Order.total), 0)).filter(Order.shop_id == shop_id).scalar()
    order_count = Order.query.filter_by(shop_id=shop_id).count()
    low_stock = Product.query.filter(Product.shop_id == shop_id, Product.stock <= 5).order_by(Product.stock.asc()).limit(10).all()
    prompt = ""
    answer = f"Halo, {user.full_name}! Saya GD AI. Saya bisa membantu membaca omzet, jumlah transaksi, dan stok toko."
    if request.method == "POST":
        prompt = request.form.get("prompt", "").strip()[:500]
        question = prompt.lower()
        if not prompt:
            answer = "Silakan tulis pertanyaan terlebih dahulu."
        elif any(word in question for word in ("omzet", "penjualan", "pendapatan")):
            answer = (f"Total penjualan yang tercatat adalah Rp {sales_total:,.0f}. Ada {order_count} transaksi. Angka ini omzet, belum dikurangi modal dan biaya.").replace(",", ".")
        elif any(word in question for word in ("transaksi", "pesanan", "order")):
            answer = f"Ada {order_count} transaksi yang tercatat di toko Anda."
        elif any(word in question for word in ("stok", "persediaan", "menipis", "habis")):
            if low_stock:
                answer = "Produk dengan stok 5 pcs atau kurang: " + "; ".join(f"{p.name}: {p.stock} pcs" for p in low_stock) + ". Silakan periksa kebutuhan restok."
            else:
                answer = "Tidak ada produk dengan stok 5 pcs atau kurang saat ini."
        else:
            answer = "Saya belum memahami pertanyaan itu. Coba tanyakan omzet toko, jumlah transaksi, atau stok menipis. Kemampuan AI percakapan penuh akan diaktifkan setelah penyedia AI dan API key dikonfigurasi."
    return render_template("assistant.html", user=user, prompt=prompt, answer=answer, sales_total=sales_total, order_count=order_count, low_stock=low_stock)

@app.get("/health")
def health():
    return {"status": "ok", "service": "GD SMART ASSISTANT"}


@app.errorhandler(404)
def not_found(_error):
    return render_template("message.html", title="Halaman tidak ditemukan",
                           message="Halaman yang kamu cari tidak tersedia."), 404


with app.app_context():
    db.create_all()
    # Add customer link for databases created by earlier versions.
    from sqlalchemy import inspect, text
    if "customer_id" not in {col["name"] for col in inspect(db.engine).get_columns("orders")}:
        with db.engine.begin() as connection:
            connection.execute(text("ALTER TABLE orders ADD COLUMN customer_id INTEGER REFERENCES customers(id)"))


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=int(os.environ.get("PORT", "5000")), debug=False)
