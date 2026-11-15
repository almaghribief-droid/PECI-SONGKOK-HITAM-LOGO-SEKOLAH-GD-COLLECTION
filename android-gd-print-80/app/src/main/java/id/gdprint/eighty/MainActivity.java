package id.gdprint.eighty;

import android.Manifest;
import android.app.Activity;
import android.bluetooth.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.View;
import android.widget.*;
import java.io.*;
import java.nio.charset.Charset;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int REQ_BT=77, REQ_LABEL=88;
    private static final UUID SPP=UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private BluetoothAdapter adapter;
    private BluetoothSocket socket;
    private Spinner printerSpinner,densitySpinner,templateSpinner;
    private final ArrayList<BluetoothDevice> devices=new ArrayList<>();
    private final ArrayList<File> templates=new ArrayList<>();
    private TextView status,sourceLabel,widthValue,scaleValue;
    private EditText shop,address,orderNo,item,qty,price,paid;
    private CropImageView cropView;
    private ImageView preview;
    private SeekBar widthSeek,scaleSeek;
    private Bitmap sourceBitmap;
    private String selectedChannel="label";
    private final ExecutorService worker=Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        adapter=BluetoothAdapter.getDefaultAdapter();
        buildUi();
        requestBtPermission();
        refreshTemplates();
    }

    private void buildUi() {
        ScrollView scroll=new ScrollView(this);
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(24,24,24,32); root.setBackgroundColor(0xFFF3F6FA); scroll.addView(root);
        TextView title=new TextView(this); title.setText("GD PRINT 80"); title.setTextSize(27);
        title.setTypeface(null,1); title.setTextColor(0xFF123B68); root.addView(title);
        TextView sub=new TextView(this); sub.setText("Bluetooth • ESC/POS • Printer thermal 80 mm"); sub.setTextSize(13); root.addView(sub);
        status=new TextView(this); status.setText("Status: Belum tersambung"); status.setPadding(0,18,0,10); root.addView(status);

        heading(root,"1. KONEKSI PRINTER");
        printerSpinner=new Spinner(this); root.addView(printerSpinner);
        addButton(root,"Muat printer Bluetooth yang sudah dipasangkan",v->loadPaired());
        addButton(root,"Sambungkan printer terpilih",v->connectPrinter());
        densitySpinner=new Spinner(this);
        densitySpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,
            new String[]{"1 - Sangat tipis","2 - Tipis","3 - Normal","4 - Pekat","5 - Sangat pekat"}));
        densitySpinner.setSelection(2); addLabel(root,"Ketebalan cetak"); root.addView(densitySpinner);
        addButton(root,"TES CETAK PRINTER",v->printTest());

        heading(root,"2. LABEL SHOPEE / TIKTOK SHOP");
        TextView info=new TextView(this);
        info.setText("Pilih file label yang sudah diunduh dari Seller Centre (PDF atau gambar). Aplikasi tidak mengambil label langsung dari akun marketplace.");
        info.setTextSize(12); info.setTextColor(0xFF526174); root.addView(info);
        addButton(root,"AMBIL LABEL SHOPEE (PDF / GAMBAR)",v->openLabelPicker("Shopee"));
        addButton(root,"AMBIL LABEL TIKTOK SHOP (PDF / GAMBAR)",v->openLabelPicker("TikTok Shop"));
        sourceLabel=new TextView(this); sourceLabel.setText("Belum ada label dipilih"); sourceLabel.setPadding(0,8,0,8); root.addView(sourceLabel);

        heading(root,"3. CROP LABEL");
        TextView cropHelp=new TextView(this);
        cropHelp.setText("Geser bagian dalam kotak untuk memindahkan crop. Tarik titik biru kanan bawah untuk mengubah ukuran crop.");
        cropHelp.setTextSize(12); cropHelp.setTextColor(0xFF526174); root.addView(cropHelp);
        cropView=new CropImageView(this);
        LinearLayout.LayoutParams cropParams=new LinearLayout.LayoutParams(-1,dp(290)); cropParams.topMargin=dp(8); root.addView(cropView,cropParams);
        addButton(root,"PERBARUI PREVIEW HASIL CROP",v->updatePreview());
        addLabel(root,"Preview hasil yang akan dicetak");
        preview=new ImageView(this); preview.setBackgroundColor(0xFFE2E8F0); preview.setScaleType(ImageView.ScaleType.FIT_CENTER);
        root.addView(preview,new LinearLayout.LayoutParams(-1,dp(210)));
        addButton(root,"SIMPAN CROP SEBAGAI TEMPLATE",v->saveTemplate());

        heading(root,"4. TEMPLATE TERSIMPAN");
        templateSpinner=new Spinner(this); root.addView(templateSpinner);
        addButton(root,"MUAT TEMPLATE TERPILIH",v->loadSelectedTemplate());
        addButton(root,"HAPUS TEMPLATE TERPILIH",v->deleteSelectedTemplate());

        heading(root,"5. UKURAN CETAK");
        addLabel(root,"Lebar cetak (dot)");
        widthSeek=new SeekBar(this); widthSeek.setMax(256); widthSeek.setProgress(192); root.addView(widthSeek);
        widthValue=new TextView(this); root.addView(widthValue);
        widthSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean u){widthValue.setText((320+p)+" dot (sekitar "+String.format(Locale.US,"%.1f",(320+p)/8.0)+" mm)");}
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        }); widthSeek.setProgress(192);
        addLabel(root,"Skala ukuran label");
        scaleSeek=new SeekBar(this); scaleSeek.setMax(100); scaleSeek.setProgress(50); root.addView(scaleSeek);
        scaleValue=new TextView(this); scaleValue.setText("100%"); root.addView(scaleValue);
        scaleSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean u){scaleValue.setText((50+p)+"%");}
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){}
        });
        addButton(root,"CETAK LABEL SEKARANG",v->printLabel());

        heading(root,"6. NOTA 80 mm");
        shop=field(root,"Nama toko","GD COLLECTION");
        address=field(root,"Alamat toko","Kebumen");
        orderNo=field(root,"Nomor pesanan","ORD-0001");
        item=field(root,"Nama barang","Songkok hitam");
        qty=field(root,"Jumlah","1");
        price=field(root,"Harga satuan (angka)","25000");
        paid=field(root,"Uang diterima (angka)","25000");
        addButton(root,"CETAK NOTA 80 mm",v->printReceipt());
        TextView footer=new TextView(this);
        footer.setText("\nCatatan: pairing printer dilakukan dari Pengaturan Bluetooth Android. Format PDF yang dipilih saat ini dirender halaman pertama. Hasil akhir wajib diuji pada printer RPP02N karena dukungan perintah ESC/POS dapat berbeda antarfirmware.");
        footer.setTextSize(12); footer.setTextColor(0xFF526174); root.addView(footer);
        setContentView(scroll);
    }

    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+0.5f);}
    private void heading(LinearLayout r,String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(17);t.setTypeface(null,1);t.setTextColor(0xFF123B68);t.setPadding(0,22,0,8);r.addView(t);}
    private void addLabel(LinearLayout r,String s){TextView t=new TextView(this);t.setText(s);t.setTextSize(13);t.setPadding(0,10,0,3);r.addView(t);}
    private void addButton(LinearLayout r,String s,View.OnClickListener l){Button b=new Button(this);b.setText(s);b.setOnClickListener(l);r.addView(b);}
    private EditText field(LinearLayout r,String label,String value){addLabel(r,label);EditText e=new EditText(this);e.setSingleLine(true);e.setText(value);e.setTextSize(15);r.addView(e);return e;}

    private void requestBtPermission(){
        if(Build.VERSION.SDK_INT>=31&&(checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)!=PackageManager.PERMISSION_GRANTED||
           checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN)!=PackageManager.PERMISSION_GRANTED)){
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT,Manifest.permission.BLUETOOTH_SCAN},REQ_BT);
        }else loadPaired();
    }
    @Override public void onRequestPermissionsResult(int code,String[] permissions,int[] results){
        super.onRequestPermissionsResult(code,permissions,results);
        if(code==REQ_BT){boolean ok=true;for(int x:results)if(x!=PackageManager.PERMISSION_GRANTED)ok=false;if(ok)loadPaired();else setStatus("Izin Bluetooth belum diberikan.");}
    }
    private boolean hasBtPermission(){return Build.VERSION.SDK_INT<31||checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)==PackageManager.PERMISSION_GRANTED;}
    private void loadPaired(){
        if(adapter==null){setStatus("HP tidak mendukung Bluetooth.");return;}
        if(!hasBtPermission()){requestBtPermission();return;}
        try{Set<BluetoothDevice> paired=adapter.getBondedDevices();devices.clear();ArrayList<String> names=new ArrayList<>();
            for(BluetoothDevice d:paired){devices.add(d);names.add(d.getName()+" • "+d.getAddress());}
            if(names.isEmpty())names.add("Belum ada printer dipasangkan");
            printerSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
            setStatus("Ditemukan "+devices.size()+" perangkat yang sudah dipasangkan.");
        }catch(Exception e){setStatus("Tidak dapat membaca perangkat Bluetooth: "+e.getMessage());}
    }
    private void connectPrinter(){
        if(devices.isEmpty()){setStatus("Pasangkan printer dari Pengaturan Bluetooth, lalu muat ulang.");return;}
        BluetoothDevice d=devices.get(printerSpinner.getSelectedItemPosition());setStatus("Menghubungkan ke "+d.getName()+"...");
        worker.execute(()->{try{if(socket!=null)try{socket.close();}catch(Exception ignored){}
            socket=d.createRfcommSocketToServiceRecord(SPP);if(adapter!=null&&adapter.isDiscovering())adapter.cancelDiscovery();socket.connect();
            runOnUiThread(()->setStatus("Tersambung: "+d.getName()));
        }catch(Exception e){socket=null;runOnUiThread(()->setStatus("Gagal tersambung: "+e.getMessage()));}});
    }

    private void openLabelPicker(String channel){
        selectedChannel=channel;
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"application/pdf","image/*"});
        startActivityForResult(i,REQ_LABEL);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){
        super.onActivityResult(requestCode,resultCode,data);
        if(requestCode==REQ_LABEL&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();try{
                Bitmap b=decodeDocument(uri);if(b==null)throw new Exception("File tidak dapat dibaca.");
                sourceBitmap=b;cropView.setBitmap(b);sourceLabel.setText(selectedChannel+" • "+b.getWidth()+" × "+b.getHeight()+" px");
                updatePreview();setStatus("Label "+selectedChannel+" berhasil dimuat. Atur crop sebelum cetak.");
            }catch(Exception e){setStatus("Gagal membuka label: "+e.getMessage());}
        }
    }
    private Bitmap decodeDocument(Uri uri)throws Exception{
        String type=getContentResolver().getType(uri);
        if("application/pdf".equals(type)||uri.toString().toLowerCase(Locale.US).endsWith(".pdf")){
            ParcelFileDescriptor fd=getContentResolver().openFileDescriptor(uri,"r");if(fd==null)throw new Exception("PDF tidak bisa dibuka.");
            PdfRenderer renderer=new PdfRenderer(fd);if(renderer.getPageCount()==0){renderer.close();fd.close();throw new Exception("PDF kosong.");}
            PdfRenderer.Page page=renderer.openPage(0);float factor=Math.min(2.5f,1800f/page.getWidth());
            int w=Math.max(1,(int)(page.getWidth()*factor)),h=Math.max(1,(int)(page.getHeight()*factor));
            Bitmap b=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);Canvas c=new Canvas(b);c.drawColor(Color.WHITE);
            page.render(b,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);page.close();renderer.close();fd.close();return b;
        }
        InputStream in=getContentResolver().openInputStream(uri);if(in==null)throw new Exception("Gambar tidak bisa dibuka.");
        Bitmap b=BitmapFactory.decodeStream(in);in.close();return b;
    }
    private void updatePreview(){
        if(cropView==null||sourceBitmap==null){setStatus("Pilih label terlebih dahulu.");return;}
        Bitmap b=cropView.getCroppedBitmap();preview.setImageBitmap(b);setStatus("Preview diperbarui. Periksa area potong sebelum mencetak.");
    }
    private File templateDir(){File d=new File(getFilesDir(),"templates");if(!d.exists())d.mkdirs();return d;}
    private void saveTemplate(){
        if(sourceBitmap==null){setStatus("Pilih dan crop label terlebih dahulu.");return;}
        try{Bitmap b=cropView.getCroppedBitmap();File f=new File(templateDir(),"template_"+System.currentTimeMillis()+".png");
            FileOutputStream out=new FileOutputStream(f);b.compress(Bitmap.CompressFormat.PNG,100,out);out.close();
            refreshTemplates();setStatus("Template crop berhasil disimpan.");
        }catch(Exception e){setStatus("Gagal menyimpan template: "+e.getMessage());}
    }
    private void refreshTemplates(){
        if(templateSpinner==null)return;templates.clear();File[] files=templateDir().listFiles((d,n)->n.toLowerCase(Locale.US).endsWith(".png"));
        ArrayList<String> names=new ArrayList<>();if(files!=null){Arrays.sort(files,(a,b)->Long.compare(b.lastModified(),a.lastModified()));for(File f:files){templates.add(f);names.add(f.getName());}}
        if(names.isEmpty())names.add("Belum ada template tersimpan");
        templateSpinner.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));
    }
    private void loadSelectedTemplate(){
        if(templates.isEmpty()){setStatus("Belum ada template tersimpan.");return;}
        try{Bitmap b=BitmapFactory.decodeFile(templates.get(templateSpinner.getSelectedItemPosition()).getAbsolutePath());
            sourceBitmap=b;cropView.setBitmap(b);sourceLabel.setText("Template tersimpan • "+b.getWidth()+" × "+b.getHeight()+" px");updatePreview();
        }catch(Exception e){setStatus("Gagal memuat template.");}
    }
    private void deleteSelectedTemplate(){
        if(templates.isEmpty()){setStatus("Belum ada template untuk dihapus.");return;}
        File f=templates.get(templateSpinner.getSelectedItemPosition());if(f.delete()){refreshTemplates();setStatus("Template dihapus.");}else setStatus("Template gagal dihapus.");
    }

    private void printLabel(){
        if(sourceBitmap==null){setStatus("Pilih label atau muat template dahulu.");return;}
        Bitmap cropped=cropView.getCroppedBitmap();if(cropped==null)return;
        int target=Math.min(576,(320+widthSeek.getProgress())*(50+scaleSeek.getProgress())/100);
        int height=Math.max(1,(int)(cropped.getHeight()*(target/(float)cropped.getWidth())));
        if(height>30000){setStatus("Label terlalu panjang. Crop lebih pendek dahulu.");return;}
        Bitmap scaled=Bitmap.createScaledBitmap(cropped,target,height,true);
        byte[] raster=bitmapToEscPos(scaled);byte[] data=join(new byte[]{0x1B,0x40},densityCommand());
        data=join(data,new byte[]{0x1B,0x61,0x01});data=join(data,raster);
        data=join(data,new byte[]{0x1B,0x61,0x00,0x1B,0x64,0x03});
        setStatus("Mengirim label "+selectedChannel+" ke printer...");send(data);
    }
    private byte[] bitmapToEscPos(Bitmap bmp){
        int w=bmp.getWidth(),h=bmp.getHeight(),bytesPerRow=(w+7)/8;
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        out.write(0x1D);out.write(0x76);out.write(0x30);out.write(0);
        out.write(bytesPerRow&255);out.write((bytesPerRow>>8)&255);out.write(h&255);out.write((h>>8)&255);
        for(int y=0;y<h;y++)for(int bx=0;bx<bytesPerRow;bx++){
            int value=0;for(int bit=0;bit<8;bit++){int x=bx*8+bit;if(x<w){
                int color=bmp.getPixel(x,y);int gray=(Color.red(color)*299+Color.green(color)*587+Color.blue(color)*114)/1000;
                if(gray<175)value|=(0x80>>bit);
            }}out.write(value);
        }return out.toByteArray();
    }
    private void printTest(){
        byte[] d=new byte[]{0x1B,0x40};d=join(d,densityCommand());d=join(d,new byte[]{0x1B,0x61,0x01});
        d=join(d,textBytes("GD PRINT 80\nTES CETAK BERHASIL\n"));d=join(d,new byte[]{0x1B,0x61,0x00});
        d=join(d,textBytes("--------------------------------\nBluetooth ESC/POS\nKertas 80 mm\n\n\n"));
        d=join(d,new byte[]{0x1D,0x56,0x00});send(d);
    }
    private void printReceipt(){
        try{String n=shop.getText().toString(),a=address.getText().toString(),no=orderNo.getText().toString(),prod=item.getText().toString();
            int q=Math.max(1,Integer.parseInt(qty.getText().toString()));long p=Long.parseLong(price.getText().toString()),pay=Long.parseLong(paid.getText().toString()),total=q*p;
            String s=n+"\n"+a+"\n--------------------------------\nNo: "+no+"\n"+prod+"\n"+q+" x "+rupiah(p)+" = "+rupiah(total)+
                "\n--------------------------------\nTOTAL       "+rupiah(total)+"\nDIBAYAR     "+rupiah(pay)+"\nKEMBALI     "+rupiah(pay-total)+"\n\nTerima kasih!\n\n";
            byte[] d=new byte[]{0x1B,0x40};d=join(d,densityCommand());d=join(d,new byte[]{0x1B,0x61,0x01});
            d=join(d,textBytes(n+"\n"+a+"\n"));d=join(d,new byte[]{0x1B,0x61,0x00});d=join(d,textBytes(s.substring((n+"\n"+a+"\n").length())));
            d=join(d,new byte[]{0x1B,0x64,0x04});send(d);
        }catch(Exception e){setStatus("Periksa jumlah dan nominal angka.");}
    }
    private byte[] densityCommand(){int level=densitySpinner==null?3:densitySpinner.getSelectedItemPosition()+1;return new byte[]{0x1D,0x28,0x45,0x02,0x00,0x05,(byte)(level*2)};}
    private String rupiah(long n){return String.format(Locale.US,"%,d",n).replace(',','.');}
    private byte[] textBytes(String s){return s.getBytes(Charset.forName("CP437"));}
    private byte[] join(byte[] a,byte[] b){byte[] c=new byte[a.length+b.length];System.arraycopy(a,0,c,0,a.length);System.arraycopy(b,0,c,a.length,b.length);return c;}
    private void send(byte[] data){
        worker.execute(()->{try{if(socket==null||!socket.isConnected())throw new Exception("Printer belum tersambung.");
            OutputStream out=socket.getOutputStream();out.write(data);out.flush();runOnUiThread(()->setStatus("Data cetak berhasil dikirim."));
        }catch(Exception e){runOnUiThread(()->setStatus("Cetak gagal: "+e.getMessage()));}});
    }
    private void setStatus(String s){if(status!=null)status.setText("Status: "+s);}
    @Override protected void onDestroy(){super.onDestroy();worker.execute(()->{try{if(socket!=null)socket.close();}catch(Exception ignored){}});worker.shutdown();}
}
