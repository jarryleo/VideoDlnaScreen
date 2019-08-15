package com.yanbo.lib_screen.listener;

import com.yanbo.lib_screen.entity.CastState;

/**
 * @author : ling luo
 * @date : 2019-08-15
 */
public interface OnControlStatusChangedListener {

    /**
     * 播放状态改变
     * @param state 状态
     */
    void onStatusChanged(CastState state);

    /**
     * 投屏接受端音量变化回调
     * @param volume 声音大小 0 为静音
     * @param isMute 是否静音
     */

    void onVolumeChanged(int volume,boolean isMute);

    /**
     * 播放进度回调
     * @param absTime 进度时间
     * @param duration 总时长
     */
    void onProgressChange(long absTime,long duration);

}
