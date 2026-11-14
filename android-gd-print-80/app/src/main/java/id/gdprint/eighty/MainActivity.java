package id.gdprint.eighty;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
import android.content.Intent;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.pdf.PdfRenderer;
import android.os.Build;
import android.os.Bundle;
import android.graphics.Typeface;
import android.view.View;
import android.widget.*;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_BT = 77;
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter adapter;
    private BluetoothSocket socket;
    private Spinner printerSpinner;
    private final ArrayList<BluetoothDevice> devices = new ArrayList<>();
    private TextView status;
    private EditText shop, address, orderNo, item, qty, price, paid;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        adapter = BluetoothAdapter.getDefaultAdapter();
        buildUi();
        requestBtPermission();
        handleIncomingShare(getIntent());
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(28, 28, 28, 32);
        root.setBackgroundColor(0xFFF4F7FB);
        scroll.addView(root);

        TextView title = new TextView(this);
        title.setText("GD PRINT 80");
        title.setTextSize(27);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF123B68);
        root.addView(title);

        TextView subtitle = new TextView(this);
        subtitle.setText("Bluetooth Thermal Printer • ESC/POS • 80 mm");
        subtitle.setTextSize(13);
        root.addView(subtitle);

        status = new TextView(this);
        status.setText("Status: Belum tersambung");
        status.setTextSize(14);
        status.setPadding(0, 22, 0, 14);
        root.addView(status);

        printerSpinner = new Spinner(this);
        root.addView(printerSpinner);
        addButton(root, "Muat printer Bluetooth yang sudah dipasangkan", v -> loadPaired());
        addButton(root, "Sambungkan printer terpilih", v -> connectPrinter());

        shop = field(root, "Nama toko", "GD COLLECTION");
        address = field(root, "Alamat toko", "Kebumen");
        orderNo = field(root, "Nomor pesanan", "ORD-0001");
        item = field(root, "Nama barang", "Songkok hitam");
        qty = field(root, "Jumlah", "1");
        price = field(root, "Harga satuan (angka)", "25000");
        paid = field(root, "Uang diterima (angka)", "25000");

        addButton(root, "TES CETAK PRINTER", v -> printTest());
        addButton(root, "CETAK NOTA 80 mm", v -> printReceipt());
        addButton(root, "CETAK ULANG FILE TERAKHIR", v -> printSharedFile());
        TextView shareHint = new TextView(this);
        shareHint.setText("\\nUntuk cetak resi: buka PDF/gambar resi dari Shopee atau TikTok, pilih Bagikan > GD PRINT 80. File PDF dan gambar akan dicetak sebagai bitmap hitam-putih.");
        shareHint.setTextSize(12);
        shareHint.setTextColor(0xFF526174);
        root.addView(shareHint);

        TextView note = new TextView(this);
        note.setText("\nCatatan: pasangkan printer lewat Pengaturan Bluetooth Android terlebih dahulu. Aplikasi ini memakai Bluetooth Classic SPP (ESC/POS). Dukungan RPP02N perlu diuji langsung; beberapa varian printer memakai mode/kode karakter berbeda.");
        note.setTextSize(12);
        note.setTextColor(0xFF526174);
        root.addView(note);
        setContentView(scroll);
    }

    private EditText field(LinearLayout root, String label, String value) {
        TextView t = new TextView(this); t.setText(label); t.setTextSize(13); t.setPadding(0, 12, 0, 3); root.addView(t);
        EditText e = new EditText(this); e.setSingleLine(true); e.setText(value); e.setTextSize(15); root.addView(e);
        return e;
    }

    private void addButton(LinearLayout root, String text, View.OnClickListener click) {
        Button b = new Button(this); b.setText(text); b.setOnClickListener(click); root.addView(b);
    }

    private void requestBtPermission() {
        if (Build.VERSION.SDK_INT >= 31 &&
            (checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
             checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)) {
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN}, REQ_BT);
        } else loadPaired();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQ_BT) {
            boolean ok = true;
            for (int r : results) if (r != PackageManager.PERMISSION_GRANTED) ok = false;
            if (ok) loadPaired(); else setStatus("Izin Bluetooth belum diberikan.");
        }
    }

    private boolean hasBtPermission() {
        return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void loadPaired() {
        if (adapter == null) { setStatus("HP ini tidak mendukung Bluetooth."); return; }
        if (!hasBtPermission()) { requestBtPermission(); return; }
        try {
            Set<BluetoothDevice> paired = adapter.getBondedDevices();
            devices.clear();
            ArrayList<String> names = new ArrayList<>();
            for (BluetoothDevice d : paired) {
                devices.add(d);
                names.add(d.getName() + "  •  " + d.getAddress());
            }
            if (names.isEmpty()) names.add("Belum ada printer dipasangkan");
            printerSpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, names));
            setStatus("Ditemukan " + devices.size() + " perangkat yang sudah dipasangkan.");
        } catch (SecurityException e) { setStatus("Izin Bluetooth diperlukan."); }
    }

    private void connectPrinter() {
        if (devices.isEmpty()) { setStatus("Pasangkan printer dari Pengaturan Bluetooth, lalu muat ulang."); return; }
        int index = printerSpinner.getSelectedItemPosition();
        if (index < 0 || index >= devices.size()) return;
        BluetoothDevice device = devices.get(index);
        setStatus("Menghubungkan ke " + device.getName() + "...");
        worker.execute(() -> {
            try {
                if (socket != null) try { socket.close(); } catch (Exception ignored) {}
                socket = device.createRfcommSocketToServiceRecord(SPP);
                if (adapter != null && adapter.isDiscovering()) adapter.cancelDiscovery();
                socket.connect();
                runOnUiThread(() -> setStatus("Tersambung: " + device.getName()));
            } catch (Exception e) {
                socket = null;
                runOnUiThread(() -> setStatus("Gagal tersambung: " + e.getMessage()));
            }
        });
    }

    private Uri sharedUri;\n    private String sharedMime;\n\n    @Override protected void onNewIntent(Intent intent) {\n        super.onNewIntent(intent);\n        setIntent(intent);\n        handleIncomingShare(intent);\n    }\n\n    private void handleIncomingShare(Intent intent) {\n        if (intent == null) return;\n        Uri uri = null;\n        String action = intent.getAction();\n        if (Intent.ACTION_SEND.equals(action)) {\n            if (Build.VERSION.SDK_INT >= 33) uri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri.class);\n            else { Object value = intent.getParcelableExtra(Intent.EXTRA_STREAM); if (value instanceof Uri) uri = (Uri) value; }\n        } else if (Intent.ACTION_VIEW.equals(action)) { uri = intent.getData(); }\n        if (uri != null) {\n            sharedUri = uri; sharedMime = intent.getType();\n            setStatus("File diterima. Sambungkan printer, lalu tekan CETAK ULANG FILE TERAKHIR.");\n        }\n    }\n\n    private void printSharedFile() {\n        final Uri uri = sharedUri;\n        if (uri == null) { setStatus("Belum ada file. Bagikan PDF/JPG/PNG ke GD PRINT 80 terlebih dahulu."); return; }\n        setStatus("Menyiapkan file untuk dicetak...");\n        worker.execute(() -> {\n            try {\n                Bitmap source;\n                String type = sharedMime == null ? getContentResolver().getType(uri) : sharedMime;\n                if (type != null && type.toLowerCase().contains("pdf")) {\n                    try (ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(uri, "r"); PdfRenderer renderer = new PdfRenderer(fd)) {\n                        if (renderer.getPageCount() < 1) throw new Exception("PDF kosong.");\n                        PdfRenderer.Page page = renderer.openPage(0);\n                        float scale = Math.min(3.0f, 576f / (float) page.getWidth());\n                        source = Bitmap.createBitmap(Math.max(1, (int)(page.getWidth()*scale)), Math.max(1, (int)(page.getHeight()*scale)), Bitmap.Config.ARGB_8888);\n                        source.eraseColor(Color.WHITE); page.render(source, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT); page.close();\n                    }\n                } else {\n                    try (java.io.InputStream in = getContentResolver().openInputStream(uri)) { source = BitmapFactory.decodeStream(in); }\n                    if (source == null) throw new Exception("Format file tidak terbaca. Gunakan PDF, JPG, atau PNG.");\n                }\n                Bitmap scaled;\n                if (source.getWidth() > 576) { int h = Math.max(1, Math.round(source.getHeight() * (576f/source.getWidth()))); scaled = Bitmap.createScaledBitmap(source, 576, h, true); }\n                else scaled = source;\n                byte[] raster = bitmapToEscPos(scaled);\n                OutputStream out;\n                if (socket == null || !socket.isConnected()) throw new Exception("Printer belum tersambung.");\n                out = socket.getOutputStream(); out.write(new byte[]{0x1B,0x40}); out.write(raster); out.write(new byte[]{0x1B,0x64,0x04}); out.flush();\n                runOnUiThread(() -> setStatus("File berhasil dikirim ke printer. Periksa hasil cetak."));\n                if (scaled != source) scaled.recycle(); source.recycle();\n            } catch (Exception e) { runOnUiThread(() -> setStatus("Cetak file gagal: " + e.getMessage())); }\n        });\n    }\n\n    private byte[] bitmapToEscPos(Bitmap bitmap) {\n        int width = bitmap.getWidth(), height = bitmap.getHeight();\n        int widthBytes = (width + 7) / 8;\n        byte[] out = new byte[8 + widthBytes * height];\n        out[0]=0x1D; out[1]=0x76; out[2]=0x30; out[3]=0;\n        out[4]=(byte)(widthBytes & 0xFF); out[5]=(byte)((widthBytes >> 8) & 0xFF);\n        out[6]=(byte)(height & 0xFF); out[7]=(byte)((height >> 8) & 0xFF);\n        int k=8;\n        for (int y=0;y<height;y++) {\n            for (int xb=0;xb<widthBytes;xb++) {\n                int bits=0;\n                for (int bit=0;bit<8;bit++) { int x=xb*8+bit; if (x<width) { int c=bitmap.getPixel(x,y); int gray=(Color.red(c)*299+Color.green(c)*587+Color.blue(c)*114)/1000; if (gray<180) bits |= (0x80 >> bit); } }\n                out[k++]=(byte)bits;\n            }\n        }\n        return out;\n    }\n\n    private void printTest() {
        byte[] data = new byte[]{0x1B, 0x40, 0x1B, 0x61, 0x01};
        data = join(data, textBytes("GD PRINT 80\nTES CETAK BERHASIL\n"));
        data = join(data, new byte[]{0x1B, 0x61, 0x00});
        data = join(data, textBytes("--------------------------------\nBluetooth ESC/POS\nUkuran kertas: 80 mm\n\n\n"));
        data = join(data, new byte[]{0x1D, 0x56, 0x00});
        send(data);
    }

    private void printReceipt() {
        try {
            String name = shop.getText().toString();
            String addr = address.getText().toString();
            String no = orderNo.getText().toString();
            String product = item.getText().toString();
            int q = Math.max(1, Integer.parseInt(qty.getText().toString()));
            long p = Long.parseLong(price.getText().toString());
            long pay = Long.parseLong(paid.getText().toString());
            long total = q * p;
            StringBuilder s = new StringBuilder();
            s.append(center(name)).append("\n").append(center(addr)).append("\n");
            s.append("--------------------------------\n");
            s.append("No: ").append(no).append("\n");
            s.append(product).append("\n");
            s.append(q).append(" x ").append(rupiah(p)).append(" = ").append(rupiah(total)).append("\n");
            s.append("--------------------------------\n");
            s.append("TOTAL       ").append(rupiah(total)).append("\n");
            s.append("DIBAYAR     ").append(rupiah(pay)).append("\n");
            s.append("KEMBALI     ").append(rupiah(pay-total)).append("\n");
            s.append("\nTerima kasih!\n");
            byte[] data = new byte[]{0x1B, 0x40};
            data = join(data, textBytes(s.toString()));
            data = join(data, new byte[]{0x1B, 0x64, 0x04});
            send(data);
        } catch (Exception e) { setStatus("Periksa jumlah dan nominal angka."); }
    }

    private String center(String s) { return s == null ? "" : s; }
    private String rupiah(long n) { return String.format(java.util.Locale.US, "%,d", n).replace(',', '.'); }
    private byte[] textBytes(String s) { return s.getBytes(Charset.forName("CP437")); }
    private byte[] join(byte[] a, byte[] b) {
        byte[] c = new byte[a.length + b.length];
        System.arraycopy(a,0,c,0,a.length); System.arraycopy(b,0,c,a.length,b.length); return c;
    }

    private void send(byte[] data) {
        worker.execute(() -> {
            try {
                if (socket == null || !socket.isConnected()) throw new Exception("Printer belum tersambung.");
                OutputStream out = socket.getOutputStream();
                out.write(data); out.flush();
                runOnUiThread(() -> setStatus("Data cetak dikirim ke printer."));
            } catch (Exception e) { runOnUiThread(() -> setStatus("Cetak gagal: " + e.getMessage())); }
        });
    }

    private void setStatus(String s) { if (status != null) status.setText("Status: " + s); }

    @Override protected void onDestroy() {
        super.onDestroy();
        worker.execute(() -> { try { if (socket != null) socket.close(); } catch (Exception ignored) {} });
        worker.shutdown();
    }
}
