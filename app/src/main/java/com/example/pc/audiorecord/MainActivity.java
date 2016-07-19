package com.example.pc.audiorecord;

import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutput;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.io.Serializable;
import java.util.Arrays;

public class MainActivity extends AppCompatActivity {

    private static final int RECORDERSAMPLERATE = 16000;
    private static final int RECORDERCHANNEL = AudioFormat.CHANNEL_IN_MONO;
    private static final int RECORDERAUDIOENCODING = AudioFormat.ENCODING_PCM_16BIT;

    private AudioRecord  recorder = null;
    private Thread recoderThread = null;
    private Thread audioPlayThread = null;
    private boolean isRecording = false;
    private boolean isAudioPlay = true;



    private Button bStartRecord;
    private Button bStopRecord;

    int bufferElements2Rec = 512;
    int bytesPerElement = 2;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bStartRecord = (Button)findViewById(R.id.button);
        bStopRecord = (Button)findViewById(R.id.button2);


        audioPlayThread = new Thread(audioPlayProc);
        audioPlayThread.start();

        bStartRecord.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View v) {
                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC,
                        RECORDERSAMPLERATE, RECORDERCHANNEL,
                        RECORDERAUDIOENCODING, bufferElements2Rec * bytesPerElement);
                recoderThread = new Thread(RecordProc);
                recoderThread.start();
                isRecording = true;
            }
        });

        bStopRecord.setOnClickListener(new Button.OnClickListener(){
            @Override
            public void onClick(View view) {
                if (null != recorder) {
                    isRecording = false;


                    recorder.stop();
                    recorder.release();

                    recorder = null;
                    recoderThread = null;
                }
            }
        });


        //int bufferSize = AudioRecord.getMinBufferSize(RECORDERSAMPLERATE , RECORDERCHANNEL , RECORDERAUDIOENCODING);
    }



    private Runnable RecordProc = new Runnable() {
        @Override
        public void run() {
            writeAudioDataToFile();
        }
    };

    private void writeAudioDataToFile() {
        String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/16k16bitMono.pcm";
        System.out.println(filePath);
        short sData[] = new short[bufferElements2Rec];
        FileOutputStream os = null;
        try{
            os = new FileOutputStream(filePath);
        }
        catch(Exception e){
            e.printStackTrace();
        }


        byte[] sendPacket = new byte[1032];
        byte[] prePacket = {'A' , 'U' , 'D' ,'X' , 0  , 0 , 0 , 0};
        System.arraycopy(prePacket , 0 , sendPacket , 0 , prePacket.length);

        DatagramPacket dp = null;
        DatagramSocket ds = null;
        InetAddress serverIP = null;
        int serverPort = 7080;

        try{
            serverIP = InetAddress.getByName("192.168.25.1");
            ds = new DatagramSocket();
        }
        catch(Exception e){
            e.printStackTrace();
        }

        byte bData[] = new byte[bufferElements2Rec * 2];
        while(isRecording){
            recorder.read(bData , 0 , bufferElements2Rec);
            //System.out.println("Short wirting to file" + sData.toString());
            try{
                //byte bData[] = short2byte(sData);
                //System.arraycopy(bData , 0 , sendPacket , 8 , bData.length);

                dp = new DatagramPacket( sendPacket, sendPacket.length , serverIP , serverPort);
                ds.send(dp);
                //os.write(bData, 0, bufferElements2Rec * bytesPerElement);
            }
            catch (IOException e){
                e.printStackTrace();
            }
        }
        try {
            os.close();
            ds.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private byte[] short2byte(short[] sData) {
        int shortArrsize = sData.length;
        byte[] bytes = new byte[shortArrsize * 2];

        for (int i = 0; i < shortArrsize; i++) {
            bytes[i * 2] = (byte) (sData[i] & 0x00FF);
            bytes[(i * 2) + 1] = (byte) (sData[i] >> 8);
            sData[i] = 0;
        }
        return bytes;
    }

    private Runnable audioPlayProc = new Runnable() {
        @Override
        public void run() {
            int intSize = android.media.AudioTrack.getMinBufferSize(8000, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            AudioTrack at = new AudioTrack(AudioManager.STREAM_MUSIC, 8000, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, intSize, AudioTrack.MODE_STREAM);
            try{
                byte[] recvBuf = new byte[512];
                DatagramPacket dp = new DatagramPacket(recvBuf , recvBuf.length);
                DatagramSocket ds = new DatagramSocket(7080);
                while(isAudioPlay){
                    at.play();
                    ds.receive(dp);
                    at.write(recvBuf , 0 , recvBuf.length);
                }
            }
            catch(Exception e){
                e.printStackTrace();
            }
            finally {
                at.stop();
                at.release();
            }
        }
    };

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isAudioPlay = false;
        isRecording = false;
    }
}
