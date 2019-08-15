package com.yanbo.videodlnascreen;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.widget.Toast;

import com.yanbo.lib_screen.entity.ClingDevice;
import com.yanbo.lib_screen.listener.OnDeviceListChangeListener;
import com.yanbo.lib_screen.manager.ClingManager;
import com.yanbo.lib_screen.manager.DeviceManager;
import com.yanbo.videodlnascreen.listener.ItemClickListener;

import java.util.List;

/**
 * 描述：
 *
 * @author Yanbo
 * @date 2018/11/6
 */
public class DeviceListActivity extends AppCompatActivity implements OnDeviceListChangeListener {

    RecyclerView recyclerView;
    private LinearLayoutManager layoutManager;
    private ClingDeviceAdapter adapter;
    public static void startSelf(Activity context) {
        Intent intent = new Intent(context, DeviceListActivity.class);
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_test_device_list);
        recyclerView=findViewById(R.id.recycler_view);
        layoutManager = new LinearLayoutManager(this);
        adapter = new ClingDeviceAdapter(this);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        adapter.setItemClickListener(new ItemClickListener() {
            @Override
            public void onItemAction(int action, Object object) {
                ClingDevice device = (ClingDevice) object;
                DeviceManager.getInstance().setCurrClingDevice(device);
                Toast.makeText(getBaseContext(),"选择了设备 " + device.getDevice().getDetails().getFriendlyName(),Toast.LENGTH_LONG).show();
                refresh();
            }
        });
        DeviceManager.getInstance().setDeviceListChangeListener(this);
    }

    public void refresh() {
        if (adapter == null) {
            adapter = new ClingDeviceAdapter(this);
            recyclerView.setAdapter(adapter);
        }
        adapter.refresh();
    }

    @Override
    public void onStart() {
        super.onStart();
        ClingManager.getInstance().searchDevices();
    }

    @Override
    public void onDeviceListChanged(List<ClingDevice> deviceList) {
        refresh();
    }
}
