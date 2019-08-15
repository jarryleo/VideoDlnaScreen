package com.yanbo.lib_screen.listener;

import com.yanbo.lib_screen.entity.ClingDevice;

import java.util.List;

/**
 * @author : ling luo
 * @date : 2019-08-15
 */
public interface OnDeviceListChangeListener {
    void onDeviceListChanged(List<ClingDevice> deviceList);
}
