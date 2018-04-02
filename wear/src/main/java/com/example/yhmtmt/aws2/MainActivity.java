package com.example.yhmtmt.aws2;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.Handler;
import android.support.wearable.activity.WearableActivity;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;

import static java.lang.Thread.sleep;
import static java.nio.charset.StandardCharsets.US_ASCII;


class AWSCom
{
    Socket sock;
    String addr;
    int port;
    int cmdLen = 1024;
    int conTimeOut = 5000;
    int maxReadTries = 10;
    int readTryInterval = 100;
    OutputStream writer = null;
    InputStream reader = null;

    byte[] bytes;
    byte[] strBytes;

    String cmdStrEoc = "eoc";

    public boolean startSession(String _addr, int _port){
        addr = _addr;
        port = _port;
        try {
            sock = new Socket();
            InetSocketAddress svr = new InetSocketAddress(addr, port);
            sock.connect(svr, conTimeOut);

            writer = sock.getOutputStream();
            reader = sock.getInputStream();
        } catch (SocketTimeoutException e) {
            System.out.println("Connection time out:" + addr + ":" + port);
            e.printStackTrace();
            return false;
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return false;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        if(bytes == null)
            bytes = new byte[cmdLen];

        return true;
    }

    private boolean cmdFset(String strCmd, String[] pStrs, String[] vStrs, int start, int end) throws IOException
    {
        // issue fset command parameters from start to end
        Arrays.fill(bytes, (byte)0);
        String strCmdParams = strCmd;
        for(int jparam = start; jparam < end; jparam++){
            strCmdParams += " " + pStrs[jparam] + " " + vStrs[jparam];
        }
        System.out.println("Issue:" + strCmdParams);
        strBytes = strCmdParams.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(strBytes, 0, bytes, 0, strBytes.length);
        writer.write(bytes);
        writer.flush();

        if(waitCmdRet() != cmdLen){
            System.out.println("May be error. Remote command processing module may be in trouble.");
        }else{
            if(bytes[0] == 0){
                System.out.println("Failed:"+ strCmdParams+ ":"
                        + new String(Arrays.copyOfRange(bytes, 1, bytes.length), "US-ASCII"));
                return false;
            }
        }

        return true;
    }

    public boolean cmdFset(String fStr, String[] pStrs, String[] vStrs) throws IOException
    {

        if(pStrs.length != vStrs.length)
            return false;

        String strCmd = "fset " + fStr;
        int len = strCmd.length();
        int startParam = 0, endParam = 1;
        for (int iparam = 0; iparam < pStrs.length; iparam++){
            int newLen = len + pStrs[iparam].length() + vStrs[iparam].length() + 2;
            if(newLen >= cmdLen - 1){
                if(!cmdFset(strCmd, pStrs, vStrs, startParam, endParam))
                    return false;

                len = strCmd.length();
                startParam  = iparam;
                endParam = iparam + 1;
                continue;
            }
            len = newLen;
            endParam = iparam + 1;
        }

        return cmdFset(strCmd, pStrs, vStrs, startParam, endParam);
    }

    public boolean cmdFget(String strCmd, String[] pStrs, String[] vStrs, int start, int end) throws IOException{
        Arrays.fill(bytes, (byte)0);
        String strCmdParams = strCmd;
        for(int jparam = start; jparam < end; jparam++){
            strCmdParams += " " + pStrs[jparam];
        }
        System.out.println("Issue:" + strCmdParams);
        strBytes = strCmdParams.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(strBytes, 0, bytes, 0, strBytes.length);
        writer.write(bytes);
        writer.flush();

        if(waitCmdRet() != cmdLen){
            System.out.println("May be error. Remote command processing module may be in trouble.");
        }else{
            if(bytes[0] == 0){
                System.out.println("Failed:"+ strCmdParams + ":"
                        + new String(Arrays.copyOfRange(bytes, 1, bytes.length), "US-ASCII"));
                return false;
            }else{
                String[] vStrRet = (new String(Arrays.copyOfRange(bytes, 1, bytes.length), "US-ASCII")).split(" ");
                for(int jparam = start; jparam < end; jparam++)
                    vStrs[jparam] = vStrRet[jparam-start];
            }
        }

        return true;
    }

    public boolean cmdFget(String fStr, String[] pStrs, String[] vStrs) throws IOException{
        String strCmd = "fget " + fStr;
        if(pStrs.length != vStrs.length)
            return false;
        int len = strCmd.length();
        int startParam = 0, endParam = 1;
        for(int iparam = 0; iparam < pStrs.length; iparam++){
            int newLen = len + pStrs[iparam].length() + 1;
            if(newLen >= cmdLen/4) {
                if(!cmdFget(strCmd, pStrs, vStrs, startParam, endParam))
                    return false;
                len = strCmd.length();
                startParam  = iparam;
                endParam = iparam + 1;
                continue;
            }
            len = newLen;
            endParam = iparam + 1;
        }

        return cmdFget(strCmd, pStrs, vStrs, startParam, endParam);
    }

    public int waitCmdRet(){
        int rcvCount = -1;
        try {
            int readTries = 0;
            while (reader.available() < cmdLen && maxReadTries > readTries) {
                sleep(readTryInterval);
                readTries++;
            }

            rcvCount = reader.read(bytes, 0, reader.available());
        }catch (InterruptedException e) {
            e.printStackTrace();
        } catch (SocketTimeoutException e) {
            System.out.println("Connection time out:" + addr + ":" + port);
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return rcvCount;
    }

    public void endSession(){
        // sending EOC command to finish session.
        try {
            Arrays.fill(bytes, (byte) 0);
            strBytes = cmdStrEoc.getBytes();
            System.arraycopy(strBytes, 0, bytes, 0, strBytes.length);
            writer.write(bytes);
            writer.flush();


        } catch (SocketTimeoutException e) {
            System.out.println("Connection time out:" + addr + ":" + port);
            e.printStackTrace();
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            sock.close();
            writer.close();
            reader.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }
}

public class MainActivity extends WearableActivity implements View.OnClickListener {
    public static final String PREFS_NAME = "aws2PrefsFile";

    // for communication
    AWSCom awsCom;
    String addr = "192.168.11.12";
    int port = 20000;

    String strUIFilter="aws1_ui";
    String[] strGetParams = {"wmeng", "wrud", "wrev", "wsog", "wcog", "wyaw", "wdpt", "wear"};
    String[] strSetParams = {"crz"};
    String[] strSetHbt = {"whbt"};

    int hbt = 0;

    //    private TextView mTextView;
    UpdaterTask updaterTask = null;
    Timer updaterTimer = null;
    Handler updaterHandler = null;

    class UpdaterTask extends TimerTask {
        @Override
        public void run() {
            hbt = (hbt + 1) % 0xFFFF;

            System.out.println("Param Updating");

            try {
                if(awsCom == null)
                    awsCom = new AWSCom();

                addr = AppData.getInstance().addr;
                port = AppData.getInstance().port;
                if(awsCom.startSession(addr, port)) {
                    String[] vStrs = new String[1];
                    vStrs[0] = null;

                    // setting order if needed
                    if (nextEngState != engState) {
                        vStrs[0] = nextEngState.name();
                    } else if (nextRudState != rudState) {
                        vStrs[0] = nextRudState.name();
                    }

                    if (vStrs[0] != null) {
                        if(!awsCom.cmdFset(strUIFilter, strSetParams, vStrs)){
                            engState = nextEngState;
                            rudState = nextRudState;
                        }else
                            throw new IOException();
                    }

                    //sending heart beat
                    vStrs[0] = Integer.toString(hbt);
                    if(!awsCom.cmdFset(strUIFilter, strSetHbt, vStrs)){
                        throw new IOException();
                    }

                    vStrs = new String[8];
                    if (awsCom.cmdFget(strUIFilter, strGetParams, vStrs)) {
                        try {
                            eng = Integer.parseInt(vStrs[0]);
                            rud = Integer.parseInt(vStrs[1]);
                            rev = Integer.parseInt(vStrs[2]);
                            sog = Integer.parseInt(vStrs[3]);
                            cog = Integer.parseInt(vStrs[4]);
                            yaw = Integer.parseInt(vStrs[5]);
                            dpt = Integer.parseInt(vStrs[6]);
                            bctrl = (vStrs[7] == "y");
                        }catch(NumberFormatException e){
                            e.printStackTrace();
                        }
                    }else{
                        throw new IOException();
                    }
                    System.out.println("eng:" + eng + " rud:" + rud + " rev:" + rev + " sog:"
                            + sog + " cog:" + cog + " yaw:" + yaw + " dpt:"+ dpt + " wear:" + bctrl);

                    awsCom.endSession();
                }
            }catch(IOException e)
            {
                awsCom.endSession();
                e.printStackTrace();
            }

            updaterHandler.post(new Runnable() {
                public void run() {
                    System.out.println("UI Updating");
                    skbRudder.setProgress(rud);
                    skbEngine.setProgress(eng);
                    String strInfo1, strInfo2;
                    strInfo1 = String.format("C%03d H%03d D%03d.%1dm", cog, yaw, dpt/10, dpt%10);
                    strInfo2 = String.format("%02d.%1dkts %04drpm", sog/10, sog%10, rev);
                    txtInfo1.setText(strInfo1);
                    txtInfo2.setText(strInfo2);
                }
            });
        }
    }

    Button btnStarboard;
    Button btnPort;
    Button btnMidship;
    Button btnForward;
    Button btnBackward;
    Button btnNeutral;
    Button btnConfig;

    SeekBar skbRudder;
    SeekBar skbEngine;

    TextView txtInfo1;
    TextView txtInfo2;

    enum EngState { stp, dsah, slah, hfah, flah, nf, dsas, slas, hfas, flas};
    EngState engState, nextEngState;
    enum RudState { mds, p10, p20, hap, s10, s20, has };
    RudState rudState, nextRudState;
    int eng, rud, rev, sog, cog, yaw, dpt;
    boolean bctrl = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        AppData.getInstance().load(settings);
        addr = AppData.getInstance().addr;
        port = AppData.getInstance().port;

        setContentView(R.layout.activity_main);

//        mTextView = (TextView) findViewById(R.id.text);

        eng = 127;
        engState = nextEngState = EngState.stp;

        rud = 127;
        rudState = nextRudState = RudState.mds;

        btnStarboard = (Button) findViewById(R.id.starboard);
        btnStarboard.setOnClickListener(this);

        btnPort = (Button) findViewById(R.id.port);
        btnPort.setOnClickListener(this);

        btnMidship = (Button) findViewById(R.id.midship);
        btnMidship.setOnClickListener(this);

        btnForward = (Button) findViewById(R.id.forward);
        btnForward.setOnClickListener(this);

        btnNeutral = (Button) findViewById(R.id.neutral);
        btnNeutral.setOnClickListener(this);

        btnBackward =(Button) findViewById(R.id.backward);
        btnBackward.setOnClickListener(this);

        btnConfig = (Button) findViewById(R.id.actConfig);
        btnConfig.setOnClickListener(this);

        skbRudder = (SeekBar) findViewById(R.id.rudder);
        skbEngine = (SeekBar) findViewById(R.id.engine);
        skbRudder.setMax(255);
        skbEngine.setMax(255);

        txtInfo1 = (TextView) findViewById(R.id.info1);
        txtInfo2 = (TextView) findViewById(R.id.info2);

        updaterTask = new UpdaterTask();
        updaterHandler = new Handler();
        updaterTimer = new Timer(true);
        updaterTimer.schedule(updaterTask,500,500);
        //        // Enables Always-on

        setAmbientEnabled();
    }

    @Override
    protected void onStop(){
        super.onStop();
        SharedPreferences settings = getSharedPreferences(PREFS_NAME, 0);
        AppData.getInstance().save(settings);
    }

    @Override
    public void onClick(View v) {
        Button btn = (Button) v;
        switch(btn.getId()) {
            case R.id.port:
                System.out.println("port");
                setNextRudState(btn.getId());
                break;
            case R.id.starboard:
                System.out.println("starboard");
                setNextRudState(btn.getId());
                break;
            case R.id.midship:
                System.out.println("midship");
                setNextRudState(btn.getId());
                break;
            case R.id.forward:
                System.out.println("forward");
                setNextEngState(btn.getId());
                break;
            case R.id.backward:
                System.out.println("backward");
                setNextEngState(btn.getId());
                break;
            case R.id.neutral:
                System.out.println("neutral");
                setNextEngState(btn.getId());
                break;
            case R.id.actConfig:
                Intent intent = new Intent(this, NetworkConfig.class);
                startActivity(intent);
                break;
        }
    }

    public void setNextEngState(int id)
    {
        if(R.id.forward == id) {
            switch(engState){
                case stp:
                    nextEngState = engState.dsah;
                    break;
                case dsah:
                    nextEngState = engState.slah;
                    break;
                case slah:
                    nextEngState = engState.hfah;
                    break;
                case hfah:
                    nextEngState = engState.flah;
                    break;
                case flah:
                    nextEngState = engState.nf;
                    break;
                case nf:
                    nextEngState = engState.nf;
                    break;
                case dsas:
                case slas:
                case hfas:
                case flas:
                    nextEngState = engState.stp;
                    break;
                default:
                    nextEngState = engState.stp;
            }
            return;
        }

        if(R.id.backward == id) {
            switch(engState){
                case stp:
                    nextEngState = engState.dsas;
                    break;
                case dsas:
                    nextEngState = engState.slas;
                    break;
                case slas:
                    nextEngState = engState.hfas;
                    break;
                case hfas:
                    nextEngState = engState.flas;
                    break;
                case flas:
                    nextEngState = engState.flas;
                    break;
                case nf:
                case dsah:
                case slah:
                case hfah:
                case flah:
                    nextEngState = engState.stp;
                    break;
                default:
                    nextEngState = engState.stp;
            }
            return;
        }

        nextEngState = engState.stp;
    }

    public void setNextRudState(int id)
    {

        if(R.id.port == id){
            switch(rudState){
                case mds:
                    nextRudState = RudState.p10;
                    break;
                case p10:
                    nextRudState = RudState.p20;
                    break;
                case p20:
                case hap:
                    nextRudState = RudState.hap;
                    break;
                case s10:
                case s20:
                case has:
                default:
                    nextRudState = RudState.mds;
            }
            return;
        }

        if(R.id.starboard == id){
            switch(rudState){
                case mds:
                    nextRudState = RudState.s10;
                    break;
                case p10:
                case p20:
                case hap:
                    nextRudState = RudState.mds;
                    break;
                case s10:
                    nextRudState = RudState.s20;
                    break;
                case s20:
                    nextRudState = RudState.has;
                    break;
                case has:
                    nextRudState = RudState.has;
                    break;
                default:
                    nextRudState = RudState.mds;
            }
            return;
        }

        nextRudState = RudState.mds;
    }
}
