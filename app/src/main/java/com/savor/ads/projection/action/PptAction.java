package com.savor.ads.projection.action;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.jar.savor.box.vo.PptRequestVo;
import com.savor.ads.activity.ScreenProjectionActivity;
import com.savor.ads.projection.ProjectPriority;
import com.savor.ads.utils.ActivitiesManager;
import com.savor.ads.utils.ConstantValues;
import com.savor.ads.utils.LogUtils;

import java.io.Serializable;

/**
 * Created by zhang.haiqiang on 2017/6/12.
 */

public class PptAction extends ProjectionActionBase implements Serializable {

    private transient Context mContext;
    private PptRequestVo pptRequestVo;
    private boolean isNewDevice;

    public PptAction(Context mContext, PptRequestVo pptRequestVo, boolean isNewDevice) {
        super();

        mPriority = ProjectPriority.HIGH;
        this.mContext = mContext;
        this.pptRequestVo = pptRequestVo;
        this.isNewDevice = isNewDevice;
    }

    @Override
    public void execute() {
        onActionBegin();

        // 跳转或将参数设置到ScreenProjectionActivity
        Bundle data = new Bundle();
        data.putString(ScreenProjectionActivity.EXTRA_TYPE, ConstantValues.PROJECT_TYPE_RSTR_PPT);
        data.putSerializable(ScreenProjectionActivity.EXTRA_PPT_CONFIG, pptRequestVo);
        data.putBoolean(ScreenProjectionActivity.EXTRA_IS_NEW_DEVICE, isNewDevice);
        data.putSerializable(ScreenProjectionActivity.EXTRA_PROJECT_ACTION, this);
        Activity activity = ActivitiesManager.getInstance().getCurrentActivity();
        if (activity instanceof ScreenProjectionActivity && !((ScreenProjectionActivity) activity).isBeenStopped()) {
            LogUtils.d("Listener will setNewProjection");
            ((ScreenProjectionActivity) activity).setNewProjection(data);
        } else {
            if (ActivitiesManager.getInstance().getCurrentActivity() == null) {
                LogUtils.d("Listener will startActivity in new task");
                Intent intent = new Intent(mContext, ScreenProjectionActivity.class);
                intent.putExtras(data);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                mContext.startActivity(intent);
            } else {
                LogUtils.d("Listener will startActivity in " + activity);
                Intent intent = new Intent(activity, ScreenProjectionActivity.class);
                intent.putExtras(data);
                activity.startActivity(intent);
            }
        }
    }

    @Override
    public String toString() {
        return "PptAction{" +
                "mContext=" + mContext +
                ", pptRequestVo=" + pptRequestVo +
                '}';
    }
}