package com.kit.scanimal.scanimal;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbRequest;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.TextView;

import java.nio.ByteBuffer;

/**
 * Created by semtad on 2015-02-19.
 */
public class RFIDUsbManager {

    private static final String TAG = "UsbManager";

    private UsbManager mUsbManager;
    private UsbDevice mDevice;
    private UsbDeviceConnection mConnection;
    private UsbEndpoint mEndpointIntr;
    UsbInterface mUsbInterface;

    private Context mContext;

    public RFIDUsbManager(Context context) {
        mContext = context;

        updateUI("Running constructor...");

        mUsbManager = (UsbManager)mContext.getSystemService(Context.USB_SERVICE);

        Intent intent = ((Activity) mContext).getIntent();
        String action = intent.getAction();
        UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

        if (device != null) {
            updateUI("A device is connected. id: " + device.getDeviceId());
        }

        if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
            //setDevice(device);
            updateUI("Device attached");
        } else if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
            if (mDevice != null && mDevice.equals(device)) {
                //setDevice(null);
                updateUI("Device detached");
            }
        }

        new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                String action = intent.getAction();

                if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(action)) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device != null && device == mDevice) {
                        // call your method that cleans up and closes communication with the device
                        if (mConnection != null) {
                            mConnection.close();
                            Log.i(TAG, "Closing connection to USB device.");
                        }
                        if (mUsbInterface !=null) {
                            boolean result = mConnection.releaseInterface(mUsbInterface);
                            Log.i(TAG, "Released interface, success: " + result);
                        }

                        updateUI("Device detached");
                    }
                }
                if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(action)) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (device != null) {
                        //setDevice(device);
                        updateUI("Device attached");
                    }
                }
            }
        };
    }

    public String sendCommand(String command) {
        //synchronized (this) {
        ByteBuffer buffer = ByteBuffer.allocate(command.length());
        UsbRequest request = new UsbRequest();
        request.initialize(mConnection, mEndpointIntr);

        // queue a request on the interrupt endpoint
        request.queue(buffer, command.length());

        if (mConnection != null) {
            byte[] message = command.getBytes();
            // Send command via a control request on endpoint zero
            mConnection.controlTransfer(0x21, 0x9, 0x200, 0, message, message.length, 0);

            if (mConnection.requestWait() == request) {
                return buffer.toString();
            } else {
                return "No result...";
            }
        }

        return "mConnection is null ??";
    }

    private void setDevice(UsbDevice device) {
        Log.d(TAG, "setDevice " + device);
        if (device.getInterfaceCount() != 1) {
            Log.e(TAG, "could not find interface");
            return;
        }
        mUsbInterface = device.getInterface(0);
        // device should have one endpoint
        if (mUsbInterface.getEndpointCount() != 1) {
            Log.e(TAG, "could not find endpoint");
            return;
        }
        // endpoint should be of type interrupt
        UsbEndpoint ep = mUsbInterface.getEndpoint(0);
        if (ep.getType() != UsbConstants.USB_ENDPOINT_XFER_INT) {
            Log.e(TAG, "endpoint is not interrupt type");
            return;
        }
        mDevice = device;
        mEndpointIntr = ep;
        if (device != null) {
            UsbDeviceConnection connection = mUsbManager.openDevice(device);
            if (connection != null && connection.claimInterface(mUsbInterface, true)) {
                Log.d(TAG, "open SUCCESS");
                mConnection = connection;
//                Thread thread = new Thread(this);
//                thread.start();

            } else {
                Log.d(TAG, "open FAIL");
                mConnection = null;
            }
        }
    }

    private void updateUI(String message) {
        Intent intent = new Intent("USB");
        // You can also include some extra data.
        intent.putExtra("message", message);
        LocalBroadcastManager.getInstance(mContext).sendBroadcast(intent);
    }
}
