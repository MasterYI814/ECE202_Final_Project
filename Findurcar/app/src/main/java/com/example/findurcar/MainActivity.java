package com.example.findurcar;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiManager;
import android.net.wifi.rtt.RangingRequest;
import android.net.wifi.rtt.RangingResult;
import android.net.wifi.rtt.RangingResultCallback;
import android.net.wifi.rtt.WifiRttManager;
import android.os.AsyncTask;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import static android.Manifest.permission.ACCESS_FINE_LOCATION;

public class MainActivity extends AppCompatActivity {

    // GUIs
    TextView dis1, rssi1, dis2, rssi2, dis3, rssi3;
    TextView recdis1, recrssi1, recdis2, recrssi2, recdis3, recrssi3;
    Button record;
    TextView direction;
    Switch wild, localize;
    TextView location;


    // Variables
    WifiRttManager rttmanager;
    Handler repeat_handler;
    repeatrunnable repeat;
    Boolean wifi_scan_requsted = false;
    Boolean aps_found = false;
    Boolean allaps_found = false;
    WifiManager wifiManager;
    List<ScanResult> available_aps;
    wifiReceiver scan_receiver;
    Boolean range_success = false;

    Boolean isWILD=false, locon=false;
    Boolean recorded=false;

    SensorManager sensorManager;
    Sensor orientation;
    SensorEventListener orilistener;
    float[] orientations = new float[3];


    String ap1mac="dc:8b:28:54:f4:fc"; // wild1
    String ap2mac="a0:a4:c5:6b:ea:db"; // wild2
    String googleap1 = "1c:f2:9a:d6:f7:f8";
    String googleap2 = "1c:f2:9a:d6:f8:92";
    String googleap3 = "1c:f2:9a:d7:39:d7";

    String scanning = "Scanning"; // for display
    String  dis1_tmp, dis2_tmp, dis3_tmp, rssi1_tmp, rssi2_tmp, rssi3_tmp;
    double  dis1_cal, dis2_cal, dis3_cal;
    double  dis1_wild, dis2_wild;

    //double  gap1_x=10, gap1_y=10, gap2_x=15, gap2_y=20, gap3_x=20, gap3_y=10;
    double  gap1_x=10, gap1_y=10, gap2_x=15, gap2_y=20, gap3_x=20, gap3_y=10; // defining Google AP location coordinates
    double  wap1_x=10, wap2_x=20;  // defining WILD AP location coordinates
    double  result_x=0;
    double  result_y=0; // current location of user
    double record_x=0, record_y=0; // recorded car location
    double  record_dis1, record_dis2, record_dis3; // recorded distance data

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //GUIs
        dis1 = findViewById(R.id.dis1);
        rssi1 = findViewById(R.id.rssi1);
        dis2 =  findViewById(R.id.dis2);
        rssi2 = findViewById(R.id.rssi2);
        dis3 = findViewById(R.id.dis3);
        rssi3 = findViewById(R.id.rssi3);

        recdis1 = findViewById(R.id.recdis1);
        recdis2 = findViewById(R.id.recdis2);
        recdis3 = findViewById(R.id.recdis3);
        recrssi1 = findViewById(R.id.recrssi1);
        recrssi2 = findViewById(R.id.recrssi2);
        recrssi3 = findViewById(R.id.recrssi3);

        record = findViewById(R.id.button);
        direction = findViewById(R.id.direction);
        direction.setText("Record your parking location first!");

        wild =  findViewById(R.id.switch1);
        localize = findViewById(R.id.switch2);
        location = findViewById(R.id.localization);

        //switch listener
        // switch for select google AP or WILD AP
        wild.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {

                wifi_scan_requsted = false;
                aps_found= false;
                allaps_found = false;
                recorded=false;
                range_success = false;
                wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE); //initialize wifi
                rttmanager = (WifiRttManager) getApplicationContext().getSystemService(Context.WIFI_RTT_RANGING_SERVICE); // initialize rttmanager
                if (isChecked){
                    isWILD = true;
                } else {
                    isWILD = false;
                }
            }
        });

        // switch to turn on localization
        localize.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                // do something, the isChecked will be
                // true if the switch is in the On position
                if (isChecked){
                    locon = true;
                } else {
                    locon = false;
                }
            }
        });

        // request permission
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_COARSE_LOCATION, ACCESS_FINE_LOCATION}, 1);

        // Get user orientation by using ROTATION_VECTOR
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        orientation = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        orilistener = new SensorEventListener(){
            @Override
            public void onSensorChanged(SensorEvent sensorEvent) {
                float[] rotationMatrix = new float[16];
                SensorManager.getRotationMatrixFromVector(rotationMatrix, sensorEvent.values);
                float[] remappedRotationMatrix = new float[16];
                SensorManager.remapCoordinateSystem(rotationMatrix, SensorManager.AXIS_X, SensorManager.AXIS_Z, remappedRotationMatrix);

                SensorManager.getOrientation(remappedRotationMatrix, orientations);
                for(int i = 0; i < 3; i++){
                    orientations[i] = (float)(Math.toDegrees(orientations[i]));
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int i){

            }
        };
        sensorManager.registerListener(orilistener, orientation, SensorManager.SENSOR_DELAY_NORMAL);


        // WIFI services
        wifiManager = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE); //initialize wifi
        rttmanager = (WifiRttManager) getApplicationContext().getSystemService(Context.WIFI_RTT_RANGING_SERVICE); // initialize rttmanager

        // Repeat executing runnable
        repeat_handler = new Handler(); // intialize repeat handler
        repeat = new repeatrunnable();
        repeat_handler.post(repeat);

    }



    private class repeatrunnable implements Runnable {
        @Override
        public void run(){
            if(!wifi_scan_requsted){
                wifi_scan_requsted = true;
                if (isWILD) {
                    dis1.setText(scanning);
                    rssi1.setText(scanning);
                    dis2.setText(scanning);
                    rssi2.setText(scanning);
                    dis3.setText("N/A");
                    rssi3.setText("N/A");
                } else {
                    dis1.setText(scanning);
                    rssi1.setText(scanning);
                    dis2.setText(scanning);
                    rssi2.setText(scanning);
                    dis3.setText(scanning);
                    rssi3.setText(scanning);
                }
                find_aps(); // searching for predefined APs
            }
            if(aps_found){
                range(); //ranging request
                if(range_success){ // display data upon ranging success
                    if (dis1_tmp != null & rssi1_tmp != null) {
                        dis1.setText(dis1_tmp);
                        rssi1.setText(rssi1_tmp);
                    }else{
                        dis1.setText("?");
                        rssi1.setText("?");
                    }

                    if (dis2_tmp != null & rssi2_tmp != null) {
                        dis2.setText(dis2_tmp);
                        rssi2.setText(rssi2_tmp);
                    }else{
                        dis2.setText("?");
                        rssi2.setText("?");
                    }
                    if(!isWILD){
                        if (dis3_tmp != null & rssi3_tmp != null) {
                            dis3.setText(dis3_tmp);
                            rssi3.setText(rssi3_tmp);
                        }else{
                            dis3.setText("?");
                            rssi3.setText("?");
                        }
                    }
                    if(recorded) updatedir(); // display direction for finding the car
                    if(locon){
                        getloc(); // localization
                    }
                }
            }else{
                Toast.makeText(getApplicationContext(),"APs not found.", Toast.LENGTH_SHORT).show();
            }
            repeat_handler.postDelayed(repeat, 2000);
        }
    }

    private void getloc() {
        //simple localization algorithm
        //location.setText("Calculating your current location...");
        if (!isWILD) {
            double A = -2 * gap1_x + 2 * gap2_x;
            double B = -2 * gap1_y + 2 * gap2_y;
            double C = (dis1_cal * dis1_cal - dis2_cal * dis2_cal - gap1_x * gap1_x + gap2_x * gap2_x - gap1_y * gap1_y + gap2_y * gap2_y);
            double D = -2 * gap2_x + 2 * gap3_x;
            double E = -2 * gap2_y + 2 * gap3_y;
            double F = (dis2_cal * dis2_cal - dis3_cal * dis3_cal - gap2_x * gap2_x + gap3_x * gap3_x - gap2_y * gap2_y + gap3_y * gap3_y);
            result_x = (C * E - F * B) / (E * A - B * D);
            result_y = (C * D - A * F) / (B * D - A * E);
            location.setText(String.format("%5.2f, %5.2f", result_x, result_y));
        }else{
            result_x = (dis1_wild * dis1_wild - dis2_wild * dis2_wild - wap1_x * wap1_x + wap2_x * wap2_x)/(2*(wap2_x - wap1_x));
            result_y = Math.sqrt(dis2_wild * dis2_wild - (result_x - wap2_x) * (result_x - wap2_x));
            location.setText(String.format("%5.2f, %5.2f", result_x, result_y));
        }
    }

    private void updatedir() { // direction algorithm
        // update directions
        double actual_orientation;
        actual_orientation=orientations[0];
        if (actual_orientation<0){
            actual_orientation=actual_orientation+360;
        }

        double diffx=record_x-result_x;
        double diffy=record_y-result_y;
        double angle_carpeople_y;
        double angle_north_car;
        double angle_result;
        double dis_result;

        if ((diffx>0)&&(diffy>0)){
            angle_carpeople_y=Math.toDegrees(Math.atan(diffx/diffy));
        } else if ((diffx>0)&&(diffy<0)){
            angle_carpeople_y=180+Math.toDegrees(Math.atan(diffx/diffy));
        } else if ((diffx<0)&&(diffy<0)){
            angle_carpeople_y=180+Math.toDegrees(Math.atan(diffx/diffy));
        } else {
            angle_carpeople_y=360+Math.toDegrees(Math.atan(diffx/diffy));
        }

        // get the north-car-user angle
        double ref_y_pos = 90; // This is the parking lot y positive axis angle relative to north clockwise
        angle_north_car=((angle_carpeople_y+ref_y_pos)>360)? (angle_carpeople_y+ref_y_pos-360):(angle_carpeople_y+ref_y_pos);

        // convert the angle into the user coordinate
        angle_result=((actual_orientation-angle_north_car)>0)? (360-actual_orientation+angle_north_car):(angle_north_car-actual_orientation);

        // calculating the distance between user and car
        dis_result=Math.sqrt(Math.pow((record_x-result_x),2)+Math.pow((record_y-result_y),2));
        if(dis_result>3) {
            if (angle_result >= 22.5 && angle_result < 67.5) {
                direction.setText(String.format("Your car is located at %5.2f meters away on your FRONT-RIGHT", dis_result));
            } else if (angle_result >= 67.5 && angle_result < 112.5) {
                direction.setText(String.format("Your car is located at %5.2f meters away on your RIGHT", dis_result));
            } else if (angle_result >= 112.5 && angle_result < 157.5) {
                direction.setText(String.format("Your car is located at %5.2f meters away on your REAR-RIGHT", dis_result));
            } else if (angle_result >= 157.5 && angle_result < 202.5) {
                direction.setText(String.format("Your car is located at %5.2f meters away on your REAR", dis_result));
            } else if (angle_result >= 202.5 && angle_result < 247.5) {
                direction.setText(String.format("Your car is located at %5.2f meters away on your REAR-LEFT", dis_result));
            } else if (angle_result >= 247.5 && angle_result < 292.5) {
                direction.setText(String.format("Your car is located at %5.2f meters away on your LEFT", dis_result));
            } else if (angle_result >= 292.5 && angle_result < 337.5) {
                direction.setText(String.format("Your car is located at %5.2f meters away on your FRONT-LEFT", dis_result));
            } else {
                direction.setText(String.format("Your car is located at %5.2f meters away on your FRONT", dis_result));
            }
        }
        else{
            direction.setText("Your car is found.");
        }



        //direction.setText(String.format("(%5.0f, %5.0f, %5.0f)\n(angle_carpeople_y=%5.2f angle_north_car=%5.2f angle_result=%5.2f dis_result=%5.2f)"
        //       , actual_orientation, orientations[1], orientations[2],angle_carpeople_y,angle_north_car,angle_result,dis_result));
        //direction.append(String.format("(angle_north_car=%5.2f angle_result=%5.2f dis_result=%5.2f)"),angle_north_car);
    }

    public void find_aps(){ // search for aps
        if(!wifiManager.isWifiEnabled()){
            wifiManager.setWifiEnabled(true); // enable wifi
        }
        IntentFilter filter = new IntentFilter(wifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
        scan_receiver = new wifiReceiver();
        getApplicationContext().registerReceiver(scan_receiver,filter);
        wifiManager.startScan();
        return; //scan started
    }

    private class wifiReceiver extends BroadcastReceiver{
        @Override
        public void onReceive(final Context context, final Intent intent){
            int aps = 0;
            available_aps = wifiManager.getScanResults();
            if(isWILD) {
                for (int i = 0; i < available_aps.size(); i++) {
                    if (available_aps.get(i).BSSID.equals(ap1mac) | available_aps.get(i).BSSID.equals(ap2mac)) {
                        aps_found = true;
                        aps++;
                    }
                }
                if (aps == 2){
                    allaps_found = true;
                }
            }else{
                for (int i = 0; i < available_aps.size(); i++) {
                    if (available_aps.get(i).BSSID.equals(googleap1) | available_aps.get(i).BSSID.equals(googleap2) | available_aps.get(i).BSSID.equals(googleap3)) {
                        aps_found = true;
                        aps++;
                    }
                }
                if(aps == 3){
                    allaps_found = true;
                }
            }
        }
    }

    public void range(){ // WIFI RTT range request
        RangingRequest rangingRequest;
        RangingRequest.Builder builder;
        builder = new RangingRequest.Builder();

        for(int i = 0; i < available_aps.size(); i++){
            if(isWILD) {
                if (available_aps.get(i).BSSID.equals(ap1mac) | available_aps.get(i).BSSID.equals(ap2mac)) {
                    builder.addAccessPoint(available_aps.get(i));
                }
            }else{
                if (available_aps.get(i).BSSID.equals(googleap1) | available_aps.get(i).BSSID.equals(googleap2) | available_aps.get(i).BSSID.equals(googleap3)) {
                    builder.addAccessPoint(available_aps.get(i));
                }
            }
        }

        rangingRequest = builder.build();
        if(checkSelfPermission(ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            rttmanager.startRanging(rangingRequest, AsyncTask.SERIAL_EXECUTOR, rangecallback);
        }
    }


    private RangingResultCallback rangecallback = new RangingResultCallback() { // process and save ranging results
        @Override
        public void onRangingFailure(int code) {
            Toast.makeText(getApplicationContext(),"Ranging Failed.", Toast.LENGTH_SHORT).show();
            range_success = false;
        }

        @Override
        public void onRangingResults(List<RangingResult> results) {
            //range_success = true;
            for (int i = 0; i < results.size(); i++){ //results.size() should match AP number
                if(results.get(i).getStatus() == RangingResult.STATUS_SUCCESS){
                    range_success = true;
                    if(isWILD){
                        if(results.get(i).getMacAddress().toString().equals(ap1mac)) {
                            dis1_tmp = String.format("%5.2f", results.get(i).getDistanceMm() / 1000.0);
                            dis1_wild = new Double(results.get(i).getDistanceMm() / 1000.0);
                            rssi1_tmp = String.format("%4d", results.get(i).getRssi());
                        }
                        if(results.get(i).getMacAddress().toString().equals(ap2mac)) {
                            dis2_tmp = String.format("%5.2f", results.get(i).getDistanceMm() / 1000.0);
                            dis2_wild =  new Double(results.get(i).getDistanceMm() / 1000.0);
                            rssi2_tmp = String.format("%4d", results.get(i).getRssi());
                        }
                    }else{
                        if(results.get(i).getMacAddress().toString().equals(googleap1)) {
                            dis1_tmp = String.format("%5.2f", results.get(i).getDistanceMm() / 1000.0);
                            dis1_cal =  new Double(results.get(i).getDistanceMm() / 1000.0);
                            if (dis1_cal<0){
                                dis1_cal=0;
                            }
                            rssi1_tmp = String.format("%4d", results.get(i).getRssi());
                        }
                        if(results.get(i).getMacAddress().toString().equals(googleap2)) {
                            dis2_tmp = String.format("%5.2f", results.get(i).getDistanceMm() / 1000.0);
                            dis2_cal =  new Double(results.get(i).getDistanceMm() / 1000.0);
                            if(dis2_cal<0){
                                dis2_cal=0;
                            }
                            rssi2_tmp = String.format("%4d", results.get(i).getRssi());
                        }
                        if(results.get(i).getMacAddress().toString().equals(googleap3)) {
                            dis3_tmp = String.format("%5.2f", results.get(i).getDistanceMm() / 1000.0);
                            dis3_cal =  new Double(results.get(i).getDistanceMm() / 1000.0);
                            if(dis3_cal<0){
                                dis3_cal=0;
                            }
                            rssi3_tmp = String.format("%4d", results.get(i).getRssi());
                        }
                    }

                }else{
                    //Toast.makeText(getApplicationContext(), "Ranging not succeed", Toast.LENGTH_SHORT).show();
                    //range_success = false;
                    if(isWILD){
                        if(results.get(i).getMacAddress().toString().equals(ap1mac)) {
                            dis1_tmp = null;
                            rssi1_tmp = null;
                        }
                        if(results.get(i).getMacAddress().toString().equals(ap2mac)) {
                            dis2_tmp = null;
                            rssi2_tmp = null;
                        }
                    }else{
                        if(results.get(i).getMacAddress().toString().equals(googleap1)) {
                            dis1_tmp = null;
                            //dis1_cal =  new Double(results.get(i).getDistanceMm() / 1000.0);
                            rssi1_tmp = null;
                        }
                        if(results.get(i).getMacAddress().toString().equals(googleap2)) {
                            dis2_tmp = null;
                            //dis2_cal =  new Double(results.get(i).getDistanceMm() / 1000.0);
                            rssi2_tmp = null;
                        }
                        if(results.get(i).getMacAddress().toString().equals(googleap3)) {
                            dis3_tmp = null;
                            //dis3_cal =  new Double(results.get(i).getDistanceMm() / 1000.0);
                            rssi3_tmp = null;
                        }
                    }
                }
            }
        }
    };

    public void record(View view) { // record button function
        // record the car location
        if (allaps_found) {
            recorded=true;
            if (!dis1.getText().toString().equals(scanning)) {
                recdis1.setText(dis1.getText().toString());
                record_dis1 = Double.parseDouble(dis1.getText().toString());
            }
            if (!rssi1.getText().toString().equals(scanning)) {
                recrssi1.setText(rssi1.getText().toString());
            }
            if (!dis2.getText().toString().equals(scanning)) {
                recdis2.setText(dis2.getText().toString());
                record_dis2 = Double.parseDouble(dis2.getText().toString());
            }
            if (!rssi2.getText().toString().equals(scanning)) {
                recrssi2.setText(rssi2.getText().toString());
            }
            if (!isWILD) {
                if (!dis3.getText().toString().equals(scanning)) {
                    recdis3.setText(dis3.getText().toString());
                    record_dis3 = Double.parseDouble(dis3.getText().toString());
                }
                if (!rssi3.getText().toString().equals(scanning)) {
                    recrssi3.setText(rssi3.getText().toString());
                }
                //Google wifi car location
                double A = -2 * gap1_x + 2 * gap2_x;
                double B = -2 * gap1_y + 2 * gap2_y;
                double C = (record_dis1 * record_dis1 - record_dis2 * record_dis2 - gap1_x * gap1_x + gap2_x * gap2_x - gap1_y * gap1_y + gap2_y * gap2_y);
                double D = -2 * gap2_x + 2 * gap3_x;
                double E = -2 * gap2_y + 2 * gap3_y;
                double F = (record_dis2 * record_dis2 - record_dis3 * record_dis3 - gap2_x * gap2_x + gap3_x * gap3_x - gap2_y * gap2_y + gap3_y * gap3_y);
                record_x = (C * E - F * B) / (E * A - B * D);
                record_y = (C * D - A * F) / (B * D - A * E);

            } else { //WILD car location
                record_x = (record_dis1 * record_dis1 - record_dis2 * record_dis2 - wap1_x * wap1_x + wap2_x * wap2_x)/(2*(wap2_x - wap1_x));
                record_y = Math.sqrt(record_dis2 * record_dis2 - (record_x - wap2_x) * (record_x - wap2_x));
            }
        }else{
            Toast.makeText(getApplicationContext(), "Missing Data", Toast.LENGTH_SHORT).show();
        }
    }


}