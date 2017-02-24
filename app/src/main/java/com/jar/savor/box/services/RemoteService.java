package com.jar.savor.box.services;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.IBinder;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.jar.savor.box.interfaces.OnRemoteOperationListener;
import com.jar.savor.box.vo.BaseRequestVo;
import com.jar.savor.box.vo.BaseResponse;
import com.jar.savor.box.vo.CheckResponseVo;
import com.jar.savor.box.vo.PlayRequstVo;
import com.jar.savor.box.vo.PlayResponseVo;
import com.jar.savor.box.vo.PrepareRequestVo;
import com.jar.savor.box.vo.PrepareResponseVo;
import com.jar.savor.box.vo.QueryPosBySessionIdResponseVo;
import com.jar.savor.box.vo.QueryRequestVo;
import com.jar.savor.box.vo.RotateRequestVo;
import com.jar.savor.box.vo.RotateResponseVo;
import com.jar.savor.box.vo.SeekRequestVo;
import com.jar.savor.box.vo.SeekResponseVo;
import com.jar.savor.box.vo.StopRequestVo;
import com.jar.savor.box.vo.StopResponseVo;
import com.jar.savor.box.vo.VolumeRequestVo;
import com.jar.savor.box.vo.VolumeResponseVo;
import com.savor.ads.utils.AppUtils;
import com.savor.ads.utils.ConstantValues;
import com.savor.ads.utils.LogUtils;
import com.savor.ads.utils.StringUtils;

import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;

import java.io.BufferedReader;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;

import javax.servlet.MultipartConfigElement;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;

/**
 * Created by zhanghq on 2016/12/22.
 */

public class RemoteService extends Service {
    private String TAG = "ControllService";
    private Server server = new Server(8080);
    private static OnRemoteOperationListener listener;
    private RemoteService.ServerThread mServerAsyncTask;

    public RemoteService() {
    }

    public void setOnRemoteOpreationListener(OnRemoteOperationListener listener1) {
        listener = listener1;
    }

    public void onCreate() {
        super.onCreate();
        LogUtils.d("-------------------> Service onCreate");
        this.mServerAsyncTask = new RemoteService.ServerThread();
        this.mServerAsyncTask.start();
    }

    public int onStartCommand(Intent intent, int flags, int startId) {
        LogUtils.d("-------------------> Service onStartCommand");
        return super.onStartCommand(intent, flags, startId);
    }

    public void onDestroy() {
        LogUtils.e("RemoteService" + "onDestroy");
        super.onDestroy();
        if (RemoteService.this.server != null) {
            try {
                RemoteService.this.server.stop();
            } catch (Exception var2) {
                var2.printStackTrace();
            }
        }
        if (this.mServerAsyncTask != null) {
            this.mServerAsyncTask.interrupt();
            this.mServerAsyncTask = null;
        }

    }

    public IBinder onBind(Intent intent) {
        return new RemoteService.OpreationBinder();
    }

    public static void stop(int SessionID) {
        LogUtils.d("stop()  ：listener = " + listener);
        StopRequestVo stopRequestVo = new StopRequestVo();
        stopRequestVo.setSessionid(SessionID);
        listener.stop(stopRequestVo);
    }

    public String int2ip(int ipInt) {
        StringBuilder sb = new StringBuilder();
        sb.append(ipInt & 255).append(".");
        sb.append(ipInt >> 8 & 255).append(".");
        sb.append(ipInt >> 16 & 255).append(".");
        sb.append(ipInt >> 24 & 255);
        return sb.toString();
    }

    private class ControllHandler extends AbstractHandler {

        private ControllHandler() {
        }

        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            LogUtils.d("***********一次请求...***********");
            LogUtils.d("target = " + target);
            StringBuilder stringBuilder = new StringBuilder();
            response.setContentType("text/json;charset=utf-8");
            response.setStatus(200);
            response.setHeader("Access-Control-Allow-Origin", "http://www.savorx.cn");
            baseRequest.setHandled(true);

            if (request.getContentType().contains("application/json")) {
                BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(request.getInputStream(), Charset.forName("UTF-8")));
                char[] chars = new char[1024];
                int length = 0;
                while ((length = bufferedReader.read(chars, 0, 1024)) != -1) {
                    stringBuilder.append(chars, 0, length);
                }

                String reqJson = stringBuilder.toString();
                //region 普通Json请求
                LogUtils.d("ServerName = " + request.getServerName() + " 客户端请求的内容 = " + reqJson);
                BaseRequestVo fromJson = (BaseRequestVo) (new Gson()).fromJson(reqJson, BaseRequestVo.class);
                LogUtils.d(fromJson != null ? "客户端请求功能 = " + fromJson.getFunction() : "无法解析请求");
                String resJson = "";
                if ("prepare".equalsIgnoreCase(fromJson.getFunction())) {
                    LogUtils.e("enter method listener.prepare");
                    PrepareRequestVo prepareRequest = (PrepareRequestVo) (new Gson()).fromJson(reqJson, PrepareRequestVo.class);
                    if (!TextUtils.isEmpty(prepareRequest.getDeviceId()) &&
                            (TextUtils.isEmpty(ConstantValues.CURRENT_PROJECT_DEVICE_ID) ||
                                    prepareRequest.getDeviceId().equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID))) {
                        ConstantValues.CURRENT_PROJECT_DEVICE_ID = prepareRequest.getDeviceId();
                        PrepareResponseVo object = RemoteService.listener.prepare(prepareRequest);
                        if (object.getResult() != 0) {
                            ConstantValues.CURRENT_PROJECT_DEVICE_ID = null;
                        }
                        resJson = new Gson().toJson(object);
                    } else {
                        RemoteService.listener.showProjectionTip();

                        PrepareResponseVo vo = new PrepareResponseVo();
                        vo.setResult(-1);
                        vo.setInfo("当前电视正在投屏,请稍后重试");
                        resJson = new Gson().toJson(vo);
                    }
                } else if ("play".equalsIgnoreCase(fromJson.getFunction())) {
                    LogUtils.d("enter method listener.play");
                    PlayRequstVo playRequst = (PlayRequstVo) (new Gson()).fromJson(reqJson, PlayRequstVo.class);
                    if (!TextUtils.isEmpty(playRequst.getDeviceId()) && playRequst.getDeviceId().equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID)) {
                        PlayResponseVo object = RemoteService.listener.play(playRequst);
                        resJson = new Gson().toJson(object);
                    }
                } else if ("rotate".equalsIgnoreCase(fromJson.getFunction())) {
                    LogUtils.d("enter method listener.rotate");
                    RotateRequestVo rotateRequest = (RotateRequestVo) (new Gson()).fromJson(reqJson, RotateRequestVo.class);
                    if (!TextUtils.isEmpty(rotateRequest.getDeviceId()) && rotateRequest.getDeviceId().equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID)) {
                        RotateResponseVo object = RemoteService.listener.rotate(rotateRequest);
                        resJson = new Gson().toJson(object);
                    }
                } else if ("seek_to".equalsIgnoreCase(fromJson.getFunction())) {
                    LogUtils.d("enter method listener.seek_to");
                    SeekRequestVo seekRequest = (SeekRequestVo) (new Gson()).fromJson(reqJson, SeekRequestVo.class);
                    if (!TextUtils.isEmpty(seekRequest.getDeviceId()) && seekRequest.getDeviceId().equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID)) {
                        SeekResponseVo object = RemoteService.listener.seekTo(seekRequest);
                        resJson = new Gson().toJson(object);
                    }
                } else if ("stop".equalsIgnoreCase(fromJson.getFunction())) {
                    LogUtils.e("enter method listener.stop");
                    StopRequestVo stopRequest = (StopRequestVo) (new Gson()).fromJson(reqJson, StopRequestVo.class);
                    if (!TextUtils.isEmpty(stopRequest.getDeviceId()) && stopRequest.getDeviceId().equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID)) {
                        StopResponseVo object = RemoteService.listener.stop(stopRequest);
                        resJson = new Gson().toJson(object);
                    }
                } else if ("volume".equalsIgnoreCase(fromJson.getFunction())) {
                    LogUtils.d("enter method listener.volume");
                    VolumeRequestVo volumeRequest = (VolumeRequestVo) (new Gson()).fromJson(reqJson, VolumeRequestVo.class);
                    if (!TextUtils.isEmpty(volumeRequest.getDeviceId()) && volumeRequest.getDeviceId().equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID)) {
                        VolumeResponseVo object = RemoteService.listener.volume(volumeRequest);
                        resJson = new Gson().toJson(object);
                    }
                } else if ("query".equalsIgnoreCase(fromJson.getFunction())) {
                    LogUtils.d("enter method listener.query");
                    QueryRequestVo queryRequest = (QueryRequestVo) (new Gson()).fromJson(reqJson, QueryRequestVo.class);
                    if (!TextUtils.isEmpty(queryRequest.getDeviceId()) && queryRequest.getDeviceId().equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID)) {
                        Object object = RemoteService.listener.query(queryRequest);
                        resJson = new Gson().toJson(object);
                    } else {
                        QueryPosBySessionIdResponseVo vo = new QueryPosBySessionIdResponseVo();
                        vo.setResult(-1);
                        resJson = new Gson().toJson(vo);
                    }
                } else if ("check".equalsIgnoreCase(fromJson.getFunction())) {
                    LogUtils.d("enter method listener.check");
                    CheckResponseVo responseCheck = RemoteService.listener.check();
                    resJson = new Gson().toJson(responseCheck);
                } else if ("showQRCode".equalsIgnoreCase(fromJson.getFunction())) {
                    LogUtils.d("enter method listener.showQRCode");
                    BaseRequestVo requestQr = (BaseRequestVo) (new Gson()).fromJson(reqJson, BaseRequestVo.class);
                    RemoteService.listener.showQrcode(requestQr);
                    BaseResponse responseQr = new BaseResponse();
                    responseQr.setResult(0);
                    responseQr.setInfo("成功");
                    resJson = new Gson().toJson(responseQr);
                } else {
                    LogUtils.d(" not enter any method");
                    BaseResponse baseResponse9 = new BaseResponse();
                    baseResponse9.setInfo("错误的功能");
                    baseResponse9.setResult(-1);
                    resJson = new Gson().toJson(baseResponse9);
                }

                LogUtils.d("返回结果:" + resJson);
                response.getWriter().println(resJson);
                //endregion
            } else if (request.getContentType().contains("multipart/form-data;"))  {

                String responseJson = "";

                MultipartConfigElement multipartConfigElement = new MultipartConfigElement((String)null);
                request.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, multipartConfigElement);
                if (request.getParts() != null) {
                    PrepareRequestVo prepareRequest = new PrepareRequestVo();
                    Bitmap bitmap = null;
                    for (Part part : request.getParts()) {
                        switch (part.getName()) {
                            case "fileUpload":
                                bitmap = BitmapFactory.decodeStream(part.getInputStream());
                                break;
                            case "function":
                                prepareRequest.setFunction(StringUtils.inputStreamToString(part.getInputStream()));
                                break;
                            case "action":
                                prepareRequest.setAction(StringUtils.inputStreamToString(part.getInputStream()));
                                break;
                            case "assetid":
                                prepareRequest.setAssetid(StringUtils.inputStreamToString(part.getInputStream()));
                                break;
                            case "assetname":
                                prepareRequest.setAssetname(StringUtils.inputStreamToString(part.getInputStream()));
                                break;
                            case "assettype":
                                prepareRequest.setAssettype(StringUtils.inputStreamToString(part.getInputStream()));
                                break;
                            case "asseturl":
                                prepareRequest.setAsseturl(StringUtils.inputStreamToString(part.getInputStream()));
                                break;
                            case "deviceId":
                                prepareRequest.setDeviceId(StringUtils.inputStreamToString(part.getInputStream()));
                                break;
                            case "deviceName":
                                prepareRequest.setDeviceName(StringUtils.inputStreamToString(part.getInputStream()));
                                break;
                            case "isThumbnail":
                                int isThumbnail = Integer.parseInt(StringUtils.inputStreamToString(part.getInputStream()));
                                prepareRequest.setIsThumbnail(isThumbnail);
                                break;
                            case "imageId":
                                prepareRequest.setImageId(StringUtils.inputStreamToString(part.getInputStream()));
                                break;
                        }
                        part.delete();
                    }

                    if (!TextUtils.isEmpty(prepareRequest.getDeviceId()) &&
                            (TextUtils.isEmpty(ConstantValues.CURRENT_PROJECT_DEVICE_ID) ||
                                    prepareRequest.getDeviceId().equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID))) {
                        ConstantValues.CURRENT_PROJECT_DEVICE_ID = prepareRequest.getDeviceId();
                        BaseResponse object = null;
                        if (bitmap != null) {
                            boolean showImage = false;
                            if (prepareRequest.getIsThumbnail() == 1) {
                                // 缩略图
                                ConstantValues.PROJECT_IMAGE_ID = prepareRequest.getImageId();
                                showImage = true;
                            } else {
                                // 大图
                                if (!TextUtils.isEmpty(prepareRequest.getImageId()) &&
                                        prepareRequest.getImageId().equals(ConstantValues.PROJECT_IMAGE_ID)) {
                                    showImage = true;
                                }
                            }
                            FileOutputStream outputStream = new FileOutputStream(AppUtils.getSDCardPath() + System.currentTimeMillis() + ".jpg");
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);

                            if (showImage) {
                                // 显示图片
                                ConstantValues.PROJECT_BITMAP = bitmap;
                                object = RemoteService.listener.prepare(prepareRequest);
                                if (object.getResult() != 0) {
                                    ConstantValues.CURRENT_PROJECT_DEVICE_ID = null;
                                }
                            } else {
                                // 图片被忽略
                                object = new BaseResponse();
                                object.setResult(-1);
                            }
                        }
                        responseJson = new Gson().toJson(object);
                    } else {
                        RemoteService.listener.showProjectionTip();

                        PrepareResponseVo vo = new PrepareResponseVo();
                        vo.setResult(-1);
                        vo.setInfo("当前电视正在投屏,请稍后重试");
                        responseJson = new Gson().toJson(vo);
                    }
                }

                LogUtils.d("返回结果:" + responseJson);
                response.getWriter().println(responseJson);
            }
        }
    }

    public class OpreationBinder extends Binder {
        public OpreationBinder() {
        }

        public RemoteService getControllor() {
            return RemoteService.this;
        }
    }

    private class ServerThread extends Thread {
        private ServerThread() {
        }

        public void run() {
            super.run();
            if (RemoteService.this.server != null) {
                try {
                    RemoteService.this.server.setHandler(RemoteService.this.new ControllHandler());
                    RemoteService.this.server.start();
                    RemoteService.this.server.join();
                } catch (Exception var2) {
                    var2.printStackTrace();
                }
            }

        }
    }
}
