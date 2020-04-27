package com.github.greyfox13.stravanotifier;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelUuid;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.github.greyfox13.stravanotifier.R;

import java.util.ArrayList;
import java.util.List;

import static android.bluetooth.le.ScanSettings.CALLBACK_TYPE_FIRST_MATCH;
import static android.bluetooth.le.ScanSettings.SCAN_MODE_BALANCED;

public class BleScan extends AppCompatActivity
{
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean scanningFlag;
    private Handler handler;

    ListView lvBleDevices;
    TextView tvScanHeader;

    ArrayList<BluetoothDevice> listDevicesBle;
    ArrayAdapter<String> adapterDevicesBle;

    String serviceUuid;

    private static final long SCAN_PERIOD = 10000;
    final static String HEART_RATE_SERVICE = "0000180D-0000-1000-8000-00805F9B34FB";
    final static String WRIST_SERVICE = "0000FF01-0000-1000-8000-00805F9B34FB";

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_scan);

        handler = new Handler();

        lvBleDevices = findViewById(R.id.lvBleDevices);
        tvScanHeader = findViewById(R.id.tvScanHeader);
        adapterDevicesBle = new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1);
        listDevicesBle = new ArrayList<BluetoothDevice>();

        final BluetoothManager bluetoothManager =
                (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        bluetoothAdapter = bluetoothManager.getAdapter();
        bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        lvBleDevices.setAdapter(adapterDevicesBle);
        Intent intent = getIntent();
        int devType = intent.getIntExtra("dev_type", 0);
        if(devType == 1) serviceUuid = HEART_RATE_SERVICE;
        else if(devType == 2) serviceUuid = WRIST_SERVICE;
        if(bluetoothAdapter.isEnabled()) scanLeDevice(true);

        lvBleDevices.setOnItemClickListener(new AdapterView.OnItemClickListener()
        {
            //Return the id of the selected already connected device
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int pos, long id)
            {
                Intent intent = new Intent();
                intent.putExtra("hr_ble_device_name",listDevicesBle.get(pos).getName());
                intent.putExtra("hr_ble_device_mac",listDevicesBle.get(pos).getAddress());
                setResult(RESULT_OK, intent);
                finish();
            }
        });
    }

    private void scanLeDevice(final boolean enable)
    {
        if(enable)
        {
            // Stops scanning after a pre-defined scan period.
            handler.postDelayed(new Runnable()
            {
                @Override
                public void run()
                {
                    tvScanHeader.setText(R.string.str_ble_scan_stop);
                    scanningFlag = false;
                    if(bluetoothAdapter.isEnabled()) bluetoothLeScanner.stopScan(leScanCallback);
                }
            }, SCAN_PERIOD);
            scanningFlag = true;
            List<ScanFilter> scanFilters = new ArrayList<ScanFilter>();
            ScanFilter.Builder scanFilterBuilder = new ScanFilter.Builder();
            ScanSettings.Builder scanSettingsBuilder = new ScanSettings.Builder();
            scanSettingsBuilder.setScanMode(SCAN_MODE_BALANCED);
            scanSettingsBuilder.setCallbackType(CALLBACK_TYPE_FIRST_MATCH);
            scanFilterBuilder.setServiceUuid(ParcelUuid.fromString(serviceUuid));
            scanFilters.add(scanFilterBuilder.build());
            bluetoothLeScanner.startScan(scanFilters,scanSettingsBuilder.build(),leScanCallback);
        }
        else
        {
            scanningFlag = false;
            if(bluetoothAdapter.isEnabled()) bluetoothLeScanner.stopScan(leScanCallback);
        }
    }

    private ScanCallback leScanCallback = new ScanCallback()
            {
                public void onScanResult(int callbackType, final ScanResult result)
                {
                    runOnUiThread(new Runnable()
                    {
                        @Override
                        public void run()
                        {
                            adapterDevicesBle.add(result.getDevice().getName() + " [" + result.getDevice().getAddress() + "]");
                            adapterDevicesBle.notifyDataSetChanged();
                            listDevicesBle.add(result.getDevice());
                        }
                    });
                }
            };

}
