

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Pattern;

class LookupTable{
	// 是否初始化完毕
	public volatile boolean initOk = false;
	private final int NUM = 8192;
	public short[] nBitsAndStrTable = new short[NUM];
	public short[] nBitsTable = new short[NUM];
	public short[] straightTable = new short[NUM];
	public short[] topCardTable = new short[NUM];
	public long[] topFiveCardsTable = new long[NUM];
	public long[] TwoCardTable = new long[1326];
	public final long[] CardMasksTable = { 
			0x1,
			0x2,
			0x4,
			0x8,
			0x10,
			0x20,
			0x40,
			0x80,
			0x100,
			0x200,
			0x400,
			0x800,
			0x1000,
			0x2000,
			0x4000,
			0x8000,
			0x10000,
			0x20000,
			0x40000,
			0x80000,
			0x100000,
			0x200000,
			0x400000,
			0x800000,
			0x1000000,
			0x2000000,
			0x4000000,
			0x8000000,
			0x10000000,
			0x20000000,
			0x40000000,
			0x80000000,
			0x100000000L,
			0x200000000L,
			0x400000000L,
			0x800000000L,
			0x1000000000L,
			0x2000000000L,
			0x4000000000L,
			0x8000000000L,
			0x10000000000L,
			0x20000000000L,
			0x40000000000L,
			0x80000000000L,
			0x100000000000L,
			0x200000000000L,
			0x400000000000L,
			0x800000000000L,
			0x1000000000000L,
			0x2000000000000L,
			0x4000000000000L,
			0x8000000000000L,
		};
	
	
	/**
	 * 初始化数据
	 */
	public void initData() {
		initOk = false;
	
		new Thread(){
			@Override
			public void run() {
				long startTime=System.currentTimeMillis();   //获取开始时间  
				BufferedReader br = null;
				try{
					File outFile = new File("hexdata.txt");
					br = new BufferedReader(new FileReader(outFile));
					String content = "";
					String tag = "";
					int idx = 0;
					String beginRegex = "\\w+/", endRegex = "/\\w+";
					Pattern beginPattern = Pattern.compile(beginRegex);
					Pattern endPattern = Pattern.compile(endRegex);
					while((content = br.readLine()) != null) {
						content = content.trim();
						if(endPattern.matcher(content).find()){							
							idx = 0;
						}else if(content.contains("/")) {
							tag = content;
						}else{
							if(tag.contains("nBitsAndStrTable")) {
								// nBitsAndStrTable开始了
								String sp = content.replace(",", "");
									try{
										String hexStr = sp.trim();
										if(hexStr.startsWith("0x")){
											hexStr = hexStr.replace("0x", "");
										}
										hexStr = hexStr.trim();
										nBitsAndStrTable[idx] = Short.parseShort(hexStr, 16);
										idx++;
									}catch(NumberFormatException ex) {
										Logger.Log(ex.getMessage());
									}
							}else if(tag.contains("nBitsTable")){
									String sp = content.replace(",", "");
									try{
										String hexStr = sp.trim();
										if(hexStr.startsWith("0x")){
											hexStr = hexStr.replace("0x", "");
										}
										hexStr = hexStr.trim();
										nBitsTable[idx] = Short.parseShort(hexStr, 16);
										if(idx == 1282) {
											System.out.println("nBitsTable[1282]"+nBitsTable[idx]);
										}
										idx++;
									}catch(NumberFormatException ex) {
										Logger.Log(ex.getMessage());
									}
							}else if(tag.contains("straightTable")){
								String sp = content.replace(",", "");
								try{
									String hexStr = sp.trim();
									if(hexStr.startsWith("0x")){
										hexStr = hexStr.replace("0x", "");
									}
									hexStr = hexStr.trim();
									straightTable[idx] = Short.parseShort(hexStr, 16);
									idx++;
								}catch(NumberFormatException ex) {
									Logger.Log(ex.getMessage());
								}
							}else if(tag.contains("topFiveCardsTable")){
								String sp = content.replace(",", "");
								try{
									String hexStr = sp.trim();
									if(hexStr.startsWith("0x")){
										hexStr = hexStr.replace("0x", "");
									}
									hexStr = hexStr.trim();
									topFiveCardsTable[idx] = Long.parseLong(hexStr, 16);
									idx++;
								}catch(NumberFormatException ex) {
									Logger.Log(ex.getMessage());
								}
							}else if(tag.contains("topCardTable")){
								String sp = content.replace(",", "");
								try{
									String hexStr = sp.trim();
									if(hexStr.startsWith("0x")){
										hexStr = hexStr.replace("0x", "");
									}
									hexStr = hexStr.trim();
									topCardTable[idx] = Short.parseShort(hexStr, 16);
									idx++;
								}catch(NumberFormatException ex) {
									Logger.Log(ex.getMessage());
								}
							}else if(tag.contains("TwoCardTable")){
								String sp[] = content.split(",");
//								System.out.println("values linesize="+sp.length+" idx="+idx);
								for(int i = 0; i <sp.length; i++) {
									try{
										String hexStr = sp[i].trim();
										if(hexStr.startsWith("0x")){
											hexStr = hexStr.replace("0x", "");
										}
										hexStr = hexStr.trim();
										TwoCardTable[idx] = Long.parseLong(hexStr, 16);
										idx++;
									}catch(NumberFormatException ex) {
										Logger.Log(ex.getMessage());
										//ex.printStackTrace();
									}
								}
							}
						}
					}
					
					Logger.Log("initok look up table inthread="+initOk);
					
				}catch(IOException ex) {
					Logger.Log(ex.getMessage());
				}finally{
					if(br != null) {
						try {
							br.close();
						} catch (IOException e) {
							Logger.Log(e.getMessage());
						}
					}
					initOk = true;
				}
			}
		}.start();
	}
}