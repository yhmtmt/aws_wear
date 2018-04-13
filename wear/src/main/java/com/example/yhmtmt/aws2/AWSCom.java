package com.example.yhmtmt.aws2;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static java.lang.Thread.sleep;

public
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
                if(vStrs.length <= vStrRet.length) {
                    for (int jparam = start; jparam < end; jparam++)
                        vStrs[jparam] = vStrRet[jparam - start];
                }else{
                    return false;
                }
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
            int navailable = 0;
            while ((navailable = reader.available()) < cmdLen && maxReadTries > readTries) {
                sleep(readTryInterval);
                readTries++;
            }
            System.out.println(navailable + "bytes ready to read.");
            if(cmdLen == navailable)
                rcvCount = reader.read(bytes, 0, navailable);
            else
                reader.skip(navailable);

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

