package com.zeki.videoplayer;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.AudioManager;
import android.os.Handler;
import android.support.v7.app.ActionBar;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.lang.reflect.Constructor;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import tv.danmaku.ijk.media.player.IMediaPlayer;


/**
 */
public abstract class VideoPlayer extends FrameLayout implements MediaPlayerListener, View.OnClickListener, SeekBar.OnSeekBarChangeListener, View.OnTouchListener, TextureView.SurfaceTextureListener {

    public static final String TAG = "VideoPlayer";

    public static final int FULLSCREEN_ID = 33797;
    public static final int TINY_ID       = 33798;
    public static final int THRESHOLD     = 80;
//    public static final int FULL_SCREEN_NORMAL_DELAY = 500;

    public static boolean ACTION_BAR_EXIST       = true;
    public static boolean TOOL_BAR_EXIST         = true;
    public static boolean WIFI_TIP_DIALOG_SHOWED = false;
//    public static long    CLICK_QUIT_FULLSCREEN_TIME = 0;

    public static final int SCREEN_LAYOUT_NORMAL     = 0;
    public static final int SCREEN_LAYOUT_LIST       = 1;
    public static final int SCREEN_WINDOW_FULLSCREEN = 2;
    public static final int SCREEN_WINDOW_TINY       = 3;

    public static final int CURRENT_STATE_NORMAL                  = 0;
    public static final int CURRENT_STATE_PREPARING               = 1;
    public static final int CURRENT_STATE_PLAYING                 = 2;
    public static final int CURRENT_STATE_PLAYING_BUFFERING_START = 3;
    public static final int CURRENT_STATE_PAUSE                   = 5;
    public static final int CURRENT_STATE_AUTO_COMPLETE           = 6;
    public static final int CURRENT_STATE_ERROR                   = 7;

    public int currentState  = -1;
    public int currentScreen = -1;


    public String              url             = "";
    public Object[]            objects         = null;
    public boolean             looping         = false;
    public Map<String, String> mapHeadData     = new HashMap<>();
    public int                 seekToInAdvance = -1;

    public ImageView startButton;
    public SeekBar   progressBar;
    public ImageView fullscreenButton;
    public TextView  currentTimeTextView, totalTimeTextView;
    public ViewGroup textureViewContainer;
    public ViewGroup topContainer, bottomContainer;
    public Surface surface;

    protected static WeakReference<BuriedPoint> JC_BURIED_POINT;
    protected static Timer                        UPDATE_PROGRESS_TIMER;

    protected int               mScreenWidth;
    protected int               mScreenHeight;
    protected AudioManager      mAudioManager;
    protected Handler           mHandler;
    protected ProgressTimerTask mProgressTimerTask;

    protected boolean mTouchingProgressBar;
    protected float   mDownX;
    protected float   mDownY;
    protected boolean mChangeVolume;
    protected boolean mChangePosition;
    protected int     mDownPosition;
    protected int     mGestureDownVolume;
    protected int     mSeekTimePosition;

    public VideoPlayer(Context context) {
        super(context);
        init(context);
    }

    public VideoPlayer(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public void init(Context context) {
        View.inflate(context, getLayoutId(), this);
        startButton = (ImageView) findViewById(R.id.start);
        fullscreenButton = (ImageView) findViewById(R.id.fullscreen);
        progressBar = (SeekBar) findViewById(R.id.progress);
        currentTimeTextView = (TextView) findViewById(R.id.current);
        totalTimeTextView = (TextView) findViewById(R.id.total);
        bottomContainer = (ViewGroup) findViewById(R.id.layout_bottom);
        textureViewContainer = (ViewGroup) findViewById(R.id.surface_container);
        topContainer = (ViewGroup) findViewById(R.id.layout_top);

        startButton.setOnClickListener(this);
        fullscreenButton.setOnClickListener(this);
        progressBar.setOnSeekBarChangeListener(this);
        bottomContainer.setOnClickListener(this);
        textureViewContainer.setOnClickListener(this);

        textureViewContainer.setOnTouchListener(this);
        mScreenWidth = getContext().getResources().getDisplayMetrics().widthPixels;
        mScreenHeight = getContext().getResources().getDisplayMetrics().heightPixels;
        mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        mHandler = new Handler();
    }

    public boolean setUp(String url, int screen, Object... objects) {
        if (!TextUtils.isEmpty(this.url) && TextUtils.equals(this.url, url)) {
            return false;
        }
        VideoPlayerManager.checkAndPutListener(this);
        if (VideoPlayerManager.CURRENT_SCROLL_LISTENER != null && VideoPlayerManager.CURRENT_SCROLL_LISTENER.get() != null) {
            if (this == VideoPlayerManager.CURRENT_SCROLL_LISTENER.get()) {
                if (((VideoPlayer) VideoPlayerManager.CURRENT_SCROLL_LISTENER.get()).currentState == CURRENT_STATE_PLAYING) {
                    if (url.equals(MediaManager.instance().mediaPlayer.getDataSource())) {
                        ((VideoPlayer) VideoPlayerManager.CURRENT_SCROLL_LISTENER.get()).startWindowTiny();//如果列表中,滑动过快,在还没来得及onScroll的时候自己已经被复用了
                    }
                }
            }
        }
        this.url = url;
        this.objects = objects;
        this.currentScreen = screen;
        setUiWitStateAndScreen(CURRENT_STATE_NORMAL);
        if (url.equals(MediaManager.instance().mediaPlayer.getDataSource())) {//如果初始化了一个正在tinyWindow的前身,就应该监听它的滑动,如果显示就在这个listener中播放
            VideoPlayerManager.putScrollListener(this);
        }
        return true;
    }

    @Override
    public int getScreenType() {
        return currentScreen;
    }

    @Override
    public String getUrl() {
        return url;
    }

    @Override
    public int getState() {
        return currentState;
    }

    public boolean setUp(String url, int screen, Map<String, String> mapHeadData, Object... objects) {
        if (setUp(url, screen, objects)) {
            this.mapHeadData.clear();
            this.mapHeadData.putAll(mapHeadData);
            return true;
        }
        return false;
    }

    @Override
    public void onClick(View v) {
        int i = v.getId();
        if (i == R.id.start) {
            Log.i(TAG, "onClick start [" + this.hashCode() + "] ");
            if (TextUtils.isEmpty(url)) {
                Toast.makeText(getContext(), getResources().getString(R.string.no_url), Toast.LENGTH_SHORT).show();
                return;
            }
            if (currentState == CURRENT_STATE_NORMAL || currentState == CURRENT_STATE_ERROR) {
                if (!url.startsWith("file") && !Utils.isWifiConnected(getContext()) && !WIFI_TIP_DIALOG_SHOWED) {
                    showWifiDialog();
                    return;
                }
                prepareVideo();
                onEvent(currentState != CURRENT_STATE_ERROR ? BuriedPoint.ON_CLICK_START_ICON : BuriedPoint.ON_CLICK_START_ERROR);
            } else if (currentState == CURRENT_STATE_PLAYING) {
                onEvent(BuriedPoint.ON_CLICK_PAUSE);
                Log.d(TAG, "pauseVideo [" + this.hashCode() + "] ");
                MediaManager.instance().mediaPlayer.pause();
                setUiWitStateAndScreen(CURRENT_STATE_PAUSE);
            } else if (currentState == CURRENT_STATE_PAUSE) {
                onEvent(BuriedPoint.ON_CLICK_RESUME);
                MediaManager.instance().mediaPlayer.start();
                setUiWitStateAndScreen(CURRENT_STATE_PLAYING);
            } else if (currentState == CURRENT_STATE_AUTO_COMPLETE) {
                onEvent(BuriedPoint.ON_CLICK_START_AUTO_COMPLETE);
                prepareVideo();
            }
        } else if (i == R.id.fullscreen) {
            Log.i(TAG, "onClick fullscreen [" + this.hashCode() + "] ");
            if (currentState == CURRENT_STATE_AUTO_COMPLETE) return;
            if (currentScreen == SCREEN_WINDOW_FULLSCREEN) {
                //quit fullscreen
                backPress();
            } else {
                Log.d(TAG, "toFullscreenActivity [" + this.hashCode() + "] ");
                onEvent(BuriedPoint.ON_ENTER_FULLSCREEN);
                startWindowFullscreen();
            }
        } else if (i == R.id.surface_container && currentState == CURRENT_STATE_ERROR) {
            Log.i(TAG, "onClick surfaceContainer State=Error [" + this.hashCode() + "] ");
            prepareVideo();
        }
    }

    public void prepareVideo() {
        Log.d(TAG, "prepareVideo [" + this.hashCode() + "] ");
        VideoPlayerManager.completeAll();
        VideoPlayerManager.putListener(this);
        addTextureView();

        AudioManager mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.requestAudioFocus(onAudioFocusChangeListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        Utils.scanForActivity(getContext()).getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        VideoPlayerManager.putScrollListener(this);
        MediaManager.instance().prepare(url, mapHeadData, looping);
        setUiWitStateAndScreen(CURRENT_STATE_PREPARING);
    }

    @Override
    public boolean onTouch(View v, MotionEvent event) {
        float x = event.getX();
        float y = event.getY();
        int id = v.getId();
        if (id == R.id.surface_container) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    Log.i(TAG, "onTouch surfaceContainer actionDown [" + this.hashCode() + "] ");
                    mTouchingProgressBar = true;

                    mDownX = x;
                    mDownY = y;
                    mChangeVolume = false;
                    mChangePosition = false;
                    /////////////////////
                    break;
                case MotionEvent.ACTION_MOVE:
                    Log.i(TAG, "onTouch surfaceContainer actionMove [" + this.hashCode() + "] ");
                    float deltaX = x - mDownX;
                    float deltaY = y - mDownY;
                    float absDeltaX = Math.abs(deltaX);
                    float absDeltaY = Math.abs(deltaY);
                    if (currentScreen == SCREEN_WINDOW_FULLSCREEN) {
                        if (!mChangePosition && !mChangeVolume) {
                            if (absDeltaX > THRESHOLD || absDeltaY > THRESHOLD) {
                                cancelProgressTimer();
                                if (absDeltaX >= THRESHOLD) {
                                    // 全屏模式下的CURRENT_STATE_ERROR状态下,不响应进度拖动事件.
                                    // 否则会因为mediaplayer的状态非法导致App Crash
                                    if (currentState != CURRENT_STATE_ERROR) {
                                        mChangePosition = true;
                                        mDownPosition = getCurrentPositionWhenPlaying();
                                    }
                                } else {
                                    mChangeVolume = true;
                                    mGestureDownVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC);
                                }
                            }
                        }
                    }
                    if (mChangePosition) {
                        int totalTimeDuration = getDuration();
                        mSeekTimePosition = (int) (mDownPosition + deltaX * totalTimeDuration / mScreenWidth);
                        if (mSeekTimePosition > totalTimeDuration)
                            mSeekTimePosition = totalTimeDuration;
                        String seekTime = Utils.stringForTime(mSeekTimePosition);
                        String totalTime = Utils.stringForTime(totalTimeDuration);

                        showProgressDialog(deltaX, seekTime, mSeekTimePosition, totalTime, totalTimeDuration);
                    }
                    if (mChangeVolume) {
                        deltaY = -deltaY;
                        int max = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
                        int deltaV = (int) (max * deltaY * 3 / mScreenHeight);
                        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, mGestureDownVolume + deltaV, 0);
                        int volumePercent = (int) (mGestureDownVolume * 100 / max + deltaY * 3 * 100 / mScreenHeight);

                        showVolumeDialog(-deltaY, volumePercent);
                    }

                    break;
                case MotionEvent.ACTION_UP:
                    Log.i(TAG, "onTouch surfaceContainer actionUp [" + this.hashCode() + "] ");
                    mTouchingProgressBar = false;
                    dismissProgressDialog();
                    dismissVolumeDialog();
                    if (mChangePosition) {
                        onEvent(BuriedPoint.ON_TOUCH_SCREEN_SEEK_POSITION);
                        MediaManager.instance().mediaPlayer.seekTo(mSeekTimePosition);
                        int duration = getDuration();
                        int progress = mSeekTimePosition * 100 / (duration == 0 ? 1 : duration);
                        progressBar.setProgress(progress);
                    }
                    if (mChangeVolume) {
                        onEvent(BuriedPoint.ON_TOUCH_SCREEN_SEEK_VOLUME);
                    }
                    startProgressTimer();
                    break;
            }
        }
        return false;
    }

    public void addTextureView() {
        Log.d(TAG, "addTextureView [" + this.hashCode() + "] ");
        if (textureViewContainer.getChildCount() > 0) {
            textureViewContainer.removeAllViews();
        }
        MediaManager.textureView = null;
        MediaManager.textureView = new ResizeTextureView(getContext());
        MediaManager.textureView.setVideoSize(MediaManager.instance().getVideoSize());
        MediaManager.textureView.setRotation(MediaManager.instance().videoRotation);
        MediaManager.textureView.setSurfaceTextureListener(this);

        LayoutParams layoutParams =
                new LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        Gravity.CENTER);
        textureViewContainer.addView(MediaManager.textureView, layoutParams);
    }

    public void setUiWitStateAndScreen(int state) {
        currentState = state;
        switch (currentState) {
            case CURRENT_STATE_NORMAL:
                if (isCurrentMediaListener()) {
                    cancelProgressTimer();
                    MediaManager.instance().releaseMediaPlayer();
                }
                break;
            case CURRENT_STATE_PREPARING:
                resetProgressAndTime();
                break;
            case CURRENT_STATE_PLAYING:
            case CURRENT_STATE_PAUSE:
            case CURRENT_STATE_PLAYING_BUFFERING_START:
                startProgressTimer();
                break;
            case CURRENT_STATE_ERROR:
                cancelProgressTimer();
                if (isCurrentMediaListener()) {
                    MediaManager.instance().releaseMediaPlayer();
                }
                break;
            case CURRENT_STATE_AUTO_COMPLETE:
                cancelProgressTimer();
                progressBar.setProgress(100);
                currentTimeTextView.setText(totalTimeTextView.getText());
                break;
        }
    }

    public void startProgressTimer() {
        cancelProgressTimer();
        UPDATE_PROGRESS_TIMER = new Timer();
        mProgressTimerTask = new ProgressTimerTask();
        UPDATE_PROGRESS_TIMER.schedule(mProgressTimerTask, 0, 300);
    }

    public void cancelProgressTimer() {
        if (UPDATE_PROGRESS_TIMER != null) {
            UPDATE_PROGRESS_TIMER.cancel();
        }
        if (mProgressTimerTask != null) {
            mProgressTimerTask.cancel();
        }
    }

    @Override
    public void onPrepared() {
        Log.i(TAG, "onPrepared " + " [" + this.hashCode() + "] ");

        if (currentState != CURRENT_STATE_PREPARING) return;
        MediaManager.instance().mediaPlayer.start();
        if (seekToInAdvance != -1) {
            MediaManager.instance().mediaPlayer.seekTo(seekToInAdvance);
            seekToInAdvance = -1;
        }
        startProgressTimer();
        setUiWitStateAndScreen(CURRENT_STATE_PLAYING);
    }

    public void clearFullscreenLayout() {
        ViewGroup vp = (ViewGroup) (Utils.scanForActivity(getContext())).getWindow().getDecorView();
//                .findViewById(Window.ID_ANDROID_CONTENT);
        View oldF = vp.findViewById(FULLSCREEN_ID);
        View oldT = vp.findViewById(TINY_ID);
        if (oldF != null) {
            vp.removeView(oldF);
        }
        if (oldT != null) {
            vp.removeView(oldT);
        }
        showSupportActionBar(getContext());
    }

    @Override
    public void onAutoCompletion() {
        Log.i(TAG, "onAutoCompletion " + " [" + this.hashCode() + "] ");
        onEvent(BuriedPoint.ON_AUTO_COMPLETE);
        VideoPlayerManager.completeAll();
    }

    @Override
    public void onCompletion() {
        Log.i(TAG, "onCompletion " + " [" + this.hashCode() + "] ");
        setUiWitStateAndScreen(CURRENT_STATE_NORMAL);
        if (textureViewContainer.getChildCount() > 0) {
            textureViewContainer.removeAllViews();
        }

        MediaManager.instance().currentVideoWidth = 0;
        MediaManager.instance().currentVideoHeight = 0;

        // 清理缓存变量
        MediaManager.instance().bufferPercent = 0;
        MediaManager.instance().videoRotation = 0;

        AudioManager mAudioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.abandonAudioFocus(onAudioFocusChangeListener);
        Utils.scanForActivity(getContext()).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        clearFullscreenLayout();
    }

    @Override
    public boolean backToOtherListener() {//这里这个名字这么写并不对,这是在回退的时候gotoother,如果直接gotoother就不叫这个名字
        Log.i(TAG, "backToOtherListener " + " [" + this.hashCode() + "] ");

        if (currentScreen == VideoPlayerStandard.SCREEN_WINDOW_FULLSCREEN
                || currentScreen == VideoPlayerStandard.SCREEN_WINDOW_TINY) {
//            if (currentScreen == VideoPlayerStandard.SCREEN_WINDOW_FULLSCREEN) {
//                final Animation ra = AnimationUtils.loadAnimation(getContext(), R.anim.quit_fullscreen);
//                startAnimation(ra);
//            }
            onEvent(currentScreen == VideoPlayerStandard.SCREEN_WINDOW_FULLSCREEN ?
                    BuriedPoint.ON_QUIT_FULLSCREEN :
                    BuriedPoint.ON_QUIT_TINYSCREEN);
            if (VideoPlayerManager.LISTENERLIST.size() == 1) {//directly fullscreen
                VideoPlayerManager.popListener().onCompletion();
                MediaManager.instance().releaseMediaPlayer();
                showSupportActionBar(getContext());
                return true;
            }
            ViewGroup vp = (ViewGroup) (Utils.scanForActivity(getContext())).getWindow().getDecorView();
//                .findViewById(Window.ID_ANDROID_CONTENT);
            vp.removeView(this);
            MediaManager.instance().lastState = currentState;//save state
            VideoPlayerManager.popListener();
            VideoPlayerManager.getFirst().goBackThisListener();
//            CLICK_QUIT_FULLSCREEN_TIME = System.currentTimeMillis();
            return true;
        }
        return false;
    }

    long lastAutoFullscreenTime = 0;

    @Override
    public void autoFullscreenLeft() {
        if ((System.currentTimeMillis() - lastAutoFullscreenTime) > 2000
                && isCurrentMediaListener()
                && currentState == CURRENT_STATE_PLAYING
                && currentScreen != SCREEN_WINDOW_FULLSCREEN
                && currentScreen != SCREEN_WINDOW_TINY) {
            lastAutoFullscreenTime = System.currentTimeMillis();
            startWindowFullscreen();
        }
    }

    @Override
    public void autoFullscreenRight() {

    }

    @Override
    public void autoQuitFullscreen() {
        if ((System.currentTimeMillis() - lastAutoFullscreenTime) > 2000
                && isCurrentMediaListener()
                && currentState == CURRENT_STATE_PLAYING
                && currentScreen == SCREEN_WINDOW_FULLSCREEN) {
            lastAutoFullscreenTime = System.currentTimeMillis();
            backPress();
        }
    }

    @Override
    public void goBackThisListener() {
        Log.i(TAG, "goBackThisListener " + " [" + this.hashCode() + "] ");

        currentState = MediaManager.instance().lastState;
        setUiWitStateAndScreen(currentState);
        addTextureView();

        showSupportActionBar(getContext());
    }

    @Override
    public void onBufferingUpdate(int percent) {
        if (currentState != CURRENT_STATE_NORMAL && currentState != CURRENT_STATE_PREPARING) {
            Log.v(TAG, "onBufferingUpdate " + percent + " [" + this.hashCode() + "] ");
            MediaManager.instance().bufferPercent = percent;
            setTextAndProgress(percent);
        }
    }

    @Override
    public void onSeekComplete() {
    }

    @Override
    public void onError(int what, int extra) {
        Log.e(TAG, "onError " + what + " - " + extra + " [" + this.hashCode() + "] ");
        if (what != 38 && what != -38) {
            setUiWitStateAndScreen(CURRENT_STATE_ERROR);
        }
    }

    @Override
    public void onInfo(int what, int extra) {
        Log.d(TAG, "onInfo what - " + what + " extra - " + extra);
        if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_START) {
            MediaManager.instance().backUpBufferState = currentState;
            setUiWitStateAndScreen(CURRENT_STATE_PLAYING_BUFFERING_START);
            Log.d(TAG, "MEDIA_INFO_BUFFERING_START");
        } else if (what == IMediaPlayer.MEDIA_INFO_BUFFERING_END) {
            if (MediaManager.instance().backUpBufferState != -1) {
                setUiWitStateAndScreen(MediaManager.instance().backUpBufferState);
                MediaManager.instance().backUpBufferState = -1;
            }
            Log.d(TAG, "MEDIA_INFO_BUFFERING_END");
        } else if (what == IMediaPlayer.MEDIA_INFO_VIDEO_ROTATION_CHANGED) {
            MediaManager.instance().videoRotation = extra;
            MediaManager.textureView.setRotation(extra);
            Log.d(TAG, "MEDIA_INFO_VIDEO_ROTATION_CHANGED");
        }
    }

    @Override
    public void onVideoSizeChanged() {
        Log.i(TAG, "onVideoSizeChanged " + " [" + this.hashCode() + "] ");
        MediaManager.textureView.setVideoSize(MediaManager.instance().getVideoSize());
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        Log.i(TAG, "onSurfaceTextureAvailable [" + this.hashCode() + "] ");
        this.surface = new Surface(surface);
        MediaManager.instance().setDisplay(this.surface);
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {

    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        surface.release();
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {

    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        Log.i(TAG, "bottomProgress onStartTrackingTouch [" + this.hashCode() + "] ");
        cancelProgressTimer();
        ViewParent vpdown = getParent();
        while (vpdown != null) {
            vpdown.requestDisallowInterceptTouchEvent(true);
            vpdown = vpdown.getParent();
        }
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        Log.i(TAG, "bottomProgress onStopTrackingTouch [" + this.hashCode() + "] ");
        onEvent(BuriedPoint.ON_SEEK_POSITION);
        startProgressTimer();
        ViewParent vpup = getParent();
        while (vpup != null) {
            vpup.requestDisallowInterceptTouchEvent(false);
            vpup = vpup.getParent();
        }
        if (currentState != CURRENT_STATE_PLAYING &&
                currentState != CURRENT_STATE_PAUSE) return;
        int time = seekBar.getProgress() * getDuration() / 100;
        MediaManager.instance().mediaPlayer.seekTo(time);
        Log.i(TAG, "seekTo " + time + " [" + this.hashCode() + "] ");
    }

    public static boolean backPress() {
        Log.i(TAG, "backPress");
        if (VideoPlayerManager.getFirst() != null) {
            return VideoPlayerManager.getFirst().backToOtherListener();
        }
        return false;
    }

    public void startWindowFullscreen() {
        Log.i(TAG, "startWindowFullscreen " + " [" + this.hashCode() + "] ");
        hideSupportActionBar(getContext());
        ViewGroup vp = (ViewGroup) (Utils.scanForActivity(getContext())).getWindow().getDecorView();
//                .findViewById(Window.ID_ANDROID_CONTENT);
        View old = vp.findViewById(FULLSCREEN_ID);
        if (old != null) {
            vp.removeView(old);
        }
        if (textureViewContainer.getChildCount() > 0) {
            textureViewContainer.removeAllViews();
        }
        try {
            Constructor<VideoPlayer> constructor = (Constructor<VideoPlayer>) VideoPlayer.this.getClass().getConstructor(Context.class);
            VideoPlayer jcVideoPlayer = constructor.newInstance(getContext());
            jcVideoPlayer.setId(FULLSCREEN_ID);
            WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
            int w = wm.getDefaultDisplay().getWidth();
            int h = wm.getDefaultDisplay().getHeight();
            LayoutParams lp = new LayoutParams(h, w);
            lp.setMargins((w - h) / 2, -(w - h) / 2, 0, 0);
            vp.addView(jcVideoPlayer, lp);
            jcVideoPlayer.setUp(url, VideoPlayerStandard.SCREEN_WINDOW_FULLSCREEN, objects);
            jcVideoPlayer.setUiWitStateAndScreen(currentState);
            jcVideoPlayer.addTextureView();
            jcVideoPlayer.setRotation(90);

//            final Animation ra = AnimationUtils.loadAnimation(getContext(), R.anim.start_fullscreen);
//            jcVideoPlayer.setAnimation(ra);

            VideoPlayerManager.putListener(jcVideoPlayer);


        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void startWindowTiny() {
        Log.i(TAG, "startWindowTiny " + " [" + this.hashCode() + "] ");
        onEvent(BuriedPoint.ON_ENTER_TINYSCREEN);

        ViewGroup vp = (ViewGroup) (Utils.scanForActivity(getContext())).getWindow().getDecorView();
//                .findViewById(Window.ID_ANDROID_CONTENT);
        View old = vp.findViewById(TINY_ID);
        if (old != null) {
            vp.removeView(old);
        }
        if (textureViewContainer.getChildCount() > 0) {
            textureViewContainer.removeAllViews();
        }
        try {
            Constructor<VideoPlayer> constructor = (Constructor<VideoPlayer>) VideoPlayer.this.getClass().getConstructor(Context.class);
            VideoPlayer mJcVideoPlayer = constructor.newInstance(getContext());
            mJcVideoPlayer.setId(TINY_ID);
            LayoutParams lp = new LayoutParams(400, 400);
            lp.gravity = Gravity.RIGHT | Gravity.BOTTOM;
            vp.addView(mJcVideoPlayer, lp);
            mJcVideoPlayer.setUp(url, VideoPlayerStandard.SCREEN_WINDOW_TINY, objects);
            mJcVideoPlayer.setUiWitStateAndScreen(currentState);
            mJcVideoPlayer.addTextureView();
            VideoPlayerManager.putListener(mJcVideoPlayer);

        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public class ProgressTimerTask extends TimerTask {
        @Override
        public void run() {
            if (currentState == CURRENT_STATE_PLAYING || currentState == CURRENT_STATE_PAUSE || currentState == CURRENT_STATE_PLAYING_BUFFERING_START) {
                int position = getCurrentPositionWhenPlaying();
                int duration = getDuration();
                Log.v(TAG, "onProgressUpdate " + position + "/" + duration + " [" + this.hashCode() + "] ");
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        setTextAndProgress(MediaManager.instance().bufferPercent);
                    }
                });
            }
        }
    }

    public int getCurrentPositionWhenPlaying() {
        int position = 0;
        if (currentState == CURRENT_STATE_PLAYING || currentState == CURRENT_STATE_PAUSE || currentState == CURRENT_STATE_PLAYING_BUFFERING_START) {
            try {
                position = (int) MediaManager.instance().mediaPlayer.getCurrentPosition();
            } catch (IllegalStateException e) {
                e.printStackTrace();
                return position;
            }
        }
        return position;
    }

    public int getDuration() {
        int duration = 0;
        try {
            duration = (int) MediaManager.instance().mediaPlayer.getDuration();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            return duration;
        }
        return duration;
    }

    public void setTextAndProgress(int secProgress) {
        int position = getCurrentPositionWhenPlaying();
        int duration = getDuration();
        int progress = position * 100 / (duration == 0 ? 1 : duration);
        setProgressAndTime(progress, secProgress, position, duration);
    }

    public void setProgressAndTime(int progress, int secProgress, int currentTime, int totalTime) {
        if (!mTouchingProgressBar) {
            if (progress != 0) progressBar.setProgress(progress);
        }
        if (secProgress > 95) secProgress = 100;
        if (secProgress != 0) progressBar.setSecondaryProgress(secProgress);
        if (currentTime != 0) currentTimeTextView.setText(Utils.stringForTime(currentTime));
        totalTimeTextView.setText(Utils.stringForTime(totalTime));
    }

    public void resetProgressAndTime() {
        progressBar.setProgress(0);
        progressBar.setSecondaryProgress(0);
        currentTimeTextView.setText(Utils.stringForTime(0));
        totalTimeTextView.setText(Utils.stringForTime(0));
    }

    public static AudioManager.OnAudioFocusChangeListener onAudioFocusChangeListener = new AudioManager.OnAudioFocusChangeListener() {
        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    releaseAllVideos();
                    Log.d(TAG, "AUDIOFOCUS_LOSS [" + this.hashCode() + "]");
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                    if (MediaManager.instance().mediaPlayer.isPlaying()) {
                        MediaManager.instance().mediaPlayer.pause();
                    }
                    Log.d(TAG, "AUDIOFOCUS_LOSS_TRANSIENT [" + this.hashCode() + "]");
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                    break;
            }
        }
    };

    public void release() {
//        if (isCurrentMediaListener() &&
//                (System.currentTimeMillis() - CLICK_QUIT_FULLSCREEN_TIME) > FULL_SCREEN_NORMAL_DELAY) {
        Log.d(TAG, "release [" + this.hashCode() + "]");
        releaseAllVideos();
//        }
    }

    public boolean isCurrentMediaListener() {
        return VideoPlayerManager.getFirst() != null
                && VideoPlayerManager.getFirst() == this;
    }

    public static void releaseAllVideos() {
        Log.d(TAG, "releaseAllVideos");
        VideoPlayerManager.completeAll();
        MediaManager.instance().releaseMediaPlayer();
    }

    public static void setJcBuriedPoint(BuriedPoint buriedPoint) {
        JC_BURIED_POINT = new WeakReference<>(buriedPoint);
    }

    public void onEvent(int type) {
        if (JC_BURIED_POINT != null && JC_BURIED_POINT.get() != null && isCurrentMediaListener()) {
            JC_BURIED_POINT.get().onEvent(type, url, currentScreen, objects);
        }
    }

    @Override
    public void onScrollChange() {//这里需要自己判断自己是 进入小窗,退出小窗,暂停还是播放
        if (url.equals(MediaManager.instance().mediaPlayer.getDataSource())) {
            if (VideoPlayerManager.getFirst() == null) return;
            if (VideoPlayerManager.getFirst().getScreenType() == SCREEN_WINDOW_TINY) {
                //如果正在播放的是小窗,择机退出小窗
                if (isShown()) {//已经显示,就退出小窗
                    backPress();
                }
            } else {
                //如果正在播放的不是小窗,择机进入小窗
                if (!isShown()) {//已经隐藏
                    if (currentState != CURRENT_STATE_PLAYING) {
                        releaseAllVideos();
                    } else {
                        startWindowTiny();
                    }
                }
            }
        }
    }

    public static void onScroll() {//这里就应该保证,listener的正确的完整的赋值,调用非播放的控件
        if (VideoPlayerManager.CURRENT_SCROLL_LISTENER != null && VideoPlayerManager.CURRENT_SCROLL_LISTENER.get() != null) {
            MediaPlayerListener mediaPlayerListener = VideoPlayerManager.CURRENT_SCROLL_LISTENER.get();
            if (//jcMediaPlayerListenerWeakReference.get().getState() != CURRENT_STATE_NORMAL &&
                    mediaPlayerListener.getState() != CURRENT_STATE_ERROR &&
                            mediaPlayerListener.getState() != CURRENT_STATE_AUTO_COMPLETE) {
                mediaPlayerListener.onScrollChange();
            }
        }
    }

    public static void startFullscreen(Context context, Class _class, String url, Object... objects) {

        hideSupportActionBar(context);
        ViewGroup vp = (ViewGroup) (Utils.scanForActivity(context)).getWindow().getDecorView();
//                .findViewById(Window.ID_ANDROID_CONTENT);
        View old = vp.findViewById(VideoPlayer.FULLSCREEN_ID);
        if (old != null) {
            vp.removeView(old);
        }
        try {
            Constructor<VideoPlayer> constructor = _class.getConstructor(Context.class);
            VideoPlayer jcVideoPlayer = constructor.newInstance(context);
            jcVideoPlayer.setId(VideoPlayerStandard.FULLSCREEN_ID);
            WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            int w = wm.getDefaultDisplay().getWidth();
            int h = wm.getDefaultDisplay().getHeight();
            LayoutParams lp = new LayoutParams(h, w);
            lp.setMargins((w - h) / 2, -(w - h) / 2, 0, 0);
            vp.addView(jcVideoPlayer, lp);

//            final Animation ra = AnimationUtils.loadAnimation(context, R.anim.start_fullscreen);
//            jcVideoPlayer.setAnimation(ra);

            jcVideoPlayer.setUp(url, VideoPlayerStandard.SCREEN_WINDOW_FULLSCREEN, objects);
            jcVideoPlayer.addTextureView();
            jcVideoPlayer.setRotation(90);

            jcVideoPlayer.startButton.performClick();

        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void hideSupportActionBar(Context context) {
        if (ACTION_BAR_EXIST) {
            ActionBar ab = Utils.getAppCompActivity(context).getSupportActionBar();
            if (ab != null) {
                ab.setShowHideAnimationEnabled(false);
                ab.hide();
            }
        }
        if (TOOL_BAR_EXIST) {
            Utils.getAppCompActivity(context).getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                    WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    public static void showSupportActionBar(Context context) {
        if (ACTION_BAR_EXIST) {
            ActionBar ab = Utils.getAppCompActivity(context).getSupportActionBar();
            if (ab != null) {
                ab.setShowHideAnimationEnabled(false);
                ab.show();
            }
        }
        if (TOOL_BAR_EXIST) {
            Utils.getAppCompActivity(context).getWindow().clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        }
    }

    public static class JCAutoFullscreenListener implements SensorEventListener {
        @Override
        public void onSensorChanged(SensorEvent event) {//可以得到传感器实时测量出来的变化值
            float x = event.values[SensorManager.DATA_X];
            float y = event.values[SensorManager.DATA_Y];
            float z = event.values[SensorManager.DATA_Z];
            if (x < -10) {
                //direction right
            } else if (x > 10) {
                //direction left
                if (VideoPlayerManager.getFirst() != null) {
                    VideoPlayerManager.getFirst().autoFullscreenLeft();
                }
            } else if (y > 9.5) {
                if (VideoPlayerManager.getFirst() != null) {
                    VideoPlayerManager.getFirst().autoQuitFullscreen();
                }
            }

        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    }

    public void showWifiDialog() {
    }

    public void showProgressDialog(float deltaX,
                                   String seekTime, int seekTimePosition,
                                   String totalTime, int totalTimeDuration) {
    }

    public void dismissProgressDialog() {

    }

    public void showVolumeDialog(float deltaY, int volumePercent) {

    }

    public void dismissVolumeDialog() {

    }


    public abstract int getLayoutId();


}
