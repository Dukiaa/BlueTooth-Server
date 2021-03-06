import java.io.BufferedReader;
import java.io.IOException;

import javax.bluetooth.BluetoothStateException;
import javax.bluetooth.LocalDevice;
import javax.bluetooth.RemoteDevice;
import javax.bluetooth.UUID;
import javax.microedition.io.Connector;
import javax.microedition.io.StreamConnection;
import javax.microedition.io.StreamConnectionNotifier;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Timer;
import java.util.TimerTask;

public class Server{

    public static void main(String[] args){
        log("Local Bluetooth device...\n");

        LocalDevice local = null;
        try {
            local = LocalDevice.getLocalDevice();
        }
        catch (BluetoothStateException e2) {
        }

        log( "address: " + local.getBluetoothAddress() );
        log( "name: " + local.getFriendlyName() );

        Runnable r = new ServerRunable();
        Thread thread = new Thread(r);
        thread.start();
    }

    private static void log(String msg) {
        System.out.println("["+(new Date()) + "] " + msg);
    }
}


class ServerRunable implements Runnable{

    //UUID for SPP
    final UUID uuid = new UUID("0000110100001000800000805F9B34FB", false);
    final String CONNECTION_URL_FOR_SPP = "btspp://localhost:"
            + uuid +";name=SPP Server";

    private StreamConnectionNotifier mStreamConnectionNotifier = null;
    private StreamConnection mStreamConnection = null;
    private int count = 0;

    @Override
    public void run() {

        try {

            mStreamConnectionNotifier = (StreamConnectionNotifier) Connector
                    .open(CONNECTION_URL_FOR_SPP);

            log("Opened connection successful.");
        } catch (IOException e) {

            log("Could not open connection: " + e.getMessage());
            return;
        }


        log("Server is now running.");



        while(true){

            log("wait for client requests...");

            try {

                mStreamConnection = mStreamConnectionNotifier.acceptAndOpen();
            } catch (IOException e1) {

                log("Could not open connection: " + e1.getMessage() );
            }


            count++;
            log("?????? ?????? ?????? ??????????????? ???: " + count);


            new Receiver(mStreamConnection).start();
        }

    }



    class Receiver extends Thread {

        private InputStream mInputStream = null;
        private OutputStream mOutputStream = null;
        private String mRemoteDeviceString = null;
        private StreamConnection mStreamConnection = null;


        Receiver(StreamConnection streamConnection){

            mStreamConnection = streamConnection;

            try {

                mInputStream = mStreamConnection.openInputStream();
                mOutputStream = mStreamConnection.openOutputStream();

                log("Open streams...");
            } catch (IOException e) {

                log("Couldn't open Stream: " + e.getMessage());

                Thread.currentThread().interrupt();
                return;
            }


            try {

                RemoteDevice remoteDevice
                        = RemoteDevice.getRemoteDevice(mStreamConnection);

                mRemoteDeviceString = remoteDevice.getBluetoothAddress();

                log("Remote device");
                log("address: "+ mRemoteDeviceString);

            } catch (IOException e1) {

                log("Found device, but couldn't connect to it: " + e1.getMessage());
                return;
            }

            log("Client is connected...");
        }


        @Override
        public void run() {

            try {

                Reader mReader = new BufferedReader(new InputStreamReader
                        ( mInputStream, Charset.forName(StandardCharsets.UTF_8.name())));

                boolean isDisconnected = false;


                while(true){
                    StringBuilder stringBuilder = new StringBuilder();
                    int c = 0;


                    while ( '\n' != (char)( c = mReader.read()) ) {

                        if ( c == -1){

                            log("Client has been disconnected");

                            count--;
                            log("?????? ?????? ?????? ??????????????? ???: " + count);

                            isDisconnected = true;
                            Thread.currentThread().interrupt();

                            break;
                        }

                        stringBuilder.append((char) c);
                    }

                    if ( isDisconnected ) break;

                    String recvMessage = stringBuilder.toString();
                    log( mRemoteDeviceString + ": " + recvMessage);


                    //????????? ?????? ??????
                    String url = "http://192.168.1.182:8083/gate/open";
                    String data = "userID="+recvMessage+"&gateNumber=11";

                    //????????? ?????? ??????
                    String s = httpGetConnection(url, data);

                    //????????? ?????? ?????????
                    int s_len = s.length();

                    //Timer
                    Timer timer = new Timer();
                    TimerTask task = new TimerTask() { //??????????????? ??????????????????.
                        @Override
                        public void run() {
                            System.out.println("?????? ???????????????.\n");
                        }
                    };


                    //Gate On/Off
                    System.out.println("\n[?????????]");
                    if(s_len > 5) {
                        TimerTask stop = new TimerTask() {
                            int num = 3;
                            @Override
                            public void run() {
                                if(num > 0){
                                    System.out.println(num+".. ");
                                    num--; //???????????? ??????
                                }
                                else{
                                    timer.cancel(); //????????? ??????
                                }
                            }
                        };
                        timer.schedule(stop, 1000, 1000); //?????? Task, 1?????? ??????, 1????????? ??????

                        System.out.println("?????? ???????????????.");
                        timer.schedule(task, 4000);
                    }

                    else
                        System.out.println("No Access\n");
                }

            } catch (IOException e) {

                log("Receiver closed" + e.getMessage());
            }
        }
        public String httpGetConnection(String UrlData, String ParamData) {

            //http ?????? ??? url ????????? ???????????? ???????????? ???????????? ?????? ?????? ??????
            String totalUrl = "";
            if(ParamData != null && ParamData.length() > 0 &&
                    !ParamData.equals("") && !ParamData.contains("null")) { //???????????? ?????? ????????? ????????? ??????
                totalUrl = UrlData.trim().toString() + "?" + ParamData.trim().toString();
            }
            else {
                totalUrl = UrlData.trim().toString();
            }

            //http ????????? ???????????? ?????? ?????? ??????
            URL url = null;
            HttpURLConnection conn = null;

            //http ?????? ?????? ??? ?????? ?????? ???????????? ?????? ?????? ??????
            String responseData = "";
            BufferedReader br = null;
            StringBuffer sb = null;

            //????????? ?????? ???????????? ???????????? ?????? ??????
            String returnData = "";

            try {
                //??????????????? ????????? url??? ????????? connection ??????
                url = new URL(totalUrl);
                conn = (HttpURLConnection) url.openConnection();

                //http ????????? ????????? ?????? ?????? ??????
                conn.setRequestProperty("Accept", "application/json");
                conn.setRequestMethod("GET");

                //http ?????? ??????
                conn.connect();

                //http ?????? ??? ?????? ?????? ???????????? ????????? ?????????
                br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                sb = new StringBuffer();
                while ((responseData = br.readLine()) != null) {
                    sb.append(responseData); //StringBuffer??? ???????????? ????????? ??????????????? ?????? ??????
                }

                //????????? ?????? ?????? ??? ???????????? ????????? ?????? ????????? ?????? ??????
                returnData = sb.toString();

                //http ?????? ?????? ?????? ?????? ??????
                String responseCode = String.valueOf(conn.getResponseCode());
                System.out.println("");
                System.out.println("http ?????? ????????? : "+returnData);

            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                //http ?????? ??? ?????? ?????? ??? BufferedReader??? ???????????????
                try {
                    if (br != null) {
                        br.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return returnData;
        }
    }

    private static void log(String msg) {
        System.out.println("["+(new Date()) + "] " + msg);
    }
}