package com.zhijianhuo.cordova.plugin;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import com.ali.auth.third.core.model.Session;
import com.alibaba.baichuan.android.trade.AlibcTrade;
import com.alibaba.baichuan.android.trade.AlibcTradeSDK;
import com.alibaba.baichuan.android.trade.callback.AlibcTradeCallback;
import com.alibaba.baichuan.android.trade.callback.AlibcTradeInitCallback;
import com.alibaba.baichuan.android.trade.model.AlibcShowParams;
import com.alibaba.baichuan.android.trade.model.OpenType;
//import com.alibaba.baichuan.android.trade.page.AlibcAddCartPage;
//import com.alibaba.baichuan.android.trade.page.AlibcBasePage;
//import com.alibaba.baichuan.android.trade.page.AlibcDetailPage;
//import com.alibaba.baichuan.android.trade.page.AlibcMyCartsPage;
//import com.alibaba.baichuan.android.trade.page.AlibcMyOrdersPage;
//import com.alibaba.baichuan.android.trade.page.AlibcPage;
//import com.alibaba.baichuan.android.trade.page.AlibcShopPage;
import com.alibaba.baichuan.trade.biz.applink.adapter.AlibcFailModeType;
import com.alibaba.baichuan.trade.biz.context.AlibcTradeResult;
import com.alibaba.baichuan.trade.biz.core.taoke.AlibcTaokeParams;
import com.alibaba.baichuan.trade.biz.login.AlibcLogin;
import com.alibaba.baichuan.trade.biz.login.AlibcLoginCallback;
import com.alibaba.baichuan.trade.common.utils.JSONUtils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import android.webkit.WebChromeClient;
import android.webkit.WebViewClient;


public class BaichuanPlugin extends CordovaPlugin {


    private static final String TAG = "BaichuanPlugin";

    private Boolean sdk_inited = false;

    public void initialize(final CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        if (!sdk_inited) {
            AlibcTradeSDK.asyncInit(cordova.getActivity().getApplication(), new AlibcTradeInitCallback() {
                @Override
                public void onSuccess() {
                    sdk_inited = true;
                    System.err.println("AlibcTradeSDK inited ok");
//                    Toast.makeText(cordova.getActivity(), "初始化成功", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFailure(int code, String message) {
                    sdk_inited = false;
                    System.err.println("AlibcTradeSDK onFailure " + message);
//                    Toast.makeText(cordova.getActivity(), "初始化异常", Toast.LENGTH_SHORT).show();
                }

            });
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        AlibcTradeSDK.destory();
    }

    public AlibcTradeCallback callback(final CallbackContext cbc) {
        return new AlibcTradeCallback() {
            @Override
            public void onTradeSuccess(AlibcTradeResult tradeResult) {
                System.out.println(JSONUtils.objectToJson("tradeResult", tradeResult));
                //当addCartPage加购成功和其他page支付成功的时候会回调
                switch (tradeResult.resultType) {
                    case TYPECART:
                        //加购成功
                        cbc.success("加购成功");
                        break;
                    case TYPEPAY:
                        //支付成功
                        Map<String, Object> rst = new HashMap<String, Object>();
                        rst.put("paySuccessOrders", tradeResult.payResult.paySuccessOrders);
                        rst.put("payFailedOrders", tradeResult.payResult.payFailedOrders);
                        success(cbc, rst);
                        break;
                    default:
                        cbc.success("操作成功");
                }
            }

            @Override
            public void onFailure(int errCode, String errMsg) {
                cbc.error("电商SDK出错,错误码=" + errCode + " / 错误消息=" + errMsg);
            }
        };
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (!sdk_inited) {
            callbackContext.error("插件初始化失败");
            return false;
        }
        if (args.length() < 1) {
            callbackContext.error("参数不正确");
            return true;
        }
        try {
            if ("showPage".equals(action)) {
                return showPage(args.getJSONObject(0), args.optJSONObject(1), args.optJSONObject(2), args.optJSONObject(3), callbackContext);
            } else if ("setting".equals(action)) {
                return setting(args.getJSONObject(0), callbackContext);
            } else if ("auth".equals(action)) {
                return auth(args.getString(0), callbackContext);
            }
            callbackContext.error("Invalid Action");
            return false;
        } catch (Exception e) {
            System.err.println("Exception: " + e.getMessage());
            callbackContext.error(e.getMessage());
            return false;
        }
    }

    private boolean auth(String action, final CallbackContext callbackContext) throws JSONException {
        final AlibcLogin alibcLogin = AlibcLogin.getInstance();
        switch (action) {
            case "login":
                alibcLogin.showLogin(new AlibcLoginCallback() {
                    @Override
                    public void onSuccess(int i,String userId, String nick) {
                        returnSession(callbackContext);
                    }
                    @Override
                    public void onFailure(int i, String s) {
                        callbackContext.error(s);
                    }
                });
                break;
            case "getSession":
                returnSession(callbackContext);
                break;
            case "logout":
                alibcLogin.logout(new AlibcLoginCallback() {
                    @Override
                    public void onSuccess(int i,String userId, String nick) {
                        callbackContext.success();
                    }

                    @Override
                    public void onFailure(int i, String s) {
                        callbackContext.error(s);
                    }
                });
                break;
            default:
                callbackContext.error("Invalid Action");
                return false;
        }
        return true;
    }

    private void returnSession(CallbackContext callbackContext) {
        final AlibcLogin alibcLogin = AlibcLogin.getInstance();
        JSONObject jsonObject = new JSONObject();
        boolean login = alibcLogin.isLogin();
        try {
            jsonObject.put("login", login);
            if(login) {
                Session session = alibcLogin.getSession();
                jsonObject.put("userid", session.userid);
                jsonObject.put("nick", session.nick);
                jsonObject.put("avatarUrl", session.avatarUrl);
                jsonObject.put("openId", session.openId);
                jsonObject.put("openSid", session.openSid);
                jsonObject.put("topAccessToken", session.topAccessToken);
                jsonObject.put("topAuthCode", session.topAuthCode);
                jsonObject.put("topExpireTime", session.topExpireTime);
            }
        } catch (JSONException e) {
            callbackContext.error("转化数据失败，失败原因为：" + e.getMessage());
        }
        success(callbackContext, jsonObject);
    }

    private boolean setting(JSONObject settings, CallbackContext callbackContext) throws JSONException {
        Iterator<String> keys = settings.keys();
        for (String key; keys.hasNext(); ) {
            key = keys.next();
//            if ("forceH5".equals(key)) {
//                AlibcTradeSDK.setForceH5(settings.optBoolean(key));
//            } else if ("syncForTaoke".equals(key)) {
//                AlibcTradeSDK.setSyncForTaoke(settings.optBoolean(key));
//            } else if ("taokeParams".equals(key)) {
//                AlibcTradeSDK.setTaokeParams(getTaokeParams(settings.optJSONObject(key)));
//            } else if ("channel".equals(key)) {
//                JSONArray jsonArray = settings.optJSONArray(key);
//                AlibcTradeSDK.setChannel(jsonArray.getString(0), jsonArray.getString(1));
//            } else if ("ISVCode".equals(key)) {
//                AlibcTradeSDK.setISVCode(settings.optString(key));
//            } else if ("ISVVersion".equals(key)) {
//                AlibcTradeSDK.setISVVersion(settings.optString(key));
//            }
        }
        callbackContext.success();
        return true;
    }

    private AlibcTaokeParams getTaokeParams(JSONObject taokeArgs) {
        if (null == taokeArgs) {
            return null;
        }
        AlibcTaokeParams taokeParams = new AlibcTaokeParams("", "", "");

        taokeParams.setPid(taokeArgs.optString("pid"));
        taokeParams.setAdzoneid(taokeArgs.optString("adzoneid"));
        taokeParams.setSubPid(taokeArgs.optString("subPid"));
        taokeParams.setUnionId(taokeArgs.optString("unionId"));

        String key = taokeArgs.optString("key");
        if (null != key) {
            taokeParams.extraParams = new HashMap<String, String>();
            taokeParams.extraParams.put("key", key);
        }
        return taokeParams;
    }

    private boolean showPage(JSONObject pageArgs, JSONObject taokeArgs, JSONObject showArgs, JSONObject exArgs, CallbackContext callbackContext) throws JSONException {
        AlibcTaokeParams taokeParams = getTaokeParams(taokeArgs);

        AlibcShowParams showParam = new AlibcShowParams();

        showParam.setClientType("taobao");
        showParam.setOpenType(OpenType.Native);
        showParam.setBackUrl("");
        showParam.setNativeOpenFailedMode(AlibcFailModeType.AlibcNativeFailModeJumpH5);

        if (null != showArgs) {
            if (showArgs.has("openType"))
                showParam.setOpenType(OpenType.valueOf(showArgs.optString("openType")));
            if (showArgs.has("backUrl")) showParam.setBackUrl(showArgs.optString("backUrl"));
            if (showArgs.has("clientType"))
                showParam.setClientType(showArgs.optString("clientType"));
            if (showArgs.has("nativeFailMode"))
                showParam.setNativeOpenFailedMode(AlibcFailModeType.valueOf("AlibcNativeFailMode" + showArgs.optString("nativeFailMode")));
            if (showArgs.has("pageClose")) showParam.setPageClose(showArgs.optBoolean("pageClose"));
            if (showArgs.has("proxyWebview"))
                showParam.setProxyWebview(showArgs.optBoolean("proxyWebview"));
            if (showArgs.has("showTitleBar"))
                showParam.setShowTitleBar(showArgs.optBoolean("showTitleBar"));
            if (showArgs.has("title")) showParam.setTitle(showArgs.optString("title"));
        }

        //提供给三方传递配置参数
        Map<String, String> exParams = new HashMap<String, String>();
//        exParams.put(AlibcConstants.ISV_CODE, "appisvcode");
        if (null != exArgs) {
            Iterator<String> keys = exArgs.keys();
            for (String key; keys.hasNext(); ) {
                key = keys.next();
                exParams.put(key, exArgs.getString(key));
            }
        }

        AlibcTrade.openByUrl(cordova.getActivity(), "trade",pageArgs.getString("url"), null,new WebViewClient(), new WebChromeClient(),showParam, taokeParams, exParams, callback(callbackContext));
        return true;
    }

    protected void success(CallbackContext callbackContext, Object result) {
        if(result instanceof String) {
            callbackContext.success((String) result);
        } else if(result instanceof JSONArray) {
            callbackContext.success((JSONArray) result);
        } else if(result instanceof JSONObject) {
            callbackContext.success((JSONObject) result);
        } else if(result instanceof List) {
            callbackContext.success(new JSONArray((List) result));
        } else if(result instanceof Map) {
            callbackContext.success(new JSONObject((Map) result));
        } else if(null != result) {
            callbackContext.error("插件返回结果类型不能识别");
        } else {
            callbackContext.success();
        }
    }

}
