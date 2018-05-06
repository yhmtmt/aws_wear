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


public class MainActivity extends WearableActivity implements View.OnClickListener {
    public static final String PREFS_NAME = "aws2PrefsFile";

    // for communication
    AWSCom awsCom;
    String addr = "192.168.11.12";
    int port = 20000;

    boolean bInitStateParam = false;
    int[] stateParam = new int[17];
    String[] strStateParam = {"stp", "dsah", "slah","hfah", "flah", "nf", "dsas", "slas", "hfas", "flas", "mds", "p10", "p20", "hap", "s10", "s20", "has"};
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
                    String[] vStrs = new String[17];
                    vStrs[0] = null;
                    if(!bInitStateParam){
                        if(awsCom.cmdFget(strUIFilter, strStateParam, vStrs)){
                            for(int iparam = 0; iparam < vStrs.length; iparam++){
                                stateParam[iparam] = Integer.parseInt(vStrs[iparam]);
                            }
                            bInitStateParam = true;
                            return;
                        }else{
                            throw new IOException();
                        }
                    }

                    vStrs = new String[1];
                    // setting order if needed
                    if (nextEngState != engState) {
                        vStrs[0] = nextEngState.name();
                    } else if (nextRudState != rudState) {
                        vStrs[0] = nextRudState.name();
                    }

                    if (vStrs[0] != null) {
                        if(!awsCom.cmdFset(strUIFilter, strSetParams, vStrs))
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
                        engState = getEngState(eng);
                        rudState = getRudState(rud);
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
                    btnForward.setText(getEngStateIconString(getNextFEngState()));
                    btnBackward.setText(getEngStateIconString(getNextBEngState()));
                    btnPort.setText(getRudStateIconString(getNextPRudState()));
                    btnStarboard.setText(getRudStateIconString(getNextSRudState()));
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

    protected EngState getEngState(int eng){
        if(eng > stateParam[0]) {
            if (eng < stateParam[1]) {
                return EngState.stp;
            }else if(eng < stateParam[2]){
                return EngState.dsah;
            }else if(eng < stateParam[3]){
                return EngState.slah;
            }else if(eng < stateParam[4]){
                return EngState.hfah;
            }else if(eng < stateParam[5]){
                return EngState.flah;
            }else{
                return EngState.nf;
            }
        }else{
            if(eng > stateParam[6]){
                return EngState.stp;
            }else if(eng > stateParam[7]){
                return EngState.dsas;
            }else if(eng > stateParam[8]){
                return EngState.slas;
            }else if(eng > stateParam[9]){
                return EngState.hfas;
            }else{
                return EngState.flas;
            }
        }
    };

    protected RudState getRudState(int rud){
        if(rud < stateParam[10]){
            if(rud > stateParam[11]){
                return RudState.mds;
            }else if(rud > stateParam[12]){
                return RudState.p10;
            }else if(rud > stateParam[13]){
                return RudState.p20;
            }else{
                return RudState.hap;
            }
        }else{
            if(rud < stateParam[14]){
                return RudState.mds;
            }else if(rud < stateParam[15]){
                return RudState.s10;
            }else if(rud < stateParam[16]){
                return RudState.s20;
            }else{
                return RudState.has;
            }
        }
    };

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

    protected EngState getNextFEngState()
    {
        switch(engState){
            case stp:
                return engState.dsah;
            case dsah:
                return engState.slah;
            case slah:
                return engState.hfah;
            case hfah:
                return engState.flah;
            case flah:
                return engState.nf;
            case nf:
                return engState.nf;
            case dsas:
                return  engState.stp;
            case slas:
                return engState.dsas;
            case hfas:
                return engState.slas;
            case flas:
                return engState.hfas;
            default:
                return engState.stp;
        }
    }

    protected EngState getNextBEngState()
    {
        switch(engState){
            case stp:
                return engState.dsas;
            case dsas:
                return engState.slas;
            case slas:
                return engState.hfas;
            case hfas:
                return engState.flas;
            case flas:
                return engState.flas;
            case nf:
                return engState.flah;
            case dsah:
                return engState.stp;
            case slah:
                return engState.dsah;
            case hfah:
                return engState.slah;
            case flah:
                return engState.hfah;
            default:
                return engState.stp;
        }
    }

    protected String getEngStateIconString(EngState es)
    {
        switch(es){
            case stp:
                return "N";
            case dsas:
                return "B0";
            case slas:
                return "B1";
            case hfas:
                return "B2";
            case flas:
                return "B3";
            case nf:
                return "F4";
            case dsah:
                return "F0";
            case slah:
                return "F1";
            case hfah:
                return "F2";
            case flah:
                return "F3";
            default:
                return "-";
        }
    }


    public void setNextEngState(int id)
    {
        if(R.id.forward == id) {
            nextEngState = getNextFEngState();
            return;
        }

        if(R.id.backward == id) {
            nextEngState = getNextBEngState();
            return;
        }

        nextEngState = engState.stp;
    }

    protected RudState getNextSRudState()
    {
        switch(rudState){
            case mds:
                return RudState.s10;
            case p10:
                return RudState.mds;
            case p20:
                return RudState.p10;
            case hap:
                return RudState.p20;
            case s10:
                return RudState.s20;
            case s20:
                return RudState.has;
            case has:
                return RudState.has;
            default:
                return RudState.mds;
        }
    }

    protected RudState getNextPRudState()
    {
        switch(rudState){
            case mds:
                return RudState.p10;
            case p10:
                return RudState.p20;
            case p20:
            case hap:
                return RudState.hap;
            case s10:
                return RudState.mds;
            case s20:
                return RudState.s10;
            case has:
                return RudState.s20;
            default:
                return RudState.mds;
        }
    }

    protected String getRudStateIconString(RudState rs)
    {
        switch(rs){
            case mds:
                return "M";
            case p10:
                return "P0";
            case p20:
                return "P1";
            case hap:
                return "P2";
            case s10:
                return "S0";
            case s20:
                return "S1";
            case has:
                return "S2";
            default:
                return "-";
        }
    }

    public void setNextRudState(int id)
    {

        if(R.id.port == id){
            nextRudState = getNextPRudState();
            return;
        }

        if(R.id.starboard == id){
            nextRudState = getNextSRudState();
            return;
        }

        nextRudState = RudState.mds;
    }
}
