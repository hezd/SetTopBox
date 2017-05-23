package com.savor.ads.projection.action;

import android.app.Activity;
import android.text.TextUtils;

import com.savor.ads.activity.LotteryActivity;
import com.savor.ads.activity.ScreenProjectionActivity;
import com.savor.ads.projection.ProjectPriority;
import com.savor.ads.utils.ActivitiesManager;
import com.savor.ads.utils.GlobalValues;

/**
 * Created by zhang.haiqiang on 2017/5/22.
 */

public class StopAction extends ProjectionActionBase {

    private String projectId;
    private boolean isLottery;

    public StopAction(String projectId, boolean isLottery) {
        super();

        mPriority = ProjectPriority.HIGH;
        this.projectId = projectId;
        this.isLottery = isLottery;
    }

    @Override
    public void execute() {
        onActionBegin();
        Activity activity = ActivitiesManager.getInstance().getCurrentActivity();
        if (isLottery) {
            if (activity instanceof LotteryActivity) {
                ((LotteryActivity) activity).stop(this);
            }
        } else {
            if (activity instanceof ScreenProjectionActivity &&
                    !TextUtils.isEmpty(projectId) && projectId.equals(GlobalValues.CURRENT_PROJECT_ID)) {
                ((ScreenProjectionActivity) activity).stop(true, this);
            }
        }
    }

    @Override
    public String toString() {
        return "StopAction{" +
                "projectId='" + projectId + '\'' +
                ", isLottery=" + isLottery +
                '}';
    }
}
