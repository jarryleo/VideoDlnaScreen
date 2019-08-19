package com.yanbo.lib_screen.manager;


import android.os.Handler;
import android.os.HandlerThread;

import com.yanbo.lib_screen.VApplication;
import com.yanbo.lib_screen.VError;
import com.yanbo.lib_screen.callback.AVTransportCallback;
import com.yanbo.lib_screen.callback.ControlCallback;
import com.yanbo.lib_screen.callback.RenderingControlCallback;
import com.yanbo.lib_screen.entity.AVTransportInfo;
import com.yanbo.lib_screen.entity.CastState;
import com.yanbo.lib_screen.entity.ClingDevice;
import com.yanbo.lib_screen.entity.RemoteItem;
import com.yanbo.lib_screen.entity.RenderingControlInfo;
import com.yanbo.lib_screen.listener.OnControlStatusChangedListener;
import com.yanbo.lib_screen.utils.ClingUtil;
import com.yanbo.lib_screen.utils.LogUtils;
import com.yanbo.lib_screen.utils.VMDate;

import org.fourthline.cling.controlpoint.ControlPoint;
import org.fourthline.cling.model.action.ActionInvocation;
import org.fourthline.cling.model.message.UpnpResponse;
import org.fourthline.cling.model.meta.Service;
import org.fourthline.cling.model.types.UDAServiceType;
import org.fourthline.cling.model.types.UnsignedIntegerFourBytes;
import org.fourthline.cling.support.avtransport.callback.GetPositionInfo;
import org.fourthline.cling.support.avtransport.callback.GetTransportInfo;
import org.fourthline.cling.support.avtransport.callback.Pause;
import org.fourthline.cling.support.avtransport.callback.Play;
import org.fourthline.cling.support.avtransport.callback.Seek;
import org.fourthline.cling.support.avtransport.callback.SetAVTransportURI;
import org.fourthline.cling.support.avtransport.callback.Stop;
import org.fourthline.cling.support.contentdirectory.DIDLParser;
import org.fourthline.cling.support.model.DIDLContent;
import org.fourthline.cling.support.model.PositionInfo;
import org.fourthline.cling.support.model.TransportInfo;
import org.fourthline.cling.support.model.TransportState;
import org.fourthline.cling.support.model.item.Item;
import org.fourthline.cling.support.renderingcontrol.callback.GetVolume;
import org.fourthline.cling.support.renderingcontrol.callback.SetMute;
import org.fourthline.cling.support.renderingcontrol.callback.SetVolume;

import java.lang.ref.WeakReference;

/**
 * Created by lzan13 on 2018/3/10.
 * 控制点管理器
 */
public class ControlManager {

    // 视频传输服务
    public static final String AV_TRANSPORT = "AVTransport";
    // DMR 设备的控制服务
    public static final String RENDERING_CONTROL = "RenderingControl";


    private static ControlManager instance;
    private Service avtService;
    private Service rcService;
    private UnsignedIntegerFourBytes instanceId;

    private AVTransportCallback avtCallback;
    private RenderingControlCallback rcCallback;
    private boolean isScreenCast = false;
    private String absTimeStr;
    private long absTime;
    private String trackDurationStr;
    private long trackDuration;

    private CastState state = CastState.STOPPED;
    private boolean isMute = false;

    private WeakReference<OnControlStatusChangedListener> mOnControlStatusChangedListener
            = new WeakReference<>(null);

    private static HandlerThread mHandlerThread;
    private static Handler mHandler;

    private ControlManager() {
        avtService = findServiceFromDevice(AV_TRANSPORT);
        rcService = findServiceFromDevice(RENDERING_CONTROL);
        instanceId = new UnsignedIntegerFourBytes("0");
    }

    public static ControlManager getInstance() {
        if (instance == null) {
            instance = new ControlManager();
            mHandlerThread = new HandlerThread("ThreadGetInfo");
            mHandlerThread.start();
            mHandler = new Handler(mHandlerThread.getLooper());
        }
        return instance;
    }

    /**
     * 开始新的投屏播放，需要先停止上一次的投屏
     *
     * @param item 需要投屏播放的本地资源对象
     */
    public void newPlayCast(final Item item, final ControlCallback callback) {
        if (state == CastState.STOPPED) {
            setState(CastState.TRANSITIONING);
            setLocal(item, callback);
            return;
        }
        setState(CastState.TRANSITIONING);
        stopCast(new ControlCallback() {
            @Override
            public void onSuccess() {
                setLocal(item, callback);
            }

            @Override
            public void onError(final int code, final String msg) {
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(code, msg);
                    }
                });
            }
        });
    }

    private void setLocal(Item item, final ControlCallback callback) {
        setAVTransportURI(item, new ControlCallback() {
            @Override
            public void onSuccess() {
                playCast(callback);
            }

            @Override
            public void onError(final int code, final String msg) {
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(code, msg);
                    }
                });
            }
        });
    }

    /**
     * 开始投屏，需要先停止上一个投屏
     *
     * @param item 需要投屏的远程网络资源对象
     */
    public void newPlayCast(final RemoteItem item, final ControlCallback callback) {
        if (state == CastState.STOPPED) {
            setState(CastState.TRANSITIONING);
            setRemote(item, callback);
            return;
        }
        setState(CastState.TRANSITIONING);
        stopCast(new ControlCallback() {
            @Override
            public void onSuccess() {
                setRemote(item, callback);
            }

            @Override
            public void onError(final int code, final String msg) {
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(code, msg);
                    }
                });
            }
        });
    }

    private void setRemote(RemoteItem item, final ControlCallback callback) {
        setAVTransportURI(item, new ControlCallback() {
            @Override
            public void onSuccess() {
                playCast(callback);
            }

            @Override
            public void onError(final int code, final String msg) {
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(code, msg);
                    }
                });
            }
        });
    }

    /**
     * 播放投屏
     */
    public void playCast(final ControlCallback callback) {
        if (checkAVTService()) {
            callback.onError(VError.SERVICE_IS_NULL, "AVTService is null");
            return;
        }
        ControlPoint controlPoint = ClingManager.getInstance().getControlPoint();
        controlPoint.execute(new Play(instanceId, avtService) {
            @Override
            public void success(ActionInvocation invocation) {
                LogUtils.i("", "Play success");
                if (state != CastState.PAUSED) {
                    initScreenCastCallback();
                }
                setState(CastState.PLAYING);
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess();
                    }
                });
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, final String msg) {
                LogUtils.e("Play error %s", msg);
                setState(CastState.STOPPED);
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(VError.UNKNOWN, msg);
                    }
                });
            }
        });
    }

    /**
     * 暂停投屏
     */
    public void pauseCast(final ControlCallback callback) {
        if (checkAVTService()) {
            callback.onError(VError.SERVICE_IS_NULL, "AVTService is null");
            return;
        }
        ControlPoint controlPoint = ClingManager.getInstance().getControlPoint();
        controlPoint.execute(new Pause(instanceId, avtService) {
            @Override
            public void success(ActionInvocation invocation) {
                LogUtils.i("", "Pause success");
                setState(CastState.PAUSED);
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess();
                    }
                });
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, final String msg) {
                LogUtils.e("Pause error %s", msg);
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(VError.UNKNOWN, msg);
                    }
                });
            }
        });
    }

    /**
     * 停止投屏
     */
    public void stopCast(final ControlCallback callback) {
        if (checkAVTService()) {
            callback.onError(VError.SERVICE_IS_NULL, "AVTService is null");
            return;
        }
        ControlPoint controlPoint = ClingManager.getInstance().getControlPoint();
        controlPoint.execute(new Stop(instanceId, avtService) {
            @Override
            public void success(ActionInvocation invocation) {
                LogUtils.i("", "Stop success");
                setState(CastState.STOPPED);
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess();
                    }
                });
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, final String msg) {
                LogUtils.e("Stop error %s", msg);
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(VError.UNKNOWN, msg);
                    }
                });
            }
        });
    }

    /**
     * 设置投屏进度
     *
     * @param target 目标进度
     */
    public void seekCast(final String target, final ControlCallback callback) {
        if (checkAVTService()) {
            callback.onError(VError.SERVICE_IS_NULL, "AVTService is null");
            return;
        }
        ControlPoint controlPoint = ClingManager.getInstance().getControlPoint();
        controlPoint.execute(new Seek(instanceId, avtService, target) {
            @Override
            public void success(ActionInvocation invocation) {
                LogUtils.d("Seek success - %s", target);
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess();
                    }
                });
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, final String msg) {
                LogUtils.e("Seek error %s", msg);
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(VError.UNKNOWN, msg);
                    }
                });
            }
        });
    }

    /**
     * -------------- RCService 相关操作 --------------
     * 设置投屏音量
     */
    public void setVolumeCast(int volume, final ControlCallback callback) {
        if (checkRCService()) {
            callback.onError(VError.SERVICE_IS_NULL, "RCService is null");
            return;
        }
        if (volume < 0 || volume > 65535) {
            return;
        }
        ControlPoint controlPoint = ClingManager.getInstance().getControlPoint();
        controlPoint.execute(new SetVolume(instanceId, rcService, volume) {
            @Override
            public void success(ActionInvocation invocation) {
                LogUtils.d(" ", "setVolume success");
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess();
                    }
                });
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, final String msg) {
                LogUtils.e("setVolume error %s", msg);
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(VError.UNKNOWN, msg);
                    }
                });
            }
        });
    }

    /**
     * 静音投屏
     */
    public void muteCast(boolean mute, final ControlCallback callback) {
        if (checkRCService()) {
            callback.onError(VError.SERVICE_IS_NULL, "RCService is null");
            return;
        }
        ControlPoint controlPoint = ClingManager.getInstance().getControlPoint();
        controlPoint.execute(new SetMute(instanceId, rcService, mute) {
            @Override
            public void success(ActionInvocation invocation) {
                LogUtils.d("", "Mute success");
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess();
                    }
                });
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, final String msg) {
                LogUtils.e("Mute error %s", msg);
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(VError.UNKNOWN, msg);
                    }
                });
            }
        });
    }

    /**
     * 设置本地资源音视频传输 URI
     *
     * @param item 需要投屏的资源
     */
    public void setAVTransportURI(Item item, final ControlCallback callback) {
        if (checkAVTService()) {
            callback.onError(VError.SERVICE_IS_NULL, "service is null");
            return;
        }
        final String uri = item.getFirstResource().getValue();
        DIDLContent content = new DIDLContent();
        content.addItem(item);
        String metadata = "";
        try {
            metadata = new DIDLParser().generate(content);
        } catch (Exception e) {
            e.printStackTrace();
        }
        LogUtils.d("metadata: %s", metadata);
        ControlPoint controlPoint = ClingManager.getInstance().getControlPoint();
        controlPoint.execute(new SetAVTransportURI(instanceId, avtService, uri, metadata) {
            @Override
            public void success(ActionInvocation invocation) {
                LogUtils.i("setAVTransportURI success %s", uri);
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess();
                    }
                });
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, final String msg) {
                LogUtils.e("setAVTransportURI - error %s url:%s", msg + "   URL   " + uri);
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(VError.UNKNOWN, msg);
                    }
                });
            }
        });
    }

    /**
     * 设置远程资源音视频传输 URI
     */
    public void setAVTransportURI(RemoteItem item, final ControlCallback callback) {
        if (checkAVTService()) {
            callback.onError(VError.SERVICE_IS_NULL, "service is null");
            return;
        }
        String metadata = ClingUtil.getItemMetadata(item);
        LogUtils.i("metadata: ", metadata);
        final String uri = item.getUrl();
        ControlPoint controlPoint = ClingManager.getInstance().getControlPoint();
        controlPoint.execute(new SetAVTransportURI(instanceId, avtService, item.getUrl(), metadata) {
            @Override
            public void success(ActionInvocation invocation) {
                LogUtils.i("setAVTransportURI success url:%s", uri);
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onSuccess();
                    }
                });
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, final String msg) {
                LogUtils.e("setAVTransportURI - error %s url:%s", msg + "   URL   " + uri);
                VApplication.getHandler().post(new Runnable() {
                    @Override
                    public void run() {
                        callback.onError(VError.UNKNOWN, msg);
                    }
                });
            }
        });
    }


    private Runnable mRunnable = new Runnable() {
        @Override
        public void run() {
            getPositionInfo();
            getTransportInfo();
            getVolume();
            if (isScreenCast) {
                mHandler.postDelayed(mRunnable, 1000);
            }
        }
    };

    /**
     * 初始化投屏相关回调
     */
    public void initScreenCastCallback() {
        unInitScreenCastCallback();
        isScreenCast = true;
        LogUtils.d("", "initScreenCastCallback");
        // 循环获取投屏接受端状态
        mHandler.removeCallbacksAndMessages(null);
        mHandler.postDelayed(mRunnable, 1000);
        // 设置投屏传输相关回调
        avtCallback = new AVTransportCallback(avtService) {
            @Override
            protected void received(AVTransportInfo info) {
                String state = info.getState();
                if (AVTransportInfo.PLAYING.equals(state)) {
                    setState(CastState.PLAYING);
                } else if (AVTransportInfo.TRANSITIONING.equals(state)) {
                    setState(CastState.TRANSITIONING);
                } else if (AVTransportInfo.PAUSED_PLAYBACK.equals(state)) {
                    setState(CastState.PAUSED);
                } else if (AVTransportInfo.STOPPED.equals(state)) {
                    setState(CastState.STOPPED);
                }

                if (mOnControlStatusChangedListener.get() != null) {
                    mOnControlStatusChangedListener.get()
                            .onProgressChange(VMDate.fromTimeString(info.getTimePosition()),
                                    VMDate.fromTimeString(info.getMediaDuration()));
                }
            }
        };
        ClingManager.getInstance().getControlPoint().execute(avtCallback);

        // 设置播放控制相关回调，这个其实在大部分设备上都无效
        rcCallback = new RenderingControlCallback(rcService) {
            @Override
            protected void received(RenderingControlInfo info) {
                LogUtils.d("RenderingControlCallback received: mute:%b, volume:%d", info.isMute() + "   volume  " + info
                        .getVolume());
                setMute(info.isMute());
                if (mOnControlStatusChangedListener.get() != null) {
                    mOnControlStatusChangedListener.get().onVolumeChanged(info.getVolume(), info.isMute());
                }
            }
        };
        ClingManager.getInstance().getControlPoint().execute(rcCallback);
    }

    /**
     * 取消初始化投屏相关回调
     */
    public void unInitScreenCastCallback() {
        LogUtils.d("", "unInitScreenCastCallback");
        absTimeStr = "00:00:00";
        absTime = 0;
        trackDurationStr = "00:00:00";
        trackDuration = 0;

        isScreenCast = false;
        avtCallback = null;
        rcCallback = null;
    }

    /**
     * 获取投屏设备端播放进度信息
     */
    public void getPositionInfo() {
        if (checkAVTService()) {
            return;
        }
        ControlPoint controlPoint = ClingManager.getInstance().getControlPoint();
        controlPoint.execute(new GetPositionInfo(instanceId, avtService) {
            @Override
            public void received(ActionInvocation invocation, PositionInfo positionInfo) {
                if (positionInfo != null) {
                    absTimeStr = positionInfo.getAbsTime();
                    absTime = VMDate.fromTimeString(absTimeStr);
                    trackDurationStr = positionInfo.getTrackDuration();
                    trackDuration = VMDate.fromTimeString(trackDurationStr);

                    if (mOnControlStatusChangedListener.get() != null) {
                        VApplication.getHandler().post(new Runnable() {
                            @Override
                            public void run() {
                                mOnControlStatusChangedListener.get().onProgressChange(absTime, trackDuration);
                            }
                        });
                    }
                    //判断是否播放完成
                    if (absTimeStr.equals(trackDurationStr) && absTime != 0 && trackDuration != 0) {
                        unInitScreenCastCallback();
                    }
                }
                LogUtils.d("getPositionInfo success positionInfo:", positionInfo.toString());
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String msg) {
                LogUtils.e("E", "getPositionInfo failed");
            }
        });
    }

    /**
     * 获取投屏设备播放端数据传输状态信息
     */
    public void getTransportInfo() {
        if (checkAVTService()) {
            return;
        }
        ControlPoint controlPoint = ClingManager.getInstance().getControlPoint();
        controlPoint.execute(new GetTransportInfo(instanceId, avtService) {

            @Override
            public void received(ActionInvocation invocation, TransportInfo transportInfo) {
                TransportState ts = transportInfo.getCurrentTransportState();
                if (TransportState.TRANSITIONING == ts) {
                    setState(CastState.TRANSITIONING);
                } else if (TransportState.PLAYING == ts) {
                    setState(CastState.PLAYING);
                } else if (TransportState.PAUSED_PLAYBACK == ts) {
                    setState(CastState.PAUSED);
                } else if (TransportState.STOPPED == ts) {
                    setState(CastState.STOPPED);
                    if (absTime != 0 && trackDuration != 0) {
                        unInitScreenCastCallback();
                    }
                } else {
                    setState(CastState.STOPPED);
                    if (absTime != 0 && trackDuration != 0) {
                        unInitScreenCastCallback();
                    }
                }
                LogUtils.d("getTransportInfo success transportInfo:", ts.getValue());
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String msg) {
                LogUtils.e("E", "getTransportInfo failed");
            }
        });
    }

    /**
     * 获取投屏音量
     */
    public void getVolume() {
        if (checkRCService()) {
            return;
        }
        ControlPoint controlPoint = ClingManager.getInstance().getControlPoint();
        controlPoint.execute(new GetVolume(instanceId, rcService) {
            @Override
            public void received(ActionInvocation actionInvocation, int currentVolume) {
                if (currentVolume == 0) {
                    setMute(true);
                } else {
                    setMute(false);
                }

                if (mOnControlStatusChangedListener.get() != null) {
                    mOnControlStatusChangedListener.get().onVolumeChanged(currentVolume, currentVolume == 0);
                }
                LogUtils.d("getVolume success volume:", currentVolume);
            }

            @Override
            public void failure(ActionInvocation invocation, UpnpResponse operation, String msg) {
                LogUtils.e("getVolume error %s", msg);
            }
        });
    }

    /**
     * 检查视频传输服务是否存在
     */
    private boolean checkAVTService() {
        if (avtService == null) {
            avtService = findServiceFromDevice(AV_TRANSPORT);
        }
        return avtService == null;
    }

    /**
     * 检查视频播放控制服务是否存在
     */
    private boolean checkRCService() {
        if (rcService == null) {
            rcService = findServiceFromDevice(RENDERING_CONTROL);
        }
        return rcService == null;
    }

    /**
     * 通过指定服务类型，搜索当前选择的设备的服务
     *
     * @param type 需要的服务类型
     */
    public Service findServiceFromDevice(String type) {
        UDAServiceType serviceType = new UDAServiceType(type);
        ClingDevice device = DeviceManager.getInstance().getCurrClingDevice();
        if (device == null) {
            return null;
        }
        return device.getDevice().findService(serviceType);
    }

    public CastState getState() {
        return state;
    }

    void setState(final CastState state) {
        this.state = state;
        if (mOnControlStatusChangedListener.get() != null) {
            VApplication.getHandler().post(new Runnable() {
                @Override
                public void run() {
                    mOnControlStatusChangedListener.get().onStatusChanged(state);
                }
            });
        }
    }

    public boolean isMute() {
        return isMute;
    }

    public void setMute(boolean mute) {
        isMute = mute;
    }

    /**
     * 销毁，释放资源
     */
    public void destroy() {
        instance = null;
        avtService = null;
        rcService = null;
        mHandlerThread.quitSafely();
        mHandlerThread = null;
        mHandler.removeCallbacksAndMessages(null);
        mHandler = null;
    }

    public void setOnControlStatusChangedListener(OnControlStatusChangedListener listener) {
        mOnControlStatusChangedListener = new WeakReference<>(listener);
    }
}
