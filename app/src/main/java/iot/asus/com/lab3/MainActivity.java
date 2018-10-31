package iot.asus.com.lab3;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.galarzaa.androidthings.Rc522;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.SpiDevice;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import iot.asus.com.lab3.R;

/**
 * Skeleton of an Android Things activity.
 * <p>
 * Android Things peripheral APIs are accessible through the class
 * PeripheralManagerService. For example, the snippet below will open a GPIO pin and
 * set it to HIGH:
 * <p>
 * <pre>{@code
 * PeripheralManagerService service = new PeripheralManagerService();
 * mLedGpio = service.openGpio("BCM6");
 * mLedGpio.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);
 * mLedGpio.setValue(true);
 * }</pre>
 * <p>
 * For more complex peripherals, look for an existing user-space driver, or implement one if none
 * is available.
 *
 * @see <a href="https://github.com/androidthings/contrib-drivers#readme">https://github.com/androidthings/contrib-drivers#readme</a>
 */
public class MainActivity extends Activity {
    private static final String TAG = MainActivity.class.getSimpleName();
    private static final String SPI_PORT = "SPI0.0";
    private static final String PIN_RESET = "BCM25";
    private static final String Green = "BCM16";
    private static final String Red = "BCM20";
    private static final String Blue = "BCM21";
    RfidTask mRfidTask;
    String resultsText = "";

    private TextView mTagUidView;
    private Button buttonRead;
    private TextView mTagResult;
    private Button buttonWrite;
    private TextView mTagWrite;

    private int countLed = 0;
    private boolean isRead = false;
    private boolean isWrite = false;
    private boolean checkIn = false;
    private boolean stateTemp = true;

    private Rc522 mRc522;
    private SpiDevice spiDevice;
    private Gpio gpioReset;
    private Gpio gpioCheck;
    private Gpio gpioArlet;
    private Gpio gpioDefault;
    private Handler mHandlerRFID = new Handler();

    private Runnable mRfid = new Runnable(){
        @Override
        public void run() {
            Log.d(TAG, "READ");
            isRead = true;
            asynTask();
        }
    };

    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            Log.d(TAG,"Enter led alert");
            if (gpioArlet == null) {
                // Exit Runnable if the GPIO is already closed
                return;
            }
            countLed++;
            if (countLed > 10) {
                countLed = 0;
                try {
                    gpioDefault.setValue(false);
                    gpioArlet.setValue(true);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            } else {
                try {
                    gpioDefault.setValue(true);
                    stateTemp = !stateTemp;
                    gpioArlet.setValue(stateTemp);
                    mHandlerRFID.postDelayed(mRunnable, 200);

                } catch (IOException e) {
                    Log.e(TAG, "Error on PeripheralIO API", e);
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "START");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTagUidView = (TextView) findViewById(R.id.textRead);
        mTagResult = (TextView) findViewById(R.id.textResult);
        mTagWrite = (TextView) findViewById(R.id.textWrite);

        PeripheralManager pioService = PeripheralManager.getInstance();

        try {
            //Names based on Raspberry Pi 3
            List<String> deviceList = pioService.getSpiBusList();
            if (deviceList.isEmpty()) {
                Log.i(TAG, "No SPI bus available on this device.");
            } else {
                Log.i(TAG, "List of available devices: " + deviceList);
            }
            spiDevice = pioService.openSpiDevice(SPI_PORT);
            gpioReset = pioService.openGpio(PIN_RESET);
            gpioCheck = pioService.openGpio(Green);
            gpioArlet = pioService.openGpio(Red);
            gpioDefault = pioService.openGpio(Blue);

            gpioCheck.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);
            gpioArlet.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);
            gpioDefault.setDirection(Gpio.DIRECTION_OUT_INITIALLY_LOW);

            mRc522 = new Rc522(spiDevice, gpioReset);
        } catch (IOException e) {
            Toast.makeText(this, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }

        mHandlerRFID.post(mRfid);

        buttonWrite = (Button) findViewById(R.id.buttonWrite);
        buttonWrite.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mRfidTask != null) mRfidTask.cancel(true);
                mHandlerRFID.removeCallbacksAndMessages(null);
                Log.d(TAG, "WRITE");
                isWrite = true;
                asynTask();
            }
        });

        buttonRead = (Button) findViewById(R.id.buttonRead);
    }


    private void asynTask() {
        mRfidTask = new RfidTask(mRc522);
        mRfidTask.execute();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (spiDevice != null) {
                spiDevice.close();
            }

            if (gpioReset != null) {
                gpioReset.close();
            }

            if (gpioDefault != null) {
                gpioDefault.close();
            }
            if (gpioCheck != null) {
                gpioCheck.close();
            }
            if (gpioArlet != null) {
                gpioArlet.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private class RfidTask extends AsyncTask<Object, Object, Boolean> {
        private static final String TAG = "RfidTask";
        private Rc522 rc522;

        RfidTask(Rc522 rc522) {
            this.rc522 = rc522;
        }

        @Override
        protected void onPreExecute() {
            if(isWrite) buttonWrite.setEnabled(false);
            mTagResult.setVisibility(View.GONE);
            mTagUidView.setVisibility(View.GONE);
            resultsText = "";
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            rc522.stopCrypto();
            Log.d(TAG, "DO IN BACKGOUND");
            while (true) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return null;
                }
                //Check if a RFID tag has been found
                if (!rc522.request()) {
                    continue;
                }
                //Check for collision errors
                if (!rc522.antiCollisionDetect()) {
                    continue;
                }
                byte[] uuid = rc522.getUid();
                return rc522.selectTag(uuid);
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (!success) {
                mTagResult.setText(R.string.unknown_error);
                Log.d(TAG, "Unknown error");
                return;
            }

            // In this case, Rc522.AUTH_A or Rc522.AUTH_B can be used
            try {
                // Try to avoid doing any non RC522 operations until you're done communicating with it.
                if (rc522.getUidString().indexOf("182-252-29-31-72") >= 0 || rc522.getUidString().indexOf("19-68-53-216-186") >= 0) {
                    checkIn = true;
                    Log.d(TAG, "Authentication succeeds");
                } else {
                    checkIn = false;
                    Log.d(TAG, "Authentication not succeed");
                }

                byte addressName = Rc522.getBlockAddress(13, 0);
                byte addressDoB = Rc522.getBlockAddress(13, 1);
                byte addressId = Rc522.getBlockAddress(13, 2);

                // Mifare's card default key A and key B, the key may have been changed previously
                byte[] key = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};
                // Each sector holds 16 bytes

                String textWrite = mTagWrite.getText().toString();
                String[] txt = textWrite.split(" ");
                String textName = txt[0];
                String textDoB = txt[1];
                String textId = txt[2];

                textName += new String(new char[16 - textName.length()]).replace('\0', ' ');
                textDoB += new String(new char[16 - textDoB.length()]).replace('\0', ' ');
                textId += new String(new char[16 - textId.length()]).replace('\0', ' ');

                byte[] infoName = textName.getBytes();
                byte[] infoDoB = textDoB.getBytes();
                byte[] infoId = textId.getBytes();

                boolean result1 = rc522.authenticateCard(Rc522.AUTH_A, addressName, key);
                boolean result2 = rc522.authenticateCard(Rc522.AUTH_A, addressDoB, key);
                boolean result3 = rc522.authenticateCard(Rc522.AUTH_A, addressId, key);
                Log.d(TAG, "KeyA: " + Arrays.toString(key));
                if (!result1 || !result2 || !result3) {
                    mTagResult.setText(R.string.authetication_error);
                    Log.d(TAG, "Authentication error");
                }
                if (isWrite == true) {
                    result1 = rc522.writeBlock(addressName, infoName);
                    result2 = rc522.writeBlock(addressDoB, infoDoB);
                    result3 = rc522.writeBlock(addressId, infoId);
                    if (!result1 && !result2 && !result3) {
                        mTagResult.setText(R.string.write_error);
                        Log.d(TAG, "Write error");
                    }
                    resultsText += "Sector written successfully";
                }
                byte[] buffer1 = new byte[16];
                byte[] buffer2 = new byte[16];
                byte[] buffer3 = new byte[16];
                //Since we're still using the same block, we don't need to authenticate again
                result1 = rc522.readBlock(addressName, buffer1);
                result2 = rc522.readBlock(addressDoB, buffer2);
                result3 = rc522.readBlock(addressId, buffer3);
                if (!result1 || !result2 || !result3) {
                    mTagResult.setText(R.string.read_error);
                    Log.d(TAG, "Read error");
                }

                resultsText = new String(buffer1).replaceAll(" ", "") + " "
                        + new String(buffer2).replaceAll(" ", "") + " "
                        + new String(buffer3).replaceAll(" ", "") + " ";

                Log.d(TAG, resultsText+ "\n" + Rc522.dataToHexString(buffer1)
                        + "\n" + Rc522.dataToHexString(buffer2)
                        + "\n" + Rc522.dataToHexString(buffer3));
                rc522.stopCrypto();
                mTagResult.setText(resultsText);

            } finally {
                mHandlerRFID.removeCallbacksAndMessages(null);
                mHandlerRFID.postDelayed(mRfid,3000);
                if (isRead){
                    if (checkIn) {
                        checkIn = false;
                        Toast.makeText(MainActivity.this,"Possible to get in!",Toast.LENGTH_SHORT);
                        try {
                            gpioDefault.setValue(true);
                            gpioCheck.setValue(false);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        mHandlerRFID.postDelayed(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    gpioDefault.setValue(false);
                                    gpioCheck.setValue(true);
                                } catch (IOException e) {
                                    e.printStackTrace();
                                }
                                // Do something after 5s = 5000m
                            }
                        }, 3000);
                    } else {
                        mHandlerRFID.post(mRunnable);
                    }
                }

                buttonWrite.setEnabled(true);
                buttonRead.setEnabled(true);
                buttonWrite.setText(R.string.buttonWrite);
                buttonRead.setText(R.string.buttonRead);

                mTagUidView.setText(getString(R.string.textRead, rc522.getUidString()));
                mTagResult.setVisibility(View.VISIBLE);
                mTagUidView.setVisibility(View.VISIBLE);

                isWrite = false;
            }
        }
    }
}