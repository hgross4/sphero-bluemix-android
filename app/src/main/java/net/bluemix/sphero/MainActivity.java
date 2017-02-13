/*
 * Copyright IBM Corp. 2015
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author: Niklas Heidloff (@nheidloff)
 */

package net.bluemix.sphero;

import android.Manifest;
import android.annotation.TargetApi;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.orbotix.ConvenienceRobot;
import com.orbotix.DualStackDiscoveryAgent;
import com.orbotix.common.DiscoveryException;
import com.orbotix.common.Robot;
import com.orbotix.common.RobotChangedStateListener;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends ActionBarActivity implements MqttCallback, RobotChangedStateListener {

    private static final int REQUEST_CODE_LOCATION_PERMISSION = 42;

    MqttClient mqttClient;

    @TargetApi(Build.VERSION_CODES.M)
    public void onConnectClick(View v) {

        // replace these values with the values you receive when registering new devices
        // with the IBM Internet of Things Foundation (IoT service dashboard in Bluemix)

        // broker: replace "irnwk2" with your org
        String broker       = "tcp://nayvxj.messaging.internetofthings.ibmcloud.com:1883";

        // clientId: replace "irnwk2" with your org, "and" with your type (if you use another one)
        // and "niklas" with your own device id
        String clientId     = "d:nayvxj:and:nexus";

        // password: replace with your own password
        String password     = "zkQhY1NUe*d9&b6)5C";

        // Connect to IBM Bluemix
        MemoryPersistence persistence = new MemoryPersistence();
        try {
            mqttClient = new MqttClient(broker, clientId, persistence);
            MqttConnectOptions connOpts = new MqttConnectOptions();
            connOpts.setPassword(password.toCharArray());
            connOpts.setUserName("use-token-auth");
            connOpts.setCleanSession(true);
            mqttClient.connect(connOpts);
            mqttClient.subscribe("iot-2/cmd/+/fmt/json");
            mqttClient.setCallback(this);
            System.out.println("Connected");
        } catch(MqttException me) {
            me.printStackTrace();
        }

        // Connect to Sphero
        if( Build.VERSION.SDK_INT < Build.VERSION_CODES.M
                || checkSelfPermission( Manifest.permission.ACCESS_COARSE_LOCATION ) == PackageManager.PERMISSION_GRANTED ) {
            startDiscovery();
        }
    }

    public void messageArrived(String topic, MqttMessage message)
            throws Exception {
        System.out.println("Message arrived");
        if (message == null) return;
        String payload = message.toString();
        if (payload == null) return;
        if (topic == null) return;

        // drive the Sphero ball (command type: "setRoll")
        // expected JSON format of msg.payload:
        //  {"d":{
        //      "heading":0,
        //      "velocity":0.8 }
        //  }
        if (topic.equalsIgnoreCase("iot-2/cmd/setRoll/fmt/json")) {
            String headingS = payload.substring(payload.indexOf("heading") + 9,
                    payload.indexOf(","));
            String speedS = payload.substring(payload.indexOf("velocity") + 10,
                    payload.indexOf("}"));
            float heading = Float.parseFloat(headingS);
            float speed = Float.parseFloat(speedS);

            mRobot.drive(heading, speed);
        }

        // change the color of the Sphero ball
        // expected JSON format of msg.payload:
        //  {"d":{
        //      "color":"red" }
        //  }
        if (topic.equalsIgnoreCase("iot-2/cmd/setColor/fmt/json")) {
            payload = payload.replace(" ","");
            String colorS = payload.substring(payload.indexOf("color") + 8,
                    payload.indexOf("}")-1);
            if (colorS.equalsIgnoreCase("")) return;

            if (colorS.equalsIgnoreCase("red")) {
                mRobot.setLed(220, 20, 60);
            }
            if (colorS.equalsIgnoreCase("green")) {
                mRobot.setLed(46,139,87);
            }
            if (colorS.equalsIgnoreCase("blue")) {
                mRobot.setLed(70,130,180);
            }
            if (colorS.equalsIgnoreCase("yellow")) {
                mRobot.setLed(255,255,0);
            }
            if (colorS.equalsIgnoreCase("black")) {
                mRobot.setLed(10,20,1);
            }
        }
    }

    public void connectionLost(Throwable cause) {
        cause.printStackTrace();
    }

    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    public void onDisconnectClick(View v) {
        // Disconnect from IBM Bluemix
        try {
            mqttClient.disconnect();
            System.out.println("Disconnected");
        }
        catch(MqttException me) {
            me.printStackTrace();
        }

        // Disconnect from Sphero
        //If the DiscoveryAgent is in discovery mode, stop it.
        if( DualStackDiscoveryAgent.getInstance().isDiscovering() ) {
            DualStackDiscoveryAgent.getInstance().stopDiscovery();
        }

        //If a robot is connected to the device, disconnect it
        if( mRobot != null ) {
            mRobot.disconnect();
            mRobot = null;
        }
    }

    private ConvenienceRobot mRobot;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        DualStackDiscoveryAgent.getInstance().addRobotStateListener(this);

        if( Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ) {
            int hasLocationPermission = checkSelfPermission( Manifest.permission.ACCESS_COARSE_LOCATION );
            if( hasLocationPermission != PackageManager.PERMISSION_GRANTED ) {
                Log.e( "Sphero", "Location permission has not already been granted" );
                List<String> permissions = new ArrayList<String>();
                permissions.add( Manifest.permission.ACCESS_COARSE_LOCATION);
                requestPermissions(permissions.toArray(new String[permissions.size()] ), REQUEST_CODE_LOCATION_PERMISSION );
            } else {
                Log.d( "Sphero", "Location permission already granted" );
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch ( requestCode ) {
            case REQUEST_CODE_LOCATION_PERMISSION: {
                for( int i = 0; i < permissions.length; i++ ) {
                    if( grantResults[i] == PackageManager.PERMISSION_GRANTED ) {
                        startDiscovery();
                        Log.d( "Permissions", "Permission Granted: " + permissions[i] );
                    } else if( grantResults[i] == PackageManager.PERMISSION_DENIED ) {
                        Log.d( "Permissions", "Permission Denied: " + permissions[i] );
                    }
                }
            }
            break;
            default: {
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
            }
        }
    }

    private void startDiscovery() {
        //If the DiscoveryAgent is not already looking for robots, start discovery.
        if( !DualStackDiscoveryAgent.getInstance().isDiscovering() ) {
            try {
                DualStackDiscoveryAgent.getInstance().startDiscovery( this );
            } catch (DiscoveryException e) {
                Log.e("Sphero", "DiscoveryException: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        DualStackDiscoveryAgent.getInstance().addRobotStateListener( null );
    }

    @Override
    public void handleRobotChangedState(Robot robot, RobotChangedStateNotificationType type) {
        switch (type) {
            case Online: {
                mRobot = new ConvenienceRobot(robot);
                break;
            }
        }
    }
}