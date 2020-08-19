package com.github.greyfox13.stravanotifier;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc;
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState;
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;

import java.lang.ref.WeakReference;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static com.github.greyfox13.stravanotifier.HrService.CLIENT_CHARACTERISTIC_CONFIG;

public class MainActivity extends AppCompatActivity implements View.OnClickListener
{
    LinearLayout llAnt;
    LinearLayout llBle;
    Switch swHrDeviceType;
    Switch swAutostart;
    Switch swUseHr;
    TextView tvAntDevice;
    TextView tvBleDevice;
    TextView tvAntDev;
    TextView tvBleDev;
    TextView tvWristDevice;
    TextView tvTime;
    TextView tvDist;
    Button btnSelectAnt;
    Button btnSelectBle;
    Button btnSelectWrist;
    Button btnStart;
    TextView tvHrStatus;
    TextView tvHeartRate;
    TextView tvStatus;
    PccReleaseHandle pccReleaseHandle;
    int selectedAntDeviceNumber = -1;
    byte heartrate = 0;
    long time = 0;
    long dist = 0;
    String selectedBleDevice = "";
    String selectedWristDevice = "";
    boolean antUsed = true;
    boolean antSelected = false;
    boolean antSupported = false;
    boolean bleUsed = false;
    boolean bleSelected = false;
    boolean bleSupported = false;
    boolean wristSelected = false;
    boolean isServiceRun = false;
    boolean isStarted = false;
    boolean isAutostart = false;
    boolean isHrEnabled = false;
    boolean permissionExtStorageGranted = false;
    boolean permissionLocationGranted = false;
    boolean isRecordRun = false;
    boolean isRecordRunNew = false;
    static MyHandler mainHandler;
    NotificationManager nm;
    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice bleWristDevice;
    BluetoothGatt bleWristGatt;
    BluetoothGattService wristService;
    BluetoothGattCharacteristic wristCharacteristic;

    SharedPreferences sPref;

    StravaNotifService.StravaData stravaData;

    private Timer wristTimer;
    private WristTimerTask wristTimerTask;

    final static String WRIST_SERVICE = "0000FF01-0000-1000-8000-00805F9B34FB";
    final static String WRIST_CHARACTERISTIC = "0000FF02-0000-1000-8000-00805F9B34FB";

    byte[] cmdSetCfg = {0x01, 0x07};
    byte[] cmdSetRun = {0x05, 0x01};
    byte[] cmdSetData = {0x02, 0x00, 0x00,0x00,0x00,0x00, 0x00,0x00,0x00,0x00, 0x00,0x00,0x00,0x00, 0x00};

    //handler messages IDs
    final static int ANT_SET_STATUS = 0;
    final static int ANT_SET_HEARTRATE = 1;
    final static int BLE_SET_STATUS = 3;
    final static int BLE_SET_HEARTRATE_RR = 4;
    final static int WRIST_SET_DATA = 5;
    final static int WRIST_SET_DIST = 6;
    final static int WRIST_SET_RECORD_STATUS = 7;
    final static int WRIST_SET_TIME = 8;
    //service notify id
    final static int SERVICE_NOTIFY_ID = 100;
    //access IDs
    final static int REQUEST_PERMISSION_EXT_STORAGE = 10;
    final static int REQUEST_PERMISSION_LOCATION = 11;
    final static int REQUEST_ENABLE_BT = 12;
    final static int REQUEST_BLE_DEVICE = 13;
    final static int REQUEST_WRIST_DEVICE = 14;

    public class StravaData1
    {
        public long dist;
        public long time;
        public boolean run;
    }

    public static class MyHandler extends Handler
    {

        WeakReference<MainActivity> wrActivity;

        public MyHandler(MainActivity activity)
        {
            wrActivity = new WeakReference<MainActivity>(activity);
        }

        public void setTarget(MainActivity activity) {
            wrActivity.clear();
            wrActivity = new WeakReference<MainActivity>(activity);
        }
        @Override
        public void handleMessage(Message msg)
        {
            MainActivity activity = wrActivity.get();
            int i=0;
            boolean res=false;
            switch (msg.what)
            {
                case ANT_SET_STATUS:
                    activity.tvHrStatus.setText(msg.obj.toString());
                    break;
                case ANT_SET_HEARTRATE:
                    activity.tvHeartRate.setText(String.valueOf(msg.arg1));
                    activity.heartrate = (byte)msg.arg1;
                    break;
                case BLE_SET_STATUS:
                    if(msg.arg1 == BluetoothProfile.STATE_DISCONNECTED)
                    {
                        activity.tvHrStatus.setText(R.string.str_ble_disconnected);
                    }
                    else
                    {
                        activity.tvHrStatus.setText(R.string.str_ble_connected);
                    }
                    break;
                case BLE_SET_HEARTRATE_RR:
                    activity.tvHeartRate.setText(String.valueOf(msg.arg1));
                    activity.heartrate = (byte)msg.arg1;
                    break;
                case WRIST_SET_DATA:
                    activity.stravaData = (StravaNotifService.StravaData)msg.obj;
                    activity.tvTime.setText(timeConvert(activity.stravaData.time));
                    activity.time = activity.stravaData.time;
                    activity.tvDist.setText(String.valueOf(activity.stravaData.dist));
                    activity.dist = activity.stravaData.dist;
                    if(activity.bleWristGatt!=null && activity.wristCharacteristic!=null && activity.isStarted)
                    {
                        activity.isRecordRun = activity.stravaData.run;
                        activity.cmdSetData[1] = activity.heartrate;
                        activity.cmdSetData[2] = (byte) (activity.dist >> 24);
                        activity.cmdSetData[3] = (byte) (activity.dist >> 16);
                        activity.cmdSetData[4] = (byte) (activity.dist >> 8);
                        activity.cmdSetData[5] = (byte) activity.dist;
                        activity.cmdSetData[6] = (byte) (activity.time >> 24);
                        activity.cmdSetData[7] = (byte) (activity.time >> 16);
                        activity.cmdSetData[8] = (byte) (activity.time >> 8);
                        activity.cmdSetData[9] = (byte) activity.time;
                        if(activity.stravaData.run) activity.cmdSetData[14] = 0x01;
                            else activity.cmdSetData[14] = 0x02;
                        activity.wristCharacteristic.setValue(activity.cmdSetData);
                        while(!res && i<20)
                        {
                            res=activity.bleWristGatt.writeCharacteristic(activity.wristCharacteristic);
                            if(!res)
                            {
                                try {
                                    Thread.sleep(100);
                                }catch (Exception e){
                                    e.printStackTrace();}
                            }
                            i++;
                            Log.d("GFX", "RUN = " + activity.stravaData.run + "; res = " + res + "; i = " + i);
                        }
                    }
                    break;
            }
            super.handleMessage(msg);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data)
    {
        if(resultCode == RESULT_OK)
        {
            switch (requestCode)
            {
                case REQUEST_BLE_DEVICE:
                    selectedBleDevice = data.getStringExtra("hr_ble_device_mac");
                    tvBleDevice.setText(data.getStringExtra("hr_ble_device_name") + " [" + selectedBleDevice + "]");
                    bleSelected = true;
                    break;
                case REQUEST_WRIST_DEVICE:
                    selectedWristDevice = data.getStringExtra("hr_ble_device_mac");
                    tvWristDevice.setText(data.getStringExtra("hr_ble_device_name") + " [" + selectedWristDevice + "]");
                    wristSelected = true;
                    break;
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        if(mainHandler == null) mainHandler = new MyHandler(this);
            else mainHandler.setTarget(this);

        llAnt = findViewById(R.id.llAnt);
        llBle = findViewById(R.id.llBle);
        swHrDeviceType = findViewById(R.id.swHrDeviceType);
        swAutostart = findViewById(R.id.swAutostart);
        swUseHr = findViewById(R.id.swUseHr);
        btnSelectAnt = findViewById(R.id.btnSelectAnt);
        btnSelectBle = findViewById(R.id.btnSelectBle);
        btnSelectWrist = findViewById(R.id.btnSelectWrist);
        btnStart = findViewById(R.id.btnStart);
        tvAntDevice = findViewById(R.id.tvAntDevice);
        tvBleDevice = findViewById(R.id.tvBleDevice);
        tvWristDevice = findViewById(R.id.tvWristDevice);
        tvAntDev = findViewById(R.id.tvAntDev);
        tvBleDev = findViewById(R.id.tvBleDev);
        tvHrStatus = findViewById(R.id.tvHrStatus);
        tvHeartRate = findViewById(R.id.tvHeartRate);
        tvStatus = findViewById(R.id.tvStatus);
        tvTime = findViewById(R.id.tvTime);
        tvDist = findViewById(R.id.tvDist);

        swUseHr.setOnClickListener(this);
        swHrDeviceType.setOnClickListener(this);
        swAutostart.setOnClickListener(this);
        btnSelectAnt.setOnClickListener(this);
        btnSelectBle.setOnClickListener(this);
        btnSelectWrist.setOnClickListener(this);
        btnStart.setOnClickListener(this);

        stravaData = new StravaNotifService.StravaData();
        //Intent intent=new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
        //startActivity(intent);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_DENIED)
        {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION},REQUEST_PERMISSION_LOCATION);
        }
        else
        {
            permissionLocationGranted = true;
        }
        String antPluginVersion = AntPluginPcc.getInstalledPluginsVersionString(this);
        if(antPluginVersion == null)
        {
            tvStatus.setText(R.string.ant_not_installed);
            btnSelectAnt.setEnabled(false);
            antSupported = false;
        }
        else
        {
            tvStatus.setText(getString(R.string.ant_version)+" "+antPluginVersion);
            antSupported = true;
            btnSelectAnt.setEnabled(true);
        }
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
        {
            tvStatus.append("\n"+getString(R.string.ble_not_supported));
            btnSelectBle.setEnabled(false);
            btnSelectWrist.setEnabled(false);
            bleSupported = false;
        }
        else
        {
            tvStatus.append("\n"+getString(R.string.ble_supported));
            bleSupported = true;
            final BluetoothManager bluetoothManager =
                    (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();
            btnSelectBle.setEnabled(true);
            btnSelectWrist.setEnabled(true);
        }
        if(!antSupported && !bleSupported) swHrDeviceType.setEnabled(false);
        loadSettings();//LOAD SETTINGS
        swAutostart.setChecked(isAutostart);
        swUseHr.setChecked(isHrEnabled);
        swHrDeviceType.setChecked(antUsed);
        if(!isHrEnabled)
        {
            btnSelectBle.setEnabled(false);
            btnSelectAnt.setEnabled(false);
        }
        if(antUsed)
        {
            llBle.setVisibility(View.GONE);
            llAnt.setVisibility(View.VISIBLE);
        }
        else
        {
            llBle.setVisibility(View.VISIBLE);
            llAnt.setVisibility(View.GONE);
        }
        if(selectedAntDeviceNumber != 0)
        {
            antSelected = true;
            tvAntDevice.setText(String.valueOf(selectedAntDeviceNumber));
        }
        if(!selectedBleDevice.isEmpty())
        {
            bleSelected = true;
            tvBleDevice.setText(selectedBleDevice);
        }
        if(!selectedWristDevice.isEmpty())
        {
            wristSelected = true;
            tvWristDevice.setText(selectedWristDevice);
        }
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O)
        {
            NotificationChannel channel = new NotificationChannel("Notifications", "Default",
                    NotificationManager.IMPORTANCE_DEFAULT);
            channel.enableVibration(false);
            channel.enableLights(false);
            nm.createNotificationChannel(channel);
        }
        swAutostart.setChecked(isAutostart);
        if(isAutostart)
        {
            btnStart.callOnClick();
        }
    }

    @Override
    public void onClick(View v)
    {
        switch(v.getId())
        {
            case R.id.swUseHr:
            {
                if (swUseHr.isChecked())
                {
                    btnSelectAnt.setEnabled(true);
                    btnSelectBle.setEnabled(true);
                    swHrDeviceType.setEnabled(true);
                    isHrEnabled = true;
                }
                else
                {
                    btnSelectAnt.setEnabled(false);
                    btnSelectBle.setEnabled(false);
                    swHrDeviceType.setEnabled(false);
                    isHrEnabled = false;
                }
                break;
            }
            case R.id.swHrDeviceType:
            {
                if (swHrDeviceType.isChecked())
                {
                    if(antSupported)
                    {
                        btnSelectAnt.setEnabled(true);
                        llAnt.setVisibility(View.VISIBLE);
                        btnSelectBle.setEnabled(false);
                        llBle.setVisibility(View.GONE);
                        antUsed = true;
                        bleUsed = false;
                    }
                }
                else
                {
                    if(bleSupported)
                    {
                        btnSelectAnt.setEnabled(false);
                        llAnt.setVisibility(View.GONE);
                        btnSelectBle.setEnabled(true);
                        llBle.setVisibility(View.VISIBLE);
                        antUsed = false;
                        bleUsed = true;
                    }
                }
                break;
            }
            case R.id.swAutostart:
            {
                isAutostart = swAutostart.isChecked();
                break;
            }
            case R.id.btnSelectBle:
            {
                if(permissionLocationGranted)
                {
                    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled())
                    {
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                    }
                    else
                    {
                        Intent intent = new Intent(this, BleScan.class);
                        intent.putExtra("dev_type",1);
                        startActivityForResult(intent, REQUEST_BLE_DEVICE);
                    }
                }
                else
                {
                    Toast.makeText(this, getString(R.string.str_permission_location), Toast.LENGTH_LONG).show();
                }
                break;
            }
            case R.id.btnSelectWrist:
            {
                if(permissionLocationGranted)
                {
                    if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled())
                    {
                        Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                        startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
                    }
                    else
                    {
                        Intent intent = new Intent(this, BleScan.class);
                        intent.putExtra("dev_type",2);
                        startActivityForResult(intent, REQUEST_WRIST_DEVICE);
                    }
                }
                else
                {
                    Toast.makeText(this, getString(R.string.str_permission_location), Toast.LENGTH_LONG).show();
                }
                break;
            }
            case R.id.btnSelectAnt:
            {
                pccReleaseHandle = AntPlusHeartRatePcc.requestAccess(MainActivity.this,this,true,0,pluginAccessResultReceiver,deviceStateChangeReceiver);
                //pccReleaseHandle.close();
                break;
            }
            case R.id.btnStart:
            {
                if(wristSelected)
                {
                    if(isHrEnabled && (antUsed && antSelected || bleUsed && bleSelected))
                    {
                        cmdSetCfg[1] = 0x07;
                        if (!isServiceRun)
                        {
                            isServiceRun = true;
                            Intent intent = new Intent(this, HrService.class);
                            if (antUsed && antSelected)
                                intent.putExtra("hr_ant_device_number", selectedAntDeviceNumber);
                            else intent.putExtra("hr_ant_device_number", 0);
                            if (bleUsed && bleSelected)
                                intent.putExtra("hr_ble_device_address", selectedBleDevice);
                            else intent.putExtra("hr_ble_device_address", "");
                            startService(intent);
                        }
                        else
                        {
                            stopService(new Intent(this, HrService.class));
                            isServiceRun = false;
                        }
                    }
                    else
                    {
                        cmdSetCfg[1] = 0x06;
                    }
                    if(!isStarted)
                    {
                        btnStart.setText("Stop");
                        btnSelectAnt.setEnabled(false);
                        btnSelectBle.setEnabled(false);
                        btnSelectWrist.setEnabled(false);
                        swHrDeviceType.setEnabled(false);
                        swAutostart.setEnabled(false);
                        swUseHr.setEnabled(false);
                        initDisplay();
                        bleWristDevice = bluetoothAdapter.getRemoteDevice(selectedWristDevice);
                        bleWristGatt = bleWristDevice.connectGatt(this, false, bleGattCallback);
                        isStarted = true;
                        wristTimer = new Timer();
                    }
                    else
                    {
                        btnStart.setText("Start");
                        isStarted = false;
                        btnSelectWrist.setEnabled(true);
                        swUseHr.setEnabled(true);
                        if(isHrEnabled) swHrDeviceType.setEnabled(true);
                        swAutostart.setEnabled(true);
                        if (antSupported)
                        {
                            btnSelectAnt.setEnabled(true);
                        }
                        if (bleSupported)
                        {
                            btnSelectBle.setEnabled(true);
                        }
                        if (bleWristGatt != null)
                        {
                            bleWristGatt.close();
                            bleWristGatt = null;
                        }
                        if (wristTimer != null)
                        {
                            wristTimer.cancel();
                            wristTimer = null;
                        }
                    }
                }
                break;
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults)
    {
        switch (requestCode)
        {
            case REQUEST_PERMISSION_EXT_STORAGE:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    permissionExtStorageGranted = true;
                }
                else
                {
                    permissionExtStorageGranted = false;
                }
                return;
            case REQUEST_PERMISSION_LOCATION:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                {
                    permissionLocationGranted = true;
                }
                else
                {
                    permissionLocationGranted = false;
                }
                return;
        }
    }

    protected AntPluginPcc.IPluginAccessResultReceiver<AntPlusHeartRatePcc> pluginAccessResultReceiver =
            new AntPluginPcc.IPluginAccessResultReceiver<AntPlusHeartRatePcc>()
            {
                @Override
                public void onResultReceived(AntPlusHeartRatePcc antPlusHeartRatePcc, RequestAccessResult requestAccessResult, DeviceState deviceState)
                {
                    antSelected = false;
                    switch(requestAccessResult)
                    {
                        case SUCCESS:
                            tvAntDevice.setText(" " + antPlusHeartRatePcc.getDeviceName() + " [" + antPlusHeartRatePcc.getAntDeviceNumber() + "]");
                            selectedAntDeviceNumber = antPlusHeartRatePcc.getAntDeviceNumber();
                            handleReset();
                            antSelected = true;
                            break;
                        case SEARCH_TIMEOUT:
                            Toast.makeText(MainActivity.this, getString(R.string.str_timout), Toast.LENGTH_LONG).show();
                            break;
                        case CHANNEL_NOT_AVAILABLE:
                            Toast.makeText(MainActivity.this, getString(R.string.str_channel_not_available), Toast.LENGTH_SHORT).show();
                            break;
                        case ADAPTER_NOT_DETECTED:
                            Toast.makeText(MainActivity.this, getString(R.string.str_ant_adapter_not_available), Toast.LENGTH_SHORT).show();
                            break;
                        case BAD_PARAMS:
                            Toast.makeText(MainActivity.this, getString(R.string.str_bad_parameters), Toast.LENGTH_SHORT).show();
                            break;
                        case OTHER_FAILURE:
                            Toast.makeText(MainActivity.this, getString(R.string.str_other_failure), Toast.LENGTH_SHORT).show();
                            break;
                        case DEPENDENCY_NOT_INSTALLED:
                            Toast.makeText(MainActivity.this, getString(R.string.str_missing_dependensy), Toast.LENGTH_SHORT).show();
                            break;
                        case USER_CANCELLED:
                            break;
                        case UNRECOGNIZED:
                            Toast.makeText(MainActivity.this, getString(R.string.str_unrecognized), Toast.LENGTH_SHORT).show();
                            break;
                        default:
                            Toast.makeText(MainActivity.this, getString(R.string.str_unrecognized_other) + " " + requestAccessResult, Toast.LENGTH_SHORT).show();
                            break;
                    }
                }
            };

    protected  AntPluginPcc.IDeviceStateChangeReceiver deviceStateChangeReceiver =
            new AntPluginPcc.IDeviceStateChangeReceiver()
            {
                @Override
                public void onDeviceStateChange(final DeviceState newDeviceState)
                {
                }
            };

    void initDisplay()
    {
        tvHrStatus.setText(R.string.str_empty);
        tvHrStatus.setText(R.string.str_empty);
        tvHeartRate.setText(R.string.str_empty);
        tvHeartRate.setText(R.string.str_empty);
    }

    @Override
    protected void onDestroy()
    {
        handleReset();
        stopService(new Intent(this, HrService.class));
        super.onDestroy();
    }

    @Override
    protected void onPause()
    {
        saveSettings();
        super.onPause();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        switch(keyCode)
        {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                return true;

             default:
                 return false;
        }
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event)
    {
        switch(keyCode)
        {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                return true;

            default:
                return false;
        }
    }

    protected void handleReset()
    {
        if(pccReleaseHandle != null)
        {
            pccReleaseHandle.close();
        }
        if(bluetoothAdapter != null)
        {
            bluetoothAdapter = null;
        }
    }

    class WristTimerTask extends TimerTask
    {

        @Override
        public void run()
        {

        }
    }

    private final BluetoothGattCallback bleGattCallback = new BluetoothGattCallback()
    {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
        {
            if(newState == BluetoothProfile.STATE_CONNECTED)
            {
                gatt.discoverServices();
            }
            else if(newState == BluetoothProfile.STATE_DISCONNECTED)
            {
                gatt.connect();
            }
        }

        @Override
        public void onCharacteristicWrite (BluetoothGatt gatt,BluetoothGattCharacteristic characteristic,int status)
        {
            Log.d("GFX", "onCharacteristicWrite: " + status);
            /*if((status != BluetoothGatt.GATT_SUCCESS) && (characteristic.getUuid().compareTo(UUID.fromString(WRIST_CHARACTERISTIC))==0))
            {
                characteristic.setValue(cmdSetCfg);
                gatt.writeCharacteristic(characteristic);
            }*/
        }

        @Override
        public void onDescriptorWrite (BluetoothGatt gatt,BluetoothGattDescriptor descriptor,int status)
        {
            if((status == BluetoothGatt.GATT_SUCCESS) && (descriptor.getUuid().compareTo(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG))==0))
            {
                wristCharacteristic.setValue(cmdSetCfg);
                gatt.writeCharacteristic(wristCharacteristic);
                //wristTimerTask = new WristTimerTask();
                //wristTimer.schedule(wristTimerTask, 500, 1000);
            }
        }

        @Override
        public void onCharacteristicChanged (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)
        {
            if(characteristic.getUuid().compareTo(UUID.fromString(WRIST_CHARACTERISTIC)) == 0)
            {
                int cmd = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                if(cmd == 0x04)
                {
                    int key = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 1);
                    if (key == 2)
                    {
                        if (isRecordRun)
                        {
                            Intent stravaIntent = new Intent("com.strava.service.StravaActivityService.PAUSE");
                            sendBroadcast(stravaIntent);
                        }
                        else
                        {
                            Intent stravaIntent = new Intent("com.strava.service.StravaActivityService.RESUME");
                            sendBroadcast(stravaIntent);
                        }
                    }
                }
            }
        }

        @Override
        public void onServicesDiscovered (BluetoothGatt gatt, int status)
        {
            if(status == BluetoothGatt.GATT_SUCCESS)
            {
                wristService = gatt.getService(UUID.fromString(WRIST_SERVICE));
                wristCharacteristic = wristService.getCharacteristic(UUID.fromString(WRIST_CHARACTERISTIC));
                gatt.setCharacteristicNotification(wristCharacteristic, true);
                BluetoothGattDescriptor descriptor = wristCharacteristic.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }
        }
    };

    void saveSettings()
    {
        sPref = getPreferences(MODE_PRIVATE);
        SharedPreferences.Editor ed = sPref.edit();
        ed.putString("ble_mac", selectedBleDevice);
        ed.putInt("ant_num", selectedAntDeviceNumber);
        ed.putString("wrist_mac", selectedWristDevice);
        ed.putBoolean("autostart", isAutostart);
        ed.putBoolean("use_hr", isHrEnabled);
        ed.putBoolean("use_ant_ble", swHrDeviceType.isChecked());//checked - ANT+, unchecked - BLE
        ed.commit();
    }

    void loadSettings()
    {
        sPref = getPreferences(MODE_PRIVATE);
        selectedBleDevice = sPref.getString("ble_mac", "");
        selectedAntDeviceNumber = sPref.getInt("ant_num", 0);
        selectedWristDevice = sPref.getString("wrist_mac", "");
        isAutostart = sPref.getBoolean("autostart", false);
        isHrEnabled = sPref.getBoolean("use_hr", false);
        antUsed = sPref.getBoolean("use_ant_ble", true);
        bleUsed = !antUsed;
    }

    static String timeConvert(long seconds)
    {
        int h, m, s;
        String time;

        h=(int)((long)seconds/3600);
        m=(int)(((long)seconds%3600)/60);
        s=(int)((long)seconds%60);
        time = String.format("%02d:%02d:%02d",h,m,s);
        return time;
    }
}
