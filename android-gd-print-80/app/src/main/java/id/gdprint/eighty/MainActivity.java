package id.gdprint.eighty;

import android.Manifest;
import android.app.Activity;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.hardware.usb.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_BT = 77;
    private static final String USB_ACTION = "id.gdprint.eighty.USB_PERMISSION";
    private static final UUID SPP = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private BluetoothAdapter btAdapter;
    private BluetoothSocket btSocket;
    private UsbManager usbManager;
    private UsbDevice usbDevice;
    private UsbInterface usbInterface;
    private UsbEndpoint usbOut;
    private UsbDeviceConnection usbConnection;
    private Socket wifiSocket;
    private Bitmap documentBitmap;
    private Uri documentUri;
    private String documentName = "Belum ada dokumen";
    private TextView status, fileLabel;
    private ImageView preview;
    private Spinner transport, paper, orientation, btList, usbList;
    private EditText customWidth, wifiHost, wifiPort;
    private final ArrayList<BluetoothDevice> btDevices = new ArrayList<>();
    private final ArrayList<UsbDevice> usbDevices = new ArrayList<>();
    private final BroadcastReceiver usbPermissionReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (USB_ACTION.equals(intent.getAction())) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                boolean granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false);
                if (granted && device != null) connectUsbDevice(device);
                else setStatus("Izin USB ditolak.");
            }
        }
    };

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        btAdapter = BluetoothAdapter.getDefaultAdapter();
        usbManager = (UsbManager)getSystemService(USB_SERVICE);
        buildUi();
        IntentFilter filter = new IntentFilter(USB_ACTION);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(usbPermissionReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(usbPermissionReceiver, filter);
        handleIncoming(getIntent());
        requestBluetoothPermission();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,24,24,28);
        root.setBackgroundColor(0xFFF3F6FA);
        scroll.addView(root);
        TextView title = new TextView(this);
        title.setText("GD PRINT");
        title.setTextSize(28);
        title.setTypeface(null, Typeface.BOLD);
        title.setTextColor(0xFF123B68);
        root.addView(title);
        TextView sub = new TextView(this);
        sub.setText("Terima PDF / JPG / PNG melalui menu Bagikan, lalu pratinjau dan cetak.");
        sub.setTextSize(13);
        root.addView(sub);
        status = new TextView(this);
        status.setText("Status: Siap");
        status.setPadding(0,16,0,12);
        root.addView(status);
        fileLabel = new TextView(this);
        fileLabel.setText(documentName);
        fileLabel.setTypeface(null, Typeface.BOLD);
        root.addView(fileLabel);
        addButton(root,"PILIH FILE PDF / JPG / PNG",v -> chooseFile());
        preview = new ImageView(this);
        preview.setBackgroundColor(0xFFFFFFFF);
        preview.setAdjustViewBounds(true);
        preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(-1, 420);
        pp.setMargins(0,12,0,12);
        root.addView(preview,pp);

        label(root,"Metode koneksi");
        transport = spinner(root,new String[]{"Bluetooth","USB OTG","Wi-Fi (IP printer)"});
        label(root,"Printer Bluetooth yang sudah dipasangkan");
        btList = spinner(root,new String[]{"Tekan MUAT BLUETOOTH"});
        addButton(root,"MUAT PRINTER BLUETOOTH",v -> loadBluetooth());
        addButton(root,"SAMBUNGKAN BLUETOOTH",v -> connectBluetooth());
        label(root,"Printer USB OTG");
        usbList = spinner(root,new String[]{"Tekan MUAT USB"});
        addButton(root,"MUAT PERANGKAT USB",v -> loadUsb());
        addButton(root,"SAMBUNGKAN USB OTG",v -> connectSelectedUsb());
        label(root,"Alamat IP printer Wi-Fi");
        wifiHost = edit(root,"Contoh: 192.168.1.100");
        label(root,"Port printer (umumnya 9100)");
        wifiPort = edit(root,"9100");
        addButton(root,"SAMBUNGKAN WI-FI",v -> connectWifi());

        label(root,"Lebar kertas");
        paper = spinner(root,new String[]{"58 mm","80 mm","112 mm","Custom"});
        customWidth = edit(root,"Lebar custom dalam mm, contoh 80");
        label(root,"Orientasi");
        orientation = spinner(root,new String[]{"Portrait (Potret)","Landscape (Lanskap)"});
        addButton(root,"SESUAIKAN PRATINJAU",v -> refreshPreview());
        addButton(root,"CETAK DOKUMEN",v -> printDocument());
        addButton(root,"TES CETAK",v -> printTest());
        TextView hint = new TextView(this);
        hint.setText("\nCatatan: Bluetooth memakai ESC/POS SPP. USB memerlukan printer USB yang kompatibel ESC/POS. Wi-Fi memakai RAW TCP (umumnya port 9100). PDF saat ini dirender halaman pertama. Hasil dan lebar cetak harus diuji pada printer sebenarnya.");
        hint.setTextSize(12);
        hint.setTextColor(0xFF526174);
        root.addView(hint);
        setContentView(scroll);
    }

    private void label(LinearLayout root,String s) {
        TextView t = new TextView(this); t.setText(s); t.setTextSize(13); t.setTypeface(null,Typeface.BOLD); t.setPadding(0,12,0,4); root.addView(t);
    }
    private EditText edit(LinearLayout root,String hint) {
        EditText e = new EditText(this); e.setSingleLine(true); e.setTextSize(14); e.setHint(hint); root.addView(e); return e;
    }
    private Spinner spinner(LinearLayout root,String[] values) {
        Spinner s = new Spinner(this);
        s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,values));
        root.addView(s); return s;
    }
    private void addButton(LinearLayout root,String text,View.OnClickListener l) {
        Button b = new Button(this); b.setText(text); b.setOnClickListener(l); root.addView(b);
    }

    private void requestBluetoothPermission() {
        if (Build.VERSION.SDK_INT >= 31 && checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT},REQ_BT);
    }
    @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(requestCode,permissions,results);
        if (requestCode == REQ_BT && results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) loadBluetooth();
    }
    private boolean btPermission() {
        return Build.VERSION.SDK_INT < 31 || checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED;
    }

    private void handleIncoming(Intent intent) {
        if (intent == null) return;
        Uri uri = null;
        if (Intent.ACTION_SEND.equals(intent.getAction())) {
            if (Build.VERSION.SDK_INT >= 33) uri = intent.getParcelableExtra(Intent.EXTRA_STREAM,Uri.class);
            else uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
        } else if (Intent.ACTION_VIEW.equals(intent.getAction())) uri = intent.getData();
        if (uri != null) loadDocument(uri);
    }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncoming(intent);
    }
    private void chooseFile() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","image/jpeg","image/png"});
        startActivityForResult(i,91);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data) {
        super.onActivityResult(requestCode,resultCode,data);
        if (requestCode == 91 && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri u = data.getData();
            try { getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch(Exception ignored) {}
            loadDocument(u);
        }
    }
    private void loadDocument(Uri uri) {
        documentUri = uri;
        worker.execute(() -> {
            try {
                Bitmap b;
                String type = getContentResolver().getType(uri);
                if (type != null && type.equals("application/pdf")) {
                    ParcelFileDescriptor fd = getContentResolver().openFileDescriptor(uri,"r");
                    if (fd == null) throw new Exception("File PDF tidak bisa dibuka.");
                    PdfRenderer renderer = new PdfRenderer(fd);
                    if (renderer.getPageCount() < 1) throw new Exception("PDF kosong.");
                    PdfRenderer.Page page = renderer.openPage(0);
                    int w = Math.max(1,page.getWidth()*2), h = Math.max(1,page.getHeight()*2);
                    b = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                    b.eraseColor(Color.WHITE);
                    page.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                    page.close(); renderer.close(); fd.close();
                } else {
                    InputStream in = getContentResolver().openInputStream(uri);
                    if (in == null) throw new Exception("Gambar tidak bisa dibuka.");
                    b = BitmapFactory.decodeStream(in); in.close();
                    if (b == null) throw new Exception("Format gambar tidak didukung.");
                }
                String name = queryName(uri);
                documentBitmap = b;
                documentName = name;
                runOnUiThread(() -> {
                    fileLabel.setText("Dokumen: " + documentName);
                    refreshPreview();
                    setStatus("Dokumen berhasil dimuat. Periksa pratinjau sebelum cetak.");
                });
            } catch(Exception e) { runOnUiThread(() -> setStatus("Gagal membuka dokumen: " + e.getMessage())); }
        });
    }
    private String queryName(Uri uri) {
        String n = "Dokumen";
        try (android.database.Cursor c = getContentResolver().query(uri,null,null,null,null)) {
            if (c != null && c.moveToFirst()) {
                int i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (i >= 0) n = c.getString(i);
            }
        } catch(Exception ignored) {}
        return n;
    }
    private int paperDots() {
        String p = String.valueOf(paper.getSelectedItem());
        double mm = 80;
        if (p.startsWith("58")) mm = 58;
        else if (p.startsWith("112")) mm = 112;
        else if (p.startsWith("Custom")) {
            try { mm = Double.parseDouble(customWidth.getText().toString()); } catch(Exception e) { mm = 80; }
        }
        mm = Math.max(30,Math.min(150,mm));
        return (int)Math.round(mm * 8.0); // nominal 203 dpi: about 8 dots/mm
    }
    private Bitmap preparedBitmap() {
        if (documentBitmap == null) return null;
        Bitmap src = documentBitmap;
        if (orientation.getSelectedItemPosition() == 1) {
            Matrix m = new Matrix(); m.postRotate(90);
            src = Bitmap.createBitmap(src,0,0,src.getWidth(),src.getHeight(),m,true);
        }
        int width = paperDots();
        int height = Math.max(1,(int)((double)src.getHeight()*width/src.getWidth()));
        height = Math.min(height,12000);
        return Bitmap.createScaledBitmap(src,width,height,true);
    }
    private void refreshPreview() {
        if (documentBitmap == null) { setStatus("Pilih atau bagikan dokumen terlebih dahulu."); return; }
        Bitmap b = preparedBitmap();
        preview.setImageBitmap(b);
        setStatus("Pratinjau siap • " + b.getWidth() + " dot lebar.");
    }

    private void loadBluetooth() {
        if (btAdapter == null) { setStatus("Bluetooth tidak tersedia."); return; }
        if (!btPermission()) { requestBluetoothPermission(); return; }
        try {
            btDevices.clear();
            ArrayList<String> names = new ArrayList<>();
            for (BluetoothDevice d : btAdapter.getBondedDevices()) {
                btDevices.add(d);
                names.add(d.getName()+" • "+d.getAddress());
            }
            if (names.isEmpty()) names.add("Belum ada perangkat dipasangkan");
            btList.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
            setStatus("Perangkat Bluetooth ditemukan: "+btDevices.size());
        } catch(Exception e) { setStatus("Tidak dapat membaca Bluetooth: "+e.getMessage()); }
    }
    private void connectBluetooth() {
        if (!btPermission()) { requestBluetoothPermission(); return; }
        if (btDevices.isEmpty()) { setStatus("Muat dan pasangkan printer Bluetooth terlebih dahulu."); return; }
        BluetoothDevice d = btDevices.get(Math.max(0,btList.getSelectedItemPosition()));
        setStatus("Menghubungkan Bluetooth...");
        worker.execute(() -> {
            try {
                if (btSocket != null) try { btSocket.close(); } catch(Exception ignored) {}
                btSocket = d.createRfcommSocketToServiceRecord(SPP);
                if (btAdapter != null) btAdapter.cancelDiscovery();
                btSocket.connect();
                runOnUiThread(() -> setStatus("Bluetooth tersambung: "+d.getName()));
            } catch(Exception e) { btSocket = null; runOnUiThread(() -> setStatus("Bluetooth gagal: "+e.getMessage())); }
        });
    }

    private void loadUsb() {
        usbDevices.clear();
        ArrayList<String> names = new ArrayList<>();
        for (UsbDevice d : usbManager.getDeviceList().values()) {
            usbDevices.add(d); names.add(d.getProductName() == null ? "USB "+d.getDeviceName() : d.getProductName());
        }
        if (names.isEmpty()) names.add("Tidak ada perangkat USB");
        usbList.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
        setStatus("Perangkat USB terdeteksi: "+usbDevices.size());
    }
    private void connectSelectedUsb() {
        if (usbDevices.isEmpty()) { setStatus("Hubungkan printer melalui kabel OTG, lalu muat USB."); return; }
        UsbDevice d = usbDevices.get(Math.max(0,usbList.getSelectedItemPosition()));
        if (!usbManager.hasPermission(d)) {
            PendingIntent pi = PendingIntent.getBroadcast(this,0,new Intent(USB_ACTION),PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
            usbManager.requestPermission(d,pi);
        } else connectUsbDevice(d);
    }
    private void connectUsbDevice(UsbDevice d) {
        try {
            UsbDeviceConnection c = usbManager.openDevice(d);
            if (c == null) throw new Exception("USB tidak dapat dibuka.");
            UsbInterface intf = null; UsbEndpoint out = null;
            for (int i=0;i<d.getInterfaceCount();i++) {
                UsbInterface candidate = d.getInterface(i);
                for (int j=0;j<candidate.getEndpointCount();j++) {
                    UsbEndpoint ep = candidate.getEndpoint(j);
                    if (ep.getDirection()==UsbConstants.USB_DIR_OUT && ep.getType()==UsbConstants.USB_ENDPOINT_XFER_BULK) { intf=candidate; out=ep; break; }
                }
                if (out != null) break;
            }
            if (out == null || intf == null) { c.close(); throw new Exception("Endpoint USB bulk OUT tidak ditemukan."); }
            if (!c.claimInterface(intf,true)) { c.close(); throw new Exception("Interface USB gagal diakses."); }
            if (usbConnection != null) usbConnection.close();
            usbConnection=c; usbDevice=d; usbInterface=intf; usbOut=out;
            setStatus("USB tersambung: "+d.getDeviceName());
        } catch(Exception e) { setStatus("USB gagal: "+e.getMessage()); }
    }

    private void connectWifi() {
        String host = wifiHost.getText().toString().trim();
        int port;
        try { port=Integer.parseInt(wifiPort.getText().toString().trim()); } catch(Exception e) { port=9100; }
        final int p=port;
        if (host.isEmpty() || p<1 || p>65535) { setStatus("Isi IP printer dan port yang benar."); return; }
        setStatus("Menghubungkan Wi-Fi...");
        worker.execute(() -> {
            try {
                Socket s = new Socket(host,p);
                if (wifiSocket != null) try { wifiSocket.close(); } catch(Exception ignored) {}
                wifiSocket=s;
                runOnUiThread(() -> setStatus("Wi-Fi tersambung ke "+host+":"+p));
            } catch(Exception e) { runOnUiThread(() -> setStatus("Wi-Fi gagal: "+e.getMessage())); }
        });
    }

    private void printTest() {
        byte[] test = new byte[]{0x1B,0x40,0x1B,0x61,0x01};
        byte[] txt = "GD PRINT\nTES KONEKSI ESC/POS\n\n\n".getBytes(Charset.forName("CP437"));
        byte[] all = new byte[test.length+txt.length];
        System.arraycopy(test,0,all,0,test.length); System.arraycopy(txt,0,all,test.length,txt.length);
        sendBytes(all);
    }
    private void printDocument() {
        if (documentBitmap == null) { setStatus("Belum ada dokumen. Bagikan atau pilih PDF/JPG/PNG."); return; }
        Bitmap b = preparedBitmap();
        setStatus("Menyiapkan data cetak...");
        worker.execute(() -> {
            try {
                byte[] data = rasterCommand(b);
                sendBytesNow(data);
                runOnUiThread(() -> setStatus("Data dokumen dikirim. Periksa hasil cetak."));
            } catch(Exception e) { runOnUiThread(() -> setStatus("Cetak gagal: "+e.getMessage())); }
        });
    }
    private byte[] rasterCommand(Bitmap b) throws Exception {
        int width=b.getWidth(), height=b.getHeight(), bytesPerRow=(width+7)/8;
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(new byte[]{0x1B,0x40});
        for (int y=0;y<height;y+=128) {
            int rows=Math.min(128,height-y);
            out.write(new byte[]{0x1D,0x76,0x30,0x00,(byte)(bytesPerRow&255),(byte)((bytesPerRow>>8)&255),(byte)(rows&255),(byte)((rows>>8)&255)});
            for (int yy=0;yy<rows;yy++) {
                for (int xb=0;xb<bytesPerRow;xb++) {
                    int value=0;
                    for (int bit=0;bit<8;bit++) {
                        int x=xb*8+bit;
                        if (x<width) {
                            int color=b.getPixel(x,y+yy);
                            int lum=(Color.red(color)*299+Color.green(color)*587+Color.blue(color)*114)/1000;
                            if (lum<180) value |= (0x80>>bit);
                        }
                    }
                    out.write(value);
                }
            }
        }
        out.write(new byte[]{0x1B,0x64,0x04});
        return out.toByteArray();
    }
    private void sendBytes(byte[] data) {
        worker.execute(() -> {
            try { sendBytesNow(data); runOnUiThread(() -> setStatus("Data berhasil dikirim ke printer.")); }
            catch(Exception e) { runOnUiThread(() -> setStatus("Pengiriman gagal: "+e.getMessage())); }
        });
    }
    private void sendBytesNow(byte[] data) throws Exception {
        String method=String.valueOf(transport.getSelectedItem());
        if (method.startsWith("Bluetooth")) {
            if (btSocket==null || !btSocket.isConnected()) throw new Exception("Bluetooth belum tersambung.");
            OutputStream out=btSocket.getOutputStream(); out.write(data); out.flush();
        } else if (method.startsWith("USB")) {
            if (usbConnection==null || usbOut==null) throw new Exception("USB OTG belum tersambung.");
            int offset=0;
            while (offset<data.length) {
                int n=Math.min(16384,data.length-offset);
                int sent=usbConnection.bulkTransfer(usbOut,data,offset,n,10000);
                if (sent<=0) throw new Exception("Transfer USB terhenti.");
                offset+=sent;
            }
        } else {
            if (wifiSocket==null || !wifiSocket.isConnected()) throw new Exception("Wi-Fi belum tersambung.");
            OutputStream out=wifiSocket.getOutputStream(); out.write(data); out.flush();
        }
    }
    private void setStatus(String s) { if (status!=null) status.setText("Status: "+s); }

    @Override protected void onDestroy() {
        try { unregisterReceiver(usbPermissionReceiver); } catch(Exception ignored) {}
        worker.execute(() -> {
            try { if (btSocket!=null) btSocket.close(); } catch(Exception ignored) {}
            try { if (usbConnection!=null) usbConnection.close(); } catch(Exception ignored) {}
            try { if (wifiSocket!=null) wifiSocket.close(); } catch(Exception ignored) {}
        });
        worker.shutdown();
        super.onDestroy();
    }
}
