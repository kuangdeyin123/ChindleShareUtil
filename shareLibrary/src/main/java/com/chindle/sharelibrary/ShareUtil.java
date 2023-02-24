package com.chindle.sharelibrary;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.tencent.mm.opensdk.modelmsg.SendMessageToWX;
import com.tencent.mm.opensdk.modelmsg.WXImageObject;
import com.tencent.mm.opensdk.modelmsg.WXMediaMessage;
import com.tencent.mm.opensdk.openapi.IWXAPI;
import com.tencent.mm.opensdk.openapi.WXAPIFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.List;

public class ShareUtil {

    private static ShareUtil shareUtil;
    private IWXAPI api;
    private Context mContext;

    private ShareUtil(Context mContext) {
        this.mContext = mContext;
        api = WXAPIFactory.createWXAPI(mContext, Constant.WEIXIN_APP_ID, false);
    }

    public static ShareUtil getInstance(Context ctx) {
        if (shareUtil == null) {
            synchronized (ShareUtil.class) {
                if (shareUtil == null) {
                    shareUtil = new ShareUtil(ctx);
                }
            }
        }
        return shareUtil;
    }

    public boolean isWeixinAvilible() {
        final PackageManager packageManager = mContext.getPackageManager();
        List<PackageInfo> pinfo = packageManager.getInstalledPackages(0);
        if (pinfo != null) {
            for (int i = 0; i < pinfo.size(); i++) {
                String pn = pinfo.get(i).packageName;
                if (pn.equals("com.tencent.mm")) {
                    return true;
                }
            }
        }
        return false;
    }

    public String saveBitmap(Bitmap bm) {
        ///storage/emulated/0/Android/data/com.chindle.SaaSSchool/cache/shareData/bitmap.jpg
        String dirPath = mContext.getExternalCacheDir().getPath() + "/shareData/";
        File file = new File(dirPath);
        if (!file.exists()) {
            try {
                file.mkdirs();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        File saveFile = new File(dirPath + "bitmap.jpg");
        try {
            FileOutputStream saveImgOut = new FileOutputStream(saveFile);
            bm.compress(Bitmap.CompressFormat.JPEG, 100, saveImgOut);
            saveImgOut.flush();
            saveImgOut.close();
            Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
            Uri uri = Uri.fromFile(file);
            intent.setData(uri);
            // new CenterToast(ctx).show("保存二维码成功", Toast.LENGTH_SHORT);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        return saveFile.getAbsolutePath();
    }

    /**
     * @param bitmap
     * @param type   1好友, 2朋友圈
     */
    public void shareImageToWx(Bitmap bitmap, int type) {
        if (mContext == null || bitmap == null) {
            Log.d("test","测试return");
            return;
        }
        String contentPath = saveBitmap(bitmap);
        if (checkVersionValid(api) && checkAndroidNotBelowN()) {
            String filePath = mContext.getExternalCacheDir().getPath() + "/shareData/bitmap.jpg";
            File file = new File(filePath);
            contentPath = getFileUri(file);
        }
        Log.d("contentPath",contentPath);
        WXImageObject imgObj = new WXImageObject();
        imgObj.setImagePath(contentPath);
        WXMediaMessage msg = new WXMediaMessage();
        msg.mediaObject = imgObj;
        Bitmap thumbBmp = Bitmap.createScaledBitmap(depthCompressBitmap(bitmap), 100, 100, true);
        msg.thumbData = bmpToByteArray(thumbBmp, true);
        SendMessageToWX.Req req = new SendMessageToWX.Req();
        req.transaction = buildTransaction("img");
        req.message = msg;
        if (type == 1) {
            req.scene = SendMessageToWX.Req.WXSceneSession;
        } else {
            req.scene = SendMessageToWX.Req.WXSceneTimeline;
        }
        api.sendReq(req);
    }

    public String getFileUri(File file) {
        ///storage/emulated/0/Android/data/com.chindle.SaaSSchool/cache/shareData/bitmap.jpg
        if (file == null || !file.exists()) {
            return null;
        }
        Uri contentUri = FileProvider.getUriForFile(mContext,
                "com.chindle.SaaSSchool.provider",
                file);
        mContext.grantUriPermission("com.tencent.mm",
                contentUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        return contentUri.toString();
    }

    public byte[] bmpToByteArray(final Bitmap bmp, final boolean needRecycle) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        bmp.compress(Bitmap.CompressFormat.PNG, 100, output);
        if (needRecycle) {
            bmp.recycle();
        }

        byte[] result = output.toByteArray();
        try {
            output.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public Bitmap depthCompressBitmap(Bitmap tmp) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        tmp.compress(Bitmap.CompressFormat.JPEG, 100, baos);
        ByteArrayInputStream isBm = new ByteArrayInputStream(baos.toByteArray());
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeStream(isBm, null, options);
        options.inJustDecodeBounds = false;
        float be = options.outWidth / 480;
        float scale = 0.55f;
        if (be >= 2) {
            scale = 0.35f;
        }
        Matrix matrix = new Matrix();
        matrix.setScale(scale, scale);
        tmp = Bitmap.createBitmap(tmp, 0, 0, options.outWidth, options.outHeight, matrix, true);
        baos.reset();
        tmp.compress(Bitmap.CompressFormat.JPEG, 50, baos);
        int op = 40;
        while (baos.toByteArray().length > 32 * 1024) {
            baos.reset();
            tmp.compress(Bitmap.CompressFormat.JPEG, op, baos);
            op -= 10;
            if (op <= 0) {
                break;
            }
        }
        if (baos.toByteArray().length > 32 * 1024) {
            isBm = new ByteArrayInputStream(baos.toByteArray());
            tmp = BitmapFactory.decodeStream(isBm, null, options);
            matrix.setScale(0.8f, 0.8f);
            tmp = Bitmap.createBitmap(tmp, 0, 0, tmp.getWidth(), tmp.getHeight(), matrix, true);
            baos.reset();
            tmp.compress(Bitmap.CompressFormat.JPEG, 70, baos);
        }
        return tmp;
    }

    public String buildTransaction(String type) {
        return (type == null) ? String.valueOf(System.currentTimeMillis()) : type + System.currentTimeMillis();
    }

    public boolean checkVersionValid(IWXAPI api) {
        return api.getWXAppSupportAPI() >= 0x27000D00;
    }

    public boolean checkAndroidNotBelowN() {
        return android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N;
    }

}
