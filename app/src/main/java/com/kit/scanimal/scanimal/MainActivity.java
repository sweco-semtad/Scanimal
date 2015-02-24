package com.kit.scanimal.scanimal;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.ActionBarActivity;
import android.support.v4.app.Fragment;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Iterator;


public class MainActivity extends ActionBarActivity {

    String TAG = "MainActivity";

    Context context;

    MainFragment mainFragment;

    private UsbManager mUsbManager;

    TextView mTextView;

    PendingIntent mPermissionIntent;

    private UsbDevice mDevice;
    private UsbInterface mUsbInterface;
    private UsbEndpoint mEndpointIntr;
    private UsbDeviceConnection mConnection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mainFragment =  MainFragment.newInstance();

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.container, mainFragment)
                    .commit();
        }

        context = getApplicationContext();

        mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        registerReceiver(mUsbReceiver, filter);

        mUsbManager = (UsbManager)getSystemService(Context.USB_SERVICE);
    }

    @Override
    protected void onPause() {
        // Unregister since the activity is paused.
        LocalBroadcastManager.getInstance(this).unregisterReceiver(
                mMessageReceiver);
        super.onPause();
    }

    @Override
    protected void onResume() {
        // Register to receive messages.
        // We are registering an observer (mMessageReceiver) to receive Intents
        // with actions named "custom-event-name".
        LocalBroadcastManager.getInstance(this).registerReceiver(
                mMessageReceiver, new IntentFilter("USB"));

        mTextView = (TextView)findViewById(R.id.textViewMain);

        appendText("App resumed");

        if (mDevice == null) {
            HashMap<String, UsbDevice> deviceList = mUsbManager.getDeviceList();

            if (deviceList.size() > 0) {
                Iterator<UsbDevice> deviceIterator = deviceList.values().iterator();
                while (deviceIterator.hasNext()) {
                    UsbDevice device = deviceIterator.next();
                    if (device.getVendorId() == 1027 && device.getProductId() == 24577) {
                        mDevice = device;
                        appendText("Device already attached, trying to connect.");
                        TryConnect(device);
                    }
                }
            }
        }

        super.onResume();
    }

    // Our handler for received Intents. This will be called whenever an Intent
    // with an action named "custom-event-name" is broadcasted.
    private BroadcastReceiver mMessageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            // Get extra data included in the Intent
            String message = intent.getStringExtra("message");
            appendText(message);
            Log.d("receiver", "Got message: " + message);
        }
    };

    private static final String ACTION_USB_PERMISSION =
            "com.kit.scanimal.scanimal.USB_PERMISSION";
    private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null && mUsbManager.hasPermission(device)){
                            //call method to set up device communication
                            appendText("Got permission to connect to device.");
                            ConnectToDevice(device);
                        }
                    }
                    else {
                        appendText("Permission denied for device.");
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
            else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    appendText("Device detached, cleaning up.");
                    CleanUpUSB(device);
                }
            }
            else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                if (device != null) {
                    TryConnect(device);
                }
            }
            else {
                appendText("Got broadcast but wrong action: " + action);
            }
        }
    };

    private void TryConnect(UsbDevice device) {
        if (mUsbManager.hasPermission(device)) {
            appendText("Already has permission to connect.");
            ConnectToDevice(device);
        } else {
            appendText("No permission so requesting it.");
            mUsbManager.requestPermission(device, mPermissionIntent);
        }
    }

    private void ConnectToDevice(UsbDevice device) {
        try {
            mDevice = device;

            int interfaceNum = 0;
            int endpointNum = 0;

            appendText("Interface count: " + device.getInterfaceCount());

            mUsbInterface = device.getInterface(interfaceNum);
            mEndpointIntr = mUsbInterface.getEndpoint(endpointNum);

            UsbDeviceConnection connection = mUsbManager.openDevice(device);

            if (connection != null && connection.claimInterface(mUsbInterface, true)) {
                appendText("Claim interface " + interfaceNum + " endpoint " + endpointNum + " OK!!");
                mConnection = connection;
            } else {
                appendText("Claim interface " + interfaceNum + " and endpoint " + endpointNum + " not OK");
                String conNull = connection == null ? "null" : "not null";
                appendText("connection is " + conNull);
            }

            /* Setting up the protocol */
            if (mConnection != null) {
                mConnection.controlTransfer(0x40, 0, 0, 0, null, 0, 0);// reset
                // mConnection.controlTransfer(0×40,
                // 0, 1, 0, null, 0,
                // 0);//clear Rx
                mConnection.controlTransfer(0x40, 0, 2, 0, null, 0, 0); // clear Tx
                mConnection.controlTransfer(0x40, 0x02, 0x0000, 0, null, 0, 0); // flow control none
                mConnection.controlTransfer(0x40, 0x03, 0x4138, 0, null, 0, 0); // baud rate 9600
                mConnection.controlTransfer(0x40, 0x04, 0x0008, 0, null, 0, 0); // data bit 8, parity none, stop bit 1, tx off

                UsbEndpoint epIN = null;
                UsbEndpoint epOUT = null;

                UsbInterface usbIf = device.getInterface(0);
                for (int i = 0; i < usbIf.getEndpointCount(); i++) {
                    appendText("EP: " + String.format("0x%02X", usbIf.getEndpoint(i).getAddress()));
                    if (usbIf.getEndpoint(i).getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                        Log.d(TAG, "Bulk Endpoint");
                        if (usbIf.getEndpoint(i).getDirection() == UsbConstants.USB_DIR_IN) {
                            epIN = usbIf.getEndpoint(i);
                            appendText("Found endpoint IN (" + i + ")");
                        }
                        else {
                            epOUT = usbIf.getEndpoint(i);
                            appendText("Found endpoint OUT (" + i + ")");
                        }
                    } else {
                        appendText("Not Bulk");
                    }
                }

                // Testcode
                appendText("Sending command");
                String responce = sendCommand("SD2\r", epIN, epOUT);
                appendText("Command result: \"" + responce + "\"");
            }
        } catch (Exception ex) {
            appendText("Exception: " + ex.getMessage());
        }
    }

    public String sendCommand(String command, UsbEndpoint epIn, UsbEndpoint epOut) {
        synchronized (this) {

            UsbRequest request = new UsbRequest();
            request.initialize(mConnection, epIn);

            ByteBuffer buffer = ByteBuffer.allocate(4096);

            // queue a request on the interrupt endpoint
            request.queue(buffer, command.length());

            if (mConnection != null) {
                byte[] message = command.getBytes(Charset.forName("US_ASCII"));

                int out = mConnection.bulkTransfer(epOut, message, message.length, 0);

                appendText("Bulk transfer result: " + out);

                if (mConnection.requestWait() == request) {
                    return new String(buffer.array(), Charset.forName("US_ASCII"));
                } else {
                    return "No result...";
                }
            }
        }

        return "mConnection is null ??";
    }

    private void CleanUpUSB(UsbDevice device) {
        if (device == mDevice) {
            mConnection.releaseInterface(mUsbInterface);
            mConnection.close();

            mDevice = null;
            mConnection = null;
            mUsbInterface = null;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
//    public static class PlaceholderFragment extends Fragment {
//
//        public PlaceholderFragment() {
//        }
//
//        @Override
//        public View onCreateView(LayoutInflater inflater, ViewGroup container,
//                                 Bundle savedInstanceState) {
//            View rootView = inflater.inflate(R.layout.fragment_main, container, false);
//            return rootView;
//        }
//    }

    private void appendText(String message) {
        mTextView.setText(mTextView.getText() + "\n" + message);
    }
}
