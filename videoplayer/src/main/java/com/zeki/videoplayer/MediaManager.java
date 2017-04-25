package com.zeki.videoplayer;

import android.graphics.Point;
import android.media.AudioManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.text.TextUtils;
import android.util.Log;
import android.view.Surface;

import java.util.Map;

import tv.danmaku.ijk.media.player.IMediaPlayer;
import tv.danmaku.ijk.media.player.IjkMediaPlayer;


/**
 * <p>统一管理MediaPlayer的地方,只有一个mediaPlayer实例，那么不会有多个视频同时播放，也节省资源。</p>
 * <p>Unified management MediaPlayer place, there is only one MediaPlayer instance, then there will be no more video broadcast at the same time, also save resources.</p>
 */
public class MediaManager implements IMediaPlayer.OnPreparedListener, IMediaPlayer.OnCompletionListener,
        IMediaPlayer.OnBufferingUpdateListener, IMediaPlayer.OnSeekCompleteListener, IMediaPlayer.OnErrorListener,
        IMediaPlayer.OnVideoSizeChangedListener, IMediaPlayer.OnInfoListener {
    public static String TAG = "JieCaoVideoPlayer";

    private static MediaManager MediaManager;
    public IjkMediaPlayer mediaPlayer;
    public static ResizeTextureView textureView;

    public int currentVideoWidth  = 0;
    public int currentVideoHeight = 0;
    public int lastState;
    public int bufferPercent;
    public int backUpBufferState = -1;
    public int videoRotation;

    public static final int HANDLER_PREPARE    = 0;
    public static final int HANDLER_SETDISPLAY = 1;
    public static final int HANDLER_RELEASE    = 2;
    HandlerThread mMediaHandlerThread;
    MediaHandler  mMediaHandler;
    Handler       mainThreadHandler;

    public static MediaManager instance() {
        if (MediaManager == null) {
            MediaManager = new MediaManager();
        }
        return MediaManager;
    }

    public MediaManager() {
        mediaPlayer = new IjkMediaPlayer();
        mMediaHandlerThread = new HandlerThread(TAG);
        mMediaHandlerThread.start();
        mMediaHandler = new MediaHandler((mMediaHandlerThread.getLooper()));
        mainThreadHandler = new Handler();
    }

    public Point getVideoSize(){
        if (currentVideoWidth != 0 && currentVideoHeight != 0) {
            return new Point(currentVideoWidth, currentVideoHeight);
        } else {
            return null;
        }
    }

    public class MediaHandler extends Handler {
        public MediaHandler(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message msg) {
            super.handleMessage(msg);
            switch (msg.what) {
                case HANDLER_PREPARE:
                    try {
                        currentVideoWidth = 0;
                        currentVideoHeight = 0;
                        mediaPlayer.release();
                        mediaPlayer = new IjkMediaPlayer();
                        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                        mediaPlayer.setDataSource(((FuckBean) msg.obj).url, ((FuckBean) msg.obj).mapHeadData);
                        mediaPlayer.setLooping(((FuckBean) msg.obj).looping);
                        mediaPlayer.setOnPreparedListener(MediaManager.this);
                        mediaPlayer.setOnCompletionListener(MediaManager.this);
                        mediaPlayer.setOnBufferingUpdateListener(MediaManager.this);
                        mediaPlayer.setScreenOnWhilePlaying(true);
                        mediaPlayer.setOnSeekCompleteListener(MediaManager.this);
                        mediaPlayer.setOnErrorListener(MediaManager.this);
                        mediaPlayer.setOnInfoListener(MediaManager.this);
                        mediaPlayer.setOnVideoSizeChangedListener(MediaManager.this);
                        mediaPlayer.prepareAsync();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    break;
                case HANDLER_SETDISPLAY:
                    if (msg.obj == null) {
                        instance().mediaPlayer.setSurface(null);
                    } else {
                        Surface holder = (Surface) msg.obj;
                        if (holder.isValid()) {
                            Log.i(TAG, "set surface");
                            instance().mediaPlayer.setSurface(holder);
                            mainThreadHandler.post(new Runnable() {
                                @Override
                                public void run() {
                                    textureView.requestLayout();
                                }
                            });
                        }
                    }
                    break;
                case HANDLER_RELEASE:
                    mediaPlayer.reset();
                    mediaPlayer.release();
                    break;
            }
        }
    }


    public void prepare(final String url, final Map<String, String> mapHeadData, boolean loop) {
        if (TextUtils.isEmpty(url)) return;
        Message msg = new Message();
        msg.what = HANDLER_PREPARE;
        FuckBean fb = new FuckBean(url, mapHeadData, loop);
        msg.obj = fb;
        mMediaHandler.sendMessage(msg);
    }

    public void releaseMediaPlayer() {
        Message msg = new Message();
        msg.what = HANDLER_RELEASE;
        mMediaHandler.sendMessage(msg);
    }

    public void setDisplay(Surface holder) {
        Message msg = new Message();
        msg.what = HANDLER_SETDISPLAY;
        msg.obj = holder;
        mMediaHandler.sendMessage(msg);
    }

    @Override
    public void onPrepared(IMediaPlayer mp) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                if (VideoPlayerManager.getFirst() != null) {
                    VideoPlayerManager.getFirst().onPrepared();
                }
            }
        });
    }

    @Override
    public void onCompletion(IMediaPlayer mp) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                if (VideoPlayerManager.getFirst() != null) {
                    VideoPlayerManager.getFirst().onAutoCompletion();
                }
            }
        });
    }

    @Override
    public void onBufferingUpdate(IMediaPlayer mp, final int percent) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                if (VideoPlayerManager.getFirst() != null) {
                    VideoPlayerManager.getFirst().onBufferingUpdate(percent);
                }
            }
        });
    }

    @Override
    public void onSeekComplete(IMediaPlayer mp) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                if (VideoPlayerManager.getFirst() != null) {
                    VideoPlayerManager.getFirst().onSeekComplete();
                }
            }
        });
    }

    @Override
    public boolean onError(IMediaPlayer mp, final int what, final int extra) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                if (VideoPlayerManager.getFirst() != null) {
                    VideoPlayerManager.getFirst().onError(what, extra);
                }
            }
        });
        return true;
    }

    @Override
    public boolean onInfo(IMediaPlayer mp, final int what, final int extra) {
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                if (VideoPlayerManager.getFirst() != null) {
                    VideoPlayerManager.getFirst().onInfo(what, extra);
                }
            }
        });
        return false;
    }

    @Override
    public void onVideoSizeChanged(IMediaPlayer mp, int width, int height, int sar_num, int sar_den) {
        currentVideoWidth = mp.getVideoWidth();
        currentVideoHeight = mp.getVideoHeight();
        mainThreadHandler.post(new Runnable() {
            @Override
            public void run() {
                if (VideoPlayerManager.getFirst() != null) {
                    VideoPlayerManager.getFirst().onVideoSizeChanged();
                }
            }
        });
    }


    private class FuckBean {
        String              url;
        Map<String, String> mapHeadData;
        boolean             looping;

        FuckBean(String url, Map<String, String> mapHeadData, boolean loop) {
            this.url = url;
            this.mapHeadData = mapHeadData;
            this.looping = loop;
        }
    }
}
