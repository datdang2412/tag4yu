package com.tag4yu.uhfdemo;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.serialport.SerialPortManager;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Toast;

import com.couchbase.lite.AbstractReplicator;
import com.couchbase.lite.BasicAuthenticator;
import com.couchbase.lite.CouchbaseLiteException;
import com.couchbase.lite.DataSource;
import com.couchbase.lite.Database;
import com.couchbase.lite.DatabaseConfiguration;
import com.couchbase.lite.Document;
import com.couchbase.lite.Endpoint;
import com.couchbase.lite.Expression;
import com.couchbase.lite.LogDomain;
import com.couchbase.lite.LogLevel;
import com.couchbase.lite.Meta;
import com.couchbase.lite.MutableDocument;
import com.couchbase.lite.Ordering;
import com.couchbase.lite.Query;
import com.couchbase.lite.QueryBuilder;
import com.couchbase.lite.Replicator;
import com.couchbase.lite.ReplicatorChange;
import com.couchbase.lite.ReplicatorChangeListener;
import com.couchbase.lite.ReplicatorConfiguration;
import com.couchbase.lite.Result;
import com.couchbase.lite.ResultSet;
import com.couchbase.lite.SelectResult;

import com.couchbase.lite.URLEndpoint;
import com.zistone.uhf.ZstCallBackListen;
import com.zistone.uhf.ZstUHFApi;


import java.io.File;
import java.net.URI;
import java.security.InvalidParameterException;
import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    public static final String TAG = "Dung.LV MainAct";

    private ZstUHFApi mZstUHFApi;
    private int gpio1_num = 81, gpio2_num = 113;
    private String serialName = "/dev/ttyHSL1";
    private boolean isStarting = false;
    private final int STATE_NO_THING = 0;//0
    private final int STATE_START_INVENTORY = STATE_NO_THING + 1;//1
    private final int STATE_SET_POWER = STATE_START_INVENTORY + 1;//2
    private final int STATE_GET_POWER = STATE_SET_POWER + 1;//3
    private final int STATE_SET_CHANNEL = STATE_GET_POWER + 1;//4
    private final int STATE_GET_CHANNEL = STATE_SET_CHANNEL + 1;//5
    private final int STATE_SET_PARAM = STATE_GET_CHANNEL + 1;//6
    private final int STATE_GET_PARAM = STATE_SET_PARAM + 1;//7
    private final int STATE_READ_TAG = STATE_GET_PARAM + 1;//8
    private final int STATE_WRITE_TAG = STATE_READ_TAG + 1;//9
    private final int STATE_SET_SELECT_COMMOND = STATE_WRITE_TAG + 1;//10
    private final int STATE_SET_SELECT_MODE = STATE_SET_SELECT_COMMOND + 1;//11

    private int m_opration = STATE_NO_THING;
    private String ss;

    //Giao diện
    ListView lstEPC;
    ArrayList<String> arrEPC = new ArrayList<>();
    ArrayAdapter<String> adapter;
    private static MainActivity instance;

    // Giao diện Demo
    ListView listView;
    EditText userNameEditText;
    Button btnCheckUserMatch;
    ArrayList<String> userListDemo;
    Replicator replicator;
    // couchbase
    Database database;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        //Khởi tạo cho UHF module
        String model = Build.MODEL;
        if (model.contains("msm8953")) {
            gpio1_num = 66;
            gpio2_num = 98;
            serialName = "/dev/ttyHSL0";
        }

        //Khởi tạo cho GUI
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fabRead);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                startReading(view);//Bắt đầu tìm UHF Tag
//                Snackbar.make(view, "Scanning..", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();

            }
        });
        instance = this;
        lstEPC = findViewById(R.id.lstEPC);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, arrEPC);
        lstEPC.setAdapter(adapter);


        //Khởi tạo UHF API
        mZstUHFApi = new ZstUHFApi(this, new MyZstUhfListen());
        mZstUHFApi.setModelPower(true, gpio1_num, gpio2_num);

        // Test và demo với Couchbase
        this.initCouchBase();
//        this.initAndShowModalDialog();
    }


    public static MainActivity getInstance() {
        return instance;
    }

    @Override
    protected void onStart() {
        super.onStart();
        openScanDevice();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Stop();
        if (mZstUHFApi != null)
            mZstUHFApi.closeDevice();
    }

    @Override
    public void onDestroy() {
        mZstUHFApi.setModelPower(false, gpio1_num, gpio2_num);
        super.onDestroy();
        System.exit(0);
    }


    private int openScanDevice() {
        String path = serialName;//sp.getString("DEVICE", SerialName);
        int baud_rate = 115200;//Integer.decode(sp.getString("BAUDRATE", getString(R.string.baud_rate_def)));
        int data_bits = 8;//Integer.decode(sp.getString("DATA", getString(R.string.data_bits_def)));
        int stop_bits = 1;//Integer.decode(sp.getString("STOP", getString(R.string.stop_bits_def)));
        int flow = 0;
        int parity = 'N';
        String flow_ctrl = "None";//sp.getString("FLOW", getString(R.string.flow_control_def));
        String parity_check = "None";//sp.getString("PARITY", getString(R.string.parity_check_def));
        Log.d(TAG, "baud_rate = " + baud_rate);
        /* Check parameters */
        if ((path.length() == 0) || (baud_rate == -1)) {
            throw new InvalidParameterException();
        }
        Log.d(TAG, "path = " + path);
        if (flow_ctrl.equals("RTS/CTS"))
            flow = 1;
        else if (flow_ctrl.equals("XON/XOFF"))
            flow = 2;

        if (parity_check.equals("Odd"))
            parity = 'O';
        else if (parity_check.equals("Even"))
            parity = 'E';

        int retOpen = -1;
        if (mZstUHFApi != null) {
            retOpen = mZstUHFApi.opendevice(
                    new File(path), baud_rate, flow,
                    data_bits, stop_bits, parity, gpio1_num);
        }
        Log.d(TAG, "retOpen = " + retOpen);
//		btn_scan_one.setEnabled(false);
//		btn_scan_continuous.setEnabled(false);
        if (retOpen == SerialPortManager.RET_OPEN_SUCCESS ||
                retOpen == SerialPortManager.RET_DEVICE_OPENED) {
//			btn_scan_one.setEnabled(true);
//			btn_scan_continuous.setEnabled(true);
//			isOpened = true;
        } else if (retOpen == SerialPortManager.RET_NO_PRTMISSIONS) {
            Log.d(TAG, "SerialPortManager.RET_NO_PRTMISSIONS");//DisplayError(R.string.error_security);
        } else if (retOpen == SerialPortManager.RET_ERROR_CONFIG) {
            Log.d(TAG, "SerialPortManager.RET_ERROR_CONFIG");//DisplayError(R.string.error_configuration);
        } else {
            Log.d(TAG, "SerialPortManager.ERROR_UNKNOWN");//DisplayError(R.string.error_unknown);
        }
        return retOpen;
    }

    private void Stop() {
        if (isStarting) {
            //m_opration = STATE_NO_THING;
            if (mZstUHFApi != null)
                mZstUHFApi.stopInventory();
            isStarting = false;
        }
    }

    private void AddListEPC(final String epc) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                //Kiểm tra thủ công
                /*
                416C69656E48696767733335
                496D70696E6A4D6F6E7A6134
                496D70696E6A204D6F6E7A6120344431
                416C69656E204869676773203431
                416C69656E204869676773203432
                 */
                String new_epc = "";
                if (epc.equalsIgnoreCase("416C69656E48696767733335"))
                    new_epc = epc + " - #1 Alien Passive RFID Windshield Tags";
                if (epc.equalsIgnoreCase("496D70696E6A4D6F6E7A6134"))
                    new_epc = epc + " - Confidex ";
                if (epc.equalsIgnoreCase("496D70696E6A204D6F6E7A6120344431"))
                    new_epc = epc + " - #5 SMARTRAC DogBone";
                if (epc.equalsIgnoreCase("416C69656E204869676773203431"))
                    new_epc = epc + " - #2 High Point Piano & Music Inc";
                if (epc.equalsIgnoreCase("416C69656E204869676773203432"))
                    new_epc = epc + " - #4 Iron work inc";
                arrEPC.add(new_epc);
                lstEPC.setAdapter(adapter);
            }
        });
    }

    //Kiểm tra có trùng cái có sẵn chưa, chưa có thì Add vào luôn
    //Trùng trả về true, không trùng là false
    public boolean checkAndAddEPC(String new_epc) {
        if (arrEPC == null) return false;
        for (int i = 0; i < arrEPC.size(); i++) {
            if (arrEPC.get(i).startsWith(new_epc)) {
                //Trùng
                return true;
            }
        }
        AddListEPC(new_epc);//Ko trùng, thêm vào luôn
        return false;
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
//        if (id == R.id.action_settings) {
//            return true;
//        }

        switch (id) {
            case R.id.action_settings:

                break;

            case R.id.mnuClear:
                arrEPC.clear();
                lstEPC.setAdapter(adapter);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void startReading(View view) {
        Log.d(TAG, "startReading()");
        if (!isStarting) {
            Log.d(TAG, "startReading() !isStarting");
            if (mZstUHFApi != null) {
                Log.d(TAG, "startReading() (mZstUHFApi != null)");
                mZstUHFApi.startInventory(2019);
                m_opration = STATE_START_INVENTORY;
                Log.d(TAG, "startReading() startInventory(2019)");
                Snackbar.make(view, "Scanning..", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                isStarting = true;
            }
        } else {
            Log.d(TAG, "startReading() is isStarting");
            if (mZstUHFApi != null) {
                mZstUHFApi.stopInventory();
                m_opration = STATE_NO_THING;
                Log.d(TAG, "startReading() stopInventory()");
                Snackbar.make(view, "Stopped..", Snackbar.LENGTH_LONG)
                        .setAction("Action", null).show();
                isStarting = false;
            }
        }

    }

    private void onDataReceived(byte[] buffer, int size) {
        int endTag = 1;
        Log.d(TAG, "onDataReceived has data size " + size);
        for (int i = 0; i < size; i++) {
            String oneByte = Util.OneByte2Hex(buffer[i]);
            Log.d(TAG, "onDataReceived OneByte2Hex = " + oneByte);
            if (oneByte.equals("BB")) {
                if (endTag == 1)
                    ss = Util.OneByte2Hex(buffer[i]) + " ";
                else {
                    ss += Util.OneByte2Hex(buffer[i]) + " ";
                    endTag = 0;
                }
            } else {
                ss += Util.OneByte2Hex(buffer[i]) + " ";
                endTag = 0;
            }

            if (oneByte.equals("7E")) {
                endTag = 1;
                if (true == Util.CheckSum(ss)) {
                    Log.d(TAG, "m_opration = " + m_opration);
                    if (m_opration == STATE_START_INVENTORY) {
                        Log.d(TAG, "ss.length() = " + ss.length());
                        if (ss.length() > 52) {
                            String data = ss.trim().replaceAll(" ", "");
                            int pl_h = Util.ToInt(data.substring(6, 8));
                            int pl_l = Util.ToInt(data.substring(8, 10));
                            int plen = (pl_h * 256 + pl_l) * 2;
                            int RSSI = Util.ToInt(data.substring(10, 12));
                            int pc_h = Util.ToInt(data.substring(12, 14));
                            int pc_l = Util.ToInt(data.substring(14, 16));
                            int pc = pc_h * 256 + pc_l;
                            String epc = "";
                            Log.d(TAG, "data.length() = " + data.length());
                            Log.d(TAG, "plen = " + plen);
                            if (plen >= 10 && data.length() >= 16 + plen - 10) {
                                epc = data.substring(16, 16 + plen - 10);
                                //Log.d(TAG, "epc = "+ epc);
                            }
                            MainActivity.getInstance().checkAndAddEPC(epc);


                            //addToList(listEPC, epc);
                            Log.d(TAG, "epc = " + epc);
                            Log.d(TAG, "RSSI = " + RSSI);
                            Log.d(TAG, "pc = " + pc);

                        } else {// chưa đủ 1 EPC
//
                        }
                    }
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            String recvStr = ss.trim().replaceAll(" ", "");
                            switch (m_opration) {
//                                case STATE_SET_POWER:
//                                    if (recvStr.substring(4, 6).equals("B6")){
//                                        tv_info.setText(getString(R.string.set_power_success));
//                                    } else {
//                                        tv_info.setText(getString(R.string.set_power_fail));
//                                    }
//                                    break;
//                                case STATE_GET_POWER:
//                                    if (recvStr.substring(4, 6).equals("B7")){
//                                        tv_info.setText(getString(R.string.get_power_success));
//                                        edittext_pwr.setText(Util.GetPower(ss.trim()));
//                                    } else {
//                                        tv_info.setText(getString(R.string.get_power_fail));
//                                    }
//                                    break;
//                                case STATE_SET_CHANNEL:
//                                    if (recvStr.substring(4, 6).equals("AB")){
//                                        tv_info.setText(getString(R.string.set_channel_success));
//                                    } else {
//                                        tv_info.setText(getString(R.string.set_channel_fail));
//                                    }
//                                    break;
//                                case STATE_GET_CHANNEL:
//                                    if (recvStr.substring(4, 6).equals("AA")){
//                                        tv_info.setText(getString(R.string.get_channel_success));
////										curArrValue = Integer.parseInt(recvStr.substring(10, 12));
////										sp_country_set.setSelection(curArrValue);
//                                    } else {
//                                        tv_info.setText(getString(R.string.get_channel_fail));
//                                    }
//                                    break;
//                                case STATE_SET_PARAM:
//                                    if (recvStr.substring(4, 6).equals("F0")){
//                                        tv_info.setText(getString(R.string.set_param_success));
//                                    } else {
//                                        tv_info.setText(getString(R.string.set_param_fail));
//                                    }
//                                    break;
//                                case STATE_GET_PARAM:
//                                    if (recvStr.substring(4, 6).equals("F1")){
//                                        tv_info.setText(getString(R.string.get_param_success));
//                                        if(recvStr.length() >= 12){
//                                            curMixeValue = Integer.parseInt(recvStr.substring(10, 12));
//                                            sp_mixe_set.setSelection(curMixeValue);
//                                        }
//                                        if(recvStr.length() >= 14){
//                                            curIfampValue = Integer.parseInt(recvStr.substring(12, 14));
//                                            sp_ifamp_set.setSelection(curIfampValue);
//                                        }
//                                        if(recvStr.length() >= 18){
//                                            String thrd_str = recvStr.substring(14, 18);
//                                            if(thrd_str != null){
//                                                Log.d(TAG, "thrd_str = "+thrd_str);
//                                                if(recvStr.length() >= 4){
//                                                    int thred_h = Util.toInt(thrd_str.substring(0,2));
//                                                    int thred_l = Util.toInt(thrd_str.substring(2,4));
//                                                    Log.d(TAG, "thred_h = "+thred_h);
//                                                    Log.d(TAG, "thred_l = "+thred_l);
//                                                    mThrd = thred_h*256 + thred_l;
//                                                    Log.d(TAG, "mThrd = "+mThrd);
//                                                    edittext_thrd.setText(String.valueOf(mThrd));
//                                                }
//                                            }
//                                        }
//                                    } else {
//                                        tv_info.setText(getString(R.string.get_param_fail));
//                                    }
//                                    break;
//                                case STATE_READ_TAG:
//                                    if (recvStr.substring(4, 6).equals("39")) {
//                                        // 读取成功
//                                        if (getResources().getConfiguration().locale
//                                                .getCountry().equals("CN")){
//                                            textViewEPC.setText(recvStr.substring(8*2, (8+12)*2));
//                                            editReadData.append("读取:"
//                                                    + recvStr.substring(recvStr.length()
//                                                            - 4 - length * 4,
//                                                    recvStr.length() - 4) + "\n");
//                                        }
//                                        else{
//                                            textViewEPC.setText(recvStr.substring(8*2, (8+12)*2));
//                                            editReadData.append("Read:"
//                                                    + recvStr.substring(recvStr.length()
//                                                            - 4 - length * 4,
//                                                    recvStr.length() - 4) + "\n");
//                                        }
//                                    } else {
//                                        // 读取失败
//                                        if (getResources().getConfiguration().locale
//                                                .getCountry().equals("CN"))
//                                            editReadData.append("读取失败 \n");
//                                        else
//                                            editReadData.append("Read Fail \n");
//                                    }
//                                    if(m_read_tag == 4){
//                                        mHandler.removeMessages(m_read_tag);
//                                        mHandler.sendEmptyMessage(m_read_tag);
//                                    }
//                                    break;
//                                case STATE_WRITE_TAG:// 写标签
//                                    if (recvStr.substring(4, 6).equals("49")) {
//                                        // 读取成功
//                                        if (getResources().getConfiguration().locale
//                                                .getCountry().equals("CN"))
//                                            editReadData.append("写入成功" + "\n");
//                                        else
//                                            editReadData.append("Write Success" + "\n");
//                                    } else {
//                                        // 读取失败
//                                        if (getResources().getConfiguration().locale
//                                                .getCountry().equals("CN"))
//                                            editReadData.append("写入失败 \n");
//                                        else
//                                            editReadData.append("Write Fail \n");
//                                    }
//                                    if(m_read_tag == 4){
//                                        mHandler.removeMessages(m_read_tag);
//                                        mHandler.sendEmptyMessage(m_read_tag);
//                                    }
//                                    break;
//                                case STATE_SET_SELECT_MODE:
//                                    if(m_read_tag != 0){
//                                        Log.d(TAG, "select mode success");
//                                        mHandler.removeMessages(2);
//                                        mHandler.sendEmptyMessage(2);
//                                    }
//                                    break;
//                                case STATE_SET_SELECT_COMMOND:
//                                    if(m_read_tag != 0){
//                                        Log.d(TAG, "select commod success");
//                                        mHandler.removeMessages(3);
//                                        mHandler.sendEmptyMessage(3);
//                                    }
//                                    break;
//                                default:break;
                            }
                        }
                    });
                } else {
                    Log.e(TAG, "checkSum if fail!!!");
                }
            }
        }
        //lostTag = ss;
    }

    private void initAndShowModalDialog() {
        AlertDialog.Builder dialogBuilder =	new AlertDialog.Builder(this);
        LayoutInflater inflater	= this.getLayoutInflater();

        @SuppressLint("ResourceType")
        View dialogView	=	inflater.inflate(R.layout.modal_dialog_demo, (ViewGroup)findViewById(R.layout.activity_main));

        listView = (ListView) dialogView.findViewById(R.id.listViewUserName);
        btnCheckUserMatch = (Button)  dialogView.findViewById(R.id.btnCheckUserMatch);
        userNameEditText = (EditText)  dialogView.findViewById(R.id.txtInputUserName);
        this.onBtnCheckUserMatchClickHandler();

        ArrayAdapter<String> arrayAdapter
                = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_checked , userListDemo);
        listView.setAdapter(arrayAdapter);

        dialogBuilder.setView(dialogView);
        dialogBuilder.create().show();
    }

    private void onBtnCheckUserMatchClickHandler() {
        btnCheckUserMatch.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String keyWord = userNameEditText.getText().toString();
                Query query = QueryBuilder
                        .select(SelectResult.expression(Meta.id),
                                SelectResult.property("identity"))
                        .from(DataSource.database(database))
                        .where(Expression.property("identity").equalTo(Expression.string(keyWord)))
                        .orderBy(Ordering.expression(Meta.id));
                try {
                    ResultSet rs = query.execute();
                    int resultCount = 0;
                    for(Result result: rs) { resultCount++; }
                    if (resultCount > 0) {
                        Toast.makeText(MainActivity.this, "Access granted", Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(MainActivity.this, "Access denied", Toast.LENGTH_SHORT).show();
                    }
                } catch (CouchbaseLiteException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void initCouchBase() {
        Log.i(TAG, "Init couchbase ::  ");
        DatabaseConfiguration config = new DatabaseConfiguration(getApplicationContext());
        Database.setLogLevel(LogDomain.REPLICATOR, LogLevel.VERBOSE);
        Database.setLogLevel(LogDomain.QUERY, LogLevel.VERBOSE);
        userListDemo = new ArrayList<>();
        try {
            database = new Database("tag4u_db", config);
            Query query = QueryBuilder
                    .select(SelectResult.expression(Meta.id),
                            SelectResult.property("username"),
                            SelectResult.property("identity"))
                    .from(DataSource.database(database))
                    .orderBy(Ordering.expression(Meta.id));

            try {
                int resultCount = 0;
                ResultSet rs = query.execute();

                for (Result result : rs) {
                    userListDemo.add("username: " + result.getString("username") + " - identity: "+ result.getString("identity"));
                    resultCount++;
                }

                Log.i(TAG, "Result count ::  " + resultCount);

                if (resultCount == 30) {
                    MutableDocument mutableDoc = new MutableDocument().setString("username", "user1").setString("identity", "300833B2DDD9014000000000");
                    MutableDocument mutableDoc1 = new MutableDocument().setString("username", "user2").setString("identity", "E20040843904023916106FC5");
                    MutableDocument mutableDoc2 = new MutableDocument().setString("username", "user3").setString("identity", "E20040843904023917805EDB");
                    database.save(mutableDoc);
                    database.save(mutableDoc1);
                    database.save(mutableDoc2);

                    Query newQuery = QueryBuilder
                            .select(SelectResult.expression(Meta.id),
                                    SelectResult.property("username"),
                                    SelectResult.property("identity"))
                            .from(DataSource.database(database))
                            .orderBy(Ordering.expression(Meta.id));

                    ResultSet newResultSet = newQuery.execute();
                    for (Result result : newResultSet) {
                        userListDemo.add("username: " + result.getString("username") + " - identity: " + result.getString("identity"));
                    }
                }
            } catch (CouchbaseLiteException e) {
                Log.e("Sample", e.getLocalizedMessage());
            }

            // Create replicators to push and pull changes to and from the cloud.
            Endpoint targetEndpoint = new URLEndpoint(new URI("ws://tag4yu.sstechvn.com:4984/tag4yu_db"));
            ReplicatorConfiguration replConfig = new ReplicatorConfiguration(database, targetEndpoint);
            replConfig.setReplicatorType(ReplicatorConfiguration.ReplicatorType.PUSH_AND_PULL);
            replConfig.setContinuous(true);

// Add authentication.
            replConfig.setAuthenticator(new BasicAuthenticator("tag4yu", "tag4yu"));

// Create replicator.
            replicator = new Replicator(replConfig);

// Listen to replicator change events.
            replicator.addChangeListener(new ReplicatorChangeListener() {
                @Override
                public void changed(ReplicatorChange change) {
                    if (change.getStatus().getError() != null) {
                        Log.i(TAG, "Error code ::  " + change.getStatus().getError().getCode());
                    }
                    Log.i(TAG, "Change status ::  " + change.getStatus().toString());
                    if (change.getStatus().getActivityLevel() == AbstractReplicator.ActivityLevel.IDLE) {
                        userListDemo = new ArrayList<>();
                        Query query = QueryBuilder
                                .select(SelectResult.expression(Meta.id),
                                        SelectResult.property("username"),
                                        SelectResult.property("identity"))
                                .from(DataSource.database(database))
                                .orderBy(Ordering.expression(Meta.id));

                        try {
                            int resultCount = 0;
                            ResultSet rs = query.execute();

                            for (Result result : rs) {
                                userListDemo.add("username: " + result.getString("username") + " - identity: "+ result.getString("identity"));
                                resultCount++;
                            }

                            Log.i(TAG, "Result count ::  " + resultCount);

                        } catch (CouchbaseLiteException e) {
                            Log.e("Sample", e.getLocalizedMessage());
                        }
                    }
                }
            });

// Start replication.
            replicator.start();
            Log.i(TAG, "Replicator started ::  ");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public class MyZstUhfListen implements ZstCallBackListen {
        @Override
        public void onUhfReceived(byte[] data, int len) {
            // TODO Auto-generated method stub
            onDataReceived(data, len);
        }
    }
}
