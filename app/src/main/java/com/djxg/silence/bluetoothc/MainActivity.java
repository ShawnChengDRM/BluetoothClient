package com.djxg.silence.bluetoothc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import android.widget.TextView;
import android.widget.Toast;
import com.djxg.silence.bluetoothc.R;
import com.djxg.silence.bluetoothc.BluetoothChatUtil;
public class MainActivity extends AppCompatActivity implements OnClickListener {

    private final static String TAG = "ClientActivity";
    //设置绑定的蓝牙名称
    public static final String BLUETOOTH_NAME = "OnePlus 3T";
    private BluetoothAdapter mBluetoothAdapter;
    private int REQUEST_ENABLE_BT = 1;
    private Context mContext;

    private Button mBtnBluetoothConnect;
    private Button mBtnBluetoohDisconnect;
    private Button mBtnSendMessage;
    private EditText mEdttMessage;

    private TextView mBtConnectState;
    private TextView mTvChat;
    private ProgressDialog mProgressDialog;
    private BluetoothChatUtil mBlthChatUtil;

    private ListView mDevicesLv;

    private List<Map<String, String>> devices = new ArrayList<>();

    private static final int MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION = 1;

    private Handler mHandler = new Handler(){
        public void handleMessage(Message msg) {
            switch(msg.what){
                case BluetoothChatUtil.STATE_CONNECTED:
                    String deviceName = msg.getData().getString(BluetoothChatUtil.DEVICE_NAME);
                    mBtConnectState.setText("已成功连接到设备" + deviceName);
                    if(mProgressDialog.isShowing()){
                        mProgressDialog.dismiss();
                    }
                    break;
                case BluetoothChatUtil.STATAE_CONNECT_FAILURE:
                    if(mProgressDialog.isShowing()){
                        mProgressDialog.dismiss();
                    }
                    Toast.makeText(getApplicationContext(), "连接失败", Toast.LENGTH_SHORT).show();
                    break;
                case BluetoothChatUtil.MESSAGE_DISCONNECTED:
                    if(mProgressDialog.isShowing()){
                        mProgressDialog.dismiss();
                    }
                    mBtConnectState.setText("与设备断开连接");
                    //mBlthChatUtil.startListen();
                    break;
                case BluetoothChatUtil.MESSAGE_READ:{
                    byte[] buf = msg.getData().getByteArray(BluetoothChatUtil.READ_MSG);
                    String str = new String(buf,0,buf.length);
                    Toast.makeText(getApplicationContext(), "读成功" + str, Toast.LENGTH_SHORT).show();

                    mTvChat.setText(mTvChat.getText().toString()+"\n"+str);
                    break;
                }
                case BluetoothChatUtil.MESSAGE_WRITE:{
                    byte[] buf = (byte[]) msg.obj;
                    String str = new String(buf,0,buf.length);
                    Toast.makeText(getApplicationContext(), "发送成功" + str, Toast.LENGTH_SHORT).show();
                    break;
                }
                default:
                    break;
            }
        };
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mContext = this;

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);

        mDevicesLv = findViewById(R.id.list_view);
        mDevicesLv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
                Map<String, String> s = devices.get(i);
                String address = s.get("address");//把地址解析出来
                //主动连接蓝牙服务端
                try {
                    // 如果当前正在搜索，则取消搜索。
                    if (mBluetoothAdapter.isDiscovering()) {
                        mBluetoothAdapter.cancelDiscovery();
                    }
                            //获得远程设备
                    BluetoothDevice connection_device = mBluetoothAdapter.getRemoteDevice(address);
                    mProgressDialog.setTitle(getResources().getString(R.string.progress_connecting));
                    mBlthChatUtil.connect(connection_device);

                } catch (Exception e) {
                }

            }
        });

        checkBluetoothPermission();

        initView();
        initBluetooth();
        mBlthChatUtil = BluetoothChatUtil.getInstance(mContext);
        mBlthChatUtil.registerHandler(mHandler);
    }

    private void initView() {
        mBtnBluetoothConnect = (Button)findViewById(R.id.btn_blth_connect);
        mBtnBluetoohDisconnect = (Button)findViewById(R.id.btn_blth_disconnect);
        mBtnSendMessage = (Button)findViewById(R.id.btn_sendmessage);
        mEdttMessage = (EditText)findViewById(R.id.edt_message);
        mBtConnectState = (TextView)findViewById(R.id.tv_connect_state);
        mTvChat = (TextView)findViewById(R.id.tv_chat);

        mBtnBluetoothConnect.setOnClickListener(this);
        mBtnBluetoohDisconnect.setOnClickListener(this);
        mBtnSendMessage.setOnClickListener(this);
        mProgressDialog = new ProgressDialog(this);
    }

    private void initBluetooth() {


        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (mBluetoothAdapter == null) {//设备不支持蓝牙
            Toast.makeText(getApplicationContext(), "设备不支持蓝牙",
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }
        //判断蓝牙是否开启
        if (!mBluetoothAdapter.isEnabled()) {//蓝牙未开启
            Intent enableIntent = new Intent(
                    BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            //mBluetoothAdapter.enable();此方法直接开启蓝牙，不建议这样用。
        }
        //注册广播接收者，监听扫描到的蓝牙设备
        IntentFilter filter = new IntentFilter();
        //发现设备
        filter.addAction(BluetoothDevice.ACTION_FOUND);
        filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        registerReceiver(mBluetoothReceiver, filter);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.d(TAG, "onActivityResult request="+requestCode+" result="+resultCode);
        if(requestCode == 1){
            if(resultCode == RESULT_OK){

            }else if(resultCode == RESULT_CANCELED){
                finish();
            }
        }
    }
    @Override
    protected void onResume() {
        super.onResume();
        if (mBlthChatUtil != null) {
            if (mBlthChatUtil.getState() == BluetoothChatUtil.STATE_CONNECTED){
                BluetoothDevice device = mBlthChatUtil.getConnectedDevice();
                if(null != device && null != device.getName()){
                    mBtConnectState.setText("已成功连接到设备" + device.getName());
                }else {
                    mBtConnectState.setText("已成功连接到设备");
                }
            }
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestroy");
        mBlthChatUtil = null;
        unregisterReceiver(mBluetoothReceiver);
    }

    @Override
    public void onClick(View arg0) {
        switch(arg0.getId()){
            case R.id.btn_blth_connect:
                if (mBlthChatUtil.getState() == BluetoothChatUtil.STATE_CONNECTED) {
                    Toast.makeText(mContext, "蓝牙已连接", Toast.LENGTH_SHORT).show();
                }else {
                    discoveryDevices();
                }
                break;
            case R.id.btn_blth_disconnect:
                if (mBlthChatUtil.getState() != BluetoothChatUtil.STATE_CONNECTED) {
                    Toast.makeText(mContext, "蓝牙未连接", Toast.LENGTH_SHORT).show();
                }else {
                    mBlthChatUtil.disconnect();
                }
                break;
            case R.id.btn_sendmessage:
                String messagesend = mEdttMessage.getText().toString();
                if(null == messagesend || messagesend.length() == 0){
                    return;
                }
                mBlthChatUtil.write(messagesend.getBytes());
                break;
            default:
                break;
        }
    }

    private void discoveryDevices() {
        if(mProgressDialog.isShowing()){
            mProgressDialog.dismiss();
        }
        if (mBluetoothAdapter.isDiscovering()){
            //如果正在扫描则返回
            return;
        }
        //mProgressDialog.setTitle(getResources().getString(R.string.progress_scaning));
        //mProgressDialog.show();
        // 扫描蓝牙设备
        mBluetoothAdapter.startDiscovery();

    }

    private BroadcastReceiver mBluetoothReceiver = new BroadcastReceiver(){
        @Override
        public void onReceive(Context context, Intent intent) {
            int i = 1;
            String action = intent.getAction();
            Log.d(TAG,"mBluetoothReceiver action ="+action);
            if(BluetoothDevice.ACTION_FOUND.equals(action)){
                //获取蓝牙设备
                BluetoothDevice scanDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if(scanDevice == null || scanDevice.getName() == null) return;
                Log.d(TAG, "name="+scanDevice.getName()+"address="+scanDevice.getAddress());

                Map<String, String> deviceMap = new HashMap<>();
                //蓝牙设备名称
                String name = scanDevice.getName();


                if(name != null && isSingleDevice(scanDevice)){

                    deviceMap.put("name", name);
                    deviceMap.put("address", scanDevice.getAddress());
                    devices.add(deviceMap);


                    //mBluetoothAdapter.cancelDiscovery(); //取消扫描
                    //mProgressDialog.setTitle(getResources().getString(R.string.progress_connecting));
                    //mBlthChatUtil.connect(scanDevice);
                }

                // 显示发现的蓝牙设备列表
                mDevicesLv.setVisibility(View.VISIBLE);
                // 加载设备
                showDevices();
            }else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)){
            }
        }
    };

    /**
     * 判断此设备是否存在
     */
    private boolean isSingleDevice(BluetoothDevice device) {
        if (devices == null) {
            return true;
        }
        for (Map<String, String> mDeviceMap : devices) {
            if ((device.getAddress()).equals(mDeviceMap.get("address"))) {
                return false;
            }
        }
        return true;
    }

    /**
     * 显示搜索到的设备列表
     */
    private void showDevices() {
        SimpleAdapter mSimpleAdapter = new SimpleAdapter(MainActivity.this, devices,
                android.R.layout.simple_list_item_2,
                new String[]{"name", "address"},
                new int[]{android.R.id.text1, android.R.id.text2});
        mDevicesLv.setAdapter(mSimpleAdapter);
    }



    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        doNext(requestCode, grantResults);
    }

    private void doNext(int requestCode, int[] grantResults) {
        if (requestCode == MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                //同意权限
                //connectBluetooth();
            } else {
                // 权限拒绝
                // 下面的方法最好写一个跳转，可以直接跳转到权限设置页面，方便用户
                //denyPermission();
            }
        }
    }


    /*
       校验蓝牙权限
      */
    private void checkBluetoothPermission() {

        //判断是否有权限
        if (ContextCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            //请求权限
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                    MY_PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);
            //判断是否需要 向用户解释，为什么要申请该权限
            if(ActivityCompat.shouldShowRequestPermissionRationale(this,
                    Manifest.permission.READ_CONTACTS)) {
                Toast.makeText(this, "shouldShowRequestPermissionRationale", Toast.LENGTH_SHORT).show();
            }
        }
    }


    @SuppressWarnings("unused")
    private void getBtDeviceInfo(){
        //获取本机蓝牙名称
        String name = mBluetoothAdapter.getName();
        //获取本机蓝牙地址
        String address = mBluetoothAdapter.getAddress();
        Log.d(TAG,"bluetooth name ="+name+" address ="+address);
        //获取已配对蓝牙设备
        Set<BluetoothDevice> devices = mBluetoothAdapter.getBondedDevices();
        Log.d(TAG, "bonded device size ="+devices.size());
        for(BluetoothDevice bonddevice:devices){
            Log.d(TAG, "bonded device name ="+bonddevice.getName()+
                    " address"+bonddevice.getAddress());
        }
    }
}
