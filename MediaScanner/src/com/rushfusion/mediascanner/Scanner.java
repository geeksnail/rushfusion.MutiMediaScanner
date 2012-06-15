package com.rushfusion.mediascanner;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Environment;
import android.os.StatFs;
import android.text.format.Formatter;
import android.util.Log;

/**
 * @author rushfusion 
 * (1).�ж���ƶ��豸,���豸�����ռ��С˳��һ��һ��ɨ��;
 * (2).ͬһ���豸��,��������������ԭ��,�Ӹ�Ŀ¼��ʼɨ���豸�ϵ������ļ���,ֱ���ﵽ����ָ�����ļ������Ϊֹ;
 * (3).��ɨ�赽ĳһ�ļ�����������һ����ý���ļ�ʱ,����ֹͣ���ļ������ļ�ɨ��,���ø��ļ���·�����������ûص�����ָ������void
 * 		onFind(String path); Ȼ�󰴲���(1),(2),(3)����ɨ����һ��Ӧ��ɨ���ļ���; 
 * (4).ȫ��ɨ����ɺ�:
 * 		�������ָ������ļ�����Ȼ�û��ɨ�����,�ò���false���ûص�������һ������onScanCompleted(boolean completed);
 * 		���ɨ���������ļ���,δ����ָ������ļ������,�ò���true���ûص����󷽷�onScanCompleted(boolean completed); 
 * (5). ���ɨ���г����쳣:δ�����ƶ��豸,�ƶ��豸�γ���,�ö����쳣����Ϊ�������ûص�����void onError(Exception e,int code);
 */
public class Scanner {

	private static final int TYPE_IMAGE = 1;// image file
	private static final int TYPE_AUDIO = 2;// audio file
	private static final int TYPE_VIDEO = 3;// video file

	public static final int ERROR_TYPE = 101;// type error
	public static final int ERROR_MAX_SCAN_DEEP = 102;// max scan deep error
	public static final int ERROR_DEVICE_NOT_FOUND = 103;// δ�����ƶ��豸
	public static final int ERROR_DEVICE_REMOVED = 104;// �ƶ��豸�γ�

	private static final String TAG = "Scanner";

	private static Scanner scanner;

	private static List<File> sdcards;
	private static LinkedList<String> Images = null;
	private static LinkedList<String> Audios = null;
	private static LinkedList<String> Videos = null;

	private static Context ctx;
	private static ScanCallback callback;
	
	
	private int currentDeep = 0;
	private int maxScanDeep = 0;
	private static boolean isScanning = false;
	private boolean isDeepEnough = true;
	
	private Scanner() {

	}

	public static Scanner getInstance(Context context) {
		if (scanner == null) {
			ctx = context;
			scanner = new Scanner();
			sdcards = new ArrayList<File>();
			initMediaType();
		}
		return scanner;
	}

	/**
	 * �ص��ӿ�
	 * @author lsm
	 */
	public interface ScanCallback {
		public void onFind(String path);

		public void onScanCompleted(boolean completed);

		public void onError(Exception e, int code);
	}
	
	/**
	 * ��ʼ��ý���ļ�����
	 * ������������ͣ�
	 * JPEG,JPG,PNG,GIF,BMP,
	 * MP3,WAV,WMA,ACC,APE,OGG,
	 * MP4,AVi,RM,RMVB,WMV,ASF,ASX,3GP,M4V,DAT��FLV,VOB,MOV
	 * ���û����Ҫ��ý���ļ�������ʹ��addMediaType(int mediaType,String extensionName)��ӡ�
	 */
	private static void initMediaType() {
		Images = new LinkedList<String>();
		Audios = new LinkedList<String>();
		Videos = new LinkedList<String>();
		Images.add(".JPEG");
		Images.add(".JPG");
		Images.add(".PNG");
		Images.add(".GIF");
		Images.add(".BMP");
		// -----------------
		Audios.add(".MP3");
		Audios.add(".WAV");
		Audios.add(".WMA");
		Audios.add(".ACC");
		Audios.add(".APE");
		Audios.add(".OGG");
		// -----------------
		Videos.add(".MP4");
		Videos.add(".AVI");
		Videos.add(".RM");
		Videos.add(".RMVB");
		Videos.add(".WMV");
		Videos.add(".ASF");
		Videos.add(".ASX");
		Videos.add(".3GP");
		Videos.add(".M4V");
		Videos.add(".DAT");
		Videos.add(".FLV");
		Videos.add(".VOB");
		Videos.add(".MOV");
	}
	
	/**
	 * 
	 * @param mediaType:    
	 * 			TYPE_IMAGE  1;// image file  
	 * 			TYPE_AUDIO	2;// audio file
	 * 			TYPE_VIDEO	3;// video file
	 * @param extensionName:
	 * 			��:".JPG",����� "."				  
	 * @throws Exception ��չ����ʽ�쳣
	 */
	public void addMediaType(int mediaType,String extensionName) throws Exception{
		if(!extensionName.contains(".")){
			throw new Exception("��չ��������� '.'");
		}
		extensionName = extensionName.substring(extensionName.lastIndexOf('.')).toUpperCase();
		switch (mediaType) {
		case TYPE_IMAGE:
			Images.add(extensionName);
			break;
		case TYPE_AUDIO:
			Audios.add(extensionName);
			break;
		case TYPE_VIDEO:
			Videos.add(extensionName);
			break;
		default:
			break;
		}
	}
	
	
	/**
	 * 
	 * @param mediaType ý������:1:ͼƬ,2:��Ƶ,3:��Ƶ;
	 * @param maxScanDeep ����ļ������
	 * @param callback һ���ص�����
	 */
	public void scan(int mediaType, int maxScanDeep, ScanCallback callback) {
		isScanning = true;
		readSDCard(callback);
		currentDeep = 0;
		this.maxScanDeep = maxScanDeep;
		Scanner.callback = callback;
		IntentFilter intentFilter = new IntentFilter();
		intentFilter.addAction(Intent.ACTION_MEDIA_EJECT);
		intentFilter.addDataScheme("file");
		ctx.registerReceiver(mUnmountReceiver, intentFilter);
		
		File[] files = reorderArray(sdcards);
		if (mediaType == TYPE_IMAGE) {
			scanByMediaType(files, callback, Images);
		} else if (mediaType == TYPE_AUDIO) {
			scanByMediaType(files, callback, Audios);
		} else if (mediaType == TYPE_VIDEO) {
			scanByMediaType(files, callback, Videos);
		} else {// δ֪type
			callback.onError(new Exception("δ֪���������쳣 mediaType-->" + mediaType),ERROR_TYPE);
		}
		ctx.unregisterReceiver(mUnmountReceiver);
		isScanning = false;
	}

	/**
	 * 
	 * @param dirs
	 * @param currentDeep
	 * @param callback
	 * @param mediaTypes
	 * @return
	 */
	private void scanByMediaType(File[] dirs, ScanCallback callback,LinkedList<String> mediaTypes) {
		for (File f : dirs) {
			if(isScanning){
				if(f.isFile()){
					if(checkFile(f, mediaTypes, callback))
						break;
				}else{
					scanDirectory(f,mediaTypes,callback);
				}
			}else
				return;
		}
		if(isScanning)
		callback.onScanCompleted(isDeepEnough);
	}
	
	private boolean checkFile(File f,LinkedList<String> mediaTypes, ScanCallback cb){
		String name = f.getName();
		int i = name.lastIndexOf('.');
		if(i != -1){
			name = name.substring(i).toUpperCase();
			if (mediaTypes.contains(name)) {
				cb.onFind(f.getParent());
//				Log.d(TAG, "find out-->"+f.getAbsolutePath());
				return true;
			}
		}
		return false;
	}
	
	private void scanDirectory(File f,LinkedList<String> mediaTypes, ScanCallback cb){
		if(!isScanning)
			return;
		if(f.listFiles()==null)
			return;
		currentDeep++;
		for(File file : f.listFiles()){
			if(isScanning){
				if (currentDeep <= maxScanDeep) {//С��ɨ�����
					if(file.isFile()){
						if(checkFile(file, mediaTypes, cb))
							break;
					}else{
						scanDirectory(file, mediaTypes, cb);
					}
				} else {//����ɨ�����
					isDeepEnough = false;
				}
			}else
				return;
		}
		currentDeep-- ;
	}
	
	
	public void stopScanning(){
		if(isScanning){
			//1��ֹͣɨ��
			isScanning = false;
			//2������callback false
			callback.onScanCompleted(false);
			if(mUnmountReceiver.isOrderedBroadcast()){
				ctx.unregisterReceiver(mUnmountReceiver);
			}
		}
	}
	/*
	 * �Ƴ��ƶ��洢�豸�㲥����-->ֹͣ����?
	 */
	private static BroadcastReceiver mUnmountReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			callback.onError(new Exception("�豸�Ƴ�����������ֹͣ!"),Scanner.ERROR_DEVICE_REMOVED);
			scanner.stopScanning();
			Log.w(TAG, "----->�豸�Ƴ�����������ֹͣ!<-----");
		}
	};

	
	
	/**
	 * ��ȡsd����Ϣ �ж���ƶ��豸,���豸�����ռ��С˳��һ��һ��ɨ��;
	 * 
	 * @param callback
	 */
	private void readSDCard(ScanCallback callback) {
		Log.d(TAG,"ExternalStorageState-->"+ Environment.getExternalStorageState()+
				"   ExternalStorageDirectory-->"+ Environment.getExternalStorageDirectory());
		File root = new File("/mnt");
		if(root.exists() && root.isDirectory()){
			checkExternStorage(root);
		}else if(Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())&&Environment.getExternalStorageDirectory().exists()){
			root = Environment.getExternalStorageDirectory();
			checkExternStorage(root);
		}else{
			callback.onError(new Exception("�豸δ�ҵ�����state-->"+Environment.getExternalStorageState()),ERROR_DEVICE_NOT_FOUND);
			Log.w(TAG, "δ�ҵ��ⲿ�洢�豸����");
		}
	}

	private void checkExternStorage(File root) {
		if(root.getAbsolutePath().equals("/sdcard")){//���iTv����
			StatFs sf = new StatFs(root.getPath());
			long totalSize = sf.getBlockSize() * sf.getBlockCount();
			long availSize = sf.getBlockSize() * sf.getAvailableBlocks();
			Log.d(TAG, "�����豸--->"+ root.getPath()+ "<---,�ܴ�С:" + formatSize(totalSize) + ",ʣ��ռ�:"+ formatSize(availSize));
			sdcards.add(root);
		}else{
			File[] files = root.listFiles();
			for (File f : files) {
				if (f.isDirectory()) {//���enjoy����
					String path = f.getAbsolutePath();
					if (
						path.equals("/mnt/flash")|| 
						path.equals("/mnt/sata")|| 
						path.equals("/mnt/sdcard")||
						path.equals("/mnt/usb")||
						path.startsWith("/mnt/sd")) 
					{
						StatFs sf = new StatFs(f.getPath());
						long totalSize = sf.getBlockSize() * sf.getBlockCount();
						long availSize = sf.getBlockSize() * sf.getAvailableBlocks();
						Log.d(TAG, "�����豸--->"+ path+ "<---,�ܴ�С:" + formatSize(totalSize) + ",ʣ��ռ�:"+ formatSize(availSize));
						sdcards.add(f);
					}
				}
			}
		}
		
	}
	private String formatSize(long size) {     
		return Formatter.formatFileSize(ctx, size);
    }
	

	private File[] reorderArray(List<File> sds) {
		File[] files = new File[sds.size()];
		for (int i = 0; i < sds.size(); i++) {
			files[i] = sds.get(i);
		}
		for (int i = 0; i < files.length; i++) {
			for (int j = i + 1; j < files.length; j++) {
				StatFs sfi = new StatFs(files[i].getPath());
				StatFs sfj = new StatFs(files[j].getPath());
				long total_i = sfi.getBlockSize() * sfi.getBlockCount() / 1024;
				long total_j = sfj.getBlockSize() * sfj.getBlockCount() / 1024;

				if (total_i > total_j) {
					File temp = files[i];
					files[i] = files[j];
					files[j] = temp;
				}
			}
		}
		return files;
	}
	
	
	/**
	 * 
	 * @param mediaType the media type
	 * @param path  the directory's path
	 * @param callBack
	 */
	public void scanTheDirectory(int mediaType,String path,ScanCallback cb){
		isScanning = true;
		File directory = new File(path);
		if(directory.listFiles()==null)
			return;
		for (File f : directory.listFiles()) {
			if(isScanning){
				if (f.isDirectory()) {
					scanTheDirectory(mediaType, f.getAbsolutePath(), cb);
				}else{
					if (mediaType == TYPE_IMAGE) {
						scanTheFileType(f,Images,cb);
					} else if (mediaType == TYPE_AUDIO) {
						scanTheFileType(f,Audios,cb);
					} else if (mediaType == TYPE_VIDEO) {
						scanTheFileType(f,Videos,cb);
					} else {
						cb.onError(new Exception("δ֪���������쳣 mediaType-->" + mediaType),ERROR_TYPE);
					}
				}
			}else
				return;
		}
		if(isScanning)//---
		cb.onScanCompleted(true);
	}
	
	private void scanTheFileType(File file,LinkedList<String> mediaTypes,ScanCallback cb){
		String name = file.getName();
		int i = name.lastIndexOf('.');
		if (i != -1) {
			name = name.substring(i).toUpperCase();
			if (mediaTypes.contains(name)) {
				cb.onFind(file.getAbsolutePath());
//				Log.d(TAG, "find file in the dir-->" + file.getAbsolutePath());
			}
		}
	}
	
}
