package com.yanbo.lib_screen.manager;

import android.support.annotation.NonNull;

import com.yanbo.lib_screen.VApplication;
import com.yanbo.lib_screen.entity.ClingDevice;
import com.yanbo.lib_screen.listener.OnDeviceListChangeListener;

import org.fourthline.cling.model.meta.Device;
import org.fourthline.cling.model.types.DeviceType;
import org.fourthline.cling.model.types.UDADeviceType;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Created by lzan13 on 2018/3/9.
 * 设备管理器，保存当前包含设备列表，以及当前选中设备
 */
public class DeviceManager {
    /** DMR 设备 类型*/
    public static final DeviceType DMR_DEVICE = new UDADeviceType("MediaRenderer");

    private static DeviceManager instance;
    private List<ClingDevice> clingDeviceList;
    private ClingDevice currClingDevice;

    private WeakReference<OnDeviceListChangeListener> mDeviceListChangeListener
            = new WeakReference<>(null);

    /**
     * 私有构造方法
     */
    private DeviceManager() {
        if (clingDeviceList == null) {
            clingDeviceList = Collections.synchronizedList(new ArrayList<ClingDevice>());
        }
        clingDeviceList.clear();
    }

    /**
     * 唯一获取单例对象实例方法
     */
    public static DeviceManager getInstance() {
        if (instance == null) {
            instance = new DeviceManager();
        }
        return instance;
    }

    /**
     * 获取当前 Cling 设备
     */
    public ClingDevice getCurrClingDevice() {
        return currClingDevice;
    }

    /**
     * 设置当前 Cling 设备
     */
    public void setCurrClingDevice(ClingDevice currClingDevice) {
        this.currClingDevice = currClingDevice;
    }

    /**
     * 添加设备到设备列表
     */
    public void addDevice(@NonNull Device device) {
        if (device.getType().equals(DMR_DEVICE)) {
            ClingDevice clingDevice = new ClingDevice(device);
            clingDeviceList.add(clingDevice);
            if(mDeviceListChangeListener.get() != null){
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        mDeviceListChangeListener.get().onDeviceListChanged(clingDeviceList);
                    }
                });
            }
        }
    }

    /**
     * 从设备列表移除设备
     */
    public void removeDevice(@NonNull Device device) {
        ClingDevice clingDevice = getClingDevice(device);
        if (clingDevice != null) {
            clingDeviceList.remove(clingDevice);
        }
    }

    /**
     * 获取设备
     */
    public ClingDevice getClingDevice(@NonNull Device device) {
        for (ClingDevice tmpDevice : clingDeviceList) {
            if (device.equals(tmpDevice.getDevice())) {
                return tmpDevice;
            }
        }
        return null;
    }

    /**
     * 获取设备列表
     */
    public List<ClingDevice> getClingDeviceList() {
        return clingDeviceList;
    }

    /**
     * 设置设备列表
     */
    public void setClingDeviceList(List<ClingDevice> list) {
        clingDeviceList = list;
    }


    /**
     * 销毁
     */
    public void destroy() {
        if (clingDeviceList != null) {
            clingDeviceList.clear();
        }
    }

    public void setDeviceListChangeListener(OnDeviceListChangeListener listenser){
        mDeviceListChangeListener = new WeakReference<>(listenser);
    }
}
