package com.savor.ads.activity;

import android.content.Intent;
import android.graphics.drawable.AnimationDrawable;
import android.media.AudioManager;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.jar.savor.box.vo.HitEggResponseVo;
import com.savor.ads.R;

import com.savor.ads.core.Session;
import com.savor.ads.SavorApplication;
import com.savor.ads.bean.PrizeItem;
import com.savor.ads.utils.AppUtils;
import com.savor.ads.utils.ConstantValues;
import com.savor.ads.utils.GlobalValues;
import com.savor.ads.utils.KeyCodeConstant;
import com.savor.ads.utils.LogUtils;

import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Random;

public class LotteryActivity extends BaseActivity {

    public static final String EXTRA_HUNGER = "extra_hunger";

    private static final int MAX_STAY_DURATION = 2 * 60 * 1000;

    private int mBrokenSoundId;
    private SoundPool mSoundPool;

    private int mCurrentFrame;
    private FileWriter mwriter = null;
    private Handler mHandler = new Handler();

    private Runnable mExitLotteryRunnable = new Runnable() {
        @Override
        public void run() {
            LogUtils.e("mExitProjectionRunnable " + LotteryActivity.this.hashCode());
            resetGlobalFlag();
            exitLottery();
        }
    };

    private Runnable mHitEggEffectRunnable = new Runnable() {
        @Override
        public void run() {
            playBrokenSound();
            doHitAnimation();
        }
    };

    private Runnable mBrokenEggEffectRunnable = new Runnable() {
        @Override
        public void run() {
            playBrokenSound();
            doBrokenAnimation();
        }
    };

    private int mLastFrameCount = -1;
    private int mHunger;

    private RelativeLayout mRootLayout;
    private ImageView mEggIv;
    private TextView mProjectTipTv;
    private RelativeLayout mWinDialogRl;
    private ImageView mPrizeNameIv;
    private TextView mPrizeTimeTv;
    private TextView mEndTimeTv;
    private ImageView mLoseDialogIv;

    private int[] EGG_FRAMES = new int[]{
            R.mipmap.egg1,
            R.mipmap.egg2,
            R.mipmap.egg3,
            R.mipmap.egg4,
            R.mipmap.egg5,
            R.mipmap.egg6,
    };
    private PrizeItem mPrizeHit;
    private Date mPrizeTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lottery);

        mHunger = getIntent().getIntExtra(EXTRA_HUNGER, 0);
        findViews();
        loadSound();
        setViews();

        rescheduleToExit(true);
    }

    private void findViews() {
        mRootLayout = (RelativeLayout) findViewById(R.id.rl_root);
        mEggIv = (ImageView) findViewById(R.id.iv_egg);
        mProjectTipTv = (TextView) findViewById(R.id.tv_project_tip);
        mPrizeNameIv = (ImageView) findViewById(R.id.iv_prize_name);
        mWinDialogRl = (RelativeLayout) findViewById(R.id.rl_win_dialog);
        mPrizeTimeTv = (TextView) findViewById(R.id.tv_prize_time);
        mEndTimeTv = (TextView) findViewById(R.id.tv_prize_end_time);
        mLoseDialogIv = (ImageView) findViewById(R.id.iv_lose_dialog);
    }

    private void loadSound() {
        mSoundPool = new SoundPool(10, AudioManager.STREAM_SYSTEM, 0);
        mBrokenSoundId = mSoundPool.load(mContext, R.raw.broken, 1);
    }

    private void setViews() {
        if (!TextUtils.isEmpty(GlobalValues.CURRENT_PROJECT_DEVICE_NAME)) {
            mProjectTipTv.setText(GlobalValues.CURRENT_PROJECT_DEVICE_NAME + "正在砸蛋");
            mProjectTipTv.setVisibility(View.VISIBLE);
        } else {
            mProjectTipTv.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        rescheduleToExit(true);

        mHunger = intent.getIntExtra(EXTRA_HUNGER, 0);
        mPrizeHit = null;
        mPrizeTime = null;
        mCurrentFrame = 0;
        mLastFrameCount = -1;
        mRootLayout.setBackgroundResource(R.mipmap.bg_egg);
        mEggIv.setImageResource(R.mipmap.egg1);
        mEggIv.setVisibility(View.VISIBLE);
        mWinDialogRl.setVisibility(View.GONE);
        mLoseDialogIv.setVisibility(View.GONE);
    }

    private void playBrokenSound() {
        if (mSoundPool != null && mBrokenSoundId > 0) {
            mSoundPool.play(mBrokenSoundId, 1, 1, 0, 0, 1);
        }
    }

    private void doHitAnimation() {
        if (mCurrentFrame < EGG_FRAMES.length) {
            mEggIv.setImageResource(EGG_FRAMES[mCurrentFrame]);
        }
    }

    private void doBrokenAnimation() {
        mEggIv.setVisibility(View.GONE);
        if (mPrizeHit != null) {
            mRootLayout.setBackgroundResource(R.drawable.anim_egg_bg_win);
            AnimationDrawable animationDrawable = (AnimationDrawable) mRootLayout.getBackground();
            animationDrawable.start();
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mWinDialogRl.setVisibility(View.VISIBLE);
                    switch (mPrizeHit.getPrize_level()) {
                        case 1:
                            mPrizeNameIv.setImageResource(R.mipmap.ic_prize1);
                            break;
                        case 2:
                            mPrizeNameIv.setImageResource(R.mipmap.ic_prize2);
                            break;
                        case 3:
                            mPrizeNameIv.setImageResource(R.mipmap.ic_prize3);
                            break;
                        default:
                            break;
                    }
                    SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy年MM月dd日 HH:mm");
                    mPrizeTimeTv.setText("中奖时间：  " + simpleDateFormat.format(mPrizeTime));
                    int hour = (mPrizeTime.getHours() + 1) % 24;
                    mEndTimeTv.setText("有效领奖时间至" + hour + ":" + mPrizeTime.getMinutes());

                    mRootLayout.setBackgroundResource(R.mipmap.bg_egg_win_prize);
                }
            }, 400);
        } else {
            mRootLayout.setBackgroundResource(R.drawable.anim_egg_bg_lose);
            AnimationDrawable animationDrawable = (AnimationDrawable) mRootLayout.getBackground();
            animationDrawable.start();
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mLoseDialogIv.setVisibility(View.VISIBLE);
                    mRootLayout.setBackgroundResource(R.mipmap.bg_egg_broken_lose);
                }
            }, 400);
        }
    }

    private void randomFrameCount() {
        Random random = new Random();
        int temp = random.nextInt(3);
        if ((mLastFrameCount == 0 && temp == 0) || (mLastFrameCount == 2 && temp == 2)) {
            mLastFrameCount = 1;
        } else {
            mLastFrameCount = temp;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (mSoundPool != null) {
            mSoundPool.release();
        }
        mHandler.removeCallbacksAndMessages(null);
    }

    public HitEggResponseVo hitEgg() {
        HitEggResponseVo responseVo = new HitEggResponseVo();
        responseVo.setResult(ConstantValues.SERVER_RESPONSE_CODE_SUCCESS);
        responseVo.setInfo("成功");

        if (mCurrentFrame <= EGG_FRAMES.length) {
            rescheduleToExit(true);
            randomFrameCount();

            mCurrentFrame += mLastFrameCount;
            if (mCurrentFrame > EGG_FRAMES.length) {
//                mCurrentFrame = MAX_FRAME;

                mPrizeTime = new Date();
                checkIfWin();
                mHandler.post(mBrokenEggEffectRunnable);
                writeLotteryRecord(0);
            } else {
                mHandler.post(mHitEggEffectRunnable);
            }
        }

        responseVo.setProgress(mCurrentFrame);
        if (mCurrentFrame <= EGG_FRAMES.length) {
            responseVo.setDone(0);
        } else {
            responseVo.setDone(1);
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd HH;mm:ss");
            responseVo.setPrize_time(simpleDateFormat.format(mPrizeTime));
            if (mPrizeHit != null) {
                responseVo.setWin(1);
                responseVo.setPrize_id(mPrizeHit.getPrize_id());
                responseVo.setPrize_name(mPrizeHit.getPrize_name());
                responseVo.setPrize_level(mPrizeHit.getPrize_level());

                // 剩余奖品数减一
                mPrizeHit.setPrize_num(mPrizeHit.getPrize_num() - 1);
                mSession.setPrizeInfo(mSession.getPrizeInfo());
            } else {
                responseVo.setWin(0);
                responseVo.setPrize_id(0);
                responseVo.setPrize_name("");
            }
        }
        return responseVo;
    }

    private void checkIfWin() {
        if (mHunger == 1) {
            if (mSession.getPrizeInfo() != null) {
                Random random = new Random();
                int denominator = 0;
                ArrayList<PrizeItem> prizeList = new ArrayList<>();
                ArrayList<Integer> mStartPosList = new ArrayList<>();
                List<PrizeItem> prize = mSession.getPrizeInfo().getPrize();
                for (int i = 0; i < prize.size(); i++) {
                    PrizeItem item = prize.get(i);
                    if (item.getPrize_num() > 0) {
                        prizeList.add(item);
                        denominator += item.getPrize_pos();

                        int startPos = item.getPrize_pos();
                        if (mStartPosList.size() > 0) {
                            startPos += mStartPosList.get(mStartPosList.size() - 1);
                        }
                        mStartPosList.add(startPos);
                    }
                }
                mStartPosList.remove(mStartPosList.size() - 1);
                mStartPosList.add(0, 0);

                if (denominator > 0) {
                    int hit = random.nextInt(denominator);
                    for (int i = prizeList.size() - 1; i > 0; i--) {
                        PrizeItem item = prizeList.get(i);
                        if (hit >= mStartPosList.get(i)) {
                            mPrizeHit = item;
                            break;
                        }
                    }
                    if (mPrizeHit != null) {
                        // 中奖了
                        LogUtils.d("中奖了");
                    } else {
                        LogUtils.e("概率计算出错，没找到命中的奖项！！！");
                    }
                } else {
                    // 未中奖，因为剩余奖项数量为0
                    LogUtils.d("未中奖，因为剩余奖项数量为0");
                }
            } else {
                // 未中奖，因为没有奖项配置信息
                LogUtils.d("未中奖，因为没有奖项配置信息");
            }
        } else {
            // 未中奖，因为hunger=0
            LogUtils.d("未中奖，因为hunger=0");
        }
    }

    public void exitImmediately() {
        mHandler.post(mExitLotteryRunnable);
    }

    /**
     * 重置定期退出页面计划
     *
     * @param scheduleNewOne 是否重置
     */
    private void rescheduleToExit(boolean scheduleNewOne) {
        LogUtils.e("rescheduleToExit scheduleNewOne=" + scheduleNewOne + " " + this.hashCode());
        mHandler.removeCallbacks(mExitLotteryRunnable);
        if (scheduleNewOne) {
            mHandler.postDelayed(mExitLotteryRunnable, MAX_STAY_DURATION);
        }
    }

    /**
     * 停止投屏
     *
     * @return
     */
    public void stop() {
        LogUtils.e("StopResponseVo will exitProjection " + this.hashCode());
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                exitLottery();
            }
        });

        resetGlobalFlag();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean handled = false;
        switch (keyCode) {
            case KeyCodeConstant.KEY_CODE_BACK:
                resetGlobalFlag();
                exitLottery();
                handled = true;
                break;
            // 呼出二维码
            case KeyCodeConstant.KEY_CODE_SHOW_QRCODE:
                ((SavorApplication) getApplication()).showQrCodeWindow(null);
                handled = true;
                break;
        }
        return handled || super.onKeyDown(keyCode, event);
    }

    /**
     * 重置全局变量
     */
    private void resetGlobalFlag() {
        GlobalValues.LAST_PROJECT_DEVICE_ID = GlobalValues.CURRENT_PROJECT_DEVICE_ID;
        GlobalValues.LAST_PROJECT_ID = GlobalValues.CURRENT_PROJECT_ID;
        GlobalValues.CURRENT_PROJECT_DEVICE_ID = null;
        GlobalValues.CURRENT_PROJECT_DEVICE_NAME = null;
        GlobalValues.IS_LOTTERY = false;
        GlobalValues.CURRENT_PROJECT_IMAGE_ID = null;
        GlobalValues.CURRENT_PROJECT_ID = null;
    }

    private void exitLottery() {
        LogUtils.e("will exitLottery " + this.hashCode());
        finish();
    }

    private void writeLotteryRecord(int prize){
        if (mwriter==null){
            createLotteryRecordFile();
            if (mwriter!=null){
                try {
                    mwriter.write("");
                    closeWriter();
                } catch (IOException e) {
                    e.printStackTrace();
                }

            }

        }
    }

    private void createLotteryRecordFile(){
        String time = AppUtils.getTime("date");
        String recordFileName = time + ".blog";
        String path = AppUtils.getFilePath(mContext, AppUtils.StorageFile.lottery);
        try {
            mwriter = new FileWriter(path+recordFileName,true);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void closeWriter() {
        if (mwriter != null) {
            try {
                mwriter.close();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            mwriter = null;
        }
    }
}