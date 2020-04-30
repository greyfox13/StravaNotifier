package com.github.greyfox13.stravanotifier;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.os.IBinder;
import android.os.Message;
import android.util.Log;
import android.widget.Toast;

import com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc;
import com.dsi.ant.plugins.antplus.pcc.defines.DeviceState;
import com.dsi.ant.plugins.antplus.pcc.defines.EventFlag;
import com.dsi.ant.plugins.antplus.pcc.defines.RequestAccessResult;
import com.dsi.ant.plugins.antplus.pccbase.AntPluginPcc;
import com.dsi.ant.plugins.antplus.pccbase.PccReleaseHandle;
import com.github.greyfox13.stravanotifier.R;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.EnumSet;
import java.util.UUID;

import androidx.core.app.NotificationCompat;

import static com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc.RrFlag.DATA_SOURCE_CACHED;
import static com.dsi.ant.plugins.antplus.pcc.AntPlusHeartRatePcc.RrFlag.DATA_SOURCE_PAGE_4;

public class HrService extends Service
{
    AntPlusHeartRatePcc hrPcc = null;
    PccReleaseHandle<AntPlusHeartRatePcc> releaseHandle = null;
    BluetoothAdapter bluetoothAdapter;
    BluetoothDevice bleHrDevice;
    BluetoothGatt bleGatt;
    Message msg;

    long heartBeatCountPrev = 0;

    NotificationManager nm;

    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;

    final static String HEART_RATE_SERVICE = "0000180D-0000-1000-8000-00805F9B34FB";
    final static String HEART_RATE_CHARACTERISTIC = "00002A37-0000-1000-8000-00805F9B34FB";
    final static String CLIENT_CHARACTERISTIC_CONFIG = "00002902-0000-1000-8000-00805F9B34FB";

    @Override
    public void onCreate()
    {
        super.onCreate();
        nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        Notification notif = new NotificationCompat.Builder(this,"Notifications")
                .setContentTitle("Strava notifier")
                .setSmallIcon(R.drawable.ic_service)
                .build();
        startForeground(MainActivity.SERVICE_NOTIFY_ID, notif);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId)
    {
        int antDeviceNum;
        String bleDeviceAddress;

        antDeviceNum = intent.getIntExtra("hr_ant_device_number",0);
        bleDeviceAddress = intent.getStringExtra("hr_ble_device_address");
        if (antDeviceNum > 0)
        {
            requestConnectToResult(antDeviceNum);
        }
        if(!bleDeviceAddress.isEmpty())
        {
            final BluetoothManager bluetoothManager =
                    (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
            bluetoothAdapter = bluetoothManager.getAdapter();
            bleHrDevice = bluetoothAdapter.getRemoteDevice(bleDeviceAddress);
            bleGatt = bleHrDevice.connectGatt(this, true, bleGattCallback);
        }

        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy()
    {
        if(releaseHandle != null) releaseHandle.close();
        if (bleGatt != null)
        {
            bleGatt.close();
            bleGatt = null;
        }
        stopForeground(true);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent)
    {
        return null;
    }

    protected void requestConnectToResult(final int antDeviceNum)
    {
         releaseHandle = AntPlusHeartRatePcc.requestAccess(this,antDeviceNum,0,
              new AntPluginPcc.IPluginAccessResultReceiver<AntPlusHeartRatePcc>()
                  {
                      @Override
                      public void onResultReceived(AntPlusHeartRatePcc result,
                                                   RequestAccessResult resultCode, DeviceState initialDeviceState)
                      {
                           if(resultCode == RequestAccessResult.SEARCH_TIMEOUT)
                           {
                               //On a connection timeout the scan automatically resumes, so we inform the user, and go back to scanning
                           }
                           else
                           {
                               //Otherwise the results, including SUCCESS, behave the same as
                               pluginAccessResultReceiver.onResultReceived(result, resultCode, initialDeviceState);
                           }
                      }
                  }, deviceStateChangeReceiver);
    }

    protected AntPluginPcc.IPluginAccessResultReceiver<AntPlusHeartRatePcc> pluginAccessResultReceiver =
            new AntPluginPcc.IPluginAccessResultReceiver<AntPlusHeartRatePcc>()
            {
                @Override
                public void onResultReceived(AntPlusHeartRatePcc antPlusHeartRatePcc, RequestAccessResult requestAccessResult, DeviceState deviceState)
                {
                    switch(requestAccessResult)
                    {
                        case SUCCESS:
                            msg=MainActivity.mainHandler.obtainMessage(MainActivity.ANT_SET_STATUS,0,0,deviceState);
                            MainActivity.mainHandler.sendMessage(msg);
                            hrPcc=antPlusHeartRatePcc;
                            subscribeToHrEvents();
                            break;
                        case CHANNEL_NOT_AVAILABLE:
                            Toast.makeText(HrService.this, getString(R.string.str_channel_not_available), Toast.LENGTH_SHORT).show();
                            break;
                        case ADAPTER_NOT_DETECTED:
                            Toast.makeText(HrService.this, getString(R.string.str_ant_adapter_not_available), Toast.LENGTH_SHORT).show();
                            break;
                        case BAD_PARAMS:
                            Toast.makeText(HrService.this, getString(R.string.str_bad_parameters), Toast.LENGTH_SHORT).show();
                            break;
                        case OTHER_FAILURE:
                            Toast.makeText(HrService.this, getString(R.string.str_other_failure), Toast.LENGTH_SHORT).show();
                            break;
                        case DEPENDENCY_NOT_INSTALLED:
                            Toast.makeText(HrService.this, getString(R.string.str_missing_dependensy), Toast.LENGTH_SHORT).show();
                            break;
                        case USER_CANCELLED:
                            break;
                        case UNRECOGNIZED:
                            Toast.makeText(HrService.this, getString(R.string.str_unrecognized), Toast.LENGTH_SHORT).show();
                            break;
                        default:
                            Toast.makeText(HrService.this, getString(R.string.str_unrecognized_other) + " " + requestAccessResult, Toast.LENGTH_SHORT).show();
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
                    msg=MainActivity.mainHandler.obtainMessage(MainActivity.ANT_SET_STATUS,0,0,newDeviceState);
                    MainActivity.mainHandler.sendMessage(msg);
                }
            };

    public void subscribeToHrEvents()
    {
        hrPcc.subscribeHeartRateDataEvent(new AntPlusHeartRatePcc.IHeartRateDataReceiver()
        {
            @Override
            public void onNewHeartRateData(final long estTimestamp, EnumSet<EventFlag> eventFlags,
                                           final int computedHeartRate, final long heartBeatCount,
                                           final BigDecimal heartBeatEventTime, final AntPlusHeartRatePcc.DataState dataState)
            {
                // Mark heart rate with asterisk if zero detected
                final String textHeartRate = String.valueOf(computedHeartRate)
                        + ((AntPlusHeartRatePcc.DataState.ZERO_DETECTED.equals(dataState)) ? "*" : "");

                // Mark heart beat count and heart beat event time with asterisk if initial value
                final String textHeartBeatCount = String.valueOf(heartBeatCount)
                        + ((AntPlusHeartRatePcc.DataState.INITIAL_VALUE.equals(dataState)) ? "*" : "");
                final String textHeartBeatEventTime = String.valueOf(heartBeatEventTime)
                        + ((AntPlusHeartRatePcc.DataState.INITIAL_VALUE.equals(dataState)) ? "*" : "");
                if(!AntPlusHeartRatePcc.DataState.ZERO_DETECTED.equals(dataState))
                {
                    msg = MainActivity.mainHandler.obtainMessage(MainActivity.ANT_SET_HEARTRATE, computedHeartRate, 0);
                    MainActivity.mainHandler.sendMessage(msg);
                }
            }
        });

        hrPcc.subscribeCalculatedRrIntervalEvent(new AntPlusHeartRatePcc.ICalculatedRrIntervalReceiver()
        {
            @Override
            public void onNewCalculatedRrInterval(final long estTimestamp,
                                                  final EnumSet<EventFlag> eventFlags, final BigDecimal rrInterval, final AntPlusHeartRatePcc.RrFlag flag)
            {
                if (flag.equals(DATA_SOURCE_CACHED) || flag.equals(DATA_SOURCE_PAGE_4))
                {
                    //RR INT
                }
            }
        });

    }

    private final BluetoothGattCallback bleGattCallback = new BluetoothGattCallback()
    {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState)
        {
            if(newState == BluetoothProfile.STATE_CONNECTED) gatt.discoverServices();
                else if(newState == BluetoothProfile.STATE_DISCONNECTED) gatt.connect();
            msg = MainActivity.mainHandler.obtainMessage(MainActivity.BLE_SET_STATUS, newState,0);
            MainActivity.mainHandler.sendMessage(msg);
        }

        @Override
        public void onCharacteristicChanged (BluetoothGatt gatt, BluetoothGattCharacteristic characteristic)
        {
            if(characteristic.getUuid().compareTo(UUID.fromString(HEART_RATE_CHARACTERISTIC)) == 0)
            {
                int flag = characteristic.getProperties();
                int format = -1;
                if ((flag & 0x01) != 0)
                {
                    format = BluetoothGattCharacteristic.FORMAT_UINT16;
                }
                else
                {
                    format = BluetoothGattCharacteristic.FORMAT_UINT8;
                }
                int heartRate = characteristic.getIntValue(format, 1);
                msg = MainActivity.mainHandler.obtainMessage(MainActivity.BLE_SET_HEARTRATE_RR, heartRate, 0);
                MainActivity.mainHandler.sendMessage(msg);
            }
        }

        @Override
        public void onServicesDiscovered (BluetoothGatt gatt, int status)
        {
            BluetoothGattCharacteristic hrCharacteristic;
            BluetoothGattService hrService;

            if(status == BluetoothGatt.GATT_SUCCESS)
            {
                hrService = gatt.getService(UUID.fromString(HEART_RATE_SERVICE));
                hrCharacteristic = hrService.getCharacteristic(UUID.fromString(HEART_RATE_CHARACTERISTIC));
                gatt.setCharacteristicNotification(hrCharacteristic, true);
                BluetoothGattDescriptor descriptor = hrCharacteristic.getDescriptor(UUID.fromString(CLIENT_CHARACTERISTIC_CONFIG));
                descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                gatt.writeDescriptor(descriptor);
            }
        }

        @Override
        // Result of a characteristic read operation
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status)
        {
            if (status == BluetoothGatt.GATT_SUCCESS)
            {
            }
        }
    };
}
