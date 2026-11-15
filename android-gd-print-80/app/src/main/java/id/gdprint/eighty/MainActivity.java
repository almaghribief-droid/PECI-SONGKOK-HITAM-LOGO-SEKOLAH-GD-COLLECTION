package id.gdprint.eighty;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.pm.PackageManager;
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
    private Spinner printerSpinner, densitySpinner;
    private final ArrayList<BluetoothDevice> devices = new ArrayList<>();
    private TextView status;
    private EditText shop, address, orderNo, item, qty, price, paid;
    private final ExecutorService worker = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        adapter = BluetoothAdapter.getDefaultAdapter();
        buildUi();
        requestBtPermission();
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
        TextView densityLabel = new TextView(this);
        densityLabel.setText("Ketebalan cetak (Print Density 1–5)");
        densityLabel.setTextSize(13);
        densityLabel.setPadding(0, 14, 0, 3);
        root.addView(densityLabel);
        densitySpinner = new Spinner(this);
        String[] densityOptions = {"1 - Sangat tipis", "2 - Tipis", "3 - Normal", "4 - Pekat", "5 - Sangat pekat"};
        densitySpinner.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, densityOptions));
        densitySpinner.setSelection(2);
        root.addView(densitySpinner);


        shop = field(root, "Nama toko", "GD COLLECTION");
        address = field(root, "Alamat toko", "Kebumen");
        orderNo = field(root, "Nomor pesanan", "ORD-0001");
        item = field(root, "Nama barang", "Songkok hitam");
        qty = field(root, "Jumlah", "1");
        price = field(root, "Harga satuan (angka)", "25000");
        paid = field(root, "Uang diterima (angka)", "25000");

        addButton(root, "TES CETAK PRINTER", v -> printTest());
        addButton(root, "CETAK NOTA 80 mm", v -> printReceipt());

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

    private void printTest() {
        byte[] data = new byte[]{0x1B, 0x40};
        data = join(data, densityCommand());
        data = join(data, new byte[]{0x1B, 0x61, 0x01});
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
            data = join(data, densityCommand());
            data = join(data, new byte[]{0x1B, 0x61, 0x01});
            data = join(data, textBytes(name + "\n" + addr + "\n"));
            data = join(data, new byte[]{0x1B, 0x61, 0x00});
            data = join(data, textBytes("--------------------------------\n"));
            data = join(data, textBytes("No: " + no + "\n" + product + "\n"));
            data = join(data, textBytes(q + " x " + rupiah(p) + " = " + rupiah(total) + "\n"));
            data = join(data, textBytes("--------------------------------\n"));
            data = join(data, textBytes("TOTAL       " + rupiah(total) + "\n"));
            data = join(data, textBytes("DIBAYAR     " + rupiah(pay) + "\n"));
            data = join(data, textBytes("KEMBALI     " + rupiah(pay-total) + "\n\nTerima kasih!\n"));
            data = join(data, new byte[]{0x1B, 0x64, 0x04});
            send(data);
        } catch (Exception e) { setStatus("Periksa jumlah dan nominal angka."); }
    }

    // ESC/POS GS ( E, Function 5: print density. Firmware support varies by printer.
    private byte[] densityCommand() {
        int level = densitySpinner == null ? 3 : densitySpinner.getSelectedItemPosition() + 1;
        int density = level * 2;
        return new byte[]{0x1D, 0x28, 0x45, 0x02, 0x00, 0x05, (byte)density};
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
