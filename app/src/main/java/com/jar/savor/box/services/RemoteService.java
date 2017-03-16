package com.jar.savor.box.services;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Binder;
import android.os.IBinder;
import android.support.annotation.NonNull;
import android.text.TextUtils;

import com.google.gson.Gson;
import com.jar.savor.box.interfaces.OnRemoteOperationListener;
import com.jar.savor.box.vo.BaseRequestVo;
import com.jar.savor.box.vo.BaseResponse;
import com.jar.savor.box.vo.PlayRequestVo;
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
import com.jar.savor.box.vo.VideoPrepareRequestVo;
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
        return new OperationBinder();
    }

    private class ControllHandler extends AbstractHandler {

        private ControllHandler() {
        }

        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            LogUtils.d("***********一次请求...***********");
            LogUtils.d("target = " + target);
            response.setContentType("application/json;charset=utf-8");
            response.setStatus(200);
            baseRequest.setHandled(true);

            String version = request.getHeader("version");
            if ("1.0".equals(version)) {
                handleRequestV10(request, response);
            } else {
                handleRequestOld(request, response);
            }
        }

        private void handleRequestOld(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            if (request.getContentType().contains("application/json")) {

                //region 普通Json请求
                String reqJson = getBodyString(request);

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
                        ConstantValues.CURRENT_PROJECT_DEVICE_NAME = prepareRequest.getDeviceName();
                        PrepareResponseVo object = RemoteService.listener.prepare(prepareRequest);
                        if (object.getResult() != ConstantValues.SERVER_RESPONSE_CODE_SUCCESS) {
                            ConstantValues.CURRENT_PROJECT_DEVICE_ID = null;
                            ConstantValues.CURRENT_PROJECT_DEVICE_NAME = null;
                        }
                        resJson = new Gson().toJson(object);
                    } else {
                        PrepareResponseVo vo = new PrepareResponseVo();
                        vo.setResult(ConstantValues.SERVER_RESPONSE_CODE_FAILED);
                        vo.setInfo("请稍等，" + ConstantValues.CURRENT_PROJECT_DEVICE_NAME + " 正在投屏");
                        resJson = new Gson().toJson(vo);
                    }
                } else if ("play".equalsIgnoreCase(fromJson.getFunction())) {
                    LogUtils.d("enter method listener.play");
                    PlayRequestVo playRequst = (PlayRequestVo) (new Gson()).fromJson(reqJson, PlayRequestVo.class);
                    if (!TextUtils.isEmpty(playRequst.getDeviceId()) && playRequst.getDeviceId().equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID)) {
                        PlayResponseVo object = RemoteService.listener.play(playRequst.getRate());
                        resJson = new Gson().toJson(object);
                    }
                } else if ("rotate".equalsIgnoreCase(fromJson.getFunction())) {
                    LogUtils.d("enter method listener.rotate");
                    RotateRequestVo rotateRequest = (RotateRequestVo) (new Gson()).fromJson(reqJson, RotateRequestVo.class);
                    if (!TextUtils.isEmpty(rotateRequest.getDeviceId()) && rotateRequest.getDeviceId().equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID)) {
                        RotateResponseVo object = RemoteService.listener.rotate(rotateRequest.getRotatevalue());
                        resJson = new Gson().toJson(object);
                    }
                } else if ("seek_to".equalsIgnoreCase(fromJson.getFunction())) {
                    LogUtils.d("enter method listener.seek");
                    SeekRequestVo seekRequest = (SeekRequestVo) (new Gson()).fromJson(reqJson, SeekRequestVo.class);
                    if (!TextUtils.isEmpty(seekRequest.getDeviceId()) && seekRequest.getDeviceId().equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID)) {
                        SeekResponseVo object = RemoteService.listener.seek(seekRequest.getAbsolutepos());
                        resJson = new Gson().toJson(object);
                    }
                } else if ("stop".equalsIgnoreCase(fromJson.getFunction())) {
                    LogUtils.e("enter method listener.stop");
                    StopRequestVo stopRequest = (StopRequestVo) (new Gson()).fromJson(reqJson, StopRequestVo.class);
                    if (!TextUtils.isEmpty(stopRequest.getDeviceId()) && stopRequest.getDeviceId().equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID)) {
                        StopResponseVo object = RemoteService.listener.stop();
                        resJson = new Gson().toJson(object);

                        ConstantValues.CURRENT_PROJECT_DEVICE_ID = null;
                        ConstantValues.CURRENT_PROJECT_DEVICE_NAME = null;
                        ConstantValues.PROJECT_IMAGE_ID = null;
                    }
                } else if ("volume".equalsIgnoreCase(fromJson.getFunction())) {
                    LogUtils.d("enter method listener.volume");
                    VolumeRequestVo volumeRequest = (VolumeRequestVo) (new Gson()).fromJson(reqJson, VolumeRequestVo.class);
                    if (!TextUtils.isEmpty(volumeRequest.getDeviceId()) && volumeRequest.getDeviceId().equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID)) {
                        VolumeResponseVo object = RemoteService.listener.volume(volumeRequest.getAction());
                        resJson = new Gson().toJson(object);
                    }
                } else if ("query".equalsIgnoreCase(fromJson.getFunction())) {
                    LogUtils.d("enter method listener.query");
                    QueryRequestVo queryRequest = (QueryRequestVo) (new Gson()).fromJson(reqJson, QueryRequestVo.class);
                    if (!TextUtils.isEmpty(queryRequest.getDeviceId()) && queryRequest.getDeviceId().equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID)) {
                        Object object = RemoteService.listener.query();
                        resJson = new Gson().toJson(object);
                    } else {
                        QueryPosBySessionIdResponseVo vo = new QueryPosBySessionIdResponseVo();
                        vo.setResult(ConstantValues.SERVER_RESPONSE_CODE_FAILED);
                        resJson = new Gson().toJson(vo);
                    }
                } else {
                    LogUtils.d(" not enter any method");
                    BaseResponse baseResponse9 = new BaseResponse();
                    baseResponse9.setInfo("错误的功能");
                    baseResponse9.setResult(ConstantValues.SERVER_RESPONSE_CODE_FAILED);
                    resJson = new Gson().toJson(baseResponse9);
                }

                LogUtils.d("返回结果:" + resJson);
                response.getWriter().println(resJson);
                //endregion
            } else if (request.getContentType().contains("multipart/form-data;")) {

                // 图片流投屏处理
                handleStreamImageProjection(request, response);
            }
        }

        @NonNull
        private String getBodyString(HttpServletRequest request) throws IOException {
            StringBuilder stringBuilder = new StringBuilder();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(request.getInputStream(), Charset.forName("UTF-8")));
            char[] chars = new char[1024];
            int length = 0;
            while ((length = bufferedReader.read(chars, 0, 1024)) != -1) {
                stringBuilder.append(chars, 0, length);
            }
            return stringBuilder.toString();
        }

        private void handleRequestV10(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            String resJson = "";
            String path = request.getPathInfo();
            if (TextUtils.isEmpty(path)) {
                BaseResponse baseResponse = new BaseResponse();
                baseResponse.setInfo("错误的功能");
                baseResponse.setResult(ConstantValues.SERVER_RESPONSE_CODE_FAILED);
                resJson = new Gson().toJson(baseResponse);
            } else {
                String[] dirs = path.split("/");
                if (dirs.length <= 1) {
                    BaseResponse baseResponse = new BaseResponse();
                    baseResponse.setInfo("错误的功能");
                    baseResponse.setResult(ConstantValues.SERVER_RESPONSE_CODE_FAILED);
                    resJson = new Gson().toJson(baseResponse);
                } else {

                    String action = dirs[1];
                    String deviceId = request.getParameter("deviceId");
                    String deviceName = request.getParameter("deviceName");
                    switch (action) {
                        case "vod":
                            String type = request.getParameter("type");
                            String mediaName = request.getParameter("name");
                            if (!TextUtils.isEmpty(deviceId) &&
                                    (TextUtils.isEmpty(ConstantValues.CURRENT_PROJECT_DEVICE_ID) ||
                                            deviceId.equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID))) {
                                ConstantValues.CURRENT_PROJECT_DEVICE_ID = deviceId;
                                ConstantValues.CURRENT_PROJECT_DEVICE_NAME = deviceName;
                                BaseResponse object = RemoteService.listener.showVod(mediaName, type);
                                if (object.getResult() != ConstantValues.SERVER_RESPONSE_CODE_SUCCESS) {
                                    ConstantValues.CURRENT_PROJECT_DEVICE_ID = null;
                                    ConstantValues.CURRENT_PROJECT_DEVICE_NAME = null;
                                }
                                resJson = new Gson().toJson(object);
                            } else {
                                PrepareResponseVo vo = new PrepareResponseVo();
                                vo.setResult(ConstantValues.SERVER_RESPONSE_CODE_FAILED);
                                vo.setInfo("请稍等，" + ConstantValues.CURRENT_PROJECT_DEVICE_NAME + " 正在投屏");
                                resJson = new Gson().toJson(vo);
                            }
                            break;
                        case "video":
                            String reqJson = getBodyString(request);
                            VideoPrepareRequestVo req = (new Gson()).fromJson(reqJson, VideoPrepareRequestVo.class);
                            String videoPath = req.getMediaPath();
                            if (!TextUtils.isEmpty(deviceId) &&
                                    (TextUtils.isEmpty(ConstantValues.CURRENT_PROJECT_DEVICE_ID) ||
                                            deviceId.equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID))) {
                                ConstantValues.CURRENT_PROJECT_DEVICE_ID = deviceId;
                                ConstantValues.CURRENT_PROJECT_DEVICE_NAME = deviceName;
                                BaseResponse object = RemoteService.listener.showVideo(videoPath);
                                if (object.getResult() != ConstantValues.SERVER_RESPONSE_CODE_SUCCESS) {
                                    ConstantValues.CURRENT_PROJECT_DEVICE_ID = null;
                                    ConstantValues.CURRENT_PROJECT_DEVICE_NAME = null;
                                }
                                resJson = new Gson().toJson(object);
                            } else {
                                PrepareResponseVo vo = new PrepareResponseVo();
                                vo.setResult(ConstantValues.SERVER_RESPONSE_CODE_FAILED);
                                vo.setInfo("请稍等，" + ConstantValues.CURRENT_PROJECT_DEVICE_NAME + " 正在投屏");
                                resJson = new Gson().toJson(vo);
                            }
                            break;
                        case "pic":
                            String isThumbnail = request.getParameter("isThumbnail");
                            String imageId = request.getParameter("imageId");
                            if (request.getContentType().contains("multipart/form-data;")) {

                                // 图片流投屏处理
                                handleStreamImageProjection(request, response, deviceId, deviceName,
                                        isThumbnail, imageId);
                            }
                            break;
                        case "stop":
                            LogUtils.e("enter method listener.stop");
                            if (!TextUtils.isEmpty(deviceId) && deviceId.equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID)) {
                                StopResponseVo object = RemoteService.listener.stop();
                                resJson = new Gson().toJson(object);

                                ConstantValues.CURRENT_PROJECT_DEVICE_ID = null;
                                ConstantValues.CURRENT_PROJECT_DEVICE_NAME = null;
                                ConstantValues.PROJECT_IMAGE_ID = null;
                            }
                            break;
                        case "rotate":
                            LogUtils.d("enter method listener.rotate");
                            if (!TextUtils.isEmpty(deviceId) && deviceId.equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID)) {
                                RotateResponseVo object = RemoteService.listener.rotate(90);
                                resJson = new Gson().toJson(object);
                            }
                            break;
                        case "resume":
                            LogUtils.d("enter method listener.play");
                            if (!TextUtils.isEmpty(deviceId) && deviceId.equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID)) {
                                PlayResponseVo object = RemoteService.listener.play(1);
                                resJson = new Gson().toJson(object);
                            }
                            break;
                        case "pause":
                            LogUtils.d("enter method listener.play");
                            if (!TextUtils.isEmpty(deviceId) && deviceId.equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID)) {
                                PlayResponseVo object = RemoteService.listener.play(0);
                                resJson = new Gson().toJson(object);
                            }
                            break;
                        case "seek":
                            LogUtils.d("enter method listener.play");
                            int position = Integer.parseInt(request.getParameter("position"));
                            if (!TextUtils.isEmpty(deviceId) && deviceId.equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID)) {
                                SeekResponseVo object = RemoteService.listener.seek(position);
                                resJson = new Gson().toJson(object);
                            }
                            break;
                        case "volume":
                            LogUtils.d("enter method listener.volume");
                            int volumeAction = Integer.parseInt(request.getParameter("action"));
                            if (!TextUtils.isEmpty(deviceId) && deviceId.equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID)) {
                                VolumeResponseVo object = RemoteService.listener.volume(volumeAction);
                                resJson = new Gson().toJson(object);
                            }
                            break;
                        case "query":
                            LogUtils.d("enter method listener.query");
                            if (!TextUtils.isEmpty(deviceId) && deviceId.equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID)) {
                                Object object = RemoteService.listener.query();
                                resJson = new Gson().toJson(object);
                            } else {
                                QueryPosBySessionIdResponseVo vo = new QueryPosBySessionIdResponseVo();
                                vo.setResult(ConstantValues.SERVER_RESPONSE_CODE_FAILED);
                                resJson = new Gson().toJson(vo);
                            }
                            break;
                        default:
                            LogUtils.d(" not enter any method");
                            BaseResponse baseResponse = new BaseResponse();
                            baseResponse.setInfo("错误的功能");
                            baseResponse.setResult(ConstantValues.SERVER_RESPONSE_CODE_FAILED);
                            resJson = new Gson().toJson(baseResponse);
                            break;
                    }
                }
            }

            if (TextUtils.isEmpty(resJson)) {
                BaseResponse baseResponse = new BaseResponse();
                baseResponse.setResult(ConstantValues.SERVER_RESPONSE_CODE_FAILED);
                baseResponse.setInfo("操作失败");
                resJson = new Gson().toJson(baseResponse);
            }
            LogUtils.d("返回结果:" + resJson);
            response.getWriter().println(resJson);
        }

        private void handleStreamImageProjection(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
            String responseJson = "";

            MultipartConfigElement multipartConfigElement = new MultipartConfigElement((String) null);
            request.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, multipartConfigElement);
            if (request.getParts() != null) {
                PrepareRequestVo prepareRequest = new PrepareRequestVo();
                Bitmap bitmap = null;
                for (Part part : request.getParts()) {
                    switch (part.getName()) {
                        case "fileUpload":
                            bitmap = BitmapFactory.decodeStream(part.getInputStream());
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
                    ConstantValues.CURRENT_PROJECT_DEVICE_NAME = prepareRequest.getDeviceName();
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
                            object = RemoteService.listener.showImage();
                            if (object.getResult() != ConstantValues.SERVER_RESPONSE_CODE_SUCCESS) {
                                ConstantValues.CURRENT_PROJECT_DEVICE_ID = null;
                                ConstantValues.CURRENT_PROJECT_DEVICE_NAME = null;
                            }
                        } else {
                            // 图片被忽略
                            object = new BaseResponse();
                            object.setResult(ConstantValues.SERVER_RESPONSE_CODE_NOT_MATCH);
                        }
                    }
                    responseJson = new Gson().toJson(object);
                } else {
                    PrepareResponseVo vo = new PrepareResponseVo();
                    vo.setResult(ConstantValues.SERVER_RESPONSE_CODE_FAILED);
                    vo.setInfo("请稍等，" + ConstantValues.CURRENT_PROJECT_DEVICE_NAME + " 正在投屏");
                    responseJson = new Gson().toJson(vo);
                }
            }

            LogUtils.d("返回结果:" + responseJson);
            response.getWriter().println(responseJson);
        }

        private void handleStreamImageProjection(HttpServletRequest request, HttpServletResponse response,
                                                 String deviceId, String deviceName, String isThumbnail,
                                                 String imageId) throws IOException, ServletException {
            String responseJson = "";

            if (!TextUtils.isEmpty(deviceId) &&
                    (TextUtils.isEmpty(ConstantValues.CURRENT_PROJECT_DEVICE_ID) ||
                            deviceId.equals(ConstantValues.CURRENT_PROJECT_DEVICE_ID))) {
                ConstantValues.CURRENT_PROJECT_DEVICE_ID = deviceId;
                ConstantValues.CURRENT_PROJECT_DEVICE_NAME = deviceName;
                BaseResponse object = null;

                boolean showImage = false;
                if ("1".equals(isThumbnail)) {
                    // 缩略图
                    ConstantValues.PROJECT_IMAGE_ID = imageId;
                    showImage = true;
                } else {
                    // 大图
                    if (!TextUtils.isEmpty(imageId) &&
                            imageId.equals(ConstantValues.PROJECT_IMAGE_ID)) {
                        showImage = true;
                    }
                }
//                            FileOutputStream outputStream = new FileOutputStream(AppUtils.getSDCardPath() + System.currentTimeMillis() + ".jpg");
//                            bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);

                if (showImage) {
                    Bitmap bitmap = null;

                    MultipartConfigElement multipartConfigElement = new MultipartConfigElement((String) null);
                    request.setAttribute(Request.__MULTIPART_CONFIG_ELEMENT, multipartConfigElement);
                    if (request.getParts() != null) {
                        for (Part part : request.getParts()) {
                            switch (part.getName()) {
                                case "fileUpload":
                                    bitmap = BitmapFactory.decodeStream(part.getInputStream());
                                    break;
                            }
                            part.delete();
                        }

                        // 显示图片
                        ConstantValues.PROJECT_BITMAP = bitmap;
                        object = RemoteService.listener.showImage();
                        if (object.getResult() != ConstantValues.SERVER_RESPONSE_CODE_SUCCESS) {
                            ConstantValues.CURRENT_PROJECT_DEVICE_ID = null;
                            ConstantValues.CURRENT_PROJECT_DEVICE_NAME = null;
                        }
                    } else {
                        // 请求格式错误
                        object = new BaseResponse();
                        object.setResult(ConstantValues.SERVER_RESPONSE_CODE_FAILED);
                    }
                } else {
                    // 图片被忽略
                    object = new BaseResponse();
                    object.setResult(ConstantValues.SERVER_RESPONSE_CODE_NOT_MATCH);
                }

                responseJson = new Gson().toJson(object);
            } else {
                BaseResponse vo = new BaseResponse();
                vo.setResult(ConstantValues.SERVER_RESPONSE_CODE_FAILED);
                vo.setInfo("请稍等，" + ConstantValues.CURRENT_PROJECT_DEVICE_NAME + " 正在投屏");
                responseJson = new Gson().toJson(vo);
            }

            LogUtils.d("返回结果:" + responseJson);
            response.getWriter().println(responseJson);
        }
    }

    public class OperationBinder extends Binder {
        public OperationBinder() {
        }

        public RemoteService getController() {
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
