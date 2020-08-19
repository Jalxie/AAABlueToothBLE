package com.example.bdr_demo_app;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;

import com.blankj.utilcode.util.ToastUtils;
import com.example.bdr_demo_app.BleAdapter.BleAdapter;
import com.tbruyelle.rxpermissions2.RxPermissions;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static android.bluetooth.BluetoothDevice.TRANSPORT_LE;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "BDR ESP32";

    TextView tvDeviceName;
    TextView tvMACAddress;
    TextView tvRssiText;
    TextView tvInformationText;
    TextView tvRssiValue;
    TextView tvInformationValue;
    TextView tvDeviceNameValue;
    TextView tvMACAddressValue;
    ListView bleListView;
    private Button btnScan;

    private List<BluetoothDevice> mDatas;
    private List<Integer> mRssis;
    private BleAdapter mAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothManager mBluetoothManager;
    private boolean isScaning=false;
    private boolean isConnecting=false;
    private BluetoothGatt mBluetoothGatt;

    //服务和特征值
    private UUID write_UUID_service;
    private UUID write_UUID_chara;
    private UUID read_UUID_service;
    private UUID read_UUID_chara;
    private UUID notify_UUID_service;
    private UUID notify_UUID_chara;
    private UUID indicate_UUID_service;
    private UUID indicate_UUID_chara;
    private String hex="7B46363941373237323532443741397D";
    private String TARGET_DEVICE_NAME = "BDR_ESP32";// name of target BLE device
    private String macAddress;
    private String name;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //Initialization
        initView();
        initData();

        mBluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();

        if (mBluetoothAdapter==null||!mBluetoothAdapter.isEnabled()){
            Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(intent,0);
        }
    }


    private void initData() {
        mDatas = new ArrayList<>();
        mRssis = new ArrayList<>();
        mAdapter = new BleAdapter(MainActivity.this,mDatas,mRssis);
        bleListView.setAdapter(mAdapter);
        mAdapter.notifyDataSetChanged();
    }

    private void initView() {
        bleListView = findViewById(R.id.ble_device_list);
        btnScan = findViewById(R.id.scan_button);
        tvDeviceName = findViewById(R.id.device_name_text);
        tvMACAddress = findViewById(R.id.mac_address_text);
        tvRssiText = findViewById(R.id.rssi);
        tvInformationText = findViewById(R.id.information_text);
        tvRssiValue = findViewById(R.id.rssi_value);
        tvInformationValue = findViewById(R.id.information_value);
        tvDeviceNameValue = findViewById(R.id.device_name_information);
        tvMACAddressValue = findViewById(R.id.mac_address_information);

        btnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                setContentView(R.layout.activity_ble_scan_list);
                scanDevice();
            }
        });

        bleListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if (isScaning){
                    stopScanDevice();
                }
                if (!isConnecting){
                    isConnecting=true;
                    BluetoothDevice bluetoothDevice;
                    bluetoothDevice = mDatas.get(position);
                    name = bluetoothDevice.getName();
                    if(name != null && name.equals(TARGET_DEVICE_NAME)) {
                        //连接设备
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            mBluetoothGatt = bluetoothDevice.connectGatt(MainActivity.this,
                                    true, gattCallback, TRANSPORT_LE);
                            Log.w(TAG, "BLE name: " + bluetoothDevice.getName());
                            //bluetoothDevice.createBond();
                            //Log.i(TAG, "----------------------------- BONDED ----------------------------");

                        } else {
                            mBluetoothGatt = bluetoothDevice.connectGatt(MainActivity.this,
                                    true, gattCallback);
                            Log.w(TAG, "BLE name: " + bluetoothDevice.getName());
                        }
                    } else{Log.w(TAG, "----------------------------- Select other device ----------------------------");}
                }

            }
        });


    }

    /**
     * 开始扫描 10秒后自动停止
     * */
    private void scanDevice() {
        isScaning = true;
        mBluetoothAdapter.startLeScan(scanCallback);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                //结束扫描
                mBluetoothAdapter.stopLeScan(scanCallback);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        isScaning = false;
                    }
                });
            }
        }, 10000);// 10 second
    }

    /**
     * 停止扫描
     * */
    private void stopScanDevice(){
        isScaning=false;
        Log.w(TAG,"---------------------------- Stop Scan ------------------------");
        mBluetoothAdapter.stopLeScan(scanCallback);
    }

    //Scan call back
    BluetoothAdapter.LeScanCallback scanCallback = new BluetoothAdapter.LeScanCallback() {
        @Override
        public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
            Log.i(TAG, "run: scanning...");
            if (!mDatas.contains(device)) {
                mDatas.add(device);
                mRssis.add(rssi);
                mAdapter.notifyDataSetChanged();
            }
        }
    };

    private BluetoothGattCallback gattCallback=new BluetoothGattCallback() {
        /**
         * 断开或连接 状态发生变化时调用
         * */
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.i(TAG,"onConnectionStateChange()");
            if (status == BluetoothGatt.GATT_SUCCESS){
                //连接成功
                if (newState == BluetoothGatt.STATE_CONNECTED){
                    Log.i(TAG,"---------------------------- Connected ----------------------------");//Connection build

                    //Once connection is built, stop scanning
                    stopScanDevice();

                    //发现服务
                    gatt.discoverServices();
                }
                if (newState == BluetoothGatt.STATE_DISCONNECTED)
                {
                    Log.i(TAG,"----------------------------- Disconnected ----------------------------");
                    addText(tvInformationText, "Disconnected");
                    mBluetoothGatt.close();//dispose previous instance
                }
            }else{
                //连接失败
                Log.i(TAG,"Fail!!! Status: "+ status);//lose connection
                Log.w(TAG, "----------------------------- Fail to Connect ----------------------------");
                //mDatas.clear();
                //ReScanDevice();
                addText(tvInformationText, "Lose Connection");
                Log.w(TAG, "----------------------------- (*********************) ----------------------------");
            }
        }


        /**
         * 发现设备（真正建立连接）
         * */
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            //直到这里才是真正建立了可通信的连接

            Log.w(TAG,"---------------------------- Cleaning ----------------------------");//建立连接
            isConnecting=false;
            Log.i(TAG,"---------------------------- Building Connection ----------------------------");//建立连接
            //获取初始化服务和特征值
            initServiceAndChara();

            //订阅通知
            mBluetoothGatt.setCharacteristicNotification(mBluetoothGatt
                    .getService(notify_UUID_service).getCharacteristic(notify_UUID_chara),true);
            Log.i(TAG,"---------------------------- Notification On ------------------------");

            //Descriptor
            UUID uuid = UUID.fromString("ffeeddcc-bbaa-9988-7766-554433221102");
            BluetoothGattDescriptor descriptor = mBluetoothGatt
                    .getService(notify_UUID_service).getCharacteristic(notify_UUID_chara).getDescriptor(uuid);
            descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
            Log.i(TAG,"---------------------------- Start Descriptor ------------------------");

            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    setContentView(R.layout.activity_ble_scan_list);
                }
            });
        }

        /**
         * 读操作的回调
         * */
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            Log.i(TAG,"onCharacteristicRead()");
        }


        /**
         * 写操作的回调
         * */
        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            super.onCharacteristicWrite(gatt, characteristic, status);

            Log.i(TAG,"onCharacteristicWrite()  status="+status+",value="+HexUtil.encodeHexStr(characteristic.getValue()));
        }


        /**
         * 接收到硬件返回的数据
         * */
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.i(TAG,"onCharacteristicChanged()" + characteristic.getValue());
            //final byte[] data=characteristic.getValue();
            final String data = Arrays.toString(characteristic.getValue());
            //final int dataInt = String2Int(data);
            Log.i(TAG,"value: "+data);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    addText(tvRssiValue,  parseRSSI(data));
                    Log.i(TAG, "Integer value: " + Integer.parseInt(parseRSSI(data)));
                    if(Integer.parseInt(parseRSSI(data)) > (-40)){
                        addText(tvInformationValue,  "Within range");
                        writeDataInrange();
                    }
                    else {
                        addText(tvInformationValue, " ");
                    }
                }
            });

        }

        //Reconnect after disconnect
        private void reConnect(){
            int length = mDatas.size();
            BluetoothDevice bluetoothDevice;
            if (!isConnecting) {
                for (int i = 0; i <= length; i++) {
                    Log.w(TAG,"---------------------------- Find MAC address ------------------------");
                    if (mDatas.get(i).getAddress().equals(macAddress) ) {
                        Log.w(TAG,"MAC Address: " + macAddress);
                        //Stop scan
                        if(isScaning){
                            isScaning = false;
                            stopScanDevice();
                            Log.w(TAG,"---------------------------- Rescan stops ------------------------");
                        }
                        bluetoothDevice = mDatas.get(i);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            mBluetoothGatt = bluetoothDevice.connectGatt(MainActivity.this,
                                    true, gattCallback, TRANSPORT_LE);
                            Log.i(TAG, "BLE name: " + bluetoothDevice.getName());
                            //bluetoothDevice.createBond();
                            //Log.i(TAG, "---------------------------- BONDED ----------------------------");
                            Log.w(TAG,"---------------------------- Re Connected ------------------------");

                        } else {
                            mBluetoothGatt = bluetoothDevice.connectGatt(MainActivity.this,
                                    true, gattCallback);
                            Log.i(TAG, "BLE name: " + bluetoothDevice.getName());
                            Log.w(TAG,"---------------------------- Re Connected ------------------------");
                        }
                    }
                    break;
                }
            }
        }
    };


    /**
     * 检查权限
     */
    private void checkPermissions() {
        RxPermissions rxPermissions = new RxPermissions(MainActivity.this);
        rxPermissions.request(android.Manifest.permission.ACCESS_FINE_LOCATION)
                .subscribe(new io.reactivex.functions.Consumer<Boolean>() {
                    @Override
                    public void accept(Boolean aBoolean) throws Exception {
                        if (aBoolean) {
                            // 用户已经同意该权限
                            scanDevice();
                        } else {
                            // 用户拒绝了该权限，并且选中『不再询问』
                            ToastUtils.showLong("用户开启权限后才能使用");
                        }
                    }
                });
    }


    private void initServiceAndChara(){
        List<BluetoothGattService> bluetoothGattServices= mBluetoothGatt.getServices();
        for (BluetoothGattService bluetoothGattService:bluetoothGattServices){
            List<BluetoothGattCharacteristic> characteristics=bluetoothGattService.getCharacteristics();
            for (BluetoothGattCharacteristic characteristic:characteristics){
                int charaProp = characteristic.getProperties();
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_READ) > 0) {
                    read_UUID_chara=characteristic.getUuid();
                    read_UUID_service=bluetoothGattService.getUuid();
                    Log.i(TAG,"read_chara="+read_UUID_chara+"----read_service="+read_UUID_service);
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE) > 0) {
                    write_UUID_chara=characteristic.getUuid();
                    write_UUID_service=bluetoothGattService.getUuid();
                    Log.i(TAG,"write_chara="+write_UUID_chara+"----write_service="+write_UUID_service);
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) > 0) {
                    write_UUID_chara=characteristic.getUuid();
                    write_UUID_service=bluetoothGattService.getUuid();
                    Log.i(TAG,"write_chara="+write_UUID_chara+"----write_service="+write_UUID_service);

                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_NOTIFY) > 0) {
                    notify_UUID_chara=characteristic.getUuid();
                    notify_UUID_service=bluetoothGattService.getUuid();
                    Log.i(TAG,"notify_chara="+notify_UUID_chara+"----notify_service="+notify_UUID_service);
                }
                if ((charaProp & BluetoothGattCharacteristic.PROPERTY_INDICATE) > 0) {
                    indicate_UUID_chara=characteristic.getUuid();
                    indicate_UUID_service=bluetoothGattService.getUuid();
                    Log.i(TAG,"indicate_chara="+indicate_UUID_chara+"----indicate_service="+indicate_UUID_service);

                }
            }
        }
    }
    int row = 1;
    private void addText(TextView textView, String content) {
        textView.setText(content);
        int offset = textView.getLineCount() * textView.getLineHeight();
        if (offset > textView.getHeight()) {
            textView.scrollTo(0, offset - textView.getHeight());
        }
    }

    /*
    private void writeData(){
        BluetoothGattService service=mBluetoothGatt.getService(write_UUID_service);
        BluetoothGattCharacteristic charaWrite=service.getCharacteristic(write_UUID_chara);
        byte[] data;
        String content=getWriteContent.getText().toString();
        if (!TextUtils.isEmpty(content)){
            data=HexUtil.hexStringToBytes(content);
        }else{
            data=HexUtil.hexStringToBytes(hex);
        }
        if (data.length>20){//数据大于个字节 分批次写入
            Log.i(TAG, "writeData: length="+data.length);
            int num=0;
            if (data.length%20!=0){
                num=data.length/20+1;
            }else{
                num=data.length/20;
            }
            for (int i=0;i<num;i++){
                byte[] tempArr;
                if (i==num-1){
                    tempArr=new byte[data.length-i*20];
                    System.arraycopy(data,i*20,tempArr,0,data.length-i*20);
                }else{
                    tempArr=new byte[20];
                    System.arraycopy(data,i*20,tempArr,0,20);
                }
                charaWrite.setValue(tempArr);
                mBluetoothGatt.writeCharacteristic(charaWrite);
            }
        }else{
            charaWrite.setValue(data);
            mBluetoothGatt.writeCharacteristic(charaWrite);
        }
    }

     */

    private void writeDataInrange(){
        BluetoothGattService service=mBluetoothGatt.getService(write_UUID_service);
        BluetoothGattCharacteristic charaWrite=service.getCharacteristic(write_UUID_chara);

        charaWrite.setValue("Driver in Range");
        mBluetoothGatt.writeCharacteristic(charaWrite);
    }

    private void writeDataOutrange(){
        BluetoothGattService service=mBluetoothGatt.getService(write_UUID_service);
        BluetoothGattCharacteristic charaWrite=service.getCharacteristic(write_UUID_chara);

        charaWrite.setValue("Driver leaving");
        mBluetoothGatt.writeCharacteristic(charaWrite);
    }

    public static String parseRSSI(String data){

        int L = data.length();
        data = data.substring(1, L-1);

        return data;
    }





    @Override
    protected void onDestroy() {
        super.onDestroy();
        mBluetoothGatt.disconnect();
    }


}
